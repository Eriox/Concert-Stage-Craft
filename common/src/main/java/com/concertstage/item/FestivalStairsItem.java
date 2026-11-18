package com.concertstage.item;

import com.concertstage.block.MultiPartBlock;
import com.concertstage.stage.StageSurfaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

/**
 * Festival stage stairs (2.0 m). Clicked on the side of a stage whose deck is 2.0 m above its base (the pre-assembled festival stage), the
 * stairs are set against that side, standing on the ground of the stage, their last riser at its rim. Anywhere else they are placed like any
 * other piece, climbing away from the player.
 */
public class FestivalStairsItem extends BlockItem {
    private static final double RISE = 2.0;
    private static final int RUN = 3;

    public FestivalStairsItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isHorizontal() && getBlock() instanceof MultiPartBlock stairs) {
            Level level = ctx.getLevel();
            BlockPos stage = ctx.getClickedPos().relative(face.getOpposite());
            StageSurfaces.Surface s = StageSurfaces.at(level, stage);
            if (s != null) {
                int base = (int) Math.floor(s.y() - RISE + 0.01);
                if (Math.abs(s.y() - RISE - base) > 0.05) return fail(ctx, "festival_stairs_height");
                BlockPos foot = new BlockPos(stage.getX(), base, stage.getZ()).relative(face, RUN);
                for (int lz = 0; lz < RUN; lz++)
                    for (int ly = 0; ly < 3; ly++) {
                        BlockPos p = foot.relative(face.getOpposite(), lz).above(ly);
                        if (!level.getBlockState(p).canBeReplaced()) return fail(ctx, "space_blocked");
                    }
                if (!level.isClientSide) {
                    stairs.placeAll(level, foot, face);
                    SoundType sound = stairs.defaultBlockState().getSoundType();
                    level.playSound(null, foot, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
                    if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.place(ctx);
    }

    private static InteractionResult fail(BlockPlaceContext ctx, String key) {
        if (ctx.getPlayer() != null && !ctx.getLevel().isClientSide)
            ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage." + key), true);
        return InteractionResult.FAIL;
    }
}
