# CSC — Export des profils de projecteurs dot2 / grandMA2 / grandMA3

**Statut : généré et vérifié hors jeu, PAS encore importé dans un vrai dot2 onPC ni dans grandMA2 onPC.**
Aucune compatibilité n'est annoncée tant que le protocole de test ci-dessous n'a pas été passé dans dot2 onPC.

## 1. Ce qui est exporté

Console LUMICORE X1 > onglet **External** > section Export > bouton **logiciel** (clic = changer) + **Export** :

| Logiciel | Format | Dossier écrit dans `concertstage/export/<show>/` | Générateur |
|---|---|---|---|
| grandMA3 GDTF | GDTF 1.2 (déjà livré) | `USB stick/grandMA3/gma3_library/fixturetypes/gdtf/` | `tools/external/build_external_assets.py` (`gdtf()`) |
| grandMA2 XML | XML de type MA2 (format du MA Fixture Builder), écrit pour grandMA2 3.9.60 | `USB stick/gma2/library/` | `tools/external/ma2_profiles.py` (`GrandMa2Exporter`) |
| dot2 XML | XML de type MA2, **écrit pour dot2 1.2.2** (rév. 16i) | `USB stick/dot2/library/` | `tools/external/ma2_profiles.py` (`Dot2Exporter`) |

**Rév. 16i :**
- **Arborescence de clé USB.** Les exports reproduisent l'arborescence que chaque logiciel lit sur une clé USB : il suffit de copier le contenu de `USB stick` à la racine de la clé.
- **Instructions.** Un fichier `INSTALL - consoles.txt` donne la marche à suivre pour dot2, grandMA2, grandMA3 et QLC+.
- **README.** Le `README` de chaque logiciel est placé à côté, et non plus dans le dossier `library` que lit la console.

L'info-bulle du bouton logiciel affiche les modes et le nombre de canaux de chaque type. « Export complete lighting rig » écrit aussi `gma2/` et `dot2/`.

Les XML **ne sont pas des GDTF renommés ni convertis** : ils sont générés depuis les tables `DmxMode` (source unique des modes DMX du jeu, exportées dans `tools/external/modes.json`). Le modèle 3D reste séparé (export « 3D models », 3DS pour dot2 3D).

MA indique qu'un fichier exporté par le MA Fixture Builder s'importe dans **les deux** systèmes (dot2 et grandMA2) : le corps XML est donc le même pour les deux exportateurs. Ce qui diffère : le dossier cible (`dot2/library`, `gma2/library`), la mention du logiciel dans l'info, et pour dot2 la vérification qu'un mode tient dans l'univers que dot2 onPC sort sans matériel MA (512 canaux ; le plus large, W19 Full, fait 124).

## 2. Fichiers (12 par logiciel)

Nom : `concert_stage_craft@<modèle>@<mode>.xml` (le « @ » est requis par la bibliothèque MA).

| Type | Full | Compact | Ultra Compact | Virtual Matrix |
|---|---|---|---|---|
| CSC VORTEX X1 | 18 | 14 | 4 | non exporté (aucun canal propre) |
| CSC LUMORA W7 | 70 (19 cellules RGB) | 12 | 4 | non exporté |
| CSC LUMORA W19 | 124 (37 cellules RGB) | 12 | 4 | non exporté |
| CSC PULSESTRIKE M | 82 (14 zones RGB + 28 segments blancs) | 10 | 4 | non exporté |

Les tables de correspondance canal par canal (n°, canal CSC, preset / feature / attribut MA, résolution, défaut, plages DMX) sont dans `README.md` de chaque dossier exporté (`common/src/main/resources/assets/concertstage/external/dot2/README.md`). Elles sont générées par le même code que les XML.

## 3. Structure générée

- Racine `<MA>` avec l'espace de noms `http://schemas.malighting.de/grandma2/xml/MA`.
  - **Version (rév. 16i) :**
    - `major_vers` / `minor_vers` / `stream_vers` = la version du logiciel visé. Une console MA **refuse un type écrit par une version plus récente que la sienne** (forum MA : un fichier marqué grandMA2 3.7 ne s'importe pas dans dot2 1.5, et le MA Fixture Builder écrit la version de la dot2 visée).
    - Les fichiers dot2 étaient marqués 3.9.60, ce qui explique les projecteurs « non reconnus » dans dot2 onPC (projecteurs remplacés par des « Dimmer » dans le patch).
    - Ils sont maintenant marqués 1.2.2 (la première dot2 onPC qui émet un univers sans matériel), sans l'attribut `highlight_value`. Ceux de grandMA2 restent marqués 3.9.60.
  - Ensuite : `<FixtureType name mode>`, `short_name`, `manufacturer`, `short_manufacturer`.
- Module 0 « Main » (classe Headmover, faisceau Spot pour le VORTEX, Wash pour les autres) : un `ChannelType` par fonction, `coarse` / `fine` (16 bits pour pan / tilt, le dimmer en Full, le zoom du VORTEX en Full), `default` dans la résolution du canal (128 → 32768 en 16 bits).
- `ChannelFunction` : plage DMX en 24 bits (`min_dmx_24` / `max_dmx_24`, contiguës de 0 à 16777215), valeurs affichées `from` / `to` et physiques `physfrom` / `physto` :
  - Pan / Tilt : ± la moitié de la course du jeu (VORTEX 540° / 270°) ;
  - Dimmer, RGB, blanc, focus, iris : 0–100 % (physique 0–1) ;
  - Shutter : 0–7 « Open » (sous-attribut SHUTTER), 8–255 « Strobe » 1 Hz → fréquence max du jeu (sous-attribut STROBE) ;
  - Zoom : angle min–max du jeu en degrés ;
  - Gobo : une fonction par fente (pas de 28, dernière jusqu'à 255) avec son `ChannelSet` ;
  - Rotation gobo / prisme : CCW 0–123, Stop 124–131, CW 132–255, en tr/min calculés depuis le rendu (220 °/s gobo, 160 °/s prisme) ;
  - Prisme : 0–127 Open, 128–255 3-Facet ;
  - Macro couleur (Ultra Compact) : 17 couleurs, une fonction + `ChannelSet` par couleur (pas de 15).
- Cellules / zones LED (Full) : sous-modules de classe LED (« RGB cell » = R G B, « White segment » = DIM), une `Instance` par zone avec `patch` = premier canal de la zone et `parent_instance="0"`.
- **PULSESTRIKE M, mode Compact (rév. 16g) : deux instances.** Instance 1 « LED bars » = canaux 5 à 7 (rouge, vert, bleu des barres LED). Instance 2 « White bar » = canaux 8 et 9 (blanc et CTO de la barre blanche du milieu). Tilt, dimmer, strobe et zoom restent sur le projecteur (module Main). Les canaux et leur ordre ne changent pas : ils sont seulement regroupés. Le dimmer est commun aux deux instances, car le mode Compact du jeu n'a qu'un seul dimmer. Définition unique : `DmxMode.instances()`, reprise dans les mêmes conditions par les profils :
  - grandMA2 / dot2 : modules LED et `Instance` ;
  - grandMA3 (GDTF) : géométries `LED_bars` et `White_bar` ;
  - QLC+ : deux `Head`.

Attributs MA utilisés (manuels grandMA2 3.9 / dot2 1.9, tables « Fixture Types » de MA 3D / dot2 3D) : PAN, TILT, DIM, SHUTTER (SHUTTER / STROBE), COLORRGB1/2/3 (R G B), COLORRGB5 (blanc), ZOOM, FOCUS, IRIS, GOBO1, GOBO1_POS (GOBO1_ROT), PRISMA1, PRISMA1_POS (PRISMA1_ROT).

## 4. Installation dans dot2 onPC

1. Exporter « dot2 XML » depuis la console CSC. Les fichiers déjà copiés avant la rév. 16i sont à remplacer.
2. Copier le dossier `concertstage/export/<show>/USB stick/dot2` à la racine d'une clé USB : `USB:/dot2/library/*.xml`. dot2 ne liste que ce dossier.
   - Sans clé : `C:\ProgramData\MA Lighting Technologies\dot2\<version>\library` (dossier caché).
3. Préparer dot2 onPC :
   - le lancer **en administrateur** ;
   - rejoindre une session : Setup > Sessions.
4. Importer les types : Setup > **Patch & Fixture Schedule** > **Add New Fixtures** > **Select Other** > **Drive** (en bas à gauche, glisser vers le haut jusqu'à la clé) > Concert Stage Craft > type > mode > OK.
5. Patcher : l'univers dot2 1 sort en Art-Net 0:0, qui pilote l'**univers CSC 0** (rév. 16h).
6. Si un type n'apparaît toujours pas, ou si OK ne fait rien :
   - ouvrir le `.xml` dans le **MA Fixture Builder** gratuit (File > Import), puis Export to USB Drive : il réécrit le fichier pour sa version de dot2 ;
   - envoyer ce fichier, ou un projecteur exporté depuis votre dot2 (Select Fixture Type > Export), pour aligner le générateur exactement.
4. Patcher à l'adresse indiquée par l'auto patch / l'export patch CSC.

grandMA2 : même chose avec le dossier `gma2/library`.

## 5. Protocole de test dans dot2 onPC — à faire, aucun point n'est validé

| # | Étape | Attendu | Résultat |
|---|---|---|---|
| 1 | Export dot2 XML depuis la console | 12 fichiers + README | OK hors jeu (test automatique) — non vérifié en jeu |
| 2 | Copie dans dot2/library | — | non testé |
| 3 | Détection dans Import Fixture Type | 4 types, 3 modes chacun | non testé |
| 4 | Sélection du type / mode | nombre de canaux = tableau §2 | non testé |
| 5 | Patch | adresses sans conflit | non testé |
| 6 | Attributs | presets Dimmer, Position, Color, Beam, Focus, Gobo | non testé |
| 7 | Pan / tilt | ±270° / ±135° sur le VORTEX, 16 bits | non testé |
| 8 | Couleurs | MixColor RGB (+ blanc / CTO selon le type), macro en Ultra | non testé |
| 9 | Dimmer | 0–100 %, 16 bits en Full | non testé |
| 10 | Zoom | 2–32° VORTEX | non testé |
| 11 | Gobos | 8 fentes nommées + rotation | non testé |
| 12 | Strobes / blinders | Open 0–7, strobe 8–255 ; PULSESTRIKE | non testé |
| 13 | Matrices | sous-fixtures des cellules (W7, W19, PULSESTRIKE Full) ; PULSESTRIKE Compact : 2 instances (barres LED, barre blanche) | non testé |

Un fichier XML exporté par dot2 lui-même (n'importe quel projecteur : Select Fixture Type > icône d'export, fichier dans `dot2\library`) permettrait de comparer la structure exacte avant ce test.

## 6. Limites connues

- **Non testé dans dot2 ni grandMA2.** Les tests automatiques vérifient seulement que les XML sont bien formés et cohérents avec les tables du jeu : chaque canal décrit une seule fois, offsets, défauts, plages contiguës (`tools/lightshow_tests/export/MaProfileTest.java`, 144 vérifications).
- Noms **non confirmés** par une source MA : attribut CTO (`COLOR / CTO / CTO`) ; macro couleur décrite comme une roue `COLOR1` sans bloc `<Wheels>` (pas de pastilles de couleur) ; attributs `from_dmx` / `to_dmx` des `ChannelSet`. Ce sont les premiers points à regarder si l'import échoue.
- `beam_intensity = 10000` : valeur **nominale** pour que dot2 3D dessine un faisceau. Le jeu n'a pas de données photométriques, ce n'est pas une mesure.
- Matrices LED : les cellules sont des sous-fixtures sans position 3D (pas d'offset X/Y/Z, l'unité MA n'est pas confirmée) ; elles n'ont pas de dimmer propre (l'intensité passe par le RGB de la cellule). La disposition en grille pour le Layout ou les effets matrice reste à faire dans dot2. Le mode Virtual Matrix n'a pas de profil (16 canaux CSC à patcher à part).
- Gobos : fentes nommées « Gobo 1..8 », sans image (pas de `<Wheels>` / média).
- Les XML sont des ressources générées au build (`sh tools/external/build.sh`) : un nouveau projecteur s'ajoute dans `FixtureProfile` / `DmxMode`, puis on relance le build.

## 1.2.0 bêta 2b (14 novembre) : mode Full en « mode pixel »

Ce qui suit remplace, pour le mode Full, les nombres de canaux du tableau de la section 2. Les tables à jour sont dans
`DMX_CHARTS.md` et dans `README.md` de chaque dossier exporté.

- **Projecteurs concernés :** ceux qui ont des pixels RGB (PIXELBAR B16, PIXELTUBE T1, PIXELWASH W37, FLOODLIGHT F8, LUMORA W7 /
  W19) et les PULSESTRIKE M / FLASHCORE FX.
- **Le mode Full n'a plus de gradateur général.** Il n'a plus de couleur de base que les pixels ont déjà : chaque pixel est sa
  propre couleur et sa propre intensité. Avant, un effet de cellules restait noir dans le jeu tant que le gradateur général était
  à 0.
- **Gradateurs virtuels.** Chaque pixel RGB a un gradateur virtuel (DIM sans adresse) dans les profils MA, et un canal
  `Offset="None"` avec ses relations dans les GDTF.
- **PULSESTRIKE M / FLASHCORE FX.** Plus d'instances « barre du haut » ni « barre du bas ». L'instance principale porte tilt,
  zoom, focus, CTO, le gradateur de la barre blanche et le stroboscope de tout le projecteur. Ensuite viennent les pixels RGB et
  les segments blancs.
- **Pan virtuel.** Un projecteur qui a un tilt sans pan reçoit un pan virtuel, car l'onglet Position de dot2 demande les deux.
- **Anciens shows.** L'ancien Full reste dans le jeu sous le nom « Full (gradateur général, avant 1.2.0 bêta 2b) » pour les
  shows enregistrés avant.
