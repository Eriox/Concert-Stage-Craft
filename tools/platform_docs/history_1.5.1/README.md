# Concert Stage Craft — 1.5.1, sources Forge 1.20.1

Cette mise à jour intègre les **22 modèles de plateformes fournis**, leurs textures et leur géométrie complète. Elle réorganise un seul onglet créatif et remplace les anciennes géométries d’échafaudage par des structures professionnelles compatibles avec leurs identifiants de sauvegarde.

**État de livraison : intégration source avec vérifications hors jeu. La compilation Forge n’a pas pu atteindre le compilateur Java : le dépôt Maven Forge renvoie HTTP 403 dans cet environnement. Aucun JAR 1.5.1 validé n’est fourni. L’ouverture d’un monde, le rendu réel, les collisions en jeu et le multijoueur restent à vérifier dans Minecraft.** Les contrôles isolés ne remplacent pas ces essais.

## Correctif de chargement 1.5.1

La capture reçue montre Forge 47.4.28 arrêté sur `State transition OBJECT_HOLDERS running`. Elle ne fournit ni exception ni pile de threads : la cause exacte reste à confirmer avec `logs/latest.log` de cette instance.

Un coût excessif a été identifié dans les sources 1.5.0 : les sept scènes de compatibilité construisaient toutes leurs collisions détaillées et rotations dès l’initialisation de `StageContent`. Le correctif rend ce calcul différé et met ces blocs en collision dynamique pour éviter leur précalcul par état. Les constructeurs des truss, tours et palans conservent leur comportement antérieur.

Les collisions des plateformes sont assemblées par unions équilibrées sur une grille bornée : extension maximale inférieure à 1/32 de bloc horizontalement et à 1/64 verticalement. Les hauteurs de marche nominales et les niveaux des marches restent exacts. Ces approximations concernent uniquement les collisions ; aucun sommet, UV ou texture de modèle visuel n’est modifié. Le cache évite de recalculer les cellules et orientations déjà utilisées.

Les tests isolés donnent 764 assertions sur le constructeur/cache (zéro appel au fournisseur de collision lors de la construction différée) et 39 424 assertions sur les plateformes, leur NBT et la précision bornée des collisions. Les 57 sources Java sont syntaxiquement valides. **Ce correctif n’a pas été testé dans un client Minecraft et aucun JAR 1.5.1 compilé n’est fourni.** Le blocage de dépendances Forge constaté à la session précédente empêche toujours de fournir une compilation validée depuis cet environnement.

## Utilisation

- Poser les équipements sur une cellule libre. Ils s’orientent par quarts de tour selon le joueur. L’angle continu des truss reste réservé au système de truss existant.
- Le coin de référence de chaque nouveau module s’aligne sur la grille ; le placement refuse un obstacle dans toute la structure, une entité dans la collision ou une position non autorisée. Un placement partiellement refusé revient à l’état précédent.
- Un module occupe plusieurs cellules mais correspond à un seul item. Casser une partie retire le module entier et restitue un équipement en survie. Les cellules réservées aux futurs garde-corps restent libres tant qu’aucune fixation ne les utilise.
- Avec un garde-corps en main, clic droit sur le bord voulu d’un plateau : fixation à la hauteur réelle de ce plateau (20, 40, 60, 80 ou 120 cm). Le choix du bord dépend de la position cliquée. Un garde-corps de 2 m exige un bord d’au moins 2 m ; une demi-extension de 0,5 m ne reçoit pas une traverse de 1 m sur son petit côté.
- Accroupi avec un garde-corps : pose indépendante sur la grille. Accroupi, main vide, clic droit sur une fixation : retrait avec restitution en survie. Casser une cellule du multibloc, y compris celle d’une fixation, retire le module complet ; utiliser le clic droit accroupi pour retirer uniquement une fixation.
- Poser le cadre porteur, puis cliquer dessus avec le plateau sans pieds : surface praticable à **1,20 m**. Les garde-corps se montent ensuite. Retirer le panneau retire aussi ses garde-corps ; leurs items sont restitués une fois chacun.
- Cliquer une demi-extension 2 × 0,5 m avec une seconde demi-extension remplit la cellule 2 × 1 m. Retirer ses fixations avant cette opération. Le module joint restitue deux demi-extensions lorsqu’on le casse.
- Les pieds séparés sont des composants éditables et plaçables. Leur apparence télescopique est conservée ; **il n’y a pas de réglage interactif de hauteur ni de fixation automatique des pieds séparés au panneau**. Les variantes de plateaux avec pieds intégrés et l’assemblage cadre + panneau assurent la construction fonctionnelle.
- Les estrades solo 4 × 2 m et festival 8 × 4 m se posent en un seul équipement multibloc. Les escaliers ont des marches de 20 cm et des mains courantes. Les plateformes restent au sol : elles ne sont pas ajoutées aux charges des palans/tours.

## Inventaire

Un seul onglet standard : truss et accessoires, tours, palan, plateaux, estrades, escaliers/garde-corps, supports/extensions/scènes, rideaux, outils/commandes. Les variantes aluminium/noir sont voisines. L’icône de l’onglet est le plateau standard. Les anciennes entrées techniques ne figurent plus dans le créatif et leurs anciennes recettes sont retirées.

Les 22 plateformes et 36 truss utilisent leur vraie géométrie 3D dans l’inventaire. Les 32 variantes de tours ont des modèles d’item statiques comprenant le mât entier, le chariot et les mécanismes ; leur niveau de détail est celui du rendu à distance existant, avec des chaînes sous forme de rubans croisés. Une vue plus plongeante permet de lire leur silhouette complète à 16 pixels sans étirer les proportions. Les vues GUI sont centrées et dimensionnées à partir des sommets réels ; les transformations de main, de sol et de cadre sont également définies.

`tools/platform_docs/Inventory_Models_EN.png` est une planche anglaise des 90 modèles 3D concernés, avec un échantillon à 16 pixels. C’est un **rendu hors jeu des exports**, pas une capture de l’inventaire Minecraft. L’image fournie avec les entrées était une overview de plateformes ; aucune capture de l’ancien inventaire n’était disponible pour une comparaison visuelle directe.

## Modèles et dimensions

16 unités Blockbench = 1 bloc = 1 m dans ce catalogue. La hauteur de marche désigne le plateau, pas le sommet d’un boulon. Les garde-corps dépassent de 1,10 m leur plateau ; leurs récepteurs descendent légèrement sous celui-ci.

| Équipement | Emprise nominale (m) | Hauteur (m) | Cubes d’origine | Identifiant |
| --- | --- | --- | ---: | --- |
| Plateau scénique — bas | 2 × 1 | 0.2 | 139 | `stage_deck_2x1_h020` |
| Plateau scénique — standard | 2 × 1 | 0.4 | 139 | `stage_deck_2x1_h040` |
| Plateau scénique — renforcé | 2 × 1 | 0.8 | 205 | `stage_deck_2x1_h080` |
| Plateau scénique — sans pieds | 2 × 1 | 0.104 | 83 | `stage_deck_2x1_without_legs` |
| Plateforme scénique surélevée | 2 × 1 | 1.2 | 205 | `raised_stage_platform_2x1_h120` |
| Estrade de batterie | 4 × 2 | 0.6 | 708 | `drum_riser_4x2_h060` |
| Estrade pour musicien | 2 × 1 | 0.4 | 148 | `musician_riser_2x1_h040` |
| Plateforme et cabine DJ | 2 × 2 | 0.8 | 482 | `dj_platform_2x2_h080` |
| Escalier scénique — 40 cm | 1 × 0.64 | 0.4 | 86 | `stage_stairs_h040` |
| Escalier scénique — 80 cm | 1 × 1.24 | 0.8 | 118 | `stage_stairs_h080` |
| Escalier scénique — 120 cm | 1 × 1.84 | 1.2 | 150 | `stage_stairs_h120` |
| Garde-corps — 1 m | 1 × 1 | 1,10 au-dessus du plateau | 37 | `safety_railings_1m` |
| Garde-corps — 2 m | 2 × 1 | 1,10 au-dessus du plateau | 37 | `safety_railings_2m` |
| Garde-corps d’angle | 1 × 1 | 1,10 au-dessus du plateau | 90 | `safety_railings_corner_1x1` |
| Cadre porteur de scène | 2 × 1 | 1,11 (cadre) / 1,20 avec panneau | 192 | `stage_support_structures_2x1_h111` |
| Pied amovible — 40 cm | 1 × 1 | 0.366 (composant) | 21 | `adjustable_leg_h040` |
| Pied télescopique — 80 cm | 1 × 1 | 0.766 (composant) | 28 | `adjustable_leg_h080` |
| Pied télescopique — 120 cm | 1 × 1 | 1.166 (composant) | 28 | `adjustable_leg_h120` |
| Extension de scène — 1 × 1 m | 1 × 1 | 0.4 | 131 | `stage_extension_1x1_h040` |
| Extension de scène — 2 × 0,5 m | 2 × 0.5 | 0.4 | 139 | `stage_extension_2x0p5_h040` |
| Scène solo préassemblée | 4 × 2 | 0.4 | 596 | `assembly_solo_stage_4x2_h040` |
| Scène de festival préassemblée | 8 × 4 | 1.2 | 3520 | `assembly_festival_stage_8x4_h120` |

Le panneau sans pieds a une épaisseur principale de 90 mm. Posé seul, ses récepteurs inférieurs imposent un décalage de 14 mm, donnant une surface de marche à 104 mm. Monté sur le cadre, il conserve sa position de conception. La cabine DJ a un plateau praticable à 0,80 m et un plan de travail à 1,798 m. La hauteur nominale des pieds correspond à leur nom ; la hauteur géométrique exacte du composant est indiquée ci-dessus. Les escaliers occupent aussi la hauteur de leurs mains courantes, soit environ 1,515 / 1,915 / 2,315 m au total. Les deux scènes préassemblées ne comprennent pas de garde-corps dans les fichiers fournis ; ceux-ci sont ajoutables séparément.

## Sauvegardes et remplacement des échafaudages

Les sept identifiants `scaffold_stage_*` restent enregistrés pour lire les palettes des mondes et les anciens items. Leur ancien modèle et leur texture ont été supprimés. Chaque identifiant affiche maintenant une scène dérivée des nouveaux profils, avec le même volume logique 3 × 3 × 3, une surface à 2 m, les mêmes propriétés `part`/`facing`, cellules principales et ancrages. Les variantes `_tour` conservent le passage central ; les côtés équipés de garde-corps sont conservés. Ces garde-corps de compatibilité sont ramenés à 1 m pour respecter l’enveloppe historique de trois blocs. Cela ne modifie aucun des 22 fichiers fournis.

Les anciens items restent utilisables pour éviter leur disparition, mais placent le remplacement professionnel. Le démontage restitue 9 extensions modernes 1 × 1 m, ou 8 pour une scène avec passage central, une seule fois. Ces extensions ont leur propre hauteur de 40 cm : le démontage ne recrée pas automatiquement une scène de 2 m. Les anciens `stage_riser` et `stage_stairs`, distincts des sept échafaudages, sont conservés pour les mondes, masqués du créatif et retirés des recettes.

Le code des tours, palans, capture de levage, déplacement des charges, rideaux, réseau et télécommandes est conservé. Le contrôle de comparaison à la source précédente couvre ces classes et leurs ressources hors plateformes. **Le chargement d’un vrai ancien monde n’a pas été effectué** ; garder une copie avant tout essai de cette version source compilée localement.

## Architecture et performances

- Les modèles fournis ne sont pas reconstruits : les 22 `.bbmodel` et les trois PNG sont conservés octet pour octet dans `tools/platform_source/`. Les exports OBJ sont comparés aux sommets, rotations et UV des fichiers éditables.
- Les objets de plus d’un bloc sont découpés spatialement en modèles OBJ Forge par cellule, en conservant les UV. Chaque cellule affiche uniquement sa portion de modèle ; elle ne répète pas le modèle complet.
- Le rendu des structures de base est statique, inclus dans les chunks. Aucun block entity ni ticker n’est créé pour un plateau sans fixation.
- Seules les cellules portant des fixations ont un block entity sans ticker. Leur NBT contient des références, des transformations et un identifiant partagé par fixation. Le rendu et la collision sont mis en cache et invalidés lors d’un changement ; les ressources sont relues après rechargement des packs.
- Les collisions suivent les plateaux, marches, montants et renforts avec la précision bornée décrite ci-dessus. Les petits boulons/poignées sont omis de la collision ; les petits tubes creux ont une enveloppe fermée et les diagonales sont subdivisées pour éviter de grands murs invisibles.
- Trois atlas partagés pour les plateformes. La scène festival contient 3 520 cubes d’origine, soit 21 120 faces avant découpage ; voir `static_costs.json` pour les coûts par module. **Aucune mesure de FPS ou de charge réelle n’a été réalisée.**

## Compiler

Cible : Minecraft **1.20.1**, Forge **47.2.0**, JDK **17**, Gradle **8.8**. Utiliser un JDK comportant `javac`, avec `JAVA_HOME` et le `PATH` configurés.

Windows : lancer `build.bat`. Le script télécharge Gradle, génère son wrapper, construit uniquement Forge et copie le résultat dans `jars/`. Aucune installation automatique de Java n’est lancée.

Linux/macOS avec Gradle 8.8 et JDK 17 :

```sh
gradle -p forge build
```

Résultat attendu après succès : `forge/build/libs/concertstage-forge-1.20.1-1.5.1.jar`. Aucun fichier de ce nom n’est livré comme résultat validé de cette session. Le dossier Fabric provient de la base multiloader ; les nouveaux modèles OBJ dépendent du chargeur Forge. **Cette livraison ne constitue pas une version Fabric fonctionnelle ou testée.**

Pour régénérer les ressources : Python 3 avec NumPy/Pillow, puis `python tools/generate_assets.py`. Le générateur exécute les exports existants, l’intégration des plateformes, les recettes/traductions et la normalisation des items. `tools/preview_inventory.py` régénère la planche hors jeu. La source inclut les fichiers Blockbench nécessaires.

## Vérifications

Voir `TEST_REPORT.md` et les rapports dans `tools/platform_docs/`. Les contrôles positifs sont distingués de ce qui reste à effectuer dans Forge. La documentation des systèmes de truss antérieurs reste dans `README_RIGGING.md` ; ses résultats historiques ne constituent pas une validation de ce build 1.5.1.
