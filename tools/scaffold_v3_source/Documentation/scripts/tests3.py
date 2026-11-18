"""Tests géométriques des passages : intersections réelles entre l'échafaudage/plateforme et les tours de la 1.6.0."""
import sys, json, math, numpy as np
from scipy.spatial import cKDTree, ConvexHull
sys.path.insert(0,'/home/claude/v3/tools')
from lib3 import element_polys
TOWERS='/home/claude/v160/concert-stage/tools/blockbench/realistic/towers'
EPS=0.03
def top_groups(d):
    els={e['uuid']:e for e in d['elements']}; m={}
    def col(g, top):
        for c in g['children']:
            if isinstance(c,dict): col(c, top)
            else: m[c]=top
    for g in d['outliner']:
        if isinstance(g,dict): col(g, g['name'])
    return m
def convex(polys):
    P=np.vstack(polys); c=P.mean(0); planes=[]
    for q in polys:
        n=np.cross(q[1]-q[0],q[2]-q[0]); nn=np.linalg.norm(n)
        if nn<1e-10: continue
        n/=nn; dd=n@q[0]
        if n@c-dd>0: n,dd=-n,-dd
        planes.append((n,dd))
    N=np.array([p[0] for p in planes]); D=np.array([p[1] for p in planes]); return N,D,P
_BARY={}
def _bary(n):
    if n not in _BARY:
        u,v=np.meshgrid(np.arange(n+1),np.arange(n+1)); m=(u+v)<=n
        _BARY[n]=(u[m]/n, v[m]/n)
    return _BARY[n]
def sample(polys, step=0.3):
    out=[]
    for q in polys:
        for i in range(1,len(q)-1):
            a,b,c=q[0],q[i],q[i+1]; L=max(np.linalg.norm(b-a),np.linalg.norm(c-a),np.linalg.norm(c-b))
            n=max(1,min(48,int(math.ceil(L/step)))); u,v=_bary(n)
            out.append(a+np.outer(u,b-a)+np.outer(v,c-a))
    return np.vstack(out) if out else np.zeros((0,3))
class Solid:
    def __init__(s, name, polys):
        s.name=name; s.N,s.D,s.P=convex(polys); s.c=s.P.mean(0); s.r=np.linalg.norm(s.P-s.c,axis=1).max()
        ext=np.ptp(s.P,axis=0); step=float(np.clip(np.sort(ext)[1]/2,0.12,0.5)) if len(ext) else 0.5
        s.pts=sample(polys, step)
    def rotated(s, k, cx=8.0, cz=8.0):
        def R(X):
            X=np.array(X,float); x=X[...,0]-cx; z=X[...,2]-cz
            for _ in range(k): x,z=z,-x
            Y=X.copy(); Y[...,0]=x+cx; Y[...,2]=z+cz; return Y
        def Rv(V):
            V=np.array(V,float); x=V[...,0]; z=V[...,2]
            for _ in range(k): x,z=z,-x
            W=V.copy(); W[...,0]=x; W[...,2]=z; return W
        o=Solid.__new__(Solid); o.name=s.name; o.N=Rv(s.N); o.P=R(s.P); o.c=R(s.c[None])[0]; o.r=s.r; o.pts=R(s.pts)
        o.D=s.D + o.N@np.array([cx,0,cz]) - Rv(np.array([[cx,0,cz]]))[0]@o.N.T*0 - (s.N@np.array([cx,0,cz]))
        # plan n·x=d ; x'=R(x-c)+c ; n'=Rn ; d'=n'·c + (d - n·c)
        o.D=o.N@np.array([cx,0,cz]) + (s.D - s.N@np.array([cx,0,cz]))
        return o
    def moved(s, t):
        o=Solid.__new__(Solid); o.name=s.name; o.N=s.N; o.D=s.D+s.N@np.array(t,float); o.P=s.P+t; o.c=s.c+t; o.r=s.r; o.pts=s.pts+t; return o
    def inside(s, X):
        if len(X)==0: return np.zeros(0,bool)
        return np.all(X@s.N.T - s.D < -EPS, axis=1)
def load_solids(path, filt=None, xf=None, ymax=None):
    d=json.load(open(path)); tg=top_groups(d); out=[]
    for e in d['elements']:
        if filt and not filt(e, tg.get(e['uuid'],'')): continue
        P=element_polys(e)
        if not P: continue
        if xf: P=[xf(q) for q in P]
        if ymax is not None and min(q[:,1].min() for q in P) > ymax: continue
        out.append(Solid(e['name'], P))
    return out
def swept(solids, dy):
    out=[]
    for s in solids:
        V=np.vstack([s.P, s.P+[0,dy,0]])
        try: h=ConvexHull(V)
        except Exception: continue
        polys=[V[sim] for sim in h.simplices]
        out.append(Solid(s.name+' (balayé)', polys))
    return out
def _aabb(L):
    lo=np.array([s.P.min(0) for s in L]); hi=np.array([s.P.max(0) for s in L]); return lo,hi
def collide(A, B, limit=5):
    """Intersections entre deux listes de solides convexes : pré-filtre AABB puis points de surface dans l'autre solide (deux sens)."""
    hits=[]
    if not A or not B: return hits
    Alo,Ahi=_aabb(A)
    for sb in B:
        lo,hi=sb.P.min(0)-EPS,sb.P.max(0)+EPS
        m=np.all(Alo<hi,axis=1)&np.all(Ahi>lo,axis=1)
        for i in np.nonzero(m)[0]:
            sa=A[i]
            pa=sa.pts[np.all((sa.pts>lo)&(sa.pts<hi),axis=1)]
            ins=sb.inside(pa) if len(pa) else np.zeros(0,bool)
            if ins.any():
                hits.append((sa.name, sb.name, pa[ins][0].round(2).tolist()))
            else:
                l2,h2=sa.P.min(0)-EPS,sa.P.max(0)+EPS
                pb=sb.pts[np.all((sb.pts>l2)&(sb.pts<h2),axis=1)]
                ins=sa.inside(pb) if len(pb) else np.zeros(0,bool)
                if ins.any(): hits.append((sa.name, sb.name, pb[ins][0].round(2).tolist()))
            if len(hits)>=limit: return hits
    return hits
def tower_xf(ax, az, k, y0=0):
    def f(q):
        x=q[:,0]-8; z=q[:,2]-8
        for _ in range(k): x,z=z,-x
        return np.c_[x+ax, q[:,1]+y0, z+az]
    return f
def is_carriage(e,g): return g.startswith('Chariot')
