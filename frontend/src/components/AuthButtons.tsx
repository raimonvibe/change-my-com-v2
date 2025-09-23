'use client';
import React, { useState, useEffect } from "react";
import { signIn, signOut, useSession } from "next-auth/react";
import { LogIn, LogOut } from "lucide-react";

export function AuthButtons() {
  const { data } = useSession();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return (
      <div className="flex items-center gap-2">
        <button className="inline-flex items-center gap-1 rounded-md bg-sky-600 px-2 sm:px-3 py-1.5 text-white text-sm">
          <LogIn size={14} /> <span className="hidden sm:inline">Sign in with Google</span><span className="sm:hidden">Sign in</span>
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      {data?.user ? (
        <button onClick={() => signOut()} className="inline-flex items-center gap-1 rounded-md bg-sky-600 px-2 sm:px-3 py-1.5 text-white hover:bg-sky-700 text-sm">
          <LogOut size={14} /> <span className="hidden sm:inline">Sign out</span>
        </button>
      ) : (
        <button onClick={() => signIn('google')} className="inline-flex items-center gap-1 rounded-md bg-sky-600 px-2 sm:px-3 py-1.5 text-white hover:bg-sky-700 text-sm">
          <LogIn size={14} /> <span className="hidden sm:inline">Sign in with Google</span><span className="sm:hidden">Sign in</span>
        </button>
      )}
    </div>
  )
}
