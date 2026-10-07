@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean build
if errorlevel 1 (
    echo.
    echo BUILD FAILED.
    pause
    exit /b 1
)
echo.
echo BUILD SUCCESSFUL.
echo JAR: build\libs\simpletrading-1.3.10.jar
pause
