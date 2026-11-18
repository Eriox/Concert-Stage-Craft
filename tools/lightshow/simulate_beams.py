#!/usr/bin/env python3
"""Offline approximation of the beam drawing (old two-billboard beams vs rev. 2 cones), additive, on a black night scene.
Same formulas as FixtureRenderer / BeamRenderer; NOT a Minecraft capture (no blocks, no fog, no Shimmer): a tuning aid.
python3 -I simulate_beams.py out.png"""
import math, sys
import numpy as np
from PIL import Image

W, H = 960, 540
CAM = np.array([0.0, 2.0, -26.0]); FOV = math.radians(60)

def project(p):
    d = p - CAM
    f = (H / 2) / math.tan(FOV / 2)
    return np.stack([W / 2 + d[..., 0] / d[..., 2] * f, H / 2 - d[..., 1] / d[..., 2] * f], -1), d[..., 2]

def raster(img, tri, col):
    (p, z) = project(tri)
    if (z <= 0.1).any(): return
    xs, ys = p[:, 0], p[:, 1]
    x0, x1 = int(max(0, xs.min())), int(min(W - 1, xs.max()) + 1)
    y0, y1 = int(max(0, ys.min())), int(min(H - 1, ys.max()) + 1)
    if x1 <= x0 or y1 <= y0: return
    gx, gy = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
    (ax, bx, cx), (ay, by, cy) = xs, ys
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9: return
    l1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
    l2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
    l3 = 1 - l1 - l2
    m = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
    c = l1[..., None] * col[0] + l2[..., None] * col[1] + l3[..., None] * col[2]
    img[y0:y1, x0:x1][m] += np.clip(c[m], 0, 1)

def basis(a):
    u = np.array([0, 1.0, 0]) if abs(a[1]) < 0.9 else np.array([1.0, 0, 0])
    u = np.cross(u, a); u /= np.linalg.norm(u)
    v = np.cross(a, u); v /= np.linalg.norm(v)
    return u, v

def cone(img, o, a, length, r0, tan_half, rgb, hits, fade, seg=18, rings=8, edge=3.0, floor=0.03):
    u, v = basis(a)
    R = []
    for k in range(rings + 1):
        t = k / rings; d = length * t ** 1.35
        ramp = min(1, 0.25 + d / 0.6); att = 1 / (1 + fade * d); tail = 1 if hits else max(0, 1 - t * t)
        R.append((d, r0 + d * tan_half, ramp * att * tail))
    def vert(k, i):
        d, rad, kk = R[k]
        ang = 2 * math.pi * i / seg
        n = u * math.cos(ang) + v * math.sin(ang)
        p = o + a * d + n * rad
        view = p - CAM; dist = np.linalg.norm(view)
        facing = abs(n.dot(view)) / dist
        k2 = kk * (floor + (1 - floor) * facing ** edge)
        return p, np.clip(np.array(rgb) * k2, 0, 1)
    for k in range(rings):
        for i in range(seg):
            q = [vert(k, i), vert(k, i + 1), vert(k + 1, i + 1), vert(k + 1, i)]
            for side in range(2):               # both windings = two layers, as in the game
                for t in ((0, 1, 2), (0, 2, 3)):
                    raster(img, np.array([q[j][0] for j in t]), np.array([q[j][1] for j in t]))

def billboard(img, o, a, length, r0, r1, rgb, far):
    p1 = o + a * length
    def side(p):
        s = np.cross(a, p - CAM); return s / np.linalg.norm(s)
    s0, s1 = side(o), side(p1)
    c0, c1 = np.array(rgb), np.array(rgb) * far
    quad = [(o - s0 * r0, c0), (o + s0 * r0, c0), (p1 + s1 * r1, c1), (p1 - s1 * r1, c1)]
    for t in ((0, 1, 2), (0, 2, 3)):
        raster(img, np.array([quad[j][0] for j in t]), np.array([quad[j][1] for j in t]))

def scene(new):
    img = np.zeros((H, W, 3))
    # floor line (dark stage) for reference
    img[int(H * 0.78):, :] += 0.03
    rig_y = 9.0
    spots = [(-8 + 3.2 * i, rig_y, 4.0) for i in range(6)]
    washes = [(-9 + 6 * i, rig_y - 1, 1.0) for i in range(4)]
    for j, p in enumerate(spots):                       # VORTEX: tight beams fanned over the audience
        o = np.array(p); a = np.array([math.sin(math.radians(-25 + 10 * j)) * 0.6, -0.75, -0.55]); a /= np.linalg.norm(a)
        col = np.array([1.0, 0.55, 0.12]); out = 1.6; inten = 1.0
        if new:
            dens = math.sqrt(max(0.35, min(1.8, 10 / 6)))
            base = inten * out
            cone(img, o, a, 13, 0.06 * 1.3 * 0.7, math.tan(math.radians(3)) * 0.55, col * base * 0.5 * dens, True, 0.012)
            cone(img, o, a, 13, 0.06 * 1.3, math.tan(math.radians(3)), col * base * 0.2 * dens, True, 0.02)
        else:
            s = inten * out * (0.55 + 0.45 * (1 - 6 / 60))
            billboard(img, o, a, 13, 0.054, 0.054 + 13 * math.tan(math.radians(3)), col * s * 0.42, 0.06)
            billboard(img, o, a, 13, 0.027, (0.054 + 13 * math.tan(math.radians(3))) * 0.35, col * s * 0.35, 0.1)
    for j, p in enumerate(washes):                      # LUMORA W19: washes down onto the stage
        o = np.array(p); a = np.array([0.0, -0.95, -0.3]); a /= np.linalg.norm(a)
        col = np.array([0.95, 0.15, 0.6]); out = 1.0
        if new:
            dens = math.sqrt(max(0.35, min(1.8, 10 / 30)))
            cone(img, o, a, 9, 0.12 * 2.1, math.tan(math.radians(15)), col * out * 0.8 * max(dens, 0.8), True, 0.025, edge=1.3, floor=0.1)
            cone(img, o, a, 9, 0.12 * 2.1 * 1.1, math.tan(math.radians(15)) * 1.3, col * out * 0.22, True, 0.04, edge=1.2, floor=0.05)
        else:
            s = out * (0.55 + 0.45 * (1 - 30 / 60))
            billboard(img, o, a, 9, 0.108, 0.108 + 9 * math.tan(math.radians(15)), col * s * 0.42, 0.06)
            billboard(img, o, a, 9, 0.054, (0.108 + 9 * math.tan(math.radians(15))) * 0.35, col * s * 0.35, 0.1)
    return np.clip(img, 0, 1)

if __name__ == '__main__':
    old, new = scene(False), scene(True)
    pad = np.ones((H, 8, 3)) * 0.4
    Image.fromarray((np.concatenate([old, pad, new], 1) * 255).astype(np.uint8)).save(sys.argv[1])
