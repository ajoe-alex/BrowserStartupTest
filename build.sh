#!/bin/bash
# Builds a self-contained fat jar for client distribution into build/browser_startup_test/.
# Everything under public/ (run.sh, run.bat, app.properties.json, ...) is copied as-is
# into the build output alongside the jar.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

BUILD_DIR="$DIR/build/browser_startup_test"
STAGE_DIR="$DIR/build/.stage"
JAR_NAME="BrowserStartupTest.jar"

echo "Cleaning previous build..."
rm -rf "$BUILD_DIR" "$STAGE_DIR"
mkdir -p "$BUILD_DIR" "$STAGE_DIR/classes"

echo "Compiling sources..."
javac -cp "libs/*" -d "$STAGE_DIR/classes" src/Main.java

echo "Merging dependency jars..."
for jar in "$DIR"/libs/*.jar; do
    (cd "$STAGE_DIR/classes" && jar xf "$jar")
done

# Drop module descriptors/signatures pulled from dependency jars; this fat jar is run
# via the classpath (java -jar), not the module path, so they're just dead weight.
rm -f "$STAGE_DIR/classes/module-info.class"
rm -rf "$STAGE_DIR/classes/META-INF/versions"
rm -f "$STAGE_DIR/classes/META-INF/MANIFEST.MF"

echo "Writing manifest..."
printf 'Main-Class: Main\n' > "$STAGE_DIR/MANIFEST.MF"

echo "Packaging fat jar..."
jar cfm "$BUILD_DIR/$JAR_NAME" "$STAGE_DIR/MANIFEST.MF" -C "$STAGE_DIR/classes" .

echo "Copying public/ into build output..."
cp -R "$DIR/public/." "$BUILD_DIR/"
chmod +x "$BUILD_DIR/run.sh" 2>/dev/null || true

mkdir -p "$BUILD_DIR/webdrivers" "$BUILD_DIR/logs"

rm -rf "$STAGE_DIR"

echo "Build complete: $BUILD_DIR/$JAR_NAME"
