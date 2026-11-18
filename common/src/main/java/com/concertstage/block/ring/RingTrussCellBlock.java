package com.concertstage.block.ring;

import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible cell of a circular truss: gives the ring its collision (you can stand on it, bump into it, and Theatrical light beams
 * stop on it), lets Theatrical / Extra Lights fixtures hang on it, and travels with the towers like a truss. The shape is the part
 * of the ring that crosses this cell (stored in its block entity). Created and removed by the ring's rigging point; no item.
 */
public class RingTrussCellBlock extends Block implements EntityBlock {
    public RingTrussCellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;      // the ring is drawn by the rigging point
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!(level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell)) return Shapes.block();
        return cell.lightOnly() ? Shapes.empty() : cell.shape();
    }

    /** A roof sheet cell stops light beams only; a truss cell is solid. */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!(level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell)) return Shapes.block();
        if (cell.lightOnly()) return com.concertstage.compat.LightCompat.isLightRay(ctx) ? cell.shape() : Shapes.empty();
        return cell.shape();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RingTrussCellBlockEntity(pos, state);
    }

    // ------------------------------------------------------------ light (1.0.2): a cell never darkens anything
    // (a new cell is a full block until its box arrives, and the light computed then stayed: a dark patch under the roof that could
    // remain after the roof was taken down)
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    // ------------------------------------------------------------ left-over cells (1.0.2)
    /** Slowly checks that the cell still has its owner; a cell nobody owns any more (roof or ring gone) removes itself. */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (orphan(level, pos)) level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    /** No owner at its anchor, or an owner that no longer lists it (only decided when the anchor is loaded). */
    public static boolean orphan(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell) || cell.anchor() == null) return true;
        BlockPos anchor = cell.anchor();
        if (!level.isLoaded(anchor)) return false;
        return !CellOwners.owns(level, anchor, pos);
    }

    /** A roof is one structure: its cells are not mined one by one (break the foot, or use the CSC Structure Manager). */
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (isRoofCell(level, pos)) return 0.0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && isRoofCell(level, pos))
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.concertstage.roof_cell_hint"), true);
        super.attack(state, level, pos, player);
    }


    static boolean isRoofCell(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell && cell.anchor() != null
            && level.getBlockState(cell.anchor()).getBlock() instanceof com.concertstage.block.roof.RoofTrussBlock;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult hung = com.concertstage.compat.TheatricalCompat.tryHang(level, pos, player, hand, hit);
        if (hung != null) return hung;
        if (level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell && cell.anchor() != null) {
            BlockPos anchor = cell.anchor();
            if (level.getBlockState(anchor).getBlock() instanceof com.concertstage.audio.SpeakerBlock)          // a cell of a speaker array
                return com.concertstage.audio.SpeakerBlock.useArray(level, anchor, player, hand);
            boolean roof = level.getBlockState(anchor).getBlock() instanceof com.concertstage.block.roof.RoofTrussBlock;
            if (player.getItemInHand(hand).is(roof ? StageContent.ROOF_TOOL : StageContent.RING_TOOL)) {      // each frame its own controller
                if (level.isClientSide) {
                    if (roof) com.concertstage.client.ClientHooks.openRoof(anchor);
                    else com.concertstage.client.ClientHooks.openRing(anchor);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Breaking a cell of a speaker array breaks the array (every cabinet back to the player); a cell of a roof broken anyway (creative)
     * is put back at once by its roof.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell && cell.anchor() != null) {
            BlockPos anchor = cell.anchor();
            if (level.getBlockState(anchor).getBlock() instanceof com.concertstage.audio.SpeakerBlock) {
                com.concertstage.audio.SpeakerBlock.giveBack(level, anchor, player);
                level.setBlock(anchor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            } else if (level.getBlockEntity(anchor) instanceof com.concertstage.block.roof.RoofTrussBlockEntity roof && level.getServer() != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.concertstage.roof_cell_hint"), true);
                level.getServer().execute(() -> { if (!roof.isRemoved()) roof.rebuildCells(); });
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell && cell.anchor() != null
            && level.getBlockState(cell.anchor()).getBlock() instanceof com.concertstage.audio.SpeakerBlock speaker)
            return speaker.getCloneItemStack(level, cell.anchor(), level.getBlockState(cell.anchor()));
        if (level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell && cell.anchor() != null
            && level.getBlockState(cell.anchor()).getBlock() instanceof com.concertstage.block.roof.RoofTrussBlock roof) return new ItemStack(roof);
        return new ItemStack(StageContent.RING_TRUSS);
    }
}
