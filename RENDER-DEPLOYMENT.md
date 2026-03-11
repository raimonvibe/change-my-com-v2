# Render Deployment Guide

## 🚀 Complete Render Setup for Image Converter

### 1. **Database Setup (PostgreSQL)**

#### Create PostgreSQL Database:
1. Go to [Render Dashboard](https://dashboard.render.com)
2. Click **"New +"** → **"PostgreSQL"**
3. Configure:
   - **Name**: `imageconverter-db`
   - **Database**: `imageconverter`
   - **User**: `imageconverter_user`
   - **Region**: Choose closest to your users
   - **Plan**: Free tier (1GB) or paid for production

4. **Save the connection details** - you'll need these for your app:
   - **Internal Database URL**: `postgresql://imageconverter_user:password@dpg-xxxxx-a/imageconverter`
   - **External Database URL**: `postgresql://imageconverter_user:password@dpg-xxxxx-a.oregon-postgres.render.com/imageconverter`

### 2. **Backend Deployment (Spring Boot)**

#### Create Web Service:
1. Click **"New +"** → **"Web Service"**
2. Connect your GitHub repository
3. Configure:
   - **Name**: `imageconverter-backend`
   - **Environment**: `Java`
   - **Build Command**: `cd backend && ./mvnw clean package -DskipTests`
   - **Start Command**: `cd backend && java -jar target/change-my-image-0.0.1-SNAPSHOT.jar`
   - **Plan**: Free tier or paid

#### Environment Variables:
Add these in Render dashboard → Your Service → Environment:

```bash
# Database
DATABASE_URL=postgresql://imageconverter_user:password@dpg-xxxxx-a.oregon-postgres.render.com/imageconverter
DATABASE_USERNAME=imageconverter_user
DATABASE_PASSWORD=your-db-password

# Stripe (get from Stripe dashboard)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth (get from Google Cloud Console)
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com

# CORS (your frontend URL)
ALLOWED_ORIGINS=https://your-frontend.onrender.com

# Production profile
SPRING_PROFILES_ACTIVE=prod

# Server port (Render sets this automatically)
PORT=10000
```

### 3. **Frontend Deployment (Next.js)**

#### Create Static Site:
1. Click **"New +"** → **"Static Site"**
2. Connect your GitHub repository
3. Configure:
   - **Name**: `imageconverter-frontend`
   - **Build Command**: `cd frontend && npm install && npm run build`
   - **Publish Directory**: `frontend/out`
   - **Node Version**: `18` or `20`

#### Environment Variables:
Add these in Render dashboard → Your Static Site → Environment:

```bash
# Backend URL (your Render backend URL)
NEXT_PUBLIC_API_URL=https://imageconverter-backend.onrender.com

# Stripe (same as backend)
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...

# Google OAuth (same as backend)
NEXTAUTH_URL=https://your-frontend.onrender.com
NEXTAUTH_SECRET=your-nextauth-secret
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

### 4. **Required Configuration Updates**

#### Update Backend CORS:
In your `SecurityConfig.java`, update the allowed origins:

```java
List<String> allowedOrigins = List.of(
    "http://localhost:3000",
    "https://your-frontend.onrender.com"  // Add your Render frontend URL
);
```

#### Update Frontend API URL:
In your frontend, make sure the API URL points to your Render backend:

```typescript
// In your frontend config
const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
```

### 5. **Stripe Webhook Configuration**

1. Go to [Stripe Dashboard](https://dashboard.stripe.com) → Webhooks
2. Click **"Add endpoint"**
3. **Endpoint URL**: `https://imageconverter-backend.onrender.com/stripe/webhook`
4. **Events to send**:
   - `checkout.session.completed`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
5. Copy the **Webhook signing secret** and add it to your backend environment variables

### 6. **Google OAuth Setup**

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create/select a project
3. Enable Google+ API
4. Go to **Credentials** → **Create Credentials** → **OAuth 2.0 Client ID**
5. **Application type**: Web application
6. **Authorized redirect URIs**:
   - `http://localhost:3000/api/auth/callback/google` (for local dev)
   - `https://your-frontend.onrender.com/api/auth/callback/google` (for production)
7. Copy the **Client ID** and **Client Secret**

### 7. **Deployment Steps**

#### Step 1: Prepare Repository
```bash
# Make sure all changes are committed
git add .
git commit -m "Prepare for Render deployment"
git push origin main
```

#### Step 2: Deploy Database
1. Create PostgreSQL service in Render
2. Note down the connection details

#### Step 3: Deploy Backend
1. Create Web Service in Render
2. Add all environment variables
3. Deploy and wait for build to complete
4. Test: `https://your-backend.onrender.com/health`

#### Step 4: Deploy Frontend
1. Create Static Site in Render
2. Add all environment variables
3. Deploy and wait for build to complete
4. Test: Visit your frontend URL

### 8. **Post-Deployment Testing**

#### Test Backend:
```bash
# Health check
curl https://your-backend.onrender.com/health

# Test convert endpoint
curl -X POST https://your-backend.onrender.com/api/convert/formats
```

#### Test Frontend:
1. Visit your frontend URL
2. Try uploading an image
3. Test authentication with Google
4. Test Stripe checkout flow

### 9. **Render-Specific Optimizations**

#### Backend Optimizations:
```yaml
# Add to application-prod.yml
server:
  tomcat:
    max-connections: 200
    accept-count: 50
    max-threads: 50
    min-spare-threads: 5
```

#### Frontend Optimizations:
```javascript
// In next.config.ts
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  trailingSlash: true,
  images: {
    unoptimized: true
  }
}
```

### 10. **Monitoring & Logs**

#### View Logs:
- **Backend**: Render Dashboard → Your Service → Logs
- **Frontend**: Render Dashboard → Your Static Site → Logs

#### Monitor Performance:
- Check Render dashboard for CPU/Memory usage
- Monitor response times
- Set up alerts for downtime

### 11. **Common Issues & Solutions**

#### Issue: Database Connection Failed
```bash
# Check if DATABASE_URL is correct
# Make sure to use external URL, not internal
```

#### Issue: CORS Errors
```bash
# Update ALLOWED_ORIGINS with your frontend URL
# Check that frontend is using correct backend URL
```

#### Issue: Stripe Webhook Failed
```bash
# Verify webhook URL is correct
# Check webhook secret is set correctly
# Test webhook in Stripe dashboard
```

#### Issue: Google OAuth Failed
```bash
# Check redirect URIs in Google Console
# Verify client ID and secret are correct
```

#### Issue: Web Service exceeded its memory limit
The app is tuned for Render’s free tier (512MB): 20MB max upload, and images over 1920px are auto-resized before conversion to avoid ImageMagick memory spikes (e.g. iPhone 12MP photos).

- **If you still see memory alerts:** Upgrade the backend to a larger instance in Render (e.g. paid plan with more RAM), or ensure users upload images under 20MB; the frontend already blocks larger files.
- **File size:** Uploads are limited to 20MB (frontend and backend). This keeps smartphone HEIC/JPEG within a safe range while allowing full-resolution conversion after server-side resize.

### 12. **Cost Optimization**

#### Free Tier Limits:
- **Backend**: 750 hours/month (sleeps after 15 min inactivity)
- **Database**: 1GB storage, 1 month retention
- **Frontend**: Unlimited static hosting

#### Paid Plans:
- **Backend**: $7/month for always-on (512MB RAM) — the app is tuned for this (20MB uploads, auto-resize at 1920px, ImageMagick memory limits).
- **Database**: ~$6/month for Starter PostgreSQL; $7/month for 1GB, $20/month for 10GB

### 13. **Security Checklist for Render**

- ✅ Environment variables set (no secrets in code)
- ✅ HTTPS enabled (automatic on Render)
- ✅ CORS configured correctly
- ✅ Database access restricted
- ✅ Stripe webhook signature verification
- ✅ Google OAuth properly configured
- ✅ Production profile active
- ✅ Error messages sanitized

**📚 Complete Security Guide:** [SECURITY-GUIDE.md](SECURITY-GUIDE.md)

### 14. **Backup & Recovery**

#### Database Backup:
```bash
# Render automatically backs up PostgreSQL
# Manual backup via pg_dump if needed
```

#### Code Backup:
```bash
# Your code is in GitHub
# Render builds from GitHub automatically
```

### 15. **Scaling Considerations**

#### When to Upgrade:
- More than 750 hours/month usage
- Database storage > 1GB
- Need faster response times
- Need more concurrent users

#### Upgrade Path:
1. Upgrade backend to paid plan
2. Upgrade database to paid plan
3. Consider CDN for frontend
4. Add monitoring and alerting

---

## 🎯 Quick Start Commands

```bash
# 1. Create database in Render dashboard
# 2. Create backend web service
# 3. Create frontend static site
# 4. Add environment variables
# 5. Deploy and test!

# Test your deployment
curl https://your-backend.onrender.com/health
```

Your image converter will be live at:
- **Frontend**: `https://your-frontend.onrender.com`
- **Backend**: `https://your-backend.onrender.com`
- **Database**: Managed by Render
