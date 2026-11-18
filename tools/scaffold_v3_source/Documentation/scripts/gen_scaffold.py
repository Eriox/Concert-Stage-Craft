"""Échafaudage multidirectionnel CSC (inspiré des systèmes à rosaces type Layher Allround) + passages de tours."""
import sys, os, math, json
sys.path.insert(0,'/home/claude/v3/tools')
from lib3 import *
OUT='/home/claude/v3/out/CSC_StagePlatforms_Scaffolding_V3'
# --- constantes (unités : 16 = 1 m) ---
RT   = 0.386        # tube Ø48,3 mm
RR   = 0.992        # rosace Ø124 mm
TR   = 0.144        # épaisseur rosace 9 mm
Y0   = 7.2          # 1re rosace : 0,45 m au-dessus du sol (sur le collier de base)
STEP = 8.0          # rosaces tous les 0,5 m
COLLAR=(1.6, 9.6)   # collier de base (sur l'écrou du vérin)
DECK = 32.0         # niveau plancher 2,00 m
YD   = DECK-0.8     # rosace du niveau plancher (31,2)
G = {'std':'Vertical Standards','led':'Horizontal Ledgers','dia':'Diagonal Braces','plat':'Platforms','guard':'Guardrails',
     'con':'Connectors','frame':'Main Frame','clr':'Tower Clearance','att':'Attachment Points'}

class Scaf:
    def __init__(s, name, lod=False):
        s.m = Model(name); s.lod = lod; s.sides = 4 if lod else 10
    # ---------------- montants ----------------
    def column(s, x, z, top, base=True, lowest=None):
        m = s.m
        if base:
            if s.lod:
                m.cube('Vérin de base','Connectors',(x-1.2,0,z-1.2),(x+1.2,0.16,z+1.2),'galva_d')
                m.cube('Tige de vérin','Connectors',(x-0.3,0.16,z-0.3),(x+0.3,COLLAR[0],z+0.3),'zinc')
            else:
                m.cube('Platine de vérin 150 × 150','Connectors',(x-1.2,0,z-1.2),(x+1.2,0.128,z+1.2),'galva_d')
                m.tube('Tige filetée de vérin Ø38','Connectors',(x,0.128,z),(x,COLLAR[0]+2.4,z),0.3,8,'zinc')
                m.cube('Écrou à ailettes du vérin','Connectors',(x-0.75,COLLAR[0]-0.32,z-0.18),(x+0.75,COLLAR[0],z+0.18),'zinc')
                m.cube('Écrou à ailettes du vérin','Connectors',(x-0.18,COLLAR[0]-0.32,z-0.75),(x+0.18,COLLAR[0],z+0.75),'zinc')
            s._tube_v('Collier de base à rosace', x, z, COLLAR[0], COLLAR[1])
            s.rosette(x, z, Y0)
        y = COLLAR[1]
        while y < top - 0.01:
            L = 48 if top - y >= 48 else (32 if top - y >= 32 else (16 if top - y >= 16 else 8))
            s._tube_v(f'Montant {L/16:g} m · tube Ø48,3', x, z, y, y+L)
            k = 0
            while y + 5.6 + STEP*k < y + L:
                s.rosette(x, z, y + 5.6 + STEP*k); k += 1
            if not s.lod: s.m.cube('Emboîtement du montant (spigot)','Connectors',(x-0.45,y+L-0.25,z-0.45),(x+0.45,y+L+0.05,z+0.45),'galva_d')
            y += L
        if not s.lod: s.m.cube('Bouchon de montant','Connectors',(x-0.42,y,z-0.42),(x+0.42,y+0.12,z+0.42),'black')
    def _tube_v(s, name, x, z, y0, y1):
        if s.lod: s.m.cube(name, G['std'], (x-RT,y0,z-RT), (x+RT,y1,z+RT), 'galva')
        else: s.m.tube(name, G['std'], (x,y0,z), (x,y1,z), RT, s.sides, 'galva')
    def rosette(s, x, z, y):
        if s.lod: return
        s.m.tube('Rosace 8 trous Ø124', G['con'], (x,y-TR/2,z), (x,y+TR/2,z), RR, 16, 'galva_d')
    # ---------------- lisses ----------------
    def ledger(s, a, b, y, kind='O', grp=None):
        """a, b = (x, z) axes de montants ; kind 'O' tube rond, 'U' profilé porte-plancher."""
        m = s.m; a = np.array(a,float); b = np.array(b,float); d = b - a; L = np.linalg.norm(d); u = d / L
        grp = grp or G['led']; p0 = a + u*RR; p1 = b - u*RR
        nm = f'Lisse {"U" if kind=="U" else "O"} {L/16:.2f} m'
        if kind == 'U' or s.lod:
            w = 0.5 if kind == 'U' else 2*RT; h = 0.8 if kind == 'U' else 2*RT
            lo = np.minimum(p0,p1); hi = np.maximum(p0,p1)
            if abs(u[0]) > 0.5: A=(lo[0], y-h/2, a[1]-w/2); B=(hi[0], y+h/2, a[1]+w/2)
            else:               A=(a[0]-w/2, y-h/2, lo[1]); B=(a[0]+w/2, y+h/2, hi[1])
            m.cube(nm, grp, A, B, 'galva')
        else:
            m.tube(nm, grp, (p0[0],y,p0[1]), (p1[0],y,p1[1]), RT, s.sides, 'galva')
        if not s.lod:
            for p, sg in ((a,1),(b,-1)):
                c = p + u*sg*(RR+0.2); w = np.array([-u[1], u[0]])
                m.cube('Tête de lisse moulée', G['con'], (min(c[0]-0.3*abs(u[0])-0.35*abs(w[0]), c[0]+0.3*abs(u[0])+0.35*abs(w[0])) , y-0.45, c[1]-0.3*abs(u[1])-0.35*abs(w[1])),
                       (c[0]+0.3*abs(u[0])+0.35*abs(w[0]), y+0.45, c[1]+0.3*abs(u[1])+0.35*abs(w[1])), 'galva_d')
                cw = p + u*sg*(RR-0.35)
                m.cube('Clavette de serrage', G['con'], (cw[0]-0.08, y-0.75, cw[1]-0.08), (cw[0]+0.08, y+0.55, cw[1]+0.08), 'wedge')
    def diagonal(s, a, b, y0, y1):
        a = np.array(a,float); b = np.array(b,float); u = (b-a)/np.linalg.norm(b-a)
        p0 = (a[0]+u[0]*RR, y0+0.9, a[1]+u[1]*RR); p1 = (b[0]-u[0]*RR, y1-0.9, b[1]-u[1]*RR)
        s.m.tube(f'Diagonale {np.linalg.norm(b-a)/16:.2f} × {(y1-y0)/16:.2f} m', G['dia'], p0, p1, RT, 4 if s.lod else 8, 'galva')
        if not s.lod:
            for p,(x,yy,z) in ((a,(a[0]+u[0]*(RR+0.2), y0+0.6, a[1]+u[1]*(RR+0.2))),(b,(b[0]-u[0]*(RR+0.2), y1-0.6, b[1]-u[1]*(RR+0.2)))):
                s.m.cube('Tête de diagonale articulée', G['con'], (x-0.3,yy-0.45,z-0.3), (x+0.3,yy+0.45,z+0.3), 'galva_d')
    # ---------------- planchers ----------------
    def decks(s, x0, x1, z0, z1, along='z', y=DECK):
        """Remplit un rectangle avec planchers acier 0,32 m + complément bois."""
        m = s.m; T = 0.64
        span = (z1-z0) if along=='z' else (x1-x0); width = (x1-x0) if along=='z' else (z1-z0)
        n = int((width-0.1)//5.12); rest = width - n*5.12
        if s.lod:
            A=(x0,y-T,z0); B=(x1,y,z1); m.cube('Plancher (LOD)', G['plat'], A, B, 'perf'); return
        off = rest/2
        def rect(w0, w1, tile, name):
            if along=='z': A=(x0+w0, y-T, z0); B=(x0+w1, y, z1)
            else:          A=(x0, y-T, z0+w0); B=(x1, y, z0+w1)
            m.cube(name, G['plat'], A, B, tile)
        if off > 0.05: rect(0, off, 'wood', f'Complément bois {off*62.5:.0f} mm')
        for i in range(n):
            w0 = off + i*5.12
            rect(w0+0.04, w0+5.08, 'perf', f'Plancher acier perforé 0,32 × {span/16:.2f} m')
            for e in (0, 1):     # griffes d'accrochage
                if along=='z': zz = z0-0.25 if e==0 else z1-0.1; A=(x0+w0+0.6, y-1.0, zz); B=(x0+w0+4.5, y-0.5, zz+0.35)
                else:          xx = x0-0.25 if e==0 else x1-0.1; A=(xx, y-1.0, z0+w0+0.6); B=(xx+0.35, y-0.5, z0+w0+4.5)
                m.cube('Griffe de plancher', G['con'], A, B, 'galva_d')
        if width-off-n*5.12 > 0.05: rect(off+n*5.12, width, 'wood', f'Complément bois {(width-off-n*5.12)*62.5:.0f} mm')
    def toeboard(s, a, b, y=DECK, inward=(0,0)):
        """Plinthe 150 × 30 mm posée sur le plancher le long du segment a→b (axes x,z), décalée vers l'intérieur."""
        a = np.array(a,float); b = np.array(b,float); ix, iz = inward
        if abs(a[0]-b[0]) > abs(a[1]-b[1]):
            z = a[1] + iz*(RT+0.1); A=(min(a[0],b[0])+RR, y, z if iz>0 else z-0.48); B=(max(a[0],b[0])-RR, y+2.4, z+0.48 if iz>0 else z)
        else:
            x = a[0] + ix*(RT+0.1); A=(x if ix>0 else x-0.48, y, min(a[1],b[1])+RR); B=(x+0.48 if ix>0 else x, y+2.4, max(a[1],b[1])-RR)
        s.m.cube('Plinthe bois 150 mm', G['guard'], A, B, 'toe')
    def guard(s, a, b, y=DECK):
        s.ledger(a, b, y+8-0.8, 'O', G['guard']); s.ledger(a, b, y+16-0.8, 'O', G['guard'])
    def gate(s, a, b, y=DECK):
        a = np.array(a,float); b = np.array(b,float); u = (b-a)/np.linalg.norm(b-a); c = (a+b)/2; L = np.linalg.norm(b-a)-2*RR-0.4
        p0 = c - u*L/2; p1 = c + u*L/2
        for yy in (y+0.9, y+15.2):
            s.m.tube('Portillon autofermant · traverse', G['guard'], (p0[0],yy,p0[1]), (p1[0],yy,p1[1]), 0.25, 6, 'yellow')
        for p in (p0, p1):
            s.m.tube('Portillon autofermant · montant', G['guard'], (p[0],y+0.9,p[1]), (p[0],y+15.2,p[1]), 0.25, 6, 'yellow')
    def ladder(s, x, z, y0, y1, face='z+'):
        """Échelle alu 0,40 m accrochée sur la face extérieure."""
        m = s.m; w = 3.2; off = 1.6
        zz = z + off if face=='z+' else z - off
        for sx in (-w, w):
            m.cube('Montant d’échelle aluminium', 'Access', (x+sx-0.2, y0, zz-0.35), (x+sx+0.2, y1+16, zz+0.35), 'alu')
        k = 0
        while y0 + 2 + 4.5*k < y1 + 15:
            yy = y0 + 2 + 4.5*k; m.cube('Échelon antidérapant', 'Access', (x-w, yy-0.2, zz-0.25), (x+w, yy+0.2, zz+0.25), 'alu_d'); k += 1
        for sx in (-w, w):
            m.cube('Crochet d’accrochage', 'Access', (x+sx-0.25, y1-0.2, zz-1.6*(1 if face=='z+' else -1) if face!='z+' else z+0.2), (x+sx+0.25, y1+0.5, zz), 'galva_d')
        m.cube('Patin d’échelle', 'Access', (x-w-0.4, y0, zz-0.5), (x+w+0.4, y0+0.3, zz+0.5), 'rubber')
    def clearance(s, name, x0, x1, z0, z1, y0, y1, note):
        """Repère technique sans géométrie (coins du volume réservé)."""
        g = G['clr'] + '/' + name
        for (x,z) in ((x0,z0),(x1,z0),(x1,z1),(x0,z1)):
            s.m.null(g, f'Coin x{x:.2f} z{z:.2f} (y {y0:g}→{y1:g})', (x, y0, z))
        s.m.doc.setdefault('clearances', []).append({'nom':name,'x':[x0,x1],'z':[z0,z1],'y':[y0,y1],'note':note})

# ======================= géométries de passage =======================
# relevé 1.6.0 : mât 14,5 u ; enveloppe mât + chaîne motrice = 9,78 u depuis l'axe (toutes orientations)
ENV_MAST = 9.78
ENV_SLEEVE = 9.78     # chariot : −0,75…16,9 / −1,66…16,7 dans la cellule → ≤ 9,78 u depuis l'axe (chaîne incluse)
CLEAR = {'Mast': 11.56, 'Sleeve': 15.56}   # demi-ouverture libre (entre rosaces), voir doc
TOP = 48+9.6        # montants jusqu'à 3,6 m (garde-corps 1 m au-dessus du plancher)
ARM = 22.6          # portée max des stabilisateurs des tours (21,9 u tour 22) + marge

def arm_cross(ax, az, a, b, y):
    """Vrai si une lisse basse a→b passerait au-dessus d'un bras de stabilisateur (croix de la tour)."""
    if y > 12.5: return False
    (x0,z0),(x1,z1) = a, b
    if abs(z0-z1) < 1e-6:     # lisse selon X, coupe le bras selon Z (x = ax)
        return min(x0,x1) < ax < max(x0,x1) and abs(z0-az) < ARM
    return min(z0,z1) < az < max(z0,z1) and abs(x0-ax) < ARM

def bay_grid(xs, zs, holes, ax, az, name, s, decks=True, guard_edges=None, access=None):
    """Construit une grille de travées. holes = set de (i,j) de travées sans plancher (ouverture)."""
    cols = {}
    def has(i,j): return 0<=i<len(xs)-1 and 0<=j<len(zs)-1 and (i,j) not in holes and (i,j) not in s._absent
    used = set()
    for i in range(len(xs)-1):
        for j in range(len(zs)-1):
            if (i,j) in s._absent: continue
            for (p,q) in ((i,j),(i+1,j),(i,j+1),(i+1,j+1)): used.add((p,q))
    for (i,j) in sorted(used): s.column(xs[i], zs[j], TOP)
    # lisses par arête de travée
    edges = set()
    for i in range(len(xs)-1):
        for j in range(len(zs)-1):
            if (i,j) in s._absent: continue
            edges |= {((i,j),(i+1,j)), ((i,j+1),(i+1,j+1)), ((i,j),(i,j+1)), ((i+1,j),(i+1,j+1))}
    for (p,q) in sorted(edges):
        a = (xs[p[0]], zs[p[1]]); b = (xs[q[0]], zs[q[1]])
        # bords extérieurs / bords de trémie
        horiz = p[1] == q[1]
        if horiz: sides = [(p[0],p[1]-1),(p[0],p[1])]
        else:     sides = [(p[0]-1,p[1]),(p[0],p[1])]
        floor_sides = [has(*t) for t in sides]
        outer = sum(1 for t in sides if 0<=t[0]<len(xs)-1 and 0<=t[1]<len(zs)-1 and t not in s._absent) == 1
        for y in (Y0, Y0+STEP):
            if not arm_cross(ax, az, a, b, y) and (outer or y == Y0+STEP):
                s.ledger(a, b, y, 'O'); break
        if any(floor_sides): s.ledger(a, b, YD, 'U')
        else: s.ledger(a, b, YD, 'O')
        if not all(floor_sides) and any(floor_sides):
            if access and (p,q) == access: s.gate(a, b)
            else: s.guard(a, b)
            t = sides[0] if floor_sides[0] else sides[1]
            inward = (0, 1 if t[1]==p[1] else -1) if horiz else (1 if t[0]==p[0] else -1, 0)
            s.toeboard(a, b, DECK, inward)
    # planchers (portée selon z entre lisses U)
    if decks:
        for i in range(len(xs)-1):
            for j in range(len(zs)-1):
                if has(i,j): s.decks(xs[i]+RT, xs[i+1]-RT, zs[j]+0.25, zs[j+1]-0.25, 'z')
    # diagonales sur les faces extérieures (une par face, du bas vers le plancher)
    for (p,q) in sorted(edges):
        horiz = p[1] == q[1]
        sides = [(p[0],p[1]-1),(p[0],p[1])] if horiz else [(p[0]-1,p[1]),(p[0],p[1])]
        n_in = sum(1 for t in sides if 0<=t[0]<len(xs)-1 and 0<=t[1]<len(zs)-1 and t not in s._absent)
        if n_in == 1:
            a = (xs[p[0]], zs[p[1]]); b = (xs[q[0]], zs[q[1]])
            L = math.dist(a, b)
            if L > 9:
                lo = Y0 if not arm_cross(ax, az, a, b, Y0) else Y0+STEP
                s.diagonal(a, b, lo, YD)

def make_center(kind, lod=False):
    c = CLEAR[kind]; a = c + 1.0
    if kind == 'Mast': F = 48; ax = 24          # 3 × 3 m, axe au centre de la cellule (1,1)
    else:              F = 80; ax = 40          # 5 × 5 m, axe au centre de la cellule (2,2)
    xs = [0, ax-a, ax+a, F]
    name = 'CSC Tower PassThrough Scaffold' + ('' if kind=='Mast' else ' Sleeve') + (' LOD' if lod else '')
    s = Scaf(name, lod); s._absent = set()
    bay_grid(xs, xs, {(1,1)}, ax, ax, name, s, access=((1,3),(2,3)))
    s.ladder(ax, F, 0, DECK, 'z+')
    s.clearance('Ouverture libre (entre rosaces)', ax-c, ax+c, ax-c, ax+c, 0, TOP, 'aucun élément dans ce prisme')
    s.clearance('Enveloppe mât + chaîne (relevée)', ax-ENV_MAST, ax+ENV_MAST, ax-ENV_MAST, ax+ENV_MAST, 16, 352, 'toutes orientations')
    if kind=='Sleeve': s.clearance('Enveloppe chariot tourné 45°', ax-ENV_SLEEVE*1.415, ax+ENV_SLEEVE*1.415, ax-ENV_SLEEVE*1.415, ax+ENV_SLEEVE*1.415, 16.74, 352, 'chariot tourné de 45°')
    s.m.null(G['att'], 'Tower_Axis', (ax, 0, ax))
    for nm,p in (('CSC_Connection_Front',(F/2,DECK,0)),('CSC_Connection_Back',(F/2,DECK,F)),('CSC_Connection_Left',(0,DECK,F/2)),('CSC_Connection_Right',(F,DECK,F/2))):
        s.m.null(G['att'], nm, p)
    s.m.doc.update({'type':kind,'emprise_m':[F/16,F/16],'plancher_m':DECK/16,'axe_tour':[ax,ax],'travee_centrale_m':round(2*a/16,3),
                    'ouverture_libre_u':round(2*c,2),'ouverture_libre_m':round(2*c/16,3),'travees_m':[round((xs[i+1]-xs[i])/16,3) for i in range(3)]})
    return s.m

def make_side(kind, lod=False):
    c = CLEAR[kind]; a = c + 1.0; ax, az = 24, -8          # tour dans la cellule devant la rive avant
    F = 48 if kind=='Mast' else 64; ax = F/2 if (F/2) % 16 == 8 else F/2 + 8
    xs = [0, ax-a, ax+a, F]; zs = [0, az + c + 1.0, 32]
    name = 'CSC Tower SidePass Scaffold' + ('' if kind=='Mast' else ' Sleeve') + (' LOD' if lod else '')
    s = Scaf(name, lod); s._absent = {(1,0)}
    bay_grid(xs, zs, set(), ax, az, name, s, access=((1,2),(2,2)))
    s.ladder(ax, 32, 0, DECK, 'z+')
    s.clearance('Ouverture libre', ax-c, ax+c, az-c, az+c, 0, TOP, 'tour dans la cellule devant la rive')
    s.m.null(G['att'], 'Tower_Axis', (ax, 0, az))
    s.m.doc.update({'type':kind,'emprise_m':[F/16,2],'plancher_m':DECK/16,'axe_tour':[ax,az],'encoche_u':[round(2*c,2), round(az+c,2)]})
    return s.m

def make_corner(kind, lod=False):
    c = CLEAR[kind]; a = c + 1.0; ax = az = 8           # tour dans la cellule d'angle
    F = 48 if kind=='Mast' else 64
    xs = [0, ax+a, F]; zs = [0, az+a, F]
    name = 'CSC Tower CornerPass Scaffold' + ('' if kind=='Mast' else ' Sleeve') + (' LOD' if lod else '')
    s = Scaf(name, lod); s._absent = {(0,0)}
    bay_grid(xs, zs, set(), ax, az, name, s, access=((1,2),(2,2)))
    s.ladder((xs[1]+xs[2])/2, F, 0, DECK, 'z+')
    s.clearance('Ouverture libre', ax-c, ax+c, az-c, az+c, 0, TOP, 'tour dans la cellule d’angle')
    s.m.null(G['att'], 'Tower_Axis', (ax, 0, az))
    s.m.doc.update({'type':kind,'emprise_m':[F/16,F/16],'plancher_m':DECK/16,'axe_tour':[ax,az],'encoche_u':[round(ax+c,2)]*2})
    return s.m

# ======================= composants unitaires =======================
def components():
    out = {}
    for L in (8,16,32,48):
        s = Scaf(f'CSC Scaffold Standard {L/16:g} m'); s._tube_v(f'Montant {L/16:g} m · tube Ø48,3', 0, 0, 0, L)
        k=0
        while 5.6+STEP*k < L: s.rosette(0,0,5.6+STEP*k); k+=1
        s.m.cube('Emboîtement du montant (spigot)','Connectors',(-0.45,L-0.25,-0.45),(0.45,L+0.05,0.45),'galva_d')
        s.m.null(G['att'],'CSC_Connection_Bottom',(0,0,0)); s.m.null(G['att'],'CSC_Connection_Top',(0,L,0))
        s.m.doc={'longueur_m':L/16,'rosaces_y':[5.6+STEP*i for i in range(k)]}
        out[f'Standards/CSC_Scaffold_Standard_{int(L/16*100):03d}']=s.m
    for L in (8,16,32,48):
        for kind in ('O','U'):
            s = Scaf(f'CSC Scaffold Ledger {kind} {L/16:g} m'); s.ledger((0,0),(L,0),0,kind)
            s.m.null(G['att'],'CSC_Connection_Left',(0,0,0)); s.m.null(G['att'],'CSC_Connection_Right',(L,0,0))
            s.m.doc={'entraxe_m':L/16,'origine':'axe du montant gauche, centre de rosace'}
            out[f'Ledgers/CSC_Scaffold_Ledger_{kind}_{int(L/16*100):03d}']=s.m
    for L in (16,32,48):
        s = Scaf(f'CSC Scaffold Brace {L/16:g} x 2 m'); s.diagonal((0,0),(L,0),0,32)
        s.m.null(G['att'],'CSC_Connection_Bottom',(0,0,0)); s.m.null(G['att'],'CSC_Connection_Top',(L,32,0))
        s.m.doc={'travee_m':L/16,'hauteur_m':2.0}
        out[f'Braces/CSC_Scaffold_Brace_{int(L/16*100):03d}x200']=s.m
    s = Scaf('CSC Scaffold Base Jack + Collar'); s.column(0,0,COLLAR[1],True)
    s.m.null(G['att'],'CSC_Connection_Top',(0,COLLAR[1],0)); s.m.doc={'rosace_y':Y0,'reglage':'vérin réglable, modélisé à 0,1 m'}
    out['Bases/CSC_Scaffold_BaseJack_Collar']=s.m
    for L in (16,32,48):
        s = Scaf(f'CSC Scaffold Guardrail {L/16:g} m'); s.guard((0,0),(L,0),0); s.toeboard((0,0),(L,0),0,(0,1))
        s.m.null(G['att'],'Deck_Level',(0,0,0)); s.m.doc={'lisses':'0,5 m et 1,0 m au-dessus du plancher','plinthe':'0,15 m'}
        out[f'Guardrails/CSC_Scaffold_Guardrail_{int(L/16*100):03d}']=s.m
    for L in (16,32,48):
        s = Scaf(f'CSC Scaffold Deck 0.32 x {L/16:g} m'); s.decks(0,5.12+0.6,0,L,'z',0.64)
        s.m.doc={'largeur_m':0.32,'longueur_m':L/16}; out[f'Decks/CSC_Scaffold_Deck_032x{int(L/16*100):03d}']=s.m
    s = Scaf('CSC Scaffold Ladder 2 m'); s.ladder(0,0,0,32,'z+'); s.m.doc={'hauteur_plancher_m':2.0}
    out['Access/CSC_Scaffold_Ladder_200']=s.m
    s = Scaf('CSC Scaffold Gate'); s.gate((0,0),(25.12,0),0); s.m.doc={'travee_m':1.57}
    out['Guardrails/CSC_Scaffold_Gate_157']=s.m
    return out

if __name__ == '__main__':
    for p,m in components().items(): m.save(f'{OUT}/Scaffolding/{p}.bbmodel')
    for kind in ('Mast','Sleeve'):
        sub = 'Mast_Openings' if kind=='Mast' else 'Sleeve_Openings'
        sfx = '' if kind=='Mast' else '_Sleeve'
        make_center(kind).save(f'{OUT}/Tower_PassThrough/{sub}/CSC_Tower_PassThrough_Scaffold{sfx}.bbmodel')
        make_side(kind).save(f'{OUT}/Tower_PassThrough/Side_Passages/CSC_Tower_SidePass_Scaffold{sfx}.bbmodel')
        make_corner(kind).save(f'{OUT}/Tower_PassThrough/Corner_Passages/CSC_Tower_CornerPass_Scaffold{sfx}.bbmodel')
        make_center(kind,True).save(f'{OUT}/LOD/CSC_Tower_PassThrough_Scaffold{sfx}_LOD.bbmodel')
        make_side(kind,True).save(f'{OUT}/LOD/CSC_Tower_SidePass_Scaffold{sfx}_LOD.bbmodel')
        make_corner(kind,True).save(f'{OUT}/LOD/CSC_Tower_CornerPass_Scaffold{sfx}_LOD.bbmodel')
    print('ok')
