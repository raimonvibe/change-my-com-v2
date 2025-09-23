'use client';
import React, { useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import { useDropzone } from "react-dropzone";
import { API_URL } from "../../env";
import { Download, Upload, Wand2, AlertTriangle, Eye, CheckCircle } from "lucide-react";

const FORMATS = ["jpg","jpeg","png","webp","avif","heic","tiff","bmp","gif","svg"];

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
  const token = (session as any)?.idToken;

  const onDrop = (acceptedFiles: File[]) => {
    const newJobs = acceptedFiles.map((f) => ({ file: f, status: 'queued' } as Job));
    setJobs((prev) => [...prev, ...newJobs]);
  };
  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop, multiple: true });
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
          headers: token ? ({ Authorization: `Bearer ${token}` } as HeadersInit) : undefined,
          body: form,
        });
        
        console.log('Response status:', res.status);
        console.log('Response ok:', res.ok);
        
        if (res.status === 401) throw new Error('Please sign in with Google to convert.');
        if (res.status === 402) throw new Error('Daily limit reached (20 free conversions). Sign in and subscribe for unlimited conversions.');
        if (!res.ok) throw new Error(`Conversion failed: ${res.status}`);
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
      } catch (e: any) {
        console.error('Conversion error:', e);
        setJobs((prev) => prev.map(x => x === j ? { ...x, status: 'error', error: e.message } : x));
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
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
          <div>
            <label className="block text-sm text-slate-600 mb-1">Target format</label>
            <select value={target} onChange={(e)=>setTarget(e.target.value)} className="w-full rounded-md border-slate-300 text-sm">
              {FORMATS.map(f => <option key={f} value={f}>{f.toUpperCase()}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm text-slate-600 mb-1">Quality</label>
            <input type="range" min={1} max={100} value={quality} onChange={(e)=>setQuality(Number(e.target.value))} className="w-full" />
            <div className="text-xs text-slate-500 mt-1">{quality}</div>
          </div>
          <div className="sm:col-span-2 lg:col-span-1 flex items-end">
            <button onClick={start} className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-md bg-sky-600 px-4 py-2 text-white hover:bg-sky-700 text-sm">
              <Wand2 size={16} /> Convert
            </button>
          </div>
        </div>
        <div {...getRootProps()} className={"mt-4 border-2 border-dashed rounded-lg p-4 sm:p-8 text-center " + dropClass}>
          <input {...getInputProps()} />
          <div className="flex items-center justify-center gap-2 text-slate-600 text-sm sm:text-base">
            <Upload size={16} /> <span className="hidden sm:inline">Drag & drop images here, or click to select</span><span className="sm:hidden">Tap to select images</span>
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
                    {Math.round(j.file.size/1024)} KB → {target.toUpperCase()}
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
                      onClick={() => window.open(j.url, '_blank')}
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
    </div>
  );
}