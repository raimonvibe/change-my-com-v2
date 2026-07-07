'use client';

import React from 'react';

type Props = {
  url: string | null;
  onClose: () => void;
};

/** Fullscreen preview of a converted image. */
export function PreviewModal({ url, onClose }: Props) {
  if (!url) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Image preview"
      className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div className="relative max-w-7xl max-h-[90vh] bg-white rounded-lg overflow-hidden" onClick={(e) => e.stopPropagation()}>
        <div className="absolute top-2 right-2 z-10">
          <button
            onClick={onClose}
            aria-label="Close preview"
            className="bg-white rounded-full p-2 shadow-lg hover:bg-gray-100"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={url}
          alt="Converted image preview"
          className="max-w-full max-h-[90vh] object-contain"
        />
      </div>
    </div>
  );
}
