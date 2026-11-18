import bpy, math
from mathutils import Vector
from pathlib import Path
root=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(root/'Models/csc_audio_console_mesh.blend'))
sc=bpy.context.scene;sc.render.engine='CYCLES';sc.cycles.samples=20;sc.cycles.use_denoising=True;sc.render.resolution_x=1200;sc.render.resolution_y=800;sc.render.resolution_percentage=100
sc.world=sc.world or bpy.data.worlds.new('Rear studio world');sc.world.color=(.16,.16,.16)
for pos,power,size in [((2,3,4),1000,4),((-3,2,2),700,3),((0,-3,4),900,3)]:
 data=bpy.data.lights.new('Rear studio softbox','AREA');data.energy=power;data.shape='DISK';data.size=size;o=bpy.data.objects.new(data.name,data);sc.collection.objects.link(o);o.location=pos;o.rotation_euler=(Vector((0,0,.8))-o.location).to_track_quat('-Z','Y').to_euler()
camdata=bpy.data.cameras.new('Rear inspection camera');cam=bpy.data.objects.new(camdata.name,camdata);sc.collection.objects.link(cam);cam.location=(2.5,4,2);cam.rotation_euler=(Vector((0,0,.8))-cam.location).to_track_quat('-Z','Y').to_euler();camdata.type='ORTHO';camdata.ortho_scale=2.7;sc.camera=cam
sc.render.filepath=str(root/'Renders/CSC_Relay_24_6_rear.png');bpy.ops.render.render(write_still=True)
