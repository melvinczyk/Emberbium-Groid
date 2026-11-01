package me.jellysquid.mods.sodium.mixin.features.render.world.block_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.embeddedt.embeddium.impl.render.BlockEntityRendererCache;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * Renderer lookup is an ImmutableMap hash probe per block entity per frame. Cache the result on the block entity
 * type, keyed by the identity of the renderer map so a resource reload (which replaces the map) invalidates it.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @WrapOperation(method = "getRenderer", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object embeddium$cacheRenderer(Map<?, ?> renderers, Object type, Operation<Object> original) {
        if (type instanceof BlockEntityRendererCache cache) {
            BlockEntityRendererCache.Entry entry = cache.embeddium$getCachedRenderer();
            if (entry != null && entry.map() == renderers) {
                return entry.renderer();
            }
            Object renderer = original.call(renderers, type);
            cache.embeddium$setCachedRenderer(new BlockEntityRendererCache.Entry(renderers, renderer));
            return renderer;
        }
        return original.call(renderers, type);
    }
}
