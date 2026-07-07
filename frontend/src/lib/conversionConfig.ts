/**
 * Conversion Configuration — single source of truth
 *
 * All format lists, size limits and UI presets for the conversion flow live
 * here. Previously these constants were duplicated between the convert page
 * and lib/validation.ts (with drifting values); every consumer now imports
 * from this module. Keep in sync with the backend whitelists in
 * ImageFormats.java and ConversionLimits.java.
 */

/** Maximum accepted upload size in bytes (backend enforces the same limit). */
export const MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

/** Maximum image width or height in pixels (backend enforces the same limit). */
export const MAX_DIMENSION = 8000;

/** Free conversions per day (mirrors backend ConversionLimits.FREE_DAILY_LIMIT). */
export const FREE_DAILY_LIMIT = 20;

/**
 * Output format groups for the format picker.
 * Only safe raster formats — SVG/PDF excluded for security,
 * TIFF & BMP excluded due to high resource requirements on limited server specs.
 */
export const FORMAT_GROUPS: Record<string, string[]> = {
  'Modern Web': ['webp', 'avif'],
  'Standard': ['jpg', 'png', 'gif'],
  'Mobile': ['heic'],
  'Other': ['ico'],
};

/** Formats selectable when extracting GIF frames to a ZIP. */
export const GIF_ZIP_FORMATS = ['png', 'jpg', 'webp', 'heic'];

/**
 * Accepted upload extensions. Wider than the output groups because some
 * formats (tiff, bmp, svg) can be recognized for a helpful error even though
 * the backend only converts the dropzone-accepted set.
 */
export const VALID_EXTENSIONS = [
  'jpg', 'jpeg', 'png', 'webp', 'avif', 'heic', 'tiff', 'bmp', 'gif', 'svg', 'ico',
];

/** MIME accept map for react-dropzone. */
export const DROPZONE_ACCEPT = {
  'image/*': ['.jpg', '.jpeg', '.png', '.webp', '.avif', '.gif', '.heic', '.heif', '.ico'],
};

/** Quick resize width presets shown in the resize panel. */
export const RESIZE_PRESETS: { width: number; label: string }[] = [
  { width: 256, label: 'Small Icon' },
  { width: 512, label: 'Favicon' },
  { width: 1024, label: 'Thumbnail' },
  { width: 1920, label: 'Full HD' },
  { width: 3840, label: '4K UHD' },
];

/** Quality slider range (backend validates 1-100). */
export const QUALITY_RANGE = { min: 1, max: 100, default: 85 };

/** Sharpness slider range (backend validates 0-200). */
export const SHARPNESS_RANGE = { min: 0, max: 200, default: 0 };

/** Resize width range (backend validates 16-8000). */
export const RESIZE_RANGE = { min: 16, max: 8000, default: 1920 };

/** Subscription plan copy (mirrors backend BillingPlan). */
export const PLAN = {
  monthlyCredits: 1000,
  priceUsd: 1.98,
} as const;
