# CSC PROFESSIONAL LIGHTING RENDER ENGINE — révisions 16 et 16b

**Statut : rien n'a été vérifié dans Minecraft.** Cet environnement ne peut pas lancer le jeu (pas de ressources Minecraft ni de natives LWJGL). Ce qui a été vérifié ici :

- Les shaders du mod compilent, se lient et s'exécutent sur une vraie implémentation OpenGL (Mesa llvmpipe, contexte 3.3 core, sans fenêtre), avec un banc d'essai écrit pour cette révision (`tools/shader_harness`).
- Le masque optique GLSL donne les mêmes valeurs que sa version Java (écart relatif maximal 1,7e-5 sur 9 cas × 4096 points).
- Les tests hors jeu passent (optique 16/16, lumière 156/156, External 125/125, 25/25 et 144/144).
- La vérification de types du module commun ne montre aucune nouvelle erreur.

Les images de `validation/rev16_harness/` sont des sorties réelles des shaders sur une scène de test (sol, mur, tube de truss, escalier, dalle), **pas des captures du jeu**.

---

## 0. Révision 16b — corrections d'après vos captures du 9 novembre

| Capture | Ce qu'on voyait | Cause | Correction | Vérifié |
|---|---|---|---|---|
| 6 | mur de brume blanche et sol / enceintes brûlés devant la scène | la rangée de PULSESTRIKE : faisceau de brume trop fort et trop long (rev. 16) ; et surtout **chaque projection multipliait la couleur jusqu'à x4, en cascade** (8 blinders sur un même sol : jusqu'à 4⁸) | brume du blinder courte et douce (5 blocs, 9 sur un flash) ; **accumulation de la lumière** : chaque projecteur ajoute sa lumière dans une cible, un seul passage l'applique (f = 1 + 3·tanh(L / 2,5), saturation douce vers x4) | banc d'essai, images `25` à `27` |
| 2, 5 | faisceaux des wash presque invisibles depuis la salle | brume des wash trop faible | brume des wash environ x1,9 | banc d'essai `08`, `10` |
| 5 | PULSESTRIKE réduits à de petits points | cellules petites, halo limité à 32 blocs | cellules plus grandes, halo jusqu'à 64 blocs et plus fort, **face entière lumineuse** derrière les cellules | **non vérifiable au banc** (émetteurs absents) |
| 4 | sol rouge en grille : joints saturés, dalles noires | la lumière multiplie la texture ; un rouge x4 sur une dalle sombre reste sombre | la part additive des lumières colorées monte avec la saturation (dalles rouge sombre) ; la saturation douce limite les joints | banc d'essai `27` (sol uniforme rouge) |

Ce qui manquait et qui est ajouté :

- **Couleurs des cellules et des zones dans la lumière**, dans l'air et sur les surfaces (`OpticalProfile.tints` / `csc_tint`). Les anneaux du W7 et du W19 se voient dans le faisceau (image `30`). Les 7 colonnes du PULSESTRIKE se voient près de la face ; elles se mélangent avec la distance, ce qui est physique (chaque cellule éclaire tout le footprint).
  - Si toutes les cellules ont la même couleur, rien ne change et le coût est nul.
  - GLSL = Java sur Mesa (`OpticsTest`).
- **Brume qui voile le décor** en Moyenne et en Dense (`concertstage_haze`). Densité de 0,005 et 0,017 par bloc ; sombre la nuit, gris clair le jour.
- **Flashs courts** : environ 45 ms à toute cadence, au lieu d'un rapport cyclique d'au moins 22 % (110 ms à 2 Hz).
- **Réglages individuels** dans `config/concertstage-lighting.properties` (−1 = selon la qualité) :
  - `max_projections`, `max_beams_in_haze`, `max_shadow_maps` ;
  - `beam_haze_steps` (0,5 à 2) ;
  - `light_distance` (32 à 512 blocs, 160 par défaut).
- **Moins de passes** : une passe par projection au lieu de quatre, plus trois passes en tout pour l'application.

## 1. Audit du moteur existant (avant la révision 16)

| Système | Où | Constat |
|---|---|---|
| Faisceau dans l'air | `VolumeLights.volume` + `concertstage_beam_volume` (rev. 15, raymarching par pixel avec carte d'ombre) ; repli en rubans `BeamRenderer.softBeam` | Bonne base. Mais **une seule texture de motif** pour tous les wash (`beam_soft`), et le **PULSESTRIKE était exclu** (rubans seulement au flash). Avec un **prisme**, retour aux rubans et carte d'ombre désactivée. |
| Lumière sur les surfaces | `VolumeLights.project` + `concertstage_light_projector` | Approche **générique** : la position de chaque pixel est reconstruite depuis une copie du depth buffer. Tout bloc ou modèle rendu dans la passe opaque reçoit la lumière, sans liste de blocs. W7, W19 et PULSESTRIKE recevaient **la même texture douce** ; le prisme faisait trois projections. |
| Gobos | même texture dans les deux shaders | Masque **dupliqué dans deux shaders**, avec deux flous de focus différents (gigue d'un côté, 9 échantillons de l'autre). Le reste de la fonction de transmission était identique. |
| Ombres / occultations | `BeamShadows` (cartes 256², une par projecteur, budget par qualité) | Fonctionnel. **Acné d'ombre** (stries) sur les surfaces éclairées en incidence rasante (blinder, wash larges) : biais dépendant de la distance seulement. Trouvé avec le banc d'essai. |
| Sources émissives | `FixtureRenderer.glows` / `halo` | Couleur par cellule ou zone déjà gérée, flash du strobe, bloom simple. |
| Atmosphère | `LightClient.haze` | Bornée entre 0,25 et 2,5 : **aucun réglage « sans brouillard »**. |
| Performances | `Perf` (niveau automatique selon les FPS), budgets par qualité (`MAX_PROJECTORS` 16/40/72, `MAX_VOLUMES` 12/24/40, cartes d'ombre 12/24/40) | Fonctionnel. Pas de mesure GPU. |
| Identité des projecteurs | constantes éparses dans `FixtureRenderer.beam` | **Aucun modèle optique par famille** : seuls changeaient la largeur, l'intensité et la texture. C'est la limite principale visée par le cahier des charges. |
| Tests | `tools/lightshow_tests/run.sh` | Le script **ne compilait plus** depuis External Show Control (dépendance au paquet `dmx`). Corrigé. |

Conclusion : il n'y avait pas à reconstruire l'architecture des passes (reconstruction depuis la profondeur, raymarching, cartes d'ombre). Il manquait un **système optique commun par famille**, utilisé partout.

## 2. Architecture

| Système du cahier des charges | Composant |
|---|---|
| 3.1 Optical Simulation | **`light/core/OpticalProfile`** (nouveau, Java pur, testable) : famille, anneaux de lentilles, face du panneau, paramètres par image. **`shaders/include/concertstage_optics.glsl`** (nouveau) : `csc_light`, jumeau exact en GLSL. |
| 3.2 Volumetric Lighting | `concertstage_beam_volume` appelle maintenant `csc_light` ; repli en rubans si la qualité est LOW ou si le budget est atteint. |
| 3.3 Surface Illumination | `concertstage_light_projector` appelle `csc_light9` (même masque, plus le flou de focus sur le gobo) ; lumières ponctuelles CSC ou Shimmer pour la lumière diffuse. |
| 3.4 Gobo Projection | même fonction `csc_gobo` avec la même rotation dans les deux shaders, prisme à 3 facettes dans le masque. |
| 3.5 Emissive Source | `FixtureRenderer.glows` / `halo` (inchangés). |
| 3.6 Atmospheric | `LightClient.haze` : préréglages Aucun / Léger / Moyen / Dense (0 / 1 / 1,5 / 2,25). |
| 3.7 Performance Manager | `Perf` et les budgets existants ; voir la section 6 pour le coût mesuré de la révision 16. |
| 3.8 Render Compatibility | reconstruction depuis le depth buffer (géométrie réellement rendue), `GpuMesh.enabled()` (pack de shaders → éclairage CSC coupé), `ShimmerLights`. |
| État centralisé | `Engine.Output` : un seul moteur pour console, AutoShow, cues, DMX et lecture. Par image, `VolumeLights.Optics` porte le profil et l'état optique (gobo, prisme, angle, netteté), recopié tel quel dans le faisceau et la projection. |
| Diagnostic | **`client/light/LightDiagnostics`** (nouveau). |

## 3. Profils optiques (identité de chaque projecteur)

Les profils sont construits **depuis les modèles du jeu** (cellules émettrices de `lightshow/<id>.json`). Les modèles Blender validés ne sont pas modifiés.

- **VORTEX X1, Beam / Spot / Gobo / Prisme.**
  - Zoom serré : un cœur concentré (beam). Le centre est 8,8 fois plus lumineux qu'à 70 % du rayon.
  - Zoom large : un disque régulier (spot), à 95 % du centre à 60 % du rayon. Son bord se règle au focus : 1,03 net contre 0,21 flou à 90 % du rayon.
  - Le gobo module le volume et la projection avec la même fonction.
  - Le prisme 3 facettes donne trois images plus petites dans un cône élargi, avec ombres (avant : trois cônes séparés et pas d'ombre).
- **LUMORA W7, 19 lentilles.** Lentilles retrouvées dans le modèle en anneaux 1 + 6 + 12. La lumière est la somme des faisceaux de chaque lentille, à leur vraie place. Au zoom le plus serré, chaque lentille se voit comme son propre faisceau en sortie de face (ondulation ×1,96 sur l'anneau extérieur à 0,3 bloc) ; à 15 blocs, c'est un wash uniforme.
- **LUMORA W19, 37 lentilles.** Anneaux 1 + 6 + 12 + 18, face plus grande, halo plus fort. La coupe diffère de celle du W7 de 17 % en moyenne à 1 bloc, à lumière égale par lentille. Ce n'est pas un W7 plus fort.
- **PULSESTRIKE M, Strobe / Blinder.**
  - Face rectangulaire de 0,92 × 0,33 bloc, prise du modèle. Près de la face, la lumière garde le rectangle (0,84 × 0,18) et la grille des cellules RGB (contraste ×3,8). Plus loin, elle devient un flood large et rond (0,96 × 0,97).
  - Le zoom (20–70°) change réellement l'ouverture : 4,4 blocs de large à 10 blocs à 20°, contre 14,9 à 70°.
  - Il a maintenant un faisceau dans la brume : faible en blinder, fort sur chaque flash, limité à 10 ou 18 blocs devant lui.

Le terme « inspiré de » ne recouvre aucune donnée constructeur : aucune spécification de ROBE ou Chauvet n'a été utilisée.

## 4. Masque optique commun

`csc_light(q, hw, along, tanH, rotation, flou)` = distribution de la famille × gobo, puis prisme. C'est la même fonction, avec les mêmes uniforms, pour l'air et pour les surfaces. Il ne peut donc pas y avoir de décalage de forme entre le volume et la projection.

Le jumeau Java `OpticalProfile.light` / `lightAt` est comparé au GLSL exécuté sur Mesa : écart relatif maximal 1,7e-5 (`OpticsTest`). La moyenne de chaque distribution sur le faisceau reste entre 0,73 et 0,95, ce qui garde l'étalonnage d'énergie des révisions précédentes.

## 5. Autres changements

- **Ombres sur les surfaces :** le biais tient compte de la pente de la surface vue du projecteur (`concertstage_light_projector`). Les stries des wash et blinders rasants ont disparu sur le banc d'essai (images `12_*` et `13_*`).
- **Brouillard :** préréglages Aucun / Léger / Moyen / Dense sur la console (réglages client). Sans brouillard, les faisceaux disparaissent et les surfaces restent éclairées (image `18_haze_0`). La valeur par défaut reste 1 (Léger), l'étalonnage d'avant.
- **Diagnostic :** bouton « Diagnostic » de la console (écran du joueur seulement, non sauvegardé). Six vues :
  - Off ;
  - faisceaux seuls ;
  - surfaces seules ;
  - émetteurs seuls ;
  - occultations (vert = éclairé, rouge = à l'ombre ; dans l'air, seules les parties à l'ombre) ;
  - masques optiques.

  L'écran F3 affiche une ligne « CSC optics » : projecteurs allumés dessinés, faisceaux, gobos et prismes. La ligne existante montre déjà, pour les projections et les faisceaux, le nombre dessiné et le nombre demandé, ainsi que les cartes d'ombre.

## 6. Coût mesuré (révision 16, avant 16b)

La révision 16b remplace les quatre passes plein-cadre par projection (trois multiplications et un ajout) par une passe additive par projection et trois passes en tout pour l'application. Pour N projecteurs : 4N passes avant, N + 3 maintenant. Le coût par pixel de chaque passe n'a pas été remesuré après 16b.


Coût d'un dessin, à scène et uniforms identiques, en 480×300. Mesuré sur Mesa llvmpipe, un rendu **sur CPU** : ce sont des **coûts relatifs indicatifs, pas une mesure sur une carte graphique** (`HARNESS_REPEAT=20`).

| Cas | Faisceau dans l'air rev. 15 → 16 | Projection rev. 15 → 16 |
|---|---|---|
| VORTEX spot ouvert | 2,19 → 1,99 ms | 1,00 → 1,43 ms |
| VORTEX gobo, focus flou | 1,56 → 2,01 ms | 0,95 → 1,33 ms |
| W7 | 1,68 → 1,85 ms | 0,74 → 1,02 ms |
| W19 | 2,53 → 2,66 ms | 0,94 → 1,76 ms |
| PULSESTRIKE blinder | 3,71 → 4,38 ms | 0,93 → 1,25 ms |

Le surcoût se situe entre −9 % et +87 % par pixel couvert. Le plus fort est la projection du W19 (4 anneaux évalués). Les budgets par qualité et le niveau automatique sont inchangés. FPS, temps GPU, RAM et draw calls **dans le jeu : non mesurés**.

## 7. Protocole de validation dans Minecraft (à faire)

Préparation :

- Console > réglages : qualité High, puis Ultra ; brouillard Léger.
- F3 ouvert ; le bouton Diagnostic sert à isoler chaque contribution.

| # | Test | Attendu |
|---|---|---|
| 1 | VORTEX zoom 0 | faisceau fin, avec un cœur, pas une ligne laser |
| 2 | VORTEX zoom 1, puis focus 0 / 0,5 / 1 | disque régulier, bord net puis doux |
| 3 | VORTEX gobos 1–8, rotation des deux côtés, prisme | motif dans le faisceau ET au sol, même orientation ; prisme = 3 images |
| 4 | gobo sur bloc plein, escalier, dalle, mur, truss, bâche, bloc d'un autre mod | le motif suit la géométrie |
| 5 | W7, zoom min / max, cellules en mode Full | wash doux ; faisceaux de lentilles visibles en sortie de face au zoom serré |
| 6 | W19, même chose | footprint plus large et plus doux que le W7 |
| 7 | PULSESTRIKE : blinder, strobe 1–25 Hz, zoom 20–70°, effets de zones | flood large et frontal ; flashs dans la brume ; zoom visible au sol |
| 8 | Diagnostic « Occultations » avec un truss dans le faisceau | ombre rouge du tube au sol et dans l'air |
| 9 | Brouillard Aucun / Léger / Moyen / Dense | sans brouillard, pas de faisceau mais le sol reste éclairé |
| 10 | 10 / 25 / 50 / 100 / 200 projecteurs | relever FPS moyens et 1 % low (F3 ou outil de profilage), lignes CSC du F3 |
| 11 | Stage Equipment Furniture + un autre mod décoratif | leurs modèles reçoivent la projection |
| 12 | Shimmer actif | pas de lumière doublée ; ligne Shimmer du F3 |
| 13 | changement de monde, F3+T, changement de qualité, session longue | pas d'écran gris, pas de fuite (mémoire F3) |

Le cahier des charges demande des captures avant / après pour chaque test, dans les mêmes conditions.

## 8. Limites connues

- **Aucun test en jeu.** Les seuils numériques des tests portent sur les distributions, pas sur un rendu jugé à l'œil dans Minecraft.
- Matériaux translucides (vitres, eau) : la projection lit la profondeur des surfaces opaques. Une vitre ne reçoit pas le motif ; ce qui est derrière le reçoit.
- Modèles des autres mods : reçoivent la lumière s'ils écrivent dans le depth buffer avant la passe lumière CSC (rendu opaque, entités de bloc). Les passes translucides ou personnalisées dessinées après ne la reçoivent pas. Non vérifié avec un mod réel.
- Pack de shaders (Iris / Oculus) : l'éclairage CSC se coupe ; seul Shimmer est pris en charge.
- Couleur des cellules dans la lumière : par anneau pour les wash (pas lentille par lentille). Par colonne pour le PULSESTRIKE ; les deux rangées RGB d'une colonne sont mélangées.
- Cible d'accumulation en 8 bits (format du jeu) : la lumière totale est bornée à x4. Les très faibles lumières sont quantifiées par pas de 1/64.
- Pas de reprojection temporelle : gigue par pixel seulement, comme en rev. 15.
- Pas de requêtes de temps GPU ni de comptage des draw calls.
- Strobe : un flash dure environ 45 ms (au moins 3 images à 60 FPS) ; pas de canal DMX de durée.
- Les réglages individuels se font dans le fichier de configuration ; seuls la qualité, la brume, le mode auto, Shimmer et le diagnostic sont sur la console.
- Émetteurs du PULSESTRIKE (cellules plus grandes, halo, face lumineuse) : modifiés sans aucune vérification visuelle possible ici.
- `DumpOptics.java` (banc d'essai) recopie les formules de `FixtureRenderer.beam` : à garder synchronisé.

## 9. Fichiers

- **Nouveaux :**
  - `concertstage_haze.*`, `concertstage_light_composite.*` / `_add.json` (16b)
  - `light/core/OpticalProfile.java`
  - `shaders/include/concertstage_optics.glsl`
  - `client/light/LightDiagnostics.java`
  - `tools/shader_harness/` (`harness.c`, `scene.py`, `DumpOptics.java`, `optics_probe.fsh`, `cells.py`)
  - `tools/lightshow_tests/OpticsTest.java`, `run_optics.sh`
- **Modifiés :**
  - `concertstage_beam_volume.fsh` / `.json`
  - `concertstage_light_projector.fsh` / `.json`, `concertstage_light_projector_add.json`
  - `VolumeLights`, `FixtureRenderer`, `LightClient`, `LightBatch`, `LumicoreScreen`
  - `tools/lightshow_tests/run.sh`
- **Régénérer les images :**

  ```
  gcc -O2 -o /tmp/harness tools/shader_harness/harness.c -ldl
  python3 -I tools/shader_harness/scene.py . validation/rev16_harness
  ```
