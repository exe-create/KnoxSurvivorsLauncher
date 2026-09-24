package com.knoxsurvivors.launcher;

import java.io.IOException;

final class UpdaterVerifier {
    private static String release(String version, boolean draft) {
        String root = "https://github.com/exe-create/KnoxSurvivorsLauncher/releases/download/" + version + "/";
        // Real GitHub responses put html_url before tag_name; assets contain
        // nested uploader objects and their own name-like fields.
        return "{\"html_url\":\"https://github.com/example\",\"body\":\"notes [braces] {\\\"quoted\\\"}\","
            + "\"assets\":[{\"uploader\":{\"name\":\"user\"},\"browser_download_url\":\"" + root
            + "KnoxSurvivorsLauncher.jar\",\"name\":\"KnoxSurvivorsLauncher.jar\"},"
            + "{\"browser_download_url\":\"" + root + "SHA256SUMS.txt\",\"name\":\"SHA256SUMS.txt\"}],"
            + "\"prerelease\":true,\"draft\":" + draft + ",\"tag_name\":\"" + version + "\"}";
    }
    static void verify(java.nio.file.Path root) throws Exception {
        check(LauncherUpdater.javaExecutable(java.nio.file.Path.of("C:/java"), true)
            .endsWith(java.nio.file.Path.of("bin", "javaw.exe")), "Windows restart stays windowless");
        check(LauncherUpdater.javaExecutable(java.nio.file.Path.of("/java"), false)
            .endsWith(java.nio.file.Path.of("bin", "java")), "Unix restart uses java");
        check(LauncherUpdater.compare("v0.2.3-preview.10", "0.2.3-preview.2") > 0, "numeric prerelease ordering");
        check(LauncherUpdater.compare("0.2.3", "0.2.3-preview.10") > 0, "stable follows preview");
        check(LauncherUpdater.compare("99999999999999999999.0.0", "1.0.0") > 0, "large version does not overflow");
        var update = LauncherUpdater.select("[" + release("v0.3.2-rc1", false) + ","
            + release("v0.3.2", false) + "," + release("v9.0.0", true) + "]");
        check(update != null && update.version().equals("v0.3.2"), "select newest complete non-draft release");
        check(LauncherUpdater.select("[" + release("v0.3.1-rc9", false) + "]") == null, "no stale update");
        check(LauncherUpdater.select("[" + release("v0.3.2", false).replace(
            "exe-create/KnoxSurvivorsLauncher/releases/download", "other/repo/releases/download") + "]") == null,
            "reject assets in another repository");
        check(LauncherUpdater.select("[" + release("v0.3.2", false).replace(
            "SHA256SUMS.txt", "unrelated.txt") + "]") == null, "incomplete release is ignored");
        String hash = "a".repeat(64);
        check(LauncherUpdater.expectedChecksum(hash + "  KnoxSurvivorsLauncher.jar\r\n").equals(hash), "exact checksum");
        reject(() -> LauncherUpdater.expectedChecksum(hash + "  KnoxSurvivorsLauncher.jar.zip"));
        reject(() -> LauncherUpdater.expectedChecksum(hash + "  KnoxSurvivorsLauncher.jar\n"
            + hash + "  KnoxSurvivorsLauncher.jar\n"));
        reject(() -> ReleaseJson.parse("{\"draft\":true,\"draft\":false}"));
        reject(() -> ReleaseJson.parse("[{}] trailing"));
        reject(() -> ReleaseJson.parse("[1,]"));
        check(ReleaseJson.parse("\"\\u004b\\n\"").equals("K\n"), "JSON escapes");
        var home = root.resolve("updater-cache-test");
        var good = cache(home, "v0.3.2", "0.3.2");
        cache(home, "v0.3.3", "wrong-manifest-version");
        check(new LauncherUpdater(home).cachedUpdate().equals(good),
            "corrupt newer cache falls back to verified cache and accepts v-prefixed release tags");
        java.nio.file.Files.writeString(good, "tampered");
        check(new LauncherUpdater(home).cachedUpdate() == null, "no cache executes after integrity failure");
        System.out.println("updater metadata, version and checksum verification passed");
    }
    private static java.nio.file.Path cache(java.nio.file.Path home, String tag, String manifestVersion) throws Exception {
        var directory = home.resolve("launcher-updates").resolve(tag);
        java.nio.file.Files.createDirectories(directory);
        var jar = directory.resolve("KnoxSurvivorsLauncher.jar");
        var manifest = new java.util.jar.Manifest();
        var values = manifest.getMainAttributes();
        values.putValue("Manifest-Version", "1.0");
        values.putValue("Main-Class", "com.knoxsurvivors.launcher.Main");
        values.putValue("Implementation-Version", manifestVersion);
        values.putValue("Knox-Update-Protocol", "1");
        try (var output = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar), manifest)) { }
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(java.nio.file.Files.readAllBytes(jar)));
        java.nio.file.Files.writeString(directory.resolve("active.properties"),
            "version=" + tag + "\njar=KnoxSurvivorsLauncher.jar\nsha256=" + hash + "\n");
        return jar;
    }
    private interface Checked { void run() throws Exception; }
    private static void reject(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Malformed metadata was accepted");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
