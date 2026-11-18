# CSC 1.2.0 rév. 16d — corrections d'après les captures et la vidéo du 9 novembre (20 h)

**Statut : corrigé dans le code, vérifié hors jeu (compilation des shaders, tests, vérification de types), PAS vérifié dans Minecraft.**

## Ce que montraient les captures, et la cause trouvée dans le code

| Problème signalé | Cause | Correction |
|---|---|---|
| **Rendu qui change avec la distance** (vidéo) | `FixtureRenderer` donnait à chaque projecteur une qualité selon sa distance : au-delà de 40 blocs, qualité moyenne avec des budgets réduits ; au-delà de 64 blocs, qualité basse, donc **plus de faisceau dans la brume, plus de projection, plus d'ombre**, avec les anciens rubans à la place. D'où les grands nuages qui traversent le toit vus du fond. | Faisceau, projection et ombres gardés jusqu'à la distance d'éclairage (160 blocs par défaut). Seul le niveau de détail baisse : ultra < 32 blocs, haute < 64, moyenne au-delà. |
| **Faisceaux invisibles selon l'angle** (F3 : « beams in haze 12 / 15 », « 14 / 27 », « 24 / 40 ») | Les plafonds de faisceaux dans la brume (12 / 24 / 40), de projections et de cartes d'ombre dépendaient de la distance de chaque projecteur. Ceux qui obtenaient une place dépendaient de **l'ordre de dessin, qui change quand la caméra tourne**. Les autres retombaient en rubans ou disparaissaient. | Plafonds de l'image entière, selon la qualité globale, et plus grands : 64 faisceaux dans la brume, 96 projections, 64 cartes d'ombre. |
| **FPS** (F3 : « 15 maps (31 redrawn) … 4805 meshes ») | Les cartes d'ombre étaient **redessinées deux fois par image**. Le moteur passe deux fois par image (après les blocs-entités, après les blocs translucides), et la liste des maillages de structures (rev. 16c) n'était pas la même aux deux passages. Le CPU aussi : « Fixtures 40 × 4 à 7 ms ». Depuis la rev. 16c, les rayons traversent les truss, et chaque bloc traversé était découpé en boîtes. | Cartes d'ombre traitées une seule fois par image. Les formes sans grande face sont écartées d'un coup, avec un cache par état de bloc. |
| **Wash trop lumineux** (images 5, 6) | Brume des wash multipliée par 1,9 en rev. 16b. Elle était invisible de loin à cause des paliers de distance, pas à cause de sa force. | Retour à la force de la rev. 16 (×0,75 W7, ×0,85 W19). |
| **Faisceaux qui traversent les bâches et les toits** | Les bâches (`TarpSheetRenderer`) et le toit de régie (`RegieRoofRenderer`) sont dessinés dans la couche « cutout » du tampon commun, que le jeu n'envoie **qu'à la fin de l'image, après les passes lumière**. Ils n'étaient donc pas dans la profondeur que lit la lumière. Depuis la rev. 16c, ils n'avaient plus non plus d'occulteur dans les cartes d'ombre. | Les couches solid / cutout / cutout-mipped du tampon commun sont envoyées **avant** la lumière (`GpuMesh.flushEarlyBlockLayers`). Dans les cartes d'ombre, bâches, rideaux, rails, ring truss et toit de régie entrent avec la forme qu'ils donnent aux rayons de lumière (feuilles fines, plaques ; `LightCollisionContext`). Jamais de boîte de contour. |
| **Pas de reflet sur les structures et la bâche** | Même cause pour les bâches et le toit de régie (absents de la profondeur). Sur les truss noirs, la lumière ne fait que multiplier une couleur presque noire. | Bâches et toit dans la profondeur (ci-dessus). **Lumière réfléchie** (reflet spéculaire) calculée par la projection et ajoutée indépendamment de la couleur du matériau : reflets sur les tubes, brillance de la bâche, dans la couleur de la lumière. |
| **Couleur des autres projecteurs** (images 7, 8) | Cause **non établie** dans le code. Chaque faisceau lit bien ses propres données (vérifié : couleur, masque optique, cellules, ombre). L'explication la plus probable est le rouge trop fort des wash (×1,9) devant et autour des faisceaux blancs : un faisceau blanc fin sur un fond rouge saturé paraît rouge. | Corrigé indirectement par le retour de la force des wash. **À revérifier** avec la vue Diagnostic « Faisceaux seuls » : si un faisceau blanc y est encore rouge, c'est un vrai mélange de données et il me faut cette capture. |

À noter : sur les images 2, 3 et 10, la ligne F3 indique **« CSC stage lights: off (desk setting) »**. Le bouton « Stage light » de la console était coupé, ce qui désactive tout le moteur (faisceaux calculés, projection, ombres) et ne laisse que les anciens rubans. Ces trois images ne montrent donc pas le moteur.

## Ce qu'il faut vérifier en jeu

Avec « Stage light » allumé, brume Légère, F3 ouvert :

1. **Distance** : refaire la marche de la vidéo, de la scène jusqu'à 80 blocs. Le faisceau doit garder le même aspect et ne jamais traverser le toit. Dans la ligne F3, « beams in haze N / N » doit avoir les deux nombres égaux.
2. **Angle** : tourner autour de la scène. Aucun faisceau ne doit disparaître ; « beams in haze » et « projectors … drawn / … asked » doivent rester égaux.
3. **FPS** : même vue que l'image 1. « shadows … maps (… redrawn) » doit rester à 0 redessin quand rien ne bouge, et « Fixtures » doit revenir sous la milliseconde.
4. **Bâches et toits** : un VORTEX vers une bâche, puis vers le toit. Le faisceau s'arrête dessus, la bâche est éclairée avec le gobo, et rien ne passe derrière.
5. **Reflets** : des truss noirs éclairés montrent des reflets.
6. **Couleurs** : wash rouges et VORTEX blancs comme sur les images 7 et 8, puis Diagnostic « Faisceaux seuls ».
7. **Wash** : W7 et W19 rouges de près et de loin. Ce ne doivent plus être des murs de couleur, mais ils doivent rester visibles depuis la salle.

## Coût

- Plus de faisceaux, de projections et de cartes d'ombre possibles qu'avant (64 / 96 / 64), mais chacun ne coûte que la surface qu'il couvre à l'écran.
- Les cartes ne sont redessinées que quand le cône, les blocs ou les structures changent.
- Le mode de performance automatique abaisse toujours la qualité globale quand les FPS chutent.
- Mesures en jeu : à faire (FPS moyens et 1 % low, lignes F3, avant / après).
