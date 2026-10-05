package dev.candle.codex.ui;

import dev.candle.codex.CodexClient;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.pages.StorePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Codex lobby: Singleplayer, Multiplayer, the client menu and quick shortcuts to downloads and cosmetics.
 * Drawn in a virtual space of at least REF_W x REF_H so it fits every GUI size.
 * Animated: staggered entrance, floating embers with a light parallax, soft button lift and a small version / tips bar.
 */
public final class CodexLobbyScreen extends Screen {
    private static final String[] MAIN = {"Singleplayer", "Multiplayer", "Codex Client"};
    private static final String[] TILES = {"Mods", "Resource Packs", "Shaders", "Schematics", "Cosmetics"};
    private static final String[] TIPS = {
            "Press Right Shift in a world for the Codex menu",
            "Store: install mods, packs and shaders from Modrinth",
            "FPS Boost: one click presets for smoother games",
            "Themes: change every colour of the client",
            "Cosmetics: capes, hats and wings"
    };
    private static final float REF_W = 520f, REF_H = 290f;
    private final long start = System.currentTimeMillis();

    private float us = 1f;
    private int vw = 520, vh = 290;

    public CodexLobbyScreen() {
        super(Component.literal("Codex"));
    }

    @Override
    protected void init() {
        us = Math.min(1f, Math.min(width / REF_W, height / REF_H));
        vw = Math.round(width / us);
        vh = Math.round(height / us);
    }

    private int bw() {
        return Math.min(220, vw - 40);
    }

    private int mainY() {
        return Math.max(80, Math.min(vh / 2 - 30, vh - 200));
    }

    private int tileW() {
        return Math.min(96, (vw - 40 - 4 * 6) / 5);
    }

    private static String version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(CodexClient.ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    }

    /** Starts the entrance + hover lift of element idx. Pair with end(). */
    private float begin(GuiGraphics g, int idx, long now, long key, boolean hover) {
        float p = Anim.stagger((now - 250) / 1000f, idx, 0.06f, 0.35f);
        float lift = Anim.to(key, hover ? 1f : 0f, 14f);
        float saved = Draw.mul;
        Draw.mul = saved * p;
        g.pose().pushMatrix();
        g.pose().translate(0f, (1f - p) * 10f - lift * 1.5f);
        return saved;
    }

    private void end(GuiGraphics g, float saved) {
        g.pose().popMatrix();
        Draw.mul = saved;
    }

    @Override
    public void render(GuiGraphics g, int rawMx, int rawMy, float dt) {
        Theme t = ThemeManager.current();
        Draw.mul = 1f;
        int mx = Math.round(rawMx / us);
        int my = Math.round(rawMy / us);
        long now = System.currentTimeMillis() - start;
        float intro = Anim.ease(now / 900f);

        // background (real screen size): dark gradient and a slow breathing heat glow at the bottom
        Draw.vGradient(g, 0, 0, width, height, 0xFF000000 | Theme.mix(t.cBg, 0x000000, 0.55f), 0xFF000000 | Theme.mix(t.cBg, t.cAccent, 0.16f));
        int heatTop = height * 55 / 100;
        int heatA = Math.round((0x26 + (float) Math.sin(now / 1300.0) * 0x0C) * intro);
        Draw.vGradient(g, 0, heatTop, width, height - heatTop, t.cAccent & 0xFFFFFF, (heatA << 24) | (t.cAccent & 0xFFFFFF));

        g.pose().pushMatrix();
        g.pose().scale(us, us);
        Draw.mul = intro;
        embers(g, t, now, mx);

        // logo: floats gently, flame flickers and its glow breathes
        int cx = vw / 2;
        int ly = Math.max(10, mainY() - 74);
        int bob = Math.round((float) Math.sin(now / 1100.0) * 1.5f);
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) (ly + bob));
        g.pose().scale(2f, 2f);
        float flick = (float) (Math.sin(now / 140.0) * 0.8 + Math.sin(now / 57.0) * 0.3);
        int fx = Math.round(flick);
        int pulse = Math.round(2f + 2f * (float) Math.sin(now / 600.0));
        Draw.glow(g, -5, 2, 10, 14, 5, t.cAccent, 10 + pulse, 6);
        Draw.rr(g, fx - 4, 2, 8, 14, 4, 0xFF000000 | t.cAccent);
        Draw.rr(g, fx - 2, 7, 4, 9, 2, 0xFF000000 | t.cAccent2);
        Draw.fill(g, 0, 16, 1, 20, 0xFF000000 | t.cDim);
        Draw.rr(g, -5, 20, 10, 16, 2, 0xFF000000 | t.cText);
        g.pose().popMatrix();
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) (ly + 78 + bob));
        g.pose().scale(2f, 2f);
        Draw.textC(g, "Codex", 0, 0, t.cAccent2);
        g.pose().popMatrix();
        Draw.textC(g, "client", cx, ly + 98 + bob, t.cDim);
        Draw.mul = 1f;

        // main buttons
        int bw = bw(), bx = cx - bw / 2, by = mainY() + 40;
        for (int i = 0; i < MAIN.length; i++) {
            int y = by + i * 26;
            boolean hover = Ui.in(mx, my, bx, y, bw, 22);
            float s = begin(g, i, now, Anim.key(30 + i, bx, y, bw, 22), hover);
            Ui.button(g, t, MAIN[i], bx, y, bw, 22, hover, i == 2);
            end(g, s);
        }
        // shortcuts
        int tw = tileW(), total = tw * 5 + 4 * 6, tx = cx - total / 2, ty = by + 3 * 26 + 8;
        for (int i = 0; i < TILES.length; i++) {
            int x = tx + i * (tw + 6);
            boolean hover = Ui.in(mx, my, x, ty, tw, 20);
            float s = begin(g, 3 + i, now, Anim.key(40 + i, x, ty, tw, 20), hover);
            Ui.button(g, t, Draw.fit(TILES[i], tw - 8), x, ty, tw, 20, hover, false);
            end(g, s);
        }
        int sy = ty + 28;
        boolean hOpt = Ui.in(mx, my, cx - 106, sy, 100, 20);
        float s1 = begin(g, 8, now, Anim.key(50, cx - 106, sy, 100, 20), hOpt);
        Ui.button(g, t, "Options", cx - 106, sy, 100, 20, hOpt, false);
        end(g, s1);
        boolean hQuit = Ui.in(mx, my, cx + 6, sy, 100, 20);
        float s2 = begin(g, 9, now, Anim.key(51, cx + 6, sy, 100, 20), hQuit);
        Ui.button(g, t, "Quit", cx + 6, sy, 100, 20, hQuit, false);
        end(g, s2);

        newsBar(g, t, now, cx, sy);
        Draw.mul = 1f;
        g.pose().popMatrix();
    }

    /** Small bar at the bottom: client version on the left, tips that cross-fade every few seconds. */
    private void newsBar(GuiGraphics g, Theme t, long now, int cx, int sy) {
        int barY = Math.max(sy + 26, vh - 24);
        int bw = Math.min(380, vw - 40), bx = cx - bw / 2;
        float np = Anim.ease((now - 900) / 600f);
        if (np <= 0.01f) return;
        Draw.mul = np;
        Draw.rr(g, bx, barY, bw, 16, 8, t.aPanel);
        Draw.ring(g, bx, barY, bw, 16, 8, t.aBorder);
        String ver = "v" + version();
        int vwid = Draw.font().width(ver) + 14;
        Draw.rr(g, bx + 2, barY + 2, vwid, 12, 6, (0x40 << 24) | t.cAccent);
        Draw.textC(g, ver, bx + 2 + vwid / 2, barY + 4, t.cAccent2);
        int idx = (int) ((now / 5000) % TIPS.length);
        float ph = (now % 5000) / 5000f;
        float fa = ph < 0.1f ? ph / 0.1f : ph > 0.9f ? (1f - ph) / 0.1f : 1f;
        Draw.mul = np * Math.max(0.05f, fa);
        int room = bw - vwid - 14;
        Draw.textC(g, Draw.fit(TIPS[idx], room), bx + 2 + vwid + (bw - vwid - 2) / 2, barY + 4, t.cDim);
    }

    /** 40 embers in three depth layers: slower and dimmer far away, a light mouse parallax, twinkle and a soft halo up close. */
    private void embers(GuiGraphics g, Theme t, long now, int mx) {
        float par = (mx - vw / 2f) / vw; // -0.5 .. 0.5
        for (int i = 0; i < 40; i++) {
            long seed = i * 7919L + 13;
            int layer = (int) (seed % 3);
            float speed = 6f + layer * 7f + (seed % 9);
            float travel = (now / 1000f * speed + (seed % 500)) % (vh + 30);
            float x = ((seed * 37) % 1000) / 1000f * vw + (float) Math.sin(now / 900.0 + i) * (4 + layer * 3) - par * (6 + layer * 10);
            int py = vh - Math.round(travel);
            float life = 1f - travel / (vh + 30f);
            float twinkle = 0.65f + 0.35f * (float) Math.sin(now / 260.0 + i * 1.7);
            int a = Math.max(0, Math.min(255, Math.round(life * 160f * twinkle * (0.55f + layer * 0.25f))));
            int size = 1 + (layer == 2 ? 1 : 0) + (seed % 11 == 0 ? 1 : 0);
            int col = (i % 4 == 0 ? t.cAccent2 : t.cAccent) & 0xFFFFFF;
            int ix = Math.round(x);
            if (layer == 2 && size >= 2) {
                Draw.fill(g, ix - 1, py - 1, ix + size + 1, py + size + 1, ((a / 4) << 24) | col);
            }
            Draw.fill(g, ix, py, ix + size, py + size, (a << 24) | col);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x() / us, my = e.y() / us;
        Minecraft mc = Minecraft.getInstance();
        int cx = vw / 2, bw = bw(), bx = cx - bw / 2, by = mainY() + 40;
        for (int i = 0; i < MAIN.length; i++) {
            if (!Ui.in(mx, my, bx, by + i * 26, bw, 22)) continue;
            switch (i) {
                case 0 -> mc.setScreen(new SelectWorldScreen(this));
                case 1 -> mc.setScreen(new JoinMultiplayerScreen(this));
                default -> mc.setScreen(new MenuScreen(this));
            }
            return true;
        }
        int tw = tileW(), total = tw * 5 + 4 * 6, tx = cx - total / 2, ty = by + 3 * 26 + 8;
        for (int i = 0; i < TILES.length; i++) {
            if (!Ui.in(mx, my, tx + i * (tw + 6), ty, tw, 20)) continue;
            if (i == 4) {
                mc.setScreen(MenuScreen.open(this, MenuScreen.PAGE_COSMETICS));
            } else {
                StorePage.requestTab(i);
                mc.setScreen(MenuScreen.open(this, MenuScreen.PAGE_STORE));
            }
            return true;
        }
        int sy = ty + 28;
        if (Ui.in(mx, my, cx - 106, sy, 100, 20)) {
            mc.setScreen(new OptionsScreen(this, mc.options));
            return true;
        }
        if (Ui.in(mx, my, cx + 6, sy, 100, 20)) {
            mc.stop();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        return false; // no Esc on the title screen
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        // own background in render()
    }
}
