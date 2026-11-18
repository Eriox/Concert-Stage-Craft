# CSC 1.2.0 rév. 16c — Gobos du VORTEX X1 sur les truss

**Statut : corrigé dans le code, vérifié hors jeu, PAS vérifié dans Minecraft Forge 1.20.1.** Je ne peux pas lancer le jeu ici : le problème n'est pas déclaré corrigé tant que le protocole de la section 6 n'a pas été passé en jeu.

## 1. Les passes qui posent de la lumière

Il y en avait quatre, et un problème de truss peut venir de chacune.

| Passe | Où | Comment elle trouve les surfaces |
|---|---|---|
| A. Projection sur les surfaces | `concertstage_light_projector` (`VolumeLights.drawProjectors`) | Position de chaque pixel reconstruite depuis une copie du **depth buffer**, prise après les blocs et les maillages du mod. Ne touche que ce qui a été dessiné. |
| B. Ombres vues du projecteur | `BeamShadows` (cartes d'ombre 256²) | Géométrie des sections autour du cône. |
| C. Faisceau dans la brume | `concertstage_beam_volume` | Raymarching jusqu'à la profondeur de la scène, coupé par la carte d'ombre (B). |
| D. Décalques de repli | `FixtureRenderer.relight` / `spot` / `glowGobo` | **Rayons** lancés dans le cône (`sampleSurfaces` → `clipRig`). Au point d'impact, un **quad plat de la taille de toute la tache** est posé selon la face touchée. Utilisés quand la passe A ne tourne pas : qualité Low, projecteur à plus de 64 blocs, budget atteint, mode auto aux FPS bas, « Stage light » coupé, pack de shaders. |

## 2. Causes trouvées dans le code

1. **Décalques sur les tubes (passe D).**
   - `clipRig` répond avec les tubes du truss (`LightCompat.lattice`), et le quad de décalque fait la largeur de la tache (1 à 3 blocs). Posé sur un tube, il **traverse les ouvertures** : le gobo s'affiche sur une surface qui n'existe pas.
   - Cela n'arrive que dans les cas de repli listés plus haut, d'où le « parfois ».
   - Le rayon central fixait aussi la longueur du faisceau. Sans carte d'ombre, le faisceau dans la brume s'arrêtait net au plan du tube, et la projection 1,5 bloc derrière.
2. **Boîtes de contour dans la carte d'ombre (passe B).** Les blocs dessinés par un renderer et non couverts par `LightCompat.lattice` entraient dans la carte d'ombre avec leur **forme de contour** : `AngledHolderBlock` (truss en angle), `RingTrussCellBlock`, `RegieRoofBlock`, `OffsetBlock`, `HoistBlock`. Cette forme est une boîte pleine, pas la structure dessinée. Le faisceau dans la brume s'arrêtait sur cette boîte invisible : la coupe du gobo apparaissait sur ses faces, **dans les ouvertures**, et rien n'éclairait derrière.
3. **Textures à trous dans la carte d'ombre (passe B).** Pour les blocs rendus comme modèles, toutes les faces étaient opaques, trous de texture compris (échafaudages, barreaux, modèles d'autres mods à texture découpée). Même effet qu'une surface invisible.
4. **Normales sur les arêtes (passe A).** L'orientation venait des dérivées 2×2 de l'écran. Sur l'arête d'un tube fin, elles mélangeaient le tube et le sol derrière. Le shader utilisait alors une orientation neutre, et rejetait aussi à tort un sol vu en incidence rasante (zone repérée grâce à la nouvelle vue Normales).

Ce qui n'était **pas** en cause : la projection A elle-même. Elle ne lit que la profondeur réellement écrite, et les truss du mod sont de la vraie géométrie (76 à 152 éléments, texture sans transparence).

## 3. Corrections

| # | Correction | Fichiers |
|---|---|---|
| 1 | Un rayon ne s'arrête que sur une **vraie grande surface** : la boîte touchée doit couvrir au moins ½ bloc dans les deux directions de la face (sol, mur, dalle, marche, bâche). Tubes, diagonales, rails et petits éléments de modèles le laissent passer. Plus aucun décalque ne flotte dans les ouvertures, et le faisceau n'est plus coupé au plan d'un tube. | `FixtureRenderer.clipRig` / `largeFace` |
| 2 | Carte d'ombre **hybride** : sections de blocs, plus **les maillages réellement dessinés** par le mod pour ses structures (tours, truss en angle, ring truss, toits, pièces de scène), dans le repère du projecteur, redessinés quand ils bougent. Plus aucune boîte de contour ; un renderer d'un autre mod ne projette pas d'ombre plutôt qu'une boîte invisible. | `GpuMesh.drawShadowCasters` / `drawnSignature`, `BeamShadows` |
| 3 | **Test alpha** dans la carte d'ombre (coordonnées de texture des modèles et des maillages) : les trous des textures découpées laissent passer la lumière. | `concertstage_shadow.*`, `BeamShadows.emit` |
| 4 | Normales reconstruites **par côté** : pour chaque axe, le voisin sur la même surface (tube, diagonale), plan rasant reconnu comme plan. Code commun à la projection et aux vues de diagnostic. | `include/concertstage_depth.glsl`, `concertstage_light_projector` |

Inchangé et préservé :

- Le masque optique commun (`concertstage_optics.glsl`) : même gobo, même rotation, même zoom et focus dans l'air et sur les surfaces.
- Les paramètres du VORTEX : spot, zoom, focus, rotation du gobo, intensité, couleur.
- Shimmer, seule compatibilité externe.
- Les modèles 3D, non modifiés.

## 4. Diagnostic (bouton « Diagnostic » de la console)

| Vue | Montre |
|---|---|
| Faisceaux seuls | la lumière volumétrique seule |
| Surfaces seules | la projection sur les surfaces seule |
| **Profondeur** (nouveau) | la profondeur que lit le moteur. Ce qui n'y est pas ne peut pas recevoir de lumière ; une ouverture de truss doit montrer le fond, pas une surface. |
| **Normales** (nouveau) | les normales reconstruites, telles que la projection les utilise. Magenta = pixel isolé (tube d'un pixel), orientation neutre. |
| Visibilité / ombres | vu depuis le projecteur : vert = éclairé, rouge = à l'ombre ; dans l'air, seules les parties à l'ombre |
| Masques optiques | le masque du gobo sur les surfaces |

La ligne F3 des ombres compte aussi les maillages dessinés dans les cartes (« N meshes »).

## 5. Banc d'essai des shaders (pas Minecraft)

Les vrais shaders du mod tournent sur OpenGL logiciel (Mesa) : truss carrés avec 4 membrures et diagonales, truss vertical, structure de plusieurs truss, bâche, modèle complexe (tabouret), VORTEX gobo 6, sans brume puis brume légère. Images dans `validation/rev16_harness/t*.png`.

- **`t10_before_outline_box` / `t10_after_real_tubes`** : le même truss, dans la carte d'ombre comme boîte de contour (avant, pour les structures dessinées par un renderer) puis comme vrais tubes (après). Avant, le faisceau s'arrête sur la boîte et le sol dessous reste noir. Après, le gobo passe entre les tubes et le sol reçoit le motif avec l'ombre des tubes.
- **`t11_diag_*`** : profondeur, normales, visibilité.

Ce que le banc ne peut pas montrer :

- les décalques de repli (dessinés côté Java) ;
- les maillages GPU du jeu dans la carte d'ombre : la scène de test est lancée en rayons dans les deux cas ;
- les FPS et les temps GPU d'une carte graphique.

## 6. Protocole dans Minecraft Forge 1.20.1 (à faire)

Même VORTEX X1, même gobo, focus net, à 6–10 blocs. Captures **avant** (version précédente) et **après**, même position de caméra.

1. Sol plat, puis mur.
2. Truss horizontal, truss vertical, structure de plusieurs truss (dont un truss en angle et un ring truss).
3. Bâche, puis un modèle d'un autre mod (Stage Equipment Furniture par exemple).
4. Pour chacun : brume **Aucune** (projection de surface seule), puis **Légère**.
5. Pour chacun : vues Diagnostic Profondeur, Normales, Visibilité / ombres.
6. Forcer le repli (qualité Low) : plus aucun motif dans les ouvertures.
7. Relever les FPS (moyenne, 1 % low) et la ligne F3 « shadows … maps (… redrawn), … meshes », avant / après. Les temps GPU ne sont pas mesurés par le mod (pas de requêtes de temps GPU).

## 7. Coût et limites

- Les maillages dans la carte d'ombre ne sont dessinés que quand une carte est redessinée (cône, blocs ou maillages modifiés), puis la carte est réutilisée. Une tour qui monte redessine la carte à chaque image du mouvement. Coût en jeu non mesuré.
- Le test alpha ajoute une lecture de texture par pixel de carte d'ombre.
- Un renderer d'un autre mod (géométrie qui n'est pas un modèle de bloc) **ne projette pas d'ombre**, mais reçoit la lumière s'il est dans le depth buffer au moment de la projection. Un rendu translucide ou dessiné plus tard ne la reçoit pas.
- Les rayons de lumière d'ambiance (« spill ») ne s'arrêtent plus sur les tubes ; les tubes au-dessus du projecteur reçoivent cette lumière par les lumières ponctuelles.
- Les cartes d'ombre restent en 256² : un tube très fin et lointain peut tomber sous un texel.
