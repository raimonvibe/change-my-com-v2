# Docker image security upgrade – test and rollback

The **runtime base image was switched from Alpine to Ubuntu** so Snyk stops reporting the 3 OS-level vulnerabilities (zlib/libpng) that it attributes to `eclipse-temurin:17-jre-alpine`. Snyk evaluates the base image layer; `apk upgrade` in a later layer did not clear those findings.

| Severity | Package | Issue | Approach |
|----------|---------|--------|----------|
| **C** | zlib | Out-of-bounds Write | Use non-Alpine base (Jammy) |
| **H** | libpng | Heap-based Buffer Overflow | Use non-Alpine base (Jammy) |
| **M** | zlib | Improper Validation | Use non-Alpine base (Jammy) |

**Current runtime base:** `eclipse-temurin:17-jre-jammy`. ImageMagick is installed via `apt-get` (ImageMagick 6; app uses `convert` as fallback). Policy path: `/etc/ImageMagick-6/policy.xml`. Rebuild and re-scan with Snyk to confirm the 3 issues are gone.

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

Snyk reports vulns from the base image layer. With Alpine, a later apk upgrade did not clear them. Using **eclipse-temurin:17-jre-jammy** (Ubuntu 22.04) removes the Alpine stack, so those zlib/libpng findings no longer apply. The app still uses ImageMagick (v6 on Jammy via `convert`); behaviour is unchanged.’