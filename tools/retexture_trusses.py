#!/usr/bin/env python3
"""Gives every truss the texture of the motorised truss tower (tools/blockbench/reference/tour_truss_motorisee_10_blocs.bbmodel).

The tower uses three flat tones (16 x 16 px, one quadrant each):
    top left / bottom left : light aluminium   (tubes, diagonals, frames)
    top right              : dark anthracite    (feet, motor, cable, winch)
    bottom right           : yellow             (control box, lamp)
The trusses share one 64 x 64 atlas of 4 x 4 "tiles" (see TILE in shrink_trusses.py). This script rewrites the four tiles that carry
those tones and leaves the others (black holes and pins, red, chain, name plate) as they are:

    alu      tubes, diagonals, bolts, nuts          -> tower aluminium (top left)
    alu_dark flanges, gussets, ribs, collars        -> tower aluminium (bottom left, same tone, other noise)
    anod     nodes, base plate, closing plates      -> tower anthracite (top right)
    yellow                                          -> tower yellow (bottom right)

It writes the new atlas into the 16 truss .bbmodel files (tools/blockbench and tools/blockbench/fullsize, so shrink_trusses.py keeps
producing the same colours). Then run tools/generate_assets.py to refresh textures/block/truss.png.
Safe to run again: the tower is always the source of the four tiles.
"""
import base64, glob, json, os, struct, zlib
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
BB = os.path.join(HERE, 'blockbench')
TOWER = os.path.join(BB, 'reference', 'tour_truss_motorisee_10_blocs.bbmodel')

# tile -> (u0, v0) of the tile in a 16 x 16 uv space (1 uv = 4 px of the 64 px atlas); every tile is 4 x 4 uv = 16 x 16 px
TILE_XY = {'alu': (0, 0), 'alu_dark': (4, 0), 'anod': (8, 0), 'yellow': (12, 0)}
# tile -> quadrant of the tower texture (x0, y0) in px, each quadrant is 8 x 8 px
FROM_TOWER = {'alu': (0, 0), 'alu_dark': (0, 8), 'anod': (8, 0), 'yellow': (8, 8)}


# ---------------------------------------------------------------- minimal PNG reader / writer (8 bit RGB / RGBA, no interlace)
def read_png(raw):
    assert raw[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos, idat, w = 8, b'', None
    while pos < len(raw):
        n, = struct.unpack('>I', raw[pos:pos + 4]); typ = raw[pos + 4:pos + 8]; body = raw[pos + 8:pos + 8 + n]; pos += 12 + n
        if typ == b'IHDR':
            w, h, depth, ctype, _, _, inter = struct.unpack('>IIBBBBB', body)
            assert depth == 8 and ctype in (2, 6) and inter == 0, 'only 8 bit RGB/RGBA PNG without interlace'
        elif typ == b'IDAT':
            idat += body
    bpp = 3 if ctype == 2 else 4
    data = zlib.decompress(idat)
    stride = w * bpp
    img = np.zeros((h, stride), np.int32)
    prev = np.zeros(stride, np.int32)
    p = 0
    for y in range(h):
        f = data[p]; line = np.frombuffer(data[p + 1:p + 1 + stride], np.uint8).astype(np.int32); p += 1 + stride
        cur = np.zeros(stride, np.int32)
        for i in range(stride):
            a = cur[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 0: v = line[i]
            elif f == 1: v = line[i] + a
            elif f == 2: v = line[i] + b
            elif f == 3: v = line[i] + ((a + b) >> 1)
            else:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                v = line[i] + (a if pa <= pb and pa <= pc else (b if pb <= pc else c))
            cur[i] = v & 255
        img[y] = cur; prev = cur
    return img.reshape(h, w, bpp)[:, :, :3].astype(np.uint8)


def write_png(rgb):
    h, w, _ = rgb.shape
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    rows = b''.join(b'\x00' + rgb[y].tobytes() for y in range(h))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def data_uri_to_png(src):
    return read_png(base64.b64decode(src.split(',', 1)[1]))


def main():
    tower = json.load(open(TOWER, encoding='utf-8'))
    tex = data_uri_to_png(tower['textures'][0]['source'])
    assert tex.shape[:2] == (16, 16), 'the tower texture is expected to be 16 x 16 px'

    files = sorted(glob.glob(os.path.join(BB, 'truss_*.bbmodel')) + glob.glob(os.path.join(BB, 'fullsize', 'truss_*.bbmodel')))
    first = json.load(open(files[0], encoding='utf-8'))
    atlas = data_uri_to_png(first['textures'][0]['source']).copy()
    assert atlas.shape[:2] == (64, 64), 'the truss atlas is expected to be 64 x 64 px'

    for tile, (u, v) in TILE_XY.items():
        qx, qy = FROM_TOWER[tile]
        quad = tex[qy:qy + 8, qx:qx + 8]
        atlas[v * 4:v * 4 + 16, u * 4:u * 4 + 16] = np.kron(quad, np.ones((2, 2, 1), np.uint8))   # 8 px -> 16 px, nearest

    uri = 'data:image/png;base64,' + base64.b64encode(write_png(atlas)).decode('ascii')
    for f in files:
        d = json.load(open(f, encoding='utf-8'))
        d['textures'][0]['source'] = uri
        with open(f, 'w', encoding='utf-8') as out:
            out.write(json.dumps(d, ensure_ascii=False))
    print('Texture of the motorised tower applied to %d truss models. Now run: python3 tools/generate_assets.py' % len(files))


if __name__ == '__main__':
    main()
