import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import Link from "next/link";
import Image from "next/image";
import Script from "next/script";
import { Providers } from "./providers";
import { ErrorBoundary } from "../components/ErrorBoundary";
import { Header } from "../components/Header";
import { headers } from "next/headers";

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
    canonical: 'https://www.change-my.com/convert',
  },
  openGraph: {
    title: "Free Online Image Converter - Convert, Sharpen & Optimize Images",
    description: "Free online image converter with quality control and sharpening. Convert images between JPG, PNG, WebP, GIF, and more. Adjust quality and apply sharpening filters. 20 free conversions daily.",
    url: "https://www.change-my.com/convert",
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

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // Get the nonce from the request headers set by middleware
  const headersList = await headers();
  const nonce = headersList.get('x-nonce') || '';

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
      "Support for AVIF, HEIC, ICO"
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
          nonce={nonce}
        />
      </head>
      <body className={`${geistSans.variable} ${geistMono.variable} bg-sky-50 text-slate-800 antialiased`}>
        <Providers>
          <Header />
          <main className="max-w-5xl mx-auto px-4 sm:px-6 py-4 sm:py-6">
            <ErrorBoundary>{children}</ErrorBoundary>
          </main>
          
          {/* Product Hunt Badge and Card */}
          <div className="max-w-5xl mx-auto px-4 py-8">
            <div className="flex flex-col items-center gap-6">
              {/* Product Hunt Badge */}
              <div className="flex justify-center">
                <a 
                  href="https://www.producthunt.com/products/raimonvibe-image-converter?embed=true&utm_source=badge-featured&utm_medium=badge&utm_source=badge-raimonvibe&#0045;image&#0045;converter" 
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <Image 
                    src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=1026042&theme=light&t=1760427610015" 
                    alt="RaimonVibe Image Converter - Convert, sharpen & optimize jpg, png, webp | raimonvibe | Product Hunt" 
                    width={250} 
                    height={54} 
                    className="rounded-lg shadow-sm hover:shadow-md transition-shadow"
                  />
                </a>
              </div>
              
              {/* Product Hunt Card */}
              <div className="w-full max-w-[500px]">
                <iframe 
                  style={{ border: 'none' }} 
                  src="https://cards.producthunt.com/cards/products/1116424" 
                  width="100%" 
                  height="405" 
                  frameBorder="0" 
                  scrolling="no" 
                  allowFullScreen
                  className="rounded-lg shadow-sm"
                />
              </div>
            </div>
          </div>
          
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
        <Script
          src='https://www.noupe.com/embed/0199e88a388375d2b1949147461462dc3c08.js'
          strategy="lazyOnload"
        />
      </body>
    </html>
  );
}
