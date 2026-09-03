@echo off
setlocal
title Build Orbit Control Windows

where java >nul 2>nul
if errorlevel 1 (
  echo JDK 17 belum ditemukan. Instal JDK 17, lalu buka ulang Command Prompt.
  exit /b 1
)

echo Membuat installer Orbit Control untuk Windows x64...
call gradlew.bat packageDistributionForCurrentOS
if errorlevel 1 (
  echo.
  echo Build gagal. Periksa pesan Gradle di atas.
  exit /b %errorlevel%
)

echo.
echo Installer tersedia di build\compose\binaries\main\...
endlocal
