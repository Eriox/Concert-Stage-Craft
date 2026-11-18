package com.concertstage.stage;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class StageEquipmentItem extends BlockItem {
    public StageEquipmentItem(StageEquipmentBlock block,Properties properties){super(block,properties);}
    public StageEquipmentBlock equipment(){return(StageEquipmentBlock)getBlock();}
    protected boolean placeBlock(BlockPlaceContext context,BlockState state){return StageStructure.place(context,state);}
    public InteractionResult useOn(UseOnContext ctx){
        if(equipment().spec.kind.equals("stairs"))return StageFlights.place(ctx,equipment());
        // 1.2.0 beta 2b: a railing on a platform laid at an angle: clamped on its edge, at its angle (StageMounting#railingOnPiece)
        if(!ctx.isSecondaryUseActive()&&"railing".equals(equipment().spec.kind)){InteractionResult r=StageMounting.railingOnPiece(ctx,equipment());if(r!=InteractionResult.PASS)return r;}
        if(!ctx.isSecondaryUseActive()){
            BlockState target=ctx.getLevel().getBlockState(ctx.getClickedPos());
            if(target.getBlock()instanceof StageEquipmentBlock host){
                String key=equipment().spec.id;
                if("railing".equals(equipment().spec.kind))return StageMounting.railing(ctx,host,target,equipment());
                if(key.equals(host.spec.id)&&target.hasProperty(StageEquipmentBlock.JOINED)&&!target.getValue(StageEquipmentBlock.JOINED))return StageMounting.joinHalves(ctx,host,target);
            }
        }
        // against the side of an angled platform or of a triangle: joined edge to edge, at that platform's angle
        if(!ctx.isSecondaryUseActive()&&StagePlatformAngle.turnable(equipment().spec)&&StagePlatformJoin.side(ctx)){
            StagePlatformJoin.Host host=StagePlatformJoin.host(ctx.getLevel(),ctx.getClickedPos(),ctx.getClickLocation());
            if(StagePlatformJoin.joins(host))return StagePlatformJoin.place(ctx,equipment(),host);
        }
        // platform protractor held at an angle: the platform is turned by that angle (mounted piece)
        int bend=com.concertstage.item.PlatformAngleToolItem.angleOf(ctx.getPlayer());
        if(bend!=0&&StagePlatformAngle.turnable(equipment().spec))return StagePlatformAngle.place(ctx,equipment(),bend);
        if(bend!=0&&("assembly".equals(equipment().spec.kind)))return StageModules.fail(ctx,"platform_angle_too_big");
        // on a floor between two block levels (a 40 cm deck...): the platform stands on that floor, not on the next block level
        if(StagePlatformAngle.turnable(equipment().spec)&&StagePlatformAngle.floorBetweenLevels(ctx)>0)return StagePlatformAngle.place(ctx,equipment(),0);
        return super.useOn(ctx);
    }
    public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,level,lines,flag);StageSpec s=equipment().spec;
        lines.add(Component.translatable("tooltip.concertstage.category."+s.kind).withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("tooltip.concertstage.dimensions."+s.id).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.concertstage.use."+s.id).withStyle(ChatFormatting.GRAY));
        if(s.mountable)lines.add(Component.translatable("tooltip.concertstage.mountable").withStyle(ChatFormatting.DARK_GRAY));
    }
}
