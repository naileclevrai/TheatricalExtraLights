package com.github.dumann089.theatricalextralights.client.blockentities;

import com.github.dumann089.theatricalextralights.blockentities.RGBBarBlockEntity;
import com.github.dumann089.theatricalextralights.util.FixtureMountTransform;
import com.github.dumann089.theatricalextralights.client.Beam2DRenderTypes;
import com.github.dumann089.theatricalextralights.client.ExtraLightsRenderTypes;
import com.github.dumann089.theatricalextralights.client.StrobeVisualEffects;
import com.github.dumann089.theatricalextralights.config.TheatricalExtraLightsConfig;
import dev.imabad.theatrical.config.TheatricalConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.imabad.theatrical.TheatricalExpectPlatform;
import dev.imabad.theatrical.blocks.HangableBlock;
import dev.imabad.theatrical.client.LazyRenderers;
import dev.imabad.theatrical.client.TheatricalRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.LightTexture;

import java.util.Optional;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class RGBbarRenderer extends ExtraLightsFixtureRenderer<RGBBarBlockEntity> {
    private BakedModel cachedPanModel, cachedTiltModel, cachedStaticModel;

    // Geometrie des pixels, relevee sur ledbar_tilt.json : neuf cellules de 4/16 separees de
    // 1/16, de x=-14 a x=30, hautes de 4/16 autour de y=7.9375, face avant a z=6.4375 (-Z devant).
    // Les x sont donnes par rapport au centre de la barre (x=8/16, ou la pose est translatee de 0.5) :
    // premiere cellule centree en -12/16, soit -20/16 depuis le centre.
    private static final float PIXEL_HALF = 2f / 16f;
    private static final float PIXEL_PITCH = 5f / 16f;
    private static final float FIRST_PIXEL_X = -20f / 16f;
    private static final float PIXEL_Y = 7.9375f / 16f;
    private static final float FACE_Z = 0.38f;
    /**
     * Faisceau volumetrique plat par pixel : le cone est ecrase en hauteur (echelle V) pour faire
     * une nappe, ce qui lui donne la haze, la poussiere et les ombres des lyres sans le volume
     * d'un cone rond. Des cones ronds a 5 % additionnaient encore une rangee de barres en un
     * eventail blanc ; des nappes se recouvrent bien moins.
     */
    private static final float PIXEL_HALF_ANGLE_DEG = 12.0f;
    private static final float PIXEL_BEAM_INTENSITY = 0.08f;
    /** Ecrasement vertical de la nappe volumetrique, par rapport a sa largeur. */
    private static final float PIXEL_BEAM_FLATNESS = 0.12f;
    /** La LED elle-meme : un point net, plus petit que la cellule du modele. */
    private static final float DOT_HALF = 1.1f / 16f;
    /** Lueur douce autour de chaque LED ; les lueurs voisines se rejoignent en un ruban. */
    private static final float DOT_GLOW_RADIUS = 0.30f;
    private static final float DOT_GLOW_STRENGTH = 0.55f;
    /** La cellule (lentille) autour de la LED s'eclaire faiblement. */
    private static final float CELL_ALPHA = 0.30f;
    /**
     * Faisceau plat : une nappe fine par pixel, a sa couleur, couchee dans l'axe du faisceau et
     * qui s'eteint sur la longueur configuree. Pas de cone : la lumiere d'une barre se lit comme
     * un eventail plat dans la haze.
     */
    private static final float FLAT_BEAM_ALPHA = 0.28f;
    /** Demi-largeur de la nappe a son extremite, en multiple de la demi-cellule. */
    private static final float FLAT_BEAM_SPREAD = 2.2f;
    /** Demi-epaisseur de la nappe : deux feuilles rapprochees, pour un peu de corps de profil. */
    private static final float FLAT_BEAM_HALF_THICKNESS = 0.015f;

    private static float pixelX(int pixel) {
        return FIRST_PIXEL_X + pixel * PIXEL_PITCH;
    }

    public RGBbarRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void renderModel(RGBBarBlockEntity blockEntity, PoseStack poseStack, VertexConsumer vertexConsumer, Direction facing, float partialTicks, boolean isFlipped, BlockState blockState, boolean isHanging, int packedLight, int packedOverlay) {
        if(cachedStaticModel == null){
            cachedStaticModel = TheatricalExpectPlatform.getBakedModel(blockEntity.getFixture().getStaticModel());
        }
        if (cachedPanModel == null){
            cachedPanModel = TheatricalExpectPlatform.getBakedModel(blockEntity.getFixture().getPanModel());
        }
        if (cachedTiltModel == null){
            cachedTiltModel = TheatricalExpectPlatform.getBakedModel(blockEntity.getFixture().getTiltModel());
        }
        //#region Fixture Hanging
        poseStack.translate(0.5F, 0, .5F);
        if(isHanging){
            Direction hangDirection = blockState.getValue(HangableBlock.HANG_DIRECTION);
            poseStack.translate(0, 0.5, 0F);
            if(hangDirection.getAxis() != Direction.Axis.Y){
                if(hangDirection.getAxis() == Direction.Axis.Z){
                    if(hangDirection == Direction.SOUTH) {
                        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    }
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));
                } else {
                    if(hangDirection == Direction.EAST) {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(90));
                    }
                }
            } else {
                //TODO: Handle hanging up
            }
            poseStack.translate(0, -0.5, 0F);
        }
        //#endregion
        poseStack.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
        poseStack.translate(-0.5F, 0, -.5F);
        if (isHanging) {
            Optional<BlockState> optionalSupport = blockEntity.getSupportingStructure();
            if (optionalSupport.isPresent()) {
                float[] transforms = blockEntity.getFixture().getTransforms(blockState, optionalSupport.get());
                poseStack.translate(transforms[0], transforms[1], transforms[2]);
            } else {
                poseStack.translate(0, 0.19, 0);
            }
            poseStack.translate(0, -0.08, 0);
        }
        if (isFlipped) {
            poseStack.translate(0.5F, 0.5, .5F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            poseStack.translate(-0.5F, -0.5, -.5F);
        }
        // Static Model Render
        minecraftRenderModel(poseStack, vertexConsumer, blockState, cachedStaticModel, packedLight, packedOverlay);
        //#region Model Pan
        float[] pans = blockEntity.getFixture().getPanRotationPosition();
        poseStack.translate(pans[0], pans[1], pans[2]);
        int prevPan = blockEntity.getPrevPan();
        int pan = blockEntity.getPan();
        poseStack.mulPose(Axis.YP.rotationDegrees((prevPan + (pan - prevPan) * partialTicks)));
        poseStack.translate(-pans[0], -pans[1], -pans[2]);
        minecraftRenderModel(poseStack, vertexConsumer, blockState, cachedPanModel, packedLight, packedOverlay);
        //#endregion
        //#region Model Tilt
        float[] tilts = blockEntity.getFixture().getTiltRotationPosition();
        poseStack.translate(tilts[0], tilts[1], tilts[2]);
        int prevTilt = blockEntity.getPrevTilt();
        int tilt = blockEntity.getTilt();
        if (isFlipped) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-180));
        } else {
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees((prevTilt + (tilt - prevTilt) * partialTicks)));
        poseStack.translate(-tilts[0], -tilts[1], -tilts[2]);
        minecraftRenderModel(poseStack, vertexConsumer, blockState, cachedTiltModel, packedLight, packedOverlay);
        //#endregion
    }
    @Override
    public void beforeRenderBeam(RGBBarBlockEntity blockEntity, PoseStack poseStack, VertexConsumer vertexConsumer, MultiBufferSource multiBufferSource, Direction facing, float partialTicks, boolean isFlipped, BlockState blockstate, boolean isHanging, int packedLight, int packedOverlay) {
        if (blockEntity.getIntensity() <= 0) {
            return;
        }
        float master = blockEntity.getIntensity() / 255f;
        boolean volumetric = TheatricalExtraLightsConfig.isVolumetricBeamEnabled();

        if (volumetric) {
            // Une nappe par suite de pixels de meme couleur : le raymarch coute par faisceau, et une
            // barre unie en neuf nappes cote a cote rend la meme image qu'une seule nappe large.
            // En mode 4 canaux c'est toujours une nappe ; en mode pixel, une par plage de couleur.
            int run = 0;
            int start = 0;
            while (start < RGBBarBlockEntity.PIXEL_COUNT) {
                int colour = blockEntity.getPixelColour(start);
                int level = blockEntity.getPixelLevel(start);
                int end = start;
                while (end + 1 < RGBBarBlockEntity.PIXEL_COUNT
                        && blockEntity.getPixelColour(end + 1) == colour
                        && blockEntity.getPixelLevel(end + 1) == level) {
                    end++;
                }
                if (level > 0) {
                    float centre = (pixelX(start) + pixelX(end)) * 0.5f;
                    float halfWidth = (pixelX(end) - pixelX(start)) * 0.5f + PIXEL_HALF;
                    PoseStack beamPose = new PoseStack();
                    preparePoseStack(blockEntity, beamPose, facing, partialTicks, isFlipped, blockstate, isHanging);
                    beamPose.translate(0.5f + centre, PIXEL_Y, FACE_Z);
                    // Couleur pleine, la luminosite du pixel passe dans l'intensite.
                    submitVolumetricBeam(blockEntity, beamPose, partialTicks, PIXEL_HALF_ANGLE_DEG, PIXEL_HALF_ANGLE_DEG,
                            null, 0, 0f, halfWidth / PIXEL_HALF, PIXEL_BEAM_FLATNESS, run, normalise(colour, level),
                            master * level / 255f * PIXEL_BEAM_INTENSITY, PIXEL_HALF);
                    run++;
                }
                start = end + 1;
            }
        }

        LazyRenderers.addLazyRender(new LazyRenderers.LazyRenderer() {
            @Override
            public void render(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, Camera camera, float partialTick) {
                poseStack.pushPose();
                Vec3 offset = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).subtract(camera.getPosition());
                poseStack.translate(offset.x, offset.y, offset.z);
                preparePoseStack(blockEntity, poseStack, facing, partialTick, isFlipped, blockstate, isHanging);

                float intensity = (blockEntity.getPrevIntensity() + ((blockEntity.getIntensity()) - blockEntity.getPrevIntensity()) * partialTick);
                float alpha = intensity / 255f;

                poseStack.translate(0.5, PIXEL_Y, FACE_Z);
                Matrix4f m = poseStack.last().pose();
                Matrix3f normal = poseStack.last().normal();

                // Face : par pixel, la cellule faiblement eclairee, la LED en point net et additif,
                // puis une lueur douce tournee vers la camera. Reference : barre LED au sol sur une
                // video de concert, des points brillants dans un ruban de lumiere.
                // Les deux render types partagent le tampon de repli du BufferSource : une passe
                // par type, sinon les quads d'une passe partent dans le lot de l'autre.
                VertexConsumer builder = bufferSource.getBuffer(Beam2DRenderTypes.getBeam());
                for (int i = 0; i < RGBBarBlockEntity.PIXEL_COUNT; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    if (blockEntity.getPixelLevel(i) <= 0) continue;
                    int r = (colour >> 16) & 0xFF;
                    int g = (colour >> 8) & 0xFF;
                    int b = colour & 0xFF;
                    float x = pixelX(i);
                    int cellA = (int) (alpha * CELL_ALPHA * 255);
                    addVertex(builder, m, normal, r, g, b, cellA, x - PIXEL_HALF, PIXEL_HALF, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, x + PIXEL_HALF, PIXEL_HALF, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, x + PIXEL_HALF, -PIXEL_HALF, 0f);
                    addVertex(builder, m, normal, r, g, b, cellA, x - PIXEL_HALF, -PIXEL_HALF, 0f);
                }
                VertexConsumer dots = bufferSource.getBuffer(ExtraLightsRenderTypes.GLOW);
                for (int i = 0; i < RGBBarBlockEntity.PIXEL_COUNT; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    if (blockEntity.getPixelLevel(i) <= 0) continue;
                    int r = (colour >> 16) & 0xFF;
                    int g = (colour >> 8) & 0xFF;
                    int b = colour & 0xFF;
                    float x = pixelX(i);
                    int dotA = (int) (alpha * 255);
                    dots.vertex(m, x - DOT_HALF, DOT_HALF, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, x + DOT_HALF, DOT_HALF, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, x + DOT_HALF, -DOT_HALF, -0.002f).color(r, g, b, dotA).endVertex();
                    dots.vertex(m, x - DOT_HALF, -DOT_HALF, -0.002f).color(r, g, b, dotA).endVertex();
                }
                poseStack.popPose();

                // Lueurs : positions des LED dans le repere camera (sans la vue), disques dessines dans la vue.
                PoseStack world = new PoseStack();
                world.translate(offset.x, offset.y, offset.z);
                preparePoseStack(blockEntity, world, facing, partialTick, isFlipped, blockstate, isHanging);
                world.translate(0.5, PIXEL_Y, FACE_Z - 0.01f);
                Matrix4f wm = world.last().pose();
                for (int i = 0; i < RGBBarBlockEntity.PIXEL_COUNT; i++) {
                    int colour = blockEntity.getPixelColour(i);
                    int level = blockEntity.getPixelLevel(i);
                    if (level <= 0) continue;
                    org.joml.Vector4f c = wm.transform(new org.joml.Vector4f(pixelX(i), 0f, 0f, 1f));
                    StrobeVisualEffects.renderGlowDot(bufferSource, poseStack, camera,
                            new org.joml.Vector3f(c.x, c.y, c.z), colour, DOT_GLOW_RADIUS,
                            alpha * level / 255f * DOT_GLOW_STRENGTH);
                }

                poseStack.pushPose();
                poseStack.translate(offset.x, offset.y, offset.z);
                preparePoseStack(blockEntity, poseStack, facing, partialTick, isFlipped, blockstate, isHanging);
                poseStack.translate(0.5, PIXEL_Y, FACE_Z);
                if (!volumetric) {
                    // Sans volumetrique : nappes plates 2D, sans haze.
                    renderFlatBeams(bufferSource, poseStack, blockEntity, alpha);
                }
                poseStack.popPose();
            }

            @Override
            public Vec3 getPos(float partialTick) {
                return blockEntity.getBlockPos().getCenter();
            }
        });
    }

    /** Nappes plates par pixel : couleur du pixel, alpha au depart, zero au bout. */
    private void renderFlatBeams(MultiBufferSource.BufferSource bufferSource, PoseStack stack,
                                 RGBBarBlockEntity blockEntity, float alpha) {
        float length = TheatricalExtraLightsConfig.getRgbBarBeamLength();
        float opacity = (float) TheatricalConfig.INSTANCE.CLIENT.beamOpacity;
        VertexConsumer vc = bufferSource.getBuffer(Beam2DRenderTypes.getBeam());
        Matrix4f m = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        for (int i = 0; i < RGBBarBlockEntity.PIXEL_COUNT; i++) {
            int colour = blockEntity.getPixelColour(i);
            int level = blockEntity.getPixelLevel(i);
            if (level <= 0) continue;
            int r = (colour >> 16) & 0xFF;
            int g = (colour >> 8) & 0xFF;
            int b = colour & 0xFF;
            int a = (int) (alpha * level / 255f * FLAT_BEAM_ALPHA * opacity * 255);
            if (a <= 0) continue;
            float x = pixelX(i);
            float w0 = PIXEL_HALF;
            float w1 = PIXEL_HALF * FLAT_BEAM_SPREAD;
            for (float y : new float[]{FLAT_BEAM_HALF_THICKNESS, -FLAT_BEAM_HALF_THICKNESS}) {
                addVertex(vc, m, normal, r, g, b, a, x - w0, y, 0f);
                addVertex(vc, m, normal, r, g, b, a, x + w0, y, 0f);
                addVertex(vc, m, normal, r, g, b, 0, x + w1, y, -length);
                addVertex(vc, m, normal, r, g, b, 0, x - w1, y, -length);
            }
        }
    }

    /** Ramene la composante la plus forte a 255 : la couleur reste saturee, la luminosite va dans l'intensite. */
    private static int normalise(int colour, int level) {
        if (level <= 0) return 0;
        int r = Math.min(255, ((colour >> 16) & 0xFF) * 255 / level);
        int g = Math.min(255, ((colour >> 8) & 0xFF) * 255 / level);
        int b = Math.min(255, (colour & 0xFF) * 255 / level);
        return (r << 16) | (g << 8) | b;
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

    protected void renderLightBeam(VertexConsumer builder, PoseStack stack, RGBBarBlockEntity tileEntityFixture, float partialTicks, float alpha, float beamWidth, float beamHeight, float length, int color) {
        alpha *= (float) TheatricalConfig.INSTANCE.CLIENT.beamOpacity;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) (alpha * 255);
        Matrix4f m = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        float focus = 1.0f;
        float endWidth = beamWidth * focus;
        float endHeight = beamHeight * focus;

        // Right Face
        addVertex(builder, m, normal, r, g, b, 0, endWidth, endHeight, -length);
        addVertex(builder, m, normal, r, g, b, a, beamWidth, beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, a, beamWidth, -beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, 0, endWidth, -endHeight, -length);

        // Left Face
        addVertex(builder, m, normal, r, g, b, 0, -endWidth, -endHeight, -length);
        addVertex(builder, m, normal, r, g, b, a, -beamWidth, -beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, a, -beamWidth, beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, 0, -endWidth, endHeight, -length);

        // UP Face
        addVertex(builder, m, normal, r, g, b, 0, -endWidth, endHeight, -length);
        addVertex(builder, m, normal, r, g, b, a, -beamWidth, beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, a, beamWidth, beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, 0, endWidth, endHeight, -length);

        // Down Face
        addVertex(builder, m, normal, r, g, b, 0, endWidth, -endHeight, -length);
        addVertex(builder, m, normal, r, g, b, a, beamWidth, -beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, a, -beamWidth, -beamHeight, 0);
        addVertex(builder, m, normal, r, g, b, 0, -endWidth, -endHeight, -length);
    }

    @Override
    public void preparePoseStack(RGBBarBlockEntity blockEntity, PoseStack poseStack, Direction facing, float partialTicks, boolean isFlipped, BlockState blockState, boolean isHanging) {
        FixtureMountTransform.apply(poseStack, blockEntity);
        poseStack.translate(0.5F, 0, .5F);
        if(isHanging){
            Direction hangDirection = blockState.getValue(HangableBlock.HANG_DIRECTION);
            poseStack.translate(0, 0.5, 0F);
            if(hangDirection.getAxis() != Direction.Axis.Y){
                if(hangDirection.getAxis() == Direction.Axis.Z){
                    if(hangDirection == Direction.SOUTH) {
                        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    }
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));
                } else {
                    if(hangDirection == Direction.EAST) {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(90));
                    }
                }
            } else {
                //TODO: Handle hanging up
            }
            poseStack.translate(0, -0.5, 0F);
        }
        //#endregion
        poseStack.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
        poseStack.translate(-0.5F, 0, -.5F);
        if (isHanging) {
            Optional<BlockState> optionalSupport = blockEntity.getSupportingStructure();
            if (optionalSupport.isPresent()) {
                float[] transforms = blockEntity.getFixture().getTransforms(blockState, optionalSupport.get());
                poseStack.translate(transforms[0], transforms[1], transforms[2]);
            } else {
                poseStack.translate(0, 0.19, 0);
            }
            poseStack.translate(0, -0.08, 0);
        }
        if (isFlipped) {
            poseStack.translate(0.5F, 0.5, .5F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            poseStack.translate(-0.5F, -0.5, -.5F);
        }
        float[] pans = blockEntity.getFixture().getPanRotationPosition();
        poseStack.translate(pans[0], pans[1], pans[2]);
        int prevPan = blockEntity.getPrevPan();
        int pan = blockEntity.getPan();
        poseStack.mulPose(Axis.YP.rotationDegrees((prevPan + (pan - prevPan) * partialTicks)));
        poseStack.translate(-pans[0], -pans[1], -pans[2]);
        //#endregion
        //#region Model Tilt
        float[] tilts = blockEntity.getFixture().getTiltRotationPosition();
        poseStack.translate(tilts[0], tilts[1], tilts[2]);
        int prevTilt = blockEntity.getPrevTilt();
        int tilt = blockEntity.getTilt();
        if (isFlipped) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-180));
        } else {
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees((prevTilt + (tilt - prevTilt) * partialTicks)));
        poseStack.translate(-tilts[0], -tilts[1], -tilts[2]);
        //#endregion
    }
}