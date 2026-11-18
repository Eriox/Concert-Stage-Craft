import bpy,pathlib,math
from mathutils import Vector
R=pathlib.Path(__file__).resolve().parents[2];B=R/'tools/aurex';M=B/'Models'
bpy.ops.wm.read_factory_settings(use_empty=True);s=bpy.context.scene;s.unit_settings.system='METRIC'
def asset(ident,pos,rot=0):
 with bpy.data.libraries.load(str(M/(ident+'.blend')),link=False) as (src,dst):dst.collections=['HIGH_DETAIL']
 c=dst.collections[0];s.collection.children.link(c);c.name=ident+' assembly';c.hide_render=False;c.hide_viewport=False
 for o in c.all_objects:o.location+=Vector(pos);o.rotation_euler.z+=rot
 return c
asset('csc_aurex_m12',(-1.8,0,.04));asset('csc_aurex_m15_w',(-.1,-.65,0));
for z in (1.55,2.70):asset('csc_aurex_s218_f',(1.7,0,z))
asset('csc_aurex_flying_frame',(1.7,0,3.85));asset('csc_aurex_truss_coupler',(1.7,.425,4.31),math.pi/2)
steel=bpy.data.materials.new('Satin structural aluminium');steel.diffuse_color=(.17,.19,.22,1);steel.use_nodes=True;p=steel.node_tree.nodes.get('Principled BSDF');p.inputs['Base Color'].default_value=(.17,.19,.22,1);p.inputs['Metallic'].default_value=.8;p.inputs['Roughness'].default_value=.45
black=bpy.data.materials.new('Studio floor charcoal');black.diffuse_color=(.018,.021,.026,1)
def rod(name,a,b,r):
 a,b=Vector(a),Vector(b);bpy.ops.mesh.primitive_cylinder_add(vertices=12,radius=r,depth=(b-a).length,location=(a+b)/2);o=bpy.context.object;o.name=name;o.rotation_euler=(b-a).to_track_quat('Z','Y').to_euler();o.data.materials.append(steel);return o
# 300 mm demonstration truss; tube centres and the separate 50 mm collar physically coincide.
for y in (.425,.675):
 for z in (4.375,4.625):rod('50 mm main chord',(-3,y,z),(3,y,z),.025)
for x in [-3+i*.5 for i in range(13)]:
 for y in (.425,.675):rod('End bracing',(x,y,4.375),(x,y,4.625),.01)
 for z in (4.375,4.625):rod('Horizontal bracing',(x,.425,z),(x,.675,z),.01)
for i in range(12):
 x=-3+i*.5
 for y in (.425,.675):rod('Diagonal bracing',(x,y,4.375),(x+.5,y,4.625),.008)
for x in (1.7-.396,1.7+.396):rod('Bridle leg — model only',(x,.55,3.97),(1.7,.425,4.32),.008)
for x in (1.7-.882,1.7+.882):
 for y in (.025,1.075):
  rod('Frame-to-cabinet connection',(x,y,3.825),(x,y,3.875),.012)
  rod('Inter-cabinet connection',(x,y,2.675),(x,y,2.725),.012)
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.005));bpy.context.object.name='Studio ground';bpy.context.object.data.materials.append(black)
s.world=bpy.data.worlds.new('Studio');s.world.use_nodes=True;s.world.node_tree.nodes['Background'].inputs[0].default_value=(.1,.13,.18,1);s.world.node_tree.nodes['Background'].inputs[1].default_value=.4
for loc,power,size in [((-4,-5,7),1800,5),((4,0,7),2200,4),((0,5,7),2500,4)]:
 data=bpy.data.lights.new('Softbox','AREA');data.energy=power;data.size=size;o=bpy.data.objects.new('Softbox',data);s.collection.objects.link(o);o.location=loc;o.rotation_euler=(Vector((0,.5,2))-o.location).to_track_quat('-Z','Y').to_euler()
data=bpy.data.cameras.new('Camera');cam=bpy.data.objects.new('Camera',data);s.collection.objects.link(cam);cam.location=(8,-11,7);cam.rotation_euler=(Vector((0,.5,2.2))-cam.location).to_track_quat('-Z','Y').to_euler();data.type='ORTHO';data.ortho_scale=8;s.camera=cam
s.render.engine='CYCLES';s.cycles.samples=32;s.cycles.use_denoising=False;s.render.resolution_x=1400;s.render.resolution_y=1000;s.render.resolution_percentage=100;s.view_settings.view_transform='AgX';s.view_settings.exposure=-.7
s['CSC_Demo']='Original model assembly; not a certified load/suspension plan';s['CSC_Integration']='Runtime uses existing CSC frame/hoist chain; this truss/coupler arrangement is a Blender demonstration'
bpy.ops.wm.save_as_mainfile(filepath=str(B/'CSC_AUREX_Assembly_Demo.blend'));s.render.filepath=str(B/'Renders/AUREX_Assembly_Demo.png');bpy.ops.render.render(write_still=True)
