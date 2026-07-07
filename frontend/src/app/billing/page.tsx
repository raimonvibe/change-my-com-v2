'use client';

import React, { useCallback, useState } from 'react';
import Link from 'next/link';
import { useSession, signIn } from 'next-auth/react';
import { CalendarDays, CreditCard, Sparkles } from 'lucide-react';
import { apiFetch } from '../../lib/apiClient';
import { PAYMENTS_ENABLED } from '../../lib/paymentsConfig';

export default function BillingPage() {
  const { data: session, status } = useSession();
  const token = session?.idToken as string | undefined;

  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  /** Stripe Checkout session creation — kept intact; UI/API gate when payments are paused. */
  const buy = useCallback(async () => {
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

      // retries: 0 — checkout session creation should never be auto-retried
      const res = await apiFetch(`/api/billing/checkout?${params.toString()}`, {
        method: 'POST',
        credentials: 'include',
        token,
        headers: { 'Content-Type': 'application/json' },
        retries: 0,
      });

      if (!res.ok) {
        if (res.status === 403) {
          throw new Error('Please sign in to subscribe');
        }
        if (res.status === 503) {
          let msg = 'Paid subscriptions are temporarily unavailable.';
          try {
            const body = (await res.json()) as { error?: string };
            if (typeof body.error === 'string' && body.error) msg = body.error;
          } catch {
            /* use default */
          }
          throw new Error(msg);
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

  if (!PAYMENTS_ENABLED) {
    return (
      <div className="space-y-6 max-w-2xl">
        <div>
          <h1 className="text-2xl font-semibold text-sky-800">Plans &amp; usage</h1>
          <p className="mt-2 text-slate-600 text-base leading-relaxed">
            Change-My is free to use with a generous daily limit. No credit card required.
          </p>
        </div>

        <div className="rounded-xl border border-sky-100 bg-gradient-to-b from-sky-50/80 to-white p-6 shadow-sm">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-sky-100 text-sky-700" aria-hidden>
              <Sparkles className="h-6 w-6" strokeWidth={1.75} />
            </div>
            <div className="min-w-0 flex-1 space-y-3">
              <h2 className="text-lg font-semibold text-slate-900">Free plan</h2>
              <ul className="space-y-2 text-slate-700 text-base">
                <li className="flex gap-2">
                  <span className="text-sky-600 font-semibold" aria-hidden>✓</span>
                  <span>
                    <strong className="text-slate-800">20 image conversions</strong> per day
                  </span>
                </li>
                <li className="flex gap-2">
                  <CalendarDays className="h-5 w-5 shrink-0 text-sky-600 mt-0.5" aria-hidden />
                  <span>Your limit <strong className="text-slate-800">resets every calendar day</strong> so you can come back tomorrow.</span>
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div className="rounded-lg border border-slate-200 bg-slate-50/80 px-4 py-3 text-sm text-slate-700 leading-relaxed">
          <strong className="text-slate-800">Paid upgrades</strong>{' '}are paused for now. We&apos;re focused on making the free experience great. Thanks for understanding.
        </div>

        {status === 'loading' && (
          <div className="rounded-lg border border-slate-200 bg-white p-4 text-sm text-slate-600">Loading…</div>
        )}

        {session && (
          <div className="rounded-lg border border-emerald-100 bg-emerald-50/60 p-5">
            <p className="text-base font-medium text-emerald-900">You&apos;re signed in</p>
            <p className="mt-1 text-sm text-emerald-800/90 leading-relaxed">
              Use the Convert page any time—your remaining conversions for today are shown on your account.
            </p>
            <Link
              href="/"
              className="mt-4 inline-flex items-center justify-center rounded-md bg-sky-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-sky-700 transition-colors"
            >
              Go to Convert
            </Link>
          </div>
        )}

        {!session && status !== 'loading' && (
          <div className="rounded-lg border border-slate-200 bg-white p-5 space-y-4">
            <p className="text-base text-slate-700 leading-relaxed">
              <strong className="text-slate-900">Sign in with Google</strong> to get a personal daily quota that follows you across devices—same 20 conversions per day, tied to your account.
            </p>
            <button
              type="button"
              onClick={() => signIn('google')}
              className="inline-flex w-full sm:w-auto items-center justify-center gap-2 rounded-md bg-sky-600 px-5 py-3 text-sm font-semibold text-white hover:bg-sky-700 transition-colors"
            >
              Continue with Google
            </button>
          </div>
        )}

        {err && (
          <p className="text-sm text-red-600 break-words" role="alert">
            {err}
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4 max-w-2xl">
      <div>
        <h1 className="text-2xl font-semibold text-sky-800">Pricing</h1>
        <p className="mt-1 text-slate-600 text-sm">Simple plan: free daily conversions or a monthly bundle.</p>
      </div>

      <div className="rounded-lg border bg-white p-6 shadow-sm">
        <div className="text-slate-700">20 free conversions per day.</div>
        <div className="text-slate-700 mb-4">
          $1.98/month for 1000 conversions per month (optional monthly renewal).
        </div>

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
