#!/bin/bash
set -e

cd "$(dirname "$0")"

echo
echo "Sell Sasha - New Photo Gallery"
echo "=============================="
echo

if ! command -v node >/dev/null 2>&1; then
    echo "Node.js 20.19+ or 22.12+ is required."
    exit 1
fi

echo "Node.js: $(node -v)"
if ! command -v java >/dev/null 2>&1; then
    echo "Java 17 or newer is required for the backend."
else
    echo "Java is installed. Confirm it is version 17 or newer."
fi

if [ ! -d "frontend/node_modules" ]; then
    echo
    echo "Installing frontend dependencies..."
    npm install --prefix frontend
fi

if [ ! -f "frontend/.env.local" ]; then
    cp "frontend/.env.example" "frontend/.env.local"
    echo "Created frontend/.env.local"
fi

echo
echo "Configure CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and"
echo "CLOUDINARY_API_SECRET in the backend terminal. Keep secrets in the backend."
echo "Set CLOUDINARY_CATEGORIES to your Cloudinary folder names."
echo
echo "Start the backend in one terminal:"
echo "  cd backend"
echo "  export CLOUDINARY_CLOUD_NAME=your-cloud-name"
echo "  export CLOUDINARY_API_KEY=your-api-key"
echo "  export CLOUDINARY_API_SECRET=your-api-secret"
echo "  ./mvnw spring-boot:run"
echo
echo "Start the frontend in a second terminal:"
echo "  cd frontend"
echo "  npm run dev"
echo
echo "Open http://localhost:5173"
echo "See README.md for configuration and category folder setup."
echo
