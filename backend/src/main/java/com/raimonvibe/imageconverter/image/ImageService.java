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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Facade over ImageMagick-based image conversion.
 * Process execution is delegated to {@link MagickCommandExecutor} (Template Method),
 * format-specific behavior to {@link FormatConversionStrategy} implementations,
 * and sharpening tiers to {@link SharpeningStrategy}.
 */
@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    /** Allowed format hints for ImageMagick (whitelist to prevent command-injection via format string). */
    private static final Set<String> ALLOWED_FORMAT_HINTS = ImageFormats.ALLOWED_INPUT_FORMATS;

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

    private final MagickCommandExecutor executor = new MagickCommandExecutor();

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

        logger.info("Conversion started: format={}, inputHint={}, size={} bytes", options.format(), inputFormatHint, input.length());

        // Declare these outside try block so they're accessible in catch/finally
        File processedInput = input;
        boolean wasAutoResized = false;

        try {
            String outExt = options.format().toLowerCase();
            FormatConversionStrategy formatStrategy = FormatConversionStrategies.forFormat(outExt);
            File out = Files.createTempFile("conv-" + UUID.randomUUID(), "." + outExt).toFile();

            // Detect image dimensions and cap sharpness to respect 10s policy time limit
            // Use the format hint if provided to help ImageMagick identify the file
            logger.debug("Flow: getting image dimensions (hint={})", inputFormatHint);
            int[] dimensions = getImageDimensions(input, inputFormatHint);
            int maxDimension = Math.max(dimensions[0], dimensions[1]);
            logger.info("Flow: dimensions {}x{}", dimensions[0], dimensions[1]);

            // Auto-resize large images (>1920px) to stay within time/memory limits on constrained hosts (e.g. Render 512MB)
            // Smartphone photos (e.g. iPhone 11 Pro 12MP) are often 4032x3024; full decode/resize can OOM
            if (maxDimension > 1920) {
                logger.info("Flow: auto-resizing {}x{} to 1920px", dimensions[0], dimensions[1]);
                processedInput = autoResizeImage(input, 1920, inputFormatHint);
                wasAutoResized = true;
                logger.debug("Flow: auto-resize done, processedInput size={}", processedInput.length());
            }

            // Format-specific dimension rules (e.g. PNG rejects sources >4000px because
            // its lossless compression is too slow for large photos even after resize)
            formatStrategy.validateDimensions(dimensions[0], dimensions[1]);

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
            logger.info("Flow: conversion loop starting (will try: {})", MagickCommandExecutor.CONVERT_COMMANDS);

            // Set resource limits to avoid OOM on constrained hosts (e.g. Render free 512MB)
            // Auto-resize and sharpness capping keep conversions within time/memory
            int magickTimeLimit = adjustedOptions.sharpness() != null && adjustedOptions.sharpness() > 100 ? 120 : 60;

            // Increased timeout for complex sharpening operations (especially 100-200% sharpness)
            // Process timeout should be longer than MAGICK_TIME_LIMIT to allow ImageMagick to finish
            int timeoutSeconds = options.sharpness() != null && options.sharpness() > 100 ? 150 : 90;

            Map<String, String> env = Map.of(
                "MAGICK_MEMORY_LIMIT", "192MB",
                "MAGICK_MAP_LIMIT", "384MB",
                "MAGICK_DISK_LIMIT", "512MB",
                "MAGICK_TIME_LIMIT", String.valueOf(magickTimeLimit)
            );

            for (List<String> cmdPrefix : MagickCommandExecutor.CONVERT_COMMANDS) {
                String cmd = String.join(" ", cmdPrefix);
                logger.debug("Conversion: trying command: {}", cmd);

                List<String> args = buildConvertArgs(
                    cmdPrefix, processedInput, out, outExt, adjustedOptions,
                    formatStrategy, inputFormatHint, magickTimeLimit
                );

                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Executing ImageMagick with time limit {}s, sharpness={}: {}",
                            magickTimeLimit, adjustedOptions.sharpness(), String.join(" ", args));
                    } else {
                        logger.info("Converting {}x{} image - sharpness: {}%, format: {}",
                            dimensions[0], dimensions[1], adjustedOptions.sharpness(), adjustedOptions.format());
                    }

                    MagickCommandExecutor.Execution result = executor.run(args, env, timeoutSeconds);

                    if (!result.finished()) {
                        lastError = new IOException("ImageMagick process timeout - try reducing sharpness level");
                        continue;
                    }

                    if (result.succeeded() && out.exists() && out.length() > 0) {
                        logger.info("Flow: conversion succeeded with command '{}', output {} bytes", cmd, out.length());
                        return out;
                    }

                    logger.warn("ImageMagick failed with exit code {}: {}", result.exitCode(), result.output());
                    lastError = new IOException("Conversion failed with '" + cmd + "', exit code=" + result.exitCode());
                } catch (IOException ioe) {
                    logger.error("Conversion: command '{}' failed: {} (trying next)", cmd, ioe.getMessage());
                    lastError = ioe;
                }
            }

            logger.error("Conversion: ALL commands failed (tried: {}). Last error: {}",
                MagickCommandExecutor.CONVERT_COMMANDS, lastError != null ? lastError.getMessage() : "none");
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
     * Builds the full ImageMagick argument list for one conversion attempt.
     * Format-specific behavior (width-resize applicability, extra output args)
     * comes from the {@link FormatConversionStrategy}; sharpening tiers from
     * {@link SharpeningStrategy}.
     */
    private List<String> buildConvertArgs(List<String> cmdPrefix, File processedInput, File out,
                                          String outExt, ConversionOptions adjustedOptions,
                                          FormatConversionStrategy formatStrategy,
                                          String inputFormatHint, int magickTimeLimit) {
        List<String> args = new ArrayList<>(cmdPrefix);

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
        if (safeHint != null) {
            String inputArg = safeHint + ":" + processedInput.getAbsolutePath();
            if ("ico".equals(safeHint)) {
                // Multi-image ICO input: only convert the first (largest) icon
                inputArg = inputArg + "[0]";
            }
            args.add(inputArg);
        } else {
            args.add(processedInput.getAbsolutePath());
        }

        // Apply EXIF orientation so output displays upright (e.g. phone photos in portrait)
        args.add("-auto-orient");

        // Apply width resize if specified and the target format supports it (ICO uses fixed sizes)
        if (adjustedOptions.width() != null && adjustedOptions.width() > 0 && formatStrategy.supportsWidthResize()) {
            // Resize maintaining aspect ratio, only if larger than specified width
            args.add("-resize");
            args.add(adjustedOptions.width() + "x>");
        }

        formatStrategy.appendOutputArgs(args);

        // Sharpening is applied AFTER resize for best results; level is already
        // capped based on image dimensions to respect the 10s policy
        if (adjustedOptions.sharpness() != null && adjustedOptions.sharpness() > 0) {
            SharpeningStrategy.forLevel(adjustedOptions.sharpness()).apply(args, adjustedOptions.sharpness());
        }

        if (adjustedOptions.quality() != null) {
            args.add("-quality");
            args.add(String.valueOf(adjustedOptions.quality()));
        }

        args.add(out.getAbsolutePath());
        return args;
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
        List<String> supportedFormats = ImageFormats.GIF_ZIP_OUTPUT_FORMATS;
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

            extractGifFrames(input, tempDir.resolve("frame-%03d.png").toString());

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
                        String normalizedFormat = ImageFormats.normalize(format);

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

    /**
     * Extracts all frames of a GIF as PNGs using ImageMagick coalesce,
     * trying each known ImageMagick command until one succeeds.
     */
    private void extractGifFrames(File input, String framePattern) throws IOException, InterruptedException {
        logger.debug("Extracting GIF frames from: {}", input.getName());

        Map<String, String> env = Map.of(
            "MAGICK_TIME_LIMIT", "120",
            "MAGICK_MEMORY_LIMIT", "192MB"
        );

        for (List<String> cmdPrefix : MagickCommandExecutor.GIF_EXTRACT_COMMANDS) {
            try {
                List<String> args = new ArrayList<>(cmdPrefix);
                args.add("-limit");
                args.add("time");
                args.add("120");
                args.add("-limit");
                args.add("memory");
                args.add("192MiB");
                args.add("-limit");
                args.add("map");
                args.add("384MiB");
                args.add(input.getAbsolutePath());
                args.add("-coalesce");
                args.add(framePattern);

                MagickCommandExecutor.Execution result = executor.run(args, env, 150);
                if (!result.finished()) {
                    continue;
                }
                if (result.exitCode() != 0) {
                    logger.debug("GIF extraction with {} failed: {}", cmdPrefix, result.output());
                    continue;
                }
                return;
            } catch (IOException e) {
                logger.warn("GIF extract: command '{}' failed: {} (trying next)", cmdPrefix, e.getMessage());
            }
        }

        logger.error("GIF extract: all commands failed (tried: {})", MagickCommandExecutor.GIF_EXTRACT_COMMANDS);
        throw new IOException("Failed to extract GIF frames (magick/convert not available or failed)");
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

    /** Supported output formats. Single source of truth lives in {@link ImageFormats}. */
    public static List<String> supportedFormats() {
        return ImageFormats.SUPPORTED_OUTPUT_FORMATS;
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

            String output = runIdentify(inputPath, true, 10, 10);
            if (output == null || output.isBlank()) {
                logger.debug("Dimensions: -ping failed, retrying identify without -ping");
                output = runIdentify(inputPath, false, 15, 15);
            }
            if (output == null || output.isBlank()) {
                logger.error("Dimensions: ALL identify attempts failed (tried: {})", MagickCommandExecutor.IDENTIFY_COMMANDS);
                throw new IOException("Failed to get image dimensions");
            }

            // For multi-image formats like ICO, only parse the first line
            String firstLine = output.split("\\r?\\n")[0].trim();
            String[] parts = firstLine.split("\\s+");

            if (parts.length >= 2) {
                logger.debug("Flow: dimensions parsed: {}x{}", parts[0], parts[1]);
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
            }

            logger.error("Dimensions: invalid output (expected width height): {}", output);
            throw new IOException("Invalid dimension output: " + output);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted getting dimensions");
        } catch (NumberFormatException e) {
            throw new IOException("Invalid dimension values");
        }
    }

    /**
     * Runs ImageMagick identify with memory/time limits, trying each known
     * identify command until one succeeds. The -ping variant does a fast
     * header-only read; the full variant is a fallback for formats where
     * -ping fails (e.g. HEIC).
     *
     * @return Trimmed "width height" output, or null when all commands fail
     */
    private String runIdentify(String inputPath, boolean usePing, int timeLimitSeconds, int timeoutSeconds)
            throws InterruptedException {
        for (List<String> cmdPrefix : MagickCommandExecutor.IDENTIFY_COMMANDS) {
            try {
                logger.debug("Dimensions: trying identify with command prefix: {}", cmdPrefix);
                List<String> args = new ArrayList<>(cmdPrefix);
                args.add("-limit");
                args.add("memory");
                args.add("128MiB");
                args.add("-limit");
                args.add("map");
                args.add("256MiB");
                args.add("-limit");
                args.add("time");
                args.add(String.valueOf(timeLimitSeconds));
                if (usePing) args.add("-ping");
                args.add("-format");
                args.add("%w %h");
                args.add(inputPath);

                MagickCommandExecutor.Execution result = executor.run(args, null, timeoutSeconds);
                if (result.succeeded() && !result.output().isBlank()) {
                    return result.output().trim();
                }
            } catch (IOException e) {
                logger.error("Dimensions: identify with {} failed: {} (trying next)", cmdPrefix, e.getMessage());
            }
        }
        logger.debug("runIdentify: all attempts returned null (usePing={})", usePing);
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
            return Math.min(requestedSharpness, 50);
        } else if (maxDimension > 2000) {
            // Large images (>2000px): limit to 100% (no professional sharpening)
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

            for (List<String> cmdPrefix : MagickCommandExecutor.CONVERT_COMMANDS) {
                String cmd = String.join(" ", cmdPrefix);
                List<String> args = new ArrayList<>(cmdPrefix);
                args.add("-limit");
                args.add("time");
                args.add("30");  // Quick resize shouldn't take long
                args.add("-limit");
                args.add("memory");
                args.add("192MiB");

                // Add input file - ImageMagick will auto-detect format from file content
                args.add(input.getAbsolutePath());
                args.add("-auto-orient");

                args.add("-resize");
                args.add(maxDimension + "x" + maxDimension + ">");  // Only shrink, don't enlarge

                args.add("-quality");
                args.add("90");  // Good quality for intermediate resize

                // Output file - format determined by extension
                args.add(resized.getAbsolutePath());

                try {
                    MagickCommandExecutor.Execution result = executor.run(args, null, 30);

                    if (!result.finished()) {
                        safeDelete(resized);
                        throw new IOException("Auto-resize timeout");
                    }

                    if (result.exitCode() != 0) {
                        logger.warn("Auto-resize failed with '{}': {}", cmd, result.output());
                        lastError = new IOException("Auto-resize failed with '" + cmd + "': " + result.output());
                        continue;  // Try next command
                    }

                    if (!resized.exists() || resized.length() == 0) {
                        logger.warn("Auto-resize with '{}' produced empty file", cmd);
                        lastError = new IOException("Auto-resize produced empty file");
                        continue;  // Try next command
                    }

                    logger.debug("Auto-resize successful with '{}', output size: {} bytes", cmd, resized.length());
                    return resized;
                } catch (IOException ioe) {
                    logger.warn("Auto-resize: command '{}' failed: {} (trying next)", cmd, ioe.getMessage());
                    lastError = ioe;
                }
            }

            // If we got here, all commands failed
            safeDelete(resized);
            throw lastError != null ? lastError : new IOException("Auto-resize failed with all ImageMagick commands");

        } catch (Exception e) {
            safeDelete(resized);
            throw e;
        }
    }
}
