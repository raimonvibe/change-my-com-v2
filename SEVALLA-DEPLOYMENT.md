# 🚀 Sevala Deployment Guide - Complete Full-Stack Solution

## 🎯 Why Sevala is Perfect for Your App

Sevala is a **developer-focused Platform-as-a-Service (PaaS)** that's ideal for your image converter:

- ✅ **Full-stack deployment** (Backend + Frontend + Database)
- ✅ **$50 free credits** to get started
- ✅ **Git-based deployments** with automatic builds
- ✅ **Docker support** for complex applications
- ✅ **PostgreSQL database** hosting
- ✅ **Health checks** and monitoring
- ✅ **Custom domains** and SSL
- ✅ **Zero-downtime deployments**

## 🚀 Quick Start (5 minutes)

### **1. Create Sevala Account**
1. Go to [Sevala Dashboard](https://sevalla.com)
2. **Sign up** with GitHub
3. **Get $50 free credits** automatically

### **2. Deploy Backend (Spring Boot)**

#### **Create Backend Application:**
1. **Click "Applications"** → **"Create an app"**
2. **Connect GitHub repository**
3. **Configure backend settings**:
   - **Name**: `imageconverter-backend`
   - **Root Directory**: `backend`
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java -jar target/change-my-image-0.0.1-SNAPSHOT.jar`
   - **Port**: `8080`

#### **Set Backend Environment Variables:**
```bash
# Spring Security (CRITICAL - fixes the warning)
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password

# Database (will be set after creating database)
DATABASE_URL=jdbc:postgresql://your-db-host:5432/imageconverter
DATABASE_USERNAME=your-db-user
DATABASE_PASSWORD=your-db-password

# Stripe (LIVE keys for production)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com

# CORS (will be updated after frontend deployment)
ALLOWED_ORIGINS=https://your-frontend.sevalla.app

# Production Profile
SPRING_PROFILES_ACTIVE=prod
```

### **3. Create PostgreSQL Database**

#### **Add Database:**
1. **Go to "Databases"** → **"Add database"**
2. **Select PostgreSQL**
3. **Configure database**:
   - **Name**: `imageconverter-db`
   - **Region**: Same as your backend
   - **Size**: Start with smallest (upgrade as needed)

#### **Connect Database to Backend:**
1. **Go to your backend application**
2. **Settings** → **Environment Variables**
3. **Add database connection variables**:
   ```bash
   DATABASE_URL=jdbc:postgresql://your-db-host:5432/imageconverter
   DATABASE_USERNAME=your-db-user
   DATABASE_PASSWORD=your-db-password
   ```

### **4. Deploy Frontend (Next.js)**

#### **Create Frontend Application:**
1. **Click "Applications"** → **"Create an app"**
2. **Connect same GitHub repository**
3. **Configure frontend settings**:
   - **Name**: `imageconverter-frontend`
   - **Root Directory**: `frontend`
   - **Build Command**: `npm install && npm run build`
   - **Start Command**: `npm start`
   - **Port**: `3000`

#### **Set Frontend Environment Variables:**
```bash
# Backend API URL (your Sevala backend URL)
NEXT_PUBLIC_API_URL=https://your-backend.sevalla.app

# Stripe (same as backend)
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...

# NextAuth
NEXTAUTH_URL=https://your-frontend.sevalla.app
NEXTAUTH_SECRET=your-random-secret

# Google OAuth (same as backend)
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

### **5. Update CORS Configuration**

#### **Update Backend CORS:**
1. **Go to backend application**
2. **Settings** → **Environment Variables**
3. **Update ALLOWED_ORIGINS**:
   ```bash
   ALLOWED_ORIGINS=https://your-frontend.sevalla.app
   ```

### **6. Configure External Services**

#### **Stripe Webhook:**
1. **Go to Stripe Dashboard** → **Webhooks**
2. **Add endpoint**: `https://your-backend.sevalla.app/stripe/webhook`
3. **Select events**: `checkout.session.completed`, `customer.subscription.*`
4. **Copy webhook secret** to `STRIPE_WEBHOOK_SECRET`

#### **Google OAuth:**
1. **Go to Google Cloud Console**
2. **Credentials** → **OAuth 2.0 Client**
3. **Add redirect URI**: `https://your-frontend.sevalla.app/api/auth/callback/google`

## 🔧 Advanced Configuration

### **Health Checks**

#### **Backend Health Check:**
Sevala will automatically check: `https://your-backend.sevalla.app/health`

#### **Frontend Health Check:**
Sevala will automatically check: `https://your-frontend.sevalla.app`

### **Custom Domains**

#### **Add Custom Domain:**
1. **Go to your application**
2. **Settings** → **Domains**
3. **Add custom domain**
4. **Sevala provides SSL** automatically

### **Environment-Specific Configuration**

#### **Development Environment:**
```bash
# Use dev profile for testing
SPRING_PROFILES_ACTIVE=dev

# Allow localhost for development
ALLOWED_ORIGINS=http://localhost:3000,https://your-frontend.sevalla.app
```

#### **Production Environment:**
```bash
# Use prod profile for production
SPRING_PROFILES_ACTIVE=prod

# Only allow production domains
ALLOWED_ORIGINS=https://your-frontend.sevalla.app
```

## 📊 Monitoring and Logs

### **Application Monitoring:**
1. **Go to your application dashboard**
2. **View real-time logs**
3. **Monitor resource usage**
4. **Check deployment status**

### **Health Check Monitoring:**
- **Sevala monitors** health checks every 10 seconds
- **Automatic restarts** if health check fails 3 times
- **Zero-downtime deployments** with health checks

### **Deployment Notifications:**
1. **Go to User Settings** → **Notifications**
2. **Enable email notifications** for failed deployments
3. **Get alerts** when deployments fail

## 🚨 Troubleshooting

### **Common Issues:**

#### **Build Failures:**
```bash
# Check build logs in Sevala dashboard
# Common fixes:
# 1. Ensure Maven wrapper is executable
# 2. Check Java version compatibility
# 3. Verify build command syntax
```

#### **Database Connection Issues:**
```bash
# Verify database environment variables
# Check database status in Sevala dashboard
# Ensure database is in same region as backend
```

#### **CORS Errors:**
```bash
# Update ALLOWED_ORIGINS in backend
# Ensure frontend URL is correct
# Check for trailing slashes in URLs
```

#### **Stripe Webhook Issues:**
```bash
# Verify webhook URL in Stripe dashboard
# Check webhook secret is correct
# Ensure webhook endpoint is accessible
```

### **Debug Mode:**
```bash
# Enable debug logging in backend
SPRING_PROFILES_ACTIVE=dev
LOGGING_LEVEL_COM_RAIMONVIBE=DEBUG
```

## 💰 Pricing and Limits

### **Free Credits:**
- ✅ **$50 free credits** to start
- ✅ **No credit card required** initially
- ✅ **Pay-as-you-go** pricing

### **Resource Limits:**
- **Applications**: Unlimited
- **Databases**: Pay per usage
- **Bandwidth**: Included in pricing
- **Storage**: Pay per usage

### **Scaling:**
- **Automatic scaling** based on demand
- **Manual scaling** available
- **Resource monitoring** in dashboard

## 🎯 Sevala vs Other Platforms

### **✅ Sevala Advantages:**
- **🎯 Developer-focused** - Built for developers
- **💰 $50 free credits** - Generous starting credit
- **🔧 Docker support** - Flexible deployment options
- **📊 Built-in monitoring** - Health checks and logs
- **🌍 Global infrastructure** - Fast worldwide performance
- **🔄 Git-based deployments** - Automatic builds from GitHub

### **🆚 Comparison:**
| Feature | Sevala | Railway | Render | Vercel |
|---------|--------|---------|--------|--------|
| **Full-stack** | ✅ | ✅ | ✅ | ❌ |
| **Free Credits** | $50 | $5/month | Limited | 100GB |
| **Docker Support** | ✅ | ✅ | ✅ | ❌ |
| **Database Hosting** | ✅ | ✅ | ✅ | ❌ |
| **Health Checks** | ✅ | ✅ | ✅ | ❌ |
| **Custom Domains** | ✅ | ✅ | ✅ | ✅ |

## 🚀 Deployment Checklist

### **✅ Pre-Deployment:**
- [ ] **Code pushed** to GitHub
- [ ] **Environment variables** prepared
- [ ] **External services** configured (Stripe, Google)
- [ ] **Database schema** ready

### **✅ Deployment:**
- [ ] **Backend deployed** and healthy
- [ ] **Database created** and connected
- [ ] **Frontend deployed** and healthy
- [ ] **CORS configured** correctly
- [ ] **External services** updated with new URLs

### **✅ Post-Deployment:**
- [ ] **Health checks** passing
- [ ] **Image conversion** working
- [ ] **Google OAuth** working
- [ ] **Stripe checkout** working
- [ ] **No security warnings** in logs
- [ ] **Custom domain** configured (if needed)

## 🎉 You're Ready to Deploy on Sevala!

### **Quick Start Commands:**
```bash
# 1. Push your code to GitHub
git add .
git commit -m "Ready for Sevala deployment"
git push origin main

# 2. Go to Sevala dashboard
# 3. Create backend application
# 4. Create database
# 5. Create frontend application
# 6. Set environment variables
# 7. Configure external services
# 8. Deploy! 🚀
```

### **Sevala is Perfect for:**
- ✅ **Full-stack applications** like yours
- ✅ **Java/Spring Boot** backends
- ✅ **Next.js** frontends
- ✅ **PostgreSQL** databases
- ✅ **Docker-based** deployments
- ✅ **Developer-friendly** workflows

Your image converter app will run beautifully on Sevala! 🎯

## 📞 Need Help?

1. **Check Sevala documentation**: [docs.sevalla.com](https://docs.sevalla.com)
2. **View application logs** in Sevala dashboard
3. **Monitor health checks** and resource usage
4. **Test each service** individually
5. **Verify external service configurations**

Happy deploying on Sevala! 🚀✨



