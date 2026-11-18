#!/usr/bin/env python3
"""Exports the motorised towers to the game.

Input : tools/blockbench/towers/tour_truss_motorisee_<N>_blocs_<corner>.bbmodel   (made by build_motor_towers.py)
Output: assets/concertstage/towers/mast_<N>.json          static part of a tower, cubes sorted by 16 px cell (one entry per block)
        assets/concertstage/towers/carriage_<corner>.json  the sliding joint, relative to the bottom of its cell
        assets/concertstage/textures/block/motor_tower.png (the 16 x 16 texture of the towers)
        blockstates / item models / lang entries of the 16 tower blocks and of the remote, remote icon

Towers are taller than the 48 px a block model can hold and use free angles, so they are drawn by a block entity
renderer straight from these cube lists (see client/TowerModel.java).
"""
import base64, io, json, math, os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'blockbench', 'towers')
ASSETS = os.path.join(HERE, '..', 'common', 'src', 'main', 'resources', 'assets', 'concertstage')
HEIGHTS = (10, 14, 18, 22)
CORNERS = ('corner3', 'corner3_h2', 'corner6', 'corner6_h2')
REST_Y = 16.0   # must match build_motor_towers.py


def r(v):
    v = round(v, 3)
    return int(v) if v == int(v) else v


def cube(e, dy=0.0):
    f, t = e['from'], e['to']
    o = e.get('origin') or [(f[i] + t[i]) / 2 for i in range(3)]
    rot = e.get('rotation') or [0, 0, 0]
    uv = e['faces']['north']['uv']
    return [r(f[0]), r(f[1] + dy), r(f[2]), r(t[0]), r(t[1] + dy), r(t[2]),
            r(o[0]), r(o[1] + dy), r(o[2]), r(rot[0]), r(rot[1]), r(rot[2]),
            int(uv[0]), int(uv[1]), int(uv[2]), int(uv[3])]


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, separators=(',', ':'))


def main():
    tdir = os.path.join(ASSETS, 'towers')
    texture_done = False
    for n in HEIGHTS:
        d = json.load(open(os.path.join(SRC, f'tour_truss_motorisee_{n}_blocs_corner3.bbmodel'), encoding='utf-8'))
        cells = [[] for _ in range(n)]
        for e in d['elements']:
            if e['name'].startswith('chariot_'):
                continue
            rot = e.get('rotation') or [0, 0, 0]
            f1, t1 = e['from'][1], e['to'][1]
            first = max(0, min(n - 1, int(math.floor(f1 / 16.0))))
            last = max(0, min(n - 1, int(math.floor((t1 - 1e-6) / 16.0))))
            if not any(rot) and last > first:
                # a straight cube that crosses several cells (the four chords of the mast are one cube each over the whole
                # tower) is cut into one piece per cell: every cell draws its own slice, so nothing disappears when the cell
                # that used to hold the whole cube is off screen (this was the "broken tower when you stand close" bug)
                for idx in range(first, last + 1):
                    piece = dict(e)
                    piece['from'] = [e['from'][0], max(f1, idx * 16.0), e['from'][2]]
                    piece['to'] = [e['to'][0], min(t1, idx * 16.0 + 16.0), e['to'][2]]
                    cells[idx].append(cube(piece))
                continue
            cy = (f1 + t1) / 2.0
            idx = max(0, min(n - 1, int(math.floor(cy / 16.0))))
            cells[idx].append(cube(e))
        dump(os.path.join(tdir, f'mast_{n}.json'), {'height': n, 'cells': cells})
        print(f'mast_{n}.json : {sum(len(c) for c in cells)} cubes')
        if not texture_done:
            src = d['textures'][0]['source'].split(',', 1)[1]
            p = os.path.join(ASSETS, 'textures', 'block', 'motor_tower.png')
            open(p, 'wb').write(base64.b64decode(src))
            texture_done = True
    for c in CORNERS:
        d = json.load(open(os.path.join(SRC, f'tour_truss_motorisee_10_blocs_{c}.bbmodel'), encoding='utf-8'))
        cubes = [cube(e, -REST_Y) for e in d['elements'] if e['name'].startswith('chariot_')]
        dump(os.path.join(tdir, f'carriage_{c}.json'), {'corner': c, 'cubes': cubes})
        print(f'carriage_{c}.json : {len(cubes)} cubes')

    # ---- blockstates and item models of the tower blocks
    item_tpl = json.load(open(os.path.join(ASSETS, 'models', 'item', 'truss_v2_h2.json'), encoding='utf-8'))
    for n in HEIGHTS:
        for c in CORNERS:
          for suffix in ('', '_noir'):
            name = f'motor_tower_{n}_{c}{suffix}'
            variants = {}
            for facing in ('north', 'east', 'south', 'west'):
                for part in range(27):
                    variants[f'facing={facing},part={part}'] = {'model': 'concertstage:block/empty_truss'}
            dump(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': variants})
            dump(os.path.join(ASSETS, 'models', 'item', name + '.json'), item_tpl)

    # ---- black tower texture: aluminium -> satin black, anthracite -> lighter anthracite (so feet / motor still read), yellow unchanged
    tex = Image.open(os.path.join(ASSETS, 'textures', 'block', 'motor_tower.png')).convert('RGBA')
    px = tex.load()
    for y in range(16):
        for x in range(16):
            r_, g_, b_, a_ = px[x, y]
            if x < 8:                                   # left half: light aluminium (both tiles)
                v = (r_ + g_ + b_) / 3.0
                k = 34 + (v - 166) * 0.9
                px[x, y] = (round(k), round(k), round(k + 3), a_)
            elif y < 8:                                 # top right: anthracite
                px[x, y] = (62, 62, 66, a_)
    tex.save(os.path.join(ASSETS, 'textures', 'block', 'motor_tower_noir.png'))

    # ---- remote icon
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    g = ImageDraw.Draw(img)
    g.rectangle((5, 1, 10, 14), fill=(52, 54, 58, 255), outline=(20, 20, 22, 255))
    g.rectangle((6, 2, 9, 5), fill=(120, 200, 120, 255))                # screen
    g.rectangle((6, 7, 9, 8), fill=(235, 175, 20, 255))                 # up
    g.rectangle((6, 10, 9, 11), fill=(235, 175, 20, 255))               # down
    g.rectangle((7, 13, 8, 13), fill=(200, 60, 50, 255))                # stop
    g.rectangle((7, 0, 8, 0), fill=(150, 152, 156, 255))                # antenna
    p = os.path.join(ASSETS, 'textures', 'item', 'lift_remote.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    dump(os.path.join(ASSETS, 'models', 'item', 'lift_remote.json'),
         {'parent': 'item/handheld', 'textures': {'layer0': 'concertstage:item/lift_remote'}})

    print('assets written')


if __name__ == '__main__':
    main()
