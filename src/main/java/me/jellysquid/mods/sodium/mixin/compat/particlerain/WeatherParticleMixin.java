package me.jellysquid.mods.sodium.mixin.compat.particlerain;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import org.embeddedt.embeddium.compat.particlerain.ParticleRainQuads;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle Rain's weather particles: direct vertex writes (see {@link ParticleRainQuads}), and the light level looked
 * up once per tick instead of every frame. A particle only moves on ticks, and vanilla reads light at its current
 * block regardless of the partial tick.
 */
@Pseudo
@Mixin(targets = "pigcart.particlerain.particle.WeatherParticle", remap = false)
public abstract class WeatherParticleMixin extends TextureSheetParticle {
    @Unique
    private static final float[] CORNER_X = {-1.0f, -1.0f, 1.0f, 1.0f}, CORNER_Y = {-1.0f, 1.0f, 1.0f, -1.0f};
    @Unique
    private final float[] embeddium$u = new float[4], embeddium$v = new float[4];
    @Unique
    private int embeddium$lightAge = -1;
    @Unique
    private long embeddium$lightPos;
    @Unique
    private int embeddium$light;

    protected WeatherParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Override
    public int getLightColor(float partialTick) {
        long pos = BlockPos.asLong((int) Math.floor(this.x), (int) Math.floor(this.y), (int) Math.floor(this.z));
        if (this.embeddium$lightAge != this.age || this.embeddium$lightPos != pos) {
            this.embeddium$lightAge = this.age;
            this.embeddium$lightPos = pos;
            this.embeddium$light = super.getLightColor(partialTick);
        }
        return this.embeddium$light;
    }

    @Inject(method = "renderRotatedQuad", at = @At("HEAD"), cancellable = true, require = 0)
    private void embeddium$writeQuadFast(VertexConsumer consumer, Quaternionf quaternion, float x, float y, float z, float tickPercent, CallbackInfo ci) {
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        float[] u = this.embeddium$u, v = this.embeddium$v;
        u[0] = u1; v[0] = v1;
        u[1] = u1; v[1] = v0;
        u[2] = u0; v[2] = v0;
        u[3] = u0; v[3] = v1;
        // Particle Rain turns the quad around before writing it; keep that, including on the caller's quaternion
        quaternion.rotateY((float) Math.PI);
        if (ParticleRainQuads.write(consumer, quaternion, x, y, z, this.getQuadSize(tickPercent), CORNER_X, CORNER_Y, u, v,
                this.rCol, this.gCol, this.bCol, this.alpha, this.getLightColor(tickPercent))) {
            ci.cancel();
        } else {
            // Undo the turn so the original method applies it once
            quaternion.rotateY((float) -Math.PI);
        }
    }
}
