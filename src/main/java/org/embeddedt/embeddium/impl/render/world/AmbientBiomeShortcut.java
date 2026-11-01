package org.embeddedt.embeddium.impl.render.world;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Lets ClientLevel's ambient particle pass skip the fuzzy biome lookup. Vanilla looks up the biome at ~1300 random
 * positions per tick (eight noise-biome reads and chunk lookups each) only to ask whether it has ambient particles,
 * which most biomes never do. Once per tick ClientLevelMixin checks the biome palettes of every section those
 * positions can reach; when none holds a biome with ambient particles, the lookups return a stand-in biome without
 * them, so the outcome is identical. The getBiome call itself stays: Windy spawns its wind particles from an inject
 * on that exact call.
 */
public interface AmbientBiomeShortcut {
    /** While set, {@code getBiome} on the setting thread returns {@code biome}; null clears it. */
    void embeddium$setShortcut(Holder<Biome> biome, Thread thread);
}
