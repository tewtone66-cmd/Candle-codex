package dev.candle.codex.cosmetics;

import java.util.List;

/**
 * The "Glitchy Cosmetics" pack: one cosmetic per tab, all with a broken-screen look (cyan / magenta colour split, jumping
 * pixels, scan lines). Like Pets and Auras every builder is pure geometry: it sends boxes to a Pets.Sink, so the game model
 * and the 3D menu picture always show the same thing. Every id starts with "glitch", so is(id) tells pack members apart.
 * Glitches happen in short "slots" (about 150 ms): a slot number picks, with a stable hash, which parts jump.
 */
public final class Glitchy {
    public static final int CYAN = 0x00F0FF, MAGENTA = 0xFF2DAA, WHITE = 0xFFFFFF, DARK = 0x0B0B14;

    public static final Cosmetics.Entry CAPE = new Cosmetics.Entry("glitchcape", "Glitch Cape", CYAN, MAGENTA);
    public static final Cosmetics.Entry HAT = new Cosmetics.Entry("glitchcubes", "Glitch Cubes", CYAN, MAGENTA);
    public static final Cosmetics.Entry GLASSES = new Cosmetics.Entry("glitchvisor", "Glitch Visor", CYAN, MAGENTA);
    public static final Cosmetics.Entry WINGS = new Cosmetics.Entry("glitchwings", "Glitch Wings", CYAN, MAGENTA);
    public static final Cosmetics.Entry PET = new Cosmetics.Entry("glitchcube", "Glitch Cube", CYAN, MAGENTA);
    public static final Cosmetics.Entry AURA = new Cosmetics.Entry("glitchrain", "Glitch Rain", CYAN, MAGENTA);

    /** All members of the pack, in the order of the menu tabs (cape, hat, wings, glasses, pet, aura). */
    public static final List<Cosmetics.Entry> PACK = List.of(CAPE, HAT, WINGS, GLASSES, PET, AURA);

    private static final float TAU = 6.2831855f;

    private Glitchy() {}

    /** True for the cosmetics of this pack. */
    public static boolean is(String id) {
        return id != null && id.startsWith("glitch");
    }

    // ------------------------------------------------------------------ helpers

    /** Stable pseudo random number 0..1 for an integer. */
    static float rnd(int n) {
        n ^= n >>> 16;
        n *= 0x7feb352d;
        n ^= n >>> 15;
        n *= 0x846ca68b;
        n ^= n >>> 16;
        return (n >>> 8) / 16777216f;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float sin(float v) {
        return (float) Math.sin(v);
    }

    private static float cos(float v) {
        return (float) Math.cos(v);
    }

    /** Glitch slot of game time t (changes about every 150 ms). */
    private static int slot(float t) {
        return (int) (t * 0.3f);
    }

    // ------------------------------------------------------------------ hat: ring of floating cubes with colour split

    /** Head model space in pixels, up is negative Y, head top is y = -8.5. */
    public static void hat(Cosmetics.Entry e, float t, Pets.Sink s) {
        final int a = e.c1(), b = e.c2();
        final int slot = slot(t);
        s.box(0, -8.8f, 0, 4.9f, 0.35f, 4.9f, DARK, false);                      // dark band on the head
        s.box(0, -9.4f, 0, 4.85f, 0.18f, 4.85f, a, true);                         // glowing line
        s.box(0.35f, -9.65f, 0, 4.85f, 0.1f, 4.85f, b, true);                     // the same line, split
        for (int i = 0; i < 6; i++) {                                             // six cubes circle the head
            float ang = i * TAU / 6f + t * 0.03f;
            boolean jump = rnd(slot * 11 + i) > 0.72f;
            float jx = jump ? (rnd(slot * 3 + i) - 0.5f) * 3f : 0f;
            float jy = jump ? (rnd(slot * 5 + i) - 0.5f) * 2f : 0f;
            float x = cos(ang) * 4.4f + jx, z = sin(ang) * 4.4f;
            float y = -12.5f - (i % 3) * 1.6f + sin(t * 0.1f + i) * 0.6f + jy;
            float sz = 0.8f + 0.25f * (i % 2);
            s.box(x - 0.45f, y, z, sz, sz, sz, b, true);                          // magenta ghost behind
            s.box(x + 0.45f, y, z, sz, sz, sz, a, true);                          // cyan cube in front
        }
        for (int k = -1; k <= 1; k++) {                                           // three bars that flicker in height
            float h = 1.6f + 2.4f * rnd(slot * 2 + k + 9);
            s.box(k * 1.9f, -9.4f - h, 0, 0.3f, h, 0.3f, k == 0 ? WHITE : a, true);
        }
    }

    // ------------------------------------------------------------------ glasses: visor with a split lens

    /** Head model space in pixels: the front of the head is z = -4.5, the eyes are around y = -3.7. */
    public static void glasses(Cosmetics.Entry e, Pets.Sink s) {
        final int a = e.c1(), b = e.c2();
        final float z = -5.25f, cy = -3.7f;
        s.box(0, cy, z, 4.6f, 1.45f, 0.2f, DARK, false);                          // visor frame
        for (int i = 0; i < 8; i++) {                                             // lens: gradient from cyan to magenta
            s.box(-3.5f + i, cy, z - 0.18f, 0.5f, 1.1f, 0.12f, Pets.mix(a, b, i / 7f), true);
        }
        s.box(-1.2f, cy - 0.45f, z - 0.34f, 2.4f, 0.16f, 0.05f, a, true);         // colour-split scan bars
        s.box(1.4f, cy + 0.4f, z - 0.34f, 2.1f, 0.16f, 0.05f, b, true);
        s.box(2.8f, cy - 0.2f, z - 0.34f, 0.5f, 0.12f, 0.05f, WHITE, true);
        for (int sd = -1; sd <= 1; sd += 2) {                                     // temples
            s.box(sd * 5.05f, -4.1f, -2.6f, 0.22f, 0.3f, 2.7f, DARK, false);
            s.box(sd * 4.65f, -4.1f, -5.25f, 0.45f, 0.3f, 0.25f, DARK, false);
        }
    }

    // ------------------------------------------------------------------ wings: stepped bars that tear sideways

    private static final float[] BAR_LEN = {23f, 21f, 18f, 15f, 12f, 8f};

    /**
     * One wing in wing space (x outwards from the shoulder, y down, z backwards, pixels). The caller bends it for the flap.
     * Every feather is a bar in two pieces; on a glitch the outer piece jumps sideways and a colour-split copy shows.
     */
    public static void wing(Cosmetics.Entry e, float t, Pets.Sink s) {
        final int a = e.c1(), b = e.c2();
        final int slot = slot(t);
        for (int k = 0; k <= 6; k++) {                                            // bone: stepped diagonal
            float f = k / 6f;
            s.box(1f + 14f * f, -1f - 13f * f, 1.5f, 0.9f, 0.9f, 0.4f, 0x14142A, false);
        }
        for (int i = 0; i < BAR_LEN.length; i++) {
            float len = BAR_LEN[i];
            float y0 = -13.5f + i * 3.1f;
            float x0 = 3f + (i < 3 ? i * 1.5f : i * 0.8f);
            boolean jump = rnd(slot * 13 + i) > 0.65f;
            float shift = jump ? (rnd(slot * 7 + i) - 0.5f) * 6f : 0f;
            int c = (i & 1) == 0 ? a : b;
            int other = (i & 1) == 0 ? b : a;
            float cut = len * 0.55f;
            s.box(x0 + cut / 2f, y0, 0.6f + i * 0.02f, cut / 2f, 0.75f, 0.3f, c, false);                       // inner piece
            s.box(x0 + cut + (len - cut) / 2f + shift, y0, 0.6f, (len - cut) / 2f - 0.4f, 0.75f, 0.3f,
                    Pets.mix(c, WHITE, 0.2f), true);                                                           // outer piece
            s.box(x0 + len / 2f + shift * 0.5f, y0 + 0.9f, 0.3f, len / 2f, 0.2f, 0.2f, other, true);        // colour-split line
            if (rnd(slot * 3 + i * 5) > 0.5f) s.box(x0 + len + shift + 0.8f, y0, 0.6f, 0.5f, 0.5f, 0.3f, WHITE, true); // tip pixel
        }
    }

    // ------------------------------------------------------------------ pet: a cube made of eight jumping cubes

    /** Pet space: origin at the base, up is negative Y, the front is -Z. */
    public static void pet(Cosmetics.Entry e, float t, Pets.Sink s) {
        final int a = e.c1(), b = e.c2();
        final int slot = slot(t);
        float bob = sin(t * 0.12f) * 0.4f;
        for (int ix = 0; ix < 2; ix++) {
            for (int iy = 0; iy < 2; iy++) {
                for (int iz = 0; iz < 2; iz++) {
                    int id = ix + iy * 2 + iz * 4;
                    boolean jump = rnd(slot * 17 + id) > 0.8f;
                    float j = jump ? (rnd(slot * 5 + id) - 0.5f) * 1.6f : 0f;
                    float x = (ix * 2 - 1) * 1.2f + j;
                    float y = -3.6f + (iy * 2 - 1) * 1.2f + bob + (jump ? j * 0.5f : 0f);
                    float z = (iz * 2 - 1) * 1.2f;
                    s.box(x, y, z, 1.1f, 1.1f, 1.1f, ((ix + iy + iz) & 1) == 0 ? a : b, false);
                }
            }
        }
        boolean blink = rnd(slot * 29) > 0.85f;                                    // eyes flicker off now and then
        if (!blink) {
            s.box(-0.8f, -4.3f + bob, -2.4f, 0.4f, 0.25f, 0.08f, WHITE, true);
            s.box(0.8f, -4.3f + bob, -2.4f, 0.4f, 0.25f, 0.08f, WHITE, true);
        }
        s.box(0, -3.0f + bob, -2.4f, 0.9f, 0.1f, 0.08f, WHITE, true);              // mouth
        float sw = frac(t * 0.03f);                                                // a scan line sweeps down the cube
        s.box(0, -6f + sw * 5.2f + bob, 0, 2.5f, 0.12f, 2.5f, WHITE, true);
        for (int k = 0; k < 4; k++) {                                              // four pixels orbit it
            float ang = t * 0.08f + k * 1.5708f;
            s.box(cos(ang) * 3.6f, -3.6f + sin(t * 0.1f + k) * 1.6f + bob, sin(ang) * 3.6f, 0.3f, 0.3f, 0.3f, (k & 1) == 0 ? a : b, true);
        }
    }

    // ------------------------------------------------------------------ aura: digital rain and tearing bars

    /** Player model space in pixels, +Y down, head top y = -8, feet y = 24, back = +Z. Under 60 boxes. */
    public static void aura(Cosmetics.Entry e, float t, Pets.Sink s) {
        final int a = e.c1(), b = e.c2();
        final int slot = slot(t);
        for (int i = 0; i < 24; i++) {                                             // pixel rain falls around the body
            float life = frac(t * 0.02f + i / 24f + rnd(i) * 0.1f);
            float ang = rnd(i + 40) * TAU;
            float rad = 5.6f + rnd(i + 80) * 1.4f;
            float sz = 0.3f + 0.2f * rnd(i + 3);
            float fade = life > 0.8f ? (1f - life) / 0.2f : 1f;
            s.box(cos(ang) * rad, -8f + life * 32f, sin(ang) * rad, sz * fade + 0.05f, sz * fade + 0.4f, sz * fade + 0.05f,
                    (i & 1) == 0 ? a : b, true);
        }
        for (int k = 0; k < 3; k++) {                                              // up to three glitch bars around the body
            if (rnd(slot * 19 + k * 7) < 0.55f) continue;
            float y = -6f + rnd(slot * 3 + k * 13) * 28f;
            float w = 3.2f + rnd(slot + k) * 0.8f;
            float d = (rnd(slot * 5 + k) - 0.5f) * 1.2f;
            for (int side = -1; side <= 1; side += 2) {
                s.box(d + 0.4f, y, side * 6f, w, 0.18f, 0.15f, a, true);           // front and back, cyan
                s.box(d - 0.4f, y + 0.35f, side * 6f, w, 0.18f, 0.15f, b, true);   // magenta copy
                s.box(side * 6f, y, d + 0.4f, 0.15f, 0.18f, w, a, true);           // left and right
                s.box(side * 6f, y + 0.35f, d - 0.4f, 0.15f, 0.18f, w, b, true);
            }
        }
    }
}
