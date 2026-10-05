package dev.candle.codex.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small immediate-mode drawing helpers. All menu drawing goes through here so fade animations just work. */
public final class Draw {
    /** Global alpha multiplier used by menu open/close animation. */
    public static float mul = 1f;

    /** While true, every draw call records the lowest y it touched (used to size the auto scroll of menu pages). */
    public static boolean track;
    public static int maxY = Integer.MIN_VALUE;

    /**
     * Page reveal wave. While reveal is true, everything a page draws fades in as a soft diagonal wave sweeps over it,
     * which gives every page a staggered entrance without the page knowing about it. MenuScreen drives the fields.
     */
    public static boolean reveal;
    public static float revealAge;
    public static int revealX, revealY;
    private static final float WAVE_SPEED = 1100f, WAVE_SOFT = 80f;

    /**
     * Text guard. While clipRight is set, text that would run past it (or past clipLeft for right/centre aligned text)
     * is shortened with "..." so nothing can leave the page, whatever the GUI size.
     */
    public static int clipLeft = -100000, clipRight = Integer.MAX_VALUE;

    private static final int[][] INSETS = new int[17][];

    private Draw() {}

    /** Plays the reveal wave again (for example after switching a category). */
    public static void replayReveal() {
        revealAge = 0f;
    }

    private static void seen(int y) {
        if (track && y > maxY) maxY = y;
    }

    /** 0..1 visibility of something drawn at x, y while the reveal wave is running. */
    private static float rv(int x, int y) {
        if (!reveal) return 1f;
        float d = (y - revealY) + (x - revealX) * 0.35f;
        float k = (revealAge * WAVE_SPEED - d) / WAVE_SOFT;
        return k <= 0f ? 0f : Math.min(1f, k);
    }

    public static int c(int argb) {
        if (mul >= 0.999f) return argb;
        int orig = (argb >>> 24) & 255;
        int a = Math.round(orig * mul);
        if (orig > 0 && a < 5) a = 5; // alpha 0 can be treated as opaque by text rendering
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Like c() but also scales alpha by f (reveal factor). */
    private static int cf(int argb, float f) {
        if (f >= 0.999f) return c(argb);
        int orig = (argb >>> 24) & 255;
        int a = Math.round(orig * mul * f);
        if (orig > 0 && a < 5) a = 5;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int rgb(int rgb) {
        return c(0xFF000000 | rgb);
    }

    public static int argb(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static int[] insets(int r) {
        int[] a = INSETS[r];
        if (a == null) {
            a = new int[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                a[i] = (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
            }
            INSETS[r] = a;
        }
        return a;
    }

    public static void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int argb) {
        seen(y2);
        float f = rv(x1, y1);
        if (f <= 0.01f) return;
        g.fill(x1, y1, x2, y2, cf(argb, f));
    }

    public static void rr(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
        if (w <= 0 || h <= 0) return;
        seen(y + h);
        float f = rv(x, y);
        if (f <= 0.01f) return;
        int col = cf(argb, f);
        r = Math.max(0, Math.min(16, Math.min(r, Math.min(w, h) / 2)));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, col);
            return;
        }
        int[] in = insets(r);
        g.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            int o = in[i];
            g.fill(x + o, y + i, x + w - o, y + i + 1, col);
            g.fill(x + o, y + h - 1 - i, x + w - o, y + h - i, col);
        }
    }

    /** 1px rounded outline. */
    public static void ring(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
        if (w <= 2 || h <= 2) return;
        seen(y + h);
        float f = rv(x, y);
        if (f <= 0.01f) return;
        int col = cf(argb, f);
        r = Math.max(0, Math.min(16, Math.min(r, Math.min(w, h) / 2)));
        if (r == 0) {
            g.fill(x, y, x + w, y + 1, col);
            g.fill(x, y + h - 1, x + w, y + h, col);
            g.fill(x, y + 1, x + 1, y + h - 1, col);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
            return;
        }
        int[] in = insets(r);
        g.fill(x + in[0], y, x + w - in[0], y + 1, col);
        g.fill(x + in[0], y + h - 1, x + w - in[0], y + h, col);
        for (int i = 1; i < r; i++) {
            int a = in[i];
            int b = Math.max(in[i] + 1, in[i - 1]);
            g.fill(x + a, y + i, x + b, y + i + 1, col);
            g.fill(x + w - b, y + i, x + w - a, y + i + 1, col);
            g.fill(x + a, y + h - 1 - i, x + b, y + h - i, col);
            g.fill(x + w - b, y + h - 1 - i, x + w - a, y + h - i, col);
        }
        g.fill(x, y + r, x + 1, y + h - r, col);
        g.fill(x + w - 1, y + r, x + w, y + h - r, col);
    }

    /** Soft glow behind a rounded rect: stacked translucent expansions. */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int rgb, int alphaPerLayer, int layers) {
        for (int k = layers; k >= 1; k--) {
            rr(g, x - k, y - k, w + 2 * k, h + 2 * k, r + k, (alphaPerLayer << 24) | (rgb & 0xFFFFFF));
        }
    }

    public static void vGradient(GuiGraphics g, int x, int y, int w, int h, int topArgb, int bottomArgb) {
        seen(y + h);
        float f = rv(x, y);
        if (f <= 0.01f) return;
        g.fillGradient(x, y, x + w, y + h, cf(topArgb, f), cf(bottomArgb, f));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    // ---- text (with the clip guard) ----

    /** Shortens s so it fits between x and clipRight; null when there is no room at all. */
    private static String guardL(String s, int x) {
        if (clipRight == Integer.MAX_VALUE) return s;
        int avail = clipRight - x;
        if (avail < 14) return null;
        return font().width(s) <= avail ? s : fit(s, avail);
    }

    private static String guardR(String s, int rx) {
        if (clipRight == Integer.MAX_VALUE) return s;
        int avail = Math.min(rx, clipRight) - clipLeft;
        if (avail < 14) return null;
        return font().width(s) <= avail ? s : fit(s, avail);
    }

    private static String guardC(String s, int cx) {
        if (clipRight == Integer.MAX_VALUE) return s;
        int avail = 2 * Math.min(cx - clipLeft, clipRight - cx);
        if (avail < 14) return null;
        return font().width(s) <= avail ? s : fit(s, avail);
    }

    public static void text(GuiGraphics g, String s, int x, int y, int rgb) {
        seen(y + 9);
        float f = rv(x, y);
        if (f <= 0.04f) return;
        s = guardL(s, x);
        if (s == null) return;
        g.drawString(font(), s, x, y, cf(0xFF000000 | rgb, f), false);
    }

    public static void textShadow(GuiGraphics g, String s, int x, int y, int rgb) {
        seen(y + 9);
        float f = rv(x, y);
        if (f <= 0.04f) return;
        s = guardL(s, x);
        if (s == null) return;
        g.drawString(font(), s, x, y, cf(0xFF000000 | rgb, f), true);
    }

    public static void textC(GuiGraphics g, String s, int cx, int y, int rgb) {
        seen(y + 9);
        float f = rv(cx, y);
        if (f <= 0.04f) return;
        s = guardC(s, cx);
        if (s == null) return;
        Font ft = font();
        g.drawString(ft, s, cx - ft.width(s) / 2, y, cf(0xFF000000 | rgb, f), false);
    }

    public static void textR(GuiGraphics g, String s, int rx, int y, int rgb) {
        seen(y + 9);
        float f = rv(rx, y);
        if (f <= 0.04f) return;
        s = guardR(s, rx);
        if (s == null) return;
        Font ft = font();
        g.drawString(ft, s, rx - ft.width(s), y, cf(0xFF000000 | rgb, f), false);
    }

    /** Trims a string with an ellipsis so it fits in maxW pixels. */
    public static String fit(String s, int maxW) {
        Font f = font();
        if (f.width(s) <= maxW) return s;
        return f.plainSubstrByWidth(s, Math.max(0, maxW - f.width("..."))) + "...";
    }

    // ---- colour helpers ----

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float r, g, b;
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    public static void toHsv(int rgb, float[] out) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }
}
