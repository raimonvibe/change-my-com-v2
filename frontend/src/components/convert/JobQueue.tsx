'use client';

import React from 'react';
import { AlertTriangle, CheckCircle, Download, Eye, Trash2 } from 'lucide-react';
import { Job } from '../../lib/conversionApi';

type Props = {
  jobs: Job[];
  target: string;
  gifFormats: string[];
  onRemove: (jobId: string) => void;
  onClearAll: () => void;
  onPreview: (url: string) => void;
};

/** Queue status banner plus the per-job list with progress, errors and downloads. */
export function JobQueue({ jobs, target, gifFormats, onRemove, onClearAll, onPreview }: Props) {
  if (jobs.length === 0) return null;

  const queued = jobs.filter(j => j.status === 'queued').length;
  const running = jobs.filter(j => j.status === 'running').length;
  const done = jobs.filter(j => j.status === 'done').length;
  const error = jobs.filter(j => j.status === 'error').length;
  const total = jobs.length;

  let message: string;
  if (running > 0) {
    message = `Converting ${running} of ${total}… ${done > 0 ? `${done} done` : ''}`.trim();
  } else if (queued === total) {
    message = `${total} file${total === 1 ? '' : 's'} in queue`;
  } else if (done > 0 || error > 0) {
    const parts = [];
    if (done > 0) parts.push(`${done} done`);
    if (error > 0) parts.push(`${error} failed`);
    if (queued > 0) parts.push(`${queued} queued`);
    message = parts.join(' • ');
  } else {
    message = `${total} file${total === 1 ? '' : 's'} uploaded`;
  }

  return (
    <div className="space-y-3">
      <div
        role="status"
        aria-live="polite"
        className="rounded-lg border border-sky-200 bg-gradient-to-r from-sky-50 via-sky-100/80 to-sky-50 px-4 py-3 flex items-center justify-between gap-3 shadow-sm"
      >
        <div className="flex items-center gap-2 min-w-0">
          {running > 0 && (
            <div className="animate-spin h-4 w-4 border-2 border-sky-600 border-t-transparent rounded-full flex-shrink-0" aria-hidden="true" />
          )}
          <span className="text-sm font-medium text-sky-900 truncate">{message}</span>
        </div>
        <button
          type="button"
          onClick={onClearAll}
          className="text-xs text-sky-600 hover:text-sky-800 hover:bg-sky-200/50 px-2 py-1 rounded flex-shrink-0 transition-colors"
          aria-label="Clear all files"
        >
          Clear all
        </button>
      </div>

      <div className="grid gap-3" role="list" aria-label="Image conversion queue">
        {jobs.map((j) => (
          <div key={j.id} className="rounded-md border bg-white p-3 sm:p-4 shadow-sm" role="listitem">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => onRemove(j.id)}
                    className="flex-shrink-0 p-1.5 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                    aria-label={`Remove ${j.file.name} from queue`}
                  >
                    <Trash2 size={20} className="sm:size-5" aria-hidden="true" />
                  </button>
                  <div className="flex-1 min-w-0">
                    <div className="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-3">
                      <div className="text-sm font-medium truncate">{j.file.name}</div>
                      <div className="text-xs text-slate-500">
                        {j.file.size > 1024 * 1024
                          ? `${Math.round(j.file.size / 1024 / 1024)} MB`
                          : `${Math.round(j.file.size / 1024)} KB`
                        } → {(j.targetFormat ?? (j.isGif ? `${gifFormats.map(f => f.toUpperCase()).join(', ')} (ZIP)` : target)).toUpperCase()}
                      </div>
                    </div>
                  </div>
                </div>
                <div className="mt-1 flex items-center gap-2 ml-7">
                  {j.status === 'running' && (
                    <div className="flex-1" role="status" aria-live="polite">
                      <div className="flex items-center gap-2 text-xs text-blue-600 mb-1">
                        <div className="animate-spin h-3 w-3 border border-blue-600 border-t-transparent rounded-full" aria-hidden="true"></div>
                        Converting... {j.progress !== undefined ? `${j.progress}%` : ''}
                      </div>
                      {j.progress !== undefined && (
                        <progress
                          className="progress-convert w-full h-1.5"
                          value={j.progress}
                          max={100}
                          aria-label={`Conversion progress: ${j.progress} percent`}
                        />
                      )}
                    </div>
                  )}
                  {j.status === 'done' && (
                    <div className="text-xs text-emerald-600 flex items-center gap-1" role="status">
                      <CheckCircle size={14} aria-hidden="true" /> Converted successfully
                    </div>
                  )}
                  {j.status === 'error' && (
                    <div className="text-rose-600 flex items-center gap-1 text-xs" role="alert">
                      <AlertTriangle size={14} aria-hidden="true" /> {j.error}
                    </div>
                  )}
                  {j.status === 'queued' && (
                    <div className="text-xs text-slate-500">
                      Queued for conversion — use Convert above
                    </div>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2 flex-shrink-0">
                {j.status === 'done' && j.url && (
                  <>
                    <button
                      type="button"
                      onClick={() => onPreview(j.url!)}
                      aria-label={`Preview converted ${j.file.name}`}
                      className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-3 sm:px-3 py-2.5 sm:py-1.5 text-slate-700 hover:bg-slate-200 text-xs sm:text-sm min-h-[44px] sm:min-h-0"
                    >
                      <Eye size={16} className="sm:size-3" aria-hidden="true" /> <span className="hidden sm:inline">Preview</span><span className="sm:hidden sr-only">Preview</span>
                    </button>
                    <a
                      href={j.url}
                      download={j.isGif ? `${j.file.name.split('.')[0]}_frames.zip` : `${j.file.name.split('.')[0]}_converted.${j.targetFormat ?? target}`}
                      aria-label={`Download converted ${j.file.name}`}
                      className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 sm:px-3 py-2.5 sm:py-1.5 text-white hover:bg-emerald-700 text-xs sm:text-sm min-h-[44px] sm:min-h-0"
                    >
                      <Download size={16} className="sm:size-3" aria-hidden="true" /> <span className="hidden sm:inline">{j.isGif ? 'Download ZIP' : 'Download'}</span><span className="sm:hidden sr-only">Download</span>
                    </a>
                  </>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
