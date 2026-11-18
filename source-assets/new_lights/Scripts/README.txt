Scripts de generation (Blender 5.3). Adapter ROOT dans run.py, regler cfg.json, puis dans la console Python de Blender :
exec(compile(open(r"<chemin>\\run.py",encoding="utf-8").read(),"run.py","exec"))

Refonte VORTEX X1 / LUMORA W7 / LUMORA W19 / PULSESTRIKE M : memes fichiers (csc_models.py, liste LEGACY4), lancement par
run_legacy4.py + cfg_legacy4.json (construction, rendus, controle, export des 17 pour le mod via csc_export.py, vue d'ensemble).
Les scripts doivent se trouver dans <ROOT>\_scripts.
