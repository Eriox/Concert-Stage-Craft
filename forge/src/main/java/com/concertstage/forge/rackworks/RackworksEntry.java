package com.concertstage.forge.rackworks;

import net.minecraftforge.fml.ModList;

/**
 * 1.2.2 — the entry point of the optional RackWorks integration on Forge. It is the only class of the integration that the mod
 * constructor touches: it checks that RackWorks (4.6.0 or later, with the cable-host API) is there before the classes that name
 * RackWorks are loaded, so a game without RackWorks never resolves them.
 */
public final class RackworksEntry {

    private RackworksEntry() {}

    /** Called from the mod constructor: wires the fixtures as RackWorks cable hosts when RackWorks is installed. */
    public static void tryInit() {
        if (!ModList.get().isLoaded("rackworks")) return;
        try {
            // 4.6.0 API: on an older RackWorks the fixtures simply stay out of its network
            Class.forName("com.rackworks.api.AddonHosts");
            RackworksCompat.init();
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT)
                RackworksClient.init();
        } catch (Throwable missing) {
            com.mojang.logging.LogUtils.getLogger().info("Concert Stage: RackWorks found, but without its add-on cable host API (4.6.0): cables stay RackWorks-only");
        }
    }
}
