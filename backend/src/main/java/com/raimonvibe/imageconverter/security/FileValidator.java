package com.raimonvibe.imageconverter.security;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public final class FileValidator {
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "webp", "avif");
    private static final Set<String> ALLOWED_MIME = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/webp",
            "image/avif"
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
            if (head.length < 4) throw new IllegalArgumentException("Invalid file.");
            
            // Check for suspicious content in file header
            if (containsSuspiciousContent(head)) {
                throw new IllegalArgumentException("File contains suspicious content.");
            }
            
            if (isPng(head) || isJpeg(head) || isWebp(head) || isAvif(head)) {
                return;
            }
            throw new IllegalArgumentException("Invalid or unsupported image signature.");
        }
    }

    private static boolean isPng(byte[] b){ return b[0]==(byte)0x89 && b[1]==0x50 && b[2]==0x4E && b[3]==0x47; }
    private static boolean isJpeg(byte[] b){ return b[0]==(byte)0xFF && b[1]==(byte)0xD8; }
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
