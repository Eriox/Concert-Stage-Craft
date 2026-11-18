package com.concertstage.light.dmx;

import com.concertstage.light.core.Bytes;
import com.concertstage.light.core.Show;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * CSC recorded show file ({@code .cscrec}): a recorded DMX show (CSC RECORD EXTERNAL SHOW) — what a desk sent, over time, independently of the
 * desk's own show format. It holds the result of the programming (values over time), not the cues, presets or effects of the
 * original desk.
 *
 * <p>Layout, version 1:
 * <pre>
 *   "CSCSHOW\0"  8 bytes
 *   version      u8 (1)
 *   flags        u8 (bit 0: body is deflate-compressed)
 *   bodyLength   u32 big-endian (uncompressed)
 *   crc32        u32 big-endian of the uncompressed body
 *   body         (deflate)
 * body:
 *   metadata: name, created (epoch ms), source, notes, duration ms, track sha1, track name, music position at the start (ms, -1 none)
 *   patch:    fixtures (fid, type, name, universe, address, mode, x, y, z, yaw, mount), matrix universe / address / order
 *   universes: list of the CSC universe numbers recorded
 *   frames:   count, then for each: time (ms since the previous frame), universe index, kind (0 key = all 512 slots,
 *             1 delta = runs of changed slots: start, length, values)
 * </pre>
 * A key frame of each universe is stored every {@link #KEY_INTERVAL_MS} so seeking never replays the whole show. Files are checked on
 * reading (magic, version, size limits, CRC); a damaged file is refused, never half-loaded.
 */
public final class CscShowFile {
    public static final byte[] MAGIC = {'C', 'S', 'C', 'S', 'H', 'O', 'W', 0};
    /** 2 = rev. 19 (the Ultra Compact of the gobo fixtures gained strobe + gobo; files of version 1 keep the 4-channel layout). */
    public static final int VERSION = 7;          // 1.2.0: RGB in every mode (migrated below)
    // was 6:          // 1.2.0 beta 2b: pixel Full (migrated below); 1.2.0 beta 2: gobo rotation channel in Compact (migrated below); rev. 21: zone layouts of the PULSESTRIKE M / FLASHCORE FX; rev. 24: two-instance Compact (migrated below)
    public static final int KEY_INTERVAL_MS = 2000;
    public static final int MAX_BODY = 256 << 20, MAX_FRAMES = 20_000_000, MAX_UNIVERSES = 64;
    /** File extension. Not ".cscshow": since 1.2.0 that is the extension of the desk show files (cue lists, presets...), another format. */
    public static final String EXTENSION = ".cscrec";

    public String name = "Recorded show", source = "", notes = "";
    public long created;
    public long durationMs;
    /** Linked music when the show was recorded (to restart it in sync), or empty / -1. */
    public String trackSha1 = "", trackName = "";
    public long musicStartMs = -1;
    /** Patch of the desk when recorded (to check, or restore, the addresses the recording expects). */
    public final List<Show.Patch> patch = new ArrayList<>();
    public int matrixUniverse = 1, matrixAddress = 497, matrixOrder = 1;
    public int[] universes = new int[0];

    /** Frames in time order. {@code data}: 512 slots for a key frame, runs for a delta (see {@link #runs}). */
    public static final class Frame {
        public long timeMs;
        public int universe;
        public boolean key;
        public byte[] data;
    }

    public final List<Frame> frames = new ArrayList<>();

    // ------------------------------------------------------------ encode
    public byte[] toBytes() {
        Bytes.Writer w = new Bytes.Writer();
        w.str(name).varLong(created).str(source).str(notes).varLong(durationMs).str(trackSha1).str(trackName).sLong(musicStartMs);
        w.varInt(patch.size());
        for (Show.Patch p : patch) {
            w.varInt(p.fid).str(p.type).str(p.name).varInt(p.universe).varInt(p.address).varInt(p.dmxMode).sInt(p.x).sInt(p.y).sInt(p.z)
                .f32(p.yaw).varInt(p.mount);
        }
        w.varInt(matrixUniverse).varInt(matrixAddress).varInt(matrixOrder);
        w.varInt(universes.length);
        for (int u : universes) w.varInt(u);
        w.varInt(frames.size());
        long prev = 0;
        for (Frame f : frames) {
            w.varLong(Math.max(0, f.timeMs - prev));
            prev = Math.max(prev, f.timeMs);
            w.varInt(indexOf(f.universe)).u8(f.key ? 0 : 1);
            if (f.key) {
                byte[] d = new byte[512];
                System.arraycopy(f.data, 0, d, 0, Math.min(512, f.data.length));
                for (byte b : d) w.u8(b);
            } else {
                w.varInt(f.data.length);
                for (byte b : f.data) w.u8(b);
            }
        }
        byte[] body = w.toBytes();
        CRC32 crc = new CRC32();
        crc.update(body);
        Deflater d = new Deflater(6);
        d.setInput(body);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length / 4 + 64);
        out.writeBytes(MAGIC);
        out.write(VERSION);
        out.write(1);
        writeInt(out, body.length);
        writeInt(out, (int) crc.getValue());
        byte[] buf = new byte[65536];
        while (!d.finished()) {
            int n = d.deflate(buf);
            out.write(buf, 0, n);
        }
        d.end();
        return out.toByteArray();
    }

    private int indexOf(int universe) {
        for (int i = 0; i < universes.length; i++) if (universes[i] == universe) return i;
        throw new IllegalStateException("universe " + universe + " not declared");
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v >>> 24); o.write(v >>> 16); o.write(v >>> 8); o.write(v);
    }

    // ------------------------------------------------------------ decode
    /** Thrown with a readable reason when a file is not a valid CSC show. */
    public static final class BadFile extends Exception {
        public BadFile(String m) { super(m); }
    }

    public static CscShowFile fromBytes(byte[] file) throws BadFile {
        if (file == null || file.length < 18) throw new BadFile("too short to be a .cscshow file");
        if (!Arrays.equals(Arrays.copyOf(file, 8), MAGIC)) throw new BadFile("not a .cscshow file");
        int version = file[8] & 255, flags = file[9] & 255;
        if (version < 1 || version > VERSION) throw new BadFile("made by a newer CSC (format " + version + ")");
        int len = readInt(file, 10), crc = readInt(file, 14);
        if (len < 0 || len > MAX_BODY) throw new BadFile("size out of range");
        byte[] body;
        if ((flags & 1) != 0) {
            Inflater inf = new Inflater();
            inf.setInput(file, 18, file.length - 18);
            body = new byte[len];
            try {
                int n = 0;
                while (n < len && !inf.finished()) {
                    int k = inf.inflate(body, n, len - n);
                    if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                    n += k;
                }
                if (n != len) throw new BadFile("truncated file");
            } catch (DataFormatException e) {
                throw new BadFile("damaged compressed data");
            } finally {
                inf.end();
            }
        } else {
            if (file.length - 18 != len) throw new BadFile("truncated file");
            body = Arrays.copyOfRange(file, 18, file.length);
        }
        CRC32 c = new CRC32();
        c.update(body);
        if ((int) c.getValue() != crc) throw new BadFile("checksum mismatch (damaged file)");
        Bytes.Reader r = new Bytes.Reader(body);
        CscShowFile f = new CscShowFile();
        f.name = r.str(256); f.created = r.varLong(); f.source = r.str(512); f.notes = r.str(4096); f.durationMs = r.varLong();
        f.trackSha1 = r.str(64); f.trackName = r.str(1024); f.musicStartMs = r.sLong();
        int n = r.count(Show.MAX_FIXTURES);
        for (int i = 0; i < n && r.ok(); i++) {
            Show.Patch p = new Show.Patch();
            p.fid = r.varInt(); p.type = r.str(64); p.name = r.str(256); p.universe = r.varInt(); p.address = r.varInt();
            p.dmxMode = Math.max(0, Math.min(DmxMode.LAST, r.varInt()));
            if (version < 2 && p.dmxMode == DmxMode.ULTRA && p.profile() != null && DmxMode.hasGoboWheel(p.profile())) p.dmxMode = DmxMode.ULTRA_V1;
            // rev. 21: a recording made before keeps the Full / Compact layouts its universes were recorded with
            if (version < 3) p.dmxMode = DmxMode.migrateRev21(p.profile(), p.dmxMode);
            if (version < 4) p.dmxMode = DmxMode.migrateRev24(p.profile(), p.dmxMode);
            if (version < 5) p.dmxMode = DmxMode.migrateGoboRot(p.profile(), p.dmxMode);   // 1.2.0 beta 2
            if (version < 6) p.dmxMode = DmxMode.migratePixelFull(p.profile(), p.dmxMode);   // 1.2.0 beta 2b
            if (version < 7) p.dmxMode = DmxMode.migrateRgb(p.profile(), p.dmxMode);       // 1.2.0
            p.x = r.sInt(); p.y = r.sInt(); p.z = r.sInt(); p.yaw = r.f32(); p.mount = r.varInt();
            f.patch.add(p);
        }
        f.matrixUniverse = r.varInt(); f.matrixAddress = r.varInt(); f.matrixOrder = r.varInt();
        int nu = r.count(MAX_UNIVERSES);
        f.universes = new int[nu];
        for (int i = 0; i < nu; i++) f.universes[i] = r.varInt();
        int nf = r.count(MAX_FRAMES);
        long t = 0;
        for (int i = 0; i < nf && r.ok(); i++) {
            Frame fr = new Frame();
            t += r.varLong();
            fr.timeMs = t;
            int ui = r.varInt();
            if (ui < 0 || ui >= nu) throw new BadFile("frame " + i + ": bad universe");
            fr.universe = f.universes[ui];
            int kind = r.u8();
            if (kind == 0) {
                fr.key = true;
                fr.data = new byte[512];
                for (int k = 0; k < 512; k++) fr.data[k] = (byte) r.u8();
            } else if (kind == 1) {
                int dl = r.varInt();
                if (dl < 0 || dl > 512 * 3 + 16) throw new BadFile("frame " + i + ": bad delta");
                fr.data = new byte[dl];
                for (int k = 0; k < dl; k++) fr.data[k] = (byte) r.u8();
                if (!validRuns(fr.data)) throw new BadFile("frame " + i + ": bad delta runs");
            } else throw new BadFile("frame " + i + ": unknown kind");
            f.frames.add(fr);
        }
        if (!r.ok()) throw new BadFile("truncated body");
        return f;
    }

    private static int readInt(byte[] b, int o) {
        return (b[o] & 255) << 24 | (b[o + 1] & 255) << 16 | (b[o + 2] & 255) << 8 | (b[o + 3] & 255);
    }

    // ------------------------------------------------------------ delta runs
    /**
     * Runs of changed slots between two universes: a list of (start u16, length u16, values), packed. Two runs closer than 4 slots are
     * joined (fewer headers).
     */
    public static byte[] runs(byte[] prev, byte[] cur) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int i = 0;
        while (i < 512) {
            if (prev[i] == cur[i]) { i++; continue; }
            int start = i, end = i, gap = 0;
            for (int j = i + 1; j < 512; j++) {
                if (prev[j] != cur[j]) { end = j; gap = 0; }
                else if (++gap > 4) break;
            }
            int len = end - start + 1;
            o.write(start >> 8); o.write(start); o.write(len >> 8); o.write(len);
            o.write(cur, start, len);
            i = end + 1;
        }
        return o.toByteArray();
    }

    /** Applies runs to a universe. */
    public static void apply(byte[] runs, byte[] target) {
        int p = 0;
        while (p + 4 <= runs.length) {
            int start = (runs[p] & 255) << 8 | (runs[p + 1] & 255), len = (runs[p + 2] & 255) << 8 | (runs[p + 3] & 255);
            p += 4;
            if (start + len > 512 || p + len > runs.length) return;
            System.arraycopy(runs, p, target, start, len);
            p += len;
        }
    }

    static boolean validRuns(byte[] runs) {
        int p = 0;
        while (p < runs.length) {
            if (p + 4 > runs.length) return false;
            int start = (runs[p] & 255) << 8 | (runs[p + 1] & 255), len = (runs[p + 2] & 255) << 8 | (runs[p + 3] & 255);
            p += 4;
            if (len == 0 || start + len > 512 || p + len > runs.length) return false;
            p += len;
        }
        return true;
    }
}
