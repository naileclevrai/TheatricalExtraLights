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
 * et 36 canaux (un dimmer et un RGB par pixel, pas de dimmer general). Dans les deux cas
 * {@code red/green/blue} portent la couleur moyenne et {@code intensity} le niveau de la barre
 * (en mode pixel : le dimmer du pixel le plus fort), pour la lumiere dynamique et les outils qui ne
 * connaissent qu'une couleur ; le rendu par pixel lit {@link #getPixelColour(int)} et
 * {@link #getPixelDimmer(int)}.
 */
public class RGBBarBlockEntity extends ExtraLightsLightBlockEntity implements HasPersonality {

    public static final int PIXEL_COUNT = 9;
    /** Canaux DMX par pixel : dimmer, rouge, vert, bleu. */
    public static final int PIXEL_STRIDE = 4;
    private static final int MODE_4CH = 0;
    private static final int MODE_PIXEL = 1;

    private int activePersonalityIndex = MODE_4CH;
    /** [p0.dim, p0.r, p0.g, p0.b, p1.dim, ...] bruts ; en mode 4 canaux, ignores (la barre vaut red/green/blue). */
    private final int[] pixels = new int[PIXEL_COUNT * PIXEL_STRIDE];

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

    /** Dimmer propre du pixel 0..255 ; 255 en mode 4 canaux, ou le dimmer de la barre est {@code intensity}. */
    public int getPixelDimmer(int pixel) {
        if (!isPixelMode()) {
            return 255;
        }
        if (pixel < 0 || pixel >= PIXEL_COUNT) {
            return 0;
        }
        return pixels[pixel * PIXEL_STRIDE];
    }

    /** Couleur 0xRRGGBB brute du pixel (avant son dimmer) ; en mode 4 canaux, la couleur de la barre. */
    public int getPixelColour(int pixel) {
        if (!isPixelMode()) {
            return getColour();
        }
        if (pixel < 0 || pixel >= PIXEL_COUNT) {
            return 0;
        }
        int base = pixel * PIXEL_STRIDE;
        return (pixels[base + 1] << 16) | (pixels[base + 2] << 8) | pixels[base + 3];
    }

    /**
     * Luminosite du pixel 0..255 : composante la plus forte de sa couleur, multipliee par son dimmer.
     * En mode 4 canaux, celle de la couleur de la barre (le dimmer est {@code intensity}).
     */
    public int getPixelLevel(int pixel) {
        int c = getPixelColour(pixel);
        int max = Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF));
        return (max * getPixelDimmer(pixel) + 127) / 255;
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

        if (isPixelMode()) {
            for (int i = 0; i < PIXEL_COUNT * PIXEL_STRIDE; i++) {
                pixels[i] = convertByteToInt(ourValues[i]);
            }
            // Pas de dimmer general : la barre prend le dimmer du pixel le plus fort, et sa couleur
            // est la moyenne des couleurs brutes ponderee par la luminosite de chaque pixel. Une barre
            // unie donne ainsi la meme lumiere dynamique qu'en mode 4 canaux.
            int maxDim = 0;
            long r = 0, g = 0, b = 0, weight = 0;
            for (int p = 0; p < PIXEL_COUNT; p++) {
                maxDim = Math.max(maxDim, getPixelDimmer(p));
                int c = getPixelColour(p);
                int pr = (c >> 16) & 0xFF, pg = (c >> 8) & 0xFF, pb = c & 0xFF;
                int w = getPixelLevel(p);
                r += (long) pr * w;
                g += (long) pg * w;
                b += (long) pb * w;
                weight += w;
            }
            intensity = maxDim;
            if (weight > 0) {
                red = (int) (r / weight);
                green = (int) (g / weight);
                blue = (int) (b / weight);
            } else {
                red = green = blue = 0;
            }
        } else {
            intensity = convertByteToInt(ourValues[0]);
            red = convertByteToInt(ourValues[1]);
            green = convertByteToInt(ourValues[2]);
            blue = convertByteToInt(ourValues[3]);
        }

        boolean changed = intensity != _pi || red != _pr || green != _pg || blue != _pb
                || focus != _pf || pan != _pp || tilt != _pt || !Arrays.equals(pixels, prevPixels);
        finishDmxUpdate(changed, prevAdvanced);
    }

    /** En mode pixel, les 36 canaux depassent le DmxFrame de Theatrical : sync bloc complete. */
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
