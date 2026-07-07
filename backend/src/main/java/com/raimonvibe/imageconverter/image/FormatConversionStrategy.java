package com.raimonvibe.imageconverter.image;

import java.io.IOException;
import java.util.List;

/**
 * Strategy for output-format-specific conversion behavior.
 * Replaces the scattered if ("png"...) / if ("ico"...) branches that used to
 * live inside ImageService.convert(). New formats with special needs get their
 * own implementation instead of another conditional in the conversion flow.
 */
public interface FormatConversionStrategy {

    /**
     * Validates the source image dimensions for this output format.
     * Called before conversion starts so oversized requests fail fast.
     */
    default void validateDimensions(int width, int height) throws IOException {}

    /** Whether the user-supplied width resize option applies to this format. */
    default boolean supportsWidthResize() {
        return true;
    }

    /** Appends format-specific ImageMagick arguments (after resize, before sharpening). */
    default void appendOutputArgs(List<String> args) {}
}
