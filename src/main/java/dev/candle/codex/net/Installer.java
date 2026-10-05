package dev.candle.codex.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.candle.codex.CodexClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Downloads mods, resource packs, shader packs and schematics into the running game instance.
 * Every Modrinth download is: resolve compatible version -> stream to a temp file on a worker thread ->
 * verify SHA-1 against the API -> move into place. Required mod dependencies are installed automatically.
 */
public final class Installer {

    public enum Kind {
        MOD("mod", "mods", ".jar", "[\"fabric\"]"),
        RESOURCEPACK("resourcepack", "resourcepacks", ".zip", null),
        SHADER("shader", "shaderpacks", ".zip", "[\"iris\",\"optifine\"]");

        public final String apiType, dirName, ext, loadersJson;

        Kind(String apiType, String dirName, String ext, String loadersJson) {
            this.apiType = apiType;
            this.dirName = dirName;
            this.ext = ext;
            this.loadersJson = loadersJson;
        }

        /** Always resolved from the running instance, never from a fixed path. */
        public Path dir() {
            return FabricLoader.getInstance().getGameDir().resolve(dirName);
        }
    }

    public static final class Result {
        public final List<Path> files = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public boolean needsRestart;

        void merge(Result o) {
            files.addAll(o.files);
            notes.addAll(o.notes);
            needsRestart |= o.needsRestart;
        }
    }

    private static final Set<String> HOSTS = Set.of("cdn.modrinth.com", "cdn-raw.modrinth.com");
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final long MAX_SCHEMATIC_BYTES = 25L * 1024 * 1024;
    private static final Map<String, String> HASH_CACHE = new HashMap<>();

    private Installer() {}

    // ------------------------------------------------------------------ public API

    public static CompletableFuture<Result> install(String projectIdOrSlug, Kind kind, Consumer<String> status) {
        return CompletableFuture.supplyAsync(() -> {
            Result r = new Result();
            try {
                installProject(projectIdOrSlug, null, kind, status, r, new HashSet<>(), true);
            } catch (Modrinth.Failure e) {
                throw new CompletionException(e);
            } catch (Exception e) {
                throw new CompletionException(new Modrinth.Failure(Modrinth.describe(e), e));
            }
            return r;
        }, CodexClient.IO);
    }

    /** Installs several projects in order. A project with no compatible build is skipped with a note. */
    public static CompletableFuture<Result> installAll(List<String> slugs, Kind kind, Consumer<String> status) {
        return CompletableFuture.supplyAsync(() -> {
            Result all = new Result();
            Set<String> seen = new HashSet<>();
            for (String slug : slugs) {
                Result r = new Result();
                try {
                    installProject(slug, null, kind, status, r, seen, true);
                } catch (Exception e) {
                    r.notes.add(slug + ": " + Modrinth.describe(e));
                    CodexClient.LOG.warn("Install of {} failed", slug, e);
                }
                all.merge(r);
            }
            return all;
        }, CodexClient.IO);
    }

    /** Direct-URL schematic import (Modrinth has no schematic project type). */
    public static CompletableFuture<Result> installSchematic(String url, Consumer<String> status) {
        return CompletableFuture.supplyAsync(() -> {
            Result r = new Result();
            try {
                URI u = URI.create(rawLink(url.trim()));
                if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) {
                    throw new Modrinth.Failure("Only https:// links are allowed");
                }
                String path = u.getPath() == null ? "" : u.getPath();
                String name = sanitizeName(path.substring(path.lastIndexOf('/') + 1));
                String lower = name.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".litematic") || lower.endsWith(".schem") || lower.endsWith(".schematic") || lower.endsWith(".nbt"))) {
                    throw new Modrinth.Failure("Link must end with .litematic, .schem, .schematic or .nbt");
                }
                Path tmp = download(u, null, 0, MAX_SCHEMATIC_BYTES, status, name, false);
                Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
                Files.createDirectories(dir);
                Path target = dir.resolve(name);
                move(tmp, target);
                r.files.add(target);
                r.notes.add("Saved to schematics/" + name);
            } catch (Modrinth.Failure e) {
                throw new CompletionException(e);
            } catch (IllegalArgumentException e) {
                throw new CompletionException(new Modrinth.Failure("That is not a valid link"));
            } catch (IOException e) {
                throw new CompletionException(new Modrinth.Failure("Could not save file: " + Modrinth.describe(e), e));
            }
            return r;
        }, CodexClient.IO);
    }

    /** Turns a normal GitHub file page (…/blob/branch/file) into its raw download link. */
    public static String rawLink(String link) {
        String s = link;
        int q = s.indexOf('?');
        if (q > 0 && s.contains("github.com/")) s = s.substring(0, q);
        if (s.startsWith("https://github.com/") && s.contains("/blob/")) {
            s = s.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/");
        }
        return s;
    }

    // ------------------------------------------------------------------ core

    private static void installProject(String idOrSlug, String forcedVersionId, Kind kind, Consumer<String> st,
                                       Result r, Set<String> seen, boolean root) throws IOException {
        JsonObject proj = Modrinth.project(idOrSlug);
        String pid = Modrinth.str(proj, "id");
        String slug = Modrinth.str(proj, "slug");
        String title = Modrinth.str(proj, "title");
        if (pid == null || !seen.add(pid)) return;
        String gv = GameVersion.get();

        if (!root && kind == Kind.MOD) {
            if (isLoaded(slug)) {
                r.notes.add(title + ": already loaded");
                return;
            }
            if (!findInstalled(pid, kind.dir()).isEmpty()) {
                r.notes.add(title + ": already installed");
                return;
            }
        }

        st.accept("Looking up " + title + "...");
        JsonObject ver = null;
        if (forcedVersionId != null) {
            try {
                JsonObject v = Modrinth.version(forcedVersionId);
                if (Modrinth.supports(v, gv, kind)) ver = v;
            } catch (Modrinth.Failure ignored) {
            }
        }
        if (ver == null) ver = Modrinth.pick(Modrinth.versions(pid, gv, kind.loadersJson));
        if (ver == null) {
            throw new Modrinth.Failure(title + " has no build for Minecraft " + gv + (kind == Kind.MOD ? " on Fabric" : ""));
        }
        JsonObject file = Modrinth.primaryFile(ver);
        if (file == null) throw new Modrinth.Failure(title + " has no downloadable file");

        String filename = sanitizeName(Modrinth.str(file, "filename"));
        if (!filename.toLowerCase(Locale.ROOT).endsWith(kind.ext)) {
            throw new Modrinth.Failure(title + ": unexpected file type (" + filename + ")");
        }
        String sha1 = null;
        if (file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")) {
            sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
        }
        if (sha1 == null) throw new Modrinth.Failure(title + ": no SHA-1 from Modrinth, refusing to install");
        long size = file.has("size") ? file.get("size").getAsLong() : 0;

        Path dir = kind.dir();
        Path target = dir.resolve(filename);
        if (Files.exists(target) && sha1.equalsIgnoreCase(sha1Of(target))) {
            r.notes.add(title + ": already up to date (" + filename + ")");
        } else {
            URI url = URI.create(Modrinth.str(file, "url"));
            Path tmp = download(url, sha1, size, MAX_BYTES, st, title, true);
            Files.createDirectories(dir);
            if (kind == Kind.MOD) retireOld(pid, dir, filename, r);
            move(tmp, target);
            r.files.add(target);
            r.notes.add(title + ": installed " + filename);
            if (kind == Kind.MOD) r.needsRestart = true;
        }

        if (kind == Kind.MOD && ver.has("dependencies")) {
            for (JsonElement de : ver.getAsJsonArray("dependencies")) {
                JsonObject d = de.getAsJsonObject();
                if (!"required".equals(Modrinth.str(d, "dependency_type"))) continue;
                String depProject = Modrinth.str(d, "project_id");
                if (depProject == null) continue;
                try {
                    installProject(depProject, Modrinth.str(d, "version_id"), kind, st, r, seen, false);
                } catch (Modrinth.Failure e) {
                    r.notes.add("Dependency problem: " + e.getMessage());
                }
            }
        }
    }

    private static boolean isLoaded(String slug) {
        if (slug == null) return false;
        FabricLoader fl = FabricLoader.getInstance();
        return fl.isModLoaded(slug) || fl.isModLoaded(slug.replace('-', '_'));
    }

    // ------------------------------------------------------------------ files

    private static String sanitizeName(String name) {
        String n = name == null ? "" : name;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.isEmpty() || n.length() > 150) throw new Modrinth.Failure("Unsafe file name");
        return n;
    }

    /** Streams to a temp file on the calling (worker) thread, hashing as it goes. */
    private static Path download(URI url, String expectedSha1, long expectedSize, long maxBytes,
                                 Consumer<String> st, String title, boolean modrinthHostOnly) throws IOException {
        if (!"https".equalsIgnoreCase(url.getScheme())) throw new Modrinth.Failure("Blocked non-https download");
        if (modrinthHostOnly && !HOSTS.contains(url.getHost())) {
            throw new Modrinth.Failure("Blocked download from unexpected host " + url.getHost());
        }
        Path tmp = Files.createTempFile("candle-", ".part");
        try {
            HttpRequest rq = HttpRequest.newBuilder(url)
                    .header("User-Agent", Modrinth.userAgent())
                    .timeout(Duration.ofMinutes(5))
                    .GET().build();
            HttpResponse<InputStream> res = Modrinth.HTTP.send(rq, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() / 100 != 2) {
                res.body().close();
                throw new Modrinth.Failure("Download failed: HTTP " + res.statusCode());
            }
            long total = res.headers().firstValueAsLong("Content-Length").orElse(expectedSize);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            long done = 0;
            long lastUi = 0;
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (done > maxBytes) throw new Modrinth.Failure(title + " is larger than the allowed limit");
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 200) {
                        lastUi = now;
                        st.accept("Downloading " + title + (total > 0 ? " " + (done * 100 / total) + "%" : " " + (done / 1024) + " KB"));
                    }
                }
            }
            String got = HexFormat.of().formatHex(md.digest());
            if (expectedSha1 != null && !got.equalsIgnoreCase(expectedSha1)) {
                throw new Modrinth.Failure("SHA-1 mismatch for " + title + ", file discarded");
            }
            st.accept("Verified " + title);
            return tmp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(tmp);
            throw new Modrinth.Failure("Download interrupted", e);
        } catch (java.security.NoSuchAlgorithmException e) {
            Files.deleteIfExists(tmp);
            throw new Modrinth.Failure("SHA-1 unavailable", e);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static synchronized String sha1Of(Path p) throws IOException {
        String key = p.toAbsolutePath() + "|" + Files.size(p) + "|" + Files.getLastModifiedTime(p).toMillis();
        String cached = HASH_CACHE.get(key);
        if (cached != null) return cached;
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            String h = HexFormat.of().formatHex(md.digest());
            HASH_CACHE.put(key, h);
            return h;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Jars in dir that Modrinth knows as belonging to the given project. */
    private static List<Path> findInstalled(String projectId, Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        Map<String, Path> byHash = new HashMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path p : ds) byHash.put(sha1Of(p), p);
        }
        if (byHash.isEmpty()) return out;
        JsonObject res;
        try {
            res = Modrinth.versionsByHashes(byHash.keySet());
        } catch (Modrinth.Failure e) {
            return out;
        }
        for (Map.Entry<String, Path> e : byHash.entrySet()) {
            JsonElement v = res.get(e.getKey());
            if (v != null && v.isJsonObject() && projectId.equals(Modrinth.str(v.getAsJsonObject(), "project_id"))) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    /** An older jar of the same mod would make Fabric crash on duplicate ids, so rename it out of the way. */
    private static void retireOld(String projectId, Path dir, String newFilename, Result r) throws IOException {
        for (Path old : findInstalled(projectId, dir)) {
            String name = old.getFileName().toString();
            if (name.equals(newFilename)) continue;
            Path disabled = dir.resolve(name + ".disabled");
            Files.move(old, disabled, StandardCopyOption.REPLACE_EXISTING);
            r.notes.add("Old version disabled: " + name);
        }
    }

    /** Used by the UI to show a friendly message from a failed future. */
    public static String message(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && (c instanceof CompletionException || c instanceof java.util.concurrent.ExecutionException)) {
            c = c.getCause();
        }
        String m = c.getMessage();
        return m == null || m.isBlank() ? c.getClass().getSimpleName() : m;
    }
}
