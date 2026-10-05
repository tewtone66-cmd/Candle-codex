package dev.candle.codex.net;

import dev.candle.codex.CodexClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Selects and loads a shader pack in Iris without a compile-time dependency (Iris is optional).
 * Everything is reflective and guarded; on any failure the caller shows a manual hint instead.
 */
public final class IrisBridge {
    private IrisBridge() {}

    public static boolean present() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    /** @return true if the pack was selected and Iris reloaded. Must run on the client thread. */
    public static boolean enable(String packFileName) {
        if (!present()) return false;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object cfg = iris.getMethod("getIrisConfig").invoke(null);
            Class<?> cfgClass = cfg.getClass();
            cfgClass.getMethod("setShaderPackName", String.class).invoke(cfg, packFileName);
            try {
                cfgClass.getMethod("setShadersEnabled", boolean.class).invoke(cfg, true);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                cfgClass.getMethod("save").invoke(cfg);
            } catch (NoSuchMethodException ignored) {
            }
            Method reload = iris.getMethod("reload");
            reload.invoke(null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            CodexClient.LOG.warn("Could not switch Iris shader pack automatically", e);
            return false;
        }
    }
}
