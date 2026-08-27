package com.knoxsurvivors.launcher;

import java.nio.file.Path;

record LauncherInstallation(
    Path steamDirectory,
    Path gameDirectory,
    Path workshopDirectory,
    Path modDirectory,
    Path agentJar,
    Path gameLauncher,
    Platform platform
) {
}
