#!/bin/bash
# Runs the BrowserStartupTest fat jar shipped alongside this script.
# Configure url/browser/waitTimeInSeconds/browser_binary_path in app.properties.json
# and place the matching driver (chromedriver/geckodriver/msedgedriver) in webdrivers/
# before running.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

mkdir -p logs webdrivers

java -jar BrowserStartupTest.jar
