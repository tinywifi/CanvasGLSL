@echo off
REM Build script for individual Minecraft versions
REM Usage: build-version.bat [1.21|1.21.4|1.21.10]

if "%1"=="" (
    echo Usage: build-version.bat [1.21^|1.21.4^|1.21.10]
    echo.
    echo Examples:
    echo   build-version.bat 1.21
    echo   build-version.bat 1.21.4
    echo   build-version.bat 1.21.10
    exit /b 1
)

echo Building Minecraft %1...
echo.

call gradlew.bat :versions:%1:build

if errorlevel 1 (
    echo ERROR: Build failed for version %1
    exit /b 1
)

echo.
echo Build successful!
echo Output: versions\%1\build\libs\
echo.
pause
