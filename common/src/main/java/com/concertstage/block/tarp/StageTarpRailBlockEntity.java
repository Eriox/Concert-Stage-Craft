package com.concertstage.block.tarp;

import com.concertstage.StageContent;
import com.concertstage.block.ResizableCloth;
import com.concertstage.block.StageProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Size and colour of a stage tarp; places its pieces. Column k (0 = the rail) runs along FACING, row j (0 = top row) goes down.
 * Like the curtain in automatic mode, each column hangs down to the first block that is not air or tarp (at most {@code height}
 * rows) and its hem is tied to it; the hem then reaches down to the real top of that block (a deck surface at 0.2 of its cell...).
 */
public class StageTarpRailBlockEntity extends BlockEntity implements ResizableCloth {
    public static final int MAX = 50;

    private int length = 4;
    private int height = 6;
    private TarpColor color = TarpColor.BLACK;
    /** Gathered against its first column (empty hand or redstone), like the curtain. */
    private boolean open = false;
    /** Every column was tied down (or stretched to a rim) at the last fit: it stays tied when the towers lift it (any height). */
    private boolean anchored = false;
    /**
     * Lifted by the towers: the cloth under the top row was taken away and every column is drawn as a sheet from the moving top row
     * down to its tie point, which stays where it is in the world (absolute Y and position across, 1/1024 block). Cleared by the next fit.
     */
    private boolean liftSheet = false;
    private int[] edgeAbs = new int[0];
    private int[] edgeAcross = new int[0];
    private boolean powered = false;
    /** Rectangle filled by the last fit (cleared on the next one, so a shrinking tarp leaves nothing behind). */
    private int lastLength = 0;
    private int lastRows = 0;
    /**
     * Columns stretched at a slant down to a stage rim (drawn by the client, see TarpSheetRenderer): drop below the bottom of the
     * rail row and position of the rim across the tarp (blocks from the centre of the cell, towards the positive side), in 1/1024.
     * A drop of 0 = the column hangs straight (made of blocks).
     */
    private int[] slantDrop = new int[MAX];
    /** Lean to the stage rim (Tarp Tool, on by default); off = every column hangs straight down to what is under it. */
    private boolean slant = true;
    private int[] slantAcross = new int[MAX];
    /** How far across the tarp (in blocks, each side) a stage rim is looked for. */
    public static final int SLANT_REACH = 8;
    /** A FOH element takes a column only when it is in the tarp's way: under it or in the cell beside it (a ledger along the tarp). */
    public static final int REGIE_REACH = 1;

    public StageTarpRailBlockEntity(BlockPos pos, BlockState state) {
        super(StageContent.STAGE_TARP_RAIL_ENTITY, pos, state);
    }

    @Override public int getLength() { return length; }
    public double getSlantDrop(int k) { return k >= 0 && k < MAX ? slantDrop[k] / 1024.0 : 0; }
    public double getSlantAcross(int k) { return k >= 0 && k < MAX ? slantAcross[k] / 1024.0 : 0; }
    public boolean isLiftSheet() { return liftSheet; }
    public int sheetColumns() { return edgeAbs.length; }
    /** Drop of column k under a rail whose row bottom is at world height {@code railY}. */
    public double sheetDrop(int k, double railY) { return railY - edgeAbs[k] / 1024.0; }
    public double sheetAcross(int k) { return edgeAcross[k] / 1024.0; }

    /**
     * Called by the lift just before it takes the structure away. A tied tarp keeps its bottom where it is: the cloth under the top row
     * is removed (and left out of the lifted blocks), the moving top row draws a sheet down to the old tie points. Returns the removed cells.
     */
    public java.util.List<BlockPos> prepareLift() {
        java.util.List<BlockPos> removed = new java.util.ArrayList<>();
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide || !(anchored || hasSlant())) return removed;
        BlockState rail = lvl.getBlockState(worldPosition);
        if (!(rail.getBlock() instanceof StageTarpRailBlock)) return removed;
        Direction dir = rail.getValue(StageTarpRailBlock.FACING);
        double plane = planeOffset(rail.getValue(StageProperties.SHIFT));
        int len = builtLength();
        int[] abs = new int[len], across = new int[len];
        for (int k = 0; k < len; k++) {
            BlockPos col = worldPosition.relative(dir, k);
            if (slantDrop[k] > 0) {
                abs[k] = worldPosition.getY() * 1024 - slantDrop[k];
                across[k] = slantAcross[k];
                continue;
            }
            int j = 1;
            BlockPos last = null;
            while (j < MAX) {
                BlockPos q = col.below(j);
                BlockState s = lvl.getBlockState(q);
                if (!(s.getBlock() instanceof StageTarpClothBlock)) break;
                last = q;
                removed.add(q);
                j++;
            }
            if (last == null) { abs[k] = worldPosition.getY() * 1024; across[k] = (int) Math.round(plane * 1024); continue; }
            int reach = lvl.getBlockState(last).getValue(TarpProperties.REACH);
            abs[k] = (int) Math.round((last.getY() - reach * 2 / 16.0) * 1024);
            across[k] = (int) Math.round(plane * 1024);
        }
        for (BlockPos q : removed) lvl.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        edgeAbs = abs;
        edgeAcross = across;
        liftSheet = true;
        setChanged();
        return removed;
    }

    public boolean hasSlant() { for (int v : slantDrop) if (v > 0) return true; return false; }
    /** Columns really built (another rail in line cuts the tarp). */
    public int builtLength() { return level == null ? length : Math.min(length, railAhead(level, getBlockState().getValue(StageTarpRailBlock.FACING))); }

    /** Position of the tarp plane across the cell (blocks from the centre, towards the positive side) for a shift value. */
    public static double planeOffset(int shift) {
        return shift == 1 ? 7.8 / 16.0 : shift == 2 ? -7.8 / 16.0 : 0.0;
    }
    @Override public int getHeight() { return height; }
    public TarpColor getColor() { return color; }
    public int getShift() { return getBlockState().getValue(StageProperties.SHIFT); }

    private static int clamp(int v) {
        return Math.max(1, Math.min(MAX, v));
    }

    /** From the Tarp Tool screen: length, maximum height and position across the cell (0 centre, 1 / 2 on a side). */
    public boolean isSlant() { return slant; }

    /** Tarp Tool: {@code flags} bits 0-2 = position, bit 3 (8) = hang straight (no lean to the stage rim). */
    public void applySettings(int length, int height, int flags) {
        int shift = flags & 7;
        this.anchored = false;                         // a new maximum height from the tool applies again
        this.slant = (flags & 8) == 0;
        this.length = clamp(length);
        this.height = clamp(height);
        Level lvl = getLevel();
        if (lvl != null && !lvl.isClientSide) {
            BlockState st = lvl.getBlockState(worldPosition);
            if (st.getBlock() instanceof StageTarpRailBlock && st.getValue(TarpProperties.TOP) == 0) {
                // a tarp tied to a stage rim keeps its side (against the stage); under a truss the side can be chosen
                BlockState next = st.setValue(StageProperties.SHIFT, Math.max(0, Math.min(2, shift)));
                if (next != st) lvl.setBlock(worldPosition, next, Block.UPDATE_ALL);
            }
        }
        setChanged();
        rebuild();
        sync();
    }

    @Override
    public void adjust(int mode, int delta) {
        if (mode == 0) length = clamp(length + delta);
        else height = clamp(height + delta);
        setChanged();
        rebuild();
        sync();
    }

    public boolean isOpen() { return open; }

    public void toggleOpen() {
        open = !open;
        setChanged();
        rebuild();
        sync();
    }

    /** Redstone: a powered tarp is gathered, unpowered it is spread (only on a change of the signal). */
    public void setPowered(boolean signal) {
        if (signal == powered) return;
        powered = signal;
        open = signal;
        setChanged();
        rebuild();
        sync();
    }

    private boolean regie() {
        return getBlockState().getBlock() instanceof StageTarpRailBlock r && r.isRegie();
    }

    public void setColor(TarpColor color) {
        this.color = color;
        setChanged();
        rebuild();
        sync();
    }

    private void sync() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        BlockState st = lvl.getBlockState(worldPosition);
        lvl.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_ALL);
    }

    private static boolean isTarp(BlockState s) {
        Block b = s.getBlock();
        return b instanceof StageTarpClothBlock || b instanceof StageTarpTopBlock;
    }

    /**
     * World height of the deck of the stage equipment that is the first solid thing under {@code top} (searched from {@code top} down),
     * or NaN when that first thing is not a stage, or is not below the rail row.
     */
    /** Rigging between the tarp and the stage (a tower mast, a truss, a hoist, a curtain, an angled truss): the stage is looked for under it. */
    private static boolean rigging(BlockState s) {
        net.minecraft.world.level.block.Block b = s.getBlock();
        return b instanceof com.concertstage.block.MotorTowerBlock || com.concertstage.block.TrussGap.isTruss(s)
            || b instanceof com.concertstage.block.CurtainClothBlock || b instanceof com.concertstage.block.CurtainRailBlock
            || b == StageContent.ANGLED_HOLDER || b == StageContent.RING_TRUSS || b == StageContent.RING_TRUSS_CELL || b instanceof com.concertstage.block.roof.RoofTrussBlock;
    }

    private double deckBelow(Level lvl, BlockPos top) {
        for (int dy = 0; dy <= MAX; dy++) {
            BlockPos q = top.below(dy);
            if (!lvl.hasChunkAt(q)) return Double.NaN;
            BlockState s = lvl.getBlockState(q);
            if (s.isAir() || isTarp(s) || s.getBlock() instanceof StageTarpRailBlock || rigging(s)) continue;   // towers, trusses, curtains: looked through
            if (!(s.getBlock() instanceof com.concertstage.stage.StageEquipmentBlock)) return Double.NaN;
            VoxelShape shape = s.getCollisionShape(lvl, q);
            if (shape.isEmpty()) continue;                         // an empty cell of the stage (tower hole...): look lower
            double surface = q.getY() + shape.max(Direction.Axis.Y);
            return surface <= worldPosition.getY() - 0.25 ? surface : Double.NaN;
        }
        return Double.NaN;
    }

    /**
     * Height of the tie point of the FOH element (ledger, node, guard rail, post, panel, girder, flight case) that is the first solid thing
     * under {@code top}, or NaN. A ledger / node is tied on its tube (middle of the cell), the others on their top.
     */
    private double regieBelow(Level lvl, BlockPos top) {
        for (int dy = 0; dy <= MAX; dy++) {
            BlockPos q = top.below(dy);
            if (!lvl.hasChunkAt(q)) return Double.NaN;
            BlockState s = lvl.getBlockState(q);
            if (s.isAir() || isTarp(s) || s.getBlock() instanceof StageTarpRailBlock) continue;
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            if (!path.startsWith("regie_") || path.startsWith("regie_tarp") || path.equals("regie_roof")) return Double.NaN;
            VoxelShape shape = s.getCollisionShape(lvl, q);
            if (shape.isEmpty()) continue;
            int drop = s.getBlock() instanceof com.concertstage.block.RegieBlock ? s.getValue(com.concertstage.block.RegieBlock.DROP) : 0;
            double y = q.getY() + (path.startsWith("regie_lisse") || path.startsWith("regie_noeud") ? 0.5 - drop / 16.0 : shape.max(Direction.Axis.Y));
            return y <= worldPosition.getY() - 0.25 ? y : Double.NaN;
        }
        return Double.NaN;
    }

    /** Columns before another tarp rail standing in line (that one owns the rest). */
    private int railAhead(Level lvl, Direction dir) {
        for (int k = 1; k < MAX; k++) {
            BlockPos q = worldPosition.relative(dir, k);
            if (lvl.hasChunkAt(q) && lvl.getBlockState(q).getBlock() instanceof StageTarpRailBlock) return k;
        }
        return MAX;
    }

    /** Places / updates / removes the pieces so the tarp matches its settings and what is under it. */
    public void rebuild() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        if (liftSheet) { liftSheet = false; edgeAbs = new int[0]; edgeAcross = new int[0]; setChanged(); sync(); }
        BlockState rail = lvl.getBlockState(worldPosition);
        if (!(rail.getBlock() instanceof StageTarpRailBlock)) return;
        Direction dir = rail.getValue(StageTarpRailBlock.FACING);
        Direction.Axis axis = dir.getAxis();
        int shift = rail.getValue(StageProperties.SHIFT);
        int top = rail.getValue(TarpProperties.TOP);
        boolean regie = rail.getBlock() instanceof StageTarpRailBlock r0 && r0.isRegie();
        if (regie && top <= 8) {                       // FOH tarp hung under an element: follows the element above it (ledger, node, girder, truss)
            int hang = StageTarpRailBlock.regieHang(lvl, worldPosition);
            if (hang != top || shift != 0) {
                rail = rail.setValue(TarpProperties.TOP, hang).setValue(StageProperties.SHIFT, 0);
                lvl.setBlock(worldPosition, rail, Block.UPDATE_CLIENTS);
                top = hang; shift = 0;
            }
        }
        Block topBlock = ((StageTarpRailBlock) rail.getBlock()).topBlock();
        Block clothBlock = ((StageTarpRailBlock) rail.getBlock()).clothBlock();
        int len = Math.min(open ? 1 : length, railAhead(lvl, dir));
        Direction start = dir.getOpposite();

        int[] rows = new int[len];
        boolean[] tied = new boolean[len];
        int[] reach = new int[len];
        int deepest = 1;
        for (int k = 0; k < len; k++) {
            BlockPos col = worldPosition.relative(dir, k);
            int r = 0;
            int limit = anchored ? MAX : height;
            while (r < limit) {
                BlockPos q = col.below(r);
                if (!lvl.hasChunkAt(q)) break;
                BlockState s = lvl.getBlockState(q);
                boolean free = (k == 0 && r == 0) || s.isAir() || isTarp(s);
                if (!free) break;
                r++;
            }
            rows[k] = r;
            if (r == 0) continue;                                  // the top cell of this column is taken: no tarp there
            BlockPos under = col.below(r);
            if (r < limit && lvl.hasChunkAt(under)) {
                BlockState s = lvl.getBlockState(under);
                tied[k] = !s.isAir();
                if (tied[k]) {
                    VoxelShape shape = s.getCollisionShape(lvl, under);
                    double surface = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
                    reach[k] = Math.max(0, Math.min(8, (int) Math.ceil((1.0 - surface) * 8.0 - 1e-6)));
                }
            }
            deepest = Math.max(deepest, r);
        }

        // Stretched at a slant down to the nearest stage rim (festival assembly, CSC decks...), across the tarp, at most SLANT_REACH blocks:
        // - column above a stage: the sheet leans to the edge of that stage (where its deck ends), at the height of the deck;
        // - column beside a stage: the sheet leans to the near edge of the stage.
        int[] nextDrop = new int[MAX];
        int[] nextAcross = new int[MAX];
        Direction nrm = Direction.fromAxisAndDirection(axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X, Direction.AxisDirection.POSITIVE);
        for (int k = 0; k < len && slant; k++) {
            if (rows[k] == 0) continue;
            BlockPos col = worldPosition.relative(dir, k);
            if (!Double.isNaN(regieBelow(lvl, col))) continue;    // a FOH element right under it: hangs straight onto it
            double bestY = 0, bestA = 0; int bestD = Integer.MAX_VALUE;
            // 1) FOH elements come first: the nearest ledger / node / rail / post beside the column takes the tarp (on its tube line)
            for (int d = 1; d <= REGIE_REACH && bestD == Integer.MAX_VALUE; d++) {
                for (int sgn : shift == 2 ? new int[] {-1, 1} : new int[] {1, -1}) {
                    double rg = regieBelow(lvl, col.relative(nrm, sgn * d));
                    if (!Double.isNaN(rg)) { bestD = d; bestY = rg; bestA = sgn * d; break; }
                }
            }
            // 2) otherwise the festival assembly / stage rim
            double here = bestD != Integer.MAX_VALUE ? Double.NaN : deckBelow(lvl, col);
            for (int sgn : bestD != Integer.MAX_VALUE ? new int[0] : shift == 2 ? new int[] {-1, 1} : new int[] {1, -1}) {
                double last = here;
                for (int d = 1; d <= SLANT_REACH && d < bestD; d++) {
                    BlockPos c = col.relative(nrm, sgn * d);
                    double sd = deckBelow(lvl, c);
                    if (!Double.isNaN(here)) {
                        if (Double.isNaN(sd)) { bestD = d; bestY = last; bestA = sgn * (d - 0.5); break; }   // the deck ends here: its rim
                        last = sd;
                    } else if (!Double.isNaN(sd)) { bestD = d; bestY = sd; bestA = sgn * (d - 0.5); break; }  // first deck beside: its near rim
                }
            }
            if (bestD == Integer.MAX_VALUE) continue;
            nextDrop[k] = (int) Math.round((worldPosition.getY() - bestY) * 1024.0);
            nextAcross[k] = (int) Math.round(bestA * 1024.0);
            rows[k] = 1;                                           // only the top row is made of blocks: the rest is the stretched sheet
        }
        if (!java.util.Arrays.equals(nextDrop, slantDrop) || !java.util.Arrays.equals(nextAcross, slantAcross)) {
            slantDrop = nextDrop;
            slantAcross = nextAcross;
            setChanged();
            sync();
        }
        if (hasSlant()) lvl.scheduleTick(worldPosition, rail.getBlock(), 40);   // a stage placed / removed beside it is noticed

        // the rail cell itself
        BlockState railNext = rail.setValue(TarpProperties.COLOR, color)
            .setValue(TarpProperties.end(start), true)
            .setValue(TarpProperties.end(dir), len == 1);
        if (railNext != rail) lvl.setBlock(worldPosition, railNext, Block.UPDATE_CLIENTS);

        int clearLen = Math.max(lastLength, len);
        int clearRows = Math.max(lastRows, deepest);
        for (int k = 0; k < Math.min(MAX, clearLen); k++) {
            for (int j = 0; j < Math.min(MAX, clearRows); j++) {
                if (k == 0 && j == 0) continue;
                BlockPos p = worldPosition.relative(dir, k).below(j);
                if (!lvl.hasChunkAt(p)) continue;
                BlockState cur = lvl.getBlockState(p);
                boolean wanted = k < len && j < rows[k];
                if (!wanted) {
                    if (isTarp(cur)) lvl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    continue;
                }
                if (!(cur.isAir() || isTarp(cur))) continue;
                boolean endStart = k == 0, endFar = k == len - 1;
                BlockState next;
                if (j == 0) {
                    next = topBlock.defaultBlockState()
                        .setValue(StageTarpTopBlock.AXIS, axis)
                        .setValue(TarpProperties.TOP, top);
                } else {
                    boolean last = j == rows[k] - 1;
                    next = clothBlock.defaultBlockState()
                        .setValue(StageTarpClothBlock.AXIS, axis)
                        .setValue(TarpProperties.BOTTOM, last ? (tied[k] ? 2 : 1) : 0)
                        .setValue(TarpProperties.REACH, last && tied[k] ? reach[k] : 0);
                }
                next = next.setValue(StageProperties.SHIFT, shift)
                    .setValue(TarpProperties.COLOR, color)
                    .setValue(TarpProperties.end(start), endStart)
                    .setValue(TarpProperties.end(dir), endFar);
                if (next != cur) lvl.setBlock(p, next, Block.UPDATE_CLIENTS);
            }
        }
        boolean allTied = len > 0;
        for (int k = 0; k < len; k++) if (rows[k] > 0 && !tied[k] && slantDrop[k] == 0) allTied = false;
        if (allTied != anchored) { anchored = allTied; setChanged(); }
        if (lastLength != len || lastRows != deepest) {
            lastLength = len;
            lastRows = deepest;
            setChanged();
        }
    }

    /** Removes every piece of this tarp (the rail was broken). */
    public void clearAll() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) return;
        BlockState rail = lvl.getBlockState(worldPosition);
        if (!(rail.getBlock() instanceof StageTarpRailBlock)) return;
        Direction dir = rail.getValue(StageTarpRailBlock.FACING);
        int len = Math.max(lastLength, length), rowsMax = Math.max(lastRows, height);
        for (int k = 0; k < Math.min(MAX, len); k++) {
            for (int j = 0; j < Math.min(MAX, rowsMax); j++) {
                BlockPos p = worldPosition.relative(dir, k).below(j);
                if (lvl.hasChunkAt(p) && isTarp(lvl.getBlockState(p))) lvl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The stretched sheet goes outside the block: Forge culls block entities with this box. */
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(MAX, MAX, MAX);
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
        tag.putInt("Length", length);
        tag.putInt("Height", height);
        tag.putString("Color", color.getSerializedName());
        tag.putInt("LastLength", lastLength);
        tag.putBoolean("Open", open);
        tag.putBoolean("Anchored", anchored);
        tag.putBoolean("LiftSheet", liftSheet);
        tag.putIntArray("EdgeAbs", edgeAbs);
        tag.putIntArray("EdgeAcross", edgeAcross);
        tag.putBoolean("Powered", powered);
        tag.putInt("LastRows", lastRows);
        tag.putIntArray("SlantDrop", slantDrop);
        tag.putBoolean("Straight", !slant);
        tag.putIntArray("SlantAcross", slantAcross);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Length")) length = clamp(tag.getInt("Length"));
        if (tag.contains("Height")) height = clamp(tag.getInt("Height"));
        if (tag.contains("Color")) {
            String c = tag.getString("Color");
            for (TarpColor v : TarpColor.values()) if (v.getSerializedName().equals(c)) color = v;
        }
        lastLength = tag.getInt("LastLength");
        open = tag.getBoolean("Open");
        anchored = tag.getBoolean("Anchored");
        liftSheet = tag.getBoolean("LiftSheet");
        edgeAbs = tag.getIntArray("EdgeAbs");
        edgeAcross = tag.getIntArray("EdgeAcross");
        if (edgeAcross.length != edgeAbs.length) { edgeAbs = new int[0]; edgeAcross = new int[0]; liftSheet = false; }
        powered = tag.getBoolean("Powered");
        lastRows = tag.getInt("LastRows");
        slantDrop = java.util.Arrays.copyOf(tag.getIntArray("SlantDrop"), MAX);
        slant = !tag.getBoolean("Straight");
        slantAcross = java.util.Arrays.copyOf(tag.getIntArray("SlantAcross"), MAX);
    }
}
