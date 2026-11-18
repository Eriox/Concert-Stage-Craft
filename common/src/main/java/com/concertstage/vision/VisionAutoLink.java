package com.concertstage.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * CSC 1.3.0 rev. 2 — LiveVision set up by itself (asked 12 November: "simplifier l'utilisation du LiveVision"). Before, a show needed
 * every link made by hand: each wall to a processor output, each camera to a source of the regie. Now, within {@link #REACH} blocks:
 * <ul>
 *   <li>a new LED wall is fed by OUT 1 of the nearest processor;</li>
 *   <li>a processor placed takes every wall around that has no source, and puts the nearest camera on OUT 1;</li>
 *   <li>a camera placed goes on OUT 1 of the nearest processor when OUT 1 shows no camera yet, and joins its automatic switcher list.</li>
 * </ul>
 * Everything stays changeable in the wall window and in the regie; nothing already linked is changed.
 */
public final class VisionAutoLink {
    public static final double REACH = 96;

    private VisionAutoLink() {}

    /** The loaded processor nearest to {@code at}, within REACH, or null. */
    static VisionProcessorBlockEntity nearestProcessor(ServerLevel level, BlockPos at) {
        VisionProcessorBlockEntity best = null;
        double bd = REACH * REACH;
        int r = (int) Math.ceil(REACH / 16);
        for (int x = (at.getX() >> 4) - r; x <= (at.getX() >> 4) + r; x++) {
            for (int z = (at.getZ() >> 4) - r; z <= (at.getZ() >> 4) + r; z++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(x, z);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof VisionProcessorBlockEntity p)) continue;
                    double d = be.getBlockPos().distSqr(at);
                    if (d < bd) { bd = d; best = p; }
                }
            }
        }
        return best;
    }

    /** Centre of a wall (block position). */
    static BlockPos centre(WallData.Wall w) {
        int u = (w.u0 + w.u1) / 2, v = (w.v0 + w.v1) / 2;
        return w.facing.getAxis() == Direction.Axis.Z ? new BlockPos(u, v, w.plane) : new BlockPos(w.plane, v, u);
    }

    /** A new wall: fed by OUT 1 of the nearest processor. */
    static void wallCreated(ServerLevel level, WallData.Wall w) {
        if (w.processor != 0) return;
        VisionProcessorBlockEntity p = nearestProcessor(level, centre(w));
        if (p == null) return;
        w.processor = p.getBlockPos().asLong();
        w.output = 0;
    }

    /** A processor placed: the walls around without source, and the nearest camera on OUT 1. */
    public static void processorPlaced(ServerLevel level, VisionProcessorBlockEntity p) {
        WallData d = WallData.get(level);
        BlockPos at = p.getBlockPos();
        for (WallData.Wall w : d.walls.values()) {
            if (w.processor != 0 || centre(w).distSqr(at) > REACH * REACH) continue;
            w.processor = at.asLong();
            w.output = 0;
            d.setDirty();
            VisionNet.wallChanged(level, w);
        }
        CameraData cams = CameraData.get(level);
        long nearest = 0;
        double bd = REACH * REACH;
        OutputState o = p.outputs[0];
        for (CameraData.Cam c : cams.cams.values()) {
            double dd = BlockPos.of(c.pos()).distSqr(at);
            if (dd > REACH * REACH) continue;
            if (!o.autoCams.contains(c.pos())) o.autoCams.add(c.pos());
            if (dd < bd) { bd = dd; nearest = c.pos(); }
        }
        if (nearest != 0 && !o.program.startsWith("cam:")) p.cut(0, OutputState.cam(BlockPos.of(nearest)), level.getGameTime());
        else p.changed();
    }

    /** A camera placed: on OUT 1 of the nearest processor if it shows no camera, and in its automatic switcher list. */
    public static void cameraPlaced(ServerLevel level, BlockPos cam) {
        VisionProcessorBlockEntity p = nearestProcessor(level, cam);
        if (p == null) return;
        OutputState o = p.outputs[0];
        if (!o.autoCams.contains(cam.asLong())) o.autoCams.add(cam.asLong());
        if (!o.program.startsWith("cam:")) p.cut(0, OutputState.cam(cam), level.getGameTime());
        else p.changed();
    }
}
