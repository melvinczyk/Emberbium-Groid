package org.embeddedt.embeddium.compat.metusoblita;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Metus Oblita's MCreator render procedures each scan every loaded chunk within render distance, and every block
 * entity in those chunks, on every frame, to find their own blocks. That cost grows with the square of the render
 * distance even when none of its blocks are anywhere nearby. This performs one shared scan at most every
 * {@link #RESCAN_TICKS} client ticks and lets the procedures skip their work while no Metus Oblita block entity is
 * loaded within render distance.
 */
public final class MetusOblitaRenderGate {
    private static final String NAMESPACE = "metus_oblita";
    private static final int RESCAN_TICKS = 10;

    private static final Map<BlockEntityType<?>, Boolean> IS_OURS = new IdentityHashMap<>();

    private static long lastScanTick = Long.MIN_VALUE;
    private static Object lastScanLevel;
    private static boolean present = true;

    private MetusOblitaRenderGate() { }

    /**
     * @return false only when it is certain that no Metus Oblita block entity is loaded within render distance
     */
    // Runtime switch, used by the benchmark harness to compare with and without the gate
    public static volatile boolean enabled = true;

    public static boolean anyBlockEntitiesNearby() {
        if (!enabled) {
            return true;
        }

        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;

        if (level == null || player == null) {
            return true;
        }

        long tick = level.getGameTime();

        if (level == lastScanLevel && tick - lastScanTick < RESCAN_TICKS && tick >= lastScanTick) {
            return present;
        }

        lastScanLevel = level;
        lastScanTick = tick;
        present = scan(level, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4, mc.options.getEffectiveRenderDistance());
        return present;
    }

    private static boolean scan(net.minecraft.client.multiplayer.ClientLevel level, int centerX, int centerZ, int radius) {
        var chunks = level.getChunkSource();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = chunks.getChunk(x, z, ChunkStatus.FULL, false);

                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (isOurs(blockEntity.getType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isOurs(BlockEntityType<?> type) {
        Boolean cached = IS_OURS.get(type);

        if (cached == null) {
            ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            cached = key != null && NAMESPACE.equals(key.getNamespace());
            IS_OURS.put(type, cached);
        }

        return cached;
    }
}
