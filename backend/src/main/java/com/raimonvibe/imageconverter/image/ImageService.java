package com.raimonvibe.imageconverter.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
                pb.environment().put("MAGICK_MEMORY_LIMIT", "128MB");
                pb.environment().put("MAGICK_MAP_LIMIT", "256MB");
                pb.environment().put("MAGICK_DISK_LIMIT", "512MB");

                pb.redirectErrorStream(true);

                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Executing ImageMagick: {}", String.join(" ", pb.command()));
                    }

                    Process p = pb.start();

                    // Capture error output for debugging
                    String errorOutput = new String(p.getErrorStream().readAllBytes());
                    String standardOutput = new String(p.getInputStream().readAllBytes());

                    boolean finished = p.waitFor(15, TimeUnit.SECONDS);
                    if (!finished) {
                        p.destroyForcibly();
                        logger.warn("ImageMagick process timeout for command: {}", cmd);
                        lastError = new IOException("ImageMagick process timeout");
                        continue;
                    }

                    int code = p.exitValue();
                    if (code == 0 && out.exists() && out.length() > 0) {
                        logger.debug("ImageMagick conversion successful, output size: {} bytes", out.length());
                        return out;
                    }

                    logger.warn("ImageMagick failed with exit code {}: {}", code, errorOutput);
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
