package com.concertstage.light;

import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Commands;
import com.concertstage.light.core.Engine;
import com.concertstage.light.core.Live;
import com.concertstage.light.core.Show;
import com.concertstage.light.dmx.AutoPatch;
import com.concertstage.light.dmx.CscShowFile;
import com.concertstage.light.dmx.DmxData;
import com.concertstage.light.dmx.DmxMerge;
import com.concertstage.light.dmx.DmxMode;
import com.concertstage.light.dmx.DmxPackets;
import com.concertstage.light.dmx.ShowPlayer;
import com.concertstage.light.dmx.ShowRecorder;
import com.concertstage.light.dmx.Timecode;
import com.concertstage.light.dmx.VirtualMatrix;
import com.concertstage.light.net.LightNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CSC External Show Control, server side: the Art-Net / sACN receiver, the recordings, the show player, OSC and MIDI timecode, and the
 * DMX sent to the clients.
 *
 * <ul>
 * <li><b>Receiver</b>: one thread per protocol, started the first time a desk needs it (External mode, recording, signal monitor,
 * test signal) and stopped with the server. It never touches the game: it merges packets into {@link DmxMerge} and feeds the
 * recorders. The server thread reads the merge once per tick.</li>
 * <li><b>DMX to the clients</b>: every tick, for each desk driven by DMX, the universes it uses are compared with what was last sent;
 * only the changed slots go (runs), a full copy every 5 s and to a player who opens the desk. So the clients compute exactly the light
 * the server does, at 20 updates per second (head movements stay smooth: the heads follow their target at motor speed).</li>
 * <li><b>Files</b>: {@code <game or server folder>/concertstage/recordings} (recorded shows, .cscrec), {@code .../export}, {@code .../import}.</li>
 * </ul>
 * Settings: {@code config/concertstage-external.properties} (created with the defaults on first use). On a dedicated server the receiver
 * is off unless {@code allow_on_dedicated_server=true}.
 */
public final class ExternalControl {
    private ExternalControl() {}

    // ------------------------------------------------------------ settings
    static final class Config {
        boolean sacn = true, osc = false;
        String bind = "0.0.0.0", midi = "";
        int artnetPriority = 100, oscPort = 7700;
        long mtcOffsetMs = 0;
        /** Rev. 16h: CSC ART-NET NETWORK SETTINGS (edited in the console: Settings → Network → Art-Net). */
        com.concertstage.light.dmx.ArtNetSettings art = new com.concertstage.light.dmx.ArtNetSettings();

        boolean dedicated() {
            return art.allowDedicated;
        }
    }

    static Config config = new Config();
    static Path base, configFile;
    static MinecraftServer server;
    static final DmxMerge MERGE = new DmxMerge();
    private static final List<Thread> THREADS = new ArrayList<>();
    private static final List<DatagramSocket> SOCKETS = new CopyOnWriteArrayList<>();
    private static volatile boolean running;
    private static volatile String receiverStatus = "off";
    private static final Set<Integer> SACN_WANTED = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> SACN_JOINED = ConcurrentHashMap.newKeySet();
    private static volatile MulticastSocket sacnSocket;
    static final CopyOnWriteArrayList<ShowRecorder> RECORDERS = new CopyOnWriteArrayList<>();
    static final Timecode.Mtc MTC = new Timecode.Mtc();
    private static volatile String midiStatus = "off";
    private static Object midiDevice;
    private static volatile long testUntil;

    /** Per desk (server). */
    static final class DeskState {
        ShowRecorder recorder;
        String recordName = "";
        ShowPlayer player;
        String playerName = "";
        boolean syncMusic = true, syncMtc, osc;
        final Map<Integer, Long> versions = new HashMap<>();
        final Map<Integer, byte[]> sent = new HashMap<>();
        long lastFullTick;
        String lastExport = "";
        long lastStatusTick;
        // rev. 16h: signal loss handling, network settings page and DMX monitor of the console
        boolean blackedOut, returnedOnLoss;
        String lastNetReport = "";
        long netUntilTick = -1, monUntilTick = -1;
        int monUniverse = 0;

        /** Whether DMX was ever received on one of these universes (a desk switched to External before any signal is not "lost"). */
        boolean everReceived(Set<Integer> us) {
            for (int u : us) if (versions.containsKey(u)) return true;
            return blackedOut;
        }
    }

    static final Map<String, DeskState> STATES = new ConcurrentHashMap<>();

    static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    static DeskState state(ServerLevel level, LumicoreBlockEntity desk) {
        return STATES.computeIfAbsent(key(level, desk.getBlockPos()), k -> new DeskState());
    }

    static long clock() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Server started: folders and settings. */
    public static void started(MinecraftServer s) {
        server = s;
        base = s.getServerDirectory().toPath().resolve("concertstage");
        configFile = s.getServerDirectory().toPath().resolve("config").resolve("concertstage-external.properties");
        config = loadConfig(configFile);
        useArtSettings(config.art);
        MERGE.artnetPriority = config.artnetPriority;
    }

    public static void stopped() {
        stopReceiver();
        for (ShowRecorder r : RECORDERS) r.finish(clock());
        RECORDERS.clear();
        STATES.clear();
        MERGE.clear();
        closeMidi();
        server = null;
    }

    static Config loadConfig(Path f) {
        Config c = new Config();
        Properties p = new Properties();
        try {
            if (Files.isRegularFile(f)) try (var r = Files.newBufferedReader(f)) { p.load(r); }
            c.art = com.concertstage.light.dmx.ArtNetSettings.load(p);
            c.sacn = !"false".equalsIgnoreCase(p.getProperty("sacn", "true").trim());
            c.bind = p.getProperty("bind_address", "0.0.0.0").trim();
            if (p.containsKey("artnet_universe_offset") && !p.containsKey("artnet_universes"))
                com.mojang.logging.LogUtils.getLogger().info("Concert Stage: the old artnet_universe_offset setting is no longer used: Art-Net "
                    + "Port-Address N now drives CSC universe N (universe 0 included). A different correspondence is set in the console: "
                    + "Settings > Network > Art-Net > Universe mapping");
            c.artnetPriority = Math.max(0, Math.min(200, Integer.parseInt(p.getProperty("artnet_priority", "100").trim())));
            c.osc = "true".equalsIgnoreCase(p.getProperty("osc", "false").trim());
            c.oscPort = Math.max(1024, Math.min(65535, Integer.parseInt(p.getProperty("osc_port", "7700").trim())));
            c.midi = p.getProperty("midi_timecode_device", "").trim();
            c.mtcOffsetMs = Long.parseLong(p.getProperty("midi_timecode_offset_ms", "0").trim());
            if (!Files.isRegularFile(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, String.join("\n",
                    "# Concert Stage Craft - External Show Control (Art-Net / sACN input, OSC, MIDI timecode)",
                    "# Art-Net: UDP 6454. sACN (E1.31): UDP 5568, multicast 239.255.x.y joined for the universes the desks use.",
                    "# These settings are edited in game: LUMICORE X1 > Settings > Network > Art-Net (nothing to change here by hand).",
                    "artnet=true", "sacn=true",
                    "# address the OSC receiver listens on (0.0.0.0 = every network interface; 127.0.0.1 = this computer only)",
                    "bind_address=0.0.0.0",
                    "# Art-Net: address and UDP port to listen on, sender filter (any or an IPv4 address), Port-Addresses received",
                    "# (0 = Net 0 Sub-Net 0 Universe 0, a normal universe), mapping Port-Address>CSC universe (empty = the same number)",
                    "artnet_bind_address=0.0.0.0", "artnet_port=6454", "artnet_source=any", "artnet_universes=0-15", "artnet_mapping=",
                    "# who drives a desk when Art-Net arrives (desk | artnet) and what a desk in External does when the signal is lost",
                    "# (hold | blackout | return)",
                    "artnet_priority_policy=desk", "artnet_signal_loss=hold",
                    "# priority given to Art-Net sources when sACN sources send the same universe (sACN default: 100)",
                    "artnet_priority=100",
                    "# OSC commands (/csc/play, /csc/pause, /csc/stop, /csc/restart, /csc/seek <s>, /csc/speed <x>, /csc/loop <0|1>,",
                    "# /csc/load <name>, /csc/mode <internal|autoshow|external|playback>, /csc/record/start, /csc/record/stop)",
                    "osc=false", "osc_port=7700",
                    "# MIDI timecode input: part of the name of the MIDI input device (empty = off); offset subtracted from the timecode",
                    "midi_timecode_device=", "midi_timecode_offset_ms=0",
                    "# a dedicated server does not open network ports for DMX unless this is true",
                    "allow_on_dedicated_server=false", ""));
            }
        } catch (IOException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: external control settings could not be read, defaults used", e);
        }
        return c;
    }

    // ------------------------------------------------------------ receiver
    static boolean allowed() {
        return server != null && (!server.isDedicatedServer() || config.dedicated());
    }

    /** Starts the receiver threads if they are not running. */
    static synchronized void ensureReceiver() {
        if (running || !allowed()) {
            if (!allowed()) { receiverStatus = "off (dedicated server: Art-Net not allowed in Settings > Network)"; ART.state = ArtStats.DISABLED; }
            return;
        }
        running = true;
        StringBuilder st = new StringBuilder();
        InetAddress bind;
        try {
            bind = InetAddress.getByName(config.bind);
        } catch (IOException e) {
            receiverStatus = "bad bind_address " + config.bind;
            running = false;
            return;
        }
        if (config.art.enabled) {
            String r = openArtNet(config.art);
            st.append(r);
        } else ART.state = ArtStats.DISABLED;
        if (config.sacn) {
            try {
                MulticastSocket s = new MulticastSocket(null);
                s.setReuseAddress(true);
                s.setSoTimeout(500);
                s.bind(new InetSocketAddress(DmxPackets.SACN_PORT));
                SOCKETS.add(s);
                sacnSocket = s;
                SACN_JOINED.clear();
                thread("CSC sACN receiver", () -> loop(s));
                if (st.length() > 0) st.append(" · ");
                st.append("sACN :").append(DmxPackets.SACN_PORT);
            } catch (IOException e) {
                if (st.length() > 0) st.append(" · ");
                st.append("sACN port ").append(DmxPackets.SACN_PORT).append(" busy (").append(e.getMessage()).append(")");
            }
        }
        if (config.osc) {
            try {
                DatagramSocket s = new DatagramSocket(null);
                s.setReuseAddress(true);
                s.setSoTimeout(500);
                s.bind(new InetSocketAddress(bind, config.oscPort));
                SOCKETS.add(s);
                thread("CSC OSC receiver", () -> oscLoop(s));
                st.append(" · OSC :").append(config.oscPort);
            } catch (IOException e) {
                st.append(" · OSC port ").append(config.oscPort).append(" busy");
            }
        }
        if (!config.midi.isEmpty()) openMidi(config.midi);
        receiverStatus = st.length() == 0 ? "no protocol enabled" : st.toString();
        if (SOCKETS.isEmpty() && artSocket == null) running = false;
    }

    private static void thread(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY + 1);
        THREADS.add(t);
        t.start();
    }

    // ------------------------------------------------------------ Art-Net (rev. 16h: its own socket, reconfigured without a restart)
    private static volatile DatagramSocket artSocket;
    private static Thread artThread;
    static final ArtStats ART = new ArtStats();

    /**
     * Counters of the Art-Net receiver, for the CSC ART-NET MONITOR (all measured here: packets counted as they arrive, the rate over
     * the last second, the senders seen). Written by the receiver thread, read by the server thread (plain volatile / atomic values).
     */
    static final class ArtStats {
        static final int DISABLED = 0, LISTENING = 1, RECEIVING = 2, NO_DATA = 3, BIND_ERROR = 4, INVALID_PACKET = 5, SOURCE_FILTERED = 6;
        static final String[] NAMES = {"DISABLED", "LISTENING", "RECEIVING", "NO DATA", "BIND ERROR", "INVALID PACKET", "SOURCE FILTERED"};
        volatile int state = DISABLED;
        volatile String bindError = "";
        final java.util.concurrent.atomic.AtomicLong packets = new java.util.concurrent.atomic.AtomicLong(), accepted = new java.util.concurrent.atomic.AtomicLong(),
            invalid = new java.util.concurrent.atomic.AtomicLong(), filtered = new java.util.concurrent.atomic.AtomicLong(),
            notSelected = new java.util.concurrent.atomic.AtomicLong(), polls = new java.util.concurrent.atomic.AtomicLong();
        volatile long lastAcceptedMs = -1, lastFilteredMs = -1, lastInvalidMs = -1, lastAnyMs = -1;
        volatile String lastSource = "", lastFilteredSource = "", lastInvalid = "";
        /** Per Port-Address: packets and last arrival (only the Port-Addresses seen; at most 512 kept). */
        final Map<Integer, long[]> ports = new ConcurrentHashMap<>();
        // rate: packets in the current and the last whole second
        private long rateSecond = -1, rateCount, lastRate;
        volatile long testSentNs = -1, testReceivedNs = -1;

        synchronized void count(long nowMs) {
            long sec = nowMs / 1000;
            if (sec != rateSecond) { lastRate = sec == rateSecond + 1 ? rateCount : 0; rateSecond = sec; rateCount = 0; }
            rateCount++;
        }

        synchronized long rate(long nowMs) {
            long sec = nowMs / 1000;
            return sec == rateSecond ? lastRate : sec == rateSecond + 1 ? rateCount : 0;
        }

        void reset() {
            packets.set(0); accepted.set(0); invalid.set(0); filtered.set(0); notSelected.set(0); polls.set(0);
            lastAcceptedMs = lastFilteredMs = lastInvalidMs = lastAnyMs = -1;
            lastSource = lastFilteredSource = lastInvalid = "";
            ports.clear();
        }

        /** State shown in the monitor. */
        int state(long nowMs, boolean open) {
            if (state == DISABLED || state == BIND_ERROR) return state;
            if (!open) return DISABLED;
            if (lastAnyMs < 0) return LISTENING;
            if (lastAcceptedMs >= 0 && nowMs - lastAcceptedMs < DmxMerge.TIMEOUT_MS) return RECEIVING;
            if (lastFilteredMs >= 0 && nowMs - lastFilteredMs < DmxMerge.TIMEOUT_MS) return SOURCE_FILTERED;
            if (lastInvalidMs >= 0 && nowMs - lastInvalidMs < DmxMerge.TIMEOUT_MS) return INVALID_PACKET;
            return NO_DATA;
        }
    }

    /** Uses these Art-Net settings in the merge and the exports (Port-Address -> CSC universe). */
    static void useArtSettings(com.concertstage.light.dmx.ArtNetSettings a) {
        com.concertstage.light.dmx.ArtNetSettings frozen = a.copy();
        com.concertstage.light.dmx.ArtNetSettings.current = frozen;
        MERGE.artnetMap = frozen::cscUniverse;
    }

    /** Opens the Art-Net socket with these settings; returns the status text. Never throws. */
    private static synchronized String openArtNet(com.concertstage.light.dmx.ArtNetSettings a) {
        closeArtNet();
        try {
            InetAddress bind = com.concertstage.light.dmx.ArtNetSettings.address(a.bind);
            DatagramSocket s = new DatagramSocket(null);
            s.setReuseAddress(true);
            s.setBroadcast(true);
            s.setSoTimeout(500);
            s.setReceiveBufferSize(1 << 20);                 // bursts of many universes (one packet per universe, up to 44 Hz each)
            s.bind(new InetSocketAddress(bind, a.port));
            artSocket = s;
            ART.state = ArtStats.LISTENING;
            ART.bindError = "";
            Thread t = new Thread(() -> artLoop(s), "CSC Art-Net receiver");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);
            artThread = t;
            t.start();
            return "Art-Net " + a.bind + ":" + a.port;
        } catch (IOException | RuntimeException e) {
            ART.state = ArtStats.BIND_ERROR;
            ART.bindError = a.bind + ":" + a.port + " - " + e.getMessage();
            return "Art-Net " + a.bind + ":" + a.port + " not opened (" + e.getMessage() + ")";
        }
    }

    private static synchronized void closeArtNet() {
        DatagramSocket s = artSocket;
        artSocket = null;
        if (s != null) s.close();
        Thread t = artThread;
        artThread = null;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (ART.state != ArtStats.BIND_ERROR) ART.state = ArtStats.DISABLED;
    }

    /** The Art-Net receiver: validates, filters by sender, maps the Port-Address, merges. */
    private static void artLoop(DatagramSocket s) {
        byte[] buf = new byte[1500];
        DatagramPacket pk = new DatagramPacket(buf, buf.length);
        byte[] snap = new byte[512];
        while (!s.isClosed() && artSocket == s) {
            try {
                pk.setLength(buf.length);
                s.receive(pk);
                long now = clock();
                ART.packets.incrementAndGet();
                ART.lastAnyMs = now;
                ART.count(now);
                String from = pk.getAddress().getHostAddress();
                com.concertstage.light.dmx.ArtNetSettings a = com.concertstage.light.dmx.ArtNetSettings.current;
                int len = pk.getLength();
                // ArtPoll (also the TEST CONNECTION probe): valid, not DMX
                if (len >= 10 && buf[0] == 'A' && buf[1] == 'r' && buf[2] == 't' && (buf[8] & 255) == 0x00 && (buf[9] & 255) == 0x20) {
                    ART.polls.incrementAndGet();
                    if (ART.testSentNs > 0 && ART.testReceivedNs < 0) ART.testReceivedNs = System.nanoTime();
                    continue;
                }
                if (!a.acceptsSource(from)) {                                   // 1.2.1: + the 2.x / 10.x alias (grandMA)
                    ART.filtered.incrementAndGet();
                    ART.lastFilteredMs = now;
                    ART.lastFilteredSource = from;
                    continue;
                }
                DmxPackets.Frame f = DmxPackets.parse(buf, len, from);
                if (f == null || f.protocol() != DmxPackets.PROTO_ARTNET) {
                    if (!DmxPackets.lastError.isEmpty()) {
                        ART.invalid.incrementAndGet();
                        ART.lastInvalidMs = now;
                        ART.lastInvalid = DmxPackets.lastError + " (" + from + ")";
                        MERGE.rejectedPacket();
                    }
                    continue;
                }
                long[] st = ART.ports.get(f.universe());
                if (st == null && ART.ports.size() < 512) ART.ports.put(f.universe(), st = new long[2]);
                if (st != null) { st[0]++; st[1] = now; }
                int u = MERGE.receive(f, now);
                if (u == DmxMerge.NOT_RECEIVED) { ART.notSelected.incrementAndGet(); continue; }
                if (u < 0) continue;
                ART.accepted.incrementAndGet();
                ART.lastAcceptedMs = now;
                ART.lastSource = from;
                if (!RECORDERS.isEmpty()) {
                    MERGE.snapshot(u, snap);
                    for (ShowRecorder r : RECORDERS) r.capture(u, snap, now);
                }
            } catch (SocketTimeoutException e) {
                // check that the socket is still the one in use
            } catch (IOException e) {
                if (!s.isClosed()) receiverStatus = "Art-Net receive error: " + e.getMessage();
            } catch (RuntimeException e) {
                ART.invalid.incrementAndGet();
                MERGE.rejectedPacket();
            }
        }
    }

    /**
     * APPLY: validates, checks the interface, reopens the Art-Net socket with the new settings and, if that fails, goes back to the
     * settings that worked. Returns the report shown to the player. Server thread.
     */
    static synchronized String applyArt(com.concertstage.light.dmx.ArtNetSettings next, boolean save) {
        String bad = next.validate();
        if (bad != null) return "Not applied: " + bad;
        if (!next.bind.equals("0.0.0.0") && !next.bind.startsWith("127.") && !localAddresses().contains(next.bind))
            return "Not applied: " + next.bind + " is not an address of this computer (choose an interface in the list)";
        com.concertstage.light.dmx.ArtNetSettings old = config.art;
        boolean wasDedicated = config.dedicated();
        config.art = next.copy();
        useArtSettings(config.art);
        // a universe may now be fed by another Port-Address: forget what was received for the universes whose source changed
        Set<Integer> affected = new java.util.HashSet<>(old.mapping.values());
        affected.addAll(next.mapping.values());
        old.selected.stream().forEach(pa -> { int u = old.cscUniverse(pa); if (u >= 0) affected.add(u); });
        next.selected.stream().forEach(pa -> { int u = next.cscUniverse(pa); if (u >= 0) affected.add(u); });
        for (int u : affected) if (old.portAddressOf(u) != next.portAddressOf(u)) MERGE.forget(u);
        String report;
        boolean socketChanged = !old.bind.equals(next.bind) || old.port != next.port || old.enabled != next.enabled || artSocket == null;
        if (!allowed()) {
            closeArtNet();
            ART.state = ArtStats.DISABLED;
            report = "Saved. Art-Net is not allowed on this dedicated server";
        } else if (!next.enabled) {
            closeArtNet();
            ART.state = ArtStats.DISABLED;
            report = "Art-Net reception off";
        } else if (socketChanged) {
            String r = openArtNet(next);
            if (ART.state == ArtStats.BIND_ERROR) {
                String err = ART.bindError;
                config.art = old;
                useArtSettings(old);
                if (old.enabled && (!server.isDedicatedServer() || wasDedicated)) openArtNet(old);
                return "BIND ERROR " + err + " - previous settings restored (" + old.bind + ":" + old.port + ")";
            }
            if (!running) ensureReceiver();
            report = "Applied: " + r;
        } else {
            report = "Applied (universes, mapping, filter, policy)";
        }
        ART.reset();
        if (save) report += saveConfig() ? " - saved" : " - could not be saved";
        return report;
    }

    /** Writes the Art-Net keys into the settings file, keeping the other keys and comments of the file as far as Properties allows. */
    static boolean saveConfig() {
        if (configFile == null) return false;
        try {
            Properties p = new Properties();
            if (Files.isRegularFile(configFile)) try (var r = Files.newBufferedReader(configFile)) { p.load(r); }
            config.art.store(p);
            Files.createDirectories(configFile.getParent());
            Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            try (var w = Files.newBufferedWriter(tmp)) {
                p.store(w, "Concert Stage Craft - External Show Control. Art-Net settings are edited in game: LUMICORE X1 > Settings > Network > Art-Net");
            }
            Files.move(tmp, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Concert Stage: Art-Net settings could not be saved", e);
            return false;
        }
    }

    /** IPv4 addresses of this computer, by interface: name, address, up. */
    record NetIf(String name, String address, boolean up, boolean loopback) {}

    static List<NetIf> interfaces() {
        List<NetIf> out = new ArrayList<>();
        try {
            var ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                boolean up;
                try { up = ni.isUp(); } catch (SocketException e) { up = false; }
                boolean lo;
                try { lo = ni.isLoopback(); } catch (SocketException e) { lo = false; }
                var addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress ad = addrs.nextElement();
                    if (ad instanceof java.net.Inet4Address) out.add(new NetIf(ni.getDisplayName() == null ? ni.getName() : ni.getDisplayName(), ad.getHostAddress(), up, lo));
                }
            }
        } catch (SocketException | RuntimeException ignored) { }
        out.sort((x, y) -> Boolean.compare(x.loopback(), y.loopback()) != 0 ? Boolean.compare(x.loopback(), y.loopback()) : Boolean.compare(y.up(), x.up()));
        return out;
    }

    static Set<String> localAddresses() {
        Set<String> s = new java.util.HashSet<>();
        for (NetIf n : interfaces()) s.add(n.address());
        return s;
    }

    /** TEST CONNECTION: an ArtPoll (no DMX: it changes no fixture) sent to the receiver's own address and port; the receiver must see it. */
    static void testConnection(ServerPlayer p) {
        com.concertstage.light.dmx.ArtNetSettings a = config.art;
        if (artSocket == null) { message(p, "Test: the Art-Net receiver is not open (" + ArtStats.NAMES[ART.state] + (ART.bindError.isEmpty() ? "" : ": " + ART.bindError) + ")"); return; }
        String target = a.bind.equals("0.0.0.0") ? "127.0.0.1" : a.bind;
        ART.testReceivedNs = -1;
        ART.testSentNs = System.nanoTime();
        Thread t = new Thread(() -> {
            String result;
            try (DatagramSocket out = new DatagramSocket()) {
                byte[] poll = new byte[14];
                System.arraycopy("Art-Net".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, poll, 0, 7);
                poll[9] = 0x20; poll[11] = 14;                               // OpPoll, protocol 14
                out.send(new DatagramPacket(poll, poll.length, com.concertstage.light.dmx.ArtNetSettings.address(target), a.port));
                long until = System.nanoTime() + 1_000_000_000L;
                while (ART.testReceivedNs < 0 && System.nanoTime() < until) Thread.sleep(5);
                long rx = ART.testReceivedNs;
                result = rx > 0 ? String.format(java.util.Locale.ROOT, "Test OK: packet sent to %s:%d received in %.1f ms (%s)", target, a.port,
                    (rx - ART.testSentNs) / 1e6, ArtStats.NAMES[ART.state(clock(), artSocket != null)])
                    : "Test FAILED: nothing received on " + a.bind + ":" + a.port + " within 1 s (firewall, another program on the port?)";
            } catch (IOException | InterruptedException e) {
                result = "Test FAILED: " + e.getMessage();
            } finally {
                ART.testSentNs = -1;
            }
            String r = result;
            MinecraftServer srv = server;
            if (srv != null) srv.execute(() -> message(p, r));
        }, "CSC Art-Net test");
        t.setDaemon(true);
        t.start();
    }

    static synchronized void stopReceiver() {
        closeArtNet();
        running = false;
        for (DatagramSocket s : SOCKETS) s.close();
        SOCKETS.clear();
        for (Thread t : THREADS) {
            try { t.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        THREADS.clear();
        sacnSocket = null;
        SACN_JOINED.clear();
        receiverStatus = "off";
    }

    private static void loop(DatagramSocket s) {
        byte[] buf = new byte[1500];
        DatagramPacket pk = new DatagramPacket(buf, buf.length);
        byte[] snap = new byte[512];
        while (running && !s.isClosed()) {
            try {
                s.receive(pk);
                DmxPackets.Frame f = DmxPackets.parse(buf, pk.getLength(), pk.getAddress().getHostAddress());
                if (f == null) {
                    if (!DmxPackets.lastError.isEmpty()) MERGE.rejectedPacket();
                    continue;
                }
                long now = clock();
                int u = MERGE.receive(f, now);
                if (u >= 0 && !RECORDERS.isEmpty()) {          // rev. 16h: universe 0 is a universe
                    MERGE.snapshot(u, snap);
                    for (ShowRecorder r : RECORDERS) r.capture(u, snap, now);
                }
            } catch (SocketTimeoutException e) {
                // check running
            } catch (IOException e) {
                if (running) receiverStatus = "receive error: " + e.getMessage();
            } catch (RuntimeException e) {
                MERGE.rejectedPacket();
            }
        }
    }

    /** Joins the sACN multicast groups of the universes the desks use (every interface that can). */
    static void joinSacn() {
        MulticastSocket s = sacnSocket;
        if (s == null) return;
        for (int u : SACN_WANTED) {
            if (u < 1 || u > 63999) continue;
            if (SACN_JOINED.contains(u)) continue;
            try {
                InetAddress g = InetAddress.getByName(DmxPackets.sacnGroup(u));
                boolean any = false;
                var ifs = NetworkInterface.getNetworkInterfaces();
                while (ifs != null && ifs.hasMoreElements()) {
                    NetworkInterface ni = ifs.nextElement();
                    try {
                        if (!ni.isUp() || !ni.supportsMulticast()) continue;
                        s.joinGroup(new InetSocketAddress(g, DmxPackets.SACN_PORT), ni);
                        any = true;
                    } catch (IOException ignored) { }
                }
                if (any) SACN_JOINED.add(u);
            } catch (IOException ignored) { }
        }
    }

    // ------------------------------------------------------------ OSC and MIDI timecode
    private static void oscLoop(DatagramSocket s) {
        byte[] buf = new byte[4096];
        DatagramPacket pk = new DatagramPacket(buf, buf.length);
        while (running && !s.isClosed()) {
            try {
                s.receive(pk);
                List<Timecode.OscMessage> msgs = Timecode.osc(buf, pk.getLength());
                if (msgs == null || msgs.isEmpty()) continue;
                MinecraftServer srv = server;
                if (srv != null) srv.execute(() -> { for (Timecode.OscMessage m : msgs) osc(m); });
            } catch (SocketTimeoutException e) {
                // check running
            } catch (IOException | RuntimeException e) {
                // a bad datagram: ignored
            }
        }
    }

    /** An OSC command, on the server thread: applies to the desks with OSC accepted. */
    static void osc(Timecode.OscMessage m) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Map.Entry<String, DeskState> e : STATES.entrySet()) {
                DeskState st = e.getValue();
                if (!st.osc || !e.getKey().startsWith(level.dimension().location() + "@")) continue;
                BlockPos pos = BlockPos.of(Long.parseLong(e.getKey().substring(e.getKey().indexOf('@') + 1)));
                if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof LumicoreBlockEntity desk)) continue;
                String a = m.address().toLowerCase(java.util.Locale.ROOT);
                long now = clock();
                switch (a) {
                    case "/csc/play" -> { if (st.player != null) { mode(level, desk, Live.PLAYBACK); st.player.play(now); } }
                    case "/csc/pause" -> { if (st.player != null) st.player.pause(); }
                    case "/csc/stop" -> { if (st.player != null) st.player.stop(desk.dmx); }
                    case "/csc/restart" -> { if (st.player != null) { mode(level, desk, Live.PLAYBACK); st.player.restart(now, desk.dmx); } }
                    case "/csc/seek" -> { if (st.player != null) st.player.seek(Math.round(m.number(0, 0) * 1000), desk.dmx); }
                    case "/csc/speed" -> { if (st.player != null) st.player.speed = Math.max(0.1f, Math.min(4f, m.number(0, 1))); }
                    case "/csc/loop" -> { if (st.player != null) st.player.loop = m.number(0, 1) != 0; }
                    case "/csc/load" -> load(level, desk, st, m.text(0), null);
                    case "/csc/record/start" -> recordStart(level, desk, st, m.text(0).isEmpty() ? "OSC recording" : m.text(0), null);
                    case "/csc/record/stop" -> recordStop(level, desk, st, null);
                    case "/csc/mode" -> {
                        int mode = switch (m.text(0).toLowerCase(java.util.Locale.ROOT)) {
                            case "internal" -> Live.INTERNAL; case "autoshow" -> Live.AUTOSHOW; case "external" -> Live.EXTERNAL;
                            case "playback" -> Live.PLAYBACK; default -> -1;
                        };
                        if (mode >= 0) mode(level, desk, mode);
                    }
                    default -> { }
                }
            }
        }
    }

    private static void openMidi(String name) {
        try {
            for (javax.sound.midi.MidiDevice.Info info : javax.sound.midi.MidiSystem.getMidiDeviceInfo()) {
                if (!info.getName().toLowerCase(java.util.Locale.ROOT).contains(name.toLowerCase(java.util.Locale.ROOT))) continue;
                javax.sound.midi.MidiDevice d = javax.sound.midi.MidiSystem.getMidiDevice(info);
                if (d.getMaxTransmitters() == 0) continue;
                d.open();
                d.getTransmitter().setReceiver(new javax.sound.midi.Receiver() {
                    @Override public void send(javax.sound.midi.MidiMessage msg, long ts) {
                        byte[] b = msg.getMessage();
                        synchronized (MTC) { MTC.message(b, b.length, System.nanoTime()); }
                    }
                    @Override public void close() { }
                });
                midiDevice = d;
                midiStatus = "MIDI timecode: " + info.getName();
                return;
            }
            midiStatus = "MIDI device \"" + name + "\" not found";
        } catch (Exception | LinkageError e) {
            midiStatus = "MIDI unavailable: " + e.getMessage();
        }
    }

    private static void closeMidi() {
        if (midiDevice instanceof javax.sound.midi.MidiDevice d) {
            try { d.close(); } catch (Exception ignored) { }
        }
        midiDevice = null;
        midiStatus = "off";
    }

    // ------------------------------------------------------------ ticks
    /** Universes a desk takes DMX from: its patch and, with members, the Virtual Matrix. */
    static Set<Integer> universes(Show show) {
        Set<Integer> u = new TreeSet<>();
        boolean matrix = false;
        for (Show.Patch p : show.patch) {
            if (p.dmxMode == DmxMode.MATRIX) matrix = true; else u.add(p.universe);
        }
        if (matrix) u.add(show.external.matrixUniverse);
        return u;
    }

    /** Every server tick for each loaded desk. */
    public static void tick(ServerLevel level, LumicoreBlockEntity desk) {
        if (server != level.getServer()) started(level.getServer());
        DeskState st = STATES.get(key(level, desk.getBlockPos()));
        int control = desk.live.control;
        boolean dmx = Live.dmxDriven(control);
        com.concertstage.light.dmx.ArtNetSettings art = config.art;
        // rev. 16h: control policy. "Art-Net takes control": a desk not in External or Playback goes to External as soon as Art-Net
        // arrives on its universes; "Return to CSC control" after a loss: it goes back to External when the signal returns.
        boolean watch = !dmx && art.enabled && (art.priority == com.concertstage.light.dmx.ArtNetSettings.PRIORITY_ARTNET || st != null && st.returnedOnLoss);
        if (st == null && !dmx && !watch) return;
        if (st == null) st = state(level, desk);
        long now = clock();
        if (watch && level.getGameTime() % 5 == 0 && !desk.show.patch.isEmpty()) {
            ensureReceiver();
            MERGE.expire(now);
            boolean live = false;
            for (int u : universes(desk.show)) if (!MERGE.lost(u)) { live = true; break; }
            if (live) {
                st.returnedOnLoss = false;
                st.blackedOut = false;
                mode(level, desk, Live.EXTERNAL);
                control = desk.live.control;
                dmx = Live.dmxDriven(control);
            }
        }
        if (control == Live.EXTERNAL) {
            ensureReceiver();
            MERGE.expire(now);
            Set<Integer> us = universes(desk.show);
            SACN_WANTED.addAll(us);
            if (level.getGameTime() % 20 == 0) joinSacn();
            byte[] tmp = new byte[512];
            boolean lost = true;
            for (int u : us) {
                long v = MERGE.snapshot(u, tmp);
                if (v < 0) continue;
                if (!MERGE.lost(u)) lost = false;
                Long prev = st.versions.get(u);
                if (prev == null || prev != v) { desk.dmx.set(u, tmp); st.versions.put(u, v); }
            }
            desk.dmx.lost = lost;
            if (!lost) st.blackedOut = false;
            // rev. 16h: signal lost (every universe of the desk silent for 3 s): hold (as before), blackout, or back to the desk
            else if (!us.isEmpty() && st.everReceived(us)) {
                if (art.loss == com.concertstage.light.dmx.ArtNetSettings.LOSS_BLACKOUT && !st.blackedOut) {
                    blackout(desk);
                    st.blackedOut = true;
                    st.versions.clear();                         // the next frame received is applied again in full
                } else if (art.loss == com.concertstage.light.dmx.ArtNetSettings.LOSS_RETURN) {
                    int back = Live.dmxDriven(desk.live.previousControl) ? Live.AUTOSHOW : desk.live.previousControl;
                    st.returnedOnLoss = true;
                    st.versions.clear();
                    mode(level, desk, back);
                }
            }
        } else if (control == Live.PLAYBACK && st.player != null) {
            ShowPlayer pl = st.player;
            long mtc;
            synchronized (MTC) { mtc = st.syncMtc && MTC.running(System.nanoTime()) ? MTC.ms(System.nanoTime()) - config.mtcOffsetMs : -1; }
            Engine.Music music = st.syncMusic && pl.show.musicStartMs >= 0 && !pl.show.trackSha1.isEmpty()
                ? LightServer.music(level, desk.live, level.getGameTime(), 0) : Engine.Music.NONE;
            if (mtc >= 0) pl.follow(mtc, desk.dmx);
            else if (music.valid() && music.playing() && pl.show.trackSha1.equals(music.sha1()))
                pl.follow(music.positionMs() - pl.show.musicStartMs, desk.dmx);       // the music is the clock: no drift over long shows
            else pl.tick(now, desk.dmx);
            desk.dmx.lost = false;
        }
        if (st.recorder != null && st.recorder.stopped()) recordStop(level, desk, st, null);
        if (testUntil > 0 && now > testUntil) testUntil = 0;
        if (dmx) broadcast(level, desk, st);
        if (level.getGameTime() - st.lastStatusTick >= 10) {
            st.lastStatusTick = level.getGameTime();
            byte[] msg = statusMessage(level, desk, st);
            for (ServerPlayer p : watchers(level, desk.getBlockPos())) if (p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(desk.getBlockPos())) < 64 * 64) LightNet.toClient(p, msg);
        }
    }

    /** Rev. 16h: signal loss, blackout: the intensity channels of every patched fixture (and the matrix intensity) to 0; positions held. */
    static void blackout(LumicoreBlockEntity desk) {
        Map<Integer, byte[]> out = new HashMap<>();
        for (Show.Patch p : desk.show.patch) {
            int u = p.dmxMode == DmxMode.MATRIX ? desk.show.external.matrixUniverse : p.universe;
            byte[] cur = desk.dmx.get(u);
            if (cur == null) continue;
            byte[] d = out.computeIfAbsent(u, k -> cur.clone());
            if (p.dmxMode == DmxMode.MATRIX) {
                int a = desk.show.external.matrixAddress - 1;          // channel 1 of the matrix: intensity
                if (a >= 0 && a < 512) d[a] = 0;
                continue;
            }
            DmxMode m = DmxMode.of(p.profile(), p.dmxMode);
            for (int c = 0; c < m.channels.length; c++) {
                DmxMode.Channel ch = m.channels[c];
                boolean intensity = ch.attr() == com.concertstage.light.core.Attr.DIMMER || ch.kind() == DmxMode.Kind.ZONE_LEVEL
                    || ch.kind() == DmxMode.Kind.ZONE_R || ch.kind() == DmxMode.Kind.ZONE_G || ch.kind() == DmxMode.Kind.ZONE_B;
                int slot = p.address - 1 + c;
                if (intensity && slot >= 0 && slot < 512) d[slot] = 0;
            }
        }
        for (Map.Entry<Integer, byte[]> e : out.entrySet()) desk.dmx.set(e.getKey(), e.getValue());
    }

    static List<ServerPlayer> watchers(ServerLevel level, BlockPos pos) {
        return LightServer.watchers(level, pos);                     // 1.2.0 beta 2: the distant players that follow the desk too
    }

    /** Changed slots of the universes of a desk to every player that sees it (full copy every 5 s). */
    static void broadcast(ServerLevel level, LumicoreBlockEntity desk, DeskState st) {
        boolean full = level.getGameTime() - st.lastFullTick >= 100;
        Bytes.Writer w = LightNet.message(LightNet.S_DMX, desk.getBlockPos());
        List<Object[]> parts = new ArrayList<>();
        for (int u : desk.dmx.universes()) {
            byte[] cur = desk.dmx.get(u);
            byte[] prev = st.sent.get(u);
            if (full || prev == null) {
                parts.add(new Object[] {u, 0, cur.clone()});
            } else {
                byte[] runs = CscShowFile.runs(prev, cur);
                if (runs.length > 0) parts.add(new Object[] {u, 1, runs});
            }
            st.sent.computeIfAbsent(u, k -> new byte[512]);
            System.arraycopy(cur, 0, st.sent.get(u), 0, 512);
        }
        if (full) st.lastFullTick = level.getGameTime();
        if (parts.isEmpty()) return;
        w.bool(desk.dmx.lost).varInt(parts.size());
        for (Object[] p : parts) w.varInt((Integer) p[0]).u8((Integer) p[1]).bytes((byte[]) p[2]);
        byte[] msg = w.toBytes();
        for (ServerPlayer p : watchers(level, desk.getBlockPos())) LightNet.toClient(p, msg);
    }

    /** A player opened the desk: the DMX it is driven by, in full. */
    public static void sendFull(ServerPlayer p, LumicoreBlockEntity desk) {
        if (desk.dmx.universes().isEmpty()) return;
        Bytes.Writer w = LightNet.message(LightNet.S_DMX, desk.getBlockPos()).bool(desk.dmx.lost).varInt(desk.dmx.universes().size());
        for (int u : desk.dmx.universes()) w.varInt(u).u8(0).bytes(desk.dmx.get(u));
        LightNet.toClient(p, w.toBytes());
    }

    /** Client: applies a DMX message to its copy of the desk. */
    public static void receiveDmx(LumicoreBlockEntity desk, Bytes.Reader r) {
        boolean lost = r.bool();
        int n = r.count(64);
        for (int i = 0; i < n && r.ok(); i++) {
            int u = r.varInt(), kind = r.u8();
            byte[] b = r.bytes(4096);
            if (!r.ok() || u < 0 || u > com.concertstage.light.dmx.ArtNetSettings.MAX_UNIVERSE) return;
            if (kind == 0 && b.length == 512) desk.dmx.set(u, b);
            else if (kind == 1) {
                byte[] cur = desk.dmx.get(u);
                byte[] next = cur == null ? new byte[512] : cur.clone();
                CscShowFile.apply(b, next);
                desk.dmx.set(u, next);
            }
        }
        desk.dmx.lost = lost;
    }

    // ------------------------------------------------------------ status for the console
    /** What the console shows (sent every half second to the players near the desk). */
    static byte[] statusMessage(ServerLevel level, LumicoreBlockEntity desk, DeskState st) {
        Bytes.Writer w = LightNet.message(LightNet.S_EXT, desk.getBlockPos());
        long now = clock();
        w.str(receiverStatus).str(midiStatus).varLong(MERGE.packets()).varLong(MERGE.rejected()).str(DmxPackets.lastError);
        List<DmxMerge.Status> ss = MERGE.status(now);
        w.varInt(Math.min(16, ss.size()));
        for (int i = 0; i < Math.min(16, ss.size()); i++) {
            DmxMerge.Status s = ss.get(i);
            w.varInt(s.universe()).bool(s.lost()).varInt(s.sources()).str(s.names()).varLong(s.packets()).sInt(s.winningPriority()).sLong(s.ageMs());
        }
        ShowRecorder rec = st.recorder;
        w.bool(rec != null);
        if (rec != null) w.str(st.recordName).varLong(rec.duration(now)).varInt(rec.frames()).varLong(rec.bytes());
        ShowPlayer pl = st.player;
        w.bool(pl != null);
        if (pl != null) {
            w.str(st.playerName).varLong(pl.position()).varLong(pl.duration()).bool(pl.playing).bool(pl.loop).f32(pl.speed)
                .bool(st.syncMusic).str(pl.show.trackName).bool(st.syncMtc);
        }
        w.bool(st.osc).bool(testUntil > 0).str(st.lastExport);
        List<String[]> lib = library();
        w.varInt(Math.min(64, lib.size()));
        for (int i = 0; i < Math.min(64, lib.size()); i++) w.str(lib.get(i)[0]).str(lib.get(i)[1]);
        writeNet(w, level, st, now);
        return w.toBytes();
    }

    private static volatile List<NetIf> cachedIfs;
    private static long cachedIfsMs;

    /**
     * Rev. 16h: the Art-Net part of the status (always: settings, state, counters, per Port-Address activity; while the Settings page
     * is open: the network interfaces; while the DMX monitor is open: the 512 slots of its universe). Every value is measured here.
     */
    static void writeNet(Bytes.Writer w, ServerLevel level, DeskState st, long now) {
        com.concertstage.light.dmx.ArtNetSettings a = config.art;
        a.write(w);
        boolean open = artSocket != null;
        w.varInt(ART.state(now, open)).str(ART.bindError).str(open ? a.bind + ":" + a.port : "")
            .varLong(ART.packets.get()).varLong(ART.accepted.get()).varLong(ART.invalid.get()).varLong(ART.filtered.get()).varLong(ART.notSelected.get())
            .varLong(ART.rate(now)).str(ART.lastSource).str(ART.lastFilteredSource).str(ART.lastInvalid)
            .sLong(ART.lastAcceptedMs < 0 ? -1 : now - ART.lastAcceptedMs).bool(allowed()).bool(server != null && server.isDedicatedServer()).str(st.lastNetReport);
        // per Port-Address activity (the 64 lowest seen)
        List<Integer> ports = new ArrayList<>(new TreeSet<>(ART.ports.keySet()));
        int n = Math.min(64, ports.size());
        w.varInt(n);
        for (int i = 0; i < n; i++) {
            long[] v = ART.ports.get(ports.get(i));
            w.varInt(ports.get(i)).varLong(v == null ? 0 : v[0]).sLong(v == null ? -1 : now - v[1]);
        }
        boolean net = level.getGameTime() <= st.netUntilTick;
        w.bool(net);
        if (net) {
            List<NetIf> ifs = cachedIfs;
            if (ifs == null || now - cachedIfsMs > 10_000) { ifs = interfaces(); cachedIfs = ifs; cachedIfsMs = now; }
            w.varInt(Math.min(16, ifs.size()));
            for (int i = 0; i < Math.min(16, ifs.size()); i++) w.str(ifs.get(i).name()).str(ifs.get(i).address()).bool(ifs.get(i).up()).bool(ifs.get(i).loopback());
        }
        boolean mon = level.getGameTime() <= st.monUntilTick;
        w.bool(mon);
        if (mon) {
            byte[] d = new byte[512];
            long v = MERGE.snapshot(st.monUniverse, d);
            List<DmxMerge.Status> ss = MERGE.status(now);
            long age = -1;
            boolean lost = true;
            for (DmxMerge.Status x : ss) if (x.universe() == st.monUniverse) { age = x.ageMs(); lost = x.lost(); }
            w.varInt(st.monUniverse).bool(v >= 0).bool(lost).sLong(age).bytes(d);
        }
    }

    /** Recorded shows: name, size. */
    static List<String[]> library() {
        List<String[]> out = new ArrayList<>();
        Path dir = base == null ? null : base.resolve("recordings");
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            s.filter(f -> f.getFileName().toString().endsWith(CscShowFile.EXTENSION)).sorted().forEach(f -> {
                String n = f.getFileName().toString();
                try { out.add(new String[] {n.substring(0, n.length() - CscShowFile.EXTENSION.length()), (Files.size(f) / 1024) + " kB"}); }
                catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
        return out;
    }

    // ------------------------------------------------------------ actions
    public static final int X_MONITOR = 0, X_HELLO = 3, X_REC_START = 1, X_REC_STOP = 2, X_LOAD = 4, X_PLAY = 5, X_PAUSE = 6, X_STOP = 7, X_RESTART = 8,
        X_SEEK = 9, X_LOOP = 10, X_SPEED = 11, X_SYNC = 12, X_AUTOPATCH = 13, X_EXPORT = 14, X_IMPORT_PATCH = 15, X_IMPORT_MVR = 16,
        X_DELETE = 17, X_TEST = 19, X_OSC = 20, X_SYNC_MTC = 21, X_UNLOAD = 22,
        // rev. 16h: CSC ART-NET NETWORK SETTINGS, ART-NET MONITOR, DMX MONITOR
        X_NET_HELLO = 23, X_NET_APPLY = 24, X_NET_SAVE = 25, X_NET_RESET = 26, X_NET_REFRESH = 27, X_NET_TEST = 28, X_DMX_MON = 29;

    /**
     * Who may change the network settings of the receiver: the player hosting a single-player / LAN world, or a server operator
     * (permission level 2). Being allowed on a desk is not enough: these settings open ports of the machine for every desk.
     */
    static boolean netAllowed(ServerPlayer p) {
        if (p == null || server == null) return false;
        return server.isSingleplayerOwner(p.getGameProfile()) || p.hasPermissions(2);
    }

    /** A console action (LightNet.A_EXT), on the server thread; {@code operate}: the player may program this desk. */
    public static void action(ServerPlayer p, ServerLevel level, LumicoreBlockEntity desk, Bytes.Reader r, boolean operate) {
        if (server != level.getServer()) started(level.getServer());
        int op = r.varInt();
        DeskState st = state(level, desk);
        if (op == X_MONITOR) { ensureReceiver(); return; }
        if (op == X_HELLO) { st.lastStatusTick = 0; return; }           // the External tab is open: status from now on (opens no port)
        // rev. 16h: the Settings > Network page and the monitors (read only: any player who may open the desk)
        if (op == X_NET_HELLO || op == X_NET_REFRESH) { st.netUntilTick = level.getGameTime() + 100; st.lastStatusTick = 0; if (op == X_NET_REFRESH) cachedIfs = null; return; }
        if (op == X_DMX_MON) {
            int u = r.varInt();
            if (r.ok() && com.concertstage.light.dmx.ArtNetSettings.validUniverse(u)) { st.monUniverse = u; st.monUntilTick = level.getGameTime() + 100; st.lastStatusTick = 0; }
            return;
        }
        if (op >= X_NET_APPLY && op <= X_NET_TEST && op != X_NET_REFRESH) {
            if (!netAllowed(p)) { message(p, "Network settings: only the host of the world or a server operator"); return; }
            switch (op) {
                case X_NET_APPLY, X_NET_SAVE -> {
                    com.concertstage.light.dmx.ArtNetSettings a = new com.concertstage.light.dmx.ArtNetSettings();
                    if (!a.read(r)) { message(p, "Network settings: malformed request"); return; }
                    if (server.isDedicatedServer() && !p.hasPermissions(4) && a.allowDedicated != config.art.allowDedicated) a.allowDedicated = config.art.allowDedicated;
                    String rep = applyArt(a, op == X_NET_SAVE);
                    st.lastNetReport = rep;
                    message(p, rep);
                    com.mojang.logging.LogUtils.getLogger().info("Concert Stage: Art-Net settings by {}: {}", p.getGameProfile().getName(), rep);
                }
                case X_NET_RESET -> {
                    com.concertstage.light.dmx.ArtNetSettings a = new com.concertstage.light.dmx.ArtNetSettings();
                    a.allowDedicated = config.art.allowDedicated;
                    String rep = applyArt(a, true);
                    st.lastNetReport = "Defaults: " + rep;
                    message(p, st.lastNetReport);
                }
                case X_NET_TEST -> testConnection(p);
                default -> { }
            }
            st.lastStatusTick = 0;
            return;
        }
        if (!operate) { message(p, "No permission on this desk"); return; }
        long now = clock();
        switch (op) {
            case X_REC_START -> recordStart(level, desk, st, clean(r.str(64)), p);
            case X_REC_STOP -> recordStop(level, desk, st, p);
            case X_LOAD -> load(level, desk, st, clean(r.str(64)), p);
            case X_UNLOAD -> { st.player = null; st.playerName = ""; }
            case X_PLAY -> { if (st.player != null) { mode(level, desk, Live.PLAYBACK); st.player.play(now); } }
            case X_PAUSE -> { if (st.player != null) st.player.pause(); }
            case X_STOP -> { if (st.player != null) st.player.stop(desk.dmx); }
            case X_RESTART -> { if (st.player != null) { mode(level, desk, Live.PLAYBACK); st.player.restart(now, desk.dmx); } }
            case X_SEEK -> { long ms = r.varLong(); if (st.player != null) st.player.seek(ms, desk.dmx); }
            case X_LOOP -> { boolean b = r.bool(); if (st.player != null) st.player.loop = b; }
            case X_SPEED -> { float s = r.f32(); if (st.player != null && Float.isFinite(s)) st.player.speed = Math.max(0.1f, Math.min(4f, s)); }
            case X_SYNC -> st.syncMusic = r.bool();
            case X_SYNC_MTC -> st.syncMtc = r.bool();
            case X_OSC -> { st.osc = r.bool(); if (st.osc) ensureReceiver(); }
            case X_AUTOPATCH -> autopatch(level, desk, p, r.varInt(), r.varInt(), r.varInt());
            case X_EXPORT -> {
                int kind = r.varInt();
                int n = r.count(Show.MAX_FIXTURES);
                Set<Integer> sel = new TreeSet<>();
                for (int i = 0; i < n; i++) sel.add(r.varInt());
                if (!r.ok()) return;
                st.lastExport = ExternalExport.export(base, desk.show, kind, sel);
                message(p, st.lastExport);
            }
            case X_IMPORT_PATCH, X_IMPORT_MVR -> {
                String file = r.str(256);
                ExternalExport.Imported im = op == X_IMPORT_PATCH ? ExternalExport.importPatch(base, desk.show, file) : ExternalExport.importMvr(base, desk.show, file);
                if (im.command() != null) LightServer.apply(level, desk, im.command());
                message(p, im.report());
            }
            case X_DELETE -> {
                String name = clean(r.str(64));
                try {
                    Path f = showFile(name);
                    if (f != null && Files.deleteIfExists(f)) message(p, "Deleted " + name);
                } catch (IOException e) { message(p, "Could not delete " + name + ": " + e.getMessage()); }
            }
            case X_TEST -> testSignal(desk.show, p);
            default -> { }
        }
    }

    static void mode(ServerLevel level, LumicoreBlockEntity desk, int mode) {
        if (desk.live.control != mode) LightServer.apply(level, desk, Commands.op(Commands.CONTROL_MODE).varInt(mode).toBytes());
    }

    static void message(ServerPlayer p, String text) {
        if (p == null) return;
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("[CSC] " + text), true);
    }

    static String clean(String s) {
        return ExternalExport.clean(s);
    }

    static Path showFile(String name) {
        if (base == null) return null;
        return base.resolve("recordings").resolve(clean(name) + CscShowFile.EXTENSION);
    }

    static void recordStart(ServerLevel level, LumicoreBlockEntity desk, DeskState st, String name, ServerPlayer p) {
        if (st.recorder != null) { message(p, "Already recording"); return; }
        ensureReceiver();
        Set<Integer> us = universes(desk.show);
        if (us.isEmpty()) { message(p, "Nothing patched: no universe to record"); return; }
        SACN_WANTED.addAll(us);
        joinSacn();
        ShowRecorder rec = new ShowRecorder(clock(), us, desk.show, name, receiverStatus);
        Engine.Music m = LightServer.music(level, desk.live, level.getGameTime(), 0);
        if (m.valid() && m.playing()) {
            var audio = LightServer.audio(level, desk.live);
            var t = audio == null ? null : audio.currentTrack();
            rec.music(m.sha1(), t == null ? "" : t.name(), m.positionMs());
        }
        // what the universes hold right now is the first key frame
        byte[] tmp = new byte[512];
        long now = clock();
        for (int u : us) if (MERGE.snapshot(u, tmp) >= 0) rec.capture(u, tmp, now);
        st.recorder = rec;
        st.recordName = name;
        RECORDERS.add(rec);
        message(p, "Recording \"" + name + "\" (universes " + us + ")");
    }

    static void recordStop(ServerLevel level, LumicoreBlockEntity desk, DeskState st, ServerPlayer p) {
        ShowRecorder rec = st.recorder;
        if (rec == null) return;
        RECORDERS.remove(rec);
        st.recorder = null;
        CscShowFile f = rec.finish(clock());
        try {
            Path out = showFile(st.recordName);
            Files.createDirectories(out.getParent());
            byte[] b = f.toBytes();
            Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
            Files.write(tmp, b);
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            message(p, "Saved " + out.getFileName() + " (" + f.frames.size() + " frames, " + f.durationMs / 1000 + " s, " + b.length / 1024 + " kB)");
        } catch (IOException e) {
            message(p, "Recording could not be saved: " + e.getMessage());
        }
    }

    static void load(ServerLevel level, LumicoreBlockEntity desk, DeskState st, String name, ServerPlayer p) {
        try {
            Path f = showFile(name);
            if (f == null || !Files.isRegularFile(f)) { message(p, "No show " + name); return; }
            CscShowFile show = CscShowFile.fromBytes(Files.readAllBytes(f));
            st.player = new ShowPlayer(show);
            st.playerName = name;
            st.player.seek(0, desk.dmx);
            // the recording expects the patch it was made with: say where it differs
            int diff = 0;
            for (Show.Patch rp : show.patch) {
                Show.Patch cur = desk.show.fixture(rp.fid);
                if (cur == null || cur.universe != rp.universe || cur.address != rp.address || cur.dmxMode != rp.dmxMode || !cur.type.equals(rp.type)) diff++;
            }
            message(p, "Loaded " + name + " (" + show.durationMs / 1000 + " s)" + (diff > 0 ? " - " + diff + " fixtures are patched differently from the recording" : ""));
        } catch (IOException e) {
            message(p, "Could not read " + name + ": " + e.getMessage());
        } catch (CscShowFile.BadFile e) {
            message(p, name + " is not a valid show: " + e.getMessage());
        }
    }

    static void autopatch(ServerLevel level, LumicoreBlockEntity desk, ServerPlayer p, int universe, int mode, int share) {
        if (!com.concertstage.light.dmx.ArtNetSettings.validUniverse(universe) || mode < 0 || mode > 2 || share < 0 || share > 3) return;
        AutoPatch.Plan plan = AutoPatch.plan(desk.show, universe, mode, share);
        Bytes.Writer w = Commands.op(Commands.PATCH_BULK).varInt(plan.entries().size());
        for (AutoPatch.Entry e : plan.entries()) w.varInt(e.fid()).varInt(e.universe()).varInt(e.address()).varInt(e.mode());
        LightServer.apply(level, desk, w.toBytes());
        if (share == AutoPatch.MATRIX) {
            int addr = Math.max(1, Math.min(497, desk.show.external.matrixAddress));
            LightServer.apply(level, desk, Commands.op(Commands.MATRIX_SETTINGS).varInt(universe).varInt(addr).varInt(desk.show.external.matrixOrder).toBytes());
        }
        message(p, plan.report());
    }

    // ------------------------------------------------------------ test signal
    /**
     * Sends Art-Net to this computer for 10 s (127.0.0.1): a slow pan sweep, a dimmer chase and changing colours for every fixture of
     * the patch, through the receiver like a real desk. It checks the whole chain without any other software.
     */
    static void testSignal(Show show, ServerPlayer p) {
        if (!allowed()) { message(p, "Receiver disabled on this server"); return; }
        ensureReceiver();
        Show s = show.copy();
        testUntil = clock() + 10_000;
        Thread t = new Thread(() -> {
            try (DatagramSocket out = new DatagramSocket()) {
                String bindIp = com.concertstage.light.dmx.ArtNetSettings.current.bind;
                InetAddress lo = bindIp.equals("0.0.0.0") ? InetAddress.getLoopbackAddress() : com.concertstage.light.dmx.ArtNetSettings.address(bindIp);
                Map<Integer, byte[]> uni = new HashMap<>();
                int seq = 1;
                long start = clock();
                while (clock() - start < 10_000 && running) {
                    double t1 = (clock() - start) / 1000.0;
                    uni.clear();
                    int i = 0;
                    for (Show.Patch fx : s.patch) {
                        byte[] u = uni.computeIfAbsent(fx.universe, k -> new byte[512]);
                        DmxMode m = DmxMode.of(fx.profile(), fx.dmxMode);
                        double ph = t1 * 0.5 + i * 0.15;
                        for (int c = 0; c < m.channels.length; c++) {
                            int slot = fx.address - 1 + c;
                            if (slot >= 512) break;
                            DmxMode.Channel ch = m.channels[c];
                            int v = ch.def();
                            if (ch.attr() != null) switch (ch.attr()) {
                                case PAN -> v = ch.kind() == DmxMode.Kind.FINE ? 0 : (int) (128 + 40 * Math.sin(ph * 2 * Math.PI));
                                case TILT -> v = ch.kind() == DmxMode.Kind.FINE ? 0 : (int) (128 + 25 * Math.cos(ph * 2 * Math.PI));
                                case DIMMER -> v = ch.kind() == DmxMode.Kind.FINE ? 255 : (int) (255 * (0.5 + 0.5 * Math.sin(ph * 4)));
                                case RED -> v = (int) (255 * (0.5 + 0.5 * Math.sin(t1)));
                                case GREEN -> v = (int) (255 * (0.5 + 0.5 * Math.sin(t1 + 2)));
                                case BLUE -> v = (int) (255 * (0.5 + 0.5 * Math.sin(t1 + 4)));
                                default -> { }
                            }
                            if (ch.kind() == DmxMode.Kind.COLOR_MACRO) v = ((int) t1 % 17) * DmxMode.MACRO_STEP;
                            if (ch.kind() == DmxMode.Kind.HUE) v = (int) (t1 * 40) & 255;            // rev. 17 Compact: the hue turns
                            if (ch.kind() == DmxMode.Kind.SATURATION) v = 180;
                            u[slot] = (byte) Math.max(0, Math.min(255, v));
                        }
                        i++;
                    }
                    if (VirtualMatrix.members(s).size() > 0) {
                        byte[] u = uni.computeIfAbsent(s.external.matrixUniverse, k -> new byte[512]);
                        int[] mv = {255, 255, 40, 0, 0, 80, 255, 90, 40, 160, 0, 70, 200, 0, 128, 128};
                        for (int c = 0; c < 16 && s.external.matrixAddress - 1 + c < 512; c++) u[s.external.matrixAddress - 1 + c] = (byte) mv[c];
                    }
                    // rev. 16h: to the Port-Address that feeds each CSC universe (the mapping; universe 0 = Port-Address 0 by default),
                    // at the receiver's own port
                    com.concertstage.light.dmx.ArtNetSettings a = com.concertstage.light.dmx.ArtNetSettings.current;
                    for (Map.Entry<Integer, byte[]> e : uni.entrySet()) {
                        int pa = a.portAddressOf(e.getKey());
                        if (pa == com.concertstage.light.dmx.ArtNetSettings.NONE) continue;
                        byte[] pk = DmxPackets.artDmx(pa, seq, e.getValue(), 512);
                        out.send(new DatagramPacket(pk, pk.length, lo, a.port));
                    }
                    seq = seq % 255 + 1;
                    Thread.sleep(25);
                }
            } catch (IOException | InterruptedException ignored) {
            } finally {
                testUntil = 0;
            }
        }, "CSC test signal");
        t.setDaemon(true);
        t.start();
        message(p, "Test signal: 10 s of Art-Net to 127.0.0.1 (switch the desk to External to see it)");
    }
}
