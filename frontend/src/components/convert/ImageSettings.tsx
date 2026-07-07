'use client';

import React from 'react';
import { QUALITY_RANGE, RESIZE_PRESETS, RESIZE_RANGE, SHARPNESS_RANGE } from '../../lib/conversionConfig';
import { Job } from '../../lib/conversionApi';

type Props = {
  quality: number;
  onQualityChange: (value: number) => void;
  sharpness: number;
  onSharpnessChange: (value: number) => void;
  resizeEnabled: boolean;
  onResizeEnabledChange: (value: boolean) => void;
  maxWidth: number;
  onMaxWidthChange: (value: number) => void;
  jobs: Job[];
};

/** Quality, sharpness and resize controls, including large-image sharpness advisories. */
export function ImageSettings({
  quality, onQualityChange,
  sharpness, onSharpnessChange,
  resizeEnabled, onResizeEnabledChange,
  maxWidth, onMaxWidthChange,
  jobs,
}: Props) {
  const queuedJobs = jobs.filter(j => j.status === 'queued');
  const maxDimension = queuedJobs.length > 0
    ? Math.max(...queuedJobs.map(j => Math.max(j.width || 0, j.height || 0)))
    : 0;

  return (
    <div className="border-t pt-4 space-y-4">
      <div className="text-sm sm:text-base font-semibold text-slate-800 mb-3">Image Settings</div>

      {/* Quality Slider */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <label htmlFor="quality-slider" className="block text-sm font-medium text-slate-700">Quality</label>
          <span className="text-sm font-semibold text-sky-600" aria-live="polite">{quality}%</span>
        </div>
        <input
          id="quality-slider"
          type="range"
          min={QUALITY_RANGE.min}
          max={QUALITY_RANGE.max}
          value={quality}
          onChange={(e) => onQualityChange(Number(e.target.value))}
          aria-label="Image quality"
          aria-valuetext={`${quality} percent quality`}
          aria-valuenow={quality}
          aria-valuemin={QUALITY_RANGE.min}
          aria-valuemax={QUALITY_RANGE.max}
          className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-sky-600"
        />
        <div className="flex justify-between text-xs text-slate-500 mt-1" aria-hidden="true">
          <span>Lower size</span>
          <span>Higher quality</span>
        </div>
      </div>

      {/* Sharpness Slider */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <label htmlFor="sharpness-slider" className="block text-sm font-medium text-slate-700">Sharpness</label>
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">
              {sharpness <= 50 && 'Subtle'}
              {sharpness > 50 && sharpness <= 100 && 'Standard'}
              {sharpness > 100 && sharpness <= 150 && 'Professional'}
              {sharpness > 150 && 'Maximum'}
            </span>
            <span className="text-sm font-semibold text-sky-600" aria-live="polite">{sharpness}%</span>
          </div>
        </div>
        <input
          id="sharpness-slider"
          type="range"
          min={SHARPNESS_RANGE.min}
          max={SHARPNESS_RANGE.max}
          value={sharpness}
          onChange={(e) => onSharpnessChange(Number(e.target.value))}
          aria-label="Image sharpness"
          aria-valuetext={`${sharpness} percent sharpness`}
          aria-valuenow={sharpness}
          aria-valuemin={SHARPNESS_RANGE.min}
          aria-valuemax={SHARPNESS_RANGE.max}
          className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-sky-600"
        />
        <div className="flex justify-between text-xs text-slate-500 mt-1" aria-hidden="true">
          <span>Off</span>
          <span className="text-emerald-600">50 • Subtle</span>
          <span className="text-sky-600">100 • Standard</span>
          <span className="text-purple-600">150 • Pro</span>
          <span className="text-orange-600">200 • Max</span>
        </div>
        {sharpness > 0 && (
          <div className="text-xs text-slate-600 bg-slate-50 rounded p-2 mt-2">
            {sharpness <= 50 && '🌱 Gentle unsharp mask for natural enhancement'}
            {sharpness > 50 && sharpness <= 100 && '✨ Adaptive sharpening - adjusts to image content'}
            {sharpness > 100 && sharpness <= 150 && '💎 Professional LAB color space sharpening - no color artifacts'}
            {sharpness > 150 && '🔥 Maximum multi-pass sharpening with contrast enhancement'}
          </div>
        )}

        {/* Sharpness capping warning for large images */}
        {maxDimension > 4000 && sharpness > 50 ? (
          <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded p-2 mt-2">
            ⚠️ Large image detected ({maxDimension}px). Sharpness will be limited to 50% to respect server time limits. For higher sharpness, resize your image to ≤4000px first.
          </div>
        ) : maxDimension > 2000 && sharpness > 100 ? (
          <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded p-2 mt-2">
            ⚠️ Large image detected ({maxDimension}px). Sharpness will be limited to 100% to respect server time limits. For higher sharpness, resize your image to ≤1920px first.
          </div>
        ) : null}
      </div>

      {/* Resize Toggle */}
      <div className="border-t pt-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex-1">
            <label htmlFor="resize-toggle" className="text-sm font-medium text-slate-700">Resize Image</label>
            <p className="text-xs text-slate-500 mt-0.5">Perfect for favicons, thumbnails & optimization</p>
          </div>
          <button
            id="resize-toggle"
            onClick={() => onResizeEnabledChange(!resizeEnabled)}
            role="switch"
            aria-checked={resizeEnabled}
            aria-label={`Resize images ${resizeEnabled ? 'enabled' : 'disabled'}`}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
              resizeEnabled ? 'bg-sky-600' : 'bg-slate-300'
            }`}
          >
            <span
              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                resizeEnabled ? 'translate-x-6' : 'translate-x-1'
              }`}
              aria-hidden="true"
            />
          </button>
        </div>
        {resizeEnabled && (
          <div className="space-y-3">
            {/* Quick Presets */}
            <div role="group" aria-label="Resize width presets">
              <label className="block text-xs font-medium text-slate-600 mb-2">Quick Presets:</label>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
                {RESIZE_PRESETS.map(({ width, label }) => (
                  <button
                    key={width}
                    onClick={() => onMaxWidthChange(width)}
                    aria-pressed={maxWidth === width}
                    aria-label={`Set width to ${width} pixels for ${label.toLowerCase()}`}
                    className={`px-3 py-2 rounded-md text-xs font-medium transition-all ${
                      maxWidth === width
                        ? 'bg-sky-600 text-white shadow-sm'
                        : 'bg-slate-100 text-slate-700 hover:bg-slate-200 border border-slate-200'
                    }`}
                  >
                    {width}px<span className="block text-[10px] opacity-80" aria-hidden="true">{label}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Custom Slider */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label htmlFor="width-slider" className="block text-xs font-medium text-slate-600">Custom Width:</label>
                <span className="text-sm font-semibold text-sky-600" aria-live="polite">{maxWidth}px</span>
              </div>
              <input
                id="width-slider"
                type="range"
                min={RESIZE_RANGE.min}
                max={RESIZE_RANGE.max}
                step={16}
                value={maxWidth}
                onChange={(e) => onMaxWidthChange(Number(e.target.value))}
                aria-label="Custom resize width"
                aria-valuetext={`${maxWidth} pixels width`}
                aria-valuenow={maxWidth}
                aria-valuemin={RESIZE_RANGE.min}
                aria-valuemax={RESIZE_RANGE.max}
                className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-sky-600"
              />
              <div className="flex justify-between text-xs text-slate-400 mt-1" aria-hidden="true">
                <span>{RESIZE_RANGE.min}px</span>
                <span>{RESIZE_RANGE.max}px</span>
              </div>
            </div>

            <p className="text-xs text-slate-500 bg-slate-50 rounded p-2">
              💡 Images maintain aspect ratio and won&apos;t be enlarged beyond their original size.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
