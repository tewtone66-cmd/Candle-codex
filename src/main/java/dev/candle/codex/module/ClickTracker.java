package dev.candle.codex.module;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Counts mouse clicks per second by polling the button state once per rendered frame (no Mixin needed).
 * Fixed-size ring buffers, no allocation.
 */
public final class ClickTracker {
    private static final int SIZE = 64;
    private static final long[][] RING = new long[2][SIZE];
    private static final int[] HEAD = new int[2];
    private static final boolean[] DOWN = new boolean[2];

    private ClickTracker() {}

    public static void poll() {
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().handle();
        long now = System.currentTimeMillis();
        for (int b = 0; b < 2; b++) {
            boolean down = mc.screen == null && GLFW.glfwGetMouseButton(win, b) == GLFW.GLFW_PRESS;
            if (down && !DOWN[b]) {
                HEAD[b] = (HEAD[b] + 1) % SIZE;
                RING[b][HEAD[b]] = now;
            }
            DOWN[b] = down;
        }
    }

    public static boolean isDown(int button) {
        return DOWN[button];
    }

    public static int cps(int button) {
        long cutoff = System.currentTimeMillis() - 1000;
        int n = 0;
        for (int i = 0; i < SIZE; i++) if (RING[button][i] > cutoff) n++;
        return n;
    }
}
