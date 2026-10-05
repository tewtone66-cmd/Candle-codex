package dev.candle.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.candle.codex.config.Config;
import dev.candle.codex.config.ProfileManager;
import dev.candle.codex.module.HudRenderer;
import dev.candle.codex.module.ModuleManager;
import dev.candle.codex.perf.FpsBoost;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.MenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CodexClient implements ClientModInitializer {
 public static final String ID="candlecodex";
 public static final Logger LOG=LoggerFactory.getLogger("CandleCodex");
 public static final ExecutorService IO=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"codex-io");t.setDaemon(true);return t;});
 public static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID,"main"));
 public static KeyMapping openMenuKey; private static boolean applied;
 @Override public void onInitializeClient(){
  Config.load(); ThemeManager.init(); ProfileManager.ensureDefaults();
  openMenuKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.candlecodex.menu",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT_SHIFT,CATEGORY));
  ModuleManager.init();
  HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,Identifier.fromNamespaceAndPath(ID,"hud"),HudRenderer::render);
  HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR,HudRenderer::crosshair);
  ClientTickEvents.END_CLIENT_TICK.register(mc->{if(!applied){applied=true;FpsBoost.apply();}while(openMenuKey.consumeClick())if(mc.screen==null)mc.setScreen(new MenuScreen());if(mc.player!=null)ModuleManager.tick(mc);Config.flushIfNeeded();});
  ClientLifecycleEvents.CLIENT_STOPPING.register(mc->{ModuleManager.shutdown();Config.save();});
  LOG.info("Candle Codex loaded");
 }
}
