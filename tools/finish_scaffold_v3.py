#!/usr/bin/env python3
"""Noms, infobulles, loot, recettes, tag pioche des équipements V3 + noms fictifs CSC des truss, tours et palan."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]; RES=ROOT/'common/src/main/resources'; AS=RES/'assets/concertstage'; DATA=RES/'data/concertstage'
def dump(p,d): p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(d,ensure_ascii=False,indent=2 if p.parent.name=='lang' else None,separators=None if p.parent.name=='lang' else (',',':')))
spec={e['id']:e for e in json.loads((DATA/'stage_equipment.json').read_text())['equipment'] if e['id'].startswith('csc_')}
FIN={'black':('noir antidérapant','black anti-slip'),'wood':('bois','wood'),'metal':('tôle alu larmée','aluminium tread plate')}
SIZE={'100':('1 × 1 m','1 × 1 m'),'200':('2 × 1 m','2 × 1 m'),'200_square':('2 × 2 m','2 × 2 m'),'100_half':('1 × 0,5 m','1 × 0.5 m'),'200_half':('2 × 0,5 m','2 × 0.5 m')}
def m(h): return f'{int(h)/100:.2f}'.replace('.',',')+' m', f'{int(h)/100:.2f} m'
NAMES={}
for k in spec:
    s=k[4:]
    if s.startswith('stagedeck_') and s.rsplit('_',1)[1] in FIN and not s.startswith(('stagedeck_tower','stagedeck_sleeve')):
        size,fin=s[len('stagedeck_'):].rsplit('_',1); fr,en=SIZE[size]; NAMES[k]=(f'Plateau CSC StageDeck {fr} — {FIN[fin][0]}',f'CSC StageDeck {en} — {FIN[fin][1]}')
    elif s.startswith('leg_'):
        parts=s.split('_'); kind=parts[1]; h=parts[2:]
        lab={'fixed':('fixe','fixed'),'adjustable':('réglable','adjustable'),'telescopic':('télescopique','telescopic'),'reinforced':('renforcé','reinforced')}[kind]
        hf=' – '.join(m(x)[0] for x in h); he=' – '.join(m(x)[1] for x in h)
        NAMES[k]=(f'Pied CSC {lab[0]} {hf}',f'CSC {lab[1]} leg {he}')
NAMES.update({
 'csc_tower_passthrough_scaffold':('Échafaudage CSC — passage de tour (mât)','CSC scaffold — tower pass-through (mast)'),
 'csc_tower_passthrough_scaffold_sleeve':('Échafaudage CSC — passage de tour (sleeve)','CSC scaffold — tower pass-through (sleeve)'),
 'csc_tower_sidepass_scaffold':('Échafaudage CSC — tour en rive (mât)','CSC scaffold — tower at the edge (mast)'),
 'csc_tower_sidepass_scaffold_sleeve':('Échafaudage CSC — tour en rive (sleeve)','CSC scaffold — tower at the edge (sleeve)'),
 'csc_tower_cornerpass_scaffold':('Échafaudage CSC — tour en angle (mât)','CSC scaffold — tower in the corner (mast)'),
 'csc_tower_cornerpass_scaffold_sleeve':('Échafaudage CSC — tour en angle (sleeve)','CSC scaffold — tower in the corner (sleeve)'),
 'csc_stagedeck_tower_opening':('Plateau CSC — ouverture de tour 3 × 3 m','CSC deck — tower opening 3 × 3 m'),
 'csc_stagedeck_tower_frame':('Plateau CSC — cadre d’ouverture de tour 3 × 3 m','CSC deck — tower opening frame 3 × 3 m'),
 'csc_stagedeck_sleeve_opening':('Plateau CSC — ouverture sleeve 5 × 5 m','CSC deck — sleeve opening 5 × 5 m'),
 'csc_stagedeck_tower_side':('Plateau CSC — encoche de tour en rive','CSC deck — tower notch (edge)'),
 'csc_stagedeck_tower_corner':('Plateau CSC — encoche de tour en angle','CSC deck — tower notch (corner)'),
 'csc_drumriser_2x2_h040':('Estrade batterie CSC 2 × 2 m (0,40 m)','CSC drum riser 2 × 2 m (0.40 m)'),
 'csc_dj_platform_2x2_h080':('Plateforme DJ CSC 2 × 2 m (0,80 m)','CSC DJ platform 2 × 2 m (0.80 m)'),
 'csc_keyboard_riser_2x1_h020':('Estrade clavier CSC 2 × 1 m (0,20 m)','CSC keyboard riser 2 × 1 m (0.20 m)'),
 'csc_technical_platform_2x2_h120':('Plateforme technique CSC 2 × 2 m (1,20 m)','CSC technical platform 2 × 2 m (1.20 m)'),

 **{f'csc_festival_stage_8x4_h220_tower_c{c}_r{r}':(f'Scène de festival préassemblée — 2,2 m, tour en colonne {c}, rangée {r}',f'Pre-assembled festival stage — 2.2 m, tower at column {c}, row {r}') for r in (2,3) for c in range(3,8)},
 'csc_festival_stage_8x4_h220_tower_b':('Scène de festival préassemblée — 2,2 m, case de tour B','Pre-assembled festival stage — 2.2 m, tower cell B'),
 'csc_festival_stage_8x4_h220_tower':('Scène de festival préassemblée — 2,2 m, case de tour','Pre-assembled festival stage — 2.2 m, tower cell'),
 'csc_foh_platform_4x2_h040':('Régie façade CSC 4 × 2 m (0,40 m)','CSC FOH platform 4 × 2 m (0.40 m)')})
USE={**{f'csc_festival_stage_8x4_h220_tower_c{c}_r{r}':(f'Case libre pour une tour truss motorisée : colonne {c} sur 8, rangée {r} sur 4 (mêmes repères que la version A = colonne 2, rangée 3 et B = colonne 2, rangée 2).',f'Free cell for a motorised truss tower: column {c} of 8, row {r} of 4 (same reference as version A = column 2, row 3 and B = column 2, row 2).') for r in (2,3) for c in range(3,8)},
     'csc_festival_stage_8x4_h220_tower_b':('Variante B : case libre décalée d’une case vers l’autre bord latéral (miroir de la version A), même rangée, pour une tour truss motorisée.','Variant B: free cell shifted one cell towards the other side edge (mirror of version A), same row, for a motorised truss tower.'),
     'csc_festival_':('Une case libre dans la scène (2e case depuis le bord latéral, 3e rangée depuis l’avant) pour une tour truss motorisée.','One free cell inside the stage (2nd cell from the side edge, 3rd row from the front) for a motorised truss tower.'),
     'csc_tower_':('Ouverture libre pour le mât d’une tour motorisée : le chariot passe ; pas de poutres accrochées au chariot sous le plancher.',
                    'Free opening for a motorised tower mast: the carriage passes; no beams on the carriage below the floor.'),
     'csc_stagedeck_tower':('Autour d’une tour : surface ≥ 0,60 m (pieds hors des bras de stabilisateurs).','Around a tower: surface ≥ 0.60 m (legs clear of the outriggers).'),
     'csc_stagedeck_sleeve':('Autour d’une tour : surface ≥ 0,60 m ; laisse passer le chariot.','Around a tower: surface ≥ 0.60 m; lets the carriage through.'),
     'csc_stagedeck_':('Plateau sans pieds, à poser sur des pieds CSC ou un support.','Deck without legs, set on CSC legs or a support.'),
     'csc_leg_':('Pied séparé ; géométrie fixe.','Separate leg; fixed geometry.'),
     'csc_':('Assemblage prêt à poser ; casser une partie récupère l’équipement.','Ready-made assembly; breaking a part returns the item.')}
CAT={'scaffold':('Échafaudages et passages de tours','Scaffolding & tower passes')}
# ---- noms fictifs CSC des truss / tours / palan ----
TRUSS={'truss_h1_l1':('CSC BoxTruss 100 — 1 m','CSC BoxTruss 100 — 1 m'),'truss_h1_l2':('CSC BoxTruss 100 — 2 m','CSC BoxTruss 100 — 2 m'),
 'truss_h2_l2':('CSC BoxTruss 100 — panneau 2 × 2 m','CSC BoxTruss 100 — 2 × 2 m panel'),'truss_h2_l1':('CSC BoxTruss 100 — panneau 1 × 2 m (complément)','CSC BoxTruss 100 — 1 × 2 m panel (filler)'),'truss_v2_h2':('CSC BoxTruss 100 — colonne 2 m','CSC BoxTruss 100 — 2 m column'),
 'truss_corner_3':('CSC BoxTruss 100 — angle 3 voies','CSC BoxTruss 100 — 3-way corner'),'truss_corner_3_h2':('CSC BoxTruss 100 — angle 3 voies, 2 m de haut','CSC BoxTruss 100 — 3-way corner, 2 m high'),
 'truss_corner_6':('CSC BoxTruss 100 — nœud 6 voies','CSC BoxTruss 100 — 6-way node'),'truss_corner_6_h2':('CSC BoxTruss 100 — nœud 6 voies, 2 m de haut','CSC BoxTruss 100 — 6-way node, 2 m high'),
 'truss_base_v2':('CSC BoxTruss 100 — embase','CSC BoxTruss 100 — base plate'),'truss_junction_90':('CSC BoxTruss 100 — jonction 90°','CSC BoxTruss 100 — 90° junction'),
 'truss_junction_t':('CSC BoxTruss 100 — jonction en T','CSC BoxTruss 100 — T junction'),'truss_junction_cross':('CSC BoxTruss 100 — jonction en croix','CSC BoxTruss 100 — cross junction'),
 'truss_h1_l2_petit':('CSC BoxTruss 60 — 2 m','CSC BoxTruss 60 — 2 m'),'truss_x30d_l2':('CSC TriTruss 30 — 2 m','CSC TriTruss 30 — 2 m'),
 'truss_h2_l2_x30d':('CSC BoxTruss 100 — panneau 2 × 2 m avec TriTruss 30','CSC BoxTruss 100 — 2 × 2 m panel with TriTruss 30'),
 'truss_ladder_l1':('CSC LadderTruss 50 — 1 m','CSC LadderTruss 50 — 1 m'),'truss_ladder_l2':('CSC LadderTruss 50 — 2 m','CSC LadderTruss 50 — 2 m'),
 'truss_ladder_v2':('CSC LadderTruss 50 — vertical 2 m','CSC LadderTruss 50 — vertical 2 m'),'palan_chaine':('CSC ChainHoist 500 (D8+)','CSC ChainHoist 500 (D8+)')}
JOINT={'corner3':('chariot 3 voies','3-way carriage'),'corner3_h2':('chariot 3 voies, 2 m','3-way carriage, 2 m'),'corner6':('chariot 6 voies','6-way carriage'),'corner6_h2':('chariot 6 voies, 2 m','6-way carriage, 2 m')}
for h in (10,14,18,22):
    for j,(fr,en) in JOINT.items(): TRUSS[f'motor_tower_{h}_{j}']=(f'CSC LiftTower {h} m — {fr}',f'CSC LiftTower {h} m — {en}')
TABS={'trusses':('Truss','Trusses'),'rigging':('Tours et levage','Towers & rigging'),'stage':('Plateformes et scènes','Platforms & stages'),
      'scaffolding':('Échafaudages CSC V3','CSC V3 scaffolding'),'curtains':('Rideaux et bâches','Curtains & tarps'),'regie':('Régie (FOH)','FOH control'),'tools':('Outils','Tools')}
for i,lang in enumerate(['fr_fr','en_us']):
    p=AS/'lang'/(lang+'.json'); d=json.loads(p.read_text())
    for k,v in NAMES.items():
        d['block.concertstage.'+k]=v[i]; e=spec[k]; g=e['grid']
        d['tooltip.concertstage.dimensions.'+k]=(f'Emprise {g[0]} × {g[2]} blocs · hauteur {g[1]} bloc(s)' if i==0 else f'Footprint {g[0]} × {g[2]} blocks · {g[1]} block(s) high')+(f' · plancher {e["walk"]:.2f} m'.replace('.',',') if i==0 and e['walk']>.2 else (f' · floor {e["walk"]:.2f} m' if e['walk']>.2 else ''))
        d['tooltip.concertstage.use.'+k]=next(v2[i] for pre,v2 in USE.items() if k.startswith(pre))
    for k,v in CAT.items(): d['tooltip.concertstage.category.'+k]=v[i]
    for k,(fr,en) in TRUSS.items():
        if 'block.concertstage.'+k in d:
            d['block.concertstage.'+k]=(fr,en)[i]
            if 'block.concertstage.'+k+'_noir' in d: d['block.concertstage.'+k+'_noir']=(fr,en)[i]+(' (noir)' if i==0 else ' (black)')
    for k,v in TABS.items(): d['itemGroup.concertstage.'+k]=v[i]
    dump(p,d)
# loot, recettes, pioche
def ing(*items): return [{'item':x} for x in items]
def rec(k):
    s=k[4:]
    TOWERDECK={'stagedeck_tower_opening':['minecraft:iron_ingot']*4+['minecraft:black_carpet']*2,'stagedeck_tower_frame':['minecraft:iron_ingot']*4+['minecraft:iron_bars'],
               'stagedeck_tower_side':['minecraft:iron_ingot']*3+['minecraft:black_carpet']*2,'stagedeck_tower_corner':['minecraft:iron_ingot']*2+['minecraft:black_carpet']*2,
               'stagedeck_sleeve_opening':['minecraft:iron_ingot']*6+['minecraft:black_carpet']*2}
    if s in TOWERDECK: return ing(*TOWERDECK[s])
    if s.startswith('stagedeck_'):
        fin={'black':'minecraft:black_carpet','wood':'minecraft:birch_planks','metal':'minecraft:iron_ingot'}[s.rsplit('_',1)[1]]
        n={'100_half':1,'100':2,'200_half':3,'200':4,'200_square':6}[s[len('stagedeck_'):].rsplit('_',1)[0]]; return ing(*['minecraft:iron_ingot']*n,fin)
    if s.startswith('leg_'):
        extra={'fixed':'minecraft:stick','adjustable':'minecraft:iron_nugget','telescopic':'minecraft:chain','reinforced':'minecraft:iron_bars'}[s.split('_')[1]]
        fam=sorted(k2 for k2 in spec if k2.startswith('csc_leg_'+s.split('_')[1]+'_')); idx=fam.index(k)+1
        return ing('minecraft:iron_ingot',extra,*['minecraft:iron_nugget']*idx)
    if s.startswith('tower_'):
        extra={'passthrough':'minecraft:iron_bars','sidepass':'minecraft:ladder','cornerpass':'minecraft:iron_trapdoor'}[s.split('_')[1]]
        return ing(*['minecraft:scaffolding']*3,*['minecraft:iron_ingot']*(4 if 'sleeve' in s else 3),extra,*(['minecraft:chain'] if 'sleeve' in s else []))
    if s.startswith('festival_stage_8x4_h220_tower_c'):
        order=['festival_stage_8x4_h220_tower_b']+[f'festival_stage_8x4_h220_tower_c{c}_r{r}' for r in (2,3) for c in range(3,8)]
        return ing('concertstage:csc_'+order[order.index(s)-1])
    if s=='festival_stage_8x4_h220_tower_b': return ing('concertstage:assembly_festival_stage_8x4_h220','minecraft:iron_bars','minecraft:iron_bars')
    if s.startswith('festival_'): return ing('concertstage:assembly_festival_stage_8x4_h220','minecraft:iron_bars')
    if s.startswith('drumriser'): return ing('concertstage:csc_stagedeck_200_black','concertstage:csc_stagedeck_200_black',*['concertstage:csc_leg_adjustable_040']*4)
    if s.startswith('dj_'): return ing('concertstage:csc_stagedeck_200_black','concertstage:csc_stagedeck_200_black',*['concertstage:csc_leg_adjustable_080']*4,'minecraft:iron_bars')
    if s.startswith('keyboard'): return ing('concertstage:csc_stagedeck_200_black',*['concertstage:csc_leg_fixed_020']*2)
    if s.startswith('technical'): return ing('concertstage:csc_stagedeck_200_black','concertstage:csc_stagedeck_200_black',*['concertstage:csc_leg_telescopic_120_200']*4,'minecraft:iron_bars')
    return ing(*['concertstage:csc_stagedeck_200_black']*4,*['concertstage:csc_leg_adjustable_040']*4,'minecraft:iron_bars')
for k in spec:
    dump(DATA/'loot_tables/blocks'/(k+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'concertstage:'+k}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    r=rec(k); assert len(r)<=9,k
    if k.startswith(('csc_tower_','csc_stagedeck_tower','csc_stagedeck_sleeve')): (DATA/'recipes'/(k+'.json')).unlink(missing_ok=True); continue   # retirés (1.9.2)
    dump(DATA/'recipes'/(k+'.json'),{'type':'minecraft:crafting_shapeless','category':'building','group':'concertstage_csc_v3','ingredients':r,'result':{'item':'concertstage:'+k}})
p=RES/'data/minecraft/tags/blocks/mineable/pickaxe.json'; d=json.loads(p.read_text()); d['values']=list(dict.fromkeys(d['values']+['concertstage:'+k for k in spec])); dump(p,d)
print('noms, loot, recettes :',len(spec),'équipements V3 ;',len(TRUSS),'noms fictifs')
