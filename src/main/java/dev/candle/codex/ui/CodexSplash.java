package dev.candle.codex.ui;

import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Codex branded start-up screen (replaces the Mojang Studios logo and progress bar of LoadingOverlay).
 * The font is not loaded yet while the game starts, so the wordmark is drawn from a tiny 5x7 pixel font.
 * Used only by LoadingOverlayMixin. If anything here throws, {@link #failed} is set and vanilla takes over again.
 */
public final class CodexSplash {
    /** Set when drawing failed once; the mixin then stops touching the vanilla screen. */
    public static volatile boolean failed;
    /** Logo alpha of the current frame (taken from the vanilla logo draw call). */
    public static float logoAlpha;
    /** True when the vanilla logo was hidden this frame, so the Codex one must be drawn. */
    public static boolean seen;

    private static final int[] C = {0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110};
    private static final int[] A = {0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001};
    private static final int[] N = {0b10001, 0b11001, 0b10101, 0b10101, 0b10011, 0b10001, 0b10001};
    private static final int[] D = {0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110};
    private static final int[] L = {0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111};
    private static final int[] E = {0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111};
    private static final int[] I = {0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110};
    private static final int[] T = {0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100};
    private static final int[] EMPTY = new int[7];

    private CodexSplash() {}

    private static int[] glyph(char ch) {
        return switch (ch) {
            case 'C' -> C;
            case 'A' -> A;
            case 'N' -> N;
            case 'D' -> D;
            case 'L' -> L;
            case 'E' -> E;
            case 'I' -> I;
            case 'T' -> T;
            default -> EMPTY;
        };
    }

    /** Draws a word in the pixel font; unit is the size of one font pixel, gap the space between letters in units. */
    private static void word(GuiGraphics g, String s, int x, int y, int unit, int gap, int topRgb, int bottomRgb) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            int[] rows = glyph(s.charAt(i));
            for (int r = 0; r < 7; r++) {
                int bits = rows[r];
                int col = 0xFF000000 | Theme.mix(topRgb, bottomRgb, r / 6f);
                int c = 0;
                while (c < 5) {
                    if ((bits & (1 << (4 - c))) == 0) {
                        c++;
                        continue;
                    }
                    int start = c;
                    while (c < 5 && (bits & (1 << (4 - c))) != 0) c++;
                    Draw.fill(g, cx + start * unit, y + r * unit, cx + c * unit, y + (r + 1) * unit, col);
                }
            }
            cx += (5 + gap) * unit;
        }
    }

    private static int wordWidth(String s, int unit, int gap) {
        return (s.length() * 5 + (s.length() - 1) * gap) * unit;
    }

    /** Codex icon in a local space (flame at y 2..16, wick, wax down to y 36) around x = 0. */
    private static void icon(GuiGraphics g, Theme t, long time) {
        float flick = (float) (Math.sin(time / 140.0) * 0.8 + Math.sin(time / 57.0) * 0.4);
        int fx = Math.round(flick);
        int grow = Math.round((float) Math.sin(time / 230.0));
        Draw.glow(g, -5, 2, 10, 14, 5, t.cAccent, 12, 6);
        Draw.rr(g, fx - 4, 2 - grow, 8, 14 + grow, 4, 0xFF000000 | t.cAccent);
        Draw.rr(g, fx - 2, 7, 4, 9, 2, 0xFF000000 | t.cAccent2);
        Draw.fill(g, 0, 16, 1, 20, 0xFF000000 | t.cDim);
        Draw.rr(g, -5, 20, 10, 16, 2, 0xFF000000 | t.cText);
        Draw.rr(g, -5, 20, 10, 3, 2, 0xFF000000 | Theme.mix(t.cText, t.cAccent2, 0.4f));
    }

    /** The logo block: candle, CANDLE and CLIENT, centred like the vanilla logo. */
    public static void logo(GuiGraphics g, int w, int h, float alpha) {
        if (alpha <= 0.02f) return;
        Theme t = ThemeManager.current();
        float prev = Draw.mul;
        try {
            Draw.mul = Math.min(1f, alpha);
            long time = System.currentTimeMillis();
            double logoHeight = Math.min(w * 0.75, h) * 0.25;
            int contentW = (int) (logoHeight * 4);
            int u = Math.max(1, (int) (contentW * 0.5 / 35));
            int u2 = Math.max(1, u / 3);
            float k = Math.max(1f, u * 0.35f);
            int iconH = Math.round(38 * k);
            int textH = 7 * u, cliH = 7 * u2;
            int gap1 = Math.max(4, u * 2), gap2 = Math.max(3, u);
            int total = iconH + gap1 + textH + gap2 + cliH;
            int top = h / 2 - total / 2;
            int cx = w / 2;

            // rising embers around the candle
            for (int i = 0; i < 14; i++) {
                long seed = i * 7919L + 13;
                float life = ((time + seed * 37) % 2600) / 2600f;
                float ex = cx + (float) Math.sin(time / 700.0 + i) * 6f * k + ((seed % 21) - 10) * k * 0.6f;
                float ey = top + 4 * k - life * iconH * 1.1f;
                int a = Math.round((1f - life) * 170);
                int sz = Math.max(1, Math.round(k / 2f));
                Draw.fill(g, Math.round(ex), Math.round(ey), Math.round(ex) + sz, Math.round(ey) + sz,
                        (a << 24) | (i % 3 == 0 ? t.cAccent2 : t.cAccent));
            }

            g.pose().pushMatrix();
            g.pose().translate((float) cx, (float) top);
            g.pose().scale(k, k);
            icon(g, t, time);
            g.pose().popMatrix();

            int wy = top + iconH + gap1;
            int ww = wordWidth("CANDLE", u, 1);
            int wx = cx - ww / 2;
            int sh = Math.max(1, u / 2);
            word(g, "CANDLE", wx + sh, wy + sh, u, 1, 0x000000, 0x000000); // shadow
            word(g, "CANDLE", wx, wy, u, 1, t.cAccent2, t.cAccent);

            int cy = wy + textH + gap2;
            int cw = wordWidth("CLIENT", u2, 2);
            word(g, "CLIENT", cx - cw / 2, cy, u2, 2, t.cDim, t.cDim);
        } finally {
            Draw.mul = prev;
        }
    }

    /** Progress bar in the place of the vanilla one: thin track, accent fill and a small flame on its head. */
    public static void bar(GuiGraphics g, int x0, int y0, int x1, int y1, float progress, float fade) {
        if (fade <= 0.02f) return;
        Theme t = ThemeManager.current();
        float prev = Draw.mul;
        try {
            Draw.mul = Math.min(1f, fade);
            long time = System.currentTimeMillis();
            int w = x1 - x0;
            if (w < 8) return;
            int th = Math.max(3, w / 110);
            int ty = (y0 + y1) / 2 - th / 2;
            int r = th / 2;
            Draw.rr(g, x0, ty, w, th, r, (0x80 << 24) | t.cBorder);
            Draw.ring(g, x0 - 1, ty - 1, w + 2, th + 2, r + 1, (0x50 << 24) | t.cDim);
            int fw = Math.round(Math.max(0f, Math.min(1f, progress)) * w);
            if (fw >= 2) {
                Draw.rr(g, x0, ty, fw, th, r, 0xFF000000 | t.cAccent);
                int hw = Math.min(fw, th * 4);
                Draw.rr(g, x0 + fw - hw, ty, hw, th, r, 0xFF000000 | t.cAccent2);
            }
            // flame on the head of the bar
            int fs = Math.max(2, th);
            float fl = (float) (Math.sin(time / 90.0) * 0.5 + Math.sin(time / 37.0) * 0.3);
            int fh = Math.max(fs * 2, Math.round(fs * (3.2f + fl)));
            int hx = x0 + fw;
            int fy = ty - fh + 1;
            Draw.glow(g, hx - fs, fy, 2 * fs, fh, fs, t.cAccent, 8, 3);
            Draw.rr(g, hx - fs, fy, 2 * fs, fh, fs, 0xFF000000 | t.cAccent);
            Draw.rr(g, hx - fs / 2, fy + fh / 3, Math.max(1, fs), fh - fh / 3, Math.max(1, fs / 2), 0xFF000000 | t.cAccent2);
        } finally {
            Draw.mul = prev;
        }
    }
}
