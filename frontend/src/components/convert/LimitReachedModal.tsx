'use client';

import React from 'react';
import { AlertTriangle, CalendarDays } from 'lucide-react';
import { PAYMENTS_ENABLED } from '../../lib/paymentsConfig';

type Props = {
  open: boolean;
  /** Whether the visitor is signed in. */
  signedIn: boolean;
  /** True when localStorage indicates a subscription but the session expired. */
  staleSubscription: boolean;
  onClose: () => void;
  onSignIn: () => void;
  onGoToBilling: () => void;
};

/** Modal shown when the daily/credit conversion limit is reached (402). */
export function LimitReachedModal({ open, signedIn, staleSubscription, onClose, onSignIn, onGoToBilling }: Props) {
  if (!open) return null;

  const softFreeOnly = !staleSubscription && !PAYMENTS_ENABLED;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="limit-modal-title"
      aria-describedby="limit-modal-description"
      className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div className="relative max-w-md w-full bg-white rounded-lg shadow-xl p-6" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-start gap-4">
          <div className="flex-shrink-0">
            {softFreeOnly ? (
              <div className="w-12 h-12 bg-sky-100 rounded-full flex items-center justify-center" aria-hidden="true">
                <CalendarDays className="w-6 h-6 text-sky-700" aria-hidden="true" />
              </div>
            ) : (
              <div className="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center" aria-hidden="true">
                <AlertTriangle className="w-6 h-6 text-amber-600" aria-hidden="true" />
              </div>
            )}
          </div>
          <div className="flex-1">
            <h3 id="limit-modal-title" className="text-lg font-semibold text-slate-900 mb-2">
              {staleSubscription
                ? 'Session expired'
                : softFreeOnly
                  ? signedIn
                    ? "That's all for today"
                    : 'Thanks for trying Change-My'
                  : 'Conversion limit reached'}
            </h3>
            <p id="limit-modal-description" className="text-slate-600 mb-4 leading-relaxed">
              {staleSubscription ? (
                <>You have an active subscription, but your session has expired. Sign in again to use your remaining monthly conversions.</>
              ) : signedIn ? (
                PAYMENTS_ENABLED ? (
                  <>You&apos;ve used all your conversions for today. Subscribe to get 1000 conversions per month for just $1.98/month.</>
                ) : (
                  <>You&apos;ve used today&apos;s free conversions. <span className="font-medium text-slate-800">Come back tomorrow</span>—your quota resets every day. No payment required.</>
                )
              ) : PAYMENTS_ENABLED ? (
                <>
                  You&apos;ve used the 20 free conversions for this session.
                  <span className="mt-2 block font-medium text-slate-800">Already subscribed? Sign in to use your monthly credits.</span>
                  New to Change-My? Sign in or subscribe for 1000 conversions/month at $1.98/month.
                </>
              ) : (
                <>
                  You&apos;ve used today&apos;s guest conversions (20 per day per browser session).
                  <span className="mt-2 block font-medium text-slate-800">Sign in with Google</span> for a personal daily quota that follows you across devices—still free, still 20 per day.
                </>
              )}
            </p>
            <div className="flex flex-col gap-3">
              {!signedIn && (
                <button
                  onClick={onSignIn}
                  aria-label="Continue with Google"
                  className="w-full inline-flex items-center justify-center gap-2 rounded-md px-4 py-3 text-white text-sm font-semibold bg-sky-600 hover:bg-sky-700"
                >
                  Continue with Google
                </button>
              )}
              {PAYMENTS_ENABLED && !signedIn && !staleSubscription && (
                <button
                  onClick={onGoToBilling}
                  aria-label="Go to billing page to subscribe"
                  className="w-full inline-flex items-center justify-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2.5 text-slate-700 hover:bg-slate-50 text-sm font-medium"
                >
                  I don&apos;t have an account — Subscribe
                </button>
              )}
              {!PAYMENTS_ENABLED && !signedIn && !staleSubscription && (
                <button
                  type="button"
                  onClick={onGoToBilling}
                  aria-label="Learn about plans and daily limits"
                  className="w-full inline-flex items-center justify-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-4 py-2.5 text-slate-800 hover:bg-slate-100 text-sm font-medium"
                >
                  How daily limits work
                </button>
              )}
              <button
                onClick={onClose}
                aria-label="Close limit modal"
                className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-slate-100 px-4 py-2.5 text-slate-600 hover:bg-slate-200 text-sm font-medium"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
