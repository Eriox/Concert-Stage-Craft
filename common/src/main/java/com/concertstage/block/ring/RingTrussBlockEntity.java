package com.concertstage.block.ring;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Settings of a circular truss: radius (half metres), tilt (degrees, animated), rotation (degrees), finish.
 *
 * <p>CSC 1.3.0 (Dynamic Truss): the same block entity carries the curved trusses (CSC CURVED TRITRUSS / QUADTRUSS blocks), so they
 * get everything a ring already has (invisible collision cells, ownership, carried by the motorised towers and hoists, fixtures
 * attached to the cells): a section (square H1 or triangular X30D), an arc angle ({@code sweep}, 360 = the circle) and, for a curved
 * piece, the direction it bends to. Geometry: {@link CurvedTrussGeometry}, the construction of the supplied Blender models. A world
 * saved before 1.3.0 loads its rings as square-section circles (the tags are absent): nothing to migrate.
 */
public class RingTrussBlockEntity extends BlockEntity {
    public static final int MIN_RADIUS2 = 2, MAX_RADIUS2 = 20;     // 1 m to 10 m (diameter 2 to 20 blocks, 1.3.0; was 16)
    /** Curved pieces: radius 1 to 20 m in half metres (at the axis of the section). */
    public static final int MIN_CURVE_RADIUS2 = 2, MAX_CURVE_RADIUS2 = 40;
    public static final int BEND_LEFT = 0, BEND_RIGHT = 1, BEND_UP = 2, BEND_DOWN = 3;
    /** Tilt speed while it is raised or lowered, degrees per tick (30 degrees per second). */
    public static final double TILT_SPEED = 1.5;

    private int radius2 = 4;
    private int tilt = 0;
    private int tiltFrom = 0;
    private long tiltStart = 0;
    private int yaw = 0;
    private boolean black = false;
    /** 1.3.0: 0 = square section (QuadTruss H1), 1 = triangular (TriTruss X30D). */
    private int section = 0;
    /** 1.3.0: arc angle in degrees (360 for a circular truss), and the side a curved piece bends to. */
    private int sweep = 360;
    private int bend = BEND_LEFT;

    public RingTrussBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.RING_TRUSS_ENTITY, pos, state);
    }

    public double radius() { return radius2 / 2.0; }
    public int radius2() { return radius2; }
    public int targetTilt() { return tilt; }
    public int yaw() { return yaw; }
    public boolean isBlack() { return black; }
    public int section() { return section; }
    public CurvedTrussGeometry.Section sectionShape() { return section == 1 ? CurvedTrussGeometry.Section.TRI : CurvedTrussGeometry.Section.QUAD; }
    public int sweep() { return sweep; }
    public int bend() { return bend; }
    /** A curved piece (CSC CURVED TRITRUSS / QUADTRUSS) rather than a circular truss. */
    public boolean isCurved() { return getBlockState().getBlock() instanceof CurvedTrussBlock; }

    /** Facing of a curved piece: the direction the truss leaves its first block in. */
    public net.minecraft.core.Direction facing() {
        BlockState st = getBlockState();
        return st.hasProperty(CurvedTrussBlock.FACING) ? st.getValue(CurvedTrussBlock.FACING) : net.minecraft.core.Direction.NORTH;
    }

    /**
     * Arc frame of a curved piece in block-local coordinates (relative to the block corner): end A at the centre of the back face of the
     * block, tangent along the facing, the centre of curvature at R towards the bend. With a radius of n + 0.5 blocks a 90° piece ends on
     * the centre of a block face (it then continues with a straight truss or another curved piece); 180° lands on one for any radius.
     */
    public CurvedTrussGeometry.Frame curveFrame() {
        net.minecraft.core.Direction f = facing();
        double[] F = {f.getStepX(), 0, f.getStepZ()}, up = {0, 1, 0};
        double[] left = {up[1] * F[2] - up[2] * F[1], up[2] * F[0] - up[0] * F[2], up[0] * F[1] - up[1] * F[0]};
        double[] B = switch (bend) {
            case BEND_RIGHT -> new double[] {-left[0], -left[1], -left[2]};
            case BEND_UP -> up;
            case BEND_DOWN -> new double[] {0, -1, 0};
            default -> left;
        };
        double R = radius();
        double[] A = {0.5 - F[0] * 0.5, 0.5, 0.5 - F[2] * 0.5};
        double[] c = {A[0] + B[0] * R, A[1] + B[1] * R, A[2] + B[2] * R};
        double[] e1 = {-B[0], -B[1], -B[2]};
        double[] n = (bend == BEND_UP || bend == BEND_DOWN) ? left : up;
        return CurvedTrussGeometry.Frame.of(c, e1, F, n);
    }

    /** Tilt drawn at this moment (moves towards the set tilt at {@link #TILT_SPEED}). */
    public double tiltAt(long gameTime, float partial) {
        double t = (gameTime - tiltStart) + partial;
        double span = Math.abs(tilt - tiltFrom);
        if (span < 1e-6 || t * TILT_SPEED >= span) return tilt;
        return tiltFrom + Math.signum(tilt - tiltFrom) * t * TILT_SPEED;
    }

    /** From the ring controller. {@code flags}: bits 0-4 = rotation / 15 degrees, bit 5 = black. */
    public void apply(int radius2, int tilt, int flags) {
        long now = level == null ? 0 : level.getGameTime();
        int newTilt = Math.max(0, Math.min(90, tilt));
        if (newTilt != this.tilt) {
            this.tiltFrom = (int) Math.round(tiltAt(now, 0));
            this.tiltStart = now;
            this.tilt = newTilt;
        }
        boolean curved = isCurved();
        this.radius2 = curved ? Math.max(MIN_CURVE_RADIUS2, Math.min(MAX_CURVE_RADIUS2, radius2)) : Math.max(MIN_RADIUS2, Math.min(MAX_RADIUS2, radius2));
        this.yaw = curved ? 0 : ((flags & 31) % 24) * 15;
        this.black = (flags & 32) != 0;
        // 1.3.0: bit 6 = triangular section, bits 7-8 = bend, bits 9-17 = arc angle (curved pieces only; 0 = unchanged)
        this.section = (flags >> 6) & 1;
        if (curved) {
            this.bend = (flags >> 7) & 3;
            int sw = (flags >> 9) & 511;
            if (sw > 0) this.sweep = Math.max(5, Math.min(355, sw));
            this.tilt = 0;
        }
        rebuildCells();
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------ collision cells
    /** Same constants as RingTrussRenderer: chord half spacing, chord half size (square section; 1.3.0: see {@link #sectionHalf()}). */
    private static final double H = 6.5 / 16, CHORD = 1.25 / 16, REACH = H + CHORD;

    /** 1.3.0: half height of the section between the chord axes, and the chord radius, for the section in use. */
    public double sectionHalf() { return section == 1 ? 4.018 / 16 : H; }
    public double chordRadius() { return section == 1 ? 0.95 / 16 : CHORD; }
    /** Cells of the ring placed by this rigging point (relative offsets), to remove them when it changes. */
    private long[] cells = new long[0];

    /** Centre of the ring (relative to the rigging cell corner) for a tilt, the way the renderer draws it. */
    private double[] centre(double tiltDeg) {
        double rad = Math.toRadians(tiltDeg);
        double top = radius() * Math.sin(rad) + sectionHalf() * Math.cos(rad);
        return new double[] {0.5, 5.0 / 16 - top - chordRadius(), 0.5};
    }

    /** Point of the ring centre line at angle {@code a}, relative to the rigging cell corner. */
    private double[] point(double a, double tiltDeg) {
        if (isCurved()) return CurvedTrussGeometry.centre(curveFrame(), radius(), a);
        double r = radius();
        double x = Math.cos(a) * r, y = 0, z = Math.sin(a) * r;
        double t = Math.toRadians(tiltDeg);
        double y1 = y * Math.cos(t) - z * Math.sin(t), z1 = y * Math.sin(t) + z * Math.cos(t);
        double f = Math.toRadians(-yaw);
        double x2 = x * Math.cos(f) + z1 * Math.sin(f), z2 = -x * Math.sin(f) + z1 * Math.cos(f);
        double[] c = centre(tiltDeg);
        return new double[] {c[0] + x2, c[1] + y1, c[2] + z2};
    }

    /** Places the invisible collision cells along the ring (target tilt) and removes those of the previous shape. */
    public void rebuildCells() {
        if (level == null || level.isClientSide) return;
        java.util.Map<Long, float[]> boxes = new java.util.LinkedHashMap<>();
        double span = isCurved() ? Math.toRadians(sweep) : 2 * Math.PI;
        double REACH = sectionShape().reach();
        int n = (int) Math.ceil(span * radius() * 10);
        for (int i = 0; i < n; i++) {
            double[] p = point(isCurved() ? span * i / Math.max(1, n - 1) : 2 * Math.PI * i / n, tilt);
            for (int cx = (int) Math.floor(p[0] - REACH); cx <= (int) Math.floor(p[0] + REACH); cx++)
                for (int cy = (int) Math.floor(p[1] - REACH); cy <= (int) Math.floor(p[1] + REACH); cy++)
                    for (int cz = (int) Math.floor(p[2] - REACH); cz <= (int) Math.floor(p[2] + REACH); cz++) {
                        if (cx == 0 && cy == 0 && cz == 0) continue;                 // the rigging point itself
                        long key = BlockPos.asLong(cx, cy, cz);
                        float[] b = boxes.computeIfAbsent(key, k -> new float[] {1, 1, 1, 0, 0, 0});
                        double[] lo = {p[0] - REACH - cx, p[1] - REACH - cy, p[2] - REACH - cz};
                        double[] hi = {p[0] + REACH - cx, p[1] + REACH - cy, p[2] + REACH - cz};
                        for (int a = 0; a < 3; a++) {
                            b[a] = (float) Math.max(0, Math.min(b[a], lo[a]));
                            b[a + 3] = (float) Math.min(1, Math.max(b[a + 3], hi[a]));
                        }
                    }
        }
        java.util.Set<Long> wanted = boxes.keySet();
        for (long rel : cells) {                                                  // remove the cells that are no longer on the ring
            if (wanted.contains(rel)) continue;
            BlockPos q = worldPosition.offset(BlockPos.of(rel));
            if (level.getBlockState(q).getBlock() instanceof RingTrussCellBlock) level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        java.util.List<Long> placed = new java.util.ArrayList<>();
        java.util.Set<Long> before = CellOwners.set(cells);
        for (var e : boxes.entrySet()) {
            BlockPos q = worldPosition.offset(BlockPos.of(e.getKey()));
            if (!level.hasChunkAt(q)) {                                           // not loaded: a cell already there stays on the list
                if (before.contains(e.getKey())) placed.add(e.getKey());
                continue;
            }
            BlockState s = level.getBlockState(q);
            boolean other = s.getBlock() instanceof RingTrussCellBlock && level.getBlockEntity(q) instanceof RingTrussCellBlockEntity c
                && !worldPosition.equals(c.anchor()) && CellOwners.owns(level, c.anchor(), q);
            if (!(s.isAir() || s.getBlock() instanceof RingTrussCellBlock) || other) continue;   // something is there: the ring goes through it
            if (s.isAir()) level.setBlock(q, StageContent.RING_TRUSS_CELL.defaultBlockState(), 3);
            if (level.getBlockEntity(q) instanceof RingTrussCellBlockEntity cell) cell.set(e.getValue(), worldPosition);
            placed.add(e.getKey());
        }
        cells = placed.stream().mapToLong(Long::longValue).toArray();
        cellSet = null;
        setChanged();
    }

    private java.util.Set<Long> cellSet;

    /** True when {@code q} is one of its cells. */
    public boolean ownsCell(BlockPos q) {
        if (cellSet == null) cellSet = CellOwners.set(cells);
        return cellSet.contains(q.subtract(worldPosition).asLong());
    }

    /** Removes every collision cell (the rigging point was broken). */
    /** World positions of its invisible collision / beam cells (a lift takes them away and the BE rebuilds them where it arrives). */
    public java.util.List<BlockPos> cellPositions() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (long rel : cells) out.add(worldPosition.offset(BlockPos.of(rel)));
        return out;
    }

    /** Removes every collision cell: the listed ones and any other one anchored here around the ring (1.0.2). */
    public void clearCells() {
        if (level == null || level.isClientSide) return;
        CellOwners.sweep(level, worldPosition, cells, getRenderBoundingBox().inflate(1));
        cells = new long[0];
        cellSet = null;
    }

    public AABB getRenderBoundingBox() {
        if (isCurved()) return new AABB(worldPosition).inflate(2 * radius() + 1.5);
        return new AABB(worldPosition).inflate(radius() + 1.5).expandTowards(0, -(radius() + 1.5), 0);
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
        tag.putInt("Radius2", radius2);
        tag.putInt("Tilt", tilt);
        tag.putInt("TiltFrom", tiltFrom);
        tag.putLong("TiltStart", tiltStart);
        tag.putInt("Yaw", yaw);
        tag.putBoolean("Black", black);
        tag.putLongArray("Cells", cells);
        tag.putInt("Section", section);
        tag.putInt("Sweep", sweep);
        tag.putInt("Bend", bend);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Radius2")) radius2 = Math.max(MIN_RADIUS2, Math.min(MAX_CURVE_RADIUS2, tag.getInt("Radius2")));
        section = tag.contains("Section") ? tag.getInt("Section") & 1 : 0;          // 1.3.0; rings of older worlds: square, full circle
        sweep = tag.contains("Sweep") ? Math.max(5, Math.min(360, tag.getInt("Sweep"))) : 360;
        bend = tag.contains("Bend") ? tag.getInt("Bend") & 3 : BEND_LEFT;
        tilt = tag.getInt("Tilt");
        tiltFrom = tag.contains("TiltFrom") ? tag.getInt("TiltFrom") : tilt;
        tiltStart = tag.getLong("TiltStart");
        yaw = tag.getInt("Yaw");
        black = tag.getBoolean("Black");
        cells = tag.getLongArray("Cells");
        cellSet = null;
    }
}
