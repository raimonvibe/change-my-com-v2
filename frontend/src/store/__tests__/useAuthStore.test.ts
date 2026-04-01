/**
 * useAuthStore Test Suite
 * Tests the Zustand auth state management store
 */

import { renderHook, act } from '@testing-library/react'
import { useAuthStore } from '../useAuthStore'

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}

  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString()
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    clear: () => {
      store = {}
    },
  }
})()

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
})

describe('useAuthStore', () => {
  beforeEach(() => {
    // Clear localStorage before each test
    localStorageMock.clear()
    // Reset the store to initial state
    const { result } = renderHook(() => useAuthStore())
    act(() => {
      result.current.reset()
    })
  })

  describe('Initial State', () => {
    it('should initialize with default values', () => {
      const { result } = renderHook(() => useAuthStore())

      expect(result.current.email).toBeNull()
      expect(result.current.authenticated).toBe(false)
      expect(result.current.freeRemaining).toBe(0)
      expect(result.current.paidCredits).toBe(0)
      expect(result.current.subscriptionStatus).toBe('none')
      expect(result.current.autoRenewal).toBe(false)
    })

    it('should have setAuth function', () => {
      const { result } = renderHook(() => useAuthStore())

      expect(typeof result.current.setAuth).toBe('function')
    })

    it('should have reset function', () => {
      const { result } = renderHook(() => useAuthStore())

      expect(typeof result.current.reset).toBe('function')
    })
  })

  describe('setAuth Action', () => {
    it('should update email', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ email: 'test@example.com' })
      })

      expect(result.current.email).toBe('test@example.com')
    })

    it('should update authenticated status', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ authenticated: true })
      })

      expect(result.current.authenticated).toBe(true)
    })

    it('should update freeRemaining credits', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ freeRemaining: 5 })
      })

      expect(result.current.freeRemaining).toBe(5)
    })

    it('should update paidCredits', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ paidCredits: 100 })
      })

      expect(result.current.paidCredits).toBe(100)
    })

    it('should update subscriptionStatus', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ subscriptionStatus: 'active' })
      })

      expect(result.current.subscriptionStatus).toBe('active')
    })

    it('should update autoRenewal', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ autoRenewal: true })
      })

      expect(result.current.autoRenewal).toBe(true)
    })

    it('should update multiple fields at once', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({
          email: 'user@example.com',
          authenticated: true,
          freeRemaining: 3,
          paidCredits: 50,
          subscriptionStatus: 'premium',
          autoRenewal: true,
        })
      })

      expect(result.current.email).toBe('user@example.com')
      expect(result.current.authenticated).toBe(true)
      expect(result.current.freeRemaining).toBe(3)
      expect(result.current.paidCredits).toBe(50)
      expect(result.current.subscriptionStatus).toBe('premium')
      expect(result.current.autoRenewal).toBe(true)
    })

    it('should merge partial updates with existing state', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({
          email: 'test@example.com',
          authenticated: true,
        })
      })

      act(() => {
        result.current.setAuth({
          freeRemaining: 10,
        })
      })

      // Previous values should be preserved
      expect(result.current.email).toBe('test@example.com')
      expect(result.current.authenticated).toBe(true)
      // New value should be set
      expect(result.current.freeRemaining).toBe(10)
    })
  })

  describe('reset Action', () => {
    it('should reset all fields to initial state', () => {
      const { result } = renderHook(() => useAuthStore())

      // Set some values
      act(() => {
        result.current.setAuth({
          email: 'test@example.com',
          authenticated: true,
          freeRemaining: 5,
          paidCredits: 100,
          subscriptionStatus: 'premium',
          autoRenewal: true,
        })
      })

      // Reset
      act(() => {
        result.current.reset()
      })

      expect(result.current.email).toBeNull()
      expect(result.current.authenticated).toBe(false)
      expect(result.current.freeRemaining).toBe(0)
      expect(result.current.paidCredits).toBe(0)
      expect(result.current.subscriptionStatus).toBe('none')
      expect(result.current.autoRenewal).toBe(false)
    })

    it('should reset from authenticated state', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({
          email: 'premium@example.com',
          authenticated: true,
          paidCredits: 500,
        })
      })

      act(() => {
        result.current.reset()
      })

      expect(result.current.authenticated).toBe(false)
      expect(result.current.email).toBeNull()
      expect(result.current.paidCredits).toBe(0)
    })
  })

  describe('Persistence Configuration', () => {
    it('should have persistence middleware configured', () => {
      // Check that the persist API is available
      expect(useAuthStore.persist).toBeDefined()
      expect(typeof useAuthStore.persist.rehydrate).toBe('function')
      expect(typeof useAuthStore.persist.hasHydrated).toBe('function')
    })

    it('should use localStorage as storage', () => {
      const options = useAuthStore.persist.getOptions()
      expect(options.name).toBe('auth-storage')
      expect(options.storage).toBeDefined()
    })

    it('should maintain state after updates', async () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({
          email: 'persist@example.com',
          authenticated: true,
          freeRemaining: 5,
        })
      })

      // Wait a bit for any async operations
      await new Promise(resolve => setTimeout(resolve, 50))

      // State should be maintained
      expect(result.current.email).toBe('persist@example.com')
      expect(result.current.authenticated).toBe(true)
      expect(result.current.freeRemaining).toBe(5)
    })
  })

  describe('State Consistency', () => {
    it('should maintain state consistency across multiple updates', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ email: 'test1@example.com' })
      })

      act(() => {
        result.current.setAuth({ authenticated: true })
      })

      act(() => {
        result.current.setAuth({ freeRemaining: 5 })
      })

      expect(result.current.email).toBe('test1@example.com')
      expect(result.current.authenticated).toBe(true)
      expect(result.current.freeRemaining).toBe(5)
    })

    it('should handle zero values correctly', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({
          freeRemaining: 10,
          paidCredits: 100,
        })
      })

      act(() => {
        result.current.setAuth({
          freeRemaining: 0,
          paidCredits: 0,
        })
      })

      expect(result.current.freeRemaining).toBe(0)
      expect(result.current.paidCredits).toBe(0)
    })

    it('should handle empty string subscriptionStatus', () => {
      const { result } = renderHook(() => useAuthStore())

      act(() => {
        result.current.setAuth({ subscriptionStatus: '' })
      })

      expect(result.current.subscriptionStatus).toBe('')
    })
  })

  describe('Type Safety', () => {
    it('should accept partial state updates', () => {
      const { result } = renderHook(() => useAuthStore())

      // Should not throw error for partial updates
      act(() => {
        result.current.setAuth({ email: 'test@example.com' })
      })

      act(() => {
        result.current.setAuth({ authenticated: true, freeRemaining: 5 })
      })

      expect(result.current.email).toBe('test@example.com')
      expect(result.current.authenticated).toBe(true)
      expect(result.current.freeRemaining).toBe(5)
    })
  })
})
