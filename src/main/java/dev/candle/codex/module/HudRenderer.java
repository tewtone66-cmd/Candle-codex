package dev.candle.codex.module;

import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.HudEditorScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Draws every enabled HUD module, plus the custom crosshair replacement. (The own name tag is a real world name tag now, see NameTag.) */
public final class HudRenderer {
    private HudRenderer() {}

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (mc.screen instanceof HudEditorScreen) return;
        Draw.mul = 1f;
        Theme t = ThemeManager.current();
        int sw = g.guiWidth(), sh = g.guiHeight();
        if (ModuleManager.CPS.enabled || ModuleManager.KEYSTROKES.enabled) ClickTracker.poll();
        List<HudModule> list = ModuleManager.HUD;
        for (int i = 0; i < list.size(); i++) {
            HudModule m = list.get(i);
            if (m.enabled) m.render(g, mc, t, sw, sh);
        }
    }

    /** Replacement for the vanilla crosshair element. */
    public static HudElement crosshair(HudElement original) {
        return (g, dt) -> {
            Minecraft mc = Minecraft.getInstance();
            Modules.Crosshair c = ModuleManager.CROSSHAIR;
            Modules.HitColor hit = ModuleManager.HITCOLOR;
            boolean flash = hit.enabled && hit.flashing();
            if (c.enabled) {
                drawCustom(g, c, flash ? hit.hitColor.color : c.color.color);
            } else {
                original.render(g, dt);
                if (flash && mc.options.getCameraType().isFirstPerson()) drawMarker(g, hit.hitColor.color);
            }
        };
    }

    private static void bar(GuiGraphics g, int x1, int y1, int x2, int y2, int col, boolean outline) {
        if (outline) g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000);
        g.fill(x1, y1, x2, y2, col);
    }

    private static void drawCustom(GuiGraphics g, Modules.Crosshair c, int rgb) {
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int col = 0xFF000000 | rgb;
        int size = c.size.i(), th = c.thickness.i();
        boolean outline = c.outline.bool;
        int lo = th / 2, hi = th - lo;
        switch (c.style.choiceName()) {
            case "Dot" -> bar(g, cx - th, cy - th, cx + th, cy + th, col, outline);
            case "Circle" -> {
                int r = Math.min(16, size + 2);
                Draw.ring(g, cx - r, cy - r, r * 2, r * 2, r, col);
            }
            default -> {
                int gap = "Cross".equals(c.style.choiceName()) ? 0 : c.gap.i();
                bar(g, cx - gap - size, cy - lo, cx - gap, cy + hi, col, outline);
                bar(g, cx + gap, cy - lo, cx + gap + size, cy + hi, col, outline);
                bar(g, cx - lo, cy - gap - size, cx + hi, cy - gap, col, outline);
                bar(g, cx - lo, cy + gap, cx + hi, cy + gap + size, col, outline);
            }
        }
    }

    private static void drawMarker(GuiGraphics g, int rgb) {
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int col = 0xFF000000 | rgb;
        for (int i = 3; i <= 6; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
            g.fill(cx - i - 1, cy + i, cx - i, cy + i + 1, col);
            g.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, col);
            g.fill(cx - i - 1, cy - i - 1, cx - i, cy - i, col);
        }
    }
}
