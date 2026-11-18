# Contrôle qualité

Contrôles exécutés par script dans Blender 5.3.0 sur le poste de travail, **après réouverture de chaque fichier .blend enregistré**.

| Modèle | S'ouvre | Bibliothèques externes | Images manquantes | Échelle ≠ 1 | Maillages tournés | Sommets isolés | Faces dégénérées | Faces sans matériau | Pièces flottantes | Test TILT | Pivots |
|---|---|---|---|---|---|---|---|---|---|---|---|
| CSC PHANTOM S1 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | PAN, ROOT, TILT |
| CSC PROFILE P1 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | PAN, ROOT, TILT |
| CSC RAZOR B1 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | PAN, ROOT, TILT |
| CSC FLASHCORE FX | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROOT, TILT |
| CSC PIXELBAR B16 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROOT, TILT |
| CSC PIXELTUBE T1 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROLL, ROOT |
| CSC BLINDER X2 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROOT, TILT |
| CSC BLINDER X4 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROOT, ROW_1, ROW_2, TILT |
| CSC BLINDER X8 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROOT, TILT |
| CSC FOLLOWSPOT F1 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | PAN, ROOT, TILT |
| CSC PIXELWASH W37 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | PAN, ROOT, TILT |
| CSC LASERCORE L1 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROOT, TILT |
| CSC FLOODLIGHT F8 | oui | 0 | 0 | 0 | 0 | 0 | 0 | 0 | aucune | ok | ROOT, TILT |

## Ce que ces contrôles couvrent

- **Pièces flottantes** : à la construction, chaque primitive du LOD0 doit toucher (boîtes englobantes, tolérance 1,5 mm) le reste de l'appareil ; un seul groupe connexe = aucune pièce flottante. Un défaut détecté sur le FOLLOWSPOT F1 (molette de blocage du pied) a été corrigé avant livraison.
- **Normales** : recalculées vers l'extérieur sur chaque primitive à la construction.
- **Rendus** : 6 vues par appareil et la vue d'ensemble sont des rendus EEVEE des fichiers livrés.

## Contrôles visuels effectués

Vue trois quarts des 13 appareils, vue d'ensemble, et un échantillon de vues arrière / face / optiques / allumées (PHANTOM, PROFILE, FLASHCORE, FOLLOWSPOT, LASERCORE, BLINDER X4 / X8, PIXELWASH). Corrections faites après inspection : matrice LED du FLASHCORE refaite, intensité des vues allumées réduite, cadrages.

## Non vérifié / limites connues

- Les 78 rendus individuels n'ont pas tous été inspectés un par un (échantillon ci-dessus).
- Comparaison aux références IRL faite sur les cotes et la construction, **pas sur photographies** (voir IRL_References.md).
- Intersections : les pièces s'interpénètrent volontairement ; aucune recherche automatique d'intersections involontaires n'a été faite.
- Collisions sur la course complète PAN/TILT non testées.
- Les lentilles sont des dômes opaques sombres (pas de verre transparent ni d'optique interne visible).
- Ouverture testée uniquement avec Blender 5.3.0 (Windows).
- Aucune intégration ni essai dans Minecraft (hors périmètre).