package dev.candle.codex.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import dev.candle.codex.CodexClient;

/** Draws the selected aura on your own player (called from CosmeticLayer). Reuses the box drawing of PetRender. */
final class AuraRender {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(CodexClient.ID, "textures/cosmetic/white.png");
    private static final int FULL = 0xF000F0;

    private AuraRender() {}

    static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, Cosmetics.Entry aura, float time) {
        poseStack.pushPose();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE), (pose, vc) ->
                Auras.build(aura, time, (cx, cy, cz, hx, hy, hz, rgb, glow) ->
                        PetRender.bx(vc, pose, glow ? FULL : light, cx, cy, cz, hx, hy, hz, rgb)));
        poseStack.popPose();
    }
}
