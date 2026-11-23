package com.rackworks.gear;

import java.util.List;

/** Compile-only shape of RackWorks' GearConnectors. */
public final class GearConnectors {
    private GearConnectors() {}
    public static void register(String id, List<Connector> connectors) {}
    public static List<Connector> of(String gearId) { return List.of(); }
}