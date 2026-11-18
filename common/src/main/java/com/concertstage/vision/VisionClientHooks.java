package com.concertstage.vision;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

/** Client screens opened by the LiveVision blocks; set by the client at start-up (nothing happens on a dedicated server). */
public final class VisionClientHooks {
    public static BiConsumer<BlockPos, Long> wall = (p, id) -> { };
    public static Consumer<BlockPos> regie = p -> { };
    public static Consumer<BlockPos> camera = p -> { };

    private VisionClientHooks() {}

    public static void openWall(BlockPos pos, long id) { wall.accept(pos, id); }
    public static void openRegie(BlockPos pos) { regie.accept(pos); }
    public static void openCamera(BlockPos pos) { camera.accept(pos); }
}
