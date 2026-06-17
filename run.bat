@echo off
setlocal

cd /d "%~dp0"

call mvn clean package -Dmaven.test.skip=true
if errorlevel 1 (
    echo.
    echo Build failed. Press any key to close this window.
    pause >nul
    exit /b 1
)

start "" javaw -jar "%~dp0target\Kubot.jar"
exit /b 0
