# Image conversion design (from scratch)

This document describes how the image conversion flow would be designed from scratch for a service running on Render (Docker), and how it aligns with the current code. It also calls out logic that was wrong and how it was fixed.

## 1. Environment

- **Runtime**: Render runs the app in a Docker container (Dockerfile at `backend/Dockerfile`, build context `backend/`).
- **Base image**: `eclipse-temurin:25.0.2_10-jre-jammy` (Ubuntu Jammy).
- **ImageMagick**: Installed with `apt-get install imagemagick`. On Ubuntu this is **ImageMagick 6**, which provides:
  - `/usr/bin/convert` — convert/resize/process images
  - `/usr/bin/identify` — read dimensions/format
  - **No** `magick` binary (that is ImageMagick 7 only).

So in the container we must use `convert` and `identify`, not `magick`.

## 2. Process execution and PATH

- Java runs the app as a non-root user; the process is started by the container’s entrypoint.
- `ProcessBuilder` runs external commands using the **process environment**, including `PATH`.
- In minimal or custom images, `PATH` may **not** include `/usr/bin`. So:
  - `new ProcessBuilder("convert", ...)` can fail with “No such file or directory” if `convert` is not on `PATH`.
  - `new ProcessBuilder("/usr/bin/convert", ...)` does **not** depend on `PATH` and is the reliable way to run ImageMagick in the container.

**Design rule:** Prefer **full paths** (`/usr/bin/convert`, `/usr/bin/identify`) when running in a Linux container so the app does not depend on `PATH`. Use short names (`convert`, `identify`, `magick`) only as fallbacks for local/dev where IM6 or IM7 might be on `PATH`.

## 3. Command order (logic fix)

**Wrong (original) order:**

1. Try `magick` (IM7 only → fails on Render).
2. Try `convert` (may fail if `PATH` does not include `/usr/bin`).
3. Try `/usr/bin/convert` (should work in Docker).

So in theory the third attempt could succeed, but the first two always fail on Render, and we were seeing “Cannot run program 'magick'” because the **first** exception was kept and rethrown. Logs also didn’t make it obvious that all three were tried.

**Correct order:**

1. Try **`/usr/bin/convert`** and **`/usr/bin/identify`** first (container-friendly, no `PATH` dependency).
2. Then try **`convert`** and **`identify`** (for dev or images that set `PATH`).
3. Then try **`magick`** (for ImageMagick 7 on dev machines).

So we try the form that works in Docker first, and only then fall back to short names and IM7.

## 4. Two places that run ImageMagick

| Purpose           | Commands to try (in order) |
|------------------|----------------------------|
| Get dimensions   | `/usr/bin/identify`, `identify`, `magick identify` (with args: `magick`, `identify`, …) |
| Convert image     | `/usr/bin/convert`, `convert`, `magick` |

For “identify” we need a list of argument lists, e.g.:

- `["/usr/bin/identify", "-limit", ...]`
- `["identify", "-limit", ...]`
- `["magick", "identify", "-limit", ...]`

For “convert” we have a single binary and the same args, so we just try, in order: `/usr/bin/convert`, `convert`, `magick`.

## 5. Flow (high level)

1. **Upload** → validate file (size, type, magic bytes) → save to temp file.
2. **Dimensions** → run `identify` (with the chosen prefix) to get width/height; used for validation and for sharpness capping.
3. **Convert** → run `convert` (with the chosen command) with limits and options (resize, sharpness, quality, format) → write to output file.
4. **Response** → stream the output file and clean up temp files.

All external calls must use the “try full path first, then short names” order above so that Docker (Render) works without relying on `PATH`.

## 6. Logging

- Log at ERROR when a **specific** attempt fails (e.g. “Conversion: command '/usr/bin/convert' failed: …”) and when **all** attempts fail (“Conversion: ALL commands failed (tried: …)”).
- So in Render logs we can see that we tried `/usr/bin/convert` (and others) and what error we got, instead of only the first failure (“magick” not found).

## 7. Summary of code changes

- **ImageService (dimensions):** Try `/usr/bin/identify` first, then `identify`, then `magick` + `identify`. Same order in `getImageDimensions` and in `runIdentifyWithLimits`.
- **ImageService (conversion):** Try `/usr/bin/convert` first, then `convert`, then `magick`. Same idea in the main convert loop and in any auto-resize path that shells out to ImageMagick.
- **ImageService (auto-resize):** Use the same ordered list of commands (full path first) and **catch `IOException` around `ProcessBuilder.start()`** so that if one command is not found we try the next instead of failing immediately.
- **Logging:** Keep ERROR-level logs for each failed attempt and for “all attempts failed” so Render logs show the full story.

After these changes, the first attempt in the container is `/usr/bin/identify` and `/usr/bin/convert`, which match the ImageMagick 6 install from `apt-get install imagemagick` and do not depend on `PATH`.
