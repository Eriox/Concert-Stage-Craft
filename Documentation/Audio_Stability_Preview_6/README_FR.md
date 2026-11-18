# Concert Stage Craft — 1.1.1-preview.6

Premier lot du nouveau cahier des charges : diagnostic des coupures, correction d'une défaillance reproduite et atténuation selon la distance. Basé sur la preview.5, Forge 1.20.1 / Java 17. Le CSC Delay Planner n'est pas encore livré.

## Diagnostic et corrections

Le diagnostic vient du code et du client de test, pas d'une analyse de la vidéo de l'utilisateur.

1. L'ancienne courbe conservait le gain maximal jusqu'à 12 blocs. Elle est remplacée par une courbe continue décroissant dès qu'on s'éloigne : `gain = output × (1 + distance/reference)^(-rolloff) × fade`. Le fade est une transition lisse sur le dernier quart de la portée effective.
2. La normalisation du mélange divisait par la somme des gains spatiaux. Lorsque plusieurs enceintes étaient audibles, elle compensait leur atténuation et pouvait créer un plateau. Le budget de mélange dépend désormais du nombre de liens de la console, indépendamment de la distance du joueur. Le limiteur existant reste utilisé.
3. Un échec du décodeur des vumètres pouvait faire exclure le flux musical du mixage. Le flux sonore continu est désormais indépendant de ce lecteur de mesure. Le test client provoque volontairement cet échec et vérifie que le signal musical reste non nul.
4. Le streaming retournait des blocs de 40 ms. Inspection locale de `Channel.attachBufferStream`, `pumpBuffers` et `ChannelAccess.scheduleTick` de Minecraft 1.20.1 : quatre buffers sont préparés. La réserve n'était donc que de 160 ms. Dans le test, Minecraft a fermé un flux après 15360 échantillons à 48 kHz (320 ms lus), avec décodeur alimenté, aucune erreur, aucun EOF et `voice.dead=false`. Ce défaut a été reproduit en sortie OpenAL nulle.
5. La durée de bloc est désormais réglable de 40 à 500 ms, défaut 250 ms : quatre blocs donnent environ une seconde de réserve OpenAL. Avec ce réglage, le même test de chargement/rendus/outil Array ne perd plus de source. Une perte confirmée est détectée après la grâce initiale de quatre ticks au lieu de quarante ; le nouveau flux repart à la position courante de la console.

Les déplacements changent les gains ; ils ne redémarrent pas volontairement le morceau. Les pertes de source confirmées font l'objet d'une récupération distincte. Le moteur conserve une source stéréo et une horloge PCM par console ; il ne crée pas un lecteur MP3 par enceinte.

## Réglages

Dans **Concert Stage - Sound**, nouvel onglet **Dist.** :

- Reference distance : 1 à 100 blocs.
- Maximum range : 16 à 512 blocs, avant facteurs de modèle/rôle et longueur de grappe.
- Rolloff : 0,1 à 3.
- Speaker output gain : x0 à x4.
- Gain smoothing : 5 à 500 ms (attaque ; relâchement 1,5 fois plus long).

Le facteur de portée tient compte du modèle, de la hauteur totale et du rôle. Le fichier client `config/concertstage-audio.properties` permet de modifier sans code `range_factor.veyra`, `.noryx`, `.orvane`, `.subs`, `.front_fill`, `.delay`. Les changements manuels de ce fichier sont pris en compte au redémarrage du client. La courbe générale utilise `reference_distance`, `maximum_distance`, `rolloff`, `speaker_gain`, `gain_smoothing_ms`.

Dans **Adv.**, `OpenAL PCM block` règle `stream_buffer_ms`. Une réserve élevée tolère mieux les ralentissements mais augmente le délai d'application des traitements déjà encodés dans les buffers. Le lissage ne supprime pas ce délai de mise en file. Choisir 40 ms réduit la latence mais réintroduit la faible marge qui a causé la défaillance reproduite.

Les nouvelles options sont locales à l'écoute. Les paramètres partagés de la console et les identifiants existants sont conservés. Les réglages de la preview.5 restent sauvegardés.

## Audio Diagnostics

Nouvel onglet **Diag.** avec valeurs issues du moteur :

- instances considérées actives par Minecraft / instances conservées ;
- demandes de démarrage et événements où le moteur a rendu une source inactive ;
- nombre de corrections de l'horloge de timeline ;
- attentes PCM de plus de 80 ms, et temps depuis la dernière ;
- état et position de la console, dérive de son horloge interne ;
- nombre d'enceintes chargées / nombre de liens ;
- état du producteur PCM et du décodeur des vumètres ;
- nombre de blocs PCM prêts dans la file bornée de 150 blocs ;
- erreur éventuelle du flux.

Le journal activable ajoute des lignes `CSC_AUDIO` dans `logs/latest.log` : démarrage demandé, perte de source, fin/fermeture du flux, nombre d'échantillons lus, état du décodeur et erreurs.

Une attente PCM de 80 ms est un événement de sous-alimentation de cette file, pas une preuve de silence audible : OpenAL peut encore disposer de données. Le compteur matériel des buffers OpenAL n'est pas exposé par l'API utilisée de Minecraft et est indiqué comme indisponible. Aucun compteur matériel fictif n'est affiché.

Les permissions de récupération de fichiers, les requêtes d'état et les tickets temporaires de console acceptent désormais une écoute jusqu'à 512 blocs autour d'une enceinte **chargée**, avec leurs budgets existants. Cela ne force pas le chargement des chunks des enceintes. Une enceinte déchargée peut encore cesser de contribuer au mixage ; le nombre chargé/lié aide à identifier ce cas. La correction générale de ce scénario reste à réaliser.

## Tests

- Suite Java audio : décodeurs MP3/WAV, recherche MP3, mixage, limiteur, répétition, transitions PCM et DSP précédents.
- `DistanceCurveTest` : monotonie sur des grilles de distances et différents réglages, bornes finies, mesures à 2, 5, 10, 25, 50, 100 et 150 blocs.
- File PCM : fichier suivant volontairement absent, attente bornée, événement de sous-alimentation et erreur réellement exposés ; fermeture du lecteur.
- Dix GameTests Forge réussis après les modifications communes/serveur.
- Test client court : sept onglets affichés, courbe décroissante calculée avec un assemblage réel, lecture distante, déplacement, master, pause/reprise, Repeat One conservant sa source, panne forcée du décodeur de vumètres et absence de perte involontaire de source avant les commandes de transport.
- Test long WAV/MP3 réussi : plus de trois minutes de WAV continu, puis plus de trois minutes de MP3 continu, flux conservé et zéro attente PCM prolongée détectée. Aucune perte de source involontaire n'apparaît dans le journal de ce test. Les fichiers sont présents dans le cache avant lecture : il ne s'agit pas d'un test de téléchargement réseau lent. Résultats dans `client-long-summary.log`.
- Les tests client utilisent OpenAL NoOutput. Aucune écoute humaine ni mesure sur un périphérique physique n'est revendiquée.

Pour reproduire : `sh tools/audio_tests/run.sh`, GameTests Forge, puis test client opt-in. `CSC_AUDIO_LONG_TEST=1` active les lectures longues dans `tools/audio_v2/run_smoke_test.py` (JDK 17, ffmpeg et affichage requis). Le test recrée uniquement son monde de développement `forge/run/saves/CSC_Audio_Review` depuis le monde GameTest.

## Reste à réaliser

Le **CSC Delay Planner n'est pas implémenté dans ce lot** : pas de nouvel outil, sélection de zone, heatmap, recommandations, prévisualisation de tour ni calcul/application de délais de groupes. Aucun bouton simulant ces fonctions n'est ajouté. Le délai commun d'écoute de la preview.5 reste présent ; il ne constitue pas un délai de tour de rappel.

Restent également : continuité complète lorsque les chunks des enceintes sont déchargés, essais multijoueurs, tests de téléchargement lent et de grosses scènes. Les défauts précédents sur padding MP3, crossfade absent, occlusion, et paramètres DSP avancés non livrés restent ouverts. Des silences encodés dans les fichiers sont conservés.

Ce lot corrige les mécanismes vérifiés ; il ne garantit pas la suppression de toutes les coupures possibles dans un autre environnement, notamment en cas de gel supérieur à la réserve, fichier absent/invalide, perte de périphérique ou autre mod audio.

## Installation et collecte d'un problème restant

Remplacer l'ancien JAR par celui-ci, sans garder deux versions du mod. En multijoueur, installer la même version côté serveur et clients pour bénéficier des extensions de portée des échanges. Sur une copie du monde, ouvrir **Concert Stage - Sound > Diag.**, activer le journal et reproduire le problème. Consulter `latest.log` et la ligne chargé/lié. Noter la durée de bloc, la distance, le modèle, et si les chunks des enceintes sont visibles. Les ressources manquantes hors audio déjà présentes dans le projet ne sont pas corrigées par ce lot.
