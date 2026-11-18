package com.concertstage.light;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * CSC LUMICORE X1 lighting desk (2 m wide). FACING points to the operator (the player who placed it); right click opens the desk.
 * The model is static (OBJ); the screens, the fader levels and the status LEDs are drawn live by its renderer.
 */
public class LumicoreBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // authored for an operator on +Z (south): desk x -0.5..1.5, z 0.03..0.97, height 1.0 (screens up to 1.45)
            double[][] boxes = {{-0.49, 0, 0.03, 1.49, 1.03, 0.97}, {-0.49, 1.03, 0.04, 1.49, 1.42, 0.42}};
            VoxelShape s = Shapes.empty();
            for (double[] b : boxes) s = Shapes.or(s, rotated(d, b));
            SHAPES[d.get2DDataValue()] = s;
        }
    }

    private static VoxelShape rotated(Direction d, double[] b) {
        // model rotation of the blockstate: south 0, west 90, north 180, east 270 (clockwise seen from above)
        int turns = switch (d) { case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> 0; };
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        for (int i = 0; i < turns; i++) {
            // 90 degrees clockwise about the block centre: (x, z) -> (1 - z, x)
            double nx0 = 1 - z1, nx1 = 1 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
        }
        return Shapes.box(Math.max(-1, x0), b[1], Math.max(-1, z0), Math.min(2, x1), b[4], Math.min(2, z1));
    }

    public LumicoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
        return new LumicoreBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StageContent.LUMICORE_ENTITY) return null;
        return (l, p, s, be) -> LumicoreBlockEntity.serverTick(l, p, s, (LumicoreBlockEntity) be);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof LumicoreBlockEntity be) {
            be.owner = p.getUUID();
            be.ownerName = p.getGameProfile().getName();
            be.changed();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(StageContent.LIGHT_PATCH_TOOL) || held.is(StageContent.CONFIG_CARD)) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) com.concertstage.client.ClientHooks.openLumicore(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LumicoreBlockEntity be)
            LightServer.deskRemoved(level, pos, be);
        super.onRemove(state, level, pos, newState, moved);
    }
}
