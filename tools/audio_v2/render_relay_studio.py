import bpy,math
from pathlib import Path
from mathutils import Vector
r=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(r/'Models/csc_audio_console_mesh.blend'))
s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.samples=24;s.cycles.use_denoising=True;s.render.resolution_x=1400;s.render.resolution_y=950;s.render.resolution_percentage=100
s.world=s.world or bpy.data.worlds.new('Studio');s.world.color=(.18,.18,.18)
for pos,power,size in [((-3,-4,5),1300,4),((4,-1,3),950,4),((0,4,5),1500,3)]:
 d=bpy.data.lights.new('Softbox','AREA');d.energy=power;d.size=size;o=bpy.data.objects.new('Softbox',d);s.collection.objects.link(o);o.location=pos;o.rotation_euler=(Vector((0,0,.8))-o.location).to_track_quat('-Z','Y').to_euler()
d=bpy.data.cameras.new('Camera');o=bpy.data.objects.new('Camera',d);s.collection.objects.link(o);s.camera=o;d.type='ORTHO'
for name,pos,target,scale in [('CSC_Relay_24_6_console',(2.5,-3.5,2.2),(0,0,.8),2.8),('CSC_Relay_screens_close',(1.5,-3,2.0),(0,.32,1.20),1.9),('CSC_Relay_24_6_rear',(2,3,2.1),(0,0,.85),2.8),('CSC_Relay_24_6_side',(4,0,1.5),(0,0,.8),1.9)]:
 o.location=pos;o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler();d.ortho_scale=scale;s.render.filepath=str(r/'Renders'/(name+'.png'));bpy.ops.render.render(write_still=True)
print('RELAY RENDERS PASS')
