package me.jellysquid.mods.sodium.mixin.compat.eclipticseasons;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.embeddedt.embeddium.compat.eclipticseasons.EclipticSeasonsFogGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ecliptic Seasons' weather fog clears an offscreen target, blits the whole color and depth buffer into it and runs a
 * full-screen shader pass every frame, even when its fog strength (eased density times rain level squared) is zero
 * and the pass draws nothing. Skip both stages while the fog would be invisible.
 */
@Pseudo
@Mixin(targets = "com.teamtea.eclipticseasons.client.render.FogRenderer", remap = false)
public class FogRendererMixin {
    @Shadow
    static float fogDensity;

    @Inject(method = "onRenderLevelStage", at = @At("HEAD"), cancellable = true, require = 0)
    private static void embeddium$skipInvisibleFog(RenderLevelStageEvent event, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level == null || !EclipticSeasonsFogGate.enabled) {
            return;
        }
        float rain = level.getRainLevel(1.0f);
        if (fogDensity * rain * rain < 1.0e-4f) {
            ci.cancel();
        }
    }
}
