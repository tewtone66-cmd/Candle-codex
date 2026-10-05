package dev.candle.codex.module;

import dev.candle.codex.config.Config;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Base class of every Codex module. onTick is only called while the module is enabled. */
public abstract class Module {
    public final String id, name, desc, category;
    public boolean enabled;
    /** Lang key of a warning label, or null. */
    public String warning;
    public final List<Setting> settings = new ArrayList<>();
    /** Menu glow animation value (0..1). */
    public float glow;
    private boolean needsEnableHook;

    protected Module(String id, String name, String desc, String category) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.category = category;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    public void onEnable() {}

    public void onDisable() {}

    public void onTick(Minecraft mc) {}

    public boolean isHud() {
        return false;
    }

    /** Used when the config has no entry for this module yet (first run). */
    protected boolean defaultEnabled() {
        return false;
    }

    /** Reads enabled state and settings from the config. Hooks run on the first tick. */
    public void loadFromConfig() {
        Boolean en = Config.data.enabled.get(id);
        enabled = en != null ? en : defaultEnabled();
        needsEnableHook = enabled;
        Map<String, String> saved = Config.data.settings.get(id);
        if (saved != null) for (Setting s : settings) s.deserialize(saved.get(s.id));
    }

    public final void runPendingHook() {
        if (needsEnableHook) {
            needsEnableHook = false;
            onEnable();
        }
    }

    public void setEnabled(boolean v) {
        if (v == enabled) return;
        enabled = v;
        needsEnableHook = false;
        Config.data.enabled.put(id, v);
        Config.markDirty();
        if (v) onEnable();
        else onDisable();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** Persist settings after the UI changed one. */
    public void touch() {
        Config.data.settings.put(id, settingsMap());
        Config.markDirty();
    }

    public Map<String, String> settingsMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Setting s : settings) m.put(s.id, s.serialize());
        return m;
    }

    /** Applies a state coming from a profile. */
    public void applyState(Boolean en, Map<String, String> savedSettings) {
        if (savedSettings != null) {
            for (Setting s : settings) s.deserialize(savedSettings.get(s.id));
            Config.data.settings.put(id, settingsMap());
        }
        setEnabled(en != null && en);
        Config.markDirty();
    }

    public boolean matches(String q) {
        if (q.isEmpty()) return true;
        if (name.toLowerCase().contains(q) || desc.toLowerCase().contains(q) || category.toLowerCase().contains(q)) return true;
        for (Setting s : settings) if (s.name.toLowerCase().contains(q)) return true;
        return false;
    }
}
