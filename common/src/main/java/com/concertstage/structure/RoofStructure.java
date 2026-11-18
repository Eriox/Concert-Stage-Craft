package com.concertstage.structure;

import com.concertstage.block.ring.CellOwners;
import com.concertstage.block.ring.RingTrussCellBlock;
import com.concertstage.block.ring.RingTrussCellBlockEntity;
import com.concertstage.block.roof.RoofTrussBlock;
import com.concertstage.block.roof.RoofTrussBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * CSC Structure Manager (1.0.2): a stage roof seen as one structure. A roof is made of feet (CSC RoofTruss frames, SkyRoof / TitanRoof
 * kits: the blocks that hold the settings and draw the roof) and of the invisible cells that give it its collision and stop light
 * beams (each cell knows its foot). Two feet belong to the same roof when their blocks touch (a sheet laid over the next frame, frames
 * side by side...). Nothing is stored: the grouping is found again each time from the blocks, so it works with roofs built before
 * 1.0.2. Cells left over by an older version (no foot owns them any more) touching the roof are found too and removed with it.
 * The same code runs on the client (preview, confirmation screen) and on the server (removal).
 */
public final class RoofStructure {
    public static final int MAX_FEET = 256, MAX_CELLS = 250_000;
    /** Whole roof (every foot touching), or the clicked frame only. */
    public static final int WHOLE = 0, SINGLE = 1;

    public final List<BlockPos> feet = new ArrayList<>();
    public final Set<BlockPos> cells = new LinkedHashSet<>();
    public final Set<BlockPos> orphans = new LinkedHashSet<>();
    public boolean truncated;
    /** Only left-over cells (no roof). */
    public boolean orphanOnly;

    private RoofStructure() {}

    public int blockCount() {
        return feet.size() + cells.size() + orphans.size();
    }

    /** Items given back: one per foot, by foot block. */
    public Map<Item, Integer> items(Level level) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (BlockPos f : feet) out.merge(level.getBlockState(f).getBlock().asItem(), 1, Integer::sum);
        return out;
    }

    public AABB bounds() {
        AABB box = null;
        for (BlockPos p : feet) box = grow(box, p);
        for (BlockPos p : cells) box = grow(box, p);
        for (BlockPos p : orphans) box = grow(box, p);
        return box;
    }

    private static AABB grow(AABB box, BlockPos p) {
        AABB b = new AABB(p);
        return box == null ? b : box.minmax(b);
    }

    /** Same structure as confirmed by the player (checked again by the server before it removes anything). */
    public int signature() {
        int h = 17;
        List<BlockPos> sorted = new ArrayList<>(feet);
        sorted.sort(Comparator.comparingLong(BlockPos::asLong));
        for (BlockPos p : sorted) h = h * 31 + Long.hashCode(p.asLong());
        return h * 31 + orphans.size() * 7 + (orphanOnly ? 1 : 0);
    }

    /** Foot of the roof the clicked block belongs to (the foot itself or one of its cells), or null. */
    public static BlockPos footOf(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof RoofTrussBlock) return pos;
        if (level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity c && c.anchor() != null
            && level.getBlockEntity(c.anchor()) instanceof RoofTrussBlockEntity) return c.anchor();
        return null;
    }

    /** The structure the clicked block belongs to, or null when it is not part of a roof (nor a left-over cell). */
    public static RoofStructure find(Level level, BlockPos clicked, int mode) {
        RoofStructure s = new RoofStructure();
        BlockPos seed = footOf(level, clicked);
        if (seed == null) {
            if (!(level.getBlockState(clicked).getBlock() instanceof RingTrussCellBlock) || !RingTrussCellBlock.orphan(level, clicked)) return null;
            s.orphanOnly = true;
            s.collectOrphans(level, clicked);
            return s;
        }
        Set<BlockPos> feetSeen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        feetSeen.add(seed);
        while (!queue.isEmpty()) {
            BlockPos foot = queue.poll();
            if (!(level.getBlockEntity(foot) instanceof RoofTrussBlockEntity roof)) continue;
            if (s.feet.size() >= MAX_FEET) { s.truncated = true; break; }
            s.feet.add(foot);
            List<BlockPos> mine = new ArrayList<>();
            mine.add(foot);
            for (BlockPos c : roof.cellPositions())
                if (level.isLoaded(c) && level.getBlockEntity(c) instanceof RingTrussCellBlockEntity cell && foot.equals(cell.anchor())) mine.add(c);
            for (int i = 1; i < mine.size(); i++) s.cells.add(mine.get(i));
            if (s.cells.size() > MAX_CELLS) { s.truncated = true; break; }
            // what touches this frame: left-over cells (always removed with it), other feet and their cells (whole roof only)
            BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
            for (BlockPos p : mine)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            n.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                            if (s.cells.contains(n) || s.orphans.contains(n) || feetSeen.contains(n) || !level.isLoaded(n)) continue;
                            BlockState st = level.getBlockState(n);
                            if (st.getBlock() instanceof RoofTrussBlock) {
                                if (mode == SINGLE) continue;
                                BlockPos f = n.immutable();
                                if (feetSeen.add(f)) queue.add(f);
                            } else if (st.getBlock() instanceof RingTrussCellBlock && level.getBlockEntity(n) instanceof RingTrussCellBlockEntity c) {
                                BlockPos a = c.anchor();
                                if (a == null || RingTrussCellBlock.orphan(level, n)) s.collectOrphans(level, n.immutable());
                                else if (mode != SINGLE && level.getBlockEntity(a) instanceof RoofTrussBlockEntity && feetSeen.add(a)) queue.add(a);
                            }
                        }
        }
        return s;
    }

    /** Left-over cells connected to {@code start}. */
    private void collectOrphans(Level level, BlockPos start) {
        Deque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        orphans.add(start);
        while (!q.isEmpty()) {
            if (orphans.size() > MAX_CELLS) { truncated = true; return; }
            BlockPos p = q.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (orphans.contains(n) || !level.isLoaded(n)) continue;
                        if (level.getBlockState(n).getBlock() instanceof RingTrussCellBlock && RingTrussCellBlock.orphan(level, n)) {
                            orphans.add(n);
                            q.add(n);
                        }
                    }
        }
    }

    /** Is the cell owned (helper for the cleaning of an area). */
    static boolean owned(Level level, BlockPos cell) {
        return level.getBlockEntity(cell) instanceof RingTrussCellBlockEntity c && c.anchor() != null && CellOwners.owns(level, c.anchor(), cell);
    }
}
