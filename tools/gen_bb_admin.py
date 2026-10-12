#!/usr/bin/env python3
"""Railway Administration Console (her ChatGPT list #111-120, admin/TileAdminConsole): a supervisory workstation -
steel desk, three monitors (main 8-line screen, an instrument monitor with MSPT / share meters, a status monitor), an
overhead annunciator light box, a desk control strip (page selector, item keys, ACTION / PRINT / EXPORT / RESET /
RESTORE, ADMIN KEY, guarded ALL STOP mushroom), a logbook drawer pedestal and a 19-inch server rack with a backup tape
drive (the tape cabinet is real storage, the rack LEDs read disk / network / CPU). Models face north; controls are
placed by u (viewer-left -> right): x = 16 - u. Also writes blockstate, item model, recipe and lang line."""
import json, os
from bbkit import Model, ASSETS

X = lambda u: 16 - u


def admin_console():
    m = Model('admin_console')
    # ---------------------------------------------------------------- desk
    m.g('desk')
    m.box((0.0, 8.4, 1.6), (16.0, 9.0, 14.8), 'paint:#4e5862', 'desk top (linoleum)')
    m.box((-0.1, 8.2, 1.4), (16.1, 8.4, 15.0), 'paint:#2c3238', 'desk top edge')
    m.box((0.4, 8.95, 1.7), (15.6, 9.0, 3.4), 'paint:#22272c', 'wrist rest pad')
    m.box((5.6, 0.6, 13.4), (10.4, 8.2, 14.0), 'paint:#3a424a', 'modesty panel')
    for x in (5.7, 9.7):
        m.box((x, 0.0, 12.6), (x + 0.6, 8.2, 14.4), 'paint:#2a2e33', 'desk leg')
    m.box((5.6, 0.0, 13.0), (10.4, 0.4, 14.2), 'paint:#2a2e33', 'foot rail')
    m.box((0.2, 7.6, 14.6), (15.8, 8.2, 15.6), 'mesh', 'cable tray')
    for x in (2.0, 6.0, 10.0, 14.0):
        m.box((x, 7.2, 14.8), (x + 0.3, 7.6, 15.4), 'metal:steel', 'tray hanger')
    for i, col in enumerate(('cable:1d1d21', 'cable:2050a0', 'cable:c81e1e', 'cable:e0c020')):
        m.box((0.4, 8.2, 14.75 + i * 0.2), (15.6, 8.35, 14.9 + i * 0.2), col, 'tray cable')

    # ---------------------------------------------------------------- logbook drawer pedestal (viewer's left)
    m.g('logbook pedestal')
    m.box((10.6, 0.3, 2.6), (15.6, 8.2, 12.6), 'paint:#46505a', 'pedestal')
    dr = m.surface('north', face=2.35, title='LOGBOOK')
    for k in range(3):
        y0 = 0.8 + k * 2.45
        m.box((10.9, y0, 2.4), (15.3, y0 + 2.15, 2.6), 'paint:#56626e', 'drawer front')
        m.box((12.4, y0 + 1.25, 2.15), (13.8, y0 + 1.55, 2.4), 'metal:steel', 'drawer pull')
        m.box((11.2, y0 + 1.7, 2.36), (12.2, y0 + 2.0, 2.4), 'label:LOG', 'card holder')
    m.box((14.4, 7.3, 2.3), (15.0, 7.9, 2.4), 'metal:brass', 'drawer lock')
    m.ctl(dr, 'zone', 'logbook', X(13.1), 4.4, w=4.4, h=7.2, col='000000', label='')
    m.box((10.6, 0.0, 2.8), (15.6, 0.3, 12.4), 'paint:#22272c', 'pedestal plinth')

    # ---------------------------------------------------------------- 19 inch server rack with tape drive (viewer's right)
    m.g('server rack')
    m.box((0.2, 0.0, 2.8), (5.4, 8.2, 13.0), 'paint:#1c1f22', 'rack cabinet')
    for x in (0.2, 5.0):
        m.box((x, 0.2, 2.6), (x + 0.4, 8.0, 2.8), 'metal:steel', 'rack rail')
        for y in [0.8 + i * 0.9 for i in range(8)]:
            m.box((x + 0.1, y, 2.55), (x + 0.3, y + 0.25, 2.6), 'paint:#0a0a0a', 'rack hole')
    rk = m.surface('north', face=2.3, title='RACK')
    # 1U servers
    for i, y in enumerate((6.6, 5.6)):
        m.box((0.6, y, 2.5), (5.0, y + 0.85, 2.8), 'paint:#2a2e33', 'server 1U')
        m.box((0.8, y + 0.15, 2.45), (2.8, y + 0.7, 2.5), 'louvre', 'server vents')
        m.box((4.2, y + 0.3, 2.45), (4.6, y + 0.55, 2.5), 'metal:steel', 'power switch')
        m.bolt(0.75, y + 0.42, 2.5, 'north', 0.3)
        m.bolt(4.85, y + 0.42, 2.5, 'north', 0.3)
    m.ctl(rk, 'lamp', 'r_cpu', X(3.4), 7.0, w=0.35, h=0.35, col='ffb020', label='CPU')
    m.ctl(rk, 'lamp', 'r_net', X(3.4), 6.0, w=0.35, h=0.35, col='30e060', label='NET')
    # tape drive
    m.box((0.6, 4.0, 2.5), (5.0, 5.3, 2.8), 'paint:#34393e', 'tape drive')
    m.box((1.2, 4.45, 2.42), (3.8, 4.85, 2.5), 'paint:#050505', 'tape slot')
    m.box((4.0, 4.3, 2.45), (4.6, 4.6, 2.5), 'paint:#d0d0d0', 'eject button')
    m.box((0.8, 5.0, 2.46), (2.6, 5.25, 2.5), 'label:LTO', 'drive badge')
    m.ctl(rk, 'lamp', 'r_disk', X(4.3), 4.95, w=0.35, h=0.35, col='40a0ff', label='TAPE', blink=True)
    # tape cabinet door (real storage)
    m.box((0.6, 0.4, 2.5), (5.0, 3.7, 2.7), 'paint:#2c3136', 'tape cabinet door')
    m.box((0.9, 0.7, 2.45), (4.7, 3.4, 2.5), 'glass', 'door window')
    for k in range(5):
        m.box((1.1 + k * 0.7, 0.9, 2.7), (1.6 + k * 0.7, 3.0, 4.6), 'paint:' + ('#1a3a6a', '#6a1a1a', '#1a5a2a', '#5a5a1a', '#3a1a5a')[k], 'backup tape')
    m.box((4.3, 1.8, 2.3), (4.6, 2.4, 2.5), 'metal:steel', 'door handle')
    m.box((0.9, 3.0, 2.44), (3.4, 3.35, 2.46), 'label:BACKUP TAPES', 'door label')
    m.ctl(rk, 'zone', 'tapes', X(2.8), 2.05, w=4.2, h=3.2, col='000000', label='')
    m.box((0.4, 0.0, 12.6), (5.2, 8.0, 13.0), 'mesh', 'rack rear door')
    m.gland(2.8, 0.0, 10.0, 'down', r=0.35)

    # ---------------------------------------------------------------- main monitor (8-line screen)
    m.g('main monitor')
    m.box((7.4, 9.0, 11.4), (8.6, 9.4, 13.4), 'paint:#2a2c2e', 'monitor foot')
    m.box((7.7, 9.4, 12.2), (8.3, 10.2, 12.8), 'paint:#2a2c2e', 'monitor neck')
    m.box((4.4, 10.0, 11.0), (11.6, 17.6, 12.0), 'paint:#1c1e20', 'main bezel')
    m.box((4.8, 10.4, 10.94), (11.2, 17.2, 11.0), 'paint:#03140a', 'main screen')
    m.box((7.2, 10.05, 10.95), (8.8, 10.3, 11.0), 'label:ADMIN', 'bezel badge')
    mon = m.surface('north', face=10.9, title='MAIN SCREEN')
    for i in range(8):
        m.ctl(mon, 'digits', 't%d' % (i + 1), X(8.0), 16.75 - i * 0.82, w=6.2, h=0.72, col='ffd040' if i == 0 else '7cf29a', label='', fmt='%s')

    # ---------------------------------------------------------------- instrument monitor (meters) - viewer's left
    m.g('instrument monitor')
    m.box((12.2, 9.0, 11.6), (13.8, 9.4, 13.4), 'paint:#2a2c2e', 'monitor foot')
    m.box((12.7, 9.4, 12.2), (13.3, 10.4, 12.8), 'paint:#2a2c2e', 'monitor neck')
    m.box((11.8, 10.2, 11.2), (16.0, 16.4, 12.2), 'paint:#1c1e20', 'meter bezel')
    m.box((12.0, 10.4, 11.1), (15.8, 16.2, 11.2), 'paint:#101418', 'meter panel')
    ins = m.surface('north', face=11.05, title='SERVER LOAD')
    m.ctl(ins, 'gauge', 'g_mspt', X(13.9), 14.6, w=2.6, h=2.6, col='f2f0e6', label='MS/TICK', max=100)
    m.ctl(ins, 'gauge', 'g_share', X(13.9), 11.6, w=2.2, h=2.2, col='f2f0e6', label='IRX %', max=100)
    m.box((12.2, 16.0, 11.05), (15.6, 16.15, 11.1), 'label:LOAD', 'meter header')

    # ---------------------------------------------------------------- status monitor - viewer's right
    m.g('status monitor')
    m.box((2.2, 9.0, 11.6), (3.8, 9.4, 13.4), 'paint:#2a2c2e', 'monitor foot')
    m.box((2.7, 9.4, 12.2), (3.3, 10.4, 12.8), 'paint:#2a2c2e', 'monitor neck')
    m.box((0.0, 10.2, 11.2), (4.2, 16.4, 12.2), 'paint:#1c1e20', 'status bezel')
    m.box((0.2, 10.4, 11.1), (4.0, 16.2, 11.2), 'paint:#03100c', 'status screen')
    st = m.surface('north', face=11.05, title='STATUS')
    m.ctl(st, 'digits', 'page', X(2.1), 15.4, w=3.4, h=0.9, col='8ad4ff', label='', fmt='%s')
    m.ctl(st, 'digits', 'tps', X(2.1), 14.0, w=3.4, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(st, 'digits', 'cells', X(2.1), 12.6, w=3.4, h=0.9, col='ffd040', label='', fmt='%d CELLS')
    for i in range(6):                                           # the little network map under the readouts
        m.box((0.6 + i * 0.55, 10.8 + (i % 3) * 0.3, 11.08), (0.9 + i * 0.55, 11.1 + (i % 3) * 0.3, 11.1), 'paint:#30e060', 'map node')
    m.box((0.6, 10.95, 11.09), (3.6, 11.0, 11.1), 'paint:#1a6a3a', 'map line')

    # ---------------------------------------------------------------- overhead annunciator light box
    m.g('annunciator light box')
    for x in (0.6, 15.0):
        m.box((x, 9.0, 13.6), (x + 0.4, 18.0, 14.0), 'metal:steel', 'light box post')
    m.box((0.2, 18.0, 12.4), (15.8, 20.2, 14.2), 'paint:#24282c', 'light box')
    m.box((0.0, 20.2, 12.2), (16.0, 20.5, 14.4), 'metal:steel', 'light box cap')
    m.box((4.4, 20.5, 13.0), (11.6, 21.4, 13.3), 'plate', 'title plate', north='plate:RAILWAY ADMINISTRATION')
    lb = m.surface('north', face=12.35, title='STATUS LAMPS')
    lamps = [('l_power', '30e060', 'POWER', False), ('l_armed', 'ff3020', 'ARMED', True), ('l_paused', 'ffb020', 'PAUSED', False),
             ('l_locks', '40a0ff', 'DESKS', False), ('l_backup', '30e060', 'BACKUP', False), ('l_recovery', 'ff3020', 'RECOVERY', True),
             ('l_overload', 'ff6020', 'OVERLOAD', True), ('l_sla', 'ffd040', 'CONTRACT', False), ('l_allstop', 'ff2020', 'ALL STOP', True)]
    for i, (cid, col, lab, blink) in enumerate(lamps):
        x = 14.9 - i * 1.7
        m.box((x - 0.75, 18.25, 12.3), (x + 0.75, 19.95, 12.4), 'paint:#101214', 'lamp window frame')
        m.ctl(lb, 'lamp', cid, X(x), 19.35, w=0.8, h=0.8, col=col, label=lab, **({'blink': True} if blink else {}))

    # ---------------------------------------------------------------- desk control strip
    m.g('control strip')
    m.box((0.4, 9.0, 3.6), (15.6, 9.25, 10.4), 'paint:#2b2f33', 'control strip plate')
    m.box((0.3, 9.0, 3.4), (15.7, 9.3, 3.6), 'metal:steel', 'strip front edge')
    cs = m.surface('up', face=16 - 9.25, title='DESK')
    # keyboard (decor) in the middle
    m.box((5.0, 9.25, 4.0), (11.0, 9.55, 6.8), 'paint:#cfcbbb', 'keyboard')
    for r in range(3):
        for c in range(10):
            m.box((5.2 + c * 0.57, 9.55, 4.2 + r * 0.85), (5.65 + c * 0.57, 9.7, 4.85 + r * 0.85), 'paint:#e8e4d4', 'key')
    m.box((11.4, 9.25, 4.4), (12.4, 9.6, 5.8), 'paint:#cfcbbb', 'mouse')
    # function buttons row (behind the keyboard)
    btns = [('prev', '1a1a1a', '<'), ('next', '1a1a1a', '>'), ('act', '2050d0', 'ACTION'), ('print', 'f0f0f0', 'PRINT'),
            ('export', '30a050', 'EXPORT'), ('reset', '808080', 'RESET'), ('restore', 'e08020', 'RESTORE')]
    for i, (cid, col, lab) in enumerate(btns):
        x = 11.2 - i * 0.95
        m.box((x - 0.4, 9.25, 7.4), (x + 0.4, 9.35, 8.2), 'paint:#1a1d20', 'button collar')
        m.ctl(cs, 'push', cid, X(x), 7.8, w=0.65, h=0.65, col=col, label=lab)
    m.ctl(cs, 'selector', 's_page', X(13.4), 8.0, w=1.1, h=1.1, col='202020', label='PAGE',
          pos=['OVERVIEW', 'TRAINS', 'SUBSYS', 'DESKS', 'BACKUP', 'STATS', 'PROFILE', 'COMMERCE'])
    m.box((12.6, 9.25, 7.2), (14.2, 9.32, 8.8), 'label:PAGE', 'selector plate')
    m.ctl(cs, 'key', 'k_arm', X(13.4), 5.2, w=1.0, h=1.0, col='c0a040', label='ADMIN KEY', pos=['SAFE', 'ARMED'])
    m.box((12.7, 9.25, 4.5), (14.1, 9.32, 5.9), 'plate', 'key escutcheon')
    # guarded ALL STOP mushroom (viewer's right)
    m.box((1.0, 9.25, 4.2), (4.0, 9.4, 7.2), 'haz', 'all stop hazard plate')
    for (x0, z0, x1, z1) in ((1.2, 4.4, 1.5, 7.0), (3.5, 4.4, 3.8, 7.0)):
        m.box((x0, 9.4, z0), (x1, 10.6, z1), 'paint:#e0c020', 'mushroom guard')
    m.ctl(cs, 'mushroom', 'allstop', X(2.5), 5.7, w=1.3, h=1.3, col='d02020', label='ALL STOP')
    m.box((1.4, 9.4, 7.3), (3.6, 9.45, 7.9), 'danger:ALL STOP', 'all stop label')
    m.ctl(cs, 'label', '', X(8.0), 9.6, w=0, h=0, col='e8e8e8', label='< >  ACTION  PRINT  EXPORT  RESET  RESTORE')

    # ---------------------------------------------------------------- desk clutter: phone, lamp, binders, mug
    m.g('desk phone')
    m.box((13.0, 9.0, 1.9), (15.4, 9.6, 3.3), 'paint:#1a1a1a', 'phone base')
    m.cyl((13.0, 9.9, 2.6), 0.3, 2.4, 'x', 'paint:#1a1a1a', 'phone handset')
    m.box((13.4, 9.6, 2.0), (14.8, 9.65, 2.6), 'paint:#3a3a3a', 'phone keys')
    m.g('binders')
    for i, col in enumerate(('#7a1a1a', '#1a3a7a', '#1a6a2a')):
        m.box((14.4 - i * 0.7, 9.0, 13.2), (14.9 - i * 0.7, 12.2, 14.6), 'paint:' + col, 'binder')
        m.box((14.45 - i * 0.7, 11.0, 13.15), (14.85 - i * 0.7, 11.6, 13.2), 'paint:#f0f0f0', 'binder label')
    m.g('coffee mug')
    m.cyl((4.6, 9.0, 2.4), 0.45, 0.9, 'y', 'paint:#f4f4f0', 'mug')
    m.box((4.95, 9.2, 2.3), (5.25, 9.7, 2.5), 'paint:#f4f4f0', 'mug handle')
    m.box((4.25, 9.85, 2.05), (4.95, 9.9, 2.75), 'paint:#3a2210', 'coffee')
    m.g('desk lamp')
    m.cyl((0.9, 9.0, 13.0), 0.5, 0.25, 'y', 'paint:#1a1a1a', 'lamp foot')
    m.box((0.8, 9.25, 12.9), (1.0, 12.6, 13.1), 'metal:steel', 'lamp arm')
    m.box((0.2, 12.2, 11.8), (1.6, 12.9, 13.2), 'paint:#2a6a9a', 'lamp shade')
    m.box((0.4, 12.1, 12.0), (1.4, 12.2, 13.0), 'paint:#fff6c8', 'lamp glow')

    # ---------------------------------------------------------------- chair
    m.g('chair')
    m.cyl((8.0, 0.0, -4.0), 0.4, 4.2, 'y', 'metal:steel', 'chair column')
    for (dx, dz) in ((2.0, 0), (-2.0, 0), (0, 2.0), (0, -2.0)):
        m.box((8.0 + min(0, dx) - 0.2, 0.0, -4.0 + min(0, dz) - 0.2), (8.0 + max(0, dx) + 0.2, 0.4, -4.0 + max(0, dz) + 0.2), 'paint:#1a1a1a', 'chair base leg')
        m.box((8.0 + dx - 0.3, 0.0, -4.0 + dz - 0.3), (8.0 + dx + 0.3, 0.3, -4.0 + dz + 0.3), 'paint:#0a0a0a', 'castor')
    m.box((5.4, 4.2, -6.6), (10.6, 5.2, -1.4), 'paint:#3a2a5a', 'chair seat')
    m.box((5.6, 5.2, -7.0), (10.4, 11.4, -6.2), 'paint:#3a2a5a', 'chair back')
    m.box((7.6, 4.6, -6.6), (8.4, 6.0, -6.0), 'metal:steel', 'back post')
    for x in (5.0, 10.6):
        m.box((x, 5.2, -5.6), (x + 0.4, 6.8, -5.2), 'metal:steel', 'arm post')
        m.box((x - 0.1, 6.8, -6.0), (x + 0.5, 7.1, -2.4), 'paint:#1a1a1a', 'arm rest')

    m.terminal_12v(16.0, 1.2, 7.0, 'east')
    m.save(item_gui=0.42)


NAMES = ['admin_console']
RECIPES = {'admin_console': (['GGG', 'RDR', 'ICI'], {'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone_block', 'D': 'irextras:maint_terminal',
                                                      'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest'})}
LANG = {'tile.irextras.admin_console.name': 'Railway Administration Console'}
DATA0 = set()

if __name__ == '__main__':
    admin_console()
    for n in NAMES:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        ing = lambda v: {'item': v, 'data': 0} if v in DATA0 else {'item': v}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# railway administration (her list #111-120)\n' + '\n'.join(add) + '\n')
