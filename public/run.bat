@echo off
REM Runs the BrowserStartupTest fat jar shipped alongside this script.
REM Configure url/browser/waitTimeInSeconds/browser_binary_path in app.properties.json
REM and place the matching driver (chromedriver.exe/geckodriver.exe/msedgedriver.exe)
REM in webdrivers\ before running.
setlocal

cd /d "%~dp0"

if not exist logs mkdir logs
if not exist webdrivers mkdir webdrivers

REM To run with a specific JDK instead of whatever "java" resolves to on PATH,
REM uncomment the line below and set it to that JDK's home directory:
REM set "JAVA_HOME=C:\path\to\jdk"

set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

"%JAVA_CMD%" -jar BrowserStartupTest.jar

endlocal
