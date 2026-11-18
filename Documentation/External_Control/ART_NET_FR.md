# CSC 1.2.0 — Réglages Art-Net dans le jeu, univers 0 (rév. 16h)

**Statut :**
- **Fait et vérifié hors jeu** : code, tests Java (148 + 25 + 160 vérifications passent, dont 23 sur l'univers 0 et les réglages réseau), réception UDP réelle en boucle locale sur cette machine, vérification de types.
- **Non vérifié** :
  - rien n'a été testé dans Minecraft ;
  - aucun test avec grandMA2, grandMA3, QLC+ ou dot2 (ces logiciels ne sont pas disponibles ici) ;
  - aucune mesure de performance en jeu.

Les tests D à H, J, L et M sont donc à faire (voir § 10).

## 1. Audit de l'existant (avant la rév. 16h)

| Point | Constat |
|---|---|
| Récepteur | Un seul moteur (`ExternalControl`) : un fil par protocole, Art-Net sur UDP 6454, sACN sur UDP 5568. Il a été **adapté**, pas doublé. |
| Adresse d'écoute | `bind_address` dans `config/concertstage-external.properties`, à modifier à la main, redémarrage nécessaire. |
| Port UDP | 6454, codé en dur. |
| Décodage ArtDmx | Correct : Port-Address sur 15 bits, longueur paire 2 à 512, version ≥ 14. Il n'a pas changé. |
| **Univers** | **Décalage +1 automatique** : `artnet_universe_offset=1` par défaut, donc Port-Address 0 = univers CSC 1. Et **l'univers 0 était refusé partout** (`u < 1`) : fusion des sources, envoi aux clients, patch (`PATCH_EDIT`, `PATCH_DMX`, `PATCH_BULK`), matrice, auto-patch, import CSV, chargement des shows, boutons U−. |
| Filtre de source | Aucun. |
| Interfaces réseau | Non listées. |
| Moniteur | Une ligne par univers dans l'onglet External ; pas de compteur de paquets par Port-Address, pas de vue des 512 canaux. |

## 2. Accès

Console LUMICORE X1 → onglet **Settings** → **NETWORK** :

- **ART-NET** : les réglages ;
- **ART-NET MONITOR** ;
- **DMX MONITOR**.

Boutons des réglages :

| Bouton | Effet |
|---|---|
| APPLY | Appliquer tout de suite, sans redémarrer. |
| SAVE | Appliquer et enregistrer. |
| RESET | Revenir aux valeurs par défaut et enregistrer. |
| REFRESH | Relire les réglages et la liste des interfaces. |
| TEST CONNECTION | Vérifier que le récepteur reçoit (voir § 3). |

Permission : seul **l'hôte de la partie** (solo ou LAN) ou un **opérateur du serveur** (niveau 2) peut modifier. Sur un serveur dédié, l'autorisation d'ouvrir le port est réservée au niveau 4. Tout joueur qui peut ouvrir la console voit les moniteurs. Chaque modification est écrite dans le journal du serveur, avec le nom du joueur.

## 3. Réglages

| Réglage | Rôle | Défaut |
|---|---|---|
| Art-Net | Réception activée ou non | activée |
| Local IP | Adresse d'écoute de la machine qui fait tourner le monde (le serveur) | `0.0.0.0` (toutes les interfaces IPv4) |
| Interfaces | Liste détectée : nom, adresse IPv4, active ou non, boucle locale. Un clic copie l'adresse dans Local IP. | — |
| UDP Port | Port d'écoute | 6454 |
| Source Filter | ANY SOURCE, ou SPECIFIC SOURCE avec **Console IP**. Les autres émetteurs sont comptés comme « filtrés ». | ANY |
| Universes | Port-Address reçues : grille 0 à 63, plus Start / End pour une plage jusqu'à 32767 | 0 à 15 |
| Universe mapping | Port-Address → univers CSC | directe (N → N) |
| Priority | « Le mode de la console décide » ou « Art-Net prend la main » | console |
| Signal lost | Hold last value / Blackout / Return to CSC control | Hold |

Les trois adresses ne se confondent pas :

- **Local IP** : où CSC écoute.
- **Console IP** : qui a le droit d'émettre (filtre).
- **Adresse de destination Art-Net** : réglée dans la console lumière. Elle vaut soit l'adresse de diffusion (broadcast), soit l'IP de la machine Minecraft (unicast).

**APPLY**, dans cet ordre :

1. validation (IPv4, port 1 à 65535, Port-Address 0 à 32767, univers CSC 0 à 64, au moins un univers) ;
2. contrôle que l'adresse locale appartient à la machine ;
3. fermeture du seul socket Art-Net (sACN et OSC continuent) ;
4. ouverture avec les nouveaux réglages ;
5. remise à zéro des compteurs ;
6. compte rendu.

Si l'adresse ou le port ne peuvent pas être ouverts, l'état passe à **BIND ERROR**, avec le message du système, et **les réglages précédents sont rétablis et rouverts**. Les univers dont la source change sont vidés : pas de mélange d'anciennes valeurs.

**TEST CONNECTION** envoie un **ArtPoll** à l'adresse et au port d'écoute (127.0.0.1 si l'écoute est sur `0.0.0.0`) et attend de le voir arriver (1 s au plus). Un ArtPoll ne contient pas de DMX : le test ne change aucun projecteur. Le résultat (délai en ms, ou « rien reçu », ce qui signale souvent un pare-feu ou un autre programme sur le port) s'affiche au joueur.

Les réglages sont enregistrés dans `config/concertstage-external.properties` par SAVE : il n'y a jamais à modifier ce fichier à la main.

## 4. Univers 0 et numérotation

- ArtDmx porte une **Port-Address** sur 15 bits : Net (7 bits) · Sub-Net (4 bits) · Universe (4 bits).

  | Net:Sub-Net:Universe | Port-Address |
  |---|---|
  | 0:0:0 | 0 |
  | 0:0:1 | 1 |
  | 0:0:15 | 15 |
  | 0:1:0 | 16 |
  | 1:0:0 | 256 |

  CSC les lit telles quelles (test C).
- **Par défaut, Port-Address N = univers CSC N** : 0 → 0, 1 → 1. **Aucun +1 n'est ajouté.**
- Les univers CSC vont de **0 à 64**. L'univers 0 est un univers comme les autres partout : patch, auto-patch, matrice, import CSV, sauvegarde du show, enregistrement et lecture `.cscshow`, envoi aux joueurs, moniteurs.
- L'absence d'univers est représentée par `NONE = -1`, **jamais par 0**.
- Un nouveau show patche son premier projecteur en **univers 0, adresse 001**. Les canaux restent numérotés de 1 à 512 dans chaque univers.
- sACN ne change pas : univers sACN N = univers CSC N. La norme sACN commence à 1, l'univers 0 n'existe donc qu'en Art-Net.
- **Anciens réglages.** L'ancienne clé `artnet_universe_offset=1` n'est plus utilisée, et le journal du serveur le signale. Un show patché avec l'ancienne règle (Art-Net 0 = univers CSC 1) se rattrape d'un clic avec le préréglage **« Port-Address N → CSC N+1 »** : la correspondance est alors visible et modifiable. Un show enregistré sur l'univers 0 est rechargé sur l'univers 0, jamais sur 1.

**Correspondance (mapping).**
- Chaque ligne associe une Port-Address (0 à 32767) à un univers CSC (0 à 64), par exemple 0 → 1 ou 256 → 3.
- Une Port-Address présente dans la correspondance est reçue même si elle n'est pas cochée dans la grille.
- Le préréglage « Direct (0 → 0) » vide la table.

**Exports.**
- QLC+ : l'ID d'univers du workspace = la Port-Address de l'univers CSC.
- MVR : adresse absolue = Port-Address × 512 + adresse, ce qui donne l'univers MVR Port-Address + 1.
- L'import MVR fait l'inverse.

## 5. Moniteurs

**ART-NET MONITOR.** Toutes les valeurs sont mesurées par le récepteur :
- état ;
- adresse d'écoute ;
- filtre ;
- port ;
- dernière source détectée ;
- paquets reçus et débit (paquets de la dernière seconde) ;
- paquets DMX utilisés, invalides (avec la raison et l'émetteur), filtrés, ou valides mais d'une Port-Address non reçue ;
- dernier DMX (ms) ;
- **liste des Port-Address** : Net:Sub:Uni, univers CSC, ACTIF / AUCUNE DONNÉE / IGNORÉ, nombre de paquets.

| État | Signification |
|---|---|
| DISABLED | Réception coupée. |
| LISTENING | Socket ouvert, rien reçu. |
| RECEIVING | DMX accepté depuis moins de 3 s. |
| NO DATA | Plus rien depuis 3 s. |
| BIND ERROR | L'adresse ou le port ne peut pas être ouvert. |
| INVALID PACKET | Seuls des paquets invalides arrivent. |
| SOURCE FILTERED | Seuls des paquets d'émetteurs filtrés arrivent. |

**DMX MONITOR.**
- Univers CSC choisi (0 à 64), avec la Port-Address qui l'alimente.
- 512 canaux sur 4 pages de 128 : numéro, valeur, barre. Un canal modifié depuis le message précédent apparaît en ambre.
- Le survol d'un canal indique le projecteur et la fonction d'après le patch réel, par exemple « VORTEX 1 : Dimmer ».
- État (RÉCEPTION / PERDU / AUCUNE DONNÉE) et âge de la dernière mise à jour.

## 6. Priorités et perte du signal

| Politique | Comportement |
|---|---|
| Le mode de la console décide (défaut, comme avant) | Art-Net ne pilote une console que si elle est en mode **External**. En Internal, AutoShow ou Playback, il est ignoré. Une seule source pilote à la fois : pas d'écrasement continu. |
| Art-Net prend la main | Une console en Internal ou AutoShow passe en External dès qu'un des univers de son patch reçoit de l'Art-Net. Playback n'est pas interrompu. |

Après 3 s sans paquet sur tous les univers d'une console en External :

| Réglage | Comportement |
|---|---|
| Hold last value | Les dernières valeurs restent. |
| Blackout | Les canaux d'intensité de chaque projecteur patché passent à 0 : dimmer, segments et cellules, intensité de la matrice. Les positions sont gardées. Le DMX suivant reprend la main. |
| Return to CSC control | La console revient à son mode précédent (AutoShow si c'était Playback), puis repasse en External quand le signal revient. |

Le comportement est identique sur l'univers 0.

## 7. Multijoueur

- Le **serveur** (ou l'hôte en solo / LAN) reçoit Art-Net, applique le DMX et envoie aux joueurs proches seulement les canaux modifiés : 20 fois par seconde, plus une copie complète toutes les 5 s. Rien n'est envoyé à chaque image graphique.
- Il n'y a qu'un récepteur UDP par serveur.
- Les clients n'ouvrent aucun port.
- Sur un serveur dédié, la Local IP est celle de **la machine du serveur**.

## 8. Sécurité et robustesse

**Ce qui est fait :**
- chaque paquet est validé (identifiant, opcode, version, longueur, taille) et un paquet mal formé est rejeté et compté ;
- un paquet mal ordonné (séquence en retard) est écarté ;
- la réception tourne sur son propre fil, jamais sur le fil du jeu ;
- un seul tampon de réception est réutilisé ;
- au plus 512 Port-Address sont suivies dans les statistiques ;
- le socket est fermé et son fil arrêté à chaque changement de réglages et à l'arrêt du serveur ;
- le filtre de source est disponible ;
- les réglages sont protégés par les permissions (§ 2).

**Limites du protocole.** Art-Net n'a **ni authentification ni chiffrement**, et une adresse IP source se falsifie sur un réseau local. Le filtre de source protège des erreurs de configuration, pas d'un attaquant sur le même réseau. Recommandations :
- utiliser un réseau lumière dédié ;
- écouter sur l'interface de ce réseau plutôt que sur `0.0.0.0` ;
- ne jamais exposer le port 6454 sur Internet.

## 9. Réglage des logiciels

Dans tous les cas, l'émetteur envoie de l'Art-Net à destination de la machine Minecraft (unicast) ou en diffusion sur son réseau. Dans CSC, il faut cocher la Port-Address émise (ou ajouter une correspondance) et patcher les projecteurs sur l'univers CSC correspondant.

### QLC+

- Par défaut, l'**univers QLC+ 1 émet sur l'Art-Net 0**, l'univers 2 sur l'Art-Net 1, etc. Le numéro Art-Net de sortie se change par univers dans le plugin Art-Net.
- Avec la correspondance directe, l'univers QLC+ 1 pilote donc l'**univers CSC 0**.
- Le workspace exporté par CSC (`.qxw`) utilise ces numéros.

### dot2 onPC

- dot2 onPC émet **512 canaux sur le premier univers** en Art-Net ou sACN sans matériel MA. Au-delà, il faut un Node4 ou une console. CSC ne contourne pas cette limite.
- Art-Net ou sACN n'est actif que dans une **session**.
- La fenêtre Network Protocols affiche les univers dot2 1 à 8 et les univers Art-Net **0:0 à 0:7**. L'univers dot2 1 sort donc en Art-Net 0:0 = Port-Address 0 = **univers CSC 0**.

### grandMA2 onPC

- Network Protocols → Art-Net. Chaque ligne associe des univers locaux à un réseau, un Sub-Net et un Universe. Le Sub-Net et l'Universe Art-Net vont de 0 à F.
- La Port-Address reçue par CSC est celle de la ligne. Par exemple Sub-Net 0, Universe 0 → Port-Address 0 → univers CSC 0.
- **À vérifier sur place.** Sans matériel MA, grandMA2 onPC peut ne rien émettre faute de paramètres. CSC ne contourne pas cette limite.

### grandMA3 onPC

- Menu → DMX Protocols → Art-Net. Chaque ligne donne un « Local Universe », un « Amount », Net / Sub-Net / Universe, ou un « Art-Net Absolute » qui est la Port-Address reçue par CSC.
- Il faut activer **Enable Output** et, si la station est seule, **Send Art-Net if Idle Master**.
- En mode Auto, grandMA3 émet en unicast vers les récepteurs qu'il découvre et passe en diffusion au-delà d'un seuil. **CSC ne répond pas encore aux ArtPoll** : en cas de doute, choisir le mode **Broadcast**, ou **Unicast** avec l'IP de la machine Minecraft.
- Comme pour grandMA2, sans matériel MA il peut n'y avoir aucun paramètre à émettre.

## 10. Tests

| Test | État |
|---|---|
| A — Univers 0 seul | Hors jeu : les 512 canaux de la Port-Address 0 sont reçus dans l'univers CSC 0, et un VORTEX patché en 0.001 prend dimmer et pan de l'univers 0. **En jeu : à faire.** |
| B — Univers 0 et 1 simultanés | Hors jeu : deux jeux de données distincts sans décalage, un W19 en 1.001 ne lit que l'univers 1. **En jeu : à faire.** |
| C — Numérotation | Hors jeu : Port-Address 0, 1, 15, 16 et 256 = 0:0:0, 0:0:1, 0:0:15, 0:1:0, 1:0:0, décodées sans changement. |
| D à G — grandMA2, grandMA3, QLC+, dot2 | **Non faits** (logiciels non disponibles ici). Protocole : régler la sortie (§ 9), regarder ART-NET MONITOR (état RECEIVING, Port-Address actives) puis DMX MONITOR (valeurs du fader). |
| H — Changement d'IP à chaud | Le code ferme et rouvre seulement le socket Art-Net, et revient aux réglages précédents en cas d'échec. **En jeu : à faire.** |
| I — Plusieurs univers | Hors jeu : univers non consécutifs 0, 2, 7 ; auto-patch sur l'univers 0. **En jeu : à faire.** |
| J — Perte du signal | Code des trois comportements. **En jeu : à faire.** |
| K — Sauvegarde | Hors jeu : show sauvegardé et relu, univers 0 conservé ; fichier de réglages (univers 0-3 et 9, correspondance 16 → 0, IP, port, politique) ; enregistrement et lecture `.cscshow` de l'univers 0. **Après relance de Minecraft : à faire.** |
| L — Multijoueur | **À faire** (permissions, synchronisation). |
| M — Performances | **Non mesurées en jeu.** Hors jeu, sur cette machine : le moteur évalue 100 projecteurs depuis le DMX en environ 50 µs par image. |

**Journaux disponibles :**
- `validation/external-tests-1.2.0-rev16h.log` (résultats des tests hors jeu) ;
- journal du serveur Minecraft (« Art-Net settings by … », erreurs d'ouverture du port).

## 11. Limites connues

- Pas de réponse aux **ArtPoll** : CSC n'apparaît pas dans la liste des nœuds des consoles. La diffusion et l'unicast vers l'IP fonctionnent.
- IPv4 uniquement. Art-Net est un protocole IPv4.
- La grille montre les Port-Address 0 à 63. Au-delà, utiliser Start / End ou la correspondance.
- Les univers CSC vont de 0 à 64.
