"""Compose unmodified in-game control captures into an English technical overview."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
r=Path(__file__).resolve().parents[2];shots=r/'tools/audio_tests/results_preview7/screenshots';out=r/'tools/audio_v2/Renders/CSC_Delay_Planner_Minecraft_Overview_EN.png'
im=Image.new('RGB',(1920,1530),'#111820');d=ImageDraw.Draw(im);font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';heading=ImageFont.truetype(font,34);label=ImageFont.truetype(font,22);small=ImageFont.truetype(font,20)
d.text((32,22),'CONCERT STAGE CRAFT — DELAY PLANNER + CSC RELAY',font=heading,fill='#edf5fb');d.text((32,70),'Forge 1.20.1 • real Minecraft captures • relative coverage estimate • editable 3D console',font=small,fill='#9eb2c5')
for i,(file,text) in enumerate([('08_planner_coverage.png','AUDIENCE COVERAGE — EXISTING SYSTEM / PROPOSED DELAYS'),('09_planner_configuration.png','PER-BUS DELAY — SERVER-CONFIRMED SETTINGS'),('10_planner_world_ghost.png','TRANSLUCENT TOWERS + WORLD COVERAGE OVERLAY'),('11_relay_live_screens.png','REVISED RELAY LCDs — LIVE TRACK / LEVELS / DELAY')]):
 pic=Image.open(shots/file).convert('RGB');pic.thumbnail((920,648));x=30+(i%2)*950;y=113+(i//2)*697;im.paste(pic,(x+(920-pic.width)//2,y));d.text((x,y+655),text,font=small,fill='#cbdff0')
d.text((32,1500),'Placement previews create no world blocks. Coverage is estimated relative gain, not measured SPL.',font=small,fill='#9eb2c5');im.save(out);print(out)
