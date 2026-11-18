package com.concertstage.audio;

import com.concertstage.StageContent;
import com.concertstage.block.ring.RingTrussCellBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One CSC cabinet (Veyra A1 / A2 / B2, Noryx A1 / A2 / B1). Right click an array of the same family and kind: the cabinet is added to
 * it (under a flown line array, on top of a sub stack). Elsewhere: a new array - flown when clicked under a block (a truss), a ground
 * stack when clicked on top of one.
 */
public class SpeakerItem extends Item {
    private final String model;

    public SpeakerItem(String model, Properties properties) {
        super(properties);
        this.model = model;
    }

    public String model() {
        return model;
    }

    /** The head block of the array the clicked block belongs to (the block itself, or one of its collision cells), or null. */
    public static BlockPos arrayAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity) return pos;
        if (level.getBlockEntity(pos) instanceof RingTrussCellBlockEntity cell && cell.anchor() != null
            && level.getBlockEntity(cell.anchor()) instanceof SpeakerBlockEntity) return cell.anchor();
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos array = arrayAt(level, ctx.getClickedPos());
        if (array != null && !(player != null && player.isShiftKeyDown())) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            SpeakerBlockEntity be = (SpeakerBlockEntity) level.getBlockEntity(array);
            SpeakerModels.Model mine = SpeakerModels.get(model);
            // rev. 4: clicked on the subs under a stack (LA on S218-F, C6 on its pole): the cabinet goes to that stack
            SpeakerBlockEntity up = be.dependent();
            if (up != null && be.firstModel() != null && !be.firstModel().fits(mine)) { be = up; array = up.getBlockPos(); }
            SpeakerModels.Model first = be.firstModel();
            if (first != null && !first.fits(mine)) {
                // 1.2.0 beta 2: names both cabinets (the one held and the array's)
                if (player != null) player.displayClientMessage(Component.translatable("message.concertstage.speaker_mismatch_two",
                    Component.translatable("item.concertstage." + model), Component.translatable("item.concertstage." + first.id())), true);
                return InteractionResult.FAIL;
            }
            if (!be.add(model)) {
                if (player != null) player.displayClientMessage(Component.translatable("message.concertstage.speaker_full"), true);
                return InteractionResult.FAIL;
            }
            if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            level.playSound(null, array, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.9f, 0.9f);
            if (player != null) player.displayClientMessage(Component.translatable("message.concertstage.speaker_added", be.cabinets().size()), true);
            return InteractionResult.CONSUME;
        }
        // a new array
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        BlockPos target = place.getClickedPos();
        if (!place.canPlace() || !level.getWorldBorder().isWithinBounds(target)) return InteractionResult.FAIL;
        Direction face = ctx.getClickedFace();
        boolean hanging = face == Direction.DOWN
            || face != Direction.UP && !level.getBlockState(target.above()).isAir() && level.getBlockState(target.below()).isAir();
        if (hanging && (SpeakerModels.get(model).isSub() || SpeakerModels.get(model).isMonitor() || SpeakerModels.groundOnly(SpeakerModels.get(model)))) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState state = StageContent.SPEAKER.defaultBlockState().setValue(SpeakerBlock.HANGING, hanging);
        if (!level.setBlock(target, state, 3)) return InteractionResult.FAIL;
        if (level.getBlockEntity(target) instanceof SpeakerBlockEntity be) {
            float yaw = player != null ? player.getYRot() + 180 : 0;
            be.init(model, yaw, hanging);
            if (!be.canFit()) { level.removeBlock(target, false); return InteractionResult.FAIL; }
        }
        if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        level.playSound(null, target, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        if (player != null) player.displayClientMessage(Component.translatable(hanging ? "message.concertstage.speaker_flown" : "message.concertstage.speaker_stacked"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        SpeakerModels.Model m = SpeakerModels.get(model);
        if (m == null) return;
        lines.add(Component.translatable("tooltip.concertstage." + model).withStyle(ChatFormatting.GRAY));
        if (m.isSub()) lines.add(Component.translatable("tooltip.concertstage.speaker_sub").withStyle(ChatFormatting.DARK_GRAY));
        else if (m.isSingle()) lines.add(Component.translatable("tooltip.concertstage.aurex_single").withStyle(ChatFormatting.DARK_GRAY));
        else lines.add(Component.translatable("tooltip.concertstage.speaker_line", (int) m.hDispersion(), (int) m.splayMax()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip.concertstage.speaker_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
