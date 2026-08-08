#!/usr/bin/env bash
# Builds a macOS .app bundle for TripTale using jpackage.
# See docs/05_macos-app-bundle.md for background and rationale.
set -euo pipefail
cd "$(dirname "$0")/../.."

JAR=target/triptale.jar
ICON=packaging/macos/triptale.icns
STAGING=target/jpackage-input
DEST=target/dist
APP_VERSION="${APP_VERSION:-1.0}"

if [ ! -f "$JAR" ]; then
    echo "error: $JAR not found — run 'make build' first" >&2
    exit 1
fi

rm -rf "$STAGING" "$DEST"
mkdir -p "$STAGING"
# jpackage copies the *entire* --input directory into Contents/app, so stage
# only the fat jar in an isolated directory (target/ also has test reports,
# jacoco.exec, the pre-repackage .original jar, etc. — none of that belongs
# in the bundle).
cp "$JAR" "$STAGING/"

jpackage \
    --type app-image \
    --name TripTale \
    --input "$STAGING" \
    --main-jar "$(basename "$JAR")" \
    --icon "$ICON" \
    --dest "$DEST" \
    --app-version "$APP_VERSION" \
    --vendor "TripTale" \
    --copyright "TripTale" \
    --mac-package-identifier net.timafe.triptale \
    --mac-app-category "healthcare-fitness" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "--sun-misc-unsafe-memory-access=allow"

echo "Built $DEST/TripTale.app"
echo "Install: cp -R $DEST/TripTale.app /Applications/"
