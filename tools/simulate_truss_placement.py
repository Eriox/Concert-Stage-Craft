#!/usr/bin/env python3
"""Simulation of MultiPartBlock.getStateForPlacement for trusses (chained placement). Mirrors the Java logic;
run it after changing the placement code: python3 tools/simulate_truss_placement.py"""
N,E,S,W,U,D = 'N','E','S','W','U','D'
STEP = {N:(0,0,-1), E:(1,0,0), S:(0,0,1), W:(-1,0,0), U:(0,1,0), D:(0,-1,0)}
CW = {N:E, E:S, S:W, W:N}; CCW = {v:k for k,v in CW.items()}; OPP = {N:S,S:N,E:W,W:E,U:D,D:U}
TURNS = {N:0,E:1,S:2,W:3}
def horiz(f): return f in (N,E,S,W)

class Piece:
    def __init__(s,name,sx,sy,sz,placement): s.name=name; s.sx,s.sy,s.sz=sx,sy,sz; s.placement=placement; s.mx=s.my=s.mz=0
    def count(s): return s.sx*s.sy*s.sz
    def idx(s,x,y,z): return x+s.sx*(z+s.sz*y)
    def anchor(s): return s.idx(0,0,0)
    def offset(s,i,k):
        lx,ly,lz=i%s.sx,i//(s.sx*s.sz),(i//s.sx)%s.sz
        dx,dy,dz=lx-s.mx,ly-s.my,lz-s.mz
        for _ in range(k): dx,dz=-dz,dx
        return (dx,dy,dz)
    def localY(s,part): return part//(s.sx*s.sz)

world={}   # pos -> (piece, facing, part)
def add(a,b): return (a[0]+b[0],a[1]+b[1],a[2]+b[2])
def sub(a,b): return (a[0]-b[0],a[1]-b[1],a[2]-b[2])

def choose_facing(p,face,look,sneak=False):
    side = horiz(face)
    if p.placement=='EXTEND': return CCW[face] if side else CCW[look]
    if p.placement=='CORNER3':
        if not side: return look
        t=OPP[face]; f1=CCW[t]; f2=OPP[t]; o1=CW[t]; o2=CCW[t]
        d=lambda o: STEP[look][0]*STEP[o][0]+STEP[look][2]*STEP[o][2]
        first = d(o1)>=d(o2)
        if sneak: first=not first
        return f1 if first else f2
    return look

def place(p, clicked, face, look, sneak=False):
    """clicked = bloc visé ; renvoie la liste des cellules posées, ou None si impossible"""
    pl = add(clicked, STEP[face])                    # position de pose
    if pl in world: return None                      # BlockItem: canPlace()
    cs = world.get(clicked)
    on_truss = isinstance(cs, tuple)
    facing = choose_facing(p,face,look,sneak); k=TURNS[facing]
    aoff = p.offset(p.anchor(),k)
    mains=[]
    if face==D: mains.append((pl[0]-aoff[0], pl[1]-(p.sy-1)+p.my, pl[2]-aoff[2]))
    elif on_truss and horiz(face):
        base = clicked[1]-cs[0].localY(cs[2]); mains.append((pl[0]-aoff[0], base+p.my, pl[2]-aoff[2]))
    mains.append(sub(pl,aoff))
    for m in mains:
        at=[i for i in range(p.count()) if add(m,p.offset(i,k))==pl]
        if not at: continue
        a=at[0]
        if all((i==a) or (add(m,p.offset(i,k)) not in world) for i in range(p.count())):
            cells={add(m,p.offset(i,k)):(p,facing,i) for i in range(p.count())}
            world.update(cells); return cells, facing
    return None

H1=Piece('beam1x2',2,1,1,'EXTEND'); H2=Piece('panel2x2',2,2,1,'EXTEND'); V2=Piece('column',1,2,1,'LOOK')
C3=Piece('corner3',1,1,1,'CORNER3'); C3H2=Piece('corner3h2',1,2,1,'CORNER3'); C6=Piece('corner6',1,1,1,'LOOK')
def show(title,res,expect=None):
    if res is None: print('  X',title,'-> IMPOSSIBLE'); return False
    cells,f=res; xs=sorted(cells)
    ok = True if expect is None else expect(cells,f)
    print('  %s %-62s cells=%s facing=%s'%('OK' if ok else '??',title,xs,f)); return ok

allok=True
print('1) Poutres 1x2 enchaînées (je clique le bout de la précédente en regardant dans une AUTRE direction)')
world.clear(); world[(0,-1,0)]='ground'
r=place(H1,(0,-1,0),U,E); allok&=show('poseur 1: pose sur le sol, regard est',r)   # (0,0,0),(1,0,0)
r=place(H1,(1,0,0),E,N);  allok&=show('poseur 2: clic bout EST (regard NORD !)',r, lambda c,f: sorted(c)==[(2,0,0),(3,0,0)])
r=place(H1,(3,0,0),E,S);  allok&=show('poseur 3: clic bout EST (regard SUD !)',r, lambda c,f: sorted(c)==[(4,0,0),(5,0,0)])
r=place(H1,(0,0,0),W,E);  allok&=show('poseur 4: clic bout OUEST, regard EST (vers la poutre)',r, lambda c,f: sorted(c)==[(-2,0,0),(-1,0,0)])

print('2) Panneaux 2x2 enchaînés, clic sur la moitié HAUTE')
world.clear(); world[(0,-1,0)]='ground'
r=place(H2,(0,-1,0),U,E); allok&=show('panneau 1 sur le sol',r)
r=place(H2,(1,1,0),E,N);  allok&=show('panneau 2: clic bout EST, cellule HAUTE (regard nord)',r, lambda c,f: sorted(c)==[(2,0,0),(2,1,0),(3,0,0),(3,1,0)])
r=place(H2,(3,0,0),E,W);  allok&=show('panneau 3: clic bout EST, cellule BASSE (regard ouest)',r, lambda c,f: sorted(c)==[(4,0,0),(4,1,0),(5,0,0),(5,1,0)])

print('3) Panneau 2x2 SUSPENDU sous une poutre (clic sur la face du dessous)')
world.clear(); world[(0,-5,0)]='ground'
for x in (0,1): world[(x,10,0)]=(H1,'N',x)
r=place(H2,(0,10,0),D,E); allok&=show('clic dessous de la poutre, y=10',r, lambda c,f: all(p[1] in (8,9) for p in c))

print('4) Joint 3 voies posé au bout d\'une poutre')
world.clear(); world[(0,-1,0)]='ground'
r=place(H1,(0,-1,0),U,E)
r=place(C3,(1,0,0),E,S);  allok&=show('coin 3 voies, clic bout EST, regard SUD',r)
cells,f=r; (pos,(p,ff,i)),=cells.items()
flanges={CW[ff],OPP[ff]}
print('     brides horizontales du coin (+x,+z) =',flanges,'-> une doit être OUEST (vers la poutre):',W in flanges,'| l\'autre vers le SUD (regard):',S in flanges)
allok &= (W in flanges and S in flanges)

print('5) Poutre posée à la suite d\'un joint (clic sur la bride libre)')
other=[d for d in flanges if d!=W][0]
r=place(H1,pos,other,N);  allok&=show(f'poutre sur la bride {other} (regard NORD)',r)
cells,f=r; xs=sorted(cells); d=STEP[other]
print('     la poutre part bien tout droit hors de la bride:', all((c[0]-pos[0])*d[0]+(c[2]-pos[2])*d[2]>0 for c in xs))

print('6) Colonne 2 de haut, posée sur un joint puis suspendue dessous')
world.clear(); world[(0,-1,0)]='ground'
r=place(C6,(0,-1,0),U,N); 
r=place(V2,(0,0,0),U,N);  allok&=show('colonne sur le joint (clic dessus)',r, lambda c,f: sorted(c)==[(0,1,0),(0,2,0)])
world.clear(); world[(0,-9,0)]='ground'; world[(0,10,0)]=(C6,'N',0)
r=place(V2,(0,10,0),D,N); allok&=show('colonne suspendue sous le joint (clic dessous)',r, lambda c,f: sorted(c)==[(0,8,0),(0,9,0)])

print('\nRESULTAT:', 'TOUS LES CAS PASSENT' if allok else 'ECHEC')
