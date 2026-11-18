import bpy,json,math,pathlib,os
from mathutils import Vector
R=pathlib.Path(__file__).resolve().parents[2];BASE=R/'tools/audio_v2';MODELS=BASE/'Models';OUT=BASE/'Renders';OUT.mkdir(exist_ok=True)
report=[]
for p in sorted(MODELS.glob('*.blend')):
 bpy.ops.wm.open_mainfile(filepath=str(p));sc=bpy.context.scene
 assert abs(sc.unit_settings.scale_length-1)<1e-6
 row={'file':p.name,'collections':{},'editable':True,'packed_images':True}
 for name in ('HIGH_DETAIL','GAME_READY'):
  col=bpy.data.collections.get(name);assert col
  vertices=triangles=0;low=[1e9]*3;high=[-1e9]*3
  for o in col.all_objects:
   if o.type!='MESH' or o.get('csc_cutter'):continue
   assert o.data.uv_layers, o.name
   obj=o.evaluated_get(bpy.context.evaluated_depsgraph_get());me=obj.to_mesh();me.calc_loop_triangles()
   assert all(math.isfinite(c) for v in me.vertices for c in v.co)
   for v in me.vertices:
    w=obj.matrix_world@v.co
    for k in range(3):low[k]=min(low[k],w[k]);high[k]=max(high[k],w[k])
   vertices+=len(me.vertices);triangles+=len(me.loop_triangles);obj.to_mesh_clear()
  row['collections'][name]={'vertices':vertices,'triangles':triangles,'dimensions_m':[round(high[k]-low[k],5) for k in (0,2,1)]}
 for image in bpy.data.images:
  if image.source=='FILE':assert image.packed_file or pathlib.Path(bpy.path.abspath(image.filepath)).exists(), image.name
 report.append(row)
# Also reopen every GLB with Blender's own importer.
for p in sorted(MODELS.glob('*.glb')):
 bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.import_scene.gltf(filepath=str(p));assert any(o.type=='MESH' for o in bpy.context.scene.objects),p.name
(BASE/'validation_blender.json').write_text(json.dumps(report,indent=2))
if os.getenv('CSC_VALIDATION_ONLY'):
 print('CSC_NATIVE_VALIDATION PASS:',len(report),'blend files and GLB imports');raise SystemExit(0)

def studio(target,scale=3.1,perspective=False):
 sc=bpy.context.scene;sc.render.engine='CYCLES';sc.cycles.samples=20;sc.cycles.use_denoising=True
 sc.render.resolution_x=1000;sc.render.resolution_y=700;sc.render.resolution_percentage=100;sc.world=sc.world or bpy.data.worlds.new('Studio world');sc.world.color=(.17,.17,.17)
 bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.002));floor=bpy.context.object;floor.name='Studio floor'
 mat=bpy.data.materials.new('Studio neutral');mat.diffuse_color=(.20,.22,.25,1);floor.data.materials.append(mat)
 for loc,power,size in [((-3,-4,5),1250,5),((4,-1,3),900,4),((0,4,5),1600,3)]:
  data=bpy.data.lights.new('Studio area','AREA');data.energy=power;data.shape='DISK';data.size=size;o=bpy.data.objects.new('Studio area',data);sc.collection.objects.link(o);o.location=loc;o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler()
 data=bpy.data.cameras.new('Technical camera');cam=bpy.data.objects.new('Technical camera',data);sc.collection.objects.link(cam);cam.location=(3.6,-5.2,2.8) if perspective else (0,-7,target[2]);cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler();data.type='ORTHO';data.ortho_scale=scale;sc.camera=cam
 sc.render.image_settings.file_format='PNG';return cam
for ident,h,d in [('csc_orvane_lx12',.78,1.10),('csc_orvane_lx8',.60,.90),('csc_orvane_sb28',1.20,1.35)]:
 bpy.ops.wm.open_mainfile(filepath=str(MODELS/(ident+'.blend')));cam=studio((0,d/2,h/2),3.0)
 if not os.getenv('CSC_RENDER_CHANGED_ONLY'):
  bpy.context.scene.render.filepath=str(OUT/(ident+'_front.png'));bpy.ops.render.render(write_still=True)
 cam.location=(3.6,-5.2,2.8);cam.rotation_euler=(Vector((0,d/2,h/2))-cam.location).to_track_quat('-Z','Y').to_euler()
 bpy.context.scene.render.filepath=str(OUT/(ident+'_perspective.png'));bpy.ops.render.render(write_still=True)
if not os.getenv('CSC_RENDER_CHANGED_ONLY'):
 bpy.ops.wm.open_mainfile(filepath=str(MODELS/'csc_audio_console_mesh.blend'));studio((0,0,.7),2.65,True);bpy.context.scene.render.filepath=str(OUT/'CSC_Relay_24_6_console.png');bpy.ops.render.render(write_still=True)
# Editable installation demo from the actual game meshes and UVs.
bpy.ops.wm.read_factory_settings(use_empty=True);sc=bpy.context.scene;sc.unit_settings.system='METRIC';sc.unit_settings.scale_length=1
img=bpy.data.images.load(str(BASE/'Textures/speakers_atlas.png'));img.pack();mat=bpy.data.materials.new('Actual game atlas');mat.use_nodes=True;bs=mat.node_tree.nodes.get('Principled BSDF');bs.inputs['Roughness'].default_value=.65;tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=img;mat.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color']);mat.node_tree.links.new(tex.outputs['Alpha'],bs.inputs['Alpha']);mat.surface_render_method='DITHERED'
A=R/'common/src/main/resources/assets/concertstage/speakers';cache={}
def add(ident,xyz=(0,0,0),pitch=0):
 if ident not in cache:
  data=json.loads((A/(ident+'.json')).read_text())['near'];verts=[];uv=[]
  for i in range(0,len(data),8):x,y,z,u,v,*n=data[i:i+8];verts.append((x,-z,y));uv.append((u,1-v))
  me=bpy.data.meshes.new(ident);me.from_pydata(verts,[],[tuple(range(i,i+3)) for i in range(0,len(verts),3)]);me.uv_layers.new(name='Atlas UV')
  for loop in me.loops:me.uv_layers.active.data[loop.index].uv=uv[loop.vertex_index]
  me.materials.append(mat);cache[ident]=me
 o=bpy.data.objects.new(ident,cache[ident]);sc.collection.objects.link(o);o.location=(xyz[0],-xyz[2],xyz[1]);o.rotation_euler.x=math.radians(pitch);return o
# Three flown cabinets, real front/rear port links and compatible support.
x=-2.5;h=.78;d=1.10;hy=3.55-.12-.30;hz=d/2;poses=[]
for i in range(3):
 theta=i*3;a=math.radians(theta);oy=hy-h*math.cos(a);oz=hz-h*math.sin(a);poses.append((x,oy,oz,theta));add('csc_orvane_lx12',(x,oy,oz),theta);hy=oy;hz=oz
add('csc_orvane_flying_frame',(x,3.55-.12-.30,d/2))
def point(p,x,y,z):
 a=math.radians(p[3]);return Vector((p[0]+x,p[1]+y*math.cos(a)-z*math.sin(a),p[2]+y*math.sin(a)+z*math.cos(a)))
def member(a,b):
 delta=b-a;o=add('csc_rig_link',a);bl=Vector((delta.x,-delta.z,delta.y));o.rotation_euler=bl.to_track_quat('Z','Y').to_euler()
 # Link's long axis is Blender Z (Minecraft Y).
 o.scale.z=delta.length;return o
for i in range(1,3):
 for side in (-1,1):
  for rear in (False,True):
   xx=side*(2*(.445 if rear else .5)-.018);zz=-d+.025 if rear else -.025
   member(point(poses[i-1],xx,.025,zz),point(poses[i],xx,h*(.88 if rear else 1)-.025,zz))
for side in (-1,1):member(Vector((x+side*.982,3.155,.525)),point(poses[0],side*.982,h-.025,-.025))
# Ground stack, a compact module, a sub and the original digital desk.
add('csc_orvane_ground_frame',(0,0,.55));add('csc_orvane_lx12',(0,.12,.55));add('csc_orvane_lx12',(0,.90,.55))
add('csc_orvane_lx8',(2.5,0,.45));add('csc_orvane_sb28',(4.7,0,.675));add('csc_audio_console_mesh',(.5,0,3.2))
trussmat=bpy.data.materials.new('Unbranded truss steel');trussmat.diffuse_color=(.13,.15,.17,1)
# Generic 50 mm truss test piece above the flying frame, with aligned attachment crossmembers.
def tube(name,a,b,r=.025):
 a=Vector((a[0],-a[2],a[1]));b=Vector((b[0],-b[2],b[1]));delta=b-a;bpy.ops.mesh.primitive_cylinder_add(vertices=12,radius=r,depth=delta.length,location=(a+b)/2);o=bpy.context.object;o.name=name;o.rotation_euler=delta.to_track_quat('Z','Y').to_euler();o.data.materials.append(trussmat)
for y in (3.65,4.05):
 for z in (-.22,.22):tube('Generic test truss chord',(-4,y,z),(-1,y,z))
for xx in (-4,-3.5,-3,-2.94,-2.5,-2.06,-2,-1.5,-1):
 tube('Truss crossmember',(xx,3.65,-.22),(xx,3.65,.22))
 for z in (-.22,.22):tube('Truss vertical',(xx,3.65,z),(xx,4.05,z))
for xx in (-3.75,-3.25,-2.75,-2.25,-1.75):
 for z in (-.22,.22):tube('Truss diagonal',(xx-.25,3.65,z),(xx+.25,4.05,z),.013)
for xx in (-2.94,-2.06):
 a=Vector((xx,3.25,0));b=Vector((-2.5,3.625,0));d=b-a;count=max(1,math.ceil(d.length/.024));direction=Vector((d.x,-d.z,d.y))
 for i in range(count):
  centre=a+d*((i+.5)/count);o=add('csc_chain_link',centre);o.rotation_euler=direction.to_track_quat('Z','Y').to_euler();o.scale.z=d.length/count/.03
  if i%2:o.rotation_euler.rotate_axis('Z',math.pi/2)
cam=studio((.2,.1,1.4),11,True);cam.location=(10,-13,9);cam.rotation_euler=(Vector((.2,.1,1.4))-cam.location).to_track_quat('-Z','Y').to_euler();sc.render.resolution_x=1600;sc.render.resolution_y=1000
bpy.ops.wm.save_as_mainfile(filepath=str(BASE/'CSC_Orvane_Installation_Demo.blend'));sc.render.filepath=str(OUT/'CSC_Orvane_Installation_Demo.png');bpy.ops.render.render(write_still=True)
print('CSC VALIDATED',len(report),'Blender files and all GLBs; renders complete')
