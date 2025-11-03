@echo off
REM Build script for CanvasGLSL - builds all Minecraft versions
REM Usage: build-all.bat [clean]

echo ========================================
echo  CanvasGLSL Multi-Version Build Script
echo ========================================
echo.

REM Always clean before building
echo [1/4] Cleaning previous builds and output folder...
if exist "build\output" (
    echo Deleting build\output\...
    rd /s /q "build\output"
)
call .\gradlew clean
echo.

echo [2/4] Building Minecraft 1.21...
call .\gradlew :versions:1.21:build
if errorlevel 1 (
    echo ERROR: Failed to build 1.21
    exit /b 1
)
echo.

echo [3/4] Building Minecraft 1.21.4...
call .\gradlew :versions:1.21.4:build
if errorlevel 1 (
    echo ERROR: Failed to build 1.21.4
    exit /b 1
)
echo.

echo [4/4] Building Minecraft 1.21.10...
call .\gradlew :versions:1.21.10:build
if errorlevel 1 (
    echo ERROR: Failed to build 1.21.10
    exit /b 1
)
echo.

echo ========================================
echo  Build Complete!
echo ========================================
echo.
echo Output JARs:
echo   1.21    : versions\1.21\build\libs\
echo   1.21.4  : versions\1.21.4\build\libs\
echo   1.21.10 : versions\1.21.10\build\libs\
echo.

REM Copy all JARs to a central output folder
echo Copying JARs to build\output\...
if not exist "build\output" mkdir "build\output"
xcopy /Y "versions\1.21\build\libs\*.jar" "build\output\" 2>nul
xcopy /Y "versions\1.21.4\build\libs\*.jar" "build\output\" 2>nul
xcopy /Y "versions\1.21.10\build\libs\*.jar" "build\output\" 2>nul
echo.
echo All JARs copied to: build\output\
echo.
pause
