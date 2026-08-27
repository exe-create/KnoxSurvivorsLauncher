#!/bin/sh
set -eu

LAUNCHER_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
LAUNCHER_JAR="$LAUNCHER_ROOT/KnoxSurvivorsLauncher.jar"
if [ ! -f "$LAUNCHER_JAR" ]; then
    printf '%s\n' 'KnoxSurvivorsLauncher.jar is missing. Extract the complete launcher archive first.' >&2
    exit 1
fi

case "$(uname -s)" in
    Darwin)
        STEAM_ROOTS="$HOME/Library/Application Support/Steam"
        ;;
    Linux)
        STEAM_ROOTS="$HOME/.local/share/Steam
$HOME/.steam/steam
$HOME/.var/app/com.valvesoftware.Steam/.local/share/Steam"
        ;;
    *)
        printf '%s\n' 'This operating system is not supported by this launcher.' >&2
        exit 1
        ;;
esac

if [ -n "${KNOX_STEAM_ROOT:-}" ]; then
    STEAM_ROOTS="$KNOX_STEAM_ROOT
$STEAM_ROOTS"
fi

find_java() {
    printf '%s\n' "$STEAM_ROOTS" | while IFS= read -r steam; do
        [ -d "$steam" ] || continue
        printf '%s\n' "$steam"
        vdf="$steam/steamapps/libraryfolders.vdf"
        if [ -f "$vdf" ]; then
            sed -n 's/.*"path"[[:space:]]*"\([^"]*\)".*/\1/p' "$vdf" | sed 's/\\\\/\\/g'
        fi
    done | while IFS= read -r library; do
        game="$library/steamapps/common/ProjectZomboid"
        [ -d "$game" ] || continue
        for java in "$game/jre64/bin/java" "$game/jre/bin/java"; do
            if [ -x "$java" ]; then
                printf '%s\n' "$java"
                return 0
            fi
        done
        java=$(find "$game" -maxdepth 8 -type f -path '*/bin/java' -perm -u+x -print -quit 2>/dev/null || true)
        if [ -n "$java" ]; then
            printf '%s\n' "$java"
            return 0
        fi
    done
}

JAVA_BIN=$(find_java | head -n 1)
if [ -z "$JAVA_BIN" ]; then
    printf '%s\n' 'Project Zomboid bundled Java was not found. Install or verify the game through Steam.' >&2
    exit 1
fi

exec "$JAVA_BIN" -jar "$LAUNCHER_JAR"
