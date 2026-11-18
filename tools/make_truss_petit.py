#!/usr/bin/env python3
"""Makes the small 1 x 2 truss (10 px section instead of 16 px) used with the chain hoist.

Reads   tools/blockbench/fullsize/truss_h1_l2.bbmodel
Writes  tools/blockbench/truss_h1_l2_petit.bbmodel

Uses the "smaller" recipe of shrink_trusses.py (tubes 2 px, tube centres 4 / 12 px from the cell edge, i.e. a 10 px section centred in the
block, 45 degree diagonals) on the 1 x 2 beam only. The flanges and end frames stay on the cell boundary (x = 0 and x = 32), so the small
beam plugs into any other truss or joint. The texture is the one of the full-size truss (tower aluminium).
Safe to run again: the full-size original is never touched.
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import shrink_trusses as S


def main():
    src = json.load(open(os.path.join(S.SRC, 'truss_h1_l2.bbmodel'), encoding='utf-8'))
    cross, lax, ltot = S.PIECES['truss_h1_l2']
    els = S.shrink_elements(src['elements'], cross)
    lines = S.chord_lines(cross, els)
    els += S.build_diagonals(src['elements'][0], cross, lax, ltot, lines)
    src['name'] = 'truss_h1_l2_petit'
    S.write('truss_h1_l2_petit', src, els)
    ys = [e['from'][1] for e in els] + [e['to'][1] for e in els]
    zs = [e['from'][2] for e in els] + [e['to'][2] for e in els]
    print('truss_h1_l2_petit: %d elements, y %.2f..%.2f, z %.2f..%.2f' % (len(els), min(ys), max(ys), min(zs), max(zs)))


if __name__ == '__main__':
    main()
