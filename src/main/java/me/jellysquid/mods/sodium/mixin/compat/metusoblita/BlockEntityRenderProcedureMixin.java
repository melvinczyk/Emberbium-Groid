package me.jellysquid.mods.sodium.mixin.compat.metusoblita;

import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.embeddedt.embeddium.compat.metusoblita.MetusOblitaRenderGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips Metus Oblita's per-frame block entity render procedures while none of its block entities are loaded nearby.
 * See {@link MetusOblitaRenderGate}.
 */
@Pseudo
@Mixin(targets = {
        "net.mcreator.metusoblita.procedures.AlchemicalGlassItemRenderProcedure",
        "net.mcreator.metusoblita.procedures.AlchemicalReactorItemRenderProcedure",
        "net.mcreator.metusoblita.procedures.CruorCollectorDisplayProcedure",
        "net.mcreator.metusoblita.procedures.CruorInfuserItemRenderProcedure",
        "net.mcreator.metusoblita.procedures.CruorLaserBeamRenderingProcedure",
        "net.mcreator.metusoblita.procedures.DepthstonePedestalItemDisplayProcedure",
        "net.mcreator.metusoblita.procedures.EssenceBoilerContainerRenderProcedure",
        "net.mcreator.metusoblita.procedures.ForgottenAltarItemRenderProcedure",
        "net.mcreator.metusoblita.procedures.ForgottenAltarShockwaveRenderProcedure"
}, remap = false)
public class BlockEntityRenderProcedureMixin {
    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void embeddium$skipWhenAbsent(Event event, LevelAccessor world, CallbackInfo ci) {
        if (!MetusOblitaRenderGate.anyBlockEntitiesNearby()) {
            ci.cancel();
        }
    }
}
