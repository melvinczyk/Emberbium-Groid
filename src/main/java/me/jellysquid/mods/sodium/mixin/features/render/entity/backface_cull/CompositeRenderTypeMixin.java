package me.jellysquid.mods.sodium.mixin.features.render.entity.backface_cull;

import net.minecraft.client.renderer.RenderType;
import org.embeddedt.embeddium.render.entity.RenderTypeCullState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public class CompositeRenderTypeMixin implements RenderTypeCullState {
    @Shadow
    @Final
    private RenderType.CompositeState state;

    // 0 = not computed, 1 = culled, 2 = not culled. The state is immutable once constructed.
    @Unique
    private byte embeddium$cullState;

    @Override
    public boolean embeddium$hasBackfaceCulling() {
        byte cached = this.embeddium$cullState;
        if (cached == 0) {
            var cull = ((CompositeStateAccessor) (Object) this.state).embeddium$getCullState();
            cached = cull != null && ((BooleanStateShardAccessor) cull).embeddium$isEnabled() ? (byte) 1 : (byte) 2;
            this.embeddium$cullState = cached;
        }
        return cached == 1;
    }
}
