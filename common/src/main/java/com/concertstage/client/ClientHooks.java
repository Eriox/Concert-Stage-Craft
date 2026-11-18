package com.concertstage.client;

import com.concertstage.block.CurtainOwners;
import com.concertstage.block.CurtainRailBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Client-only entry points. Only ever called when {@code level.isClientSide}, so a dedicated server never loads this class. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openTarpTool(BlockPos clicked) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        BlockPos tarp = com.concertstage.block.tarp.StageTarpRailBlock.findRail(level, clicked);
        if (tarp != null && level.getBlockEntity(tarp) instanceof com.concertstage.block.tarp.StageTarpRailBlockEntity t) {
            boolean regie = t.getBlockState().getBlock() instanceof com.concertstage.block.tarp.StageTarpRailBlock r && r.isRegie();
            TarpToolScreen screen = new TarpToolScreen(tarp, true, t.getLength(), t.getHeight(), t.getShift(), t.getBlockState(),
                regie ? "gui.concertstage.title_regie_tarp" : "gui.concertstage.title_stage_tarp");
            mc.setScreen(regie ? screen : screen.withSlant(t.isSlant()));
            return;
        }
        BlockPos target = CurtainOwners.find(level, clicked);
        if (target == null) return;
        BlockEntity be = level.getBlockEntity(target);
        if (be instanceof CurtainRailBlockEntity rail) {
            mc.setScreen(new TarpToolScreen(target, true, rail.getLength(), rail.getHeight(), rail.getShift(), rail.getBlockState()));
        }
    }

    public static void openRoof(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.block.roof.RoofTrussBlockEntity be)
            mc.setScreen(new RoofScreen(pos, be));
    }

    public static void openRegieRoof(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.block.regie.RegieRoofBlockEntity be)
            mc.setScreen(new RegieRoofScreen(pos, be));
    }

    public static void openRing(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.block.ring.RingTrussBlockEntity be)
            mc.setScreen(new RingScreen(pos, be));
    }

    /** CSC Array Tool screen of a speaker array. */
    public static void openDelayPlanner(BlockPos pos,net.minecraft.nbt.CompoundTag selection){net.minecraft.client.Minecraft.getInstance().setScreen(new com.concertstage.client.audio.DelayPlannerScreen(null,pos,selection));}

    public static void openArray(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.audio.SpeakerBlockEntity be && !be.cabinets().isEmpty())
            mc.setScreen(new com.concertstage.client.audio.ArrayScreen(pos, be));
    }

    /** CSC Audio Console screen. */
    public static void openConsole(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.audio.AudioConsoleBlockEntity)
            mc.setScreen(new com.concertstage.client.audio.ConsoleScreen(pos));
    }

    /** CSC LUMICORE X1 lighting desk (1.2.0). */
    /** Rev. 1.3.0-2: the screen of a CSC ART-NET NODE. */
    public static void openArtNetConsole(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.light.LumicoreBlockEntity)
            mc.setScreen(new com.concertstage.client.light.ArtNetConsoleScreen(pos));
    }

    /** Rev. 1.3.0-2: the CSC CONFIGURATION CARD in that hand. */
    public static void openConfigCard(net.minecraft.world.InteractionHand hand) {
        Minecraft.getInstance().setScreen(new com.concertstage.client.light.ConfigCardScreen(hand));
    }

    /** Rev. 1.3.0-2: the patch screen of the fixture at {@code pos}. */
    public static void openFixturePatch(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.light.FixtureBlockEntity)
            mc.setScreen(new com.concertstage.client.light.FixturePatchScreen(pos));
    }

    public static void openLumicore(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.concertstage.light.LumicoreBlockEntity)
            mc.setScreen(new com.concertstage.client.light.LumicoreScreen(pos));
    }

    /** CSC Structure Manager: the roof the clicked block belongs to. */
    public static void openStructure(BlockPos clicked) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.setScreen(new StructureScreen(clicked));
    }

    public static void openAngle() {
        Minecraft.getInstance().setScreen(new AngleScreen());
    }

    public static void openPlatformAngle() {
        Minecraft.getInstance().setScreen(new AngleScreen(true));
    }

    public static void openRemote(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new RemoteScreen(hand));
    }
}
