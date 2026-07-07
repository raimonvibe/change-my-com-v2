package com.raimonvibe.imageconverter.image;

import java.util.List;

/**
 * Strategy for the tiered sharpening approach, formalizing what used to be a
 * single if/else chain in ImageService.applySharpeningStrategy(). Each tier
 * uses progressively more sophisticated ImageMagick techniques.
 */
public enum SharpeningStrategy {

    /** Subtle sharpening (1-50): gentle unsharp mask for natural enhancement. */
    SUBTLE {
        @Override
        public void apply(List<String> args, int sharpness) {
            double amount = sharpness / 50.0; // 0.02 to 1.0
            args.add("-unsharp");
            args.add(String.format("0.5x0.5+%.2f+0.01", amount));
        }
    },

    /**
     * Standard adaptive sharpening (51-100): adjusts based on local image
     * features, sharpening edges more than flat areas (reduces noise amplification).
     */
    ADAPTIVE {
        @Override
        public void apply(List<String> args, int sharpness) {
            double strength = (sharpness - 50) / 25.0; // 0.04 to 2.0
            args.add("-adaptive-sharpen");
            args.add(String.format("0x%.2f", strength));
        }
    },

    /**
     * Professional multi-pass sharpening (101-150): LAB color space so only
     * luminosity is sharpened (prevents color artifacts); fine detail pass
     * followed by edge enhancement.
     */
    PROFESSIONAL {
        @Override
        public void apply(List<String> args, int sharpness) {
            args.add("-colorspace");
            args.add("Lab");
            args.add("-channel");
            args.add("L"); // Sharpen only Lightness channel

            // First pass: fine detail enhancement
            args.add("-unsharp");
            args.add("0.5x0.5+1.0+0.02");

            // Second pass: edge sharpening with strength based on level
            double edgeStrength = (sharpness - 100) / 25.0; // 0.04 to 2.0
            args.add("-unsharp");
            args.add(String.format("2x1+%.2f+0.05", 0.8 + edgeStrength));

            args.add("+channel");
            args.add("-colorspace");
            args.add("sRGB");
        }
    },

    /**
     * Maximum sharpening (151-200): contrast enhancement plus aggressive
     * multi-pass sharpening, similar to Photoshop's high-pass filter technique.
     */
    MAXIMUM {
        @Override
        public void apply(List<String> args, int sharpness) {
            args.add("-contrast-stretch");
            args.add("0.15x0.05%");

            args.add("-colorspace");
            args.add("Lab");
            args.add("-channel");
            args.add("L");

            double maxStrength = (sharpness - 150) / 50.0; // 0.02 to 1.0
            args.add("-unsharp");
            args.add(String.format("1x0.8+%.2f+0.05", 2.0 + maxStrength));

            args.add("-adaptive-sharpen");
            args.add(String.format("0x%.2f", 1.5 + maxStrength));

            args.add("+channel");
            args.add("-colorspace");
            args.add("sRGB");
        }
    };

    public abstract void apply(List<String> args, int sharpness);

    /** Selects the tier for a sharpness level (1-200). */
    public static SharpeningStrategy forLevel(int sharpness) {
        if (sharpness <= 50) return SUBTLE;
        if (sharpness <= 100) return ADAPTIVE;
        if (sharpness <= 150) return PROFESSIONAL;
        return MAXIMUM;
    }
}
