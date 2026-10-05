package dev.candle.codex.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.candle.codex.config.Config;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Minimal Modrinth API v2 client. All calls block, so run them off the render thread. */
public final class Modrinth {
    public static final String API = "https://api.modrinth.com/v2";
    private static final Set<String> SORTS = Set.of("relevance", "downloads", "follows", "newest", "updated");

    public static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Thrown for any network / API problem; message is safe to show to the user. */
    public static final class Failure extends RuntimeException {
        public Failure(String msg) {
            super(msg);
        }

        public Failure(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static final class Hit {
        public String id, slug, title, author, description;
        public String iconUrl, featured, clientSide, serverSide, updated;
        public long downloads, follows;
        public List<String> gallery = new ArrayList<>();
        public List<String> categories = new ArrayList<>();
    }

    public static final class SearchResult {
        public final List<Hit> hits = new ArrayList<>();
        public int total;
    }

    private Modrinth() {}

    public static String userAgent() {
        String ver = FabricLoader.getInstance().getModContainer("candle")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        String contact = Config.data.contact == null || Config.data.contact.isBlank() ? "" : " " + Config.data.contact.trim();
        return "CodexClient/" + ver + " (Fabric; Minecraft " + GameVersion.get() + ")" + contact;
    }

    /** Exception text that is never "null": falls back to the exception class name. */
    public static String describe(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static JsonElement send(HttpRequest.Builder b) {
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpRequest rq = b.header("User-Agent", userAgent()).header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(25)).build();
                HttpResponse<String> res = HTTP.send(rq, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code == 429) {
                    Thread.sleep(1500L * (attempt + 1));
                    continue;
                }
                if (code == 404) throw new Failure("Not found on Modrinth");
                if (code / 100 != 2) throw new Failure("Modrinth returned HTTP " + code);
                return JsonParser.parseString(res.body());
            }
            throw new Failure("Modrinth rate limit reached, try again in a moment");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure("Interrupted", e);
        } catch (IOException e) {
            throw new Failure("Network error: " + describe(e), e);
        }
    }

    static JsonElement get(String path) {
        return send(HttpRequest.newBuilder(URI.create(API + path)).GET());
    }

    public static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    public static SearchResult search(Installer.Kind kind, String query, String sort, int offset, int limit) {
        String gv = GameVersion.get();
        if (!SORTS.contains(sort)) sort = "relevance";
        StringBuilder f = new StringBuilder("[[\"project_type:").append(kind.apiType).append("\"],[\"versions:").append(gv).append("\"]");
        if (kind == Installer.Kind.MOD) {
            f.append(",[\"categories:fabric\"],[\"client_side:required\",\"client_side:optional\"]");
        } else if (kind == Installer.Kind.SHADER) {
            f.append(",[\"categories:iris\",\"categories:optifine\"]");
        }
        f.append("]");
        String path = "/search?query=" + enc(query == null ? "" : query) + "&facets=" + enc(f.toString())
                + "&index=" + sort + "&offset=" + offset + "&limit=" + limit;
        JsonObject o = get(path).getAsJsonObject();
        SearchResult r = new SearchResult();
        r.total = o.has("total_hits") ? o.get("total_hits").getAsInt() : 0;
        for (JsonElement e : o.getAsJsonArray("hits")) {
            JsonObject h = e.getAsJsonObject();
            Hit hit = new Hit();
            hit.id = str(h, "project_id");
            hit.slug = str(h, "slug");
            hit.title = str(h, "title");
            hit.author = str(h, "author");
            hit.description = str(h, "description");
            hit.downloads = h.has("downloads") ? h.get("downloads").getAsLong() : 0;
            hit.follows = h.has("follows") ? h.get("follows").getAsLong() : 0;
            hit.iconUrl = str(h, "icon_url");
            hit.featured = str(h, "featured_gallery");
            hit.clientSide = str(h, "client_side");
            hit.serverSide = str(h, "server_side");
            hit.updated = str(h, "date_modified");
            strings(h.get("gallery"), hit.gallery);
            strings(h.get("display_categories"), hit.categories);
            if (hit.categories.isEmpty()) strings(h.get("categories"), hit.categories);
            if (hit.id != null) r.hits.add(hit);
        }
        return r;
    }

    public static void strings(JsonElement e, List<String> out) {
        if (e == null || !e.isJsonArray()) return;
        for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
    }

    /** Team members of a project (user + role). */
    public static JsonArray members(String projectId) {
        return get("/project/" + enc(projectId) + "/members").getAsJsonArray();
    }

    public static JsonObject project(String idOrSlug) {
        return get("/project/" + enc(idOrSlug)).getAsJsonObject();
    }

    public static JsonObject version(String versionId) {
        return get("/version/" + enc(versionId)).getAsJsonObject();
    }

    /** Versions of a project compatible with the given game version (and loaders, if non-null), newest first. */
    public static JsonArray versions(String projectId, String gameVersion, String loadersJson) {
        String path = "/project/" + enc(projectId) + "/version?game_versions=" + enc("[\"" + gameVersion + "\"]");
        if (loadersJson != null) path += "&loaders=" + enc(loadersJson);
        return get(path).getAsJsonArray();
    }

    /** Prefers the newest release, then beta, then alpha. */
    public static JsonObject pick(JsonArray versions) {
        JsonObject beta = null, alpha = null;
        for (JsonElement e : versions) {
            JsonObject v = e.getAsJsonObject();
            String t = str(v, "version_type");
            if ("release".equals(t)) return v;
            if ("beta".equals(t) && beta == null) beta = v;
            if (alpha == null) alpha = v;
        }
        return beta != null ? beta : alpha;
    }

    public static boolean supports(JsonObject version, String gameVersion, Installer.Kind kind) {
        if (!arrayHas(version.getAsJsonArray("game_versions"), gameVersion)) return false;
        if (kind == Installer.Kind.MOD) return arrayHas(version.getAsJsonArray("loaders"), "fabric");
        if (kind == Installer.Kind.SHADER) {
            return arrayHas(version.getAsJsonArray("loaders"), "iris") || arrayHas(version.getAsJsonArray("loaders"), "optifine");
        }
        return true;
    }

    private static boolean arrayHas(JsonArray a, String v) {
        if (a == null) return false;
        for (JsonElement e : a) if (v.equals(e.getAsString())) return true;
        return false;
    }

    public static JsonObject primaryFile(JsonObject version) {
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.isEmpty()) return null;
        for (JsonElement e : files) {
            JsonObject f = e.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) return f;
        }
        return files.get(0).getAsJsonObject();
    }

    /** Looks up which Modrinth versions the given SHA-1 hashes belong to. Result: hash -> version object. */
    public static JsonObject versionsByHashes(Collection<String> sha1s) {
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String h : sha1s) arr.add(h);
        body.add("hashes", arr);
        body.addProperty("algorithm", "sha1");
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + "/version_files"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return send(b).getAsJsonObject();
    }
}
