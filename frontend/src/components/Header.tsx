'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Menu, X } from 'lucide-react';
import { AuthButtons } from './AuthButtons';

export function Header() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="border-b bg-white sticky top-0 z-50">
      <div className="max-w-5xl mx-auto px-4 py-3">
        {/* Desktop Header */}
        <div className="hidden md:flex items-center justify-between">
          <Link href="/" className="font-semibold text-sky-700 text-lg">Image Converter</Link>
          <div className="flex items-center gap-4">
            <nav className="flex gap-4 text-base">
              <Link href="/home" className="text-slate-600 hover:text-sky-700">Home</Link>
              <Link href="/convert" className="text-slate-600 hover:text-sky-700">Convert</Link>
              <Link href="/billing" className="text-slate-600 hover:text-sky-700">Pricing</Link>
              <Link href="/account" className="text-slate-600 hover:text-sky-700">Account</Link>
              <Link href="/contact" className="text-slate-600 hover:text-sky-700">Contact</Link>
            </nav>
            <AuthButtons />
          </div>
        </div>

        {/* Mobile Header */}
        <div className="md:hidden flex items-center justify-between">
          <Link href="/" className="font-semibold text-sky-700 text-lg">Converter</Link>
          <div className="flex items-center gap-3">
            <AuthButtons />
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 text-slate-600 hover:text-sky-700"
              aria-label="Toggle menu"
            >
              {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
            </button>
          </div>
        </div>

        {/* Mobile Navigation - Collapsible */}
        {mobileMenuOpen && (
          <div className="md:hidden mt-3 pt-3 border-t border-slate-200">
            <nav className="flex flex-col gap-2 text-base">
              <Link
                href="/home"
                className="text-slate-600 hover:text-sky-700 py-2 px-3 rounded hover:bg-slate-50"
                onClick={() => setMobileMenuOpen(false)}
              >
                Home
              </Link>
              <Link
                href="/convert"
                className="text-slate-600 hover:text-sky-700 py-2 px-3 rounded hover:bg-slate-50"
                onClick={() => setMobileMenuOpen(false)}
              >
                Convert
              </Link>
              <Link
                href="/billing"
                className="text-slate-600 hover:text-sky-700 py-2 px-3 rounded hover:bg-slate-50"
                onClick={() => setMobileMenuOpen(false)}
              >
                Pricing
              </Link>
              <Link
                href="/account"
                className="text-slate-600 hover:text-sky-700 py-2 px-3 rounded hover:bg-slate-50"
                onClick={() => setMobileMenuOpen(false)}
              >
                Account
              </Link>
              <Link
                href="/contact"
                className="text-slate-600 hover:text-sky-700 py-2 px-3 rounded hover:bg-slate-50"
                onClick={() => setMobileMenuOpen(false)}
              >
                Contact
              </Link>
            </nav>
          </div>
        )}
      </div>
    </header>
  );
}
