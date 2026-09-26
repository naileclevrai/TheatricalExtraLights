package com.github.dumann089.theatricalextralights.blockentities;

import com.github.dumann089.theatricalextralights.blockentities.interfaces.HasPersonality;
import com.github.dumann089.theatricalextralights.blocks.RGBbarBlock;
import com.github.dumann089.theatricalextralights.client.StrobeRenderHelper;
import com.github.dumann089.theatricalextralights.fixtures.Fixtures;
import com.github.dumann089.theatricalextralights.util.BlockEntitySync;
import dev.imabad.theatrical.api.Fixture;
import dev.imabad.theatrical.api.dmx.DMXPersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

/**
 * Barre LED a neuf pixels.
 *
 * <p>Deux personnalites : 4 canaux (dimmer + un RGB pour toute la barre, comportement d'origine)
 * et 28 canaux (dimmer + un RGB par pixel). Dans les deux cas {@code red/green/blue} portent la
 * couleur moyenne et {@code intensity} le dimmer, pour la lumiere dynamique et les outils qui
 * ne connaissent qu'une couleur ; le rendu par pixel lit {@link #getPixelColour(int)}.
 */
public class RGBBarBlockEntity extends ExtraLightsLightBlockEntity implements HasPersonality {

    public static final int PIXEL_COUNT = 9;
    private static final int MODE_4CH = 0;
    private static final int MODE_PIXEL = 1;

    private int activePersonalityIndex = MODE_4CH;
    /** [p0.r, p0.g, p0.b, p1.r, ...] — en mode 4 canaux, tous les pixels valent red/green/blue. */
    private final int[] pixels = new int[PIXEL_COUNT * 3];

    public RGBBarBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.RGB_BAR.get(), pos, state);
        setChannelCount(getPersonalityChannelCount());
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
    public int getActivePersonality() {
        return activePersonalityIndex;
    }

    @Override
    public void setActivePersonality(int index) {
        List<DMXPersonality> personalities = getFixture().getDMXPersonalities();
        if (index < 0 || index >= personalities.size()) {
            return;
        }
        activePersonalityIndex = index;
        setChannelCount(getPersonalityChannelCount());
        setChanged();
        if (level != null) {
            BlockEntitySync.sendData(this);
        }
    }

    private int getPersonalityChannelCount() {
        List<DMXPersonality> personalities = getFixture().getDMXPersonalities();
        if (personalities == null || personalities.isEmpty()) {
            return 4;
        }
        return personalities.get(activePersonalityIndex).getChannelCount();
    }

    public boolean isPixelMode() {
        return activePersonalityIndex == MODE_PIXEL;
    }

    /** Couleur 0xRRGGBB du pixel ; en mode 4 canaux, la couleur de la barre. */
    public int getPixelColour(int pixel) {
        if (!isPixelMode()) {
            return getColour();
        }
        if (pixel < 0 || pixel >= PIXEL_COUNT) {
            return 0;
        }
        int base = pixel * 3;
        return (pixels[base] << 16) | (pixels[base + 1] << 8) | pixels[base + 2];
    }

    /** Luminosite propre du pixel 0..255 (max de ses composantes), avant le dimmer. */
    public int getPixelLevel(int pixel) {
        int c = getPixelColour(pixel);
        return Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF));
    }

    @Override
    public void consume(byte[] dmxValues) {
        int channelCount = getPersonalityChannelCount();
        int start = this.getChannelStart() > 0 ? this.getChannelStart() - 1 : 0;
        byte[] ourValues = Arrays.copyOfRange(dmxValues, start, start + channelCount);
        if (ourValues.length < channelCount) {
            return;
        }
        boolean prevAdvanced = beginDmxUpdate();
        int _pi = intensity, _pr = red, _pg = green, _pb = blue, _pf = focus, _pp = pan, _pt = tilt;
        int[] prevPixels = pixels.clone();

        intensity = convertByteToInt(ourValues[0]);
        if (isPixelMode()) {
            for (int i = 0; i < PIXEL_COUNT * 3; i++) {
                pixels[i] = convertByteToInt(ourValues[1 + i]);
            }
            // Couleur moyenne ponderee par la luminosite de chaque pixel, pour la lumiere dynamique.
            long r = 0, g = 0, b = 0, weight = 0;
            for (int p = 0; p < PIXEL_COUNT; p++) {
                int pr = pixels[p * 3], pg = pixels[p * 3 + 1], pb = pixels[p * 3 + 2];
                int w = Math.max(pr, Math.max(pg, pb));
                r += (long) pr * w;
                g += (long) pg * w;
                b += (long) pb * w;
                weight += w;
            }
            if (weight > 0) {
                red = (int) (r / weight);
                green = (int) (g / weight);
                blue = (int) (b / weight);
            } else {
                red = green = blue = 0;
            }
        } else {
            red = convertByteToInt(ourValues[1]);
            green = convertByteToInt(ourValues[2]);
            blue = convertByteToInt(ourValues[3]);
        }

        boolean changed = intensity != _pi || red != _pr || green != _pg || blue != _pb
                || focus != _pf || pan != _pp || tilt != _pt || !Arrays.equals(pixels, prevPixels);
        finishDmxUpdate(changed, prevAdvanced);
    }

    /** En mode pixel, les 28 canaux depassent le DmxFrame de Theatrical : sync bloc complete. */
    @Override
    protected boolean hasExtraDmxChannelsBeyondBatch() {
        return isPixelMode();
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

    public int convertByteToInt(byte val) {
        return Byte.toUnsignedInt(val);
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        tag.putInt("activePersonality", activePersonalityIndex);
        tag.putIntArray("Pixels", pixels);
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        if (tag.contains("activePersonality")) {
            int index = tag.getInt("activePersonality");
            List<DMXPersonality> personalities = getFixture().getDMXPersonalities();
            if (index >= 0 && index < personalities.size()) {
                activePersonalityIndex = index;
            }
        }
        setChannelCount(getPersonalityChannelCount());
        int[] saved = tag.getIntArray("Pixels");
        if (saved.length == pixels.length) {
            System.arraycopy(saved, 0, pixels, 0, pixels.length);
        }
        if (level != null && level.isClientSide) {
            StrobeRenderHelper.markSectionDirty(getBlockPos());
        }
    }

    @Override
    public String getTranslationKey() {
        return "block.theatricalextralights.rgb_bar";
    }
}
