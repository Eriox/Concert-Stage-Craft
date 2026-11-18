import sys, json; sys.path.insert(0,'/home/claude/v3/tools')
from run_tests import *
TS=tower_sets()
D=R+'/Preassembled_Structures'
DEMOS={'CSC_Demo_Tower_Surround_Scaffold':[('LiftTower 18M (corner3 h2)',24,24)],
       'CSC_Demo_Sleeve_Through_Opening':[('LiftTower 22M (corner6 h2)',40,40)],
       'CSC_Demo_Tower_Through_Deck':[('LiftTower 10M (corner3)',24,24)],
       'CSC_Demo_SidePass':[('LiftTower 14M (corner6)',24,-8)],
       'CSC_Demo_Four_Towers_Stage':[('LiftTower 10M (corner3)',24,24),('LiftTower 10M (corner3)',184,24),('LiftTower 18M (corner3 h2)',24,168),('LiftTower 14M (corner6)',184,152)]}
out=[]
for n,towers in DEMOS.items():
    S=load_solids(f'{D}/{n}.bbmodel', lambda e,g: not g.startswith('Tower'))
    row={'demo':n,'tests':[]}
    for tn,ax,az in towers:
        st,car,dy=TS[tn]; t=np.array([ax-8,0,az-8])
        h1=collide(S,[s.moved(t) for s in st],3); h2=collide(S,[s.moved(t) for s in car],3)
        row['tests'].append({'tour':tn,'axe':[ax,az],'mat_et_pied':not h1,'chariot_balaye':not h2,'details':[list(map(str,h)) for h in h1+h2]})
        print(n,tn,(ax,az),'OK' if not(h1 or h2) else 'ÉCHEC',(h1+h2)[:2],flush=True)
    out.append(row)
json.dump(out,open(R+'/Documentation/validation_demos.json','w'),ensure_ascii=False,indent=1)
