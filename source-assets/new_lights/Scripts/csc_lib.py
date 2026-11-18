# CSC 1.2.0 - Lightshow Revolution : bibliotheque de modelisation procedurale (Blender 5.x)
# Unites : 1 unite Blender = 1 m = 1 bloc Minecraft. Z haut, face avant / faisceau = -Y.
import bpy, bmesh, os
from mathutils import Matrix, Vector
from math import sin, cos, pi, radians, copysign, sqrt, atan

_F = {'-Y': ((1, 0, 0), (0, 0, 1), (0, -1, 0)), 'Y': ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
      'X': ((0, 1, 0), (0, 0, 1), (1, 0, 0)), '-X': ((0, -1, 0), (0, 0, 1), (-1, 0, 0)),
      'Z': ((1, 0, 0), (0, 1, 0), (0, 0, 1)), '-Z': ((1, 0, 0), (0, -1, 0), (0, 0, -1))}


def basis(axis='Z', loc=(0, 0, 0)):
    cx, cy, cz = _F[axis]
    return Matrix(((cx[0], cy[0], cz[0], loc[0]), (cx[1], cy[1], cz[1], loc[1]),
                   (cx[2], cy[2], cz[2], loc[2]), (0, 0, 0, 1)))


# nom: (couleur, metallic, roughness[, emission, force])
_MATS = {
    'body': ((.028, .030, .033), 0.0, .58), 'body2': ((.050, .052, .056), .55, .42),
    'panel': ((.075, .077, .082), .8, .38), 'plastic': ((.020, .020, .022), 0, .70),
    'rubber': ((.012, .012, .012), 0, .92), 'black': ((.004, .004, .004), 0, .5),
    'vent': ((.008, .008, .009), .3, .6), 'alu': ((.60, .61, .63), 1, .32),
    'aludark': ((.12, .125, .13), 1, .38), 'steel': ((.42, .43, .44), 1, .28),
    'gold': ((.9, .68, .25), 1, .25), 'accent': ((.95, .36, .04), 1, .34),
    'reflector': ((.92, .92, .90), 1, .10), 'lens': ((.012, .014, .017), 0, .04),
    'led': ((.10, .10, .105), 0, .08), 'ledrefl': ((.80, .80, .78), 1, .16),
    'chip': ((.85, .66, .12), 0, .45), 'diffuser': ((.78, .79, .80), 0, .55),
    'screen': ((.01, .02, .04), 0, .15, (.25, .55, 1.0), 1.5), 'label': ((.75, .75, .73), 0, .6),
    'ledgreen': ((.02, .2, .03), 0, .3, (.1, 1, .2), 2.0), 'ledred': ((.2, .02, .02), 0, .3, (1, .08, .05), 2.0),
    'red': ((.55, .03, .02), 0, .4), 'conn_blue': ((.02, .10, .45), 0, .5),
    'conn_grey': ((.35, .36, .37), 0, .5), 'floor': ((.30, .30, .31), 0, .85),
}
LIT = {'led': ((.45, .75, 1.0), 2.6), 'ledrefl': ((1.0, .62, .30), 2.2), 'diffuser': ((1.0, .30, .75), 2.5),
       'lens': ((.80, .90, 1.0), 2.5), 'chip': ((1.0, .8, .5), 6.0)}


def mat(key):
    name = 'CSC_' + key
    m = bpy.data.materials.get(name)
    if m: return m
    d = _MATS[key]
    m = bpy.data.materials.new(name)
    try: m.use_nodes = True
    except Exception: pass
    b = None
    for n in m.node_tree.nodes:
        if n.bl_idname == 'ShaderNodeBsdfPrincipled': b = n
    if b:
        b.inputs['Base Color'].default_value = (d[0][0], d[0][1], d[0][2], 1)
        b.inputs['Metallic'].default_value = d[1]
        b.inputs['Roughness'].default_value = d[2]
        if key in ('lens', 'led'):
            try: b.inputs['Coat Weight'].default_value = 1.0; b.inputs['Coat Roughness'].default_value = .03
            except Exception: pass
        if len(d) > 3:
            b.inputs['Emission Color'].default_value = (d[3][0], d[3][1], d[3][2], 1)
            b.inputs['Emission Strength'].default_value = d[4]
    m.diffuse_color = (d[0][0], d[0][1], d[0][2], 1)
    return m


def set_lit(on):
    for k, (c, st) in LIT.items():
        m = bpy.data.materials.get('CSC_' + k)
        if not m: continue
        for n in m.node_tree.nodes:
            if n.bl_idname == 'ShaderNodeBsdfPrincipled':
                n.inputs['Emission Color'].default_value = (c[0], c[1], c[2], 1) if on else (0, 0, 0, 1)
                n.inputs['Emission Strength'].default_value = st if on else 0.0


class Fx:
    def __init__(s, name, lod):
        s.name = name; s.lod = lod; s.parts = {}; s.pivots = {}; s.emp = []; s.prims = []
        s.cur = ('BASE', 'ROOT'); s.cellkey = None; s.stack = [Matrix.Identity(4)]; s.info = {}; s.ncells = 0

    @property
    def M(s): return s.stack[-1]
    def push(s, axis='Z', loc=(0, 0, 0)): s.stack.append(s.M @ basis(axis, loc))
    def pop(s): s.stack.pop()
    def pivot(s, name, parent, loc): s.pivots[name] = (parent, Vector(loc))
    def to(s, group, pivot=None): s.cur = (group, pivot or s.cur[1]); s.cellkey = None
    def grp(s, group): s.to(group)
    def cell(s, name, zone=0):
        s.cellkey = ('LED_CELLS', s.cur[1], name, zone); s.ncells += 1
    def endcell(s): s.cellkey = None
    def focus(s, c, r): s.info['optics'] = (Vector(c), r)
    def sg(s, n): return n if s.lod == 0 else (max(8, n // 2) if s.lod == 1 else max(6, n // 3))

    def _part(s):
        if s.lod > 0: key = ('LOD%d' % s.lod, s.cur[1], None, 0)
        elif s.cellkey: key = s.cellkey
        else: key = (s.cur[0], s.cur[1], None, 0)
        p = s.parts.get(key)
        if p is None: p = s.parts[key] = {'bm': bmesh.new(), 'mats': []}
        return p

    def _add(s, t, m, loc, axis, lod, smooth, rot=None, Mx=None):
        if s.lod > lod or len(t.verts) == 0:
            t.free(); return
        if Mx is not None: M = s.M @ Mx
        else:
            M = s.M @ Matrix.Translation(Vector(loc))
            if rot: M = M @ Matrix.Rotation(rot[0], 4, rot[1])
            M = M @ basis(axis)
        bmesh.ops.transform(t, matrix=M, verts=t.verts[:])
        bmesh.ops.recalc_face_normals(t, faces=t.faces[:])
        for f in t.faces: f.smooth = bool(smooth) and len(f.verts) <= 4
        cs = [v.co for v in t.verts]
        s.prims.append((Vector((min(c.x for c in cs), min(c.y for c in cs), min(c.z for c in cs))),
                        Vector((max(c.x for c in cs), max(c.y for c in cs), max(c.z for c in cs)))))
        p = s._part()
        if m not in p['mats']: p['mats'].append(m)
        mi = p['mats'].index(m)
        tmp = bpy.data.meshes.new('_t'); t.to_mesh(tmp); t.free()
        bm = p['bm']; n0 = len(bm.faces); bm.from_mesh(tmp); bpy.data.meshes.remove(tmp)
        bm.faces.ensure_lookup_table()
        for i in range(n0, len(bm.faces)): bm.faces[i].material_index = mi

    # ---------- primitives ----------
    def box(s, size, loc, m='body', bevel=.003, lod=1, axis='Z', rot=None):
        if s.lod > lod: return
        t = bmesh.new(); bmesh.ops.create_cube(t, size=1.0)
        for v in t.verts: v.co.x *= size[0]; v.co.y *= size[1]; v.co.z *= size[2]
        if bevel and s.lod == 0:
            try: bmesh.ops.bevel(t, geom=t.edges[:], offset=min(bevel, min(size) * .45), segments=1, affect='EDGES', profile=.5)
            except Exception: pass
        s._add(t, m, loc, axis, lod, False, rot)

    def cyl(s, r, d, loc, m='body', axis='Z', segs=24, r2=None, lod=1, smooth=True, rot=None):
        if s.lod > lod: return
        t = bmesh.new()
        bmesh.ops.create_cone(t, cap_ends=True, cap_tris=False, segments=s.sg(segs), radius1=r,
                              radius2=r if r2 is None else r2, depth=d)
        s._add(t, m, loc, axis, lod, smooth, rot)

    def rod(s, a, b, r, m='steel', segs=10, lod=1):
        if s.lod > lod: return
        a = Vector(a); b = Vector(b); d = b - a
        t = bmesh.new()
        bmesh.ops.create_cone(t, cap_ends=True, cap_tris=False, segments=s.sg(segs), radius1=r, radius2=r, depth=d.length)
        Mx = Matrix.Translation((a + b) / 2) @ Vector((0, 0, 1)).rotation_difference(d.normalized()).to_matrix().to_4x4()
        s._add(t, m, None, None, lod, True, Mx=Mx)

    def handle(s, p0, p1, out, r=.008, m='steel', lod=1):
        p0 = Vector(p0); p1 = Vector(p1); o = Vector(out)
        s.rod(p0, p0 + o, r, m, lod=lod); s.rod(p1, p1 + o, r, m, lod=lod)
        e = (p1 - p0).normalized() * r
        s.rod(p0 + o - e, p1 + o + e, r, m, lod=lod)

    def lathe(s, prof, loc, m='body', axis='Z', segs=24, closed=False, lod=1, smooth=True):
        if s.lod > lod: return
        n = s.sg(segs); t = bmesh.new(); rings = []
        for (r, z) in prof:
            if r < 1e-6: rings.append([t.verts.new((0, 0, z))])
            else: rings.append([t.verts.new((r * cos(2 * pi * k / n), r * sin(2 * pi * k / n), z)) for k in range(n)])
        pairs = list(zip(rings, rings[1:]))
        if closed: pairs.append((rings[-1], rings[0]))
        for A, B in pairs:
            for k in range(n):
                k2 = (k + 1) % n
                try:
                    if len(A) == 1 and len(B) == 1: continue
                    if len(A) == 1: t.faces.new((A[0], B[k], B[k2]))
                    elif len(B) == 1: t.faces.new((A[k], A[k2], B[0]))
                    else: t.faces.new((A[k], A[k2], B[k2], B[k]))
                except ValueError: pass
        s._add(t, m, loc, axis, lod, smooth)

    def tube(s, r, ri, d, loc, m='body', axis='Z', segs=24, lod=1):
        s.lathe([(ri, -d / 2), (r, -d / 2), (r, d / 2), (ri, d / 2)], loc, m, axis, segs, True, lod, False)

    def lens(s, r, b, loc, axis='-Y', m='lens', segs=24, lod=1):
        R = (r * r + b * b) / (2 * b); k = 5 if s.lod == 0 else 3
        prof = [(r * (1 - i / k), sqrt(max(R * R - (r * (1 - i / k)) ** 2, 0)) - (R - b)) for i in range(k + 1)]
        s.lathe(prof, loc, m, axis, segs, False, lod, True)

    def loft(s, secs, m='body', loc=(0, 0, 0), segs=28, lod=1, smooth=True):
        if s.lod > lod: return
        n = s.sg(segs); t = bmesh.new(); rings = []
        for sec in secs:
            y, hw, hh, e = sec[0], sec[1], sec[2], sec[3]
            ring = []
            for k in range(n):
                a = 2 * pi * (k + .5) / n; c = cos(a); q = sin(a)
                ring.append(t.verts.new((hw * copysign(abs(c) ** (2 / e), c), y, hh * copysign(abs(q) ** (2 / e), q))))
            rings.append(ring)
        for A, B in zip(rings, rings[1:]):
            for k in range(n):
                k2 = (k + 1) % n
                t.faces.new((A[k], A[k2], B[k2], B[k]))
        t.faces.new(rings[0]); t.faces.new(rings[-1])
        s._add(t, m, loc, 'Z', lod, smooth)

    def prism(s, pts, d, loc, m='body', axis='X', lod=1, bevel=0):
        if s.lod > lod: return
        t = bmesh.new()
        A = [t.verts.new((u, v, -d / 2)) for u, v in pts]; B = [t.verts.new((u, v, d / 2)) for u, v in pts]
        n = len(pts)
        t.faces.new(A); t.faces.new(B)
        for k in range(n): t.faces.new((A[k], A[(k + 1) % n], B[(k + 1) % n], B[k]))
        if bevel and s.lod == 0:
            try: bmesh.ops.bevel(t, geom=t.edges[:], offset=bevel, segments=1, affect='EDGES', profile=.5)
            except Exception: pass
        s._add(t, m, loc, axis, lod, False)

    def bolts(s, pts, r=.004, axis='Z', m='steel'):
        for p in pts: s.cyl(r, .004, p, m, axis, 6, lod=0, smooth=False)

    def text(s, body, size, loc, axis='Z', m='label', lod=0):
        if s.lod > lod: return
        cu = bpy.data.curves.new('_txt', 'FONT'); cu.body = body; cu.size = size; cu.extrude = .0005; cu.resolution_u = 2
        cu.align_x = 'CENTER'; cu.align_y = 'CENTER'
        ob = bpy.data.objects.new('_txt', cu); bpy.context.scene.collection.objects.link(ob)
        bpy.context.view_layer.update()
        me = bpy.data.meshes.new_from_object(ob.evaluated_get(bpy.context.evaluated_depsgraph_get()))
        t = bmesh.new(); t.from_mesh(me)
        bpy.data.meshes.remove(me); bpy.data.objects.remove(ob); bpy.data.curves.remove(cu)
        s._add(t, m, loc, axis, lod, False)

    # ---------- composants (repere local : +Z = vers l'exterieur) ----------
    def display(s, w, h, loc=(0, 0, 0), buttons=4):
        x, y, z = loc
        s.box((w + .014, h + .014, .006), (x, y, z + .002), 'plastic', bevel=.002)
        s.box((w, h, .002), (x, y, z + .0055), 'screen', bevel=0)
        for i in range(buttons):
            s.cyl(.0055, .005, (x + w / 2 + .022 + (i % 2) * .017, y + (.5 - i // 2) * .018, z + .0025), 'rubber', segs=10, lod=0)

    def connectors(s, items, pitch=.036):
        n = len(items); s.box((n * pitch + .016, .052, .003), (0, 0, .0005), 'panel', bevel=0)
        for i, it in enumerate(items):
            x = (i - (n - 1) / 2) * pitch
            if it in ('pcin', 'pcout'):
                s.tube(.0155, .0115, .012, (x, 0, .007), 'conn_blue' if it == 'pcin' else 'conn_grey', segs=16)
                s.cyl(.0115, .004, (x, 0, .003), 'black', segs=16, lod=0)
            elif it == 'xlr':
                s.tube(.0125, .0095, .008, (x, 0, .005), 'steel', segs=16); s.cyl(.0095, .003, (x, 0, .003), 'black', segs=16, lod=0)
                for k in range(5):
                    a = pi * (.15 + k * .175)
                    s.cyl(.0011, .006, (x + .0055 * cos(a), -.002 + .0055 * sin(a), .006), 'gold', segs=6, lod=0)
            elif it == 'rj':
                s.tube(.0135, .0105, .008, (x, 0, .005), 'steel', segs=16); s.box((.013, .011, .004), (x, 0, .003), 'black', bevel=0, lod=0)
            elif it == 'key':
                s.cyl(.010, .012, (x, 0, .007), 'steel', segs=16); s.box((.002, .009, .003), (x, 0, .0135), 'black', bevel=0, lod=0)
            elif it == 'estop':
                s.cyl(.012, .008, (x, 0, .005), 'steel', segs=16); s.cyl(.015, .008, (x, 0, .013), 'red', segs=16)

    def vent(s, w, h, n=7):
        s.box((w, h, .003), (0, 0, .0005), 'vent', bevel=0)
        for i in range(n):
            s.box((w * .96, h / n * .45, .005), (0, (i - (n - 1) / 2) * h / n, .003), 'body2', bevel=0, lod=0, rot=(radians(20), 'X'))

    def fan(s, r):
        s.tube(r, r * .86, .012, (0, 0, .005), 'body2', segs=24); s.cyl(r * .87, .002, (0, 0, 0), 'vent', segs=24)
        s.cyl(r * .3, .008, (0, 0, .005), 'plastic', segs=12, lod=0)
        for k in range(7):
            a = 2 * pi * k / 7
            s.box((r * .56, r * .24, .002), (r * .56 * cos(a), r * .56 * sin(a), .005), 'plastic', bevel=0, lod=0, rot=(a, 'Z'))
        for q in (.45, .72): s.tube(r * q + .002, r * q - .002, .003, (0, 0, .0115), 'steel', segs=24, lod=0)
        for k in range(4): s.box((r * 1.9, .004, .002), (0, 0, .0115), 'steel', bevel=0, lod=0, rot=(k * pi / 4, 'Z'))

    def _emp(s, kind, name, loc, axis):
        if s.lod: return
        M = s.M @ basis(axis, loc)
        s.emp.append((kind, name, s.cur[1], M.to_translation(), M.to_3x3()))
    def emit(s, name, loc, axis='-Y'): s._emp('EMIT', name, loc, axis)
    def mount(s, name, loc, axis='-Z'): s._emp('MOUNT', name, loc, axis)
    def mark(s, name, loc, axis='-Y'): s._emp('MARK', name, loc, axis)

    # ---------- controles ----------
    def bbox(s):
        return (Vector((min(p[0].x for p in s.prims), min(p[0].y for p in s.prims), min(p[0].z for p in s.prims))),
                Vector((max(p[1].x for p in s.prims), max(p[1].y for p in s.prims), max(p[1].z for p in s.prims))))

    def islands(s, tol=.0015):
        P = s.prims; n = len(P); par = list(range(n))
        def f(i):
            while par[i] != i: par[i] = par[par[i]]; i = par[i]
            return i
        for i in range(n):
            a0, a1 = P[i]
            for j in range(i + 1, n):
                b0, b1 = P[j]
                if a0.x - tol <= b1.x and b0.x - tol <= a1.x and a0.y - tol <= b1.y and b0.y - tol <= a1.y and a0.z - tol <= b1.z and b0.z - tol <= a1.z:
                    ri, rj = f(i), f(j)
                    if ri != rj: par[ri] = rj
        comp = {}
        for i in range(n): comp.setdefault(f(i), []).append(i)
        cs = sorted(comp.values(), key=len, reverse=True)
        return len(cs), [[round(x, 3) for x in ((P[c[0]][0] + P[c[0]][1]) / 2)] for c in cs[1:6]]

    def finalize(s, top, piv):
        def sub(name):
            for c in top.children:
                if c.name.split('.')[0] == name: return c
            c = bpy.data.collections.new(name); top.children.link(c); return c
        if piv is None:
            piv = {}; rig = sub('PIVOTS')
            for n, (par, loc) in s.pivots.items():
                e = bpy.data.objects.new(n, None); e.empty_display_type = 'PLAIN_AXES'; e.empty_display_size = .06
                rig.objects.link(e)
                if par: e.parent = piv[par]; e.location = loc - s.pivots[par][1]
                else: e.location = loc
                e['csc_role'] = 'pivot'; piv[n] = e
        tris = 0
        for (group, pv, cname, zone), p in s.parts.items():
            bm = p['bm']
            bmesh.ops.translate(bm, vec=-s.pivots[pv][1], verts=bm.verts[:])
            name = cname or ('%s_%s' % (group, pv))
            me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
            for k in p['mats']: me.materials.append(mat(k))
            try: me.set_sharp_from_angle(angle=radians(38))
            except Exception: pass
            ob = bpy.data.objects.new(name, me); ob.parent = piv[pv]
            c = sub(group); c.objects.link(ob)
            if group.startswith('LOD'): c.hide_viewport = True; c.hide_render = True
            if cname: ob['csc_cell'] = cname; ob['csc_zone'] = zone
            ob['csc_lod'] = s.lod
            tris += sum(len(q.vertices) - 2 for q in me.polygons)
        if s.lod == 0:
            cn = {'EMIT': 'EMISSION_POINTS', 'MOUNT': 'MOUNT_POINTS', 'MARK': 'PREP_MARKERS'}
            for kind, name, pv, loc, R in s.emp:
                e = bpy.data.objects.new(name, None)
                e.empty_display_type = 'SPHERE' if kind == 'MARK' else 'SINGLE_ARROW'
                e.empty_display_size = .012 if kind == 'MARK' else .08
                e.parent = piv[pv]; e.location = loc - s.pivots[pv][1]; e.rotation_euler = R.to_euler()
                sub(cn[kind]).objects.link(e); e['csc_role'] = kind
        return piv, tris


# ---------- scene / studio / rendu ----------
def wipe():
    for o in list(bpy.data.objects): bpy.data.objects.remove(o)
    for coll in (bpy.data.meshes, bpy.data.curves, bpy.data.materials, bpy.data.cameras, bpy.data.lights,
                 bpy.data.collections, bpy.data.images, bpy.data.worlds):
        for b in list(coll): coll.remove(b)
    for l in list(bpy.data.libraries):
        try: bpy.data.libraries.remove(l)
        except Exception: pass


def look(ob, target):
    ob.rotation_euler = (Vector(target) - ob.location).to_track_quat('-Z', 'Y').to_euler()


def studio(floor=600):
    sc = bpy.context.scene
    st = bpy.data.collections.new('STUDIO'); sc.collection.children.link(st)
    me = bpy.data.meshes.new('STUDIO_Floor'); h = floor / 2
    me.from_pydata([(-h, -h, 0), (h, -h, 0), (h, h, 0), (-h, h, 0)], [], [(0, 1, 2, 3)]); me.materials.append(mat('floor'))
    st.objects.link(bpy.data.objects.new('STUDIO_Floor', me))
    for n, d, e in (('Key', (-1.2, -1.6, 2.2), 3.2), ('Fill', (2.0, -1.2, .9), 1.1), ('Rim', (.8, 2.0, 1.6), 2.2), ('Back', (-1.6, 1.4, .7), .8)):
        l = bpy.data.lights.new('STUDIO_' + n, 'SUN'); l.energy = e
        try: l.angle = radians(12)
        except Exception: pass
        o = bpy.data.objects.new('STUDIO_' + n, l); o.location = d; look(o, (0, 0, 0)); st.objects.link(o)
    cd = bpy.data.cameras.new('CAM'); cd.lens = 70; cd.clip_start = .01; cd.clip_end = 3000
    cam = bpy.data.objects.new('CAM', cd); st.objects.link(cam); sc.camera = cam
    w = bpy.data.worlds.new('CSC_Studio'); sc.world = w
    try: w.use_nodes = True
    except Exception: pass
    for n in w.node_tree.nodes:
        if n.bl_idname == 'ShaderNodeBackground':
            n.inputs[0].default_value = (.20, .20, .21, 1); n.inputs[1].default_value = 1.0
    sc.render.engine = 'BLENDER_EEVEE'
    for k, v in (('taa_render_samples', 48), ('use_raytracing', True), ('use_gtao', True), ('use_shadows', True)):
        try: setattr(sc.eevee, k, v)
        except Exception: pass
    sc.render.image_settings.file_format = 'PNG'
    sc.unit_settings.system = 'METRIC'; sc.unit_settings.scale_length = 1.0
    return cam


def aim(cam, c, R, d, res=(1400, 1050)):
    sc = bpy.context.scene; sc.render.resolution_x, sc.render.resolution_y = res; sc.render.resolution_percentage = 100
    hv = atan((36 * res[1] / res[0] / 2) / cam.data.lens)
    cam.location = Vector(c) + Vector(d).normalized() * (R / sin(hv) * 1.06); look(cam, c)


VIEWS = (('1_front', (0, -1, .30), 0), ('2_rear', (0, 1, .32), 0), ('3_side', (1, 0, .30), 0),
         ('4_threequarter', (-.8, -1, .5), 0), ('5_optics', (-.5, -1, .32), 1), ('6_lit', (.7, -1, .4), 2))


def render_views(name, st, outdir, only=None):
    cam = bpy.data.objects['CAM']; lo, hi = st['bbox']; c = (lo + hi) / 2; R = (hi - lo).length / 2
    for key, d, mode in VIEWS:
        if only and key not in only: continue
        cc, RR = c, R
        if mode == 1 and st.get('optics'): cc, RR = st['optics']
        if mode == 2: set_lit(True)
        aim(cam, cc, RR, d)
        bpy.context.scene.render.filepath = os.path.join(outdir, '%s_%s.png' % (name, key))
        bpy.ops.render.render(write_still=True)
        if mode == 2: set_lit(False)


def build(name, func, blend_dir, save=True):
    wipe(); sc = bpy.context.scene
    top = bpy.data.collections.new(name); sc.collection.children.link(top)
    piv = None; st = {}
    for lod in (0, 1, 2):
        fx = Fx(name, lod); func(fx)
        if lod == 0:
            st['islands'] = fx.islands(); st['bbox'] = fx.bbox(); st['cells'] = fx.ncells
            st['emit'] = [e[1] for e in fx.emp if e[0] == 'EMIT']; st['mounts'] = [e[1] for e in fx.emp if e[0] == 'MOUNT']
            st['marks'] = [e[1] for e in fx.emp if e[0] == 'MARK']; st['optics'] = fx.info.get('optics')
            st['pivots'] = {k: [v[0], [round(x, 4) for x in v[1]]] for k, v in fx.pivots.items()}
        piv, st['tris%d' % lod] = fx.finalize(top, piv)
    studio()
    lo, hi = st['bbox']; aim(bpy.data.objects['CAM'], (lo + hi) / 2, (hi - lo).length / 2, (-.8, -1, .5))
    if save: bpy.ops.wm.save_as_mainfile(filepath=os.path.join(blend_dir, name + '.blend'), copy=True)
    return st
