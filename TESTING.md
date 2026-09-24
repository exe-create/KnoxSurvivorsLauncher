# Testing Knox Survivors

Knox Survivors currently needs both the Steam Workshop mod and this launcher. The launcher only starts the game when the Workshop files and Knox Java runtime match, so it will refuse to combine an older Workshop build with a newer launcher.

## Before you start

1. Subscribe to [Knox Survivors on Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604).
2. Let Steam finish downloading the update.
3. Enable **Knox Survivors** in Project Zomboid's Mods menu.
4. Download the launcher archive for your operating system from the latest GitHub release.
5. Extract the archive. Do not run it from inside the ZIP.

## Windows

1. Open the extracted **Knox Survivors Launcher** folder.
2. Double-click **Launch Knox Survivors.cmd**.
3. Press **PLAY KNOX SURVIVORS**.

If Windows warns about the downloaded file, choose **More info** and confirm only if the file came from this repository's Releases page.

## Linux

1. Open the extracted **Knox Survivors Launcher** folder in a terminal.
2. Run `chmod +x scripts/launch-knox-survivors.sh` once if needed.
3. Run `./scripts/launch-knox-survivors.sh`.
4. Press **PLAY KNOX SURVIVORS**.

The locator checks Flatpak and usual native Steam locations. A desktop environment is
required; Flatpak permissions and actual game launching still need live tester confirmation.

## macOS

1. Open the extracted **Knox Survivors Launcher** folder.
2. Control-click **Launch Knox Survivors.command**, choose **Open**, then confirm **Open** the first time.
3. Press **PLAY KNOX SURVIVORS**.

If macOS blocks the file because it is downloaded and unsigned, open **System Settings → Privacy & Security** and allow it only when it came from this repository's Releases page.

## What to report

Please include:

- operating system and version;
- Steam native or Flatpak on Linux;
- whether the launcher reached **READY**;
- what happened after pressing Play;
- the exact error shown, if any;
- `launcher.log` from the `KnoxSurvivors` folder in your home directory;
- Project Zomboid's `console.txt` when the game launched but the mod failed.

Do not post save files or logs publicly without checking them for personal paths first.

## Automated checks

The launcher build also verifies updater version filtering, release asset requirements,+checksum and manifest validation, stale/corrupt cache rejection, and atomic staged-update+activation using fixture data. It does not publish or contact GitHub during the test suite.

The build verifies published Workshop layout, linked and independently discovered Steam libraries, missing/mismatched
files, corrupt checksums, duplicate runtimes, and real child-process command quoting with
spaced paths. Windows also tests the bootstrap script with a temporary Steam/library
fixture. These checks do not start Project Zomboid or validate in-game NPC behavior.

The Windows argument regressions reject shell operators, variable expansion, embedded quotes,
and control characters. The native EXE receives game options directly; a fixture batch file
still verifies the fallback route's `%1 %2` forwarding. Tests cover JSON memory detection,
bundled-Java `PATH` priority while preserving the existing suffix, spaces, trailing backslashes,
and game paths containing ampersands and parentheses. Parser checks also retain unmatched-quote
errors and ensure Windows shell restrictions do not affect direct Linux/macOS arguments.

Maintainers can check a staged upload with `LauncherVerifier`, using the built main/test
classes and two arguments: the installed game directory, then the staging `Contents`
directory. The mod source repository is not needed by a subscriber's launcher.

# ZombieBuddy runtime isolation

ZombieBuddy remains a separate optional runtime. Test these cases on Windows before
publishing a launcher update:

1. No ZombieBuddy game-directory files: Knox launches normally and does not add it.
2. Valid `ZombieBuddy.jar` plus `zbNative.dll`, with no active agent option: the launcher
   reports it as installed but inactive and injects only the Knox agent.
3. The BAT or JSON contains `-agentlib:zbNative`: the launcher blocks before starting the game
   with a clear choice between the ZombieBuddy and Knox Launcher paths.
4. `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, or `JDK_JAVA_OPTIONS` contains an active ZombieBuddy
   agent: the launcher blocks and identifies the responsible environment variable.
5. Launch normally with ZombieBuddy and separately through the Knox Launcher. Confirm each
   launch reports its own bridge-ready source and never loads both patch systems together.

Subscription without ZombieBuddy's separate installation is expected to remain unavailable;
the Knox launcher does not silently execute native code directly from a Workshop subscription.

# Debug mode

1. Leave **Enable Project Zomboid Debug Mode** unchecked and launch. Confirm the normal game
   starts without debug tools.
2. Check it and launch again. Confirm Project Zomboid starts with its normal debug tools and
   the launcher log reports `debug=true`.
3. Confirm both launches still report the Knox bridge ready. Run this launcher test with
   ZombieBuddy inactive; test the ZombieBuddy path in a separate normal game launch.
4. On Windows, confirm the native EXE applies `ProjectZomboid64.json`, Debug and custom game
   options work, and the launcher leaves the JSON unchanged. If testing the BAT fallback,
   confirm it clearly rejects more than two forwarded game options.
