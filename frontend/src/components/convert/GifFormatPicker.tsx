'use client';

import React from 'react';
import { GIF_ZIP_FORMATS } from '../../lib/conversionConfig';

type Props = {
  gifFormats: string[];
  onToggle: (format: string) => void;
};

/** Multi-format selection for GIF frame extraction (ZIP output). */
export function GifFormatPicker({ gifFormats, onToggle }: Props) {
  return (
    <div className="border-t pt-4" role="group" aria-labelledby="gif-formats-label">
      <div className="flex items-center gap-2 mb-3">
        <div className="text-sm font-medium text-slate-700">GIF Frame Extraction</div>
        <span className="text-xs bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full">Multiple Formats</span>
      </div>
      <p className="text-xs text-slate-600 mb-3">
        Select multiple formats to extract GIF frames. All frames will be converted and bundled in a ZIP file.
      </p>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
        {GIF_ZIP_FORMATS.map(fmt => (
          <button
            key={fmt}
            onClick={() => onToggle(fmt)}
            aria-pressed={gifFormats.includes(fmt)}
            aria-label={`${gifFormats.includes(fmt) ? 'Remove' : 'Add'} ${fmt.toUpperCase()} format for GIF extraction`}
            className={`px-3 py-2 rounded-md text-sm font-medium transition-all ${
              gifFormats.includes(fmt)
                ? 'bg-purple-600 text-white shadow-sm'
                : 'bg-slate-50 text-slate-700 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            {fmt.toUpperCase()}
          </button>
        ))}
      </div>
      {gifFormats.length === 0 && (
        <div className="text-xs text-amber-600 bg-amber-50 rounded p-2 mt-2">
          ⚠️ Please select at least one format for GIF conversion
        </div>
      )}
    </div>
  );
}
