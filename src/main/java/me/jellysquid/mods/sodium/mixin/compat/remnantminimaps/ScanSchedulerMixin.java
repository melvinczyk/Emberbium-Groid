package me.jellysquid.mods.sodium.mixin.compat.remnantminimaps;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.embeddedt.embeddium.compat.remnantminimaps.RemnantScanGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips re-scanning minimap chunks that have not changed; see {@link RemnantScanGate}.
 */
@Pseudo
@Mixin(targets = "io.nightbeam.remnantminimaps.client.scan.ScanScheduler", remap = false)
public class ScanSchedulerMixin {
    @Inject(method = "tick(Lnet/minecraft/client/multiplayer/ClientLevel;Lio/nightbeam/remnantminimaps/client/session/DimensionMap;IIIZIJ)V", at = @At("HEAD"), require = 0)
    private void embeddium$beginTick(ClientLevel level, @Coerce Object map, int playerChunkX, int playerChunkZ, int sliceY, boolean cave, int viewDistance, long budgetNanos, CallbackInfo ci) {
        RemnantScanGate.beginTick(sliceY, cave, level.getGameTime());
    }

    @Inject(method = "enqueue(JZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void embeddium$skipUnchanged(long pos, boolean priority, CallbackInfo ci) {
        if (RemnantScanGate.isFresh(pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "onChunkLoaded(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("HEAD"), require = 0)
    private void embeddium$chunkLoaded(LevelChunk chunk, CallbackInfo ci) {
        RemnantScanGate.invalidate(chunk.getPos().toLong());
    }

    @Inject(method = "reset()V", at = @At("HEAD"), require = 0)
    private void embeddium$reset(CallbackInfo ci) {
        RemnantScanGate.reset();
    }
}
