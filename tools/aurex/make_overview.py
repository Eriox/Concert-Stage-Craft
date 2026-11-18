from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json
B=Path(__file__).resolve().parent;O=B/'Renders';D=B/'Documentation';F='/usr/share/fonts/truetype/dejavu/DejaVuSans'
font=lambda n,b=False:ImageFont.truetype(F+('-Bold' if b else '')+'.ttf',n)
im=Image.new('RGB',(1800,1150),(16,20,26));d=ImageDraw.Draw(im)
d.text((64,36),'CSC AUREX',font=font(58,True),fill=(245,244,240));d.text((67,108),'ORIGINAL SOUND SYSTEMS  /  BLENDER MODEL OVERVIEW',font=font(22),fill=(190,155,100));d.line((64,155,1736,155),fill=(190,155,100),width=2)
rows=[('csc_aurex_m12','M12','Compact main speaker','Orvane LX8 envelope: 1.60 × 0.60 × 0.90 m'),('csc_aurex_s218_f','S218-F','Dual 18 inch flying subwoofer','1.80 × 1.15 × 1.10 m  /  dedicated flying frame'),('csc_aurex_m15_w','M15-W','Dual-position stage monitor','0.78 × 0.47 × 0.70 m  /  collision envelope')]
for i,(ident,title,sub,dim) in enumerate(rows):
 x=62+i*576
 d.rounded_rectangle((x,185,x+550,1030),radius=16,fill=(27,33,41))
 d.text((x+24,207),title,font=font(36,True),fill=(242,241,237));d.text((x+24,258),sub,font=font(20),fill=(196,202,210))
 a=Image.open(O/(ident+'_perspective.png')).convert('RGB').resize((518,518),Image.Resampling.LANCZOS);im.paste(a,(x+16,300))
 a=Image.open(O/(ident+'_front.png')).convert('RGB').resize((240,240),Image.Resampling.LANCZOS);im.paste(a,(x+21,775))
 a=Image.open(O/(ident+'_rear.png')).convert('RGB').resize((240,240),Image.Resampling.LANCZOS);im.paste(a,(x+289,775))
 d.text((x+29,800),'FRONT',font=font(15,True),fill=(228,229,232));d.text((x+297,800),'REAR',font=font(15,True),fill=(228,229,232));d.text((x+4,1045),dim,font=font(16),fill=(186,193,202))
d.text((64,1100),'Native .blend + GAME READY GLB  •  Original CSC designs  •  Images rendered in Blender, not Minecraft',font=font(20),fill=(153,166,181));im.save(O/'CSC_AUREX_Overview_EN.png')
# UV wireframes correspond to the actual packed GAME_READY BakeUV layer.
for path in D.glob('*_uv.json'):
 edges=json.loads(path.read_text());uv=Image.new('RGB',(1024,1024),(18,22,27));draw=ImageDraw.Draw(uv)
 for face in edges:
  pts=[(int(x*1023),int((1-y)*1023)) for x,y in face]
  if pts:draw.line(pts+[pts[0]],fill=(160,183,204),width=1)
 uv.save(B/'Textures'/(path.stem+'.png'))
print('Overview and UV templates created')
