package dev.candle.codex.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.candle.codex.CodexClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

/**
 * Zoom with a smooth, time-based ease (critically damped spring in log space), so the speed no longer depends on
 * the tick rate and the zoom feels even. The speed can be changed in the module settings.
 * While the Zoom key is held the mouse wheel changes the zoom level (can be switched off in the settings).
 */
public final class ZoomModule extends Module {
    private static ZoomModule instance;
    private static boolean enabledFlag;
    private static float current = 1f, target = 1f, vel;
    private static long lastNs;
    private static boolean scrollHooked;
    private static GLFWScrollCallback prevScroll;

    private final KeyMapping key = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.candle.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CodexClient.CATEGORY));
    private final Setting level = add(Setting.num("level", "Zoom level", 4.0, 1.5, 10.0, 0.5));
    private final Setting smooth = add(Setting.bool("smooth", "Smooth zoom", true));
    private final Setting speed = add(Setting.num("speed", "Zoom speed", 6.0, 2.0, 20.0, 1.0));
    private final Setting scrollZoom = add(Setting.bool("scrollZoom", "Scroll changes zoom", true));

    public ZoomModule() {
        super("zoom", "Zoom", "Hold the Zoom key to zoom in. Scroll to adjust.", "Visual");
        instance = this;
    }

    /** FOV multiplier read by GameRendererMixin once per call. 1.0 when idle. Advances the animation. */
    public static float factor() {
        if (!enabledFlag) return 1f;
        advance();
        return current;
    }

    /** Current multiplier without advancing the animation (used by the name tag). */
    public static float peek() {
        return enabledFlag ? current : 1f;
    }

    private static void advance() {
        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (instance == null || !instance.smooth.bool) {
            current = target;
            vel = 0f;
            return;
        }
        if (dt <= 0f) return;
        float smoothTime = 2.0f / (float) instance.speed.num;
        float omega = 2f / smoothTime;
        float x = omega * dt;
        float ex = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
        float cur = (float) Math.log(current);
        float tgt = (float) Math.log(target);
        float change = cur - tgt;
        float temp = (vel + omega * change) * dt;
        vel = (vel - omega * temp) * ex;
        float out = tgt + (change + temp) * ex;
        current = (float) Math.exp(out);
        if (Math.abs(current - target) < 0.002f && Math.abs(vel) < 0.01f) {
            current = target;
            vel = 0f;
        }
    }

    /** Chains our scroll handler in front of the game's one. Done once; it passes everything through unless zooming. */
    private static void hookScroll(Minecraft mc) {
        if (scrollHooked) return;
        scrollHooked = true;
        long win = mc.getWindow().handle();
        prevScroll = GLFW.glfwSetScrollCallback(win, ZoomModule::onScroll);
    }

    private static void onScroll(long window, double xOffset, double yOffset) {
        ZoomModule z = instance;
        if (z != null && enabledFlag && z.scrollZoom.bool && yOffset != 0.0
                && z.key.isDown() && Minecraft.getInstance().screen == null) {
            double v = z.level.num + Math.signum(yOffset) * z.level.step;
            z.level.num = Math.max(z.level.min, Math.min(z.level.max, v));
            z.touch();
            return;
        }
        if (prevScroll != null) prevScroll.invoke(window, xOffset, yOffset);
    }

    @Override
    public void onEnable() {
        enabledFlag = true;
        current = 1f;
        target = 1f;
        vel = 0f;
        lastNs = 0L;
    }

    @Override
    public void onDisable() {
        enabledFlag = false;
        current = 1f;
        target = 1f;
        vel = 0f;
        lastNs = 0L;
    }

    @Override
    public void onTick(Minecraft mc) {
        enabledFlag = true;
        hookScroll(mc);
        target = key.isDown() && mc.screen == null ? (float) (1.0 / level.num) : 1f;
    }
}
