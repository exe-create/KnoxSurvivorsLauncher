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

public final class LauncherVerifier {
    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("knox-launcher-verifier-");
        try {
            verifyLibraryParsing(root);
            verifySplitLibraryDiscovery(root);
            verifyValidationAndCommands(root);
            verifyChildLaunch(root);
            if (arguments.length == 2) verifyPublishedPackage(Path.of(arguments[0]), Path.of(arguments[1]));
            System.out.println("launcher verification passed");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void verifySplitLibraryDiscovery(Path root) throws Exception {
        Path steam = root.resolve("Split Steam");
        Path workshopLibrary = root.resolve("Split Workshop Library");
        Path game = steam.resolve("steamapps/common/ProjectZomboid");
        Path workshop = workshopLibrary.resolve("steamapps/workshop/content/108600/3749727604");
        // Steam distributes the contents of Contents, without that parent directory.
        Path mod = workshop.resolve("mods/KnoxSurvivors");
        Path jar = mod.resolve("java/knox-agent-test.jar");
        Files.createDirectories(game);
        Files.createDirectories(mod.resolve("42"));
        Files.createDirectories(jar.getParent());
        Files.writeString(game.resolve("projectzomboid.jar"), "test");
        Files.writeString(game.resolve("ProjectZomboid64.bat"), "@echo off");
        Files.writeString(mod.resolve("mod.info"), "name=Knox Survivors\nid=KnoxSurvivors\n");
        Files.copy(mod.resolve("mod.info"), mod.resolve("42/mod.info"));
        Files.writeString(mod.resolve("42/knox-runtime.properties"),
            "runtime=iso-player-agent-v1\nlauncherCompatibility=1\nruntimeVersion=0.2.0\n");
        createAgent(jar, "0.2.0");
        Files.writeString(Path.of(jar + ".sha256"), sha256(jar) + "  " + jar.getFileName());
        Files.createDirectories(steam.resolve("steamapps"));
        Files.writeString(
            steam.resolve("steamapps/libraryfolders.vdf"),
            "\"libraryfolders\"\n{\n \"1\" { \"path\" \""
                + workshopLibrary.toString().replace("\\", "\\\\") + "\" }\n}",
            StandardCharsets.UTF_8
        );
        LauncherInstallation found = new SteamLocator().locateFromRoots(
            List.of(steam), Platform.WINDOWS
        );
        require(found.gameDirectory().equals(game.toAbsolutePath().normalize()),
            "game in primary library not found");
        require(found.workshopDirectory().equals(workshop.toAbsolutePath().normalize()),
            "Workshop item in secondary library not found");
        require(found.agentJar().equals(jar), "published runtime layout not discovered");
        new InstallationValidator().validate(found);
        Path duplicate = jar.resolveSibling("knox-agent-stale.jar");
        Files.copy(jar, duplicate);
        expectFailure(() -> new SteamLocator().locateFromRoots(List.of(steam), Platform.WINDOWS),
            "Multiple Knox Java runtimes");
        Files.delete(duplicate);
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

    private static void verifyValidationAndCommands(Path root) throws Exception {
        Path game = root.resolve("ProjectZomboid");
        Path workshop = root.resolve("3749727604");
        Path mod = workshop.resolve("Contents/mods/KnoxSurvivors");
        Path jar = workshop.resolve("java/build/libs/knox-agent-test.jar");
        Files.createDirectories(mod.resolve("42"));
        Files.createDirectories(jar.getParent());
        Files.createDirectories(game);
        Files.writeString(game.resolve("projectzomboid.jar"), "test");
        Files.writeString(game.resolve("ProjectZomboid64.bat"), "@echo off");
        Files.writeString(game.resolve("projectzomboid.sh"), "#!/bin/sh");
        Files.writeString(mod.resolve("mod.info"), "name=Knox Survivors\nid=KnoxSurvivors\n");
        Files.copy(mod.resolve("mod.info"), mod.resolve("42/mod.info"));
        Files.writeString(mod.resolve("42/knox-runtime.properties"),
            "runtime=iso-player-agent-v1\nlauncherCompatibility=1\nruntimeVersion=0.2.0\n");
        createAgent(jar, "0.2.0");
        Files.writeString(Path.of(jar + ".sha256"), sha256(jar) + "  " + jar.getFileName());

        InstallationValidator validator = new InstallationValidator();
        for (Platform platform : Platform.values()) {
            Path executable = platform == Platform.WINDOWS
                ? game.resolve("ProjectZomboid64.bat") : game.resolve("projectzomboid.sh");
            LauncherInstallation installation = new LauncherInstallation(
                root, game, workshop, mod, jar, executable, platform
            );
            validator.validate(installation);
            List<String> command = GameLauncher.command(installation);
            require(command.stream().anyMatch(value -> value.contains(executable.toString())),
                platform + " launch command lost game executable");
        }
        LauncherInstallation installation = new LauncherInstallation(
            root, game, workshop, mod, jar, game.resolve("ProjectZomboid64.bat"), Platform.WINDOWS
        );
        Path checksum = Path.of(jar + ".sha256");
        String goodChecksum = Files.readString(checksum);
        Files.writeString(checksum, "0".repeat(64));
        expectFailure(() -> validator.validate(installation), "checksum");
        Files.delete(checksum);
        expectFailure(() -> validator.validate(installation), "checksum is missing");
        Files.writeString(checksum, goodChecksum);
        createAgent(jar, "other-version");
        expectFailure(() -> validator.validate(installation), "versions do not match");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) { }
        expectFailure(() -> validator.validate(installation), "no launcher manifest");
        createAgent(jar, "0.2.0");
        Files.writeString(checksum, sha256(jar));
        Files.delete(mod.resolve("42/mod.info"));
        expectFailure(() -> validator.validate(installation), "mod.info is missing");
        Files.copy(mod.resolve("mod.info"), mod.resolve("42/mod.info"));
        Path marker = mod.resolve("42/knox-runtime.properties");
        String goodMarker = Files.readString(marker);
        Files.writeString(marker, goodMarker.replace("launcherCompatibility=1", "launcherCompatibility=2"));
        expectFailure(() -> validator.validate(installation), "different versions");
        Files.delete(marker);
        expectFailure(() -> validator.validate(installation), "older Knox Survivors release");
        Files.writeString(marker, goodMarker);
        validator.validate(installation);
    }

    private static void verifyPublishedPackage(Path game, Path contents) throws Exception {
        Path mod = contents.resolve("mods/KnoxSurvivors");
        List<Path> jars;
        try (var files = Files.walk(contents, 6)) {
            jars = files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().matches("knox-agent-.*\\.jar"))
                .toList();
        }
        require(jars.size() == 1, "published Contents must contain exactly one agent");
        Platform platform = Platform.current();
        Path executable = game.resolve(platform == Platform.WINDOWS ? "ProjectZomboid64.bat" : "projectzomboid.sh");
        new InstallationValidator().validate(new LauncherInstallation(
            game, game, contents, mod, jars.get(0), executable, platform
        ));
        System.out.println("actual Workshop Contents / launcher compatibility passed");
    }

    private static void verifyChildLaunch(Path root) throws Exception {
        // Exercise the real OS command/quoting path, without starting Project Zomboid.
        Path game = root.resolve("Game Folder With Spaces");
        Files.createDirectories(game);
        Platform platform = Platform.current();
        Path output = game.resolve("child-environment.txt");
        Path executable = game.resolve(platform == Platform.WINDOWS ? "ProjectZomboid64.bat" : "projectzomboid.sh");
        Files.writeString(executable, platform == Platform.WINDOWS
            ? "@echo off\r\nset JAVA_TOOL_OPTIONS > \"" + output + "\"\r\nexit /b 0\r\n"
            : "#!/bin/sh\nprintf '%s' \"$JAVA_TOOL_OPTIONS\" > '" + output + "'\n");
        Path agent = game.resolve("Mod Folder With Spaces/knox-agent-test.jar");
        String originalHome = System.getProperty("user.home");
        String originalOptions = System.getenv("JAVA_TOOL_OPTIONS");
        try {
            System.setProperty("user.home", root.toString());
            Process child = new GameLauncher().launch(new LauncherInstallation(
                root, game, root, root, agent, executable, platform
            ));
            boolean finished = child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) child.destroyForcibly();
            if (finished && child.exitValue() != 0) {
                Process diagnostic = new ProcessBuilder(GameLauncher.command(new LauncherInstallation(
                    root, game, root, root, agent, executable, platform
                ))).directory(game.toFile()).redirectErrorStream(true).start();
                throw new IllegalStateException("native child launch/quoting failed: "
                    + new String(diagnostic.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            }
            require(finished, "native child launch timed out");
            require(Files.readString(output).contains("-javaagent:\"" + agent.toAbsolutePath() + "\"=pz-game"),
                "child lost or split the agent path");
            require(java.util.Objects.equals(originalOptions, System.getenv("JAVA_TOOL_OPTIONS")),
                "launch changed parent environment");
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }

    private static void expectFailure(CheckedAction action, String message) throws Exception {
        try {
            action.run();
        } catch (LauncherException expected) {
            require(expected.getMessage().contains(message), "unexpected rejection: " + expected.getMessage());
            return;
        }
        throw new IllegalStateException("expected rejection: " + message);
    }

    private static void createAgent(Path path, String version) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Premain-Class", "com.knoxsurvivors.agent.KnoxAgent");
        attributes.putValue("Implementation-Version", version);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            // Manifest-only test agent is enough for launcher validation.
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        );
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted((first, second) -> second.compareTo(first)).forEach(path -> {
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
