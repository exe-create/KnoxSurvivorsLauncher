# Knox Survivors Launcher

[![Windows, Linux, and macOS checks](https://github.com/exe-create/KnoxSurvivorsLauncher/actions/workflows/release.yml/badge.svg)](https://github.com/exe-create/KnoxSurvivorsLauncher/actions/workflows/release.yml)

The Knox Survivors Launcher starts the current Project Zomboid Build 42 Knox Survivors Java NPC runtime. Steam Workshop still installs and updates the mod; the launcher handles the runtime check and game startup.

For the current Java build, the launcher is the supported startup method. A future Workshop-native runtime is being investigated separately, but users should not need to edit Java, Windows PATH, Steam launch options, or Project Zomboid files for this launcher build.

**Launcher version:** `0.3.0-rc3`

## What the launcher does

Steam Workshop still installs and updates the mod. The launcher:

- locates Project Zomboid and Workshop ID `3749727604`;
- verifies Mod ID `KnoxSurvivors`;
- accepts the current stable `knox-agent.jar` runtime filename (and legacy versioned agent names for compatibility);
- checks the runtime manifest and SHA-256 sidecar;
- starts Project Zomboid's normal platform launcher/executable with the Knox Java agent enabled for that child game process;
- preserves inherited `JAVA_TOOL_OPTIONS`; and
- leaves Project Zomboid's own memory configuration alone.

It does **not** patch Project Zomboid, modify Steam, require administrator access, or create a permanent system environment variable.

## Download and use

1. Install Project Zomboid through Steam.
2. Subscribe to Knox Survivors: https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604
3. Download the current Windows, Linux, or macOS ZIP from Releases: https://github.com/exe-create/KnoxSurvivorsLauncher/releases
4. Extract the complete archive. Do not run it from inside the ZIP.
5. Start it:
   - Windows: double-click **Launch Knox Survivors.cmd**
   - Linux: run **scripts/launch-knox-survivors.sh**
   - macOS: double-click **Launch Knox Survivors.command**
6. Press **PLAY KNOX SURVIVORS**.
7. Enable Knox Survivors for the save in Project Zomboid.

## Custom launch options

The **Custom Launch Options** field is for game arguments you would otherwise add to Project Zomboid's Steam launch options. The value is remembered for later launcher starts.

On Windows, the launcher intentionally prefers `ProjectZomboid64.bat` so Project Zomboid's bundled Java runtime is used consistently with the Knox Java agent. The BAT currently forwards at most two game options; the Knox launcher rejects extras rather than silently dropping them. Debug mode counts as one option.

For safety, Windows shell operators and environment-variable expressions are rejected in custom options. Enter expanded paths instead of `%USERPROFILE%`-style variables.

## Project Zomboid memory

Project Zomboid owns its JVM memory setting. The launcher reads the effective configuration for display, but does not rewrite `ProjectZomboid64.json`, inject a competing heap flag, or allocate all installed RAM. Change memory through Project Zomboid's normal configuration.

The launcher uses Project Zomboid's bundled Java runtime; players do not need to install a separate Java runtime.

## Optional ZombieBuddy compatibility

ZombieBuddy is not required. If a valid ZombieBuddy installation is present, the launcher preserves its compatible agent configuration and orders it before the Knox agent. Knox does not install, update, approve or silently enable another mod's native code.

## Launcher updates

The launcher can check the public GitHub releases for a newer non-draft build. Updates are downloaded to the user's `KnoxSurvivors/launcher-updates` directory, verified against the release checksum and JAR metadata, and never overwrite the currently running JAR in place. A network failure does not block normal play.

## Support

If verification or startup fails, include the launcher log with the report:

- Windows: `%USERPROFILE%\KnoxSurvivors\launcher.log`
- Linux/macOS: `~/KnoxSurvivors/launcher.log`

Windows is the primary test platform. Linux/macOS packages are exercised by automated launcher checks, but real Project Zomboid behavior can still vary by platform/runtime layout.

## Build

Windows:

```powershell
.\scripts\build.ps1
```

Linux/macOS:

```sh
./scripts/build.sh
```

The build compiles with `--release 17`, runs the standalone launcher verifier and creates Windows, Linux and macOS archives under `dist`.

## Ownership and permission

The Knox Survivors launcher and Knox Survivors project code were built from the ground up and are owned by **.exe**. This repository is publicly visible for official distribution but is not open source. No permission is granted to copy, modify, redistribute, repackage, publish or reuse the source without written permission. See [LICENSE.md](LICENSE.md).
