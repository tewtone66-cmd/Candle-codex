package dev.candle.codex.mixin;

import dev.candle.codex.module.ZoomModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: scales the field of view. A single float read when Zoom is off. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void candle$zoom(CallbackInfoReturnable<Float> cir) {
        float f = ZoomModule.factor();
        if (f != 1f) cir.setReturnValue(cir.getReturnValue() * f);
    }
}
