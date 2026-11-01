package org.embeddedt.embeddium.render.entity;

/**
 * Implemented by vertex consumers that know whether the geometry written into them will be drawn with back-face
 * culling enabled. When it will, model cuboid faces pointing away from the camera can be skipped on the CPU instead of
 * being written, uploaded and then discarded by the GPU.
 */
public interface BackfaceCullHint {
    boolean embeddium$isBackfaceCulled();
}
