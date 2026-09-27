package com.github.dumann089.theatricalextralights.fixtures;

import com.github.dumann089.theatricalextralights.blockentities.DenseVerticalbarBlockEntity;
import com.github.dumann089.theatricalextralights.blockentities.PixelBarBlockEntity;
import dev.imabad.theatrical.api.dmx.DMXPersonality;
import dev.imabad.theatrical.fixtures.SharedSlots;

import java.util.List;

/** Vertical RGB Bar dense : le meme appareil, 46 pixels au lieu de 9 en mode pixel (184 canaux). */
public class DenseVerticalbarFixture extends VerticalbarFixture {

    public static final int PIXEL_CHANNEL_COUNT = PixelBarBlockEntity.pixelModeChannelCount(0, DenseVerticalbarBlockEntity.PIXEL_COUNT);

    private static final List<DMXPersonality> PERSONALITIES = List.of(
            new DMXPersonality(4, "4-Channel Mode")
                    .addSlot(SharedSlots.INTENSITY)
                    .addSlot(SharedSlots.RED)
                    .addSlot(SharedSlots.GREEN)
                    .addSlot(SharedSlots.BLUE),
            PixelBarPersonalities.addPixels(new DMXPersonality(PIXEL_CHANNEL_COUNT,
                    PixelBarPersonalities.pixelModeName(PIXEL_CHANNEL_COUNT, DenseVerticalbarBlockEntity.PIXEL_COUNT, "")),
                    DenseVerticalbarBlockEntity.PIXEL_COUNT)
    );

    @Override
    public List<DMXPersonality> getDMXPersonalities() {
        return PERSONALITIES;
    }
}
