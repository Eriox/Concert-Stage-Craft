import sys, json, time, copy, os, numpy as np
sys.path.insert(0,'/home/claude/v3/tools')
from tests3 import *
from lib3 import element_polys
R='/home/claude/v3/out/CSC_StagePlatforms_Scaffolding_V3'
TW={'LiftTower 10M (corner3)':TOWERS+'/motor_tower_10_corner3.bbmodel','LiftTower 22M (corner6 h2)':TOWERS+'/motor_tower_22_corner6_h2.bbmodel',
    'LiftTower 14M (corner6)':TOWERS+'/motor_tower_14_corner6.bbmodel','LiftTower 18M (corner3 h2)':TOWERS+'/motor_tower_18_corner3_h2.bbmodel'}
def axis_of(path):
    d=json.load(open(path))
    def w(o):
        for g in o:
            if isinstance(g,dict):
                if g['name']=='Tower_Axis': return g['origin']
                r=w(g['children'])
                if r: return r
    return w(d['outliner'])
YMAX=72.0   # au-dessus de tout élément d'échafaudage / plateforme
def tower_sets():
    out={}
    for name,p in TW.items():
        d=json.load(open(p)); H=max(max(q[:,1].max() for q in element_polys(e)) for e in d['elements'] if element_polys(e))
        st=load_solids(p, lambda e,g: not is_carriage(e,g), None, YMAX)
        car=load_solids(p, lambda e,g: is_carriage(e,g))
        cy=min(s.P[:,1].min() for s in car); dy=min(H-16-max(s.P[:,1].max() for s in car), YMAX-cy)
        out[name]=(st, swept(car, dy), round(float(H-16-max(s.P[:,1].max() for s in car)),2))
    return out
def scaffold_solids(path, y=0, legs=None):
    S=load_solids(path)
    if y: S=[s.moved(np.array([0,y,0])) for s in S]
    if legs:
        leg=load_solids(legs)
        d=json.load(open(path)); pts=[]
        def w(o):
            for g in o:
                if isinstance(g,dict):
                    if g['name'].startswith('Leg_Socket'): pts.append(g['origin'])
                    w(g['children'])
        w(d['outliner'])
        for p in pts: S+= [s.moved(np.array([p[0],0,p[2]])) for s in leg]
    return S
CASES=[]
for sub,f in [('Mast_Openings','CSC_Tower_PassThrough_Scaffold'),('Sleeve_Openings','CSC_Tower_PassThrough_Scaffold_Sleeve'),
              ('Side_Passages','CSC_Tower_SidePass_Scaffold'),('Side_Passages','CSC_Tower_SidePass_Scaffold_Sleeve'),
              ('Corner_Passages','CSC_Tower_CornerPass_Scaffold'),('Corner_Passages','CSC_Tower_CornerPass_Scaffold_Sleeve')]:
    CASES.append((f, f'{R}/Tower_PassThrough/{sub}/{f}.bbmodel', 0, None))
    CASES.append((f+'_LOD', f'{R}/LOD/{f}_LOD.bbmodel', 0, None))
for sub,f in [('Mast_Openings','CSC_StageDeck_Tower_Opening'),('Mast_Openings','CSC_StageDeck_Tower_Frame'),('Sleeve_Openings','CSC_StageDeck_Sleeve_Opening'),
              ('Side_Passages','CSC_StageDeck_Tower_Side'),('Corner_Passages','CSC_StageDeck_Tower_Corner')]:
    for h in (80,120):
        CASES.append((f+f' @ {h/100:.1f} m (+ pieds)', f'{R}/Tower_PassThrough/{sub}/{f}.bbmodel', h*16/100-1.44, f'{R}/Platform_Legs/CSC_Leg_Adjustable_{h:03d}.bbmodel'))
    CASES.append((f+'_LOD @ 0.8 m', f'{R}/LOD/{f}_LOD.bbmodel', 12.8-1.44, f'{R}/Platform_Legs/CSC_Leg_Adjustable_080.bbmodel'))
if __name__=='__main__':
    t=time.time(); TS=tower_sets(); print('tours prêtes',round(time.time()-t,1),flush=True)
    results=[]
    for name,path,y,legs in CASES:
        ax=axis_of(path); S=scaffold_solids(path,y,legs)
        S0=[s.moved(np.array([8-ax[0],0,8-ax[2]])) for s in S]
        row={'modele':name,'axe':ax,'tests':[]}
        for k in range(4):
          Sl=[s.rotated((4-k)%4) for s in S0]      # tourner l'échafaudage de -k·90° = tourner la tour de +k·90°
          for tn,(st,car,dy) in TS.items():
            h1=collide(Sl,st,3); h2=collide(Sl,car,3)
            row['tests'].append({'tour':tn,'rotation':k*90,'mat_et_pied':len(h1)==0,'chariot_balaye':len(h2)==0,'course_chariot_u':dy,
                                 'details':[list(map(str,h)) for h in (h1+h2)[:3]]})
        ok=all(x['mat_et_pied'] and x['chariot_balaye'] for x in row['tests'])
        row['ok']=ok; results.append(row); print(('OK ' if ok else 'ÉCHEC ')+name, round(time.time()-t,1), flush=True)
        if not ok:
            for x in row['tests']:
                if x['details']: print('   ',x['tour'],x['rotation'],x['details'][:2])
    # contrôle négatif : tour décalée de 6 u dans l'ouverture → doit échouer
    path=CASES[0][1]; ax=axis_of(path); S=scaffold_solids(path)
    neg=collide([s.moved(np.array([8-ax[0]-6,0,8-ax[2]])) for s in S], TS['LiftTower 10M (corner3)'][0], 1)
    results.append({'modele':'CONTRÔLE NÉGATIF (tour décalée de 6 u)','detecte':len(neg)>0,'details':[list(map(str,h)) for h in neg]})
    print('contrôle négatif détecté :', len(neg)>0, neg[:1])
    os.makedirs(R+'/Documentation',exist_ok=True)
    json.dump(results, open(R+'/Documentation/validation_passages.json','w'), ensure_ascii=False, indent=1)
