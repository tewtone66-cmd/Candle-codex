package dev.candle.codex.ui.pages;

import dev.candle.codex.config.Config;
import dev.candle.codex.cosmetics.Auras;
import dev.candle.codex.cosmetics.Cosmetics;
import dev.candle.codex.ui.Draw;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Arrays;

/** Menu pictures for auras: a small pixel-art player from the front with the same particles as the 3D aura around it. */
final class AuraArt {
    private static final int MAX = 256;
    private static final float[] X = new float[MAX], Y = new float[MAX], Z = new float[MAX], HX = new float[MAX], HY = new float[MAX];
    private static final int[] RGB = new int[MAX];
    private static final boolean[] GLOW = new boolean[MAX];
    private static final long[] KEYS = new long[MAX];
    private static final int[] IDX = new int[MAX];
    private static int n;

    private AuraArt() {}

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int shade(int c, float f) {
        return opaque((Math.min(255, Math.round(((c >> 16) & 255) * f)) << 16)
                | (Math.min(255, Math.round(((c >> 8) & 255) * f)) << 8)
                | Math.min(255, Math.round((c & 255) * f)));
    }

    private static void rect(GuiGraphics g, int ox, int oy, float s, float x0, float y0, float x1, float y1, int argb) {
        int a = ox + Math.round(x0 * s), b = oy + Math.round(y0 * s);
        int c = ox + Math.round(x1 * s), d = oy + Math.round(y1 * s);
        if (c <= a) c = a + 1;
        if (d <= b) d = b + 1;
        Draw.fill(g, a, b, c, d, argb);
    }

    /** Orders IDX so that the boxes with the largest z (far from the viewer) come first; no objects are created. */
    private static void sortFarFirst() {
        for (int i = 0; i < n; i++) KEYS[i] = ((long) Math.round((64f - Z[i]) * 64f) << 32) | i;
        Arrays.sort(KEYS, 0, n);
        for (int i = 0; i < n; i++) IDX[i] = (int) (KEYS[i] & 0xFFFFFFFFL);
    }

    private static void paint(GuiGraphics g, int ox, int oy, float s, boolean back) {
        for (int k = 0; k < n; k++) {
            int i = IDX[k];
            if ((Z[i] > 0f) != back) continue;
            int col = RGB[i];
            rect(g, ox, oy, s, X[i] - HX[i], Y[i] - HY[i], X[i] + HX[i], Y[i] + HY[i], GLOW[i] ? opaque(col) : shade(col, 0.95f));
        }
    }

    /** Picture centred on cx, cy inside a 92 x 100 card. */
    static void draw(GuiGraphics g, Cosmetics.Entry e, int cx, int cy) {
        n = 0;
        float t = Config.data.animSpeed > 0f ? (System.currentTimeMillis() % 1_000_000L) / 50f * Math.min(1f, Config.data.animSpeed) : 0f;
        Auras.build(e, t, (bx, by, bz, hx, hy, hz, rgb, glow) -> {
            if (n >= MAX) return;
            X[n] = bx;
            Y[n] = by;
            Z[n] = bz;
            HX[n] = hx;
            HY[n] = hy;
            RGB[n] = rgb;
            GLOW[n] = glow;
            n++;
        });
        sortFarFirst();
        float s = 2.5f;
        int ox = cx, oy = cy - Math.round(10f * s);
        paint(g, ox, oy, s, true);                                              // behind the player
        rect(g, ox, oy, s, -4, -8, 4, 0, opaque(0xC8956A));                    // head
        rect(g, ox, oy, s, -4, -8, 4, -5.5f, opaque(0x4B3621));
        rect(g, ox, oy, s, -2.6f, -4.2f, -1.2f, -3.0f, opaque(0x2B2B33));
        rect(g, ox, oy, s, 1.2f, -4.2f, 2.6f, -3.0f, opaque(0x2B2B33));
        rect(g, ox, oy, s, -4, 0, 4, 12, opaque(0x4A90D9));                    // torso
        rect(g, ox, oy, s, -8, 0, -4, 12, opaque(0x3A78BC));                   // arms
        rect(g, ox, oy, s, 4, 0, 8, 12, opaque(0x3A78BC));
        rect(g, ox, oy, s, -4, 12, 0, 24, opaque(0x2E3E70));                   // legs
        rect(g, ox, oy, s, 0, 12, 4, 24, opaque(0x2A3866));
        paint(g, ox, oy, s, false);                                             // in front
    }
}
