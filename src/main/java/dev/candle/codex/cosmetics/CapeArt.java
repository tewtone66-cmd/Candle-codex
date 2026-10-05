package dev.candle.codex.cosmetics;

/**
 * Pixel art for two animated capes, drawn into the 40 x 64 outer face that AnimCapes turns into a cape texture:
 * "Lion & Sun" (the flag bands with a rotating sun and a lion holding a sword) and "Codex Flame" (the Codex logo
 * on a plain background with a living flame). Every function returns FW * FH ARGB pixels for animation frame f.
 * All motion is periodic over FRAMES frames, so the loop has no jump.
 */
final class CapeArt {
    private static final int FW = 40, FH = 64;
    private static final int FRAMES = AnimCapes.FRAMES;
    private static final float TAU = AnimCapes.TAU;

    private CapeArt() {}

    private static void put(int[] p, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < FW && y < FH) p[y * FW + x] = c;
    }

    private static int at(int[] p, int x, int y) {
        return p[Math.max(0, Math.min(FH - 1, y)) * FW + Math.max(0, Math.min(FW - 1, x))];
    }

    // ------------------------------------------------------------------ Lion & Sun

    /** Lion facing left with a raised sword, 32 x 24. Letters are palette entries, '.' is empty. */
    private static final String[] LION = {
            "...a............................",
            "...a........................bbb.",
            "...a..bb...................bcddb",
            "...a.befb.bb..............bcdddd",
            "...abfffebefb.............bddgdc",
            ".aaaafffffffeb............bdddcg",
            "...abgffdffffb.............bdcdb",
            "...adddgcdffdb.bbbbbbbbb....bbff",
            "..bddhffdgddcdbfefefefefbbb..bfh",
            "..bdbbfffddcddefffffffffefebbffb",
            ".bhfffffffcddgffffffffffffffbfhb",
            ".bbfffffffddddffffffffffffffehb.",
            ".bbfffffffgddcffffffffffffffhb..",
            ".bhhfffffdddcffffffffffffffhb...",
            "..bbhhffdddcffhffffffffffffb....",
            "....bbdddgcfffbfffhhfffhfffb....",
            "......bbbbbfffbfffbbfffbfffb....",
            "..........bfffbfffbbfffbfffb....",
            "..........bfffbfffbbfffbfffb....",
            "..........bfffbfffbbfffbfffb....",
            "..........bfffbfffbbfffbfffb....",
            "..........bfffbfffbbfffbfffb....",
            "..........bfffbfffbbfffbfffb....",
            "..........bhhhbhhhbbhhhbhhhb....",
    };
    private static final int LX = 4, LY = 24;

    private static int lionColor(char c) {
        return switch (c) {
            case 'a' -> 0xFFD6DEEC;
            case 'b' -> 0xFF341E08;
            case 'c' -> 0xFFD68422;
            case 'd' -> 0xFFB06216;
            case 'e' -> 0xFFFFE08C;
            case 'f' -> 0xFFE8B23E;
            case 'g' -> 0xFF8C4C10;
            default -> 0xFFBE7C22; // 'h'
        };
    }

    static int[] lionSunFace(int f) {
        float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];

        // flag bands (green / white / red) with a slow cloth ripple
        for (int y = 0; y < FH; y++) {
            int base = y < 19 ? 0xFF239F40 : y < 46 ? 0xFFF4F4EE : 0xFFDA0000;
            float vert = 0.93f + 0.07f * (1f - y / (float) (FH - 1));
            for (int x = 0; x < FW; x++) {
                float ripple = 0.965f + 0.035f * (float) Math.sin(TAU * t + x * 0.32f + y * 0.11f);
                p[y * FW + x] = AnimCapes.shade(base, vert * ripple);
            }
        }

        // warm glow behind the sun
        final float cx = 24f, cy = 27f;
        float pulse = 0.8f + 0.2f * (float) Math.sin(TAU * t);
        for (int y = 0; y < FH; y++) {
            for (int x = 0; x < FW; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 22f) {
                    float k = 1f - d / 22f;
                    p[y * FW + x] = AnimCapes.mix(p[y * FW + x], 0xFFFFC857, 0.30f * k * k * pulse);
                }
            }
        }

        // 16 rays turning slowly (one ray gap per loop), then the disc with a face-less glowing core
        final int n = 16;
        final float gap = TAU / n, rot = gap * t;
        for (int y = 0; y < FH; y++) {
            for (int x = 0; x < FW; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                if (r > 7.2f && r < 15.5f) {
                    float ang = (float) Math.atan2(dy, dx) - rot;
                    float local = ((ang % gap) + gap) % gap - gap / 2f;
                    float arc = Math.abs(local) * r;
                    float half = 0.4f + 1.9f * (1f - (r - 7.2f) / 8.3f);
                    if (arc < half) p[y * FW + x] = arc > half * 0.62f ? 0xFFE39A12 : 0xFFFFD23C;
                } else if (r <= 7.2f) {
                    int c = AnimCapes.mix(0xFFFFEDA8, 0xFFF0A21C, r / 7.2f);
                    if (r > 6.2f) c = 0xFFD98A10;
                    p[y * FW + x] = c;
                }
            }
        }

        // the lion, drawn over the sun
        for (int y = 0; y < LION.length; y++) {
            String row = LION[y];
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c != '.') put(p, LX + x, LY + y, lionColor(c));
            }
        }

        // sword glint: a short twinkle every loop
        int g = f % FRAMES;
        if (g >= 4 && g <= 6) {
            int gx = LX + 3, gy = LY;
            int c = g == 5 ? 0xFFFFFFFF : 0xFFE8F0FF;
            put(p, gx, gy - 1, c);
            put(p, gx, gy, 0xFFFFFFFF);
            if (g == 5) {
                put(p, gx - 1, gy, c);
                put(p, gx + 1, gy, c);
                put(p, gx, gy - 2, c);
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ Codex Flame

    static int[] flameFace(int f, int bgRgb) {
        float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];
        int bg = 0xFF000000 | (bgRgb & 0xFFFFFF);
        int bgTop = AnimCapes.shade(bg, 1.18f), bgBot = AnimCapes.shade(bg, 0.78f);

        // plain background: soft vertical gradient plus a warm glow around the flame
        final float cx = 20f, gcy = 19f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(TAU * t);
        for (int y = 0; y < FH; y++) {
            int row = AnimCapes.mix(bgTop, bgBot, y / (float) (FH - 1));
            for (int x = 0; x < FW; x++) {
                float dx = x + 0.5f - cx, dy = (y + 0.5f - gcy) * 0.85f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int c = row;
                if (d < 27f) {
                    float k = 1f - d / 27f;
                    c = AnimCapes.mix(row, 0xFFFF9A2E, 0.42f * k * k * pulse);
                }
                p[y * FW + x] = c;
            }
        }

        // wax body: lit in the middle, darker at the edges, rounded melted top, a few drips
        for (int y = 34; y < 61; y++) {
            for (int x = 11; x < 29; x++) {
                float dx = (x + 0.5f - 20f) / 9f;
                if (y < 38) {
                    float ey = (y + 0.5f - 37f) / 3.2f;
                    if (dx * dx + ey * ey > 1f) continue;
                }
                float edge = (float) Math.pow(Math.abs(dx), 1.6);
                int c = AnimCapes.mix(0xFFFFF3D8, 0xFFD2BE96, edge);
                if (y > 52) c = AnimCapes.shade(c, 1f - 0.12f * (y - 52) / 8f);
                p[y * FW + x] = c;
            }
        }
        // melted top surface catching the flame light
        for (int y = 34; y < 40; y++) {
            for (int x = 11; x < 29; x++) {
                float dx = (x + 0.5f - 20f) / 9f, dy = (y + 0.5f - 36.8f) / 2.6f;
                float d = dx * dx + dy * dy;
                if (d <= 1f) p[y * FW + x] = AnimCapes.mix(0xFFFFE9B0, 0xFFFFC866, d * 0.8f + 0.08f * (float) Math.sin(TAU * t));
            }
        }
        int[][] drips = {{13, 14}, {25, 10}, {22, 6}, {16, 4}};
        for (int[] dr : drips) {
            for (int y = 38; y < 38 + dr[1]; y++) put(p, dr[0], y, 0xFFFFF8E6);
            put(p, dr[0], 38 + dr[1], 0xFFFFE9C4);
        }
        for (int x = 11; x < 29; x++) put(p, x, 60, AnimCapes.shade(at(p, x, 60), 0.85f));

        // wick
        for (int y = 29; y < 36; y++) {
            put(p, 19, y, 0xFF2B2B2B);
            put(p, 20, y, 0xFF2B2B2B);
        }
        put(p, 19, 29, 0xFFFF8A2E);
        put(p, 20, 29, 0xFFFF8A2E);

        // flame (same recipe as the Codex Ad cape, bigger)
        float tip = 3f + 1.8f * (float) Math.sin(TAU * 2 * t);
        float by = 22f, rx = 6.1f, ry = 6.9f, bottom = by + ry;
        for (int y = 0; y < 30; y++) {
            float hw;
            if (y + 0.5f < tip) continue;
            if (y + 0.5f <= by) hw = rx * (float) Math.pow((y + 0.5f - tip) / (by - tip), 1.4);
            else {
                float q = (y + 0.5f - by) / ry;
                hw = q >= 1f ? 0f : rx * (float) Math.sqrt(1 - q * q);
            }
            if (hw <= 0.05f) continue;
            float sway = (float) Math.sin(TAU * (t + y * 0.045f)) * 1.8f * Math.max(0f, 1f - (y - tip) / (bottom - tip));
            for (int x = 0; x < FW; x++) {
                float dx = Math.abs(x + 0.5f - (cx + sway));
                if (dx > hw) continue;
                float ratio = dx / hw;
                int c;
                if (ratio < 0.38f && y > tip + 6f) c = 0xFFFFF6C8;
                else if (ratio < 0.72f) c = 0xFFFFC933;
                else c = 0xFFFF8A00;
                p[y * FW + x] = c;
            }
        }
        // three rising sparks
        for (int i = 0; i < 3; i++) {
            float ph = (t * 2 + i / 3f) % 1f;
            int sx = Math.round(cx + (float) Math.sin(TAU * (t + i * 0.31f)) * (5f + i));
            int sy = Math.round(tip - 1f - ph * 8f);
            if (sy >= 0) put(p, sx, sy, AnimCapes.mix(at(p, sx, sy), 0xFFFFD27A, 1f - ph));
        }
        return p;
    }
}
