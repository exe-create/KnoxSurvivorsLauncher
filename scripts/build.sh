#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
DIST="$ROOT/dist"
rm -rf "$BUILD"
rm -rf "$DIST"
mkdir -p "$CLASSES" "$TEST_CLASSES" "$DIST"
find "$ROOT/src/main/java" -name '*.java' -print | sed 's/.*/"&"/' > "$BUILD/main-sources.txt"
javac --release 17 -d "$CLASSES" @"$BUILD/main-sources.txt"
printf '%s\n' 'Manifest-Version: 1.0' 'Main-Class: com.knoxsurvivors.launcher.Main' 'Implementation-Version: 0.2.3-preview.2' 'Knox-Update-Protocol: 1' > "$BUILD/MANIFEST.MF"
jar --create --file "$ROOT/KnoxSurvivorsLauncher.jar" --manifest "$BUILD/MANIFEST.MF" -C "$CLASSES" .
find "$ROOT/src/test/java" -name '*.java' -print | sed 's/.*/"&"/' > "$BUILD/test-sources.txt"
javac --release 17 -cp "$CLASSES" -d "$TEST_CLASSES" @"$BUILD/test-sources.txt"
java -cp "$CLASSES:$TEST_CLASSES" com.knoxsurvivors.launcher.LauncherVerifier

WIN="$BUILD/windows/Knox Survivors Launcher"
LINUX="$BUILD/linux/Knox Survivors Launcher"
MACOS="$BUILD/macos/Knox Survivors Launcher"
mkdir -p "$WIN/scripts" "$LINUX/scripts" "$MACOS/scripts"
cp "$ROOT/KnoxSurvivorsLauncher.jar" "$ROOT/Launch Knox Survivors.cmd" "$ROOT/README.txt" "$WIN/"
cp "$ROOT/scripts/launch-knox-survivors.ps1" "$WIN/scripts/"
cp "$ROOT/KnoxSurvivorsLauncher.jar" "$ROOT/README.txt" "$LINUX/"
cp "$ROOT/scripts/launch-knox-survivors.sh" "$LINUX/scripts/"
cp "$ROOT/KnoxSurvivorsLauncher.jar" "$ROOT/Launch Knox Survivors.command" "$ROOT/README.txt" "$MACOS/"
cp "$ROOT/scripts/launch-knox-survivors.sh" "$MACOS/scripts/"
chmod +x "$LINUX/scripts/launch-knox-survivors.sh"
chmod +x "$MACOS/Launch Knox Survivors.command" "$MACOS/scripts/launch-knox-survivors.sh"
(cd "$BUILD/windows" && zip -qr "$DIST/KnoxSurvivorsLauncher-windows.zip" "Knox Survivors Launcher")
(cd "$BUILD/linux" && zip -qr "$DIST/KnoxSurvivorsLauncher-linux.zip" "Knox Survivors Launcher")
(cd "$BUILD/macos" && zip -qr "$DIST/KnoxSurvivorsLauncher-macos.zip" "Knox Survivors Launcher")
if command -v sha256sum >/dev/null 2>&1; then
    (cd "$DIST" && sha256sum KnoxSurvivorsLauncher-*.zip > SHA256SUMS.txt)
else
    (cd "$DIST" && shasum -a 256 KnoxSurvivorsLauncher-*.zip > SHA256SUMS.txt)
fi
cp "$ROOT/KnoxSurvivorsLauncher.jar" "$DIST/KnoxSurvivorsLauncher.jar"
