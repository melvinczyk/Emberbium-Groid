package me.jellysquid.mods.sodium.mixin.features.render.entity.backface_cull;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.BooleanStateShard.class)
public interface BooleanStateShardAccessor {
    @Accessor("enabled")
    boolean embeddium$isEnabled();
}
