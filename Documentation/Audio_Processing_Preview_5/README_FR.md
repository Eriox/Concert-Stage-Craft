# Concert Stage Craft — 1.1.1-preview.5

Suite de la preview.4 : traitements audio, panneau Sound et détails mécaniques du Relay. Le cahier des charges complet n'est pas encore terminé ; ce document distingue les fonctions livrées de celles qui restent à réaliser.

## Fonctions ajoutées

Le panneau **Concert Stage - Sound**, accessible depuis la console, comprend cinq onglets : System, Equalizer, Dynamics, Spatial, Advanced. Fond sombre, accent violet, commandes modifiées sans reconstruire l'écran. Les curseurs appliquent les valeurs pendant le déplacement ; la sauvegarde du profil intervient à la fermeture du panneau.

- Volume d'écoute et gain de sortie x1 à x8.
- Niveau des subwoofers de 0 à x2 et crossover Linkwitz-Riley 24 dB/oct réglable de 50 à 200 Hz. Les subs reçoivent le passe-bas ; les enceintes principales reçoivent le passe-haut lorsqu'une console pilote des subs, comme auparavant.
- EQ cinq bandes : plateau grave 90 Hz, cloches 300 Hz / 1 kHz / 3 kHz, plateau aigu 8 kHz ; +/-12 dB. Les fréquences et le Q sont fixes dans cette version. Cet EQ d'écoute s'ajoute aux trois bandes de la console partagée.
- Compresseur stéréo lié, activable : seuil -40 à 0 dBFS, ratio 1:1 à 10:1, attaque 10 ms et relâchement 150 ms fixes. La réduction de gain affichée provient du traitement audio réel et représente le maximum des consoles actives.
- Limiteur de sortie activable. Lorsqu'il est désactivé, le dernier écrêtage de sécurité empêche le dépassement du format PCM, mais une saturation audible reste possible avec des réglages excessifs.
- Délai commun de sortie de 0 à 500 ms et inversion de polarité commune. Il ne s'agit PAS d'un réglage relatif entre les subs et les enceintes principales.
- Profil CSC Outdoor Concert Mode : absorption des hautes fréquences par un passe-bas variant progressivement avec la distance moyenne des enceintes audibles (pondérée par leur couverture). Directivité et atténuation selon la distance sont désactivables séparément. La couverture existante conserve hauteur, longueur de la grappe et dispersion des modèles. Aucun effet de réverbération artificielle n'est ajouté.
- Réglages de consoles simultanées, cache, autorisation des téléchargements serveur et ouverture du dossier cache conservés.

## Portée et sauvegarde

Les nouveaux traitements constituent un **profil d'écoute local**, commun aux consoles entendues par ce client. Ils sont sauvegardés dans `config/concertstage-audio.properties`. Des instantanés immuables transmettent les valeurs au thread sonore ; décodage et DSP ne passent pas sur le thread principal.

La playlist, Repeat Off/All/One, le transport, les faders/groupes et l'EQ historique restent pilotés par le serveur et sauvegardés dans le monde. Les transferts audio autorisés et le cache SHA-1 restent utilisés ; aucun chemin local n'est supposé partagé entre joueurs.

## Relay 24/6

Le boîtier existant est conservé et détaillé davantage : six connecteurs arrière correspondant aux six groupes logiques, platines de fixation, inserts, broches et vis ; poignées compactes sur platines et traverses latérales sous le châssis. Les 24 commandes physiques restent réparties quatre par groupe logique. Aucune nouvelle entrée audio ni gestion de 24 canaux indépendants n'est inventée.

Les connecteurs physiques sont des détails visuels ; les liens audio Minecraft utilisent toujours l'Audio Link Tool. Les faders, afficheurs et vumètres existants restent pilotés par les états réels. Les collisions générales du châssis sont conservées ; chaque broche ne reçoit pas une collision distincte.

Les fichiers `.blend` HIGH_DETAIL/GAME_READY et `.glb` du Relay sont actualisés dans le pack de modèles. Échelle : 1 mètre = 1 bloc. Les autres modèles ne sont pas refaits.

## Validation

- Tests Java existants : décodeurs MP3/WAV, recherche, mixage, limiteur, politique de répétition et file PCM continue.
- Nouveau `SystemProcessorTest` : conservation d'un signal plat, délai exact de 48 échantillons à 48 kHz pour 1 ms, inversion de polarité, réduction de niveau par compression, gestion des valeurs non finies et limites du crossover.
- Tests de réponse : réduction d'un signal 10 kHz par le filtre d'absorption et modification effective de la réponse des subs à 100 Hz entre crossovers 50/200 Hz.
- Dix GameTests Forge réussis avant la modification finale des ressources visuelles.
- Compilation Forge finale : voir build-summary.log.
- Test client : voir client-summary.log. Il contrôle le rendu des cinq onglets, la lecture distante, le master, pause/reprise et la conservation de la source sonore lors de Repeat One.
- Vérification Blender : les fichiers natifs et exports sont rouverts par le validateur ; résultats dans validation_blender.json. Les rendus de contrôle sont fournis avec les modèles.
- Aucun essai d'écoute humaine ni test sur deux machines n'a été réalisé. L'environnement de test utilise une sortie OpenAL nulle.

## Travail restant

Le **crossfade facultatif et le retrait des délais/padding MP3 ne sont pas implémentés**. Les limites de la preview.4 sur les transitions de fichiers manquants, les silences encodés, la fin de playlist quantifiée au tick et la dérive entre machines restent applicables.

Le panneau ne dispose pas encore de toutes les sections demandées : le transport/Repeat/playlist restent dans la console existante ; pas de commandes dédiées Gapless/Crossfade, de réglage de tampon/qualité, ni d'occlusion géométrique. Les libellés nouveaux sont actuellement en anglais. Le réglage de fréquence/Q par bande, les délais/polarités par groupe et un profil DSP partagé par le serveur restent à faire.

Des avertissements de ressources manquantes hors audio préexistants restent présents. Ce lot ne valide pas tous les blocs historiques, les grosses scènes ni les interactions avec d'autres mods.

## Installation

Remplacer l'ancien JAR Concert Stage Craft par le nouveau, sans garder deux versions du mod. Forge 1.20.1 / Java 17. Vérifier d'abord la lecture et les contrôles sur une copie du monde, puis comparer les réglages pendant un même morceau.
