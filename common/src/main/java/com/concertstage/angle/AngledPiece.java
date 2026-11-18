package com.concertstage.angle;

import com.concertstage.ConcertStage;
import com.concertstage.block.MultiPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * One truss placed at an angle: which block it is, where its pivot is (the centre of its first cell, y = the bottom of the piece) and the
 * compass angle of its authored +x axis (see {@link AngleMath}). It is drawn by the model of that block, turned about the pivot.
 *
 * The pivot is stored relative to the corner of the cell that holds the piece, not in world coordinates: when a lift carries the cell
 * somewhere else the piece goes with it.
 */
public final class AngledPiece {
    public final String block;     // registry path of a truss block of this mod, e.g. "truss_h1_l2"
    public final double x;         // pivot, relative to the minimum corner of the holder cell
    public final double y;
    public final double z;
    public final float angle;

    public AngledPiece(String block, double x, double y, double z, float angle) {
        this.block = block;
        this.x = x;
        this.y = y;
        this.z = z;
        this.angle = angle;
    }

    /** The truss block, or null if the name is not a block of this mod any more. */
    public MultiPartBlock truss() {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(ConcertStage.MOD_ID, block));
        return b instanceof MultiPartBlock mp && b != Blocks.AIR ? mp : null;
    }

    /** Blocks along the axis (2 for the 1 x 2 trusses). */
    public int length() {
        MultiPartBlock t = truss();
        return t == null ? 1 : t.sizeX();
    }

    /** Blocks high (2 for the 2 x 2 panels and the h2 corners). */
    public int height() {
        MultiPartBlock t = truss();
        return t == null ? 1 : t.sizeY();
    }

    public double absX(BlockPos cell) { return cell.getX() + x; }

    public double absY(BlockPos cell) { return cell.getY() + y; }

    public double absZ(BlockPos cell) { return cell.getZ() + z; }

    /** Centre of the end face of the piece (world x, z) when it is held by {@code cell}. */
    public double[] end(BlockPos cell) {
        return AngleMath.endPoint(absX(cell), absZ(cell), angle, length());
    }

    /** Centre of the start face of the piece (world x, z) when it is held by {@code cell}. */
    public double[] start(BlockPos cell) {
        return AngleMath.startPoint(absX(cell), absZ(cell), angle);
    }

    /** A piece with its pivot at world (ax, ay, az), held by {@code cell}. */
    public static AngledPiece at(String block, BlockPos cell, double ax, double ay, double az, float angle) {
        return new AngledPiece(block, ax - cell.getX(), ay - cell.getY(), az - cell.getZ(), angle);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Block", block);
        t.putDouble("X", x);
        t.putDouble("Y", y);
        t.putDouble("Z", z);
        t.putFloat("A", angle);
        return t;
    }

    public static AngledPiece load(CompoundTag t) {
        return new AngledPiece(t.getString("Block"), t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), t.getFloat("A"));
    }
}
