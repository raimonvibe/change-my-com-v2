import React from "react";
import { Camera, CheckCircle2 } from "lucide-react";
import Link from "next/link";
import type { Metadata } from "next";
import { WhoItsFor } from "../../components/WhoItsFor";

export const metadata: Metadata = {
  title: "Free Image Converter | Optimize for Web Performance",
  description: "Convert JPG, PNG, WebP images with quality control. Perfect for web developers, designers, and bloggers. 20 free conversions daily.",
};

export default function HomePage() {
  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-sky-800 flex items-center gap-2">
          <Camera className="text-sky-600" /> Image Converter
        </h1>
      </div>
      <p className="text-slate-600 text-lg">Convert and optimize images for faster websites instantly</p>
      <div className="grid md:grid-cols-3 gap-4">
        {[
          "Batch convert up to 20 images at once",
          "WebP for 30% smaller file sizes",
          "Quality control and image sharpening"
        ].map((t) => (
          <div key={t} className="rounded-lg border bg-white p-4 flex gap-3">
            <CheckCircle2 className="text-sky-600 mt-1 flex-shrink-0" />
            <div className="text-slate-700">{t}</div>
          </div>
        ))}
      </div>
      <div className="space-y-3">
        <Link href="/convert" className="inline-flex items-center gap-2 rounded-md bg-sky-600 px-4 py-2 text-white hover:bg-sky-700">
          Try converter
        </Link>
        <p className="text-sm text-slate-500">Trusted by web developers, designers, and content creators</p>
      </div>

      <WhoItsFor />
    </div>
  );
}

