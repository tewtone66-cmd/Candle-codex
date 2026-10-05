package dev.candle.codex.ui;

import net.minecraft.client.gui.GuiGraphics;

/** One tab of the Codex menu. Coordinates given to every method are screen pixels. */
public abstract class Page {
    public final String title;
    protected int x, y, w, h;

    protected Page(String title) {
        this.title = title;
    }

    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void onShow() {}

    /** True when the page scrolls its own content. All other pages get automatic scrolling from the menu. */
    public boolean ownScroll() {
        return false;
    }

    public abstract void render(GuiGraphics g, int mx, int my);

    public boolean click(double mx, double my, int button) {
        return false;
    }

    public void drag(double mx, double my) {}

    public void release() {}

    public boolean scroll(double mx, double my, double amount) {
        return false;
    }

    public boolean key(int key, int mods) {
        return false;
    }

    public boolean chr(int cp) {
        return false;
    }

    /** True while a text field is capturing keyboard input (so the menu key does not close the menu). */
    public boolean typing() {
        return false;
    }
}
