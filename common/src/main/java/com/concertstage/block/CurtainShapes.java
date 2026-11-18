package com.concertstage.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outline shapes of a curtain block for each axis and shift (see {@link StageProperties#SHIFT}). Shift 1 and 2 put the cloth on the line
 * of the truss tubes, 1.5 px from the cell edge since the trusses are one block wide (1.2.9).
 */
final class CurtainShapes {
    private static final VoxelShape[] X = {
        Block.box(0, 0, 6, 16, 16, 10), Block.box(0, 0, 12.5, 16, 16, 16), Block.box(0, 0, 0, 16, 16, 3.5)};
    private static final VoxelShape[] Z = {
        Block.box(6, 0, 0, 10, 16, 16), Block.box(12.5, 0, 0, 16, 16, 16), Block.box(0, 0, 0, 3.5, 16, 16)};

    private CurtainShapes() {}

    static VoxelShape get(Direction.Axis axis, int shift) {
        return (axis == Direction.Axis.X ? X : Z)[Math.max(0, Math.min(2, shift))];
    }
}
