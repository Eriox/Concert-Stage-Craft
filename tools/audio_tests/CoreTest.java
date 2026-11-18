import com.concertstage.audio.core.*;
import java.nio.file.*;
public class CoreTest {
  static short[] all(Path p, String t, long max) throws Exception {
    try (TrackDecoder d = AudioFiles.open(p, t)) {
      java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
      short[] buf = new short[8192]; long tot = 0; java.util.ArrayList<short[]> parts = new java.util.ArrayList<>();
      int n; while ((n = d.read(buf)) >= 0 && tot < max) { short[] c = java.util.Arrays.copyOf(buf, n * 2); parts.add(c); tot += n; }
      short[] out = new short[(int) tot * 2]; int o = 0; for (short[] c : parts) { System.arraycopy(c, 0, out, o, c.length); o += c.length; }
      return out;
    }
  }
  public static void main(String[] a) throws Exception {
    Path dir = Path.of(a[0]);
    for (String f : new String[]{"t.mp3","tm.mp3","t.wav","t24.wav"}) {
      Path p = dir.resolve(f); String t = AudioFiles.detect(p);
      System.out.println(f + " type=" + t + " dur=" + AudioFiles.durationMs(p, t) + " decodes=" + AudioFiles.decodes(p, t));
    }
    // full decode
    short[] seq = all(dir.resolve("t.mp3"), "mp3", Long.MAX_VALUE);
    System.out.println("mp3 frames=" + seq.length / 2 + " (" + seq.length / 2 / 44.1 + " ms)");
    // skip accuracy
    for (long target : new long[]{441000, 100, 5000, 300000}) {
      try (TrackDecoder d = AudioFiles.open(dir.resolve("t.mp3"), "mp3")) {
        long s = d.skip(target); short[] b = new short[2000]; int n = d.read(b);
        int max = 0; for (int i = 0; i < n * 2; i++) max = Math.max(max, Math.abs(b[i] - seq[(int) target * 2 + i]));
        System.out.println("skip " + target + " -> " + s + " read " + n + " maxdiff " + max);
      }
    }
    short[] w = all(dir.resolve("t24.wav"), "wav", Long.MAX_VALUE);
    System.out.println("wav24 frames=" + w.length / 2 + " L==R " + (w[1000] == w[1001]));
    // TrackPcm
    TrackPcm pcm = new TrackPcm(dir.resolve("t.mp3"), "mp3", 5000);
    System.out.println("ready " + pcm.awaitReady(3000) + " rate " + pcm.sampleRate());
    TrackPcm.Reader r1 = pcm.reader(pcm.frameAt(5000)), r2 = pcm.reader(pcm.frameAt(5500));
    short[] b1 = new short[44100 * 2], b2 = new short[44100 * 2];
    int bad = 0;
    for (int k = 0; k < 3; k++) {
      long p1 = r1.position(), p2 = r2.position();
      int n1 = r1.read(b1, 44100, 2000), n2 = r2.read(b2, 44100, 2000);
      for (int i = 0; i < n1 * 2; i++) if (b1[i] != seq[(int) p1 * 2 + i]) bad++;
      for (int i = 0; i < n2 * 2; i++) if (b2[i] != seq[(int) p2 * 2 + i]) bad++;
    }
    System.out.println("pcm mismatches " + bad);
    TrackPcm.Reader r3 = pcm.reader(pcm.frameAt(19500));
    int n, total = 0; long t0 = System.currentTimeMillis();
    while ((n = r3.read(b1, 44100, 3000)) > 0) total += n;
    System.out.println("tail frames " + total + " end=" + pcm.endFrame() + " last read=" + n + " in " + (System.currentTimeMillis() - t0) + " ms");
    pcm.close();
    // DSP
    for (double f : new double[]{50, 100, 1000}) {
      short[] st = new short[44100 * 2]; for (int i = 0; i < 44100; i++) { short v = (short) (10000 * Math.sin(2 * Math.PI * f * i / 44100)); st[2*i] = v; st[2*i+1] = v; }
      short[] mono = new short[44100];
      SpeakerDsp lp = new SpeakerDsp(44100), hp = new SpeakerDsp(44100), eq = new SpeakerDsp(44100);
      lp.process(st, 44100, 0, 0, 0, 0, 1, mono); double gl = rms(mono) / 7071.0;
      hp.process(st, 44100, 0, 0, 0, 0, 2, mono); double gh = rms(mono) / 7071.0;
      eq.process(st, 44100, 0, 6, 6, 6, 0, mono); double ge = rms(mono) / 7071.0;
      System.out.printf("f=%4.0f LP %.1f dB  HP %.1f dB  EQ+6 %.1f dB%n", f, 20*Math.log10(gl), 20*Math.log10(gh), 20*Math.log10(ge));
    }
    for (double d : new double[]{3, 6, 12, 24, 48, 96, 150, 200}) System.out.printf("dist %3.0f line(5m) %.3f sub %.3f%n", d, Acoustics.distanceGain(d, false, 5), Acoustics.distanceGain(d, true, 0));
    for (double ang : new double[]{0, 45, 55, 80, 120, 180}) System.out.printf("angle %3.0f dir %.3f sub %.3f%n", ang, Acoustics.directivity(false, ang, 5, 110, 10, 0, 20, 0.18), Acoustics.directivity(true, ang, 5, 360, 360, 0, 0, 0.75));
    for (double el : new double[]{-30, -10, 0, 10, 25, 40, 70}) System.out.printf("elev %3.0f dir %.3f%n", el, Acoustics.directivity(false, 0, el, 110, 10, 0, 20, 0.18));
  }
  static double rms(short[] m) { double s = 0; int n = m.length / 2; for (int i = m.length / 2; i < m.length; i++) s += m[i] * (double) m[i]; return Math.sqrt(s / (m.length - n)); }
}
