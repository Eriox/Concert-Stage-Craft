# Articulations et pivots

Chaque fichier contient une collection `PIVOTS` d'objets vides (Empty) sans rotation ni échelle. Les maillages sont parentés au pivot qui les entraîne ; leurs sommets sont exprimés dans le repère du pivot. Les objets LOD1 / LOD2 sont parentés aux mêmes pivots.

- `ROOT` : origine de l'appareil (surface d'appui).
- `PAN` : rotation autour de **Z** local.
- `TILT` : rotation autour de **X** local (axe des tourillons).
- `ROW_1`, `ROW_2` (BLINDER X4) : orientation manuelle de chaque rangée autour de X.
- `ROLL` (PIXELTUBE T1) : rotation du tube dans ses pinces autour de X.

| Modèle | Pivot | Parent | Position absolue au repos (m) | Type |
|---|---|---|---|---|
| CSC PHANTOM S1 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC PHANTOM S1 | `PAN` | ROOT | (0.000, 0.000, 0.135) | motorisé |
| CSC PHANTOM S1 | `TILT` | PAN | (0.000, 0.000, 0.500) | motorisé |
| CSC PROFILE P1 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC PROFILE P1 | `PAN` | ROOT | (0.000, 0.000, 0.100) | motorisé |
| CSC PROFILE P1 | `TILT` | PAN | (0.000, 0.000, 0.440) | motorisé |
| CSC RAZOR B1 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC RAZOR B1 | `PAN` | ROOT | (0.000, 0.000, 0.110) | motorisé |
| CSC RAZOR B1 | `TILT` | PAN | (0.000, 0.000, 0.330) | motorisé |
| CSC FLASHCORE FX | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC FLASHCORE FX | `TILT` | ROOT | (0.000, 0.000, 0.165) | TILT motorisé |
| CSC PIXELBAR B16 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC PIXELBAR B16 | `TILT` | ROOT | (0.000, 0.000, 0.205) | TILT motorisé |
| CSC PIXELTUBE T1 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC PIXELTUBE T1 | `ROLL` | ROOT | (0.000, 0.000, 0.120) | manuel |
| CSC BLINDER X2 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC BLINDER X2 | `TILT` | ROOT | (0.000, 0.000, 0.155) | étrier manuel |
| CSC BLINDER X4 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC BLINDER X4 | `TILT` | ROOT | (0.000, 0.000, 0.230) | étrier manuel |
| CSC BLINDER X4 | `ROW_1` | TILT | (0.000, 0.000, 0.330) | étrier manuel |
| CSC BLINDER X4 | `ROW_2` | TILT | (0.000, 0.000, 0.130) | étrier manuel |
| CSC BLINDER X8 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC BLINDER X8 | `TILT` | ROOT | (0.000, 0.000, 0.260) | étrier manuel |
| CSC FOLLOWSPOT F1 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC FOLLOWSPOT F1 | `PAN` | ROOT | (0.000, 0.000, 1.220) | manuel ou motorisable |
| CSC FOLLOWSPOT F1 | `TILT` | PAN | (0.000, 0.000, 1.460) | manuel ou motorisable |
| CSC PIXELWASH W37 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC PIXELWASH W37 | `PAN` | ROOT | (0.000, 0.000, 0.110) | motorisé |
| CSC PIXELWASH W37 | `TILT` | PAN | (0.000, 0.000, 0.430) | motorisé |
| CSC LASERCORE L1 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC LASERCORE L1 | `TILT` | ROOT | (0.000, 0.000, 0.165) | étrier manuel |
| CSC FLOODLIGHT F8 | `ROOT` | — | (0.000, 0.000, 0.000) | origine |
| CSC FLOODLIGHT F8 | `TILT` | ROOT | (0.000, 0.000, 0.215) | étrier manuel |

**Test réalisé :** dans chaque fichier rouvert, le pivot `TILT` (ou `ROLL`) a été tourné de 0,6 rad puis remis à zéro ; les maillages enfants suivent sans déformation d'échelle (voir Quality_Control.md).

**Non vérifié :** l'absence de collision mécanique sur toute la course PAN/TILT (les dégagements ont été prévus à la conception mais pas testés angle par angle) ; les butées et courses réelles sont à définir à l'intégration.

Pose de repos : tête horizontale, faisceau vers −Y. Les appareils fixes n'ont pas de PAN/TILT motorisé fictif.