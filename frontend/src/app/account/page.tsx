'use client';
// Trigger new deployment - TypeScript fixes applied - v2
import React, { useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import { useAuthStore } from "../../store/useAuthStore";
import { API_URL } from "../../env";

export default function AccountPage() {
  const { data: session } = useSession();
  const setAuth = useAuthStore(s => s.setAuth);
  const email = useAuthStore(s => s.email);
  const freeRemaining = useAuthStore(s => s.freeRemaining);
  const paidCredits = useAuthStore(s => s.paidCredits);
  const subscriptionStatus = useAuthStore(s => s.subscriptionStatus);
  const autoRenewal = useAuthStore(s => s.autoRenewal);
  const [toggleLoading, setToggleLoading] = useState(false);

  useEffect(() => {
    const fetchMe = async (retryCount = 0): Promise<void> => {
      if (!session) return;
      const token = session?.idToken;
      
      // Wait for idToken to be available
      if (!token) {
        console.log('Waiting for idToken to be available...');
        // Retry after a short delay if we haven't exceeded max retries
        if (retryCount < 3) {
          setTimeout(() => fetchMe(retryCount + 1), 1000);
        }
        return;
      }
      
      try {
        console.log('Fetching user data with token:', token ? 'present' : 'missing');
        const res = await fetch(`${API_URL}/api/user/me`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        
        console.log('Response status:', res.status, res.statusText);
        console.log('Response headers:', Object.fromEntries(res.headers.entries()));
        
        if (!res.ok) {
          console.error('Failed to fetch user data:', res.status, res.statusText);
          // Try to get response text for debugging
          const errorText = await res.text();
          console.error('Error response body:', errorText);
          
          // If 403 and we haven't retried too many times, try again
          if (res.status === 403 && retryCount < 2) {
            console.log('Retrying after 403 error...');
            setTimeout(() => fetchMe(retryCount + 1), 2000);
          }
          return;
        }
        
        const contentType = res.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
          console.error('Response is not JSON:', contentType);
          const responseText = await res.text();
          console.error('Response body:', responseText);
          return;
        }
        
        const data = await res.json() as {
          authenticated: boolean;
          email: string;
          freeRemaining: number;
          paidCredits: number;
          subscriptionStatus: string;
          autoRenewal: boolean;
        };
        console.log('User data received:', data);
        setAuth({
          authenticated: data.authenticated,
          email: data.email,
          freeRemaining: data.freeRemaining,
          paidCredits: data.paidCredits,
          subscriptionStatus: data.subscriptionStatus,
          autoRenewal: data.autoRenewal
        });
      } catch (error) {
        console.error('Error fetching user data:', error);
        // Retry on network errors
        if (retryCount < 2) {
          console.log('Retrying after network error...');
          setTimeout(() => fetchMe(retryCount + 1), 2000);
        }
      }
    };
    fetchMe();
  }, [session, setAuth]);

  const toggleAutoRenewal = async () => {
    if (!session?.idToken) return;

    setToggleLoading(true);
    try {
      const res = await fetch(`${API_URL}/api/user/toggle-auto-renewal`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${session.idToken}` },
      });

      if (res.ok) {
        const data = await res.json();
        if (data.success) {
          setAuth({ autoRenewal: data.autoRenewal });
        }
      }
    } catch (error) {
      console.error('Error toggling auto-renewal:', error);
    } finally {
      setToggleLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl md:text-3xl font-semibold text-sky-800">Your Account</h1>
      
      {session?.user ? (
        <div className="space-y-4">
          {/* User Info Card */}
          <div className="rounded-lg border bg-white p-6">
            <h2 className="text-xl md:text-2xl font-medium text-slate-800 mb-4">Account Information</h2>
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 bg-sky-100 rounded-full flex items-center justify-center">
                  <span className="text-sky-600 font-medium text-lg">
                    {email ? email.charAt(0).toUpperCase() : 'U'}
                  </span>
                </div>
                <div>
                  <div className="font-medium text-slate-800 text-lg">{email || 'Loading...'}</div>
                  <div className="text-base text-slate-500">Google Account</div>
                </div>
              </div>
            </div>
          </div>

          {/* Credits Card */}
          <div className="rounded-lg border bg-white p-6">
            <h2 className="text-xl md:text-2xl font-medium text-slate-800 mb-4">Usage & Credits</h2>

            {paidCredits > 0 ? (
              // Subscriber view
              <div className="space-y-4">
                <div className="bg-gradient-to-br from-emerald-50 to-emerald-100 rounded-lg p-6 border-2 border-emerald-200">
                  <div className="flex items-center gap-2 mb-2">
                    <svg className="w-6 h-6 text-emerald-600" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                    <div className="text-base font-semibold text-emerald-700">Active Subscription</div>
                  </div>
                  <div className="text-4xl md:text-5xl font-bold text-emerald-600 mb-1">{paidCredits}</div>
                  <div className="text-base text-emerald-700">conversions remaining this month</div>
                  <div className="text-sm text-emerald-600 mt-2">You have 1000 conversions per month</div>
                </div>

                {/* Auto-renewal toggle */}
                <div className="bg-white rounded-lg p-4 border border-slate-200">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="font-medium text-slate-800 text-lg">Auto-Renewal</div>
                      <div className="text-base text-slate-600">
                        {autoRenewal ? 'Your subscription will automatically renew each month' : 'Your subscription will not renew automatically'}
                      </div>
                    </div>
                    <button
                      onClick={toggleAutoRenewal}
                      disabled={toggleLoading}
                      className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                        autoRenewal ? 'bg-emerald-600' : 'bg-slate-300'
                      } ${toggleLoading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                    >
                      <span
                        className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                          autoRenewal ? 'translate-x-6' : 'translate-x-1'
                        }`}
                      />
                    </button>
                  </div>
                </div>

                <div className="text-base text-slate-600 bg-slate-50 rounded-lg p-4">
                  <strong>Note:</strong> As a subscriber, you&apos;re using your monthly credits. Free daily conversions are available after your subscription expires.
                </div>
              </div>
            ) : (
              // Free user view
              <div className="space-y-4">
                <div className="bg-sky-50 rounded-lg p-4">
                  <div className="text-base text-slate-600 mb-1">Free Conversions Today</div>
                  <div className="text-3xl md:text-4xl font-bold text-sky-600">{freeRemaining || 0}</div>
                  <div className="text-sm text-slate-500">out of 20 daily</div>
                </div>
                <div className="text-base text-slate-600 bg-amber-50 rounded-lg p-4 border border-amber-200">
                  <strong>Want more conversions?</strong> Subscribe for 1000 conversions per month for just $1.98/month.
                </div>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="rounded-lg border bg-white p-6 text-center">
          <div className="text-slate-600 mb-4 text-lg">Please sign in to view your account information</div>
          <button
            onClick={() => window.location.href = '/convert'}
            className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-5 py-3 text-white hover:bg-sky-700 text-base"
          >
            Go to Convert Page
          </button>
        </div>
      )}
    </div>
  );
}
