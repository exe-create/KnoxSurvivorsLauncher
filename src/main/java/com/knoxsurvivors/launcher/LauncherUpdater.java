package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarFile;

/** Small, fail-closed updater. It never replaces the running JAR in place. */
final class LauncherUpdater {
    static final String CURRENT_VERSION = "0.2.3-preview.2";
    private static final String API = "https://api.github.com/repos/exe-create/KnoxSurvivorsLauncher/releases";
    private static final Pattern SHA = Pattern.compile("(?im)^([0-9a-f]{64})[ \\t]+\\*?KnoxSurvivorsLauncher\\.jar[ \\t]*$");
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$");
    private final HttpClient client;
    private final Path home;

    record Update(String version, URI jar, URI sums, String page) {}

    LauncherUpdater() { this(Path.of(System.getProperty("user.home", "."), "KnoxSurvivors")); }
    LauncherUpdater(Path home) { this.home = home; this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build(); }

    Update check() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API)).header("Accept", "application/vnd.github+json").header("User-Agent", "KnoxSurvivorsLauncher/" + CURRENT_VERSION).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("GitHub release check returned HTTP " + response.statusCode());
        return select(response.body());
    }

    static Update select(String json) throws IOException {
        Object parsed = ReleaseJson.parse(json);
        if (!(parsed instanceof java.util.List<?> releases)) throw new IOException("Expected release list.");
        Update best = null;
        for (Object entry : releases) {
            if (!(entry instanceof java.util.Map<?, ?> release)
                    || !Boolean.FALSE.equals(release.get("draft"))
                    || !(release.get("tag_name") instanceof String tag)
                    || compare(tag, CURRENT_VERSION) <= 0
                    || !(release.get("assets") instanceof java.util.List<?> assets)) continue;
            URI jar = null, sums = null;
            for (Object item : assets) {
                if (!(item instanceof java.util.Map<?, ?> asset)
                        || !(asset.get("name") instanceof String name)
                        || !(asset.get("browser_download_url") instanceof String url)) continue;
                URI uri;
                try { uri = URI.create(url); } catch (IllegalArgumentException ignored) { continue; }
                String expected = "/exe-create/KnoxSurvivorsLauncher/releases/download/" + tag + "/" + name;
                if (!isGithubAsset(uri) || !"github.com".equalsIgnoreCase(uri.getHost())
                        || !expected.equals(uri.getPath()) || uri.getQuery() != null
                        || uri.getFragment() != null || uri.getUserInfo() != null) continue;
                if (name.equals("KnoxSurvivorsLauncher.jar")) jar = uri;
                if (name.equals("SHA256SUMS.txt")) sums = uri;
            }
            if (jar != null && sums != null && (best == null || compare(tag, best.version()) > 0)) {
                best = new Update(tag, jar, sums,
                    "https://github.com/exe-create/KnoxSurvivorsLauncher/releases/tag/" + tag);
            }
        }
        return best;
    }

    Path install(Update update) throws IOException, InterruptedException {
        if (update == null || compare(update.version(), CURRENT_VERSION) <= 0) throw new IOException("Refusing stale launcher update.");
        Files.createDirectories(home.resolve("launcher-updates"));
        Path lockPath = home.resolve("launcher-updates/update.lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = lockChannel.tryLock()) {
            if (lock == null) throw new IOException("Another launcher update is already running.");
            Path directory = home.resolve("launcher-updates").resolve(safeVersion(update.version()));
            Files.createDirectories(directory);
            Path jar = download(update.jar(), directory.resolve("candidate.jar"));
            String sums = downloadText(update.sums());
            String expected = expectedChecksum(sums);
            if (!expected.equalsIgnoreCase(sha256(jar))) throw new IOException("Launcher update checksum did not match.");
            verifyJar(jar, update.version());
            Path installed = directory.resolve("KnoxSurvivorsLauncher.jar");
            move(jar, installed);
            jar = installed;
            Path marker = directory.resolve("active.properties.tmp");
            Files.writeString(marker, "version=" + update.version() + "\njar=" + jar.getFileName() + "\nsha256=" + expected.toLowerCase(java.util.Locale.ROOT) + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            move(marker, directory.resolve("active.properties"));
            return jar;
        }
    }

    Path cachedUpdate() throws IOException {
        Path root = home.resolve("launcher-updates");
        if (!Files.isDirectory(root)) return null;
        Path selected = null;
        String selectedVersion = CURRENT_VERSION;
        try (var dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                try {
                    Path marker = dir.resolve("active.properties");
                    if (!Files.isRegularFile(marker)
                            || !"KnoxSurvivorsLauncher.jar".equals(property(marker, "jar"))) continue;
                    String version = property(marker, "version");
                    Path jar = dir.resolve("KnoxSurvivorsLauncher.jar");
                    if (compare(version, selectedVersion) <= 0 || !Files.isRegularFile(jar)
                            || !sha256(jar).equalsIgnoreCase(property(marker, "sha256"))) continue;
                    verifyJar(jar, version);
                    selected = jar; selectedVersion = version;
                } catch (IOException | IllegalArgumentException corrupt) {
                    // A broken newer cache must not hide a verified older cache.
                }
            }
        }
        return selected;
    }

    boolean launchCachedIfNewer() {
        try {
            Path selected = cachedUpdate();
            if (selected == null) return false;
            new ProcessBuilder(javaExecutable(), "-jar", selected.toAbsolutePath().toString()).inheritIO().start();
            return true;
        } catch (Exception ignored) { return false; }
    }

    private Path download(URI uri, Path target) throws IOException, InterruptedException {
        if (!isGithubAsset(uri)) throw new IOException("Untrusted update URL.");
        HttpRequest request = HttpRequest.newBuilder(uri).header("User-Agent", "KnoxSurvivorsLauncher/" + CURRENT_VERSION).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("Update download returned HTTP " + response.statusCode());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream input = response.body()) { Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING); }
        move(temporary, target); return target;
    }
    private String downloadText(URI uri) throws IOException, InterruptedException {
        if (!isGithubAsset(uri)) throw new IOException("Untrusted checksum URL.");
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri).header("User-Agent", "KnoxSurvivorsLauncher/" + CURRENT_VERSION).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("Checksum download returned HTTP " + response.statusCode());
        return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
    }
    private static boolean isGithubAsset(URI uri) { return "https".equalsIgnoreCase(uri.getScheme()) && ("github.com".equalsIgnoreCase(uri.getHost()) || "objects.githubusercontent.com".equalsIgnoreCase(uri.getHost()) || "github-releases.githubusercontent.com".equalsIgnoreCase(uri.getHost())); }
    static String expectedChecksum(String sums) throws IOException {
        Matcher matcher = SHA.matcher(sums);
        if (!matcher.find()) throw new IOException("Launcher JAR checksum is missing.");
        String hash = matcher.group(1);
        if (matcher.find()) throw new IOException("Duplicate launcher JAR checksums.");
        return hash;
    }
    private static String safeVersion(String version) { return version.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String property(Path file, String key) throws IOException { for (String line : Files.readAllLines(file)) if (line.startsWith(key + "=")) return line.substring(key.length() + 1); return ""; }
    private static String javaExecutable() { return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString(); }
    private static void verifyJar(Path jar, String version) throws IOException { try (JarFile file = new JarFile(jar.toFile())) { var manifest = file.getManifest(); if (manifest == null || !"com.knoxsurvivors.launcher.Main".equals(manifest.getMainAttributes().getValue("Main-Class")) || !version.replaceFirst("^v", "").equals(manifest.getMainAttributes().getValue("Implementation-Version")) || !"1".equals(manifest.getMainAttributes().getValue("Knox-Update-Protocol"))) throw new IOException("Launcher update manifest is invalid."); } }
    private static String sha256(Path file) throws IOException { try (InputStream input = Files.newInputStream(file)) { var digest = getDigest(); input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest)); return HexFormat.of().formatHex(digest.digest()); } }
    private static MessageDigest getDigest() { try { return MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void move(Path from, Path to) throws IOException { try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); } }
    static int compare(String left, String right) {
        if (left == null || right == null) return -1;
        Matcher a = VERSION.matcher(left), b = VERSION.matcher(right);
        if (!a.matches() || !b.matches()) return -1;
        for (int i = 1; i <= 3; i++) {
            int c = new java.math.BigInteger(a.group(i)).compareTo(new java.math.BigInteger(b.group(i)));
            if (c != 0) return c;
        }
        String ap = a.group(4), bp = b.group(4);
        if (ap == null && bp == null) return 0;
        if (ap == null) return 1;
        if (bp == null) return -1;
        String[] aa = ap.split("\\."), bb = bp.split("\\.");
        for (int i = 0; i < Math.min(aa.length, bb.length); i++) {
            boolean an = aa[i].matches("[0-9]+"), bn = bb[i].matches("[0-9]+");
            int c = an && bn ? new java.math.BigInteger(aa[i]).compareTo(new java.math.BigInteger(bb[i]))
                : an != bn ? (an ? -1 : 1) : aa[i].compareTo(bb[i]);
            if (c != 0) return c;
        }
        return Integer.compare(aa.length, bb.length);
    }
}
