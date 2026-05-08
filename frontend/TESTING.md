# 🧪 Frontend Testing Guide

Complete guide for running and writing tests for the Change-My.com frontend.

---

## 📋 Table of Contents

- [Test Types](#test-types)
- [Running Tests](#running-tests)
- [Test Structure](#test-structure)
- [Writing Tests](#writing-tests)
- [Best Practices](#best-practices)
- [CI/CD Integration](#cicd-integration)

---

## 🎯 Test Types

### 1. **Unit Tests** (Jest + React Testing Library)
- Component testing
- Utility function testing
- Custom hook testing
- **Coverage**: 70% threshold

### 2. **E2E Tests** (Playwright)
- User journey testing
- Cross-browser testing
- Integration testing
- **Browsers**: Chromium, Firefox, WebKit

---

## 🚀 Running Tests

### Unit Tests

```bash
# Run all unit tests in watch mode
npm test

# Run unit tests once
npm run test:unit

# Run with coverage report
npm run test:coverage

# Run for CI (no watch, with coverage)
npm run test:ci
```

### E2E Tests

```bash
# Run all E2E tests
npm run test:e2e

# Run with UI mode (interactive)
npm run test:e2e:ui

# Run in headed mode (see browser)
npm run test:e2e:headed

# Run specific browser
npm run test:e2e:chromium
npm run test:e2e:firefox
npm run test:e2e:webkit

# View test report
npm run test:e2e:report
```

### All Tests

```bash
# Run both unit and E2E tests
npm run test:all
```

### Create Test Fixtures

```bash
# Generate test image files
npm run test:fixtures
```

---

## 📂 Test Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── Header.tsx
│   │   └── __tests__/
│   │       └── Header.test.tsx          # Component unit tests
│   ├── lib/
│   │   ├── axios.ts
│   │   └── __tests__/
│   │       ├── axios.test.ts            # Utility unit tests
│   │       └── validation.test.ts
│   └── hooks/
│       └── __tests__/
│           └── useFileUpload.test.ts    # Custom hook tests
├── e2e/
│   ├── homepage.spec.ts                 # E2E test files
│   ├── convert-anonymous.spec.ts
│   ├── auth.setup.ts                    # Auth setup
│   └── fixtures/
│       ├── test-image.png               # Test fixtures
│       └── create-fixtures.js
├── jest.config.js                       # Jest configuration
├── jest.setup.js                        # Jest setup (mocks, etc.)
└── playwright.config.ts                 # Playwright configuration
```

---

## ✍️ Writing Tests

### Unit Test Example (Component)

```typescript
// src/components/__tests__/Header.test.tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { Header } from '../Header'

describe('Header Component', () => {
  it('should render logo', () => {
    render(<Header />)
    expect(screen.getByText('Image Converter')).toBeInTheDocument()
  })

  it('should toggle mobile menu', () => {
    render(<Header />)
    const menuButton = screen.getByLabelText('Toggle menu')
    fireEvent.click(menuButton)
    // Assert menu is visible
  })
})
```

### Unit Test Example (Utility)

```typescript
// src/lib/__tests__/validation.test.ts
import { validateEmail } from '../validation'

describe('Email Validation', () => {
  it('should accept valid emails', () => {
    expect(validateEmail('test@example.com')).toBe(true)
  })

  it('should reject invalid emails', () => {
    expect(validateEmail('invalid')).toBe(false)
  })
})
```

### Unit Test Example (Custom Hook)

```typescript
// src/hooks/__tests__/useFileUpload.test.ts
import { renderHook, act } from '@testing-library/react'
import { useFileUpload } from '../useFileUpload'

describe('useFileUpload Hook', () => {
  it('should add files', () => {
    const { result } = renderHook(() => useFileUpload())

    act(() => {
      result.current.addFiles([new File(['test'], 'test.jpg')])
    })

    expect(result.current.files).toHaveLength(1)
  })
})
```

### E2E Test Example

```typescript
// e2e/convert-anonymous.spec.ts
import { test, expect } from '@playwright/test';

test('should upload and convert image', async ({ page }) => {
  await page.goto('/convert');

  // Upload file
  const fileInput = page.locator('input[type="file"]');
  await fileInput.setInputFiles('./e2e/fixtures/test-image.png');

  // Select format
  await page.getByRole('button', { name: /png/i }).click();

  // Verify preview
  await expect(page.locator('img[src*="blob:"]')).toBeVisible();
});
```

---

## ✅ Best Practices

### General

- ✅ Write tests for all new features
- ✅ Test error states and edge cases
- ✅ Use descriptive test names
- ✅ Follow AAA pattern (Arrange, Act, Assert)
- ✅ Keep tests isolated and independent

### Unit Tests

- ✅ Mock external dependencies
- ✅ Test components in isolation
- ✅ Use `screen` queries for accessibility
- ✅ Test user interactions, not implementation
- ✅ Aim for 70%+ code coverage

### E2E Tests

- ✅ Test critical user journeys
- ✅ Use data-testid sparingly (prefer role/label)
- ✅ Handle async operations properly
- ✅ Clean up test data
- ✅ Run on multiple browsers

---

## 📊 Test Coverage

### Current Coverage

| Type | Files | Coverage |
|------|-------|----------|
| **Components** | 3/3 | 100% |
| **Utils** | 2/2 | 100% |
| **Hooks** | 1/1 | 100% |
| **E2E** | 6 suites | ~73 tests |

### Coverage Thresholds

```javascript
// jest.config.js
coverageThresholds: {
  global: {
    branches: 70,
    functions: 70,
    lines: 70,
    statements: 70,
  },
}
```

### Viewing Coverage

```bash
npm run test:coverage

# Open HTML report
open coverage/lcov-report/index.html
```

---

## 🔧 Configuration

### Jest (Unit Tests)

**File**: `jest.config.js`

- Test environment: `jsdom`
- Setup file: `jest.setup.js`
- Transform: Next.js config
- Coverage: 70% threshold

### Playwright (E2E Tests)

**File**: `playwright.config.ts`

- Browsers: Chromium, Firefox, WebKit, Mobile
- Base URL: `http://localhost:3000`
- Retry: 2 times on CI
- Screenshots/Videos on failure

---

## 🔄 CI/CD Integration

### GitHub Actions Example

```yaml
name: Tests

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: npm ci
      - run: npm run test:ci
      - uses: codecov/codecov-action@v3

  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: npm ci
      - run: npx playwright install --with-deps
      - run: npm run test:e2e
      - uses: actions/upload-artifact@v3
        if: failure()
        with:
          name: playwright-report
          path: playwright-report/
```

---

## 🐛 Debugging Tests

### Unit Tests

```bash
# Run specific test file
npm test Header.test.tsx

# Run tests matching pattern
npm test -- --testNamePattern="should render"

# Debug in VS Code
# Add breakpoint and use "Jest: Debug" configuration
```

### E2E Tests

```bash
# Run in UI mode (best for debugging)
npm run test:e2e:ui

# Run in headed mode
npm run test:e2e:headed

# Debug specific test
npm run test:e2e -- homepage.spec.ts --debug
```

### Common Issues

**Issue**: Tests timeout
**Solution**: Increase timeout in playwright.config.ts

**Issue**: Module not found
**Solution**: Check `moduleNameMapper` in jest.config.js

**Issue**: Auth tests failing
**Solution**: Check mock in `e2e/auth.setup.ts`

---

## 📝 Test Checklist

Before committing code:

- [ ] All unit tests pass (`npm run test:unit`)
- [ ] Coverage meets threshold (70%)
- [ ] E2E tests pass (`npm run test:e2e`)
- [ ] No console errors/warnings
- [ ] Tests are documented
- [ ] Test names are descriptive

---

## 🔗 Resources

- [Jest Documentation](https://jestjs.io/)
- [React Testing Library](https://testing-library.com/react)
- [Playwright Documentation](https://playwright.dev/)
- [Testing Best Practices](https://kentcdodds.com/blog/common-mistakes-with-react-testing-library)

---

## 📞 Support

If you encounter issues with tests:

1. Check this documentation
2. Review test examples in codebase
3. Check CI/CD logs
4. Create an issue with test output

---

**Happy Testing! 🎉**
