# Secrets Management Guide

## Quick Setup

1. **Copy the example environment file:**
   ```bash
   cp .env.example .env
   ```

2. **Fill in your actual values** in the `.env` file:
   - Get Stripe keys from: https://dashboard.stripe.com/test/apikeys
   - Get Google OAuth credentials from: https://console.cloud.google.com/
   - Set your own admin password

3. **Never commit `.env` to git** - it's already in `.gitignore`

## Environment Variables

### Backend (.env in root or backend/)

```bash
# Spring Security
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=your-secure-password

# Google OAuth
GOOGLE_CLIENT_ID=your-google-client-id

# Stripe API Keys
STRIPE_SECRET_KEY=sk_test_your_key_here
STRIPE_PUBLISHABLE_KEY=pk_test_your_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret

# CORS
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

## Security Best Practices

### ✅ DO:
- Use `.env.example` as a template (safe to commit)
- Store actual secrets in `.env` files (never commit)
- Use different keys for development and production
- Rotate keys immediately if leaked
- Use environment variables in CI/CD (GitHub Secrets, etc.)

### ❌ DON'T:
- Never commit `.env` files
- Never hardcode secrets in source code
- Never share secrets via email or chat
- Never commit files like `setup-local.sh` with actual keys

## GitHub Actions Secrets

For CI/CD, add secrets in GitHub:
1. Go to: Settings → Secrets and variables → Actions
2. Add repository secrets:
   - `STRIPE_SECRET_KEY`
   - `GOOGLE_CLIENT_ID`
   - etc.

## What to Do If You Leak a Secret

1. **Immediately rotate/revoke the key** at the service provider
2. **Remove from git history** using `git-filter-repo`
3. **Force push** to update remote repository
4. **Notify collaborators** to re-clone the repository
5. **Close the GitHub security alert** as "Revoked"

## Production Deployment

For production, use proper secrets management:
- **Heroku**: Config Vars in dashboard
- **AWS**: Systems Manager Parameter Store or Secrets Manager
- **Docker**: Environment variables or Docker secrets
- **Kubernetes**: Kubernetes Secrets

Never store production secrets in code or commit them to version control.
