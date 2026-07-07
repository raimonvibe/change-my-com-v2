/**
 * API Client (Adapter pattern)
 *
 * Central wrapper around fetch() for all backend calls: base URL resolution,
 * optional Bearer auth, per-attempt timeout via AbortController, and a single
 * retry on transient network errors. Replaces the raw fetch calls that were
 * previously scattered across pages and hooks, and the unused axios instance.
 */

import { API_URL } from '../env';

export interface ApiRequestOptions {
  method?: string;
  /** Bearer token; when set an Authorization header is added. */
  token?: string;
  body?: BodyInit;
  headers?: Record<string, string>;
  credentials?: RequestCredentials;
  /** Per-attempt timeout in milliseconds. Defaults to 30s. */
  timeoutMs?: number;
  /** Number of retries on transient network errors. Defaults to 1. */
  retries?: number;
}

/**
 * Detects transient network failures worth retrying. Deliberately excludes
 * AbortError (our own timeout) so we never silently retry a timed-out request.
 */
export function isTransientNetworkError(e: unknown): boolean {
  if (e instanceof TypeError) return true; // fetch() throws TypeError on network failure
  if (e instanceof Error) {
    const msg = e.message;
    return (
      msg.includes('Failed to fetch') ||
      msg.includes('NetworkError') ||
      msg.includes('ERR_CONNECTION') ||
      msg.includes('ERR_NETWORK') ||
      msg.includes('ERR_EMPTY_RESPONSE')
    );
  }
  return false;
}

/**
 * fetch() wrapper with a per-attempt timeout and retry on transient network
 * errors. Each attempt uses a fresh AbortController (and therefore a fresh
 * connection), which works around a reset/poisoned keep-alive connection
 * being reused for back-to-back requests.
 */
export async function fetchWithRetry(
  url: string,
  init: RequestInit,
  timeoutMs: number,
  retries = 1
): Promise<Response> {
  let lastError: unknown;
  for (let attempt = 0; attempt <= retries; attempt++) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await fetch(url, { ...init, signal: controller.signal });
    } catch (e) {
      lastError = e;
      if (attempt < retries && isTransientNetworkError(e)) {
        // Brief backoff before retrying on a new connection.
        await new Promise((resolve) => setTimeout(resolve, 400));
        continue;
      }
      throw e;
    } finally {
      clearTimeout(timeoutId);
    }
  }
  // Unreachable in practice, but keeps TypeScript's control-flow analysis happy.
  throw lastError;
}

/**
 * Performs a backend API request. The path is appended to the configured
 * API base URL (pass a path starting with "/").
 */
export async function apiFetch(path: string, options: ApiRequestOptions = {}): Promise<Response> {
  const {
    method = 'GET',
    token,
    body,
    headers = {},
    credentials,
    timeoutMs = 30000,
    retries = 1,
  } = options;

  const finalHeaders: Record<string, string> = { ...headers };
  if (token) {
    finalHeaders.Authorization = `Bearer ${token}`;
  }

  return fetchWithRetry(
    `${API_URL}${path}`,
    { method, headers: finalHeaders, body, ...(credentials ? { credentials } : {}) },
    timeoutMs,
    retries
  );
}

/**
 * Extracts the most specific error message from an error response:
 * X-Error-Message header first (most reliable), then a JSON `error` field,
 * then the provided fallback.
 */
export async function extractErrorMessage(res: Response, fallback: string): Promise<string> {
  const headerError = res.headers.get('X-Error-Message');
  if (headerError) return headerError;
  const errorData = (await res.json().catch(() => ({}))) as { error?: string };
  return errorData.error || fallback;
}
