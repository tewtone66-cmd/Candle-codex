package dev.candle.codex.cosmetics;

import java.util.List;

/**
 * Auras: soft, layered effects that move around your whole body. Pure geometry (boxes sent to a Pets.Sink), so the 3D
 * model (AuraRender) and the menu picture (AuraArt) show the same thing. Nothing is allocated per frame. To avoid the
 * blocky look every effect uses many small boxes with smooth colour gradients, trails and eased motion.
 * Space: player model space in pixels, +Y down, head top y = -8, feet y = 24, back = +Z. Every aura stays under 120 boxes.
 */
public final class Auras {
    /** c1 / c2 = the two colours of the effect (see each method). */
    public static final List<Cosmetics.Entry> LIST = List.of(
            new Cosmetics.Entry("none", "No aura", 0x555555, 0x333333),
            new Cosmetics.Entry("fire", "Fire", 0xFF6A1A, 0xFFD84A),
            new Cosmetics.Entry("star", "Stars", 0xFFE066, 0xFFFFFF),
            new Cosmetics.Entry("heart", "Hearts", 0xFF4F8B, 0xFFB3CE),
            new Cosmetics.Entry("lightning", "Lightning", 0x7AD7FF, 0xFFFFFF),
            new Cosmetics.Entry("magic", "Magic", 0xA070FF, 0xFF8AE6),
            new Cosmetics.Entry("smoke", "Smoke", 0x5A5A66, 0xB8B8C4),
            Glitchy.AURA);

    private static final float TAU = 6.2831855f;

    private Auras() {}

    public static boolean animated(String id) {
        return !"none".equals(id);
    }

    public static void build(Cosmetics.Entry e, float t, Pets.Sink s) {
        switch (e.id()) {
            case "fire" -> fire(e, t, s);
            case "star" -> star(e, t, s);
            case "heart" -> heart(e, t, s);
            case "lightning" -> lightning(e, t, s);
            case "magic" -> magic(e, t, s);
            case "smoke" -> smoke(e, t, s);
            case "glitchrain" -> Glitchy.aura(e, t, s);
            default -> { }
        }
    }

    /** Stable pseudo random number 0..1 for an integer. */
    private static float rnd(int n) {
        n ^= n >>> 16;
        n *= 0x7feb352d;
        n ^= n >>> 15;
        n *= 0x846ca68b;
        n ^= n >>> 16;
        return (n >>> 8) / 16777216f;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float sin(float v) {
        return (float) Math.sin(v);
    }

    private static float cos(float v) {
        return (float) Math.cos(v);
    }

    // ---- fire: 8 flame tongues stand in a ring around the body (dark red base, orange middle, yellow tips) and flicker; sparks rise

    private static void fire(Cosmetics.Entry e, float t, Pets.Sink s) {
        final int deep = 0xB81E0A;
        for (int i = 0; i < 8; i++) {
            float ang = i * TAU / 8f + t * 0.02f;
            float ca = cos(ang), sa = sin(ang);
            float h = 13f + 7f * rnd(i + 3) + 2f * sin(t * 0.17f + i * 1.7f);   // height of this tongue
            float rad = 6.3f + 0.6f * rnd(i + 20);
            for (int j = 0; j < 5; j++) {
                float f = j / 4f;
                float sway = sin(t * 0.21f + i * 1.3f + f * 3f) * 1.1f * f;
                float r = rad * (1f - 0.18f * f) + sway * 0.3f;
                float x = ca * r - sa * sway, z = sa * r + ca * sway;
                float y = 22.5f - f * h;
                float wd = 1.45f * (float) Math.pow(1f - f, 0.8f) + 0.22f;
                int col = f < 0.5f ? Pets.mix(deep, e.c1(), f / 0.5f) : Pets.mix(e.c1(), e.c2(), (f - 0.5f) / 0.5f);
                s.box(x, y, z, wd, h / 8f + 0.3f, wd, col, true);
            }
        }
        for (int i = 0; i < 8; i++) {                                           // tiny sparks rising off the flames
            float life = frac(t * 0.022f + i / 8f + rnd(i) * 0.1f);
            float ang = rnd(i + 50) * TAU + t * 0.03f;
            float rad = (5.2f + rnd(i + 90) * 2.5f) * (1f - 0.35f * life);
            float sz = 0.45f * (1f - life) + 0.1f;
            s.box(cos(ang) * rad, 20f - life * 30f, sin(ang) * rad, sz, sz, sz, Pets.mix(e.c2(), e.c1(), life), true);
        }
    }

    // ---- stars: 6 four-point stars orbit at different heights and twinkle; each drags a short fading trail

    private static void star(Cosmetics.Entry e, float t, Pets.Sink s) {
        for (int i = 0; i < 6; i++) {
            float dir = (i & 1) == 0 ? 1f : -1f;
            float rad = 8f + (i % 3) * 1.2f;
            float yb = 6f + sin(t * 0.07f + i * 1.3f) * 10f;
            for (int k = 3; k >= 0; k--) {                                     // k = 0 is the star, 1..3 the trail behind it
                float ang = t * 0.06f * dir + i * 1.047f - dir * 0.13f * k;
                float x = cos(ang) * rad, z = sin(ang) * rad;
                float y = yb + k * 0.35f;
                if (k > 0) {
                    float u = 0.32f - 0.07f * k;
                    s.box(x, y, z, u, u, u, Pets.mix(e.c1(), 0x3A2A10, k / 4f), true);
                    continue;
                }
                float u = 0.3f + 0.55f * (0.6f + 0.4f * sin(t * 0.2f + i * 2f));
                s.box(x, y, z, u, u, 0.35f, e.c2(), true);
                s.box(x - 2f * u, y, z, 0.9f * u, 0.3f * u, 0.28f, e.c1(), true);
                s.box(x + 2f * u, y, z, 0.9f * u, 0.3f * u, 0.28f, e.c1(), true);
                s.box(x, y - 2f * u, z, 0.3f * u, 0.9f * u, 0.28f, e.c1(), true);
                s.box(x, y + 2f * u, z, 0.3f * u, 0.9f * u, 0.28f, e.c1(), true);
            }
        }
    }

    // ---- hearts: 6 small hearts (7 x 6 cells, drawn as row runs so there are no gaps) float up, sway and fade out

    /** First and last cell of every row of the heart. */
    private static final int[][] HEART_ROWS = {{1, 2}, {4, 5}, {0, 6}, {0, 6}, {1, 5}, {2, 4}, {3, 3}};

    private static void heart(Cosmetics.Entry e, float t, Pets.Sink s) {
        final float cell = 0.42f;
        for (int i = 0; i < 6; i++) {
            float life = frac(t * 0.017f + i / 6f + rnd(i) * 0.08f);
            float ang = i * 1.05f + rnd(i + 7) * 1.5f;
            float x = cos(ang) * (5.6f + rnd(i + 11) * 1.4f) + sin(t * 0.1f + i) * 1.2f;
            float z = sin(ang) * 6f;
            float y = 24f - life * 32f;
            float k = life < 0.8f ? 1f : (1f - life) / 0.2f;                    // shrinks away at the top
            float c = cell * (0.4f + 0.6f * k) + 0.02f;
            int col = Pets.mix(e.c1(), e.c2(), life * 0.7f);
            for (int r = 0; r < HEART_ROWS.length; r++) {
                int a = HEART_ROWS[r][0], b = HEART_ROWS[r][1];
                float cx = x + ((a + b) / 2f - 3f) * c * 2f;
                int cc = r == 0 ? Pets.mix(col, 0xFFFFFF, 0.35f) : col;
                s.box(cx, y + (r - 3f) * c * 2f, z, (b - a + 1) * c, c, 0.4f, cc, true);
            }
        }
    }

    // ---- lightning: up to 5 jagged bolts flicker around the body; each has a bright core and a coloured glow

    private static void lightning(Cosmetics.Entry e, float t, Pets.Sink s) {
        int slot = (int) (t * 0.45f);
        for (int i = 0; i < 5; i++) {
            if (rnd(i * 31 + slot * 7) < 0.25f) continue;
            float ang = rnd(i + slot * 13) * TAU;
            float x = cos(ang) * 5.8f, z = sin(ang) * 5.8f;
            float y = rnd(i * 17 + slot * 5) * 20f - 6f;
            for (int k = 0; k < 6; k++) {
                float nx = x + (rnd(i * 5 + k + slot * 3) - 0.5f) * 3.0f;
                float nz = z + (rnd(i * 9 + k + slot * 11) - 0.5f) * 3.0f;
                float len = 1.3f;
                s.box((x + nx) / 2f, y + len, (z + nz) / 2f, 0.38f, len + 0.15f, 0.38f, e.c1(), true);       // glow
                s.box((x + nx) / 2f, y + len, (z + nz) / 2f, 0.16f, len + 0.1f, 0.16f, e.c2(), true);        // white core
                x = nx;
                z = nz;
                y += len * 2f;
            }
        }
    }

    // ---- magic: a helix of 20 orbs with growing size, a rune ring at the feet and a counter-rotating ring over the head

    private static void magic(Cosmetics.Entry e, float t, Pets.Sink s) {
        for (int i = 0; i < 20; i++) {
            float f = frac(t * 0.012f + i / 20f);
            float ang = i * 1.1f + f * 9f + t * 0.05f;
            float rad = 6f + sin(f * 3.14159f);
            float sz = 0.3f + 0.45f * sin(f * 3.14159f);
            s.box(cos(ang) * rad, 24f - f * 32f, sin(ang) * rad, sz, sz, sz, Pets.mix(e.c1(), e.c2(), f), true);
        }
        for (int k = 0; k < 12; k++) {
            float ang = t * 0.04f + k * TAU / 12f;
            s.box(cos(ang) * 7f, 23.6f, sin(ang) * 7f, 0.5f, 0.12f, 0.5f, (k & 1) == 0 ? e.c1() : e.c2(), true);
        }
        for (int k = 0; k < 10; k++) {
            float ang = -t * 0.05f + k * TAU / 10f;
            float y = -11f + sin(t * 0.08f) * 0.6f;
            s.box(cos(ang) * 5.5f, y, sin(ang) * 5.5f, 0.38f, 0.1f, 0.38f, Pets.mix(e.c2(), 0xFFFFFF, 0.35f * (k & 1)), true);
        }
    }

    // ---- smoke: 14 puffs rise from the feet, swirl, grow and get lighter (not glowing)

    private static void smoke(Cosmetics.Entry e, float t, Pets.Sink s) {
        for (int i = 0; i < 14; i++) {
            float life = frac(t * 0.013f + i / 14f + rnd(i) * 0.1f);
            float a = i * 2.4f + life * 1.5f;
            float rad = 4.5f + life * 2.8f;
            float x = cos(a) * rad + sin(t * 0.05f + i * 2f) * (1f + life * 2f);
            float z = sin(a) * rad;
            float sz = 0.6f + life * 1.7f;
            float fade = life > 0.75f ? 1f - (life - 0.75f) * 2.4f : 1f;       // thins out at the top
            s.box(x, 23f - life * 30f, z, sz * fade + 0.1f, sz * fade + 0.1f, sz * fade + 0.1f, Pets.mix(e.c1(), e.c2(), life), false);
        }
    }
}
