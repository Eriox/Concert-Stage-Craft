import importlib.util, sys, math
import numpy as np
from PIL import Image
spec = importlib.util.spec_from_file_location('sim', '../../tools/lightshow/simulate_rev5.py')
sim = importlib.util.module_from_spec(spec); spec.loader.exec_module(sim)
sim.GLOW = np.asarray(Image.open('../../common/src/main/resources/assets/concertstage/textures/lightshow/beam_soft.png').convert('L'), float) / 255
sim.REV = 7
GRID = 7; CELL = 1 / GRID * 1.15
def shafts(path):
    im = np.asarray(Image.open(path).convert('L'), float) / 255; h, w = im.shape; out = []
    for gy in range(GRID):
        for gx in range(GRID):
            cx = (gx + .5) / GRID * 2 - 1; cy = (gy + .5) / GRID * 2 - 1
            if cx * cx + cy * cy > 1.05: continue
            wgt = im[gy*h//GRID:(gy+1)*h//GRID, gx*w//GRID:(gx+1)*w//GRID].mean()
            if wgt > .2: out.append((cx, cy, wgt))
    return out
cam = sim.Cam([-14, 4, 4], [0, 6, 4])
imgs = []
for g in (1, 2, 5):
    img = sim.scene(cam)
    o = np.array([0, 10.3, 4.0]); a = sim.norm(np.array([0.15, -1, 0.1]))
    L, p, n = sim.hit(o, a)
    tan = math.tan(math.radians(9)); r0 = .11
    side = sim.norm(np.cross([1.0, 0, 0], a)); up = sim.norm(np.cross(a, side))
    col = np.array([0.75, 0.8, 1.0])
    sim.soft_beam(cam, img, o, a, L, r0, tan, col * .12 * 1.35, True)
    for qx, qy, wgt in shafts(f'../../common/src/main/resources/assets/concertstage/textures/lightshow/gobo_{g}.png'):
        oo = o + (side * qx + up * qy) * r0
        d = a + (side * qx + up * qy) * tan; dl = np.linalg.norm(d); d = d / dl
        sim.soft_beam(cam, img, oo, d, L, r0 * CELL, tan * CELL / dl, col * .75 * 1.35 * wgt, True, segs=4, nd=0, discs=False)
    imgs.append(img)
out = np.concatenate([np.concatenate([i, np.ones((sim.H, 4, 3))], 1) for i in imgs], 1)
sim.save(out, '/tmp/simg/gobo_beams.png')
print('ok')
