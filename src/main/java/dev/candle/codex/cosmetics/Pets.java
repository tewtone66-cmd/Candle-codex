package dev.candle.codex.cosmetics;

import java.util.List;

/**
 * Pets: small animals built from plain boxes (no model files). The builder is pure geometry: it sends boxes to a Sink,
 * so the 3D model (PetRender) and the menu picture (PetArt) always show the same pet.
 * Local space (pixels): origin at the base of the pet, up is negative Y, the front is -Z, x is sideways.
 * Shoulder pets sit on the shoulder; the Blue Butterfly is built around the HEAD CENTRE and circles it.
 */
public final class Pets {
    /** c1 = main colour, c2 = accent colour. */
    public static final List<Cosmetics.Entry> LIST = List.of(
            new Cosmetics.Entry("none", "No pet", 0x555555, 0x333333),
            new Cosmetics.Entry("dragon", "Baby Dragon", 0x8A3FD6, 0xFFC94A),
            new Cosmetics.Entry("slime", "Slime", 0x6FD654, 0x2F8F2E),
            new Cosmetics.Entry("allay", "Allay", 0x4FC3FF, 0xFFFFFF),
            new Cosmetics.Entry("vex", "Mini Vex", 0xA9C4E8, 0x5A78B0),
            new Cosmetics.Entry("parrot", "Parrot", 0xE03A3A, 0x3A8FE0),
            new Cosmetics.Entry("cat", "Kitty", 0xE8A24A, 0xFFFFFF),
            new Cosmetics.Entry("butterfly", "Blue Butterfly", 0x2F8CFF, 0x0B2F7A),
            Glitchy.PET);

    /** Receives the boxes of a pet (centre and half sizes in pixels). */
    public interface Sink {
        void box(float cx, float cy, float cz, float hx, float hy, float hz, int rgb, boolean glow);
    }

    private Pets() {}

    public static boolean animated(String id) {
        return !"none".equals(id);
    }

    /** True for pets that circle the head instead of sitting on the shoulder. */
    public static boolean orbits(String id) {
        return "butterfly".equals(id);
    }

    /** Builds the pet at time t (game ticks) into the sink. */
    public static void build(Cosmetics.Entry e, float t, Sink sink) {
        Pen p = new Pen(sink);
        switch (e.id()) {
            case "dragon" -> dragon(p, e, t);
            case "slime" -> slime(p, e, t);
            case "allay" -> allay(p, e, t);
            case "vex" -> vex(p, e, t);
            case "parrot" -> parrot(p, e, t);
            case "cat" -> cat(p, e, t);
            case "butterfly" -> butterfly(p, e, t);
            case "glitchcube" -> Glitchy.pet(e, t, p.sink);
            default -> { }
        }
    }

    /** Box helper with an optional yaw (turn around the Y axis) and offset, used by the butterfly. */
    private static final class Pen {
        final Sink sink;
        float yc = 1f, ys = 0f, ox, oy, oz;

        Pen(Sink sink) {
            this.sink = sink;
        }

        void b(float cx, float cy, float cz, float hx, float hy, float hz, int rgb) {
            put(cx, cy, cz, hx, hy, hz, rgb, false);
        }

        void g(float cx, float cy, float cz, float hx, float hy, float hz, int rgb) {
            put(cx, cy, cz, hx, hy, hz, rgb, true);
        }

        /** Smooth rounded blob: horizontal slices whose size follows an ellipse; lighter on top, darker below. */
        void ell(float cx, float cy, float cz, float rx, float ry, float rz, int col, boolean glow) {
            int n = Math.max(3, Math.round(ry * 2.4f));
            for (int i = 0; i < n; i++) {
                float f = (i + 0.5f) / n * 2f - 1f;                 // -1 (top) .. 1 (bottom)
                float k = (float) Math.sqrt(Math.max(0f, 1f - f * f));
                int c = shade(col, 1.0f - 0.2f * (f + 1f) / 2f);
                put(cx, cy + f * ry, cz, Math.max(0.2f, rx * k), ry / n + 0.06f, Math.max(0.2f, rz * k), c, glow);
            }
        }

        private float xa, xb;

        private void cross(float ax, float ay, float bx, float by, float y) {
            if ((ay <= y && by > y) || (by <= y && ay > y)) {
                float x = ax + (y - ay) / (by - ay) * (bx - ax);
                xa = Math.min(xa, x);
                xb = Math.max(xb, x);
            }
        }

        /** Flat triangle in the x / y plane at depth z, filled with thin rows (no gaps); colour goes from c0 (top) to c1 (bottom). */
        void tri(float x0, float y0, float x1, float y1, float x2, float y2, float z, float hz, int c0, int c1, boolean glow) {
            float ymin = Math.min(y0, Math.min(y1, y2)), ymax = Math.max(y0, Math.max(y1, y2));
            for (float y = ymin + 0.25f; y < ymax; y += 0.5f) {
                xa = Float.MAX_VALUE;
                xb = -Float.MAX_VALUE;
                cross(x0, y0, x1, y1, y);
                cross(x1, y1, x2, y2, y);
                cross(x2, y2, x0, y0, y);
                if (xb - xa < 0.05f) continue;
                put((xa + xb) / 2f, y, z, (xb - xa) / 2f + 0.12f, 0.32f, hz, mix(c0, c1, (y - ymin) / (ymax - ymin + 0.01f)), glow);
            }
        }

        private void put(float cx, float cy, float cz, float hx, float hy, float hz, int rgb, boolean glow) {
            float x = cx * yc + cz * ys, z = -cx * ys + cz * yc;
            sink.box(ox + x, oy + cy, oz + z, hx, hy, hz, rgb, glow);
        }
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int shade(int c, float f) {
        return (Math.min(255, Math.round(((c >> 16) & 255) * f)) << 16)
                | (Math.min(255, Math.round(((c >> 8) & 255) * f)) << 8)
                | Math.min(255, Math.round((c & 255) * f));
    }

    // ------------------------------------------------------------------ baby dragon (rounded body, wagging tail, flapping membrane wings)

    private static void dragon(Pen p, Cosmetics.Entry e, float t) {
        int c = e.c1(), a = e.c2(), d = shade(c, 0.68f), l = mix(c, 0xFFFFFF, 0.28f), belly = mix(a, c, 0.4f);
        float bob = (float) Math.sin(t * 0.12f) * 0.2f;
        float hb = (float) Math.sin(t * 0.17f + 1f) * 0.3f;
        p.ell(0, -2.6f + bob, 0.4f, 1.7f, 1.7f, 2.4f, c, false);                // body
        p.ell(0, -1.9f + bob, -0.1f, 1.2f, 1.1f, 1.9f, belly, false);           // belly
        for (int sx = -1; sx <= 1; sx += 2) {
            p.ell(sx * 1.3f, -0.8f, -1.2f, 0.6f, 0.8f, 0.6f, d, false);        // legs
            p.ell(sx * 1.3f, -0.8f, 2.0f, 0.7f, 0.8f, 0.7f, d, false);
            p.b(sx * 1.3f, -0.1f, -1.7f, 0.5f, 0.12f, 0.35f, a);               // claws
            p.b(sx * 1.3f, -0.1f, 2.5f, 0.55f, 0.12f, 0.35f, a);
        }
        p.ell(0, -4.0f + bob, -1.5f, 0.9f, 1.2f, 0.9f, c, false);               // neck
        p.ell(0, -5.2f + hb, -2.6f, 1.5f, 1.3f, 1.4f, c, false);                // head
        p.ell(0, -4.7f + hb, -4.0f, 0.9f, 0.65f, 0.9f, l, false);               // snout
        for (int sx = -1; sx <= 1; sx += 2) {
            p.b(sx * 0.35f, -4.95f + hb, -4.85f, 0.12f, 0.12f, 0.06f, d);      // nostrils
            p.g(sx * 0.95f, -5.7f + hb, -3.65f, 0.34f, 0.38f, 0.1f, a);        // glowing eyes
            p.b(sx * 0.95f, -6.9f + hb, -2.2f, 0.28f, 0.6f, 0.28f, a);         // horns
            p.b(sx * 1.05f, -7.7f + hb, -1.9f, 0.2f, 0.45f, 0.2f, mix(a, 0xFFFFFF, 0.3f));
        }
        for (int i = 0; i < 4; i++) p.b(0, -4.5f + bob + i * 0.25f, -0.4f + i * 0.9f, 0.25f, 0.45f, 0.32f, a); // back spikes
        for (int i = 0; i < 6; i++) {                                           // tail wags and tapers
            float sway = (float) Math.sin(t * 0.22f - i * 0.6f) * (0.3f + 0.35f * i);
            float sz = 1.1f - 0.14f * i;
            p.ell(sway, -1.6f - i * 0.15f, 2.8f + i * 1.25f, sz, sz, 0.9f, i == 5 ? a : c, false);
        }
        float flap = (float) Math.sin(t * 0.5f);
        float ang = 0.5f + 0.55f * flap;
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        for (int sx = -1; sx <= 1; sx += 2) {                                   // wings: bone + two membrane panels
            float x0 = sx * 1.3f, y0 = -3.8f + bob;
            float tx = sx * (1.3f + 4.6f * cs), ty = y0 - 4.6f * sn;
            float mx = sx * (1.3f + 2.6f * (float) Math.cos(ang * 0.4f)), my = y0 + 2.4f;
            p.tri(x0, y0, tx, ty, mx, my, 0.8f, 0.14f, mix(c, 0xFFFFFF, 0.12f), mix(a, c, 0.3f), false);
            p.tri(x0, y0, mx, my, sx * 1.6f, y0 + 2.2f, 0.78f, 0.14f, mix(c, 0xFFFFFF, 0.12f), d, false);
            for (int k = 0; k <= 5; k++) {
                float f = k / 5f;
                p.b(x0 + (tx - x0) * f, y0 + (ty - y0) * f, 0.55f, 0.28f, 0.28f, 0.3f, d);
            }
            p.b(tx, ty - 0.3f, 0.55f, 0.2f, 0.35f, 0.25f, a);                  // claw at the wing tip
        }
    }

    // ------------------------------------------------------------------ slime (round jelly, squashes and bounces)

    private static void slime(Pen p, Cosmetics.Entry e, float t) {
        int c = e.c1(), dk = e.c2(), hi = mix(c, 0xFFFFFF, 0.55f);
        float s = (float) Math.sin(t * 0.22f);
        float h = 2.5f + 0.6f * s, w = 2.7f - 0.45f * s;
        float lift = Math.abs((float) Math.sin(t * 0.11f)) * 0.9f;
        float cy = -h - lift;
        p.ell(0, -0.3f, 0, w * 1.12f, 0.35f, w * 1.12f, shade(c, 0.8f), false);  // puddle at the bottom
        p.ell(0, cy, 0, w, h, w, c, false);                                      // jelly body
        p.ell(w * 0.3f, cy - h * 1.15f - 0.2f, 0.2f, 0.75f, 0.6f, 0.75f, mix(c, 0xFFFFFF, 0.2f), false); // blob on top
        p.b(-w * 0.45f, cy - h * 0.55f, -w * 0.9f - 0.1f, 0.55f, 0.35f, 0.08f, hi);      // shine
        p.b(-w * 0.65f, cy - h * 0.2f, -w * 0.8f - 0.1f, 0.2f, 0.2f, 0.08f, hi);
        for (int sx = -1; sx <= 1; sx += 2) {
            p.b(sx * w * 0.38f, cy - h * 0.05f, -w - 0.12f, 0.42f, 0.6f, 0.1f, dk);      // eyes
            p.b(sx * w * 0.38f - 0.12f, cy - h * 0.2f, -w - 0.2f, 0.14f, 0.18f, 0.05f, 0xFFFFFF);
        }
        p.b(0, cy + h * 0.45f, -w - 0.1f, 0.35f, 0.16f, 0.1f, dk);                       // mouth
    }

    // ------------------------------------------------------------------ allay (hovers, glows, glowing membrane wings)

    private static void allay(Pen p, Cosmetics.Entry e, float t) {
        int c = e.c1(), a = e.c2(), l = mix(c, 0xFFFFFF, 0.35f), d = shade(c, 0.75f);
        float by = -3.8f + (float) Math.sin(t * 0.15f) * 0.9f;
        p.ell(0, by, 0, 1.1f, 1.7f, 0.9f, c, false);                            // body
        p.g(0, by + 0.3f, -0.9f, 0.65f, 0.95f, 0.08f, l);                       // glowing belly
        p.ell(0, by - 3.0f, -0.2f, 1.5f, 1.35f, 1.35f, l, false);               // head
        for (int sx = -1; sx <= 1; sx += 2) {
            p.b(sx * 0.65f, by - 3.0f, -1.5f, 0.25f, 0.32f, 0.08f, 0x1A2A55);  // eyes
            p.b(sx * 1.85f, by - 3.1f, -0.2f, 0.3f, 0.7f, 0.5f, d);             // ears
            p.ell(sx * 1.6f, by + 0.2f, -0.5f, 0.4f, 1.1f, 0.4f, c, false);     // arms
        }
        float flap = (float) Math.sin(t * 0.9f);
        float ang = 0.35f + 0.5f * flap;
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        for (int sx = -1; sx <= 1; sx += 2) {                                   // two glowing wing panels per side
            float x0 = sx * 1.1f, y0 = by - 0.5f;
            p.tri(x0, y0, sx * (1.1f + 3.8f * cs), y0 - 3.8f * sn - 0.6f, sx * (1.1f + 2.8f * cs), y0 + 1.2f, 0.95f, 0.1f, l, a, true);
            p.tri(x0, y0, sx * (1.1f + 2.8f * cs), y0 + 1.2f, sx * 1.3f, y0 + 2.6f, 0.93f, 0.1f, a, c, true);
        }
        float ph = t * 0.2f;                                                    // a tiny spark circles it
        p.g((float) Math.sin(ph) * 2.8f, by - 0.5f + (float) Math.sin(ph * 1.7f), (float) Math.cos(ph) * 2.8f, 0.22f, 0.22f, 0.22f, a);
    }

    // ------------------------------------------------------------------ mini vex (hovers, red eyes, sword, fast wings)

    private static void vex(Pen p, Cosmetics.Entry e, float t) {
        int c = e.c1(), w = e.c2(), d = shade(c, 0.78f);
        float by = -4.0f + (float) Math.sin(t * 0.2f) * 1.0f;
        p.ell(0, by, 0, 1.0f, 1.5f, 0.75f, c, false);                           // chest
        for (int i = 0; i < 4; i++) {                                           // body fades into a tapering ghost tail
            float s = 1.0f - 0.2f * i;
            p.b(0, by + 1.5f + i * 0.9f, 0.12f * i, s * 0.85f, 0.5f, s * 0.65f, mix(c, w, i * 0.18f));
        }
        p.ell(0, by - 2.7f, -0.2f, 1.4f, 1.3f, 1.25f, c, false);                // head
        for (int sx = -1; sx <= 1; sx += 2) {
            p.g(sx * 0.65f, by - 2.8f, -1.45f, 0.3f, 0.3f, 0.1f, 0xFF3030);   // eyes
            p.b(sx * 1.0f, by - 4.2f, -0.2f, 0.18f, 0.5f, 0.18f, d);           // small horns
        }
        p.b(0, by - 1.9f, -1.45f, 0.5f, 0.12f, 0.08f, 0x1B1F2B);               // mouth
        p.b(-2.0f, by - 0.3f, -1.9f, 0.18f, 0.18f, 1.9f, 0xB8C4D0);            // sword blade
        p.b(-2.0f, by - 0.3f, -0.1f, 0.6f, 0.15f, 0.2f, 0x6B4A2A);             // guard
        p.ell(-1.4f, by, -0.6f, 0.4f, 0.4f, 0.4f, c, false);                   // hand
        float flap = (float) Math.sin(t * 1.3f);
        float ang = 0.4f + 0.6f * flap;
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        for (int sx = -1; sx <= 1; sx += 2) {                                   // pale wing panels
            float x0 = sx * 0.9f, y0 = by - 0.6f;
            p.tri(x0, y0, sx * (0.9f + 4.4f * cs), y0 - 4.4f * sn, sx * (0.9f + 3.0f * cs), y0 + 1.0f, 0.7f, 0.12f, w, shade(w, 0.75f), false);
            p.tri(x0, y0, sx * (0.9f + 3.0f * cs), y0 + 1.0f, sx * 1.1f, y0 + 2.2f, 0.68f, 0.12f, shade(w, 0.9f), shade(w, 0.7f), false);
        }
    }

    // ------------------------------------------------------------------ parrot (rounded body, head bob, layered wings, long tail)

    private static void parrot(Pen p, Cosmetics.Entry e, float t) {
        int c = e.c1(), b = e.c2(), dk = shade(c, 0.75f), lt = mix(c, 0xFFFFFF, 0.3f);
        float hb = (float) Math.sin(t * 0.3f) * 0.25f;
        float flutter = (float) Math.max(0.0, Math.sin(t * 0.08f) - 0.8f) * 5f;
        p.ell(0, -2.6f, 0.3f, 1.2f, 1.9f, 1.3f, c, false);                      // body
        p.ell(0, -2.3f, -0.45f, 0.8f, 1.4f, 0.6f, lt, false);                   // lighter chest
        p.ell(0, -5.3f + hb, -0.5f, 1.15f, 1.15f, 1.15f, c, false);             // head
        p.b(0, -5.2f + hb, -1.8f, 0.45f, 0.5f, 0.45f, 0xFFC94A);                // beak
        p.b(0, -4.7f + hb, -2.05f, 0.3f, 0.3f, 0.25f, 0xE0A030);               // hooked tip
        p.b(0, -6.6f + hb, -0.4f, 0.3f, 0.6f, 0.5f, b);                         // crest
        p.b(0, -7.2f + hb, -0.2f, 0.25f, 0.4f, 0.4f, mix(b, 0xFFFFFF, 0.2f));
        for (int sx = -1; sx <= 1; sx += 2) {
            p.b(sx * 0.75f, -5.5f + hb, -1.5f, 0.32f, 0.32f, 0.06f, 0xFFFFFF); // eyes
            p.b(sx * 0.75f, -5.5f + hb, -1.55f, 0.17f, 0.17f, 0.08f, 0x111111);
            for (int i = 0; i < 3; i++) {                                       // layered wing feathers
                p.b(sx * (1.45f + flutter * 0.1f), -3.7f + i * 1.05f - flutter * 0.3f, 0.4f + i * 0.35f, 0.3f, 0.62f, 0.9f, i == 2 ? b : mix(c, b, i * 0.3f));
            }
            p.b(sx * 0.5f, -0.25f, 0f, 0.25f, 0.25f, 0.45f, 0x777777);          // feet
        }
        for (int i = 0; i < 4; i++) {                                           // long tail feathers
            float s = (i - 1.5f) * 0.45f;
            p.b(s, -1.2f + i * 0.05f, 2.6f + (i % 2) * 0.3f, 0.28f, 0.22f, 2.0f, i % 2 == 0 ? b : mix(b, c, 0.5f));
        }
    }

    // ------------------------------------------------------------------ kitty (sits, breathes, tail swishes)

    private static void cat(Pen p, Cosmetics.Entry e, float t) {
        int c = e.c1(), w = e.c2(), dk = shade(c, 0.78f);
        float hb = (float) Math.sin(t * 0.1f) * 0.15f;
        float br = (float) Math.sin(t * 0.12f) * 0.12f;
        p.ell(0, -2.0f, 0.5f, 1.5f, 1.9f + br, 1.7f, c, false);                 // body
        p.ell(0, -1.7f, -0.9f, 0.8f, 1.2f, 0.6f, w, false);                     // white chest
        p.ell(0, -5.0f + hb, -0.7f, 1.9f, 1.6f, 1.6f, c, false);                // head
        p.ell(0, -4.3f + hb, -2.1f, 0.8f, 0.5f, 0.4f, w, false);                // muzzle
        p.b(0, -4.5f + hb, -2.45f, 0.25f, 0.18f, 0.1f, 0xFF8AA0);               // nose
        for (int i = -1; i <= 1; i++) p.b(i * 0.6f, -6.3f + hb, -2.0f, 0.15f, 0.5f, 0.05f, dk); // forehead stripes
        for (int sx = -1; sx <= 1; sx += 2) {
            p.ell(sx * 1.25f, -0.9f, 1.0f, 0.7f, 0.9f, 1.1f, dk, false);        // haunches
            p.ell(sx * 0.65f, -0.6f, -1.2f, 0.4f, 0.7f, 0.4f, c, false);        // front legs
            p.b(sx * 0.65f, -0.1f, -1.5f, 0.45f, 0.15f, 0.55f, w);              // paws
            p.b(sx * 0.95f, -5.2f + hb, -2.25f, 0.38f, 0.42f, 0.08f, 0x5CE05C); // eyes
            p.b(sx * 0.95f, -5.2f + hb, -2.3f, 0.14f, 0.38f, 0.05f, 0x10201A);
            p.b(sx * 1.3f, -6.9f + hb, -0.7f, 0.55f, 0.5f, 0.4f, c);            // ears
            p.b(sx * 1.2f, -7.7f + hb, -0.7f, 0.3f, 0.35f, 0.3f, c);
            p.b(sx * 1.3f, -6.9f + hb, -1.05f, 0.3f, 0.35f, 0.05f, 0xFFAAB5);
            p.b(sx * 2.0f, -4.2f + hb, -2.0f, 0.9f, 0.05f, 0.05f, 0xF4F4F4);    // whiskers
        }
        for (int i = 0; i < 6; i++) {                                           // tail curls up and swishes
            float sway = (float) Math.sin(t * 0.12f + i * 0.55f) * (0.3f + i * 0.35f);
            p.b(sway, -1.0f - i * 0.9f, 2.2f + i * 0.3f, 0.5f - i * 0.03f, 0.55f, 0.5f - i * 0.03f, i == 5 ? w : dk);
        }
    }

    // ------------------------------------------------------------------ blue butterfly: circles the head (origin = head centre)

    private static final float[] UP_V = {-2.4f, -1.4f, -0.4f};
    private static final int[] UP_N = {3, 3, 3, 2};
    private static final int[] LO_N = {2, 2, 1};

    private static void butterfly(Pen p, Cosmetics.Entry e, float t) {
        int c = e.c1(), edge = e.c2(), core = mix(c, 0xFFFFFF, 0.45f);
        float th = t * 0.07f;                                                   // one lap in about 4.5 seconds
        float r = 10f;
        p.ox = (float) Math.cos(th) * r;
        p.oz = (float) Math.sin(th) * r;
        p.oy = -4f - 2f + (float) Math.sin(t * 0.11f) * 2.2f;                  // around eye / hat height
        p.ys = (float) Math.sin(th);                                            // turn the butterfly along its flight path
        p.yc = -(float) Math.cos(th);
        float f = 0.15f + 0.75f * (0.5f + 0.5f * (float) Math.sin(t * 0.7f)); // wing angle (0 = open flat)
        float cf = (float) Math.cos(f), sf = (float) Math.sin(f);
        float q = 0.45f;
        p.b(0, 0, -1.0f, 0.35f, 0.35f, 0.4f, edge);                            // body
        p.b(0, 0, 0f, 0.35f, 0.35f, 0.5f, edge);
        p.b(0, 0, 1.1f, 0.3f, 0.3f, 0.5f, edge);
        for (int sx = -1; sx <= 1; sx += 2) {
            p.b(sx * 0.45f, -0.55f, -1.9f, 0.1f, 0.1f, 0.5f, edge);            // antennae
            for (int i = 0; i < UP_N.length; i++) {                             // upper (front) wing
                float u = 0.9f + i * 1.1f;
                for (int j = 0; j < UP_N[i]; j++) {
                    float v = i == UP_N.length - 1 ? UP_V[j + 1] : UP_V[j];
                    boolean rim = i == UP_N.length - 1 || j == 0;
                    boolean mid = i == 1 && j == 1;
                    int col = mid ? core : rim ? edge : c;
                    float wx = sx * u * cf, wy = -u * sf;
                    if (mid) p.g(wx, wy, v, q, q, q, col);
                    else p.b(wx, wy, v, q, q, q, col);
                }
            }
            for (int i = 0; i < LO_N.length; i++) {                             // lower (back) wing
                float u = 0.9f + i * 1.0f;
                for (int j = 0; j < LO_N[i]; j++) {
                    float v = 0.9f + j * 1.0f;
                    boolean rim = i == LO_N.length - 1 || j == LO_N[i] - 1;
                    p.b(sx * u * cf, -u * sf, v, q, q, q, rim ? edge : c);
                }
            }
        }
    }
}
