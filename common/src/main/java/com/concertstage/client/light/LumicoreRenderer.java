package com.concertstage.client.light;

import com.concertstage.light.LightServer;
import com.concertstage.light.LumicoreBlock;
import com.concertstage.light.LumicoreBlockEntity;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Show;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * Live screens of the CSC LUMICORE X1 (the desk itself is a static model): left — show, patch and programmer; centre — the playbacks
 * with their current cue and fader; right — music clock, tempo, masters and safety states. Positions from
 * {@code lightshow/lumicore_layout.json} (written by {@code tools/lightshow/make_lumicore.py}).
 */
public class LumicoreRenderer implements BlockEntityRenderer<LumicoreBlockEntity> {
    private static final double[] SCREEN_X = {-0.12, 0.50, 1.12};
    private static final double SCREEN_Y = 1.2256, SCREEN_Z = 0.2393, SCREEN_W = 0.56, SCREEN_H = 0.36;
    private static final float TILT = 18f;

    public LumicoreRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LumicoreBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        if (level == null) return;
        if (com.concertstage.light.ArtNetConsoleBlock.isNode(be)) return;     // rev. 1.3.0-2: the Art-Net node has no desk screens
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos())) > 40 * 40) return;
        Direction facing = be.getBlockState().getValue(LumicoreBlock.FACING);
        int turns = switch (facing) { case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> 0; };
        Show show = be.show;
        Live live = be.live;
        double gameTime = level.getGameTime() + partial;
        Engine.Music music = LightServer.music(level, live, gameTime, 0);
        List<String> left = new ArrayList<>(), mid = new ArrayList<>(), right = new ArrayList<>();
        left.add("§b" + show.name);
        left.add("§7Fixtures: §f" + show.patch.size() + "  §7Groups: §f" + show.groups.size());
        left.add("§7Programmer: §f" + live.programmer.size() + (live.programmerEffects.isEmpty() ? "" : " §d+" + live.programmerEffects.size() + " fx"));
        left.add("§7Lists: §f" + show.lists.size() + "  §7Presets: §f" + show.presets.size());
        if (!be.status.isEmpty()) left.add("§e" + be.status);
        int shown = 0;
        for (Show.CueList l : show.lists) {
            if (shown++ >= 6) break;
            Live.Playback pb = live.playbacks.get(l.id);
            String cue = "-";
            if (pb != null && pb.running() && pb.index < l.cues.size()) {
                Show.Cue c = l.cues.get(pb.index);
                cue = fmt(c.number) + " " + c.name;
            }
            int lv = pb == null ? 100 : Math.round(pb.level * 100);
            mid.add((pb != null && pb.running() ? "§a▶ " : "§8■ ") + "§f" + clip(l.name, 12) + " §7" + clip(cue, 16) + " §8" + lv + "%");
        }
        if (mid.isEmpty()) mid.add("§8No cue list");
        if (music.valid()) right.add((music.playing() ? "§a▶ " : "§e❚❚ ") + "§f" + time(music.positionMs()));
        else right.add("§8No music linked");
        if (show.timeline.bpm > 0) right.add("§7BPM §f" + String.format(Locale.ROOT, "%.1f", show.timeline.bpm));
        right.add("§7GM §f" + Math.round(live.grandMaster * 100) + "%");
        if (live.blackout) right.add("§c§lBLACKOUT");
        if (live.noStrobe) right.add("§6NO STROBE");
        if (live.moveLimit) right.add("§6MOVE LIMIT");
        if (!live.autoEnabled) right.add("§cAUTO OFF");
        right.add(live.timelineArmed ? "§bTimeline armed" : "§8Timeline off");

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-90f * turns));
        pose.translate(-0.5, 0, -0.5);
        List<List<String>> screens = List.of(left, mid, right);
        Font font = mc.font;
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(SCREEN_X[i], SCREEN_Y, SCREEN_Z + 0.004);
            pose.mulPose(Axis.XP.rotationDegrees(-TILT));
            float s = 0.0028f;
            pose.translate(-SCREEN_W / 2 + 0.02, SCREEN_H / 2 - 0.02, 0.002);
            pose.scale(s, -s, s);
            int y = 0;
            for (String line : screens.get(i)) {
                font.drawInBatch(font.plainSubstrByWidth(line, (int) ((SCREEN_W - 0.04) / s)), 0, y, 0xFFFFFFFF, false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                y += 10;
                if (y > (SCREEN_H - 0.04) / s - 8) break;
            }
            pose.popPose();
        }
        pose.popPose();
    }

    static String fmt(float n) {
        return n == Math.rint(n) ? String.valueOf((int) n) : String.format(Locale.ROOT, "%.1f", n);
    }

    static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    @Override
    public boolean shouldRenderOffScreen(LumicoreBlockEntity be) {
        return true;
    }
}
