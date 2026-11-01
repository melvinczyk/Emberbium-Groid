package org.embeddedt.embeddium.compat.fieldguide;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prefixed Field Guide entry ids of blocks, items and entity types; see AutoPopulateRegistryMixin.
 */
public final class FieldGuideIdCache {
    // Toggled by the dev benchmark to A/B the cache
    public static boolean enabled = true;
    public static final Map<Object, ResourceLocation> PREFIXED_IDS = new ConcurrentHashMap<>();

    private FieldGuideIdCache() {
    }
}
