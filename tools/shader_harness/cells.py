"""Emitter cells of the four CSC models (centre relative to the beam origin, half size, kind), for the off-game optics tests.
Same data the client reads (FixtureMeshes zones). Usage: python3 -I tools/shader_harness/cells.py > tools/lightshow_tests/optics_cells.txt"""
import json, os, sys
root = sys.argv[1] if len(sys.argv) > 1 else "."
K = {"rgb": 0, "white": 1, "cell": 2}
m = json.load(open(os.path.join(root, "tools/external/modes.json")))
for f in m["fixtures"]:
    d = json.load(open(os.path.join(root, "common/src/main/resources/assets/concertstage/lightshow/%s.json" % f["id"])))
    o = d["beam"]["origin"]
    cs = ["%g,%g,%g,%g,%d" % (z["centre"][0] - o[0], z["centre"][1] - o[1], z["half"][0], z["half"][1], K[z["kind"]])
          for z in d["zones"].values() if "centre" in z]
    print("%s;%g;%g;%s" % (f["id"], f["render_scale"], d["beam"]["lens_radius"] * 0.9 * f["render_scale"], " ".join(cs)))
