# Rapport d'optimisation

## Mesures réalisées

| Modèle | Tris LOD0 | Tris LOD1 | Tris LOD2 | Objets LOD0 | Objets LOD1 | Objets LOD2 | Taille .blend (Ko) |
|---|---|---|---|---|---|---|---|
| CSC PHANTOM S1 | 7888 | 1742 | 348 | 8 | 3 | 3 | 210 |
| CSC PROFILE P1 | 7840 | 1884 | 352 | 7 | 3 | 3 | 210 |
| CSC RAZOR B1 | 6616 | 1572 | 340 | 7 | 3 | 3 | 194 |
| CSC FLASHCORE FX | 7644 | 1080 | 192 | 28 | 2 | 2 | 193 |
| CSC PIXELBAR B16 | 8936 | 1416 | 64 | 22 | 2 | 2 | 209 |
| CSC PIXELTUBE T1 | 3372 | 956 | 136 | 19 | 2 | 2 | 152 |
| CSC BLINDER X2 | 4008 | 1048 | 216 | 7 | 2 | 2 | 171 |
| CSC BLINDER X4 | 5888 | 1664 | 452 | 12 | 4 | 4 | 176 |
| CSC BLINDER X8 | 11048 | 3036 | 780 | 13 | 2 | 2 | 251 |
| CSC FOLLOWSPOT F1 | 8660 | 2302 | 456 | 6 | 3 | 3 | 229 |
| CSC PIXELWASH W37 | 15586 | 2924 | 229 | 45 | 3 | 3 | 279 |
| CSC LASERCORE L1 | 6208 | 1164 | 60 | 5 | 2 | 2 | 199 |
| CSC FLOODLIGHT F8 | 15660 | 2676 | 60 | 13 | 2 | 2 | 258 |

## Optimisations effectuées

- **Trois niveaux de détail par appareil**, générés par reconstruction (pas par décimation) : LOD0 complet ; LOD1 sans visserie, lamelles, broches, textes ni chanfreins, segments circulaires divisés par 2 ; LOD2 limité aux volumes principaux, segments divisés par 3.
- **Regroupement des objets** : un maillage par fonction et par pivot au LOD0 (BASE, YOKE, HEAD, OPTICS, CONNECTORS, VENTILATION) ; un maillage par pivot aux LOD1 / LOD2. Seules les cellules pilotables restent des objets séparés au LOD0.
- **Matériaux partagés** : une bibliothèque commune de matériaux `CSC_*`, aucune texture image, aucune transparence.
- **PIXELTUBE** : le diffuseur est opaque et segmenté en 16 pixels ; les LED internes cachées ne sont pas modélisées.
- Collections `LOD1` et `LOD2` masquées à l'affichage et au rendu dans les fichiers.

## Limites

- Les pièces sont assemblées par interpénétration (pas d'opérations booléennes) : des faces internes cachées subsistent au LOD0.
- Aucune UV ni atlas de textures : les matériaux sont procéduraux. Un dépliage UV et un atlas seront nécessaires pour l'export vers Minecraft.
- Aucun test dans le moteur Minecraft n'a été fait : **aucun gain de FPS n'est mesuré ni revendiqué**.
- Les budgets de polygones n'ont pas été confrontés à une cible chiffrée du mod (aucune cible fournie).