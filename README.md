# Knox Survivors Launcher

This is the small companion launcher for the Project Zomboid 42.20 Knox Survivors
IsoPlayer rebuild.

Steam Workshop installs and updates the mod. The launcher verifies that the subscribed
files are complete, checks the Knox Java runtime checksum, and starts the normal Project
Zomboid executable with that runtime enabled for the game process. It does not patch the
game, modify Steam, request administrator access, or set permanent environment variables.

## Download and use

1. Install Project Zomboid through Steam.
2. [Subscribe to Knox Survivors](https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604).
3. Download the Windows, Linux, or macOS archive from [Releases](../../releases/latest).
4. Extract the complete archive. Do not run it from inside the ZIP.
5. Start the launcher:
   - Windows: double-click **Launch Knox Survivors.cmd**.
   - Linux: open a terminal in the extracted folder and run **scripts/launch-knox-survivors.sh**.
   - macOS: double-click **Launch Knox Survivors.command**.
6. Press **PLAY KNOX SURVIVORS**.
7. In Project Zomboid, enable Knox Survivors for the save you want to use.

The launcher uses Project Zomboid's bundled Java runtime. A separate Java 17 installation
is not required.

If launch or verification fails, attach `KnoxSurvivors/launcher.log` from your home folder
to the bug report. The log contains launcher paths and results but no Steam password or token.

> The public Workshop item may still contain the older Knox Survivors release while the
> rebuild is being tested. The launcher detects that situation and refuses to combine an
> old Lua build with the new Java runtime.

## Supported systems

- Windows Steam installation, including extra Steam library drives
- Linux Steam installation, including common native and Flatpak locations
- macOS Steam installation

The shared launcher is written in Java 17 and runs on Project Zomboid's own newer bundled
runtime. Platform wrappers only locate that bundled runtime and open the same launcher UI.

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
