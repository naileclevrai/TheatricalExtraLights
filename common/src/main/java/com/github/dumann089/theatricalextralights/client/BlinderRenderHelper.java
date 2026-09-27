package com.github.dumann089.theatricalextralights.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.imabad.theatrical.blockentities.light.BaseLightBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.function.Consumer;

/**
 * Rendu des blinders. L'eclairage au sol passe par la lumiere dynamique Theatrical/Shimmer ; ici,
 * ce que l'on voit de l'appareil : chaque lampe est une face emissive au coeur brule vers le
 * blanc, et l'ensemble des lampes bave en un halo avec une lueur dans la haze devant lui.
 * Reference : blinders au-dessus d'une scene sur une video de concert, des paves blancs-chauds
 * qui debordent dans l'image, les lampes a peine distinctes.
 */
public final class BlinderRenderHelper {

    /** Ancien rendu : un seul rectangle colore sur la face. Encore utilise par d'autres appareils. */
    public record FaceQuad(float x, float y, float z, float halfW, float halfH) {
        public static final FaceQuad STANDARD = new FaceQuad(0.5f, 0.68f, 0.422f, 0.3125f, 0.625f);
        public static final FaceQuad DOUBLE_2X2 = new FaceQuad(0.5f, 0.415f, 0.422f, 0.3125f, 0.284375f);
        public static final FaceQuad COMPACT = new FaceQuad(0.5f, 0.40625f, 0.34375f, 0.35f, 0.35f);
    }

    /**
     * Lampes d'un blinder : une grille {@code xs} x {@code ys} sur la face avant en {@code z}
     * (-Z devant), chaque lampe de demi-taille {@code halfW} x {@code halfH}. Releve sur les
     * elements « light » des modeles.
     */
    public record Lamps(float[] xs, float[] ys, float z, float halfW, float halfH) {
        /** 4x2_blinder : deux colonnes, quatre rangees de lentilles de 4/16. */
        public static final Lamps STANDARD_4X2 = new Lamps(
                new float[]{5f / 16f, 11f / 16f},
                new float[]{3.25f / 16f, 8.2f / 16f, 13.2f / 16f, 18.15f / 16f},
                0.422f, 2f / 16f, 2f / 16f);
        /** blinder2x2 : deux colonnes, deux rangees. */
        public static final Lamps DOUBLE_2X2 = new Lamps(
                new float[]{5f / 16f, 11f / 16f},
                new float[]{4.25f / 16f, 9.15f / 16f},
                0.422f, 2f / 16f, 2f / 16f);
        /** blinder1x1 : une lentille de 4.5 x 5. */
        public static final Lamps COMPACT = new Lamps(
                new float[]{0.5f},
                new float[]{6.25f / 16f},
                0.34375f, 2.25f / 16f, 2.5f / 16f);
        /** blinder1x2 : la tete du 1x1 a x = 5/16 et 11/16. */
        public static final Lamps DUAL_1X2 = new Lamps(
                new float[]{5f / 16f, 11f / 16f},
                new float[]{6.25f / 16f},
                0.34375f, 2.25f / 16f, 2.5f / 16f);

        int count() {
            return xs.length * ys.length;
        }

        /** Face englobant toutes les lampes, pour le halo commun. */
        StrobeVisualEffects.Face array() {
            float x0 = xs[0], x1 = xs[0], y0 = ys[0], y1 = ys[0];
            for (float x : xs) {
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
            }
            for (float y : ys) {
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
            }
            return new StrobeVisualEffects.Face((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, z,
                    (x1 - x0) * 0.5f + halfW, (y1 - y0) * 0.5f + halfH, false);
        }
    }

    /** Part du halo d'un strobe a pleine puissance : une lampe seule, puis huit lampes. */
    private static final float HALO_SHARE_ONE_LAMP = 0.55f;
    private static final float HALO_SHARE_EIGHT_LAMPS = 1.0f;

    private BlinderRenderHelper() {
    }

    /**
     * Dessine les lampes allumees : face emissive et coeur surexpose par lampe (dans la pose vue),
     * puis un halo pour l'ensemble (bloom vers la camera, lueur dans la haze devant la face).
     * A appeler depuis un LazyRenderer.
     *
     * @param viewPose      pose recue par le LazyRenderer (vue camera)
     * @param headTransform place une pose fraiche sur la tete : decalage du bloc puis preparePoseStack
     */
    public static void renderLamps(BaseLightBlockEntity blockEntity, MultiBufferSource.BufferSource bufferSource,
                                   PoseStack viewPose, Camera camera, float partialTick,
                                   Consumer<PoseStack> headTransform, Lamps lamps) {
        float intensity = StrobeRenderHelper.renderedIntensity(blockEntity, partialTick);
        if (intensity <= 0f) {
            return;
        }
        float level = Math.min(1f, intensity / 255f);
        int color = blockEntity.getColour();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) (level * 255f);

        for (float x : lamps.xs()) {
            for (float y : lamps.ys()) {
                StrobeVisualEffects.Face lamp = new StrobeVisualEffects.Face(x, y, lamps.z(), lamps.halfW(), lamps.halfH(), false);
                viewPose.pushPose();
                headTransform.accept(viewPose);
                lamp.apply(viewPose);
                StrobeVisualEffects.renderFace(bufferSource, viewPose, lamp, r, g, b, a);
                StrobeVisualEffects.renderHotCore(bufferSource, viewPose, lamp, color, level);
                viewPose.popPose();
            }
        }

        // Un seul halo pour la rangee : les lampes voisines fondent leur bloom en un pave.
        PoseStack faceWorld = new PoseStack();
        headTransform.accept(faceWorld);
        lamps.array().apply(faceWorld);
        float share = HALO_SHARE_ONE_LAMP + (HALO_SHARE_EIGHT_LAMPS - HALO_SHARE_ONE_LAMP)
                * Math.min(1f, (lamps.count() - 1) / 7f);
        StrobeVisualEffects.renderHalo(bufferSource, viewPose, camera, faceWorld, color, level * share);
    }

    /** Ancien rendu : rectangle colore sur la face, sans coeur ni halo. */
    public static void renderFace(
            BaseLightBlockEntity blockEntity,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            float partialTicks,
            FaceQuad face
    ) {
        float intensity = StrobeRenderHelper.renderedIntensity(blockEntity, partialTicks);
        if (intensity <= 0f) {
            return;
        }

        int color = blockEntity.getColour();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) intensity;

        VertexConsumer beamConsumer = bufferSource.getBuffer(ExtraLightsRenderTypes.BEAM);
        poseStack.pushPose();
        poseStack.translate(face.x(), face.y(), face.z());
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        addVertex(beamConsumer, matrix, normal, r, g, b, a, -face.halfW(), face.halfH(), 0f);
        addVertex(beamConsumer, matrix, normal, r, g, b, a, face.halfW(), face.halfH(), 0f);
        addVertex(beamConsumer, matrix, normal, r, g, b, a, face.halfW(), -face.halfH(), 0f);
        addVertex(beamConsumer, matrix, normal, r, g, b, a, -face.halfW(), -face.halfH(), 0f);
        poseStack.popPose();
    }

    public static Vec3 lazyRenderPos(BaseLightBlockEntity blockEntity) {
        return blockEntity.getBlockPos().getCenter();
    }

    private static void addVertex(
            VertexConsumer builder,
            Matrix4f matrix,
            Matrix3f normal,
            int r,
            int g,
            int b,
            int a,
            float x,
            float y,
            float z
    ) {
        builder.vertex(matrix, x, y, z).color(r, g, b, a).endVertex();
    }
}
