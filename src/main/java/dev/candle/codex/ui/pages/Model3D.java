package dev.candle.codex.ui.pages;

import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import dev.candle.codex.cosmetics.AnimCapes;
import dev.candle.codex.cosmetics.Auras;
import dev.candle.codex.cosmetics.CosmeticLayer;
import dev.candle.codex.cosmetics.Cosmetics;
import dev.candle.codex.cosmetics.Glitchy;
import dev.candle.codex.cosmetics.Pets;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Img;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 3D pictures for the Cosmetics menu. Nothing here needs a player model: every hat, glasses, wing, pet and aura is built
 * from exactly the same boxes as the in-game model (CosmeticLayer / Pets / Auras) and drawn with depth sorting,
 * hidden-face removal and lighting.
 * <p>
 * 1.5.1: the models no longer spin. Each card shows a fixed three-quarter view, like a shop picture. To keep the menu
 * fast, every model is drawn ONCE into a small picture (baked, no overdraw) and that picture is just blitted afterwards.
 * Only the card under the mouse and the selected card are drawn live, so animations still play where you look.
 * Capes are shown as a standing cloth slab with the real (animated) cape texture.
 * If anything ever throws, draw() returns false once and for all and the page falls back to the old flat pictures.
 */
final class Model3D {
    /** Camera looks slightly down on the model (radians). */
    private static final float PITCH = 0.30f;
    /** Fixed turn of every model (radians): a calm three-quarter view. */
    private static final float YAW = 0.50f;
    private static final float MAX_SCALE = 5f;
    /** Radius (screen pixels) of the sphere every model has to fit in. */
    private static final float RADIUS_PX = 40f;
    /** Size of a baked picture and the position of the model centre in it. */
    private static final int BAKE = 96, BAKE_C = 48;
    /** Direction towards the light (view space: x right, y down, z away from the viewer). */
    private static final float LX = -0.25f, LY = -0.65f, LZ = -0.72f;
    private static final long START_NS = System.nanoTime();
    private static boolean broken;

    /** When set, rectangles are painted into this array (BAKE x BAKE) instead of the screen. */
    private static int[] raster;
    private static final Map<String, Identifier> BAKED = new HashMap<>();
    private static final Set<String> BAKE_FAILED = new HashSet<>();

    private Model3D() {}

    // ------------------------------------------------------------------ entry points

    /** Still picture (used by the Glitchy banner). */
    static boolean draw(GuiGraphics g, int tab, Cosmetics.Entry e, int cx, int cy) {
        return draw(g, tab, e, cx, cy, false);
    }

    /**
     * Draws entry e of cosmetics tab "tab" centred at cx, cy. live = the card is hovered or selected, so animated models
     * move; every other card shows a baked still. False means the caller should draw the old flat picture.
     */
    static boolean draw(GuiGraphics g, int tab, Cosmetics.Entry e, int cx, int cy, boolean live) {
        if (broken) return false;
        try {
            switch (tab) {
                case 0 -> cape(g, e, cx, cy);
                case 1 -> hat(g, e, cx, cy, live);
                case 2 -> wings(g, e, cx, cy, live);
                case 3 -> glasses(g, e, cx, cy, live);
                case 4 -> pet(g, e, cx, cy, live);
                case 5 -> aura(g, e, cx, cy, live);
                default -> {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException ex) {
            broken = true;
            CodexClient.LOG.warn("Codex: 3D menu pictures switched off, using flat pictures", ex);
            return false;
        }
    }

    // ------------------------------------------------------------------ clocks

    /** Game ticks (50 ms) since the first call; stays small, so float precision is never a problem. */
    private static float ticks() {
        return (System.nanoTime() - START_NS) / 50_000_000f;
    }

    private static boolean animating() {
        return Config.data.animSpeed > 0f;
    }

    private static float yaw() {
        return YAW;
    }

    private static float dragonPhase;
    private static long dragonLastNs;

    /** Dragon flap clock, same rule as in the game: advances with the speed setting. */
    private static float dragonClock() {
        long now = System.nanoTime();
        float dt = dragonLastNs == 0L ? 0f : (now - dragonLastNs) / 1_000_000f / 50f;
        dragonLastNs = now;
        if (!(dt >= 0f && dt < 5f)) dt = 0f;
        float speed = Config.data.cosmetics.dragonSpeed;
        if (!(speed >= 0.2f && speed <= 3f)) speed = 1f;
        if (animating()) dragonPhase += dt * speed;
        if (dragonPhase > 1000f) dragonPhase -= 392.699f; // ten full periods of sin(phase * 0.16)
        return dragonPhase;
    }

    // ------------------------------------------------------------------ models

    /** A pile of boxes (centre and half sizes in model pixels, +Y down, front is -Z). */
    private static final class Scene implements Pets.Sink {
        float[] x = new float[256], y = new float[256], z = new float[256];
        float[] hx = new float[256], hy = new float[256], hz = new float[256];
        int[] rgb = new int[256];
        boolean[] glow = new boolean[256];
        /** Per frame scratch: screen position and sort keys. */
        float[] sx = new float[256], sy = new float[256];
        long[] keys = new long[256];
        int n;

        void add(float cx, float cy, float cz, float bx, float by, float bz, int c, boolean gl) {
            if (n == x.length) grow(n * 2);
            x[n] = cx;
            y[n] = cy;
            z[n] = cz;
            hx[n] = bx;
            hy[n] = by;
            hz[n] = bz;
            rgb[n] = c;
            glow[n] = gl;
            n++;
        }

        private void grow(int m) {
            x = Arrays.copyOf(x, m);
            y = Arrays.copyOf(y, m);
            z = Arrays.copyOf(z, m);
            hx = Arrays.copyOf(hx, m);
            hy = Arrays.copyOf(hy, m);
            hz = Arrays.copyOf(hz, m);
            rgb = Arrays.copyOf(rgb, m);
            glow = Arrays.copyOf(glow, m);
            sx = Arrays.copyOf(sx, m);
            sy = Arrays.copyOf(sy, m);
            keys = Arrays.copyOf(keys, m);
        }

        @Override
        public void box(float cx, float cy, float cz, float bx, float by, float bz, int c, boolean gl) {
            add(cx, cy, cz, bx, by, bz, c, gl);
        }
    }

    /** One wing of a pair: moves the wing to the shoulder (1.4 px out, 1.8 px down, 3.7 px behind) like the 3D model. */
    private static final class WingAdapter implements CosmeticLayer.WingSink {
        private final Scene scene;
        private final float side;

        WingAdapter(Scene scene, int side) {
            this.scene = scene;
            this.side = side;
        }

        @Override
        public void box(float cx, float cy, float cz, float hx, float hy, int rgb, boolean glow) {
            box(cx, cy, cz, hx, hy, 0.3f, rgb, glow);
        }

        @Override
        public void box(float cx, float cy, float cz, float hx, float hy, float hz, int rgb, boolean glow) {
            scene.add(cx + side * 1.4f, cy + 1.8f, cz + 3.7f, hx, hy, hz, rgb, glow);
        }
    }

    private interface Builder {
        void build(float t, Scene s);
    }

    private static final class Model {
        final Scene scene = new Scene();
        Builder builder;
        boolean animated, dragon;
        /** Centre of the turn and radius of the bounding sphere over a whole animation. */
        float px, py, pz, r = 10f;
        long builtMs;
    }

    private static final Map<String, Model> MODELS = new HashMap<>();

    /** Finds or creates a model; the first time it also measures the box over several animation times so the size stays fixed. */
    private static Model model(String key, boolean animated, boolean dragon, Builder b) {
        Model m = MODELS.get(key);
        if (m != null) return m;
        m = new Model();
        m.builder = b;
        m.animated = animated;
        m.dragon = dragon;
        Scene tmp = new Scene();
        int samples = animated ? 10 : 1;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int k = 0; k < samples; k++) {
            tmp.n = 0;
            b.build(k * 10f, tmp);
            for (int i = 0; i < tmp.n; i++) {
                minX = Math.min(minX, tmp.x[i] - tmp.hx[i]);
                maxX = Math.max(maxX, tmp.x[i] + tmp.hx[i]);
                minY = Math.min(minY, tmp.y[i] - tmp.hy[i]);
                maxY = Math.max(maxY, tmp.y[i] + tmp.hy[i]);
                minZ = Math.min(minZ, tmp.z[i] - tmp.hz[i]);
                maxZ = Math.max(maxZ, tmp.z[i] + tmp.hz[i]);
            }
        }
        if (minX > maxX) {
            m.px = 0f;
            m.py = 0f;
            m.pz = 0f;
        } else {
            m.px = (minX + maxX) / 2f;
            m.py = (minY + maxY) / 2f;
            m.pz = (minZ + maxZ) / 2f;
        }
        float r2 = 4f;
        for (int k = 0; k < samples; k++) {
            tmp.n = 0;
            b.build(k * 10f, tmp);
            for (int i = 0; i < tmp.n; i++) {
                float dx = tmp.x[i] - m.px, dy = tmp.y[i] - m.py, dz = tmp.z[i] - m.pz;
                float reach = (float) Math.sqrt(dx * dx + dy * dy + dz * dz)
                        + (float) Math.sqrt(tmp.hx[i] * tmp.hx[i] + tmp.hy[i] * tmp.hy[i] + tmp.hz[i] * tmp.hz[i]);
                r2 = Math.max(r2, reach);
            }
        }
        m.r = r2;
        MODELS.put(key, m);
        return m;
    }

    /** Rebuilds the boxes when the model is animated (about 20 times a second, only for live cards). */
    private static void refresh(Model m) {
        long now = System.currentTimeMillis();
        boolean live = m.animated && animating();
        if (m.scene.n == 0 || (live && now - m.builtMs >= 50L)) {
            m.scene.n = 0;
            float t = m.dragon ? dragonClock() : (animating() ? ticks() : 6f);
            m.builder.build(t, m.scene);
            m.builtMs = now;
        }
    }

    // ------------------------------------------------------------------ baking

    /** Draws the model at rest into a BAKE x BAKE picture once and returns its texture, or null when that is not possible. */
    private static Identifier baked(String key, Model m) {
        Identifier id = BAKED.get(key);
        if (id != null) return id;
        if (BAKE_FAILED.contains(key)) return null;
        try {
            m.scene.n = 0;
            m.builder.build(6f, m.scene);
            m.builtMs = 0L;
            int[] px = new int[BAKE * BAKE];
            raster = px;
            try {
                render(null, m, BAKE_C, BAKE_C);
            } finally {
                raster = null;
            }
            id = AnimCapes.registerPicture("menu_" + key.replace(':', '_'), BAKE, BAKE, px);
        } catch (RuntimeException ex) {
            raster = null;
            id = null;
        }
        if (id == null) BAKE_FAILED.add(key);
        else BAKED.put(key, id);
        return id;
    }

    /** Live (animated, hovered or selected) or baked still. */
    private static void show(GuiGraphics g, String key, Model m, int cx, int cy, boolean live) {
        if (live && m.animated && animating()) {
            refresh(m);
            render(g, m, cx, cy);
            return;
        }
        Identifier id = baked(key, m);
        if (id != null) {
            Img.region(g, id, cx - BAKE_C, cy - BAKE_C, BAKE, BAKE, 0f, 0f, BAKE, BAKE, BAKE, BAKE);
            return;
        }
        refresh(m);
        render(g, m, cx, cy);
    }

    // ------------------------------------------------------------------ the six kinds

    private static void hat(GuiGraphics g, Cosmetics.Entry e, int cx, int cy, boolean live) {
        String key = "hat:" + e.id();
        Model m = model(key, "halo".equals(e.id()) || Glitchy.is(e.id()), false, (t, s) -> CosmeticLayer.previewHat(e, t, s));
        show(g, key, m, cx, cy, live);
    }

    private static void glasses(GuiGraphics g, Cosmetics.Entry e, int cx, int cy, boolean live) {
        String key = "glasses:" + e.id();
        Model m = model(key, false, false, (t, s) -> CosmeticLayer.previewGlasses(e, s));
        show(g, key, m, cx, cy, live);
    }

    private static void wings(GuiGraphics g, Cosmetics.Entry e, int cx, int cy, boolean live) {
        String key = "wings:" + e.id();
        boolean dragon = "dragon".equals(e.id());
        Model m = model(key, true, dragon, (t, s) -> {
            CosmeticLayer.previewWing(e, 1, t, t, new WingAdapter(s, 1));
            CosmeticLayer.previewWing(e, -1, t, t, new WingAdapter(s, -1));
        });
        show(g, key, m, cx, cy, live);
    }

    private static void pet(GuiGraphics g, Cosmetics.Entry e, int cx, int cy, boolean live) {
        String key = "pet:" + e.id();
        Model m = model(key, true, false, (t, s) -> Pets.build(e, t, s));
        show(g, key, m, cx, cy, live);
    }

    private static void aura(GuiGraphics g, Cosmetics.Entry e, int cx, int cy, boolean live) {
        String key = "aura:" + e.id();
        Model m = model(key, true, false, (t, s) -> Auras.build(e, t, s));
        show(g, key, m, cx, cy, live);
    }

    // ------------------------------------------------------------------ box renderer

    /** Fills a rectangle on the screen, or in the baked picture when baking. */
    private static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        int[] r = raster;
        if (r == null) {
            Draw.fill(g, x0, y0, x1, y1, argb);
            return;
        }
        int a = Math.max(0, x0), b = Math.max(0, y0), c = Math.min(BAKE, x1), d = Math.min(BAKE, y1);
        for (int y = b; y < d; y++) {
            int o = y * BAKE;
            for (int x = a; x < c; x++) r[o + x] = argb;
        }
    }

    private static int col(int rgb, float b) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * b));
        int gr = Math.min(255, Math.round(((rgb >> 8) & 255) * b));
        int bl = Math.min(255, Math.round((rgb & 255) * b));
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    private static float light(float nx, float ny, float nz) {
        float d = nx * LX + ny * LY + nz * LZ;
        return 0.45f + 0.55f * Math.max(0f, d);
    }

    private static void render(GuiGraphics g, Model m, int cx, int cy) {
        Scene sc = m.scene;
        int n = sc.n;
        if (n == 0) return;
        float s = Math.min(MAX_SCALE, RADIUS_PX / Math.max(4f, m.r));
        float yaw = yaw();
        float cY = (float) Math.cos(yaw), sY = (float) Math.sin(yaw);
        float cP = (float) Math.cos(PITCH), sP = (float) Math.sin(PITCH);
        // the three box axes after turning and tilting (x, y on screen, z = depth, larger is farther away)
        float exX = cY, exY = sY * sP, exZ = -sY * cP;
        float eyX = 0f, eyY = cP, eyZ = sP;
        float ezX = sY, ezY = -cY * sP, ezZ = cY * cP;

        for (int i = 0; i < n; i++) {
            float dx = sc.x[i] - m.px, dy = sc.y[i] - m.py, dz = sc.z[i] - m.pz;
            float x1 = dx * cY + dz * sY;
            float z1 = -dx * sY + dz * cY;
            float sx = x1;
            float sy = dy * cP - z1 * sP;
            float depth = dy * sP + z1 * cP;
            sc.sx[i] = cx + sx * s;
            sc.sy[i] = cy + sy * s;
            sc.keys[i] = ((long) Math.round((depth + 512f) * 128f) << 32) | i;
        }
        Arrays.sort(sc.keys, 0, n);

        for (int k = n - 1; k >= 0; k--) { // far boxes first
            int i = (int) (sc.keys[k] & 0xFFFFFFFFL);
            float px = sc.sx[i], py = sc.sy[i];
            float bx = sc.hx[i] * s, by = sc.hy[i] * s, bz = sc.hz[i] * s;
            // half vectors of the box on screen
            float ax = exX * bx, ay = exY * bx;
            float bxx = eyX * by, byy = eyY * by;
            float cxx = ezX * bz, cyy = ezY * bz;
            float extX = Math.abs(ax) + Math.abs(bxx) + Math.abs(cxx);
            float extY = Math.abs(ay) + Math.abs(byy) + Math.abs(cyy);
            int c = sc.rgb[i];
            boolean gl = sc.glow[i];
            if (extX <= 1.3f && extY <= 1.3f) { // too small to show faces: one dot
                int x0 = Math.round(px - extX), x1 = Math.round(px + extX);
                int y0 = Math.round(py - extY), y1 = Math.round(py + extY);
                if (x1 <= x0) x1 = x0 + 1;
                if (y1 <= y0) y1 = y0 + 1;
                rect(g, x0, y0, x1, y1, col(c, gl ? 1f : 0.85f));
                continue;
            }
            // +X / -X faces (axis A = x, the others are y and z)
            if (exZ < 0f) face(g, px, py, ax, ay, bxx, byy, cxx, cyy, 1f, c, gl ? 1f : light(exX, exY, exZ));
            else if (exZ > 0f) face(g, px, py, ax, ay, bxx, byy, cxx, cyy, -1f, c, gl ? 1f : light(-exX, -exY, -exZ));
            // +Y / -Y faces (top is -Y)
            if (eyZ < 0f) face(g, px, py, bxx, byy, ax, ay, cxx, cyy, 1f, c, gl ? 1f : light(eyX, eyY, eyZ));
            else if (eyZ > 0f) face(g, px, py, bxx, byy, ax, ay, cxx, cyy, -1f, c, gl ? 1f : light(-eyX, -eyY, -eyZ));
            // +Z / -Z faces (front is -Z)
            if (ezZ < 0f) face(g, px, py, cxx, cyy, ax, ay, bxx, byy, 1f, c, gl ? 1f : light(ezX, ezY, ezZ));
            else if (ezZ > 0f) face(g, px, py, cxx, cyy, ax, ay, bxx, byy, -1f, c, gl ? 1f : light(-ezX, -ezY, -ezZ));
        }
    }

    private static final float[] QX = new float[4], QY = new float[4];

    /**
     * One face of a box. (ax, ay) is the half vector of the axis the face is perpendicular to, sign picks which of the two
     * faces; (bx, by) and (cx, cy) are the half vectors that span the face.
     */
    private static void face(GuiGraphics g, float px, float py, float ax, float ay, float bx, float by, float cx, float cy,
                             float sign, int rgb, float bright) {
        float mx = px + sign * ax, my = py + sign * ay;
        QX[0] = mx + bx + cx;
        QY[0] = my + by + cy;
        QX[1] = mx - bx + cx;
        QY[1] = my - by + cy;
        QX[2] = mx - bx - cx;
        QY[2] = my - by - cy;
        QX[3] = mx + bx - cx;
        QY[3] = my + by - cy;
        fillQuad(g, col(rgb, bright));
    }

    /** Fills a convex quad with horizontal runs (equal neighbouring rows are merged into one rectangle). */
    private static void fillQuad(GuiGraphics g, int argb) {
        float minY = Math.min(Math.min(QY[0], QY[1]), Math.min(QY[2], QY[3]));
        float maxY = Math.max(Math.max(QY[0], QY[1]), Math.max(QY[2], QY[3]));
        int rowA = (int) Math.floor(minY + 0.5f);
        int rowB = (int) Math.floor(maxY - 0.5f);
        if (rowB < rowA) { // thinner than one pixel: a single row in the middle
            rowA = (int) Math.floor((minY + maxY) / 2f);
            rowB = rowA;
        }
        boolean run = false;
        int runX0 = 0, runX1 = 0, runY0 = 0, runY1 = 0;
        for (int row = rowA; row <= rowB; row++) {
            float yc = Math.max(minY, Math.min(maxY, row + 0.5f));
            float xa = Float.MAX_VALUE, xb = -Float.MAX_VALUE;
            for (int e = 0; e < 4; e++) {
                int f = (e + 1) & 3;
                float ya = QY[e], yb = QY[f];
                if (ya == yb) {
                    if (Math.abs(yc - ya) < 0.0001f) {
                        xa = Math.min(xa, Math.min(QX[e], QX[f]));
                        xb = Math.max(xb, Math.max(QX[e], QX[f]));
                    }
                    continue;
                }
                if (yc >= Math.min(ya, yb) && yc <= Math.max(ya, yb)) {
                    float xx = QX[e] + (yc - ya) / (yb - ya) * (QX[f] - QX[e]);
                    xa = Math.min(xa, xx);
                    xb = Math.max(xb, xx);
                }
            }
            if (xb < xa) continue;
            int ix0 = Math.round(xa), ix1 = Math.round(xb);
            if (ix1 <= ix0) ix1 = ix0 + 1;
            if (run && ix0 == runX0 && ix1 == runX1 && row == runY1 + 1) {
                runY1 = row;
            } else {
                if (run) rect(g, runX0, runY0, runX1, runY1 + 1, argb);
                run = true;
                runX0 = ix0;
                runX1 = ix1;
                runY0 = row;
                runY1 = row;
            }
        }
        if (run) rect(g, runX0, runY0, runX1, runY1 + 1, argb);
    }

    // ------------------------------------------------------------------ capes: a standing cloth slab

    private static final int CAPE_W = 40, CAPE_H = 64, CAPE_D = 3;

    /**
     * The real cape texture (outer face 10 x 16 units at 1, 1 of a 64 x 32 layout, animated frames included) shown as a
     * standing slab: rod on top, soft shadow, a darker side and bottom edge for thickness. It does not turn. One blit.
     */
    private static void cape(GuiGraphics g, Cosmetics.Entry e, int cx, int cy) {
        Identifier file = Cosmetics.capeFile(Cosmetics.liveKey(e.id()));
        int x = cx - CAPE_W / 2 - 1, y = cy - CAPE_H / 2;
        // soft shadow on the card
        Draw.fill(g, x + 3, y + 4, x + CAPE_W + CAPE_D + 3, y + CAPE_H + CAPE_D + 4, 0x44000000);
        // thickness: right edge and bottom edge
        Draw.fill(g, x + CAPE_W, y + 2, x + CAPE_W + CAPE_D, y + CAPE_H + CAPE_D, 0xFF15151D);
        Draw.fill(g, x + 2, y + CAPE_H, x + CAPE_W + CAPE_D, y + CAPE_H + CAPE_D, 0xFF1D1D27);
        // the cloth
        Img.region(g, file, x, y, CAPE_W, CAPE_H, 1f, 1f, 10, 16, 64, 32);
        Draw.ring(g, x, y, CAPE_W, CAPE_H, 2, 0x55FFFFFF);
        // the rod the cape hangs from
        Draw.fill(g, x - 2, y - 3, x + CAPE_W + CAPE_D + 2, y - 1, 0xFF2B2B33);
        Draw.fill(g, x - 2, y - 3, x + CAPE_W + CAPE_D + 2, y - 2, 0xFF6A6A78);
    }
}
