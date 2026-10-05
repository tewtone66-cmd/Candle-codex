package dev.candle.codex.config;

import dev.candle.codex.CodexClient;
import dev.candle.codex.module.ModuleManager;
import dev.candle.codex.perf.FpsBoost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Quick-switch setting profiles stored in config/candle/profiles/*.json. */
public final class ProfileManager {
    public static final class ProfileData {
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
        public Map<String, Map<String, String>> settings = new LinkedHashMap<>();
        public Map<String, float[]> hud = new LinkedHashMap<>();
        public Config.Perf perf;
    }

    private ProfileManager() {}

    public static Path dir() {
        return Config.dir().resolve("profiles");
    }

    private static String clean(String n) {
        String s = n == null ? "" : n.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (s.length() > 24) s = s.substring(0, 24).trim();
        return s.isEmpty() ? "Profile" : s;
    }

    /** Creates the two starter profiles the first time Codex runs. */
    public static void ensureDefaults() {
        try {
            Files.createDirectories(dir());
            if (!Files.exists(dir().resolve("PvP.json"))) {
                ProfileData d = new ProfileData();
                for (String id : new String[]{"fps", "ping", "cps", "keystrokes", "armor", "totems", "crystals", "hitcolor", "togglesprint"}) d.enabled.put(id, true);
                Files.writeString(dir().resolve("PvP.json"), Config.GSON.toJson(d), StandardCharsets.UTF_8);
            }
            if (!Files.exists(dir().resolve("Build.json"))) {
                ProfileData d = new ProfileData();
                for (String id : new String[]{"fps", "coords", "zoom", "togglesprint"}) d.enabled.put(id, true);
                Files.writeString(dir().resolve("Build.json"), Config.GSON.toJson(d), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            CodexClient.LOG.warn("Could not create default profiles", e);
        }
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir(), "*.json")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                out.add(n.substring(0, n.length() - 5));
            }
        } catch (IOException ignored) {
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static String saveCurrent(String requested) {
        String name = clean(requested);
        ProfileData d = new ProfileData();
        for (var m : ModuleManager.ALL) {
            d.enabled.put(m.id, m.enabled);
            d.settings.put(m.id, m.settingsMap());
        }
        for (var h : ModuleManager.HUD) d.hud.put(h.id, new float[]{h.fx, h.fy});
        d.perf = Config.GSON.fromJson(Config.GSON.toJson(Config.data.perf), Config.Perf.class);
        d.perf.backup = null; // backups are per install, not per profile
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(name + ".json"), Config.GSON.toJson(d), StandardCharsets.UTF_8);
            Config.data.profile = name;
            Config.markDirty();
            return name;
        } catch (IOException e) {
            CodexClient.LOG.warn("Could not save profile", e);
            return null;
        }
    }

    public static boolean load(String name) {
        try {
            Path p = dir().resolve(clean(name) + ".json");
            if (!Files.exists(p)) return false;
            ProfileData d = Config.GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), ProfileData.class);
            if (d == null) return false;
            ModuleManager.apply(d.enabled, d.settings, d.hud);
            if (d.perf != null) {
                Config.Perf cur = Config.data.perf;
                Map<String, String> keep = cur.backup;
                boolean wasActive = cur.active;
                Config.data.perf = d.perf;
                Config.data.perf.backup = keep;
                if (wasActive && !d.perf.active) FpsBoost.restoreVanilla();
                else FpsBoost.apply();
            }
            Config.data.profile = clean(name);
            Config.markDirty();
            return true;
        } catch (IOException | RuntimeException e) {
            CodexClient.LOG.warn("Could not load profile {}", name, e);
            return false;
        }
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(dir().resolve(clean(name) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }
}
