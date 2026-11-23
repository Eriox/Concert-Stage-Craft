package com.rackworks.power;

import java.util.List;

/** Compile-only shape of RackWorks' PowerNode. */
public interface PowerNode extends GearNode {
    List<Port> ports();
    PortLinks links();
    void linksChanged();
    void describe(PowerModel.Node node);
    void apply(PowerModel.Result result);
    default int portOf(String connectorId) { return -1; }
}