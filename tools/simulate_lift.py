#!/usr/bin/env python3
"""Checks the logic of the tower lift on a toy world (no Minecraft needed).

This is a port of the algorithms of lift/LiftControl.java, lift/LiftStructureEntity.java and block/TowerBlockEntity.java:
what gets captured (breadth first search from the joints, stops at the ground bases, size limit), the lift as a function of the
game time, the block-boundary obstacle check, stop = next block boundary, placement when the lift ends, and carrying whoever
stands on top. It is not the Java code itself: a bug in the Java would not show up here, a bug in the design would.
"""
import sys
from collections import deque

UNITS = 20
LIFTABLE = {'truss', 'rail', 'cloth', 'petit', 'hoist'}
HOIST_LOAD = {'petit', 'rail', 'cloth'}      # what a chain hoist takes along
RANGE = 8
MAX_BLOCKS = 4096
NEIGH = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]


def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


class Tower:
    def __init__(self, main, height, cells, sides, hoist=False):
        self.main, self.height, self.cells, self.sides, self.hoist = main, height, cells, sides, hoist
        self.units, self.moving, self.start_tick, self.start_units, self.target, self.session, self.fault = 0, False, 0, 0, 0, None, 0

    def max_lift(self): return RANGE if self.hoist else self.height - 2 - self.cells

    def units_at(self, now):
        if not self.moving: return self.units
        d = 1 if self.target > self.start_units else -1 if self.target < self.start_units else 0
        v = self.start_units + d * max(0, now - self.start_tick)
        return min(v, self.target) if d > 0 else max(v, self.target)


class World:
    def __init__(self):
        self.blocks = {}          # pos -> kind
        self.towers = []
        self.session = None
        self.tick = 0

    def solid(self, p): return p in self.blocks
    def tower_cell(self, p):
        return any(t.main[0] == p[0] and t.main[2] == p[2] and t.main[1] <= p[1] < t.main[1] + t.height for t in self.towers)

    # ---- LiftControl.capture
    def capture(self, towers, lift):
        found, seen, q = {}, set(), deque()
        hoist = towers[0].hoist
        ok = HOIST_LOAD if hoist else LIFTABLE
        for t in towers:
            starts = [(t.main[0], t.main[1] - 1 - (RANGE - lift), t.main[2])] if hoist else \
                [add((t.main[0], t.main[1] + 1 + lift + c, t.main[2]), d) for c in range(t.cells) for d in t.sides]
            for s in starts:
                if s not in seen:
                    seen.add(s)
                    if self.blocks.get(s) in ok: found[s] = self.blocks[s]; q.append(s)
        while q:
            cur = q.popleft()
            for d in NEIGH:
                n = add(cur, d)
                if n in seen: continue
                seen.add(n)
                k = self.blocks.get(n)
                if k == 'base': return None, ('GROUNDED', n)
                if k not in ok: continue
                found[n] = k
                if len(found) > MAX_BLOCKS: return None, ('TOO_BIG', None)
                q.append(n)
        return found, None

    def can_occupy(self, anchor, rel, off):
        return all(add(add(anchor, r), (0, off, 0)) not in self.blocks and not self.tower_cell(add(add(anchor, r), (0, off, 0)))
                   for r in rel)

    # ---- LiftControl.start
    def start(self, towers, target_blocks):
        u0 = towers[0].units
        if any(t.moving for t in towers): return 'MOVING'
        if any(t.units != u0 for t in towers): return 'NOT_ALIGNED'
        target = max(0, min(min(t.max_lift() for t in towers), target_blocks)) * UNITS
        if target == u0: return 'ALREADY'
        d = 1 if target > u0 else -1
        found, err = self.capture(towers, u0 // UNITS)
        if err: return err[0]
        anchor = towers[0].main
        rel = {add(p, tuple(-x for x in anchor)): k for p, k in found.items()}
        own = set(found)
        for r in rel:
            p = add(add(anchor, r), (0, d, 0))
            if p not in own and (p in self.blocks or self.tower_cell(p)): return 'BLOCKED'
        for p in found: del self.blocks[p]
        self.session = dict(id=object(), anchor=anchor, rel=rel, base=u0, start_tick=self.tick, start=u0, target=target, towers=towers)
        for t in towers:
            t.moving, t.start_tick, t.start_units, t.target, t.session, t.fault = True, self.tick, u0, target, self.session['id'], 0
        return 'STARTED'

    def session_units(self):
        s = self.session
        d = 1 if s['target'] > s['start'] else -1 if s['target'] < s['start'] else 0
        v = s['start'] + d * max(0, self.tick - s['start_tick'])
        return min(v, s['target']) if d > 0 else max(v, s['target'])

    def retarget(self, new_target):
        s = self.session; u = self.session_units()
        s['start_tick'], s['start'], s['target'] = self.tick, u, new_target
        for t in s['towers']: t.start_tick, t.start_units, t.target = self.tick, u, new_target

    def stop(self):
        if not self.session: return False
        s = self.session; u = self.session_units(); d = 1 if s['target'] > s['start'] else -1
        rem = u % UNITS
        self.retarget(u if rem == 0 else (u + UNITS - rem if d > 0 else u - rem))
        return True

    # ---- LiftStructureEntity.tick (server)
    def step(self):
        self.tick += 1
        s = self.session
        if not s: return
        u = self.session_units()
        if u != s['target'] and u % UNITS == 0:
            d = 1 if s['target'] > s['start'] else -1
            if not self.can_occupy(s['anchor'], s['rel'], (u - s['base']) // UNITS + d):
                self.retarget(u)
                for t in s['towers']: t.fault = 1
                u = self.session_units()
        if u == s['target']:
            off = (u - s['base']) // UNITS
            for r, k in s['rel'].items(): self.blocks[add(add(s['anchor'], r), (0, off, 0))] = k
            for t in s['towers']: t.units, t.moving, t.session = u, False, None
            self.session = None


def beam_world(towers_heights=(10, 10), gap=3, ground_base=False):
    w = World()
    x = 0
    for h in towers_heights:
        w.towers.append(Tower((x, 0, 0), h, 1, [(1, 0, 0), (-1, 0, 0)]))
        x += gap + 1
    for t in w.towers: w.blocks[t.main] = 'tower'   # marker so the tower column is not empty (cells checked by tower_cell)
    for t in w.towers: del w.blocks[t.main]
    # a beam of `gap` truss blocks between the two carriages (carriage cell 1), plus a curtain hanging under the middle block
    for i in range(1, gap + 1): w.blocks[(i, 1, 0)] = 'truss'
    w.blocks[(2, 0, 0)] = 'cloth'
    if ground_base: w.blocks[(2, -1, 0)] = 'base'
    return w


def run(w, towers, target, limit=3000):
    r = w.start(towers, target)
    if r != 'STARTED': return r
    for _ in range(limit):
        w.step()
        if not w.session: return 'DONE'
    return 'TIMEOUT'


checks = []
def check(name, cond, info=''):
    checks.append(cond); print(('  OK   ' if cond else '  FAIL ') + name + ((' : ' + str(info)) if info else ''))


print('Scenario 1 : two towers, a beam between the joints, lifted 5 blocks')
w = beam_world(); ts = w.towers
r = run(w, ts, 5)
check('lift completes', r == 'DONE', r)
check('beam is 5 blocks higher', all(w.blocks.get((i, 6, 0)) == 'truss' for i in (1, 2, 3)) and not any((i, 1, 0) in w.blocks for i in (1, 2, 3)))
check('curtain follows (now at y=5)', w.blocks.get((2, 5, 0)) == 'cloth')
check('towers report 5 blocks', all(t.units == 5 * UNITS and not t.moving for t in ts))
check('it took 100 ticks (1 block per second)', w.tick == 100, w.tick)

print('Scenario 2 : stop in the middle')
w = beam_world(); ts = w.towers
w.start(ts, 6)
for _ in range(47): w.step()
check('stop accepted', w.stop())
while w.session: w.step()
check('stops on the next block boundary (3 blocks)', ts[0].units == 3 * UNITS and w.blocks.get((2, 4, 0)) == 'truss', ts[0].units)
r = run(w, ts, 0)
check('and can go back down to the start', r == 'DONE' and w.blocks.get((2, 1, 0)) == 'truss')

print('Scenario 3 : obstacle above')
w = beam_world(); ts = w.towers
w.blocks[(2, 5, 0)] = 'scaffold'                         # in the way at the 4th block of travel
r = run(w, ts, 7)
check('stops under the obstacle', r == 'DONE' and ts[0].units == 3 * UNITS, ts[0].units)
check('fault flagged on the towers', all(t.fault == 1 for t in ts))
check('beam placed at y = 4', w.blocks.get((2, 4, 0)) == 'truss')

print('Scenario 4 : structure standing on the ground')
w = beam_world(ground_base=True); ts = w.towers
check('refused (GROUNDED)', w.start(ts, 4) == 'GROUNDED')
check('nothing was removed', w.blocks.get((2, 1, 0)) == 'truss' and w.session is None)

print('Scenario 5 : towers at different heights / already moving / same height')
w = beam_world(); ts = w.towers
ts[0].units = 2 * UNITS
check('different heights refused', w.start(ts, 4) == 'NOT_ALIGNED')
ts[0].units = 0
check('already at that height', w.start(ts, 0) == 'ALREADY')
w.start(ts, 2)
check('second start while moving refused', w.start(ts, 4) == 'MOVING')

print('Scenario 6 : limits')
w = beam_world((10, 14)); ts = w.towers
r = run(w, ts, 99)
check('target limited by the lower tower (7 blocks)', r == 'DONE' and ts[0].units == 7 * UNITS == ts[1].units, ts[0].units)

print('Scenario 7 : a structure too big')
w = beam_world(); ts = w.towers
for i in range(4, 4 + MAX_BLOCKS + 10): w.blocks[(i, 1, 0)] = 'truss'
check('refused (TOO_BIG)', w.start(ts, 3) == 'TOO_BIG')

print('Scenario 8 : no attachments (joint alone)')
w = World(); w.towers = [Tower((0, 0, 0), 10, 1, [(1, 0, 0)])]
r = run(w, w.towers, 4)
check('towers move, nothing to place', r == 'DONE' and w.towers[0].units == 4 * UNITS)

print('Scenario 9 : the lift is the same function on every side (game time only)')
t = Tower((0, 0, 0), 10, 1, []); t.moving, t.start_tick, t.start_units, t.target = True, 100, 20, 120
check('values', [t.units_at(n) for n in (90, 100, 101, 150, 300)] == [20, 20, 21, 70, 120])

print('Scenario 10 : carrying a player standing on a beam')
top, pos, y = 1 + 1, None, 2.0       # beam block at y=1 -> top face at y=2
player_y = 2.0
for u in range(0, 5 * UNITS + 1):    # per tick the platform rises 1/20 block and the rider is snapped to the new top
    old_top = 2.0 + (u - 1) / UNITS if u else 2.0
    new_top = 2.0 + u / UNITS
    if player_y >= old_top - 0.6 and player_y <= old_top + 0.12: player_y = new_top
check('rider ends 5 blocks higher, never left behind', abs(player_y - 7.0) < 1e-9, player_y)

print('Scenario 11 : chain hoists carry only small trusses, hung under a big truss')
w = World()
w.blocks[(0, 5, 0)] = 'truss'; w.blocks[(1, 5, 0)] = 'truss'          # big beam at y=5
hs = [Tower((0, 4, 0), 1, 1, [], hoist=True), Tower((1, 4, 0), 1, 1, [], hoist=True)]
for t in hs: t.units = RANGE * UNITS                                  # a new hoist is fully raised
w.towers = hs
w.blocks[(0, 3, 0)] = 'petit'; w.blocks[(1, 3, 0)] = 'petit'          # small beam right under the hoists
w.blocks[(1, 2, 0)] = 'cloth'                                         # curtain hanging from the small beam
w.blocks[(2, 3, 0)] = 'truss'                                         # a big truss touching the small beam: stays
r = run(w, hs, 0)
check('lowering completes', r == 'DONE', r)
check('small beam went down 8 blocks', w.blocks.get((0, -5, 0)) == 'petit' and w.blocks.get((1, -5, 0)) == 'petit' and (0, 3, 0) not in w.blocks)
check('curtain followed', w.blocks.get((1, -6, 0)) == 'cloth')
check('big trusses stayed', w.blocks.get((0, 5, 0)) == 'truss' and w.blocks.get((2, 3, 0)) == 'truss')
check('hoists report 0 (lowest)', all(t.units == 0 for t in hs))
r = run(w, hs, 8)
check('raising brings the load back under the hoists', r == 'DONE' and w.blocks.get((0, 3, 0)) == 'petit' and w.blocks.get((1, 2, 0)) == 'cloth', r)
w.blocks[(0, -2, 0)] = 'scaffold'
r = run(w, hs, 0)
check('lowering stops on an obstacle', r == 'DONE' and hs[0].units > 0 and all(t.fault == 1 for t in hs), hs[0].units)
w2 = World(); t2 = Tower((0, 4, 0), 1, 1, [], hoist=True); t2.units = RANGE * UNITS; w2.towers = [t2]
w2.blocks[(0, 3, 0)] = 'truss'                                        # a big truss under the hoist is not a hoist load
check('hoist with nothing to carry just moves', run(w2, [t2], 3) == 'DONE' and w2.blocks.get((0, 3, 0)) == 'truss')

print()
ok = all(checks)
print('%d / %d checks passed' % (sum(checks), len(checks)))
sys.exit(0 if ok else 1)
