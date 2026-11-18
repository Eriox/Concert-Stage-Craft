# Documentation technique — système lumière 1.2.0

## Découpage
| Couche | Paquet / fichier | Rôle |
|---|---|---|
| Profils, attributs | `light/core/FixtureProfile`, `Attr` | Capacités de chaque modèle (seules celles-ci existent dans l'interface et le moteur) |
| Show | `light/core/Show` | Patch, groupes, presets, couleurs enregistrées, listes de cues, effets, timeline, réglages AutoShow ; format binaire versionné (magic + `FORMAT`, migration à la lecture) |
| État vivant | `light/core/Live` | Programmer, playbacks, masters, sécurités (temps = ms de l'horloge de jeu) |
| Commandes | `light/core/Commands` | Toutes les modifications : patch, programmer, presets, cues, playback, masters, sécurités, timeline, AutoShow. Validées (bornes, droits) puis appliquées à l'identique sur le serveur et les clients |
| Moteur | `light/core/Engine`, `Effects`, `Movement` | Fonction pure (show, état, horloge, musique) → valeurs de chaque projecteur ; effets calculés une fois par effet puis décalés par projecteur ; visées calculées depuis la géométrie réelle |
| Analyse | `light/analysis/Analyzer`, `Analysis` | Décodage unique, mono 11 025 Hz, FFT 1024 / pas 256, bandes, flux spectral, chroma ; tempo, temps, mesures, sections, motifs, rôles, confiance |
| AutoShow | `light/analysis/AutoShow`, `LiveAutoShow`, `Palettes` | Pré-analyse → liste de cues + timeline ; direct → looks préparés + stabilisation |
| Serveur | `light/LightServer`, `LumicoreBlockEntity`, `FixtureBlockEntity` | Droits, application des commandes, historique, bibliothèque, analyses en tâche de fond, patch, levage |
| Réseau | `light/net/LightNet` (+ `forge/LightPacket`, Fabric) | Canal `concertstage:light`, octets bruts |
| Client | `client/light/LightClient`, `FixtureRenderer`, `FixtureMeshes`, `LumicoreRenderer`, `LumicoreScreen` | Copie locale du show, calcul de la lumière, rendu, écran |

Le moteur, les commandes, l'analyse et l'AutoShow n'importent rien de Minecraft : ils sont compilés et testés hors jeu
(`tools/lightshow_tests/run.sh`).

## Synchronisation
- Horloge commune : `heure de jeu × 50 ms`. La position musicale vient de `AudioConsoleBlockEntity.positionMs(gameTime)`, déjà
  utilisée par le son 1.1.0. La sortie du moteur en dépend seulement : aucun chronomètre par projecteur ni par enceinte.
- Une commande du client (`C_CMD`) est validée par le serveur, appliquée, horodatée (heure serveur) et renvoyée avec un numéro de
  révision (`S_CMD`). Chaque client applique les mêmes octets ; un écart de révision déclenche une demande d'état complet (`C_SYNC` →
  `S_FULL`, show + état compressés).
- Les actions réservées au serveur (`C_ACTION`) : analyse AutoShow, AutoShow direct, bibliothèque, annuler / rétablir, auto-patch,
  droits, import en morceaux de 28 ko, lien audio, sauvegarde automatique, positions standard, nettoyage du patch.
- Délai lumière par joueur (`light_delay_ms`, défaut 120 ms) : décale seulement le calcul local, pour compenser la mémoire tampon du son.

## Priorités
Valeurs de repos < listes de cues triées par priorité (AutoShow en bas) < effets des cues < accents de timeline < programmer < effets du
programmer < Grand Master, Blackout, sécurités. Intensité HTP entre playbacks (multipliée par leurs faders), autres paramètres LTP.

## Rendu et performances
- Modèles : `assets/concertstage/lightshow/<id>.json` (positions quantifiées, couleurs par cellule de palette, zones émissives
  numérotées, pivots, axe du faisceau, LOD proche / lointain / bas). Les corps sont gardés dans la carte graphique (`GpuMesh`) par LOD
  et lumière ; la lyre et la tête sont dessinées avec leur matrice courante.
- Faisceau (révision 2, `BeamRenderer`) : cône maillé additif (`RenderType.eyes`) dessiné des deux faces, luminosité par sommet =
  atténuation le long du faisceau × (0,03 + 0,97 × orientation³) où l'orientation est |normale radiale · direction de vue| ; longueur
  coupée au premier bloc (lancer de rayon toutes les 6 / 12 / 24 images), nappe douce + tache (gobo) sur le bloc touché, prisme = trois
  faisceaux, halo dans l'axe. Échelle d'affichage par modèle : `FixtureProfile.renderScale`.
- Shimmer (`ShimmerLights`) : `LightManager.INSTANCE.addLight(Vector3f, int argb, float rayon)`, `ColorPointLight.update()` / `remove()`,
  trouvés par réflexion ; une lumière par faisceau au point d'impact, alpha = intensité ; nettoyage des lumières non vues depuis
  20 images ; désactivation automatique à la première erreur.
- Budget : 24 / 64 / 160 faisceaux par image. Mode automatique : moyenne des images > 22 ms pendant 2 s → un niveau de moins ;
  < 14 ms pendant 5 s → un niveau de plus. Seul le dessin change, jamais les valeurs du show.
- Réseau : quelques octets par commande ; aucun envoi périodique d'état de projecteur.

## Persistance
- Console : show courant + état vivant + bibliothèque (16 shows) + accès + historique (25) dans l'entité de bloc.
- Fichiers `.cscshow` : même format binaire que le stockage, lus avec contrôle de toutes les tailles (un fichier importé est une donnée
  non fiable).
- Format versionné (`Show.FORMAT = 1`) : un format plus récent est refusé proprement, un plus ancien est migré à la lecture.

## Export des modèles
`tools/lightshow/export_fixtures.py <dossier des .blend> <assets/concertstage>` : lecteur `.blend` en Python pur (pas de Blender),
transformations parent / parentinv / Euler / quaternion, séparation par pivot, conversion des axes, triangulation, couleurs Principled
(linéaire → sRVB), zones émissives (cellules, LED RGB, segments blancs, source), LOD par regroupement de sommets. Les modificateurs de
biseau ne sont pas appliqués (pas de Blender) ; la géométrie n'est jamais modifiée. `make_lumicore.py` génère la console (modèle
original), `make_light_assets.py` les états de bloc / modèles / recettes / butins, `light_lang.py` les textes FR / EN.

## Limites connues
- Non compilé avec Minecraft ni testé en jeu dans cet environnement (voir `TEST_REPORT_1.2.0.md`).
- Les textes des écrans 3D de la console et les messages d'état de l'analyse restent en anglais (style console).
- L'analyse est une estimation : les sections et rôles portent un indice de confiance.
- Pas de sortie DMX réelle : univers et adresses sont logiques (affichage, organisation).
