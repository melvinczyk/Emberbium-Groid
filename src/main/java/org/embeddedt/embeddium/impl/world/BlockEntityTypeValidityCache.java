package org.embeddedt.embeddium.impl.world;

import net.minecraft.world.level.block.entity.BlockEntityType;

public interface BlockEntityTypeValidityCache {
    BlockEntityType<?> embeddium$getValidBlockEntityType();

    void embeddium$setValidBlockEntityType(BlockEntityType<?> type);
}
