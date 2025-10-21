import { renderHook, act, waitFor } from '@testing-library/react'
import { useFileUpload } from '../useFileUpload'

describe('useFileUpload Hook', () => {
  it('should initialize with empty files array', () => {
    const { result } = renderHook(() => useFileUpload())

    expect(result.current.files).toEqual([])
    expect(result.current.uploading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('should add files', () => {
    const { result } = renderHook(() => useFileUpload())

    const file1 = new File(['content'], 'test1.jpg', { type: 'image/jpeg' })
    const file2 = new File(['content'], 'test2.png', { type: 'image/png' })

    act(() => {
      result.current.addFiles([file1, file2])
    })

    expect(result.current.files).toHaveLength(2)
    expect(result.current.files[0]).toBe(file1)
    expect(result.current.files[1]).toBe(file2)
  })

  it('should remove file by index', () => {
    const { result } = renderHook(() => useFileUpload())

    const file1 = new File(['content'], 'test1.jpg', { type: 'image/jpeg' })
    const file2 = new File(['content'], 'test2.png', { type: 'image/png' })

    act(() => {
      result.current.addFiles([file1, file2])
    })

    act(() => {
      result.current.removeFile(0)
    })

    expect(result.current.files).toHaveLength(1)
    expect(result.current.files[0]).toBe(file2)
  })

  it('should clear all files', () => {
    const { result } = renderHook(() => useFileUpload())

    const file1 = new File(['content'], 'test1.jpg', { type: 'image/jpeg' })

    act(() => {
      result.current.addFiles([file1])
    })

    expect(result.current.files).toHaveLength(1)

    act(() => {
      result.current.clearFiles()
    })

    expect(result.current.files).toHaveLength(0)
  })

  it('should clear error when adding files', () => {
    const { result } = renderHook(() => useFileUpload())

    // Simulate an error
    act(() => {
      result.current.uploadFiles(async () => {
        throw new Error('Test error')
      })
    })

    // Add files should clear error
    act(() => {
      result.current.addFiles([new File(['content'], 'test.jpg', { type: 'image/jpeg' })])
    })

    expect(result.current.error).toBeNull()
  })

  it('should handle successful upload', async () => {
    const { result } = renderHook(() => useFileUpload())

    const file = new File(['content'], 'test.jpg', { type: 'image/jpeg' })
    const mockUpload = jest.fn().mockResolvedValue(undefined)

    act(() => {
      result.current.addFiles([file])
    })

    await act(async () => {
      await result.current.uploadFiles(mockUpload)
    })

    expect(mockUpload).toHaveBeenCalledWith([file])
    expect(result.current.uploading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('should handle upload error', async () => {
    const { result } = renderHook(() => useFileUpload())

    const file = new File(['content'], 'test.jpg', { type: 'image/jpeg' })
    const mockUpload = jest.fn().mockRejectedValue(new Error('Upload failed'))

    act(() => {
      result.current.addFiles([file])
    })

    await act(async () => {
      await result.current.uploadFiles(mockUpload)
    })

    expect(result.current.uploading).toBe(false)
    expect(result.current.error).toBe('Upload failed')
  })

  it('should set uploading state during upload', async () => {
    const { result } = renderHook(() => useFileUpload())

    const file = new File(['content'], 'test.jpg', { type: 'image/jpeg' })
    let resolveUpload: () => void
    const mockUpload = jest.fn(() => new Promise<void>(resolve => {
      resolveUpload = resolve
    }))

    act(() => {
      result.current.addFiles([file])
    })

    // Start upload without awaiting
    act(() => {
      result.current.uploadFiles(mockUpload)
    })

    // Wait for uploading state to be true
    await waitFor(() => {
      expect(result.current.uploading).toBe(true)
    })

    // Resolve upload
    await act(async () => {
      resolveUpload!()
      await new Promise(resolve => setTimeout(resolve, 0))
    })

    // Should no longer be uploading
    expect(result.current.uploading).toBe(false)
  })

  it('should handle non-Error objects in upload failure', async () => {
    const { result } = renderHook(() => useFileUpload())

    const file = new File(['content'], 'test.jpg', { type: 'image/jpeg' })
    const mockUpload = jest.fn().mockRejectedValue('String error')

    await act(async () => {
      result.current.addFiles([file])
    })

    await act(async () => {
      await result.current.uploadFiles(mockUpload)
    })

    expect(result.current.error).toBe('Upload failed')
  })

  it('should maintain file order', async () => {
    const { result } = renderHook(() => useFileUpload())

    const file1 = new File(['1'], 'test1.jpg', { type: 'image/jpeg' })
    const file2 = new File(['2'], 'test2.jpg', { type: 'image/jpeg' })
    const file3 = new File(['3'], 'test3.jpg', { type: 'image/jpeg' })

    await act(async () => {
      result.current.addFiles([file1])
    })

    await act(async () => {
      result.current.addFiles([file2])
    })

    await act(async () => {
      result.current.addFiles([file3])
    })

    expect(result.current.files[0]).toBe(file1)
    expect(result.current.files[1]).toBe(file2)
    expect(result.current.files[2]).toBe(file3)
  })
})
