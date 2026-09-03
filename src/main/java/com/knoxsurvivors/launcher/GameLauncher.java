package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class GameLauncher {
    Process launch(LauncherInstallation installation, boolean debugMode) throws LauncherException {
        return launch(installation, debugMode, "");
    }

    Process launch(LauncherInstallation installation, boolean debugMode, String customOptions)
        throws LauncherException {
        List<String> command = command(installation, debugMode, customOptions);
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(installation.gameDirectory().toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        String existing = builder.environment().getOrDefault("JAVA_TOOL_OPTIONS", "").trim();
        String options = toolOptions(installation, existing);
        ZombieBuddyCompatibility.Result zombieBuddy = ZombieBuddyCompatibility.inspect(installation);
        builder.environment().put("JAVA_TOOL_OPTIONS", options);
        try {
            Process process = builder.start();
            LauncherLog.write("launched platform=" + installation.platform()
                + " game=" + installation.gameDirectory()
                + " workshop=" + installation.workshopDirectory()
                + " zombieBuddy=" + zombieBuddy.state()
                + " debug=" + debugMode
                + " customOptions=" + (customOptions == null || customOptions.isBlank() ? "none" : "set"));
            return process;
        } catch (IOException exception) {
            throw new LauncherException(
                "Project Zomboid could not be started. Verify the game through Steam and try again.",
                exception
            );
        }
    }

    static String toolOptions(LauncherInstallation installation, String inherited) throws LauncherException {
        String existing = inherited == null ? "" : inherited.trim();
        if (containsKnoxAgent(existing)) {
            throw new LauncherException(
                "Knox Survivors is already present in JAVA_TOOL_OPTIONS. Close other custom launchers and try again."
            );
        }
        List<String> additions = new ArrayList<>();
        ZombieBuddyCompatibility.Result zombieBuddy = ZombieBuddyCompatibility.inspect(installation);
        if (zombieBuddy.enabled() && !containsZombieBuddyAgent(existing)) {
            additions.add(zombieBuddy.option());
        }
        additions.add("-javaagent:\"" + installation.agentJar().toAbsolutePath() + "\"=pz-game");
        if (!existing.isEmpty()) additions.add(0, existing);
        return String.join(" ", additions);
    }

    private static boolean containsKnoxAgent(String options) {
        return options.toLowerCase(java.util.Locale.ROOT).contains("knox-agent-");
    }

    private static boolean containsZombieBuddyAgent(String options) {
        String value = options.toLowerCase(java.util.Locale.ROOT);
        return value.contains("-agentlib:zbnative")
            || (value.contains("-javaagent:") && value.contains("zombiebuddy.jar"));
    }

    static List<String> command(LauncherInstallation installation) throws LauncherException {
        return command(installation, false, "");
    }

    static List<String> command(LauncherInstallation installation, boolean debugMode)
        throws LauncherException {
        return command(installation, debugMode, "");
    }

    static List<String> command(LauncherInstallation installation, boolean debugMode, String customOptions)
        throws LauncherException {
        List<String> command = new ArrayList<>();
        if (installation.platform() == Platform.WINDOWS) {
            command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
            command.add("/d");
            command.add("/s");
            command.add("/c");
            command.add("\"\"" + installation.gameLauncher().toAbsolutePath() + "\"\"");
        } else if (installation.gameLauncher().getFileName().toString().endsWith(".sh")) {
            command.add("/bin/sh");
            command.add(installation.gameLauncher().toAbsolutePath().toString());
        } else {
            command.add(installation.gameLauncher().toAbsolutePath().toString());
        }
        if (debugMode) command.add("-debug");
        command.addAll(parseLaunchOptions(customOptions));
        return command;
    }

    static List<String> parseLaunchOptions(String value) throws LauncherException {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (quoted) {
                if (ch == quote) {
                    quoted = false;
                } else if (ch == '\\' && i + 1 < value.length() && value.charAt(i + 1) == quote) {
                    token.append(quote);
                    i++;
                } else {
                    token.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quoted = true;
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (token.length() > 0) {
                    result.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(ch);
            }
        }
        if (quoted) throw new LauncherException("Custom launch options contain an unmatched quote.");
        if (token.length() > 0) result.add(token.toString());
        return result;
    }
}
