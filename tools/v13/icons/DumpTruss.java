/** Writes the mesh (x y z mat per corner) of a 90° curved piece, radius 3.5 m, for the item icons (tools/v13/icons). */
public class DumpTruss {
    public static void main(String[] a) throws Exception {
        for (String s : new String[] {"QUAD", "TRI"}) {
            CurvedTrussGeometry.Mesh m = new CurvedTrussGeometry.Mesh();
            CurvedTrussGeometry.Section sec = CurvedTrussGeometry.Section.valueOf(s);
            double R = 3.5;
            CurvedTrussGeometry.Frame f = CurvedTrussGeometry.Frame.of(new double[] {0, 0, 0}, new double[] {1, 0, 0}, new double[] {0, 0, -1}, new double[] {0, 1, 0});
            CurvedTrussGeometry.curved(m, 1, f, sec, R, 0, Math.toRadians(90), true);
            float[] d = m.data();
            try (java.io.PrintWriter w = new java.io.PrintWriter(args0(a) + "/truss_" + s.toLowerCase() + ".txt")) {
                for (int i = 0; i < d.length; i += 7) w.println(d[i] + " " + d[i + 1] + " " + d[i + 2] + " " + (int) d[i + 6]);
            }
        }
    }
    static String args0(String[] a) { return a.length > 0 ? a[0] : "."; }
}
