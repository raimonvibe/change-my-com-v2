# Icons and Favicon Setup

## Required Files

To ensure your favicon and app icon display correctly across all platforms, add these files to the `/public` folder:

### Current Status
- ✅ `favicon.ico` - Already exists (4.2KB)
- ❌ `icon.png` - **NEEDED** (192x192px PNG)
- ❌ `apple-icon.png` - **NEEDED** (180x180px PNG for iOS)

## How to Add Your Logo

1. **Prepare your logo image** (ideally a square PNG with transparent background)

2. **Create icon.png** (192x192px):
   ```bash
   # If you have ImageMagick installed:
   convert your-logo.png -resize 192x192 public/icon.png

   # Or use an online tool like:
   # - https://favicon.io/favicon-converter/
   # - https://realfavicongenerator.net/
   ```

3. **Create apple-icon.png** (180x180px):
   ```bash
   convert your-logo.png -resize 180x180 public/apple-icon.png
   ```

## What These Files Are For

- **favicon.ico**: Browser tab icon (all browsers)
- **icon.png**: Used for:
  - Android home screen
  - PWA icon
  - Link previews (Open Graph)
  - Twitter card images
- **apple-icon.png**: iOS Safari "Add to Home Screen" icon

## Testing

After adding the files:
1. Clear browser cache
2. Test in incognito/private mode
3. Share a link on social media to see preview
4. Check browser tab for favicon

## Quick Option

If you just want to convert your existing favicon.ico to PNG:
```bash
# Install ImageMagick first if needed
convert public/favicon.ico public/icon.png
convert public/favicon.ico -resize 180x180 public/apple-icon.png
```
