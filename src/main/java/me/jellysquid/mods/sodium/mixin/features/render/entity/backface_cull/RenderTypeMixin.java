package me.jellysquid.mods.sodium.mixin.features.render.entity.backface_cull;

import net.minecraft.client.renderer.RenderType;
import org.embeddedt.embeddium.render.entity.RenderTypeCullState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderType.class)
public class RenderTypeMixin implements RenderTypeCullState {
    @Override
    public boolean embeddium$hasBackfaceCulling() {
        // Unknown render type implementations are assumed to draw both sides
        return false;
    }
}
