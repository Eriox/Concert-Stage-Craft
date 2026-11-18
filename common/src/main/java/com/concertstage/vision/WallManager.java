package com.concertstage.vision;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * CSC 1.3.0 - automatic assembly of the LED walls. When a panel is placed or removed, the panels connected to it (sharing an edge in
 * the same plane, facing the same way) are gathered: they form one wall with one id, one continuous image and one set of settings.
 * Merging two walls keeps the settings of the larger one; splitting a wall gives the largest part the old id and copies its settings to
 * the others. Rectangular bounding area; holes are allowed (the missing panels just show nothing). The first version handles flat walls
 * (the planes of the four horizontal facings); the registry ({@link WallData}) is ready for other layouts (a curved wall would be a
 * list of planes).
 *
 * <p>Cost: one flood fill over the wall when a panel changes (at most {@link WallData#MAX_SIDE}² panels), nothing per tick. Panels in
 * unloaded chunks are left as they are (the previous rectangle is kept for them).
 */
public final class WallManager {
    private static final int MAX_PANELS = WallData.MAX_SIDE * WallData.MAX_SIDE;
    private static final java.util.Random RANDOM = new java.util.Random();

    private WallManager() {}

    static int u(BlockPos p, Direction facing) { return facing.getAxis() == Direction.Axis.Z ? p.getX() : p.getZ(); }
    static int plane(BlockPos p, Direction facing) { return facing.getAxis() == Direction.Axis.Z ? p.getZ() : p.getX(); }

    private static boolean isPanel(ServerLevel level, BlockPos p, Direction facing) {
        if (!level.isLoaded(p)) return false;
        var s = level.getBlockState(p);
        return s.getBlock() instanceof LedPanelBlock && s.getValue(VisionBlock.FACING) == facing;
    }

    /** The panels connected to {@code start} (same facing, same plane). */
    private static List<BlockPos> gather(ServerLevel level, BlockPos start, Direction facing) {
        List<BlockPos> out = new ArrayList<>();
        if (!isPanel(level, start, facing)) return out;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        Direction side = facing.getClockWise();
        while (!q.isEmpty() && out.size() < MAX_PANELS) {
            BlockPos p = q.poll();
            out.add(p);
            for (BlockPos n : new BlockPos[] {p.above(), p.below(), p.relative(side), p.relative(side.getOpposite())}) {
                if (seen.add(n) && isPanel(level, n, facing)) q.add(n);
            }
        }
        return out;
    }

    public static void panelChanged(ServerLevel level, BlockPos pos) {
        var s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof LedPanelBlock)) return;
        rebuild(level, gather(level, pos, s.getValue(VisionBlock.FACING)), s.getValue(VisionBlock.FACING), new HashSet<>());
    }

    public static void panelRemoved(ServerLevel level, BlockPos pos, Direction facing) {
        Direction side = facing.getClockWise();
        Set<BlockPos> done = new HashSet<>();
        Set<Long> used = new HashSet<>();
        WallData data = WallData.get(level);
        // the wall the panel belonged to: the id of a neighbour (the removed panel's block entity is gone)
        List<List<BlockPos>> parts = new ArrayList<>();
        for (BlockPos n : new BlockPos[] {pos.above(), pos.below(), pos.relative(side), pos.relative(side.getOpposite())}) {
            if (done.contains(n) || !isPanel(level, n, facing)) continue;
            List<BlockPos> g = gather(level, n, facing);
            done.addAll(g);
            parts.add(g);
        }
        parts.sort((a, b) -> b.size() - a.size());                       // the largest part keeps the id
        for (List<BlockPos> g : parts) rebuild(level, g, facing, used);
        // a wall whose last panel went away
        List<Long> empty = new ArrayList<>();
        for (WallData.Wall w : data.walls.values()) {
            if (w.facing != facing || w.plane != plane(pos, facing)) continue;
            if (!w.has(u(pos, facing), pos.getY())) continue;
            if (!used.contains(w.id)) {
                boolean any = false;
                for (int v = w.v0; v <= w.v1 && !any; v++)
                    for (int uu = w.u0; uu <= w.u1 && !any; uu++)
                        if (w.has(uu, v) && !(w.pos(uu, v).equals(pos)) && isPanel(level, w.pos(uu, v), facing)) any = true;
                if (!any) empty.add(w.id);
            }
        }
        for (long id : empty) {
            data.walls.remove(id);
            VisionNet.wallRemoved(level, id);
        }
        data.setDirty();
    }

    /** Makes {@code group} one wall: picks its id, writes the rectangle and the panels, tells the panels and the players. */
    private static void rebuild(ServerLevel level, List<BlockPos> group, Direction facing, Set<Long> used) {
        if (group.isEmpty()) return;
        WallData data = WallData.get(level);
        Map<Long, Integer> votes = new HashMap<>();
        for (BlockPos p : group) {
            if (level.getBlockEntity(p) instanceof LedPanelBlockEntity be && be.wallId() != 0 && data.walls.containsKey(be.wallId()))
                votes.merge(be.wallId(), 1, Integer::sum);
        }
        long id = 0;
        int best = -1;
        for (var e : votes.entrySet()) {
            if (used.contains(e.getKey())) continue;
            WallData.Wall w = data.walls.get(e.getKey());
            int score = e.getValue() * 100000 + (w == null ? 0 : w.count);
            if (score > best) { best = score; id = e.getKey(); }
        }
        WallData.Wall source = null;
        if (id == 0 && !votes.isEmpty()) source = data.walls.get(votes.keySet().iterator().next());     // split: copy the settings
        if (id == 0) {
            do id = RANDOM.nextLong() & Long.MAX_VALUE; while (id == 0 || data.walls.containsKey(id));
        }
        used.add(id);
        WallData.Wall w = data.walls.computeIfAbsent(id, k -> new WallData.Wall());
        boolean fresh = w.id == 0 && source == null;
        if (w.id == 0) {
            w.id = id;
            if (source != null) {
                w.name = source.name + " (2)"; w.brightness = source.brightness; w.processor = source.processor; w.output = source.output;
                w.angle = source.angle; w.pivot = source.pivot; w.quality = source.quality; w.fps = source.fps;
            } else {
                w.name = "LED " + (data.walls.size());
            }
        }
        // merged walls: their ids disappear (their panels now carry this one)
        for (long other : votes.keySet()) {
            if (other != id && !used.contains(other)) {
                WallData.Wall o = data.walls.get(other);
                if (o != null && w.processor == 0 && o.processor != 0) { w.processor = o.processor; w.output = o.output; }
                data.walls.remove(other);
                VisionNet.wallRemoved(level, other);
            }
        }
        w.facing = facing;
        w.plane = plane(group.get(0), facing);
        int u0 = Integer.MAX_VALUE, v0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, v1 = Integer.MIN_VALUE;
        for (BlockPos p : group) {
            int uu = u(p, facing);
            u0 = Math.min(u0, uu); u1 = Math.max(u1, uu); v0 = Math.min(v0, p.getY()); v1 = Math.max(v1, p.getY());
        }
        // limit the size (MAX_SIDE x MAX_SIDE): panels beyond stay alone
        u1 = Math.min(u1, u0 + WallData.MAX_SIDE - 1);
        v1 = Math.min(v1, v0 + WallData.MAX_SIDE - 1);
        w.u0 = u0; w.v0 = v0; w.u1 = u1; w.v1 = v1;
        w.panels = new java.util.BitSet();
        int count = 0;
        for (BlockPos p : group) {
            int uu = u(p, facing), v = p.getY();
            if (uu > u1 || v > v1) continue;
            w.panels.set((v - v0) * w.width() + (uu - u0));
            count++;
            if (level.getBlockEntity(p) instanceof LedPanelBlockEntity be) be.setWallId(id);
        }
        w.count = count;
        if (fresh) VisionAutoLink.wallCreated(level, w);          // rev. 2: a new wall is fed by the nearest processor
        data.setDirty();
        VisionNet.wallChanged(level, w);
    }
}
