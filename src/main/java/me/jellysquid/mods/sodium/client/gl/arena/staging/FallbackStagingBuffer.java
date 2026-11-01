package me.jellysquid.mods.sodium.client.gl.arena.staging;

import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import org.lwjgl.opengl.GL11C;

import java.nio.ByteBuffer;

public class FallbackStagingBuffer implements StagingBuffer {
    /**
     * Upload straight into the destination with glBufferSubData instead of through a scratch buffer and
     * glCopyBufferSubData. Apple's OpenGL-on-Metal driver has no accelerated buffer-to-buffer copy (it logs
     * "gldCopyBufferSubData: NEEDS IMPLEMENTATION"), and as it lacks ARB_buffer_storage it always takes this fallback,
     * so every chunk upload paid for a scratch allocation plus a slow copy. Defaults on for Apple renderers only;
     * -Demberbium.directStagingUploads=true/false forces it.
     */
    public static boolean directUploads = Boolean.parseBoolean(System.getProperty("emberbium.directStagingUploads",
            String.valueOf(isAppleRenderer())));

    private final GlMutableBuffer fallbackBufferObject;

    public FallbackStagingBuffer(CommandList commandList) {
        this.fallbackBufferObject = commandList.createMutableBuffer();
    }

    private static boolean isAppleRenderer() {
        String vendor = GL11C.glGetString(GL11C.GL_VENDOR);
        return vendor != null && vendor.contains("Apple");
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        if (directUploads) {
            commandList.uploadSubData(dst, writeOffset, data);
            return;
        }
        commandList.uploadData(this.fallbackBufferObject, data, GlBufferUsage.STREAM_COPY);
        commandList.copyBufferSubData(this.fallbackBufferObject, dst, 0, writeOffset, data.remaining());
    }

    @Override
    public void flush(CommandList commandList) {
        if (directUploads) {
            return;
        }
        commandList.allocateStorage(this.fallbackBufferObject, 0L, GlBufferUsage.STREAM_COPY);
    }

    @Override
    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.fallbackBufferObject);
    }

    @Override
    public void flip() {

    }

    @Override
    public String toString() {
        return directUploads ? "Direct" : "Fallback";
    }
}
