# Quick Deployment Guide

## 🚀 Automated Deployment (Recommended)

### Run the deployment script with sudo:

```bash
sudo ./deploy-security.sh
```

This will:
- ✅ Deploy ImageMagick security policy
- ✅ Create log directory
- ✅ Show environment variables to set

### Verify deployment:

```bash
./verify-security.sh
```

---

## 📋 Manual Deployment Steps

### Step 1: Deploy ImageMagick Security Policy

```bash
# For ImageMagick 7
sudo cp backend/src/main/resources/imagemagick-policy.xml /etc/ImageMagick-7/policy.xml

# For ImageMagick 6 (Ubuntu/Debian default)
sudo cp backend/src/main/resources/imagemagick-policy.xml /etc/ImageMagick-6/policy.xml

# Set proper permissions
sudo chmod 644 /etc/ImageMagick-*/policy.xml
```

### Step 2: Set Environment Variables

Add to `/etc/environment` or `~/.bashrc`:

```bash
# Production Profile
export SPRING_PROFILES_ACTIVE=prod

# Database (update with your credentials)
export DATABASE_URL='jdbc:postgresql://your-host:5432/your-db?sslmode=require'
export DATABASE_USERNAME='your-username'
export DATABASE_PASSWORD='your-password'

# Google OAuth
export GOOGLE_CLIENT_ID='your-google-client-id'

# Stripe
export STRIPE_SECRET_KEY='sk_live_...'
export STRIPE_PUBLISHABLE_KEY='pk_live_...'
export STRIPE_WEBHOOK_SECRET='whsec_...'

# CORS Origins (frontend URL)
export ALLOWED_ORIGINS='https://your-frontend-domain.com'
```

Reload environment:
```bash
source ~/.bashrc
# or
source /etc/environment
```

### Step 3: Create Log Directory

```bash
sudo mkdir -p /app/logs
sudo chown $USER:$USER /app/logs
sudo chmod 755 /app/logs
```

### Step 4: Build and Run Application

```bash
# Build backend
cd backend
./mvnw clean package -DskipTests

# Run with production profile
java -jar target/*.jar --spring.profiles.active=prod
```

---

## ✅ Verification

Run the verification script:

```bash
./verify-security.sh
```

Expected output:
```
✓ ImageMagick installed
✓ Security policy deployed
✓ Production profile set
✓ Database SSL enforced
✓ All environment variables set
✓ Log directory configured
```

---

## 🔍 Test Security Features

### Test ImageMagick Policy

```bash
# This should be BLOCKED (returns error)
convert https://example.com/image.jpg test.jpg
# Expected: "not authorized" or "security policy" error

# This should WORK
convert input.jpg -quality 85 output.webp
# Expected: successful conversion
```

### Test Application

```bash
# Check health endpoint
curl http://localhost:8080/health

# Check security headers
curl -I http://localhost:8080/health | grep -i "x-frame\|hsts\|csp"
```

---

## 🆘 Troubleshooting

### ImageMagick policy not working

```bash
# Check which ImageMagick version is installed
convert -version | grep Version

# List active policy
magick -list policy
# or for ImageMagick 6:
ls -l /etc/ImageMagick-6/policy.xml
```

### Environment variables not loading

```bash
# Check if variables are set
echo $SPRING_PROFILES_ACTIVE
echo $DATABASE_URL

# Add to shell profile
nano ~/.bashrc
# Add export statements, then:
source ~/.bashrc
```

### Log directory permission issues

```bash
# Fix ownership
sudo chown -R $USER:$USER /app/logs
sudo chmod 755 /app/logs

# Check if writable
touch /app/logs/test.log && rm /app/logs/test.log
```

### Database SSL connection fails

```bash
# Verify PostgreSQL has SSL enabled
psql "sslmode=require host=your-host dbname=your-db" -c "SHOW ssl;"
# Should return: ssl | on

# If SSL is not available on your database, temporarily use:
export DATABASE_URL='jdbc:postgresql://host:5432/db'
# (Not recommended for production)
```

---

## 📚 Additional Documentation

- Security guide: [SECURITY-GUIDE.md](SECURITY-GUIDE.md)
- Deployment overview: [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md)
- Secrets management: [SECRETS-MANAGEMENT.md](SECRETS-MANAGEMENT.md)
- Platform-specific guides:
  - [RAILWAY-DEPLOYMENT.md](RAILWAY-DEPLOYMENT.md)
  - [RENDER-DEPLOYMENT.md](RENDER-DEPLOYMENT.md)
  - [VERCEL-DEPLOYMENT.md](VERCEL-DEPLOYMENT.md)
  - [SEVALLA-DEPLOYMENT.md](SEVALLA-DEPLOYMENT.md)

---

## 🔒 Production Checklist

Before going live:

- [ ] ImageMagick security policy deployed
- [ ] All environment variables set
- [ ] Database SSL enabled
- [ ] Log rotation configured
- [ ] HTTPS enabled (use reverse proxy like Nginx)
- [ ] Firewall configured
- [ ] Secrets rotated from defaults
- [ ] Backups configured
- [ ] Monitoring set up

---

## 🎯 Quick Commands Reference

```bash
# Deploy security
sudo ./deploy-security.sh

# Verify security
./verify-security.sh

# Build backend
cd backend && ./mvnw clean package

# Run production
java -jar target/*.jar --spring.profiles.active=prod

# View logs
tail -f /app/logs/application.log

# Monitor security
grep "validation failed\|denied" /app/logs/application.log
```
