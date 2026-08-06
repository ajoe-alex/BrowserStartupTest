#!/bin/bash
# Runs the BrowserStartupTest fat jar shipped alongside this script.
# Configure url/browser/waitTimeInSeconds/browser_binary_path in app.properties.json
# and place the matching driver (chromedriver/geckodriver/msedgedriver) in webdrivers/
# before running.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

mkdir -p logs webdrivers

# To run with a specific JDK instead of whatever "java" resolves to on PATH,
# uncomment the line below and set it to that JDK's home directory:
# JAVA_HOME="/path/to/jdk"

JAVA_CMD="java"
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

"$JAVA_CMD" -jar BrowserStartupTest.jar
