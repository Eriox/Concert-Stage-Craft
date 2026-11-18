from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
root=Path(__file__).resolve().parents[2];p=root/'common/src/main/resources/assets/concertstage/textures/block/speakers_atlas.png';im=Image.open(p).convert('RGBA');d=ImageDraw.Draw(im)
colors=[(36,39,43),(24,27,30),(61,66,72),(148,155,160),(15,17,20),(47,112,126),(12,25,31),(220,230,232),(220,40,45),(40,210,100),(235,156,36),(8,10,12)]
for i,c in enumerate(colors):d.rectangle((800+i*16,320,815+i*16,335),fill=c+(255,))
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',16)
for i,name in enumerate(['ORVANE LX12','ORVANE LX8','ORVANE SB28']):
 y=400+i*80;d.rectangle((800,y,1023,y+60),fill=(28,32,36,255));d.text((810,y+6),'CSC',font=font,fill=(100,178,190));d.text((810,y+30),name,font=font,fill=(218,226,228))
# Opaque game grille: subtle perforations avoid alpha shimmer and diagonal moire.
d.rectangle((800,700,1023,923),fill=(25,28,31,255))
for yy in range(704,924,8):
 for xx in range(804+(4 if (yy//8)%2 else 0),1024,8):
  d.ellipse((xx-2,yy-2,xx+2,yy+2),fill=(6,8,10,255))
im.save(p)
out=root/'tools/audio_v2/Textures';out.mkdir(exist_ok=True);im.save(out/'speakers_atlas.png')
