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
    private static final String EXPECTED_RUNTIME = "iso-player-agent-v1";
    private static final String LAUNCHER_COMPATIBILITY = "1";

    void validate(LauncherInstallation installation) throws LauncherException {
        require(Files.isDirectory(installation.gameDirectory()),
            "Project Zomboid's installation folder is missing.");
        require(Files.isRegularFile(installation.gameLauncher()),
            "Project Zomboid's normal launcher is missing.");
        require(Files.isRegularFile(installation.gameDirectory().resolve("projectzomboid.jar")),
            "Project Zomboid looks incomplete. Verify the game through Steam.");
        validateModInfo(installation.modDirectory().resolve("mod.info"));
        Path buildInfo = installation.modDirectory().resolve("42/knox-runtime.properties");
        require(Files.isRegularFile(buildInfo),
            "The subscribed Workshop item is still the older Knox Survivors release. "
                + "The IsoPlayer rebuild has not been published there yet.");
        try (InputStream input = Files.newInputStream(buildInfo)) {
            Properties marker = new Properties();
            marker.load(input);
            require(EXPECTED_RUNTIME.equals(marker.getProperty("runtime")),
                "The Workshop mod and launcher runtime are not compatible.");
            require(LAUNCHER_COMPATIBILITY.equals(marker.getProperty("launcherCompatibility")),
                "This launcher and the Workshop build are different versions. Download the latest launcher release.");
            String runtimeVersion = marker.getProperty("runtimeVersion", "").trim();
            require(!runtimeVersion.isEmpty(), "The Workshop runtime version is missing.");
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

    private static void validateAgent(Path jar, String runtimeVersion) throws LauncherException {
        require(jar != null && Files.isRegularFile(jar), "The Knox Java runtime is missing.");
        try (JarFile archive = new JarFile(jar.toFile())) {
            Attributes attributes = archive.getManifest().getMainAttributes();
            require(EXPECTED_PREMAIN.equals(attributes.getValue("Premain-Class")),
                "The Knox Java runtime has an invalid launcher manifest.");
            require(runtimeVersion.equals(attributes.getValue("Implementation-Version")),
                "The Workshop mod and Knox Java runtime versions do not match. Let Steam finish updating and try again.");
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
                "The Knox Java runtime did not pass its checksum. Verify the Workshop item through Steam.");
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new LauncherException("The Knox Java runtime checksum could not be verified.", exception);
        }
    }

    private static void require(boolean condition, String message) throws LauncherException {
        if (!condition) throw new LauncherException(message);
    }
}
