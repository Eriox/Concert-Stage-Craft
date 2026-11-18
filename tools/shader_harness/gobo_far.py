import sys, os
root, out = sys.argv[1], sys.argv[2]
sys.argv = ["scene.py", root, out]
sys.path.insert(0, os.path.join(root, "tools/shader_harness"))
import scene as S
S.W, S.H = 1440, 760
from PIL import Image
os.makedirs(out, exist_ok=True)
V = "csc_vortex_x1"
eye = (0.0, 1.0, 0.0)
pics = []
for name, fix, aim, gobo, focus, title in [
    ("g1_far_gobo6", (0.0, 8.0, -46.0), (0.0, -3.0, -30.0), 6, 0.0, "gobo 6 on the floor 30 blocks away"),
    ("g2_far_gobo3", (0.0, 8.0, -46.0), (0.0, -3.0, -30.0), 3, 0.0, "gobo 3, 30 blocks away, focus 0"),
    ("g3_mid_gobo6", (0.0, 8.0, -26.0), (0.0, -3.0, -12.0), 6, 0.0, "gobo 6, 12 blocks away, focus 0")]:
    im = S.render(S.Scene(), eye, (aim[0], aim[1] + 1.5, aim[2]), fix, aim, (V, 0.7, focus, gobo, 0, 1, 1, 1.0, 0), (1, 1, 1), 25.0, title)
    pics.append(im)
W, H = pics[0].size
sheet = Image.new("RGB", (W * len(pics), H))
for k, p in enumerate(pics): sheet.paste(p, (k * W, 0))
sheet.save(os.path.join(out, "far_sheet.png"))
