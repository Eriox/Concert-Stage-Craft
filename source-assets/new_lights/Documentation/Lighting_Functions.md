# Fonctions lumineuses préparées

Cette livraison **prépare** les modèles ; aucune fonction n'est programmée dans Minecraft.

- **Points d'émission** : collection `EMISSION_POINTS`, vides nommés `EMIT_*`, flèche (+Z local) orientée dans l'axe du faisceau, parentés au pivot mobile.
- **Cellules** : collection `LED_CELLS`, un objet par cellule, propriétés personnalisées `csc_cell` (nom stable) et `csc_zone` (index ou couronne).
- **Repères internes** : collection `PREP_MARKERS` (zoom, focus, iris, gobos, prismes, couteaux…), simples positions sur l'axe optique — pas de mécanisme modélisé à l'intérieur des têtes.
- **Fixation** : collection `MOUNT_POINTS` (`MOUNT_*`).

| Modèle | Fonctions prévues | Cellules (noms) | Points d'émission |
|---|---|---|---|
| CSC PHANTOM S1 | Dimmer, strobe, PAN/TILT, zoom, focus, iris, 2 roues de gobos rotatifs, roue d'animation, 2 prismes, CMY | — (source unique) | EMIT_MAIN |
| CSC PROFILE P1 | Dimmer, strobe, PAN/TILT, zoom, focus, iris, 4 couteaux + rotation du module, gobos rotatifs, animation, prisme, mélange de couleurs | — (source unique) | EMIT_MAIN |
| CSC RAZOR B1 | Dimmer, strobe, PAN/TILT rapides, faisceau ultra-étroit, roue de couleurs, roue de gobos, prisme 8 facettes, frost, focus | — (source unique) | EMIT_MAIN |
| CSC FLASHCORE FX | Strobe blanc 12 segments, 12 zones RGB indépendantes, dimmer, TILT, animations pixel | STROBE_01…12 (zone 0), RGB_T_01…06 (zone 1), RGB_B_01…06 (zone 2) | EMIT_STROBE, EMIT_RGB_TOP, EMIT_RGB_BOTTOM |
| CSC PIXELBAR B16 | 16 cellules RGBW indépendantes, dimmer, strobe, TILT, zoom (chariot préparé), effets pixel / vague | CELL_01…16 | EMIT_01 … EMIT_16 (16) |
| CSC PIXELTUBE T1 | 16 pixels RGB indépendants, dimmer, strobe, température de couleur, animations par pixel | PIXEL_01…16 | EMIT_PX_01 … EMIT_PX_16 (16) |
| CSC BLINDER X2 | 2 cellules indépendantes, dimmer, strobe, gradation tungstène simulée (à programmer) | CELL_01…02 | EMIT_01, EMIT_02 |
| CSC BLINDER X4 | 4 cellules indépendantes, dimmer, strobe, flash, vague, orientation manuelle par rangée | CELL_01…04 | EMIT_01, EMIT_02, EMIT_03, EMIT_04 |
| CSC BLINDER X8 | 8 cellules indépendantes, dimmer, strobe, flash, vague, animations pixel | CELL_01…08 | EMIT_01 … EMIT_08 (8) |
| CSC FOLLOWSPOT F1 | Dimmer / douser, iris, focus, zoom, 6 cadres de couleur, PAN/TILT manuels ou motorisables, effet de poursuite | — (source unique) | EMIT_MAIN |
| CSC PIXELWASH W37 | 37 pixels RGBW (zones par couronne), dimmer, strobe, PAN/TILT, zoom | CELL_01…37 (zone = couronne 0 à 3) | EMIT_01 … EMIT_37 (37) |
| CSC LASERCORE L1 | Faisceaux laser virtuels RGB (6 points d'émission), obturateur de sécurité, scanner (repères). Aucun système laser réel. | — (source unique) | EMIT_APERTURE, EMIT_BEAM_1, EMIT_BEAM_2, EMIT_BEAM_3, EMIT_BEAM_4, EMIT_BEAM_5 |
| CSC FLOODLIGHT F8 | 8 zones RGBW indépendantes, dimmer, strobe, température de couleur, effets pixel | CELL_01…08 (6 lentilles chacune) | EMIT_01 … EMIT_08 (8) |