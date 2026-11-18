#!/usr/bin/env python3
"""Turns the X30D triangular truss (2 blocks long) so that it runs along x like every other truss.

Reads   tools/blockbench/reference/truss_x30d_L100_x2.bbmodel   (as drawn in Blockbench: runs along z, 32 px long)
Writes  tools/blockbench/truss_x30d_l2.bbmodel                  (runs along x, size 2 x 1 x 1, the convention of tools/generate_assets.py)

The turn is a proper rotation of a quarter turn around the vertical axis through the middle of the block, (x, y, z) -> (z, y, 16 - x),
so nothing is mirrored. Tilted cubes keep their angle: a rotation around x becomes a rotation around z (opposite sign), around z becomes
around x, around y stays. Safe to run again.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'blockbench', 'reference', 'truss_x30d_L100_x2.bbmodel')
DST = os.path.join(HERE, 'blockbench', 'truss_x30d_l2.bbmodel')


def turn(p):
    return [p[2], p[1], 16.0 - p[0]]


def main():
    d = json.load(open(SRC, encoding='utf-8'))
    for e in d['elements']:
        a, b = turn(e['from']), turn(e['to'])
        e['from'] = [round(min(a[i], b[i]), 4) for i in range(3)]
        e['to'] = [round(max(a[i], b[i]), 4) for i in range(3)]
        e['origin'] = [round(v, 4) for v in turn(e['origin'])]
        r = e.get('rotation')
        if r and any(r):
            e['rotation'] = [r[2], r[1], -r[0]]
    d['name'] = 'truss_x30d_l2'
    json.dump(d, open(DST, 'w', encoding='utf-8'), ensure_ascii=False)
    xs = [e['from'][0] for e in d['elements']] + [e['to'][0] for e in d['elements']]
    zs = [e['from'][2] for e in d['elements']] + [e['to'][2] for e in d['elements']]
    print('truss_x30d_l2: x %.2f..%.2f, z %.2f..%.2f' % (min(xs), max(xs), min(zs), max(zs)))


if __name__ == '__main__':
    main()
