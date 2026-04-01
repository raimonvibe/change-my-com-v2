# 🖼️ Change-My.com

![Change-My Logo](change-my.png)

**🌐 Live at: [change-my.com](https://change-my.com)**

---

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/raimonvibe/change-my-com-v2)

## 📋 Executive Summary

**Change-My** is a ⚡ fast, 🔐 secure, and 🎯 user-friendly image conversion web application that transforms images between formats (PNG, JPG, WEBP, GIF, AVIF, and more) with just a few clicks. Built with modern web technologies and enterprise-grade security practices, it offers both anonymous conversions (up to 20/month) and authenticated user conversions with flexible pay-as-you-go pricing powered by Stripe.

**🤔 Why does it exist?** Many online image converters are clunky, ad-ridden, or require downloads. Change-My provides a clean, privacy-focused, and performant alternative that respects user data while offering professional-grade image processing through ImageMagick.

---

## 🏗️ Architecture Overview

### 📊 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         User's Browser                           │
│                      (React 19 + Next.js 16)                     │
└────────────────────────┬─────────────────────────────────────────┘
                         │
                         │ HTTPS (NextAuth.js OAuth)
                         ↓
┌──────────────────────────────────────────────────────────────────┐
│                    Frontend (Vercel)                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Next.js    │  │  Tailwind    │  │   Zustand    │          │
│  │   SSR/CSR    │  │     CSS      │  │    Store     │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└────────────────────────┬─────────────────────────────────────────┘
                         │
                         │ REST API (axios)
                         ↓
┌──────────────────────────────────────────────────────────────────┐
│                 Backend API (Railway/Render)                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Spring Boot 3.5.7 (Java 17)                   │ │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐  │ │
│  │  │   Security  │  │   Image      │  │    Billing      │  │ │
│  │  │   Layer     │→ │   Service    │  │    Service      │  │ │
│  │  │  (OAuth +   │  │ (ImageMagick)│  │   (Stripe)      │  │ │
│  │  │  Rate Limit)│  └──────────────┘  └─────────────────┘  │ │
│  │  └─────────────┘                                           │ │
│  └────────────────────────────────────────────────────────────┘ │
└────────────────────────┬─────────────────┬───────────────────────┘
                         │                 │
                         ↓                 ↓
         ┌───────────────────┐   ┌──────────────────┐
         │   PostgreSQL 16   │   │  Stripe API      │
         │   (Database)      │   │  (Payments)      │
         └───────────────────┘   └──────────────────┘
```

### 🧩 Key Modules & Components

#### 🎨 Frontend (`/frontend`)
- 📄 **Pages**: Home, Convert, Account, Billing, Legal, Privacy, Contact
- 🧱 **Components**: Header, AuthButtons, ErrorBoundary, WhoItsFor
- 🪝 **Hooks**: Custom React hooks for image conversion and auth state
- 🗄️ **Store**: Zustand for client-side state management
- 🛣️ **API Routes**: Next.js API routes for server-side operations

#### ☕ Backend (`/backend/src/main/java`)
- 🎮 **Controllers**: ConvertController, UserController, BillingController, StripeWebhookController
- ⚙️ **Services**: ImageService, UserService, AnonymousUserService
- 🔒 **Security**: GoogleIdTokenAuthFilter, RateLimitFilter, FileValidator, SecurityAuditLogger
- 📦 **Repositories**: JPA repositories for User, CreditLedger, IpConversionTracker
- 🛠️ **Config**: SecurityConfig, CorsConfig, WebConfig

### 🔄 Request Flow

1. 📤 **User uploads image** → Frontend validates file (size, type)
2. ⚡ **Frontend sends request** → Backend validates authentication (OAuth token or anonymous IP)
3. 🚦 **Rate limiting check** → Bucket4j verifies user hasn't exceeded limits
4. 🖼️ **Image processing** → ImageMagick converts image to target format
5. 💳 **Credit deduction** → User credits updated in PostgreSQL
6. 💾 **Download response** → Converted image returned as blob

---

## ⚡ Setup Guide

### ✅ Prerequisites

Before you begin, ensure you have the following installed:

| Tool | Version | Check Command |
|------|---------|---------------|
| 🟢 **Node.js** | 20.9.0+ | `node --version` |
| 📦 **npm** | 9.0.0+ | `npm --version` |
| ☕ **Java** | 17+ | `java -version` |
| 🏗️ **Maven** | 3.8.0+ | `mvn --version` |
| 🐘 **PostgreSQL** | 14+ | `psql --version` |
| 🐳 **Docker** (optional) | 20.10+ | `docker --version` |
| 🎨 **ImageMagick** | 7.0+ | `magick --version` |

**📝 Note for ImageMagick**:
- 🍎 **macOS**: `brew install imagemagick`
- 🐧 **Ubuntu/Debian**: `sudo apt-get install imagemagick`
- 🪟 **Windows**: Download from [imagemagick.org](https://imagemagick.org/script/download.php)

### 📥 Installation

#### 1️⃣ Step 1: Clone the Repository

```bash
git clone https://github.com/raimonvibe/change-my-com-v2.git
cd change-my-com-v2
```

#### 2️⃣ Step 2: Start PostgreSQL Database

**🅰️ Option A: Using Docker (Recommended)**

```bash
# ⚡ Start PostgreSQL container in background
docker-compose up -d

# ✅ Verify it's running
docker ps | grep postgres
```

**🅱️ Option B: Using Local PostgreSQL**

```bash
# 🗄️ Create database
psql -U postgres
CREATE DATABASE imageconverter;
\q
```

#### 3️⃣ Step 3: Backend Setup

```bash
# 📂 Navigate to backend directory
cd backend

# 📋 Copy environment variables template
cp ../.env.example .env

# ✏️ Edit .env with your actual values
nano .env  # or use your preferred editor

# 📦 Install dependencies and run tests
./mvnw clean install

# ⚡ Start the backend server (runs on http://localhost:8080)
./mvnw spring-boot:run
```

**🔐 Backend Environment Variables** (`.env`):

```bash
# 🔑 Google OAuth - Get from: https://console.cloud.google.com/
GOOGLE_CLIENT_ID=your-google-client-id-here

# 💳 Stripe Keys - Get from: https://dashboard.stripe.com/test/apikeys
STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key_here
STRIPE_PUBLISHABLE_KEY=pk_test_your_stripe_publishable_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret_here

# 🌐 CORS (allowed frontend URLs)
ALLOWED_ORIGINS=http://localhost:3000

# 🗄️ Optional: Database (uses defaults if not set)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/imageconverter
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

#### 4️⃣ Step 4: Frontend Setup

```bash
# 🖥️ Open new terminal, navigate to frontend directory
cd frontend

# 📦 Install dependencies (uses legacy-peer-deps for Next.js 16 + next-auth v4 compatibility)
npm install

# 📄 Create environment file
touch .env.local

# ✏️ Edit .env.local with your values
nano .env.local
```

**🔐 Frontend Environment Variables** (`.env.local`):

```bash
# 🔑 NextAuth Configuration
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-random-secret-here  # Generate with: openssl rand -base64 32

# 🔐 Google OAuth (same client ID as backend)
GOOGLE_CLIENT_ID=your-google-client-id-here
GOOGLE_CLIENT_SECRET=your-google-client-secret-here

# 🌐 Backend API URL
NEXT_PUBLIC_API_URL=http://localhost:8080

# 💳 Stripe Publishable Key (frontend only uses public key)
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_test_your_stripe_publishable_key_here
```

```bash
# ⚡ Start the frontend development server (runs on http://localhost:3000)
npm run dev
```

#### 5️⃣ Step 5: Verify Installation

Open your browser and navigate to:
- 🎨 **Frontend**: [http://localhost:3000](http://localhost:3000)
- 💚 **Backend Health**: [http://localhost:8080/health](http://localhost:8080/health)

🎉 You should see the Change-My homepage and be able to convert images!

---

## 📚 Usage Guide

### 👥 For End Users

#### 🕵️ Anonymous User Conversion

1. 🌐 Visit [change-my.com](https://change-my.com) or `http://localhost:3000`
2. 🖱️ Click **"Start Converting"** or navigate to `/convert`
3. 📎 Drag-and-drop or click to upload image (max 20MB)
4. 🎯 Select target format (PNG, JPG, WEBP, GIF, AVIF)
5. ⚡ Click **"Convert"**
6. 💾 Download converted image

**⚠️ Limits**: 20 conversions per month per IP address (anonymous users)

#### 🔐 Authenticated User Conversion

1. 👤 Click **"Sign In"** (top-right corner)
2. 🔑 Sign in with Google OAuth
3. 🎁 New users get 20 free credits
4. 🔄 Convert images (same flow as above)
5. 💰 Purchase more credits via **Account** → **Buy Credits**

**💰 Pricing**:
- 🆓 Free tier: 20 conversions
- ⭐ Pro plan: 1000 conversions for $9.99

### 👨‍💻 For Developers

#### 🛣️ API Endpoints

**🌐 Base URL**: `http://localhost:8080` (development) or `https://your-backend-domain.com` (production)

##### 1️⃣ Convert Image (Anonymous)

```bash
POST /api/convert/anonymous

curl -X POST http://localhost:8080/api/convert/anonymous \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/image.png" \
  -F "targetFormat=jpg" \
  --output converted.jpg
```

**📋 Parameters**:
- 📎 `file` (multipart/form-data): Image file (max 20MB)
- 🎯 `targetFormat` (string): Target format (`png`, `jpg`, `webp`, `gif`, `avif`)

**📤 Response**: Binary image data (Content-Type: `image/jpeg`, `image/png`, etc.)

##### 2️⃣ Convert Image (Authenticated)

```bash
POST /api/convert

curl -X POST http://localhost:8080/api/convert \
  -H "Authorization: Bearer YOUR_GOOGLE_ID_TOKEN" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@/path/to/image.png" \
  -F "targetFormat=webp" \
  --output converted.webp
```

##### 3️⃣ Get User Credits

```bash
GET /api/users/credits

curl -X GET http://localhost:8080/api/users/credits \
  -H "Authorization: Bearer YOUR_GOOGLE_ID_TOKEN"

# 📊 Response:
{
  "email": "user@example.com",
  "remainingCredits": 42
}
```

##### 4️⃣ Create Stripe Checkout Session

```bash
POST /api/billing/create-checkout-session

curl -X POST http://localhost:8080/api/billing/create-checkout-session \
  -H "Authorization: Bearer YOUR_GOOGLE_ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "successUrl": "http://localhost:3000/account?success=true",
    "cancelUrl": "http://localhost:3000/account?canceled=true"
  }'

# 📊 Response:
{
  "url": "https://checkout.stripe.com/c/pay/cs_test_..."
}
```

#### ⚛️ Frontend Components Example

```tsx
// 💡 Example: Using the conversion feature in a React component
import { useState } from 'react';
import axios from 'axios';

export default function ImageConverter() {
  const [file, setFile] = useState<File | null>(null);
  const [targetFormat, setTargetFormat] = useState('png');

  const handleConvert = async () => {
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);
    formData.append('targetFormat', targetFormat);

    try {
      const response = await axios.post(
        `${process.env.NEXT_PUBLIC_API_URL}/api/convert/anonymous`,
        formData,
        { responseType: 'blob' }
      );

      // 💾 Download converted image
      const url = URL.createObjectURL(response.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `converted.${targetFormat}`;
      a.click();
    } catch (error) {
      console.error('Conversion failed:', error);
    }
  };

  return (
    <div>
      <input type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} />
      <select value={targetFormat} onChange={(e) => setTargetFormat(e.target.value)}>
        <option value="png">PNG</option>
        <option value="jpg">JPG</option>
        <option value="webp">WEBP</option>
      </select>
      <button onClick={handleConvert}>Convert</button>
    </div>
  );
}
```

---

## ⚙️ Configuration

### 🔐 Environment Variables

#### ☕ Backend (`.env`)

| Variable | Description | Example | Required |
|----------|-------------|---------|----------|
| `GOOGLE_CLIENT_ID` | 🔑 Google OAuth client ID | `123456789-abc.apps.googleusercontent.com` | ✅ Yes |
| `STRIPE_SECRET_KEY` | 🔒 Stripe secret API key | `sk_test_51...` | ✅ Yes |
| `STRIPE_PUBLISHABLE_KEY` | 🔓 Stripe publishable key | `pk_test_51...` | ✅ Yes |
| `STRIPE_WEBHOOK_SECRET` | 🔐 Stripe webhook signing secret | `whsec_...` | ✅ Yes |
| `ALLOWED_ORIGINS` | 🌐 CORS allowed origins (comma-separated) | `http://localhost:3000,https://change-my.com` | ✅ Yes |
| `SPRING_DATASOURCE_URL` | 🗄️ PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/imageconverter` | ⚪ No (has default) |
| `SPRING_DATASOURCE_USERNAME` | 👤 Database username | `postgres` | ⚪ No (has default) |
| `SPRING_DATASOURCE_PASSWORD` | 🔑 Database password | `postgres` | ⚪ No (has default) |

#### ⚛️ Frontend (`.env.local`)

| Variable | Description | Example | Required |
|----------|-------------|---------|----------|
| `NEXTAUTH_URL` | 🌐 Frontend application URL | `http://localhost:3000` | ✅ Yes |
| `NEXTAUTH_SECRET` | 🔐 NextAuth.js encryption secret | `a1b2c3d4e5f6...` (use `openssl rand -base64 32`) | ✅ Yes |
| `GOOGLE_CLIENT_ID` | 🔑 Google OAuth client ID (same as backend) | `123456789-abc.apps.googleusercontent.com` | ✅ Yes |
| `GOOGLE_CLIENT_SECRET` | 🔒 Google OAuth client secret | `GOCSPX-...` | ✅ Yes |
| `NEXT_PUBLIC_API_URL` | 🌐 Backend API base URL | `http://localhost:8080` | ✅ Yes |
| `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` | 💳 Stripe publishable key (exposed to browser) | `pk_test_51...` | ✅ Yes |

### 🔒 Secrets Handling

**🛠️ Development**:
- 💾 Store secrets in `.env` (backend) and `.env.local` (frontend)
- ⚠️ **NEVER commit these files to git** (already in `.gitignore`)

**⚡ Production**:
- 🔐 Use platform-specific secret management:
  - ▲ **Vercel**: Environment Variables in project settings
  - 🚂 **Railway/Render**: Environment Variables in dashboard
  - ⚙️ **CI/CD**: GitHub Secrets for `GOOGLE_CLIENT_ID`, `STRIPE_SECRET_KEY`, etc.

**🔑 Generating Secrets**:

```bash
# 🎲 Generate NEXTAUTH_SECRET
openssl rand -base64 32

# 💳 Generate Stripe webhook secret
# 1️⃣ Go to https://dashboard.stripe.com/test/webhooks
# 2️⃣ Create webhook endpoint: https://your-backend.com/api/webhooks/stripe
# 3️⃣ Copy the signing secret (starts with whsec_)
```

### 📝 Configuration Files

#### ☕ `backend/src/main/resources/application.properties`

```properties
# 📛 Application name
spring.application.name=ImageConverter

# 📊 Logging level
logging.level.com.raimonvibe.imageconverter=DEBUG

# 🔄 Flyway database migrations (enabled automatically)
spring.flyway.enabled=true
```

#### ⚛️ `frontend/next.config.ts`

```typescript
// ⚙️ Next.js configuration
export default {
  reactStrictMode: true,
  images: {
    domains: ['lh3.googleusercontent.com'], // 🖼️ Google profile images
  },
  // 🛠️ Other Next.js settings...
};
```

---

## 🧪 Testing

### 🎯 Test Strategy

**⚛️ Frontend**: Unit tests (Jest) + E2E tests (Playwright)
**☕ Backend**: Unit tests (JUnit) + Integration tests (Spring Boot Test with H2 in-memory DB)
**📊 Coverage Goal**: 80%+ code coverage across both frontend and backend

### 🏃 Running Tests

#### ⚛️ Frontend Tests

```bash
cd frontend

# ▶️ Run all unit tests (watch mode)
npm test

# 🔄 Run unit tests once (CI mode with coverage)
npm run test:ci

# 📊 Run unit tests with coverage report
npm run test:coverage

# 🎭 Run E2E tests (all browsers)
npm run test:e2e

# 👁️ Run E2E tests in headed mode (see browser)
npm run test:e2e:headed

# 🖥️ Run E2E tests in UI mode (interactive)
npm run test:e2e:ui

# 🌐 Run E2E tests for specific browser
npm run test:e2e:chromium
npm run test:e2e:firefox
npm run test:e2e:webkit

# 📄 View E2E test report
npm run test:e2e:report
```

**📝 Example Test**: `frontend/src/components/__tests__/Header.test.tsx`

```typescript
import { render, screen } from '@testing-library/react';
import Header from '../Header';

test('renders header with logo', () => {
  render(<Header />);
  expect(screen.getByText('Change-My')).toBeInTheDocument();
});
```

#### ☕ Backend Tests

```bash
cd backend

# ▶️ Run all tests
./mvnw test

# 📊 Run tests with coverage
./mvnw test jacoco:report

# 🎯 Run specific test class
./mvnw test -Dtest=ImageServiceTest

# ⚡ Run tests in parallel (faster)
./mvnw test -T 4
```

**📝 Example Test**: `backend/src/test/java/com/raimonvibe/imageconverter/image/ImageServiceTest.java`

```java
@SpringBootTest
class ImageServiceTest {
    @Autowired
    private ImageService imageService;

    @Test
    void testConvertImage_Success() {
        // 📋 Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.png", "image/png", "test".getBytes()
        );

        // ⚡ Act
        byte[] result = imageService.convertImage(file, "jpg");

        // ✅ Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
}
```

### 📊 Test Coverage

```bash
# ⚛️ Frontend coverage (generates coverage/ directory)
cd frontend
npm run test:coverage
open coverage/lcov-report/index.html  # 📄 View HTML report

# ☕ Backend coverage (generates target/site/jacoco/ directory)
cd backend
./mvnw test jacoco:report
open target/site/jacoco/index.html  # 📄 View HTML report
```

**✅ Current Test Status**: 200+ tests with 100% pass rate

---

## ⚡ Deployment

### 💻 Local Development

Already covered in **Setup Guide** above. Quick reminder:

```bash
# 🖥️ Terminal 1: Database
docker-compose up

# ☕ Terminal 2: Backend
cd backend && ./mvnw spring-boot:run

# ⚛️ Terminal 3: Frontend
cd frontend && npm run dev
```

### 🌍 Staging/Production

#### ▲ Frontend Deployment (Vercel)

**🤖 Automatic Deployment**:
1. 📤 Push to `main` branch triggers automatic deployment via GitHub Actions
2. ✅ Vercel builds and deploys frontend to `https://change-my.com`

**👋 Manual Deployment**:

```bash
cd frontend

# 📦 Install Vercel CLI
npm install -g vercel

# 🔑 Login to Vercel
vercel login

# ⚡ Deploy to preview
vercel

# 🌟 Deploy to production
vercel --prod
```

**🔐 Environment Variables** (set in Vercel dashboard):
- `NEXTAUTH_URL=https://change-my.com`
- `NEXTAUTH_SECRET=<your-production-secret>`
- `GOOGLE_CLIENT_ID=<your-google-client-id>`
- `GOOGLE_CLIENT_SECRET=<your-google-client-secret>`
- `NEXT_PUBLIC_API_URL=https://your-backend-url.com`
- `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...`

📖 See [VERCEL-DEPLOYMENT.md](VERCEL-DEPLOYMENT.md) for detailed instructions.

#### 🚂 Backend Deployment (Railway)

**⚡ Deployment Steps**:

```bash
cd backend

# 📦 Install Railway CLI
npm install -g @railway/cli

# 🔑 Login to Railway
railway login

# 🔗 Link project
railway link

# ⚡ Deploy
railway up
```

**🔐 Required Environment Variables** (set in Railway dashboard):
- `GOOGLE_CLIENT_ID`
- `STRIPE_SECRET_KEY`
- `STRIPE_PUBLISHABLE_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `ALLOWED_ORIGINS=https://change-my.com`
- `DATABASE_URL` (automatically provisioned by Railway PostgreSQL)

**🎨 ImageMagick Setup**: Railway uses Docker. The `Dockerfile` in `/backend` includes ImageMagick installation with AVIF support.

📖 See [RAILWAY-DEPLOYMENT.md](RAILWAY-DEPLOYMENT.md) for detailed instructions.

#### 🎨 Alternative: Backend Deployment (Render)

📖 See [RENDER-DEPLOYMENT.md](RENDER-DEPLOYMENT.md) for Render deployment instructions.

### ⚙️ CI/CD Pipeline

**🤖 GitHub Actions** (`.github/workflows/ci-cd.yml`):

```yaml
# 🔄 Automated on every push to main and all pull requests
- ✅ Checkout code
- 🛠️ Setup Node.js 20 + Java 17
- 📦 Install dependencies (frontend & backend)
- 🧪 Run tests (frontend & backend)
- 📦 Build application packages
```

**🎯 Workflow triggers**:
- 📤 Push to `main` → Run tests + deploy
- 🔀 Pull request → Run tests only

**👀 View workflow runs**: [GitHub Actions](https://github.com/raimonvibe/change-my-com-v2/actions)

---

## 🤝 Contributing Guide

We welcome contributions from the community! Whether you're fixing bugs, adding features, or improving documentation, your help is appreciated. 🎉

### ⚡ Getting Started

1. 🍴 **Fork the repository** on GitHub
2. 📥 **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/change-my-com-v2.git
   cd change-my-com-v2
   ```
3. 🌿 **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/bug-description
   ```

### 🌳 Branching Strategy

- 🌟 **`main`**: Production-ready code (protected, requires PR approval)
- ✨ **`feature/*`**: New features (e.g., `feature/add-pdf-support`)
- 🐛 **`fix/*`**: Bug fixes (e.g., `fix/auth-token-expiry`)
- 📚 **`docs/*`**: Documentation updates (e.g., `docs/update-readme`)
- 🧪 **`test/*`**: Test improvements (e.g., `test/add-e2e-coverage`)

### 🛠️ Development Workflow

1. ✍️ **Write code** following existing patterns and style
2. 🎨 **Run linter** to ensure code quality:
   ```bash
   # ⚛️ Frontend
   cd frontend
   npm run lint

   # ☕ Backend (checkstyle via Maven)
   cd backend
   ./mvnw validate
   ```
3. 🧪 **Write tests** for new features:
   - ⚛️ Frontend: Add Jest tests in `__tests__/` directories
   - ☕ Backend: Add JUnit tests in `src/test/java/`
4. ✅ **Run all tests** to ensure nothing breaks:
   ```bash
   # ⚛️ Frontend
   npm run test:ci
   npm run test:e2e

   # ☕ Backend
   ./mvnw test
   ```
5. 💾 **Commit your changes** with clear messages:
   ```bash
   git add .
   git commit -m "feat: add PDF to image conversion support"
   # or
   git commit -m "fix: resolve auth token expiry issue #123"
   ```

   **📋 Commit message format**:
   - ✨ `feat:` New feature
   - 🐛 `fix:` Bug fix
   - 📚 `docs:` Documentation changes
   - 🧪 `test:` Test additions/changes
   - ♻️ `refactor:` Code refactoring
   - 🔧 `chore:` Build/tooling changes

6. 📤 **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. 🔀 **Open a Pull Request** on GitHub:
   - 📝 Title: Clear description of change (e.g., "Add PDF to image conversion")
   - 📄 Description: Explain what changed, why, and how to test
   - 🔗 Link related issues (e.g., "Closes #123")

### ✅ Pull Request Guidelines

**📋 Before submitting**:
- ✅ Code follows existing style and patterns
- ✅ All tests pass (`npm run test:ci` and `./mvnw test`)
- ✅ Linter passes (`npm run lint` and `./mvnw validate`)
- ✅ New features have tests
- ✅ Documentation updated (README, code comments)
- ✅ No sensitive data (API keys, passwords) committed

**🎯 PR Requirements**:
- 👍 At least 1 approval from maintainer
- ✅ All CI/CD checks pass (GitHub Actions)
- 🚫 No merge conflicts with `main`

### 🎨 Code Style

**⚛️ Frontend (TypeScript/React)**:
- 📘 Use **TypeScript** for all new files
- 🎯 Follow **ESLint** configuration (`.eslintrc.json`)
- ⚛️ Use **functional components** with hooks
- 📦 Prefer **named exports** over default exports
- 🎨 Use **Tailwind CSS** for styling (avoid inline styles)

**☕ Backend (Java/Spring Boot)**:
- 📐 Follow **Java naming conventions** (camelCase methods, PascalCase classes)
- 🏷️ Use **Lombok** annotations for getters/setters (`@Data`, `@Builder`)
- 📝 Write **Javadoc** for public methods
- 💉 Use **constructor injection** for dependencies (not `@Autowired` fields)
- 🌐 Follow **RESTful** API design principles

### 🐛 Reporting Issues

Found a bug or have a feature request? [Open an issue](https://github.com/raimonvibe/change-my-com-v2/issues/new):

**📝 Bug Report Template**:
```
**🐛 Describe the bug**
A clear description of what the bug is.

**🔄 To Reproduce**
1. Go to '...'
2. Click on '...'
3. See error

**✅ Expected behavior**
What you expected to happen.

**📸 Screenshots**
If applicable, add screenshots.

**💻 Environment:**
- OS: [e.g., macOS 13.4]
- Browser: [e.g., Chrome 114]
- Version: [e.g., v1.0.0]
```

---

## ❓ FAQ & Troubleshooting

### 🔧 Common Errors & Fixes

#### 1️⃣ `ECONNREFUSED` when frontend calls backend

**❌ Error**:
```
Error: connect ECONNREFUSED 127.0.0.1:8080
```

**🔍 Cause**: Backend server not running or wrong `NEXT_PUBLIC_API_URL`

**✅ Fix**:
```bash
# ✅ Verify backend is running
curl http://localhost:8080/health

# 🔍 Check .env.local in frontend
cat frontend/.env.local | grep NEXT_PUBLIC_API_URL
# Should be: NEXT_PUBLIC_API_URL=http://localhost:8080

# 🔄 Restart frontend to reload environment variables
cd frontend && npm run dev
```

---

#### 2️⃣ `ImageMagick not found` error

**❌ Error**:
```
java.io.IOException: Cannot run program "magick": error=2, No such file or directory
```

**🔍 Cause**: ImageMagick not installed or not in PATH

**✅ Fix**:
```bash
# 🍎 macOS
brew install imagemagick

# 🐧 Ubuntu/Debian
sudo apt-get update && sudo apt-get install imagemagick

# ✅ Verify installation
magick --version
```

---

#### 3️⃣ Database connection failed

**❌ Error**:
```
org.postgresql.util.PSQLException: Connection refused. Check that the hostname and port are correct
```

**🔍 Cause**: PostgreSQL not running or wrong credentials

**✅ Fix**:
```bash
# 🔍 Check if PostgreSQL is running
docker ps | grep postgres
# or (if using local PostgreSQL)
sudo systemctl status postgresql

# ⚡ Start PostgreSQL with Docker
docker-compose up -d

# ✅ Verify connection manually
psql -h localhost -U postgres -d imageconverter
```

---

#### 4️⃣ Google OAuth error: "redirect_uri_mismatch"

**❌ Error**:
```
Error 400: redirect_uri_mismatch
```

**🔍 Cause**: Redirect URI not configured in Google Cloud Console

**✅ Fix**:
1. 🌐 Go to [Google Cloud Console](https://console.cloud.google.com/)
2. 📂 Select your project → APIs & Services → Credentials
3. ✏️ Edit OAuth 2.0 Client ID
4. ➕ Add authorized redirect URIs:
   - `http://localhost:3000/api/auth/callback/google` (dev)
   - `https://change-my.com/api/auth/callback/google` (prod)
5. 💾 Save changes and restart frontend

---

#### 5️⃣ Stripe webhook fails

**❌ Error**:
```
Stripe webhook signature verification failed
```

**🔍 Cause**: Wrong `STRIPE_WEBHOOK_SECRET` or webhook not configured

**✅ Fix**:
```bash
# 1️⃣ Install Stripe CLI
brew install stripe/stripe-cli/stripe

# 2️⃣ Login to Stripe
stripe login

# 3️⃣ Forward webhooks to local backend
stripe listen --forward-to localhost:8080/api/webhooks/stripe

# 4️⃣ Copy webhook signing secret from terminal output (starts with whsec_)
# 5️⃣ Update .env with new STRIPE_WEBHOOK_SECRET
# 6️⃣ Restart backend
```

---

#### 6️⃣ Tests fail with "Cannot find module"

**❌ Error**:
```
Cannot find module '@/components/Header' from 'src/app/__tests__/page.test.tsx'
```

**🔍 Cause**: Jest cannot resolve TypeScript path aliases

**✅ Fix**:
```bash
# 🔍 Ensure jest.config.js has moduleNameMapper
cat frontend/jest.config.js

# Should include:
# moduleNameMapper: {
#   '^@/(.*)$': '<rootDir>/src/$1',
# }

# 🧹 Clear Jest cache
npm run test -- --clearCache
npm test
```

---

#### 7️⃣ Rate limiting blocks all requests

**❌ Error**:
```
HTTP 429: Too Many Requests
```

**🔍 Cause**: Rate limit exceeded (50 requests/min per IP)

**✅ Fix**:
- 🛠️ **Development**: Wait 1 minute or restart backend to reset rate limits
- ⚡ **Production**: Review `RateLimitFilter.java` and adjust limits if needed
- 🧪 **Testing**: Use multiple IPs or authenticated requests (higher limits)

---

#### 8️⃣ File upload fails: "File too large"

**❌ Error**:
```
Payload Too Large (413)
```

**🔍 Cause**: File exceeds 20MB limit (Spring Boot default)

**✅ Fix**:
```properties
# ☕ backend/src/main/resources/application.properties
# Add these lines:
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=20MB
```

---

### 💬 Frequently Asked Questions

**❓ Q: Can I use this project for commercial purposes?**
💡 A: This project is proprietary software. Contact the author for licensing.

**❓ Q: How do I add support for new image formats (e.g., TIFF, BMP)?**
💡 A: Add the format to `ImageService.java` ALLOWED_FORMATS list and update frontend format selector.

**❓ Q: Can I deploy this without Docker?**
💡 A: Yes! Use local PostgreSQL and ImageMagick installations. Docker is optional but recommended.

**❓ Q: How do I enable HTTPS locally?**
💡 A: Use [mkcert](https://github.com/FiloSottile/mkcert) to generate local SSL certificates, then configure Next.js and Spring Boot to use them.

**❓ Q: Where can I find API documentation?**
💡 A: See the **Usage Guide → API Endpoints** section above. For detailed API specs, consider adding Swagger/OpenAPI (not currently implemented).

**❓ Q: How do I contribute?**
💡 A: See the **Contributing Guide** section above. Start with small PRs and join discussions in GitHub Issues.

---

## 📄 License and Credits

### ⚖️ License

This project is **proprietary software**. All rights reserved.

For licensing inquiries, please contact the author.

### 👤 Author

**Raimon Vibe**
- 🐙 GitHub: [@raimonvibe](https://github.com/raimonvibe)
- 🌐 Website: [change-my.com](https://change-my.com)
- 📧 Email: contact via GitHub

### 🙏 Credits & Acknowledgments

**🛠️ Built with**:
- ⚛️ [Next.js](https://nextjs.org/) - React framework by Vercel
- ☕ [Spring Boot](https://spring.io/projects/spring-boot) - Java application framework
- 🐘 [PostgreSQL](https://www.postgresql.org/) - Open-source relational database
- 🎨 [ImageMagick](https://imagemagick.org/) - Image processing library
- 💳 [Stripe](https://stripe.com/) - Payment processing
- 🎨 [Tailwind CSS](https://tailwindcss.com/) - Utility-first CSS framework
- 🔐 [NextAuth.js](https://next-auth.js.org/) - Authentication for Next.js
- 🗄️ [Zustand](https://github.com/pmndrs/zustand) - State management
- 🎭 [Playwright](https://playwright.dev/) - E2E testing framework
- 🧪 [Jest](https://jestjs.io/) - JavaScript testing framework

**🌟 Special Thanks**:
- ▲ Deployed on [Vercel](https://vercel.com/) (frontend) and 🚂 [Railway](https://railway.app/) (backend)
- 🎨 Icons from [Lucide React](https://lucide.dev/)
- 👥 Community contributors and testers

**📦 Open Source Dependencies**: See `package.json` (frontend) and `pom.xml` (backend) for full list of dependencies and their licenses.

---

**⚡ Ready to convert your images? Visit [change-my.com](https://change-my.com) now!**

💬 Need help? [Open an issue](https://github.com/raimonvibe/change-my-com-v2/issues) or check our [existing documentation](https://github.com/raimonvibe/change-my-com-v2/tree/main).
