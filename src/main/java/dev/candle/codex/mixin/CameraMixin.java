package dev.candle.codex.mixin;

import dev.candle.codex.module.Modules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: overrides the camera angles while the key is held. Does nothing when the module is off. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("RETURN"))
    private void candle$freelook(CallbackInfo ci) {
        if (Modules.Freelook.isActive()) setRotation(Modules.Freelook.yaw(), Modules.Freelook.pitch());
    }
}
