# Test Fixtures

This directory contains test files for E2E testing.

## Image Files

All test images are small, optimized files for fast test execution.

### Included Fixtures:

1. **test-image.png** - Valid PNG file (1x1 pixel)
2. **test-image.jpg** - Valid JPEG file (1x1 pixel)
3. **test-image.webp** - Valid WebP file (1x1 pixel)
4. **test-image.gif** - Valid GIF file (1x1 pixel, animated)
5. **large-file.jpg** - File exceeding 20MB size limit (for validation tests)
6. **invalid-file.txt** - Text file (for type validation tests)

## Usage

```typescript
import path from 'path';

const testImagePath = path.join(__dirname, './fixtures/test-image.png');

await page.setInputFiles('input[type="file"]', testImagePath);
```

## Creating New Fixtures

To add new test fixtures:

1. Keep file sizes minimal (< 1KB for small images)
2. Use actual valid image formats
3. Document the purpose in this README
4. Use descriptive filenames

## Base64 Test Images (Alternative)

For tests that don't require file uploads, use base64:

```typescript
// 1x1 transparent GIF
const base64Image = 'R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
const buffer = Buffer.from(base64Image, 'base64');

const file = new File([buffer], 'test.gif', { type: 'image/gif' });
```

## Notes

- These fixtures are for testing purposes only
- Do not commit large files to the repository
- Always clean up uploaded files in test teardown
