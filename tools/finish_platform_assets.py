#!/usr/bin/env python3
"""Deterministic platform recipes, compatibility loot, bilingual descriptions and inventory sizing."""
import json,math,re
from pathlib import Path
import integrate_platforms as p
R=p.ROOT;A=p.AS;D=p.DATA

I='minecraft:iron_ingot';N='minecraft:iron_nugget';B='minecraft:iron_bars';C='minecraft:black_carpet';P='minecraft:dark_oak_planks'
M=lambda name:'concertstage:'+name
BOARD=M('stage_deck_2x1_without_legs');D40=M('stage_deck_2x1_h040');D80=M('stage_deck_2x1_h080');L40=M('adjustable_leg_h040');L80=M('adjustable_leg_h080');L120=M('adjustable_leg_h120');S40=M('stage_stairs_h040')
RECIPES={
 'adjustable_leg_h040':[I]*2+[N,B], 'adjustable_leg_h080':[I]*3+[N,B], 'adjustable_leg_h120':[I]*4+[N,B],
 'safety_railings_1m':[B]*2+[I,N], 'safety_railings_2m':[B]*4+[I,I,N], 'safety_railings_corner_1x1':[B]*3+[I,I,N],
 'stage_support_structures_2x1_h111':[B]*4+[I]*3+[N],
 'stage_deck_2x1_without_legs':[C,C,P,P,I,I,N], 'stage_deck_2x1_h020':[C,C,P,P,I,I,N,B],
 'stage_deck_2x1_h040':[BOARD]+[L40]*4, 'stage_deck_2x1_h080':[BOARD]+[L80]*4, 'raised_stage_platform_2x1_h120':[BOARD]+[L120]*4+[B],
 'drum_riser_4x2_h060':[D40]*4+[B,B,L80], 'musician_riser_2x1_h040':[D40,B,B,N,N],
 'dj_platform_2x2_h080':[D80,D80,C,C,P,P,B], 'stage_stairs_h040':[B,B,C,I,'minecraft:black_concrete'],
 'stage_stairs_h080':[S40,S40,B], 'stage_stairs_h120':[S40,S40,S40,B],
 'stage_extension_1x1_h040':[BOARD,I,C,L40,L40], 'stage_extension_2x0p5_h040':[BOARD,I,C,L40],
 'assembly_solo_stage_4x2_h040':[D40]*4,
 'assembly_festival_stage_8x4_h120':['minecraft:iron_block']*8+[M('raised_stage_platform_2x1_h120')]
}

def finish():
 specs=json.loads((D/'stage_equipment.json').read_text())['equipment']
 for s in specs:
  key=s['id'];old=s['kind']=='legacy';item='stage_extension_1x1_h040'if old else key
  functions=[]
  if old:functions=[{'function':'minecraft:set_count','count':8 if s['bay']else 9}]
  elif s['mode']=='joined':functions=[{'function':'minecraft:set_count','count':2,'conditions':[{'condition':'minecraft:block_state_property','block':'concertstage:'+key,'properties':{'joined':'true'}}]}]
  entry={'type':'minecraft:item','name':'concertstage:'+item}
  if functions:entry['functions']=functions
  p.dump(D/'loot_tables/blocks'/(key+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
  recipe=D/'recipes'/(key+'.json')
  if old:recipe.unlink(missing_ok=True);continue
  ingredients=RECIPES[key]
  p.dump(recipe,{'type':'minecraft:crafting_shapeless','category':'building','group':'concertstage_platforms','ingredients':[{'item':item}for item in ingredients],'result':{'item':'concertstage:'+key}})
 for name in ['stage_riser','stage_stairs']:(D/'recipes'/(name+'.json')).unlink(missing_ok=True)
 kinds={'platform':('Stage decks','Plateaux scéniques'),'riser':('Artist platforms','Estrades pour artistes'),'assembly':('Complete stages','Scènes complètes'),'extension':('Stage extensions','Extensions scéniques'),'stairs':('Stairs','Escaliers'),'railing':('Safety railings','Garde-corps'),'frame':('Support frames','Cadres porteurs'),'support':('Removable supports','Pieds amovibles')}
 for locale,lang_index in [('en_us',0),('fr_fr',1)]:
  dest=A/'lang'/(locale+'.json');lang=json.loads(dest.read_text())
  for k in list(lang):
   if k.startswith('block.concertstage.scaffold_stage_'):lang[k]='Compatibility stage — professional replacement'if lang_index==0 else 'Scène de compatibilité — remplacement professionnel'
  for kind,labels in kinds.items():lang['tooltip.concertstage.category.'+kind]=labels[lang_index]
  lang['tooltip.concertstage.mountable']=['Right-click an edge with a railing. Sneak + empty hand: remove attachment.','Clic droit sur un bord avec un garde-corps. Accroupi + main vide : retirer la fixation.'][lang_index]
  for key,labels in zip(p.NAMES,p.LABELS):
   s=next(s for s in specs if s['id']==key);lang['block.concertstage.'+key]=labels[lang_index]
   if s['kind']=='support':dim=[f'Nominal {int(key[-3:]):g} cm · actual component height {s["raw_bounds"][4]-s["raw_bounds"][1]:.3f} m; fixed geometry.',f'Nominal {int(key[-3:]):g} cm · hauteur réelle {s["raw_bounds"][4]-s["raw_bounds"][1]:.3f} m ; géométrie fixe.'][lang_index].replace('040 cm','40 cm').replace('080 cm','80 cm').replace('120 cm','120 cm')
   elif s['kind']=='railing':dim=('Corner 1 × 1 m'if'corner'in key else f'{s["width"]:g} m')+(' · guard height 1.10 m'if lang_index==0 else ' · hauteur utile 1,10 m')
   elif s['kind']=='frame':dim=['2 × 1 m · frame 1.11 m · with deck 1.20 m','2 × 1 m · cadre 1,11 m · avec plateau 1,20 m'][lang_index]
   else:dim=f'{s["width"]:g} × {s["depth"]:g} m · '+(['walking height','hauteur praticable'][lang_index])+f' {s["walk"]:g} m'
   if s['kind']=='stairs':dim=f'1 × {s["walk"]/.2*.3+.04:.2f} m · '+(['walking height','hauteur praticable'][lang_index])+f' {s["walk"]:g} m'
   if key.endswith('without_legs'):dim=['2 × 1 m · panel thickness 90 mm (excluding receivers)','2 × 1 m · épaisseur du panneau 90 mm (hors récepteurs)'][lang_index]
   if key.startswith('dj_'):dim+=[' · DJ worktop 1.798 m',' · plan DJ 1,798 m'][lang_index]
   use=['Aligned multiblock; breaking one part recovers one equipment.','Multibloc aligné ; casser une partie récupère un équipement.'][lang_index]
   if s['kind']=='support':use=['Separate component; telescopic appearance, no height adjustment interaction.','Composant séparé ; aspect télescopique, sans réglage interactif.'][lang_index]
   if s['kind']=='railing':use=['Right-click a deck edge to fasten at its exact height. Sneak: free placement.','Clic droit sur un bord de plateau : fixation à sa hauteur exacte. Accroupi : pose libre.'][lang_index]
   if key.endswith('without_legs'):use=['Right-click the support frame to fasten; sneak for standalone placement.','Clic droit sur le cadre porteur pour fixer ; accroupi pour poser seul.'][lang_index]
   if s['kind']=='frame':use=['Fasten the deck without legs for a 1.20 m walking surface.','Fixer le plateau sans pieds pour obtenir une surface à 1,20 m.'][lang_index]
   if s['mode']=='joined':use=['Right-click with a second half to fill the 2 × 1 m cell.','Clic droit avec une seconde moitié pour remplir la cellule de 2 × 1 m.'][lang_index]
   lang['tooltip.concertstage.dimensions.'+key]=dim;lang['tooltip.concertstage.use.'+key]=use
  messages={'not_mountable':('This equipment cannot receive a railing.','Cet équipement ne reçoit pas de garde-corps.'),'frame_needs_deck':('Fasten a deck without legs first.','Fixer d’abord un plateau sans pieds.'),'railing_too_long':('The railing is too long for this edge.','Le garde-corps est trop long pour ce bord.'),'already_mounted':('A fitting already occupies this position.','Une fixation occupe déjà cet emplacement.'),'space_blocked':('Not enough free space, or an entity is in the way.','Espace insuffisant ou entité présente.'),'remove_attachments_first':('Remove attachments before joining the second half.','Retirer les fixations avant de joindre la seconde moitié.')}
  for k,v in messages.items():lang['message.concertstage.'+k]=v[lang_index]
  for k,v in {'tooltip.concertstage.existing.truss': ('Truss & rigging accessories', 'Truss et accessoires de structure'), 'tooltip.concertstage.existing.tower': ('Motorised towers · link with the lift remote', 'Tours motorisées · liaison avec la télécommande'), 'tooltip.concertstage.existing.hoist': ('Chain hoist · compatible with the small and X30D trusses', 'Palan à chaîne · compatible truss petites et X30D'), 'tooltip.concertstage.existing.curtain': ('Curtains & accessories', 'Rideaux et accessoires'), 'tooltip.concertstage.existing.tools': ('Tools & controls', 'Outils et commandes'), 'tooltip.concertstage.existing.compatibility': ('Save compatibility · hidden from creative · professional replacement', 'Compatibilité des sauvegardes · masqué du créatif · remplacement professionnel'), 'tooltip.concertstage.truss_size': ('Footprint: %s × %s × 1 blocks (length × height × depth)', 'Encombrement : %s × %s × 1 blocs (longueur × hauteur × profondeur)'), 'tooltip.concertstage.tower_size': ('Height: %s blocks · carriage: %s blocks', 'Hauteur : %s blocs · chariot : %s blocs')}.items():lang[k]=v[lang_index]
  p.dump(dest,lang)
 # All new blocks are harvestable with a pickaxe without forcing a tool tier.
 tag=D/'tags/blocks/stage_equipment.json';p.dump(tag,{'replace':False,'values':['concertstage:'+x for x in p.NAMES]})
 mine=R/'common/src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json';data=json.loads(mine.read_text())if mine.exists()else{'replace':False,'values':[]};data['values']=list(dict.fromkeys(data['values']+['concertstage:'+x for x in p.NAMES]));p.dump(mine,data)
 print('Recipes, loot, translations and tags:',len(specs),'definitions')
if __name__=='__main__':finish(); import removed_blocks; removed_blocks.purge()
