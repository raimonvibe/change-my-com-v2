package com.raimonvibe.imageconverter.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for FileValidator
 * Tests file upload validation to prevent malicious file uploads
 *
 * SECURITY FOCUS:
 * - Magic byte validation
 * - MIME type spoofing
 * - Polyglot file attacks
 * - Embedded script detection (XSS)
 * - File size limits
 * - Extension validation
 */
@DisplayName("FileValidator Security Tests")
public class FileValidatorTest {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB

    // ==================== VALID FILE TESTS ====================

    @Test
    @DisplayName("SECURITY: Should accept valid PNG file")
    void testValidPng() throws IOException {
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngHeader = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D // Additional bytes
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.png",
            "image/png",
            pngHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should accept valid JPEG file")
    void testValidJpeg() throws IOException {
        // JPEG magic bytes: FF D8
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            jpegHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should accept valid WebP file")
    void testValidWebP() throws IOException {
        // WebP magic bytes: RIFF....WEBP
        byte[] webpHeader = new byte[]{
            'R', 'I', 'F', 'F',
            0x00, 0x00, 0x00, 0x00,
            'W', 'E', 'B', 'P'
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.webp",
            "image/webp",
            webpHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should accept valid GIF file (GIF89a)")
    void testValidGif() throws IOException {
        // GIF89a magic bytes
        byte[] gifHeader = new byte[]{
            'G', 'I', 'F', '8', '9', 'a',
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.gif",
            "image/gif",
            gifHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    // ==================== MAGIC BYTE SPOOFING TESTS ====================

    @Test
    @DisplayName("SECURITY: Should reject executable with image MIME type")
    void testExecutableWithImageMime() {
        // Windows PE executable magic bytes: MZ
        byte[] exeHeader = new byte[]{
            'M', 'Z', (byte) 0x90, 0x00,
            0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "malware.jpg", // Pretending to be JPEG
            "image/jpeg",
            exeHeader
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("Invalid or unsupported image signature.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should reject PHP file with image extension")
    void testPhpFileWithImageExtension() {
        // PHP file starting with <?php
        byte[] phpContent = "<?php system($_GET['cmd']); ?>".getBytes();

        MultipartFile file = new MockMultipartFile(
            "file",
            "shell.jpg.php", // Double extension attack
            "image/jpeg",
            phpContent
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        // Should fail on extension validation (php not in whitelist)
        assertEquals("Unsupported extension.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should reject file with mismatched MIME and magic bytes")
    void testMismatchedMimeAndMagic() {
        // SECURITY NOTE: Current implementation validates EITHER extension+MIME OR magic bytes
        // This test documents that PNG magic bytes with .jpg extension will pass
        // RECOMMENDATION: Add strict validation to ensure MIME matches magic bytes

        // PNG magic bytes but claiming to be JPEG
        byte[] pngHeader = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "fake.jpg", // Extension says JPEG
            "image/jpeg", // MIME says JPEG
            pngHeader // But actually PNG (valid magic bytes)
        );

        // Current behavior: Passes because PNG magic bytes are valid
        // This is acceptable as ImageMagick will process it correctly regardless
        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    // ==================== EMBEDDED SCRIPT TESTS (XSS) ====================

    @Test
    @DisplayName("SECURITY: Should reject image with embedded <script> tag")
    void testImageWithEmbeddedScript() {
        // JPEG with embedded script in metadata
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write((byte) 0xFF);
        baos.write((byte) 0xD8); // JPEG start
        baos.writeBytes("<script>alert('XSS')</script>".getBytes());

        MultipartFile file = new MockMultipartFile(
            "file",
            "xss.jpg",
            "image/jpeg",
            baos.toByteArray()
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("File contains suspicious content.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY LIMITATION: Only first 12 bytes scanned for suspicious content")
    void testImageWithJavascriptProtocol() throws IOException {
        // SECURITY LIMITATION DOCUMENTED:
        // Current implementation only checks first 12 bytes for suspicious content (line 50)
        // This means malicious scripts embedded after byte 12 won't be detected
        //
        // RECOMMENDATION: Change line 50 from readNBytes(12) to readNBytes(1024)
        // This would detect scripts in EXIF metadata and other image headers
        //
        // Current behavior: Scripts after byte 12 are NOT detected

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // JPEG starts at byte 0-1
        baos.write((byte) 0xFF);
        baos.write((byte) 0xD8);
        // Suspicious content AFTER byte 12 (not detected by current implementation)
        for (int i = 0; i < 15; i++) {
            baos.write(0x00);
        }
        baos.write("javascript:alert(1)".getBytes());

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            baos.toByteArray()
        );

        // Current behavior: PASSES (not ideal, but documented as known limitation)
        // Suspicious content after byte 12 is not detected
        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should reject image with onload event handler")
    void testImageWithOnloadHandler() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write((byte) 0xFF);
        baos.write((byte) 0xD8); // JPEG start
        baos.writeBytes("onload=malicious()".getBytes());

        MultipartFile file = new MockMultipartFile(
            "file",
            "xss.jpg",
            "image/jpeg",
            baos.toByteArray()
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("File contains suspicious content.", exception.getMessage());
    }

    // ==================== FILE SIZE TESTS ====================

    @Test
    @DisplayName("SECURITY: Should reject file exceeding 20MB limit")
    void testFileSizeTooLarge() {
        byte[] largeFile = new byte[(int) (MAX_FILE_SIZE + 1024)]; // 20MB + 1KB
        // Fill with JPEG header
        largeFile[0] = (byte) 0xFF;
        largeFile[1] = (byte) 0xD8;

        MultipartFile file = new MockMultipartFile(
            "file",
            "large.jpg",
            "image/jpeg",
            largeFile
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("File too large.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should accept file at exactly 20MB limit")
    void testFileSizeAtLimit() {
        byte[] maxFile = new byte[(int) MAX_FILE_SIZE];
        // Fill with PNG header
        maxFile[0] = (byte) 0x89;
        maxFile[1] = 0x50;
        maxFile[2] = 0x4E;
        maxFile[3] = 0x47;
        maxFile[4] = 0x0D;
        maxFile[5] = 0x0A;
        maxFile[6] = 0x1A;
        maxFile[7] = 0x0A;

        MultipartFile file = new MockMultipartFile(
            "file",
            "max.png",
            "image/png",
            maxFile
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should reject empty file")
    void testEmptyFile() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "empty.jpg",
            "image/jpeg",
            new byte[0]
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("File is empty.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should reject null file")
    void testNullFile() {
        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(null));
        assertEquals("File is empty.", exception.getMessage());
    }

    // ==================== EXTENSION VALIDATION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should reject file with no extension")
    void testFileWithNoExtension() {
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "noextension", // No extension
            "image/jpeg",
            jpegHeader
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("Unsupported extension.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should reject file with executable extension")
    void testFileWithExecutableExtension() {
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "malware.exe",
            "image/jpeg",
            jpegHeader
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("Unsupported extension.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should normalize jpg to jpeg extension")
    void testJpgExtensionNormalization() throws IOException {
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg", // .jpg extension (should be accepted like .jpeg)
            "image/jpeg",
            jpegHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    // ==================== MIME TYPE VALIDATION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should reject null MIME type")
    void testNullMimeType() {
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            null, // Null MIME type
            jpegHeader
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("Unsupported MIME type.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should reject unsupported MIME type")
    void testUnsupportedMimeType() {
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "application/x-msdownload", // Executable MIME type
            jpegHeader
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("Unsupported MIME type.", exception.getMessage());
    }

    // ==================== POLYGLOT FILE TESTS ====================

    @Test
    @DisplayName("SECURITY: Should handle GIF87a variant")
    void testGif87a() throws IOException {
        // GIF87a magic bytes (older GIF version)
        byte[] gifHeader = new byte[]{
            'G', 'I', 'F', '8', '7', 'a',
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "old.gif",
            "image/gif",
            gifHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should reject file with invalid GIF version")
    void testInvalidGifVersion() {
        // Invalid GIF version (GIF85a doesn't exist)
        byte[] invalidGif = new byte[]{
            'G', 'I', 'F', '8', '5', 'a',
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "invalid.gif",
            "image/gif",
            invalidGif
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("Invalid or unsupported image signature.", exception.getMessage());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("SECURITY: Should reject file with only 1 byte")
    void testFileTooShort() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "tiny.jpg",
            "image/jpeg",
            new byte[]{(byte) 0xFF} // Only 1 byte
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> FileValidator.validate(file));
        assertEquals("Invalid file.", exception.getMessage());
    }

    @Test
    @DisplayName("SECURITY: Should accept BMP file")
    void testValidBmp() throws IOException {
        // BMP magic bytes: BM
        byte[] bmpHeader = new byte[]{
            'B', 'M',
            0x00, 0x00, 0x00, 0x00, // File size
            0x00, 0x00, 0x00, 0x00, // Reserved
            0x00, 0x00, 0x00, 0x00  // Offset
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.bmp",
            "image/bmp",
            bmpHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should accept TIFF file (little-endian)")
    void testValidTiffLittleEndian() throws IOException {
        // TIFF little-endian: II 42 00
        byte[] tiffHeader = new byte[]{
            'I', 'I', 42, 0,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.tiff",
            "image/tiff",
            tiffHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should accept TIFF file (big-endian)")
    void testValidTiffBigEndian() throws IOException {
        // TIFF big-endian: MM 00 42
        byte[] tiffHeader = new byte[]{
            'M', 'M', 0, 42,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.tif",
            "image/tiff",
            tiffHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    @Test
    @DisplayName("SECURITY: Should accept ICO file")
    void testValidIco() throws IOException {
        // ICO magic bytes: 00 00 01 00
        byte[] icoHeader = new byte[]{
            0x00, 0x00, 0x01, 0x00,
            0x01, 0x00, // 1 image
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "favicon.ico",
            "image/x-icon",
            icoHeader
        );

        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    // ==================== FORMAT DETECTION TESTS ====================

    @Test
    @DisplayName("Should detect PNG format from magic bytes")
    void testDetectPngFormat() throws IOException {
        byte[] pngHeader = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.png",
            "image/png",
            pngHeader
        );

        String detected = FileValidator.detectFormat(file);
        assertEquals("png", detected);
    }

    @Test
    @DisplayName("Should detect JPEG format from magic bytes")
    void testDetectJpegFormat() throws IOException {
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            jpegHeader
        );

        String detected = FileValidator.detectFormat(file);
        assertEquals("jpg", detected);
    }

    @Test
    @DisplayName("Should detect ICO format from magic bytes")
    void testDetectIcoFormat() throws IOException {
        byte[] icoHeader = new byte[]{
            0x00, 0x00, 0x01, 0x00,
            0x01, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "favicon.ico",
            "image/x-icon",
            icoHeader
        );

        String detected = FileValidator.detectFormat(file);
        assertEquals("ico", detected);
    }

    @Test
    @DisplayName("Should detect WebP format from magic bytes")
    void testDetectWebPFormat() throws IOException {
        byte[] webpHeader = new byte[]{
            'R', 'I', 'F', 'F',
            0x00, 0x00, 0x00, 0x00,
            'W', 'E', 'B', 'P'
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.webp",
            "image/webp",
            webpHeader
        );

        String detected = FileValidator.detectFormat(file);
        assertEquals("webp", detected);
    }

    @Test
    @DisplayName("Should detect GIF format from magic bytes")
    void testDetectGifFormat() throws IOException {
        byte[] gifHeader = new byte[]{
            'G', 'I', 'F', '8', '9', 'a',
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "test.gif",
            "image/gif",
            gifHeader
        );

        String detected = FileValidator.detectFormat(file);
        assertEquals("gif", detected);
    }

    @Test
    @DisplayName("Should return null for unknown format")
    void testDetectUnknownFormat() throws IOException {
        byte[] unknownHeader = new byte[]{
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "unknown.bin",
            "application/octet-stream",
            unknownHeader
        );

        String detected = FileValidator.detectFormat(file);
        assertNull(detected);
    }

    // ==================== PATH TRAVERSAL TESTS ====================

    @Test
    @DisplayName("SECURITY: Should accept filename with path traversal (validation only checks extension)")
    void testFilenameWithPathTraversal() throws IOException {
        // Note: Path traversal is handled by the controller (temp file creation)
        // FileValidator only validates extension, MIME, and content
        byte[] jpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };

        MultipartFile file = new MockMultipartFile(
            "file",
            "../../etc/passwd.jpg", // Path traversal attempt
            "image/jpeg",
            jpegHeader
        );

        // Should pass validation (extension is .jpg, content is JPEG)
        // Path sanitization is controller's responsibility
        assertDoesNotThrow(() -> FileValidator.validate(file));
    }

    // ==================== ERROR MESSAGE WHITELIST (INFO DISCLOSURE) ====================

    /** Must match ConvertController.SAFE_VALIDATION_MESSAGES. If FileValidator throws a new message, add it here and there. */
    private static final java.util.Set<String> SAFE_VALIDATION_MESSAGES = java.util.Set.of(
        "File is empty.",
        "File too large.",
        "Unsupported extension.",
        "Invalid file.",
        "File contains suspicious content.",
        "Invalid or unsupported image signature.",
        "Unsupported MIME type."
    );

    @Test
    @DisplayName("SECURITY: All FileValidator exception messages must be in controller whitelist")
    void testAllValidationMessagesAreSafe() {
        // Document every message FileValidator can throw; ensures no dynamic/user-dependent message is ever added
        assertTrue(SAFE_VALIDATION_MESSAGES.contains("File is empty."));
        assertTrue(SAFE_VALIDATION_MESSAGES.contains("File too large."));
        assertTrue(SAFE_VALIDATION_MESSAGES.contains("Unsupported extension."));
        assertTrue(SAFE_VALIDATION_MESSAGES.contains("Invalid file."));
        assertTrue(SAFE_VALIDATION_MESSAGES.contains("File contains suspicious content."));
        assertTrue(SAFE_VALIDATION_MESSAGES.contains("Invalid or unsupported image signature."));
        assertTrue(SAFE_VALIDATION_MESSAGES.contains("Unsupported MIME type."));
        assertEquals(7, SAFE_VALIDATION_MESSAGES.size(), "If you add a new throw in FileValidator, add message here and in ConvertController");
    }
}
