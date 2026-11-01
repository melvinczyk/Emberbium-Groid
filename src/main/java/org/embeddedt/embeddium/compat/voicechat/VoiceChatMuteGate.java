package org.embeddedt.embeddium.compat.voicechat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Simple Voice Chat keeps the microphone open and runs every captured frame through its denoiser, gain control and
 * voice activity detection while the player is muted, only to throw the result away. MicThreadMixin treats muted
 * like disabled instead, which stops the microphone until the player unmutes.
 */
public final class VoiceChatMuteGate {
    // Toggled by the dev benchmark to A/B the gate
    public static boolean enabled = true;

    private static MethodHandle getStateManager, isMuted;
    private static boolean failed;

    private VoiceChatMuteGate() {
    }

    public static boolean isMuted() {
        if (!enabled || failed) {
            return false;
        }
        try {
            if (isMuted == null) {
                var lookup = MethodHandles.publicLookup();
                Class<?> manager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
                Class<?> state = Class.forName("de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager");
                getStateManager = lookup.findStatic(manager, "getPlayerStateManager", MethodType.methodType(state));
                isMuted = lookup.findVirtual(state, "isMuted", MethodType.methodType(boolean.class));
            }
            Object stateManager = getStateManager.invoke();
            return stateManager != null && (boolean) isMuted.invoke(stateManager);
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }
}
