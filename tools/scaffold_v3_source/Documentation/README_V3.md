# CSC Stage Platforms & Scaffolding — V3

Modèles **.bbmodel** uniquement (format Blockbench « free », textures intégrées). Aucune intégration Java : le mod n'est pas modifié.
Échelle **16 u = 1 m = 1 bloc** ; Y hauteur, X largeur, Z profondeur. Non ouverts dans Blockbench de mon côté : vérifiés par script (JSON, UV, outliner, collisions).

## Contenu (91 modèles)
| Dossier | Contenu |
|---|---|
| StageDecks | StageDeck 100 (1×1), 200 (2×1), 200 Square (2×2), 100 Half (1×0,5), 200 Half (2×0,5) × finitions Black / Wood / Metal. Construction reprise du deck 2×1 existant (cadre 90 mm, récepteurs 60×60, mêmes textures), redimensionnée + coins renforcés. |
| Platform_Legs | Pieds fixes 020–120, réglables 040–120, télescopiques 080-120 / 120-200, renforcés 120/200/300. Origine au sol, sommet calé dans le récepteur du deck (hauteur de surface = nom). |
| Platform_Connectors | Serre-deck, collier de pied Ø48, contreventements en X 1 m / 2 m. |
| Scaffolding | Système multidirectionnel à rosaces : montants 0,5/1/2/3 m, lisses O et U 0,5/1/2/3 m, diagonales 1×2/2×2/3×2 m, vérin + collier de base, planchers acier 0,32 m, garde-corps (0,5 + 1,0 m + plinthe), portillon, échelle. |
| Tower_PassThrough | Passages mât / sleeve : centraux, latéraux (SidePass), d'angle (CornerPass), en échafaudage et en système deck (StageDeck_Tower_*). |
| LOD | Versions allégées de tous les passages (mêmes ouvertures, repères et pivots — testées). |
| Preassembled_Structures | Drum Riser, DJ, Keyboard, Technical, FOH + 5 démonstrations avec tours (dont la scène à 4 tours). |

## Groupes Blockbench
Main Frame, Vertical Standards, Horizontal Ledgers, Diagonal Braces, Platforms, Guardrails, Connectors, Leg Supports, Access, **Tower Clearance** (repères vides, aucune géométrie), **Attachment Points** (CSC_Connection_*, Tower_Axis, Leg_Socket*).

## Correspondance avec l'existant (rien n'est supprimé ni renommé)
| Existant 1.6.0 | V3 |
|---|---|
| stage_deck_2x1_without_legs | CSC_StageDeck_200_Black (même géométrie) + autres tailles/finitions |
| stage_extension_1x1 / 2x0p5 | CSC_StageDeck_100 / 200_Half (plateau seul) |
| adjustable_leg_h040/080/120 | CSC_Leg_Adjustable_040/080/120 (+ 060/100) |
| drum_riser_4x2_h060, dj_platform_2x2_h080 | CSC_DrumRiser_2x2_H040, CSC_DJ_Platform_2x2_H080 (nouvelles configurations, les anciennes restent) |
| stage_stairs_h040/080/120, safety_railings_* | réutilisés tels quels dans les assemblages |

## Voir aussi
- `Passages_Tours.md` : mesures des tours, fiches des ouvertures, matrice de compatibilité, limites, résultats de tests.
- `clearance_volumes.json` : volumes réservés (u et blocs) pour la future gestion des collisions.
- `validation_passages.json`, `validation_demos.json`, `inventaire_et_reperes.json`.
- `REFERENCES_INTERNES.md` : références réelles (usage interne uniquement).
- `scripts/` : générateurs et tests (Python) pour régénérer ou ajuster.
