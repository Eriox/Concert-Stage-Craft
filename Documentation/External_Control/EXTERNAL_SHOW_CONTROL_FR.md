# Concert Stage Craft 1.2.0 — CSC External Show Control

> **État : rien n'a été testé dans Minecraft, ni avec grandMA3, grandMA2, dot2 ou QLC+.** Ce document dit ce qui est
> implémenté et vérifié hors du jeu, et ce qu'il reste à vérifier. Aucune compatibilité n'est annoncée sans test.

## 1. Ce qui est livré

| Fonction | État | Vérification |
|---|---|---|
| Modes DMX Full / Compact / Ultra Compact des 4 projecteurs, tableaux uniques (`DmxMode`) | Implémenté | Tests : empreintes, noms, valeurs |
| Zoom du PULSESTRIKE M (20–70°, repos à 70°) | Implémenté | Tests ; rendu non vérifié en jeu |
| Réception Art-Net (UDP 6454) et sACN E1.31 (UDP 5568, multicast par univers) | Implémenté | Tests : décodage, validation, socket UDP local |
| Fusion des sources : priorité, HTP à priorité égale, perte du signal (3 s, maintien) | Implémenté | Tests |
| Modes de contrôle INTERNAL / AUTOSHOW / EXTERNAL / PLAYBACK, fondu de 1 s | Implémenté | Tests moteur |
| AUTO PATCH — 1 UNIVERSE, partage d'adresses, transformations locales | Implémenté | Tests |
| CSC VIRTUAL MATRIX (16 canaux, déterministe) | Implémenté | Tests (deux moteurs, même résultat) |
| CSC RECORD EXTERNAL SHOW, format `.cscrec`, CSC SHOW PLAYER | Implémenté | Tests : aller-retour, seek, boucle, vitesse, fichier abîmé |
| Synchronisation : musique de la console audio CSC, MIDI Timecode, OSC | Implémenté | Décodeurs testés ; périphériques non testés |
| Export GDTF 1.2 (4 fichiers, géométries, roues, 3 modes, modèles glTF) | Généré | XML vérifié (références internes) ; **non importé dans grandMA3** |
| Export MVR 1.6 de l'implantation (positions, orientations, adresses, modes) | Implémenté | Tests : matrices, adresses, réimport ; **non importé dans grandMA3** |
| Modèles 3D : glTF (hiérarchie Base > Yoke > Head > Beam), OBJ, 3DS MA 3D / dot2 3D | Générés | Hiérarchie vérifiée par un rendu de contrôle ; **non importés dans MA 3D / dot2 3D** |
| QLC+ : définitions `.qxf` et espace de travail `.qxw` avec le patch | Générés | XML bien formé ; **non chargés dans QLC+** |
| Import du patch (CSV CSC) et d'un MVR (par Fixture ID) | Implémenté | Tests aller-retour |
| Interface « External » de la console | Implémenté | Compilée ; non vue en jeu |
| Import natif des fichiers de show grandMA3 / grandMA2 / dot2 | **Non pris en charge** | Formats propriétaires non documentés : voir §8 |
| Déclenchements par notes MIDI | **Non pris en charge** | Seul le MIDI Timecode est lu |
| Moniteur canal par canal | **Partiel** | État par univers seulement (sources, priorité, perte) |

## 2. Modes de contrôle et priorités

Un seul moteur pilote les projecteurs à la fois (`Live.control`) :

- **INTERNAL** : la console (listes de cues, programmer, timeline) ; l'AutoShow est coupé.
- **AUTOSHOW** : la console, avec l'AutoShow autorisé (le comportement d'avant).
- **EXTERNAL** : le DMX reçu en Art-Net / sACN.
- **PLAYBACK** : un show enregistré.

Règles :

- Passer d'un moteur à un autre fait un **fondu de 1 s** depuis les valeurs de l'ancien moteur. Gobo et prisme basculent à mi-fondu.
- Entre EXTERNAL et PLAYBACK, il n'y a pas de fondu : les deux sont du DMX.
- Les **sécurités s'appliquent à tous les moteurs** : grand master, BLACKOUT, NO STROBE, plafond de strobe, MOVE LIMIT.
- En EXTERNAL et PLAYBACK, le programmer, les cues et l'AutoShow ne modifient rien. Une fonction absente du mode DMX d'un projecteur reste à sa valeur de repos.

## 3. Modes DMX

Les tableaux complets sont dans `DMX_CHARTS.md`, générés depuis le code.

| Projecteur | Full | Compact | Ultra Compact |
|---|---:|---:|---:|
| VORTEX X1 | 18 | 8 | 4 |
| LUMORA W7 | 70 (19 cellules RGB) | 7 | 4 |
| LUMORA W19 | 124 (37 cellules RGB) | 7 | 4 |
| PULSESTRIKE M | 82 (14 zones RGB + 28 segments blancs) | 8 | 4 |

Les 13 autres projecteurs : voir `DMX_CHARTS.md`. Depuis la rév. 17, **le Compact de chaque projecteur tient en 8 canaux au plus** :
64 projecteurs (73 pour les washes) tiennent dans un seul univers.

- **Full** : toutes les fonctions. Pan, tilt et dimmer sont en 16 bits (zoom aussi sur le VORTEX). Chaque cellule ou zone a ses canaux.
  - Une cellule affiche la valeur la plus haute entre la couleur générale et ses propres canaux.
  - Pour dessiner des pixels, mettez la couleur générale à 0.
- **Compact** (rév. 17, 8 canaux au plus) : le projecteur dans son ensemble, sans contrôle par cellule.
  - Un projecteur dont l'ancien Compact tenait déjà en 8 canaux le garde tel quel (blinders, PIXELTUBE, FOLLOWSPOT, LASERCORE,
    FLOODLIGHT) ; le PIXELBAR B16 perd seulement son tilt fin.
  - Les autres sont regroupés : pan et tilt en 8 bits ; couleur par **teinte** (0–255 = 0–360°) et **saturation** (0 = blanc ; sur
    un wash RGBW le blanc non saturé vient de ses LED blanches, et la fin du canal, 200–255, donne le blanc chaud CTO) ; le
    PULSESTRIKE et le FLASHCORE gardent leurs canaux White et CTO pour la barre blanche.
  - Le canal **strobe** porte aussi l'iris et/ou le focus par plages (0–7 ouvert, 8–127 strobe, puis iris, puis focus) : un seul à
    la fois.
  - Un canal **gobo** contient **tous les gobos de toutes les roues** (PHANTOM : roue 1 et roue 2), chacun avec une plage fixe et
    deux plages de rotation (un sens, l'autre sens, de lent à rapide).
  - Le prisme reste hors du Compact (comme avant). Chaque tableau liste ce que le mode n'atteint pas.
- **Ultra Compact** : dimmer, position et une **macro de 17 couleurs fixes**.
  - Pas de mélange RGB libre, pas de zoom ni de gobo.
  - Le PULSESTRIKE garde son canal strobe.
  - La liste de ce qui est inaccessible figure dans chaque tableau.
- **Virtual Matrix** : le projecteur n'a aucun canal propre et suit la matrice.

## 4. CSC SINGLE UNIVERSE MODE

**AUTO PATCH — 1 UNIVERSE** (onglet External), dans l'ordre :

1. Les projecteurs partent du mode préféré.
2. Tant que le total dépasse 512 canaux, un type de projecteur descend d'un mode (Full → Compact → Ultra Compact). Rév. 17 : **tous
   les types passent en Compact avant qu'un seul ne passe en Ultra Compact** (avant, le type le plus nombreux — souvent les VORTEX —
   partait en Ultra Compact en premier) ; une fois que tout tient, les types descendus trop bas remontent si la place le permet.
3. Si le **partage** est demandé, il est appliqué d'abord :
   - **par type** : tous les projecteurs d'un type ont la même adresse et les mêmes valeurs ;
   - **par groupe** : les projecteurs d'un même groupe et d'un même type partagent une adresse.
4. Le résultat indique les canaux utilisés et les modes choisis. Un dépassement est signalé, et le reste passe sur l'univers suivant, sans chevauchement.

Exemple testé : 10 VORTEX, 12 W7, 8 W19 et 10 PULSESTRIKE en individuel donnent 400 / 512 canaux : les VORTEX restent en Full, les
autres passent en Compact. 22 VORTEX, 12 W7, 10 W19 et 10 PULSESTRIKE (54 projecteurs) : 410 / 512, tous en Compact. 64 VORTEX en
Compact : 512 / 512.

**Boutons Full / Compact / Ultra / Matrix** (rév. 17) : le changement de mode s'applique à toute la sélection d'un coup ; un
projecteur qui ne tient plus à son adresse passe à la prochaine adresse libre (avant, le changement était refusé en fin d'univers et
l'auto patch remettait ensuite l'ancien mode).

Partagé par type, n'importe quel nombre de projecteurs tient en 294 canaux, tous en Full.

**Transformations locales** (Inverser pan, Inverser tilt, Miroir côté droit, décalages en degrés) : elles sont appliquées après le DMX. Ce sont les **mêmes** valeurs adaptées à la place de chaque projecteur, **pas** du contrôle individuel en plus.

**Conflits** : deux projecteurs différents sur les mêmes canaux sont signalés. Un partage voulu (même type, même mode, même adresse) n'est pas un conflit.

> Patcher sur un univers ne garantit pas que le logiciel externe puisse l'émettre. Selon MA Lighting, dot2 onPC émet 512 canaux sur le
> premier univers en Art-Net ou sACN sans matériel MA, et il faut un Node4 pour davantage. CSC ne contourne aucune licence.

## 5. CSC VIRTUAL MATRIX

16 canaux, à l'adresse réglable dans l'onglet External (497 par défaut, univers 0 pour un nouveau show) :

| Canal | Fonction | Valeurs |
|---:|---|---|
| 1 | Intensité générale | 0–100 % |
| 2–4 | Couleur A (R, V, B) | |
| 5–7 | Couleur B (R, V, B) | |
| 8 | Vitesse | 0 = arrêt, 255 = 4 cycles/s (progression quadratique) |
| 9 | Mouvement | 0–31 aucun · 32–63 cercle · 64–95 balayage pan · 96–127 balayage tilt · 128–159 huit · 160–191 éventail · 192–223 positions aléatoires · 224–255 ballyhoo |
| 10 | Taille du mouvement | jusqu'à ±45° en pan, ±35° en tilt |
| 11 | Direction | 0–63 avant · 64–127 arrière · 128–191 avant en miroir · 192–255 arrière en miroir |
| 12 | Effet | 0–31 couleur A · 32–63 alternance A/B · 64–95 chenillard A→B · 96–127 chenillard dimmer · 128–159 vague dimmer · 160–191 arc-en-ciel · 192–223 scintillement · 224–255 dégradé A→B |
| 13 | Phase | 0 = tous ensemble, 255 = un cycle complet sur la ligne |
| 14 | Strobe | comme le canal strobe des projecteurs |
| 15 | Centre tilt | 128 = neutre |
| 16 | Centre pan | 128 = neutre |

L'ordre des projecteurs sur la ligne est réglable : numéro, est-ouest, ou nord-sud.

Le calcul est une fonction pure des 16 valeurs et de l'horloge du jeu : les mêmes valeurs au même instant donnent toujours la même lumière, sur le serveur et sur chaque client.

## 6. Contrôle en temps réel

1. Dans l'onglet External, cliquez **Start receiver**, puis passez la console en **External**.
2. Le logiciel externe envoie de l'Art-Net (diffusion ou unicast vers l'ordinateur) ou du sACN.
   - **Depuis la rév. 16h** : Art-Net Port-Address N = univers CSC N. Donc Art-Net 0:0:0 = univers CSC 0, sans +1 automatique. sACN N = univers CSC N (sACN n'a pas d'univers 0).
   - La correspondance, les univers reçus, l'adresse d'écoute et le filtre de source se règlent **dans le jeu** : Settings → Network → Art-Net. Voir `ART_NET_FR.md`.
3. **Test signal** envoie 10 s d'Art-Net à 127.0.0.1 à travers le récepteur : c'est le moyen de vérifier la chaîne sans autre logiciel.

Fonctionnement technique :

- La réception tourne sur des fils séparés, jamais sur le fil du jeu.
- Un paquet mal formé est rejeté et compté.
- Le serveur envoie aux joueurs les canaux qui changent, 20 fois par seconde, et une copie complète toutes les 5 s.
- Les têtes suivent leur cible à la vitesse des moteurs, ce qui lisse les mouvements.

Fichier de réglages (`config/concertstage-external.properties`, créé à la première utilisation) :

- `artnet`, `sacn` ;
- `artnet_bind_address`, `artnet_port`, `artnet_source`, `artnet_universes`, `artnet_mapping`, `artnet_priority_policy`, `artnet_signal_loss`. Ces clés sont écrites par le bouton SAVE de la console : il n'y a rien à modifier à la main. `artnet_universe_offset` n'est plus utilisé ;
- `bind_address` (récepteur OSC ; `0.0.0.0` par défaut, `127.0.0.1` pour cet ordinateur seulement) ;
- `artnet_priority` ;
- `osc`, `osc_port` ;
- `midi_timecode_device`, `midi_timecode_offset_ms` ;
- `allow_on_dedicated_server` (faux par défaut : un serveur dédié n'ouvre pas de port sans accord).

## 7. Enregistrement et lecture

**CSC RECORD EXTERNAL SHOW**

- La console capture le DMX fusionné de **ses** univers (patch et matrice), à l'arrivée de chaque paquet, avec l'horodatage.
- Seuls les canaux qui changent sont stockés, avec une image complète de chaque univers toutes les 2 s.
- Si la musique de la console audio CSC joue au début, sa référence et sa position sont stockées.
- Le fichier est écrit dans `concertstage/recordings/<nom>.cscrec`.

Format `.cscrec`, version 1 :

- en-tête (`CSCSHOW\0`, version, taille, CRC32), puis un corps compressé ;
- métadonnées, patch au moment de l'enregistrement, réglages de la matrice, univers, images ;
- un fichier abîmé ou tronqué est refusé, jamais chargé à moitié ;
- l'extension n'est **pas** `.cscshow` : depuis 1.2.0, `.cscshow` est déjà le format des shows de la console (cues, presets).

Mesuré ici (Java, cet ordinateur) : 10 min de 4 univers avec 30 changements par image donnent un fichier de 2 Mo. Il se relit en 0,1 s, et un seek prend environ 0,1 ms.

**CSC SHOW PLAYER**

- Commandes : charger, lecture (passe en Playback), pause, stop, recommencer, aller à (clic sur la barre), boucle, vitesse 0,5x / 1x / 2x, supprimer.
- Si le patch actuel diffère de celui de l'enregistrement, le chargement le dit.

Synchronisation, par ordre de priorité :

1. **MIDI Timecode** (si activé et reçu) : position = timecode moins le décalage réglé.
2. **Musique** : si l'enregistrement a une référence de musique et que la console audio CSC joue ce titre, la position suit la musique. C'est la correction de dérive sur les longues séquences : l'horloge est la musique elle-même.
3. Sinon, l'horloge propre du lecteur.

**OSC** (à activer dans le fichier de réglages et par console dans l'onglet) :

- `/csc/play`, `/csc/pause`, `/csc/stop`, `/csc/restart`, `/csc/seek <s>`, `/csc/speed <x>`, `/csc/loop <0|1>` ;
- `/csc/load <nom>`, `/csc/mode <internal|autoshow|external|playback>`, `/csc/record/start [nom]`, `/csc/record/stop`.

**Ce que l'enregistrement contient** : le **résultat** des commandes du logiciel externe dans le temps, pas ses cues, presets ni effets paramétriques. C'est différent d'un import natif (§8), qui n'est pas disponible.

## 8. Import natif des shows : non pris en charge

Les fichiers de show de grandMA3, grandMA2 et dot2 sont des formats propriétaires, sans documentation publique que j'aie trouvée. Je ne les ai pas lus : aucun adaptateur n'est livré.

Ce qui est importable :

- le **patch** d'un MVR (adresse et mode, par Fixture ID), par exemple exporté par grandMA3 ;
- le **patch** CSV de CSC.

Pour rejouer un show programmé ailleurs, la méthode est l'**enregistrement DMX** (§7).

## 9. Exports et logiciels

Tous les exports sont écrits dans `concertstage/export/` (dossier du jeu en solo, dossier du serveur sinon).

- **Implantation complète** : un dossier daté contenant `patch.csv`, `patch.json`, les GDTF, les XML grandMA2 / dot2, le `.mvr`, les modèles 3D et QLC+.
- **EXPORT FIXTURE PROFILE** : bouton logiciel (grandMA3 GDTF / grandMA2 XML / dot2 XML) + Export ; l'info-bulle donne les modes et le nombre de canaux. Détails, tables et protocole de test : `DOT2_FIXTURE_PROFILES_FR.md`.
- **Coordonnées** : Minecraft (x est, y haut, z sud, 1 bloc = 1 m) vers MVR (X est, Y nord, Z haut, mm).
- Les projecteurs suspendus sont tournés de 180°. L'orientation (yaw) est celle du bloc.
- **Échelle** : celle du jeu (modèle Blender × l'échelle de rendu du mod, par exemple ×1,95 pour le VORTEX).
- Les fichiers Blender validés ne sont pas modifiés. Les modèles exportés viennent des maillages du jeu, eux-mêmes tirés de ces fichiers.

| Logiciel | Ce qui est fourni | Procédure | Testé |
|---|---|---|---|
| **grandMA3 onPC** | GDTF + MVR | Versions 1.x : copier les `.gdtf` dans `C:\ProgramData\MALightingTechnology\gma3_x.x.x\shared\lib_fixture_types\gdtf`, puis Patch > Fixture Types > Import. Versions 2.x : bibliothèque `gma3_library` (voir le manuel de votre version). MVR : Patch > Import MVR (dossier `shared/resource/lib_mvr` en 1.x, `gma3_library/mvr` en 2.x) | **Non** |
| **grandMA2 onPC + MA 3D** | XML de type MA2 (12 fichiers, 3 modes par type, généré depuis `DmxMode`) + 3DS au nommage MA (`_XAP` pan, `_XAT` tilt, `_XB` faisceau, `_XSX` corps) | Export « grandMA2 XML », copier dans `gma2/library` d'une clé, importer le type ; importer le `.3ds` dans MA 3D (File > Import > Import 3D Model). Voir `DOT2_FIXTURE_PROFILES_FR.md` | **Non** |
| **dot2 onPC + dot2 3D** | XML de type MA2 pour dot2 (12 fichiers) + 3DS au nommage MA + aperçu `.import.png` 32×32 | Export « dot2 XML », copier dans `dot2/library` d'une clé, Select Fixture Type > Import. Sortie Art-Net / sACN gratuite sur le premier univers seulement : utiliser AUTO PATCH — 1 UNIVERSE. Voir `DOT2_FIXTURE_PROFILES_FR.md` | **Non** |
| **QLC+** | `.qxf` (3 modes, cellules en têtes) + espace de travail `.qxw` avec le patch | Copier les `.qxf` dans le dossier de fixtures utilisateur de QLC+, ouvrir le `.qxw`, régler la sortie Art-Net de chaque univers | **Non** |
| Autres visualiseurs | glTF (hiérarchie), OBJ par pièce + pivots, GDTF, MVR | Selon le logiciel | **Non** |

Points à vérifier en premier dans un visualiseur :

- **Sens du tilt et position neutre.** Les géométries sont exportées comme dans le jeu : au DMX 128, le faisceau est horizontal et sort de l'avant. Un logiciel qui suppose une autre position neutre montrera un décalage constant ; il se corrige dans le profil.
- **Taille des modèles 3DS dans MA 3D / dot2 3D** (écrits en mètres).
- **Axes `_XAT` / `_XAP`.** Le sens des normales du carré d'axe définit le sens positif dans MA 3D. Il n'a pas été vérifié.

## 10. Fichiers du mod

- **Code** :
  - `light/dmx/` : DmxMode, DmxPackets, DmxMerge, DmxData, DmxDecode, VirtualMatrix, AutoPatch, CscShowFile, ShowRecorder, ShowPlayer, Timecode ;
  - `light/ExternalControl` : réception, lecture, envoi aux clients ;
  - `light/ExternalExport` : exports et imports ;
  - `client/light/ExternalStatus` et l'onglet External de `LumicoreScreen`.
- **Ressources** : `assets/concertstage/external/` (GDTF, glb, OBJ, 3DS, qxf, XML `gma2/` et `dot2/`), environ 3,3 Mo compressés dans le jar.
- **Outils** :
  - `tools/external/build.sh` : régénère tout depuis le code et les modèles ;
  - `tools/external/ma2_profiles.py` : exportateurs grandMA2 / dot2 XML ;
  - `tools/lightshow_tests/run_external.sh` : tests.

## 11. Tests et mesures

- `validation/external-tests-1.2.0.log` : 125 tests du cœur et 22 tests d'export / import, tous passés. Ils couvrent les modes, Art-Net / sACN, la fusion, le moteur, le fondu, l'auto patch, la matrice, l'enregistrement, le lecteur, le timecode, l'OSC, les formats, les commandes, le socket UDP et les temps.
- Moteur avec 100 projecteurs pilotés en DMX : environ 0,06–0,07 ms par image sur cet ordinateur (Java seul, hors rendu).
- **Non mesuré** : FPS, temps de rendu, CPU, GPU et mémoire dans Minecraft avec 10, 25, 50 et 100 projecteurs. Méthode proposée : ligne F3 « total of the mod », avec et sans signal externe.
- Les 156 tests existants de l'éclairage passent toujours. La vérification de types contre Minecraft 1.20.1 ne trouve aucune erreur nouvelle (`validation/typecheck-common-1.2.0-external.log`).
