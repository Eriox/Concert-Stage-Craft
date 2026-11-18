package com.concertstage.light.dmx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Received DMX of every universe, merged from every source. Rules (explicit, deterministic):
 * <ol>
 * <li>A source is one sender of one protocol (Art-Net: its IP address, sACN: its CID) on one universe.</li>
 * <li>The highest priority wins (sACN priority 0–200; Art-Net has none and counts as {@link #artnetPriority}, 100 by default like the
 * sACN default).</li>
 * <li>Sources with the same, highest priority are merged channel by channel, highest value (HTP), as Art-Net nodes do.</li>
 * <li>A source silent for {@link #TIMEOUT_MS} (or that sent sACN "stream terminated") is dropped. When the last source of a universe
 * goes, the universe is marked lost; its last values are kept (hold), as real fixtures do.</li>
 * </ol>
 * CSC universes start at 0 (rev. 16h): sACN universe N = CSC universe N (sACN has no universe 0); Art-Net Port-Address P = the CSC
 * universe {@link #artnetMap} gives (the Art-Net settings: by default the same number, 0 → 0, no automatic +1), or not received.
 *
 * <p>Thread safety: the network thread calls {@link #receive}, the game thread {@link #snapshot}; both synchronise on this object, for
 * a few microseconds.
 */
public final class DmxMerge {
    public static final long TIMEOUT_MS = 3000;
    public int artnetPriority = 100;
    /** Rev. 16h: Port-Address → CSC universe, {@link ArtNetSettings#NONE} when that Port-Address is not received. */
    public volatile java.util.function.IntUnaryOperator artnetMap = pa -> pa;
    /** {@link #receive} result: a valid packet for a Port-Address that is not received (not an error). */
    public static final int NOT_RECEIVED = -2;

    private static final class Source {
        final String id, name;
        final int protocol;
        final byte[] data = new byte[512];
        int priority, lastSeq = -1;
        long lastMs;
        long packets, outOfOrder;

        Source(String id, String name, int protocol) {
            this.id = id;
            this.name = name;
            this.protocol = protocol;
        }
    }

    private static final class Universe {
        final Map<String, Source> sources = new HashMap<>();
        final byte[] merged = new byte[512];
        long version, lastMs;
        boolean lost = true;
        long packets;
    }

    private final Map<Integer, Universe> universes = new TreeMap<>();
    private long packets, rejected, ignored;

    /**
     * A frame from the network ({@code nowMs}: monotonic clock). Returns the CSC universe it went to (0 is a universe), -1 when
     * rejected, {@link #NOT_RECEIVED} for an Art-Net Port-Address that is not selected.
     */
    public synchronized int receive(DmxPackets.Frame f, long nowMs) {
        int u;
        if (f.protocol() == DmxPackets.PROTO_ARTNET) {
            u = artnetMap.applyAsInt(f.universe());
            if (u == ArtNetSettings.NONE) { ignored++; return NOT_RECEIVED; }
        } else u = f.universe();
        if (u < 0 || u > ArtNetSettings.MAX_UNIVERSE) { rejected++; return -1; }
        packets++;
        Universe un = universes.computeIfAbsent(u, k -> new Universe());
        if (f.terminated()) {
            un.sources.remove(f.sourceId());
            remerge(un, nowMs);
            return u;
        }
        Source s = un.sources.computeIfAbsent(f.sourceId(), k -> new Source(f.sourceId(), f.sourceName(), f.protocol()));
        // sequence: E1.31 6.7.2 — a packet up to 20 behind the last one is out of order and dropped (0 = sequence not used by Art-Net)
        if (s.lastSeq >= 0 && f.sequence() != 0) {
            int diff = (byte) (f.sequence() - s.lastSeq);
            if (diff <= 0 && diff > -20) { s.outOfOrder++; return u; }
        }
        s.lastSeq = f.sequence();
        s.priority = f.priority() < 0 ? artnetPriority : f.priority();
        s.lastMs = nowMs;
        s.packets++;
        System.arraycopy(f.data(), 0, s.data, 0, 512);
        un.packets++;
        remerge(un, nowMs);
        return u;
    }

    public synchronized void rejectedPacket() {
        rejected++;
    }

    private static void remerge(Universe un, long nowMs) {
        int best = -1;
        for (Source s : un.sources.values()) best = Math.max(best, s.priority);
        if (best < 0) { un.lost = true; un.version++; return; }
        byte[] m = new byte[512];
        for (Source s : un.sources.values()) {
            if (s.priority != best) continue;
            for (int i = 0; i < 512; i++) if ((s.data[i] & 255) > (m[i] & 255)) m[i] = s.data[i];
        }
        System.arraycopy(m, 0, un.merged, 0, 512);
        un.lost = false;
        un.lastMs = nowMs;
        un.version++;
    }

    /** Drops the sources that went silent. Call regularly (every tick). */
    public synchronized void expire(long nowMs) {
        for (Universe un : universes.values()) {
            boolean changed = false;
            for (Iterator<Source> it = un.sources.values().iterator(); it.hasNext(); ) {
                if (nowMs - it.next().lastMs > TIMEOUT_MS) { it.remove(); changed = true; }
            }
            if (changed) remerge(un, nowMs);
        }
    }

    /** Copy of the merged data of a universe into {@code out} (512); returns its version (changes with every update), -1 if never seen. */
    public synchronized long snapshot(int universe, byte[] out) {
        Universe un = universes.get(universe);
        if (un == null) return -1;
        System.arraycopy(un.merged, 0, out, 0, 512);
        return un.version;
    }

    public synchronized boolean lost(int universe) {
        Universe un = universes.get(universe);
        return un == null || un.lost;
    }

    public synchronized void clear() {
        universes.clear();
        packets = rejected = ignored = 0;
    }

    /** One line per universe for the signal monitor. */
    public record Status(int universe, boolean lost, int sources, String names, long packets, int winningPriority, long ageMs) {}

    public synchronized List<Status> status(long nowMs) {
        List<Status> out = new ArrayList<>();
        for (Map.Entry<Integer, Universe> e : universes.entrySet()) {
            Universe un = e.getValue();
            StringBuilder names = new StringBuilder();
            int best = -1;
            for (Source s : un.sources.values()) {
                if (names.length() > 0) names.append(", ");
                names.append(s.name).append(" (").append(s.priority).append(')');
                best = Math.max(best, s.priority);
            }
            out.add(new Status(e.getKey(), un.lost, un.sources.size(), names.toString(), un.packets, best, un.lastMs == 0 ? -1 : nowMs - un.lastMs));
        }
        return out;
    }

    public synchronized long packets() {
        return packets;
    }

    public synchronized long rejected() {
        return rejected;
    }

    /** Valid Art-Net packets of Port-Addresses that are not received. */
    public synchronized long ignored() {
        return ignored;
    }

    /** Forgets the universes (after a change of the Art-Net settings: a universe may now be fed by another Port-Address). */
    public synchronized void forget(int universe) {
        universes.remove(universe);
    }
}
