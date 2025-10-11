'use client';
import React, { useEffect, useState } from "react";
import { useSession, signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useDropzone, FileRejection } from "react-dropzone";
import { API_URL } from "../../env";
import { Download, Upload, Wand2, AlertTriangle, Eye, CheckCircle, X } from "lucide-react";
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
  const [target, setTarget] = useState('webp');
  const [quality, setQuality] = useState(85);
  const [sharpness, setSharpness] = useState(0);
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
        const data = await res.json();
        setAuth({
          freeRemaining: data.freeRemaining,
          paidCredits: data.paidCredits,
        });
      }
    } catch {
      // Silently fail - not critical
    }
  };

  // Validate image dimensions
  const validateImageDimensions = (file: File): Promise<{ valid: boolean; error?: string }> => {
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
          resolve({ valid: true });
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

    // Validate file sizes and dimensions for accepted files
    const validatedJobs: Job[] = [];

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

      validatedJobs.push({
        id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        file,
        status: 'queued'
      });
    }

    if (validatedJobs.length > 0) {
      setJobs((prev) => [...prev, ...validatedJobs]);
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

  const start = async () => {
    const pending = jobs.filter(j => j.status === 'queued');
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
          setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'error', error: 'Conversion limit reached' } : x));
          return;
        }
        if (res.status === 413) {
          const errorData = await res.json().catch(() => ({}));
          throw new Error(errorData.error || 'File is too large. Maximum allowed size is 8MB.');
        }
        if (!res.ok) {
          const errorData = await res.json().catch(() => ({}));
          throw new Error(errorData.error || `Conversion failed: ${res.status}`);
        }
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        setJobs((prev) => prev.map(job => job.id === j.id ? { ...job, status: 'done' as const, url, progress: 100 } : job));

        // Refresh user credits after successful conversion
        await refreshCredits();
      } catch (e: unknown) {
        clearInterval(progressInterval);
        const errorMessage = e instanceof Error ? e.message : 'Unknown error occurred';
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
        <div className="rounded-lg bg-red-50 border border-red-200 p-4 flex items-start gap-3">
          <AlertTriangle className="text-red-600 flex-shrink-0 mt-0.5" size={20} />
          <div className="flex-1">
            <p className="text-sm text-red-800">{errorMessage}</p>
          </div>
          <button onClick={() => setErrorMessage(null)} className="text-red-600 hover:text-red-800">
            <X size={18} />
          </button>
        </div>
      )}

      {/* Rate Limit Warning */}
      {rateLimitRemaining !== null && rateLimitRemaining <= 3 && rateLimitRemaining > 0 && (
        <div className="rounded-lg bg-amber-50 border border-amber-200 p-4 flex items-start gap-3">
          <AlertTriangle className="text-amber-600 flex-shrink-0 mt-0.5" size={20} />
          <div className="flex-1">
            <p className="text-sm text-amber-800">
              <strong>Rate limit warning:</strong> Only {rateLimitRemaining} request{rateLimitRemaining === 1 ? '' : 's'} remaining. Please wait before making more conversions.
            </p>
          </div>
          <button onClick={() => setRateLimitRemaining(null)} className="text-amber-600 hover:text-amber-800">
            <X size={18} />
          </button>
        </div>
      )}

      <div className="rounded-lg border bg-white p-3 sm:p-4">
        {/* Drop Zone - Now at the top and bigger */}
        <div {...getRootProps()} className={"mb-6 border-2 border-dashed rounded-lg p-8 sm:p-16 text-center " + dropClass}>
          <input {...getInputProps()} />
          <div className="flex flex-col items-center justify-center gap-3 text-slate-600">
            <Upload size={48} className="text-sky-500" />
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
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-3">Convert to:</label>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
              {Object.entries(FORMAT_GROUPS).map(([groupName, formats]) => (
                <div key={groupName} className="space-y-1.5">
                  <div className="text-xs font-medium text-slate-500 px-1">{groupName}</div>
                  <div className="space-y-1">
                    {formats.map(fmt => (
                      <button
                        key={fmt}
                        onClick={() => setTarget(fmt)}
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
                <label className="block text-sm font-medium text-slate-700">Quality</label>
                <span className="text-sm font-semibold text-sky-600">{quality}%</span>
              </div>
              <input
                type="range"
                min={1}
                max={100}
                value={quality}
                onChange={(e)=>setQuality(Number(e.target.value))}
                className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-sky-600"
              />
              <div className="flex justify-between text-xs text-slate-500 mt-1">
                <span>Lower size</span>
                <span>Higher quality</span>
              </div>
            </div>

            {/* Sharpness Slider */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-sm font-medium text-slate-700">Sharpness</label>
                <span className="text-sm font-semibold text-sky-600">{sharpness}%</span>
              </div>
              <input
                type="range"
                min={0}
                max={200}
                value={sharpness}
                onChange={(e)=>setSharpness(Number(e.target.value))}
                className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-sky-600"
              />
              <div className="flex justify-between text-xs text-slate-500 mt-1">
                <span>No sharpening</span>
                <span>Maximum sharpness</span>
              </div>
            </div>
          </div>

          {/* Convert Button */}
          <div className="border-t pt-4">
            <button
              onClick={start}
              disabled={jobs.filter(j => j.status === 'queued').length === 0}
              className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-sky-600 px-4 py-3 text-white hover:bg-sky-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium shadow-sm"
            >
              <Wand2 size={18} /> Convert {jobs.filter(j => j.status === 'queued').length > 0 ? `(${jobs.filter(j => j.status === 'queued').length})` : 'All'}
            </button>
          </div>
        </div>
      </div>

      <div className="grid gap-3">
        {jobs.map((j) => (
          <div key={j.id} className="rounded-md border bg-white p-3 sm:p-4 shadow-sm">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
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
                <div className="mt-1 flex items-center gap-2">
                  {j.status === 'running' && (
                    <div className="flex-1">
                      <div className="flex items-center gap-2 text-xs text-blue-600 mb-1">
                        <div className="animate-spin h-3 w-3 border border-blue-600 border-t-transparent rounded-full"></div>
                        Converting... {j.progress !== undefined ? `${j.progress}%` : ''}
                      </div>
                      {j.progress !== undefined && (
                        <div className="w-full bg-slate-200 rounded-full h-1.5">
                          <div
                            className="bg-blue-600 h-1.5 rounded-full transition-all duration-200"
                            style={{ width: `${j.progress}%` }}
                          ></div>
                        </div>
                      )}
                    </div>
                  )}
                  {j.status === 'done' && (
                    <div className="text-xs text-emerald-600 flex items-center gap-1">
                      <CheckCircle size={14}/> Converted successfully
                    </div>
                  )}
                  {j.status === 'error' && (
                    <div className="text-rose-600 flex items-center gap-1 text-xs">
                      <AlertTriangle size={14}/> {j.error}
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
                      className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 sm:px-3 py-1.5 text-slate-700 hover:bg-slate-200 text-xs sm:text-sm"
                    >
                      <Eye size={12} /> <span className="hidden sm:inline">Preview</span>
                    </button>
                    <a
                      href={j.url}
                      download={`${j.file.name.split('.')[0]}_converted.${target}`}
                      className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2 sm:px-3 py-1.5 text-white hover:bg-emerald-700 text-xs sm:text-sm"
                    >
                      <Download size={12} /> <span className="hidden sm:inline">Download</span>
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
          className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50 p-4"
          onClick={() => setPreviewUrl(null)}
        >
          <div className="relative max-w-7xl max-h-[90vh] bg-white rounded-lg overflow-hidden" onClick={(e) => e.stopPropagation()}>
            <div className="absolute top-2 right-2 z-10">
              <button
                onClick={() => setPreviewUrl(null)}
                className="bg-white rounded-full p-2 shadow-lg hover:bg-gray-100"
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={previewUrl}
              alt="Preview"
              className="max-w-full max-h-[90vh] object-contain"
            />
          </div>
        </div>
      )}

      {/* Conversion Limit Modal */}
      {showLimitModal && (
        <div
          className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50 p-4"
          onClick={() => setShowLimitModal(false)}
        >
          <div className="relative max-w-md w-full bg-white rounded-lg shadow-xl p-6" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-start gap-4">
              <div className="flex-shrink-0">
                <div className="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center">
                  <AlertTriangle className="w-6 h-6 text-amber-600" />
                </div>
              </div>
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-slate-900 mb-2">
                  Conversion Limit Reached
                </h3>
                <p className="text-slate-600 mb-4">
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
                      className="flex-1 inline-flex items-center justify-center gap-2 rounded-md bg-sky-600 px-4 py-2.5 text-white hover:bg-sky-700 text-sm font-medium"
                    >
                      Sign In
                    </button>
                  )}
                  <button
                    onClick={() => router.push('/billing')}
                    className="flex-1 inline-flex items-center justify-center gap-2 rounded-md bg-emerald-600 px-4 py-2.5 text-white hover:bg-emerald-700 text-sm font-medium"
                  >
                    Subscribe Now
                  </button>
                  <button
                    onClick={() => setShowLimitModal(false)}
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