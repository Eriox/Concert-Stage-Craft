#!/usr/bin/env python3
"""Makes the 1 x 1 truss (1 block long, 1 block high) from the 1 x 2 truss.

Reads   tools/blockbench/truss_h1_l2.bbmodel
Writes  tools/blockbench/truss_h1_l1.bbmodel

It is the 1 x 2 truss cut to one block: same tubes, same two end frames, same flanges on both ends (x = 0 and x = 16) at the same
positions as every other truss and corner, so it plugs into any of them. Each of the two long faces and the top / bottom faces has one
45 degree diagonal ("/" seen from +z); place the block turned by half a turn to get the opposite direction, so a run of 1 x 1 trusses can zigzag.
Safe to run again: the 1 x 2 truss is always the source.
"""
import copy, json, os, re, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
BB = os.path.join(HERE, 'blockbench')


def shift_x(e, dx):
    for k in ('from', 'to', 'origin'):
        e[k][0] = round(e[k][0] + dx, 4)


def main():
    d = json.load(open(os.path.join(BB, 'truss_h1_l2.bbmodel'), encoding='utf-8'))
    out = []
    for e in d['elements']:
        n = e['name']
        e = copy.deepcopy(e)
        e['uuid'] = str(uuid.uuid4())
        if re.match(r'^diag_x[yz]_b_', n):
            continue                                        # the second half of the zigzag: not needed in one block
        if re.match(r'^diag_x[yz]_a_', n):
            shift_x(e, -1.5)                                # the "/" diagonal centred in the block (spans the 13 x 13 between the tube axes)
        elif n.startswith('arete_x_'):
            e['to'][0] = 15.95                              # the long tubes now stop at the end of the block
            e['origin'][0] = 8.0
        elif (e['from'][0] + e['to'][0]) / 2 > 16:
            shift_x(e, -16)                                 # the far end frame (posts, cross tubes, nodes) and its flange move back by one block
        out.append(e)
    d['elements'] = out
    d['outliner'] = [e['uuid'] for e in out]
    d['name'] = 'truss_h1_l1'
    d['visible_box'] = [1, 1, 0]
    names = [e['name'] for e in out]
    assert len(set(names)) == len(names), 'duplicate names'
    with open(os.path.join(BB, 'truss_h1_l1.bbmodel'), 'w', encoding='utf-8') as f:
        f.write(json.dumps(d, ensure_ascii=False))
    xs = [e['from'][0] for e in out] + [e['to'][0] for e in out]
    print('truss_h1_l1: %d elements, x from %.2f to %.2f' % (len(out), min(xs), max(xs)))


if __name__ == '__main__':
    main()
