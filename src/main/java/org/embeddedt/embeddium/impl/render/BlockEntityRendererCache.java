package org.embeddedt.embeddium.impl.render;

import java.util.Map;

public interface BlockEntityRendererCache {
    /**
     * The renderer looked up from a dispatcher's renderer map. One immutable object, so a thread reading it never
     * pairs a map with a renderer from another map: getRenderer is also called off the render thread (Entity
     * Culling's cull thread, for one), and the map is replaced on every resource reload.
     */
    record Entry(Map<?, ?> map, Object renderer) {
    }

    Entry embeddium$getCachedRenderer();

    void embeddium$setCachedRenderer(Entry entry);
}
