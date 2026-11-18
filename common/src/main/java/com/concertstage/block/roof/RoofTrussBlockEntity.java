package com.concertstage.block.roof;

import com.concertstage.StageContent;
import com.concertstage.block.ring.RingTrussCellBlock;
import com.concertstage.block.ring.RingTrussCellBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Roof support truss (CSC RoofTruss): one roof frame in box truss, the same section as the 1-block trusses of the mod, standing on the grid
 * trusses. It starts on the truss under its foot block and goes {@link #span()} blocks in its direction to the truss on the other side:
 * an arc (barrel roof), a gable (ridge in the middle) or a single slope, {@link #rise()} metres high. Set with the ring / roof controller.
 */
public class RoofTrussBlockEntity extends BlockEntity {
    public static final int ARC = 0, GABLE = 1, SLOPE = 2, BOW = 3;
    public static final int MAX_COVER = 128;
    public static final int MIN_SPAN = 2, MAX_SPAN = 96, MAX_RISE2 = 32;
    /** Same section as the circular truss / 1-block trusses: chord half spacing, chord half size. */
    public static final double H = 6.5 / 16, CHORD = 1.25 / 16, REACH = H + CHORD, Y0 = H + CHORD;
    /** Size factor of the heavy ridge bar and end gables of the CSC TitanRoof. */
    public static final double HEAVY = 1.5;

    private int span = 8, rise2 = 6, shape = ARC, turn = 0, cover = 0, overhang = 0;
    private boolean black = false, full = false, heavy = false;
    /** Presets of the roof kits. */
    public static final int PRESET_NONE = 0, PRESET_ST = 1, PRESET_CT = 2;
    private long[] cells = new long[0];
    /** Per vertex of the frame line, at the two roof edges (offset 0 and offset cover): 1 = a block (truss) under it, the sheet goes down to it. */
    private int[] skirt0 = new int[0], skirt1 = new int[0];
    /** Per block of the cover width, at the two ends of the frame line: 1 = a truss under it, the sheet turns down to it (meets the stage tarp). */
    /** x, y, z (thousandths of a block, relative to the foot) where the sheet turned down at each column of a frame end lands: the top of the
     *  stage tarp near it (slanted, like a stage tarp stretched to a stage rim), else straight down outside the truss; NONE = nothing. */
    private int[] flap0 = new int[0], flap1 = new int[0];
    public static final int NONE = Integer.MIN_VALUE;
    /** Sheet down to the trusses: under which edge frames (0 none, 1 start, 2 opposite edge, 3 both), and down at the frame ends (flaps). */
    private int skirtMode = 0;
    private boolean flaps = false;
    /** Gable on a middle truss: a truss column from the ridge of each frame down to what is under it (a truss across the middle). */
    private boolean kingPost = false;
    /** Per frame (see frameOffsets): y (thousandths, relative to the foot) of the top of what the column stands on, NONE = no column. */
    private int[] kingBottoms = new int[0];
    public boolean hasKingPost() { return kingPost && shape == GABLE; }
    /** The columns under the ridge in the small truss (10 px section, like the small 1 x 2 truss) instead of the frame truss. */
    private boolean kingSmall = false;
    public boolean isKingSmall() { return kingSmall; }
    /** Section of the small truss compared with the frame truss. */
    public static final double SMALL = 10.0 / 16.0;

    /** Per vertex of the frame line (x, y, z thousandths, NONE): where the closed gable under that edge frame comes down to. */
    public int[] skirt(int edge) { return edge == 0 ? ((skirtMode & 1) != 0 ? skirt0 : new int[0]) : ((skirtMode & 2) != 0 ? skirt1 : new int[0]); }
    public int[] flap(int end) { return !flaps ? new int[0] : end == 0 ? flap0 : flap1; }
    public int skirtMode() { return skirtMode; }
    public boolean hasFlaps() { return flaps; }

    public RoofTrussBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.ROOF_TRUSS_ENTITY, pos, state);
    }

    public int span() { return span; }
    public int rise2() { return rise2; }
    public double rise() { return rise2 / 2.0; }
    public int shape() { return shape; }
    public int turn() { return turn; }
    public boolean isBlack() { return black; }
    /** Roof sheet laid on this frame, in blocks towards its side (negative = the other side), 0 = none. */
    public int cover() { return cover; }
    /** Whole roof: frames every ~3 m over the cover width, ridge bar and purlins (CSC SkyRoof / TitanRoof kits). */
    public boolean isFull() { return full; }
    /** Heavier ridge bar and end gables (CSC TitanRoof). */
    public boolean isHeavy() { return heavy; }
    /** Roof sheet past the end frames (eaves / front canopy), blocks. */
    public int overhang() { return overhang; }

    /** Settings flags as sent by the controller (see {@link #apply}). */
    public int flags() {
        return shape | (turn << 2) | (black ? 32 : 0) | ((cover + 128) << 20) | (full ? 1 << 12 : 0) | (heavy ? 1 << 13 : 0) | (overhang << 14) | (skirtMode << 16) | (flaps ? 1 << 18 : 0) | (kingPost ? 1 << 19 : 0) | (kingSmall ? 1 << 6 : 0);
    }

    /** Side offsets (blocks, towards the cover side) of the frames: just this one, or every ~3 m for a whole roof. */
    public static double[] frameOffsets(boolean full, int cover) {
        if (!full || cover == 0) return new double[] {0};
        int n = Math.max(1, (int) Math.round(Math.abs(cover) / 3.0));
        double[] out = new double[n + 1];
        for (int k = 0; k <= n; k++) out[k] = cover * (double) k / n;
        return out;
    }

    /** Horizontal unit vector towards the positive cover side (right of the frame direction). */
    public static double[] side(int turn) {
        Direction d = Direction.from2DDataValue(turn);
        return new double[] {-d.getStepZ(), 0, d.getStepX()};
    }

    /** Highest point of the frame (ridge / top of the arc / high end of a slope), where the ridge bar runs. */
    public static double[] apex(List<double[]> line, int shape) {
        return shape == SLOPE ? line.get(line.size() - 1) : line.get(line.size() / 2);
    }

    /** The centre line carried on past both ends by {@code eave} blocks along its end slopes (roof edge past the stage). */
    public static List<double[]> withEaves(List<double[]> line, double eave) {
        if (eave <= 0 || line.size() < 2) return line;
        List<double[]> out = new ArrayList<>(line);
        double[] a = line.get(0), a1 = line.get(1), b = line.get(line.size() - 1), b1 = line.get(line.size() - 2);
        out.add(0, extend(a, a1, eave));
        out.add(extend(b, b1, eave));
        return out;
    }

    private static double[] extend(double[] end, double[] inner, double eave) {
        double dx = end[0] - inner[0], dy = end[1] - inner[1], dz = end[2] - inner[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horiz = Math.sqrt(dx * dx + dz * dz), k = eave / Math.max(1e-6, horiz);     // eave measured flat
        return new double[] {end[0] + dx * k, end[1] + dy * k, end[2] + dz * k};
    }

    /** Line the frames are built on: with the eaves for a whole roof. */
    public static List<double[]> roofLine(int shape, int span, double rise, int turn, boolean full, int overhang) {
        List<double[]> line = centreLine(shape, span, rise, turn);
        return full ? withEaves(line, overhang) : line;
    }

    public static List<double[]> shifted(List<double[]> line, double[] side, double k) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : line) out.add(new double[] {p[0] + side[0] * k, p[1], p[2] + side[2] * k});
        return out;
    }
    public Direction direction() { return Direction.from2DDataValue(turn); }

    /** Height of the centre line above its ends at {@code along} blocks from the foot. */
    public static double height(int shape, double span, double rise, double along) {
        if (rise <= 0 || span <= 0) return 0;
        double t = Math.max(0, Math.min(1, along / span));
        switch (shape) {
            case GABLE: return rise * (1 - Math.abs(2 * t - 1));
            case SLOPE: return rise * t;
            default: {
                double c = span / 2, r = (c * c + rise * rise) / (2 * rise), x = along - c;
                return Math.sqrt(Math.max(0, r * r - x * x)) - (r - rise);
            }
        }
    }

    /** Vertices of the centre line, relative to the foot cell corner (arc: smooth, gable: through the ridge, slope: straight). */
    public static List<double[]> centreLine(int shape, int span, double rise, int turn) {
        Direction d = Direction.from2DDataValue(turn);
        int n = shape == ARC || shape == BOW ? Math.max(8, span * 2) : shape == GABLE ? 2 * Math.max(1, (span + 1) / 2) : Math.max(1, span);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double along = span * (double) i / n;
            out.add(new double[] {0.5 + d.getStepX() * along, Y0 + height(shape, span, rise, along), 0.5 + d.getStepZ() * along});
        }
        return out;
    }

    /**
     * Arc on posts (BOW, set along a grid truss): every 1.5 m a pair of posts goes from the bottom of the arc down to the top of the truss
     * it stands on. Returns {top, bottom} pairs of the post line (centre of the truss section).
     */
    public static List<double[][]> posts(int shape, int span, double rise, int turn) {
        List<double[][]> out = new ArrayList<>();
        if (shape != BOW) return out;
        List<double[]> line = centreLine(shape, span, rise, turn);
        int n = line.size() - 1, step = Math.max(1, (int) Math.round(n / (double) span * 1.5));
        for (int i = step; i < n; i += step) {
            double[] p = line.get(i);
            if (p[1] - H - CHORD < 0.15) continue;
            out.add(new double[][] {{p[0], p[1] - H, p[2]}, {p[0], 0.0, p[2]}});
        }
        return out;
    }

    /** Truss columns under the ridge (gable on a middle truss): {top, bottom} centre points, one per frame that has something under it. */
    public List<double[][]> kingPosts() {
        List<double[][]> out = new ArrayList<>();
        if (!hasKingPost()) return out;
        List<double[]> line = roofLine(shape, span, rise(), turn, full, overhang);
        double[] a = apex(line, shape), side = side(turn);
        double[] offs = frameOffsets(full, cover);
        for (int f = 0; f < offs.length && f < kingBottoms.length; f++) {
            if (kingBottoms[f] == NONE) continue;
            double x = a[0] + side[0] * offs[f], z = a[2] + side[2] * offs[f];
            out.add(new double[][] {{x, a[1] - H, z}, {x, kingBottoms[f] / 1000.0, z}});
        }
        return out;
    }

    public List<double[]> centreLine() {
        return centreLine(shape, span, rise(), turn);
    }

    /** First placement: the frame goes away from the player; a roof kit takes its preset. */
    public void init(Direction look, int preset) {
        turn = look.get2DDataValue();
        // roof kits: the foot on the front corner, looking along the front truss; the frames go from the front to the back (ridge across
        // the width, one slope towards the audience, one towards the back), the roof carried on 1 m past the stage all round
        if (preset == PRESET_ST) {             // CSC SkyRoof 1612: 16 x 12 m
            turn = look.getClockWise().get2DDataValue();
            shape = GABLE; span = 12; rise2 = 5; cover = -16; full = true; heavy = false; overhang = 1;
        } else if (preset == PRESET_CT) {      // CSC TitanRoof 1812: 18 x 12 m (up to 20 x 14), heavy ridge and gables
            turn = look.getClockWise().get2DDataValue();
            shape = GABLE; span = 12; rise2 = 6; cover = -18; full = true; heavy = true; overhang = 1;
        }
        // kits: the sheet closes the gables and the frame ends down to the stage tarps (or the trusses) from the start
        if (preset == PRESET_ST || preset == PRESET_CT) { skirtMode = 3; flaps = true; }
        setChanged();
    }

    /**
     * From the controller. {@code flags}: bits 0-1 = shape, bits 2-3 = direction (2D value), bit 5 = black, bits 20-28 = cover + 128,
     * bit 12 = whole roof, bit 13 = heavy, bits 14-15 = overhang,
     * bits 16-17 = sheet down under the edge frames (1 start, 2 opposite, 3 both), bit 18 = sheet down at the frame ends,
     * bit 19 = gable on a middle truss (truss columns under the ridge), bit 6 = those columns in the small truss.
     */
    public void apply(int span, int rise2, int flags) {
        this.span = Math.max(MIN_SPAN, Math.min(MAX_SPAN, span));
        this.rise2 = Math.max(0, Math.min(MAX_RISE2, rise2));
        this.shape = flags & 3;                                                      // ARC, GABLE, SLOPE, BOW
        this.turn = (flags >> 2) & 3;
        this.black = (flags & 32) != 0;
        this.cover = Math.max(-MAX_COVER, Math.min(MAX_COVER, ((flags >> 20) & 511) - 128));
        this.full = (flags & (1 << 12)) != 0;
        this.heavy = (flags & (1 << 13)) != 0;
        this.overhang = (flags >> 14) & 3;
        this.skirtMode = (flags >> 16) & 3;
        this.flaps = (flags & (1 << 18)) != 0;
        this.kingPost = (flags & (1 << 19)) != 0 && this.shape == GABLE;
        this.kingSmall = this.kingPost && (flags & (1 << 6)) != 0;
        rebuildCells();
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------ collision cells (same invisible cells as the circular truss)
    public void rebuildCells() {
        if (level == null || level.isClientSide) return;
        Map<Long, float[]> boxes = new LinkedHashMap<>();
        List<double[]> line = roofLine(shape, span, rise(), turn, full, overhang);
        double[] side = side(turn);
        double[] offsets = frameOffsets(full, cover);
        for (int f = 0; f < offsets.length; f++) {
            double reach = heavy && (f == 0 || f == offsets.length - 1) ? REACH * HEAVY : REACH;
            List<double[]> fl = shifted(line, side, offsets[f]);
            for (int s2 = 0; s2 + 1 < fl.size(); s2++) sample(boxes, fl.get(s2), fl.get(s2 + 1), reach);
        }
        if (full && cover != 0) {                                                           // ridge bar
            double[] a = apex(line, shape), b = {a[0] + side[0] * cover, a[1], a[2] + side[2] * cover};
            sample(boxes, a, b, heavy ? REACH * HEAVY : REACH);
        }
        for (double off : offsets) for (double[][] post : posts(shape, span, rise(), turn)) {
            Direction d = direction();
            for (double sd : new double[] {-H, H}) {
                double px = post[0][0] - d.getStepZ() * sd + side[0] * off, pz = post[0][2] + d.getStepX() * sd + side[2] * off;
                for (int cy = (int) Math.floor(post[1][1]); cy <= (int) Math.floor(post[0][1]); cy++) {
                    if (Math.floor(px) == 0 && cy == 0 && Math.floor(pz) == 0) continue;
                    float[] bx = boxes.computeIfAbsent(BlockPos.asLong((int) Math.floor(px), cy, (int) Math.floor(pz)), q -> new float[] {1, 1, 1, 0, 0, 0});
                    double fx = px - Math.floor(px), fz = pz - Math.floor(pz);
                    double y0 = Math.max(0, post[1][1] - cy), y1 = Math.min(1, post[0][1] - cy);
                    bx[0] = (float) Math.max(0, Math.min(bx[0], fx - CHORD)); bx[3] = (float) Math.min(1, Math.max(bx[3], fx + CHORD));
                    bx[1] = (float) Math.min(bx[1], y0); bx[4] = (float) Math.max(bx[4], y1);
                    bx[2] = (float) Math.max(0, Math.min(bx[2], fz - CHORD)); bx[5] = (float) Math.min(1, Math.max(bx[5], fz + CHORD));
                }
            }
        }
        refreshEdges();
        for (double[][] k : kingPosts()) sample(boxes, k[0], k[1], kingSmall ? REACH * SMALL : heavy ? REACH * HEAVY : REACH);   // truss columns on the middle truss
        // the roof sheet (and what turns down to the trusses) stops light beams: thin cells along it, only for the beams
        Map<Long, float[]> sheet = sheetCells(line, side);
        for (Long k : boxes.keySet()) sheet.remove(k);
        Set<Long> wanted = new java.util.HashSet<>(boxes.keySet());
        wanted.addAll(sheet.keySet());
        for (long rel : cells) {
            if (wanted.contains(rel)) continue;
            BlockPos q = worldPosition.offset(BlockPos.of(rel));
            if (level.getBlockState(q).getBlock() instanceof RingTrussCellBlock) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
        }
        List<Long> placed = new ArrayList<>();
        Set<Long> before = com.concertstage.block.ring.CellOwners.set(cells);
        for (var e : boxes.entrySet()) {
            BlockPos q = worldPosition.offset(BlockPos.of(e.getKey()));
            if (!level.hasChunkAt(q)) {                                                // not loaded: a cell already there stays on the list
                if (before.contains(e.getKey())) placed.add(e.getKey());
                continue;
            }
            BlockState s = level.getBlockState(q);
            boolean ours = s.getBlock() instanceof RingTrussCellBlock && level.getBlockEntity(q) instanceof RingTrussCellBlockEntity c
                && (c.anchor().equals(worldPosition) || !com.concertstage.block.ring.CellOwners.owns(level, c.anchor(), q));   // ours, or left over
            if (!(s.isAir() || ours)) continue;                                        // a truss, a tower... : the frame goes through it
            if (s.isAir()) level.setBlock(q, StageContent.RING_TRUSS_CELL.defaultBlockState(), 3);
            if (level.getBlockEntity(q) instanceof RingTrussCellBlockEntity cell) cell.set(e.getValue(), worldPosition, false);
            placed.add(e.getKey());
        }
        for (var e : sheet.entrySet()) {
            BlockPos q = worldPosition.offset(BlockPos.of(e.getKey()));
            if (!level.hasChunkAt(q)) {
                if (before.contains(e.getKey())) placed.add(e.getKey());
                continue;
            }
            BlockState s = level.getBlockState(q);
            boolean ours = s.getBlock() instanceof RingTrussCellBlock && level.getBlockEntity(q) instanceof RingTrussCellBlockEntity c
                && (c.anchor().equals(worldPosition) || !com.concertstage.block.ring.CellOwners.owns(level, c.anchor(), q));
            if (!(s.isAir() || ours)) continue;
            if (s.isAir()) level.setBlock(q, StageContent.RING_TRUSS_CELL.defaultBlockState(), 3);
            if (level.getBlockEntity(q) instanceof RingTrussCellBlockEntity cell) cell.set(e.getValue(), worldPosition, true);
            placed.add(e.getKey());
        }
        cells = placed.stream().mapToLong(Long::longValue).toArray();
        cellSet = null;
        revision++;
        setChanged();
    }

    private Set<Long> cellSet;
    private boolean healed;
    /** Changes with every setting / shape change (the renderer rebuilds its mesh only then). */
    private int revision;

    public int revision() { return revision; }

    /** True when {@code q} is one of its cells. */
    public boolean ownsCell(BlockPos q) {
        if (cellSet == null) cellSet = com.concertstage.block.ring.CellOwners.set(cells);
        return cellSet.contains(q.subtract(worldPosition).asLong());
    }

    /** Cells crossed by the roof sheet, its turned-down edges and its flaps (the geometry drawn by RoofTrussRenderer), with thin boxes. */
    private Map<Long, float[]> sheetCells(List<double[]> line, double[] side) {
        Map<Long, float[]> out = new LinkedHashMap<>();
        double h = heavy ? H * HEAVY : H, ch = CHORD * h / H, lift = h + ch + 0.02, half = h + ch + 0.03;
        double sign = cover == 0 ? 1 : Math.signum(cover), ov = full ? overhang : 0;
        final double r = 0.06, step = 0.25;
        if (cover != 0) {
            List<double[]> top = withEaves(line, half);
            double from = -sign * (ov + half), to = cover + sign * (ov + half), lo = Math.min(from, to), hi = Math.max(from, to);
            for (int s = 0; s + 1 < top.size(); s++) {
                double[] a = top.get(s), b = top.get(s + 1);
                double len = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
                int n = Math.max(1, (int) Math.ceil(len / step));
                for (int k = 0; k <= n; k++) {
                    double f = k / (double) n;
                    double px = a[0] + (b[0] - a[0]) * f, py = a[1] + (b[1] - a[1]) * f + lift, pz = a[2] + (b[2] - a[2]) * f;
                    for (double w = lo; w <= hi + 1e-6; w += step) point(out, px + side[0] * w, py, pz + side[2] * w, r);
                }
            }
        }
        for (int edge = 0; edge < 2; edge++) {                                          // gables, down to the stage tarps
            int[] sk = skirt(edge);
            double off = edge == 0 ? -sign * half : cover + sign * half;
            for (int i = 0; i < line.size() && 3 * i + 2 < sk.length; i++) {
                if (sk[3 * i] == NONE) continue;
                double[] a = line.get(i);
                double ax = a[0] + side[0] * off, ay = a[1] + lift, az = a[2] + side[2] * off;
                double tx = sk[3 * i] / 1000.0, ty = sk[3 * i + 1] / 1000.0, tz = sk[3 * i + 2] / 1000.0;
                double len = Math.sqrt((tx - ax) * (tx - ax) + (ty - ay) * (ty - ay) + (tz - az) * (tz - az));
                int n = Math.max(1, (int) Math.ceil(len / step));
                for (int k = 0; k <= n; k++) {
                    double f = k / (double) n;
                    point(out, ax + (tx - ax) * f, ay + (ty - ay) * f, az + (tz - az) * f, r);
                }
                if (i + 1 < line.size() && 3 * i + 5 < sk.length && sk[3 * i + 3] != NONE) {
                    double[] b = line.get(i + 1);
                    for (double g = 0.25; g < 1; g += 0.25) {
                        double bx = b[0] + side[0] * off, by = b[1] + lift, bz = b[2] + side[2] * off;
                        double sx = ax + (bx - ax) * g, sy = ay + (by - ay) * g, sz = az + (bz - az) * g;
                        double ex = tx + (sk[3 * i + 3] / 1000.0 - tx) * g, ey = ty + (sk[3 * i + 4] / 1000.0 - ty) * g, ez = tz + (sk[3 * i + 5] / 1000.0 - tz) * g;
                        double l2 = Math.sqrt((ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz));
                        int m = Math.max(1, (int) Math.ceil(l2 / step));
                        for (int k = 0; k <= m; k++) point(out, sx + (ex - sx) * k / m, sy + (ey - sy) * k / m, sz + (ez - sz) * k / m, r);
                    }
                }
            }
        }
        if (flaps && cover != 0) {                                                      // flaps to the stage tarps
            List<double[]> top = withEaves(line, half);
            for (int end = 0; end < 2; end++) {
                int[] fl = end == 0 ? flap0 : flap1;
                double[] p = top.get(end == 0 ? 0 : top.size() - 1);
                for (int i = 0; 3 * i + 2 < fl.length; i++) {
                    if (fl[3 * i] == NONE) continue;
                    double ax = p[0] + side[0] * sign * i, ay = p[1] + lift, az = p[2] + side[2] * sign * i;
                    double tx = fl[3 * i] / 1000.0, ty = fl[3 * i + 1] / 1000.0, tz = fl[3 * i + 2] / 1000.0;
                    double len = Math.sqrt((tx - ax) * (tx - ax) + (ty - ay) * (ty - ay) + (tz - az) * (tz - az));
                    int n = Math.max(1, (int) Math.ceil(len / step));
                    for (int k = 0; k <= n; k++) {
                        double f = k / (double) n;
                        for (double w = -0.5; w <= 0.5001; w += step)
                            point(out, ax + (tx - ax) * f + side[0] * sign * w, ay + (ty - ay) * f, az + (tz - az) * f + side[2] * sign * w, r);
                    }
                }
            }
        }
        return out;
    }

    private static void point(Map<Long, float[]> out, double x, double y, double z, double r) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        if (cx == 0 && cy == 0 && cz == 0) return;
        float[] bx = out.computeIfAbsent(BlockPos.asLong(cx, cy, cz), q -> new float[] {1, 1, 1, 0, 0, 0});
        double[] lo = {x - r - cx, y - r - cy, z - r - cz}, hi = {x + r - cx, y + r - cy, z + r - cz};
        for (int i = 0; i < 3; i++) {
            bx[i] = (float) Math.max(0, Math.min(bx[i], lo[i]));
            bx[i + 3] = (float) Math.min(1, Math.max(bx[i + 3], hi[i]));
        }
    }

    /** Roof sheet down to the trusses under its edges / to the stage tarps at its two ends; true if something changed. */
    private boolean refreshEdges() {
        List<double[]> line = roofLine(shape, span, rise(), turn, full, overhang);
        double[] side = side(turn);
        int[] s0 = skirtMode != 0 ? skirtOf(line, side, 0, -1) : new int[0];
        int[] s1 = skirtMode != 0 && cover != 0 ? skirtOf(line, side, cover, 1) : new int[0];
        int[] f0 = flaps && cover != 0 ? flapOf(line.get(0), side, -1) : new int[0];
        int[] f1 = flaps && cover != 0 ? flapOf(line.get(line.size() - 1), side, 1) : new int[0];
        int[] kb = hasKingPost() ? kingBottomsOf(line, side) : new int[0];
        boolean changed = !java.util.Arrays.equals(kb, kingBottoms) || !java.util.Arrays.equals(s0, skirt0) || !java.util.Arrays.equals(s1, skirt1)
            || !java.util.Arrays.equals(f0, flap0) || !java.util.Arrays.equals(f1, flap1);
        skirt0 = s0; skirt1 = s1; flap0 = f0; flap1 = f1; kingBottoms = kb;
        return changed;
    }

    /** Every 5 s: follows the stage tarps and trusses placed, moved or removed around the roof (no need to set it again). */
    public void serverTick() {
        if (level == null || level.isClientSide) return;
        if (!healed) {                                       // once after loading: takes back the cells it had lost track of (1.0.2)
            healed = true;
            rebuildCells();
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
        }
        if (skirtMode == 0 && !flaps && !hasKingPost()) return;
        if ((level.getGameTime() + worldPosition.asLong()) % 100 != 0) return;
        if (refreshEdges()) {
            rebuildCells();                                                             // the beam cells follow the sheet
            setChanged();
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
        }
    }

    /** Half width of the frame plus a little: the sheets turned down go just outside the truss. */
    public static final double OUTSIDE = H + CHORD + 0.03;

    private int[] flapOf(double[] end, double[] side, int outward) {
        int n = Math.abs(cover);
        int[] out = new int[3 * (n + 1)];
        java.util.Arrays.fill(out, NONE);
        double sign = Math.signum(cover);
        Direction d = direction();
        double ox = d.getStepX() * outward, oz = d.getStepZ() * outward;
        for (int i = 0; i <= n; i++) {
            double qx = end[0] + side[0] * sign * i, qz = end[2] + side[2] * sign * i;
            double[] t = tarpNear(qx, qz, ox, oz);
            if (t == null) {                                                                // no stage tarp: straight down outside the truss under it
                BlockPos q = worldPosition.offset((int) Math.floor(qx), -1, (int) Math.floor(qz));
                if (level.hasChunkAt(q)) {
                    BlockState s = level.getBlockState(q);
                    if (!s.isAir() && !(s.getBlock() instanceof RingTrussCellBlock) && !s.getCollisionShape(level, q).isEmpty())
                        t = new double[] {qx + ox * OUTSIDE, -0.3, qz + oz * OUTSIDE};
                }
            }
            if (t != null) for (int c = 0; c < 3; c++) out[3 * i + c] = (int) Math.round(t[c] * 1000);
        }
        return out;
    }


    /** Top of the stage tarp nearest {@code (qx, qz)} (relative to the foot), looking up to 8 blocks out along (ox, oz) and from 2 blocks
     *  over to 3 blocks under the foot: where a sheet turned down meets it (in the plane of the tarp), or null. */
    private double[] tarpNear(double qx, double qz, double ox, double oz) {
        int bestY = Integer.MIN_VALUE;
        for (int k = 0; k <= 8; k++) {
            // only a tarp hung from the trusses around the roof (its top at most 3 blocks under the foot): the skirt of the deck or a
            // tarp lower down (in front of the stage) is not joined, the sheet would hide the stage
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos q = worldPosition.offset((int) Math.floor(qx + ox * k), dy, (int) Math.floor(qz + oz * k));
                if (!level.hasChunkAt(q)) break;
                BlockState s = level.getBlockState(q);
                boolean rail = s.getBlock() instanceof com.concertstage.block.tarp.StageTarpRailBlock;
                boolean top = s.getBlock() instanceof com.concertstage.block.tarp.StageTarpTopBlock;
                if (!rail && !top) continue;
                if (dy <= bestY) break;
                bestY = dy;
                Direction.Axis axis = rail ? s.getValue(com.concertstage.block.tarp.StageTarpRailBlock.FACING).getAxis()
                    : s.getValue(com.concertstage.block.tarp.StageTarpTopBlock.AXIS);
                double plane = com.concertstage.block.tarp.StageTarpRailBlockEntity.planeOffset(s.getValue(com.concertstage.block.StageProperties.SHIFT));
                int topV = s.getValue(com.concertstage.block.tarp.TarpProperties.TOP);
                double y = dy + (topV <= 0 || topV > 8 ? 1.0 : Math.min(1.0, topV * 2 / 16.0));
                double cx = Math.floor(qx + ox * k) + 0.5, cz = Math.floor(qz + oz * k) + 0.5;
                return axis == Direction.Axis.X ? new double[] {qx + ox * k, y, cz + plane} : new double[] {cx + plane, y, qz + oz * k};
            }
        }
        return null;
    }

    /** For each frame: the top of the first solid block (a truss across the middle...) under its ridge, up to 24 blocks down; NONE. */
    private int[] kingBottomsOf(List<double[]> line, double[] side) {
        double[] a = apex(line, shape);
        double[] offs = frameOffsets(full, cover);
        int[] out = new int[offs.length];
        java.util.Arrays.fill(out, NONE);
        for (int f = 0; f < offs.length; f++) {
            double x = a[0] + side[0] * offs[f], z = a[2] + side[2] * offs[f], from = a[1] - REACH - 0.05;
            for (int cy = (int) Math.floor(from); cy >= -24; cy--) {
                BlockPos q = worldPosition.offset((int) Math.floor(x), cy, (int) Math.floor(z));
                if (!level.hasChunkAt(q)) break;
                BlockState s = level.getBlockState(q);
                if (s.isAir() || s.getBlock() instanceof RingTrussCellBlock) continue;
                net.minecraft.world.phys.shapes.VoxelShape sh = s.getCollisionShape(level, q);
                if (sh.isEmpty()) continue;
                double top = cy + Math.min(1, sh.max(Direction.Axis.Y));
                if (top > from - 0.3) break;                                                     // no room for a column
                out[f] = (int) Math.round(top * 1000);
                break;
            }
        }
        return out;
    }

    /** Half width of the edge frames plus a little: the sheet turned down under them goes just outside the truss. */
    private double edgeHalf() {
        double h = heavy ? H * HEAVY : H;
        return h + CHORD * h / H + 0.03;
    }
    /**
     * Gable closed under an edge frame (offset {@code off}: 0 or cover): per vertex of the frame line, where the sheet hanging outside the
     * frame comes down to: the top of the stage tarp near it (in its plane, slanted like the flaps of the frame ends), else straight down to
     * just under the top of the truss under it; NONE = nothing under it.
     */
    private int[] skirtOf(List<double[]> line, double[] side, double off, int outward) {
        int[] out = new int[3 * line.size()];
        java.util.Arrays.fill(out, NONE);
        double sign = cover == 0 ? 1 : Math.signum(cover), half = edgeHalf();
        double ox = side[0] * sign * outward, oz = side[2] * sign * outward;
        for (int i = 0; i < line.size(); i++) {
            double[] p = line.get(i);
            double qx = p[0] + side[0] * off, qz = p[2] + side[2] * off;
            double[] t = tarpNear(qx, qz, ox, oz);
            if (t == null) {
                BlockPos q = worldPosition.offset((int) Math.floor(qx), -1, (int) Math.floor(qz));
                if (!level.hasChunkAt(q)) continue;
                BlockState s = level.getBlockState(q);
                if (p[1] - H - CHORD < 0.05 || s.isAir() || s.getBlock() instanceof RingTrussCellBlock || s.getCollisionShape(level, q).isEmpty()) continue;
                t = new double[] {qx + ox * half, -0.3, qz + oz * half};
            }
            for (int c = 0; c < 3; c++) out[3 * i + c] = (int) Math.round(t[c] * 1000);
        }
        return out;
    }

    /** Collision of a straight piece of truss (centre line a-b, half size {@code reach}) cell by cell. */
    private static void sample(Map<Long, float[]> boxes, double[] a, double[] b, double reach) {
        double len = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
        int steps = Math.max(1, (int) Math.ceil(len * 10));
        for (int k = 0; k <= steps; k++) {
            double f = k / (double) steps;
            double[] p = {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
            for (int cx = (int) Math.floor(p[0] - reach); cx <= (int) Math.floor(p[0] + reach); cx++)
                for (int cy = (int) Math.floor(p[1] - reach); cy <= (int) Math.floor(p[1] + reach); cy++)
                    for (int cz = (int) Math.floor(p[2] - reach); cz <= (int) Math.floor(p[2] + reach); cz++) {
                        if (cx == 0 && cy == 0 && cz == 0) continue;                     // the foot block itself
                        float[] bx = boxes.computeIfAbsent(BlockPos.asLong(cx, cy, cz), q -> new float[] {1, 1, 1, 0, 0, 0});
                        double[] lo = {p[0] - reach - cx, p[1] - reach - cy, p[2] - reach - cz};
                        double[] hi = {p[0] + reach - cx, p[1] + reach - cy, p[2] + reach - cz};
                        for (int i = 0; i < 3; i++) {
                            bx[i] = (float) Math.max(0, Math.min(bx[i], lo[i]));
                            bx[i + 3] = (float) Math.min(1, Math.max(bx[i + 3], hi[i]));
                        }
                    }
        }
    }

    /** World positions of its invisible collision / beam cells (a lift takes them away and the BE rebuilds them where it arrives). */
    public java.util.List<BlockPos> cellPositions() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (long rel : cells) out.add(worldPosition.offset(BlockPos.of(rel)));
        return out;
    }

    /**
     * Removes every collision / beam cell: the listed ones, and any other one anchored here over the whole roof (cells a roof lost track
     * of used to stay behind, invisible, and could darken the place where the roof was).
     */
    public void clearCells() {
        if (level == null || level.isClientSide) return;
        com.concertstage.block.ring.CellOwners.sweep(level, worldPosition, cells, getRenderBoundingBox().inflate(2));
        cells = new long[0];
        cellSet = null;
    }

    public AABB getRenderBoundingBox() {
        Direction d = direction();
        // down to the sheets turned down to the stage tarps (about 3 blocks) and to the bottom of the columns under the ridge
        double down = 3.5;
        for (int k : kingBottoms) if (k != NONE) down = Math.max(down, -k / 1000.0 + 0.5);
        return new AABB(worldPosition).expandTowards(d.getStepX() * span, rise() + 1, d.getStepZ() * span)
            .expandTowards(-d.getStepZ() * cover, 0, d.getStepX() * cover).expandTowards(0, -down, 0).inflate(1.5 + overhang);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Span", span);
        tag.putInt("Rise2", rise2);
        tag.putInt("Shape", shape);
        tag.putInt("Turn", turn);
        tag.putBoolean("Black", black);
        tag.putInt("Cover", cover);
        tag.putBoolean("Full", full);
        tag.putBoolean("Heavy", heavy);
        tag.putInt("Overhang", overhang);
        tag.putLongArray("Cells", cells);
        tag.putIntArray("SkirtT0", skirt0);
        tag.putIntArray("SkirtT1", skirt1);
        tag.putIntArray("FlapT0", flap0);
        tag.putIntArray("FlapT1", flap1);
        tag.putInt("SkirtMode", skirtMode);
        tag.putBoolean("Flaps", flaps);
        tag.putBoolean("KingPost", kingPost);
        tag.putIntArray("KingBottoms", kingBottoms);
        tag.putBoolean("KingSmall", kingSmall);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Span")) span = Math.max(MIN_SPAN, Math.min(MAX_SPAN, tag.getInt("Span")));
        if (tag.contains("Rise2")) rise2 = Math.max(0, Math.min(MAX_RISE2, tag.getInt("Rise2")));
        shape = Math.max(0, Math.min(BOW, tag.getInt("Shape")));
        cover = Math.max(-MAX_COVER, Math.min(MAX_COVER, tag.getInt("Cover")));
        full = tag.getBoolean("Full");
        heavy = tag.getBoolean("Heavy");
        overhang = tag.getInt("Overhang") & 3;
        turn = tag.getInt("Turn") & 3;
        black = tag.getBoolean("Black");
        cells = tag.getLongArray("Cells");
        cellSet = null;
        revision++;
        skirt0 = tag.getIntArray("SkirtT0");                // older saves (Skirt0 bytes): recomputed at the next check
        skirt1 = tag.getIntArray("SkirtT1");
        flap0 = tag.getIntArray("FlapT0");
        flap1 = tag.getIntArray("FlapT1");
        skirtMode = tag.getInt("SkirtMode") & 3;
        flaps = tag.getBoolean("Flaps");
        kingPost = tag.getBoolean("KingPost");
        kingBottoms = tag.getIntArray("KingBottoms");
        kingSmall = tag.getBoolean("KingSmall");
    }
}
