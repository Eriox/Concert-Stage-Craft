package com.concertstage.client.audio;

import com.concertstage.StageContent;
import com.concertstage.audio.AudioConsoleBlockEntity;
import com.concertstage.audio.SpeakerBlockEntity;
import com.concertstage.audio.net.AudioNet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Client audio snapshots, refreshed through a nearby array even outside console render distance. */
final class RemoteConsoles {
    private record Snapshot(AudioConsoleBlockEntity console, long tick) {}
    private static final Map<BlockPos, Snapshot> CACHE = new HashMap<>();
    static void reset() { CACHE.clear(); }
    static Set<BlockPos> positions() { return CACHE.keySet(); }
    static AudioConsoleBlockEntity get(BlockPos p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (level.getBlockEntity(p) instanceof AudioConsoleBlockEntity c) return c;
        Snapshot s = CACHE.get(p);
        return s == null ? null : s.console;
    }
    static void receive(BlockPos pos, CompoundTag tag) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (tag == null) { CACHE.remove(pos); return; }
        AudioConsoleBlockEntity c = new AudioConsoleBlockEntity(pos, StageContent.AUDIO_CONSOLE.defaultBlockState());
        c.loadAudioSnapshot(level, tag);
        CACHE.put(pos, new Snapshot(c, level.getGameTime()));
    }
    static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long tick = level.getGameTime();
        for (BlockPos p : AudioConsoleBlockEntity.clientConsoles())
            if (level.getBlockEntity(p) instanceof AudioConsoleBlockEntity c) CACHE.put(p, new Snapshot(c, tick));
        CACHE.entrySet().removeIf(e -> tick - e.getValue().tick > 100);
        if (tick % 20 != 0) return;
        Set<BlockPos> sent = new HashSet<>();
        int count = 0;
        for (BlockPos p : SpeakerBlockEntity.clientArrays()) {
            if (!(level.getBlockEntity(p) instanceof SpeakerBlockEntity s) || s.console() == null || !sent.add(s.console())) continue;
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.distanceToSqr(s.acousticAxis()[0]) > 512 * 512) continue;
            if (++count > 8) break;
            var packet = AudioNet.message(AudioNet.C_STATE); packet.writeBlockPos(p); AudioNet.toServer(packet);
        }
    }
}
