package com.rackworks.power;

import net.minecraft.nbt.CompoundTag;
import java.util.Map;

/** Compile-only shape of RackWorks' PortLinks. */
public final class PortLinks {
    public PortLinks() {}
    public End get(int port) { return null; }
    public void put(int port, End end) {}
    public Map<Integer, End> all() { return Map.of(); }
    public boolean isEmpty() { return true; }
    public long origin() { return 0L; }
    public void save(CompoundTag tag, String name, long origin) {}
    public void load(CompoundTag tag, String name, int portCount, long origin) {}
    public static CompoundTag updateTag(PortLinks power, PortLinks signal, long origin) { return new CompoundTag(); }
    public static final class End {}
}