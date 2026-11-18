package com.concertstage.audio;
import com.concertstage.StageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.network.chat.Component;

/** Place an empty support, then add compatible cabinets by right-clicking it. */
public final class SpeakerFrameItem extends Item {
    private final String frame;
    public SpeakerFrameItem(String frame, Properties p) { super(p); this.frame = frame; }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
                                          java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        String key = frame.endsWith("_stack_adapter") ? "tooltip.concertstage.stack_adapter" : "tooltip.concertstage." + frame;
        if (net.minecraft.locale.Language.getInstance().has(key)) lines.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResult useOn(UseOnContext ctx) {
        var level = ctx.getLevel(); var player = ctx.getPlayer(); var type = SpeakerFrames.get(frame);
        var existing = SpeakerItem.arrayAt(level, ctx.getClickedPos());
        // rev. 4: a ground support clicked on its sub stack stands on top of it (AUREX LA frame on S218-F, stack adapter on its sub,
        // C6 pole on the B18P)
        if (existing != null && type.sub() != null && !type.flying() && level.getBlockEntity(existing) instanceof SpeakerBlockEntity sub
            && !sub.hanging() && sub.firstModel() != null && type.sub().equals(sub.firstModel().id())) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            String why = standOn(level, sub, type);
            if (why != null) {
                if (player != null) player.displayClientMessage(Component.translatable(why), true);
                return InteractionResult.FAIL;
            }
        } else if (existing != null) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(level.getBlockEntity(existing) instanceof SpeakerBlockEntity s) || !s.installFrame(frame)) return InteractionResult.FAIL;
        } else {
            if (type.onSubOnly()) {
                if (player != null) player.displayClientMessage(Component.translatable("message.concertstage.frame_on_sub",
                    Component.translatable("item.concertstage." + type.sub())), true);
                return InteractionResult.FAIL;
            }
            if (type.flying() ? ctx.getClickedFace() != Direction.DOWN : ctx.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
            if (level.getBlockState(ctx.getClickedPos()).getCollisionShape(level, ctx.getClickedPos()).isEmpty()) return InteractionResult.FAIL;
            var place = new BlockPlaceContext(ctx); var target = place.getClickedPos();
            if (!place.canPlace() || !level.getWorldBorder().isWithinBounds(target)) return InteractionResult.FAIL;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!level.setBlock(target, StageContent.SPEAKER.defaultBlockState().setValue(SpeakerBlock.HANGING, type.flying()), 3)) return InteractionResult.FAIL;
            if (level.getBlockEntity(target) instanceof SpeakerBlockEntity s) {
                s.initFrame(frame, player == null ? 0 : player.getYRot() + 180);
                if (!s.canFit()) { level.removeBlock(target, false); return InteractionResult.FAIL; }
            }
        }
        if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        if (player != null) player.displayClientMessage(Component.translatable("message.concertstage.frame_ready"), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Rev. 4: a new empty support standing on {@code sub}: its block is the first one wholly above the subs (same column), its bottom
     * lowered onto the top of the last sub. Returns null when placed, else the message key of the reason.
     */
    public static String standOn(net.minecraft.world.level.Level level, SpeakerBlockEntity sub, SpeakerFrames.Frame type) {
        if (sub.dependent() != null) return "message.concertstage.frame_occupied";
        double top = sub.getBlockPos().getY() + sub.stackTop();       // stackTop: from the bottom of the sub's block (its base included)
        int y = (int) Math.ceil(top - 1e-4);
        BlockPos target = new BlockPos(sub.getBlockPos().getX(), y, sub.getBlockPos().getZ());
        if (!level.getWorldBorder().isWithinBounds(target) || !level.getBlockState(target).canBeReplaced()) return "message.concertstage.speaker_full";
        if (!level.setBlock(target, StageContent.SPEAKER.defaultBlockState().setValue(SpeakerBlock.HANGING, false), 3)) return "message.concertstage.speaker_full";
        if (!(level.getBlockEntity(target) instanceof SpeakerBlockEntity s)) { level.removeBlock(target, false); return "message.concertstage.speaker_full"; }
        s.initFrame(type.id(), sub.yaw());
        s.standOn(sub.getBlockPos(), (float) (top - y));
        if (!s.canFit()) { level.removeBlock(target, false); return "message.concertstage.speaker_full"; }
        return null;
    }
}
