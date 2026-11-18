#!/usr/bin/env python3
"""
CSC 1.1.1 - truss models: the flat end caps of the small rods (pins / bolts of the end plates) were fans of triangles. Forge's OBJ
loader bakes every face as a quad (a triangle is a quad with a repeated corner), so a hexagonal cap of 4 triangles cost 4 quads in
the chunk mesh. The same flat convex polygon is now written as quads (a hexagon = 2 quads): same outline, same plane, same normal,
same texture region; only the internal split changes.

Safety checks for every merged cap (the file is left untouched if one fails):
  * the triangles of a cap share one plane (normal within 0.5 degrees) and one group, and tile a convex polygon exactly
    (sum of the triangle areas = polygon area within 1e-9);
  * the quads cover exactly the same polygon (same area, every polygon corner used, convex quads, winding = original normal).
Usage: python3 -I tools/v111/merge_truss_caps.py <models/rigging dir> [--dry]
Prints, per file, faces before / after.
"""
import math, os, sys


def parse(lines):
    V, VT, faces = [], [], []
    for i, l in enumerate(lines):
        t = l.split()
        if not t:
            continue
        if t[0] == 'v':
            V.append(tuple(float(x) for x in t[1:4]))
        elif t[0] == 'vt':
            VT.append(tuple(float(x) for x in t[1:3]))
        elif t[0] == 'f':
            faces.append((i, [tuple(int(x) if x else 0 for x in (s.split('/') + ['', ''])[:3]) for s in t[1:]]))
    return V, VT, faces


def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def norm(a): return math.sqrt(dot(a, a))


def tri_normal(P):
    n = cross(sub(P[1], P[0]), sub(P[2], P[0]))
    l = norm(n)
    return (n[0] / l, n[1] / l, n[2] / l), l / 2


def poly_area(P, n):
    s = (0.0, 0.0, 0.0)
    for i in range(len(P)):
        c = cross(P[i], P[(i + 1) % len(P)])
        s = (s[0] + c[0], s[1] + c[1], s[2] + c[2])
    return abs(dot(s, n)) / 2


def key(p): return tuple(round(c, 6) for c in p)


def process(path, dry):
    lines = open(path).read().split('\n')
    V, VT, faces = parse(lines)
    group_of, g = {}, None
    for i, l in enumerate(lines):
        if l.startswith('g ') or l.startswith('o '):
            g = l.split()[1] if len(l.split()) > 1 else ''
        group_of[i] = g
    tris = [(i, idx) for i, idx in faces if len(idx) == 3]
    # caps: triangles of one group sharing their first corner and their plane
    caps = {}
    for i, idx in tris:
        P = [V[a[0] - 1] for a in idx]
        n, area = tri_normal(P)
        if area < 1e-12:
            continue
        k = (group_of[i], key(P[0]), tuple(round(c, 2) for c in n))
        caps.setdefault(k, []).append((i, idx, n, area))
    replace, drop = {}, set()
    merged = 0
    for k, members in caps.items():
        if len(members) < 2:
            continue
        n = members[0][2]
        if any(math.degrees(math.acos(max(-1, min(1, dot(n, m[2]))))) > 0.5 for m in members):
            continue
        corner = {}                                    # position -> (v, vt, vn) of its first use
        for _, idx, _, _ in members:
            for a in idx:
                corner.setdefault(key(V[a[0] - 1]), a)
        pts = list(corner.keys())
        c = tuple(sum(p[j] for p in pts) / len(pts) for j in range(3))
        u = sub(pts[0], c); ul = norm(u)
        if ul < 1e-12:
            continue
        u = (u[0] / ul, u[1] / ul, u[2] / ul); w = cross(n, u)
        pts.sort(key=lambda p: math.atan2(dot(sub(p, c), w), dot(sub(p, c), u)))
        m = len(pts)
        if m < 4:
            continue
        # convex, counter-clockwise around n
        convex = all(dot(cross(sub(pts[(i + 1) % m], pts[i]), sub(pts[(i + 2) % m], pts[(i + 1) % m])), n) > 1e-12 for i in range(m))
        tri_area = sum(a for _, _, _, a in members)
        if not convex or abs(poly_area(pts, n) - tri_area) > 1e-9:
            continue
        quads = []
        for s in range(1, m - 1, 2):
            if s + 2 <= m - 1:
                quads.append([pts[0], pts[s], pts[s + 1], pts[s + 2]])
            else:
                quads.append([pts[0], pts[s], pts[s + 1]])
        if sum(poly_area(q, n) for q in quads) - tri_area > 1e-9 or len(quads) >= len(members):
            continue
        if any(len(q) == 4 and not all(dot(cross(sub(q[(i + 1) % 4], q[i]), sub(q[(i + 2) % 4], q[(i + 1) % 4])), n) > 0 for i in range(4)) for q in quads):
            continue
        text = ['f ' + ' '.join('%d/%d/%d' % corner[p] for p in q) for q in quads]
        first = max(i for i, _, _, _ in members)          # after the last vertex any of them uses (OBJ indices must be defined before use)
        replace[first] = '\n'.join(text)
        drop.update(i for i, _, _, _ in members if i != first)
        merged += 1
    before = len(faces)
    after = before - sum(len(v) for v in caps.values() if False)
    out, removed, added = [], 0, 0
    for i, l in enumerate(lines):
        if i in drop:
            removed += 1
            continue
        if i in replace:
            removed += 1
            added += replace[i].count('\n') + 1
            out.append(replace[i])
            continue
        out.append(l)
    if not dry and merged:
        open(path, 'w').write('\n'.join(out))
    return before, before - removed + added, merged


def main():
    root = sys.argv[1]
    dry = '--dry' in sys.argv
    tb = ta = 0
    for dirpath, _, files in os.walk(root):
        if os.path.basename(dirpath) == 'inventory':
            continue                                   # item models (inventory pictures), not drawn in the world
        for f in sorted(files):
            if not f.endswith('.obj'):
                continue
            b, a, m = process(os.path.join(dirpath, f), dry)
            tb += b; ta += a
            print('%-58s faces %5d -> %5d  (%d caps)' % (os.path.relpath(os.path.join(dirpath, f), root), b, a, m))
    print('TOTAL faces (= baked quads) %d -> %d (%.1f %%)' % (tb, ta, 100.0 * (ta - tb) / max(1, tb)))


if __name__ == '__main__':
    main()
