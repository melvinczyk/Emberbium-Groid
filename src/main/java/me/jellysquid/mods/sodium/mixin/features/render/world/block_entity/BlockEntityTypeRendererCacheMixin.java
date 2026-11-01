package me.jellysquid.mods.sodium.mixin.features.render.world.block_entity;

import org.embeddedt.embeddium.impl.render.BlockEntityRendererCache;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntityType.class)
public class BlockEntityTypeRendererCacheMixin implements BlockEntityRendererCache {
    @Unique
    private Entry embeddium$rendererEntry;

    @Override
    public Entry embeddium$getCachedRenderer() {
        return this.embeddium$rendererEntry;
    }

    @Override
    public void embeddium$setCachedRenderer(Entry entry) {
        this.embeddium$rendererEntry = entry;
    }
}
