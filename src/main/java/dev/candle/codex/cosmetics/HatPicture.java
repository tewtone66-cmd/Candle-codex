package dev.candle.codex.cosmetics;

/**
 * Flat front picture of a hat on a head, made from the very same boxes as the 3D model (Hats). The menu uses it as the
 * fallback when the 3D pictures are switched off, so the flat picture can never drift away from the model in the game.
 * No game classes here, only arithmetic.
 */
public final class HatPicture {
    /** Picture size in art pixels. */
    public static final int W = 24, H = 36;
    /** The same slightly-from-above view as the 3D menu pictures (radians). */
    private static final float PITCH = 0.30f;

    private HatPicture() {}

    /**
     * ARGB art pixels (0 = empty), row-major W x H: the head with the hat on it, seen from the front and a little from
     * above, scaled to fill the picture (every hat gets the biggest size that fits, so short hats are not tiny).
     */
    public static int[] render(Cosmetics.Entry e) {
        final float[][] boxes = new float[600][];
        final int[] n = {0};
        final Pets.Sink sink = (cx, cy, cz, hx, hy, hz, rgb, glow) -> {
            if (n[0] < boxes.length) boxes[n[0]++] = new float[]{cx, cy, cz, hx, hy, hz, rgb, glow ? 1f : 0f};
        };
        Hats.head(sink);
        Hats.build(e, 6f, sink);

        final float cp = (float) Math.cos(PITCH), sp = (float) Math.sin(PITCH);
        // screen rectangle of every box: x as is, y = y * cos - z * sin (the face looks towards -z, so near boxes are lower)
        final float[] x0 = new float[n[0]], x1 = new float[n[0]], y0 = new float[n[0]], y1 = new float[n[0]], depth = new float[n[0]];
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n[0]; i++) {
            final float[] b = boxes[i];
            final float sy = b[1] * cp - b[2] * sp, ry = b[4] * cp + b[5] * sp;
            x0[i] = b[0] - b[3];
            x1[i] = b[0] + b[3];
            y0[i] = sy - ry;
            y1[i] = sy + ry;
            depth[i] = b[1] * sp + b[2] * cp;
            minX = Math.min(minX, x0[i]);
            maxX = Math.max(maxX, x1[i]);
            minY = Math.min(minY, y0[i]);
            maxY = Math.max(maxY, y1[i]);
        }
        final float k = Math.min(2.2f, Math.min((W - 2f) / Math.max(1f, maxX - minX), (H - 2f) / Math.max(1f, maxY - minY)));
        final float midX = (minX + maxX) / 2f, midY = (minY + maxY) / 2f;

        final int[] px = new int[W * H];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                final float x = (c + 0.5f - W / 2f) / k + midX, y = (r + 0.5f - H / 2f) / k + midY;
                float best = Float.MAX_VALUE;
                int color = 0;
                for (int i = 0; i < n[0]; i++) {
                    if (x < x0[i] || x > x1[i] || y < y0[i] || y > y1[i] || depth[i] >= best) continue;
                    best = depth[i];
                    final float[] b = boxes[i];
                    color = (int) b[6];
                    if (b[7] < 0.5f) color = shade(color, 0.9f);
                    color |= 0xFF000000;
                }
                px[r * W + c] = color;
            }
        }
        // a darker outline on the edge pixels keeps the silhouette crisp at this size
        final int[] out = px.clone();
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                if (px[r * W + c] == 0) continue;
                final boolean edge = c == 0 || r == 0 || c == W - 1 || r == H - 1
                        || px[r * W + c - 1] == 0 || px[r * W + c + 1] == 0 || px[(r - 1) * W + c] == 0 || px[(r + 1) * W + c] == 0;
                if (edge) out[r * W + c] = 0xFF000000 | shade(px[r * W + c], 0.68f);
            }
        }
        return out;
    }

    private static int shade(int c, float f) {
        return (Math.round(((c >> 16) & 255) * f) << 16) | (Math.round(((c >> 8) & 255) * f) << 8) | Math.round((c & 255) * f);
    }
}
