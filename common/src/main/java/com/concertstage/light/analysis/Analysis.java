package com.concertstage.light.analysis;

import com.concertstage.light.core.Bytes;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of the analysis of one track: frame features (energy per band, onset strength), tempo and beat grid, bars, and the sections
 * with their probable role and a confidence. Compact and versioned: cached next to the audio library so a track is analysed once.
 */
public final class Analysis {
    public static final int FORMAT = 1;
    public static final String[] KINDS = {"Intro", "Verse", "Build", "Chorus", "Drop", "Break", "Outro", "Silence"};
    public static final int INTRO = 0, VERSE = 1, BUILD = 2, CHORUS = 3, DROP = 4, BREAK = 5, OUTRO = 6, SILENCE = 7;

    public String sha1 = "";
    public long durationMs;
    /** Milliseconds between two frames of the feature arrays. */
    public float hopMs;
    /** Per frame, normalised 0..1 to the loudest frame of the track. */
    public float[] rms = new float[0], low = new float[0], mid = new float[0], high = new float[0], onset = new float[0];
    public float bpm;
    /** Confidence of the tempo, 0..1 (strength of the autocorrelation peak). */
    public float tempoConfidence;
    public long firstBeatMs;
    public int beatsPerBar = 4;
    /** Index (in beats) of the first downbeat. */
    public int downbeatPhase;
    public final List<Section> sections = new ArrayList<>();

    public static final class Section {
        public long startMs, endMs;
        public int kind;
        /** Sections with the same motif sound alike (repetition of a chorus, of a verse). */
        public int motif;
        /** Occurrence of the motif (0 = first time it is heard). */
        public int occurrence;
        public float energy, lowRatio, slope, confidence;

        public String label() {
            return KINDS[Math.max(0, Math.min(KINDS.length - 1, kind))] + " " + (char) ('A' + Math.min(25, motif)) + (occurrence + 1);
        }
    }

    public double beatMs() {
        return bpm > 0 ? 60000.0 / bpm : 500.0;
    }

    /** Time of beat {@code k} (0 = first beat). */
    public long beat(long k) {
        return firstBeatMs + Math.round(k * beatMs());
    }

    /** Whether beat {@code k} starts a bar. */
    public boolean downbeat(long k) {
        return Math.floorMod(k - downbeatPhase, beatsPerBar) == 0;
    }

    /** Feature value at a time (nearest frame). */
    public float at(float[] f, long ms) {
        if (f.length == 0) return 0f;
        int i = (int) Math.round(ms / Math.max(1e-3, hopMs));
        return f[Math.max(0, Math.min(f.length - 1, i))];
    }

    /** Section at a time, or null. */
    public Section sectionAt(long ms) {
        for (Section s : sections) if (ms >= s.startMs && ms < s.endMs) return s;
        return null;
    }

    // ------------------------------------------------------------ codec (frame features stored at 8 bits)
    public byte[] toBytes() {
        Bytes.Writer w = new Bytes.Writer();
        w.i32(0x43534141).varInt(FORMAT).str(sha1).varLong(durationMs).f32(hopMs).f32(bpm).f32(tempoConfidence).varLong(firstBeatMs)
            .varInt(beatsPerBar).varInt(downbeatPhase);
        for (float[] f : new float[][] {rms, low, mid, high, onset}) {
            w.varInt(f.length);
            for (float v : f) w.u8(Math.round(Math.max(0, Math.min(1, v)) * 255));
        }
        w.varInt(sections.size());
        for (Section s : sections) {
            w.varLong(s.startMs).varLong(s.endMs).varInt(s.kind).varInt(s.motif).varInt(s.occurrence).unit(s.energy).unit(s.lowRatio)
                .f32(s.slope).unit(s.confidence);
        }
        return w.toBytes();
    }

    public static Analysis fromBytes(byte[] b) {
        if (b == null) return null;
        Bytes.Reader r = new Bytes.Reader(b);
        if (r.i32() != 0x43534141 || r.varInt() != FORMAT) return null;
        Analysis a = new Analysis();
        a.sha1 = r.str(64); a.durationMs = r.varLong(); a.hopMs = r.f32(); a.bpm = r.f32(); a.tempoConfidence = r.f32(); a.firstBeatMs = r.varLong();
        a.beatsPerBar = Math.max(1, r.varInt()); a.downbeatPhase = r.varInt();
        float[][] arrays = new float[5][];
        for (int k = 0; k < 5; k++) {
            int n = r.count(1 << 20);
            float[] f = new float[n];
            for (int i = 0; i < n; i++) f[i] = r.u8() / 255f;
            arrays[k] = f;
        }
        a.rms = arrays[0]; a.low = arrays[1]; a.mid = arrays[2]; a.high = arrays[3]; a.onset = arrays[4];
        int n = r.count(4096);
        for (int i = 0; i < n && r.ok(); i++) {
            Section s = new Section();
            s.startMs = r.varLong(); s.endMs = r.varLong(); s.kind = r.varInt(); s.motif = r.varInt(); s.occurrence = r.varInt();
            s.energy = r.unit(); s.lowRatio = r.unit(); s.slope = r.f32(); s.confidence = r.unit();
            a.sections.add(s);
        }
        return r.ok() ? a : null;
    }
}
