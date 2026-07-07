/**
 * Validation Utilities
 * Client-side validation functions for file uploads and forms.
 * Limits come from lib/conversionConfig.ts (single source of truth).
 */

import { MAX_FILE_SIZE, VALID_EXTENSIONS } from './conversionConfig'

export { MAX_FILE_SIZE }
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

/**
 * Validates file size against MAX_FILE_SIZE limit
 */
export function validateFileSize(file: File): { valid: boolean; error?: string } {
  if (file.size > MAX_FILE_SIZE) {
    return {
      valid: false,
      error: `File size exceeds 20MB limit. Your file is ${(file.size / 1024 / 1024).toFixed(2)}MB`,
    }
  }
  return { valid: true }
}

/**
 * Validates file type against ALLOWED_IMAGE_TYPES
 */
export function validateFileType(file: File): { valid: boolean; error?: string } {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return {
      valid: false,
      error: `Invalid file type. Allowed types: JPG, PNG, WebP, AVIF, GIF, HEIC, BMP, TIFF, ICO`,
    }
  }
  return { valid: true }
}

/**
 * Validates a file for both size and type, collecting all errors
 */
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

/**
 * Validates email address format
 */
export function validateEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/**
 * Validates image quality value (1-100)
 */
export function validateQuality(quality: number): boolean {
  return quality >= 1 && quality <= 100
}

/**
 * Validates sharpness value (0-200)
 */
export function validateSharpness(sharpness: number): boolean {
  return sharpness >= 0 && sharpness <= 200
}

/**
 * Validates image dimensions (width and height must be between 16 and 8000 pixels)
 */
export function validateDimensions(width?: number, height?: number): { valid: boolean; error?: string } {
  if (width !== undefined && (width < 16 || width > 8000)) {
    return { valid: false, error: 'Width must be between 16 and 8000 pixels' }
  }
  if (height !== undefined && (height < 16 || height > 8000)) {
    return { valid: false, error: 'Height must be between 16 and 8000 pixels' }
  }
  return { valid: true }
}

/**
 * Validates a filename's extension against the accepted upload extensions,
 * with a helpful message for web downloads carrying platform suffixes
 * (":small", "_thumb", etc.). Moved here from the convert page.
 */
export function validateFileExtension(fileName: string): { valid: boolean; error?: string } {
  const extension = fileName.split('.').pop()?.toLowerCase()

  if (!extension) {
    return { valid: false, error: 'File has no extension' }
  }

  if (!VALID_EXTENSIONS.includes(extension)) {
    // Check if it's a common web download issue (like :small, :thumb, _small, _thumb, etc.)
    const commonSuffixes = [':small', ':thumb', ':medium', ':large', ':resized', ':compressed', ':1', ':2', ':3',
                           '_small', '_thumb', '_medium', '_large', '_resized', '_compressed', '_1', '_2', '_3']
    const hasCommonSuffix = commonSuffixes.some(suffix => fileName.toLowerCase().includes(suffix))

    if (hasCommonSuffix) {
      return {
        valid: false,
        error: `Invalid file format detected. This appears to be a web download with a platform suffix (like :small, _small, :thumb, _thumb). Please rename the file to remove the suffix (e.g., "image.jpg_small" → "image.jpg") and try again.`,
      }
    }

    return {
      valid: false,
      error: `Unsupported file format: .${extension}. Please use a standard image format like .jpg, .png, or .webp.`,
    }
  }

  return { valid: true }
}
