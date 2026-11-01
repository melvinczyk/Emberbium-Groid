package me.jellysquid.mods.sodium.mixin.compat.remnantminimaps;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.embeddedt.embeddium.compat.remnantminimaps.RemnantScanGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remnant Minimaps draws each outlined label (compass letters, info lines) as five separate strings, and outside a
 * managed draw every string flushes the buffers on its own: five draw calls per label, every frame. Drawing the five
 * strings as one managed batch gives the same text in the same order with one flush.
 */
@Pseudo
@Mixin(targets = "io.nightbeam.remnantminimaps.client.ui.Ui", remap = false)
public class UiMixin {
    @Unique
    private static boolean embeddium$drawing;

    @Inject(method = "outlined(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void embeddium$batchOutline(GuiGraphics g, Font font, String text, int x, int y, int color, int outline, CallbackInfo ci) {
        if (embeddium$drawing || !RemnantScanGate.enabled || ((GuiGraphicsAccessor) g).embeddium$isManaged()) {
            return;
        }
        ci.cancel();
        embeddium$drawing = true;
        try {
            g.drawManaged(() -> embeddium$invokeOutlined(g, font, text, x, y, color, outline));
        } finally {
            embeddium$drawing = false;
        }
    }

    @Unique
    private static void embeddium$invokeOutlined(GuiGraphics g, Font font, String text, int x, int y, int color, int outline) {
        // Same calls as Remnant Minimaps' own method
        g.drawString(font, text, x - 1, y, outline, false);
        g.drawString(font, text, x + 1, y, outline, false);
        g.drawString(font, text, x, y - 1, outline, false);
        g.drawString(font, text, x, y + 1, outline, false);
        g.drawString(font, text, x, y, color, false);
    }
}
