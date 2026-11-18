package com.concertstage.stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Statically baked base, optional unticked attachment entities only where needed. */
public class StageEquipmentBlock extends Block implements EntityBlock {
    /** Broken by a player: the item goes straight into the inventory. */
    @Override
    public void playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.entity.BlockEntity be,
                              net.minecraft.world.item.ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,15);
    public static final BooleanProperty ATTACHED=BooleanProperty.create("attached");
    public static final BooleanProperty JOINED=BooleanProperty.create("joined");
    public static final BooleanProperty MOUNTED=BooleanProperty.create("mounted");
    public final StageSpec spec;
    public StageEquipmentBlock(Properties properties,StageSpec spec){super(properties);this.spec=spec;BlockState s=stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(partProperty(),0).setValue(ATTACHED,false);if(s.hasProperty(JOINED))s=s.setValue(JOINED,false);if(s.hasProperty(MOUNTED))s=s.setValue(MOUNTED,false);registerDefaultState(s);}
    public IntegerProperty partProperty(){return PART;}
    public int part(BlockState s){return s.getValue(partProperty());}
    public int variant(BlockState s){return(s.hasProperty(JOINED)&&s.getValue(JOINED))||(s.hasProperty(MOUNTED)&&s.getValue(MOUNTED))?1:0;}
    public double depth(BlockState s){return s.hasProperty(JOINED)&&s.getValue(JOINED)?1:spec.depth;}
    public BlockPos mainPos(BlockState s,BlockPos pos){return pos.subtract(spec.offset(part(s),StageSpec.turns(s.getValue(FACING))));}
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,partProperty(),ATTACHED);}
    public BlockState getStateForPlacement(BlockPlaceContext ctx){BlockState s=defaultBlockState().setValue(FACING,ctx.getHorizontalDirection().getOpposite());return StageStructure.canPlace(ctx,s)?s:null;}
    public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(ATTACHED)?new StageAttachmentBlockEntity(pos,state):null;}
    public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){
        VoxelShape base=spec.shape(part(s),StageSpec.turns(s.getValue(FACING)),variant(s));
        if(s.getValue(ATTACHED)&&level.getBlockEntity(pos)instanceof StageAttachmentBlockEntity a)return a.withBase(s,base);return base;
    }
    public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return getShape(s,l,p,c);}
    /** Furniture, racks, LED floors, speakers… from other mods (SEF, Rackworks, NDI Displays, StagePower…) need a sturdy floor: every
     *  solid cell of a stage counts as one (its real shape stays the collision). */
    public VoxelShape getBlockSupportShape(BlockState s,BlockGetter l,BlockPos p){VoxelShape v=getShape(s,l,p,CollisionContext.empty());return v.isEmpty()?v:net.minecraft.world.phys.shapes.Shapes.block();}
    /** A cell left over from an older layout of the piece (the 2.2 m festival deck, now at 2.0 m): no shape, nothing drawn; it gives way. */
    public boolean stray(BlockState s){int i=part(s);return (i>=spec.occupied.length||!spec.occupied[i])&&!s.getValue(ATTACHED);}
    public boolean canBeReplaced(BlockState s,BlockPlaceContext ctx){return stray(s)||super.canBeReplaced(s,ctx);}
    public void onRemove(BlockState s,Level level,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock())&&!level.isClientSide&&!StageStructure.editing()&&!stray(s))StageStructure.remove(level,pos,s,true);super.onRemove(s,level,pos,next,moving);}
    public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){com.concertstage.block.Drops.breaking(level,player);if(!level.isClientSide&&player.getAbilities().instabuild)StageStructure.remove(level,pos,state,false);super.playerWillDestroy(level,pos,state,player);}
    public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    public InteractionResult use(BlockState s,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isShiftKeyDown()&&player.getItemInHand(hand).isEmpty()&&s.getValue(ATTACHED)){InteractionResult result=StageModules.dismantle(level,pos,player,hit);if(result!=InteractionResult.PASS)return result;return StageMounting.dismantle(level,pos,s,player,hit);}return InteractionResult.PASS;
    }
    public BlockState updateShape(BlockState state,Direction d,BlockState neighbor,net.minecraft.world.level.LevelAccessor level,BlockPos pos,BlockPos other){if(stray(state))return Blocks.AIR.defaultBlockState();if(!level.isClientSide()&&state.getValue(ATTACHED))level.scheduleTick(pos,this,1);return state;}
    public void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){StageModules.validate(level,pos);}
    public static final class Large extends StageEquipmentBlock {
        public static final IntegerProperty LARGE_PART=IntegerProperty.create("part",0,127);
        public Large(Properties p,StageSpec s){super(p,s);}public IntegerProperty partProperty(){return LARGE_PART;}
    }
    public static final class Half extends StageEquipmentBlock {
        public Half(Properties p,StageSpec s){super(p,s);}protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(JOINED);}
    }
    public static final class Railing extends StageEquipmentBlock {
        public Railing(Properties p,StageSpec s){super(p,s);}protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(MOUNTED);}
    }
}
