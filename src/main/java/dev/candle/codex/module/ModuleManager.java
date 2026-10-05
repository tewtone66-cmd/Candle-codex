package dev.candle.codex.module;

import dev.candle.codex.config.Config;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();
    public static final List<HudModule> HUD = new ArrayList<>();

    /** Setting values every module had right after it was constructed (before the config was read). */
    private static final Map<String, Map<String, String>> DEFAULTS = new HashMap<>();

    public static Modules.Cps CPS;
    public static Modules.Keystrokes KEYSTROKES;
    public static Modules.Crosshair CROSSHAIR;
    public static Modules.HitColor HITCOLOR;
    public static Modules.Fullbright FULLBRIGHT;
    public static ExtraModules.Combo COMBO;
    public static ExtraModules.Reach REACH;

    private ModuleManager() {}

    private static <T extends Module> T reg(T m) {
        ALL.add(m);
        if (m instanceof HudModule h) HUD.add(h);
        DEFAULTS.put(m.id, m.settingsMap());
        m.loadFromConfig();
        return m;
    }

    /** Must run after the key category exists (modules register their key binds in constructors). */
    public static void init() {
        reg(new Modules.Fps());
        reg(new Modules.FpsGraph());
        reg(new Modules.Clock());
        reg(new Modules.Memory());
        reg(new Modules.Speed());
        reg(new Modules.Direction());
        reg(new Modules.Ping());
        reg(new Modules.Coordinates());
        reg(new Modules.PotionHud());
        CPS = reg(new Modules.Cps());
        KEYSTROKES = reg(new Modules.Keystrokes());
        reg(new Modules.ArmorHud());
        reg(new Modules.Totems());
        reg(new Modules.Crystals());
        HITCOLOR = reg(new Modules.HitColor());
        reg(new Modules.ToggleSprint());
        reg(new ZoomModule());
        reg(new Modules.Freelook());
        CROSSHAIR = reg(new Modules.Crosshair());
        FULLBRIGHT = reg(new Modules.Fullbright());
        // part 4
        reg(new ExtraModules.ToggleSneak());
        COMBO = reg(new ExtraModules.Combo());
        REACH = reg(new ExtraModules.Reach());
        reg(new ExtraModules.Food());
        reg(new ExtraModules.ItemCount());
    }

    public static Module byId(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    public static void tick(Minecraft mc) {
        if ((COMBO != null && COMBO.enabled) || (REACH != null && REACH.enabled)) ExtraModules.HitTracker.tick(mc);
        for (int i = 0; i < ALL.size(); i++) {
            Module m = ALL.get(i);
            if (!m.enabled) continue;
            m.runPendingHook();
            m.onTick(mc);
        }
    }

    public static void shutdown() {
        if (FULLBRIGHT != null) FULLBRIGHT.restore();
    }

    /** Applies a profile snapshot to every module. */
    public static void apply(Map<String, Boolean> enabled, Map<String, Map<String, String>> settings, Map<String, float[]> hud) {
        for (Module m : ALL) {
            m.applyState(enabled == null ? null : enabled.get(m.id), settings == null ? null : settings.get(m.id));
            if (m instanceof HudModule h) {
                float[] p = hud == null ? null : hud.get(m.id);
                if (p != null && p.length == 2) {
                    h.fx = p[0];
                    h.fy = p[1];
                    Config.data.hud.put(m.id, new float[]{p[0], p[1]});
                }
            }
        }
        Config.markDirty();
    }

    /** Puts every module back to its first-run state: default on/off, default settings, default HUD position. */
    public static void resetAll() {
        for (Module m : ALL) {
            Map<String, String> d = DEFAULTS.get(m.id);
            if (d != null) for (Setting s : m.settings) s.deserialize(d.get(s.id));
            m.setEnabled(m.defaultEnabled());
            if (m instanceof HudModule h) h.resetPosition(h.defX, h.defY);
        }
        Config.markDirty();
    }
}
