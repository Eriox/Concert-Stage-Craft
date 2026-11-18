from pathlib import Path
from PIL import Image,ImageDraw
import random
R=Path(__file__).resolve().parents[2];T=R/'tools/aurex/Textures';T.mkdir(exist_ok=True)
im=Image.open(R/'common/src/main/resources/assets/concertstage/textures/block/speakers_atlas.png').convert('RGBA');d=ImageDraw.Draw(im);rng=random.Random(21812)
# Shared UV layout, separate texture: legacy cabinets remain byte-for-byte unchanged.
for y in range(320,336):
 for x in range(800,816):
  n=rng.randrange(-2,3);d.point((x,y),fill=(23+n,25+n,28+n,255))
d.rectangle((800,700,1023,923),fill=(18,20,23,255))
for y in range(702,924,4):
 for x in range(802+(2 if y//4%2 else 0),1024,4):d.rectangle((x,y,x+1,y+1),fill=(4,5,7,255))
im.save(T/'aurex_atlas.png');im.save(R/'common/src/main/resources/assets/concertstage/textures/block/aurex_atlas.png')
normal=Image.new('RGB',(256,256));rough=Image.new('L',(256,256))
for y in range(256):
 for x in range(256):normal.putpixel((x,y),(128+rng.randrange(-4,5),128+rng.randrange(-4,5),255));rough.putpixel((x,y),rng.randrange(180,197))
normal.save(T/'aurex_coating_normal.png');rough.save(T/'aurex_coating_roughness.png')
