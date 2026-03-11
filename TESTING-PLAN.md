# Comprehensive Testing Plan - Image Converter Application

## Application Overview

**Name:** change-my.com - Free Online Image Converter
**Version:** 0.0.1-SNAPSHOT
**Status:** Production Ready ✅
**Type:** Full-stack web application

### Technology Stack

**Backend:**
- Framework: Spring Boot 3.5.6
- Language: Java 17
- Build Tool: Maven 3.8.7
- Database: PostgreSQL (with Flyway migrations)
- ORM: Hibernate/JPA
- Authentication: Google OAuth 2.0 + JWT
- Payment: Stripe API (v30.0.0)
- Rate Limiting: Bucket4j 8.15.0
- Image Processing: ImageMagick 6.9.12+

**Frontend:**
- Framework: Next.js 15.5.6
- Language: TypeScript 5
- UI Library: React 19.2.0
- Styling: Tailwind CSS 4
- Auth: NextAuth.js 4.24.11
- HTTP Client: Axios 1.11.0
- State Management: Zustand 5.0.7
- File Handling: React Dropzone 14.3.8

### Key Features
- Image format conversion (JPG, PNG, WebP, AVIF, HEIC, GIF, ICO)
- Quality control (1-100%)
- Image sharpening with unsharp mask (0-200%)
- Resize functionality with max width control
- Free tier: 20 conversions/day per user/IP
- Paid tier: 1000 conversions per subscription ($1.98/month)
- Auto-renewal toggle for subscriptions
- Batch conversion support
- GIF frame extraction to multiple formats (max 100 frames, exported as ZIP)
- Anonymous user support (IP-based tracking)

### System Limits
- **File Upload:** 20MB max per file
- **GIF Frames:** 100 max
- **Output Formats:** 4 max per GIF conversion
- **Request Timeout:** 30 seconds
- **Image Dimensions:** 8000px max width/height
- **Database Connections:** 10 (HikariCP pool)
- **Tomcat Threads:** 200 max
- **Concurrent Connections:** 8192 max

### Rate Limits
- **Authenticated Users:** 300 requests/minute (general API)
- **Anonymous Users:** 60 requests/minute
- **Conversion Endpoints:** 10 conversions/minute per user/IP
- **Webhook Endpoints:** 10 requests/minute

### API Endpoints

**Public:**
- `GET /health` - Health check
- `GET /actuator/health` - Detailed health
- `GET /actuator/info` - App info
- `POST /stripe/webhook` - Stripe webhooks (signed)
- `GET /api/convert/formats` - Supported formats list
- `POST /api/convert` - Single image conversion
- `POST /api/convert/gif` - GIF frame extraction → ZIP

**Authenticated:**
- `GET /api/user/me` - Get current user info
- `POST /api/user/toggle-auto-renewal` - Toggle subscription renewal
- `POST /api/billing/checkout` - Create Stripe checkout session

**Debug (dev only):**
- `GET /api/debug/users` - List all users

---

## 1. Functional Testing

### 1.1 User Authentication & Authorization

#### Test Cases:
- **AUTH-001**: Anonymous user can access homepage and convert page
- **AUTH-002**: Anonymous user cannot access account or billing pages
- **AUTH-003**: Google OAuth sign-in flow completes successfully
- **AUTH-004**: User session persists across page navigation
- **AUTH-005**: Logged-in user can access all protected pages
- **AUTH-006**: Sign-out clears session and redirects appropriately
- **AUTH-007**: Invalid/expired tokens are rejected by backend
- **AUTH-008**: Backend validates Google ID token audience correctly

**Priority:** Critical
**Test Data:** Valid Google account, invalid tokens, expired sessions

---

### 1.2 Image Conversion Core Functionality

#### Test Cases:
- **CONV-001**: Convert single image: JPG → PNG
- **CONV-002**: Convert single image: PNG → JPG
- **CONV-003**: Convert single image: JPG → WebP
- **CONV-004**: Convert single image: PNG → AVIF
- **CONV-005**: Convert single image: HEIC → JPG
- **CONV-006**: Convert animated GIF → PNG/JPG (ZIP output)
- **CONV-007**: Convert ICO format successfully
- **CONV-008**: Batch conversion (multiple files, same format)
- **CONV-009**: Quality slider affects output file size (test at 20%, 50%, 85%, 100%)
- **CONV-010**: Sharpness slider produces visible sharpening (test at 0%, 50%, 100%, 200%)
- **CONV-011**: Resize with max width constraint works correctly
- **CONV-012**: Disable resize preserves original dimensions
- **CONV-013**: Upload progress indicator displays correctly
- **CONV-014**: Conversion progress updates during processing
- **CONV-015**: Download button appears after successful conversion
- **CONV-016**: Downloaded file has correct format and quality
- **CONV-017**: "Clear All" button removes all uploaded files

**Priority:** Critical
**Test Data:** Sample images in all supported formats, various file sizes, animated GIFs

---

### 1.3 File Upload Validation

#### Test Cases:
- **UPLOAD-001**: Accept valid image formats (JPG, PNG, WebP, AVIF, HEIC, GIF, ICO)
- **UPLOAD-002**: Reject files exceeding 20MB size limit
- **UPLOAD-003**: Reject unsupported file formats (PDF, SVG, TIFF, BMP)
- **UPLOAD-004**: Reject files with invalid extensions (e.g., image.jpg_small, image:thumb)
- **UPLOAD-005**: Reject files with dimension exceeding 8000px
- **UPLOAD-006**: Display appropriate error messages for each rejection reason
- **UPLOAD-007**: Handle drag-and-drop upload
- **UPLOAD-008**: Handle click-to-upload
- **UPLOAD-009**: Handle multiple file selection
- **UPLOAD-010**: Validate MIME type matches file content (magic number validation)
- **UPLOAD-011**: Reject files with suspicious headers
- **UPLOAD-012**: Malformed filenames are handled safely

**Priority:** High
**Test Data:** Files of various sizes, malformed files, renamed executables, files with incorrect extensions

---

### 1.4 Credit System & Billing

#### Test Cases:
- **CREDIT-001**: Anonymous users have 20 free conversions per day
- **CREDIT-002**: Free conversion counter decrements after each conversion
- **CREDIT-003**: Free conversions reset at midnight (daily)
- **CREDIT-004**: Authenticated users start with 20 free conversions per day
- **CREDIT-005**: Purchasing subscription adds 1000 conversions
- **CREDIT-006**: Paid conversions are deducted before free conversions
- **CREDIT-007**: Conversion blocked when credits reach zero
- **CREDIT-008**: Credit display updates in real-time on account page
- **CREDIT-009**: Auto-renewal toggle persists user preference
- **CREDIT-010**: Auto-renewal automatically purchases 1000 credits monthly
- **CREDIT-011**: Disabling auto-renewal stops automatic purchases
- **CREDIT-012**: Multiple subscriptions in one month stack credits (2 subscriptions = 2000 credits)

**Priority:** Critical
**Test Data:** Test Stripe cards, various subscription scenarios

---

### 1.5 Stripe Payment Integration

#### Test Cases:
- **PAY-001**: Checkout session created with correct amount ($1.98)
- **PAY-002**: Stripe Checkout page loads successfully
- **PAY-003**: Successful payment redirects to success URL
- **PAY-004**: Cancelled payment redirects to cancel URL
- **PAY-005**: Webhook receives payment success event
- **PAY-006**: Credits added to user account after webhook confirmation
- **PAY-007**: Failed payment does not add credits
- **PAY-008**: Open redirect validation rejects external domains
- **PAY-009**: Open redirect validation accepts localhost (development)
- **PAY-010**: Open redirect validation accepts production domain (www.change-my.com)
- **PAY-011**: Open redirect validation rejects javascript: protocol
- **PAY-012**: Open redirect validation rejects data: protocol
- **PAY-013**: Subscription status updates correctly (active, canceled, past_due)

**Priority:** Critical
**Test Data:** Stripe test cards (4242424242424242, card_declined, etc.)

---

### 1.6 User Account Management

#### Test Cases:
- **ACC-001**: Account page displays current credit balance
- **ACC-002**: Account page shows subscription status (active/inactive)
- **ACC-003**: Account page shows auto-renewal status
- **ACC-004**: Account page shows conversions remaining
- **ACC-005**: Free conversions counter displays for non-subscribers
- **ACC-006**: Auto-renewal toggle updates immediately
- **ACC-007**: User email displayed correctly (only visible to user, not in logs)
- **ACC-008**: Navigation between pages maintains session state

**Priority:** High
**Test Data:** Users with various subscription states

---

### 1.7 UI/UX Functionality

#### Test Cases:
- **UI-001**: Homepage displays correctly with clear call-to-action
- **UI-002**: Convert page drag-and-drop zone is visible and functional
- **UI-003**: Format selection dropdown shows all supported formats
- **UI-004**: Quality slider (1-100) adjusts smoothly
- **UI-005**: Sharpness slider (0-200) adjusts smoothly
- **UI-006**: Resize toggle enables/disables max width input
- **UI-007**: Max width input accepts valid numeric values
- **UI-008**: File preview displays after upload
- **UI-009**: Error modals display with appropriate messages
- **UI-010**: Success indicators appear after conversion
- **UI-011**: Download button triggers file download
- **UI-012**: Navigation menu works on all pages
- **UI-013**: Mobile responsive design displays correctly
- **UI-014**: Escape key closes modals
- **UI-015**: GIF format conversion shows format selection (PNG/JPG)
- **UI-016**: "Clear All" button appears when files are uploaded

**Priority:** High
**Test Data:** Various screen sizes, different browsers

---

## 2. Security Testing

### 2.1 Authentication Security

#### Test Cases:
- **SEC-AUTH-001**: Invalid Google OAuth tokens are rejected
- **SEC-AUTH-002**: Expired tokens trigger re-authentication
- **SEC-AUTH-003**: Backend validates JWT signature
- **SEC-AUTH-004**: Backend validates JWT audience matches GOOGLE_CLIENT_ID
- **SEC-AUTH-005**: Unauthenticated requests to protected endpoints return 401/403
- **SEC-AUTH-006**: User ID (not email) is logged for GDPR compliance

**Priority:** Critical
**Methodology:** Manual + Automated (OWASP ZAP)

---

### 2.2 Input Validation & Injection Prevention

#### Test Cases:
- **SEC-INJ-001**: SQL injection attempts in API parameters are blocked
- **SEC-INJ-002**: XSS attempts in filename are sanitized
- **SEC-INJ-003**: Path traversal attempts in filename are blocked
- **SEC-INJ-004**: Command injection attempts in conversion parameters are blocked
- **SEC-INJ-005**: MIME type spoofing is detected (magic number validation)
- **SEC-INJ-006**: Malicious file headers are detected and rejected
- **SEC-INJ-007**: Large payload attacks are blocked (20MB limit enforced)

**Priority:** Critical
**Test Data:** OWASP Top 10 payloads, malicious files

---

### 2.3 CORS & CSRF Protection

#### Test Cases:
- **SEC-CORS-001**: Only allowed origins can make API requests
- **SEC-CORS-002**: Requests from unauthorized origins are blocked
- **SEC-CORS-003**: Credentials are not exposed via CORS
- **SEC-CORS-004**: Preflight OPTIONS requests are handled correctly

**Priority:** High
**Methodology:** Manual testing with different origins

---

### 2.4 Rate Limiting

#### Test Cases:
- **SEC-RATE-001**: Anonymous users limited to 60 requests/minute
- **SEC-RATE-002**: Authenticated users limited to 300 requests/minute
- **SEC-RATE-003**: Conversion endpoint limited to 10 conversions/minute per user
- **SEC-RATE-004**: Rate limit headers are returned (X-RateLimit-Remaining, etc.)
- **SEC-RATE-005**: Rate limit exceeded returns 429 status
- **SEC-RATE-006**: Rate limit resets after time window expires
- **SEC-RATE-007**: Security logs record rate limit violations

**Priority:** High
**Methodology:** Automated script to send rapid requests

---

### 2.5 Open Redirect Prevention

#### Test Cases:
- **SEC-REDIR-001**: External domain redirects are blocked (evil.com)
- **SEC-REDIR-002**: Localhost redirects are allowed (development)
- **SEC-REDIR-003**: Production domain redirects are allowed (www.change-my.com)
- **SEC-REDIR-004**: JavaScript protocol redirects are blocked
- **SEC-REDIR-005**: Data protocol redirects are blocked
- **SEC-REDIR-006**: Malformed URLs are rejected
- **SEC-REDIR-007**: Empty URLs are rejected

**Priority:** Critical
**Test Data:** Various malicious redirect URLs

---

### 2.6 Secrets & Environment Variables

#### Test Cases:
- **SEC-ENV-001**: No secrets committed to Git repository
- **SEC-ENV-002**: Environment variables loaded correctly in production
- **SEC-ENV-003**: Database credentials are not exposed in error messages
- **SEC-ENV-004**: Stripe keys are not exposed in frontend code
- **SEC-ENV-005**: Debug endpoints disabled in production
- **SEC-ENV-006**: Stack traces disabled in production error responses

**Priority:** Critical
**Methodology:** Code review + environment inspection

---

### 2.7 Security Headers

#### Test Cases:
- **SEC-HEAD-001**: Content-Security-Policy header is present
- **SEC-HEAD-002**: X-Frame-Options: DENY header is present
- **SEC-HEAD-003**: X-Content-Type-Options: nosniff header is present
- **SEC-HEAD-004**: Strict-Transport-Security header is present
- **SEC-HEAD-005**: Referrer-Policy header is configured
- **SEC-HEAD-006**: Permissions-Policy header restricts sensitive features

**Priority:** High
**Methodology:** Browser DevTools + Security header analyzer

---

## 3. Performance Testing

### 3.1 Load Testing

#### Test Cases:
- **PERF-LOAD-001**: 100 concurrent users can upload and convert images
- **PERF-LOAD-002**: 500 concurrent users stress test
- **PERF-LOAD-003**: Response time under load remains < 3 seconds for conversion
- **PERF-LOAD-004**: Database connection pool handles concurrent queries
- **PERF-LOAD-005**: Memory usage remains stable under sustained load

**Priority:** Medium
**Tools:** JMeter, k6, Artillery
**Metrics:** Response time, throughput, error rate, resource utilization

---

### 3.2 File Size Performance

#### Test Cases:
- **PERF-FILE-001**: 100KB file converts in < 1 second
- **PERF-FILE-002**: 1MB file converts in < 2 seconds
- **PERF-FILE-003**: 5MB file converts in < 5 seconds
- **PERF-FILE-004**: 20MB file (max size) converts in < 10 seconds
- **PERF-FILE-005**: GIF animation conversion completes within timeout

**Priority:** Medium
**Test Data:** Files of various sizes

---

### 3.3 Frontend Performance

#### Test Cases:
- **PERF-FE-001**: First Contentful Paint (FCP) < 1.5 seconds
- **PERF-FE-002**: Largest Contentful Paint (LCP) < 2.5 seconds
- **PERF-FE-003**: Time to Interactive (TTI) < 3.0 seconds
- **PERF-FE-004**: Cumulative Layout Shift (CLS) < 0.1
- **PERF-FE-005**: First Input Delay (FID) < 100ms

**Priority:** Medium
**Tools:** Google Lighthouse, WebPageTest

---

## 4. Compatibility Testing

### 4.1 Browser Compatibility

#### Test Browsers:
- **COMPAT-BR-001**: Chrome (latest, latest-1)
- **COMPAT-BR-002**: Firefox (latest, latest-1)
- **COMPAT-BR-003**: Safari (latest, latest-1)
- **COMPAT-BR-004**: Edge (latest)
- **COMPAT-BR-005**: Opera (latest)
- **COMPAT-BR-006**: Mobile Safari (iOS 15+)
- **COMPAT-BR-007**: Mobile Chrome (Android 10+)

**Priority:** High
**Test Scope:** Core functionality (upload, convert, download)

---

### 4.2 Device Compatibility

#### Test Devices:
- **COMPAT-DEV-001**: Desktop (1920x1080, 1366x768)
- **COMPAT-DEV-002**: Tablet (768x1024, portrait/landscape)
- **COMPAT-DEV-003**: Mobile (375x667, 414x896)
- **COMPAT-DEV-004**: Large desktop (2560x1440, 4K)

**Priority:** High
**Test Scope:** Responsive design, touch interactions

---

### 4.3 Image Format Compatibility

#### Test Cases:
- **COMPAT-IMG-001**: JPG/JPEG images from various sources (cameras, phones, web)
- **COMPAT-IMG-002**: PNG with transparency
- **COMPAT-IMG-003**: WebP images (lossy and lossless)
- **COMPAT-IMG-004**: AVIF images
- **COMPAT-IMG-005**: HEIC images from iPhone
- **COMPAT-IMG-006**: Animated GIF
- **COMPAT-IMG-007**: ICO files (16x16, 32x32, 64x64)

**Priority:** High
**Test Data:** Sample images from real-world sources

---

## 5. Usability Testing

### 5.1 User Workflow Testing

#### Test Scenarios:
- **USAB-001**: New anonymous user converts first image
- **USAB-002**: User exceeds free daily limit and is prompted to subscribe
- **USAB-003**: User signs in with Google OAuth
- **USAB-004**: User purchases subscription via Stripe
- **USAB-005**: User converts images using paid credits
- **USAB-006**: User toggles auto-renewal on/off
- **USAB-007**: User navigates between pages without losing context
- **USAB-008**: User understands error messages and can resolve issues
- **USAB-009**: User finds download button after conversion
- **USAB-010**: User clears uploaded files and starts fresh

**Priority:** Medium
**Methodology:** User testing sessions with 5-10 participants

---

### 5.2 Accessibility Testing (WCAG 2.1 AA)

#### Test Cases:
- **A11Y-001**: Keyboard navigation works for all interactive elements
- **A11Y-002**: Screen reader announces all important content
- **A11Y-003**: Color contrast meets WCAG AA standards
- **A11Y-004**: All images have alt text
- **A11Y-005**: Form inputs have proper labels
- **A11Y-006**: Focus indicators are visible
- **A11Y-007**: ARIA labels present for icon-only buttons
- **A11Y-008**: Error messages are announced to screen readers

**Priority:** Medium
**Tools:** WAVE, axe DevTools, NVDA/JAWS screen readers

---

## 6. Integration Testing

### 6.1 Frontend-Backend Integration

#### Test Cases:
- **INT-001**: Frontend successfully calls all backend API endpoints
- **INT-002**: Error responses from backend are handled gracefully
- **INT-003**: Authentication token is passed correctly in headers
- **INT-004**: File upload multipart/form-data is processed correctly
- **INT-005**: Credit balance updates after conversion
- **INT-006**: Real-time progress updates during conversion

**Priority:** High
**Methodology:** End-to-end tests with Playwright/Cypress

---

### 6.2 Database Integration

#### Test Cases:
- **INT-DB-001**: User creation on first Google sign-in
- **INT-DB-002**: Credit updates persist correctly
- **INT-DB-003**: Subscription status updates persist
- **INT-DB-004**: Daily free conversion reset works correctly
- **INT-DB-005**: Concurrent credit updates don't cause race conditions
- **INT-DB-006**: Database connection pool handles failures gracefully

**Priority:** High
**Methodology:** Backend unit tests + integration tests

---

### 6.3 Stripe Webhook Integration

#### Test Cases:
- **INT-STRIPE-001**: checkout.session.completed webhook is received
- **INT-STRIPE-002**: Credits added after successful payment
- **INT-STRIPE-003**: Webhook signature validation works correctly
- **INT-STRIPE-004**: Failed payments do not add credits
- **INT-STRIPE-005**: Duplicate webhook events are handled (idempotency)

**Priority:** Critical
**Methodology:** Stripe CLI webhook testing, manual webhook replay

---

### 6.4 ImageMagick Integration

#### Test Cases:
- **INT-IM-001**: ImageMagick 6.9.12+ is installed and accessible
- **INT-IM-002**: Basic image conversion commands execute successfully
- **INT-IM-003**: Quality parameter (-quality) works correctly
- **INT-IM-004**: Sharpness parameter (-unsharp) works correctly
- **INT-IM-005**: Resize parameter (-resize) works correctly
- **INT-IM-006**: GIF frame extraction (-coalesce) works for animated GIFs
- **INT-IM-007**: GIF with 100 frames extracts successfully
- **INT-IM-008**: GIF with >100 frames is rejected
- **INT-IM-009**: Conversion errors are caught and logged
- **INT-IM-010**: ImageMagick process doesn't hang or timeout (30s limit)
- **INT-IM-011**: Temporary files are cleaned up after conversion
- **INT-IM-012**: Temporary files are cleaned up after errors
- **INT-IM-013**: Multiple format outputs work for GIF (PNG, JPG, WebP, AVIF)
- **INT-IM-014**: ZIP file creation works for GIF outputs
- **INT-IM-015**: Memory usage stays within limits during conversion

**Priority:** High
**Methodology:** Backend integration tests

---

## 7. Regression Testing

### 7.1 Critical Path Regression

Run after every deployment or major code change:

1. **User Authentication Flow**: Sign in, access protected pages, sign out
2. **Core Conversion**: Upload image, convert, download
3. **Credit System**: Free conversion, paid conversion, credit deduction
4. **Payment Flow**: Checkout, payment, credit addition
5. **Security**: Rate limiting, file validation, CORS

**Priority:** Critical
**Frequency:** Every deployment
**Automation:** High priority for CI/CD pipeline

---

### 7.2 Full Regression Suite

Run weekly or before major releases:

- All functional test cases
- All security test cases (except penetration testing)
- Core performance tests
- Browser compatibility checks

**Priority:** High
**Frequency:** Weekly + before major releases

---

## 8. Database Testing

### 8.1 Database Schema

#### Tables to Test:
1. **app_user** - User data and subscription status
   - Columns: id, email, free_used_today, last_free_reset, paid_credits, last_paid_reset, stripe_subscription_id, subscription_status, auto_renewal
   - Indexes: idx_user_email, idx_app_user_stripe_sub

2. **ip_conversion_tracker** - Anonymous user tracking
   - Columns: id, ip_address, conversions_used_today, last_reset
   - Index: idx_ip_tracker_ip

3. **webhook_event** - Webhook idempotency
   - Columns: id, stripe_event_id, event_type, processed_at
   - Index: idx_webhook_event_stripe_id

---

### 8.2 Data Integrity

#### Test Cases:
- **DB-001**: User data persists correctly across sessions
- **DB-002**: Credit transactions are atomic (no partial updates)
- **DB-003**: Concurrent updates don't corrupt data
- **DB-004**: Database constraints prevent invalid data (unique email, unique stripe_event_id)
- **DB-005**: Foreign key relationships maintained correctly
- **DB-006**: free_used_today resets daily at midnight
- **DB-007**: paid_credits decrements correctly after conversion
- **DB-008**: IP address stored correctly (IPv4 and IPv6 support)
- **DB-009**: Webhook events stored with correct stripe_event_id
- **DB-010**: Subscription status updates correctly (active, canceled, past_due)
- **DB-011**: auto_renewal boolean persists correctly

**Priority:** High
**Methodology:** Direct database queries + integration tests

---

### 8.3 Database Migration (Flyway)

#### Test Cases:
- **DB-MIG-001**: Flyway migrations run successfully on fresh database
- **DB-MIG-002**: Flyway migrations are idempotent (can run multiple times safely)
- **DB-MIG-003**: V1__initial_schema.sql creates all tables correctly
- **DB-MIG-004**: All indexes are created (idx_user_email, idx_ip_tracker_ip, etc.)
- **DB-MIG-005**: Data preserved during schema changes
- **DB-MIG-006**: `hibernate.ddl-auto: validate` prevents accidental schema changes
- **DB-MIG-007**: Flyway metadata table (flyway_schema_history) tracks migrations

**Priority:** High
**Methodology:** Test database setup with various scenarios

---

### 8.4 Connection Pooling (HikariCP)

#### Test Cases:
- **DB-POOL-001**: Connection pool creates up to 10 connections
- **DB-POOL-002**: Minimum 2 idle connections maintained
- **DB-POOL-003**: Connection timeout (30s) handled gracefully
- **DB-POOL-004**: Idle connections closed after 10 minutes
- **DB-POOL-005**: Connection max lifetime (30 min) enforced
- **DB-POOL-006**: Connection test query (SELECT 1) validates connections
- **DB-POOL-007**: SSL connections enforced in production

**Priority:** Medium
**Methodology:** Load testing + monitoring

---

## 9. Monitoring & Logging Testing

### 9.1 Application Logging

#### Test Cases:
- **LOG-001**: User authentication events are logged (with user ID, not email)
- **LOG-002**: Conversion attempts are logged
- **LOG-003**: Rate limit violations are logged
- **LOG-004**: Security events are logged
- **LOG-005**: Error stack traces do NOT appear in production logs
- **LOG-006**: Sensitive data (emails, tokens) is NOT logged

**Priority:** High
**Methodology:** Log file inspection

---

### 9.2 Health Monitoring

#### Test Cases:
- **MON-001**: /actuator/health endpoint returns 200 when healthy
- **MON-002**: Health check detects database connectivity issues
- **MON-003**: Memory usage is tracked and reported
- **MON-004**: CPU usage is tracked and reported
- **MON-005**: Disk space usage is monitored

**Priority:** Medium
**Tools:** Spring Boot Actuator, monitoring platform

---

## 10. Disaster Recovery & Backup Testing

### 10.1 Backup Testing

#### Test Cases:
- **DR-001**: Database backups are created automatically
- **DR-002**: Database backup can be restored successfully
- **DR-003**: Restored database contains all user data
- **DR-004**: Point-in-time recovery works correctly
- **DR-005**: Backup retention policy is enforced

**Priority:** Medium
**Frequency:** Quarterly

---

### 10.2 Failure Scenarios

#### Test Cases:
- **DR-FAIL-001**: Application handles database connection failure gracefully
- **DR-FAIL-002**: Application handles Stripe API failure gracefully
- **DR-FAIL-003**: Application handles Google OAuth failure gracefully
- **DR-FAIL-004**: Application handles ImageMagick crash gracefully
- **DR-FAIL-005**: Application recovers from out-of-memory errors

**Priority:** Medium
**Methodology:** Chaos engineering, fault injection

---

## 11. Test Automation Strategy

### 11.1 Unit Tests (Backend - JUnit)

**Coverage Target:** 80%+

- Service layer logic (UserService, BillingService)
- Credit calculation logic
- File validation logic
- Rate limiting logic
- Security validators

**Tools:** JUnit 5, Mockito, Spring Boot Test

---

### 11.2 Integration Tests (Backend - Spring Boot Test)

**Coverage Target:** Key endpoints

- REST API endpoints
- Database interactions
- Stripe webhook handling
- ImageMagick integration

**Tools:** Spring Boot Test, TestRestTemplate, H2 in-memory database

---

### 11.3 End-to-End Tests (Frontend + Backend)

**Coverage Target:** Critical user flows

- User sign-in flow
- Image upload and conversion
- Payment and credit addition
- Account management

**Tools:** Playwright, Cypress

---

### 11.4 CI/CD Pipeline

**On Every Commit:**
- Run unit tests (backend)
- Run linting (frontend + backend)
- Run build verification

**On Pull Request:**
- Run integration tests
- Run security scans (OWASP Dependency Check)
- Run code quality checks (SonarQube)

**On Deployment (Staging):**
- Run full regression suite
- Run smoke tests

**On Deployment (Production):**
- Run smoke tests
- Monitor error rates for 15 minutes

**Tools:** GitHub Actions, Jenkins, CircleCI

---

## 12. Test Environment Setup

### 12.1 Test Environments

| Environment | Purpose | Data | URL |
|-------------|---------|------|-----|
| **Local** | Development testing | Synthetic test data | localhost:3000 |
| **Staging** | Pre-production testing | Anonymized production-like data | staging.change-my.com |
| **Production** | Live monitoring | Real user data | www.change-my.com |

---

### 12.2 Test Data Requirements

- **User Accounts**: 10 test Google accounts with various states (free, subscribed, expired)
- **Sample Images**: Collection of 50+ images in all supported formats
- **Stripe Test Cards**: Collection of test cards for various scenarios
- **Invalid Files**: Collection of malicious/malformed files for security testing

---

## 13. Defect Management

### 13.1 Severity Levels

| Severity | Definition | Example | Response Time |
|----------|------------|---------|---------------|
| **Critical** | System down, data loss, security breach | Payment processing fails, database corruption | Immediate (< 1 hour) |
| **High** | Major functionality broken | Image conversion fails for all users | < 4 hours |
| **Medium** | Feature degraded, workaround exists | Specific format conversion fails | < 24 hours |
| **Low** | Minor UI issue, cosmetic bug | Button alignment off | Next sprint |

---

### 13.2 Defect Tracking

**Tool:** GitHub Issues
**Fields:**
- Title
- Description
- Steps to reproduce
- Expected vs. actual behavior
- Severity
- Environment (browser, OS, etc.)
- Screenshots/logs
- Test case reference

---

## 14. Testing Schedule

### Daily
- Smoke tests (production health checks)
- Monitor error logs

### Weekly
- Full regression suite
- Security scans
- Performance monitoring review

### Monthly
- Accessibility audit
- Browser compatibility checks
- Dependency updates and security patches

### Quarterly
- Penetration testing
- Load testing
- Disaster recovery drills
- User acceptance testing (UAT)

### Annually
- Full security audit
- Performance optimization review
- Test plan review and update

---

## 15. Sign-off Criteria

### Definition of Done (DoD)

A feature is considered "done" when:

- [ ] All functional test cases pass
- [ ] All security test cases pass
- [ ] Unit test coverage > 80%
- [ ] Integration tests pass
- [ ] No critical or high severity defects
- [ ] Code reviewed and approved
- [ ] Documentation updated
- [ ] Accessibility requirements met (WCAG 2.1 AA)
- [ ] Performance benchmarks met
- [ ] Security scan shows no vulnerabilities
- [ ] User acceptance testing completed
- [ ] Deployed to staging and tested
- [ ] Product owner sign-off

---

## 16. Roles & Responsibilities

| Role | Responsibilities |
|------|------------------|
| **QA Engineer** | Execute test plans, report defects, maintain automation |
| **Developer** | Fix defects, write unit tests, support integration testing |
| **DevOps Engineer** | Maintain test environments, configure CI/CD pipelines |
| **Product Owner** | Define acceptance criteria, perform UAT, sign-off releases |
| **Security Engineer** | Perform security testing, review vulnerabilities |

---

## 17. Tools & Resources

### Testing Tools
- **Unit Testing:** JUnit 5, Mockito
- **Integration Testing:** Spring Boot Test, TestRestTemplate
- **E2E Testing:** Playwright, Cypress
- **Performance Testing:** JMeter, k6, Artillery
- **Security Testing:** OWASP ZAP, Burp Suite, npm audit
- **Accessibility Testing:** WAVE, axe DevTools
- **Browser Testing:** BrowserStack, Sauce Labs
- **CI/CD:** GitHub Actions, Jenkins
- **Monitoring:** Spring Boot Actuator, Prometheus, Grafana

### Documentation
- Test case repository: GitHub Wiki
- Test results: Test management tool (TestRail, Zephyr)
- Defect tracking: GitHub Issues
- Test metrics dashboard: Grafana

---

## 18. Success Metrics

### Test Effectiveness
- **Defect Detection Rate**: # of defects found in testing / total defects
- **Defect Leakage**: # of defects found in production / total defects
- **Test Coverage**: % of code covered by tests
- **Test Pass Rate**: % of tests passing

### Quality Metrics
- **Mean Time to Detect (MTTD)**: Average time to detect a defect
- **Mean Time to Repair (MTTR)**: Average time to fix a defect
- **Defect Density**: # of defects per 1000 lines of code
- **Production Incidents**: # of incidents per month

### Performance Metrics
- **Response Time**: Average API response time < 500ms
- **Throughput**: Requests per second > 100
- **Error Rate**: < 1% of requests fail
- **Uptime**: > 99.9%

---

## Appendix A: GDPR Compliance Testing

### Test Cases:
- **GDPR-001**: User email is NOT logged (only user ID)
- **GDPR-002**: User data can be exported on request
- **GDPR-003**: User data can be deleted on request
- **GDPR-004**: Privacy policy is accessible and clear
- **GDPR-005**: User consent is obtained before data processing
- **GDPR-006**: Third-party integrations (Google, Stripe) are GDPR compliant

**Priority:** Critical (EU users)

---

## Appendix B: Stripe Test Cards

| Card Number | Scenario |
|-------------|----------|
| 4242424242424242 | Successful payment |
| 4000000000000002 | Card declined |
| 4000000000009995 | Insufficient funds |
| 4000000000000069 | Charge fails |
| 4000002500003155 | 3D Secure required |

---

## Appendix C: Sample Test Data Files

Create a test data repository with:
- **valid-images/**: JPG, PNG, WebP, AVIF, HEIC, GIF, ICO samples
- **invalid-files/**: Malformed images, executables renamed as images
- **edge-cases/**: 0-byte files, max-size files, unusual dimensions
- **security-test-files/**: Files with XSS payloads in metadata, SQL injection in filenames
- **gif-samples/**: GIFs with 1 frame, 50 frames, 100 frames, 101 frames (should fail)

---

## Appendix D: Configuration Testing

### Spring Boot Configuration Tests

#### Test Cases:
- **CONFIG-001**: Server port configured correctly (default 8080)
- **CONFIG-002**: Max upload size enforced (20MB)
- **CONFIG-003**: Tomcat max connections (8192) configured
- **CONFIG-004**: Tomcat thread pool (200 max) configured
- **CONFIG-005**: Error details not exposed (include-stacktrace: never)
- **CONFIG-006**: Database URL loaded from environment variable
- **CONFIG-007**: HikariCP pool settings correct (max 10, min 2)
- **CONFIG-008**: SSL required for production database
- **CONFIG-009**: Hibernate ddl-auto set to 'validate' (not 'update')
- **CONFIG-010**: Flyway migrations enabled
- **CONFIG-011**: Stripe keys loaded from environment
- **CONFIG-012**: Google OAuth client ID loaded
- **CONFIG-013**: CORS allowed origins configured correctly
- **CONFIG-014**: Actuator endpoints exposed correctly (health, info only in prod)
- **CONFIG-015**: Logging configuration correct (30-day retention, 50MB max)
- **CONFIG-016**: Production profile activates correctly (SPRING_PROFILES_ACTIVE=prod)

**Priority:** Medium
**Methodology:** Configuration validation tests, environment inspection

---

## Appendix E: Anonymous User Testing

### IP-Based Tracking Tests

#### Test Cases:
- **ANON-001**: Anonymous user (no authentication) can convert images
- **ANON-002**: Anonymous user limited to 20 conversions per day
- **ANON-003**: IP address tracked in ip_conversion_tracker table
- **ANON-004**: IPv4 addresses stored correctly
- **ANON-005**: IPv6 addresses stored correctly (45 char max)
- **ANON-006**: Conversions reset daily at midnight
- **ANON-007**: Different IP addresses tracked separately
- **ANON-008**: Same IP address across sessions uses same counter
- **ANON-009**: Anonymous user exceeding limit receives appropriate error
- **ANON-010**: Anonymous user prompted to sign in after limit reached

**Priority:** High
**Test Data:** Multiple IP addresses, VPN testing

---

## Appendix F: Credit System Flow Testing

### End-to-End Credit Scenarios

#### Scenario 1: Free User Daily Cycle
1. New user signs in via Google
2. User has 20 free conversions
3. User converts 10 images (10 remaining)
4. User converts 10 more images (0 remaining)
5. User attempts 21st conversion → blocked
6. Wait until midnight (or manipulate last_free_reset date)
7. Counter resets to 20 conversions

**Expected:** Credits reset daily, blocking works at limit

---

#### Scenario 2: Subscription Purchase
1. User signs in with 0 free conversions remaining
2. User clicks "Upgrade" → redirected to Stripe
3. User completes payment with test card 4242424242424242
4. Stripe webhook fires → backend processes checkout.session.completed
5. User paid_credits set to 1000
6. User converts 50 images (950 remaining)
7. Free conversions still reset daily (unused while paid credits exist)

**Expected:** 1000 credits added immediately, paid credits used first

---

#### Scenario 3: Auto-Renewal
1. User has active subscription with auto_renewal = true
2. Stripe subscription renews monthly (simulated via webhook)
3. Backend receives invoice.paid webhook
4. User paid_credits += 1000
5. User now has previous_remaining + 1000 credits

**Expected:** Credits stack with auto-renewal

---

#### Scenario 4: Multiple Subscriptions in One Month
1. User purchases subscription (1000 credits)
2. User uses 500 credits
3. User purchases another subscription same month
4. User receives additional 1000 credits (total: 1500)

**Expected:** Multiple purchases stack credits

---

#### Scenario 5: Anonymous to Authenticated Conversion
1. Anonymous user (IP: 192.168.1.1) uses 10 conversions
2. Anonymous user signs in via Google
3. User now has 20 free conversions (separate from IP counter)
4. IP counter remains at 10/20 for other anonymous users on same IP

**Expected:** IP tracking and user tracking are separate

---

## Appendix G: Performance Benchmarks

### Expected Performance Targets

| Operation | Target | Acceptable | Unacceptable |
|-----------|--------|------------|--------------|
| **File Upload (1MB)** | < 500ms | < 1s | > 2s |
| **Convert JPG→PNG (1MB)** | < 1s | < 2s | > 3s |
| **Convert JPG→WebP (1MB)** | < 1s | < 2s | > 3s |
| **Convert PNG→AVIF (1MB)** | < 2s | < 3s | > 5s |
| **GIF extraction (50 frames)** | < 5s | < 10s | > 15s |
| **ZIP creation (100 files)** | < 3s | < 5s | > 10s |
| **Database query (user lookup)** | < 10ms | < 50ms | > 100ms |
| **Stripe checkout session** | < 500ms | < 1s | > 2s |
| **Health check endpoint** | < 50ms | < 100ms | > 200ms |
| **Page load (FCP)** | < 1s | < 1.5s | > 2s |
| **Page load (LCP)** | < 2s | < 2.5s | > 4s |

---

## Appendix H: Test Automation Examples

### Example Unit Test (Backend)

```java
@Test
public void testCreditDeduction_WithPaidCredits() {
    User user = new User();
    user.setPaidCredits(100);
    user.setFreeUsedToday(0);

    userService.deductCredit(user);

    assertEquals(99, user.getPaidCredits());
    assertEquals(0, user.getFreeUsedToday());
}

@Test
public void testCreditDeduction_WithoutPaidCredits() {
    User user = new User();
    user.setPaidCredits(0);
    user.setFreeUsedToday(10);

    userService.deductCredit(user);

    assertEquals(0, user.getPaidCredits());
    assertEquals(11, user.getFreeUsedToday());
}
```

### Example Integration Test (Backend)

```java
@SpringBootTest
@AutoConfigureMockMvc
public class ConvertControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testImageConversion_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg",
            Files.readAllBytes(Paths.get("test-data/test.jpg"))
        );

        mockMvc.perform(multipart("/api/convert")
                .file(file)
                .param("to", "png")
                .param("quality", "85"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));
    }
}
```

### Example E2E Test (Playwright)

```typescript
test('user can upload and convert image', async ({ page }) => {
  await page.goto('http://localhost:3000/convert');

  // Upload file
  await page.setInputFiles('input[type="file"]', 'test-data/sample.jpg');

  // Select format
  await page.selectOption('select[name="format"]', 'webp');

  // Click convert
  await page.click('button:has-text("Convert")');

  // Wait for conversion
  await page.waitForSelector('button:has-text("Download")', { timeout: 5000 });

  // Verify download button appears
  const downloadBtn = await page.$('button:has-text("Download")');
  expect(downloadBtn).toBeTruthy();
});
```

---

**Document Version:** 1.1
**Last Updated:** 2025-10-19
**Updated By:** QA Team (with COMPLETE_APP_CONFIGURATION.md integration)
**Author:** QA Team
**Status:** Ready for Review
