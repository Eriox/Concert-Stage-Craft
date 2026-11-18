"""CSC 1.3.0 - LiveVision & Dynamic Truss: exports the supplied Blender 5.3 models to the game mesh format of CSC.

Run in Blender (batch, nothing is saved, the .blend files are only read):
    blender -b --factory-startup --python export_vision_blender.py -- <folder with the .blend files> <output folder>

For every file it writes <output>/<id>.json:
- pivots: the empties with csc_role = pivot (ROOT, PAN, TILT, BOOM...), their parent and rest position (game frame);
- materials: Principled base colour (linear -> sRGB), metallic, roughness, emission colour / strength;
- lods[0..3]: for each pivot, the triangles of every LOD collection (LOD0 = MAIN + MOVING_PARTS + CONNECTORS + ACCESSORIES),
  expressed in the frame of the pivot they hang from, grouped by material: positions (float32), corner normals (int8),
  and the 0-1 UV of LED_SURFACE / DISPLAY_DYNAMIC (the video surfaces), base64 little-endian;
- points: attach / emission / lens empties (pivot, position, direction of their local +Z);
- stats: objects, triangles per LOD, materials, bounding box (game frame) per LOD.
The parametric truss files (Geometry Nodes objects) are measured but their PARAMETRIC object is not exported (the game builds the
curved trusses itself with the same constants, see CurvedTrussGeometry.java).

Game frame (as the 1.2.0 fixtures): mc = (-x, z, y) - Y up, the front of the model (Blender -Y) towards north (-Z).
"""
import base64
import json
import math
import struct
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

IDS = {
    'CSC_LED_Panel_P1': 'led_panel_p1', 'CSC_LED_Accessories': 'led_accessories', 'CSC_Vision_Processor_VP1': 'vision_processor_vp1',
    'CSC_CAM_PTZ_P1': 'cam_ptz_p1', 'CSC_Broadcast_B1': 'broadcast_b1', 'CSC_Crane_C1': 'crane_c1', 'CSC_Remote_R1': 'remote_r1',
    'CSC_Curved_TriTruss': 'curved_tritruss', 'CSC_Curved_QuadTruss': 'curved_quadtruss', 'CSC_Truss_Circular_Rework': 'truss_circular_rework',
}
VIDEO_SURFACES = ('LED_SURFACE', 'DISPLAY_DYNAMIC')
TRUSS = ('CSC_Curved_TriTruss', 'CSC_Curved_QuadTruss', 'CSC_Truss_Circular_Rework')


def mc(v):
    return [-v[0], v[2], v[1]]


def srgb(c):
    c = max(0.0, min(1.0, c))
    return 12.92 * c if c <= .0031308 else 1.055 * c ** (1 / 2.4) - .055


def hexrgb(c):
    return ''.join('%02x' % max(0, min(255, round(srgb(x) * 255))) for x in c[:3])


def wm(o):
    # world matrix from the parent chain (the LOD1-3 collections are hidden: their matrix_world is not evaluated)
    m = o.matrix_basis.copy()
    return wm(o.parent) @ o.matrix_parent_inverse @ m if o.parent else m


def pivot_of(o, pivots):
    p = o.parent
    while p is not None:
        if p.name in pivots:
            return p.name
        p = p.parent
    return 'ROOT' if 'ROOT' in pivots else None


def lod_of(o):
    if 'csc_lod' in o.keys():
        return int(o['csc_lod'])
    for c in o.users_collection:
        n = c.name.split('.')[0]
        if n.startswith('LOD') and n[3:].isdigit():
            return int(n[3:])
    return 0


def in_collection(o, name):
    def walk(c):
        if c.name.split('.')[0] == name:
            return True
        return False
    for c in o.users_collection:
        if walk(c):
            return True
    # parent collections
    for top in bpy.data.collections:
        if top.name.split('.')[0] == name and any(o.name in ch.all_objects for ch in [top]):
            return True
    return False


def material_info(m):
    info = dict(name=m.name if m else 'none', rgb='0a0a0a', metal=0.0, rough=0.5, emit=None, strength=0.0)
    if m is None:
        return info
    c = m.diffuse_color[:3]
    if m.use_nodes and m.node_tree:
        b = next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if b:
            c = b.inputs['Base Color'].default_value[:3]
            info['metal'] = round(float(b.inputs['Metallic'].default_value), 3)
            info['rough'] = round(float(b.inputs['Roughness'].default_value), 3)
            try:
                e = b.inputs['Emission Color'].default_value[:3]
                s = float(b.inputs['Emission Strength'].default_value)
                if s > 0 and any(x > 1e-4 for x in e) and not all(abs(x - 1.0) < 1e-4 for x in e):
                    info['emit'] = hexrgb(e)
                    info['strength'] = round(s, 3)
            except KeyError:
                pass
    info['rgb'] = hexrgb(c)
    return info


def b64(fmt, values):
    return base64.b64encode(struct.pack('<%d%s' % (len(values), fmt), *values)).decode('ascii')


def export_file(path, out):
    bpy.ops.wm.open_mainfile(filepath=str(path), load_ui=False)
    stem = path.stem
    fid = IDS.get(stem, stem.lower())
    obs = bpy.data.objects
    pivots = {o.name: o for o in obs if o.type == 'EMPTY' and o.get('csc_role') == 'pivot'}
    if not pivots and 'ROOT' in obs:
        pivots = {'ROOT': obs['ROOT']}
    piv_world = {n: wm(o) for n, o in pivots.items()}
    data = dict(id=fid, source=path.name, frame='mc = (-x, z, y), metres, front north', pivots={}, materials=[], lods=[{}, {}, {}, {}],
                surfaces={}, points={}, stats=dict(objects=0, tris=[0, 0, 0, 0], bbox=[None, None, None, None], parametric=[]))
    for n, o in pivots.items():
        par = pivot_of(o, pivots) if o.parent else None
        data['pivots'][n] = dict(parent=par, pos=[round(x, 5) for x in mc(piv_world[n].translation)])
    mats, mat_index = [], {}

    def mat_id(m):
        key = m.name if m else None
        if key not in mat_index:
            mat_index[key] = len(mats)
            mats.append(material_info(m))
        return mat_index[key]

    truss = stem in TRUSS
    for o in obs:
        if o.type == 'EMPTY':
            role = o.get('csc_role')
            if role in ('MOUNT', 'EMIT') or o.name in ('LENS_AXIS',) or o.name.startswith(('MOUNT_', 'EMIT_', 'CONN_', 'CENTER_')):
                w = wm(o)
                pv = pivot_of(o, pivots)
                local = piv_world[pv].inverted() @ w if pv else w
                z = (local.to_3x3() @ Vector((0, 0, 1))).normalized()
                data['points'][o.name] = dict(pivot=pv, pos=[round(x, 5) for x in mc(local.translation)], dir=[round(x, 4) for x in mc(z)], role=role)
            continue
        if o.type != 'MESH':
            continue
        cols = [c.name.split('.')[0] for c in o.users_collection]
        if 'PARAMETRIC' in cols or o.modifiers.get('CSC_Parametric'):
            data['stats']['parametric'].append(o.name)
            continue
        if 'STUDIO' in cols:
            continue
        lod = lod_of(o)
        if lod > 3:
            continue
        me = o.data
        me.calc_loop_triangles()
        pv = pivot_of(o, pivots)
        local = (piv_world[pv].inverted() @ wm(o)) if pv else wm(o)
        nmat = local.to_3x3().inverted().transposed()
        try:
            cn = me.corner_normals
            corner_n = lambda li: cn[li].vector
        except AttributeError:
            corner_n = lambda li: me.loops[li].normal
        uvl = me.uv_layers.active
        surface = o.name.split('.')[0] in VIDEO_SURFACES or any(o.name.startswith(s) for s in VIDEO_SURFACES)
        groups = {}
        bb = data['stats']['bbox'][lod]
        for t in me.loop_triangles:
            mi = mat_id(o.material_slots[t.material_index].material if t.material_index < len(o.material_slots) else None)
            g = groups.setdefault(mi, dict(v=[], n=[], uv=[]))
            for li, vi in zip(t.loops, t.vertices):
                p = mc(local @ me.vertices[vi].co)
                g['v'].extend(round(x, 5) for x in p)
                nn = (nmat @ corner_n(li)).normalized()
                g['n'].extend(max(-127, min(127, round(x * 127))) for x in mc(nn))
                if surface and uvl:
                    u, v = uvl.data[li].uv
                    g['uv'].extend((round(u, 5), round(v, 5)))
                wp = mc(piv_world[pv] @ (local @ me.vertices[vi].co)) if pv else p
                if bb is None:
                    bb = [list(wp), list(wp)]
                else:
                    for k in range(3):
                        bb[0][k] = min(bb[0][k], wp[k]); bb[1][k] = max(bb[1][k], wp[k])
        data['stats']['bbox'][lod] = bb
        data['stats']['objects'] += 1
        data['stats']['tris'][lod] += len(me.loop_triangles)
        if truss:
            data['stats'].setdefault('truss_objects', []).append(dict(name=o.name, lod=lod, pivot=pv, tris=len(me.loop_triangles)))
            continue                                     # the trusses are built by the game: only measured
        target = data['surfaces'] if surface else data['lods'][lod].setdefault(pv, [])
        for mi, g in groups.items():
            entry = dict(m=mi, count=len(g['v']) // 3, v=b64('f', g['v']), n=b64('b', g['n']))
            if surface:
                entry['uv'] = b64('f', g['uv'])
                entry['pivot'] = pv
                entry['lod'] = lod
                target.setdefault(o.name.split('.')[0], []).append(entry)
            else:
                target.append(entry)
    data['materials'] = mats
    for i in range(4):
        bb = data['stats']['bbox'][i]
        if bb:
            data['stats']['bbox'][i] = [[round(x, 4) for x in bb[0]], [round(x, 4) for x in bb[1]]]
    (out / (fid + '.json')).write_text(json.dumps(data, separators=(',', ':')), encoding='utf-8')
    return fid, data['stats']['tris'], len(mats), data['stats']['objects']


def main():
    argv = sys.argv[sys.argv.index('--') + 1:]
    src, out = Path(argv[0]), Path(argv[1])
    out.mkdir(parents=True, exist_ok=True)
    report = []
    for p in sorted(src.glob('*.blend')):
        try:
            fid, tris, nm, no = export_file(p, out)
            report.append('%s -> %s : tris LOD0-3 %s, %d materials, %d objects' % (p.name, fid, tris, nm, no))
        except Exception as e:  # noqa: BLE001
            import traceback
            report.append('%s FAILED %s\n%s' % (p.name, e, traceback.format_exc()))
    (out / 'export_report.txt').write_text('\n'.join(report) + '\n', encoding='utf-8')
    print('\n'.join(report))


main()
