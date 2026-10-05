package dev.candle.codex.ui.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.candle.codex.CodexClient;
import dev.candle.codex.net.GameVersion;
import dev.candle.codex.net.Installer;
import dev.candle.codex.net.Modrinth;
import dev.candle.codex.theme.Theme;
import dev.candle.codex.ui.Anim;
import dev.candle.codex.ui.Draw;
import dev.candle.codex.ui.Img;
import dev.candle.codex.ui.ScrollBar;
import dev.candle.codex.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full page for one Modrinth project, like the project page on the website: icon, author and team,
 * gallery pictures with captions, the long description, links and details. Drawn inside the Store page area.
 */
public final class ProjectView {
    public interface Host {
        boolean busy();

        /** Download percent of the running install (0..100), or -1 when there is none / it is not known yet. */
        int progress();

        boolean installed(String id);

        void install(String id, Installer.Kind kind);
    }

    private record Pic(String url, String title, String desc) {}

    private record Line(String text, boolean heading) {}

    private final Host host;
    private volatile Modrinth.Hit hit;
    private Installer.Kind kind;
    private volatile JsonObject project;
    private volatile JsonArray members;
    private volatile String error = "";

    private List<Pic> pics = new ArrayList<>();
    private boolean picsBuilt;
    private int pic;
    private List<Line> body = new ArrayList<>();
    private int bodyW = -1;
    private int scroll, maxScroll;
    private final ScrollBar bar = new ScrollBar();
    private long copiedUntil;
    private String copiedWhat = "";

    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    private final List<Boolean> fixedBar = new ArrayList<>();
    private long openedAt;
    private int viewTop;

    public ProjectView(Host host) {
        this.host = host;
    }

    public boolean isOpen() {
        return hit != null;
    }

    public void close() {
        hit = null;
        project = null;
        members = null;
        bar.hide();
    }

    public void open(Modrinth.Hit h, Installer.Kind k) {
        openedAt = System.currentTimeMillis();
        hit = h;
        kind = k;
        project = null;
        members = null;
        error = "";
        pics = new ArrayList<>();
        picsBuilt = false;
        pic = 0;
        body = new ArrayList<>();
        bodyW = -1;
        scroll = 0;
        bar.hide();
        CodexClient.IO.execute(() -> {
            try {
                JsonObject p = Modrinth.project(h.id);
                if (hit == h) project = p;
            } catch (RuntimeException e) {
                if (hit == h) error = StorePage.nice(e.getMessage() == null ? "Could not load project" : e.getMessage());
            }
            try {
                JsonArray m = Modrinth.members(h.id);
                if (hit == h) members = m;
            } catch (RuntimeException e) {
                if (hit == h) members = new JsonArray();
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private static String s(JsonObject o, String k) {
        return o == null ? null : Modrinth.str(o, k);
    }

    private static String cap(String v) {
        if (v == null || v.isEmpty()) return "";
        String x = v.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }

    private static String count(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
        return Long.toString(n);
    }

    private static String date(String iso) {
        return iso == null || iso.length() < 10 ? "-" : iso.substring(0, 10);
    }

    private void buildPics(JsonObject p) {
        picsBuilt = true;
        List<Pic> featured = new ArrayList<>(), rest = new ArrayList<>();
        JsonElement ge = p.get("gallery");
        if (ge != null && ge.isJsonArray()) {
            for (JsonElement e : ge.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String url = s(o, "url");
                if (url == null) continue;
                Pic pc = new Pic(url, s(o, "title"), s(o, "description"));
                boolean f = o.has("featured") && !o.get("featured").isJsonNull() && o.get("featured").getAsBoolean();
                (f ? featured : rest).add(pc);
            }
        }
        pics = new ArrayList<>(featured);
        pics.addAll(rest);
    }

    /** Word wrap by pixel width. */
    private static void wrap(String text, int maxW, boolean heading, List<Line> out, int cap) {
        Font f = Minecraft.getInstance().font;
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String tryLine = cur.length() == 0 ? word : cur + " " + word;
            if (f.width(tryLine) <= maxW || cur.length() == 0) {
                cur = new StringBuilder(tryLine);
                while (f.width(cur.toString()) > maxW && cur.length() > 1) {
                    int cut = Math.max(1, f.plainSubstrByWidth(cur.toString(), maxW).length());
                    out.add(new Line(cur.substring(0, cut), heading));
                    cur = new StringBuilder(cur.substring(cut));
                }
            } else {
                out.add(new Line(cur.toString(), heading));
                cur = new StringBuilder(word);
            }
            if (out.size() >= cap) return;
        }
        if (cur.length() > 0) out.add(new Line(cur.toString(), heading));
    }

    private static List<Line> convert(String md, int maxW) {
        List<Line> out = new ArrayList<>();
        if (md == null) return out;
        String t = md.replace("\r", "");
        t = t.replaceAll("(?s)<!--.*?-->", "");
        t = t.replaceAll("(?s)```.*?```", "");
        t = t.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");
        t = t.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        t = t.replaceAll("(?i)<br\\s*/?>", "\n");
        t = t.replaceAll("<[^>]+>", "");
        t = t.replace("**", "").replace("__", "").replace("~~", "").replace("`", "");
        boolean blank = true;
        for (String raw : t.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                if (!blank) out.add(new Line("", false));
                blank = true;
                continue;
            }
            if (line.matches("^[-=_*]{3,}$") || line.startsWith("|")) continue;
            boolean heading = false;
            if (line.startsWith("#")) {
                line = line.replaceFirst("^#+\\s*", "");
                heading = true;
            } else if (line.matches("^[-*+]\\s+.*")) {
                line = "- " + line.substring(2).strip();
            } else if (line.startsWith(">")) {
                line = line.replaceFirst("^>+\\s*", "");
            }
            line = line.replace("*", "").strip();
            if (line.isEmpty()) continue;
            blank = false;
            wrap(line, maxW, heading, out, 320);
            if (out.size() >= 320) break;
        }
        return out;
    }

    private void hot(int x, int y, int w, int h, Runnable r) {
        hot(x, y, w, h, r, false);
    }

    private void hot(int x, int y, int w, int h, Runnable r, boolean fixed) {
        rects.add(new int[]{x, y, w, h});
        acts.add(r);
        fixedBar.add(fixed);
    }

    private void copy(String what, String url) {
        Minecraft.getInstance().keyboardHandler.setClipboard(url);
        copiedWhat = what;
        copiedUntil = System.currentTimeMillis() + 1800;
    }

    // ------------------------------------------------------------------ render

    public void render(GuiGraphics g, Theme t, int mx, int my, int x, int y, int w, int h) {
        Modrinth.Hit hh = hit;
        if (hh == null) return;
        rects.clear();
        acts.clear();
        fixedBar.clear();
        float oe = Anim.ease((System.currentTimeMillis() - openedAt) / 280f);
        float savedMul = Draw.mul;
        Draw.mul = savedMul * oe;
        g.pose().pushMatrix();
        g.pose().translate(0f, (1f - oe) * 14f);
        JsonObject p = project;
        if (p != null && !picsBuilt) buildPics(p);

        // fixed top bar
        Ui.button(g, t, "< Back", x, y, 56, 20, Ui.in(mx, my, x, y, 56, 20), false);
        hot(x, y, 56, 20, this::close, true);
        Draw.text(g, Draw.fit(hh.title, w - 260), x + 66, y + 6, t.cAccent2);
        int ibx = x + w - 90;
        if (host.installed(hh.id)) {
            Draw.rr(g, ibx, y, 90, 20, 5, (0x30 << 24) | t.cAccent);
            Draw.textC(g, "Installed", ibx + 45, y + 6, t.cAccent2);
        } else {
            boolean can = !host.busy();
            int pg = host.progress();
            String lab = can ? "Install" : (pg >= 0 ? pg + "%" : "...");
            Ui.button(g, t, lab, ibx, y, 90, 20, can && Ui.in(mx, my, ibx, y, 90, 20), can);
            if (can) hot(ibx, y, 90, 20, () -> host.install(hh.id, kind), true);
        }
        if (System.currentTimeMillis() < copiedUntil) {
            Draw.textR(g, "Copied " + copiedWhat, ibx - 8, y + 6, t.cAccent);
        }

        viewTop = y + 26;
        int vh = h - 26;
        g.enableScissor(x, viewTop, x + w, y + h);
        int base = viewTop - scroll;
        int cy = base;

        // header card
        Ui.card(g, t, x, cy, w, 70, false, 0f);
        Img.coverOr(g, t, hh.iconUrl, x + 10, cy + 11, 48, 48, 6, hh.title);
        int tx = x + 68;
        Draw.textShadow(g, Draw.fit(hh.title, w - 80), tx, cy + 9, t.cAccent2);
        Draw.text(g, "by " + (hh.author == null ? "?" : hh.author), tx, cy + 21, t.cDim);
        Draw.text(g, Draw.fit(hh.description == null ? "" : hh.description, w - 80), tx, cy + 34, t.cText);
        long dl = p != null && p.has("downloads") ? p.get("downloads").getAsLong() : hh.downloads;
        long fo = p != null && p.has("followers") ? p.get("followers").getAsLong() : hh.follows;
        String upd = p != null ? date(s(p, "updated")) : date(hh.updated);
        Draw.text(g, count(dl) + " downloads   " + count(fo) + " followers   updated " + upd, tx, cy + 50, t.cDim);
        cy += 76;

        // category chips
        List<String> cats = hh.categories;
        if (p != null && p.has("categories") && p.get("categories").isJsonArray()) {
            List<String> pc = new ArrayList<>();
            Modrinth.strings(p.get("categories"), pc);
            if (!pc.isEmpty()) cats = pc;
        }
        int chx = x;
        for (int i = 0; i < cats.size() && i < 7; i++) {
            String lab = cap(cats.get(i));
            int cw = Draw.font().width(lab) + 16;
            if (chx + cw > x + w) break;
            Ui.chip(g, t, lab, chx, cy, cw, 16, true, false);
            chx += cw + 4;
        }
        cy += 22;

        if (!error.isEmpty()) {
            Draw.text(g, Draw.fit(error, w), x, cy + 4, 0xE06060);
            cy += 20;
        }

        int lw = w * 60 / 100, gap = 10, rw = w - lw - gap, rx = x + lw + gap;
        int ly = cy, ry = cy;

        // ---- left column: gallery
        if (!pics.isEmpty()) {
            int gh = Math.min(150, lw * 9 / 16);
            Draw.rr(g, x, ly, lw, gh, t.radius, 0xFF000000 | Theme.mix(t.cBg, 0x000000, 0.5f));
            Pic pc = pics.get(Math.max(0, Math.min(pic, pics.size() - 1)));
            if (!Img.fit(g, pc.url(), x + 2, ly + 2, lw - 4, gh - 4)) {
                Draw.textC(g, Img.failed(pc.url()) ? "Preview not available" : "Loading picture...", x + lw / 2, ly + gh / 2 - 4, t.cDim);
            }
            Draw.ring(g, x, ly, lw, gh, t.radius, t.aBorder);
            if (pics.size() > 1) {
                int ay = ly + gh / 2 - 11;
                Ui.button(g, t, "<", x + 6, ay, 22, 22, Ui.in(mx, my, x + 6, ay, 22, 22), false);
                Ui.button(g, t, ">", x + lw - 28, ay, 22, 22, Ui.in(mx, my, x + lw - 28, ay, 22, 22), false);
                final int n = pics.size();
                hot(x + 6, ay, 22, 22, () -> pic = (pic + n - 1) % n);
                hot(x + lw - 28, ay, 22, 22, () -> pic = (pic + 1) % n);
                Draw.rr(g, x + lw - 40, ly + gh - 16, 34, 12, 6, 0x99000000);
                Draw.textC(g, (pic + 1) + "/" + n, x + lw - 23, ly + gh - 14, 0xFFFFFF);
            }
            ly += gh + 6;
            if (pc.title() != null && !pc.title().isBlank()) {
                Draw.text(g, Draw.fit(pc.title(), lw), x + 2, ly, t.cAccent2);
                ly += 11;
            }
            if (pc.desc() != null && !pc.desc().isBlank()) {
                List<Line> dl2 = new ArrayList<>();
                wrap(pc.desc().replace("\n", " "), lw - 4, false, dl2, 3);
                for (Line l : dl2) {
                    Draw.text(g, l.text(), x + 2, ly, t.cDim);
                    ly += 10;
                }
            }
            ly += 8;
        } else if (p != null) {
            Draw.rr(g, x, ly, lw, 30, t.radius, t.aPanel);
            Draw.text(g, "This project has no pictures.", x + 10, ly + 11, t.cDim);
            ly += 38;
        } else if (error.isEmpty()) {
            Draw.rr(g, x, ly, lw, 30, t.radius, t.aPanel);
            Draw.text(g, "Loading project...", x + 10, ly + 11, t.cDim);
            ly += 38;
        }

        // ---- left column: description
        if (p != null) {
            if (bodyW != lw - 4) {
                bodyW = lw - 4;
                body = convert(s(p, "body"), bodyW);
            }
            Draw.textShadow(g, "About", x + 2, ly, t.cAccent2);
            ly += 14;
            if (body.isEmpty()) {
                Draw.text(g, "No description.", x + 2, ly, t.cDim);
                ly += 12;
            }
            for (Line l : body) {
                if (l.text().isEmpty()) {
                    ly += 5;
                    continue;
                }
                if (ly > viewTop - 12 && ly < y + h) Draw.text(g, l.text(), x + 2, ly, l.heading() ? t.cAccent2 : t.cText);
                ly += l.heading() ? 13 : 10;
            }
            ly += 6;
        }

        // ---- right column: team
        JsonArray mem = members;
        int shown = mem == null ? 1 : Math.max(1, Math.min(mem.size(), 8));
        int teamH = 26 + shown * 20 + 4;
        Ui.card(g, t, rx, ry, rw, teamH, false, 0f);
        Draw.textShadow(g, "Team", rx + 10, ry + 8, t.cAccent2);
        if (mem == null) {
            Draw.text(g, "Loading...", rx + 10, ry + 30, t.cDim);
        } else if (mem.isEmpty()) {
            Draw.text(g, "by " + (hh.author == null ? "?" : hh.author), rx + 10, ry + 30, t.cText);
        } else {
            for (int i = 0; i < shown; i++) {
                JsonObject m = mem.get(i).getAsJsonObject();
                JsonObject u = m.has("user") && m.get("user").isJsonObject() ? m.getAsJsonObject("user") : null;
                String name = s(u, "username");
                String role = s(m, "role");
                int my2 = ry + 24 + i * 20;
                String av = s(u, "avatar_url");
                Img.coverOr(g, t, av, rx + 10, my2, 16, 16, 3, name);
                Draw.text(g, Draw.fit(name == null ? "?" : name, rw - 110), rx + 32, my2 + 1, t.cText);
                if (role != null) Draw.textR(g, Draw.fit(role, 80), rx + rw - 10, my2 + 1, t.cDim);
            }
        }
        ry += teamH + 8;

        // ---- right column: links
        if (p != null) {
            List<String[]> links = new ArrayList<>();
            String slug = s(p, "slug");
            String ptype = s(p, "project_type");
            if (slug != null && ptype != null) links.add(new String[]{"Modrinth", "https://modrinth.com/" + ptype + "/" + slug});
            if (s(p, "source_url") != null) links.add(new String[]{"Source", s(p, "source_url")});
            if (s(p, "issues_url") != null) links.add(new String[]{"Issues", s(p, "issues_url")});
            if (s(p, "wiki_url") != null) links.add(new String[]{"Wiki", s(p, "wiki_url")});
            if (s(p, "discord_url") != null) links.add(new String[]{"Discord", s(p, "discord_url")});
            if (!links.isEmpty()) {
                int lh = 26 + links.size() * 20 + 4;
                Ui.card(g, t, rx, ry, rw, lh, false, 0f);
                Draw.textShadow(g, "Links", rx + 10, ry + 8, t.cAccent2);
                for (int i = 0; i < links.size(); i++) {
                    String[] l = links.get(i);
                    int row = ry + 24 + i * 20;
                    Draw.text(g, l[0], rx + 10, row + 4, t.cText);
                    int bx = rx + rw - 54;
                    boolean hv = Ui.in(mx, my, bx, row, 44, 16);
                    Ui.button(g, t, "Copy", bx, row, 44, 16, hv, false);
                    hot(bx, row, 44, 16, () -> copy(l[0] + " link", l[1]));
                }
                ry += lh + 8;
            }

            // ---- right column: details
            List<String[]> det = new ArrayList<>();
            JsonObject lic = p.has("license") && p.get("license").isJsonObject() ? p.getAsJsonObject("license") : null;
            if (lic != null && s(lic, "name") != null) det.add(new String[]{"License", s(lic, "name")});
            String cs = s(p, "client_side"), ss = s(p, "server_side");
            if (cs != null || ss != null) det.add(new String[]{"Client", cap(cs)});
            if (ss != null) det.add(new String[]{"Server", cap(ss)});
            List<String> ld = new ArrayList<>();
            Modrinth.strings(p.get("loaders"), ld);
            if (!ld.isEmpty()) det.add(new String[]{"Loaders", String.join(", ", ld.stream().map(ProjectView::cap).toList())});
            List<String> gv = new ArrayList<>();
            Modrinth.strings(p.get("game_versions"), gv);
            if (!gv.isEmpty()) {
                String mine = GameVersion.get();
                det.add(new String[]{"Latest MC", gv.get(gv.size() - 1)});
                det.add(new String[]{"Your MC " + mine, gv.contains(mine) ? "Supported" : "Not listed"});
            }
            det.add(new String[]{"Published", date(s(p, "published"))});
            det.add(new String[]{"Updated", date(s(p, "updated"))});
            int dh = 26 + det.size() * 14 + 6;
            Ui.card(g, t, rx, ry, rw, dh, false, 0f);
            Draw.textShadow(g, "Details", rx + 10, ry + 8, t.cAccent2);
            for (int i = 0; i < det.size(); i++) {
                int row = ry + 24 + i * 14;
                Draw.text(g, det.get(i)[0], rx + 10, row, t.cDim);
                Draw.textR(g, Draw.fit(det.get(i)[1], rw - 100), rx + rw - 10, row, t.cText);
            }
            ry += dh + 8;
        }

        g.disableScissor();
        int bottom = Math.max(ly, ry);
        maxScroll = Math.max(0, bottom - base - vh + 8);
        if (scroll > maxScroll) scroll = maxScroll;
        // draggable scrollbar, same look as the rest of the menu
        bar.render(g, t, x + w + 3, viewTop + 2, vh - 4, scroll, maxScroll, mx, my);
        g.pose().popMatrix();
        Draw.mul = savedMul;
    }

    // ------------------------------------------------------------------ input

    public boolean click(double mx, double my) {
        if (hit == null) return false;
        if (bar.click(mx, my)) {
            scroll = bar.scrollAt(my);
            return true;
        }
        boolean inView = my >= viewTop;
        for (int i = rects.size() - 1; i >= 0; i--) {
            int[] r = rects.get(i);
            if (!Ui.in(mx, my, r[0], r[1], r[2], r[3])) continue;
            // everything except the fixed top bar lives inside the scrolled viewport
            if (fixedBar.get(i) || inView) {
                acts.get(i).run();
                return true;
            }
        }
        return true; // the overlay swallows clicks so nothing behind it reacts
    }

    /** Mouse drag on the scrollbar. */
    public void drag(double my) {
        if (bar.dragging()) scroll = Math.max(0, Math.min(maxScroll, bar.scrollAt(my)));
    }

    public void release() {
        bar.release();
    }

    public boolean scroll(double amount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(amount * 28)));
        return true;
    }
}
