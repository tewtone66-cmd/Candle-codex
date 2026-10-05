package dev.candle.codex.ui.pages;

import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import dev.candle.codex.cosmetics.Badge;
import dev.candle.codex.module.HudModule;
import dev.candle.codex.module.ModuleManager;
import dev.candle.codex.perf.FpsBoost;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.HudEditorScreen;
import dev.candle.codex.ui.MenuScreen;
import dev.candle.codex.ui.Page;
import dev.candle.codex.ui.TextField;
import dev.candle.codex.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/** General settings: menu key, HUD tools, Glitchy banner, animation speed, interface size, Modrinth contact, reset, instance info. */
public final class SettingsPage extends Page {
    // row offsets from the top of the page (render and click must agree, so they live here)
    private static final int R_KEY = 24, R_HUD = 54, R_NAME = 84, R_BADGE = 114, R_ANIM = 148, R_SIZE = 178, R_RESET = 208;
    private static final int R_CONTACT = 252, R_FOLDER = 302, R_INFO = 346;

    private static final String[] ANIM_NAMES = {"Off", "Slow", "Normal", "Fast", "Max"};
    private static final float[] ANIM_VALUES = {0f, 0.6f, 1f, 1.6f, 2.5f};
    private static final String[] SIZE_NAMES = {"80%", "90%", "100%", "115%", "130%"};
    private static final float[] SIZE_VALUES = {0.8f, 0.9f, 1f, 1.15f, 1.3f};
    private static final int CHIP_W = 46, CHIP_STEP = 50, CHIP_H = 16;
    private static final long CONFIRM_MS = 6000;

    private final TextField contact = new TextField("optional: your email or Discord for the Modrinth User-Agent");
    private String message = "";
    private int resetStep; // 0 = idle, 1 = waiting for the confirmation click
    private long resetAt;

    public SettingsPage() {
        super("Settings");
        contact.max = 80;
        contact.onChange = s -> {
            Config.data.contact = s;
            Config.markDirty();
        };
    }

    @Override
    public void onShow() {
        contact.setText(Config.data.contact);
        resetStep = 0;
    }

    private void chips(GuiGraphics g, Theme t, int mx, int my, int cy, String[] names, float[] values, float current) {
        for (int i = 0; i < names.length; i++) {
            int cx = x + 120 + i * CHIP_STEP;
            Ui.chip(g, t, names[i], cx, cy + 1, CHIP_W, CHIP_H, Math.abs(current - values[i]) < 0.05f, Ui.in(mx, my, cx, cy + 1, CHIP_W, CHIP_H));
        }
    }

    private int chipAt(double mx, double my, int cy, int count) {
        for (int i = 0; i < count; i++) {
            if (Ui.in(mx, my, x + 120 + i * CHIP_STEP, cy + 1, CHIP_W, CHIP_H)) return i;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        Theme t = ThemeManager.current();
        Draw.textShadow(g, "Settings", x + 4, y + 2, t.cAccent2);
        if (!message.isEmpty()) Draw.text(g, Draw.fit(message, w - 100), x + 90, y + 2, t.cAccent2);
        if (resetStep == 1 && System.currentTimeMillis() - resetAt > CONFIRM_MS) resetStep = 0;

        int cy = y + R_KEY;
        Draw.text(g, "Menu key", x + 4, cy + 5, t.cText);
        String key = CodexClient.openMenuKey.getTranslatedKeyMessage().getString();
        Draw.text(g, key, x + 120, cy + 5, t.cAccent2);
        Ui.button(g, t, "Change key", x + 220, cy, 80, 18, Ui.in(mx, my, x + 220, cy, 80, 18), false);
        Draw.text(g, "Codex lobby", x + 330, cy + 5, t.cText);
        Ui.toggle(g, t, x + 420, cy + 3, Config.data.lobby, Config.data.lobby ? 1f : 0f);

        cy = y + R_HUD;
        Draw.text(g, "HUD layout", x + 4, cy + 5, t.cText);
        Ui.button(g, t, "Open editor", x + 120, cy, 90, 18, Ui.in(mx, my, x + 120, cy, 90, 18), false);
        Ui.button(g, t, "Reset positions", x + 216, cy, 100, 18, Ui.in(mx, my, x + 216, cy, 100, 18), false);
        boolean banner = !Config.data.glitchBannerHidden;
        Draw.text(g, "Glitchy banner", x + 330, cy + 5, t.cText);
        Ui.toggle(g, t, x + 420, cy + 3, banner, banner ? 1f : 0f);

        cy = y + R_NAME;
        Draw.text(g, "Show my name (F5)", x + 4, cy + 5, t.cText);
        Ui.toggle(g, t, x + 120, cy + 3, Config.data.ownName, Config.data.ownName ? 1f : 0f);
        Draw.text(g, "Codex badge by my name", x + 190, cy + 5, t.cText);
        Ui.toggle(g, t, x + 330, cy + 3, Config.data.badge, Config.data.badge ? 1f : 0f);

        cy = y + R_BADGE;
        Draw.text(g, "Badge colour", x + 4, cy + 3, t.cText);
        for (int i = 0; i < Badge.COLOURS.length; i++) {
            int sx = x + 120 + i * 22;
            int rgb = Badge.COLOURS[i] < 0 ? t.cAccent : Badge.COLOURS[i];
            Draw.rr(g, sx, cy, 16, 16, 8, 0xFF000000 | rgb);
            if (Config.data.badgeColor == Badge.COLOURS[i]) Draw.ring(g, sx - 2, cy - 2, 20, 20, 10, 0xFF000000 | t.cAccent2);
        }
        Badge.draw(g, t, x + 120 + Badge.COLOURS.length * 22 + 10, cy - 2, 20);

        cy = y + R_ANIM;
        Draw.text(g, "Animation speed", x + 4, cy + 5, t.cText);
        chips(g, t, mx, my, cy, ANIM_NAMES, ANIM_VALUES, Config.data.animSpeed);

        cy = y + R_SIZE;
        Draw.text(g, "Interface size", x + 4, cy + 5, t.cText);
        chips(g, t, mx, my, cy, SIZE_NAMES, SIZE_VALUES, Config.data.uiScale);
        Draw.text(g, "Small screens may cap it", x + 120 + 5 * CHIP_STEP + 6, cy + 5, t.cDim);

        cy = y + R_RESET;
        Draw.text(g, "Reset", x + 4, cy + 5, t.cText);
        if (resetStep == 0) {
            Ui.button(g, t, "Reset all settings", x + 120, cy, 120, 18, Ui.in(mx, my, x + 120, cy, 120, 18), false);
        } else {
            String ask = "Really reset everything?";
            int aw = Draw.font().width(ask);
            Draw.text(g, ask, x + 120, cy + 5, t.cAccent2);
            int yx = x + 120 + aw + 10;
            Ui.button(g, t, "Yes, reset", yx, cy, 76, 18, Ui.in(mx, my, yx, cy, 76, 18), true);
            Ui.button(g, t, "Cancel", yx + 82, cy, 60, 18, Ui.in(mx, my, yx + 82, cy, 60, 18), false);
        }
        Draw.text(g, "Modules, theme, HUD, FPS options. Saved profiles and theme files stay.", x + 120, cy + 22, t.cDim);

        cy = y + R_CONTACT;
        Draw.text(g, "Modrinth contact", x + 4, cy + 5, t.cText);
        contact.bounds(x + 120, cy, Math.min(w - 124, 330));
        contact.render(g, t);
        Draw.text(g, "Added to the User-Agent header Codex sends, as Modrinth asks API clients to do.", x + 120, cy + 24, t.cDim);

        cy = y + R_FOLDER;
        Draw.text(g, "Game folder", x + 4, cy + 1, t.cText);
        Draw.text(g, Draw.fit(FabricLoader.getInstance().getGameDir().toAbsolutePath().toString(), w - 124), x + 120, cy + 1, t.cDim);
        Draw.text(g, "Downloads go here", x + 4, cy + 15, t.cText);
        Draw.text(g, "mods / resourcepacks / shaderpacks / schematics", x + 120, cy + 15, t.cDim);

        cy = y + R_INFO;
        Draw.rr(g, x, cy, w, 52, t.radius, t.aPanel);
        Draw.ring(g, x, cy, w, 52, t.radius, t.aBorder);
        Draw.text(g, "Codex only contains features that are allowed on most servers.", x + 10, cy + 10, t.cText);
        Draw.text(g, "Fullbright and Freelook are marked: some servers forbid them, check the rules first.", x + 10, cy + 24, t.cDim);
        Draw.text(g, "There is no auto-clicker, auto-crystal or any other automation.", x + 10, cy + 36, t.cDim);
    }

    @Override
    public boolean click(double mx, double my, int button) {
        contact.click(mx, my);
        Minecraft mc = Minecraft.getInstance();

        int cy = y + R_KEY;
        if (Ui.in(mx, my, x + 220, cy, 80, 18)) {
            mc.setScreen(new KeyBindsScreen(mc.screen, mc.options));
            return true;
        }
        if (Ui.in(mx, my, x + 326, cy, 130, 18)) {
            Config.data.lobby = !Config.data.lobby;
            Config.markDirty();
            message = Config.data.lobby ? "Codex lobby on (shown next time the title screen opens)" : "Vanilla title screen restored";
            return true;
        }

        cy = y + R_HUD;
        if (Ui.in(mx, my, x + 120, cy, 90, 18)) {
            mc.setScreen(new HudEditorScreen(mc.screen));
            return true;
        }
        if (Ui.in(mx, my, x + 216, cy, 100, 18)) {
            for (HudModule m : ModuleManager.HUD) m.resetPosition(m.defX, m.defY);
            message = "HUD positions reset";
            return true;
        }
        if (Ui.in(mx, my, x + 326, cy, 130, 18)) {
            Config.data.glitchBannerHidden = !Config.data.glitchBannerHidden;
            Config.markDirty();
            message = Config.data.glitchBannerHidden ? "Glitchy banner hidden" : "Glitchy banner shown";
            if (mc.screen instanceof MenuScreen menu) menu.relayout(); // the pages move up or down with the banner
            return true;
        }

        cy = y + R_NAME;
        if (Ui.in(mx, my, x + 116, cy, 34, 18)) {
            Config.data.ownName = !Config.data.ownName;
            Config.markDirty();
            return true;
        }
        if (Ui.in(mx, my, x + 326, cy, 34, 18)) {
            Config.data.badge = !Config.data.badge;
            Config.markDirty();
            return true;
        }

        cy = y + R_BADGE;
        for (int i = 0; i < Badge.COLOURS.length; i++) {
            if (Ui.in(mx, my, x + 120 + i * 22, cy, 16, 16)) {
                Config.data.badgeColor = Badge.COLOURS[i];
                Config.markDirty();
                return true;
            }
        }

        int i = chipAt(mx, my, y + R_ANIM, ANIM_VALUES.length);
        if (i >= 0) {
            Config.data.animSpeed = ANIM_VALUES[i];
            Config.markDirty();
            message = "Animation speed: " + ANIM_NAMES[i];
            return true;
        }

        i = chipAt(mx, my, y + R_SIZE, SIZE_VALUES.length);
        if (i >= 0) {
            Config.data.uiScale = SIZE_VALUES[i];
            Config.markDirty();
            message = "Interface size: " + SIZE_NAMES[i];
            if (mc.screen instanceof MenuScreen ms) ms.relayout();
            return true;
        }

        cy = y + R_RESET;
        if (resetStep == 0) {
            if (Ui.in(mx, my, x + 120, cy, 120, 18)) {
                resetStep = 1;
                resetAt = System.currentTimeMillis();
                return true;
            }
        } else {
            int yx = x + 120 + Draw.font().width("Really reset everything?") + 10;
            if (Ui.in(mx, my, yx, cy, 76, 18)) {
                resetStep = 0;
                resetEverything();
                return true;
            }
            if (Ui.in(mx, my, yx + 82, cy, 60, 18)) {
                resetStep = 0;
                message = "Reset cancelled";
                return true;
            }
        }
        return contact.focused;
    }

    /** Undoes what is applied to the game first (FPS options need the old backup), then wipes the config. */
    private void resetEverything() {
        FpsBoost.restoreVanilla();
        ModuleManager.resetAll();
        Config.resetAll();
        Theme flame = ThemeManager.find("Flame");
        if (flame != null) ThemeManager.setCurrent(flame);
        Config.save();
        contact.setText("");
        message = "Everything is back to defaults";
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MenuScreen ms) ms.relayout();
    }

    @Override
    public boolean key(int key, int mods) {
        return contact.key(key, mods);
    }

    @Override
    public boolean chr(int cp) {
        return contact.chr(cp);
    }

    @Override
    public boolean typing() {
        return contact.focused;
    }
}
