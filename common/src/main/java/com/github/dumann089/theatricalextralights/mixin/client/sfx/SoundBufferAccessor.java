package com.github.dumann089.theatricalextralights.mixin.client.sfx;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.sound.sampled.AudioFormat;
import java.util.OptionalInt;

/** Acces au tampon OpenAL et au format d'un echantillon decode, pour poser des points de boucle. */
@Mixin(SoundBuffer.class)
public interface SoundBufferAccessor {
    @Invoker("getAlBuffer")
    OptionalInt tel$getAlBuffer();

    @Accessor("format")
    AudioFormat tel$getFormat();
}
