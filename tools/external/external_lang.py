#!/usr/bin/env python3
"""French / English texts of the External tab of the LUMICORE X1 (CSC External Show Control). Same key rule as client/ui/Tr.key.
Idempotent: python3 -I tools/external/external_lang.py"""
import json, os, re
L = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'common', 'src', 'main', 'resources', 'assets', 'concertstage', 'lang')

def tr_key(english):
    text = english.strip()
    label = text.endswith(':')
    s = re.sub(r'^_+|_+$', '', re.sub(r'[^a-z0-9]+', '_', text.lower().replace('+', ' plus ')))
    return 'gui.concertstage.ui.' + s[:60] + ('_label' if label else '')

UI = {
 'External': 'Externe', 'Control': 'Contrôle', 'Internal': 'Interne', 'AutoShow': 'AutoShow', 'Playback': 'Playback',
 'The desk drives the fixtures (cue lists, programmer, timeline), AutoShow off': "La console pilote les projecteurs (listes de cues, programmer, timeline), AutoShow coupé",
 'The desk with the AutoShow allowed (as before)': "La console, avec l'AutoShow autorisé (comme avant)",
 'A third-party desk drives the fixtures over Art-Net / sACN': 'Une console externe pilote les projecteurs en Art-Net / sACN',
 'A recorded show drives the fixtures': 'Un show enregistré pilote les projecteurs',
 'Connection': 'Connexion', 'Start receiver': 'Démarrer la réception', 'Test signal': 'Signal de test',
 'Opens Art-Net (UDP 6454) and sACN (UDP 5568) on this computer or server': 'Ouvre Art-Net (UDP 6454) et sACN (UDP 5568) sur cet ordinateur ou ce serveur',
 '10 s of Art-Net to 127.0.0.1 through the receiver, to check the whole chain without another program':
     "10 s d'Art-Net vers 127.0.0.1 à travers la réception, pour vérifier toute la chaîne sans autre logiciel",
 'Accept OSC commands for this desk (port in config/concertstage-external.properties)': 'Accepter les commandes OSC pour cette console (port dans config/concertstage-external.properties)',
 '%d packets · %d rejected': '%d paquets · %d rejetés', 'No DMX received yet': 'Aucun DMX reçu pour le moment', 'lost': 'perdu',
 'No signal: holding the last values': 'Plus de signal : dernières valeurs maintenues',
 'Patch': 'Patch', 'Select fixtures (Fixtures tab)': 'Sélectionnez des projecteurs (onglet Projecteurs)', '%d selected': '%d sélectionnés',
 'Full (master dimmer, before 1.2.0 beta 2b)': 'Complet (gradateur général, avant 1.2.0 bêta 2b)',
 'Ultra Compact (colour macro, before 1.2.0)': 'Ultra Compact (macro couleur, avant 1.2.0)',
 'Compact (hue / saturation, before 1.2.0)': 'Compact (teinte / saturation, avant 1.2.0)',
 'Full': 'Full', 'Compact': 'Compact', 'Ultra': 'Ultra', 'Matrix': 'Matrix', 'Ultra Compact': 'Ultra Compact', 'Virtual Matrix': 'Virtual Matrix',
 'DMX mode of the selected fixtures (addresses kept: run the auto patch after)': "Mode DMX des projecteurs sélectionnés (adresses gardées : lancez l'auto patch ensuite)",
 'DMX mode of the selected fixtures (a fixture that no longer fits at its address moves to the next free address)': "Mode DMX des projecteurs sélectionnés (un projecteur qui ne tient plus à son adresse passe à la prochaine adresse libre)",
 'Invert pan': 'Inverser pan', 'Invert tilt': 'Inverser tilt', 'Mirror (right side)': 'Miroir (côté droit)', 'Clear transforms': 'Effacer transformations',
 'Local transform: the same DMX turns these fixtures the other way (no extra DMX control)': "Transformation locale : le même DMX tourne ces projecteurs dans l'autre sens (aucun contrôle DMX en plus)",
 'Symmetry: pan inverted for the selected fixtures east of their centre, so shared values open fans': "Symétrie : pan inversé pour les projecteurs sélectionnés à l'est de leur centre, pour que des valeurs partagées ouvrent des éventails",
 'AUTO PATCH — 1 UNIVERSE': 'AUTO PATCH — 1 UNIVERS', 'Universe %d': 'Univers %d', 'Prefer': 'Préférer',
 'Mode the auto patch starts from; it reduces it type by type only if needed': "Mode de départ de l'auto patch ; il le réduit type par type seulement si nécessaire",
 'Individual': 'Individuel', 'Share by type': 'Partage par type', 'Share by group': 'Partage par groupe',
 'Individual: one address each · Share by type / group: fixtures get the same values · Virtual Matrix: 16 channels for all':
     'Individuel : une adresse chacun · Partage par type / groupe : mêmes valeurs pour tous · Virtual Matrix : 16 canaux pour tous',
 'Fits the whole patch in this universe (512 channels): modes, sharing, addresses. Undo with the desk undo':
     "Range tout le patch dans cet univers (512 canaux) : modes, partage, adresses. Annulable avec l'annulation de la console",
 'Nothing patched': 'Rien de patché', 'No address conflict': "Aucun conflit d'adresse", 'CSC VIRTUAL MATRIX': 'CSC VIRTUAL MATRIX',
 'Order': 'Ordre', 'by number': 'par numéro', 'east-west': 'est-ouest', 'north-south': 'nord-sud',
 'Order of the fixtures along the matrix line (phase, chases, gradients)': 'Ordre des projecteurs le long de la ligne de la matrice (phase, poursuites, dégradés)',
 '%d fixtures follow it': '%d projecteurs la suivent',
 'Export': 'Export', 'Patch (CSV / JSON)': 'Patch (CSV / JSON)', 'GDTF profiles': 'Profils GDTF', 'MVR rig': 'Implantation MVR', '3D models': 'Modèles 3D',
 'Complete rig': 'Implantation complète', 'QLC+': 'QLC+',
 'EXPORT COMPLETE LIGHTING RIG: patch, GDTF, MVR, 3D models and QLC+ in one folder': 'EXPORT COMPLETE LIGHTING RIG : patch, GDTF, MVR, modèles 3D et QLC+ dans un dossier',
 'EXPORT COMPLETE LIGHTING RIG: patch, GDTF, grandMA2 / dot2 XML, MVR, 3D models and QLC+ in one folder': 'EXPORT COMPLETE LIGHTING RIG : patch, GDTF, XML grandMA2 / dot2, MVR, modèles 3D et QLC+ dans un dossier',
 'grandMA3 GDTF': 'grandMA3 GDTF', 'grandMA2 XML': 'grandMA2 XML', 'dot2 XML': 'dot2 XML',
 'Click: change the software. Channels Full / Compact / Ultra Compact:': 'Clic : changer de logiciel. Canaux Full / Compact / Ultra Compact :',
 'EXPORT FIXTURE PROFILE: every DMX mode of the fixture types, to concertstage/export': 'EXPORT FIXTURE PROFILE : tous les modes DMX des types de projecteurs, dans concertstage/export',
 'Writes to concertstage/export': 'Écrit dans concertstage/export', 'Selected fixtures only': 'Projecteurs sélectionnés seulement',
 'Import': 'Import', 'Patch CSV': 'Patch CSV', 'MVR': 'MVR',
 'Reads concertstage/import/<name>.csv (as exported): universe, address and mode by fixture number': "Lit concertstage/import/<nom>.csv (comme exporté) : univers, adresse et mode par numéro de projecteur",
 'Reads concertstage/import/<name>.mvr: address and mode of the fixtures with the same Fixture ID': 'Lit concertstage/import/<nom>.mvr : adresse et mode des projecteurs de même Fixture ID',
 'Recording': 'Enregistrement', 'Stop': 'Stop', 'Record': 'Enregistrer',
 'CSC RECORD EXTERNAL SHOW: captures the DMX the desk receives (its universes), with the time, to concertstage/recordings':
     'CSC RECORD EXTERNAL SHOW : capture le DMX reçu par la console (ses univers), avec le temps, dans concertstage/recordings',
 'Delete': 'Supprimer', 'No recording yet': 'Aucun enregistrement', 'Play (switches the desk to Playback)': 'Lecture (passe la console en Playback)', 'Restart': 'Recommencer',
 'Loop': 'Boucle', 'Playback speed 0.5x / 1x / 2x': 'Vitesse de lecture 0,5x / 1x / 2x', 'Seek': 'Aller à', 'Music': 'Musique',
 'Recorded without music': 'Enregistré sans musique',
 'Follow the MIDI timecode (device in config/concertstage-external.properties)': 'Suivre le timecode MIDI (périphérique dans config/concertstage-external.properties)',
 'Recording name': "Nom d'enregistrement", 'File in import/': 'Fichier dans import/',
 # rev. 16h: Settings > Network > Art-Net, Art-Net monitor, DMX monitor
 'Settings': 'Réglages', 'NETWORK': 'RÉSEAU', 'ART-NET': 'ART-NET', 'ART-NET MONITOR': 'MONITEUR ART-NET', 'DMX MONITOR': 'MONITEUR DMX',
 'ART-NET NETWORK SETTINGS': 'RÉGLAGES RÉSEAU ART-NET', 'Receive Art-Net on this computer or server': 'Recevoir Art-Net sur cet ordinateur ou ce serveur',
 'Local IP': 'IP locale', 'Interfaces (click to use)': 'Interfaces (cliquer pour utiliser)', 'All interfaces': 'Toutes les interfaces',
 'Listen on every IPv4 interface of this computer': "Écouter sur toutes les interfaces IPv4 de l'ordinateur",
 'Loopback: this computer only': 'Boucle locale : cet ordinateur seulement', 'Active interface': 'Interface active', 'Interface down': 'Interface désactivée',
 'REFRESH to list the interfaces': 'REFRESH pour lister les interfaces', 'UDP port': 'Port UDP', 'Default 6454': 'Défaut 6454',
 'ANY SOURCE': 'TOUTES SOURCES', 'SPECIFIC SOURCE': 'SOURCE PRÉCISE', 'Console IP (optional)': 'IP console (facultative)',
 'Source filter: every sender, or only the console IP on the right (the others are counted as filtered)':
     "Filtre de source : tous les émetteurs, ou seulement l'IP console à droite (les autres sont comptés comme filtrés)",
 'Priority': 'Priorité', 'Desk mode decides': 'Le mode de la console décide', 'Art-Net takes control': 'Art-Net prend la main',
 'Desk mode decides: Art-Net drives a desk only in External. Art-Net takes control: a desk goes to External as soon as Art-Net arrives on its universes':
     "Le mode de la console décide : Art-Net ne pilote une console qu'en mode Externe. Art-Net prend la main : la console passe en Externe dès que de l'Art-Net arrive sur ses univers",
 'Signal lost': 'Perte du signal', 'Hold last value': 'Garder la dernière valeur', 'Blackout': 'Blackout', 'Return to CSC control': 'Retour au contrôle CSC',
 'What a desk in External does after 3 s without Art-Net on its universes': "Ce que fait une console en Externe après 3 s sans Art-Net sur ses univers",
 'Allowed on this dedicated server': 'Autorisé sur ce serveur dédié',
 'Opens the UDP port on the server machine (server operators of level 4)': 'Ouvre le port UDP sur la machine du serveur (opérateurs de niveau 4)',
 'ART-NET UNIVERSES (Port-Address)': 'UNIVERS ART-NET (Port-Address)', 'Start': 'Début', 'End': 'Fin', 'Select range': 'Choisir la plage', 'Clear': 'Vider',
 'Universe Start / Universe End: receive exactly these Port-Addresses (0 is a normal universe)':
     "Univers de début / de fin : recevoir exactement ces Port-Address (0 est un univers normal)",
 'Universe %d (Art-Net %s)': 'Univers %d (Art-Net %s)', 'receiving': 'réception', '+%d selected above 63': '+%d sélectionnés au-delà de 63',
 'UNIVERSE MAPPING (Port-Address → CSC universe)': 'CORRESPONDANCE DES UNIVERS (Port-Address → univers CSC)',
 'Direct: Port-Address N drives CSC universe N (0 → 0, 1 → 1, no +1)': "Directe : la Port-Address N pilote l'univers CSC N (0 → 0, 1 → 1, pas de +1)",
 '+%d more': '+%d autres', 'Remove': 'Retirer', 'Port-Address': 'Port-Address', 'Add mapping': 'Ajouter',
 'Port-Address must be 0 to 32767': 'La Port-Address doit être entre 0 et 32767', 'Port-Address must be a number': 'La Port-Address doit être un nombre',
 'UDP port must be a number': 'Le port UDP doit être un nombre',
 'Direct (0 → 0)': 'Directe (0 → 0)', 'Default: the Art-Net Port-Address is the CSC universe number': "Par défaut : la Port-Address Art-Net est le numéro d'univers CSC",
 'Port-Address N → CSC N+1': 'Port-Address N → CSC N+1',
 'For shows patched with the old rule (Art-Net 0 = CSC universe 1)': "Pour les shows patchés avec l'ancienne règle (Art-Net 0 = univers CSC 1)",
 'APPLY': 'APPLIQUER', 'SAVE': 'ENREGISTRER', 'RESET': 'RÉINITIALISER', 'REFRESH': 'ACTUALISER', 'TEST CONNECTION': 'TESTER LA CONNEXION',
 'Use these settings now (no restart); the previous ones come back if the address cannot be opened':
     "Utiliser ces réglages tout de suite (sans redémarrer) ; les précédents reviennent si l'adresse ne peut pas être ouverte",
 'Apply and keep these settings (config/concertstage-external.properties)': 'Appliquer et garder ces réglages (config/concertstage-external.properties)',
 'Back to the defaults (0.0.0.0:6454, universes 0-15, direct mapping) and save': 'Retour aux valeurs par défaut (0.0.0.0:6454, univers 0-15, correspondance directe) et enregistrement',
 'Reload the settings and the interface list from the server': 'Recharger les réglages et la liste des interfaces depuis le serveur',
 'Sends an ArtPoll (no DMX) to the receiver and checks that it arrives': "Envoie un ArtPoll (pas de DMX) au récepteur et vérifie qu'il arrive",
 'DISABLED': 'DÉSACTIVÉ', 'LISTENING': 'EN ÉCOUTE', 'RECEIVING': 'RÉCEPTION', 'NO DATA': 'AUCUNE DONNÉE', 'BIND ERROR': "ERREUR D'ÉCOUTE",
 'INVALID PACKET': 'PAQUET INVALIDE', 'SOURCE FILTERED': 'SOURCE FILTRÉE', 'waiting for the server': 'en attente du serveur',
 'No answer from the server yet': 'Pas encore de réponse du serveur',
 'CSC ART-NET MONITOR': 'CSC ART-NET MONITOR', 'ART-NET RECEIVER': 'RÉCEPTEUR ART-NET', 'Source Filter': 'Filtre de source', 'UDP Port': 'Port UDP',
 'Detected Source': 'Source détectée', 'Protocol': 'Protocole', 'Packets Received': 'Paquets reçus', 'Packet Rate': 'Débit',
 'DMX packets used': 'Paquets DMX utilisés', 'Invalid packets': 'Paquets invalides', 'Filtered (source)': 'Filtrés (source)', 'Not selected': 'Non sélectionnés',
 'valid packets of Port-Addresses not received': 'paquets valides de Port-Address non reçues', 'Last DMX': 'Dernier DMX',
 'Universe': 'Univers', 'State': 'État', 'Packets': 'Paquets', 'Active Universes %d': 'Univers actifs %d', 'ACTIVE': 'ACTIF', 'IGNORED': 'IGNORÉ',
 'CSC universes with data: %s': 'Univers CSC avec données : %s', 'DMX MONITOR — UNIVERSE %d': 'MONITEUR DMX — UNIVERS %d', 'CSC universe %d': 'Univers CSC %d',
 'no Art-Net Port-Address feeds it': 'aucune Port-Address Art-Net ne le pilote', 'from Art-Net Port-Address %d (%s)': 'depuis la Port-Address Art-Net %d (%s)',
 'LOST (last values)': 'PERDU (dernières valeurs)', 'Last Update': 'Dernière mise à jour', 'amber: changed · hover: fixture': 'ambre : modifié · survol : projecteur',
 'Channel %03d = %d': 'Canal %03d = %d',
}

for name, idx in (('en_us.json', 0), ('fr_fr.json', 1)):
    p = os.path.join(L, name)
    d = json.load(open(p, encoding='utf8'))
    for en, fr in UI.items():
        k = tr_key(en)
        if k in d and idx == 1 and d[k] != fr and d[k] != en:
            continue
        d[k] = en.strip() if idx == 0 else fr
    json.dump(d, open(p, 'w', encoding='utf8'), ensure_ascii=False, indent=1)
print(len(UI), 'texts')
