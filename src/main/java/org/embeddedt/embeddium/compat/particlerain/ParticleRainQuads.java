package org.embeddedt.embeddium.compat.particlerain;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ParticleVertex;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Writes Particle Rain's weather quads straight into the vertex buffer. Its own code allocates five vectors per quad
 * and goes through the vertex consumer chain one attribute at a time, for every rain, snow and dust particle every
 * frame. Same corners, texture coordinates, color and light as Particle Rain.
 */
public final class ParticleRainQuads {
    // Toggled by the dev benchmark to A/B the fast path
    public static boolean enabled = true;

    // Particles render on the render thread only (like EntityRenderer's scratch buffer); avoids a ThreadLocal stack
    // lookup per particle
    private static final long SCRATCH = MemoryUtil.nmemAlignedAlloc(64, 4 * ParticleVertex.STRIDE);
    private static final MemoryStack STACK = MemoryStack.create(4096);

    private ParticleRainQuads() {
    }

    /**
     * Writes a quad with corners (cornerX[i], cornerY[i], 0) rotated by {@code q}, scaled by {@code size} and offset
     * by (x, y, z). Returns false, writing nothing, when the consumer has no direct buffer access.
     */
    public static boolean write(VertexConsumer consumer, Quaternionf q, float x, float y, float z, float size,
                                float[] cornerX, float[] cornerY, float[] u, float[] v,
                                float r, float g, float b, float a, int light) {
        if (!enabled || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        var writer = VertexBufferWriter.tryOf(consumer);
        if (writer == null) {
            return false;
        }

        // Rotation matrix of q, as Quaternionf.transform computes it (columns 0 and 1; corners have z = 0)
        float xx = q.x * q.x, yy = q.y * q.y, zz = q.z * q.z, ww = q.w * q.w;
        float xy = q.x * q.y, xz = q.x * q.z, yz = q.y * q.z, xw = q.x * q.w, zw = q.z * q.w, yw = q.y * q.w;
        float k = 1.0f / (xx + yy + zz + ww);
        float m00 = (xx - yy - zz + ww) * k * size, m01 = 2.0f * (xy - zw) * k * size;
        float m10 = 2.0f * (xy + zw) * k * size, m11 = (yy - xx - zz + ww) * k * size;
        float m20 = 2.0f * (xz - yw) * k * size, m21 = 2.0f * (yz + xw) * k * size;

        int color = ColorABGR.pack(r, g, b, a);

        long ptr = SCRATCH;
        for (int i = 0; i < 4; i++) {
            float cx = cornerX[i], cy = cornerY[i];
            ParticleVertex.put(ptr, m00 * cx + m01 * cy + x, m10 * cx + m11 * cy + y, m20 * cx + m21 * cy + z,
                    u[i], v[i], color, light);
            ptr += ParticleVertex.STRIDE;
        }
        STACK.push();
        writer.push(STACK, SCRATCH, 4, ParticleVertex.FORMAT);
        STACK.pop();
        return true;
    }
}
