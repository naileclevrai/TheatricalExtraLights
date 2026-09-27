package com.github.dumann089.theatricalextralights.client;

import com.github.dumann089.theatricalextralights.blockentities.Flow2JetBlockEntity;
import com.github.dumann089.theatricalextralights.client.blockentities.Flow2JetRenderer;
import com.github.dumann089.theatricalextralights.client.particle.Flow2JetDissipation;
import com.github.dumann089.theatricalextralights.client.particle.Flow2JetParticleSpawner;
import com.github.dumann089.theatricalextralights.client.render.pyro.Co2PlumeRenderer;
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
    /** flow2jet.ogg est normalise a -1.7 dB crete : volume plein a pleine pression. */
    private static final float LOOP_VOLUME_MIN = 0.55f;
    private static final float LOOP_VOLUME_RANGE = 0.45f;
    private static final Map<BlockPos, Boolean> WAS_ACTIVE = new ConcurrentHashMap<>();
    /** Ticks depuis l'ouverture de la vanne, pour le coup de pression du depart (particules). */
    private static final Map<BlockPos, Integer> ACTIVE_TICKS = new ConcurrentHashMap<>();
    /** Etat du panache volumetrique par machine : quand la vanne s'est ouverte, puis fermee. */
    private static final Map<BlockPos, PlumeState> PLUMES = new ConcurrentHashMap<>();
    /** Vanne fermee : le nuage lache se dilue et disparait en trois secondes et demie. */
    private static final float DISSIPATE_TICKS = 70f;

    private Flow2JetClientEffects() {
    }

    /** Bouche de la buse et axe du jet en coordonnees monde, d'apres la pose du modele. */
    private record JetPose(Vec3 nozzle, Vector3f axis) {
    }

    private static JetPose jetPose(Flow2JetBlockEntity blockEntity, float partialTick) {
        BlockPos pos = blockEntity.getBlockPos();
        Matrix4f modelPose = Flow2JetRenderer.modelPose(blockEntity, partialTick);
        float[] mouth = blockEntity.getFixture().getBeamStartPosition();
        Vector4f nozzleLocal = modelPose.transform(new Vector4f(mouth[0], mouth[1], mouth[2], 1f));
        Vec3 nozzle = new Vec3(pos.getX() + nozzleLocal.x, pos.getY() + nozzleLocal.y, pos.getZ() + nozzleLocal.z);
        Vector4f axisLocal = modelPose.transform(new Vector4f(0f, 1f, 0f, 0f));
        Vector3f axis = new Vector3f(axisLocal.x, axisLocal.y, axisLocal.z);
        if (axis.lengthSquared() < 1.0e-8f) {
            axis.set(0f, 1f, 0f);
        } else {
            axis.normalize();
        }
        return new JetPose(nozzle, axis);
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

        long gameTime = level.getGameTime();
        JetPose jet = jetPose(blockEntity, minecraft.getFrameTime());

        if (active) {
            PlumeState plume = PLUMES.computeIfAbsent(pos, key -> new PlumeState());
            if (plume.openTick < 0 || plume.closeTick >= 0) {
                plume.openTick = gameTime;
                plume.closeTick = -1;
            }
            plume.pressure = intensityFactor;

            Flow2JetDissipation.markRunning(pos);
            WAS_ACTIVE.put(pos, true);

            if (!FireworkRenderDistances.isWithinClientFlameRange(jet.nozzle().x, jet.nozzle().y, jet.nozzle().z)) {
                return;
            }
            // Particules seulement quand le panache volumetrique n'est pas disponible (Iris, shader absent).
            if (!Co2PlumeRenderer.available()) {
                int ticksActive = ACTIVE_TICKS.compute(pos, (key, ticks) -> ticks == null ? 0 : Math.min(ticks + 1, 1_000_000));
                Flow2JetParticleSpawner.spawnJet(level, jet.nozzle(), jet.axis(), (int) blockEntity.getIntensity(), ticksActive, level.random);
            }
        } else {
            PlumeState plume = PLUMES.get(pos);
            if (plume != null && plume.closeTick < 0) {
                plume.closeTick = gameTime;
            }
            ACTIVE_TICKS.remove(pos);
            boolean wasPumping = Boolean.TRUE.equals(WAS_ACTIVE.get(pos))
                    || blockEntity.getPrevIntensity() > 0;
            if (wasPumping) {
                WAS_ACTIVE.put(pos, false);
                if (!Flow2JetDissipation.isDissipating(pos)) {
                    Flow2JetDissipation.beginStop(pos, jet.nozzle(), jet.axis(), gameTime);
                }
            }
        }
    }

    /**
     * Soumet le panache volumetrique pour cette image. Appele par le renderer du bloc a chaque
     * frame : le front avance avec l'age du jet, la coupure remonte depuis la buse une fois la
     * vanne fermee, et l'etat est oublie quand la coupure a depasse le front.
     */
    public static void submitPlume(Flow2JetBlockEntity blockEntity, float partialTick) {
        BlockPos pos = blockEntity.getBlockPos();
        PlumeState plume = PLUMES.get(pos);
        if (plume == null || plume.openTick < 0 || !(blockEntity.getLevel() instanceof ClientLevel level)) {
            return;
        }
        if (!Co2PlumeRenderer.available()) {
            return;
        }
        double now = level.getGameTime() + (double) partialTick;
        float age = (float) Math.max(0.0, now - plume.openTick);
        float length = Flow2JetParticleSpawner.plumeLength(plume.pressure, age);
        float cutFront = -1f;
        float dissipate = 0f;
        // Horloge du gaz : le temps ecoule, qui ralentit une fois la vanne fermee (le gaz lache freine).
        float flowClock = age;
        if (plume.closeTick >= 0) {
            float sinceClose = (float) Math.max(0.0, now - plume.closeTick);
            cutFront = Flow2JetParticleSpawner.CUT_SPEED * sinceClose;
            dissipate = Math.min(1f, sinceClose / DISSIPATE_TICKS);
            float openDuration = (float) Math.max(0.0, plume.closeTick - plume.openTick);
            flowClock = openDuration + sinceClose * (1f - 0.8f * dissipate);
            // On n'oublie le panache qu'une fois sa densite a zero : la coupure seule laisserait une
            // tete encore visible disparaitre d'un coup.
            if (dissipate >= 1f) {
                PLUMES.remove(pos);
                return;
            }
        }
        JetPose jet = jetPose(blockEntity, partialTick);
        if (!FireworkRenderDistances.isWithinClientFlameRange(jet.nozzle().x, jet.nozzle().y, jet.nozzle().z)) {
            return;
        }
        Co2PlumeRenderer.submit(jet.nozzle(), jet.axis(), length, cutFront, plume.pressure, dissipate, flowClock,
                Flow2JetParticleSpawner.exitSpeed(plume.pressure));
    }

    public static void stop(BlockPos pos) {
        WAS_ACTIVE.remove(pos);
        ACTIVE_TICKS.remove(pos);
        PLUMES.remove(pos);
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

    private static final class PlumeState {
        long openTick = -1;
        long closeTick = -1;
        float pressure = 1f;
    }
}
