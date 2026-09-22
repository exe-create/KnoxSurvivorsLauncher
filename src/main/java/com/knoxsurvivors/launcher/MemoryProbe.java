package com.knoxsurvivors.launcher;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what the game will actually use for heap so the launcher can show it
 * instead of asking players to find it: the native Windows launcher reads
 * ProjectZomboid64.json, script launchers carry their own -Xmx flag.
 */
final class MemoryProbe {
    private static final Pattern HEAP = Pattern.compile("-Xmx([0-9]+[mMgG])");

    record Heap(String value, String source) {}

    private MemoryProbe() {}

    static Heap gameHeap(LauncherInstallation installation) {
        try {
            Path gameDir = installation.gameDirectory();
            String launcherName = installation.gameLauncher().getFileName().toString();
            if (launcherName.equalsIgnoreCase("ProjectZomboid64.exe")) {
                String heap = firstHeap(readText(gameDir.resolve("ProjectZomboid64.json")));
                if (!heap.isEmpty()) return new Heap(heap, "ProjectZomboid64.json");
                return new Heap("", "ProjectZomboid64.json");
            }
            String heap = firstHeap(readText(installation.gameLauncher()));
            if (!heap.isEmpty()) return new Heap(heap, launcherName);
        } catch (Exception ignored) {
        }
        return new Heap("", "");
    }

    static long totalPhysicalMemoryBytes() {
        try {
            OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                return extended.getTotalMemorySize();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    static long parseHeapBytes(String heap) {
        if (heap == null) return -1;
        Matcher matcher = Pattern.compile("^([0-9]+)([mMgG])$").matcher(heap.trim());
        if (!matcher.matches()) return -1;
        long value = Long.parseLong(matcher.group(1));
        return matcher.group(2).equalsIgnoreCase("g") ? value * 1024L * 1024L * 1024L : value * 1024L * 1024L;
    }

    static String friendlyBytes(long bytes) {
        if (bytes < 0) return "unknown";
        if (bytes >= 1024L * 1024L * 1024L && bytes % (1024L * 1024L * 1024L) == 0) {
            return (bytes / (1024L * 1024L * 1024L)) + " GB";
        }
        if (bytes >= 1024L * 1024L) {
            long mb = bytes / (1024L * 1024L);
            if (mb >= 1024 && mb % 1024 == 0) return (mb / 1024) + " GB";
            return mb + " MB";
        }
        return bytes + " bytes";
    }

    private static String firstHeap(String text) {
        if (text == null) return "";
        Matcher matcher = HEAP.matcher(text);
        return matcher.find() ? matcher.group(1).toLowerCase(java.util.Locale.ROOT) : "";
    }

    private static String readText(Path file) {
        try {
            if (Files.isRegularFile(file) && Files.size(file) < 4L * 1024L * 1024L) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
