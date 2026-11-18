#!/usr/bin/env python3
"""1.1.1 audio fix texts (EN / FR). Same key rule as client/ui/Tr.key. Idempotent: python3 -I tools/audio_fix/lang_audio_fix.py"""
import json, os, re
L = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'common', 'src', 'main', 'resources', 'assets', 'concertstage', 'lang')

def ui(english):
    t = english.strip(); label = t.endswith(':')
    s = re.sub(r'^_+|_+$', '', re.sub(r'[^a-z0-9]+', '_', t.lower().replace('+', ' plus ')))[:60]
    return 'gui.concertstage.ui.' + s + ('_label' if label else '')

UI = {
 'auto (sub model)': 'auto (modèle de caisson)', 'Auto': 'Auto', 'Gentle': 'Douce', 'Realistic': 'Réaliste', 'Strong': 'Forte', 'Legacy': 'Ancienne',
 'Diffusion distance': 'Distance de diffusion', 'Spatialisation': 'Spatialisation', 'Towers: auto': 'Rappels : auto',
 'Towers: Delay group': 'Rappels : groupe Delay', 'Towers: off': 'Rappels : non', 'Tower trim': 'Avance rappel', 'Auto delays': 'Délais auto',
 'Propagation': 'Propagation', 'Delay tower': 'Tour de rappel', 'Other': 'Autre',
 'Console %s: %d arrays, %d delay towers (classified %d x, last %.2f ms)': 'Console %s : %d enceintes, %d tours de rappel (classées %d fois, dernière %.2f ms)',
 'main system (stage plane)': 'système principal (plan de scène)', 'detection off': 'détection désactivée',
 'no left / right mains: nothing classified': 'pas de façade gauche / droite : rien n’est classé',
 'not in the Delay group (mode: Delay group only)': 'pas dans le groupe Delay (mode : groupe Delay seulement)',
 'no full-range array (subs only)': 'pas d’enceinte large bande (caissons seuls)', 'stage monitors are never delay towers': 'les retours ne sont jamais des rappels',
 'front fills are never delay towers': 'les front fills ne sont jamais des rappels', 'beside the mains, not in front of them': 'à côté de la façade, pas devant',
 'aimed back at the stage': 'tourné vers la scène', 'aim ambiguous (sideways): left as it is': 'orientation ambiguë (de côté) : laissé tel quel',
 'delay tower (Delay group, in front, aimed away)': 'tour de rappel (groupe Delay, devant, tournée vers le public)',
 'delay tower (in front of the mains, aimed away)': 'tour de rappel (devant la façade, tournée vers le public)',
 '%.0f blocks: %.1f ms': '%.0f blocs : %.1f ms',
 'Delay towers recognised: %d (%s), delays set automatically': 'Tours de rappel reconnues : %d (%s), délais réglés automatiquement',
 'Where to put the towers: about %s blocks in front of the mains': 'Où placer les tours : environ %s blocs devant la façade',
 ' (one every %.0f blocks)': ' (une tous les %.0f blocs)',
 'No delay tower needed: the mains cover the whole audience': 'Pas besoin de tour de rappel : la façade couvre tout le public',
 "Tower %d (%.0f blocks): not needed by the mains' coverage": 'Tour %d (%.0f blocs) : pas nécessaire pour la couverture',
 'Tower %d (%.0f blocks): well placed': 'Tour %d (%.0f blocs) : bien placée',
 'Tower %d (%.0f blocks): %.0f blocks too far, move it towards the stage': 'Tour %d (%.0f blocs) : %.0f blocs trop loin, rapprochez-la de la scène',
 'Tower %d (%.0f blocks): %.0f blocks too close, move it away from the stage': 'Tour %d (%.0f blocs) : %.0f blocs trop près, éloignez-la de la scène',
 'Coverage: the level drops %.0f dB too low around %.0f blocks': 'Couverture : le niveau descend %.0f dB trop bas vers %.0f blocs',
 'Anti-echo': 'Anti-écho', 'Tower advice in chat': 'Conseils des rappels dans le chat',
}
TIPS = {
 'crossover_auto': ("Crossover from the subwoofer model: 80 Hz for the double 18\" (S218-F, SB28), 90 Hz for the Veyra B2, 100 Hz for the Noryx B1. Moving the slider turns it off",
                    "Coupure selon le modèle de caisson : 80 Hz pour les doubles 18\" (S218-F, SB28), 90 Hz pour le Veyra B2, 100 Hz pour le Noryx B1. Bouger le curseur la désactive"),
 'curve_gentle': ("The level falls 25 % more slowly with distance than the realistic curve", "Le niveau baisse 25 % moins vite avec la distance que la courbe réaliste"),
 'curve_realistic': ("Each kind of speaker follows its own law: line arrays about 3 dB per doubling of distance then 6 dB, point sources and monitors 6 dB, subs 4.5 dB; the level stops rising very close",
                     "Chaque type d’enceinte suit sa loi : line arrays environ 3 dB par doublement de distance puis 6 dB, enceintes ponctuelles et retours 6 dB, caissons 4,5 dB ; le niveau cesse de monter tout près"),
 'curve_strong': ("The level falls 30 % faster with distance than the realistic curve", "Le niveau baisse 30 % plus vite avec la distance que la courbe réaliste"),
 'curve_legacy': ("The single curve of earlier versions (reference distance and rolloff below)", "La courbe unique des versions précédentes (distance de référence et pente ci-dessous)"),
 'spatial': ("How much the sound moves between your ears with the place of the speakers (0 % = always centred)", "À quel point le son se place entre vos oreilles selon la position des enceintes (0 % = toujours au centre)"),
 'towers_auto': ("Delay towers are recognised by themselves: same console, in front of the mains (20 blocks or more), aimed away from the stage, no monitor or fill",
                 "Les tours de rappel sont reconnues toutes seules : même console, devant la façade (20 blocs ou plus), tournées vers le public, sans retour ni front fill"),
 'towers_delay_group': ("Only installations whose arrays are in the Delay group can be delay towers", "Seules les installations dont les enceintes sont dans le groupe Delay peuvent être des rappels"),
 'towers_off': ("No detection: every array plays as its console group, with the console's group delays (as before)", "Pas de détection : chaque enceinte joue selon son groupe de console, avec les délais de groupe de la console (comme avant)"),
 'tower_trim': ("Added to the automatic delay of each tower so the mains arrive a little first and the sound stays on the stage (precedence effect)",
                "Ajouté au délai automatique de chaque rappel pour que la façade arrive un peu avant et que le son reste sur la scène (effet de précédence)"),
 'tower_auto_delay': ("On: each tower is delayed by its distance to the main system / 343 m/s. Off: the delay of the console's Delay group", "Activé : chaque rappel est retardé de sa distance à la façade / 343 m/s. Désactivé : le délai du groupe Delay de la console"),
 'propagation': ("The sound of a far installation arrives later (343 m/s): the mains and a tower line up where both are heard, without delaying the music as a whole",
                 "Le son d’une installation éloignée arrive plus tard (343 m/s) : façade et rappel s’alignent là où on entend les deux, sans retarder la musique dans son ensemble"),
 'anti_echo': ("A delay tower heard more than 35 ms after the first sound (behind it, or a far tower) would be an echo: it is turned down, up to 18 dB. In front of a tower nothing changes",
               "Une tour entendue plus de 35 ms après le premier son (derrière elle, ou une tour lointaine) ferait un écho : elle est baissée, jusqu'à 18 dB. Devant une tour rien ne change"),
 'tower_hints': ("When the speakers of a console near you change, the chat says which delay towers were recognised, their delays and where towers should stand",
                 "Quand les enceintes d'une console proche changent, le chat indique les tours de rappel reconnues, leurs délais et où placer les tours"),
 'max_range': ("How far the speakers carry (100 % = normal); every kind of speaker keeps its own range", "Jusqu’où portent les enceintes (100 % = normal) ; chaque type garde sa propre portée"),
}
for name, idx in (('en_us.json', 0), ('fr_fr.json', 1)):
    p = os.path.join(L, name)
    d = json.load(open(p, encoding='utf8'))
    for en, fr in UI.items():
        d[ui(en)] = en if idx == 0 else fr
    for k, v in TIPS.items():
        d['gui.concertstage.tip.' + k] = v[idx]
    open(p, 'w', encoding='utf8').write(json.dumps(d, ensure_ascii=False, indent=2) + '\n')
print(len(UI) + len(TIPS), 'texts')
