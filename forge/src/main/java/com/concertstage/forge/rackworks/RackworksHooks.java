package com.concertstage.forge.rackworks;

import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.FixtureHooks;
import com.rackworks.power.CableBounds;
import com.rackworks.power.PowerGrid;
import com.rackworks.power.PowerWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 1.2.2 — what a CSC fixture does with RackWorks on Forge: its cable host joins the power network when it loads, its cables are
 * saved with it and given back when it is broken. Every method is a no-op when the fixture has no host (a build without RackWorks
 * never installs these hooks at all). The drawing of the cables is {@link RackworksClient}, client only.
 */
final class RackworksHooks implements FixtureHooks {

    @Override
    public void join(FixtureBlockEntity be) {
        FixtureCableHost host = RackworksCompat.host(be);
        Level level = be.getLevel();
        if (level == null) return;
        if (level.isClientSide) {
            PowerWorld.track(host, level);                         // sockets can be aimed at: the client draws their outline
            return;
        }
        PowerGrid power = PowerWorld.grid(level);
        if (power != null) power.add(host);
    }

    @Override
    public void leave(FixtureBlockEntity be) {
        if (!(be.rackworksHost instanceof FixtureCableHost host)) return;
        Level level = be.getLevel();
        if (level == null) return;
        if (level.isClientSide) {
            PowerWorld.untrack(host);
            return;
        }
        PowerGrid power = PowerWorld.grid(level);
        if (power != null) power.remove(host);
    }

    @Override
    public void removed(FixtureBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) return;
        PowerWorld.removed(level, be.getBlockPos());               // unplugs and gives the cable items back
    }

    @Override
    public void rebind(FixtureBlockEntity be) {
        // a hoist put the fixture down at another height: the far ends of its cables follow it (RackWorks repairs the links)
        Level level = be.getLevel();
        if (level == null || level.isClientSide || !(be.rackworksHost instanceof FixtureCableHost host)) return;
        PowerWorld.rebindMovedHost(level, host);
    }

    @Override
    public void save(FixtureBlockEntity be, CompoundTag tag) {
        if (be.rackworksHost instanceof FixtureCableHost host) host.save(tag);
    }

    @Override
    public void load(FixtureBlockEntity be, CompoundTag tag) {
        RackworksCompat.host(be).load(tag);
    }

    @Override
    public AABB renderBox(FixtureBlockEntity be, AABB box) {
        if (!(be.rackworksHost instanceof FixtureCableHost host)) return box;
        return box.minmax(CableBounds.around(be.getBlockPos(), host.links()));
    }
}
