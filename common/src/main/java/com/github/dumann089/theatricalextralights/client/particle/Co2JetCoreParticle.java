package com.github.dumann089.theatricalextralights.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Coeur du jet : la colonne dense et serree des premiers metres, blanche et nette, vite dissoute. */
@Environment(EnvType.CLIENT)
public class Co2JetCoreParticle extends Co2FogParticle {
    private static final float DRAG = 0.905f;
    private static final float TURBULENCE = 0.004f;

    private static SpriteSet spriteSet;

    /** @param pressure 0..1, intensite DMX. */
    Co2JetCoreParticle(ClientLevel level, Vec3 pos, Vec3 velocity, RandomSource random, float pressure) {
        super(level, pos, velocity, spriteSet, random,
                0.05f + random.nextFloat() * 0.03f,
                (0.16f + random.nextFloat() * 0.08f) * (0.8f + 0.2f * pressure),
                0.28f + random.nextFloat() * 0.08f,
                DRAG,
                TURBULENCE,
                5 + random.nextInt(4));
    }

    static boolean ready() {
        return spriteSet != null;
    }

    @Environment(EnvType.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double dirX,
                double dirY,
                double dirZ
        ) {
            return new Co2JetCoreParticle(level, new Vec3(x, y, z), new Vec3(dirX, dirY, dirZ), level.random, 1f);
        }
    }
}
