#!/usr/bin/env python3
"""Industry integration (her ChatGPT list section 13 #95): the Construction Site Office - a portakabin site office on
sleeper blocks with the project control panel, the bill-of-materials whiteboard, the materials store (container doors)
and a plan chest, a fenced materials compound beside it with real-looking stacks of rails, sleepers, steel and cable,
a site lamp mast and the warning signs. Models face north; controls by u = 16 - x. Live controls: industry/
TileConstructionSite.java (ids must match). Also writes the blockstate, item model, recipe and lang line."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_depot import lamp_row, push_row, estop_plate

X = lambda x: 16 - x


def construction_site():
    m = Model('construction_site')
    # ---- the cabin on its sleeper blocks
    m.g('foundations')
    for x in (0.6, 7.2, 13.4):
        for z in (4.6, 13.2):
            m.box((x, 0, z), (x + 2.0, 1.0, z + 2.0), 'wood:#5a4630', 'sleeper block')
    m.g('cabin')
    m.box((0.2, 1.0, 3.6), (15.8, 15.0, 16.0), 'paint:#2f6f9a', 'cabin shell')
    m.box((0.0, 15.0, 3.4), (16.0, 15.8, 16.2), 'paint:#24323e', 'roof')
    m.box((0.4, 15.8, 4.0), (15.6, 16.0, 15.8), 'paint:#1c2830', 'roof membrane')
    for z in (3.4, 16.0):
        m.box((0.0, 1.0, z), (16.0, 1.6, z + 0.2), 'paint:#24323e', 'chassis rail')
    for x in (0.0, 15.6):
        m.box((x, 1.0, 3.4), (x + 0.4, 15.0, 3.8), 'paint:#24323e', 'corner post')
        m.box((x, 1.0, 15.8), (x + 0.4, 15.0, 16.2), 'paint:#24323e', 'corner post')
    for y in (4.0, 8.0, 12.0):
        m.box((0.15, y, 4.0), (0.2, y + 0.3, 15.6), 'paint:#285e84', 'side rib', faces=('east', 'west'))
        m.box((15.8, y, 4.0), (15.85, y + 0.3, 15.6), 'paint:#285e84', 'side rib', faces=('east', 'west'))
    m.box((15.85, 9.0, 6.0), (15.95, 13.0, 13.0), 'glass', 'side window')
    m.box((15.85, 8.8, 5.8), (16.0, 9.0, 13.2), 'paint:#d8d8d4', 'window sill')
    for z in (8.2, 10.6):
        m.box((15.85, 9.0, z), (16.0, 13.0, z + 0.2), 'paint:#d8d8d4', 'window bar')
    m.box((15.86, 2.0, 6.0), (15.96, 6.0, 13.0), 'plate:SITE OFFICE', 'side name plate')
    # the cabin door on the east side, with its steps and handrail
    m.g('door + steps')
    m.box((-0.05, 1.6, 9.0), (0.2, 13.0, 13.4), 'paint:#d8d8d4', 'cabin door')
    m.box((-0.3, 7.0, 12.6), (-0.05, 7.6, 13.0), 'metal:steel', 'door handle')
    m.box((-0.15, 10.4, 10.0), (-0.05, 12.0, 12.4), 'glass', 'door vision panel')
    m.box((-0.12, 3.0, 9.6), (-0.05, 5.4, 12.8), 'label:SITE OFFICE|HARD HATS', 'door sign', faces=('west',))
    for i, y in enumerate((0.0, 0.5)):
        m.box((-3.0 + i * 1.2, y, 8.6), (0.0, y + 0.5, 13.8), 'mesh', 'step')
    m.box((-3.2, 0.0, 8.4), (-2.9, 4.6, 8.7), 'paint:#f0c818', 'handrail post')
    m.box((-3.2, 4.3, 8.4), (0.0, 4.6, 8.7), 'paint:#f0c818', 'handrail')
    # ---- the front: materials store doors | whiteboard + plan chest | control panel
    m.g('materials store')
    m.box((10.8, 1.6, 3.2), (15.6, 14.2, 3.6), 'paint:#8a2a1e', 'store doors')
    m.box((13.15, 1.6, 3.1), (13.25, 14.2, 3.2), 'paint:#5a1a12', 'door split')
    for x in (11.6, 14.4):
        m.box((x - 0.1, 1.8, 2.9), (x + 0.1, 14.0, 3.2), 'metal:steel', 'locking bar')
        for y in (3.0, 12.4):
            m.box((x - 0.3, y, 2.8), (x + 0.3, y + 0.6, 3.2), 'metal:steel', 'bar keeper')
    m.box((12.4, 7.0, 2.8), (13.0, 8.4, 3.1), 'paint:#202020', 'lock lever')
    m.box((11.0, 12.6, 3.15), (15.4, 14.0, 3.2), 'label:MATERIALS|GOODS IN', 'store sign')
    for x in (11.2, 15.2):
        for y in (2.4, 7.6, 13.0):
            m.box((x - 0.15, y, 2.95), (x + 0.15, y + 0.8, 3.2), 'metal:steel', 'hinge')
    st = m.surface('north', face=2.78, title='STORE')
    m.ctl(st, 'zone', 'yard', X(13.2), 7.9, w=4.6, h=12.0, col='000000', label='')
    m.g('whiteboard')
    m.box((6.6, 4.0, 3.3), (10.6, 14.4, 3.6), 'paint:#3a3f44', 'board frame')
    m.box((6.8, 4.2, 3.25), (10.4, 14.2, 3.3), 'paint:#f4f4f0', 'whiteboard')
    m.box((6.8, 13.4, 3.2), (10.4, 14.2, 3.25), 'label:BILL OF MATERIALS', 'board title')
    m.box((6.8, 3.6, 3.0), (10.4, 4.0, 3.6), 'paint:#3a3f44', 'pen tray')
    for x, c in ((7.2, '#2040c0'), (7.9, '#c02020'), (8.6, '#202020')):
        m.box((x, 4.0, 3.1), (x + 0.5, 4.2, 3.3), 'paint:' + c, 'marker')
    wb = m.surface('north', face=3.18, title='BOARD')
    for i in range(8):
        m.ctl(wb, 'digits', 'r%d' % i, X(8.6), 12.7 - i * 1.05, w=3.4, h=0.62, col='1a3aa0', label='', fmt='%s')
        m.ctl(wb, 'lamp', 'rl%d' % i, X(10.1), 12.7 - i * 1.05, w=0.3, h=0.3, col='30e060', label='')
    m.g('plan chest')
    m.box((6.6, 1.0, 2.6), (10.6, 3.6, 3.6), 'paint:#6a6e70', 'plan chest')
    for y in (1.3, 2.4):
        m.box((6.8, y, 2.5), (10.4, y + 0.9, 2.6), 'paint:#7a7e80', 'plan drawer')
        m.box((8.2, y + 0.3, 2.3), (9.0, y + 0.5, 2.5), 'metal:steel', 'drawer pull')
    pc = m.surface('north', face=2.28, title='PLANS')
    m.ctl(pc, 'zone', 'office', X(8.6), 2.3, w=4.0, h=2.4, col='000000', label='')
    # ---- the project control panel
    m.g('control panel')
    m.box((0.4, 1.8, 3.0), (6.4, 14.0, 3.6), 'paint:#d8dcd8', 'panel enclosure')
    m.box((0.5, 2.0, 2.9), (6.3, 13.8, 3.0), 'paint:#2b2f33', 'panel face')
    m.box((0.2, 14.0, 2.6), (6.6, 14.6, 3.6), 'paint:#d8dcd8', 'panel hood')
    m.box((0.6, 13.85, 2.85), (6.2, 14.0, 2.9), 'plate:PROJECT CONTROL', 'panel title')
    m.gland(3.4, 1.8, 3.3, 'down', r=0.35, cable='cable:202020', length=0.8)
    f = m.surface('north', face=2.88, title='PROJECT')
    m.box((0.8, 10.6, 2.86), (6.0, 13.6, 2.9), 'paint:#101410', 'display window')
    m.ctl(f, 'digits', 'proj', X(3.4), 12.9, w=5.0, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'stage', X(3.4), 12.0, w=5.0, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'pct', X(3.4), 11.2, w=2.6, h=0.6, col='8ad4ff', label='', fmt='%s')
    for i, (cid, lab) in enumerate((('matg', 'MATERIALS'), ('buildg', 'WORKS'))):
        m.ctl(f, 'gauge', cid, X(2.0 + i * 2.8), 9.2, w=2.0, h=2.0, col='f2f0e6', label=lab, max=100)
        m.ctl(f, 'label', '', X(2.0 + i * 2.8), 7.95, w=0, h=0, col='e8e8e8', label=lab)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('auth', '30e060', 'AUTH'), ('mat', '30e060', 'MAT'), ('rail', '30e060', 'RAIL'),
                    ('bld', 'ffb020', 'WORK', True), ('done', '8ad4ff', 'DONE'), ('stop', 'ff3030', 'STOP', True)], X(5.9), 7.1, step=0.82, size=0.45, label_dv=-0.45)
    push_row(m, f, [('prev', '202020', 'PREV'), ('next', '202020', 'NEXT'), ('order', '2050d0', 'ORDER'), ('build', '1fa34a', 'BUILD'), ('test', 'b0b0b0', 'TEST')],
             X(5.8), 5.3, step=1.1, size=0.75, label_dv=-0.65)
    m.ctl(f, 'key', 'k_auth', X(5.2), 3.0, w=0.9, h=0.9, col='c8a040', label='AUTHORISE', pos=['OFF', 'ON'])
    m.ctl(f, 'label', '', X(5.2), 2.2, w=0, h=0, col='e8e8e8', label='AUTHORISE')
    estop_plate(m, f, X(1.8), 3.0, cid='m_estop')
    m.ctl(f, 'label', '', X(1.8), 2.0, w=0, h=0, col='e8e8e8', label='STOP WORK')
    m.terminal_12v(15.8, 3.0, 5.0, 'east')
    # ---- roof gear: site lamp mast, aerial, the extract vent
    m.g('roof gear')
    m.box((1.0, 16.0, 13.0), (1.6, 26.0, 13.6), 'galv', 'lamp mast')
    m.box((0.2, 26.0, 12.4), (2.4, 27.2, 14.2), 'paint:#2a2a2a', 'floodlight')
    m.box((0.4, 26.2, 12.3), (2.2, 27.0, 12.4), 'lamp:fff0c0', 'floodlight lens')
    m.box((13.8, 16.0, 13.6), (14.0, 22.0, 13.8), 'metal:steel', 'aerial')
    m.box((13.2, 21.6, 13.6), (14.6, 21.8, 13.8), 'metal:steel', 'aerial element')
    m.cyl((8.0, 16.0, 10.0), 0.8, 1.6, 'y', 'galv', 'extract vent')
    m.box((7.0, 17.6, 9.0), (9.0, 18.0, 11.0), 'galv', 'vent cap')
    m.box((4.0, 15.0, 3.0), (12.0, 16.4, 3.4), 'label:CONSTRUCTION SITE', 'front fascia')
    m.box((12.2, 15.0, 3.0), (15.6, 16.4, 3.4), 'danger:HARD HATS', 'fascia danger')
    # ---- the materials compound west of the cabin (viewer's right): Heras fencing round real-looking stock
    m.g('compound fence')
    for z in (0.0, 16.0):
        for x0 in (-15.6, -8.0):
            m.box((x0, 0.0, z - 0.1), (x0 + 7.4, 10.0, z + 0.1), 'mesh', 'fence panel')
            m.box((x0, 9.6, z - 0.15), (x0 + 7.4, 10.0, z + 0.15), 'galv', 'fence top tube')
            m.box((x0 + 0.2, 0.0, z - 0.8), (x0 + 1.4, 0.6, z + 0.8), 'concrete', 'fence foot')
    for z0 in (0.2, 8.0):
        m.box((-15.8, 0.0, z0), (-15.6, 10.0, z0 + 7.6), 'mesh', 'fence panel')
        m.box((-15.85, 9.6, z0), (-15.55, 10.0, z0 + 7.6), 'galv', 'fence top tube')
    m.box((-14.0, 5.0, -0.25), (-9.0, 8.0, -0.15), 'danger:NO ENTRY', 'compound sign')
    m.g('rail stack')
    for i in range(4):
        y = 0.4 + i * 0.9
        m.box((-14.8, y, 1.0), (-14.0, y + 0.6, 15.0), 'paint:#2a2a2a', 'bearer') if i == 0 else None
        for j in range(4):
            x = -13.6 + j * 1.1
            m.box((x, y + 0.6, 1.4), (x + 0.6, y + 1.3, 14.6), 'metal:steel', 'rail')
            m.box((x - 0.15, y + 0.6, 1.4), (x + 0.75, y + 0.8, 14.6), 'paint:#6a3a22', 'rail foot')
    m.g('sleeper stack')
    for i in range(5):
        y = i * 1.1
        for j in range(3):
            z = 2.0 + j * 1.6 + (0.8 if i % 2 else 0)
            m.box((-8.0, y, z), (-1.4, y + 1.0, z + 1.2), 'paint:#33261a', 'creosoted sleeper')
    m.box((-8.2, 5.6, 2.0), (-1.2, 5.8, 8.8), 'paint:#d0d0d0', 'banding strap', faces=('up', 'north', 'south'))
    m.g('steel + cable')
    for i in range(3):
        m.box((-7.6, 0.0 + i * 0.7, 10.0 + i * 0.2), (-4.4, 0.6 + i * 0.7, 14.8 - i * 0.2), 'metal:steel', 'steel billet bundle')
    m.box((-7.8, 0.0, 9.6), (-4.2, 0.4, 15.2), 'wood:#7a5a3a', 'pallet')
    m.cyl((-2.6, 2.4, 10.4), 2.2, 1.0, 'z', 'wood:#8a6a40', 'cable drum flange')
    m.cyl((-2.6, 2.4, 13.4), 2.2, 1.0, 'z', 'wood:#8a6a40', 'cable drum flange')
    m.cyl((-2.6, 2.4, 11.4), 1.7, 2.0, 'z', 'cable:202020', 'cable on drum')
    m.box((-3.0, 0.0, 9.8), (-2.2, 0.6, 14.8), 'wood:#5a4630', 'drum chock')
    m.g('ballast heap')
    m.box((-14.6, 0.0, 8.0), (-9.6, 1.4, 15.2), 'concrete', 'ballast heap')
    m.box((-13.8, 1.4, 9.0), (-10.4, 2.4, 14.2), 'concrete', 'ballast heap')
    m.box((-13.0, 2.4, 10.0), (-11.2, 3.0, 13.2), 'concrete', 'ballast heap')
    from gen_bb_biz import construction_programme                # project management board (her next-48 #11)
    construction_programme(m)
    m.save(item_gui=0.35)


RECIPE = (['SPS', 'CBC', 'IHI'], {'S': 'minecraft:sign', 'P': {'item': 'irextras:part', 'data': 20}, 'C': {'item': 'irextras:part', 'data': 2},
                                  'B': 'minecraft:chest', 'I': 'minecraft:iron_ingot', 'H': 'minecraft:iron_bars'})

if __name__ == '__main__':
    construction_site()
    n = 'construction_site'
    bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                       'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    pat, key = RECIPE
    json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: (v if isinstance(v, dict) else {'item': v}) for k, v in key.items()},
               'result': {'item': 'irextras:' + n}}, open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    k = 'tile.irextras.construction_site.name=Construction Site Office'
    if k.split('=')[0] + '=' not in have:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# industry integration (her list section 13)\n' + k + '\n')
