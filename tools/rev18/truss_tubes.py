#!/usr/bin/env python3
"""
CSC 1.2.0 rev. 18 - the horizontal tubes of every truss model, for hanging the fixtures on them (FixtureMount).

Reads the block models actually drawn in the world (models/rigging/lit/<name>_lit_r<turn>.obj: the truss of each facing, coordinates
in blocks from the origin of its part-0 cell) and keeps, per OBJ group (one tube / plate each), the straight horizontal tubes: a group
longer than 0.35 block along X or Z and thinner than 0.3 block across in the two other directions. Diagonals, posts and plates are
left out. Writes assets/concertstage/lightshow/truss_tubes.json:
  {"<model name>": [[axis(0 = X, 2 = Z), y_min, y_max, c_min, c_max, a_min, a_max], ...]}
with c = the coordinate across (Z for an X tube), a = along. Usage: python3 -I tools/rev18/truss_tubes.py <repo root>
"""
import glob, json, os, sys

root = sys.argv[1] if len(sys.argv) > 1 else '.'
res = os.path.join(root, 'common/src/main/resources/assets/concertstage')
out = {}
for f in sorted(glob.glob(os.path.join(res, 'models/rigging/lit/*.obj'))):
    groups, g, V = {}, None, []
    for line in open(f):
        t = line.split()
        if not t: continue
        if t[0] == 'v': V.append([float(x) for x in t[1:4]])
        elif t[0] in ('g', 'o'): g = t[1] if len(t) > 1 else ''
        elif t[0] == 'f':
            for s in t[1:]:
                groups.setdefault(g, []).append(V[int(s.split('/')[0]) - 1])
    tubes = []
    for name, pts in groups.items():
        lo = [min(p[i] for p in pts) for i in range(3)]
        hi = [max(p[i] for p in pts) for i in range(3)]
        ex = [hi[i] - lo[i] for i in range(3)]
        for axis, other in ((0, 2), (2, 0)):
            if ex[axis] >= 0.35 and ex[other] <= 0.3 and ex[1] <= 0.3:
                tubes.append([axis, round(lo[1], 4), round(hi[1], 4), round(lo[other], 4), round(hi[other], 4), round(lo[axis], 4), round(hi[axis], 4)])
    # merge tubes split in several groups along their length (same axis, same section)
    tubes.sort()
    merged = []
    for t in tubes:
        if merged and merged[-1][:5] == t[:5] and t[5] <= merged[-1][6] + 0.02:
            merged[-1][6] = max(merged[-1][6], t[6]); continue
        merged.append(t)
    out[os.path.basename(f)[:-4]] = merged
p = os.path.join(res, 'lightshow/truss_tubes.json')
json.dump(out, open(p, 'w'), separators=(',', ':'), sort_keys=True)
print(len(out), 'truss models,', sum(len(v) for v in out.values()), 'horizontal tubes ->', os.path.relpath(p, root))
for k in ('truss_h1_l2_lit_r0', 'truss_h1_l2_lit_r1', 'truss_h1_l2_petit_lit_r0', 'truss_x30d_l2_lit_r0', 'truss_ladder_l2_lit_r0', 'truss_h2_l2_lit_r0', 'truss_corner_6_lit_r0', 'truss_base_v2_lit_r0', 'truss_v2_h2_lit_r0'):
    print(k, out.get(k))

# block -> model of its part-0 cell per facing (north, east, south, west), from the blockstates (FixtureMount finds the tubes from it)
blocks = {}
for f in sorted(glob.glob(os.path.join(res, 'blockstates/*.json'))):
    d = json.load(open(f))
    for k, val in d.get('variants', {}).items():
        if isinstance(val, list): val = val[0]
        m = val.get('model', '').split('/')[-1]
        props = dict(p.split('=') for p in k.split(',') if '=' in p)
        if m in out and props.get('part') == '0' and 'facing' in props and 'x' not in val and 'y' not in val:
            i = ('north', 'east', 'south', 'west').index(props['facing'])
            blocks.setdefault(os.path.basename(f)[:-5], [None] * 4)[i] = m
p2 = os.path.join(res, 'lightshow/truss_blocks.json')
json.dump(blocks, open(p2, 'w'), separators=(',', ':'), sort_keys=True)
print(len(blocks), 'truss blocks ->', os.path.relpath(p2, root))
