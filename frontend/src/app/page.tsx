'use client';

import React, { useEffect, useState } from "react";
import { useSession, signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useDropzone, FileRejection } from "react-dropzone";
import { Upload, Wand2, AlertTriangle, X, Lightbulb } from "lucide-react";
import { useAuthStore } from "../store/useAuthStore";
import { useUserData } from "../hooks/useUserData";
import { useConversionQueue } from "../hooks/useConversionQueue";
import { useImageValidation } from "../hooks/useImageValidation";
import { apiFetch } from "../lib/apiClient";
import {
  DROPZONE_ACCEPT,
  FREE_DAILY_LIMIT,
  MAX_DIMENSION,
  MAX_FILE_SIZE,
  QUALITY_RANGE,
  RESIZE_RANGE,
  SHARPNESS_RANGE,
} from "../lib/conversionConfig";
import { Job } from "../lib/conversionApi";
import { PAYMENTS_ENABLED } from "../lib/paymentsConfig";
import { FormatPicker } from "../components/convert/FormatPicker";
import { GifFormatPicker } from "../components/convert/GifFormatPicker";
import { ImageSettings } from "../components/convert/ImageSettings";
import { JobQueue } from "../components/convert/JobQueue";
import { LimitReachedModal } from "../components/convert/LimitReachedModal";
import { PreviewModal } from "../components/convert/PreviewModal";

// Note: Metadata is set in layout.tsx since this is a client component.
// We deliberately do NOT persist uploaded images or conversion results
// for security and privacy reasons. Images are only kept in memory during the session.

export default function ConvertPage() {
  const router = useRouter();
  const { data: session } = useSession();
  const auth = useAuthStore(s => s);
  const { refetch: refetchUserData } = useUserData(); // Handles session sync automatically
  const [target, setTarget] = useState('webp');
  const [quality, setQuality] = useState(QUALITY_RANGE.default);
  const [sharpness, setSharpness] = useState(SHARPNESS_RANGE.default);
  const [resizeEnabled, setResizeEnabled] = useState(false);
  const [maxWidth, setMaxWidth] = useState(RESIZE_RANGE.default);
  const { jobs, addJobs, removeJob, clearJobs, markPendingAsError, startAll } = useConversionQueue();
  const { validateExtension, validateDimensions } = useImageValidation();
  const convertActionRef = React.useRef<HTMLDivElement>(null);
  const prevQueuedCountRef = React.useRef(0);
  const queuedCount = jobs.filter((j) => j.status === 'queued').length;
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [showLimitModal, setShowLimitModal] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [rateLimitRemaining, setRateLimitRemaining] = useState<number | null>(null);
  const [gifFormats, setGifFormats] = useState<string[]>(['png', 'jpg']);
  const [anonymousRemaining, setAnonymousRemaining] = useState<number | null>(null);
  const [anonymousDailyLimit, setAnonymousDailyLimit] = useState(FREE_DAILY_LIMIT);
  const token = session?.idToken as string | undefined;

  // Fetch anonymous (IP-based) remaining conversions when not logged in. Used for UX and testing.
  // State updates happen in promise callbacks (async, after the response arrives).
  const fetchAnonymousRemaining = React.useCallback(() => {
    if (session) return;
    apiFetch('/api/anonymous/remaining')
      .then(async (res) => {
        if (!res.ok) return;
        const data = await res.json() as { remaining: number; dailyLimit: number };
        setAnonymousRemaining(data.remaining);
        setAnonymousDailyLimit(data.dailyLimit ?? FREE_DAILY_LIMIT);
      })
      .catch(() => setAnonymousRemaining(null));
  }, [session]);

  // When not logged in, load anonymous remaining on mount so user can see "X of 20 left today".
  // The banner rendering is already gated on !session, so no reset is needed when logged in.
  useEffect(() => {
    if (!session) fetchAnonymousRemaining();
  }, [session, fetchAnonymousRemaining]);

  // After upload, scroll the Convert action into view so it is not missed below the queue.
  useEffect(() => {
    if (queuedCount > 0 && prevQueuedCountRef.current === 0) {
      convertActionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    prevQueuedCountRef.current = queuedCount;
  }, [queuedCount]);

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

  const showTransientError = (message: string) => {
    setErrorMessage(message);
    setTimeout(() => setErrorMessage(null), 5000);
  };

  const onDrop = async (acceptedFiles: File[], rejectedFiles: FileRejection[]) => {
    // Handle rejected files (too large, wrong type, etc.)
    if (rejectedFiles.length > 0) {
      const rejectedFile = rejectedFiles[0];
      if (rejectedFile.errors.some((e) => e.code === 'file-too-large')) {
        showTransientError(`File "${rejectedFile.file.name}" is too large. Maximum allowed size is 20MB.`);
        return;
      }
      if (rejectedFile.errors.some((e) => e.code === 'file-invalid-type')) {
        showTransientError(`File "${rejectedFile.file.name}" has an unsupported format. Only images are allowed.`);
        return;
      }
    }

    // Validate file extensions first
    for (const file of acceptedFiles) {
      const extensionValidation = validateExtension(file.name);
      if (!extensionValidation.valid) {
        showTransientError(`File "${file.name}": ${extensionValidation.error}`);
        return;
      }
    }

    // Validate file sizes and dimensions for accepted files
    const validatedJobs: Job[] = [];
    let largestWidth = 0;

    for (const file of acceptedFiles) {
      if (file.size > MAX_FILE_SIZE) {
        showTransientError(`File "${file.name}" is too large (${Math.round(file.size / 1024 / 1024)}MB). Maximum allowed size is 20MB.`);
        continue;
      }

      // Validate dimensions
      const validation = await validateDimensions(file);
      if (!validation.valid) {
        showTransientError(validation.error || 'Invalid image');
        continue;
      }

      // Track the largest width for setting default resize value
      if (validation.width && validation.width > largestWidth) {
        largestWidth = validation.width;
      }

      validatedJobs.push({
        id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        file,
        status: 'queued',
        isGif: file.type === 'image/gif',
        width: validation.width,
        height: validation.height,
      });
    }

    if (validatedJobs.length > 0) {
      addJobs(validatedJobs);

      // Set maxWidth to the largest uploaded image's width (capped at 8000px)
      // This gives users a sensible default that matches their content
      if (largestWidth > 0) {
        setMaxWidth(Math.min(largestWidth, RESIZE_RANGE.max));
      }
    }
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
    maxSize: MAX_FILE_SIZE,
    accept: DROPZONE_ACCEPT,
  });
  const dropClass = isDragActive ? 'border-sky-500 bg-sky-50' : 'border-slate-300';

  const start = async () => {
    const pending = jobs.filter(j => j.status === 'queued');

    // Check if user has credits before starting conversion (logged-in: auth store; anonymous: IP quota)
    const loggedInNoCredits = session && auth.freeRemaining === 0 && auth.paidCredits === 0;
    const anonymousNoCredits = !session && anonymousRemaining === 0;
    if (pending.length > 0 && (loggedInNoCredits || anonymousNoCredits)) {
      setShowLimitModal(true);
      markPendingAsError(outOfCreditsMessage());
      return;
    }

    await startAll(
      { target, quality, sharpness, resizeEnabled, maxWidth, gifFormats },
      token,
      outOfCreditsMessage,
      {
        onLimitReached: () => {
          setShowLimitModal(true);
          if (!session) fetchAnonymousRemaining();
        },
        onConverted: async () => {
          await refetchUserData();
          if (!session) fetchAnonymousRemaining();
        },
        onRateLimitRemaining: setRateLimitRemaining,
      }
    );
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
          <FormatPicker target={target} onSelect={setTarget} jobs={jobs} />

          {jobs.some(j => j.isGif && j.status === 'queued') && (
            <GifFormatPicker
              gifFormats={gifFormats}
              onToggle={(fmt) => {
                setGifFormats(prev =>
                  prev.includes(fmt)
                    ? prev.filter(f => f !== fmt)
                    : [...prev, fmt]
                );
              }}
            />
          )}

          <ImageSettings
            quality={quality}
            onQualityChange={setQuality}
            sharpness={sharpness}
            onSharpnessChange={setSharpness}
            resizeEnabled={resizeEnabled}
            onResizeEnabledChange={setResizeEnabled}
            maxWidth={maxWidth}
            onMaxWidthChange={setMaxWidth}
            jobs={jobs}
          />

          {/* Convert button — sticky when files are queued so it stays visible on mobile */}
          <div
            ref={convertActionRef}
            className={`border-t pt-4 ${queuedCount > 0 ? 'sticky bottom-3 z-30 -mx-1 rounded-lg bg-white/95 px-1 py-2 shadow-lg ring-1 ring-sky-100 backdrop-blur-sm' : ''}`}
          >
            {queuedCount > 0 ? (
              <p className="mb-2 text-center text-xs text-sky-800">
                {queuedCount} file{queuedCount === 1 ? '' : 's'} ready — click Convert below
              </p>
            ) : jobs.length > 0 ? (
              <p className="mb-2 text-center text-xs text-slate-500">
                All files converted. Upload more or clear the queue.
              </p>
            ) : (
              <p className="mb-2 text-center text-xs text-slate-500">
                Upload images above, then convert
              </p>
            )}
            <button
              type="button"
              onClick={start}
              disabled={queuedCount === 0}
              aria-label={`Convert ${queuedCount} queued ${queuedCount === 1 ? 'image' : 'images'}`}
              className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-sky-600 px-4 py-3 text-white hover:bg-sky-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium shadow-sm"
            >
              <Wand2 size={18} aria-hidden="true" /> Convert {queuedCount > 0 ? `(${queuedCount})` : 'All'}
            </button>
          </div>
        </div>
      </div>

      {/* Queue list — below upload/settings so Convert is always above the file list */}
      <JobQueue
        jobs={jobs}
        target={target}
        gifFormats={gifFormats}
        onRemove={removeJob}
        onClearAll={clearJobs}
        onPreview={setPreviewUrl}
      />

      <PreviewModal url={previewUrl} onClose={() => setPreviewUrl(null)} />

      <LimitReachedModal
        open={showLimitModal}
        signedIn={!!session}
        staleSubscription={hasStaleSubscription()}
        onClose={() => setShowLimitModal(false)}
        onSignIn={() => signIn('google')}
        onGoToBilling={() => router.push('/billing')}
      />

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
