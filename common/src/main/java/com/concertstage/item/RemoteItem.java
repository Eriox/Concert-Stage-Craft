package com.concertstage.item;

import com.concertstage.block.MotorTowerBlock;
import com.concertstage.client.ClientHooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lift remote. Right click a motorised tower to link it, sneak + right click to unlink it. Right click anywhere else
 * opens the control screen, which lifts every linked tower (and the structure hanging on them) together.
 * The linked towers are kept in the item itself ("Towers" = list of positions, "Dim" = dimension).
 */
public class RemoteItem extends Item {
    private static final String TOWERS = "Towers";
    private static final String DIM = "Dim";

    public RemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockState state = level.getBlockState(ctx.getClickedPos());
        if (player == null || !(state.getBlock() instanceof MotorTowerBlock tower)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);

        ItemStack stack = ctx.getItemInHand();
        BlockPos main = tower.mainPos(state, ctx.getClickedPos());
        List<BlockPos> list = read(stack, level);
        if (player.isShiftKeyDown()) {
            list.remove(main);
            write(stack, level, list);
            player.displayClientMessage(Component.translatable("message.concertstage.lift_unlinked", list.size()), true);
        } else {
            if (!list.contains(main)) list.add(main);
            write(stack, level, list);
            player.displayClientMessage(Component.translatable("message.concertstage.lift_linked", list.size()), true);
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) ClientHooks.openRemote(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ------------------------------------------------------------ linked towers
    private static String dimension(Level level) {
        return level.dimension().location().toString();
    }

    /** Linked towers of this remote in this dimension (a remote taken to another dimension starts empty). */
    public static List<BlockPos> read(ItemStack stack, Level level) {
        List<BlockPos> out = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TOWERS) || !dimension(level).equals(tag.getString(DIM))) return out;
        ListTag list = tag.getList(TOWERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) out.add(NbtUtils.readBlockPos(list.getCompound(i)));
        return out;
    }

    /** Group (1 = A ... 4 = D) of each linked motor, kept with its position. */
    public static java.util.Map<BlockPos, Integer> groups(ItemStack stack) {
        java.util.Map<BlockPos, Integer> out = new java.util.HashMap<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TOWERS)) return out;
        ListTag list = tag.getList(TOWERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            out.put(NbtUtils.readBlockPos(c), Math.max(1, Math.min(GROUPS, c.contains("G") ? c.getInt("G") : 1)));
        }
        return out;
    }

    /** Groups of motors per remote: A ... M (1.1.0, before: A ... D). */
    public static final int GROUPS = 13;

    public static int groupOf(ItemStack stack, BlockPos pos) {
        return groups(stack).getOrDefault(pos, 1);
    }

    /** Moves one linked motor to another group (1..4). */
    public static void setGroup(ItemStack stack, Level level, BlockPos pos, int group) {
        java.util.Map<BlockPos, Integer> g = groups(stack);
        if (!g.containsKey(pos)) return;
        g.put(pos, Math.max(1, Math.min(GROUPS, group)));
        writeWithGroups(stack, level, read(stack, level), g);
    }

    public static void write(ItemStack stack, Level level, List<BlockPos> towers) {
        writeWithGroups(stack, level, towers, groups(stack));
    }

    private static void writeWithGroups(ItemStack stack, Level level, List<BlockPos> towers, java.util.Map<BlockPos, Integer> g) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = new ListTag();
        for (BlockPos p : towers) {
            CompoundTag c = NbtUtils.writeBlockPos(p);
            c.putInt("G", g.getOrDefault(p, 1));
            list.add(c);
        }
        tag.put(TOWERS, list);
        tag.putString(DIM, dimension(level));
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TOWERS);
            tag.remove(DIM);
        }
    }
}
