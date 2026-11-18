import sys, os, json, math, numpy as np
sys.path.insert(0,'/home/claude/v3/tools')
from lib3 import *
R='/home/claude/v3/out/CSC_StagePlatforms_Scaffolding_V3'
MOD='/home/claude/v160/concert-stage/tools/platform_source/Models'
TW='/home/claude/v160/concert-stage/tools/blockbench/realistic/towers'
def nulls(path, prefix):
    d=json.load(open(path)); out=[]
    def w(o):
        for g in o:
            if isinstance(g,dict):
                if g['name'].startswith(prefix): out.append(g['origin'])
                w(g['children'])
    w(d['outliner']); return out
def zmax(path):
    d=json.load(open(path)); return max(e['to'][2] for e in d['elements'] if 'to' in e)
def deck(m, f, x, z, H, legs=None, grp='Platforms'):
    p=f'{R}/StageDecks/{f}.bbmodel'; m.import_bb(p, f'{grp}/{f} @ x{x/16:g} z{z/16:g}', (x, H-1.44, z))
    if legs:
        for s in nulls(p,'Leg_Socket'):
            m.import_bb(f'{R}/Platform_Legs/{legs}.bbmodel', f'Vertical Standards/{legs}', (x+s[0], 0, z+s[2]))
def rail(m, f, x, z, H, edge):
    k={'front':0,'back':2,'left':1,'right':3}[edge]; p=f'{MOD}/{f}.bbmodel'; L=32 if f.endswith('2m') else 16
    off={'front':(x,H,z),'back':(x+L,H,z),'left':(x,H,z+L),'right':(x,H,z)}[edge]
    m.import_bb(p, f'Guardrails/{f} ({edge})', off, k)
def stairs(m, h, x, z_front):
    p=f'{MOD}/stage_stairs_h{h:03d}.bbmodel'; m.import_bb(p, f'Access/stage_stairs_h{h:03d} (existant)', (x, 0, z_front-zmax(p)))
def clamp(m, x, z, H, along='x'):
    m.import_bb(f'{R}/Platform_Connectors/CSC_Deck_Clamp.bbmodel','Connectors/CSC_Deck_Clamp',(x,H-1.44,z), 0 if along=='x' else 1)
def tower(m, f, ax, az, carriage_dy=0, grp='Tower (démo séparable)'):
    m.import_bb(f'{TW}/{f}.bbmodel', f'{grp}/{f}', (ax-8,0,az-8), group_shift={'Chariot mobile':(0,carriage_dy,0)} if carriage_dy else None)

def specialized():
    res={}
    m=Model('CSC Drum Riser 2x2 H040',atlas=False)
    for z in (0,16): deck(m,'CSC_StageDeck_200_Black',0,z,6.4,'CSC_Leg_Adjustable_040')
    for x in (8,24): clamp(m,x,16,6.4)
    m.doc={'dimensions_m':[2,0.4,2],'composants':'2 × StageDeck 200, 8 pieds réglables 040, 2 serre-decks'}; res['CSC_DrumRiser_2x2_H040']=m
    m=Model('CSC DJ Platform 2x2 H080',atlas=False)
    for z in (0,16): deck(m,'CSC_StageDeck_200_Black',0,z,12.8,'CSC_Leg_Adjustable_080')
    rail(m,'safety_railings_2m',0,32,12.8,'back'); rail(m,'safety_railings_2m',0,0,12.8,'left'); rail(m,'safety_railings_2m',32,0,12.8,'right')
    stairs(m,80,8,0); m.doc={'dimensions_m':[2,0.8,2],'acces':'escalier h080 existant en façade'}; res['CSC_DJ_Platform_2x2_H080']=m
    m=Model('CSC Keyboard Riser 2x1 H020',atlas=False)
    deck(m,'CSC_StageDeck_200_Black',0,0,3.2,'CSC_Leg_Fixed_020'); m.doc={'dimensions_m':[2,0.2,1]}; res['CSC_Keyboard_Riser_2x1_H020']=m
    m=Model('CSC Technical Platform 2x2 H120')
    for z in (0,16): deck(m,'CSC_StageDeck_200_Black',0,z,19.2,'CSC_Leg_Telescopic_120_200')
    for z in (1.36,30.64):
        for (a,b) in (((1.36,2.0),(30.64,13.0)),((1.36,13.0),(30.64,2.0))):
            m.tube('Contreventement en X','Diagonal Braces',(a[0],a[1],z),(b[0],b[1],z),0.18,6,'galva')
    rail(m,'safety_railings_2m',0,32,19.2,'back'); rail(m,'safety_railings_2m',0,0,19.2,'left'); rail(m,'safety_railings_2m',32,0,19.2,'right')
    stairs(m,120,8,0); m.doc={'dimensions_m':[2,1.2,2],'pieds':'télescopiques 1,2–2,0 m réglés à 1,2 m'}; res['CSC_Technical_Platform_2x2_H120']=m
    m=Model('CSC FOH Platform 4x2 H040',atlas=False)
    for x in (0,32):
        for z in (0,16): deck(m,'CSC_StageDeck_200_Black',x,z,6.4,'CSC_Leg_Adjustable_040')
    for x in (0,32): rail(m,'safety_railings_2m',x,32,6.4,'back')
    rail(m,'safety_railings_2m',0,0,6.4,'left'); rail(m,'safety_railings_2m',64,0,6.4,'right'); rail(m,'safety_railings_2m',32,0,6.4,'front')
    stairs(m,40,8,0); m.doc={'dimensions_m':[4,0.4,2],'usage':'régie façade (FOH)'}; res['CSC_FOH_Platform_4x2_H040']=m
    return res

def demos():
    res={}
    P=f'{R}/Tower_PassThrough'
    m=Model('CSC Demo Tower Surround Scaffold',atlas=False)
    m.import_bb(f'{P}/Mast_Openings/CSC_Tower_PassThrough_Scaffold.bbmodel','Scaffold (séparable)')
    tower(m,'motor_tower_18_corner3',24,24,carriage_dy=64)
    m.doc={'demo':'tour 18 blocs dans le passage mât ; chariot au-dessus du plancher'}; res['CSC_Demo_Tower_Surround_Scaffold']=m
    m=Model('CSC Demo Sleeve Through Opening',atlas=False)
    m.import_bb(f'{P}/Sleeve_Openings/CSC_Tower_PassThrough_Scaffold_Sleeve.bbmodel','Scaffold (séparable)')
    tower(m,'motor_tower_10_corner6_h2',40,40,carriage_dy=8)
    m.doc={'demo':'chariot h2 à cheval sur le niveau plancher (y 24,7→55,3)'}; res['CSC_Demo_Sleeve_Through_Opening']=m
    m=Model('CSC Demo Tower Through Deck',atlas=False)
    m.import_bb(f'{P}/Mast_Openings/CSC_StageDeck_Tower_Opening.bbmodel','Platforms/CSC_StageDeck_Tower_Opening',(0,19.2-1.44,0))
    for s in nulls(f'{P}/Mast_Openings/CSC_StageDeck_Tower_Opening.bbmodel','Leg_Socket'):
        m.import_bb(f'{R}/Platform_Legs/CSC_Leg_Adjustable_120.bbmodel','Vertical Standards/CSC_Leg_Adjustable_120',(s[0],0,s[2]))
    tower(m,'motor_tower_10_corner3',24,24,carriage_dy=4)
    m.doc={'demo':'plateforme deck 3 × 3 m à 1,2 m autour d’une tour 10 blocs ; chariot traversant le plateau'}; res['CSC_Demo_Tower_Through_Deck']=m
    m=Model('CSC Demo SidePass',atlas=False)
    m.import_bb(f'{P}/Side_Passages/CSC_Tower_SidePass_Scaffold.bbmodel','Scaffold (séparable)')
    tower(m,'motor_tower_14_corner6',24,-8,carriage_dy=10)
    m.doc={'demo':'tour 14 blocs dans la cellule devant la rive'}; res['CSC_Demo_SidePass']=m
    return res

def festival():
    """Scène de démonstration : 4 tours, scène deck 1,2 m, 2 plateformes techniques à 2 m (LOD)."""
    m=Model('CSC Demo Four Towers Stage',atlas=False); H=19.2
    # tours : avant (24,24) et (184,24) ; arrière (24,168) et (184,152)
    T=[('motor_tower_10_corner3',24,24,40),('motor_tower_10_corner3',184,24,40),('motor_tower_14_corner6_h2',24,168,24),('motor_tower_14_corner6',184,152,80)]
    for i,(f,ax,az,dy) in enumerate(T): tower(m,f,ax,az,dy,f'Towers (séparables)/Tour {i+1}')
    # plateformes deck à ouverture autour des tours avant (1,2 m)
    for ax in (24,184):
        p=f'{R}/Tower_PassThrough/Mast_Openings/CSC_StageDeck_Tower_Opening.bbmodel'
        m.import_bb(p,f'Platforms/Deck tour x{ax}',(ax-24,H-1.44,0))
        for s in nulls(p,'Leg_Socket'): m.import_bb(f'{R}/Platform_Legs/CSC_Leg_Adjustable_120.bbmodel','Vertical Standards/Pieds deck tour',(ax-24+s[0],0,s[2]))
    # scène centrale : x 48→160 (7 m), z 0→112 (7 m)
    for row in range(7):
        z=row*16
        for x in (48,80,112): deck(m,'CSC_StageDeck_200_Black',x,z,H,'CSC_Leg_Adjustable_120','Platforms/Scène')
        deck(m,'CSC_StageDeck_100_Black',144,z,H,'CSC_Leg_Adjustable_120','Platforms/Scène')
    # retours de scène derrière les decks de tour avant (z 48→112)
    for x0 in (0,160):
        for row in range(3,7):
            for x in (x0, x0+32 if x0==0 else x0+32):
                if x+32<=x0+48 or x==x0: deck(m,'CSC_StageDeck_200_Black',x0,row*16,H,'CSC_Leg_Adjustable_120','Platforms/Retours')
                break
            deck(m,'CSC_StageDeck_100_Black',x0+32,row*16,H,'CSC_Leg_Adjustable_120','Platforms/Retours')
    # plateformes techniques à 2 m autour des tours arrière (LOD) : sleeve à gauche, mât à droite
    m.import_bb(f'{R}/LOD/CSC_Tower_PassThrough_Scaffold_Sleeve_LOD.bbmodel','Scaffolding/Plateforme technique gauche (sleeve, LOD)',(24-40,0,168-40))
    m.import_bb(f'{R}/LOD/CSC_Tower_PassThrough_Scaffold_LOD.bbmodel','Scaffolding/Plateforme technique droite (mât, LOD)',(184-24,0,152-24))
    # garde-corps de rive arrière de scène et accès
    for x in (48,80,112): rail(m,'safety_railings_2m',x,112,H,'back')
    rail(m,'safety_railings_1m',144,112,H,'back')
    stairs(m,120,96,0)
    m.doc={'demo':'4 tours (2 × 10 blocs, 2 × 14 blocs), scène 1,2 m, 2 plateformes techniques à 2 m','avertissement':'démonstration visuelle Minecraft, pas un plan de construction'}
    return m

if __name__=='__main__':
    for n,m in specialized().items(): m.save(f'{R}/Preassembled_Structures/{n}.bbmodel'); print(n,len(m.elements))
    for n,m in demos().items(): m.save(f'{R}/Preassembled_Structures/{n}.bbmodel'); print(n,len(m.elements))
    m=festival(); m.save(f'{R}/Preassembled_Structures/CSC_Demo_Four_Towers_Stage.bbmodel'); print('festival',len(m.elements),len(m.textures))
