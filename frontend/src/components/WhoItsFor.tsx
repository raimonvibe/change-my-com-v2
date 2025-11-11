import React from "react";
import { CheckCircle } from "lucide-react";

export function WhoItsFor() {
  const audiences = [
    {
      icon: "👨‍💻",
      title: "Web Developers & Designers",
      description: "Optimize images for web performance without sacrificing quality. Batch convert to WebP for faster page loads.",
      benefits: [
        "Batch process up to 10 images/min",
        "WebP for modern browsers",
        "Quality control & compression"
      ]
    },
    {
      icon: "✍️",
      title: "Content Creators & Bloggers",
      description: "Perfect images for your blog. Fast, sharp, and ready to publish across all platforms.",
      benefits: [
        "Optimize for blog loading speed",
        "Sharpen images automatically",
        "Multiple format support"
      ]
    }
  ];

  return (
    <section className="space-y-8">
      <h2 className="text-2xl font-semibold text-sky-800 text-center">Perfect For</h2>
      <div className="grid md:grid-cols-2 gap-6">
        {audiences.map((audience) => (
          <div
            key={audience.title}
            className="rounded-lg border bg-white p-6 space-y-4 shadow-sm hover:shadow-md transition-shadow"
          >
            <div className="flex items-center gap-3">
              <span className="text-3xl" role="img" aria-label={audience.title}>
                {audience.icon}
              </span>
              <h3 className="text-lg font-semibold text-sky-800">{audience.title}</h3>
            </div>
            <p className="text-slate-600">{audience.description}</p>
            <ul className="space-y-2">
              {audience.benefits.map((benefit) => (
                <li key={benefit} className="flex items-start gap-2">
                  <CheckCircle className="text-sky-600 mt-0.5 flex-shrink-0" size={18} />
                  <span className="text-slate-700 text-sm">{benefit}</span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}
