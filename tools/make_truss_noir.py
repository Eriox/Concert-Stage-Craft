#!/usr/bin/env python3
"""Black version of the truss texture: tools/blockbench/reference/truss_noir.png (64 x 64, same layout as the truss atlas).

Starts from the atlas embedded in tools/blockbench/truss_h1_l2.bbmodel (so the aluminium look is always the source) and recolours the
tiles of tools/retexture_trusses.py (4 x 4 uv per tile, 16 x 16 px):
    alu      tubes, diagonals, bolts   -> satin black   (keeps the original grain)
    alu_dark flanges, gussets, ribs    -> a little darker black
    anod     nodes, plates             -> dark anthracite, lighter than the tubes so the nodes still read
    yellow, red, black holes, steel     -> unchanged (steel / name plate only toned down)
tools/generate_assets.py copies the result to textures/block/truss_noir.png (blocks `truss_*_noir`).
Safe to run again.
"""
import base64, json, os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from retexture_trusses import data_uri_to_png, write_png

SRC = os.path.join(HERE, 'blockbench', 'truss_h1_l2.bbmodel')
OUT = os.path.join(HERE, 'blockbench', 'reference', 'truss_noir.png')

# tile (column, row) -> (target mean of the grey, grain kept as a fraction)
TILES = {(0, 0): (34, 0.9), (1, 0): (27, 0.9), (2, 0): (62, 0.9), (2, 1): (92, 0.5)}


def main():
    d = json.load(open(SRC, encoding='utf-8'))
    atlas = data_uri_to_png(d['textures'][0]['source']).astype(np.float64)
    assert atlas.shape[:2] == (64, 64)
    for (tx, ty), (mean, grain) in TILES.items():
        t = atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16]
        lum = t.mean(axis=2, keepdims=True)
        dev = (lum - lum.mean()) * grain * (mean / max(lum.mean(), 1.0)) ** 0.5
        blue = np.array([0.0, 0.0, 3.0])             # a faint cold tint like the aluminium
        atlas[ty * 16:(ty + 1) * 16, tx * 16:(tx + 1) * 16] = np.clip(mean + dev + blue, 0, 255)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, 'wb').write(write_png(atlas.round().astype(np.uint8)))
    print('wrote', OUT)


if __name__ == '__main__':
    main()
