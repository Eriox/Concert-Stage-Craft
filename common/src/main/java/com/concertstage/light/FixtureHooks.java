package com.concertstage.light;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/**
 * 1.2.2 — the optional hooks of a loader module on a fixture (server side too), so a fixture can be part of another mod's systems
 * without the common code (shared by Forge and Fabric) naming that mod. Today the Forge module uses them for RackWorks: the
 * fixture is a cable host, its mains comes from the power network, and its cables are saved with it. Fabric leaves
 * {@link FixtureBlockEntity#HOOKS} null and nothing changes. The drawing of the cables is a client hook of its own
 * ({@code client.light.FixtureRenderHook}), never loaded on a dedicated server.
 *
 * <p>Every method has a do-nothing default: a loader implements only what it needs. They are called on both sides as noted.
 */
public interface FixtureHooks {

    /** The block entity is in the world (server and client): it can join the loader's networks. */
    default void join(FixtureBlockEntity be) {}

    /** The block entity is going (chunk unload or block removed): drop what was registered for it. */
    default void leave(FixtureBlockEntity be) {}

    /** The block is broken (server only): give back what was plugged into it. */
    default void removed(FixtureBlockEntity be) {}

    /** The fixture's own NBT is read and the loader's data restored (both sides): repair what pointed at the old position. */
    default void rebind(FixtureBlockEntity be) {}

    /** Save the loader's own data in the fixture's NBT (both sides, before the update tag is sent). */
    default void save(FixtureBlockEntity be, CompoundTag tag) {}

    /** Read the loader's own data back (both sides, after the fixture's own fields). */
    default void load(FixtureBlockEntity be, CompoundTag tag) {}

    /** The render box of the fixture, extended by what the loader draws around it (cable runs), client only. */
    default AABB renderBox(FixtureBlockEntity be, AABB box) {
        return box;
    }
}
