package com.raimonvibe.imageconverter.image;

import com.raimonvibe.imageconverter.security.FileValidator;
import com.raimonvibe.imageconverter.user.AnonymousUserService;
import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.raimonvibe.imageconverter.user.UserService;
import com.raimonvibe.imageconverter.monitoring.CostMonitor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Set<String> ALLOWED_OUT = Set.of(
        "jpg", "jpeg", "png", "webp", "avif",
        "gif", "heic", "heif", "ico"
    );

    private final ImageService imageService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AnonymousUserService anonymousUserService;
    private final CostMonitor costMonitor;

    @Value("${app.stripe.pricePackSize:20}")
    private int packSize;

    public ConvertController(ImageService imageService,
                             UserRepository userRepository,
                             UserService userService,
                             AnonymousUserService anonymousUserService,
                             CostMonitor costMonitor) {
        this.imageService = imageService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.anonymousUserService = anonymousUserService;
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
            return badRequest(iae.getMessage());
        } catch (IOException ioe) {
            logger.error("File read error during validation: {}", ioe.getMessage());
            return unprocessable("Failed to read upload");
        }

        // ---- 3) Credits check (same as regular conversion)
        boolean allowed;
        try {
            if (principal != null) {
                final String email = principal.getName();
                final User user = userService.ensureUserByEmail(email);
                allowed = userService.consumeOneConversion(user, packSize);
                if (!allowed) {
                    logger.info("GIF conversion denied for user {} - insufficient credits", email);
                }
            } else {
                final String clientIp = getClientIpAddress(request);
                allowed = anonymousUserService.consumeOneConversion(clientIp, 20);
                if (!allowed) {
                    logger.info("GIF conversion denied for IP {} - limit reached", clientIp);
                }
            }
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
            return unprocessable("Conversion failed");
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
        try {
            FileValidator.validate(file);
            logger.debug("File validation passed for: {}", file.getOriginalFilename());
        } catch (IllegalArgumentException iae) {
            logger.warn("File validation failed: {}", iae.getMessage());
            return badRequest(iae.getMessage());
        } catch (IOException ioe) {
            logger.error("File read error during validation: {}", ioe.getMessage());
            return unprocessable("Failed to read upload");
        }

        // ---- 3) Credits (user of IP)
        boolean allowed;
        try {
            if (principal != null) {
                final String email = principal.getName();
                final User user = userService.ensureUserByEmail(email);
                allowed = userService.consumeOneConversion(user, packSize);
                if (!allowed) {
                    logger.info("Conversion denied for user {} - insufficient credits", email);
                }
            } else {
                final String clientIp = getClientIpAddress(request);
                allowed = anonymousUserService.consumeOneConversion(clientIp, 20);
                if (!allowed) {
                    logger.info("Conversion denied for IP {} - limit reached", clientIp);
                }
            }
        } catch (Exception e) {
            logger.error("Credit check failed: {}", e.getMessage());
            return serverError("Credit check failed");
        }

        if (!allowed) {
            // 402 Payment Required: client kan flow naar afrekenen starten
            return ResponseEntity.status(402)
                    .header("Cache-Control", "no-store")
                    .build();
        }

        // ---- 4) Veilige temp-bestanden & conversie
        File tmp = null;
        File out = null;
        long startTime = System.currentTimeMillis();
        try {
            tmp = File.createTempFile("upload-", ".bin");
            file.transferTo(tmp);

            final Integer q = (quality != null) ? quality : null;
            final Integer s = (sharpness != null) ? sharpness : 0;
            final Integer w = (width != null) ? width : null;
            out = imageService.convert(tmp, new ImageService.ConversionOptions(fmt, q, s, w));

            // Record cost metrics
            long processingTime = System.currentTimeMillis() - startTime;
            String userEmail = principal != null ? principal.getName() : null;
            costMonitor.recordConversion(file.getSize(), processingTime, userEmail, fmt);

            logger.info("Conversion successful - format: {}, time: {}ms", fmt, processingTime);

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
            logger.error("Conversion interrupted: {}", ie.getMessage());
            Thread.currentThread().interrupt();
            safeDelete(tmp);
            safeDelete(out);
            return unprocessable("Conversion interrupted");
        } catch (IOException ioe) {
            logger.error("Conversion I/O error: {}", ioe.getMessage());
            safeDelete(tmp);
            safeDelete(out);
            return unprocessable("Conversion failed");
        } catch (Exception e) {
            logger.error("Unexpected conversion error: {}", e.getMessage());
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
        return ResponseEntity.badRequest()
                .header("Cache-Control", "no-store")
                .build();
    }

    private static ResponseEntity<StreamingResponseBody> unprocessable(String msg) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("Cache-Control", "no-store")
                .build();
    }

    private static ResponseEntity<StreamingResponseBody> serverError(String msg) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Cache-Control", "no-store")
                .build();
    }

    private static void safeDelete(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Exception ignored) {}
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String contentDispositionAttachment(String filename) {
        // RFC 6266 / 5987: zowel filename als filename* voor i18n/UTF-8
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                                   .replace("+", "%20");
        return "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded;
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
