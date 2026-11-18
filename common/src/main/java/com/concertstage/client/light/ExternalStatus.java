package com.concertstage.client.light;

import com.concertstage.light.core.Bytes;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** External Show Control status of the desks near the player, as the server last sent it (receiver, sources, recorder, player, library). */
public final class ExternalStatus {
    public String receiver = "off", midi = "off", lastError = "", lastExport = "";
    public long packets, rejected;
    public record Universe(int universe, boolean lost, int sources, String names, long packets, int priority, long ageMs) {}
    public final List<Universe> universes = new ArrayList<>();
    public boolean recording;
    public String recordName = "";
    public long recordMs, recordBytes;
    public int recordFrames;
    public boolean loaded, playing, loop, syncMusic, syncMtc, osc, testing;
    public String playerName = "", trackName = "";
    public long positionMs, durationMs;
    public float speed = 1f;
    public final List<String[]> library = new ArrayList<>();
    public long receivedAt;
    // rev. 16h: Art-Net network settings and monitors (see ExternalControl.writeNet)
    public final com.concertstage.light.dmx.ArtNetSettings art = new com.concertstage.light.dmx.ArtNetSettings();
    public boolean hasArt;
    public int artState;
    public String bindError = "", listening = "", lastSource = "", lastFiltered = "", lastInvalid = "", netReport = "";
    public long artPackets, artAccepted, artInvalid, artFiltered, artNotSelected, artRate, lastAcceptedAgo = -1;
    public boolean receiverAllowed, dedicated;
    public record Port(int portAddress, long packets, long ageMs) {}
    public final List<Port> ports = new ArrayList<>();
    public record NetIf(String name, String address, boolean up, boolean loopback) {}
    public final List<NetIf> interfaces = new ArrayList<>();
    public boolean hasInterfaces, hasMonitor, monData, monLost;
    public int monUniverse;
    public long monAgeMs = -1;
    public byte[] monValues = new byte[512];
    /** Values of the previous message (the DMX monitor marks the channels that changed). */
    public byte[] prevValues = new byte[512];
    public int prevUniverse;

    private static final Map<BlockPos, ExternalStatus> BY_DESK = new HashMap<>();

    public static ExternalStatus of(BlockPos pos) {
        return BY_DESK.computeIfAbsent(pos.immutable(), k -> new ExternalStatus());
    }

    static void receive(BlockPos pos, Bytes.Reader r) {
        ExternalStatus s = new ExternalStatus();
        s.receiver = r.str(512); s.midi = r.str(512); s.packets = r.varLong(); s.rejected = r.varLong(); s.lastError = r.str(256);
        int n = r.count(16);
        for (int i = 0; i < n && r.ok(); i++) {
            s.universes.add(new Universe(r.varInt(), r.bool(), r.varInt(), r.str(2048), r.varLong(), r.sInt(), r.sLong()));
        }
        s.recording = r.bool();
        if (s.recording) { s.recordName = r.str(256); s.recordMs = r.varLong(); s.recordFrames = r.varInt(); s.recordBytes = r.varLong(); }
        s.loaded = r.bool();
        if (s.loaded) {
            s.playerName = r.str(256); s.positionMs = r.varLong(); s.durationMs = r.varLong(); s.playing = r.bool(); s.loop = r.bool();
            s.speed = r.f32(); s.syncMusic = r.bool(); s.trackName = r.str(1024); s.syncMtc = r.bool();
        }
        s.osc = r.bool(); s.testing = r.bool(); s.lastExport = r.str(1024);
        int k = r.count(64);
        for (int i = 0; i < k && r.ok(); i++) s.library.add(new String[] {r.str(256), r.str(64)});
        if (r.ok() && r.more() && s.art.read(r)) {
            s.hasArt = true;
            s.artState = r.varInt(); s.bindError = r.str(512); s.listening = r.str(128);
            s.artPackets = r.varLong(); s.artAccepted = r.varLong(); s.artInvalid = r.varLong(); s.artFiltered = r.varLong(); s.artNotSelected = r.varLong();
            s.artRate = r.varLong(); s.lastSource = r.str(64); s.lastFiltered = r.str(64); s.lastInvalid = r.str(512);
            s.lastAcceptedAgo = r.sLong(); s.receiverAllowed = r.bool(); s.dedicated = r.bool(); s.netReport = r.str(1024);
            int np = r.count(64);
            for (int i = 0; i < np && r.ok(); i++) s.ports.add(new Port(r.varInt(), r.varLong(), r.sLong()));
            s.hasInterfaces = r.bool();
            if (s.hasInterfaces) {
                int ni = r.count(16);
                for (int i = 0; i < ni && r.ok(); i++) s.interfaces.add(new NetIf(r.str(256), r.str(64), r.bool(), r.bool()));
            }
            s.hasMonitor = r.bool();
            if (s.hasMonitor) {
                s.monUniverse = r.varInt(); s.monData = r.bool(); s.monLost = r.bool(); s.monAgeMs = r.sLong();
                byte[] b = r.bytes(512);
                if (b.length == 512) s.monValues = b;
            }
        }
        if (!r.ok()) return;
        // keep the last interface list and DMX values between two status messages that do not carry them
        ExternalStatus prev = BY_DESK.get(pos);
        if (prev != null) {
            if (!s.hasInterfaces) { s.interfaces.addAll(prev.interfaces); }
            if (!s.hasMonitor) { s.monValues = prev.monValues; s.monUniverse = prev.monUniverse; s.monData = prev.monData; s.monLost = prev.monLost; s.monAgeMs = prev.monAgeMs; }
            s.prevValues = prev.monValues;
            s.prevUniverse = prev.monUniverse;
        }
        s.receivedAt = System.currentTimeMillis();
        BY_DESK.put(pos.immutable(), s);
    }

    public static void clear() {
        BY_DESK.clear();
    }
}
