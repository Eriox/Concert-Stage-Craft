#!/usr/bin/env python3
"""Block states, block / item models, loot tables, recipes, tags and the patch tool icon of the lighting content (1.2.0).
Run from the project root: python3 -I tools/lightshow/make_light_assets.py  (idempotent)."""
import json, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'common', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'concertstage')
D = os.path.join(ROOT, 'data', 'concertstage')
FIXTURES = ['csc_vortex_x1', 'csc_lumora_w7', 'csc_lumora_w19', 'csc_pulsestrike_m']
PALETTE = 'concertstage:block/lightshow_palette'

def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: json.dump(obj, f, indent=1)

# fixtures: drawn by their block entity renderer (moving parts), the block model only gives the break particles
put(os.path.join(A, 'models', 'block', 'csc_fixture_particle.json'), {'textures': {'particle': PALETTE}})
for fid in FIXTURES:
    variants = {}
    for facing in ('north', 'east', 'south', 'west'):
        for hung in ('false', 'true'):
            variants['facing=%s,hung=%s' % (facing, hung)] = {'model': 'concertstage:block/csc_fixture_particle'}
    put(os.path.join(A, 'blockstates', fid + '.json'), {'variants': variants})

def obj_item(model, gui_scale, gui_y, gui_ty=0.0, world=1.0):
    s = gui_scale
    w = gui_scale * world          # held / dropped / framed: at the size drawn in the world (rev. 2)
    return {'loader': 'forge:obj', 'model': 'concertstage:models/lightshow/%s.obj' % model, 'flip_v': True, 'automatic_culling': False,
            'shade_quads': True, 'textures': {'palette': PALETTE, 'particle': PALETTE}, 'render_type': 'minecraft:cutout', 'gui_light': 'side',
            'display': {
                'gui': {'rotation': [25, gui_y, 0], 'translation': [0, gui_ty, 0], 'scale': [s, s, s]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [w * 0.5, w * 0.5, w * 0.5]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [w * 0.9, w * 0.9, w * 0.9]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [w * 0.45, w * 0.45, w * 0.45]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [w * 0.5, w * 0.5, w * 0.5]},
                'firstperson_lefthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [w * 0.5, w * 0.5, w * 0.5]}}}

# the fixtures face north (-Z) in their models: the inventory shows their front three-quarter
SCALE = {'csc_vortex_x1': 1.45, 'csc_lumora_w7': 1.6, 'csc_lumora_w19': 1.3, 'csc_pulsestrike_m': 1.6}
WORLD = {'csc_vortex_x1': 1.95, 'csc_lumora_w7': 1.90, 'csc_lumora_w19': 2.10, 'csc_pulsestrike_m': 2.60}   # = FixtureProfile.renderScale
for fid in FIXTURES:
    put(os.path.join(A, 'models', 'item', fid + '.json'), obj_item(fid, SCALE[fid], 30, 2.5, WORLD[fid]))

# CSC LUMICORE X1: static OBJ desk (2 m wide, operator on +Z when facing=south), live screens drawn by its renderer
put(os.path.join(A, 'models', 'block', 'csc_lumicore_x1.json'), {
    'loader': 'forge:obj', 'model': 'concertstage:models/lightshow/csc_lumicore_x1.obj', 'flip_v': True, 'automatic_culling': False,
    'shade_quads': True, 'textures': {'palette': PALETTE, 'particle': PALETTE}, 'render_type': 'minecraft:cutout'})
put(os.path.join(A, 'blockstates', 'csc_lumicore_x1.json'), {'variants': {
    'facing=south': {'model': 'concertstage:block/csc_lumicore_x1'},
    'facing=west': {'model': 'concertstage:block/csc_lumicore_x1', 'y': 90},
    'facing=north': {'model': 'concertstage:block/csc_lumicore_x1', 'y': 180},
    'facing=east': {'model': 'concertstage:block/csc_lumicore_x1', 'y': 270}}})
desk_item = obj_item('csc_lumicore_x1', 0.42, 200, 1.0)
put(os.path.join(A, 'models', 'item', 'csc_lumicore_x1.json'), desk_item)

# patch tool: flat icon
put(os.path.join(A, 'models', 'item', 'csc_light_patch_tool.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'concertstage:item/csc_light_patch_tool'}})
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()
body, edge, screen, led, beam = (34, 37, 43, 255), (88, 94, 104, 255), (53, 201, 232, 255), (176, 124, 255, 255), (176, 124, 255, 150)
for y in range(5, 15):                      # handle + body (diagonal tool)
    for x in range(3, 10):
        if 5 <= y <= 9 or (x >= 4 and x <= 7):
            px[x, y] = body
for y in range(5, 10):
    px[3, y] = edge; px[9, y] = edge
for x in range(3, 10):
    px[x, 5] = edge
for y in range(6, 9):
    for x in range(4, 9):
        px[x, y] = screen
px[5, 7] = (232, 234, 237, 255); px[7, 7] = (232, 234, 237, 255)
for y in range(10, 15):
    px[4, y] = edge
px[8, 4] = led; px[9, 3] = beam; px[10, 2] = beam; px[11, 1] = (176, 124, 255, 90)
px[6, 11] = (76, 208, 138, 255)            # status LED
os.makedirs(os.path.join(A, 'textures', 'item'), exist_ok=True)
img.save(os.path.join(A, 'textures', 'item', 'csc_light_patch_tool.png'))

# loot: each block gives itself back
for bid in FIXTURES + ['csc_lumicore_x1']:
    put(os.path.join(D, 'loot_tables', 'blocks', bid + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': 'concertstage:' + bid}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

def shaped(name, pattern, key, count=1):
    put(os.path.join(D, 'recipes', name + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'redstone', 'pattern': pattern,
        'key': {k: {'item': v} for k, v in key.items()}, 'result': {'item': 'concertstage:' + name, 'count': count}})

shaped('csc_vortex_x1', [' I ', 'GLG', 'IRI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'L': 'minecraft:end_rod', 'R': 'minecraft:redstone'})
shaped('csc_lumora_w7', [' I ', 'GLG', 'IRI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'L': 'minecraft:glowstone', 'R': 'minecraft:redstone'})
shaped('csc_lumora_w19', ['IGI', 'GLG', 'IRI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'L': 'minecraft:glowstone', 'R': 'minecraft:redstone_block'})
shaped('csc_pulsestrike_m', ['GGG', 'LRL', 'I I'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'L': 'minecraft:sea_lantern', 'R': 'minecraft:redstone'})
shaped('csc_lumicore_x1', ['GCG', 'RQR', 'III'], {'G': 'minecraft:black_stained_glass_pane', 'C': 'minecraft:comparator', 'R': 'minecraft:redstone',
                                                  'Q': 'minecraft:repeater', 'I': 'minecraft:iron_ingot'})
shaped('csc_light_patch_tool', ['  G', ' R ', 'I  '], {'G': 'minecraft:glowstone_dust', 'R': 'minecraft:redstone', 'I': 'minecraft:iron_ingot'})

# breakable with a pickaxe, like the other CSC metal blocks
tag = os.path.join(ROOT, 'data', 'minecraft', 'tags', 'blocks', 'mineable', 'pickaxe.json')
t = json.load(open(tag))
for bid in FIXTURES + ['csc_lumicore_x1']:
    if 'concertstage:' + bid not in t['values']: t['values'].append('concertstage:' + bid)
json.dump(t, open(tag, 'w'), indent=1)
print('lighting assets written')
