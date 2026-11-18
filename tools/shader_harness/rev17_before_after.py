"""
Rev. 17 lighting calibration, before / after, with the REAL shaders of the mod on Mesa (see scene.py; not Minecraft).
Usage: python3 -I tools/shader_harness/rev17_before_after.py <repo root> <shaders dir of the version before (assets/minecraft/shaders)> <out dir>
"Before" = those shaders + the rev. 16d calibration of FixtureRenderer (DumpOptics -Dcsc.calib=16); "after" = this repository.
Needs /tmp/harness (gcc tools/shader_harness/harness.c -ldl), numpy, Pillow, javac.
"""
import os, sys
ROOT = os.path.abspath(sys.argv[1])
ORIG = os.path.abspath(sys.argv[2])
OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
sys.argv = ["scene.py", ROOT, OUT]
sys.path.insert(0, os.path.join(ROOT, "tools/shader_harness"))
import scene as S
from PIL import Image, ImageDraw
W19, V, W7 = "csc_lumora_w19", "csc_vortex_x1", "csc_lumora_w7"
NEW = S.SH
st = S.stage()
cases = [
 ("1 - deux W19 vers le public (comme la capture)", (0.0, 1.6, 12.0), (0.0, 4.0, -15.0), (-3.0, 5.0, -15.0), (-1.0, 1.0, 10.0), (W19, 0.4, 0, 0, 0, 1, 1, 1.0, 0), (0.55, 0.35, 1.0),
   [((3.0, 5.0, -15.0), (1.0, 1.0, 10.0), (W19, 0.4, 0, 0, 0, 1, 1, 1.0, 0), (0.55, 0.35, 1.0))]),
 ("2 - W19 vu de cote, pres de la scene", (3.0, 1.6, -8.0), (-1.0, 2.0, -14.0), (-1.5, 5.0, -13.0), (-1.5, -3.0, -12.0), (W19, 0.6, 0, 0, 0, 1, 1, 1.0, 0), (1.0, 0.3, 0.1), []),
 ("3 - W7 sur le plateau", (8.0, 1.6, -2.0), (0.0, 1.0, -15.0), (-2.0, 5.0, -15.0), (2.0, -3.0, -10.0), (W7, 0.35, 0, 0, 0, 1, 1, 1.0, 0), (0.1, 1.0, 0.4), []),
 ("4 - deux VORTEX croises (gobo a droite)", (0.0, 1.6, 8.0), (0.0, 2.0, -15.0), (-5.0, 5.0, -15.0), (6.0, -3.0, -8.0), (V, 0.2, 0.5, 0, 0, 1, 1, 1.0, 0), (0.2, 0.6, 1.0),
   [((5.0, 5.0, -15.0), (-6.0, -3.0, -8.0), (V, 0.2, 0.5, 3, 0, 1, 1, 1.0, 0), (1.0, 0.5, 0.1))]),
]
rows = []
for title, eye, look, fix, aim, stt, col, more in cases:
    pair = []
    for lab, sh, cal in (("AVANT", ORIG, "16"), ("APRES", NEW, "17")):
        S.SH = sh; os.environ["CSC_CALIB"] = cal; os.environ["CSC_JOPTS"] = ""
        pair.append(S.render(st, eye, look, fix, aim, stt, col, 25.0, "%s - %s" % (lab, title), more=more))
    w, h = pair[0].size
    row = Image.new("RGB", (w * 2 + 6, h), (30, 30, 30)); row.paste(pair[0], (0, 0)); row.paste(pair[1], (w + 6, 0)); rows.append(row)
sheet = Image.new("RGB", (rows[0].width, sum(r.height + 6 for r in rows) - 6), (30, 30, 30))
y = 0
for r in rows: sheet.paste(r, (0, y)); y += r.height + 6
sheet.save(os.path.join(OUT, "eclairage_avant_apres.png"))
