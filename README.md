# Knox Survivors Launcher

[![Windows, Linux, and macOS checks](https://github.com/exe-create/KnoxSurvivorsLauncher/actions/workflows/release.yml/badge.svg)](https://github.com/exe-create/KnoxSurvivorsLauncher/actions/workflows/release.yml)

This is the small companion launcher for the Project Zomboid 42.20 Knox Survivors
IsoPlayer rebuild.

Steam Workshop installs and updates the mod. The launcher verifies that the subscribed
files are complete, checks the Knox Java runtime checksum, and starts the normal Project
Zomboid executable with that runtime enabled for the game process. It does not patch the
game, modify Steam, request administrator access, or set permanent environment variables.

## Download and use

1. Install Project Zomboid through Steam.
2. [Subscribe to Knox Survivors](https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604).
3. Download the Windows, Linux, or macOS archive from [Releases](https://github.com/exe-create/KnoxSurvivorsLauncher/releases). Use the preview ZIP for your system, not GitHub's source-code ZIP.
4. Extract the complete archive. Do not run it from inside the ZIP.
5. Start the launcher:
   - Windows: double-click **Launch Knox Survivors.cmd**.
   - Linux: open a terminal in the extracted folder and run **scripts/launch-knox-survivors.sh**.
   - macOS: double-click **Launch Knox Survivors.command**.
6. Press **PLAY KNOX SURVIVORS**.
7. In Project Zomboid, enable Knox Survivors for the save you want to use.

On Windows, the launcher checks Steam's configured library list and also looks for common
Steam/SteamLibrary locations on other local drives. Project Zomboid may therefore live on
`D:`, `E:`, or another Steam library drive without editing the launcher scripts.

To use Project Zomboid's normal debug tools, select **Enable Project Zomboid Debug Mode**
before pressing Play. It is off by default and simply passes the standard `-debug` argument
to the existing game launcher.

### Custom launch options

The launcher includes a **Custom Launch Options** field for options you would normally place
in Steam's Project Zomboid launch-options box. The value is remembered for later launches.
For example, a player using a custom Project Zomboid data folder can enter:

```text
-cachedir="D:\Zomboid"
```

This is useful when a normal Steam launch depends on a custom cache/profile directory for
saves, mods, logs, or other user data.

Project Zomboid's current Windows batch launcher forwards at most two game options. The Knox
launcher rejects extras with a clear message instead of silently dropping them; Debug mode counts
as one option. Linux and macOS do not use that Windows batch-file limit.

Use single or double quotes to group an option containing spaces. Backslashes are literal,
so a quoted directory can end in `\`. Backslash-escaped quotes are not supported; use the
other quote style to include a literal quote on Linux/macOS. On Windows, shell operators
(`& | < > ^`), environment-variable syntax (`%` and `!`), literal quotes within an option,
and control characters other than tabs are rejected. Enter expanded paths such as
`C:\Users\Gary\Zomboid` instead of `%USERPROFILE%\Zomboid`. Windows game installation paths
containing `%` or `!` are also rejected to prevent command expansion.

The launcher uses Project Zomboid's bundled Java runtime. A separate Java 17 installation
is not required.

### Launcher updates

The launcher checks the public Knox Survivors Launcher releases in the background at startup.
When a newer non-draft release is available, it can download the standalone launcher JAR,
verify its SHA-256 checksum and release metadata, then restart into the staged copy.
The running launcher is never overwritten. A network failure leaves the normal Play button
available, and updates are stored under the user's `KnoxSurvivors/launcher-updates` folder.

### Optional ZombieBuddy compatibility

ZombieBuddy is not required by Knox Survivors. If you use it, complete ZombieBuddy's own
installation first; subscribing to its Workshop item alone does not install its native/Java
agent into Project Zomboid. The Knox launcher detects a valid installed ZombieBuddy agent,
preserves an existing configuration, and places it before the Knox agent. On Windows this
supports `zbNative.dll` plus
`ZombieBuddy.jar` and carries its compatible options from `ProjectZomboid64.json`;
Linux/macOS support the separately installed `ZombieBuddy.jar`.

When an existing platform launcher also contains ZombieBuddy, Knox composes ZombieBuddy first
in the child JVM environment. ZombieBuddy's own duplicate-install guard safely ignores the later
platform-launcher entry. This avoids Knox initializing before ZombieBuddy's Lua exposure is ready.

You do not need to edit `ProjectZomboid64.bat` specifically for Knox compatibility. The
launcher executes Project Zomboid's existing platform launcher, so its normal JVM arguments
remain authoritative, and inherited `JAVA_TOOL_OPTIONS` are preserved. Knox does not copy,
approve, update, or silently enable another mod's native code.

If launch or verification fails, attach `KnoxSurvivors/launcher.log` from your home folder
to the bug report. The log contains launcher paths and results but no Steam password or token.

> The public Workshop item may still contain the older Knox Survivors release while the
> rebuild is being tested. The launcher detects that situation and refuses to combine an
> old Lua build with the new Java runtime.

## Supported systems

Windows is the primary test platform. Linux/macOS packages have automated checks,
but actual game launch and gameplay on those systems still need tester confirmation.
Flatpak permissions, macOS security prompts, and game runtime layouts can differ.

- Windows Steam installation, including extra Steam library drives
- Linux Steam installation, including common native and Flatpak locations
- macOS Steam installation

The shared launcher is written in Java 17 and runs on Project Zomboid's own newer bundled
runtime. Platform wrappers only locate that bundled runtime and open the same launcher UI.

The mod's source repository can stay private. Players do not need access to it or a
GitHub account: the public launcher uses the Java runtime delivered with the Workshop mod.

## Build

Windows:

```powershell
.\scripts\build.ps1
```

Linux or macOS:

```sh
./scripts/build.sh
```

The build compiles with `--release 17`, runs the standalone locator/validation verifier,
and writes separate Windows, Linux, and macOS archives under `dist`. GitHub verifies the
same source on actual Windows, Linux, and macOS runners before publishing a tagged release.

## Permission

This repository is publicly visible but is not open source. Official releases may be
downloaded and used with Knox Survivors. No permission is granted to copy, modify,
redistribute, repackage, publish, or reuse the source without written permission. See
[LICENSE.md](LICENSE.md).
