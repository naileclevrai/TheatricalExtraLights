package com.github.dumann089.theatricalextralights.client.blockentities;

import com.github.dumann089.theatricalextralights.TheatricalExtraLights;
import com.github.dumann089.theatricalextralights.client.gobo.GoboLibrary;
import com.github.dumann089.theatricalextralights.util.FixtureMountTransform;
import com.github.dumann089.theatricalextralights.blockentities.LaserBlockEntity;
import com.github.dumann089.theatricalextralights.config.TheatricalExtraLightsConfig;
import com.github.dumann089.theatricalextralights.laser.LaserBeam;
import com.github.dumann089.theatricalextralights.laser.LaserPattern;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;

public class LaserRenderer extends ExtraLightsFixtureRenderer<LaserBlockEntity> {
    private BakedModel cachedPanModel, cachedTiltModel, cachedStaticModel;
    // DEBUG: throttle render-side logs to one print every ~120 frames
    private int renderLogTick = 0;

    public LaserRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void renderModel(LaserBlockEntity blockEntity, PoseStack poseStack, VertexConsumer vertexConsumer, Direction facing, float partialTicks, boolean isFlipped, BlockState blockState, boolean isHanging, int packedLight, int packedOverlay) {
        if (cachedStaticModel == null) {
            cachedStaticModel = TheatricalExpectPlatform.getBakedModel(blockEntity.getFixture().getStaticModel());
        }
        if (cachedPanModel == null) {
            cachedPanModel = TheatricalExpectPlatform.getBakedModel(blockEntity.getFixture().getPanModel());
        }
        if (cachedTiltModel == null) {
            cachedTiltModel = TheatricalExpectPlatform.getBakedModel(blockEntity.getFixture().getTiltModel());
        }
        poseStack.translate(0.5F, 0, .5F);
        if (isHanging) {
            Direction hangDirection = blockState.getValue(HangableBlock.HANG_DIRECTION);
            poseStack.translate(0, 0.5, 0F);
            if (hangDirection.getAxis() != Direction.Axis.Y) {
                if (hangDirection.getAxis() == Direction.Axis.Z) {
                    if (hangDirection == Direction.SOUTH) {
                        poseStack.mulPose(Axis.ZP.rotationDegrees(90));
                        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.ZP.rotationDegrees(90));
                        poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    }
                } else {
                    if (hangDirection == Direction.EAST) {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(90));
                    }
                }
            }
            poseStack.translate(0, -0.5, 0F);
        }
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
        minecraftRenderModel(poseStack, vertexConsumer, blockState, cachedStaticModel, packedLight, packedOverlay);
        float[] pans = blockEntity.getFixture().getPanRotationPosition();
        poseStack.translate(pans[0], pans[1], pans[2]);
        int prevPan = blockEntity.getPrevPan();
        int pan = blockEntity.getPan();
        poseStack.mulPose(Axis.YP.rotationDegrees((prevPan + (pan - prevPan) * partialTicks)));
        poseStack.translate(-pans[0], -pans[1], -pans[2]);
        minecraftRenderModel(poseStack, vertexConsumer, blockState, cachedPanModel, packedLight, packedOverlay);
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
    }

    @Override
    public void beforeRenderBeam(LaserBlockEntity blockEntity, PoseStack poseStack, VertexConsumer vertexConsumer,
                                 MultiBufferSource multiBufferSource, Direction facing, float partialTicks, boolean isFlipped,
                                 BlockState blockstate, boolean isHanging, int packedLight, int packedOverlay) {
        if (blockEntity.getIntensity() <= 0) {
            // Vaciar el rastro cuando el fixture está apagado para que no se quede congelado
            blockEntity.getTrailBuffer().clear();
            return;
        }

        submitLaserVolumes(blockEntity, facing, partialTicks, isFlipped, blockstate, isHanging);
        if (TheatricalExtraLightsConfig.isVolumetricBeamEnabled()) {
            return;
        }

        LazyRenderers.addLazyRender(new LazyRenderers.LazyRenderer() {
            @Override
            public void render(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, Camera camera, float partialTick) {
                poseStack.pushPose();
                Vec3 offset = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).subtract(camera.getPosition());
                poseStack.translate(offset.x, offset.y, offset.z);

                preparePoseStack(blockEntity, poseStack, facing, partialTick, isFlipped, blockstate, isHanging);

                VertexConsumer beamConsumer = bufferSource.getBuffer(TheatricalRenderTypes.BEAM);

                float intensity = blockEntity.getPrevIntensity() + ((blockEntity.getIntensity() - blockEntity.getPrevIntensity()) * partialTick);
                float intensity01 = intensity / 255f;

                int c1 = blockEntity.getColour();
                int c2 = blockEntity.getColour2();
                int c3 = blockEntity.getColour3();
                if (c2 == 0) c2 = c1;
                if (c3 == 0) c3 = c2;

                double animTimeSec = (blockEntity.getLevel().getGameTime() + partialTick) / 20.0;
                LaserPattern pattern = blockEntity.getPattern();
                List<LaserBeam> beams = pattern.generate(
                        blockEntity.getSizeRaw(),
                        blockEntity.getAmplitudeRaw(),
                        blockEntity.getSpeedRaw(),
                        blockEntity.getRotationRaw(),
                        animTimeSec,
                        c1, c2, c3
                );

                renderLogTick++;
                if (renderLogTick % 120 == 0) {
                    TheatricalExtraLights.LOGGER.info(
                            "[LaserRenderer@{}] patternRaw={} selected={} sizeRaw={} ampRaw={} beams={} c1={} c2={} c3={}",
                            blockEntity.getBlockPos(), blockEntity.getPatternRaw(), pattern.name(),
                            blockEntity.getSizeRaw(), blockEntity.getAmplitudeRaw(), beams.size(),
                            Integer.toHexString(c1), Integer.toHexString(c2), Integer.toHexString(c3));
                }

                int focus = blockEntity.getFocus();
                float focusNorm = focus / 255f;
                float beamWidth = 0.008f + focusNorm * 0.018f;
                float baseLength = TheatricalExtraLightsConfig.getLaserBeamLength();
                float volCap = TheatricalExtraLightsConfig.getVolumetricBeamDistance();

                Vec3 baseOrigin = new Vec3(0.5F, 0.5F, 0.0F);
                if (isHanging) {
                    baseOrigin = new Vec3(baseOrigin.x, 1.0 - baseOrigin.y, baseOrigin.z);
                }

                float[] effLengths = new float[beams.size()];
                boolean[] beamHits = new boolean[beams.size()];
                for (int i = 0; i < beams.size(); i++) {
                    LaserBeam beam = beams.get(i);
                    float maxLen = baseLength * (beam.length / 32f);
                    boolean[] hit = new boolean[1];
                    effLengths[i] = raycastBeamLengthDebug(blockEntity, beam.yawDeg, beam.pitchDeg, maxLen, hit, null);
                    beamHits[i] = true;
                }

                Vec3[] currentEndpoints = new Vec3[beams.size()];
                for (int i = 0; i < beams.size(); i++) {
                    LaserBeam beam = beams.get(i);
                    currentEndpoints[i] = computeEndpoint(baseOrigin, beam.yawDeg, beam.pitchDeg, effLengths[i]);
                }

                int persistenceRaw = blockEntity.getPersistenceRaw();
                if (persistenceRaw > 0 && beams.size() > 1) {
                    float persistAlpha = (persistenceRaw / 255f) * intensity01 * 0.8f;
                    int loopLimit = pattern.isClosed() ? beams.size() : beams.size() - 1;
                    for (int i = 0; i < loopLimit; i++) {
                        int nextIdx = (i + 1) % beams.size();
                        Vec3 pA = currentEndpoints[i];
                        Vec3 pB = currentEndpoints[nextIdx];
                        int color = beams.get(i).color;
                        renderCurtain(beamConsumer, poseStack, baseOrigin, pA, pB, color, persistAlpha * 0.35f);
                        renderRibbon(beamConsumer, poseStack, pA, pB, beamWidth * 0.5f, color, persistAlpha);
                    }
                }

                for (int idx = 0; idx < beams.size(); idx++) {
                    if (!beamHits[idx]) continue;
                    LaserBeam beam = beams.get(idx);
                    renderOneBeam(beamConsumer, poseStack, baseOrigin,
                            beam.yawDeg, beam.pitchDeg, effLengths[idx], beamWidth,
                            beam.color, intensity01);
                }

                poseStack.popPose();
            }

            @Override
            public Vec3 getPos(float partialTick) {
                return blockEntity.getBlockPos().getCenter();
            }
        });
    }

    /** Halo visible sans noyer la salle. */
    private static final float LASER_HAZE_MIN = 0.11f;
    private static final float LASER_HAZE_MAX = 0.16f;
    private static final float LASER_SHEET_THICKNESS = 0.08f;
    private static final int MAX_VOLUMETRIC_RAYS = 12;

    private void submitLaserVolumes(LaserBlockEntity blockEntity, Direction facing, float partialTicks,
                                    boolean isFlipped, BlockState blockstate, boolean isHanging) {
        if (!TheatricalExtraLightsConfig.isVolumetricBeamEnabled()) {
            return;
        }
        float intensity = blockEntity.getPrevIntensity()
                + ((blockEntity.getIntensity() - blockEntity.getPrevIntensity()) * partialTicks);
        float intensity01 = Math.min(1.0f, intensity / 255f);
        if (intensity01 <= 0.0f) {
            return;
        }

        int c1 = blockEntity.getColour();
        int c2 = blockEntity.getColour2();
        int c3 = blockEntity.getColour3();
        if (c2 == 0) c2 = c1;
        if (c3 == 0) c3 = c2;

        double animTimeSec = (blockEntity.getLevel().getGameTime() + partialTicks) / 20.0;
        LaserPattern pattern = blockEntity.getPattern();
        List<LaserBeam> beams = pattern.generate(
                blockEntity.getSizeRaw(),
                blockEntity.getAmplitudeRaw(),
                blockEntity.getSpeedRaw(),
                blockEntity.getRotationRaw(),
                animTimeSec,
                c1, c2, c3
        );
        if (beams.isEmpty()) {
            return;
        }

        float focusNorm = blockEntity.getFocus() / 255f;
        float baseLength = TheatricalExtraLightsConfig.getLaserBeamLength();
        float volCap = TheatricalExtraLightsConfig.getVolumetricBeamDistance();
        float hazeRadius = LASER_HAZE_MIN + focusNorm * (LASER_HAZE_MAX - LASER_HAZE_MIN);

        Vec3 baseOrigin = new Vec3(0.5F, 0.5F, 0.0F);
        if (isHanging) {
            baseOrigin = new Vec3(baseOrigin.x, 1.0 - baseOrigin.y, baseOrigin.z);
        }

        int n = beams.size();
        int rayStep = n <= MAX_VOLUMETRIC_RAYS
                ? 1
                : Math.max(1, (n + MAX_VOLUMETRIC_RAYS - 1) / MAX_VOLUMETRIC_RAYS);
        int submitted = 0;
        int[] pick = new int[MAX_VOLUMETRIC_RAYS];
        float[] pickLen = new float[MAX_VOLUMETRIC_RAYS];
        for (int i = 0; i < n && submitted < MAX_VOLUMETRIC_RAYS; i += rayStep) {
            pick[submitted] = i;
            LaserBeam beam = beams.get(i);
            float maxLen = baseLength * (beam.length / 32f);
            boolean[] hit = new boolean[1];
            pickLen[submitted] = Math.min(
                    raycastBeamLengthDebug(blockEntity, beam.yawDeg, beam.pitchDeg, maxLen, hit, null),
                    volCap);
            submitted++;
        }

        for (int s = 0; s < submitted; s++) {
            LaserBeam beam = beams.get(pick[s]);
            PoseStack beamPose = new PoseStack();
            preparePoseStack(blockEntity, beamPose, facing, partialTicks, isFlipped, blockstate, isHanging);
            beamPose.translate(baseOrigin.x, baseOrigin.y, baseOrigin.z);
            beamPose.mulPose(Axis.YP.rotationDegrees(beam.yawDeg));
            beamPose.mulPose(Axis.XP.rotationDegrees(beam.pitchDeg));
            // Les spots volumétriques partent en -Z ; le laser historique part en +Z.
            beamPose.mulPose(Axis.YP.rotationDegrees(180.0F));

            submitVolumetricBeam(
                    blockEntity, beamPose, partialTicks,
                    0.05f, 0.05f,
                    GoboLibrary.MACVIP, 0, 0.0f,
                    1.0f, 1.0f, 0,
                    beam.color, intensity01 * 1.25f, hazeRadius, pickLen[s], true, true, false
            );
        }

        int persistenceRaw = blockEntity.getPersistenceRaw();
        if (persistenceRaw > 0 && submitted > 1) {
            float sheetIntensity = intensity01 * (persistenceRaw / 255f) * 0.40f;
            int sheetCount = pattern.isClosed() ? submitted : submitted - 1;
            for (int s = 0; s < sheetCount; s++) {
                int i0 = pick[s];
                int i1 = pick[(s + 1) % submitted];
                float sheetLen = Math.min(pickLen[s], pickLen[(s + 1) % submitted]);
                if (sheetLen <= 0.05f || sheetIntensity <= 0.001f) {
                    continue;
                }
                submitLaserSheet(blockEntity, facing, partialTicks, isFlipped, blockstate, isHanging,
                        baseOrigin, beams.get(i0), beams.get(i1), sheetLen, sheetIntensity);
            }
        }
    }

    private void submitLaserSheet(LaserBlockEntity blockEntity, Direction facing, float partialTicks,
                                  boolean isFlipped, BlockState blockstate, boolean isHanging,
                                  Vec3 baseOrigin, LaserBeam a, LaserBeam b,
                                  float sheetLen, float intensity) {
        Vec3 dirA = localBeamDir(a.yawDeg, a.pitchDeg);
        Vec3 dirB = localBeamDir(b.yawDeg, b.pitchDeg);
        Vec3 bisector = dirA.add(dirB);
        if (bisector.lengthSqr() < 1.0e-8) {
            return;
        }
        bisector = bisector.normalize();
        Vec3 planeN = dirA.cross(dirB);
        if (planeN.lengthSqr() < 1.0e-10) {
            return;
        }
        planeN = planeN.normalize();
        Vec3 inPlane = planeN.cross(bisector);
        if (inPlane.lengthSqr() < 1.0e-10) {
            return;
        }
        inPlane = inPlane.normalize();
        if (inPlane.dot(dirA.subtract(bisector)) < 0.0) {
            inPlane = inPlane.scale(-1.0);
        }

        double cosHalf = Math.max(-1.0, Math.min(1.0, dirA.dot(bisector)));
        float halfAngleDeg = (float) Math.toDegrees(Math.acos(cosHalf));
        if (halfAngleDeg < 0.8f) {
            return;
        }
        halfAngleDeg = Math.min(halfAngleDeg, 70.0f);

        PoseStack sheetPose = new PoseStack();
        preparePoseStack(blockEntity, sheetPose, facing, partialTicks, isFlipped, blockstate, isHanging);
        sheetPose.translate(baseOrigin.x, baseOrigin.y, baseOrigin.z);
        applyOrthonormalBasis(sheetPose, inPlane, planeN, bisector.scale(-1.0));

        submitVolumetricBeam(
                blockEntity, sheetPose, partialTicks,
                halfAngleDeg, halfAngleDeg,
                GoboLibrary.MACVIP, 0, 0.0f,
                1.0f, 1.0f, 0,
                a.color, intensity, LASER_SHEET_THICKNESS, sheetLen, true, false, true
        );
    }

    /** Direction +Z après yaw/pitch (espace lentille, avant le flip volumétrique). */
    private static Vec3 localBeamDir(float yawDeg, float pitchDeg) {
        PoseStack tmp = new PoseStack();
        tmp.mulPose(Axis.YP.rotationDegrees(yawDeg));
        tmp.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        Matrix4f m = tmp.last().pose();
        Vec3 dir = new Vec3(m.m20(), m.m21(), m.m22());
        if (dir.lengthSqr() < 1.0e-8) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        return dir.normalize();
    }

    /** Colonnes X/Y/Z du pose = axes fournis (espace lentille). */
    private static void applyOrthonormalBasis(PoseStack pose, Vec3 x, Vec3 y, Vec3 z) {
        Matrix4f basis = new Matrix4f();
        basis.m00((float) x.x);
        basis.m01((float) x.y);
        basis.m02((float) x.z);
        basis.m10((float) y.x);
        basis.m11((float) y.y);
        basis.m12((float) y.z);
        basis.m20((float) z.x);
        basis.m21((float) z.y);
        basis.m22((float) z.z);
        basis.m33(1.0f);
        pose.last().pose().mul(basis);
        pose.last().normal().mul(new Matrix3f(basis));
    }

    private void renderOneBeam(VertexConsumer beamConsumer, PoseStack poseStack, Vec3 baseOrigin,
                               float yawDeg, float pitchDeg, float length, float beamWidth,
                               int color, float alpha) {
        poseStack.pushPose();
        poseStack.translate(baseOrigin.x, baseOrigin.y, baseOrigin.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        renderLightBeam(beamConsumer, poseStack, alpha, beamWidth, length, color);
        poseStack.popPose();
    }

    /**
     * Cast a ray in world space along the beam direction, returning the effective
     * length until the first solid block hit (or {@code maxLen} if no hit). The
     * world direction is computed analytically from the laser's pan/tilt + per-beam
     * offsets, NOT from the pose stack matrix — the latter is camera-relative and
     * would give different results from different camera angles.
     */
    /** Hits within this distance of the laser are treated as the laser's own
     *  mounting structure (truss, frame, the laser block itself) and skipped.
     *  Real walls beyond this distance still block beams. */
    private static final float RAYCAST_SKIP_RADIUS = 2.5f;

    /**
     * @param hitOut single-element array; set to {@code true} if the ray actually
     *               hit a surface beyond the skip radius (real wall/floor) and
     *               {@code false} if it goes into open space (no surface to
     *               project the polyline onto). Caller can decide whether to
     *               draw the trace.
     */
    private float raycastBeamLengthDebug(LaserBlockEntity be, float yawDeg, float pitchDeg,
                                         float maxLen, boolean[] hitOut, String[] dbgOut) {
        hitOut[0] = false;
        if (dbgOut != null && dbgOut.length > 0) dbgOut[0] = null;
        if (be == null || be.getLevel() == null || maxLen <= 0.001f) return maxLen;
        Vec3 origin = be.getBlockPos().getCenter();
        Vec3 dir = getBeamWorldDir(be, yawDeg, pitchDeg);
        if (dir.lengthSqr() < 1e-8) return maxLen;
        Vec3 endWorld = origin.add(dir.scale(maxLen));

        // Iteratively skip:
        //   - hits within the mounting-structure radius (close truss/laser block)
        //   - hits on Theatrical/TheatricalExtraLights blocks at any distance (the
        //     whole rig can be made of these and shouldn't catch beams)
        // Stop only when we find a non-mod block beyond the skip radius.
        Vec3 rayStart = origin;
        int safety = 24;
        while (safety-- > 0) {
            BlockHitResult hit = be.getLevel().clip(new ClipContext(rayStart, endWorld,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (hit.getType() == HitResult.Type.MISS) {
                return maxLen;
            }
            BlockState hitState = be.getLevel().getBlockState(hit.getBlockPos());
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(hitState.getBlock());
            boolean isModBlock = key != null
                    && (key.getNamespace().equals("theatrical")
                        || key.getNamespace().equals("theatricalextralights"));
            boolean isPassThrough = key != null
                    && TheatricalExtraLightsConfig.isLaserPassThrough(key.toString());
            float dist = (float) hit.getLocation().distanceTo(origin);

            if (!isModBlock && !isPassThrough && dist >= RAYCAST_SKIP_RADIUS) {
                hitOut[0] = true;
                if (dbgOut != null && dbgOut.length > 0) {
                    Vec3 hl = hit.getLocation();
                    dbgOut[0] = "hit=" + (key == null ? "?" : key.toString())
                            + "@dist=" + String.format("%.1f", dist)
                            + " worldXYZ=(" + String.format("%.0f", hl.x)
                            + "," + String.format("%.0f", hl.y)
                            + "," + String.format("%.0f", hl.z) + ")";
                }
                return Math.min(maxLen, dist);
            }
            // Skip this hit (mod block, or close truss/mounting), continue past it.
            rayStart = hit.getLocation().add(dir.scale(0.01));
            if (rayStart.distanceToSqr(endWorld) < 1e-4) {
                return maxLen;
            }
        }
        return maxLen;
    }

    /**
     * Compute the world-space direction of a beam by replicating EXACTLY the
     * pose-stack rotation chain used by {@link #preparePoseStack} and
     * {@link #renderOneBeam}, applied to the local +Z forward vector. This
     * guarantees the raycast direction matches what's actually rendered
     * regardless of facing, hanging direction, flipped state, pan, or tilt.
     */
    private static Vec3 getBeamWorldDir(LaserBlockEntity be, float beamYawDeg, float beamPitchDeg) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(dev.imabad.theatrical.blocks.light.BaseLightBlock.FACING);
        Direction hangDir = Direction.UP;
        boolean isHanging = false;
        try {
            hangDir = state.getValue(dev.imabad.theatrical.blocks.HangableBlock.HANG_DIRECTION);
            isHanging = state.getValue(dev.imabad.theatrical.blocks.HangableBlock.HANGING);
        } catch (Exception ignored) {}
        boolean isFlipped = be.isUpsideDown();
        float pan = be.getPan();
        float tilt = be.getTilt();

        PoseStack temp = new PoseStack();

        // ---- Mirror preparePoseStack rotations (translates omitted — they
        // don't affect direction). The pose calls right-multiply, and vertex
        // transforms apply in REVERSE-push order, but that's exactly what
        // we want when feeding a pure +Z vertex in at the end. ----
        if (isHanging && hangDir.getAxis() != Direction.Axis.Y) {
            if (hangDir.getAxis() == Direction.Axis.Z) {
                temp.mulPose(Axis.ZP.rotationDegrees(90));
                if (hangDir == Direction.SOUTH) {
                    temp.mulPose(Axis.XP.rotationDegrees(-90));
                } else {
                    temp.mulPose(Axis.XP.rotationDegrees(90));
                }
            } else {
                if (hangDir == Direction.EAST) {
                    temp.mulPose(Axis.ZN.rotationDegrees(-90));
                } else {
                    temp.mulPose(Axis.ZN.rotationDegrees(90));
                }
            }
        }
        temp.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
        if (isFlipped) {
            temp.mulPose(Axis.ZP.rotationDegrees(180));
        }
        temp.mulPose(Axis.YP.rotationDegrees(pan));
        if (isFlipped) {
            temp.mulPose(Axis.XP.rotationDegrees(-180));
        } else {
            temp.mulPose(Axis.XP.rotationDegrees(180));
        }
        temp.mulPose(Axis.XP.rotationDegrees(tilt));

        // ---- Beam-local rotations (renderOneBeam) ----
        temp.mulPose(Axis.YP.rotationDegrees(beamYawDeg));
        temp.mulPose(Axis.XP.rotationDegrees(beamPitchDeg));

        // Apply the composed matrix to the model +Z vector.
        Matrix4f m = temp.last().pose();
        org.joml.Vector4f v = new org.joml.Vector4f(0f, 0f, 1f, 0f);
        v.mul(m);
        Vec3 dir = new Vec3(v.x, v.y, v.z);
        if (dir.lengthSqr() < 1e-8) return Vec3.ZERO;
        return dir.normalize();
    }

    /**
     * Compute the 3D endpoint of a beam in fixture-local space, given (yaw, pitch, length).
     * Mirrors the rotation chain in {@link #renderOneBeam} (translate baseOrigin, rotate Y, rotate X,
     * extend +Z by length).
     */
    private Vec3 computeEndpoint(Vec3 baseOrigin, float yawDeg, float pitchDeg, float length) {
        double y = Math.toRadians(yawDeg);
        double p = Math.toRadians(pitchDeg);
        double cy = Math.cos(y), sy = Math.sin(y);
        double cp = Math.cos(p), sp = Math.sin(p);
        double x = length * sy * cp;
        double yc = -length * sp;
        double z = length * cy * cp;
        return new Vec3(baseOrigin.x + x, baseOrigin.y + yc, baseOrigin.z + z);
    }

    /**
     * Draw a thin 4-sided ribbon (tube) connecting two 3D points in fixture-local space.
     * The current pose stack is used as-is (no extra transform); world transform is
     * already applied by preparePoseStack on the caller's side.
     */
    private void renderRibbon(VertexConsumer builder, PoseStack stack,
                              Vec3 a, Vec3 b, float halfWidth, int color, float alpha) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-8) return;
        dir = dir.normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize().scale(halfWidth);
        Vec3 perp = right.cross(dir).normalize().scale(halfWidth);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int bC = color & 0xFF;
        int aV = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);

        Matrix4f m = stack.last().pose();
        Matrix3f normal = stack.last().normal();

        float ax1 = (float) (a.x + right.x), ay1 = (float) (a.y + right.y), az1 = (float) (a.z + right.z);
        float ax2 = (float) (a.x - right.x), ay2 = (float) (a.y - right.y), az2 = (float) (a.z - right.z);
        float ax3 = (float) (a.x + perp.x),  ay3 = (float) (a.y + perp.y),  az3 = (float) (a.z + perp.z);
        float ax4 = (float) (a.x - perp.x),  ay4 = (float) (a.y - perp.y),  az4 = (float) (a.z - perp.z);
        float bx1 = (float) (b.x + right.x), by1 = (float) (b.y + right.y), bz1 = (float) (b.z + right.z);
        float bx2 = (float) (b.x - right.x), by2 = (float) (b.y - right.y), bz2 = (float) (b.z - right.z);
        float bx3 = (float) (b.x + perp.x),  by3 = (float) (b.y + perp.y),  bz3 = (float) (b.z + perp.z);
        float bx4 = (float) (b.x - perp.x),  by4 = (float) (b.y - perp.y),  bz4 = (float) (b.z - perp.z);

        // 4 sides forming a thin rectangular tube around the segment
        addVertex(builder, m, normal, r, g, bC, aV, ax1, ay1, az1);
        addVertex(builder, m, normal, r, g, bC, aV, ax3, ay3, az3);
        addVertex(builder, m, normal, r, g, bC, aV, bx3, by3, bz3);
        addVertex(builder, m, normal, r, g, bC, aV, bx1, by1, bz1);

        addVertex(builder, m, normal, r, g, bC, aV, ax3, ay3, az3);
        addVertex(builder, m, normal, r, g, bC, aV, ax2, ay2, az2);
        addVertex(builder, m, normal, r, g, bC, aV, bx2, by2, bz2);
        addVertex(builder, m, normal, r, g, bC, aV, bx3, by3, bz3);

        addVertex(builder, m, normal, r, g, bC, aV, ax2, ay2, az2);
        addVertex(builder, m, normal, r, g, bC, aV, ax4, ay4, az4);
        addVertex(builder, m, normal, r, g, bC, aV, bx4, by4, bz4);
        addVertex(builder, m, normal, r, g, bC, aV, bx2, by2, bz2);

        addVertex(builder, m, normal, r, g, bC, aV, ax4, ay4, az4);
        addVertex(builder, m, normal, r, g, bC, aV, ax1, ay1, az1);
        addVertex(builder, m, normal, r, g, bC, aV, bx1, by1, bz1);
        addVertex(builder, m, normal, r, g, bC, aV, bx4, by4, bz4);
    }

    private void renderCurtain(VertexConsumer builder, PoseStack stack, Vec3 origin, Vec3 a, Vec3 b, int color, float alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int bC = color & 0xFF;
        int aV = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);

        if (aV <= 0) return;

        Matrix4f m = stack.last().pose();
        Matrix3f normal = stack.last().normal();

        float ox = (float) origin.x, oy = (float) origin.y, oz = (float) origin.z;
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z;
        float bx = (float) b.x, by = (float) b.y, bz = (float) b.z;

        // Cara Frontal (Origen -> A -> B -> Origen para cerrar el Quad)
        addVertex(builder, m, normal, r, g, bC, aV, ox, oy, oz);
        addVertex(builder, m, normal, r, g, bC, aV, ax, ay, az);
        addVertex(builder, m, normal, r, g, bC, aV, bx, by, bz);
        addVertex(builder, m, normal, r, g, bC, aV, ox, oy, oz);

        // Cara Trasera (Origen -> B -> A -> Origen) garantiza que sea visible de ambos lados
        addVertex(builder, m, normal, r, g, bC, aV, ox, oy, oz);
        addVertex(builder, m, normal, r, g, bC, aV, bx, by, bz);
        addVertex(builder, m, normal, r, g, bC, aV, ax, ay, az);
        addVertex(builder, m, normal, r, g, bC, aV, ox, oy, oz);
    }

    private void renderLightBeam(VertexConsumer builder, PoseStack stack, float alpha, float beamSize, float length, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        Matrix4f m = stack.last().pose();
        Matrix3f normal = stack.last().normal();

        // Beam splits into two segments: full opacity for the first 80% of
        // the length, then a linear fade from 100% → 0% alpha over the last
        // 20%. Thickness stays uniform so the cone keeps a clean shape.
        float zMid = length * 0.8f;
        float s = beamSize;

        // Segment 1: [0 .. 0.8 length], uniform full alpha
        // R face
        addVertex(builder, m, normal, r, g, b, a,  s,  s, zMid);
        addVertex(builder, m, normal, r, g, b, a,  s,  s, 0);
        addVertex(builder, m, normal, r, g, b, a,  s, -s, 0);
        addVertex(builder, m, normal, r, g, b, a,  s, -s, zMid);
        // L face
        addVertex(builder, m, normal, r, g, b, a, -s, -s, zMid);
        addVertex(builder, m, normal, r, g, b, a, -s, -s, 0);
        addVertex(builder, m, normal, r, g, b, a, -s,  s, 0);
        addVertex(builder, m, normal, r, g, b, a, -s,  s, zMid);
        // Top face
        addVertex(builder, m, normal, r, g, b, a, -s,  s, zMid);
        addVertex(builder, m, normal, r, g, b, a, -s,  s, 0);
        addVertex(builder, m, normal, r, g, b, a,  s,  s, 0);
        addVertex(builder, m, normal, r, g, b, a,  s,  s, zMid);
        // Down face
        addVertex(builder, m, normal, r, g, b, a,  s, -s, zMid);
        addVertex(builder, m, normal, r, g, b, a,  s, -s, 0);
        addVertex(builder, m, normal, r, g, b, a, -s, -s, 0);
        addVertex(builder, m, normal, r, g, b, a, -s, -s, zMid);

        // Segment 2: [0.8 length .. length], alpha fades to 0
        int aEnd = 0;
        // R face
        addVertex(builder, m, normal, r, g, b, aEnd,  s,  s, length);
        addVertex(builder, m, normal, r, g, b, a,    s,  s, zMid);
        addVertex(builder, m, normal, r, g, b, a,    s, -s, zMid);
        addVertex(builder, m, normal, r, g, b, aEnd,  s, -s, length);
        // L face
        addVertex(builder, m, normal, r, g, b, aEnd, -s, -s, length);
        addVertex(builder, m, normal, r, g, b, a,   -s, -s, zMid);
        addVertex(builder, m, normal, r, g, b, a,   -s,  s, zMid);
        addVertex(builder, m, normal, r, g, b, aEnd, -s,  s, length);
        // Top face
        addVertex(builder, m, normal, r, g, b, aEnd, -s,  s, length);
        addVertex(builder, m, normal, r, g, b, a,   -s,  s, zMid);
        addVertex(builder, m, normal, r, g, b, a,    s,  s, zMid);
        addVertex(builder, m, normal, r, g, b, aEnd,  s,  s, length);
        // Down face
        addVertex(builder, m, normal, r, g, b, aEnd,  s, -s, length);
        addVertex(builder, m, normal, r, g, b, a,    s, -s, zMid);
        addVertex(builder, m, normal, r, g, b, a,   -s, -s, zMid);
        addVertex(builder, m, normal, r, g, b, aEnd, -s, -s, length);
    }

    @Override
    public void preparePoseStack(LaserBlockEntity blockEntity, PoseStack poseStack, Direction facing, float partialTicks, boolean isFlipped, BlockState blockState, boolean isHanging) {
        FixtureMountTransform.apply(poseStack, blockEntity);
        poseStack.translate(0.5F, 0, .5F);
        if (isHanging) {
            Direction hangDirection = blockState.getValue(HangableBlock.HANG_DIRECTION);
            poseStack.translate(0, 0.5, 0F);
            if (hangDirection.getAxis() != Direction.Axis.Y) {
                if (hangDirection.getAxis() == Direction.Axis.Z) {
                    if (hangDirection == Direction.SOUTH) {
                        poseStack.mulPose(Axis.ZP.rotationDegrees(90));
                        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.ZP.rotationDegrees(90));
                        poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    }
                } else {
                    if (hangDirection == Direction.EAST) {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(-90));
                    } else {
                        poseStack.mulPose(Axis.ZN.rotationDegrees(90));
                    }
                }
            }
            poseStack.translate(0, -0.5, 0F);
        }
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
    }
}
