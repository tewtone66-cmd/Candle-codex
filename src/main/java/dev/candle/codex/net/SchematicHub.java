package dev.candle.codex.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds schematics on GitHub (Modrinth has no schematic section). Uses only the public, key-less GitHub API:
 * repository search, then the file tree of one repository. Files are downloaded from raw.githubusercontent.com
 * through {@link Installer#installSchematic}.
 */
public final class SchematicHub {
    public static final class Repo {
        public String fullName, description, avatar, branch;
        public int stars;
    }

    public static final class FileEntry {
        public String path, name;
        public long size;
    }

    private static final String API = "https://api.github.com";

    private SchematicHub() {}

    private static JsonElement get(String url) {
        try {
            HttpRequest rq = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", Modrinth.userAgent())
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> res = Modrinth.HTTP.send(rq, HttpResponse.BodyHandlers.ofString());
            int c = res.statusCode();
            if (c == 403 || c == 429) throw new Modrinth.Failure("GitHub rate limit reached, try again in a minute");
            if (c == 404) throw new Modrinth.Failure("Not found on GitHub");
            if (c / 100 != 2) throw new Modrinth.Failure("GitHub returned HTTP " + c);
            return JsonParser.parseString(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Modrinth.Failure("Interrupted", e);
        } catch (IOException e) {
            throw new Modrinth.Failure("Network error: " + Modrinth.describe(e), e);
        }
    }

    public static List<Repo> search(String text) {
        String t = text == null ? "" : text.trim();
        String q = (t.isEmpty() ? "" : t + " ") + "litematic in:name,description,topics";
        String url = API + "/search/repositories?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&sort=stars&order=desc&per_page=30";
        JsonObject o = get(url).getAsJsonObject();
        List<Repo> out = new ArrayList<>();
        JsonArray items = o.getAsJsonArray("items");
        if (items == null) return out;
        for (JsonElement e : items) {
            JsonObject r = e.getAsJsonObject();
            Repo repo = new Repo();
            repo.fullName = Modrinth.str(r, "full_name");
            repo.description = Modrinth.str(r, "description");
            repo.branch = Modrinth.str(r, "default_branch");
            repo.stars = r.has("stargazers_count") ? r.get("stargazers_count").getAsInt() : 0;
            JsonObject owner = r.has("owner") && r.get("owner").isJsonObject() ? r.getAsJsonObject("owner") : null;
            repo.avatar = owner == null ? null : Modrinth.str(owner, "avatar_url");
            if (repo.fullName != null && repo.branch != null) out.add(repo);
        }
        return out;
    }

    public static List<FileEntry> files(Repo repo) {
        String url = API + "/repos/" + repo.fullName + "/git/trees/" + URLEncoder.encode(repo.branch, StandardCharsets.UTF_8) + "?recursive=1";
        JsonObject o = get(url).getAsJsonObject();
        List<FileEntry> out = new ArrayList<>();
        JsonArray tree = o.getAsJsonArray("tree");
        if (tree == null) return out;
        for (JsonElement e : tree) {
            JsonObject n = e.getAsJsonObject();
            if (!"blob".equals(Modrinth.str(n, "type"))) continue;
            String path = Modrinth.str(n, "path");
            if (path == null) continue;
            String low = path.toLowerCase(Locale.ROOT);
            if (!(low.endsWith(".litematic") || low.endsWith(".schem") || low.endsWith(".schematic") || low.endsWith(".nbt"))) continue;
            FileEntry f = new FileEntry();
            f.path = path;
            f.name = path.substring(path.lastIndexOf('/') + 1);
            f.size = n.has("size") ? n.get("size").getAsLong() : 0;
            if (f.size > 25L * 1024 * 1024) continue;
            out.add(f);
            if (out.size() >= 400) break;
        }
        return out;
    }

    public static String rawUrl(Repo repo, FileEntry f) {
        StringBuilder sb = new StringBuilder("https://raw.githubusercontent.com/").append(repo.fullName).append('/');
        sb.append(URLEncoder.encode(repo.branch, StandardCharsets.UTF_8).replace("+", "%20")).append('/');
        String[] parts = f.path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }
}
