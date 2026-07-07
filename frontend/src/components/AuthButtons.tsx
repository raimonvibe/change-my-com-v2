'use client';
import React, { useSyncExternalStore } from "react";
import { signIn, signOut, useSession } from "next-auth/react";
import { LogOut } from "lucide-react";
import { useAuthStore } from "../store/useAuthStore";

const emptySubscribe = () => () => {};

export function AuthButtons() {
  const { data } = useSession();
  const reset = useAuthStore(s => s.reset);
  // Hydration-safe "mounted" flag: false during SSR/hydration, true after.
  const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);

  if (!mounted) {
    return (
      <div className="flex items-center gap-2">
        <button className="inline-flex items-center justify-center gap-2 rounded bg-white px-3 sm:px-4 py-2 text-gray-700 font-medium text-sm shadow-md hover:shadow-lg border border-gray-300 transition-shadow">
          <svg width="16" height="16" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
            <g fill="none" fillRule="evenodd">
              <path d="M17.64 9.205c0-.639-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4"/>
              <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z" fill="#34A853"/>
              <path d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332z" fill="#FBBC05"/>
              <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z" fill="#EA4335"/>
            </g>
          </svg>
          <span className="hidden sm:inline">Sign in with Google</span>
          <span className="sm:hidden">Sign in</span>
        </button>
      </div>
    );
  }

  const handleSignOut = () => {
    // Clear auth store to prevent stale subscription data
    reset();
    signOut();
  };

  return (
    <div className="flex items-center gap-2">
      {data?.user ? (
        <button onClick={handleSignOut} className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-3 sm:px-4 py-2 text-white hover:bg-sky-700 text-sm">
          <LogOut size={16} /> <span className="hidden sm:inline">Sign out</span>
        </button>
      ) : (
        <button onClick={() => signIn('google')} className="inline-flex items-center justify-center gap-2 rounded bg-white px-3 sm:px-4 py-2 text-gray-700 font-medium text-sm shadow-md hover:shadow-lg border border-gray-300 transition-shadow">
          <svg width="16" height="16" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
            <g fill="none" fillRule="evenodd">
              <path d="M17.64 9.205c0-.639-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4"/>
              <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z" fill="#34A853"/>
              <path d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332z" fill="#FBBC05"/>
              <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0 A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z" fill="#EA4335"/>
            </g>
          </svg>
          <span className="hidden sm:inline">Sign in with Google</span>
          <span className="sm:hidden">Sign in</span>
        </button>
      )}
    </div>
  )
}
