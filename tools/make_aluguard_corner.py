#!/usr/bin/env python3
"""CSC AluGuard 124 — angle 90° (1.1.0-aurex.2): the corner piece that closes a square of crash barriers, public outside.
Built from the same parts as the straight AluGuard (tools/make_barriers.py), only this piece is written (the others are left alone).
Frame of the straight piece: 16 u = 1 m, public side toward -z, panel at z 19.6..21 of its 2-deep footprint.
The corner (2 x 2 cells, 2 high) joins a line running along x (public toward -z, panel z 19.6..21, as a straight one facing north) and a
line running along z (public toward +x: a straight one turned so, its panel at x 11..12.4). Feet outside, back legs inside."""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_barriers as mb

KEY = 'barrier_aluguard_corner'
PX = 11.0        # inner face of the panel of the line along z (x), outer face 12.4
PZ = 19.6        # public face of the panel of the line along x (z)

def corner():
    e = []
    # feet (aluminium slats) on the public side of both lines, nose tubes on the outer edges
    e.append(mb.el((0, 0, 0), (32, 0.7, PZ), 'slats'))
    mb.tube_x(e, 0, 32, 0.55, 0.6, 0.6, 'alu')
    e.append(mb.el((PX + 1.4, 0, PZ), (32, 0.7, 32), 'slats'))
    mb.tube_z(e, 0, 32, 31.4, 0.55, 0.6, 'alu')
    # panel along x (z 19.6..21), from x 0 to the corner post
    e += mb.prolyte_panel(0, PX + 1.4, zp=PZ)
    # panel along z (x 11..12.4), from the corner to z 32: built along x then turned a quarter (x <-> z)
    e += mb.swap_xz(mb.prolyte_panel(PZ, 32, zp=PX))
    mb.tube_y(e, 0, 20.2, PX + 0.7, PZ + 0.7, 1.1, 'alu')                  # corner post (hinge)
    # back legs inside the square (pit side): under the line along x toward +z, under the line along z toward -x
    for x in (0.8, PX - 2.5):
        e.append(mb.el((x - 0.45, 0, PZ + 1.4), (x + 0.45, 0.9, 28.6), 'alu'))
    for z in (PZ + 4.0, 31.2):
        e.append(mb.el((PX - 7.6, 0, z - 0.45), (PX, 0.9, z + 0.45), 'alu'))
    e.append(mb.el((0.4, 0, 27.6), (PX - 7.6, 0.9, 28.6), 'alu'))
    return e

def main():
    AS, DATA, RES = mb.AS, mb.DATA, mb.RES
    tex = {'0': 'concertstage:block/csc_barrier', 'particle': 'concertstage:block/csc_barrier'}
    size = (2, 2, 2); sx, sy, sz = size
    els = corner()
    parts = {}
    for (cx, cy, cz), pe in mb.split(els, size).items():
        p = cx + sx * (cz + sz * cy); name = f'{KEY}_p{p}'; parts[p] = name
        assert mb.fits(pe), (KEY, p)
        mb.dump(AS/'models/block'/(name + '.json'), {'ambientocclusion': False, 'textures': tex, 'elements': pe})
    mb.dump(AS/'models/block'/(KEY + '.json'), {'ambientocclusion': False, 'textures': tex, 'elements': mb.half(els, size)})
    v = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for part in range(27):
            row = {'model': 'concertstage:block/' + parts.get(part, 'empty_regie')}
            if y and part in parts: row['y'] = y
            v[f'facing={f},part={part}'] = row
    mb.dump(AS/'blockstates'/(KEY + '.json'), {'variants': v})
    m = 2; S = lambda a: [round(a * m, 3)] * 3
    mb.dump(AS/'models/item'/(KEY + '.json'), {'parent': 'concertstage:block/' + KEY, 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -2, 0], 'scale': S(0.42)},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': S(0.25)}, 'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': S(0.4)},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': S(0.3)}, 'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': S(0.3)},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': S(0.3)}}})
    mb.dump(DATA/'loot_tables/blocks'/(KEY + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'concertstage:' + KEY}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}, 2)
    mb.dump(DATA/'recipes'/(KEY + '.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'building', 'ingredients': [{'item': 'concertstage:barrier_aluguard_1m'}] * 2 + [{'item': 'minecraft:iron_ingot'}], 'result': {'item': 'concertstage:' + KEY, 'count': 1}}, 2)
    for lg, name in (('fr_fr', 'CSC AluGuard 124 — angle 90° (public à l’extérieur)'), ('en_us', 'CSC AluGuard 124 — 90° corner (public outside)')):
        p = AS/'lang'/(lg + '.json'); d = json.loads(p.read_text()); d['block.concertstage.' + KEY] = name; p.write_text(json.dumps(d, ensure_ascii=False, indent=2))
    p = RES/'data/minecraft/tags/blocks/mineable/pickaxe.json'; t = json.loads(p.read_text())
    t['values'] = list(dict.fromkeys(t['values'] + ['concertstage:' + KEY])); p.write_text(json.dumps(t, indent=2))
    bad = [(e['from'], e['to']) for e in els if any(a > b for a, b in zip(e['from'], e['to']))]
    print(KEY, 'parts', sorted(parts), 'bad', bad)

if __name__ == '__main__':
    main()
