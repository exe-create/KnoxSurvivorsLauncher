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
and control characters. A fixture batch file forwards `%1 %2` to a real Java argument recorder
to check no options, Debug plus custom options, spaces, trailing backslashes, and game paths
containing ampersands and parentheses. Parser checks also retain unmatched-quote errors and
ensure Windows shell restrictions do not affect direct Linux/macOS arguments.

Maintainers can check a staged upload with `LauncherVerifier`, using the built main/test
classes and two arguments: the installed game directory, then the staging `Contents`
directory. The mod source repository is not needed by a subscriber's launcher.

# Optional ZombieBuddy compatibility

ZombieBuddy remains a separate optional installation. Test these cases on Windows before
publishing a launcher update:

1. No ZombieBuddy game-directory files: Knox launches normally and does not add it.
2. Valid `ZombieBuddy.jar` plus `zbNative.dll`: the launcher status reports detection;
   `JAVA_TOOL_OPTIONS` places `-agentlib:zbNative` before the Knox agent.
3. The BAT already contains `-agentlib:zbNative`: ZombieBuddy is composed before Knox in
   `JAVA_TOOL_OPTIONS`; ZombieBuddy safely ignores the later BAT entry.
4. A custom inherited ZombieBuddy option such as `verbosity=2` remains byte-for-byte intact.
5. With ZombieBuddy and FastLoading enabled, confirm the `ZombieBuddy` Lua global and the
   FastLoading Java class exist, while the Knox bridge still reports ready.

Subscription without ZombieBuddy's separate installation is expected to remain unavailable;
the Knox launcher does not silently execute native code directly from a Workshop subscription.

# Debug mode

1. Leave **Enable Project Zomboid Debug Mode** unchecked and launch. Confirm the normal game
   starts without debug tools.
2. Check it and launch again. Confirm Project Zomboid starts with its normal debug tools and
   the launcher log reports `debug=true`.
3. Confirm both launches still report the Knox bridge ready. If ZombieBuddy is installed,
   also confirm its Lua global remains available in both modes.
4. On Windows, confirm Debug plus one quoted custom option works and a third combined option is
   rejected clearly rather than silently disappearing in `ProjectZomboid64.bat`.
