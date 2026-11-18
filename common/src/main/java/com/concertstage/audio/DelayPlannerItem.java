package com.concertstage.audio;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Server-owned audience/main selection. No action places blocks. */
public final class DelayPlannerItem extends Item {
  public DelayPlannerItem(Properties p) {
    super(p);
  }

  @Override
  public InteractionResult useOn(UseOnContext ctx) {
    var level = ctx.getLevel();
    var player = ctx.getPlayer();
    if (player == null) return InteractionResult.PASS;
    var tag = ctx.getItemInHand().getOrCreateTag();
    var pos = ctx.getClickedPos();
    if (level.getBlockEntity(pos) instanceof AudioConsoleBlockEntity) {
      tag.putLong("PlannerConsole", pos.asLong());
      if (level.isClientSide) com.concertstage.client.ClientHooks.openDelayPlanner(pos, tag.copy());
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
    if (!level.isClientSide) {
      var array = SpeakerItem.arrayAt(level, pos);
      if (array != null) {
        var ids = new ArrayList<Long>();
        for (long n : tag.getLongArray("PlannerMains")) ids.add(n);
        if (!ids.remove(array.asLong()) && ids.size() < 64) ids.add(array.asLong());
        tag.putLongArray("PlannerMains", ids);
        player.displayClientMessage(Component.translatable("message.concertstage.planner_mains", ids.size()), true);
      } else if (player.isShiftKeyDown()) {
        BlockPos stage = BlockPos.containing(ctx.getClickLocation().add(0,.01,0));
        tag.putLong("PlannerStage", stage.asLong());
        player.displayClientMessage(
            Component.translatable("message.concertstage.planner_stage", stage.toShortString()), true);
      } else {
        String key =
            !tag.contains("PlannerA") || tag.contains("PlannerB") ? "PlannerA" : "PlannerB";
        if (key.equals("PlannerA")) tag.remove("PlannerB");
        tag.putLong(key, pos.asLong());
        player.displayClientMessage(
            Component.translatable(
                key.equals("PlannerA") ? "message.concertstage.planner_audience_a" : "message.concertstage.planner_audience_b", pos.toShortString()),
            true);
      }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(
      Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
    var s = player.getItemInHand(hand);
    var n = s.getOrCreateTag();
    if (!n.contains("PlannerConsole")) return InteractionResultHolder.pass(s);
    if (level.isClientSide)
      com.concertstage.client.ClientHooks.openDelayPlanner(
          BlockPos.of(n.getLong("PlannerConsole")), n.copy());
    return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
    text.add(Component.translatable("tooltip.concertstage.delay_planner"));
  }
}
