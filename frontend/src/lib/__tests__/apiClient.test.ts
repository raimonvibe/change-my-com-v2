/**
 * API Client Test Suite
 * Tests the fetch adapter: transient error detection, retry behavior,
 * request building (auth header, base URL), and error message extraction.
 */

import {
  isTransientNetworkError,
  fetchWithRetry,
  apiFetch,
  extractErrorMessage,
} from '../apiClient'

// jsdom does not provide the Response global, so use a minimal stub.
function stubResponse(
  body = '',
  options: { status?: number; headers?: Record<string, string> } = {}
): Response {
  const { status = 200, headers = {} } = options
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name: string) => headers[name] ?? null },
    json: () => {
      try {
        return Promise.resolve(JSON.parse(body))
      } catch {
        return Promise.reject(new Error('invalid json'))
      }
    },
  } as unknown as Response
}

describe('apiClient', () => {
  const originalFetch = global.fetch

  afterEach(() => {
    global.fetch = originalFetch
    jest.restoreAllMocks()
  })

  describe('isTransientNetworkError', () => {
    it('should treat TypeError as transient (fetch network failure)', () => {
      expect(isTransientNetworkError(new TypeError('Failed to fetch'))).toBe(true)
    })

    it.each([
      'Failed to fetch',
      'NetworkError when attempting to fetch resource',
      'net::ERR_CONNECTION_RESET',
      'net::ERR_NETWORK',
      'net::ERR_EMPTY_RESPONSE',
    ])('should treat "%s" errors as transient', (message) => {
      expect(isTransientNetworkError(new Error(message))).toBe(true)
    })

    it('should not treat other errors as transient', () => {
      expect(isTransientNetworkError(new Error('Something else broke'))).toBe(false)
    })

    it('should not treat abort errors as transient', () => {
      const abortError = new DOMException('The operation was aborted', 'AbortError')
      expect(isTransientNetworkError(abortError)).toBe(false)
    })

    it('should not treat non-error values as transient', () => {
      expect(isTransientNetworkError('boom')).toBe(false)
      expect(isTransientNetworkError(null)).toBe(false)
      expect(isTransientNetworkError(undefined)).toBe(false)
    })
  })

  describe('fetchWithRetry', () => {
    it('should return the response on first success', async () => {
      const response = stubResponse('ok')
      global.fetch = jest.fn().mockResolvedValue(response)

      const result = await fetchWithRetry('http://example.com', {}, 1000)

      expect(result).toBe(response)
      expect(global.fetch).toHaveBeenCalledTimes(1)
    })

    it('should pass an abort signal to fetch', async () => {
      global.fetch = jest.fn().mockResolvedValue(stubResponse('ok'))

      await fetchWithRetry('http://example.com', { method: 'POST' }, 1000)

      const init = (global.fetch as jest.Mock).mock.calls[0][1]
      expect(init.method).toBe('POST')
      expect(init.signal).toBeInstanceOf(AbortSignal)
    })

    it('should retry once on a transient network error', async () => {
      const response = stubResponse('ok')
      global.fetch = jest
        .fn()
        .mockRejectedValueOnce(new TypeError('Failed to fetch'))
        .mockResolvedValueOnce(response)

      const result = await fetchWithRetry('http://example.com', {}, 1000, 1)

      expect(result).toBe(response)
      expect(global.fetch).toHaveBeenCalledTimes(2)
    })

    it('should throw after exhausting retries', async () => {
      global.fetch = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'))

      await expect(fetchWithRetry('http://example.com', {}, 1000, 1)).rejects.toThrow(
        'Failed to fetch'
      )
      expect(global.fetch).toHaveBeenCalledTimes(2)
    })

    it('should not retry non-transient errors', async () => {
      global.fetch = jest.fn().mockRejectedValue(new Error('fatal'))

      await expect(fetchWithRetry('http://example.com', {}, 1000, 1)).rejects.toThrow('fatal')
      expect(global.fetch).toHaveBeenCalledTimes(1)
    })

    it('should not retry when retries is 0', async () => {
      global.fetch = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'))

      await expect(fetchWithRetry('http://example.com', {}, 1000, 0)).rejects.toThrow()
      expect(global.fetch).toHaveBeenCalledTimes(1)
    })
  })

  describe('apiFetch', () => {
    beforeEach(() => {
      global.fetch = jest.fn().mockResolvedValue(stubResponse('ok'))
    })

    it('should prepend the API base URL to the path', async () => {
      await apiFetch('/api/user/me')

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/user/me',
        expect.any(Object)
      )
    })

    it('should default to GET', async () => {
      await apiFetch('/api/user/me')

      const init = (global.fetch as jest.Mock).mock.calls[0][1]
      expect(init.method).toBe('GET')
    })

    it('should add an Authorization header when a token is provided', async () => {
      await apiFetch('/api/user/me', { token: 'my-token' })

      const init = (global.fetch as jest.Mock).mock.calls[0][1]
      expect(init.headers.Authorization).toBe('Bearer my-token')
    })

    it('should not add an Authorization header without a token', async () => {
      await apiFetch('/api/anonymous/remaining')

      const init = (global.fetch as jest.Mock).mock.calls[0][1]
      expect(init.headers.Authorization).toBeUndefined()
    })

    it('should pass method, body and custom headers through', async () => {
      const body = JSON.stringify({ a: 1 })
      await apiFetch('/api/billing/checkout', {
        method: 'POST',
        body,
        headers: { 'Content-Type': 'application/json' },
      })

      const init = (global.fetch as jest.Mock).mock.calls[0][1]
      expect(init.method).toBe('POST')
      expect(init.body).toBe(body)
      expect(init.headers['Content-Type']).toBe('application/json')
    })

    it('should include credentials only when specified', async () => {
      await apiFetch('/api/a')
      let init = (global.fetch as jest.Mock).mock.calls[0][1]
      expect(init.credentials).toBeUndefined()

      await apiFetch('/api/b', { credentials: 'include' })
      init = (global.fetch as jest.Mock).mock.calls[1][1]
      expect(init.credentials).toBe('include')
    })
  })

  describe('extractErrorMessage', () => {
    it('should prefer the X-Error-Message header', async () => {
      const res = stubResponse(JSON.stringify({ error: 'json error' }), {
        headers: { 'X-Error-Message': 'header error' },
      })

      expect(await extractErrorMessage(res, 'fallback')).toBe('header error')
    })

    it('should fall back to the JSON error field', async () => {
      const res = stubResponse(JSON.stringify({ error: 'json error' }))

      expect(await extractErrorMessage(res, 'fallback')).toBe('json error')
    })

    it('should use the fallback when the body is not JSON', async () => {
      const res = stubResponse('not json')

      expect(await extractErrorMessage(res, 'fallback')).toBe('fallback')
    })

    it('should use the fallback when JSON has no error field', async () => {
      const res = stubResponse(JSON.stringify({ message: 'nope' }))

      expect(await extractErrorMessage(res, 'fallback')).toBe('fallback')
    })
  })
})
