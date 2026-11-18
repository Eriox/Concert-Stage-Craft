import bpy,math,json,pathlib,struct,os
from mathutils import Vector
ROOT=pathlib.Path(__file__).resolve().parents[2];BASE=ROOT/'tools/aurex';OUT=BASE/'Renders';OUT.mkdir(exist_ok=True)
report=[]
def look(obj,point):obj.rotation_euler=(Vector(point)-obj.location).to_track_quat('-Z','Y').to_euler()
def studio():
 s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.samples=64;s.cycles.use_denoising=False;s.render.resolution_x=700;s.render.resolution_y=700;s.render.resolution_percentage=100
 s.world=bpy.data.worlds.new('AUREX neutral studio');s.world.use_nodes=True;s.world.node_tree.nodes['Background'].inputs[0].default_value=(.12,.15,.20,1);s.world.node_tree.nodes['Background'].inputs[1].default_value=.35
 for loc,power,size in [((-3,-4,5),1300,4),((4,-1,3),900,3),((0,4,4),1500,3)]:
  data=bpy.data.lights.new('Studio softbox','AREA');data.energy=power;data.shape='DISK';data.size=size;ob=bpy.data.objects.new('Studio softbox',data);s.collection.objects.link(ob);ob.location=loc;look(ob,(0,.4,.4))
 data=bpy.data.cameras.new('Presentation camera');cam=bpy.data.objects.new('Presentation camera',data);s.collection.objects.link(cam);s.camera=cam;data.type='ORTHO';data.ortho_scale=2.25
 s.view_settings.view_transform='AgX';s.view_settings.look='AgX - Medium High Contrast';s.view_settings.exposure=-.8;s.render.image_settings.file_format='PNG';return s,cam
for path in sorted((BASE/'Models').glob('*.blend')):
 bpy.ops.wm.open_mainfile(filepath=str(path));sc=bpy.context.scene
 row={'id':path.stem,'units_m':sc.unit_settings.scale_length,'nominal_dimensions':list(sc['CSC_Dimensions']),'lods':{}}
 for name in ('HIGH_DETAIL','GAME_READY'):
  col=bpy.data.collections[name];col.hide_viewport=False;bpy.context.view_layer.update();tri=0;bounds=[]
  for o in col.all_objects:
   if o.type!='MESH' or o.get('csc_cutter'):continue
   assert len(o.data.uv_layers)>=2,(path,o.name)
   assert all(math.isfinite(x) for v in o.data.vertices for x in v.co)
   me=o.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh();me.calc_loop_triangles();tri+=len(me.loop_triangles)
   assert all(math.isfinite(x) for n in me.polygons for x in n.normal)
   bounds.extend(o.matrix_world@v.co for v in me.vertices);o.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh_clear()
  row['lods'][name]={'triangles':tri,'min':[min(v[i] for v in bounds) for i in range(3)],'max':[max(v[i] for v in bounds) for i in range(3)]}
 bpy.data.collections['GAME_READY'].hide_viewport=True
 glb=path.with_suffix('.glb').read_bytes();magic,version,length=struct.unpack('<4sII',glb[:12]);assert magic==b'glTF' and version==2 and length==len(glb)
 row['glb_valid']=True;row['attach_points']=[o.name for o in bpy.data.collections['GAME_READY'].all_objects if o.type=='EMPTY'];report.append(row)
 if not os.getenv('CSC_VALIDATE_ONLY') and path.stem in ('csc_aurex_m12','csc_aurex_s218_f','csc_aurex_m15_w'):
  s,cam=studio();d=list(sc['CSC_Dimensions']);center=(0,d[2]/2,d[1]/2);cam.data.ortho_scale=max(d)*1.4
  for name,off in [('perspective',(3,-5,2.5)),('front',(0,-5,.05)),('rear',(0,5,.1)),('side',(5,0,.1)),('top',(0,.01,5))]:
   cam.location=Vector(center)+Vector(off);look(cam,center);s.render.filepath=str(OUT/(path.stem+'_'+name+'.png'));bpy.ops.render.render(write_still=True)
(BASE/'Documentation/validation_blender.json').write_text(json.dumps(report,indent=2))
print('AUREX VALIDATED',len(report),flush=True)
