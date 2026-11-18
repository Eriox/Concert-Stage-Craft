package com.concertstage.audio;

import com.concertstage.StageContent;
import com.concertstage.block.Drops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** CSC Audio Console (FOH desk): right click to open it; the operator stands in front of it, facing the stage. */
public class AudioConsoleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    private static VoxelShape localBox(Direction d,double x0,double y0,double z0,double x1,double y1,double z1) {
        double angle=Math.toRadians(d.toYRot()),c=Math.cos(angle),s=Math.sin(angle);double minX=1e9,minZ=1e9,maxX=-1e9,maxZ=-1e9;
        for(int k=0;k<4;k++) {double x=(k&1)==0?x0:x1,z=(k&2)==0?z0:z1;
            double xx=.5+x*c-z*s,zz=.5+x*s+z*c;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minZ=Math.min(minZ,zz);maxZ=Math.max(maxZ,zz);}
        return Shapes.box(minX,y0,minZ,maxX,y1,maxZ);
    }
    static {
        for(Direction d:Direction.Plane.HORIZONTAL) {
            VoxelShape shape=localBox(d,-.95,.81,-.52,.95,1.075,.50);
            shape=Shapes.or(shape,localBox(d,-.74,1.065,-.55,.76,1.40,-.30));
            for(double x:new double[]{-.78,.78})for(double z:new double[]{-.34,.34})
                shape=Shapes.or(shape,localBox(d,x-.055,0,z-.055,x+.055,.81,z+.055));
            SHAPES[d.get2DDataValue()]=shape;
        }
    }
    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) { return net.minecraft.world.level.block.RenderShape.INVISIBLE; }

    public AudioConsoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** FACING = towards the operator (the player who placed it). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state=defaultBlockState().setValue(FACING,ctx.getHorizontalDirection().getOpposite());
        var level=ctx.getLevel();var pos=ctx.getClickedPos();var shape=SHAPES[state.getValue(FACING).get2DDataValue()];var bounds=shape.bounds();
        for(int x=(int)Math.floor(bounds.minX);x<=Math.floor(bounds.maxX-1e-6);x++)
            for(int y=(int)Math.floor(bounds.minY);y<=Math.floor(bounds.maxY-1e-6);y++)
                for(int z=(int)Math.floor(bounds.minZ);z<=Math.floor(bounds.maxZ-1e-6);z++) {
                    if(x==0&&y==0&&z==0)continue;var q=pos.offset(x,y,z);
                    var other=level.getBlockState(q).getCollisionShape(level,q).move(x,y,z);
                    if(Shapes.joinIsNotEmpty(shape,other,net.minecraft.world.phys.shapes.BooleanOp.AND))return null;
                }
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AudioConsoleBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StageContent.AUDIO_CONSOLE_ENTITY) return null;
        return (l, p, s, be) -> AudioConsoleBlockEntity.serverTick(l, p, s, (AudioConsoleBlockEntity) be);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(StageContent.LINK_TOOL)||held.is(StageContent.DELAY_PLANNER)) return InteractionResult.PASS;            // the Link Tool selects the console
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) com.concertstage.client.ClientHooks.openConsole(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Drops.breaking(level, player);
        unlinkAll(level, pos);
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool) {
        Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) unlinkAll(level, pos);
        super.onRemove(state, level, pos, newState, moving);
    }

    private static void unlinkAll(Level level, BlockPos pos) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof AudioConsoleBlockEntity c)) return;
        for (BlockPos p : c.speakers())
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof SpeakerBlockEntity s && pos.equals(s.console())) s.unlink();
    }
}
