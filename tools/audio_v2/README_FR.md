# Concert Stage Craft 1.1.1-preview.2 — Orvane et Relay

Version de développement construite à partir de la source 1.1.0 fournie. Cible : Minecraft Forge 1.20.1, Java 17. Compilation, tests de monde et essai client réalisés avec Forge 47.2.0.

## Installation

Remplacer l'ancien JAR Concert Stage Craft par `concertstage-forge-1.20.1-1.1.1-preview.2.jar`. Les identifiants des six enceintes Veyra/Noryx, de la console, des outils et des autres blocs sont conservés. Les anciens arrays sans cadre explicite gardent leur position initiale.

La console existante devient une CSC Relay 24/6 de 1,90 m de large, 1,04 m de profondeur nominale et 1,36 m de haut : prévoir un emplacement dégagé autour des consoles déjà installées.

## Nouvelle gamme fictive CSC Orvane

Les proportions s'inspirent de la capture fournie par l'utilisateur. Ces dimensions sont des choix de conception CSC, pas des dimensions d'un produit de fabricant réel. Aucun logo de fabricant n'est reproduit.

| Modèle | Largeur | Hauteur | Profondeur | Construction |
|---|---:|---:|---:|---|
| Orvane LX12 | 2,00 m | 0,78 m | 1,10 m | Grand module line array, trois baies de grille |
| Orvane LX8 | 1,60 m | 0,60 m | 0,90 m | Module plus compact, deux baies de grille |
| Orvane SB28 | 1,80 m | 1,20 m | 1,35 m | Subwoofer de sol, deux grilles et deux évents |

Chaque caisson possède une coque travaillée, des arêtes chanfreinées, des poignées avec de véritables logements, un panneau de connecteurs en retrait, des fixations, des protections et des interfaces de rigging. Les LX ont un profil en coin et des mécanismes arrière distincts. Les nouvelles grilles du jeu emploient une texture opaque discrète pour réduire le scintillement ; les versions détaillées Blender comportent des perforations géométriques.

**Échelle : 1 m = 1 bloc Minecraft = 16 unités Blockbench.** Les anciens caissons ne sont pas agrandis artificiellement ; les nouveaux Orvane sont réellement plus grands, avec des collisions calculées sur leurs dimensions.

## Cadres, raccordements et collisions

Six objets séparés sont ajoutés : Flying Frame et Ground Stack Frame pour Veyra, Noryx et Orvane.

1. Poser un Flying Frame sous un support solide, ou un Ground Stack Frame sur une surface supérieure.
2. Ajouter une enceinte compatible avec un clic droit sur le cadre ou sur ses cellules.
3. Ajouter d'autres modules de même famille et de dimensions compatibles.
4. Utiliser le CSC Array Tool pour orienter et courber l'ensemble.
5. Maj + clic droit avec la main vide retire le dernier module ; un cadre explicite reste en place. Casser l'ensemble restitue ses éléments selon les règles de drops du mod.

Les LX12 et LX8 ne se mélangent pas dans un même array : leurs largeurs sont différentes. Un cadre Orvane s'adapte à l'un ou à l'autre format. Les cadres portent les line arrays ; les nouveaux placements de subwoofers sont réservés au sol.

Les cadres au sol ont des traverses porteuses et des patins alignés sous les caissons. Les cadres suspendus explicites réservent 30 cm sous le point supérieur pour une suspension à deux brins, avec de vrais maillons rendus en 3D. Les raccords suivent les interfaces avant/arrière et les angles des modules.

Les formes de collision et de sélection suivent les poses calculées. Une extension ou une rotation à travers un obstacle est refusée ; le nettoyage ne supprime pas les cellules appartenant à un autre équipement. La pose d'une nouvelle console vérifie également son encombrement autour du bloc central. Les géométries ne constituent pas une certification de levage ni une simulation de charge.

## Console CSC Relay 24/6

Nouvelle coque large et inclinée, châssis inférieur, pieds, renforts, connecteurs arrière, ventilation, écrans, boutons et encodeurs. Les faders sont de véritables objets 3D et leurs tiges rejoignent les rainures.

Les 24 faders de groupe sont répartis par quatre sur les **six groupes audio existants**, avec un master séparé. Ils visualisent ces six groupes ; ce ne sont pas 24 sources audio indépendantes. Leurs positions sont lissées et suivent les réglages du mod. Les voyants de mute, VU-mètres et écrans affichent l'état réel, la piste, le temps de lecture, le master, l'EQ et les liens. Le clic droit conserve l'interface et les fonctions MP3/WAV, playlist, import, groupes, mute, EQ et liaison.

Un survol de la liste des arrays affiche le modèle, les dimensions, le support, le nombre de modules, les angles et la distance.

## Son plus fort et continuité

- Gain de sortie par défaut **×2,8, soit environ +8,9 dB avant limitation**. Le fichier `config/concertstage-audio.properties` accepte `output_gain` de 1 à 8. Les volumes généraux Minecraft et le réglage de volume du mod restent applicables.
- Distance de référence portée à 12 m ; les courbes de distance/directivité restent progressives. Ce modèle est réglé pour le jeu, sans prétendre reproduire des mesures acoustiques réelles.
- Limiteur stéréo lié avec anticipation de 5 ms, attaque de 1 ms et relâchement de 150 ms. Les crêtes restent bornées ; le gain effectif d'un morceau déjà fort dépend donc de la limitation.
- Un décodeur et un flux stéréo par console : tous ses arrays traitent les mêmes échantillons. Ils ne relancent pas chacun une copie de la musique.
- Gain lissé par échantillon, panoramique relatif à la caméra, séparation mains/subs, EQ, normalisation des contributions cohérentes et petites portions de sortie conservés. La normalisation dépend de la couverture spatiale, pas du master ni des faders : elle ne compense donc pas artificiellement une baisse de volume.
- L'état d'une console peut être transmis depuis un array proche même lorsque le chunk de la console n'est pas affiché par le client. Des tickets temporaires maintiennent son fonctionnement serveur ; ils expirent après 100 ticks sans rafraîchissement. Le suivi est borné à huit consoles par joueur et les demandes de snapshots sont limitées.
- `max_streams` borne les consoles sonores simultanées, quatre par défaut, jusqu'à six ; il ne limite plus les arrays d'une même console à quatre.

## Array Tool

Aperçu local réactif, poses mises en cache, sélection d'un module dans la vue 3D avec contour orange, caméra/zoom et défilement interpolés, boutons ±0,1° et saisie du yaw, tilt et splay au centième de degré. Les limites propres aux modèles et le verrouillage du premier splay sont conservés. Un seul envoi réseau a lieu à l'application ; annuler ne modifie pas le monde.

## Fichiers Blender et exports

`tools/audio_v2/Models/` contient 13 fichiers `.blend` et leurs exports `.glb` : trois caissons, six cadres, une console et trois accessoires de rendu. Chaque fichier contient les collections `HIGH_DETAIL` et `GAME_READY`, des UV, des matériaux et des origines cohérentes. Les textures utiles sont intégrées aux fichiers Blender et également fournies dans `Textures/`.

Dans la console Blender, les propriétés de scène `CSC_Group_0` à `CSC_Group_5` et `CSC_Master` commandent les drivers des faders. Les points d'attache sont des empties nommés `CSC_Attach_*`. Z est la hauteur dans Blender ; la conversion vers le jeu est documentée dans le générateur.

`CSC_Orvane_Installation_Demo.blend` est une démonstration éditable à partir des géométries de jeu : suspension, array courbé, stack au sol, caissons et console. Le morceau de truss de cette présentation est un support de démonstration non marqué. Les six anciennes sources Veyra/Noryx restent dans `tools/speaker_source/hd/`.

## Vérifications effectivement réalisées

- **Compilation et réobfuscation Forge : réussies.** Intégrité ZIP/CRC du JAR, classes et assets nécessaires vérifiés. Le client de test développeur est exclu du JAR publié.
- **Suites audio hors jeu : réussies.** MP3 mono/stéréo/VBR, WAV 16/24 bits, recherches dans le PCM, filtres, routage, lissage, indépendance vis-à-vis du découpage des callbacks et contrôle des valeurs non finies.
- **Test du gain/limiteur : réussi.** RMS du signal faible après gain : 1 385 unités PCM pour le cas de test ; crête du cas fortement amplifié : 31 697, inférieure à la borne 32 112.
- **Audit des neuf caissons : réussi.** Dimensions du registre confrontées aux sommets des deux LODs, coordonnées finies, normales unitaires, UV et atlas valides.
- **Blender : 13 fichiers rouverts et vérifiés, 13 GLB réimportés.** Dimensions, collections, UV, géométries et textures contrôlés ; rendus de face/perspective et démonstration produits.
- **8 GameTests dans un vrai monde Forge : réussis.** Raccordement des ports après orientation, compatibilité, cadre au sol, sauvegarde/rechargement, extension bloquée par un obstacle, propriété des cellules voisines, placement des six cadres par leurs objets, codec de console et fonctionnement d'une console éloignée. Le huitième test vérifie le refus d'une rotation bloquée ; certains tests couvrent plusieurs de ces cas.
- **Client Minecraft lancé : test intégré réussi.** Rendu du monde et des modèles, ouverture/édition de l'Array Tool, console, lecture WAV dans le moteur sonore, signal PCM non nul, console absente des chunks du client, même décodeur après déplacement, baisse effective du signal après réglage du master, pause/reprise distante. Le passage du master de 0,95 à 0,25 a fait passer la crête mesurée du flux de 0,148 à 0,040 dans ce scénario. Captures réelles fournies dans `Minecraft_Previews/`.

Le test sonore du client utilise le périphérique virtuel OpenAL **No Output** : il vérifie le traitement audio et l'activité du canal, pas une écoute humaine sur un casque.

### À vérifier encore sur l'installation de jeu

Écoute subjective du volume et du timbre ; import manuel et lecture de longues playlists réelles ; multijoueur avec plusieurs machines et latence ; très grands assemblages ; transport des équipements par les tours/palans dans différentes configurations ; compatibilité avec les autres mods et rendu de tous les blocs historiques. Le chargement de la base signale des références de modèles historiques manquantes hors de cette gamme audio ; ce travail n'est pas une validation exhaustive de tout le catalogue du mod. Fabric n'a pas été compilé/testé.

## Recompiler et reproduire les tests

Java 17 requis. Depuis `forge/` : `./gradlew build`, ou `gradlew.bat build` sous Windows. Le wrapper Gradle 8.8 est fourni et sa distribution est vérifiée par SHA-256.

- Monde de test : `./gradlew runGameTestServer`.
- Audio hors jeu : `sh tools/audio_tests/run.sh` depuis la racine (JDK 17 et ffmpeg).
- Audit des géométries : `python tools/audio_tests/check_speaker_assets.py`.
- Modèles : `python tools/audio_v2/prepare_atlas.py` (Pillow), puis Blender 4.5.3 en arrière-plan avec `tools/audio_v2/build_models.py`.
- Rendus et contrôle natif : `tools/audio_v2/validate_and_render.py` dans Blender.
- Client développeur automatisé : `python tools/audio_v2/run_smoke_test.py`, après le GameTest serveur. Il utilise un monde jetable, une piste de test générée et une sortie sonore virtuelle ; ses sources ne sont activées qu'avec `-PcscSmoke`.

Référence de l'outillage de test : [documentation officielle Forge Game Tests](https://docs.minecraftforge.net/en/1.20.x/misc/gametest/). Les décisions de proportions sont propres au projet CSC et inspirées de la capture de référence fournie.
