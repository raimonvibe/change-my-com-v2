# 🚂 Railway Deployment Guide - Complete Full-Stack Solution

## 🎯 Why Railway is Perfect for Your App

Railway can deploy **everything** in one place:
- ✅ **Backend** (Spring Boot + Java)
- ✅ **Frontend** (Next.js)
- ✅ **Database** (PostgreSQL)
- ✅ **Automatic deployments** from GitHub
- ✅ **Environment management**
- ✅ **Custom domains** and SSL

### **1. Database Setup (PostgreSQL)**

#### Create PostgreSQL Database:
1. Go to [Railway Dashboard](https://railway.app)
2. Click **"New Project"**
3. Click **"Provision PostgreSQL"**
4. **Database will be created automatically** with connection details

#### Get Connection Details:
1. **Click on your PostgreSQL service**
2. **Go to "Variables" tab**
3. **Copy these values**:
   - `DATABASE_URL` (full connection string)
   - `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`

### **2. Backend Deployment (Spring Boot)**

#### Create Backend Service:
1. **In your Railway project**, click **"New Service"**
2. **Select "GitHub Repo"**
3. **Choose your repository**
4. **Railway will auto-detect** it's a Java/Spring Boot app

#### Configure Backend:
1. **Go to your backend service**
2. **Click "Settings"** → **"Variables"**
3. **Add these environment variables**:

```bash
# Database (Railway provides these automatically)
DATABASE_URL=${{PostgreSQL.DATABASE_URL}}
DATABASE_USERNAME=${{PostgreSQL.PGUSER}}
DATABASE_PASSWORD=${{PostgreSQL.PGPASSWORD}}

# Stripe (get from Stripe dashboard)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth (get from Google Cloud Console)
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com

# CORS (your frontend URL)
ALLOWED_ORIGINS=https://your-frontend.railway.app

# Production profile
SPRING_PROFILES_ACTIVE=prod

# Railway automatically sets PORT
```

#### Railway Configuration:
1. **Go to "Settings"** → **"Deploy"**
2. **Root Directory**: `backend`
3. **Build Command**: `./mvnw clean package -DskipTests`
4. **Start Command**: `java -jar target/change-my-image-0.0.1-SNAPSHOT.jar`

### **3. Frontend Deployment (Next.js)**

#### Create Frontend Service:
1. **In your Railway project**, click **"New Service"**
2. **Select "GitHub Repo"** (same repo)
3. **Railway will auto-detect** it's a Next.js app

#### Configure Frontend:
1. **Go to your frontend service**
2. **Click "Settings"** → **"Variables"**
3. **Add these environment variables**:

```bash
# Backend URL (Railway provides this automatically)
NEXT_PUBLIC_API_URL=${{Backend.RAILWAY_PUBLIC_DOMAIN}}

# Stripe (same as backend)
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...

# NextAuth
NEXTAUTH_URL=${{Frontend.RAILWAY_PUBLIC_DOMAIN}}
NEXTAUTH_SECRET=your-random-secret-here

# Google OAuth (same as backend)
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

#### Railway Configuration:
1. **Go to "Settings"** → **"Deploy"**
2. **Root Directory**: `frontend`
3. **Build Command**: `npm install && npm run build`
4. **Start Command**: `npm start`

### **4. One-Click Deployment (Recommended)**

#### Use Railway Blueprint:
1. **Go to Railway Dashboard**
2. **Click "New Project"** → **"Deploy from Blueprint"**
3. **Paste this configuration**:

```yaml
services:
  - type: pserv
    name: imageconverter-db
    env: postgresql
    plan: free

  - type: web
    name: imageconverter-backend
    env: java
    plan: free
    buildCommand: cd backend && ./mvnw clean package -DskipTests
    startCommand: cd backend && java -jar target/change-my-image-0.0.1-SNAPSHOT.jar
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DATABASE_URL
        fromDatabase:
          name: imageconverter-db
          property: connectionString
      - key: SPRING_SECURITY_USER_NAME
        value: admin
      - key: SPRING_SECURITY_USER_PASSWORD
        sync: false
      - key: STRIPE_SECRET_KEY
        sync: false
      - key: STRIPE_PUBLISHABLE_KEY
        sync: false
      - key: STRIPE_WEBHOOK_SECRET
        sync: false
      - key: GOOGLE_CLIENT_ID
        sync: false
      - key: ALLOWED_ORIGINS
        fromService:
          type: web
          name: imageconverter-frontend
          property: host

  - type: web
    name: imageconverter-frontend
    env: static
    plan: free
    buildCommand: cd frontend && npm install && npm run build
    staticPublishPath: frontend/out
    envVars:
      - key: NEXT_PUBLIC_API_URL
        fromService:
          type: web
          name: imageconverter-backend
          property: host
      - key: NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY
        sync: false
      - key: NEXTAUTH_URL
        fromService:
          type: web
          name: imageconverter-frontend
          property: host
      - key: NEXTAUTH_SECRET
        sync: false
      - key: GOOGLE_CLIENT_ID
        sync: false
      - key: GOOGLE_CLIENT_SECRET
        sync: false
```

### **4. Railway Configuration Files**

#### Create railway.json (Optional):
```json
{
  "$schema": "https://railway.app/railway.schema.json",
  "build": {
    "builder": "NIXPACKS"
  },
  "deploy": {
    "startCommand": "java -jar target/change-my-image-0.0.1-SNAPSHOT.jar",
    "restartPolicyType": "ON_FAILURE",
    "restartPolicyMaxRetries": 10
  }
}
```

#### Create nixpacks.toml for Backend:
```toml
[phases.setup]
nixPkgs = ["jdk17", "maven"]

[phases.install]
cmds = ["./mvnw dependency:go-offline -B"]

[phases.build]
cmds = ["./mvnw clean package -DskipTests"]

[start]
cmd = "java -jar target/change-my-image-0.0.1-SNAPSHOT.jar"
```

### **5. Stripe Webhook Configuration**

1. **Go to Stripe Dashboard** → **Webhooks**
2. **Add endpoint**:
   - **URL**: `https://your-backend.railway.app/stripe/webhook`
   - **Events**: `checkout.session.completed`, `customer.subscription.*`
3. **Copy webhook secret** and add to backend environment variables

### **6. Google OAuth Setup**

1. **Go to Google Cloud Console**
2. **Add authorized redirect URI**:
   ```
   https://your-frontend.railway.app/api/auth/callback/google
   ```

### **7. Railway-Specific Optimizations**

#### Backend Optimizations:
```yaml
# Add to application-prod.yml
server:
  port: ${PORT:8080}
  tomcat:
    max-connections: 200
    accept-count: 50
    max-threads: 50
    min-spare-threads: 5

# Railway-specific settings
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

#### Frontend Optimizations:
```typescript
// In next.config.ts
const nextConfig: NextConfig = {
  // Railway optimizations
  images: {
    domains: ['your-backend.railway.app'],
    formats: ['image/webp', 'image/avif'],
  },
  
  // Enable experimental features
  experimental: {
    optimizeCss: true,
  }
}
```

### **8. Deployment Steps**

#### Step 1: Prepare Repository
```bash
# Make sure all changes are committed
git add .
git commit -m "Prepare for Railway deployment"
git push origin main
```

#### Step 2: Create Railway Project
1. **Go to Railway Dashboard**
2. **Click "New Project"**
3. **Select "Deploy from GitHub repo"**
4. **Choose your repository**

#### Step 3: Add Services
1. **Add PostgreSQL** (automatic)
2. **Add Backend** (Spring Boot)
3. **Add Frontend** (Next.js)

#### Step 4: Configure Environment Variables
1. **Set all required variables** in each service
2. **Use Railway's variable references** for service URLs

#### Step 5: Deploy
1. **Railway will automatically deploy** when you push to GitHub
2. **Check logs** for any issues
3. **Test your endpoints**

### **9. Railway Advantages**

#### ✅ **Why Railway is Great:**
- **🚀 Zero-config deployment** - Auto-detects frameworks
- **🔗 Service linking** - Automatic environment variables
- **📊 Built-in monitoring** - Logs, metrics, health checks
- **🔄 GitHub integration** - Auto-deploy on push
- **💰 Generous free tier** - $5 credit monthly
- **🌍 Global CDN** - Fast worldwide
- **🔒 Automatic HTTPS** - SSL certificates included
- **📱 Mobile app** - Deploy from your phone

#### 🎯 **Perfect for Full-Stack:**
- **Single project** for frontend + backend + database
- **Automatic service discovery** between services
- **Shared environment variables**
- **Unified logging and monitoring**

### **10. Testing Your Deployment**

#### Test Backend:
```bash
# Health check
curl https://your-backend.railway.app/health

# Test convert endpoint
curl -X POST https://your-backend.railway.app/api/convert/formats
```

#### Test Frontend:
1. **Visit your frontend URL**
2. **Test image upload**
3. **Test authentication**
4. **Test Stripe checkout**

### **11. Monitoring & Logs**

#### View Logs:
- **Railway Dashboard** → **Your Service** → **Logs**
- **Real-time logs** with filtering
- **Error tracking** and alerts

#### Monitor Performance:
- **CPU/Memory usage**
- **Response times**
- **Request counts**
- **Error rates**

### **12. Environment Variables Reference**

#### Backend Variables:
```bash
# Database (Railway provides automatically)
DATABASE_URL=${{PostgreSQL.DATABASE_URL}}
DATABASE_USERNAME=${{PostgreSQL.PGUSER}}
DATABASE_PASSWORD=${{PostgreSQL.PGPASSWORD}}

# Stripe
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com

# CORS
ALLOWED_ORIGINS=https://your-frontend.railway.app

# Production
SPRING_PROFILES_ACTIVE=prod
```

#### Frontend Variables:
```bash
# Backend URL (Railway provides automatically)
NEXT_PUBLIC_API_URL=${{Backend.RAILWAY_PUBLIC_DOMAIN}}

# Stripe
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...

# NextAuth
NEXTAUTH_URL=${{Frontend.RAILWAY_PUBLIC_DOMAIN}}
NEXTAUTH_SECRET=your-random-secret

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

### **13. Cost Optimization**

#### Free Tier:
- **$5 credit monthly**
- **500 hours of usage**
- **1GB RAM per service**
- **1GB database storage**

#### Paid Plans:
- **Pro**: $20/month for unlimited usage
- **Team**: $99/month for team features

### **14. Troubleshooting**

#### Common Issues:

**Build Fails:**
```bash
# Check Railway logs
# Verify build commands
# Check for missing dependencies
```

**Database Connection Failed:**
```bash
# Verify DATABASE_URL is set
# Check PostgreSQL service is running
# Verify connection string format
```

**CORS Errors:**
```bash
# Update ALLOWED_ORIGINS with frontend URL
# Check frontend is using correct backend URL
```

**Environment Variables Not Working:**
```bash
# Use Railway's variable references
# Check variable names are correct
# Redeploy after adding variables
```

### **15. Railway CLI (Optional)**

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login
railway login

# Link project
railway link

# Deploy
railway up

# View logs
railway logs

# Open in browser
railway open
```

### **16. Security Checklist**

- ✅ **Environment variables** set (no secrets in code)
- ✅ **HTTPS enabled** (automatic on Railway)
- ✅ **CORS configured** correctly
- ✅ **Database access** restricted
- ✅ **Stripe webhook** signature verification
- ✅ **Google OAuth** properly configured
- ✅ **Production profile** active
- ✅ **Error messages** sanitized

**📚 Complete Security Guide:** [SECURITY-GUIDE.md](SECURITY-GUIDE.md)

### **17. Backup & Recovery**

#### Database Backup:
- **Railway automatically backs up** PostgreSQL
- **Point-in-time recovery** available
- **Manual backup** via Railway dashboard

#### Code Backup:
- **GitHub repository** is your backup
- **Railway builds from GitHub** automatically

### **18. Scaling Considerations**

#### When to Upgrade:
- **More than $5 credit** usage
- **Need more RAM** (>1GB per service)
- **Need faster response times**
- **Need team collaboration**

#### Upgrade Path:
1. **Upgrade to Pro plan** ($20/month)
2. **Add more services** if needed
3. **Scale database** if needed
4. **Add monitoring** and alerting

---

## 🎯 Quick Start Commands

```bash
# 1. Create Railway project
# 2. Add PostgreSQL service
# 3. Add backend service
# 4. Add frontend service
# 5. Set environment variables
# 6. Deploy and test!

# Test your deployment
curl https://your-backend.railway.app/health
```

Your image converter will be live at:
- **Frontend**: `https://your-frontend.railway.app`
- **Backend**: `https://your-backend.railway.app`
- **Database**: Managed by Railway

## 🚂 Why Railway is Perfect for Your App

- **🎯 Full-stack deployment** in one place
- **🔗 Automatic service linking**
- **📊 Built-in monitoring**
- **🚀 Zero-config deployment**
- **💰 Generous free tier**
- **🌍 Global performance**

Railway makes deploying your image converter app incredibly easy! 🎉






