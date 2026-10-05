package dev.candle.codex.cosmetics;

/**
 * All hat models, rebuilt from scratch (part 2, step 1). Pure geometry: every builder sends boxes to a Pets.Sink, so the
 * model on your player (CosmeticLayer) and the 3D picture in the menu (Model3D) are always exactly the same thing.
 *
 * Head model space in pixels (1/16 block): x -4..4, y -8..0, z -4..4, up is NEGATIVE y, the face looks towards -z.
 * With the skin's hat layer the top of the head is y = -8.5, so every hat sits on y = -8.5.
 *
 * Shapes: round parts (brims, cones, rings) are built from rows of boxes, square parts get a chamfered corner.
 * Colours: c1 = main colour, c2 = accent colour. Shading is built into the geometry (stepped gradients, darker tips,
 * lighter rims) because every box has one flat colour.
 */
public final class Hats {
    private static final int GOLD = 0xFFD166, PEARL = 0xFFF6E8, RUBY = 0xE0405A, SAPPHIRE = 0x3CAAFF;
    private static final int FUR = 0xF6F6FA, FUR_SHADE = 0xD2D5E6, RED_BAND = 0xB02A37, WHITE = 0xFFFFFF;

    private Hats() {}

    /** Sends the boxes of the hat e (animated hats use time in game ticks) to the sink. "none" and unknown ids add nothing. */
    public static void build(Cosmetics.Entry e, float time, Pets.Sink s) {
        final int a = e.c1(), b = e.c2();
        switch (e.id()) {
            case "crown" -> crown(s, a, b);
            case "tophat" -> topHat(s, a, b);
            case "halo" -> halo(s, a, b, time);
            case "party" -> party(s, a, b);
            case "cat" -> cat(s, a, b);
            case "santa" -> santa(s, a);
            case "wizard" -> wizard(s, a, b);
            case "cap" -> cap(s, a, b);
            default -> { }
        }
    }

    /**
     * A simple head for the menu pictures only (the game shows your real head): skin, hair on top, eyes and mouth.
     * The hat sits on top of it exactly as it does on the player.
     */
    public static void head(Pets.Sink s) {
        s.box(0, -3.75f, 0, 4.25f, 3.75f, 4.25f, 0xC8956A, false);                     // skin, y -7.5 .. 0
        s.box(0, -7.95f, 0, 4.3f, 0.55f, 4.3f, 0x4B3621, false);                        // hair, y -8.5 .. -7.4
        s.box(0, -6.9f, -4.3f, 4.3f, 0.5f, 0.06f, 0x4B3621, false);                     // fringe
        for (int sx = -1; sx <= 1; sx += 2) {
            s.box(sx * 2.1f, -3.7f, -4.3f, 1.0f, 0.5f, 0.06f, 0xF4F4F4, false);         // eye white
            s.box(sx * 1.6f, -3.7f, -4.34f, 0.5f, 0.5f, 0.06f, 0x3A3FA8, false);        // pupil
        }
        s.box(0, -1.4f, -4.3f, 1.2f, 0.35f, 0.06f, 0x8A5A3C, false);                    // mouth
    }

    // ------------------------------------------------------------------ crown

    private static void crown(Pets.Sink s, int a, int b) {
        final int al = mix(a, WHITE, 0.4f), bl = mix(b, WHITE, 0.3f);
        frame(s, -8.8f, 0.3f, 4.95f, 1.4f, 1.2f, b);                  // dark base trim
        frame(s, -9.85f, 0.75f, 4.85f, 1.3f, 1.2f, a);                // gold band
        frame(s, -10.72f, 0.12f, 4.9f, 1.3f, 1.2f, al);               // bright rim
        final float m = 4.2f, c = 3.6f;
        final float[][] pts = {
                {0, -m, 1}, {0, m, 1}, {-m, 0, 1}, {m, 0, 1},         // 4 tall points
                {-c, -c, 0}, {c, -c, 0}, {-c, c, 0}, {c, c, 0}};      // 4 short points on the corners
        for (float[] p : pts) {
            final boolean tall = p[2] > 0.5f;
            final int layers = tall ? 3 : 2;
            float y = -10.84f;
            for (int i = 0; i < layers; i++) {
                final float half = (tall ? 0.98f : 0.86f) - 0.24f * i;
                final float hy = 0.55f - 0.05f * i;
                y -= hy;
                s.box(p[0], y, p[1], half, hy, half, i == layers - 1 ? al : a, false);
                y -= hy;
            }
            s.box(p[0], y - 0.4f, p[1], 0.4f, 0.4f, 0.4f, tall ? PEARL : bl, false);
        }
        // gems on the band: rubies on the four sides, sapphires between them
        s.box(0, -9.85f, -4.95f, 0.8f, 0.5f, 0.14f, RUBY, false);
        s.box(0, -9.85f, 4.95f, 0.8f, 0.5f, 0.14f, RUBY, false);
        s.box(-4.95f, -9.85f, 0, 0.14f, 0.5f, 0.8f, RUBY, false);
        s.box(4.95f, -9.85f, 0, 0.14f, 0.5f, 0.8f, RUBY, false);
        for (int u = -1; u <= 1; u += 2) {
            for (int v = -1; v <= 1; v += 2) {
                s.box(u * 2.6f, -9.85f, v * 4.95f, 0.5f, 0.42f, 0.14f, SAPPHIRE, false);
                s.box(u * 4.95f, -9.85f, v * 2.6f, 0.14f, 0.42f, 0.5f, SAPPHIRE, false);
            }
        }
    }

    // ------------------------------------------------------------------ top hat

    private static void topHat(Pets.Sink s, int a, int b) {
        final int brim = shade(a, 0.8f), low = shade(a, 0.9f), up = mix(a, WHITE, 0.1f), top = mix(a, WHITE, 0.24f);
        disc(s, 0, -8.75f, 0, 6.7f, 0.25f, brim);                      // wide round brim
        ring(s, 0, -8.97f, 0, 6.7f, 6.2f, 0.05f, mix(a, WHITE, 0.16f), false); // sheen on the brim edge
        disc(s, 0, -10.6f, 0, 4.45f, 1.6f, low);                       // lower body
        disc(s, 0, -13.7f, 0, 4.45f, 1.5f, a);                         // middle body
        disc(s, 0, -15.9f, 0, 4.45f, 0.7f, up);                        // upper body
        disc(s, 0, -16.7f, 0, 4.5f, 0.12f, top);                       // top face
        disc(s, 0, -9.95f, 0, 4.62f, 0.7f, RED_BAND);                  // band
        s.box(0, -9.95f, -4.72f, 1.0f, 0.62f, 0.14f, GOLD, false);     // buckle
        s.box(0, -9.95f, -4.84f, 0.5f, 0.3f, 0.1f, shade(b, 0.55f), false);
    }

    // ------------------------------------------------------------------ halo (glows, bobs, sparkles)

    private static void halo(Pets.Sink s, int a, int b, float time) {
        final float y = -14.6f + (float) Math.sin(time * 0.12f) * 0.5f;
        ring(s, 0, y, 0, 5.5f, 4.5f, 0.32f, a, true);                  // core
        ring(s, 0, y, 0, 5.95f, 5.5f, 0.2f, b, true);                  // warm outer edge
        ring(s, 0, y - 0.42f, 0, 5.3f, 4.7f, 0.1f, WHITE, true);       // bright top
        for (int k = 0; k < 4; k++) {                                  // sparkles circling the ring
            final float ang = time * 0.06f + k * 1.5707964f;
            final float sy = y - 0.9f + (float) Math.sin(time * 0.2f + k * 1.7f) * 0.35f;
            s.box((float) Math.cos(ang) * 5.2f, sy, (float) Math.sin(ang) * 5.2f, 0.28f, 0.28f, 0.28f, WHITE, true);
        }
    }

    // ------------------------------------------------------------------ party hat

    private static void party(Pets.Sink s, int a, int b) {
        final float[] rad = new float[7], cy = new float[7];
        for (int i = 0; i < 7; i++) {
            rad[i] = 4.2f - 0.55f * i;
            cy[i] = -8.5f - (i + 0.5f) * 2f;
            disc(s, 0, cy[i], 0, rad[i], 1f, i % 2 == 0 ? a : b);   // stripes
        }
        for (int i = 1; i <= 5; i++) {                                // confetti dots on the cone
            final float ang = i * 2.1f + 0.5f, r = rad[i] + 0.1f;
            s.box((float) Math.cos(ang) * r, cy[i], (float) Math.sin(ang) * r, 0.32f, 0.32f, 0.32f, i % 2 == 0 ? WHITE : GOLD, false);
        }
        s.box(0, -23.6f, 0, 1.25f, 1.25f, 1.25f, mix(a, WHITE, 0.82f), false);   // pompom
        s.box(0, -24.95f, 0, 0.95f, 0.2f, 0.95f, WHITE, false);
    }

    // ------------------------------------------------------------------ cat ears

    private static void cat(Pets.Sink s, int a, int b) {
        final int ad = shade(a, 0.78f), bl = mix(b, WHITE, 0.3f);
        frame(s, -8.8f, 0.3f, 4.8f, 1.2f, 1.0f, ad);                  // head band
        final float[] hx = {2.0f, 1.7f, 1.3f, 0.85f};
        final float[] hz = {1.0f, 0.9f, 0.8f, 0.7f};
        final float zc = -0.6f;
        for (int sx = -1; sx <= 1; sx += 2) {
            float y = -9.1f;
            for (int i = 0; i < 4; i++) {
                final float cx = sx * (2.7f + 0.25f * i);
                final float cy = y - 0.7f;
                s.box(cx, cy, zc, hx[i], 0.7f, hz[i], i == 3 ? ad : mix(a, ad, i * 0.22f), false);
                if (i < 3) s.box(cx, cy, zc - hz[i] - 0.06f, hx[i] * 0.55f, 0.6f, 0.06f, i == 2 ? bl : b, false); // pink inside
                y -= 1.4f;
            }
        }
    }

    // ------------------------------------------------------------------ santa hat (bends over to the side, pompom hangs down)

    private static void santa(Pets.Sink s, int a) {
        final int dark = shade(a, 0.62f);
        frame(s, -9.1f, 0.6f, 5.0f, 1.7f, 1.2f, FUR);                 // fur trim
        frame(s, -8.62f, 0.12f, 5.05f, 1.75f, 1.2f, FUR_SHADE);       // soft shadow line at its foot
        final float[] x = {0f, 0.3f, 1.0f, 2.3f, 4.0f, 5.4f, 6.4f};
        final float[] y = {-10.3f, -11.7f, -12.9f, -13.6f, -13.4f, -12.4f, -11.0f};
        final float[] z = {0.1f, 0.3f, 0.6f, 1.0f, 1.4f, 1.6f, 1.7f};
        final float[] r = {4.35f, 4.0f, 3.55f, 3.1f, 2.6f, 2.15f, 1.75f};
        for (int i = 0; i < 7; i++) disc(s, x[i], y[i], z[i], r[i], 0.78f, mix(a, dark, i / 6f));
        s.box(7.1f, -9.1f, 1.8f, 1.5f, 1.5f, 1.5f, FUR, false);        // pompom
        s.box(7.1f, -10.45f, 1.8f, 1.1f, 0.15f, 1.1f, WHITE, false);
        s.box(7.1f, -7.8f, 1.8f, 1.1f, 0.15f, 1.1f, FUR_SHADE, false);
    }

    // ------------------------------------------------------------------ wizard hat

    private static void wizard(Pets.Sink s, int a, int b) {
        final int ad = shade(a, 0.7f), bl = mix(b, WHITE, 0.55f);
        disc(s, 0, -8.7f, 0, 7.4f, 0.2f, shade(a, 0.55f));            // brim (two steps, so it slopes)
        disc(s, 0, -9.05f, 0, 6.4f, 0.2f, ad);
        disc(s, 0, -9.65f, 0, 4.5f, 0.5f, b);                          // gold band
        s.box(0, -9.65f, -4.58f, 0.8f, 0.5f, 0.1f, bl, false);         // buckle
        final float[] zOff = new float[8], rad = new float[8], cy = new float[8];
        for (int i = 0; i < 8; i++) {                                  // cone that bends backwards, darker towards the tip
            rad[i] = 4.15f - 0.5f * i;
            cy[i] = -10.9f - 1.45f * i;
            zOff[i] = i * i * 0.09f;
            disc(s, 0, cy[i], zOff[i], rad[i], 0.75f, mix(a, ad, i / 8f));
        }
        star(s, -1.4f, 2, rad, cy, zOff, bl);
        star(s, 1.0f, 4, rad, cy, zOff, bl);
        star(s, -0.4f, 5, rad, cy, zOff, bl);
    }

    /** A small glowing plus sign on the front of cone layer i. */
    private static void star(Pets.Sink s, float x, int i, float[] rad, float[] cy, float[] zOff, int col) {
        final float z = zOff[i] - (float) Math.sqrt(Math.max(0.2f, rad[i] * rad[i] - x * x)) - 0.12f;
        s.box(x, cy[i], z, 0.55f, 0.16f, 0.1f, col, true);
        s.box(x, cy[i], z, 0.16f, 0.55f, 0.1f, col, true);
    }

    // ------------------------------------------------------------------ cap

    private static void cap(Pets.Sink s, int a, int b) {
        final int brim = shade(a, 0.9f);
        plate(s, -9.0f, 0.5f, 4.85f, 1.4f, a);                         // dome in four rounded steps
        plate(s, -9.95f, 0.45f, 4.6f, 1.4f, mix(a, WHITE, 0.04f));
        plate(s, -10.8f, 0.4f, 3.9f, 1.3f, mix(a, WHITE, 0.1f));
        plate(s, -11.5f, 0.3f, 2.8f, 1.1f, mix(a, WHITE, 0.17f));
        s.box(0, -12.05f, 0, 0.55f, 0.25f, 0.55f, b, false);           // button
        frame(s, -8.62f, 0.12f, 4.9f, 1.0f, 1.0f, b);                  // stitched band at the bottom
        s.box(0, -9.8f, -4.68f, 0.75f, 0.3f, 0.1f, b, false);          // little flame logo
        s.box(0, -10.25f, -4.68f, 0.45f, 0.2f, 0.1f, b, false);
        s.box(0, -9.7f, -4.74f, 0.3f, 0.15f, 0.08f, mix(b, WHITE, 0.5f), false);
        s.box(0, -8.9f, -6.3f, 3.0f, 0.28f, 1.9f, brim, false);        // brim, curved a little at the sides
        s.box(-3.8f, -8.9f, -5.5f, 0.9f, 0.28f, 1.2f, brim, false);
        s.box(3.8f, -8.9f, -5.5f, 0.9f, 0.28f, 1.2f, brim, false);
        s.box(0, -8.9f, -8.2f, 3.0f, 0.3f, 0.06f, b, false);           // trim on the brim edge
    }

    // ------------------------------------------------------------------ shape helpers

    /** Solid square plate with chamfered corners (half side h, corner cut k): three boxes. */
    private static void plate(Pets.Sink s, float cy, float hy, float h, float k, int c) {
        s.box(0, cy, 0, h, hy, h - k, c, false);
        s.box(0, cy, 0, h - k / 2f, hy, h - k / 2f, c, false);
        s.box(0, cy, 0, h - k, hy, h, c, false);
    }

    /** Hollow square frame with chamfered outer corners (half side h, wall thickness t, corner cut k <= t): four boxes. */
    private static void frame(Pets.Sink s, float cy, float hy, float h, float t, float k, int c) {
        final float mid = h - t / 2f;
        s.box(0, cy, -mid, h - k, hy, t / 2f, c, false);
        s.box(0, cy, mid, h - k, hy, t / 2f, c, false);
        s.box(-mid, cy, 0, t / 2f, hy, h - k, c, false);
        s.box(mid, cy, 0, t / 2f, hy, h - k, c, false);
    }

    /** Solid round disc of radius r made of rows of boxes. */
    private static void disc(Pets.Sink s, float cx, float cy, float cz, float r, float hy, int c) {
        ring(s, cx, cy, cz, r, 0f, hy, c, false);
    }

    /** Round ring (outer radius ro, inner radius ri, 0 for a full disc) made of rows of boxes along z. */
    private static void ring(Pets.Sink s, float cx, float cy, float cz, float ro, float ri, float hy, int c, boolean glow) {
        final float step = ro < 2.5f ? 0.5f : 1f;
        final int n = (int) Math.ceil(ro / step);
        for (int k = 0; k < n; k++) {
            final float zc = (k + 0.5f) * step;
            if (zc >= ro) break;
            final float wo = (float) Math.sqrt(ro * ro - zc * zc);
            final float wi = zc < ri ? (float) Math.sqrt(ri * ri - zc * zc) : 0f;
            for (int sg = -1; sg <= 1; sg += 2) {
                final float z = cz + sg * zc;
                if (wi > 0.05f) {
                    final float mid = (wo + wi) / 2f, half = (wo - wi) / 2f;
                    if (half < 0.05f) continue;
                    s.box(cx - mid, cy, z, half, hy, step / 2f, c, glow);
                    s.box(cx + mid, cy, z, half, hy, step / 2f, c, glow);
                } else {
                    s.box(cx, cy, z, wo, hy, step / 2f, c, glow);
                }
            }
        }
    }

    // ------------------------------------------------------------------ colours

    private static int mix(int a, int b, float t) {
        final int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        final int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        final int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shade(int c, float f) {
        return (Math.round(((c >> 16) & 255) * f) << 16) | (Math.round(((c >> 8) & 255) * f) << 8) | Math.round((c & 255) * f);
    }
}
