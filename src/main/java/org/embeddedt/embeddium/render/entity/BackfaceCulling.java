package org.embeddedt.embeddium.render.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class BackfaceCulling {
    private static final Logger LOGGER = LoggerFactory.getLogger("Groidberbium/BackfaceCulling");

    // Shader packs render extra passes (e.g. shadows) from other viewpoints using the same geometry, so culling is
    // only allowed while no shader pack is active. Iris/Oculus (and forks such as Emberlens) expose this through
    // IrisApi; merely having the mod installed with shaders turned off must not disable the optimization.
    private static final MethodHandle IRIS_GET_INSTANCE, IRIS_SHADER_PACK_IN_USE, IRIS_SHADOW_PASS;

    static {
        MethodHandle getInstance = null, inUse = null, shadowPass = null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getInstance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).asType(MethodType.methodType(Object.class));
            inUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).asType(MethodType.methodType(boolean.class, Object.class));
            shadowPass = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).asType(MethodType.methodType(boolean.class, Object.class));
        } catch (ClassNotFoundException ignored) {
            // No shader mod installed
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Shader mod API not usable; model back-face culling disabled", e);
            getInstance = null;
            inUse = null;
            shadowPass = null;
            SHADER_API_BROKEN = true;
        }
        IRIS_GET_INSTANCE = getInstance;
        IRIS_SHADER_PACK_IN_USE = inUse;
        IRIS_SHADOW_PASS = shadowPass;
    }

    private static boolean SHADER_API_BROKEN;

    private BackfaceCulling() { }

    private static boolean shadersActive() {
        if (SHADER_API_BROKEN) {
            return true;
        }
        if (IRIS_GET_INSTANCE == null) {
            return false;
        }
        try {
            Object api = IRIS_GET_INSTANCE.invokeExact();
            return (boolean) IRIS_SHADER_PACK_IN_USE.invokeExact(api) || (boolean) IRIS_SHADOW_PASS.invokeExact(api);
        } catch (Throwable t) {
            LOGGER.warn("Shader mod API failed; model back-face culling disabled", t);
            SHADER_API_BROKEN = true;
            return true;
        }
    }

    /**
     * Whether model faces written into {@code consumer} right now may be culled on the CPU. Requires that the consumer
     * is known to feed a render type with back-face culling, and that the projection is a perspective one, where the
     * eye sits at the origin of the pose's space (orthographic GUI rendering is excluded).
     */
    public static boolean canCull(VertexConsumer consumer) {
        return SodiumClientMod.options().performance.useModelBackfaceCulling
                && consumer instanceof BackfaceCullHint hint && hint.embeddium$isBackfaceCulled()
                && RenderSystem.getProjectionMatrix().m33() == 0.0f
                && !shadersActive();
    }
}
