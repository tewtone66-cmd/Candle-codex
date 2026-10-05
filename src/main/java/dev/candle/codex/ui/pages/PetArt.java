package dev.candle.codex.ui.pages;

import dev.candle.codex.config.Config;
import dev.candle.codex.cosmetics.Cosmetics;
import dev.candle.codex.cosmetics.Pets;
import dev.candle.codex.ui.Draw;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Arrays;

/**
 * Menu pictures for pets: a small pixel-art player seen from the front, with the pet drawn from the very same boxes as the
 * 3D model (Pets.build), painted as flat rectangles, far boxes first. Animated with the menu animation setting.
 */
final class PetArt {
    private static final int MAX = 256;
    private static final float[] X = new float[MAX], Y = new float[MAX], Z = new float[MAX], HX = new float[MAX], HY = new float[MAX];
    private static final int[] RGB = new int[MAX];
    private static final boolean[] GLOW = new boolean[MAX];
    private static final long[] KEYS = new long[MAX];
    private static final int[] IDX = new int[MAX];
    private static int n;

    private PetArt() {}

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

    /** Pet picture centred on cx, cy inside a 92 x 100 card. */
    static void draw(GuiGraphics g, Cosmetics.Entry e, int cx, int cy) {
        n = 0;
        final boolean orbit = Pets.orbits(e.id());
        // menu animation clock in game ticks (frozen when menu animations are off)
        float t = Config.data.animSpeed > 0f ? (System.currentTimeMillis() % 1_000_000L) / 50f * Math.min(1f, Config.data.animSpeed) : 0f;
        final float ex = orbit ? 0f : 6.3f, ey = orbit ? 0f : -0.3f, ez = orbit ? 0f : 0.5f;
        Pets.build(e, t, (bx, by, bz, hx, hy, hz, rgb, glow) -> {
            if (n >= MAX) return;
            X[n] = bx + ex;
            Y[n] = by + ey;
            Z[n] = bz + ez;
            HX[n] = hx;
            HY[n] = hy;
            RGB[n] = rgb;
            GLOW[n] = glow;
            n++;
        });
        sortFarFirst(); // far (large z) first
        float s;
        int ox, oy;
        if (orbit) {
            s = 3.1f;
            ox = cx;
            oy = cy + Math.round(2f * s);
        } else {
            s = 4.4f;
            ox = cx - Math.round(4.25f * s);
            oy = cy + Math.round(3f * s);
        }
        // butterfly behind the head first, then the head, then everything in front
        if (orbit) paint(g, ox, oy, s, true);
        rect(g, ox, oy, s, -4, -8, 4, 0, opaque(0xC8956A));                  // head (front view)
        rect(g, ox, oy, s, -4, -8, 4, -5.5f, opaque(0x4B3621));              // hair
        rect(g, ox, oy, s, -2.6f, -4.2f, -1.2f, -3.0f, opaque(0x2B2B33));    // eyes
        rect(g, ox, oy, s, 1.2f, -4.2f, 2.6f, -3.0f, opaque(0x2B2B33));
        rect(g, ox, oy, s, -4, 0, 4, 9, opaque(0x4A90D9));                   // torso
        rect(g, ox, oy, s, -8, 0, -4, 9, opaque(0x3A78BC));                  // arms
        rect(g, ox, oy, s, 4, 0, 8, 9, opaque(0x3A78BC));
        if (orbit) paint(g, ox, oy, s, false);
        else {
            paint(g, ox, oy, s, true);
            paint(g, ox, oy, s, false);
        }
    }
}
