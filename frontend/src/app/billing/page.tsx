'use client';

import React, { useCallback, useState } from 'react';
import { useSession, signIn } from 'next-auth/react';
import { CreditCard } from 'lucide-react';
import { API_URL } from '../../env';

export default function BillingPage() {
  const { data: session, status } = useSession();
  const token = session?.idToken as string | undefined;

  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const buy = useCallback(async () => {
    // Check if user is authenticated before making request
    if (!session || !token) {
      setErr('Please sign in to subscribe');
      return;
    }

    setErr(null);
    setLoading(true);

    try {
      const params = new URLSearchParams({
        successUrl: `${window.location.origin}/account`,
        cancelUrl: window.location.href,
      });

      const res = await fetch(`${API_URL}/api/billing/checkout?${params.toString()}`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
      });

      if (!res.ok) {
        // Handle 403 Forbidden specifically
        if (res.status === 403) {
          throw new Error('Please sign in to subscribe');
        }
        const text = await res.text().catch(() => '');
        throw new Error(`Checkout failed: ${res.status} ${res.statusText}${text ? ` — ${text}` : ''}`);
      }

      let data: unknown;
      try {
        data = await res.json();
      } catch {
        throw new Error('Invalid JSON response from server.');
      }

      const url = (data as { url?: unknown })?.url;
      if (typeof url !== 'string' || !url) {
        throw new Error('Missing checkout URL in response.');
      }

      window.location.assign(url);
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : 'Unknown error';
      setErr(message);
      console.error('[billing] buy error:', e);
    } finally {
      setLoading(false);
    }
  }, [session, token]);

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-sky-800">Pricing</h1>

      <div className="rounded-lg border bg-white p-6">
        <div className="text-slate-700">20 free conversions per day.</div>
        <div className="text-slate-700 mb-4">$1.98/month for 1000 conversions per month (optional monthly renewal).</div>

        {status === 'loading' && (
          <div className="mb-4 p-3 bg-slate-50 border border-slate-200 rounded-md text-sm text-slate-600">
            Loading...
          </div>
        )}

        {!session && status !== 'loading' && (
          <div className="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-md text-sm text-blue-800">
            Please sign in to subscribe
          </div>
        )}

        <button
          onClick={!session ? () => signIn('google') : buy}
          disabled={loading || status === 'loading'}
          className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-4 py-2 text-white hover:bg-sky-700 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          <CreditCard size={16} />
          {loading ? 'Processing…' : status === 'loading' ? 'Loading...' : !session ? 'Sign in to Subscribe' : 'Subscribe'}
        </button>

        {err && (
          <p className="mt-3 text-sm text-red-600 break-words">
            {err}
          </p>
        )}
      </div>
    </div>
  );
}