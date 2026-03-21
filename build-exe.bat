@echo off
:: ============================================================
::  build-exe.bat  -  Tao file Furniture.exe
:: ============================================================

set MVN="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
set JAVA_HOME=C:\Program Files\Apache NetBeans\jdk

:: WiX nam o "Tep Chuong trinh (x86)" (tieng Viet)
set PATH=%JAVA_HOME%\bin;C:\Tep Chuong trinh (x86)\WiX Toolset v3.14\bin;%PATH%

echo ==========================================
echo  Buoc 1: Build fat JAR bang Maven
echo ==========================================
call %MVN% clean package -q
if errorlevel 1 (
    echo [LOI] Maven build that bai.
    pause & exit /b 1
)
echo [OK] Fat JAR da tao: target\FurnitureSystem-fat.jar

echo.
echo ==========================================
echo  Buoc 2: Tao Furniture.exe bang jpackage
echo ==========================================

set OUTPUT_DIR=installer
if exist %OUTPUT_DIR% rmdir /s /q %OUTPUT_DIR%
mkdir %OUTPUT_DIR%

jpackage ^
    --type exe ^
    --name "Furniture System" ^
    --app-version "1.0" ^
    --vendor "Fair Deal Store" ^
    --input target ^
    --main-jar FurnitureSystem-fat.jar ^
    --main-class furniture_system.Launcher ^
    --dest %OUTPUT_DIR% ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser ^
    --java-options "-Dfurniture.gmail.user=pnghuya23032@cusc.ctu.edu.vn" ^
    --java-options "-Dfurniture.gmail.pass=xzdktikhojjquszv" ^
    --java-options "-Dfile.encoding=UTF-8"

if errorlevel 1 (
    echo [LOI] jpackage that bai. Xem log o tren.
    pause & exit /b 1
)

echo.
echo ==========================================
echo  HOAN THANH!
echo  File: installer\Furniture System-1.0.exe
echo ==========================================
pause
