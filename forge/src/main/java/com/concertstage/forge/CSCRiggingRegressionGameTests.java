package com.concertstage.forge;

import com.concertstage.StageContent;
import com.concertstage.block.HoistBlock;
import com.concertstage.block.MultiPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("concertstage")
@PrefixGameTestTemplate(false)
public final class CSCRiggingRegressionGameTests {
    @GameTest(template="audio_empty", timeoutTicks=100)
    public static void hoist_orientation_follows_beam(GameTestHelper h) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var support = h.absolutePos(new BlockPos(5, 5, 5));
        var hoist = (HoistBlock) StageContent.CHAIN_HOIST;
        var stack = new ItemStack(hoist);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                h.getLevel().setBlock(support, StageContent.TRUSS_X30D_L1.defaultBlockState()
                    .setValue(MultiPartBlock.FACING, facing), 3);
                for (int yaw = 0; yaw < 360; yaw += 90) {
                    player.setYRot(yaw);
                    for (boolean sneak : new boolean[] {false, true}) {
                        player.setShiftKeyDown(sneak);
                        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, -.5, 0), Direction.DOWN, support, false);
                        var ctx = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
                        var state = hoist.getStateForPlacement(ctx);
                        h.assertTrue(state != null, "hoist accepted under 1 m TriTruss");
                        // 1.0.2 behaviour: the hoist runs along its beam, turned from the player's view; sneak = the other way round
                        boolean beamAlongZ = ((facing.get2DDataValue() + 2) % 4) % 2 == 1;
                        Direction look = player.getDirection();
                        Direction expected = (((look.get2DDataValue() + 2) % 4) % 2 == 1) != beamAlongZ ? look.getClockWise() : look;
                        if (sneak) expected = expected.getOpposite();
                        h.assertTrue(state.getValue(MultiPartBlock.FACING) == expected, "hoist runs along its beam (1.0.2 placement)");
                    }
                }
            }
        } finally { player.setShiftKeyDown(false); }
        h.succeed();
    }
    @GameTest(template="audio_empty", timeoutTicks=100)
    public static void hoist_upper_hook_inside_every_truss(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3, 4, 3));
        for (var block : new net.minecraft.world.level.block.Block[] {StageContent.TRUSS_X30D_L1, StageContent.TRUSS_X30D_L1_NOIR}) {
            h.getLevel().setBlock(pos.above(), block.defaultBlockState(), 3);
            h.getLevel().setBlock(pos, StageContent.CHAIN_HOIST.defaultBlockState(), 3);
            float rise = HoistBlock.hookRise(h.getLevel(), pos, h.getLevel().getBlockState(pos));
            h.assertTrue(Math.abs(rise - (16f + 4.45f - HoistBlock.HOOK_BIGHT)) < 1e-3f, "hook rests on the lower bar of the TriTruss");
            h.assertTrue(rise > 0f, "upper hook goes up into the truss");
            h.getLevel().removeBlock(pos, false);
        }
        h.assertTrue(HoistBlock.catchBarTop("truss_h1_l2_noir") == 2.25f && HoistBlock.catchBarTop("truss_ladder_l2") == 4.45f
            && HoistBlock.catchBarTop("truss_h1_l1_petit") == 4.45f && HoistBlock.catchBarTop("truss_h2_l2_x30d") == 2.25f, "table of bars");
        h.succeed();
    }
    @GameTest(template="audio_empty", timeoutTicks=100)
    public static void tritruss_one_metre_size_and_hoist_support(GameTestHelper h) {
        for (var block : new net.minecraft.world.level.block.Block[] {StageContent.TRUSS_X30D_L1, StageContent.TRUSS_X30D_L1_NOIR}) {
            var truss = (MultiPartBlock) block;
            h.assertTrue(truss.sizeX() == 1 && truss.sizeY() == 1, "1 m truss uses one cell");
            h.assertTrue(HoistBlock.isBeam(truss.defaultBlockState()), "both finishes accept a hoist");
        }
        h.succeed();
    }
    @GameTest(template="audio_empty", timeoutTicks=160)
    public static void tritruss_one_metre_aluminium_tower_round_trip(GameTestHelper h) {
        oneMetreTowerRoundTrip(h, false);
    }

    @GameTest(template="audio_empty", timeoutTicks=160)
    public static void tritruss_one_metre_black_tower_round_trip(GameTestHelper h) {
        oneMetreTowerRoundTrip(h, true);
    }

    /** Exercise actual capture, moving entity, arrival and recapture, including mixed lengths and an attachment. */
    private static void oneMetreTowerRoundTrip(GameTestHelper h, boolean black) {
        var level = h.getLevel();
        var tower = StageContent.TOWERS.get(black ? 1 : 0);
        var base = h.absolutePos(new BlockPos(3, 2, 3));
        var state = tower.defaultBlockState().setValue(MultiPartBlock.FACING, Direction.NORTH);
        for (int cell = 0; cell < tower.towerHeight(); cell++)
            level.setBlock(base.above(cell), state.setValue(MultiPartBlock.PART, cell), 3);
        var controller = (com.concertstage.block.TowerBlockEntity) level.getBlockEntity(base);
        h.assertTrue(controller != null, "tower controller created");
        var first = base.above().east();
        var shortBlock = black ? StageContent.TRUSS_X30D_L1_NOIR : StageContent.TRUSS_X30D_L1;
        var otherBlock = black ? StageContent.TRUSS_X30D_L1 : StageContent.TRUSS_X30D_L1_NOIR;
        var longBlock = black ? StageContent.TRUSS_X30D_L2_NOIR : StageContent.TRUSS_X30D_L2;
        var positions = java.util.List.of(first, first.east(), first.east(2), first.east(3), first.above());
        var states = java.util.List.of(
            shortBlock.defaultBlockState(), otherBlock.defaultBlockState(),
            longBlock.defaultBlockState().setValue(MultiPartBlock.PART, 0),
            longBlock.defaultBlockState().setValue(MultiPartBlock.PART, 1),
            net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState());
        for (int n = 0; n < positions.size(); n++) level.setBlock(positions.get(n), states.get(n), 3);
        var towers = java.util.List.of(controller);
        var up = com.concertstage.lift.LiftControl.start(level, towers, 1, null);
        h.assertTrue(up == com.concertstage.lift.LiftControl.Result.STARTED, "tower starts raising mixed TriTruss chain");
        h.assertTrue(level.getBlockState(first).isAir(), "one metre module captured immediately");
        h.runAfterDelay(30, () -> {
            h.assertTrue(!controller.isMoving() && controller.restUnits() == 20, "tower arrives one block higher");
            for (int n = 0; n < positions.size(); n++)
                h.assertTrue(level.getBlockState(positions.get(n).above()).equals(states.get(n)), "raised module or attachment retained its state: " + n);
            var down = com.concertstage.lift.LiftControl.start(level, towers, 0, null);
            h.assertTrue(down == com.concertstage.lift.LiftControl.Result.STARTED, "tower recaptures chain for descent");
            h.runAfterDelay(30, () -> {
                h.assertTrue(!controller.isMoving() && controller.restUnits() == 0, "tower returns to initial height");
                for (int n = 0; n < positions.size(); n++)
                    h.assertTrue(level.getBlockState(positions.get(n)).equals(states.get(n)), "returned module or attachment retained its state: " + n);
                h.succeed();
            });
        });
    }
}
