package com.github.dumann089.theatricalextralights.client.blockentities;

import com.github.dumann089.theatricalextralights.blockentities.PixelBarBlockEntity;
import com.github.dumann089.theatricalextralights.client.Beam2DRenderTypes;
import com.github.dumann089.theatricalextralights.client.ExtraLightsRenderTypes;
import com.github.dumann089.theatricalextralights.client.StrobeVisualEffects;
import com.github.dumann089.theatricalextralights.config.TheatricalExtraLightsConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.imabad.theatrical.client.LazyRenderers;
import dev.imabad.theatrical.config.TheatricalConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Rendu commun des barres LED a pixels (RGB Bar, Vertical RGB Bar, Moving Bar) : par pixel la
 * cellule faiblement eclairee, la LED en point net et additif, une lueur douce tournee vers la
 * camera, et une nappe (volumetrique, ou plate sans le volumetrique). La geometrie des pixels vient
 * de {@link #strip()} ; le placement de la tete de {@code preparePoseStack}, comme pour le modele.
 *
 * <p>Reference : barre LED au sol sur une video de concert, des points brillants dans un ruban de
 * lumiere, et la lumiere d'une barre qui se lit comme un eventail plat dans la haze.
 */
public abstract class PixelBarRenderer<T extends PixelBarBlockEntity> extends ExtraLightsFixtureRenderer<T> {

    /**
     * Geometrie des pixels dans le repere local de la tete, apres {@code preparePoseStack}.
     *
     * @param vertical   pixels le long de Y (barre verticale) au lieu de X
     * @param first      centre du premier pixel le long de l'axe, par rapport au centre de la face
     * @param pitch      pas entre deux pixels
     * @param halfAlong  demi-taille d'une cellule le long de l'axe
     * @param halfAcross demi-taille d'une cellule en travers
     * @param dotHalf    demi-taille du point LED, plus petit que la cellule
     * @param faceX      centre de la face
     * @param faceY      centre de la face
     * @param faceZ      face avant, -Z devant
     */
    protected record Strip(boolean vertical, float first, float pitch, float halfAlong, float halfAcross,
                           float dotHalf, float faceX, float faceY, float faceZ) {
        float along(int pixel) {
            return first + pixel * pitch;
        }

        /** Lueur proportionnelle a la cellule : 0.30 bloc pour une cellule de 4/16. */
        float glowRadius() {
            return halfAlong * 2.4f;
        }

        float x(float along, float across) {
            return vertical ? across : along;
        }

        float y(float along, float across) {
            return vertical ? along : across;
        }
    }

    /**
     * Faisceau volumetrique plat par pixel : le cone est ecrase en travers pour faire une nappe, ce
     * qui lui donne la haze, la poussiere et les ombres des lyres sans le volume d'un cone rond. Des
     * cones ronds a 5 % additionnaient encore une rangee de barres en un eventail blanc ; des nappes
     * se recouvrent bien moins.
     */
    private static final float PIXEL_HALF_ANGLE_DEG = 12.0f;
    private static final float PIXEL_BEAM_INTENSITY = 0.08f;
    /** Ecrasement de la nappe volumetrique en travers, par rapport a sa largeur. */
    private static final float PIXEL_BEAM_FLATNESS = 0.12f;
    /** Lueur douce autour de chaque LED ; les lueurs voisines se rejoignent en un ruban. */
    private static final float DOT_GLOW_STRENGTH = 0.55f;
    /** La cellule (lentille) autour de la LED s'eclaire faiblement. */
    private static final float CELL_ALPHA = 0.30f;
    /**
     * Faisceau plat : une nappe fine par pixel, a sa couleur, couchee dans l'axe du faisceau et qui
     * s'eteint sur la longueur configuree. Pas de cone.
     */
    private static final float FLAT_BEAM_ALPHA = 0.28f;
    /** Demi-largeur de la nappe a son extremite, en multiple de la demi-cellule. */
    private static final float FLAT_BEAM_SPREAD = 2.2f;
    /** Demi-epaisseur de la nappe : deux feuilles rapprochees, pour un peu de corps de profil. */
    private static final float FLAT_BEAM_HALF_THICKNESS = 0.015f;

    protected PixelBarRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** Geometrie des pixels de cette barre. */
    protected abstract Strip strip();

    @Override
    public void beforeRenderBeam(T blockEntity, PoseStack poseStack, VertexConsumer vertexConsumer, MultiBufferSource multiBufferSource, Direction facing, float partialTicks, boolean isFlipped, BlockState blockstate, boolean isHanging, int packedLight, int packedOverlay) {
        if (blockEntity.getIntensity() <= 0) {
            return;
        }
        // En mode pixel il n'y a pas de dimmer general : chaque pixel porte le sien (dans son niveau
        // et dans getPixelDimmer), et intensity ne sert qu'a savoir si la barre est allumee.
        boolean pixelMode = blockEntity.isPixelMode();
        float master = pixelMode ? 1f : blockEntity.getIntensity() / 255f;
        boolean volumetric = TheatricalExtraLightsConfig.isVolumetricBeamEnabled();
        Strip s = strip();
        int count = blockEntity.getPixelCount();

        if (volumetric) {
            // Une LED allumee fait une ligne de haze ; des LED voisines allumees de la meme couleur
            // font une seule nappe, qui s'elargit avec elles quel que soit leur niveau (rendue au
            // niveau moyen) : le raymarch coute par faisceau, une barre unie en neuf nappes cote a
            // cote rend la meme image qu'une seule nappe large, et une nappe qui se casserait en
            // lignes des que deux niveaux different clignoterait pendant un fondu ou un chase.
            int[] run = {0};
            forEachLitRun(blockEntity, (start, end, colour, meanLevel) -> {
                float centre = (s.along(start) + s.along(end)) * 0.5f;
                float halfRun = (s.along(end) - s.along(start)) * 0.5f + s.halfAlong();
                PoseStack beamPose = new PoseStack();
                preparePoseStack(blockEntity, beamPose, facing, partialTicks, isFlipped, blockstate, isHanging);
                beamPose.translate(s.faceX() + s.x(centre, 0f), s.faceY() + s.y(centre, 0f), s.faceZ());
                float runScale = halfRun / s.halfAlong();
                float widthScale = s.vertical() ? PIXEL_BEAM_FLATNESS : runScale;
                float heightScale = s.vertical() ? runScale : PIXEL_BEAM_FLATNESS;
                // Couleur pleine, la luminosite passe dans l'intensite.
                submitVolumetricBeam(blockEntity, beamPose, partialTicks, PIXEL_HALF_ANGLE_DEG, PIXEL_HALF_ANGLE_DEG,
                        null, 0, 0f, widthScale, heightScale, run[0]++, normalise(colour),
                        master * meanLevel / 255f * PIXEL_BEAM_INTENSITY, s.halfAlong());
            });
        }

        LazyRenderers.addLazyRender(new LazyRenderers.LazyRenderer() {
            @Override
            public void render(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, Camera camera, float partialTick) {
                poseStack.pushPose();
                Vec3 offset = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).subtract(camera.getPosition());
                poseStack.translate(offset.x, offset.y, offset.z);
                preparePoseStack(blockEntity, poseStack, facing, partialTick, isFlipped, blockstate, isHanging);

                float intensity = (blockEntity.getPrevIntensity() + ((blockEntity.getIntensity()) - blockEntity.getPrevIntensity()) * partialTick);
                float alpha = pixelMode ? 1f : intensity / 255f;

                poseStack.translate(s.faceX(), s.faceY(), s.faceZ());
                Matrix4f m = poseStack.last().pose();
                Matrix3f normal = poseStack.last().normal();
                float hx = s.x(s.halfAlong(), s.halfAcross());
                float hy = s.y(s.halfAlong(), s.halfAcross());

                // Les deux render types partagent le tampon de repli du BufferSource : une passe
                // par type, sinon les quads d'une passe partent dans le lot de l'autre.
                VertexConsumer builder = bufferSource.getBuffer(Beam2DRenderTypes.getBeam());
                for (int i = 0; i < count; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    if (blockEntity.getPixelLevel(i) <= 0) continue;
                    int r = (colour >> 16) & 0xFF;
                    int g = (colour >> 8) & 0xFF;
                    int b = colour & 0xFF;
                    float cx = s.x(s.along(i), 0f);
                    float cy = s.y(s.along(i), 0f);
                    int cellA = (int) (alpha * blockEntity.getPixelDimmer(i) / 255f * CELL_ALPHA * 255);
                    addVertex(builder, m, normal, r, g, b, cellA, cx - hx, cy + hy, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, cx + hx, cy + hy, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, cx + hx, cy - hy, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, cx - hx, cy - hy, 0f);
                }
                VertexConsumer dots = bufferSource.getBuffer(ExtraLightsRenderTypes.GLOW);
                float d = s.dotHalf();
                for (int i = 0; i < count; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    if (blockEntity.getPixelLevel(i) <= 0) continue;
                    int r = (colour >> 16) & 0xFF;
                    int g = (colour >> 8) & 0xFF;
                    int b = colour & 0xFF;
                    float cx = s.x(s.along(i), 0f);
                    float cy = s.y(s.along(i), 0f);
                    int dotA = (int) (alpha * blockEntity.getPixelDimmer(i));
                    dots.vertex(m, cx - d, cy + d, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, cx + d, cy + d, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, cx + d, cy - d, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, cx - d, cy - d, -0.002f).color(r, g, b, dotA).endVertex();
                }
                poseStack.popPose();

                // Lueurs : positions des LED dans le repere camera (sans la vue), disques dessines dans la vue.
                PoseStack world = new PoseStack();
                world.translate(offset.x, offset.y, offset.z);
                preparePoseStack(blockEntity, world, facing, partialTick, isFlipped, blockstate, isHanging);
                world.translate(s.faceX(), s.faceY(), s.faceZ() - 0.01f);
                Matrix4f wm = world.last().pose();
                for (int i = 0; i < count; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    int level = blockEntity.getPixelLevel(i);
                    if (level <= 0) continue;
                    Vector4f c = wm.transform(new Vector4f(s.x(s.along(i), 0f), s.y(s.along(i), 0f), 0f, 1f));
                    StrobeVisualEffects.renderGlowDot(bufferSource, poseStack, camera,
                            new Vector3f(c.x, c.y, c.z), colour, s.glowRadius(),
                            alpha * level / 255f * DOT_GLOW_STRENGTH);
                }

                if (!volumetric) {
                    // Sans volumetrique : nappes plates 2D, sans haze.
                    poseStack.pushPose();
                    poseStack.translate(offset.x, offset.y, offset.z);
                    preparePoseStack(blockEntity, poseStack, facing, partialTick, isFlipped, blockstate, isHanging);
                    poseStack.translate(s.faceX(), s.faceY(), s.faceZ());
                    renderFlatBeams(bufferSource, poseStack, blockEntity, s, alpha);
                    poseStack.popPose();
                }
            }

            @Override
            public Vec3 getPos(float partialTick) {
                return blockEntity.getBlockPos().getCenter();
            }
        });
    }

    /** Nappes plates : une par plage de LED voisines allumees, a leur couleur, alpha au depart, zero au bout. */
    private void renderFlatBeams(MultiBufferSource.BufferSource bufferSource, PoseStack stack,
                                 T blockEntity, Strip s, float alpha) {
        float length = TheatricalExtraLightsConfig.getRgbBarBeamLength();
        float opacity = (float) TheatricalConfig.INSTANCE.CLIENT.beamOpacity;
        VertexConsumer vc = bufferSource.getBuffer(Beam2DRenderTypes.getBeam());
        Matrix4f m = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        forEachLitRun(blockEntity, (start, end, colour, meanLevel) -> {
            int r = (colour >> 16) & 0xFF;
            int g = (colour >> 8) & 0xFF;
            int b = colour & 0xFF;
            int a = (int) (alpha * meanLevel / 255f * FLAT_BEAM_ALPHA * opacity * 255);
            if (a <= 0) return;
            float c = (s.along(start) + s.along(end)) * 0.5f;
            float w0 = (s.along(end) - s.along(start)) * 0.5f + s.halfAlong();
            // La divergence est celle d'une cellule, quelle que soit la largeur de la plage.
            float w1 = w0 + s.halfAlong() * (FLAT_BEAM_SPREAD - 1f);
            for (float t : new float[]{FLAT_BEAM_HALF_THICKNESS, -FLAT_BEAM_HALF_THICKNESS}) {
                addVertex(vc, m, normal, r, g, b, a, s.x(c - w0, t), s.y(c - w0, t), 0f);
                addVertex(vc, m, normal, r, g, b, a, s.x(c + w0, t), s.y(c + w0, t), 0f);
                addVertex(vc, m, normal, r, g, b, 0, s.x(c + w1, t), s.y(c + w1, t), -length);
                addVertex(vc, m, normal, r, g, b, 0, s.x(c - w1, t), s.y(c - w1, t), -length);
            }
        });
    }

    /** Une plage de LED voisines allumees, de la meme couleur brute. */
    @FunctionalInterface
    protected interface LitRun {
        void accept(int start, int end, int colour, float meanLevel);
    }

    /**
     * Parcourt les plages de LED voisines allumees de la meme couleur : une LED seule fait une
     * plage a elle, et les niveaux peuvent differer dans une plage (rendue au niveau moyen).
     */
    protected static void forEachLitRun(PixelBarBlockEntity blockEntity, LitRun consumer) {
        int count = blockEntity.getPixelCount();
        int start = 0;
        while (start < count) {
            int level = blockEntity.getPixelLevel(start);
            if (level <= 0) {
                start++;
                continue;
            }
            int colour = blockEntity.getPixelColour(start);
            int end = start;
            long sum = level;
            while (end + 1 < count
                    && blockEntity.getPixelLevel(end + 1) > 0
                    && blockEntity.getPixelColour(end + 1) == colour) {
                end++;
                sum += blockEntity.getPixelLevel(end);
            }
            consumer.accept(start, end, colour, sum / (float) (end - start + 1));
            start = end + 1;
        }
    }

    /** Ramene la composante la plus forte a 255 : la couleur reste saturee, la luminosite va dans l'intensite. */
    private static int normalise(int colour) {
        int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max <= 0) return 0;
        return (r * 255 / max << 16) | (g * 255 / max << 8) | (b * 255 / max);
    }

    @Override
    protected void addVertex(VertexConsumer builder, Matrix4f m, Matrix3f nm,
                             int r, int g, int b, int a,
                             float x, float y, float z) {
        if (Beam2DRenderTypes.isShadersActive()) {
            builder.vertex(m, x, y, z)
                    .color(r, g, b, a)
                    .uv(0f, 0f)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .endVertex();
        } else {
            super.addVertex(builder, m, nm, r, g, b, a, x, y, z);
        }
    }
}
