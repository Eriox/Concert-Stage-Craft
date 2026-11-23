package com.rackworks.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Compile-only shape of RackWorks' CableBounds. */
public final class CableBounds {
    private CableBounds() {}
    public static AABB around(BlockPos pos, PortLinks... all) { return new AABB(pos); }
}