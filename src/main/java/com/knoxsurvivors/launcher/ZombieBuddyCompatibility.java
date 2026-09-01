package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional detection only; ZombieBuddy remains separately installed and owned. */
final class ZombieBuddyCompatibility {
    private static final String PREMAIN = "me.zed_0xff.zombie_buddy.Agent";
    private static final Pattern WINDOWS_JSON_OPTION = Pattern.compile(
        "\\\"(-agentlib:zbNative(?:=[A-Za-z0-9_=,.-]+)?)\\\"", Pattern.CASE_INSENSITIVE
    );

    record Result(String option, String state) {
        boolean enabled() { return option != null; }
    }

    static Result inspect(LauncherInstallation installation) {
        Path game = installation.gameDirectory();
        if (installation.platform() == Platform.WINDOWS) {
            Path jar = game.resolve("ZombieBuddy.jar");
            Path nativeAgent = game.resolve("zbNative.dll");
            if (validJar(jar) && Files.isRegularFile(nativeAgent)) {
                if (launcherAlreadyConfigures(installation.gameLauncher(), "-agentlib:zbnative")) {
                    return new Result(null, "enabled-by-game-launcher");
                }
                String configured = windowsJsonOption(game.resolve("ProjectZomboid64.json"));
                return new Result(configured != null ? configured : "-agentlib:zbNative",
                    configured != null ? "enabled-windows-json" : "enabled-windows-native");
            }
            if (Files.exists(jar) || Files.exists(nativeAgent)) {
                return new Result(null, "incomplete-windows-install");
            }
            return new Result(null, "not-installed");
        }
        List<Path> candidates = installation.platform() == Platform.MAC
            ? List.of(
                game.resolve("Project Zomboid.app/Contents/Java/ZombieBuddy.jar"),
                game.resolve("ZombieBuddy.jar")
            )
            : List.of(game.resolve("projectzomboid/ZombieBuddy.jar"), game.resolve("ZombieBuddy.jar"));
        for (Path jar : candidates) {
            if (validJar(jar)) {
                if (launcherAlreadyConfigures(installation.gameLauncher(), "zombiebuddy.jar")) {
                    return new Result(null, "enabled-by-game-launcher");
                }
                return new Result("-javaagent:\"" + jar.toAbsolutePath() + "\"", "enabled-java-agent");
            }
        }
        for (Path jar : candidates) if (Files.exists(jar)) return new Result(null, "invalid-java-agent");
        return new Result(null, "not-installed");
    }

    private static String windowsJsonOption(Path json) {
        try {
            Matcher matcher = WINDOWS_JSON_OPTION.matcher(Files.readString(json));
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException unavailable) {
            return null;
        }
    }

    private static boolean launcherAlreadyConfigures(Path launcher, String marker) {
        try {
            return Files.isRegularFile(launcher)
                && Files.readString(launcher).toLowerCase(java.util.Locale.ROOT).contains(marker);
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static boolean validJar(Path path) {
        if (!Files.isRegularFile(path)) return false;
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getManifest() != null && PREMAIN.equals(
                jar.getManifest().getMainAttributes().getValue("Premain-Class")
            );
        } catch (IOException invalid) {
            return false;
        }
    }

    private ZombieBuddyCompatibility() { }
}
