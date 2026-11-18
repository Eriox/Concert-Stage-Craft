package com.concertstage.light.dmx;

import com.concertstage.light.core.Bytes;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.BitSet;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * CSC ART-NET NETWORK SETTINGS (1.2.0 rev. 16h): what the Art-Net receiver listens to and how a received Port-Address becomes a CSC
 * universe. Pure Java (no Minecraft): built, checked and saved on the server, edited in the LUMICORE X1 (Settings → Network → Art-Net),
 * tested off-game.
 *
 * <p><b>Universes.</b> Art-Net carries a 15-bit Port-Address (Net 7 bits · Sub-Net 4 bits · Universe 4 bits): 0:0:0 = 0, 0:0:1 = 1,
 * 0:1:0 = 16, 1:0:0 = 256. A Port-Address is received when it is <i>selected</i> (any of 0 … 32767, 0 included) or has an explicit
 * mapping. It becomes the CSC universe of its mapping, by default <b>the same number</b> (0 → 0, 1 → 1: no automatic +1). CSC universes
 * are {@value #MIN_UNIVERSE} … {@value #MAX_UNIVERSE}; universe 0 is a normal universe. "No universe" is {@link #NONE} (−1), never 0.
 *
 * <p>sACN is not affected: an sACN universe N (1 … 63999, the standard has no universe 0) stays CSC universe N.
 */
public final class ArtNetSettings {
    /** CSC universes: 0 for Art-Net plus the complete E1.31 range 1..63999. */
    public static final int MIN_UNIVERSE = 0, MAX_UNIVERSE = 63999;
    /** "No universe" (a Port-Address that is not received, a CSC universe no Port-Address feeds). Never 0. */
    public static final int NONE = -1;
    public static final int MAX_PORT_ADDRESS = 32767;
    public static final int DEFAULT_PORT = 6454;
    /** Control when Art-Net data arrives: the desk mode decides (as before) / Art-Net takes the desk to External by itself. */
    public static final int PRIORITY_DESK = 0, PRIORITY_ARTNET = 1;
    /** When the Art-Net signal is lost (no packet for 3 s) on a desk in External. */
    public static final int LOSS_HOLD = 0, LOSS_BLACKOUT = 1, LOSS_RETURN = 2;
    public static final String[] PRIORITY_NAMES = {"Desk mode decides", "Art-Net takes control"};
    public static final String[] LOSS_NAMES = {"Hold last value", "Blackout", "Return to CSC control"};

    public boolean enabled = true;
    /** Address the receiver binds to: 0.0.0.0 = every IPv4 interface. */
    public String bind = "0.0.0.0";
    public int port = DEFAULT_PORT;
    /** Source filter: any sender, or only {@link #sourceIp}. */
    public boolean anySource = true;
    public String sourceIp = "";
    /** Port-Addresses received (bit = Port-Address). */
    public final BitSet selected = new BitSet();
    /** Explicit Port-Address → CSC universe (absent = same number). */
    public final TreeMap<Integer, Integer> mapping = new TreeMap<>();
    public int priority = PRIORITY_DESK;
    public int loss = LOSS_HOLD;
    /** A dedicated server opens the port only when allowed (by an operator in the console, or in the file). */
    public boolean allowDedicated = false;

    public ArtNetSettings() {
        selected.set(0, 16);                          // Port-Addresses 0 … 15 (Net 0, Sub-Net 0) received by default
    }

    /** The only process-wide settings in use (set by the server; read by the exports to name the Port-Address of a CSC universe). */
    public static volatile ArtNetSettings current = new ArtNetSettings();

    // ------------------------------------------------------------ universes
    /** CSC universe that receives Port-Address {@code pa}, or {@link #NONE} when it is not received. */
    public int cscUniverse(int pa) {
        if (pa < 0 || pa > MAX_PORT_ADDRESS) return NONE;
        Integer m = mapping.get(pa);
        if (m != null) return m;
        if (!selected.get(pa)) return NONE;
        return pa <= MAX_UNIVERSE ? pa : NONE;
    }

    /** Port-Address that feeds CSC universe {@code u} (what a desk must send), or {@link #NONE}. */
    public int portAddressOf(int u) {
        if (u < MIN_UNIVERSE || u > MAX_UNIVERSE) return NONE;
        for (Map.Entry<Integer, Integer> e : mapping.entrySet()) if (e.getValue() == u) return e.getKey();
        // the same number, if that Port-Address is received and not mapped elsewhere
        if (!mapping.containsKey(u) && selected.get(u)) return u;
        return NONE;
    }

    public static boolean validUniverse(int u) {
        return u >= MIN_UNIVERSE && u <= MAX_UNIVERSE;
    }

    /** "Net:Sub-Net:Universe" of a Port-Address (3:15:15 style, as consoles show the Art-Net address). */
    public static String netSubUni(int pa) {
        return (pa >> 8 & 0x7F) + ":" + (pa >> 4 & 0xF) + ":" + (pa & 0xF);
    }

    public static int portAddress(int net, int sub, int uni) {
        return (net & 0x7F) << 8 | (sub & 0xF) << 4 | (uni & 0xF);
    }

    /** Selects the Port-Addresses {@code start} … {@code end} only (Universe Start / Universe End). */
    public void range(int start, int end) {
        int a = Math.max(0, Math.min(MAX_PORT_ADDRESS, Math.min(start, end))), b = Math.max(0, Math.min(MAX_PORT_ADDRESS, Math.max(start, end)));
        selected.clear();
        selected.set(a, b + 1);
    }

    public int rangeStart() {
        int i = selected.nextSetBit(0);
        return i < 0 ? NONE : i;
    }

    public int rangeEnd() {
        int i = selected.length() - 1;
        return i < 0 ? NONE : i;
    }

    // ------------------------------------------------------------ checks
    /** Null when the settings are usable, otherwise what is wrong (shown in the console, nothing applied). */
    public String validate() {
        if (port < 1 || port > 65535) return "UDP port must be 1 to 65535";
        if (!ipv4(bind)) return "Local IP is not an IPv4 address: " + bind;
        if (!anySource && !ipv4(sourceIp)) return "Console IP is not an IPv4 address: " + sourceIp;
        if (selected.length() > MAX_PORT_ADDRESS + 1) return "Port-Address above 32767";
        for (Map.Entry<Integer, Integer> e : mapping.entrySet()) {
            if (e.getKey() < 0 || e.getKey() > MAX_PORT_ADDRESS) return "Mapping: Port-Address " + e.getKey() + " out of 0 to 32767";
            if (!validUniverse(e.getValue())) return "Mapping: CSC universe " + e.getValue() + " out of " + MIN_UNIVERSE + " to " + MAX_UNIVERSE;
        }
        if (selected.isEmpty() && mapping.isEmpty()) return "No universe selected";
        if (priority < 0 || priority > 1 || loss < 0 || loss > 2) return "Bad control policy";
        return null;
    }

    /**
     * 1.2.1: is a packet from {@code from} accepted by the source filter? The set IP itself, or its Art-Net alias: grandMA2 and
     * grandMA3 (and other Art-Net nodes) send from the Art-Net network 2.x.y.z (or 10.x.y.z) built on the PC's own address, so a PC
     * at 192.168.178.61 sends from 2.168.178.61 (players' report, 15 November: every packet "SOURCE FILTERED").
     */
    public boolean acceptsSource(String from) {
        if (anySource) return true;
        if (from == null) return false;
        if (sourceIp.equals(from)) return true;
        return artNetAlias(sourceIp, from) || artNetAlias(from, sourceIp);
    }

    /** {@code alias} = 2.x.y.z or 10.x.y.z with the same last three numbers as {@code ip}. */
    static boolean artNetAlias(String ip, String alias) {
        if (!ipv4(ip) || !ipv4(alias)) return false;
        String[] a = ip.trim().split("\\."), b = alias.trim().split("\\.");
        if (!b[0].equals("2") && !b[0].equals("10")) return false;
        return Integer.parseInt(a[1]) == Integer.parseInt(b[1]) && Integer.parseInt(a[2]) == Integer.parseInt(b[2])
            && Integer.parseInt(a[3]) == Integer.parseInt(b[3]);
    }

    /** Dotted IPv4 literal (no host name lookup: a name would block the server thread on DNS). */
    public static boolean ipv4(String s) {
        if (s == null) return false;
        String[] p = s.trim().split("\\.", -1);
        if (p.length != 4) return false;
        for (String x : p) {
            if (x.isEmpty() || x.length() > 3) return false;
            for (int i = 0; i < x.length(); i++) if (!Character.isDigit(x.charAt(i))) return false;
            if (Integer.parseInt(x) > 255) return false;
        }
        return true;
    }

    public static InetAddress address(String ipv4) throws java.net.UnknownHostException {
        String[] p = ipv4.trim().split("\\.");
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) b[i] = (byte) Integer.parseInt(p[i]);
        return Inet4Address.getByAddress(b);
    }

    public ArtNetSettings copy() {
        ArtNetSettings c = new ArtNetSettings();
        Bytes.Writer w = new Bytes.Writer();
        write(w);
        c.read(new Bytes.Reader(w.toBytes()));
        return c;
    }

    // ------------------------------------------------------------ network (console <-> server)
    public void write(Bytes.Writer w) {
        w.bool(enabled).str(bind).varInt(port).bool(anySource).str(sourceIp);
        int n = selected.cardinality();
        w.varInt(n);
        for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) w.varInt(i);
        w.varInt(mapping.size());
        for (Map.Entry<Integer, Integer> e : mapping.entrySet()) w.varInt(e.getKey()).varInt(e.getValue());
        w.varInt(priority).varInt(loss).bool(allowDedicated);
    }

    /** Reads what {@link #write} wrote; false (and this object unusable) when malformed. */
    public boolean read(Bytes.Reader r) {
        enabled = r.bool(); bind = r.str(64).trim(); port = r.varInt(); anySource = r.bool(); sourceIp = r.str(64).trim();
        selected.clear();
        int n = r.count(MAX_PORT_ADDRESS + 1);
        for (int i = 0; i < n && r.ok(); i++) {
            int pa = r.varInt();
            if (pa < 0 || pa > MAX_PORT_ADDRESS) return false;
            selected.set(pa);
        }
        mapping.clear();
        int m = r.count(1024);
        for (int i = 0; i < m && r.ok(); i++) {
            int pa = r.varInt(), u = r.varInt();
            if (pa < 0 || pa > MAX_PORT_ADDRESS || !validUniverse(u)) return false;
            mapping.put(pa, u);
        }
        priority = r.varInt(); loss = r.varInt(); allowDedicated = r.bool();
        return r.ok();
    }

    // ------------------------------------------------------------ file (config/concertstage-external.properties)
    /** Reads the Art-Net keys of the settings file (missing keys: defaults). A malformed value keeps its default. */
    public static ArtNetSettings load(Properties p) {
        ArtNetSettings s = new ArtNetSettings();
        s.enabled = !"false".equalsIgnoreCase(p.getProperty("artnet", "true").trim());
        String bind = p.getProperty("artnet_bind_address", p.getProperty("bind_address", "0.0.0.0")).trim();
        if (ipv4(bind)) s.bind = bind;
        try { s.port = Integer.parseInt(p.getProperty("artnet_port", String.valueOf(DEFAULT_PORT)).trim()); } catch (NumberFormatException e) { s.port = DEFAULT_PORT; }
        if (s.port < 1 || s.port > 65535) s.port = DEFAULT_PORT;
        String src = p.getProperty("artnet_source", "any").trim();
        s.anySource = !ipv4(src);
        s.sourceIp = s.anySource ? "" : src;
        String uni = p.getProperty("artnet_universes", "").trim();
        if (!uni.isEmpty()) {
            BitSet b = parseList(uni);
            if (b != null && !b.isEmpty()) { s.selected.clear(); s.selected.or(b); }
        }
        String map = p.getProperty("artnet_mapping", "").trim();
        if (!map.isEmpty()) {
            for (String part : map.split(",")) {
                String[] kv = part.trim().split("[>=]");
                if (kv.length != 2) continue;
                try {
                    int pa = Integer.parseInt(kv[0].trim()), u = Integer.parseInt(kv[1].trim());
                    if (pa >= 0 && pa <= MAX_PORT_ADDRESS && validUniverse(u)) s.mapping.put(pa, u);
                } catch (NumberFormatException ignored) { }
            }
        }
        s.priority = "artnet".equalsIgnoreCase(p.getProperty("artnet_priority_policy", "desk").trim()) ? PRIORITY_ARTNET : PRIORITY_DESK;
        String loss = p.getProperty("artnet_signal_loss", "hold").trim().toLowerCase(java.util.Locale.ROOT);
        s.loss = loss.equals("blackout") ? LOSS_BLACKOUT : loss.equals("return") ? LOSS_RETURN : LOSS_HOLD;
        s.allowDedicated = "true".equalsIgnoreCase(p.getProperty("allow_on_dedicated_server", "false").trim());
        return s;
    }

    /** Writes the Art-Net keys into {@code p} (other keys of the file are kept by the caller). */
    public void store(Properties p) {
        p.setProperty("artnet", String.valueOf(enabled));
        p.setProperty("artnet_bind_address", bind);
        p.setProperty("artnet_port", String.valueOf(port));
        p.setProperty("artnet_source", anySource ? "any" : sourceIp);
        p.setProperty("artnet_universes", list(selected));
        StringBuilder m = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : mapping.entrySet()) m.append(m.length() == 0 ? "" : ",").append(e.getKey()).append('>').append(e.getValue());
        p.setProperty("artnet_mapping", m.toString());
        p.setProperty("artnet_priority_policy", priority == PRIORITY_ARTNET ? "artnet" : "desk");
        p.setProperty("artnet_signal_loss", loss == LOSS_BLACKOUT ? "blackout" : loss == LOSS_RETURN ? "return" : "hold");
        p.setProperty("allow_on_dedicated_server", String.valueOf(allowDedicated));
        p.remove("artnet_universe_offset");                              // rev. 16h: replaced by the mapping (direct by default)
    }

    /** "0-3,7,16-31" of a set of Port-Addresses. */
    public static String list(BitSet b) {
        StringBuilder s = new StringBuilder();
        for (int i = b.nextSetBit(0); i >= 0; ) {
            int j = b.nextClearBit(i) - 1;
            if (s.length() > 0) s.append(',');
            s.append(i);
            if (j > i) s.append('-').append(j);
            i = b.nextSetBit(j + 1);
        }
        return s.toString();
    }

    /** Parses "0-3,7,16-31" (null when malformed). */
    public static BitSet parseList(String text) {
        BitSet b = new BitSet();
        for (String part : text.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                int dash = t.indexOf('-', 1);
                int a = Integer.parseInt(dash < 0 ? t : t.substring(0, dash).trim());
                int c = dash < 0 ? a : Integer.parseInt(t.substring(dash + 1).trim());
                if (a < 0 || c < a || c > MAX_PORT_ADDRESS) return null;
                b.set(a, c + 1);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return b;
    }
}
