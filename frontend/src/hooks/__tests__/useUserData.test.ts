/**
 * useUserData Test Suite
 * Tests session-driven user data fetching and auth store synchronization.
 */

import { renderHook, act, waitFor } from '@testing-library/react'
import { useSession } from 'next-auth/react'
import { useUserData } from '../useUserData'
import { apiFetch } from '../../lib/apiClient'

jest.mock('next-auth/react')

const mockSetAuth = jest.fn()
const mockReset = jest.fn()
jest.mock('../../store/useAuthStore', () => ({
  useAuthStore: jest.fn((selector) => {
    const store = { setAuth: mockSetAuth, reset: mockReset }
    return selector ? selector(store) : store
  }),
}))

jest.mock('../../lib/apiClient', () => ({
  apiFetch: jest.fn(),
}))

const mockUseSession = useSession as jest.MockedFunction<typeof useSession>
const mockApiFetch = apiFetch as jest.MockedFunction<typeof apiFetch>

const userPayload = {
  authenticated: true,
  email: 'test@example.com',
  freeRemaining: 15,
  paidCredits: 100,
  subscriptionStatus: 'active',
  autoRenewal: true,
}

// jsdom does not provide the Response global, so use a minimal stub.
function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as unknown as Response
}

describe('useUserData', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    jest.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    jest.restoreAllMocks()
  })

  it('should do nothing while the session is loading', () => {
    mockUseSession.mockReturnValue({ data: null, status: 'loading', update: jest.fn() })

    renderHook(() => useUserData())

    expect(mockApiFetch).not.toHaveBeenCalled()
    expect(mockReset).not.toHaveBeenCalled()
  })

  it('should reset the auth store when unauthenticated', () => {
    mockUseSession.mockReturnValue({ data: null, status: 'unauthenticated', update: jest.fn() })

    renderHook(() => useUserData())

    expect(mockReset).toHaveBeenCalled()
    expect(mockApiFetch).not.toHaveBeenCalled()
  })

  it('should fetch user data and update the store when authenticated', async () => {
    mockUseSession.mockReturnValue({
      data: { idToken: 'token-123', expires: '2099-01-01' },
      status: 'authenticated',
      update: jest.fn(),
    })
    mockApiFetch.mockResolvedValue(jsonResponse(userPayload))

    const { result } = renderHook(() => useUserData())

    await waitFor(() => {
      expect(mockSetAuth).toHaveBeenCalledWith(userPayload)
    })
    expect(mockApiFetch).toHaveBeenCalledWith('/api/user/me', {
      token: 'token-123',
      timeoutMs: 10000,
      retries: 0,
    })
    expect(result.current.error).toBeNull()
    expect(result.current.loading).toBe(false)
  })

  it('should default autoRenewal to false when missing from the response', async () => {
    mockUseSession.mockReturnValue({
      data: { idToken: 'token-123', expires: '2099-01-01' },
      status: 'authenticated',
      update: jest.fn(),
    })
    mockApiFetch.mockResolvedValue(jsonResponse({ ...userPayload, autoRenewal: undefined }))

    renderHook(() => useUserData())

    await waitFor(() => {
      expect(mockSetAuth).toHaveBeenCalledWith({ ...userPayload, autoRenewal: false })
    })
  })

  it('should set an error when the request fails with a non-OK status', async () => {
    mockUseSession.mockReturnValue({
      data: { idToken: 'token-123', expires: '2099-01-01' },
      status: 'authenticated',
      update: jest.fn(),
    })
    mockApiFetch.mockResolvedValue(jsonResponse({}, 401))

    const { result } = renderHook(() => useUserData())

    await waitFor(() => {
      expect(result.current.error).toContain('401')
    })
    expect(mockSetAuth).not.toHaveBeenCalled()
    expect(result.current.loading).toBe(false)
  })

  it('should set an error when the request throws', async () => {
    mockUseSession.mockReturnValue({
      data: { idToken: 'token-123', expires: '2099-01-01' },
      status: 'authenticated',
      update: jest.fn(),
    })
    mockApiFetch.mockRejectedValue(new Error('network down'))

    const { result } = renderHook(() => useUserData())

    await waitFor(() => {
      expect(result.current.error).toBe('network down')
    })
  })

  it('should not fetch when authenticated without an idToken', () => {
    mockUseSession.mockReturnValue({
      data: { expires: '2099-01-01' },
      status: 'authenticated',
      update: jest.fn(),
    })

    renderHook(() => useUserData())

    expect(mockApiFetch).not.toHaveBeenCalled()
  })

  it('should refetch on demand', async () => {
    mockUseSession.mockReturnValue({
      data: { idToken: 'token-123', expires: '2099-01-01' },
      status: 'authenticated',
      update: jest.fn(),
    })
    mockApiFetch.mockResolvedValue(jsonResponse(userPayload))

    const { result } = renderHook(() => useUserData())

    await waitFor(() => expect(mockApiFetch).toHaveBeenCalledTimes(1))

    mockApiFetch.mockResolvedValue(jsonResponse(userPayload))
    await act(async () => {
      await result.current.refetch()
    })

    expect(mockApiFetch).toHaveBeenCalledTimes(2)
  })

  it('should not refetch without a session token', async () => {
    mockUseSession.mockReturnValue({ data: null, status: 'unauthenticated', update: jest.fn() })

    const { result } = renderHook(() => useUserData())

    await act(async () => {
      await result.current.refetch()
    })

    expect(mockApiFetch).not.toHaveBeenCalled()
  })
})
