package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class LauncherVerifier {
    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("knox-launcher-verifier-");
        try {
            verifyLibraryParsing(root);
            verifySplitLibraryDiscovery(root);
            verifyValidationAndCommands(root);
            verifyOptionalAgentComposition(root);
            verifyChildLaunch(root);
            if (arguments.length == 2) verifyPublishedPackage(Path.of(arguments[0]), Path.of(arguments[1]));
            if (arguments.length >= 3) verifyRealZombieBuddy(root, Path.of(arguments[2]));
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
        installZombieBuddy(game, platform);
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
            String childOptions = Files.readString(output);
            require(childOptions.contains("-javaagent:\"" + agent.toAbsolutePath() + "\"=pz-game"),
                "child lost or split the agent path");
            String expectedZombieBuddy = platform == Platform.WINDOWS
                ? "-agentlib:zbNative" : "-javaagent:\"";
            require(childOptions.contains(expectedZombieBuddy), "child lost optional ZombieBuddy agent");
            require(childOptions.indexOf(expectedZombieBuddy) < childOptions.indexOf("knox-agent-test.jar"),
                "ZombieBuddy was not initialized before Knox");
            require(java.util.Objects.equals(originalOptions, System.getenv("JAVA_TOOL_OPTIONS")),
                "launch changed parent environment");
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    private static void verifyOptionalAgentComposition(Path root) throws Exception {
        Path game = root.resolve("Agent Composition Game");
        Path knox = root.resolve("Knox Mod/knox-agent-test.jar");
        Files.createDirectories(game);
        Files.createDirectories(knox.getParent());
        Files.writeString(knox, "fixture");
        for (Platform platform : Platform.values()) {
            LauncherInstallation installation = new LauncherInstallation(
                root, game, root, root, knox, game.resolve("launcher"), platform
            );
            String absent = GameLauncher.toolOptions(installation, "-Dexisting=value");
            require(absent.startsWith("-Dexisting=value ") && absent.contains("knox-agent-test.jar")
                && !absent.toLowerCase().contains("zombiebuddy"),
                platform + " made absent ZombieBuddy a dependency or lost inherited options");
            installZombieBuddy(game, platform);
            String composed = GameLauncher.toolOptions(installation, "-Dexisting=value");
            String marker = platform == Platform.WINDOWS ? "-agentlib:zbNative" : "ZombieBuddy.jar";
            require(composed.startsWith("-Dexisting=value ") && composed.contains(marker),
                platform + " did not compose installed ZombieBuddy");
            require(composed.indexOf(marker) < composed.indexOf("knox-agent-test.jar"),
                platform + " agent order is wrong");
            String custom = platform == Platform.WINDOWS
                ? "-agentlib:zbNative=verbosity=2" : "-javaagent:\"ZombieBuddy.jar\"=verbosity=2";
            String preserved = GameLauncher.toolOptions(installation, custom);
            require(preserved.startsWith(custom + " ") && preserved.indexOf(marker) == preserved.lastIndexOf(marker),
                platform + " duplicated or replaced user ZombieBuddy options");
            if (platform == Platform.WINDOWS) {
                Files.writeString(game.resolve("ProjectZomboid64.json"),
                    "{\"vmArgs\":[\"-Xmx3072m\",\"-agentlib:zbNative=verbosity=2,policy=deny-new\"]}");
                String jsonOptions = GameLauncher.toolOptions(installation, "");
                require(jsonOptions.startsWith("-agentlib:zbNative=verbosity=2,policy=deny-new "),
                    "ZombieBuddy options from the normal JSON launch were not carried into Knox launch");
                Files.delete(game.resolve("ProjectZomboid64.json"));
            }
            Path configuredLauncher = game.resolve(platform == Platform.WINDOWS ? "ProjectZomboid64.bat" : "projectzomboid.sh");
            Files.writeString(configuredLauncher, platform == Platform.WINDOWS
                ? "SET _JAVA_OPTIONS=-agentlib:zbNative=verbosity=2,policy=deny-new"
                : "java -javaagent:ZombieBuddy.jar");
            LauncherInstallation configured = new LauncherInstallation(
                root, game, root, root, knox, configuredLauncher, platform
            );
            String ordered = GameLauncher.toolOptions(configured, "");
            require(ordered.contains(marker) && ordered.contains("knox-agent-test.jar")
                && ordered.indexOf(marker) < ordered.indexOf("knox-agent-test.jar"),
                platform + " did not place the configured ZombieBuddy agent before Knox");
            if (platform == Platform.WINDOWS) {
                require(ordered.startsWith("-agentlib:zbNative=verbosity=2,policy=deny-new "),
                    "Windows BAT ZombieBuddy options were not preserved");
            }
            Files.delete(configuredLauncher);
            removeZombieBuddy(game, platform);
        }
        expectFailure(() -> GameLauncher.toolOptions(new LauncherInstallation(
            root, game, root, root, knox, game.resolve("launcher"), Platform.WINDOWS
        ), "-javaagent:\"C:\\A Folder\\knox-agent-old.jar\"=pz-game"), "already present");
        Files.writeString(game.resolve("zbNative.dll"), "partial");
        String partial = GameLauncher.toolOptions(new LauncherInstallation(
            root, game, root, root, knox, game.resolve("launcher"), Platform.WINDOWS
        ), "");
        require(!partial.contains("zbNative"), "partial external install was activated");
    }

    private static void verifyRealZombieBuddy(Path root, Path workshopLibs) throws Exception {
        Path sourceJar = workshopLibs.resolve("ZombieBuddy.jar");
        Path sourceDll = workshopLibs.resolve("zbNative.dll");
        require(Files.isRegularFile(sourceJar) && Files.isRegularFile(sourceDll),
            "installed Workshop reference is missing ZombieBuddy agents");
        Path game = root.resolve("Real ZombieBuddy Game");
        Files.createDirectories(game);
        Files.copy(sourceJar, game.resolve("ZombieBuddy.jar"));
        Files.copy(sourceDll, game.resolve("zbNative.dll"));
        Path knox = root.resolve("Real ZombieBuddy Knox/knox-agent-test.jar");
        Files.createDirectories(knox.getParent()); Files.writeString(knox, "fixture");
        LauncherInstallation installation = new LauncherInstallation(
            root, game, root, root, knox, game.resolve("ProjectZomboid64.bat"), Platform.WINDOWS
        );
        String options = GameLauncher.toolOptions(installation, "-Dpreserved=1");
        require(options.startsWith("-Dpreserved=1 -agentlib:zbNative ")
            && options.endsWith("knox-agent-test.jar\"=pz-game"),
            "real ZombieBuddy manifest/native pair was not composed before Knox");
        try (JarFile actual = new JarFile(sourceJar.toFile())) {
            System.out.println("actual ZombieBuddy compatibility passed version="
                + actual.getManifest().getMainAttributes().getValue("Implementation-Version"));
        }
    }

    private static void installZombieBuddy(Path game, Platform platform) throws Exception {
        Path jar;
        if (platform == Platform.WINDOWS) {
            jar = game.resolve("ZombieBuddy.jar");
            Files.writeString(game.resolve("zbNative.dll"), "native-fixture");
        } else if (platform == Platform.MAC) {
            jar = game.resolve("Project Zomboid.app/Contents/Java/ZombieBuddy.jar");
        } else {
            jar = game.resolve("projectzomboid/ZombieBuddy.jar");
        }
        Files.createDirectories(jar.getParent());
        createJar(jar, "me.zed_0xff.zombie_buddy.Agent", "test");
    }

    private static void removeZombieBuddy(Path game, Platform platform) throws IOException {
        Files.deleteIfExists(game.resolve("zbNative.dll"));
        Files.deleteIfExists(game.resolve("ZombieBuddy.jar"));
        Files.deleteIfExists(game.resolve("projectzomboid/ZombieBuddy.jar"));
        Files.deleteIfExists(game.resolve("Project Zomboid.app/Contents/Java/ZombieBuddy.jar"));
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
        createJar(path, "com.knoxsurvivors.agent.KnoxAgent", version);
    }

    private static void createJar(Path path, String premain, String version) throws IOException {
        Files.createDirectories(path.getParent());
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Premain-Class", premain);
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
