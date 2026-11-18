import sys, os
root, out = sys.argv[1], sys.argv[2]
sys.argv = ["scene.py", root, out]
sys.path.insert(0, os.path.join(root, "tools/shader_harness"))
import scene as S
row = [((-7.0 + 2.0 * k, 0.3, -12.0), (-7.0 + 2.0 * k, -0.5, 6.0)) for k in range(8)]
st = ("csc_pulsestrike_m", 0.6, 0.0, 0, 0, 1, 1, 1, 0)
f0, a0 = row[0]
im = S.render(S.Scene(), (0.0, 0.5, 2.0), (0, 0, -12), f0, a0, st, (1, 1, 1), 25.0, "8 PULSESTRIKE, blinder", 0, [(f, a, st, (1, 1, 1)) for f, a in row[1:]])
im.save(os.path.join(out, "25_pulse_row_blinder.png"))
