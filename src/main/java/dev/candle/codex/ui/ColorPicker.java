package dev.candle.codex.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntConsumer;

/** HSV picker: saturation/value square plus a hue slider. */
public final class ColorPicker {
    public int x, y, w, h;
    public final float[] hsv = {0f, 1f, 1f};
    public IntConsumer onChange = c -> {};
    private boolean dragSv, dragHue;
    private static final int HUE_W = 12, GAP = 6;

    public void setColor(int rgb) {
        float[] tmp = new float[3];
        Draw.toHsv(rgb, tmp);
        if (tmp[1] > 0.001f && tmp[2] > 0.001f) hsv[0] = tmp[0]; // keep hue for greys/black
        hsv[1] = tmp[1];
        hsv[2] = tmp[2];
    }

    public int color() {
        return Draw.hsv(hsv[0], hsv[1], hsv[2]);
    }

    public void bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    private int svW() {
        return w - HUE_W - GAP;
    }

    public void render(GuiGraphics g) {
        int sw = svW();
        // saturation (left->right) is drawn as columns, value (top->bottom) as the vertical gradient
        int step = 2;
        for (int cx = 0; cx < sw; cx += step) {
            float s = cx / (float) Math.max(1, sw - 1);
            int top = 0xFF000000 | Draw.hsv(hsv[0], s, 1f);
            Draw.vGradient(g, x + cx, y, Math.min(step, sw - cx), h, top, 0xFF000000);
        }
        int px = x + Math.round(hsv[1] * (sw - 1));
        int py = y + Math.round((1f - hsv[2]) * (h - 1));
        Draw.fill(g, px - 3, py - 1, px + 4, py + 2, 0xFF000000);
        Draw.fill(g, px - 1, py - 3, px + 2, py + 4, 0xFF000000);
        Draw.fill(g, px - 2, py, px + 3, py + 1, 0xFFFFFFFF);
        Draw.fill(g, px, py - 2, px + 1, py + 3, 0xFFFFFFFF);

        int hx = x + sw + GAP;
        int seg = 6;
        for (int i = 0; i < seg; i++) {
            int y1 = y + i * h / seg, y2 = y + (i + 1) * h / seg;
            Draw.vGradient(g, hx, y1, HUE_W, y2 - y1,
                    0xFF000000 | Draw.hsv(i / (float) seg, 1f, 1f), 0xFF000000 | Draw.hsv((i + 1) / (float) seg, 1f, 1f));
        }
        int hy = y + Math.round(hsv[0] * (h - 1));
        Draw.fill(g, hx - 2, hy - 1, hx + HUE_W + 2, hy + 2, 0xFF000000);
        Draw.fill(g, hx - 1, hy, hx + HUE_W + 1, hy + 1, 0xFFFFFFFF);
    }

    public boolean click(double mx, double my) {
        int sw = svW();
        if (mx >= x && mx < x + sw && my >= y && my < y + h) {
            dragSv = true;
            drag(mx, my);
            return true;
        }
        int hx = x + sw + GAP;
        if (mx >= hx - 3 && mx < hx + HUE_W + 3 && my >= y && my < y + h) {
            dragHue = true;
            drag(mx, my);
            return true;
        }
        return false;
    }

    public void drag(double mx, double my) {
        if (dragSv) {
            hsv[1] = clamp((float) ((mx - x) / Math.max(1, svW() - 1)));
            hsv[2] = 1f - clamp((float) ((my - y) / Math.max(1, h - 1)));
            onChange.accept(color());
        } else if (dragHue) {
            hsv[0] = Math.min(0.9999f, clamp((float) ((my - y) / Math.max(1, h - 1))));
            onChange.accept(color());
        }
    }

    public void release() {
        dragSv = false;
        dragHue = false;
    }

    public boolean dragging() {
        return dragSv || dragHue;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
