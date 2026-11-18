# Passages de tours — documentation technique (V3)

Échelle : **16 unités Blockbench = 1 m = 1 bloc**. Y = hauteur, X = largeur, Z = profondeur (avant = −Z). Origine de chaque modèle : coin avant-gauche de l’emprise, au sol (y = 0), sauf indication.

## 1. Mesures relevées sur les tours de la 1.6.0

Source : `tools/blockbench/realistic/towers/motor_tower_*` et `realistic/carriages/carriage_*` (coordonnées dans la cellule 0…16 de la tour).

| Élément | Mesure | Remarque |
|---|---|---|
| Mât (section) | 0,75 → 15,25 u (14,5 u = 0,906 m) | identique pour 10, 14, 18, 22 blocs et toutes variantes (corner3/6, h2, noir) |
| Chaîne motrice | jusqu’à z = −1,78 u, sur toute la hauteur | impose 9,78 u depuis l’axe |
| Chariot (sleeve) | x −0,75 → 16,9 ; z −1,66 → 16,7 ; h 14,5 u (h2 : 30,5 u) | ≤ 9,78 u depuis l’axe ; course de y = 16,74 jusqu’à la tête |
| Stabilisateurs (pied) | en croix, 19,5 u (10 blocs) → 21,9 u (22 blocs) depuis l’axe ; jambes de force jusqu’à y = 11,2 | les pieds/lisses basses doivent éviter les lignes des bras |
| Palan au sol | côté −Z, jusqu’à y = 11,5, à ≤ 10,6 u de l’axe | dans l’ouverture |

**Conséquence :** un seul profil de mât existe → toutes les tours partagent les mêmes passages. La hauteur de tour ne change que la longueur des stabilisateurs (vérifiée par les tests).

## 2. Fiches des modèles de passage

### CSC_Tower_PassThrough_Scaffold

- Fichier : `Tower_PassThrough/Mast_Openings/CSC_Tower_PassThrough_Scaffold.bbmodel` (+ LOD : `LOD/CSC_Tower_PassThrough_Scaffold_LOD.bbmodel`)
- Dimensions extérieures : 3,00 × 3,00 m (48 × 48 u)
- Axe de la tour : x = 24, z = 24 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = bloc (1,1) de l’emprise
- Ouverture libre : x 12.44 → 35.56, z 12.44 → 35.56 (23.12 × 23.12 u = 1.445 × 1.445 m), sur toute la hauteur
- Hauteur de plateforme : 2,00 m (y = 32)
- Épaisseur des cadres : plancher 0,64 u sur lisses U 0,8 u ; montants Ø48,3 ; rosaces Ø124
- Compatible : mât + chaîne + chariot (toutes orientations 0/90/180/270°)
- Dégagements / limites : marge 1,78 u (11 cm) autour de l’enveloppe mât+chaîne/chariot ; chariot tourné à 45° : NON
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : réussis

### CSC_Tower_PassThrough_Scaffold_Sleeve

- Fichier : `Tower_PassThrough/Sleeve_Openings/CSC_Tower_PassThrough_Scaffold_Sleeve.bbmodel` (+ LOD : `LOD/CSC_Tower_PassThrough_Scaffold_Sleeve_LOD.bbmodel`)
- Dimensions extérieures : 5,00 × 5,00 m (80 × 80 u)
- Axe de la tour : x = 40, z = 40 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = bloc (2,2) de l’emprise
- Ouverture libre : x 24.44 → 55.56, z 24.44 → 55.56 (31.12 × 31.12 u = 1.945 × 1.945 m), sur toute la hauteur
- Hauteur de plateforme : 2,00 m (y = 32)
- Épaisseur des cadres : idem
- Compatible : mât + chariot + adaptateurs ; marge suffisante pour un chariot tourné à 45° (calcul)
- Dégagements / limites : marge 5,78 u (36 cm) ; 1,72 u si chariot à 45°
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : réussis

### CSC_Tower_SidePass_Scaffold

- Fichier : `Tower_PassThrough/Side_Passages/CSC_Tower_SidePass_Scaffold.bbmodel` (+ LOD : `LOD/CSC_Tower_SidePass_Scaffold_LOD.bbmodel`)
- Dimensions extérieures : 3,00 × 2,00 m (48 × 32 u) avec encoche
- Axe de la tour : x = 24, z = -8 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = (1, −1) depuis l’origine
- Ouverture libre : x 12.44 → 35.56, z -19.56 → 3.56 (23.12 × 23.12 u = 1.445 × 1.445 m), sur toute la hauteur
- Hauteur de plateforme : 2,00 m
- Épaisseur des cadres : idem
- Compatible : mât + chariot, tour dans la cellule devant la rive avant
- Dégagements / limites : encoche jusqu’à z = 4,56 (ligne des montants) ; marge 1,78 u
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : réussis

### CSC_Tower_SidePass_Scaffold_Sleeve

- Fichier : `Tower_PassThrough/Side_Passages/CSC_Tower_SidePass_Scaffold_Sleeve.bbmodel` (+ LOD : `LOD/CSC_Tower_SidePass_Scaffold_Sleeve_LOD.bbmodel`)
- Dimensions extérieures : 4,00 × 2,00 m (64 × 32 u) avec encoche
- Axe de la tour : x = 40, z = -8 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = (2, −1)
- Ouverture libre : x 24.44 → 55.56, z -23.56 → 7.56 (31.12 × 31.12 u = 1.945 × 1.945 m), sur toute la hauteur
- Hauteur de plateforme : 2,00 m
- Épaisseur des cadres : idem
- Compatible : chariot à toute orientation
- Dégagements / limites : marge 5,78 u
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : réussis

### CSC_Tower_CornerPass_Scaffold

- Fichier : `Tower_PassThrough/Corner_Passages/CSC_Tower_CornerPass_Scaffold.bbmodel` (+ LOD : `LOD/CSC_Tower_CornerPass_Scaffold_LOD.bbmodel`)
- Dimensions extérieures : 3,00 × 3,00 m en L (48 × 48 u)
- Axe de la tour : x = 8, z = 8 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = (0,0)
- Ouverture libre : x -3.56 → 19.56, z -3.56 → 19.56 (23.12 × 23.12 u = 1.445 × 1.445 m), sur toute la hauteur
- Hauteur de plateforme : 2,00 m
- Épaisseur des cadres : idem
- Compatible : mât + chariot, tour dans la cellule d’angle
- Dégagements / limites : marge 1,78 u
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : réussis

### CSC_Tower_CornerPass_Scaffold_Sleeve

- Fichier : `Tower_PassThrough/Corner_Passages/CSC_Tower_CornerPass_Scaffold_Sleeve.bbmodel` (+ LOD : `LOD/CSC_Tower_CornerPass_Scaffold_Sleeve_LOD.bbmodel`)
- Dimensions extérieures : 4,00 × 4,00 m en L (64 × 64 u)
- Axe de la tour : x = 8, z = 8 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = (0,0)
- Ouverture libre : x -7.56 → 23.56, z -7.56 → 23.56 (31.12 × 31.12 u = 1.945 × 1.945 m), sur toute la hauteur
- Hauteur de plateforme : 2,00 m
- Épaisseur des cadres : idem
- Compatible : chariot à toute orientation
- Dégagements / limites : marge 5,78 u
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : réussis

### CSC_StageDeck_Tower_Opening

- Fichier : `Tower_PassThrough/Mast_Openings/CSC_StageDeck_Tower_Opening.bbmodel` (+ LOD : `LOD/CSC_StageDeck_Tower_Opening_LOD.bbmodel`)
- Dimensions extérieures : 3,00 × 3,00 m, 4 panneaux en moulinet
- Axe de la tour : x = 24, z = 24 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = (1,1)
- Ouverture libre : x 12 → 36, z 12 → 36 (24.00 × 24.00 u = 1.500 × 1.500 m), sur toute la hauteur
- Hauteur de plateforme : réglable par les pieds : ≥ 0,60 m testé (0,40 m = collision)
- Épaisseur des cadres : cadre 90 mm (1,44 u), profils de rive alu
- Compatible : mât + chariot (0/90/180/270°)
- Dégagements / limites : marge 2,22 u ; pieds hors des bras de stabilisateurs
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : voir validation_passages.json

### CSC_StageDeck_Tower_Frame

- Fichier : `Tower_PassThrough/Mast_Openings/CSC_StageDeck_Tower_Frame.bbmodel` (+ LOD : `LOD/CSC_StageDeck_Tower_Frame_LOD.bbmodel`)
- Dimensions extérieures : 3,00 × 3,00 m, cadre seul
- Axe de la tour : x = 24, z = 24 (repère `Attachment Points/Tower_Axis`) — bloc (1,1)
- Ouverture libre : x 12 → 36, z 12 → 36 (24.00 × 24.00 u = 1.500 × 1.500 m), sur toute la hauteur
- Hauteur de plateforme : ≥ 0,60 m
- Épaisseur des cadres : cadre 90 mm
- Compatible : idem
- Dégagements / limites : sans panneaux : base pour panneaux sur mesure
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : voir validation_passages.json

### CSC_StageDeck_Sleeve_Opening

- Fichier : `Tower_PassThrough/Sleeve_Openings/CSC_StageDeck_Sleeve_Opening.bbmodel` (+ LOD : `LOD/CSC_StageDeck_Sleeve_Opening_LOD.bbmodel`)
- Dimensions extérieures : 5,00 × 5,00 m, 4 panneaux en moulinet
- Axe de la tour : x = 40, z = 40 (repère `Attachment Points/Tower_Axis`) — bloc (2,2)
- Ouverture libre : x 24 → 56, z 24 → 56 (32.00 × 32.00 u = 2.000 × 2.000 m), sur toute la hauteur
- Hauteur de plateforme : ≥ 0,60 m
- Épaisseur des cadres : cadre 90 mm
- Compatible : chariot à toute orientation (45° compris)
- Dégagements / limites : marge 6,22 u ; 2,16 u à 45°
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : voir validation_passages.json

### CSC_StageDeck_Tower_Side

- Fichier : `Tower_PassThrough/Side_Passages/CSC_StageDeck_Tower_Side.bbmodel` (+ LOD : `LOD/CSC_StageDeck_Tower_Side_LOD.bbmodel`)
- Dimensions extérieures : 3,00 × 1,50 m avec encoche 1,50 × 0,25 m
- Axe de la tour : x = 24, z = -8 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = (1, −1)
- Ouverture libre : x 12 → 36, z -20 → 4 (24.00 × 24.00 u = 1.500 × 1.500 m), sur toute la hauteur
- Hauteur de plateforme : ≥ 0,60 m
- Épaisseur des cadres : cadre 90 mm
- Compatible : mât + chariot
- Dégagements / limites : encoche de 4 u
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : voir validation_passages.json

### CSC_StageDeck_Tower_Corner

- Fichier : `Tower_PassThrough/Corner_Passages/CSC_StageDeck_Tower_Corner.bbmodel` (+ LOD : `LOD/CSC_StageDeck_Tower_Corner_LOD.bbmodel`)
- Dimensions extérieures : 2,00 × 2,00 m avec encoche 0,25 × 0,25 m
- Axe de la tour : x = -8, z = -8 (repère `Attachment Points/Tower_Axis`) — bloc de la tour = (−1, −1)
- Ouverture libre : x -20 → 4, z -20 → 4 (24.00 × 24.00 u = 1.500 × 1.500 m), sur toute la hauteur
- Hauteur de plateforme : ≥ 0,60 m
- Épaisseur des cadres : cadre 90 mm
- Compatible : mât + chariot
- Dégagements / limites : tour dans la cellule diagonale
- Points de connexion : `Attachment Points/*` (CSC_Connection_*, Tower_Axis, Leg_Socket*) ; volume réservé : `Tower Clearance/*` (repères sans géométrie)
- Tests : voir validation_passages.json

## 3. Matrice de compatibilité tours × ouvertures

Testé par intersection géométrique réelle (voir §5) pour les 4 orientations de la tour.

| Ouverture | 10 blocs | 14 blocs | 18 blocs | 22 blocs | Chariot tourné 45° (calcul, non testé) | Chariot + poutres truss attachées |
|---|---|---|---|---|---|---|
| CSC_Tower_PassThrough_Scaffold | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ (voir §4) |
| CSC_Tower_PassThrough_Scaffold_Sleeve | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (voir §4) |
| CSC_Tower_SidePass_Scaffold | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ (voir §4) |
| CSC_Tower_SidePass_Scaffold_Sleeve | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (voir §4) |
| CSC_Tower_CornerPass_Scaffold | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ (voir §4) |
| CSC_Tower_CornerPass_Scaffold_Sleeve | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (voir §4) |
| CSC_StageDeck_Tower_Opening | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ (voir §4) |
| CSC_StageDeck_Tower_Frame | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ (voir §4) |
| CSC_StageDeck_Sleeve_Opening | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (voir §4) |
| CSC_StageDeck_Tower_Side | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ (voir §4) |
| CSC_StageDeck_Tower_Corner | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ (voir §4) |

Variantes testées : corner3, corner6, h2 (chariot double hauteur) ; les variantes noires ont la même géométrie.

## 4. Limites documentées

- **Poutres truss accrochées au chariot** : elles partent à l’horizontale vers les tours voisines ; aucune ouverture ne peut les laisser traverser un plancher. Avec des poutres, la course basse du chariot doit rester au-dessus du garde-corps du plancher (plancher + 1,10 m + demi-hauteur de poutre).
- **Plateformes deck autour d’une tour** : surface ≥ 0,60 m (testé). À 0,40 m, le cadre touche les jambes de force des stabilisateurs (collision détectée par les tests).
- **Pieds et lisses basses** : jamais sur les lignes des bras de stabilisateurs (axe de la tour ± 22,6 u). Les lisses basses intérieures sont montées au 2e niveau de rosaces (0,95 m) pour passer au-dessus des jambes de force.
- **Passages mât** : marge de 1,78 u (≈ 11 cm) ; ils laissent aussi passer le chariot droit (même enveloppe mesurée) mais pas un chariot tourné à 45°. Utiliser les variantes *Sleeve* dès que le chariot ou des adaptateurs non standard doivent traverser.
- Démonstrations visuelles Minecraft : **ni plan de construction, ni validation de sécurité**.

## 5. Validation géométrique

Script `tests3.py` / `run_tests.py` : chaque élément est traité comme un solide convexe ; les points de surface de chaque solide sont testés dans les solides de l’autre ensemble (dans les deux sens). Tour placée sur l’axe du modèle, 4 orientations, 4 tours (10M corner3, 14M corner6, 18M corner3 h2, 22M corner6 h2).

- Test 1 (mât seul + pied de tour) et Test 4/5 (plateaux, lisses, garde-corps, diagonales) : intersections mât/stabilisateurs/palan ↔ tous les éléments.
- Tests 2/3 (sleeve à différentes hauteurs, mouvement simulé) : enveloppe convexe du chariot balayée de sa position basse jusqu’à 72 u (au-dessus de tout élément d’échafaudage).
- Test 6 (variantes de tours) et Test 7 (LOD) : mêmes tests sur toutes les tours et sur chaque LOD.

**Résultat : 27 / 27 cas réussis**, chacun sur 16 combinaisons (4 tours × 4 orientations).  
Contrôle négatif (tour décalée de 6 u) : collision détectée = True (Lisse O 1.57 m ↔ Support du capteur bas). Contrôle plateforme deck à 0,40 m : collision détectée avec les jambes de force (attendu).

| Cas | Résultat |
|---|---|
| CSC_Tower_PassThrough_Scaffold | ✅ |
| CSC_Tower_PassThrough_Scaffold_LOD | ✅ |
| CSC_Tower_PassThrough_Scaffold_Sleeve | ✅ |
| CSC_Tower_PassThrough_Scaffold_Sleeve_LOD | ✅ |
| CSC_Tower_SidePass_Scaffold | ✅ |
| CSC_Tower_SidePass_Scaffold_LOD | ✅ |
| CSC_Tower_SidePass_Scaffold_Sleeve | ✅ |
| CSC_Tower_SidePass_Scaffold_Sleeve_LOD | ✅ |
| CSC_Tower_CornerPass_Scaffold | ✅ |
| CSC_Tower_CornerPass_Scaffold_LOD | ✅ |
| CSC_Tower_CornerPass_Scaffold_Sleeve | ✅ |
| CSC_Tower_CornerPass_Scaffold_Sleeve_LOD | ✅ |
| CSC_StageDeck_Tower_Opening @ 0.8 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Opening @ 1.2 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Opening_LOD @ 0.8 m | ✅ |
| CSC_StageDeck_Tower_Frame @ 0.8 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Frame @ 1.2 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Frame_LOD @ 0.8 m | ✅ |
| CSC_StageDeck_Sleeve_Opening @ 0.8 m (+ pieds) | ✅ |
| CSC_StageDeck_Sleeve_Opening @ 1.2 m (+ pieds) | ✅ |
| CSC_StageDeck_Sleeve_Opening_LOD @ 0.8 m | ✅ |
| CSC_StageDeck_Tower_Side @ 0.8 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Side @ 1.2 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Side_LOD @ 0.8 m | ✅ |
| CSC_StageDeck_Tower_Corner @ 0.8 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Corner @ 1.2 m (+ pieds) | ✅ |
| CSC_StageDeck_Tower_Corner_LOD @ 0.8 m | ✅ |

**Démonstrations :**

| Démo | Tour testée (enveloppe) | Résultat |
|---|---|---|
| CSC_Demo_Tower_Surround_Scaffold | LiftTower 18M (corner3 h2) @ [24, 24] | ✅ |
| CSC_Demo_Sleeve_Through_Opening | LiftTower 22M (corner6 h2) @ [40, 40] | ✅ |
| CSC_Demo_Tower_Through_Deck | LiftTower 10M (corner3) @ [24, 24] | ✅ |
| CSC_Demo_SidePass | LiftTower 14M (corner6) @ [24, -8] | ✅ |
| CSC_Demo_Four_Towers_Stage | LiftTower 10M (corner3) @ [24, 24] | ✅ |
| CSC_Demo_Four_Towers_Stage | LiftTower 10M (corner3) @ [184, 24] | ✅ |
| CSC_Demo_Four_Towers_Stage | LiftTower 18M (corner3 h2) @ [24, 168] | ✅ |
| CSC_Demo_Four_Towers_Stage | LiftTower 14M (corner6) @ [184, 152] | ✅ |

*Limite du test : il ne vérifie que l’absence de pénétration (tolérance 0,03 u), pas l’ouverture effective dans Blockbench. Le contrôle visuel dans Blockbench reste à faire de ton côté.*
