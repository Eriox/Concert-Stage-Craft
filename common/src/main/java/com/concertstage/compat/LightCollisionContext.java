package com.concertstage.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.2.0 rev. 16d: the collision context of a CSC light query. Its name ends with ".LightCollisionContext", the name
 * {@link LightCompat#isLightRay} recognises (the convention of the light ray traces): the blocks that describe what stops light
 * (tarps, curtains, ring truss cells, rails, towers...) answer it with that shape — used by the beam shadow maps for the structures
 * the mod draws with a renderer instead of a block model.
 */
public final class LightCollisionContext implements CollisionContext {
    public static final LightCollisionContext INSTANCE = new LightCollisionContext();

    private LightCollisionContext() {}

    @Override public boolean isDescending() { return false; }
    @Override public boolean isAbove(VoxelShape shape, BlockPos pos, boolean canAscend) { return false; }
    @Override public boolean isHoldingItem(Item item) { return false; }
    @Override public boolean canStandOnFluid(FluidState above, FluidState fluid) { return false; }
}
