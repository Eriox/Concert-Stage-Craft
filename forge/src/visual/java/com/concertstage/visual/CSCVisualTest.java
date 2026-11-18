package com.concertstage.visual;

import com.concertstage.StageContent;
import com.concertstage.block.tarp.StageTarpRailBlock;
import com.concertstage.block.tarp.StageTarpRailBlockEntity;
import com.concertstage.client.light.LightClient;
import com.concertstage.client.light.LightDiagnostics;
import com.concertstage.light.FixtureBlock;
import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.LightServer;
import com.concertstage.light.LumicoreBlockEntity;
import com.concertstage.light.core.Attr;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.Bytes;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Development only (src/visual, built with -PcscVisual, never in the published JAR): builds lighting test scenes in a flat night world,
 * takes screenshots into run/csc-visual and writes run/csc-visual/report.txt, then quits. Scenes: tone mapping on a light floor, the
 * beam families side by side in the haze, gobos on a tarp sheet and on a ceiling, a large rig turned in front of the camera (budgets).
 * Optional -Dcsc.visual.only=<scene prefix> runs only the matching steps; -Dcsc.visual.stay=true keeps the game open at the end.
 */
@Mod.EventBusSubscriber(modid = "concertstage", value = Dist.CLIENT)
public final class CSCVisualTest {
    private static final String WORLD = System.getProperty("csc.visual.world", "").isEmpty() ? "CSC_Visual" : System.getProperty("csc.visual.world");
    /** Rev. 20: a copied world of the user (its CSC stage), lit from its own desk instead of the built scenes. */
    private static final boolean SCENE = !System.getProperty("csc.visual.world", "").isEmpty();
    private static final int Y = 100;
    private static boolean opened;
    private static int tick, step = -1, wait;
    private static Path out;
    private static final List<Step> STEPS = new ArrayList<>();

    private record Step(String name, Runnable server, Runnable client, int settle) {}

    private CSCVisualTest() {}

    private static boolean on() {
        return Boolean.getBoolean("csc.visual");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !on()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!opened) {
                // rev. 22: a fresh run folder opens on the accessibility welcome screen first (nobody to click it during the test)
                if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                    mc.options.onboardAccessibility = false; mc.options.save(); mc.setScreen(new TitleScreen(true)); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                opened = true;
                out = mc.gameDirectory.toPath().resolve("csc-visual");
                Files.createDirectories(out);
                Files.writeString(out.resolve("report.txt"), "CSC visual test " + java.time.LocalDateTime.now() + "\n", StandardCharsets.UTF_8);
                if (mc.getLevelSource().levelExists(WORLD)) mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
                else {
                    GameRules rules = new GameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                    rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261111L, false, false),
                        ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            if (mc.level == null || mc.player == null || server == null || server.getPlayerList().getPlayers().isEmpty()) return;
            tick++;
            if (tick == 1) {
                if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v141")) v141();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v140")) v140();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v139")) { v139(); v140(); }
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v138")) v138();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v137")) v137();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v136")) v136();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v135")) v135();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v134")) v134();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v133")) v133();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v132")) v132();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v131b")) v131b();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("v131")) v131();
                else if (System.getProperty("csc.visual.only", "").startsWith("v13")) v13();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("r25d")) r25d();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("r25")) r25();
                else if (SCENE && System.getProperty("csc.visual.only", "").startsWith("r24")) r24();
                else if (SCENE && (System.getProperty("csc.visual.only", "").startsWith("r22") || System.getProperty("csc.visual.only", "").startsWith("r23"))) r22(); else if (SCENE) scene(); else build();
                mc.options.hideGui = true;
                mc.options.pauseOnLostFocus = false;          // the window may lose the focus during the test
                mc.options.renderDistance().set(10);
                run(server, "gamemode creative @a", "time set 18000", "weather clear 100000", "gamerule doDaylightCycle false",
                    "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "kill @e[type=!player]");
                server.execute(() -> { ServerPlayer p = player(server); p.getAbilities().flying = true; p.onUpdateAbilities(); });
                log("GL " + com.mojang.blaze3d.platform.GlUtil.getRenderer() + " / " + com.mojang.blaze3d.platform.GlUtil.getOpenGLVersion());
                wait = 40;
                return;
            }
            if (nullShader) com.mojang.blaze3d.systems.RenderSystem.setShader(() -> null);   // rev. 2: what ImmediatelyFast leaves before the camera images
            if (feedCam != null) com.concertstage.client.vision.CameraFeeds.frame(feedCam);   // rev. 1: keeps the test camera image live
            if (moving && all != null && tick % 2 == 0) {
                // rev. 21: every head follows a moving target (an AutoShow-like sweep): shadow maps and sections are redrawn all the time
                final int t = tick;
                server.execute(() -> { LumicoreBlockEntity d = desk(SCENE_DESK); if (d == null) return;
                    for (var p : d.show.patch) aim(d, p.fid, SX - 6 + 8 * Math.sin(t * 0.07 + p.fid), SY, p.z + 6 * Math.cos(t * 0.05 + p.fid)); });
            }
            if (wait > 0) { wait--; return; }
            if (step >= 0 && step < STEPS.size()) {
                Step s = STEPS.get(step);
                if (s.name != null && s.name.startsWith("#")) logBudgets(s.name.substring(1));
                else if (s.name != null && !s.name.startsWith("-")) shot(mc, s.name);
            }
            step++;
            String only = System.getProperty("csc.visual.only", "");
            while (step < STEPS.size() && !only.isEmpty() && STEPS.get(step).name != null && !STEPS.get(step).name.startsWith("-")
                && !STEPS.get(step).name.startsWith("#") && java.util.Arrays.stream(only.split(",")).noneMatch(STEPS.get(step).name::startsWith)) step++;
            if (step >= STEPS.size()) {
                log("DONE");
                if (!Boolean.getBoolean("csc.visual.stay")) mc.stop();
                else mc.options.hideGui = false;
                step = Integer.MAX_VALUE / 2;
                return;
            }
            Step s = STEPS.get(step);
            if (s.server != null) server.execute(s.server);
            if (s.client != null) s.client.run();
            wait = s.settle;
        } catch (Throwable t) {
            log("ERROR " + t);
            LogUtils.getLogger().error("CSC_VISUAL", t);
            for (StackTraceElement el : t.getStackTrace()) log("  at " + el);
        }
    }

    // ------------------------------------------------------------------ scenes
    private static BlockPos deskTone, deskBeams, deskGobo, deskRig;
    private static int[] tone8, tone1, beams, gobos, rig, close;
    private static BlockPos deskClose;

    private static void build() {
        // ---- 1. tone mapping: light floor (two light textures), wash lights above
        add("-build tone", () -> {
            ServerLevel l = level();
            fill(l, -16, Y - 1, -16, 16, Y - 1, 16, "minecraft:white_concrete");
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++)
                if ((x + z & 1) == 0) l.setBlock(new BlockPos(x, Y - 1, z), net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
            fill(l, -9, Y - 1, 2, 9, Y - 1, 6, "minecraft:light_gray_concrete");
            fill(l, -9, Y + 11, 0, 9, Y + 11, 0, "minecraft:black_concrete");
            deskTone = desk(l, new BlockPos(0, Y, -14));
            List<Integer> f = new ArrayList<>();
            for (int i = 0; i < 8; i++) f.add(fixture(l, deskTone, StageContent.LUMORA_W19, new BlockPos(-7 + i * 2, Y + 10, 0), true, Direction.SOUTH));
            tone8 = ints(f);
            tone1 = new int[]{tone8[3]};
        }, null, 40);
        add("-tone off", () -> { LumicoreBlockEntity d = desk(deskTone); clear(d); for (int i = 0; i < tone8.length; i++) aim(d, tone8[i], -2.5 + (i % 4) * 1.7, Y, 2 + (i / 4) * 1.5); },
            () -> cam(0, Y + 7, -13, 0, 28), 40);
        add("tone_1x_white_tm_on", () -> { LumicoreBlockEntity d = desk(deskTone); clear(d); aim(d, tone1[0], 0, Y, 3); white(d, tone1, 0.6f); },
            () -> LightClient.toneMapping = true, 60);
        add("tone_1x_white_tm_off", null, () -> LightClient.toneMapping = false, 30);
        add("tone_8x_white_tm_off", () -> { LumicoreBlockEntity d = desk(deskTone);
            for (int i = 0; i < tone8.length; i++) aim(d, tone8[i], -1.5 + (i % 4), Y, 2.5 + (i / 4)); white(d, tone8, 0.6f); }, null, 60);
        add("tone_8x_white_tm_on", null, () -> LightClient.toneMapping = true, 30);
        add("tone_8x_red_tm_on", () -> color(desk(deskTone), tone8, 1, 0, 0), null, 30);
        add("tone_8x_mixed_tm_on", () -> { LumicoreBlockEntity d = desk(deskTone);
            for (int i = 0; i < tone8.length; i++) rgb(d, new int[]{tone8[i]}, i % 3 == 0 ? 1 : 0, i % 3 == 1 ? 1 : 0.2f, i % 3 == 2 ? 1 : 0); }, null, 30);
        add("-tone dark", () -> clear(desk(deskTone)), null, 10);

        // ---- 2. beam families in the haze: Beam / Spot / Profile / Wash / PULSESTRIKE, standing on a black floor
        add("-build beams", () -> {
            ServerLevel l = level();
            fill(l, 50, Y - 1, -20, 90, Y - 1, 40, "minecraft:black_concrete");
            deskBeams = desk(l, new BlockPos(70, Y, -18));
            Block[] b = {StageContent.RAZOR_B1, StageContent.VORTEX_X1, StageContent.PHANTOM_S1, StageContent.PROFILE_P1, StageContent.LUMORA_W19,
                StageContent.LUMORA_W7, StageContent.PULSESTRIKE_M, StageContent.PIXELWASH_W37};
            List<Integer> f = new ArrayList<>();
            for (int i = 0; i < b.length; i++) f.add(fixture(l, deskBeams, b[i], new BlockPos(56 + i * 4, Y, 0), false, Direction.SOUTH));
            beams = ints(f);
        }, null, 40);
        add("-beams aim", () -> { LumicoreBlockEntity d = desk(deskBeams); clear(d);
            for (int i = 0; i < beams.length; i++) aim(d, beams[i], 56 + i * 4, Y + 22, 14); white(d, beams, 0.5f); },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[1]; cam(70, Y + 6, -16, 0, -12); }, 60);
        add("beams_white_haze_light_front", null, null, 10);
        add("beams_white_haze_light_side", null, () -> cam(44, Y + 9, 7, -90, -10), 40);
        add("beams_white_haze_dense_side", null, () -> LightClient.haze = LightClient.HAZE_PRESETS[3], 30);
        add("beams_blue_haze_dense_side", () -> color(desk(deskBeams), beams, 0.1f, 0.3f, 1f), null, 30);
        add("beams_gobo3_haze_dense_front", () -> { LumicoreBlockEntity d = desk(deskBeams); color(d, beams, 1, 1, 1); set(d, beams, Attr.GOBO, 3 / 8f); },
            () -> cam(70, Y + 6, -16, 0, -12), 40);
        add("beams_lookinto_gobo3", () -> { LumicoreBlockEntity d = desk(deskBeams); for (int i = 0; i < beams.length; i++) aim(d, beams[i], 70, Y + 8, -16); }, () -> cam(70, Y + 7, -16, 0, 0), 60);
        add("beams_wide_zoom_haze_dense_side", () -> { LumicoreBlockEntity d = desk(deskBeams); set(d, beams, Attr.GOBO, 0);
            for (int i = 0; i < beams.length; i++) aim(d, beams[i], 56 + i * 4, Y + 22, 14); set(d, beams, Attr.ZOOM, 1f); },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[3]; cam(44, Y + 9, 7, -90, -10); }, 60);
        add("beams_narrow_zoom_haze_dense_side", () -> set(desk(deskBeams), beams, Attr.ZOOM, 0f), null, 30);
        add("-beams dark", () -> clear(desk(deskBeams)), () -> LightClient.haze = LightClient.HAZE_PRESETS[1], 10);

        // ---- 3. gobos on a tarp sheet, on a light wall and on a ceiling
        add("-build gobo", () -> {
            ServerLevel l = level();
            fill(l, 110, Y - 1, -24, 150, Y - 1, 12, "minecraft:gray_concrete");
            fill(l, 112, Y + 12, 0, 128, Y + 12, 0, "minecraft:black_concrete");                 // beam the tarp hangs under
            fill(l, 136, Y, 4, 148, Y + 10, 4, "minecraft:white_concrete");                       // wall
            fill(l, 112, Y + 14, -22, 148, Y + 14, -8, "minecraft:white_concrete");               // ceiling
            BlockPos rail = new BlockPos(113, Y + 11, 0);
            l.setBlock(rail, StageContent.STAGE_TARP_RAIL.defaultBlockState().setValue(StageTarpRailBlock.FACING, Direction.EAST), 3);
            if (l.getBlockEntity(rail) instanceof StageTarpRailBlockEntity t) { t.applySettings(14, 11, 8); log("tarp built length " + t.getLength() + " height " + t.getHeight()); }
            else log("tarp rail block entity missing");
            deskGobo = desk(l, new BlockPos(130, Y, -22));
            List<Integer> f = new ArrayList<>();
            Block[] b = {StageContent.VORTEX_X1, StageContent.PHANTOM_S1, StageContent.PROFILE_P1, StageContent.RAZOR_B1};
            for (int i = 0; i < 4; i++) f.add(fixture(l, deskGobo, b[i], new BlockPos(115 + i * 3, Y, -14), false, Direction.NORTH));
            for (int i = 0; i < 4; i++) f.add(fixture(l, deskGobo, b[i], new BlockPos(134 + i * 3, Y, -14), false, Direction.NORTH));
            gobos = ints(f);
        }, null, 60);
        add("-gobo aim", () -> { LumicoreBlockEntity d = desk(deskGobo); clear(d);
            for (int i = 0; i < 4; i++) aim(d, gobos[i], 115 + i * 3, Y + 5, 0);
            for (int i = 4; i < 8; i++) aim(d, gobos[i], 136 + (i - 4) * 3.5, Y + 5, 4);
            white(d, gobos, 0.5f); set(d, gobos, Attr.GOBO, 3 / 8f); set(d, gobos, Attr.ZOOM, 0.25f); },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[1]; cam(130, Y + 5, -20, 0, -4); }, 80);
        add("gobo_tarp_and_wall", null, null, 10);
        add("gobo_tarp_close", null, () -> cam(120, Y + 5, -7, 0, -2), 30);
        add("gobo_wall_close", null, () -> cam(140, Y + 5, -4, 0, -2), 30);
        add("gobo_tarp_far", null, () -> cam(130, Y + 8, -40, 0, -6), 40);
        add("gobo_tarp_white_close", () -> { if (level().getBlockEntity(new BlockPos(113, Y + 11, 0)) instanceof StageTarpRailBlockEntity t)
            t.setColor(com.concertstage.block.tarp.TarpColor.WHITE); }, () -> cam(120, Y + 5, -7, 0, -2), 40);
        add("gobo_tarp_grey_close", () -> { if (level().getBlockEntity(new BlockPos(113, Y + 11, 0)) instanceof StageTarpRailBlockEntity t)
            t.setColor(com.concertstage.block.tarp.TarpColor.GREY); }, null, 30);
        add("gobo_ceiling", () -> { LumicoreBlockEntity d = desk(deskGobo); for (int i = 0; i < 8; i++) aim(d, gobos[i], 116 + i * 4, Y + 14, -15); },
            () -> cam(130, Y + 3, -26, 0, -30), 60);
        add("gobo_ceiling_diag_occlusion", null, () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.OCCLUSION, 20);
        add("gobo_ceiling_diag_mask", null, () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.MASK, 20);
        add("gobo_ceiling_diag_surfaces", null, () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.SURFACE, 20);
        add("gobo_ceiling_tilted", () -> { LumicoreBlockEntity d = desk(deskGobo); for (int i = 0; i < 8; i++) aim(d, gobos[i], 116 + i * 4, Y + 14, -19); },
            () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.OFF, 60);
        add("-gobo dark", () -> clear(desk(deskGobo)), null, 10);

        // ---- rev. 20b: close-up of the fixtures (where the beam leaves the lens, light around the bodies) and focus sweep
        add("-build close", () -> {
            ServerLevel l = level();
            fill(l, 250, Y - 1, -20, 290, Y - 1, 20, "minecraft:light_gray_concrete");
            fill(l, 254, Y + 6, 0, 270, Y + 6, 0, "minecraft:black_concrete");
            fill(l, 250, Y, 16, 290, Y + 12, 16, "minecraft:white_concrete");             // wall for the focus sweep
            deskClose = desk(l, new BlockPos(270, Y, -18));
            close = new int[]{
                fixture(l, deskClose, StageContent.VORTEX_X1, new BlockPos(256, Y + 5, 0), true, Direction.SOUTH),
                fixture(l, deskClose, StageContent.LUMORA_W19, new BlockPos(260, Y + 5, 0), true, Direction.SOUTH),
                fixture(l, deskClose, StageContent.PULSESTRIKE_M, new BlockPos(264, Y + 5, 0), true, Direction.SOUTH),
                fixture(l, deskClose, StageContent.PROFILE_P1, new BlockPos(268, Y + 5, 0), true, Direction.SOUTH),
                fixture(l, deskClose, StageContent.VORTEX_X1, new BlockPos(256, Y, 4), false, Direction.SOUTH),
                fixture(l, deskClose, StageContent.LUMORA_W19, new BlockPos(260, Y, 4), false, Direction.SOUTH),
                fixture(l, deskClose, StageContent.PHANTOM_S1, new BlockPos(264, Y, 4), false, Direction.SOUTH),
                fixture(l, deskClose, StageContent.RAZOR_B1, new BlockPos(268, Y, 4), false, Direction.SOUTH)};
        }, null, 60);
        add("-close on", () -> { LumicoreBlockEntity d = desk(deskClose); clear(d);
            for (int i = 0; i < 4; i++) aim(d, close[i], 256 + i * 4, Y, 8);
            for (int i = 4; i < 8; i++) aim(d, close[i], 256 + (i - 4) * 4, Y + 10, 16);
            white(d, close, 0.35f); },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[1]; cam(262, Y + 3, -7, 0, 0); }, 80);
        add("close_front_on", null, null, 10);
        add("close_side_on", null, () -> cam(250, Y + 4, 2, -90, 10), 30);
        add("close_side_lens", null, () -> cam(253, Y + 4, 1.5, -90, 25), 30);
        add("close_side_emitters", null, () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.EMISSIVE, 20);
        add("close_side_surfaces", null, () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.SURFACE, 20);
        add("close_side_beams", null, () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.VOLUME, 20);
        add("close_side_off", () -> set(desk(deskClose), close, Attr.DIMMER, 0f),
            () -> com.concertstage.client.light.LightDiagnostics.mode = com.concertstage.client.light.LightDiagnostics.OFF, 30);
        float[] foci = {0f, 0.3f, 0.6f, 0.8f, 1f};
        for (float f : foci) {
            String n = String.format("%03d", Math.round(f * 100));
            add("focus_" + n + "_side", () -> { LumicoreBlockEntity d = desk(deskClose); set(d, close, Attr.DIMMER, 1f);
                set(d, close, Attr.GOBO, 3 / 8f); set(d, close, Attr.FOCUS, f); },
                () -> cam(246, Y + 5, 9, -90, -8), 40);
            add("focus_" + n + "_wall", null, () -> cam(262, Y + 4, 6, 0, 8), 25);
        }
        add("-close dark", () -> clear(desk(deskClose)), null, 10);

        // ---- 4. large rig turned in front of the camera (budgets, flicker)
        add("-build rig", () -> {
            ServerLevel l = level();
            fill(l, 170, Y - 1, -30, 230, Y - 1, 30, "minecraft:gray_concrete");
            deskRig = desk(l, new BlockPos(200, Y, -28));
            List<Integer> f = new ArrayList<>();
            for (int i = 0; i < 12; i++) for (int j = 0; j < 10; j++)
                f.add(fixture(l, deskRig, j % 2 == 0 ? StageContent.VORTEX_X1 : StageContent.LUMORA_W19, new BlockPos(178 + i * 4, Y, -18 + j * 4), false, Direction.SOUTH));
            rig = ints(f);
            log("rig patched " + rig.length);
        }, null, 80);
        add("-rig aim", () -> { LumicoreBlockEntity d = desk(deskRig); clear(d);
            for (int i = 0; i < rig.length; i++) aim(d, rig[i], 178 + (i / 10) * 4 + 3, Y + 24, -18 + (i % 10) * 4 + 6);
            white(d, rig, 0.4f); set(d, rig, Attr.GOBO, 2 / 8f); },
            () -> cam(200, Y + 4, -36, 0, -8), 120);
        add("#rig fps hold 1", null, null, 100);
        add("#rig fps hold 2", null, null, 60);
        for (int k = 0; k < 6; k++) add("rig_still_" + k, null, null, 2);
        for (int k = 0; k < 24; k++) {
            int kk = k;
            add("rig_turn_" + String.format("%02d", k), null, () -> { cam(200, Y + 4, -36, -30 + kk * 2.5f, -8); logBudgets("rig " + kk); }, 3);
        }
        add("#rig fps after turn", null, () -> cam(200, Y + 4, -36, 30, -8), 100);
        for (int k = 0; k < 6; k++) add("rig_still_b" + k, null, null, 2);
        add("-rig dark", () -> clear(desk(deskRig)), null, 10);
    }

    // ------------------------------------------------------------------ the user's stage (CraftCity copy)
    private static final BlockPos SCENE_DESK = new BlockPos(Integer.getInteger("csc.visual.deskX", -343), Integer.getInteger("csc.visual.deskY", 205),
        Integer.getInteger("csc.visual.deskZ", -310));
    private static int[] all, zoneFids = new int[0];
    private static int zoneY = 203;
    private static volatile boolean moving;
    /** Rev. 23: quality of the r23 steps (-Dcsc.visual.quality, default high). */
    private static final int r23q = Integer.getInteger("csc.visual.quality", 2);
    private static final double SX = -293, SY = 205, SZ = -310;          // stage centre (deck height)

    private static void scene() {
        add("-scene load", null, () -> cam(-340, 208, -310, -90, -6), 100);
        add("-scene desk", () -> {
            if (!(level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d)) { log("no desk at " + SCENE_DESK); all = new int[0]; return; }
            all = d.show.patch.stream().mapToInt(p -> p.fid).toArray();
            StringBuilder sb = new StringBuilder("scene desk: " + all.length + " fixtures:");
            java.util.Map<String, Integer> types = new java.util.TreeMap<>();
            for (var p : d.show.patch) types.merge(p.profile().name + (p.mount == 1 ? " (hung)" : ""), 1, Integer::sum);
            types.forEach((k, v) -> sb.append(" ").append(v).append(" x ").append(k).append(","));
            log(sb.toString());
            clear(d);
            for (var p : d.show.patch) aim(d, p.fid, SX - 4 + (p.z - SZ) * 0.2, SY, p.z);
            white(d, all, 0.45f);
        }, null, 100);
        add("scene_foh_white", null, null, 10);
        add("scene_close_white", null, () -> cam(-322, 206, -310, -90, -12), 40);
        add("scene_foh_colour_gobo", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (int i = 0; i < all.length; i++) rgb(d, new int[]{all[i]}, i % 3 == 0 ? 1 : 0.1f, i % 3 == 1 ? 1 : 0.1f, i % 3 == 2 ? 1 : 0.2f);
            set(d, all, Attr.GOBO, 3 / 8f); for (var p : d.show.patch) aim(d, p.fid, -322, SY - 1, p.z); },
            () -> cam(-340, 208, -310, -90, -6), 80);
        add("scene_foh_colour_gobo_dense", null, () -> LightClient.haze = LightClient.HAZE_PRESETS[3], 40);
        add("scene_side_colour_gobo_dense", null, () -> cam(-312, 213, -284, -142, -10), 40);
        add("-scene roof", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); white(d, all, 0.3f);
            for (var p : d.show.patch) aim(d, p.fid, p.x < -290 ? -299 : -284, 226, p.z); },
            () -> LightClient.haze = LightClient.HAZE_PRESETS[1], 80);
        add("scene_roof_from_stage", null, () -> cam(-300, 207, -310, -90, -62), 40);
        add("scene_roof_from_foh", null, () -> cam(-330, 209, -310, -90, -18), 40);
        add("scene_tarp", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (int i = 0; i < all.length; i++) aim(d, all[i], -281.5, 219, -328 + (i % 24)); set(d, all, Attr.GOBO, 5 / 8f); },
            () -> cam(-312, 212, -312, -60, -8), 80);
        add("scene_roof_above", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) aim(d, p.fid, p.x < -290 ? -299 : -284, 240, p.z); set(d, all, Attr.GOBO, 0); },
            () -> cam(-330, 246, -310, -90, 35), 80);
        add("scene_roof_under", null, () -> cam(-300, 207, -310, -90, -62), 30);
        add("scene_tarp_behind", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) aim(d, p.fid, p.x + 0.5, 214, -340); },
            () -> cam(-292, 214, -352, 0, -4), 80);
        add("scene_tarp_inside", null, () -> cam(-292, 210, -300, 180, 0), 30);
        // rev. 20c: the author's third picture (red washes, haze seen from the stage towards its back) and an all-out "drop"
        add("-scene red", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile().name.contains("W19")) {
                aim(d, p.fid, -300, SY, p.z); set(d, new int[]{p.fid}, Attr.DIMMER, 1f); rgb(d, new int[]{p.fid}, 1f, 0f, 0f); set(d, new int[]{p.fid}, Attr.ZOOM, 0.8f); } },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[1]; cam(-316, 208, -310, -90, 2); }, 80);
        add("scene_red_haze_light", null, null, 10);
        add("scene_red_haze_medium", null, () -> LightClient.haze = LightClient.HAZE_PRESETS[2], 30);
        add("scene_red_haze_dense", null, () -> LightClient.haze = LightClient.HAZE_PRESETS[3], 30);
        add("scene_red_haze_dense_foh", null, () -> cam(-340, 208, -310, -90, -6), 40);
        add("-scene drop", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) aim(d, p.fid, -325, SY - 1, p.z + (p.fid % 3 - 1) * 6);
            white(d, all, 0.6f); },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[2]; cam(-341, 207.6, -310, -90, -4); }, 80);
        add("scene_drop_full_foh", null, null, 10);
        add("scene_drop_full_side", null, () -> cam(-330, 208, -278, -150, -6), 40);
        add("-scene turn", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, all, Attr.GOBO, 0);
            for (var p : d.show.patch) aim(d, p.fid, -322, SY - 1, p.z); }, () -> cam(-340, 208, -310, -90, -6), 80);
        add("#scene fps hold", null, null, 100);
        for (int k = 0; k < 4; k++) add("scene_still_" + k, null, null, 2);
        add("-scene dark", () -> clear(desk(SCENE_DESK)), null, 10);
        rev21();
    }

    /**
     * Rev. 21: the author's four pictures of 11 November (rev. 20) on the copied stage — red washes on the backdrop seen from the stage
     * front, blue light under the stage deck next to the FOH subs, the roof truss seen from below in purple, the stage from far away —
     * with the shadow diagnostic (green lit, red behind an obstacle) for the deck.
     */
    private static void rev21() {
        add("-r21 red", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) {
                aim(d, p.fid, -300, SY, p.z); set(d, new int[]{p.fid}, Attr.DIMMER, 1f); rgb(d, new int[]{p.fid}, 1f, 0f, 0f); set(d, new int[]{p.fid}, Attr.ZOOM, 0.8f); } },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[2]; LightDiagnostics.mode = LightDiagnostics.OFF; cam(-318, 206, -310, -90, -14); }, 100);
        add("r21_red_backdrop", null, null, 10);
        add("r21_red_backdrop_left", null, () -> cam(-318, 206, -322, -70, -12), 40);
        add("-r21 blue", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) {
                aim(d, p.fid, -318 + (p.fid % 4) * 2, SY, p.z); set(d, new int[]{p.fid}, Attr.DIMMER, 1f); rgb(d, new int[]{p.fid}, 0.35f, 0.1f, 1f); set(d, new int[]{p.fid}, Attr.ZOOM, 0.9f); } },
            () -> cam(-318, 205.5, -306, -90, 30), 100);
        add("r21_under_deck_subs", null, null, 10);
        add("r21_under_deck_low", null, () -> cam(-318, 202.6, -306, -90, 6), 40);
        add("r21_under_deck_shadows", null, () -> LightDiagnostics.mode = LightDiagnostics.OCCLUSION, 30);
        add("-r21 diag off 0", null, () -> LightDiagnostics.mode = LightDiagnostics.OFF, 5);
        add("-r21 red risers", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) { aim(d, p.fid, -300, SY, p.z); rgb(d, new int[]{p.fid}, 1f, 0f, 0f); } },
            () -> cam(-316, 206.2, -312, -90, 6), 100);
        add("r21_red_risers", null, null, 10);
        add("r21_red_risers_surfaces_only", null, () -> LightDiagnostics.mode = LightDiagnostics.SURFACE, 30);
        add("r21_red_risers_surfaces_no_rig_light", null, () -> LightClient.rigLight = 0f, 30);
        add("-r21 rig back", null, () -> LightClient.rigLight = 1f, 5);
        add("r21_red_risers_surfaces_high_fixed", null, () -> { LightClient.autoPerformance = false; LightClient.beamQuality = 2; }, 60);
        add("-r21 auto back", null, () -> LightClient.autoPerformance = true, 5);
        add("r21_red_risers_shadows", null, () -> LightDiagnostics.mode = LightDiagnostics.OCCLUSION, 30);
        add("-r21 diag off 1", null, () -> LightDiagnostics.mode = LightDiagnostics.OFF, 5);
        add("-r21 diag off", null, () -> LightDiagnostics.mode = LightDiagnostics.OFF, 5);
        add("-r21 purple", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) {
                aim(d, p.fid, p.x < -290 ? -299 : -284, 226, p.z); set(d, new int[]{p.fid}, Attr.DIMMER, 1f); rgb(d, new int[]{p.fid}, 0.6f, 0.15f, 1f); set(d, new int[]{p.fid}, Attr.ZOOM, 0.6f); } },
            () -> cam(-312, 206, -310, -90, -60), 100);
        add("r21_truss_from_below", null, null, 10);
        add("r21_truss_from_side", null, () -> cam(-300, 210, -336, 0, -30), 40);
        add("r21_truss_from_back", null, () -> cam(-270, 212, -310, 90, -25), 40);
        add("-r21 far", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) aim(d, p.fid, -300, SY, p.z); },
            () -> cam(-400, 207, -310, -90, -4), 120);
        add("r21_far_stage", null, null, 10);
        add("r21_far_stage_100", null, () -> cam(-430, 208, -310, -90, -3), 60);
        add("#r21 fps", null, null, 60);
        // rev. 21: cost of each part at the FOH view of the whole rig (no vsync, fixed quality = the default "high", no automatic level)
        add("-r21 perf setup", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                for (var p : d.show.patch) aim(d, p.fid, SX - 4 + (p.z - SZ) * 0.2, SY, p.z); white(d, all, 0.45f); },
            () -> { Minecraft mc = Minecraft.getInstance(); mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
                LightClient.autoPerformance = false; LightClient.beamQuality = 2; cam(-340, 208, -310, -90, -6); }, 200);
        add("#r21 perf all", null, null, 100);
        add("#r21 perf no bloom", null, () -> LightClient.bloom = 0f, 100);
        add("#r21 perf no bloom no rig light", null, () -> LightClient.rigLight = 0f, 100);
        add("#r21 perf ultra", null, () -> { LightClient.bloom = 1f; LightClient.rigLight = 1f; LightClient.beamQuality = 3; }, 100);
        add("#r21 perf moving heads ultra", null, () -> moving = true, 160);
        add("#r21 perf moving heads high", null, () -> LightClient.beamQuality = 2, 160);
        add("-r21 perf still", null, () -> moving = false, 5);
        add("-r21 perf back", null, () -> { LightClient.beamQuality = 2; LightClient.autoPerformance = true; }, 5);
        // rev. 21: the three light zones of the PULSESTRIKE M (top RGB bar, centre white strobe, bottom RGB bar)
        add("-r21 zones", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            ServerLevel l = level();
            // three PULSESTRIKE M standing on the audience floor, facing the camera (west)
            java.util.List<Integer> f = new java.util.ArrayList<>();
            for (int k = 0; k < 3; k++) {
                int x = -328, z = -314 + k * 4;
                int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                f.add(fixture(l, SCENE_DESK, StageContent.PULSESTRIKE_M, new BlockPos(x, y, z), false, Direction.WEST));
                zoneY = y;
            }
            int[] ps = ints(f);
            zoneFids = ps;
            set(d, ps, Attr.DIMMER, 1f); set(d, ps, Attr.WHITE, 1f);
            set(d, ps, Attr.TOP_DIM, 1f); set(d, ps, Attr.TOP_RED, 1f); set(d, ps, Attr.TOP_GREEN, 0f); set(d, ps, Attr.TOP_BLUE, 0f);
            set(d, ps, Attr.BOT_DIM, 1f); set(d, ps, Attr.BOT_RED, 0f); set(d, ps, Attr.BOT_GREEN, 0.2f); set(d, ps, Attr.BOT_BLUE, 1f);
            set(d, ps, Attr.MID_DIM, 0f); },
            () -> LightClient.haze = LightClient.HAZE_PRESETS[1], 60);
        add("-r21 zones cam", null, () -> cam(-337, zoneY + 0.4, -310, -90, 6), 60);
        add("r21_zones_bars_red_blue_centre_off", null, null, 10);
        add("r21_zones_centre_only", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            set(d, zoneFids, Attr.TOP_DIM, 0f); set(d, zoneFids, Attr.BOT_DIM, 0f); set(d, zoneFids, Attr.MID_DIM, 1f); }, null, 30);
        add("r21_zones_all_off_but_top", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            set(d, zoneFids, Attr.TOP_DIM, 1f); set(d, zoneFids, Attr.BOT_DIM, 0f); set(d, zoneFids, Attr.MID_DIM, 0f); }, null, 30);
        add("-r21 centre flash", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            set(d, zoneFids, Attr.TOP_DIM, 1f); set(d, zoneFids, Attr.BOT_DIM, 1f); set(d, zoneFids, Attr.MID_DIM, 1f); set(d, zoneFids, Attr.MID_STROBE, 0.4f); }, null, 30);
        for (int k = 0; k < 6; k++) add("r21_zones_centre_flash_bars_steady_" + k, null, null, 1);
        add("-r21 alternate", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            set(d, zoneFids, Attr.MID_STROBE, 0f); set(d, zoneFids, Attr.MID_DIM, 0f);
            set(d, zoneFids, Attr.TOP_STROBE, 0.4f); set(d, zoneFids, Attr.BOT_STROBE, 0.4f); }, null, 30);
        for (int k = 0; k < 6; k++) add("r21_zones_alternate_" + k, null, null, 1);
        add("-r21 zones cleanup", () -> { ServerLevel l = level();
            for (int k = 0; k < 3; k++) l.setBlock(new BlockPos(-328, zoneY, -314 + k * 4), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }, null, 10);
        add("-r21 dark", () -> clear(desk(SCENE_DESK)), () -> LightClient.haze = LightClient.HAZE_PRESETS[1], 10);
    }


    /**
     * Rev. 22: the author's video and two pictures of 11 November (rev. 21 in his Modrinth profile, beam quality "low", fast graphics):
     * his own show as saved in the world, VORTEX shot straight down on the festival deck, LUMORA / PULSESTRIKE washes at full zoom
     * and focus, each at low and at high quality.
     */
    private static void r22() {
        add("-r22 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST); mc.levelRenderer.allChanged();
            LightClient.autoPerformance = false; LightClient.beamQuality = 0; LightClient.haze = LightClient.HAZE_PRESETS[2];
            cam(-341, 207.6, -310, -90, -4); }, 160);
        // rev. 23: remove what the gobo test of rev. 22 left in the copied world (white wall and three fixtures in front of FOH)
        add("-r22 cleanup", () -> { ServerLevel l = level();
            for (int y = (int) SY - 12; y <= (int) SY + 20; y++) {
                for (int z = -324; z <= -296; z++) {
                    BlockPos w = new BlockPos(-326, y, z);
                    if (l.getBlockState(w).is(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE)) l.setBlock(w, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                for (int k = 0; k < 3; k++) {
                    BlockPos fp = new BlockPos(-338, y, -319 + k * 9);
                    if (l.getBlockState(fp).getBlock() instanceof FixtureBlock) l.setBlock(fp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            } }, null, 20);
        add("-r22 desk", () -> {
            if (!(level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d)) { log("no desk at " + SCENE_DESK); all = new int[0]; return; }
            all = d.show.patch.stream().mapToInt(p -> p.fid).toArray();
            StringBuilder sb = new StringBuilder("scene desk: " + all.length + " fixtures:");
            java.util.Map<String, Integer> types = new java.util.TreeMap<>();
            for (var p : d.show.patch) types.merge(p.profile().name + (p.mount == 1 ? " (hung)" : ""), 1, Integer::sum);
            types.forEach((k, v) -> sb.append(" ").append(v).append(" x ").append(k).append(","));
            for (var p : d.show.patch) sb.append("\n   fid ").append(p.fid).append(" ").append(p.profile().name).append(" at ").append(p.x).append(" ").append(p.y).append(" ").append(p.z);
            log(sb.toString()); }, null, 10);
        boolean r23only = System.getProperty("csc.visual.only", "").startsWith("r23");
        if (!r23only) {
        // 1. the show as the author left it in his world
        add("r22_user_foh_low", null, null, 10);
        add("r22_user_side_low", null, () -> cam(-321, 206.5, -336, -45, -3), 60);
        add("r22_user_side_high", null, () -> LightClient.beamQuality = 2, 80);
        add("r22_user_foh_high", null, () -> cam(-341, 207.6, -310, -90, -4), 60);
        // 2. every spot straight down on the deck (picture 1)
        add("-r22 down", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile().isSpot()) { aim(d, p.fid, p.x + 0.5, SY - 3, p.z + 0.5); set(d, new int[]{p.fid}, Attr.DIMMER, 1f);
                rgb(d, new int[]{p.fid}, 1, 1, 1); set(d, new int[]{p.fid}, Attr.ZOOM, 0.1f); }
            for (var p : d.show.patch) if (!p.profile().isSpot()) { aim(d, p.fid, -300, SY, p.z); set(d, new int[]{p.fid}, Attr.DIMMER, 1f);
                rgb(d, new int[]{p.fid}, 1f, 0f, 0f); set(d, new int[]{p.fid}, Attr.ZOOM, 0.6f); } },
            () -> { LightClient.beamQuality = 0; cam(-321, 206.5, -336, -45, -3); }, 120);
        add("r22_down_side_low", null, null, 10);
        add("r22_down_foh_low", null, () -> cam(-341, 207.6, -310, -90, -4), 60);
        add("r22_down_foh_high", null, () -> LightClient.beamQuality = 2, 80);
        add("r22_down_side_high", null, () -> cam(-321, 206.5, -336, -45, -3), 60);
        add("r22_down_deck_high", null, () -> cam(-312, 209, -322, -60, 25), 60);
        add("r22_down_deck_low", null, () -> LightClient.beamQuality = 0, 80);
        // 3. washes at full zoom and full focus (picture 2, video at 45 s and 87-105 s)
        add("-r22 wash", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) { boolean spot = p.profile().isSpot();
                aim(d, p.fid, spot ? -300 : -330, spot ? SY : SY + 4, p.z); set(d, new int[]{p.fid}, Attr.DIMMER, 1f);
                if (spot) rgb(d, new int[]{p.fid}, 1f, 0f, 0f); else rgb(d, new int[]{p.fid}, 1f, 1f, 1f);
                set(d, new int[]{p.fid}, Attr.ZOOM, 1f); set(d, new int[]{p.fid}, Attr.FOCUS, 1f); } },
            () -> { LightClient.beamQuality = 0; cam(-341, 207.6, -310, -90, -4); }, 120);
        add("r22_wash_zoom_foh_low", null, null, 10);
        add("r22_wash_zoom_side_low", null, () -> cam(-321, 206.5, -336, -45, -3), 60);
        add("r22_wash_zoom_under_low", null, () -> cam(-318, 206, -310, -90, -45), 60);
        add("r22_wash_zoom_under_high", null, () -> LightClient.beamQuality = 2, 80);
        add("r22_wash_zoom_side_high", null, () -> cam(-321, 206.5, -336, -45, -3), 60);
        add("r22_wash_zoom_foh_high", null, () -> cam(-341, 207.6, -310, -90, -4), 60);
        add("-r22 red wash zoom", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (!p.profile().isSpot()) { aim(d, p.fid, -300, SY, p.z); set(d, new int[]{p.fid}, Attr.DIMMER, 1f);
                rgb(d, new int[]{p.fid}, 1f, 0f, 0f); set(d, new int[]{p.fid}, Attr.ZOOM, 1f); set(d, new int[]{p.fid}, Attr.FOCUS, 1f); } },
            () -> { LightClient.beamQuality = 0; cam(-341, 207.6, -310, -90, -4); }, 120);
        add("r22_redwash_zoom_foh_low", null, null, 10);
        add("r22_redwash_zoom_foh_high", null, () -> LightClient.beamQuality = 2, 80);
        // 4. the PULSESTRIKE M row alone, white, aimed at the audience: zoom and focus (picture 2: a white bar floating over the stage)
        add("-r22p ps", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M) { int[] one = {p.fid};
                aim(d, p.fid, -345, SY + 1, p.z); set(d, one, Attr.DIMMER, 1f); set(d, one, Attr.WHITE, 1f); rgb(d, one, 1f, 1f, 1f);
                set(d, one, Attr.ZOOM, 1f); set(d, one, Attr.FOCUS, 1f); } },
            () -> { LightClient.beamQuality = 2; cam(-341, 207.6, -310, -90, -2); }, 120);
        add("r22p_ps_zoom1_focus1_foh_high", null, null, 10);
        add("r22p_ps_zoom1_focus1_side_high", null, () -> cam(-321, 206.5, -336, -45, -3), 60);
        add("r22p_ps_zoom1_focus1_foh_low", null, () -> { LightClient.beamQuality = 0; cam(-341, 207.6, -310, -90, -2); }, 80);
        add("-r22p ps focus0", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M) set(d, new int[]{p.fid}, Attr.FOCUS, 0f); },
            () -> LightClient.beamQuality = 2, 60);
        add("r22p_ps_zoom1_focus0_foh_high", null, null, 10);
        add("-r22p ps zoom0", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M) set(d, new int[]{p.fid}, Attr.ZOOM, 0f); }, null, 60);
        add("r22p_ps_zoom0_focus0_foh_high", null, null, 10);
        // 5. the LUMORA W19 alone, white, full zoom, aimed at the audience and seen from under the rig (video at 87-105 s)
        add("-r22p w19", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.LUMORA_W19) { int[] one = {p.fid};
                aim(d, p.fid, -335, SY, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 1f, 1f); set(d, one, Attr.ZOOM, 1f); } },
            () -> cam(-341, 207.6, -310, -90, -2), 120);
        add("r22p_w19_zoom1_foh_high", null, null, 10);
        add("r22p_w19_zoom1_under_high", null, () -> cam(-312, 206, -310, -90, -35), 60);
        add("r22p_w19_zoom1_side_high", null, () -> cam(-321, 206.5, -336, -45, -3), 60);
        add("r22p_w19_zoom1_under_low", null, () -> { LightClient.beamQuality = 0; cam(-312, 206, -310, -90, -35); }, 80);
        add("r22p_w19_zoom1_side_low", null, () -> cam(-321, 206.5, -336, -45, -3), 60);
        // 6. strobe at switch-on (author, 21 h 50): the same lit rig captured 8 times one tick apart, bloom on — must not change
        add("-r22p still", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) { int[] one = {p.fid}; aim(d, p.fid, -320, SY, p.z); set(d, one, Attr.DIMMER, 1f);
                rgb(d, one, 0.2f, 0.4f, 1f); set(d, one, Attr.ZOOM, 0.5f); } },
            () -> { LightClient.beamQuality = 2; LightClient.bloom = 1f; cam(-341, 207.6, -310, -90, -4); }, 120);
        for (int k = 0; k < 8; k++) add("r22p_still_" + k, null, null, 1);
        // 7. the new gobo wheels: a PHANTOM S1, a PROFILE P1 and a RAZOR B1 on the audience floor, each on its own patch of a white wall
        add("-r22g setup", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d); ServerLevel l = level();
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -338, -310);
            zoneY = y;
            fill(l, -326, y, -324, -326, y + 9, -296, "minecraft:white_concrete");
            Block[] heads = {StageContent.PHANTOM_S1, StageContent.PROFILE_P1, StageContent.RAZOR_B1};
            java.util.List<Integer> f = new java.util.ArrayList<>();
            for (int k = 0; k < 3; k++) f.add(fixture(l, SCENE_DESK, heads[k], new BlockPos(-338, y, -319 + k * 9), false, Direction.EAST));
            zoneFids = ints(f);
            for (int k = 0; k < 3; k++) { int[] one = {zoneFids[k]}; aim(d, zoneFids[k], -326, y + 4.5, -319 + k * 9 + 0.5);
                set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 1f, 1f); set(d, one, Attr.ZOOM, 0.55f); set(d, one, Attr.FOCUS, 0.4f); } },
            () -> LightClient.beamQuality = 2, 120);
        add("-r22g cam", null, () -> cam(-345, zoneY + 4, -310, -90, -6), 80);
        for (int g = 1; g <= 8; g++) {
            final float v = g / 8f;
            add("r22g_gobo_" + g, () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, zoneFids, Attr.GOBO, v); }, null, 30);
        }
        add("-r22g wheel2", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, zoneFids, Attr.GOBO, 0f); set(d, new int[]{zoneFids[0]}, Attr.GOBO_2, 3 / 8f); }, null, 30);
        add("r22g_phantom_wheel2_3", null, null, 10);
        // 8. the rev. 21 measurement again (FOH, 62 fixtures white at zoom 0.45, no vsync, fixed quality), then low, then moving heads
        add("-r22perf setup", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                for (var p : d.show.patch) aim(d, p.fid, SX - 4 + (p.z - SZ) * 0.2, SY, p.z); white(d, all, 0.45f); },
            () -> { Minecraft mc = Minecraft.getInstance(); mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
                LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[1]; cam(-340, 208, -310, -90, -6); }, 200);
        add("#r22perf high", null, null, 100);
        add("#r22perf ultra", null, () -> LightClient.beamQuality = 3, 100);
        add("#r22perf medium", null, () -> LightClient.beamQuality = 1, 100);
        add("#r22perf low", null, () -> LightClient.beamQuality = 0, 100);
        add("#r22perf moving heads high", null, () -> { LightClient.beamQuality = 2; moving = true; }, 160);
        add("#r22perf moving heads low", null, () -> LightClient.beamQuality = 0, 160);
        add("-r22perf still", null, () -> { moving = false; LightClient.beamQuality = 2; }, 5);
        add("r22perf_foh_high", null, null, 40);
        // rev. 23 — the author's video of 22 h 18: LUMORA lit, then the VORTEX switched on (do the LUMORA dim?), then each family alone at 100 %
        }
        for (int qi : new int[]{0, 1, 2}) {
            final int QQ = qi;
        add("-r23 lumora q" + QQ, () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.LUMORA_W19) { int[] one = {p.fid};
                aim(d, p.fid, -318, SY, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 0.55f, 0.1f, 1f); set(d, one, Attr.ZOOM, 0.5f); } },
            () -> { LightClient.haze = LightClient.HAZE_PRESETS[2]; LightClient.autoPerformance = false; LightClient.beamQuality = QQ; cam(-341, 207.6, -310, -90, -4); }, 140);
        add("r23_lumora_alone_q" + QQ, null, null, 10);
        add("-r23 vortex on q" + QQ, () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.VORTEX_X1) { int[] one = {p.fid};
                aim(d, p.fid, -300, SY, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 1f, 1f); set(d, one, Attr.ZOOM, 0.3f); } }, null, 100);
        add("r23_lumora_plus_vortex_q" + QQ, null, null, 10);
        add("-r23 pulse on q" + QQ, () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M) { int[] one = {p.fid};
                aim(d, p.fid, -330, SY, p.z); set(d, one, Attr.DIMMER, 1f); set(d, one, Attr.WHITE, 1f); rgb(d, one, 1f, 1f, 1f); } }, null, 100);
        add("r23_lumora_plus_vortex_plus_pulse_q" + QQ, null, null, 10);
        }
        add("-r23 high", null, () -> LightClient.beamQuality = 2, 20);
        String[] fams = {"LUMORA_W19", "VORTEX_X1", "PULSESTRIKE_M"};
        for (String fam : fams) {
            for (int c = 0; c < 2; c++) {
                final boolean white = c == 0; final String fm = fam;
                add("-r23 " + fam + (white ? " white" : " purple"), () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                    for (var p : d.show.patch) if (p.profile().id.equals("csc_" + fm.toLowerCase())) { int[] one = {p.fid};
                        boolean spot = p.profile().isSpot();
                        aim(d, p.fid, spot ? -300 : -332, spot ? SY : SY + 3, p.z); set(d, one, Attr.DIMMER, 1f);
                        if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M) set(d, one, Attr.WHITE, white ? 1f : 0f);
                        if (white) rgb(d, one, 1f, 1f, 1f); else rgb(d, one, 0.55f, 0.1f, 1f); set(d, one, Attr.ZOOM, 0.6f); } }, null, 100);
                add("r23_full_" + fam.toLowerCase() + (white ? "_white" : "_purple"), null, null, 10);
            }
        }
        add("-r23 dark", () -> clear(desk(SCENE_DESK)), null, 20);
        if (r23only) return;
        add("#r22 fps high", null, null, 60);
        add("-r22 low again", null, () -> LightClient.beamQuality = 0, 80);
        add("#r22 fps low", null, null, 60);
    }

    // ------------------------------------------------------------------ rev. 24
    private static int[] blinders = new int[0];

    /**
     * Rev. 24 (11 November, 23 h): washes + gobos on the same floor (gobos took the colour of the floor), every fixture a little dimmer
     * (the rev. 23 family scenes again), blinders tilted with the angle tool, the rig from far away, the half-resolution haze.
     */
    private static void r24() {
        add("-r24 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST); mc.levelRenderer.allChanged();
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            LightClient.hazeResolution = 0; cam(-341, 207.6, -310, -90, -4); }, 160);
        add("-r24 desk", () -> {
            if (!(level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d)) { log("no desk at " + SCENE_DESK); all = new int[0]; return; }
            all = d.show.patch.stream().mapToInt(p -> p.fid).toArray();
            log("scene desk: " + all.length + " fixtures"); }, null, 10);
        // 1. washes + gobos on the audience floor (picture 1 of 23 h): LUMORA in cyan, VORTEX in magenta with a gobo, both on the floor
        String[][] mixes = {{"washes_only", "1", "0"}, {"gobos_only", "0", "1"}, {"washes_and_gobos", "1", "1"}};
        for (String[] mx : mixes) {
            final boolean w = mx[1].equals("1"), gb = mx[2].equals("1");
            add("-r24 mix " + mx[0], () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                double fy = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -310);
                for (var p : d.show.patch) { int[] one = {p.fid};
                    if (p.profile() == com.concertstage.light.core.FixtureProfile.LUMORA_W19 && w) {
                        aim(d, p.fid, -336, fy, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 0f, 0.85f, 1f); set(d, one, Attr.ZOOM, 0.7f); }
                    if (p.profile() == com.concertstage.light.core.FixtureProfile.VORTEX_X1 && gb) {
                        aim(d, p.fid, -336 + (p.fid % 3) * 2, fy, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 0.1f, 0.7f);
                        set(d, one, Attr.ZOOM, 0.25f); set(d, one, Attr.GOBO, 3 / 8f); set(d, one, Attr.FOCUS, 0.5f); } } },
                () -> cam(-318, 214, -336, 35, 24), 140);
            add("r24_mix_" + mx[0] + "_high", null, null, 10);
            add("-r24 mix low " + mx[0], null, () -> LightClient.beamQuality = 0, 80);
            add("r24_mix_" + mx[0] + "_low", null, null, 10);
            add("-r24 mix back " + mx[0], null, () -> LightClient.beamQuality = 2, 20);
        }
        // 2. brightness: the rev. 23 family scenes again (FOH, each family alone at 100 %, white then purple)
        String[] fams = {"LUMORA_W19", "VORTEX_X1", "PULSESTRIKE_M"};
        for (String fam : fams) for (int c = 0; c < 2; c++) {
            final boolean white = c == 0; final String fm = fam;
            add("-r24 " + fam + (white ? " white" : " purple"), () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                for (var p : d.show.patch) if (p.profile().id.equals("csc_" + fm.toLowerCase())) { int[] one = {p.fid};
                    boolean spot = p.profile().isSpot();
                    aim(d, p.fid, spot ? -300 : -332, spot ? SY : SY + 3, p.z); set(d, one, Attr.DIMMER, 1f);
                    if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M) set(d, one, Attr.WHITE, white ? 1f : 0f);
                    if (white) rgb(d, one, 1f, 1f, 1f); else rgb(d, one, 0.55f, 0.1f, 1f); set(d, one, Attr.ZOOM, 0.6f); } },
                () -> cam(-341, 207.6, -310, -90, -4), 100);
            add("r24_full_" + fam.toLowerCase() + (white ? "_white" : "_purple"), null, null, 10);
        }
        // 3. blinders on the audience floor, tilted with the angle tool (0, +30, -20 degrees), lit like a halogen blinder
        add("-r24 blinders", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d); ServerLevel l = level();
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -334, -310);
            zoneY = y;
            java.util.List<Integer> f = new java.util.ArrayList<>();
            float[] tilts = {0f, 30f, -20f};
            for (int k = 0; k < 3; k++) {
                BlockPos bp = new BlockPos(-334, y, -316 + k * 6);
                f.add(fixture(l, SCENE_DESK, StageContent.BLINDER_X4, bp, false, Direction.WEST));
                if (l.getBlockEntity(bp) instanceof com.concertstage.light.FixtureBlockEntity be) be.setManualTilt(tilts[k]);
            }
            blinders = ints(f);
            set(d, blinders, Attr.DIMMER, 1f); set(d, blinders, Attr.WHITE, 1f);
            set(d, blinders, Attr.CTO, com.concertstage.light.dmx.DmxMode.tungstenCto(1f)); },
            () -> cam(-346, zoneY + 3, -310, -90, 8), 120);
        add("r24_blinders_tilt_0_30_m20", null, null, 10);
        add("-r24 blinders side", null, () -> cam(-334, zoneY + 3, -326, 0, 4), 60);
        add("r24_blinders_side", null, null, 10);
        add("-r24 blinders dim", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, blinders, Attr.DIMMER, 0.25f);
            set(d, blinders, Attr.CTO, com.concertstage.light.dmx.DmxMode.tungstenCto(0.25f)); }, () -> cam(-346, zoneY + 3, -310, -90, 8), 60);
        add("r24_blinders_dimmed_25", null, null, 10);
        add("-r24 blinders gone", () -> { ServerLevel l = level();
            for (int k = 0; k < 3; k++) { BlockPos bp = new BlockPos(-334, zoneY, -316 + k * 6);
                if (l.getBlockState(bp).getBlock() instanceof FixtureBlock) l.setBlock(bp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); } },
            null, 20);
        // 4. the rig from far away (render distance 32 chunks): 200, 350 and 480 blocks behind FOH, every fixture lit
        add("-r24 far", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                for (var p : d.show.patch) { int[] one = {p.fid}; aim(d, p.fid, SX - 30 + (p.z - SZ) * 0.6, SY + 25, p.z + (p.z - SZ) * 0.8);
                    set(d, one, Attr.DIMMER, 1f); rgb(d, one, 0.3f + 0.7f * (p.fid % 2), 0.2f, 1f); set(d, one, Attr.ZOOM, 0.3f); } },
            () -> { Minecraft mc = Minecraft.getInstance(); mc.options.renderDistance().set(32); LightClient.beamQuality = 2; cam(-493, 230, -310, -90, -4); }, 400);
        add("r24_far_200", null, null, 10);
        add("-r24 far 350", null, () -> cam(-643, 240, -310, -90, -3), 400);
        add("r24_far_350", null, null, 10);
        add("-r24 far 480", null, () -> cam(-773, 250, -310, -90, -3), 400);
        add("r24_far_480", null, null, 10);
        add("-r24 near again", null, () -> { Minecraft.getInstance().options.renderDistance().set(10); cam(-341, 207.6, -310, -90, -4); }, 160);
        // 5. measurements (FOH, 62 fixtures white at zoom 0.45, no vsync, fixed quality, as rev. 21 / 22): haze full vs half resolution
        add("-r24perf setup", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                for (var p : d.show.patch) aim(d, p.fid, SX - 4 + (p.z - SZ) * 0.2, SY, p.z); white(d, all, 0.45f); },
            () -> { Minecraft mc = Minecraft.getInstance(); mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
                LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[1];
                LightClient.hazeResolution = 1; cam(-340, 208, -310, -90, -6); }, 200);
        String[] ql = {"low", "medium", "high", "ultra"};
        for (int q = 0; q < 4; q++) for (int hr : new int[]{1, 2}) {
            final int Q = q, HR = hr;
            add("-r24perf " + ql[q] + (hr == 1 ? " full" : " half"), null, () -> { LightClient.beamQuality = Q; LightClient.hazeResolution = HR; }, 100);
            add("#r24perf " + ql[q] + " haze " + (hr == 1 ? "full" : "half"), null, null, 5);
        }
        add("-r24perf washes", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.LUMORA_W19) { int[] one = {p.fid};
                aim(d, p.fid, -335, SY, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 1f, 1f); set(d, one, Attr.ZOOM, 1f); } },
            () -> { LightClient.beamQuality = 2; LightClient.hazeResolution = 1; cam(-312, 206, -310, -90, -35); }, 160);
        add("#r24perf W19 zoom max under the rig high haze full", null, null, 5);
        add("r24perf_w19_under_full", null, null, 5);
        add("-r24perf washes half", null, () -> LightClient.hazeResolution = 2, 100);
        add("#r24perf W19 zoom max under the rig high haze half", null, null, 5);
        add("r24perf_w19_under_half", null, null, 5);
        add("-r24perf moving", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                for (var p : d.show.patch) aim(d, p.fid, SX - 4 + (p.z - SZ) * 0.2, SY, p.z); white(d, all, 0.45f); },
            () -> { LightClient.hazeResolution = 1; cam(-340, 208, -310, -90, -6); moving = true; }, 160);
        add("#r24perf moving heads high full", null, null, 5);
        add("-r24perf moving half", null, () -> LightClient.hazeResolution = 2, 120);
        add("#r24perf moving heads high half", null, null, 5);
        add("-r24perf moving low", null, () -> { LightClient.beamQuality = 0; LightClient.hazeResolution = 0; }, 120);
        add("#r24perf moving heads low auto (half)", null, null, 5);
        add("-r24 end", null, () -> { moving = false; LightClient.hazeResolution = 0; LightClient.beamQuality = 2; }, 20);
        add("-r24 dark", () -> clear(desk(SCENE_DESK)), null, 20);
    }

    // ------------------------------------------------------------------ rev. 25
    private static void r25load() {
        add("-r25 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST); mc.levelRenderer.allChanged();
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            LightClient.hazeResolution = 0; LightClient.bloom = 1f; cam(-341, 207.6, -310, -90, -4); }, 160);
        add("-r25 desk", () -> {
            if (!(level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d)) { log("no desk at " + SCENE_DESK); all = new int[0]; return; }
            all = d.show.patch.stream().mapToInt(p -> p.fid).toArray(); }, null, 10);
    }

    /**
     * Rev. 25 (12 November, 13 h 45): blinders like the photo of a lit 4-lite blinder (yellow, four lamps seen), a violet LUMORA
     * strobing (no white), measurements of every pass (card and processor) and of the memory.
     */
    /**
     * Rev. 25d (12 November, 15 h 34): the PULSESTRIKE / FLASHCORE strobing turned into one big white blob over the stage seen from
     * FOH. The same view: every PULSESTRIKE M and FLASHCORE FX of the show, white, full, strobing (16 frames one tick apart), then
     * steady, then dark (to compare the light on the stage, truss and surroundings).
     */
    private static void r25d() {
        r25load();
        add("-r25d strobe", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M || p.profile() == com.concertstage.light.core.FixtureProfile.FLASHCORE_FX) {
                int[] one = {p.fid}; set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 1f, 1f);
                if (p.profile().has(Attr.WHITE)) set(d, one, Attr.WHITE, 1f);
                set(d, one, Attr.STROBE, 0.35f); } },
            () -> cam(-341, 207.6, -310, -90, -4), 120);
        for (int k = 0; k < 16; k++) add("r25d_strobe_" + String.format("%02d", k), null, null, 1);
        add("-r25d steady", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, all, Attr.STROBE, 0f); }, null, 40);
        add("r25d_steady", null, null, 10);
        add("-r25d dark", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, all, Attr.DIMMER, 0f); }, null, 40);
        add("r25d_dark", null, null, 10);
        add("-r25d count", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); int n = 0;
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M || p.profile() == com.concertstage.light.core.FixtureProfile.FLASHCORE_FX) n++;
            System.out.println("[CSC visual] r25d strobing fixtures: " + n); }, null, 2);
    }

    private static void r25() {
        r25load();
        // 1. blinders X2, X4, X8 on the audience floor facing the camera (6 blocks), full, 50 %, 15 %
        add("-r25 blinders", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d); ServerLevel l = level();
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -334, -310);
            zoneY = y;
            java.util.List<Integer> f = new java.util.ArrayList<>();
            Block[] kinds = {StageContent.BLINDER_X2, StageContent.BLINDER_X4, StageContent.BLINDER_X8};
            for (int k = 0; k < 3; k++) {
                BlockPos bp = new BlockPos(-334, y + 1, -314 + k * 4);
                l.setBlock(bp.below(), net.minecraft.world.level.block.Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
                f.add(fixture(l, SCENE_DESK, kinds[k], bp, false, Direction.WEST));
                if (l.getBlockEntity(bp) instanceof com.concertstage.light.FixtureBlockEntity be) be.setManualTilt(8f);
            }
            blinders = ints(f);
            set(d, blinders, Attr.DIMMER, 1f); set(d, blinders, Attr.WHITE, 1f); set(d, blinders, Attr.CTO, 0f); },
            () -> cam(-341, zoneY + 2.2, -310, -90, 6), 140);
        for (float lv : new float[] {1f, 0.5f, 0.15f}) {
            final float L = lv;
            add("-r25 blinders " + lv, () -> set(desk(SCENE_DESK), blinders, Attr.DIMMER, L), () -> cam(-341, zoneY + 2.2, -310, -90, 6), 40);
            add("r25_blinders_" + Math.round(lv * 100), null, null, 10);
        }
        add("-r25 blinders close", () -> set(desk(SCENE_DESK), blinders, Attr.DIMMER, 1f), () -> cam(-338.5, zoneY + 1.6, -310, -90, 4), 40);
        add("r25_blinders_close", null, null, 10);
        add("-r25 blinders dark", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, blinders, Attr.DIMMER, 0f); }, null, 20);
        // 2. violet LUMORA strobing: 16 frames one tick apart, from FOH, then the same steady
        add("-r25 strobe", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.LUMORA_W19 || p.profile() == com.concertstage.light.core.FixtureProfile.LUMORA_W7) {
                int[] one = {p.fid}; aim(d, p.fid, -320, SY, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 0.55f, 0.1f, 1f);
                set(d, one, Attr.ZOOM, 0.6f); set(d, one, Attr.STROBE, 0.35f); } },
            () -> cam(-341, 207.6, -310, -90, -4), 120);
        for (int k = 0; k < 16; k++) add("r25_strobe_violet_" + String.format("%02d", k), null, null, 1);
        add("-r25 steady", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, all, Attr.STROBE, 0f); }, null, 40);
        add("r25_steady_violet", null, null, 10);
        // 3. measurements (as rev. 24), with the cost of each pass and the memory
        add("-r25perf setup", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
                for (var p : d.show.patch) aim(d, p.fid, SX - 4 + (p.z - SZ) * 0.2, SY, p.z); white(d, all, 0.45f); },
            () -> { Minecraft mc = Minecraft.getInstance(); mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
                LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[1];
                LightClient.hazeResolution = 0; cam(-340, 208, -310, -90, -6); }, 200);
        String[] ql = {"low", "medium", "high", "ultra"};
        for (int q = 0; q < 4; q++) {
            final int Q = q;
            add("-r25perf " + ql[q], null, () -> LightClient.beamQuality = Q, 120);
            add("#r25perf static " + ql[q], null, null, 5);
        }
        add("-r25perf moving", null, () -> { LightClient.beamQuality = 2; moving = true; }, 160);
        add("#r25perf moving high", null, null, 5);
        add("-r25perf moving low", null, () -> LightClient.beamQuality = 0, 160);
        add("#r25perf moving low", null, null, 5);
        add("-r25perf washes", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            for (var p : d.show.patch) if (p.profile() == com.concertstage.light.core.FixtureProfile.LUMORA_W19) { int[] one = {p.fid};
                aim(d, p.fid, -335, SY, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 1f, 1f); set(d, one, Attr.ZOOM, 1f); } },
            () -> { moving = false; LightClient.beamQuality = 2; cam(-312, 206, -310, -90, -35); }, 160);
        add("#r25perf W19 under the rig high", null, null, 5);
        add("-r25perf gc", null, () -> { System.gc(); }, 60);
        add("#r25perf memory after gc", null, null, 5);
        add("-r25 end", null, () -> { moving = false; LightClient.beamQuality = 2; }, 20);
        add("-r25 gone", () -> { ServerLevel l = level(); clear(desk(SCENE_DESK));
            for (int k = 0; k < 3; k++) { BlockPos bp = new BlockPos(-334, zoneY + 1, -314 + k * 4);
                if (l.getBlockState(bp).getBlock() instanceof FixtureBlock) l.setBlock(bp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                l.setBlock(bp.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); } }, null, 20);
    }

    // ------------------------------------------------------------------ 1.3.0 LiveVision & Dynamic Truss (tests A-D)
    private static final int VX = 2000, VZ = 0;
    private static int vy = -60;                 // flat world: first air block
    private static BlockPos vProc, vPtz, vCrane;

    private static String lvStats() {
        return String.join(" ", com.concertstage.client.vision.VisionStats.lines()) + " | fps " + Minecraft.getInstance().getFps()
            + " | feeds " + com.concertstage.client.vision.CameraFeeds.status();
    }

    private static com.concertstage.vision.WallData walls() { return com.concertstage.vision.WallData.get(level()); }

    private static void logWalls(String at) {
        StringBuilder sb = new StringBuilder("[v13] " + at + ": walls " + walls().walls.size());
        for (var w : walls().walls.values()) sb.append(" | ").append(w.name).append(' ').append(w.width()).append('x').append(w.height()).append(" panels ").append(w.count);
        log(sb.toString());
    }

    private static com.concertstage.vision.VisionProcessorBlockEntity proc() {
        return (com.concertstage.vision.VisionProcessorBlockEntity) level().getBlockEntity(vProc);
    }

    private static void program(int out, String src) {
        var be = proc();
        be.cut(out, src, level().getGameTime());
    }

    private static BlockPos camera(ServerLevel l, Block b, BlockPos p, Direction f, int number, String name) {
        l.setBlock(p, b.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, f), 3);
        var be = (com.concertstage.vision.CameraBlockEntity) l.getBlockEntity(p);
        be.number = number; be.name = name; be.changed();
        return p;
    }


    // ------------------------------------------------------------------ rev. 1: fixtures and beams in the camera images, laser lines
    private static BlockPos feedCam;
    private static int[] lasers = new int[0];
    private static BlockPos[] laserPos = new BlockPos[0];
    private static int groundY = 203;

    /** Saves the last image of the test camera (its frame buffer texture) as a PNG. */
    private static void feedShot(String name) {
        var f = com.concertstage.client.vision.CameraFeeds.frame(feedCam);
        if (f == null) { log("[v131] " + name + ": no camera image (" + com.concertstage.client.vision.CameraFeeds.status() + ")"); return; }
        com.mojang.blaze3d.systems.RenderSystem.bindTexture(f.texture());
        int w = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
        int h = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
        try (NativeImage img = new NativeImage(w, h, false)) {
            img.downloadTexture(0, false);
            if (f.flipV()) img.flipY();
            img.writeToFile(out.resolve(name + ".png"));
        } catch (Exception e) {
            log("[v131] " + name + " save failed " + e);
        }
        log("[v131] " + name + " " + w + "x" + h + " | " + lvStats());
    }

    private static void lasersOn(float pattern, float size, float speed) {
        LumicoreBlockEntity d = desk(SCENE_DESK);
        float[][] c = {{0f, 1f, 0f}, {1f, 0f, 0f}, {0.1f, 0.3f, 1f}};
        for (int k = 0; k < lasers.length; k++) { int[] one = {lasers[k]};
            set(d, one, Attr.DIMMER, 1f); set(d, one, Attr.DOUSER, 1f); rgb(d, one, c[k][0], c[k][1], c[k][2]);
            set(d, one, Attr.LASER_PATTERN, pattern); set(d, one, Attr.LASER_SIZE, size); set(d, one, Attr.LASER_SPEED, speed); }
    }

    private static void logLaser(String at) {
        if (laserPos.length == 0 || !(Minecraft.getInstance().level.getBlockEntity(laserPos[0]) instanceof FixtureBlockEntity fe)) return;
        StringBuilder sb = new StringBuilder("[v131] " + at + " laser 0: lines " + fe.laserCount + " lengths");
        for (int i = 0; i < fe.laserCount; i++) sb.append(String.format(java.util.Locale.ROOT, " %.1f%s", fe.laserLen[i], fe.laserHit[i] ? "*" : ""));
        log(sb.toString());
    }

    private static void v131() {
        add("-v131 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED);
            cam(-341, 207.6, -310, -90, -4); }, 160);
        add("-v131 desk", () -> {
            if (!(level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d)) { log("no desk at " + SCENE_DESK); all = new int[0]; return; }
            all = d.show.patch.stream().mapToInt(p -> p.fid).toArray();
            log("[v131] scene desk: " + all.length + " fixtures"); }, null, 10);
        // A. a PTZ camera in the audience looking at the stage, the rig lit
        add("-v131 rig", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); ServerLevel l = level(); clear(d);
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -300);
            feedCam = camera(l, StageContent.CAM_PTZ_P1, new BlockPos(-336, groundY, -300), Direction.EAST, 9, "V131");
            log("[v131] camera at " + feedCam);
            float[][] cols = {{1f, 0.1f, 0.6f}, {0.2f, 0.5f, 1f}, {1f, 0.6f, 0.1f}, {1f, 1f, 1f}};
            int i = 0;
            for (var p : d.show.patch) { int[] one = {p.fid}; float[] c = cols[i++ % cols.length];
                aim(d, p.fid, -318 + (p.z - SZ) * 0.15, SY - 1, p.z + 2); set(d, one, Attr.DIMMER, 1f); rgb(d, one, c[0], c[1], c[2]); set(d, one, Attr.ZOOM, 0.35f); } },
            () -> cam(-337, 207, -297, -90, -6), 160);
        add("v131_main_rig", null, null, 10);
        add("-v131 feed wide", () -> ((com.concertstage.vision.CameraBlockEntity) level().getBlockEntity(feedCam)).moveTo(new Float[] {0f, 0f, 0f, null, null}, level().getGameTime()),
            null, 80);
        add("-v131 feed shot wide", null, () -> feedShot("v131_feed_rig_wide"), 5);
        add("-v131 feed tilt+", () -> ((com.concertstage.vision.CameraBlockEntity) level().getBlockEntity(feedCam)).moveTo(new Float[] {0f, 12f, 0.15f, null, null}, level().getGameTime()),
            null, 80);
        add("-v131 feed shot tilt+", null, () -> feedShot("v131_feed_rig_tilt_p12"), 5);
        add("-v131 feed tilt-", () -> ((com.concertstage.vision.CameraBlockEntity) level().getBlockEntity(feedCam)).moveTo(new Float[] {0f, -12f, 0.15f, null, null}, level().getGameTime()),
            null, 80);
        add("-v131 feed shot tilt-", null, () -> feedShot("v131_feed_rig_tilt_m12"), 5);
        add("-v131 feed nolight", null, () -> com.concertstage.client.vision.VisionSettings.feedBeams = false, 40);
        add("-v131 feed shot nolight", null, () -> feedShot("v131_feed_rig_bodies_only"), 5);
        add("-v131 feed light", null, () -> com.concertstage.client.vision.VisionSettings.feedBeams = true, 20);
        // frame rate: no camera image, then one image with the light of the rig, then without it
        add("-v131 perf nofeed", null, () -> { feedCam0 = feedCam; feedCam = null; cam(-341, 207.6, -310, -90, -4); }, 240);
        add("#v131perf no camera image", null, null, 5);
        add("-v131 perf feed", null, () -> feedCam = feedCam0, 240);
        add("#v131perf camera image with fixtures and light", null, () -> log("[v131] perf feed " + lvStats()), 5);
        add("-v131 perf feed nolight", null, () -> com.concertstage.client.vision.VisionSettings.feedBeams = false, 240);
        add("#v131perf camera image fixtures without light", null, () -> log("[v131] perf feed nolight " + lvStats()), 5);
        add("-v131 perf end", null, () -> com.concertstage.client.vision.VisionSettings.feedBeams = true, 5);
        // B. three LASERCORE L1 on the audience floor shooting at the stage: every figure, before (legacy cone) and after (lines)
        add("-v131 lasers", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); ServerLevel l = level(); clear(d);
            java.util.List<Integer> f = new java.util.ArrayList<>();
            laserPos = new BlockPos[3];
            for (int k = 0; k < 3; k++) {
                int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -330, -316 + k * 6);
                laserPos[k] = new BlockPos(-330, gy, -316 + k * 6);
                f.add(fixture(l, SCENE_DESK, StageContent.LASERCORE_L1, laserPos[k], false, Direction.EAST));
            }
            lasers = ints(f);
            log("[v131] lasers " + java.util.Arrays.toString(lasers) + " at " + java.util.Arrays.toString(laserPos));
            lasersOn(0.3f, 0.6f, 0.2f); },
            () -> { com.concertstage.client.light.FixtureRenderer.legacyLaser = false; cam(-345, groundY + 3, -310, -90, -4); }, 120);
        String[] names = {"dot", "fan", "circle", "cross", "square"};
        for (int pt = 0; pt < 5; pt++) {
            final float pv = (pt + 0.5f) / 5f;
            final String nm = names[pt];
            for (boolean legacy : new boolean[] {true, false}) {
                final boolean lg = legacy;
                String tag = legacy ? "before" : "after";
                add("-v131 laser " + nm + " " + tag, () -> lasersOn(pv, 0.6f, 0.2f),
                    () -> { com.concertstage.client.light.FixtureRenderer.legacyLaser = lg; cam(-345, groundY + 3, -310, -90, -4); }, 60);
                add("v131_laser_" + nm + "_" + tag + "_back", null, () -> logLaser(nm + " " + tag), 10);
                add("v131_laser_" + nm + "_" + tag + "_side", null, () -> cam(-338, groundY + 3, -328, -51, -6), 50);
            }
        }
        add("-v131 laser tilt", () -> { ServerLevel l = level(); for (BlockPos p : laserPos) ((FixtureBlockEntity) l.getBlockEntity(p)).setManualTilt(15f); lasersOn(0.3f, 0.6f, 0.2f); },
            () -> { com.concertstage.client.light.FixtureRenderer.legacyLaser = false; cam(-345, groundY + 3, -310, -90, -4); }, 60);
        add("v131_laser_fan_tilt15_back", null, () -> logLaser("fan tilt 15"), 10);
        add("v131_laser_fan_tilt15_side", null, () -> cam(-338, groundY + 3, -328, -51, -6), 50);
        add("-v131 laser tiltm", () -> { ServerLevel l = level(); for (BlockPos p : laserPos) ((FixtureBlockEntity) l.getBlockEntity(p)).setManualTilt(-15f); }, null, 60);
        add("v131_laser_fan_tiltm15_side", null, () -> logLaser("fan tilt -15"), 10);
        // the lasers in the camera image
        add("-v131 laser feed", () -> { ServerLevel l = level(); for (BlockPos p : laserPos) ((FixtureBlockEntity) l.getBlockEntity(p)).setManualTilt(0f); lasersOn(0.5f, 0.6f, 0.2f);
                ((com.concertstage.vision.CameraBlockEntity) l.getBlockEntity(feedCam)).moveTo(new Float[] {0f, 0f, 0f, null, null}, l.getGameTime()); },
            () -> cam(-345, groundY + 3, -310, -90, -4), 80);
        add("-v131 laser feed shot", null, () -> feedShot("v131_feed_lasers_circle"), 5);
        add("#v131perf lasers circle lines", null, null, 5);
        add("-v131 laser legacy perf", null, () -> com.concertstage.client.light.FixtureRenderer.legacyLaser = true, 120);
        add("#v131perf lasers circle legacy cone", null, null, 5);
        // clean up: the lasers and the camera go away, the desk is cleared
        add("-v131 gone", () -> { ServerLevel l = level(); clear(desk(SCENE_DESK));
            for (BlockPos p : laserPos) if (l.getBlockState(p).getBlock() instanceof FixtureBlock) l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            if (feedCam != null) l.setBlock(feedCam, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); },
            () -> { com.concertstage.client.light.FixtureRenderer.legacyLaser = false; feedCam = null; }, 20);
    }

    private static BlockPos feedCam0;

    // ------------------------------------------------------------------ 1.3.0 rev. 2
    private static volatile boolean nullShader;
    private static BlockPos nodePos;
    private static BlockPos[] v2Pos;
    private static int[] v2Fid;
    private static net.minecraft.world.item.ItemStack card;
    private static BlockPos[] v2Links;

    /** One ArtDmx packet (Art-Net 4) to this computer, Port-Address {@code pa}: what an external desk sends. */
    private static void artDmx(int pa, byte[] dmx) {
        try (java.net.DatagramSocket so = new java.net.DatagramSocket()) {
            byte[] b = new byte[18 + 512];
            byte[] id = "Art-Net\0".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(id, 0, b, 0, 8);
            b[8] = 0x00; b[9] = 0x50;                       // OpDmx, little endian
            b[10] = 0; b[11] = 14;                          // protocol 14
            b[12] = 0; b[13] = 0;                           // sequence off, physical 0
            b[14] = (byte) (pa & 0xFF); b[15] = (byte) (pa >> 8 & 0x7F);
            b[16] = 0x02; b[17] = 0x00;                     // 512 slots, big endian
            System.arraycopy(dmx, 0, b, 18, Math.min(512, dmx.length));
            so.send(new java.net.DatagramPacket(b, b.length, java.net.InetAddress.getLoopbackAddress(), 6454));
        } catch (IOException e) {
            log("[v132] ArtDmx failed " + e);
        }
    }

    private static final byte[] UNI1 = new byte[512];

    private static void slot(int address, int... values) {
        for (int i = 0; i < values.length; i++) UNI1[address - 1 + i] = (byte) values[i];
    }

    /** The client copy of the node: what each fixture shows now (the engine output the renderer uses). */
    private static void logNode(String at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(nodePos) instanceof LumicoreBlockEntity d)) { log("[v132] " + at + ": no node on the client"); return; }
        StringBuilder sb = new StringBuilder("[v132] " + at + ": control " + d.live.control + ", patch");
        for (var p : d.show.patch) sb.append(" #").append(p.fid).append(' ').append(p.type).append(' ').append(p.universe).append('.').append(p.address)
            .append(' ').append(com.concertstage.light.dmx.DmxMode.NAMES[p.dmxMode]);
        log(sb.toString());
        var outs = d.lastOutputs();
        for (var p : d.show.patch) {
            var o = outs.get(p.fid);
            if (o == null) { log("[v132]   #" + p.fid + " no output"); continue; }
            var pr = p.profile();
            StringBuilder z = new StringBuilder(String.format(java.util.Locale.ROOT, "[v132]   #%d %s dim %.2f beam %.2f %.2f %.2f", p.fid, pr.name,
                o.get(Attr.DIMMER), o.beamR, o.beamG, o.beamB));
            if (pr.has(Attr.IRIS)) z.append(String.format(java.util.Locale.ROOT, " iris %.2f strobe %.2f", o.get(Attr.IRIS), o.get(Attr.STROBE)));
            if (pr.hasSections()) {
                for (int k = 0; k < 3; k++) {
                    // first cell of each zone section: r g b level
                    for (int c = 0; c < pr.zones; c++) if (pr.section(c) == k) {
                        z.append(String.format(java.util.Locale.ROOT, " | %s %.2f %.2f %.2f L%.2f", com.concertstage.light.core.FixtureProfile.SECTION_NAMES[k],
                            o.zones[c * 4], o.zones[c * 4 + 1], o.zones[c * 4 + 2], o.zones[c * 4 + 3]));
                        break;
                    }
                }
            }
            log(z.toString());
        }
    }

    private static void v132() {
        add("-v132 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED);
            cam(-341, 207.6, -310, -90, -4); }, 160);
        // ---- A. camera images: the own terrain path (Embeddium / Sodium) and the null shader ImmediatelyFast leaves
        add("-v132 rig", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); ServerLevel l = level(); clear(d);
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -300);
            feedCam = camera(l, StageContent.CAM_PTZ_P1, new BlockPos(-336, groundY, -300), Direction.EAST, 9, "V132");
            float[][] cols = {{1f, 0.1f, 0.6f}, {0.2f, 0.5f, 1f}, {1f, 0.6f, 0.1f}, {1f, 1f, 1f}};
            int i = 0;
            for (var p : d.show.patch) { int[] one = {p.fid}; float[] c = cols[i++ % cols.length];
                aim(d, p.fid, -318 + (p.z - SZ) * 0.15, SY - 1, p.z + 2); set(d, one, Attr.DIMMER, 1f); rgb(d, one, c[0], c[1], c[2]); set(d, one, Attr.ZOOM, 0.35f); }
            log("[v132] camera at " + feedCam); },
            () -> cam(-337, 207, -297, -90, -6), 160);
        add("-v132 feed vanilla", () -> ((com.concertstage.vision.CameraBlockEntity) level().getBlockEntity(feedCam)).moveTo(new Float[] {0f, 0f, 0f, null, null}, level().getGameTime()),
            null, 80);
        add("-v132 feed shot vanilla", null, () -> { feedShot("v132_feed_vanilla_terrain"); log("[v132] vanilla: chunks " + com.concertstage.client.vision.VisionStats.feedChunks); }, 5);
        add("-v132 own terrain", null, () -> System.setProperty("csc.feed.ownTerrain", "true"), 120);
        add("-v132 feed shot own", null, () -> { feedShot("v132_feed_own_terrain"); log("[v132] own terrain: sections " + com.concertstage.client.vision.VisionStats.feedChunks
            + " (negative = CSC's own section meshes), status " + com.concertstage.client.vision.CameraFeeds.status()); }, 5);
        add("#v132perf camera image own terrain", null, () -> log("[v132] perf own terrain " + lvStats()), 5);
        add("-v132 null shader", null, () -> nullShader = true, 120);
        add("-v132 feed shot null shader", null, () -> { feedShot("v132_feed_null_shader"); log("[v132] null shader: status " + com.concertstage.client.vision.CameraFeeds.status()); }, 5);
        add("-v132 shader back", null, () -> { nullShader = false; System.clearProperty("csc.feed.ownTerrain"); }, 20);
        // ---- B. the Art-Net node, the configuration card, the external application (ArtDmx sent to this computer)
        add("-v132 node", () -> { ServerLevel l = level(); clear(desk(SCENE_DESK));
            ServerPlayer pl = player(server());
            nodePos = new BlockPos(-338, groundY, -306);
            l.setBlock(nodePos, StageContent.ARTNET_CONSOLE.defaultBlockState().setValue(com.concertstage.light.LumicoreBlock.FACING, Direction.WEST), 3);
            StageContent.ARTNET_CONSOLE.setPlacedBy(l, nodePos, l.getBlockState(nodePos), pl, new net.minecraft.world.item.ItemStack(StageContent.ARTNET_CONSOLE));
            Block[] kinds = {StageContent.PULSESTRIKE_M, StageContent.PULSESTRIKE_M, StageContent.FLASHCORE_FX, StageContent.VORTEX_X1, StageContent.RAZOR_B1, StageContent.PHANTOM_S1};
            v2Pos = new BlockPos[kinds.length];
            for (int k = 0; k < kinds.length; k++) {
                v2Pos[k] = new BlockPos(-332, groundY, -318 + k * 3);
                l.setBlock(v2Pos[k], kinds[k].defaultBlockState().setValue(FixtureBlock.FACING, Direction.WEST).setValue(FixtureBlock.HUNG, false), 3);
            }
            card = new net.minecraft.world.item.ItemStack(StageContent.CONFIG_CARD);
            com.concertstage.light.QuickPatch.link(card, nodePos);
            com.concertstage.light.QuickPatch.write(card, 1, 1, com.concertstage.light.dmx.DmxMode.COMPACT, true);
            for (BlockPos p : v2Pos) {
                com.concertstage.light.QuickPatch.cardOnFixture(l, pl, card, (FixtureBlockEntity) l.getBlockEntity(p));
                var c = com.concertstage.light.QuickPatch.card(card);
                log("[v132] card after " + l.getBlockState(p).getBlock().getDescriptionId() + ": next " + c.universe() + "." + c.address());
            }
            LumicoreBlockEntity node = (LumicoreBlockEntity) l.getBlockEntity(nodePos);
            log("[v132] node control " + node.live.control + " (2 = External), " + node.show.patch.size() + " fixtures");
            v2Fid = new int[v2Pos.length];
            for (int k = 0; k < v2Pos.length; k++) v2Fid[k] = ((FixtureBlockEntity) l.getBlockEntity(v2Pos[k])).fid(); },
            () -> cam(-344, groundY + 2.5, -310.5, -90, 4), 60);
        add("-v132 node log", null, () -> logNode("patched by the card"), 5);
        // PULSESTRIKE M / FLASHCORE FX Compact, two instances: tilt, zoom, LED bars dimmer R G B strobe, white bar dimmer strobe
        add("-v132 dmx red", null, () -> {
            java.util.Arrays.fill(UNI1, (byte) 0);
            var d = (LumicoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(nodePos);
            for (var p : d.show.patch) {
                String t = p.type;
                if (t.equals("csc_pulsestrike_m") || t.equals("csc_flashcore_fx"))
                    slot(p.address, 128, 128, 255, 255, 0, 0, 0, 255, 0);           // LED bars red, white bar full
                else if (t.equals("csc_vortex_x1")) slot(p.address, 128, 128, 255, 172);   // pan tilt dim, strobe/iris channel: iris 30 %
            }
            for (int k = 0; k < 30; k++) artDmx(1, UNI1); }, 40);
        add("-v132 dmx red log", null, () -> { artDmx(1, UNI1); logNode("LED bars red, white bar full, VORTEX iris 30 %"); }, 5);
        add("v132_node_bars_red_white_full", null, () -> artDmx(1, UNI1), 10);
        add("-v132 dmx blue", null, () -> {
            var d = (LumicoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(nodePos);
            for (var p : d.show.patch) {
                String t = p.type;
                if (t.equals("csc_pulsestrike_m") || t.equals("csc_flashcore_fx")) slot(p.address, 128, 128, 255, 0, 0, 255, 0, 0, 0);   // bars blue, white bar off
                else if (t.equals("csc_vortex_x1")) slot(p.address, 128, 128, 255, 60);          // strobe range: the iris must stay at 30 %
            }
            for (int k = 0; k < 20; k++) artDmx(1, UNI1); }, 40);
        add("-v132 dmx blue log", null, () -> { artDmx(1, UNI1); logNode("LED bars blue, white bar off, VORTEX strobe (iris held?)"); }, 5);
        add("v132_node_bars_blue_white_off", null, () -> artDmx(1, UNI1), 10);
        add("-v132 dmx open", null, () -> {
            var d = (LumicoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(nodePos);
            for (var p : d.show.patch) if (p.type.equals("csc_vortex_x1")) slot(p.address, 128, 128, 255, 0);   // shutter open
            for (int k = 0; k < 10; k++) artDmx(1, UNI1); }, 20);
        add("-v132 dmx open log", null, () -> { artDmx(1, UNI1); logNode("VORTEX shutter open (iris still held at 30 %?)"); }, 5);
        // ---- C. the screens
        add("-v132 screen node", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.light.ArtNetConsoleScreen(nodePos)), 60);
        add("v132_screen_artnet_node", null, null, 5);
        add("-v132 screen card", () -> player(server()).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, card.copy()),
            () -> Minecraft.getInstance().setScreen(null), 20);
        add("-v132 screen card open", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.light.ConfigCardScreen(net.minecraft.world.InteractionHand.MAIN_HAND)), 30);
        add("v132_screen_config_card", null, null, 5);
        add("-v132 screen fixture", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.light.FixturePatchScreen(v2Pos[3])), 30);
        add("v132_screen_fixture_patch", null, null, 5);
        add("-v132 screen close", () -> player(server()).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY),
            () -> Minecraft.getInstance().setScreen(null), 20);
        // ---- D. gobo rotation (desk control): RAZOR B1 wheel 1, PHANTOM S1 wheel 2
        add("-v132 gobo", () -> { ServerLevel l = level(); LumicoreBlockEntity n = (LumicoreBlockEntity) l.getBlockEntity(nodePos);
            com.concertstage.light.ExternalControl.class.getName();
            cmd(n, Commands.op(Commands.CONTROL_MODE).varInt(com.concertstage.light.core.Live.INTERNAL).toBytes());
            int razor = v2Fid[4], phantom = v2Fid[5];
            for (int f : new int[] {razor, phantom}) { int[] one = {f}; set(n, one, Attr.DIMMER, 1f); rgb(n, one, 1, 1, 1); set(n, one, Attr.ZOOM, 0.3f);
                aim(n, f, -300, groundY + 6, v2Pos[f == razor ? 4 : 5].getZ()); }
            set(n, new int[] {razor}, Attr.GOBO, 3 / 8f); set(n, new int[] {razor}, Attr.GOBO_ROT, 0.85f);
            set(n, new int[] {phantom}, Attr.GOBO, 0f); set(n, new int[] {phantom}, Attr.GOBO_2, 2 / 8f); set(n, new int[] {phantom}, Attr.GOBO_2_ROT, 0.15f); },
            () -> cam(-326, groundY + 3, -322, -40, 10), 80);
        add("-v132 gobo angle 1", null, () -> logGobo("t0"), 5);
        add("v132_gobo_rotation_t0", null, null, 25);
        add("-v132 gobo angle 2", null, () -> logGobo("t+1.25 s"), 5);
        add("v132_gobo_rotation_t1", null, null, 5);
        // ---- E. the blinding radius of a BLINDER X4 seen from the audience, by day
        add("-v132 blinder", () -> { ServerLevel l = level(); LumicoreBlockEntity n = (LumicoreBlockEntity) l.getBlockEntity(nodePos);
            BlockPos b = new BlockPos(-332, groundY + 1, -300);
            l.setBlock(b.below(), net.minecraft.world.level.block.Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
            l.setBlock(b, StageContent.BLINDER_X4.defaultBlockState().setValue(FixtureBlock.FACING, Direction.WEST).setValue(FixtureBlock.HUNG, false), 3);
            FixtureBlockEntity fe = (FixtureBlockEntity) l.getBlockEntity(b);
            LightServer.patch(l, n, fe);
            set(n, new int[] {fe.fid()}, Attr.DIMMER, 1f);
            run(server(), "time set 6000"); },
            () -> cam(-346, groundY + 2, -300, -90, 0), 80);
        add("v132_blinder_day", null, null, 5);
        add("-v132 blinder night", () -> run(server(), "time set 18000"), null, 60);
        add("v132_blinder_night", null, null, 5);
        // ---- F. patch links (reported 13 November): console broken / replaced, unpatch, fixture numbers given again
        add("-v132 links", () -> { ServerLevel l = level();
            BlockPos dp = new BlockPos(-338, groundY, -322);
            l.setBlock(dp, StageContent.LUMICORE.defaultBlockState(), 3);
            LumicoreBlockEntity d1 = (LumicoreBlockEntity) l.getBlockEntity(dp);
            BlockPos[] fx = {new BlockPos(-334, groundY + 4, -324), new BlockPos(-334, groundY + 4, -326), new BlockPos(-334, groundY + 4, -328)};
            for (BlockPos b : fx) { l.setBlock(b.above(), net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), 3);
                l.setBlock(b, StageContent.VORTEX_X1.defaultBlockState().setValue(FixtureBlock.HUNG, true), 3); }
            FixtureBlockEntity a = (FixtureBlockEntity) l.getBlockEntity(fx[0]), b = (FixtureBlockEntity) l.getBlockEntity(fx[1]), c = (FixtureBlockEntity) l.getBlockEntity(fx[2]);
            for (FixtureBlockEntity f : new FixtureBlockEntity[] {a, b, c}) LightServer.patch(l, d1, f);
            log("[v132] links: 3 patched, fids " + a.fid() + " " + b.fid() + " " + c.fid() + ", desk id " + Long.toHexString(d1.deskId));
            long oldId = d1.deskId;
            l.setBlock(dp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            log("[v132] links: desk broken -> a linked " + (a.console() != null) + ", b " + (b.console() != null) + ", c " + (c.console() != null) + " (all false expected)");
            l.setBlock(dp, StageContent.LUMICORE.defaultBlockState(), 3);
            LumicoreBlockEntity d2 = (LumicoreBlockEntity) l.getBlockEntity(dp);
            a.patch(dp, 1, oldId);              // what a fixture in an unloaded chunk keeps from the broken desk
            LightServer.patch(l, d2, c);        // the new desk gives fixture number 1 to c
            log("[v132] links: new desk id " + Long.toHexString(d2.deskId) + ", c fid " + c.fid() + ", stale a (fid 1, old id) really patched: "
                + (LightServer.linkedDesk(l, a) != null) + " (false expected), c: " + (LightServer.linkedDesk(l, c) != null) + " (true expected)");
            v2Links = new BlockPos[] {dp, fx[0], fx[1], fx[2]}; },
            () -> cam(-340, groundY + 3, -326, -90, -10), 70);
        add("-v132 links client", () -> { ServerLevel l = level();
            FixtureBlockEntity a = (FixtureBlockEntity) l.getBlockEntity(v2Links[1]), b = (FixtureBlockEntity) l.getBlockEntity(v2Links[2]), c = (FixtureBlockEntity) l.getBlockEntity(v2Links[3]);
            LumicoreBlockEntity d2 = (LumicoreBlockEntity) l.getBlockEntity(v2Links[0]);
            log("[v132] links after 3 s on the server: stale a console " + a.console() + " (null expected: dropped by checkLink)");
            boolean okB = LightServer.patch(l, d2, a), okA = LightServer.patch(l, d2, b);
            log("[v132] links: a and b patched again " + okB + " " + okA + ", fids a " + a.fid() + " b " + b.fid() + " c " + c.fid() + ", patch " + d2.show.patch.size());
            cmd(d2, Commands.op(Commands.PATCH_REMOVE).varInt(a.fid()).toBytes());
            log("[v132] links: PATCH_REMOVE of a -> a console " + a.console() + " (null expected), b still " + (LightServer.linkedDesk(l, b) != null) + ", c still " + (LightServer.linkedDesk(l, c) != null));
            LightServer.patch(l, d2, a);
            log("[v132] links: a patched again, fid " + a.fid() + " (new number, not shared): b " + b.fid() + " c " + c.fid()); },
            () -> { Minecraft mc = Minecraft.getInstance();
                for (int k = 1; k <= 3; k++) if (mc.level.getBlockEntity(v2Links[k]) instanceof FixtureBlockEntity f)
                    log("[v132] links client: fixture " + k + " output " + (com.concertstage.client.light.LightClient.output(mc.level, f, 0f) != null)); }, 60);
        add("-v132 links client 2", null, () -> { Minecraft mc = Minecraft.getInstance();
            for (int k = 1; k <= 3; k++) if (mc.level.getBlockEntity(v2Links[k]) instanceof FixtureBlockEntity f)
                log("[v132] links client end: fixture " + k + " fid " + f.fid() + " output " + (com.concertstage.client.light.LightClient.output(mc.level, f, 0f) != null)
                    + " tilt " + String.format(java.util.Locale.ROOT, "%.1f", f.curTilt)); }, 5);
        add("v132_hung_heads_straight_down", null, null, 5);
        add("-v132 tilt math", () -> { LumicoreBlockEntity d2 = (LumicoreBlockEntity) level().getBlockEntity(v2Links[0]);
            for (var p : d2.show.patch) {
                float[] down = com.concertstage.light.core.Movement.aim(p, p.x + 0.5, p.y - 10, p.z + 0.5);
                float[] side = com.concertstage.light.core.Movement.aim(p, p.x + 10.5, p.y, p.z + 0.5);
                log(String.format(java.util.Locale.ROOT, "[v132] tilt: hung #%d aim down -> tilt %.3f (0.500 = DMX 128 expected), aim horizontal -> pan %.3f tilt %.3f (+-90 deg: 0.167 / 0.833)",
                    p.fid, down[1], side[0], side[1]));
            } }, null, 5);
        add("-v132 links gone", () -> { ServerLevel l = level();
            for (int k = 1; k <= 3; k++) { l.setBlock(v2Links[k], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                l.setBlock(v2Links[k].above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }
            l.setBlock(v2Links[0], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }, null, 10);
        add("-v132 gone", () -> { ServerLevel l = level();
            for (BlockPos p : v2Pos) l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(new BlockPos(-332, groundY + 1, -300), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(nodePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            if (feedCam != null) l.setBlock(feedCam, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); },
            () -> feedCam = null, 20);
    }

    // ------------------------------------------------------------------ 1.3.0 rev. 3
    private static com.concertstage.vision.WallData.Wall v3Wall;

    private static void feedLog(String at) {
        var a = com.concertstage.client.vision.CameraFeeds.actual(feedCam);
        log(String.format(java.util.Locale.ROOT, "[v133] %s: fps %d | image %dp %d i/s | frame without images %.2f ms (work %.2f ms), extra with one %.2f ms, budget %.2f ms/frame | %s",
            at, Minecraft.getInstance().getFps(), a[0], a[1], com.concertstage.client.vision.CameraFeeds.statBaseMs, com.concertstage.client.vision.CameraFeeds.statWorkMs,
            com.concertstage.client.vision.CameraFeeds.statExtraMs, com.concertstage.client.vision.CameraFeeds.statBudgetMs, com.concertstage.client.vision.CameraFeeds.status()));
    }

    private static void wallSet(float angle, int pivot, int quality, int fps) {
        var w = walls().walls.get(v3Wall.id);
        w.angle = angle; w.pivot = pivot; w.quality = quality; w.fps = fps;
        walls().setDirty();
        com.concertstage.vision.VisionNet.wallChanged(level(), w);
    }

    // ------------------------------------------------------------------ 1.3.0 rev. 4: the Professional Audio Expansion, the C6 portable
    private static final java.util.List<BlockPos> V134 = new java.util.ArrayList<>();

    private static com.concertstage.audio.SpeakerBlockEntity v4Array(ServerLevel l, BlockPos p, boolean hanging) {
        l.setBlock(p, StageContent.SPEAKER.defaultBlockState().setValue(com.concertstage.audio.SpeakerBlock.HANGING, hanging), 3);
        V134.add(p);
        return (com.concertstage.audio.SpeakerBlockEntity) l.getBlockEntity(p);
    }

    /** A sub stack, then a support standing on it, then cabinets on the support; logs where it all went. */
    private static void v4Stack(ServerLevel l, String name, BlockPos at, String sub, int subs, String frame, String cab, int cabs, float[] splays) {
        var s = v4Array(l, at, false);
        s.init(sub, -90, false);
        for (int i = 1; i < subs; i++) s.add(sub);
        String why = com.concertstage.audio.SpeakerFrameItem.standOn(l, s, com.concertstage.audio.SpeakerFrames.get(frame));
        var up = s.dependent();
        if (up != null) V134.add(up.getBlockPos());
        int added = 0;
        if (up != null) for (int i = 0; i < cabs; i++) if (up.add(cab)) added++;
        boolean angles = up != null && up.tryApplyAngles(up.yaw(), 0, splays);
        boolean moreSubs = s.add(sub);                                   // refused: something stands on them
        log(String.format(java.util.Locale.ROOT, "[v134] %s: %d x %s, %s -> %s, on it %d / %d x %s, angles %s, base %.3f, stack top %.3f, bottom of the first cabinet %.3f, another sub refused %s",
            name, s.cabinets().size(), sub, frame, why == null ? "placed" : why, added, cabs, cab, angles, up == null ? 0 : up.base(), s.stackTop(),
            up == null || up.poses().isEmpty() ? Double.NaN : up.getBlockPos().getY() + up.poses().get(0).y() - s.getBlockPos().getY(), !moreSubs));
    }

    private static void v134() {
        add("-v134 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            cam(-336, 207.6, -312, 90, 2); }, 160);
        add("-v134 rig", () -> { ServerLevel l = level(); V134.clear();
            l.setDayTime(6000);
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -350, -312);
            int g = groundY;
            for (int x = -356; x <= -344; x++) for (int y = g; y <= g + 12; y++) for (int z = -340; z <= -288; z++)
                l.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            v4Stack(l, "LA12 ground stack", new BlockPos(-350, g, -318), "csc_aurex_s218_f", 2, "csc_aurex_la12_ground_frame", "csc_aurex_la12", 4, new float[] {0, 1, 2, 3});
            v4Stack(l, "LA8 on one S218-F", new BlockPos(-350, g, -324), "csc_aurex_s218_f", 1, "csc_aurex_la8_ground_frame", "csc_aurex_la8", 2, new float[] {0, 2});
            v4Stack(l, "Orvane LX8 adapter", new BlockPos(-350, g, -310), "csc_orvane_sb28", 1, "csc_orvane_lx8_stack_adapter", "csc_orvane_lx8", 3, new float[] {0, 1, 2});
            v4Stack(l, "Veyra A1 adapter", new BlockPos(-350, g, -304), "csc_veyra_b2", 1, "csc_veyra_a1_stack_adapter", "csc_veyra_a1", 3, new float[] {0, 1, 2});
            v4Stack(l, "Noryx A2 adapter", new BlockPos(-350, g, -300), "csc_noryx_b1", 1, "csc_noryx_a2_stack_adapter", "csc_noryx_a2", 3, new float[] {0, 1, 2});
            v4Stack(l, "C6 portable", new BlockPos(-350, g, -296), "csc_aurex_b18p", 1, "csc_aurex_portable_pole", "csc_aurex_c6", 3, new float[] {0, 3});
            // flown LA8 under a beam, a LA12 ground frame on the floor
            BlockPos beam = new BlockPos(-350, g + 9, -331);
            l.setBlock(beam, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), 3); V134.add(beam);
            var fly = v4Array(l, beam.below(), true);
            fly.initFrame("csc_aurex_la8_fly_frame", -90);
            int n = 0;
            for (int i = 0; i < 6; i++) if (fly.add("csc_aurex_la8")) n++;
            boolean a = fly.tryApplyAngles(-90, -3, new float[] {0, 1, 2, 3, 5, 7});
            log("[v134] LA8 flown: " + n + " / 6 under the LA8 fly frame, angles " + a + ", lowest at " + String.format(java.util.Locale.ROOT, "%.2f", fly.bounds().minY - g));
            var floor = v4Array(l, new BlockPos(-350, g, -337), false);
            floor.initFrame("csc_aurex_la12_ground_frame", -90);
            n = 0;
            for (int i = 0; i < 3; i++) if (floor.add("csc_aurex_la12")) n++;
            log("[v134] LA12 on its ground frame on the floor: " + n + " / 3, first cabinet at " + String.format(java.util.Locale.ROOT, "%.3f", floor.poses().get(0).y()));
            // what is refused
            var lone = v4Array(l, new BlockPos(-346, g, -290), false);
            lone.init("csc_aurex_c6", -90, false);
            boolean third = lone.add("csc_aurex_c6") && !lone.add("csc_aurex_c6");
            // rev. 4b: every line array at the Orvane size — one row of two-cabinet arrays, behind the stacks
            String[] row = {"csc_orvane_lx12", "csc_aurex_la12", "csc_veyra_a2", "csc_noryx_a2", "csc_orvane_lx8", "csc_aurex_la8", "csc_veyra_a1", "csc_noryx_a1", "csc_aurex_c6"};
            StringBuilder sizes = new StringBuilder("[v134] sizes (w x h x d):");
            for (int i = 0; i < row.length; i++) {
                var arr = v4Array(l, new BlockPos(-355, g, -339 + i * 3), false);
                arr.init(row[i], -90, false); arr.add(row[i]);
                var m = com.concertstage.audio.SpeakerModels.get(row[i]);
                sizes.append(String.format(java.util.Locale.ROOT, " %s %.2f x %.2f x %.2f (x%.2f, %d cab)", row[i].substring(4), m.width(), m.height(), m.depth(),
                    com.concertstage.audio.SpeakerModels.scale(row[i]), arr.cabinets().size()));
            }
            log(sizes.toString());
            log("[v134] A1 + A2 still rig together: " + com.concertstage.audio.SpeakerModels.get("csc_veyra_a1").fits(com.concertstage.audio.SpeakerModels.get("csc_veyra_a2"))
                + ", LX8 + LX12: " + com.concertstage.audio.SpeakerModels.get("csc_orvane_lx8").fits(com.concertstage.audio.SpeakerModels.get("csc_orvane_lx12")));
            log("[v134] C6 on the ground: two at most " + third + "; B18P limit " + com.concertstage.audio.SpeakerModels.max(com.concertstage.audio.SpeakerModels.get("csc_aurex_b18p")));
        }, null, 120);
        add("v134_overview", null, null, 5);
        add("-v134 cam sizes", null, () -> cam(-341, groundY + 2.5, -328, 90, 6), 60);
        add("v134_sizes", null, null, 5);
        add("-v134 cam la12", null, () -> cam(-341, groundY + 2.2, -318.5, 90, 8), 60);
        add("v134_la12_on_s218f", null, null, 5);
        add("-v134 cam adapters", null, () -> cam(-341, groundY + 2.0, -307.5, 75, 6), 60);
        add("v134_stack_adapters", null, null, 5);
        add("-v134 cam c6", null, () -> cam(-344.5, groundY + 2.2, -296.5, 90, 4), 60);
        add("v134_c6_portable", null, null, 5);
        add("-v134 cam fly", null, () -> cam(-342, groundY + 4.5, -331, 90, -12), 60);
        add("v134_la8_flown", null, null, 5);
        add("-v134 cam close", null, () -> cam(-347.6, groundY + 2.8, -317.6, 120, 15), 60);
        add("v134_la12_close", null, null, 5);
        add("-v134 cam far", null, () -> cam(-300, groundY + 4, -312, 90, 2), 60);
        add("v134_far_lod", null, null, 5);
        add("-v134 gone", () -> { ServerLevel l = level();
            java.util.Collections.reverse(V134);
            for (BlockPos p : V134) { if (l.getBlockEntity(p) instanceof com.concertstage.audio.SpeakerBlockEntity s) s.clearCells(); l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }
            l.setDayTime(18000);
            log("[v134] removed " + V134.size()); }, null, 20);
    }

    private static void v133() {
        add("-v133 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED);
            com.concertstage.client.vision.VisionSettings.feedImpact = 3;
            cam(-344, 207.6, -310.5, -90, -2); }, 160);
        // ---- A. a wall of 8 x 5 panels in the audience, a PTZ camera on the stage, the scene lit
        add("-v133 rig", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -300);
            for (int z = 0; z < 8; z++) for (int y = 0; y < 5; y++)
                l.setBlock(new BlockPos(-328, groundY + 1 + y, -314 + z), StageContent.LED_PANEL_P1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.WEST), 3);
            com.concertstage.vision.WallManager.panelChanged(l, new BlockPos(-328, groundY + 1, -314));
            vProc = new BlockPos(-330, groundY, -318);
            l.setBlock(vProc, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);   // a new processor, not one left by an earlier run
            l.setBlock(vProc, StageContent.VISION_PROCESSOR_VP1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.WEST), 3);
            v3Wall = null;
            for (var w : walls().walls.values()) if (w.facing == Direction.WEST && w.plane == -328) v3Wall = w;
            v3Wall.processor = vProc.asLong(); v3Wall.output = 0; walls().setDirty();
            com.concertstage.vision.VisionNet.wallChanged(l, v3Wall);
            feedCam = camera(l, StageContent.CAM_PTZ_P1, new BlockPos(-322, groundY, -300), Direction.EAST, 31, "V133");
            float[][] cols = {{1f, 0.1f, 0.2f}, {1f, 1f, 1f}};
            int i = 0;
            for (var p : d.show.patch) { int[] one = {p.fid}; float[] c = cols[i++ % 2];
                aim(d, p.fid, -300 + (p.z - SZ) * 0.15, SY - 1, p.z); set(d, one, Attr.DIMMER, 1f); rgb(d, one, c[0], c[1], c[2]); set(d, one, Attr.ZOOM, 0.3f); }
            program(0, com.concertstage.vision.OutputState.BARS);
            log("[v133] wall " + v3Wall.name + " " + v3Wall.width() + "x" + v3Wall.height() + ", camera " + feedCam); }, null, 200);
        add("-v133 fps no image", null, () -> feedLog("wall on BARS (no camera image)"), 5);
        add("v133_wall_bars", null, null, 5);
        add("-v133 cam auto", () -> { program(0, com.concertstage.vision.OutputState.cam(feedCam)); wallSet(0, 0, 0, 0); }, null, 200);
        add("-v133 log auto", null, () -> feedLog("camera, automatic (BALANCED: 270p 10 i/s)"), 5);
        add("v133_wall_camera_auto", null, null, 5);
        add("-v133 cam 1080 60", () -> wallSet(0, 0, 1080, 60), null, 240);
        add("-v133 log 1080 60", null, () -> feedLog("camera asked 1080p 60 i/s, frame limit off, 3 %"), 5);
        add("v133_wall_camera_1080_60", null, null, 5);
        add("-v133 limit 60", null, () -> { Minecraft mc = Minecraft.getInstance(); mc.options.framerateLimit().set(60); mc.getWindow().setFramerateLimit(60); }, 240);
        add("-v133 log limit 60", null, () -> feedLog("camera asked 1080p 60 i/s, frame limit 60"), 5);
        add("-v133 limit off", null, () -> { Minecraft mc = Minecraft.getInstance(); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260); }, 60);
        add("-v133 cam 720 30", () -> wallSet(0, 0, 720, 30), null, 200);
        add("-v133 log 720 30", null, () -> feedLog("camera asked 720p 30 i/s, frame limit off, 3 %"), 5);
        // rev. 3b: the camera's own image next to the main view from about the same place (same light, same look asked)
        add("-v133 back 1080", () -> wallSet(0, 0, 1080, 60), null, 100);
        add("-v133 feed png", null, () -> log("[v133] camera image saved: " + com.concertstage.client.vision.CameraFeeds.saveImage(feedCam,
            Minecraft.getInstance().gameDirectory.toPath().resolve("csc-visual").resolve("v133_feed_image.png"))), 5);
        add("-v133 main from lens", null, () -> { double[] l = com.concertstage.client.vision.CameraFeeds.lensOf(feedCam);
            if (l == null) return;
            double fov = 60.0 * Math.pow(7.0 / 60.0, l[5]);
            v3Fov = Minecraft.getInstance().options.fov().get();
            // the same field of view as the camera (the game's own vertical FOV), within the game's 30..110
            Minecraft.getInstance().options.fov().set((int) Math.max(30, Math.min(110, Math.round(fov))));
            log(String.format(java.util.Locale.ROOT, "[v133] camera lens zoom %.2f -> FOV %.1f deg (main view set to %d)", l[5], fov, Minecraft.getInstance().options.fov().get()));
            cam(l[0] - 0.5, l[1] - 1.62, l[2] - 0.5, (float) l[3], (float) l[4]); }, 60);
        add("v133_main_from_camera_lens", null, null, 5);
        add("-v133 fov back", null, () -> { if (v3Fov > 0) Minecraft.getInstance().options.fov().set(v3Fov); }, 5);
        add("-v133 bars again", () -> program(0, com.concertstage.vision.OutputState.BARS), null, 120);
        add("-v133 log bars again", null, () -> feedLog("back to BARS"), 5);
        // ---- B. the wall at an angle
        add("-v133 angle 30", () -> { program(0, com.concertstage.vision.OutputState.cam(feedCam)); wallSet(30, 0, 0, 0); }, () -> cam(-344, groundY + 3, -305, -110, 5), 80);
        add("v133_wall_angle_30_centre", null, null, 5);
        add("-v133 angle -35 left", () -> wallSet(-35, 1, 0, 0), null, 60);
        add("v133_wall_angle_m35_left_edge", null, null, 5);
        add("-v133 angle 0", () -> wallSet(0, 0, 0, 0), null, 20);
        // ---- C. colour change on blinders / white bars (reported 13 November: "changing colour puts the brightness to 0 %")
        add("-v133 white", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK);
            BlockPos b1 = new BlockPos(-334, groundY + 1, -298), b2 = new BlockPos(-334, groundY + 1, -295);
            l.setBlock(b1, StageContent.BLINDER_X4.defaultBlockState().setValue(FixtureBlock.FACING, Direction.WEST).setValue(FixtureBlock.HUNG, false), 3);
            l.setBlock(b2, StageContent.PULSESTRIKE_M.defaultBlockState().setValue(FixtureBlock.FACING, Direction.WEST).setValue(FixtureBlock.HUNG, false), 3);
            FixtureBlockEntity f1 = (FixtureBlockEntity) l.getBlockEntity(b1), f2 = (FixtureBlockEntity) l.getBlockEntity(b2);
            LightServer.patch(l, d, f1); LightServer.patch(l, d, f2);
            v3White = new int[] {f1.fid(), f2.fid()};
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v3White); cmd(d, cw.bool(false).toBytes()); }   // nothing left from an earlier run (same fid), sent like the desk so the client follows
            set(d, v3White, Attr.DIMMER, 1f); }, null, 40);
        add("-v133 white log 1", null, () -> logWhite("dimmer 100 %, before any colour"), 5);
        add("-v133 white rgb new", () -> { Bytes.Writer w = Commands.op(Commands.P_RGB); Commands.fids(w, v3White);
            cmd(desk(SCENE_DESK), w.unit(1f).unit(0.2f).unit(0f).unit(Float.NaN).toBytes()); }, null, 20);
        add("-v133 white log 2", null, () -> logWhite("colour sent the rev. 3 way (white untouched)"), 5);
        add("-v133 white rgb old", () -> { Bytes.Writer w = Commands.op(Commands.P_RGB); Commands.fids(w, v3White);
            cmd(desk(SCENE_DESK), w.unit(1f).unit(0.2f).unit(0f).unit(0f).toBytes()); }, null, 20);
        add("-v133 white log 3", null, () -> logWhite("colour sent the old way (white 0, the reported bug)"), 5);
        // ---- D. focus tool
        add("-v133 aim", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK); ServerPlayer pl = player(server());
            List<BlockPos> sel = new ArrayList<>();
            for (var p : d.show.patch) if (p.profile().hasPan() && sel.size() < 3) sel.add(new BlockPos(p.x, p.y, p.z));
            Vec3 target = new Vec3(-320.5, groundY + 1, -310.5);
            com.concertstage.light.AimToolItem.aim(l, pl, sel, target);
            for (BlockPos b : sel) if (l.getBlockEntity(b) instanceof FixtureBlockEntity fe) {
                var lk = d.live.programmer.get(fe.fid());
                var pa = d.show.fixture(fe.fid());
                log(String.format(java.util.Locale.ROOT, "[v133] aim #%d -> programmer pan %.3f tilt %.3f | external: %s", fe.fid(),
                    lk == null ? -1 : lk.get(Attr.PAN), lk == null ? -1 : lk.get(Attr.TILT),
                    com.concertstage.light.AimToolItem.dmxLine(pa, com.concertstage.light.core.Movement.aim(pa, target.x, target.y, target.z)).getString()));
            } }, () -> cam(-344, groundY + 4, -310.5, -90, 15), 80);
        add("v133_focus_tool_aimed", null, null, 5);
        add("-v133 gone", () -> { ServerLevel l = level();
            for (int z = 0; z < 8; z++) for (int y = 0; y < 5; y++) l.setBlock(new BlockPos(-328, groundY + 1 + y, -314 + z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos b : new BlockPos[] {vProc, feedCam, new BlockPos(-334, groundY + 1, -298), new BlockPos(-334, groundY + 1, -295)})
                l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }, () -> feedCam = null, 20);
    }


    // ------------------------------------------------------------------ 1.2.0 beta 2 (13 November, 20 h 46)


    // ------------------------------------------------------------------ 1.2.0 beta 2b, 14 November 00 h 34: performance
    private static final java.util.List<BlockPos> V137 = new java.util.ArrayList<>();
    private static BlockPos v7Cam;

    // ------------------------------------------------------------------ 1.2.0 beta 2b (14 November, 13 h 32)
    private static final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> V138 = new java.util.LinkedHashMap<>();
    private static final java.util.List<BlockPos> V138B = new java.util.ArrayList<>();
    private static BlockPos v8Desk;
    private static int[] v8Fix = new int[0];
    private static int v8Floor;
    private static final int V8_UNIVERSE = 9;

    /** Floor of smooth stone at y - 1 and air up to y + 3 over the rectangle (the blocks are given back at the end). */
    private static void v8floor(ServerLevel l, int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int dy = -1; dy <= 3; dy++) {
            BlockPos p = new BlockPos(x, y + dy, z);
            V138.putIfAbsent(p.immutable(), l.getBlockState(p));
            l.setBlock(p, dy < 0 ? net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** The test player uses {@code main} on a block (as a right click), holding {@code off} in the other hand, looking at {@code yaw}. */
    private static net.minecraft.world.InteractionResult v8use(net.minecraft.world.item.ItemStack main, net.minecraft.world.item.ItemStack off, Vec3 hit,
                                                               Direction face, BlockPos pos, float yaw) {
        ServerPlayer p = player(server());
        p.setYRot(yaw); p.setYHeadRot(yaw); p.yRotO = yaw; p.setXRot(60f);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, main);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, off);
        var ctx = new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(hit, face, pos, false));
        return main.useOn(ctx);
    }

    private static net.minecraft.world.item.ItemStack v8item(Block b) {
        return new net.minecraft.world.item.ItemStack(StageContent.ITEMS.get(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath()));
    }

    private static net.minecraft.world.item.ItemStack v8item(String id) {
        return new net.minecraft.world.item.ItemStack(StageContent.ITEMS.get(id));
    }

    /** The DMX the external desk gets: each fixture in its Full (pixel mode), a cell pattern, no master dimmer touched. */
    private static byte[] v8dmx(LumicoreBlockEntity d) {
        byte[] u = new byte[512];
        for (int f : v8Fix) {
            var pa = d.show.fixture(f);
            var m = com.concertstage.light.dmx.DmxMode.of(pa.profile(), pa.dmxMode);
            for (int i = 0; i < m.channels.length; i++) {
                var c = m.channels[i];
                int slot = pa.address - 1 + i, v = c.def();
                if (c.zone() >= 0) {
                    boolean even = c.zone() % 2 == 0;
                    int kind = pa.profile().zoneKind(c.zone());
                    v = switch (c.kind()) {
                        case ZONE_R -> even ? 255 : 0;
                        case ZONE_G -> kind != 1 && c.zone() >= pa.profile().zones - pa.profile().rgbZones / 2 && pa.profile().hasSections() ? 255 : 0;
                        case ZONE_B -> even ? 0 : 255;
                        case ZONE_LEVEL -> c.zone() % 4 == 0 ? 255 : 0;
                        default -> 0;
                    };
                } else if (c.attr() == Attr.TILT) v = 128;
                if (slot >= 0 && slot < 512) u[slot] = (byte) v;
            }
        }
        return u;
    }

    /** Server thread only (a block entity of the server level is not given to another thread). */
    private static void v8logPixels(String at) {
        LumicoreBlockEntity d = desk(v8Desk);
        StringBuilder sb = new StringBuilder("[v138] " + at + ": control " + d.live.control + " |");
        for (int f : v8Fix) {
            var pa = d.show.fixture(f);
            float[] v = new float[Attr.COUNT], z = new float[pa.profile().zones * 4];
            for (Attr a : Attr.ALL) v[a.ordinal()] = pa.profile().home(a);
            boolean ok = com.concertstage.light.dmx.DmxDecode.decode(pa, d.dmx, v, z);
            sb.append(String.format(java.util.Locale.ROOT, " %s %s %d ch @%d.%d decoded %s dimmer %.2f cells:", pa.profile().name, com.concertstage.light.dmx.DmxMode.NAMES[pa.dmxMode],
                pa.footprint(), pa.universe, pa.address, ok, v[Attr.DIMMER.ordinal()]));
            for (int k = 0; k < Math.min(6, pa.profile().zones); k++) sb.append(String.format(java.util.Locale.ROOT, " (%.0f %.0f %.0f|%.0f)", z[k * 4] * 255, z[k * 4 + 1] * 255, z[k * 4 + 2] * 255, z[k * 4 + 3] * 255));
            sb.append(" |");
        }
        log(sb.toString());
    }

    /** Every piece held by the stage cells and angled holders of a box: name, place, angle. */
    private static String v8pieces(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            BlockPos p = new BlockPos(x, y, z);
            if (l.getBlockEntity(p) instanceof com.concertstage.stage.StageAttachmentBlockEntity be)
                for (var e : be.entries()) if (seen.add(e.token()))
                    sb.append(String.format(java.util.Locale.ROOT, " %s@(%.2f %.2f %.2f) %.1f° [%s];", e.key(), x + e.x(), y + e.y(), z + e.z(), e.degrees(), e.support()));
            if (l.getBlockEntity(p) instanceof com.concertstage.block.AngledHolderBlockEntity h)
                for (var a : h.pieces()) sb.append(String.format(java.util.Locale.ROOT, " %s@(%.2f %.2f %.2f) %.1f°;", a.block, a.absX(p), a.absY(p), a.absZ(p), a.angle));
            var st = l.getBlockState(p);
            if (st.getBlock() instanceof com.concertstage.stage.StageEquipmentBlock eb && st.hasProperty(com.concertstage.stage.StageEquipmentBlock.FACING)) {
                if (seen.add("blk" + eb.mainPos(st, p))) sb.append(" block ").append(eb.spec.id).append(" main ").append(eb.mainPos(st, p).toShortString()).append(';');
            }
        }
        return sb.toString();
    }

    private static void v8clearChain() {
        try {
            var f = com.concertstage.angle.AnglePlacement.class.getDeclaredField("CHAINS");
            f.setAccessible(true);
            ((java.util.Map<?, ?>) f.get(null)).clear();
        } catch (ReflectiveOperationException e) { log("[v138] chains " + e); }
    }

    /**
     * Beta 2b, 14 November: (1) the Full of the pixel fixtures and of the PULSESTRIKE / FLASHCORE is a pixel mode (no master dimmer, no LED
     * bar runs): a cell effect sent by a desk lights the cells; (2) a railing set on a platform laid at an angle is clamped on its edge at its
     * angle, railings join up; (3) the barriers and fences go at an angle with the angle tool, end to end.
     */
    private static void v138() {
        r25load();
        // ---- 1. pixel Full over DMX (a third-party desk on universe 9: cells alternately red / blue, no master dimmer)
        add("-v138 pixels", () -> { ServerLevel l = level();
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -334, -300);
            v8Desk = new BlockPos(-338, groundY, -310);
            V138.putIfAbsent(v8Desk, l.getBlockState(v8Desk));
            desk(l, v8Desk);
            LumicoreBlockEntity d = desk(v8Desk);
            Block[] bs = {StageContent.PIXELBAR_B16, StageContent.PULSESTRIKE_M, StageContent.FLASHCORE_FX};
            v8Fix = new int[bs.length];
            int addr = 1;
            for (int i = 0; i < bs.length; i++) {
                BlockPos p = new BlockPos(-334, groundY + 7, -306 + i * 7);
                V138.putIfAbsent(p, l.getBlockState(p)); V138.putIfAbsent(p.above(), l.getBlockState(p.above()));
                v8Fix[i] = fixture(l, v8Desk, bs[i], p, true, Direction.EAST);
                var pa = d.show.fixture(v8Fix[i]);
                cmd(d, Commands.op(Commands.PATCH_DMX).varInt(v8Fix[i]).varInt(V8_UNIVERSE).varInt(addr).varInt(com.concertstage.light.dmx.DmxMode.FULL)
                    .bool(false).bool(false).f32(0f).f32(0f).toBytes());
                addr += com.concertstage.light.dmx.DmxMode.of(pa.profile(), com.concertstage.light.dmx.DmxMode.FULL).footprint() + 3;
            }
            cmd(d, Commands.op(Commands.CONTROL_MODE).varInt(com.concertstage.light.core.Live.EXTERNAL).toBytes());
            d.dmx.set(V8_UNIVERSE, v8dmx(d)); },
            () -> cam(-341, groundY + 9, -299, -90, 38), 120);
        add("-v138 pixels dmx", () -> { LumicoreBlockEntity d = desk(v8Desk); d.dmx.set(V8_UNIVERSE, v8dmx(d)); v8logPixels("cell pattern"); }, null, 60);
        add("v138_pixels_floor", null, null, 10);
        add("-v138 pixels faces", () -> { LumicoreBlockEntity d = desk(v8Desk); d.dmx.set(V8_UNIVERSE, v8dmx(d)); }, () -> cam(-329, groundY + 2, -299, 90, -42), 60);
        add("v138_pixels_faces", null, null, 10);
        add("-v138 pixels off", () -> { LumicoreBlockEntity d = desk(v8Desk); d.dmx.set(V8_UNIVERSE, new byte[512]); }, null, 40);
        add("v138_pixels_dmx_zero", () -> v8logPixels("all DMX at 0"), null, 10);
        add("-v138 pixels end", () -> { LumicoreBlockEntity d = desk(v8Desk);
            cmd(d, Commands.op(Commands.CONTROL_MODE).varInt(com.concertstage.light.core.Live.INTERNAL).toBytes()); clear(d); }, null, 10);

        // ---- 2. railings on a straight platform and on a platform laid at 30° (platform protractor)
        add("-v138 railings", () -> { ServerLevel l = level();
            v8Floor = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -322, -318);
            int y = v8Floor;
            v8floor(l, -330, -326, -314, -313, y);
            ServerPlayer pl = player(server());
            pl.teleportTo(l, -322.5, y + 12, -305.5, 180, 50);
            // straight deck, railing on its edge (the clamp of 1.0)
            var r1 = v8use(v8item("flexdeck_2x1_h100"), net.minecraft.world.item.ItemStack.EMPTY, new Vec3(-327.5, y, -322.5), Direction.UP, new BlockPos(-328, y - 1, -323), 180);
            BlockPos deck = new BlockPos(-328, y, -323);
            var r2 = v8use(v8item("safety_railings_1m"), net.minecraft.world.item.ItemStack.EMPTY, new Vec3(-327.5, y + 1.0, -322.95), Direction.UP, deck, 180);
            log("[v138] straight deck " + r1 + ", railing on it " + r2 + ":" + v8pieces(l, -330, y - 1, -325, -325, y + 2, -320));
            // deck at 30° with the protractor
            var tool = new net.minecraft.world.item.ItemStack(StageContent.PLATFORM_ANGLE_TOOL);
            tool.getOrCreateTag().putInt(com.concertstage.item.PlatformAngleToolItem.TAG, 30);
            var r3 = v8use(v8item("flexdeck_2x1_h100"), tool, new Vec3(-320.5, y, -319.5), Direction.UP, new BlockPos(-321, y - 1, -320), 180);
            com.concertstage.stage.StageAttachmentBlockEntity.Entry host = null; BlockPos hostCell = null;
            for (int x = -324; x <= -317 && host == null; x++) for (int z = -323; z <= -316 && host == null; z++) for (int dy = 0; dy <= 1 && host == null; dy++) {
                BlockPos c = new BlockPos(x, y + dy, z);
                if (l.getBlockEntity(c) instanceof com.concertstage.stage.StageAttachmentBlockEntity be)
                    for (var e : be.entries()) if (e.key().equals("flexdeck_2x1_h100") && "ground".equals(e.support())) { host = e; hostCell = c; break; }
            }
            log("[v138] deck at 30° " + r3 + (host == null ? " NOT FOUND" : String.format(java.util.Locale.ROOT, " at (%.2f %.2f %.2f) %.1f°", hostCell.getX() + host.x(), hostCell.getY() + host.y(), hostCell.getZ() + host.z(), host.degrees())));
            if (host == null) return;
            double hx = hostCell.getX() + host.x(), hy = hostCell.getY() + host.y(), hz = hostCell.getZ() + host.z(), a = host.degrees();
            String[][] clicks = {{"safety_railings_1m", "0.5", "0.04"}, {"safety_railings_1m", "1.5", "0.04"}, {"safety_railings_corner_1x1", "1.96", "0.96"},
                {"safety_railings_1m", "0.5", "0.96"}, {"safety_railings_1m", "0.03", "0.5"}};
            StringBuilder sb = new StringBuilder("[v138] railings on the 30° deck:");
            for (String[] c : clicks) {
                double[] w = com.concertstage.stage.StagePose.point(new double[] {Double.parseDouble(c[1]), 1.0, Double.parseDouble(c[2])}, hx, hy, hz, a);
                BlockPos cell = BlockPos.containing(w[0], w[1] - 0.01, w[2]);
                var r = v8use(v8item(c[0]), net.minecraft.world.item.ItemStack.EMPTY, new Vec3(w[0], w[1], w[2]), Direction.UP, cell, 180);
                sb.append(' ').append(c[0]).append(" at ").append(c[1]).append(',').append(c[2]).append(" -> ").append(r).append(';');
            }
            log(sb.toString());
            log("[v138] pieces after:" + v8pieces(l, -325, y - 1, -324, -315, y + 2, -314)); },
            () -> cam(-321, v8Floor + 6, -312, 160, 42), 100);
        add("v138_railings", null, null, 10);
        add("-v138 railings close", null, () -> cam(-318, v8Floor + 2.5, -317, 120, 28), 60);
        add("v138_railings_close", null, null, 10);
        add("-v138 railings straight", null, () -> cam(-327, v8Floor + 2.6, -327, 0, 28), 60);
        add("v138_railings_straight_deck", null, null, 10);
        // the deck taken away: its railings go with it (given back)
        add("-v138 railings remove", () -> { ServerLevel l = level(); int y = v8Floor;
            com.concertstage.stage.StageModules.Piece deck = null;
            for (int x = -324; x <= -317 && deck == null; x++) for (int z = -323; z <= -316 && deck == null; z++) {
                BlockPos c = new BlockPos(x, y, z);
                for (var e : com.concertstage.stage.StageModules.entries(l, c)) if (e.key().equals("flexdeck_2x1_h100")) { deck = new com.concertstage.stage.StageModules.Piece(c, e); break; }
            }
            if (deck != null) com.concertstage.stage.StageModules.remove(l, deck, null, false);
            log("[v138] after removing the 30° deck:" + v8pieces(l, -325, y - 1, -324, -315, y + 2, -314)); }, null, 20);

        // ---- 3. barriers at 30° with the angle tool, one line per kind
        add("-v138 barriers", () -> { ServerLevel l = level(); int y = v8Floor;
            v8floor(l, -330, -312, -314, -298, y);
            var tool = new net.minecraft.world.item.ItemStack(StageContent.ANGLE_TOOL);
            tool.getOrCreateTag().putInt(com.concertstage.item.AngleToolItem.TAG, 30);
            Object[][] lines = {
                {new Block[] {StageContent.BARRIER_CROWDGUARD_1M, StageContent.BARRIER_CROWDGUARD_1M, StageContent.BARRIER_CROWDGUARD_1M, StageContent.BARRIER_CROWDGUARD_GATE, StageContent.BARRIER_CROWDGUARD_END}, -311},
                {new Block[] {StageContent.BARRIER_CITYLINE_2M, StageContent.BARRIER_CITYLINE_2M, StageContent.BARRIER_CITYLINE_2M}, -308},
                {new Block[] {StageContent.BARRIER_ALUGUARD_1M, StageContent.BARRIER_ALUGUARD_1M, StageContent.BARRIER_ALUGUARD_1M, StageContent.BARRIER_ALUGUARD_CORNER}, -305},
                {new Block[] {StageContent.FENCE_SITE_SCREEN, StageContent.FENCE_SITE, StageContent.FENCE_SITE_SCREEN}, -301}};
            for (Object[] line : lines) {
                v8clearChain();
                int z = (Integer) line[1];
                StringBuilder sb = new StringBuilder("[v138] line z " + z + ":");
                for (Block b : (Block[]) line[0]) {
                    var r = v8use(v8item(b), tool.copy(), new Vec3(-328.5, y, z + 0.5), Direction.UP, new BlockPos(-329, y - 1, z), -90);
                    sb.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath()).append(" -> ").append(r).append(';');
                }
                log(sb.toString());
            }
            log("[v138] barriers:" + v8pieces(l, -330, y - 1, -312, -314, y + 2, -298)); },
            () -> cam(-322, v8Floor + 9, -294, 180, 48), 100);
        add("v138_barriers", null, null, 10);
        add("-v138 barriers low", null, () -> cam(-315, v8Floor + 2, -306, 110, 12), 60);
        add("v138_barriers_low", null, null, 10);
        // a truss line after the barriers starts by itself (not carried on from the barrier line)
        add("-v138 truss after barriers", () -> { ServerLevel l = level(); int y = v8Floor;
            var tool = new net.minecraft.world.item.ItemStack(StageContent.ANGLE_TOOL);
            tool.getOrCreateTag().putInt(com.concertstage.item.AngleToolItem.TAG, 30);
            var r = v8use(v8item("truss_h1_l2"), tool, new Vec3(-317.5, y, -299.5), Direction.UP, new BlockPos(-318, y - 1, -300), -90);
            log("[v138] truss after the barrier lines: " + r + ":" + v8pieces(l, -320, y - 1, -302, -314, y + 1, -297)); }, null, 20);

        add("-v138 cleanup", () -> { ServerLevel l = level();
            for (int x = -331; x <= -313; x++) for (int z = -327; z <= -297; z++) for (int dy = -1; dy <= 3; dy++) {
                BlockPos p = new BlockPos(x, v8Floor + dy, z);
                if (l.getBlockEntity(p) instanceof com.concertstage.block.AngledHolderBlockEntity h) h.noDrops = true;
            }
            com.concertstage.stage.StageStructure.begin();
            try {
                for (var e : V138.entrySet()) l.setBlock(e.getKey(), e.getValue(), 2 | 16);
            } finally { com.concertstage.stage.StageStructure.end(); }
            for (var it : l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(-345, v8Floor - 5, -330, -305, v8Floor + 15, -285))) it.discard();
            V138.clear(); }, null, 10);
    }

    private static void v137log(String at) {
        Minecraft mc = Minecraft.getInstance();
        log(String.format(java.util.Locale.ROOT, "[v137] %s | fps %d | feed base %.2f ms, extra %.2f ms, budget %.2f ms | camera image %s | %s", at, mc.getFps(),
            com.concertstage.client.vision.CameraFeeds.statBaseMs, com.concertstage.client.vision.CameraFeeds.statExtraMs, com.concertstage.client.vision.CameraFeeds.statBudgetMs,
            java.util.Arrays.toString(com.concertstage.client.vision.CameraFeeds.actual(v7Cam)), String.join(" ", com.concertstage.client.vision.VisionStats.lines())));
    }

    /**
     * Beta 2b perf: the author's case (13 November, 00 h 19: 45 fps, a camera image 30 / s costing 13 ms a frame). A 6 x 4 wall showing a
     * PTZ camera fixed at 720p 30 i/s, the stage lit; frame rate and camera cost measured looking at the wall, at the author's position
     * (wall out of view) and from behind the wall.
     */
    private static void v137() {
        r25load();
        add("-v137 setup", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK);
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -296);
            for (int x = 0; x < 6; x++) for (int y = 0; y < 4; y++) {
                BlockPos p = new BlockPos(-340, groundY + y, -298 + x);
                l.setBlock(p, StageContent.LED_PANEL_P1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.EAST), 3);
                V137.add(p);
            }
            com.concertstage.vision.WallManager.panelChanged(l, new BlockPos(-340, groundY, -298));
            vProc = new BlockPos(-338, groundY, -301);
            l.setBlock(vProc, StageContent.VISION_PROCESSOR_VP1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.EAST), 3);
            V137.add(vProc);
            v7Cam = camera(l, StageContent.CAM_PTZ_P1, new BlockPos(-334, groundY, -302), Direction.EAST, 77, "PERF");
            V137.add(v7Cam);
            var cb = (com.concertstage.vision.CameraBlockEntity) l.getBlockEntity(v7Cam);
            cb.videoHeight = 720; cb.videoFps = 30; cb.changed();
            for (var w : walls().walls.values()) if (w.facing == Direction.EAST && w.plane == -340 && w.v0 == groundY && w.u0 <= -298 && w.u1 >= -293) {
                w.processor = vProc.asLong(); w.output = 0; walls().setDirty(); com.concertstage.vision.VisionNet.wallChanged(l, w);
                log("[v137] wall " + w.name + " " + w.width() + "x" + w.height());
            }
            program(0, com.concertstage.vision.OutputState.cam(v7Cam));
            clear(d);
            for (var p : d.show.patch) aim(d, p.fid, SX - 4 + (p.z - SZ) * 0.2, SY, p.z);
            white(d, all, 0.45f); },
            () -> { Minecraft mc = Minecraft.getInstance(); mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
                cam(-330, groundY + 3, -296, 90, 5); }, 240);
        add("v137_wall_in_view", null, () -> v137log("A wall in view"), 100);
        add("-v137 wall in view log", null, () -> v137log("A wall in view (2)"), 2);
        add("-v137 author pose", null, () -> cam(-296, 217.53, -298, -153.2f, 27.9f), 240);
        add("v137_author_pose", null, () -> v137log("B author's position"), 100);
        add("-v137 author log", null, () -> v137log("B author's position (2)"), 2);
        add("-v137 behind", null, () -> cam(-344, groundY + 3, -296, -90, 0), 240);
        add("v137_wall_behind", null, () -> v137log("C behind the wall"), 100);
        add("-v137 behind log", null, () -> v137log("C behind the wall (2)"), 2);
        add("-v137 foh", null, () -> cam(-341, 207.6, -310, -90, -4), 240);
        add("v137_foh", null, () -> v137log("D front of house"), 100);
        add("-v137 foh log", null, () -> v137log("D front of house (2)"), 2);
        // E: heavy camera (1080p, 60 i/s fixed) and a far view taking in the wall and the stage: the case where a fixed camera ate the frame
        add("-v137 heavy", () -> { var cb = (com.concertstage.vision.CameraBlockEntity) level().getBlockEntity(v7Cam);
                cb.videoHeight = 1080; cb.videoFps = 60; cb.changed(); },
            () -> cam(-300, groundY + 6, -296, 90, 8), 240);
        add("v137_heavy", null, () -> v137log("E heavy camera, far view"), 100);
        add("-v137 heavy log", null, () -> v137log("E heavy camera, far view (2)"), 2);
        add("-v137 cleanup", () -> { ServerLevel l = level();
            for (BlockPos p : V137) l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            V137.clear(); }, null, 10);
    }

    // ------------------------------------------------------------------ 1.2.0 beta 2b (13 November, 22 h 52)
    private static final java.util.List<BlockPos> V136 = new java.util.ArrayList<>();
    private static int[] v6Fix = new int[0];
    private static BlockPos v6Console;
    private static int v6Before;

    /** A 1 s 16-bit mono WAV (a quiet tone), for the import tests. */
    private static void wav(java.nio.file.Path f, int hz) throws java.io.IOException {
        int rate = 22050, n = rate;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + n * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
            .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
        for (int i = 0; i < n; i++) b.putShort((short) (3000 * Math.sin(2 * Math.PI * hz * i / rate)));
        java.nio.file.Files.write(f, b.array());
    }

    /**
     * Beta 2b: (1) the light of the strobes and bars on the floor has a hot centre and, for a pixel bar, keeps its cells; (2) the music
     * picker of the console imports a selection or every file, one after the other.
     */
    private static void v136() {
        r25load();
        add("-v136 fixtures", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK); clear(d);
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -334, -300);
            Block[] bs = {StageContent.PIXELBAR_B16, StageContent.PULSESTRIKE_M, StageContent.FLASHCORE_FX};
            v6Fix = new int[bs.length];
            for (int i = 0; i < bs.length; i++) {
                BlockPos p = new BlockPos(-334, groundY + 7, -306 + i * 7);
                V136.add(p); V136.add(p.above());
                v6Fix[i] = fixture(l, SCENE_DESK, bs[i], p, true, Direction.EAST);
            }
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v6Fix); cmd(d, cw.bool(false).toBytes()); }
            for (int i = 0; i < v6Fix.length; i++) aim(d, v6Fix[i], -329.5, groundY, -305.5 + i * 7);
            white(d, v6Fix, 0.5f);
            for (int f : v6Fix) if (d.show.fixture(f).profile().has(Attr.WHITE)) set(d, new int[] {f}, Attr.WHITE, 1f); },
            () -> cam(-341, groundY + 9, -299, -90, 38), 160);
        add("v136_floor_white", null, null, 10);
        add("-v136 cells", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            var pr = d.show.fixture(v6Fix[0]).profile();
            float[][] c = {{1, 0, 0}, {0, 1, 0}, {0, 0.3f, 1}, {1, 0.8f, 0}};
            for (int z = 0; z < pr.zones; z++) {
                float[] k = c[(z * 4 / Math.max(1, pr.zones)) % 4];
                cmd(d, Commands.op(Commands.P_ZONE).varInt(v6Fix[0]).varInt(z).unit(k[0]).unit(k[1]).unit(k[2]).unit(1f).toBytes());
            }
            log("[v136] PIXELBAR zones " + pr.zones + " in 4 colour blocks"); }, null, 40);
        add("v136_floor_bar_cells", null, null, 10);
        add("-v136 cells close", null, () -> cam(-333, groundY + 4, -309, -40, 50), 40);
        add("v136_floor_bar_cells_close", null, null, 10);
        add("-v136 strobe", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, new int[] {v6Fix[1], v6Fix[2]}, Attr.STROBE, 0.35f); },
            () -> cam(-341, groundY + 9, -299, -90, 38), 60);
        for (int k = 0; k < 8; k++) add("v136_floor_strobe_" + k, null, null, 1);
        add("-v136 lights off", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, v6Fix, Attr.STROBE, 0f); set(d, v6Fix, Attr.DIMMER, 0f); }, null, 10);
        // music import: 5 files in the music folder, 2 ticked then imported, then "import all"
        add("-v136 console", () -> { ServerLevel l = level();
            v6Console = new BlockPos(-338, groundY, -312);
            l.setBlock(v6Console, StageContent.AUDIO_CONSOLE.defaultBlockState(), 3); V136.add(v6Console); },
            () -> { try {
                var dir = com.concertstage.client.audio.AudioCache.musicDir();
                for (int i = 1; i <= 5; i++) wav(dir.resolve("csc_test_" + i + ".wav"), 220 * i);
            } catch (Exception e) { log("[v136] wav " + e); } cam(-341, groundY + 1, -312, -90, 10); }, 40);
        add("-v136 picker", null, () -> { try {
                var sc = new com.concertstage.client.audio.ConsoleScreen(v6Console);
                Minecraft.getInstance().setScreen(sc);
                var m = com.concertstage.client.audio.ConsoleScreen.class.getDeclaredMethod("openPicker"); m.setAccessible(true); m.invoke(sc);
                var ff = com.concertstage.client.audio.ConsoleScreen.class.getDeclaredField("files"); ff.setAccessible(true);
                var cf = com.concertstage.client.audio.ConsoleScreen.class.getDeclaredField("chosen"); cf.setAccessible(true);
                @SuppressWarnings("unchecked") var files = (java.util.List<java.nio.file.Path>) ff.get(sc);
                @SuppressWarnings("unchecked") var chosen = (java.util.Set<java.nio.file.Path>) cf.get(sc);
                for (var f : files) if (f.getFileName().toString().equals("csc_test_2.wav") || f.getFileName().toString().equals("csc_test_4.wav")) chosen.add(f);
                sc.init(Minecraft.getInstance(), sc.width, sc.height);
                log("[v136] picker lists " + files.size() + " files, ticked " + chosen.size());
            } catch (Exception e) { log("[v136] picker " + e); } }, 20);
        add("v136_picker_selection", null, null, 2);
        add("-v136 import selected", null, () -> {
            var c = (com.concertstage.audio.AudioConsoleBlockEntity) Minecraft.getInstance().level.getBlockEntity(v6Console);
            v6Before = c == null ? -1 : c.playlist().size();
            java.util.List<java.nio.file.Path> sel = new java.util.ArrayList<>();
            for (int i : new int[] {2, 4}) sel.add(com.concertstage.client.audio.AudioCache.musicDir().resolve("csc_test_" + i + ".wav"));
            com.concertstage.client.audio.Transfers.importFiles(v6Console, sel);
            log("[v136] import selection of 2 started, playlist before " + v6Before + ", queued " + com.concertstage.client.audio.Transfers.queued()); }, 200);
        add("-v136 import selected log", null, () -> {
            var c = (com.concertstage.audio.AudioConsoleBlockEntity) Minecraft.getInstance().level.getBlockEntity(v6Console);
            log("[v136] after selection: playlist " + (c == null ? -1 : c.playlist().size()) + ", busy " + com.concertstage.client.audio.Transfers.busy()
                + ", status '" + com.concertstage.client.audio.Transfers.status().getString() + "'"); }, 2);
        add("-v136 import all", null, () -> {
            java.util.List<java.nio.file.Path> all5 = new java.util.ArrayList<>();
            for (int i = 1; i <= 5; i++) all5.add(com.concertstage.client.audio.AudioCache.musicDir().resolve("csc_test_" + i + ".wav"));
            com.concertstage.client.audio.Transfers.importFiles(v6Console, all5);
            log("[v136] import all (5) started, queued " + com.concertstage.client.audio.Transfers.queued()); }, 400);
        add("-v136 import all log", null, () -> {
            var c = (com.concertstage.audio.AudioConsoleBlockEntity) Minecraft.getInstance().level.getBlockEntity(v6Console);
            StringBuilder sb = new StringBuilder();
            if (c != null) for (var t : c.playlist()) sb.append(t.name()).append(", ");
            log("[v136] after import all: playlist " + (c == null ? -1 : c.playlist().size()) + " [" + sb + "], busy " + com.concertstage.client.audio.Transfers.busy()
                + ", status '" + com.concertstage.client.audio.Transfers.status().getString() + "'"); }, 2);
        add("v136_console_after_import", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.audio.ConsoleScreen(v6Console)), 30);
        add("-v136 cleanup", () -> { ServerLevel l = level();
            for (BlockPos p : V136) l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            V136.clear(); },
            () -> { Minecraft.getInstance().setScreen(null);
                try { for (int i = 1; i <= 5; i++) java.nio.file.Files.deleteIfExists(com.concertstage.client.audio.AudioCache.musicDir().resolve("csc_test_" + i + ".wav")); }
                catch (Exception ignored) { } }, 10);
    }

    private static final java.util.List<BlockPos> V135 = new java.util.ArrayList<>();
    private static int[] v5Bars = new int[0];

    private static void v5Put(ServerLevel l, BlockPos p, net.minecraft.world.level.block.state.BlockState st) {
        l.setBlock(p, st, 3);
        V135.add(p);
    }

    private static void v135() {
        add("-v135 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED);
            com.concertstage.client.vision.VisionSettings.feedImpact = 1;
            com.concertstage.client.RenderQuality.setBeamPercent(100); com.concertstage.client.RenderQuality.setBlockPercent(100);
            cam(-344, 207.6, -310.5, -90, -2); }, 160);
        // ---- 1. LiveVision models (missing from the first beta 2 jar), curved truss particle model
        add("-v135 vision", () -> { ServerLevel l = level();
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -300);
            V135.add(camera(l, StageContent.CAM_PTZ_P1, new BlockPos(-338, groundY, -322), Direction.EAST, 41, "PTZ"));
            V135.add(camera(l, StageContent.BROADCAST_B1, new BlockPos(-338, groundY, -319), Direction.EAST, 42, "B1"));
            V135.add(camera(l, StageContent.REMOTE_R1, new BlockPos(-338, groundY, -316), Direction.EAST, 43, "R1"));
            v5Put(l, new BlockPos(-338, groundY, -313), StageContent.VISION_PROCESSOR_VP1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.EAST));
            for (int z = 0; z < 3; z++) for (int y = 0; y < 2; y++)
                v5Put(l, new BlockPos(-340, groundY + 1 + y, -311 + z), StageContent.LED_PANEL_P1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.EAST));
            com.concertstage.vision.WallManager.panelChanged(l, new BlockPos(-340, groundY + 1, -311));
            BlockPos ct = new BlockPos(-336, groundY + 2, -306);
            l.setBlock(ct.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3); V135.add(ct.below());
            v5Put(l, ct, StageContent.CURVED_TRITRUSS.defaultBlockState()); },
            () -> { StringBuilder sb = new StringBuilder("[v135] LiveVision models:");
                for (String m : new String[] {"cam_ptz_p1", "broadcast_b1", "crane_c1", "remote_r1", "vision_processor_vp1", "led_panel_p1", "led_accessories"})
                    sb.append(' ').append(m).append(com.concertstage.client.vision.VisionModel.get(m) != null ? " ok" : " MISSING");
                var bm = Minecraft.getInstance().getModelManager().getModel(new net.minecraft.client.resources.model.ModelResourceLocation(
                    new net.minecraft.resources.ResourceLocation("concertstage", "curved_tritruss"), "facing=north"));
                sb.append(" | curved truss block model ").append(bm == Minecraft.getInstance().getModelManager().getMissingModel() ? "MISSING" : "ok");
                log(sb.toString());
                cam(-332.5, groundY + 2.2, -315.5, 90, 18); }, 100);
        add("v135_cameras_processor_panels", null, null, 5);
        add("-v135 curved", null, () -> cam(-332.5, groundY + 3.5, -303, 140, 20), 30);
        add("v135_curved_truss", null, null, 5);
        // ---- 2. a camera at 60 images/s set on the camera itself, with the frame budget almost nothing (1 %)
        add("-v135 feed", () -> { ServerLevel l = level();
            var be = (com.concertstage.vision.CameraBlockEntity) l.getBlockEntity(V135.get(0));
            be.videoHeight = 720; be.videoFps = 60; be.changed();
            feedCam = V135.get(0);
            v3Wall = null;
            for (var w : walls().walls.values()) if (w.facing == Direction.EAST && w.plane == -340) v3Wall = w;
            if (v3Wall != null) {
                v3Wall.processor = new BlockPos(-338, groundY, -313).asLong(); v3Wall.output = 0; walls().setDirty();
                com.concertstage.vision.VisionNet.wallChanged(l, v3Wall);
            }
            log("[v135] wall " + (v3Wall == null ? "NOT FOUND" : v3Wall.name + " " + v3Wall.width() + "x" + v3Wall.height()));
            if (v3Wall != null) program(0, com.concertstage.vision.OutputState.cam(feedCam)); },
            () -> { Minecraft mc = Minecraft.getInstance(); mc.options.framerateLimit().set(60); mc.getWindow().setFramerateLimit(60); }, 260);
        add("-v135 feed log", null, () -> { var a = com.concertstage.client.vision.CameraFeeds.actual(feedCam);
            log(String.format(java.util.Locale.ROOT, "[v135] camera set to 720p 60 i/s on the camera, budget 1 %%, frame limit 60: game %d fps, image %dp %d i/s",
                Minecraft.getInstance().getFps(), a[0], a[1])); }, 5);
        add("-v135 feed auto", () -> { var be = (com.concertstage.vision.CameraBlockEntity) level().getBlockEntity(feedCam); be.videoHeight = 0; be.videoFps = 0; be.changed(); }, null, 240);
        add("-v135 feed auto log", null, () -> { var a = com.concertstage.client.vision.CameraFeeds.actual(feedCam);
            log(String.format(java.util.Locale.ROOT, "[v135] same camera back to Auto (screens decide, BALANCED 10 i/s, budget 1 %%): game %d fps, image %dp %d i/s",
                Minecraft.getInstance().getFps(), a[0], a[1]));
            Minecraft mc = Minecraft.getInstance(); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260); }, 5);
        // ---- 3. bars and strobes: DMX 128 on the tilt is straight up when standing (0 on the desk), like the moving heads
        add("-v135 bars", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK);
            Block[] bs = {StageContent.PIXELBAR_B16, StageContent.PULSESTRIKE_M, StageContent.FLASHCORE_FX};
            v5Bars = new int[bs.length];
            for (int i = 0; i < bs.length; i++) {
                BlockPos p = new BlockPos(-336, groundY + 1, -298 + i * 3);
                l.setBlock(p.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                V135.add(p); V135.add(p.below());
                v5Bars[i] = fixture(l, SCENE_DESK, bs[i], p, false, Direction.EAST);
            }
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v5Bars); cmd(d, cw.bool(false).toBytes()); }
            set(d, v5Bars, Attr.DIMMER, 1f); set(d, v5Bars, Attr.TILT, 0.5f);
            StringBuilder sb = new StringBuilder("[v135] tilt at DMX 128 (desk 0):");
            for (int f : v5Bars) { var pr = d.show.fixture(f).profile();
                float val = com.concertstage.light.dmx.DmxMode.decode(pr, Attr.TILT, 128, false);
                sb.append(String.format(java.util.Locale.ROOT, " %s %.1f deg (0 = horizontal, 90 = along the base) range %.0f..%.0f |", pr.name, pr.tiltDegrees(val), pr.tiltMin(), pr.tiltMax())); }
            log(sb.toString()); }, () -> cam(-330.5, groundY + 2.5, -295, 90, 10), 100);
        add("v135_bars_tilt_dmx128", null, null, 5);
        add("-v135 bars dmx0", () -> set(desk(SCENE_DESK), v5Bars, Attr.TILT, 0f), null, 80);
        add("v135_bars_tilt_dmx0", null, null, 5);
        // ---- 4. Compact of the gobo fixtures: its own rotation channel
        add("-v135 gobo", null, () -> {
            StringBuilder sb = new StringBuilder("[v135] Compact gobo rotation:");
            for (var pr : new com.concertstage.light.core.FixtureProfile[] {com.concertstage.light.core.FixtureProfile.VORTEX_X1, com.concertstage.light.core.FixtureProfile.PHANTOM_S1,
                    com.concertstage.light.core.FixtureProfile.PROFILE_P1, com.concertstage.light.core.FixtureProfile.RAZOR_B1}) {
                var m = com.concertstage.light.dmx.DmxMode.of(pr, com.concertstage.light.dmx.DmxMode.COMPACT);
                var old = com.concertstage.light.dmx.DmxMode.of(pr, com.concertstage.light.dmx.DmxMode.COMPACT_G8);
                int gi = -1, ri = -1;
                for (int i = 0; i < m.channels.length; i++) { if (m.channels[i].kind() == com.concertstage.light.dmx.DmxMode.Kind.GOBO_FX) gi = i;
                    if (m.channels[i].kind() == com.concertstage.light.dmx.DmxMode.Kind.ATTR && m.channels[i].attr() == Attr.GOBO_ROT) ri = i; }
                com.concertstage.light.core.Show.Patch pa = new com.concertstage.light.core.Show.Patch(); pa.type = pr.id; pa.universe = 0; pa.address = 1; pa.dmxMode = com.concertstage.light.dmx.DmxMode.COMPACT;
                com.concertstage.light.dmx.DmxData dmx = new com.concertstage.light.dmx.DmxData();
                byte[] u = new byte[512];
                int step = com.concertstage.light.dmx.DmxMode.goboBlock(pr);
                u[gi] = (byte) (2 * step + 1);                                  // gobo 2, still
                float[] v = new float[Attr.COUNT];
                StringBuilder one = new StringBuilder();
                for (int rot : new int[] {128, 200, 40}) {
                    u[ri] = (byte) rot; dmx.set(0, u.clone());
                    java.util.Arrays.fill(v, 0f);
                    com.concertstage.light.dmx.DmxDecode.decode(pa, dmx, v, null);
                    one.append(String.format(java.util.Locale.ROOT, " rot ch %d -> gobo %.2f rot %.2f;", rot, v[Attr.GOBO.ordinal()], v[Attr.GOBO_ROT.ordinal()]));
                }
                sb.append(String.format(java.util.Locale.ROOT, " %s: %d ch (old %d), gobo ch %d, rotation ch %d:%s |", pr.name, m.channels.length, old.channels.length, gi + 1, ri + 1, one));
            }
            log(sb.toString()); }, 5);
        // ---- 5. focus tool over an external desk (Live.focus), saved and read back
        add("-v135 focus", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            int[] heads = d.show.patch.stream().filter(p -> p.profile().hasPan()).limit(2).mapToInt(p -> p.fid).toArray();
            var live = d.live.copy();
            var w = Commands.op(Commands.P_FOCUS); Commands.fids(w, heads);
            int r = Commands.apply(d.show, live, w.f32(-320.5f).f32((float) groundY + 1).f32(-310.5f).toBytes(), 0, null);
            var back = com.concertstage.light.core.Live.fromBytes(live.toBytes());
            StringBuilder sb = new StringBuilder("[v135] focus over external desk: result " + r + ", held " + live.focus.size() + ", after save/read " + back.focus.size() + ":");
            for (var e : back.focus.entrySet()) sb.append(String.format(java.util.Locale.ROOT, " #%d pan %.3f tilt %.3f", e.getKey(), e.getValue()[0], e.getValue()[1]));
            var c = Commands.op(Commands.P_FOCUS_CLEAR); Commands.fids(c, heads);
            Commands.apply(d.show, live, c.toBytes(), 0, null);
            log(sb + " | after release " + live.focus.size()); }, null, 5);
        // ---- 6. AUREX M12 in a column of 3 (a 4th refused), flown under a block
        add("-v135 m12", () -> { ServerLevel l = level();
            BlockPos top = new BlockPos(-348, groundY + 9, -296);
            v5Put(l, top, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
            BlockPos at = top.below();
            l.setBlock(at, StageContent.SPEAKER.defaultBlockState().setValue(com.concertstage.audio.SpeakerBlock.HANGING, true), 3); V135.add(at);
            var s = (com.concertstage.audio.SpeakerBlockEntity) l.getBlockEntity(at);
            s.init("csc_aurex_m12", -90, true);
            boolean a1 = s.add("csc_aurex_m12"), a2 = s.add("csc_aurex_m12"), a3 = s.add("csc_aurex_m12"), a4 = s.add("csc_aurex_m12");
            var m = com.concertstage.audio.SpeakerModels.get("csc_aurex_m12");
            log("[v135] M12 column: adds " + a1 + " " + a2 + " " + a3 + " " + a4 + " -> " + s.cabinets().size() + " cabinets (max 3), M12 fits M12 " + m.fits(m)
                + ", LA12 on M12 " + m.fits(com.concertstage.audio.SpeakerModels.get("csc_aurex_la12"))); }, () -> cam(-341, groundY + 5, -296, 90, 5), 60);
        add("v135_m12_column", null, null, 5);
        // ---- 7. reach of the beams / distance of the blocks (configuration screen)
        add("-v135 range 100", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (var p : d.show.patch) { int[] one = {p.fid}; set(d, one, Attr.DIMMER, 1f); rgb(d, one, 1f, 1f, 1f); set(d, one, Attr.ZOOM, 0.2f); } },
            () -> { com.concertstage.client.RenderQuality.setBeamPercent(100); com.concertstage.client.RenderQuality.setBlockPercent(100);
                cam(-344, 207.6, -310.5, -90, -2); }, 80);
        add("-v135 range log 100", null, () -> log(String.format(java.util.Locale.ROOT, "[v135] beam range 100 %%: VORTEX throw %.1f blocks, blocks drawn to %d (world %d)",
            com.concertstage.client.light.FixtureRenderer.throwOf(com.concertstage.light.core.FixtureProfile.VORTEX_X1), com.concertstage.client.RenderQuality.distance(),
            com.concertstage.client.RenderQuality.worldDistance())), 5);
        add("v135_beams_100", null, null, 5);
        add("-v135 range 250", null, () -> { com.concertstage.client.RenderQuality.setBeamPercent(250); com.concertstage.client.RenderQuality.setBlockPercent(50);
            for (var f : new java.util.ArrayList<>(V135)) { } }, 80);
        add("-v135 range log 250", null, () -> log(String.format(java.util.Locale.ROOT, "[v135] beam range 250 %%, blocks 50 %%: VORTEX throw %.1f blocks, blocks drawn to %d (world %d)",
            com.concertstage.client.light.FixtureRenderer.throwOf(com.concertstage.light.core.FixtureProfile.VORTEX_X1), com.concertstage.client.RenderQuality.distance(),
            com.concertstage.client.RenderQuality.worldDistance())), 5);
        add("v135_beams_250", null, null, 5);
        add("-v135 config screen", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.RenderSettingsScreen(null)), 30);
        add("v135_config_screen", null, null, 5);
        add("-v135 camera screen", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.vision.CameraScreen(V135.get(0))), 30);
        add("v135_camera_screen", null, null, 5);
        add("-v135 end", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK);
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v5Bars); cmd(d, cw.bool(false).toBytes()); }
            java.util.List<BlockPos> rev = new java.util.ArrayList<>(V135); java.util.Collections.reverse(rev);
            for (BlockPos b : rev) l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            V135.clear(); },
            () -> { Minecraft.getInstance().setScreen(null); feedCam = null;
                com.concertstage.client.RenderQuality.setBeamPercent(100); com.concertstage.client.RenderQuality.setBlockPercent(100); }, 20);
    }


    // ------------------------------------------------------------ 1.2.0 (15 November)
    private static final java.util.List<BlockPos> V139 = new java.util.ArrayList<>();
    private static int[] v9Bars = new int[0], v9Far = new int[0];
    private static BlockPos v9Desk;

    private static void v139() {
        add("-v139 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            mc.options.renderDistance().set(12);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            cam(-344, 207.6, -310.5, -90, -2); }, 160);
        // ---- 1. hung bars (PIXELBAR B16, PULSESTRIKE M, FLASHCORE FX) straight down, white, and a PROFILE P1 (lens)
        add("-v139 bars", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK);
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -300);
            Block[] bs = {StageContent.PIXELBAR_B16, StageContent.PULSESTRIKE_M, StageContent.FLASHCORE_FX, StageContent.PROFILE_P1};
            v9Bars = new int[bs.length];
            for (int i = 0; i < bs.length; i++) {
                BlockPos p = new BlockPos(-336, groundY + 7, -300 + i * 3);
                V139.add(p.above()); V139.add(p);
                v9Bars[i] = fixture(l, SCENE_DESK, bs[i], p, true, Direction.EAST);
            }
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v9Bars); cmd(d, cw.bool(false).toBytes()); }
            set(d, v9Bars, Attr.DIMMER, 1f); rgb(d, v9Bars, 1f, 1f, 1f); set(d, v9Bars, Attr.TILT, 0.5f); set(d, v9Bars, Attr.ZOOM, 0.3f);
            if (d.show.fixture(v9Bars[0]) != null) set(d, v9Bars, Attr.WHITE, 1f); },
            () -> cam(-346, groundY + 4.5, -295.5, -90, -8), 120);
        add("v139_bars_down_side", null, null, 5);
        add("-v139 bars front", null, () -> cam(-336.5, groundY + 4, -306, 0, -20), 40);
        add("v139_bars_down_end", null, null, 5);
        add("-v139 bars below", null, () -> cam(-338.5, groundY + 1.7, -297.5, -90, -55), 40);
        add("v139_bars_down_below", null, null, 5);
        add("-v139 p1 lens", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); set(d, new int[] {v9Bars[3]}, Attr.DIMMER, 0f); set(d, new int[] {v9Bars[3]}, Attr.TILT, 0.5f); },
            () -> cam(-336, groundY + 5.2, -291, 0, -89.5f), 40);
        add("v139_profile_p1_lens", null, null, 5);
        // ---- 2. a desk far away (beyond the view distance): its fixtures here still light (what a second player sees in multiplayer)
        add("-v139 far desk", () -> { ServerLevel l = level();
            int fx = -343 + 420, fz = -310;
            int fy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz);
            v9Desk = new BlockPos(fx, fy, fz);
            l.setBlock(v9Desk, StageContent.LUMICORE.defaultBlockState(), 3);
            LumicoreBlockEntity fd = (LumicoreBlockEntity) l.getBlockEntity(v9Desk);
            Block[] bs = {StageContent.VORTEX_X1, StageContent.PIXELBAR_B16};
            v9Far = new int[bs.length];
            for (int i = 0; i < bs.length; i++) {
                BlockPos p = new BlockPos(-332, groundY + 7, -300 + i * 3);
                V139.add(p.above()); V139.add(p);
                v9Far[i] = fixture(l, v9Desk, bs[i], p, true, Direction.EAST);
            }
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v9Far); cmd(fd, cw.bool(false).toBytes()); }
            set(fd, v9Far, Attr.DIMMER, 1f); rgb(fd, v9Far, 0.1f, 0.3f, 1f); set(fd, v9Far, Attr.TILT, 0.5f); set(fd, v9Far, Attr.ZOOM, 0.3f);
            log(String.format(java.util.Locale.ROOT, "[v139] far desk at %d %d %d (%.0f blocks from the stage), %d fixtures patched", fx, fy, fz, Math.sqrt(v9Desk.distSqr(new BlockPos(-332, groundY, -300))), fd.show.patch.size())); },
            () -> cam(-344, groundY + 4.5, -297, -90, -6), 200);
        add("-v139 far log", null, () -> { Minecraft mc = Minecraft.getInstance();
            boolean loaded = mc.level.isLoaded(v9Desk) && mc.level.getBlockEntity(v9Desk) instanceof LumicoreBlockEntity;
            var fe = (FixtureBlockEntity) mc.level.getBlockEntity(new BlockPos(-332, groundY + 7, -300));
            var o = fe == null ? null : LightClient.output(mc.level, fe, 0f);
            log("[v139] far desk: loaded in this game " + loaded + ", followed copy " + (LightClient.desk(mc.level, v9Desk) != null)
                + ", VORTEX output " + (o == null ? "NONE" : String.format(java.util.Locale.ROOT, "dimmer %.2f", o.get(Attr.DIMMER)))); }, 5);
        add("v139_far_desk_lights", null, null, 5);
        // ---- 3. RGB in every mode (and the shows saved before keep theirs)
        add("-v139 rgb", null, () -> {
            StringBuilder sb = new StringBuilder("[v139] modes:");
            for (var pr : new com.concertstage.light.core.FixtureProfile[] {com.concertstage.light.core.FixtureProfile.VORTEX_X1, com.concertstage.light.core.FixtureProfile.LUMORA_W7,
                    com.concertstage.light.core.FixtureProfile.PIXELWASH_W37, com.concertstage.light.core.FixtureProfile.PIXELBAR_B16, com.concertstage.light.core.FixtureProfile.PIXELTUBE_T1,
                    com.concertstage.light.core.FixtureProfile.PULSESTRIKE_M, com.concertstage.light.core.FixtureProfile.BLINDER_X4}) {
                for (int mo : new int[] {com.concertstage.light.dmx.DmxMode.COMPACT, com.concertstage.light.dmx.DmxMode.ULTRA}) {
                    var m = com.concertstage.light.dmx.DmxMode.of(pr, mo);
                    StringBuilder ch = new StringBuilder();
                    for (var c : m.channels) ch.append(c.name()).append(',');
                    sb.append(String.format(java.util.Locale.ROOT, " %s %s %d ch [%s] saved before -> %s |", pr.name, m.name(), m.channels.length, ch,
                        com.concertstage.light.dmx.DmxMode.NAMES[com.concertstage.light.dmx.DmxMode.migrateRgb(pr, mo)]));
                }
            }
            log(sb.toString());
            var pr = com.concertstage.light.core.FixtureProfile.LUMORA_W7;
            var m = com.concertstage.light.dmx.DmxMode.of(pr, com.concertstage.light.dmx.DmxMode.COMPACT);
            com.concertstage.light.core.Show.Patch pa = new com.concertstage.light.core.Show.Patch(); pa.type = pr.id; pa.universe = 0; pa.address = 1; pa.dmxMode = com.concertstage.light.dmx.DmxMode.COMPACT;
            com.concertstage.light.dmx.DmxData dmx = new com.concertstage.light.dmx.DmxData();
            byte[] u = new byte[512];
            for (int i = 0; i < m.channels.length; i++) { var c = m.channels[i];
                if (c.attr() == Attr.RED) u[i] = (byte) 255; if (c.attr() == Attr.GREEN) u[i] = (byte) 128; if (c.attr() == Attr.BLUE) u[i] = 0; if (c.attr() == Attr.DIMMER) u[i] = (byte) 255; }
            dmx.set(0, u);
            float[] v = new float[Attr.COUNT];
            com.concertstage.light.dmx.DmxDecode.decode(pa, dmx, v, null);
            log(String.format(java.util.Locale.ROOT, "[v139] LUMORA W7 Compact red 255 green 128 blue 0 -> R %.2f G %.2f B %.2f dimmer %.2f", v[Attr.RED.ordinal()], v[Attr.GREEN.ordinal()], v[Attr.BLUE.ordinal()], v[Attr.DIMMER.ordinal()])); }, 5);
        // ---- 4. safety railings on the ground: four closed squares (4 corners, 4 straight 1 m), each with the corners turned one more
        // quarter, seen from above: the one whose corners close the square shows the rails meet on one line
        add("-v139 railings", () -> { ServerLevel l = level();
            var reg = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
            Block corner = reg.get(new net.minecraft.resources.ResourceLocation("concertstage", "safety_railings_corner_1x1"));
            Block rail = reg.get(new net.minecraft.resources.ResourceLocation("concertstage", "safety_railings_1m"));
            Direction[] f = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            for (int k = 0; k < 4; k++) {
                BlockPos c0 = new BlockPos(-344 + k * 4, groundY, -290);
                Object[][] put = {{corner, c0, f[k % 4]}, {corner, c0.east(2), f[(k + 1) % 4]}, {corner, c0.east(2).south(2), f[(k + 2) % 4]},
                    {corner, c0.south(2), f[(k + 3) % 4]}, {rail, c0.east(), Direction.NORTH}, {rail, c0.east().south(2), Direction.SOUTH},
                    {rail, c0.south(), Direction.WEST}, {rail, c0.south().east(2), Direction.EAST}};
                for (Object[] o : put) {
                    Block bl = (Block) o[0]; BlockPos p = (BlockPos) o[1];
                    var st = bl.defaultBlockState().setValue(com.concertstage.stage.StageEquipmentBlock.FACING, (Direction) o[2]);
                    var prop = ((com.concertstage.stage.StageEquipmentBlock) bl).partProperty();
                    l.setBlock(p, st.setValue(prop, 0), 3); l.setBlock(p.above(), st.setValue(prop, 1), 3);
                    V139.add(p.above()); V139.add(p);
                }
            } }, () -> cam(-336.5, groundY + 11, -288.5, 0, 90), 60);
        add("v139_railings_squares", null, null, 5);
        add("-v139 end", () -> { ServerLevel l = level();
            java.util.List<BlockPos> rev = new java.util.ArrayList<>(V139); java.util.Collections.reverse(rev);
            for (BlockPos b : rev) l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            if (v9Desk != null) l.setBlock(v9Desk, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            V139.clear(); }, null, 20);
    }


    // ------------------------------------------------------------ 1.2.0: frame rate of small, medium and large rigs
    private static final java.util.List<BlockPos> V140 = new java.util.ArrayList<>();
    private static BlockPos v10Desk;
    private static volatile long v10Frames, v10Last, v10Max;
    private static final long[] V10_DT = new long[20000];
    private static int v10N;
    private static long v10T0, v10A0, v10C0, v10F0;

    /** Counts the frames drawn (render thread) and their intervals, for the frame rate of the v140 rigs. */
    @SubscribeEvent
    public static void v140Frame(net.minecraftforge.client.event.RenderLevelStageEvent e) {
        if (e.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        long now = System.nanoTime();
        if (v10Last != 0 && v10N < V10_DT.length) V10_DT[v10N++] = now - v10Last;
        v10Last = now;
        v10Frames++;
    }

    private static com.sun.management.ThreadMXBean v10Mx() {
        return (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    }

    private static jdk.jfr.Recording v10Jfr;

    /** A Java Flight Recording of the measured window (CPU samples and allocations of the render thread), saved next to the pictures. */
    private static void v10JfrStart() {
        try {
            v10Jfr = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            v10Jfr.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
            v10Jfr.start();
        } catch (Exception e) { log("[v140] jfr " + e); v10Jfr = null; }
    }

    private static void v10JfrStop(String name) {
        if (v10Jfr == null) return;
        try {
            v10Jfr.stop();
            Path out = Minecraft.getInstance().gameDirectory.toPath().resolve("csc-visual").resolve(name + ".jfr");
            v10Jfr.dump(out);
            log("[v140] flight recording " + out);
        } catch (Exception e) { log("[v140] jfr " + e); }
        v10Jfr.close();
        v10Jfr = null;
    }

    private static void v10Mark() {
        v10T0 = System.nanoTime(); v10F0 = v10Frames; v10N = 0;
        v10A0 = v10Mx().getThreadAllocatedBytes(Thread.currentThread().getId());
        v10C0 = v10Mx().getCurrentThreadCpuTime();
    }

    private static void v10Log(String label, int fixtures) {
        long t = System.nanoTime() - v10T0, f = Math.max(1, v10Frames - v10F0);
        long a = v10Mx().getThreadAllocatedBytes(Thread.currentThread().getId()) - v10A0, c = v10Mx().getCurrentThreadCpuTime() - v10C0;
        long[] dt = java.util.Arrays.copyOf(V10_DT, v10N);
        java.util.Arrays.sort(dt);
        double p99 = dt.length == 0 ? 0 : dt[Math.min(dt.length - 1, (int) (dt.length * 0.99))] / 1e6;
        double low1 = p99 <= 0 ? 0 : 1000.0 / p99;
        log(String.format(java.util.Locale.ROOT, "[v140] %s: %d fixtures | %.1f fps average (%d frames in %.1f s) | 1%% low %.1f fps (frame %.2f ms) | render thread %.2f ms CPU and %.0f KB allocated per frame",
            label, fixtures, f / (t / 1e9), f, t / 1e9, low1, p99, c / 1e6 / f, a / 1024.0 / f));
    }

    // ------------------------------------------------------------ 1.2.1 (15 November): free placement, in-game guide
    private static final java.util.List<BlockPos> V141 = new java.util.ArrayList<>();
    private static BlockPos v11Wall, v11Side, v11Truss, v11Hung, v11Free, v11Tilt;
    private static int[] v11F = new int[0], v11Conv = new int[0];
    private static BlockPos v11Cam;

    /** Places {@code block} through its real placement code (getStateForPlacement + setPlacedBy), as a player clicking {@code hit} on {@code face} of {@code on}. */
    private static BlockPos v11Place(ServerLevel l, Block block, BlockPos on, Direction face, net.minecraft.world.phys.Vec3 hit, boolean sneak, float playerYaw) {
        var pl = level().getServer().getPlayerList().getPlayers().get(0);
        float oldYaw = pl.getYRot();
        pl.setYRot(playerYaw);
        pl.setShiftKeyDown(sneak);
        var stack = new net.minecraft.world.item.ItemStack(block.asItem());
        var ctx = new net.minecraft.world.item.context.BlockPlaceContext(pl, net.minecraft.world.InteractionHand.MAIN_HAND, stack,
            new net.minecraft.world.phys.BlockHitResult(hit, face, on, false));
        BlockPos at = ctx.getClickedPos();
        var st = block.getStateForPlacement(ctx);
        if (st == null) { log("[v141] placement refused at " + at.toShortString()); pl.setShiftKeyDown(false); pl.setYRot(oldYaw); return at; }
        l.setBlock(at, st, 3);
        block.setPlacedBy(l, at, st, pl, stack);
        pl.setShiftKeyDown(false);
        pl.setYRot(oldYaw);
        V141.add(at);
        if (!(l.getBlockEntity(at) instanceof FixtureBlockEntity fe)) { log("[v141] no block entity at " + at.toShortString()); return at; }
        log(String.format(java.util.Locale.ROOT, "[v141] placed %s at %s facing %s hung %s pose off=(%.3f %.3f %.3f) yaw %.1f pitch %.1f roll %.1f",
            block.getDescriptionId(), at.toShortString(), st.getValue(FixtureBlock.FACING), st.getValue(FixtureBlock.HUNG),
            fe.offX, fe.offY, fe.offZ, fe.rotYaw, fe.rotPitch, fe.rotRoll));
        return at;
    }

    private static void v141() {
        add("-v141 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            mc.options.renderDistance().set(12);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            cam(-341, 207.6, -310, -90, -4); }, 100);
        add("-v141 scene dark", () -> { if (level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d) {
            int[] f = d.show.patch.stream().mapToInt(p -> p.fid).toArray();
            set(d, f, Attr.DIMMER, 0f); } }, null, 20);
        add("-v141 build", () -> { ServerLevel l = level();
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -334, -300);
            var stone = net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState();
            // 1. a wall: a moving head mounted on its east side (sneak + click the side)
            v11Wall = new BlockPos(-336, groundY + 3, -304);
            for (int y = 0; y < 5; y++) for (int z = -1; z <= 1; z++) { BlockPos w = new BlockPos(-336, groundY + y, -304 + z); l.setBlock(w, stone, 3); V141.add(w); }
            v11Side = v11Place(l, StageContent.VORTEX_X1, v11Wall, Direction.EAST, new net.minecraft.world.phys.Vec3(-335.0, groundY + 3.5, -303.5), true, 90f);
            // 2. a truss: a wash hung under it the standard way, a second one sneaking (exactly where aimed, near the edge)
            v11Truss = new BlockPos(-333, groundY + 6, -300);
            for (int z = -1; z <= 2; z++) { BlockPos t = new BlockPos(-333, groundY + 6, -300 + z); l.setBlock(t, StageContent.TRUSS_LADDER_L1.defaultBlockState(), 3); V141.add(t); }
            v11Hung = v11Place(l, StageContent.LUMORA_W7, v11Truss, Direction.DOWN, new net.minecraft.world.phys.Vec3(-332.5, groundY + 6.0, -299.5), false, 90f);
            v11Free = v11Place(l, StageContent.LUMORA_W7, v11Truss.south(), Direction.DOWN, new net.minecraft.world.phys.Vec3(-332.75, groundY + 6.0, -298.6), true, 60f);
            // 3. a fixture on the floor sneaking (off centre, turned 30°), then tilted and rolled with the position screen values
            v11Tilt = v11Place(l, StageContent.PIXELBAR_B16, new BlockPos(-331, groundY - 1, -303), Direction.UP, new net.minecraft.world.phys.Vec3(-330.3, groundY, -302.3), true, 120f);
            if (l.getBlockEntity(v11Tilt) instanceof FixtureBlockEntity tf) tf.setPose(0.2f, 0.4f, 0.3f, 30f, 25f, -20f);
            LumicoreBlockEntity d = desk(SCENE_DESK);
            java.util.List<Integer> fs = new java.util.ArrayList<>();
            for (BlockPos p : new BlockPos[] {v11Side, v11Hung, v11Free, v11Tilt}) {
                if (!(l.getBlockEntity(p) instanceof FixtureBlockEntity fe)) { log("[v141] no fixture at " + p); continue; }
                if (!LightServer.patch(l, d, fe)) log("[v141] patch failed " + p);
                fs.add(fe.fid());
            }
            v11F = ints(fs);
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v11F); cmd(d, cw.bool(false).toBytes()); }
            set(d, v11F, Attr.DIMMER, 1f); rgb(d, v11F, 1f, 0.85f, 0.6f); set(d, v11F, Attr.ZOOM, 0.25f);
            set(d, v11F, Attr.TILT, 0.5f); set(d, v11F, Attr.PAN, 0.5f);
            for (int f : v11F) { var pt = d.show.fixture(f);
                if (pt != null) log(String.format(java.util.Locale.ROOT, "[v141] desk patch %d yaw %.1f mount %d pose (%.3f %.3f %.3f) pitch %.1f roll %.1f posed %s",
                    f, pt.yaw, pt.mount, pt.ox, pt.oy, pt.oz, pt.pitch, pt.roll, pt.posed())); } },
            () -> cam(-326.5, groundY + 3.5, -301, 90, -8), 140);
        add("v141_free_placement_overview", null, null, 5);
        // 7. a show from before 1.2.0: a wash in the hue / saturation Compact and a spot in the colour macro Ultra Compact move to the
        //    current RGB layouts when nothing is in the way; one squeezed against another fixture keeps its old layout
        add("-v141 legacy dmx", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            com.concertstage.light.core.Show sh = com.concertstage.light.core.Show.fromBytes(d.show.toBytes());
            java.util.List<com.concertstage.light.core.Show.Patch> ps = sh.patch;
            int[] fids = {v11F[1], v11F[2], v11F[0]};
            int[] modes = {com.concertstage.light.dmx.DmxMode.COMPACT_HS, com.concertstage.light.dmx.DmxMode.COMPACT_HS, com.concertstage.light.dmx.DmxMode.ULTRA_M};
            int[] addr = {401, 0, 301};
            for (int i = 0; i < 3; i++) { var pt = sh.fixture(fids[i]); if (pt == null) continue;
                pt.universe = 9; pt.dmxMode = modes[i];
                pt.address = i == 1 ? 401 + com.concertstage.light.dmx.DmxMode.of(pt.profile(), modes[i]).footprint() : addr[i];   // right after the first wash
                log(String.format(java.util.Locale.ROOT, "[v141] legacy before: fid %d %s mode %s footprint %d at 9.%d", pt.fid, pt.type,
                    com.concertstage.light.dmx.DmxMode.NAMES[pt.dmxMode], com.concertstage.light.dmx.DmxMode.of(pt.profile(), pt.dmxMode).footprint(), pt.address)); }
            sh.upgradeLegacyDmx();
            for (int f : fids) { var pt = sh.fixture(f); if (pt == null) continue;
                log(String.format(java.util.Locale.ROOT, "[v141] legacy after: fid %d mode %s footprint %d at 9.%d", pt.fid,
                    com.concertstage.light.dmx.DmxMode.NAMES[pt.dmxMode], com.concertstage.light.dmx.DmxMode.of(pt.profile(), pt.dmxMode).footprint(), pt.address)); }
            log("[v141] legacy upgraded " + sh.legacyUpgraded + " kept " + sh.legacyKept); }, null, 5);
        add("-v141 wall close", null, () -> cam(-331, groundY + 4, -304.5, 90, -8), 40);
        add("v141_wall_side_mount", null, null, 5);
        add("-v141 truss close", null, () -> cam(-329, groundY + 4.5, -299.5, 90, 18), 40);
        add("v141_truss_hung_standard_and_free", null, null, 5);
        add("-v141 tilt close", null, () -> cam(-327, groundY + 1.8, -302.5, 90, 5), 40);
        add("v141_floor_tilted_rolled", null, null, 5);
        // 4. the desk aims the posed heads: focus every fixture on one floor point, then log the aim error of each beam
        add("-v141 focus", () -> { LumicoreBlockEntity d = desk(SCENE_DESK);
            for (int f : v11F) { var pt = d.show.fixture(f); if (pt == null) continue;
                float[] pt2 = com.concertstage.light.core.Movement.aim(pt, -330.5, groundY + 0.0, -300.5);
                if (pt2 != null) { set(d, new int[] {f}, Attr.PAN, (float) pt2[0]); set(d, new int[] {f}, Attr.TILT, (float) pt2[1]); }
                log(String.format(java.util.Locale.ROOT, "[v141] focus fid %d -> %s", f, pt2 == null ? "unreachable" : String.format(java.util.Locale.ROOT, "pan %.3f tilt %.3f", pt2[0], pt2[1]))); } },
            () -> cam(-322, groundY + 6, -300.5, 90, 35), 80);
        add("v141_focus_one_point", null, null, 5);
        // 4b. 1.2.1 conventionals: PAR, Fresnel, PC, ellipsoidal hung on a bar (tilted 40° down, a gel each) and the same four standing
        add("-v141 conventionals", () -> { ServerLevel l = level(); LumicoreBlockEntity d = desk(SCENE_DESK);
            Block[] bs = {StageContent.PAR_P64, StageContent.FRESNEL_F1, StageContent.PC_C1, StageContent.ELLIPSOIDAL_E1};
            int[] gels = {0xFFFFFF, 0xFF2A1A, 0x2B4BFF, 0xFFB347};
            java.util.List<Integer> fs = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                BlockPos hp = new BlockPos(-331, groundY + 5, -298 + i * 2), sp = new BlockPos(-331, groundY, -297 + i * 2);
                V141.add(hp.above()); V141.add(hp); V141.add(sp);
                for (BlockPos p : new BlockPos[] {hp, sp}) {
                    int f = fixture(l, SCENE_DESK, bs[i], p, p == hp, Direction.EAST);
                    fs.add(f);
                    if (l.getBlockEntity(p) instanceof FixtureBlockEntity fe) { fe.setManualTilt(p == hp ? 40f : 25f); fe.setGel(p == hp ? gels[i] : 0xFFFFFF); }
                }
            }
            v11Conv = ints(fs);
            { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, v11Conv); cmd(d, cw.bool(false).toBytes()); }
            set(d, v11Conv, Attr.DIMMER, 1f);
            for (int f : v11Conv) { var pt = d.show.fixture(f); var dm = pt == null ? null : com.concertstage.light.dmx.DmxMode.of(pt.profile(), pt.dmxMode);
                if (pt != null) log(String.format(java.util.Locale.ROOT, "[v141] conventional fid %d %s mode %s footprint %d gel %06X",
                    f, pt.type, com.concertstage.light.dmx.DmxMode.NAMES[pt.dmxMode], dm.footprint(), pt.gel)); } },
            () -> cam(-321, groundY + 5, -294.5, 90, 22), 120);
        add("v141_conventionals", null, null, 5);
        add("-v141 conventionals close", null, () -> cam(-328.5, groundY + 4.2, -294.5, 70, 8), 40);
        add("v141_conventionals_close", null, null, 5);
        add("-v141 conventionals half", () -> set(desk(SCENE_DESK), v11Conv, Attr.DIMMER, 0.25f), null, 30);
        add("v141_conventionals_25pct", null, null, 5);
        // 4c. 1.2.1: a camera looking at the conventionals, with particles in front of it (fireworks, sparks): the camera image must
        //     show the fixtures, their light and the particles
        add("-v141 feed cam", () -> { ServerLevel l = level();
            v11Cam = camera(l, StageContent.CAM_PTZ_P1, new BlockPos(-322, groundY, -294), Direction.WEST, 41, "V141");
            V141.add(v11Cam); }, () -> com.concertstage.client.vision.CameraFeeds.frame(v11Cam), 20);
        for (int k = 0; k < 12; k++) add("-v141 feed keep " + k, null, () -> {
            com.concertstage.client.vision.CameraFeeds.frame(v11Cam);
            var lv = Minecraft.getInstance().level;
            java.util.Random rnd = new java.util.Random(141);
            for (int i = 0; i < 30; i++)
                lv.addParticle(i % 2 == 0 ? net.minecraft.core.particles.ParticleTypes.END_ROD : net.minecraft.core.particles.ParticleTypes.FLAME,
                    -326.5 + rnd.nextGaussian() * 0.8, groundY + 2.5 + rnd.nextGaussian() * 0.8, -293.5 + rnd.nextGaussian() * 0.8, 0, 0.01, 0);
        }, 60);
        add("-v141 feed png", null, () -> log("[v141] camera image saved: " + com.concertstage.client.vision.CameraFeeds.saveImage(v11Cam,
            Minecraft.getInstance().gameDirectory.toPath().resolve("csc-visual").resolve("v141_feed_conventionals_particles.png"))), 5);
        add("-v141 lv settings", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.vision.VisionSettingsScreen(null)), 30);
        add("v141_lv_settings_screen", null, null, 5);
        add("-v141 gel screen", null, () -> com.concertstage.client.light.FixtureAngleScreen.open(new BlockPos(-331, groundY + 5, -296), -40f, "CSC FRESNEL F1", Float.NaN), 30);
        add("v141_gel_screen", null, null, 5);
        add("-v141 gel screen close", null, () -> Minecraft.getInstance().setScreen(null), 10);
        // 5. the position screen and the guide
        add("-v141 pose screen", null, () -> com.concertstage.client.light.FixturePoseScreen.open(v11Tilt, "PIXELBAR B16", true), 30);
        add("v141_pose_screen", null, null, 5);
        add("-v141 guide", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.ui.GuideScreen(null)), 30);
        add("v141_guide_top", null, null, 5);
        add("-v141 guide scroll", null, () -> { var sc = Minecraft.getInstance().screen; if (sc != null) for (int i = 0; i < 40; i++) sc.mouseScrolled(300, 200, -1); }, 20);
        add("v141_guide_scrolled", null, null, 5);
        add("-v141 guide fr", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.getLanguageManager().setSelected("fr_fr"); mc.setScreen(new com.concertstage.client.ui.GuideScreen(null)); }, 30);
        add("v141_guide_fr", null, null, 5);
        add("-v141 guide close", null, () -> { Minecraft mc = Minecraft.getInstance(); mc.getLanguageManager().setSelected("en_us"); mc.setScreen(null); }, 20);
        // 6. the built-in FFmpeg: found, unpacked, makes a test video in the media folder and reads 10 frames of it back
        add("-v141 ffmpeg", null, () -> new Thread(() -> { try {
            Class<?> c = Class.forName("com.concertstage.client.vision.MediaLibrary$Ffmpeg");
            var mp = c.getDeclaredMethod("path"); mp.setAccessible(true);
            var ms = c.getDeclaredMethod("source"); ms.setAccessible(true);
            long t0 = System.nanoTime();
            String exe = (String) mp.invoke(null);
            log(String.format(java.util.Locale.ROOT, "[v141] ffmpeg path %s source %s (%.0f ms)", exe, ms.invoke(null), (System.nanoTime() - t0) / 1e6));
            if (exe == null) return;
            var mpb = c.getDeclaredMethod("process", java.util.List.class); mpb.setAccessible(true);
            java.nio.file.Path media = com.concertstage.client.vision.MediaLibrary.localFolder();
            java.nio.file.Files.createDirectories(media);
            java.nio.file.Path mp4 = media.resolve("csc_test_v141.mp4").toAbsolutePath();
            Process pr = ((ProcessBuilder) mpb.invoke(null, java.util.List.of(exe, "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=4:size=640x360:rate=30",
                "-pix_fmt", "yuv420p", mp4.toString()))).redirectErrorStream(true).start();
            String err = new String(pr.getInputStream().readAllBytes()).trim();
            log("[v141] ffmpeg encode exit " + pr.waitFor() + " size " + (java.nio.file.Files.exists(mp4) ? java.nio.file.Files.size(mp4) : -1) + (err.isEmpty() ? "" : " : " + err));
            pr = ((ProcessBuilder) mpb.invoke(null, java.util.List.of(exe, "-v", "error", "-i", mp4.toString(), "-frames:v", "10", "-f", "rawvideo", "-pix_fmt", "rgba", "-")))
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            long n = pr.getInputStream().readAllBytes().length;
            log("[v141] ffmpeg decode exit " + pr.waitFor() + " bytes " + n + " (10 frames 640x360 RGBA = " + (10L * 640 * 360 * 4) + ")");
            pr = ((ProcessBuilder) mpb.invoke(null, java.util.List.of(exe, "-version"))).redirectErrorStream(true).start();
            String v = new String(pr.getInputStream().readAllBytes());
            pr.waitFor();
            log("[v141] ffmpeg build: gpl " + v.contains("--enable-gpl") + ", nonfree " + v.contains("--enable-nonfree") + ", version3 " + v.contains("--enable-version3"));
            for (String line : v.split("\\R")) if (line.startsWith("ffmpeg version") || line.startsWith("configuration")) log("[v141] " + (line.length() > 400 ? line.substring(0, 400) : line));
        } catch (Throwable e) { log("[v141] ffmpeg test failed: " + e); } }, "v141 ffmpeg").start(), 200);
        add("-v141 end", () -> { ServerLevel l = level();
            for (int i = V141.size() - 1; i >= 0; i--) l.setBlock(V141.get(i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            V141.clear(); }, null, 20);
    }

    private static void v140() {
        add("-v140 load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            mc.options.renderDistance().set(12);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            cam(-341, 207.6, -310, -90, -4); }, 100);
        // the stage of the scene dark: only the rig under test lights
        add("-v140 scene dark", () -> { if (level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d) {
            int[] f = d.show.patch.stream().mapToInt(p -> p.fid).toArray();
            set(d, f, Attr.DIMMER, 0f); } }, null, 20);
        Block[] mix = {StageContent.VORTEX_X1, StageContent.LUMORA_W7, StageContent.PIXELBAR_B16, StageContent.LUMORA_W19, StageContent.PHANTOM_S1, StageContent.BLINDER_X4};
        int[] sizes = {24, 96, 288};
        String[] names = {"small", "medium", "large"};
        for (int k = 0; k < sizes.length; k++) {
            final int n = sizes[k]; final String nm = names[k];
            add("-v140 build " + nm, () -> { ServerLevel l = level();
                for (int i = V140.size() - 1; i >= 0; i--) l.setBlock(V140.get(i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                V140.clear();
                if (v10Desk != null) l.setBlock(v10Desk, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -300);
                v10Desk = new BlockPos(-330, groundY, -318);
                l.setBlock(v10Desk, StageContent.LUMICORE.defaultBlockState(), 3);
                LumicoreBlockEntity d = (LumicoreBlockEntity) l.getBlockEntity(v10Desk);
                java.util.Random rnd = new java.util.Random(140);
                int[] f = new int[n];
                for (int i = 0; i < n; i++) {
                    BlockPos p = new BlockPos(-338 - 2 * (i / 24), groundY + 14, -313 + i % 24);
                    V140.add(p.above()); V140.add(p);
                    f[i] = fixture(l, v10Desk, mix[i % mix.length], p, true, Direction.EAST);
                }
                { var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, f); cmd(d, cw.bool(false).toBytes()); }
                set(d, f, Attr.DIMMER, 1f); set(d, f, Attr.ZOOM, 0.4f);
                for (int i = 0; i < n; i++) {
                    int[] one = {f[i]};
                    float h = (i * 0.137f) % 1f;
                    rgb(d, one, Math.max(0f, Math.abs(h * 6 - 3) - 1), Math.max(0f, 2 - Math.abs(h * 6 - 2)), Math.max(0f, 2 - Math.abs(h * 6 - 4)));
                    set(d, one, Attr.TILT, 0.35f + 0.3f * rnd.nextFloat());
                    set(d, one, Attr.PAN, 0.3f + 0.4f * rnd.nextFloat());
                }
                log("[v140] " + nm + ": " + d.show.patch.size() + " fixtures patched"); },
                () -> cam(-324, groundY + 3, -301, 90, -14), 200);
            add("-v140 mark " + nm, null, () -> { if (n >= 288) v10JfrStart(); v10Mark(); }, 200);
            add("v140_" + nm, null, () -> { v10Log(nm + " rig, audience view", n); v10JfrStop("v140_" + nm); }, 5);
            add("-v140 mark far " + nm, null, () -> { cam(-300, groundY + 6, -301, 90, -6); }, 120);
            add("-v140 mark far2 " + nm, null, CSCVisualTest::v10Mark, 200);
            add("v140_" + nm + "_far", null, () -> v10Log(nm + " rig, far view", n), 5);
        }
        add("-v140 end", () -> { ServerLevel l = level();
            for (int i = V140.size() - 1; i >= 0; i--) l.setBlock(V140.get(i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            V140.clear();
            if (v10Desk != null) l.setBlock(v10Desk, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            if (level().getBlockEntity(SCENE_DESK) instanceof LumicoreBlockEntity d) {
                var cw = Commands.op(Commands.P_CLEAR); Commands.fids(cw, d.show.patch.stream().mapToInt(p -> p.fid).toArray()); cmd(d, cw.bool(false).toBytes()); } },
            null, 20);
    }
    private static int[] v3White = new int[0];
    private static int v3Fov;

    private static void logWhite(String at) {
        var d = (LumicoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(SCENE_DESK);
        var outs = d.lastOutputs();
        StringBuilder sb = new StringBuilder("[v133] " + at + ":");
        for (int f : v3White) {
            var o = outs.get(f);
            var pa = d.show.fixture(f);
            if (o == null || pa == null) { sb.append(" #").append(f).append(" no output"); continue; }
            var pr = pa.profile();
            float white = 0; int nw = 0;
            for (int z = 0; z < pr.zones; z++) if (pr.zoneKind(z) == 1) { white += o.zones[z * 4 + 3]; nw++; }
            sb.append(String.format(java.util.Locale.ROOT, " | %s dimmer %.2f white zones level %.2f", pr.name, o.get(Attr.DIMMER), nw == 0 ? -1 : white / nw));
        }
        log(sb.toString());
    }

    private static void logGobo(String at) {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder("[v132] gobo angles " + at + ":");
        for (int k : new int[] {4, 5}) if (mc.level.getBlockEntity(v2Pos[k]) instanceof FixtureBlockEntity fe)
            sb.append(String.format(java.util.Locale.ROOT, " %s %.1f° (wheel1 %.1f, wheel2 %.1f)", k == 4 ? "RAZOR" : "PHANTOM", fe.goboAngle, fe.wheel1Angle, fe.wheel2Angle));
        log(sb.toString());
    }

    // a small sampling profiler of the render thread while a camera image is drawn (1.3.0 rev. 1 measurements)
    private static volatile Thread renderThread;
    private static volatile boolean sampling;
    private static final java.util.Map<String, Integer> SELF = new java.util.concurrent.ConcurrentHashMap<>(), INCL = new java.util.concurrent.ConcurrentHashMap<>();
    private static int samples;

    private static void startSampler() {
        renderThread = Thread.currentThread();
        if (sampling) return;
        sampling = true;
        Thread t = new Thread(() -> {
            while (sampling) {
                try { Thread.sleep(1); } catch (InterruptedException e) { return; }
                if (!com.concertstage.client.vision.CameraFeeds.rendering) continue;
                StackTraceElement[] st = renderThread.getStackTrace();
                if (st.length == 0) continue;
                samples++;
                SELF.merge(st[0].getClassName() + "." + st[0].getMethodName(), 1, Integer::sum);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (int i = 0; i < Math.min(st.length, 40); i++) {
                    String k = st[i].getClassName() + "." + st[i].getMethodName();
                    if (seen.add(k)) INCL.merge(k, 1, Integer::sum);
                }
            }
        }, "CSC visual sampler");
        t.setDaemon(true);
        t.start();
    }

    private static void logSamples(String at) {
        StringBuilder sb = new StringBuilder("[profile] " + at + ": " + samples + " samples\n  self:");
        SELF.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(15).forEach(e -> sb.append("\n    ").append(e.getValue()).append(' ').append(e.getKey()));
        sb.append("\n  inclusive:");
        INCL.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40).forEach(e -> sb.append("\n    ").append(e.getValue()).append(' ').append(e.getKey()));
        log(sb.toString());
        SELF.clear(); INCL.clear(); samples = 0;
    }

    /** 1.3.0 rev. 1, second pass: the camera on a tripod height, the cost of each part of a camera image, the scanned laser figures. */
    private static void v131b() {
        add("-v131b load", null, () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST); mc.levelRenderer.allChanged();
            mc.options.enableVsync().set(false); mc.getWindow().updateVsync(false); mc.options.framerateLimit().set(260); mc.getWindow().setFramerateLimit(260);
            LightClient.autoPerformance = false; LightClient.beamQuality = 2; LightClient.haze = LightClient.HAZE_PRESETS[2];
            com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED);
            cam(-341, 207.6, -310, -90, -4); }, 160);
        add("-v131b rig", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); ServerLevel l = level(); clear(d);
            all = d.show.patch.stream().mapToInt(p -> p.fid).toArray();
            groundY = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -336, -300);
            fill(l, -336, groundY, -300, -336, groundY + 1, -300, "minecraft:black_concrete");
            feedCam = camera(l, StageContent.CAM_PTZ_P1, new BlockPos(-336, groundY + 2, -300), Direction.EAST, 9, "V131");
            log("[v131b] camera at " + feedCam + ", " + all.length + " fixtures");
            float[][] cols = {{1f, 0.1f, 0.6f}, {0.2f, 0.5f, 1f}, {1f, 0.6f, 0.1f}, {1f, 1f, 1f}};
            int i = 0;
            for (var p : d.show.patch) { int[] one = {p.fid}; float[] c = cols[i++ % cols.length];
                aim(d, p.fid, -318 + (p.z - SZ) * 0.15, SY - 1, p.z + 2); set(d, one, Attr.DIMMER, 1f); rgb(d, one, c[0], c[1], c[2]); set(d, one, Attr.ZOOM, 0.35f); }
            ((com.concertstage.vision.CameraBlockEntity) l.getBlockEntity(feedCam)).moveTo(new Float[] {0f, 6f, 0.05f, null, null}, l.getGameTime()); },
            () -> cam(-341, 207.6, -310, -90, -4), 200);
        add("-v131b feed shot", null, () -> { feedShot("v131b_feed_rig"); log("[v131b] " + com.concertstage.client.vision.VisionStats.phases()); }, 5);
        add("v131b_main_rig", null, null, 5);
        add("-v131b nofeed", null, () -> { feedCam0 = feedCam; feedCam = null; }, 200);
        add("#v131bperf no camera image", null, null, 5);
        add("-v131b old", null, () -> { feedCam = feedCam0; com.concertstage.client.vision.CameraFeeds.testNoFixtures = true; com.concertstage.client.vision.VisionStats.phases(); }, 200);
        add("#v131bperf camera image without fixtures (1.3.0)", null, () -> log("[v131b] no fixtures: " + com.concertstage.client.vision.VisionStats.phases() + " | " + lvStats()), 5);
        add("-v131b new", null, () -> { com.concertstage.client.vision.CameraFeeds.testNoFixtures = false; com.concertstage.client.vision.VisionStats.phases();
            startSampler(); SELF.clear(); INCL.clear(); samples = 0; }, 200);
        add("#v131bperf camera image with fixtures and light", null, () -> { log("[v131b] fixtures and light: " + com.concertstage.client.vision.VisionStats.phases() + " | " + lvStats());
            logSamples("camera images"); sampling = false; }, 5);
        add("-v131b nolight", null, () -> { com.concertstage.client.vision.VisionSettings.feedBeams = false; com.concertstage.client.vision.VisionStats.phases(); }, 200);
        add("#v131bperf camera image fixtures without light", null, () -> log("[v131b] fixtures only: " + com.concertstage.client.vision.VisionStats.phases() + " | " + lvStats()), 5);
        add("-v131b light back", null, () -> com.concertstage.client.vision.VisionSettings.feedBeams = true, 5);
        // the scanned figures with their sheets
        add("-v131b lasers", () -> { LumicoreBlockEntity d = desk(SCENE_DESK); ServerLevel l = level(); clear(d);
            java.util.List<Integer> f = new java.util.ArrayList<>();
            laserPos = new BlockPos[3];
            for (int k = 0; k < 3; k++) {
                int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -330, -316 + k * 6);
                laserPos[k] = new BlockPos(-330, gy, -316 + k * 6);
                f.add(fixture(l, SCENE_DESK, StageContent.LASERCORE_L1, laserPos[k], false, Direction.EAST));
            }
            lasers = ints(f);
            for (BlockPos p : laserPos) if (l.getBlockEntity(p) instanceof FixtureBlockEntity fe) fe.setManualTilt(8f);
            lasersOn(0.5f, 0.6f, 0.2f); },
            () -> { feedCam = null; com.concertstage.client.light.FixtureRenderer.legacyLaser = false; cam(-338, groundY + 3, -328, -51, -6); }, 120);
        String[] names = {"circle", "cross", "square", "fan"};
        float[] pv = {0.5f, 0.7f, 0.9f, 0.3f};
        for (int k = 0; k < names.length; k++) {
            final float v = pv[k]; final String nm = names[k];
            add("-v131b laser " + nm, () -> lasersOn(v, 0.6f, 0.2f), null, 50);
            add("v131b_laser_" + nm + "_side", null, () -> logLaser(nm), 10);
        }
        add("-v131b laser feed", () -> lasersOn(0.5f, 0.6f, 0.2f), () -> { feedCam = feedCam0; com.concertstage.client.vision.VisionStats.phases(); }, 100);
        add("-v131b laser feed shot", null, () -> { feedShot("v131b_feed_lasers"); log("[v131b] lasers: " + com.concertstage.client.vision.VisionStats.phases()); }, 5);
        // the Shows page of the desk at GUI scale 2 (the author's picture of 12 November: overlapping columns and buttons)
        add("-v131b gui", null, () -> { Minecraft mc = Minecraft.getInstance(); feedCam = null;
            try {
                var f = com.concertstage.client.light.LumicoreScreen.class.getDeclaredField("lastTab");
                f.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"}) Object shows = Enum.valueOf((Class) Class.forName("com.concertstage.client.light.LumicoreScreen$Tab"), "SHOWS");
                f.set(null, shows);
            } catch (ReflectiveOperationException e) { log("[v131b] tab " + e); }
            mc.options.hideGui = false; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.setScreen(new com.concertstage.client.light.LumicoreScreen(SCENE_DESK)); }, 40);
        add("v131b_lumicore_shows_gui2", null, null, 5);
        add("-v131b gui3", null, () -> { Minecraft mc = Minecraft.getInstance(); mc.options.guiScale().set(3); mc.resizeDisplay(); }, 20);
        add("v131b_lumicore_shows_gui3", null, null, 5);
        add("-v131b gui close", null, () -> { Minecraft mc = Minecraft.getInstance(); mc.setScreen(null); mc.options.guiScale().set(0); mc.resizeDisplay(); mc.options.hideGui = true; }, 10);
        add("-v131b gone", () -> { ServerLevel l = level(); clear(desk(SCENE_DESK));
            for (BlockPos p : laserPos) if (l.getBlockState(p).getBlock() instanceof FixtureBlock) l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            if (feedCam0 != null) { l.setBlock(feedCam0, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                fill(l, -336, feedCam0.getY() - 2, -300, -336, feedCam0.getY() - 1, -300, "minecraft:air"); } },
            () -> feedCam = null, 20);
    }


    private static void v13() {
        add("-v13 load", () -> run(server(), "time set 1000"), () -> { Minecraft mc = Minecraft.getInstance();
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(260); mc.getWindow().updateVsync(false); mc.getWindow().setFramerateLimit(260);
            com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED);
            cam(VX + 4, -50, VZ - 16, 0, 10); }, 120);
        // A. 8 x 5 wall = ONE wall, one picture; holes; split
        add("-v13 A build", () -> { ServerLevel l = level();
            log("[v13] ground " + l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, VX, VZ));
            fill(l, VX - 20, vy - 1, VZ - 30, VX + 70, vy - 1, VZ + 30, "minecraft:gray_concrete");
            for (int x = 0; x < 8; x++) for (int y = 0; y < 5; y++)
                l.setBlock(new BlockPos(VX + x, vy + y, VZ), StageContent.LED_PANEL_P1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.NORTH), 3);
            long t0 = System.nanoTime();
            com.concertstage.vision.WallManager.panelChanged(l, new BlockPos(VX, vy, VZ));
            log(String.format(java.util.Locale.ROOT, "[v13] A assemble 40 panels: %.2f ms", (System.nanoTime() - t0) / 1e6));
            logWalls("A after 8x5");
            vProc = new BlockPos(VX - 3, vy, VZ - 6);
            l.setBlock(vProc, StageContent.VISION_PROCESSOR_VP1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.NORTH), 3);
            var w = walls().walls.values().iterator().next();
            w.processor = vProc.asLong(); w.output = 0; walls().setDirty();
            com.concertstage.vision.VisionNet.wallChanged(l, w);
            program(0, com.concertstage.vision.OutputState.GRID); },
            () -> cam(VX + 4, vy + 2.5, VZ - 13, 0, 0), 80);
        add("v13_A_wall_8x5_grid", null, () -> log("[v13] A client " + lvStats()), 10);
        add("-v13 A bars", () -> program(0, com.concertstage.vision.OutputState.BARS), null, 40);
        add("v13_A_wall_8x5_bars", null, null, 10);
        add("-v13 A night", () -> run(server(), "time set 18000"), null, 20);
        add("v13_A_wall_8x5_bars_night", null, null, 10);
        add("-v13 A day", () -> run(server(), "time set 1000"), null, 10);
        add("-v13 A hole", () -> { level().setBlock(new BlockPos(VX + 3, vy + 2, VZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); logWalls("A hole (3,2)"); }, null, 40);
        add("v13_A_wall_hole", null, null, 10);
        add("-v13 A split", () -> { ServerLevel l = level();
            for (int y = 0; y < 5; y++) l.setBlock(new BlockPos(VX + 5, vy + y, VZ), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            logWalls("A split column 5"); }, null, 40);
        add("v13_A_wall_split", null, null, 10);
        add("-v13 A restore", () -> { ServerLevel l = level();
            for (int y = 0; y < 5; y++) l.setBlock(new BlockPos(VX + 5, vy + y, VZ), StageContent.LED_PANEL_P1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.NORTH), 3);
            l.setBlock(new BlockPos(VX + 3, vy + 2, VZ), StageContent.LED_PANEL_P1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.NORTH), 3);
            com.concertstage.vision.WallManager.panelChanged(l, new BlockPos(VX + 5, vy, VZ));
            com.concertstage.vision.WallManager.panelChanged(l, new BlockPos(VX + 3, vy + 2, VZ));
            logWalls("A restored");
            for (var w : walls().walls.values()) if (w.processor == 0) { w.processor = vProc.asLong(); w.output = 0; com.concertstage.vision.VisionNet.wallChanged(l, w); } }, null, 40);
        add("v13_A_wall_restored", null, null, 10);
        // accessories on the bottom row / a single panel
        add("-v13 A acc", () -> { ServerLevel l = level();
            for (int k = 0; k < 3; k++) {
                BlockPos p = new BlockPos(VX - 8 + k * 2, vy, VZ);
                l.setBlock(p, StageContent.LED_PANEL_P1.defaultBlockState().setValue(com.concertstage.vision.VisionBlock.FACING, Direction.NORTH), 3);
                com.concertstage.vision.WallManager.panelChanged(l, p);
                ((com.concertstage.vision.LedPanelBlockEntity) l.getBlockEntity(p)).toggleMount(1 << k);
            } }, () -> cam(VX - 3, vy + 1.4, VZ + 3.5, 140, 12), 40);
        add("v13_A_accessories", null, null, 10);
        // C. cameras, regie, transitions, auto switcher, media
        add("-v13 C build", () -> { ServerLevel l = level();
            for (int k = 0; k < 6; k++) fill(l, VX + 26 + k * 2, vy, VZ + 8, VX + 26 + k * 2, vy + 2 + k, VZ + 8,
                new String[] {"minecraft:red_wool", "minecraft:orange_wool", "minecraft:yellow_wool", "minecraft:lime_wool", "minecraft:light_blue_wool", "minecraft:purple_wool"}[k]);
            vPtz = camera(l, StageContent.CAM_PTZ_P1, new BlockPos(VX + 31, vy, VZ - 4), Direction.SOUTH, 1, "PTZ");
            vCrane = camera(l, StageContent.CRANE_C1, new BlockPos(VX + 34, vy, VZ - 9), Direction.SOUTH, 2, "CRANE");
            camera(l, StageContent.BROADCAST_B1, new BlockPos(VX + 24, vy, VZ - 3), Direction.SOUTH, 3, "B1");
            camera(l, StageContent.REMOTE_R1, new BlockPos(VX + 38, vy, VZ - 3), Direction.SOUTH, 4, "R1");
            ((com.concertstage.vision.CameraBlockEntity) l.getBlockEntity(vPtz)).moveTo(new Float[] {0f, 8f, 0.2f, null, null}, l.getGameTime());
            ((com.concertstage.vision.CameraBlockEntity) l.getBlockEntity(vCrane)).moveTo(new Float[] {0f, -20f, 0.1f, 25f, 0f}, l.getGameTime());
            program(0, com.concertstage.vision.OutputState.cam(vPtz)); }, () -> cam(VX + 33, vy + 4, VZ - 16, 20, 15), 80);
        add("v13_C_cameras_models", null, null, 10);
        add("-v13 C feed", null, () -> cam(VX + 4, vy + 2.5, VZ - 13, 0, 0), 60);
        add("v13_C_wall_ptz_feed", null, () -> log("[v13] C client " + lvStats()), 30);
        add("-v13 C regie", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.vision.RegieScreen(vProc)), 40);
        add("v13_C_regie_screen", null, null, 4);
        add("-v13 C wall gui", null, () -> { var w = walls().walls.values().iterator().next();
            Minecraft.getInstance().setScreen(new com.concertstage.client.vision.WallScreen(new BlockPos(VX, vy, VZ), w.id)); }, 30);
        add("v13_C_wall_screen", null, null, 4);
        add("-v13 C cam gui", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.vision.CameraScreen(vPtz)), 30);
        add("v13_C_camera_screen", null, null, 4);
        add("-v13 C settings gui", null, () -> Minecraft.getInstance().setScreen(new com.concertstage.client.vision.VisionSettingsScreen(null)), 30);
        add("v13_C_settings_screen", null, null, 4);
        add("-v13 C ring gui", () -> { ServerLevel l = level(); BlockPos p = new BlockPos(VX + 50, vy + 1, VZ - 6);
                if (!(l.getBlockEntity(p) instanceof com.concertstage.block.ring.RingTrussBlockEntity)) {
                    l.setBlock(p, StageContent.CURVED_QUADTRUSS.defaultBlockState().setValue(com.concertstage.block.ring.CurvedTrussBlock.FACING, Direction.SOUTH), 3);
                    ((com.concertstage.block.ring.RingTrussBlockEntity) l.getBlockEntity(p)).apply(7, 0, 90 << 9); } },
            null, 10);
        add("-v13 C ring gui open", null, () -> com.concertstage.client.ClientHooks.openRing(new BlockPos(VX + 50, vy + 1, VZ - 6)), 20);
        add("v13_C_curve_screen", null, null, 4);
        add("-v13 C close gui", null, () -> Minecraft.getInstance().setScreen(null), 10);
        add("-v13 C dissolve", () -> { var be = proc(); be.outputs[0].transition = com.concertstage.vision.OutputState.DISSOLVE; be.outputs[0].duration = 60;
            be.outputs[0].preview = com.concertstage.vision.OutputState.cam(vCrane); be.take(0, level().getGameTime()); }, null, 30);
        add("v13_C_dissolve_mid", null, null, 1);
        add("-v13 C dissolve end", null, null, 40);
        add("v13_C_crane_feed", null, null, 1);
        add("-v13 C fade", () -> { var be = proc(); be.outputs[0].transition = com.concertstage.vision.OutputState.FADE; be.outputs[0].duration = 40;
            be.cut(0, com.concertstage.vision.OutputState.BARS, level().getGameTime()); }, null, 20);
        add("v13_C_fade_black", null, null, 1);
        add("-v13 C auto", () -> { var o = proc().outputs[0]; o.transition = com.concertstage.vision.OutputState.CUT; o.auto = true; o.random = false; o.interval = 2;
            o.autoCams.clear(); o.autoCams.add(vPtz.asLong()); o.autoCams.add(vCrane.asLong()); o.nextSwitch = level().getGameTime() + 40; proc().changed();
            log("[v13] C auto start program " + o.program); }, null, 2);
        for (int k = 1; k <= 6; k++) {
            final int kk = k;
            add("-v13 C auto t" + k, () -> log("[v13] C auto t=" + kk + " s program " + proc().outputs[0].program + " next in " + (proc().outputs[0].nextSwitch - level().getGameTime()) + " ticks"), null, 20);
        }
        add("-v13 C auto pause", () -> { var o = proc().outputs[0]; o.autoPaused = true; proc().changed(); log("[v13] C auto paused on " + o.program); }, null, 60);
        add("-v13 C auto paused", () -> { var o = proc().outputs[0]; log("[v13] C after 3 s paused: " + o.program); o.auto = false; o.autoPaused = false; proc().changed(); }, null, 2);
        add("-v13 C media", () -> { try {
                java.nio.file.Path dir = com.concertstage.vision.VisionNet.mediaFolder(server());
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(640, 360, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(255, 40, 120), 640, 360, new java.awt.Color(40, 120, 255))); g.fillRect(0, 0, 640, 360);
                g.setColor(java.awt.Color.WHITE); g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 72)); g.drawString("CSC 1.3.0", 120, 200); g.dispose();
                javax.imageio.ImageIO.write(img, "png", dir.resolve("csc_test.png").toFile());
                program(0, "media:csc_test.png"); } catch (Exception e) { log("[v13] media " + e); } }, null, 60);
        add("v13_C_media_png", null, () -> log("[v13] C media state '" + com.concertstage.client.vision.MediaLibrary.state("csc_test.png") + "'"), 10);
        add("-v13 C video", () -> program(0, "media:missing_clip.mp4"), null, 20);
        add("-v13 C video state", null, () -> log("[v13] C video state '" + com.concertstage.client.vision.MediaLibrary.state("missing_clip.mp4") + "'"), 2);
        add("-v13 C back to ptz", () -> program(0, com.concertstage.vision.OutputState.cam(vPtz)), null, 20);
        // D. Dynamic Truss: curved Quad / Tri, 30 / 45 / 90 / 180 deg, circular truss diameter 20 (tri section)
        add("-v13 D build", () -> { ServerLevel l = level();
            int[][] pieces = {{0, 7, 90}, {1, 7, 45}, {0, 10, 180}, {1, 5, 30}};
            for (int i = 0; i < pieces.length; i++) {
                BlockPos p = new BlockPos(VX + 50 + i * 9, vy + 1, VZ - 6);
                Block b = pieces[i][0] == 1 ? StageContent.CURVED_TRITRUSS : StageContent.CURVED_QUADTRUSS;
                l.setBlock(p, b.defaultBlockState().setValue(com.concertstage.block.ring.CurvedTrussBlock.FACING, Direction.SOUTH), 3);
                ((com.concertstage.block.ring.RingTrussBlockEntity) l.getBlockEntity(p)).apply(pieces[i][1], 0, (pieces[i][0] << 6) | (pieces[i][2] << 9));
            }
            BlockPos r = new BlockPos(VX + 62, vy + 12, VZ + 16);
            l.setBlock(r, StageContent.RING_TRUSS.defaultBlockState(), 3);
            long t0 = System.nanoTime();
            ((com.concertstage.block.ring.RingTrussBlockEntity) l.getBlockEntity(r)).apply(20, 0, 64);
            log(String.format(java.util.Locale.ROOT, "[v13] D ring D20 tri cells built in %.2f ms", (System.nanoTime() - t0) / 1e6)); },
            () -> cam(VX + 62, vy + 9, VZ - 22, 0, 12), 80);
        add("v13_D_trusses", null, () -> log("[v13] D client " + lvStats()), 10);
        add("-v13 D close", null, () -> cam(VX + 52, vy + 3, VZ - 12, 20, 15), 40);
        add("v13_D_curved_close", null, null, 10);
        // B. long range: 300 m (beyond the loaded chunks at 10 chunks), 700 m with ULTRA
        add("-v13 B far", () -> program(0, com.concertstage.vision.OutputState.BARS), () -> cam(VX + 4, vy + 2.5, VZ - 300, 0, 0), 120);
        add("v13_B_far_300", null, () -> log("[v13] B 300 m client " + lvStats() + " | wall chunk loaded " + com.concertstage.client.vision.LedWalls.loaded(Minecraft.getInstance(), new BlockPos(VX, vy, VZ))), 10);
        add("-v13 B ultra", null, () -> { com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.ULTRA);
            cam(VX + 4, vy + 2.5, VZ - 700, 0, 0); }, 120);
        add("v13_B_far_700_ultra", null, () -> log("[v13] B 700 m client " + lvStats() + " | wall chunk loaded " + com.concertstage.client.vision.LedWalls.loaded(Minecraft.getInstance(), new BlockPos(VX, vy, VZ))), 10);
        add("-v13 B nolong", null, () -> com.concertstage.client.vision.VisionSettings.longRange = false, 20);
        add("v13_B_far_700_longrange_off", null, () -> log("[v13] B 700 m ULTRA long range OFF " + lvStats()), 10);
        add("-v13 B low", null, () -> com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.LOW), 40);
        add("v13_B_far_700_low", null, () -> log("[v13] B 700 m LOW client " + lvStats()), 10);
        // perf: near, all cameras on screen, BALANCED / ULTRA
        add("-v13 perf", null, () -> { com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED);
            cam(VX + 20, vy + 6, VZ - 24, 10, 12); }, 100);
        add("v13_perf_balanced", null, () -> log("[v13] perf BALANCED " + lvStats()), 60);
        add("-v13 perf ultra", null, () -> com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.ULTRA), 100);
        add("v13_perf_ultra", null, () -> log("[v13] perf ULTRA " + lvStats()), 60);
        add("-v13 save", () -> { level().getDataStorage().save(); logWalls("saved"); }, () ->
            com.concertstage.client.vision.VisionSettings.apply(com.concertstage.client.vision.VisionSettings.Profile.BALANCED), 4);
    }

    // ------------------------------------------------------------------ helpers
    private static void add(String name, Runnable server, Runnable client, int settle) {
        STEPS.add(new Step(name, server, client, settle));
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    private static ServerLevel level() {
        return server().overworld();
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void run(MinecraftServer s, String... cmds) {
        s.execute(() -> { for (String c : cmds) s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), c); });
    }

    private static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(block));
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) l.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), 2);
    }

    private static BlockPos desk(ServerLevel l, BlockPos p) {
        l.setBlock(p, StageContent.LUMICORE.defaultBlockState(), 3);
        return p;
    }

    private static LumicoreBlockEntity desk(BlockPos p) {
        return (LumicoreBlockEntity) level().getBlockEntity(p);
    }

    private static int fixture(ServerLevel l, BlockPos desk, Block block, BlockPos p, boolean hung, Direction facing) {
        if (hung) l.setBlock(p.above(), net.minecraft.world.level.block.Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
        l.setBlock(p, block.defaultBlockState().setValue(FixtureBlock.FACING, facing).setValue(FixtureBlock.HUNG, hung), 3);
        FixtureBlockEntity fe = (FixtureBlockEntity) l.getBlockEntity(p);
        LumicoreBlockEntity d = (LumicoreBlockEntity) l.getBlockEntity(desk);
        if (!LightServer.patch(l, d, fe)) log("patch failed " + p);
        return fe.fid();
    }

    private static int[] ints(List<Integer> f) {
        return f.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int id(Attr a) {
        return Arrays.asList(Attr.ALL).indexOf(a);
    }

    private static void cmd(LumicoreBlockEntity d, byte[] c) {
        if (!LightServer.apply(level(), d, c)) log("command rejected op " + c[0]);
    }

    private static void set(LumicoreBlockEntity d, int[] f, Attr a, float v) {
        var w = Commands.op(Commands.P_SET);
        Commands.fids(w, f);
        cmd(d, w.varInt(id(a)).unit(v).toBytes());
    }

    private static void rgb(LumicoreBlockEntity d, int[] f, float r, float g, float b) {
        var w = Commands.op(Commands.P_RGB);
        Commands.fids(w, f);
        cmd(d, w.unit(r).unit(g).unit(b).unit(r > 0.99f && g > 0.99f && b > 0.99f ? Float.NaN : 0f).toBytes());
    }

    private static void color(LumicoreBlockEntity d, int[] f, float r, float g, float b) {
        rgb(d, f, r, g, b);
    }

    private static void white(LumicoreBlockEntity d, int[] f, float zoom) {
        set(d, f, Attr.DIMMER, 1f);
        rgb(d, f, 1, 1, 1);
        set(d, f, Attr.ZOOM, zoom);
    }

    private static void aim(LumicoreBlockEntity d, int fid, double x, double y, double z) {
        var w = Commands.op(Commands.P_AIM);
        Commands.fids(w, new int[]{fid});
        cmd(d, w.f32((float) x).f32((float) y).f32((float) z).toBytes());
    }

    private static void clear(LumicoreBlockEntity d) {
        var w = Commands.op(Commands.P_CLEAR);
        Commands.fids(w, new int[0]);
        cmd(d, w.bool(true).toBytes());
    }

    private static void cam(double x, double y, double z, float yaw, float pitch) {
        MinecraftServer s = server();
        s.execute(() -> { ServerPlayer p = player(s); p.teleportTo(s.overworld(), x + 0.5, y, z + 0.5, yaw, pitch); p.getAbilities().flying = true; p.onUpdateAbilities(); });
    }

    private static void logBudgets(String at) {
        try {
            Class<?> perf = Class.forName("com.concertstage.client.light.FixtureRenderer$Perf");
            StringBuilder sb = new StringBuilder(at).append(": ");
            for (String n : new String[]{"BEAMS", "FAR", "VOLUMES", "PROJECTIONS"}) {
                var f = perf.getDeclaredField(n);
                f.setAccessible(true);
                sb.append(f.get(null)).append(" | ");
            }
            var q = perf.getDeclaredField("autoLevel");
            q.setAccessible(true);
            var ms = perf.getDeclaredField("avgMs");
            ms.setAccessible(true);
            sb.append("auto level ").append(q.get(null)).append(" | frame ").append(String.format("%.1f ms", (double) ms.get(null)))
                .append(" | fps ").append(Minecraft.getInstance().getFps());
            sb.append(" | ").append(com.concertstage.client.light.LightBatch.shimmerStats());
            if (at.contains("perf")) {
                // rev. 25: cost of each pass on the card and the processor, the mod's processor time, the Java memory in use
                sb.append(" | ").append(com.concertstage.client.light.GpuTimers.stats());
                for (String l : com.concertstage.client.RenderStats.lines()) if (l.contains("total of the mod") || l.contains("drawn ")) sb.append(" |").append(l);
                Runtime rt = Runtime.getRuntime();
                sb.append(String.format(java.util.Locale.ROOT, " | heap %d MB used / %d MB", (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20));
            }
            log(sb.toString());
        } catch (ReflectiveOperationException e) {
            log("budgets unreadable " + e);
        }
    }

    private static void shot(Minecraft mc, String name) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(out.resolve(name + ".png"));
            logBudgets(name);
        } catch (IOException e) {
            log("screenshot failed " + name + " " + e);
        }
    }

    private static void log(String s) {
        LogUtils.getLogger().info("CSC_VISUAL {}", s);
        if (out == null) return;
        try {
            Files.writeString(out.resolve("report.txt"), s + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
