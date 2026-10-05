package dev.candle.codex.ui;

import dev.candle.codex.config.Config;

import java.util.HashMap;
import java.util.Iterator;

/** Tiny animation helper: smooth values that chase a target, keyed by where a control is drawn. */
public final class Anim {
    private static final class S {
        float v;
        long t;
    }

    private static final HashMap<Long, S> M = new HashMap<>();

    /**
     * Added to y when building keys. Pages that scroll their own content set this to their scroll offset while
     * drawing, so a control keeps the same key (and its smooth hover state) while it moves.
     */
    public static int keyShift;

    private Anim() {}

    /**
     * User animation speed multiplier (Settings). 1 = normal, 0 in the config means "Off", which is returned as a
     * very large factor so every animation finishes within a single frame.
     */
    public static float speed() {
        float s = Config.data == null ? 1f : Config.data.animSpeed;
        if (!(s > 0f)) return 60f;
        return Math.max(0.25f, Math.min(4f, s));
    }

    public static long key(int kind, int x, int y, int w, int h) {
        long k = kind;
        k = k * 31 + x;
        k = k * 31 + (y + keyShift);
        k = k * 31 + w;
        k = k * 31 + h;
        return k;
    }

    /** Moves the stored value toward target. speed ~ 10..20 feels snappy but smooth. */
    public static float to(long key, float target, float speed) {
        long now = System.nanoTime();
        S s = M.get(key);
        if (s == null) {
            if (M.size() > 1200) prune(now);
            s = new S();
            s.v = target;
            s.t = now;
            M.put(key, s);
            return target;
        }
        float dt = Math.min(0.05f, (now - s.t) / 1e9f);
        s.t = now;
        s.v += (target - s.v) * Math.min(1f, dt * speed * speed());
        if (Math.abs(target - s.v) < 0.003f) s.v = target;
        return s.v;
    }

    /** Drops entries that were not touched for a few seconds; clears everything if that is not enough. */
    private static void prune(long now) {
        Iterator<S> it = M.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().t > 3_000_000_000L) it.remove();
        }
        if (M.size() > 1000) M.clear();
    }

    public static int argb(int a, int b, float t) {
        int r = 0;
        for (int sh = 24; sh >= 0; sh -= 8) {
            int ca = (a >>> sh) & 255, cb = (b >>> sh) & 255;
            r |= (Math.round(ca + (cb - ca) * t) & 255) << sh;
        }
        return r;
    }

    public static float ease(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    /** Eased 0..1 progress of item number index when items start step seconds apart and each takes dur seconds. */
    public static float stagger(float age, int index, float step, float dur) {
        return ease((age - index * step) / dur);
    }
}
