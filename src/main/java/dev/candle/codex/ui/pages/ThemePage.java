package dev.candle.codex.ui.pages;

import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.ColorPicker;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Page;
import dev.candle.codex.ui.TextField;
import dev.candle.codex.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Theme editor: live preview, HSV picker + hex, roundness / opacity, presets, save / delete / import / export. */
public final class ThemePage extends Page {
    private static final String[] SLOTS = {"Background", "Panel", "Card", "Accent", "Accent 2", "Text", "Dim text", "Border"};

    private final ColorPicker picker = new ColorPicker();
    private final TextField hex = new TextField("#RRGGBB");
    private final TextField name = new TextField("Theme name");
    private int slot = 3;
    private int dragSlider = -1;
    private int sx, sw;
    private String message = "";
    private List<String> saved = List.of();
    private boolean updatingHex;

    public ThemePage() {
        super("Themes");
        hex.max = 7;
        hex.onChange = s -> {
            if (updatingHex) return;
            if (s.matches("#?[0-9A-Fa-f]{6}")) {
                int rgb = Theme.parse(s);
                setSlotColor(rgb);
                picker.setColor(rgb);
            }
        };
        picker.onChange = rgb -> {
            setSlotColor(rgb);
            syncHex(rgb);
        };
    }

    @Override
    public void onShow() {
        saved = ThemeManager.userThemes();
        loadSlot();
        name.setText(ThemeManager.current().name);
    }

    private String slotValue(int i) {
        Theme t = ThemeManager.current();
        return switch (i) {
            case 0 -> t.bg;
            case 1 -> t.panel;
            case 2 -> t.card;
            case 3 -> t.accent;
            case 4 -> t.accent2;
            case 5 -> t.text;
            case 6 -> t.dim;
            default -> t.border;
        };
    }

    private void setSlotColor(int rgb) {
        Theme t = ThemeManager.current();
        String v = Theme.hex(rgb);
        switch (slot) {
            case 0 -> t.bg = v;
            case 1 -> t.panel = v;
            case 2 -> t.card = v;
            case 3 -> t.accent = v;
            case 4 -> t.accent2 = v;
            case 5 -> t.text = v;
            case 6 -> t.dim = v;
            default -> t.border = v;
        }
        ThemeManager.changed();
    }

    private void loadSlot() {
        int rgb = Theme.parse(slotValue(slot));
        picker.setColor(rgb);
        syncHex(rgb);
    }

    private void syncHex(int rgb) {
        updatingHex = true;
        hex.setText(Theme.hex(rgb));
        updatingHex = false;
    }

    private int rightX() {
        return x + w / 2 + 6;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        Theme t = ThemeManager.current();
        Draw.textShadow(g, "Themes", x + 4, y + 2, t.cAccent2);
        Draw.text(g, "Current: " + t.name, x + 62, y + 2, t.cDim);
        int half = w / 2 - 6;

        // ---------------- left: preview + presets + saved
        drawPreview(g, t, x, y + 20, half, 112);
        String[] presets = {"Flame", "Midnight", "Frost"};
        int px = x;
        for (String p : presets) {
            int bw = Draw.font().width(p) + 20;
            Ui.button(g, t, p, px, y + 138, bw, 18, Ui.in(mx, my, px, y + 138, bw, 18), p.equals(t.name));
            px += bw + 5;
        }
        Draw.text(g, "Saved themes", x, y + 164, t.cDim);
        int ly = y + 176;
        if (saved.isEmpty()) Draw.text(g, "None yet. Save one on the right.", x, ly + 4, t.cDim);
        for (int i = 0; i < saved.size() && ly + 20 <= y + h; i++) {
            String s = saved.get(i);
            boolean hover = Ui.in(mx, my, x, ly, half, 18);
            Draw.rr(g, x, ly, half, 18, 4, hover ? t.aCardHover : t.aCard);
            Draw.text(g, Draw.fit(s, half - 30), x + 8, ly + 5, s.equals(t.name) ? t.cAccent2 : t.cText);
            boolean hx = Ui.in(mx, my, x + half - 18, ly, 18, 18);
            Draw.text(g, "x", x + half - 12, ly + 5, hx ? 0xE06060 : t.cDim);
            ly += 20;
        }

        // ---------------- right: editor
        int rx = rightX();
        int rw = w - (rx - x);
        int cx = rx, cy = y + 18;
        for (int i = 0; i < SLOTS.length; i++) {
            int cwid = Draw.font().width(SLOTS[i]) + 22;
            if (cx + cwid > rx + rw) {
                cx = rx;
                cy += 20;
            }
            boolean act = i == slot;
            Ui.chip(g, t, SLOTS[i], cx, cy, cwid, 17, act, Ui.in(mx, my, cx, cy, cwid, 17));
            Draw.rr(g, cx + 4, cy + 5, 7, 7, 3, 0xFF000000 | Theme.parse(slotValue(i)));
            cx += cwid + 4;
        }
        int pickY = cy + 26;
        picker.bounds(rx, pickY, rw - 74, 78);
        picker.render(g);
        hex.bounds(rx + rw - 66, pickY, 66);
        hex.render(g, t);
        Draw.rr(g, rx + rw - 66, pickY + 24, 66, 20, 5, 0xFF000000 | Theme.parse(slotValue(slot)));
        Draw.ring(g, rx + rw - 66, pickY + 24, 66, 20, 5, t.aBorder);

        int sy = pickY + 90;
        sx = rx + 74;
        sw = rw - 74 - 40;
        Draw.text(g, "Roundness", rx, sy, t.cText);
        Ui.slider(g, t, sx, sy - 3, sw, t.radius / 14f, dragSlider == 0);
        Draw.text(g, Integer.toString(t.radius), sx + sw + 8, sy, t.cAccent2);
        Draw.text(g, "Opacity", rx, sy + 20, t.cText);
        Ui.slider(g, t, sx, sy + 17, sw, (t.opacity - 0.4f) / 0.6f, dragSlider == 1);
        Draw.text(g, Math.round(t.opacity * 100) + "%", sx + sw + 8, sy + 20, t.cAccent2);

        int ay = sy + 40;
        name.bounds(rx, ay, rw - 130);
        name.render(g, t);
        Ui.button(g, t, "Save", rx + rw - 124, ay - 1, 40, 20, Ui.in(mx, my, rx + rw - 124, ay - 1, 40, 20), true);
        Ui.button(g, t, "Import", rx + rw - 80, ay - 1, 40, 20, Ui.in(mx, my, rx + rw - 80, ay - 1, 40, 20), false);
        Ui.button(g, t, "Export", rx + rw - 36, ay - 1, 36, 20, Ui.in(mx, my, rx + rw - 36, ay - 1, 36, 20), false);
        Draw.text(g, "Import reads JSON from the clipboard. Export copies it and writes a file.", rx, ay + 26, t.cDim);
        if (!message.isEmpty()) Draw.text(g, Draw.fit(message, rw), rx, ay + 40, t.cAccent2);
    }

    private void drawPreview(GuiGraphics g, Theme t, int px, int py, int pw, int ph) {
        Draw.rr(g, px, py, pw, ph, t.radius + 2, t.aBg);
        Draw.ring(g, px, py, pw, ph, t.radius + 2, t.aBorder);
        Draw.rr(g, px + 5, py + 5, 38, ph - 10, t.radius, t.aPanel);
        Draw.rr(g, px + 10, py + 12, 28, 6, 3, (0x50 << 24) | t.cAccent);
        Draw.rr(g, px + 10, py + 24, 28, 6, 3, 0x30FFFFFF & 0xFFFFFF | (0x30 << 24));
        for (int i = 0; i < 2; i++) {
            int cx = px + 50 + i * ((pw - 56) / 2);
            int cw = (pw - 56) / 2 - 5;
            boolean on = i == 0;
            Ui.card(g, t, cx, py + 8, cw, 44, false, on ? 1f : 0f);
            Draw.text(g, on ? "FPS" : "CPS", cx + 6, py + 14, on ? t.cAccent2 : t.cText);
            Draw.text(g, "preview", cx + 6, py + 28, t.cDim);
            Ui.toggle(g, t, cx + cw - 30, py + 13, on, on ? 1f : 0f);
        }
        Ui.button(g, t, "Button", px + 50, py + 62, 56, 18, false, true);
        Ui.button(g, t, "Button", px + 112, py + 62, 56, 18, false, false);
        Draw.text(g, "Text", px + 50, py + 90, t.cText);
        Draw.text(g, "dim text", px + 82, py + 90, t.cDim);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean click(double mx, double my, int button) {
        Theme t = ThemeManager.current();
        int half = w / 2 - 6;
        String[] presets = {"Flame", "Midnight", "Frost"};
        int px = x;
        for (String p : presets) {
            int bw = Draw.font().width(p) + 20;
            if (Ui.in(mx, my, px, y + 138, bw, 18)) {
                Theme n = ThemeManager.find(p);
                if (n != null) {
                    ThemeManager.setCurrent(n);
                    loadSlot();
                    name.setText(n.name);
                    message = "Loaded " + p;
                }
                return true;
            }
            px += bw + 5;
        }
        int ly = y + 176;
        for (int i = 0; i < saved.size() && ly + 20 <= y + h; i++) {
            if (Ui.in(mx, my, x, ly, half, 18)) {
                String s = saved.get(i);
                if (Ui.in(mx, my, x + half - 18, ly, 18, 18)) {
                    ThemeManager.delete(s);
                    saved = ThemeManager.userThemes();
                    message = "Deleted " + s;
                } else {
                    Theme n = ThemeManager.find(s);
                    if (n != null) {
                        ThemeManager.setCurrent(n);
                        loadSlot();
                        name.setText(n.name);
                        message = "Loaded " + s;
                    } else message = "Could not read " + s;
                }
                return true;
            }
            ly += 20;
        }

        int rx = rightX();
        int rw = w - (rx - x);
        int cx = rx, cy = y + 18;
        for (int i = 0; i < SLOTS.length; i++) {
            int cwid = Draw.font().width(SLOTS[i]) + 22;
            if (cx + cwid > rx + rw) {
                cx = rx;
                cy += 20;
            }
            if (Ui.in(mx, my, cx, cy, cwid, 17)) {
                slot = i;
                loadSlot();
                return true;
            }
            cx += cwid + 4;
        }
        int pickY = cy + 26;
        if (picker.click(mx, my)) return true;
        hex.click(mx, my);
        int sy = pickY + 90;
        if (Ui.in(mx, my, sx - 4, sy - 5, sw + 8, 14)) {
            dragSlider = 0;
            setSlider(mx);
            return true;
        }
        if (Ui.in(mx, my, sx - 4, sy + 15, sw + 8, 14)) {
            dragSlider = 1;
            setSlider(mx);
            return true;
        }
        int ay = sy + 40;
        name.click(mx, my);
        if (Ui.in(mx, my, rx + rw - 124, ay - 1, 40, 20)) {
            String n = ThemeManager.save(name.text);
            message = n == null ? "Could not save theme" : "Saved as " + n;
            saved = ThemeManager.userThemes();
            if (n != null) name.setText(n);
            return true;
        }
        if (Ui.in(mx, my, rx + rw - 80, ay - 1, 40, 20)) {
            try {
                Theme n = ThemeManager.importFromClipboard();
                ThemeManager.setCurrent(n);
                loadSlot();
                name.setText(n.name);
                message = "Imported " + n.name + " (press Save to keep it)";
            } catch (RuntimeException e) {
                message = "Clipboard does not contain a valid theme";
            }
            return true;
        }
        if (Ui.in(mx, my, rx + rw - 36, ay - 1, 36, 20)) {
            ThemeManager.export(t);
            message = "Copied to clipboard and saved in config/candle/themes/";
            return true;
        }
        return name.focused || hex.focused;
    }

    private void setSlider(double mx) {
        Theme t = ThemeManager.current();
        float f = Ui.sliderFrac(mx, sx, sw);
        if (dragSlider == 0) t.radius = Math.round(f * 14f);
        else t.opacity = 0.4f + f * 0.6f;
        ThemeManager.changed();
    }

    @Override
    public void drag(double mx, double my) {
        if (dragSlider >= 0) setSlider(mx);
        else if (picker.dragging()) picker.drag(mx, my);
    }

    @Override
    public void release() {
        dragSlider = -1;
        picker.release();
    }

    @Override
    public boolean key(int key, int mods) {
        return hex.key(key, mods) || name.key(key, mods);
    }

    @Override
    public boolean chr(int cp) {
        return hex.chr(cp) || name.chr(cp);
    }

    @Override
    public boolean typing() {
        return hex.focused || name.focused;
    }
}
