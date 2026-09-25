#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
DIST="$ROOT/dist"
VERSION=0.3.2
if [ "${GITHUB_REF_TYPE:-}" = "tag" ]; then
  VERSION=${GITHUB_REF_NAME#v}
  if ! printf '%s\n' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$'; then
    echo "Invalid launcher release tag: ${GITHUB_REF_NAME:-}" >&2
    exit 1
  fi
fi
rm -rf "$BUILD"
rm -rf "$DIST"
mkdir -p "$CLASSES" "$TEST_CLASSES" "$DIST"
find "$ROOT/src/main/java" -name '*.java' -print | sed 's/.*/"&"/' > "$BUILD/main-sources.txt"
javac --release 17 -encoding UTF-8 -d "$CLASSES" @"$BUILD/main-sources.txt"
if [ -d "$ROOT/src/main/resources" ]; then
  for f in "$ROOT"/src/main/resources/*.png "$ROOT"/src/main/resources/*.jpg "$ROOT"/src/main/resources/changelog.txt; do
    [ -e "$f" ] || continue
    case "$(basename "$f")" in README.txt) continue;; *) cp "$f" "$CLASSES/";; esac
  done
fi
printf '%s\n' 'Manifest-Version: 1.0' 'Main-Class: com.knoxsurvivors.launcher.Main' "Implementation-Version: $VERSION" 'Knox-Update-Protocol: 1' > "$BUILD/MANIFEST.MF"
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
cp "$ROOT/KnoxSurvivorsLauncher.jar" "$DIST/KnoxSurvivorsLauncher.jar"
if command -v sha256sum >/dev/null 2>&1; then
    (cd "$DIST" && sha256sum KnoxSurvivorsLauncher-*.zip KnoxSurvivorsLauncher.jar > SHA256SUMS.txt)
else
    (cd "$DIST" && shasum -a 256 KnoxSurvivorsLauncher-*.zip KnoxSurvivorsLauncher.jar > SHA256SUMS.txt)
fi
