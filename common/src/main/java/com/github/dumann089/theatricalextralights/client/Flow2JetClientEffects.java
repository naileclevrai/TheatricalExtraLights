package com.github.dumann089.theatricalextralights.client;

import com.github.dumann089.theatricalextralights.blockentities.Flow2JetBlockEntity;
import com.github.dumann089.theatricalextralights.client.blockentities.Flow2JetRenderer;
import com.github.dumann089.theatricalextralights.client.particle.Flow2JetDissipation;
import com.github.dumann089.theatricalextralights.client.particle.Flow2JetParticleSpawner;
import com.github.dumann089.theatricalextralights.client.sfx.FixtureLoopSfx;
import com.github.dumann089.theatricalextralights.firework.FireworkRenderDistances;
import com.github.dumann089.theatricalextralights.sounds.ModSounds;
import com.github.dumann089.theatricalextralights.util.FixtureJetDirection;
import dev.imabad.theatrical.TheatricalClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class Flow2JetClientEffects {
    private static final double HEAR_DISTANCE = 48.0;
    private static final double HEAR_DISTANCE_SQ = HEAR_DISTANCE * HEAR_DISTANCE;
    /** Volume très bas — le fichier co2.ogg est très fort à la source. */
    private static final float LOOP_VOLUME_MIN = 0.006f;
    private static final float LOOP_VOLUME_RANGE = 0.009f;
    private static final Map<BlockPos, Boolean> WAS_ACTIVE = new ConcurrentHashMap<>();

    private Flow2JetClientEffects() {
    }

    public static void tick(Flow2JetBlockEntity blockEntity) {
        boolean active = blockEntity.getIntensity() > 0;
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 center = pos.getCenter();

        boolean playerCanHear = minecraft.player != null
                && minecraft.player.distanceToSqr(center) <= HEAR_DISTANCE_SQ;
        float intensityFactor = FixtureJetDirection.intensityFactor((int) blockEntity.getIntensity());
        float volume = LOOP_VOLUME_MIN + LOOP_VOLUME_RANGE * intensityFactor;
        boolean shouldPlaySound = active && playerCanHear;

        if (shouldPlaySound) {
            FixtureLoopSfx.sustain(
                    blockEntity.getLevel(),
                    pos,
                    ModSounds.FLOW2JET_LOOP.get(),
                    volume,
                    1.0f
            );
        } else {
            FixtureLoopSfx.release(pos);
        }

        if (!(blockEntity.getLevel() instanceof ClientLevel level)) {
            return;
        }

        // Buse et axe du jet : la matrice qui dessine le modele, appliquee a la bouche de la buse
        // et a l'axe +Y Blockbench. Pas de re-derivation a la main du facing / pan / tilt.
        float partial = minecraft.getFrameTime();
        Matrix4f modelPose = Flow2JetRenderer.modelPose(blockEntity, partial);
        float[] mouth = blockEntity.getFixture().getBeamStartPosition();
        Vector4f nozzleLocal = modelPose.transform(new Vector4f(mouth[0], mouth[1], mouth[2], 1f));
        Vec3 nozzle = new Vec3(pos.getX() + nozzleLocal.x, pos.getY() + nozzleLocal.y, pos.getZ() + nozzleLocal.z);
        Vector4f axis = modelPose.transform(new Vector4f(0f, 1f, 0f, 0f));
        Vector3f jetDirection = new Vector3f(axis.x, axis.y, axis.z);
        if (jetDirection.lengthSquared() < 1.0e-8f) {
            jetDirection.set(0f, 1f, 0f);
        } else {
            jetDirection.normalize();
        }

        if (active) {
            Flow2JetDissipation.markRunning(pos);
            WAS_ACTIVE.put(pos, true);

            if (!FireworkRenderDistances.isWithinClientFlameRange(nozzle.x, nozzle.y, nozzle.z)) {
                return;
            }

            Flow2JetParticleSpawner.spawnJet(level, nozzle, jetDirection, (int) blockEntity.getIntensity(), level.random);
        } else {
            boolean wasPumping = Boolean.TRUE.equals(WAS_ACTIVE.get(pos))
                    || blockEntity.getPrevIntensity() > 0;
            if (wasPumping) {
                WAS_ACTIVE.put(pos, false);
                if (!Flow2JetDissipation.isDissipating(pos)) {
                    Flow2JetDissipation.beginStop(pos, nozzle, jetDirection, level.getGameTime());
                }
            }
        }
    }

    public static void stop(BlockPos pos) {
        WAS_ACTIVE.remove(pos);
        Flow2JetDissipation.clear(pos);
        FixtureLoopSfx.release(pos);
    }

    /** Client-only debug overlay toggle — never call from the dedicated server. */
    public static void toggleDebugOverlay(BlockPos pos) {
        if (TheatricalClient.DEBUG_BLOCKS.contains(pos)) {
            TheatricalClient.DEBUG_BLOCKS.remove(pos);
        } else {
            TheatricalClient.DEBUG_BLOCKS.add(pos);
        }
    }

    public static void onBlockRemoved(BlockPos pos) {
        TheatricalClient.DEBUG_BLOCKS.remove(pos);
        stop(pos);
    }
}
