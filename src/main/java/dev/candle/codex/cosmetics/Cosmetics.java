package dev.candle.codex.cosmetics;

import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Catalogue of cosmetics. Capes, hats, glasses and wings are rendered on your own player. */
public final class Cosmetics {
    public record Entry(String id, String name, int c1, int c2) {}

    public static final List<Entry> CAPES = List.of(
            new Entry("none", "No cape", 0x555555, 0x333333),
            new Entry("flame", "Flame", 0xFF9A2E, 0x8C2814),
            new Entry("ocean", "Ocean", 0x3CAAFF, 0x0F328C),
            new Entry("royal", "Royal", 0x8C46DC, 0x321464),
            new Entry("mint", "Mint", 0x5AE1A0, 0x146E50),
            new Entry("shadow", "Shadow", 0x3C3C4B, 0x0F0F16),
            new Entry("sakura", "Sakura", 0xFFAACD, 0xBE4678),
            new Entry("gold", "Gold", 0xFFD75A, 0xAA7314),
            new Entry("candle", "Codex", 0xFF9A2E, 0x161A34),
            new Entry("iran", "Lion & Sun", 0x239F40, 0xDA0000),
            new Entry("galaxy", "Galaxy", 0x5C28AA, 0x0A061C),
            // animated capes (frames are drawn by AnimCapes / CapeArt)
            new Entry("candleflame", "Codex Flame", 0xFF9A2E, 0x161A34),
            new Entry("ad", "Codex Ad", 0xFF9A2E, 0x161A34),
            new Entry("aurora", "Aurora", 0x3DFFA0, 0x5A28AA),
            new Entry("inferno", "Inferno", 0xFFB000, 0x8C0A00),
            new Entry("matrix", "Matrix", 0x00FF50, 0x001A08),
            new Entry("hanok", "Hanok Night", 0xC0392B, 0x0B1030),
            new Entry("cherry", "Cherry Blossom", 0xFFB3CF, 0xA9C7F0),
            new Entry("blossomgate", "Blossom Gate", 0xF58FB5, 0x5C8EDC),
            new Entry("moongate", "Moon Gate", 0x8F4A86, 0x0A0E2E),
            Glitchy.CAPE);

    /** c1 = main colour, c2 = accent colour. The menu picture (HeadArt) and the 3D model (CosmeticLayer) both use them. */
    public static final List<Entry> HATS = List.of(
            new Entry("none", "No hat", 0x555555, 0x333333),
            new Entry("crown", "Crown", 0xFFD166, 0xC98A1A),
            new Entry("tophat", "Top hat", 0x2B2B33, 0x888899),
            new Entry("halo", "Halo", 0xFFF2A8, 0xFFC93C),
            new Entry("party", "Party hat", 0xFF5C8A, 0x5CC8FF),
            new Entry("cat", "Cat ears", 0xE8E8F0, 0xFF9EB5),
            new Entry("santa", "Santa", 0xE03A3A, 0xFFFFFF),
            new Entry("wizard", "Wizard", 0x5B2BA0, 0xFFD166),
            new Entry("cap", "Cap", 0x2F6FDB, 0xFFFFFF),
            Glitchy.HAT);

    /** c1 = frame colour, c2 = lens colour. */
    public static final List<Entry> GLASSES = List.of(
            new Entry("none", "No glasses", 0x555555, 0x333333),
            new Entry("black", "Black Glass", 0x07070A, 0x14141C),
            new Entry("classic", "Classic", 0x1B1B22, 0xFFFFFF),
            new Entry("round", "Round Gold", 0xFFD166, 0xFFF2A8),
            new Entry("aviator", "Aviator", 0xE0B040, 0x3B2A1A),
            new Entry("neon", "Neon Visor", 0x00F0FF, 0xFF2DAA),
            new Entry("heart", "Hearts", 0xFF4F8B, 0xFFB3CE),
            Glitchy.GLASSES);

    /**
     * Wings. c1 / c2 mean different things per wing (the menu picture and the 3D model share them):
     * dragon, bat = membrane / bones; angel = feather white / shadow tint; phoenix = red / yellow ends of the fire gradient;
     * crystal = first / last shard colour; fairy = upper / lower wing; flame = flame / tip.
     */
    public static final List<Entry> WINGS = List.of(
            new Entry("none", "No wings", 0x555555, 0x333333),
            new Entry("dragon", "Dragon", 0x7A1FA2, 0x2A0A3C),
            new Entry("angel", "Angel", 0xFFFFFF, 0xCFE8FF),
            new Entry("phoenix", "Phoenix", 0xFF3C1E, 0xFFD23C),
            new Entry("bat", "Bat", 0x3E3050, 0x17121F),
            new Entry("crystal", "Crystal", 0x7AF0FF, 0xC890FF),
            new Entry("fairy", "Fairy", 0x9CF2FF, 0xFF9CE6),
            new Entry("flame", "Flame", 0xFF9A2E, 0xFFE08A),
            Glitchy.WINGS);

    private static final Map<String, ClientAsset.ResourceTexture> SKIN_CAPES = new HashMap<>();
    /** Identifiers are cached so drawing a cape preview does not create new objects every frame. */
    private static final Map<String, Identifier> FILES = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> ASSETS = new ConcurrentHashMap<>();
    /** Last animated frame key per cape and until when it stays valid (frames last 80 ms or more). */
    private static final Map<String, Live> LIVE = new HashMap<>();
    private static final long LIVE_MS = 25L;

    private record Live(String key, long until) {}

    private Cosmetics() {}

    /** Cape texture object for the player skin, cached per cape (or per animation frame). */
    public static ClientAsset.ResourceTexture skinCape(String id) {
        return SKIN_CAPES.computeIfAbsent(id, k -> new ClientAsset.ResourceTexture(capeAsset(k)));
    }

    public static Entry find(List<Entry> list, String id) {
        for (Entry e : list) if (e.id().equals(id)) return e;
        return list.get(0);
    }

    /** The cape picked in the menu, or null when none. */
    public static Entry selectedCape() {
        Entry e = find(CAPES, Config.data.cosmetics.cape);
        return "none".equals(e.id()) ? null : e;
    }

    public static boolean animated(String id) {
        return AnimCapes.isAnimated(id);
    }

    /**
     * Texture key to draw right now: the cape itself, or the current frame for animated capes. Render thread only.
     * The frame lookup is repeated at most every 25 ms per cape (the shortest frame lasts 80 ms).
     */
    public static String liveKey(String id) {
        if (!AnimCapes.isAnimated(id)) return id;
        long now = System.currentTimeMillis();
        Live l = LIVE.get(id);
        if (l != null && now < l.until()) return l.key();
        String key = AnimCapes.frameKey(id);
        LIVE.put(id, new Live(key, now + LIVE_MS));
        return key;
    }

    /** Capes whose big texture has a separate, pre-scaled 40 x 64 picture for the menu. None now: Lion & Sun is animated. */
    public static boolean hasPreview(String id) {
        return false;
    }

    /** Texture file used for GUI previews. */
    public static Identifier capeFile(String id) {
        return FILES.computeIfAbsent(id, k -> Identifier.fromNamespaceAndPath(CodexClient.ID, "textures/cape/" + k + ".png"));
    }

    /** Asset id used by the player skin (the game adds textures/ and .png itself). */
    public static Identifier capeAsset(String id) {
        return ASSETS.computeIfAbsent(id, k -> Identifier.fromNamespaceAndPath(CodexClient.ID, "cape/" + k));
    }
}
