package dev.candle.codex.perf;

import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.level.ParticleStatus;

import java.util.List;
import java.util.Map;

/**
 * Applies the "FPS Boost" settings. Original values are saved before the first change so that
 * "Restore vanilla" brings back exactly what the player had. The mixin flags are plain static booleans.
 */
public final class FpsBoost {
    public static volatile boolean reduceExplosions;
    public static volatile boolean noFire;

    /** Optimizer mods installed by the Crystal PvP preset (skipped automatically if no 1.21.11 build exists). */
    public static final List<String> OPTIMIZER_MODS = List.of(
            "sodium", "lithium", "ferrite-core", "entityculling", "immediatelyfast", "more-culling");

    private FpsBoost() {}

    public enum Preset { BALANCED, MAX_FPS, CRYSTAL_PVP }

    public static void preset(Preset p) {
        Config.Perf c = Config.data.perf;
        c.active = true;
        c.reduceExplosions = true;
        c.cloudsOff = true;
        c.shadowsOff = true;
        switch (p) {
            case BALANCED -> {
                c.noFireOverlay = false;
                c.particlesMinimal = false;
                c.unlimitedFps = false;
                c.vsyncOff = false;
                c.noDamageTilt = false;
                c.noViewBob = false;
                c.entityDistance = 0.8f;
                c.renderDistance = 12;
                c.biomeBlend = 1;
            }
            case MAX_FPS -> {
                c.noFireOverlay = true;
                c.particlesMinimal = true;
                c.unlimitedFps = true;
                c.vsyncOff = true;
                c.noDamageTilt = true;
                c.noViewBob = true;
                c.entityDistance = 0.5f;
                c.renderDistance = 8;
                c.biomeBlend = 0;
            }
            case CRYSTAL_PVP -> {
                c.noFireOverlay = true;
                c.particlesMinimal = true;
                c.unlimitedFps = true;
                c.vsyncOff = true;
                c.noDamageTilt = true;
                c.noViewBob = false;
                c.entityDistance = 0.75f;
                c.renderDistance = 10;
                c.biomeBlend = 1;
            }
        }
        c.lowSimulation = p != Preset.BALANCED;
        c.simDistance = p == Preset.MAX_FPS ? 6 : 8;
        c.lowMipmaps = p == Preset.MAX_FPS;
        Config.markDirty();
        apply();
    }

    private static <T> void set(Map<String, String> backup, String key, OptionInstance<T> opt, T value, java.util.function.Function<T, String> str) {
        if (opt.get().equals(value)) return;
        backup.putIfAbsent(key, str.apply(opt.get()));
        opt.set(value);
    }

    /** Pushes the configuration into the game. Safe to call at any time on the client thread. */
    public static void apply() {
        Config.Perf c = Config.data.perf;
        reduceExplosions = c.active && c.reduceExplosions;
        noFire = c.active && c.noFireOverlay;
        if (!c.active) return;
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        Map<String, String> b = c.backup;
        try {
            set(b, "renderDistance", o.renderDistance(), clamp(c.renderDistance, 2, 32), String::valueOf);
            set(b, "entityDistance", o.entityDistanceScaling(), (double) Math.max(0.5f, Math.min(5f, c.entityDistance)), String::valueOf);
            set(b, "biomeBlend", o.biomeBlendRadius(), clamp(c.biomeBlend, 0, 7), String::valueOf);
            if (c.unlimitedFps) set(b, "fps", o.framerateLimit(), 260, String::valueOf);
            if (c.vsyncOff) set(b, "vsync", o.enableVsync(), false, String::valueOf);
            if (c.cloudsOff) set(b, "clouds", o.cloudStatus(), CloudStatus.OFF, Enum::name);
            if (c.shadowsOff) set(b, "shadows", o.entityShadows(), false, String::valueOf);
            if (c.particlesMinimal) set(b, "particles", o.particles(), ParticleStatus.MINIMAL, Enum::name);
            if (c.noDamageTilt) set(b, "tilt", o.damageTiltStrength(), 0.0, String::valueOf);
            if (c.noViewBob) set(b, "bob", o.bobView(), false, String::valueOf);
            if (c.lowSimulation) set(b, "sim", o.simulationDistance(), clamp(c.simDistance, 5, 32), String::valueOf);
            if (c.lowMipmaps) set(b, "mip", o.mipmapLevels(), 1, String::valueOf);
        } catch (RuntimeException | LinkageError e) {
            CodexClient.LOG.warn("FPS boost could not apply an option", e);
        }
        Config.markDirty();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Restores every option Codex changed and turns the boost off. */
    public static void restoreVanilla() {
        restoreValues();
        Config.Perf c = Config.data.perf;
        c.active = false;
        reduceExplosions = false;
        noFire = false;
        Config.markDirty();
    }

    /** Puts original values back, then applies the current configuration again (used after a toggle changes). */
    public static void reapply() {
        restoreValues();
        apply();
    }

    private static void restoreValues() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        Map<String, String> b = Config.data.perf.backup;
        try {
            if (b.containsKey("renderDistance")) o.renderDistance().set(Integer.parseInt(b.get("renderDistance")));
            if (b.containsKey("entityDistance")) o.entityDistanceScaling().set(Double.parseDouble(b.get("entityDistance")));
            if (b.containsKey("biomeBlend")) o.biomeBlendRadius().set(Integer.parseInt(b.get("biomeBlend")));
            if (b.containsKey("fps")) o.framerateLimit().set(Integer.parseInt(b.get("fps")));
            if (b.containsKey("vsync")) o.enableVsync().set(Boolean.parseBoolean(b.get("vsync")));
            if (b.containsKey("clouds")) o.cloudStatus().set(CloudStatus.valueOf(b.get("clouds")));
            if (b.containsKey("shadows")) o.entityShadows().set(Boolean.parseBoolean(b.get("shadows")));
            if (b.containsKey("particles")) o.particles().set(ParticleStatus.valueOf(b.get("particles")));
            if (b.containsKey("tilt")) o.damageTiltStrength().set(Double.parseDouble(b.get("tilt")));
            if (b.containsKey("bob")) o.bobView().set(Boolean.parseBoolean(b.get("bob")));
            if (b.containsKey("sim")) o.simulationDistance().set(Integer.parseInt(b.get("sim")));
            if (b.containsKey("mip")) o.mipmapLevels().set(Integer.parseInt(b.get("mip")));
        } catch (RuntimeException e) {
            CodexClient.LOG.warn("Could not restore an option", e);
        }
        b.clear();
        o.save();
    }
}
