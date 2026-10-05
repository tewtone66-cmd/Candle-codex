package dev.candle.codex.cosmetics;

import dev.candle.codex.config.Config;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.ui.Draw;
import net.minecraft.client.gui.GuiGraphics;

/** The animated candle badge: a round plate (colour configurable) with a flickering candle on it. */
public final class Badge {
    /** Colour choices for the plate; -1 means "use the theme accent". */
    public static final int[] COLOURS = {0xFFFFFF, 0x1B1B22, 0xFF9A2E, 0x3CAAFF, 0xFF6FA8, 0x56E39F, 0xB07CFF, 0xFFD166, -1};

    private Badge() {}

    public static int plate(Theme t) {
        int c = Config.data.badgeColor;
        return c < 0 ? t.cAccent : c;
    }

    /** Draws the badge with its top-left corner at (x, y), size x size pixels. */
    public static void draw(GuiGraphics g, Theme t, int x, int y, int size) {
        float sway = (float) Math.sin(System.currentTimeMillis() / 130.0);
        float grow = (float) Math.sin(System.currentTimeMillis() / 210.0);
        int fx = Math.round(sway * 0.8f);
        int plate = plate(t);
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(size / 18f, size / 18f);
        Draw.rr(g, 0, 0, 18, 18, 9, 0xFF000000 | plate);
        Draw.ring(g, 0, 0, 18, 18, 9, 0x33000000);
        // wax
        Draw.rr(g, 6, 9, 7, 7, 2, 0xFFF6ECD6);
        Draw.fill(g, 7, 10, 8, 15, 0xFFFFFAEC);
        // wick
        Draw.fill(g, 9, 7, 10, 9, 0xFF463729);
        // flame
        int top = grow > 0.3f ? 1 : 2;
        Draw.rr(g, 7 + fx, top, 5, 7 + (2 - top), 2, 0xFFFF9628);
        Draw.rr(g, 8 + fx, top + 2, 3, 5 + (2 - top), 1, 0xFFFFD666);
        g.pose().popMatrix();
    }
}
