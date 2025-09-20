# Production Security Checklist

## ✅ Implemented Security Measures

### 1. **Security Headers**
- ✅ Content Security Policy (CSP) with strict directives
- ✅ HSTS with preload and 1-year max-age
- ✅ X-Frame-Options: DENY
- ✅ X-Content-Type-Options: nosniff
- ✅ Referrer-Policy: strict-origin-when-cross-origin
- ✅ Permissions-Policy (camera, microphone, geolocation disabled)
- ✅ Cross-Origin policies (COEP, COOP, CORP)

### 2. **Authentication & Authorization**
- ✅ Google OAuth2 with JWT token verification
- ✅ Environment-specific debug endpoint access
- ✅ Stateless API (no sessions/cookies)
- ✅ Bearer token authentication

### 3. **Input Validation & File Security**
- ✅ File size limits (8MB max)
- ✅ MIME type validation
- ✅ File extension whitelist
- ✅ Magic number validation (PNG, JPEG, WebP, AVIF)
- ✅ Suspicious content detection in file headers
- ✅ AVIF brand validation

### 4. **Rate Limiting**
- ✅ Separate buckets for general API and conversion endpoints
- ✅ 60 req/min anonymous, 300 req/min authenticated
- ✅ 10 conversions/min per user/IP
- ✅ Rate limit headers exposed to clients
- ✅ Security audit logging for rate limit violations

### 5. **CORS Configuration**
- ✅ Explicit allowed origins (no wildcards)
- ✅ Limited HTTP methods (GET, POST, OPTIONS)
- ✅ Specific allowed headers
- ✅ Credentials disabled

### 6. **Error Handling**
- ✅ No sensitive information in error responses
- ✅ No stack traces in production
- ✅ Generic error messages

### 7. **Logging & Monitoring**
- ✅ Security event logging
- ✅ Rate limit violation tracking
- ✅ File upload attempt logging
- ✅ Authentication attempt logging
- ✅ Structured log format

## 🔧 Required Environment Variables

```bash
# Database
DATABASE_URL=jdbc:postgresql://your-db-host:5432/imageconverter
DATABASE_USERNAME=your-db-user
DATABASE_PASSWORD=your-secure-password

# Stripe
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Google OAuth
GOOGLE_CLIENT_ID=your-google-client-id

# CORS
ALLOWED_ORIGINS=https://your-frontend-domain.com

# Server
PORT=8080
```

## 🚀 Production Deployment Steps

### 1. **Database Setup**
```sql
-- Create production database
CREATE DATABASE imageconverter;
CREATE USER imageconverter_user WITH PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE imageconverter TO imageconverter_user;
```

### 2. **Environment Configuration**
```bash
# Set production profile
export SPRING_PROFILES_ACTIVE=prod

# Set all required environment variables
export DATABASE_URL=...
export STRIPE_SECRET_KEY=...
# ... etc
```

### 3. **ImageMagick Installation**
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install imagemagick

# Verify installation
magick -version
```

### 4. **Log Directory Setup**
```bash
sudo mkdir -p /var/log/imageconverter
sudo chown your-app-user:your-app-user /var/log/imageconverter
```

### 5. **Reverse Proxy (Nginx)**
```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    # Security headers
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Rate limiting at proxy level
        limit_req zone=api burst=20 nodelay;
    }
}
```

### 6. **Firewall Configuration**
```bash
# Allow only necessary ports
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw deny 8080/tcp   # Block direct access to app
sudo ufw enable
```

## 🔍 Security Monitoring

### 1. **Log Monitoring**
Monitor these log patterns:
```bash
# Rate limit violations
grep "RATE_LIMIT_EXCEEDED" /var/log/imageconverter/application.log

# Security events
grep "SECURITY_EVENT" /var/log/imageconverter/application.log

# Failed authentication attempts
grep "AUTH_ATTEMPT.*FAILED" /var/log/imageconverter/application.log
```

### 2. **Health Checks**
```bash
# Application health
curl https://your-domain.com/actuator/health

# Metrics
curl https://your-domain.com/actuator/metrics
```

### 3. **Regular Security Tasks**
- [ ] Review security logs weekly
- [ ] Update dependencies monthly
- [ ] Rotate API keys quarterly
- [ ] Security audit annually

## ⚠️ Security Considerations

### 1. **Secrets Management**
- Use environment variables or secret management service
- Never commit secrets to version control
- Rotate keys regularly

### 2. **Database Security**
- Use connection pooling
- Enable SSL for database connections
- Regular backups with encryption

### 3. **Network Security**
- Use HTTPS only
- Implement WAF if possible
- Monitor for DDoS attacks

### 4. **Application Security**
- Keep dependencies updated
- Regular security scans
- Monitor for unusual traffic patterns

## 🆘 Incident Response

### 1. **Rate Limit Abuse**
- Check logs for patterns
- Consider IP blocking
- Adjust rate limits if needed

### 2. **Suspicious File Uploads**
- Review file validation logs
- Check for malware patterns
- Update file validation rules

### 3. **Authentication Issues**
- Monitor failed login attempts
- Check for credential stuffing
- Implement account lockout if needed

## 📊 Security Metrics to Track

- Rate limit violations per hour
- Failed authentication attempts
- File upload rejections
- Error rates by endpoint
- Response times
- Memory and CPU usage
