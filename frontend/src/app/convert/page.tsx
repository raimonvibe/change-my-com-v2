'use client';
// Enhanced file validation v2 - force deployment
import React, { useEffect, useState } from "react";
import { useSession, signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useDropzone, FileRejection } from "react-dropzone";
import { API_URL } from "../../env";
import { Download, Upload, Wand2, AlertTriangle, Eye, CheckCircle, X, Trash2 } from "lucide-react";
import { useAuthStore } from "../../store/useAuthStore";

// Organized format groups for better UX
// Only safe raster formats - SVG/PDF excluded for security
// TIFF & BMP removed due to high resource requirements on limited server specs
const FORMAT_GROUPS = {
  'Modern Web': ['webp', 'avif'],
  'Standard': ['jpg', 'png', 'gif'],
  'Mobile': ['heic'],
  'Other': ['ico']
};

const MAX_FILE_SIZE = 8 * 1024 * 1024; // 8MB in bytes
const MAX_DIMENSION = 8000; // Maximum width or height in pixels

// Valid file extensions for better error handling
const VALID_EXTENSIONS = [
  'jpg', 'jpeg', 'png', 'webp', 'avif', 'heic', 'tiff', 'bmp', 'gif', 'svg', 'ico'
];

// Function to validate file extension
const validateFileExtension = (fileName: string): { valid: boolean; error?: string } => {
  const extension = fileName.split('.').pop()?.toLowerCase();
  
  if (!extension) {
    return { valid: false, error: 'File has no extension' };
  }
  
  if (!VALID_EXTENSIONS.includes(extension)) {
    // Check if it's a common web download issue (like :small, :thumb, _small, _thumb, etc.)
    const commonSuffixes = [':small', ':thumb', ':medium', ':large', ':resized', ':compressed', ':1', ':2', ':3',
                           '_small', '_thumb', '_medium', '_large', '_resized', '_compressed', '_1', '_2', '_3'];
    const hasCommonSuffix = commonSuffixes.some(suffix => fileName.toLowerCase().includes(suffix));
    
    if (hasCommonSuffix) {
      return { 
        valid: false, 
        error: `Invalid file format detected. This appears to be a web download with a platform suffix (like :small, _small, :thumb, _thumb). Please rename the file to remove the suffix (e.g., "image.jpg_small" → "image.jpg") and try again.` 
      };
    }
    
    return { 
      valid: false, 
      error: `Unsupported file format: .${extension}. Please use a standard image format like .jpg, .png, or .webp.` 
    };
  }
  
  return { valid: true };
};

type Job = {
  id: string;
  file: File;
  status: 'queued' | 'running' | 'done' | 'error';
  url?: string;
  error?: string;
  progress?: number;
  startTime?: number;
};

export default function ConvertPage() {
  const router = useRouter();
  const { data: session } = useSession();
  const setAuth = useAuthStore(s => s.setAuth);
  const auth = useAuthStore(s => s);
  const [target, setTarget] = useState('webp');
  const [quality, setQuality] = useState(85);
  const [sharpness, setSharpness] = useState(0);
  const [resizeEnabled, setResizeEnabled] = useState(false);
  const [maxWidth, setMaxWidth] = useState(1920);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [showLimitModal, setShowLimitModal] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [rateLimitRemaining, setRateLimitRemaining] = useState<number | null>(null);
  const token = session?.idToken as string | undefined;

  // Cleanup blob URLs on unmount to prevent memory leaks
  useEffect(() => {
    return () => {
      jobs.forEach(job => {
        if (job.url) {
          URL.revokeObjectURL(job.url);
        }
      });
    };
  }, [jobs]);

  // Close modals on ESC key
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setPreviewUrl(null);
        setShowLimitModal(false);
      }
    };
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, []);

  // Function to refresh user credits after conversion
  const refreshCredits = async () => {
    if (!session?.idToken) return;

    try {
      const res = await fetch(`${API_URL}/api/user/me`, {
        headers: { Authorization: `Bearer ${session.idToken}` },
      });

      if (res.ok) {
        const data = await res.json() as { freeRemaining?: number; paidCredits?: number };
        setAuth({
          freeRemaining: data.freeRemaining,
          paidCredits: data.paidCredits,
        });
      }
    } catch {
      // Silently fail - not critical
    }
  };

  // Validate image dimensions and return dimensions
  const validateImageDimensions = (file: File): Promise<{ valid: boolean; error?: string; width?: number; height?: number }> => {
    return new Promise((resolve) => {
      const img = new Image();
      const url = URL.createObjectURL(file);

      img.onload = () => {
        URL.revokeObjectURL(url);
        if (img.width > MAX_DIMENSION || img.height > MAX_DIMENSION) {
          resolve({
            valid: false,
            error: `Image dimensions (${img.width}x${img.height}) exceed maximum allowed (${MAX_DIMENSION}x${MAX_DIMENSION}px)`
          });
        } else {
          resolve({ valid: true, width: img.width, height: img.height });
        }
      };

      img.onerror = () => {
        URL.revokeObjectURL(url);
        resolve({ valid: false, error: 'Unable to read image dimensions' });
      };

      img.src = url;
    });
  };

  const onDrop = async (acceptedFiles: File[], rejectedFiles: FileRejection[]) => {
    // Handle rejected files (too large, wrong type, etc.)
    if (rejectedFiles.length > 0) {
      const rejectedFile = rejectedFiles[0];
      if (rejectedFile.errors.some((e) => e.code === 'file-too-large')) {
        setErrorMessage(`File "${rejectedFile.file.name}" is too large. Maximum allowed size is 8MB.`);
        setTimeout(() => setErrorMessage(null), 5000);
        return;
      }
      if (rejectedFile.errors.some((e) => e.code === 'file-invalid-type')) {
        setErrorMessage(`File "${rejectedFile.file.name}" has an unsupported format. Only images are allowed.`);
        setTimeout(() => setErrorMessage(null), 5000);
        return;
      }
    }

    // Validate file extensions first
    for (const file of acceptedFiles) {
      const extensionValidation = validateFileExtension(file.name);
      if (!extensionValidation.valid) {
        setErrorMessage(`File "${file.name}": ${extensionValidation.error}`);
        setTimeout(() => setErrorMessage(null), 5000);
        return;
      }
    }

    // Validate file sizes and dimensions for accepted files
    const validatedJobs: Job[] = [];
    let largestWidth = 0;

    for (const file of acceptedFiles) {
      if (file.size > MAX_FILE_SIZE) {
        setErrorMessage(`File "${file.name}" is too large (${Math.round(file.size / 1024 / 1024)}MB). Maximum allowed size is 8MB.`);
        setTimeout(() => setErrorMessage(null), 5000);
        continue;
      }

      // Validate dimensions
      const validation = await validateImageDimensions(file);
      if (!validation.valid) {
        setErrorMessage(validation.error || 'Invalid image');
        setTimeout(() => setErrorMessage(null), 5000);
        continue;
      }

      // Track the largest width for setting default resize value
      if (validation.width && validation.width > largestWidth) {
        largestWidth = validation.width;
      }

      validatedJobs.push({
        id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        file,
        status: 'queued'
      });
    }

    if (validatedJobs.length > 0) {
      setJobs((prev) => [...prev, ...validatedJobs]);

      // Set maxWidth to the largest uploaded image's width (capped at 8000px)
      // This gives users a sensible default that matches their content
      if (largestWidth > 0) {
        setMaxWidth(Math.min(largestWidth, 8000));
      }
    }
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
    maxSize: MAX_FILE_SIZE,
    accept: {
      'image/*': ['.jpg', '.jpeg', '.png', '.webp', '.avif', '.gif', '.heic', '.heif', '.ico']
    }
  });
  const dropClass = isDragActive ? 'border-sky-500 bg-sky-50' : 'border-slate-300';

  const removeJob = (jobId: string) => {
    setJobs((prev) => {
      const jobToRemove = prev.find(j => j.id === jobId);
      // Revoke blob URL if it exists to prevent memory leak
      if (jobToRemove?.url) {
        URL.revokeObjectURL(jobToRemove.url);
      }
      return prev.filter(j => j.id !== jobId);
    });
  };

  const start = async () => {
    const pending = jobs.filter(j => j.status === 'queued');
    
    // Check if user has credits before starting conversion
    if (pending.length > 0 && auth.freeRemaining === 0 && auth.paidCredits === 0) {
      setShowLimitModal(true);
      setJobs((prev) => prev.map(job => 
        pending.some(p => p.id === job.id) 
          ? { ...job, status: 'error', error: 'No conversions remaining. Please subscribe to continue.' }
          : job
      ));
      return;
    }
    
    for (const j of pending) {
      const startTime = Date.now();
      setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'running', startTime, progress: 0 } : x));

      // Simulate progress for better UX (actual progress not available from backend)
      const progressInterval = setInterval(() => {
        setJobs((prev) => prev.map(x => {
          if (x.id === j.id && x.status === 'running' && x.progress !== undefined) {
            const elapsed = Date.now() - (x.startTime || Date.now());
            // Progress based on file size and elapsed time
            const estimatedTime = (x.file.size / 1024 / 1024) * 2000; // ~2s per MB
            const newProgress = Math.min(90, Math.floor((elapsed / estimatedTime) * 100));
            return { ...x, progress: newProgress };
          }
          return x;
        }));
      }, 200);

      const form = new FormData();
      form.append('file', j.file);
      form.append('to', target);
      form.append('quality', String(quality));
      form.append('sharpness', String(sharpness));
      if (resizeEnabled && maxWidth) {
        form.append('width', String(maxWidth));
      }

      try {
        const res = await fetch(`${API_URL}/api/convert`, {
          method: 'POST',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          body: form,
        });

        clearInterval(progressInterval);

        // Extract rate limit info from headers
        const remaining = res.headers.get('X-RateLimit-Remaining');
        if (remaining) {
          setRateLimitRemaining(parseInt(remaining));
        }

        if (res.status === 401) throw new Error('Please sign in with Google to convert.');
        if (res.status === 402) {
          setShowLimitModal(true);
          setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'error', error: 'No conversions remaining. Please subscribe to continue.' } : x));
          return;
        }
        if (res.status === 413) {
          const errorData = await res.json().catch(() => ({})) as { error?: string };
          throw new Error(errorData.error || 'File is too large. Maximum allowed size is 8MB.');
        }
        if (!res.ok) {
          const errorData = await res.json().catch(() => ({})) as { error?: string };
          let errorMessage = errorData.error || `Conversion failed: ${res.status}`;
          
          // Provide more specific error messages for common issues
          if (res.status === 400) {
            errorMessage = 'Invalid file format or corrupted image. Please try a different file.';
          } else if (res.status === 415) {
            errorMessage = 'Unsupported file format. Please use standard image formats like .jpg, .png, or .webp.';
          } else if (res.status === 422) {
            errorMessage = 'File format not supported for conversion. Please try a different image format.';
          }
          
          throw new Error(errorMessage);
        }
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        setJobs((prev) => prev.map(job => job.id === j.id ? { ...job, status: 'done' as const, url, progress: 100 } : job));

        // Refresh user credits after successful conversion
        await refreshCredits();
      } catch (e: unknown) {
        clearInterval(progressInterval);
        let errorMessage = 'Unknown error occurred';
        
        if (e instanceof Error) {
          if (e.message.includes('Failed to fetch') || e.message.includes('NetworkError')) {
            errorMessage = 'Network error. Please check your connection and try again.';
          } else if (e.message.includes('fetch')) {
            errorMessage = 'Unable to connect to the server. Please try again later.';
          } else {
            errorMessage = e.message;
          }
        }
        
        setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'error', error: errorMessage } : x));
      }
    }
  };

  return (
    <div className="space-y-4 sm:space-y-6">
      <h1 className="text-lg sm:text-xl font-semibold text-sky-800 flex items-center gap-2">
        <Wand2 className="text-sky-600" /> Convert Images
      </h1>

      {/* Error Message Toast */}
      {errorMessage && (
        <div role="alert" aria-live="assertive" className="rounded-lg bg-red-50 border border-red-200 p-4 flex items-start gap-3">
          <AlertTriangle className="text-red-600 flex-shrink-0 mt-0.5" size={20} aria-hidden="true" />
          <div className="flex-1">
            <p className="text-sm text-red-800">{errorMessage}</p>
          </div>
          <button onClick={() => setErrorMessage(null)} className="text-red-600 hover:text-red-800" aria-label="Close error message">
            <X size={18} aria-hidden="true" />
          </button>
        </div>
      )}

      {/* Rate Limit Warning */}
      {rateLimitRemaining !== null && rateLimitRemaining <= 3 && rateLimitRemaining > 0 && (
        <div role="alert" aria-live="polite" className="rounded-lg bg-amber-50 border border-amber-200 p-4 flex items-start gap-3">
          <AlertTriangle className="text-amber-600 flex-shrink-0 mt-0.5" size={20} aria-hidden="true" />
          <div className="flex-1">
            <p className="text-sm text-amber-800">
              <strong>Rate limit warning:</strong> Only {rateLimitRemaining} request{rateLimitRemaining === 1 ? '' : 's'} remaining. Please wait before making more conversions.
            </p>
          </div>
          <button onClick={() => setRateLimitRemaining(null)} className="text-amber-600 hover:text-amber-800" aria-label="Close warning">
            <X size={18} aria-hidden="true" />
          </button>
        </div>
      )}

      <div className="rounded-lg border bg-white p-3 sm:p-4">
        {/* Drop Zone - Now at the top and bigger */}
        <div {...getRootProps()} className={"mb-6 border-2 border-dashed rounded-lg p-8 sm:p-16 text-center " + dropClass} role="button" aria-label="Upload images - drag and drop or click to select. Maximum 8MB, 8000x8000 pixels. Supported formats: JPG, PNG, WebP, AVIF, GIF, HEIC, ICO" tabIndex={0}>
          <input {...getInputProps()} aria-label="File upload input" />
          <div className="flex flex-col items-center justify-center gap-3 text-slate-600">
            <Upload size={48} className="text-sky-500" aria-hidden="true" />
            <div className="text-base sm:text-lg font-medium">
              <span className="hidden sm:inline">Drag & drop images here, or click to select</span>
              <span className="sm:hidden">Tap to select images</span>
            </div>
            <div className="text-sm text-slate-500 mt-1">
              Max: 8MB, {MAX_DIMENSION}x{MAX_DIMENSION}px • Formats: JPG, PNG, WebP, AVIF, GIF, HEIC, ICO
            </div>
          </div>
        </div>

        <div className="space-y-4">
          {/* Format Selection - Organized Grid */}
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
                        onClick={() => setTarget(fmt)}
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
          </div>

          {/* Image Settings */}
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
                min={1}
                max={100}
                value={quality}
                onChange={(e)=>setQuality(Number(e.target.value))}
                aria-label="Image quality"
                aria-valuetext={`${quality} percent quality`}
                aria-valuenow={quality}
                aria-valuemin={1}
                aria-valuemax={100}
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
                min={0}
                max={200}
                value={sharpness}
                onChange={(e)=>setSharpness(Number(e.target.value))}
                aria-label="Image sharpness"
                aria-valuetext={`${sharpness} percent sharpness`}
                aria-valuenow={sharpness}
                aria-valuemin={0}
                aria-valuemax={200}
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
                  onClick={() => setResizeEnabled(!resizeEnabled)}
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
                      <button
                        onClick={() => setMaxWidth(256)}
                        aria-pressed={maxWidth === 256}
                        aria-label="Set width to 256 pixels for small icon"
                        className={`px-3 py-2 rounded-md text-xs font-medium transition-all ${
                          maxWidth === 256
                            ? 'bg-sky-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-700 hover:bg-slate-200 border border-slate-200'
                        }`}
                      >
                        256px<span className="block text-[10px] opacity-80" aria-hidden="true">Small Icon</span>
                      </button>
                      <button
                        onClick={() => setMaxWidth(512)}
                        aria-pressed={maxWidth === 512}
                        aria-label="Set width to 512 pixels for favicon"
                        className={`px-3 py-2 rounded-md text-xs font-medium transition-all ${
                          maxWidth === 512
                            ? 'bg-sky-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-700 hover:bg-slate-200 border border-slate-200'
                        }`}
                      >
                        512px<span className="block text-[10px] opacity-80" aria-hidden="true">Favicon</span>
                      </button>
                      <button
                        onClick={() => setMaxWidth(1024)}
                        aria-pressed={maxWidth === 1024}
                        aria-label="Set width to 1024 pixels for thumbnail"
                        className={`px-3 py-2 rounded-md text-xs font-medium transition-all ${
                          maxWidth === 1024
                            ? 'bg-sky-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-700 hover:bg-slate-200 border border-slate-200'
                        }`}
                      >
                        1024px<span className="block text-[10px] opacity-80" aria-hidden="true">Thumbnail</span>
                      </button>
                      <button
                        onClick={() => setMaxWidth(1920)}
                        aria-pressed={maxWidth === 1920}
                        aria-label="Set width to 1920 pixels for Full HD"
                        className={`px-3 py-2 rounded-md text-xs font-medium transition-all ${
                          maxWidth === 1920
                            ? 'bg-sky-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-700 hover:bg-slate-200 border border-slate-200'
                        }`}
                      >
                        1920px<span className="block text-[10px] opacity-80" aria-hidden="true">Full HD</span>
                      </button>
                      <button
                        onClick={() => setMaxWidth(3840)}
                        aria-pressed={maxWidth === 3840}
                        aria-label="Set width to 3840 pixels for 4K UHD"
                        className={`px-3 py-2 rounded-md text-xs font-medium transition-all ${
                          maxWidth === 3840
                            ? 'bg-sky-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-700 hover:bg-slate-200 border border-slate-200'
                        }`}
                      >
                        3840px<span className="block text-[10px] opacity-80" aria-hidden="true">4K UHD</span>
                      </button>
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
                      min={16}
                      max={8000}
                      step={16}
                      value={maxWidth}
                      onChange={(e)=>setMaxWidth(Number(e.target.value))}
                      aria-label="Custom resize width"
                      aria-valuetext={`${maxWidth} pixels width`}
                      aria-valuenow={maxWidth}
                      aria-valuemin={16}
                      aria-valuemax={8000}
                      className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-sky-600"
                    />
                    <div className="flex justify-between text-xs text-slate-400 mt-1" aria-hidden="true">
                      <span>16px</span>
                      <span>8000px</span>
                    </div>
                  </div>

                  <p className="text-xs text-slate-500 bg-slate-50 rounded p-2">
                    💡 Images maintain aspect ratio and won&apos;t be enlarged beyond their original size.
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Convert Button */}
          <div className="border-t pt-4">
            <button
              onClick={start}
              disabled={jobs.filter(j => j.status === 'queued').length === 0}
              aria-label={`Convert ${jobs.filter(j => j.status === 'queued').length} queued ${jobs.filter(j => j.status === 'queued').length === 1 ? 'image' : 'images'}`}
              className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-sky-600 px-4 py-3 text-white hover:bg-sky-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium shadow-sm"
            >
              <Wand2 size={18} aria-hidden="true" /> Convert {jobs.filter(j => j.status === 'queued').length > 0 ? `(${jobs.filter(j => j.status === 'queued').length})` : 'All'}
            </button>
          </div>
        </div>
      </div>

      <div className="grid gap-3" role="list" aria-label="Image conversion queue">
        {jobs.map((j) => (
          <div key={j.id} className="rounded-md border bg-white p-3 sm:p-4 shadow-sm" role="listitem">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => removeJob(j.id)}
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
                        } → {target.toUpperCase()}
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
                        <div className="w-full bg-slate-200 rounded-full h-1.5" role="progressbar" aria-valuenow={j.progress} aria-valuemin={0} aria-valuemax={100} aria-label={`Conversion progress: ${j.progress} percent`}>
                          <div
                            className="bg-blue-600 h-1.5 rounded-full transition-all duration-200"
                            style={{ width: `${j.progress}%` }}
                          ></div>
                        </div>
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
                      Queued for conversion
                    </div>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2 flex-shrink-0">
                {j.status === 'done' && j.url && (
                  <>
                    <button
                      onClick={() => setPreviewUrl(j.url || null)}
                      aria-label={`Preview converted ${j.file.name}`}
                      className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-3 sm:px-3 py-2.5 sm:py-1.5 text-slate-700 hover:bg-slate-200 text-xs sm:text-sm min-h-[44px] sm:min-h-0"
                    >
                      <Eye size={16} className="sm:size-3" aria-hidden="true" /> <span className="hidden sm:inline">Preview</span><span className="sm:hidden sr-only">Preview</span>
                    </button>
                    <a
                      href={j.url}
                      download={`${j.file.name.split('.')[0]}_converted.${target}`}
                      aria-label={`Download converted ${j.file.name}`}
                      className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 sm:px-3 py-2.5 sm:py-1.5 text-white hover:bg-emerald-700 text-xs sm:text-sm min-h-[44px] sm:min-h-0"
                    >
                      <Download size={16} className="sm:size-3" aria-hidden="true" /> <span className="hidden sm:inline">Download</span><span className="sm:hidden sr-only">Download</span>
                    </a>
                  </>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Preview Modal */}
      {previewUrl && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Image preview"
          className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50 p-4"
          onClick={() => setPreviewUrl(null)}
        >
          <div className="relative max-w-7xl max-h-[90vh] bg-white rounded-lg overflow-hidden" onClick={(e) => e.stopPropagation()}>
            <div className="absolute top-2 right-2 z-10">
              <button
                onClick={() => setPreviewUrl(null)}
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
              src={previewUrl}
              alt="Converted image preview"
              className="max-w-full max-h-[90vh] object-contain"
            />
          </div>
        </div>
      )}

      {/* Conversion Limit Modal */}
      {showLimitModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="limit-modal-title"
          aria-describedby="limit-modal-description"
          className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50 p-4"
          onClick={() => setShowLimitModal(false)}
        >
          <div className="relative max-w-md w-full bg-white rounded-lg shadow-xl p-6" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-start gap-4">
              <div className="flex-shrink-0">
                <div className="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center" aria-hidden="true">
                  <AlertTriangle className="w-6 h-6 text-amber-600" aria-hidden="true" />
                </div>
              </div>
              <div className="flex-1">
                <h3 id="limit-modal-title" className="text-lg font-semibold text-slate-900 mb-2">
                  Conversion Limit Reached
                </h3>
                <p id="limit-modal-description" className="text-slate-600 mb-4">
                  {session ? (
                    <>You&apos;ve used all your conversions for today. Subscribe to get 1000 conversions per month for just $1.98/month.</>
                  ) : (
                    <>You&apos;ve used all 20 free conversions for today. Sign in to continue, or subscribe for 1000 conversions per month at $1.98/month.</>
                  )}
                </p>
                <div className="flex flex-col sm:flex-row gap-3">
                  {!session && (
                    <button
                      onClick={() => signIn('google')}
                      aria-label="Sign in with Google"
                      className="flex-1 inline-flex items-center justify-center gap-2 rounded-md bg-sky-600 px-4 py-2.5 text-white hover:bg-sky-700 text-sm font-medium"
                    >
                      Sign In
                    </button>
                  )}
                  <button
                    onClick={() => router.push('/billing')}
                    aria-label="Go to billing page to subscribe"
                    className="flex-1 inline-flex items-center justify-center gap-2 rounded-md bg-emerald-600 px-4 py-2.5 text-white hover:bg-emerald-700 text-sm font-medium"
                  >
                    Subscribe Now
                  </button>
                  <button
                    onClick={() => setShowLimitModal(false)}
                    aria-label="Close limit modal"
                    className="flex-1 inline-flex items-center justify-center gap-2 rounded-md bg-slate-100 px-4 py-2.5 text-slate-700 hover:bg-slate-200 text-sm font-medium"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}