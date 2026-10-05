package dev.candle.codex.ui.pages;

import dev.candle.codex.CodexClient;
import dev.candle.codex.net.SchematicHub;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Img;
import dev.candle.codex.ui.ScrollBar;
import dev.candle.codex.ui.TextField;
import dev.candle.codex.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The "Schematics" tab: search GitHub for .litematic files, browse a repository, or paste a direct link. */
public final class SchematicsView {
    public interface Host {
        boolean busy();

        void importLink(String url);

        void installLitematica();
    }

    private static final int ROW = 34;
    private final Host host;
    private final TextField field = new TextField("Search schematics on GitHub, or paste a direct https link...");
    private final ScrollBar bar = new ScrollBar();
    private volatile List<SchematicHub.Repo> repos;
    private volatile List<SchematicHub.FileEntry> files;
    private volatile SchematicHub.Repo openRepo;
    private volatile boolean loading;
    private volatile String error = "";
    private int scroll;
    private int top, rows;
    private final Set<String> saved = ConcurrentHashMap.newKeySet();

    public SchematicsView(Host host) {
        this.host = host;
        field.max = 300;
        field.onEnter = this::submit;
    }

    public void onShow() {
        if (repos == null && !loading) search("");
    }

    private boolean isLink() {
        return field.text.trim().toLowerCase(Locale.ROOT).startsWith("http");
    }

    private void submit() {
        if (isLink()) {
            host.importLink(field.text.trim());
        } else {
            search(field.text);
        }
    }

    private void search(String q) {
        loading = true;
        error = "";
        openRepo = null;
        files = null;
        scroll = 0;
        CodexClient.IO.execute(() -> {
            try {
                repos = SchematicHub.search(q);
            } catch (RuntimeException e) {
                error = StorePage.nice(e.getMessage() == null ? "Search failed" : e.getMessage());
            }
            loading = false;
        });
    }

    private void open(SchematicHub.Repo r) {
        openRepo = r;
        files = null;
        loading = true;
        error = "";
        scroll = 0;
        CodexClient.IO.execute(() -> {
            try {
                files = SchematicHub.files(r);
            } catch (RuntimeException e) {
                error = StorePage.nice(e.getMessage() == null ? "Could not list files" : e.getMessage());
            }
            loading = false;
        });
    }

    private static String size(long b) {
        if (b >= 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
        return Math.max(1, b / 1024) + " KB";
    }

    // ------------------------------------------------------------------ render

    public void render(GuiGraphics g, Theme t, int mx, int my, int x, int y, int w, int h) {
        field.bounds(x, y, w - 96);
        field.render(g, t);
        String bl = isLink() ? "Download" : "Search";
        int bx = x + w - 90;
        Ui.button(g, t, bl, bx, y, 90, 18, !host.busy() && Ui.in(mx, my, bx, y, 90, 18), !host.busy());

        top = y + 26;
        int bottom = y + h - 40;
        rows = Math.max(2, (bottom - top) / (ROW + 3));
        boolean inRepo = openRepo != null;
        int ry = top;
        if (inRepo) {
            Ui.button(g, t, "< Back", x, top, 56, 18, Ui.in(mx, my, x, top, 56, 18), false);
            Draw.text(g, Draw.fit(openRepo.fullName, w - 70), x + 64, top + 5, t.cAccent2);
            ry += 24;
            rows = Math.max(2, (bottom - ry) / (ROW + 3));
        }
        int total = 0; // number of list entries, drives the scrollbar

        if (loading) {
            Draw.textC(g, "Loading...", x + w / 2, ry + 30, t.cDim);
        } else if (!error.isEmpty()) {
            Draw.textC(g, Draw.fit(error, w - 20), x + w / 2, ry + 30, 0xE06060);
        } else if (inRepo) {
            List<SchematicHub.FileEntry> fl = files;
            if (fl != null) {
                total = fl.size();
                if (fl.isEmpty()) Draw.textC(g, "No schematic files in this repository.", x + w / 2, ry + 30, t.cDim);
                scroll = Math.max(0, Math.min(scroll, Math.max(0, fl.size() - rows)));
                for (int i = 0; i < rows && scroll + i < fl.size(); i++) {
                    SchematicHub.FileEntry f = fl.get(scroll + i);
                    int py = ry + i * (ROW + 3);
                    boolean hover = Ui.in(mx, my, x, py, w, ROW);
                    Ui.card(g, t, x, py, w, ROW, hover, 0f);
                    Draw.text(g, Draw.fit(f.name, w - 190), x + 10, py + 7, t.cAccent2);
                    Draw.text(g, Draw.fit(f.path, w - 190), x + 10, py + 20, t.cDim);
                    Draw.textR(g, size(f.size), x + w - 100, py + 13, t.cDim);
                    String url = SchematicHub.rawUrl(openRepo, f);
                    int dx = x + w - 90, dy = py + 7;
                    if (saved.contains(url)) {
                        Draw.rr(g, dx, dy, 80, 20, 5, (0x30 << 24) | t.cAccent);
                        Draw.textC(g, "Saved", dx + 40, dy + 6, t.cAccent2);
                    } else {
                        Ui.button(g, t, host.busy() ? "..." : "Download", dx, dy, 80, 20, !host.busy() && Ui.in(mx, my, dx, dy, 80, 20), !host.busy());
                    }
                }
                Draw.textR(g, fl.size() + " files", x + w, top + 5, t.cDim);
            }
        } else {
            List<SchematicHub.Repo> rl = repos;
            if (rl != null) {
                total = rl.size();
                if (rl.isEmpty()) Draw.textC(g, "Nothing found. Try another word.", x + w / 2, ry + 30, t.cDim);
                scroll = Math.max(0, Math.min(scroll, Math.max(0, rl.size() - rows)));
                for (int i = 0; i < rows && scroll + i < rl.size(); i++) {
                    SchematicHub.Repo r = rl.get(scroll + i);
                    int py = ry + i * (ROW + 3);
                    boolean hover = Ui.in(mx, my, x, py, w, ROW);
                    Ui.card(g, t, x, py, w, ROW, hover, 0f);
                    Img.coverOr(g, t, r.avatar, x + 6, py + 5, 24, 24, 4, r.fullName);
                    Draw.text(g, Draw.fit(r.fullName, w - 200), x + 38, py + 7, t.cAccent2);
                    Draw.text(g, Draw.fit(r.description == null ? "" : r.description, w - 150), x + 38, py + 20, t.cText);
                    Draw.textR(g, r.stars + " stars", x + w - 80, py + 7, t.cDim);
                    Ui.button(g, t, "Files", x + w - 62, py + 7, 52, 20, hover, false);
                }
            }
        }

        // draggable scrollbar next to the list (scroll units are list rows); hidden when everything fits
        int listH = Math.max(0, rows * (ROW + 3) - 3);
        bar.render(g, t, x + w + 3, ry, listH, scroll, Math.max(0, total - rows), mx, my);

        // Litematica row
        int ly = y + h - 34;
        Draw.rr(g, x, ly, w, 30, t.radius, t.aPanel);
        Draw.ring(g, x, ly, w, 30, t.radius, t.aBorder);
        boolean has = FabricLoader.getInstance().isModLoaded("litematica");
        Draw.text(g, Draw.fit("Saved to: " + FabricLoader.getInstance().getGameDir().resolve("schematics") + "   (place them with Litematica)", w - 170), x + 10, ly + 11, t.cDim);
        String ll = has ? "Litematica installed" : "Install Litematica";
        int lw = Draw.font().width(ll) + 18;
        Ui.button(g, t, ll, x + w - lw - 6, ly + 5, lw, 20, !has && !host.busy() && Ui.in(mx, my, x + w - lw - 6, ly + 5, lw, 20), !has && !host.busy());
    }

    // ------------------------------------------------------------------ input

    public boolean click(double mx, double my, int x, int y, int w, int h) {
        if (bar.click(mx, my)) {
            scroll = bar.scrollAt(my);
            return true;
        }
        field.click(mx, my);
        if (Ui.in(mx, my, x + w - 90, y, 90, 18)) {
            if (!host.busy()) submit();
            return true;
        }
        int ly = y + h - 34;
        boolean has = FabricLoader.getInstance().isModLoaded("litematica");
        String ll = has ? "Litematica installed" : "Install Litematica";
        int lw = Draw.font().width(ll) + 18;
        if (!has && Ui.in(mx, my, x + w - lw - 6, ly + 5, lw, 20)) {
            host.installLitematica();
            return true;
        }
        int ry = y + 26;
        if (openRepo != null) {
            if (Ui.in(mx, my, x, ry, 56, 18)) {
                openRepo = null;
                files = null;
                error = "";
                scroll = 0;
                return true;
            }
            ry += 24;
            List<SchematicHub.FileEntry> fl = files;
            if (fl != null) {
                for (int i = 0; i < rows && scroll + i < fl.size(); i++) {
                    int py = ry + i * (ROW + 3);
                    if (Ui.in(mx, my, x + w - 90, py + 7, 80, 20) && !host.busy()) {
                        String url = SchematicHub.rawUrl(openRepo, fl.get(scroll + i));
                        saved.add(url);
                        host.importLink(url);
                        return true;
                    }
                }
            }
        } else {
            List<SchematicHub.Repo> rl = repos;
            if (rl != null) {
                for (int i = 0; i < rows && scroll + i < rl.size(); i++) {
                    int py = ry + i * (ROW + 3);
                    if (Ui.in(mx, my, x, py, w, ROW)) {
                        open(rl.get(scroll + i));
                        return true;
                    }
                }
            }
        }
        return field.focused;
    }

    /** Mouse drag on the scrollbar. */
    public void drag(double my) {
        if (bar.dragging()) scroll = bar.scrollAt(my);
    }

    public void release() {
        bar.release();
    }

    public boolean scroll(double amount) {
        int n = openRepo != null ? (files == null ? 0 : files.size()) : (repos == null ? 0 : repos.size());
        scroll = Math.max(0, Math.min(Math.max(0, n - rows), scroll - (int) Math.signum(amount)));
        return true;
    }

    public boolean key(int key, int mods) {
        return field.key(key, mods);
    }

    public boolean chr(int cp) {
        return field.chr(cp);
    }

    public boolean typing() {
        return field.focused;
    }
}
