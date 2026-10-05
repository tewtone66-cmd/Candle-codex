package dev.candle.codex.mixin;

import dev.candle.codex.cosmetics.CosmeticLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Codex hat / wings layer to the player renderer once, when the renderer is created. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void candle$layer(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) (Object) this).candle$addLayer(new CosmeticLayer((RenderLayerParent) (Object) this));
    }
}
