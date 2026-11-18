# CSC AUREX — 1.1.0-aurex.1

Gamme originale intégrée au module audio de Concert Stage Craft, à partir du ZIP 1.1.0 fourni. Cette livraison est une variante de cette base 1.1.0 ; elle ne fusionne pas le développement AutoShow 1.2.0.

## Installation

Minecraft 1.20.1 et Java 17. Fermer le jeu et sauvegarder le monde, puis remplacer l'ancien JAR Concert Stage Craft par le JAR correspondant au chargeur utilisé. Ne pas installer simultanément l'ancien et le nouveau JAR : ils portent le même identifiant `concertstage`. Les bibliothèques de construction ne sont pas à ajouter au dossier mods. Pour Fabric, conserver Fabric API et le chargeur compatibles avec la version fournie.

Les anciens identifiants Veyra, Orvane et Noryx restent enregistrés. Leurs maillages et textures n'ont pas été réécrits. Les six anciens bus gardent leurs numéros et leurs valeurs ; les retours sont ajoutés aux indices 6, 7 et 8.

## Équipements

| Équipement | Enveloppe nominale X / hauteur / profondeur | Utilisation |
|---|---|---|
| AUREX M12 | 1,60 × 0,60 × 0,90 m | Enceinte principale individuelle, coffret trapézoïdal en plan, un 12 pouces et un pavillon séparé |
| AUREX S218-F | 1,80 × 1,15 × 1,10 m | Double 18 pouces, stack au sol ou suspension via cadre AUREX |
| AUREX M15-W | 0,78 × 0,47 × 0,70 m réservés en jeu | Wedge de 15 pouces ; géométrie haute d'environ 0,466 m, marge de collision incluse |
| AUREX Flying Frame | environ 1,834 × 0,118 × 1,132 m hors tout | Cadre du S218-F, goupilles, chapes, traverses et manilles |
| M12 Suspension Yoke | environ 1,634 × 0,118 × 0,920 m hors tout | Suspension dédiée au M12 |
| Collier de truss | environ 0,072 × 0,095 × 0,060 m | Accessoire Blender/GLB, alésage pour tube de 50 mm ; pas d'item indépendant ajouté en jeu |

1 mètre Blender = 1 bloc Minecraft. La référence dimensionnelle du M12 est **Orvane LX8** : mêmes dimensions logiques, même unité et même convention d'origine. La forme du coffret, la façade et les détails AUREX sont nouveaux. Le M12 ne s'accouple pas en line array ; le wedge ne s'empile pas comme une enceinte de diffusion.

## Placement et suspension

- M12 : clic sur une surface pour le poser. La suspension reprend les règles CSC existantes ; le support M12 dédié peut être placé sous un truss ou un palan, puis recevoir un M12. Sa face avant se règle avec l'Array Tool.
- S218-F au sol : poser un sub, puis cliquer dessus avec un autre S218-F pour prolonger le stack. Les limites de quantité du moteur CSC existant restent applicables.
- S218-F suspendu : placer **CSC AUREX S218-F Flying Frame** sous un support compatible ou le crochet d'un palan CSC, puis ajouter les S218-F par clic droit. Le placement direct d'un sub sous un bloc reste refusé : le cadre dédié est obligatoire. Un cadre d'une autre gamme ou le yoke M12 ne reçoit pas de S218-F.
- Utiliser la télécommande de levage CSC habituelle pour déplacer le palan. La liste des enceintes, le cadre et les états audio voyagent dans les données de l'assemblage. Les cellules de collision sont reconstruites à l'arrivée.
- Maj + clic vide retire le dernier élément d'un stack selon le comportement CSC habituel. Casser l'assemblage restitue ses équipements selon le mode de jeu.

Les liens et chaînes de suspension en jeu utilisent les composants CSC existants. Le collier 50 mm est livré comme modèle éditable et montré dans la démonstration Blender ; il n'ajoute pas un système autonome de fixation universelle. La démonstration de truss est une présentation géométrique, pas un plan de levage certifié.

## Retours et monitoring

Poser le M15-W au sol. **Maj + clic droit avec la main vide** alterne les deux positions de sol ; l'Array Tool règle son azimut. Le tilt est ramené aux deux positions supportées, 0° et −15°. La position et la collision emploient le même pivot corrigé pour conserver l'appui au sol. L'axe audio inclut l'inclinaison réelle du baffle et cette rotation.

L'Audio Link Tool propose les modes **Retour 1**, **Retour 2**, **Retour 3** en plus des anciens groupes. Sélectionner la console avec cet outil, choisir le circuit et lier le wedge. Le mode Auto choisit Retour 1 pour un M15-W.

Dans la console audio, cliquer sur le sélecteur de banque **Façade / FX — Retours >** pour accéder aux trois faders de retours et à leurs mutes. Le master reste commun. Les groupes disposent de leurs gains et délais réels dans le mixeur ; leurs valeurs sont sauvegardées et synchronisées par le serveur.

Il s'agit de trois circuits de **la même piste PCM stéréo**, convertie en mono pour les retours. Le moteur existant ne fournit pas de multipiste, d'entrées micro séparées ni de mix pré-fader par instrument : aucun de ces services n'est simulé par un simple libellé. Les faders physiques de l'ancien modèle de console conservent leur animation historique des six groupes de façade ; les retours se pilotent dans l'interface de la console.

## Fichiers Blender et conversion

Chaque équipement possède un `.blend` natif et un `.glb` GAME_READY. Les `.blend` sont modifiables, en mètres et avec Z vertical ; les images utilisées sont intégrées dans les fichiers. Les exports GLB suivent la convention standard glTF Y vertical. L'origine de l'enceinte est centrée au bord avant inférieur. Les exports techniques Minecraft convertissent Blender `(X,Y,Z)` en `(X,Z,-Y)`.

- `HIGH_DETAIL` : grilles perforées modélisées, cônes, connecteurs, visserie, matériau noir avec microrelief et cartes de rugosité/normales.
- `GAME_READY` : géométrie simplifiée et grilles opaques texturées. La première couche UV est l'atlas du jeu ; les petits composants métalliques utilisent volontairement des plages de matériau partagées. `BakeUV` est une seconde couche dépliée et empaquetée pour l'équipement GAME_READY complet ; les gabarits PNG sont dans Textures.
- Les objets et points d'attache sont nommés par fonction. Les points de connexion du sub distinguent haut/bas, gauche/droite et avant/arrière. Le collier possède `CSC_Attach_Truss_Tube` et `CSC_Attach_Hoist` ; le wedge possède les repères de sol et de visée.
- Le renderer CSC lit les triangles et leurs normales depuis `assets/concertstage/speakers/*.json`. L'inventaire utilise les OBJ et l'atlas associé, compatibles avec les chargeurs OBJ Forge/Fabric déjà présents dans le projet. Aucun passage par Blockbench ni nouveau `.bbmodel`.
- Les scripts de reconstruction sont dans `tools/aurex` des sources complètes. Exécuter `prepare_textures.py`, puis Blender en arrière-plan avec `build_aurex.py`. Les utilitaires Blender existants de `tools/audio_v2/build_models.py` sont réutilisés pour les primitives et l'export ; aucun ancien coffret n'est utilisé comme base de géométrie.

Les dimensions hors tout mesurées et les nombres de triangles sont dans `Documentation/validation_blender.json`. Le rendu distant conserve une silhouette AUREX simplifiée, au lieu de remplacer le wedge par un cube générique. Les versions HIGH_DETAIL ne sont pas embarquées comme géométrie de jeu.

## Validation et limites

Voir `Documentation/RAPPORT_TESTS_FR.md`. Les rendus livrés proviennent de Blender et ne sont pas des captures Minecraft. Les tests serveur valident des comportements réels, mais ne constituent pas une vérification graphique de la scène du joueur, des FPS GPU ou d'un serveur multijoueur avec deux clients.
