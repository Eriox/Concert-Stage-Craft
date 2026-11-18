import com.concertstage.audio.core.*;
import java.nio.file.*;
public class SeekTest {
  public static void main(String[] a) throws Exception {
    Path p = Path.of(a[0]);
    short[] seq = new short[4000000]; int tot = 0;
    try (TrackDecoder d = AudioFiles.open(p, "mp3")) { short[] b = new short[4096]; int n; while ((n = d.read(b)) >= 0) { System.arraycopy(b, 0, seq, tot*2, n*2); tot += n; } }
    System.out.println("total " + tot);
    for (long target : new long[]{6000, 20000, 100000, 441000}) {
      try (TrackDecoder d = AudioFiles.open(p, "mp3")) {
        d.skip(target); short[] b = new short[20000]; d.read(b);
        // per 1152 block error
        StringBuilder sb = new StringBuilder();
        for (int blk = 0; blk < 8; blk++) { long e = 0; for (int i = blk*1152; i < (blk+1)*1152 && i < 10000; i++) e += Math.abs(b[2*i] - seq[(int)(target+i)*2]); sb.append(e/1152).append(' '); }
        System.out.println("target " + target + " err/blk " + sb);
      }
    }
  }
}
