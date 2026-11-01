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
 * Records which chunks the minimap scanned (only its scan scheduler calls this); see {@link RemnantScanGate}.
 */
@Pseudo
@Mixin(targets = "io.nightbeam.remnantminimaps.client.scan.ChunkScanner", remap = false)
public class ChunkScannerMixin {
    @Inject(method = "scan(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lio/nightbeam/remnantminimaps/client/scan/ChunkScanner$Result;IZ)V", at = @At("HEAD"), require = 0)
    private void embeddium$markScanned(ClientLevel level, LevelChunk chunk, @Coerce Object result, int sliceY, boolean cave, CallbackInfo ci) {
        RemnantScanGate.markScanned(chunk.getPos().toLong());
    }
}
