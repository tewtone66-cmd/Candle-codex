package dev.candle.codex.mixin;

import dev.candle.codex.cosmetics.NameTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla never shows the name tag of the player you are controlling. In third person this makes the game's own
 * name tag renderer treat your player like any other player, so depth, zoom and background are exactly vanilla.
 * If a game update changes this method the injection is skipped (defaultRequire is 0) and only the tag is missing.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void candle$ownName(LivingEntity entity, double distSq, CallbackInfoReturnable<Boolean> cir) {
        if (NameTag.wantsOwnTag(entity)) cir.setReturnValue(true);
    }
}
