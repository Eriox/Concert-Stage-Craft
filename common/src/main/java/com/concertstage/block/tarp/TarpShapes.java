package com.concertstage.block.tarp;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Outline of a tarp piece: a thin plate in the middle of the cell (shift 0) or on the cell edge (shift 1 = positive side, 2 = negative). */
final class TarpShapes {
    private static final VoxelShape[] X = {
        Block.box(0, 0, 7, 16, 16, 9), Block.box(0, 0, 14, 16, 16, 16), Block.box(0, 0, 0, 16, 16, 2)};
    private static final VoxelShape[] Z = {
        Block.box(7, 0, 0, 9, 16, 16), Block.box(14, 0, 0, 16, 16, 16), Block.box(0, 0, 0, 2, 16, 16)};

    private TarpShapes() {}

    static VoxelShape get(Direction.Axis axis, int shift) {
        return (axis == Direction.Axis.X ? X : Z)[Math.max(0, Math.min(2, shift))];
    }
}
