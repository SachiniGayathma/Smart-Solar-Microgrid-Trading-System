@echo off
echo ========================================================
echo   Smart Solar Microgrid - 1-Click App Refresh
echo ========================================================
cd /d "%~dp0"

echo [1/3] Building and installing latest debug APK...
call .\gradlew.bat installDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle build or installation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/3] Restarting app on emulator...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.example.smartsolarmobileapp
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell am start -n com.example.smartsolarmobileapp/.MainActivity

echo [3/3] Done! The app is now refreshed with all latest changes!
echo ========================================================
