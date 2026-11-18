#!/usr/bin/env python3
"""
CSC 1.3.0 - item icons of LiveVision and Dynamic Truss, rendered from the models exported from the supplied Blender files
(assets/concertstage/vision/<id>.json, LOD0, rest pose) and, for the curved trusses, from the Java geometry (truss_<quad|tri>.txt
written by DumpTruss.java, 90 deg R 3.5 m). Same renderer and lights as the fixture icons (tools/lightshow/render_icons.py).
Usage: python3 tools/v13/icons/render_vision_icons.py <truss dump folder>
"""
import base64, json, math, os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "lightshow"))
import render_icons as ri  # noqa: E402

ASSETS = os.path.join(ROOT, "common/src/main/resources/assets/concertstage")


def model_tris(mid, parts=None, offset=None):
    d = json.load(open(os.path.join(ASSETS, "vision", mid + ".json")))
    mats = d["materials"]
    tris, cols = [], []
    for part, batches in d["lods"][0].items():
        if parts is not None and part not in parts:
            continue
        piv = np.array(d["pivots"][part]["pos"]) if parts is None else np.zeros(3)
        for b in batches:
            v = np.frombuffer(base64.b64decode(b["v"]), "<f4").reshape(-1, 3, 3).astype(np.float64) + piv
            if offset is not None and part in offset:
                v = v + np.array(offset[part])
            m = mats[b["m"]]
            rgb = m.get("emit") or m["rgb"]
            c = [int(rgb[i:i + 2], 16) for i in (0, 2, 4)]
            if "ledvideo" in m["name"] or "screendyn" in m["name"]:
                c = [30, 120, 210]
            tris.append(v)
            cols.extend([c] * len(v))
    return np.concatenate(tris), np.array(cols, float)


def truss_tris(path):
    a = np.loadtxt(path)
    v = a[:, :3].reshape(-1, 3, 3)
    mat = a[::3, 3].astype(int)
    pal = {0: [170, 174, 180], 1: [120, 124, 130], 2: [90, 92, 96]}
    return v, np.array([pal.get(m, pal[0]) for m in mat], float)


def render(tris, cols):
    cam = ri.camera(tris.reshape(-1, 3)).reshape(-1, 3, 3)
    lo, hi = cam.reshape(-1, 3)[:, :2].min(0), cam.reshape(-1, 3)[:, :2].max(0)
    big = ri.SIZE * ri.SS
    scale = (big - 2 * ri.MARGIN * ri.SS) / max(hi - lo)
    centre = (lo + hi) / 2
    s2 = np.stack([(cam[..., 0] - centre[0]) * scale + big / 2, big / 2 - (cam[..., 1] - centre[1]) * scale], -1)
    img = np.zeros((big, big, 3)); zbuf = np.full((big, big), -1e9)
    e1, e2 = cam[:, 1] - cam[:, 0], cam[:, 2] - cam[:, 0]
    n = np.cross(e1, e2); n /= np.maximum(1e-12, np.linalg.norm(n, axis=1, keepdims=True)); n[n[:, 2] < 0] *= -1
    light = np.full(len(n), ri.AMBIENT)
    for d, k in ri.LIGHTS:
        dc = ri.camera(d[None, :])[0]; dc /= np.linalg.norm(dc)
        light += k * np.clip(n @ dc, 0, None)
    shade = np.clip(np.maximum(cols, ri.FLOOR) * light[:, None], 0, 255)
    for i in range(len(cam)):
        ri.raster(img, zbuf, s2[i], cam[i, :, 2], shade[i])
    alpha = (zbuf > -1e8).astype(np.float64)
    rgba = np.dstack([img * alpha[..., None], alpha * 255]).clip(0, 255).astype(np.uint8)
    small = Image.fromarray(rgba, "RGBA").resize((ri.SIZE, ri.SIZE), Image.LANCZOS)
    a = np.asarray(small).astype(np.float64); al = a[..., 3:4] / 255.0
    rgb = np.where(al > 0, a[..., :3] / np.maximum(al, 1e-6), 0)
    return Image.fromarray(np.dstack([rgb, al * 255]).clip(0, 255).astype(np.uint8), "RGBA")


def save(item, im):
    im.save(os.path.join(ASSETS, "textures", "item", item + ".png"))
    with open(os.path.join(ASSETS, "models", "item", item + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "concertstage:item/" + item}}, f, indent=1)
    print("icon", item)


def main():
    dump = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    for item, mid in [("csc_led_panel_p1", "led_panel_p1"), ("csc_vision_processor_vp1", "vision_processor_vp1"), ("csc_cam_ptz_p1", "cam_ptz_p1"),
                      ("csc_broadcast_b1", "broadcast_b1"), ("csc_crane_c1", "crane_c1"), ("csc_remote_r1", "remote_r1")]:
        t, c = model_tris(mid)
        save(item, render(t, c))
    for item, part in [("csc_led_hang_bar", "HANG_BAR"), ("csc_led_ground_base", "GROUND_BASE"), ("csc_led_wall_mount", "WALL_MOUNT")]:
        save(item, render(*model_tris("led_accessories", parts={part})))
    save("curved_quadtruss", render(*truss_tris(os.path.join(dump, "truss_quad.txt"))))
    save("curved_tritruss", render(*truss_tris(os.path.join(dump, "truss_tri.txt"))))


if __name__ == "__main__":
    main()
