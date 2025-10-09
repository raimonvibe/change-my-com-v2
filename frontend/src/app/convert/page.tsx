'use client';
import React, { useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import { useDropzone, FileRejection } from "react-dropzone";
import { API_URL } from "../../env";
import { Download, Upload, Wand2, AlertTriangle, Eye, CheckCircle } from "lucide-react";

// Organized format groups for better UX
// Only safe raster formats - SVG/PDF excluded for security
const FORMAT_GROUPS = {
  'Modern Web': ['webp', 'avif'],
  'Standard': ['jpg', 'png', 'gif'],
  'Professional': ['tiff', 'bmp'],
  'Mobile': ['heic'],
  'Other': ['ico']
};

const MAX_FILE_SIZE = 8 * 1024 * 1024; // 8MB in bytes

type Job = {
  file: File;
  status: 'queued' | 'running' | 'done' | 'error';
  url?: string;
  error?: string;
};

export default function ConvertPage() {
  const { data: session } = useSession();
  const [target, setTarget] = useState('webp');
  const [quality, setQuality] = useState(85);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const token = session?.idToken as string | undefined;

  const onDrop = (acceptedFiles: File[], rejectedFiles: FileRejection[]) => {
    // Handle rejected files (too large, wrong type, etc.)
    if (rejectedFiles.length > 0) {
      const rejectedFile = rejectedFiles[0];
      if (rejectedFile.errors.some((e) => e.code === 'file-too-large')) {
        alert(`Bestand "${rejectedFile.file.name}" is te groot. Maximum toegestane grootte is 8MB.`);
        return;
      }
      if (rejectedFile.errors.some((e) => e.code === 'file-invalid-type')) {
        alert(`Bestand "${rejectedFile.file.name}" heeft een niet-ondersteund formaat. Alleen afbeeldingen zijn toegestaan.`);
        return;
      }
    }

    // Validate file sizes for accepted files
    const validFiles = acceptedFiles.filter(file => {
      if (file.size > MAX_FILE_SIZE) {
        alert(`Bestand "${file.name}" is te groot (${Math.round(file.size / 1024 / 1024)}MB). Maximum toegestane grootte is 8MB.`);
        return false;
      }
      return true;
    });

    const newJobs = validFiles.map((f) => ({ file: f, status: 'queued' } as Job));
    setJobs((prev) => [...prev, ...newJobs]);
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
    maxSize: MAX_FILE_SIZE,
    accept: {
      'image/*': ['.jpg', '.jpeg', '.png', '.webp', '.avif', '.gif', '.bmp', '.tiff', '.tif', '.heic', '.heif', '.ico']
    }
  });
  const dropClass = isDragActive ? 'border-sky-500 bg-sky-50' : 'border-slate-300';

  const start = async () => {
    const pending = jobs.filter(j => j.status === 'queued');
    for (const j of pending) {
      setJobs((prev) => prev.map(x => x === j ? { ...x, status: 'running' } : x));
      const form = new FormData();
      form.append('file', j.file);
      form.append('to', target);
      form.append('quality', String(quality));
      try {
        console.log('Starting conversion for:', j.file.name, 'to', target);
        console.log('API_URL:', API_URL);
        console.log('Token present:', !!token);
        
        const res = await fetch(`${API_URL}/api/convert`, {
          method: 'POST',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          body: form,
        });
        
        console.log('Response status:', res.status);
        console.log('Response ok:', res.ok);
        
        if (res.status === 401) throw new Error('Please sign in with Google to convert.');
        if (res.status === 402) throw new Error('Daily limit reached (20 free conversions). Sign in and subscribe for unlimited conversions.');
        if (res.status === 413) {
          const errorData = await res.json().catch(() => ({}));
          throw new Error(errorData.error || 'Bestand is te groot. Maximum toegestane grootte is 8MB.');
        }
        if (!res.ok) {
          const errorData = await res.json().catch(() => ({}));
          throw new Error(errorData.error || `Conversion failed: ${res.status}`);
        }
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        console.log('Conversion successful, blob URL created:', url);
        console.log('Current job object:', j);
        setJobs((prev) => {
          console.log('Previous jobs state:', prev);
          const updated = prev.map(job => {
            if (job.file.name === j.file.name && job.status === 'running') {
              console.log('Updating job from running to done:', job);
              return { ...job, status: 'done' as const, url };
            }
            return job;
          });
          console.log('New jobs state:', updated);
          return updated;
        });
        setRefreshKey(prev => {
          console.log('Updating refresh key from', prev, 'to', prev + 1);
          return prev + 1;
        });
      } catch (e: unknown) {
        console.error('Conversion error:', e);
        const errorMessage = e instanceof Error ? e.message : 'Unknown error occurred';
        setJobs((prev) => prev.map(x => x === j ? { ...x, status: 'error', error: errorMessage } : x));
      }
    }
  };

  useEffect(() => {
    console.log('Jobs state changed:', jobs);
  }, [jobs]);

  useEffect(() => {
    console.log('RefreshKey changed:', refreshKey);
  }, [refreshKey]);

  return (
    <div className="space-y-4 sm:space-y-6">
      <h1 className="text-lg sm:text-xl font-semibold text-sky-800 flex items-center gap-2">
        <Wand2 className="text-sky-600" /> Convert Images
      </h1>
      <div className="rounded-lg border bg-white p-3 sm:p-4">
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

          {/* Quality Slider */}
          <div className="border-t pt-4">
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
        <div {...getRootProps()} className={"mt-4 border-2 border-dashed rounded-lg p-4 sm:p-8 text-center " + dropClass}>
          <input {...getInputProps()} />
          <div className="flex flex-col items-center justify-center gap-2 text-slate-600 text-sm sm:text-base">
            <div className="flex items-center gap-2">
              <Upload size={16} /> 
              <span className="hidden sm:inline">Drag & drop images here, or click to select</span>
              <span className="sm:hidden">Tap to select images</span>
            </div>
            <div className="text-xs text-slate-500 mt-1">
              Maximum bestandsgrootte: 8MB • Ondersteunde formaten: JPG, PNG, WebP, AVIF, GIF, BMP, TIFF, HEIC, ICO
            </div>
          </div>
        </div>
      </div>

      <div className="grid gap-3" key={refreshKey}>
        {jobs.map((j, idx) => (
          <div key={`${idx}-${j.status}-${refreshKey}`} className="rounded-md border bg-white p-3 sm:p-4 shadow-sm">
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
                    <div className="flex items-center gap-2 text-xs text-blue-600">
                      <div className="animate-spin h-3 w-3 border border-blue-600 border-t-transparent rounded-full"></div>
                      Converting...
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
    </div>
  );
}