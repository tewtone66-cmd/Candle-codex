package dev.candle.codex.theme;

import dev.candle.codex.config.Config;

/**
 * Every colour used by the client lives here. Render code only reads the cached int fields.
 * JSON form uses "#RRGGBB" strings.
 */
public final class Theme {
    public String name = "Flame";
    public String bg = "#14100D";
    public String panel = "#1C1612";
    public String card = "#281F18";
    public String accent = "#FF9A2E";
    public String accent2 = "#FFC46B";
    public String text = "#F5E9D3";
    public String dim = "#A08F78";
    public String border = "#3F3024";
    public int radius = 8;
    public float opacity = 0.94f;

    // ---- cached values (rebuilt by rebuild()) ----
    public transient int cBg, cPanel, cCard, cAccent, cAccent2, cText, cDim, cBorder;
    public transient int aBg, aPanel, aCard, aCardHover, aBorder, aAccentSoft, aAccentGlow;

    public Theme() {
        rebuild();
    }

    public static int parse(String s) {
        try {
            String v = s.trim();
            if (v.startsWith("#")) v = v.substring(1);
            if (v.length() != 6) return 0xFF00FF;
            return Integer.parseInt(v, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return 0xFF00FF;
        }
    }

    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public void rebuild() {
        radius = Math.max(0, Math.min(14, radius));
        opacity = Math.max(0.4f, Math.min(1f, opacity));
        cBg = parse(bg);
        cPanel = parse(panel);
        cCard = parse(card);
        cAccent = parse(accent);
        cAccent2 = parse(accent2);
        cText = parse(text);
        cDim = parse(dim);
        cBorder = parse(border);
        int a = Math.round(opacity * 255f);
        aBg = (a << 24) | cBg;
        aPanel = (a << 24) | cPanel;
        aCard = (Math.round(opacity * 235f) << 24) | cCard;
        int hov = mix(cCard, cAccent, 0.12f);
        aCardHover = (Math.round(opacity * 245f) << 24) | hov;
        aBorder = (0xC0 << 24) | cBorder;
        aAccentSoft = (0x40 << 24) | cAccent;
        aAccentGlow = (0x14 << 24) | cAccent;
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bgc = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bgc - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public Theme copy() {
        Theme t = Config.GSON.fromJson(Config.GSON.toJson(this), Theme.class);
        t.rebuild();
        return t;
    }

    public static Theme flame() {
        return new Theme();
    }

    public static Theme midnight() {
        Theme t = new Theme();
        t.name = "Midnight";
        t.bg = "#0C0F1A";
        t.panel = "#121626";
        t.card = "#1A2036";
        t.accent = "#6C8CFF";
        t.accent2 = "#9DB4FF";
        t.text = "#E6EAF7";
        t.dim = "#8590AD";
        t.border = "#283050";
        t.rebuild();
        return t;
    }

    public static Theme frost() {
        Theme t = new Theme();
        t.name = "Frost";
        t.bg = "#0E1A1F";
        t.panel = "#13242B";
        t.card = "#1B323B";
        t.accent = "#5FD3F3";
        t.accent2 = "#A5ECFF";
        t.text = "#E8F8FC";
        t.dim = "#8AAAB4";
        t.border = "#285160";
        t.rebuild();
        return t;
    }

    public static boolean isPreset(String name) {
        return "Flame".equalsIgnoreCase(name) || "Midnight".equalsIgnoreCase(name) || "Frost".equalsIgnoreCase(name);
    }
}
