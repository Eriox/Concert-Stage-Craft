package com.concertstage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Finds the rail that owns a piece of curtain, so the tool also works when you click the cloth itself. */
public final class CurtainOwners {
    private CurtainOwners() {}

    /** @return the position of the curtain rail controlling the block at {@code pos}, or null. */
    public static BlockPos find(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CurtainRailBlock) return pos;
        if (state.getBlock() instanceof CurtainClothBlock) return findCurtainRail(level, pos, state.getValue(CurtainClothBlock.AXIS));
        return null;
    }

    private static BlockPos findCurtainRail(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir.getAxis() != axis) continue;
            for (int k = 0; k < CurtainRailBlockEntity.MAX; k++) {
                for (int j = 0; j < CurtainRailBlockEntity.MAX; j++) {
                    BlockPos q = pos.relative(dir.getOpposite(), k).above(j);
                    BlockState st = level.getBlockState(q);
                    if (st.getBlock() instanceof CurtainRailBlock && st.getValue(CurtainRailBlock.FACING) == dir) {
                        BlockEntity be = level.getBlockEntity(q);
                        if (be instanceof CurtainRailBlockEntity rail && rail.getLength() > k && rail.getHeight() > j) return q;
                    }
                }
            }
        }
        return null;
    }
}
