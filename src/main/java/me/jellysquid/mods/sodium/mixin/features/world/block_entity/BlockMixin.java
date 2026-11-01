package me.jellysquid.mods.sodium.mixin.features.world.block_entity;

import org.embeddedt.embeddium.impl.world.BlockEntityTypeValidityCache;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Block.class)
public class BlockMixin implements BlockEntityTypeValidityCache {
    /**
     * The last block entity type this block was confirmed valid for. Only positive results are stored, so a racing
     * write from another thread can never produce a wrong answer, only a cache miss.
     */
    @Unique
    private BlockEntityType<?> embeddium$validBlockEntityType;

    @Override
    public BlockEntityType<?> embeddium$getValidBlockEntityType() {
        return this.embeddium$validBlockEntityType;
    }

    @Override
    public void embeddium$setValidBlockEntityType(BlockEntityType<?> type) {
        this.embeddium$validBlockEntityType = type;
    }
}
