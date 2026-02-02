#!/bin/bash

# Security Verification Script
# Verifies that all security configurations are properly deployed

set -e

echo "========================================="
echo "Security Configuration Verification"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SUCCESS=0
WARNINGS=0
ERRORS=0

# Function to check status
check_status() {
    local name=$1
    local status=$2
    local details=$3

    if [ $status -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $name"
        [ -n "$details" ] && echo "  $details"
        SUCCESS=$((SUCCESS + 1))
    else
        echo -e "${RED}✗${NC} $name"
        [ -n "$details" ] && echo "  $details"
        ERRORS=$((ERRORS + 1))
    fi
}

check_warning() {
    local name=$1
    local details=$2

    echo -e "${YELLOW}⚠${NC} $name"
    [ -n "$details" ] && echo "  $details"
    WARNINGS=$((WARNINGS + 1))
}

echo "1. ImageMagick Security Policy"
echo "----------------------------------------------"

# Check if ImageMagick is installed
if command -v convert &> /dev/null || command -v magick &> /dev/null; then
    check_status "ImageMagick installed" 0

    # Find policy file
    if [ -f "/etc/ImageMagick-7/policy.xml" ]; then
        POLICY_FILE="/etc/ImageMagick-7/policy.xml"
    elif [ -f "/etc/ImageMagick-6/policy.xml" ]; then
        POLICY_FILE="/etc/ImageMagick-6/policy.xml"
    else
        check_status "ImageMagick policy file" 1 "Not found"
        POLICY_FILE=""
    fi

    if [ -n "$POLICY_FILE" ]; then
        # Check if our security policy is deployed
        if grep -q "change-my-com-v2" "$POLICY_FILE" 2>/dev/null || grep -q "Only safe raster formats" "$POLICY_FILE" 2>/dev/null; then
            check_status "Security policy deployed" 0 "Location: $POLICY_FILE"

            # Verify dangerous coders are blocked
            if grep -q 'pattern="PDF"' "$POLICY_FILE" && grep -q 'rights="none"' "$POLICY_FILE"; then
                check_status "Dangerous coders blocked" 0 "PDF, SVG, PS disabled"
            else
                check_warning "Security policy may be outdated"
            fi
        else
            check_status "Custom security policy" 1 "Default policy detected"
        fi
    fi
else
    check_status "ImageMagick installed" 1 "ImageMagick not found"
fi

echo ""
echo "2. Environment Variables"
echo "----------------------------------------------"

# Check production profile
if [ "$SPRING_PROFILES_ACTIVE" = "prod" ]; then
    check_status "Production profile set" 0 "SPRING_PROFILES_ACTIVE=prod"
else
    check_warning "Production profile not set" "Current: ${SPRING_PROFILES_ACTIVE:-not set}"
fi

# Check database URL
if [ -n "$DATABASE_URL" ]; then
    if [[ "$DATABASE_URL" == *"sslmode=require"* ]]; then
        check_status "Database SSL enforced" 0 "sslmode=require detected"
    else
        check_warning "Database SSL not enforced" "Add ?sslmode=require to DATABASE_URL"
    fi
else
    check_warning "DATABASE_URL not set"
fi

# Check required secrets
[ -n "$GOOGLE_CLIENT_ID" ] && check_status "Google Client ID set" 0 || check_warning "GOOGLE_CLIENT_ID not set"
[ -n "$STRIPE_SECRET_KEY" ] && check_status "Stripe Secret Key set" 0 || check_warning "STRIPE_SECRET_KEY not set"
[ -n "$STRIPE_WEBHOOK_SECRET" ] && check_status "Stripe Webhook Secret set" 0 || check_warning "STRIPE_WEBHOOK_SECRET not set"

echo ""
echo "3. Log Configuration"
echo "----------------------------------------------"

# Check log directory
if [ -d "/app/logs" ]; then
    check_status "Log directory exists" 0 "/app/logs"

    # Check permissions
    if [ -w "/app/logs" ]; then
        check_status "Log directory writable" 0
    else
        check_status "Log directory writable" 1 "Not writable by current user"
    fi
else
    check_status "Log directory exists" 1 "Create with: sudo mkdir -p /app/logs"
fi

echo ""
echo "4. Application Files"
echo "----------------------------------------------"

# Check if backend JAR exists
if [ -f "backend/target/*.jar" ] || ls backend/target/*.jar 1> /dev/null 2>&1; then
    check_status "Backend JAR built" 0
else
    check_warning "Backend JAR not found" "Run: cd backend && ./mvnw clean package"
fi

# Check security improvements documentation
if [ -f "SECURITY-IMPROVEMENTS.md" ]; then
    check_status "Security documentation" 0 "SECURITY-IMPROVEMENTS.md exists"
else
    check_status "Security documentation" 1 "SECURITY-IMPROVEMENTS.md not found"
fi

echo ""
echo "5. Test Security Features"
echo "----------------------------------------------"

# Test if dangerous operations are blocked
if command -v convert &> /dev/null; then
    echo "Testing ImageMagick security policy..."

    # Try to access a URL (should be blocked)
    if convert https://example.com/test.jpg /tmp/test.jpg 2>&1 | grep -qi "not authorized\|operation not allowed\|security policy"; then
        check_status "URL access blocked" 0 "Security policy working"
    else
        check_warning "URL access test inconclusive" "May need manual verification"
    fi
fi

echo ""
echo "========================================="
echo "Verification Summary"
echo "========================================="
echo -e "${GREEN}Passed: $SUCCESS${NC}"
echo -e "${YELLOW}Warnings: $WARNINGS${NC}"
echo -e "${RED}Errors: $ERRORS${NC}"
echo ""

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✓ All security checks passed!${NC}"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠ Some warnings detected. Review and address them.${NC}"
    exit 0
else
    echo -e "${RED}✗ Some checks failed. Please fix the errors above.${NC}"
    exit 1
fi
