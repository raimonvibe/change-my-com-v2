'use client';
// Trigger new deployment - TypeScript fixes applied - v2
import React, { useState, useEffect } from "react";
import { useSession } from "next-auth/react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { useAuthStore } from "../../store/useAuthStore";
import { useUserData } from "../../hooks/useUserData";
import { API_URL } from "../../env";

export default function AccountPage() {
  const router = useRouter();
  const { data: session } = useSession();
  const setAuth = useAuthStore(s => s.setAuth);
  const email = useAuthStore(s => s.email);
  const freeRemaining = useAuthStore(s => s.freeRemaining);
  const paidCredits = useAuthStore(s => s.paidCredits);
  const autoRenewal = useAuthStore(s => s.autoRenewal);
  const [toggleLoading, setToggleLoading] = useState(false);
  const [toggleError, setToggleError] = useState<string | null>(null);

  // Use shared hook for auth state synchronization
  const { refetch } = useUserData();

  // Force fresh data fetch on mount to ensure autoRenewal is synced
  useEffect(() => {
    refetch();
  }, [refetch]);

  const toggleAutoRenewal = async () => {
    if (!session?.idToken) return;

    setToggleLoading(true);
    setToggleError(null);
    try {
      const res = await fetch(`${API_URL}/api/user/toggle-auto-renewal`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${session.idToken}` },
      });

      const data = await res.json().catch(() => ({})) as { success?: boolean; autoRenewal?: boolean; error?: string };
      if (res.ok && data.success) {
        setAuth({ autoRenewal: data.autoRenewal });
        await refetch();
      } else {
        const message = data.error || `Request failed (${res.status})`;
        setToggleError(message);
      }
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Network or server error';
      setToggleError(message);
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
              <div className="flex items-center gap-3 sm:gap-4">
                {session.user.image ? (
                  <Image
                    src={session.user.image}
                    alt="Profile"
                    width={80}
                    height={80}
                    className="w-14 h-14 sm:w-16 sm:h-16 md:w-20 md:h-20 rounded-full flex-shrink-0"
                  />
                ) : (
                  <div className="w-14 h-14 sm:w-16 sm:h-16 md:w-20 md:h-20 bg-sky-100 rounded-full flex items-center justify-center flex-shrink-0">
                    <span className="text-sky-600 font-medium text-xl sm:text-2xl md:text-3xl">
                      {email ? email.charAt(0).toUpperCase() : 'U'}
                    </span>
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="font-medium text-slate-800 text-lg break-words">{email || 'Loading...'}</div>
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
                  <div className="text-sm text-emerald-600 mt-2">1000 conversions per subscription. Auto-renewal adds 1000 monthly.</div>
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
                      type="button"
                      role="switch"
                      aria-label={autoRenewal ? 'Auto-renewal on; turn off' : 'Auto-renewal off; turn on'}
                      aria-checked={autoRenewal}
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
                        aria-hidden
                      />
                    </button>
                  </div>
                  {toggleError && (
                    <p role="alert" className="mt-3 text-sm text-red-600">
                      {toggleError}
                    </p>
                  )}
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
            onClick={() => router.push('/')}
            className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-5 py-3 text-white hover:bg-sky-700 text-base"
          >
            Go to Convert Page
          </button>
        </div>
      )}
    </div>
  );
}
