'use client';

import React from 'react';
import { FORMAT_GROUPS } from '../../lib/conversionConfig';
import { Job } from '../../lib/conversionApi';

type Props = {
  target: string;
  onSelect: (format: string) => void;
  jobs: Job[];
};

/** Output format selection grid plus PNG/auto-resize advisories for queued images. */
export function FormatPicker({ target, onSelect, jobs }: Props) {
  const queuedJobs = jobs.filter(j => j.status === 'queued' && !j.isGif);
  const maxDimension = queuedJobs.length > 0
    ? Math.max(...queuedJobs.map(j => Math.max(j.width || 0, j.height || 0)))
    : 0;

  return (
    <div role="group" aria-labelledby="format-label">
      <label id="format-label" className="block text-sm font-medium text-slate-700 mb-3">Convert to:</label>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
        {Object.entries(FORMAT_GROUPS).map(([groupName, formats]) => (
          <div key={groupName} className="space-y-1.5">
            <div className="text-xs font-medium text-slate-500 px-1" aria-label={`${groupName} formats`}>{groupName}</div>
            <div className="space-y-1" role="group" aria-label={groupName}>
              {formats.map(fmt => (
                <button
                  key={fmt}
                  onClick={() => onSelect(fmt)}
                  aria-pressed={target === fmt}
                  aria-label={`Convert to ${fmt.toUpperCase()} format`}
                  className={`w-full px-3 py-2 rounded-md text-sm font-medium transition-all ${
                    target === fmt
                      ? 'bg-sky-600 text-white shadow-sm'
                      : 'bg-slate-50 text-slate-700 hover:bg-slate-100 border border-slate-200'
                  }`}
                >
                  {fmt.toUpperCase()}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* PNG warning for large images */}
      {target === 'png' && maxDimension > 4000 && (
        <div className="text-xs text-red-700 bg-red-50 border border-red-200 rounded p-3 mt-3">
          <div className="font-semibold mb-1">⚠️ PNG not supported for very large images</div>
          <div className="mb-2">
            Your image is {maxDimension}px. Even after auto-resize to 1920px, PNG compression is too slow.
          </div>
          <div className="font-medium">
            Recommended: <span className="text-emerald-600">WebP</span> (best quality/size),
            JPEG (universal), or AVIF (modern)
          </div>
        </div>
      )}

      {/* Auto-resize info for large images */}
      {maxDimension > 1920 && (
        <div className="text-xs text-blue-700 bg-blue-50 border border-blue-200 rounded p-3 mt-3">
          <div className="font-semibold mb-1">ℹ️ Auto-resize enabled</div>
          <div>
            Your {maxDimension}px image will be resized to 1920px before conversion to fit server limits.
            For custom sizes, use the resize option below.
          </div>
        </div>
      )}
    </div>
  );
}
