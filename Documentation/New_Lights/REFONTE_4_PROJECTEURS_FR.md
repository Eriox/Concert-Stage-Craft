# Refonte 3D de VORTEX X1, LUMORA W7, LUMORA W19 et PULSESTRIKE M

Les quatre projecteurs historiques du mod ont été remodelés dans Blender 5.3 avec le même langage de forme que les 13 nouveaux
appareils (corps sombres, anneaux et pastilles orange, base à écran, marquage CSC), puis intégrés à la place des anciens modèles.
Ce sont des créations originales : les appareils du commerce cités n'ont servi que de repère de catégorie et de proportions.

| Projecteur | Repère de proportions | Zones lumineuses | Triangles (proche / loin / très loin) | SHA-256 du `.blend` |
|---|---|---|---|---|
| CSC VORTEX X1 | ROBE MegaPointe | 1 (lentille Ø 142 mm) | 8100 / 1798 / 456 | `08bf005b3d77…` |
| CSC LUMORA W7 | ROBE Spiider | 19 (18 + centrale) | 11388 / 2278 / 873 | `7d7e4be68f1f…` |
| CSC LUMORA W19 | ROBE Tarrantula | 37 (36 + centrale) | 15840 / 2998 / 1413 | `37f95857026f…` |
| CSC PULSESTRIKE M | Chauvet Color STRIKE M | 14 RVB + 28 blanches | 9516 / 1460 / 564 | `9bc07e92fbc7…` |

## Ce qui a changé

- `source-assets/new_lights/Blender/` : quatre nouveaux `.blend` (pivots ROOT / PAN / TILT, collections, LOD1 et LOD2, points
  d'émission et d'accroche, comme les 13 autres). `source-assets/new_lights/Scripts/` : le générateur complet (17 modèles).
- `assets/concertstage/lightshow/csc_{vortex_x1,lumora_w7,lumora_w19,pulsestrike_m}.json` : géométrie exportée depuis ces fichiers.
- `models/lightshow/*.obj` (inventaire), `lightshow/palette.json` et `textures/block/lightshow_palette.png` (couleurs ajoutées à la
  suite, les cases existantes ne bougent pas), `external/models/*` et vignettes des profils GDTF / QLC+ : régénérés.
- Aucune table Java n'a dû changer : hauteurs d'axe tilt (0,361 / 0,322 / 0,396 / 0,222 m), nombre, type et ordre des zones sont
  ceux des anciens modèles (écart maximal d'une cellule : 4 mm sur les LUMORA, 3 mm en hauteur sur le PULSESTRIKE). Modes DMX, profils consoles et
  shows enregistrés restent donc valables.
- VORTEX X1 : le faisceau part maintenant de toute la lentille frontale (rayon 71 mm au lieu d'une source de 29 mm au fond du
  nez). Le faisceau est plus large à la sortie de l'appareil ; l'angle, lui, ne change pas.

## Correction trouvée en route : niveaux de détail éloignés des 13 nouveaux projecteurs

En contrôlant l'export, j'ai constaté que les maillages « loin » et « très loin » (`lod_far`, `lod_low`) des 13 nouveaux projecteurs
étaient décalés d'une hauteur de pivot : la lyre était descendue de la hauteur de la base et la tête de la hauteur de l'axe tilt
(la tête du PHANTOM S1 se retrouvait 0,5 m trop bas). Cause : les collections LOD1 / LOD2 sont masquées dans les `.blend`,
Blender n'évalue pas leurs objets et `matrix_world` ne contenait pas le pivot parent. `tools/lightshow/export_new_lights_blender.py`
calcule désormais la matrice monde par la chaîne de parents, et les 17 projecteurs ont été réexportés. Le niveau proche (`parts`),
les zones, les pivots et les faisceaux des 13 sont strictement identiques à avant ; seuls leurs deux niveaux éloignés changent.
`check_new_assets.py` vérifie maintenant que chaque niveau de détail est bien posé sur son pivot (36 écarts sur l'archive d'origine,
0 maintenant). Ce niveau « très loin » est aussi celui affiché quand un pack de shaders désactive le cache GPU : pour les quatre
modèles refaits, il garde donc les lentilles et les cellules.

## Vérifications faites

- Les quatre `.blend` rouverts dans Blender 5.3.0 : aucune bibliothèque liée, aucun sommet libre, aucune face dégénérée ni sans
  matériau, échelles à 1, rotation du pivot TILT propre.
- Suites hors jeu, toutes relancées après intégration : `run.sh` 156, `run_external.sh` 148 + 27 + 756, `run_new_fixtures.sh` 2812,
  `run_new_optics.sh` 26 × 4096 points, `run_optics.sh` 19, `check_new_assets.py` — 0 échec.

## Ce qui n'a pas été vérifié

- **Aucun lancement de Minecraft** : compilation Gradle, rendu en jeu, aspect dans l'inventaire et FPS restent à contrôler.
- Les réglages d'affichage des objets d'inventaire (`models/item/csc_*.json`) sont ceux des anciens modèles ; les gabarits sont
  proches, mais le cadrage peut demander une retouche.
- Imports grandMA2 / grandMA3 / dot2 / QLC+ des profils régénérés : structure contrôlée par les tests, pas d'import réel.

## Refaire l'export

```
blender -b --python tools/lightshow/export_new_lights_blender.py -- source-assets/new_lights/Blender <dossier de sortie>
python3 -I tools/lightshow/integrate_remodelled_fixtures.py <dossier de sortie> .
sh tools/external/build.sh
```
