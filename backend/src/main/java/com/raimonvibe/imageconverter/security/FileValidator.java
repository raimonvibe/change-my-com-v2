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

    public static void validate(MultipartFile file) throws IOException, IllegalArgumentException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("File too large.");

        // Extension whitelist
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) throw new IllegalArgumentException("Unsupported extension.");

        // MIME header
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported MIME type.");
        }

        // Magic numbers (quick checks)
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (head.length < 2) throw new IllegalArgumentException("Invalid file.");

            // Check for suspicious content in file header
            if (containsSuspiciousContent(head)) {
                throw new IllegalArgumentException("File contains suspicious content.");
            }

            // Validate against known safe magic bytes
            if (isPng(head) || isJpeg(head) || isWebp(head) || isAvif(head) ||
                isGif(head) || isBmp(head) || isTiff(head) || isHeic(head) || isIco(head)) {
                return;
            }
            throw new IllegalArgumentException("Invalid or unsupported image signature.");
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
        if (b.length<12) return false;
        if (b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p') {
            String brand = new String(b, 8, Math.min(4, b.length - 8));
            return brand.contains("heic") || brand.contains("heif") || brand.contains("mif1");
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
