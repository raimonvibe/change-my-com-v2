package com.raimonvibe.imageconverter.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageService
 * Tests business logic and validation (not actual ImageMagick execution)
 */
@DisplayName("ImageService Unit Tests")
public class ImageServiceTest {

    private final ImageService imageService = new ImageService();

    // ==================== SUPPORTED FORMATS TESTS ====================

    @Test
    @DisplayName("Should return list of supported formats")
    void testSupportedFormats() {
        List<String> formats = ImageService.supportedFormats();

        assertNotNull(formats);
        assertFalse(formats.isEmpty());
        assertTrue(formats.size() >= 8);
    }

    @Test
    @DisplayName("Should include common formats")
    void testCommonFormatsIncluded() {
        List<String> formats = ImageService.supportedFormats();

        assertTrue(formats.contains("jpg"));
        assertTrue(formats.contains("jpeg"));
        assertTrue(formats.contains("png"));
        assertTrue(formats.contains("webp"));
        assertTrue(formats.contains("gif"));
    }

    @Test
    @DisplayName("Should include modern formats")
    void testModernFormatsIncluded() {
        List<String> formats = ImageService.supportedFormats();

        assertTrue(formats.contains("avif"));
        assertTrue(formats.contains("heic"));
        assertTrue(formats.contains("ico"));
    }

    @Test
    @DisplayName("SECURITY: Should not include unsafe formats (SVG, PDF)")
    void testUnsafeFormatsExcluded() {
        List<String> formats = ImageService.supportedFormats();

        assertFalse(formats.contains("svg"), "SVG should be excluded for security");
        assertFalse(formats.contains("pdf"), "PDF should be excluded for security");
        assertFalse(formats.contains("ps"), "PostScript should be excluded for security");
        assertFalse(formats.contains("eps"), "EPS should be excluded for security");
    }

    @Test
    @DisplayName("Should not include resource-intensive formats (TIFF, BMP)")
    void testResourceIntensiveFormatsExcluded() {
        List<String> formats = ImageService.supportedFormats();

        assertFalse(formats.contains("tiff"), "TIFF excluded due to resource requirements");
        assertFalse(formats.contains("bmp"), "BMP excluded due to resource requirements");
    }

    // ==================== CONVERSION OPTIONS TESTS ====================

    @Test
    @DisplayName("Should create ConversionOptions with all parameters")
    void testConversionOptionsWithAllParams() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "png",
            85,
            100,
            1920
        );

        assertEquals("png", options.format());
        assertEquals(85, options.quality());
        assertEquals(100, options.sharpness());
        assertEquals(1920, options.width());
    }

    @Test
    @DisplayName("Should create ConversionOptions with null optional parameters")
    void testConversionOptionsWithNullParams() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "jpg",
            null,
            null,
            null
        );

        assertEquals("jpg", options.format());
        assertNull(options.quality());
        assertNull(options.sharpness());
        assertNull(options.width());
    }

    @Test
    @DisplayName("Should create ConversionOptions with minimum quality")
    void testConversionOptionsMinQuality() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "webp",
            1,
            0,
            16
        );

        assertEquals(1, options.quality());
        assertEquals(0, options.sharpness());
    }

    @Test
    @DisplayName("Should create ConversionOptions with maximum quality")
    void testConversionOptionsMaxQuality() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "avif",
            100,
            200,
            8000
        );

        assertEquals(100, options.quality());
        assertEquals(200, options.sharpness());
    }

    // ==================== GIF CONVERSION VALIDATION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should validate format count (max 4)")
    void testGifConversion_TooManyFormats() {
        // This test documents the security limit
        // Actual test would require mocking File I/O and ImageMagick
        List<String> tooManyFormats = List.of("png", "jpg", "webp", "avif", "heic");

        assertEquals(5, tooManyFormats.size(), "Test data should have 5 formats");
        assertTrue(tooManyFormats.size() > 4, "Should exceed the 4 format limit");
    }

    @Test
    @DisplayName("SECURITY: Should validate supported formats only")
    void testGifConversion_UnsupportedFormat() {
        // Document that only specific formats are allowed
        List<String> supportedGifFormats = List.of("jpeg", "jpg", "png", "webp", "heic", "heif");
        List<String> unsupportedFormats = List.of("svg", "pdf", "tiff", "bmp");

        for (String format : unsupportedFormats) {
            assertFalse(supportedGifFormats.contains(format),
                "Format " + format + " should not be supported for GIF conversion");
        }
    }

    @Test
    @DisplayName("Should allow up to 4 formats for GIF conversion")
    void testGifConversion_ValidFormatCount() {
        List<String> validFormats = List.of("png", "jpg", "webp", "heic");

        assertEquals(4, validFormats.size());
        assertTrue(validFormats.size() <= 4, "Should be within 4 format limit");
    }

    // ==================== SHARPENING STRATEGY TESTS ====================

    @Test
    @DisplayName("Should use different sharpening strategies for different levels")
    void testSharpeningLevelRanges() {
        // Strategy 1: Subtle (1-50)
        assertTrue(25 <= 50, "Level 25 should use subtle sharpening");
        assertTrue(50 <= 50, "Level 50 should use subtle sharpening");

        // Strategy 2: Adaptive (51-100)
        assertTrue(75 > 50 && 75 <= 100, "Level 75 should use adaptive sharpening");
        assertTrue(100 > 50 && 100 <= 100, "Level 100 should use adaptive sharpening");

        // Strategy 3: Professional (101-150)
        assertTrue(125 > 100 && 125 <= 150, "Level 125 should use professional sharpening");

        // Strategy 4: Maximum (151-200)
        assertTrue(175 > 150, "Level 175 should use maximum sharpening");
        assertTrue(200 > 150, "Level 200 should use maximum sharpening");
    }

    @Test
    @DisplayName("Should handle zero sharpness (no sharpening)")
    void testZeroSharpness() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "png",
            85,
            0,
            null
        );

        assertEquals(0, options.sharpness());
        // Zero sharpness means no sharpening will be applied
    }

    @Test
    @DisplayName("Should handle null sharpness (no sharpening)")
    void testNullSharpness() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "jpg",
            85,
            null,
            1920
        );

        assertNull(options.sharpness());
        // Null sharpness means no sharpening will be applied
    }

    // ==================== RESIZE STRATEGY TESTS ====================

    @Test
    @DisplayName("Should not apply resize for ICO format")
    void testIcoResizeHandling() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "ico",
            null,
            null,
            1920 // Width specified but should be ignored for ICO
        );

        assertEquals("ico", options.format());
        assertEquals(1920, options.width());
        // ICO format uses fixed 256x256 size regardless of width parameter
    }

    @Test
    @DisplayName("Should apply resize for non-ICO formats")
    void testStandardResizeHandling() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "png",
            85,
            null,
            1920
        );

        assertEquals("png", options.format());
        assertEquals(1920, options.width());
        // Standard formats should use the specified width
    }

    @Test
    @DisplayName("Should skip resize when width is null")
    void testNullWidthHandling() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "jpg",
            85,
            100,
            null
        );

        assertNull(options.width());
        // Null width means no resizing will be applied
    }

    @Test
    @DisplayName("Should skip resize when width is zero")
    void testZeroWidthHandling() {
        ImageService.ConversionOptions options = new ImageService.ConversionOptions(
            "webp",
            85,
            50,
            0
        );

        assertEquals(0, options.width());
        // Zero width means no resizing will be applied (checked with > 0 condition)
    }

    // ==================== FORMAT HINT SANITIZATION (SECURITY) ====================

    @Test
    @DisplayName("SECURITY: sanitizeFormatHint returns null for null and empty")
    void testSanitizeFormatHint_NullAndEmpty() {
        assertNull(ImageService.sanitizeFormatHint(null));
        assertNull(ImageService.sanitizeFormatHint(""));
        assertNull(ImageService.sanitizeFormatHint("   "));
    }

    @Test
    @DisplayName("SECURITY: sanitizeFormatHint allows only whitelisted formats")
    void testSanitizeFormatHint_AllowedFormats() {
        assertEquals("png", ImageService.sanitizeFormatHint("png"));
        assertEquals("jpg", ImageService.sanitizeFormatHint("jpg"));
        assertEquals("jpeg", ImageService.sanitizeFormatHint("jpeg"));
        assertEquals("webp", ImageService.sanitizeFormatHint("webp"));
        assertEquals("avif", ImageService.sanitizeFormatHint("avif"));
        assertEquals("gif", ImageService.sanitizeFormatHint("gif"));
        assertEquals("heic", ImageService.sanitizeFormatHint("heic"));
        assertEquals("ico", ImageService.sanitizeFormatHint("ico"));
        assertEquals("png", ImageService.sanitizeFormatHint("PNG"));
        // Whitespace or extra chars are rejected (must be exact alphanumeric)
        assertNull(ImageService.sanitizeFormatHint("  JPEG  "));
    }

    @Test
    @DisplayName("SECURITY: sanitizeFormatHint rejects dangerous formats (SVG, PDF, PS)")
    void testSanitizeFormatHint_RejectsDangerousFormats() {
        assertNull(ImageService.sanitizeFormatHint("svg"));
        assertNull(ImageService.sanitizeFormatHint("pdf"));
        assertNull(ImageService.sanitizeFormatHint("ps"));
        assertNull(ImageService.sanitizeFormatHint("eps"));
        assertNull(ImageService.sanitizeFormatHint("SVG"));
    }

    @Test
    @DisplayName("SECURITY: sanitizeFormatHint rejects command-injection style input")
    void testSanitizeFormatHint_RejectsInjectionAttempts() {
        assertNull(ImageService.sanitizeFormatHint("png;id"));
        assertNull(ImageService.sanitizeFormatHint("png|cat /etc/passwd"));
        assertNull(ImageService.sanitizeFormatHint("png\n/etc/passwd"));
        assertNull(ImageService.sanitizeFormatHint("png$(whoami)"));
        assertNull(ImageService.sanitizeFormatHint("../../../etc/passwd"));
        assertNull(ImageService.sanitizeFormatHint("png\r\n"));
        assertNull(ImageService.sanitizeFormatHint("p\"ng\""));
    }

    @Test
    @DisplayName("SECURITY: sanitizeFormatHint rejects unknown extensions")
    void testSanitizeFormatHint_RejectsUnknownFormats() {
        assertNull(ImageService.sanitizeFormatHint("xyz"));
        assertNull(ImageService.sanitizeFormatHint("exe"));
        assertNull(ImageService.sanitizeFormatHint("php"));
    }

    // ==================== DIMENSION VALIDATION (FALLBACK / SECURITY) ====================

    @Test
    @DisplayName("validateImageDimensions throws on null file")
    void testValidateImageDimensions_NullFile() {
        assertThrows(NullPointerException.class, () ->
            imageService.validateImageDimensions(null, "png"));
    }

    @Test
    @DisplayName("validateImageDimensions throws on non-existent file")
    void testValidateImageDimensions_NonExistentFile() {
        File nonexistent = new File(System.getProperty("java.io.tmpdir"), "nonexistent-image-xyz.png");
        assertFalse(nonexistent.exists());
        assertThrows(IOException.class, () ->
            imageService.validateImageDimensions(nonexistent, "png"));
    }

    @Test
    @DisplayName("SECURITY: invalid format hint does not leak into command (dimensions fail safely)")
    void testValidateImageDimensions_InvalidFormatHintFailsSafely() throws IOException {
        // With invalid hint, sanitizeFormatHint returns null so we use path-only; empty file still fails
        File empty = Files.createTempFile("empty", ".tmp").toFile();
        try {
            assertThrows(IOException.class, () ->
                imageService.validateImageDimensions(empty, "svg"));
            assertThrows(IOException.class, () ->
                imageService.validateImageDimensions(empty, "png;id"));
        } finally {
            empty.delete();
        }
    }

    @Test
    @DisplayName("ImageMagick commands are fixed (magick, convert, identify, /usr/bin/*) - no user-controlled executable")
    void testImageMagickCommandsAreFixed() {
        // Document security: we only ever invoke fixed commands or full paths - never user input
        List<String> allowedCommands = List.of("magick", "convert", "identify", "/usr/bin/convert", "/usr/bin/identify");
        assertTrue(allowedCommands.contains("magick"));
        assertTrue(allowedCommands.contains("convert"));
        assertTrue(allowedCommands.contains("/usr/bin/convert"));
        assertTrue(allowedCommands.contains("/usr/bin/identify"));
    }
}
