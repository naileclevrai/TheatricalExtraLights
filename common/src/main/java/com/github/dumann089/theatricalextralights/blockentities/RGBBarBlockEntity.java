package com.github.dumann089.theatricalextralights.blockentities;

import com.github.dumann089.theatricalextralights.blocks.RGBbarBlock;
import com.github.dumann089.theatricalextralights.fixtures.Fixtures;
import dev.imabad.theatrical.api.Fixture;
import dev.imabad.theatrical.blockentities.light.BaseDMXConsumerLightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

public class RGBBarBlockEntity extends ExtraLightsLightBlockEntity {

    public RGBBarBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.RGB_BAR.get(), pos, state);
        setChannelCount(4);
    }
    @Override
    public Fixture getFixture() {
        return Fixtures.RGB_BAR.get();
    }

    @Override
    public int getFocus() {
        return 255;
    }

    @Override
    public void consume(byte[] dmxValues) {
        int start = this.getChannelStart() > 0 ? this.getChannelStart() - 1 : 0;
        byte[] ourValues = Arrays.copyOfRange(dmxValues, start,
                start+ this.getChannelCount());
        if(ourValues.length < 4){
            return;
        }
                boolean prevAdvanced = beginDmxUpdate();
        int _pi = intensity, _pr = red, _pg = green, _pb = blue, _pf = focus, _pp = pan, _pt = tilt;
        intensity = convertByteToInt(ourValues[0]);
        red = convertByteToInt(ourValues[1]);
        green = convertByteToInt(ourValues[2]);
        blue = convertByteToInt(ourValues[3]);
        finishDmxUpdate(intensity != _pi || red != _pr || green != _pg || blue != _pb || focus != _pf || pan != _pp || tilt != _pt, prevAdvanced);
    }


    @Override
    public int getDeviceTypeId() {
        return 0x02;
    }

    @Override
    public String getModelName() {
        return "RGB Bar";
    }

    @Override
    public boolean isUpsideDown() {
        return getBlockState().getValue(RGBbarBlock.HANGING) && getBlockState().getValue(RGBbarBlock.HANG_DIRECTION) == Direction.UP;
    }

    @Override
    public ResourceLocation getFixtureId() {
        return Fixtures.RGB_BAR.getId();
    }

    @Override
    public int getActivePersonality() {
        return 0;
    }

    public int convertByteToInt(byte val) {
        return Byte.toUnsignedInt(val);
    }

    @Override
    public String getTranslationKey() {
        return "block.theatricalextralights.rgb_bar";
    }
}