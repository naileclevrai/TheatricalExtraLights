package com.github.dumann089.theatricalextralights.client.particle;

import com.github.dumann089.theatricalextralights.firework.FireworkRenderDistances;
import com.github.dumann089.theatricalextralights.particle.ModParticle;
import com.github.dumann089.theatricalextralights.util.FixtureJetDirection;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class Flow2JetParticleSpawner {
    private static final int BASE_COUNT = 14;
    private static final int EXTRA_VARIANCE = 8;
    private static final float CONE_DEGREES = 6.5f;

    private Flow2JetParticleSpawner() {
    }

    /**
     * @param nozzle       bouche de la buse en coordonnees monde (pose du modele deja appliquee)
     * @param jetDirection axe du jet, unitaire, en coordonnees monde
     */
    public static void spawnJet(ClientLevel level, Vec3 nozzle, Vector3f jetDirection, int intensity, RandomSource random) {
        if (intensity <= 0) {
            return;
        }

        if (!FireworkRenderDistances.isWithinClientFlameRange(nozzle.x, nozzle.y, nozzle.z)) {
            return;
        }

        float intensityFactor = FixtureJetDirection.intensityFactor(intensity);
        int count = Math.max(5, Math.round((BASE_COUNT + random.nextInt(EXTRA_VARIANCE + 1)) * intensityFactor));
        float speed = (0.17f + 0.08f * intensityFactor) + random.nextFloat() * 0.04f;

        for (int i = 0; i < count; i++) {
            Vec3 spawn = nozzle.add(Co2SmokePhysics.randomDisk(jetDirection, random, 0.018f + random.nextFloat() * 0.022f));
            float spread = CONE_DEGREES * (0.75f + random.nextFloat() * 0.5f);
            Vec3 velocity = Co2SmokePhysics.randomUnitCone(jetDirection, spread, random).scale(speed);

            level.addParticle(
                    ModParticle.CO2_JET_PUFF.get(),
                    true,
                    spawn.x,
                    spawn.y,
                    spawn.z,
                    velocity.x,
                    velocity.y,
                    velocity.z
            );
        }
    }
}
