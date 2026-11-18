"""Ressources du système son 1.1.0 (relancer remplace tout) : états de blocs, modèles, textures de la console et des deux outils,
sounds.json, table de butin, recettes, étiquette pioche. Les maillages des enceintes viennent de make_speakers.py."""
import json, pathlib, random
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'common/src/main/resources'
A = ROOT / 'assets/concertstage'
D = ROOT / 'data/concertstage'
SPEAKERS = ['csc_veyra_a1', 'csc_veyra_a2', 'csc_veyra_b2', 'csc_noryx_a1', 'csc_noryx_a2', 'csc_noryx_b1']


def dump(p, o):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(o, indent=1))


def console_texture():
    """64 x 64: chassis (0-16), fader panel (16-32), meter bridge screen (32-48), end cheek (48-64); second row: knobs."""
    rnd = random.Random(7)
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 255))
    px = im.load()
    for y in range(64):
        for x in range(64):
            n = rnd.randint(-3, 3)
            px[x, y] = (27 + n, 29 + n, 33 + n, 255)
    # fader panel: 7 strips, slots and caps, a coloured cap per group
    caps = [(232, 234, 237), (91, 140, 255), (229, 72, 77), (176, 124, 255), (76, 208, 138), (242, 179, 61), (53, 201, 232)]
    for s in range(7):
        x = 17 + s * 2
        for y in range(2, 15):
            px[x, y] = (10, 11, 13, 255)
        cy = 4 + (s * 5) % 9
        px[x, cy] = caps[s] + (255,)
        px[x, cy + 1] = caps[s] + (255,)
    for x in range(16, 32):
        px[x, 0] = (60, 64, 70, 255)
    # screen of the meter bridge: dark blue with meter bars
    for y in range(1, 15):
        for x in range(33, 47):
            px[x, y] = (12, 22, 38, 255)
    for b in range(6):
        h = 3 + (b * 7) % 9
        for y in range(14 - h, 14):
            c = (76, 208, 138) if y > 8 else (242, 179, 61) if y > 5 else (229, 72, 77)
            px[34 + b * 2, y] = c + (255,)
    for x in range(33, 47):
        px[x, 2] = (53, 201, 232, 255)
    # end cheek: matte black with a brushed edge
    for y in range(16):
        for x in range(48, 64):
            px[x, y] = (16, 17, 19, 255) if x > 49 else (70, 74, 80, 255)
    # knobs row (0-16, 16-32): rows of small knobs
    for ky in (18, 22, 26, 30):
        for kx in range(1, 16, 2):
            px[kx, ky] = (190, 194, 200, 255)
    return im


def tool_model(accent_uv):
    m = json.loads((A / 'models/item/ring_truss_tool.json').read_text())
    for e in m['elements']:
        for f in e['faces'].values():
            if f['uv'] == [12, 4, 16, 8]: f['uv'] = list(accent_uv)
    return m


def box(fr, to, tex, uv=None):
    faces = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        u = uv.get(d, uv['all']) if uv else [0, 0, 4, 4]
        faces[d] = {'uv': u, 'texture': tex}
    return {'from': fr, 'to': to, 'faces': faces}


def main():
    (A / 'textures/block').mkdir(parents=True, exist_ok=True)
    console_texture().save(A / 'textures/block/audio_console.png')
    C, F, S, E, K = [0, 0, 4, 4], [4, 0, 8, 4], [8, 0, 12, 4], [12, 0, 16, 4], [0, 4, 4, 8]
    elements = [
        box([1, 0, 1], [15, 9, 15], '#t', {'all': C, 'north': K}),                         # body, knobs on the operator side
        box([1, 9, 1.5], [15, 10, 10.5], '#t', {'all': C, 'up': F}),                       # fader surface
        box([1, 9, 10.5], [15, 15, 15], '#t', {'all': C, 'north': S}),                     # meter bridge with its screen
        box([0, 0, 1], [1, 11, 15], '#t', {'all': E}),                                     # end cheeks
        box([15, 0, 1], [16, 11, 15], '#t', {'all': E}),
    ]
    dump(A / 'models/block/csc_audio_console.json', {'parent': 'minecraft:block/block', 'textures': {'t': 'concertstage:block/audio_console',
        'particle': 'concertstage:block/audio_console'}, 'elements': elements, 'render_type': 'minecraft:cutout'})
    dump(A / 'models/item/csc_audio_console.json', {'parent': 'concertstage:block/csc_audio_console'})
    dump(A / 'blockstates/csc_audio_console.json', {'variants': {f'facing={f}': {'model': 'concertstage:block/csc_audio_console', **({'y': y} if y else {})}
        for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    # speaker head block: drawn by its renderer; the model only gives the breaking particles
    dump(A / 'models/block/csc_speaker.json', {'textures': {'particle': 'concertstage:block/speakers_atlas'}})
    dump(A / 'blockstates/csc_speaker.json', {'variants': {'hanging=true': {'model': 'concertstage:block/csc_speaker'},
                                                          'hanging=false': {'model': 'concertstage:block/csc_speaker'}}})
    # tools: the tablet of the other controllers, orange (array) / purple (link) accents
    dump(A / 'models/item/csc_array_tool.json', tool_model([4, 8, 8, 12]))
    dump(A / 'models/item/csc_audio_link_tool.json', tool_model([0, 12, 4, 16]))
    # sound
    dump(A / 'sounds.json', {'speaker_stream': {'sounds': [{'name': 'concertstage:empty', 'stream': True}],
                                               'subtitle': 'subtitles.concertstage.speaker_stream'}})
    # loot (the console; the arrays give their cabinets back themselves)
    dump(D / 'loot_tables/blocks/csc_audio_console.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': 'concertstage:csc_audio_console'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    # recipes
    def shaped(name, pattern, key, count=1):
        dump(D / f'recipes/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'redstone', 'pattern': pattern,
            'key': {k: {'item': v} for k, v in key.items()}, 'result': {'item': f'concertstage:{name}', 'count': count}})
    shaped('csc_veyra_a1', ['III', 'NJN', 'III'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:note_block', 'J': 'minecraft:jukebox'}, 2)
    shaped('csc_veyra_a2', ['III', 'NJN', 'ICI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:note_block', 'J': 'minecraft:jukebox', 'C': 'minecraft:copper_ingot'}, 2)
    shaped('csc_veyra_b2', ['INI', 'NJN', 'IBI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:note_block', 'J': 'minecraft:jukebox', 'B': 'minecraft:iron_block'})
    shaped('csc_noryx_a1', ['ICI', 'NJN', 'ICI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:note_block', 'J': 'minecraft:jukebox', 'C': 'minecraft:copper_ingot'}, 2)
    shaped('csc_noryx_a2', ['CCC', 'NJN', 'ICI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:note_block', 'J': 'minecraft:jukebox', 'C': 'minecraft:copper_ingot'}, 2)
    shaped('csc_noryx_b1', ['ICI', 'NJN', 'IBI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:note_block', 'J': 'minecraft:jukebox', 'C': 'minecraft:copper_ingot', 'B': 'minecraft:iron_block'})
    shaped('csc_audio_console', ['RQR', 'NJN', 'III'], {'R': 'minecraft:redstone', 'Q': 'minecraft:comparator', 'N': 'minecraft:note_block', 'J': 'minecraft:jukebox', 'I': 'minecraft:iron_ingot'})
    shaped('csc_array_tool', [' A ', 'IRI', ' I '], {'A': 'minecraft:amethyst_shard', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'})
    shaped('csc_audio_link_tool', [' L ', 'IRI', ' I '], {'L': 'minecraft:lightning_rod', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'})
    # pickaxe
    tag = ROOT / 'data/minecraft/tags/blocks/mineable/pickaxe.json'
    t = json.loads(tag.read_text())
    for b in ('concertstage:csc_speaker', 'concertstage:csc_audio_console'):
        if b not in t['values']: t['values'].append(b)
    tag.write_text(json.dumps(t, indent=2))
    print('ok')


if __name__ == '__main__':
    main()
