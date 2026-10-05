package dev.candle.codex.cosmetics;

/**
 * Pixel art for the animated "Glitch Cape" (Glitchy Cosmetics pack). Same recipe as CapeArt: face(f) returns FW * FH ARGB
 * pixels (the 40 x 64 outer face) for animation frame f. A dark grid with falling digital rain, a big "G" drawn three times
 * (white, cyan and magenta copies pushed apart for the colour split), and a few bands of rows that tear sideways, different
 * in every frame. The rain loops cleanly over FRAMES frames.
 */
final class CapeArtGlitch {
    private static final int FW = 40, FH = 64;
    private static final int FRAMES = AnimCapes.FRAMES;
    private static final int CYAN = 0xFF00F0FF, MAGENTA = 0xFFFF2DAA;

    private CapeArtGlitch() {}

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return rgb(r, g, bl);
    }

    private static int shade(int c, float f) {
        return rgb(Math.round(((c >> 16) & 255) * f), Math.round(((c >> 8) & 255) * f), Math.round((c & 255) * f));
    }

    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    /** Mask of the letter G (centre 20, 30): a ring open on the upper right, with a bar pointing inwards. */
    private static boolean[] glyph() {
        boolean[] m = new boolean[FW * FH];
        for (int y = 0; y < FH; y++) {
            for (int x = 0; x < FW; x++) {
                float dx = x + 0.5f - 20f, dy = y + 0.5f - 30f;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                boolean ring = r >= 8f && r <= 12.5f && !(dx > 2f && dy > -7f && dy < -1f);
                boolean bar = dy >= -1f && dy <= 2.5f && dx >= 1f && dx <= 12.5f;
                m[y * FW + x] = ring || bar;
            }
        }
        return m;
    }

    private static boolean at(boolean[] m, int x, int y) {
        return x >= 0 && y >= 0 && x < FW && y < FH && m[y * FW + x];
    }

    static int[] face(int f) {
        final float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];

        // dark background with a faint grid and scan lines
        for (int y = 0; y < FH; y++) {
            int row = mix(0xFF0B0720, 0xFF06242E, y / 63f);
            for (int x = 0; x < FW; x++) {
                int c = row;
                if (x % 8 == 0 || y % 8 == 0) c = mix(c, 0xFF1B2A55, 0.5f);
                if ((y & 1) == 1) c = shade(c, 0.85f);
                p[y * FW + x] = c;
            }
        }

        // digital rain: ten columns, every column loops once or twice per cycle
        for (int col = 0; col < 10; col++) {
            int x = 2 + col * 4 + hash(col, 1, 41) % 2;
            int k = col % 3 == 0 ? 2 : 1;
            float head = ((t * k + (hash(col, 2, 41) % 100) / 100f) % 1f) * 84f - 10f;
            int base = col % 2 == 0 ? CYAN : MAGENTA;
            for (int j = 0; j < 10; j++) {
                int y = Math.round(head) - j;
                if (y < 0 || y >= FH || x >= FW) continue;
                p[y * FW + x] = mix(base, 0xFF0B0720, j / 10f);
            }
        }

        // the big G with a colour split: magenta copy to the left, cyan copy to the right, white letter on top
        boolean[] g = glyph();
        for (int y = 0; y < FH; y++) {
            for (int x = 0; x < FW; x++) {
                boolean w = at(g, x, y), cy = at(g, x - 2, y), mg = at(g, x + 2, y);
                if (w) p[y * FW + x] = 0xFFFFFFFF;
                else if (cy && mg) p[y * FW + x] = 0xFFE8F4FF;
                else if (cy) p[y * FW + x] = CYAN;
                else if (mg) p[y * FW + x] = MAGENTA;
            }
        }

        // bands of rows tear sideways (every frame has its own bands, so the picture keeps jumping)
        int bands = 2 + f % 3;
        for (int b = 0; b < bands; b++) {
            int y0 = hash(f, b, 1) % 56;
            int h = 2 + hash(f, b, 2) % 7;
            int dx = hash(f, b, 3) % 13 - 6;
            if (dx == 0) dx = 3;
            int[] tmp = new int[FW];
            for (int y = y0; y < Math.min(FH, y0 + h); y++) {
                for (int x = 0; x < FW; x++) tmp[x] = p[y * FW + ((x - dx) % FW + FW) % FW];
                for (int x = 0; x < FW; x++) p[y * FW + x] = tmp[x];
            }
        }

        // a few bright noise blocks
        for (int i = 0; i < 7; i++) {
            int x = hash(f, i, 4) % (FW - 2), y = hash(f, i, 5) % FH;
            int c = i % 3 == 0 ? 0xFFFFFFFF : i % 3 == 1 ? CYAN : MAGENTA;
            p[y * FW + x] = c;
            p[y * FW + x + 1] = c;
        }

        // thin frame whose colour jumps between frames
        int fc = f % 2 == 0 ? CYAN : MAGENTA;
        for (int x = 0; x < FW; x++) {
            p[x] = fc;
            p[(FH - 1) * FW + x] = fc;
        }
        for (int y = 0; y < FH; y++) {
            p[y * FW] = fc;
            p[y * FW + FW - 1] = fc;
        }
        return p;
    }
}
