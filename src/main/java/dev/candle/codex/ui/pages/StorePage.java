package dev.candle.codex.ui.pages;

import dev.candle.codex.CodexClient;
import dev.candle.codex.net.GameVersion;
import dev.candle.codex.net.Installer;
import dev.candle.codex.net.IrisBridge;
import dev.candle.codex.net.Modrinth;
import dev.candle.codex.net.PackManager;
import dev.candle.codex.net.Restarter;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Img;
import dev.candle.codex.ui.Page;
import dev.candle.codex.ui.TextField;
import dev.candle.codex.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-game Modrinth store: mods, resource packs, shader packs, plus schematic import.
 * Only versions that match the running Minecraft version (read from Fabric metadata) and Fabric are listed.
 */
public final class StorePage extends Page {
    private static final String[] TABS = {"Mods", "Resource Packs", "Shaders", "Schematics"};
    private static final Installer.Kind[] KINDS = {Installer.Kind.MOD, Installer.Kind.RESOURCEPACK, Installer.Kind.SHADER, null};
    private static final String[] SORT_LABELS = {"Relevance", "Downloads", "Newest", "Updated"};
    private static final String[] SORT_KEYS = {"relevance", "downloads", "newest", "updated"};
    private static final int ROW = 40;

    private final TextField search = new TextField("Search Modrinth...");
    private static volatile int pendingTab = -1;
    private final ProjectView view = new ProjectView(new ProjectView.Host() {
        @Override
        public boolean busy() {
            return busy;
        }

        @Override
        public int progress() {
            return busy ? percent(status) : -1;
        }

        @Override
        public boolean installed(String id) {
            return done.contains(id);
        }

        @Override
        public void install(String id, Installer.Kind k) {
            StorePage.this.install(id, k);
        }
    });
    private final SchematicsView schem = new SchematicsView(new SchematicsView.Host() {
        @Override
        public boolean busy() {
            return busy;
        }

        @Override
        public void importLink(String link) {
            importSchematic(link);
        }

        @Override
        public void installLitematica() {
            installByIds(java.util.List.of("litematica"), "Litematica");
        }
    });
    private int tab, sort, page;
    private int pageSize = 6;
    private long searchAt = -1;
    private final AtomicInteger requestId = new AtomicInteger();

    private volatile Modrinth.SearchResult result;
    private volatile boolean loading;
    private volatile String error = "";
    private volatile String status = "";
    private volatile boolean busy;
    private volatile boolean needsRestart;
    private String restartError = "";
    private final Set<String> done = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public StorePage() {
        super("Store");
        search.onChange = s -> searchAt = System.currentTimeMillis() + 450;
        search.onEnter = () -> {
            page = 0;
            fetch();
        };
    }

    /** Lets the lobby open the store on a given tab (0 mods, 1 resource packs, 2 shaders, 3 schematics). */
    public static void requestTab(int t) {
        pendingTab = t;
    }

    // ------------------------------------------------------------------ helpers

    /** "Downloading Name 45%" -> 45, anything else -> -1. */
    static int percent(String s) {
        if (s == null || !s.endsWith("%")) return -1;
        int sp = s.lastIndexOf(' ');
        if (sp < 0) return -1;
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(s.substring(sp + 1, s.length() - 1))));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Turns a technical error text into a sentence a player understands. Shared with the project and schematic views. */
    static String nice(String raw) {
        if (raw == null || raw.isBlank()) return "Something went wrong. Please try again.";
        String l = raw.toLowerCase(Locale.ROOT);
        if (l.contains("network error") || l.contains("unknownhost") || l.contains("connect") || l.contains("timed out")
                || l.contains("timeout") || l.contains("no route")) {
            return "Can't reach the server. Check your internet connection and try again.";
        }
        if (l.contains("rate limit") || l.contains("http 429")) {
            return "Too many requests. Wait a few seconds and try again.";
        }
        if (l.matches(".*http 5\\d\\d.*")) {
            return "The server has a problem right now. Try again later.";
        }
        if (l.contains("sha-1 mismatch")) {
            return "The download was damaged, so it was thrown away. Please try again.";
        }
        if (l.contains("has no build for")) {
            return raw + ". Pick another project or wait for an update.";
        }
        if (l.contains("access is denied") || l.contains("permission") || l.contains("no space") || l.contains("could not save file")) {
            return "Could not write the file. Check free disk space and folder permissions.";
        }
        return raw;
    }

    @Override
    public void onShow() {
        int pt = pendingTab;
        if (pt >= 0 && pt < TABS.length) {
            pendingTab = -1;
            tab = pt;
            page = 0;
            result = null;
            error = "";
            view.close();
            search.setText("");
        }
        if (tab == 3) schem.onShow();
        else if (result == null) fetch();
    }

    private Installer.Kind kind() {
        return KINDS[tab];
    }

    private void fetch() {
        if (tab >= 3) return;
        searchAt = -1;
        final int id = requestId.incrementAndGet();
        final Installer.Kind k = kind();
        final String q = search.text.trim();
        final int off = page * pageSize;
        final String sortKey = SORT_KEYS[sort];
        final int limit = pageSize;
        loading = true;
        error = "";
        CodexClient.IO.execute(() -> {
            try {
                Modrinth.SearchResult r = Modrinth.search(k, q, sortKey, off, limit);
                if (id == requestId.get()) {
                    result = r;
                    loading = false;
                }
            } catch (RuntimeException e) {
                if (id == requestId.get()) {
                    error = nice(e.getMessage() == null ? "Search failed" : e.getMessage());
                    loading = false;
                }
            }
        });
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        Theme t = ThemeManager.current();
        if (searchAt > 0 && System.currentTimeMillis() >= searchAt) {
            page = 0;
            fetch();
        }
        Draw.textShadow(g, "Store", x + 4, y + 2, t.cAccent2);
        Draw.textR(g, "Minecraft " + GameVersion.get() + " | Fabric", x + w - 2, y + 3, t.cDim);

        int tx = x;
        for (int i = 0; i < TABS.length; i++) {
            int tw = Draw.font().width(TABS[i]) + 20;
            Ui.chip(g, t, TABS[i], tx, y + 18, tw, 18, i == tab, Ui.in(mx, my, tx, y + 18, tw, 18));
            tx += tw + 5;
        }

        if (tab == 3) {
            schem.render(g, t, mx, my, x, y + 42, w, h - 82);
        } else if (view.isOpen()) {
            view.render(g, t, mx, my, x, y + 42, w, h - 82);
        } else {
            renderBrowser(g, t, mx, my);
        }
        renderFooter(g, t, mx, my);
    }

    private void renderBrowser(GuiGraphics g, Theme t, int mx, int my) {
        int sy = y + 42;
        search.bounds(x, sy, Math.min(240, w - 260));
        search.render(g, t);
        int cx = search.x + search.w + 8;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int cw = Draw.font().width(SORT_LABELS[i]) + 14;
            if (cx + cw > x + w) break;
            Ui.chip(g, t, SORT_LABELS[i], cx, sy + 1, cw, 16, i == sort, Ui.in(mx, my, cx, sy + 1, cw, 16));
            cx += cw + 4;
        }

        int top = sy + 26;
        int footer = 44;
        int avail = y + h - footer - top - 22;
        pageSize = Math.max(3, avail / (ROW + 4));
        Modrinth.SearchResult r = result;

        if (tab == 2 && !IrisBridge.present()) {
            Draw.rr(g, x, top, w, 26, t.radius, (0x30 << 24) | t.cAccent);
            Draw.text(g, "Shader packs need Iris (and Sodium) to be used.", x + 10, top + 9, t.cText);
            String bl = "Install Iris";
            int bw = Draw.font().width(bl) + 20;
            Ui.button(g, t, bl, x + w - bw - 6, top + 3, bw, 20, Ui.in(mx, my, x + w - bw - 6, top + 3, bw, 20), true);
            top += 32;
        }

        if (loading && r == null) {
            Draw.textC(g, "Loading from Modrinth...", x + w / 2, top + 30, t.cDim);
        } else if (!error.isEmpty()) {
            Draw.textC(g, Draw.fit(error, w - 20), x + w / 2, top + 30, 0xE06060);
        } else if (r != null) {
            if (r.hits.isEmpty()) {
                Draw.textC(g, "Nothing found for Minecraft " + GameVersion.get() + ".", x + w / 2, top + 30, t.cDim);
            }
            for (int i = 0; i < r.hits.size() && i < pageSize; i++) {
                drawRow(g, t, r.hits.get(i), x, top + i * (ROW + 4), mx, my);
            }
            int pages = Math.max(1, (r.total + pageSize - 1) / pageSize);
            int py = y + h - footer - 14;
            Draw.textC(g, "Page " + (page + 1) + " / " + pages + "  (" + r.total + " results)", x + w / 2, py + 3, t.cDim);
            boolean hp = Ui.in(mx, my, x + w / 2 - 110, py - 2, 30, 16);
            boolean hn = Ui.in(mx, my, x + w / 2 + 80, py - 2, 30, 16);
            Ui.button(g, t, "<", x + w / 2 - 110, py - 2, 30, 16, hp, false);
            Ui.button(g, t, ">", x + w / 2 + 80, py - 2, 30, 16, hn, false);
        }
        if (loading && r != null) Draw.textR(g, "Loading...", x + w, sy + 5, t.cDim);
    }

    private void drawRow(GuiGraphics g, Theme t, Modrinth.Hit h, int px, int py, int mx, int my) {
        boolean hover = Ui.in(mx, my, px, py, w, ROW);
        Ui.card(g, t, px, py, w, ROW, hover, 0f);
        int ix = px + 6, iy = py + 4, is = ROW - 8;
        Img.coverOr(g, t, h.iconUrl, ix, iy, is, is, 5, h.title);
        int tx = px + is + 14;
        String title = Draw.fit(h.title, w - 270);
        Draw.text(g, title, tx, py + 7, t.cAccent2);
        Draw.text(g, "by " + h.author, tx + 4 + Draw.font().width(title), py + 7, t.cDim);
        Draw.text(g, Draw.fit(h.description == null ? "" : h.description, w - 200), tx, py + 21, t.cText);
        Draw.textR(g, formatCount(h.downloads) + " downloads", px + w - 96, py + 7, t.cDim);
        boolean isDone = done.contains(h.id);
        int bx = px + w - 84, by = py + 10, bw = 74, bh = 20;
        if (isDone) {
            Draw.rr(g, bx, by, bw, bh, 5, (0x30 << 24) | t.cAccent);
            Draw.textC(g, "Installed", bx + bw / 2, by + 6, t.cAccent2);
        } else {
            Ui.button(g, t, busy ? "..." : "Install", bx, by, bw, bh, !busy && Ui.in(mx, my, bx, by, bw, bh), !busy);
        }
    }

    private static String formatCount(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
        return Long.toString(n);
    }

    private void renderFooter(GuiGraphics g, Theme t, int mx, int my) {
        int fy = y + h - 36;
        Draw.rr(g, x, fy, w, 34, t.radius, t.aPanel);
        Draw.ring(g, x, fy, w, 34, t.radius, t.aBorder);
        String st = !restartError.isEmpty() ? restartError : status;
        if (st.isEmpty()) st = "Files are saved into this game instance: " + FabricLoader.getInstance().getGameDir().getFileName();
        int textW = needsRestart ? w - 150 : w - 20;
        boolean working = busy;
        Draw.text(g, Draw.fit(st, textW), x + 10, working ? fy + 8 : fy + 13, working ? t.cAccent2 : t.cText);
        if (working) {
            // progress bar: filled by the download percent, or a sliding block while the size is not known yet
            int bx = x + 10, by = fy + 22, bw = w - 20;
            Draw.rr(g, bx, by, bw, 4, 2, (0x40 << 24) | (t.cDim & 0xFFFFFF));
            int pct = percent(status);
            int fw, fx = bx;
            if (pct >= 0) {
                fw = Math.max(4, bw * pct / 100);
            } else {
                fw = Math.max(8, bw / 4);
                float ph = (System.currentTimeMillis() % 1500L) / 1500f;
                fx = bx + Math.round((bw - fw) * ph);
            }
            Draw.rr(g, fx, by, fw, 4, 2, 0xFF000000 | (t.cAccent & 0xFFFFFF));
        }
        if (needsRestart) {
            String rl = "Install and restart";
            int rw = Draw.font().width(rl) + 20;
            Ui.button(g, t, rl, x + w - rw - 8, fy + 7, rw, 20, Ui.in(mx, my, x + w - rw - 8, fy + 7, rw, 20), true);
        }
    }

    // ------------------------------------------------------------------ actions

    private void install(String projectId, Installer.Kind k) {
        if (busy) return;
        busy = true;
        restartError = "";
        status = "Starting...";
        Installer.install(projectId, k, s -> status = s).whenComplete((r, ex) -> {
            busy = false;
            if (ex != null) {
                status = "Failed: " + nice(Installer.message(ex));
                return;
            }
            done.add(projectId);
            afterInstall(k, r);
        });
    }

    private void afterInstall(Installer.Kind k, Installer.Result r) {
        String last = r.notes.isEmpty() ? "Done" : r.notes.get(r.notes.size() - 1);
        Minecraft mc = Minecraft.getInstance();
        switch (k) {
            case MOD -> {
                needsRestart |= r.needsRestart;
                status = r.needsRestart
                        ? "Installed. Fabric loads mods at startup, so press 'Install and restart' to start using it."
                        : last;
            }
            case RESOURCEPACK -> {
                if (!r.files.isEmpty()) {
                    PackManager.enableAndReload(r.files.get(r.files.size() - 1).getFileName().toString());
                    status = "Resource pack downloaded, enabled and reloaded.";
                } else status = last;
            }
            case SHADER -> {
                if (!r.files.isEmpty()) {
                    String name = r.files.get(r.files.size() - 1).getFileName().toString();
                    mc.execute(() -> {
                        boolean ok = IrisBridge.enable(name);
                        status = ok ? "Shader pack downloaded and enabled." : IrisBridge.present()
                                ? "Shader pack downloaded. Select it in Video Settings > Shader Packs."
                                : "Shader pack downloaded. Install Iris to use it.";
                    });
                } else status = last;
            }
        }
    }

    private void installByIds(java.util.List<String> slugs, String label) {
        if (busy) return;
        busy = true;
        restartError = "";
        status = "Installing " + label + "...";
        Installer.installAll(slugs, Installer.Kind.MOD, s -> status = s).whenComplete((r, ex) -> {
            busy = false;
            if (ex != null) {
                status = "Failed: " + nice(Installer.message(ex));
                return;
            }
            needsRestart |= r.needsRestart;
            status = r.files.isEmpty() ? nice(String.join("; ", r.notes)) : label + " installed. Restart to load it.";
        });
    }

    private void importSchematic(String link) {
        if (busy || link == null || link.isBlank()) return;
        busy = true;
        restartError = "";
        status = "Downloading schematic...";
        Installer.installSchematic(link, st -> status = st).whenComplete((r, ex) -> {
            busy = false;
            status = ex != null ? "Failed: " + nice(Installer.message(ex)) : String.join(" ", r.notes);
        });
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean click(double mx, double my, int button) {
        int tx = x;
        for (int i = 0; i < TABS.length; i++) {
            int tw = Draw.font().width(TABS[i]) + 20;
            if (Ui.in(mx, my, tx, y + 18, tw, 18)) {
                view.close();
                if (tab != i) {
                    tab = i;
                    page = 0;
                    result = null;
                    error = "";
                    search.setText("");
                    if (tab < 3) fetch();
                    else schem.onShow();
                }
                return true;
            }
            tx += tw + 5;
        }
        int fy = y + h - 36;
        if (needsRestart) {
            String rl = "Install and restart";
            int rw = Draw.font().width(rl) + 20;
            if (Ui.in(mx, my, x + w - rw - 8, fy + 7, rw, 20)) {
                String err = Restarter.restart(Minecraft.getInstance());
                restartError = err == null ? "" : err;
                return true;
            }
        }
        if (tab == 3) return schem.click(mx, my, x, y + 42, w, h - 82);
        if (view.isOpen()) return view.click(mx, my);

        search.click(mx, my);
        int sy = y + 42;
        int cx = search.x + search.w + 8;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int cw = Draw.font().width(SORT_LABELS[i]) + 14;
            if (cx + cw > x + w) break;
            if (Ui.in(mx, my, cx, sy + 1, cw, 16)) {
                sort = i;
                page = 0;
                fetch();
                return true;
            }
            cx += cw + 4;
        }
        int top = sy + 26;
        if (tab == 2 && !IrisBridge.present()) {
            String bl = "Install Iris";
            int bw = Draw.font().width(bl) + 20;
            if (Ui.in(mx, my, x + w - bw - 6, top + 3, bw, 20)) {
                installByIds(java.util.List.of("iris"), "Iris");
                return true;
            }
            top += 32;
        }
        Modrinth.SearchResult r = result;
        if (r != null) {
            for (int i = 0; i < r.hits.size() && i < pageSize; i++) {
                int py = top + i * (ROW + 4);
                if (Ui.in(mx, my, x + w - 84, py + 10, 74, 20) && !done.contains(r.hits.get(i).id)) {
                    install(r.hits.get(i).id, kind());
                    return true;
                }
                if (Ui.in(mx, my, x, py, w, ROW)) {
                    view.open(r.hits.get(i), kind());
                    return true;
                }
            }
            int pyy = y + h - 44 - 14;
            int pages = Math.max(1, (r.total + pageSize - 1) / pageSize);
            if (Ui.in(mx, my, x + w / 2 - 110, pyy - 2, 30, 16) && page > 0) {
                page--;
                fetch();
                return true;
            }
            if (Ui.in(mx, my, x + w / 2 + 80, pyy - 2, 30, 16) && page + 1 < pages) {
                page++;
                fetch();
                return true;
            }
        }
        return search.focused;
    }

    @Override
    public void drag(double mx, double my) {
        if (tab == 3) schem.drag(my);
        else if (view.isOpen()) view.drag(my);
    }

    @Override
    public void release() {
        schem.release();
        view.release();
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (tab == 3) return schem.scroll(amount);
        if (view.isOpen()) return view.scroll(amount);
        Modrinth.SearchResult r = result;
        if (r == null) return false;
        int pages = Math.max(1, (r.total + pageSize - 1) / pageSize);
        int np = Math.max(0, Math.min(pages - 1, page - (int) Math.signum(amount)));
        if (np != page && !loading) {
            page = np;
            fetch();
        }
        return true;
    }

    @Override
    public boolean key(int key, int mods) {
        return tab == 3 ? schem.key(key, mods) : search.key(key, mods);
    }

    @Override
    public boolean chr(int cp) {
        return tab == 3 ? schem.chr(cp) : search.chr(cp);
    }

    @Override
    public boolean typing() {
        return search.focused || schem.typing();
    }
}
