package com.rackworks.power;

import com.rackworks.gear.Connector;
import com.rackworks.gear.GearConnectors;
import java.util.List;

/** Compile-only shape of RackWorks' GearNode. */
public interface GearNode {
    NodeKey key();
    String nodeName();
    String gearId();
    Placement placement();
    default List<Connector> connectors() { return GearConnectors.of(gearId()); }
    default Connector connector(String id) { return null; }
}