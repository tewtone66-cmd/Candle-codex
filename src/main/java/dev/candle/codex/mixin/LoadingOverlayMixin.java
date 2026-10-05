package dev.candle.codex.mixin;

import dev.candle.codex.CodexClient;
import dev.candle.codex.theme.ThemeManager;
import dev.candle.codex.ui.CodexSplash;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/**
 * Codex start-up screen. Injectors are optional (defaultRequire 0): if one does not match, the vanilla
 * Mojang screen just keeps working. If our drawing ever throws, CodexSplash.failed switches everything off.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    private float currentProgress;

    /** Background colour (vanilla red or black) becomes the theme background. */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int candle$background(IntSupplier original) {
        if (CodexSplash.failed) return original.getAsInt();
        try {
            return 0xFF000000 | ThemeManager.current().cBg;
        } catch (Throwable e) {
            fail(e);
            return original.getAsInt();
        }
    }

    /** Both halves of the Mojang logo are drawn with one blit call each; make them invisible and remember the alpha. */
    @ModifyArg(method = "render", index = 12,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private int candle$hideMojangLogo(int color) {
        if (CodexSplash.failed) return color;
        CodexSplash.logoAlpha = ((color >>> 24) & 255) / 255f;
        CodexSplash.seen = true;
        return 0;
    }

    /** Replaces the white vanilla progress bar. */
    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
    private void candle$progress(GuiGraphics graphics, int x0, int y0, int x1, int y1, float fade, CallbackInfo ci) {
        if (CodexSplash.failed || !CodexSplash.seen) return;
        try {
            CodexSplash.bar(graphics, x0, y0, x1, y1, this.currentProgress, fade);
            ci.cancel();
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Codex logo drawn on top, after the background and the (now invisible) vanilla logo. */
    @Inject(method = "render", at = @At("TAIL"))
    private void candle$logo(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (CodexSplash.failed || !CodexSplash.seen) return;
        CodexSplash.seen = false;
        try {
            CodexSplash.logo(graphics, graphics.guiWidth(), graphics.guiHeight(), CodexSplash.logoAlpha);
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void fail(Throwable e) {
        CodexSplash.failed = true;
        CodexClient.LOG.warn("Codex splash disabled", e);
    }
}
