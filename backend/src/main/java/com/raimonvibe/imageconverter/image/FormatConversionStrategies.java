package com.raimonvibe.imageconverter.image;

import java.io.IOException;
import java.util.List;

/**
 * Factory for {@link FormatConversionStrategy} implementations, keyed by
 * output format. Plain static factory (not Spring-managed) so ImageService
 * stays constructible without a container.
 */
public final class FormatConversionStrategies {

    private FormatConversionStrategies() {}

    private static final FormatConversionStrategy DEFAULT = new FormatConversionStrategy() {};
    private static final FormatConversionStrategy PNG = new PngStrategy();
    private static final FormatConversionStrategy ICO = new IcoStrategy();

    public static FormatConversionStrategy forFormat(String outputFormat) {
        return switch (outputFormat) {
            case "png" -> PNG;
            case "ico" -> ICO;
            default -> DEFAULT;
        };
    }

    /**
     * PNG uses expensive lossless compression that is too slow for very large
     * photos, even after auto-resize, so conversions above 4000px are rejected.
     */
    private static final class PngStrategy implements FormatConversionStrategy {
        private static final int MAX_PNG_SOURCE_DIMENSION = 4000;

        @Override
        public void validateDimensions(int width, int height) throws IOException {
            if (Math.max(width, height) > MAX_PNG_SOURCE_DIMENSION) {
                throw new IOException(String.format(
                    "PNG conversion not supported for very large images (%dx%d). " +
                    "Even after auto-resize, PNG compression is too slow for photos >4000px. " +
                    "Use WebP (recommended), JPEG, or AVIF instead for faster conversions.",
                    width, height
                ));
            }
        }
    }

    /**
     * ICO ignores the user width option and always produces a fixed multi-size
     * icon (256 down to 16 pixels) with a 256-color palette.
     */
    private static final class IcoStrategy implements FormatConversionStrategy {
        @Override
        public boolean supportsWidthResize() {
            return false;
        }

        @Override
        public void appendOutputArgs(List<String> args) {
            args.add("-resize");
            args.add("256x256");
            args.add("-define");
            args.add("icon:auto-resize=256,128,64,48,32,16");
            args.add("-colors");
            args.add("256");
        }
    }
}
