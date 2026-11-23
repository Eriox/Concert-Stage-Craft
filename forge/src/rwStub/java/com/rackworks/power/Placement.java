package com.rackworks.power;

import com.rackworks.rigging.Affine;
import net.minecraft.core.Direction;

/** Compile-only shape of RackWorks' Placement. */
public record Placement(int x, int y, int z, Direction facing, double ox, double oy, double oz, Affine pose) {
    public Placement(int x, int y, int z, Direction facing, double ox, double oy, double oz) {
        this(x, y, z, facing, ox, oy, oz, null);
    }
}