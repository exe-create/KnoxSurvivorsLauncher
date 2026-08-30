package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class GameLauncher {
    Process launch(LauncherInstallation installation) throws LauncherException {
        List<String> command = command(installation);
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(installation.gameDirectory().toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        String agent = "-javaagent:\"" + installation.agentJar().toAbsolutePath() + "\"=pz-game";
        String existing = builder.environment().getOrDefault("JAVA_TOOL_OPTIONS", "").trim();
        if (existing.contains("knox-agent-")) {
            throw new LauncherException(
                "Knox Survivors is already present in JAVA_TOOL_OPTIONS. Close other custom launchers and try again."
            );
        }
        builder.environment().put(
            "JAVA_TOOL_OPTIONS",
            existing.isEmpty() ? agent : existing + " " + agent
        );
        try {
            Process process = builder.start();
            LauncherLog.write("launched platform=" + installation.platform()
                + " game=" + installation.gameDirectory()
                + " workshop=" + installation.workshopDirectory());
            return process;
        } catch (IOException exception) {
            throw new LauncherException(
                "Project Zomboid could not be started. Verify the game through Steam and try again.",
                exception
            );
        }
    }

    static List<String> command(LauncherInstallation installation) {
        List<String> command = new ArrayList<>();
        if (installation.platform() == Platform.WINDOWS) {
            command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
            command.add("/d");
            command.add("/s");
            command.add("/c");
            command.add("\"" + installation.gameLauncher().toAbsolutePath() + "\"");
        } else if (installation.gameLauncher().getFileName().toString().endsWith(".sh")) {
            command.add("/bin/sh");
            command.add(installation.gameLauncher().toAbsolutePath().toString());
        } else {
            command.add(installation.gameLauncher().toAbsolutePath().toString());
        }
        return command;
    }
}
