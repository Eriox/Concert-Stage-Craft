# Modèles natifs CSC Audio — preview.7

Ouvrir les fichiers `Models/*.blend` avec Blender. Ils ont été vérifiés avec Blender 4.5.3. Les images utilisées sont intégrées aux fichiers ; une copie des textures est fournie.

Le dossier contient 13 modèles : trois enceintes Orvane (LX12, LX8, SB28), six cadres de support Veyra/Noryx/Orvane, la console Relay, un capuchon de fader, un maillon de chaîne et une pièce de liaison. Les fichiers GLB fournissent leurs versions optimisées. Il s'agit de créations CSC fictives.

Chaque fichier contient les collections HIGH_DETAIL et GAME_READY. La première est visible par défaut ; ne pas afficher/rendre les deux ensembles simultanément. Objets, matériaux et points nommés restent modifiables. Les petits composants ne sont pas fusionnés en un caisson unique.

Échelle : 1 unité Blender = 1 mètre = 1 bloc Minecraft. Blender utilise Z pour la hauteur ; la conversion depuis les coordonnées du mod est `(X, -Z, Y)`. Les enceintes prennent pour référence le centre du bord inférieur avant. La console est centrée au sol.

Les nouveaux LCD du Relay ont des boîtiers inclinés de 12°, des vitres en retrait, des supports et des aérations. Les points `CSC_Screen_Track` et `CSC_Screen_Mix` suivent cette inclinaison. Les surfaces des écrans sont statiques dans Blender : les textes, VU et délais sont dessinés par le moteur de rendu Minecraft à partir de l'état audio réel.

`validation_blender.json` contient les dimensions et nombres de triangles mesurés pour chaque collection. Les 13 fichiers Blender et leurs 13 exports GLB ont été rouverts. `Renders/` contient les présentations natives et l'aperçu en jeu du planner.

Les scripts de génération/export fournis attendent l'arborescence complète des sources du mod (`tools/audio_v2` et `common/src/main/resources`). Utiliser l'archive de sources complète pour les relancer. L'édition manuelle des fichiers `.blend` reste indépendante de ces scripts.
