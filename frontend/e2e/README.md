# E2E Tests with Playwright

## Overview

This directory contains end-to-end (E2E) tests for the change-my.com frontend application using Playwright.

**Total Test Count**: 60+ E2E tests across 6 test suites

## Test Suites

### 1. `homepage.spec.ts` (10 tests)
- Homepage loading and navigation
- Responsive design (mobile, tablet, desktop)
- SEO metadata verification
- Performance budget testing

### 2. `convert-anonymous.spec.ts` (18 tests)
- Anonymous user conversion flow
- File upload and dropzone functionality
- Format selection and quality adjustments
- File validation (size limits, file types)
- State persistence security verification
- Accessibility testing

### 3. `pricing.spec.ts` (11 tests)
- Pricing page display
- Free tier and paid plan information
- Subscription CTA buttons
- Responsive design across devices
- SEO and structured data

### 4. `account-authenticated.spec.ts` (14 tests)
**Note**: These tests require authentication setup
- User dashboard display
- Credit balance and usage tracking
- Subscription management
- Auto-renewal toggle
- Security: authentication requirement

### 5. `api-integration.spec.ts` (12 tests)
- API endpoint integration
- Error handling (500, 429, network failures)
- Rate limiting responses
- File validation (client + server)
- CORS and security headers

### 6. `user-journey.spec.ts` (11 tests)
- Complete user flows from landing to conversion
- Multi-page navigation journeys
- Format selection and option adjustments
- Error recovery scenarios
- Cross-device experiences (mobile, tablet, desktop)

## Running Tests

### Prerequisites

1. **Install dependencies**:
   ```bash
   npm install
   ```

2. **Install Playwright browsers** (if not already installed):
   ```bash
   npx playwright install chromium
   ```

3. **Start development server** (tests run against `http://localhost:3000`):
   ```bash
   npm run dev
   ```

### Test Commands

```bash
# Run all E2E tests (headless)
npm run test:e2e

# Run with UI mode (interactive)
npm run test:e2e:ui

# Run with browser visible (headed mode)
npm run test:e2e:headed

# View HTML report
npm run test:e2e:report
```

### Run Specific Test Suite

```bash
# Run only homepage tests
npx playwright test homepage

# Run only conversion tests
npx playwright test convert-anonymous

# Run only API integration tests
npx playwright test api-integration
```

## Test Configuration

- **Browser**: Chromium (Desktop Chrome)
- **Base URL**: `http://localhost:3000`
- **Retries**: 2 on CI, 0 locally
- **Parallel Execution**: Enabled (except on CI)
- **Reporters**: HTML report
- **Screenshots**: On failure only
- **Videos**: Retained on failure
- **Traces**: On first retry

Configuration file: `playwright.config.ts`

## Authentication Tests

Tests in `account-authenticated.spec.ts` are currently **skipped** because they require authentication setup.

### To Enable Authentication Tests:

1. **Set up Google OAuth test credentials**
2. **Use Playwright's `storageState` feature**:
   ```typescript
   // auth.setup.ts
   test('authenticate', async ({ page }) => {
     await page.goto('/api/auth/signin');
     // Perform OAuth login
     await page.context().storageState({ path: 'auth.json' });
   });
   ```

3. **Load auth state in tests**:
   ```typescript
   test.use({ storageState: 'auth.json' });
   ```

See: https://playwright.dev/docs/auth

## Test Best Practices

### 1. **Locator Strategy**
- Prefer `getByRole()` for semantic elements
- Use `getByText()` for flexible text matching
- Avoid CSS selectors unless necessary

### 2. **Wait Strategy**
- Use `waitForSelector()` for dynamic content
- Use `expect().toBeVisible()` with timeout
- Avoid hardcoded `waitForTimeout()` when possible

### 3. **Test Data**
- Use programmatic buffers for test images
- Avoid external dependencies (files, APIs)
- Mock API responses when testing error handling

### 4. **Assertions**
- Check visibility: `expect(element).toBeVisible()`
- Check URL: `expect(page).toHaveURL(/pattern/)`
- Check attributes: `expect(element).toHaveAttribute('name', 'value')`

### 5. **Error Handling**
- Use conditional checks: `if (await element.count() > 0)`
- Gracefully handle optional elements
- Log useful debug information

## CI/CD Integration

### GitHub Actions Example

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Install Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci

      - name: Install Playwright browsers
        run: npx playwright install --with-deps chromium

      - name: Start backend
        run: |
          cd ../backend
          ./mvnw spring-boot:run &
          sleep 30

      - name: Run E2E tests
        run: npm run test:e2e

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: playwright-report
          path: playwright-report/
```

## Debugging Tests

### 1. **Run with UI Mode**
```bash
npm run test:e2e:ui
```
- Interactive test runner
- Time-travel debugging
- View network requests

### 2. **Run in Headed Mode**
```bash
npm run test:e2e:headed
```
- See browser actions in real-time

### 3. **Debug Specific Test**
```bash
npx playwright test homepage.spec.ts:10 --debug
```

### 4. **View Trace**
```bash
npx playwright show-trace trace.zip
```

## Coverage Summary

| Category | Test Count | Status |
|----------|------------|--------|
| Homepage & Navigation | 10 | ✅ Complete |
| Anonymous Conversion Flow | 18 | ✅ Complete |
| Pricing Page | 11 | ✅ Complete |
| Authenticated Account | 14 | ⏸️ Requires auth setup |
| API Integration | 12 | ✅ Complete |
| User Journeys | 11 | ✅ Complete |
| **Total** | **76** | **60 active, 14 skipped** |

## Known Limitations

1. **Authentication Tests**: Skipped until OAuth test setup is complete
2. **Real Image Processing**: Tests use mock data; actual ImageMagick conversion not tested
3. **Stripe Integration**: Payment flow requires Stripe test mode setup
4. **Database State**: Tests don't verify backend database state directly

## Next Steps

- [ ] Set up Google OAuth test credentials
- [ ] Enable authentication tests with storageState
- [ ] Add Stripe checkout flow tests (requires test API keys)
- [ ] Add visual regression testing with Playwright screenshots
- [ ] Integrate with CI/CD pipeline

## Resources

- [Playwright Documentation](https://playwright.dev)
- [Best Practices](https://playwright.dev/docs/best-practices)
- [Authentication Guide](https://playwright.dev/docs/auth)
- [API Testing](https://playwright.dev/docs/api-testing)

---

**Last Updated**: 2025-10-19
**Playwright Version**: 1.56.1
**Node Version**: 24.x
