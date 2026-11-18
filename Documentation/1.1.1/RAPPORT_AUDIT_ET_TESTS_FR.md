# CSC 1.1.1 — audit, modifications et mesures

Date : 11 novembre 2026. Base : `CSC_1_1_0-source.zip` (1.1.0 + perf12–15 + 1.1.1-preview.2 à .9). Cible : Minecraft 1.20.1, Forge
(et Fabric), Java 17.

## 0. Ce qui n'a pas pu être fait ici, et pourquoi

| Demandé | État | Raison |
|---|---|---|
| JAR Forge 1.20.1, logs de compilation | **non produit** | Accès refusé (HTTP 403 de la politique réseau de l'environnement) à maven.minecraftforge.net, Maven Central, services.gradle.org et Mojang : ForgeGradle et Minecraft ne peuvent pas être téléchargés. |
| Benchmarks graphiques (FPS, 1 % low, temps GPU, draw calls, VRAM) | **non mesurés** | Pas de client Minecraft. Aucun chiffre FPS n'est donné. |
| Tests en jeu : spatialisation à l'écoute, multijoueur, chunks, dimensions, reconnexion | **non faits** | Idem. Les comportements sont testés hors jeu sur le modèle de calcul du jeu (section 4). |
| Benchmarks audio avant / après | **faits hors jeu** | Moteur audio en Java pur : le même code que le jeu, sur des signaux de test. |

Pour compiler chez vous (Java 17, Internet) : `cd forge && ./gradlew build` (JAR dans `forge/build/libs`), puis
`./gradlew runGameTestServer` pour les tests de monde existants. Tests hors jeu : `sh tools/audio_tests/run.sh`.

## 1. Audit — ce que fait réellement la 1.1.0

### Rendu

- **Truss droites, angles, jonctions, échelles** : blocs ordinaires dont le modèle OBJ est **cuit dans le maillage des chunks** par
  Minecraft. Aucun calcul CSC par image ; le coût est uniquement GPU (nombre de quads), plus la reconstruction d'un chunk quand on y
  pose ou casse un bloc. De 256 quads (échelle 1 m) à 1 620 (angle 6 directions) par bloc. Les modèles n'ont **ni face cachée
  entière ni doublon** (analyse de toutes les faces contre tous les volumes fermés : 0), les tubes sont déjà sans bouchons.
  Seule perte sûre trouvée : les bouchons des petits axes en éventails de triangles (le chargeur OBJ de Forge fait un quad par
  triangle). Un niveau de détail à distance est impossible pour une géométrie de chunk sans changer de méthode de rendu (ce que le
  cahier des charges réserve aux cas mesurés) : non fait.
- **Pièces dessinées par le mod** (toits, truss circulaires et inclinées, tours motorisées, éléments montés, enceintes, console,
  structure en levage) : déjà en cache GPU (`GpuMesh`, perf), reconstruites seulement quand elles changent, culling hors écran,
  distance liée à celle de Minecraft × qualité, LOD des tours, mémoïsation des enceintes. Allocations restantes dans les `render()` :
  0 à 5 par renderer, aucune dans une boucle. Rien de coûteux supplémentaire trouvé à ce niveau sans mesure en jeu.
- **Projecteurs** : **la 1.1.0 n'a pas de projecteur CSC**. Les projecteurs et leurs faisceaux sont ceux de Theatrical / Extra
  Lights, les lumières colorées celles de Shimmer. CSC intervient seulement par `LightCompat` (les rayons des faisceaux traversent le
  treillis des truss ; formes précalculées et type de contexte mis en cache depuis aurex.2). La chute de FPS « projecteurs allumés »
  ne peut donc pas être corrigée dans CSC au-delà de ce point ; elle est à mesurer avec et sans ces mods.
- Pas d'AUTO dans les qualités Faible / Moyen / Élevé : ajouté (section 2).

### Son

- **Architecture** (déjà en place) : un seul flux stéréo par console, envoyé à Minecraft comme une source relative sans atténuation
  OpenAL ; un décodeur partagé par console sur son propre thread ; tout le spatial (distance, directivité, obstacles, panoramique)
  calculé dans le PCM par groupe (9 groupes : façade G/D, subs, front fill, delay, custom, 3 retours), filtres par groupe, crossover
  Linkwitz-Riley 24 dB/oct réglable, retard par groupe, traitement (EQ 5 bandes, compresseur, polarité, retard commun), réverbération,
  limiteur. Pas de source OpenAL par enceinte, pas d'appel OpenAL hors du thread son. Horloge commune par console.
- **Défauts trouvés et mesurés** (section 4) :
  1. **Niveau** : chaque enceinte divisée par le nombre d'enceintes de la console → façade très en dessous du titre, et plus on
     ajoute d'enceintes, plus c'est faible. C'est la cause la plus probable du niveau faible de l'enregistrement signalé.
  2. **Écrêtage interne** : signal de chaque groupe arrondi et écrêté à 16 bits avant son gain.
  3. **Conversion de fréquence linéaire** (44,1 → 48 kHz, donc presque tous les titres) : aigus atténués et repliement.
  4. **Retours** : même portée qu'une enceinte principale (118 blocs).
  5. **Calcul inutile** : groupes inaudibles filtrés quand même ; logarithme + puissance par échantillon même compresseur coupé ;
     une ligne de visée d'obstacles par enceinte.

## 2. Modifications

Voir `CHANGES_1.1.1.md`. Résumé : `ArrayMix` (systèmes cohérents + somme en énergie, gain recalibré et converti), chaîne en virgule
flottante, `SincResampler`, portée des retours, groupes inactifs non calculés, compresseur sans calcul quand il est coupé, obstacles
par système, `LevelMeter` (indicateur et mesures), réglages têtes / caissons / retours, qualité de rendu AUTO, bouchons des truss en
quads.

## 3. Mesures graphiques (géométrie, hors jeu)

| Modèle | Quads avant | Après |
|---|---:|---:|
| Truss droite 2 m (h1_l2) | 584 | 552 |
| Truss x30d 1 m / 2 m | 438 | 414 |
| Angle 3 directions | 876 | 828 |
| Angle 6 directions | 1 620 | 1 524 |
| Jonction croix | 1 124 | 1 060 |
| Échelle 1 m | 256 | 240 |
| **Total des 210 modèles posés** | **149 000** | **140 440 (-5,7 %)** |

Détail : `validation/1.1.1/truss_quads.log`. AUTO : `validation/1.1.1/auto_quality.log` (descente, dwell de 20 s, aucune oscillation
avec des FPS qui alternent 35 / 85 toutes les 2 s, plafond).

## 4. Mesures audio (hors jeu, avant = code 1.1.0, après = 1.1.1)

**Test A — niveau.** Système de 24 enceintes reliées à une console (2 lignes de 6 M12, 2 piles de 3 S218-F accrochées, 4 retours
M15-W, 2 front fills), titre de synthèse à -8,1 LUFS / crêtes -0,3 dBFS (sonie vérifiée aussi avec ffmpeg ebur128 : -8,1). Sortie de
la console mesurée selon BS.1770-4. Fichiers : `validation/1.1.1/level_chain_*.txt`.

| Endroit | Avant (1.1.0, gain ×2,8) | Après (1.1.1, gain ×1) |
|---|---:|---:|
| Premier rang, 6 m | -16,8 LUFS, crête -3,8 dBFS | -7,6 LUFS, crête -0,2 |
| Façade, 25 m | -16,7 LUFS, crête -3,8 | **-8,6 LUFS**, crête -0,2 |
| 50 m | -19,1 | -10,6 |
| 100 m | -28,1 | -21,2 |
| 160 m | silence | silence (fondu progressif depuis ~115 m) |
| Côté gauche / droit | -18,1 | -10,3 |
| Derrière la scène | -20,8 | -10,1 (*) |
| Sur scène (retours) | -20,4 | -9,0 |
| Échantillons écrêtés | 0 | 0 |
| Limiteur actif (façade) | 0 % | 0,6 % des échantillons |
| 8 retours ajoutés : façade | -2,1 dB | +0,0 dB |

(*) À cet endroit on est dans l'axe des retours (tournés vers les musiciens) et les caissons sont presque omnidirectionnels ; les
têtes y sont 15 dB plus bas qu'en façade (directivité conservée).

**Test B — qualité.** Conversion 44,1 → 48 kHz, sons purs à -6 dBFS (`ResamplerTest`) :

| Fréquence | Avant : niveau / signal-reste | Après |
|---|---|---|
| 1 kHz | -6,02 dB / 62 dB | -6,00 / 84 dB |
| 10 kHz | -7,50 / 20 dB | -6,00 / 81 dB |
| 15 kHz | -9,44 / 11 dB | -6,00 / 83 dB |
| 19 kHz | -11,67 / 5 dB | -6,28 / 74 dB |

Écrêtage interne (`HeadroomTest`, 60 Hz à -1 dBFS, grave +12 dB, groupe à 25 %) : signal / reste **9,8 dB → 96 dB**.
Coût de la conversion : environ 3 à 7 ms de calcul par seconde de son, sur le thread du décodeur (pas le thread son).

**Test C — basses** (`CrossoverTest`) : à 80, 100 et 120 Hz, S218-F (passe-bas) et M12 (passe-haut) à -6,02 dB au point de coupure,
somme plate à ±0,01 dB de 30 Hz à 1 kHz, pentes de 24 dB/oct. (Inchangé en 1.1.1 : déjà correct ; désormais mesuré.)

**Test D — spatialisation** (`LevelChainTest`) : décroissance continue avec la distance ; à 10 m à gauche de la façade, canal gauche
1,69 / droit 2,25 (la ligne de droite est vue sur la droite, celle de gauche presque au centre), symétrique à droite ; derrière les
têtes -15 dB. L'écoute réelle reste à faire en jeu.

**Test E — coût** (`MixerCostTest`, une machine partagée, indicatif) : mix de la console, thread son, par seconde de son : 4 groupes
entendus (cas courant) 13,8 → **7,8 ms** ; 9 groupes 13,9 → 15,4 ms (le calcul en virgule flottante coûte un peu plus quand tout est
entendu). Somme des niveaux `ArrayMix` sur le thread du jeu : 0,3 µs (2 enceintes) à 10 µs (100 enceintes) par tick. Une source
Minecraft par console, quel que soit le nombre d'enceintes (inchangé).

**Test F — synchronisation** : non modifiée (horloge commune par console, tests existants `PlaylistPcmTest`, `SeekTest`,
`GaplessTest`, `DelayPlannerTest` : réussis). Chunks, dimensions, multijoueur, reconnexion : à tester en jeu.

Tous les tests audio : `validation/1.1.1/audio_tests_after.log` (15 suites réussies, 0 échec).

## 5. Compatibilité

- Identifiants, sauvegardes, paquets réseau : inchangés. Les modèles de truss gardent leurs noms et leur aspect.
- `config/concertstage-audio.properties` : `output_gain` 1.1.0 converti une fois (÷ 2,8) et marqué `gain_model=2` ; nouveaux
  `level_mains`, `level_monitors`, `range_factor.monitors`, `range_factor.aurex_main`.
- `config/concertstage-client.properties` : `render_quality=AUTO` accepté (les anciennes valeurs restent valides).
- Vérifications : analyse syntaxique des 221 sources Java (sans résolution des classes Minecraft), `check_rigging_assets.py`
  (2 805 339 assertions), `check_rigging_math.py` (169 630), `check_startup_shapes.py` (764). **Les classes client (écrans, ClientAudio,
  RenderQuality) n'ont pas été compilées contre Minecraft** : la première compilation chez vous est le premier contrôle de types.

## 6. Limites restantes

- Aucun FPS mesuré ; l'effet de -5,7 % de quads sur les truss est réel mais modeste. Une vraie baisse du coût GPU des grandes
  structures demanderait un rendu à niveaux de détail des truss (hors chunks), à décider après mesure.
- Le coût des projecteurs Theatrical / Extra Lights et des lumières Shimmer ne dépend pas de CSC.
- Le niveau ressenti dépend aussi des curseurs Général / Juke-box de Minecraft et du logiciel de capture ; l'indicateur de l'onglet
  Diag. donne le niveau envoyé par CSC pour faire la part des choses.
