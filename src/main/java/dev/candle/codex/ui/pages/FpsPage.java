package dev.candle.codex.ui.pages;

import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import dev.candle.codex.module.Module;
import dev.candle.codex.module.ModuleManager;
import dev.candle.codex.net.Installer;
import dev.candle.codex.net.Restarter;
import dev.candle.codex.perf.FpsBoost;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Page;
import dev.candle.codex.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** FPS Boost: one-click presets, individual options, live frame graph and the Crystal PvP installer. */
public final class FpsPage extends Page {
    private record Opt(String name, Supplier<Boolean> get, Consumer<Boolean> set) {}

    private final List<Opt> opts = new ArrayList<>();

    // ---- live graph: a smooth line over a soft fill, like the FPS Graph HUD module ----
    private static final int N = 240;            // samples kept (24 s at one sample per 100 ms)
    private static final long STEP_MS = 100;
    private static final float TARGET = 60f;     // colour scale: green at or above this
    private final float[] hist = new float[N];   // ring buffer of raw samples
    private final float[] sm = new float[N];     // smoothed samples, oldest first
    private final float[] tmp = new float[N];
    private int pos, filled, smN;
    private long nextSample, lastNs;
    private float shown, lo, hi = 120f;
    private float avg, low, best;

    private int dragSlider = -1;
    private int sliderX, sliderW;

    private volatile String status = "";
    private volatile boolean busy;
    private volatile boolean needsRestart;
    private String restartError = "";

    public FpsPage() {
        super("FPS Boost");
        opts.add(new Opt("Unlimited FPS", () -> c().unlimitedFps, v -> c().unlimitedFps = v));
        opts.add(new Opt("VSync off", () -> c().vsyncOff, v -> c().vsyncOff = v));
        opts.add(new Opt("Fewer explosion particles", () -> c().reduceExplosions, v -> c().reduceExplosions = v));
        opts.add(new Opt("Minimal particles", () -> c().particlesMinimal, v -> c().particlesMinimal = v));
        opts.add(new Opt("No fire overlay", () -> c().noFireOverlay, v -> c().noFireOverlay = v));
        opts.add(new Opt("Clouds off", () -> c().cloudsOff, v -> c().cloudsOff = v));
        opts.add(new Opt("Entity shadows off", () -> c().shadowsOff, v -> c().shadowsOff = v));
        opts.add(new Opt("No damage tilt", () -> c().noDamageTilt, v -> c().noDamageTilt = v));
        opts.add(new Opt("No view bobbing", () -> c().noViewBob, v -> c().noViewBob = v));
        opts.add(new Opt("Lower simulation distance", () -> c().lowSimulation, v -> c().lowSimulation = v));
        opts.add(new Opt("Fewer mipmaps", () -> c().lowMipmaps, v -> c().lowMipmaps = v));
    }

    private static Config.Perf c() {
        return Config.data.perf;
    }

    private void changed() {
        c().active = true;
        Config.markDirty();
        FpsBoost.reapply();
    }

    /** Red below 30 % of the target, through yellow, to green at the target. Same scale as the FPS Graph HUD. */
    private static int colorFor(float fps) {
        float r = fps / TARGET;
        int red = 0xFF5A5A, yellow = 0xFFD166, green = 0x56E39F;
        if (r >= 1f) return green;
        if (r >= 0.6f) return Theme.mix(yellow, green, (r - 0.6f) / 0.4f);
        if (r >= 0.3f) return Theme.mix(red, yellow, (r - 0.3f) / 0.3f);
        return red;
    }

    /** Stores one sample and refreshes the smoothed curve, AVG, LOW (average of the worst 5 %) and BEST. */
    private void sample(float fps) {
        hist[pos] = fps;
        pos = (pos + 1) % N;
        if (filled < N) filled++;
        int n = filled;
        float sum = 0, mx = 0, mn = 1e9f;
        for (int k = 0; k < n; k++) {
            float v = hist[((pos - n + k) % N + N) % N];
            tmp[k] = v;
            sum += v;
            mx = Math.max(mx, v);
            mn = Math.min(mn, v);
        }
        for (int k = 0; k < n; k++) {
            float a = tmp[k], b = k > 0 ? tmp[k - 1] : a, d = k < n - 1 ? tmp[k + 1] : a;
            sm[k] = (a * 2f + b + d) * 0.25f;
        }
        smN = n;
        Arrays.sort(tmp, 0, n);
        int kk = Math.max(1, n / 20);
        float l = 0;
        for (int k = 0; k < kk; k++) l += tmp[k];
        low = l / kk;
        avg = sum / n;
        best = mx;
        hi += (Math.max(TARGET * 1.2f, mx * 1.1f) - hi) * 0.2f;
        lo += (Math.max(0f, Math.min(mn * 0.85f, TARGET * 0.5f)) - lo) * 0.2f;
    }

    /** Smooth line with a faint fill below it, one pixel column at a time. */
    private void plot(GuiGraphics g, Theme t, int px, int py, int pw, int ph) {
        float range = Math.max(10f, hi - lo);
        // reference line at the target FPS
        float tk = (TARGET - lo) / range;
        if (tk > 0.02f && tk < 0.98f) {
            int ty = py + ph - 2 - Math.round((ph - 3) * tk);
            Draw.fill(g, px, ty, px + pw, ty + 1, 0x28000000 | t.cDim);
        }
        if (smN < 3) {
            Draw.textC(g, "collecting...", px + pw / 2, py + ph / 2 - 4, t.cDim);
            return;
        }
        int prev = Integer.MIN_VALUE;
        for (int i = 0; i < pw; i++) {
            float f = i * (smN - 1) / (float) Math.max(1, pw - 1);
            int k0 = (int) f;
            int k1 = Math.min(smN - 1, k0 + 1);
            float v = sm[k0] + (sm[k1] - sm[k0]) * (f - k0);
            float k = Math.max(0f, Math.min(1f, (v - lo) / range));
            int yy = py + ph - 2 - Math.round((ph - 3) * k);
            int rgb = colorFor(v);
            Draw.fill(g, px + i, yy + 2, px + i + 1, py + ph, 0x22000000 | rgb);
            int from = prev == Integer.MIN_VALUE ? yy : prev;
            Draw.fill(g, px + i, Math.min(from, yy), px + i + 1, Math.max(from, yy) + 2, 0xFF000000 | rgb);
            prev = yy;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        Theme t = ThemeManager.current();
        Minecraft mc = Minecraft.getInstance();
        Draw.textShadow(g, "FPS Boost", x + 4, y + 2, t.cAccent2);
        Draw.text(g, c().active ? "Active" : "Off (vanilla settings)", x + 90, y + 2, c().active ? t.cAccent : t.cDim);

        // presets
        String[] names = {"Balanced", "Max FPS", "Crystal PvP", "Restore vanilla"};
        int bx = x;
        for (int i = 0; i < names.length; i++) {
            int bw = Draw.font().width(names[i]) + 22;
            Ui.button(g, t, names[i], bx, y + 18, bw, 20, Ui.in(mx, my, bx, y + 18, bw, 20), i == 2);
            bx += bw + 6;
        }

        // graph card: big FPS + frame time on top, smooth plot, AVG / BEST / LOW below
        long nowNs = System.nanoTime();
        float cur = mc.getFps();
        if (lastNs != 0) {
            float dt = Math.min(0.25f, (nowNs - lastNs) / 1e9f);
            shown += (cur - shown) * Math.min(1f, dt * 5f);
        } else {
            shown = cur;
        }
        lastNs = nowNs;
        long now = System.currentTimeMillis();
        if (now >= nextSample) {
            nextSample = now + STEP_MS;
            sample(cur);
        }
        int gw = 230, gx = x + w - gw, gy = y + 46;
        int gh = Math.max(96, Math.min(150, h - 78 - 46 - 8)); // never reaches the installer panel
        Draw.rr(g, gx, gy, gw, gh, t.radius, t.aPanel);
        Draw.ring(g, gx, gy, gw, gh, t.radius, t.aBorder);

        int main = colorFor(shown);
        String big = Integer.toString(Math.round(shown));
        g.pose().pushMatrix();
        g.pose().translate((float) (gx + 10), (float) (gy + 7));
        g.pose().scale(1.5f, 1.5f);
        Draw.textShadow(g, big, 0, 0, main);
        g.pose().popMatrix();
        int bigW = Math.round(Draw.font().width(big) * 1.5f);
        Draw.text(g, "FPS", gx + 10 + bigW + 4, gy + 12, t.cDim);
        String ms = shown > 0.5f ? String.format(java.util.Locale.ROOT, "%.1f ms", 1000f / shown) : "- ms";
        Draw.textR(g, ms, gx + gw - 10, gy + 12, t.cDim);

        int py = gy + 26, ph = gh - 26 - 22;
        plot(g, t, gx + 10, py, gw - 20, ph);

        int sy = gy + gh - 16;
        Draw.text(g, "AVG " + Math.round(avg), gx + 10, sy, t.cText);
        Draw.textC(g, "BEST " + Math.round(best), gx + gw / 2, sy, t.cDim);
        Draw.textR(g, "LOW " + Math.round(low), gx + gw - 10, sy, 0xFFC46B);

        // options (two columns on the left)
        int colW = (w - 230 - 18) / 2;
        int oy = y + 46;
        for (int i = 0; i < opts.size(); i++) {
            int ox = x + (i % 2) * (colW + 8);
            int ry = oy + (i / 2) * 26;
            Opt o = opts.get(i);
            boolean hover = Ui.in(mx, my, ox, ry, colW, 22);
            Ui.card(g, t, ox, ry, colW, 22, hover, 0f);
            Draw.text(g, Draw.fit(o.name, colW - 44), ox + 8, ry + 7, t.cText);
            Ui.toggle(g, t, ox + colW - 34, ry + 5, o.get.get(), o.get.get() ? 1f : 0f);
        }

        // sliders
        int sly = oy + ((opts.size() + 1) / 2) * 26 + 6;
        sliderX = x + 130;
        sliderW = w - 230 - 18 - 130 - 50;
        drawSlider(g, t, mx, my, 0, "Render distance", sly, c().renderDistance + " chunks", (c().renderDistance - 2) / 30f);
        drawSlider(g, t, mx, my, 1, "Entity distance", sly + 22, Math.round(c().entityDistance * 100) + "%", (c().entityDistance - 0.5f) / 4.5f);
        drawSlider(g, t, mx, my, 2, "Biome blend", sly + 44, Integer.toString(c().biomeBlend), c().biomeBlend / 7f);

        // crystal pvp installer
        int ipy = y + h - 78;
        int pw = w;
        Draw.rr(g, x, ipy, pw, 74, t.radius, t.aPanel);
        Draw.ring(g, x, ipy, pw, 74, t.radius, t.aBorder);
        Draw.textShadow(g, "Crystal PvP preset", x + 10, ipy + 8, t.cAccent2);
        Draw.text(g, "One click: boost settings, PvP HUD, and optimizer mods from Modrinth (Sodium, Lithium, ...).", x + 10, ipy + 21, t.cDim);
        int ib = x + 10;
        String label = busy ? "Working..." : "Apply and install";
        int iw = Draw.font().width(label) + 24;
        Ui.button(g, t, label, ib, ipy + 36, iw, 20, !busy && Ui.in(mx, my, ib, ipy + 36, iw, 20), !busy);
        if (needsRestart) {
            String rl = "Restart now";
            int rw = Draw.font().width(rl) + 24;
            Ui.button(g, t, rl, ib + iw + 8, ipy + 36, rw, 20, Ui.in(mx, my, ib + iw + 8, ipy + 36, rw, 20), true);
        }
        String st = status;
        if (!restartError.isEmpty()) st = restartError;
        if (!st.isEmpty()) Draw.text(g, Draw.fit(st, pw - 20), x + 10, ipy + 60, t.cText);
    }

    private void drawSlider(GuiGraphics g, Theme t, int mx, int my, int idx, String label, int sy, String value, float frac) {
        Draw.text(g, label, x + 4, sy + 3, t.cText);
        Ui.slider(g, t, sliderX, sy + 1, sliderW, Math.max(0f, Math.min(1f, frac)), dragSlider == idx || Ui.in(mx, my, sliderX, sy, sliderW, 14));
        Draw.text(g, value, sliderX + sliderW + 8, sy + 3, t.cAccent2);
    }

    private void setSlider(int idx, double mx) {
        float f = Ui.sliderFrac(mx, sliderX, sliderW);
        switch (idx) {
            case 0 -> c().renderDistance = 2 + Math.round(f * 30);
            case 1 -> c().entityDistance = Math.round((0.5f + f * 4.5f) * 20f) / 20f;
            case 2 -> c().biomeBlend = Math.round(f * 7);
        }
        c().active = true;
        Config.markDirty();
        FpsBoost.apply();
    }

    @Override
    public boolean click(double mx, double my, int button) {
        // presets
        String[] names = {"Balanced", "Max FPS", "Crystal PvP", "Restore vanilla"};
        int bx = x;
        for (int i = 0; i < names.length; i++) {
            int bw = Draw.font().width(names[i]) + 22;
            if (Ui.in(mx, my, bx, y + 18, bw, 20)) {
                switch (i) {
                    case 0 -> FpsBoost.preset(FpsBoost.Preset.BALANCED);
                    case 1 -> FpsBoost.preset(FpsBoost.Preset.MAX_FPS);
                    case 2 -> FpsBoost.preset(FpsBoost.Preset.CRYSTAL_PVP);
                    default -> FpsBoost.restoreVanilla();
                }
                return true;
            }
            bx += bw + 6;
        }
        int colW = (w - 230 - 18) / 2;
        int oy = y + 46;
        for (int i = 0; i < opts.size(); i++) {
            int ox = x + (i % 2) * (colW + 8);
            int ry = oy + (i / 2) * 26;
            if (Ui.in(mx, my, ox, ry, colW, 22)) {
                Opt o = opts.get(i);
                o.set.accept(!o.get.get());
                changed();
                return true;
            }
        }
        int sy = oy + ((opts.size() + 1) / 2) * 26 + 6;
        for (int i = 0; i < 3; i++) {
            if (Ui.in(mx, my, sliderX - 4, sy + i * 22, sliderW + 8, 16)) {
                dragSlider = i;
                setSlider(i, mx);
                return true;
            }
        }
        int py = y + h - 78;
        String label = busy ? "Working..." : "Apply and install";
        int iw = Draw.font().width(label) + 24;
        if (!busy && Ui.in(mx, my, x + 10, py + 36, iw, 20)) {
            startCrystalInstall();
            return true;
        }
        if (needsRestart) {
            String rl = "Restart now";
            int rw = Draw.font().width(rl) + 24;
            if (Ui.in(mx, my, x + 10 + iw + 8, py + 36, rw, 20)) {
                String err = Restarter.restart(Minecraft.getInstance());
                restartError = err == null ? "" : err;
                return true;
            }
        }
        return false;
    }

    private void startCrystalInstall() {
        busy = true;
        needsRestart = false;
        restartError = "";
        FpsBoost.preset(FpsBoost.Preset.CRYSTAL_PVP);
        for (String id : new String[]{"fps", "ping", "cps", "armor", "totems", "crystals", "hitcolor"}) {
            Module m = ModuleManager.byId(id);
            if (m != null) m.setEnabled(true);
        }
        Config.markDirty();
        status = "Preparing...";
        Installer.installAll(FpsBoost.OPTIMIZER_MODS, Installer.Kind.MOD, s -> status = s).whenComplete((r, ex) -> {
            busy = false;
            if (ex != null) {
                status = "Failed: " + Installer.message(ex);
                return;
            }
            needsRestart = r.needsRestart;
            int ok = r.files.size();
            status = ok == 0
                    ? "Nothing new was installed. " + String.join("; ", r.notes)
                    : "Installed " + ok + " file(s). Restart Minecraft to load them.";
            CodexClient.LOG.info("Crystal PvP install: {}", r.notes);
        });
    }

    @Override
    public void drag(double mx, double my) {
        if (dragSlider >= 0) setSlider(dragSlider, mx);
    }

    @Override
    public void release() {
        dragSlider = -1;
    }
}
