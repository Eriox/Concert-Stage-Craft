# Concert Stage Craft — lot de travail audio / Array Tool

Base : archive source Concert Stage Craft 1.1.0 fournie par l'utilisateur.
Cible : Minecraft Forge 1.20.1, Java 17. Ce lot ne constitue pas la mise à jour complète demandée.

## Changements de ce lot

- Une seule source sonore stéréo par console remplace les sources indépendantes par array. Tous les groupes traitent les mêmes échantillons PCM ; les copies d'une même musique ne démarrent plus séparément pour chaque enceinte.
- Toutes les enceintes chargées et reliées contribuent au mixage. Leur classement par volume ne sélectionne plus les quatre arrays retenus.
- Normalisation en amplitude des contributions corrélées, panoramique relatif à la caméra, EQ et séparation main/sub conservés.
- Lissage du gain par échantillon : attaque 35 ms, relâchement 90 ms. Ces valeurs sont des constantes temporelles exponentielles, et non des durées de fondu terminant exactement à ces instants.
- Tampons de sortie limités à 40 ms. La latence réelle dépend aussi de la file OpenAL/Minecraft et du périphérique audio ; elle n'est pas mesurée ici.
- Une source déjà démarrée reste active à volume nul tant que sa console joue. Se déplacer hors de la zone audible ne recrée donc pas son lecteur. L'ouverture initiale du décodeur considère également la proximité des arrays, plutôt que celle de la console seule.
- Le réglage `max_streams` limite désormais les consoles simultanées (4 par défaut), et non le nombre d'arrays. Les flux silencieux conservés comptent dans cette limite.
- Array Tool : curseurs continus dans l'aperçu local, saisie numérique du splay, du yaw et du tilt avec précision 0,01°, boutons ±0,1°, sélection par clic sur les boîtes projetées des modules et cache des poses. Les changements réseau restent différés jusqu'à « Appliquer » ; « Annuler » ne modifie pas le monde.
- Une valeur d'angle ou de volume non finie est rejetée. Le nettoyage des cellules de collision vérifie leur propriétaire avant suppression.

## Dimensions auditées

Les maillages existants utilisent déjà 1 unité = 1 mètre = 1 bloc Minecraft. Aucun agrandissement arbitraire n'a été appliqué.

| Modèle | Largeur m | Hauteur m | Profondeur m |
|---|---:|---:|---:|
| Veyra A1 | 1,18 | 0,42 | 0,62 |
| Veyra A2 | 1,24 | 0,40 | 0,58 |
| Veyra B2 | 1,12 | 0,82 | 0,88 |
| Noryx A1 | 0,88 | 0,32 | 0,47 |
| Noryx A2 | 0,94 | 0,305 | 0,445 |
| Noryx B1 | 0,86 | 0,64 | 0,72 |

Le fichier `speaker_geometry_audit.json` contient les limites calculées sur les sommets, les nombres de triangles et le contrôle des valeurs finies des deux niveaux de détail. Cette vérification ne remplace pas une inspection visuelle en jeu.

## Tests réellement exécutés

`sh tools/audio_tests/run.sh`, Java 17 et ffmpeg :

- décodage MP3 stéréo/mono et WAV 16/24 bits, fréquences différentes ;
- recherche et comparaison des blocs de PCM sur plusieurs fichiers MP3 ;
- filtres et observations du modèle acoustique fournis par les tests existants ;
- nouveau MixerTest : routage L/R, attaque sans saut, relâchement sans saut, rejet des commandes non finies et résultats identiques lorsque le même signal est découpé en callbacks différents.

Résultat des tests hors jeu : PASS. MixerTest a aussi été compilé et exécuté contre les classes du JAR final : PASS. Journal joint : `audio-tests.log`.
Compilation Forge et réobfuscation : **BUILD SUCCESSFUL**, 7 tâches (5 exécutées, 2 à jour lors de la compilation finale). Intégrité du JAR et classes nécessaires : PASS. Détails dans `BUILD_STATUS.txt`.

Audit reproductible des six maillages : `python3 tools/audio_tests/check_speaker_assets.py` — PASS (deux LODs, dimensions, sommets finis, normales, UV et atlas).

## Vérifications restant obligatoires en jeu

Aucun lancement de client Minecraft ni test d'écoute n'a été effectué dans ce lot.

1. Charger une copie d'un monde 1.1.0 et contrôler IDs, liens et sauvegarde/rechargement.
2. Écouter MP3 et WAV, pause/reprise, changement de piste et recherche temporelle.
3. Traverser les zones d'écoute à pied, en vol et en tournant la caméra ; vérifier volume et panoramique au centre et sur les côtés.
4. Écouter un puis plusieurs arrays, une configuration mains/subs et plusieurs consoles ; vérifier la limite des flux et l'absence de saturation gênante.
5. Tester déplacement des arrays par les palans/tours et passage entre chunks, à deux joueurs.
6. Vérifier glissement des curseurs, saisie numérique, sélection en 3D, zoom, rotation, annulation et application des angles.
7. Détruire un array voisin d'une autre structure et vérifier ses cellules de collision.

## Limites et travail restant

- Le déchargement du chunk de la console ferme encore sa lecture côté client. Les changements de gain sont corrigés dans ce lot ; la continuité lorsque la console elle-même quitte les chunks chargés nécessite un travail de synchronisation supplémentaire.
- Les angles/atténuations sont un modèle de jeu, pas des mesures acoustiques réelles. Aucun délai de propagation acoustique n'est simulé.
- La sélection 3D utilise les boîtes projetées, pas une intersection exacte avec chaque triangle.
- Les quatre nouveaux supports Flying/Ground Frame, la nouvelle console 3D animée, les détails supplémentaires des enceintes, les diagnostics complets et le défilement interpolé ne sont pas réalisés dans ce lot.
- Les registres de blocs/items, le format des sauvegardes et les paquets réseau existants sont conservés. La compatibilité effective doit encore être vérifiée en jeu.
- Les sources communes Fabric ont changé, mais la compilation et le runtime Fabric ne sont pas validés.
