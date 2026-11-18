package com.concertstage.block.ring;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The invisible collision / beam cells ({@link RingTrussCellBlock}) belong to a circular truss or a roof frame (their anchor). Helpers
 * shared by both owners (1.0.2): who owns a cell, keeping track of the cells in unloaded chunks, and removing every cell of an owner,
 * even the ones its list lost (they used to stay in the world, invisible, after the roof was taken down).
 */
public final class CellOwners {
    /** Largest volume swept when an owner removes its cells (a 96-block roof fits). */
    private static final long MAX_SWEEP = 600_000;

    private CellOwners() {}

    /** True when the block entity at {@code anchor} is a cell owner that has {@code cell} in its list. */
    public static boolean owns(Level level, BlockPos anchor, BlockPos cell) {
        BlockEntity be = level.getBlockEntity(anchor);
        if (be instanceof RingTrussBlockEntity r) return r.ownsCell(cell);
        if (be instanceof com.concertstage.block.roof.RoofTrussBlockEntity r) return r.ownsCell(cell);
        if (be instanceof com.concertstage.audio.SpeakerBlockEntity s) return s.ownsCell(cell);
        return false;
    }

    /** True when there is a cell owner (loaded) at {@code anchor}. */
    public static boolean isOwner(Level level, BlockPos anchor) {
        BlockEntity be = level.getBlockEntity(anchor);
        return be instanceof RingTrussBlockEntity || be instanceof com.concertstage.block.roof.RoofTrussBlockEntity
            || be instanceof com.concertstage.audio.SpeakerBlockEntity;
    }

    public static Set<Long> set(long[] cells) {
        Set<Long> s = new HashSet<>(cells.length * 2);
        for (long c : cells) s.add(c);
        return s;
    }

    /**
     * Removes the listed cells, then every other cell anchored to {@code anchor} inside {@code box} (loaded chunks only). Removing a cell
     * also updates the light there.
     */
    public static int sweep(Level level, BlockPos anchor, long[] listed, AABB box) {
        if (level == null || level.isClientSide) return 0;
        int removed = 0;
        for (long rel : listed) {
            BlockPos q = anchor.offset(BlockPos.of(rel));
            if (level.isLoaded(q) && level.getBlockState(q).getBlock() instanceof RingTrussCellBlock) {
                if (!(level.getBlockEntity(q) instanceof RingTrussCellBlockEntity c) || anchor.equals(c.anchor())) {
                    level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                    removed++;
                }
            }
        }
        if (box == null) return removed;
        int x0 = (int) Math.floor(box.minX), y0 = Math.max(level.getMinBuildHeight(), (int) Math.floor(box.minY)), z0 = (int) Math.floor(box.minZ);
        int x1 = (int) Math.floor(box.maxX), y1 = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(box.maxY)), z1 = (int) Math.floor(box.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_SWEEP) return removed;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                q.set(x, 0, z);
                if (!level.isLoaded(q)) continue;
                for (int y = y0; y <= y1; y++) {
                    q.set(x, y, z);
                    if (!(level.getBlockState(q).getBlock() instanceof RingTrussCellBlock)) continue;
                    if (level.getBlockEntity(q) instanceof RingTrussCellBlockEntity c && anchor.equals(c.anchor())) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                        removed++;
                    }
                }
            }
        return removed;
    }
}
