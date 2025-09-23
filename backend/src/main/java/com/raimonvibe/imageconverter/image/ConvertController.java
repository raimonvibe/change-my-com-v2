package com.raimonvibe.imageconverter.image;

import com.raimonvibe.imageconverter.security.FileValidator;
import com.raimonvibe.imageconverter.user.AnonymousUserService;
import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.raimonvibe.imageconverter.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/convert")
@Validated
public class ConvertController {

    private static final Set<String> ALLOWED_OUT = Set.of("png", "jpg", "jpeg", "webp", "avif");

    private final ImageService imageService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AnonymousUserService anonymousUserService;

    @Value("${app.stripe.pricePackSize:20}")
    private int packSize;

    public ConvertController(ImageService imageService,
                             UserRepository userRepository,
                             UserService userService,
                             AnonymousUserService anonymousUserService) {
        this.imageService = imageService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.anonymousUserService = anonymousUserService;
    }

    @GetMapping("/formats")
    public Object formats() {
        return ImageService.supportedFormats();
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
            Principal principal,
            HttpServletRequest request
    ) {
        // ---- 1) Output-formaat whitelist
        final String fmt = toFormat.toLowerCase();
        if (!ALLOWED_OUT.contains(fmt)) {
            return badRequest("Unsupported target format");
        }

        // ---- 2) Bestandsvalidatie (size/MIME/magic)
        try {
            FileValidator.validate(file);
        } catch (IllegalArgumentException iae) {
            return badRequest(iae.getMessage());
        } catch (IOException ioe) {
            return unprocessable("Failed to read upload");
        }

        // ---- 3) Credits (user of IP)
        boolean allowed;
        try {
            if (principal != null) {
                final String email = principal.getName();
                // Use UserService to ensure user exists and get proper user object
                final User user = userService.ensureUserByEmail(email);
                allowed = userService.consumeOneConversion(user, packSize);
            } else {
                final String clientIp = getClientIpAddress(request);
                allowed = anonymousUserService.consumeOneConversion(clientIp, 20);
            }
        } catch (Exception e) {
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
        try {
            tmp = File.createTempFile("upload-", ".bin");
            file.transferTo(tmp);

            final Integer q = (quality != null) ? quality : null;
            out = imageService.convert(tmp, new ImageService.ConversionOptions(fmt, q));

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
            Thread.currentThread().interrupt();
            safeDelete(tmp);
            safeDelete(out);
            return unprocessable("Conversion interrupted");
        } catch (IOException ioe) {
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
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}
