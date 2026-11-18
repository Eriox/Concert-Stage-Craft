package com.concertstage.audio.net;

import com.concertstage.audio.SpeakerBlockEntity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Short lived, ticking console tickets while a listener is near a linked, loaded array.
 * Tickets expire after five seconds; no saved force-loaded chunks or global world scan. */
public final class AudioContinuity {
    private static final Set<SpeakerBlockEntity> ARRAYS = new HashSet<>();
    private static final TicketType<BlockPos> TICKET = TicketType.create("csc_audio_console",
        Comparator.comparingLong(BlockPos::asLong), 100);
    private AudioContinuity() {}
    public static void add(SpeakerBlockEntity s) { ARRAYS.add(s); }
    public static void remove(SpeakerBlockEntity s) { ARRAYS.remove(s); }
    public static void clear() { ARRAYS.clear(); }
    public static boolean canHear(ServerPlayer p, BlockPos console) {
        for (SpeakerBlockEntity s : ARRAYS) if (!s.isRemoved() && s.getLevel() == p.serverLevel()
            && console.equals(s.console()) && p.distanceToSqr(s.acousticAxis()[0]) <= 512 * 512) return true;
        return false;
    }
    public static void keep(ServerLevel level, BlockPos console) {
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(console), 1, console.immutable());
    }
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) return;
        ARRAYS.removeIf(s -> s.isRemoved() || s.getLevel() == null || s.getLevel().getServer() != server);
        Set<String> refreshed = new HashSet<>();
        java.util.Map<java.util.UUID,Integer> budget=new java.util.HashMap<>();
        for (SpeakerBlockEntity s : ARRAYS) {
            if (s.console() == null || s.cabinets().isEmpty() || !(s.getLevel() instanceof ServerLevel level)) continue;
            String key = level.dimension().location() + ":" + s.console().asLong();
            if (refreshed.contains(key)) continue;
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(s.acousticAxis()[0]) <= 512 * 512 && budget.getOrDefault(p.getUUID(),0)<8) {
                keep(level, s.console()); budget.merge(p.getUUID(),1,Integer::sum); refreshed.add(key); break;
            }
        }
    }
}
