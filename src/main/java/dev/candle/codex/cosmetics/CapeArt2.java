package dev.candle.codex.cosmetics;

/**
 * Pixel art for two more animated capes, same recipe as CapeArt: every function returns FW * FH ARGB pixels (the 40 x 64
 * outer face) for animation frame f, and all motion is periodic over FRAMES frames so the loop has no jump.
 * "Hanok Night" = a big Korean hanok (curved tile roof, painted eaves, wooden posts, paper lattice doors that flicker,
 * swinging lanterns, fireflies, twinkling stars). "Cherry Blossom" = a pink cherry tree with petals falling.
 */
final class CapeArt2 {
    private static final int FW = 40, FH = 64;
    private static final int FRAMES = AnimCapes.FRAMES;
    private static final float TAU = (float) (Math.PI * 2);

    private CapeArt2() {}

    // ------------------------------------------------------------------ helpers

    private static void put(int[] p, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < FW && y < FH) p[y * FW + x] = c;
    }

    private static int at(int[] p, int x, int y) {
        return p[Math.max(0, Math.min(FH - 1, y)) * FW + Math.max(0, Math.min(FW - 1, x))];
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int shade(int c, float f) {
        return rgb(Math.round(((c >> 16) & 255) * f), Math.round(((c >> 8) & 255) * f), Math.round((c & 255) * f));
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return rgb(r, g, bl);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    /** Soft radial glow: blends colour col into the pixels within radius r of (cx, cy). */
    private static void glow(int[] p, float cx, float cy, float r, int col, float amount) {
        int x0 = Math.max(0, (int) (cx - r)), x1 = Math.min(FW - 1, (int) (cx + r) + 1);
        int y0 = Math.max(0, (int) (cy - r)), y1 = Math.min(FH - 1, (int) (cy + r) + 1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < r) {
                    float k = 1f - d / r;
                    p[y * FW + x] = mix(p[y * FW + x], col, amount * k * k);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Hanok Night

    private static final int[] POSTS = {4, 5, 14, 15, 24, 25, 34, 35};
    private static final int[] BAYS = {6, 16, 26};

    private static boolean isPost(int x) {
        for (int q : POSTS) if (q == x) return true;
        return false;
    }

    static int[] hanokFace(int f) {
        final float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];

        // night sky: indigo at the top, a warm purple glow low down
        for (int y = 0; y < FH; y++) {
            float k = y / (float) (FH - 1);
            int row = mix(0xFF0B1030, 0xFFB5586A, (float) Math.pow(k, 1.5));
            for (int x = 0; x < FW; x++) {
                int c = row;
                int h = hash(x, y, 5);
                if (y < 26 && h % 53 == 0) {
                    float tw = 0.5f + 0.5f * (float) Math.sin(TAU * (t + (h % 13) / 13f));
                    c = mix(row, 0xFFFFFFFF, 0.3f + 0.7f * tw);
                }
                p[y * FW + x] = c;
            }
        }
        // crescent moon with a faint halo
        glow(p, 32f, 6f, 7f, 0xFFFFF1C2, 0.25f);
        for (int y = 2; y < 11; y++) {
            for (int x = 28; x < 37; x++) {
                float dx = x + 0.5f - 32f, dy = y + 0.5f - 6f;
                float ex = x + 0.5f - 30.8f, ey = y + 0.5f - 5.2f;
                if (dx * dx + dy * dy <= 10.2f && ex * ex + ey * ey > 7f) p[y * FW + x] = 0xFFFFF1C2;
            }
        }

        // ground (stone path in the middle)
        for (int y = 56; y < FH; y++) {
            float k = (y - 56) / 8f;
            for (int x = 0; x < FW; x++) {
                int c = mix(0xFF1A2A24, 0xFF0D1613, k);
                if (hash(x, y, 2) % 7 == 0) c = shade(c, 1.25f);
                if (x >= 16 && x < 24 && y >= 59) {
                    boolean seam = (y - 59) % 3 == 2 || (x + ((y - 59) / 3) * 3) % 5 == 0;
                    c = seam ? 0xFF3A3D44 : 0xFF6A6E77;
                }
                p[y * FW + x] = c;
            }
        }

        // curved tile roof: concave slope, upswept eave tips, round end tiles along the lower edge
        for (int x = 0; x < FW; x++) {
            float d = Math.abs(x + 0.5f - 20f);
            float up = d > 13f ? (d - 13f) / 7f * 3f : 0f;
            float bottom = 27f - up;
            float top;
            if (d <= 8f) {
                top = 11f;
            } else {
                float u = Math.min(1f, (d - 8f) / 12f);
                top = 11f + 12f * (1f - (float) Math.pow(1f - u, 1.8));
                if (d > 14f) top -= (d - 14f) / 6f * 2.5f;
            }
            int y0 = (int) Math.floor(top), y1 = (int) Math.ceil(bottom) - 1;
            for (int y = y0; y <= y1; y++) {
                int c = 0xFF39414F;
                if (x % 2 == 0) c = shade(c, 0.8f);
                if (y % 3 == 0) c = shade(c, 1.1f);
                if (y == y0) c = 0xFF5B6A82;
                if (y == y1) c = x % 2 == 0 ? 0xFFC9CFD8 : 0xFF262C37;
                put(p, x, y, c);
            }
            if (d <= 9.5f) put(p, x, 10, 0xFF6C7A92);          // ridge cap
            if (d >= 7.5f && d <= 9.5f) put(p, x, 9, 0xFF6C7A92); // upturned ridge ends
            if (d >= 8.5f && d <= 9.5f) put(p, x, 8, 0xFF8796AE);
        }

        // painted eave beam (red / green / blue blocks), then a dark beam
        for (int x = 3; x < 37; x++) {
            int m = x % 6;
            int c = m < 2 ? 0xFFC0392B : m < 4 ? 0xFF2E8B57 : 0xFF2A5DA8;
            put(p, x, 27, shade(c, 0.85f));
            put(p, x, 28, c);
            put(p, x, 29, 0xFF3E2412);
        }

        // wooden wall: posts and glowing paper lattice doors
        for (int y = 30; y < 50; y++) {
            for (int x = 4; x < 36; x++) {
                if (isPost(x)) {
                    int c = x % 2 == 0 ? 0xFF7A4A24 : 0xFF63391A;
                    if (hash(x, y, 4) % 6 == 0) c = shade(c, 0.85f);
                    put(p, x, y, c);
                }
            }
        }
        for (int b = 0; b < BAYS.length; b++) {
            int sx = BAYS[b];
            float fl = 0.55f + 0.45f * (float) Math.sin(TAU * (2 * t + b * 0.31f));
            for (int ly = 0; ly < 20; ly++) {
                int paper = shade(mix(0xFFFFC66A, 0xFFFFEDB8, fl), 1f - 0.12f * ly / 19f);
                for (int lx = 0; lx < 8; lx++) {
                    boolean frame = lx == 0 || lx == 7 || ly == 0 || ly == 19;
                    boolean vert = lx == 4 || lx == 2 && b == 1 || lx == 6 && b == 1;
                    boolean horiz = b == 1 ? (ly == 4 || ly == 9 || ly == 14) : (ly == 6 || ly == 13);
                    put(p, sx + lx, 30 + ly, frame || vert || horiz ? 0xFF4A2A12 : paper);
                }
            }
            glow(p, sx + 4f, 40f, 9f, 0xFFFFB04A, 0.10f * fl);
        }

        // wooden floor, stone base, steps
        for (int y = 50; y < 53; y++) {
            for (int x = 3; x < 37; x++) {
                int c = y == 50 ? 0xFFB9824A : y == 52 ? 0xFF6E4620 : 0xFF9A6630;
                if (x % 6 == 0) c = shade(c, 0.8f);
                put(p, x, y, c);
            }
        }
        for (int y = 53; y < 56; y++) {
            for (int x = 2; x < 38; x++) {
                int c = y == 53 ? 0xFF8D9099 : 0xFF70747C;
                if ((x + (y % 2) * 4) % 8 == 0) c = shade(c, 0.78f);
                put(p, x, y, c);
            }
        }
        for (int x = 14; x < 26; x++) {
            put(p, x, 56, 0xFFA3A7AF);
            put(p, x, 57, 0xFF80848C);
            put(p, x, 58, 0xFF5E626A);
        }

        // two swinging paper lanterns hanging from the eaves
        lantern(p, 2, TAU * t, t);
        lantern(p, 37, TAU * t + 3.1416f, t);

        // fireflies over the ground
        for (int i = 0; i < 5; i++) {
            float fx = 3f + hash(i, 1, 9) % 33 + 2.5f * (float) Math.sin(TAU * (t + i * 0.21f));
            float fy = 58f + hash(i, 2, 9) % 4 + 2.2f * (float) Math.sin(TAU * (2 * t + i * 0.17f));
            float b = 0.5f + 0.5f * (float) Math.sin(TAU * (3 * t + i * 0.4f));
            int ix = Math.round(fx), iy = Math.round(fy);
            put(p, ix, iy, mix(at(p, ix, iy), 0xFFE6FF7A, 0.4f + 0.6f * b));
            put(p, ix + 1, iy, mix(at(p, ix + 1, iy), 0xFFE6FF7A, 0.25f * b));
        }
        return p;
    }

    private static void lantern(int[] p, int cx, float ang, float t) {
        int sway = Math.round((float) Math.sin(ang));
        float pulse = 0.8f + 0.2f * (float) Math.sin(TAU * 2 * t + cx);
        glow(p, cx + sway + 0.5f, 32f, 7f, 0xFFFF9A2E, 0.45f * pulse);
        for (int y = 25; y < 29; y++) put(p, cx, y, 0xFF2B2B2B);
        int bx = cx + sway;
        for (int x = bx - 1; x <= bx + 1; x++) {
            put(p, x, 29, 0xFF2B2B2B);
            put(p, x, 34, 0xFF2B2B2B);
            for (int y = 30; y < 34; y++) put(p, x, y, x == bx && y > 30 && y < 33 ? 0xFFFFB347 : 0xFFD9392B);
        }
        put(p, bx, 35, 0xFFFFC857);
        put(p, bx, 36, 0xFFFFC857);
    }

    // ------------------------------------------------------------------ Cherry Blossom

    /** Canopy blobs: x, y, radius. */
    private static final float[][] BLOBS = {
            {20, 15, 9}, {11, 21, 7.5f}, {30, 20, 8}, {6, 30, 5.5f}, {35, 29, 5.5f},
            {20, 26, 8.5f}, {14, 11, 6}, {27, 10, 6.5f}, {20, 6, 4.5f}};

    private static void limb(int[] p, float x0, float y0, float x1, float y1, float w0, float w1) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(1, Math.round(len * 2f));
        for (int s = 0; s <= steps; s++) {
            float k = s / (float) steps;
            float cx = x0 + (x1 - x0) * k, cy = y0 + (y1 - y0) * k, w = w0 + (w1 - w0) * k;
            for (int y = (int) Math.floor(cy - w / 2); y <= (int) Math.ceil(cy + w / 2); y++) {
                for (int x = (int) Math.floor(cx - w / 2); x <= (int) Math.ceil(cx + w / 2); x++) {
                    float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                    if (dx * dx + dy * dy > w * w / 4f + 0.25f) continue;
                    int c = dx < -0.3f * w ? 0xFF8A5E4E : dx > 0.3f * w ? 0xFF45291F : 0xFF5F3E31;
                    if (hash(x, y, 6) % 6 == 0) c = shade(c, 0.85f);
                    put(p, x, y, c);
                }
            }
        }
    }

    static int[] cherryFace(int f) {
        final float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];

        // soft spring sky
        for (int y = 0; y < FH; y++) {
            float k = Math.min(1f, y / 46f);
            int row = mix(0xFF9DBCF2, 0xFFFFD1E3, (float) Math.pow(k, 1.2));
            for (int x = 0; x < FW; x++) p[y * FW + x] = row;
        }
        // warm pink glow behind the tree
        float pulse = 0.85f + 0.15f * (float) Math.sin(TAU * t);
        glow(p, 20f, 22f, 26f, 0xFFFFEAF2, 0.38f * pulse);
        // two slowly drifting clouds
        float[][] clouds = {{9f, 8f, 5f, 0f}, {29f, 15f, 4f, 1.7f}};
        for (float[] c : clouds) {
            float cx = c[0] + 2.5f * (float) Math.sin(TAU * t + c[3]);
            for (int y = (int) c[1] - 3; y <= (int) c[1] + 3; y++) {
                for (int x = 0; x < FW; x++) {
                    float dx = (x + 0.5f - cx) / c[2], dy = (y + 0.5f - c[1]) / 1.8f;
                    float d = dx * dx + dy * dy;
                    if (d < 1f && y >= 0 && y < FH) p[y * FW + x] = mix(p[y * FW + x], 0xFFFFFFFF, 0.6f * (1f - d));
                }
            }
        }

        // grass with a petal carpet, plus a soft shadow under the tree
        for (int y = 54; y < FH; y++) {
            int row = mix(0xFF8CC96B, 0xFF4F8F4B, (y - 54) / 10f);
            for (int x = 0; x < FW; x++) {
                int c = row;
                int h = hash(x, y, 2);
                if (h % 5 == 0) c = shade(c, 1.12f);
                float dx = (x + 0.5f - 20f) / 10f, dy = (y + 0.5f - 57f) / 2.4f;
                if (dx * dx + dy * dy < 1f) c = shade(c, 0.78f);
                if (y >= 56 && hash(x, y, 7) % 9 == 0) {
                    float tw = 0.75f + 0.25f * (float) Math.sin(TAU * (t + (h % 12) / 12f));
                    c = shade(0xFFFFB6D0, tw);
                }
                p[y * FW + x] = c;
            }
        }

        // trunk and branches
        limb(p, 20, 57, 20, 44, 5.5f, 4f);
        limb(p, 19, 44, 12, 35, 3.2f, 2.2f);
        limb(p, 12, 35, 6, 27, 2.2f, 1.4f);
        limb(p, 21, 44, 28, 34, 3.2f, 2.2f);
        limb(p, 28, 34, 34, 26, 2.2f, 1.4f);
        limb(p, 20, 44, 20, 28, 4f, 2.2f);
        limb(p, 20, 30, 15, 22, 2f, 1.2f);
        limb(p, 20, 30, 26, 21, 2f, 1.2f);

        // blossom canopy, swaying a little in the wind (sampled with a shifted x, so it moves as one piece)
        for (int y = 0; y < 42; y++) {
            float sway = 1.0f * (float) Math.sin(TAU * t + y * 0.05f);
            for (int x = 0; x < FW; x++) {
                int ix = Math.round(x - sway);
                float best = -1f, bdy = 0f;
                for (float[] b : BLOBS) {
                    float dx = ix + 0.5f - b[0], dy = y + 0.5f - b[1];
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float edge = b[2] + 1.4f * ((hash(ix, y, 11) % 100) / 100f - 0.5f);
                    float cov = 1f - d / edge;
                    if (cov > 0f && cov > best) {
                        best = cov;
                        bdy = dy / b[2];
                    }
                }
                if (best <= 0f) continue;
                float n = (hash(ix, y, 3) % 100) / 100f;
                int c = n < 0.12f ? 0xFFFFE9F2 : n < 0.45f ? 0xFFFFC2D9 : n < 0.8f ? 0xFFFF9EC2 : 0xFFEE78A6;
                c = shade(c, 1.06f - 0.16f * Math.max(-1f, Math.min(1f, bdy)));
                int h = hash(ix, y, 5);
                if (h % 11 == 0) {
                    float tw = Math.max(0f, (float) Math.sin(TAU * (t + (h % 12) / 12f)));
                    c = mix(c, 0xFFFFFFFF, 0.55f * tw);
                }
                p[y * FW + x] = c;
            }
        }

        // falling petals (every petal loops once or twice per cycle, so there is no jump)
        for (int i = 0; i < 20; i++) {
            int k = i % 4 == 0 ? 2 : 1;
            float ph = (hash(i, 4, 17) % 100) / 100f;
            float fall = (t * k + ph) % 1f;
            float py = 8f + fall * 56f;
            float px = 6f + hash(i, 9, 17) % 28 + 3.2f * (float) Math.sin(TAU * (t * k + ph * 3f));
            int ix = Math.round(px), iy = Math.round(py);
            int o = (f + i) % 3;
            int ox = o == 2 ? 0 : 1, oy = o == 0 ? 0 : 1;
            float land = iy >= 57 ? 0.5f : 1f;
            put(p, ix, iy, mix(at(p, ix, iy), 0xFFFFFFFF, land));
            put(p, ix + ox, iy + oy, mix(at(p, ix + ox, iy + oy), 0xFFFF6FA6, land));
        }
        return p;
    }
}
