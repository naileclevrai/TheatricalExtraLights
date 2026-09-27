package com.github.dumann089.theatricalextralights.mixin.client.sfx;

import com.github.dumann089.theatricalextralights.client.sfx.SampleLoopPoints;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Retient les tampons des echantillons qui ont des points de boucle declares. */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Inject(method = "getCompleteBuffer", at = @At("RETURN"))
    private void tel$rememberLoopPointBuffer(ResourceLocation soundFile, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        if (!SampleLoopPoints.hasPoints(soundFile)) {
            return;
        }
        CompletableFuture<SoundBuffer> future = cir.getReturnValue();
        if (future != null) {
            future.thenAccept(buffer -> SampleLoopPoints.onBufferLoaded(soundFile, buffer));
        }
    }
}
