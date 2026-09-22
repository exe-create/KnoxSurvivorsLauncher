KNOX SURVIVORS LAUNCHER 0.3.0-rc1

The launcher is optional. Knox Survivors can also be started directly through Steam on
Windows using the launch option on the Workshop page. Use ONE startup method, not both.

LAUNCHER METHOD
1. Install Project Zomboid through Steam.
2. Subscribe to Knox Survivors (Workshop ID 3749727604).
3. Let Steam finish downloading the mod.
4. Extract this entire launcher archive.
5. Windows: double-click "Launch Knox Survivors.cmd".
   Linux: run scripts/launch-knox-survivors.sh.
   macOS: double-click "Launch Knox Survivors.command".
6. Press PLAY KNOX SURVIVORS.
7. Enable Knox Survivors for the save in Project Zomboid.

The launcher verifies the Workshop mod/runtime and starts the normal Project Zomboid
executable with the Knox Java agent enabled only for that game process. It does not
change Project Zomboid's configured memory, request administrator access, or set a
permanent system environment variable.

SWITCHING TO STEAM-ONLY
Normal Project Zomboid saves, sandbox/mod settings and memory configuration stay in their
normal PZ locations. The launcher's Custom Launch Options preference is launcher-specific,
so copy any custom game arguments you still need into Steam after the final --. The Debug
checkbox is also launcher-specific; use the normal -debug game argument in Steam instead.

OPTIONAL ZOMBIEBUDDY
ZombieBuddy is not required. If a valid installation is present, the launcher preserves
its compatible agent configuration and places it before Knox.

SUPPORT LOG
Windows: %USERPROFILE%\KnoxSurvivors\launcher.log
Linux/macOS: ~/KnoxSurvivors/launcher.log

Workshop: https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604
Launcher releases: https://github.com/exe-create/KnoxSurvivorsLauncher/releases
