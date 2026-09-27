package com.github.dumann089.theatricalextralights.client.particle;

import com.github.dumann089.theatricalextralights.firework.FireworkRenderDistances;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Brouillard CO2 : une bouffee de gaz froid poussee par le jet, freinee par l'air, qui grossit en se
 * diluant puis retombe un peu (le CO2 est plus lourd que l'air). Partage par la colonne dense du
 * coeur et les volutes du panache.
 */
@Environment(EnvType.CLIENT)
abstract class Co2FogParticle extends TextureSheetParticle {
    /** La fumee est eclairee par la scene : jamais noire dans une salle sombre. */
    private static final int MIN_BLOCK_LIGHT = 11;
    /** Fondu quand la camera entre dans une volute, sinon les sprites coupes par le plan proche clignotent. */
    private static final float CAMERA_FADE_START = 0.3f;
    private static final float CAMERA_FADE_LENGTH = 1.1f;
    private static final float SINK = 0.0010f;
    /** Contre une surface, la fumee s'etale et se dilue vite. */
    private static final float SURFACE_FADE = 0.88f;

    private final float startSize;
    private final float peakSize;
    private final float baseAlpha;
    private final float drag;
    private final float turbulence;
    private final float spin;
    private boolean collided;
    private float surfaceFade = 1f;

    protected Co2FogParticle(
            ClientLevel level,
            Vec3 pos,
            Vec3 velocity,
            SpriteSet sprites,
            RandomSource random,
            float startSize,
            float peakSize,
            float baseAlpha,
            float drag,
            float turbulence,
            int lifetime
    ) {
        super(level, pos.x, pos.y, pos.z);
        xd = velocity.x;
        yd = velocity.y;
        zd = velocity.z;
        hasPhysics = true;
        gravity = 0f;
        this.lifetime = Math.max(2, lifetime);
        float distanceScale = FireworkRenderDistances.flameParticleSizeScale(pos.x, pos.y, pos.z);
        this.startSize = startSize * distanceScale;
        this.peakSize = peakSize * distanceScale;
        this.baseAlpha = baseAlpha;
        this.drag = drag;
        this.turbulence = turbulence;
        quadSize = this.startSize;
        alpha = 0f;
        float tint = 0.94f + random.nextFloat() * 0.06f;
        rCol = tint;
        gCol = tint;
        bCol = Math.min(1f, tint + 0.03f);
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        spin = (random.nextFloat() - 0.5f) * 0.06f;
        pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ExtraLightsRenderTypes.co2JetRenderType();
    }

    @Override
    public int getLightColor(float partialTick) {
        int packed = super.getLightColor(partialTick);
        return LightTexture.pack(Math.max(LightTexture.block(packed), MIN_BLOCK_LIGHT), LightTexture.sky(packed));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float life = (float) age / (float) lifetime;

        xd *= drag;
        yd *= drag;
        zd *= drag;
        float jitter = turbulence * (0.35f + life);
        xd += (random.nextFloat() - 0.5f) * jitter;
        yd += (random.nextFloat() - 0.5f) * jitter;
        zd += (random.nextFloat() - 0.5f) * jitter;
        yd -= SINK * life;
        move(xd, yd, zd);
        if (collided) {
            xd *= 0.82;
            zd *= 0.82;
            surfaceFade *= SURFACE_FADE;
        }

        roll += spin * (1f - 0.5f * life);
        float grow = 1f - (1f - life) * (1f - life);
        quadSize = Mth.lerp(grow, startSize, peakSize);
        float fadeIn = Math.min(1f, age / 2f);
        float fadeOut = 1f;
        if (life > 0.45f) {
            float t = (life - 0.45f) / 0.55f;
            fadeOut = 1f - t * t;
        }
        alpha = baseAlpha * fadeIn * fadeOut * surfaceFade;

        long gameTime = level.getGameTime();
        float dissipation = Flow2JetDissipation.alphaMultiplier(x, y, z, gameTime);
        if (dissipation < 1f) {
            alpha *= dissipation;
            float damp = Flow2JetDissipation.motionDamping(dissipation);
            xd *= damp;
            yd *= damp;
            zd *= damp;
            if (dissipation < 0.1f) {
                remove();
                return;
            }
        }
        if (age > 3 && alpha < 0.006f) {
            remove();
        }
    }

    /**
     * Collision blocs sans le gel vanilla : une particule qui bute verticalement reste libre de
     * s'etaler le long de la surface au lieu de se figer sur place.
     */
    @Override
    public void move(double moveX, double moveY, double moveZ) {
        double wantX = moveX;
        double wantY = moveY;
        double wantZ = moveZ;
        if (hasPhysics && (moveX != 0.0 || moveY != 0.0 || moveZ != 0.0)) {
            Vec3 allowed = Entity.collideBoundingBox(null, new Vec3(moveX, moveY, moveZ), getBoundingBox(), level, List.of());
            moveX = allowed.x;
            moveY = allowed.y;
            moveZ = allowed.z;
        }
        if (moveX != 0.0 || moveY != 0.0 || moveZ != 0.0) {
            setBoundingBox(getBoundingBox().move(moveX, moveY, moveZ));
            setLocationFromBoundingbox();
        }
        onGround = wantY != moveY && wantY < 0.0;
        if (wantX != moveX) {
            xd = 0.0;
            collided = true;
        }
        if (wantY != moveY) {
            yd = 0.0;
            collided = true;
        }
        if (wantZ != moveZ) {
            zd = 0.0;
            collided = true;
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        double dx = Mth.lerp(partialTicks, xo, x) - cam.x;
        double dy = Mth.lerp(partialTicks, yo, y) - cam.y;
        double dz = Mth.lerp(partialTicks, zo, z) - cam.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float fade = Mth.clamp((distance - CAMERA_FADE_START) / CAMERA_FADE_LENGTH, 0f, 1f);
        if (fade <= 0f) {
            return;
        }
        float saved = alpha;
        alpha = saved * fade;
        super.render(buffer, camera, partialTicks);
        alpha = saved;
    }
}
