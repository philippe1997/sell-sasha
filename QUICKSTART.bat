@echo off
setlocal
cd /d "%~dp0"

echo.
echo Sell Sasha - New Photo Gallery
echo =============================
echo.

node -v > nul 2>&1
if errorlevel 1 (
    echo Node.js is required. Install Node.js 20.19+ or 22.12+.
    exit /b 1
)
for /f "tokens=*" %%i in ('node -v') do set NODE_VERSION=%%i
echo Node.js: %NODE_VERSION%

java -version > nul 2>&1
if errorlevel 1 (
    echo Java 17 or newer is required for the backend.
) else (
    echo Java is installed. Confirm it is version 17 or newer.
)

if not exist "frontend\node_modules" (
    echo.
    echo Installing frontend dependencies...
    call npm install --prefix frontend
    if errorlevel 1 exit /b 1
)

if not exist "frontend\.env.local" (
    copy "frontend\.env.example" "frontend\.env.local" > nul
    echo Created frontend\.env.local
)

echo.
echo Configure CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and
echo CLOUDINARY_API_SECRET in the backend terminal. Keep secrets in the backend.
echo Set CLOUDINARY_CATEGORIES to your Cloudinary folder names.
echo.
echo Start the backend in one terminal:
echo   cd backend
echo   set CLOUDINARY_CLOUD_NAME=your-cloud-name
echo   set CLOUDINARY_API_KEY=your-api-key
echo   set CLOUDINARY_API_SECRET=your-api-secret
echo   .\mvnw.cmd spring-boot:run
echo.
echo Start the frontend in a second terminal:
echo   cd frontend
echo   npm run dev
echo.
echo Open http://localhost:5173
echo See README.md for configuration and category folder setup.
echo.
