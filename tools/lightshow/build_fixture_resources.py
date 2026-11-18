#!/usr/bin/env python3
"""Generate inventory icons from converted meshes, blockstate/loot resources and fixture labels.
The DMX registry dump is the fixture inventory; no separate channel table is maintained.
Run after tools/external/build_external_assets.py (tools/external/build.sh handles this).
"""
from pathlib import Path
import json,sys
root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'tools/external'))
from build_external_assets import Model,thumbnail
res=root/'common/src/main/resources';assets=res/'assets/concertstage'
rows=[[f['id'][4:].upper(),f['function']] for f in json.loads((root/'tools/external/modes.json').read_text())['fixtures'][4:]]
for row in rows:
 name=row[0];fid='csc_'+name.lower();m=Model(assets/'lightshow'/f'{fid}.json',1)
 # rev. 25b: the icons come from tools/lightshow/render_icons.py (z-buffer, anti-aliased, lit lenses, same framing), run below
 (assets/'blockstates'/f'{fid}.json').write_bytes((assets/'blockstates/csc_vortex_x1.json').read_bytes())
 for sub in ['loot_tables/blocks']:
  p=res/'data/concertstage'/sub/f'{fid}.json';p.write_text((res/'data/concertstage'/sub/'csc_vortex_x1.json').read_text().replace('csc_vortex_x1',fid))
for p in (assets/'lang').glob('*.json'):
 d=json.loads(p.read_text())
 for row in rows:
  fid='csc_'+row[0].lower();d[f'block.concertstage.{fid}']='CSC '+row[0].replace('_',' ')
  if 'halogen' in row[1] and f'tooltip.concertstage.fixture.{fid}' in d: continue   # 1.2.1: conventionals keep their own tooltip (one dimmer channel)
  d[f'tooltip.concertstage.fixture.{fid}']=f'{row[1]} · FULL / COMPACT / ULTRA COMPACT / VIRTUAL MATRIX'
 p.write_text(json.dumps(d,ensure_ascii=False,indent=1)+'\n')
# Block breaking tool tag follows the existing fixtures.
for p in (res/'data').rglob('tags/blocks/*.json'):
 try:d=json.loads(p.read_text())
 except Exception:continue
 vals=d.get('values',[])
 if 'concertstage:csc_vortex_x1' in vals:
  vals.extend('concertstage:csc_'+r[0].lower() for r in rows if 'concertstage:csc_'+r[0].lower() not in vals);p.write_text(json.dumps(d,indent=1)+'\n')
# rev. 25b: inventory / creative tab icons of every fixture (and their flat item models)
import subprocess
subprocess.run([sys.executable,str(root/'tools/lightshow/render_icons.py')],check=True)
