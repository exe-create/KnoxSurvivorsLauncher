package com.knoxsurvivors.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class LaunchOptionsVerifier {
    static void verify(Path root) throws Exception {
        Path game = Files.createDirectories(root.resolve("Custom Options Game & (Test)"));
        Path batch = game.resolve("ProjectZomboid64.bat");
        LauncherInstallation windows = new LauncherInstallation(
            root, game, root, root, game.resolve("knox-agent-test.jar"), batch, Platform.WINDOWS
        );
        for (String option : List.of("-x=a&echo", "-x=a|echo", "-x=<file", "-x=>file",
                "-x=a^b", "-x=%PATH%", "-x=!PATH!", "'-x=a\"b'", "-x=a\nb", "-x=a\rb", "-x=a\u0000b")) {
            try {
                GameLauncher.command(windows, false, option);
                throw new IllegalStateException("Unsafe Windows option was accepted: " + option);
            } catch (LauncherException expected) {
                require(expected.getMessage().contains("Windows launch options"), "unclear option error");
            }
        }
        require(GameLauncher.parseLaunchOptions("-cachedir=\"D:\\Zomboid Profile\\\"")
            .equals(List.of("-cachedir=D:\\Zomboid Profile\\")), "trailing backslash was not preserved");
        require(GameLauncher.parseLaunchOptions("-cachedir='D:\\Zomboid Profile\\' -novoip")
            .equals(List.of("-cachedir=D:\\Zomboid Profile\\", "-novoip")), "single-quoted path was not preserved");
        try {
            GameLauncher.parseLaunchOptions("-cachedir=\"unfinished");
            throw new IllegalStateException("Unmatched quote was accepted");
        } catch (LauncherException expected) {
            require(expected.getMessage().contains("unmatched quote"), "unclear quote error");
        }
        for (String directory : List.of("Game %PATH%", "Game !PATH!")) {
            LauncherInstallation unsafePath = new LauncherInstallation(
                root, game, root, root, windows.agentJar(), root.resolve(directory).resolve("game.bat"), Platform.WINDOWS
            );
            try {
                GameLauncher.command(unsafePath);
                throw new IllegalStateException("Expandable batch path was accepted");
            } catch (LauncherException expected) {
                require(expected.getMessage().contains("launcher path"), "unclear batch path error");
            }
        }
        LauncherInstallation linux = new LauncherInstallation(
            root, game, root, root, windows.agentJar(), game.resolve("projectzomboid.sh"), Platform.LINUX
        );
        require(GameLauncher.command(linux, false, "'-x=a&b' '-y=%PATH%' -novoip")
            .contains("-x=a&b"), "Windows restrictions leaked to direct Unix arguments");

        if (Platform.current() != Platform.WINDOWS) return;
        // Mirror the game's batch forwarding into a real Java argument recorder.
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin/java.exe").toString();
        String classpath = System.getProperty("java.class.path");
        Files.writeString(batch, "@echo off\r\n\"" + javaExecutable + "\" -cp \"" + classpath
            + "\" com.knoxsurvivors.launcher.LaunchOptionsVerifier %1 %2\r\n");
        for (String custom : List.of("", "-cachedir=D:\\Zomboid", "-cachedir=\"D:\\Zomboid Profile\"",
                "-cachedir=\"D:\\Zomboid Profile\\\"")) {
            boolean debug = !custom.isEmpty();
            ProcessBuilder builder = new ProcessBuilder(GameLauncher.command(windows, debug, custom))
                .directory(game.toFile()).redirectErrorStream(true);
            builder.environment().remove("JAVA_TOOL_OPTIONS");
            builder.environment().remove("_JAVA_OPTIONS");
            builder.environment().remove("JDK_JAVA_OPTIONS");
            Process child = builder.start();
            boolean finished = child.waitFor(10, TimeUnit.SECONDS);
            if (!finished) child.destroyForcibly();
            require(finished, "custom argument child timed out");
            String output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            require(child.exitValue() == 0, "custom argument child failed: " + output);
            List<String> expected = debug ? List.of("-debug", GameLauncher.parseLaunchOptions(custom).get(0)) : List.of();
            require(Files.readAllLines(game.resolve("arguments.txt")).equals(expected),
                "batch forwarding changed spaced/trailing-backslash custom argument");
        }
        System.out.println("launch option security and native argument verification passed");
    }

    public static void main(String[] args) throws Exception {
        Files.write(Path.of("arguments.txt"), List.of(args));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
