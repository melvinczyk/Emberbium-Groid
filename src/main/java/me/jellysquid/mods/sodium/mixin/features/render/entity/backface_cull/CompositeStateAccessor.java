package me.jellysquid.mods.sodium.mixin.features.render.entity.backface_cull;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("cullState")
    RenderStateShard.CullStateShard embeddium$getCullState();
}
