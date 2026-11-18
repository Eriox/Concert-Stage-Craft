package com.concertstage.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * CSC 1.3.0 LiveVision: base of the equipment blocks drawn from the supplied Blender models. FACING is where the front of the model
 * (lens, LED face, front panel) points: towards the player who places it. HANGING: placed against the underside of a block (truss,
 * ceiling): the cameras are drawn upside down under their mount (PTZ ceiling plate, REMOTE R1 truss clamp).
 */
public abstract class VisionBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    /** Shape when facing north (x0, y0, z0, x1, y1, z1 in 1/16), hanging shape (null: same). */
    private final double[] box, hangBox;
    private final VoxelShape[] shapes = new VoxelShape[8];

    protected VisionBlock(Properties props, double[] box, double[] hangBox) {
        super(props);
        this.box = box;
        this.hangBox = hangBox;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HANGING, false));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = rotated(box, d);
            shapes[4 + d.get2DDataValue()] = rotated(hangBox == null ? box : hangBox, d);
        }
    }

    /** Box given for FACING = NORTH turned to {@code d}. */
    static VoxelShape rotated(double[] b, Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        double[] r = switch (d) {
            case SOUTH -> new double[] {16 - x1, 16 - z1, 16 - x0, 16 - z0};
            case WEST -> new double[] {z0, 16 - x1, z1, 16 - x0};
            case EAST -> new double[] {16 - z1, x0, 16 - z0, x1};
            default -> new double[] {x0, z0, x1, z1};
        };
        return Block.box(Math.min(r[0], r[2]), b[1], Math.min(r[1], r[3]), Math.max(r[0], r[2]), b[4], Math.max(r[1], r[3]));
    }

    /** Can be hung under a block (cameras). */
    protected boolean canHang() { return false; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HANGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean hang = canHang() && ctx.getClickedFace() == Direction.DOWN;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(HANGING, hang);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape s = shapes[(state.getValue(HANGING) ? 4 : 0) + state.getValue(FACING).get2DDataValue()];
        return s == null ? Shapes.block() : s;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Broken by a player: the item goes straight into the inventory (as every CSC block). */
    @Override
    public void playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state,
                              net.minecraft.world.level.block.entity.BlockEntity be, net.minecraft.world.item.ItemStack tool) {
        com.concertstage.block.Drops.playerDestroy(this, level, player, pos, state, be, tool);
    }

    @Override
    public void playerWillDestroy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        com.concertstage.block.Drops.breaking(level, player);
        super.playerWillDestroy(level, pos, state, player);
    }
}
