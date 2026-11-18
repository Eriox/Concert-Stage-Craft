#!/usr/bin/env python3
"""Audit packaged speaker geometry against the actual Java registry (no Blender dependency)."""
from pathlib import Path
import json, math, re, struct
root = Path(__file__).resolve().parents[2]
source = (root / 'common/src/main/java/com/concertstage/audio/SpeakerModels.java').read_text()
models = re.findall(r'new Model\("([^"]+)",\s*"[^"]+",\s*Kind\.\w+,\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)', source)
assert models, 'Speaker registry not found'
assets = root / 'common/src/main/resources/assets/concertstage'
rows = []
for ident, width, height, depth in models:
    expected = list(map(float, [width, height, depth]))
    data = json.loads((assets / 'speakers' / (ident + '.json')).read_text())
    assert [data[k] for k in ('width', 'height', 'depth')] == expected, ident
    row = {'id': ident, 'dimensions_m': expected}
    for lod in ('near', 'far'):
        vertices = data[lod]
        assert vertices and len(vertices) % 24 == 0, (ident, lod, 'triangle layout')
        assert all(math.isfinite(v) for v in vertices), (ident, lod, 'non-finite vertex')
        bounds = [min(vertices[k::8]) for k in range(3)] + [max(vertices[k::8]) for k in range(3)]
        target = [-expected[0]/2, 0, -expected[2], expected[0]/2, expected[1], 0]
        assert max(abs(a-b) for a,b in zip(bounds,target)) < 2e-5, (ident,lod,bounds,target)
        for k in range(0, len(vertices), 8):
            assert 0 <= vertices[k+3] <= 1 and 0 <= vertices[k+4] <= 1, (ident,lod,'atlas UV')
            length = sum(v*v for v in vertices[k+5:k+8]) ** .5
            assert abs(length-1) < .01, (ident,lod,'normal',length)
        row[lod + '_triangles'] = len(vertices)//24
        row[lod + '_bounds'] = bounds
    rows.append(row)
texture = (assets / 'textures/entity/speakers_atlas.png')
if not texture.exists():
    matches = list(assets.rglob('speakers_atlas.png'))
    assert len(matches) == 1, 'Speaker atlas missing or ambiguous'
    texture = matches[0]
with texture.open('rb') as f:
    header = f.read(24)
assert header[:8] == b'\x89PNG\r\n\x1a\n'
assert struct.unpack('>II', header[16:24]) == (1024,1024)
output = root / 'Documentation/Audio_Orvane_Preview_2/speaker_geometry_audit.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(rows, indent=2) + '\n')
print(f'PASS: {len(rows)} speaker models, both LODs, registry dimensions, finite vertices, unit normals, atlas UVs and texture')
