package com.rackworks.power;

import java.util.List;

/** Compile-only shape of RackWorks' Port. */
public record Port(String name, int arg, boolean inlet, PortType type, int amps, int phase, int way, int channel, List<String> at) {}