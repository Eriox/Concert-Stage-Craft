#!/usr/bin/env python3
"""Minimal reader of uncompressed .blend files (Blender 4.x, little endian, 8-byte pointers).

Reads the SDNA, every file block, and gives structured access to the data blocks (by old pointer). Enough to pull meshes
(positions, faces, corners, UVs, material indices), objects (transforms, parents, modifiers) and materials (node colours)
out of the CSC fixture files without Blender. Pure Python, no dependency.
"""
import struct


class SDNA:
    def __init__(self, data):
        p = 0
        assert data[0:4] == b'SDNA'
        p = 4

        def align():
            nonlocal p
            p = (p + 3) & ~3

        assert data[p:p + 4] == b'NAME'; p += 4
        n = struct.unpack_from('<i', data, p)[0]; p += 4
        self.names = []
        for _ in range(n):
            e = data.index(b'\0', p); self.names.append(data[p:e].decode('latin1')); p = e + 1
        align()
        assert data[p:p + 4] == b'TYPE'; p += 4
        n = struct.unpack_from('<i', data, p)[0]; p += 4
        self.types = []
        for _ in range(n):
            e = data.index(b'\0', p); self.types.append(data[p:e].decode('latin1')); p = e + 1
        align()
        assert data[p:p + 4] == b'TLEN'; p += 4
        self.tlen = list(struct.unpack_from('<%dh' % len(self.types), data, p)); p += 2 * len(self.types)
        align()
        assert data[p:p + 4] == b'STRC'; p += 4
        n = struct.unpack_from('<i', data, p)[0]; p += 4
        self.structs = []           # (type index, [(type index, name index)])
        for _ in range(n):
            t, nf = struct.unpack_from('<hh', data, p); p += 4
            fields = []
            for _ in range(nf):
                ft, fn = struct.unpack_from('<hh', data, p); p += 4
                fields.append((ft, fn))
            self.structs.append((t, fields))
        self.struct_by_type = {self.types[t]: i for i, (t, _) in enumerate(self.structs)}
        self._layout = {}

    def layout(self, sidx):
        """{field name (without * and [..]): (offset, type name, is_pointer, array dims, raw name)} of struct number sidx."""
        if sidx in self._layout:
            return self._layout[sidx]
        t, fields = self.structs[sidx]
        off = 0
        out = {}
        for ft, fn in fields:
            raw = self.names[fn]
            ptr = raw.startswith('*') or raw.startswith('(*')
            base = raw.lstrip('*')
            dims = []
            if '[' in base:
                parts = base.split('[')
                bare = parts[0]
                for q in parts[1:]:
                    dims.append(int(q.rstrip(']')))
            else:
                bare = base
            if bare.startswith('(*'):
                bare = bare[2:].split(')')[0]
            size = 8 if ptr else self.tlen[ft]
            count = 1
            for d in dims:
                count *= d
            out[bare] = (off, self.types[ft], ptr, dims, raw)
            off += size * count
        self._layout[sidx] = out
        return out


class Block:
    __slots__ = ('code', 'size', 'old', 'sdna', 'count', 'offset')

    def __init__(self, code, size, old, sdna, count, offset):
        self.code, self.size, self.old, self.sdna, self.count, self.offset = code, size, old, sdna, count, offset


class Blend:
    def __init__(self, path):
        self.data = open(path, 'rb').read()
        d = self.data
        assert d[:7] == b'BLENDER', 'compressed or not a blend file'
        assert d[7:8] == b'-' and d[8:9] == b'v', 'expects 64-bit little-endian'
        self.version = d[9:12].decode()
        p = 12
        self.blocks = []
        self.by_ptr = {}
        dna = None
        while p + 24 <= len(d):
            code = d[p:p + 4]
            size, = struct.unpack_from('<i', d, p + 4)
            old, = struct.unpack_from('<Q', d, p + 8)
            sdna, count = struct.unpack_from('<ii', d, p + 16)
            b = Block(code, size, old, sdna, count, p + 24)
            p += 24 + size
            if code == b'ENDB':
                break
            self.blocks.append(b)
            if old:
                self.by_ptr[old] = b
            if code == b'DNA1':
                dna = d[b.offset:b.offset + size]
        self.sdna = SDNA(dna)

    # ---------------------------------------------------------------- structured access
    def struct_size(self, tname):
        return self.sdna.tlen[self.sdna.structs[self.sdna.struct_by_type[tname]][0]]

    def view(self, off, tname):
        return View(self, off, tname)

    def deref(self, ptr, tname=None):
        """View on the data block a pointer points to (or None)."""
        if not ptr:
            return None
        b = self.by_ptr.get(ptr)
        if b is None:
            return None
        if tname is None:
            tname = self.sdna.types[self.sdna.structs[b.sdna][0]]
        return View(self, b.offset, tname)

    def block_of(self, ptr):
        return self.by_ptr.get(ptr) if ptr else None

    def raw(self, ptr):
        b = self.block_of(ptr)
        return None if b is None else self.data[b.offset:b.offset + b.size]

    def ids(self, code):
        """Every ID block of the given code (b'OB', b'ME', b'MA'...)."""
        for b in self.blocks:
            if b.code[:2] == code and b.code[2:] == b'\0\0':
                yield View(self, b.offset, self.sdna.types[self.sdna.structs[b.sdna][0]])

    def listbase(self, lb_view, tname):
        """Items of a ListBase field (first/last), as views of type tname."""
        ptr = lb_view.get('first')
        seen = set()
        while ptr and ptr not in seen:
            seen.add(ptr)
            v = self.deref(ptr, tname)
            if v is None:
                break
            yield v
            ptr = v.get('next')


_PRIM = {'char': ('b', 1), 'uchar': ('B', 1), 'short': ('h', 2), 'ushort': ('H', 2), 'int': ('i', 4), 'uint': ('I', 4),
         'float': ('f', 4), 'double': ('d', 8), 'int64_t': ('q', 8), 'uint64_t': ('Q', 8), 'int8_t': ('b', 1), 'uint8_t': ('B', 1),
         'int16_t': ('h', 2), 'uint16_t': ('H', 2), 'int32_t': ('i', 4), 'uint32_t': ('I', 4), 'bool': ('?', 1)}


class View:
    def __init__(self, blend, off, tname):
        self.b, self.off, self.t = blend, off, tname
        self.lay = blend.sdna.layout(blend.sdna.struct_by_type[tname])

    def has(self, name):
        return name in self.lay

    def field_off(self, name):
        return self.off + self.lay[name][0]

    def get(self, name, default=None):
        if name not in self.lay:
            return default
        off, tname, ptr, dims, raw = self.lay[name]
        d = self.b.data
        o = self.off + off
        if ptr:
            if dims:
                n = 1
                for x in dims:
                    n *= x
                return list(struct.unpack_from('<%dQ' % n, d, o))
            return struct.unpack_from('<Q', d, o)[0]
        if tname in _PRIM:
            c, sz = _PRIM[tname]
            if dims:
                n = 1
                for x in dims:
                    n *= x
                if tname == 'char':
                    bs = d[o:o + n]
                    e = bs.find(b'\0')
                    return (bs if e < 0 else bs[:e]).decode('utf-8', 'replace')
                return list(struct.unpack_from('<%d%s' % (n, c), d, o))
            return struct.unpack_from('<' + c, d, o)[0]
        # nested struct
        if dims:
            sz = self.b.struct_size(tname)
            n = 1
            for x in dims:
                n *= x
            return [View(self.b, o + i * sz, tname) for i in range(n)]
        return View(self.b, o, tname)

    def name(self):
        idv = self.get('id')
        return idv.get('name')[2:] if idv is not None else None
