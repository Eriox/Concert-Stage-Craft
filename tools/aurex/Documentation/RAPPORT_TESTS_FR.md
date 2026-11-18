# Rapport de tests — CSC AUREX 1.1.0-aurex.1

6 novembre 2026. Base : archive 1.1.0 transmise par l'utilisateur, récupérée et vérifiée par CRC complet. Blender 4.3.2 ; Minecraft 1.20.1 ; Forge 47.2.0 ; JDK 17.0.21 ; Gradle 8.8. Le profil utilisateur Forge 47.4.28 reste à essayer sur son poste.

## Réalisé

Trois modèles d'enceintes originaux, un cadre pour sub suspendu, un support de suspension M12 et un collier pour tube de truss ont été construits dans Blender. Les fichiers natifs possèdent HIGH_DETAIL et GAME_READY, des matériaux, des UV et des points d'attache. Les objets restent éditables. Une scène de démonstration assemble les équipements. Aucune nouvelle géométrie n'a été produite dans Blockbench.

Les enceintes et deux supports sont enregistrés dans le module audio existant, avec le renderer de maillage CSC, ses caches GPU et ses niveaux de détail. La texture AUREX est indépendante. Les autres gammes restent présentes avec leurs identifiants, modèles et atlas antérieurs. Les règles limitent le M12 et le wedge à une enceinte individuelle ; le S218-F n'est accepté en suspension que sur son cadre dédié. Le collier est un livrable Blender/GLB, pas un item autonome ajouté au jeu.

Trois bus de monitoring ont été ajoutés à la suite des six groupes historiques. Le mixeur PCM supporte désormais un nombre de groupes paramétrable ; le constructeur historique à six groupes reste disponible. Le flux du jeu utilise neuf groupes. La banque de retours de la console expose ses faders/mutes ; les données sont persistées par le mécanisme CSC existant. Le wedge dispose de deux positions de sol et d'un axe audio suivant la pente de son baffle.

## Tests exécutés

| Contrôle | Résultat |
|---|---|
| Archive d'entrée | CRC complet réussi |
| Blender natif | Six modèles ouverts ; unité métrique, géométrie finie, UV et normales contrôlées, comptage des triangles évalués |
| Scène de démonstration | Fichier réouvert ; aucun lien de bibliothèque externe restant ; images utilisées intégrées |
| GLB | Six fichiers importés réellement dans Blender, en plus de la vérification de l'en-tête glTF 2 |
| Maillages Minecraft | 18 tableaux LOD vérifiés : longueurs de triangles, UV bornées, valeurs finies, normales unitaires |
| Rendus | 15 vues d'enceintes : face, arrière, côté, dessus et perspective ; démonstration et planche d'aperçu |
| Forge | Compilation, réobfuscation et assemblage réussis ; seconde compilation finale réussie après localisation des libellés |
| GameTests | **23 tests requis exécutés et réussis**, incluant cinq scénarios AUREX |
| M12 | Dimensions logiques identiques au LX8 ; placement orienté, sauvegarde, refus de raccordement en line array et aux anciens modèles |
| Sub suspendu | Deux S218-F sur leur cadre, continuité de l'assemblage, collisions, refus d'enceintes incompatibles et sauvegarde/relocalisation NBT |
| Wedge | Deux positions, correction du pivot de sol, azimut, direction acoustique montante, refus de stack et persistance du circuit/angle |
| Console | Migration depuis six faders, valeurs et mute des trois bus supplémentaires, délais et sauvegarde |
| Palan réel | Descente de deux blocs puis remontée du S218-F avec cadre ; données conservées, cellules de collision reconstruites à destination et absence de tête d'enceinte fantôme à l'origine |
| Audio hors jeu | Suite PCM/MP3/WAV, seek, mixage, limiteur, répétition, EQ, délais, distance, continuité et réverbération passée ; nouveau test du neuvième bus : PCM effectivement produit, extinction après mute, nombre de bus invalide rejeté |
| Compatibilité des ressources | Comparaison avec le ZIP d'entrée : anciens maillages et atlas d'enceintes inchangés |

Les 23 GameTests ont été exécutés sur le code fonctionnel livré ; la compilation finale ne change ensuite que les libellés FR/EN de sélection de banque. Journaux dans Tests. La validation des maillages ne certifie pas un rendu Minecraft identique à Blender.

## Optimisation

| Maillage | HIGH_DETAIL | Jeu proche | Jeu intermédiaire | Distant |
|---|---:|---:|---:|---:|
| M12 | 183 694 | 4 534 | 3 806 | 332 |
| S218-F | 367 132 | 6 532 | 5 228 | 332 |
| M15-W | 110 930 | 4 154 | 3 426 | 428 |
| Flying Frame S218-F | 4 212 | 1 348 | 892 | 220 |
| Suspension M12 | 2 532 | 820 | 540 | 220 |
| Collier | 400 | 208 | 124 | 96 |

Nombres de triangles, pas de cubes. HIGH_DETAIL reste destiné à Blender. Les petits détails sont supprimés des LOD et les silhouettes lointaines restent propres aux équipements. Les caches GPU existants sont conservés. Aucun chiffre de FPS n'est déduit de ces nombres : le coût réel dépend de la scène, du nombre d'assemblages visibles, de l'éclairage et de l'ordinateur.

## Fabric et contrôles restant à réaliser

Loom 1.6.12 se résout, mais échoue dans CurrentPlatform.isUnixDomainSocketsSupported avant compilation : `java.net.SocketException: Operation not permitted`. Ce point dépend de l'environnement d'exécution. Les sources sont fournies avec Loom épinglé en 1.6.12, Fabric Loader 0.15.11 et Fabric API 0.92.2+1.20.1. **Aucun JAR Fabric AUREX validé n'est livré** ; l'ancien JAR Fabric 1.1.0 n'est pas présenté comme une nouvelle compilation.

Restent à vérifier dans un client Minecraft : rendu et inventaire, manipulation sur la scène du joueur, écoute spatiale avec ses musiques, toutes les implantations de truss, culling et FPS GPU, Shimmer et projections des éclairages tiers, puis multijoueur avec plusieurs vrais clients. Les tests serveur couvrent le transport des données et le déplacement, pas ces observations visuelles ou réseau de bout en bout. Aucun modèle de charge ni certification de levage n'est fourni.

## Reproduction

- Forge : JDK 17, puis `gradle -p forge build runGameTestServer -x downloadAssets`.
- Audio : `bash tools/audio_tests/run.sh`, avec JDK 17 et ffmpeg.
- Modèles : `python3 tools/aurex/prepare_textures.py`, puis `blender -b --python tools/aurex/build_aurex.py`.
- Validation/rendus : `blender -b --python tools/aurex/validate_render.py` ; `blender -b --python tools/aurex/check_exports.py`.
- Démonstration : `blender -b --python tools/aurex/build_demo.py`.

## Provenance

Archive source fournie : SHA-256 `b569d8605892d0bb3eb7492d5edb7c8fb58fea51075bfaa439df68d7f7be0e6e`. Référence dimensionnelle interne : SpeakerModels.java, Orvane LX8 (1,60 × 0,60 × 0,90). AUREX est une conception originale ; aucune mesure de fabricant tiers n'est revendiquée. Les paramètres de directivité et de diffusion sont des réglages du jeu.
