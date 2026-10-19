@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Ahl Al-Quran - Build Android APK
chcp 65001 >nul

echo ===============================================
echo   Ahl Al-Quran - Android APK Builder
echo ===============================================
echo.

if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: Install Android Studio and its bundled JDK first.
  goto :fail
)

if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk\platforms" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME (
  echo ERROR: Android SDK not found. Open Android Studio, finish Setup Wizard,
  echo and install Android SDK Platform 35 in SDK Manager.
  goto :fail
)
if not exist "%ANDROID_HOME%\platforms\android-35\android.jar" (
  echo ERROR: Install Android SDK Platform 35 using Android Studio SDK Manager.
  goto :fail
)
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "GRADLE_DIR=%~dp0.build-tools\gradle-8.7"
if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Downloading Gradle 8.7 (one-time setup, internet required)...
  if not exist ".build-tools" mkdir ".build-tools"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path (Get-Location) '.build-tools\gradle-8.7-bin.zip'; Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-8.7-bin.zip' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath '.build-tools' -Force"
  if errorlevel 1 goto :fail
)

echo Building debug APK...
call "%GRADLE_DIR%\bin\gradle.bat" --no-daemon :app:assembleDebug
if errorlevel 1 goto :fail
if not exist "app\build\outputs\apk\debug\app-debug.apk" goto :fail
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "AhlAlQuran-debug.apk" >nul

echo.
echo APK READY: %CD%\AhlAlQuran-debug.apk
echo This is a TEST/DEBUG APK. Sign a RELEASE APK with your OWN private key
 echo before distributing it widely. Keep the signing key secure.
start "" explorer.exe /select,"%CD%\AhlAlQuran-debug.apk"
pause
exit /b 0

:fail
echo.
echo BUILD FAILED. Read BUILD_GUIDE_AR.txt and the error messages above.
pause
exit /b 1
