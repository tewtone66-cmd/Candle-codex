package dev.candle.codex.mixin;

import dev.candle.codex.perf.FpsBoost;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

/** FPS boost: drops most explosion particles (crystal PvP spams them). */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void candle$reduceExplosions(ParticleOptions options, double x, double y, double z,
                                         double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        if (!FpsBoost.reduceExplosions) return;
        var type = options.getType();
        if ((type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.POOF
                || type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE)
                && ThreadLocalRandom.current().nextInt(4) != 0) {
            cir.setReturnValue(null);
        }
    }
}
