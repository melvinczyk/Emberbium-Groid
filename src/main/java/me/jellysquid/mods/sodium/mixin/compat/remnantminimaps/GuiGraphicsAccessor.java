package me.jellysquid.mods.sodium.mixin.compat.remnantminimaps;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Accessor("managed")
    boolean embeddium$isManaged();
}
