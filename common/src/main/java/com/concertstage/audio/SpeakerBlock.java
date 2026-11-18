package com.concertstage.audio;

import com.concertstage.StageContent;
import com.concertstage.block.Drops;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Head block of a CSC speaker array (line array flown under a truss, or ground stack). The cabinets are drawn by
 * {@code SpeakerRenderer}; the block itself holds the list in its block entity. Sneak + right click with an empty hand takes the last
 * cabinet off; breaking the block gives every cabinet back.
 */
public class SpeakerBlock extends Block implements EntityBlock {
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");

    public SpeakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HANGING, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANGING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) return Shapes.block();
        float[] b = be.headBox();
        return Shapes.box(b[0], b[1], b[2], Math.max(b[0] + 0.02, b[3]), Math.max(b[1] + 0.02, b[4]), Math.max(b[2] + 0.02, b[5]));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return useArray(level, pos, player, hand);
    }

    /** Also called by the collision cells of the array. */
    public static InteractionResult useArray(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(StageContent.ARRAY_TOOL)) {
            if (be.cabinets().isEmpty()) { player.displayClientMessage(Component.translatable("message.concertstage.frame_ready"), true); return InteractionResult.sidedSuccess(level.isClientSide); }
            if (level.isClientSide) com.concertstage.client.ClientHooks.openArray(pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!held.isEmpty() || !player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (be.firstModel() != null && be.firstModel().isMonitor()) {
                boolean ok = be.tryApplyAngles(be.yaw(), be.tilt() < -7.5f ? 0 : -15, new float[0]);
                player.displayClientMessage(Component.translatable(ok ? "message.concertstage.monitor_angle" : "message.concertstage.speaker_full", (int)be.tilt()), true);
                return InteractionResult.CONSUME;
            }
            String model = be.removeLast();
            if (model == null) {
                player.displayClientMessage(Component.translatable(be.dependent() != null ? "message.concertstage.frame_occupied"
                    : "message.concertstage.speaker_last"), true);
            } else {
                if (!player.getAbilities().instabuild) {
                    ItemStack back = new ItemStack(StageContent.speakerItem(model));
                    if (!player.getInventory().add(back)) player.drop(back, false);
                }
                level.playSound(null, pos, SoundEvents.METAL_BREAK, SoundSource.BLOCKS, 0.8f, 1.1f);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        giveBack(level, pos, player);
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Every cabinet back to the player who breaks the array (nothing in creative). */
    public static void giveBack(Level level, BlockPos pos, Player player) {
        Drops.breaking(level, player);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) return;
        // rev. 4: the stack standing on these subs comes down with them (frame and cabinets given back too)
        SpeakerBlockEntity up = be.dependent();
        if (up != null) {
            BlockPos q = up.getBlockPos();
            giveBack(level, q, player);
            level.removeBlock(q, false);
        }
        for (SpeakerBlockEntity.Cabinet c : be.cabinets()) Drops.give(level, pos, new ItemStack(StageContent.speakerItem(c.model())));
        if (!be.support().isEmpty()) Drops.give(level, pos, new ItemStack(StageContent.frameItem(be.support())));
        be.clearCells();
        AudioConsoleBlockEntity.forgetSpeaker(level, be.console(), pos);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }

    /** Removed any other way (explosion, command, a lift that took the cells first): the cells go too, nothing is dropped. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SpeakerBlockEntity be) be.clearCells();
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity empty && empty.cabinets().isEmpty() && !empty.support().isEmpty()) return new ItemStack(StageContent.frameItem(empty.support()));
        if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity be && !be.cabinets().isEmpty())
            return new ItemStack(StageContent.speakerItem(be.cabinets().get(0).model()));
        return new ItemStack(StageContent.speakerItem("csc_veyra_a1"));
    }
}
