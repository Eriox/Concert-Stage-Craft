package com.concertstage.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FOH tarp item: clicking any face of a FOH element (the bottom one too) (ledger, node, girder, post, rail...) puts the tarp UNDER that element, hung from it,
 * instead of beside or on top of it.
 */
public class RegieTarpItem extends BlockItem {
    public RegieTarpItem(Block block, Properties properties) {
        super(block, properties);
    }

    static boolean isRegieElement(BlockState s) {
        String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return path.startsWith("regie_") && !path.startsWith("regie_tarp") && !path.equals("regie_roof");
    }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (ctx.replacingClickedOnBlock()) return ctx;
        Level level = ctx.getLevel();
        BlockPos element = ctx.getClickedPos().relative(face.getOpposite());
        if (!isRegieElement(level.getBlockState(element))) return ctx;
        BlockPos target = com.concertstage.block.tarp.StageTarpRailBlock.regieHangPos(level, element);
        for (BlockPos p = element.below(); p.getY() >= target.getY(); p = p.below())
            if (!level.getBlockState(p).canBeReplaced(ctx)) return ctx;      // something in the way: placed where clicked
        return BlockPlaceContext.at(ctx, target, Direction.DOWN);
    }
}
