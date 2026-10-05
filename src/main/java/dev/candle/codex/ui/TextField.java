package dev.candle.codex.ui;

import dev.candle.codex.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/** Themed single-line text input (own implementation so it follows the Theme and needs no vanilla widgets). */
public final class TextField {
    public int x, y, w, h = 18;
    public String text = "";
    public String hint = "";
    public boolean focused;
    public int max = 200;
    public Consumer<String> onChange = s -> {};
    public Runnable onEnter = () -> {};
    private int cursor;

    public TextField(String hint) {
        this.hint = hint;
    }

    public void setText(String s) {
        text = s == null ? "" : s;
        cursor = text.length();
    }

    public void bounds(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
    }

    public boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void render(GuiGraphics g, Theme t) {
        Draw.rr(g, x, y, w, h, Math.max(2, t.radius / 2), t.aPanel);
        Draw.ring(g, x, y, w, h, Math.max(2, t.radius / 2), focused ? (0xFF << 24) | t.cAccent : t.aBorder);
        Font f = Minecraft.getInstance().font;
        int inner = w - 10;
        if (text.isEmpty() && !focused) {
            Draw.text(g, Draw.fit(hint, inner), x + 5, y + (h - 8) / 2, t.cDim);
            return;
        }
        // scroll so the cursor is always visible
        String before = text.substring(0, cursor);
        int start = 0;
        while (start < cursor && f.width(before.substring(start)) > inner - 2) start++;
        String shown = f.plainSubstrByWidth(text.substring(start), inner);
        Draw.text(g, shown, x + 5, y + (h - 8) / 2, t.cText);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 5 + f.width(before.substring(start));
            Draw.fill(g, cx, y + 3, cx + 1, y + h - 3, 0xFF000000 | t.cAccent2);
        }
    }

    public boolean click(double mx, double my) {
        focused = inside(mx, my);
        if (focused) {
            Font f = Minecraft.getInstance().font;
            int rel = (int) mx - (x + 5);
            int pos = 0;
            while (pos < text.length() && f.width(text.substring(0, pos + 1)) <= rel) pos++;
            cursor = pos;
        }
        return focused;
    }

    private void changed() {
        onChange.accept(text);
    }

    /** @return true if the key was consumed */
    public boolean key(int key, int mods) {
        if (!focused) return false;
        boolean ctrl = (mods & 2) != 0 || (mods & 8) != 0;
        switch (key) {
            case 259 -> { // backspace
                if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                    changed();
                }
            }
            case 261 -> { // delete
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                    changed();
                }
            }
            case 263 -> cursor = Math.max(0, cursor - 1);
            case 262 -> cursor = Math.min(text.length(), cursor + 1);
            case 268 -> cursor = 0;
            case 269 -> cursor = text.length();
            case 257, 335 -> onEnter.run();
            case 256 -> focused = false;
            default -> {
                if (ctrl && key == 86) { // V
                    insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                } else if (ctrl && key == 65) { // A -> clear to allow quick replace
                    cursor = text.length();
                } else if (ctrl && key == 88) { // X
                    Minecraft.getInstance().keyboardHandler.setClipboard(text);
                    text = "";
                    cursor = 0;
                    changed();
                } else if (ctrl && key == 67) { // C
                    Minecraft.getInstance().keyboardHandler.setClipboard(text);
                } else {
                    return true; // swallow other keys while typing
                }
            }
        }
        return true;
    }

    public boolean chr(int cp) {
        if (!focused) return false;
        if (cp < 32 || cp == 127) return true;
        insert(new String(Character.toChars(cp)));
        return true;
    }

    private void insert(String s) {
        if (s == null || s.isEmpty()) return;
        s = s.replace("\r", "").replace("\n", "");
        if (text.length() + s.length() > max) s = s.substring(0, Math.max(0, max - text.length()));
        text = text.substring(0, cursor) + s + text.substring(cursor);
        cursor += s.length();
        changed();
    }
}
