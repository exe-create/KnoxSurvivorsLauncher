package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detection only. The Knox Launcher never injects, preserves, or composes ZombieBuddy.
 * ZombieBuddy belongs to the separate Steam/ZombieBuddy runtime path.
 */
final class ZombieBuddyCompatibility {
    private static final Pattern WINDOWS_AGENT_OPTION = Pattern.compile(
        "(-agentlib:zbNative(?:=[^\\s\\\"']+)?)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAVA_AGENT_OPTION = Pattern.compile(
        "-javaagent:[^\\r\\n]*ZombieBuddy\\.jar", Pattern.CASE_INSENSITIVE
    );

    record Result(boolean active, boolean installed, String state) { }

    static Result inspect(LauncherInstallation installation) {
        Path game = installation.gameDirectory();
        if (installation.platform() == Platform.WINDOWS) {
            String configured = windowsAgentOption(installation.gameLauncher());
            if (configured == null) {
                configured = windowsAgentOption(game.resolve("ProjectZomboid64.json"));
            }
            boolean installed = Files.isRegularFile(game.resolve("ZombieBuddy.jar"))
                || Files.isRegularFile(game.resolve("zbNative.dll"));
            if (configured != null) {
                return new Result(true, installed, "configured-separate-runtime");
            }
            return new Result(false, installed, installed ? "installed-not-injected" : "not-installed");
        }

        List<Path> configs = installation.platform() == Platform.MAC
            ? List.of(
                installation.gameLauncher(),
                game.resolve("Project Zomboid.app/Contents/MacOS/Project Zomboid"),
                game.resolve("Project Zomboid.app/Contents/MacOS/JavaAppLauncher")
            )
            : List.of(installation.gameLauncher(), game.resolve("projectzomboid.sh"));

        for (Path config : configs) {
            if (javaAgentConfigured(config)) {
                return new Result(true, true, "configured-separate-runtime");
            }
        }

        boolean installed = installation.platform() == Platform.MAC
            ? Files.isRegularFile(game.resolve("Project Zomboid.app/Contents/Java/ZombieBuddy.jar"))
                || Files.isRegularFile(game.resolve("ZombieBuddy.jar"))
            : Files.isRegularFile(game.resolve("projectzomboid/ZombieBuddy.jar"))
                || Files.isRegularFile(game.resolve("ZombieBuddy.jar"));
        return new Result(false, installed, installed ? "installed-not-injected" : "not-installed");
    }

    private static String windowsAgentOption(Path configuration) {
        try {
            if (configuration == null || !Files.isRegularFile(configuration)) return null;
            Matcher matcher = WINDOWS_AGENT_OPTION.matcher(Files.readString(configuration));
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException unavailable) {
            return null;
        }
    }

    private static boolean javaAgentConfigured(Path configuration) {
        try {
            if (configuration == null || !Files.isRegularFile(configuration)) return false;
            return JAVA_AGENT_OPTION.matcher(Files.readString(configuration)).find();
        } catch (IOException unavailable) {
            return false;
        }
    }

    private ZombieBuddyCompatibility() { }
}
