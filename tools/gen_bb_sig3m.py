#!/usr/bin/env python3
"""Signalling batch 3, part M (her ChatGPT batch-3 list #19): the Route Macro Panel. Front = north; the panel stands in
the back half of the block (z 9..16) so it can go against a wall or back-to-back on a desk. Every lamp, button, key,
selector, dial and display is a live control (m.ctl) that safety/TileRouteMacros reads or works; the 12 V terminal box
is a wire terminal, not a button. Also writes the blockstate, item model, recipe and lang line."""
import json, os
from bbkit import Model, ASSETS

X = lambda u: 16 - u
GREY = 'ral7035'
DARK = 'paint:#2a2d30'
PANEL = 'paint:#c9cbc7'
FACE = 9.0                     # front face of the panel (z)


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label='', s=0.45, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def label(m, sf, u, v, text, col='202020'):
    m.ctl(sf, 'label', '', u, v, w=0, h=0, col=col, label=text)


COLS = (2.2, 5.9, 9.6, 13.3)                 # macro column centres (u)
ROWS = ((11.6, 10.3, 9.15), (7.8, 6.5, 5.35))  # name window, button, lamp row (v) for macros 1-4 / 5-8


def route_macro_panel():
    m = Model('route_macro_panel')
    m.g('cabinet')
    m.box((0.3, 0, 9.2), (15.7, 0.6, 16.0), 'paint:#141416', 'kick plinth')
    m.box((0.4, 0.6, 9.4), (15.6, 15.4, 15.8), GREY, 'panel cabinet')
    m.box((0.2, 15.4, 9.0), (15.8, 16.0, 16.0), GREY, 'top hood')
    m.box((0.2, 15.3, 8.8), (15.8, 15.4, 9.2), 'paint:#8a8d8a', 'hood drip edge')
    for x in (0.4, 15.2):
        m.box((x, 0.6, 9.2), (x + 0.4, 15.4, 9.4), 'paint:#b5b8b4', 'front frame stile')
    m.box((0.8, 0.6, 9.2), (15.2, 1.0, 9.4), 'paint:#b5b8b4', 'front frame rail')
    m.box((0.8, 15.0, 9.2), (15.2, 15.4, 9.4), 'paint:#b5b8b4', 'front frame rail')
    m.louvres(1.0, 4.0, 1.6, 6.0, 15.8, n=4)
    m.louvres(12.0, 15.0, 1.6, 6.0, 15.8, n=4)
    for y in (2.0, 13.0):
        m.box((15.6, y, 10.2), (15.9, y + 1.6, 10.8), 'metal:steel', 'side hinge')
    m.box((0.1, 7.0, 11.0), (0.4, 9.0, 11.6), 'metal:alu', 'side door handle')
    m.g('front panel')
    m.box((0.8, 1.0, FACE + 0.1), (15.2, 15.0, FACE + 0.2), PANEL, 'engraved front panel')
    for x in (1.1, 14.9):
        for y in (1.3, 14.7):
            m.bolt(x, y, FACE + 0.1, 'north', 0.3)
    m.nameplate(3.0, 14.2, 13.0, 14.85, FACE + 0.1, 'ROUTE MACRO PANEL')
    m.g('status windows')
    m.box((0.9, 12.25, FACE + 0.02), (12.8, 13.95, FACE + 0.1), 'paint:#101418', 'display bezel')
    m.box((1.0, 12.35, FACE - 0.02), (12.7, 13.85, FACE + 0.02), 'paint:#03140a', 'display glass')
    m.box((13.1, 12.1, FACE + 0.02), (15.3, 14.1, FACE + 0.1), 'paint:#1a1a1c', 'dial bezel')
    m.g('macro buttons')
    for r, (vn, vb, vl) in enumerate(ROWS):
        for c, u in enumerate(COLS):
            n = r * 4 + c + 1
            x = X(u)
            m.box((x - 1.75, vl - 0.55, FACE + 0.02), (x + 1.75, vn + 0.55, FACE + 0.1), 'paint:#b0b2ae', 'macro %d cell' % n)
            m.box((x - 1.75, vn + 0.55, FACE + 0.0), (x + 1.75, vn + 0.65, FACE + 0.1), 'paint:#5a5e62', 'cell top edge')
            m.box((x - 1.6, vn - 0.38, FACE - 0.02), (x + 1.6, vn + 0.38, FACE + 0.02), 'paint:#0a0c10', 'name window')
            m.box((x - 0.95, vb - 0.95, FACE - 0.08), (x + 0.95, vb + 0.95, FACE + 0.02), 'metal:alu', 'button bezel')
            m.box((x - 1.65, vb - 0.3, FACE - 0.02), (x - 1.15, vb + 0.3, FACE + 0.02), 'label:%d' % n, 'number plate', faces=('north',))
            for k in range(3):
                lx = x + (k - 1) * 0.8
                m.box((lx - 0.33, vl - 0.33, FACE - 0.06), (lx + 0.33, vl + 0.33, FACE + 0.02), 'paint:#2a2d30', 'lamp holder')
    m.g('control strip')
    m.box((0.8, 0.9, FACE + 0.02), (15.2, 4.5, FACE + 0.1), 'paint:#b9bbb7', 'control strip')
    m.box((0.8, 4.5, FACE + 0.0), (15.2, 4.6, FACE + 0.1), 'paint:#5a5e62', 'strip edge')
    m.box((X(2.0) - 0.8, 2.6, FACE - 0.06), (X(2.0) + 0.8, 4.2, FACE + 0.02), 'metal:brass', 'key escutcheon')
    for u in (4.4, 6.8):
        m.box((X(u) - 0.75, 2.65, FACE - 0.05), (X(u) + 0.75, 4.15, FACE + 0.02), 'paint:#1a1a1c', 'selector plate')
    m.box((X(9.8) - 2.1, 1.85, FACE - 0.02), (X(9.8) + 2.1, 2.55, FACE + 0.02), 'paint:#0a0c10', 'step window')
    m.box((X(13.2) - 1.3, 1.85, FACE - 0.02), (X(13.2) + 1.3, 2.55, FACE + 0.02), 'paint:#0a0c10', 'count window')
    for u in (9.0, 10.6, 12.4, 14.0):
        m.box((X(u) - 0.6, 3.0, FACE - 0.06), (X(u) + 0.6, 4.2, FACE + 0.02), 'metal:alu', 'push bezel')
    m.box((0.9, 0.95, FACE - 0.02), (6.0, 1.2, FACE + 0.02), 'label:PWR REC RUN FLT', 'lamp legend', faces=('north',))
    m.g('cable entry')
    for x in (3.0, 6.0, 10.0, 13.0):
        m.gland(x, 0.6, 13.0, 'down', r=0.3)
    m.box((2.0, 0.1, 12.6), (14.0, 0.5, 13.4), 'cable', 'cable tray')
    m.terminal_12v(15.6, 9.0, 13.0, 'east')
    m.g('rear mounting')
    for y in (2.0, 13.0):
        m.box((1.0, y, 15.8), (15.0, y + 0.8, 16.0), 'metal:steel', 'wall rail')
        for x in (2.0, 14.0):
            m.bolt(x, y + 0.4, 16.0, 'south', 0.35)

    f = m.surface('north', face=FACE - 0.04, title='ROUTE MACROS')
    m.ctl(f, 'digits', 'sel', 7.15, 13.45, w=11.2, h=0.62, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'stat', 7.15, 12.75, w=11.2, h=0.62, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'gauge', 'g_routes', 14.2, 13.1, w=1.8, h=1.8, col='f2f0e6', label='ROUTES', max=20)
    for r, (vn, vb, vl) in enumerate(ROWS):
        for c, u in enumerate(COLS):
            i = r * 4 + c + 1
            m.ctl(f, 'digits', 'n%d' % i, u, vn, w=3.1, h=0.62, col='ffb020', label='', fmt='%s')
            m.ctl(f, 'push', 'm%d' % i, u, vb, w=1.4, h=1.4, col='e8e8e0', label='')
            lamp(m, f, 'l%d' % i, u - 0.8, vl, 'f8f8f0', '')
            lamp(m, f, 'r%d' % i, u, vl, 'ffb020', '', blink=True)
            lamp(m, f, 'f%d' % i, u + 0.8, vl, 'ff3030', '', blink=True)
    m.ctl(f, 'key', 'k_rec', 2.0, 3.4, w=1.2, h=1.2, col='c0a040', label='RECORD', pos=['OFF', 'REC'])
    m.ctl(f, 'selector', 's_bank', 4.4, 3.4, w=1.1, h=1.1, col='202020', label='BANK', pos=['1-8', '9-16'])
    m.ctl(f, 'selector', 's_mode', 6.8, 3.4, w=1.1, h=1.1, col='202020', label='MODE', pos=['SEQ', 'ALL'])
    m.ctl(f, 'push', 'ent', 9.0, 3.6, w=0.9, h=0.9, col='30a030', label='ENTRANCE')
    m.ctl(f, 'push', 'ext', 10.6, 3.6, w=0.9, h=0.9, col='d0d020', label='EXIT')
    m.ctl(f, 'push', 'cancel', 12.4, 3.6, w=0.9, h=0.9, col='d02020', label='CANCEL')
    m.ctl(f, 'push', 'clear', 14.0, 3.6, w=0.9, h=0.9, col='202020', label='CLEAR')
    m.ctl(f, 'digits', 'step', 9.8, 2.2, w=4.0, h=0.6, col='5bcefa', label='', fmt='%s')
    m.ctl(f, 'digits', 'nroute', 13.2, 2.2, w=2.4, h=0.6, col='5bcefa', label='', fmt='%d RTE')
    lamp(m, f, 'pwr', 1.4, 1.6, '30e060', '', 0.5)
    lamp(m, f, 'rec', 2.4, 1.6, 'ff2020', '', 0.5, blink=True)
    lamp(m, f, 'busy', 3.4, 1.6, 'ffb020', '', 0.5, blink=True)
    lamp(m, f, 'flt', 4.4, 1.6, 'ff3030', '', 0.5, blink=True)
    m.ctl(f, 'push', 'test', 5.6, 1.6, w=0.55, h=0.55, col='f0f0f0', label='')
    m.save(item_gui=0.6)
    facing_states('route_macro_panel')
    return m


RECIPES = {
    'route_macro_panel': (['GLG', 'BRB', 'ICI'], {'G': 'minecraft:glass_pane', 'L': 'minecraft:redstone_lamp', 'B': 'minecraft:stone_button',
                                                  'R': 'minecraft:repeater', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:comparator'}),
}
LANG = {'tile.irextras.route_macro_panel.name': 'Route Macro Panel'}

if __name__ == '__main__':
    mm = route_macro_panel()
    print('route_macro_panel boxes:', len(mm.boxes))
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: {'item': v} for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as fh:
            fh.write(('' if have.endswith('\n') else '\n') + '\n# signalling batch 3 part M (#19 route macros)\n' + '\n'.join(add) + '\n')
    print('route macro panel assets written')
