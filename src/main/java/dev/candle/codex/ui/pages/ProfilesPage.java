package dev.candle.codex.ui.pages;

import dev.candle.codex.config.Config;
import dev.candle.codex.config.ProfileManager;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Page;
import dev.candle.codex.ui.TextField;
import dev.candle.codex.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Quick-switch profiles (e.g. PvP and Build). */
public final class ProfilesPage extends Page {
    private final TextField name = new TextField("New profile name");
    private List<String> profiles = List.of();
    private String message = "";

    public ProfilesPage() {
        super("Profiles");
    }

    @Override
    public void onShow() {
        profiles = ProfileManager.list();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        Theme t = ThemeManager.current();
        Draw.textShadow(g, "Profiles", x + 4, y + 2, t.cAccent2);
        Draw.text(g, "Active: " + Config.data.profile, x + 70, y + 2, t.cDim);
        Draw.text(g, "A profile stores which modules are on, their settings, HUD positions and FPS Boost options.", x + 4, y + 18, t.cDim);

        name.bounds(x, y + 36, Math.min(220, w - 120));
        name.render(g, t);
        int bx = name.x + name.w + 8;
        Ui.button(g, t, "Save current", bx, y + 35, 90, 20, Ui.in(mx, my, bx, y + 35, 90, 20), true);
        if (!message.isEmpty()) Draw.text(g, Draw.fit(message, w), x, y + 62, t.cAccent2);

        int ly = y + 80;
        int cw = Math.min(w, 420);
        for (String p : profiles) {
            if (ly + 30 > y + h) break;
            boolean active = p.equals(Config.data.profile);
            boolean hover = Ui.in(mx, my, x, ly, cw, 28);
            Ui.card(g, t, x, ly, cw, 28, hover, active ? 1f : 0f);
            Draw.text(g, Draw.fit(p, cw - 150), x + 10, ly + 10, active ? t.cAccent2 : t.cText);
            Ui.button(g, t, "Load", x + cw - 108, ly + 4, 46, 20, Ui.in(mx, my, x + cw - 108, ly + 4, 46, 20), false);
            Ui.button(g, t, "Delete", x + cw - 58, ly + 4, 50, 20, Ui.in(mx, my, x + cw - 58, ly + 4, 50, 20), false);
            ly += 34;
        }
    }

    @Override
    public boolean click(double mx, double my, int button) {
        name.click(mx, my);
        int bx = name.x + name.w + 8;
        if (Ui.in(mx, my, bx, y + 35, 90, 20)) {
            String n = ProfileManager.saveCurrent(name.text.isBlank() ? Config.data.profile : name.text);
            message = n == null ? "Could not save profile" : "Saved profile " + n;
            profiles = ProfileManager.list();
            return true;
        }
        int ly = y + 80;
        int cw = Math.min(w, 420);
        for (String p : profiles) {
            if (ly + 30 > y + h) break;
            if (Ui.in(mx, my, x + cw - 108, ly + 4, 46, 20)) {
                message = ProfileManager.load(p) ? "Loaded " + p : "Could not load " + p;
                return true;
            }
            if (Ui.in(mx, my, x + cw - 58, ly + 4, 50, 20)) {
                ProfileManager.delete(p);
                profiles = ProfileManager.list();
                message = "Deleted " + p;
                return true;
            }
            ly += 34;
        }
        return name.focused;
    }

    @Override
    public boolean key(int key, int mods) {
        return name.key(key, mods);
    }

    @Override
    public boolean chr(int cp) {
        return name.chr(cp);
    }

    @Override
    public boolean typing() {
        return name.focused;
    }
}
