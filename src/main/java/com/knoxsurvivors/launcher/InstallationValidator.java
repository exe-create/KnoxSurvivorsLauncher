package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

final class InstallationValidator {
    private static final String EXPECTED_PREMAIN = "com.knoxsurvivors.agent.KnoxAgent";
    private static final String EXPECTED_RUNTIME = "zombie-buddy-java-mod-v1";
    private static final String LEGACY_AGENT_COMPATIBILITY = "true";

    void validate(LauncherInstallation installation) throws LauncherException {
        require(Files.isDirectory(installation.gameDirectory()),
            "Project Zomboid's installation folder is missing.");
        require(Files.isRegularFile(installation.gameLauncher()),
            "Project Zomboid's normal launcher is missing.");
        require(Files.isRegularFile(installation.gameDirectory().resolve("projectzomboid.jar")),
            "Project Zomboid looks incomplete: projectzomboid.jar is missing. Verify the game through Steam.");
        if (installation.platform() == Platform.WINDOWS
                && installation.gameLauncher().getFileName().toString().equalsIgnoreCase("ProjectZomboid64.bat")) {
            require(Files.isRegularFile(installation.gameDirectory().resolve("jre64/bin/java.exe")),
                "Project Zomboid's bundled Java runtime is missing (jre64\\bin\\java.exe). Verify the game through Steam.");
        }
        if (GameLauncher.usesNativeWindowsLauncher(installation)) {
            Path game = installation.gameDirectory();
            require(Files.isRegularFile(game.resolve("ProjectZomboid64.json")),
                "Project Zomboid's JVM settings are missing (ProjectZomboid64.json). Verify the game through Steam.");
            for (String runtimeFile : new String[] {
                    "jre64/bin/java.dll", "jre64/bin/jli.dll",
                    "jre64/bin/instrument.dll", "jre64/bin/server/jvm.dll" }) {
                require(Files.isRegularFile(game.resolve(runtimeFile)),
                    "Project Zomboid's bundled Java runtime is incomplete (" + runtimeFile
                        + "). Verify the game through Steam.");
            }
        }
        validateModInfo(installation.modDirectory().resolve("mod.info"));
        validateModInfo(installation.modDirectory().resolve("42/mod.info"));
        Path buildInfo = installation.modDirectory().resolve("42/knox-runtime.properties");
        require(Files.isRegularFile(buildInfo),
            "The subscribed Workshop package is missing the current Knox runtime. "
                + "Let Steam finish updating or verify the Workshop files.");
        try (InputStream input = Files.newInputStream(buildInfo)) {
            Properties marker = new Properties();
            marker.load(input);
            String runtimeType = marker.getProperty("runtime", "").trim();
            String compatibility = marker.getProperty("legacyAgentCompatible", "").trim();
            require(EXPECTED_RUNTIME.equals(runtimeType),
                "The Workshop runtime type is '" + runtimeType + "' but this launcher expects '"
                    + EXPECTED_RUNTIME + "'. Update the Workshop mod and launcher together.");
            require(LEGACY_AGENT_COMPATIBILITY.equalsIgnoreCase(compatibility),
                "The Workshop runtime is not compatible with the Knox Launcher. "
                    + "Update the Workshop mod and launcher together.");
            String runtimeVersion = marker.getProperty("runtimeVersion", "").trim();
            require(!runtimeVersion.isEmpty(), "The Workshop runtime version is missing from knox-runtime.properties.");
            String workshopVersion = modInfoVersion(installation.modDirectory().resolve("mod.info"));
            if (!workshopVersion.isEmpty()) {
                require(workshopVersion.equals(runtimeVersion),
                    "The Workshop mod reports version " + workshopVersion + " but the runtime reports "
                        + runtimeVersion + ". Let Steam finish updating the mod.");
            }
            validateAgent(installation.agentJar(), runtimeVersion);
        } catch (IOException exception) {
            throw new LauncherException("The Knox runtime marker could not be read.", exception);
        }
    }

    private static void validateModInfo(Path file) throws LauncherException {
        require(Files.isRegularFile(file), "Knox Survivors mod.info is missing.");
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            require(text.lines().map(String::trim).anyMatch("id=KnoxSurvivors"::equals),
                "The Workshop folder does not contain Mod ID KnoxSurvivors.");
        } catch (IOException exception) {
            throw new LauncherException("Knox Survivors mod.info could not be read.", exception);
        }
    }


    private static String modInfoVersion(Path file) {
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.regionMatches(true, 0, "version=", 0, 8)) {
                    return trimmed.substring(8).trim();
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }
    private static void validateAgent(Path jar, String runtimeVersion) throws LauncherException {
        require(jar != null && Files.isRegularFile(jar), "The Knox Java runtime is missing.");
        try (JarFile archive = new JarFile(jar.toFile())) {
            require(archive.getManifest() != null,
                "The Knox Java runtime has no launcher manifest. Verify the Workshop item through Steam.");
            Attributes attributes = archive.getManifest().getMainAttributes();
            require(EXPECTED_PREMAIN.equals(attributes.getValue("Premain-Class")),
                "The Knox Java runtime has an invalid launcher manifest.");
            String actualVersion = attributes.getValue("Implementation-Version");
            require(runtimeVersion.equals(actualVersion),
                "The Workshop expects runtime " + runtimeVersion + " but knox-agent.jar reports "
                    + String.valueOf(actualVersion) + ". Let Steam finish updating and try again.");
        } catch (IOException exception) {
            throw new LauncherException("The Knox Java runtime could not be opened.", exception);
        }
        Path checksum = Path.of(jar + ".sha256");
        require(Files.isRegularFile(checksum),
            "The Knox Java runtime checksum is missing. Verify the Workshop item through Steam.");
        try {
            String expected = Files.readString(checksum, StandardCharsets.US_ASCII)
                .trim().split("\\s+", 2)[0].toLowerCase();
            String actual;
            try (InputStream input = Files.newInputStream(jar)) {
                actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            }
            require(expected.equals(actual),
                "The Knox Java runtime checksum does not match the Workshop checksum. "
                    + "Verify the Workshop item through Steam.");
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new LauncherException("The Knox Java runtime checksum could not be verified.", exception);
        }
    }

    private static void require(boolean condition, String message) throws LauncherException {
        if (!condition) throw new LauncherException(message);
    }
}
