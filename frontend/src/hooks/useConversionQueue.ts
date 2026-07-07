import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ConversionSettings,
  Job,
  runConversion,
  strategyFor,
} from '../lib/conversionApi';

export type QueueCallbacks = {
  /** Called when the backend reports the credit limit was reached (402). */
  onLimitReached: () => void;
  /** Called after each finished conversion to refresh credit counters. */
  onConverted: () => void | Promise<void>;
  onRateLimitRemaining: (remaining: number) => void;
};

/**
 * Facade hook owning the conversion queue: job list state, blob URL lifecycle,
 * simulated progress, and the start loop that runs each queued job through
 * the strategy-based conversion pipeline. Extracted from the convert page.
 */
export function useConversionQueue() {
  const [jobs, setJobs] = useState<Job[]>([]);

  // Keep a ref to the latest jobs so the unmount cleanup can revoke blob URLs
  // WITHOUT re-running on every jobs change (a [jobs]-dependent cleanup used to
  // revoke still-in-use object URLs and break repeat downloads).
  const jobsRef = useRef<Job[]>([]);
  useEffect(() => {
    jobsRef.current = jobs;
  }, [jobs]);

  // Cleanup blob URLs only when the component unmounts (per-job removal is
  // handled in removeJob and clearJobs).
  useEffect(() => {
    return () => {
      jobsRef.current.forEach(job => {
        if (job.url) {
          URL.revokeObjectURL(job.url);
        }
      });
    };
  }, []);

  const addJobs = useCallback((newJobs: Job[]) => {
    setJobs(prev => [...prev, ...newJobs]);
  }, []);

  const removeJob = useCallback((jobId: string) => {
    setJobs(prev => {
      const jobToRemove = prev.find(j => j.id === jobId);
      // Revoke blob URL if it exists to prevent memory leak
      if (jobToRemove?.url) {
        URL.revokeObjectURL(jobToRemove.url);
      }
      return prev.filter(j => j.id !== jobId);
    });
  }, []);

  const clearJobs = useCallback(() => {
    setJobs(prev => {
      prev.forEach(job => { if (job.url) URL.revokeObjectURL(job.url); });
      return [];
    });
  }, []);

  const markPendingAsError = useCallback((message: string) => {
    setJobs(prev => prev.map(job =>
      job.status === 'queued' ? { ...job, status: 'error' as const, error: message } : job
    ));
  }, []);

  /** Simulated progress: the backend streams no progress, so estimate by file size. */
  const startProgressSimulation = useCallback((jobId: string, msPerMb: number) => {
    return setInterval(() => {
      setJobs(prev => prev.map(x => {
        if (x.id === jobId && x.status === 'running' && x.progress !== undefined) {
          const elapsed = Date.now() - (x.startTime || Date.now());
          const estimatedTime = (x.file.size / 1024 / 1024) * msPerMb;
          const newProgress = Math.min(90, Math.floor((elapsed / estimatedTime) * 100));
          return { ...x, progress: newProgress };
        }
        return x;
      }));
    }, 200);
  }, []);

  /**
   * Runs all queued jobs through the conversion pipeline. GIF jobs use the
   * ZIP endpoint; a credit-limit response on a raster job stops the queue
   * (matching previous behavior), while GIF jobs continue with the next job.
   */
  const startAll = useCallback(async (
    settings: ConversionSettings,
    token: string | undefined,
    limitErrorMessage: () => string,
    callbacks: QueueCallbacks
  ) => {
    const pending = jobsRef.current.filter(j => j.status === 'queued');

    for (const j of pending) {
      const strategy = strategyFor(j);
      const startTime = Date.now();
      const targetLabel = strategy.targetLabel(settings);

      setJobs(prev => prev.map(x =>
        x.id === j.id ? { ...x, status: 'running' as const, startTime, progress: 0, targetFormat: targetLabel } : x
      ));

      const progressInterval = startProgressSimulation(j.id, strategy.estimatedMsPerMb);

      const outcome = await runConversion(j, strategy, settings, {
        token,
        onRateLimitRemaining: callbacks.onRateLimitRemaining,
      });

      clearInterval(progressInterval);

      if (outcome.kind === 'done') {
        setJobs(prev => prev.map(job =>
          job.id === j.id ? { ...job, status: 'done' as const, url: outcome.url, progress: 100 } : job
        ));
        await callbacks.onConverted();
        continue;
      }

      if (outcome.kind === 'limit') {
        callbacks.onLimitReached();
        const errorMsg = limitErrorMessage();
        setJobs(prev => prev.map(x =>
          x.id === j.id ? { ...x, status: 'error' as const, error: errorMsg } : x
        ));
        if (strategy.stopQueueOnLimit) {
          return;
        }
        continue;
      }

      setJobs(prev => prev.map(x =>
        x.id === j.id ? { ...x, status: 'error' as const, error: outcome.message } : x
      ));
    }
  }, [startProgressSimulation]);

  return { jobs, addJobs, removeJob, clearJobs, markPendingAsError, startAll };
}
