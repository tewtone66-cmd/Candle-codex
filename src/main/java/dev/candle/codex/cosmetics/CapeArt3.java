package dev.candle.codex.cosmetics;

/**
 * Pixel-art capes: a red torii gate under a cherry tree with falling petals, by day (Blossom Gate) and by night
 * (Moon Gate, with lanterns and fireflies). One 40 x 64 face per frame; every motion repeats exactly after
 * AnimCapes.FRAMES frames, so the loop has no jump.
 */
final class CapeArt3 {
    private static final int W = 40, H = 64;
    private static final float TAU = AnimCapes.TAU;

    private CapeArt3() {}

    static int[] face(int f, boolean night) {
        float t = f / (float) AnimCapes.FRAMES;
        int[] p = new int[W * H];
        sky(p, night);
        mountains(p, night);
        ground(p, night);
        torii(p, t, night);
        tree(p, t, night);
        if (night) fireflies(p, t);
        petals(p, t, night);
        return p;
    }

    // ------------------------------------------------------------------ helpers

    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    private static int mix(int a, int b, float t) {
        return AnimCapes.mix(a, b, t);
    }

    private static int shade(int c, float f) {
        return AnimCapes.shade(c, f);
    }

    private static void put(int[] p, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < W && y < H) p[y * W + x] = c;
    }

    /** Filled rectangle, x0..x1-1 and y0..y1-1. */
    private static void rect(int[] p, int x0, int y0, int x1, int y1, int c) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) put(p, x, y, c);
        }
    }

    /** Thick stepped line. */
    private static void line(int[] p, int x0, int y0, int x1, int y1, int th, int c) {
        int n = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= n; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) n);
            int y = y0 + Math.round((y1 - y0) * i / (float) n);
            rect(p, x, y, x + th, y + th, c);
        }
    }

    /** Soft round glow: blends col into the pixels around (cx, cy). */
    private static void glow(int[] p, float cx, float cy, float r, int col, float strength) {
        int x0 = Math.max(0, (int) (cx - r)), x1 = Math.min(W - 1, (int) (cx + r));
        int y0 = Math.max(0, (int) (cy - r)), y1 = Math.min(H - 1, (int) (cy + r));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= r) continue;
                float k = 1f - d / r;
                p[y * W + x] = mix(p[y * W + x], col, strength * k * k);
            }
        }
    }

    // ------------------------------------------------------------------ scenery

    private static void sky(int[] p, boolean night) {
        int top = night ? 0xFF0A0E2E : 0xFF5C8EDC;
        int mid = night ? 0xFF3A2F7A : 0xFFB7B4F0;
        int bot = night ? 0xFF8F4A86 : 0xFFFFC9B0;
        for (int y = 0; y < H; y++) {
            float k = Math.min(1f, y / 42f);
            int c = k < 0.55f ? mix(top, mid, k / 0.55f) : mix(mid, bot, (k - 0.55f) / 0.45f);
            for (int x = 0; x < W; x++) p[y * W + x] = c;
        }
        float cx = 29f, cy = 23f;
        for (int y = 6; y < 42; y++) {
            for (int x = 10; x < W; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int i = y * W + x;
                if (night) {
                    if (d < 4.4f) {
                        float ex = dx - 1.9f, ey = dy + 0.9f;
                        if (ex * ex + ey * ey >= 15f) p[i] = 0xFFF6F1D4;   // crescent moon
                    } else if (d < 11f) {
                        float k = 1f - d / 11f;
                        p[i] = mix(p[i], 0xFFB9B4F0, 0.3f * k * k);
                    }
                } else if (d < 4.6f) {
                    p[i] = 0xFFFFF2C4;                                      // sun
                } else if (d < 13f) {
                    float k = 1f - d / 13f;
                    p[i] = mix(p[i], 0xFFFFE7B0, 0.5f * k * k);
                }
            }
        }
        if (night) {
            for (int y = 0; y < 30; y++) {
                for (int x = 0; x < W; x++) {
                    if (hash(x, y, 5) % 53 == 0) p[y * W + x] = 0xFFFFFFFF;
                }
            }
        }
    }

    private static void mountains(int[] p, boolean night) {
        int body = night ? 0xFF2B3070 : 0xFF6F8CC4;
        int lit = night ? 0xFF3A418A : 0xFF8FA8DA;
        int snow = night ? 0xFFBDC6F0 : 0xFFF4F7FF;
        int haze = night ? 0xFF6A4A8E : 0xFFE9C4D4;
        for (int x = 0; x < W; x++) {
            int top = Math.round(24f + Math.abs(x - 24) * 0.55f);
            for (int y = top; y < 46; y++) {
                int depth = y - top;
                int c = x < 24 ? lit : body;
                if (depth < 3 + hash(x, 1, 9) % 3) c = x < 24 ? snow : shade(snow, 0.88f);
                c = mix(c, haze, Math.min(0.55f, depth / 40f * 0.6f));
                put(p, x, y, c);
            }
        }
        int hill = night ? 0xFF14284A : 0xFF4E9078;
        int hill2 = night ? 0xFF0D1B33 : 0xFF3C7A63;
        for (int x = 0; x < W; x++) {
            int y0 = Math.round(41f + 2.2f * (float) Math.sin(x * 0.27f + 1f));
            for (int y = y0; y < 50; y++) put(p, x, y, y < y0 + 2 ? hill : hill2);
        }
    }

    private static void ground(int[] p, boolean night) {
        int g1 = night ? 0xFF1E4040 : 0xFF63B05A;
        int g2 = night ? 0xFF15302F : 0xFF4C9A4E;
        int path = night ? 0xFF5A4F78 : 0xFFEBCFA6;
        int path2 = night ? 0xFF463C60 : 0xFFD2B085;
        for (int y = 49; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = ((x + y) % 5 == 0 || hash(x, y, 11) % 7 == 0) ? g2 : g1;
                if (y == 49) c = shade(g1, 1.15f);
                p[y * W + x] = c;
            }
        }
        for (int y = 55; y < H; y++) {   // path from the gate to the front, wider near the bottom
            float half = 1.5f + (y - 55) * 0.6f;
            float mid = 20.5f + (float) Math.sin((y - 55) * 0.35f) * 1.2f;
            for (int x = 0; x < W; x++) {
                if (Math.abs(x + 0.5f - mid) <= half) p[y * W + x] = (hash(x, y, 4) % 4 == 0) ? path2 : path;
            }
        }
    }

    private static void torii(int[] p, float t, boolean night) {
        int red = night ? 0xFFB02A2E : 0xFFDD3A2C;
        int dark = night ? 0xFF6A1620 : 0xFF9E1F1C;
        int hi = night ? 0xFFD04A44 : 0xFFF5694C;
        int cap = 0xFF1E1418;
        int stone = night ? 0xFF4A4A62 : 0xFF9A9AAE;
        for (int px : new int[]{12, 27}) {                       // pillars
            rect(p, px, 38, px + 3, 55, red);
            rect(p, px, 38, px + 1, 55, hi);
            rect(p, px + 2, 38, px + 3, 55, dark);
            rect(p, px - 1, 54, px + 4, 56, stone);              // base stone
        }
        rect(p, 10, 43, 32, 45, red);                            // lower tie beam
        rect(p, 10, 44, 32, 45, dark);
        rect(p, 19, 39, 23, 43, dark);                           // plaque
        rect(p, 20, 40, 22, 42, 0xFFE8C860);
        rect(p, 8, 37, 34, 39, red);                             // top beam
        rect(p, 8, 38, 34, 39, dark);
        rect(p, 6, 36, 10, 38, red);                             // raised ends
        rect(p, 32, 36, 36, 38, red);
        rect(p, 7, 35, 35, 37, cap);                             // black cap
        rect(p, 5, 34, 9, 36, cap);
        rect(p, 33, 34, 37, 36, cap);
        float fl = 0.75f + 0.25f * (float) Math.sin(TAU * t * 2f);
        for (int lx : new int[]{16, 23}) {                       // hanging lanterns
            rect(p, lx + 1, 45, lx + 2, 46, cap);
            int lamp = night ? mix(0xFFFFB347, 0xFFFFE8A0, fl) : 0xFFE8A030;
            rect(p, lx, 46, lx + 3, 50, lamp);
            rect(p, lx, 46, lx + 3, 47, cap);
            rect(p, lx, 49, lx + 3, 50, cap);
            if (night) glow(p, lx + 1.5f, 48f, 7f, 0xFFFFB347, 0.5f * fl);
        }
    }

    private static void tree(int[] p, float t, boolean night) {
        int bark = night ? 0xFF3A2430 : 0xFF5E3B2E;
        int bark2 = night ? 0xFF2A1A24 : 0xFF3F271F;
        line(p, 0, 63, 2, 52, 5, bark);
        line(p, 2, 52, 3, 42, 4, bark);
        line(p, 3, 42, 4, 33, 3, bark);
        line(p, 4, 37, 11, 29, 2, bark);
        line(p, 3, 41, 0, 34, 2, bark);
        line(p, 8, 32, 19, 24, 1, bark);
        for (int y = 30; y < H; y++) {
            for (int x = 0; x < 12; x++) {
                if (p[y * W + x] == bark && hash(x, y, 8) % 3 == 0) p[y * W + x] = bark2;
            }
        }
        int[] pink = night
                ? new int[]{0xFFC77BA6, 0xFFA85E8E, 0xFFE0A0C4, 0xFF84446E}
                : new int[]{0xFFFFB7D0, 0xFFF58FB5, 0xFFFFDDE9, 0xFFD9689A};
        float[][] blobs = {{5, 25, 9}, {14, 19, 7.5f}, {22, 16, 6.5f}, {30, 13, 5.5f}, {9, 12, 7}, {2, 33, 5},
                {17, 27, 5}, {35, 8, 4.5f}, {24, 6, 5.5f}, {12, 3, 6}, {4, 3, 6}};
        for (int b = 0; b < blobs.length; b++) {
            float bx = blobs[b][0] + (float) Math.sin(TAU * t + b) * 0.8f;
            float by = blobs[b][1] + (float) Math.cos(TAU * t + b * 1.7f) * 0.5f;
            float r = blobs[b][2];
            int x0 = Math.max(0, (int) (bx - r)), x1 = Math.min(W - 1, (int) (bx + r));
            int y0 = Math.max(0, (int) (by - r)), y1 = Math.min(H - 1, (int) (by + r));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float dx = x + 0.5f - bx, dy = y + 0.5f - by;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > r) continue;
                    int h = hash(x, y, 20 + b);
                    if (d > r - 1.6f && h % 3 == 0) continue;     // ragged edge
                    float lit = (-dx - dy) / (2f * r);
                    int c = h % 5 == 0 ? pink[2] : (h % 4 == 0 ? pink[1] : pink[0]);
                    if (lit < -0.25f) c = (h % 3 == 0) ? pink[3] : pink[1];
                    else if (lit > 0.3f && h % 2 == 0) c = pink[2];
                    p[y * W + x] = c;
                }
            }
        }
    }

    private static void fireflies(int[] p, float t) {
        for (int i = 0; i < 6; i++) {
            float x = 8 + hash(i, 3, 9) % 28 + (float) Math.sin(TAU * t + i * 1.3f) * 2.5f;
            float y = 44 + hash(i, 4, 9) % 14 + (float) Math.cos(TAU * t + i * 0.9f) * 2f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(TAU * t * 2f + i * 2.1f);
            int px = Math.round(x), py = Math.round(y);
            if (px < 0 || py < 0 || px >= W || py >= H) continue;
            glow(p, px + 0.5f, py + 0.5f, 3.5f, 0xFFFFF59A, 0.4f * pulse);
            p[py * W + px] = mix(p[py * W + px], 0xFFFFF59A, 0.4f + 0.6f * pulse);
        }
    }

    private static void petals(int[] p, float t, boolean night) {
        int c1 = night ? 0xFFE9A8C8 : 0xFFFFD1E0;
        int c2 = night ? 0xFFC77BA6 : 0xFFFF9EC0;
        for (int i = 0; i < 16; i++) {
            int pass = 1 + (i & 1);                               // whole number of passes: the loop is seamless
            float base = hash(i, 1, 33) % H;
            float y = (base + t * H * pass) % H;
            float x = (hash(i, 2, 33) % W) + (float) Math.sin(TAU * t * pass + i * 1.9f) * 3f;
            int px = Math.round(x), py = Math.round(y);
            int c = (i % 3 == 0) ? c2 : c1;
            put(p, px, py, c);
            if (i % 2 == 0) put(p, px + 1, py, c);
            if (i % 5 == 0) put(p, px, py + 1, c2);
        }
    }
}
