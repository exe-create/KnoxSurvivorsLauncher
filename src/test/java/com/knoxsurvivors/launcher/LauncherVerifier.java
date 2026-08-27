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
        Path mod = workshop.resolve("Contents/mods/KnoxSurvivors");
        Path jar = workshop.resolve("java/build/libs/knox-agent-test.jar");
        Files.createDirectories(game);
        Files.createDirectories(mod.resolve("42"));
        Files.createDirectories(jar.getParent());
        Files.writeString(game.resolve("projectzomboid.jar"), "test");
        Files.writeString(game.resolve("ProjectZomboid64.bat"), "@echo off");
        Files.writeString(mod.resolve("mod.info"), "name=Knox Survivors\nid=KnoxSurvivors\n");
        Files.writeString(mod.resolve("42/knox-runtime.properties"), "runtime=iso-player-agent-v1\n");
        createAgent(jar);
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
        Files.writeString(mod.resolve("42/knox-runtime.properties"), "runtime=iso-player-agent-v1\n");
        createAgent(jar);
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
    }

    private static void createAgent(Path path) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Premain-Class", "com.knoxsurvivors.agent.KnoxAgent");
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
