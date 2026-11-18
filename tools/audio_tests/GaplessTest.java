import com.concertstage.audio.core.*;
import java.nio.file.*;

/** MP3 gapless (1.1.0): the LAME encoder delay and padding are removed, the MP3 lines up with its WAV source sample for sample. */
public class GaplessTest {
    static short[] all(Path f, String t) throws Exception {
        try (TrackDecoder d = AudioFiles.open(f, t)) {
            short[] o = new short[1 << 22], b = new short[8192]; int tot = 0, n;
            while ((n = d.read(b)) >= 0) { System.arraycopy(b, 0, o, tot * 2, n * 2); tot += n; }
            return java.util.Arrays.copyOf(o, tot * 2);
        }
    }
    public static void main(String[] a) throws Exception {
        Path dir = Path.of(a[0]);
        short[] m = all(dir.resolve("n.mp3"), "mp3"), w = all(dir.resolve("n.wav"), "wav");
        if (m.length != w.length) throw new AssertionError("length " + m.length / 2 + " != " + w.length / 2);
        int best = 0; double be = Double.MAX_VALUE;
        for (int k = -3000; k <= 3000; k++) {
            double e = 0;
            for (int i = 100000; i < 102000; i++) e += Math.abs(m[2 * i] - w[2 * (i + k)]);
            if (e < be) { be = e; best = k; }
        }
        if (best != 0) throw new AssertionError("offset " + best);
        long ms = AudioFiles.durationMs(dir.resolve("n.mp3"), "mp3");
        if (ms != 10000) throw new AssertionError("duration " + ms);
        System.out.println("GaplessTest PASS: MP3 length = WAV length (" + m.length / 2 + "), offset 0, duration " + ms + " ms");
    }
}
