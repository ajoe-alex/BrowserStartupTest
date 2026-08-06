@echo off
REM Builds a self-contained fat jar for client distribution into build\browser_startup_test\.
REM Everything under public\ (run.sh, run.bat, app.properties.json, ...) is copied as-is
REM into the build output alongside the jar.
setlocal

set "DIR=%~dp0"
cd /d "%DIR%"

set "BUILD_DIR=%DIR%build\browser_startup_test"
set "STAGE_DIR=%DIR%build\.stage"
set "JAR_NAME=BrowserStartupTest.jar"

echo Cleaning previous build...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%STAGE_DIR%" rmdir /s /q "%STAGE_DIR%"
mkdir "%BUILD_DIR%"
mkdir "%STAGE_DIR%\classes"

echo Compiling sources...
javac -cp "libs/*" -d "%STAGE_DIR%\classes" src\Main.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Merging dependency jars...
pushd "%STAGE_DIR%\classes"
for %%J in ("%DIR%libs\*.jar") do (
    jar xf "%%J"
)
popd

if exist "%STAGE_DIR%\classes\module-info.class" del "%STAGE_DIR%\classes\module-info.class"
if exist "%STAGE_DIR%\classes\META-INF\versions" rmdir /s /q "%STAGE_DIR%\classes\META-INF\versions"
if exist "%STAGE_DIR%\classes\META-INF\MANIFEST.MF" del "%STAGE_DIR%\classes\META-INF\MANIFEST.MF"

echo Writing manifest...
> "%STAGE_DIR%\MANIFEST.MF" echo Main-Class: Main

echo Packaging fat jar...
jar cfm "%BUILD_DIR%\%JAR_NAME%" "%STAGE_DIR%\MANIFEST.MF" -C "%STAGE_DIR%\classes" .
if errorlevel 1 (
    echo Packaging failed.
    exit /b 1
)

echo Copying public\ into build output...
xcopy /e /i /y "%DIR%public\*" "%BUILD_DIR%\" >nul

if not exist "%BUILD_DIR%\webdrivers" mkdir "%BUILD_DIR%\webdrivers"
if not exist "%BUILD_DIR%\logs" mkdir "%BUILD_DIR%\logs"

rmdir /s /q "%STAGE_DIR%"

echo Build complete: %BUILD_DIR%\%JAR_NAME%

endlocal
