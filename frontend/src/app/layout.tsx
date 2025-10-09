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
  description: "Convert images across formats. 20 free/day, $1.98/month for 1000 conversions.",
  robots: "index, follow",
  icons: {
    icon: [
      { url: '/favicon.ico', sizes: 'any' },
      { url: '/icon.png', type: 'image/png', sizes: '192x192' },
    ],
    apple: '/apple-icon.png',
    other: [
      {
        rel: 'icon',
        type: 'image/png',
        sizes: '192x192',
        url: '/icon.png',
      },
    ],
  },
  openGraph: {
    title: "RaimonVibe Image Converter",
    description: "Convert images across formats. 20 free/day, $1.98/month for 1000 conversions.",
    url: "https://change-my-com-v2.vercel.app",
    siteName: "RaimonVibe Image Converter",
    images: [
      {
        url: 'https://change-my-com-v2.vercel.app/icon.png',
        width: 192,
        height: 192,
        alt: 'RaimonVibe Image Converter Logo',
      },
    ],
    locale: 'en_US',
    type: 'website',
  },
  twitter: {
    card: 'summary',
    title: "RaimonVibe Image Converter",
    description: "Convert images across formats. 20 free/day, $1.98/month for 1000 conversions.",
    images: ['https://change-my-com-v2.vercel.app/icon.png'],
  },
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
                <AuthButtons />
              </div>
              
              {/* Mobile Navigation */}
              <div className="md:hidden mt-3 pt-3 border-t border-slate-200">
                <nav className="flex gap-3 text-base">
                  <Link href="/home" className="text-slate-600 hover:text-sky-700">Home</Link>
                  <Link href="/convert" className="text-slate-600 hover:text-sky-700">Convert</Link>
                  <Link href="/billing" className="text-slate-600 hover:text-sky-700">Pricing</Link>
                  <Link href="/account" className="text-slate-600 hover:text-sky-700">Account</Link>
                  <Link href="/contact" className="text-slate-600 hover:text-sky-700">Contact</Link>
                </nav>
              </div>
            </div>
          </header>
          <main className="max-w-5xl mx-auto px-4 sm:px-6 py-4 sm:py-6">{children}</main>
          <footer className="border-t bg-white mt-12">
            <div className="max-w-5xl mx-auto px-4 py-6">
              <div className="flex flex-col sm:flex-row justify-between items-center gap-4 text-base text-slate-600">
                <div>
                  © {new Date().getFullYear()}{' '}
                  <a
                    href="https://www.raimonvibe.com/"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-sky-700 transition-colors"
                  >
                    RaimonVibe
                  </a>
                  . All rights reserved.
                </div>
                <div className="flex flex-wrap justify-center sm:justify-end gap-4 sm:gap-6">
                  <Link
                    href="/contact"
                    className="hover:text-sky-700 transition-colors"
                  >
                    Contact
                  </Link>
                  <Link
                    href="/privacy"
                    className="hover:text-sky-700 transition-colors"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Privacy Notice
                  </Link>
                  <Link
                    href="/legal"
                    className="hover:text-sky-700 transition-colors"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Legal Notice
                  </Link>
                </div>
              </div>
            </div>
          </footer>
        </Providers>
      </body>
    </html>
  );
}
