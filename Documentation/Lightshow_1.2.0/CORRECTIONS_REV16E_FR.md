# CSC 1.2.0 rév. 16e — lueur noire, lentilles LUMORA, regard dans un VORTEX, PULSESTRIKE M

**Statut : corrigé dans le code et vérifié hors jeu (compilation des 7 shaders sur Mesa, 19 tests optiques, vérification de types, banc d'essai des shaders). PAS vérifié dans Minecraft.** Je n'ai pas Minecraft ici : rien de ce qui suit n'est présenté comme validé en jeu.

## 1. La « lueur noire » (les 8 captures du 9 novembre, 21 h)

**Ce que montrent les captures.** Les zones noires ont trois formes :

- des **rectangles alignés sur l'écran** autour des faisceaux (image 2 : au-dessus du truss, le ciel et les faisceaux coupés net) ;
- des **carrés noirs avec une lueur blanche au centre** autour des lentilles des projecteurs au sol (image 3) ;
- des **plans sombres** qui laissent voir le motif d'ombre du truss (image 1).

**Cause.** Chacune de ces formes correspond exactement à la surface d'une passe lumière du mod :

| Forme | Passe lumière correspondante |
|---|---|
| Rectangle autour d'un faisceau | Le quadrilatère d'un faisceau dans la brume |
| Carré avec lueur au centre | Le halo d'une lentille. Sa texture est un disque blanc sur fond noir **opaque**. |
| Plan sombre avec motif d'ombre | La lumière projetée sur une surface |

Ces passes sont faites pour **s'ajouter** à l'image (mélange « screen » ou additif). Dessinées **sans mélange**, elles la **remplacent**. On voit alors leur contenu brut : noir partout, plus clair là où il y a de la lumière.

Le jeu garde une copie de l'état de mélange d'OpenGL et saute un appel « activer le mélange » quand sa copie dit qu'il est déjà actif. Si l'état réel a été changé sans passer par cette copie, le mélange restait coupé.

Le seul changement de la rév. 16d qui touchait cet endroit du rendu est l'envoi anticipé des couches « solid / cutout » partagées du jeu. Il a été fait pour mettre les bâches dans la profondeur, et c'est le déclencheur le plus probable. La cause exacte du décalage d'état ne peut pas être observée sans le jeu.

**Corrections.**

1. **Mélange imposé avant chaque passe.** Avant chaque passe lumière (faisceaux dans la brume, lumière sur les surfaces, voile de brume, halos, émetteurs), l'état de mélange est imposé pour de vrai (`VolumeLights.forceBlend`) :
   - désactivation puis activation (la copie ne peut pas sauter les deux) ;
   - équation et facteurs envoyés directement à OpenGL, avec les mêmes valeurs que la copie ;
   - suivi des mélanges des shaders remis à « opaque », pour que le shader de la passe ne puisse pas couper le mélange en s'appliquant.

   Les types de rendu des halos (`LightRenderTypes`) font de même.
2. **Couches du jeu laissées intactes.** La rév. 16d n'envoie plus les couches partagées du jeu (`flushEarlyBlockLayers` supprimé).
   - Les bâches, le toit de régie, les tôles de toit et les maillages de rigging du mod sont enregistrés à part (`GpuMesh.cutout`).
   - Ils sont dessinés **avant la lumière**, avec l'état explicite des maillages GPU, qui s'affichent correctement.
   - Ils restent dans la profondeur : la lumière s'y pose et les faisceaux s'y arrêtent.
   - Ce qu'ont dessiné les autres mods et le jeu dans ces couches n'est plus touché.
3. **Bug corrigé dans les cartes d'ombre.** La texture servant au test de transparence (trous des textures découpées, grilles) était remplacée par la dernière texture liée, car `VertexBuffer.drawWithShader` relit les textures de `RenderSystem`. Les ombres pouvaient donc être pleines ou trouées au hasard. La texture passe maintenant par `RenderSystem.setShaderTexture`.

## 2. Lentilles des LUMORA invisibles (image 6)

La lueur de chaque cellule était un carré **à plat sur la lentille**. Vue à 30–60° de l'axe, elle se réduisait à un trait, et la LUMORA allumée montrait des lentilles sombres.

Une lentille de wash LED est un dôme qui montre sa source sous un grand angle. Chaque cellule allumée reçoit maintenant aussi une **petite lueur tournée vers la caméra**, juste devant la lentille :

- forte dans l'axe, elle s'atténue vers le côté ;
- elle disparaît derrière la face ;
- elle garde la couleur et le niveau de la cellule (zones DMX).

Vue de dos (image 8 : la caméra au-dessus des têtes orientées vers le sol), les lentilles restent cachées, comme sur un vrai projecteur.

## 3. Regarder un VORTEX dans les yeux (avec ou sans gobo)

- **Faisceau dans la brume.** Dans l'axe, la ligne de visée traverse toute la longueur du faisceau au lieu de sa largeur. La somme valait des dizaines de fois la vue de côté, et tout le cône était écrêté en **disque blanc uniforme** (gobo et dégradé perdus).
  - L'œil (et un appareil photo) s'adapte à une source dans le champ : la vue dans l'axe est exposée plus bas.
  - La lumière est **compressée en douceur** par pixel, en gardant la teinte, au lieu d'être écrêtée.
  - Résultat sur le banc d'essai (`validation/rev16e_harness/vortex_into_before.png` / `_after.png`) : cœur chaud, dégradé jusqu'au bord du cône, forme du gobo et rayons du gobo visibles.
  - Les vues de côté ne changent pas : en dessous de 0,6 la courbe est quasi linéaire.
- **Lentille.** Le disque « regard dans la lentille » allait jusqu'à 6,4 rayons de lentille : c'était le grand disque blanc de l'image 7. Il fait maintenant la taille de la lentille (jusqu'à 1,5 rayon dans l'axe). Le halo autour est un peu plus serré.
- **Étoile de diffraction.** Pour le VORTEX regardé presque dans l'axe : 4 rayons fins et discrets, plus longs quand on est exactement aligné (diffraction des cils et de l'ouverture de l'œil ou de l'appareil).
- **Reflets sur les surfaces (rév. 16d).** Ils sont plus fins et plus faibles (exposant 40 au lieu de 20, force 0,2 au lieu de 0,35). Les blocs mats montraient de grands disques blancs sous une rangée de blinders.

## 4. PULSESTRIKE M

Chaque cellule et chaque barre allumée est traitée comme un **tube de lumière** visible sur presque tout le demi-espace devant la face, et non comme un autocollant plat :

- un ruban qui suit la barre et fait toujours face à la caméra ;
- un cœur additif qui tire vers le blanc au centre, plus fort sur les barres blanches ;
- une lueur plus large de la barre dans l'air, plus forte sur un flash.

Ces lueurs s'ajoutent à celles de la rév. 16b (cellules, bloom, panneau).

## Ce qu'il faut vérifier en jeu (F3 ouvert, « Stage light » allumé)

1. Refaire les vues des images 1, 2, 3 et 7. **Aucun rectangle ni carré noir**, ni autour des faisceaux, ni autour des lentilles.
2. Bâches et toits :
   - un VORTEX vers une bâche, puis vers le toit ;
   - le faisceau s'arrête dessus, la bâche est éclairée, et elle a la même couleur et le même éclairage qu'en rév. 16c.
3. LUMORA allumées vues de face, puis à 45° et à 70° : les lentilles se lisent comme des points lumineux.
4. Se placer dans l'axe d'un VORTEX, ouvert puis avec gobo :
   - un point chaud de la taille de la lentille, un halo, une étoile discrète ;
   - le gobo visible dans la brume, pas un disque blanc.
5. PULSESTRIKE M de face et à 60° : les barres se lisent comme des tubes allumés.
6. FPS dans la vue de l'image 1 de la rév. 16d : comparer avec la rév. 16d.

## Fichiers modifiés

| Fichiers | Modification |
|---|---|
| `client/GpuMesh.java` | `cutout`, `flushSheets` ; suppression de `flushEarlyBlockLayers` ; texture des ombres |
| `client/TarpSheetRenderer.java`, `RegieRoofRenderer.java`, `RoofTrussRenderer.java`, `RiggingMeshes.java` | `GpuMesh.cutout` |
| `client/light/VolumeLights.java` | `forceBlend` avant chaque passe |
| `client/light/LightRenderTypes.java` | Mélange imposé |
| `client/light/BeamShadows.java` | Texture du test de transparence |
| `client/light/FixtureRenderer.java` | Lueurs des lentilles LUMORA, tubes PULSESTRIKE, lentille et étoile du VORTEX |
| `concertstage_beam_volume.fsh` | Adaptation et compression dans l'axe |
| `concertstage_light_projector.fsh`, `concertstage_light_composite.fsh` | Reflets plus fins |
| `tools/shader_harness/endon.py`, `pulse_row.py` | Nouvelles scènes du banc d'essai |
