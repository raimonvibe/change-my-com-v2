/**
 * Validation Utilities Test Suite
 * Tests client-side validation functions for file uploads and forms
 */

// File validation utilities
export const MAX_FILE_SIZE = 8 * 1024 * 1024 // 8MB
export const ALLOWED_IMAGE_TYPES = [
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/webp',
  'image/avif',
  'image/gif',
  'image/heic',
  'image/bmp',
  'image/tiff',
  'image/x-icon',
]

export function validateFileSize(file: File): { valid: boolean; error?: string } {
  if (file.size > MAX_FILE_SIZE) {
    return {
      valid: false,
      error: `File size exceeds 8MB limit. Your file is ${(file.size / 1024 / 1024).toFixed(2)}MB`,
    }
  }
  return { valid: true }
}

export function validateFileType(file: File): { valid: boolean; error?: string } {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return {
      valid: false,
      error: `Invalid file type. Allowed types: JPG, PNG, WebP, AVIF, GIF, HEIC, BMP, TIFF, ICO`,
    }
  }
  return { valid: true }
}

export function validateFile(file: File): { valid: boolean; errors: string[] } {
  const errors: string[] = []

  const sizeValidation = validateFileSize(file)
  if (!sizeValidation.valid && sizeValidation.error) {
    errors.push(sizeValidation.error)
  }

  const typeValidation = validateFileType(file)
  if (!typeValidation.valid && typeValidation.error) {
    errors.push(typeValidation.error)
  }

  return {
    valid: errors.length === 0,
    errors,
  }
}

export function validateEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

export function validateQuality(quality: number): boolean {
  return quality >= 1 && quality <= 100
}

export function validateSharpness(sharpness: number): boolean {
  return sharpness >= 0 && sharpness <= 200
}

export function validateDimensions(width?: number, height?: number): { valid: boolean; error?: string } {
  if (width !== undefined && (width < 16 || width > 8000)) {
    return { valid: false, error: 'Width must be between 16 and 8000 pixels' }
  }
  if (height !== undefined && (height < 16 || height > 8000)) {
    return { valid: false, error: 'Height must be between 16 and 8000 pixels' }
  }
  return { valid: true }
}

// Tests
describe('Validation Utilities', () => {
  describe('File Size Validation', () => {
    it('should accept files within size limit', () => {
      const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })
      const result = validateFileSize(file)
      expect(result.valid).toBe(true)
      expect(result.error).toBeUndefined()
    })

    it('should reject files exceeding 8MB', () => {
      const largeContent = new Array(9 * 1024 * 1024).fill('a').join('')
      const file = new File([largeContent], 'large.jpg', { type: 'image/jpeg' })
      const result = validateFileSize(file)
      expect(result.valid).toBe(false)
      expect(result.error).toContain('8MB')
    })

    it('should include file size in error message', () => {
      const largeContent = new Array(9 * 1024 * 1024).fill('a').join('')
      const file = new File([largeContent], 'large.jpg', { type: 'image/jpeg' })
      const result = validateFileSize(file)
      expect(result.error).toMatch(/\d+\.\d+MB/)
    })

    it('should accept file at exactly 8MB', () => {
      const content = new Array(8 * 1024 * 1024).fill('a').join('')
      const file = new File([content], 'max.jpg', { type: 'image/jpeg' })
      const result = validateFileSize(file)
      expect(result.valid).toBe(true)
    })
  })

  describe('File Type Validation', () => {
    it('should accept JPEG files', () => {
      const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })
      const result = validateFileType(file)
      expect(result.valid).toBe(true)
    })

    it('should accept PNG files', () => {
      const file = new File(['test'], 'test.png', { type: 'image/png' })
      const result = validateFileType(file)
      expect(result.valid).toBe(true)
    })

    it('should accept WebP files', () => {
      const file = new File(['test'], 'test.webp', { type: 'image/webp' })
      const result = validateFileType(file)
      expect(result.valid).toBe(true)
    })

    it('should accept AVIF files', () => {
      const file = new File(['test'], 'test.avif', { type: 'image/avif' })
      const result = validateFileType(file)
      expect(result.valid).toBe(true)
    })

    it('should accept GIF files', () => {
      const file = new File(['test'], 'test.gif', { type: 'image/gif' })
      const result = validateFileType(file)
      expect(result.valid).toBe(true)
    })

    it('should reject PDF files', () => {
      const file = new File(['test'], 'test.pdf', { type: 'application/pdf' })
      const result = validateFileType(file)
      expect(result.valid).toBe(false)
      expect(result.error).toContain('Invalid file type')
    })

    it('should reject text files', () => {
      const file = new File(['test'], 'test.txt', { type: 'text/plain' })
      const result = validateFileType(file)
      expect(result.valid).toBe(false)
    })

    it('should reject executable files', () => {
      const file = new File(['test'], 'malware.exe', { type: 'application/x-msdownload' })
      const result = validateFileType(file)
      expect(result.valid).toBe(false)
    })
  })

  describe('Complete File Validation', () => {
    it('should pass valid file', () => {
      const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })
      const result = validateFile(file)
      expect(result.valid).toBe(true)
      expect(result.errors).toHaveLength(0)
    })

    it('should collect multiple errors', () => {
      const largeContent = new Array(9 * 1024 * 1024).fill('a').join('')
      const file = new File([largeContent], 'test.txt', { type: 'text/plain' })
      const result = validateFile(file)
      expect(result.valid).toBe(false)
      expect(result.errors).toHaveLength(2)
    })

    it('should return all error messages', () => {
      const largeContent = new Array(9 * 1024 * 1024).fill('a').join('')
      const file = new File([largeContent], 'test.txt', { type: 'text/plain' })
      const result = validateFile(file)
      expect(result.errors.some(e => e.includes('8MB'))).toBe(true)
      expect(result.errors.some(e => e.includes('Invalid file type'))).toBe(true)
    })
  })

  describe('Email Validation', () => {
    it('should accept valid email addresses', () => {
      expect(validateEmail('test@example.com')).toBe(true)
      expect(validateEmail('user.name@domain.co.uk')).toBe(true)
      expect(validateEmail('user+tag@example.com')).toBe(true)
    })

    it('should reject invalid email addresses', () => {
      expect(validateEmail('invalid')).toBe(false)
      expect(validateEmail('@example.com')).toBe(false)
      expect(validateEmail('user@')).toBe(false)
      expect(validateEmail('user @example.com')).toBe(false)
      expect(validateEmail('')).toBe(false)
    })
  })

  describe('Quality Validation', () => {
    it('should accept valid quality values (1-100)', () => {
      expect(validateQuality(1)).toBe(true)
      expect(validateQuality(50)).toBe(true)
      expect(validateQuality(85)).toBe(true)
      expect(validateQuality(100)).toBe(true)
    })

    it('should reject quality values below 1', () => {
      expect(validateQuality(0)).toBe(false)
      expect(validateQuality(-10)).toBe(false)
    })

    it('should reject quality values above 100', () => {
      expect(validateQuality(101)).toBe(false)
      expect(validateQuality(200)).toBe(false)
    })
  })

  describe('Sharpness Validation', () => {
    it('should accept valid sharpness values (0-200)', () => {
      expect(validateSharpness(0)).toBe(true)
      expect(validateSharpness(100)).toBe(true)
      expect(validateSharpness(200)).toBe(true)
    })

    it('should reject negative sharpness', () => {
      expect(validateSharpness(-1)).toBe(false)
      expect(validateSharpness(-100)).toBe(false)
    })

    it('should reject sharpness above 200', () => {
      expect(validateSharpness(201)).toBe(false)
      expect(validateSharpness(500)).toBe(false)
    })
  })

  describe('Dimension Validation', () => {
    it('should accept valid dimensions', () => {
      const result = validateDimensions(1920, 1080)
      expect(result.valid).toBe(true)
      expect(result.error).toBeUndefined()
    })

    it('should accept undefined dimensions', () => {
      const result = validateDimensions(undefined, undefined)
      expect(result.valid).toBe(true)
    })

    it('should reject width below 16', () => {
      const result = validateDimensions(15, 1080)
      expect(result.valid).toBe(false)
      expect(result.error).toContain('Width')
    })

    it('should reject width above 8000', () => {
      const result = validateDimensions(8001, 1080)
      expect(result.valid).toBe(false)
      expect(result.error).toContain('Width')
    })

    it('should reject height below 16', () => {
      const result = validateDimensions(1920, 15)
      expect(result.valid).toBe(false)
      expect(result.error).toContain('Height')
    })

    it('should reject height above 8000', () => {
      const result = validateDimensions(1920, 8001)
      expect(result.valid).toBe(false)
      expect(result.error).toContain('Height')
    })

    it('should accept boundary values', () => {
      expect(validateDimensions(16, 16).valid).toBe(true)
      expect(validateDimensions(8000, 8000).valid).toBe(true)
    })
  })

  describe('Constants', () => {
    it('should export correct max file size', () => {
      expect(MAX_FILE_SIZE).toBe(8 * 1024 * 1024)
    })

    it('should include all supported image types', () => {
      expect(ALLOWED_IMAGE_TYPES).toContain('image/jpeg')
      expect(ALLOWED_IMAGE_TYPES).toContain('image/png')
      expect(ALLOWED_IMAGE_TYPES).toContain('image/webp')
      expect(ALLOWED_IMAGE_TYPES).toContain('image/avif')
      expect(ALLOWED_IMAGE_TYPES).toContain('image/gif')
    })

    it('should have at least 8 supported types', () => {
      expect(ALLOWED_IMAGE_TYPES.length).toBeGreaterThanOrEqual(8)
    })
  })
})
