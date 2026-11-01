package me.jellysquid.mods.sodium.mixin.compat.remnantminimaps;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.embeddium.compat.remnantminimaps.RemnantScanGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the minimap re-scan a chunk once a block in it changes; see {@link RemnantScanGate}.
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void embeddium$invalidateMinimapChunk(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        RemnantScanGate.onBlockChanged(pos);
    }
}
