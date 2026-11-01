package me.jellysquid.mods.sodium.mixin.compat.particlerain;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import org.embeddedt.embeddium.compat.particlerain.ParticleRainQuads;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Direct vertex writes for Particle Rain's stretched, velocity-aligned quads (rain streaks); see {@link ParticleRainQuads}.
 */
@Pseudo
@Mixin(targets = "pigcart.particlerain.particle.CustomParticle", remap = false)
public abstract class CustomParticleMixin extends TextureSheetParticle {
    @Unique
    private static final float[] CORNER_X = {1.0f, 1.0f, -1.0f, -1.0f};
    @Unique
    private final float[] embeddium$cornerY = new float[4], embeddium$u = new float[4], embeddium$v = new float[4];

    protected CustomParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "renderSquishyRotatedQuad", at = @At("HEAD"), cancellable = true, require = 0)
    private void embeddium$writeSquishyQuadFast(VertexConsumer consumer, Quaternionf quaternion, float x, float y, float z, float tickPercent, float squish, CallbackInfo ci) {
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        float[] cy = this.embeddium$cornerY, u = this.embeddium$u, v = this.embeddium$v;
        cy[0] = -squish; u[0] = u1; v[0] = v1;
        cy[1] = squish; u[1] = u1; v[1] = v0;
        cy[2] = squish; u[2] = u0; v[2] = v0;
        cy[3] = -squish; u[3] = u0; v[3] = v1;
        if (ParticleRainQuads.write(consumer, quaternion, x, y, z, this.getQuadSize(tickPercent), CORNER_X, cy, u, v,
                this.rCol, this.gCol, this.bCol, this.alpha, this.getLightColor(tickPercent))) {
            ci.cancel();
        }
    }
}
