package com.concertstage.light.dmx;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Decoding (and, for the tests and the loopback test sender, encoding) of the two DMX-over-Ethernet protocols CSC receives:
 * <ul>
 * <li><b>Art-Net</b> (Art-Net 4, Artistic Licence): ArtDmx packets on UDP 6454. Port-Address = Net (7 bits) · Sub-Net · Universe
 * (15 bits). ArtPoll is recognised (so it is not counted as an error) but CSC does not answer it.</li>
 * <li><b>sACN</b> (ANSI E1.31-2018): data packets on UDP 5568, multicast 239.255.{hi}.{lo} per universe. Priority, preview data and
 * the stream-terminated option are read; synchronisation packets are ignored (data applies on arrival).</li>
 * </ul>
 * Every field the receiver relies on is checked; anything malformed is rejected ({@link #parse} returns null and says why).
 */
public final class DmxPackets {
    private DmxPackets() {}

    public static final int ARTNET_PORT = 6454, SACN_PORT = 5568;
    public static final int PROTO_ARTNET = 1, PROTO_SACN = 2;
    private static final byte[] ARTNET_ID = "Art-Net\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ACN_ID = {0x41, 0x53, 0x43, 0x2d, 0x45, 0x31, 0x2e, 0x31, 0x37, 0x00, 0x00, 0x00};
    public static final int OP_DMX = 0x5000, OP_POLL = 0x2000;

    /** One received universe. {@code universe}: Art-Net Port-Address or sACN universe number; {@code data}: 512 slots. */
    public record Frame(int protocol, int universe, int priority, int sequence, boolean terminated, String sourceId, String sourceName,
                        byte[] data, int length) {}

    /** Last reason a packet was rejected (monitoring only; not thread confined, informative). */
    public static volatile String lastError = "";

    /** A received datagram, or null (not DMX data, malformed: see {@link #lastError}). */
    public static Frame parse(byte[] b, int len, String from) {
        if (len >= 10 && startsWith(b, ARTNET_ID)) return artnet(b, len, from);
        if (len >= 126 && b[0] == 0 && b[1] == 0x10 && Arrays.equals(Arrays.copyOfRange(b, 4, 16), ACN_ID)) return sacn(b, len);
        lastError = "unknown packet";
        return null;
    }

    private static Frame artnet(byte[] b, int len, String from) {
        int op = (b[8] & 255) | (b[9] & 255) << 8;
        if (op == OP_POLL) { lastError = ""; return null; }
        if (op != OP_DMX) { lastError = "Art-Net op 0x" + Integer.toHexString(op) + " ignored"; return null; }
        if (len < 20) { lastError = "Art-Net: short ArtDmx"; return null; }
        int ver = (b[10] & 255) << 8 | (b[11] & 255);
        if (ver < 14) { lastError = "Art-Net: protocol version " + ver; return null; }
        int seq = b[12] & 255;
        int port = (b[14] & 255) | (b[15] & 0x7F) << 8;
        int n = (b[16] & 255) << 8 | (b[17] & 255);
        if (n < 2 || n > 512 || 18 + n > len) { lastError = "Art-Net: bad length " + n; return null; }
        byte[] d = new byte[512];
        System.arraycopy(b, 18, d, 0, n);
        lastError = "";
        return new Frame(PROTO_ARTNET, port, -1, seq, false, "artnet/" + from, "Art-Net " + from, d, n);
    }

    private static Frame sacn(byte[] b, int len) {
        if (u32(b, 18) != 0x00000004) { lastError = "sACN: root vector"; return null; }
        int rootLen = u16(b, 16) & 0x0FFF;
        if (rootLen != len - 16) { lastError = "sACN: root length"; return null; }
        int frameVector = u32(b, 40);
        if (frameVector == 0x00000001) { lastError = ""; return null; }     // synchronisation packet
        if (frameVector != 0x00000002) { lastError = "sACN: framing vector"; return null; }
        if ((u16(b, 38) & 0x0FFF) != len - 38) { lastError = "sACN: framing length"; return null; }
        String name = new String(b, 44, 64, StandardCharsets.UTF_8);
        int z = name.indexOf('\0');
        if (z >= 0) name = name.substring(0, z);
        int priority = b[108] & 255;
        if (priority > 200) { lastError = "sACN: priority " + priority; return null; }
        int seq = b[111] & 255;
        int options = b[112] & 255;
        int universe = u16(b, 113);
        if (universe < 1 || universe > 63999) { lastError = "sACN: universe " + universe; return null; }
        if ((u16(b, 115) & 0x0FFF) != len - 115 || (b[117] & 255) != 0x02 || (b[118] & 255) != 0xA1
            || u16(b, 119) != 0 || u16(b, 121) != 1) { lastError = "sACN: DMP layer"; return null; }
        int count = u16(b, 123);
        if (count < 1 || count > 513 || 125 + count > len) { lastError = "sACN: property count " + count; return null; }
        if ((options & 0x80) != 0) { lastError = ""; return null; }       // preview data: not for live output
        boolean terminated = (options & 0x40) != 0;
        int start = b[125] & 255;
        if (start != 0 && !terminated) { lastError = ""; return null; }   // alternate start code (RDM, text...): not dimmer data
        byte[] d = new byte[512];
        System.arraycopy(b, 126, d, 0, count - 1);
        StringBuilder cid = new StringBuilder("sacn/");
        for (int i = 22; i < 38; i++) cid.append(String.format("%02x", b[i] & 255));
        lastError = "";
        return new Frame(PROTO_SACN, universe, priority, seq, terminated, cid.toString(), name, d, count - 1);
    }

    private static boolean startsWith(byte[] b, byte[] p) {
        for (int i = 0; i < p.length; i++) if (b[i] != p[i]) return false;
        return true;
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 255) << 8 | (b[o + 1] & 255);
    }

    private static int u32(byte[] b, int o) {
        return (b[o] & 255) << 24 | (b[o + 1] & 255) << 16 | (b[o + 2] & 255) << 8 | (b[o + 3] & 255);
    }

    // ------------------------------------------------------------ encoding (tests, test sender)
    public static byte[] artDmx(int portAddress, int sequence, byte[] data, int n) {
        n = Math.max(2, Math.min(512, n + (n & 1)));
        byte[] b = new byte[18 + n];
        System.arraycopy(ARTNET_ID, 0, b, 0, 8);
        b[8] = 0x00; b[9] = 0x50; b[10] = 0; b[11] = 14;
        b[12] = (byte) sequence; b[13] = 0;
        b[14] = (byte) (portAddress & 255); b[15] = (byte) ((portAddress >> 8) & 0x7F);
        b[16] = (byte) (n >> 8); b[17] = (byte) n;
        System.arraycopy(data, 0, b, 18, Math.min(n, data.length));
        return b;
    }

    public static byte[] sacnData(int universe, int priority, int sequence, boolean terminated, byte[] cid16, String source, byte[] data, int n) {
        n = Math.max(1, Math.min(512, n));
        int len = 126 + n;
        byte[] b = new byte[len];
        b[1] = 0x10;
        System.arraycopy(ACN_ID, 0, b, 4, 12);
        put16(b, 16, 0x7000 | (len - 16));
        put32(b, 18, 4);
        System.arraycopy(cid16, 0, b, 22, 16);
        put16(b, 38, 0x7000 | (len - 38));
        put32(b, 40, 2);
        byte[] nm = source.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nm, 0, b, 44, Math.min(63, nm.length));
        b[108] = (byte) priority;
        b[111] = (byte) sequence;
        b[112] = (byte) (terminated ? 0x40 : 0);
        put16(b, 113, universe);
        put16(b, 115, 0x7000 | (len - 115));
        b[117] = 0x02; b[118] = (byte) 0xA1;
        put16(b, 119, 0); put16(b, 121, 1); put16(b, 123, n + 1);
        b[125] = 0;
        System.arraycopy(data, 0, b, 126, Math.min(n, data.length));
        return b;
    }

    private static void put16(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 8); b[o + 1] = (byte) v;
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 24); b[o + 1] = (byte) (v >> 16); b[o + 2] = (byte) (v >> 8); b[o + 3] = (byte) v;
    }

    /** Multicast group of an sACN universe. */
    public static String sacnGroup(int universe) {
        return "239.255." + ((universe >> 8) & 255) + "." + (universe & 255);
    }
}
