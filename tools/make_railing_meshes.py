#!/usr/bin/env python3
"""1.2.0 beta 2b: stage_meshes/<railing>.json for the safety railings clamped on a platform (StageMounting).

A railing set on the edge of a platform is not a block: it is a piece held by the platform cells (StageAttachmentBlockEntity), drawn by
StageAttachmentRenderer from stage_meshes/<id>.json. The three railings had no such mesh, so a railing clamped on a platform (straight or
at an angle) was there (collision, item given back) but drawn by nothing. This writes the mesh from the "mounted" OBJ parts of the
railing blocks (models/stage/world/<id>_<part>_1.obj, the clamped version, in the frame of the piece once moved to their cell), in the
format of the other stage meshes: per texture slot (0 metals, 1 anti_slip, 2 hardware) a list of quads, 4 x (x, y, z, u, v, nx, ny, nz),
v flipped as the block models do (flip_v).

Usage: python3 -I tools/make_railing_meshes.py <repo root>
"""
import json
import os
import sys

RAILINGS = {"safety_railings_1m": (1, 2, 1), "safety_railings_2m": (2, 2, 1), "safety_railings_corner_1x1": (1, 2, 1)}
SLOT = {"material_0": "0", "material_1": "1", "material_2": "2"}


def obj_quads(path, off):
    V, T, N, out, mat = [], [], [], {}, None
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == "v": V.append([float(x) for x in p[1:4]])
        elif p[0] == "vt": T.append([float(x) for x in p[1:3]])
        elif p[0] == "vn": N.append([float(x) for x in p[1:4]])
        elif p[0] == "usemtl": mat = p[1]
        elif p[0] == "f":
            idx = [[int(x) for x in q.split("/")] for q in p[1:]]
            if len(idx) == 3: idx.append(idx[2])
            if len(idx) != 4: raise ValueError("face of %d vertices in %s" % (len(idx), path))
            q = []
            for a, b, c in idx:
                v, t, n = V[a - 1], T[b - 1], N[c - 1]
                q += [round(v[0] + off[0], 4), round(v[1] + off[1], 4), round(v[2] + off[2], 4), round(t[0], 7), round(1 - t[1], 7),
                      n[0] + 0.0, n[1] + 0.0, n[2] + 0.0]
            out.setdefault(SLOT[mat], []).append(q)
    return out


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    res = os.path.join(root, "common/src/main/resources/assets/concertstage")
    for key, (sx, sy, sz) in RAILINGS.items():
        mesh = {}
        for part in range(sx * sy * sz):
            lx, ly, lz = part % sx, part // (sx * sz), (part // sx) % sz
            path = os.path.join(res, "models/stage/world/%s_%d_1.obj" % (key, part))
            for slot, quads in obj_quads(path, (lx, ly, lz)).items():
                mesh.setdefault(slot, []).extend(quads)
        out = os.path.join(res, "stage_meshes", key + ".json")
        json.dump({k: mesh[k] for k in sorted(mesh)}, open(out, "w"), separators=(",", ":"))
        print(key, {k: len(v) for k, v in sorted(mesh.items())}, "quads")


if __name__ == "__main__":
    main()
