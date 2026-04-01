'use client';
// Enhanced file validation v2 - force deployment
import React, { useEffect, useState } from "react";
import { useSession, signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useDropzone, FileRejection } from "react-dropzone";
import { API_URL } from "../env";
import { Download, Upload, Wand2, AlertTriangle, Eye, CheckCircle, X, Trash2, Lightbulb, CalendarDays } from "lucide-react";
import { useAuthStore } from "../store/useAuthStore";
import { useUserData } from "../hooks/useUserData";
import { PAYMENTS_ENABLED } from "../lib/paymentsConfig";

// Note: Metadata is set in layout.tsx since this is a client component

// Organized format groups for better UX
// Only safe raster formats - SVG/PDF excluded for security
// TIFF & BMP removed due to high resource requirements on limited server specs
const FORMAT_GROUPS = {
  'Modern Web': ['webp', 'avif'],
  'Standard': ['jpg', 'png', 'gif'],
  'Mobile': ['heic'],
  'Other': ['ico']
};

const MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB in bytes
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
  isGif?: boolean;
  width?: number;
  height?: number;
  /** Target format used for this conversion (so UI shows correct format per job after dropdown changes) */
  targetFormat?: string;
};

// Note: We deliberately do NOT persist uploaded images or conversion results
// for security and privacy reasons. Images are only kept in memory during the session.

export default function ConvertPage() {
  const router = useRouter();
  const { data: session } = useSession();
  const auth = useAuthStore(s => s);
  const { refetch: refetchUserData } = useUserData(); // Handles session sync automatically
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
  const [gifFormats, setGifFormats] = useState<string[]>(['png', 'jpg']);
  const [anonymousRemaining, setAnonymousRemaining] = useState<number | null>(null);
  const [anonymousDailyLimit, setAnonymousDailyLimit] = useState(20);
  const token = session?.idToken as string | undefined;

  // Fetch anonymous (IP-based) remaining conversions when not logged in. Used for UX and testing.
  const fetchAnonymousRemaining = React.useCallback(async () => {
    if (session) return;
    try {
      const res = await fetch(`${API_URL}/api/anonymous/remaining`);
      if (res.ok) {
        const data = await res.json() as { remaining: number; dailyLimit: number };
        setAnonymousRemaining(data.remaining);
        setAnonymousDailyLimit(data.dailyLimit ?? 20);
      }
    } catch {
      setAnonymousRemaining(null);
    }
  }, [session]);

  // When not logged in, load anonymous remaining on mount so user can see "X of 20 left today"
  useEffect(() => {
    if (!session) fetchAnonymousRemaining();
    else setAnonymousRemaining(null);
  }, [session, fetchAnonymousRemaining]);

  // Helper to detect if user has subscription but isn't logged in
  // Used purely for UX messaging - backend still validates JWT for all authenticated requests
  const hasStaleSubscription = (): boolean => {
    if (session) return false; // Already logged in
    // Check if localStorage indicates user previously had an active subscription
    return auth.subscriptionStatus === 'active' || auth.paidCredits > 0;
  };

  const outOfCreditsMessage = (): string => {
    if (hasStaleSubscription()) {
      return 'Your session has expired. Please sign in to use your subscription.';
    }
    if (session) {
      return PAYMENTS_ENABLED
        ? 'No conversions remaining. Subscribe to continue.'
        : 'No conversions left for today. Your free quota resets tomorrow.';
    }
    return PAYMENTS_ENABLED
      ? 'Daily limit reached. Sign in to use your account or subscribe to continue.'
      : "You've used today's guest limit. Sign in for your personal daily quota, or try again tomorrow.";
  };

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

  // Validate image dimensions and return dimensions
  const validateImageDimensions = (file: File): Promise<{ valid: boolean; error?: string; width?: number; height?: number }> => {
    return new Promise((resolve) => {
      // HEIC files cannot be read by browsers' Image API, so skip dimension validation
      // Backend will validate dimensions using ImageMagick
      const extension = file.name.split('.').pop()?.toLowerCase();
      if (extension === 'heic' || extension === 'heif') {
        resolve({ valid: true }); // Skip dimension check for HEIC - backend will validate
        return;
      }

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
        // For HEIC/HEIF, this is expected - browsers can't read them
        // Backend will validate dimensions
        if (extension === 'heic' || extension === 'heif') {
          resolve({ valid: true });
        } else {
          resolve({ valid: false, error: 'Unable to read image dimensions' });
        }
      };

      img.src = url;
    });
  };

  const onDrop = async (acceptedFiles: File[], rejectedFiles: FileRejection[]) => {
    // Handle rejected files (too large, wrong type, etc.)
    if (rejectedFiles.length > 0) {
      const rejectedFile = rejectedFiles[0];
      if (rejectedFile.errors.some((e) => e.code === 'file-too-large')) {
        setErrorMessage(`File "${rejectedFile.file.name}" is too large. Maximum allowed size is 20MB.`);
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
        setErrorMessage(`File "${file.name}" is too large (${Math.round(file.size / 1024 / 1024)}MB). Maximum allowed size is 20MB.`);
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

      const isGif = file.type === 'image/gif';

      validatedJobs.push({
        id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        file,
        status: 'queued',
        isGif,
        width: validation.width,
        height: validation.height
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

  const convertGif = async (j: Job) => {
    const startTime = Date.now();
    const gifTargetLabel = `${gifFormats.map(f => f.toUpperCase()).join(', ')} (ZIP)`;
    setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'running', startTime, progress: 0, targetFormat: gifTargetLabel } : x));

    const progressInterval = setInterval(() => {
      setJobs((prev) => prev.map(x => {
        if (x.id === j.id && x.status === 'running' && x.progress !== undefined) {
          const elapsed = Date.now() - (x.startTime || Date.now());
          const estimatedTime = (x.file.size / 1024 / 1024) * 3000; // ~3s per MB for GIF
          const newProgress = Math.min(90, Math.floor((elapsed / estimatedTime) * 100));
          return { ...x, progress: newProgress };
        }
        return x;
      }));
    }, 200);

    const form = new FormData();
    form.append('file', j.file);
    form.append('formats', gifFormats.join(','));
    form.append('quality', String(quality));
    form.append('sharpness', String(sharpness));
    if (resizeEnabled && maxWidth) {
      form.append('width', String(maxWidth));
    }

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 60000); // 60 second timeout for GIFs

      const res = await fetch(`${API_URL}/api/convert/gif`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: form,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      clearInterval(progressInterval);

      const remaining = res.headers.get('X-RateLimit-Remaining');
      if (remaining) {
        setRateLimitRemaining(parseInt(remaining));
      }

      if (res.status === 401) throw new Error('Please sign in with Google to convert.');
      if (res.status === 402) {
        setShowLimitModal(true);
        if (!session) fetchAnonymousRemaining();
        const errorMsg = outOfCreditsMessage();
        setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'error', error: errorMsg } : x));
        return;
      }
      if (res.status === 413) {
        const errorData = await res.json().catch(() => ({})) as { error?: string };
        throw new Error(errorData.error || 'File is too large. Maximum allowed size is 20MB.');
      }
      if (!res.ok) {
        // Try to get error message from response header first (more reliable)
        const headerError = res.headers.get('X-Error-Message');
        const errorData = await res.json().catch(() => ({})) as { error?: string };
        let errorMessage = headerError || errorData.error || `Conversion failed: ${res.status}`;

        // Only provide fallback messages if no specific error was provided
        if (!headerError && !errorData.error) {
          if (res.status === 400) {
            errorMessage = 'Invalid GIF file or corrupted. Please try a different file.';
          } else if (res.status === 415) {
            errorMessage = 'Only GIF files are supported for frame extraction.';
          }
        }

        throw new Error(errorMessage);
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      setJobs((prev) => prev.map(job => job.id === j.id ? { ...job, status: 'done' as const, url, progress: 100 } : job));

      await refetchUserData();
      if (!session) fetchAnonymousRemaining();
    } catch (e: unknown) {
      clearInterval(progressInterval);
      let errorMessage = 'Unknown error occurred';

      if (e instanceof Error) {
        if (e.name === 'AbortError') {
          errorMessage = 'Request timed out. GIF conversion may take longer for large files.';
        } else if (e.message.includes('Failed to fetch') || e.message.includes('NetworkError')) {
          errorMessage = 'Network error. Please check your connection and try again.';
        } else {
          errorMessage = e.message;
        }
      }

      setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'error', error: errorMessage } : x));
    }
  };

  const start = async () => {
    const pending = jobs.filter(j => j.status === 'queued');

    // Check if user has credits before starting conversion (logged-in: auth store; anonymous: IP quota)
    const loggedInNoCredits = session && auth.freeRemaining === 0 && auth.paidCredits === 0;
    const anonymousNoCredits = !session && anonymousRemaining === 0;
    if (pending.length > 0 && (loggedInNoCredits || anonymousNoCredits)) {
      setShowLimitModal(true);
      const errorMsg = outOfCreditsMessage();
      setJobs((prev) => prev.map(job =>
        pending.some(p => p.id === job.id)
          ? { ...job, status: 'error', error: errorMsg }
          : job
      ));
      return;
    }

    for (const j of pending) {
      // Handle GIF conversions differently
      if (j.isGif) {
        await convertGif(j);
        continue;
      }

      // Regular image conversion — store target format so UI shows correct format per job
      const startTime = Date.now();
      setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'running', startTime, progress: 0, targetFormat: target } : x));

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
        // Add timeout handling for slow connections
        // GIF conversions need more time due to complexity
        const controller = new AbortController();
        const timeoutMs = target === 'gif' ? 60000 : 30000; // 60s for GIF, 30s for others
        const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

        const res = await fetch(`${API_URL}/api/convert`, {
          method: 'POST',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          body: form,
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        clearInterval(progressInterval);

        // Extract rate limit info from headers
        const remaining = res.headers.get('X-RateLimit-Remaining');
        if (remaining) {
          setRateLimitRemaining(parseInt(remaining));
        }

        if (res.status === 401) throw new Error('Please sign in with Google to convert.');
        if (res.status === 402) {
          setShowLimitModal(true);
          if (!session) fetchAnonymousRemaining();
          const errorMsg = outOfCreditsMessage();
          setJobs((prev) => prev.map(x => x.id === j.id ? { ...x, status: 'error', error: errorMsg } : x));
          return;
        }
        if (res.status === 413) {
          const errorData = await res.json().catch(() => ({})) as { error?: string };
          throw new Error(errorData.error || 'File is too large. Maximum allowed size is 20MB.');
        }
        if (!res.ok) {
          // Try to get error message from response header first (more reliable)
          const headerError = res.headers.get('X-Error-Message');
          const errorData = await res.json().catch(() => ({})) as { error?: string };
          let errorMessage = headerError || errorData.error || `Conversion failed: ${res.status}`;

          // Only provide fallback messages if no specific error was provided
          if (!headerError && !errorData.error) {
            if (res.status === 400) {
              errorMessage = 'Invalid file format or corrupted image. Please try a different file.';
            } else if (res.status === 415) {
              errorMessage = 'Unsupported file format. Please use standard image formats like .jpg, .png, or .webp.';
            } else if (res.status === 422) {
              errorMessage = 'File format not supported for conversion. Please try a different image format.';
            }
          }

          throw new Error(errorMessage);
        }
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        setJobs((prev) => prev.map(job => job.id === j.id ? { ...job, status: 'done' as const, url, progress: 100 } : job));

        await refetchUserData();
        if (!session) fetchAnonymousRemaining();
      } catch (e: unknown) {
        clearInterval(progressInterval);
        let errorMessage = 'Unknown error occurred';

        if (e instanceof Error) {
          if (e.name === 'AbortError') {
            errorMessage = 'Request timed out. Please check your internet connection and try again.';
          } else if (e.message.includes('Failed to fetch') || e.message.includes('NetworkError')) {
            errorMessage = 'Network error. Please check your connection and try again.';
          } else if (e.message.includes('ERR_TIMED_OUT') || e.message.includes('ERR_CONNECTION_RESET')) {
            errorMessage = 'Connection timeout. Please try again with a better internet connection.';
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

      {/* Anonymous remaining (when not logged in) - so you can test free conversions without signing in */}
      {!session && anonymousRemaining !== null && (
        <p className="text-sm text-slate-600" aria-live="polite">
          <strong>{anonymousRemaining}</strong> of {anonymousDailyLimit} free conversions left today.
          <span className="text-slate-500"> Sign in to use your subscription.</span>
        </p>
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

      {/* Queue summary + Convert button + progress list — above the fold */}
      {jobs.length > 0 && (
        <>
          {/* Queue & progress summary bar */}
          {(() => {
            const queued = jobs.filter(j => j.status === 'queued').length;
            const running = jobs.filter(j => j.status === 'running').length;
            const done = jobs.filter(j => j.status === 'done').length;
            const error = jobs.filter(j => j.status === 'error').length;
            const total = jobs.length;
            let message: string;
            if (running > 0) {
              message = `Converting ${running} of ${total}… ${done > 0 ? `${done} done` : ''}`.trim();
            } else if (queued === total) {
              message = `${total} file${total === 1 ? '' : 's'} in queue — set options and click Convert`;
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
                  onClick={() => {
                    jobs.forEach(job => { if (job.url) URL.revokeObjectURL(job.url); });
                    setJobs([]);
                  }}
                  className="text-xs text-sky-600 hover:text-sky-800 hover:bg-sky-200/50 px-2 py-1 rounded flex-shrink-0 transition-colors"
                  aria-label="Clear all files"
                >
                  Clear all
                </button>
              </div>
            );
          })()}

          {/* Convert button — above the fold when there are queued files */}
          <div>
            <button
              type="button"
              onClick={start}
              disabled={jobs.filter(j => j.status === 'queued').length === 0}
              aria-label={`Convert ${jobs.filter(j => j.status === 'queued').length} queued ${jobs.filter(j => j.status === 'queued').length === 1 ? 'image' : 'images'}`}
              className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-sky-600 px-4 py-3 text-white hover:bg-sky-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium shadow-sm"
            >
              <Wand2 size={18} aria-hidden="true" /> Convert {jobs.filter(j => j.status === 'queued').length > 0 ? `(${jobs.filter(j => j.status === 'queued').length})` : 'All'}
            </button>
          </div>

          {/* Job list with progress bars — above the fold */}
          <div className="grid gap-3" role="list" aria-label="Image conversion queue">
            {jobs.map((j) => (
              <div key={j.id} className="rounded-md border bg-white p-3 sm:p-4 shadow-sm" role="listitem">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
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
                          type="button"
                          onClick={() => setPreviewUrl(j.url || null)}
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
        </>
      )}

      <div className="rounded-lg border bg-white p-3 sm:p-4">
        {/* Drop Zone - Now at the top and bigger */}
        <div {...getRootProps()} className={"mb-6 border-2 border-dashed rounded-lg p-6 sm:p-16 text-center cursor-pointer " + dropClass} role="button" aria-label="Upload images - drag and drop or click to select. Maximum 20MB, 8000x8000 pixels. Supported formats: JPG, PNG, WebP, AVIF, GIF, HEIC, ICO" tabIndex={0}>
          <input {...getInputProps()} aria-label="File upload input" className="hidden" />
          <div className="flex flex-col items-center justify-center gap-2 sm:gap-3 text-slate-600">
            <Upload size={40} className="sm:w-12 sm:h-12 text-sky-500" aria-hidden="true" />
            <div className="text-sm sm:text-lg font-medium px-2">
              <span className="hidden sm:inline">Drag & drop images here, or click to select</span>
              <span className="sm:hidden">Tap to select images</span>
            </div>
            <div className="text-xs sm:text-sm text-slate-500 mt-1 px-2">
              <div className="hidden sm:block">Max: 20MB, {MAX_DIMENSION}x{MAX_DIMENSION}px • Formats: JPG, PNG, WebP, AVIF, GIF, HEIC, ICO</div>
              <div className="sm:hidden">Max: 20MB • JPG, PNG, WebP, AVIF, GIF, HEIC, ICO</div>
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

            {/* PNG warning for large images */}
            {(() => {
              if (target !== 'png') return null;

              const queuedJobs = jobs.filter(j => j.status === 'queued' && !j.isGif);
              if (queuedJobs.length === 0) return null;

              const maxDimension = Math.max(...queuedJobs.map(j => Math.max(j.width || 0, j.height || 0)));

              if (maxDimension > 4000) {
                return (
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
                );
              }
              return null;
            })()}

            {/* Auto-resize info for large images */}
            {(() => {
              const queuedJobs = jobs.filter(j => j.status === 'queued' && !j.isGif);
              if (queuedJobs.length === 0) return null;

              const maxDimension = Math.max(...queuedJobs.map(j => Math.max(j.width || 0, j.height || 0)));

              if (maxDimension > 1920) {
                return (
                  <div className="text-xs text-blue-700 bg-blue-50 border border-blue-200 rounded p-3 mt-3">
                    <div className="font-semibold mb-1">ℹ️ Auto-resize enabled</div>
                    <div>
                      Your {maxDimension}px image will be resized to 1920px before conversion to fit server limits.
                      For custom sizes, use the resize option below.
                    </div>
                  </div>
                );
              }
              return null;
            })()}
          </div>

          {/* GIF Multi-Format Selection */}
          {jobs.some(j => j.isGif && j.status === 'queued') && (
            <div className="border-t pt-4" role="group" aria-labelledby="gif-formats-label">
              <div className="flex items-center gap-2 mb-3">
                <div className="text-sm font-medium text-slate-700">GIF Frame Extraction</div>
                <span className="text-xs bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full">Multiple Formats</span>
              </div>
              <p className="text-xs text-slate-600 mb-3">
                Select multiple formats to extract GIF frames. All frames will be converted and bundled in a ZIP file.
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                {['png', 'jpg', 'webp', 'heic'].map(fmt => (
                  <button
                    key={fmt}
                    onClick={() => {
                      setGifFormats(prev =>
                        prev.includes(fmt)
                          ? prev.filter(f => f !== fmt)
                          : [...prev, fmt]
                      );
                    }}
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
          )}

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

              {/* Sharpness capping warning for large images */}
              {(() => {
                const queuedJobs = jobs.filter(j => j.status === 'queued');
                if (queuedJobs.length === 0) return null;

                const maxDimension = Math.max(...queuedJobs.map(j => Math.max(j.width || 0, j.height || 0)));

                if (maxDimension > 4000 && sharpness > 50) {
                  return (
                    <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded p-2 mt-2">
                      ⚠️ Large image detected ({maxDimension}px). Sharpness will be limited to 50% to respect server time limits. For higher sharpness, resize your image to ≤4000px first.
                    </div>
                  );
                } else if (maxDimension > 2000 && sharpness > 100) {
                  return (
                    <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded p-2 mt-2">
                      ⚠️ Large image detected ({maxDimension}px). Sharpness will be limited to 100% to respect server time limits. For higher sharpness, resize your image to ≤1920px first.
                    </div>
                  );
                }
                return null;
              })()}
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
      {showLimitModal && (() => {
        const stale = hasStaleSubscription();
        const softFreeOnly = !stale && !PAYMENTS_ENABLED;
        return (
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
                {softFreeOnly ? (
                  <div className="w-12 h-12 bg-sky-100 rounded-full flex items-center justify-center" aria-hidden="true">
                    <CalendarDays className="w-6 h-6 text-sky-700" aria-hidden="true" />
                  </div>
                ) : (
                  <div className="w-12 h-12 bg-amber-100 rounded-full flex items-center justify-center" aria-hidden="true">
                    <AlertTriangle className="w-6 h-6 text-amber-600" aria-hidden="true" />
                  </div>
                )}
              </div>
              <div className="flex-1">
                <h3 id="limit-modal-title" className="text-lg font-semibold text-slate-900 mb-2">
                  {stale
                    ? 'Session expired'
                    : softFreeOnly
                      ? session
                        ? "That's all for today"
                        : 'Thanks for trying Change-My'
                      : 'Conversion limit reached'}
                </h3>
                <p id="limit-modal-description" className="text-slate-600 mb-4 leading-relaxed">
                  {stale ? (
                    <>You have an active subscription, but your session has expired. Sign in again to use your remaining monthly conversions.</>
                  ) : session ? (
                    PAYMENTS_ENABLED ? (
                      <>You&apos;ve used all your conversions for today. Subscribe to get 1000 conversions per month for just $1.98/month.</>
                    ) : (
                      <>You&apos;ve used today&apos;s free conversions. <span className="font-medium text-slate-800">Come back tomorrow</span>—your quota resets every day. No payment required.</>
                    )
                  ) : PAYMENTS_ENABLED ? (
                    <>
                      You&apos;ve used the 20 free conversions for this session.
                      <span className="mt-2 block font-medium text-slate-800">Already subscribed? Sign in to use your monthly credits.</span>
                      New to Change-My? Sign in or subscribe for 1000 conversions/month at $1.98/month.
                    </>
                  ) : (
                    <>
                      You&apos;ve used today&apos;s guest conversions (20 per day per browser session).
                      <span className="mt-2 block font-medium text-slate-800">Sign in with Google</span> for a personal daily quota that follows you across devices—still free, still 20 per day.
                    </>
                  )}
                </p>
                <div className="flex flex-col gap-3">
                  {!session && (
                    <button
                      onClick={() => signIn('google')}
                      aria-label="Continue with Google"
                      className="w-full inline-flex items-center justify-center gap-2 rounded-md px-4 py-3 text-white text-sm font-semibold bg-sky-600 hover:bg-sky-700"
                    >
                      Continue with Google
                    </button>
                  )}
                  {PAYMENTS_ENABLED && !session && !stale && (
                    <button
                      onClick={() => router.push('/billing')}
                      aria-label="Go to billing page to subscribe"
                      className="w-full inline-flex items-center justify-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2.5 text-slate-700 hover:bg-slate-50 text-sm font-medium"
                    >
                      I don&apos;t have an account — Subscribe
                    </button>
                  )}
                  {!PAYMENTS_ENABLED && !session && !stale && (
                    <button
                      type="button"
                      onClick={() => router.push('/billing')}
                      aria-label="Learn about plans and daily limits"
                      className="w-full inline-flex items-center justify-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-4 py-2.5 text-slate-800 hover:bg-slate-100 text-sm font-medium"
                    >
                      How daily limits work
                    </button>
                  )}
                  <button
                    onClick={() => setShowLimitModal(false)}
                    aria-label="Close limit modal"
                    className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-slate-100 px-4 py-2.5 text-slate-600 hover:bg-slate-200 text-sm font-medium"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        );
      })()}

      {/* Contextual Messaging for Target Audiences */}
      <div className="mt-8 grid md:grid-cols-2 gap-4">
        <div className="rounded-lg border bg-sky-50 border-sky-200 p-4">
          <div className="flex items-start gap-3">
            <Lightbulb className="text-sky-600 mt-0.5 flex-shrink-0" size={20} />
            <div>
              <h3 className="font-semibold text-sky-900 mb-1">💡 For Developers</h3>
              <p className="text-sm text-sky-800">
                Convert to WebP for up to 30% smaller file sizes with the same visual quality
              </p>
            </div>
          </div>
        </div>
        <div className="rounded-lg border bg-emerald-50 border-emerald-200 p-4">
          <div className="flex items-start gap-3">
            <Lightbulb className="text-emerald-600 mt-0.5 flex-shrink-0" size={20} />
            <div>
              <h3 className="font-semibold text-emerald-900 mb-1">💡 For Bloggers</h3>
              <p className="text-sm text-emerald-800">
                Use sharpening to enhance image clarity after compression
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}