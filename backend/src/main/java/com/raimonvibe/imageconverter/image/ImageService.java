package com.raimonvibe.imageconverter.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    /** Allowed format hints for ImageMagick (whitelist to prevent command-injection via format string). */
    private static final Set<String> ALLOWED_FORMAT_HINTS = Set.of(
        "png", "jpg", "jpeg", "webp", "avif", "gif", "bmp", "tiff", "tif", "heic", "heif", "ico"
    );

    public record ConversionOptions(String format, Integer quality, Integer sharpness, Integer width) {}

    /**
     * Sanitize format hint for use in ImageMagick commands. Returns the hint only if it is in the
     * allowed whitelist and contains no extra characters (prevents injection via format:path syntax).
     * Used for all identify/convert arguments that include a format prefix.
     */
    static String sanitizeFormatHint(String formatHint) {
        if (formatHint == null || formatHint.isEmpty()) return null;
        // Reject if any non-alphanumeric (e.g. "png;id", "png\n", "png ") so only exact whitelist match passes
        if (!formatHint.matches("(?i)^[a-z0-9]+$")) return null;
        String normalized = formatHint.toLowerCase().trim();
        return ALLOWED_FORMAT_HINTS.contains(normalized) ? normalized : null;
    }

    // Maximaal 4 conversies tegelijk
    private final Semaphore semaphore = new Semaphore(4);

    /**
     * Converteert input-bestand naar target-format via ImageMagick ("magick" CLI).
     * - Concurrency gelimiteerd met Semaphore
     * - Timeout van 15 seconden
     * - Temp output in system temp
     *
     * @param input Input file to convert
     * @param options Conversion options (format, quality, sharpness, width)
     * @param inputFormatHint Optional format hint for input file (e.g., "ico", "png")
     * @return Converted file
     */
    public File convert(File input, ConversionOptions options, String inputFormatHint) throws IOException, InterruptedException {
        if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new IOException("Server busy: too many concurrent conversions");
        }

        // Declare these outside try block so they're accessible in catch/finally
        File processedInput = input;
        boolean wasAutoResized = false;

        try {
            String outExt = options.format().toLowerCase();
            File out = Files.createTempFile("conv-" + UUID.randomUUID(), "." + outExt).toFile();

            // Detect image dimensions and cap sharpness to respect 10s policy time limit
            // Use the format hint if provided to help ImageMagick identify the file
            int[] dimensions = getImageDimensions(input, inputFormatHint);
            int maxDimension = Math.max(dimensions[0], dimensions[1]);

            // Auto-resize large images (>1920px) to stay within time/memory limits on constrained hosts (e.g. Render 512MB)
            // Smartphone photos (e.g. iPhone 11 Pro 12MP) are often 4032x3024; full decode/resize can OOM
            if (maxDimension > 1920) {
                logger.info("Auto-resizing {}x{} image to 1920px for time/memory limits",
                    dimensions[0], dimensions[1]);
                processedInput = autoResizeImage(input, 1920, inputFormatHint);
                wasAutoResized = true;
            }

            // Block PNG conversion for very large images (>4000px) - even after resize, PNG is slow
            // PNG uses expensive lossless compression that's slow for large photos
            if ("png".equals(outExt) && maxDimension > 4000) {
                String message = String.format(
                    "PNG conversion not supported for very large images (%dx%d). " +
                    "Even after auto-resize, PNG compression is too slow for photos >4000px. " +
                    "Use WebP (recommended), JPEG, or AVIF instead for faster conversions.",
                    dimensions[0], dimensions[1]
                );
                logger.warn(message);
                if (wasAutoResized && processedInput != input) {
                    safeDelete(processedInput);
                }
                throw new IOException(message);
            }

            int originalSharpness = options.sharpness() != null ? options.sharpness() : 0;
            // Use resized dimensions for sharpness capping if auto-resized
            int sharpnessCheckDimension = wasAutoResized ? 1920 : maxDimension;
            int cappedSharpness = capSharpnessForDimensions(sharpnessCheckDimension, originalSharpness);

            if (cappedSharpness < originalSharpness) {
                logger.warn("Sharpness reduced from {}% to {}% for {}x{} image to respect 10s time limit policy",
                    originalSharpness, cappedSharpness, dimensions[0], dimensions[1]);
            }

            // Create new options with capped sharpness
            ConversionOptions adjustedOptions = new ConversionOptions(
                options.format(),
                options.quality(),
                cappedSharpness,
                options.width()
            );

            IOException lastError = null;

            for (String cmd : List.of("magick", "convert")) {
                ProcessBuilder pb;

                // Build command arguments
                java.util.List<String> args = new java.util.ArrayList<>();
                args.add(cmd);

                // Set resource limits to avoid OOM on constrained hosts (e.g. Render free 512MB)
                // Auto-resize and sharpness capping keep conversions within time/memory
                int magickTimeLimit = adjustedOptions.sharpness() != null && adjustedOptions.sharpness() > 100 ? 120 : 60;
                args.add("-limit");
                args.add("time");
                args.add(String.valueOf(magickTimeLimit));
                args.add("-limit");
                args.add("memory");
                args.add("192MiB");
                args.add("-limit");
                args.add("map");
                args.add("384MiB");

                // Add input file with optional format hint (whitelisted to prevent injection)
                String safeHint = sanitizeFormatHint(inputFormatHint);
                String inputArg;
                if (safeHint != null) {
                    inputArg = safeHint + ":" + processedInput.getAbsolutePath();
                    if ("ico".equals(safeHint)) {
                        inputArg = inputArg + "[0]";
                    }
                    args.add(inputArg);
                } else {
                    args.add(processedInput.getAbsolutePath());
                }

                // Apply width resize if specified (before format-specific handling)
                if (adjustedOptions.width() != null && adjustedOptions.width() > 0 && !"ico".equals(outExt)) {
                    // Resize maintaining aspect ratio, only if larger than specified width
                    args.add("-resize");
                    args.add(adjustedOptions.width() + "x>");
                }

                // Special handling for ICO format
                if ("ico".equals(outExt)) {
                    // ICO requires specific sizing and color handling
                    args.add("-resize");
                    args.add("256x256");
                    args.add("-define");
                    args.add("icon:auto-resize=256,128,64,48,32,16");
                    args.add("-colors");
                    args.add("256");
                }

                // Apply advanced sharpening if requested (0-200 scale)
                // Note: Sharpening is applied AFTER resize for best results
                // Uses tiered approach: subtle → adaptive → professional → maximum
                // Sharpness is capped based on image dimensions to respect 10s policy
                if (adjustedOptions.sharpness() != null && adjustedOptions.sharpness() > 0) {
                    applySharpeningStrategy(args, adjustedOptions.sharpness());
                }

                // Apply quality if specified
                if (adjustedOptions.quality() != null) {
                    args.add("-quality");
                    args.add(String.valueOf(adjustedOptions.quality()));
                }

                args.add(out.getAbsolutePath());
                pb = new ProcessBuilder(args);

                // Set memory limit for ImageMagick to prevent OOM on low-resource servers (e.g. Render 512MB)
                pb.environment().put("MAGICK_MEMORY_LIMIT", "192MB");
                pb.environment().put("MAGICK_MAP_LIMIT", "384MB");
                pb.environment().put("MAGICK_DISK_LIMIT", "512MB");

                // Also set environment variables as fallback for older ImageMagick versions
                pb.environment().put("MAGICK_TIME_LIMIT", String.valueOf(magickTimeLimit));

                pb.redirectErrorStream(true);

                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Executing ImageMagick with time limit {}s, sharpness={}: {}",
                            magickTimeLimit, adjustedOptions.sharpness(), String.join(" ", pb.command()));
                    } else {
                        logger.info("Converting {}x{} image - sharpness: {}%, format: {}",
                            dimensions[0], dimensions[1], adjustedOptions.sharpness(), adjustedOptions.format());
                    }

                    Process p = pb.start();

                    // Capture output for debugging
                    // Note: redirectErrorStream(true) merges stderr into stdout, so we read from InputStream
                    String processOutput = new String(p.getInputStream().readAllBytes());

                    // Increased timeout for complex sharpening operations (especially 100-200% sharpness)
                    // High sharpness uses LAB colorspace conversion and multiple passes
                    // Process timeout should be longer than MAGICK_TIME_LIMIT to allow ImageMagick to finish
                    // Extra time for cloud/shared hosting environments with limited CPU resources
                    int timeoutSeconds = options.sharpness() != null && options.sharpness() > 100 ? 150 : 90;
                    boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                    if (!finished) {
                        p.destroyForcibly();
                        logger.warn("ImageMagick process timeout after {}s for command: {}", timeoutSeconds, cmd);
                        lastError = new IOException("ImageMagick process timeout - try reducing sharpness level");
                        continue;
                    }

                    int code = p.exitValue();
                    if (code == 0 && out.exists() && out.length() > 0) {
                        logger.debug("ImageMagick conversion successful, output size: {} bytes", out.length());
                        return out;
                    }

                    logger.warn("ImageMagick failed with exit code {}: {}", code, processOutput);
                    lastError = new IOException("Conversion failed with '" + cmd + "', exit code=" + code);
                } catch (IOException ioe) {
                    logger.info("ImageMagick with '{}' not available ({}), trying next command", cmd, ioe.getMessage());
                    lastError = ioe;
                }
            }

            throw (lastError != null ? lastError : new IOException("Unknown conversion error"));
        } catch (Exception e) {
            // Clean up auto-resized temp file on error
            if (wasAutoResized && processedInput != input) {
                safeDelete(processedInput);
            }
            throw e;
        } finally {
            semaphore.release();
            // Clean up auto-resized temp file after successful conversion
            if (wasAutoResized && processedInput != input) {
                safeDelete(processedInput);
            }
        }
    }

    /**
     * Advanced sharpening strategy using tiered approach based on sharpness level.
     * Each tier uses progressively more sophisticated ImageMagick techniques.
     *
     * @param args Command arguments list to append sharpening parameters to
     * @param sharpness Sharpness level from 0-200
     */
    private void applySharpeningStrategy(java.util.List<String> args, int sharpness) {
        // Strategy 1: Subtle sharpening (1-50)
        // Uses gentle unsharp mask for natural enhancement
        if (sharpness <= 50) {
            double amount = sharpness / 50.0; // 0.02 to 1.0
            args.add("-unsharp");
            args.add(String.format("0.5x0.5+%.2f+0.01", amount));

            if (logger.isDebugEnabled()) {
                logger.debug("Applying subtle sharpening: level={}, amount={}", sharpness, amount);
            }
        }

        // Strategy 2: Standard adaptive sharpening (51-100)
        // Uses adaptive-sharpen which adjusts based on local image features
        // Sharpens edges more than flat areas (reduces noise amplification)
        else if (sharpness <= 100) {
            double strength = (sharpness - 50) / 25.0; // 0.04 to 2.0
            args.add("-adaptive-sharpen");
            args.add(String.format("0x%.2f", strength));

            if (logger.isDebugEnabled()) {
                logger.debug("Applying adaptive sharpening: level={}, strength={}", sharpness, strength);
            }
        }

        // Strategy 3: Professional multi-pass sharpening (101-150)
        // Uses LAB color space to sharpen only luminosity (prevents color artifacts)
        // Two-pass approach: fine detail + edge enhancement
        else if (sharpness <= 150) {
            // Convert to LAB color space for cleaner sharpening
            args.add("-colorspace");
            args.add("Lab");
            args.add("-channel");
            args.add("L"); // Sharpen only Lightness channel

            // First pass: Fine detail enhancement
            args.add("-unsharp");
            args.add("0.5x0.5+1.0+0.02");

            // Second pass: Edge sharpening with strength based on level
            double edgeStrength = (sharpness - 100) / 25.0; // 0.04 to 2.0
            args.add("-unsharp");
            args.add(String.format("2x1+%.2f+0.05", 0.8 + edgeStrength));

            // Return to sRGB color space
            args.add("+channel");
            args.add("-colorspace");
            args.add("sRGB");

            if (logger.isDebugEnabled()) {
                logger.debug("Applying professional LAB sharpening: level={}, edge_strength={}",
                    sharpness, edgeStrength);
            }
        }

        // Strategy 4: Maximum sharpening (151-200)
        // Professional-grade with contrast enhancement + aggressive multi-pass
        // Similar to Photoshop's high-pass filter technique
        else {
            // Step 1: Subtle contrast enhancement to make sharpening more effective
            args.add("-contrast-stretch");
            args.add("0.15x0.05%");

            // Step 2: LAB color space for clean sharpening
            args.add("-colorspace");
            args.add("Lab");
            args.add("-channel");
            args.add("L");

            // Step 3: Aggressive unsharp mask
            double maxStrength = (sharpness - 150) / 50.0; // 0.02 to 1.0
            args.add("-unsharp");
            args.add(String.format("1x0.8+%.2f+0.05", 2.0 + maxStrength));

            // Step 4: Final adaptive pass for edge refinement
            args.add("-adaptive-sharpen");
            args.add(String.format("0x%.2f", 1.5 + maxStrength));

            // Return to sRGB
            args.add("+channel");
            args.add("-colorspace");
            args.add("sRGB");

            if (logger.isDebugEnabled()) {
                logger.debug("Applying maximum sharpening: level={}, max_strength={}",
                    sharpness, maxStrength);
            }
        }
    }

    /**
     * Converts a GIF file to multiple formats and bundles them in a ZIP file.
     * Extracts each frame from the GIF and converts to the requested formats.
     *
     * @param input GIF file to convert
     * @param formats List of target formats (e.g., ["png", "jpg", "webp"])
     * @param options Conversion options (quality, sharpness, width)
     * @return ZIP file containing all converted frames in all requested formats
     */
    public File convertGifToZip(File input, List<String> formats, ConversionOptions options)
            throws IOException, InterruptedException {
        // Security: Limit number of formats to prevent resource exhaustion
        if (formats.size() > 4) {
            throw new IllegalArgumentException("Maximum 4 output formats allowed");
        }

        // Security: Validate all formats are supported
        List<String> supportedFormats = List.of("jpeg", "jpg", "png", "webp", "heic", "heif");
        for (String format : formats) {
            if (!supportedFormats.contains(format.toLowerCase())) {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }
        }

        if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new IOException("Server busy: too many concurrent conversions");
        }

        List<File> tempFiles = new ArrayList<>();
        try {
            // Create temp directory for extracted frames
            Path tempDir = Files.createTempDirectory("gif-frames-" + UUID.randomUUID());
            File tempDirFile = tempDir.toFile();
            tempFiles.add(tempDirFile);

            // Extract GIF frames using ImageMagick coalesce (try "magick" then "convert" for IM6)
            logger.debug("Extracting GIF frames from: {}", input.getName());
            String framePattern = tempDir.resolve("frame-%03d.png").toString();

            boolean extracted = false;
            for (String imCmd : List.of("magick", "convert")) {
                try {
                    List<String> extractArgs = new ArrayList<>();
                    extractArgs.add(imCmd);
                    extractArgs.add("-limit");
                    extractArgs.add("time");
                    extractArgs.add("120");
                    extractArgs.add("-limit");
                    extractArgs.add("memory");
                    extractArgs.add("192MiB");
                    extractArgs.add("-limit");
                    extractArgs.add("map");
                    extractArgs.add("384MiB");
                    extractArgs.add(input.getAbsolutePath());
                    extractArgs.add("-coalesce");
                    extractArgs.add(framePattern);

                    ProcessBuilder extractPb = new ProcessBuilder(extractArgs);
                    extractPb.environment().put("MAGICK_TIME_LIMIT", "120");
                    extractPb.environment().put("MAGICK_MEMORY_LIMIT", "192MB");
                    extractPb.redirectErrorStream(true);

                    Process extractProcess = extractPb.start();
                    boolean extractFinished = extractProcess.waitFor(150, TimeUnit.SECONDS);
                    if (!extractFinished) {
                        extractProcess.destroyForcibly();
                        continue;
                    }
                    if (extractProcess.exitValue() != 0) {
                        logger.debug("GIF extraction with {} failed: {}", imCmd,
                            new String(extractProcess.getInputStream().readAllBytes()));
                        continue;
                    }
                    extracted = true;
                    break;
                } catch (IOException e) {
                    logger.info("ImageMagick GIF extract with '{}' not available ({}), trying next", imCmd, e.getMessage());
                }
            }

            if (!extracted) {
                throw new IOException("Failed to extract GIF frames (magick/convert not available or failed)");
            }

            // Get list of extracted frames
            File[] frames = tempDirFile.listFiles((dir, name) -> name.startsWith("frame-") && name.endsWith(".png"));
            if (frames == null || frames.length == 0) {
                throw new IOException("No frames extracted from GIF");
            }

            // Security: Limit frame count to prevent ZIP bombs and resource exhaustion
            // 100 frames * 4 formats * ~500KB avg = ~200MB max ZIP size
            if (frames.length > 100) {
                throw new IOException("GIF has too many frames (max 100). Found: " + frames.length);
            }

            logger.info("Extracted {} frames from GIF", frames.length);

            // Create ZIP file
            File zipFile = Files.createTempFile("gif-converted-" + UUID.randomUUID(), ".zip").toFile();
            tempFiles.add(zipFile);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                // Convert each frame to each requested format
                for (int i = 0; i < frames.length; i++) {
                    File frame = frames[i];

                    for (String format : formats) {
                        String normalizedFormat = format.toLowerCase();
                        if ("jpg".equals(normalizedFormat)) {
                            normalizedFormat = "jpeg";
                        }

                        // Create options for this conversion
                        ConversionOptions frameOptions = new ConversionOptions(
                            normalizedFormat,
                            options.quality(),
                            options.sharpness(),
                            options.width()
                        );

                        // Convert frame (PNG frames from GIF extraction, no format hint needed)
                        File converted = convert(frame, frameOptions, null);
                        tempFiles.add(converted);

                        // Add to ZIP with proper naming: frame-001.png, frame-001.jpg, etc.
                        String zipEntryName = String.format("frame-%03d.%s", i, normalizedFormat);
                        ZipEntry entry = new ZipEntry(zipEntryName);
                        zos.putNextEntry(entry);

                        try (FileInputStream fis = new FileInputStream(converted)) {
                            fis.transferTo(zos);
                        }
                        zos.closeEntry();

                        logger.debug("Added {} to ZIP", zipEntryName);
                    }
                }
            }

            logger.info("Created ZIP with {} frames in {} formats", frames.length, formats.size());

            // Remove zipFile from tempFiles list since it's being returned
            tempFiles.remove(zipFile);

            // Clean up all temp files except the final ZIP
            for (File tempFile : tempFiles) {
                safeDelete(tempFile);
            }

            return zipFile;

        } catch (Exception e) {
            // On error, clean up everything including the ZIP
            for (File tempFile : tempFiles) {
                safeDelete(tempFile);
            }
            throw e;
        } finally {
            semaphore.release();
        }
    }

    private void safeDelete(File f) {
        if (f == null || !f.exists()) return;

        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    safeDelete(child);
                }
            }
        }
        f.delete();
    }

    /**
     * Ondersteunde outputformaten.
     * Sync houden met validator in controller.
     * Alleen veilige raster formaten - SVG/PDF uitgesloten om veiligheidsredenen.
     * TIFF & BMP verwijderd vanwege hoge resource-eisen op beperkte server specs.
     */
    public static List<String> supportedFormats() {
        return List.of(
            "jpg", "jpeg", "png", "webp", "avif",
            "gif", "heic", "heif", "ico"
        );
    }

    /**
     * Validate image dimensions (public wrapper for dimension checking).
     * This is used by the controller to validate images before conversion.
     *
     * @param input Image file to analyze
     * @param formatHint Optional format hint (e.g., "ico", "png") to help ImageMagick identify the file
     * @return Array with [width, height]
     * @throws IOException if dimensions cannot be determined
     */
    public int[] validateImageDimensions(File input, String formatHint) throws IOException {
        return getImageDimensions(input, formatHint);
    }

    /**
     * Get image dimensions using ImageMagick identify command.
     * Uses -ping to avoid loading full image into memory (header-only read where possible),
     * and strict memory limits so large smartphone photos (e.g. iPhone 12MP) don't OOM the instance.
     *
     * @param input Image file to analyze
     * @param formatHint Optional format hint (e.g., "ico", "png") to help ImageMagick identify the file
     * @return Array with [width, height]
     * @throws IOException if dimensions cannot be determined
     */
    private int[] getImageDimensions(File input, String formatHint) throws IOException {
        try {
            // Whitelist format hint to prevent injection (only pass allowed formats to ImageMagick)
            String safeHint = sanitizeFormatHint(formatHint);
            String inputPath = safeHint != null
                ? safeHint + ":" + input.getAbsolutePath()
                : input.getAbsolutePath();

            // For ICO files, only get dimensions of the first image using [0] index
            if ("ico".equals(safeHint)) {
                inputPath = inputPath + "[0]";
            }

            // Try "magick identify" (ImageMagick 7) then "identify" (ImageMagick 6) so we work on systems without magick
            String output = null;
            for (List<String> identifyPrefix : List.of(
                List.of("magick", "identify"),
                List.of("identify")
            )) {
                try {
                    List<String> args = new ArrayList<>(identifyPrefix);
                    args.add("-limit");
                    args.add("memory");
                    args.add("128MiB");
                    args.add("-limit");
                    args.add("map");
                    args.add("256MiB");
                    args.add("-limit");
                    args.add("time");
                    args.add("10");
                    args.add("-ping");
                    args.add("-format");
                    args.add("%w %h");
                    args.add(inputPath);

                    ProcessBuilder pb = new ProcessBuilder(args);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    output = new String(p.getInputStream().readAllBytes()).trim();

                    if (!p.waitFor(10, TimeUnit.SECONDS)) {
                        p.destroyForcibly();
                        output = null;
                        continue;
                    }
                    if (p.exitValue() != 0) {
                        output = null;
                        continue;
                    }
                    break;
                } catch (IOException e) {
                    // magick/identify not found or exec failed - try next (e.g. Render has IM6: identify, not magick)
                    logger.info("ImageMagick identify with {} not available ({}), trying next", identifyPrefix, e.getMessage());
                    output = null;
                }
            }

            if (output == null || output.isBlank()) {
                // -ping may not work for all formats (e.g. some HEIC); retry without -ping
                output = runIdentifyWithLimits(inputPath, false);
            }
            if (output == null || output.isBlank()) {
                throw new IOException("Failed to get image dimensions");
            }

            // For multi-image formats like ICO, only parse the first line
            String firstLine = output.split("\\r?\\n")[0].trim();
            String[] parts = firstLine.split("\\s+");

            if (parts.length >= 2) {
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
            }

            throw new IOException("Invalid dimension output: " + output);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted getting dimensions");
        } catch (NumberFormatException e) {
            throw new IOException("Invalid dimension values");
        }
    }

    /**
     * Run ImageMagick identify with memory/time limits. Used as fallback when -ping fails (e.g. HEIC).
     * @return Trimmed output or null on failure
     */
    private String runIdentifyWithLimits(String inputPath, boolean usePing) {
        for (List<String> identifyPrefix : List.of(
            List.of("magick", "identify"),
            List.of("identify")
        )) {
            try {
                List<String> args = new ArrayList<>(identifyPrefix);
                args.add("-limit");
                args.add("memory");
                args.add("128MiB");
                args.add("-limit");
                args.add("map");
                args.add("256MiB");
                args.add("-limit");
                args.add("time");
                args.add("15");
                if (usePing) args.add("-ping");
                args.add("-format");
                args.add("%w %h");
                args.add(inputPath);

                ProcessBuilder pb = new ProcessBuilder(args);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (!p.waitFor(15, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    continue;
                }
                if (p.exitValue() == 0 && out != null && !out.isBlank()) {
                    return out;
                }
            } catch (Exception e) {
                logger.debug("Identify fallback with {} failed: {}", identifyPrefix, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Cap sharpness based on image dimensions to respect 10s policy time limit.
     * Large images with high sharpness exceed the time limit due to complex operations.
     *
     * Limits:
     * - Images > 4000px: max 50% sharpness (simple unsharp mask only)
     * - Images > 2000px: max 100% sharpness (no LAB colorspace conversion)
     * - Images ≤ 2000px: no limit (all sharpness levels allowed)
     *
     * @param maxDimension Maximum of width or height
     * @param requestedSharpness User's requested sharpness level (0-200)
     * @return Capped sharpness level that will complete within 10s
     */
    private int capSharpnessForDimensions(int maxDimension, int requestedSharpness) {
        if (maxDimension > 4000) {
            // Very large images (>4000px): limit to 50% (subtle sharpening only)
            // 50% uses simple unsharp mask without LAB conversion
            return Math.min(requestedSharpness, 50);
        } else if (maxDimension > 2000) {
            // Large images (>2000px): limit to 100% (no professional sharpening)
            // 100% uses adaptive sharpening without LAB conversion
            return Math.min(requestedSharpness, 100);
        }
        // Small/medium images (≤2000px): no limit
        return requestedSharpness;
    }

    /**
     * Auto-resize large image to specified max dimension to reduce processing time.
     * This is a fast, simple resize operation that dramatically reduces conversion time
     * for large phone photos (12MP+) from 50-60s to 5-10s.
     *
     * @param input Original image file
     * @param maxDimension Maximum width or height (maintains aspect ratio)
     * @param inputFormatHint Format hint to help ImageMagick identify the input file (e.g., "png", "jpg")
     * @return Resized temp file (caller must delete)
     * @throws IOException if resize fails
     */
    private File autoResizeImage(File input, int maxDimension, String inputFormatHint) throws IOException, InterruptedException {
        // Use whitelisted format for extension; default to jpg if hint missing or invalid
        String safeHint = sanitizeFormatHint(inputFormatHint);
        String extension = safeHint != null ? "." + safeHint : ".jpg";
        File resized = Files.createTempFile("resized-" + UUID.randomUUID(), extension).toFile();

        try {
            IOException lastError = null;

            // Try both 'magick' (ImageMagick 7.0+) and 'convert' (ImageMagick 6.x) commands
            for (String cmd : List.of("magick", "convert")) {
                java.util.List<String> args = new java.util.ArrayList<>();
                args.add(cmd);
                args.add("-limit");
                args.add("time");
                args.add("30");  // Quick resize shouldn't take long
                args.add("-limit");
                args.add("memory");
                args.add("192MiB");

                // Add input file - ImageMagick will auto-detect format from file content
                args.add(input.getAbsolutePath());

                args.add("-resize");
                args.add(maxDimension + "x" + maxDimension + ">");  // Only shrink, don't enlarge

                args.add("-quality");
                args.add("90");  // Good quality for intermediate resize

                // Output file - format determined by extension
                args.add(resized.getAbsolutePath());

                ProcessBuilder pb = new ProcessBuilder(args);
                pb.redirectErrorStream(true);

                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes());

                if (!p.waitFor(30, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    safeDelete(resized);
                    throw new IOException("Auto-resize timeout");
                }

                if (p.exitValue() != 0) {
                    logger.warn("Auto-resize failed with '{}': {}", cmd, output);
                    lastError = new IOException("Auto-resize failed with '" + cmd + "': " + output);
                    continue;  // Try next command
                }

                if (!resized.exists() || resized.length() == 0) {
                    logger.warn("Auto-resize with '{}' produced empty file", cmd);
                    lastError = new IOException("Auto-resize produced empty file");
                    continue;  // Try next command
                }

                logger.debug("Auto-resize successful with '{}', output size: {} bytes", cmd, resized.length());
                return resized;
            }

            // If we got here, both commands failed
            safeDelete(resized);
            throw lastError != null ? lastError : new IOException("Auto-resize failed with all ImageMagick commands");

        } catch (Exception e) {
            safeDelete(resized);
            throw e;
        }
    }
}
