package com.knoxsurvivors.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class SteamLocator {
    static final String STEAM_APP_ID = "108600";
    static final String WORKSHOP_ITEM_ID = "3749727604";
    private static final Pattern VDF_PATH = Pattern.compile("\\\"path\\\"\\s+\\\"([^\\\"]+)\\\"");
    private static final Pattern INSTALL_DIR = Pattern.compile("\\\"installdir\\\"\\s+\\\"([^\\\"]+)\\\"");

    LauncherInstallation locate() throws LauncherException {
        Platform platform = Platform.current();
        return locateFromRoots(steamCandidates(platform), platform);
    }

    LauncherInstallation locateFromRoots(List<Path> steamRoots, Platform platform)
        throws LauncherException {
        for (Path steam : steamRoots) {
            if (!Files.isDirectory(steam)) continue;
            List<Path> libraries = libraries(steam);
            Path game = null;
            Path workshop = null;
            for (Path library : libraries) {
                if (game == null) game = gameDirectory(library);
                Path candidate = library.resolve("steamapps/workshop/content")
                    .resolve(STEAM_APP_ID).resolve(WORKSHOP_ITEM_ID);
                if (workshop == null && Files.isDirectory(candidate)) workshop = candidate;
            }
            if (game != null && workshop != null) {
                Path mod = modDirectory(workshop);
                Path agent = agentJar(workshop);
                Path gameLauncher = gameLauncher(game, platform);
                return new LauncherInstallation(
                    steam, game, workshop, mod, agent, gameLauncher, platform
                );
            }
        }
        throw new LauncherException(
            "Project Zomboid and the subscribed Knox Survivors Workshop item could not both be found. "
                + "Make sure Steam has finished downloading Workshop item " + WORKSHOP_ITEM_ID + "."
        );
    }

    static List<Path> libraries(Path steamDirectory) throws LauncherException {
        Set<Path> results = new LinkedHashSet<>();
        results.add(normalize(steamDirectory));
        Path file = steamDirectory.resolve("steamapps/libraryfolders.vdf");
        if (!Files.isRegularFile(file)) return List.copyOf(results);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = VDF_PATH.matcher(text);
            while (matcher.find()) {
                String raw = matcher.group(1).replace("\\\\", "\\");
                if (!raw.isBlank()) results.add(normalize(Paths.get(raw)));
            }
        } catch (IOException exception) {
            throw new LauncherException("Steam's library list could not be read.", exception);
        }
        return List.copyOf(results);
    }

    private static List<Path> steamCandidates(Platform platform) {
        Set<Path> candidates = new LinkedHashSet<>();
        add(candidates, System.getenv("KNOX_STEAM_ROOT"));
        add(candidates, System.getenv("STEAM_PATH"));
        Path home = Paths.get(System.getProperty("user.home", "."));
        if (platform == Platform.WINDOWS) {
            add(candidates, windowsRegistrySteamPath());
            add(candidates, System.getenv("ProgramFiles(x86)"), "Steam");
            add(candidates, System.getenv("ProgramFiles"), "Steam");
            add(candidates, "C:\\Program Files (x86)\\Steam");
            add(candidates, "C:\\Program Files\\Steam");
        } else if (platform == Platform.MAC) {
            candidates.add(home.resolve("Library/Application Support/Steam"));
        } else {
            candidates.add(home.resolve(".local/share/Steam"));
            candidates.add(home.resolve(".steam/steam"));
            candidates.add(home.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam"));
        }
        return new ArrayList<>(candidates);
    }

    private static String windowsRegistrySteamPath() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        try {
            Process process = new ProcessBuilder(
                "reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath"
            ).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int marker = line.indexOf("REG_SZ");
                    if (marker >= 0) return line.substring(marker + 6).trim();
                }
            }
        } catch (IOException ignored) {
            // Standard install candidates remain available.
        }
        return null;
    }

    private static Path gameDirectory(Path library) {
        Path manifest = library.resolve("steamapps/appmanifest_" + STEAM_APP_ID + ".acf");
        if (Files.isRegularFile(manifest)) {
            try {
                Matcher matcher = INSTALL_DIR.matcher(Files.readString(manifest, StandardCharsets.UTF_8));
                if (matcher.find()) {
                    Path found = library.resolve("steamapps/common").resolve(matcher.group(1));
                    if (Files.isDirectory(found)) return found;
                }
            } catch (IOException ignored) {
                // Fall through to the stable default directory name.
            }
        }
        Path standard = library.resolve("steamapps/common/ProjectZomboid");
        return Files.isDirectory(standard) ? standard : null;
    }

    private static Path modDirectory(Path workshop) throws LauncherException {
        for (Path candidate : List.of(
            workshop.resolve("Contents/mods/KnoxSurvivors"),
            workshop.resolve("mods/KnoxSurvivors")
        )) {
            if (Files.isRegularFile(candidate.resolve("mod.info"))) return candidate;
        }
        throw new LauncherException(
            "The Workshop download does not contain Mod ID KnoxSurvivors. "
                + "Verify the Workshop item through Steam."
        );
    }

    private static Path agentJar(Path workshop) throws LauncherException {
        try (Stream<Path> paths = Files.walk(workshop, 6)) {
            List<Path> jars = paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith("knox-agent-"))
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .toList();
            if (jars.size() == 1) return jars.get(0);
            if (jars.isEmpty()) {
                throw new LauncherException(
                    "This Workshop download is the older Knox Survivors build and does not include "
                        + "the IsoPlayer Java runtime yet. Wait for the rebuild update, then let Steam download it."
                );
            }
            throw new LauncherException(
                "Multiple Knox Java runtimes were found. Verify the Workshop item through Steam."
            );
        } catch (IOException exception) {
            throw new LauncherException("The Workshop files could not be inspected.", exception);
        }
    }

    private static Path gameLauncher(Path game, Platform platform) throws LauncherException {
        List<Path> candidates = new ArrayList<>();
        if (platform == Platform.WINDOWS) {
            candidates.add(game.resolve("ProjectZomboid64.bat"));
        } else {
            candidates.add(game.resolve("projectzomboid.sh"));
            candidates.add(game.resolve("ProjectZomboid64"));
        }
        if (platform == Platform.MAC) {
            candidates.add(game.resolve("Project Zomboid.app/Contents/MacOS/Project Zomboid"));
            candidates.add(game.resolve("Project Zomboid.app/Contents/MacOS/JavaAppLauncher"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        if (platform == Platform.MAC) {
            Path macOs = game.resolve("Project Zomboid.app/Contents/MacOS");
            if (Files.isDirectory(macOs)) {
                try (Stream<Path> files = Files.list(macOs)) {
                    Path executable = files.filter(Files::isRegularFile).findFirst().orElse(null);
                    if (executable != null) return executable;
                } catch (IOException ignored) {
                }
            }
        }
        throw new LauncherException("Project Zomboid's normal game launcher was not found.");
    }

    private static void add(Set<Path> paths, String value) {
        if (value != null && !value.isBlank()) paths.add(normalize(Paths.get(value)));
    }

    private static void add(Set<Path> paths, String parent, String child) {
        if (parent != null && !parent.isBlank()) paths.add(normalize(Paths.get(parent, child)));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
