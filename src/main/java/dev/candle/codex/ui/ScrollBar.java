package dev.candle.codex.ui;

import dev.candle.codex.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** A slim vertical scrollbar that can be dragged with the mouse. Call render every frame, then click / scrollAt / release. */
public final class ScrollBar {
    private int tx, ty, th, thumbY, thumbH, max;
    private boolean shown, drag;
    private double grab;

    /** Draws the bar. x is the left edge of the track, y/h its vertical extent. Nothing is drawn when max is 0. */
    public void render(GuiGraphics g, Theme t, int x, int y, int h, int scroll, int maxScroll, double mx, double my) {
        shown = maxScroll > 0 && h > 30;
        max = maxScroll;
        if (!shown) {
            drag = false;
            return;
        }
        tx = x;
        ty = y;
        th = h;
        thumbH = Math.min(h, Math.max(22, Math.round(h * (h / (float) (h + maxScroll)))));
        float f = Math.max(0f, Math.min(1f, scroll / (float) maxScroll));
        thumbY = ty + Math.round((th - thumbH) * f);
        boolean hover = drag || Ui.in(mx, my, tx - 5, ty, 14, th);
        float a = Anim.to(Anim.key(9, tx, ty, th, 7), hover ? 1f : 0f, 14f);
        int bw = 4 + Math.round(a * 2f);
        int bx = tx - Math.round(a);
        Draw.rr(g, bx, ty, bw, th, bw / 2, (0x38 << 24) | (t.cBorder & 0xFFFFFF));
        int thumb = Anim.argb((0xA0 << 24) | (t.cDim & 0xFFFFFF), (0xFF << 24) | (t.cAccent & 0xFFFFFF), a);
        Draw.rr(g, bx, thumbY, bw, thumbH, bw / 2, thumb);
    }

    /** Forgets the last geometry, used when the page has no scrollbar. */
    public void hide() {
        shown = false;
        drag = false;
    }

    public boolean click(double mx, double my) {
        if (!shown || !Ui.in(mx, my, tx - 5, ty, 14, th)) return false;
        drag = true;
        grab = (my >= thumbY && my < thumbY + thumbH) ? my - thumbY : thumbH / 2.0;
        return true;
    }

    public boolean dragging() {
        return drag;
    }

    /** Scroll offset for a mouse position, using the grab point of the current drag. */
    public int scrollAt(double my) {
        int range = Math.max(1, th - thumbH);
        double f = (my - grab - ty) / range;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, f)) * max);
    }

    public void release() {
        drag = false;
    }
}
