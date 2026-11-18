# Vérifications du correctif de chargement 1.5.1

- Capture : Forge 47.4.28, Minecraft 1.20.1, étape `OBJECT_HOLDERS`. Pas de journal ou de thread dump fourni ; cause du lancement bloqué non confirmée.
- Correction d’un coût de calcul identifié : construction différée et mise en cache des collisions des scènes de compatibilité ; les autres constructeurs de rigging gardent leur chemin antérieur. Union équilibrée et grille bornée pour les collisions des plateformes/fixations. Visuels et identifiants inchangés.
- `check_startup_shapes.py` : **764 assertions** sur les corps de constructeur/cache de production avec substituts Java ; zéro appel au fournisseur de collisions dans le constructeur différé, un seul calcul par cellule, réutilisation des rotations, accès concurrents et égalité avec le chemin eager.
- `check_platform_structure.py` : **39 424 assertions** sur les vraies classes concernées et des substituts d’API ; placement/retrait/NBT/cache, enveloppe des collisions, borne de précision, nombre de plans de coordonnées et hauteurs de marche préservées.
- `check_java_syntax.py` : **57 sources** parsées par le JDK 17. Ne résout pas l’API Forge/Minecraft.
- Aucun test de lancement Minecraft ni compilation complète 1.5.1. Les données et modèles visuels ne sont pas régénérés. Le rapport 1.5.0 ci-dessous reste l’historique des contrôles effectués avant ce correctif.
- Pour confirmer le diagnostic, lire `logs/latest.log` de l’instance bloquée ; si le journal n’expose pas la cause, une pile de threads du processus encore bloqué sera nécessaire.

---

# Vérifications — sources 1.5.0

Session du 31 octobre 2026. Cible Forge 1.20.1 / 47.2.0, Java 17. Les contrôles ci-dessous décrivent leur portée réelle. Aucun monde Minecraft n’a été lancé.

| Contrôle effectué | Résultat | Portée et limites |
| --- | --- | --- |
| Blockbench natif 5.2.2 | 22/22 fichiers ouverts, modifiables et réexportables | Application web officielle chargée localement avec Chromium/WebGL logiciel. Cubes, groupes et trois textures chargés. Aucun fichier source réenregistré ou modifié. |
| Conservation des entrées | 22 modèles et 3 textures identiques aux fichiers ZIP fournis | Comparaison octet par octet ; SHA-256 dans `source_preservation.json`. |
| Ressources, géométrie et UV | 26,619 assertions positives | Géométrie de l’OBJ fourni comparée aux cubes/rotations/UV de chaque BB ; références des modèles, textures et MTL ; tables de loot, traductions, recettes distinctes ; coordonnées des collisions. |
| Surfaces de marche et escaliers | Inclus dans le contrôle des ressources | Échantillonnage de la continuité du plateau, hauteurs des marches, limites des boîtes. Ne simule pas la physique du joueur Minecraft. |
| Icônes | 90 modèles 3D sans dépassement de leur cadre projeté | 22 plateformes + 36 truss + 32 tours. Vérification mathématique des transformations et inspection de la planche hors jeu. Aucun screenshot de Minecraft. |
| Placement, retrait, transformations, NBT | 15 298 assertions positives | Exécute les vraies classes `StageStructure`, `StageSpec`, `StageMath` et `StageAttachmentBlockEntity`, avec Gson réel et classes Minecraft simulées. 22 équipements, quatre orientations, obstacle, autorisation, retour arrière après refus, retrait complet, restitution unique d’une fixation, conservation des données, rejet de données invalides, invalidation du cache. Ne compile pas `StageMounting` contre Forge et ne teste pas le vrai réseau. |
| Analyse Java 17 | 56 fichiers parsés sans erreur de syntaxe | Parseur du JDK sur `common` et `forge`. Ne résout pas les signatures de l’API Minecraft/Forge. |
| Synchronisation des charges existantes | 34 contrôles positifs | Corps des méthodes de production avec substituts Java. Pas de vrai paquet réseau. |
| Rendu des block entities déplacés | 30 contrôles positifs | Classe de production avec substituts de l’API graphique ; pas de rendu OpenGL Minecraft. |
| Calculs de levage et de chaîne | 169 630 assertions positives | Classes mathématiques de production compilées/exécutées. |
| Chargeur/rendu des truss et tours | 2 313 assertions positives | Gson et classes de production, substituts de l’API ; LOD, cache, bornes et découpage. |
| Ressources des 76 modèles de rigging et comparaison à la base | 3 459 510 assertions positives | Vérifications de maillage/UV/connecteurs et conservation des classes/réseau/commandes/recettes/blockstates hors plateformes. Les changements intentionnels des plateformes et du créatif sont exclus. Pas de chargement d’ancien monde. |
| Compilation Forge complète | **Bloquée avant `compileJava`** | Résolution de ForgeGradle impossible ; le dépôt officiel Forge renvoie HTTP 403. Aucun JAR mod 1.5.0 validé. |
| Client, serveur dédié, multijoueur, FPS et ancien monde | **Non effectués** | Nécessitent une distribution Forge compilée et un environnement Minecraft exécutable. |

## Corrections issues des contrôles

- Conservation des fixations lorsque la cellule cassée est déjà remplacée dans la palette au moment de `onRemove` : lecture du block entity d’origine avant sa suppression.
- Collision dynamique des cellules portant des fixations ; cache de l’union plateau + fixation, invalidé sur modification/chargement.
- Placement et retour arrière cohérents dans toutes les orientations ; pas de module partiellement placé après refus simulé.
- Décalage correct du garde-corps d’angle indépendant ; fixations montées placées selon la hauteur réelle du plateau.
- Recettes distinctes : les variantes ne se disputent pas une même combinaison d’ingrédients.
- Vue plongeante des tours pour conserver le mât complet dans leur icône ; prévisualisation utilisant les bons matériaux pour chaque face.

## Reproduire les contrôles

JDK 17 requis ; définir `JAVA_HOME`. Python 3, NumPy et Pillow. `GSON_JAR` désigne le JAR Gson fourni avec Gradle 8.8, ou une dépendance Gson 2.10 compatible.

```sh
python tools/check_platform_assets.py
python tools/check_platform_structure.py --gson "$GSON_JAR"
python tools/check_java_syntax.py
python tools/check_lift_sync.py
python tools/check_moving_render.py
python tools/check_rigging_math.py
python tools/check_rigging_renderer.py --gson "$GSON_JAR"
python tools/check_rigging_assets.py --baseline /chemin/vers/source-1.4.3.zip
```

Le contrôle natif Blockbench est `tools/validate_platform_blockbench.cjs`, avec Playwright, `BB_WEB_APP` pointant vers les assets web officiels de Blockbench (bundle compris) et `CHROME_BIN` vers Chromium. Le report conserve la version effectivement utilisée. L’image `Inventory_Models_EN.png` est produite par `tools/preview_inventory.py` à partir des exports ; elle ne prouve pas le chargement des modèles Forge.

La base de comparaison utilisée porte le SHA-256 `79a40b0e06fcf7a22d31d06e9b8639e9e148ea218881809a1f302acfa124f2b9`. Les rapports JSON sont dans `tools/platform_docs/`. `static_costs.json` compte les faces et cellules : ce n’est pas un benchmark de performances.

## Validation restante dans Minecraft

1. Compiler avec JDK 17 / Gradle 8.8, puis charger Forge 47.2.0 côté client et serveur dédié. Vérifier l’absence d’erreurs Java, de modèles manquants et de textures roses/noires.
2. Parcourir le créatif en français et en anglais, contrôler la présence des 22 nouveaux équipements, les icônes et tooltips et l’absence des anciennes entrées. Vérifier les recettes et restitutions en survie.
3. Poser chaque module dans les quatre orientations, contre un obstacle, près d’un joueur et près d’un autre module. Casser chaque type de partie, aussi en créatif. Tester les bordures de chunks et les limites de construction.
4. Marcher sur tous les plateaux et escaliers, longer les garde-corps, vérifier les zones sous les structures. Fixer/retirer les trois garde-corps à toutes les hauteurs, poser le panneau sur son cadre, joindre les demi-extensions et recompter les items après démontage.
5. Sauvegarder/recharger des structures avec fixations, quitter/rejoindre en multijoueur, décharger/recharger les chunks, recharger les packs. Tester le rendu et les collisions pour les observateurs tardifs.
6. Sur une copie d’un monde 1.4.3, contrôler les sept remplacements, les différentes orientations, la hauteur de 2 m, les passages de tours et les anciens items en coffre.
7. Faire monter/descendre les truss portant des équipements d’autres mods et vérifier leur rendu à plusieurs clients. Confirmer que les plateformes restent au sol. Tester les palans, rideaux, télécommandes et l’outil d’angle.
8. Mesurer FPS, temps des ticks serveur, mémoire et reconstruction des chunks avec de nombreuses scènes festival et fixations. Aucun chiffre de performances réelles n’est avancé ici.

Ces validations restent nécessaires avant de qualifier cette version de release jouable validée.
