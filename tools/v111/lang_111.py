#!/usr/bin/env python3
"""1.1.1 texts (EN / FR). Same key rule as client/ui/Tr.key. Idempotent: python3 -I tools/v111/lang_111.py"""
import json, os, re
L = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'common', 'src', 'main', 'resources', 'assets', 'concertstage', 'lang')

def ui(english):
    t = english.strip(); label = t.endswith(':')
    s = re.sub(r'^_+|_+$', '', re.sub(r'[^a-z0-9]+', '_', t.lower().replace('+', ' plus ')))[:60]
    return 'gui.concertstage.ui.' + s + ('_label' if label else '')

UI = {'Mains': 'Têtes', 'Subs': 'Caissons', 'Monitors': 'Retours', 'Output level: no sound': 'Niveau de sortie : aucun son',
      'Output %.1f LUFS (3 s) · peak %.1f dBFS · limiter -%.1f dB · %d groups': 'Sortie %.1f LUFS (3 s) · crête %.1f dBFS · limiteur -%.1f dB · %d groupes'}
FIXED = {
 'gui.concertstage.tip.output_gain': ("Overall loudness of the console output before the limiter. x1 = calibrated (front of house about as loud as the track itself); above, the limiter works more",
                                      "Puissance générale de la sortie de la console avant le limiteur. x1 = calibré (façade à peu près aussi forte que le titre lui-même) ; au-dessus, le limiteur travaille davantage"),
 'gui.concertstage.tip.level_mains': ("Level of the main speakers and line arrays on your side (100 % = as the console sends them)",
                                      "Niveau des enceintes principales et line arrays de votre côté (100 % = comme la console les envoie)"),
 'gui.concertstage.tip.level_monitors': ("Level of the stage monitors on your side (100 % = as the console sends them)",
                                         "Niveau des retours de scène de votre côté (100 % = comme la console les envoie)"),
 'gui.concertstage.render_auto_on': ('AUTO: on', 'AUTO : activé'),
 'gui.concertstage.render_auto_off': ('AUTO: off', 'AUTO : désactivé'),
 'gui.concertstage.tip.render_auto': ('The quality follows your frame rate: one step down after 3 s under 40 FPS, one step up after 15 s over 75 FPS, at most one change every 20 s (never changes a device, only how the blocks are drawn)',
                                     'La qualité suit vos images par seconde : un cran de moins après 3 s sous 40 FPS, un cran de plus après 15 s au-dessus de 75 FPS, au plus un changement toutes les 20 s (ne change jamais un appareil, seulement le dessin des blocs)'),
 'gui.concertstage.tip.sub_level': ("Level of the subwoofers (100 % = normal)", "Niveau des caissons de basses (100 % = normal)"),
}

for name, idx in (('en_us.json', 0), ('fr_fr.json', 1)):
    p = os.path.join(L, name)
    d = json.load(open(p, encoding='utf8'))
    for en, fr in UI.items():
        d[ui(en)] = en if idx == 0 else fr
    for k, v in FIXED.items():
        d[k] = v[idx]
    open(p, 'w', encoding='utf8').write(json.dumps(d, ensure_ascii=False, indent=2) + '\n')
print(len(UI) + len(FIXED), 'texts')
