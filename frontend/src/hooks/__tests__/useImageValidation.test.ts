/**
 * useImageValidation Test Suite
 * Tests browser-side dimension validation (via a mocked Image) and the
 * validation facade hook.
 */

import { renderHook } from '@testing-library/react'
import { validateImageDimensions, useImageValidation } from '../useImageValidation'
import { validateFileExtension } from '../../lib/validation'
import { MAX_DIMENSION } from '../../lib/conversionConfig'

type MockImageBehavior = { width?: number; height?: number; fail?: boolean }

function mockImage(behavior: MockImageBehavior) {
  class FakeImage {
    onload: (() => void) | null = null
    onerror: (() => void) | null = null
    width = behavior.width ?? 0
    height = behavior.height ?? 0

    set src(_value: string) {
      queueMicrotask(() => {
        if (behavior.fail) this.onerror?.()
        else this.onload?.()
      })
    }
  }
  global.Image = FakeImage as unknown as typeof Image
}

describe('useImageValidation', () => {
  const originalImage = global.Image
  const originalCreateObjectURL = global.URL.createObjectURL
  const originalRevokeObjectURL = global.URL.revokeObjectURL

  beforeEach(() => {
    global.URL.createObjectURL = jest.fn(() => 'blob:mock')
    global.URL.revokeObjectURL = jest.fn()
  })

  afterEach(() => {
    global.Image = originalImage
    global.URL.createObjectURL = originalCreateObjectURL
    global.URL.revokeObjectURL = originalRevokeObjectURL
  })

  describe('validateImageDimensions', () => {
    it('should accept images within the dimension limit', async () => {
      mockImage({ width: 1920, height: 1080 })
      const file = new File(['x'], 'photo.jpg', { type: 'image/jpeg' })

      const result = await validateImageDimensions(file)

      expect(result).toEqual({ valid: true, width: 1920, height: 1080 })
      expect(global.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
    })

    it('should reject images exceeding the dimension limit', async () => {
      mockImage({ width: MAX_DIMENSION + 1, height: 100 })
      const file = new File(['x'], 'huge.png', { type: 'image/png' })

      const result = await validateImageDimensions(file)

      expect(result.valid).toBe(false)
      expect(result.error).toContain(`${MAX_DIMENSION}x${MAX_DIMENSION}`)
    })

    it('should skip the check for HEIC files (browser cannot decode)', async () => {
      const file = new File(['x'], 'photo.heic', { type: 'image/heic' })

      const result = await validateImageDimensions(file)

      expect(result).toEqual({ valid: true })
      // Never even creates an object URL for HEIC
      expect(global.URL.createObjectURL).not.toHaveBeenCalled()
    })

    it('should skip the check for HEIF files', async () => {
      const file = new File(['x'], 'photo.heif', { type: 'image/heif' })

      const result = await validateImageDimensions(file)

      expect(result).toEqual({ valid: true })
    })

    it('should reject unreadable non-HEIC images', async () => {
      mockImage({ fail: true })
      const file = new File(['x'], 'broken.jpg', { type: 'image/jpeg' })

      const result = await validateImageDimensions(file)

      expect(result.valid).toBe(false)
      expect(result.error).toContain('Unable to read image dimensions')
      expect(global.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
    })
  })

  describe('useImageValidation hook', () => {
    it('should expose the extension and dimension validators', () => {
      const { result } = renderHook(() => useImageValidation())

      expect(result.current.validateExtension).toBe(validateFileExtension)
      expect(result.current.validateDimensions).toBe(validateImageDimensions)
    })

    it('should validate extensions through the facade', () => {
      const { result } = renderHook(() => useImageValidation())

      expect(result.current.validateExtension('a.png').valid).toBe(true)
      expect(result.current.validateExtension('a.xyz').valid).toBe(false)
    })
  })
})
