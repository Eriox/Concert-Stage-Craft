#!/usr/bin/env python3
"""Textures of the beams, lens glows and gobos (CSC 1.2.0). White on black: drawn additively and tinted by the beam colour.
Gobos are original patterns. Usage: python3 make_light_textures.py <assets/concertstage>"""
import math, os, sys
from PIL import Image
dst = os.path.join(sys.argv[1], 'textures', 'lightshow')
os.makedirs(dst, exist_ok=True)

def save(img, name):
    img.save(os.path.join(dst, name + '.png'))

# beam: soft profile across u (bright core, soft edges), constant along v
W, H = 64, 16
img = Image.new('RGBA', (W, H))
for x in range(W):
    u = (x + 0.5) / W * 2 - 1
    core = math.exp(-(u * 3.2) ** 2)
    halo = max(0.0, 1 - abs(u)) ** 1.6
    v = min(1.0, 0.55 * halo + 0.6 * core)
    c = int(255 * v)
    for y in range(H):
        img.putpixel((x, y), (c, c, c, 255))
save(img, 'beam')
# beam through a gobo: strands across the width
img = Image.new('RGBA', (W, H))
for x in range(W):
    u = (x + 0.5) / W * 2 - 1
    halo = max(0.0, 1 - abs(u)) ** 1.2
    strands = 0.5 + 0.5 * math.cos(u * math.pi * 7)
    v = halo * (0.25 + 0.75 * strands ** 3)
    c = int(255 * min(1, v))
    for y in range(H):
        img.putpixel((x, y), (c, c, c, 255))
save(img, 'beam_gobo')
# glow: radial
S = 32
img = Image.new('RGBA', (S, S))
for x in range(S):
    for y in range(S):
        r = math.hypot((x + 0.5) / S * 2 - 1, (y + 0.5) / S * 2 - 1)
        v = max(0.0, 1 - r) ** 1.4
        c = int(255 * v)
        img.putpixel((x, y), (c, c, c, 255))
save(img, 'glow')
# gobos (projected spot): 0 open, 1..8 patterns
# rev. 14b: 256 px with 3 x 3 supersampling (64 px before): they are projected large on walls and truss, edges must stay crisp
S = 256
def gobo(fn):
    img = Image.new('RGBA', (S, S))
    for x in range(S):
        for y in range(S):
            acc = 0.0
            for sx in (0.17, 0.5, 0.83):
                for sy in (0.17, 0.5, 0.83):
                    px, py = (x + sx) / S * 2 - 1, (y + sy) / S * 2 - 1
                    r, a = math.hypot(px, py), math.atan2(py, px)
                    edge = max(0.0, min(1.0, (0.98 - r) / 0.03))
                    acc += max(0.0, min(1.0, fn(r, a, px, py))) * edge
            c = int(round(255 * acc / 9))
            img.putpixel((x, y), (c, c, c, 255))
    return img

def dots(r, a, x, y):
    """rev. 14b: breakup of round holes on concentric rings (centre, 6, 11, 16), smaller towards the edge"""
    rings = [(0.0, 1, 0.12), (0.31, 6, 0.105), (0.57, 11, 0.09), (0.81, 16, 0.075)]
    for rr, n, size in rings:
        for k in range(n):
            ang = 2 * math.pi * k / n + (0.3 if n % 2 else 0.0)
            cx, cy = rr * math.cos(ang), rr * math.sin(ang)
            if math.hypot(x - cx, y - cy) < size:
                return 1.0
    return 0.0
patterns = [
    lambda r, a, x, y: 1.0,                                                             # open
    lambda r, a, x, y: 1.0 if (math.cos(a * 6) > 0.2 and r > 0.15) or r < 0.12 else 0.0,  # 6 petals
    dots,                                                                               # dots (rev. 14b: round holes on rings)
    lambda r, a, x, y: 1.0 if math.sin(a * 3 + r * 9) > 0.1 else 0.0,                   # spiral
    lambda r, a, x, y: 1.0 if 0.55 < r or r < 0.25 else 0.0,                            # ring
    lambda r, a, x, y: 1.0 if math.cos(a * 12) > 0.6 else 0.0,                          # rays
    lambda r, a, x, y: 1.0 if (abs(x) % 0.4) < 0.18 else 0.0,                           # bars
    lambda r, a, x, y: 1.0 if (math.sin(x * 11) * math.cos(y * 9) + math.sin((x + y) * 7)) > 0.3 else 0.0,  # breakup
    lambda r, a, x, y: 1.0 if r < 0.35 * (1 + 0.6 * math.cos(a * 5)) else 0.0,         # star
]
for i, fn in enumerate(patterns):
    save(gobo(fn), 'gobo_%d' % i)
print('textures written to', dst)

# 1.2.0 rev. 2 — volumetric cones: plain beam and a tileable haze (smoke) texture scrolled along the beams
img = Image.new('RGBA', (8, 8), (255, 255, 255, 255))
save(img, 'beam_solid')
import random
random.seed(7)
S = 64
G = 8
grid = [[random.random() for _ in range(G)] for _ in range(G)]
def smooth(t): return t * t * (3 - 2 * t)
def noise(x, y):
    x0, y0 = int(x) % G, int(y) % G
    x1, y1 = (x0 + 1) % G, (y0 + 1) % G
    fx, fy = smooth(x - int(x)), smooth(y - int(y))
    a = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
    b = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
    return a * (1 - fy) + b * fy
img = Image.new('RGBA', (S, S))
for x in range(S):
    for y in range(S):
        v = 0.0
        for o, w in ((1, 0.6), (2, 0.3), (4, 0.1)):
            v += w * noise(x / S * G * o / 1.0 % G, y / S * G * o / 1.0 % G)
        c = int(255 * (0.62 + 0.38 * v))
        img.putpixel((x, y), (c, c, c, 255))
save(img, 'beam_haze')

# ---- rev. 5: smooth radial profile for the soft beams, the projected light and the halos (128 px, so that the profile across a beam
# never shows steps with the game's nearest-pixel filtering): 1 at the centre, a long soft shoulder, exactly 0 from the edge outwards
N = 128
img = Image.new('RGBA', (N, N))
px = img.load()
for y in range(N):
    for x in range(N):
        r = math.hypot((x + 0.5) / N * 2 - 1, (y + 0.5) / N * 2 - 1)
        t = max(0.0, 1.0 - r)
        v = t * t * (3 - 2 * t)
        v = v ** 1.15
        c = int(round(255 * v))
        px[x, y] = (c, c, c, 255)
save(img, 'beam_soft')

# ---- rev. 8: open spot of the VORTEX on a surface: a defined edge softened over its outer 22 %, a gentle hot centre
N = 128
img = Image.new('RGBA', (N, N))
px = img.load()
for y in range(N):
    for x in range(N):
        r = math.hypot((x + 0.5) / N * 2 - 1, (y + 0.5) / N * 2 - 1)
        t = min(1.0, max(0.0, (1 - r) / 0.22))
        v = t * t * (3 - 2 * t) * (0.72 + 0.28 * max(0.0, 1 - r * r))
        c = int(round(255 * v))
        px[x, y] = (c, c, c, 255)
save(img, 'spot_open')
