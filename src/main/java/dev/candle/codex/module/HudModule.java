package dev.candle.codex.module;

import dev.candle.codex.config.Config;
import dev.candle.codex.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** A module that draws something on screen and can be dragged in the HUD editor. */
public abstract class HudModule extends Module {
    /** Position as a fraction of the screen so it survives window resizes. */
    public float fx, fy;
    /** Default position, used by the reset buttons of the HUD editor. */
    public final float defX, defY;
    /** Unscaled size, updated every draw. */
    public int w = 60, h = 14;

    protected final Setting background = add(Setting.bool("background", "Background", false));
    protected final Setting scale = add(Setting.num("scale", "Scale", 1.0, 0.5, 3.0, 0.05));
    protected final Setting bgTheme = add(Setting.bool("bgTheme", "Theme background colour", true));
    protected final Setting bgColor = add(Setting.color("bgColor", "Background colour", 0x281F18));
    protected final Setting bgOpacity = add(Setting.num("bgOpacity", "Background opacity (%)", 100, 10, 100, 5));
    protected final Setting rounded = add(Setting.bool("rounded", "Rounded corners", true));
    protected final Setting textOverride = add(Setting.bool("textOverride", "Custom text colour", false));
    protected final Setting textColor = add(Setting.color("textColor", "Text colour", 0xFFFFFF));

    /** Scratch theme that carries this module's style overrides. Filled in place every frame, so nothing is allocated. */
    private final Theme styled = new Theme();

    protected HudModule(String id, String name, String desc, String category, float defX, float defY) {
        super(id, name, desc, category);
        this.defX = defX;
        this.defY = defY;
        this.fx = defX;
        this.fy = defY;
    }

    @Override
    public boolean isHud() {
        return true;
    }

    @Override
    protected boolean defaultEnabled() {
        return true;
    }

    @Override
    public void loadFromConfig() {
        super.loadFromConfig();
        float[] p = Config.data.hud.get(id);
        if (p != null && p.length == 2) {
            fx = p[0];
            fy = p[1];
        }
    }

    public float scaleF() {
        return (float) scale.num;
    }

    public int pxW() {
        return Math.max(1, Math.round(w * scaleF()));
    }

    public int pxH() {
        return Math.max(1, Math.round(h * scaleF()));
    }

    public int pixelX(int sw) {
        return Math.max(0, Math.min(Math.max(0, sw - pxW()), Math.round(fx * sw)));
    }

    public int pixelY(int sh) {
        return Math.max(0, Math.min(Math.max(0, sh - pxH()), Math.round(fy * sh)));
    }

    public void setPixel(int x, int y, int sw, int sh) {
        fx = x / (float) sw;
        fy = y / (float) sh;
        Config.data.hud.put(id, new float[]{fx, fy});
        Config.markDirty();
    }

    public void resetPosition(float x, float y) {
        fx = x;
        fy = y;
        Config.data.hud.remove(id);
        Config.markDirty();
    }

    /**
     * Returns the theme the module should draw with. When no style option was changed it is the real theme,
     * otherwise a copy where the card colour, corner radius and text colours carry the module's settings.
     */
    private Theme styledTheme(Theme t) {
        boolean plain = bgTheme.bool && bgOpacity.num >= 99.5 && rounded.bool && !textOverride.bool;
        if (plain) return t;
        Theme v = styled;
        v.cBg = t.cBg;
        v.cPanel = t.cPanel;
        v.cCard = t.cCard;
        v.cAccent = t.cAccent;
        v.cAccent2 = t.cAccent2;
        v.cText = t.cText;
        v.cDim = t.cDim;
        v.cBorder = t.cBorder;
        v.aBg = t.aBg;
        v.aPanel = t.aPanel;
        v.aCard = t.aCard;
        v.aCardHover = t.aCardHover;
        v.aBorder = t.aBorder;
        v.aAccentSoft = t.aAccentSoft;
        v.aAccentGlow = t.aAccentGlow;
        v.radius = t.radius;
        v.opacity = t.opacity;
        int base = bgTheme.bool ? t.aCard : ((t.aCard & 0xFF000000) | (bgColor.color & 0xFFFFFF));
        int a = Math.round(((base >>> 24) & 255) * (float) (bgOpacity.num / 100.0));
        v.aCard = (a << 24) | (base & 0xFFFFFF);
        if (!rounded.bool) v.radius = 0;
        if (textOverride.bool) {
            v.cText = textColor.color & 0xFFFFFF;
            v.cDim = textColor.color & 0xFFFFFF;
        }
        return v;
    }

    public final void render(GuiGraphics g, Minecraft mc, Theme t, int sw, int sh) {
        float s = scaleF();
        int px = pixelX(sw), py = pixelY(sh);
        Theme v = styledTheme(t);
        g.pose().pushMatrix();
        g.pose().translate((float) px, (float) py);
        g.pose().scale(s, s);
        drawContent(g, mc, v);
        g.pose().popMatrix();
    }

    protected abstract void drawContent(GuiGraphics g, Minecraft mc, Theme t);
}
