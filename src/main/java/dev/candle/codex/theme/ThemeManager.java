package dev.candle.codex.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Holds the active theme and handles save / delete / import / export (config/candle/themes/*.json). */
public final class ThemeManager {
    private static Theme current = Theme.flame();

    private ThemeManager() {}

    public static Path dir() {
        return Config.dir().resolve("themes");
    }

    public static Theme current() {
        return current;
    }

    public static void init() {
        try {
            Files.createDirectories(dir());
        } catch (IOException e) {
            CodexClient.LOG.warn("Could not create themes dir", e);
        }
        Theme t = find(Config.data.theme);
        setCurrent(t != null ? t : Theme.flame());
    }

    public static Theme find(String name) {
        if (name == null) return null;
        if ("Flame".equalsIgnoreCase(name)) return Theme.flame();
        if ("Midnight".equalsIgnoreCase(name)) return Theme.midnight();
        if ("Frost".equalsIgnoreCase(name)) return Theme.frost();
        Path p = dir().resolve(clean(name) + ".json");
        if (!Files.exists(p)) return null;
        try {
            return fromJson(Files.readString(p, StandardCharsets.UTF_8));
        } catch (Exception e) {
            CodexClient.LOG.warn("Bad theme file {}", p, e);
            return null;
        }
    }

    public static List<String> userThemes() {
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

    /** Makes the theme active. */
    public static void setCurrent(Theme t) {
        t.rebuild();
        current = t;
        Config.data.theme = t.name;
        Config.markDirty();
    }

    /** Edits mutate current() directly; call this after changing a field. */
    public static void changed() {
        current.rebuild();
    }

    public static String clean(String name) {
        String n = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (n.length() > 24) n = n.substring(0, 24).trim();
        return n.isEmpty() ? "My Theme" : n;
    }

    public static String save(String requested) {
        String name = clean(requested);
        if (Theme.isPreset(name)) name = name + " Copy";
        Theme t = current.copy();
        t.name = name;
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(name + ".json"), toJson(t), StandardCharsets.UTF_8);
            current.name = name;
            Config.data.theme = name;
            Config.markDirty();
            return name;
        } catch (IOException e) {
            CodexClient.LOG.warn("Could not save theme", e);
            return null;
        }
    }

    public static boolean delete(String name) {
        if (Theme.isPreset(name)) return false;
        try {
            return Files.deleteIfExists(dir().resolve(clean(name) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    public static String toJson(Theme t) {
        return Config.GSON.toJson(t);
    }

    /** Parses and validates theme JSON. Throws if it is not a usable theme. */
    public static Theme fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        Theme t = Config.GSON.fromJson(o, Theme.class);
        if (t == null) throw new IllegalArgumentException("Empty theme");
        String[] cols = {t.bg, t.panel, t.card, t.accent, t.accent2, t.text, t.dim, t.border};
        for (String c : cols) {
            if (c == null || !c.matches("#?[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Invalid colour in theme");
        }
        t.name = clean(t.name);
        t.rebuild();
        return t;
    }

    /** Copies the JSON to the clipboard and writes the file into the themes folder. */
    public static void export(Theme t) {
        String json = toJson(t);
        Minecraft.getInstance().keyboardHandler.setClipboard(json);
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(clean(t.name).toLowerCase(Locale.ROOT).replace(' ', '_') + ".export.json"), json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static Theme importFromClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        return fromJson(clip);
    }
}
