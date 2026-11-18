"""Génération .bbmodel V3 (cubes + meshes, multi-textures). Échelle : 16 unités = 1 m. Y haut, X largeur, Z profondeur."""
import json, uuid, math, io, base64, copy, os
import numpy as np
from PIL import Image

def U(): return str(uuid.uuid4())
TEX_DIR = '/home/claude/v3/out/Textures'

# ---------- atlas V3 (512 px) ----------
TILES = {  # nom -> (x, y, taille)
 'film':(0,0,128),'wood':(128,0,128),'tread':(256,0,128),'perf':(384,0,128),'toe':(0,128,128),
 'galva':(128,128,64),'galva_d':(192,128,64),'alu':(128,192,64),'alu_d':(192,192,64),
 'black':(256,128,64),'rubber':(320,128,64),'zinc':(256,192,64),'wedge':(320,192,64),
 'yellow':(384,128,64),'red':(448,128,64),'steel':(384,192,64),'dark':(448,192,64)}
def build_atlas(path):
    rng = np.random.default_rng(11); A = np.zeros((512,512,3))
    def fill(name, base, streak=0, noise=2, fn=None):
        x,y,s = TILES[name]; t = np.zeros((s,s,3)) + np.array(base, float)
        if streak: t += streak*rng.normal(0,1,(s,1,1))
        t += noise*rng.normal(0,1,(s,s,1))
        if fn: fn(t, s)
        A[y:y+s, x:x+s] = t
    def hexdots(t,s):            # film antidérapant : relief hexagonal
        for j in range(0,s,6):
            for i in range((j//6)%2*3, s, 6): t[j:j+2, i:i+2] += 9
    def plywood(t,s):
        yy = np.arange(s)[:,None]; xx = np.arange(s)[None,:]
        t += (6*np.sin(xx/7.0 + 2*np.sin(yy/23.0)))[...,None]
    def tread(t,s):              # tôle larmée alu
        for j in range(4,s,10):
            for i in range((j//10)%2*5, s, 10):
                t[j:j+2, i:i+5] += 25; t[j+2:j+3, i:i+5] -= 15
    def perf(t,s):               # plancher acier perforé
        for j in range(3,s,8):
            for i in range(3,s,8): t[j:j+3, i:i+3] = (40,42,45)
        t[:, :3] += 10
    def galv(t,s):               # fleurage de galvanisation
        for _ in range(40):
            cx,cy,r = rng.integers(0,s,2).tolist()+[int(rng.integers(3,9))]
            t[max(0,cy-r):cy+r, max(0,cx-r):cx+r] += rng.normal(0,5)
    fill('film',(34,35,37),0,2,hexdots); fill('wood',(150,112,70),0,4,plywood)
    fill('tread',(172,176,180),3,3,tread); fill('perf',(150,154,158),2,3,perf)
    fill('toe',(196,160,98),0,5,plywood)
    fill('galva',(160,164,168),2,3,galv); fill('galva_d',(120,124,128),2,3,galv)
    fill('alu',(186,190,194),4,2); fill('alu_d',(140,145,150),4,2)
    fill('black',(30,31,33),1,2); fill('rubber',(22,22,22),0,3)
    fill('zinc',(200,198,180),2,3); fill('wedge',(178,182,186),3,2)
    fill('yellow',(222,182,32),0,3); fill('red',(170,40,34),0,3)
    fill('steel',(96,100,106),2,3); fill('dark',(48,50,54),1,2)
    im = Image.fromarray(np.clip(A,0,255).astype('uint8')); im.save(path); return path

def tex_entry(path, name=None):
    im = Image.open(path); b = io.BytesIO(); im.save(b,'PNG')
    w,h = im.size
    return {'path':'','name':name or os.path.basename(path),'folder':'','namespace':'','id':'0','particle':False,
            'render_mode':'default','visible':True,'mode':'bitmap','saved':True,'uuid':U(),
            'width':w,'height':h,'uv_width':w,'uv_height':h,
            'source':'data:image/png;base64,'+base64.b64encode(b.getvalue()).decode()}

def _frame(a):
    a = a/np.linalg.norm(a); t = np.array([0,1,0]) if abs(a[1]) < 0.9 else np.array([1,0,0])
    u = np.cross(a,t); u /= np.linalg.norm(u); v = np.cross(a,u); return u, v

class Model:
    """Arborescence : chemins de groupes 'A/B'. Texture 0 = atlas V3 sauf indication."""
    def __init__(self, name, atlas=True):
        self.name = name; self.elements = []; self.textures = []; self.tree = {}; self.order = []
        self.nulls = []; self.doc = {}
        if atlas: self.textures.append(tex_entry(os.path.join(TEX_DIR,'csc_v3_atlas.png')))
    # -- groupes
    def _node(self, path):
        parts = path.split('/'); cur = self.tree; key = ''
        for p in parts:
            key = (key+'/'+p) if key else p
            if p not in cur:
                cur[p] = {'_children':[], '_sub':{}, '_order':[], '_origin':[0,0,0]}
                (self.order if cur is self.tree else None)
            cur_node = cur[p]; cur = cur_node['_sub']
        return cur_node
    def _add(self, el, grp):
        self.elements.append(el); self._ensure(grp)['_children'].append(el['uuid'])
    def _ensure(self, path):
        parts = path.split('/'); level = self.tree; order = self.order; node = None
        for p in parts:
            if p not in level:
                level[p] = {'_children':[], '_sub':{}, '_order':[]}; order.append(p)
            node = level[p]; level = node['_sub']; order = node['_order']
        return node
    def null(self, grp, name, pos):
        """Repère sans géométrie : groupe vide dont l'origine porte la coordonnée."""
        node = self._ensure(grp); node['_children'].append({'name':name,'origin':[round(float(v),4) for v in pos],
            'color':0,'uuid':U(),'export':False,'isOpen':False,'visibility':True,'children':[]})
    # -- primitives
    @staticmethod
    def _uv_rect(tile):
        x,y,s = TILES[tile]; return [x+0.5, y+0.5, x+s-0.5, y+s-0.5]
    def cube(self, name, grp, a, b, tile='galva', rot=None, origin=None, tex=0, uv=None, faces_uv=None):
        lo = [min(p,q) for p,q in zip(a, b)]; hi = [max(p,q) for p,q in zip(a, b)]
        r = uv or self._uv_rect(tile)
        faces = {f:{'uv':list(r),'texture':tex} for f in ('north','east','south','west','up','down')}
        if faces_uv:
            for f,v in faces_uv.items(): faces[f] = v
        el = {'name':name,'type':'cube','uuid':U(),'box_uv':False,'rescale':False,'locked':False,
              'from':[round(float(v),4) for v in lo],'to':[round(float(v),4) for v in hi],
              'autouv':0,'color':0,'origin':[round(float(v),4) for v in (origin if origin is not None else [(l+h)/2 for l,h in zip(lo,hi)])],
              'rotation':list(rot) if rot else [0,0,0],'faces':faces,'visibility':True,'export':True}
        self._add(el, grp); return el
    def mesh(self, name, grp, verts, faces, tile='galva', tex=0):
        V = np.array(verts, float); c = V.mean(0); x,y,s = TILES[tile]; vd = {}; fd = {}
        for i,p in enumerate(V): vd[str(i)] = [round(float(q),4) for q in p]
        for fi,f in enumerate(faces):
            P = V[f]; n = np.cross(P[1]-P[0], P[2]-P[0])
            if np.dot(n, P.mean(0)-c) < 0: f = f[::-1]
            k = len(f)
            if k == 4: cs = [(0,0),(1,0),(1,1),(0,1)]
            elif k == 3: cs = [(0,0),(1,0),(.5,1)]
            else: cs = [(.5+.45*math.cos(2*math.pi*j/k), .5+.45*math.sin(2*math.pi*j/k)) for j in range(k)]
            fd[str(fi)] = {'vertices':[str(q) for q in f],
                           'uv':{str(q):[round(x+0.5+cs[j][0]*(s-1),3), round(y+0.5+cs[j][1]*(s-1),3)] for j,q in enumerate(f)},
                           'texture':tex}
        self._add({'name':name,'type':'mesh','uuid':U(),'origin':[0,0,0],'rotation':[0,0,0],'vertices':vd,'faces':fd,
                   'color':0,'visibility':True,'export':True}, grp)
    def tube(self, name, grp, p0, p1, r, sides=8, tile='galva', r1=None, caps=True):
        p0 = np.array(p0,float); p1 = np.array(p1,float); r1 = r if r1 is None else r1
        u,v = _frame(p1-p0); V = []; F = []
        for p,rr in ((p0,r),(p1,r1)):
            for i in range(sides):
                a = 2*math.pi*(i+.5)/sides; V.append(p + rr*(math.cos(a)*u + math.sin(a)*v))
        for i in range(sides):
            j = (i+1) % sides; F.append([i,j,sides+j,sides+i])
        if caps: F += [list(range(sides)), [sides+i for i in range(sides)]]
        self.mesh(name, grp, V, F, tile)
    def disc(self, name, grp, c, axis, r, t, sides=12, tile='galva'):
        c = np.array(c,float); a = np.array(axis,float); a /= np.linalg.norm(a)
        self.tube(name, grp, c - a*t/2, c + a*t/2, r, sides, tile)
    # -- import d'un autre .bbmodel (assemblages)
    def import_bb(self, path, grp, offset=(0,0,0), rot90=0, keep_groups=True, group_shift=None, share_textures=True):
        d = json.load(open(path)); base = len(self.textures)
        gs = {}
        if group_shift:
            def col(o, top):
                for c in o:
                    if isinstance(c, dict): col(c['children'], top)
                    else: gs[c] = top
            for g in d['outliner']:
                if isinstance(g, dict) and g['name'] in group_shift: col(g['children'], g['name'])
        # textures : réutilise une texture identique déjà présente (même nom + même source)
        remap = {}
        for i,t in enumerate(d['textures']):
            j = next((k for k,t2 in enumerate(self.textures) if share_textures and t2['name']==t['name'] and t2.get('source')==t.get('source')), None)
            if j is None:
                t = copy.deepcopy(t); t['uuid'] = U(); self.textures.append(t); j = len(self.textures)-1
            remap[i] = j
        k = rot90 % 4; off = np.array(offset, float)
        def R(p):      # rotation de k*90° autour de Y (x,z)
            x,y,z = p
            for _ in range(k): x,z = z,-x
            return np.array([x,y,z]) + off
        uid = {}
        for e in d['elements']:
            sh = np.array(group_shift[gs[e['uuid']]], float) if e['uuid'] in gs else None
            e = copy.deepcopy(e); nu = U(); uid[e['uuid']] = nu; e['uuid'] = nu
            for f in e['faces'].values():
                if f.get('texture') is not None: f['texture'] = remap[f['texture']]
            if sh is not None:
                if e['type'] == 'mesh': e['origin'] = [float(a)+float(b) for a,b in zip(e.get('origin',[0,0,0]), sh)]
                else:
                    e['from'] = [a+b for a,b in zip(e['from'], sh)]; e['to'] = [a+b for a,b in zip(e['to'], sh)]
                    e['origin'] = [a+b for a,b in zip(e.get('origin',[0,0,0]), sh)]
            if e['type'] == 'mesh':
                o = np.array(e.get('origin',[0,0,0]),float)
                assert not any(e.get('rotation',[0,0,0])), 'rotation de mesh non gérée'
                e['vertices'] = {kk:[round(float(q),4) for q in R(np.array(vv)+o)] for kk,vv in e['vertices'].items()}
                e['origin'] = [0,0,0]
            else:
                if k:
                    assert k in (0,2) or not any(e.get('rotation',[0,0,0])), 'cube tourné + rot90'
                a = R(np.array(e['from'],float)); b = R(np.array(e['to'],float))
                e['from'] = [round(float(q),4) for q in np.minimum(a,b)]; e['to'] = [round(float(q),4) for q in np.maximum(a,b)]
                e['origin'] = [round(float(q),4) for q in R(np.array(e.get('origin',[0,0,0]),float))]
                if k == 2 and any(e.get('rotation',[0,0,0])): e['rotation'] = [-e['rotation'][0], e['rotation'][1], -e['rotation'][2]]
            self.elements.append(e)
        def conv(o):
            out = []
            for c in o:
                if isinstance(c, dict):
                    c2 = copy.deepcopy(c); c2['uuid'] = U(); c2['children'] = conv(c['children'])
                    c2['origin'] = [round(float(q),4) for q in R(np.array(c.get('origin',[0,0,0]),float))]; out.append(c2)
                else: out.append(uid[c])
            return out
        node = self._ensure(grp); node['_children'] += conv(d['outliner'])
    # -- sortie
    def _outliner(self, level, order):
        out = []
        for name in order:
            n = level[name]
            out.append({'name':name,'origin':[0,0,0],'color':0,'uuid':U(),'export':True,'isOpen':False,'visibility':True,
                        'children': self._outliner(n['_sub'], n['_order']) + n['_children']})
        return out
    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        d = {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':self.name,
             'model_identifier':'','visible_box':[1,1,0],'variable_placeholders':'','variable_placeholder_buttons':[],
             'timeline_setups':[],'unhandled_root_fields':{},
             'resolution':{'width':512,'height':512},'elements':self.elements,
             'outliner':self._outliner(self.tree, self.order),'textures':self.textures,'animations':[],'display':{}}
        if self.doc: d['csc_doc'] = self.doc
        json.dump(d, open(path,'w'), ensure_ascii=False)
        return path

# ---------- géométrie monde (tests / aperçus) ----------
def _rotm(rot):
    rx,ry,rz = [math.radians(v) for v in rot]
    Rx = np.array([[1,0,0],[0,math.cos(rx),-math.sin(rx)],[0,math.sin(rx),math.cos(rx)]])
    Ry = np.array([[math.cos(ry),0,math.sin(ry)],[0,1,0],[-math.sin(ry),0,math.cos(ry)]])
    Rz = np.array([[math.cos(rz),-math.sin(rz),0],[math.sin(rz),math.cos(rz),0],[0,0,1]])
    return Rz @ Ry @ Rx
def element_polys(e):
    """Liste de polygones (np.array Nx3) en coordonnées monde."""
    if e['type'] == 'mesh':
        o = np.array(e.get('origin',[0,0,0]),float); R = _rotm(e.get('rotation',[0,0,0]))
        V = {k: R @ np.array(v,float) + o for k,v in e['vertices'].items()}
        return [np.array([V[k] for k in f['vertices']]) for f in e['faces'].values() if len(f['vertices'])>=3]
    if 'from' not in e: return []
    a = np.array(e['from'],float); b = np.array(e['to'],float); o = np.array(e.get('origin',[0,0,0]),float)
    R = _rotm(e.get('rotation',[0,0,0]))
    C = lambda x,y,z: R @ (np.array([x,y,z]) - o) + o
    x0,y0,z0 = a; x1,y1,z1 = b
    q = [[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)],[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
         [(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0)],[(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],
         [(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)],[(x1,y0,z0),(x1,y0,z1),(x1,y1,z1),(x1,y1,z0)]]
    return [np.array([C(*p) for p in f]) for f in q]
