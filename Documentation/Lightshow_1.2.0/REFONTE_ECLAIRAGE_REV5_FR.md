# CSC 1.2.0 — Refonte du rendu lumineux (révision 5)

Référence demandée : le rendu de Theatrical ExtraLights (image 2 du cahier des charges).

> **État de vérification.** Rien de ce document n'a été vu en jeu : je ne peux pas lancer Minecraft dans mon environnement.
> Ce qui est vérifié : la vérification de types du module commun contre les classes de Minecraft 1.20.1, la syntaxe Java,
> 137 tests hors jeu (dont 20 sur l'optique des faisceaux) et une simulation hors jeu de la géométrie dessinée.
> Les tests comparatifs A à F en jeu restent à faire (protocole au § 7).
> Aucune mesure de FPS n'est inventée : les chiffres de performance ci-dessous sont **calculés** (nombre de sommets), pas mesurés.

## 1. Problèmes identifiés dans le moteur actuel (révisions 3 et 4)

| Problème visible (image 1) | Cause dans le code |
|---|---|
| Grands triangles lumineux qui traversent la scène | Les faisceaux étaient des cônes maillés presque aussi lumineux au bout qu'à la lentille (atténuation 1/(1 + 0,025·d)). Un plancher de luminosité (0,1) éclairait aussi les bords vus de profil. Un wash à 50–60° gardait donc toute sa force sur 20 blocs. |
| Blanc surexposé, sans nuance | Mélange additif pur : chaque couche s'ajoute, et les faisceaux superposés finissent au blanc. En simulation, 6 à 15 % des pixels de l'image étaient saturés. |
| Surfaces blanches plates, décors sombres | L'éclairage des surfaces se limitait à un carré additif au point d'impact central. Il ajoutait une couleur uniforme par-dessus la texture au lieu de l'éclairer, et rien n'était éclairé hors du centre (instruments, fond de scène). Shimmer recevait une seule lumière par faisceau. |
| Coût élevé (≈ 12,5 ms/frame pour 52 fixtures sur tes captures) | Deux cônes de 18 × 8 facettes émis sur les deux faces, soit 2 304 sommets par wash, avec `Math.pow` à chaque sommet. Chaque projecteur changeait aussi de type de rendu 3 ou 4 fois, ce qui vidait le tampon partagé du jeu à chaque changement. |

## 2. Étude de Theatrical ExtraLights

**Licence** : MIT, © 2025 Stuart Pomeroy (fichier `LICENSE` de l'archive). La réutilisation serait permise avec mention, mais **aucun fichier ni aucune classe n'a été copié** : CSC a sa propre implémentation, adaptée à son architecture.

Mécanismes qui produisent l'image 2 :

- **Faisceau volumétrique** (`StackedVolumeMesh`, `VolumetricBeamRenderer`, shader `volumetric_beam.fsh`) :
  - le cône est rempli de disques empilés le long du faisceau, plus deux plans longitudinaux croisés et une coque légère à 8 côtés ;
  - l'opacité de chaque tranche vaut `exp(−d/L·densité) × 1/(1 + (d/d_ref)²)`, avec un fondu à la fin quand le faisceau ne touche rien ;
  - le shader calcule un profil radial adouci (`smoothstep`), ce qui supprime toute arête visible ;
  - le maillage est mis en cache selon un hash de l'état du projecteur ;
  - un mode « raymarch » optionnel existe.
- **Couleurs et intensité** : `pow(intensité, 0,45)` pour l'opacité, couleur pure éclaircie vers le blanc au centre de la lentille.
- **Éclairage des surfaces** (Theatrical `LightManager`, ExtraLights `BeamSpotLighting`) :
  - avec Shimmer : une lumière colorée ponctuelle au point où le faisceau arrive, de rayon égal à la section du cône à cette distance ;
  - sans Shimmer : une « lumière dynamique » façon LambDynamicLights. Le niveau de lumière des blocs est relevé autour du point d'impact, ce qui demande des mixins sur le rendu du monde et la reconstruction des chunks voisins. Cette lumière est blanche.

  **Sur l'image 2, la lumière colorée sur les truss et les instruments vient de Shimmer.**
- **Grand nombre de projecteurs** : maillages en cache, rendu différé (`LazyRenderers`) regroupé par type de rendu.

## 3. Techniques retenues pour CSC

Le principe d'ExtraLights que CSC reprend : **séparer la lumière dans l'air (le faisceau) de la lumière sur les surfaces**.
Le modèle est dans une classe pure, `light/core/BeamOptics.java`, testée hors jeu.

### 3.1 Faisceaux dans l'air (`BeamRenderer.softBeam`)

- **Ruban doux** : une bande le long de l'axe, tournée vers la caméra à chaque anneau et aussi large que le cône à cet endroit. Sa texture porte un profil radial lisse (`beam_soft.png`, 128 px).
  - De profil, on voit un volume rond de lumière dans la brume, sans facette, sans arête et sans triangle.
- **Disques transversaux** : ils sont visibles quand on regarde dans l'axe (face au projecteur ou derrière lui).
  - Le ruban et les disques sont pondérés selon l'angle de vue (`sideWeight` / `axisWeight`). Le faisceau reste donc visible de face, de côté et de dos ; c'est vérifié par un test.
- **Loi de luminosité** `air(d)` :
  - démarrage doux à la lentille ;
  - diffusion en 1/r^0,85, où r est le rayon du cône : un faisceau serré porte loin, un wash large brille près de l'appareil puis s'éteint ;
  - absorption exponentielle de la brume ;
  - fin douce quand le faisceau ne touche rien.
- **Mélange « screen »** `src + dst × (1 − src)` : les faisceaux superposés s'éclaircissent sans jamais brûler au blanc.
- **Comportements par projecteur** :
  - **VORTEX X1** : un corps doux, plus un cœur dense quand le zoom est serré (aspect Beam) qui disparaît quand le zoom s'ouvre (aspect Spot). Le gobo est projeté sur la surface et ses brins restent visibles dans l'air en qualité Élevée et Ultra.
  - **LUMORA W7 / W19** : un corps large et doux ; le W19 est plus puissant.
  - **PULSESTRIKE M** : aucun faisceau en usage normal. Un flash éclaire seulement quelques blocs de brume devant la face. Les flashs de la révision 4 sont conservés.

### 3.2 Lumière sur les surfaces

- **Échantillonnage** : quelques rayons sondent ce qui se trouve sous le faisceau (sol, plateformes, instruments, truss, bâches, fond de scène).
  - Il y a 1 rayon pour un faisceau serré, et 1 / 7 / 13 rayons pour un wash large en qualité Faible / Élevée / Ultra.
  - Le résultat est gardé plusieurs images. On rééchantillonne quand la tête a tourné d'un dixième du cône, ou au bout de 8 à 30 images, dans un budget de rayons par image.
- **Lumière projetée multiplicative** (`LightRenderTypes.modulate`, `dst × (1 + src)`) :
  - elle multiplie ce qui est déjà dessiné : la surface garde sa texture et prend la couleur de la lumière ;
  - le noir reste noir, et un disque qui déborde d'un bord n'allume rien dans le ciel ;
  - la force tient compte de la direction, de l'intensité, de l'ouverture (la lumière s'étale sur l'empreinte) et de la distance (`BeamOptics.surface`) ;
  - elle tient aussi compte de la lumière déjà présente sur le bloc (`relight`) : la nuit, la même lumière se voit beaucoup plus qu'en plein jour. Le facteur est plafonné à ×12, sans brûlure.
- **Shimmer, si installé** : une lumière au centre de l'empreinte, de rayon égal à la section du cône (comme ExtraLights).
  - Un wash large reçoit en plus 2 lumières (Élevée) ou 4 (Ultra) réparties sur son empreinte.
  - La lumière devant la lentille de la révision 4 est conservée.
  - Avec Shimmer, la projection est réduite à 45 % pour ne pas compter la lumière deux fois.

### 3.3 Pourquoi pas la « lumière dynamique » de blocs de Theatrical

Ce compromis est volontaire :
- elle demande des mixins dans le rendu du monde (CSC n'en a aucun) et la reconstruction de chunks à chaque mouvement de lyre, ce qui est coûteux avec des dizaines de têtes mobiles ;
- elle est blanche ;
- elle risquerait d'entrer en conflit avec Theatrical, qui modifie déjà la même méthode.

Ce qui la remplace :
- sans Shimmer, la projection multiplicative ;
- avec Shimmer, un vrai éclairage coloré, comme sur l'image 2.

### 3.4 Performances

- **Géométrie** : un wash en qualité Élevée passe de **2 304 sommets (rév. 3)** à **92 sommets (rév. 5)**, et un VORTEX à 104. Même en Ultra, un faisceau reste sous 212 sommets. Ces nombres sont **calculés** et vérifiés par test.
- **Regroupement** (`LightBatch`) : les projecteurs n'écrivent plus dans les tampons du jeu. Ils enregistrent leurs sommets, envoyés **en un seul draw par type de rendu** après les block entities, au même moment que les maillages GPU du mod. Il n'y a plus de vidage de tampon à chaque projecteur.
  - Les tableaux sont réutilisés d'une image à l'autre : aucune allocation une fois qu'ils ont atteint leur taille.
- **Autres réductions** :
  - plus de `Math.pow` par sommet ;
  - les rayons de surface et les impacts sont mis en cache ;
  - le niveau de détail baisse avec la distance (Ultra < 24 blocs, Élevée < 40, Moyenne < 64, Faible au-delà) ;
  - le culling hors champ est conservé.
- **4 niveaux de qualité** : Faible, Moyenne, Élevée, Ultra, dans la console et dans `beam_quality` 0 à 3 du fichier de configuration. Le mode automatique peut descendre d'un niveau quand l'image dépasse 22 ms.
- **F3** : une nouvelle ligne `light (1.2.0): N vertices/frame in M grouped draws`.
  - Attention : le temps « Fixtures » n'inclut plus l'envoi au GPU, qui est maintenant compté dans « sent ». Pour comparer avant et après, utiliser la ligne `total of the mod`.

## 4. Fichiers modifiés

- `light/core/BeamOptics.java` (nouveau) : modèle optique pur, testé.
- `client/light/BeamRenderer.java` : `softBeam` (ruban + disques), halo, projection.
- `client/light/FixtureRenderer.java` : nouvelle méthode `beam` (air + surfaces), échantillonnage `sampleSurfaces`, `relight`, Shimmer réparti, qualité selon la distance, 4 niveaux.
- `client/light/LightRenderTypes.java` : types additif, screen et modulate.
- `client/light/LightBatch.java` (nouveau) : regroupement des draws.
- `client/GpuMesh.java` : envoi de la lumière après les maillages opaques.
- `client/RenderStats.java` : ligne F3.
- `light/FixtureBlockEntity.java` : cache des impacts et clés des lumières Shimmer.
- `client/light/LightClient.java`, `LumicoreScreen.java`, `tools/lightshow/light_lang.py` : niveau Ultra.
- `textures/lightshow/beam_soft.png` (nouveau, généré).
- Tests : `tools/lightshow_tests/LightshowTest.java` (section optique).
- Outils : `tools/lightshow/simulate_rev5.py`, `tools/typecheck/`.

**Non touchés** : les modèles Blender, la console, l'AutoShow, les cues, presets et groupes, la synchronisation musicale, les truss et les fixations.

## 5. Comparaison visuelle (simulation hors jeu)

Fichiers : `rev5_simulation_face_avant_apres.png`, `rev5_simulation_cote_avant_apres.png`, `rev5_simulation_haut_avant_apres.png` (rév. 3 à gauche, rév. 5 à droite).

La scène simulée comporte 6 W19 ambrés à 30°, 4 VORTEX à 4°, un sol, un fond de scène, deux truss et deux boîtes « instruments ».

| Vue | Luminance moyenne rév. 3 → rév. 5 | Pixels saturés (> 0,97) rév. 3 → rév. 5 |
|---|---|---|
| Face | 0,133 → 0,056 | 10,6 % → 0,2 % |
| Côté | 0,102 → 0,056 | 6,0 % → 0,5 % |
| Haut | 0,175 → 0,066 | 14,5 % → 0,5 % |

**Limites de la simulation** :
- ce n'est pas Minecraft : pas de textures du jeu, pas de brouillard, pas de Shimmer ;
- elle n'a pas de tampon de profondeur, donc la lumière projetée peut apparaître sur un objet qui devrait la masquer ;
- elle ne remplace pas une capture en jeu ni une comparaison avec l'image 2.

## 6. Logs

Dans `validation/` :
- `typecheck-common-1.2.0-rev14.log` : 45 erreurs, toutes liées à l'environnement (LWJGL, Netty, Authlib, interfaces injectées par Forge/Fabric, stubs JOML), aucune dans le code de la lumière. Un test volontaire a confirmé que les erreurs de type sont bien détectées dans `FixtureRenderer`.
- `java-syntax-1.2.0.log` : 251 sources.
- `lightshow-tests-1.2.0.log` : 137/137.
- `simulation-rev5.log`.

Les builds Gradle Forge et Fabric restent à lancer de ton côté.

## 7. Protocole des tests en jeu (A à F)

Pour chaque test, noter dans le F3 les lignes `total of the mod`, `light (1.2.0)` et `Fixtures`, ainsi que les FPS. Tester d'abord sans Shimmer, puis avec Shimmer, puis avec Shimmer + ExtraLights.

- **A** : même scène, ambre (R 255, V 160, B 64) sur des W19 et sur des projecteurs ExtraLights équivalents, de nuit. Comparer à l'image 2.
- **B** : un VORTEX serré, un VORTEX ouvert et un W19 vus de face, de côté et de dos. Vérifier qu'il n'y a ni triangle ni bord dur, et que le faisceau est visible sous tous les angles.
- **C** : instruments, plateformes, truss et fond de scène éclairés (avec et sans Shimmer).
- **D** : 10, 25, 50 et 100 projecteurs allumés, en Élevée puis en Ultra.
- **E** : Shimmer + ExtraLights ensemble. Vérifier la profondeur, le scintillement et les couleurs.
- **F** : comparer la rév. 4 et la rév. 5 dans les mêmes conditions (même position, même show, même heure du jeu).

## 8. Limitations restantes

- **Aucun test en jeu** (rendu, FPS, mémoire, compatibilité).
- **Pack de shaders (Iris / Oculus)** : le regroupement se désactive et les sommets vont directement dans les tampons du jeu. Selon le pack, les modes de mélange « screen » et « modulate » peuvent être remplacés par ceux du programme du pack.
- **Sans Shimmer** : la lumière projetée éclaire une surface par rapport à sa texture, donc une texture noire reste noire. Elle suit les faces des blocs touchés par les rayons, pas la forme exacte des modèles 3D : un instrument à modèle complexe reçoit la lumière sur la face de son bloc.
- **Les rayons de surface ne sont pas recalculés à chaque image** : pendant un mouvement rapide, la lumière au sol peut suivre avec quelques images de retard. Le rayon central reste prioritaire.
- **Shimmer** : son nombre de lumières est partagé avec ExtraLights. Le budget CSC est réglable (`shimmer_max_lights`, 64 par défaut).
- **Couleurs** : la couleur vient du show. L'ambre de l'image 2 s'obtient avec une palette ambrée ; rien n'est réchauffé automatiquement.

---

# Révision 6 — finalisation (5 images de référence ExtraLights)

> Même état de vérification que la révision 5 : **rien n'a été vu en jeu**. Vérifié : types du module commun contre Minecraft
> 1.20.1, syntaxe, 140 tests hors jeu.

## R6.1 Analyse des cinq images de référence

| Image | Ce qui produit le rendu | Conséquence pour CSC |
|---|---|---|
| 1 — violet, gobos | Gobos nets sur le mur de gauche (points), truss entièrement teinté en violet, faisceaux à peine visibles dans l'air. | La couleur vient de lumières Shimmer posées près des projecteurs et aux impacts, pas des faisceaux. Le gobo n'existe que sur la surface. |
| 2 — bleu / blanc, gobos | Faisceaux blancs fins mais diffus, en grand nombre, avec un halo autour de chaque faisceau. Gobos en points blancs nets sur le mur, déformés par l'angle d'incidence. Truss clairs. | Il faut un cœur plus un halo diffus autour de chaque faisceau, et un gobo étiré selon l'incidence. |
| 3 — rose, vue lointaine | Toute la structure est rose. Pas de surexposition : les détails sombres restent lisibles. | La lumière doit être multipliée sur les textures, pas ajoutée en aplat. Le plafond évite la brûlure. |
| 4 — ambre / rouge, vue de la régie | Structure chaude, fond de scène éclairé, faisceaux faibles à distance. | Le wash doit éclairer de grandes surfaces, et le faisceau doit s'atténuer avec la distance. |
| 5 — ambre, instruments | Instruments et plateformes éclairés en ambre, faisceaux de wash doux en dégradé. | Les surfaces doivent être échantillonnées et éclairées (révision 5), et la lumière doit se répandre autour des projecteurs. |

Point commun aux cinq images : la richesse vient de **la lumière posée sur les structures** (Shimmer), beaucoup plus que des cônes dans l'air.

## R6.2 Corrections

- **Lentilles des W7 / W19 sombres : cause trouvée et corrigée.** J'ai mesuré les modèles exportés : la lentille blanche (le dôme) dépasse de 1,2 à 1,9 cm (unités modèle, selon le niveau de détail) devant le plan où la lueur des cellules était dessinée. La lueur était donc cachée derrière une lentille opaque, alors que le faisceau était bien coloré. Même problème pour la source du VORTEX (5,8 à 6,2 cm). Les lueurs sont maintenant posées devant la géométrie la plus avancée de chaque cellule (`FixtureMeshes.frontZ`, calculé sur tous les niveaux de détail), et le reflet et le halo de lentille aussi. **Les modèles Blender ne sont pas modifiés.**
- **Lentilles et matrices LED plus lumineuses** : chaque cellule allumée (W7 : 19, W19 : 37, PULSESTRIKE : 14 RGB et 28 blanches) reçoit en plus un rayonnement doux deux fois plus grand qu'elle, jusqu'à 32 blocs. Couleur et dimmer sont ceux de la cellule ; il est plus fort sur un flash du PULSESTRIKE.
- **PULSESTRIKE M agrandi de 30 %** : rendu à 2,60 au lieu de 2,00. Le pivot de tilt et l'objet en main suivent ; la hitbox reste limitée à un bloc.
- **Faisceaux** :
  - **VORTEX (Beam)** : cœur élargi et halo diffus autour du faisceau en qualité Moyenne et plus (image 2), pour qu'il ne ressemble plus à une ligne laser.
  - **Washes** : cœur plus dense dans le corps doux. Le W19 est un peu plus large et plus puissant que le W7.
- **Gobos (VORTEX)** :
  - le motif est étiré selon l'angle d'incidence (une projection oblique est une ellipse) ;
  - le **focus** est géré : net au milieu de la plage, flou de part et d'autre (motif dessiné en 3 couches de tailles proches) ;
  - une faible couche additive garde le motif lisible sur les matériaux très sombres (images 1 et 5) ;
  - le motif n'est dessiné que là où le rayon central touche une surface : jamais en l'air.
- **Structures colorées** : la lumière Shimmer devant chaque objectif est plus large (6 blocs, 7 pour le W19, 12 sur un flash) et plus forte. Le truss et la structure autour d'un projecteur allumé prennent sa couleur.

## R6.3 Limites restantes (en plus de celles de la révision 5)

- **Gobo sur plusieurs surfaces** : le gobo est projeté sur la surface touchée par le rayon central seulement. Une partie du motif qui déborderait sur une autre surface (marche, décor devant le mur) n'y est pas reportée. Hors du bord de la surface, la couche multiplicative n'éclaire rien ; la faible couche additive peut dépasser un peu.
- **Changement de gobo** : le changement est instantané (la roue de gobos n'est pas animée entre deux motifs).
- **Occultation partielle** : un wash large partiellement masqué par un objet n'est coupé qu'aux endroits sondés par les rayons de surface. Le faisceau dans l'air s'arrête à l'impact du rayon central.
- **Sans Shimmer, pas de structure entièrement colorée** comme sur les images de référence : seuls les impacts des faisceaux sont éclairés. Ces images reposent sur Shimmer.

---

# Révision 8 — reflets sur le rig et faisceaux (repart de la révision 6)

> Base : l'archive envoyée (= révision 6). Les changements de la révision 7 n'y étaient pas.
> **Repris ici** : ce qui améliore les faisceaux et les impacts (fondu près de la caméra, fin adoucie, washes moins denses dans l'air, VORTEX plus large avec halo, impacts étirés, spot ouvert doux).
> **Pas repris** : les quatre niveaux de brouillard. Le réglage « Brume » de la révision 6 reste en place.
>
> **NON VALIDÉ DANS MINECRAFT.** Vérifié : types du module commun contre Minecraft 1.20.1, syntaxe, 149 tests hors jeu.

## R8.1 Ce que montrent les images de référence

Truss, toit, bâches et plateformes prennent la couleur des projecteurs sur toute la structure, pas seulement là où arrive un faisceau. Ces images ont été faites avec Shimmer : chaque projecteur ExtraLights y pose une lumière colorée autour de lui.

Avant cette révision, CSC n'éclairait la structure qu'à trois endroits :
- l'impact du faisceau ;
- une lumière Shimmer de 6–7 blocs devant l'objectif ;
- avec un budget de 64 lumières dont seulement 60 % étaient disponibles pour ces lumières autour des projecteurs.

Avec 50 projecteurs, une bonne partie n'avait donc pas de lumière Shimmer du tout.

## R8.2 Nouveau : lumière diffuse sur le rig (« spill »)

- **Principe** : chaque projecteur allumé éclaire ce qui l'entoure, en plus de son faisceau.
- **Rayons** : 14 rayons partent du projecteur (haut, 4 côtés, 4 diagonales montantes, 4 diagonales descendantes, bas) et trouvent jusqu'à 8 blocs :
  - le truss auquel il est accroché ;
  - le toit et les bâches au-dessus ;
  - la plateforme dessous ;
  - les structures voisines.

  En qualité Faible / Moyenne, 5 / 9 rayons. Le résultat est gardé environ 6 secondes (un rig ne bouge pas). Les projecteurs voisins sont ignorés : leur bloc est une boîte invisible.
- **Lumière projetée** : chaque surface trouvée reçoit une lumière multipliée sur sa texture. Le truss, la bâche et la plateforme gardent leur aspect et prennent la couleur du projecteur.
  - Forte près du projecteur, nulle à 8 blocs, plus faible sous un angle rasant.
  - Les washes et les blinders éclairent plus leur rig qu'un VORTEX.
  - Plus forte sur un flash de strobe.
  - Fonctionne **sans Shimmer**.
- **Avec Shimmer** :
  - une lumière colorée est placée sur le rig au-dessus du projecteur (rayon 5 à 9 blocs) ;
  - la lumière devant l'objectif est élargie (7,5 à 9,5 blocs, 13 sur un flash) et renforcée ;
  - le budget passe à 160 lumières (Shimmer en accepte 2 048) ; les anciennes valeurs par défaut sauvegardées, 48 ou 64, sont migrées ;
  - ces lumières autour des projecteurs peuvent utiliser jusqu'à 75 % du budget ;
  - avec Shimmer, la projection CSC est réduite à 60 % pour ne pas compter la lumière deux fois.
- **F3** : une ligne indique si Shimmer est détecté, combien de lumières CSC sont actives, et si l'éclairage coloré est **désactivé dans la configuration de Shimmer**. Dans ce cas, Shimmer n'affiche aucune lumière, ni celles de CSC ni celles d'ExtraLights.

## R8.3 Faisceaux (tous les projecteurs)

- **Tous** :
  - fondu là où la caméra est dans le faisceau ou juste à côté (plus de mur de couleur) ;
  - fin du faisceau adoucie juste avant la surface touchée (plus de coupure droite par le sol) ;
  - disques transversaux presque supprimés pour les faisceaux larges.
- **VORTEX** :
  - rayon de sortie d'au moins 11 cm (5 cm avant : effet « laser ») ;
  - cœur précis qui suit le zoom ;
  - halo subtil à toutes les qualités.
- **W7 / W19** :
  - lumière dans l'air réduite quand le zoom s'ouvre (×0,46 à 50°) ;
  - cœur plus dense dans l'axe ;
  - W19 plus large et plus fort que le W7.
- **PULSESTRIKE** : pas de faisceau hors flash (inchangé).
- **Impacts** :
  - étirés selon l'incidence, dégradé jusqu'au bord ;
  - spot ouvert du VORTEX avec un bord défini mais doux (`spot_open.png`) ;
  - pas de couche additive sur le spot ouvert.

## R8.4 Limites

- **Pas de simulation** pour la lumière diffuse : la scène simulée n'a pas de vrai truss. Seul un test en jeu dira si l'intensité est juste.
- **Sans Shimmer** : seules les surfaces touchées par les rayons sont éclairées, sous forme de taches douces de 2 à 6 blocs. Shimmer éclaire de façon continue tout ce qui se trouve dans son rayon, donc sans Shimmer on n'obtient pas exactement l'aspect des images de référence.
- **Truss ajouré** : la lumière projetée sur un truss couvre aussi ses vides. Ce qui se voit à travers est multiplié, donc un fond noir reste noir, mais un fond déjà éclairé peut être un peu éclairci.
- **Coût GPU de Shimmer** : 160 lumières CSC représentent un coût GPU pour Shimmer, qui calcule chaque lumière pour chaque pixel. Si les FPS baissent, réduire `shimmer_max_lights` dans `config/concertstage-lighting.properties`.

---

# Révision 9 — lumières colorées CSC, iris du VORTEX, focus du PULSESTRIKE

> **NON VALIDÉ DANS MINECRAFT.**
> - Vérifié : types du module commun contre Minecraft 1.20.1, syntaxe Java, 154 tests hors jeu.
> - **Le nouveau shader GLSL n'a pas pu être compilé ici** (aucun compilateur GLSL disponible). S'il ne compile pas sur ta carte graphique, la fonction se coupe toute seule, le jeu continue, et la ligne F3 l'indique.

## R9.1 Ce que montrent tes captures (révision 8) face aux références (images 4 à 8)

- **Truss, toit et bâches restent sombres** (captures 2 et 3) : seuls le sol et les faisceaux sont colorés. Sur les références, toute la structure prend la couleur, y compris les faces avant et les côtés du truss, grâce aux lumières de Shimmer.
- **Corps des projecteurs voilés de rose** (capture 1). Cause trouvée dans le code de la révision 8 : un rayon de lumière diffuse partait déjà *à l'intérieur* du truss au-dessus du projecteur. Le jeu considère alors que la surface est touchée au point de départ, si bien que la lumière était projetée sur un grand carré vertical *dans* le truss, à travers les projecteurs suspendus. La lumière d'objectif était aussi trop proche du corps (0,9 bloc).
- **Faisceaux lointains** (capture 2) : ils deviennent des lignes dures d'un pixel qui scintillent.

## R9.2 Nouveau : les lumières colorées de CSC (sans Shimmer)

- **Principe** : chaque projecteur place déjà des points lumineux (devant l'objectif, sur le rig au-dessus, au centre et autour de l'empreinte de son faisceau). Sans Shimmer, CSC les dessine lui-même : **toutes les surfaces dans leur rayon s'éclairent dans leur couleur**, de face comme de côté (truss, toit, bâches, plateformes, instruments, murs).
- **Technique** :
  - après le dessin des objets opaques, la profondeur de l'image est copiée ;
  - chaque lumière est un quad à l'écran dont le shader (`assets/minecraft/shaders/core/concertstage_light_volume.*`) retrouve la position 3D de chaque pixel et calcule la distance à la lumière et l'orientation de la surface ;
  - il multiplie ensuite la couleur par `1 + lumière` en 3 passes : les textures gardent leur détail, le noir reste noir, le ciel n'est jamais touché ;
  - la lumière est plus forte la nuit (gain de 0,6 le jour à 5 la nuit).
- **Pas de mixin, pas de reconstruction de chunks, identique sur Forge et Fabric.** Au plus 384 lumières par image.
- **Avec Shimmer**, c'est Shimmer qui dessine ces lumières, comme avant.
- **Réglages** : bouton « Lum. scène » dans la console, ou `stage_lights` dans `config/concertstage-lighting.properties`.
- **F3** : nombre de lumières CSC dessinées, ou la raison de l'arrêt (shader pack, erreur de shader, réglage).

## R9.3 Corrections

- **Rayons de lumière** : ils partent maintenant du centre du projecteur et traversent son propre bloc et ceux des projecteurs voisins. Le truss est trouvé par sa vraie face, et un rayon qui démarrerait dans un bloc ignore ce bloc. Les faisceaux ne s'arrêtent plus non plus sur la boîte invisible d'un projecteur voisin.
- **Lumière d'objectif** : placée 2 blocs devant la lentille (0,9 avant), un peu moins forte. Elle éclaire le rig et la scène sans brûler le corps du projecteur.
- **Lumière du rig** : posée sur la face du truss lui-même (0,3 bloc), un peu plus douce, avec un rayon de 6 à 10 blocs.
- **Faisceaux lointains** : ils gardent environ 3 pixels de largeur douce (plus larges et moins intenses) au lieu d'une ligne dure.

## R9.4 Iris du VORTEX

- Fermer l'iris resserre l'angle (comme avant), mais aussi **la sortie du faisceau** (×0,45 fermé) et retient un peu de lumière (×0,7 fermé).
- **Iris ouvert et focus net** : le faisceau montre dans la brume le **bord défini de l'optique** (texture `spot_open`), comme un vrai spot.
- **Iris fermé ou focus flou** : un fût doux.
- Le halo diminue quand le bord est défini.

## R9.5 Focus du PULSESTRIKE M

- Nouvel attribut **Focus** : son curseur apparaît dans la console pour les PULSESTRIKE.
  - **0** : flood large à 70°, le comportement d'avant (valeur par défaut).
  - **1** : « punch » serré à 20°.
- **Effets d'un focus serré** :
  - le flash porte plus loin dans l'air (9 à 25 blocs) ;
  - au-delà de 0,3, un léger fût reste visible entre les flashs ;
  - l'impact au sol et les lumières suivent l'angle.
- L'AutoShow ne touche pas au focus des PULSESTRIKE.

## R9.6 Limites

- **Shader non compilé ici** : il ne peut être vérifié qu'en jeu. En cas d'échec, la fonction se coupe (log « coloured stage lights turned off ») et la projection de la révision 8 reste.
- **Pas d'ombres** : ces lumières n'ont pas d'ombres (comme celles de Shimmer). Elles éclairent tout ce qui est dans leur rayon, y compris derrière un mur fin.
- **Shader packs (Iris / Oculus)** : la fonction est désactivée.
- **Coût** : 3 passes par lumière, sur la surface d'écran qu'elle couvre. Une lumière qui entoure la caméra couvre tout l'écran. À mesurer avec le F3.
- **Lumière déjà présente** : la force est réglée sur l'obscurité du ciel, pas sur la lumière déjà présente sur chaque bloc. Une scène très éclairée par des lampes peut donc être un peu trop éclaircie.

---

# Révision 10 — gobos dans le truss, faisceaux jusqu'au sol, plus de volume

> **NON VALIDÉ DANS MINECRAFT.**
> - Vérifié : types du module commun contre Minecraft 1.20.1, syntaxe Java, 156 tests hors jeu.
> - Les deux shaders GLSL n'ont pas pu être compilés ici. S'ils ne compilent pas sur ta carte graphique, la fonction se coupe toute seule et la projection des révisions précédentes reprend.

Lecture de la demande :
- **« les gobos se confondent dans les truss »** (image 3) : le motif du gobo doit se poser sur les barres du truss et passer par ses vides jusqu'au mur derrière.
- **« les truss ne touchent pas le sol »** (image 1) : je l'ai lu comme « les faisceaux n'atteignent pas le sol ». La fin des faisceaux s'effaçait trop tôt. Si c'était autre chose, dis-le.

## R10.1 Projecteurs de lumière (gobo, spot, wash) sur toutes les surfaces

Nouveau shader `concertstage_light_projector` (même principe que les lumières de la révision 9) :
- chaque projecteur allumé projette sa lumière sur **tout ce qui est dessiné dans son cône** :
  - le **gobo** du VORTEX (ou son spot ouvert) ;
  - la lumière douce d'un wash ou d'un blinder ;
- la position de chaque pixel est retrouvée grâce à la profondeur, puis ramenée dans le repère du projecteur : à l'intérieur du cône, le motif s'applique, tourné par la roue de gobos, avec un flou selon le focus ;
- la lumière suit la même loi que l'éclairage des surfaces des révisions précédentes (distance, empreinte, incidence), multipliée sur les textures ;
- **pas d'ombres** : les barres du truss, leurs vides et le mur derrière reçoivent tous le motif, découpé par la géométrie. C'est l'effet de l'image 3 ;
- la portée s'arrête 1,5 bloc derrière la surface que touche le centre du faisceau, ce qui évite d'éclairer à travers un mur jusqu'à l'autre côté de la scène.

**Budget** : 16 / 40 / 72 projecteurs en qualité Moyenne / Élevée / Ultra, aucun en Faible. Les projecteurs hors budget gardent la projection de la révision 9. Quand un projecteur est dessiné ainsi, ses taches projetées et ses lumières d'empreinte ne sont plus utiles et ne sont plus dessinées. Avec Shimmer, une lumière d'empreinte plus douce est gardée. La ligne F3 donne le nombre de projecteurs dessinés.

## R10.2 Faisceaux jusqu'au sol

- La fin d'un faisceau qui touche une surface est adoucie sur une distance plus courte (au plus 25 % de sa longueur, environ un demi-rayon), et **45 % de sa lumière reste là où il touche** (15 % avant).
- Avec les projecteurs, le sol sous le faisceau est éclairé en continu.

## R10.3 Plus de volume

- Densité de brume dans l'air ×1,35 pour tous les faisceaux.
- Les washes larges sont moins atténués dans l'air (×0,57 à 50°, ×0,46 avant).
- Nouveau : une **lueur de brume** large et faible, un peu devant chaque objectif allumé : le volume de lumière dans la fumée à la source. Sur un PULSESTRIKE, seulement pendant un flash.

## R10.4 Limites

- **Pas d'ombres** : un gobo passe à travers un décor fin. C'est voulu pour le truss, mais aussi vrai pour un instrument devant un mur.
- **Coût en FPS** : chaque projecteur dessine 3 passes sur la surface d'écran de son cône. Un wash large vu de près couvre beaucoup d'écran. À mesurer avec le F3 en qualité Élevée et Ultra, puis Moyenne si besoin.
- **Shader packs** : la fonction est désactivée.
- **Résolution des gobos** : les textures font 64 px. Elles sont lissées pour la projection, mais un gobo très agrandi reste doux.

---

# Révision 11 — gobos sur le truss

> **NON VALIDÉ DANS MINECRAFT.** Vérifié : types du module commun, syntaxe, 156 tests hors jeu. Shaders non compilés ici.

## Causes trouvées

1. **Rayons arrêtés par la case entière du truss.** Pour les rayons de lumière de CSC, une case de truss était un bloc plein. Le faisceau d'un VORTEX visant le truss s'arrêtait donc sur la face de la case, et la projection ne dépassait pas 1,5 bloc derrière. Le motif ne passait pas par les vides jusqu'à ce qui est derrière, et il ne tombait pas sur les tubes eux-mêmes.
2. **Lumière seulement multipliée sur la texture.** Sur un truss noir ou sombre, multiplier une texture presque noire ne montre presque rien. C'est pour cela qu'un gobo ne se voyait pas sur le truss, alors qu'il se voit sur un sol plus clair.

## Corrections

- **Treillis du truss** : les rayons de CSC utilisent maintenant le treillis réel des truss et des mâts. C'est la même forme que celle déjà fournie aux faisceaux de Theatrical par `LightCompat` : les quatre tubes au lieu de la case pleine. La lumière passe par les vides, se pose sur les tubes qu'elle rencontre vraiment, et la projection continue jusqu'à la surface derrière. Un gobo se découpe ainsi sur le truss et sur le décor du fond.
- **Passe additive** : chaque projecteur de lumière et chaque lumière ponctuelle reçoit une passe de plus qui **ajoute** un peu de lumière colorée (fichiers `*_add.json`, mélange un + un) : 12 % de la lumière pour les projecteurs de lumière, 6 % pour les lumières ponctuelles. Les matériaux sombres (truss noir, bâches) montrent ainsi le motif dans sa couleur, comme la brillance d'un tube métallique dans un faisceau. Le reste de la lumière reste multiplié sur les textures, ce qui préserve les détails.

## À vérifier

- La ligne F3 doit indiquer « … projectors » avec un nombre supérieur à 0 :
  - **0** : les projecteurs de lumière ne sont pas dessinés (qualité Faible, shader pack, ou erreur de shader) ;
  - **erreur de shader** : le log l'indique.
- Un VORTEX avec un gobo, visant un truss devant un mur.

---

# Révision 12 — artefacts des gobos sur le truss

> **NON VALIDÉ DANS MINECRAFT.** Shaders non compilés ici.

Constat (capture du 8 novembre, 23 h 36) : les gobos se voient sur le truss, mais le rendu semble « parasité ». Trois causes ont été trouvées dans le code :

1. **Flou de focus en copies** : le flou superposait 3 copies du motif à des tailles légèrement différentes. Loin du centre, ces copies ne se recouvrent plus et donnent une image double ou triple, une sorte de moiré. **Corrigé** : le flou est maintenant un petit disque de 9 échantillons autour de chaque point, comme une vraie optique floue.
2. **Orientation des surfaces bruitée sur les bords des objets** : l'orientation est calculée à partir des pixels voisins. Sur un tube de truss fin, presque chaque pixel touche un bord, et ses voisins sont sur une autre surface loin derrière. Le résultat était du bruit : des points clairs et sombres qui scintillent quand le faisceau ou la caméra bougent. **Corrigé** : l'orientation n'est utilisée que si les voisins sont proches en profondeur ; sinon une valeur neutre est prise. C'est appliqué aux deux shaders.
3. **Ligne F3 « 0 drawn / 0 asked » fausse** : la lumière est envoyée plusieurs fois par image, et le dernier envoi, vide, écrasait les chiffres. **Corrigé** : les totaux de l'image sont additionnés.

Ce qui est normal et reste : un faisceau presque parallèle à un tube (faisceau vertical le long d'un montant) étire le motif en longues traînées, comme une vraie projection rasante. Les tubes devant un faisceau le masquent, ce qui donne des bandes sombres dans le faisceau.

---

# Révision 13 — le faisceau prend la forme du gobo

> **NON VALIDÉ DANS MINECRAFT.** Simulation hors jeu : `rev13_simulation_faisceaux_gobo.png` (gobos 1, 2 et 5 vus de côté). Ce n'est pas une capture.

Sur l'image de référence, un faisceau avec gobo n'est pas un cône plein. Dans la brume, c'est un **faisceau de rayons** : la lumière qui passe par les trous du motif. Entre ces rayons, des bandes sombres correspondent au métal du gobo.

- **Découpage du gobo** : la texture du gobo est lue une fois et découpée en grille 7 × 7 sur son disque (`GoboShafts`). Chaque case qui laisse passer la lumière devient **un rayon** :
  - il part du point de l'optique correspondant ;
  - il s'ouvre avec le faisceau, à l'échelle de sa partie du motif ;
  - sa luminosité est la quantité de lumière que la case laisse passer.
- **Synchronisation** : les rayons tournent avec la roue de gobos, dans le même repère que la projection sur les surfaces. Le faisceau porte donc exactement le motif qui arrive sur le truss.
- **Focus** : un focus flou élargit et adoucit les rayons.
- **Remplissage** : le corps plein du faisceau ne reste qu'en léger fond de brume (×0,12). Le bord défini et le cœur serré ne sont pas dessinés quand un gobo est en place.
- **Qualité** : en qualité Moyenne, un rayon sur deux ; en Élevée et Ultra, tous (jusqu'à 37). Chaque rayon fait 4 segments, soit environ 150 quads par faisceau. En qualité Faible, le faisceau garde son corps plein, sans texture de gobo.
- L'ancienne texture de brins sur le cône est retirée.

---

# Révision 14 — surexposition corrigée (faisceaux de gobo, projection)

> **NON VALIDÉ DANS MINECRAFT.**

Constat sur ta capture du 8 novembre (23 h 52) : ciel blanc et voile gris sur le sol et la régie, avec 16 projections de lumière actives.

- **Rayons du gobo (révision 13)** : chaque rayon était aussi lumineux qu'un faisceau entier. Deux raisons :
  - un rayon fin ne s'atténuait pas avec la distance comme le faisceau large dont il fait partie ;
  - vus de côté, environ 4 rayons se superposent sur une même ligne de vue, et 16 faisceaux de 37 rayons s'additionnaient jusqu'au blanc.

  **Corrigé** : un rayon suit maintenant la loi de lumière du faisceau entier, et la lumière est partagée entre les rayons. En simulation, la lumière ajoutée par un faisceau de gobo (0,0029 à 0,0044) est du même ordre que celle d'un faisceau plein (0,0036) : `rev14_simulation_faisceau_plein_et_gobos.png`.
- **Projection de lumière** :
  - elle portait loin avec une atténuation faible ;
  - la nuit, elle pouvait multiplier une surface jusqu'à ×16.

  Un faisceau dirigé au-dessus du public inondait donc le sol et la régie. **Corrigé** :
  - atténuation 3 fois plus forte avec la distance ;
  - facteur plafonné à ×4 ;
  - passe additive réduite de moitié (0,12 → 0,06 pour la projection, 0,06 → 0,05 pour les lumières ponctuelles).

---

# Révision 14b — gobos nets, VORTEX moins lumineux, corps des projecteurs

> **NON VALIDÉ DANS MINECRAFT.**

- **Gobos (référence : image 3, motifs de points nets sur le mur et le truss)** :
  - les textures de gobo passent de 64 à **256 px**, avec un lissage des bords (3 × 3 échantillons par pixel). Projetées en grand, elles gardent des bords nets ;
  - le gobo 2 « points » devient des **trous ronds sur des anneaux** (centre, puis 6, 11 et 16 trous de plus en plus petits vers le bord), comme sur la référence. Les autres motifs sont inchangés, seule leur définition augmente.
- **VORTEX** : faisceaux dans l'air **40 % moins lumineux** (corps, cœur, bord, halo et rayons de gobo).
- **Projecteurs qui prennent la couleur des projecteurs allumés quand on s'en approche** :
  - **lumières de scène CSC** : les corps des projecteurs sont maintenant dessinés **après** ces lumières, qui ne les éclairent plus ;
  - **Shimmer** (qui éclaire tout dans son rayon, sans exception possible) :
    - la lumière devant l'objectif est placée à 3,5 blocs (au lieu de 2) et elle est moins forte ;
    - la lumière du rig est placée à l'intérieur du truss, un demi-bloc derrière sa face, et elle est plus douce.

    Le truss et le toit restent colorés, mais les corps voisins ne sont plus baignés de lumière.

**Limite** : Shimmer éclaire aussi les objets, donc un projecteur placé juste devant le faisceau d'un autre en prendra toujours un peu la couleur.

---

# Révision 14c

> **NON VALIDÉ DANS MINECRAFT.**

- **Corps des projecteurs blanchis de près** : deux causes restaient, toutes deux dans la couleur des projecteurs allumés.
  - La **lueur de brume** devant chaque objectif (révision 10) était un grand carré doux de 4,8 blocs tourné vers la caméra. De près, il passait devant les corps des projecteurs voisins et les voilait. **Elle n'est plus dessinée que sur un flash de blinder.**
  - La **lumière Shimmer devant l'objectif du VORTEX** (rayon 7,5 blocs) baignait les corps voisins, et Shimmer éclaire tout dans son rayon. **Supprimée pour le VORTEX** : un projecteur à faisceau ne diffuse presque pas de lumière autour de son objectif, et son impact est déjà éclairé. Pour les washes et les blinders, elle est gardée, avec un rayon un peu plus petit (6,5 à 7,5 blocs).
- **Grandes formes pâles et anguleuses dans le ciel** : les rayons de gobo partant vers le ciel n'avaient que 4 segments pour se tourner vers la caméra sur 70 blocs, ce qui faisait de grandes facettes plates. **Passés à 6 segments.**

---

# Révision 15 — ombres des structures dans la lumière et dans la brume

> **NON VALIDÉ DANS MINECRAFT.** Les trois nouveaux shaders n'ont pas pu être compilés ici (pas de compilateur GLSL ni de jeu). Une erreur de compilation n'empêche pas de jouer : la fonction se coupe seule (message dans le log), et on revient au rendu de la révision 14.

## Principe : une carte d'ombre par projecteur (vraie profondeur)

1. **Ce que voit l'optique** (`BeamShadows`). Pour chaque faisceau, la géométrie des blocs situés dans son cône est dessinée, vue depuis le projecteur, dans une petite texture de profondeur 256 × 256 :
   - pour les blocs dessinés par le jeu, les **modèles réels** : les trusses avec leurs membrures, croisillons et diagonales tels que modélisés, les tours, les échafaudages, les plateformes, les murs ;
   - pour les blocs dessinés par un renderer (tours détaillées, bâches, toits), les membrures du truss (`LightCompat.lattice`) ou leur contour ;
   - les corps des projecteurs CSC, les liquides, le verre et les blocs translucides ne font pas d'ombre.
2. **La lumière projetée** (`concertstage_light_projector`). Chaque point d'une surface compare sa distance au projecteur avec celle de la carte (4 échantillons, transition douce). Un point caché par un tube ne reçoit rien. Le gobo garde son motif et perd seulement les parties que les tubes masquent.
3. **Le faisceau dans la brume** (nouveau shader `concertstage_beam_volume`). Il remplace les rubans pour les VORTEX et les washes. Pour chaque pixel :
   - la ligne de vue est coupée par le cône du faisceau, puis arrêtée par la scène (copie de la profondeur, corps des projecteurs compris) ;
   - la brume est échantillonnée le long de ce segment : 14 pas en Moyenne, 22 en Élevée, 30 en Ultra, décalés d'un pixel à l'autre pour éviter les bandes ;
   - en chaque point, la lumière reçue est le motif de l'optique à cet endroit, multiplié par la réponse de la carte d'ombre, puis par la loi de lumière des faisceaux (rampe, 1/r^0,85, absorption).

   Résultat : derrière chaque tube, une **ombre** traverse le volume du faisceau. Entre les tubes, la lumière continue dans la brume et jusqu'à ce qui se trouve derrière.

## Ce qui suit les réglages

- **Pan/tilt, zoom et portée** : la carte est redessinée quand l'un d'eux change.
- **Roue de gobos** : la rotation est appliquée dans les deux shaders. La carte d'ombre n'en dépend pas, donc rien n'est redessiné.
- **Focus** : un focus flou adoucit le motif dans le volume. Chaque pas lit un point voisin du motif, et leur moyenne donne le flou.
- **Brouillard** :
  - la quantité de lumière dans l'air suit le réglage Haze, comme les rubans ;
  - l'absorption va de 0,6 à 1,4 fois 0,018 par bloc, selon le Haze.
- **Portée** : quand la carte existe, la lumière ne s'arrête plus à la surface que touche le centre du faisceau. Ce qui la déborde (bord d'une plateforme, trous d'un truss) continue, jusqu'à la portée de l'ombre : 24 blocs en Moyenne, 32 en Élevée, 48 en Ultra.

## Coût (estimations de conception, pas des mesures)

- **Géométrie** :
  - rassemblée par section de 16³ dans un tampon gardé sur la carte graphique et partagé par tous les projecteurs ;
  - reconstruite toutes les 160 images environ (un bloc posé ou cassé apparaît dans l'ombre en quelques secondes) ;
  - au plus 1 section par image (2 en Ultra).
- **Cartes d'ombre** :
  - nombre maximal par qualité : 12, 24 ou 40 ;
  - redessinées par image au plus : 6, 12 ou 24 ;
  - redessinées seulement si le faisceau a changé.
- **Faisceaux calculés dans la brume** : 12, 24 ou 40 au plus. Au-delà, ou en qualité Faible, avec un prisme, avec un pack de shaders ou avec « Stage light » coupé, les rubans de la révision 14 sont dessinés (sans ombre).

## Vérifications faites ici

- **Intersection ligne de vue / cône** (copie Python des calculs du shader) : 1300 rayons sur 1300 donnent le même segment qu'une marche brute, sur 3000 tirages de faisceaux de 0,3° à 90°. Voir `tools/lightshow/check_rev15_cone.py` et `validation/check-rev15-cone.log`.
- **Profondeur de la carte → distance** : erreur maximale de 1e-13 bloc.
- **Simulation CPU** des deux shaders, avec un truss approché par ses membrures et des diagonales en escalier (pas le modèle Blender) : `rev15_simulation_ombres_truss.png`. À gauche la révision 14, à droite la révision 15 ; en haut un spot ouvert, en bas le gobo 2. Ce n'est **pas** une capture du jeu.
- **Vérification de types** contre Minecraft 1.20.1 : aucune erreur nouvelle (`validation/typecheck-common-1.2.0-rev15.log`, mêmes 45 erreurs attendues qu'en révision 14).
- **Tests** : 156 / 156. **Syntaxe Java** : 255 fichiers OK.

## Limites connues

- Les lumières Shimmer et les lumières ponctuelles CSC (rig, débordement) ne sont pas occultées : seuls le faisceau et sa projection le sont.
- Le cercle de truss (dessiné par son point d'accroche) ne fait pas d'ombre.
- Les blocs à trous dans leur texture (feuilles, grilles) font une ombre pleine.
- Une ombre plus loin que la portée de la carte n'est pas calculée.
- Avec un prisme, le faisceau reste en rubans, sans ombre.

---

# Révision 15b — écran gris au niveau du sol

> **NON VALIDÉ DANS MINECRAFT.**

**Constat** (vidéo du 9 novembre, 00 h 45) : quand on est au sol, l'écran devient une image grise, claire sur les bords et noire au centre, qui cache tout le monde. En vol, l'image est normale.

**Cause la plus probable** : c'est la **vignette** du HUD, dessinée sans transparence.
- **Indice 1 : la forme.** Le dégradé est exactement celui de la texture de la vignette.
- **Indice 2 : la lumière.** La vignette est plus forte quand le joueur est dans moins de lumière : lumière 11 à 13 au sol, 15 en l'air.
- **Mécanisme** :
  - le jeu mémorise le mode de mélange du dernier shader appliqué ;
  - les shaders de lumière de CSC ont leur propre mélange (multiplier, ajouter, « écran »), alors que tous les shaders du jeu sont « opaques » ;
  - le premier shader du jeu appliqué après ceux de CSC coupe donc la transparence ;
  - la vignette ne la rallume pas elle-même ;
  - le shader de faisceau dans la brume (révision 15), dessiné juste avant les rubans et en fin de rendu du monde, laissait ce cas se produire beaucoup plus souvent.

**Correction** :
- après chaque passe de lumière CSC, un mélange opaque est réappliqué une fois, à un moment où rien ne se dessine ;
- l'état attendu par le jeu est ainsi rétabli à chaque image, même quand CSC ne dessine rien (`VolumeLights.resetBlend`, appelée depuis `GpuMesh.flush`).

**Autre correction** : le compteur F3 « shadows N maps » comptait une carte plusieurs fois par image (72 affichées pour 40 au maximum).

**Point non expliqué** : dans la vidéo, l'écran reste gris à quelques moments où la ligne F3 indique que CSC ne dessinait plus rien. Le rétablissement à chaque image devrait couvrir aussi ce cas, mais ce n'est pas vérifié.
