package dev.candle.codex.module;

import dev.candle.codex.theme.Theme;
import dev.candle.codex.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A "LABEL value" pill. The value string and its pixel width are recomputed only every {@code interval} ms,
 * so a normal frame allocates nothing.
 */
public abstract class TextHud extends HudModule {
    private final String label;
    private final int interval;
    private String value = "";
    private long next;
    private int lw, vw;

    protected final Setting showLabel = add(Setting.bool("label", "Show label", true));
    protected final Setting themeColor = add(Setting.bool("themeColor", "Use theme colour", true));
    protected final Setting color = add(Setting.color("color", "Custom colour", 0xFFFFFF));
    protected final Setting shadow = add(Setting.bool("shadow", "Text shadow", false));

    protected TextHud(String id, String name, String desc, String category, String label, float x, float y, int interval) {
        super(id, name, desc, category, x, y);
        this.label = label;
        this.interval = interval;
    }

    protected abstract String compute(Minecraft mc);

    @Override
    protected void drawContent(GuiGraphics g, Minecraft mc, Theme t) {
        long now = System.currentTimeMillis();
        if (now >= next) {
            next = now + interval;
            Font f = mc.font;
            value = compute(mc);
            lw = showLabel.bool ? f.width(label) : 0;
            vw = f.width(value);
        }
        int pad = 4, gap = showLabel.bool ? 4 : 0;
        w = pad * 2 + lw + gap + vw;
        h = 16;
        int r = rounded.bool ? Math.max(2, t.radius / 2) : 0;
        if (background.bool) Draw.rr(g, 0, 0, w, h, r, t.aCard);
        boolean sh = shadow.bool;
        int x = pad;
        if (showLabel.bool) {
            if (sh) Draw.textShadow(g, label, x, 4, t.cDim);
            else Draw.text(g, label, x, 4, t.cDim);
            x += lw + gap;
        }
        int vc = themeColor.bool ? t.cAccent2 : color.color;
        if (sh) Draw.textShadow(g, value, x, 4, vc);
        else Draw.text(g, value, x, 4, vc);
    }
}
