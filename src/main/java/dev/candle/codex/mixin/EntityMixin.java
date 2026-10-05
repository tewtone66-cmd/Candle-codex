package dev.candle.codex.mixin;

import dev.candle.codex.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: while active, mouse movement rotates the camera instead of the player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void candle$turn(double yRot, double xRot, CallbackInfo ci) {
        if (Modules.Freelook.isActive() && (Object) this == Minecraft.getInstance().player) {
            Modules.Freelook.turn(yRot, xRot);
            ci.cancel();
        }
    }
}
