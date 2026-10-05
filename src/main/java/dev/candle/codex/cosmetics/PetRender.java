package dev.candle.codex.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.candle.codex.CodexClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws the selected pet on your own player (called from CosmeticLayer, same model space: pixels / 16, +Y down).
 * Shoulder pets are moved onto the shoulder; the butterfly is already built around the head centre.
 * First person never gets here: the game does not render your own body in first person, so nothing is drawn there.
 */
final class PetRender {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(CodexClient.ID, "textures/cosmetic/white.png");
    private static final int FULL = 0xF000F0;

    private PetRender() {}

    static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, Cosmetics.Entry pet, float time) {
        final boolean orbit = Pets.orbits(pet.id());
        poseStack.pushPose();
        if (!orbit) poseStack.translate(6.3f / 16f, -0.3f / 16f, 0.5f / 16f); // on top of the shoulder (arm top is y = 0)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE), (pose, vc) ->
                Pets.build(pet, time, (cx, cy, cz, hx, hy, hz, rgb, glow) ->
                        bx(vc, pose, glow ? FULL : light, cx, cy, cz, hx, hy, hz, rgb)));
        poseStack.popPose();
    }

    /** Box given by centre and half sizes in pixels (1/16 block); up is -Y. Every vertex element is filled. */
    static void bx(VertexConsumer vc, PoseStack.Pose p, int light, float cx, float cy, float cz, float hx, float hy, float hz, int rgb) {
        float x0 = (cx - hx) / 16f, x1 = (cx + hx) / 16f;
        float y0 = (cy - hy) / 16f, y1 = (cy + hy) / 16f;
        float z0 = (cz - hz) / 16f, z1 = (cz + hz) / 16f;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        quad(vc, p, light, r, g, b, 1.00f, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(vc, p, light, r, g, b, 0.55f, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(vc, p, light, r, g, b, 0.85f, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(vc, p, light, r, g, b, 0.85f, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(vc, p, light, r, g, b, 0.70f, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(vc, p, light, r, g, b, 0.70f, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, int light, int r, int g, int b, float shade,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        int rr = Math.round(r * shade), gg = Math.round(g * shade), bb = Math.round(b * shade);
        vert(vc, p, light, rr, gg, bb, nx, ny, nz, ax, ay, az, 0f, 0f);
        vert(vc, p, light, rr, gg, bb, nx, ny, nz, bx, by, bz, 1f, 0f);
        vert(vc, p, light, rr, gg, bb, nx, ny, nz, cx, cy, cz, 1f, 1f);
        vert(vc, p, light, rr, gg, bb, nx, ny, nz, dx, dy, dz, 0f, 1f);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose p, int light, int r, int g, int b,
                             float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        vc.addVertex(p, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, nx, ny, nz);
    }
}
