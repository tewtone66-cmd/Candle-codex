package dev.candle.codex.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Animated capes. Every frame is drawn with code into a 256 x 128 cape atlas (the outer face is 40 x 64 pixels),
 * encoded as PNG, read back with NativeImage.read and registered as a normal texture at
 * candle:textures/cape/anim_[cape]_[frame].png. So the menu preview and the player skin simply point at a
 * different texture id each frame. Frames are built lazily, one at a time, on the render thread.
 */
public final class AnimCapes {
    public static final int FRAMES = 12;
    private static final int S = 4, FW = 40, FH = 64, TW = 64 * S, TH = 32 * S;

    /** Milliseconds each frame stays on screen. */
    private static final Map<String, Integer> PERIOD = new HashMap<>(Map.of(
            "ad", 110, "aurora", 200, "inferno", 80, "matrix", 90, "iran", 150, "candleflame", 90,
            "hanok", 140, "cherry", 130, "glitchcape", 100));
    static {
        PERIOD.put("blossomgate", 120);
        PERIOD.put("moongate", 120);
    }
    /** Per cape: the background colour each frame was built with, -1 when not built yet. */
    private static final Map<String, int[]> BUILT = new HashMap<>();
    private static final Set<String> REGISTERED = new HashSet<>();

    private AnimCapes() {}

    public static boolean isAnimated(String id) {
        return PERIOD.containsKey(id);
    }

    /** Texture key (use with Cosmetics.capeFile / capeAsset) of the frame to show right now. Render thread only. */
    public static String frameKey(String id) {
        int f = (int) ((System.currentTimeMillis() / PERIOD.get(id)) % FRAMES);
        return ensure(id, f);
    }

    private static int wantColor(String id) {
        if (id.equals("ad")) return Config.data.cosmetics.adColor & 0xFFFFFF;
        if (id.equals("candleflame")) return Config.data.cosmetics.flameColor & 0xFFFFFF;
        return 0;
    }

    private static String ensure(String id, int f) {
        String key = "anim_" + id + "_" + f;
        int[] built = BUILT.computeIfAbsent(id, k -> {
            int[] a = new int[FRAMES];
            Arrays.fill(a, -1);
            return a;
        });
        int want = wantColor(id);
        if (built[f] != want) {
            built[f] = want; // set first so a failure is not retried every frame
            build(id, f, key, want);
        }
        return key;
    }

    /** Rebuilds every frame that already exists (used when the cape's background colour changes). */
    public static void rebuild(String id) {
        int[] built = BUILT.get(id);
        if (built == null) return;
        for (int f = 0; f < FRAMES; f++) {
            if (built[f] != -1) ensure(id, f);
        }
    }

    /** Registers a finished ARGB picture as a texture (the 3D menu pictures use it). Returns its id, or null on failure. */
    public static Identifier registerPicture(String key, int w, int h, int[] argb) {
        try {
            byte[] data = png(w, h, argb);
            NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
            Identifier tid = Cosmetics.capeFile(key);
            var tm = Minecraft.getInstance().getTextureManager();
            if (REGISTERED.contains(key)) tm.release(tid);
            final String label = "candle/" + key;
            tm.register(tid, new DynamicTexture(() -> label, img));
            REGISTERED.add(key);
            return tid;
        } catch (Exception e) {
            CodexClient.LOG.warn("Codex: could not register picture {}", key, e);
            return null;
        }
    }

    private static void build(String id, int f, String key, int color) {
        try {
            int[] face = switch (id) {
                case "ad" -> adFace(f, color);
                case "aurora" -> auroraFace(f);
                case "inferno" -> infernoFace(f);
                case "iran" -> CapeArt.lionSunFace(f);
                case "candleflame" -> CapeArt.flameFace(f, color);
                case "hanok" -> CapeArt2.hanokFace(f);
                case "cherry" -> CapeArt2.cherryFace(f);
                case "blossomgate" -> CapeArt3.face(f, false);
                case "moongate" -> CapeArt3.face(f, true);
                case "glitchcape" -> CapeArtGlitch.face(f);
                default -> matrixFace(f);
            };
            byte[] png = png(TW, TH, atlas(face));
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            Identifier tid = Cosmetics.capeFile(key);
            var tm = Minecraft.getInstance().getTextureManager();
            if (REGISTERED.contains(key)) tm.release(tid);
            final String label = "candle/" + key;
            tm.register(tid, new DynamicTexture(() -> label, img));
            REGISTERED.add(key);
        } catch (Exception e) {
            CodexClient.LOG.warn("Codex: could not build animated cape frame {}", key, e);
        }
    }

    // ------------------------------------------------------------------ atlas + PNG

    /** Cape layout (in 4 px cells): top (1,0), bottom (11,0), left (0,1), outer (1,1), right (11,1), inner (12,1). */
    private static int[] atlas(int[] face) {
        int[] a = new int[TW * TH];
        for (int y = 0; y < FH; y++) {
            for (int x = 0; x < FW; x++) {
                int c = face[y * FW + x];
                a[(S + y) * TW + (S + x)] = c;
                a[(S + y) * TW + (12 * S + x)] = shade(c, 0.8f);
            }
        }
        for (int x = 0; x < FW; x++) {
            int top = shade(face[x], 0.9f), bot = shade(face[(FH - 1) * FW + x], 0.9f);
            for (int y = 0; y < S; y++) {
                a[y * TW + (S + x)] = top;
                a[y * TW + (11 * S + x)] = bot;
            }
        }
        for (int y = 0; y < FH; y++) {
            int l = shade(face[y * FW], 0.9f), r = shade(face[y * FW + FW - 1], 0.9f);
            for (int x = 0; x < S; x++) {
                a[(S + y) * TW + x] = l;
                a[(S + y) * TW + (11 * S + x)] = r;
            }
        }
        return a;
    }

    private static byte[] png(int w, int h, int[] argb) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(h * (w * 4 + 1));
        for (int y = 0; y < h; y++) {
            raw.write(0);
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                raw.write((c >> 16) & 255);
                raw.write((c >> 8) & 255);
                raw.write(c & 255);
                raw.write((c >>> 24) & 255);
            }
        }
        Deflater d = new Deflater(1);
        d.setInput(raw.toByteArray());
        d.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(buf);
            z.write(buf, 0, n);
        }
        d.end();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, 0, 8);
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, w);
        putInt(ihdr, 4, h);
        ihdr[8] = 8;  // bit depth
        ihdr[9] = 6;  // RGBA
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void putInt(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 24);
        b[o + 1] = (byte) (v >>> 16);
        b[o + 2] = (byte) (v >>> 8);
        b[o + 3] = (byte) v;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] len = new byte[4];
        putInt(len, 0, data.length);
        out.write(len, 0, 4);
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(t, 0, 4);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(t, 0, 4);
        crc.update(data, 0, data.length);
        byte[] c = new byte[4];
        putInt(c, 0, (int) crc.getValue());
        out.write(c, 0, 4);
    }

    // ------------------------------------------------------------------ colour helpers

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int shade(int c, float f) {
        if ((c >>> 24) == 0) return c;
        return rgb(Math.round(((c >> 16) & 255) * f), Math.round(((c >> 8) & 255) * f), Math.round((c & 255) * f));
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return rgb(r, g, bl);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    static final float TAU = (float) (Math.PI * 2);

    // ------------------------------------------------------------------ Codex Ad

    private static final Map<Character, String[]> F5 = new HashMap<>();
    private static final Map<Character, String[]> F3 = new HashMap<>();

    static {
        F5.put('C', new String[]{".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."});
        F5.put('A', new String[]{".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"});
        F5.put('N', new String[]{"#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"});
        F5.put('D', new String[]{"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."});
        F5.put('L', new String[]{"#....", "#....", "#....", "#....", "#....", "#....", "#####"});
        F5.put('E', new String[]{"#####", "#....", "#....", "####.", "#....", "#....", "#####"});
        F5.put('I', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"});
        F5.put('T', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."});
        F3.put('P', new String[]{"###", "#.#", "###", "#..", "#.."});
        F3.put('L', new String[]{"#..", "#..", "#..", "#..", "###"});
        F3.put('A', new String[]{".#.", "#.#", "###", "#.#", "#.#"});
        F3.put('Y', new String[]{"#.#", "#.#", ".#.", ".#.", ".#."});
        F3.put('W', new String[]{"#.#", "#.#", "#.#", "###", "#.#"});
        F3.put('I', new String[]{"###", ".#.", ".#.", ".#.", "###"});
        F3.put('T', new String[]{"###", ".#.", ".#.", ".#.", ".#."});
        F3.put('H', new String[]{"#.#", "#.#", "###", "#.#", "#.#"});
        F3.put(' ', new String[]{"...", "...", "...", "...", "..."});
    }

    private static void text(int[] p, String s, int x0, int y0, int color, Map<Character, String[]> font, int gw, int gh) {
        int x = x0;
        for (char ch : s.toCharArray()) {
            String[] g = font.get(ch);
            if (g != null) {
                for (int gy = 0; gy < gh; gy++) {
                    for (int gx = 0; gx < gw; gx++) {
                        if (g[gy].charAt(gx) == '#') put(p, x + gx, y0 + gy, color);
                    }
                }
            }
            x += gw + 1;
        }
    }

    private static void put(int[] p, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < FW && y < FH) p[y * FW + x] = c;
    }

    private static int[] adFace(int f, int bg) {
        float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];
        int bgTop = shade(0xFF000000 | bg, 1.15f), bgBot = shade(0xFF000000 | bg, 0.82f);
        int lum = (((bg >> 16) & 255) * 30 + ((bg >> 8) & 255) * 59 + (bg & 255) * 11) / 100;
        boolean light = lum > 150;

        // background gradient + warm glow around the flame
        float cx = 20f, gcy = 11f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(TAU * t);
        for (int y = 0; y < FH; y++) {
            int row = mix(bgTop, bgBot, y / (float) (FH - 1));
            for (int x = 0; x < FW; x++) {
                float dx = x + 0.5f - cx, dy = (y + 0.5f - gcy) * 0.9f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int c = row;
                if (d < 17f) {
                    float k = 1f - d / 17f;
                    c = mix(row, 0xFFFF9A2E, 0.36f * k * k * pulse);
                }
                p[y * FW + x] = c;
            }
        }

        // wax body with a soft shaded edge and two drips
        for (int y = 19; y < 33; y++) {
            for (int x = 14; x < 26; x++) {
                int c = 0xFFF3E6C8;
                if (x <= 15) c = 0xFFD9C7A2;
                else if (x >= 24) c = 0xFFE6D5B0;
                if (y == 19) c = 0xFFFFF4DC;
                p[y * FW + x] = c;
            }
        }
        for (int y = 33; y < 36; y++) put(p, 15, y, 0xFFE6D5B0);
        for (int y = 33; y < 35; y++) put(p, 23, y, 0xFFE6D5B0);
        // wick
        for (int y = 16; y < 20; y++) {
            put(p, 19, y, 0xFF2B2B2B);
            put(p, 20, y, 0xFF2B2B2B);
        }

        // flame
        float tip = 2f + 1.2f * (float) Math.sin(TAU * 2 * t);
        float by = 10.5f, rx = 4.3f, ry = 4.8f, bottom = by + ry;
        for (int y = 0; y < 18; y++) {
            float hw;
            if (y + 0.5f < tip) continue;
            if (y + 0.5f <= by) hw = rx * (float) Math.pow((y + 0.5f - tip) / (by - tip), 1.4);
            else {
                float q = (y + 0.5f - by) / ry;
                hw = q >= 1f ? 0f : rx * (float) Math.sqrt(1 - q * q);
            }
            if (hw <= 0.05f) continue;
            float sway = (float) Math.sin(TAU * (t + y * 0.045f)) * 1.3f * Math.max(0f, 1f - (y - tip) / (bottom - tip));
            for (int x = 0; x < FW; x++) {
                float dx = Math.abs(x + 0.5f - (cx + sway));
                if (dx > hw) continue;
                float ratio = dx / hw;
                int c;
                if (ratio < 0.38f && y > tip + 4f) c = 0xFFFFF6C8;
                else if (ratio < 0.72f) c = 0xFFFFC933;
                else c = 0xFFFF8A00;
                p[y * FW + x] = c;
            }
        }
        // two rising sparks
        for (int i = 0; i < 2; i++) {
            float ph = (t * 2 + i * 0.5f) % 1f;
            int sx = Math.round(cx + (float) Math.sin(TAU * (t + i * 0.37f)) * 5f);
            int sy = Math.round(tip - 1f - ph * 6f);
            if (sy >= 0) put(p, sx, sy, mix(p[Math.max(0, sy) * FW + Math.max(0, Math.min(FW - 1, sx))], 0xFFFFD27A, 1f - ph));
        }

        // text
        int ink = light ? 0xFF1A1A1A : 0xFFFFFFFF;
        int accent = light ? 0xFFB34700 : 0xFFFFB347;
        int shadow = shade(0xFF000000 | bg, light ? 0.55f : 0.45f);
        text(p, "PLAY WITH", 2, 36, ink, F3, 3, 5);
        text(p, "CANDLE", 3, 45, shadow, F5, 5, 7);
        text(p, "CANDLE", 2, 44, accent, F5, 5, 7);
        text(p, "CLIENT", 3, 54, shadow, F5, 5, 7);
        text(p, "CLIENT", 2, 53, ink, F5, 5, 7);
        return p;
    }

    // ------------------------------------------------------------------ Aurora

    private static int[] auroraFace(int f) {
        float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];
        int[][] cols = {{60, 255, 150}, {50, 190, 255}, {170, 90, 255}};
        int[] ks = {1, 1, 2};
        for (int y = 0; y < FH; y++) {
            float k = y / (float) (FH - 1);
            for (int x = 0; x < FW; x++) {
                float r = 4 + 8 * k, g = 8 + 10 * k, b = 24 + 22 * k;
                for (int i = 0; i < 3; i++) {
                    float phase = i * 2.1f;
                    float cy = 16 + 15 * i + 6 * (float) Math.sin(x * (0.17f + 0.05f * i) + TAU * ks[i] * t + phase)
                            + 3 * (float) Math.sin(x * 0.06f + phase * 2);
                    float dy = y - cy, sg = 4 + 2 * i;
                    float inten = (float) Math.exp(-dy * dy / (2 * sg * sg));
                    inten *= 0.6f + 0.4f * (float) Math.sin(TAU * (t + x * 0.03f + i * 0.2f));
                    r += cols[i][0] * inten * 0.75f;
                    g += cols[i][1] * inten * 0.75f;
                    b += cols[i][2] * inten * 0.75f;
                }
                int h = hash(x, y, 3);
                if (h % 97 == 0) {
                    float tw = 0.5f + 0.5f * (float) Math.sin(TAU * (t + (h % 13) / 13f));
                    r += 230 * tw;
                    g += 230 * tw;
                    b += 230 * tw;
                }
                p[y * FW + x] = rgb(Math.round(r), Math.round(g), Math.round(b));
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ Inferno

    private static final int[][] FIRE = {
            {0, 10, 0, 0}, {25, 120, 10, 0}, {50, 230, 60, 0}, {75, 255, 150, 10}, {90, 255, 220, 80}, {100, 255, 250, 200}};

    private static int firePalette(float h) {
        h = Math.max(0f, Math.min(1f, h)) * 100f;
        for (int i = 1; i < FIRE.length; i++) {
            if (h <= FIRE[i][0]) {
                float k = (h - FIRE[i - 1][0]) / (float) (FIRE[i][0] - FIRE[i - 1][0]);
                return rgb(Math.round(FIRE[i - 1][1] + (FIRE[i][1] - FIRE[i - 1][1]) * k),
                        Math.round(FIRE[i - 1][2] + (FIRE[i][2] - FIRE[i - 1][2]) * k),
                        Math.round(FIRE[i - 1][3] + (FIRE[i][3] - FIRE[i - 1][3]) * k));
            }
        }
        return rgb(255, 250, 200);
    }

    private static int[] infernoFace(int f) {
        float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];
        for (int y = 0; y < FH; y++) {
            float u = y / (float) (FH - 1);
            float base = (float) Math.pow(u, 1.15);
            for (int x = 0; x < FW; x++) {
                float w1 = (float) Math.sin(x * 0.62f + TAU * t + y * 0.30f);
                float w2 = (float) Math.sin(x * 0.31f - 1.7f + TAU * 2 * t + y * 0.55f);
                float w3 = (float) Math.sin(x * 1.1f + TAU * 3 * t + y * 0.9f);
                float h = base * 1.25f + 0.22f * w1 + 0.16f * w2 + 0.08f * w3 - 0.18f;
                p[y * FW + x] = firePalette(h);
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ Matrix

    private static int[] matrixFace(int f) {
        float t = f / (float) FRAMES;
        int[] p = new int[FW * FH];
        for (int x = 0; x < FW; x++) {
            int c = x / 3;
            boolean gap = x % 3 == 2;
            int s = 1 + hash(c, 1, 7) % 2;
            int o = hash(c, 2, 7) % FH;
            int len = 14 + hash(c, 3, 7) % 18;
            int head = (int) Math.floor((t * s * FH + o) % FH);
            for (int y = 0; y < FH; y++) {
                int col = rgb(0, 6, 2);
                if (!gap) {
                    int d = ((head - y) % FH + FH) % FH;
                    if (d < len) {
                        float inten = 1f - d / (float) len;
                        if ((hash(x, y, f) & 3) == 0) inten *= 0.55f;
                        if (d == 0) col = rgb(210, 255, 220);
                        else col = rgb(0, Math.round(255 * (float) Math.pow(inten, 1.2)), Math.round(70 * inten));
                    }
                }
                p[y * FW + x] = col;
            }
        }
        return p;
    }
}
