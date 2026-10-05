package dev.candle.codex.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.candle.codex.CodexClient;
import dev.candle.codex.mixin.OptionInstanceAccessor;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.HudEditorScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** All built-in modules. Each module only does work while it is enabled. */
public final class Modules {
    private Modules() {}

    private static KeyMapping key(String lang, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(lang, InputConstants.Type.KEYSYM, code, CodexClient.CATEGORY));
    }

    // ------------------------------------------------------------------ text pills

    public static final class Fps extends TextHud {
        public Fps() {
            super("fps", "FPS", "Frames per second.", "HUD", "FPS", 0.01f, 0.02f, 250);
        }

        @Override
        protected String compute(Minecraft mc) {
            return Integer.toString(mc.getFps());
        }
    }

    public static final class Cps extends TextHud {
        public Cps() {
            super("cps", "CPS", "Left | right clicks per second.", "PvP", "CPS", 0.01f, 0.07f, 100);
        }

        @Override
        protected String compute(Minecraft mc) {
            return ClickTracker.cps(0) + " | " + ClickTracker.cps(1);
        }
    }

    public static final class Ping extends TextHud {
        public Ping() {
            super("ping", "Ping", "Server latency.", "HUD", "PING", 0.01f, 0.12f, 500);
        }

        @Override
        protected String compute(Minecraft mc) {
            if (mc.player == null || mc.getConnection() == null) return "-";
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? "-" : info.getLatency() + " ms";
        }
    }

    public static final class Coordinates extends TextHud {
        public Coordinates() {
            super("coords", "Coordinates", "Your block position.", "HUD", "XYZ", 0.01f, 0.17f, 200);
        }

        @Override
        protected String compute(Minecraft mc) {
            if (mc.player == null) return "-";
            return mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
        }
    }

    private static int count(Minecraft mc, net.minecraft.world.item.Item item) {
        if (mc.player == null) return 0;
        int n = 0;
        var inv = mc.player.getInventory();
        int size = Math.min(inv.getContainerSize(), 36);
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        ItemStack off = mc.player.getOffhandItem();
        if (off.is(item)) n += off.getCount();
        return n;
    }

    public static final class Totems extends TextHud {
        public Totems() {
            super("totems", "Totem Counter", "Totems of Undying in your inventory and offhand.", "PvP", "TOTEMS", 0.01f, 0.22f, 250);
        }

        @Override
        protected String compute(Minecraft mc) {
            return Integer.toString(count(mc, Items.TOTEM_OF_UNDYING));
        }
    }

    public static final class Crystals extends TextHud {
        public Crystals() {
            super("crystals", "Crystal Counter", "End Crystals in your inventory and offhand.", "PvP", "CRYSTALS", 0.01f, 0.27f, 250);
        }

        @Override
        protected String compute(Minecraft mc) {
            return Integer.toString(count(mc, Items.END_CRYSTAL));
        }
    }

    public static final class Clock extends TextHud {
        private static final java.time.format.DateTimeFormatter FMT = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        public Clock() {
            super("clock", "Clock", "Your local time.", "HUD", "TIME", 0.17f, 0.02f, 1000);
        }

        @Override
        protected String compute(Minecraft mc) {
            return java.time.LocalTime.now().format(FMT);
        }
    }

    public static final class Memory extends TextHud {
        public Memory() {
            super("memory", "Memory", "Java memory in use.", "HUD", "MEM", 0.17f, 0.07f, 1000);
        }

        @Override
        protected String compute(Minecraft mc) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            long max = rt.maxMemory() >> 20;
            return used + "/" + max + " MB";
        }
    }

    public static final class Speed extends TextHud {
        public Speed() {
            super("speed", "Speed", "Horizontal speed in blocks per second.", "HUD", "SPEED", 0.17f, 0.12f, 150);
        }

        @Override
        protected String compute(Minecraft mc) {
            if (mc.player == null) return "-";
            double v = mc.player.getDeltaMovement().horizontalDistance() * 20.0;
            return String.format(java.util.Locale.ROOT, "%.1f b/s", v);
        }
    }

    public static final class Direction extends TextHud {
        private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

        public Direction() {
            super("direction", "Direction", "Compass direction you are facing.", "HUD", "DIR", 0.17f, 0.17f, 100);
        }

        @Override
        protected String compute(Minecraft mc) {
            if (mc.player == null) return "-";
            float deg = ((mc.player.getYRot() + 180f) % 360f + 360f) % 360f;
            int i = Math.round(deg / 45f) % 8;
            return NAMES[i] + " " + (Math.round(deg) % 360) + "\u00b0";
        }
    }

    /** Clean FPS readout with a smooth sparkline. "Pill" is one tiny row, "Card" adds frame time and AVG / LOW. */
    public static final class FpsGraph extends HudModule {
        private static final int N = 300;
        private static final long STEP_NS = 40_000_000L;
        private final float[] col = new float[N];
        private final float[] tmp = new float[N];
        private int pos, filled;
        private long lastNs, bucketStart;
        private float bucketSum;
        private int bucketN;
        private float shown, lo = 0f, hi = 120f;
        private final Setting style = add(Setting.choice("style", "Style", "Pill", "Pill", "Card"));
        private final Setting graphW = add(Setting.num("width", "Graph width", 70, 40, 160, 5));
        private final Setting target = add(Setting.num("target", "Target FPS", 60, 30, 240, 10));
        private long next;
        private String fpsText = "0", avgText = "AVG 0", lowText = "LOW 0", msText = "0.0 ms";

        public FpsGraph() {
            super("fpsgraph", "FPS Graph", "Tidy FPS readout with a smooth sparkline.", "HUD", 0.74f, 0.70f);
        }

        private static int colorFor(float fps, float target) {
            float r = fps / target;
            int red = 0xFF5A5A, yellow = 0xFFD166, green = 0x56E39F;
            if (r >= 1f) return green;
            if (r >= 0.6f) return Theme.mix(yellow, green, (r - 0.6f) / 0.4f);
            if (r >= 0.3f) return Theme.mix(red, yellow, (r - 0.3f) / 0.3f);
            return red;
        }

        private void sample(long nowNs, float fps) {
            if (bucketStart == 0) bucketStart = nowNs;
            bucketSum += fps;
            bucketN++;
            while (nowNs - bucketStart >= STEP_NS) {
                col[pos] = bucketN > 0 ? bucketSum / bucketN : fps;
                pos = (pos + 1) % N;
                if (filled < N) filled++;
                bucketSum = 0;
                bucketN = 0;
                bucketStart += STEP_NS;
            }
        }

        private float at(int back) {
            return col[((pos - 1 - back) % N + N) % N];
        }

        private void stats() {
            int n = Math.min(filled, 150);
            if (n < 3) return;
            float sum = 0, mx = 0, mn = 1e9f;
            for (int i = 0; i < n; i++) {
                float v = at(i);
                tmp[i] = v;
                sum += v;
                mx = Math.max(mx, v);
                mn = Math.min(mn, v);
            }
            java.util.Arrays.sort(tmp, 0, n);
            int k = Math.max(1, n / 20);
            float low = 0;
            for (int i = 0; i < k; i++) low += tmp[i];
            low /= k;
            float avg = sum / n;
            avgText = "AVG " + Math.round(avg);
            lowText = "LOW " + Math.round(low);
            msText = avg > 0 ? String.format(java.util.Locale.ROOT, "%.1f ms", 1000f / avg) : "- ms";
            hi += (Math.max((float) target.num * 1.2f, mx * 1.1f) - hi) * 0.2f;
            lo += (Math.max(0f, Math.min(mn * 0.85f, (float) target.num * 0.5f)) - lo) * 0.2f;
        }

        private void spark(GuiGraphics g, int x, int y, int gw, int gh, float tFps) {
            int n = Math.min(gw, filled);
            float range = Math.max(10f, hi - lo);
            int prev = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                int back = n - 1 - i;
                float a = at(back), b = back + 1 < filled ? at(back + 1) : a, c = back > 0 ? at(back - 1) : a;
                float v = (a * 2f + b + c) * 0.25f;
                float k = Math.max(0f, Math.min(1f, (v - lo) / range));
                int yy = y + gh - 2 - Math.round((gh - 3) * k);
                int px = x + (gw - n) + i;
                int rgb = colorFor(v, tFps);
                Draw.fill(g, px, yy + 2, px + 1, y + gh, 0x22000000 | rgb);
                int from = prev == Integer.MIN_VALUE ? yy : prev;
                Draw.fill(g, px, Math.min(from, yy), px + 1, Math.max(from, yy) + 2, 0xFF000000 | rgb);
                prev = yy;
            }
        }

        @Override
        protected void drawContent(GuiGraphics g, Minecraft mc, Theme t) {
            long nowNs = System.nanoTime();
            float cur = mc.getFps();
            if (lastNs != 0) {
                float dt = Math.min(0.25f, (nowNs - lastNs) / 1e9f);
                shown += (cur - shown) * Math.min(1f, dt * 5f);
            } else {
                shown = cur;
            }
            lastNs = nowNs;
            sample(nowNs, cur);
            long now = System.currentTimeMillis();
            if (now >= next) {
                next = now + 200;
                fpsText = Integer.toString(Math.round(shown));
                stats();
            }

            float tFps = (float) target.num;
            int main = colorFor(shown, tFps);
            int r = Math.max(4, t.radius / 2 + 2);
            boolean card = style.choice == 1;
            int gw = graphW.i();
            if (!card) {
                int fw = mc.font.width(fpsText), uw = mc.font.width("FPS");
                w = 8 + 8 + fw + 4 + uw + 8 + gw + 6;
                h = 22;
                if (background.bool) {
                    Draw.rr(g, 0, 0, w, h, h / 2, t.aCard);
                    Draw.ring(g, 0, 0, w, h, h / 2, t.aBorder);
                }
                float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 400.0);
                Draw.glow(g, 8, h / 2 - 3, 6, 6, 3, main, Math.round(3 + 3 * pulse), 3);
                Draw.rr(g, 8, h / 2 - 3, 6, 6, 3, 0xFF000000 | main);
                Draw.textShadow(g, fpsText, 20, (h - 8) / 2, 0xFFFFFF);
                Draw.text(g, "FPS", 20 + fw + 4, (h - 8) / 2, t.cDim);
                spark(g, 20 + fw + 4 + uw + 8, 4, gw, h - 8, tFps);
            } else {
                w = Math.max(100, gw + 16);
                int pad = 8;
                h = pad + 16 + 4 + 24 + 4 + 10 + pad - 2;
                if (background.bool) {
                    Draw.glow(g, 0, 0, w, h, r, main, 3, 3);
                    Draw.rr(g, 0, 0, w, h, r, t.aCard);
                    Draw.ring(g, 0, 0, w, h, r, t.aBorder);
                }
                g.pose().pushMatrix();
                g.pose().translate((float) pad, (float) (pad - 1));
                g.pose().scale(1.5f, 1.5f);
                Draw.textShadow(g, fpsText, 0, 0, main);
                g.pose().popMatrix();
                int nw = Math.round(mc.font.width(fpsText) * 1.5f);
                Draw.text(g, "FPS", pad + nw + 4, pad + 5, t.cDim);
                Draw.textR(g, msText, w - pad, pad + 5, t.cDim);
                spark(g, pad, pad + 20, w - pad * 2, 24, tFps);
                int sy = pad + 20 + 24 + 4;
                Draw.text(g, avgText, pad, sy, t.cText);
                Draw.textR(g, lowText, w - pad, sy, 0xFFFFC46B);
            }
        }
    }

    // ------------------------------------------------------------------ custom HUDs

    public static final class Keystrokes extends HudModule {
        private final Setting mouse = add(Setting.bool("mouse", "Show mouse buttons", true));
        private final Setting space = add(Setting.bool("space", "Show space bar", true));
        private String lText = "LMB 0", rText = "RMB 0";
        private long next;

        public Keystrokes() {
            super("keystrokes", "Keystrokes", "WASD, mouse buttons and jump.", "PvP", 0.01f, 0.5f);
            background.bool = false;
        }

        private void box(GuiGraphics g, Theme t, boolean down, String label, int x, int y, int bw, int bh) {
            int r = Math.max(2, t.radius / 2);
            if (down) {
                Draw.rr(g, x, y, bw, bh, r, (0xE0 << 24) | t.cAccent);
            } else if (background.bool) {
                Draw.rr(g, x, y, bw, bh, r, t.aCard);
            }
            Draw.textC(g, label, x + bw / 2, y + (bh - 8) / 2, down ? t.cBg : t.cText);
        }

        @Override
        protected void drawContent(GuiGraphics g, Minecraft mc, Theme t) {
            int s = 22, gap = 2;
            var o = mc.options;
            box(g, t, o.keyUp.isDown(), "W", s + gap, 0, s, s);
            box(g, t, o.keyLeft.isDown(), "A", 0, s + gap, s, s);
            box(g, t, o.keyDown.isDown(), "S", s + gap, s + gap, s, s);
            box(g, t, o.keyRight.isDown(), "D", 2 * (s + gap), s + gap, s, s);
            int totalW = 3 * s + 2 * gap;
            int y = 2 * (s + gap);
            if (mouse.bool) {
                long now = System.currentTimeMillis();
                if (now >= next) {
                    next = now + 200;
                    lText = "LMB " + ClickTracker.cps(0);
                    rText = "RMB " + ClickTracker.cps(1);
                }
                int half = (totalW - gap) / 2;
                box(g, t, ClickTracker.isDown(0), lText, 0, y, half, 18);
                box(g, t, ClickTracker.isDown(1), rText, half + gap, y, totalW - half - gap, 18);
                y += 18 + gap;
            }
            if (space.bool) {
                box(g, t, o.keyJump.isDown(), "-----", 0, y, totalW, 12);
                y += 12 + gap;
            }
            w = totalW;
            h = y - gap;
        }
    }

    public static final class ArmorHud extends HudModule {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        private final Setting layout = add(Setting.choice("layout", "Layout", "Vertical", "Vertical", "Horizontal"));
        private final Setting durability = add(Setting.bool("durability", "Show durability", true));
        private final String[] dur = {"", "", "", ""};
        private int textW;
        private long next;

        public ArmorHud() {
            super("armor", "Armor HUD", "Your armor with remaining durability.", "PvP", 0.9f, 0.35f);
        }

        @Override
        protected void drawContent(GuiGraphics g, Minecraft mc, Theme t) {
            long now = System.currentTimeMillis();
            if (now >= next) {
                next = now + 500;
                int mw = 0;
                for (int i = 0; i < 4; i++) {
                    ItemStack s = mc.player.getItemBySlot(SLOTS[i]);
                    dur[i] = s.isEmpty() || !s.isDamageableItem() ? "" : Integer.toString(s.getMaxDamage() - s.getDamageValue());
                    mw = Math.max(mw, mc.font.width(dur[i]));
                }
                textW = mw;
            }
            boolean vertical = layout.choice == 0;
            boolean showDur = durability.bool;
            if (vertical) {
                w = 4 + 16 + (showDur ? 4 + textW : 0) + 4;
                h = 4 * 18 + 2;
            } else {
                w = 4 * 20 + 4;
                h = 4 + 16 + (showDur ? 11 : 0) + 2;
            }
            if (background.bool) Draw.rr(g, 0, 0, w, h, Math.max(2, t.radius / 2), t.aCard);
            for (int i = 0; i < 4; i++) {
                ItemStack s = mc.player.getItemBySlot(SLOTS[i]);
                int ix = vertical ? 4 : 4 + i * 20;
                int iy = vertical ? 2 + i * 18 : 3;
                if (!s.isEmpty()) {
                    g.renderItem(s, ix, iy);
                    if (showDur && !dur[i].isEmpty()) {
                        if (vertical) Draw.text(g, dur[i], ix + 20, iy + 4, t.cText);
                        else Draw.textC(g, dur[i], ix + 8, iy + 18, t.cText);
                    }
                }
            }
        }
    }

    public static final class PotionHud extends HudModule {
        private final String[] lines = new String[8];
        private int count, lineW;
        private long next;

        public PotionHud() {
            super("potion", "Potion HUD", "Active effects and time left.", "HUD", 0.8f, 0.02f);
        }

        @Override
        protected void drawContent(GuiGraphics g, Minecraft mc, Theme t) {
            long now = System.currentTimeMillis();
            if (now >= next) {
                next = now + 500;
                count = 0;
                int mw = 0;
                for (MobEffectInstance e : mc.player.getActiveEffects()) {
                    if (count >= lines.length) break;
                    String name = e.getEffect().value().getDisplayName().getString();
                    if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
                    String time;
                    if (e.isInfiniteDuration()) {
                        time = "inf";
                    } else {
                        int sec = e.getDuration() / 20;
                        time = (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
                    }
                    lines[count] = name + "  " + time;
                    mw = Math.max(mw, mc.font.width(lines[count]));
                    count++;
                }
                lineW = mw;
            }
            if (count == 0) {
                if (mc.screen instanceof HudEditorScreen) {
                    w = 70;
                    h = 16;
                    Draw.rr(g, 0, 0, w, h, Math.max(2, t.radius / 2), t.aCard);
                    Draw.text(g, "Potions", 6, 4, t.cDim);
                } else {
                    w = 1;
                    h = 1;
                }
                return;
            }
            w = lineW + 10;
            h = count * 11 + 5;
            if (background.bool) Draw.rr(g, 0, 0, w, h, Math.max(2, t.radius / 2), t.aCard);
            for (int i = 0; i < count; i++) Draw.text(g, lines[i], 5, 3 + i * 11, t.cText);
        }
    }

    // ------------------------------------------------------------------ gameplay modules

    public static final class ToggleSprint extends Module {
        public ToggleSprint() {
            super("togglesprint", "Toggle Sprint", "Sprints automatically while you walk forward.", "Utility");
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.player == null || mc.screen != null) return;
            if (mc.options.keyUp.isDown() && !mc.player.isUsingItem() && !mc.player.isShiftKeyDown()) {
                mc.options.keySprint.setDown(true);
            }
        }

        @Override
        public void onDisable() {
            Minecraft.getInstance().options.keySprint.setDown(false);
        }
    }

    public static final class Freelook extends Module {
        private static boolean active;
        private static float yaw, pitch;
        private final KeyMapping key = key("key.candle.freelook", GLFW.GLFW_KEY_LEFT_ALT);

        public Freelook() {
            super("freelook", "Freelook", "Hold the key to look around without turning.", "Visual");
            warning = "candle.warn.ban";
        }

        public static boolean isActive() {
            return active;
        }

        public static float yaw() {
            return yaw;
        }

        public static float pitch() {
            return pitch;
        }

        /** Called by EntityMixin with the raw mouse deltas. */
        public static void turn(double dYaw, double dPitch) {
            yaw += (float) dYaw * 0.15f;
            pitch = Math.max(-90f, Math.min(90f, pitch + (float) dPitch * 0.15f));
        }

        @Override
        public void onDisable() {
            active = false;
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.player == null) {
                active = false;
                return;
            }
            boolean down = key.isDown() && mc.screen == null;
            if (down && !active) {
                yaw = mc.player.getYRot();
                pitch = mc.player.getXRot();
            }
            active = down;
        }
    }

    public static final class Fullbright extends Module {
        private double previous = 0.5;
        private boolean applied;

        public Fullbright() {
            super("fullbright", "Fullbright", "Maximum brightness everywhere.", "Visual");
            warning = "candle.warn.ban";
        }

        private static void setGamma(Minecraft mc, double v) {
            ((OptionInstanceAccessor) (Object) mc.options.gamma()).candle$setValue(v);
        }

        @Override
        public void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            double cur = mc.options.gamma().get();
            previous = cur > 1.0 ? 0.5 : cur;
            applied = true;
            setGamma(mc, 16.0);
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.options.gamma().get() < 15.9) setGamma(mc, 16.0);
        }

        @Override
        public void onDisable() {
            restore();
        }

        public void restore() {
            if (!applied) return;
            applied = false;
            setGamma(Minecraft.getInstance(), previous);
        }
    }

    public static final class Crosshair extends Module {
        public final Setting style = add(Setting.choice("style", "Style", "Plus Gap", "Cross", "Plus Gap", "Dot", "Circle"));
        public final Setting size = add(Setting.num("size", "Size", 5, 1, 14, 1));
        public final Setting gap = add(Setting.num("gap", "Gap", 3, 0, 8, 1));
        public final Setting thickness = add(Setting.num("thickness", "Thickness", 1, 1, 4, 1));
        public final Setting outline = add(Setting.bool("outline", "Outline", true));
        public final Setting color = add(Setting.color("color", "Colour", 0xFFFFFF));

        public Crosshair() {
            super("crosshair", "Custom Crosshair", "Replaces the vanilla crosshair.", "Visual");
        }
    }

    public static final class HitColor extends Module {
        public final Setting hitColor = add(Setting.color("hitColor", "Hit colour", 0xFF3B3B));
        public final Setting duration = add(Setting.num("duration", "Flash time (ms)", 220, 80, 600, 20));
        private long flashUntil;
        private int lastHurt;

        public HitColor() {
            super("hitcolor", "Hit Color", "Flashes the crosshair when you hit a target.", "PvP");
        }

        public boolean flashing() {
            return System.currentTimeMillis() < flashUntil;
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.crosshairPickEntity instanceof LivingEntity le) {
                if (le.hurtTime > lastHurt) flashUntil = System.currentTimeMillis() + (long) duration.num;
                lastHurt = le.hurtTime;
            } else {
                lastHurt = 0;
            }
        }
    }
}
