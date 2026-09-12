package com.github.dumann089.theatricalextralights.client.render.beam;

import com.github.dumann089.theatricalextralights.util.FramingShutterState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record BeamRenderData(
        BlockPos fixturePos,
        Vec3 origin,
        Vec3 beamDir,
        Vec3 axisU,
        Vec3 axisV,
        float zoomNorm,
        float scanLen,
        float tanHalfAngle,
        int color,
        float intensity,
        ResourceLocation goboTexture,
        ResourceLocation nextGoboTexture,
        float goboRotation,
        float wheelTransition,
        Level level,
        float widthScale,
        float heightScale,
        float baseRadius,
        /** Etat des couteaux (null = pas de module ou lames sorties). */
        FramingShutterState.Snapshot shutters
) {
    /** Constructeur sans couteaux (compatibilite avec les renderers existants). */
    public BeamRenderData(
            BlockPos fixturePos,
            Vec3 origin,
            Vec3 beamDir,
            Vec3 axisU,
            Vec3 axisV,
            float zoomNorm,
            float scanLen,
            float tanHalfAngle,
            int color,
            float intensity,
            ResourceLocation goboTexture,
            ResourceLocation nextGoboTexture,
            float goboRotation,
            float wheelTransition,
            Level level,
            float widthScale,
            float heightScale,
            float baseRadius
    ) {
        this(fixturePos, origin, beamDir, axisU, axisV, zoomNorm, scanLen, tanHalfAngle,
                color, intensity, goboTexture, nextGoboTexture, goboRotation, wheelTransition,
                level, widthScale, heightScale, baseRadius, null);
    }

    /** Constructor de compatibilidad actualizado */
    public BeamRenderData(
            BlockPos fixturePos,
            Vec3 origin,
            Vec3 beamDir,
            Vec3 axisU,
            Vec3 axisV,
            float zoomNorm,
            float scanLen,
            float tanHalfAngle,
            int color,
            float intensity,
            ResourceLocation goboTexture,
            float goboRotation,
            Level level,
            float widthScale,
            float heightScale
    ) {
        this(fixturePos, origin, beamDir, axisU, axisV,
                zoomNorm, scanLen, tanHalfAngle,
                color, intensity, goboTexture, goboTexture, goboRotation, 0.0f, level,
                widthScale, heightScale, 0.05f, null);
    }

    /** Copie avec l'etat couteaux fourni. */
    public BeamRenderData withShutters(FramingShutterState.Snapshot snapshot) {
        return new BeamRenderData(fixturePos, origin, beamDir, axisU, axisV, zoomNorm, scanLen,
                tanHalfAngle, color, intensity, goboTexture, nextGoboTexture, goboRotation,
                wheelTransition, level, widthScale, heightScale, baseRadius, snapshot);
    }

    public boolean hasShutters() {
        return shutters != null && shutters.isActive();
    }

    /** Open / wash cones may paint a circular wall spot. Pattern gobos must not. */
    public static boolean isOpenFloodGobo(ResourceLocation gobo) {
        if (gobo == null) {
            return true;
        }
        String path = gobo.getPath();
        return path.contains("/open")
                || path.contains("/wash")
                || path.endsWith("empty_fallback.png");
    }

    public boolean isOpenFloodGobo() {
        return isOpenFloodGobo(goboTexture);
    }

    /**
     * Length of the volumetric cone. Patterned gobos stop short of the wall so
     * {@code GoboGPUProjector} owns the surface tache.
     */
    public float volumeLength(float maxDist) {
        boolean hit = scanLen < maxDist;
        if (!hit) {
            return maxDist;
        }
        if (isOpenFloodGobo()) {
            return scanLen + 2.5f;
        }
        return Math.max(0.05f, scanLen - 0.35f);
    }

    public int generateStateHash(int slices) {
        int hash = 17;
        hash = 31 * hash + fixturePos.hashCode();
        hash = 31 * hash + Float.floatToIntBits((float) origin.x);
        hash = 31 * hash + Float.floatToIntBits((float) origin.y);
        hash = 31 * hash + Float.floatToIntBits((float) origin.z);
        hash = 31 * hash + Float.floatToIntBits((float) beamDir.x);
        hash = 31 * hash + Float.floatToIntBits((float) beamDir.y);
        hash = 31 * hash + Float.floatToIntBits((float) beamDir.z);
        hash = 31 * hash + Float.floatToIntBits(zoomNorm);
        hash = 31 * hash + Float.floatToIntBits(scanLen);
        hash = 31 * hash + Float.floatToIntBits(tanHalfAngle);
        hash = 31 * hash + color;
        hash = 31 * hash + Float.floatToIntBits(intensity);
        hash = 31 * hash + (goboTexture != null ? goboTexture.hashCode() : 0);
        hash = 31 * hash + (nextGoboTexture != null ? nextGoboTexture.hashCode() : 0);
        hash = 31 * hash + Float.floatToIntBits(goboRotation);
        hash = 31 * hash + Float.floatToIntBits(wheelTransition);
        hash = 31 * hash + slices;
        hash = 31 * hash + Float.floatToIntBits(widthScale);
        hash = 31 * hash + Float.floatToIntBits(heightScale);
        hash = 31 * hash + Float.floatToIntBits(baseRadius);
        hash = 31 * hash + (shutters != null ? shutters.stateHash() : 0);
        return hash;
    }
}
