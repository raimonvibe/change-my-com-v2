# 🚀 Deployment Guide - Image Converter

## 🎯 Choose Your Deployment Platform

This guide helps you deploy your full-stack image converter application. Choose the platform that best fits your needs:

---

## Platform Comparison

| Platform | Best For | Free Tier | Backend | Frontend | Database | Documentation |
|----------|----------|-----------|---------|----------|----------|---------------|
| **Railway** | Complete solution in one place | $5/month credit | ✅ | ✅ | ✅ | [Railway Guide →](RAILWAY-DEPLOYMENT.md) |
| **Sevalla** | Developer-focused workflows | $50 free credits | ✅ | ✅ | ✅ | [Sevalla Guide →](SEVALLA-DEPLOYMENT.md) |
| **Render** | Backend + Database hosting | Limited free tier | ✅ | ✅ | ✅ | [Render Guide →](RENDER-DEPLOYMENT.md) |
| **Vercel** | Frontend performance (hybrid) | 100GB bandwidth | ❌ | ✅ | ❌ | [Vercel Guide →](VERCEL-DEPLOYMENT.md) |

---

## Deployment Options

### 🥇 Option 1: Railway (Recommended for Beginners)

**Perfect for:** Getting started quickly with everything in one place

**Advantages:**
- ✅ One-click full-stack deployment
- ✅ Automatic GitHub deployments
- ✅ Built-in PostgreSQL database
- ✅ Integrated monitoring and logs
- ✅ Custom domains with automatic SSL
- ✅ $5 monthly credit (generous free tier)

**Quick Start:**
1. Sign up at [railway.app](https://railway.app)
2. Connect your GitHub repository
3. Railway auto-detects services
4. Set environment variables
5. Deploy!

**📚 Full Documentation:** [RAILWAY-DEPLOYMENT.md](RAILWAY-DEPLOYMENT.md)

---

### 🥈 Option 2: Sevalla (Best for Developers)

**Perfect for:** Developer-focused workflows with Docker support

**Advantages:**
- ✅ $50 free credits to start
- ✅ Docker and Git-based deployments
- ✅ Advanced health checks
- ✅ Zero-downtime deployments
- ✅ Developer-centric platform
- ✅ Flexible scaling options

**Quick Start:**
1. Sign up at [sevalla.com](https://sevalla.com)
2. Get $50 free credits automatically
3. Create backend, database, and frontend apps
4. Configure Git deployments
5. Deploy!

**📚 Full Documentation:** [SEVALLA-DEPLOYMENT.md](SEVALLA-DEPLOYMENT.md)

---

### 🥉 Option 3: Hybrid (Best Performance)

**Perfect for:** Maximum frontend performance with specialized hosting

**Recommended Combinations:**
- **Railway (Backend + DB) + Vercel (Frontend)**
- **Render (Backend + DB) + Vercel (Frontend)**

**Advantages:**
- ✅ Vercel's optimized Next.js hosting
- ✅ Global edge network for frontend
- ✅ Specialized platforms for each service
- ✅ Best-in-class performance

**Setup Steps:**
1. Deploy backend + database on Railway or Render
2. Deploy frontend on Vercel
3. Update CORS and environment variables
4. Configure cross-origin communication

**📚 Documentation:**
- [Render Backend Guide →](RENDER-DEPLOYMENT.md)
- [Vercel Frontend Guide →](VERCEL-DEPLOYMENT.md)

---

## 🔧 Environment Variables Reference

All platforms require these environment variables. See [SECRETS-MANAGEMENT.md](SECRETS-MANAGEMENT.md) for detailed configuration.

### Backend Variables
```bash
# Spring Security
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password

# Database (auto-provided by platform)
DATABASE_URL=jdbc:postgresql://...
DATABASE_USERNAME=...
DATABASE_PASSWORD=...

# Stripe
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com

# CORS
ALLOWED_ORIGINS=https://your-frontend-domain.com

# Profile
SPRING_PROFILES_ACTIVE=prod
```

### Frontend Variables
```bash
# API
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

---

## 🔒 Security Configuration

Before deploying to production, ensure you've applied all security measures:

**📚 Complete Security Guide:** [SECURITY-GUIDE.md](SECURITY-GUIDE.md)

**Quick Security Checklist:**
- [ ] ImageMagick security policy deployed
- [ ] Database SSL enabled
- [ ] All environment variables set
- [ ] No secrets in code
- [ ] CORS properly configured
- [ ] Stripe webhook signature verification
- [ ] Production profile active
- [ ] Security headers enabled

---

## 📋 Post-Deployment Checklist

After deploying, verify everything works:

### ✅ Service Health
- [ ] Backend health endpoint: `https://your-backend/health`
- [ ] Frontend loads successfully
- [ ] Database connection working

### ✅ Functionality Tests
- [ ] Image upload and conversion works
- [ ] Google OAuth authentication works
- [ ] Stripe checkout flow works
- [ ] Rate limiting active
- [ ] No security warnings in logs

### ✅ External Services
- [ ] Stripe webhook configured: `https://your-backend/stripe/webhook`
- [ ] Google OAuth redirect URI: `https://your-frontend/api/auth/callback/google`
- [ ] Custom domain configured (if applicable)
- [ ] SSL certificate active

---

## 🚨 Common Issues & Solutions

### Database Connection Failed
- Verify DATABASE_URL is correct
- Check database service is running
- Ensure SSL is properly configured

### CORS Errors
- Update ALLOWED_ORIGINS with frontend URL
- Check frontend uses correct backend URL
- Verify no trailing slashes mismatch

### Stripe Webhook Failed
- Update webhook URL in Stripe dashboard
- Verify webhook secret is correct
- Test webhook in Stripe dashboard

### Google OAuth Failed
- Check redirect URIs in Google Console
- Verify client ID and secret are correct
- Ensure domain is authorized

---

## 🎯 Recommended Deployment Path

### For Most Users:
1. **Start with Railway** - Easiest full-stack deployment
2. **Deploy everything in one place**
3. **Test thoroughly**
4. **Upgrade if needed**

### For Maximum Performance:
1. **Deploy backend on Railway/Render**
2. **Deploy frontend on Vercel**
3. **Configure cross-origin communication**
4. **Optimize with CDN and edge functions**

### For Developer Workflows:
1. **Use Sevalla** for Git-based deployments
2. **Leverage Docker support**
3. **Configure health checks**
4. **Scale as needed**

---

## 📚 Additional Resources

- **Setup Guide:** [SETUP.md](SETUP.md) - Local development setup
- **Security Guide:** [SECURITY-GUIDE.md](SECURITY-GUIDE.md) - Production security
- **Secrets Management:** [SECRETS-MANAGEMENT.md](SECRETS-MANAGEMENT.md) - Environment variables
- **Quick Start:** [DEPLOYMENT-QUICK-START.md](DEPLOYMENT-QUICK-START.md) - Automated deployment script

---

## 📞 Need Help?

1. Check platform-specific logs in your dashboard
2. Verify all environment variables are set correctly
3. Test each service individually
4. Review external service configurations
5. Check security warnings in logs

Choose your platform and follow the detailed guide for step-by-step instructions. Happy deploying! 🚀
