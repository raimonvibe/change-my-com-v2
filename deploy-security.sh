#!/bin/bash

# Security Deployment Script
# Run this script with sudo privileges to apply security configurations

set -e

echo "========================================="
echo "Security Configuration Deployment"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running with sudo
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Error: This script must be run with sudo${NC}"
    echo "Usage: sudo ./deploy-security.sh"
    exit 1
fi

echo "Step 1: Deploying ImageMagick Security Policy"
echo "----------------------------------------------"

# Detect ImageMagick version
if [ -d "/etc/ImageMagick-7" ]; then
    IMAGEMAGICK_DIR="/etc/ImageMagick-7"
elif [ -d "/etc/ImageMagick-6" ]; then
    IMAGEMAGICK_DIR="/etc/ImageMagick-6"
else
    echo -e "${RED}Error: ImageMagick config directory not found${NC}"
    echo "Please install ImageMagick first"
    exit 1
fi

echo "Detected ImageMagick config directory: $IMAGEMAGICK_DIR"

# Backup existing policy
if [ -f "$IMAGEMAGICK_DIR/policy.xml" ]; then
    BACKUP_FILE="$IMAGEMAGICK_DIR/policy.xml.backup.$(date +%Y%m%d_%H%M%S)"
    echo "Backing up existing policy to: $BACKUP_FILE"
    cp "$IMAGEMAGICK_DIR/policy.xml" "$BACKUP_FILE"
fi

# Deploy new policy
echo "Deploying new security policy..."
cp backend/src/main/resources/imagemagick-policy.xml "$IMAGEMAGICK_DIR/policy.xml"
chmod 644 "$IMAGEMAGICK_DIR/policy.xml"
chown root:root "$IMAGEMAGICK_DIR/policy.xml"

echo -e "${GREEN}✓ ImageMagick policy deployed successfully${NC}"
echo ""

# Verify policy
echo "Verifying ImageMagick policy..."
if command -v magick &> /dev/null; then
    magick -list policy | head -20
elif command -v convert &> /dev/null; then
    echo "ImageMagick 6 detected (convert command)"
    echo "Policy file located at: $IMAGEMAGICK_DIR/policy.xml"
fi

echo ""
echo "Step 2: Environment Variables Setup"
echo "----------------------------------------------"
echo "Add these to your environment (e.g., /etc/environment or ~/.bashrc):"
echo ""
echo -e "${YELLOW}# Production Profile${NC}"
echo "export SPRING_PROFILES_ACTIVE=prod"
echo ""
echo -e "${YELLOW}# Database with SSL (update with your credentials)${NC}"
echo "export DATABASE_URL='jdbc:postgresql://your-host:5432/your-db?sslmode=require'"
echo "export DATABASE_USERNAME='your-username'"
echo "export DATABASE_PASSWORD='your-password'"
echo ""
echo -e "${YELLOW}# Google OAuth${NC}"
echo "export GOOGLE_CLIENT_ID='your-google-client-id'"
echo ""
echo -e "${YELLOW}# Stripe${NC}"
echo "export STRIPE_SECRET_KEY='your-stripe-secret-key'"
echo "export STRIPE_PUBLISHABLE_KEY='your-stripe-publishable-key'"
echo "export STRIPE_WEBHOOK_SECRET='your-stripe-webhook-secret'"
echo ""

echo "Step 3: Log Directory Setup"
echo "----------------------------------------------"

if [ ! -d "/app/logs" ]; then
    echo "Creating log directory: /app/logs"
    mkdir -p /app/logs

    # Set ownership to the user who will run the application
    # Change 'your-app-user' to your actual application user
    if id "stefan" &>/dev/null; then
        chown stefan:stefan /app/logs
        echo -e "${GREEN}✓ Log directory created and owned by stefan${NC}"
    else
        echo -e "${YELLOW}! Log directory created with root ownership${NC}"
        echo "  Run: sudo chown your-app-user:your-app-user /app/logs"
    fi

    chmod 755 /app/logs
else
    echo -e "${GREEN}✓ Log directory already exists: /app/logs${NC}"
fi

echo ""
echo "========================================="
echo "Deployment Summary"
echo "========================================="
echo -e "${GREEN}✓ ImageMagick security policy deployed${NC}"
echo -e "${GREEN}✓ Log directory configured${NC}"
echo ""
echo "Next steps:"
echo "1. Set environment variables (see above)"
echo "2. Restart your application"
echo "3. Verify security with: ./verify-security.sh"
echo ""
echo -e "${GREEN}Security deployment complete!${NC}"
