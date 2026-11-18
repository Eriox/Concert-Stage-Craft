# Guide de Concert Stage Craft

## 1. Avant de commencer

**Shimmer n'est pas nécessaire.** CSC éclaire lui-même les blocs, les truss et les bâches. Le rendu est le même avec ou sans Shimmer.

**FFmpeg est intégré** (1.2.1) : les murs LED lisent vidéos, GIF et images sans rien installer.

Sur un serveur, le serveur et tous les joueurs doivent avoir la même version du mod.

Pour piloter avec une vraie console, téléchargez aussi `CSC_Gobos_et_Patchs` sur la page du mod : il contient les profils des projecteurs et les gobos (partie 4).

## 2. Votre premier show avec la console LUMICORE X1

En cinq minutes : poser, patcher, programmer, enregistrer une cue, faire GO.

### Poser le matériel

1. Dans l'onglet **CSC Lumière**, prenez des projecteurs, une console **CSC LUMICORE X1** et le **CSC Light Patch Tool**.
2. Posez les projecteurs au sol, ou suspendus : visez le dessous d'un truss ou d'un bloc. Un projecteur posé contre le côté d'un bloc qui a quelque chose au-dessus et rien en dessous est aussi suspendu.
3. Posez la console (2 m de large). Le joueur qui la pose en est le propriétaire.

### Poser un projecteur n'importe où

- **Accroupi en le posant** : le projecteur va exactement où vous visez, sur un tube, un bord ou le dessus de n'importe quel bloc, tourné dans le sens de votre regard.
- **Accroupi sur le côté d'un bloc** : le projecteur est fixé sur ce côté, le faisceau droit hors du mur.
- Sans s'accroupir, un projecteur posé s'assoit sur le vrai dessus de ce qu'il y a dessous (une dalle, un truss).
- **Réglage fin** : clic droit sur le projecteur avec l'**outil d'angle** (sur un blinder ou une barre, bouton **Position…** dans l'écran d'angle). L'écran de position le déplace jusqu'à 2 blocs dans chaque sens (X, Y, Z) et le tourne (lacet, tangage, roulis). **Copier** garde la position dans l'outil ; accroupi + clic droit sur un autre projecteur la colle.
- La console vise un projecteur déplacé depuis sa vraie place.

### Patcher

- Le plus simple : dans la console, onglet **Projecteurs**, **Auto-patch (48 blocs)**. Tous les projecteurs libres autour sont ajoutés et numérotés.
- À la main, avec le **Patch Tool** : clic droit sur la console pour la choisir, puis clic droit sur chaque projecteur. Accroupi : dépatcher.
- Clic droit main vide sur un projecteur : son écran de patch (console, numéro, adresse, mode DMX).

### Programmer

1. **Sélectionnez** des projecteurs (onglet Projecteurs, ou touches 1 à 9 pour vos groupes).
2. Onglet **Programmer** : intensité, position, zoom, gobo, prisme, strobe… Seuls les réglages que ces projecteurs ont sont affichés. Clic droit sur un curseur : relâche la valeur.
3. Onglets **Couleur** (roue, curseurs RVB, couleurs enregistrées) et **Position** (pad pan / tilt). **Créer les positions standard** calcule centre de scène, public, fond, croisé… depuis la place réelle de chaque projecteur.
4. Onglet **Effets** : Cercle, Vague, Éventail, Poursuite, Pulsation… puis « Lancer sur la sélection ».

### Enregistrer et jouer

1. Onglet **Cues**, **Record** (touche R) : le programmer devient la cue suivante. Réglez son fondu, son délai, son enchaînement.
2. Onglet **Playback** : **GO** (Espace), Retour, Pause, Stop, Flash, faders. Grand Master et Blackout sont en bas de l'écran.
3. Onglet **Shows** : enregistrez votre show (16 par console), exportez-le en `.cscshow`, annulez avec Ctrl+Z.

### Viser avec le Focus Tool

Clic droit sur les projecteurs pour les sélectionner, puis visez l'endroit où les envoyer. Maj + clic droit dans le vide vide la sélection et rend les projecteurs à la console.

### Projecteurs traditionnels (PAR, Fresnel, PC, découpe)

- **CSC PAR P64**, **CSC FRESNEL F1** (bord doux), **CSC PC C1** et **CSC ELLIPSOIDAL E1** (découpe, bord net) : projecteurs halogènes à **un seul canal gradateur** dans tous les modes DMX.
- Pas de moteur : orientez-les avec l'**outil d'angle** (clic droit), qui met aussi une **gélatine** devant la lentille (10 couleurs).
- Patchez-les comme les autres : le gradateur de la console est leur seule commande.

### Blinders et projecteurs sans moteur

Clic droit sur un blinder avec l'outil d'angle : un écran avec un rapporteur règle son angle. **Copier** garde l'angle, et accroupi + clic droit sur un autre blinder le colle.

### Partager la console

Onglet Shows : ajoutez des opérateurs par leur nom, ou rendez la console publique. Les boutons de sécurité (BLACKOUT, STOP SHOW, SANS STROBE, LIMITE MOUV., REPOS, STOP AUTO) sont toujours en bas de l'écran.

## 3. La lumière sur la musique et l'AutoShow

La lumière suit la console audio CSC liée, sur la même horloge que le son, chez tous les joueurs.

### Mettre de la musique

1. Sur la console audio, **Importer MP3 / WAV…** : cochez des fichiers, puis **Importer la sélection** ou **Tout importer**. Le glisser-déposer de plusieurs fichiers marche aussi.
2. Dans la LUMICORE, onglet Projecteurs : **Relier la console audio la plus proche**.

### Placer des cues sur le morceau (Timeline)

- Onglet **Timeline** : « Prendre le titre en cours », puis Maj + clic pour placer un GO de la cue choisie. Glisser pour déplacer, clic droit pour supprimer.
- Un clic montre la lumière à cet instant, sans musique. Passez la timeline en **ARMÉE** pour qu'elle parte avec la musique.
- « Votre délai lumière » avance ou retarde vos lumières à vous seul, si votre son arrive en retard.

### Laisser l'AutoShow créer le show

1. Lancez un titre sur la console audio liée.
2. Onglet **AutoShow** : choisissez un style (Concert Pop, EDM Festival, Rock Live, Ambient, Personnalisé), l'intensité, le mouvement, les couleurs, le strobe maximal, ou aucun flash.
3. **Analyser le titre et générer** : vous obtenez une liste de cues « AutoShow » et sa timeline. C'est un show normal, que vous pouvez modifier et enregistrer.
4. Ou **Lancer l'AutoShow en direct** : il suit la musique pendant qu'elle joue.
5. **STOP AUTO** l'arrête tout de suite. Votre programmation à la main passe toujours par-dessus.

L'analyse est une estimation : elle ne reconnaît pas parfaitement toutes les structures de morceau. Retouchez le résultat si besoin.

## 4. Piloter avec une vraie console (Art-Net)

Une console ou un logiciel lumière (grandMA, dot2, QLC+, Onyx…) peut piloter les projecteurs par Art-Net, en quatre étapes.

| Objet | Rôle |
| --- | --- |
| **CSC ART-NET NODE** (bloc) | Reçoit l'Art-Net. Les projecteurs patchés dessus suivent votre console. |
| **Carte de configuration** (objet) | Donne à chaque projecteur son adresse quand vous cliquez dessus. |
| Clic droit main vide sur un projecteur | Écran « Patch DMX » : univers, adresse, mode, « Suivante libre ». |

1. **Posez le node**, clic droit dessus. Mettez l'IP du PC qui fait tourner votre console (ou « Any sender »), le port **6454** et les univers reçus. **Apply & save**. L'état réel s'affiche : EN ÉCOUTE, RÉCEPTION, SOURCE FILTRÉE…
2. **Réglez la carte** : clic droit dans le vide avec la carte. Choisissez l'univers, le mode DMX, l'adresse de départ et l'incrément auto, puis **Enregistrer**.
3. **Liez la carte** : clic droit sur le node avec la carte.
4. **Patchez** : clic droit sur chaque projecteur avec la carte. La carte passe seule à l'adresse suivante et prévient si deux projecteurs se chevauchent.

### Les modes DMX

- **Full** : tous les canaux. Sur les projecteurs à pixels, chaque cellule a sa couleur et son intensité.
- **Compact** et **Ultra Compact** : moins de canaux, pour mettre beaucoup de projecteurs dans un univers.
- La couleur est en rouge / vert / bleu dans tous les modes. Le nombre de canaux de chaque mode est dans `DMX_CHARTS.md`.
- Tilt des lyres : DMX 128 = tout droit (vers le bas quand le projecteur est suspendu), ±135° autour.

### Les profils pour votre console

Dans `CSC_Gobos_et_Patchs` : profils GDTF (grandMA3, Capture, Vectorworks…), grandMA2, dot2, QLC+ et ChamSys MagicQ, avec les gobos. Importez le profil du **même mode** que celui choisi sur la carte, sinon les adresses ne correspondent pas.

### Si rien ne bouge

Vérifiez d'abord que la **sortie Art-Net est activée** sur votre console : une ligne de sortie désactivée n'envoie rien du tout.

- Regardez l'état sur le node. SOURCE FILTRÉE : l'IP réglée n'est pas celle de votre console.
- Rien ne s'affiche : autorisez Java (Minecraft) dans le pare-feu Windows pour le port UDP 6454.
- « External control: OFF » sur le node rend la main au programmer de la console du jeu.

### grandMA2 / grandMA3

- Ces consoles envoient l'Art-Net depuis une adresse en **2.x.x.x** construite sur l'IP du PC (2.168.178.61 pour 192.168.178.61). Depuis la 1.2.1, le node l'accepte tout seul.
- Si le node affiche encore « SOURCE FILTERED », cliquez sur **Accept this sender** sous l'adresse refusée, ou sur **Any sender**, puis **Apply & save**.
- Sur grandMA2, laissez **Network** à 1 (sa valeur la plus basse), avec Subnet 0 et Universe 0.
- Patchez le projecteur dans Minecraft sur l'univers 0 à la même adresse que dans grandMA (1.001 = univers 0, adresse 1), dans le même mode DMX que le profil importé.

## 5. Écrans LED et caméras (LiveVision)

Un mur LED, un processeur et une caméra suffisent pour filmer votre scène en direct sur un écran.

### Monter un mur LED

1. Posez des **CSC LED PANEL P1** côte à côte, dans un même plan et tournés du même côté. Ils forment seuls **un seul mur** (96 × 96 panneaux au plus).
2. Posez un **CSC VISION PROCESSOR VP1** : il prend les murs et caméras proches. Un mur se lie seul au processeur le plus proche.
3. Clic droit sur un panneau : nom, luminosité, processeur et sortie (OUT 1 à 4), bouton « Source ▸ ».
4. Accessoires : clic droit sur un panneau avec la barre d'accroche, le pied de sol ou le support mural en main.

### La régie (clic droit sur le VP1)

- Moniteurs **PREVIEW** (vert) et **PROGRAM** (rouge) pour chacune des 4 sorties.
- Clic sur une source pour la mettre en preview, Maj + clic pour l'envoyer directement à l'antenne.
- **TAKE** passe la preview à l'antenne avec la transition choisie (CUT, DISSOLVE, FADE). **CUT** coupe net.
- Commutation automatique entre les caméras, toutes les 2 à 120 s.

### Les caméras

- CAM PTZ P1, BROADCAST B1, CRANE C1, REMOTE R1. La PTZ et la Remote se suspendent sous un bloc.
- Clic droit : pan, tilt, zoom, et 8 positions (clic pour rappeler, Maj + clic pour mémoriser).
- Boutons **Qualité** (Auto, 360p à 1080p) et **Img/s** (Auto, 15 à 60). Une valeur fixée est tenue même si le jeu ralentit. Restez sur Auto si votre PC peine.
- Une caméra posée va sur la sortie 1.

### Vos images et vidéos

- **Images** (PNG, JPG, GIF animé) : dans `<monde>/csc_livevision/media/`. Le serveur les envoie aux joueurs (16 Mo par fichier au plus).
- **Vidéos** (MP4, MOV, WEBM, MKV, AVI…) : chaque joueur doit avoir le fichier, sous le même nom, dans `config/concertstage/livevision/media/`. FFmpeg est intégré au mod : rien à installer.

## 6. Truss, barrières et enceintes

### Truss courbes et circulaires

- **CSC Curved QuadTruss** et **CSC Curved TriTruss** : rayon de 1 à 20 m, angle de 5 à 355°, courbe à gauche, à droite, vers le haut ou vers le bas, finition alu ou noire. Réglage par clic droit avec l'outil Ring.
- **Truss circulaire** : diamètre de 2 à 20 m, section QUAD ou TRI. Vos anciens cercles se chargent tels quels.
- Les projecteurs s'y accrochent et les moteurs (lifts) marchent comme sur les autres truss.

### Barrières et garde-corps

- **Barrières en angle** (CrowdGuard, CityLine, AluGuard, clôtures SiteFence) : posez-les avec l'outil d'angle, comme les truss. Une barrière en angle n'a pas de collision : on passe au travers.
- **Garde-corps sur une plateforme en angle** : ils se fixent au bord le plus proche et partent avec la plateforme.
- **Garde-corps au sol** : les droits et ceux d'angle se rejoignent sur une même ligne. Si un angle ne ferme pas le coin, tournez-vous et reposez-le : son sens dépend de la direction dans laquelle vous regardez.

### Nouvelles enceintes AUREX

- Line arrays **LA12** et **LA8** : accrochés avec leur fly frame sous une structure ou un palan, au sol avec leur ground frame, ou sur une pile de subs S218-F.
- **Adaptateurs** pour poser les line arrays Orvane, Veyra et Noryx sur les subs de leur marque.
- **Système portable** : un sub B18P, un mât, et 2 enceintes C6 au plus.
- On ne peut plus ajouter ni retirer un sub sous une pile qui porte quelque chose. Retirez d'abord ce qu'il y a dessus.
- Ces enceintes n'ont pas de recette : mode créatif ou `/give`.

## 7. Réglages et performances

Tous ces réglages ne changent que votre écran, jamais le show des autres.

| Où | Réglage | À quoi ça sert |
| --- | --- | --- |
| Touche **K** (ou menu Mods > Concert Stage Craft > Config sur Forge) | Portée des faisceaux (25 à 300 %) | Longueur des faisceaux et portée de leur lumière sur les blocs |
| Touche **K** | Distance des blocs (25 à 200 %) | Distance d'affichage des blocs du mod |
| Touche **K** > LiveVision… | Profil LOW à ULTRA | Distance des murs LED, taille et images/s des caméras, mémoire des médias |
| LUMICORE, onglet Shows | Qualité des faisceaux | Faible, Moyenne, Élevée |
| LUMICORE, onglet Shows | Performance auto | Baisse le détail des faisceaux quand les images ralentissent |
| LUMICORE, onglet Shows | Brume, Rés. brume | Visibilité des faisceaux dans l'air, et leur résolution |

### Si votre jeu ralentit

1. Regardez « T: » dans l'écran F3 : c'est la limite d'images par seconde de Minecraft. À 20, le jeu ne dépassera jamais 20 images/s : montez **Images max** dans Options > Vidéo.
2. Laissez **Performance auto** activée.
3. Passez la qualité des faisceaux en Moyenne ou Faible, et la résolution de la brume en Moitié.
4. Baissez la portée des faisceaux et le profil LiveVision (moins de caméras actives, moins d'images par seconde).
5. Laissez les caméras en Qualité et Img/s **Auto**.

## 8. Problèmes fréquents

| Problème | Solution |
| --- | --- |
| Impossible de rejoindre le serveur | Le serveur et le joueur doivent avoir la même version du mod. |
| Un projecteur ne s'allume pas | Vérifiez qu'il est patché (clic droit main vide dessus), que le Grand Master est monté et que le Blackout est coupé. |
| Un joueur ne voit pas les lumières | Il doit être à moins de 768 blocs de la console. Les projecteurs ne s'affichent pas au-delà de la distance de rendu de Minecraft. |
| Les faisceaux s'arrêtent trop tôt | Montez la portée des faisceaux (touche K). |
| Fabric : pas de bouton de réglages dans le menu Mods | Utilisez la touche **K**. |
| Le node ne reçoit aucun paquet | Activez la sortie Art-Net sur votre console (grandMA2 : Setup > Network Protocols > Art-Net ; grandMA3 : Menu > DMX Protocols > Art-Net). |
| Un wash ou un spot répond en teinte / saturation au lieu de RGB | Un show d'avant la 1.2.0 gardait ses anciens modes ; la 1.2.1 les passe en RGB au chargement. Si un projecteur le fait encore, repatchez-le avec la carte (même mode). |
| Le node affiche « SOURCE FILTERED » | Cliquez sur « Accept this sender » sur le node (partie 4). |
| Les adresses ne correspondent pas sur la console externe | Le profil importé doit être du même mode (Full, Compact, Ultra Compact) que celui de la carte de configuration. |
| Caméra « NO SIGNAL » ou ciel seul | Attendez une dizaine de secondes : la caméra réessaie seule. |
| Une vidéo ne s'affiche pas | Chaque joueur doit avoir le fichier, sous le même nom, dans `config/concertstage/livevision/media/` (partie 5). |
| Impossible de retirer un sub | Une pile qui porte quelque chose ne change plus : retirez d'abord ce qu'il y a dessus. |
| Un garde-corps d'angle ne ferme pas le coin | Cassez-le, tournez-vous et reposez-le. |
| Le jeu est bloqué à 20 images/s | « T: 20 » dans le F3 : montez Images max dans Options > Vidéo (partie 7). |

Un autre souci ? Notez votre version de Forge ou de Fabric et vos autres mods, et envoyez le fichier `logs/latest.log` avec votre message.
