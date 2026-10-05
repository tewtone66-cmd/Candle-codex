package dev.candle.codex.ui.pages;

import dev.candle.codex.module.Module;
import dev.candle.codex.module.ModuleManager;
import dev.candle.codex.module.Setting;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.Anim;
import dev.candle.codex.ui.ColorPicker;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.HudEditorScreen;
import dev.candle.codex.ui.Page;
import dev.candle.codex.ui.ScrollBar;
import dev.candle.codex.ui.TextField;
import dev.candle.codex.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Module cards with categories, search and a per-module settings drawer. */
public final class ModulesPage extends Page {
    private static final String[] CATS = {"All", "HUD", "PvP", "Visual", "Utility"};
    private static final int CW = 150, CH = 58, GAP = 8, DRAWER = 232;

    private final TextField search = new TextField("Search modules and settings...");
    private final List<Module> shown = new ArrayList<>();
    private final ScrollBar bar = new ScrollBar();
    private int cat;
    private int scroll, maxScroll;
    private Module selected;
    private Setting editingColor;
    private final ColorPicker picker = new ColorPicker();
    private Setting dragNum;
    private int dragX, dragW;
    private int dScroll;

    /** Drawer open/close animation (0 closed .. 1 open) and the module it is still showing while it closes. */
    private float drawerAnim;
    private Module drawerMod;
    private long lastNs = System.nanoTime();

    public ModulesPage() {
        super("Modules");
        search.onChange = s -> rebuild();
    }

    @Override
    public boolean ownScroll() {
        return true;
    }

    @Override
    public void onShow() {
        rebuild();
        drawerAnim = selected != null ? 1f : 0f;
        drawerMod = selected;
    }

    private void rebuild() {
        shown.clear();
        String q = search.text.trim().toLowerCase();
        for (Module m : ModuleManager.ALL) {
            if (cat != 0 && !m.category.equals(CATS[cat])) continue;
            if (m.matches(q)) shown.add(m);
        }
        scroll = 0;
    }

    private int gridW() {
        return selected != null ? w - DRAWER - 10 : w;
    }

    private int cols() {
        return Math.max(1, (gridW() + GAP) / (CW + GAP));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        Theme t = ThemeManager.current();
        long nowNs = System.nanoTime();
        float sec = Math.min(0.1f, (nowNs - lastNs) / 1e9f);
        lastNs = nowNs;

        Draw.textShadow(g, "Modules", x + 4, y + 2, t.cAccent2);
        search.bounds(x + 86, y - 1, Math.min(190, w - 300));
        search.render(g, t);
        int ex = x + w - 74;
        Ui.button(g, t, "Edit HUD", ex, y - 1, 74, 18, Ui.in(mx, my, ex, y - 1, 74, 18), false);

        int cx = x;
        int cy = y + 24;
        for (int i = 0; i < CATS.length; i++) {
            int cwid = Draw.font().width(CATS[i]) + 18;
            Ui.chip(g, t, CATS[i], cx, cy, cwid, 16, i == cat, Ui.in(mx, my, cx, cy, cwid, 16));
            cx += cwid + 5;
        }

        int top = y + 46;
        int viewH = h - 46;
        int cols = cols();
        int rows = (shown.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (CH + GAP) - viewH + 4);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        g.enableScissor(x - 6, top, x + gridW() + 6, top + viewH);
        Anim.keyShift = scroll; // card hover animations keep their state while the grid scrolls
        for (int i = 0; i < shown.size(); i++) {
            Module m = shown.get(i);
            int px = x + (i % cols) * (CW + GAP) + 2;
            int py = top + (i / cols) * (CH + GAP) - scroll + 4;
            if (py + CH < top || py > top + viewH) continue;
            drawCard(g, t, m, px, py, mx, my, my >= top && my < top + viewH);
        }
        Anim.keyShift = 0;
        g.disableScissor();
        if (shown.isEmpty()) Draw.textC(g, "No modules match.", x + gridW() / 2, top + 30, t.cDim);

        bar.render(g, t, x + gridW() + 3, top + 2, viewH - 4, scroll, maxScroll, mx, my);

        // drawer animation: slides in from the right while fading, and slides out again when closed
        if (selected != null) drawerMod = selected;
        float target = selected != null ? 1f : 0f;
        drawerAnim += (target - drawerAnim) * Math.min(1f, sec * 14f);
        if (Math.abs(target - drawerAnim) < 0.01f) drawerAnim = target;
        if (drawerAnim > 0.01f && drawerMod != null) {
            float e = Anim.ease(drawerAnim);
            float savedMul = Draw.mul;
            Draw.mul = savedMul * e;
            g.pose().pushMatrix();
            g.pose().translate((1f - e) * (DRAWER * 0.45f), 0f);
            drawDrawer(g, t, drawerMod, mx, my);
            g.pose().popMatrix();
            Draw.mul = savedMul;
        } else if (selected == null) {
            drawerMod = null;
        }
    }

    private void drawCard(GuiGraphics g, Theme t, Module m, int px, int py, int mx, int my, boolean inView) {
        boolean hover = inView && Ui.in(mx, my, px, py, CW, CH);
        m.glow += ((m.enabled ? 1f : 0f) - m.glow) * 0.2f;
        Ui.card(g, t, px, py, CW, CH, hover, m.glow);
        Draw.text(g, Draw.fit(m.name, CW - 44), px + 8, py + 8, m.enabled ? t.cAccent2 : t.cText);
        Ui.toggle(g, t, px + CW - 34, py + 7, m.enabled, m.glow);
        Draw.text(g, Draw.fit(m.desc, CW - 14), px + 8, py + 22, t.cDim);
        if (m.warning != null) {
            Draw.text(g, "! " + Draw.fit(Component.translatable(m.warning).getString(), CW - 28), px + 8, py + 34, 0xE0A040);
        }
        boolean sh = inView && Ui.in(mx, my, px + 6, py + CH - 15, 62, 12);
        Draw.text(g, selected == m ? "Settings <" : "Settings >", px + 8, py + CH - 14, sh ? t.cAccent2 : t.cDim);
        Draw.textR(g, m.category, px + CW - 8, py + CH - 14, t.cDim);
    }

    // ------------------------------------------------------------ drawer

    private int drawerX() {
        return x + w - DRAWER;
    }

    private void drawDrawer(GuiGraphics g, Theme t, Module mod, int mx, int my) {
        int dx = drawerX(), dy = y + 24, dh = h - 24;
        Draw.rr(g, dx, dy, DRAWER, dh, t.radius, t.aPanel);
        Draw.ring(g, dx, dy, DRAWER, dh, t.radius, t.aBorder);
        Draw.textShadow(g, mod.name, dx + 10, dy + 9, t.cAccent2);
        Draw.text(g, "x", dx + DRAWER - 14, dy + 9, Ui.in(mx, my, dx + DRAWER - 18, dy + 5, 14, 14) ? t.cText : t.cDim);
        if (mod.settings.isEmpty()) {
            Draw.text(g, "No settings.", dx + 10, dy + 30, t.cDim);
            return;
        }
        int ry = dy + 28 - dScroll;
        int clipTop = dy + 24, clipBot = dy + dh - 4;
        g.enableScissor(dx, clipTop, dx + DRAWER, clipBot);
        int contentH = 0;
        int idx = 0;
        float baseMul = Draw.mul;
        for (Setting s : mod.settings) {
            int rh = rowHeight(s);
            // rows appear one after the other while the drawer opens
            float rp = Math.max(0f, Math.min(1f, drawerAnim * 1.6f - idx * 0.09f));
            Draw.mul = baseMul * Anim.ease(rp);
            drawSetting(g, t, s, dx + 10, ry + contentH, DRAWER - 20, mx, my);
            contentH += rh;
            idx++;
        }
        Draw.mul = baseMul;
        g.disableScissor();
        int total = contentH;
        int view = clipBot - clipTop;
        if (total > view) {
            int bh = Math.max(16, view * view / total);
            int by = clipTop + (int) ((view - bh) * (dScroll / (float) (total - view + 4)));
            Draw.rr(g, dx + DRAWER - 5, by, 2, bh, 1, 0x88000000 | t.cDim);
        }
    }

    private int rowHeight(Setting s) {
        if (s.type == Setting.Type.COLOR && s == editingColor) return 24 + 86;
        return 24;
    }

    private void drawSetting(GuiGraphics g, Theme t, Setting s, int px, int py, int pw, int mx, int my) {
        Draw.text(g, s.name, px, py + 4, t.cText);
        switch (s.type) {
            case BOOL -> Ui.toggle(g, t, px + pw - 26, py + 3, s.bool, s.bool ? 1f : 0f);
            case NUM -> {
                String v = s.step >= 1 ? Integer.toString(s.i()) : String.format("%.2f", s.num);
                Draw.textR(g, v, px + pw, py + 4, t.cAccent2);
                int sx = px + pw / 2 - 20, sw = pw / 2 + 20 - 34;
                float frac = (float) ((s.num - s.min) / (s.max - s.min));
                Ui.slider(g, t, sx, py + 14 - 6 + 4, sw, frac, dragNum == s);
            }
            case CHOICE -> {
                String name = s.choiceName();
                int bw = Draw.font().width(name) + 14;
                Ui.button(g, t, name, px + pw - bw, py + 1, bw, 16, Ui.in(mx, my, px + pw - bw, py + 1, bw, 16), false);
            }
            case COLOR -> {
                Draw.rr(g, px + pw - 26, py + 2, 26, 14, 4, 0xFF000000 | s.color);
                Draw.ring(g, px + pw - 26, py + 2, 26, 14, 4, s == editingColor ? 0xFF000000 | t.cAccent : t.aBorder);
                if (s == editingColor) {
                    picker.bounds(px, py + 22, pw, 76);
                    picker.render(g);
                }
            }
        }
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean click(double mx, double my, int button) {
        if (bar.click(mx, my)) {
            scroll = bar.scrollAt(my);
            return true;
        }
        boolean wasFocused = search.focused;
        search.click(mx, my);
        if (search.focused || wasFocused) {
            if (search.inside(mx, my)) return true;
        }
        int ex = x + w - 74;
        if (Ui.in(mx, my, ex, y - 1, 74, 18)) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new HudEditorScreen(mc.screen));
            return true;
        }
        int cx = x, cy = y + 24;
        for (int i = 0; i < CATS.length; i++) {
            int cwid = Draw.font().width(CATS[i]) + 18;
            if (Ui.in(mx, my, cx, cy, cwid, 16)) {
                if (cat != i) Draw.replayReveal(); // cards fade in again for the new category
                cat = i;
                rebuild();
                return true;
            }
            cx += cwid + 5;
        }
        if (selected != null && clickDrawer(mx, my)) return true;

        int top = y + 46, viewH = h - 46;
        if (my < top || my >= top + viewH) return false;
        int cols = cols();
        for (int i = 0; i < shown.size(); i++) {
            int px = x + (i % cols) * (CW + GAP) + 2;
            int py = top + (i / cols) * (CH + GAP) - scroll + 4;
            if (!Ui.in(mx, my, px, py, CW, CH)) continue;
            Module m = shown.get(i);
            boolean settingsHit = Ui.in(mx, my, px + 6, py + CH - 15, 62, 12);
            if (button == 1 || settingsHit) {
                selected = selected == m ? null : m;
                editingColor = null;
                dScroll = 0;
            } else {
                m.toggle();
            }
            return true;
        }
        return false;
    }

    private boolean clickDrawer(double mx, double my) {
        int dx = drawerX(), dy = y + 24;
        if (!Ui.in(mx, my, dx, dy, DRAWER, h - 24)) return false;
        if (Ui.in(mx, my, dx + DRAWER - 18, dy + 5, 14, 14)) {
            selected = null;
            editingColor = null;
            return true;
        }
        int ry = dy + 28 - dScroll;
        int px = dx + 10, pw = DRAWER - 20;
        for (Setting s : selected.settings) {
            int rh = rowHeight(s);
            if (my >= ry && my < ry + rh && my > dy + 24) {
                switch (s.type) {
                    case BOOL -> {
                        s.bool = !s.bool;
                        selected.touch();
                    }
                    case CHOICE -> {
                        s.choice = (s.choice + 1) % s.choices.length;
                        selected.touch();
                    }
                    case NUM -> {
                        int sx = px + pw / 2 - 20, sw = pw / 2 + 20 - 34;
                        if (mx >= sx - 4 && mx <= sx + sw + 4) {
                            dragNum = s;
                            dragX = sx;
                            dragW = sw;
                            setNum(s, mx);
                        }
                    }
                    case COLOR -> {
                        if (my < ry + 22) {
                            editingColor = editingColor == s ? null : s;
                            if (editingColor != null) {
                                picker.setColor(s.color);
                                final Setting target = s;
                                picker.onChange = c -> {
                                    target.color = c;
                                    selected.touch();
                                };
                            }
                        } else if (s == editingColor) {
                            picker.click(mx, my);
                        }
                    }
                }
                return true;
            }
            ry += rh;
        }
        return true;
    }

    private void setNum(Setting s, double mx) {
        double v = s.min + Ui.sliderFrac(mx, dragX, dragW) * (s.max - s.min);
        v = Math.round(v / s.step) * s.step;
        s.num = Math.max(s.min, Math.min(s.max, v));
        selected.touch();
    }

    @Override
    public void drag(double mx, double my) {
        if (bar.dragging()) scroll = bar.scrollAt(my);
        else if (dragNum != null) setNum(dragNum, mx);
        else if (picker.dragging()) picker.drag(mx, my);
    }

    @Override
    public void release() {
        bar.release();
        dragNum = null;
        picker.release();
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (selected != null && mx >= drawerX()) {
            int total = 0;
            for (Setting s : selected.settings) total += rowHeight(s);
            int view = h - 24 - 28;
            dScroll = Math.max(0, Math.min(Math.max(0, total - view + 4), dScroll - (int) (amount * 16)));
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (amount * 24)));
        return true;
    }

    @Override
    public boolean key(int key, int mods) {
        return search.key(key, mods);
    }

    @Override
    public boolean chr(int cp) {
        return search.chr(cp);
    }

    @Override
    public boolean typing() {
        return search.focused;
    }
}
