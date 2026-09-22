package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
        append(Instant.now() + " [KnoxSurvivorsLauncher] " + message + System.lineSeparator());
    }

    static void writeException(String context, Throwable problem) {
        if (problem == null) {
            write(context + ": <no exception details>");
            return;
        }
        write(context + ": " + problem);
        StringWriter buffer = new StringWriter();
        problem.printStackTrace(new PrintWriter(buffer));
        for (String line : buffer.toString().split("\\R")) {
            append(Instant.now() + " [KnoxSurvivorsLauncher][detail] " + line + System.lineSeparator());
        }
    }

    static void sessionSnapshot() {
        write("diagnostic javaHome=" + System.getProperty("java.home")
            + " cwd=" + System.getProperty("user.dir")
            + " userHome=" + System.getProperty("user.home")
            + " arch=" + System.getProperty("os.arch"));
    }

    private static void append(String text) {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never prevent the game from launching.
        }
    }
}
