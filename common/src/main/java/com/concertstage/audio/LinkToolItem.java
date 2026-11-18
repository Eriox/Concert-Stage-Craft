package com.concertstage.audio;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * CSC Audio Link Tool: right click a console to select it, then right click speaker arrays to link them to it in the group shown
 * on the tool (sneak + right click in the air to change it; "Auto" picks Main L / Main R by the side of the console, Subwoofers for
 * subs, Front Fill for a ground-stacked line array, Delay for an array behind the console). Sneak + right click an array unlinks it.
 * Right click in the air: diagnostics of the selected console. While the tool is held, the links are drawn as lines of particles.
 */
public class LinkToolItem extends Item {
    /** Tool modes: 0 = Auto, then 1 + group. */
    public static final int MODES = 1 + AudioGroups.COUNT;

    public LinkToolItem(Properties properties) {
        super(properties);
    }

    public static BlockPos console(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains("Console") ? BlockPos.of(t.getLong("Console")) : null;
    }

    public static int mode(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? 0 : Math.floorMod(t.getInt("Mode"), MODES);
    }

    public static Component modeName(int mode) {
        return mode == 0 ? Component.translatable("audio.concertstage.group.auto") : Component.translatable(AudioGroups.name(mode - 1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) return InteractionResult.PASS;
        if (level.getBlockEntity(pos) instanceof AudioConsoleBlockEntity) {
            if (!level.isClientSide) {
                stack.getOrCreateTag().putLong("Console", pos.asLong());
                player.displayClientMessage(Component.translatable("message.concertstage.link_console", pos.getX(), pos.getY(), pos.getZ()), true);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos array = SpeakerItem.arrayAt(level, pos);
        if (array == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        SpeakerBlockEntity sp = (SpeakerBlockEntity) level.getBlockEntity(array);
        if (player.isShiftKeyDown()) {
            if (sp.console() == null) { player.displayClientMessage(Component.translatable("message.concertstage.link_none"), true); return InteractionResult.CONSUME; }
            AudioConsoleBlockEntity.forgetSpeaker(level, sp.console(), array);
            sp.unlink();
            player.displayClientMessage(Component.translatable("message.concertstage.link_removed"), true);
            level.playSound(null, array, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6f, 0.6f);
            return InteractionResult.CONSUME;
        }
        BlockPos console = console(stack);
        if (console == null) { player.displayClientMessage(Component.translatable("message.concertstage.link_select_first"), true); return InteractionResult.CONSUME; }
        if (!console.closerThan(array, AudioConsoleBlockEntity.LINK_RANGE)) {
            player.displayClientMessage(Component.translatable("message.concertstage.link_far", AudioConsoleBlockEntity.LINK_RANGE), true);
            return InteractionResult.CONSUME;
        }
        if (!level.isLoaded(console) || !(level.getBlockEntity(console) instanceof AudioConsoleBlockEntity c)) {
            player.displayClientMessage(Component.translatable("message.concertstage.link_console_gone"), true);
            return InteractionResult.CONSUME;
        }
        if (sp.console() != null && !sp.console().equals(console)) AudioConsoleBlockEntity.forgetSpeaker(level, sp.console(), array);
        if (!c.link(array)) { player.displayClientMessage(Component.translatable("message.concertstage.link_full", AudioConsoleBlockEntity.MAX_SPEAKERS), true); return InteractionResult.CONSUME; }
        int mode = mode(stack);
        int group = mode == 0 ? autoGroup(level, console, array, sp) : mode - 1;
        sp.link(console, group);
        player.displayClientMessage(Component.translatable("message.concertstage.link_done", Component.translatable(AudioGroups.name(group)), c.speakers().size()), true);
        level.playSound(null, array, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
        return InteractionResult.CONSUME;
    }

    /** Group chosen by "Auto" from where the array is seen from the console operator. */
    public static int autoGroup(Level level, BlockPos console, BlockPos array, SpeakerBlockEntity sp) {
        if (sp.firstModel() != null && sp.firstModel().isMonitor()) return AudioGroups.MONITOR_1;
        if (sp.isSub()) return AudioGroups.SUBS;
        if (!sp.hanging() && (sp.firstModel() == null || sp.firstModel().kind() != SpeakerModels.Kind.MAIN)) return AudioGroups.FRONT_FILL;
        Direction facing = level.getBlockState(console).hasProperty(AudioConsoleBlock.FACING) ? level.getBlockState(console).getValue(AudioConsoleBlock.FACING) : Direction.SOUTH;
        Direction look = facing.getOpposite();                         // the operator looks at the stage
        double vx = array.getX() - console.getX(), vz = array.getZ() - console.getZ();
        double ahead = vx * look.getStepX() + vz * look.getStepZ();
        if (ahead < -4) return AudioGroups.DELAY;                     // behind the desk: a delay tower
        double right = vx * -look.getStepZ() + vz * look.getStepX();
        return right > 0 ? AudioGroups.MAIN_RIGHT : AudioGroups.MAIN_LEFT;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {
            int m = (mode(stack) + 1) % MODES;
            stack.getOrCreateTag().putInt("Mode", m);
            player.displayClientMessage(Component.translatable("message.concertstage.link_mode", modeName(m)), true);
            return InteractionResultHolder.consume(stack);
        }
        diagnostics(level, player, console(stack));
        return InteractionResultHolder.consume(stack);
    }

    /** Chat report of a console: links per group, arrays out of loaded chunks, dead links (removed), what it plays. */
    public static void diagnostics(Level level, Player player, BlockPos console) {
        if (console == null) { player.displayClientMessage(Component.translatable("message.concertstage.link_select_first"), true); return; }
        if (!level.isLoaded(console) || !(level.getBlockEntity(console) instanceof AudioConsoleBlockEntity c)) {
            player.sendSystemMessage(Component.translatable("message.concertstage.link_console_gone").withStyle(ChatFormatting.RED));
            return;
        }
        int dead = c.pruneLinks();
        int[] per = new int[AudioGroups.COUNT];
        int unloaded = 0, cabinets = 0;
        for (BlockPos p : c.speakers()) {
            if (!level.isLoaded(p)) { unloaded++; continue; }
            if (level.getBlockEntity(p) instanceof SpeakerBlockEntity s) { per[s.group()]++; cabinets += s.cabinets().size(); }
        }
        player.sendSystemMessage(Component.translatable("message.concertstage.diag_title", console.getX(), console.getY(), console.getZ(),
            c.speakers().size(), cabinets).withStyle(ChatFormatting.AQUA));
        net.minecraft.network.chat.MutableComponent line = Component.literal("  ");
        for (int g = 0; g < AudioGroups.COUNT; g++) {
            if (g > 0) line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable(AudioGroups.name(g)).withStyle(per[g] > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + per[g] + (c.muted(g) ? " M" : "")).withStyle(c.muted(g) ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        player.sendSystemMessage(line);
        if (unloaded > 0) player.sendSystemMessage(Component.translatable("message.concertstage.diag_unloaded", unloaded).withStyle(ChatFormatting.YELLOW));
        if (dead > 0) player.sendSystemMessage(Component.translatable("message.concertstage.diag_dead", dead).withStyle(ChatFormatting.YELLOW));
        if (per[AudioGroups.MAIN_LEFT] > 0 != per[AudioGroups.MAIN_RIGHT] > 0)
            player.sendSystemMessage(Component.translatable("message.concertstage.diag_mono").withStyle(ChatFormatting.YELLOW));
        AudioConsoleBlockEntity.Track t = c.currentTrack();
        if (c.state() == AudioConsoleBlockEntity.PLAYING && t != null)
            player.sendSystemMessage(Component.translatable("message.concertstage.diag_playing", t.name()).withStyle(ChatFormatting.GREEN));
        else player.sendSystemMessage(Component.translatable("message.concertstage.diag_idle", c.playlist().size()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        BlockPos c = console(stack);
        lines.add(Component.translatable("tooltip.concertstage.link_mode", modeName(mode(stack))).withStyle(ChatFormatting.AQUA));
        lines.add(c == null ? Component.translatable("tooltip.concertstage.link_no_console").withStyle(ChatFormatting.GRAY)
            : Component.translatable("tooltip.concertstage.link_console", c.getX(), c.getY(), c.getZ()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.concertstage.link_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return console(stack) != null;
    }
}
