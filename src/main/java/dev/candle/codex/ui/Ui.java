package dev.candle.codex.ui;

import dev.candle.codex.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** Shared themed controls: buttons, switches, sliders. All geometry is passed in, nothing is allocated. */
public final class Ui {
    private Ui() {}

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void button(GuiGraphics g, Theme t, String label, int x, int y, int w, int h, boolean hover, boolean primary) {
        int r = Math.max(2, t.radius / 2 + 1);
        float a = Anim.to(Anim.key(1, x, y, w, h), hover ? 1f : 0f, 16f);
        if (primary) {
            int base = Theme.mix(t.cAccent, t.cAccent2, a);
            if (a > 0.05f) Draw.glow(g, x, y, w, h, r, t.cAccent, Math.round(a * 5), 3);
            Draw.rr(g, x, y, w, h, r, 0xFF000000 | base);
            Draw.textC(g, label, x + w / 2, y + (h - 8) / 2, t.cBg);
        } else {
            Draw.rr(g, x, y, w, h, r, Anim.argb(t.aCard, t.aCardHover, a));
            Draw.ring(g, x, y, w, h, r, Anim.argb(t.aBorder, (0xAA << 24) | t.cAccent, a));
            Draw.textC(g, label, x + w / 2, y + (h - 8) / 2, Theme.mix(t.cText, t.cAccent2, a));
        }
    }

    public static void chip(GuiGraphics g, Theme t, String label, int x, int y, int w, int h, boolean active, boolean hover) {
        int r = h / 2;
        float act = Anim.to(Anim.key(2, x, y, w, h), active ? 1f : 0f, 14f);
        float hv = Anim.to(Anim.key(3, x, y, w, h), hover ? 1f : 0f, 16f);
        int base = Anim.argb(t.aCard, t.aCardHover, hv);
        Draw.rr(g, x, y, w, h, r, Anim.argb(base, (0x30 << 24) | t.cAccent, act));
        if (act > 0.02f) Draw.ring(g, x, y, w, h, r, (Math.round(act * 0xCC) << 24) | t.cAccent);
        int idle = Theme.mix(t.cDim, t.cText, hv);
        Draw.textC(g, label, x + w / 2, y + (h - 8) / 2, Theme.mix(idle, t.cAccent2, act));
    }

    /** Pill switch, 26x12. The knob slides smoothly whenever the state changes. */
    public static void toggle(GuiGraphics g, Theme t, int x, int y, boolean on, float ignored) {
        int w = 26, h = 12;
        float anim = Anim.to(Anim.key(4, x, y, w, h), on ? 1f : 0f, 14f);
        int track = Theme.mix(t.cBorder, t.cAccent, anim);
        Draw.rr(g, x, y, w, h, 6, 0xFF000000 | track);
        int kx = x + 2 + Math.round(anim * (w - 12));
        Draw.rr(g, kx, y + 2, 8, 8, 4, 0xFF000000 | Theme.mix(t.cDim, t.cBg, anim));
    }

    /** Horizontal slider track, returns nothing; value fraction 0..1. */
    public static void slider(GuiGraphics g, Theme t, int x, int y, int w, float frac, boolean hover) {
        int h = 4;
        int cy = y + 4;
        Draw.rr(g, x, cy, w, h, 2, 0xFF000000 | t.cBorder);
        int fw = Math.max(2, Math.round(frac * w));
        Draw.rr(g, x, cy, fw, h, 2, 0xFF000000 | t.cAccent);
        int kx = x + Math.round(frac * (w - 6));
        Draw.rr(g, kx, cy - 3, 6, 10, 3, 0xFF000000 | (hover ? t.cAccent2 : t.cText));
    }

    public static float sliderFrac(double mx, int x, int w) {
        return (float) Math.max(0, Math.min(1, (mx - x) / (double) Math.max(1, w)));
    }

    /** Soft-edged card with optional glow when active; hover fades in and out. */
    public static void card(GuiGraphics g, Theme t, int x, int y, int w, int h, boolean hover, float glow) {
        int r = t.radius;
        float hv = Anim.to(Anim.key(5, x, y, w, h), hover ? 1f : 0f, 14f);
        float gl = Math.max(glow, hv * 0.35f);
        if (gl > 0.01f) {
            int a = Math.round(gl * 9);
            if (a > 0) Draw.glow(g, x, y, w, h, r, t.cAccent, a, 4);
        }
        Draw.rr(g, x + 1, y + 2, w, h, r, 0x30000000);
        Draw.rr(g, x, y, w, h, r, Anim.argb(t.aCard, t.aCardHover, hv));
        if (gl > 0.01f) Draw.ring(g, x, y, w, h, r, (Math.round(0x30 + gl * 0x90) << 24) | t.cAccent);
        else Draw.ring(g, x, y, w, h, r, t.aBorder);
    }
}
