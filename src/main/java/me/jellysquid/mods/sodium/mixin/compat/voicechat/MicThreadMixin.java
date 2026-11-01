package me.jellysquid.mods.sodium.mixin.compat.voicechat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.embeddedt.embeddium.compat.voicechat.VoiceChatMuteGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops Simple Voice Chat's microphone capture and processing while muted, through its existing "disabled" path
 * (flush, reset the processor, stop the microphone, idle). See {@link VoiceChatMuteGate}.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.MicThread", remap = false)
public class MicThreadMixin {
    @ModifyExpressionValue(method = "run()V", at = @At(value = "INVOKE", target = "Lde/maxhenkel/voicechat/voice/client/ClientPlayerStateManager;isDisabled()Z"), require = 0)
    private boolean embeddium$idleWhileMuted(boolean disabled) {
        return disabled || VoiceChatMuteGate.isMuted();
    }
}
