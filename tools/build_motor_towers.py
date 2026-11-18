#!/usr/bin/env python3
"""Adds a truss joint ("chariot") that slides along each motorised tower.

Reads  tools/blockbench/towers_src/tour_truss_motorisee_<N>_blocs.bbmodel   (the 4 towers, written by make_towers_src.py)
       tools/blockbench/truss_corner_{3,3_h2,6,6_h2}.bbmodel               (the joints, one block wide since 1.2.9)
Writes tools/blockbench/towers/tour_truss_motorisee_<N>_blocs_<corner>.bbmodel  (4 heights x 4 joints = 16 models)
       tools/blockbench/towers/chariot_<corner>.bbmodel                          (the carriage alone, to render it separately)

In each tower model the joint lives in its own group "Chariot". Everything attached to the joint's flanges (trusses,
curtains...) is meant to follow that group. The animations "monter" and "descendre" move the group from the lowest to the
highest position (1 block per second) so the movement can be previewed in Blockbench. In the game, translate the carriage by
`lift` pixels (0 = lowest, TRAVEL = highest, written in the animation name / README).
"""
import base64, copy, io, json, math, os, re, sys, uuid
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from shrink_trusses import classify, bar_axis  # noqa: E402

BB = os.path.join(HERE, 'blockbench')
SRC_T = os.path.join(BB, 'towers_src')
OUT = os.path.join(BB, 'towers')

REST_Y = 16.0      # the joint rests in cell 1 of the tower (cell 0 holds the motor box), so it is always aligned with the block grid
HEAD_GAP = 2.0     # distance kept under the head plate at the top
GROW = 0.12        # the joint tubes are a hair thicker than the mast tubes, so they never share a face (no z-fighting)
SPEED = 16.0       # px per second in the preview animation (1 block / s)

CORNERS = {'corner3': 'truss_corner_3', 'corner3_h2': 'truss_corner_3_h2',
           'corner6': 'truss_corner_6', 'corner6_h2': 'truss_corner_6_h2'}
HEIGHTS = (10, 14, 18, 22)

# tiles of the 64x64 truss texture (uv units) -> tiles of the 16x16 tower texture
CORNER_TILE = {'alu': (0, 0), 'alu_dark': (4, 0), 'anod': (8, 0), 'yellow': (12, 0),
               'red': (0, 4), 'black': (4, 4), 'chain': (8, 4), 'plate': (12, 4)}
TOWER_TILE = {'alu': [0, 0, 8, 8], 'alu_dark': [0, 8, 8, 16], 'anod': [8, 0, 16, 8], 'yellow': [8, 8, 16, 16],
              'red': [8, 8, 16, 16], 'black': [8, 0, 16, 8], 'chain': [0, 0, 8, 8], 'plate': [0, 0, 8, 8]}
RECT2NAME = {v: k for k, v in CORNER_TILE.items()}


def tile_name(uv):
    return RECT2NAME[(int(uv[0]), int(uv[1]))]


def patch_texture(tex_b64, corner_png_b64):
    """The tower texture has an unused quarter (0..8, 8..16): put the joint's dark aluminium there. Nothing else changes."""
    im = Image.open(io.BytesIO(base64.b64decode(tex_b64.split(',', 1)[1]))).convert('RGBA')
    cim = Image.open(io.BytesIO(base64.b64decode(corner_png_b64.split(',', 1)[1]))).convert('RGBA')
    tile = cim.crop((16, 0, 32, 16)).resize((8, 8), Image.NEAREST)      # alu_dark of the joint texture
    im.paste(tile, (0, 8))
    buf = io.BytesIO(); im.save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


def carriage_elements(corner_name, rest_y, to_tower):
    """Elements of the joint, open frame (the mast goes through it), tubes grown by GROW, lifted by rest_y."""
    src = json.load(open(os.path.join(BB, corner_name + '.bbmodel'), encoding='utf-8'))
    out = []
    for e in src['elements']:
        name = e['name']
        if re.match(r'^(haut_)?plaque', name):
            continue                                       # closed faces would block the mast
        cat = classify(name)
        e = copy.deepcopy(e)
        f, t = list(e['from']), list(e['to'])
        if cat == 'bar':
            b = bar_axis(e)
            for a in range(3):
                if a != b: f[a] -= GROW; t[a] += GROW
        elif cat == 'node':
            for a in range(3): f[a] -= GROW; t[a] += GROW
        f[1] += rest_y; t[1] += rest_y
        e['from'] = [round(v, 4) for v in f]
        e['to'] = [round(v, 4) for v in t]
        e['origin'] = [round((f[i] + t[i]) / 2, 4) for i in range(3)]
        if e.get('rotation') is None: e['rotation'] = [0, 0, 0]
        e['name'] = 'chariot_' + name
        e['uuid'] = str(uuid.uuid4())
        for face in e['faces'].values():
            nm = tile_name(face['uv'])
            face['uv'] = list(TOWER_TILE[nm]) if to_tower else face['uv']
            face['texture'] = 0
        out.append(e)
    return out, src


def lifting_spider(template, mid_y, to_tower, uvtile):
    """Cross bars between the joint tubes + a shackle round the chain: this is where the motor chain pulls the joint."""
    def cube(name, f, t, tile):
        e = copy.deepcopy(template)
        e['name'] = name; e['uuid'] = str(uuid.uuid4())
        e['from'] = [round(v, 4) for v in f]; e['to'] = [round(v, 4) for v in t]
        e['origin'] = [round((a + b) / 2, 4) for a, b in zip(f, t)]
        e['rotation'] = [0, 0, 0]
        e.pop('rotation_origin', None)
        e['faces'] = {k: {'uv': list(uvtile(tile)), 'texture': 0} for k in ('north', 'east', 'south', 'west', 'up', 'down')}
        return e
    return [cube('chariot_levage_x', [3.0, mid_y - 0.6, 7.3], [13.0, mid_y + 0.6, 8.7], 'alu_dark'),
            cube('chariot_levage_z', [7.3, mid_y - 0.6, 3.0], [8.7, mid_y + 0.6, 13.0], 'alu_dark'),
            cube('chariot_manille', [6.9, mid_y - 0.9, 6.9], [9.1, mid_y + 1.5, 9.1], 'yellow')]


def anim(group_uuid, name, y0, y1, seconds):
    def kf(t, y):
        return {'channel': 'position', 'data_points': [{'x': '0', 'y': str(round(y, 3)), 'z': '0'}],
                'uuid': str(uuid.uuid4()), 'time': t, 'color': -1, 'interpolation': 'linear'}
    return {'uuid': str(uuid.uuid4()), 'name': name, 'loop': 'once', 'override': False, 'length': round(seconds, 3),
            'snapping': 24, 'selected': False, 'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
            'animators': {group_uuid: {'name': 'Chariot', 'type': 'bone',
                                        'keyframes': [kf(0, y0), kf(round(seconds, 3), y1)]}}}


def main():
    os.makedirs(OUT, exist_ok=True)
    corner_png = json.load(open(os.path.join(BB, 'truss_corner_3.bbmodel'), encoding='utf-8'))['textures'][0]['source']
    summary = []
    for n in HEIGHTS:
        tower = json.load(open(os.path.join(SRC_T, f'tour_truss_motorisee_{n}_blocs.bbmodel'), encoding='utf-8'))
        mast_top = max(e['to'][1] for e in tower['elements'] if e['name'].startswith('montant'))
        for key, cname in CORNERS.items():
            els, _ = carriage_elements(cname, REST_Y, True)
            cell_h = 32 if cname.endswith('_h2') else 16
            mid = REST_Y + (16 if cell_h == 32 else 8)
            els += lifting_spider(tower['elements'][0], mid, True, lambda nm: TOWER_TILE[nm])
            top_geom = max(e['to'][1] for e in els)
            travel = round((mast_top - HEAD_GAP) - top_geom, 2)
            d = copy.deepcopy(tower)
            d['name'] = f'tour_truss_motorisee_{n}_blocs_{key}'
            d['elements'] += els
            grp = copy.deepcopy(d['outliner'][2])
            grp.update({'name': 'Chariot', 'uuid': str(uuid.uuid4()), 'children': [e['uuid'] for e in els], 'isOpen': True})
            d['outliner'].append(grp)
            d['animations'] = [anim(grp['uuid'], 'monter', 0, travel, travel / SPEED),
                               anim(grp['uuid'], 'descendre', travel, 0, travel / SPEED)]
            d['textures'][0]['source'] = patch_texture(d['textures'][0]['source'], corner_png)
            with open(os.path.join(OUT, d['name'] + '.bbmodel'), 'w', encoding='utf-8') as f:
                json.dump(d, f)
            summary.append((d['name'], len(d['elements']), travel))
    # the carriage alone (own texture), for a separate renderer
    for key, cname in CORNERS.items():
        els, src = carriage_elements(cname, 0.0, False)
        cell_h = 32 if cname.endswith('_h2') else 16
        els += lifting_spider(src['elements'][0], 16 if cell_h == 32 else 8, False,
                              lambda nm: [CORNER_TILE[nm][0], CORNER_TILE[nm][1], CORNER_TILE[nm][0] + 4, CORNER_TILE[nm][1] + 4])
        d = copy.deepcopy(src)
        d['name'] = 'chariot_' + key
        d['elements'] = els
        d['outliner'] = [e['uuid'] for e in els]
        with open(os.path.join(OUT, d['name'] + '.bbmodel'), 'w', encoding='utf-8') as f:
            json.dump(d, f)
    for s in summary:
        print(f'{s[0]:48s} {s[1]:4d} elements   course {s[2]:7.2f} px ({s[2] / 16:5.2f} blocs)')


if __name__ == '__main__':
    main()
