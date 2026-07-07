package com.raimonvibe.imageconverter.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Template Method for running ImageMagick CLI commands.
 *
 * ImageMagick may be installed as IM6 ("convert"/"identify", possibly only under
 * /usr/bin) or IM7 ("magick"). Every caller used to duplicate the same
 * "try each command until one works" loop; this class centralizes the process
 * execution, environment setup, output capture and timeout handling, while the
 * callers only vary the argument list per attempt.
 */
public class MagickCommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MagickCommandExecutor.class);

    /** Convert commands in preferred order: full path first (container PATH may omit /usr/bin), then IM6, then IM7. */
    public static final List<List<String>> CONVERT_COMMANDS = List.of(
        List.of("/usr/bin/convert"), List.of("convert"), List.of("magick")
    );

    /** GIF frame extraction prefers IM7 "magick" first (historical order kept for behavior parity). */
    public static final List<List<String>> GIF_EXTRACT_COMMANDS = List.of(
        List.of("magick"), List.of("convert"), List.of("/usr/bin/convert")
    );

    /** Identify commands: full path first, then IM6 "identify", then IM7 "magick identify". */
    public static final List<List<String>> IDENTIFY_COMMANDS = List.of(
        List.of("/usr/bin/identify"), List.of("identify"), List.of("magick", "identify")
    );

    /** Result of a single ImageMagick invocation. */
    public record Execution(int exitCode, String output, boolean finished) {
        public boolean succeeded() {
            return finished && exitCode == 0;
        }
    }

    /**
     * Builds the argument list for one attempt, given the command prefix
     * (e.g. ["magick", "identify"]) for that attempt.
     */
    @FunctionalInterface
    public interface ArgsBuilder {
        List<String> build(List<String> commandPrefix);
    }

    /**
     * Runs a single ImageMagick process with merged stderr, optional extra
     * environment variables, and a hard timeout. On timeout the process is
     * forcibly destroyed and {@code finished} is false.
     */
    public Execution run(List<String> args, Map<String, String> extraEnv, int timeoutSeconds)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
        }
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            logger.warn("ImageMagick process timeout after {}s: {}", timeoutSeconds, args.get(0));
            return new Execution(-1, output, false);
        }
        return new Execution(p.exitValue(), output, true);
    }
}
