package com.github.dumann089.theatricalextralights.fixtures;

import com.github.dumann089.theatricalextralights.TheatricalExtraLights;
import dev.imabad.theatrical.api.Fixture;
import dev.imabad.theatrical.api.HangType;
import dev.imabad.theatrical.api.dmx.DMXPersonality;
import dev.imabad.theatrical.blocks.light.BaseLightBlock;
import dev.imabad.theatrical.fixtures.SharedSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;

public class AtomicStrobeFixture extends Fixture {

    private static DMXPersonality buildPersonality() {
        DMXPersonality p = new DMXPersonality(34, "34-Channel Atomic")
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE)
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE)
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE)
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE)
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE)
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE)
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE)
                .addSlot(SharedSlots.RED)
                .addSlot(SharedSlots.GREEN)
                .addSlot(SharedSlots.BLUE);
        for (int i = 0; i < 9; i++) {
            p.addSlot(SharedSlots.INTENSITY);
        }
        p.addSlot(SharedSlots.FOCUS);
        return p;
    }

    private static final List<DMXPersonality> PERSONALITIES = Collections.singletonList(buildPersonality());

    private static final ResourceLocation TILT_MODEL = new ResourceLocation(TheatricalExtraLights.MOD_ID, "block/atomic_strobe/atomic_strobe_tilt");
    private static final ResourceLocation PAN_MODEL = new ResourceLocation(TheatricalExtraLights.MOD_ID, "block/atomic_strobe/atomic_strobe_pan");
    private static final ResourceLocation STATIC_MODEL = new ResourceLocation(TheatricalExtraLights.MOD_ID, "block/atomic_strobe/atomic_strobe_static");
    // Body at y∈[2,7], z∈[9,11] (face at z=11.1). Yoke is now a vertical floor
    // stand: ears at y=[4,5], posts at y=[0.5,4], base plate at y=[0,0.5].
    // Pan pivot = base centre (where the floor stand rotates), tilt pivot =
    // ear↔body joint (where the body would tilt on its yoke).
    private final float[] tiltRotation = new float[]{0.5F, 4.5F / 16F, 9.2F / 16F};
    private final float[] panRotation = new float[]{0.5F, 0.0F, 9.2F / 16F};
    private final float[] beamStartPosition = new float[]{0.5F, 4.5F / 16F, 11.1F / 16F};

    @Override
    public ResourceLocation getTiltModel() {
        return TILT_MODEL;
    }

    @Override
    public ResourceLocation getPanModel() {
        return PAN_MODEL;
    }

    @Override
    public ResourceLocation getStaticModel() {
        return STATIC_MODEL;
    }

    @Override
    public float[] getTiltRotationPosition() {
        return tiltRotation;
    }

    @Override
    public float[] getPanRotationPosition() {
        return panRotation;
    }

    @Override
    public float[] getBeamStartPosition() {
        return beamStartPosition;
    }

    @Override
    public float getDefaultRotation() {
        return 90;
    }

    @Override
    public float getBeamWidth() {
        return 0.0f;
    }

    @Override
    public float getRayTraceRotation() {
        return 180f;
    }

    @Override
    public HangType getHangType() {
        return HangType.HOOK_BAR;
    }

    @Override
    public float[] getTransforms(BlockState fixtureBlockState, BlockState supportBlockState) {
        if (fixtureBlockState.getValue(BaseLightBlock.HANG_DIRECTION) == Direction.UP) {
            return new float[]{0, .510f, 0};
        }
        return new float[]{0, -0.365F, 0};
    }

    @Override
    public List<DMXPersonality> getDMXPersonalities() {
        return PERSONALITIES;
    }

    @Override
    public boolean invertTilt() {
        return false;
    }

    @Override
    public boolean invertPan() {
        return false;
    }

    @Override
    public double getLightRadius() {
        return 14.5;
    }
}
