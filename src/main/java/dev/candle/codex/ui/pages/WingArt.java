package dev.candle.codex.ui.pages;

import dev.candle.codex.config.Config;
import dev.candle.codex.cosmetics.CosmeticLayer;
import dev.candle.codex.cosmetics.Cosmetics;
import dev.candle.codex.ui.Draw;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Menu pictures for wings: a small pixel-art player seen from behind, with both wings open. The wings are not drawn by
 * hand: CosmeticLayer.previewWing builds the very same boxes as the 3D model and they are painted here as flat rectangles
 * (far boxes first), so the picture always matches the game and uses the same c1 / c2 colours.
 * The dragon wings flap in the menu with the same speed setting as in the game; the other wings are cached.
 */
final class WingArt {
    /** Screen pixels per model pixel are chosen so that both wings fit into this width. */
    private static final float FIT_W = 84f;
    private static final float MAX_SCALE = 1.6f;
    private static final float TICK_MS = 50f;

    /** Boxes of one wing, sorted back to front once finished. */
    private static final class Buf implements CosmeticLayer.WingSink {
        float[] x = new float[256], y = new float[256], z = new float[256], hx = new float[256], hy = new float[256];
        int[] rgb = new int[256];
        boolean[] glow = new boolean[256];
        int n;
        int[] order = new int[0];
        long[] keys = new long[0];
        float maxX;

        @Override
        public void box(float cx, float cy, float cz, float hxv, float hyv, int c, boolean g) {
            if (n == x.length) {
                int m = n * 2;
                x = Arrays.copyOf(x, m);
                y = Arrays.copyOf(y, m);
                z = Arrays.copyOf(z, m);
                hx = Arrays.copyOf(hx, m);
                hy = Arrays.copyOf(hy, m);
                rgb = Arrays.copyOf(rgb, m);
                glow = Arrays.copyOf(glow, m);
            }
            x[n] = cx;
            y[n] = cy;
            z[n] = cz;
            hx[n] = hxv;
            hy[n] = hyv;
            rgb[n] = c;
            glow[n] = g;
            n++;
            maxX = Math.max(maxX, Math.abs(cx) + hxv);
        }

        void finish() {
            if (keys.length < n) keys = new long[x.length];
            for (int i = 0; i < n; i++) {
                // z is small and may be negative: shift it into a positive range so the bits sort like the numbers
                int zi = Math.round((z[i] + 64f) * 64f);
                keys[i] = ((long) zi << 32) | i;
            }
            Arrays.sort(keys, 0, n);
            if (order.length != n) order = new int[n];
            for (int i = 0; i < n; i++) order[i] = (int) (keys[i] & 0xFFFFFFFFL);
        }

        void clear() {
            n = 0;
            maxX = 0f;
        }
    }

    private static final Map<String, Buf> CACHE = new HashMap<>();
    private static final Buf LIVE = new Buf();
    private static float dragonPhase;
    private static long lastNs;
    private static long lastBuildMs;

    private WingArt() {}

    private static Buf build(Cosmetics.Entry e, float phase, Buf into) {
        Buf b = into != null ? into : new Buf();
        b.clear();
        // both wings are built with the shoulder offset of the 3D model (1.4 px outwards, 1.8 px down)
        CosmeticLayer.previewWing(e, 1, 0f, phase, b);
        CosmeticLayer.previewWing(e, -1, 0f, phase, b);
        b.finish();
        return b;
    }

    /** Dragon flap clock: advances in game ticks (50 ms) times the speed setting; frozen when menu animations are off. */
    private static float dragonClock() {
        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0f : (now - lastNs) / 1_000_000f / TICK_MS;
        lastNs = now;
        if (!(dt >= 0f && dt < 5f)) dt = 0f;
        float speed = Config.data.cosmetics.dragonSpeed;
        if (!(speed >= 0.2f && speed <= 3f)) speed = 1f;
        if (Config.data.animSpeed > 0f) dragonPhase += dt * speed;
        if (dragonPhase > 1000f) dragonPhase -= 392.699f; // ten full periods of sin(phase * 0.16)
        return dragonPhase;
    }

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

    /** Player seen from behind (model pixels, y = 0 at the shoulders), then the wings in front of it. Centred on cx, cy. */
    static void draw(GuiGraphics g, Cosmetics.Entry e, int cx, int cy, boolean live) {
        boolean dragon = "dragon".equals(e.id());
        Buf buf;
        if (dragon && live) {
            float ph = dragonClock();
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastBuildMs >= 33L || LIVE.n == 0) {
                lastBuildMs = nowMs;
                build(e, ph, LIVE);
            }
            buf = LIVE;
        } else if (dragon) {
            buf = CACHE.get("dragon");
            if (buf == null) {
                buf = build(e, 6f, null);
                CACHE.put("dragon", buf);
            }
        } else {
            buf = CACHE.get(e.id());
            if (buf == null) {
                buf = build(e, 0f, null);
                CACHE.put(e.id(), buf);
            }
        }
        float s = Math.min(MAX_SCALE, FIT_W / (2f * (Math.max(24f, buf.maxX) + 1.4f)));
        int ox = cx, oy = cy - Math.round(4.5f * s);

        // body from behind: hair, torso, arms, legs
        rect(g, ox, oy, s, -4, -8, 4, 0, opaque(0x4B3621));
        rect(g, ox, oy, s, -4, -2.5f, 4, 0, shade(0x4B3621, 0.8f));
        rect(g, ox, oy, s, -4, 0, 4, 12, opaque(0x4A90D9));
        rect(g, ox, oy, s, -8, 0, -4, 12, opaque(0x3A78BC));
        rect(g, ox, oy, s, 4, 0, 8, 12, opaque(0x3A78BC));
        rect(g, ox, oy, s, -8, 11, -4, 12, opaque(0xC8956A));
        rect(g, ox, oy, s, 4, 11, 8, 12, opaque(0xC8956A));
        rect(g, ox, oy, s, -4, 12, 0, 24, opaque(0x2E3E70));
        rect(g, ox, oy, s, 0, 12, 4, 24, opaque(0x2A3866));

        for (int k = 0; k < buf.n; k++) {
            int i = buf.order[k];
            float px = buf.x[i] + (buf.x[i] >= 0f ? 1.4f : -1.4f), py = buf.y[i] + 1.8f;
            int col = buf.rgb[i];
            int argb = buf.glow[i] ? opaque(col) : shade(col, 0.92f);
            rect(g, ox, oy, s, px - buf.hx[i], py - buf.hy[i], px + buf.hx[i], py + buf.hy[i], argb);
        }
    }
}
