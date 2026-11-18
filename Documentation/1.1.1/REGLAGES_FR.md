# CSC 1.1.1 — nouveaux réglages

## Rendu (touche K, ou Config dans la liste des mods)

| Réglage | Fichier `config/concertstage-client.properties` | Effet |
|---|---|---|
| Faible / Moyen / Élevé | `render_quality=LOW / MEDIUM / HIGH` | Comme en 1.1.0 : distance des pièces du mod (50 / 75 / 100 % de la distance de Minecraft), maillage détaillé des tours, petite quincaillerie. |
| **AUTO** (bouton en bas à gauche) | `render_quality=AUTO` | La qualité suit les FPS : un cran de moins après 3 s sous 40 FPS, un cran de plus après 15 s au-dessus de 75 FPS, au plus un changement toutes les 20 s, jamais au-dessus de Élevé. Cliquer une carte repasse en manuel. Ne modifie jamais un appareil. |
| Cache GPU | `gpu_meshes=true / false` | Inchangé. |

Le profil son « Performance / Balanced / High quality » règle toujours aussi la qualité de rendu (et repasse donc en manuel).

## Son (console → Concert Stage - Sound)

| Onglet | Réglage | Fichier `config/concertstage-audio.properties` | Défaut |
|---|---|---|---|
| System | **Gain de sortie** x0,25 à x4, affiché en dB | `output_gain` (+ `gain_model=2`) | x1 = calibré : en façade, à peu près le niveau du titre lui-même ; au-delà, le limiteur travaille davantage. Un ancien réglage est converti une fois (÷ 2,8). |
| System | **Têtes** 0–200 % | `level_mains` | 100 % |
| System | **Caissons** 0–200 % | `processing.12` (inchangé) | 100 % |
| System | **Retours** 0–200 % | `level_monitors` | 100 % |
| — | Portée des retours | `range_factor.monitors` | 0.22 (≈ 25 blocs) |
| — | Portée des AUREX M12 | `range_factor.aurex_main` | 1.0 |
| Diag. | **Niveau de sortie** : LUFS (3 s), crête, réduction du limiteur, groupes mixés | — | lecture seule |

Ces réglages sont propres à chaque joueur ; le mix de la console (faders, mutes, retards de groupes) reste partagé. Le son passe
toujours par les curseurs Général et Juke-box / Blocs musicaux de Minecraft.

Crossover subs / têtes : onglet System, « LR24 crossover » (50–200 Hz, 100 Hz par défaut), comme avant.
