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

                // Apply sharpening if requested (0-200 scale)
                if (options.sharpness() != null && options.sharpness() > 0) {
                    // Convert 0-200 scale to ImageMagick parameters
                    // radius: 0-2.0, amount: 0-2.0, threshold: 0.03
                    double radius = 1.5;
                    double amount = options.sharpness() / 100.0; // 0-2.0
                    double threshold = 0.03;

                    args.add("-unsharp");
                    args.add(String.format("%.1fx%.1f+%.2f+%.2f", radius, radius, amount, threshold));
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
