package dev.candle.codex.ui;

import dev.candle.codex.config.Config;
import dev.candle.codex.module.HudModule;
import dev.candle.codex.module.ModuleManager;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Drag-and-drop HUD layout editor with edge/centre snapping and alignment guides. */
public final class HudEditorScreen extends Screen {
    private static final int SNAP = 6;
    private static final int BTN_W = 84, BTN_H = 18, BTN_Y = 46;
    /** Snapping stays as the player left it while the game is running. */
    private static boolean snapOn = true;
    private final Screen parent;
    private HudModule dragging;
    private int offX, offY;
    private int guideX = -1, guideY = -1;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    private int snapBtnX() {
        return width / 2 - BTN_W - 3;
    }

    private int resetBtnX() {
        return width / 2 + 3;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        Theme t = ThemeManager.current();
        Draw.mul = 1f;
        g.fill(0, 0, width, height, 0x88000000);
        // centre guides
        Draw.fill(g, width / 2, 0, width / 2 + 1, height, 0x22FFFFFF);
        Draw.fill(g, 0, height / 2, width, height / 2 + 1, 0x22FFFFFF);

        List<HudModule> list = ModuleManager.HUD;
        for (HudModule m : list) {
            if (!m.enabled) continue;
            if (Minecraft.getInstance().player != null) m.render(g, Minecraft.getInstance(), t, width, height);
            int px = m.pixelX(width), py = m.pixelY(height);
            boolean hover = Ui.in(mx, my, px, py, m.pxW(), m.pxH());
            int col = m == dragging ? 0xFF000000 | t.cAccent2 : hover ? 0xCC000000 | t.cAccent : 0x66000000 | t.cDim;
            Draw.ring(g, px - 2, py - 2, m.pxW() + 4, m.pxH() + 4, 4, col);
        }
        if (guideX >= 0) Draw.fill(g, guideX, 0, guideX + 1, height, 0xAA000000 | t.cAccent);
        if (guideY >= 0) Draw.fill(g, 0, guideY, width, guideY + 1, 0xAA000000 | t.cAccent);

        Draw.rr(g, width / 2 - 150, 8, 300, 34, t.radius, t.aPanel);
        Draw.ring(g, width / 2 - 150, 8, 300, 34, t.radius, t.aBorder);
        Draw.textC(g, "HUD Editor", width / 2, 14, t.cAccent2);
        Draw.textC(g, "Drag to move  |  Right click: default spot  |  Esc: done", width / 2, 27, t.cDim);

        int sx = snapBtnX(), rx = resetBtnX();
        Ui.button(g, t, snapOn ? "Snap: ON" : "Snap: OFF", sx, BTN_Y, BTN_W, BTN_H, Ui.in(mx, my, sx, BTN_Y, BTN_W, BTN_H), snapOn);
        Ui.button(g, t, "Reset all", rx, BTN_Y, BTN_W, BTN_H, Ui.in(mx, my, rx, BTN_Y, BTN_W, BTN_H), false);
    }

    private HudModule hit(double mx, double my) {
        for (int i = ModuleManager.HUD.size() - 1; i >= 0; i--) {
            HudModule m = ModuleManager.HUD.get(i);
            if (m.enabled && Ui.in(mx, my, m.pixelX(width), m.pixelY(height), m.pxW(), m.pxH())) return m;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            if (Ui.in(e.x(), e.y(), snapBtnX(), BTN_Y, BTN_W, BTN_H)) {
                snapOn = !snapOn;
                guideX = -1;
                guideY = -1;
                return true;
            }
            if (Ui.in(e.x(), e.y(), resetBtnX(), BTN_Y, BTN_W, BTN_H)) {
                for (HudModule h : ModuleManager.HUD) h.resetPosition(h.defX, h.defY);
                Config.markDirty();
                return true;
            }
        }
        HudModule m = hit(e.x(), e.y());
        if (m == null) return super.mouseClicked(e, dbl);
        if (e.button() == 1) {
            m.resetPosition(m.defX, m.defY);
            return true;
        }
        dragging = m;
        offX = (int) e.x() - m.pixelX(width);
        offY = (int) e.y() - m.pixelY(height);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(e, dx, dy);
        int nx = (int) e.x() - offX, ny = (int) e.y() - offY;
        int w = dragging.pxW(), h = dragging.pxH();
        guideX = -1;
        guideY = -1;
        if (snapOn) {
            // snap edges to screen edges and centre lines
            if (Math.abs(nx) < SNAP) { nx = 0; guideX = 0; }
            else if (Math.abs(nx + w - width) < SNAP) { nx = width - w; guideX = width - 1; }
            else if (Math.abs(nx + w / 2 - width / 2) < SNAP) { nx = width / 2 - w / 2; guideX = width / 2; }
            if (Math.abs(ny) < SNAP) { ny = 0; guideY = 0; }
            else if (Math.abs(ny + h - height) < SNAP) { ny = height - h; guideY = height - 1; }
            else if (Math.abs(ny + h / 2 - height / 2) < SNAP) { ny = height / 2 - h / 2; guideY = height / 2; }
            // snap to other HUD elements
            for (HudModule o : ModuleManager.HUD) {
                if (o == dragging || !o.enabled) continue;
                int ox = o.pixelX(width), oy = o.pixelY(height), ow = o.pxW(), oh = o.pxH();
                if (guideX < 0) {
                    if (Math.abs(nx - ox) < SNAP) { nx = ox; guideX = ox; }
                    else if (Math.abs(nx - (ox + ow)) < SNAP) { nx = ox + ow; guideX = ox + ow; }
                    else if (Math.abs(nx + w - ox) < SNAP) { nx = ox - w; guideX = ox; }
                }
                if (guideY < 0) {
                    if (Math.abs(ny - oy) < SNAP) { ny = oy; guideY = oy; }
                    else if (Math.abs(ny - (oy + oh)) < SNAP) { ny = oy + oh; guideY = oy + oh; }
                    else if (Math.abs(ny + h - oy) < SNAP) { ny = oy - h; guideY = oy; }
                }
            }
        }
        nx = Math.max(0, Math.min(width - w, nx));
        ny = Math.max(0, Math.min(height - h, ny));
        dragging.setPixel(nx, ny, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        guideX = -1;
        guideY = -1;
        Config.markDirty();
        return super.mouseReleased(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
