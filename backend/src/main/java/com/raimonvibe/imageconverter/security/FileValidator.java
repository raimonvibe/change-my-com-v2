package com.raimonvibe.imageconverter.security;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public final class FileValidator {
    private static final Set<String> ALLOWED_EXT = Set.of(
        "png", "jpg", "jpeg", "webp", "avif",
        "gif", "bmp", "tiff", "tif", "heic", "heif", "ico"
    );
    private static final Set<String> ALLOWED_MIME = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_GIF_VALUE,
            "image/webp",
            "image/avif",
            "image/bmp",
            "image/x-ms-bmp",
            "image/tiff",
            "image/heic",
            "image/heif",
            "image/x-icon",
            "image/vnd.microsoft.icon"
    );
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private FileValidator() {}

    /**
     * Detect image format from magic bytes.
     * This helps ImageMagick process files correctly by providing format hints.
     *
     * @param file The uploaded file
     * @return Format extension (png, jpg, gif, webp, avif, heic, ico, bmp, tiff) or null if unknown
     * @throws IOException if file cannot be read
     */
    public static String detectFormat(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (head.length < 2) return null;

            if (isPng(head)) return "png";
            if (isJpeg(head)) return "jpg";
            if (isGif(head)) return "gif";
            if (isWebp(head)) return "webp";
            if (isAvif(head)) return "avif";
            if (isHeic(head)) return "heic";
            if (isIco(head)) return "ico";
            if (isBmp(head)) return "bmp";
            if (isTiff(head)) return "tiff";

            return null;
        }
    }

    public static void validate(MultipartFile file) throws IOException, IllegalArgumentException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("File too large.");

        // Extension whitelist
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) throw new IllegalArgumentException("Unsupported extension.");

        // Magic numbers (quick checks) - do this first to detect format
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
            if (head.length < 2) throw new IllegalArgumentException("Invalid file.");

            // Check for suspicious content in file header
            if (containsSuspiciousContent(head)) {
                throw new IllegalArgumentException("File contains suspicious content.");
            }

            // Validate against known safe magic bytes
            if (!(isPng(head) || isJpeg(head) || isWebp(head) || isAvif(head) ||
                isGif(head) || isBmp(head) || isTiff(head) || isHeic(head) || isIco(head))) {
                throw new IllegalArgumentException("Invalid or unsupported image signature.");
            }
        }

        // MIME header validation
        // Security: HEIC files may be uploaded with incorrect MIME types (e.g., application/octet-stream)
        // We ONLY allow HEIC files with wrong MIME type if ALL security checks pass:
        // 1. Magic bytes match HEIC signature (already validated above)
        // 2. Extension is .heic or .heif (already validated above)
        // 3. No suspicious content detected (already validated above)
        // This prevents bypassing validation with other file types
        String contentType = file.getContentType();
        boolean isHeicFile = isHeic(head);
        boolean isHeicExtension = "heic".equals(ext) || "heif".equals(ext);
        
        if (contentType != null && !ALLOWED_MIME.contains(contentType.toLowerCase())) {
            // Security: Only allow wrong MIME type for HEIC if ALL conditions are met
            if (!isHeicFile || !isHeicExtension) {
                throw new IllegalArgumentException("Unsupported MIME type.");
            }
            // HEIC file with wrong MIME type - allow it since magic bytes AND extension match
        }
    }

    private static boolean isPng(byte[] b){
        return b.length >= 4 && b[0]==(byte)0x89 && b[1]==0x50 && b[2]==0x4E && b[3]==0x47;
    }

    private static boolean isJpeg(byte[] b){
        return b.length >= 2 && b[0]==(byte)0xFF && b[1]==(byte)0xD8;
    }

    private static boolean isWebp(byte[] b){
        // "RIFF....WEBP" at bytes 0..3 and 8..11
        return b.length>=12 && b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F' && b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';
    }

    private static boolean isAvif(byte[] b){
        // ISO BMFF: "ftyp" at 4..7 and brand contains "avif"/"avis"
        if (b.length<12) return false;
        if (b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p') {
            // Check for AVIF brand in bytes 8-11
            String brand = new String(b, 8, Math.min(4, b.length - 8));
            return brand.contains("avif") || brand.contains("avis");
        }
        return false;
    }

    private static boolean isGif(byte[] b){
        // GIF87a or GIF89a
        return b.length >= 6 && b[0]=='G' && b[1]=='I' && b[2]=='F' && b[3]=='8' &&
               (b[4]=='7' || b[4]=='9') && b[5]=='a';
    }

    private static boolean isBmp(byte[] b){
        // BM header
        return b.length >= 2 && b[0]=='B' && b[1]=='M';
    }

    private static boolean isTiff(byte[] b){
        // TIFF: "II" (little-endian) or "MM" (big-endian) followed by 42
        return b.length >= 4 &&
               ((b[0]=='I' && b[1]=='I' && b[2]==42 && b[3]==0) ||
                (b[0]=='M' && b[1]=='M' && b[2]==0 && b[3]==42));
    }

    private static boolean isHeic(byte[] b){
        // HEIC/HEIF uses ISO BMFF with ftyp containing heic/heif/mif1
        // Security: Check exact byte patterns to prevent false positives
        if (b.length<12) return false;
        if (b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p') {
            // Check for exact HEIC/HEIF brand identifiers at bytes 8-11
            // HEIC: bytes 8-11 should be "heic", "heif", or "mif1" (case-sensitive in ISO BMFF)
            if (b.length >= 12) {
                // Check for "heic" (0x68 0x65 0x69 0x63)
                if (b[8]=='h' && b[9]=='e' && b[10]=='i' && b[11]=='c') return true;
                // Check for "heif" (0x68 0x65 0x69 0x66)
                if (b[8]=='h' && b[9]=='e' && b[10]=='i' && b[11]=='f') return true;
                // Check for "mif1" (0x6D 0x69 0x66 0x31)
                if (b[8]=='m' && b[9]=='i' && b[10]=='f' && b[11]=='1') return true;
            }
        }
        return false;
    }

    private static boolean isIco(byte[] b){
        // ICO: starts with 0x00 0x00 0x01 0x00
        return b.length >= 4 && b[0]==0 && b[1]==0 && b[2]==1 && b[3]==0;
    }
    
    // Additional security: check for embedded scripts in image metadata
    private static boolean containsSuspiciousContent(byte[] data) {
        String content = new String(data, 0, Math.min(data.length, 1024));
        String lowerContent = content.toLowerCase();
        
        // Check for common script patterns in metadata
        return lowerContent.contains("<script") || 
               lowerContent.contains("javascript:") ||
               lowerContent.contains("vbscript:") ||
               lowerContent.contains("onload=") ||
               lowerContent.contains("onerror=");
    }
}
