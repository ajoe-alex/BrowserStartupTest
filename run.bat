@echo off
REM Compiles and runs BrowserStartupTest on Windows.
REM Configure url/browser/waitTimeInSeconds in app.properties.json before running.
setlocal

cd /d "%~dp0"

if not exist out mkdir out
if not exist logs mkdir logs
if not exist webdrivers mkdir webdrivers

echo Compiling...
javac -cp "libs/*" -d out src\Main.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Running BrowserStartupTest...
java -cp "out;libs/*" Main

endlocal
