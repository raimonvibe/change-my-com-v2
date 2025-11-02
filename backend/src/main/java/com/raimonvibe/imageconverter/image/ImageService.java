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
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    public record ConversionOptions(String format, Integer quality, Integer sharpness, Integer width) {}

    // Maximaal 4 conversies tegelijk
    private final Semaphore semaphore = new Semaphore(4);

    /**
     * Converteert input-bestand naar target-format via ImageMagick ("magick" CLI).
     * - Concurrency gelimiteerd met Semaphore
     * - Timeout van 15 seconden
     * - Temp output in system temp
     */
    public File convert(File input, ConversionOptions options) throws IOException, InterruptedException {
        if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new IOException("Server busy: too many concurrent conversions");
        }
        try {
            String outExt = options.format().toLowerCase();
            File out = Files.createTempFile("conv-" + UUID.randomUUID(), "." + outExt).toFile();
            IOException lastError = null;

            for (String cmd : List.of("magick", "convert")) {
                ProcessBuilder pb;

                // Build command arguments
                java.util.List<String> args = new java.util.ArrayList<>();
                args.add(cmd);

                // Set resource limits as command-line options (more reliable than env vars)
                // These override both environment variables and policy.xml settings
                int magickTimeLimit = options.sharpness() != null && options.sharpness() > 100 ? 120 : 60;
                args.add("-limit");
                args.add("time");
                args.add(String.valueOf(magickTimeLimit));
                args.add("-limit");
                args.add("memory");
                args.add("256MiB");
                args.add("-limit");
                args.add("map");
                args.add("512MiB");

                args.add(input.getAbsolutePath());

                // Apply width resize if specified (before format-specific handling)
                if (options.width() != null && options.width() > 0 && !"ico".equals(outExt)) {
                    // Resize maintaining aspect ratio, only if larger than specified width
                    args.add("-resize");
                    args.add(options.width() + "x>");
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
                if (options.sharpness() != null && options.sharpness() > 0) {
                    applySharpeningStrategy(args, options.sharpness());
                }

                // Apply quality if specified
                if (options.quality() != null) {
                    args.add("-quality");
                    args.add(String.valueOf(options.quality()));
                }

                args.add(out.getAbsolutePath());
                pb = new ProcessBuilder(args);

                // Set memory limit for ImageMagick to prevent OOM on low-resource servers
                // Increased limits to handle high sharpness conversions (100-200%)
                pb.environment().put("MAGICK_MEMORY_LIMIT", "256MB");
                pb.environment().put("MAGICK_MAP_LIMIT", "512MB");
                pb.environment().put("MAGICK_DISK_LIMIT", "1GB");

                // Set ImageMagick's internal time limit (separate from process timeout)
                // This controls ImageMagick's own resource manager time limit
                // Higher values needed for complex sharpening operations (100-200%)
                // Increased limits for cloud/shared hosting environments with limited CPU
                int magickTimeLimit = options.sharpness() != null && options.sharpness() > 100 ? 120 : 60;
                pb.environment().put("MAGICK_TIME_LIMIT", String.valueOf(magickTimeLimit));

                pb.redirectErrorStream(true);

                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Executing ImageMagick with time limit {}s, sharpness={}: {}",
                            magickTimeLimit, options.sharpness(), String.join(" ", pb.command()));
                    } else {
                        logger.info("Converting with time limit: {}s, sharpness: {}, format: {}",
                            magickTimeLimit, options.sharpness(), options.format());
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
                    logger.error("IOException during ImageMagick execution: {}", ioe.getMessage());
                    lastError = ioe;
                }
            }

            throw (lastError != null ? lastError : new IOException("Unknown conversion error"));
        } finally {
            semaphore.release();
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

            // Extract GIF frames using ImageMagick coalesce
            // This splits animated GIF into individual frames
            logger.debug("Extracting GIF frames from: {}", input.getName());
            String framePattern = tempDir.resolve("frame-%03d.png").toString();

            ProcessBuilder extractPb = new ProcessBuilder(
                "magick",
                "-limit", "time", "120",
                "-limit", "memory", "256MiB",
                "-limit", "map", "512MiB",
                input.getAbsolutePath(),
                "-coalesce",  // Properly handle GIF animation layers
                framePattern
            );
            // Also set environment variables as fallback
            extractPb.environment().put("MAGICK_TIME_LIMIT", "120");
            extractPb.environment().put("MAGICK_MEMORY_LIMIT", "256MB");
            extractPb.redirectErrorStream(true);

            logger.info("Extracting GIF frames with 120s time limit");

            Process extractProcess = extractPb.start();
            boolean extractFinished = extractProcess.waitFor(150, TimeUnit.SECONDS);
            if (!extractFinished) {
                extractProcess.destroyForcibly();
                throw new IOException("GIF frame extraction timeout");
            }

            if (extractProcess.exitValue() != 0) {
                String error = new String(extractProcess.getInputStream().readAllBytes());
                logger.error("GIF extraction failed: {}", error);
                throw new IOException("Failed to extract GIF frames");
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

                        // Convert frame
                        File converted = convert(frame, frameOptions);
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
}
