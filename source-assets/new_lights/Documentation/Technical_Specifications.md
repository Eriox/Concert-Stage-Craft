# Fiches techniques — modèles Blender

Unités : 1 unité Blender = 1 m = 1 bloc Minecraft (16 px = 1 m). Axes : Z vers le haut, face avant / faisceau vers −Y, X = largeur. Origine `ROOT` au centre de la surface d'appui (z = 0). Toutes les transformations des maillages sont appliquées (échelle 1, rotation 0).

| Modèle | Catégorie | L × P × H (mm) | Tris LOD0 | LOD1 | LOD2 | Objets maillés LOD0 | Cellules | Points d'émission | Points de fixation |
|---|---|---|---|---|---|---|---|---|---|
| CSC PHANTOM S1 | Moving Spot | 499 × 632 × 675 | 7888 | 1742 | 348 | 8 | 0 | 1 | 3 |
| CSC PROFILE P1 | Moving Profile | 462 × 555 × 590 | 7840 | 1884 | 352 | 7 | 0 | 1 | 3 |
| CSC RAZOR B1 | Moving Beam | 426 × 352 × 470 | 6616 | 1572 | 340 | 7 | 0 | 1 | 3 |
| CSC FLASHCORE FX | Hybrid Strobe | 434 × 169 × 258 | 7644 | 1080 | 192 | 28 | 24 | 3 | 2 |
| CSC PIXELBAR B16 | Motorized LED Bar | 1020 × 158 × 270 | 8936 | 1416 | 64 | 22 | 16 | 16 | 3 |
| CSC PIXELTUBE T1 | RGB LED Tube | 1070 × 130 × 148 | 3372 | 956 | 136 | 19 | 16 | 16 | 4 |
| CSC BLINDER X2 | Dual Blinder | 507 × 155 × 292 | 4008 | 1048 | 216 | 7 | 2 | 2 | 2 |
| CSC BLINDER X4 | Quad Blinder | 512 × 212 × 435 | 5888 | 1664 | 452 | 12 | 4 | 4 | 2 |
| CSC BLINDER X8 | Mega Blinder | 1022 × 208 × 535 | 11048 | 3036 | 780 | 13 | 8 | 8 | 3 |
| CSC FOLLOWSPOT F1 | Followspot | 1034 × 1501 × 1685 | 8660 | 2302 | 456 | 6 | 0 | 1 | 1 |
| CSC PIXELWASH W37 | Pixel Wash | 539 × 319 × 636 | 15586 | 2924 | 229 | 45 | 37 | 37 | 3 |
| CSC LASERCORE L1 | RGB Laser | 438 × 406 × 256 | 6208 | 1164 | 60 | 5 | 0 | 6 | 2 |
| CSC FLOODLIGHT F8 | Architectural Floodlight | 786 × 198 × 425 | 15660 | 2676 | 60 | 13 | 8 | 8 | 3 |

Les dimensions sont celles de la boîte englobante du modèle en pose de repos (tête horizontale), accessoires compris (poignées, molettes, pied).

## CSC PHANTOM S1

- **Caractéristiques modélisées :** Tête 0,54 m, lentille Ø196 mm en retrait de 50 mm, dissipateur 9 ailettes + ventilateur, 3 grilles, écran, 6 connecteurs
- **Points de fixation :** MOUNT_OMEGA_1, MOUNT_OMEGA_2, MOUNT_SAFETY
- **Repères de préparation :** ZOOM_GROUP, FOCUS_GROUP, PRISM_1, PRISM_2, IRIS, GOBO_ROT_1, GOBO_ROT_2, ANIM_WHEEL, COLOR_CMY, LED_ENGINE

## CSC PROFILE P1

- **Caractéristiques modélisées :** Corps 0,49 m, collier de découpe + 4 carters, cadre frontal carré, lentille Ø156 mm, poignée et ventilateur arrière
- **Points de fixation :** MOUNT_OMEGA_1, MOUNT_OMEGA_2, MOUNT_SAFETY
- **Repères de préparation :** ZOOM_GROUP, FOCUS_GROUP, FRAMING_ROTATION, IRIS, PRISM, GOBO_ROT, ANIM_WHEEL, COLOR_MIX, LED_ENGINE, FRAMING_BLADE_1, FRAMING_BLADE_2, FRAMING_BLADE_3, FRAMING_BLADE_4

## CSC RAZOR B1

- **Caractéristiques modélisées :** Tête de révolution 0,30 m, lentille bombée Ø188 mm saillante, ventilateur + 10 ailettes radiales, socle réduit 4 connecteurs
- **Points de fixation :** MOUNT_OMEGA_1, MOUNT_OMEGA_2, MOUNT_SAFETY
- **Repères de préparation :** FOCUS, FROST, PRISM_8F, GOBO_WHEEL, COLOR_WHEEL, LAMP_SOURCE

## CSC FLASHCORE FX

- **Caractéristiques modélisées :** 12 segments strobe + 12 zones RGB (matrice 4 × 4), dissipateur + ventilateur arrière, œillets de sécurité
- **Points de fixation :** MOUNT_OMEGA_1, MOUNT_OMEGA_2
- **Repères de préparation :** —

## CSC PIXELBAR B16

- **Caractéristiques modélisées :** 16 lentilles Ø46 mm au pas de 57,5 mm, tête profilée 0,93 m, 30 ailettes, 2 platines oméga, 6 connecteurs
- **Points de fixation :** MOUNT_OMEGA_1, MOUNT_OMEGA_2, MOUNT_SAFETY
- **Repères de préparation :** ZOOM_CARRIAGE

## CSC PIXELTUBE T1

- **Caractéristiques modélisées :** 16 segments de diffuseur, dos aluminium, 2 embouts + œillets, bandeau de commande, 2 pinces sur pied
- **Points de fixation :** MOUNT_HOLDER_1, MOUNT_HOLDER_2, MOUNT_EYEBOLT_L, MOUNT_EYEBOLT_R
- **Repères de préparation :** —

## CSC BLINDER X2

- **Caractéristiques modélisées :** 2 cellules Ø176 mm à réflecteur + puce COB, étrier acier, poignée, ailettes, 4 connecteurs
- **Points de fixation :** MOUNT_CLAMP, MOUNT_SAFETY
- **Repères de préparation :** —

## CSC BLINDER X4

- **Caractéristiques modélisées :** 4 cellules 2 × 2, 2 rangées orientables, flasques + tube transversal, bloc alim arrière
- **Points de fixation :** MOUNT_CLAMP, MOUNT_SAFETY
- **Repères de préparation :** —

## CSC BLINDER X8

- **Caractéristiques modélisées :** 8 cellules 4 × 2, cadre + croisillon, 24 ailettes, 2 ventilateurs, étrier renforcé à patins
- **Points de fixation :** MOUNT_CLAMP_1, MOUNT_CLAMP_2, MOUNT_SAFETY
- **Repères de préparation :** —

## CSC FOLLOWSPOT F1

- **Caractéristiques modélisées :** Corps 1,45 m, changeur 6 couleurs, leviers IRIS / DOUSER, bagues FOCUS / ZOOM, 4 poignées, viseur, trépied
- **Points de fixation :** MOUNT_STAND_SPIGOT
- **Repères de préparation :** COLOR_FRAME_1, COLOR_FRAME_2, COLOR_FRAME_3, COLOR_FRAME_4, COLOR_FRAME_5, COLOR_FRAME_6, IRIS, DOUSER, ZOOM, FOCUS, SOURCE

## CSC PIXELWASH W37

- **Caractéristiques modélisées :** 37 lentilles Ø49 mm en 4 couronnes, tête Ø410 mm, ventilateur + 12 ailettes radiales
- **Points de fixation :** MOUNT_OMEGA_1, MOUNT_OMEGA_2, MOUNT_SAFETY
- **Repères de préparation :** ZOOM_CARRIAGE

## CSC LASERCORE L1

- **Caractéristiques modélisées :** Boîtier 340 × 353 × 168 mm, fenêtre optique, 2 ventilateurs, clé + arrêt d'urgence, 7 connecteurs
- **Points de fixation :** MOUNT_CLAMP, MOUNT_SAFETY
- **Repères de préparation :** GALVO_SCANNER, SAFETY_SHUTTER, SOURCE_R, SOURCE_G, SOURCE_B

## CSC FLOODLIGHT F8

- **Caractéristiques modélisées :** 8 modules de 6 lentilles (48), 24 ailettes, étrier-pied, poignée, 4 repères de volets
- **Points de fixation :** MOUNT_CLAMP_1, MOUNT_CLAMP_2, MOUNT_SAFETY
- **Repères de préparation :** BARNDOOR_MOUNT_1, BARNDOOR_MOUNT_2, BARNDOOR_MOUNT_3, BARNDOOR_MOUNT_4

## Matériaux (communs, procéduraux, sans texture externe)

`CSC_body` anthracite mat · `CSC_body2` métal peint · `CSC_panel` · `CSC_plastic` · `CSC_rubber` · `CSC_alu` / `CSC_aludark` aluminium anodisé · `CSC_steel` acier satiné · `CSC_accent` (signature CSC orange anodisé) · `CSC_reflector` · `CSC_lens` verre optique · `CSC_led` lentille de cellule · `CSC_ledrefl` réflecteur de blinder · `CSC_chip` · `CSC_diffuser` · `CSC_screen` · `CSC_label` · connecteurs.

Les matériaux `CSC_led`, `CSC_ledrefl`, `CSC_diffuser`, `CSC_lens`, `CSC_chip` ont une émission à 0 dans les fichiers (appareil éteint) ; les rendus « 6_lit » l'activent temporairement.