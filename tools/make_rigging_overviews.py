#!/usr/bin/env python3
from pathlib import Path
import json,shutil,tempfile
from PIL import Image,ImageDraw,ImageFont
from render_rigging import render
ROOT=Path(__file__).resolve().parents[1];BB=ROOT/'tools/blockbench/realistic';OUT=ROOT/'tools/realistic_docs/previews';OUT.mkdir(parents=True,exist_ok=True)
NAMES=[('truss_h1_l1','BOX TRUSS · 1 m'),('truss_h1_l2','BOX TRUSS · 2 m'),('truss_h2_l2','TALL BOX TRUSS · 2 m'),('truss_v2_h2','VERTICAL BOX TRUSS'),('truss_corner_3','3-WAY CORNER'),('truss_corner_3_h2','3-WAY CORNER · TALL'),('truss_corner_6','6-WAY CORNER'),('truss_corner_6_h2','6-WAY CORNER · TALL'),('truss_base_v2','BASE PLATE MODULE'),('truss_x30d_l2','TRIANGULAR TRUSS'),('truss_h2_l2_x30d','BOX / TRIANGLE ADAPTER'),('truss_h1_l2_petit','COMPACT BOX TRUSS'),('truss_ladder_l1','LADDER TRUSS · 1 m'),('truss_ladder_l2','LADDER TRUSS · 2 m'),('truss_ladder_v2','VERTICAL LADDER TRUSS'),('truss_junction_90','90-DEGREE JUNCTION'),('truss_junction_t','T JUNCTION'),('truss_junction_cross','CROSS JUNCTION')]
def f(size,bold=False):
 try:return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold'if bold else'')+'.ttf',size)
 except OSError:return ImageFont.load_default()
canvas=Image.new('RGB',(2400,2600),'#111922');d=ImageDraw.Draw(canvas);d.text((60,35),'CONCERT STAGE CRAFT',font=f(59,True),fill='white');d.text((63,115),'ALL TRUSSES  /  18 SHAPES  /  36 EDITABLE BLOCKBENCH MODELS',font=f(27),fill='#81d4ce')
for index,(name,title)in enumerate(NAMES):
 x=60+(index%3)*770;y=200+(index//3)*390;d.rounded_rectangle((x,y,x+740,y+365),radius=14,fill='#23313e')
 for black in[False,True]:
  key=name+('_noir'if black else'');p=OUT/(key+'.png');render(BB/'trusses'/(key+'.bbmodel'),p,(1,.75,-1.6),(350,235));canvas.paste(Image.open(p),(x+10+int(black)*370,y+10));d.text((x+18+int(black)*370,y+251),'MATTE BLACK'if black else'ALUMINIUM',font=f(17),fill='#adc2d5')
 d.text((x+18,y+281),title,font=f(25,True),fill='white');d.text((x+18,y+324),name,font=f(18),fill='#c8d6e2')
d.text((62,2544),'Rendered from actual model geometry. Editable meshes, embedded textures. 16 units = 1 Minecraft block.',font=f(23),fill='#a8bdcf');canvas.save(OUT/'Concert_Stage_Craft_All_Trusses_EN.png')
# Tower detail images supplied alongside the complete source pack.
for name in ['motor_tower_10_corner3','motor_tower_22_corner6_h2_noir']:render(BB/'towers'/(name+'.bbmodel'),OUT/(name+'.png'),(1,.35,-1.6),(650,1250))
doc=json.loads((BB/'towers/motor_tower_10_corner3.bbmodel').read_text());doc['elements']=[e for e in doc['elements']if max(v[1]for v in e['vertices'].values())<36]
with tempfile.TemporaryDirectory()as tmp:
 p=Path(tmp)/'detail.bbmodel';p.write_text(json.dumps(doc));render(p,OUT/'motor_tower_base_detail.png',(1,.65,-1.6),(1000,800))
print('Overview and model renders saved',flush=True)
