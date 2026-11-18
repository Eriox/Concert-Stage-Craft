#!/usr/bin/env python3
"""1.2.0 remodel of the four historical fixtures (VORTEX X1, LUMORA W7, LUMORA W19, PULSESTRIKE M).

The four models are rebuilt in Blender 5.3 with the generator of the 13 new fixtures (source-assets/new_lights/Scripts) and
exported with the same backend (export_new_lights_blender.py). This script puts one such export in place:
- lightshow/<id>.json of the four fixtures and their entries of new_lights_export_report.json;
- the inventory models models/lightshow/<id>.obj (middle level of detail, neutral pose, palette texture);
- the colours they need, appended to lightshow/palette.json / textures/block/lightshow_palette.png (existing cells keep their place:
  the LUMICORE X1 desk model refers to them).
Zone order, zone kinds and tilt axis heights are checked against the values the Java tables rely on.

Usage: python3 -I integrate_remodelled_fixtures.py <export folder holding lightshow/*.json> [repo root]
"""
import json, math, os, shutil, sys

IDS = {'csc_vortex_x1': (0, 0, 0, 0.361), 'csc_lumora_w7': (0, 0, 19, 0.322), 'csc_lumora_w19': (0, 0, 37, 0.396),
       'csc_pulsestrike_m': (14, 28, 0, 0.222)}          # rgb, white, cell zones and tilt axis height of FixtureProfile
CELLS, SIZE = 16, 64


def palette_uv(i):
    return ((i % CELLS + 0.5) / CELLS, 1.0 - (i // CELLS + 0.5) / CELLS)


def write_obj(d, colors, path):
    q = d['quant']; pan = d['pivots']['pan'] or [0, 0, 0]; tilt = d['pivots']['tilt']
    off = {'base': (0, 0, 0), 'yoke': pan, 'head': [pan[i] + tilt[i] for i in range(3)]}
    lines = ['mtllib lightshow.mtl', 'usemtl palette']; n = 0
    for part in ('base', 'yoke', 'head'):
        if part not in d['lod_far']: continue
        o = off[part]
        for col, arr in d['lod_far'][part]['static'].items():
            u, v = palette_uv(colors.index(col))
            for t in range(0, len(arr), 9):
                p = [(arr[t + 3 * k] / q + o[0] + 0.5, arr[t + 3 * k + 1] / q + o[1], arr[t + 3 * k + 2] / q + o[2] + 0.5) for k in range(3)]
                a = [p[1][i] - p[0][i] for i in range(3)]; b = [p[2][i] - p[0][i] for i in range(3)]
                nx, ny, nz = a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
                nl = math.sqrt(nx * nx + ny * ny + nz * nz)
                if nl < 1e-12: continue
                for x in p:
                    lines.append('v %.4f %.4f %.4f' % x); lines.append('vt %.5f %.5f' % (u, v)); lines.append('vn %.4f %.4f %.4f' % (nx / nl, ny / nl, nz / nl))
                lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d' % (n + 1, n + 1, n + 1, n + 2, n + 2, n + 2, n + 3, n + 3, n + 3)); n += 3
    open(path, 'w').write('\n'.join(lines) + '\n')
    return n // 3


def main():
    src = sys.argv[1]; root = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
    res = os.path.join(root, 'common/src/main/resources/assets/concertstage')
    new_rep = json.load(open(os.path.join(src, 'lightshow/new_lights_export_report.json')))
    rp = os.path.join(res, 'lightshow/new_lights_export_report.json'); rep = json.load(open(rp))
    pal_path = os.path.join(res, 'lightshow/palette.json'); pal = json.load(open(pal_path)); colors = pal['colors']
    for fid, (rgb, white, cell, pivot) in IDS.items():
        d = json.load(open(os.path.join(src, 'lightshow', fid + '.json')))
        kinds = [z['kind'] for z in (d['zones'][str(i)] for i in range(len(d['zones'])))]
        want = ['rgb'] * rgb + ['white'] * white + ['cell'] * max(cell, 0 if rgb or white else 1)
        assert kinds == want, (fid, kinds)
        pan = d['pivots']['pan'] or [0, 0, 0]
        assert abs(pan[1] + d['pivots']['tilt'][1] - pivot) < 1e-4, (fid, d['pivots'])
        if cell > 1:                                      # last cell = the central flower lens, on the axis
            c = d['zones'][str(cell - 1)]['centre']; assert math.hypot(c[0], c[1]) < 1e-3, fid
        counts = {k: sum(len(a) // 9 for p in d[k].values() for a in p['static'].values()) for k in ('parts', 'lod_far', 'lod_low')}
        assert counts == new_rep[fid]['triangles'] and counts['parts'] > counts['lod_far'] > counts['lod_low'] > 0, fid
        shutil.copyfile(os.path.join(src, 'lightshow', fid + '.json'), os.path.join(res, 'lightshow', fid + '.json'))
        rep[fid] = new_rep[fid]
        for part in d['lod_far'].values():
            for col in part['static']:
                if col not in colors: colors.append(col)
        assert len(colors) <= CELLS * CELLS, len(colors)
        tris = write_obj(d, colors, os.path.join(res, 'models/lightshow', fid + '.obj'))
        print(fid, counts, 'zones', len(kinds), 'inventory triangles', tris)
    json.dump(rep, open(rp, 'w'), indent=1)
    json.dump(pal, open(pal_path, 'w'))
    from PIL import Image
    img = Image.new('RGBA', (SIZE, SIZE), (255, 255, 255, 255)); cell = SIZE // CELLS
    for i, c in enumerate(colors):
        rgb = tuple(int(c[j:j + 2], 16) for j in (0, 2, 4)) + (255,)
        for x in range(cell):
            for y in range(cell): img.putpixel((i % CELLS * cell + x, i // CELLS * cell + y), rgb)
    img.save(os.path.join(res, 'textures/block/lightshow_palette.png'))
    print('palette', len(colors), 'colours')


if __name__ == '__main__':
    main()
