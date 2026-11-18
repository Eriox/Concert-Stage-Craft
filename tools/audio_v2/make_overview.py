from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
base=Path(__file__).resolve().parent;out=base/'Renders';w,h=1920,1460
im=Image.new('RGB',(w,h),'#edf0f2');d=ImageDraw.Draw(im)
def font(size,bold=False):return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold' if bold else '')+'.ttf',size)
def text(x,y,s,size=22,bold=False,colour='#26343d'):d.text((x,y),s,font=font(size,bold),fill=colour)
d.rectangle((0,0,w,146),fill='#14262f');d.rectangle((54,36,64,113),fill='#52a2af')
text(90,31,'CONCERT STAGE CRAFT',26,True,'#86bec7');text(88,68,'ORVANE AUDIO SYSTEMS',44,True,'#ffffff')
text(58,163,'Original fictional equipment  /  Real scale: 1 metre = 1 Minecraft block',23)
models=[('csc_orvane_lx12','ORVANE LX12','FULL-SIZE LINE ARRAY','2.00 × 0.78 × 1.10 m'),('csc_orvane_lx8','ORVANE LX8','COMPACT LINE ARRAY','1.60 × 0.60 × 0.90 m'),('csc_orvane_sb28','ORVANE SB28','GROUND SUBWOOFER','1.80 × 1.20 × 1.35 m')]
for i,(ident,name,kind,dims) in enumerate(models):
 x=58+i*615;picture=Image.open(out/(ident+'_front.png')).convert('RGB').resize((588,412),Image.Resampling.LANCZOS);im.paste(picture,(x,216))
 text(x+3,645,name,30,True);text(x+3,685,kind,18,True,'#367c89');text(x+3,717,dims,23)
d.line((58,774,1860,774),fill='#bccad0',width=2)
text(58,802,'RELAY 24/6',30,True);text(58,845,'Animated faders, meters, mutes and live displays',19)
text(990,802,'MODULAR RIGGING',30,True);text(990,845,'Six compatible frames for Veyra, Noryx and Orvane',19)
for name,x in [('CSC_Relay_24_6_console.png',58),('CSC_Orvane_Installation_Demo.png',990)]:
 pic=Image.open(out/name).convert('RGB');pic.thumbnail((870,470),Image.Resampling.LANCZOS);im.paste(pic,(x+(870-pic.width)//2,888+(470-pic.height)//2))
d.rectangle((0,1390,w,h),fill='#14262f');text(58,1408,'FORGE 1.20.1  •  EDITABLE BLENDER FILES  •  GAME-READY GLB EXPORTS  •  1.1.1-preview.2',23,True,'#e0edf0')
im.save(out/'CSC_Orvane_Relay_Overview_EN.png',optimize=True)
