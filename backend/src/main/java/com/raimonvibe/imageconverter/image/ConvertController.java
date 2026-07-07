package com.raimonvibe.imageconverter.image;

import com.raimonvibe.imageconverter.config.ConversionLimits;
import com.raimonvibe.imageconverter.security.FileValidator;
import com.raimonvibe.imageconverter.user.ConversionQuotaService;
import com.raimonvibe.imageconverter.monitoring.CostMonitor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/convert")
@Validated
public class ConvertController {

    private static final Logger logger = LoggerFactory.getLogger(ConvertController.class);

    private static final Set<String> ALLOWED_OUT = ImageFormats.SUPPORTED_OUTPUT_FORMAT_SET;

    private final ImageService imageService;
    private final ConversionQuotaService quotaService;
    private final CostMonitor costMonitor;

    public ConvertController(ImageService imageService,
                             ConversionQuotaService quotaService,
                             CostMonitor costMonitor) {
        this.imageService = imageService;
        this.quotaService = quotaService;
        this.costMonitor = costMonitor;
    }

    @GetMapping("/formats")
    public Object formats() {
        return ImageService.supportedFormats();
    }

    /**
     * Converts a GIF file to multiple formats and returns a ZIP file.
     * Allows selection of multiple target formats at once.
     */
    @PostMapping(value = "/gif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StreamingResponseBody> convertGif(
            @RequestParam("file") MultipartFile file,
            @RequestParam("formats") String formatsParam,
            @RequestParam(value = "quality", required = false) @Min(1) @Max(100) Integer quality,
            @RequestParam(value = "sharpness", required = false) @Min(0) @Max(200) Integer sharpness,
            @RequestParam(value = "width", required = false) @Min(16) @Max(8000) Integer width,
            Principal principal,
            HttpServletRequest request
    ) {
        if (logger.isDebugEnabled()) {
            logger.debug("GIF conversion request - formats: {}, size: {} bytes, authenticated: {}",
                formatsParam, file.getSize(), principal != null);
        }

        // ---- 1) Parse and validate output formats
        java.util.List<String> formats;
        try {
            formats = Arrays.stream(formatsParam.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .map(fmt -> "jpg".equals(fmt) ? "jpeg" : fmt)
                .filter(fmt -> ALLOWED_OUT.contains(fmt))
                .collect(Collectors.toList());

            if (formats.isEmpty()) {
                logger.warn("No valid formats requested: {}", formatsParam);
                return badRequest("No valid target formats");
            }
        } catch (Exception e) {
            logger.warn("Failed to parse formats: {}", formatsParam);
            return badRequest("Invalid formats parameter");
        }

        // ---- 2) Validate it's actually a GIF file
        try {
            FileValidator.validate(file);
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("image/gif")) {
                logger.warn("Non-GIF file submitted to GIF endpoint: {}", contentType);
                return badRequest("Only GIF files are supported for this endpoint");
            }
        } catch (IllegalArgumentException iae) {
            logger.warn("File validation failed: {}", iae.getMessage());
            return badRequest(sanitizeValidationError(iae.getMessage()));
        } catch (IOException ioe) {
            logger.error("File read error during validation: {}", ioe.getMessage());
            return unprocessable("Failed to read upload");
        }

        // ---- 3) Credits (same as regular): free 20/day without login (IP); paid only when logged in
        // Quota policy selection (authenticated vs anonymous IP) lives in ConversionQuotaService.
        boolean allowed;
        try {
            allowed = quotaService.consumeOneConversion(principal, request);
        } catch (Exception e) {
            logger.error("Credit check failed: {}", e.getMessage());
            return serverError("Credit check failed");
        }

        if (!allowed) {
            return ResponseEntity.status(402)
                    .header("Cache-Control", "no-store")
                    .build();
        }

        // ---- 4) Convert GIF to ZIP
        File tmp = null;
        File zipOut = null;
        long startTime = System.currentTimeMillis();
        try {
            tmp = File.createTempFile("upload-gif-", ".gif");
            file.transferTo(tmp);

            final Integer q = (quality != null) ? quality : null;
            final Integer s = (sharpness != null) ? sharpness : 0;
            final Integer w = (width != null) ? width : null;

            ImageService.ConversionOptions options = new ImageService.ConversionOptions(
                "zip", q, s, w
            );

            zipOut = imageService.convertGifToZip(tmp, formats, options);

            // Record cost metrics
            long processingTime = System.currentTimeMillis() - startTime;
            String userEmail = principal != null ? principal.getName() : null;
            costMonitor.recordConversion(file.getSize(), processingTime, userEmail, "gif-to-zip");

            logger.info("GIF conversion successful - {} frames, {} formats, time: {}ms",
                "N/A", formats.size(), processingTime);

            // ---- 5) Stream ZIP response
            final File zipFile = zipOut;
            final File tmpFile = tmp;
            final long length = zipFile.length();

            StreamingResponseBody body = output -> {
                try (var in = new FileInputStream(zipFile)) {
                    in.transferTo(output);
                } finally {
                    safeDelete(tmpFile);
                    safeDelete(zipFile);
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                contentDispositionAttachment("converted-gif-frames.zip"));
            headers.add("X-Content-Type-Options", "nosniff");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(length)
                    .body(body);

        } catch (InterruptedException ie) {
            logger.error("GIF conversion interrupted: {}", ie.getMessage());
            Thread.currentThread().interrupt();
            safeDelete(tmp);
            safeDelete(zipOut);
            return unprocessable("Conversion interrupted");
        } catch (IOException ioe) {
            logger.error("GIF conversion I/O error: {}", ioe.getMessage());
            safeDelete(tmp);
            safeDelete(zipOut);

            // Sanitize error message before sending to user (security: prevent info disclosure)
            String errorMessage = sanitizeErrorMessage(ioe.getMessage());
            return unprocessable(errorMessage);
        } catch (Exception e) {
            logger.error("Unexpected GIF conversion error: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("GIF conversion error details", e);
            }
            safeDelete(tmp);
            safeDelete(zipOut);
            return unprocessable("Conversion failed");
        }
    }

    /**
     * Converteert een afbeelding met strikte validatie en veilige headers.
     * - Upload-limieten/timeouts via application.yml
     * - Credits: authenticated user of anonieme IP bucket
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StreamingResponseBody> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("to") @NotBlank String toFormat,
            @RequestParam(value = "quality", required = false) @Min(1) @Max(100) Integer quality,
            @RequestParam(value = "sharpness", required = false) @Min(0) @Max(200) Integer sharpness,
            @RequestParam(value = "width", required = false) @Min(16) @Max(8000) Integer width,
            Principal principal,
            HttpServletRequest request
    ) {
        if (logger.isDebugEnabled()) {
            logger.debug("Conversion request - format: {}, size: {} bytes, authenticated: {}",
                toFormat, file.getSize(), principal != null);
        }

        // ---- 1) Output-formaat whitelist & normalisatie
        logger.info("Convert request received: to={}, size={} bytes, auth={}", toFormat, file.getSize(), principal != null);

        String fmt = toFormat.toLowerCase();
        // Normaliseer jpg naar jpeg voor consistentie
        if ("jpg".equals(fmt)) {
            fmt = "jpeg";
        }
        if (!ALLOWED_OUT.contains(fmt)) {
            logger.warn("Unsupported format requested: {}", fmt);
            return badRequest("Unsupported target format");
        }

        // ---- 2) Bestandsvalidatie (size/MIME/magic)
        String detectedFormat = null;
        try {
            FileValidator.validate(file);
            // Detect format from magic bytes to help ImageMagick process the file
            detectedFormat = FileValidator.detectFormat(file);
            logger.debug("File validation passed for: {}, detected format: {}",
                file.getOriginalFilename(), detectedFormat);
        } catch (IllegalArgumentException iae) {
            logger.warn("File validation failed: {}", iae.getMessage());
            return badRequest(sanitizeValidationError(iae.getMessage()));
        } catch (IOException ioe) {
            logger.error("File read error during validation: {}", ioe.getMessage());
            return unprocessable("Failed to read upload");
        }
        logger.debug("Flow: validation done, detectedFormat={}", detectedFormat);

        // ---- 3) Credits: free 20/day without login (IP-based); paid only when logged in
        // Security: paid credits exist only for authenticated users; the anonymous
        // policy never touches User.paidCredits (see ConversionQuotaService).
        boolean allowed;
        try {
            allowed = quotaService.consumeOneConversion(principal, request);
        } catch (Exception e) {
            logger.error("Credit check failed: {}", e.getMessage());
            return serverError("Credit check failed");
        }

        if (!allowed) {
            logger.info("Flow: credits denied (402)");
            return ResponseEntity.status(402)
                    .header("Cache-Control", "no-store")
                    .build();
        }

        // ---- 4) Veilige temp-bestanden & conversie
        logger.debug("Flow: credits OK, creating temp file and validating dimensions");
        File tmp = null;
        File out = null;
        long startTime = System.currentTimeMillis();
        try {
            tmp = File.createTempFile("upload-", ".bin");
            file.transferTo(tmp);

            // Validate image dimensions before attempting conversion
            // This provides early feedback for oversized images
            // Pass the detected format to help ImageMagick identify files like ICO
            try {
                logger.debug("Flow: calling validateImageDimensions (tmp size={})", tmp.length());
                int[] dimensions = imageService.validateImageDimensions(tmp, detectedFormat);
                int maxDim = Math.max(dimensions[0], dimensions[1]);
                logger.info("Flow: dimensions OK {}x{} (maxDim={})", dimensions[0], dimensions[1], maxDim);
                final int MAX_DIMENSION = ConversionLimits.MAX_IMAGE_DIMENSION;

                if (maxDim > MAX_DIMENSION) {
                    safeDelete(tmp);
                    String message = String.format(
                        "Image dimensions (%dx%d pixels) exceed maximum allowed (%dx%d pixels). " +
                        "Please resize your image before uploading.",
                        dimensions[0], dimensions[1], MAX_DIMENSION, MAX_DIMENSION
                    );
                    logger.warn("Image too large: {}x{}", dimensions[0], dimensions[1]);
                    return unprocessable(message);
                }
            } catch (IOException dimError) {
                logger.warn("Flow: dimensions failed ({}), proceeding with conversion anyway", dimError.getMessage());
            }

            final Integer q = (quality != null) ? quality : null;
            final Integer s = (sharpness != null) ? sharpness : 0;
            final Integer w = (width != null) ? width : null;
            logger.info("Flow: calling imageService.convert format={} quality={} sharpness={} width={}", fmt, q, s, w);
            out = imageService.convert(tmp, new ImageService.ConversionOptions(fmt, q, s, w), detectedFormat);

            // Record cost metrics
            long processingTime = System.currentTimeMillis() - startTime;
            String userEmail = principal != null ? principal.getName() : null;
            costMonitor.recordConversion(file.getSize(), processingTime, userEmail, fmt);

            logger.info("Flow: conversion done - format: {}, time: {}ms, outputSize: {} bytes", fmt, processingTime, out.length());

            // ---- 5) Streaming response (opruimen ná verzenden)
            final File outFile = out; // effectively final voor lambda
            final File tmpFile = tmp;

            final String outName = "converted." + fmt; // geen user input in bestandsnaam
            final long length = outFile.length();

            StreamingResponseBody body = output -> {
                try (var in = new FileInputStream(outFile)) {
                    in.transferTo(output);
                } finally {
                    safeDelete(tmpFile);
                    safeDelete(outFile);
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
            headers.add(HttpHeaders.CONTENT_DISPOSITION, contentDispositionAttachment(outName));
            headers.add("X-Content-Type-Options", "nosniff");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(mimeFor(fmt)))
                    .contentLength(length)
                    .body(body);

        } catch (InterruptedException ie) {
            logger.error("Flow: conversion interrupted - {}", ie.getMessage());
            Thread.currentThread().interrupt();
            safeDelete(tmp);
            safeDelete(out);
            return unprocessable("Conversion interrupted");
        } catch (IOException ioe) {
            String msg = ioe.getMessage() != null ? ioe.getMessage() : "";
            boolean fromDimensions = msg.contains("Failed to get image dimensions") || msg.contains("Invalid dimension");
            logger.error("Flow: conversion I/O error - {} (exception: {}). Failure from: {}",
                msg, ioe.getClass().getSimpleName(), fromDimensions ? "dimensions/identify" : "conversion loop (magick/convert)");
            safeDelete(tmp);
            safeDelete(out);

            // Sanitize error message before sending to user (security: prevent info disclosure)
            String errorMessage = sanitizeErrorMessage(ioe.getMessage());
            return unprocessable(errorMessage);
        } catch (Exception e) {
            logger.error("Flow: unexpected conversion error - {} (exception: {})", e.getMessage(), e.getClass().getSimpleName());
            if (logger.isDebugEnabled()) {
                logger.debug("Conversion error details", e);
            }
            safeDelete(tmp);
            safeDelete(out);
            return unprocessable("Conversion failed");
        }
    }

    // ===== Helpers =====

    private static ResponseEntity<StreamingResponseBody> badRequest(String msg) {
        logger.warn("Bad request: {}", msg);
        return ResponseEntity.badRequest()
                .header("Cache-Control", "no-store")
                .header("X-Error-Message", msg)
                .build();
    }

    private static ResponseEntity<StreamingResponseBody> unprocessable(String msg) {
        logger.warn("Unprocessable entity: {}", msg);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("Cache-Control", "no-store")
                .header("X-Error-Message", msg)
                .build();
    }

    private static ResponseEntity<StreamingResponseBody> serverError(String msg) {
        logger.error("Server error: {}", msg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Cache-Control", "no-store")
                .header("X-Error-Message", msg)
                .build();
    }

    private static void safeDelete(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Exception ignored) {}
    }

    private static String contentDispositionAttachment(String filename) {
        // RFC 6266 / 5987: zowel filename als filename* voor i18n/UTF-8
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                                   .replace("+", "%20");
        return "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded;
    }

    /** Whitelist of safe validation messages from FileValidator (no user/path data). */
    private static final Set<String> SAFE_VALIDATION_MESSAGES = Set.of(
        "File is empty.",
        "File too large.",
        "Unsupported extension.",
        "Invalid file.",
        "File contains suspicious content.",
        "Invalid or unsupported image signature.",
        "Unsupported MIME type."
    );

    private static String sanitizeValidationError(String message) {
        if (message != null && SAFE_VALIDATION_MESSAGES.contains(message)) {
            return message;
        }
        return "Invalid file or format. Please check file type and size.";
    }

    /**
     * Sanitize error messages before sending to users to prevent information disclosure.
     * Uses a whitelist approach to only pass through safe, known error patterns.
     * Detailed errors are logged server-side for debugging.
     *
     * @param errorMessage Raw error message from exception
     * @return Safe, sanitized error message for user
     */
    private static String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "Image conversion failed. Please try a different image.";
        }

        // Whitelist: Known safe error messages that are helpful to users
        // These are constructed in our code and don't leak system information

        // Dimension validation errors (from our validation code)
        if (errorMessage.startsWith("Image dimensions (") &&
            errorMessage.contains("exceed maximum allowed")) {
            // Extract just the dimension info, no file paths
            return errorMessage.replaceAll("/[^\\s]+", "[file]"); // Remove any paths
        }

        // PNG conversion restriction (from ImageService.java:66-76)
        if (errorMessage.contains("PNG conversion not supported for very large images")) {
            return "PNG conversion not supported for very large images. Use WebP, JPEG, or AVIF instead for faster conversions.";
        }

        // GIF frame limit (from ImageService.java:402)
        if (errorMessage.contains("GIF has too many frames")) {
            // Extract just the frame count, sanitize the message
            if (errorMessage.matches(".*\\(max \\d+\\)\\..*Found: \\d+.*")) {
                return "GIF has too many frames (maximum 100 allowed). Please use a shorter GIF.";
            }
        }

        // Timeout errors - provide helpful message without system details
        if (errorMessage.toLowerCase().contains("timeout") ||
            errorMessage.toLowerCase().contains("timed out")) {
            return "Image processing timed out. The image may be too large or complex. Try a smaller image or reduce quality settings.";
        }

        // Server busy (from ImageService.java:39)
        if (errorMessage.contains("Server busy")) {
            return "Server is currently busy. Please try again in a moment.";
        }

        // Dimension reading errors (from ImageService.java:521)
        if (errorMessage.contains("get image dimensions") ||
            errorMessage.contains("Invalid dimension")) {
            return "Unable to process image. The file may be corrupted or in an unsupported format.";
        }

        // GIF extraction errors (from ImageService.java:390)
        if (errorMessage.contains("Failed to extract GIF frames") ||
            errorMessage.contains("No frames extracted")) {
            return "Failed to process GIF file. The file may be corrupted or invalid.";
        }

        // Default: Don't leak any exception details
        // Log the full error server-side, show generic message to user
        logger.debug("Sanitized error message for user. Original: {}", errorMessage);
        return "Image conversion failed. Please try a different image or contact support if the issue persists.";
    }

    private static String mimeFor(String fmt) {
        return switch (fmt) {
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "webp" -> "image/webp";
            case "avif" -> "image/avif";
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "heic", "heif" -> "image/heic";
            case "ico" -> "image/x-icon";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}
