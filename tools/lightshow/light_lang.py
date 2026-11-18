#!/usr/bin/env python3
"""French / English texts of the lighting content (1.2.0): names, tooltips, messages, and every text of the LUMICORE X1 screen
(keys gui.concertstage.ui.<text>, same rule as client/ui/Tr.key). Idempotent: python3 -I tools/lightshow/light_lang.py"""
import json, os, re
L = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'common', 'src', 'main', 'resources', 'assets', 'concertstage', 'lang')

def tr_key(english):
    text = english.strip()
    label = text.endswith(':')
    s = re.sub(r'^_+|_+$', '', re.sub(r'[^a-z0-9]+', '_', text.lower().replace('+', ' plus ')))
    return 'gui.concertstage.ui.' + s[:60] + ('_label' if label else '')

FIXED = {  # key: (en, fr)
 'block.concertstage.csc_vortex_x1': ('CSC VORTEX X1 — Beam / Spot', 'CSC VORTEX X1 — Beam / Spot'),
 'block.concertstage.csc_lumora_w7': ('CSC LUMORA W7 — Compact Wash', 'CSC LUMORA W7 — Wash compact'),
 'block.concertstage.csc_lumora_w19': ('CSC LUMORA W19 — High Power Wash', 'CSC LUMORA W19 — Wash haute puissance'),
 'block.concertstage.csc_pulsestrike_m': ('CSC PULSESTRIKE M — Motorised Strobe / Blinder', 'CSC PULSESTRIKE M — Strobe / Blinder motorisé'),
 'block.concertstage.csc_lumicore_x1': ('CSC LUMICORE X1 — Lighting Desk', 'CSC LUMICORE X1 — Console lumière'),
 'item.concertstage.csc_light_patch_tool': ('CSC Light Patch Tool', 'CSC Light Patch Tool (outil de patch)'),
 'itemGroup.concertstage.lighting': ('CSC Lighting', 'CSC Lumière'),
 'tooltip.concertstage.fixture.csc_vortex_x1': ('Beam / spot: pan 540°, tilt 270°, colour, zoom 2–32°, focus, iris, 8 rotating gobos, prism, strobe',
                                                'Beam / spot : pan 540°, tilt 270°, couleur, zoom 2–32°, focus, iris, 8 gobos tournants, prisme, strobe'),
 'tooltip.concertstage.fixture.csc_lumora_w7': ('Wash: pan / tilt, RGBW, zoom 8–50°, 19 LED cells (pixel effects), strobe',
                                                'Wash : pan / tilt, RGBW, zoom 8–50°, 19 cellules LED (effets pixel), strobe'),
 'tooltip.concertstage.fixture.csc_lumora_w19': ('Wash: pan / tilt, RGBW, zoom 6–60°, 37 LED cells (pixel effects), strobe',
                                                 'Wash : pan / tilt, RGBW, zoom 6–60°, 37 cellules LED (effets pixel), strobe'),
 'tooltip.concertstage.fixture.csc_pulsestrike_m': ('Strobe / blinder: motorised tilt, 14 RGB zones, 2 × 14 white segments, flashes and chases',
                                                    'Strobe / blinder : tilt motorisé, 14 zones RGB, 2 × 14 segments blancs, flashs et poursuites'),
 'tooltip.concertstage.fixture_place': ('On the floor, or under a truss (hung upside down). Patch it to a CSC LUMICORE X1.',
                                        'Au sol, ou sous un truss (suspendu, tête en bas). À patcher sur une CSC LUMICORE X1.'),
 'tooltip.concertstage.patch_desk': ('Desk: %s %s %s', 'Console : %s %s %s'),
 'tooltip.concertstage.patch_tool_1': ('Right click a LUMICORE X1: choose the desk', 'Clic droit sur une LUMICORE X1 : choisit la console'),
 'tooltip.concertstage.patch_tool_2': ('Right click a fixture: patch it (sneak: unpatch)', 'Clic droit sur un projecteur : le patche (accroupi : le dépatche)'),
 'tooltip.concertstage.patch_tool_3': ('Use in the air: patch every free fixture within 48 blocks', 'Utiliser dans le vide : patche tous les projecteurs libres à 48 blocs'),
 'message.concertstage.fixture_info': ('%s: fixture %s of the desk at %s %s %s', '%s : projecteur %s de la console en %s %s %s'),
 'message.concertstage.fixture_unpatched': ('%s: not patched (CSC Light Patch Tool, or Auto-patch on the desk)', '%s : non patché (CSC Light Patch Tool, ou Auto-patch sur la console)'),
 'message.concertstage.light_audio_linked': ('Audio console linked (%s %s %s)', 'Console audio reliée (%s %s %s)'),
 'message.concertstage.light_busy': ('An analysis is already running on this desk', 'Une analyse est déjà en cours sur cette console'),
 'message.concertstage.light_import_bad': ('Not a valid CSC show file', "Ce n'est pas un fichier de show CSC valide"),
 'message.concertstage.light_imported': ('Show imported: %s', 'Show importé : %s'),
 'message.concertstage.light_library_full': ('The desk library is full', 'La bibliothèque de la console est pleine'),
 'message.concertstage.light_no_audio': ('No CSC audio console linked (Fixtures tab: link the nearest one)', 'Aucune console audio CSC reliée (onglet Fixtures : relier la plus proche)'),
 'message.concertstage.light_no_fixtures': ('Patch some fixtures first', "Patchez d'abord des projecteurs"),
 'message.concertstage.light_no_permission': ('You may not operate this desk', "Vous n'avez pas le droit d'utiliser cette console"),
 'message.concertstage.light_no_track': ('The audio console has no track selected', "La console audio n'a pas de titre choisi"),
 'message.concertstage.light_nothing_undo': ('Nothing to undo', 'Rien à annuler'),
 'message.concertstage.light_nothing_redo': ('Nothing to redo', 'Rien à rétablir'),
 'message.concertstage.light_patched': ('%s fixtures patched (%s on the desk)', '%s projecteurs patchés (%s sur la console)'),
 'message.concertstage.light_player_unknown': ('Unknown player: %s', 'Joueur inconnu : %s'),
 'message.concertstage.light_too_far': ('Too far from the desk', 'Trop loin de la console'),
 'message.concertstage.light_track_missing': ('The track file is not on the server', "Le fichier du titre n'est pas sur le serveur"),
 'message.concertstage.patch_added': ('Patched as fixture %s (%s on the desk)', 'Patché comme projecteur %s (%s sur la console)'),
 'message.concertstage.patch_already': ('Already patched (fixture %s)', 'Déjà patché (projecteur %s)'),
 'message.concertstage.patch_desk_selected': ('Desk selected: %s %s %s', 'Console choisie : %s %s %s'),
 'message.concertstage.patch_no_desk': ('Right click a CSC LUMICORE X1 first', "Faites d'abord un clic droit sur une CSC LUMICORE X1"),
 'message.concertstage.patch_removed': ('Fixture unpatched', 'Projecteur dépatché'),
}

UI = {  # English text (as in the code) -> French
 'Fixtures': 'Projecteurs', 'Groups': 'Groupes', 'Program': 'Programmer', 'Color': 'Couleur', 'Position': 'Position', 'Effects': 'Effets',
 'Cues': 'Cues', 'Playback': 'Playback', 'Timeline': 'Timeline', 'AutoShow': 'AutoShow', 'Shows': 'Shows', 'Help': 'Aide',
 'Search…': 'Rechercher…', 'New name': 'Nouveau nom', 'Group name': 'Nom du groupe', 'Preset name': 'Nom du preset', 'Cue name': 'Nom de la cue',
 'Cue no.': 'N° de cue', 'Show name': 'Nom du show', 'Player': 'Joueur', 'Marker': 'Marqueur', 'audio linked': 'audio reliée', 'no audio': 'pas d’audio',
 '%d fixtures · %d selected · %s': '%d projecteurs · %d sélectionnés · %s', 'BLACKOUT': 'BLACKOUT', 'Everything dark at once (B)': 'Tout noir immédiatement (B)',
 'STOP SHOW': 'STOP SHOW', 'Stops every playback and the timeline': 'Arrête tous les playbacks et la timeline', 'NO STROBE': 'SANS STROBE',
 'Strobes held open (no flashing)': 'Strobes tenus ouverts (aucun flash)', 'MOVE LIMIT': 'LIMITE MOUV.', 'Small, slow movements only': 'Mouvements petits et lents seulement',
 'HOME': 'REPOS', 'Every fixture back to its rest position, lamps at zero': 'Tous les projecteurs en position de repos, lampes à zéro',
 'STOP AUTO': 'STOP AUTO', 'Stops the AutoShow at once (click again to allow it)': "Arrête l'AutoShow immédiatement (recliquer pour le réautoriser)",
 'Grand Master': 'Grand Master', 'Undo': 'Annuler', 'Redo': 'Rétablir', 'Patch': 'Patch',
 'No.   Name                 Type              U.Addr   Mount': 'N°    Nom                  Type              U.Adr    Pose',
 'All': 'Tous', 'None': 'Aucun', 'Odd': 'Impairs', 'Even': 'Pairs', 'Select every fixture of this type': 'Sélectionne tous les projecteurs de ce type',
 'Auto-patch (48 blocks)': 'Auto-patch (48 blocs)', 'Patches every CSC fixture around the desk that has no desk yet': 'Patche tous les projecteurs CSC autour de la console qui n’ont pas encore de console',
 'Highlight': 'Repérer', 'Full white on the selection, to find them on stage': 'Blanc plein sur la sélection, pour les trouver sur scène', 'Unpatch': 'Dépatcher',
 'Rename': 'Renommer', 'Clean up': 'Nettoyer', 'Unpatches the fixtures that are no longer in the world': 'Dépatche les projecteurs qui ne sont plus dans le monde',
 'Audio console linked ✓': 'Console audio reliée ✓', 'Link nearest audio console': 'Relier la console audio la plus proche',
 'The CSC Relay that plays the music the lights follow': 'La CSC Relay qui joue la musique que les lumières suivent', 'Universe': 'Univers', 'Address': 'Adresse',
 'Front Wash': 'Face Wash', 'Back Wash': 'Contre Wash', 'Beams': 'Beams', 'Strobes': 'Strobes', 'Stage Left': 'Jardin', 'Stage Right': 'Cour', 'Audience': 'Public',
 'click = select, Shift = add': 'clic = sélectionner, Maj = ajouter', 'Store the selection as:': 'Enregistrer la sélection sous :', 'Store (name above)': 'Enregistrer (nom ci-dessus)',
 'Selected group:': 'Groupe choisi :', 'Update': 'Mettre à jour', 'Replace the fixtures of this group by the selection': 'Remplace les projecteurs de ce groupe par la sélection',
 'Delete group': 'Supprimer le groupe', 'Select fixtures first': "Sélectionnez d'abord des projecteurs",
 'Select fixtures (Fixtures or Groups tab) to program them.': 'Sélectionnez des projecteurs (onglet Projecteurs ou Groupes) pour les programmer.',
 'Intensity': 'Intensité', 'Full': 'Plein', 'Home position': 'Position de repos', 'Beam': 'Faisceau', 'Gobo': 'Gobo', 'Prism': 'Prisme', 'IN': 'IN', 'OUT': 'OUT',
 'Tight shaft: zoom closed, iris small': 'Faisceau serré : zoom fermé, iris petit', 'Spot': 'Spot', 'Wider spot, iris open, for gobos': 'Spot plus large, iris ouvert, pour les gobos',
 'Pixels / LED zones': 'Pixels / zones LED', 'No LED zones on the selection.': 'Aucune zone LED dans la sélection.',
 'Click: zone on / off · right click: release': 'Clic : zone allumée / éteinte · clic droit : libérer', 'Odd/Even': 'Impairs/Pairs', 'Gradient': 'Dégradé', 'Clear': 'Effacer',
 'Pattern on the zones of the selection, in the colour of the Color tab': "Motif sur les zones de la sélection, dans la couleur de l'onglet Couleur",
 'Clear selection values': 'Effacer les valeurs de la sélection', 'Clear programmer': 'Vider le programmer', 'Delete key': 'Touche Suppr',
 'Colour wheel: drag to choose hue and saturation': 'Roue chromatique : glisser pour choisir teinte et saturation', 'Brightness': 'Luminosité',
 'Red': 'Rouge', 'Green': 'Vert', 'Blue': 'Bleu', 'White': 'Blanc', 'Quick colours': 'Couleurs rapides', 'Saved colours': 'Couleurs enregistrées', 'Save colour': 'Enregistrer la couleur',
 'Right click a saved colour to delete it': 'Clic droit sur une couleur enregistrée pour la supprimer', 'Gradient over the selection': 'Dégradé sur la sélection',
 'Click: take the current colour': 'Clic : prendre la couleur actuelle', 'Apply gradient': 'Appliquer le dégradé', 'Colour presets': 'Presets couleur',
 'Position presets': 'Presets position', 'Presets': 'Presets', 'click = apply, right click = delete': 'clic = appliquer, clic droit = supprimer',
 'Store preset (selection)': 'Enregistrer le preset (sélection)', 'Pan / Tilt pad': 'Pad Pan / Tilt',
 'Drag: absolute pan (horizontal) and tilt (vertical) of the selection': 'Glisser : pan (horizontal) et tilt (vertical) absolus de la sélection',
 'Home / rest position': 'Position de repos', 'Build standard positions': 'Créer les positions standard',
 'Stage centre, audience, upstage, cross, vertical beams, fan out, rest — computed from where each fixture really is':
     'Centre scène, public, fond de scène, croisement, faisceaux verticaux, éventail, repos — calculés depuis la place réelle de chaque projecteur',
 'Positions are aimed per fixture from the rig geometry.': "Les positions sont visées projecteur par projecteur d'après la géométrie du kit.",
 'Effect engine — runs on the selection, recorded into the next cue': "Moteur d'effets — tourne sur la sélection, enregistré dans la prochaine cue",
 'Beats per cycle (/16)': 'Temps par cycle (/16)', 'Speed (cycles/s, /4)': 'Vitesse (cycles/s, /4)', 'Hz': 'Hz', 'Amplitude': 'Amplitude', 'Phase spread': 'Décalage de phase',
 'Duty / width': 'Largeur / rapport', 'Fade in (/10 s)': 'Fondu d’entrée (/10 s)', 'BPM sync': 'Synchro BPM',
 'Locks the effect to the beat of the analysed track (or the show tempo)': 'Cale l’effet sur le temps du titre analysé (ou le tempo du show)',
 'Direction': 'Sens', 'Symmetric': 'Symétrique', 'Phase mirrored from the middle of the selection': 'Phase en miroir depuis le milieu de la sélection',
 'Chase colours (saved colours):': 'Couleurs de la poursuite (couleurs enregistrées) :', 'Run on selection': 'Lancer sur la sélection',
 'Stop programmer effects': 'Arrêter les effets du programmer', 'Running in the programmer:': 'En cours dans le programmer :', 'New cue list': 'Nouvelle liste de cues',
 'Cue    Name                      In     Out    Delay  Follow  Trig.  Prio': 'Cue    Nom                       Entr.  Sort.  Délai  Suite   Décl.  Prio',
 'FLW': 'SUI', 'TC': 'TC', 'GO': 'GO', 'No cue list yet: Record creates one.': 'Pas encore de liste de cues : Record en crée une.',
 'Record mask:': "Masque d'enregistrement :", 'Intensity / Position / Color / Beam (none lit = all)': 'Intensité / Position / Couleur / Faisceau (aucun allumé = tout)',
 'Fade (s, /10)': 'Fondu (s, /10)', 'Record': 'Record', 'Stores the programmer as the next cue (R)': 'Enregistre le programmer comme cue suivante (R)',
 'Merges the programmer into the selected cue': 'Fusionne le programmer dans la cue choisie', 'Cue': 'Cue', 'Fade in': "Fondu d'entrée", 'Fade out': 'Fondu de sortie',
 'Delay': 'Délai', 'Follow': 'Enchaîner', 'Timecode': 'Timecode', 'Trigger': 'Déclenchement', 'Priority': 'Priorité', 'Apply name/no.': 'Appliquer nom/n°',
 'Go to': 'Aller à', 'Copy': 'Copier', 'Delete': 'Supprimer', 'Higher priority lists win the positions and colours': 'Les listes plus prioritaires gagnent les positions et couleurs',
 'Loop': 'Boucle', 'Rename list': 'Renommer la liste', 'Delete list': 'Supprimer la liste', 'Back': 'Retour', 'Pause': 'Pause',
 'Stop (releases with the fade out)': 'Stop (libère avec le fondu de sortie)', 'FLASH': 'FLASH', 'Held: full level': 'Maintenu : niveau plein',
 'Record cues (Cues tab) or generate an AutoShow to get playbacks.': "Enregistrez des cues (onglet Cues) ou générez un AutoShow pour avoir des playbacks.",
 'Space = GO of the selected list': 'Espace = GO de la liste choisie', 'Track': 'Titre', '(none: use the current track)': '(aucun : titre en cours)',
 'Offset': 'Décalage', 'STOP': 'STOP', 'Accent': 'Accent', 'drag = move, right click = delete': 'glisser = déplacer, clic droit = supprimer',
 'Click: preview the light at that time · Shift+click: add GO (cue selected in Cues) · wheel: zoom · Shift+wheel: scroll · right click: end preview':
     'Clic : prévisualiser la lumière à cet instant · Maj+clic : ajouter un GO (cue choisie dans Cues) · molette : zoom · Maj+molette : défiler · clic droit : fin de prévisualisation',
 'Preview': 'Aperçu', 'ARMED': 'ARMÉE', 'OFF': 'OFF', 'Armed: the events fire with the music of the linked audio console': 'Armée : les événements partent avec la musique de la console audio reliée',
 'Use current track': 'Prendre le titre en cours', 'Lights earlier (20 ms)': 'Lumière plus tôt (20 ms)', 'Lights later (20 ms)': 'Lumière plus tard (20 ms)',
 'Clear events': 'Effacer les événements', 'Accent here': 'Accent ici',
 'A short flash of the selected group (Groups tab) at the preview cursor or the playhead': "Un court flash du groupe choisi (onglet Groupes) au curseur d'aperçu ou à la tête de lecture",
 'STOP list here': 'STOP de la liste ici', 'Your light delay': 'Votre délai lumière', 'Only for you: lights earlier (your sound buffering)': 'Pour vous seulement : lumière plus tôt (mémoire tampon de votre son)',
 'Only for you: lights later': 'Pour vous seulement : lumière plus tard', 'CSC AUTOshow Engine': 'CSC AUTOshow Engine', 'Style': 'Style', 'Mode': 'Mode',
 'Pre-analysis (editable timeline)': 'Pré-analyse (timeline modifiable)', 'Live analysis (during playback)': 'Analyse en direct (pendant la lecture)', 'Movement': 'Mouvement',
 'Colour variety': 'Variété des couleurs', 'Off: no strobe and no flash accent (photosensitive audience)': 'Éteint : ni strobe ni accent flash (public photosensible)',
 'Max strobe': 'Strobe max', 'Effects used': 'Effets utilisés', 'Favourite colours (saved colours)': 'Couleurs préférées (couleurs enregistrées)',
 'Save colours in the Color tab first.': "Enregistrez d'abord des couleurs dans l'onglet Couleur.",
 'Link the CSC audio console first (Fixtures tab), then play a track.': "Reliez d'abord la console audio CSC (onglet Projecteurs), puis lancez un titre.",
 'Analyse the track and generate': 'Analyser le titre et générer',
 'Analyses the current track of the linked audio console (tempo, beats, sections, repetitions) and writes the AutoShow cue list and timeline':
     "Analyse le titre en cours de la console audio reliée (tempo, temps, sections, répétitions) et écrit la liste de cues et la timeline de l'AutoShow",
 'Start live AutoShow': "Lancer l'AutoShow en direct", 'Follows the music while it plays, with stabilised changes': 'Suit la musique pendant la lecture, avec des changements stabilisés',
 'Stop live': 'Arrêter le direct', 'Ready.': 'Prêt.',
 'The result is a normal show: edit its cues (Cues) and events (Timeline), or record over it. STOP AUTO stops it at once.':
     'Le résultat est un show normal : modifiez ses cues (Cues) et ses événements (Timeline), ou enregistrez par-dessus. STOP AUTO l’arrête immédiatement.',
 'Current show': 'Show en cours', 'Save': 'Enregistrer', 'Saves the current show in the desk library under the name above': 'Enregistre le show en cours dans la bibliothèque de la console sous le nom ci-dessus',
 'New show': 'Nouveau show', 'Empty show, same patch': 'Show vide, même patch', 'Desk library': 'Bibliothèque de la console', 'Load': 'Charger', 'Files': 'Fichiers',
 'Export current show': 'Exporter le show en cours', 'Imports this file into the desk library': 'Importe ce fichier dans la bibliothèque de la console',
 'No .cscshow file in the shows folder': 'Aucun fichier .cscshow dans le dossier shows', 'Access': 'Accès', 'Owner': 'Propriétaire', 'Public desk': 'Console publique',
 'Public: every player may use it. Otherwise the owner, the operators and the server operators': 'Publique : tous les joueurs peuvent l’utiliser. Sinon le propriétaire, les opérateurs de la console et les opérateurs du serveur',
 'Add': 'Ajouter', 'Gives a player the right to use this desk': 'Donne à un joueur le droit d’utiliser cette console', 'Autosave': 'Sauvegarde auto',
 'Low': 'Faible', 'Medium': 'Moyenne', 'High': 'Élevée', 'Ultra': 'Ultra', 'Your own beam quality (only on your screen)': 'Votre qualité de faisceaux (sur votre écran seulement)',
 'Auto performance': 'Performance auto', 'Draws fewer beam details when the frame rate drops (never changes the show)': 'Dessine moins de détails de faisceaux quand les images par seconde baissent (ne change jamais le show)',
 'Exported': 'Exporté', 'Export failed': "Échec de l'export", 'Not a valid show file': "Ce n'est pas un fichier de show valide", 'Import failed': "Échec de l'import",
 'Your first show, step by step': 'Votre premier show, pas à pas',
 'Place CSC fixtures (on the floor, or under a truss), then patch them: Fixtures tab > Auto-patch, or the CSC Light Patch Tool.':
     'Posez des projecteurs CSC (au sol ou sous un truss), puis patchez-les : onglet Projecteurs > Auto-patch, ou le CSC Light Patch Tool.',
 'Select fixtures: Fixtures tab (click, Shift / Ctrl for several) or Groups tab.': 'Sélectionnez des projecteurs : onglet Projecteurs (clic, Maj / Ctrl pour plusieurs) ou onglet Groupes.',
 'Give them intensity and a colour: Program tab (Dimmer) and Color tab (wheel, quick colours).': 'Donnez-leur une intensité et une couleur : onglet Programmer (Dimmer) et onglet Couleur (roue, couleurs rapides).',
 'Record cue 1: Cues tab > Record. Change something, record cue 2.': 'Enregistrez la cue 1 : onglet Cues > Record. Changez quelque chose, enregistrez la cue 2.',
 'Play it: Playback tab > GO (or Space). Faders scale the intensity; Back, Pause, Stop, Flash.': 'Jouez-la : onglet Playback > GO (ou Espace). Les faders dosent l’intensité ; Retour, Pause, Stop, Flash.',
 'Music: link the CSC audio console (Fixtures tab), play a track, then AutoShow tab > Analyse and generate.': 'Musique : reliez la console audio CSC (onglet Projecteurs), lancez un titre, puis onglet AutoShow > Analyser et générer.',
 'Keyboard': 'Clavier',
 'Space GO · B blackout · R record · Delete clear programmer · Ctrl+A select all · 1-9 groups · Ctrl+Z / Ctrl+Y undo / redo · Tab next work space':
     'Espace GO · B blackout · R record · Suppr vider le programmer · Ctrl+A tout sélectionner · 1-9 groupes · Ctrl+Z / Ctrl+Y annuler / rétablir · Tab espace de travail suivant',
 'Safety: BLACKOUT, STOP SHOW, NO STROBE (flashes held open), MOVE LIMIT, HOME (rest), STOP AUTO are always at the bottom. Strobes are capped at 10 Hz by default.':
     'Sécurité : BLACKOUT, STOP SHOW, SANS STROBE (flashs tenus ouverts), LIMITE MOUV., REPOS, STOP AUTO sont toujours en bas. Les strobes sont limités à 10 Hz par défaut.',
 'Circle': 'Cercle', 'Wave': 'Vague', 'Fan': 'Éventail', 'Chase': 'Poursuite', 'Pulse': 'Pulsation', 'Sweep': 'Balayage', 'Dimmer Chase': 'Poursuite dimmer',
 'Color Chase': 'Poursuite couleur', 'Pixel': 'Pixel', 'Concert Pop': 'Concert Pop', 'EDM Festival': 'EDM Festival', 'Rock Live': 'Rock Live', 'Ambient': 'Ambient', 'Custom': 'Personnalisé',
 'Dim': 'Dimmer', 'Pan': 'Pan', 'Tilt': 'Tilt', 'CTO': 'CTO', 'Zoom': 'Zoom', 'Focus': 'Focus', 'Iris': 'Iris', 'Gobo rot': 'Rot. gobo', 'Prism rot': 'Rot. prisme', 'Strobe': 'Strobe',
 'fixtures': 'projecteurs', 'beats': 'temps', 'hung': 'suspendu', 'standing': 'au sol',
 'Live AutoShow stopped': 'AutoShow en direct arrêté', 'Live AutoShow stopped (track changed)': 'AutoShow en direct arrêté (titre changé)', 'Analysis failed': "Échec de l'analyse",
 'Haze': 'Brume', 'Stage light': 'Lum. scène', "Without Shimmer: CSC's own coloured lights on the truss, roof, tarps and platforms around the fixtures": 'Sans Shimmer : lumières colorées de CSC sur le truss, le toit, les bâches et les plateformes autour des projecteurs', 'Shimmer light': 'Lumière Shimmer', 'Beams': 'Faisceaux',
 'How visible the beams are in the air (0.5 clean air to 2 thick smoke), only on your screen': "Visibilité des faisceaux dans l'air (0,5 air pur à 2 fumée épaisse), sur votre écran seulement",
 'With Shimmer installed: the beams light the blocks they hit in their colour': 'Avec Shimmer installé : les faisceaux éclairent les blocs touchés de leur couleur',
 'Intro': 'Intro', 'Verse': 'Couplet', 'Build': 'Montée', 'Chorus': 'Refrain', 'Drop': 'Drop', 'Break': 'Break', 'Outro': 'Outro', 'Silence': 'Silence', 'Washes': 'Washes',
 # rev. 16: atmosphere presets and diagnostic views
 'None': 'Aucune', 'Light': 'Légère', 'Medium': 'Moyenne', 'Dense': 'Dense',
 'Haze in the air: none (beams almost invisible, surfaces still lit), light, medium, dense — only on your screen':
     "Brume dans l'air : aucune (faisceaux presque invisibles, surfaces toujours éclairées), légère, moyenne, dense — sur votre écran seulement",
 # rev. 17: brightness of the light
 'Lens glow': 'Halo lentilles',
 'Brightness of the beams in the haze: 60, 100, 140, 200 % (only on your screen)': 'Luminosité des faisceaux dans la brume : 60, 100, 140, 200 % (sur votre écran seulement)',
 'Brightness of the light the fixtures project on the stage, the truss and the set: 60, 100, 140, 200 % (only on your screen)':
     'Luminosité de la lumière projetée par les projecteurs sur la scène, le truss et le décor : 60, 100, 140, 200 % (sur votre écran seulement)',
 'Glow around the lit lenses: 60, 100, 140, 200 % (only on your screen)': 'Halo autour des lentilles allumées : 60, 100, 140, 200 % (sur votre écran seulement)',
 'Diagnostic': 'Diagnostic', 'Off': 'Off', 'Beams only': 'Faisceaux seuls', 'Surfaces only': 'Surfaces seules', 'Emitters only': 'Émetteurs seuls',
 'Occlusions': 'Occultations', 'Optical masks': 'Masques optiques',
 'Visibility / shadows': 'Visibilité / ombres', 'Depth': 'Profondeur', 'Normals': 'Normales',
 'Diagnostic views (your screen only, not saved): beams only, surfaces only, emitters only, occlusions (green lit / red shadow), optical masks, depth, normals. Counters on the F3 screen':
     "Vues de diagnostic (votre écran seulement, non sauvegardé) : faisceaux seuls, surfaces seules, émetteurs seuls, occultations (vert éclairé / rouge ombre), masques optiques, profondeur, normales. Compteurs sur l'écran F3",
}

for name, idx in (('en_us.json', 0), ('fr_fr.json', 1)):
    p = os.path.join(L, name)
    d = json.load(open(p, encoding='utf8'))
    for k, v in FIXED.items(): d[k] = v[idx]
    for en, fr in UI.items():
        k = tr_key(en)
        if k in d and idx == 1 and d[k] != fr and d[k] != en:
            continue        # keep an existing audio translation that uses the same key
        d[k] = en.strip() if idx == 0 else fr
    json.dump(d, open(p, 'w', encoding='utf8'), ensure_ascii=False, indent=1)
print(len(FIXED), 'fixed keys,', len(UI), 'screen texts')
