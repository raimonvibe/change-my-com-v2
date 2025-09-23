import React from "react";
import { Camera, CheckCircle2 } from "lucide-react";
import Link from "next/link";

export default function HomePage() {
  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-sky-800 flex items-center gap-2">
          <Camera className="text-sky-600" /> Image Converter
        </h1>
      </div>
      <p className="text-slate-600">Convert images between PNG, JPG, WEBP, AVIF, HEIC, TIFF, BMP, GIF, SVG and more. 20 free conversions per day. $1.98/month for unlimited conversions.</p>
      <div className="grid md:grid-cols-3 gap-4">
        {[
          "Classy and trustworthy light blue UI",
          "Google login, secure Stripe checkout",
          "Fast conversions with multiple formats"
        ].map((t) => (
          <div key={t} className="rounded-lg border bg-white p-4 flex gap-3">
            <CheckCircle2 className="text-sky-600 mt-1" />
            <div className="text-slate-700">{t}</div>
          </div>
        ))}
      </div>
      <div>
        <Link href="/convert" className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-4 py-2 text-white hover:bg-sky-700">
          Try converter
        </Link>
      </div>
    </div>
  );
}

