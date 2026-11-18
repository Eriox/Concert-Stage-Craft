package com.concertstage.forge;

import com.concertstage.StageContent;
import com.concertstage.light.FixtureBlock;
import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.LightServer;
import com.concertstage.light.LumicoreBlockEntity;
import com.concertstage.light.core.Show;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Lighting (1.2.0) in a real world: patch, persistence of the desk and the fixture, unpatch when the fixture is broken. */
@GameTestHolder("concertstage")
@PrefixGameTestTemplate(false)
public final class CSCLightingGameTests {
    private static LumicoreBlockEntity desk(GameTestHelper h) {
        BlockPos p = h.absolutePos(new BlockPos(8, 2, 3));
        h.getLevel().setBlock(p, StageContent.LUMICORE.defaultBlockState(), 3);
        return (LumicoreBlockEntity) h.getLevel().getBlockEntity(p);
    }

    private static FixtureBlockEntity fixture(GameTestHelper h, net.minecraft.world.level.block.Block block, int x, boolean hung) {
        BlockPos p = h.absolutePos(new BlockPos(x, hung ? 9 : 2, 12));
        h.getLevel().setBlock(p, block.defaultBlockState().setValue(FixtureBlock.FACING, Direction.NORTH).setValue(FixtureBlock.HUNG, hung), 3);
        return (FixtureBlockEntity) h.getLevel().getBlockEntity(p);
    }

    @GameTest(template = "audio_empty", timeoutTicks = 100)
    public static void patch_save_and_unpatch(GameTestHelper h) {
        LumicoreBlockEntity d = desk(h);
        FixtureBlockEntity a = fixture(h, StageContent.VORTEX_X1, 4, false);
        FixtureBlockEntity b = fixture(h, StageContent.LUMORA_W19, 6, true);
        FixtureBlockEntity c = fixture(h, StageContent.PULSESTRIKE_M, 8, false);
        h.assertTrue(LightServer.patch(h.getLevel(), d, a) && LightServer.patch(h.getLevel(), d, b) && LightServer.patch(h.getLevel(), d, c), "three fixtures patched");
        h.assertFalse(LightServer.patch(h.getLevel(), d, a), "a fixture is patched once");
        h.assertTrue(d.show.patch.size() == 3, "desk patch holds three fixtures");
        h.assertTrue(d.getBlockPos().equals(a.console()) && a.fid() != b.fid() && b.fid() != c.fid(), "fixtures know their desk and unique numbers");
        // persistence: desk show and fixture link survive a save / load
        var copy = new LumicoreBlockEntity(d.getBlockPos(), d.getBlockState());
        copy.load(d.saveWithoutMetadata());
        h.assertTrue(copy.show.patch.size() == 3, "desk show saved with its patch");
        var fcopy = new FixtureBlockEntity(b.getBlockPos(), b.getBlockState());
        fcopy.load(b.saveWithoutMetadata());
        h.assertTrue(b.fid() == fcopy.fid() && d.getBlockPos().equals(fcopy.console()), "fixture link saved");
        h.assertTrue(Show.fromBytes(d.show.toBytes()) != null, "show bytes read back");
        // breaking a fixture removes it from the desk
        int removed = c.fid();
        h.getLevel().removeBlock(c.getBlockPos(), false);
        h.assertTrue(d.show.fixture(removed) == null && d.show.patch.size() == 2, "broken fixture unpatched");
        h.succeed();
    }
}
