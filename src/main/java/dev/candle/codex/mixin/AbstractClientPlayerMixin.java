package dev.candle.codex.mixin;

import dev.candle.codex.cosmetics.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swaps the cape of your own player for the one chosen in the Cosmetics tab. No-op when "No cape" is selected. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void candle$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        if ((Object) this != Minecraft.getInstance().player) return;
        Cosmetics.Entry cape = Cosmetics.selectedCape();
        if (cape == null) return;
        PlayerSkin s = cir.getReturnValue();
        // animated capes return a different texture every few frames
        cir.setReturnValue(new PlayerSkin(s.body(), Cosmetics.skinCape(Cosmetics.liveKey(cape.id())), s.elytra(), s.model(), s.secure()));
    }
}
