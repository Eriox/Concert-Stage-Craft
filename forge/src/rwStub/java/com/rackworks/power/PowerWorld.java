package com.rackworks.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Compile-only shape of RackWorks' PowerWorld. */
public final class PowerWorld {
    private PowerWorld() {}
    public static PowerGrid grid(Level level) { return null; }
    public static void track(GearHost host, Level level) {}
    public static void untrack(GearHost host) {}
    public static void removed(Level level, BlockPos pos) {}
    public static void rebindMovedHost(Level level, GearHost host) {}
}