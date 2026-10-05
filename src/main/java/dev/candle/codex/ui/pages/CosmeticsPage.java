package dev.candle.codex.ui.pages;

import dev.candle.codex.config.Config;
import dev.candle.codex.cosmetics.AnimCapes;
import dev.candle.codex.cosmetics.Cosmetics;
import dev.candle.codex.cosmetics.Auras;
import dev.candle.codex.cosmetics.Glitchy;
import dev.candle.codex.cosmetics.Pets;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.ColorPicker;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Img;
import dev.candle.codex.ui.Page;
import dev.candle.codex.ui.TextField;
import dev.candle.codex.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pick a cape, hat, wings, glasses, pet and aura. Everything is drawn on your own player. */
public final class CosmeticsPage extends Page {
    // tab numbers: 0 capes, 1 hats, 2 wings, 3 glasses, 4 pets, 5 auras
    private static final String[] TABS = {"Capes", "Hats", "Wings", "Glasses", "Pets", "Aura"};
    private static final int CW = 92, CH = 112, GAP = 8;
    /** Space kept free on the right for the menu scrollbar, so cards never slide under it. */
    private static final int SCROLL_PAD = 14;
    private static final int PANEL_H = 100;
    private static final int GEAR = 14;
    private static final int[] AD_COLOURS = {
            0x161A34, 0x000000, 0xFFFFFF, 0xE03A3A, 0xFF8C1E, 0xFFD166, 0x2ECC71, 0x1FA2FF, 0x8C46DC, 0xFF5CA0};

    private final ColorPicker picker = new ColorPicker();
    private int tab;
    /** True while the Codex Flame settings (gear) panel is open. */
    private boolean flameOpen;
    /** True while the Dragon wing speed (gear) panel is open. */
    private boolean dragonOpen;
    /** True while the speed slider is being dragged. */
    private boolean dragSpeed;
    private static final float SPEED_MIN = 0.2f, SPEED_MAX = 3f;
    private static final int SPEED_PANEL_H = 64;
    /** Which cape the picker currently edits ("" when no panel). */
    private String pickerFor = "";

    /** One card: an entry together with the tab it belongs to (search and the pack view mix tabs). */
    private record Item(int tab, Cosmetics.Entry e) {}

    private final TextField search = new TextField("Search cosmetics...");
    /** True while only the Glitchy Cosmetics pack is shown. */
    private boolean glitchOnly;
    /** Cards on screen; rebuilt by rebuild() whenever the tab, the search text or the pack filter changes. */
    private List<Item> items = new ArrayList<>();

    public CosmeticsPage() {
        super("Cosmetics");
        search.max = 40;
        search.onChange = s -> rebuild();
        rebuild();
    }

    @Override
    public void onShow() {
        pickerFor = "";
        rebuild();
    }

    /** Shows only the Glitchy Cosmetics pack (the menu banner calls this). Clears the search text. */
    public void showGlitchPack() {
        glitchOnly = true;
        search.setText("");
        search.focused = false;
        pickerFor = "";
        rebuild();
    }

    /** True when the cards are not simply the entries of the current tab. */
    private boolean mixed() {
        return glitchOnly || !search.text.trim().isEmpty();
    }

    /** Rebuilds the card list: the current tab, or (search text / Glitchy filter) matching entries from every tab. */
    private void rebuild() {
        List<Item> l = new ArrayList<>();
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        if (!mixed()) {
            for (Cosmetics.Entry e : list(tab)) l.add(new Item(tab, e));
        } else {
            for (int t = 0; t < TABS.length; t++) {
                for (Cosmetics.Entry e : list(t)) {
                    if (e.id().equals("none")) continue;
                    if (glitchOnly && !Glitchy.is(e.id())) continue;
                    if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.id().contains(q)) continue;
                    l.add(new Item(t, e));
                }
            }
        }
        items = l;
    }

    private static List<Cosmetics.Entry> list(int tab) {
        return switch (tab) {
            case 0 -> Cosmetics.CAPES;
            case 1 -> Cosmetics.HATS;
            case 2 -> Cosmetics.WINGS;
            case 4 -> Pets.LIST;
            case 5 -> Auras.LIST;
            default -> Cosmetics.GLASSES;
        };
    }

    private static String selected(int tab) {
        return switch (tab) {
            case 0 -> Config.data.cosmetics.cape;
            case 1 -> Config.data.cosmetics.hat;
            case 2 -> Config.data.cosmetics.wings;
            case 4 -> Config.data.cosmetics.pet;
            case 5 -> Config.data.cosmetics.aura;
            default -> Config.data.cosmetics.glasses;
        };
    }

    private void select(int tab, String id) {
        switch (tab) {
            case 0 -> Config.data.cosmetics.cape = id;
            case 1 -> Config.data.cosmetics.hat = id;
            case 2 -> Config.data.cosmetics.wings = id;
            case 4 -> Config.data.cosmetics.pet = id;
            case 5 -> Config.data.cosmetics.aura = id;
            default -> Config.data.cosmetics.glasses = id;
        }
        Config.markDirty();
    }

    // ------------------------------------------------------------------ layout (one source of truth for render and click)

    private int usableW() {
        return Math.max(CW, w - SCROLL_PAD);
    }

    private int perRow() {
        return Math.max(1, (usableW() + GAP) / (CW + GAP));
    }

    private int cardX(int i) {
        return x + (i % perRow()) * (CW + GAP);
    }

    private int cardY(int i) {
        return y + 58 + (i / perRow()) * (CH + GAP);
    }

    private int gridBottom() {
        int n = items.size();
        int rows = (n + perRow() - 1) / perRow();
        return y + 58 + rows * (CH + GAP);
    }

    /** The cape whose colour panel is showing, or "" for none. The gear panel wins over the Codex Ad panel. */
    private String panelId() {
        if (tab != 0) return "";
        if (flameOpen) return "candleflame";
        return "ad".equals(Config.data.cosmetics.cape) ? "ad" : "";
    }

    private static int colorOf(String id) {
        return ("candleflame".equals(id) ? Config.data.cosmetics.flameColor : Config.data.cosmetics.adColor) & 0xFFFFFF;
    }

    private void setColor(String id, int rgb) {
        if ("candleflame".equals(id)) Config.data.cosmetics.flameColor = rgb & 0xFFFFFF;
        else Config.data.cosmetics.adColor = rgb & 0xFFFFFF;
        Config.markDirty();
        AnimCapes.rebuild(id);
    }

    /** The gear is only offered on the tab that owns the settings panel, so a mixed view never opens a panel that is not drawn. */
    private boolean hasGear(Item it) {
        return it.tab() == tab && ((tab == 0 && "candleflame".equals(it.e().id())) || (tab == 2 && "dragon".equals(it.e().id())));
    }

    // search box and pack button share the title row
    private int searchX() {
        return x + 96;
    }

    private int searchW() {
        return Math.max(60, Math.min(190, usableW() - 96 - 78));
    }

    private int packX() {
        return searchX() + searchW() + 8;
    }

    private static final int PACK_W = 66;

    private int speedX() {
        return x + 10;
    }

    private int speedW() {
        return Math.max(40, Math.min(220, usableW() - 90));
    }

    private void setSpeed(double mx) {
        float f = Ui.sliderFrac(mx, speedX(), speedW());
        float v = SPEED_MIN + f * (SPEED_MAX - SPEED_MIN);
        v = Math.round(v * 20f) / 20f;
        Config.data.cosmetics.dragonSpeed = Math.max(SPEED_MIN, Math.min(SPEED_MAX, v));
        Config.markDirty();
    }

    private void drawSpeedPanel(GuiGraphics g, Theme t, int mx, int my, int py) {
        int pw = usableW();
        Draw.rr(g, x, py, pw, SPEED_PANEL_H, t.radius, t.aPanel);
        Draw.ring(g, x, py, pw, SPEED_PANEL_H, t.radius, t.aBorder);
        Draw.text(g, "Dragon wing speed", x + 10, py + 8, t.cAccent2);
        int sx = speedX(), sy = py + 30, sw = speedW();
        float sp = Config.data.cosmetics.dragonSpeed;
        Ui.slider(g, t, sx, sy, sw, (sp - SPEED_MIN) / (SPEED_MAX - SPEED_MIN), dragSpeed || Ui.in(mx, my, sx - 4, sy - 4, sw + 8, 18));
        Draw.text(g, String.format(java.util.Locale.ROOT, "%.2fx", sp), sx + sw + 10, sy + 1, t.cText);
        Draw.text(g, Draw.fit("Drag to change how fast the dragon wings flap.", pw - 20), x + 10, py + 48, t.cDim);
    }

    private int gearX(int cx) {
        return cx + CW - GEAR - 4;
    }

    private int gearY(int cy) {
        return cy + CH - GEAR - 3;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        Theme t = ThemeManager.current();
        Draw.textShadow(g, "Cosmetics", x + 4, y + 2, t.cAccent2);
        search.bounds(searchX(), y - 2, searchW());
        search.render(g, t);
        glitchChip(g, t, mx, my);
        int tx = x;
        for (int i = 0; i < TABS.length; i++) {
            int tw = Draw.font().width(TABS[i]) + 22;
            Ui.chip(g, t, TABS[i], tx, y + 18, tw, 18, i == tab && !mixed(), Ui.in(mx, my, tx, y + 18, tw, 18));
            tx += tw + 5;
        }
        String note = mixed() ? (items.isEmpty() ? "Nothing found. Try another word, or press Glitchy to see the whole pack."
                : glitchOnly ? "Glitchy Cosmetics: one piece for every tab. Search narrows it down."
                : "Search results from all tabs. Click a tab to go back.")
                : tab == 0 ? "Capes are visible to you in third person (F5). Animated capes move in the menu and in the game."
                : tab == 5 ? "Auras float around you. Visible in third person (F5)."
                : tab == 4 ? "Pets sit on your shoulder; the Blue Butterfly circles your head. Visible in third person (F5)."
                : "Hats, glasses and wings are drawn on your own player (visible in third person, F5).";
        Draw.text(g, Draw.fit(note, usableW()), x + 2, y + 42, t.cDim);

        for (int i = 0; i < items.size(); i++) {
            int cx = cardX(i), cy = cardY(i);
            Item it = items.get(i);
            Cosmetics.Entry e = it.e();
            int tb = it.tab();
            boolean on = e.id().equals(selected(tb));
            boolean hover = Ui.in(mx, my, cx, cy, CW, CH);
            Ui.card(g, t, cx, cy, CW, CH, hover, on ? 1f : 0f);
            if (Glitchy.is(e.id())) glitchBackdrop(g, cx, cy, i);
            drawPreview(g, tb, e, cx + CW / 2, cy + 50, hover || on);
            if (Glitchy.is(e.id())) glitchTag(g, cx, cy);
            Draw.textC(g, Draw.fit(e.name(), CW - 8), cx + CW / 2, cy + CH - 22, on ? t.cAccent2 : t.cText);
            String sub = on ? "Selected" : ((tb == 0 && Cosmetics.animated(e.id())) || (tb == 4 && Pets.animated(e.id())) || (tb == 5 && Auras.animated(e.id()))) ? "Animated" : "Click to use";
            if (mixed() && !on) sub = TABS[tb] + (sub.equals("Animated") ? " · animated" : "");
            Draw.textC(g, sub, cx + CW / 2, cy + CH - 11, on ? t.cAccent : t.cDim);
            if (hasGear(it)) drawGear(g, t, gearX(cx), gearY(cy), Ui.in(mx, my, gearX(cx), gearY(cy), GEAR, GEAR), tab == 2 ? dragonOpen : flameOpen);
        }

        String pid = panelId();
        if (!pid.isEmpty()) {
            if (!pid.equals(pickerFor) && !picker.dragging()) {
                pickerFor = pid;
                picker.setColor(colorOf(pid));
            }
            drawPanel(g, t, gridBottom() + 2, pid);
        } else {
            pickerFor = "";
        }
        if (tab == 2 && dragonOpen) drawSpeedPanel(g, t, mx, my, gridBottom() + 2);
    }

    /** Button that switches the Glitchy Cosmetics pack view on and off; its label glitches while the animations run. */
    private void glitchChip(GuiGraphics g, Theme t, int mx, int my) {
        int bx = packX(), by = y - 2;
        boolean hover = Ui.in(mx, my, bx, by, PACK_W, 18);
        Ui.chip(g, t, "Glitchy", bx, by, PACK_W, 18, glitchOnly, hover);
        if (glitchFrame() && Config.data.animSpeed > 0f) {
            Draw.fill(g, bx + 3, by + 5, bx + PACK_W - 3, by + 6, 0x8800F0FF);
            Draw.fill(g, bx + 6, by + 11, bx + PACK_W - 2, by + 12, 0x88FF2DAA);
        }
    }

    /** True for a short moment now and then (about every 700 ms, 120 ms long); the pack cards use it for their glitches. */
    private static boolean glitchFrame() {
        long ms = System.currentTimeMillis();
        return (ms / 120L) % 6L == 0L;
    }

    /** Scan lines and a colour-split border behind the picture of a Glitchy card. Still when animations are off. */
    private static void glitchBackdrop(GuiGraphics g, int cx, int cy, int index) {
        boolean live = Config.data.animSpeed > 0f;
        long ms = System.currentTimeMillis();
        for (int ly = cy + 4 + (live ? (int) ((ms / 90L) % 4L) : 0); ly < cy + CH - 26; ly += 4) {
            Draw.fill(g, cx + 4, ly, cx + CW - 4, ly + 1, 0x1800F0FF);
        }
        if (live) {
            int bar = cy + 4 + (int) (((ms / 25L) + index * 17L) % (CH - 34));
            Draw.fill(g, cx + 4, bar, cx + CW - 4, bar + 2, 0x30FFFFFF);
        }
        int off = live && glitchFrame() ? 2 : 1;
        Draw.ring(g, cx - off, cy, CW, CH, 6, 0x6600F0FF);
        Draw.ring(g, cx + off, cy, CW, CH, 6, 0x66FF2DAA);
    }

    /** Small "GLITCH" label in the top left corner of a pack card, drawn with a colour split. */
    private static void glitchTag(GuiGraphics g, int cx, int cy) {
        int off = Config.data.animSpeed > 0f && glitchFrame() ? 2 : 1;
        Draw.text(g, "GLITCH", cx + 5 - off, cy + 5, 0xFF2DAA);
        Draw.text(g, "GLITCH", cx + 5 + off, cy + 5, 0x00F0FF);
        Draw.text(g, "GLITCH", cx + 5, cy + 5, 0xFFFFFF);
    }

    /** Small cog drawn from rectangles: a ring with 8 teeth. */
    private static void drawGear(GuiGraphics g, Theme t, int gx, int gy, boolean hover, boolean active) {
        int col = 0xFF000000 | (active ? t.cAccent2 : hover ? t.cText : t.cDim);
        int c = GEAR / 2;
        int cx = gx + c, cy = gy + c;
        Draw.rr(g, gx + 2, gy + 2, GEAR - 4, GEAR - 4, 5, col);
        Draw.fill(g, cx - 1, gy, cx + 1, gy + GEAR, col);
        Draw.fill(g, gx, cy - 1, gx + GEAR, cy + 1, col);
        Draw.fill(g, gx + 2, gy + 2, gx + 4, gy + 4, col);
        Draw.fill(g, gx + GEAR - 4, gy + 2, gx + GEAR - 2, gy + 4, col);
        Draw.fill(g, gx + 2, gy + GEAR - 4, gx + 4, gy + GEAR - 2, col);
        Draw.fill(g, gx + GEAR - 4, gy + GEAR - 4, gx + GEAR - 2, gy + GEAR - 2, col);
        Draw.rr(g, cx - 2, cy - 2, 4, 4, 2, 0xFF1A1A22);
    }

    private void drawPanel(GuiGraphics g, Theme t, int py, String id) {
        int pw = usableW();
        Draw.rr(g, x, py, pw, PANEL_H, t.radius, t.aPanel);
        Draw.ring(g, x, py, pw, PANEL_H, t.radius, t.aBorder);
        Draw.text(g, "candleflame".equals(id) ? "Codex Flame background" : "Codex Ad background", x + 10, py + 8, t.cAccent2);
        int cur = colorOf(id);
        for (int i = 0; i < AD_COLOURS.length; i++) {
            int sx = x + 10 + i * 22, sy = py + 24;
            Draw.rr(g, sx, sy, 16, 16, 8, 0xFF000000 | AD_COLOURS[i]);
            Draw.ring(g, sx, sy, 16, 16, 8, 0x55FFFFFF);
            if (cur == AD_COLOURS[i]) Draw.ring(g, sx - 2, sy - 2, 20, 20, 10, 0xFF000000 | t.cAccent2);
        }
        picker.bounds(x + 10, py + 50, 150, 40);
        picker.render(g);
        int shown = picker.dragging() ? picker.color() : cur;
        Draw.rr(g, x + 176, py + 50, 40, 40, 6, 0xFF000000 | shown);
        Draw.ring(g, x + 176, py + 50, 40, 40, 6, 0x66FFFFFF);
        int room = pw - 226 - 8;
        if (room > 40) {
            Draw.text(g, Draw.fit("Pick a colour, release to apply.", room), x + 226, py + 58, t.cDim);
            Draw.text(g, Draw.fit("Saved in your Codex config.", room), x + 226, py + 72, t.cDim);
        }
    }

    /** Preview centred at (cx, cy) inside a 92 x 100 card. */
    private void drawPreview(GuiGraphics g, int tab, Cosmetics.Entry e, int cx, int cy, boolean live) {
        // real 3D models (turning, animated); falls back to the flat pictures below if they are switched off
        if (!e.id().equals("none") && Model3D.draw(g, tab, e, cx, cy, live)) return;
        if (e.id().equals("none")) {
            Draw.rr(g, cx - 16, cy - 22, 32, 44, 5, 0x40FFFFFF);
            Draw.textC(g, "-", cx, cy - 4, 0x999999);
            return;
        }
        if (tab == 0) {
            if (Cosmetics.hasPreview(e.id())) {
                // pre-scaled 40 x 64 picture, drawn 1:1 so the edges stay clean
                Img.region(g, Cosmetics.capeFile(e.id() + "_prev"), cx - 20, cy - 32, 40, 64, 0f, 0f, 40, 64, 40, 64);
            } else {
                // outer face of the cape texture: 10 x 16 units at (1,1) of a 64 x 32 layout (any resolution)
                Img.region(g, Cosmetics.capeFile(Cosmetics.liveKey(e.id())), cx - 20, cy - 32, 40, 64, 1f, 1f, 10, 16, 64, 32);
            }
            Draw.ring(g, cx - 20, cy - 32, 40, 64, 2, 0x66FFFFFF);
        } else if (tab == 1) {
            HeadArt.hat(g, e, cx, cy);
        } else if (tab == 3) {
            HeadArt.glasses(g, e, cx, cy);
        } else if (tab == 4) {
            PetArt.draw(g, e, cx, cy);
        } else if (tab == 5) {
            AuraArt.draw(g, e, cx, cy);
        } else {
            drawWings(g, e, cx, cy, e.id().equals(selected(tab)));
        }
    }

    private static void drawWings(GuiGraphics g, Cosmetics.Entry e, int cx, int cy, boolean live) {
        WingArt.draw(g, e, cx, cy, live);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean click(double mx, double my, int button) {
        search.click(mx, my);
        if (search.focused) return true;
        if (Ui.in(mx, my, packX(), y - 2, PACK_W, 18)) {
            glitchOnly = !glitchOnly;
            rebuild();
            return true;
        }
        int tx = x;
        for (int i = 0; i < TABS.length; i++) {
            int tw = Draw.font().width(TABS[i]) + 22;
            if (Ui.in(mx, my, tx, y + 18, tw, 18)) {
                tab = i;
                glitchOnly = false;
                search.setText("");
                search.focused = false;
                rebuild();
                return true;
            }
            tx += tw + 5;
        }
        String pid = panelId();
        if (!pid.isEmpty()) {
            int py = gridBottom() + 2;
            for (int i = 0; i < AD_COLOURS.length; i++) {
                if (Ui.in(mx, my, x + 10 + i * 22, py + 24, 16, 16)) {
                    setColor(pid, AD_COLOURS[i]);
                    picker.setColor(AD_COLOURS[i]);
                    return true;
                }
            }
            if (picker.click(mx, my)) return true;
        }
        if (tab == 2 && dragonOpen) {
            int py = gridBottom() + 2;
            if (Ui.in(mx, my, speedX() - 4, py + 26, speedW() + 8, 18)) {
                dragSpeed = true;
                setSpeed(mx);
                return true;
            }
        }
        for (int i = 0; i < items.size(); i++) {
            int cx = cardX(i), cy = cardY(i);
            if (hasGear(items.get(i)) && Ui.in(mx, my, gearX(cx), gearY(cy), GEAR, GEAR)) {
                if (tab == 2) {
                    dragonOpen = !dragonOpen;
                } else {
                    flameOpen = !flameOpen;
                    pickerFor = "";
                }
                return true;
            }
            if (Ui.in(mx, my, cx, cy, CW, CH)) {
                select(items.get(i).tab(), items.get(i).e().id());
                return true;
            }
        }
        return false;
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

    @Override
    public void drag(double mx, double my) {
        if (dragSpeed) {
            setSpeed(mx);
            return;
        }
        if (picker.dragging()) picker.drag(mx, my);
    }

    @Override
    public void release() {
        dragSpeed = false;
        boolean was = picker.dragging();
        picker.release();
        if (was && !pickerFor.isEmpty()) setColor(pickerFor, picker.color());
    }
}
