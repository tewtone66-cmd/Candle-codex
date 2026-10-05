package dev.candle.codex.mixin;

import dev.candle.codex.perf.FpsBoost;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS boost / visibility: hides the first-person fire overlay. Static handler so it works for static or instance targets. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void candle$noFire(CallbackInfo ci) {
        if (FpsBoost.noFire) ci.cancel();
    }
}
