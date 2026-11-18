# CSC Veyra — Modèles originaux Concert Stage Craft

Ce lot remplace la conception de référence par une gamme fictive propre au projet. Les dimensions, façades, poignées, panneaux arrière, accessoires d'accroche, systèmes de transport et plaques d'identification ont été redessinés. Ce travail dépasse un changement de noms ou de textures.

La façade des enceintes A1/A2 est divisée en trois panneaux verticaux. A2 possède un panneau central plus large et un caisson plus large et moins profond. Le B2 possède deux baies de grille, deux ports frontaux larges, des renforts internes de ports et un kit de quatre roulettes noires sous le caisson. Les petits accents de gamme sont cuivrés et mats.

Sur les enceintes A1/A2, les flancs sont fermés et portent des poignées droites encastrées. Deux joues latérales à perçages en éventail remplacent la mécanique centrale précédente. Les prises arrière sont réparties horizontalement sur un panneau de service original. Aucun logo ni nom de fabricant réel ne figure sur les modèles.

## Fichiers

Models : trois fichiers .blend individuels, avec HIGH_DETAIL et GAME_READY, et une scène comparative Showcase. Exports : les trois GLB optimisés, avec leurs textures incorporées. Textures : badges originaux, grille alpha, microrelief, normale et rugosité du revêtement. Renders : face, arrière, côté et perspective pour chaque modèle, ainsi que les deux vues comparatives. Documentation : dimensions et rapports de contrôle. Tools : scripts de reconstruction. Fonts : police de présentation et sa licence.

## Dimensions de conception

Échelle métrique : 1 mètre Blender correspond à 1 bloc Minecraft, soit 16 unités Blockbench. L'origine des modèles est au centre du bord avant au niveau du sol. X = largeur, +Y = arrière, Z = hauteur. Les GLB utilisent la convention Y-up du format glTF et retrouvent l'orientation Blender lors de leur réimportation.

| Modèle | Largeur m | Hauteur m | Profondeur m | Triangles HD | Triangles optimisés |
|---|---:|---:|---:|---:|---:|
| CSC Veyra A1 | 1.180 | 0.420 | 0.620 | 469504 | 3350 |
| CSC Veyra A2 | 1.240 | 0.400 | 0.580 | 471032 | 3350 |
| CSC Veyra B2 | 1.120 | 0.820 | 0.880 | 517274 | 4766 |

Les dimensions ci-dessus proviennent de la géométrie évaluée, et non d'un objet de mesure ajouté. Les deux variantes d'un modèle ont la même enveloppe extérieure et les mêmes repères CSC_Attach_*.

## Modification dans Blender

Ouvrir un fichier individuel avec Blender 4.5.3 LTS ou une version compatible. HIGH_DETAIL est visible à l'ouverture. Pour observer GAME_READY, masquer HIGH_DETAIL dans le viewport et au rendu, puis afficher GAME_READY. Chaque pièce reste un maillage éditable dans des collections thématiques : Cabinet, Front Grille, Rear Panel, Handles, Rigging, Connectors, Screws, Identity, Transport Kit et Attachment Points. Les textes des plaques sont des textures PNG modifiables ; generate_textures.py permet de les régénérer.

La scène Showcase utilise des collections liées par chemins relatifs. Conserver les fichiers du dossier Models ensemble ; modifier un modèle individuel actualise sa présence dans la scène comparative. Les textures sont aussi incorporées aux .blend. Les scripts de génération utilisent des opérations géométriques ordinaires de Blender et les fichiers restent utilisables sans ces scripts.

Le kit Transport Kit du Veyra B2 est séparé du caisson. Pour une configuration sans roulettes, masquer ce kit puis abaisser l'ensemble du caisson et de ses patins de 0,166 m. Les dimensions publiées du B2 incluent les roulettes dans sa configuration par défaut.

## Matériaux et UV

Noir mat texturé pour le caisson, acier peint pour les grilles et les joues, acier pour les axes, caoutchouc pour les poignées et les roues. Les grilles HD sont réellement perforées. La variante optimisée remplace les petites perforations par une texture alpha ; les GLB utilisent un seuil alpha MASK. Les UV sont organisés par pièce avec répétition des textures de revêtement et normalisation des plaques. Il ne s'agit pas d'un atlas UV unique pour un bake complet. La normal map du revêtement est utilisée dans Blender ; les cartes de hauteur et de rugosité sont également fournies pour les adaptations futures.

## Vérifications réalisées

Réouverture des trois modèles natifs et de la scène comparative ; résolution des chemins relatifs ; vérification des dimensions et de l'égalité des variantes ; présence et valeurs finies des UV et normales ; suppression des faces dégénérées ; contrôle des repères ; inspection des quatre vues de chaque modèle ; export des trois GLB autonomes puis réimportation dans Blender et contrôle de leur enveloppe. Les résultats détaillés sont dans FINAL_VALIDATION.json.

Les pièces sont des équipements fictifs de visualisation. Les indices A1/A2/B1/B2 ne constituent pas des mesures de dispersion ni une fiche acoustique calculée. Les repères d'assemblage ne définissent pas encore un moteur d'array articulé. Les logements de superposition restent des éléments de géométrie à ajuster avec les futurs assemblages. Ce lot n'effectue aucune modification du code Minecraft ; le placement, les collisions et la synchronisation en jeu devront être vérifiés lors de l'intégration.

## Reconstruction

Exécuter generate_textures.py avec Python et Pillow/NumPy, puis build_original_audio.py dans Blender. validate_render.py nettoie et contrôle les maillages, exporte les GLB et calcule les vues. gallery.py crée la scène comparative et ses rendus. check_exports.py réouvre les natifs, règle l'alpha des GLB et les réimporte pour contrôle. Les scripts lancés depuis ce lot traitent cette famille par défaut. Les dossiers et fichiers déjà livrés permettent l'édition directe sans reconstruction.
