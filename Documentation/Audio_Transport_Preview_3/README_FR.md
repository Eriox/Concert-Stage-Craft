# Concert Stage Craft — 1.1.1-preview.3

Premier lot du nouveau cahier des charges audio, basé sur la preview.2. Cette version ne constitue pas la réalisation complète du cahier des charges.

## Changements livrés

- Trois modes de répétition : Off, All, One. Le bouton ↻ de la console fait défiler ces modes ; ↻1 identifie One, la sélection colorée distingue les modes actifs et une infobulle indique le mode en français/anglais.
- Repeat One conserve le morceau courant à sa fin. Next reste une action manuelle permettant de changer de morceau, même en Repeat One.
- Mode conservé après fermeture de l'interface et sauvegarde du monde. Migration des anciennes sauvegardes : Repeat=true devient All ; false devient Off ; absence du champ conserve All.
- Suppression des 250 ms d'attente artificielle dans le transport serveur. Les dépassements de durée sont reportés sur le morceau suivant ; les positions, recherches et reprises conservent les millisecondes entre deux ticks.
- Téléchargement anticipé du successeur immédiat par le système existant de transferts autorisés, cache SHA-1 et réglage client autorisant/refusant les téléchargements. Ne télécharge pas toute la bibliothèque. Repeat Off ne précharge pas après le dernier morceau.
- Identifiants des blocs/objets conservés. Enceintes Veyra, Noryx, Orvane et Relay 24/6 de la preview.2 conservées. Aucune nouvelle modification de modèle 3D dans ce lot.

## Limite essentielle : pas encore de gapless PCM

Le préchargement de cette version concerne le fichier, pas un décodeur prêt à enchaîner les échantillons. La source audio et le lecteur sont encore recréés lors d'une transition. Des coupures restent donc possibles, y compris en Repeat One. La suppression du délai serveur ne prouve pas une transition sans silence.

Restent à implémenter et valider : flux PCM continu avec décodeur suivant préparé, répétition PCM sans reconstruction OpenAL, fréquences d'échantillonnage mixtes, retrait du délai/padding MP3 documenté, crossfade facultatif, nouveau profil Outdoor et réglages DSP demandés, onglet complet Concert Stage - Sound, refonte 3D supplémentaire de Relay. Le gain 2,8 et le limiteur de la preview.2 restent présents ; ils ne constituent pas les nouveaux traitements demandés.

## Validation de ce lot

- Compilation Forge Java 17 : réussie.
- Serveur Forge GameTest : 10 tests requis réussis, incluant les 8 tests précédents.
- Nouveaux tests en jeu serveur : sauvegarde des trois modes, migration des anciennes données, Next manuel, cycle de sélection, cinq répétitions successives, conservation de 27 ms après une fin à 1023 ms, progression All, arrêt Off, seek et pause/reprise précis.
- Tests Java audio : décodeurs WAV/MP3, recherche MP3, mixage, limiteur et nouvelle politique de répétition réussis. La politique est testée sur des playlists vides et de 1 à 64 morceaux.
- Test client automatisé : résultat fourni dans client-summary.log. Sortie OpenAL de test sans périphérique physique ; aucune écoute humaine revendiquée.
- Préchargement réseau du morceau suivant : code compilé, accès contrôlé par le mécanisme existant ; essai réel sur deux machines et téléchargement lent à effectuer.
- Le chargement client signale encore des ressources manquantes préexistantes hors audio (notamment certaines plateformes/régie). Ce lot ne corrige pas ces avertissements et ne prétend pas valider tous les blocs historiques.
- Aucune mesure de silence ni validation de gapless revendiquée dans cette version.

## Installation et vérification manuelle

Remplacer l'ancien JAR Concert Stage Craft par le nouveau ; ne pas conserver simultanément deux versions du même mod. Forge 1.20.1, Java 17.

Importer deux morceaux, choisir le premier et cliquer sur ↻ jusqu'à ↻1. Attendre plusieurs fins de morceau : le premier doit rester sélectionné. Tester Next, puis All et Off. Fermer et rouvrir la console, sauvegarder/recharger le monde et vérifier le mode. Une coupure audible peut encore survenir tant que le flux PCM continu n'est pas livré.
