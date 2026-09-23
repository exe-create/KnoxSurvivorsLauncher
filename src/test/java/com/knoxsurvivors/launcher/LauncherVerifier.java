package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Verifies Knox-only launcher behavior after separating the ZombieBuddy runtime path. */
public final class LauncherVerifier {
    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("knox-launcher-verifier-");
        try {
            verifyLibraryParsing(root);
            verifySplitLibraryDiscovery(root);
            verifyIndependentRootDiscovery(root);
            verifyValidationAndKnoxJarDiscovery(root);
            verifyRuntimeIsolation(root);
            verifyCommands(root);
            verifyChildLaunch(root);
            LaunchOptionsVerifier.verify(root);
            UpdaterVerifier.verify(root);
            if (arguments.length >= 2) {
                verifyPublishedPackage(Path.of(arguments[0]), Path.of(arguments[1]));
            }
            System.out.println("launcher verification passed - Knox and ZombieBuddy runtimes isolated");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void verifyLibraryParsing(Path root) throws Exception {
        Path steam = root.resolve("Steam");
        Path second = root.resolve("Second Library");
        Files.createDirectories(steam.resolve("steamapps"));
        Files.writeString(
            steam.resolve("steamapps/libraryfolders.vdf"),
            "\"libraryfolders\"\n{\n \"1\" { \"path\" \""
                + second.toString().replace("\\", "\\\\") + "\" }\n}",
            StandardCharsets.UTF_8
        );
        List<Path> found = SteamLocator.libraries(steam);
        require(found.contains(steam.toAbsolutePath().normalize()), "primary library missing");
        require(found.contains(second.toAbsolutePath().normalize()), "secondary library missing");
    }

    private static void verifySplitLibraryDiscovery(Path root) throws Exception {
        Path steam = root.resolve("Split Steam");
        Path workshopLibrary = root.resolve("Split Workshop Library");
        Path game = steam.resolve("steamapps/common/ProjectZomboid");
        Path workshop = workshopLibrary.resolve("steamapps/workshop/content/108600/3749727604");
        Path mod = workshop.resolve("mods/KnoxSurvivors");
        Path jar = mod.resolve("java/knox-agent.jar");
        createValidInstallation(game, mod, jar, "0.3.0-rc1");
        Files.createDirectories(steam.resolve("steamapps"));
        Files.writeString(steam.resolve("steamapps/libraryfolders.vdf"),
            "\"libraryfolders\"\n{\n \"1\" { \"path\" \""
                + workshopLibrary.toString().replace("\\", "\\\\") + "\" }\n}",
            StandardCharsets.UTF_8);

        LauncherInstallation found = new SteamLocator().locateFromRoots(List.of(steam), Platform.WINDOWS);
        require(found.gameDirectory().equals(game.toAbsolutePath().normalize()),
            "game in primary library not found");
        require(found.workshopDirectory().equals(workshop.toAbsolutePath().normalize()),
            "Workshop item in secondary library not found");
        new InstallationValidator().validate(found);

        Path duplicate = jar.resolveSibling("knox-agent-stale.jar");
        Files.copy(jar, duplicate);
        expectFailure(() -> new SteamLocator().locateFromRoots(List.of(steam), Platform.WINDOWS),
            "Multiple Knox Java runtimes");
        Files.delete(duplicate);
    }

    private static void verifyIndependentRootDiscovery(Path root) throws Exception {
        Path gameRoot = root.resolve("Independent Game Root");
        Path workshopRoot = root.resolve("Independent Workshop Root");
        Path game = gameRoot.resolve("steamapps/common/ProjectZomboid");
        Path workshop = workshopRoot.resolve("steamapps/workshop/content/108600/3749727604");
        Path mod = workshop.resolve("mods/KnoxSurvivors");
        Path jar = mod.resolve("java/knox-agent.jar");
        createValidInstallation(game, mod, jar, "0.3.0-rc1");

        LauncherInstallation found = new SteamLocator().locateFromRoots(
            List.of(gameRoot, workshopRoot), Platform.WINDOWS);
        require(found.gameDirectory().equals(game.toAbsolutePath().normalize()),
            "independent game root was not retained");
        require(found.workshopDirectory().equals(workshop.toAbsolutePath().normalize()),
            "independent Workshop root was not combined with the game root");
        new InstallationValidator().validate(found);

        Files.writeString(game.resolve("ProjectZomboid64.exe"), "fixture");
        String configured = "{\"vmArgs\":[\"-Xmx8192m\"]}";
        Files.writeString(game.resolve("ProjectZomboid64.json"), configured);
        LauncherInstallation rediscovered = new SteamLocator().locateFromRoots(
            List.of(gameRoot, workshopRoot), Platform.WINDOWS);
        require(rediscovered.gameLauncher().equals(game.resolve("ProjectZomboid64.bat")),
            "Windows must prefer the bundled-Java BAT launcher for Knox agent injection");
        require(Files.readString(game.resolve("ProjectZomboid64.json")).equals(configured),
            "user memory configuration must remain untouched");
    }

    private static void verifyValidationAndKnoxJarDiscovery(Path root) throws Exception {
        Path steam = root.resolve("Discovery Steam");
        Path game = steam.resolve("steamapps/common/ProjectZomboid");
        Path workshop = steam.resolve("steamapps/workshop/content/108600/3749727604");
        Path mod = workshop.resolve("mods/KnoxSurvivors");
        Path jar = mod.resolve("java/knox-agent.jar");
        Files.createDirectories(game.resolve("jre64/bin"));
        Files.createDirectories(mod.resolve("42"));
        Files.createDirectories(jar.getParent());
        Files.writeString(game.resolve("projectzomboid.jar"), "fixture");
        Files.writeString(game.resolve("ProjectZomboid64.bat"), "@echo off");
        Files.writeString(game.resolve("jre64/bin/java.exe"), "fixture");
        Files.writeString(mod.resolve("mod.info"), "name=Knox Survivors\nid=KnoxSurvivors\n");
        Files.copy(mod.resolve("mod.info"), mod.resolve("42/mod.info"));
        Files.writeString(mod.resolve("42/knox-runtime.properties"),
            "runtime=zombie-buddy-java-mod-v1\nlegacyAgentCompatible=true\nruntimeVersion=0.3.0-rc1\n");
        createAgent(jar, "0.3.0-rc1");
        Files.writeString(Path.of(jar + ".sha256"), sha256(jar) + "  " + jar.getFileName());

        LauncherInstallation found = new SteamLocator().locateFromRoots(List.of(steam), Platform.WINDOWS);
        require(found.agentJar().equals(jar.toAbsolutePath().normalize()),
            "launcher did not select knox-agent.jar");
        new InstallationValidator().validate(found);

        Path checksum = Path.of(jar + ".sha256");
        String goodChecksum = Files.readString(checksum);
        Files.writeString(checksum, "0".repeat(64));
        expectFailure(() -> new InstallationValidator().validate(found), "checksum");
        Files.writeString(checksum, goodChecksum);
        createAgent(jar, "other-version");
        expectFailure(() -> new InstallationValidator().validate(found), "expects runtime");
        createAgent(jar, "0.3.0-rc1");
        Files.writeString(checksum, sha256(jar) + "  " + jar.getFileName());
        Path marker = mod.resolve("42/knox-runtime.properties");
        String goodMarker = Files.readString(marker);
        Files.writeString(marker, goodMarker.replace("legacyAgentCompatible=true", "legacyAgentCompatible=false"));
        expectFailure(() -> new InstallationValidator().validate(found), "not compatible");
        Files.writeString(marker, goodMarker);
        new InstallationValidator().validate(found);

        // A ZombieBuddy JAR in the game directory must never replace Knox's selected agent.
        createJar(game.resolve("ZombieBuddy.jar"), "me.zed_0xff.zombie_buddy.Agent", "2.3.2");
        Files.writeString(game.resolve("zbNative.dll"), "fixture");
        LauncherInstallation withZombieBuddy = new SteamLocator().locateFromRoots(List.of(steam), Platform.WINDOWS);
        require(withZombieBuddy.agentJar().equals(jar.toAbsolutePath().normalize()),
            "ZombieBuddy was mistaken for the Knox agent");
    }

    private static void verifyRuntimeIsolation(Path root) throws Exception {
        Path game = root.resolve("Runtime Isolation Game");
        Path knox = root.resolve("Runtime Isolation Mod/knox-agent-test.jar");
        Path launcher = game.resolve("ProjectZomboid64.bat");
        Files.createDirectories(game);
        Files.createDirectories(knox.getParent());
        Files.writeString(knox, "fixture");
        Files.writeString(launcher, "@echo off");

        LauncherInstallation installation = new LauncherInstallation(
            root, game, root, root, knox, launcher, Platform.WINDOWS
        );

        String normal = GameLauncher.toolOptions(installation, "-Dexisting=value");
        require(normal.startsWith("-Dexisting=value "), "non-agent inherited option was not preserved");
        require(normal.contains("knox-agent-test.jar"), "Knox agent missing from launcher options");
        require(!normal.toLowerCase().contains("zombiebuddy"), "launcher injected ZombieBuddy by name");
        require(!normal.toLowerCase().contains("zbnative"), "launcher injected ZombieBuddy native agent");

        // Installed but inactive ZombieBuddy is allowed; the Knox launcher still injects only Knox.
        createJar(game.resolve("ZombieBuddy.jar"), "me.zed_0xff.zombie_buddy.Agent", "2.3.2");
        Files.writeString(game.resolve("zbNative.dll"), "fixture");
        String installedOnly = GameLauncher.toolOptions(installation, "");
        require(installedOnly.contains("knox-agent-test.jar") && !installedOnly.contains("zbNative"),
            "inactive ZombieBuddy installation was composed into Knox launch");

        expectFailure(() -> GameLauncher.toolOptions(installation, "-agentlib:zbNative"),
            "Pick one runtime");
        expectFailure(() -> GameLauncher.rejectZombieBuddyEnvironment(
            "_JAVA_OPTIONS", "-agentlib:zbNative"), "_JAVA_OPTIONS");
        expectFailure(() -> GameLauncher.rejectZombieBuddyEnvironment(
            "JDK_JAVA_OPTIONS", "-javaagent:C:\\PZ\\ZombieBuddy.jar"), "JDK_JAVA_OPTIONS");

        Files.writeString(game.resolve("ProjectZomboid64.json"),
            "{\"vmArgs\":[\"-Xmx3072m\",\"-agentlib:zbNative\"]}");
        expectFailure(() -> GameLauncher.toolOptions(installation, ""), "Pick one runtime");
        Files.delete(game.resolve("ProjectZomboid64.json"));
    }

    private static void verifyCommands(Path root) throws Exception {
        Path game = root.resolve("Command Game");
        Files.createDirectories(game);
        Path batch = game.resolve("ProjectZomboid64.bat");
        Files.writeString(batch, "@echo off");
        Path agent = root.resolve("Command Mod/knox-agent-test.jar");
        Files.createDirectories(agent.getParent());
        Files.writeString(agent, "fixture");
        LauncherInstallation installation = new LauncherInstallation(
            root, game, root, root, agent, batch, Platform.WINDOWS
        );
        List<String> command = GameLauncher.command(installation, true, "-novoip");
        require(command.get(0).toLowerCase(java.util.Locale.ROOT).contains("cmd"),
            "Windows BAT launcher must run through cmd");
        require(command.get(command.size() - 1).contains("-debug"), "debug argument missing");
        require(command.get(command.size() - 1).contains("-novoip"), "custom argument missing");
    }

    private static void verifyChildLaunch(Path root) throws Exception {
        Path game = root.resolve("Game Folder With Spaces");
        Files.createDirectories(game);
        Platform platform = Platform.current();
        Path output = game.resolve("child-environment.txt");
        Path argumentOutput = game.resolve("child-argument.txt");
        Path executable = game.resolve(platform == Platform.WINDOWS
            ? "ProjectZomboid64.bat" : "projectzomboid.sh");
        Files.writeString(executable, platform == Platform.WINDOWS
            ? "@echo off\r\nset JAVA_TOOL_OPTIONS > \"" + output + "\"\r\n"
                + "echo %~1> \"" + argumentOutput + "\"\r\nexit /b 0\r\n"
            : "#!/bin/sh\nprintf '%s' \"$JAVA_TOOL_OPTIONS\" > '" + output + "'\n"
                + "printf '%s' \"$1\" > '" + argumentOutput + "'\n");
        Path agent = game.resolve("Mod Folder With Spaces/knox-agent-test.jar");
        Files.createDirectories(agent.getParent());
        Files.writeString(agent, "fixture");
        Process child = new GameLauncher().launch(new LauncherInstallation(
            root, game, root, root, agent, executable, platform), true);
        boolean finished = child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) child.destroyForcibly();
        require(finished, "native child launch timed out");
        require(child.exitValue() == 0, "native child launch/quoting failed");
        require(Files.readString(output).contains("-javaagent:\"" + agent.toAbsolutePath() + "\"=pz-game"),
            "child lost or split the Knox agent path");
        require(Files.readString(argumentOutput).trim().equals("-debug"),
            "native child launch lost the debug argument");
    }

    private static void verifyPublishedPackage(Path game, Path contents) throws Exception {
        Path mod = contents.resolve("mods/KnoxSurvivors");
        List<Path> jars;
        try (var files = Files.walk(contents, 6)) {
            jars = files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals("knox-agent.jar")
                    || path.getFileName().toString().matches("knox-agent-.*\\.jar"))
                .toList();
        }
        require(jars.size() == 1, "published Contents must contain exactly one Knox agent");
        Platform platform = Platform.current();
        Path executable = game.resolve(platform == Platform.WINDOWS
            ? "ProjectZomboid64.bat" : "projectzomboid.sh");
        new InstallationValidator().validate(new LauncherInstallation(
            game, game, contents, mod, jars.get(0), executable, platform
        ));
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }

    private static void expectFailure(CheckedAction action, String text) throws Exception {
        try {
            action.run();
        } catch (LauncherException expected) {
            require(expected.getMessage().contains(text),
                "unexpected rejection: " + expected.getMessage());
            return;
        }
        throw new IllegalStateException("expected rejection containing: " + text);
    }

    private static void createAgent(Path path, String version) throws IOException {
        createJar(path, "com.knoxsurvivors.agent.KnoxAgent", version);
    }

    private static void createValidInstallation(Path game, Path mod, Path jar, String version)
        throws Exception {
        Files.createDirectories(game.resolve("jre64/bin"));
        Files.createDirectories(mod.resolve("42"));
        Files.createDirectories(jar.getParent());
        Files.writeString(game.resolve("projectzomboid.jar"), "fixture");
        Files.writeString(game.resolve("ProjectZomboid64.bat"), "@echo off");
        Files.writeString(game.resolve("jre64/bin/java.exe"), "fixture");
        Files.writeString(mod.resolve("mod.info"), "name=Knox Survivors\nid=KnoxSurvivors\n");
        Files.copy(mod.resolve("mod.info"), mod.resolve("42/mod.info"));
        Files.writeString(mod.resolve("42/knox-runtime.properties"),
            "runtime=zombie-buddy-java-mod-v1\nlegacyAgentCompatible=true\nruntimeVersion=" + version + "\n");
        createAgent(jar, version);
        Files.writeString(Path.of(jar + ".sha256"), sha256(jar) + "  " + jar.getFileName());
    }

    private static void createJar(Path path, String premain, String version) throws IOException {
        Files.createDirectories(path.getParent());
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Premain-Class", premain);
        attributes.putValue("Implementation-Version", version);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path), manifest)) { }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        );
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
