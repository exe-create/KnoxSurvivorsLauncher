KNOX SURVIVORS LAUNCHER

1. Install Project Zomboid through Steam.
2. Subscribe to Knox Survivors on Steam Workshop:
   https://steamcommunity.com/sharedfiles/filedetails/?id=3749727604
3. Let Steam finish downloading the mod.
4. Extract this entire launcher archive.
5. Windows: double-click "Launch Knox Survivors.cmd".
   Linux: open a terminal here and run scripts/launch-knox-survivors.sh.
   macOS: double-click "Launch Knox Survivors.command".
6. Press PLAY KNOX SURVIVORS.
7. In Project Zomboid, make sure Knox Survivors is enabled for your save.

Always start Project Zomboid through this launcher when using the IsoPlayer rebuild.

OPTIONAL ZOMBIEBUDDY COMPATIBILITY

ZombieBuddy is not required. If you use it, complete ZombieBuddy's own installation first;
subscribing to its Workshop item alone is not enough. The Knox launcher will detect a valid
installed ZombieBuddy agent, preserve its options, and start it before Knox. If its own
platform launcher also supplies the agent later, ZombieBuddy's
duplicate guard ignores that later entry. You do not need to edit ProjectZomboid64.bat for Knox.

The launcher uses Project Zomboid's own bundled Java runtime. You do not need to install
Java 17 separately. It does not modify the game, request administrator access, or set
permanent system options. Steam Workshop installs and updates the mod; this launcher
verifies those files and enables the required Java agent only for the game process it starts.

If the launcher says the Workshop build is still the older release, the IsoPlayer rebuild
has not been published to Workshop yet. The launcher will intentionally refuse to mix the
old mod with the new Java runtime.

If something fails, include this support log with your report:
Windows: %USERPROFILE%\KnoxSurvivors\launcher.log
Linux/macOS: ~/KnoxSurvivors/launcher.log
