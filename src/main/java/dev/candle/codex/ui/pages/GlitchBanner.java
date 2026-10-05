package dev.candle.codex.ui.pages;

import dev.candle.codex.config.Config;
import dev.candle.codex.cosmetics.Cosmetics;
import dev.candle.codex.cosmetics.Glitchy;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.Anim;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The Glitchy Cosmetics banner at the top of the Codex menu. MenuScreen draws it once (not every Page), keeps the
 * space free above the page content and sends the clicks here. It lives in the pages package because it shows the
 * pack with the same package-private Model3D pictures as the Cosmetics tab (scaled down).
 * Still when menu animations are off (Config.data.animSpeed == 0). The models are skipped if the banner is narrow.
 */
public final class GlitchBanner {
    /** Height of the banner in virtual menu pixels. */
    public static final int HEIGHT = 46;
    /** What hit() reports. */
    public static final int HIT_NONE = 0, HIT_BODY = 1, HIT_CLOSE = 2;

    private static final int CLOSE = 14, CLOSE_PAD = 5;
    private static final int CELL = 38;
    private static final int TEXT_MIN = 150;
    private static final float MODEL_SCALE = 0.42f;
    private static final int CYAN = 0x00F0FF, MAGENTA = 0xFF2DAA, DARK = 0x0B0B14;

    private GlitchBanner() {}

    /** False once the player closed the banner. */
    public static boolean visible() {
        return !Config.data.glitchBannerHidden;
    }

    /** Hides the banner for good (saved in the config; Settings brings it back). */
    public static void hide() {
        Config.data.glitchBannerHidden = true;
        Config.markDirty();
    }

    private static int closeX(int bx, int bw) {
        return bx + bw - CLOSE - CLOSE_PAD;
    }

    private static int closeY(int by) {
        return by + CLOSE_PAD;
    }

    /** What is under the mouse: the close button, the banner itself, or nothing. Coordinates are virtual menu pixels. */
    public static int hit(double mx, double my, int bx, int by, int bw) {
        if (!Ui.in(mx, my, bx, by, bw, HEIGHT)) return HIT_NONE;
        if (Ui.in(mx, my, closeX(bx, bw) - 2, closeY(by) - 2, CLOSE + 4, CLOSE + 4)) return HIT_CLOSE;
        return HIT_BODY;
    }

    private static int modelCount(int bw) {
        int room = bw - 12 - TEXT_MIN - CLOSE - CLOSE_PAD - 10;
        return Math.max(0, Math.min(Glitchy.PACK.size(), room / CELL));
    }

    public static void render(GuiGraphics g, int bx, int by, int bw, int mx, int my) {
        Theme t = ThemeManager.current();
        boolean live = Config.data.animSpeed > 0f;
        long ms = System.currentTimeMillis();
        boolean over = hit(mx, my, bx, by, bw) != HIT_NONE;
        float hv = Anim.to(Anim.key(72, bx, by, bw, HEIGHT), over ? 1f : 0f, 14f);
        int r = Math.max(4, t.radius);

        // body: dark plate with scan lines and a colour-split border (the border stays inside the banner)
        Draw.rr(g, bx + 1, by + 2, bw, HEIGHT, r, 0x30000000);
        Draw.rr(g, bx, by, bw, HEIGHT, r, 0xFF000000 | DARK);
        if (hv > 0.02f) Draw.rr(g, bx, by, bw, HEIGHT, r, (Math.round(hv * 0x14) << 24) | CYAN);
        int shift = live ? (int) ((ms / 90L) % 4L) : 0;
        for (int ly = by + 3 + shift; ly < by + HEIGHT - 3; ly += 4) {
            Draw.fill(g, bx + 4, ly, bx + bw - 4, ly + 1, 0x1800F0FF);
        }
        if (live) {
            int bar = by + 3 + (int) ((ms / 25L) % (HEIGHT - 8));
            Draw.fill(g, bx + 4, bar, bx + bw - 4, bar + 2, 0x24FFFFFF);
        }
        int off = live && (ms / 120L) % 6L == 0L ? 2 : 1;
        int ra = Math.round(0x55 + hv * 0x55);
        Draw.ring(g, bx + 2 - off, by, bw - 4, HEIGHT, r, (ra << 24) | CYAN);
        Draw.ring(g, bx + 2 + off, by, bw - 4, HEIGHT, r, (ra << 24) | MAGENTA);

        // text on the left
        int models = modelCount(bw);
        int tx = bx + 12;
        int modelsX0 = bx + bw - CLOSE - CLOSE_PAD - 8 - models * CELL;
        int textW = Math.max(40, modelsX0 - tx - 6);
        String title = Draw.font().width("GLITCHY COSMETICS") <= textW ? "GLITCHY COSMETICS" : "GLITCHY";
        int tOff = live && (ms / 120L) % 6L == 0L ? 2 : 1;
        Draw.text(g, Draw.fit(title, textW), tx - tOff, by + 7, MAGENTA);
        Draw.text(g, Draw.fit(title, textW), tx + tOff, by + 7, CYAN);
        Draw.text(g, Draw.fit(title, textW), tx, by + 7, 0xFFFFFF);
        Draw.text(g, Draw.fit("New pack: 6 animated pieces", textW), tx, by + 20, t.cText);
        Draw.text(g, Draw.fit("Click to see the whole pack", textW), tx, by + 32, Theme.mix(t.cDim, t.cAccent2, hv));

        // the pack as small turning 3D pictures (cape, hat, wings, glasses, pet, aura: the same order as the tabs)
        if (models > 0) {
            List<Cosmetics.Entry> pack = Glitchy.PACK;
            g.enableScissor(bx + 2, by + 2, bx + bw - 2, by + HEIGHT - 2);
            for (int i = 0; i < models; i++) {
                Cosmetics.Entry e = pack.get(i);
                int cx = modelsX0 + i * CELL + CELL / 2, cy = by + HEIGHT / 2;
                g.pose().pushMatrix();
                g.pose().translate((float) cx, (float) cy);
                g.pose().scale(MODEL_SCALE, MODEL_SCALE);
                boolean ok = Model3D.draw(g, i, e, 0, 0);
                g.pose().popMatrix();
                if (!ok) { // 3D pictures are switched off: two little squares in the pack colours
                    Draw.rr(g, cx - 8, cy - 6, 12, 12, 3, 0xFF000000 | e.c2());
                    Draw.rr(g, cx - 4, cy - 8, 12, 12, 3, 0xFF000000 | e.c1());
                }
            }
            g.disableScissor();
        }

        // close button
        int cx0 = closeX(bx, bw), cy0 = closeY(by);
        boolean hc = Ui.in(mx, my, cx0 - 2, cy0 - 2, CLOSE + 4, CLOSE + 4);
        Draw.rr(g, cx0, cy0, CLOSE, CLOSE, 4, hc ? 0x66FFFFFF : 0x22FFFFFF);
        Draw.textC(g, "x", cx0 + CLOSE / 2, cy0 + 3, hc ? 0xFFFFFF : t.cDim);
    }
}
