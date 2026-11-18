#!/usr/bin/env python3
"""Pictures of the hanging hardware under truss models (flat-shaded, painter's algorithm; geometry check, not Minecraft).
Usage: python3 -I tools/rev18/render_mounts.py <repo root> <mount obj dir> <out.png>"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw
root, mdir, outp = sys.argv[1], sys.argv[2], sys.argv[3]
lit = os.path.join(root, 'common/src/main/resources/assets/concertstage/models/rigging/lit')
COL = {'c24272b': (52, 56, 62), 'ca9afb7': (190, 196, 204)}

def load(p, dy=0.0, col=(120, 124, 130)):
    V, F, c = [], [], col
    for l in open(p):
        t = l.split()
        if not t: continue
        if t[0] == 'v': V.append([float(t[1]), float(t[2]) + dy, float(t[3])])
        elif t[0] == 'usemtl': c = COL.get(t[1], col)
        elif t[0] == 'f': F.append(([int(s.split('/')[0]) - 1 for s in t[1:]], c))
    return V, F

def scene(name):
    V1, F1 = load(os.path.join(lit, name + '_lit_r0.obj'), 1.0, (150, 154, 160))
    V2, F2 = load(os.path.join(mdir, name + '_lit_r0_mount.obj'))
    top = float(open(os.path.join(mdir, name + '_lit_r0_mount.obj')).readline().split()[2])
    # the fixture base as a box under the bracket (0.5 wide, 0.25 high)
    b = [[0.28, top - 0.25, 0.28], [0.72, top - 0.25, 0.28], [0.72, top, 0.28], [0.28, top, 0.28], [0.28, top - 0.25, 0.72], [0.72, top - 0.25, 0.72], [0.72, top, 0.72], [0.28, top, 0.72]]
    fb = [[0, 3, 2, 1], [4, 5, 6, 7], [0, 1, 5, 4], [3, 7, 6, 2], [0, 4, 7, 3], [1, 2, 6, 5]]
    V = V1 + V2 + b
    F = F1 + [([i + len(V1) for i in f], c) for f, c in F2] + [([i + len(V1) + len(V2) for i in f], (200, 120, 40)) for f in fb]
    return np.array(V), F

def render(V, F, yaw, pitch, S=520, centre=(0.5, 1.0, 0.5), span=1.0):
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    R = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    P = (V - np.array(centre)) @ R.T
    sc = S / (2 * span)
    img = Image.new('RGB', (S, S), (18, 20, 24)); d = ImageDraw.Draw(img)
    L = np.array([0.3, 0.8, 0.5]); L /= np.linalg.norm(L)
    for i in sorted(range(len(F)), key=lambda i: -np.mean(P[F[i][0], 2])):
        f, c = F[i]; q = P[f]
        n = np.cross(q[1] - q[0], q[2] - q[0]); ln = np.linalg.norm(n)
        if ln < 1e-12: continue
        k = 0.35 + 0.65 * abs(np.dot(n / ln, R @ L))
        d.polygon([(S / 2 + x * sc, S / 2 - y * sc) for x, y, _ in q], fill=tuple(int(v * k) for v in c))
    return img

names = ['truss_h1_l2', 'truss_x30d_l2', 'truss_ladder_l2', 'truss_corner_6']
rows = []
for n in names:
    V, F = scene(n)
    ims = [render(V, F, y, p) for y, p in [(0.0, 0.0), (math.pi / 2, 0.0), (0.65, 0.35)]]
    row = Image.new('RGB', (520 * 3, 540), (30, 30, 30))
    for k, im in enumerate(ims): row.paste(im, (k * 520, 20))
    ImageDraw.Draw(row).text((6, 4), n + "  (front, side, 3/4) - geometry check, not Minecraft", fill=(230, 230, 230))
    rows.append(row)
sheet = Image.new('RGB', (520 * 3, 540 * len(rows)))
for i, r in enumerate(rows): sheet.paste(r, (0, 540 * i))
sheet.save(outp)
