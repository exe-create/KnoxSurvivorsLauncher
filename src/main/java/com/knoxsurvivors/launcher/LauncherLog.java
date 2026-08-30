package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

final class LauncherLog {
    private static final Path PATH = Path.of(
        System.getProperty("user.home", "."), "KnoxSurvivors", "launcher.log"
    );

    private LauncherLog() {
    }

    static Path path() {
        return PATH;
    }

    static void write(String message) {
        String line = Instant.now() + " [KnoxSurvivorsLauncher] " + message
            + System.lineSeparator();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never prevent the game from launching.
        }
    }
}
