import { MAX_DIMENSION } from '../lib/conversionConfig';
import { validateFileExtension } from '../lib/validation';

export type DimensionValidation = {
  valid: boolean;
  error?: string;
  width?: number;
  height?: number;
};

/**
 * Validates image dimensions in the browser and returns them.
 * HEIC/HEIF cannot be decoded by browsers, so those skip the check and the
 * backend validates instead.
 */
export function validateImageDimensions(file: File): Promise<DimensionValidation> {
  return new Promise((resolve) => {
    const extension = file.name.split('.').pop()?.toLowerCase();
    if (extension === 'heic' || extension === 'heif') {
      resolve({ valid: true }); // Skip dimension check for HEIC - backend will validate
      return;
    }

    const img = new Image();
    const url = URL.createObjectURL(file);

    img.onload = () => {
      URL.revokeObjectURL(url);
      if (img.width > MAX_DIMENSION || img.height > MAX_DIMENSION) {
        resolve({
          valid: false,
          error: `Image dimensions (${img.width}x${img.height}) exceed maximum allowed (${MAX_DIMENSION}x${MAX_DIMENSION}px)`,
        });
      } else {
        resolve({ valid: true, width: img.width, height: img.height });
      }
    };

    img.onerror = () => {
      URL.revokeObjectURL(url);
      // For HEIC/HEIF this is expected - browsers can't read them; backend validates
      if (extension === 'heic' || extension === 'heif') {
        resolve({ valid: true });
      } else {
        resolve({ valid: false, error: 'Unable to read image dimensions' });
      }
    };

    img.src = url;
  });
}

/**
 * Facade over the client-side upload checks (extension + dimensions),
 * so the convert page doesn't own validation logic.
 */
export function useImageValidation() {
  // Module-level functions are already referentially stable; no memoization needed.
  return { validateExtension: validateFileExtension, validateDimensions: validateImageDimensions };
}
