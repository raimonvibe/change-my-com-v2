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
  metadataBase: new URL('https://www.change-my.com'),
  title: {
    default: "Free Online Image Converter - Convert, Sharpen & Optimize JPG, PNG, WebP | RaimonVibe",
    template: "%s | RaimonVibe Image Converter"
  },
  description: "Free online image converter with quality control and sharpening. Convert images between JPG, PNG, WebP, GIF, and more. Adjust quality, apply sharpening filters. 20 free conversions daily, or subscribe for 1000 monthly conversions at $1.98/month.",
  keywords: [
    "image converter",
    "convert images online",
    "jpg to png",
    "png to jpg",
    "webp converter",
    "image format converter",
    "free image converter",
    "online image tool",
    "convert pictures",
    "image transformation",
    "photo converter",
    "gif converter",
    "batch image converter",
    "image sharpening",
    "sharpen images online",
    "image quality control",
    "compress images",
    "image optimizer"
  ],
  authors: [{ name: "RaimonVibe", url: "https://www.raimonvibe.com" }],
  creator: "RaimonVibe",
  publisher: "RaimonVibe",
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      'max-video-preview': -1,
      'max-image-preview': 'large',
      'max-snippet': -1,
    },
  },
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
  alternates: {
    canonical: 'https://www.change-my.com',
  },
  openGraph: {
    title: "Free Online Image Converter - Convert, Sharpen & Optimize Images",
    description: "Free online image converter with quality control and sharpening. Convert images between JPG, PNG, WebP, GIF, and more. Adjust quality and apply sharpening filters. 20 free conversions daily.",
    url: "https://www.change-my.com",
    siteName: "RaimonVibe Image Converter",
    images: [
      {
        url: 'https://www.change-my.com/icon.png',
        width: 192,
        height: 192,
        alt: 'RaimonVibe Image Converter Logo',
      },
    ],
    locale: 'en_US',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: "Free Online Image Converter - Convert, Sharpen & Optimize Images",
    description: "Free online image converter with quality control and sharpening. Convert, optimize, and sharpen images instantly. 20 free conversions daily.",
    images: ['https://www.change-my.com/icon.png'],
  },
  category: 'technology',
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
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "WebApplication",
    "name": "RaimonVibe Image Converter",
    "description": "Free online image converter with quality control and sharpening. Convert images between JPG, PNG, WebP, GIF, and more formats with advanced optimization.",
    "url": "https://www.change-my.com",
    "applicationCategory": "MultimediaApplication",
    "operatingSystem": "Any",
    "offers": {
      "@type": "Offer",
      "price": "1.98",
      "priceCurrency": "USD",
      "description": "1000 image conversions per month"
    },
    "featureList": [
      "Convert JPG to PNG",
      "Convert PNG to JPG",
      "Convert WebP images",
      "Convert GIF images",
      "Image quality control (1-100%)",
      "Image sharpening (0-200%)",
      "Unsharp mask filter",
      "20 free conversions daily",
      "Batch image conversion",
      "Support for AVIF, HEIC, TIFF, BMP, ICO"
    ],
    "screenshot": "https://www.change-my.com/icon.png",
    "publisher": {
      "@type": "Organization",
      "name": "RaimonVibe",
      "url": "https://www.raimonvibe.com"
    }
  };

  return (
    <html lang="en">
      <head>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      </head>
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
                <nav className="grid grid-cols-2 gap-3 text-base">
                  <Link href="/home" className="text-slate-600 hover:text-sky-700 py-2 text-center">Home</Link>
                  <Link href="/convert" className="text-slate-600 hover:text-sky-700 py-2 text-center">Convert</Link>
                  <Link href="/billing" className="text-slate-600 hover:text-sky-700 py-2 text-center">Pricing</Link>
                  <Link href="/account" className="text-slate-600 hover:text-sky-700 py-2 text-center">Account</Link>
                  <Link href="/contact" className="text-slate-600 hover:text-sky-700 py-2 text-center col-span-2">Contact</Link>
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
