@echo off
REM Runs the BrowserStartupTest fat jar shipped alongside this script.
REM Configure url/browser/waitTimeInSeconds/browser_binary_path in app.properties.json
REM and place the matching driver (chromedriver.exe/geckodriver.exe/msedgedriver.exe)
REM in webdrivers\ before running.
setlocal

cd /d "%~dp0"

if not exist logs mkdir logs
if not exist webdrivers mkdir webdrivers

java -jar BrowserStartupTest.jar

endlocal
