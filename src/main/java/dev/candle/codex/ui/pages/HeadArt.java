package dev.candle.codex.ui.pages;

import dev.candle.codex.cosmetics.Cosmetics;
import dev.candle.codex.ui.Draw;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Menu pictures for hats and glasses: a small pixel-art head (front view) with the item drawn on it in the item's own
 * colours. The canvas is 20 x 22 art pixels, every art pixel is PX screen pixels. The head is 12 x 12 art pixels at
 * column 4, row 10 (so 1.5 art pixels = 1 pixel of the real 8 x 8 head). The same colours (c1 / c2 of the entry) drive
 * the 3D model in CosmeticLayer.
 *
 * Letters in the sprites: a/f = colour 1, A = dark 1, L = light 1, b/l = colour 2, B = dark 2, M = light 2,
 * g = glint (light 2), w = white, x = light grey, r = ruby, u = sapphire, k = near black, . = empty.
 */
final class HeadArt {
    private static final int PX = 3, W = 20, H = 22, HEAD_COL = 4, HEAD_ROW = 10;
    /** The card shows this picture centred on x, with its vertical centre at y. */
    private static final int HALF_H = H * PX / 2;

    private static final String[] HEAD = {
            "hhhhhhhhhhhh",
            "hhhhhhhhhhhh",
            "hhhhhhhhhhhh",
            "hhsssssssshh",
            "hssssssssssh",
            "ssssssssssss",
            "ssWpsssspWss",
            "ssWpsssspWss",
            "ssssssssssss",
            "ssssssssssss",
            "sssmmmmmmsss",
            "ssssssssssss"};

    private record Sprite(int c0, int bottom, String[] rows) {}

    private static Sprite sp(int c0, int bottom, String... rows) {
        return new Sprite(c0, bottom, rows);
    }

    private HeadArt() {}

    // ------------------------------------------------------------------ colours

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int shade(int c, float f) {
        return opaque((Math.round(((c >> 16) & 255) * f) << 16) | (Math.round(((c >> 8) & 255) * f) << 8) | Math.round((c & 255) * f));
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return opaque((r << 16) | (g << 8) | bl);
    }

    private static int itemColor(char ch, Cosmetics.Entry e) {
        int a = e.c1(), b = e.c2();
        return switch (ch) {
            case 'a', 'f' -> opaque(a);
            case 'A' -> shade(a, 0.68f);
            case 'L' -> mix(a, 0xFFFFFF, 0.4f);
            case 'b', 'l' -> opaque(b);
            case 'B' -> shade(b, 0.68f);
            case 'M' -> mix(b, 0xFFFFFF, 0.4f);
            case 'g' -> mix(b, 0xFFFFFF, 0.45f);
            case 'w' -> 0xFFF4F4F4;
            case 'x' -> 0xFFD0D0DA;
            case 'r' -> 0xFFE0405A;
            case 'u' -> 0xFF3CAAFF;
            case 'k' -> 0xFF1B1B22;
            default -> 0;
        };
    }

    private static int headColor(char ch) {
        return switch (ch) {
            case 'h' -> 0xFF4B3621;
            case 's' -> 0xFFC8956A;
            case 'W' -> 0xFFF4F4F4;
            case 'p' -> 0xFF3A3FA8;
            case 'm' -> 0xFF8A5A3C;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ drawing

    private static void put(GuiGraphics g, int ox, int oy, int col, int row, int argb) {
        int x = ox + col * PX, y = oy + row * PX;
        Draw.fill(g, x, y, x + PX, y + PX, argb);
    }

    private static void head(GuiGraphics g, int ox, int oy) {
        for (int r = 0; r < HEAD.length; r++) {
            for (int c = 0; c < HEAD[r].length(); c++) {
                char ch = HEAD[r].charAt(c);
                int argb = headColor(ch);
                if (ch == 's' && (c == HEAD[r].length() - 1 || r == HEAD.length - 1)) argb = shade(0xC8956A, 0.82f); // soft edge shadow
                put(g, ox, oy, HEAD_COL + c, HEAD_ROW + r, argb);
            }
        }
    }

    private static void sprite(GuiGraphics g, int ox, int oy, Sprite s, Cosmetics.Entry e) {
        int top = s.bottom() - s.rows().length + 1;
        for (int r = 0; r < s.rows().length; r++) {
            String row = s.rows()[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '.') continue;
                int argb = itemColor(ch, e);
                if (argb != 0) put(g, ox, oy, s.c0() + c, top + r, argb);
            }
        }
    }

    // ------------------------------------------------------------------ hats

    private static Sprite hatSprite(String id) {
        return switch (id) {
            case "crown" -> sp(4, 11,
                    "LL.L.LL.L.LL",
                    "aa.a.aa.a.aa",
                    "aaaaaaaaaaaa",
                    "aauaarraauaa",
                    "aaaaaaaaaaaa",
                    "bbbbbbbbbbbb");
            case "tophat" -> sp(3, 11,
                    "...Laaaaaaa...",
                    "...Laaaaaaa...",
                    "...Laaaaaaa...",
                    "...Laaaaaaa...",
                    "...Laaaaaaa...",
                    "...rrrbbrrr...",
                    "AAAAAAAAAAAAAA");
            case "halo" -> sp(3, 6,
                    "...MMMMMMMM...",
                    "..aa........aa",
                    "...AAAAAAAA...");
            case "party" -> sp(4, 11,
                    ".....ww.....",
                    ".....ww.....",
                    ".....aa.....",
                    "....bbbb....",
                    "...aaaaaa...",
                    "..bbbbbbbb..",
                    ".aaaaaaaaaa.",
                    "bbbbbbbbbbbb");
            case "cat" -> sp(4, 11,
                    ".A........A.",
                    ".aa......aa.",
                    ".abA....Aba.",
                    "aabbaaaabbaa",
                    "aaaaaaaaaaaa",
                    "AAAAAAAAAAAA");
            case "santa" -> sp(3, 11,
                    "......ww......",
                    ".....aaaA.....",
                    "....aaaaaA....",
                    "...aaaaaaaA...",
                    "..aaaaaaaaaA..",
                    ".aaaaaaaaaaaA.",
                    ".wwwwwwwwwwww.",
                    ".xxxxxxxxxxxx.");
            case "wizard" -> sp(2, 11,
                    ".......aa.......",
                    ".......aa.......",
                    "......aaaA......",
                    "......aaaA......",
                    ".....aabbaA.....",
                    ".....aaaaaA.....",
                    "....aaaaaaaA....",
                    "....bbbbbbbb....",
                    "AAAAAAAAAAAAAAAA");
            case "cap" -> sp(3, 11,
                    "......bb......",
                    "....aaaaaa....",
                    "...aaaaaaaa...",
                    "..aaaaaaaaaa..",
                    ".aaaaaaaaaaaA.",
                    ".aaaaaaaaaaaA.",
                    ".bbbbbbbbbbbb.",
                    "BBBBBBBBBBBBBB");
            default -> null;
        };
    }

    /** Head with the hat on it, centred on cx; cy is the vertical centre of the picture. */
    static void hat(GuiGraphics g, Cosmetics.Entry e, int cx, int cy) {
        int ox = cx - W * PX / 2, oy = cy - HALF_H;
        head(g, ox, oy);
        Sprite s = hatSprite(e.id());
        if (s != null) sprite(g, ox, oy, s, e);
    }

    // ------------------------------------------------------------------ glasses

    private static String[] lensShape(String id) {
        return switch (id) {
            case "black" -> new String[]{"ffff", "fgll", "flll", ".ff."};
            case "classic" -> new String[]{"ffff", "f..f", "f..f", "ffff"};
            case "round" -> new String[]{".ff.", "f..f", "f..f", ".ff."};
            case "aviator" -> new String[]{"ffff", "fgll", ".fll", "..f."};
            case "heart" -> new String[]{".l.f.", "fffff", ".fff.", "..f.."};
            default -> null;
        };
    }

    private static void drawRows(GuiGraphics g, int ox, int oy, int col0, int row0, String[] rows, Cosmetics.Entry e) {
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                if (ch == '.') continue;
                put(g, ox, oy, col0 + c, row0 + r, itemColor(ch, e));
            }
        }
    }

    /** Head with the glasses on it, centred on cx; cy is the vertical centre of the picture. */
    static void glasses(GuiGraphics g, Cosmetics.Entry e, int cx, int cy) {
        int ox = cx - W * PX / 2, oy = cy - HALF_H;
        head(g, ox, oy);
        int row0 = HEAD_ROW + 5; // the eyes of the head sprite are on rows 6 and 7
        if (e.id().equals("neon")) {
            for (int c = 4; c <= 15; c++) {
                put(g, ox, oy, c, row0, 0xFF0B0B10);
                put(g, ox, oy, c, row0 + 3, 0xFF0B0B10);
            }
            for (int r = 1; r <= 2; r++) {
                put(g, ox, oy, 4, row0 + r, 0xFF0B0B10);
                put(g, ox, oy, 15, row0 + r, 0xFF0B0B10);
                for (int c = 5; c <= 14; c++) put(g, ox, oy, c, row0 + r, mix(e.c1(), e.c2(), (c - 5) / 9f));
            }
            return;
        }
        String[] lens = lensShape(e.id());
        if (lens == null) return;
        boolean heart = e.id().equals("heart");
        drawRows(g, ox, oy, heart ? 4 : 5, row0, lens, e);
        drawRows(g, ox, oy, 11, row0, lens, e);
        int frame = itemColor('f', e);
        put(g, ox, oy, 9, row0 + 1, frame); // bridge
        put(g, ox, oy, 10, row0 + 1, frame);
        if (!heart) {
            put(g, ox, oy, 4, row0 + 1, frame); // temples
            put(g, ox, oy, 15, row0 + 1, frame);
        }
    }
}
