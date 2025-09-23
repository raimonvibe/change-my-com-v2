#!/bin/bash

# 🚀 Image Converter - Local Development Setup Script

echo "🚀 Setting up Image Converter for local development..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Start PostgreSQL database
echo "📦 Starting PostgreSQL database..."
docker compose up -d

# Wait for database to be ready
echo "⏳ Waiting for database to be ready..."
sleep 5

# Set environment variables for local development
echo "🔧 Setting environment variables..."

export DATABASE_URL=jdbc:postgresql://localhost:5432/imageconverter
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres
export SPRING_SECURITY_USER_NAME=admin
export SPRING_SECURITY_USER_PASSWORD=dev-password-123
export GOOGLE_CLIENT_ID=409321867036-1imcfj1apd0cgsmg04ha82k68lu33nah.apps.googleusercontent.com
export STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key_here
export STRIPE_PUBLISHABLE_KEY=pk_test_your_stripe_publishable_key_here
export STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret_here
export ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
export SPRING_PROFILES_ACTIVE=dev
export PORT=8080

echo "✅ Environment variables set!"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17+ first."
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js 20+ first."
    exit 1
fi

echo "🔍 Checking dependencies..."

# Check if Maven wrapper exists
if [ ! -f "backend/mvnw" ]; then
    echo "❌ Maven wrapper not found in backend directory."
    exit 1
fi

# Check if package.json exists
if [ ! -f "frontend/package.json" ]; then
    echo "❌ package.json not found in frontend directory."
    exit 1
fi

echo "✅ All dependencies found!"

echo ""
echo "🎯 Setup complete! Now you can:"
echo ""
echo "1. Start the backend:"
echo "   cd backend && ./mvnw spring-boot:run"
echo ""
echo "2. Start the frontend (in a new terminal):"
echo "   cd frontend && npm install && npm run dev"
echo ""
echo "3. Access your app:"
echo "   Frontend: http://localhost:3000"
echo "   Backend:  http://localhost:8080"
echo "   Health:   http://localhost:8080/health"
echo "   Debug:    http://localhost:8080/api/debug/whoami (with auth)"
echo ""
echo "🔧 Debug endpoints are enabled (SPRING_PROFILES_ACTIVE=dev)"
echo "🔒 Spring Security password is set (no more warnings!)"
echo ""
echo "Happy coding! 🚀"



