#!/usr/bin/env python3
"""CPU renders of actual BB vertices and UVs; these are not Minecraft screenshots."""
import json,base64,io,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont,ImageFilter
def render(path,output,eye=(1,.7,-1.5),size=(1100,650)):
 d=json.loads(Path(path).read_text());tex=np.array(Image.open(io.BytesIO(base64.b64decode(d['textures'][0]['source'].split(',')[1]))).convert('RGB'))
 verts=[];tris=[];uvs=[]
 def add(vs,uv):
  for j in range(1,len(vs)-1):
   idx=len(verts);verts.extend([vs[0],vs[j],vs[j+1]]);tris.append([idx,idx+1,idx+2]);uvs.append([uv[0],uv[j],uv[j+1]])
 for e in d['elements']:
  if not e.get('visibility',True):continue
  if e.get('type')=='mesh':
   for f in e['faces'].values():
    keys=f['vertices'];add([e['vertices'][k]for k in keys],[f['uv'][k]for k in keys])
  else:
   a,b=e['from'],e['to'];x0,y0,z0=a;x1,y1,z1=b
   sides={'north':[[x1,y0,z0],[x0,y0,z0],[x0,y1,z0],[x1,y1,z0]],'south':[[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]],'east':[[x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1]],'west':[[x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0]],'up':[[x0,y1,z0],[x0,y1,z1],[x1,y1,z1],[x1,y1,z0]],'down':[[x0,y0,z1],[x0,y0,z0],[x1,y0,z0],[x1,y0,z1]]}
   for key,vs in sides.items():
    u0,v0,u1,v1=e['faces'][key]['uv'];add(vs,[[u0,v1],[u1,v1],[u1,v0],[u0,v0]])
 v=np.array(verts);eye=np.array(eye,dtype=float);eye/=np.linalg.norm(eye);right=np.cross(eye,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,eye)
 xy=np.stack([v@right,-v@up],axis=1);dep=v@eye;lo=xy.min(axis=0);hi=xy.max(axis=0);w,h=size;scale=min((w-40)/(hi[0]-lo[0]),(h-40)/(hi[1]-lo[1]));xy=(xy-(hi+lo)/2)*scale+[w/2,h/2-12]
 bg=Image.new('RGB',size,(235,237,240));sh=Image.new('RGBA',size,(0,0,0,0));sd=ImageDraw.Draw(sh);sd.ellipse((w*.18,h*.73,w*.83,h*.89),fill=(15,19,24,22));sh=sh.filter(ImageFilter.GaussianBlur(22));bg=Image.alpha_composite(bg.convert('RGBA'),sh).convert('RGB');pix=np.array(bg).astype(float);zbuf=np.full((h,w),-np.inf)
 light=np.array([-.35,.85,-.4]);light/=np.linalg.norm(light)
 for ids,uv in zip(tris,uvs):
  p=xy[ids];zz=dep[ids];uv=np.array(uv);minx=max(0,int(np.floor(p[:,0].min())));maxx=min(w-1,int(np.ceil(p[:,0].max())));miny=max(0,int(np.floor(p[:,1].min())));maxy=min(h-1,int(np.ceil(p[:,1].max())))
  if minx>maxx or miny>maxy:continue
  den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
  if abs(den)<1e-9:continue
  yy,xx=np.mgrid[miny:maxy+1,minx:maxx+1];xx=xx+.5;yy=yy+.5
  a=((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/den
  b=((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/den;c=1-a-b;z=a*zz[0]+b*zz[1]+c*zz[2]
  mask=(a>=-1e-7)&(b>=-1e-7)&(c>=-1e-7)&(z>zbuf[miny:maxy+1,minx:maxx+1]+1e-5)
  if not mask.any():continue
  uu=a*uv[0,0]+b*uv[1,0]+c*uv[2,0];vv=a*uv[0,1]+b*uv[1,1]+c*uv[2,1]
  colors=tex[np.clip(vv.astype(int),0,tex.shape[0]-1),np.clip(uu.astype(int),0,tex.shape[1]-1)].astype(float)
  norm=np.cross(v[ids[1]]-v[ids[0]],v[ids[2]]-v[ids[0]]);norm/=max(1e-9,np.linalg.norm(norm));shade=.65+.65*abs(norm@light)
  colors=np.clip(colors*shade*1.03,0,255)
  pix[miny:maxy+1,minx:maxx+1][mask]=colors[mask];zbuf[miny:maxy+1,minx:maxx+1][mask]=z[mask]
 out=Image.fromarray(pix.astype('uint8'));out.save(output);return out
