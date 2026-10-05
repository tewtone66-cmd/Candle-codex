package dev.candle.codex;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CodexClient implements ClientModInitializer {
    public static final String ID = "candlecodex";
    public static KeyMapping menuKey;
    @Override public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.candlecodex.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { while (menuKey.consumeClick()) if (mc.screen == null) mc.setScreen(new CodexScreen()); });
    }
}
