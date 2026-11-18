# CSC 1.2.0 rév. 16g — FPS, lentilles selon la couleur, gobos (distance et flou)

**Statut : corrigé dans le code, vérifié hors jeu (7 shaders compilés sur Mesa, 156 tests lumière, 19 tests optiques, vérification de types, banc d'essai). PAS vérifié dans Minecraft.**

Base : captures et vidéo du 9 novembre (22 h 37 – 22 h 41). Les carrés noirs ont disparu (rév. 16f).

## FPS

Ce que montrent la vidéo et les captures :
- ligne F3 « shadows 15 maps (15 redrawn) » avec 1 400 à 2 600 maillages, GPU à 100 % pendant l'AutoShow ;
- 58 fps devant les wash rouges proches.

| Cause | Correction |
|---|---|
| Chaque mouvement de lyre redessinait **toutes** les cartes d'ombre **à chaque image**. | Au plus 3, 4 ou 6 cartes par image selon la qualité (au lieu de 6, 12 ou 24), les plus anciennes d'abord. Une lyre en mouvement voit son ombre suivre en 2 à 4 images. |
| Chaque carte dessinait **toutes** les structures dans un rayon autour du projecteur. | Elle ne dessine plus que celles qui sont dans le cône de lumière (à 6 blocs près). |
| Les faisceaux de wash et de PULSESTRIKE dans la brume utilisaient autant de pas de calcul que le VORTEX. Proches de la caméra, ils couvrent tout l'écran. | 60 % des pas pour les wash, 50 % pour le PULSESTRIKE (leur lumière dans l'air est douce). Le VORTEX garde tous ses pas pour son gobo. |

Je n'ai pas mesuré les FPS : ce sera à faire en jeu, dans les mêmes vues.

## Lentilles LUMORA et cellules PULSESTRIKE invisibles selon la couleur

Les émetteurs sont additifs. Une couleur saturée ajoute très peu de luminosité : le bleu pur pèse 7 % de la luminance du blanc. Une lentille allumée en bleu ou en rouge profond paraissait donc sombre sur un corps sombre.

Une LED vue en face sature l'œil ou la caméra : son cœur tire vers le blanc, et la lueur autour garde la couleur. Le cœur de chaque émetteur est maintenant éclairci vers le blanc, jusqu'à une luminance d'environ la moitié de son canal le plus fort. Exemples :
- bleu : cœur bleu clair ;
- rouge : cœur rose-blanc ;
- ambre et blanc : inchangés.

Les blooms et halos gardent la couleur pure.

Cela concerne les cellules LUMORA (à plat et en dôme) et les barres et cellules du PULSESTRIKE M.

## Gobos flous

Le focus d'usine d'un projecteur est **0**. L'ancienne loi rendait le gobo net seulement autour de **50 %**, et totalement flou à 0 % et à 100 %. Un VORTEX dont on n'avait pas touché le focus projetait donc un gobo flou.

Nouvelle loi :
- net de 0 à 60 % ;
- de plus en plus doux de 60 à 100 % (effet « soft focus »).

L'AutoShow (50 %) reste net. Les canaux DMX ne changent pas.

## Gobos mal affichés selon la distance

Le gobo était lu à pleine résolution (256 px) quelle que soit la distance. De loin, un texel devient plus petit qu'un pixel : les barres fines scintillaient et se cassaient en bruit.

Correction :
- les images de gobo reçoivent leurs **mipmaps** une seule fois, avec un filtrage trilinéaire ;
- les shaders lisent le gobo **à la taille du pixel** :
  - sur les surfaces : distance à la caméra et inclinaison de la surface ;
  - dans la brume : distance de chaque pas.
- Si les mipmaps ne peuvent pas être créées, le gobo garde son niveau unique et un filtrage linéaire. La texture ne devient jamais incomplète.

Le banc d'essai a été mis à jour en conséquence : il génère maintenant les mipmaps comme le mod.

## À vérifier en jeu

1. Reprendre la vidéo (AutoShow, gobos sur le public, de près puis de loin) :
   - gobos nets et stables à toutes les distances ;
   - ligne F3 « … redrawn » à 6 au plus.
2. FPS dans les vues des quatre captures (devant les wash rouges), à comparer avec 125 et 58 fps.
3. LUMORA et PULSESTRIKE en bleu, rouge et vert profonds : les lentilles et les barres se voient.
4. VORTEX avec focus à 0, 50 % et 100 % : net, net, doux.

## Fichiers

| Fichier | Modification |
|---|---|
| `client/light/BeamShadows.java` | Budget et ordre des cartes |
| `client/GpuMesh.java` | Tri des structures par le cône de lumière |
| `client/light/VolumeLights.java` | Pas par famille, mipmaps des gobos |
| `client/light/FixtureRenderer.java` | `hotCore` |
| `light/core/BeamOptics.java` | `focusSharpness` |
| `concertstage_optics.glsl` | `cscGoboLod`, `textureLod` |
| `concertstage_light_projector.fsh`, `concertstage_beam_volume.fsh` | Niveau de détail du gobo |
| `tools/shader_harness/harness.c`, `scene.py` | Mipmaps |
| `tools/lightshow_tests/LightshowTest.java` | Test du focus |
