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
        <button className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-4 sm:px-6 py-2.5 sm:py-3 text-white text-base sm:text-lg">
          <LogIn size={18} className="sm:w-5 sm:h-5" /> Continue with Google
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      {data?.user ? (
        <button onClick={() => signOut()} className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-4 sm:px-6 py-2.5 sm:py-3 text-white hover:bg-sky-700 text-base sm:text-lg">
          <LogOut size={18} className="sm:w-5 sm:h-5" /> Sign out
        </button>
      ) : (
        <button onClick={() => signIn('google')} className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-4 sm:px-6 py-2.5 sm:py-3 text-white hover:bg-sky-700 text-base sm:text-lg">
          <LogIn size={18} className="sm:w-5 sm:h-5" /> Continue with Google
        </button>
      )}
    </div>
  )
}
