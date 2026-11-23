package com.rackworks.power;

import java.util.ArrayList;
import java.util.List;

/** Compile-only shape of RackWorks' GearHost (the power half our fixtures use). */
public interface GearHost {
    default List<PowerNode> powerNodes() { return List.of(); }
    default PowerNode powerNode(int sub) { return null; }
    default List<GearNode> gearNodes() { return new ArrayList<>(); }
    default boolean hasCables() { return false; }
}
