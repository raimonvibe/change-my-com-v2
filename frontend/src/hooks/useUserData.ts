import { useEffect, useState } from 'react';
import { useSession } from 'next-auth/react';
import { useAuthStore } from '../store/useAuthStore';
import { API_URL } from '../env';

/**
 * useUserData Hook
 *
 * Manages user authentication state synchronization between NextAuth session and auth store.
 * Automatically fetches and updates user data when session changes.
 * Clears stale data when user logs out.
 *
 * Features:
 * - Watches for session changes and fetches user data
 * - Updates auth store with credits, subscription status, etc.
 * - Resets auth store on logout to prevent stale data
 * - Provides loading and error states
 *
 * @returns {Object} - { loading, error, refetch }
 */
export function useUserData() {
  const { data: session, status } = useSession();
  const setAuth = useAuthStore(s => s.setAuth);
  const reset = useAuthStore(s => s.reset);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Fetch user data from backend
  const fetchUserData = async (idToken: string) => {
    setLoading(true);
    setError(null);

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 10000);

      const res = await fetch(`${API_URL}/api/user/me`, {
        headers: { Authorization: `Bearer ${idToken}` },
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        throw new Error(`Failed to fetch user data: ${res.status}`);
      }

      const data = await res.json() as {
        authenticated: boolean;
        email: string;
        freeRemaining: number;
        paidCredits: number;
        subscriptionStatus: string;
        autoRenewal: boolean;
      };

      // Update auth store with all user data
      setAuth({
        authenticated: data.authenticated,
        email: data.email,
        freeRemaining: data.freeRemaining,
        paidCredits: data.paidCredits,
        subscriptionStatus: data.subscriptionStatus,
        autoRenewal: data.autoRenewal ?? false,
      });
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : 'Failed to fetch user data';
      setError(errorMsg);
      console.error('useUserData error:', errorMsg);
    } finally {
      setLoading(false);
    }
  };

  // Watch for session changes
  useEffect(() => {
    if (status === 'loading') {
      return; // Wait for session to load
    }

    if (status === 'unauthenticated') {
      // User logged out - clear stale auth data
      reset();
      setError(null);
      return;
    }

    if (status === 'authenticated' && session?.idToken) {
      // User logged in - fetch fresh user data
      fetchUserData(session.idToken as string);
    }
  }, [status, session?.idToken]);

  // Manual refetch function for explicit updates (e.g., after conversion)
  const refetch = async () => {
    if (session?.idToken) {
      await fetchUserData(session.idToken as string);
    }
  };

  return {
    loading,
    error,
    refetch,
  };
}
