package com.concertstage.forge;

import com.concertstage.StageContent;
import com.concertstage.audio.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native AUREX regression coverage, using the same blocks/collision owners as legacy arrays. */
@GameTestHolder("concertstage")
@PrefixGameTestTemplate(false)
public final class CSCAurexGameTests {
    private static SpeakerBlockEntity speaker(GameTestHelper h, int y, boolean flying) {
        var p=h.absolutePos(new BlockPos(8,y,8));
        h.getLevel().setBlock(p,StageContent.SPEAKER.defaultBlockState().setValue(SpeakerBlock.HANGING,flying),3);
        return (SpeakerBlockEntity)h.getLevel().getBlockEntity(p);
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void main_envelope_and_single_cabinet(GameTestHelper h) {
        var m=SpeakerModels.get("csc_aurex_m12");var ref=SpeakerModels.get("csc_orvane_lx8");
        h.assertTrue(m.width()==ref.width()&&m.height()==ref.height()&&m.depth()==ref.depth(),"M12 uses the measured LX8 envelope");
        var s=speaker(h,3,false);s.init(m.id(),90,false);
        h.assertTrue(s.canFit(),"M12 ground placement with yaw");
        h.assertFalse(s.add(m.id()),"M12 is not a line array element");
        h.assertFalse(s.add("csc_orvane_lx8"),"legacy cabinets cannot connect to a main");
        var tag=s.saveWithoutMetadata();var copy=new SpeakerBlockEntity(s.getBlockPos(),s.getBlockState());copy.load(tag);
        h.assertTrue(copy.cabinets().size()==1&&copy.yaw()==90&&copy.firstModel().id().equals(m.id()),"M12 persistence");
        h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void flying_sub_frame_stack_and_save(GameTestHelper h) {
        var s=speaker(h,9,true);s.initFrame("csc_aurex_flying_frame",0);
        h.assertTrue(s.add("csc_aurex_s218_f")&&s.add("csc_aurex_s218_f"),"two suspended subs fit dedicated frame");
        h.assertTrue(s.group()==AudioGroups.SUBS,"first frame cabinet selects subwoofer bus");
        h.assertFalse(s.add("csc_aurex_m12"),"main rejected by sub frame");
        h.assertFalse(s.add("csc_orvane_sb28"),"foreign sub rigging rejected");
        var p=s.poses();h.assertTrue(Math.abs(p.get(0).y()-p.get(1).y()-1.15)<.00001,"sub stack continuous under frame");
        h.assertTrue(!s.cellPositions().isEmpty(),"wide flown assembly owns collision cells");
        var copy=new SpeakerBlockEntity(s.getBlockPos().below(2),s.getBlockState());copy.load(s.saveWithoutMetadata());
        h.assertTrue(copy.support().equals("csc_aurex_flying_frame")&&copy.cabinets().size()==2&&copy.hanging(),"assembly survives NBT relocation");
        h.assertTrue(copy.poses().equals(s.poses()),"local geometry unchanged by relocation");
        s.clearCells();h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void monitor_floor_positions_and_acoustics(GameTestHelper h) {
        var s=speaker(h,3,false);s.init("csc_aurex_m15_w",0,false);
        h.assertTrue(s.group()==AudioGroups.MONITOR_1&&!s.hanging(),"monitor chooses monitor bus and floor");
        h.assertFalse(s.add("csc_aurex_m15_w"),"wedges cannot stack as a line array");
        h.assertTrue(s.tryApplyAngles(45,-15,new float[0]),"second floor position accepted");
        var pose=s.poses().get(0);h.assertTrue(pose.y()>0&&pose.pitch()==-15,"floor-pivot correction applies to render and collision");
        h.assertTrue(s.acousticAxis()[1].y>.7,"monitor acoustic axis follows inclined baffle");
        s.link(h.absolutePos(new BlockPos(2,2,2)),AudioGroups.MONITOR_3);
        var copy=new SpeakerBlockEntity(s.getBlockPos(),s.getBlockState());copy.load(s.saveWithoutMetadata());
        h.assertTrue(copy.group()==AudioGroups.MONITOR_3&&copy.tilt()==-15&&copy.yaw()==45,"monitor circuit and angle persist");
        h.assertTrue(s.tryApplyAngles(-45,0,new float[0])&&s.poses().get(0).y()==0,"first position restores floor origin");h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void monitor_buses_legacy_migration(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(8,2,8));h.getLevel().setBlock(p,StageContent.AUDIO_CONSOLE.defaultBlockState(),3);
        var c=(AudioConsoleBlockEntity)h.getLevel().getBlockEntity(p);
        var old=c.saveWithoutMetadata();for(int i=6;i<9;i++)old.remove("Fader"+i);old.putFloat("Fader2",.37f);c.load(old);
        h.assertTrue(Math.abs(c.fader(2)-.37f)<.00001&&Math.abs(c.fader(8)-.8f)<.00001,"old six-bus snapshots keep values and initialize monitor buses");
        c.setFader(6,.23f);c.setFader(7,.54f);c.setFader(8,.91f);c.toggleMute(7);c.setGroupDelay(8,12.5f);
        var copy=new AudioConsoleBlockEntity(p,c.getBlockState());copy.load(c.saveWithoutMetadata());
        h.assertTrue(copy.fader(6)==.23f&&copy.fader(7)==.54f&&copy.fader(8)==.91f&&copy.muted(7)&&copy.groupDelay(8)==12.5f,"three independent monitor bus states persist");
        h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=180)
    public static void hoist_moves_flown_sub_and_collision_cells(GameTestHelper h) {
        var level=h.getLevel();var hp=h.absolutePos(new BlockPos(8,12,8));
        level.setBlock(hp.above(),StageContent.TRUSS_H1_L2_PETIT.defaultBlockState(),3);
        level.setBlock(hp,StageContent.CHAIN_HOIST.defaultBlockState(),3);
        var hoist=(com.concertstage.block.TowerBlockEntity)level.getBlockEntity(hp);
        var sp=hp.below();level.setBlock(sp,StageContent.SPEAKER.defaultBlockState().setValue(SpeakerBlock.HANGING,true),3);
        var sub=(SpeakerBlockEntity)level.getBlockEntity(sp);sub.initFrame("csc_aurex_flying_frame",0);
        h.assertTrue(sub.add("csc_aurex_s218_f"),"sub attached under hoist");
        var result=com.concertstage.lift.LiftControl.start(level,java.util.List.of(hoist),com.concertstage.block.HoistBlock.RANGE-2,null);
        h.assertTrue(result==com.concertstage.lift.LiftControl.Result.STARTED,"hoist accepts AUREX assembly: "+result);
        h.runAtTickTime(75,()->{
            h.assertTrue(level.getBlockEntity(sp.below(2)) instanceof SpeakerBlockEntity,"sub head moved two blocks with hoist");
            var moved=(SpeakerBlockEntity)level.getBlockEntity(sp.below(2));
            h.assertTrue(moved.support().equals("csc_aurex_flying_frame")&&moved.cabinets().size()==1,"frame and cabinet retained on arrival");
            h.assertTrue(!moved.cellPositions().isEmpty(),"collision cells rebuilt at destination");
            h.assertTrue(!(level.getBlockEntity(sp) instanceof SpeakerBlockEntity),"no ghost speaker at origin");
            var up=com.concertstage.lift.LiftControl.start(level,java.util.List.of(hoist),com.concertstage.block.HoistBlock.RANGE,null);
            h.assertTrue(up==com.concertstage.lift.LiftControl.Result.STARTED,"hoist raises same sub again");
        });
        h.runAtTickTime(150,()->{h.assertTrue(level.getBlockEntity(sp) instanceof SpeakerBlockEntity,"sub returned to original position");h.succeed();});
    }
}
