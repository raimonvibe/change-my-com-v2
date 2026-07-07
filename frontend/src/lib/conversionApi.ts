/**
 * Conversion API — Strategy + Template Method
 *
 * One shared pipeline (runConversion) handles the request/response flow for
 * every conversion: build form data, POST with timeout+retry, map status
 * codes, create the result blob URL. The differences between raster and GIF
 * conversions (endpoint, form fields, timeouts, error copy) live in two
 * ConversionStrategy objects. This replaces the ~90% duplicated logic that
 * previously existed between convertGif() and the raster path in page.tsx.
 */

import { apiFetch, extractErrorMessage } from './apiClient';

export type JobStatus = 'queued' | 'running' | 'done' | 'error';

export type Job = {
  id: string;
  file: File;
  status: JobStatus;
  url?: string;
  error?: string;
  progress?: number;
  startTime?: number;
  isGif?: boolean;
  width?: number;
  height?: number;
  /** Target format used for this conversion (so UI shows correct format per job after dropdown changes) */
  targetFormat?: string;
};

export type ConversionSettings = {
  target: string;
  quality: number;
  sharpness: number;
  resizeEnabled: boolean;
  maxWidth: number;
  gifFormats: string[];
};

export type ConversionOutcome =
  | { kind: 'done'; url: string }
  | { kind: 'limit' }
  | { kind: 'error'; message: string };

export interface ConversionStrategy {
  /** Label stored on the job so the queue shows the right target per job. */
  targetLabel(settings: ConversionSettings): string;
  /** Estimated conversion time per megabyte, for simulated progress. */
  estimatedMsPerMb: number;
  /** Whether hitting the credit limit should stop the rest of the queue. */
  stopQueueOnLimit: boolean;
  endpoint: string;
  timeoutMs(settings: ConversionSettings): number;
  buildForm(job: Job, settings: ConversionSettings): FormData;
  /** Fallback error copy when the server provides no specific message. */
  fallbackErrorForStatus(status: number): string | undefined;
  /** Error copy for a timed-out request (AbortError). */
  timeoutErrorMessage: string;
}

function appendCommonFields(form: FormData, settings: ConversionSettings) {
  form.append('quality', String(settings.quality));
  form.append('sharpness', String(settings.sharpness));
  if (settings.resizeEnabled && settings.maxWidth) {
    form.append('width', String(settings.maxWidth));
  }
}

export const rasterConversionStrategy: ConversionStrategy = {
  targetLabel: (settings) => settings.target,
  estimatedMsPerMb: 2000,
  stopQueueOnLimit: true,
  endpoint: '/api/convert',
  // GIF output needs more time due to complexity
  timeoutMs: (settings) => (settings.target === 'gif' ? 60000 : 30000),
  buildForm: (job, settings) => {
    const form = new FormData();
    form.append('file', job.file);
    form.append('to', settings.target);
    appendCommonFields(form, settings);
    return form;
  },
  fallbackErrorForStatus: (status) => {
    if (status === 400) return 'Invalid file format or corrupted image. Please try a different file.';
    if (status === 415) return 'Unsupported file format. Please use standard image formats like .jpg, .png, or .webp.';
    if (status === 422) return 'File format not supported for conversion. Please try a different image format.';
    return undefined;
  },
  timeoutErrorMessage: 'Request timed out. Please check your internet connection and try again.',
};

export const gifConversionStrategy: ConversionStrategy = {
  targetLabel: (settings) => `${settings.gifFormats.map(f => f.toUpperCase()).join(', ')} (ZIP)`,
  estimatedMsPerMb: 3000,
  stopQueueOnLimit: false,
  endpoint: '/api/convert/gif',
  timeoutMs: () => 60000,
  buildForm: (job, settings) => {
    const form = new FormData();
    form.append('file', job.file);
    form.append('formats', settings.gifFormats.join(','));
    appendCommonFields(form, settings);
    return form;
  },
  fallbackErrorForStatus: (status) => {
    if (status === 400) return 'Invalid GIF file or corrupted. Please try a different file.';
    if (status === 415) return 'Only GIF files are supported for frame extraction.';
    return undefined;
  },
  timeoutErrorMessage: 'Request timed out. GIF conversion may take longer for large files.',
};

export function strategyFor(job: Job): ConversionStrategy {
  return job.isGif ? gifConversionStrategy : rasterConversionStrategy;
}

/**
 * Template Method: the conversion request flow shared by all strategies.
 * Returns a discriminated outcome; the caller (queue hook) maps outcomes
 * to job state and UI side effects.
 */
export async function runConversion(
  job: Job,
  strategy: ConversionStrategy,
  settings: ConversionSettings,
  deps: {
    token?: string;
    onRateLimitRemaining?: (remaining: number) => void;
  } = {}
): Promise<ConversionOutcome> {
  try {
    const res = await apiFetch(strategy.endpoint, {
      method: 'POST',
      token: deps.token,
      body: strategy.buildForm(job, settings),
      timeoutMs: strategy.timeoutMs(settings),
    });

    const remaining = res.headers.get('X-RateLimit-Remaining');
    if (remaining && deps.onRateLimitRemaining) {
      deps.onRateLimitRemaining(parseInt(remaining));
    }

    if (res.status === 401) {
      return { kind: 'error', message: 'Please sign in with Google to convert.' };
    }
    if (res.status === 402) {
      return { kind: 'limit' };
    }
    if (res.status === 413) {
      const errorData = (await res.json().catch(() => ({}))) as { error?: string };
      return { kind: 'error', message: errorData.error || 'File is too large. Maximum allowed size is 20MB.' };
    }
    if (!res.ok) {
      const fallback = strategy.fallbackErrorForStatus(res.status) || `Conversion failed: ${res.status}`;
      const message = await extractErrorMessage(res, fallback);
      return { kind: 'error', message };
    }

    const blob = await res.blob();
    return { kind: 'done', url: URL.createObjectURL(blob) };
  } catch (e: unknown) {
    return { kind: 'error', message: describeConversionError(e, strategy) };
  }
}

function describeConversionError(e: unknown, strategy: ConversionStrategy): string {
  if (!(e instanceof Error)) return 'Unknown error occurred';

  if (e.name === 'AbortError') {
    return strategy.timeoutErrorMessage;
  }
  if (e.message.includes('Failed to fetch') || e.message.includes('NetworkError')) {
    return 'Network error. Please check your connection and try again.';
  }
  if (e.message.includes('ERR_TIMED_OUT') || e.message.includes('ERR_CONNECTION_RESET')) {
    return 'Connection timeout. Please try again with a better internet connection.';
  }
  if (e.message.includes('fetch')) {
    return 'Unable to connect to the server. Please try again later.';
  }
  return e.message;
}
