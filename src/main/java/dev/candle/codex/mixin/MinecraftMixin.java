package dev.candle.codex.mixin;

import dev.candle.codex.config.Config;
import dev.candle.codex.ui.CodexLobbyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replaces the vanilla title screen with the Codex lobby (can be switched off in Settings). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen candle$lobby(Screen screen) {
        if (screen instanceof TitleScreen && Config.data.lobby) return new CodexLobbyScreen();
        return screen;
    }
}
