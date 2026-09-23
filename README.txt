KNOX SURVIVORS LAUNCHER 0.3.0

The current Knox Survivors Java NPC runtime is started through this launcher.
Steam Workshop still installs and updates the mod itself.

HOW TO PLAY
1. Install Project Zomboid through Steam.
2. Subscribe to Knox Survivors (Workshop ID 3749727604).
3. Let Steam finish downloading/updating the mod.
4. Extract this entire launcher archive.
5. Windows: double-click "Launch Knox Survivors.cmd".
   Linux: run scripts/launch-knox-survivors.sh.
   macOS: double-click "Launch Knox Survivors.command".
6. Wait for the system checks to finish.
7. Green = verified, amber = checking/non-blocking warning, red = failed.
8. Press PLAY KNOX SURVIVORS when the launcher says READY.
9. Enable Knox Survivors for the save in Project Zomboid.

The launcher verifies the Workshop mod/runtime and starts Project Zomboid with the Knox
Java agent enabled only for that game process. It does not require administrator access,
set a permanent system environment variable, or replace Project Zomboid's normal saves
and settings.

If a check fails, read the matching SYSTEM CHECK row first. The launcher also writes a
more detailed diagnostic log here:
Windows: %USERPROFILE%\KnoxSurvivors\launcher.log
Linux/macOS: ~/KnoxSurvivors/launcher.log

ZOMBIEBUDDY
ZombieBuddy is not required. Choose one runtime per launch. Launch normally with
ZombieBuddy, or disable its game-launcher configuration before using the Knox Launcher.
An installed but inactive ZombieBuddy copy is allowed.

Workshop: https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604
Launcher releases: https://github.com/exe-create/KnoxSurvivorsLauncher/releases
