package me.jellysquid.mods.sodium.mixin.features.render.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.embeddedt.embeddium.impl.render.world.AmbientBiomeGate;
import org.embeddedt.embeddium.impl.render.world.AmbientBiomeShortcut;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.function.Predicate;

import java.util.function.Consumer;
import java.util.function.Supplier;

// Use a very low priority so most injects into doAnimateTick will still work
@Mixin(value = ClientLevel.class, priority = 500)
public abstract class ClientLevelMixin extends Level {
    protected ClientLevelMixin(WritableLevelData p_270739_, ResourceKey<Level> p_270683_, RegistryAccess p_270200_, Holder<DimensionType> p_270240_, Supplier<ProfilerFiller> p_270692_, boolean p_270904_, boolean p_270470_, long p_270248_, int p_270466_) {
        super(p_270739_, p_270683_, p_270200_, p_270240_, p_270692_, p_270904_, p_270470_, p_270248_, p_270466_);
    }

    @Shadow
    private void lambda$doAnimateTick$8(BlockPos.MutableBlockPos pos, AmbientParticleSettings settings) {
        throw new AssertionError();
    }

    private BlockPos.MutableBlockPos embeddium$particlePos;
    private final Consumer<AmbientParticleSettings> embeddium$particleSettingsConsumer = settings -> lambda$doAnimateTick$8(embeddium$particlePos, settings);

    @Shadow
    protected abstract void trySpawnDripParticles(BlockPos p_104690_, BlockState p_104691_, ParticleOptions p_104692_, boolean p_104693_);

    // Biome returned for this tick's ambient particle lookups when no reachable biome has ambient particles; null
    // when the real lookup is needed. See AmbientBiomeShortcut.
    private Holder<Biome> embeddium$ambientStandIn;

    private static final Predicate<Holder<Biome>> EMBEDDIUM$HAS_AMBIENT = biome -> biome.value().getAmbientParticle().isPresent();

    // doAnimateTick picks positions up to 31 blocks away and the fuzzy lookup reads quart cells up to 8 blocks
    // further, so every lookup lands in a section within this many blocks of the center
    private static final int EMBEDDIUM$AMBIENT_REACH = 40;

    @Inject(method = "animateTick", at = @At("HEAD"))
    private void embeddium$checkAmbientBiomes(int posX, int posY, int posZ, CallbackInfo ci) {
        this.embeddium$ambientStandIn = AmbientBiomeGate.enabled ? this.embeddium$findStandIn(posX, posY, posZ) : null;
    }

    private Holder<Biome> embeddium$plains;

    /**
     * Vanilla answers every biome read in an unloaded chunk with two registry lookups for plains. Fog and sky color
     * sampling, biome tints and minimaps read past the loaded edge every frame, so the holder is kept per level (its
     * registries never change).
     */
    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void embeddium$cachedPlains(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        cir.setReturnValue(this.embeddium$plains());
    }

    private Holder<Biome> embeddium$plains() {
        Holder<Biome> plains = this.embeddium$plains;
        if (plains == null) {
            plains = this.embeddium$plains = this.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        }
        return plains;
    }

    private Holder<Biome> embeddium$findStandIn(int x, int y, int z) {
        // Unloaded chunks answer with plains, so plains must have no ambient particles either; it is also the stand-in
        Holder<Biome> plains = this.embeddium$plains();
        if (EMBEDDIUM$HAS_AMBIENT.test(plains)) {
            return null;
        }
        int r = EMBEDDIUM$AMBIENT_REACH;
        int minSection = this.getSectionIndex(y - r), maxSection = this.getSectionIndex(y + r);
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                LevelChunk chunk = this.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk c ? c : null;
                if (chunk == null) {
                    continue;
                }
                var sections = chunk.getSections();
                // Biome reads clamp to the world's height, so out-of-range sections are the top or bottom one
                for (int i = Math.max(0, minSection); i <= Math.min(sections.length - 1, maxSection); i++) {
                    if (sections[i].getBiomes().maybeHas(EMBEDDIUM$HAS_AMBIENT)) {
                        return null;
                    }
                }
            }
        }
        return plains;
    }

    /**
     * @author embeddedt
     * @reason Use singlethreaded random to avoid AtomicLong overhead
     */
    @Redirect(method = "animateTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"))
    private RandomSource createLocal() {
        return new SingleThreadedRandomSource(RandomSupport.generateUniqueSeed());
    }

    /**
     * @author embeddedt
     * @reason Avoid allocations & do some misc optimizations. Partially based on old Sodium 0.2 mixin
     */
    @Overwrite
    public void doAnimateTick(int xCenter, int yCenter, int zCenter, int radius, RandomSource random, @Nullable Block markerBlock, BlockPos.MutableBlockPos pos) {
        int x = xCenter + (random.nextInt(radius) - random.nextInt(radius));
        int y = yCenter + (random.nextInt(radius) - random.nextInt(radius));
        int z = zCenter + (random.nextInt(radius) - random.nextInt(radius));

        pos.set(x, y, z);

        BlockState blockState = this.getBlockState(pos);

        blockState.getBlock().animateTick(blockState, this, pos, random);

        FluidState fluidState = blockState.getFluidState();

        if (!fluidState.isEmpty()) {
            fluidState.animateTick(this, pos, random);
            ParticleOptions particleoptions = fluidState.getDripParticle();
            if (particleoptions != null && random.nextInt(10) == 0) {
                boolean flag = blockState.isFaceSturdy(this, pos, Direction.DOWN);
                BlockPos blockpos = pos.below();
                this.trySpawnDripParticles(blockpos, this.getBlockState(blockpos), particleoptions, flag);
            }
        }

        if (blockState.getBlock() == markerBlock) {
            this.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, blockState), (double)x + 0.5D, (double)y + 0.5D, (double)z + 0.5D, 0.0D, 0.0D, 0.0D);
        }

        if (!blockState.isCollisionShapeFullBlock(this, pos)) {
            // This dance looks ridiculous over just calling the lambda, but it's needed because mod mixins target the ifPresent call.
            // The important part (skipping the allocation) still happens.
            embeddium$particlePos = pos;
            Holder<Biome> standIn = this.embeddium$ambientStandIn;
            if (standIn != null) {
                ((AmbientBiomeShortcut) this.getBiomeManager()).embeddium$setShortcut(standIn, Thread.currentThread());
            }
            try {
                this.getBiome(pos).value().getAmbientParticle().ifPresent(embeddium$particleSettingsConsumer);
            } finally {
                if (standIn != null) {
                    ((AmbientBiomeShortcut) this.getBiomeManager()).embeddium$setShortcut(null, null);
                }
            }
        }
    }
}
