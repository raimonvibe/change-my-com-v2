'use client';
// Trigger new deployment - TypeScript fixes applied
import React, { useEffect } from "react";
import { useSession } from "next-auth/react";
import { useAuthStore } from "../../store/useAuthStore";
import { API_URL } from "../../env";

export default function AccountPage() {
  const { data: session } = useSession();
  const setAuth = useAuthStore(s => s.setAuth);
  const email = useAuthStore(s => s.email);
  const freeRemaining = useAuthStore(s => s.freeRemaining);
  const paidCredits = useAuthStore(s => s.paidCredits);

  useEffect(() => {
    const fetchMe = async (retryCount = 0) => {
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
        };
        console.log('User data received:', data);
        setAuth({
          authenticated: data.authenticated,
          email: data.email,
          freeRemaining: data.freeRemaining,
          paidCredits: data.paidCredits
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

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-sky-800">Your Account</h1>
      
      {session?.user ? (
        <div className="space-y-4">
          {/* User Info Card */}
          <div className="rounded-lg border bg-white p-6">
            <h2 className="text-lg font-medium text-slate-800 mb-4">Account Information</h2>
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-sky-100 rounded-full flex items-center justify-center">
                  <span className="text-sky-600 font-medium text-sm">
                    {email ? email.charAt(0).toUpperCase() : 'U'}
                  </span>
                </div>
                <div>
                  <div className="font-medium text-slate-800">{email || 'Loading...'}</div>
                  <div className="text-sm text-slate-500">Google Account</div>
                </div>
              </div>
            </div>
          </div>

          {/* Credits Card */}
          <div className="rounded-lg border bg-white p-6">
            <h2 className="text-lg font-medium text-slate-800 mb-4">Usage & Credits</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="bg-sky-50 rounded-lg p-4">
                <div className="text-sm text-slate-600 mb-1">Free Conversions Today</div>
                <div className="text-2xl font-bold text-sky-600">{freeRemaining || 0}</div>
                <div className="text-xs text-slate-500">out of 20 daily</div>
              </div>
              <div className="bg-emerald-50 rounded-lg p-4">
                <div className="text-sm text-slate-600 mb-1">Paid Credits</div>
                <div className="text-2xl font-bold text-emerald-600">{paidCredits || 0}</div>
                <div className="text-xs text-slate-500">unlimited conversions</div>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="rounded-lg border bg-white p-6 text-center">
          <div className="text-slate-600 mb-4">Please sign in to view your account information</div>
          <button 
            onClick={() => window.location.href = '/convert'}
            className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-4 py-2 text-white hover:bg-sky-700"
          >
            Go to Convert Page
          </button>
        </div>
      )}
    </div>
  );
}
