package dev.candle.codex.ui;

import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.pages.CosmeticsPage;
import dev.candle.codex.ui.pages.FpsPage;
import dev.candle.codex.ui.pages.GlitchBanner;
import dev.candle.codex.ui.pages.ModulesPage;
import dev.candle.codex.ui.pages.ProfilesPage;
import dev.candle.codex.ui.pages.SettingsPage;
import dev.candle.codex.ui.pages.StorePage;
import dev.candle.codex.ui.pages.ThemePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The main Codex menu: sidebar navigation, animated open/close (fade + slide), themed pages.
 * The whole menu is drawn in a virtual space that is never smaller than REF_W x REF_H, so it fits every GUI size
 * (Auto, 1, 2, 3, 4 ...). Pages that do not scroll themselves are scrolled automatically with a draggable scrollbar.
 * Every page is drawn with the Draw reveal wave (staggered fade in) and the Draw text guard (nothing leaves the page).
 * Settings can scale the menu (Config.data.uiScale) and speed the animations up or down (Config.data.animSpeed).
 * The Glitchy pack banner (GlitchBanner) is drawn here once, above every page; the pages are laid out below it, so it
 * never covers their content. Closing it is remembered in Config.data.glitchBannerHidden.
 */
public final class MenuScreen extends Screen {
    private static final int SIDEBAR = 132;
    private static final float REF_W = 660f, REF_H = 400f;
    /** Gap between the banner and the page content. */
    private static final int BANNER_GAP = 6;
    private static int lastPage = 0;

    private final Page[] pages = new Page[]{
            new ModulesPage(), new FpsPage(), new StorePage(), new CosmeticsPage(), new ThemePage(), new ProfilesPage(), new SettingsPage()
    };
    private final float[] hoverAnim = new float[pages.length];
    private final float[] hlAnim = new float[pages.length];
    private int current = lastPage;
    private float indY = lastPage; // animated position of the active-page highlight, in item units
    private float menuAge;          // animation seconds since the menu opened (drives the sidebar entrance)
    private int wx, wy, ww, wh;
    /** Space the banner takes above the pages (0 = no banner), the same value layoutPages() used. */
    private int bo;

    /** UI scale that keeps the virtual screen at least REF_W x REF_H, and the virtual screen size. */
    private float us = 1f;
    private int vw, vh;

    private final ScrollBar bar = new ScrollBar();
    private float scrollPos;
    private int scrollTarget, maxScroll, curScroll;

    private float anim; // 0..1
    private float pageAnim = 1f;
    private boolean closing;
    private long lastNs = System.nanoTime();

    public static final int PAGE_STORE = 2, PAGE_COSMETICS = 3;
    private final Screen parent;

    public MenuScreen() {
        this(null);
    }

    public MenuScreen(Screen parent) {
        super(Component.literal("Codex"));
        this.parent = parent;
    }

    /** Opens the menu on a given page (used by the lobby shortcuts). */
    public static MenuScreen open(Screen parent, int page) {
        lastPage = page;
        return new MenuScreen(parent);
    }

    private static boolean own(Page p) {
        return p instanceof ModulesPage || p instanceof StorePage || p.ownScroll();
    }

    @Override
    protected void init() {
        // never above what keeps REF_W x REF_H visible, so a big interface size is simply capped on small screens
        float fit = Math.min(width / REF_W, height / REF_H);
        float want = Config.data.uiScale;
        if (!(want >= 0.5f && want <= 1.5f)) want = 1f;
        us = Math.min(fit, want);
        vw = Math.round(width / us);
        vh = Math.round(height / us);
        ww = Math.min(vw - 24, 780);
        wh = Math.min(vh - 24, 470);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;
        layoutPages();
        pages[current].onShow();
        Draw.revealAge = -0.06f; // the page wave starts just after the window has begun to open
    }

    /** Lays the pages out below the banner (or from the top when there is no banner). */
    private void layoutPages() {
        bo = bannerFits() ? GlitchBanner.HEIGHT + BANNER_GAP : 0;
        for (Page p : pages) p.layout(wx + SIDEBAR + 12, wy + 12 + bo, ww - SIDEBAR - 24, wh - 24 - bo);
    }

    /** The banner is shown unless the player closed it or the window is too small to give the pages enough room. */
    private boolean bannerFits() {
        return GlitchBanner.visible() && ww - SIDEBAR - 24 >= 300 && wh >= 300;
    }

    /** Lays the menu out again (after the interface size changed in Settings, or the banner was switched on or off). */
    public void relayout() {
        init();
    }

    private static float ease(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    @Override
    public void render(GuiGraphics g, int rawMx, int rawMy, float dt) {
        long now = System.nanoTime();
        float sec = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float asec = sec * Anim.speed(); // animation time: faster or slower than real time, "Off" ends in one frame
        anim += (closing ? -1f : 1f) * asec / 0.18f;
        if (anim >= 1f) anim = 1f;
        if (closing && anim <= 0f) {
            finishClose();
            return;
        }
        menuAge += asec;
        float e = ease(Math.max(0f, anim));
        Theme t = ThemeManager.current();
        Draw.mul = e;
        int off = Math.round((1f - e) * 18f);
        int mx = Math.round(rawMx / us);
        int my = Math.round(rawMy / us) - off;

        g.fill(0, 0, width, height, ((int) (e * 0x99) << 24));
        g.pose().pushMatrix();
        g.pose().scale(us, us);
        g.pose().translate(0f, (float) off);

        // window: shadow, body, border
        Draw.rr(g, wx + 2, wy + 4, ww, wh, t.radius + 4, 0x55000000);
        Draw.rr(g, wx, wy, ww, wh, t.radius + 4, t.aBg);
        Draw.ring(g, wx, wy, ww, wh, t.radius + 4, t.aBorder);
        // sidebar
        Draw.rr(g, wx + 6, wy + 6, SIDEBAR - 6, wh - 12, t.radius, t.aPanel);
        drawLogo(g, t, wx + 6, wy + 12);

        // moving highlight behind the active page
        indY += (current - indY) * Math.min(1f, asec * 16f);
        if (Math.abs(current - indY) < 0.004f) indY = current;
        int hy = wy + 66 + Math.round(indY * 28f);
        int hr = Math.max(3, t.radius - 2);
        Draw.rr(g, wx + 12, hy, SIDEBAR - 18, 24, hr, ((int) (0x38) << 24) | t.cAccent);
        Draw.rr(g, wx + 12, hy + 5, 3, 14, 1, 0xFF000000 | t.cAccent);

        float k = Math.min(1f, asec * 15f);
        int ty = wy + 66;
        for (int i = 0; i < pages.length; i++) {
            boolean hover = Ui.in(mx, my, wx + 12, ty, SIDEBAR - 18, 24);
            hoverAnim[i] += ((i == current ? 1f : hover ? 0.55f : 0f) - hoverAnim[i]) * k;
            hlAnim[i] += ((hover && i != current ? 1f : 0f) - hlAnim[i]) * k;
            float a = hoverAnim[i];
            float p = Anim.stagger(menuAge - 0.05f, i, 0.04f, 0.25f); // entrance of this item
            float saved = Draw.mul;
            Draw.mul = saved * p;
            if (hlAnim[i] > 0.02f) {
                Draw.rr(g, wx + 12, ty, SIDEBAR - 18, 24, hr, ((int) (hlAnim[i] * 0x28) << 24) | t.cAccent);
            }
            int slide = Math.round((1f - p) * -12f);
            Draw.text(g, pages[i].title, wx + 24 + slide + Math.round(a * 2f), ty + 8, Theme.mix(t.cDim, t.cText, a));
            Draw.mul = saved;
            ty += 28;
        }
        Draw.text(g, "v" + version(), wx + 16, wy + wh - 22, t.cDim);
        Draw.text(g, "Press Esc to close", wx + 16, wy + wh - 32, t.cDim);

        // Glitchy pack banner: drawn once for every page, above the page area (the pages start below it)
        if (bo > 0) {
            float savedBanner = Draw.mul;
            Draw.mul = savedBanner * Anim.ease((menuAge - 0.12f) / 0.3f);
            GlitchBanner.render(g, wx + SIDEBAR + 12, wy + 12, ww - SIDEBAR - 24, mx, my);
            Draw.mul = savedBanner;
        }

        // page scroll state (pages that scroll themselves keep their own)
        Page cur = pages[current];
        boolean own = own(cur);
        if (own) {
            scrollPos = 0f;
            scrollTarget = 0;
            maxScroll = 0;
        } else {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
            scrollPos += (scrollTarget - scrollPos) * Math.min(1f, asec * 18f);
            if (Math.abs(scrollTarget - scrollPos) < 0.4f) scrollPos = scrollTarget;
        }
        curScroll = Math.round(scrollPos);

        pageAnim = Math.min(1f, pageAnim + asec / 0.22f);
        float pe = Anim.ease(pageAnim);
        float saved = Draw.mul;
        Draw.mul = saved * pe;
        int px = Math.round((1f - pe) * 16f);
        Draw.revealAge += asec;
        // the page may scroll up to just below the banner (2 px under it), never into it
        int clipTop = bo > 0 ? wy + 12 + bo - 4 : wy + 4;
        g.enableScissor(wx + SIDEBAR + 4, clipTop, wx + ww - 4, wy + wh - 4);
        g.pose().pushMatrix();
        g.pose().translate((float) px, (float) -curScroll);
        Draw.track = !own;
        Draw.maxY = Integer.MIN_VALUE;
        Draw.reveal = Draw.revealAge < 1.0f;
        Draw.revealX = cur.x;
        Draw.revealY = cur.y;
        Draw.clipLeft = cur.x;
        Draw.clipRight = cur.x + cur.w;
        try {
            cur.render(g, mx - px, my + curScroll);
        } finally {
            Draw.reveal = false;
            Draw.clipLeft = -100000;
            Draw.clipRight = Integer.MAX_VALUE;
            Anim.keyShift = 0;
            Draw.track = false;
            g.pose().popMatrix();
            g.disableScissor();
            Draw.mul = saved;
        }

        if (own) {
            bar.hide();
        } else {
            int over = Draw.maxY == Integer.MIN_VALUE ? 0 : Draw.maxY - (cur.y + cur.h);
            maxScroll = over > 2 ? over + 10 : 0;
            bar.render(g, t, wx + ww - 10, wy + 14 + bo, wh - 28 - bo, curScroll, maxScroll, mx, my);
        }

        g.pose().popMatrix();
        Draw.mul = 1f;
    }

    private static String version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(CodexClient.ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    }

    /** A little candle: wax body, wick and a two-layer flame that flickers with time. */
    private void drawLogo(GuiGraphics g, Theme t, int x, int y) {
        int cx = x + 22;
        long ms = System.currentTimeMillis();
        float flick = (float) (Math.sin(ms / 140.0) * 0.8 + Math.sin(ms / 53.0) * 0.3);
        int fx = cx + Math.round(flick);
        Draw.glow(g, cx - 5, y + 2, 10, 14, 5, t.cAccent, 10, 5);
        Draw.rr(g, fx - 4, y + 2, 8, 14, 4, 0xFF000000 | t.cAccent);
        Draw.rr(g, fx - 2, y + 7, 4, 9, 2, 0xFF000000 | t.cAccent2);
        Draw.fill(g, cx, y + 16, cx + 1, y + 20, 0xFF000000 | t.cDim);
        Draw.rr(g, cx - 5, y + 20, 10, 18, 2, 0xFF000000 | t.cText);
        Draw.rr(g, cx - 5, y + 20, 10, 3, 2, 0xFF000000 | Theme.mix(t.cText, t.cAccent2, 0.4f));
        Draw.textShadow(g, "Codex", x + 38, y + 10, t.cAccent2);
        Draw.text(g, "client", x + 38, y + 21, t.cDim);
    }

    // ---------------------------------------------------------------- input

    /** Screen x to virtual x. */
    private double lx(double x) {
        return x / us;
    }

    /** Screen y to virtual y, including the open animation offset. */
    private double ly(double y) {
        float e = ease(Math.max(0f, anim));
        return y / us - Math.round((1f - e) * 18f);
    }

    /** Switches to page i (does nothing when it is already shown). */
    private void switchTo(int i) {
        if (i == current) return;
        current = i;
        lastPage = i;
        pageAnim = 0f;
        Draw.revealAge = 0f;
        scrollPos = 0f;
        scrollTarget = 0;
        maxScroll = 0;
        curScroll = 0;
        bar.hide();
        pages[i].onShow();
    }

    /** Banner click: the Cosmetics tab with only the Glitchy pack showing. */
    private void openGlitchPack() {
        switchTo(PAGE_COSMETICS);
        if (pages[PAGE_COSMETICS] instanceof CosmeticsPage cp) cp.showGlitchPack();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = lx(e.x()), my = ly(e.y());
        int ty = wy + 66;
        for (int i = 0; i < pages.length; i++) {
            if (Ui.in(mx, my, wx + 12, ty, SIDEBAR - 18, 24)) {
                switchTo(i);
                return true;
            }
            ty += 28;
        }
        if (bo > 0) {
            int hit = GlitchBanner.hit(mx, my, wx + SIDEBAR + 12, wy + 12, ww - SIDEBAR - 24);
            if (hit == GlitchBanner.HIT_CLOSE) {
                GlitchBanner.hide();
                layoutPages();
                return true;
            }
            if (hit == GlitchBanner.HIT_BODY) {
                openGlitchPack();
                return true;
            }
        }
        if (!own(pages[current]) && bar.click(mx, my)) {
            scrollTarget = bar.scrollAt(my);
            scrollPos = scrollTarget;
            return true;
        }
        if (pages[current].click(mx, my + curScroll, e.button())) return true;
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        double mx = lx(e.x()), my = ly(e.y());
        if (bar.dragging()) {
            scrollTarget = bar.scrollAt(my);
            scrollPos = scrollTarget;
            return true;
        }
        pages[current].drag(mx, my + curScroll);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        bar.release();
        pages[current].release();
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double vx = lx(mx), vy = ly(my);
        if (pages[current].scroll(vx, vy + curScroll, sy)) return true;
        if (!own(pages[current]) && maxScroll > 0) {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (int) Math.round(sy * 32)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (pages[current].key(e.key(), e.modifiers())) return true;
        if (!pages[current].typing() && !own(pages[current]) && maxScroll > 0) {
            int k = e.key();
            if (k == 266) { // Page Up
                scrollTarget = Math.max(0, scrollTarget - 200);
                return true;
            }
            if (k == 267) { // Page Down
                scrollTarget = Math.min(maxScroll, scrollTarget + 200);
                return true;
            }
            if (k == 268) { // Home
                scrollTarget = 0;
                return true;
            }
            if (k == 269) { // End
                scrollTarget = maxScroll;
                return true;
            }
        }
        if (e.key() == 256 || (!pages[current].typing() && CodexClient.openMenuKey.matches(e))) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (pages[current].chr(e.codepoint())) return true;
        return super.charTyped(e);
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            Config.save();
        }
    }

    private void finishClose() {
        Draw.mul = 1f;
        Draw.reveal = false;
        Draw.clipRight = Integer.MAX_VALUE;
        Draw.clipLeft = -100000;
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        // we draw our own dimmed backdrop in render()
    }
}
