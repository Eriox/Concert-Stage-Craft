#!/usr/bin/env python3
"""Structural checks only: assets, original Blender digests, LOD counts and generated profile XML.
Does not claim a Minecraft render or console import test.
"""
from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2];res=root/'common/src/main/resources/assets/concertstage'
modes=json.loads((root/'tools/external/modes.json').read_text())['fixtures']
report=json.loads((res/'lightshow/new_lights_export_report.json').read_text());rows=[]
for f in modes[4:]:
 fid=f['id'];d=json.loads((res/'lightshow'/f'{fid}.json').read_text());r=report[fid]
 source=root/'source-assets/new_lights/Blender'/f'{fid.upper()}.blend'
 assert hashlib.sha256(source.read_bytes()).hexdigest()==r['source_sha256'],fid
 counts={k:sum(len(a)//9 for p in d[k].values() for a in p['static'].values()) for k in ['parts','lod_far','lod_low']}
 assert counts==r['triangles'],fid
 assert counts['parts']>counts['lod_far']>counts['lod_low']>0,fid
 assert len(d['zones'])==max(1,f['zones']),fid
 assert (res/'models/item'/f'{fid}.json').exists()
 assert (res/'textures/item'/f'{fid}.png').exists()
 assert (res/'blockstates'/f'{fid}.json').exists()
 ET.parse(res/'external/qlcplus'/('Concert-Stage-Craft-'+f['name'].replace('CSC ','').replace(' ','-')+'.qxf'))
 with zipfile.ZipFile(res/'external/gdtf'/('Concert Stage Craft@'+f['name']+'.gdtf')) as arc:
  xml=ET.fromstring(arc.read('description.xml'))
  assert len(xml.findall('.//DMXMode'))==3,fid
  if 'PRISM' in f['attrs']:
   facets=xml.findall('.//Wheel[@Name="Prism1"]/Slot/Facet');assert len(facets)==f['prism_facets'],fid
 for sub in ['gma2','dot2']:
  for mode in ['full','compact','ultra_compact']:
   name='concert_stage_craft@'+f['name'].lower().replace(' ','_')+'@'+mode+'.xml';ET.parse(res/'external'/sub/name)
 rows.append(dict(id=fid,triangles=counts,pixels=f['zones'],dmx=[len(m['channels']) for m in f['modes']],original_blend_sha256=r['source_sha256']))
# 1.2.0 remodel of the four historical fixtures: same Blender pipeline, zone layout of the Java tables unchanged.
remodelled=[]
for fid,zones,pivot in [('csc_vortex_x1',1,.361),('csc_lumora_w7',19,.322),('csc_lumora_w19',37,.396),('csc_pulsestrike_m',42,.222)]:
 data=json.loads((res/'lightshow'/(fid+'.json')).read_text());r=report[fid]
 assert hashlib.sha256((root/'source-assets/new_lights/Blender'/(fid.upper()+'.blend')).read_bytes()).hexdigest()==r['source_sha256'],fid
 counts=[sum(len(a)//9 for part in data[key].values() for a in part['static'].values()) for key in ['parts','lod_far','lod_low']]
 assert counts[0]>counts[1]>counts[2]>0 and len(data['zones'])==zones,fid
 pan=data['pivots']['pan'] or [0,0,0];assert abs(pan[1]+data['pivots']['tilt'][1]-pivot)<1e-4,fid
 assert (res/'models/lightshow'/(fid+'.obj')).is_file() and (res/'external/models'/(fid+'.glb')).is_file(),fid
 remodelled.append(dict(id=fid,triangles=counts,zones=zones,original_blend_sha256=r['source_sha256']))
# Every level of detail sits on its pivot: a far level exported without its pivot would hang one pivot height too low.
for fid in report:
 data=json.loads((res/'lightshow'/(fid+'.json')).read_text())
 def span(key,part):
  ys=[a[i+1] for a in data[key][part]['static'].values() for i in range(0,len(a),3)];return (min(ys)/4096,max(ys)/4096) if ys else None
 for part in data['parts']:
  a=span('parts',part)
  for key in ['lod_far','lod_low']:
   b=span(key,part)
   if a and b:assert abs(a[0]-b[0])<.08 and abs(a[1]-b[1])<.08,(fid,key,part,a,b)
print(json.dumps(dict(fixtures=len(rows),checks='source hashes, 3 LODs on their pivots, emission counts, resources, XML structure and prism facets',results=rows,remodelled=remodelled),indent=1))
