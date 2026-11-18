# CSC 1.2.0 rév. 16f — la vraie cause des carrés noirs

**Statut : cause trouvée dans le code du jeu et corrigée ; vérifié hors jeu (vérification de types). PAS vérifié dans Minecraft.**

## Ce que montrent les captures de 22 h 12 – 22 h 17

Carrés noirs autour de **chaque** lentille allumée (VORTEX au sol, LUMORA W19 sur le truss, rangée de wash), avec la lueur au centre. C'est pire qu'en rév. 16d.

## Cause (établie)

Les lumières des projecteurs sont dessinées avec le shader « eyes » du jeu :
- halos des lentilles ;
- lueurs des cellules ;
- blooms ;
- rayons de l'étoile ;
- émetteurs.

Le fichier json de ce shader dans Minecraft 1.20.1 (`rendertype_eyes.json`) déclare son propre mélange : **src alpha / one minus src alpha**. Vérifié dans le jar 1.20.1.

`BlendMode.apply` (vérifié dans le bytecode 1.20.1) :
- n'applique le mélange d'un shader que s'il est **différent du dernier mélange enregistré** ;
- quand il l'applique, il **remplace** le mélange que le type de rendu venait de régler (additif ou « screen ») ;
- n'enregistre un mélange que lorsque l'on passe d'opaque à non opaque ou l'inverse.

La texture des halos est opaque : alpha 1 partout, noire autour de la lueur. Avec src alpha / one minus src alpha, chaque halo **remplaçait l'image** par son carré noir.

Pourquoi c'est apparu, puis empiré :
- **Avant la rév. 16d**, le premier dessin de lumière après un shader opaque était la lumière projetée (pas visible si elle remplaçait). Les suivants tombaient sur le mélange déjà enregistré, et le shader ne touchait plus au mélange.
- **Rév. 16d** : la lumière projetée est passée dans un autre shader ; un halo est devenu le premier dessin, donc noir.
- **Rév. 16e** : la remise à « opaque » avant chaque passe (`forceBlend`) a rendu **chaque** halo noir. D'où « c'est pire ».

## Correction

Les types de rendu de lumière (`LightRenderTypes`) appellent `VolumeLights.lightTypeBlend` dans leur réglage :
1. le mélange enregistré par le jeu est mis à celui du shader eyes ;
2. le shader ne réapplique donc pas le sien ;
3. puis le mélange voulu est réglé : additif pour les émetteurs, « screen » pour les halos, multiplication pour la lumière projetée.

Après le dessin groupé des lumières, l'enregistrement repasse à « opaque ». Ce garde-fou existe depuis la rév. 15b : sans lui, la vignette de l'écran devient un écran gris.

Les passes du moteur avec leurs propres shaders gardent `forceBlend` : leurs fichiers json déclarent exactement le mélange voulu. Ce sont les faisceaux dans la brume, la lumière sur les surfaces, le composite et le voile.

## Ce qui reste de la rév. 16e

Ces changements restent :
- bâches et toits dessinés à part avant la lumière ;
- texture des cartes d'ombre corrigée ;
- lentilles LUMORA visibles de biais ;
- regard dans un VORTEX ;
- tubes du PULSESTRIKE M.

## À vérifier en jeu

1. Les trois vues des captures de 22 h : plus aucun carré noir.
2. Halos, lueurs et étoile du VORTEX s'ajoutent à l'image.
3. La vignette de l'écran reste normale : pas d'écran gris en marchant au sol.
