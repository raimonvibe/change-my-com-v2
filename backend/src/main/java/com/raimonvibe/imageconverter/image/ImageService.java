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

    public record ConversionOptions(String format, Integer quality) {}

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

                // Special handling for ICO format
                if ("ico".equals(outExt)) {
                    // ICO requires specific sizing and color handling
                    if (options.quality() != null) {
                        pb = new ProcessBuilder(cmd, input.getAbsolutePath(),
                                "-resize", "256x256",
                                "-define", "icon:auto-resize=256,128,64,48,32,16",
                                "-colors", "256",
                                "-quality", String.valueOf(options.quality()),
                                out.getAbsolutePath());
                    } else {
                        pb = new ProcessBuilder(cmd, input.getAbsolutePath(),
                                "-resize", "256x256",
                                "-define", "icon:auto-resize=256,128,64,48,32,16",
                                "-colors", "256",
                                out.getAbsolutePath());
                    }
                } else {
                    pb = (options.quality() != null)
                            ? new ProcessBuilder(cmd, input.getAbsolutePath(),
                                    "-quality", String.valueOf(options.quality()), out.getAbsolutePath())
                            : new ProcessBuilder(cmd, input.getAbsolutePath(), out.getAbsolutePath());
                }

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
     */
    public static List<String> supportedFormats() {
        return List.of(
            "jpg", "jpeg", "png", "webp", "avif",
            "gif", "bmp", "tiff", "tif", "heic", "heif",
            "ico"
        );
    }
}
