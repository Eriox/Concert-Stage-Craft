package com.concertstage.client.audio;

import com.concertstage.StageContent;
import com.concertstage.audio.*;
import com.concertstage.audio.core.TrackPcm;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in development-only client test; src/smoke is absent from production builds. */
@Mod.EventBusSubscriber(modid="concertstage",value=Dist.CLIENT)
public final class CSCSmokeClient {
    private static boolean requested,ready;private static int tick;
    private static final BlockPos DESK=new BlockPos(4,128,6),REMOTE=new BlockPos(128,128,0),FLOWN=new BlockPos(12,132,0);
    private static TrackPcm decoder,failedMeter;
    private static float initialPeak, faultPeak;
    private static com.concertstage.audio.core.PlaylistPcm gaplessFeed;
    private static Object gaplessSound;
    private static final boolean LONG="1".equals(System.getenv("CSC_AUDIO_LONG_TEST"));
    private static int gaplessSession;
    private static void assertTrue(boolean v,String why){if(!v)throw new IllegalStateException("CSC_SMOKE_FAIL: "+why);}
    private static float assertOutputActive() {
        try {
            var field=ClientAudio.class.getDeclaredField("VOICES");field.setAccessible(true);
            var voices=(java.util.Map<?,?>)field.get(null);boolean active=false;float peak=0;
            for(Object value:voices.values()) {var v=(ClientAudio.Voice)value;peak=Math.max(peak,v.peak);active|=v.sound!=null&&Minecraft.getInstance().getSoundManager().isActive(v.sound);}
            assertTrue(active&&peak>.00001,"Minecraft sound channel consumes nonzero streamed PCM");return peak;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void shot(String name) {
        var mc=Minecraft.getInstance();
        try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path folder=mc.gameDirectory.toPath().resolve("csc-smoke");Files.createDirectories(folder);image.writeToFile(folder.resolve(name+".png"));
            LogUtils.getLogger().info("CSC_SMOKE screenshot {}",name);
        }catch(Exception e){throw new IllegalStateException("CSC_SMOKE_SCREENSHOT",e);}
    }
    private static int plannerPhase;private static long plannerDeadline;
    private static Object read(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private static void write(Object object,String name,Object value){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(Exception e){throw new RuntimeException(e);}}
    private static void invoke(Object object,String name){try{var m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(object);}catch(Exception e){throw new RuntimeException(e);}}
    private static void plannerTick(Minecraft mc,net.minecraft.server.MinecraftServer server){
        if(tick==1){AudioSettings.diagnosticsLog=true;server.execute(()->{
            var l=server.overworld();var reviewPlayer=server.getPlayerList().getPlayers().get(0);reviewPlayer.getAbilities().flying=true;reviewPlayer.onUpdateAbilities();for(int x=-24;x<=24;x++)for(int z=8;z<=88;z++)l.setBlock(new BlockPos(x,127,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            for(int x=-8;x<=8;x++)for(int y=128;y<=133;y++)l.setBlock(new BlockPos(x,y,24),Blocks.STONE.defaultBlockState(),3);
            var c=(AudioConsoleBlockEntity)l.getBlockEntity(DESK);for(int i=0;i<6;i++){c.setFader(i,.85f);if(c.muted(i))c.toggleMute(i);}
            var n=new net.minecraft.nbt.CompoundTag();n.putIntArray("Zone",new int[]{-20,8,20,80,128});n.putIntArray("Stage",new int[]{0,128,0});n.putLongArray("Mains",java.util.List.of(new BlockPos(0,128,0).asLong(),new BlockPos(4,128,0).asLong()));assertTrue(c.setPlannerPlan(n),"planner saved initial zone");
        });mc.options.renderDistance().set(8);}
        if(tick==80){mc.options.hideGui=false;mc.setScreen(new DelayPlannerScreen(new ConsoleScreen(DESK),DESK,new net.minecraft.nbt.CompoundTag()));invoke(mc.screen,"analyze");plannerDeadline=tick+1200;plannerPhase=1;}
        if(plannerPhase==1&&mc.screen instanceof DelayPlannerScreen screen){
            assertTrue(tick<plannerDeadline,"planner analysis completed within 60 seconds");
            var r=(com.concertstage.audio.core.DelayCoverage.Result)read(screen,"result");if(r!=null&&!(boolean)read(screen,"stale")){
                assertTrue(!r.towers().isEmpty(),"solver proposes towers from real terrain");
                assertTrue(java.util.Arrays.stream(r.grid().ground()).anyMatch(Double::isFinite),"loaded world ground sampled");
                for(var t:r.towers())assertTrue(Double.isFinite(t.delayMs())&&t.height()>=3,"finite proposed delays and heights");
                plannerPhase=10;plannerDeadline=tick+20;
            }
        }else if(plannerPhase==10&&tick>=plannerDeadline&&mc.screen instanceof DelayPlannerScreen screen){
            shot("08_planner_coverage");write(screen,"page",3);screen.resize(mc,screen.width,screen.height);write(screen,"worldGhost",true);DelayPlannerOverlay.heatmap(true);invoke(screen,"publishGhost");
            int left=(screen.width-590)/2,top=(screen.height-330)/2;boolean edited=false;
            for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.EditBox field&&field.getX()==left+338&&field.getY()==top+229){field.setValue("175.25");edited=true;}
            assertTrue(edited,"manual delay field exists and invokes its responder");
            screen.mouseClicked(left+440,top+290,0);screen.mouseReleased(left+440,top+290,0);plannerPhase=2;plannerDeadline=tick+100;
        }else if(plannerPhase==2&&tick>=plannerDeadline&&mc.screen instanceof DelayPlannerScreen screen){
            var c=RemoteConsoles.get(DESK);var list=(java.util.List<com.concertstage.audio.core.DelayCoverage.Tower>)read(screen,"draft");int bus=list.get(0).group();assertTrue(Math.abs(c.groupDelay(bus)-175.25)<.01,"server acknowledged actual bus delay packet");assertTrue(c.groupLabel(bus).equals("Delay Tower 1"),"server acknowledged bus name");
            assertTrue(!c.plannerPlan().getList("Towers",10).isEmpty(),"planner placements synchronized from server");shot("09_planner_configuration");screen.onClose();mc.setScreen(null);mc.options.hideGui=true;
            server.execute(()->server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),22,132,34,55,0));plannerPhase=3;plannerDeadline=tick+60;
        }else if(plannerPhase==3&&tick>=plannerDeadline){shot("10_planner_world_ghost");server.execute(()->server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),4,128,8.5,180,3));plannerPhase=4;plannerDeadline=tick+45;
        }else if(plannerPhase==4&&tick>=plannerDeadline){shot("11_relay_live_screens");assertOutputActive();AudioSettings.diagnosticsLog=false;AudioSettings.save();LogUtils.getLogger().info("CSC_PLANNER_SMOKE PASS: real terrain sampling, proposals, bus DSP settings packet, persistent plan, world ghost and Relay screens");mc.stop();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("csc.smoke"))return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(mc.level==null||mc.player==null||server==null)return;
        if(!requested) {
            requested=true;
            server.execute(()->{
                var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(60000,0,false,false);
                for(int x=-5;x<17;x++)for(int z=-5;z<17;z++)for(int y=140;y>=128;y--)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                for(int x=-5;x<17;x++)for(int z=-5;z<17;z++)level.setBlock(new BlockPos(x,127,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                level.setBlock(DESK,StageContent.AUDIO_CONSOLE.defaultBlockState().setValue(AudioConsoleBlock.FACING,net.minecraft.core.Direction.SOUTH),3);
                var c=(AudioConsoleBlockEntity)level.getBlockEntity(DESK);c.setMaster(.95f);c.setFader(0,.85f);c.setFader(1,.75f);c.setFader(2,.9f);c.setFader(4,.4f);c.toggleMute(4);
                String[] models={"csc_orvane_lx12","csc_orvane_lx8","csc_orvane_sb28","csc_orvane_lx12"};
                BlockPos[] pos={new BlockPos(0,128,0),new BlockPos(4,128,0),new BlockPos(8,128,0),FLOWN};
                for(int i=0;i<4;i++) {
                    level.setBlock(pos[i],StageContent.SPEAKER.defaultBlockState().setValue(SpeakerBlock.HANGING,i==3),3);
                    var s=(SpeakerBlockEntity)level.getBlockEntity(pos[i]);
                    if(i==1){s.initFrame("csc_orvane_ground_frame",0);assertTrue(s.add(models[i]),"ground support module");}
                    else if(i==3){s.initFrame("csc_orvane_flying_frame",0);for(int n=0;n<3;n++)assertTrue(s.add(models[i]),"flying module "+n);s.applyAngles(0,0,new float[]{0,3,5});}
                    else s.init(models[i],0,false);
                    s.link(DESK,i==2?2:i==1?1:0);c.link(pos[i]);
                }
                for(int x=10;x<=14;x++)level.setBlock(new BlockPos(x,133,0),StageContent.TRUSS_H1_L1.defaultBlockState(),3);
                String sha=System.getenv("CSC_SMOKE_SHA");long size=Long.parseLong(System.getenv("CSC_SMOKE_SIZE"));
                c.addTrack(new AudioConsoleBlockEntity.Track(sha,"CSC pipeline WAV test","wav",size,180000,"CSC smoke"));c.play();
                var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StageContent.ARRAY_TOOL));p.teleportTo(level,6,129,13,180,12);
                mc.execute(()->{mc.options.hideGui=true;ready=true;LogUtils.getLogger().info("CSC_SMOKE scene ready");});
            });
        }
        if(!ready)return;tick++;if("1".equals(System.getenv("CSC_PLANNER_TEST"))){plannerTick(mc,server);return;}if(tick==1)AudioSettings.diagnosticsLog=true;
        if(tick==80){assertTrue(ClientAudio.activeVoices()>0,"audio stream started");shot("01_world_overview");}
        if(tick==100){mc.options.hideGui=false;assertTrue(mc.level.getBlockEntity(FLOWN) instanceof SpeakerBlockEntity,"client received flying BE");mc.setScreen(new ArrayScreen(FLOWN,(SpeakerBlockEntity)mc.level.getBlockEntity(FLOWN)));}
        if(tick==120)shot("02_array_tool");
        if(tick==130&&mc.screen instanceof ArrayScreen s) {
            int left=(s.width-470)/2,top=(s.height-262)/2;
            s.mouseClicked(left+350,top+46+24+3,0);
            s.mouseClicked(left+390,top+34+110+14,0);s.mouseDragged(left+425,top+34+110+14,0,35,0);s.mouseReleased(left+425,top+34+110+14,0);
        }
        if(tick==150)shot("03_array_tool_edited");
        if(tick==160&&mc.screen instanceof ArrayScreen s){int left=(s.width-470)/2,top=(s.height-262)/2;s.mouseClicked(left+440,top+249,0);s.mouseReleased(left+440,top+249,0);}
        if(tick==180){mc.options.hideGui=true;server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(server.overworld(),2.4,128,4.2,155,17);});}
        if(tick==210){mc.options.hideGui=true;shot("04_orvane_closeup");}
        if(tick==230){mc.options.hideGui=false;mc.setScreen(new ConsoleScreen(DESK));}
        if(tick==250)shot("05_console_screen");
        if(tick==265){assertTrue(ClientAudio.inactiveSourceEvents()==0,"no involuntary source loss during world/render/array UI test");LogUtils.getLogger().info("CSC_SMOKE no involuntary source loss with buffered stream");}
        if(tick==270){mc.setScreen(null);mc.options.hideGui=true;server.execute(()->{
            var level=server.overworld();com.concertstage.audio.net.AudioContinuity.keep(level,REMOTE);level.getChunkAt(REMOTE);
            var old=(AudioConsoleBlockEntity)level.getBlockEntity(DESK);var tag=old.getUpdateTag();
            level.setBlock(REMOTE,StageContent.AUDIO_CONSOLE.defaultBlockState(),3);var remote=(AudioConsoleBlockEntity)level.getBlockEntity(REMOTE);remote.load(tag);old.stop();
            for(BlockPos p:remote.speakers())if(level.getBlockEntity(p) instanceof SpeakerBlockEntity s)s.link(REMOTE,s.group());
        });}
        if(tick==340){
            var pb=ClientAudio.playback(REMOTE);assertTrue(mc.level.getBlockEntity(REMOTE)==null,"remote console is outside client chunks");
            assertTrue(pb!=null&&pb.playing&&pb.pcm!=null&&pb.pcm.ready(),"snapshot starts real remote PCM playback");
            assertTrue(pb.vuL>.001f||pb.vuR>.001f,"remote playback has nonzero decoded signal");initialPeak=assertOutputActive();decoder=pb.pcm;
            LogUtils.getLogger().info("CSC_SMOKE remote console active with client chunk absent, meters {}/{}",pb.vuL,pb.vuR);
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(server.overworld(),10,128,6,210,9);});
        }
        if(tick==350)server.execute(()->((AudioConsoleBlockEntity)server.overworld().getBlockEntity(REMOTE)).setMaster(.25f));
        if(tick==365)faultPeak=assertOutputActive();
        if(tick==370){var pb=ClientAudio.playback(REMOTE);failedMeter=new TrackPcm(mc.gameDirectory.toPath().resolve("absent-meter-test.wav"),"wav",0);pb.pcm=failedMeter;}
        if(tick==390){assertTrue(failedMeter.failed(),"injected meter decoder fails");assertTrue(assertOutputActive()>Math.max(.0001f,faultPeak*.2f),"failed meter does not silence healthy continuous audio");ClientAudio.playback(REMOTE).pcm=decoder;failedMeter.close();LogUtils.getLogger().info("CSC_SMOKE continuous playback survives failed meter decoder");}
        if(tick==400){var pb=ClientAudio.playback(REMOTE);assertTrue(pb!=null&&pb.pcm==decoder&&pb.playing,"movement keeps identical decoder");float reducedPeak=assertOutputActive();assertTrue(reducedPeak<initialPeak*.65f,"master fader reduces actual streamed PCM with multiple arrays");LogUtils.getLogger().info("CSC_SMOKE master gain verified: {} -> {}",initialPeak,reducedPeak);shot("06_remote_console_audio");}
        if(tick==410){
            var s=(SpeakerBlockEntity)mc.level.getBlockEntity(FLOWN);var axis=s.acousticAxis();double previous=Double.POSITIVE_INFINITY;
            for(double distance:new double[]{2,5,10,25,50,100,150}){double gain=ClientAudio.arrayGain(s,axis[0],axis[1],axis[0].add(axis[1].normalize().scale(distance)));assertTrue(Double.isFinite(gain)&&gain<=previous+1e-9,"world attenuation monotone at "+distance);previous=gain;LogUtils.getLogger().info("CSC_SMOKE distance {} gain {}",distance,gain);}
        }
        if(tick==420)server.execute(()->((AudioConsoleBlockEntity)server.overworld().getBlockEntity(REMOTE)).pause());
        if(tick==430){mc.setScreen(new AudioSettingsScreen(null));assertTrue(mc.screen.getTitle().getString().equals("Concert Stage - Sound"),"Sound hub title");}
        if(tick>=430&&tick<=460&&(tick-430)%5==0&&mc.screen instanceof AudioSettingsScreen screen){
            int tab=(tick-430)/5,left=(screen.width-380)/2,top=(screen.height-260)/2;
            screen.mouseClicked(left+14+tab*51+22,top+49,0);screen.mouseReleased(left+14+tab*51+22,top+49,0);
        }
        if(tick>=432&&tick<=462&&(tick-432)%5==0&&mc.screen instanceof AudioSettingsScreen screen){
            int tab=(tick-432)/5;shot("07_sound_tab_"+tab);
            if(tab==6){screen.onClose();LogUtils.getLogger().info("CSC_SMOKE Sound hub seven tabs rendered");}
        }
        if(tick==460){var pb=ClientAudio.playback(REMOTE);assertTrue(pb==null||!pb.playing,"remote pause propagated");server.execute(()->((AudioConsoleBlockEntity)server.overworld().getBlockEntity(REMOTE)).play());}
        if(tick==510)server.execute(()->{var c=(AudioConsoleBlockEntity)server.overworld().getBlockEntity(REMOTE);c.setRepeatMode(AudioConsoleBlockEntity.REPEAT_ONE);c.seek(177000);});
        if(tick==540){
            var pb=ClientAudio.playback(REMOTE);gaplessFeed=pb.continuous;gaplessSession=pb.session;
            try {var f=ClientAudio.class.getDeclaredField("VOICES");f.setAccessible(true);gaplessSound=((ClientAudio.Voice)((java.util.Map<?,?>)f.get(null)).get(REMOTE)).sound;}catch(Exception ex){throw new RuntimeException(ex);}
            assertTrue(gaplessFeed!=null,"continuous feed started");
        }
        if(tick==620){
            var pb=ClientAudio.playback(REMOTE);assertTrue(pb.continuous==gaplessFeed&&pb.session==gaplessSession,"natural Repeat One keeps exact PCM feed/session");
            try {var f=ClientAudio.class.getDeclaredField("VOICES");f.setAccessible(true);assertTrue(((ClientAudio.Voice)((java.util.Map<?,?>)f.get(null)).get(REMOTE)).sound==gaplessSound,"same Minecraft sound source across EOF");}catch(Exception ex){throw new RuntimeException(ex);}
            assertOutputActive();LogUtils.getLogger().info("CSC_SMOKE gapless Repeat One keeps feed, session and OpenAL sound across EOF");
        }
        if(LONG&&tick==4400){
            assertTrue(ClientAudio.playback(REMOTE).continuous==gaplessFeed,"WAV feed stable for over three minutes");assertOutputActive();
            LogUtils.getLogger().info("CSC_SMOKE LONG WAV PASS: over three minutes continuous playback");
            server.execute(()->{var c=(AudioConsoleBlockEntity)server.overworld().getBlockEntity(REMOTE);c.addTrack(new AudioConsoleBlockEntity.Track(System.getenv("CSC_SMOKE_MP3_SHA"),"MP3 long test","mp3",Long.parseLong(System.getenv("CSC_SMOKE_MP3_SIZE")),180000,"CSC smoke"));c.select(1);});
        }
        if(LONG&&tick==4460){gaplessFeed=ClientAudio.playback(REMOTE).continuous;assertTrue(gaplessFeed!=null,"MP3 feed starts");}
        if(LONG&&tick==8200){assertTrue(ClientAudio.playback(REMOTE).continuous==gaplessFeed,"MP3 feed stable for over three minutes");assertOutputActive();assertTrue(com.concertstage.audio.core.PlaylistPcm.STARVATIONS.get()==0,"no long-test PCM starvation");LogUtils.getLogger().info("CSC_SMOKE LONG MP3 PASS: over three minutes, no starvation");}
        if(tick==(LONG?8220:640)){var pb=ClientAudio.playback(REMOTE);assertTrue(pb!=null&&pb.playing&&pb.pcm!=null,"remote resume propagated");AudioSettings.diagnosticsLog=false;AudioSettings.save();LogUtils.getLogger().info("CSC_SMOKE PASS: world rendering, array controls, console UI, PCM, remote snapshots, stable movement reader, pause/resume");mc.stop();}
    }
}
