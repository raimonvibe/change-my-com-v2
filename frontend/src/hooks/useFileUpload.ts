import { useState, useCallback } from 'react'

/**
 * useFileUpload Hook
 * Custom hook for managing file upload state
 */
export function useFileUpload() {
  const [files, setFiles] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const addFiles = useCallback((newFiles: File[]) => {
    setFiles(prev => [...prev, ...newFiles])
    setError(null)
  }, [])

  const removeFile = useCallback((index: number) => {
    setFiles(prev => prev.filter((_, i) => i !== index))
  }, [])

  const clearFiles = useCallback(() => {
    setFiles([])
    setError(null)
  }, [])

  const uploadFiles = useCallback(async (uploadFn: (files: File[]) => Promise<void>) => {
    setUploading(true)
    setError(null)
    try {
      await uploadFn(files)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }, [files])

  return {
    files,
    uploading,
    error,
    addFiles,
    removeFile,
    clearFiles,
    uploadFiles,
  }
}
