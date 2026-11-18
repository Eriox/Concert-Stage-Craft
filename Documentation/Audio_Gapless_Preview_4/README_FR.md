# Concert Stage Craft — 1.1.1-preview.4

Deuxième lot du cahier des charges audio, basé sur la preview.3. Forge 1.20.1 / Java 17. Cette livraison concerne le flux musical continu ; le cahier des charges complet n'est pas encore terminé.

## Implémentation

- Une source sonore Minecraft par console est conservée lors des changements naturels de morceau, y compris Repeat One. Les commandes manuelles (seek, sélection, pause/reprise, stop) conservent leurs règles de réinitialisation.
- Nouveau lecteur `PlaylistPcm` : décodage asynchrone, file bornée de 150 blocs de 40 ms (au maximum environ six secondes / 1,15 Mo de données PCM par console), anticipation des fichiers suivants, conversion stéréo 48 kHz.
- Un même appel de lecture peut contenir la fin d'un morceau et le début du suivant. Aucun tampon de silence n'est inséré au passage entre deux fichiers prêts.
- Conversion linéaire des fréquences d'échantillonnage : WAV et MP3 peuvent partager le flux 48 kHz sans recréer la source OpenAL. Cette conversion est une interpolation linéaire, pas un rééchantillonneur de mastering avec filtre antirepliement avancé.
- Repeat One et All fonctionnent dans la file PCM. Une modification des modes invalide les anticipations devenues inutiles tout en conservant les échantillons restants du morceau audible.
- Le transport serveur ne change plus le numéro de session aux transitions naturelles. Les actions manuelles changent toujours ce numéro. L'indicateur de position et le lecteur des vumètres suivent le nouveau morceau.
- Les transferts réseau autorisés et le cache SHA-1 de la version précédente sont conservés. Le joueur peut toujours refuser les téléchargements.
- Fermeture des décodeurs et interruption des producteurs lors de l'arrêt. Une absence de données pendant trois secondes provoque un arrêt/réessai, pas un blocage indéfini du thread sonore ni l'insertion volontaire de silence.
- Les enceintes, modèles et identifiants de la preview.3 restent inchangés.

## Vérifications

- Tests audio Java : décodeurs MP3/WAV, recherche, mixage, limiteur et modes de répétition.
- Nouveau test `PlaylistPcmTest` : raccord exact de WAV constants de 44,1 et 48 kHz, vérification des deux canaux et de chaque échantillon, compte exact de 9600 échantillons après conversion, Repeat One sur plusieurs boucles, Repeat All, limite de la file, changement All vers One pendant la lecture sans jouer le successeur périmé.
- Playlist mixte MP3/WAV : nombre d'échantillons de sortie vérifié contre le nombre réellement décodé et les taux de conversion. Le test conserve les éventuels silences encodés et ne prouve pas le retrait du padding MP3.
- Dix GameTests Forge réussis. Le test de console distante vérifie désormais que la timeline avance sans changer de session lors d'une répétition naturelle.
- Compilation Forge réussie.
- Test client automatisé : voir `client-summary.log`. Il vérifie notamment l'identité du flux PCM, de la session et de l'objet sonore Minecraft avant/après EOF en Repeat One, ainsi qu'une sortie PCM non nulle. OpenAL utilise un périphérique de sortie nul dans cet environnement : aucune écoute humaine n'est revendiquée.

## Limites et travaux suivants

La continuité est assurée lorsque les fichiers suivants arrivent et se décodent assez tôt. Un téléchargement tardif, un fichier invalide ou un périphérique audio surchargé peuvent encore provoquer un arrêt. Aucun moteur ne peut garantir l'absence de coupure si le fichier suivant n'est pas disponible.

Le délai d'encodage/padding MP3 n'est PAS encore supprimé à partir des métadonnées LAME/Xing. Un silence déjà présent dans les données décodées est donc conservé. Des morceaux musicalement discontinus peuvent aussi produire un saut ou un clic : aucun fondu n'est imposé pour masquer le signal d'origine.

Le client utilise son horloge PCM continue entre les actions de transport ; la synchronisation de plusieurs machines, les longues playlists réelles et la dérive prolongée doivent encore être mesurées en multijoueur. L'arrêt serveur à la fin d'une playlist Off reste quantifié au tick Minecraft ; la toute fin du flux doit encore être contrôlée sur une sortie physique.

Restent à réaliser : retrait documenté du padding MP3, crossfade facultatif, commandes Gapless/Crossfade dans le nouvel onglet complet Concert Stage - Sound, profil Outdoor supplémentaire, paramètres DSP avancés demandés (crossover, compression, phase/délai, EQ avancé), et nouvelle refonte du Relay 24/6. Le gain et le limiteur de la preview.2 sont conservés ; ils ne constituent pas ces nouveaux traitements.

Les avertissements de ressources manquantes hors audio déjà présents dans le projet n'ont pas été corrigés par ce lot.

## Installation / essai

Remplacer l'ancien JAR Concert Stage Craft par celui-ci, sans conserver deux versions dans `mods`. Tester d'abord sur une copie du monde.

Importer deux WAV sans silence aux extrémités, activer All et écouter leur raccord. Essayer One puis changer de mode pendant la lecture. Répéter avec MP3/WAV et fichiers de fréquences différentes. Vérifier pause/reprise, déplacements, console éloignée et sauvegarde du monde. Signaler séparément un silence dans le fichier et une coupure du lecteur.
