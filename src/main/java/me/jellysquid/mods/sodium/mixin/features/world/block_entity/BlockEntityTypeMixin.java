package me.jellysquid.mods.sodium.mixin.features.world.block_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.embeddedt.embeddium.impl.world.BlockEntityTypeValidityCache;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/**
 * {@link BlockEntityType#isValid(BlockState)} is called every tick for every ticking block entity, and every frame
 * for every rendered one. Remember the last type each block matched so the common case skips the set lookup.
 */
@Mixin(BlockEntityType.class)
public class BlockEntityTypeMixin {
    @WrapOperation(method = "isValid", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean embeddium$cacheValidity(Set<?> validBlocks, Object block, Operation<Boolean> original) {
        if (block instanceof BlockEntityTypeValidityCache cache) {
            if (cache.embeddium$getValidBlockEntityType() == (Object) this) {
                return true;
            }
            boolean valid = original.call(validBlocks, block);
            if (valid) {
                cache.embeddium$setValidBlockEntityType((BlockEntityType<?>) (Object) this);
            }
            return valid;
        }
        return original.call(validBlocks, block);
    }
}
