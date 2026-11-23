package com.rackworks.gear;

/** Compile-only shape of RackWorks' Connector (model units, front to the north). */
public record Connector(String id, String kind, double x, double y, double z, double nx, double ny, double nz,
                        double ux, double uy, double uz, double vx, double vy, double vz, double w, double h, double depth,
                        int amps, String colour, boolean inlet, boolean male, int pins, String label, String phase) {}