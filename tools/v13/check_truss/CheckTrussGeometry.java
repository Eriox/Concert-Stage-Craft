/** CSC 1.3.0: the curved / circular truss geometry of the game against the supplied Blender variants (triangles per LOD, end B). */
public class CheckTrussGeometry {
    public static void main(String[] a) {
        Object[][] cases = {{"QUAD",30,3.0},{"QUAD",45,3.0},{"QUAD",90,3.0},{"QUAD",180,3.0},{"QUAD",90,2.0},{"QUAD",90,5.0},
                            {"TRI",30,3.0},{"TRI",45,3.0},{"TRI",90,3.0},{"TRI",180,3.0},{"TRI",90,2.0},{"TRI",90,5.0}};
        for (Object[] c : cases) {
            CurvedTrussGeometry.Section s = CurvedTrussGeometry.Section.valueOf((String) c[0]);
            double R = (Double) c[2]; double sw = Math.toRadians((Integer) c[1]);
            StringBuilder b = new StringBuilder(c[0] + "_A" + c[1] + "_R" + (int) R + " ");
            for (int lod = 0; lod < 4; lod++) {
                CurvedTrussGeometry.Mesh m = new CurvedTrussGeometry.Mesh();
                CurvedTrussGeometry.curved(m, lod, CurvedTrussGeometry.blenderFrame(R), s, R, 0, sw, true);
                b.append(m.triangles()).append(lod < 3 ? "/" : "");
            }
            double[] e = CurvedTrussGeometry.centre(CurvedTrussGeometry.blenderFrame(R), R, sw);
            b.append("  B=(" + String.format("%.4f, %.4f", e[0], e[2]) + ")");
            System.out.println(b);
        }
        for (Object[] c : new Object[][] {{"QUAD",2.0},{"QUAD",4.0},{"QUAD",8.0},{"QUAD",12.0},{"QUAD",20.0},{"TRI",4.0},{"TRI",8.0}}) {
            CurvedTrussGeometry.Section s = CurvedTrussGeometry.Section.valueOf((String) c[0]);
            double R = (Double) c[1] / 2; StringBuilder b = new StringBuilder("RING_" + c[0] + "_D" + (int) (2 * R) + " ");
            for (int lod = 0; lod < 4; lod++) {
                CurvedTrussGeometry.Mesh m = new CurvedTrussGeometry.Mesh();
                CurvedTrussGeometry.ring(m, lod, CurvedTrussGeometry.Frame.of(new double[3], new double[]{1,0,0}, new double[]{0,0,1}, new double[]{0,1,0}), s, R);
                b.append(m.triangles()).append(lod < 3 ? "/" : "");
            }
            System.out.println(b);
        }
    }
}
