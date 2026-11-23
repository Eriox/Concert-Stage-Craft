package com.rackworks.api;

import com.rackworks.power.GearHost;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Compile-only shape of RackWorks' AddonHosts (4.6.0). */
public final class AddonHosts {
    private AddonHosts() {}
    @FunctionalInterface
    public interface Provider { GearHost host(BlockEntity be); }
    public static void register(BlockEntityType<?> type, Provider provider) {}
    public static GearHost host(BlockEntity be) { return null; }
}