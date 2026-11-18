package com.concertstage.stage;

import com.concertstage.StageContent;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Exact-height, quarter-turn attachments; all edits are transactional and unticked. */
public final class StageMounting {
    private StageMounting(){}
    private record Mount(double x,double y,double z,int turn){}
    private record Change(BlockPos pos,BlockState oldState,BlockState newState,List<StageAttachmentBlockEntity.Entry> oldEntries,List<StageAttachmentBlockEntity.Entry> entries){}
    private static InteractionResult fail(UseOnContext ctx,String key){if(ctx.getPlayer()!=null&&!ctx.getLevel().isClientSide)ctx.getPlayer().displayClientMessage(Component.translatable("message.concertstage."+key),true);return InteractionResult.FAIL;}
    private static InteractionResult success(UseOnContext ctx){return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);}
    private static void consume(UseOnContext ctx){if(ctx.getPlayer()==null||!ctx.getPlayer().getAbilities().instabuild)ctx.getItemInHand().shrink(1);ctx.getLevel().playSound(null,ctx.getClickedPos(),SoundEvents.METAL_PLACE,SoundSource.BLOCKS,.8F,1F);}
    private static double[] hit(UseOnContext ctx,BlockPos main,int turn){Vec3 v=ctx.getClickLocation();double[] p=StageMath.rotate(v.x-main.getX()-.5,0,v.z-main.getZ()-.5,-turn);p[0]+=.5;p[2]+=.5;return p;}
    private static Mount choose(double[] hit,double w,double d,double y,StageSpec rail){
        double x=Math.max(0,Math.min(w,hit[0])),z=Math.max(0,Math.min(d,hit[2]));
        if(rail.id.equals("safety_railings_corner_1x1")){
            if(w<1-1e-7||d<1-1e-7)return null;boolean east=x>w/2,south=z>d/2;return new Mount(east?w:0,y,south?d:0,east?(south?2:1):(south?3:0));
        }
        int side=0;double best=z;for(int i=1;i<4;i++){double distance=new double[]{z,w-x,d-z,x}[i];if(distance<best){side=i;best=distance;}}
        double length=rail.id.equals("safety_railings_2m")?2:1,edge=(side%2==0)?w:d;
        if(edge<length-1e-7)return null;double start=Math.max(0,Math.min(edge-length,Math.floor((side%2==0?x:z)+1e-7)));
        return switch(side){case 0->new Mount(start,y,0,0);case 1->new Mount(w,y,start,1);case 2->new Mount(start+length,y,d,2);default->new Mount(0,y,start+length,3);};
    }
    private static double[] worldOrigin(BlockPos main,Mount m,int ownerTurn){double[] p=StageMath.rotate(m.x-.5,m.y,m.z-.5,ownerTurn);return new double[]{main.getX()+.5+p[0],main.getY()+p[1],main.getZ()+.5+p[2]};}
    private static Map<String,String> groups(Level level,BlockPos main,BlockState state){
        StageEquipmentBlock b=(StageEquipmentBlock)state.getBlock();int turn=StageSpec.turns(state.getValue(StageEquipmentBlock.FACING));Map<String,String> out=new LinkedHashMap<>();
        for(int i=0;i<b.spec.count();i++){BlockPos p=main.offset(b.spec.offset(i,turn));if(StageStructure.matches(b,state,level.getBlockState(p),i)&&level.getBlockEntity(p)instanceof StageAttachmentBlockEntity a)for(var e:a.entries())out.put(e.token(),e.key());}return out;
    }
    public static InteractionResult railing(UseOnContext ctx,StageEquipmentBlock host,BlockState state,StageEquipmentBlock railing){
        if(!host.spec.mountable)return fail(ctx,"not_mountable");BlockPos main=host.mainPos(state,ctx.getClickedPos());double walk=host.spec.walk;
        Mount m=choose(hit(ctx,main,StageSpec.turns(state.getValue(StageEquipmentBlock.FACING))),host.spec.width,host.depth(state),walk,railing.spec);
        if(m==null)return fail(ctx,"railing_too_long");return attach(ctx,main,state,railing.spec,m);
    }
    /**
     * 1.2.0 beta 2b (14 November: "the railings do not join up and do not follow the platform at an angle"): a railing clicked on a platform
     * laid at an angle (platform protractor, or joined to one) is clamped on the nearest edge of that platform, at its walking height and
     * at its angle, on the same 1 m steps from its corner as on a straight platform: railings set one after the other join up, the corner
     * railing closes a corner. It goes with the platform (support "piece:" + its token).
     */
    public static InteractionResult railingOnPiece(UseOnContext ctx,StageEquipmentBlock railing){
        Level level=ctx.getLevel();BlockPos pos=ctx.getClickedPos();Vec3 hit=ctx.getClickLocation();
        if(!(level.getBlockEntity(pos)instanceof StageAttachmentBlockEntity be))return InteractionResult.PASS;
        StageAttachmentBlockEntity.Entry host=null;double dist=.09;
        for(var e:be.entries()){StageSpec s=StageSpec.find(e.key());if(s==null||!StagePlatformAngle.turnable(s)||!"ground".equals(e.support()))continue;
            for(double[] b:e.boxes()){double dx=Math.max(pos.getX()+b[0]-hit.x,Math.max(0,hit.x-pos.getX()-b[3])),dy=Math.max(pos.getY()+b[1]-hit.y,Math.max(0,hit.y-pos.getY()-b[4])),dz=Math.max(pos.getZ()+b[2]-hit.z,Math.max(0,hit.z-pos.getZ()-b[5]));double d=dx*dx+dy*dy+dz*dz;if(d<dist){dist=d;host=e;}}}
        if(host==null)return InteractionResult.PASS;
        StageSpec spec=StageSpec.get(host.key());
        if(!spec.mountable||spec.outline().length!=4)return fail(ctx,"not_mountable");
        double hx=pos.getX()+host.x(),hy=pos.getY()+host.y(),hz=pos.getZ()+host.z(),a=host.degrees();
        double[] q=StagePose.rotate(hit.x-hx,0,hit.z-hz,-a);
        Mount m=choose(new double[]{q[0],0,q[2]},spec.width,spec.depth,spec.walk,railing.spec);
        if(m==null)return fail(ctx,"railing_too_long");
        double[] o=StagePose.point(new double[]{m.x,m.y,m.z},hx,hy,hz,a);
        if(!StageModules.place(ctx,railing.spec,o[0],o[1],o[2],a+m.turn*90.0,railing.spec.id+"=1",StageModules.ON_PIECE+host.token(),null,Map.of(),host.token()))return fail(ctx,"space_blocked");
        if(!level.isClientSide)consume(ctx);
        return success(ctx);
    }
    private static InteractionResult attach(UseOnContext ctx,BlockPos main,BlockState state,StageSpec component,Mount m){
        Level level=ctx.getLevel();StageEquipmentBlock owner=(StageEquipmentBlock)state.getBlock();int ownerTurn=StageSpec.turns(state.getValue(StageEquipmentBlock.FACING));double[] origin=worldOrigin(main,m,ownerTurn);int turn=Math.floorMod(ownerTurn+m.turn,4);String token=UUID.randomUUID().toString();List<Change> changes=new ArrayList<>();List<AABB> collisions=new ArrayList<>();
        for(int i=0;i<owner.spec.count();i++){
            BlockPos p=main.offset(owner.spec.offset(i,ownerTurn));int[] cell=owner.spec.local(i);
            // 1.2.0 beta 2b: a cell of the top row also holds what stands above the platform (the 1.1 m of a railing on a deck one block
            // high, e.g. the FlexDecks): clipped to the cell, the railing was cut at the deck and only its clamps were drawn
            double top=cell[1]==owner.spec.grid[1]-1?Math.max(1,component.raw_bounds[4]+m.y-cell[1]+.01):1;
            double[] limits=StageMath.cellBox(new double[]{cell[0]==0?-.125:0,0,cell[2]==0?-.125:0,cell[0]==owner.spec.grid[0]-1?1.125:1,top,cell[2]==owner.spec.grid[2]-1?1.125:1},ownerTurn);
            var entry=new StageAttachmentBlockEntity.Entry(token,component.id,origin[0]-p.getX(),origin[1]-p.getY(),origin[2]-p.getZ(),turn,limits);
            if(StageMath.intersect(StageMath.transformBox(component.raw_bounds,entry.x(),entry.y(),entry.z(),turn),limits)==null)continue;
            BlockState old=level.getBlockState(p);boolean existing=StageStructure.matches(owner,state,old,i);
            if(!level.isInWorldBounds(p)||(!existing&&!old.canBeReplaced()))return fail(ctx,"space_blocked");
            if(ctx.getPlayer()!=null&&!ctx.getPlayer().mayUseItemAt(p,ctx.getClickedFace(),ctx.getItemInHand()))return fail(ctx,"space_blocked");
            List<StageAttachmentBlockEntity.Entry> before=existing&&level.getBlockEntity(p)instanceof StageAttachmentBlockEntity be?be.entries():List.of();List<double[]> boxes=entry.boxes();
            for(var previous:before){StageSpec prior=StageSpec.find(previous.key());if(prior!=null&&prior.kind.equals(component.kind))for(double[] a:boxes)for(double[] b:previous.boxes())if(StageMath.overlaps(a,b,.0005))return fail(ctx,"already_mounted");}
            for(double[] b:boxes)collisions.add(new AABB(b[0]+p.getX(),b[1]+p.getY(),b[2]+p.getZ(),b[3]+p.getX(),b[4]+p.getY(),b[5]+p.getZ()));
            List<StageAttachmentBlockEntity.Entry> after=new ArrayList<>(before);after.add(entry);changes.add(new Change(p,old,state.setValue(owner.partProperty(),i).setValue(StageEquipmentBlock.ATTACHED,true),before,after));
        }
        if(changes.isEmpty())return fail(ctx,"space_blocked");
        AABB bounds=collisions.isEmpty()?new AABB(main):collisions.get(0);for(AABB b:collisions)bounds=bounds.minmax(b);
        for(var entity:level.getEntitiesOfClass(LivingEntity.class,bounds,e->!e.isSpectator()))for(AABB box:collisions)if(entity.getBoundingBox().intersects(box))return fail(ctx,"space_blocked");
        if(level.isClientSide)return success(ctx);
        if(!commit(level,changes))return fail(ctx,"space_blocked");consume(ctx);return success(ctx);
    }
    private static boolean commit(Level level,List<Change> changes){
        StageStructure.begin();try{
            for(Change c:changes){if(!level.getBlockState(c.pos).equals(c.oldState))throw new IllegalStateException("Stage changed during mount");if(!c.oldState.equals(c.newState)&&!level.setBlock(c.pos,c.newState,Block.UPDATE_CLIENTS))throw new IllegalStateException("Attachment cell rejected");
                StageAttachmentBlockEntity be=level.getBlockEntity(c.pos)instanceof StageAttachmentBlockEntity existing?existing:null;
                if(be==null){be=new StageAttachmentBlockEntity(c.pos,c.newState);level.setBlockEntity(be);}be.replace(c.entries);
            }
        }catch(RuntimeException e){
            for(Change c:changes){level.setBlock(c.pos,c.oldState,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);if(!c.oldEntries.isEmpty()){StageAttachmentBlockEntity be=new StageAttachmentBlockEntity(c.pos,c.oldState);level.setBlockEntity(be);be.replace(c.oldEntries);}else level.removeBlockEntity(c.pos);}
            LogUtils.getLogger().warn("Stage attachment placement rolled back",e);return false;
        }finally{StageStructure.end();}
        for(Change c:changes)level.updateNeighborsAt(c.pos,c.newState.getBlock());return true;
    }
    public static InteractionResult joinHalves(UseOnContext ctx,StageEquipmentBlock host,BlockState state){
        if(state.getValue(StageEquipmentBlock.JOINED))return InteractionResult.PASS;Level level=ctx.getLevel();BlockPos main=host.mainPos(state,ctx.getClickedPos());if(!groups(level,main,state).isEmpty())return fail(ctx,"remove_attachments_first");int turn=StageSpec.turns(state.getValue(StageEquipmentBlock.FACING));
        for(int i=0;i<host.spec.count();i++)if(host.spec.occupied[i]){BlockPos p=main.offset(host.spec.offset(i,turn));BlockState s=level.getBlockState(p);if(!StageStructure.matches(host,state,s,i)||!level.isUnobstructed(s.setValue(StageEquipmentBlock.JOINED,true),p,CollisionContext.empty()))return fail(ctx,"space_blocked");}
        if(level.isClientSide)return success(ctx);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();boolean joined=true;
        StageStructure.begin();try{
            for(int i=0;i<host.spec.count();i++)if(host.spec.occupied[i]){BlockPos p=main.offset(host.spec.offset(i,turn));BlockState old=level.getBlockState(p);before.put(p,old);if(!level.setBlock(p,old.setValue(StageEquipmentBlock.JOINED,true),Block.UPDATE_CLIENTS)){joined=false;break;}}
            if(!joined)for(var entry:before.entrySet())level.setBlock(entry.getKey(),entry.getValue(),Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
        }finally{StageStructure.end();}
        if(!joined)return fail(ctx,"space_blocked");for(BlockPos p:before.keySet())level.updateNeighborsAt(p,host);consume(ctx);return success(ctx);
    }
    public static InteractionResult dismantle(Level level,BlockPos pos,BlockState state,Player player,BlockHitResult hit){
        if(!(level.getBlockEntity(pos)instanceof StageAttachmentBlockEntity be)||be.entries().isEmpty())return InteractionResult.PASS;
        Vec3 p=hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());String token=null;double best=Double.POSITIVE_INFINITY;
        for(var e:be.entries())for(double[] b:e.boxes()){double dx=Math.max(b[0]-p.x,Math.max(0,p.x-b[3])),dy=Math.max(b[1]-p.y,Math.max(0,p.y-b[4])),dz=Math.max(b[2]-p.z,Math.max(0,p.z-b[5]));double d=dx*dx+dy*dy+dz*dz;if(d<best){best=d;token=e.token();}}
        if(token==null||best>.01)return InteractionResult.PASS;if(level.isClientSide)return InteractionResult.SUCCESS;
        StageEquipmentBlock owner=(StageEquipmentBlock)state.getBlock();BlockPos main=owner.mainPos(state,pos);Map<String,String> all=groups(level,main,state),removed=new LinkedHashMap<>();removed.put(token,all.get(token));
        int turn=StageSpec.turns(state.getValue(StageEquipmentBlock.FACING));
        StageStructure.begin();try{for(int i=0;i<owner.spec.count();i++){BlockPos at=main.offset(owner.spec.offset(i,turn));BlockState s=level.getBlockState(at);if(!StageStructure.matches(owner,state,s,i)||!(level.getBlockEntity(at)instanceof StageAttachmentBlockEntity a))continue;List<StageAttachmentBlockEntity.Entry> next=a.entries().stream().filter(e->!removed.containsKey(e.token())).toList();a.replace(next);if(next.isEmpty()){if(owner.spec.occupied[i])level.setBlock(at,s.setValue(StageEquipmentBlock.ATTACHED,false),Block.UPDATE_ALL);else level.setBlock(at,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);level.removeBlockEntity(at);}}}finally{StageStructure.end();}
        if(!player.getAbilities().instabuild)for(String key:removed.values())if(StageContent.ITEMS.containsKey(key)){ItemStack stack=new ItemStack(StageContent.ITEMS.get(key));if(!player.getInventory().add(stack))Block.popResource(level,pos,stack);}return InteractionResult.CONSUME;
    }
}
