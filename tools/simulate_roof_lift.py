#!/usr/bin/env python3
"""Checks, on a toy world, which blocks a lift takes along (trusses + a roof made of ordinary blocks + hanging lights).

Port of LiftControl.addAttachments / standsOnSomethingThatStays / isAttachable (1.2.9): attached blocks are followed from block to
block at any distance, then what rests on something that stays is dropped (with what was only held through it), then only what is still
connected to a truss is kept. It is not the Java code itself: a bug in the Java would not show up here, a bug in the design would.
Run: python3 tools/simulate_roof_lift.py
"""
from collections import deque

MAX_BLOCKS = 24000
MAX_SCAN = 120000
NEIGH = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
TRUSS = {'truss', 'corner'}            # carriers
SOLID = {'truss', 'corner', 'concrete', 'wall', 'ground', 'deck', 'tower', 'planks'}   # have a collision box
TERRAIN = {'ground'}                   # dirt, stone, sand, leaves, logs...: never attachable
OURS_STAY = {'deck', 'tower'}          # blocks of this mod that stay on the ground: never attachable


def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def attachable(k):
    return k is not None and k not in TERRAIN and k not in OURS_STAY and k not in TRUSS and k != 'air'


def add_attachments(blocks, cap):
    """blocks: pos -> kind (the world); cap: pos -> kind already captured (trusses). Returns (attached set, ok)."""
    found, q = set(), deque()
    for p, k in cap.items():
        if k not in TRUSS: continue
        for d in NEIGH:
            n = add(p, d)
            if n in cap or n in found: continue
            if attachable(blocks.get(n)): found.add(n); q.append(n)
    while q:
        cur = q.popleft()
        for d in NEIGH:
            n = add(cur, d)
            if n in cap or n in found: continue
            if attachable(blocks.get(n)):
                found.add(n)
                if len(found) > MAX_SCAN: return set(), False
                q.append(n)

    def stands_on_stay(keep, p):
        below = add(p, (0, -1, 0))
        if below in keep or below in cap: return False
        return blocks.get(below) in SOLID

    keep = set(found)
    dropped = deque(p for p in found if stands_on_stay(keep, p))
    while dropped:
        p = dropped.popleft()
        if p not in keep: continue
        keep.discard(p)
        above = add(p, (0, 1, 0))
        if above in keep and stands_on_stay(keep, above): dropped.append(above)

    linked, q2 = set(), deque()
    for p, k in cap.items():
        if k not in TRUSS: continue
        for d in NEIGH:
            n = add(p, d)
            if n in keep and n not in linked: linked.add(n); q2.append(n)
    while q2:
        cur = q2.popleft()
        for d in NEIGH:
            n = add(cur, d)
            if n in keep and n not in linked: linked.add(n); q2.append(n)
    return linked, True


def stage(roof_w=40, roof_l=40, layers=3, y_roof=21):
    """Ground, a deck, 4 truss beams around a roof of concrete resting on them."""
    w = {}
    for x in range(-5, roof_w + 5):
        for z in range(-5, roof_l + 5): w[(x, 0, z)] = 'ground'
    for x in range(0, roof_w):                      # two long beams under the roof, at y_roof - 1
        for z in (0, roof_l - 1): w[(x, y_roof - 1, z)] = 'truss'
    for z in range(0, roof_l):
        for x in (0, roof_w - 1): w[(x, y_roof - 1, z)] = 'truss'
    for x in range(0, roof_w):
        for z in range(0, roof_l):
            for y in range(layers): w[(x, y_roof + y, z)] = 'concrete'
    return w


def capture(w):
    cap = {p: k for p, k in w.items() if k in TRUSS}
    att, ok = add_attachments(w, cap)
    return cap, att, ok


checks = []
def check(name, cond, info=''):
    checks.append(cond); print(('  OK   ' if cond else '  FAIL ') + name + ((' : ' + str(info)) if info else ''))


print('Scenario 1 : a 40 x 40 roof, 3 layers of black concrete, resting on four beams')
w = stage(); cap, att, ok = capture(w)
roof = {p for p, k in w.items() if k == 'concrete'}
check('the whole roof is taken (4800 blocks)', ok and att == roof, (len(att), len(roof)))
check('well beyond the old limit of 3 blocks from a truss', max(min(abs(p[0] - 20), 99) for p in att) >= 19)
check('total under the limit', len(cap) + len(att) <= MAX_BLOCKS, len(cap) + len(att))

print('Scenario 2 : any block, not only concrete')
w = stage(12, 12, 2)
for p in list(w):
    if w[p] == 'concrete' and p[0] % 2: w[p] = 'planks'
cap, att, ok = capture(w)
check('planks and concrete both follow', ok and {w[p] for p in att} == {'concrete', 'planks'} and len(att) == 12 * 12 * 2, len(att))

print('Scenario 3 : the roof overhangs the beams')
w = stage(); y = 21
for x in range(-3, 43):
    for z in range(-3, 43):
        if (x, y, z) not in w: w[(x, y, z)] = 'concrete'
cap, att, ok = capture(w)
check('overhanging blocks (resting on nothing) come too', ok and (-3, y, -3) in att and (42, y, 42) in att)

print('Scenario 4 : a house next to the stage touches the roof edge: it stays')
w = stage()
for y in range(1, 22):
    for z in range(0, 40): w[(-1, y, z)] = 'wall'            # wall standing on the ground, up to the roof level
cap, att, ok = capture(w)
check('house wall is not lifted', not any(w[p] == 'wall' for p in att))
check('the roof still is, except where it rests on the wall', ok and len(att) > 4000, len(att))

print('Scenario 5 : a roof block directly above a wall that stays is left behind (it rests on it)')
w = stage()
for y in range(1, 21): w[(10, y, 10)] = 'wall'              # a pillar from the ground up to under the roof
cap, att, ok = capture(w)
check('block above the pillar stays', (10, 21, 10) not in att)
check('blocks above it (layers 2 and 3) stay too, they stood on it', (10, 22, 10) not in att and (10, 23, 10) not in att)
check('its neighbours are still taken', (11, 21, 10) in att and (9, 21, 10) in att)

print('Scenario 6 : a block standing on the ground next to the roof is not taken, even if it touches it')
w = stage(); w[(5, 1, 5)] = 'planks'
cap, att, ok = capture(w)
check('ground block ignored', (5, 1, 5) not in att)

print('Scenario 7 : a light hanging under a beam and a torch-like block on the roof')
w = stage(); w[(5, 19, 0)] = 'planks'; w[(5, 24, 5)] = 'planks'    # under the beam (y 20) and on the roof top (y 23 is top layer)
cap, att, ok = capture(w)
check('hanging block goes up', (5, 19, 0) in att)
check('block on the roof goes up', (5, 24, 5) in att)

print('Scenario 8 : the deck of the stage and the towers never travel')
w = stage(); w[(5, 20, 5)] = 'deck'; w[(6, 20, 5)] = 'tower'
cap, att, ok = capture(w)
check('deck / tower not taken', (5, 20, 5) not in att and (6, 20, 5) not in att)

print('Scenario 9 : a roof too big to scan is refused (not silently left behind)')
w = stage(); big = {}
for x in range(0, 400):                                   # a huge slab that touches the roof edge (x = 40 follows x = 39)
    for z in range(0, 400): big[(x + 40, 21, z)] = 'concrete'
w.update(big)
cap, att, ok = capture(w)
check('scan limit reached -> refused', not ok)

print('Scenario 10 : more than 24000 blocks after the scan -> too big')
w = stage(100, 100, 3); cap, att, ok = capture(w)
check('30000 roof blocks > MAX_BLOCKS', ok and len(cap) + len(att) > MAX_BLOCKS, len(cap) + len(att))

print()
print('%d / %d checks passed' % (sum(checks), len(checks)))
raise SystemExit(0 if all(checks) else 1)
