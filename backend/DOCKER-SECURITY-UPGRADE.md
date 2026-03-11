# Docker image security upgrade – test and rollback

The runtime image runs `apk update && apk upgrade --no-cache` before installing app packages. This fixes the 3 OS-level vulnerabilities reported for `eclipse-temurin:17-jre-alpine` (Alpine 3.23.3):

| Severity | Package | Issue | Fixed in |
|----------|---------|--------|----------|
| **C** | zlib | Out-of-bounds Write (Score 786) | zlib@1.3.2-r0 |
| **H** | libpng | Heap-based Buffer Overflow (Score 686) | libpng@1.6.55-r0 |
| **M** | zlib | Improper Validation of Specified Quantity in Input (Score 586) | zlib@1.3.2-r0 |

Alpine 3.23 repos provide these versions; `apk upgrade` pulls them in. Rebuild the image and re-run your security scan (e.g. Snyk/GitHub Advanced Security) to confirm the issues are cleared.

## Before deploying

1. **Run backend tests (baseline and after change):**
   ```bash
   cd backend
   ./mvnw test
   ```
   All tests should pass before and after the Dockerfile change.

2. **Optional – build and run the image locally:**
   ```bash
   cd backend
   docker build -t change-my-backend:latest .
   # With database (e.g. from project root):
   docker compose up -d
   docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/imageconverter -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=postgres change-my-backend:latest
   ```
   Then hit `http://localhost:8080/health`. The app needs PostgreSQL (or set `SPRING_DATASOURCE_URL` to a reachable DB); without it the container will exit.

3. **After deploy:** Monitor logs and conversion/health endpoints. If anything breaks, roll back.

## Rollback

- **Revert the Dockerfile change only:**
  ```bash
  git checkout HEAD -- backend/Dockerfile
  ```
  Then rebuild and redeploy.

- **Revert the whole commit** (if you already committed):
  ```bash
  git revert <commit-sha> --no-edit
  git push
  ```

## Why this works

The base image `eclipse-temurin:17-jre-alpine` (Alpine 3.23.3) ships older zlib/libpng. Alpine’s repos for 3.23 already contain patched versions (zlib 1.3.2-r0, libpng 1.6.55-r0). Running `apk upgrade` in the Dockerfile applies those updates in the image so scanners no longer report the 3 CVEs.
