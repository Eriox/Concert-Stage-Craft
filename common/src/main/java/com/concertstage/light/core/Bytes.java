package com.concertstage.light.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Compact binary writer / reader used by the show format, the live state and the lighting commands (pure Java: the same bytes travel
 * in the network messages and in the block entity NBT, and the tests read them without Minecraft). Variable-length ints, floats,
 * UTF-8 strings with a length cap. The reader never throws past the end: it returns zeros and remembers the error
 * ({@link Reader#ok()}), so a damaged message is rejected as a whole by the caller.
 */
public final class Bytes {
    private Bytes() {}

    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(256);

        public Writer u8(int v) { out.write(v & 255); return this; }

        public Writer bool(boolean b) { return u8(b ? 1 : 0); }

        public Writer varInt(int v) {
            while ((v & ~0x7F) != 0) { out.write((v & 0x7F) | 0x80); v >>>= 7; }
            out.write(v);
            return this;
        }

        /** Zig-zag: small negative numbers stay small. */
        public Writer sInt(int v) { return varInt((v << 1) ^ (v >> 31)); }

        public Writer varLong(long v) {
            while ((v & ~0x7FL) != 0) { out.write((int) (v & 0x7F) | 0x80); v >>>= 7; }
            out.write((int) v);
            return this;
        }

        public Writer sLong(long v) { return varLong((v << 1) ^ (v >> 63)); }

        public Writer i32(int b) {
            out.write(b); out.write(b >>> 8); out.write(b >>> 16); out.write(b >>> 24);
            return this;
        }

        public Writer f32(float f) {
            int b = Float.floatToIntBits(f);
            out.write(b); out.write(b >>> 8); out.write(b >>> 16); out.write(b >>> 24);
            return this;
        }

        /** A value 0..1 (or NaN = unset) in two bytes: 1/65534 resolution. */
        public Writer unit(float f) {
            int v = Float.isNaN(f) ? 0xFFFF : Math.max(0, Math.min(65534, Math.round(f * 65534f)));
            out.write(v); out.write(v >>> 8);
            return this;
        }

        public Writer str(String s) {
            byte[] b = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
            varInt(b.length);
            out.write(b, 0, b.length);
            return this;
        }

        public Writer bytes(byte[] b) {
            varInt(b.length);
            out.write(b, 0, b.length);
            return this;
        }

        public byte[] toBytes() { return out.toByteArray(); }

        public int size() { return out.size(); }
    }

    public static final class Reader {
        private final byte[] b;
        private int p;
        private boolean ok = true;

        public Reader(byte[] b) { this.b = b == null ? new byte[0] : b; }

        public boolean ok() { return ok; }

        public boolean more() { return ok && p < b.length; }

        public int remaining() { return b.length - p; }

        private int next() {
            if (p >= b.length) { ok = false; return 0; }
            return b[p++] & 255;
        }

        public int u8() { return next(); }

        public boolean bool() { return next() != 0; }

        public int varInt() {
            int v = 0, shift = 0;
            while (true) {
                int c = next();
                v |= (c & 0x7F) << shift;
                if ((c & 0x80) == 0 || !ok) return v;
                shift += 7;
                if (shift > 28) { ok = false; return 0; }
            }
        }

        public int sInt() { int v = varInt(); return (v >>> 1) ^ -(v & 1); }

        public long varLong() {
            long v = 0;
            int shift = 0;
            while (true) {
                int c = next();
                v |= (long) (c & 0x7F) << shift;
                if ((c & 0x80) == 0 || !ok) return v;
                shift += 7;
                if (shift > 63) { ok = false; return 0; }
            }
        }

        public long sLong() { long v = varLong(); return (v >>> 1) ^ -(v & 1); }

        public int i32() {
            return next() | next() << 8 | next() << 16 | next() << 24;
        }

        public float f32() {
            int v = next() | next() << 8 | next() << 16 | next() << 24;
            float f = Float.intBitsToFloat(v);
            return Float.isFinite(f) ? f : 0f;
        }

        public float unit() {
            int v = next() | next() << 8;
            return v == 0xFFFF ? Float.NaN : v / 65534f;
        }

        /** String of at most {@code max} bytes (longer ones mark the reader as failed). */
        public String str(int max) {
            int n = varInt();
            if (n < 0 || n > max || n > remaining()) { ok = false; return ""; }
            String s = new String(b, p, n, StandardCharsets.UTF_8);
            p += n;
            return s;
        }

        public byte[] bytes(int max) {
            int n = varInt();
            if (n < 0 || n > max || n > remaining()) { ok = false; return new byte[0]; }
            byte[] out = new byte[n];
            System.arraycopy(b, p, out, 0, n);
            p += n;
            return out;
        }

        /** A count that must stay below {@code max} (protects the loops of the decoders). */
        public int count(int max) {
            int n = varInt();
            if (n < 0 || n > max) { ok = false; return 0; }
            return n;
        }
    }
}
