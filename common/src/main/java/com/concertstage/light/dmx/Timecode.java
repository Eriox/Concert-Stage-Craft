package com.concertstage.light.dmx;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Synchronisation inputs of the show player: MIDI Timecode (quarter frames and full frames) and OSC commands. Pure decoders; the
 * sockets and MIDI devices are opened by the server ({@code ExternalControl}).
 */
public final class Timecode {
    private Timecode() {}

    /** Frames per second of the MTC rate codes 0..3. 29.97 drop frame counts as 30 here (no drop-frame correction). */
    public static final double[] MTC_FPS = {24, 25, 29.97, 30};

    /**
     * MIDI Timecode decoder. Feed it every MIDI message ({@link #message}); {@link #ms} is the last complete position. Quarter frames
     * give a full time every 8 messages (two frames), so the position is advanced by those two frames (MTC convention) when piece 7
     * arrives.
     */
    public static final class Mtc {
        private final int[] pieces = new int[8];
        private int got;
        private long ms = -1, updatedNanos;
        private int rate = 1;
        private boolean forward = true;
        private int lastPiece = -1;

        /** Returns true when a new position is known. */
        public boolean message(byte[] m, int len, long nowNanos) {
            if (len >= 2 && (m[0] & 255) == 0xF1) {
                int d = m[1] & 255, piece = d >> 4, val = d & 15;
                if (lastPiece >= 0) forward = piece != (lastPiece + 7) % 8;       // pieces counting down: the timecode runs backwards
                lastPiece = piece;
                pieces[piece] = val;
                got |= 1 << piece;
                if (piece == 7 && got == 0xFF) {
                    int frames = pieces[0] | pieces[1] << 4, secs = pieces[2] | pieces[3] << 4, mins = pieces[4] | pieces[5] << 4;
                    int hours = pieces[6] | (pieces[7] & 1) << 4;
                    rate = (pieces[7] >> 1) & 3;
                    long t = position(hours, mins, secs, frames + 2, rate);
                    got = 0;
                    ms = t;
                    updatedNanos = nowNanos;
                    return true;
                }
                return false;
            }
            // full frame: F0 7F <device> 01 01 hh mm ss ff F7
            if (len >= 10 && (m[0] & 255) == 0xF0 && (m[1] & 255) == 0x7F && (m[3] & 255) == 0x01 && (m[4] & 255) == 0x01) {
                int hh = m[5] & 255;
                rate = (hh >> 5) & 3;
                ms = position(hh & 31, m[6] & 63, m[7] & 63, m[8] & 31, rate);
                got = 0;
                updatedNanos = nowNanos;
                return true;
            }
            return false;
        }

        static long position(int h, int m, int s, int f, int rate) {
            return Math.round(((h * 60L + m) * 60L + s) * 1000.0 + f * 1000.0 / MTC_FPS[rate]);
        }

        /** Last position (ms), -1 if none yet. */
        public long ms() {
            return ms;
        }

        /** Position extrapolated to {@code nowNanos} (running timecode), or the last one when it stopped arriving (over 200 ms). */
        public long ms(long nowNanos) {
            if (ms < 0) return -1;
            long dt = (nowNanos - updatedNanos) / 1_000_000;
            return dt > 200 ? ms : ms + Math.max(0, dt);
        }

        public boolean running(long nowNanos) {
            return ms >= 0 && (nowNanos - updatedNanos) / 1_000_000 <= 200;
        }

        public double fps() {
            return MTC_FPS[rate];
        }

        public boolean forward() {
            return forward;
        }
    }

    /** An OSC message: address and arguments (Integer, Float, String). */
    public record OscMessage(String address, List<Object> args) {
        public float number(int i, float def) {
            if (i >= args.size()) return def;
            Object o = args.get(i);
            if (o instanceof Number n) return n.floatValue();
            if (o instanceof String s) try { return Float.parseFloat(s.trim()); } catch (NumberFormatException e) { return def; }
            return def;
        }

        public String text(int i) {
            return i < args.size() ? String.valueOf(args.get(i)) : "";
        }
    }

    /** OSC 1.0 message or bundle (its messages); unsupported types end the arguments. Null if malformed. */
    public static List<OscMessage> osc(byte[] b, int len) {
        List<OscMessage> out = new ArrayList<>();
        try {
            parse(b, 0, len, out, 0);
        } catch (RuntimeException e) {
            return null;
        }
        return out;
    }

    private static void parse(byte[] b, int off, int end, List<OscMessage> out, int depth) {
        if (depth > 4 || off >= end) return;
        int[] p = {off};
        String addr = oscString(b, p, end);
        if (addr.equals("#bundle")) {
            p[0] += 8;                                                     // time tag
            while (p[0] + 4 <= end) {
                int size = i32(b, p[0]);
                p[0] += 4;
                if (size <= 0 || p[0] + size > end) throw new IllegalArgumentException("bundle element");
                parse(b, p[0], p[0] + size, out, depth + 1);
                p[0] += size;
            }
            return;
        }
        if (!addr.startsWith("/")) throw new IllegalArgumentException("address");
        List<Object> args = new ArrayList<>();
        if (p[0] < end && b[p[0]] == ',') {
            String tags = oscString(b, p, end);
            for (int i = 1; i < tags.length(); i++) {
                char t = tags.charAt(i);
                if (t == 'i') { args.add(i32(b, p[0])); p[0] += 4; }
                else if (t == 'f') { args.add(Float.intBitsToFloat(i32(b, p[0]))); p[0] += 4; }
                else if (t == 's') args.add(oscString(b, p, end));
                else if (t == 'T') args.add(1);
                else if (t == 'F') args.add(0);
                else break;
            }
        }
        out.add(new OscMessage(addr, args));
    }

    private static String oscString(byte[] b, int[] p, int end) {
        int s = p[0], e = s;
        while (e < end && b[e] != 0) e++;
        if (e >= end) throw new IllegalArgumentException("string");
        String r = new String(b, s, e - s, StandardCharsets.UTF_8);
        p[0] = (e + 4) & ~3;
        return r;
    }

    private static int i32(byte[] b, int o) {
        return (b[o] & 255) << 24 | (b[o + 1] & 255) << 16 | (b[o + 2] & 255) << 8 | (b[o + 3] & 255);
    }

    /** Builds an OSC message (tests, documentation examples). Arguments: Integer, Float or String. */
    public static byte[] oscMessage(String address, Object... args) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        pad(o, address.getBytes(StandardCharsets.UTF_8));
        StringBuilder tags = new StringBuilder(",");
        for (Object a : args) tags.append(a instanceof Integer ? 'i' : a instanceof Float ? 'f' : 's');
        pad(o, tags.toString().getBytes(StandardCharsets.UTF_8));
        for (Object a : args) {
            if (a instanceof Integer i) put(o, i);
            else if (a instanceof Float f) put(o, Float.floatToIntBits(f));
            else pad(o, String.valueOf(a).getBytes(StandardCharsets.UTF_8));
        }
        return o.toByteArray();
    }

    private static void pad(java.io.ByteArrayOutputStream o, byte[] s) {
        o.writeBytes(s);
        int n = 4 - (s.length % 4);
        for (int i = 0; i < n; i++) o.write(0);
    }

    private static void put(java.io.ByteArrayOutputStream o, int v) {
        o.write(v >>> 24); o.write(v >>> 16); o.write(v >>> 8); o.write(v);
    }
}
