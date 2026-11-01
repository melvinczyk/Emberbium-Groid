package org.embeddedt.embeddium.render.entity;

public interface RenderTypeCullState {
    /**
     * @return true only if this render type is known to draw with back-face culling enabled
     */
    boolean embeddium$hasBackfaceCulling();
}
