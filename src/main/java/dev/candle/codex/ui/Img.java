package dev.candle.codex.ui;

import dev.candle.codex.CodexClient;
import dev.candle.codex.net.Modrinth;
import dev.candle.codex.theme.Theme;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads images (mod icons, gallery pictures, avatars) on the IO pool, turns them into GPU textures on the
 * render thread and draws them. A small LRU cache keeps memory bounded.
 *
 * Loading order for every picture: original PNG/JPG/GIF (Modrinth thumbnails are WebP, which the game cannot read),
 * then a PNG conversion by a public image proxy (only for WebP, switch off with USE_PROXY). Every file is decoded by
 * the game's own decoder first and by Java ImageIO (re-encoded to PNG in memory) if that fails.
 * A failed picture is retried after a while, and the reason of every failed try is written to the log in ONE line.
 */
public final class Img {
    public static final class Tex {
        public final Identifier id;
        public final int w, h;

        Tex(Identifier id, int w, int h) {
            this.id = id;
            this.w = w;
            this.h = h;
        }
    }

    /** Set to false to never contact the WebP-to-PNG proxy (WebP-only pictures then show a placeholder). */
    private static final boolean USE_PROXY = true;
    private static final String PROXY = "https://wsrv.nl/?output=png&url=";

    private static final int MAX_TEXTURES = 70;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;
    private static final long RETRY_MS = 90_000L;
    private static final Map<String, Tex> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
    private static final Pattern THUMB = Pattern.compile("^(.*)_\\d+\\.webp$", Pattern.CASE_INSENSITIVE);
    private static int counter;

    private Img() {}

    private record Dl(byte[] data, int status, String note) {}

    /** Returns the texture if it is ready, otherwise starts loading it and returns null. */
    public static Tex get(String url) {
        if (url == null || url.isEmpty()) return null;
        Tex t = CACHE.get(url);
        if (t != null) return t;
        if (failed(url) || !PENDING.add(url)) return null;
        CodexClient.IO.execute(() -> fetch(url));
        return null;
    }

    /** True while a picture is in its "failed" pause. After RETRY_MS it is tried again. */
    public static boolean failed(String url) {
        if (url == null) return false;
        Long at = FAILED.get(url);
        if (at == null) return false;
        if (System.currentTimeMillis() - at > RETRY_MS) {
            FAILED.remove(url);
            return false;
        }
        return true;
    }

    private static String proxy(String url) {
        return PROXY + URLEncoder.encode(url, StandardCharsets.UTF_8);
    }

    /** Modrinth serves thumbnails as name_96.webp, which the game cannot decode. Try the original formats first. */
    private static List<String> candidates(String url) {
        List<String> out = new ArrayList<>();
        String bare = url;
        int q = bare.indexOf('?');
        if (q > 0) bare = bare.substring(0, q);
        Matcher m = THUMB.matcher(bare);
        if (m.matches()) {
            String base = m.group(1);
            out.add(base + ".png");
            out.add(base + ".jpeg");
            out.add(base + ".jpg");
            out.add(base + ".gif");
            out.add(USE_PROXY ? proxy(url) : url);
        } else if (bare.toLowerCase(Locale.ROOT).endsWith(".webp")) {
            out.add(USE_PROXY ? proxy(url) : url);
        } else {
            out.add(url);
        }
        return out;
    }

    private static String shortName(String u) {
        if (u.startsWith(PROXY)) return "proxy";
        int q = u.indexOf('?');
        String s = q > 0 ? u.substring(0, q) : u;
        int slash = s.lastIndexOf('/');
        String n = slash >= 0 ? s.substring(slash + 1) : s;
        return n.length() > 40 ? n.substring(n.length() - 40) : n;
    }

    /** One HTTP try. status: HTTP code, -1 network problem (worth a retry), -2 interrupted. */
    private static Dl download(String url) {
        try {
            URI u = URI.create(url);
            if (!"https".equalsIgnoreCase(u.getScheme())) return new Dl(null, 0, "not https");
            HttpRequest rq = HttpRequest.newBuilder(u)
                    .header("User-Agent", Modrinth.userAgent())
                    .header("Accept", "image/png,image/jpeg,image/gif,image/*;q=0.8")
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<InputStream> res = Modrinth.HTTP.send(rq, HttpResponse.BodyHandlers.ofInputStream());
            int code = res.statusCode();
            if (code / 100 != 2) {
                res.body().close();
                return new Dl(null, code, "HTTP " + code);
            }
            try (InputStream in = res.body()) {
                byte[] data = in.readNBytes((int) MAX_BYTES + 1);
                if (data.length > MAX_BYTES) return new Dl(null, code, "bigger than 8 MB");
                return new Dl(data, code, "ok");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Dl(null, -2, "interrupted");
        } catch (Exception e) {
            return new Dl(null, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean isWebp(byte[] d) {
        return d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    /** Game decoder first, then Java ImageIO re-encoded to PNG in memory. Returns null (and a reason) on failure. */
    private static NativeImage decode(byte[] data, List<String> why, String label) {
        if (isWebp(data)) {
            why.add(label + " is WebP (the game cannot read it)");
            return null;
        }
        NativeImage img = null;
        try {
            img = NativeImage.read(new ByteArrayInputStream(data));
        } catch (Exception e) {
            why.add(label + " game decoder: " + e.getMessage());
        }
        if (img == null) {
            try {
                java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
                if (bi == null) {
                    why.add(label + " ImageIO: unknown format");
                    return null;
                }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                if (!javax.imageio.ImageIO.write(bi, "png", bo)) {
                    why.add(label + " ImageIO: could not convert to PNG");
                    return null;
                }
                img = NativeImage.read(new ByteArrayInputStream(bo.toByteArray()));
            } catch (Throwable e) {
                why.add(label + " ImageIO: " + e);
                return null;
            }
        }
        if ((long) img.getWidth() * img.getHeight() > MAX_PIXELS) {
            why.add(label + " is too big (" + img.getWidth() + "x" + img.getHeight() + ")");
            img.close();
            return null;
        }
        return img;
    }

    private static void fetch(String url) {
        List<String> why = new ArrayList<>();
        for (String u : candidates(url)) {
            String label = shortName(u);
            Dl d = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                d = download(u);
                // only network errors and server errors are worth a second try
                if (d.data() != null || d.status() == -2 || (d.status() >= 0 && d.status() < 500)) break;
            }
            if (d.status() == -2) {
                fail(url);
                return;
            }
            if (d.data() == null) {
                why.add(label + " " + d.note());
                continue;
            }
            if (d.data().length < 16) {
                why.add(label + " is empty");
                continue;
            }
            NativeImage image = decode(d.data(), why, label);
            if (image != null) {
                final NativeImage ready = image;
                Minecraft.getInstance().execute(() -> create(url, ready));
                return;
            }
        }
        CodexClient.LOG.info("Codex: could not load image {} -> {}", url, why.isEmpty() ? "no source" : String.join("; ", why));
        fail(url);
    }

    private static void fail(String url) {
        FAILED.put(url, System.currentTimeMillis());
        PENDING.remove(url);
    }

    /** Render thread. */
    private static void create(String url, NativeImage image) {
        try {
            int w = image.getWidth(), h = image.getHeight();
            final String label = "candle/img/" + (counter++);
            DynamicTexture tex = new DynamicTexture(() -> label, image);
            Identifier id = Identifier.fromNamespaceAndPath(CodexClient.ID, "dyn/" + label.substring(label.lastIndexOf('/') + 1));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            CACHE.put(url, new Tex(id, w, h));
            PENDING.remove(url);
            trim();
        } catch (Exception e) {
            CodexClient.LOG.info("Codex: texture upload failed for {} ({})", url, e.toString());
            try {
                image.close();
            } catch (Exception ignored) {
            }
            fail(url);
        }
    }

    private static void trim() {
        if (CACHE.size() <= MAX_TEXTURES) return;
        Iterator<Map.Entry<String, Tex>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && it.hasNext()) {
            Map.Entry<String, Tex> e = it.next();
            Minecraft.getInstance().getTextureManager().release(e.getValue().id);
            it.remove();
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Draws a region of any texture scaled into the destination rectangle. */
    public static void region(GuiGraphics g, Identifier id, int x, int y, int w, int h,
                              float u, float v, int regionW, int regionH, int texW, int texH) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, texW, texH);
    }

    /** Fills the box with the picture (centre crop, keeps the aspect ratio). Returns false while loading / failed. */
    public static boolean cover(GuiGraphics g, String url, int x, int y, int w, int h) {
        Tex t = get(url);
        if (t == null) return false;
        float boxR = w / (float) h, imgR = t.w / (float) t.h;
        int sw = t.w, sh = t.h, sx = 0, sy = 0;
        if (imgR > boxR) {
            sw = Math.max(1, Math.round(t.h * boxR));
            sx = (t.w - sw) / 2;
        } else {
            sh = Math.max(1, Math.round(t.w / boxR));
            sy = (t.h - sh) / 2;
        }
        region(g, t.id, x, y, w, h, sx, sy, sw, sh, t.w, t.h);
        return true;
    }

    /** Fits the whole picture inside the box (letterbox), centred. Returns false while loading / failed. */
    public static boolean fit(GuiGraphics g, String url, int x, int y, int w, int h) {
        Tex t = get(url);
        if (t == null) return false;
        float s = Math.min(w / (float) t.w, h / (float) t.h);
        int dw = Math.max(1, Math.round(t.w * s)), dh = Math.max(1, Math.round(t.h * s));
        region(g, t.id, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, 0, 0, t.w, t.h, t.w, t.h);
        return true;
    }

    /** A soft tinted tile with the first letter of the name. It pulses gently while the picture is still loading. */
    public static void placeholder(GuiGraphics g, Theme t, int x, int y, int w, int h, int r, String label, boolean loading) {
        int a = loading ? 0x16 + (int) (0x14 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 260.0))) : 0x1C;
        Draw.rr(g, x, y, w, h, r, t.aPanel);
        if (w > 4 && h > 4) Draw.rr(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), (a << 24) | (t.cAccent & 0xFFFFFF));
        if (w >= 14 && h >= 14) {
            String s = label == null || label.isBlank() ? "?" : label.strip().substring(0, 1).toUpperCase(Locale.ROOT);
            Draw.textC(g, s, x + w / 2, y + h / 2 - 4, loading ? t.cDim : t.cAccent2);
        }
    }

    /** Picture if ready, otherwise the placeholder tile. */
    public static void coverOr(GuiGraphics g, Theme t, String url, int x, int y, int w, int h, int r, String label) {
        if (cover(g, url, x, y, w, h)) return;
        boolean loading = url != null && !url.isEmpty() && !failed(url);
        placeholder(g, t, x, y, w, h, r, label, loading);
    }
}
