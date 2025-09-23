# 🚀 Complete Deployment Guide - Image Converter

## 🎯 Choose Your Deployment Strategy

### **Option 1: Railway (Recommended) - Everything in One Place**
- ✅ **Full-stack deployment** (Backend + Frontend + Database)
- ✅ **One-click setup** with blueprint
- ✅ **Automatic deployments** from GitHub
- ✅ **Built-in monitoring** and logs
- ✅ **Custom domains** and SSL
- ✅ **$5 credit monthly** (generous free tier)

### **Option 2: Sevala (Developer-Focused) - Docker & Git**
- ✅ **Full-stack deployment** (Backend + Frontend + Database)
- ✅ **$50 free credits** to start
- ✅ **Docker support** for complex applications
- ✅ **Git-based deployments** with automatic builds
- ✅ **Health checks** and zero-downtime deployments
- ✅ **Developer-focused** platform

### **Option 3: Hybrid (Railway + Vercel) - Best Performance**
- ✅ **Railway**: Backend + Database
- ✅ **Vercel**: Frontend (optimized for Next.js)
- ✅ **Best of both worlds** - Railway's simplicity + Vercel's frontend performance

### **Option 4: Render + Vercel - Alternative**
- ✅ **Render**: Backend + Database
- ✅ **Vercel**: Frontend
- ✅ **Good alternative** if you prefer Render

## 🚂 Option 1: Railway (Complete Solution)

### **Quick Start (5 minutes):**

#### **1. One-Click Deployment:**
1. Go to [Railway Dashboard](https://railway.app)
2. Click **"New Project"** → **"Deploy from GitHub repo"**
3. Select your repository
4. Railway will auto-detect and create all services

#### **2. Set Environment Variables:**
In Railway dashboard, add these to your backend service:

```bash
# Spring Security (CRITICAL - fixes the warning)
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password

# Stripe (LIVE keys for production)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com

# Production Profile
SPRING_PROFILES_ACTIVE=prod
```

And these to your frontend service:

```bash
# Stripe
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...

# NextAuth
NEXTAUTH_SECRET=your-random-secret

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

#### **3. Configure External Services:**

**Stripe Webhook:**
- URL: `https://your-backend.railway.app/stripe/webhook`
- Events: `checkout.session.completed`, `customer.subscription.*`

**Google OAuth:**
- Redirect URI: `https://your-frontend.railway.app/api/auth/callback/google`

#### **4. Custom Domain (Optional):**
1. Go to your frontend service
2. Click **"Settings"** → **"Domains"**
3. Add your custom domain
4. Railway provides SSL automatically

### **Railway Advantages:**
- 🎯 **Single platform** for everything
- 🔄 **Automatic deployments** from GitHub
- 📊 **Built-in monitoring** and logs
- 🔒 **Automatic SSL** certificates
- 🌍 **Global CDN** for static assets
- 💰 **Generous free tier** ($5 credit monthly)

## 🚀 Option 2: Sevala (Developer-Focused)

### **Quick Start (5 minutes):**

#### **1. Create Sevala Account:**
1. Go to [Sevala Dashboard](https://sevalla.com)
2. **Sign up** with GitHub
3. **Get $50 free credits** automatically

#### **2. Deploy Backend:**
1. **Click "Applications"** → **"Create an app"**
2. **Connect GitHub repository**
3. **Configure backend**:
   - **Name**: `imageconverter-backend`
   - **Root Directory**: `backend`
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java -jar target/change-my-image-0.0.1-SNAPSHOT.jar`

#### **3. Create Database:**
1. **Go to "Databases"** → **"Add database"**
2. **Select PostgreSQL**
3. **Connect to backend** with environment variables

#### **4. Deploy Frontend:**
1. **Create new application**
2. **Configure frontend**:
   - **Name**: `imageconverter-frontend`
   - **Root Directory**: `frontend`
   - **Build Command**: `npm install && npm run build`
   - **Start Command**: `npm start`

#### **5. Set Environment Variables:**
**Backend:**
```bash
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
SPRING_PROFILES_ACTIVE=prod
```

**Frontend:**
```bash
NEXT_PUBLIC_API_URL=https://your-backend.sevalla.app
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...
NEXTAUTH_URL=https://your-frontend.sevalla.app
NEXTAUTH_SECRET=your-random-secret
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

### **Sevala Advantages:**
- 🎯 **Developer-focused** platform
- 💰 **$50 free credits** to start
- 🐳 **Docker support** for complex applications
- 🔄 **Git-based deployments** with automatic builds
- 📊 **Health checks** and zero-downtime deployments
- 🌍 **Global infrastructure** with fast performance

## 🚂⚡ Option 3: Railway + Vercel (Best Performance)

### **Setup:**

#### **1. Deploy Backend + Database on Railway:**
1. Create Railway project
2. Add PostgreSQL service
3. Add backend service (Spring Boot)
4. Set backend environment variables

#### **2. Deploy Frontend on Vercel:**
1. Go to [Vercel Dashboard](https://vercel.com)
2. Import your GitHub repository
3. Set Root Directory: `frontend`
4. Add frontend environment variables:

```bash
NEXT_PUBLIC_API_URL=https://your-backend.railway.app
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...
NEXTAUTH_URL=https://your-frontend.vercel.app
NEXTAUTH_SECRET=your-random-secret
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

#### **3. Update Backend CORS:**
In Railway backend environment variables:
```bash
ALLOWED_ORIGINS=https://your-frontend.vercel.app
```

### **Hybrid Advantages:**
- ⚡ **Vercel's frontend performance** (built for Next.js)
- 🚂 **Railway's backend simplicity**
- 🌍 **Global edge network** for frontend
- 📊 **Advanced analytics** from Vercel
- 🔄 **Automatic deployments** for both

## 🎨 Option 3: Render + Vercel (Alternative)

### **Setup:**

#### **1. Deploy Backend + Database on Render:**
1. Create PostgreSQL database
2. Create backend web service
3. Set backend environment variables

#### **2. Deploy Frontend on Vercel:**
1. Import repository to Vercel
2. Set Root Directory: `frontend`
3. Add frontend environment variables

### **Render + Vercel Advantages:**
- 🎯 **Specialized platforms** for each service
- ⚡ **Vercel's frontend optimization**
- 🔧 **Render's backend reliability**
- 💰 **Good free tiers** for both

## 🔧 Environment Variables Reference

### **Backend (All Platforms):**
```bash
# Spring Security (CRITICAL)
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password

# Database (auto-provided by platform)
DATABASE_URL=jdbc:postgresql://...
DATABASE_USERNAME=...
DATABASE_PASSWORD=...

# Stripe (LIVE keys for production)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com

# CORS (your frontend URL)
ALLOWED_ORIGINS=https://your-frontend-domain.com

# Production Profile
SPRING_PROFILES_ACTIVE=prod
```

### **Frontend (All Platforms):**
```bash
# Backend API URL
NEXT_PUBLIC_API_URL=https://your-backend-domain.com

# Stripe
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...

# NextAuth
NEXTAUTH_URL=https://your-frontend-domain.com
NEXTAUTH_SECRET=your-random-secret

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

## 🎯 My Recommendations

### **🥇 Top Choice: Railway (Option 1)**
**Perfect for getting started quickly:**
1. **🎯 Complete Solution**: Everything in one place
2. **🚀 Easy Setup**: One-click deployment
3. **🔄 Automatic Deployments**: Push to GitHub = auto-deploy
4. **📊 Built-in Monitoring**: Logs, metrics, health checks
5. **💰 Cost-Effective**: $5 credit monthly covers most usage

### **🥈 Great Alternative: Sevala (Option 2)**
**Perfect for developer-focused workflows:**
1. **🎯 Developer-Focused**: Built specifically for developers
2. **💰 $50 Free Credits**: More generous starting credit
3. **🐳 Docker Support**: Flexible deployment options
4. **📊 Health Checks**: Zero-downtime deployments
5. **🔄 Git-Based**: Automatic builds from GitHub

### **🥉 Performance Option: Railway + Vercel (Option 3)**
**Perfect for maximum frontend performance:**
1. **⚡ Vercel Frontend**: Optimized for Next.js
2. **🚂 Railway Backend**: Simple backend hosting
3. **🌍 Global CDN**: Fast worldwide performance
4. **📊 Advanced Analytics**: Vercel's performance insights

### **Quick Setup (Any Option):**
```bash
# 1. Push your code to GitHub
git add .
git commit -m "Ready for deployment"
git push origin main

# 2. Choose your platform:
#    - Railway: Go to railway.app
#    - Sevala: Go to sevalla.com
#    - Vercel: Go to vercel.com

# 3. Deploy from GitHub repo
# 4. Set environment variables
# 5. Configure external services (Stripe, Google)
# 6. Done! 🎉
```

## 🔍 Post-Deployment Checklist

### **✅ Test Everything:**
- [ ] **Backend health**: `https://your-backend.railway.app/health`
- [ ] **Frontend loads**: `https://your-frontend.railway.app`
- [ ] **File upload works**: Test image conversion
- [ ] **Google OAuth works**: Test authentication
- [ ] **Stripe checkout works**: Test payment flow
- [ ] **No security warnings**: Check backend logs

### **✅ Configure External Services:**
- [ ] **Stripe webhook**: Update URL to production
- [ ] **Google OAuth**: Add production redirect URI
- [ ] **Custom domain**: Set up if needed
- [ ] **SSL certificate**: Should be automatic

### **✅ Monitor:**
- [ ] **Check logs**: Monitor for errors
- [ ] **Test performance**: Ensure fast response times
- [ ] **Monitor usage**: Track resource consumption
- [ ] **Set up alerts**: For downtime or errors

## 🚨 Troubleshooting

### **Common Issues:**

#### **Spring Security Warning:**
```bash
# Problem: "Using generated security password"
# Solution: Set SPRING_SECURITY_USER_PASSWORD
SPRING_SECURITY_USER_PASSWORD=your-secure-password
```

#### **CORS Errors:**
```bash
# Problem: Frontend cannot connect to backend
# Solution: Update ALLOWED_ORIGINS
ALLOWED_ORIGINS=https://your-frontend-domain.com
```

#### **Database Connection Failed:**
```bash
# Problem: Cannot connect to database
# Solution: Check DATABASE_URL (usually auto-provided)
```

#### **Stripe Webhook Failed:**
```bash
# Problem: Webhook not receiving events
# Solution: Update webhook URL in Stripe dashboard
```

## 🎉 You're Ready to Deploy!

### **Recommended Path:**
1. **Start with Railway** (Option 1) - easiest and most complete
2. **If you need more frontend performance**, consider Railway + Vercel (Option 2)
3. **If you prefer specialized platforms**, use Render + Vercel (Option 3)

### **Railway is perfect for:**
- ✅ **Getting started quickly**
- ✅ **Full-stack applications**
- ✅ **Automatic deployments**
- ✅ **Built-in monitoring**
- ✅ **Cost-effective hosting**

Your image converter app is ready for production deployment! 🚀

## 📞 Need Help?

1. **Check the logs** in your deployment platform
2. **Verify environment variables** are set correctly
3. **Test each service** individually
4. **Check external service configurations** (Stripe, Google)
5. **Monitor for security warnings** in backend logs

Happy deploying! 🎯
