import json, copy, os, sys, math
sys.path.insert(0,'/home/claude/v3/tools')
from lib3 import *
SRC='/home/claude/v160/concert-stage/tools/platform_source/Models'
OUT='/home/claude/v3/out/CSC_StagePlatforms_Scaffolding_V3'
DECK_T=1.44          # épaisseur deck 90 mm (identique au mod)
TEMPLATE=json.load(open(f'{SRC}/stage_deck_2x1_without_legs.bbmodel'))

def _shift(e, axis, d, stretch=False):
    if stretch: e['to'][axis]=round(e['to'][axis]+d,4)
    else:
        e['from'][axis]=round(e['from'][axis]+d,4); e['to'][axis]=round(e['to'][axis]+d,4)
        if 'origin' in e: e['origin'][axis]=round(e['origin'][axis]+d,4)
    if stretch and 'origin' in e: e['origin'][axis]=round(e['origin'][axis]+d/2,4)

FINISH={'Black':('Panneau multiplis 18 mm · film antidérapant noir',None),
        'Wood':('Panneau multiplis bouleau 18 mm · finition bois','wood'),
        'Metal':('Tôle aluminium larmée 4 mm sur multiplis','tread')}

def make_deck(code, W, D, finish='Black'):
    """W, D en unités (16 = 1 m). Construction reprise du deck 2×1 du mod, redimensionnée."""
    d=copy.deepcopy(TEMPLATE); els=d['elements']
    keep=[]; trav=[]; acc_long=[]; acc_end=[]
    for e in els:
        n=e['name']
        if n.startswith('Traverse porteuse'): trav.append(e); continue
        if n.startswith('Accès au connecteur de rive'): acc_long.append(e); continue
        if n.startswith('Logement du verrou de bout'): acc_end.append(e); continue
        keep.append(e)
    dx,dz=W-32,D-16
    for e in keep:
        f,t=e['from'],e['to']
        if f[0]<=0.3 and t[0]>=31.7: _shift(e,0,dx,True)
        elif f[0]>=16: _shift(e,0,dx)
        if f[2]<=0.9 and t[2]>=15.1: _shift(e,2,dz,True)
        elif f[2]>=8: _shift(e,2,dz)
    new=[]
    # traverses porteuses tous les 0,5 m
    proto=[e for e in trav if 7<e['from'][0]<9]
    for k in range(1,int(W//8)):
        for p in proto:
            e=copy.deepcopy(p); e['uuid']=U(); _shift(e,0,8*k-8); _shift(e,2,dz,True); new.append(e)
    # raidisseur longitudinal central pour les grands decks
    if D>=32:
        for p in proto:
            e=copy.deepcopy(p); e['uuid']=U()
            f,t=e['from'],e['to']
            e['from']=[0.88, f[1], f[0]-8+D/2]; e['to']=[W-0.88, t[1], t[0]-8+D/2]
            e['origin']=[W/2,(f[1]+t[1])/2,D/2]; e['name']='Raidisseur longitudinal 40 × 20'
            fu=copy.deepcopy(e['faces']); e['faces']=fu; new.append(e)
    for p in [a for a in acc_long if a['from'][0]<9]:
        for k in range(int(W//16)):
            e=copy.deepcopy(p); e['uuid']=U(); _shift(e,0,16*k)
            if p['from'][2]>8: _shift(e,2,dz)
            new.append(e)
    for p in acc_end:
        zs=[8+16*k for k in range(int(D//16))] if D>=16 else [D/2]
        for z in zs:
            e=copy.deepcopy(p); e['uuid']=U(); _shift(e,2,z-8)
            if p['from'][0]>16: _shift(e,0,dx)
            new.append(e)
    allel=keep+new
    # coins renforcés (moulages d'angle) et finition
    m=Model(f'CSC StageDeck {code} {finish}', atlas=False)
    m.textures=copy.deepcopy(d['textures']); m.textures.append(tex_entry(os.path.join(TEX_DIR,'csc_v3_atlas.png')))
    AT=len(m.textures)-1
    for e in allel:
        n=e['name']
        if n.startswith('Panneau multiplis'):
            e['name']=FINISH[finish][0]
            if FINISH[finish][1]:
                e['faces']['up']={'uv':Model._uv_rect(FINISH[finish][1]),'texture':AT}
        if n.startswith('Panneau'): grp='Platforms'
        elif n.startswith(('Récepteur','Patte','Axe','Levier')): grp='Leg Supports'
        elif n.startswith(('Accès','Logement','Boulon')): grp='Connectors'
        else: grp='Main Frame'
        e['uuid']=U(); m.elements.append(e); m._ensure(grp)['_children'].append(e['uuid'])
    for x in (0, W-0.88):
        for z in (0, D-0.88):
            m.cube('Coin renforcé · moulage aluminium','Main Frame',(x,0.064,z),(x+0.88,1.088,z+0.88),'alu_d',tex=AT)
    for x in (1.36, W-1.36):
        for z in (1.36, D-1.36):
            m.null('Attachment Points',f'Leg_Socket x{x/16:.2f} z{z/16:.2f}',(x,-0.16,z))
    m.null('Attachment Points','CSC_Connection_Front',(W/2,DECK_T/2,0)); m.null('Attachment Points','CSC_Connection_Back',(W/2,DECK_T/2,D))
    m.null('Attachment Points','CSC_Connection_Left',(0,DECK_T/2,D/2)); m.null('Attachment Points','CSC_Connection_Right',(W,DECK_T/2,D/2))
    m.doc={'dimensions_m':[W/16,0.09,D/16],'origine':'coin avant-gauche, dessous du cadre (y=0) ; surface à y=1.44',
           'base':'construction du stage_deck_2x1 du mod, redimensionnée','finition':finish}
    return m

DECKS={'100':(16,16),'200':(32,16),'200_Square':(32,32),'100_Half':(16,8),'200_Half':(32,8)}

# ---------------- pieds ----------------
def hollow(m,name,grp,cx,cz,half,t,y0,y1,tile,tex=0):
    """Tube carré creux (4 parois) centré sur cx,cz."""
    m.cube(name+' · paroi',grp,(cx-half,y0,cz-half),(cx-half+t,y1,cz+half),tile,tex=tex)
    m.cube(name+' · paroi',grp,(cx+half-t,y0,cz-half),(cx+half,y1,cz+half),tile,tex=tex)
    m.cube(name+' · retour',grp,(cx-half+t,y0,cz-half),(cx+half-t,y1,cz-half+t),tile,tex=tex)
    m.cube(name+' · retour',grp,(cx-half+t,y0,cz+half-t),(cx+half-t,y1,cz+half),tile,tex=tex)
def leg_top(h_cm): return h_cm*16/100 - 0.544     # sommet du pied dans le récepteur du deck

def make_leg(kind, h_cm, h_max=None):
    top=leg_top(h_cm); nm={'Fixed':'fixe','Adjustable':'réglable','Telescopic':'télescopique','Reinforced':'renforcé'}[kind]
    m=Model(f'CSC Leg {kind} {h_cm}' + (f'-{h_max}' if h_max else ''))
    G='Vertical Standards'
    if kind=='Fixed':
        m.cube('Patin antimarque','Connectors',(-0.8,0,-0.8),(0.8,0.112,0.8),'rubber')
        m.cube('Embout de pied aluminium','Connectors',(-0.48,0.112,-0.48),(0.48,0.24,0.48),'alu_d')
        hollow(m,'Pied aluminium 48 × 48',G,0,0,0.384,0.048,0.24,top,'alu')
    elif kind=='Adjustable':
        m.cube('Patin antimarque','Connectors',(-0.8,0,-0.8),(0.8,0.112,0.8),'rubber')
        m.cube('Platine de répartition acier','Connectors',(-0.768,0.112,-0.768),(0.768,0.224,0.768),'galva')
        m.tube('Vis de nivellement M16','Connectors',(0,0.224,0),(0,1.6,0),0.13,8,'zinc')
        m.cube('Écrou papillon de réglage','Connectors',(-0.42,0.9,-0.12),(0.42,1.04,0.12),'zinc')
        m.cube('Insert fileté du pied','Connectors',(-0.4,1.2,-0.4),(0.4,1.264,0.4),'steel')
        hollow(m,'Pied aluminium 48 × 48',G,0,0,0.384,0.048,1.232,top,'alu')
    elif kind=='Telescopic':
        mid=0.32*16+0.4
        m.cube('Platine de base','Connectors',(-0.9,0,-0.9),(0.9,0.16,0.9),'galva')
        hollow(m,'Fût extérieur 60 × 60',G,0,0,0.48,0.064,0.16,mid,'alu_d')
        m.cube('Collier de serrage','Connectors',(-0.58,mid-0.5,-0.58),(0.58,mid,0.58),'steel')
        m.cube('Poignée de serrage','Connectors',(0.58,mid-0.34,-0.08),(1.2,mid-0.18,0.08),'black')
        m.tube('Broche de sécurité','Connectors',(-0.75,mid+0.6,0),(0.75,mid+0.6,0),0.07,8,'zinc')
        hollow(m,'Fût intérieur 48 × 48',G,0,0,0.384,0.048,mid-1.0,top,'alu')
        for y in [mid+0.6+0.8*k for k in range(int((top-mid-1)/0.8))]:
            m.cube('Perçage de réglage','Connectors',(-0.392,y-0.07,-0.07),(-0.38,y+0.07,0.07),'dark')
    elif kind=='Reinforced':
        m.cube('Platine de pied 200 × 200','Connectors',(-1.6,0,-1.6),(1.6,0.192,1.6),'galva')
        for sx,sz,ry in ((1,0,0),(-1,0,0),(0,1,90),(0,-1,90)):
            m.cube('Gousset de pied','Connectors',(sx*0.48 if sx>0 else -1.4,0.192,-0.032) if sz==0 else (-0.032,0.192,sz*0.48 if sz>0 else -1.4),
                   (1.4 if sx>0 else -0.48,1.2,0.032) if sz==0 else (0.032,1.2,1.4 if sz>0 else -0.48),'galva')
        hollow(m,'Fût renforcé 60 × 60 × 4',G,0,0,0.48,0.064,0.192,top,'galva')
        for y in (top*0.35, top*0.7):
            m.cube('Collier de contreventement','Connectors',(-0.62,y-0.25,-0.62),(0.62,y+0.25,0.62),'zinc')
            m.cube('Oreille de collier','Connectors',(0.62,y-0.2,-0.05),(0.95,y+0.2,0.05),'zinc')
    m.cube('Tête de pied · bague de centrage','Connectors',(-0.42,top-0.12,-0.42),(0.42,top,0.42),'black')
    m.null('Attachment Points','Deck_Receiver_Top',(0,top,0)); m.null('Attachment Points','Ground_Contact',(0,0,0))
    m.doc={'hauteur_surface_deck_m':h_cm/100,'plage_m':[h_cm/100,(h_max or h_cm)/100],'origine':'au sol, axe du pied','sommet':round(top,3)}
    return m

# ---------------- connecteurs ----------------
def make_connectors():
    res={}
    m=Model('CSC Deck Clamp')
    m.cube('Mâchoire fixe','Connectors',(-0.6,0.0,-0.35),(0.6,1.44,-0.05),'alu_d')
    m.cube('Mâchoire mobile','Connectors',(-0.6,0.0,0.05),(0.6,1.44,0.35),'alu_d')
    m.cube('Pont de mâchoire','Connectors',(-0.6,-0.25,-0.35),(0.6,0.0,0.35),'alu')
    m.tube('Vis de serrage','Connectors',(0,-0.25,-0.5),(0,-0.25,0.5),0.08,8,'zinc')
    m.cube('Poignée en T','Connectors',(-0.45,-0.45,0.5),(0.45,-0.05,0.62),'black')
    m.null('Attachment Points','Deck_Edge_A',(0,0.72,-0.05)); m.null('Attachment Points','Deck_Edge_B',(0,0.72,0.05))
    m.doc={'usage':'serre deux profils de rive de decks adjacents (bas de cadre à y=0)'}; res['CSC_Deck_Clamp']=m
    m=Model('CSC Leg Clamp 48')
    m.tube('Collier demi-coquille A','Connectors',(0,-0.3,0),(0,0.3,0),0.52,12,'galva')
    m.cube('Oreille de serrage','Connectors',(0.45,-0.25,-0.12),(0.9,0.25,0.12),'galva')
    m.tube('Boulon M12','Connectors',(0.68,0,-0.3),(0.68,0,0.3),0.08,8,'zinc')
    m.cube('Platine de liaison de traverse','Connectors',(-0.9,-0.25,-0.05),(-0.45,0.25,0.05),'galva')
    m.null('Attachment Points','Brace_Pivot',(-0.68,0,0)); m.doc={'usage':'collier sur pied 48 mm, reçoit une traverse de renfort'}
    res['CSC_Leg_Clamp_48']=m
    for L in (16,32):
        h=9.6; m=Model(f'CSC Cross Brace {L//16}m')
        ang=math.degrees(math.atan2(h,L)); ln=math.hypot(L,h)
        for s,(a,b) in enumerate((((0,0.6,0),(L,0.6+h,0)),((0,0.6+h,0),(L,0.6,0)))):
            m.tube(f'Barre de contreventement {s+1}','Diagonal Braces',(a[0],a[1],0.1*(1-2*s)),(b[0],b[1],0.1*(1-2*s)),0.18,6,'galva')
        m.tube('Boulon de croisement','Connectors',(L/2,0.6+h/2,-0.35),(L/2,0.6+h/2,0.35),0.08,8,'zinc')
        for x in (0,L):
            for y in (0.6,0.6+h): m.null('Attachment Points',f'Clamp_Point x{x/16:.0f} y{y/16:.2f}',(x,y,0))
        m.doc={'entraxe_pieds_m':L/16,'hauteur_contreventement_m':round(h/16,2)}; res[f'CSC_Cross_Brace_{L//16}m']=m
    return res

if __name__=='__main__':
    build_atlas(os.path.join(TEX_DIR,'csc_v3_atlas.png'))
    for code,(W,D) in DECKS.items():
        for fin in FINISH:
            make_deck(code,W,D,fin).save(f'{OUT}/StageDecks/CSC_StageDeck_{code}_{fin}.bbmodel')
    LEGS=[('Fixed',h,None) for h in (20,40,60,80,100,120)]+[('Adjustable',h,None) for h in (40,60,80,100,120)] \
        +[('Telescopic',80,120),('Telescopic',120,200)]+[('Reinforced',h,None) for h in (120,200,300)]
    for k,h,hm in LEGS:
        make_leg(k,h,hm).save(f'{OUT}/Platform_Legs/CSC_Leg_{k}_{h:03d}'+(f'_{hm:03d}' if hm else '')+'.bbmodel')
    for n,m in make_connectors().items(): m.save(f'{OUT}/Platform_Connectors/{n}.bbmodel')
    print('ok')
