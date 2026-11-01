package org.embeddedt.embeddium.compat.remnantminimaps;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Remnant Minimaps re-scans every chunk within render distance in an endless sweep (three chunks a tick plus the 3x3
 * around the player every ten ticks), each column reading blocks and biome-blended tints, whether or not anything in
 * the chunk changed. This remembers when each chunk was last scanned for the current slice and skips re-queueing it
 * until a block in it changes, it is reloaded, the slice changes, or {@link #MAX_AGE_TICKS} pass (so slow color
 * changes such as seasons still reach the map). Render thread only.
 */
public final class RemnantScanGate {
    // Toggled by the dev benchmark to A/B the gate and the label batching in UiMixin
    public static boolean enabled = true;
    private static final long MAX_AGE_TICKS = 1200;

    private static final Long2LongOpenHashMap SCANNED_AT = new Long2LongOpenHashMap();
    private static int sliceY = Integer.MIN_VALUE + 1;
    private static boolean cave;
    private static long gameTime;

    static {
        SCANNED_AT.defaultReturnValue(Long.MIN_VALUE);
    }

    private RemnantScanGate() {
    }

    public static void beginTick(int slice, boolean caveMode, long time) {
        gameTime = time;
        if (slice != sliceY || caveMode != cave) {
            sliceY = slice;
            cave = caveMode;
            SCANNED_AT.clear();
        }
    }

    public static boolean isFresh(long chunkPos) {
        if (!enabled) {
            return false;
        }
        long at = SCANNED_AT.get(chunkPos);
        return at != Long.MIN_VALUE && gameTime - at < MAX_AGE_TICKS && gameTime >= at;
    }

    public static void markScanned(long chunkPos) {
        SCANNED_AT.put(chunkPos, gameTime);
    }

    public static void invalidate(long chunkPos) {
        SCANNED_AT.remove(chunkPos);
    }

    public static void onBlockChanged(BlockPos pos) {
        if (!SCANNED_AT.isEmpty()) {
            SCANNED_AT.remove(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
    }

    public static void reset() {
        SCANNED_AT.clear();
    }
}
