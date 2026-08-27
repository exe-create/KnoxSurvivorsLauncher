#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
DIST="$ROOT/dist"
rm -rf "$BUILD"
mkdir -p "$CLASSES" "$TEST_CLASSES" "$DIST"
find "$ROOT/src/main/java" -name '*.java' -print > "$BUILD/main-sources.txt"
javac --release 17 -d "$CLASSES" @"$BUILD/main-sources.txt"
jar --create --file "$ROOT/KnoxSurvivorsLauncher.jar" \
    --main-class com.knoxsurvivors.launcher.Main -C "$CLASSES" .
find "$ROOT/src/test/java" -name '*.java' -print > "$BUILD/test-sources.txt"
javac --release 17 -cp "$CLASSES" -d "$TEST_CLASSES" @"$BUILD/test-sources.txt"
java -cp "$CLASSES:$TEST_CLASSES" com.knoxsurvivors.launcher.LauncherVerifier

WIN="$BUILD/windows/Knox Survivors Launcher"
UNIX="$BUILD/unix/Knox Survivors Launcher"
mkdir -p "$WIN/scripts" "$UNIX/scripts"
cp "$ROOT/KnoxSurvivorsLauncher.jar" "$ROOT/Launch Knox Survivors.cmd" "$ROOT/README.txt" "$WIN/"
cp "$ROOT/scripts/launch-knox-survivors.ps1" "$WIN/scripts/"
cp "$ROOT/KnoxSurvivorsLauncher.jar" "$ROOT/Launch Knox Survivors.command" "$ROOT/README.txt" "$UNIX/"
cp "$ROOT/scripts/launch-knox-survivors.sh" "$UNIX/scripts/"
chmod +x "$UNIX/Launch Knox Survivors.command" "$UNIX/scripts/launch-knox-survivors.sh"
(cd "$BUILD/windows" && zip -qr "$DIST/KnoxSurvivorsLauncher-windows.zip" "Knox Survivors Launcher")
(cd "$BUILD/unix" && zip -qr "$DIST/KnoxSurvivorsLauncher-linux-macos.zip" "Knox Survivors Launcher")
(cd "$DIST" && sha256sum KnoxSurvivorsLauncher-*.zip > SHA256SUMS.txt)
