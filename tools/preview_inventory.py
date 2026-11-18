#!/usr/bin/env python3
"""Exact exported mesh projection, offline contact sheet; never presented as a Minecraft screenshot."""
import json,math,textwrap
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
import integrate_platforms as p
import improve_inventory as inv
A=p.AS;DOC=p.DOC

def render(quads,display,textures,size=112):
 rx,ry=np.radians(display['rotation'][:2]);R=np.array([[1,0,0],[0,math.cos(rx),-math.sin(rx)],[0,math.sin(rx),math.cos(rx)]])@np.array([[math.cos(ry),0,math.sin(ry)],[0,1,0],[-math.sin(ry),0,math.cos(ry)]])
 image=np.zeros((size,size,4),dtype=np.uint8);zb=np.full((size,size),-np.inf);light=np.array([-.3,.5,1.]);light/=np.linalg.norm(light)
 for slot,q in quads:
  vs=((q[:,:3]-.5)*display['scale'])@R.T+np.array(display['translation'])/16;xy=vs[:,:2]*[size,-size]+size/2
  for indices in [[0,1,2],[0,2,3]]:
   pts=xy[indices];zz=vs[indices,2];uv=q[indices,3:5];x0=max(0,int(np.floor(pts[:,0].min())));x1=min(size-1,int(np.ceil(pts[:,0].max())));y0=max(0,int(np.floor(pts[:,1].min())));y1=min(size-1,int(np.ceil(pts[:,1].max())))
   if x1<x0 or y1<y0:continue
   den=np.cross(pts[1]-pts[2],pts[0]-pts[2])
   if abs(den)<1e-10:continue
   yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
   a=((pts[1,0]-pts[2,0])*(yy-pts[2,1])-(pts[1,1]-pts[2,1])*(xx-pts[2,0]))/den;b=((pts[2,0]-pts[0,0])*(yy-pts[2,1])-(pts[2,1]-pts[0,1])*(xx-pts[2,0]))/den;c=1-a-b;z=a*zz[0]+b*zz[1]+c*zz[2];mask=(a>=-1e-7)&(b>=-1e-7)&(c>=-1e-7)&(z>zb[y0:y1+1,x0:x1+1]+1e-8)
   if not mask.any():continue
   uvv=a[...,None]*uv[0]+b[...,None]*uv[1]+c[...,None]*uv[2];tex=textures[slot];col=tex[np.clip((uvv[:,:,1]*tex.shape[0]).astype(int),0,tex.shape[0]-1),np.clip((uvv[:,:,0]*tex.shape[1]).astype(int),0,tex.shape[1]-1)].copy();normal=q[0,5:8]@R.T;shade=.63+.37*abs(normal@light);col[:,:,:3]=np.clip(col[:,:,:3].astype(float)*shade,0,255);mask&=col[:,:,3]>=128;image[y0:y1+1,x0:x1+1][mask]=col[mask];zb[y0:y1+1,x0:x1+1][mask]=z[mask]
 return Image.fromarray(image)

def main():
 lang=json.loads((A/'lang/en_us.json').read_text());rigging=json.loads((DOC/'inventory_models.json').read_text());names=[x['id']for x in rigging]+p.NAMES;W=1900;cw=186;ch=177;top=172;H=top+math.ceil(len(names)/10)*ch+60;canvas=Image.new('RGB',(W,H),'#14202d');d=ImageDraw.Draw(canvas);fontpath='/usr/share/fonts/truetype/dejavu/';font=lambda n,b=False:ImageFont.truetype(fontpath+('DejaVuSans-Bold.ttf'if b else'DejaVuSans.ttf'),n)
 d.text((24,22),'CONCERT STAGE CRAFT',font=font(43,True),fill='white');d.text((25,77),'1.5.0 • INVENTORY MODEL CONTACT SHEET',font=font(25),fill='#8fc4ee');d.text((25,120),'90 actual 3D models • normalized isometric views • offline preview, not an in-game screenshot',font=font(21),fill='#becddd')
 for i,key in enumerate(names):
  model=json.loads((A/'models/item'/(key+'.json')).read_text());obj=A/model['model'].split(':')[1]
  if key in p.NAMES:quads=p.obj_quads(obj);textures=[np.array(Image.open(A/'textures/block/stage'/(n+'.png')).convert('RGBA'))for n in p.TEXTURES]
  else:quads=inv.read_obj(obj);textures=[np.array(Image.open(A/'textures/block'/('rigging_detail'+('_noir'if key.endswith('_noir')else'')+'.png')).convert('RGBA'))]
  thumb=render(quads,model['display']['gui'],textures);x=20+i%10*cw;y=top+i//10*ch;d.rounded_rectangle((x,y,x+cw-7,y+ch-7),radius=6,fill='#e6ebf0');canvas.paste(thumb,(x+8,y+1),thumb);small=thumb.resize((16,16),Image.Resampling.LANCZOS).resize((48,48),Image.Resampling.NEAREST);canvas.paste(small,(x+124,y+35),small)
  d=ImageDraw.Draw(canvas);label=lang.get('block.concertstage.'+key,key).replace('Motorised truss tower','Tower').replace(' blocks',' m').replace(' (black)',' / black')
  lines=textwrap.wrap(label,width=23)[:3]
  for j,line in enumerate(lines):d.text((x+7,y+115+j*16),line,font=font(12,True),fill='#213245')
  print(i+1,key,flush=True)
 d.text((24,H-39),'Large view + 16 px sample enlarged ×3. Models are shown at different scales. Geometry and textures come from the exported assets.',font=font(18),fill='#becddd');dest=DOC/'Inventory_Models_EN.png';canvas.save(dest,optimize=True);print(dest,flush=True)
if __name__=='__main__':main()
