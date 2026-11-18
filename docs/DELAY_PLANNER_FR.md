# CSC Delay Planner — preview.7

## Utilisation

Dans la console, ouvrir Concert Stage - Sound puis Delay Planner. L'outil CSC Delay Planner est également disponible dans l'onglet créatif du mod.

Avec l'outil : cliquer sur deux points du sol pour définir la zone public, Maj-clic pour la référence scène, cliquer sur une enceinte pour sélectionner/désélectionner une source principale, puis cliquer sur la console. L'outil ne pose aucun bloc.

1. Stage Setup : choisir les enceintes principales chargées.
2. Audience Area : vérifier la zone et la hauteur du sol, choisir le modèle proposé et la résolution.
3. Coverage : lancer Analyze. La carte compare l'installation actuelle et l'ajout des tours proposées. Vert/jaune/orange/rouge représentent un gain relatif configurable ; gris signifie terrain inconnu ou non chargé.
4. Delay Configuration : choisir une tour, cliquer sur la carte pour la déplacer, Maj-cliquer pour changer son point d'alignement. Régler hauteur acoustique, modules, orientation, groupe cible et nom du groupe. Auto align utilise en priorité une enceinte existante de ce groupe.
5. Apply applique le retard à **toutes les enceintes du groupe choisi**. Le nom et la valeur sont confirmés par l'état reçu du serveur. Rester à moins de 12 blocs de la console pour appliquer ou enregistrer.
6. Show world ghost et World heatmap affichent des aperçus transparents. Ils ne construisent pas les tours. Save plan conserve la zone, les sources et les trois propositions au maximum dans la console.

## Calcul

Un bloc représente un mètre. Le retard vaut `(distance principale → référence − distance delay → référence) / 343 × 1000` en millisecondes. Le point de référence change donc le résultat. Une valeur négative reste visible et ne peut pas être appliquée ; elle indique que ce groupe ne peut pas être aligné en lui ajoutant seulement un retard positif.

Le retard de groupe est traité dans le signal PCM partagé, après EQ/crossover du groupe, avant sa sommation. Les groupes utilisent la même horloge de lecture. Les changements de retard sont raccordés sur 20 ms. Valeurs autorisées : 0 à 1500 ms.

## Limites

La carte est une estimation du gain du moteur audio, pas une mesure SPL ni une simulation acoustique physique. Les enceintes principales déjà retardées ne sont pas compensées dans le calcul géométrique. L'analyse ne modélise pas les interférences de phase, la diffraction ou la météo. Les obstacles pleins atténuent les sources existantes ; les propositions utilisent les hauteurs du terrain échantillonné. Les chunks inconnus restent gris et ne sont jamais forcés au chargement.

Les propositions représentent des **ajouts** à l'installation existante. Après avoir construit et relié une tour, relancer l'analyse de l'installation réelle et ignorer l'ancienne proposition pour éviter de la compter deux fois. Les structures transparentes sont des schémas de placement, sans validation de charge ni modification du monde.

Zone maximale : 512 × 512 blocs ; grille adaptative jusqu'à environ 1600 points. Les lectures du monde sont réparties entre les ticks ; le calcul sur les données numériques s'exécute ensuite séparément. Fonction destinée à Forge 1.20.1 ; le backend Fabric n'a pas été adapté.

## CSC Relay

Deux LCD inclinés de 12°, boîtiers séparés, bordures, vitres en retrait, supports et aérations. Les écrans en jeu affichent le titre, le transport, la répétition, le temps, les niveaux réels et les noms/retards des groupes. Le contenu est limité à 12 blocs pour contenir le coût du rendu. Les fichiers Blender restent modifiables et contiennent les collections HIGH_DETAIL et GAME_READY.

La hauteur désigne le centre acoustique. Elle est relevée automatiquement si nécessaire pour garder le bas d'un grand assemblage au-dessus du sol. Les aperçus restent statiques. En Repeat Off, la fin de lecture ne prolonge pas le transport pour vider la mémoire des retards ; l'écoute des queues de retard reste à améliorer pour ce cas.
