package com.concertstage.forge.rackworks;

import com.concertstage.StageContent;
import com.concertstage.light.FixtureBlockEntity;
import com.concertstage.light.core.FixtureProfile;
import com.rackworks.api.AddonHosts;
import com.rackworks.gear.Connector;
import com.rackworks.gear.GearConnectors;

import java.util.List;

/**
 * 1.2.2 - Concert Stage Craft's fixtures on RackWorks' cable network (Forge only, optional).
 *
 * <p>Every CSC fixture is a cable host of RackWorks: a powerCON inlet (the mains, from a distro, a generator or a strip) on its
 * base. RackWorks' cable item, sockets, power solver and cable renderer work on it as on RackWorks' own gear; the fixture is dark
 * while it has no supply, and a halogen lamp cools out through the lamp model.
 *
 * <p>RackWorks is not packaged or required: the common code only knows {@link com.concertstage.light.FixtureHooks}, and
 * {@link RackworksEntry} loads this class only when RackWorks 4.6.0 or later is installed.
 */
public final class RackworksCompat {

    private RackworksCompat() {}

    static void init() {
        FixtureBlockEntity.HOOKS = new RackworksHooks();
        for (FixtureProfile profile : FixtureProfile.all())
            GearConnectors.register(gearId(profile), connectors(profile));
        AddonHosts.register(StageContent.FIXTURE_ENTITY, be -> be instanceof FixtureBlockEntity fixture ? host(fixture) : null);
        com.mojang.logging.LogUtils.getLogger().info("Concert Stage: {} fixtures are cable hosts on RackWorks' power network",
            FixtureProfile.all().size());
    }

    /** The cable host of a fixture, made once for its life. */
    static FixtureCableHost host(FixtureBlockEntity be) {
        if (be.rackworksHost instanceof FixtureCableHost host) return host;
        FixtureCableHost host = new FixtureCableHost(be);
        be.rackworksHost = host;
        return host;
    }

    static String gearId(FixtureProfile profile) {
        return "concertstage:" + profile.id;
    }

    /**
     * The power socket of a fixture, on the back of its base: a powerCON inlet. Coordinates are the model's own (metres, front to
     * the north); the profile's render scale turns them into the block units RackWorks places. (DMX in / out come with the wired
     * DMX step: a socket that no network uses would only confuse the cable tool.)
     */
    static List<Connector> connectors(FixtureProfile profile) {
        double s = profile.renderScale;
        return List.of(new Connector("powercon0", "powercon", 0.0, 0.055 * s, 0.12 * s,
            0, 0, 1, 1, 0, 0, 0, 1, 0, 0.09, 0.09, 0.03, 16, "blue", true, false, 0,
            "block.concertstage.connector.mains", ""));
    }
}
