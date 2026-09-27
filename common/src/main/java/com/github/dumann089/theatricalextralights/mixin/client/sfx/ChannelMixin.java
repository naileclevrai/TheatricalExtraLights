package com.github.dumann089.theatricalextralights.mixin.client.sfx;

import com.github.dumann089.theatricalextralights.client.sfx.SampleLoopPoints;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pose les points de boucle sur le tampon juste avant qu'il soit attache a la source. */
@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Inject(method = "attachStaticBuffer", at = @At("HEAD"))
    private void tel$applyLoopPoints(SoundBuffer buffer, CallbackInfo ci) {
        SampleLoopPoints.beforeAttach(buffer);
    }
}
