package com.concertstage.stage;

import net.minecraft.network.chat.Component;

public enum StageSize {
    // width and depth are multiples of 6 (3x3 scaffold modules, beams of 2 blocks); height is odd (corner + 2-block columns), measured from the ground; the deck is at y = 1
    SMALL(12, 6, 9, "small"),
    MEDIUM(18, 12, 11, "medium"),
    LARGE(24, 18, 13, "large");

    public final int width;
    public final int depth;
    public final int height;
    private final String key;

    StageSize(int width, int depth, int height, String key) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("stage_size.concertstage." + key);
    }

    public StageSize next() {
        StageSize[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static StageSize byIndex(int i) {
        StageSize[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
