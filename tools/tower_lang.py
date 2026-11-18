"""Names and texts of the motorised towers and of the lift remote (shared by export_towers.py and generate_assets.py)."""

HEIGHTS = (10, 14, 18, 22)
CORNERS = ('corner3', 'corner3_h2', 'corner6', 'corner6_h2')
TOWER_BLOCKS = [f'motor_tower_{h}_{c}{s}' for h in HEIGHTS for c in CORNERS for s in ('', '_noir')]   # aluminium and black


def texts():
    """Returns (fr, en) dictionaries of translation keys."""
    corner_fr = {'corner3': 'coin 3 voies', 'corner3_h2': 'coin 3 voies, 2 de haut',
                 'corner6': 'coin 6 voies', 'corner6_h2': 'coin 6 voies, 2 de haut'}
    corner_en = {'corner3': '3-way joint', 'corner3_h2': '3-way joint, 2 high',
                 'corner6': '6-way joint', 'corner6_h2': '6-way joint, 2 high'}
    fr = {'item.concertstage.lift_remote': 'Télécommande de levage',
          'message.concertstage.lift_linked': 'Tour liée (%1$s au total)',
          'message.concertstage.lift_unlinked': 'Tour retirée (%1$s restantes)',
          'message.concertstage.lift_cleared': 'Toutes les tours ont été déliées',
          'message.concertstage.lift_none': 'Aucune tour liée : clic droit sur une tour motorisée pour la lier',
          'message.concertstage.lift_unloaded': 'Une tour liée est trop loin (zone non chargée)',
          'message.concertstage.lift_missing': 'Une tour liée n\'existe plus : elle a été retirée de la télécommande',
          'message.concertstage.lift_moving': 'Les tours sont déjà en mouvement (utilise Stop)',
          'message.concertstage.lift_not_aligned': 'Les tours ne sont pas à la même hauteur',
          'message.concertstage.lift_start': 'Levage vers %1$s blocs',
          'message.concertstage.lift_already': 'Les tours sont déjà à cette hauteur',
          'message.concertstage.lift_grounded': 'Structure reliée au sol en %1$s : impossible de la lever',
          'message.concertstage.lift_too_big': 'Structure trop grande (plus de %1$s blocs)',
          'message.concertstage.lift_blocked': 'Obstacle : la structure s\'arrête',
          'message.concertstage.lift_stopping': 'Arrêt au prochain bloc',
          'message.concertstage.lift_idle': 'Rien à arrêter : les tours sont à l\'arrêt',
          'gui.concertstage.remote_title': 'Télécommande de levage',
          'gui.concertstage.remote_no_towers': 'Aucune tour liée. Clic droit sur une tour motorisée avec la télécommande pour la lier, clic droit accroupi pour la retirer.',
          'gui.concertstage.remote_tower': 'Tour %1$s : %2$s / %3$s blocs, %4$s',
          'gui.concertstage.remote_more': '... et %1$s autres',
          'gui.concertstage.remote_target': 'Hauteur : %1$s',
          'gui.concertstage.remote_go': 'Aller à cette hauteur',
          'gui.concertstage.remote_up': 'Monter d\'un bloc',
          'gui.concertstage.remote_down': 'Descendre d\'un bloc',
          'gui.concertstage.remote_stop': 'Stop',
          'gui.concertstage.remote_clear': 'Délier toutes les tours',
          'gui.concertstage.state_idle': 'à l\'arrêt',
          'gui.concertstage.state_moving': 'en mouvement',
          'gui.concertstage.state_blocked': 'bloquée',
          'gui.concertstage.state_unloaded': 'zone non chargée'}
    en = {'item.concertstage.lift_remote': 'Lift Remote',
          'message.concertstage.lift_linked': 'Tower linked (%1$s in total)',
          'message.concertstage.lift_unlinked': 'Tower removed (%1$s left)',
          'message.concertstage.lift_cleared': 'All towers unlinked',
          'message.concertstage.lift_none': 'No tower linked: right click a motorised tower to link it',
          'message.concertstage.lift_unloaded': 'A linked tower is too far away (area not loaded)',
          'message.concertstage.lift_missing': 'A linked tower no longer exists: removed from the remote',
          'message.concertstage.lift_moving': 'The towers are already moving (use Stop)',
          'message.concertstage.lift_not_aligned': 'The towers are not at the same height',
          'message.concertstage.lift_start': 'Lifting to %1$s blocks',
          'message.concertstage.lift_already': 'The towers are already at that height',
          'message.concertstage.lift_grounded': 'Structure connected to the ground at %1$s: it cannot be lifted',
          'message.concertstage.lift_too_big': 'Structure too big (more than %1$s blocks)',
          'message.concertstage.lift_blocked': 'Obstacle: the structure stops',
          'message.concertstage.lift_stopping': 'Stopping at the next block',
          'message.concertstage.lift_idle': 'Nothing to stop: the towers are idle',
          'gui.concertstage.remote_title': 'Lift Remote',
          'gui.concertstage.remote_no_towers': 'No tower linked. Right click a motorised tower with the remote to link it, sneak + right click to remove it.',
          'gui.concertstage.remote_tower': 'Tower %1$s: %2$s / %3$s blocks, %4$s',
          'gui.concertstage.remote_more': '... and %1$s more',
          'gui.concertstage.remote_target': 'Height: %1$s',
          'gui.concertstage.remote_go': 'Go to this height',
          'gui.concertstage.remote_up': 'Up one block',
          'gui.concertstage.remote_down': 'Down one block',
          'gui.concertstage.remote_stop': 'Stop',
          'gui.concertstage.remote_clear': 'Unlink all towers',
          'gui.concertstage.state_idle': 'idle',
          'gui.concertstage.state_moving': 'moving',
          'gui.concertstage.state_blocked': 'blocked',
          'gui.concertstage.state_unloaded': 'area not loaded'}
    for n in HEIGHTS:
        for c in CORNERS:
            fr[f'block.concertstage.motor_tower_{n}_{c}'] = f'Tour truss motorisée {n} blocs ({corner_fr[c]})'
            en[f'block.concertstage.motor_tower_{n}_{c}'] = f'Motorised truss tower {n} blocks ({corner_en[c]})'
            fr[f'block.concertstage.motor_tower_{n}_{c}_noir'] = f'Tour truss motorisée {n} blocs ({corner_fr[c]}) (noire)'
            en[f'block.concertstage.motor_tower_{n}_{c}_noir'] = f'Motorised truss tower {n} blocks ({corner_en[c]}) (black)'
    return fr, en


def add_lang(en_dict, fr_dict):
    fr, en = texts()
    en_dict.update(en)
    fr_dict.update(fr)
