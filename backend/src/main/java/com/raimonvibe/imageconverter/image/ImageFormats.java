package com.raimonvibe.imageconverter.image;

import java.util.List;
import java.util.Set;

/**
 * Single source of truth for supported image formats.
 * Previously the output whitelist lived in ConvertController.ALLOWED_OUT and
 * ImageService.supportedFormats(), and the input whitelist was duplicated
 * between ImageService.ALLOWED_FORMAT_HINTS and FileValidator.ALLOWED_EXT.
 */
public final class ImageFormats {

    private ImageFormats() {}

    /**
     * Supported output formats. Only safe raster formats; SVG/PDF excluded for
     * security, TIFF/BMP excluded because of high resource cost on small hosts.
     */
    public static final List<String> SUPPORTED_OUTPUT_FORMATS = List.of(
        "jpg", "jpeg", "png", "webp", "avif",
        "gif", "heic", "heif", "ico"
    );

    /** Same as {@link #SUPPORTED_OUTPUT_FORMATS} but as a set for fast whitelist checks. */
    public static final Set<String> SUPPORTED_OUTPUT_FORMAT_SET = Set.copyOf(SUPPORTED_OUTPUT_FORMATS);

    /**
     * Accepted input formats (upload extensions and ImageMagick format hints).
     * Wider than the output list: BMP/TIFF can be read but not produced.
     */
    public static final Set<String> ALLOWED_INPUT_FORMATS = Set.of(
        "png", "jpg", "jpeg", "webp", "avif",
        "gif", "bmp", "tiff", "tif", "heic", "heif", "ico"
    );

    /** Output formats allowed when converting GIF frames to a ZIP bundle. */
    public static final List<String> GIF_ZIP_OUTPUT_FORMATS = List.of(
        "jpeg", "jpg", "png", "webp", "heic", "heif"
    );

    /** Normalizes the "jpg" alias to the canonical "jpeg" format name. */
    public static String normalize(String format) {
        String lower = format.toLowerCase();
        return "jpg".equals(lower) ? "jpeg" : lower;
    }
}
