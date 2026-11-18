import sys, os
sys.argv = ["scene.py", sys.argv[1], sys.argv[2]]
sys.path.insert(0, os.path.join(sys.argv[1], "tools/shader_harness"))
import scene as S
from PIL import Image
out = sys.argv[2]; os.makedirs(out, exist_ok=True)
V = "csc_vortex_x1"
eye = (0.0, 1.5, 0.0)
fix = (0.0, 5.0, -22.0)
pics = []
for name, aim, gobo, title in [
    ("e1_open_axis", (0.0, 1.5, 0.0), 0, "looking straight into a VORTEX, open"),
    ("e2_gobo_axis", (0.0, 1.5, 0.0), 3, "straight into a VORTEX, gobo 3"),
    ("e3_open_off", (2.2, 1.5, 0.0), 0, "VORTEX 6 deg off the eye, open"),
    ("e4_gobo_off", (2.2, 1.5, 0.0), 6, "VORTEX 6 deg off the eye, gobo 6")]:
    # aim beyond the eye so the beam runs past the camera
    ax = [aim[i] + (aim[i] - fix[i]) * 0.5 for i in range(3)]
    im = S.render(S.Scene(), eye, fix, fix, ax, (V, 0.6, 0.5, gobo, 0, 1, 1, 1.0, 0), (1, 1, 1), 25.0, title)
    im.save(os.path.join(out, name + ".png")); pics.append(im)
W, H = pics[0].size
sheet = Image.new("RGB", (W * 2, H * 2))
for k, p in enumerate(pics): sheet.paste(p, ((k % 2) * W, (k // 2) * H))
sheet.save(os.path.join(out, "sheet.png"))
