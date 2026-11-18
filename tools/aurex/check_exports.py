import bpy,pathlib,json,math
from mathutils import Vector
R=pathlib.Path(__file__).resolve().parents[2];B=R/'tools/aurex';rows=[]
for path in sorted((B/'Models').glob('*.glb')):
 bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.import_scene.gltf(filepath=str(path));meshes=[o for o in bpy.context.scene.objects if o.type=='MESH'];assert meshes,path
 for o in meshes:
  assert all(math.isfinite(v) for pt in o.data.vertices for v in pt.co)
  assert len(o.data.uv_layers)>=1
 data=json.loads((R/'common/src/main/resources/assets/concertstage/speakers'/(path.stem+'.json')).read_text())
 for lod in ('near','far','box'):
  arr=data[lod];assert arr and len(arr)%24==0
  for i in range(0,len(arr),8):
   x,y,z,u,v,nx,ny,nz=arr[i:i+8];assert all(math.isfinite(q) for q in (x,y,z,u,v,nx,ny,nz));assert 0<=u<=1 and 0<=v<=1
   assert abs(nx*nx+ny*ny+nz*nz-1)<.0001
 rows.append({'id':path.stem,'blender_glb_import':'pass','meshes':len(meshes),'minecraft_lods':{lod:len(data[lod])//24 for lod in ('near','far','box')}})
bpy.ops.wm.open_mainfile(filepath=str(B/'CSC_AUREX_Assembly_Demo.blend'));assert not bpy.data.libraries
assert all(im.packed_file or im.source=='GENERATED' for im in bpy.data.images if im.type=='IMAGE')
(B/'Documentation/export_validation.json').write_text(json.dumps(rows,indent=2));print('AUREX EXPORT VALIDATION PASS',len(rows),'GLBs, 18 LOD arrays, editable self-contained demo')
