package me.jellysquid.mods.sodium.mixin.compat.fieldguide;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.embeddedt.embeddium.compat.fieldguide.FieldGuideIdCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Field Guide's scanner looks up the guide entry id of whatever the player is looking at every tick, and each lookup
 * builds a new "block:/item:/entity:"-prefixed id by string replacement. For blocks, items and entity types the result
 * only depends on the registry id, which never changes once registered, so remember it.
 */
@Pseudo
@Mixin(targets = "com.evandev.fieldguide.api.AutoPopulateRegistry", remap = false)
public class AutoPopulateRegistryMixin {
    @Unique
    private static boolean embeddium$isRegistryObject(Object obj) {
        return obj instanceof Block || obj instanceof Item || obj instanceof EntityType;
    }

    @Inject(method = "getEntryId(Ljava/lang/Object;Z)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true, require = 0)
    private static void embeddium$cachedId(Object obj, boolean prefixed, CallbackInfoReturnable<ResourceLocation> cir) {
        if (prefixed && FieldGuideIdCache.enabled && embeddium$isRegistryObject(obj)) {
            ResourceLocation id = FieldGuideIdCache.PREFIXED_IDS.get(obj);
            if (id != null) {
                cir.setReturnValue(id);
            }
        }
    }

    @Inject(method = "getEntryId(Ljava/lang/Object;Z)Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"), require = 0)
    private static void embeddium$rememberId(Object obj, boolean prefixed, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation id = cir.getReturnValue();
        if (prefixed && id != null && embeddium$isRegistryObject(obj)) {
            FieldGuideIdCache.PREFIXED_IDS.put(obj, id);
        }
    }
}
