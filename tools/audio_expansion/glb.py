"""Minimal glTF 2.0 binary (.glb) reader for the CSC audio expansion: meshes (positions, normals, UV0, indices), node transforms,
materials (base colour factor / texture, alpha mode) and embedded images. numpy only."""
import json, struct, io
import numpy as np

_CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
_NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


class Glb:
    def __init__(self, path):
        data = open(path, 'rb').read()
        magic, ver, length = struct.unpack_from('<III', data, 0)
        assert magic == 0x46546C67, path
        off = 12
        self.json, self.bin = None, b''
        while off < length:
            clen, ctype = struct.unpack_from('<II', data, off)
            chunk = data[off + 8: off + 8 + clen]
            if ctype == 0x4E4F534A: self.json = json.loads(chunk)
            elif ctype == 0x004E4942: self.bin = chunk
            off += 8 + clen
        self.j = self.json

    def accessor(self, i):
        a = self.j['accessors'][i]
        bv = self.j['bufferViews'][a['bufferView']]
        dt = np.dtype(_CT[a['componentType']])
        n, c = a['count'], _NC[a['type']]
        start = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
        stride = bv.get('byteStride', 0)
        if stride and stride != dt.itemsize * c:
            raw = np.frombuffer(self.bin, dtype=np.uint8, count=stride * (n - 1) + dt.itemsize * c, offset=start)
            out = np.empty((n, c), dt)
            for k in range(n):
                out[k] = np.frombuffer(raw[k * stride: k * stride + dt.itemsize * c].tobytes(), dt)
            arr = out
        else:
            arr = np.frombuffer(self.bin, dtype=dt, count=n * c, offset=start).reshape(n, c)
        if a.get('normalized'):
            arr = arr.astype(np.float32) / np.iinfo(dt).max
        return arr

    def image(self, i):
        im = self.j['images'][i]
        bv = self.j['bufferViews'][im['bufferView']]
        s = bv.get('byteOffset', 0)
        return self.bin[s: s + bv['byteLength']]

    @staticmethod
    def _matrix(node):
        if 'matrix' in node:
            return np.array(node['matrix'], dtype=np.float64).reshape(4, 4).T
        t = np.array(node.get('translation', [0, 0, 0]), dtype=np.float64)
        q = node.get('rotation', [0, 0, 0, 1])
        s = np.array(node.get('scale', [1, 1, 1]), dtype=np.float64)
        x, y, z, w = q
        r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                      [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                      [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
        m = np.eye(4)
        m[:3, :3] = r * s
        m[:3, 3] = t
        return m

    def primitives(self):
        """Yields (node name, world matrix, primitive dict) for every mesh primitive of the default scene."""
        scene = self.j['scenes'][self.j.get('scene', 0)]
        out = []

        def walk(ni, parent):
            node = self.j['nodes'][ni]
            m = parent @ self._matrix(node)
            if 'mesh' in node:
                for p in self.j['meshes'][node['mesh']]['primitives']:
                    out.append((node.get('name', ''), m, p))
            for c in node.get('children', []):
                walk(c, m)

        for n in scene['nodes']:
            walk(n, np.eye(4))
        return out

    def material(self, i):
        return self.j['materials'][i] if i is not None and 'materials' in self.j else {}

    def nodes_named(self):
        """World position of every node by name (connectors, markers)."""
        scene = self.j['scenes'][self.j.get('scene', 0)]
        out = {}

        def walk(ni, parent):
            node = self.j['nodes'][ni]
            m = parent @ self._matrix(node)
            out[node.get('name', '')] = m[:3, 3].copy()
            for c in node.get('children', []):
                walk(c, m)

        for n in scene['nodes']:
            walk(n, np.eye(4))
        return out
