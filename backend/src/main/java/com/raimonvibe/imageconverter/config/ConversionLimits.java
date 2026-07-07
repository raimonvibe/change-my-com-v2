package com.raimonvibe.imageconverter.config;

/**
 * Single source of truth for conversion quota and size limits.
 * Deliberately fixed in code (not externalized configuration) so a bad
 * environment variable can never raise the free limit or upload size.
 * Previously these constants were duplicated across ConvertController,
 * UserController and AnonymousUserController with "must match" comments.
 */
public final class ConversionLimits {

    private ConversionLimits() {}

    /** Free conversions per day for all users, anonymous (IP-based) and logged-in alike. */
    public static final int FREE_DAILY_LIMIT = 20;

    /** Maximum accepted upload size in bytes (must stay in sync with spring.servlet.multipart limits). */
    public static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    /** Maximum image width/height in pixels accepted for conversion. */
    public static final int MAX_IMAGE_DIMENSION = 8000;
}
