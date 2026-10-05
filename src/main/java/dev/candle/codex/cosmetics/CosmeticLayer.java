package dev.candle.codex.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.candle.codex.CodexClient;
import dev.candle.codex.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * 3D hats, glasses and wings for your own player, built from plain boxes (no model files).
 * Space note: inside a render layer the pose stack is the model space of the player, in blocks, with +Y pointing
 * DOWN, the head pivot at the origin and the back of the player towards +Z (so the face looks towards -Z).
 * The head is 8 x 8 x 8 pixels: x -4..4, y -8..0, z -4..4 (the hat overlay of the skin reaches 4.5).
 * Raw generic types are used on purpose so the class does not depend on the PlayerModel package name.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class CosmeticLayer extends RenderLayer {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(CodexClient.ID, "textures/cosmetic/white.png");
    /** Full brightness (the same value as the game's LightTexture.FULL_BRIGHT), used for glowing parts. */
    private static final int FULL = 0xF000F0;
    /** Dragon flap clock (advances with the speed setting) and the last game time it was updated at. Render thread only. */
    private static float flapPhase, lastTime;

    public CosmeticLayer(RenderLayerParent parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, EntityRenderState rs, float yRot, float xRot) {
        if (!(rs instanceof AvatarRenderState state)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state.id != mc.player.getId() || state.isInvisible) return;

        // layers are submitted just before the game submits the name tag, so this is the place to add the badge
        NameTag.decorate(state);

        final Cosmetics.Entry hat = Cosmetics.find(Cosmetics.HATS, Config.data.cosmetics.hat);
        final Cosmetics.Entry glasses = Cosmetics.find(Cosmetics.GLASSES, Config.data.cosmetics.glasses);
        final Cosmetics.Entry wings = Cosmetics.find(Cosmetics.WINGS, Config.data.cosmetics.wings);
        final boolean hasHat = !"none".equals(hat.id());
        final boolean hasGlasses = !"none".equals(glasses.id());
        final boolean hasWings = !"none".equals(wings.id());
        final Cosmetics.Entry pet = Cosmetics.find(Pets.LIST, Config.data.cosmetics.pet);
        final boolean hasPet = !"none".equals(pet.id());
        final Cosmetics.Entry aura = Cosmetics.find(Auras.LIST, Config.data.cosmetics.aura);
        final boolean hasAura = !"none".equals(aura.id());
        if (!hasHat && !hasGlasses && !hasWings && !hasPet && !hasAura) return;
        final float time = state.ageInTicks;
        if (hasPet) PetRender.draw(poseStack, collector, light, pet, time);
        if (hasAura) AuraRender.draw(poseStack, collector, light, aura, time);

        if (hasHat || hasGlasses) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE), (pose, vc) -> {
                if (hasHat) drawHat(vc, pose, light, hat, time);
                if (hasGlasses) drawGlasses(vc, pose, light, glasses);
            });
            poseStack.popPose();
        }
        if (hasWings) {
            // the dragon flap runs on its own phase so the speed setting can change without the wings jumping
            float speed = Config.data.cosmetics.dragonSpeed;
            if (!(speed >= 0.2f && speed <= 3f)) speed = 1f;
            float dt = time - lastTime;
            if (!(dt >= 0f && dt < 10f)) dt = 0f;
            lastTime = time;
            flapPhase += dt * speed;
            if (flapPhase > 1000f) flapPhase -= 392.699f; // ten full periods of sin(phase * 0.16), so no visible jump
            final float dragonPhase = flapPhase;
            for (int side = -1; side <= 1; side += 2) {
                final int s = side;
                poseStack.pushPose();
                poseStack.translate(s * 1.4f / 16f, 1.8f / 16f, 3.7f / 16f);
                collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
                        (pose, vc) -> drawWing(vc, pose, light, wings, s, time, dragonPhase));
                poseStack.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ hats (pixels, up is negative Y, head top is y = -8.5 with the skin overlay)

    private static void drawHat(VertexConsumer vc, PoseStack.Pose p, int light, Cosmetics.Entry e, float time) {
        int a = e.c1(), b = e.c2();
        int ad = shadeRgb(a, 0.72f), al = mixRgb(a, 0xFFFFFF, 0.38f);
        int bd = shadeRgb(b, 0.72f), bl = mixRgb(b, 0xFFFFFF, 0.38f);
        switch (e.id()) {
            case "crown" -> {
                bx(vc, p, light, 0, -8.85f, 0, 4.85f, 0.35f, 4.85f, b);       // dark trim
                bx(vc, p, light, 0, -9.9f, 0, 4.75f, 0.75f, 4.75f, a);        // band
                bx(vc, p, light, 0, -10.75f, 0, 4.8f, 0.12f, 4.8f, al);       // bright rim
                float[] pos = {-3.95f, 0f, 3.95f};
                for (float cx : pos) {
                    for (float cz : pos) {
                        if (cx == 0f && cz == 0f) continue;                    // 8 points around the ring
                        bx(vc, p, light, cx, -12.2f, cz, 0.85f, 1.45f, 0.85f, a);
                        bx(vc, p, light, cx, -13.85f, cz, 0.55f, 0.25f, 0.55f, al);
                        bx(vc, p, light, cx, -14.4f, cz, 0.3f, 0.3f, 0.3f, 0xFFFFFF); // pearl
                    }
                }
                bx(vc, p, light, 0, -9.9f, -4.8f, 0.85f, 0.5f, 0.12f, 0xE0405A);     // ruby
                bx(vc, p, light, -2.7f, -9.9f, -4.8f, 0.5f, 0.4f, 0.12f, 0x3CAAFF);  // sapphires
                bx(vc, p, light, 2.7f, -9.9f, -4.8f, 0.5f, 0.4f, 0.12f, 0x3CAAFF);
                bx(vc, p, light, 0, -9.9f, 4.8f, 0.85f, 0.5f, 0.12f, 0xE0405A);
            }
            case "tophat" -> {
                bx(vc, p, light, 0, -8.75f, 0, 6.6f, 0.3f, 6.6f, ad);          // brim
                bx(vc, p, light, 0, -12.9f, 0, 4.35f, 3.9f, 4.35f, a);         // body
                bx(vc, p, light, 0, -16.8f, 0, 4.5f, 0.18f, 4.5f, al);         // top rim
                bx(vc, p, light, 0, -9.9f, 0, 4.48f, 0.8f, 4.48f, 0xB02A37);   // red band
                bx(vc, p, light, 0, -9.9f, -4.5f, 0.85f, 0.6f, 0.12f, b);      // buckle
                bx(vc, p, light, 0, -9.9f, -4.55f, 0.45f, 0.3f, 0.1f, bd);
            }
            case "halo" -> {
                float bob = (float) Math.sin(time * 0.12f) * 0.5f;
                for (int k = 0; k < 24; k++) {
                    double ang = k * Math.PI / 12.0;
                    float x = (float) Math.cos(ang) * 5.3f, z = (float) Math.sin(ang) * 5.3f;
                    bx(vc, p, light, x, -15f + bob, z, 0.95f, 0.4f, 0.95f, k % 2 == 0 ? a : al);
                    bx(vc, p, light, x, -15.45f + bob, z, 0.45f, 0.12f, 0.45f, b);
                }
            }
            case "party" -> {
                for (int i = 0; i < 6; i++) {
                    float half = 4.1f - 0.58f * i;
                    bx(vc, p, light, 0, -8.65f - (i + 0.5f) * 2f, 0, half, 1f, half, i % 2 == 0 ? a : b);
                }
                bx(vc, p, light, 0, -21f, 0, 1.1f, 1.1f, 1.1f, 0xFFFFFF);                // pompom
                bx(vc, p, light, -1.6f, -11.6f, -3.62f, 0.35f, 0.35f, 0.1f, 0xFFFFFF);   // confetti
                bx(vc, p, light, 1.4f, -13.6f, -3.04f, 0.35f, 0.35f, 0.1f, 0xFFFFFF);
            }
            case "cat" -> {
                bx(vc, p, light, 0, -8.8f, 0, 4.7f, 0.35f, 4.7f, a);                     // head band
                for (int sx = -1; sx <= 1; sx += 2) {
                    bx(vc, p, light, sx * 2.6f, -9.9f, 0.3f, 1.5f, 1.0f, 0.9f, a);
                    bx(vc, p, light, sx * 2.5f, -11.4f, 0.3f, 1.1f, 0.9f, 0.7f, a);
                    bx(vc, p, light, sx * 2.4f, -12.5f, 0.3f, 0.6f, 0.5f, 0.5f, ad);
                    bx(vc, p, light, sx * 2.6f, -10.2f, -0.65f, 0.9f, 0.7f, 0.1f, b);    // pink inside
                    bx(vc, p, light, sx * 2.5f, -11.5f, -0.45f, 0.55f, 0.5f, 0.1f, b);
                }
            }
            case "santa" -> {
                bx(vc, p, light, 0, -9.0f, 0, 4.95f, 0.8f, 4.95f, 0xF4F4F4);             // fur trim
                bx(vc, p, light, 0, -8.7f, 0, 5.0f, 0.2f, 5.0f, 0xD8D8E0);
                bx(vc, p, light, 0, -10.7f, 0.2f, 4.4f, 1.0f, 4.4f, a);
                bx(vc, p, light, 0, -12.2f, 0.9f, 3.7f, 0.9f, 3.7f, ad);
                bx(vc, p, light, 0, -13.4f, 2.3f, 3.0f, 0.9f, 3.0f, a);
                bx(vc, p, light, 0, -13.5f, 4.0f, 2.4f, 0.9f, 2.4f, ad);
                bx(vc, p, light, 0, -12.5f, 5.6f, 1.9f, 0.9f, 1.9f, a);
                bx(vc, p, light, 0, -9.9f, 6.5f, 1.5f, 1.5f, 1.5f, 0xFFFFFF);            // pompom hangs behind
            }
            case "wizard" -> {
                bx(vc, p, light, 0, -8.8f, 0, 7.2f, 0.25f, 7.2f, ad);                    // wide brim
                bx(vc, p, light, 0, -9.1f, 0, 4.4f, 0.5f, 4.4f, b);                      // gold band
                for (int i = 0; i < 7; i++) {                                           // cone that bends backwards
                    float half = 4.25f - 0.55f * i;
                    bx(vc, p, light, 0, -9.3f - (i + 0.5f) * 1.45f, i * i * 0.1f, half, 0.75f, half, i % 2 == 0 ? a : ad);
                }
                bx(vc, p, light, -1.2f, -12.9f, -3.28f, 0.4f, 0.4f, 0.1f, bl);          // stars
                bx(vc, p, light, 1.0f, -14.4f, -1.72f, 0.35f, 0.35f, 0.1f, bl);
            }
            case "cap" -> {
                bx(vc, p, light, 0, -9.35f, 0, 4.8f, 0.9f, 4.8f, a);                     // dome
                bx(vc, p, light, 0, -10.55f, 0, 4.3f, 0.5f, 4.3f, a);
                bx(vc, p, light, 0, -11.2f, 0, 0.5f, 0.25f, 0.5f, b);                    // button
                bx(vc, p, light, 0, -8.95f, 0, 4.85f, 0.22f, 4.85f, b);                  // band
                bx(vc, p, light, 0, -8.95f, -5.9f, 3.5f, 0.2f, 1.9f, bd);                // brim
            }
            case "glitchcubes" -> Glitchy.hat(e, time,
                    (hx0, hy0, hz0, sx0, sy0, sz0, col0, gl0) -> bx(vc, p, gl0 ? FULL : light, hx0, hy0, hz0, sx0, sy0, sz0, col0));
            default -> { }
        }
    }

    // ------------------------------------------------------------------ glasses (front of the head is z = -4.5, eyes around y = -3.7)

    private static final String[] HEART = {".#.#.", "#####", ".###.", "..#.."};

    private static void drawGlasses(VertexConsumer vc, PoseStack.Pose p, int light, Cosmetics.Entry e) {
        final int f = e.c1(), l = e.c2();
        final float z = -5.25f, cy = -3.7f;
        switch (e.id()) {
            case "black" -> {
                for (int s = -1; s <= 1; s += 2) {
                    float cx = s * 2.3f;
                    bx(vc, p, light, cx, cy, z, 1.7f, 1.45f, 0.2f, f);                   // frame
                    bx(vc, p, light, cx, cy, z - 0.18f, 1.4f, 1.15f, 0.12f, l);          // dark lens
                    bx(vc, p, light, cx - 0.5f, cy - 0.7f, z - 0.32f, 0.55f, 0.16f, 0.04f, mixRgb(l, 0xFFFFFF, 0.4f)); // glint
                    bx(vc, p, light, cx - 0.5f, cy - 0.35f, z - 0.32f, 0.2f, 0.16f, 0.04f, mixRgb(l, 0xFFFFFF, 0.25f));
                }
                bridge(vc, p, light, f, -4.4f, 0.75f);
                arms(vc, p, light, f, -4.1f);
            }
            case "classic" -> {
                for (int s = -1; s <= 1; s += 2) ring(vc, p, light, s * 2.3f, cy, z, 1.7f, 1.4f, 0.4f, f);
                bridge(vc, p, light, f, -4.2f, 0.75f);
                arms(vc, p, light, f, -4.1f);
            }
            case "round" -> {
                for (int s = -1; s <= 1; s += 2) {
                    float cx = s * 2.3f;
                    bx(vc, p, light, cx, cy - 1.3f, z, 0.9f, 0.2f, 0.2f, f);             // octagon ring
                    bx(vc, p, light, cx, cy + 1.3f, z, 0.9f, 0.2f, 0.2f, f);
                    bx(vc, p, light, cx - 1.5f, cy, z, 0.2f, 0.9f, 0.2f, f);
                    bx(vc, p, light, cx + 1.5f, cy, z, 0.2f, 0.9f, 0.2f, f);
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sy = -1; sy <= 1; sy += 2) bx(vc, p, light, cx + sx * 1.05f, cy + sy * 1.0f, z, 0.35f, 0.3f, 0.2f, f);
                    }
                    bx(vc, p, light, cx - 0.6f, cy - 0.5f, z - 0.05f, 0.25f, 0.12f, 0.05f, l); // sparkle
                }
                bridge(vc, p, light, f, -4.2f, 0.75f);
                arms(vc, p, light, f, -4.1f);
            }
            case "aviator" -> {
                for (int s = -1; s <= 1; s += 2) {
                    float cx = s * 2.3f;
                    ring(vc, p, light, cx, cy, z, 1.75f, 1.45f, 0.3f, f);
                    bx(vc, p, light, cx, cy - 0.5f, z - 0.02f, 1.45f, 0.6f, 0.1f, l);                       // upper lens, dark
                    bx(vc, p, light, cx, cy + 0.65f, z - 0.02f, 1.45f, 0.55f, 0.1f, mixRgb(l, 0xFFFFFF, 0.22f)); // lower lens, lighter
                }
                bridge(vc, p, light, f, -4.5f, 0.75f);
                bridge(vc, p, light, f, -3.9f, 0.75f);
                arms(vc, p, light, f, -4.1f);
            }
            case "neon" -> {
                final int dark = 0x0B0B10;
                bx(vc, p, light, 0, cy, z, 4.55f, 1.4f, 0.2f, dark);                     // visor frame
                for (int i = 0; i < 8; i++) {                                           // glowing gradient lens
                    bx(vc, p, FULL, -3.5f + i, cy, z - 0.18f, 0.5f, 1.1f, 0.12f, mixRgb(f, l, i / 7f));
                }
                arms(vc, p, light, dark, -4.1f);
            }
            case "heart" -> {
                final float cell = 0.64f;
                for (int s = -1; s <= 1; s += 2) {
                    float cx = s * 2.35f;
                    for (int r = 0; r < HEART.length; r++) {
                        for (int c = 0; c < 5; c++) {
                            if (HEART[r].charAt(c) != '#') continue;
                            int col = (r == 0 && c == 1) ? l : f;                          // light spot on the left lobe
                            bx(vc, p, light, cx + (c - 2) * cell, cy + (r - 1.5f) * cell, z, cell / 2f, cell / 2f, 0.2f, col);
                        }
                    }
                }
                bridge(vc, p, light, f, -4.0f, 0.75f);
                arms(vc, p, light, f, -4.1f);
            }
            case "glitchvisor" -> Glitchy.glasses(e,
                    (gx0, gy0, gz0, sx0, sy0, sz0, col0, gl0) -> bx(vc, p, gl0 ? FULL : light, gx0, gy0, gz0, sx0, sy0, sz0, col0));
            default -> { }
        }
    }

    /** Rectangular frame without a lens. */
    private static void ring(VertexConsumer vc, PoseStack.Pose p, int light, float cx, float cy, float z, float hx, float hy, float t, int rgb) {
        bx(vc, p, light, cx, cy - hy + t / 2f, z, hx, t / 2f, 0.2f, rgb);
        bx(vc, p, light, cx, cy + hy - t / 2f, z, hx, t / 2f, 0.2f, rgb);
        bx(vc, p, light, cx - hx + t / 2f, cy, z, t / 2f, hy - t, 0.2f, rgb);
        bx(vc, p, light, cx + hx - t / 2f, cy, z, t / 2f, hy - t, 0.2f, rgb);
    }

    private static void bridge(VertexConsumer vc, PoseStack.Pose p, int light, int rgb, float y, float hx) {
        bx(vc, p, light, 0, y, -5.25f, hx, 0.3f, 0.25f, rgb);
    }

    /** Temples running back along the sides of the head, with a small hinge at the front. */
    private static void arms(VertexConsumer vc, PoseStack.Pose p, int light, int rgb, float y) {
        for (int s = -1; s <= 1; s += 2) {
            bx(vc, p, light, s * 5.05f, y, -2.6f, 0.22f, 0.3f, 2.7f, rgb);
            bx(vc, p, light, s * 4.65f, y, -5.25f, 0.45f, 0.3f, 0.25f, rgb);
        }
    }

    // ------------------------------------------------------------------ wings
    // Wing space: x outwards from the shoulder (mirrored for the left wing), y down, z backwards, all in pixels.
    // Each wing is built from bones (stepped diagonal lines), membranes / feathers (thin filled shapes) and highlights.
    // It flaps by bending its points around the shoulder (Pen.warp), so no pose rotation is needed.

    /** Receives the boxes of a wing instead of the game (used for the menu pictures). x is mirrored for side -1; z grows backwards. */
    public interface WingSink {
        void box(float cx, float cy, float cz, float hx, float hy, int rgb, boolean glow);

        /** Same box with its depth (half size along z); thin wing parts used to lose it. Used by the 3D menu pictures. */
        default void box(float cx, float cy, float cz, float hx, float hy, float hz, int rgb, boolean glow) {
            box(cx, cy, cz, hx, hy, rgb, glow);
        }
    }

    /** Builds one wing (side 1 or -1) into a sink, with exactly the same shapes as the 3D wing. dragonPhase drives the dragon flap. */
    public static void previewWing(Cosmetics.Entry e, int side, float time, float dragonPhase, WingSink sink) {
        Pen w = new Pen(null, null, 0, side);
        w.sink = sink;
        buildWing(w, e, time, dragonPhase);
    }

    private static void drawWing(VertexConsumer vc, PoseStack.Pose p, int light, Cosmetics.Entry e, int side, float time, float dragonPhase) {
        buildWing(new Pen(vc, p, light, side), e, time, dragonPhase);
    }

    private static void buildWing(Pen w, Cosmetics.Entry e, float time, float dragonPhase) {
        switch (e.id()) {
            case "dragon" -> wingDragon(w, e, dragonPhase);
            case "bat" -> wingBat(w, e, time);
            case "phoenix" -> wingPhoenix(w, e, time);
            case "crystal" -> wingCrystal(w, e, time);
            case "fairy" -> wingFairy(w, e, time);
            case "flame" -> wingFlame(w, e, time);
            case "glitchwings" -> wingGlitch(w, e, time);
            default -> wingAngel(w, e, time);
        }
    }

    /** Glitchy pack: the shapes come from Glitchy.wing; this only bends them for the flap and sets which parts glow. */
    private static void wingGlitch(Pen w, Cosmetics.Entry e, float time) {
        w.anim(26f, 0.12f, 0.16f, (float) Math.sin(time * 0.2f), 0.12f);
        Glitchy.wing(e, time, (wx0, wy0, wz0, sx0, sy0, sz0, col0, gl0) -> {
            w.lt = gl0 ? FULL : w.light;
            w.box(wx0, wy0, wz0, sx0, sy0, sz0, col0);
        });
        w.lt = w.light;
    }

    /** Draws the pieces of one wing (new object per wing and frame, so nothing is shared between render calls). */
    private static final class Pen {
        final VertexConsumer vc;
        final PoseStack.Pose p;
        final int light;
        final int side;
        /** Light of the next boxes: the world light, or FULL for glowing parts. */
        int lt;
        /** When set, boxes go here instead of the vertex consumer. */
        WingSink sink;
        float span = 24f, rest, amp, wave, sweep;
        private float ox, oy, oz, slope;

        Pen(VertexConsumer vc, PoseStack.Pose p, int light, int side) {
            this.vc = vc;
            this.p = p;
            this.light = light;
            this.side = side;
            this.lt = light;
        }

        /** Flap: the wing bends up by (rest + amp * wave) radians at the tip, less near the shoulder; sweep pushes the tip backwards. */
        void anim(float span, float rest, float amp, float wave, float sweep) {
            this.span = span;
            this.rest = rest;
            this.amp = amp;
            this.wave = wave;
            this.sweep = sweep;
        }

        private void warp(float x, float y) {
            float k = Math.max(0f, Math.min(1f, x / span));
            float a = (rest + amp * wave) * (0.25f + 0.75f * k);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            ox = x * c + y * s;
            oy = -x * s + y * c;
            oz = x * sweep;
            slope = Math.abs(s);
        }

        void box(float cx, float cy, float cz, float hx, float hy, float hz, int rgb) {
            warp(cx, cy);
            if (sink != null) {
                sink.box(side * ox, oy, cz + oz, hx, hy + hx * slope, hz, rgb, lt == FULL);
                return;
            }
            if (hz <= 0.45f) bxFlat(vc, p, lt, side * ox, oy, cz + oz, hx, hy + hx * slope, hz, rgb);
            else bx(vc, p, lt, side * ox, oy, cz + oz, hx, hy + hx * slope, hz, rgb);
        }

        /** Thick stepped line from (x0, y0) to (x1, y1); thickness and colour change along it; the part after glowFrom (0..1) glows. */
        void seg(float x0, float y0, float x1, float y1, float t0, float t1, float z, float hz, int c0, int c1, float glowFrom) {
            float dx = x1 - x0, dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float tm = Math.max(t0, t1);
            int n = Math.max(1, (int) Math.ceil(len / Math.max(0.35f, tm * 0.55f)));
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                float t = t0 + (t1 - t0) * f;
                lt = f >= glowFrom ? FULL : light;
                box(x0 + dx * f, y0 + dy * f, z + (i & 3) * 0.01f, t / 2f, t / 2f, hz, mixRgb(c0, c1, f));
            }
            lt = light;
        }

        /** Fills a convex polygon with 1 px strips cut into chunks; the colour goes from near (at ax, ay) to far (maxD away). */
        void fill(float[] xs, float[] ys, float ax, float ay, float maxD, int near, int far, float z, float hz, float chunk) {
            int n = xs.length;
            float ymin = Float.MAX_VALUE, ymax = -Float.MAX_VALUE;
            for (int k = 0; k < n; k++) {
                ymin = Math.min(ymin, ys[k]);
                ymax = Math.max(ymax, ys[k]);
            }
            lt = light;
            for (float y = ymin + 0.4f; y < ymax + 0.2f; y += 1.1f) {
                float xa = Float.MAX_VALUE, xb = -Float.MAX_VALUE;
                for (int k = 0; k < n; k++) {
                    int k2 = (k + 1) % n;
                    float y0 = ys[k], y1 = ys[k2];
                    if ((y0 <= y && y1 > y) || (y1 <= y && y0 > y)) {
                        float x = xs[k] + (y - y0) / (y1 - y0) * (xs[k2] - xs[k]);
                        xa = Math.min(xa, x);
                        xb = Math.max(xb, x);
                    }
                }
                if (xb - xa < 0.05f) continue;
                int parts = Math.max(1, Math.round((xb - xa) / (chunk * 1.3f)));
                float cw = (xb - xa) / parts;
                for (int q = 0; q < parts; q++) {
                    float cx = xa + cw * (q + 0.5f);
                    float tt = Math.min(1f, (float) Math.sqrt((cx - ax) * (cx - ax) + (y - ay) * (y - ay)) / maxD);
                    int col = mixRgb(near, far, tt);
                    if (((q + (int) y) & 1) == 1) col = shadeRgb(col, 0.95f); // faint pixel texture
                    box(cx, y, z, cw / 2f + 0.4f, 0.85f, hz, col);
                }
            }
        }
    }

    private static float dist(float ax, float ay, float bx, float by) {
        return (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
    }

    // ---- dragon and bat: arm bones, fingers and a scalloped membrane

    private static void wingDragon(Pen w, Cosmetics.Entry e, float phase) {
        w.anim(24f, 0.15f, 0.42f, (float) Math.sin(phase * 0.16f), 0.22f);
        membrane(w, e, new float[]{5f, -6.5f, 12f, -12f},
                new float[][]{{24f, -8f}, {22f, 0.5f}, {16f, 7.5f}, {8f, 10.5f}}, 1.2f, 9f, 0.22f, 1.15f, true);
    }

    private static void wingBat(Pen w, Cosmetics.Entry e, float time) {
        w.anim(29f, 0.10f, 0.30f, (float) Math.sin(time * 0.23f), 0.18f);
        membrane(w, e, new float[]{6f, -7.5f, 14f, -13f},
                new float[][]{{29f, -7.5f}, {25f, 3f}, {17.5f, 11.5f}, {9f, 14f}}, 1.5f, 10f, 0.32f, 0.95f, false);
    }

    /**
     * arm = {elbowX, elbowY, wristX, wristY} (the shoulder is 0, 0); fingers start at the wrist; the membrane reaches the
     * body at (bodyX, bodyY). pull bends the trailing edge between two fingertips towards the wrist (scallops).
     */
    private static void membrane(Pen w, Cosmetics.Entry e, float[] arm, float[][] fingers, float bodyX, float bodyY,
                                 float pull, float boneT, boolean spikes) {
        int mem = e.c1(), bone = e.c2();
        int near = mixRgb(mem, 0xFFFFFF, 0.16f), far = shadeRgb(mem, 0.6f), rim = shadeRgb(mem, 0.42f);
        int boneHi = mixRgb(bone, 0xFFFFFF, 0.3f), claw = mixRgb(bone, 0xFFFFFF, 0.72f);
        float ex = arm[0], ey = arm[1], wx = arm[2], wy = arm[3];
        int nf = fingers.length;

        // membrane panels fan out from the wrist: between every two fingertips, then fingertip -> body, then body -> shoulder
        float[][] ends = new float[nf + 2][];
        for (int i = 0; i < nf; i++) ends[i] = fingers[i];
        ends[nf] = new float[]{bodyX, bodyY};
        ends[nf + 1] = new float[]{0f, 0f};
        for (int i = 0; i <= nf; i++) {
            float[] a = ends[i], b = ends[i + 1];
            float maxD = Math.max(dist(wx, wy, a[0], a[1]), dist(wx, wy, b[0], b[1]));
            float ps = i % 2 == 0 ? 1f : 0.9f;
            int n0 = shadeRgb(near, ps), f0 = shadeRgb(far, ps);
            float z = 0.12f + i * 0.01f;
            if (i == nf) {
                w.fill(new float[]{wx, a[0], b[0]}, new float[]{wy, a[1], b[1]}, wx, wy, maxD, n0, f0, z, 0.2f, 3.2f);
                continue;
            }
            float mx = (a[0] + b[0]) / 2f, my = (a[1] + b[1]) / 2f;
            mx += (wx - mx) * pull;
            my += (wy - my) * pull;
            w.fill(new float[]{wx, a[0], mx}, new float[]{wy, a[1], my}, wx, wy, maxD, n0, f0, z, 0.2f, 3.2f);
            w.fill(new float[]{wx, mx, b[0]}, new float[]{wy, my, b[1]}, wx, wy, maxD, n0, f0, z + 0.005f, 0.2f, 3.2f);
            w.seg(a[0], a[1], mx, my, 0.7f, 0.7f, z, 0.2f, rim, rim, 2f);   // dark trailing edge
            w.seg(mx, my, b[0], b[1], 0.7f, 0.7f, z, 0.2f, rim, rim, 2f);
        }

        // arm
        w.seg(0f, 0f, ex, ey, boneT * 1.5f, boneT * 1.2f, 0f, 0.55f, bone, bone, 2f);
        w.seg(ex, ey, wx, wy, boneT * 1.2f, boneT, 0f, 0.55f, bone, bone, 2f);
        w.seg(0.3f, -0.7f, ex, ey - 0.7f, boneT * 0.45f, boneT * 0.4f, 0.55f, 0.12f, boneHi, boneHi, 2f); // highlight on top
        w.box(ex, ey, 0f, boneT * 0.95f, boneT * 0.95f, 0.6f, boneHi);                                     // elbow
        w.box(wx, wy, 0f, boneT * 1.05f, boneT * 1.05f, 0.65f, boneHi);                                    // wrist
        w.seg(wx, wy, wx + 1.6f, wy - 4.4f, boneT, boneT * 0.3f, 0f, 0.45f, claw, claw, 2f);               // thumb claw
        if (spikes) {
            w.seg(ex, ey, ex - 0.4f, ey - 3.2f, 1.2f, 0.4f, 0f, 0.45f, claw, claw, 2f);
            w.seg(ex * 0.5f, ey * 0.5f, ex * 0.5f - 0.3f, ey * 0.5f - 2.6f, 0.9f, 0.35f, 0f, 0.4f, claw, claw, 2f);
            w.seg((ex + wx) / 2f, (ey + wy) / 2f, (ex + wx) / 2f - 0.3f, (ey + wy) / 2f - 2.6f, 0.9f, 0.35f, 0f, 0.4f, claw, claw, 2f);
        }
        // fingers with a small claw at the tip
        for (float[] f : fingers) {
            w.seg(wx, wy, f[0], f[1], boneT, boneT * 0.5f, 0f, 0.5f, bone, bone, 2f);
            w.box(f[0], f[1], 0f, boneT * 0.45f, boneT * 0.45f, 0.45f, claw);
        }
    }

    // ---- feathers (angel, phoenix)

    /** One feather: a stepped line from the base point in the direction angDeg (0 = straight down, 90 = straight out). */
    private static void feather(Pen w, float[] base, float angDeg, float len, float t0, float t1, float z, float hz, int c0, int c1, float glowFrom) {
        double a = Math.toRadians(angDeg);
        w.seg(base[0], base[1], base[0] + (float) Math.sin(a) * len, base[1] + (float) Math.cos(a) * len, t0, t1, z, hz, c0, c1, glowFrom);
    }

    private static final float[][] ANGEL_BASE = {{1.5f, -1.5f}, {4f, -5f}, {7.5f, -8.7f}, {11f, -10.8f}, {14.5f, -11.6f}, {18f, -11.2f}, {21f, -9.8f}, {23.5f, -7.5f}};
    private static final float[] ANGEL_ANG = {8f, 18f, 30f, 42f, 54f, 66f, 78f, 88f};
    private static final float[] ANGEL_LEN = {13f, 14.5f, 16f, 17f, 16.5f, 15f, 13f, 10.5f};

    private static void wingAngel(Pen w, Cosmetics.Entry e, float time) {
        w.anim(24f, 0.06f, 0.08f, (float) Math.sin(time * 0.09f), 0.12f);
        int c1 = e.c1(), c2 = e.c2();
        int back = shadeRgb(c2, 0.96f), mid = mixRgb(c1, c2, 0.5f);
        int bone = mixRgb(c2, 0x8FA6C8, 0.6f);
        int n = ANGEL_BASE.length;
        for (int i = 0; i < n; i++) {                       // long flight feathers at the back
            int col = i % 2 == 0 ? back : shadeRgb(back, 0.92f);
            feather(w, ANGEL_BASE[i], ANGEL_ANG[i], ANGEL_LEN[i], 2.6f, 1.5f, -0.2f, 0.4f, col, mixRgb(col, 0xFFFFFF, 0.55f), 2f);
        }
        for (int i = 0; i < n; i++) {                       // middle layer
            int col = i % 2 == 0 ? mid : shadeRgb(mid, 0.94f);
            feather(w, ANGEL_BASE[i], ANGEL_ANG[i] + 5f, ANGEL_LEN[i] * 0.68f, 2.4f, 1.4f, 0.5f, 0.4f, col, mixRgb(col, 0xFFFFFF, 0.4f), 2f);
        }
        for (int i = 1; i < n - 1; i++) {                   // short white coverts on top
            int col = i % 2 == 0 ? c1 : shadeRgb(c1, 0.93f);
            feather(w, ANGEL_BASE[i], ANGEL_ANG[i] + 2f, ANGEL_LEN[i] * 0.42f, 2.2f, 1.3f, 1.2f, 0.4f, col, col, 2f);
        }
        w.seg(0f, 0f, ANGEL_BASE[0][0], ANGEL_BASE[0][1], 1.5f, 1.4f, 1.7f, 0.3f, bone, bone, 2f); // leading edge bone
        for (int i = 0; i + 1 < n; i++) {
            w.seg(ANGEL_BASE[i][0], ANGEL_BASE[i][1], ANGEL_BASE[i + 1][0], ANGEL_BASE[i + 1][1], 1.4f, 1.2f, 1.7f, 0.3f, bone, bone, 2f);
        }
    }

    private static final float[][] PH_BASE = {{1.5f, -1.5f}, {4f, -6f}, {7.5f, -10f}, {11.5f, -13f}, {16f, -14.5f}, {20.5f, -14f}, {24.5f, -12f}, {27.5f, -9.5f}};
    private static final float[] PH_ANG = {5f, 14f, 24f, 36f, 48f, 60f, 72f, 82f};
    private static final float[] PH_LEN = {11f, 13.5f, 16f, 18f, 19.5f, 18.5f, 16f, 12.5f};

    private static void wingPhoenix(Pen w, Cosmetics.Entry e, float time) {
        w.anim(30f, 0.10f, 0.16f, (float) Math.sin(time * 0.13f), 0.15f);
        int red = e.c1(), yellow = e.c2();
        int orange = mixRgb(red, yellow, 0.45f);
        int n = PH_BASE.length;
        for (int i = 0; i < n; i++) {                       // back layer: red at the root, yellow glowing tips
            float sway = 4f * (float) Math.sin(time * 0.22f + i * 0.9f);
            int c0 = shadeRgb(red, 0.75f);
            feather(w, PH_BASE[i], PH_ANG[i] + sway, PH_LEN[i], 2.8f, 1.4f, -0.2f, 0.4f, c0, mixRgb(orange, yellow, 0.8f), 0.35f);
        }
        for (int i = 0; i < n; i++) {                       // middle layer: orange to yellow, all glowing
            float sway = 3f * (float) Math.sin(time * 0.26f + i * 1.2f + 1f);
            feather(w, PH_BASE[i], PH_ANG[i] + 5f + sway, PH_LEN[i] * 0.66f, 2.5f, 1.3f, 0.5f, 0.4f, red, yellow, 0f);
        }
        for (int i = 1; i < n - 1; i++) {                   // short hot coverts
            feather(w, PH_BASE[i], PH_ANG[i] + 2f, PH_LEN[i] * 0.38f, 2.2f, 1.2f, 1.2f, 0.4f, orange, mixRgb(yellow, 0xFFFFFF, 0.6f), 0f);
        }
        int bone = shadeRgb(red, 0.45f);
        w.seg(0f, 0f, PH_BASE[0][0], PH_BASE[0][1], 1.6f, 1.5f, 1.7f, 0.3f, bone, bone, 2f);
        for (int i = 0; i + 1 < n; i++) {
            w.seg(PH_BASE[i][0], PH_BASE[i][1], PH_BASE[i + 1][0], PH_BASE[i + 1][1], 1.5f, 1.2f, 1.7f, 0.3f, bone, bone, 2f);
        }
    }

    // ---- crystal: bright glowing shards fanning out from the shoulder

    /** {angle in degrees from straight out (counter-clockwise, up is positive), length, width} */
    private static final float[][] SHARDS = {
            {88f, 12f, 2.6f}, {80f, 16f, 3.0f}, {62f, 19f, 3.4f}, {45f, 20f, 3.4f}, {28f, 18f, 3.2f},
            {10f, 15f, 3.0f}, {-10f, 12f, 2.6f}, {-30f, 10f, 2.2f}, {-52f, 8f, 2.0f}};

    private static void wingCrystal(Pen w, Cosmetics.Entry e, float time) {
        w.anim(22f, 0.10f, 0.05f, (float) Math.sin(time * 0.07f), 0.10f);
        int c1 = e.c1(), c2 = e.c2();
        int count = SHARDS.length;
        w.lt = FULL;
        for (int idx = 0; idx < count; idx++) {
            float[] s = SHARDS[idx];
            double a = Math.toRadians(s[0]);
            float dx = (float) Math.cos(a), dy = -(float) Math.sin(a);
            int base = mixRgb(c1, c2, idx / (float) (count - 1));
            float z = (idx % 2 == 0) ? 1.0f : 0.4f;
            int steps = Math.max(4, (int) Math.ceil(s[1] / (s[2] * 0.5f)));
            for (int i = 0; i < steps; i++) {
                float f = (i + 0.5f) / steps;
                float prof = f < 0.45f ? 0.35f + 0.65f * f / 0.45f : 1f - (f - 0.45f) / 0.55f * 0.8f;
                float size = s[2] * prof;
                float cx = 1f + dx * s[1] * f, cy = 0.5f + dy * s[1] * f;
                float hz = Math.max(0.35f, size * 0.3f);
                w.box(cx, cy, z, size / 2f, size / 2f, hz, mixRgb(base, 0xFFFFFF, 0.25f * f));
                if (size > 1.2f) w.box(cx - 0.15f, cy - 0.15f, z + hz * 0.9f, size * 0.22f, size * 0.22f, 0.2f, mixRgb(base, 0xFFFFFF, 0.7f)); // bright core
            }
            if (idx % 3 == 1) {                              // twinkling spark at the tip
                float v = (float) Math.sin(time * 0.17f + idx * 1.9f);
                if (v > 0.55f) {
                    float sp = 0.3f + 0.6f * (v - 0.55f) / 0.45f;
                    w.box(1f + dx * (s[1] + 1.2f), 0.5f + dy * (s[1] + 1.2f), z + 0.8f, sp, sp, 0.3f, 0xFFFFFF);
                }
            }
        }
        w.lt = w.light;
    }

    // ---- fairy: two thin rounded wings with veins

    private static void ellipse(Pen w, float cx, float cy, float rx, float ry, float rotDeg, int n,
                                float ax, float ay, float maxD, int near, int far, float z, float hz) {
        float[] xs = new float[n], ys = new float[n];
        double r = Math.toRadians(rotDeg), cr = Math.cos(r), sr = Math.sin(r);
        for (int i = 0; i < n; i++) {
            double t = 2.0 * Math.PI * i / n;
            double ux = Math.cos(t) * rx, uy = Math.sin(t) * ry;
            xs[i] = (float) (cx + ux * cr - uy * sr);
            ys[i] = (float) (cy + ux * sr + uy * cr);
        }
        w.fill(xs, ys, ax, ay, maxD, near, far, z, hz, 2.6f);
    }

    /** Three veins from the wing root to the rim of an ellipse like the one drawn by ellipse(). */
    private static void veins(Pen w, float cx, float cy, float rx, float ry, float rotDeg, int col, float z) {
        double r = Math.toRadians(rotDeg), cr = Math.cos(r), sr = Math.sin(r);
        float sx = (float) (cx - rx * 0.95 * cr), sy = (float) (cy - rx * 0.95 * sr);
        for (int k = -1; k <= 1; k++) {
            double ux = rx * (0.92 - 0.25 * Math.abs(k)), uy = ry * 0.85 * 0.65 * k;
            float tx = (float) (cx + ux * cr - uy * sr), ty = (float) (cy + ux * sr + uy * cr);
            w.seg(sx, sy, tx, ty, 0.55f, 0.4f, z, 0.1f, col, col, 2f);
        }
    }

    private static void wingFairy(Pen w, Cosmetics.Entry e, float time) {
        w.anim(22f, 0.10f, 0.22f, (float) Math.sin(time * 0.5f), 0.15f);
        int c1 = e.c1(), c2 = e.c2();
        ellipse(w, 11f, -8.5f, 11f, 5.2f, -24f, 14, 0.5f, -0.5f, 22f,
                mixRgb(c1, 0xFFFFFF, 0.6f), mixRgb(c1, c2, 0.85f), 0.1f, 0.2f);
        veins(w, 11f, -8.5f, 11f, 5.2f, -24f, mixRgb(c1, 0xFFFFFF, 0.85f), 0.35f);
        ellipse(w, 7f, 4f, 7.5f, 3.8f, 28f, 12, 0.5f, 0.5f, 14f,
                mixRgb(c2, 0xFFFFFF, 0.55f), mixRgb(c2, c1, 0.35f), 0.05f, 0.2f);
        veins(w, 7f, 4f, 7.5f, 3.8f, 28f, mixRgb(c2, 0xFFFFFF, 0.85f), 0.3f);
    }

    // ---- flame: glowing flame tongues that sway

    private static final float[][] FLAME_BASE = {{2f, -1f}, {6f, -3f}, {10f, -4.5f}, {14f, -5f}, {18f, -4.5f}, {22f, -3f}};
    private static final float[] FLAME_H = {13f, 17f, 19f, 17f, 13f, 9f};

    private static void wingFlame(Pen w, Cosmetics.Entry e, float time) {
        w.anim(24f, 0.08f, 0.10f, (float) Math.sin(time * 0.11f), 0.12f);
        int deep = 0xC8200E, c1 = e.c1(), c2 = e.c2();
        int arch = shadeRgb(deep, 0.55f);
        for (int i = 0; i + 1 < FLAME_BASE.length; i++) {   // dark glowing-ember arch under the tongues
            w.seg(FLAME_BASE[i][0], FLAME_BASE[i][1], FLAME_BASE[i + 1][0], FLAME_BASE[i + 1][1], 1.6f, 1.6f, -0.4f, 0.5f, arch, arch, 2f);
        }
        w.lt = FULL;
        final int steps = 7;
        for (int i = 0; i < FLAME_BASE.length; i++) {
            float bx0 = FLAME_BASE[i][0], by0 = FLAME_BASE[i][1], h = FLAME_H[i];
            float z = (i % 2) * 0.5f - 0.2f;
            float hy = h / (steps - 1) / 2f + 0.4f;
            for (int j = 0; j < steps; j++) {
                float f = j / (float) (steps - 1);
                float width = 3.6f - 2.8f * (float) Math.pow(f, 0.85);
                float sway = (float) Math.sin(time * 0.25f + i * 1.3f + f * 3f) * 1.3f * f;
                float cx = bx0 + sway, cy = by0 - f * h;
                int col = f < 0.5f ? mixRgb(deep, c1, f / 0.5f) : mixRgb(c1, c2, (f - 0.5f) / 0.5f);
                w.box(cx, cy, z, width / 2f, hy, 0.6f, col);
                if (f < 0.7f) w.box(cx, cy, z + 0.45f, width * 0.22f, hy * 0.9f, 0.3f, mixRgb(c2, 0xFFFFFF, 0.5f)); // hot core
            }
        }
        w.lt = w.light;
    }

    private static int mixRgb(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shadeRgb(int c, float f) {
        return (Math.round(((c >> 16) & 255) * f) << 16) | (Math.round(((c >> 8) & 255) * f) << 8) | Math.round((c & 255) * f);
    }

    // ------------------------------------------------------------------ box helper

    /** While set, bx() sends boxes here instead of the vertex consumer (3D menu pictures). Render thread only. */
    private static Pets.Sink capture;

    /** Sends the boxes of a hat (head model space, pixels) to a sink. Used by the 3D menu pictures. */
    public static void previewHat(Cosmetics.Entry e, float time, Pets.Sink sink) {
        capture = sink;
        try {
            drawHat(null, null, 0, e, time);
        } finally {
            capture = null;
        }
    }

    /** Sends the boxes of glasses (head model space, pixels) to a sink. Used by the 3D menu pictures. */
    public static void previewGlasses(Cosmetics.Entry e, Pets.Sink sink) {
        capture = sink;
        try {
            drawGlasses(null, null, 0, e);
        } finally {
            capture = null;
        }
    }

    /** Box given by centre and half sizes, all in pixels (1/16 block). Every vertex element is filled (the entity format needs that). */
    private static void bx(VertexConsumer vc, PoseStack.Pose p, int light, float cx, float cy, float cz, float hx, float hy, float hz, int rgb) {
        if (capture != null) {
            capture.box(cx, cy, cz, hx, hy, hz, rgb, light == FULL);
            return;
        }
        float x0 = (cx - hx) / 16f, x1 = (cx + hx) / 16f;
        float y0 = (cy - hy) / 16f, y1 = (cy + hy) / 16f;
        float z0 = (cz - hz) / 16f, z1 = (cz + hz) / 16f;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        // up is -Y here
        quad(vc, p, light, r, g, b, 1.00f, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(vc, p, light, r, g, b, 0.55f, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(vc, p, light, r, g, b, 0.85f, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(vc, p, light, r, g, b, 0.85f, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(vc, p, light, r, g, b, 0.70f, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(vc, p, light, r, g, b, 0.70f, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
    }

    /** Same as bx but only the two big faces (front and back) and the top: thin wing parts are never seen edge-on, so this is 3x cheaper. */
    private static void bxFlat(VertexConsumer vc, PoseStack.Pose p, int light, float cx, float cy, float cz, float hx, float hy, float hz, int rgb) {
        float x0 = (cx - hx) / 16f, x1 = (cx + hx) / 16f;
        float y0 = (cy - hy) / 16f, y1 = (cy + hy) / 16f;
        float z0 = (cz - hz) / 16f, z1 = (cz + hz) / 16f;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        quad(vc, p, light, r, g, b, 0.85f, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(vc, p, light, r, g, b, 0.85f, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(vc, p, light, r, g, b, 1.00f, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
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
