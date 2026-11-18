package com.concertstage.item;

import com.concertstage.angle.AnglePlacement;
import com.concertstage.block.MotorTowerBlock;
import com.concertstage.block.MultiPartBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Item of a truss or a motorised tower. Without the angle tool (or with it at 0) it places the block as usual. With the tool set to an
 * angle, a truss becomes an angled piece (see {@link AnglePlacement}) and a tower is turned about its own axis. A truss clicked on the
 * end of an angled truss continues it, with or without the tool.
 */
public class AngleAwareBlockItem extends BlockItem {
    public AngleAwareBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * A click in the air with a truss or a tower in hand and the angle tool in the other hand changes the angle (no need to swap hands):
     * click = next angle, sneaking + click = previous one.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (getBlock() instanceof MultiPartBlock && AngleToolItem.holding(player) && !AngleToolItem.aimingAtBlock(level, player)) {
            if (!level.isClientSide) AngleToolItem.cycle(player);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Block block = getBlock();
        int bend = AngleToolItem.bendOf(ctx.getPlayer());
        if(block instanceof MultiPartBlock truss&&truss.isChain()){InteractionResult mounted=com.concertstage.stage.StageRigging.truss(ctx,truss);if(mounted!=InteractionResult.PASS)return mounted;}
        if (block instanceof MotorTowerBlock tower) {
            var plan=com.concertstage.stage.StageRigging.towerPlan(ctx,tower);
            if(plan!=null){if(plan.context()==null)return com.concertstage.stage.StageModules.fail(ctx,"rig_no_support");if(!com.concertstage.stage.StageRigging.placeAdapter(ctx,plan))return com.concertstage.stage.StageModules.fail(ctx,"space_blocked");InteractionResult mounted=super.place(plan.context());if(!mounted.consumesAction()&&!ctx.getLevel().isClientSide)com.concertstage.stage.StageRigging.discardAdapter(ctx.getLevel(),plan);if(bend!=0&&mounted.consumesAction()&&!ctx.getLevel().isClientSide)AnglePlacement.angleTower(plan.context(),tower,bend);return mounted;}

            InteractionResult result = super.place(ctx);
            if (!tower.isHoist() && bend != 0 && result.consumesAction() && !ctx.getLevel().isClientSide) {
                AnglePlacement.angleTower(ctx, tower, bend);
            }
            return result;
        }
        // 1.2.0 beta 2b: the barriers and fences too (AnglePlacement#barrier)
        if (block instanceof MultiPartBlock truss && AnglePlacement.angleable(truss) && (bend != 0 || AnglePlacement.continuesPiece(ctx))) {
            InteractionResult result = AnglePlacement.place(ctx, truss, bend);
            if (result != InteractionResult.PASS) return result;
        }
        return super.place(ctx);
    }
}
