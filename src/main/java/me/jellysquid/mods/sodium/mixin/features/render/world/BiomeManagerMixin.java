package me.jellysquid.mods.sodium.mixin.features.render.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.embeddedt.embeddium.impl.render.world.AmbientBiomeShortcut;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The stand-in biome for ClientLevel's ambient particle lookups, see {@link AmbientBiomeShortcut}. Per instance (only
 * the client level's own manager is ever set) and per thread, so the server's levels and other threads that read the
 * client level's biomes at the same moment are unaffected.
 */
@Mixin(BiomeManager.class)
public class BiomeManagerMixin implements AmbientBiomeShortcut {
    @Unique
    private Holder<Biome> embeddium$shortcutBiome;
    @Unique
    private Thread embeddium$shortcutThread;

    @Override
    public void embeddium$setShortcut(Holder<Biome> biome, Thread thread) {
        this.embeddium$shortcutBiome = biome;
        this.embeddium$shortcutThread = thread;
    }

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void embeddium$useShortcut(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> biome = this.embeddium$shortcutBiome;
        if (biome != null && Thread.currentThread() == this.embeddium$shortcutThread) {
            cir.setReturnValue(biome);
        }
    }
}
