package dev.candle.codex.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.candle.codex.CodexClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent client configuration (config/candle/config.json). */
public final class Config {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** FPS-boost related options. */
    public static final class Perf {
        /** When true Codex manages the video options below. */
        public boolean active = false;
        public boolean reduceExplosions = false;
        public boolean noFireOverlay = false;
        public boolean particlesMinimal = false;
        public boolean unlimitedFps = false;
        public boolean vsyncOff = false;
        public boolean cloudsOff = false;
        public boolean shadowsOff = false;
        public boolean noDamageTilt = false;
        public boolean noViewBob = false;
        public boolean lowSimulation = false;
        public int simDistance = 8;
        public boolean lowMipmaps = false;
        public float entityDistance = 1.0f;
        public int renderDistance = 12;
        public int biomeBlend = 1;
        /** Original vanilla values captured before the first boost, so they can be restored. */
        public Map<String, String> backup = new LinkedHashMap<>();
    }

    /** Selected cosmetics (ids of the entries in dev.candle.cosmetics.Cosmetics). */
    public static final class Cosmetic {
        public String cape = "none";
        public String hat = "none";
        public String wings = "none";
        /** Glasses on the face (id from Cosmetics.GLASSES). */
        public String glasses = "none";
        /** Pet on the shoulder, or the butterfly around the head (id from Pets.LIST). */
        public String pet = "none";
        /** Aura around the player (id from Auras.LIST). */
        public String aura = "none";
        /** Background RGB of the animated "Codex Ad" cape. */
        public int adColor = 0x161A34;
        /** Background RGB of the animated "Codex Flame" cape. */
        public int flameColor = 0x161A34;
        /** Flap speed of the Dragon wings: 0.2 (slow) to 3 (fast), 1 = normal. */
        public float dragonSpeed = 1.0f;
    }

    public static final class Data {
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
        public Map<String, Map<String, String>> settings = new LinkedHashMap<>();
        public Map<String, float[]> hud = new LinkedHashMap<>();
        public String theme = "Flame";
        public String profile = "PvP";
        /** Optional contact appended to the Modrinth User-Agent (Modrinth asks for one). */
        public String contact = "";
        public Perf perf = new Perf();
        /** Replace the vanilla title screen with the Codex lobby. */
        public boolean lobby = true;
        /** Show your own name tag in third person. */
        public boolean ownName = true;
        /** Codex badge in front of your name. */
        public boolean badge = true;
        /** RGB of the badge plate, or -1 for the theme accent. */
        public int badgeColor = 0xFFFFFF;
        /** Menu animation speed: 0 = off, 1 = normal. */
        public float animSpeed = 1.0f;
        /** Menu size factor (1 = normal). The menu never gets smaller than its minimum layout. */
        public float uiScale = 1.0f;
        public Cosmetic cosmetics = new Cosmetic();
        /** True once the HUD backgrounds were switched off by the 1.5.0 migration. */
        public boolean hudBgRemoved = false;
        /** True once the player closed the Glitchy pack banner at the top of the menu (Settings can show it again). */
        public boolean glitchBannerHidden = false;
    }

    public static Data data = new Data();
    private static boolean dirty;
    private static long lastSave;

    private Config() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("candle");
    }

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static void load() {
        try {
            Files.createDirectories(dir());
            Path p = dir().resolve("config.json");
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) data = d;
                }
            }
        } catch (Exception e) {
            CodexClient.LOG.warn("Could not read Codex config, using defaults", e);
        }
        if (data.enabled == null) data.enabled = new LinkedHashMap<>();
        if (data.settings == null) data.settings = new LinkedHashMap<>();
        if (data.hud == null) data.hud = new LinkedHashMap<>();
        if (data.perf == null) data.perf = new Perf();
        if (data.perf.backup == null) data.perf.backup = new LinkedHashMap<>();
        if (data.theme == null) data.theme = "Flame";
        if (data.profile == null) data.profile = "PvP";
        if (data.contact == null) data.contact = "";
        if (data.cosmetics == null) data.cosmetics = new Cosmetic();
        if (data.cosmetics.cape == null) data.cosmetics.cape = "none";
        if (data.cosmetics.hat == null) data.cosmetics.hat = "none";
        if (data.cosmetics.wings == null) data.cosmetics.wings = "none";
        if (data.cosmetics.glasses == null) data.cosmetics.glasses = "none";
        if (data.cosmetics.pet == null) data.cosmetics.pet = "none";
        if (data.cosmetics.aura == null) data.cosmetics.aura = "none";
        if (!data.hudBgRemoved) {
            // 1.5.0: HUD backgrounds are off by default; switch off the saved ones once (users can turn them on again)
            for (Map<String, String> m : data.settings.values()) {
                if (m != null && m.containsKey("background")) m.put("background", "false");
            }
            data.hudBgRemoved = true;
        }
        // the comparisons are written so that NaN also falls back to the default
        if (!(data.animSpeed >= 0f && data.animSpeed <= 4f)) data.animSpeed = 1f;
        if (!(data.uiScale >= 0.5f && data.uiScale <= 1.5f)) data.uiScale = 1f;
        if (!(data.cosmetics.dragonSpeed >= 0.2f && data.cosmetics.dragonSpeed <= 3f)) data.cosmetics.dragonSpeed = 1f;
    }

    public static void markDirty() {
        dirty = true;
    }

    /** Called every tick; writes at most once every two seconds. */
    public static void flushIfNeeded() {
        if (dirty && System.currentTimeMillis() - lastSave > 2000) save();
    }

    /**
     * Replaces every setting with its default and writes the file. Callers must first undo what is applied to the
     * game (FpsBoost.restoreVanilla needs the backup that lives in the old data) and reset the modules.
     */
    public static synchronized void resetAll() {
        data = new Data();
        save();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(dir());
            Path tmp = dir().resolve("config.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, dir().resolve("config.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, dir().resolve("config.json"), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (Exception e) {
            CodexClient.LOG.warn("Could not save Codex config", e);
        }
    }
}
