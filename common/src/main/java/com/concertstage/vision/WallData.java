package com.concertstage.vision;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * CSC 1.3.0 - the LED walls of a dimension, saved in the world ({@code data/csc_livevision_walls.dat}): geometry (plane, rectangle,
 * panels present), name, brightness, and the processor output that feeds it. The server sends this registry to the players of the
 * dimension, so a wall keeps being drawn far beyond the loaded chunks (the panels themselves are only in the chunks).
 */
public class WallData extends SavedData {
    public static final String NAME = "csc_livevision_walls";
    public static final int MAX_SIDE = 96;

    public static final class Wall {
        public long id;
        public String name = "";
        public Direction facing = Direction.NORTH;
        /** Coordinate of the panels along the facing axis (x for east / west, z for north / south). */
        public int plane;
        /** Rectangle in the wall plane: u along the horizontal axis of the plane (x or z), v = y. Inclusive. */
        public int u0, v0, u1, v1;
        /** Panels present in the rectangle, row by row (v from v0, u from u0). */
        public java.util.BitSet panels = new java.util.BitSet();
        public int count;
        public int brightness = 100;
        /** Processor feeding the wall (BlockPos.asLong, 0 = none) and its output (0-3). */
        public long processor;
        public int output;
        /**
         * Rev. 1.3.0-3: angle of the wall about the vertical axis (degrees, -90..90; asked 13 November: screens at an angle), around
         * its centre ({@link #pivot} 0) or one of its side edges seen from the front (1 left, 2 right). The panels and the picture turn
         * together; the blocks (collisions) stay on the grid.
         */
        public float angle;
        public int pivot;
        /** Rev. 1.3.0-3: height of the camera image this wall asks for (0 = LiveVision settings) and its images per second (0 = idem). */
        public int quality, fps;

        public int width() { return u1 - u0 + 1; }
        public int height() { return v1 - v0 + 1; }
        public boolean has(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1 && panels.get((v - v0) * width() + (u - u0)); }

        /** True when the picture's left (seen from the front) is at the high u (facing north / east). */
        public boolean reversed() { return facing == Direction.NORTH || facing == Direction.EAST; }

        /** World x, z of the rotation axis (on the plane of the blocks' centres). */
        public double[] pivotXZ() {
            double u = pivot == 0 ? (u0 + u1 + 1) / 2.0 : (pivot == 1) != reversed() ? u0 : u1 + 1;
            return facing.getAxis() == Direction.Axis.Z ? new double[] {u, plane + 0.5} : new double[] {plane + 0.5, u};
        }

        /** A world point (x, z) turned with the wall: {x, z}. Same sense as joml rotateY / Axis.YP. */
        public double[] turn(double x, double z) {
            if (angle == 0f) return new double[] {x, z};
            double[] p = pivotXZ();
            double a = Math.toRadians(angle), c = Math.cos(a), s = Math.sin(a), dx = x - p[0], dz = z - p[1];
            return new double[] {p[0] + dx * c + dz * s, p[1] - dx * s + dz * c};
        }

        /** World position of the panel at (u, v) of the plane. */
        public BlockPos pos(int u, int v) {
            return facing.getAxis() == Direction.Axis.Z ? new BlockPos(u, v, plane) : new BlockPos(plane, v, u);
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("Id", id); t.putString("Name", name); t.putInt("Facing", facing.get3DDataValue()); t.putInt("Plane", plane);
            t.putInt("U0", u0); t.putInt("V0", v0); t.putInt("U1", u1); t.putInt("V1", v1);
            t.putLongArray("Panels", panels.toLongArray()); t.putInt("Count", count);
            t.putInt("Brightness", brightness); t.putLong("Processor", processor); t.putInt("Output", output);
            t.putFloat("Angle", angle); t.putInt("Pivot", pivot); t.putInt("Quality", quality); t.putInt("Fps", fps);
            return t;
        }

        public static Wall load(CompoundTag t) {
            Wall w = new Wall();
            w.id = t.getLong("Id"); w.name = t.getString("Name"); w.facing = Direction.from3DDataValue(t.getInt("Facing")); w.plane = t.getInt("Plane");
            w.u0 = t.getInt("U0"); w.v0 = t.getInt("V0"); w.u1 = t.getInt("U1"); w.v1 = t.getInt("V1");
            w.panels = java.util.BitSet.valueOf(t.getLongArray("Panels")); w.count = t.getInt("Count");
            w.brightness = t.contains("Brightness") ? t.getInt("Brightness") : 100; w.processor = t.getLong("Processor"); w.output = t.getInt("Output");
            w.angle = clampAngle(t.getFloat("Angle")); w.pivot = Math.max(0, Math.min(2, t.getInt("Pivot")));
            w.quality = clampQuality(t.getInt("Quality")); w.fps = Math.max(0, Math.min(60, t.getInt("Fps")));
            return w;
        }
    }

    public static float clampAngle(float a) { return Float.isFinite(a) ? Math.max(-90f, Math.min(90f, a)) : 0f; }

    public static int clampQuality(int q) { return q <= 0 ? 0 : Math.max(144, Math.min(1080, q)); }

    public final Map<Long, Wall> walls = new LinkedHashMap<>();
    /** Last state of each processor's outputs (BlockPos.asLong): the walls keep showing it when the processor's chunk is not loaded. */
    public final Map<Long, OutputState[]> processors = new LinkedHashMap<>();

    public static WallData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WallData::load, WallData::new, NAME);
    }

    public static WallData load(CompoundTag tag) {
        WallData d = new WallData();
        for (Tag t : tag.getList("Walls", Tag.TAG_COMPOUND)) {
            Wall w = Wall.load((CompoundTag) t);
            d.walls.put(w.id, w);
        }
        for (Tag t : tag.getList("Processors", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ListTag ol = c.getList("Outputs", Tag.TAG_COMPOUND);
            OutputState[] o = new OutputState[VisionProcessorBlockEntity.OUTPUTS];
            for (int i = 0; i < o.length; i++) o[i] = i < ol.size() ? OutputState.load(ol.getCompound(i)) : new OutputState();
            d.processors.put(c.getLong("Pos"), o);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Wall w : walls.values()) l.add(w.save());
        tag.put("Walls", l);
        ListTag pl = new ListTag();
        for (var e : processors.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getKey());
            ListTag ol = new ListTag();
            for (OutputState o : e.getValue()) ol.add(o.save());
            c.put("Outputs", ol);
            pl.add(c);
        }
        tag.put("Processors", pl);
        return tag;
    }

    /** Walls fed by this processor output. */
    public java.util.List<Wall> fedBy(long processor, int output) {
        java.util.List<Wall> out = new java.util.ArrayList<>();
        for (Wall w : walls.values()) if (w.processor == processor && w.output == output) out.add(w);
        return out;
    }
}
