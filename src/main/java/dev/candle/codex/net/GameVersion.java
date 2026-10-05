package dev.candle.codex.net;

import net.fabricmc.loader.api.FabricLoader;

/** The running Minecraft version, read from Fabric metadata (never hard-coded). */
public final class GameVersion {
    private static String cached;

    private GameVersion() {}

    public static String get() {
        if (cached == null) {
            cached = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        }
        return cached;
    }
}
