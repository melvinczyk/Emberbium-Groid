package me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline;

import me.jellysquid.mods.sodium.client.model.color.ColorProviderRegistry;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelShaper;

/**
 * Holds important caches and working data structures for a single chunk meshing thread. All objects within
 * this class do not need to be thread-safe or lightweight to construct, as a separate instance is allocated per thread
 * and reused for the lifetime of that thread.
 */
public class BlockRenderCache {
    private final ArrayLightDataCache lightDataCache;

    private final BlockRenderer blockRenderer;
    private final FluidRenderer fluidRenderer;
    private final LightPipelineProvider lightPipelineProvider;

    private final BlockModelShaper blockModels;
    private final WorldSlice worldSlice;

    public BlockRenderCache(Minecraft client, ClientLevel world) {
        this.worldSlice = new WorldSlice(world);
        this.lightDataCache = new ArrayLightDataCache(this.worldSlice);

        LightPipelineProvider lightPipelineProvider = new LightPipelineProvider(this.lightDataCache);

        var colorRegistry = new ColorProviderRegistry(client.getBlockColors());

        this.blockRenderer = new BlockRenderer(colorRegistry, lightPipelineProvider);
        this.fluidRenderer = new FluidRenderer(colorRegistry, lightPipelineProvider);
        this.lightPipelineProvider = lightPipelineProvider;

        this.blockModels = client.getModelManager().getBlockModelShaper();
    }

    public BlockModelShaper getBlockModels() {
        return this.blockModels;
    }

    public BlockRenderer getBlockRenderer() {
        return this.blockRenderer;
    }

    public FluidRenderer getFluidRenderer() {
        return this.fluidRenderer;
    }

    /**
     * Initialize the render cache for a new chunk.
     */
    public void init(ChunkRenderContext context) {
        this.lightDataCache.reset(context.getOrigin());
        this.lightPipelineProvider.reset();
        this.worldSlice.copyData(context);
    }

    // Per block state: 1 = model has only face-culled quads (safe to skip when enclosed), 2 = not safe
    private final it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap<net.minecraft.world.level.block.state.BlockState> enclosedSkippable = new it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap<>();
    private final net.minecraft.core.BlockPos.MutableBlockPos neighborPos = new net.minecraft.core.BlockPos.MutableBlockPos();
    private final net.minecraft.util.RandomSource probeRandom = new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(42L);

    /**
     * Whether a block's model can be skipped entirely because it is an opaque full cube buried on all six sides by
     * other opaque full cubes. Every one of its faces would be culled, so meshing it only costs time. Blocks whose
     * models contain quads that are not tied to a face (which could poke outside the cube) are never skipped, nor are
     * blocks with block entities (whose models often depend on dynamic model data).
     */
    public boolean isEnclosedAndSkippable(net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        if (!me.jellysquid.mods.sodium.client.SodiumClientMod.options().performance.useBlockFaceCulling
                || !me.jellysquid.mods.sodium.client.SodiumClientMod.options().performance.useEnclosedBlockSkipping) {
            return false;
        }

        WorldSlice slice = this.worldSlice;

        if (state.hasBlockEntity() || !state.isSolidRender(slice, pos)) {
            return false;
        }

        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        var adj = this.neighborPos;

        for (net.minecraft.core.Direction dir : me.jellysquid.mods.sodium.client.util.DirectionUtil.ALL_DIRECTIONS) {
            adj.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
            if (!slice.getBlockState(adj).isSolidRender(slice, adj)) {
                return false;
            }
        }

        byte cached = this.enclosedSkippable.getByte(state);

        if (cached == 0) {
            cached = this.hasOnlyCulledQuads(state) ? (byte) 1 : (byte) 2;
            this.enclosedSkippable.put(state, cached);
        }

        return cached == 1;
    }

    private boolean hasOnlyCulledQuads(net.minecraft.world.level.block.state.BlockState state) {
        try {
            var model = this.blockModels.getBlockModel(state);

            if (org.embeddedt.embeddium.render.frapi.FRAPIModelUtils.isFRAPIModel(model)) {
                return false;
            }

            var data = net.minecraftforge.client.model.data.ModelData.EMPTY;
            this.probeRandom.setSeed(42L);

            for (var layer : model.getRenderTypes(state, this.probeRandom, data)) {
                this.probeRandom.setSeed(42L);
                if (!model.getQuads(state, null, this.probeRandom, data, layer).isEmpty()) {
                    return false;
                }
            }

            return true;
        } catch (RuntimeException e) {
            // Some models expect real model data; never skip those
            return false;
        }
    }

    public WorldSlice getWorldSlice() {
        return this.worldSlice;
    }

    public void cleanup() {
        this.worldSlice.reset();
    }
}
