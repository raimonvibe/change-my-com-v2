package com.raimonvibe.imageconverter.security;

import com.raimonvibe.imageconverter.config.ConversionLimits;
import com.raimonvibe.imageconverter.image.ImageFormats;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Upload validation implemented as a Chain of Responsibility: an ordered list
 * of {@link ValidationStep}s (presence/size, extension whitelist, magic-byte
 * signature, MIME type) that each inspect the shared {@link ValidationContext}
 * and throw on failure. Magic-byte format detection uses a registry of
 * signature matchers instead of a hardcoded if-chain.
 */
public final class FileValidator {
    private static final Set<String> ALLOWED_EXT = ImageFormats.ALLOWED_INPUT_FORMATS;
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
    private static final long MAX_BYTES = ConversionLimits.MAX_UPLOAD_BYTES;

    private FileValidator() {}

    /** Mutable state shared along the validation chain. */
    private static final class ValidationContext {
        final MultipartFile file;
        String extension;
        byte[] head;

        ValidationContext(MultipartFile file) {
            this.file = file;
        }
    }

    /** One link in the validation chain; throws to reject the upload. */
    @FunctionalInterface
    private interface ValidationStep {
        void check(ValidationContext ctx) throws IOException, IllegalArgumentException;
    }

    /** Ordered validation chain: cheap checks first, I/O-dependent checks later. */
    private static final List<ValidationStep> VALIDATION_CHAIN = List.of(
        FileValidator::checkPresenceAndSize,
        FileValidator::checkExtension,
        FileValidator::checkMagicBytes,
        FileValidator::checkMimeType
    );

    /** Magic-byte signature matcher registry: first match wins. */
    private record FormatSignature(String format, Predicate<byte[]> matches) {}

    private static final List<FormatSignature> FORMAT_SIGNATURES = List.of(
        new FormatSignature("png", FileValidator::isPng),
        new FormatSignature("jpg", FileValidator::isJpeg),
        new FormatSignature("gif", FileValidator::isGif),
        new FormatSignature("webp", FileValidator::isWebp),
        new FormatSignature("avif", FileValidator::isAvif),
        new FormatSignature("heic", FileValidator::isHeic),
        new FormatSignature("ico", FileValidator::isIco),
        new FormatSignature("bmp", FileValidator::isBmp),
        new FormatSignature("tiff", FileValidator::isTiff)
    );

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

            return FORMAT_SIGNATURES.stream()
                .filter(sig -> sig.matches().test(head))
                .map(FormatSignature::format)
                .findFirst()
                .orElse(null);
        }
    }

    /** Runs the upload through the full validation chain. */
    public static void validate(MultipartFile file) throws IOException, IllegalArgumentException {
        ValidationContext ctx = new ValidationContext(file);
        for (ValidationStep step : VALIDATION_CHAIN) {
            step.check(ctx);
        }
    }

    // ===== Chain links =====

    private static void checkPresenceAndSize(ValidationContext ctx) {
        if (ctx.file == null || ctx.file.isEmpty()) throw new IllegalArgumentException("File is empty.");
        if (ctx.file.getSize() > MAX_BYTES) throw new IllegalArgumentException("File too large.");
    }

    private static void checkExtension(ValidationContext ctx) {
        String original = ctx.file.getOriginalFilename() == null ? "" : ctx.file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) throw new IllegalArgumentException("Unsupported extension.");
        ctx.extension = ext;
    }

    private static void checkMagicBytes(ValidationContext ctx) throws IOException {
        try (InputStream in = ctx.file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (head.length < 2) throw new IllegalArgumentException("Invalid file.");

            // Check for suspicious content in file header
            if (containsSuspiciousContent(head)) {
                throw new IllegalArgumentException("File contains suspicious content.");
            }

            // Validate against known safe magic bytes
            boolean recognized = FORMAT_SIGNATURES.stream().anyMatch(sig -> sig.matches().test(head));
            if (!recognized) {
                throw new IllegalArgumentException("Invalid or unsupported image signature.");
            }

            ctx.head = head;
        }
    }

    private static void checkMimeType(ValidationContext ctx) {
        // Security: HEIC files may be uploaded with incorrect MIME types (e.g., application/octet-stream or null)
        // We ONLY allow HEIC files with wrong/null MIME type if ALL earlier chain links passed:
        // 1. Magic bytes match HEIC signature
        // 2. Extension is .heic or .heif
        // 3. No suspicious content detected
        // This prevents bypassing validation with other file types
        String contentType = ctx.file.getContentType();
        boolean isHeicFile = isHeic(ctx.head);
        boolean isHeicExtension = "heic".equals(ctx.extension) || "heif".equals(ctx.extension);
        boolean heicExemption = isHeicFile && isHeicExtension;

        if (contentType == null || contentType.trim().isEmpty()) {
            // Security: Reject null MIME types except for verified HEIC files
            if (!heicExemption) {
                throw new IllegalArgumentException("Unsupported MIME type.");
            }
            return;
        }

        // For non-null MIME types, validate against whitelist (HEIC exemption applies)
        if (!ALLOWED_MIME.contains(contentType.toLowerCase()) && !heicExemption) {
            throw new IllegalArgumentException("Unsupported MIME type.");
        }
    }

    // ===== Magic-byte signatures =====

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
        if (b == null || b.length<12) return false;
        if (b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p') {
            // Check for exact HEIC/HEIF brand identifiers at bytes 8-11
            if (b[8]=='h' && b[9]=='e' && b[10]=='i' && b[11]=='c') return true;
            if (b[8]=='h' && b[9]=='e' && b[10]=='i' && b[11]=='f') return true;
            if (b[8]=='m' && b[9]=='i' && b[10]=='f' && b[11]=='1') return true;
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
