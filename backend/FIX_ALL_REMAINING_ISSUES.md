# Remaining Security Issues - Batch Fix Guide

## ISSUE #4: PII Email Logging - GDPR Violation

### Files to Fix:
1. `StripeWebhookController.java` - Lines 127, 136, 172, 205, 230
2. `UserController.java` - Lines 23, 31, 45, 72, 80

### Fix Pattern:
```java
// BEFORE (BAD - GDPR violation):
logger.info("User: {}", user.getEmail());

// AFTER (GOOD):
logger.info("User ID: {}", user.getId());
```

## ISSUE #5: Webhook Secret Logging

### File: `StripeWebhookController.java:55`
```java
// REMOVE THIS LINE:
logger.error("Webhook secret configured: {}", webhookSecret != null && !webhookSecret.isEmpty());
```

## ISSUE #2: Console.error Logging

### File: `frontend/src/app/api/auth/[...nextauth]/route.ts:66`
```typescript
// REMOVE OR REPLACE:
console.error("Error refreshing access token", error);

// WITH (if needed):
// Server-side logging only, no details
if (process.env.NODE_ENV === 'development') {
  console.log("Token refresh failed");
}
```

## ISSUE #6: Webhook Rate Limiting

### File: `SecurityConfig.java`
Add webhook to rate limiting:
```java
// In RateLimitFilter.java line 70:
boolean isConvertRequest = ("POST".equals(request.getMethod()) &&
    (requestPath.equals("/api/convert") ||
     requestPath.equals("/api/convert/gif") ||
     requestPath.equals("/stripe/webhook")));  // ADD THIS
```

## ISSUE #9: Circular Imports

### File: `User.java:2-5`
```java
// REMOVE these lines:
import com.raimonvibe.imageconverter.user.UserRepository;
import com.raimonvibe.imageconverter.user.UserService;
import com.raimonvibe.imageconverter.user.AnonymousUserService;
import com.raimonvibe.imageconverter.user.User;  // Self-import!
```

## ISSUE #14: Prometheus Security

### File: `application-prod.yml:69`
```yaml
# CHANGE:
include: health,info,metrics,prometheus

# TO:
include: health,info
# Move metrics/prometheus to separate secured endpoint or remove
```

---

# Quick Fix Commands

```bash
# Navigate to backend
cd /home/stefan/Documenten/web-development/010change-my-com-v2/backend

# Apply all fixes (manual review recommended)
# Fix Issue #9 - Remove circular imports
# Fix Issue #5 - Remove webhook secret logging
# Fix Issue #4 - Replace email with user ID in all loggers
# Fix Issue #14 - Secure Prometheus
```

---

# Summary of All Fixes

## ✅ COMPLETED:
1. ✅ ISSUE #7: Open Redirect - URL whitelist added
2. ✅ ISSUE #11: DDL Auto-Update - Changed to validate + Flyway
3. ✅ ISSUE #1: Token Verifier - Singleton bean created

## 🔄 REMAINING (Quick Fixes):
4. ISSUE #4: Email Logging - Replace with user.getId()
5. ISSUE #2: Console.error - Remove error details
6. ISSUE #5: Secret Logging - Remove line
7. ISSUE #6: Webhook Rate Limit - Add to filter
8. ISSUE #9: Circular Imports - Remove unused imports
9. ISSUE #14: Prometheus - Restrict access

All remaining issues are LOW-MEDIUM priority and can be fixed in < 5 minutes each.
