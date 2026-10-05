package dev.candle.codex.cosmetics;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import dev.candle.codex.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

/**
 * Your own name tag in third person (F5), with the Codex badge.
 * This is the game's real name tag (depth tested, correct while zooming, vanilla background):
 * LivingEntityRendererMixin makes the game show it, and CosmeticLayer calls {@link #decorate} right before the game
 * draws it, which puts the badge glyph (font candle:badge) in front of the name.
 */
public final class NameTag {
    private static final int[] PLATES = {0xFFFFFF, 0x1B1B22, 0xFF9A2E, 0x3CAAFF, 0xFF6FA8, 0x56E39F, 0xB07CFF, 0xFFD166};
    private static final Component[] GLYPHS = new Component[PLATES.length];
    private static Component lastTag;
    private static Component lastSource;
    private static String lastText = "";
    private static int lastGlyph = -1;
    private static boolean lastName;
    /** Colour -> plate index cache, so the nearest-colour search does not run every frame. */
    private static int cachedWant = Integer.MIN_VALUE;
    private static int cachedIndex;

    private NameTag() {}

    /** True when the game should draw the name tag of this entity although it is the local player. */
    public static boolean wantsOwnTag(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e != mc.player || mc.player == null || mc.options.getCameraType().isFirstPerson()) return false;
        return Config.data.ownName || Config.data.badge;
    }

    /** Adds the badge to the name tag the game is about to draw for your own player. */
    public static void decorate(EntityRenderState state) {
        if (state.nameTag == null) return;
        boolean name = Config.data.ownName;
        if (!Config.data.badge) {
            if (!name) state.nameTag = null; // nothing to show (the mixin normally prevents this case)
            return;
        }
        int gi = glyphIndex();
        Component glyph = glyph(gi);
        if (glyph == null) return; // glyph could not be built: keep the plain name
        Component source = state.nameTag;
        // same component object as last frame and same settings: nothing to rebuild (no string work at all)
        if (lastTag != null && source == lastSource && gi == lastGlyph && name == lastName) {
            state.nameTag = lastTag;
            return;
        }
        String text = name ? source.getString() : "";
        if (lastTag == null || gi != lastGlyph || name != lastName || !text.equals(lastText)) {
            MutableComponent c = Component.empty().append(glyph);
            if (name) c.append(Component.literal(" ")).append(source);
            lastTag = c;
            lastGlyph = gi;
            lastName = name;
            lastText = text;
        }
        lastSource = source;
        state.nameTag = lastTag;
    }

    /** Index of the plate colour glyph: the configured colour, or the closest one to the theme accent. */
    private static int glyphIndex() {
        int want = Config.data.badgeColor;
        if (want < 0) want = ThemeManager.current().cAccent;
        want &= 0xFFFFFF;
        if (want == cachedWant) return cachedIndex;
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < PLATES.length; i++) {
            int c = PLATES[i];
            long dr = ((c >> 16) & 255) - ((want >> 16) & 255), dg = ((c >> 8) & 255) - ((want >> 8) & 255), db = (c & 255) - (want & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        cachedWant = want;
        cachedIndex = best;
        return best;
    }

    /** One glyph of the badge font as a text component, parsed once from the normal JSON text format. */
    private static Component glyph(int i) {
        if (GLYPHS[i] == null) {
            String json = "{\"text\":\"" + String.format("\\u%04x", 0xE000 + i) + "\",\"font\":\"" + CodexClient.ID + ":badge\"}";
            try {
                GLYPHS[i] = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElse(null);
            } catch (RuntimeException e) {
                CodexClient.LOG.warn("Codex: badge glyph could not be built", e);
            }
        }
        return GLYPHS[i];
    }
}
