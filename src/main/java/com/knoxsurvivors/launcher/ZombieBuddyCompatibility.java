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
    private static final Pattern WINDOWS_AGENT_OPTION = Pattern.compile(
        "(-agentlib:zbNative(?:=[^\\s\\\"']+)?)", Pattern.CASE_INSENSITIVE
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
                // JAVA_TOOL_OPTIONS is processed before the BAT's _JAVA_OPTIONS. Compose
                // ZombieBuddy here even when the BAT is already patched so its first
                // initialization is guaranteed to happen before the Knox Java agent.
                // ZombieBuddy itself guards a later duplicate entry from a patched BAT.
                String configured = windowsAgentOption(installation.gameLauncher());
                if (configured == null) {
                    configured = windowsAgentOption(game.resolve("ProjectZomboid64.json"));
                }
                return new Result(configured != null ? configured : "-agentlib:zbNative",
                    configured != null ? "enabled-windows-configured" : "enabled-windows-native");
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
                // The platform launcher may also contain this agent. Supplying it first
                // preserves deterministic ordering; ZombieBuddy ignores its later duplicate.
                return new Result("-javaagent:\"" + jar.toAbsolutePath() + "\"", "enabled-java-agent");
            }
        }
        for (Path jar : candidates) if (Files.exists(jar)) return new Result(null, "invalid-java-agent");
        return new Result(null, "not-installed");
    }

    private static String windowsAgentOption(Path configuration) {
        try {
            Matcher matcher = WINDOWS_AGENT_OPTION.matcher(Files.readString(configuration));
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException unavailable) {
            return null;
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
