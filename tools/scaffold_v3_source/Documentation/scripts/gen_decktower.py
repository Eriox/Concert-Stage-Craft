"""Plateformes du système deck avec ouverture/encoche pour tour (CSC_StageDeck_Tower_*)."""
import sys, os; sys.path.insert(0,'/home/claude/v3/tools')
from lib3 import *
OUT='/home/claude/v3/out/CSC_StagePlatforms_Scaffolding_V3'
T=1.44
def profile(m, a, b, inward, lod=False):
    """Profil de rive 90 mm le long d'un segment axis-aligned a→b (x,z), ailes vers 'inward' (nx,nz)."""
    (x0,z0),(x1,z1)=a,b; nx,nz=inward
    if abs(z0-z1)<1e-6:   # selon X
        xa,xb=min(x0,x1),max(x0,x1); z=z0
        web=((xa,0,z-0.064 if nz<0 else z),(xb,T,z if nz<0 else z+0.064))
        fl=lambda y0,y1,w:((xa,y0,z-w if nz<0 else z),(xb,y1,z if nz<0 else z+w))
    else:
        za,zb=min(z0,z1),max(z0,z1); x=x0
        web=((x-0.064 if nx<0 else x,0,za),(x if nx<0 else x+0.064,T,zb))
        fl=lambda y0,y1,w:((x-w if nx<0 else x,y0,za),(x if nx<0 else x+w,y1,zb))
    m.cube('Profil de rive 90 mm · âme','Main Frame',*web,'alu_d')
    if not lod:
        m.cube('Profil de rive · aile basse','Main Frame',*fl(0,0.064,0.88),'alu_d')
        m.cube('Profil de rive · aile haute','Main Frame',*fl(1.088,1.152,0.88),'alu_d')
        m.cube('Lèvre affleurante','Main Frame',*fl(1.392,T,0.288),'alu')
def panel(m, x0,z0,x1,z1, lod=False):
    m.cube(f'Panneau multiplis 18 mm · film antidérapant {(x1-x0)/16:.2f} × {(z1-z0)/16:.2f} m','Platforms',(x0+0.288,1.152,z0+0.288),(x1-0.288,T,z1-0.288),'film')
    if lod: return
    # traverses porteuses 40 × 20 tous les 0,5 m selon le grand côté
    if x1-x0 >= z1-z0:
        x=x0+8
        while x < x1-1:
            m.cube('Traverse porteuse 40 × 20','Main Frame',(x-0.32,0.512,z0+0.88),(x+0.32,1.152,z1-0.88),'alu'); x+=8
    else:
        z=z0+8
        while z < z1-1:
            m.cube('Traverse porteuse 40 × 20','Main Frame',(x0+0.88,0.512,z-0.32),(x1-0.88,1.152,z+0.32),'alu'); z+=8
def socket(m, x, z, lod=False):
    m.cube('Récepteur de pied 60 × 60','Leg Supports',(x-0.48,-0.16,z-0.48),(x+0.48,0.896,z+0.48),'alu_d')
    if not lod: m.cube('Levier de serrage captif','Leg Supports',(x+0.48,-0.224,z-0.1),(x+0.67,0.064,z+0.9),'black')
    m.null('Attachment Points',f'Leg_Socket x{x/16:.2f} z{z/16:.2f}',(x,-0.16,z))
def rect_profiles(m, x0,z0,x1,z1, lod=False):
    profile(m,(x0,z0),(x1,z0),(0,1),lod); profile(m,(x0,z1),(x1,z1),(0,-1),lod)
    profile(m,(x0,z0),(x0,z1),(1,0),lod); profile(m,(x1,z0),(x1,z1),(-1,0),lod)
def clearance(m, name, x0,x1,z0,z1, note):
    for (x,z) in ((x0,z0),(x1,z0),(x1,z1),(x0,z1)): m.null('Tower Clearance/'+name,f'Coin x{x:.2f} z{z:.2f}',(x,0,z))
    m.doc.setdefault('clearances',[]).append({'nom':name,'x':[x0,x1],'z':[z0,z1],'note':note})
def deck_kit(name, panels, sockets, opening, axis, note, lod=False, frame_only=False):
    m=Model(name+(' LOD' if lod else ''))
    for (x0,z0,x1,z1) in panels:
        rect_profiles(m,x0,z0,x1,z1,lod)
        if not frame_only: panel(m,x0,z0,x1,z1,lod)
    for (x,z) in sockets: socket(m,x,z,lod)
    ox0,ox1,oz0,oz1=opening; clearance(m,'Ouverture libre',ox0,ox1,oz0,oz1,note)
    m.null('Attachment Points','Tower_Axis',(axis[0],0,axis[1]))
    m.doc.update({'ouverture_u':[ox0,ox1,oz0,oz1],'axe_tour':axis,'origine':'coin avant-gauche, dessous du cadre (y=0) ; surface à y=1.44',
                  'hauteur_min_surface_m':0.8,'note':note})
    return m
def pinwheel(F, o0, o1):
    """4 panneaux en moulinet autour d'une ouverture carrée [o0,o1]²."""
    return [(0,0,o1,o0),(o1,0,F,o1),(o0,o1,F,F),(0,o0,o0,F)]
def sockets_ring(F, o0, o1, ax):
    pts=[(1.36,1.36),(F-1.36,1.36),(1.36,F-1.36),(F-1.36,F-1.36)]
    # pieds intermédiaires hors des bras de stabilisateurs (lignes x=ax, z=ax)
    for t in (o0-1.36, o1+1.36):
        if abs(t-ax) > 2.5:
            pts += [(t,1.36),(t,F-1.36),(1.36,t),(F-1.36,t)]
    return pts
def build(lod=False):
    res={}
    # ouverture mât : 3 × 3 m, ouverture 1,5 × 1,5 m (24 u), axe (24,24)
    F,o0,o1,ax=48,12,36,24
    res['Mast_Openings/CSC_StageDeck_Tower_Opening']=deck_kit('CSC StageDeck Tower Opening',pinwheel(F,o0,o1),sockets_ring(F,o0,o1,ax),(o0,o1,o0,o1),(ax,ax),'mât + chaîne (enveloppe 9,78 u) : marge 2,22 u',lod)
    res['Mast_Openings/CSC_StageDeck_Tower_Frame']=deck_kit('CSC StageDeck Tower Frame',pinwheel(F,o0,o1),sockets_ring(F,o0,o1,ax),(o0,o1,o0,o1),(ax,ax),'cadre seul, sans panneaux',lod,True)
    # ouverture sleeve : 5 × 5 m, ouverture 2 × 2 m (32 u), axe (40,40)
    F,o0,o1,ax=80,24,56,40
    res['Sleeve_Openings/CSC_StageDeck_Sleeve_Opening']=deck_kit('CSC StageDeck Sleeve Opening',pinwheel(F,o0,o1),sockets_ring(F,o0,o1,ax),(o0,o1,o0,o1),(ax,ax),'chariot à toute orientation (enveloppe 45° : 13,84 u) : marge 2,16 u',lod)
    # rive : 3 × 1,5 m, tour dans la cellule devant la rive (axe 24,-8), encoche 1,5 × 0,25 m
    res['Side_Passages/CSC_StageDeck_Tower_Side']=deck_kit('CSC StageDeck Tower Side',[(0,0,12,24),(12,4,36,24),(36,0,48,24)],
        [(1.36,1.36),(10.64,1.36),(37.36,1.36),(46.64,1.36),(1.36,22.64),(46.64,22.64),(13.36,22.64),(34.64,22.64)],(12,36,-20,4),(24,-8),'encoche de 4 u de profondeur ; tour dans la cellule voisine',lod)
    # angle : 2 × 2 m, tour dans la cellule diagonale (axe -8,-8), encoche 0,25 × 0,25 m
    res['Corner_Passages/CSC_StageDeck_Tower_Corner']=deck_kit('CSC StageDeck Tower Corner',[(4,0,32,4),(0,4,32,32)],
        [(5.36,1.36),(30.64,1.36),(1.36,5.36),(1.36,30.64),(30.64,30.64)],(-20,4,-20,4),(-8,-8),'encoche d’angle 4 × 4 u ; tour dans la cellule diagonale',lod)
    return res
if __name__=='__main__':
    for p,m in build().items(): m.save(f'{OUT}/Tower_PassThrough/{p}.bbmodel')
    for p,m in build(True).items(): m.save(f'{OUT}/LOD/{os.path.basename(p)}_LOD.bbmodel')
    print('ok')
