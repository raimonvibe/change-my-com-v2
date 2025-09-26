import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import Link from "next/link";
import { Providers } from "./providers";
import { AuthButtons } from "../components/AuthButtons";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "RaimonVibe Image Converter",
  description: "Convert images across formats. 20 free/day, $1.98/month unlimited.",
  icons: {
    icon: [
      { url: '/favicon.ico', sizes: 'any' },
      { url: '/favicon.ico', type: 'image/x-icon' }
    ],
    shortcut: '/favicon.ico',
    apple: '/favicon.ico',
  },
  robots: "index, follow",
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${geistSans.variable} ${geistMono.variable} bg-sky-50 text-slate-800 antialiased`}>
        <Providers>
          <header className="border-b bg-white">
            <div className="max-w-5xl mx-auto px-4 py-3">
              {/* Desktop Header */}
              <div className="hidden md:flex items-center justify-between">
                <Link href="/" className="font-semibold text-sky-700">Image Converter</Link>
                <div className="flex items-center gap-4">
                  <nav className="flex gap-4 text-sm">
                    <Link href="/home" className="text-slate-600 hover:text-sky-700">Home</Link>
                    <Link href="/convert" className="text-slate-600 hover:text-sky-700">Convert</Link>
                    <Link href="/billing" className="text-slate-600 hover:text-sky-700">Pricing</Link>
                    <Link href="/account" className="text-slate-600 hover:text-sky-700">Account</Link>
                  </nav>
                  <AuthButtons />
                </div>
              </div>
              
              {/* Mobile Header */}
              <div className="md:hidden flex items-center justify-between">
                <Link href="/" className="font-semibold text-sky-700 text-lg">Converter</Link>
                <AuthButtons />
              </div>
              
              {/* Mobile Navigation */}
              <div className="md:hidden mt-3 pt-3 border-t border-slate-200">
                <nav className="flex gap-3 text-sm">
                  <Link href="/home" className="text-slate-600 hover:text-sky-700">Home</Link>
                  <Link href="/convert" className="text-slate-600 hover:text-sky-700">Convert</Link>
                  <Link href="/billing" className="text-slate-600 hover:text-sky-700">Pricing</Link>
                  <Link href="/account" className="text-slate-600 hover:text-sky-700">Account</Link>
                </nav>
              </div>
            </div>
          </header>
          <main className="max-w-5xl mx-auto px-4 sm:px-6 py-4 sm:py-6">{children}</main>
        </Providers>
      </body>
    </html>
  );
}
