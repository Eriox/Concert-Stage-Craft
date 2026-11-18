package com.concertstage.stage;

import com.concertstage.StageContent;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Transactional placement and one-drop dismantling of any footprint, up to the 8x4 assembly. */
public final class StageStructure {
    private static final ThreadLocal<Integer> EDITING=ThreadLocal.withInitial(()->0);
    private StageStructure(){}
    public static boolean editing(){return EDITING.get()>0;}
    public static void begin(){EDITING.set(EDITING.get()+1);}
    public static void end(){EDITING.set(EDITING.get()-1);}
    public static boolean canPlace(BlockPlaceContext ctx,BlockState state){
        StageEquipmentBlock b=(StageEquipmentBlock)state.getBlock();Level level=ctx.getLevel();BlockPos main=ctx.getClickedPos();int turn=StageSpec.turns(state.getValue(StageEquipmentBlock.FACING));
        for(int i=0;i<b.spec.count();i++)if(b.spec.occupied[i]){
            BlockPos p=main.offset(b.spec.offset(i,turn));BlockState part=state.setValue(b.partProperty(),i);
            if(!level.isInWorldBounds(p)||!level.getBlockState(p).canBeReplaced()||!level.isUnobstructed(part,p,CollisionContext.empty()))return false;
            if(ctx.getPlayer()!=null&&!ctx.getPlayer().mayUseItemAt(p,ctx.getClickedFace(),ctx.getItemInHand()))return false;
        }
        return true;
    }
    public static boolean place(BlockPlaceContext ctx,BlockState state){
        if(!canPlace(ctx,state))return false;StageEquipmentBlock b=(StageEquipmentBlock)state.getBlock();Level level=ctx.getLevel();BlockPos main=ctx.getClickedPos();int turn=StageSpec.turns(state.getValue(StageEquipmentBlock.FACING));Map<BlockPos,BlockState> old=new LinkedHashMap<>();boolean success=true;
        begin();try{
            for(int i=0;i<b.spec.count();i++)if(b.spec.occupied[i]){BlockPos p=main.offset(b.spec.offset(i,turn));old.put(p,level.getBlockState(p));if(!level.setBlock(p,state.setValue(b.partProperty(),i),Block.UPDATE_CLIENTS)){success=false;break;}}
            if(!success)for(var e:old.entrySet())level.setBlock(e.getKey(),e.getValue(),Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
        }finally{end();}
        if(success)for(BlockPos p:old.keySet())level.updateNeighborsAt(p,b);return success;
    }
    public static boolean matches(StageEquipmentBlock b,BlockState owner,BlockState candidate,int part){return candidate.is(b)&&b.part(candidate)==part&&candidate.getValue(StageEquipmentBlock.FACING)==owner.getValue(StageEquipmentBlock.FACING);}
    public static Map<String,String> clearAttachments(Level level,BlockPos main,BlockState owner){
        StageEquipmentBlock b=(StageEquipmentBlock)owner.getBlock();int turn=StageSpec.turns(owner.getValue(StageEquipmentBlock.FACING));Map<String,String> groups=new LinkedHashMap<>();
        for(int i=0;i<b.spec.count();i++){BlockPos p=main.offset(b.spec.offset(i,turn));if(matches(b,owner,level.getBlockState(p),i)&&level.getBlockEntity(p)instanceof StageAttachmentBlockEntity a){for(var entry:a.entries())if(!StageModules.internal(entry))groups.put(entry.token(),entry.key());a.replace(List.of());}}
        return groups;
    }
    public static void remove(Level level,BlockPos pos,BlockState state,boolean dropAttachments){
        StageEquipmentBlock b=(StageEquipmentBlock)state.getBlock();BlockPos main=b.mainPos(state,pos);int turn=StageSpec.turns(state.getValue(StageEquipmentBlock.FACING));begin();try{
            Map<String,String> groups=new LinkedHashMap<>();
            // onRemove runs after the palette contains the replacement, but before the old BE is detached.
            if(level.getBlockEntity(pos)instanceof StageAttachmentBlockEntity original)for(var e:original.entries())if(!StageModules.internal(e))groups.put(e.token(),e.key());
            for(int i=0;i<b.spec.count();i++){BlockPos at=main.offset(b.spec.offset(i,turn));if(at.equals(pos)||matches(b,state,level.getBlockState(at),i))StageModules.removedHost(level,at,dropAttachments);}
            groups.putAll(clearAttachments(level,main,state));
            for(int i=0;i<b.spec.count();i++){BlockPos p=main.offset(b.spec.offset(i,turn));if(!p.equals(pos)&&matches(b,state,level.getBlockState(p),i))level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);}
            if(dropAttachments)for(String key:groups.values())if(StageContent.ITEMS.containsKey(key))com.concertstage.block.Drops.give(level,pos,new ItemStack(StageContent.ITEMS.get(key)));
        }finally{end();}
    }
}
