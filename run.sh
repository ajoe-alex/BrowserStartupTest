#!/bin/bash
# Compiles and runs BrowserStartupTest on macOS/Linux.
# Configure url/browser/waitTimeInSeconds in app.properties.json before running.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

mkdir -p out logs webdrivers

echo "Compiling..."
javac -cp "libs/*" -d out src/Main.java

echo "Running BrowserStartupTest..."
java -cp "out:libs/*" Main
