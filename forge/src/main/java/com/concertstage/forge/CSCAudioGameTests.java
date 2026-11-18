package com.concertstage.forge;
import com.concertstage.StageContent;
import com.concertstage.audio.*;
import com.concertstage.audio.net.AudioContinuity;
import com.concertstage.block.ring.RingTrussCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("concertstage")
@PrefixGameTestTemplate(false)
public final class CSCAudioGameTests {
    private static SpeakerBlockEntity speaker(GameTestHelper h,int y,boolean flying) {
        BlockPos p=h.absolutePos(new BlockPos(8,y,8));
        h.getLevel().setBlock(p,StageContent.SPEAKER.defaultBlockState().setValue(SpeakerBlock.HANGING,flying),3);
        return (SpeakerBlockEntity)h.getLevel().getBlockEntity(p);
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void planner_tool_selects_without_placing(GameTestHelper h) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var stack=new ItemStack(StageContent.DELAY_PLANNER);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var a=h.absolutePos(new BlockPos(3,1,3));var b=h.absolutePos(new BlockPos(10,1,10));
        for(var p:java.util.List.of(a,b)){h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);StageContent.DELAY_PLANNER.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false)));}
        h.assertTrue(stack.getOrCreateTag().getLong("PlannerA")==a.asLong()&&stack.getOrCreateTag().getLong("PlannerB")==b.asLong(),"tool stores two audience corners");
        h.assertTrue(h.getLevel().isEmptyBlock(a.above())&&h.getLevel().isEmptyBlock(b.above()),"selection does not place blocks");
        player.setShiftKeyDown(true);StageContent.DELAY_PLANNER.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(a).add(.5,1,.5),Direction.UP,a,false)));player.setShiftKeyDown(false);
        h.assertTrue(stack.getOrCreateTag().getLong("PlannerStage")==a.above().asLong(),"tool stores stage reference");h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void planner_metadata_and_group_delay_roundtrip(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(8,2,8));
        h.getLevel().setBlock(p,StageContent.AUDIO_CONSOLE.defaultBlockState(),3);
        var c=(AudioConsoleBlockEntity)h.getLevel().getBlockEntity(p);
        c.setGroupDelay(4,291.55f);c.setGroupLabel(4,"Delay North");
        var plan=new net.minecraft.nbt.CompoundTag();
        plan.putIntArray("Zone",new int[]{p.getX()-10,p.getZ(),p.getX()+10,p.getZ()+50,p.getY()});
        plan.putIntArray("Stage",new int[]{p.getX(),p.getY(),p.getZ()});
        plan.putString("UntrustedField","discard");
        h.assertTrue(c.setPlannerPlan(plan),"valid zone accepted");
        var copy=new AudioConsoleBlockEntity(p,c.getBlockState());copy.loadAudioSnapshot(h.getLevel(),c.getUpdateTag());
        h.assertTrue(Math.abs(copy.groupDelay(4)-291.55f)<.001&&copy.groupLabel(4).equals("Delay North"),"bus delay and alias survive snapshot");
        h.assertTrue(copy.plannerPlan().getIntArray("Zone").length==5&&!copy.plannerPlan().contains("UntrustedField"),"plan is persisted using whitelist");
        plan.putIntArray("Zone",new int[]{0,0,1000,1000,p.getY()});
        h.assertTrue(!c.setPlannerPlan(plan),"oversized zone rejected");
        var ids=new net.minecraft.nbt.CompoundTag();ids.putIntArray("Zone",new int[]{p.getX(),p.getZ(),p.getX()+2,p.getZ()+2,p.getY()});ids.putLongArray("Mains",new long[65]);
        h.assertTrue(!c.setPlannerPlan(ids),"oversized source list rejected");
        h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void flying_ports_and_compatibility(GameTestHelper h) {
        var s=speaker(h,8,true);s.initFrame("csc_orvane_flying_frame",0);
        h.assertTrue(s.add("csc_orvane_lx12"),"first Orvane cabinet fits frame");
        h.assertTrue(s.add("csc_orvane_lx12"),"second cabinet connects");
        h.assertFalse(s.add("csc_orvane_lx8"),"different widths must not connect");
        h.assertFalse(s.add("csc_veyra_a1"),"different families must not connect");
        s.applyAngles(90,7,new float[]{0,8});
        var a=s.poses().get(0);var b=s.poses().get(1);
        Vec3 hingeA=s.toWorld(a,0,0,0),hingeB=s.toWorld(b,0,b.model().height(),0);
        h.assertTrue(hingeA.distanceTo(hingeB)<1e-6,"front hinge stays connected after yaw/tilt/splay");
        h.assertTrue(s.bounds().getYsize()>1,"bounds include both cabinets and frame");h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void ground_frame_save_and_remove(GameTestHelper h) {
        var s=speaker(h,2,false);s.initFrame("csc_noryx_ground_frame",0);
        h.assertTrue(s.add("csc_noryx_a1")&&s.add("csc_noryx_a1"),"ground modules connect");
        h.assertTrue(Math.abs(s.poses().get(0).y()-.12)<1e-6,"cabinet sits on support height");
        s.applyAngles(-90,4,new float[]{0,2.5f});s.setGroup(3);s.setVolume(1.2f);
        var copy=new SpeakerBlockEntity(s.getBlockPos(),s.getBlockState());copy.load(s.saveWithoutMetadata());
        h.assertTrue(copy.support().equals(s.support())&&copy.cabinets().size()==2&&copy.yaw()==-90&&copy.group()==3,"save/load preserves array state");
        s.removeLast();s.removeLast();h.assertTrue(s.cabinets().isEmpty()&&s.supportModel()!=null,"last cabinet leaves editable empty support");h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void occupied_cells_reject_extension(GameTestHelper h) {
        var s=speaker(h,8,true);s.init("csc_orvane_lx12",0,true);
        h.getLevel().setBlock(s.getBlockPos().below(),Blocks.STONE.defaultBlockState(),3);
        h.assertFalse(s.add("csc_orvane_lx12"),"cabinet cannot grow through solid block");
        h.assertTrue(s.cabinets().size()==1,"failed extension keeps original state");h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void foreign_collision_owner_survives(GameTestHelper h) {
        var s=speaker(h,4,false);s.init("csc_orvane_lx12",0,false);
        BlockPos foreign=null;
        for(BlockPos p:s.cellPositions())if(h.getLevel().getBlockEntity(p) instanceof RingTrussCellBlockEntity cell) {
            foreign=p;cell.set(new float[]{0,0,0,1,1,1},s.getBlockPos().offset(7,0,0),false);break;
        }
        h.assertTrue(foreign!=null,"wide model creates collision cells");s.clearCells();
        h.assertTrue(h.getLevel().getBlockEntity(foreign) instanceof RingTrussCellBlockEntity,"foreign owner must survive cleanup");h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void all_six_support_items_place_and_connect(GameTestHelper h) {
        var player=h.makeMockPlayer();int i=0;
        for(var f:SpeakerFrames.ALL.values()) {
            BlockPos surface=h.absolutePos(new BlockPos(4+(i%3)*5,f.flying()?9:1,4+(i/3)*7));
            h.getLevel().setBlock(surface,Blocks.IRON_BLOCK.defaultBlockState(),3);
            var face=f.flying()?Direction.DOWN:Direction.UP;
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StageContent.frameItem(f.id())));
            var hit=new BlockHitResult(Vec3.atCenterOf(surface),face,surface,false);
            var result=StageContent.frameItem(f.id()).useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            h.assertTrue(result.consumesAction(),"support item places: "+f.id());
            BlockPos anchor=surface.relative(face);
            h.assertTrue(h.getLevel().getBlockEntity(anchor) instanceof SpeakerBlockEntity,"support anchor exists");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StageContent.speakerItem(f.model())));
            result=StageContent.speakerItem(f.model()).useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(anchor),Direction.UP,anchor,false)));
            h.assertTrue(result.consumesAction(),"cabinet attaches to placed support: "+f.id());i++;
        }
        h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void console_snapshot_codec(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(8,2,8));h.getLevel().setBlock(p,StageContent.AUDIO_CONSOLE.defaultBlockState(),3);
        var c=(AudioConsoleBlockEntity)h.getLevel().getBlockEntity(p);
        c.setMaster(.92f);c.setFader(2,.63f);c.toggleMute(4);c.setEq(0,4);c.setLabel("CSC TEST");
        var copy=new AudioConsoleBlockEntity(p,c.getBlockState());copy.loadAudioSnapshot(h.getLevel(),c.getUpdateTag());
        h.assertTrue(copy.master()==.92f&&copy.fader(2)==.63f&&copy.muted(4)&&copy.eq(0)==4&&copy.label().equals("CSC TEST"),"snapshot preserves controls");
        h.assertTrue(c.getBlockState().getCollisionShape(h.getLevel(),p).max(Direction.Axis.Y)>1.3,"console bridge has actual collision height");h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void remote_console_keeps_ticking(GameTestHelper h) {
        var level=h.getLevel();BlockPos far=h.absolutePos(new BlockPos(512,3,512));
        AudioContinuity.keep(level,far);level.getChunkAt(far);
        level.setBlock(far,StageContent.AUDIO_CONSOLE.defaultBlockState(),3);
        var c=(AudioConsoleBlockEntity)level.getBlockEntity(far);
        c.addTrack(new AudioConsoleBlockEntity.Track("0000000000000000000000000000000000000000","test","wav",44,100,"gametest"));
        c.play();int session=c.session();long start=c.startTick();
        // Await actual chunk ticking; a fixed 40-tick check races first-world chunk promotion.
        h.succeedWhen(()->{
            h.assertTrue(c.session()==session&&c.startTick()>start,"natural repeat outside player chunks: session "+c.session()+"/"+session+", start "+c.startTick()+"/"+start+", state "+c.state()+", now "+level.getGameTime());
            level.removeBlock(far,false);
        });
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void rotation_rejects_foreign_obstacle(GameTestHelper h) {
        var s=speaker(h,4,false);s.init("csc_orvane_lx12",0,false);
        h.getLevel().setBlock(s.getBlockPos().south(),Blocks.IRON_BARS.defaultBlockState(),3);
        h.assertTrue(s.canFit(),"original orientation clears narrow obstacle");
        h.assertFalse(s.tryApplyAngles(90,0,new float[]{0}),"rotation through neighbouring obstacle rejected");
        h.assertTrue(s.yaw()==0&&s.cabinets().size()==1,"failed rotation keeps previous state");h.succeed();
    }
    private static AudioConsoleBlockEntity transportConsole(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(8,2,8));
        h.getLevel().setBlock(p,StageContent.AUDIO_CONSOLE.defaultBlockState(),3);
        var c=(AudioConsoleBlockEntity)h.getLevel().getBlockEntity(p);
        c.addTrack(new AudioConsoleBlockEntity.Track("a".repeat(40),"A","wav",128,1023,"test"));
        c.addTrack(new AudioConsoleBlockEntity.Track("b".repeat(40),"B","mp3",128,1557,"test"));
        return c;
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void repeat_modes_save_migration_and_manual_next(GameTestHelper h) {
        var c=transportConsole(h);
        for(int mode=0;mode<3;mode++) {
            c.setRepeatMode(mode);var saved=c.saveWithoutMetadata();
            var copy=new AudioConsoleBlockEntity(c.getBlockPos(),c.getBlockState());copy.load(saved);
            h.assertTrue(copy.repeatMode()==mode,"Repeat mode survives save/load: "+mode);
        }
        var legacy=c.saveWithoutMetadata();legacy.remove("RepeatMode");legacy.putBoolean("Repeat",false);c.load(legacy);
        h.assertTrue(c.repeatMode()==AudioConsoleBlockEntity.REPEAT_OFF,"legacy false migrates Off");
        legacy.putBoolean("Repeat",true);c.load(legacy);
        h.assertTrue(c.repeatMode()==AudioConsoleBlockEntity.REPEAT_ALL,"legacy true migrates All");
        legacy.remove("Repeat");c.load(legacy);
        h.assertTrue(c.repeatMode()==AudioConsoleBlockEntity.REPEAT_ALL,"missing legacy flag retains old default All");
        c.setRepeatMode(AudioConsoleBlockEntity.REPEAT_ONE);c.select(0);c.next();
        h.assertTrue(c.current()==1,"manual Next escapes Repeat One");
        c.next();h.assertTrue(c.current()==0,"manual Next wraps when repeating");
        c.setRepeatMode(-99);h.assertTrue(c.repeatMode()==AudioConsoleBlockEntity.REPEAT_ALL,"invalid mode safely defaults All");
        c.setRepeatMode(0);c.toggleRepeat();h.assertTrue(c.repeatMode()==1,"cycle Off -> All");
        c.toggleRepeat();h.assertTrue(c.repeatMode()==2,"cycle All -> One");c.toggleRepeat();h.assertTrue(c.repeatMode()==0,"cycle One -> Off");
        h.succeed();
    }
    @GameTest(template="audio_empty",timeoutTicks=100)
    public static void natural_repeat_carries_subtick_time(GameTestHelper h) {
        var c=transportConsole(h);long now=h.getLevel().getGameTime();
        c.setRepeatMode(AudioConsoleBlockEntity.REPEAT_ONE);c.select(0);
        for(int loop=0;loop<5;loop++) {
            // Pretend 1050 ms have elapsed for a 1023 ms file: keep the 27 ms overshoot.
            var tag=c.saveWithoutMetadata();tag.putLong("Start",now-21);tag.putLong("StartRemainderMs",0);c.load(tag);
            AudioConsoleBlockEntity.serverTick(h.getLevel(),c.getBlockPos(),c.getBlockState(),c);
            h.assertTrue(c.current()==0&&c.state()==AudioConsoleBlockEntity.PLAYING,"Repeat One retains current track");
            h.assertTrue(c.positionMs(now)==27,"sub-tick overshoot preserved exactly");
        }
        c.setRepeatMode(AudioConsoleBlockEntity.REPEAT_ALL);c.select(0);
        var tag=c.saveWithoutMetadata();tag.putLong("Start",now-21);c.load(tag);
        AudioConsoleBlockEntity.serverTick(h.getLevel(),c.getBlockPos(),c.getBlockState(),c);
        h.assertTrue(c.current()==1&&c.positionMs(now)==27,"All advances without added 250 ms wait");
        c.setRepeatMode(AudioConsoleBlockEntity.REPEAT_OFF);c.select(1);
        tag=c.saveWithoutMetadata();tag.putLong("Start",now-32);c.load(tag);
        AudioConsoleBlockEntity.serverTick(h.getLevel(),c.getBlockPos(),c.getBlockState(),c);
        h.assertTrue(c.state()==AudioConsoleBlockEntity.STOPPED,"Off stops at final track");
        c.select(0);c.seek(123);h.assertTrue(c.positionMs(now)==123,"seek retains milliseconds");
        c.pause();c.play();h.assertTrue(c.positionMs(now)==123,"pause/resume retains milliseconds");h.succeed();
    }
}
