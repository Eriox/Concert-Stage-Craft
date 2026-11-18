# CSC 1.2.0 - refonte VORTEX X1 / LUMORA W7 / LUMORA W19 / PULSESTRIKE M : construction, rendus, controle, export mod, vue d'ensemble.
# Console Python de Blender 5.3 : exec(compile(open(r"<ROOT>\_scripts\run_legacy4.py",encoding="utf-8").read(),"run_legacy4.py","exec"))
import bpy, sys, os, time, traceback, json, importlib
ROOT = r"C:\Users\kylia\Documents\Minecraft\CSC_1.2.0_New_Lights"
S = os.path.join(ROOT, "_scripts")
if S not in sys.path: sys.path.insert(0, S)
out = []
def P(*a):
    out.append(" ".join(str(x) for x in a))
    open(os.path.join(S, "log_legacy4.txt"), "w", encoding="utf-8").write("\n".join(out))
try:
    cfg = json.load(open(os.path.join(S, "cfg_legacy4.json")))
    import csc_lib, csc_models, csc_export
    importlib.reload(csc_lib); importlib.reload(csc_models); importlib.reload(csc_export)
    from mathutils import Vector
    NAMES = [n for n, f in csc_models.LEGACY4]
    P("blender", bpy.app.version_string)
    stats = json.load(open(os.path.join(S, "stats_build.json")))
    if os.path.exists(os.path.join(S, "stats_legacy4.json")): stats.update(json.load(open(os.path.join(S, "stats_legacy4.json"))))
    new = {}
    if cfg.get("build", True):
        for name, func in csc_models.LEGACY4:
            if cfg.get("models") and name not in cfg["models"]: continue
            t = time.time()
            try:
                st = csc_lib.build(name, func, os.path.join(ROOT, "Blender"), save=True)
                if cfg.get("views") != []:
                    csc_lib.render_views(name, st, os.path.join(ROOT, cfg.get("outdir", "Previews/Individual")), cfg.get("views"))
                lo, hi = st["bbox"]
                st["bbox"] = [[round(x, 4) for x in lo], [round(x, 4) for x in hi]]
                if st.get("optics"): st["optics"] = [[round(x, 4) for x in st["optics"][0]], st["optics"][1]]
                new[name] = st
                P(name, "OK %.1fs" % (time.time() - t), "tris", st["tris0"], st["tris1"], st["tris2"], "islands", st["islands"], "cells", st["cells"], "dims", [round(hi[i] - lo[i], 3) for i in range(3)])
            except Exception:
                P(name, "FAIL\n" + traceback.format_exc())
        old = json.load(open(os.path.join(S, "stats_legacy4.json"))) if os.path.exists(os.path.join(S, "stats_legacy4.json")) else {}
        old.update(new); json.dump(old, open(os.path.join(S, "stats_legacy4.json"), "w"), indent=1); stats.update(new)
    if cfg.get("qc", True):
        qc = {}
        for name in NAMES:
            path = os.path.join(ROOT, "Blender", name + ".blend"); q = {}
            try:
                bpy.ops.wm.open_mainfile(filepath=path, load_ui=False)
                q["opens"] = True; q["size_kb"] = os.path.getsize(path) // 1024; q["version"] = list(bpy.data.version)
                q["libraries"] = len(bpy.data.libraries); q["images_missing"] = len([i for i in bpy.data.images if i.filepath and not os.path.exists(bpy.path.abspath(i.filepath))])
                q["bad_scale"] = [o.name for o in bpy.data.objects if (Vector(o.scale) - Vector((1, 1, 1))).length > 1e-6]
                q["rot_meshes"] = [o.name for o in bpy.data.objects if o.type == 'MESH' and Vector(o.rotation_euler).length > 1e-6]
                loose = 0; degen = 0; nomat = 0; tris = {0: 0, 1: 0, 2: 0}; nobj = {0: 0, 1: 0, 2: 0}
                for o in bpy.data.objects:
                    if o.type != 'MESH' or o.name.startswith('STUDIO'): continue
                    me = o.data; used = set()
                    for p in me.polygons:
                        used.update(p.vertices)
                        if p.area < 1e-12: degen += 1
                        if p.material_index >= len(me.materials): nomat += 1
                    loose += len(me.vertices) - len(used)
                    l = int(o.get("csc_lod", 0)); tris[l] += sum(len(p.vertices) - 2 for p in me.polygons); nobj[l] += 1
                q["loose_verts"] = loose; q["degenerate_faces"] = degen; q["faces_without_material"] = nomat
                q["tris"] = tris; q["mesh_objects"] = nobj
                q["pivots"] = {o.name: [round(x, 4) for x in o.matrix_world.translation] for o in bpy.data.objects if o.get("csc_role") == "pivot"}
                q["cells"] = sorted(o.name for o in bpy.data.objects if o.get("csc_cell"))
                q["emit"] = len([o for o in bpy.data.objects if o.get("csc_role") == "EMIT"])
                q["mounts"] = sorted(o.name for o in bpy.data.objects if o.get("csc_role") == "MOUNT")
                q["marks"] = sorted(o.name for o in bpy.data.objects if o.get("csc_role") == "MARK")
                q["materials"] = sorted(m.name for m in bpy.data.materials)
                q["collections"] = sorted(c.name for c in bpy.data.collections)
                piv = bpy.data.objects.get("TILT")
                ch = [o for o in piv.children_recursive if o.type == 'MESH']
                piv.rotation_euler[0] = 0.6; bpy.context.view_layer.update()
                q["tilt_test_children"] = len(ch); q["tilt_test_ok"] = all(abs(o.matrix_world.to_scale()[i] - 1) < 1e-5 for o in ch for i in range(3))
                piv.rotation_euler[0] = 0.0
            except Exception:
                q["error"] = traceback.format_exc()
            qc[name] = q
        json.dump(qc, open(os.path.join(S, "qc_legacy4.json"), "w"), indent=1)
        P("QC", {k: (v.get("opens"), v.get("loose_verts"), v.get("degenerate_faces"), v.get("bad_scale"), len(v.get("cells", [])), v.get("error")) for k, v in qc.items()})
    if cfg.get("export", True):
        try:
            rep = csc_export.run(os.path.join(ROOT, "Blender"), os.path.join(S, "export"))
            P("export", {k: (v["triangles"], v["zones"], v["pivots"], v["beam"]["lens_radius"]) for k, v in rep.items()})
        except Exception:
            P("export FAIL\n" + traceback.format_exc())
    if cfg.get("overview", True):
        try: bpy.ops.wm.read_homefile(use_empty=True, load_ui=False)
        except Exception: bpy.ops.wm.read_factory_settings(use_empty=True)
        csc_lib.wipe(); cam = csc_lib.studio()
        rows = [["CSC_VORTEX_X1", "CSC_LUMORA_W7", "CSC_LUMORA_W19", "CSC_PULSESTRIKE_M"],
                ["CSC_PROFILE_P1", "CSC_PHANTOM_S1", "CSC_PIXELWASH_W37", "CSC_RAZOR_B1", "CSC_FOLLOWSPOT_F1"],
                ["CSC_BLINDER_X2", "CSC_BLINDER_X4", "CSC_BLINDER_X8", "CSC_FLOODLIGHT_F8", "CSC_LASERCORE_L1"],
                ["CSC_FLASHCORE_FX", "CSC_PIXELBAR_B16", "CSC_PIXELTUBE_T1"]]
        ys = cfg.get("ys", [3.2, 1.45, 0.1, -0.95]); gap = 0.28
        xo = cfg.get("xoff", [-0.35, 0, 0, 0])
        for ri, (row, y) in enumerate(zip(rows, ys)):
            ws = [stats[n]["bbox"][1][0] - stats[n]["bbox"][0][0] for n in row]
            x = -(sum(ws) + gap * (len(row) - 1)) / 2 + xo[ri]
            for n, w in zip(row, ws):
                with bpy.data.libraries.load(os.path.join(ROOT, "Blender", n + ".blend"), link=False) as (src, dst):
                    dst.collections = [n]
                c = dst.collections[0]; bpy.context.scene.collection.children.link(c)
                root = [o for o in c.all_objects if o.parent is None and o.get("csc_role") == "pivot"][0]
                lo, hi = stats[n]["bbox"]
                root.location = (x - lo[0], y, 0)
                cu = bpy.data.curves.new("LBL_" + n, "FONT"); cu.body = n.replace("_", " "); cu.size = 0.055; cu.extrude = 0.002
                cu.align_x = "CENTER"; cu.materials.append(csc_lib.mat("black"))
                t = bpy.data.objects.new("LBL_" + n, cu); bpy.context.scene.collection.objects.link(t)
                t.location = (x + w / 2, y + max(lo[1], -0.42) - 0.14, 0.004); t.rotation_euler = (0.35, 0, 0)
                x += w + gap
        csc_lib.aim(cam, cfg.get("ov_c", (0, 1.1, 0.5)), cfg.get("ov_r", 2.7), cfg.get("ov_d", (-0.25, -1, 0.72)), res=(2560, 1440))
        bpy.context.scene.render.filepath = os.path.join(ROOT, "Previews", "Overview_17_Lights.png")
        bpy.ops.render.render(write_still=True)
        P("overview rendered")
    P("DONE")
except Exception:
    P(traceback.format_exc())
