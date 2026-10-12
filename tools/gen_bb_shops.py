#!/usr/bin/env python3
"""Station shops (#5) and emergency services (#6): newsstand, coffee kiosk, ticket office counter, fire / ambulance /
police station desks (three-block-wide desks: real storages behind every door, drawer and hopper, display shelves, a
12 V terminal AND a mains isolator), the police cordon barrier, and the three vehicles (fire engine, ambulance, police
car - modelled at HALF size, drawn twice as big by ems/client/EmsClient; their lamp lenses sit where EmsClient's
LIGHTS overlay flashes). Coffee cup item textures + models too. Models face north; controls on north surfaces sit at
u = 16 - x (X()), on 'up' surfaces v = z. Live controls: shop/Tile*.java, ems/TileEmergencyStation.java.
Writes models, controls, blockstates, item models and recipes (lang lines are appended by hand)."""
import json, os
from PIL import Image
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing
from gen_bb_economy import back_wall, desk, strip, cupboard_shelf, monitor, hood, X

SPOTS = {}
RED2, ORANGE = 'ff5020', 'ff8a20'


def fascia(m, text, col, top=30.0):
    m.g('fascia')
    m.box((-14, top - 4.0, 14.6), (30, top, 15.4), 'paint:' + col, 'fascia board')
    m.box((-6, top - 3.4, 14.5), (22, top - 0.6, 14.6), 'plate', 'fascia lettering', north='label:' + text)
    for x in (-13.0, 29.0):
        m.box((x - 0.5, top - 3.6, 14.4), (x + 0.5, top - 0.4, 14.6), 'metal:brass', 'fascia lamp')


def keypad(m, sf, ids, x0, v0, step=1.0, size=0.8, cols=3, labels=None):
    """a 3-wide keypad of push buttons on an 'up' surface (ids row by row from the back)"""
    for i, cid in enumerate(ids):
        r, c = divmod(i, cols)
        u = X(x0 + c * step)
        v = v0 - r * step
        lab = labels[i] if labels else cid[-1]
        m.ctl(sf, 'push', cid, u, v, w=size, h=size, col='d8d8d0' if cid[-1].isdigit() else ('d03030' if 'clr' in cid else '30a040'), label=lab)


def till(m, x0, z0, top=9.0):
    m.g('till')
    m.box((x0, top, z0), (x0 + 3.6, top + 1.4, z0 + 2.4), 'paint:#202226', 'till base')
    m.box((x0 + 0.3, top + 1.4, z0 + 0.4), (x0 + 3.3, top + 3.0, z0 + 0.6), 'paint:#03140a', 'till screen')
    m.box((x0 + 0.3, top + 1.4, z0 + 0.6), (x0 + 3.3, top + 3.0, z0 + 1.0), 'paint:#2a2c2e', 'till screen back')
    for i in range(3):
        for j in range(4):
            m.box((x0 + 0.4 + j * 0.75, top + 1.4, z0 + 1.2 + i * 0.4), (x0 + 0.9 + j * 0.75, top + 1.55, z0 + 1.5 + i * 0.4), 'paint:#3a3d42', 'till key')
    m.box((x0 + 3.7, top, z0 + 0.4), (x0 + 4.9, top + 1.0, z0 + 2.0), 'paint:#2a2c2e', 'card reader')
    m.box((x0 + 3.85, top + 1.0, z0 + 0.6), (x0 + 4.75, top + 1.05, z0 + 1.4), 'paint:#03140a', 'card reader screen')


def counter_bell(m, x, z, top=9.0):
    m.cyl((x, top, z), 0.7, 0.2, 'y', 'paint:#202020', 'bell base')
    m.cyl((x, top + 0.2, z), 0.55, 0.6, 'y', 'metal:brass', 'counter bell')
    m.cyl((x, top + 0.8, z), 0.12, 0.25, 'y', 'metal:brass', 'bell plunger')


# ================================================================================================== newsstand
def newsstand():
    m = Model('newsstand')
    back_wall(m, panel='#3a2a5a', plaster='#f0ece0', top=30)
    fascia(m, 'NEWS - PAPERS - MAGAZINES', '#2a1f4a')
    # ---- the little offset press on the left
    m.g('press base')
    m.box((-13.4, 0, 9.0), (-2.6, 7.6, 15.2), 'paint:#3a3f46', 'press stand')
    m.box((-13.6, 7.6, 8.8), (-2.4, 8.2, 15.4), 'metal:steel', 'press bed')
    for lvl in range(2):
        y = 1.0 + lvl * 3.2
        m.box((-13.0, y, 9.4), (-3.0, y + 0.3, 15.0), 'metal:steel', 'ream shelf')
        for k in range(4):
            x0 = -12.6 + k * 2.4
            m.box((x0, y + 0.3, 10.2), (x0 + 2.0, y + 1.0 + (k % 2) * 0.4, 14.2), 'paint:#f4f2ea', 'paper ream')
            m.box((x0, y + 0.55, 10.15), (x0 + 2.0, y + 0.75, 10.2), 'paint:#c03030', 'ream wrapper band')
    m.g('press')
    for x in (-13.0, -3.8):
        m.box((x, 8.2, 10.0), (x + 0.8, 15.4, 14.6), 'paint:#2b4f6a', 'press side frame')
        m.bolt(x + 0.4, 9.0, 10.0, 'north', 0.4); m.bolt(x + 0.4, 14.6, 10.0, 'north', 0.4)
    for (y, z, r, mat, nm) in ((9.6, 11.0, 0.9, 'metal:steel', 'impression cylinder'), (11.6, 11.4, 1.0, 'paint:#202020', 'blanket cylinder'),
                               (13.6, 12.2, 0.9, 'metal:steel', 'plate cylinder'), (14.8, 13.8, 0.4, 'paint:#1a1a1a', 'ink roller'),
                               (14.9, 12.6, 0.35, 'paint:#1a1a1a', 'ink roller'), (10.4, 13.6, 0.5, 'metal:brass', 'feed roller')):
        m.cyl((-12.2, y, z), r, 8.4, 'x', mat, nm)
    m.box((-12.0, 14.6, 13.8), (-4.2, 15.8, 15.2), 'paint:#101010', 'ink fountain')
    m.box((-11.8, 15.8, 14.0), (-4.4, 16.0, 15.0), 'paint:#050505', 'ink')
    m.cyl((-12.2, 10.6, 15.0), 1.4, 8.4, 'x', 'paint:#f4f2ea', 'newsprint roll')
    m.box((-12.4, 8.2, 8.4), (-4.4, 8.6, 10.0), 'metal:steel', 'delivery tray')
    for i in range(5):
        m.box((-12.0, 8.6 + i * 0.12, 8.6), (-4.8, 8.7 + i * 0.12, 9.8), 'paint:#ece8dc' if i % 2 else 'paint:#f6f4ee', 'printed sheet')
    m.box((-3.6, 8.2, 12.2), (-2.6, 10.6, 14.8), 'paint:#2b4f6a', 'drive motor')
    m.cyl((-3.1, 10.6, 13.5), 0.5, 0.6, 'y', 'metal:steel', 'motor fan cowl')
    m.box((-13.0, 15.4, 10.0), (-3.0, 15.8, 14.6), 'paint:#2b4f6a', 'press top tie')
    # press control panel on the press's front (north)
    m.box((-12.8, 11.6, 9.6), (-4.4, 15.2, 10.0), 'paint:#24282c', 'press panel')
    pp = m.surface('north', face=9.58, title='PRESS')
    pushes(m, pp, [('print', '30a040', 'PRINT 1'), ('print5', '30a040', 'PRINT 5'), ('cancel', 'd03030', 'STOP')], X(-11.8), 14.4, step=1.6, size=0.75, lab_dv=0.6)
    m.ctl(pp, 'gauge', 'gpaper', X(-6.6), 14.0, w=1.8, h=1.8, col='f2f0e6', label='PAPER %', max=100)
    m.ctl(pp, 'gauge', 'gink', X(-4.9), 14.0, w=1.4, h=1.4, col='f2f0e6', label='INK %', max=100)
    lamps(m, pp, [('printl', AMBER, 'RUN', True), ('paperlow', RED, 'PAPER'), ('inklow', RED, 'INK'), ('newed', BLUE, 'NEW ED', True)], X(-11.8), 12.4, step=1.6, size=0.5)
    # ---- counter
    desk(m, {('l', 1): 'back'}, top=9.0, body='#3a2a5a', front='#5a4a7a', pull='metal:brass')
    sf = strip(m, 4.0, 8.4, top=9.0)
    monitor(m, 2.0, 14.0, 12.0, 18.2, 10.6, ['price', 'stockn', 'papern', 'soldn', 'ed', 'last', 'stn'],
            cols=['ffd040', '7cf29a', '7cf29a', '5bcefa', '8a94a0', 'ffb020', '8a94a0'], stand_y=9.0)
    pushes(m, sf, [('buy', '30a040', 'BUY'), ('hprev', 'e0e0e0', 'HEAD <'), ('hnext', 'e0e0e0', 'HEAD >'), ('proof', '2050d0', 'EDITION'), ('bell', 'c0a040', 'BELL')],
           X(1.6), 7.6, step=-2.6, size=0.9)
    m.ctl(sf, 'key', 'k_open', X(1.6), 5.2, w=0.9, h=0.9, col='c0a040', label='SHUTTER', pos=['CLOSED', 'OPEN'])
    m.ctl(sf, 'key', 'k_auto', X(3.6), 5.2, w=0.9, h=0.9, col='c0a040', label='AUTO PRINT', pos=['OFF', 'ON'])
    m.ctl(sf, 'selector', 's_price', X(5.8), 5.2, w=1.0, h=1.0, col='202020', label='PRICE', pos=['0.50', '1.00', '2.00'])
    lamps(m, sf, [('openl', GREEN, 'OPEN'), ('onsale', GREEN, 'ON SALE'), ('soldout', RED, 'SOLD OUT', True), ('autol', AMBER, 'AUTO'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')],
          X(7.6), 5.2, step=-1.35, size=0.5, lab_dv=0.6)
    till(m, 11.4, 9.6)
    # bundle bin at the front of the counter + racks of papers
    m.g('bundle bin')
    m.box((0.8, 0.0, 0.6), (15.2, 0.6, 3.4), 'paint:#2a2c2e', 'bin base')
    for x in (0.8, 15.0):
        m.box((x, 0.6, 0.6), (x + 0.2, 6.0, 3.4), 'galv', 'bin end')
    for y in (2.0, 4.0, 6.0):
        m.box((0.8, y - 0.15, 0.55), (15.2, y, 0.7), 'galv', 'bin wire')
    for k in range(6):
        x0 = 1.4 + k * 2.3
        h = 2.6 + (k % 3) * 0.8
        m.box((x0, 0.6, 1.2), (x0 + 2.0, 0.6 + h, 3.0), 'paint:#f2f0e8', 'newspaper bundle')
        m.box((x0, 0.6 + h * 0.5, 1.15), (x0 + 2.0, 0.6 + h * 0.5 + 0.2, 1.2), 'paint:#303030', 'bundle strap')
        m.box((x0 + 0.2, 0.6 + h - 0.8, 1.15), (x0 + 1.8, 0.6 + h - 0.2, 1.2), 'paint:#20202a', 'masthead')
    bb = m.surface('north', face=0.5, title='BUNDLE BIN')
    m.ctl(bb, 'zone', 'copies', X(8.0), 3.0, w=14.0, h=5.6, col='000000', label='')
    # headline board hung over the counter (the stand's front)
    m.g('headline board')
    for x in (-5.8, 21.4):
        m.box((x, 9.0, 2.4), (x + 0.4, 21.6, 2.8), 'metal:steel', 'board post')
    m.box((-6.4, 21.6, 2.2), (22.4, 28.2, 3.0), 'paint:#1a1430', 'board frame')
    m.box((-6.0, 22.0, 2.1), (22.0, 27.8, 2.2), 'paint:#070a08', 'LED headline panel')
    for x in (-6.2, 22.2):
        for y in (21.8, 28.0):
            m.bolt(x, y, 2.2, 'north', 0.3)
    hb = m.surface('north', face=2.08, title='HEADLINES')
    for i, cid in enumerate(('mast', 'h1', 'h2', 'h3')):
        m.ctl(hb, 'digits', cid, X(8.0), 27.0 - i * 1.4, w=27.0, h=1.15, col=('ffffff' if i == 0 else 'ffb020'), label='', fmt='%s')
    SPOTS['newsstand'] = cupboard_shelf(m, 18.4, 29.4, ['np'], 6, title='MAGAZINES', body='#3a2a5a', door='#5a4a7a')
    m.box((18.6, 10.4, 14.4), (29.2, 13.6, 15.0), 'plate', 'stock sign', north='label:NEWSPRINT + INK')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    m.save(item_gui=0.28)
    return m


# ================================================================================================ coffee kiosk
def coffee_kiosk():
    m = Model('coffee_kiosk')
    back_wall(m, panel='#4a2e1e', plaster='#f2e8d8', top=30)
    fascia(m, 'COFFEE - ESPRESSO BAR', '#3a2216')
    # ---- menu board on the back wall
    m.g('menu board')
    m.box((0.0, 15.6, 14.9), (16.0, 25.6, 15.4), 'wood', 'menu frame')
    m.box((0.4, 16.0, 14.8), (15.6, 25.2, 14.9), 'paint:#1e2a22', 'chalk board')
    mb = m.surface('north', face=14.78, title='MENU')
    m.ctl(mb, 'label', '', X(8.0), 24.4, w=0, h=0, col='f4f0e0', label='MENU')
    for i in range(5):
        m.ctl(mb, 'digits', 'm%d' % (i + 1), X(8.0), 23.0 - i * 1.3, w=14.0, h=1.0, col='f4f0e0', label='', fmt='%s')
    m.ctl(mb, 'digits', 'order', X(8.0), 16.8, w=14.0, h=0.9, col='ffd040', label='', fmt='%s')
    # ---- back counter + espresso machine (left)
    m.g('back counter')
    m.box((-13.4, 0, 9.0), (-2.6, 8.6, 15.2), 'paint:#3a3a3a', 'back counter')
    m.box((-13.6, 8.6, 8.8), (-2.4, 9.0, 15.4), 'metal:steel', 'back counter top')
    m.g('milk fridge')
    m.box((-13.0, 0.6, 8.9), (-7.4, 8.0, 9.0), 'glass', 'fridge door')
    m.box((-13.0, 0.6, 8.8), (-12.6, 8.0, 8.9), 'metal:steel', 'fridge door frame')
    m.box((-7.8, 3.0, 8.5), (-7.5, 5.6, 8.8), 'metal:steel', 'fridge handle')
    for y in (2.6, 5.0):
        m.box((-12.8, y, 9.2), (-7.6, y + 0.15, 14.8), 'metal:steel', 'fridge shelf')
        for x in (-12.2, -10.6, -9.0):
            m.cyl((x + 0.6, y + 0.15, 10.8), 0.55, 2.0, 'y', 'paint:#f8f8f4', 'milk jug')
            m.box((x + 0.2, y + 1.0, 10.2), (x + 1.0, y + 1.5, 10.25), 'paint:#3070d0', 'milk label')
    fr = m.surface('north', face=8.78, title='FRIDGE')
    m.ctl(fr, 'zone', 'fridge', X(-10.2), 4.3, w=5.4, h=7.2, col='000000', label='')
    m.box((-7.0, 0.6, 8.9), (-3.0, 8.0, 9.0), 'paint:#4a4a4a', 'knock box door')
    m.box((-5.4, 4.0, 8.7), (-4.6, 4.6, 8.9), 'metal:steel', 'knock box pull')
    m.g('espresso machine')
    m.box((-13.0, 9.0, 10.6), (-3.4, 15.4, 15.0), 'metal:steel', 'machine body')
    m.box((-13.2, 15.4, 10.4), (-3.2, 15.8, 15.2), 'metal:steel', 'cup warmer')
    for x in (-12.6, -10.8, -9.0, -7.2, -5.4):
        m.cyl((x + 0.6, 15.8, 12.4), 0.5, 1.0, 'y', 'paint:#f8f4ec', 'warming cup')
    m.box((-12.8, 9.0, 9.6), (-3.6, 9.6, 10.6), 'metal:steel', 'drip tray')
    for x in (-12.4, -11.6, -10.8, -10.0, -9.2, -8.4, -7.6, -6.8, -6.0, -5.2, -4.4):
        m.box((x, 9.6, 9.7), (x + 0.3, 9.65, 10.5), 'paint:#202020', 'drip grid slot')
    for x in (-10.6, -6.0):
        m.box((x - 0.8, 12.2, 10.0), (x + 0.8, 13.4, 10.6), 'metal:steel', 'group head')
        m.cyl((x, 11.4, 10.3), 0.7, 0.8, 'y', 'metal:steel', 'portafilter basket')
        m.box((x - 0.2, 11.6, 7.8), (x + 0.2, 12.0, 10.0), 'paint:#101010', 'portafilter handle')
        m.cyl((x, 9.6, 10.3), 0.5, 1.0, 'y', 'paint:#f8f4ec', 'espresso cup')
        m.cyl((x, 10.5, 10.3), 0.42, 0.1, 'y', 'paint:#4a2a10', 'crema')
    for x in (-12.6, -3.8):
        m.box((x - 0.12, 9.8, 9.8), (x + 0.12, 13.6, 10.1), 'metal:steel', 'steam wand', rot={'axis': 'z', 'angle': -22.5 if x < -8 else 22.5, 'origin': [x, 13.6, 10.0]})
        m.cyl((x, 13.4, 10.2), 0.35, 0.6, 'y', 'paint:#101010', 'steam knob')
    m.box((-12.8, 13.8, 10.4), (-3.6, 15.2, 10.6), 'paint:#24282c', 'control strip')
    em = m.surface('north', face=10.38, title='ESPRESSO')
    for i, (cid, lab) in enumerate((('espresso', 'ESPRESSO'), ('americano', 'AMERICANO'), ('latte', 'LATTE'), ('cappuccino', 'CAPPUCC.'), ('mocha', 'MOCHA'))):
        m.ctl(em, 'push', cid, X(-12.2 + i * 1.75), 14.75, w=0.75, h=0.75, col='e8e0d0', label=lab)
        m.ctl(em, 'label', '', X(-12.2 + i * 1.75), 14.1, w=0, h=0, col='e8e8e8', label=lab)
    m.box((-8.8, 10.0, 10.4), (-7.8, 13.6, 10.6), 'paint:#24282c', 'gauge panel')
    m.ctl(em, 'gauge', 'gpress', X(-8.3), 12.9, w=1.0, h=1.0, col='f2f0e6', label='BAR', max=100)
    m.ctl(em, 'gauge', 'gtemp', X(-8.3), 11.1, w=1.0, h=1.0, col='f2f0e6', label='C', max=100)
    for i, (cid, col, lab, bl) in enumerate((('heat', ORANGE, 'HEAT', False), ('ready', GREEN, 'READY', False), ('brew', BLUE, 'BREW', True), ('cleanl', RED, 'CLEAN', True))):
        m.ctl(em, 'lamp', cid, X(-4.4), 13.3 - i * 0.8, w=0.45, h=0.45, col=col, label=lab, **({'blink': True} if bl else {}))
        m.ctl(em, 'label', '', X(-5.4), 13.3 - i * 0.8, w=0, h=0, col='e8e8e8', label=lab)
    m.box((-13.2, 9.0, 13.0), (-12.0, 15.4, 15.0), 'paint:#2a2c2e', 'machine side panel', east='label:PRIDE')
    # ---- counter (middle): grinder, cup tube, till, bell, controls
    desk(m, {('l', 1): 'cups'}, top=9.0, body='#4a2e1e', front='#6a4630', pull='metal:brass')
    sf = strip(m, 4.0, 8.4, top=9.0)
    pushes(m, sf, [('grind', 'c0a040', 'GRIND'), ('steam', 'e0e0e0', 'STEAM'), ('clean', '2050d0', 'CLEAN'), ('sugar', 'f0f0f0', 'SUGAR'), ('bell', 'c0a040', 'BELL')],
           X(1.6), 7.6, step=-2.6, size=0.9)
    m.ctl(sf, 'key', 'k_open', X(1.6), 5.2, w=0.9, h=0.9, col='c0a040', label='SHUTTER', pos=['CLOSED', 'OPEN'])
    m.ctl(sf, 'key', 'k_boiler', X(3.6), 5.2, w=0.9, h=0.9, col='d04020', label='BOILER', pos=['OFF', 'ON'])
    m.ctl(sf, 'selector', 's_size', X(5.8), 5.2, w=1.0, h=1.0, col='202020', label='SIZE', pos=['SMALL', 'REG', 'LARGE'])
    m.ctl(sf, 'selector', 's_shots', X(7.8), 5.2, w=1.0, h=1.0, col='202020', label='SHOTS', pos=['SINGLE', 'DOUBLE'])
    lamps(m, sf, [('openl', GREEN, 'OPEN'), ('beanlow', AMBER, 'BEANS'), ('milklow', AMBER, 'MILK'), ('cuplow', AMBER, 'CUPS'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')],
          X(9.6), 5.2, step=-1.15, size=0.45, lab_dv=0.55)
    monitor(m, 4.2, 12.4, 11.6, 16.6, 11.0, ['status', 'press', 'stock', 'sold', 'dirty', 'last'],
            cols=['ffd040', '5bcefa', '7cf29a', '7cf29a', 'ffb020', '8a94a0'], stand_y=9.0)
    till(m, 12.4, 9.8)
    counter_bell(m, 1.4, 10.4)
    m.g('grinder')
    m.box((0.6, 9.0, 9.6), (3.4, 11.0, 12.0), 'paint:#2a2c2e', 'grinder base')
    m.box((1.0, 11.0, 9.8), (3.0, 13.4, 11.8), 'paint:#3a3d42', 'grinder body')
    m.box((1.2, 9.0, 9.4), (2.8, 9.6, 9.6), 'metal:steel', 'grinder fork')
    m.box((0.8, 13.4, 9.6), (3.2, 16.2, 12.0), 'glass', 'bean hopper')
    for y in (13.6, 14.2, 14.8):
        m.box((1.0, y, 9.8), (3.0, y + 0.5, 11.8), 'paint:#3a1e0e', 'beans')
    m.box((0.7, 16.2, 9.5), (3.3, 16.5, 12.1), 'paint:#101010', 'hopper lid')
    gr = m.surface('north', face=9.58, title='HOPPER')
    m.ctl(gr, 'zone', 'beans', X(2.0), 14.8, w=2.4, h=2.8, col='000000', label='')
    m.g('cup tube')
    m.cyl((14.6, 9.0, 6.2), 0.8, 5.0, 'y', 'glass', 'cup dispenser')
    for i in range(8):
        m.cyl((14.6, 9.2 + i * 0.55, 6.2), 0.65, 0.5, 'y', 'paint:#f4f0e8', 'paper cup')
    SPOTS['coffee_kiosk'] = cupboard_shelf(m, 18.4, 29.4, ['cups'], 6, title='PASTRY CASE', body='#4a2e1e', door='#6a4630')
    m.box((18.8, 7.0, 11.4), (29.0, 12.6, 11.6), 'glass', 'pastry case front')
    m.box((18.8, 12.6, 11.4), (29.0, 12.8, 15.0), 'glass', 'pastry case top')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    m.cyl((-13.0, 0.0, 14.6), 0.3, 9.0, 'y', 'metal:copper', 'water feed pipe')
    m.save(item_gui=0.28)
    return m


# ============================================================================================== ticket office
def ticket_office():
    m = Model('ticket_office')
    back_wall(m, panel='#1f3a5a', plaster='#eef0f0', top=30)
    fascia(m, 'TICKETS - BOOKING OFFICE', '#14304e')
    SPOTS['ticket_office'] = cupboard_shelf(m, -13.4, -2.6, ['rack'], 6, title='LEAFLETS', body='#1f3a5a', door='#2f5a80')
    m.box((-13.2, 10.4, 14.4), (-2.8, 13.6, 15.0), 'plate', 'leaflet sign', north='label:TIMETABLES')
    # ---- counter + window
    desk(m, {('l', 1): 'cash', ('r', 1): 'stock'}, top=9.0, body='#1f3a5a', front='#2f5a80', pull='metal:steel')
    m.g('booking window')
    m.box((-0.4, 9.0, 3.0), (16.4, 9.6, 4.0), 'paint:#14304e', 'window sill')
    for x in (-0.4, 7.8, 16.0):
        m.box((x, 9.6, 3.2), (x + 0.4, 24.0, 3.8), 'metal:steel', 'window mullion')
    m.box((-0.4, 24.0, 3.0), (16.4, 25.0, 4.0), 'paint:#14304e', 'window head')
    m.box((0.0, 13.0, 3.45), (7.8, 24.0, 3.55), 'glass', 'glass screen')
    m.box((8.2, 13.0, 3.45), (16.0, 24.0, 3.55), 'glass', 'glass screen')
    for x in (3.9, 12.1):
        m.cyl((x, 15.0, 3.35), 0.9, 0.3, 'z', 'metal:steel', 'speaking grille')
        for dx in (-0.4, 0.0, 0.4):
            m.box((x + dx - 0.06, 14.5, 3.3), (x + dx + 0.06, 15.5, 3.35), 'paint:#202020', 'grille slot')
    m.box((1.6, 9.6, 2.6), (6.2, 10.0, 4.4), 'metal:steel', 'deal tray')
    m.box((9.8, 9.6, 2.6), (14.4, 10.0, 4.4), 'metal:steel', 'deal tray')
    # customer display over the window + clerk screen behind
    m.box((0.2, 19.6, 3.2), (15.8, 23.6, 3.45), 'paint:#070a08', 'customer display')
    cd = m.surface('north', face=3.18, title='WINDOW')
    for i, (cid, col) in enumerate((('stn', 'ffffff'), ('sel', 'ffb020'), ('fare', '7cf29a'), ('pay', '5bcefa'))):
        m.ctl(cd, 'digits', cid, X(8.0), 23.0 - i * 0.95, w=15.0, h=0.85, col=col, label='', fmt='%s')
    m.box((0.2, 17.0, 3.2), (15.8, 19.4, 3.45), 'paint:#070a08', 'queue display')
    for i, (cid, col) in enumerate((('queue', 'ffd040'), ('clock', 'ffffff'))):
        m.ctl(cd, 'digits', cid, X(8.0), 18.8 - i * 1.1, w=15.0, h=0.9, col=col, label='', fmt='%s')
    lamps(m, cd, [('openl', GREEN, 'OPEN'), ('servicel', GREEN, 'SERVING'), ('queuel', AMBER, 'QUEUE', True), ('boardl', BLUE, 'BOARDING'), ('peakl', AMBER, 'PEAK')],
          X(1.6), 16.2, step=-3.0, size=0.6, lab_dv=0.7)
    monitor(m, 3.0, 13.0, 12.4, 16.0, 11.4, ['sold', 'stockn', 'last'], cols=['7cf29a', '7cf29a', 'ffb020'], stand_y=9.0)
    m.g('ticket printer')
    m.box((12.8, 9.0, 9.0), (15.6, 11.0, 11.4), 'paint:#2a2c2e', 'ticket printer')
    m.box((13.2, 10.2, 8.9), (15.2, 10.4, 9.0), 'paint:#101010', 'ticket slot')
    m.box((13.4, 10.0, 8.6), (15.0, 10.1, 9.0), 'paint:#f0d8a0', 'ticket')
    m.box((0.6, 9.0, 9.4), (3.0, 9.8, 11.6), 'paint:#202226', 'ticket stamp')
    m.cyl((1.8, 9.8, 10.5), 0.4, 1.2, 'y', 'wood', 'stamp handle')
    # front strip: keypad, fare controls
    sf = strip(m, 4.0, 8.4, top=9.0)
    keypad(m, sf, ['n7', 'n8', 'n9', 'n4', 'n5', 'n6', 'n1', 'n2', 'n3', 'nclr', 'n0', 'issue'], 0.8, 7.9, step=0.95, size=0.75,
           labels=['7', '8', '9', '4', '5', '6', '1', '2', '3', 'CLR', '0', 'ISSUE'])
    pushes(m, sf, [('single', 'e0e0e0', 'SINGLE'), ('return', 'e0e0e0', 'RETURN'), ('dprev', 'e0e0e0', 'DEST <'), ('dnext', 'e0e0e0', 'DEST >')], X(4.6), 7.6, step=-1.9, size=0.8)
    pushes(m, sf, [('fares', '2050d0', 'FARES'), ('call', 'f0d020', 'CALL TRAIN'), ('next', '30a040', 'NEXT'), ('bell', 'c0a040', 'BELL')], X(4.6), 5.2, step=-1.9, size=0.8)
    m.ctl(sf, 'key', 'k_open', X(12.4), 7.6, w=0.9, h=0.9, col='c0a040', label='WINDOW', pos=['CLOSED', 'OPEN'])
    m.ctl(sf, 'selector', 's_pay', X(14.2), 7.6, w=0.9, h=0.9, col='202020', label='PAY', pos=['ITEMS', 'COINS'])
    m.ctl(sf, 'selector', 's_scheme', X(12.4), 5.2, w=0.9, h=0.9, col='202020', label='FARES', pos=['DIST', 'ZONES', 'FLAT'])
    for i, (cid, col, lab) in enumerate((('rtnl', BLUE, 'RTN'), ('stockl', RED, 'STOCK'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'))):
        m.ctl(sf, 'lamp', cid, X(14.2 + (i % 2) * 1.0), 5.6 - (i // 2) * 1.2, w=0.4, h=0.4, col=col, label=lab)
        m.ctl(sf, 'label', '', X(14.2 + (i % 2) * 1.0), 5.1 - (i // 2) * 1.2, w=0, h=0, col='e8e8e8', label=lab)
    # right: departures poster + ticket racks
    m.g('poster boards')
    m.box((18.4, 0, 11.6), (29.4, 9.0, 15.4), 'paint:#1f3a5a', 'rack cabinet')
    for k in range(4):
        y = 1.0 + k * 2.0
        m.box((18.8, y, 11.4), (29.0, y + 1.6, 11.6), 'paint:#2f5a80', 'rack drawer')
        m.box((23.4, y + 0.6, 11.2), (24.4, y + 1.0, 11.4), 'metal:steel', 'rack pull')
    for i, (x0, txt) in enumerate(((18.8, 'DEPARTURES'), (24.2, 'FARES'))):
        m.box((x0, 10.4, 14.8), (x0 + 4.8, 24.0, 15.2), 'wood', 'poster frame')
        m.box((x0 + 0.3, 21.6, 14.7), (x0 + 4.5, 23.6, 14.8), 'plate', 'poster title', north='label:' + txt)
        for j in range(8):
            m.box((x0 + 0.4, 11.0 + j * 1.2, 14.7), (x0 + 4.4 - (j % 3) * 0.6, 11.5 + j * 1.2, 14.8), 'paint:#30343a', 'timetable line')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    m.save(item_gui=0.28)
    return m


# =================================================================================== fire / ambulance / police desks
KINDS = {
    'fire_station': dict(sign='FIRE STATION - WATCH ROOM', panel='#5a1a1a', fas='#a01818', a='BA SETS', b='HOSE + TOOLS', accent='d02020'),
    'ambulance_station': dict(sign='AMBULANCE STATION', panel='#1f4a3a', fas='#18a050', a='DRUGS', b='KIT + O2', accent='30c060'),
    'police_station': dict(sign='POLICE - FRONT DESK', panel='#1a2a5a', fas='#14286e', a='RIOT KIT', b='BARRIERS', accent='2050d0'),
}


def emergency_desk(name):
    k = KINDS[name]
    m = Model(name)
    back_wall(m, panel=k['panel'], plaster='#eceae4', top=30)
    fascia(m, k['sign'], k['fas'])
    SPOTS[name] = cupboard_shelf(m, -13.4, -2.6, ['logd'], 6, title='DISPLAY', body=k['panel'], door='#5a5a5a')
    # ---- turnout bell + call map on the back wall
    m.g('turnout bell')
    m.cyl((8.0, 20.6, 15.0), 0.4, 0.4, 'z', 'metal:steel', 'bell bracket')
    m.cyl((8.0, 20.6, 13.6), 1.9, 1.4, 'z', 'paint:#c01818', 'alarm gong')
    m.cyl((8.0, 20.6, 13.3), 0.5, 0.3, 'z', 'metal:steel', 'gong striker')
    m.box((6.6, 18.0, 14.6), (9.4, 18.6, 15.2), 'plate', 'bell plate', north='label:ALARM')
    m.g('area map')
    m.box((0.6, 22.6, 15.0), (15.4, 26.4, 15.3), 'wood', 'map frame')
    m.box((1.0, 23.0, 14.95), (15.0, 26.0, 15.0), 'paint:#d8e0c8', 'area map')
    for (x, y, c) in ((3.0, 24.0, '#c02020'), (6.4, 25.2, '#2050d0'), (9.8, 23.6, '#20a040'), (12.6, 24.8, '#c0a020'), (5.0, 23.4, '#202020')):
        m.box((x, y, 14.85), (x + 0.4, y + 0.4, 14.95), 'paint:' + c, 'map pin')
    for x0 in (1.4, 4.8, 8.2, 11.6):
        m.box((x0, 23.8, 14.9), (x0 + 3.0, 23.95, 14.95), 'paint:#808080', 'map road')
    # ---- lockers (right)
    m.g('lockers')
    for (x0, x1, cid, lab) in ((18.4, 23.6, 'lockA', k['a']), (24.0, 29.4, 'lockB', k['b'])):
        m.box((x0, 0, 11.6), (x1, 24.0, 15.4), 'paint:#8a8e94', 'locker body')
        m.box((x0 + 0.2, 0.4, 11.4), (x1 - 0.2, 23.6, 11.6), 'paint:#9aa0a6', 'locker door')
        for y in (20.0, 20.6, 21.2, 21.8):
            m.box((x0 + 1.0, y, 11.3), (x1 - 1.0, y + 0.25, 11.4), 'paint:#5a5e64', 'locker vent')
        m.box((x1 - 1.2, 10.0, 11.1), (x1 - 0.8, 13.0, 11.4), 'metal:steel', 'locker handle')
        m.box((x1 - 1.4, 9.0, 11.2), (x1 - 0.6, 9.6, 11.4), 'metal:brass', 'padlock hasp')
        m.box((x0 + 0.6, 16.4, 11.3), (x1 - 0.6, 18.0, 11.4), 'plate', 'locker label', north='label:' + lab)
        for y in (2.0, 22.0):
            m.box((x0 + 0.2, y, 11.3), (x0 + 0.5, y + 1.2, 11.4), 'metal:steel', 'hinge')
    lk = m.surface('north', face=11.28, title='LOCKERS')
    m.ctl(lk, 'zone', 'lockA', X(21.0), 12.0, w=5.0, h=22.0, col='000000', label='')
    m.ctl(lk, 'zone', 'lockB', X(26.7), 12.0, w=5.0, h=22.0, col='000000', label='')
    # ---- kind decor
    m.g('equipment')
    if name == 'fire_station':
        for i, x in enumerate((-12.6, -9.4, -6.2)):
            m.box((x, 17.0, 15.0), (x + 0.3, 17.4, 15.4), 'metal:steel', 'helmet hook')
            m.box((x - 0.9, 15.4, 13.4), (x + 1.2, 17.0, 15.2), 'paint:#e8e030' if i == 0 else 'paint:#202020', 'fire helmet')
            m.box((x - 1.2, 15.2, 13.0), (x + 1.5, 15.5, 15.4), 'paint:#e8e030' if i == 0 else 'paint:#202020', 'helmet brim')
            m.box((x - 0.8, 15.9, 13.3), (x + 1.1, 16.2, 13.4), 'paint:#d0d0d0', 'reflective band')
        m.cyl((-4.0, 12.0, 13.6), 2.6, 1.4, 'z', 'paint:#c01818', 'hose reel')
        m.cyl((-4.0, 12.0, 13.4), 1.0, 1.8, 'z', 'metal:steel', 'reel drum')
        m.box((-3.2, 15.8, 14.4), (-2.8, 22.0, 15.0), 'wood', 'axe handle')
        m.box((-3.8, 21.0, 14.4), (-2.2, 22.4, 14.8), 'paint:#c01818', 'axe head')
    elif name == 'ambulance_station':
        m.box((-12.8, 14.0, 13.4), (-9.0, 17.6, 15.2), 'paint:#f0d020', 'defibrillator')
        m.box((-12.4, 14.4, 13.3), (-9.4, 16.2, 13.4), 'paint:#20a040', 'defib panel', north='label:AED')
        m.cyl((-7.4, 11.0, 14.2), 0.8, 7.0, 'y', 'paint:#f0f0f0', 'oxygen cylinder')
        m.cyl((-7.4, 17.4, 14.2), 0.3, 0.8, 'y', 'metal:brass', 'cylinder valve')
        m.box((-6.0, 11.0, 12.6), (-2.8, 13.8, 15.2), 'paint:#20a040', 'paramedic bag')
        m.box((-5.0, 13.8, 13.6), (-3.8, 14.2, 14.2), 'paint:#101010', 'bag handle')
    else:
        m.cyl((-8.0, 20.0, 14.6), 0.2, 2.0, 'y', 'metal:steel', 'lamp bracket')
        m.box((-9.4, 15.6, 12.8), (-6.6, 20.0, 15.4), 'paint:#14286e', 'blue lamp')
        m.box((-9.2, 16.0, 12.7), (-6.8, 19.6, 12.8), 'lamp:5080ff', 'blue lamp lens')
        m.box((-9.0, 18.4, 12.65), (-7.0, 19.2, 12.7), 'plate', 'lamp sign', north='label:POLICE')
        m.box((-5.6, 11.0, 13.0), (-2.8, 15.0, 15.2), 'paint:#202226', 'evidence safe')
        m.cyl((-4.2, 13.0, 12.9), 0.5, 0.2, 'z', 'metal:steel', 'safe dial')
    # ---- the desk
    desk(m, {}, top=9.0, body=k['panel'], front='#5a5a5a', pull='metal:steel')
    sf = strip(m, 4.0, 8.4, top=9.0)
    hd = hood(m)
    monitor(m, 1.4, 14.6, 12.2, 19.4, 11.6, ['stn', 'status', 'inc', 'eta', 'rtime', 'runs', 'veh', 'crewn', 'last'],
            cols=['ffffff', 'ffd040', 'ff6060', '5bcefa', '7cf29a', '7cf29a', '8a94a0', '8a94a0', 'ffb020'], stand_y=11.8)
    lamps(m, hd, [('avail', GREEN, 'AVAIL'), ('turnout', AMBER, 'TURNOUT', True), ('resp', BLUE, 'MOBILE', True), ('scene', RED, 'SCENE'), ('ret', GREEN, 'RETURN', True),
                  ('callw', RED, 'CALL', True), ('autol', AMBER, 'AUTO'), ('lockok', GREEN, 'LOCKERS'), ('silentl', AMBER, 'SILENT')], X(1.4), 10.9, step=-1.25, size=0.5, lab_dv=0.55)
    m.ctl(hd, 'gauge', 'gresp', X(13.3), 10.4, w=1.4, h=1.4, col='f2f0e6', label='RESP', max=100)
    m.ctl(hd, 'digits', 'clock', X(14.9), 10.4, w=1.2, h=0.7, col='ff3030', label='', fmt='%s')
    m.ctl(sf, 'mushroom', 'dispatch', X(1.8), 7.0, w=1.4, h=1.4, col='d02020', label='DISPATCH')
    m.ctl(sf, 'label', '', X(1.8), 5.6, w=0, h=0, col='e8e8e8', label='DISPATCH')
    pushes(m, sf, [('recall', 'f0d020', 'RECALL'), ('incprev', 'e0e0e0', 'INC <'), ('incnext', 'e0e0e0', 'INC >'), ('bell', 'd02020', 'BELL'), ('radio', '2050d0', 'RADIO')],
           X(3.8), 7.6, step=-1.75, size=0.8)
    pushes(m, sf, [('log', 'e0e0e0', 'LOG'), ('ack', 'f0f0f0', 'ACK'), ('test', 'f0f0f0', 'TEST')], X(3.8), 5.2, step=-1.75, size=0.7)
    m.ctl(sf, 'key', 'k_manual', X(9.4), 5.2, w=0.9, h=0.9, col='c0a040', label='MODE', pos=['AUTO', 'MANUAL'])
    m.ctl(sf, 'key', 'k_silent', X(11.2), 5.2, w=0.9, h=0.9, col='c0a040', label='SIRENS', pos=['ON', 'SILENT'])
    m.ctl(sf, 'selector', 's_crew', X(13.0), 5.2, w=1.0, h=1.0, col='202020', label='CREW', pos=['2', '4', '6'])
    m.ctl(sf, 'lamp', 'pwr', X(14.6), 5.6, w=0.4, h=0.4, col=GREEN, label='12V')
    m.ctl(sf, 'label', '', X(14.6), 5.1, w=0, h=0, col='e8e8e8', label='12V')
    m.ctl(sf, 'lamp', 'mainsl', X(14.6), 7.2, w=0.4, h=0.4, col=GREEN, label='MAINS')
    m.ctl(sf, 'label', '', X(14.6), 6.7, w=0, h=0, col='e8e8e8', label='MAINS')
    m.g('radio set')
    m.box((13.4, 9.0, 9.0), (15.8, 10.8, 11.2), 'paint:#202226', 'radio set')
    m.box((13.6, 10.0, 8.9), (15.0, 10.6, 9.0), 'paint:#03140a', 'radio display')
    m.cyl((15.4, 9.6, 8.9), 0.25, 0.15, 'z', 'metal:steel', 'channel knob')
    m.box((13.0, 9.0, 11.4), (14.4, 9.6, 12.8), 'paint:#101010', 'handset')
    m.box((15.6, 10.8, 10.6), (15.8, 14.0, 10.8), 'metal:steel', 'radio aerial')
    m.box((13.0, 9.05, 12.0), (14.4, 9.15, 12.2), 'cable:1d1d21', 'handset cord')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    m.save(item_gui=0.28)
    return m


# ===================================================================================================== cordon
def police_cordon():
    m = Model('police_cordon')
    m.g('feet')
    for x in (0.6, 15.4):
        m.box((x - 0.4, 0, 3.0), (x + 0.4, 0.6, 13.0), 'paint:#d0d4d8', 'barrier foot')
        m.box((x - 0.3, 0.6, 7.6), (x + 0.3, 15.0, 8.4), 'paint:#e8ecf0', 'barrier leg')
    m.g('frame')
    for y in (1.6, 14.4):
        m.box((0.2, y, 7.6), (15.8, y + 0.6, 8.4), 'paint:#e8ecf0', 'barrier rail')
    for i in range(1, 14):
        m.box((0.6 + i * 1.07, 2.2, 7.85), (0.85 + i * 1.07, 14.4, 8.15), 'paint:#e8ecf0', 'barrier bar')
    m.g('tape')
    for (y, txt) in ((11.0, 'POLICE LINE'), (6.0, 'DO NOT CROSS')):
        m.box((0.2, y, 7.5), (15.8, y + 1.8, 8.5), 'paint:#1a3aa0', 'cordon tape', north='label:' + txt, south='label:' + txt)
    for x in range(1, 16, 2):
        m.box((x, 15.0, 7.7), (x + 1, 15.6, 8.3), 'paint:#1a3aa0' if x % 4 == 1 else 'paint:#f0f0f0', 'battenburg check')
    for x in (0.6, 15.4):
        for z in (3.4, 12.6):
            m.box((x - 0.5, 0, z - 0.4), (x + 0.5, 0.3, z + 0.4), 'paint:#202020', 'rubber pad')
        m.box((x - 0.35, 13.6, 7.4), (x + 0.35, 14.2, 8.6), 'metal:steel', 'link hook')
    m.box((6.0, 8.6, 7.45), (10.0, 9.8, 8.55), 'plate', 'force plate', north='label:PRIDE POLICE', south='label:PRIDE POLICE')
    m.box((7.2, 15.6, 7.8), (8.8, 16.0, 8.2), 'lamp:ffb020', 'barrier lamp')
    m.save(item_gui=0.625)
    return m


# =================================================================================== vehicles (HALF SIZE, drawn x2)
def wheel(m, x, z, r=2.6, w=1.8):
    m.cyl((x, r, z), r, w, 'x', 'paint:#161618', 'tyre')
    m.cyl((x - 0.05 if x < 8 else x + w - 0.05, r, z), r * 0.55, 0.1, 'x', 'metal:steel', 'wheel rim')
    m.cyl((x - 0.1 if x < 8 else x + w, r, z), r * 0.2, 0.1, 'x', 'paint:#202020', 'hub nut')


def interior(m, x0, x1, z, y, seat='#202020', rows=1, step=6.0):
    """seats, dashboard, steering wheel seen through the glass"""
    g = m.group
    m.g('interior')
    m.box((x0, y + 2.6, z - 1.6), (x1, y + 3.6, z - 0.4), 'paint:#18181a', 'dashboard')
    m.box((x0 + 0.6, y + 3.6, z - 1.2), (x0 + 2.6, y + 4.0, z - 0.8), 'paint:#03140a', 'dash screen')
    m.cyl((x1 - 3.0, y + 3.2, z - 0.4), 1.0, 0.25, 'z', 'paint:#101010', 'steering wheel')
    m.box((x1 - 3.2, y + 2.4, z - 0.4), (x1 - 2.8, y + 3.2, z + 0.6), 'paint:#101010', 'steering column')
    for r in range(rows):
        zz = z + 1.0 + r * step
        for sx in ((x0 + 0.6, (x0 + x1) / 2 - 0.4), ((x0 + x1) / 2 + 0.4, x1 - 0.6)):
            m.box((sx[0], y, zz), (sx[1], y + 1.2, zz + 2.6), 'paint:' + seat, 'seat cushion')
            m.box((sx[0], y + 1.2, zz + 2.2), (sx[1], y + 4.4, zz + 2.8), 'paint:' + seat, 'seat back')
            m.box((sx[0] + 0.6, y + 4.4, zz + 2.3), (sx[1] - 0.6, y + 5.2, zz + 2.7), 'paint:' + seat, 'head rest')
    m.g(g)


def wiper(m, x, y, z):
    m.box((x, y, z - 0.12), (x + 3.0, y + 0.18, z), 'paint:#101010', 'wiper', rot={'axis': 'z', 'angle': 22.5, 'origin': [x, y, z]})


def lens(m, x0, y0, z0, x1, y1, z1, col, name='beacon lens'):
    """a lamp lens just inside where EmsClient draws the flashing overlay"""
    e = 0.06
    m.box((x0 + e, y0 + e, z0 + e), (x1 - e, y1 - e, z1 - e), 'lamp:' + col, name)


def fire_engine():
    m = Model('ems_fire_engine')
    R = 'paint:#c81414'
    m.g('chassis')
    m.box((1.4, 1.6, -11.6), (14.6, 3.4, 27.6), 'paint:#202020', 'chassis frame')
    m.box((0.4, 2.2, -12.2), (15.6, 4.0, -11.4), 'metal:steel', 'front bumper')
    m.box((0.6, 2.6, 27.4), (15.4, 3.8, 28.2), 'paint:#202020', 'rear step')
    for (x, z) in ((-0.2, -6.0), (14.4, -6.0), (-0.2, 17.4), (14.4, 17.4), (-0.2, 22.6), (14.4, 22.6)):
        wheel(m, x, z)
    m.g('cab')
    m.box((0.4, 3.4, -12.0), (15.6, 18.0, 0.6), R, 'cab body')
    m.box((0.8, 10.2, -12.08), (15.2, 16.8, -11.98), 'glass', 'windscreen')
    for x in (0.32, 15.6):
        m.box((x, 10.4, -10.8), (x + 0.08, 16.4, -2.4), 'glass', 'cab side window')
        m.box((x, 10.4, -1.6), (x + 0.08, 16.4, 0.2), 'glass', 'crew window')
        m.box((x, 3.8, -10.6), (x + 0.1, 9.8, -6.4), 'paint:#a80c0c', 'cab door')
        m.box((x, 7.4, -7.4), (x + 0.15, 7.8, -6.8), 'metal:steel', 'door handle')
    m.box((0.6, 3.6, -12.1), (15.4, 9.6, -11.98), 'paint:#202020', 'grille', north='label:PRIDE FIRE')
    for y in (4.4, 5.8, 7.2, 8.4):
        m.box((5.8, y, -12.14), (10.2, y + 0.3, -12.08), 'metal:steel', 'grille bar')
    m.box((-0.6, 12.6, -10.6), (0.4, 14.6, -10.0), 'paint:#202020', 'mirror')
    m.box((15.6, 12.6, -10.6), (16.6, 14.6, -10.0), 'paint:#202020', 'mirror')
    m.box((0.4, 18.0, -11.6), (15.6, 18.2, 0.4), 'paint:#f0f0f0', 'cab roof')
    m.box((2.8, 18.2, -10.6), (13.2, 19.4, -8.6), 'paint:#202020', 'light bar base')
    lens(m, 3.0, 18.2, -10.4, 7.6, 19.0, -8.8, 'ff2020'); lens(m, 8.4, 18.2, -10.4, 13.0, 19.0, -8.8, 'ff2020')
    lens(m, 0.4, 16.8, -11.6, 1.4, 17.8, -10.8, 'ff2020'); lens(m, 14.6, 16.8, -11.6, 15.6, 17.8, -10.8, 'ff2020')
    lens(m, 4.4, 5.0, -12.12, 5.6, 5.8, -12.0, 'ff2020'); lens(m, 10.4, 5.0, -12.12, 11.6, 5.8, -12.0, 'ff2020')
    lens(m, 1.6, 6.8, -12.12, 3.6, 8.0, -12.0, 'fff4d0', 'headlight'); lens(m, 12.4, 6.8, -12.12, 14.4, 8.0, -12.0, 'fff4d0', 'headlight')
    m.g('body')
    m.box((0.6, 3.6, 1.0), (15.4, 20.0, 27.6), R, 'locker body')
    for x in (0.52, 15.4):
        for (z0, z1) in ((1.6, 8.2), (8.6, 15.2), (15.6, 21.4), (21.8, 27.0)):
            m.box((x, 5.0, z0), (x + 0.08, 18.6, z1), 'paint:#b0b4b8', 'roller shutter')
            for y in (6.0, 8.0, 10.0, 12.0, 14.0, 16.0):
                m.box((x - 0.02, y, z0), (x + 0.1, y + 0.12, z1), 'paint:#80848a', 'shutter slat')
            m.box((x - 0.04, 5.0, (z0 + z1) / 2 - 0.6), (x + 0.12, 5.6, (z0 + z1) / 2 + 0.6), 'metal:steel', 'shutter handle')
        m.box((x - 0.02, 3.8, 1.0), (x + 0.1, 4.8, 27.6), 'paint:#f0e020', 'chevron stripe')
    m.box((0.6, 4.8, 27.55), (15.4, 19.6, 27.65), 'haz', 'rear chevrons')
    m.box((5.0, 6.0, 27.5), (11.0, 16.0, 27.7), 'paint:#80848a', 'pump bay door')
    for (x, y) in ((6.4, 14.0), (9.6, 14.0)):
        m.cyl((x, y, 27.7), 0.7, 0.2, 'z', 'gauge', 'pump gauge')
    for x in (6.2, 9.8):
        m.cyl((x, 8.0, 27.7), 0.5, 0.8, 'z', 'metal:brass', 'hose outlet')
    m.g('roof gear')
    m.box((0.6, 20.0, 1.0), (15.4, 20.2, 27.6), 'paint:#d0d0d0', 'body roof')
    for x in (3.0, 12.4):
        m.box((x, 20.2, 2.0), (x + 0.6, 21.0, 26.6), 'metal:steel', 'ladder stile')
    for z in range(3, 26, 2):
        m.box((3.0, 20.4, z), (13.0, 20.8, z + 0.4), 'metal:steel', 'ladder rung')
    m.box((2.6, 20.2, 2.0), (3.0, 21.4, 2.8), 'paint:#202020', 'ladder gantry')
    m.box((13.0, 20.2, 2.0), (13.4, 21.4, 2.8), 'paint:#202020', 'ladder gantry')
    m.cyl((8.0, 20.2, 24.0), 1.6, 1.0, 'y', 'paint:#202020', 'hose reel drum')
    lens(m, 0.4, 20.2, 26.4, 1.6, 21.4, 27.6, 'ff2020'); lens(m, 14.4, 20.2, 26.4, 15.6, 21.4, 27.6, 'ff2020')
    m.box((0.6, 17.0, 1.0), (15.4, 17.4, 27.6), 'paint:#f0f0f0', 'body stripe')
    m.box((3.0, 10.0, 0.6), (13.0, 12.4, 1.0), 'plate', 'cab back plate', north='label:FIRE + RESCUE')
    m.save(item_gui=0.25)
    return m


def ambulance():
    m = Model('ems_ambulance')
    Y, G = 'paint:#f0e030', 'paint:#20a040'
    m.g('chassis')
    m.box((1.4, 1.6, -9.6), (14.6, 3.2, 25.6), 'paint:#202020', 'chassis frame')
    m.box((0.6, 2.2, -10.2), (15.4, 3.8, -9.4), 'paint:#303030', 'front bumper')
    for (x, z) in ((-0.2, -5.0), (14.4, -5.0), (-0.2, 19.0), (14.4, 19.0)):
        wheel(m, x, z, r=2.3, w=1.6)
    m.g('cab')
    m.box((0.6, 3.2, -10.0), (15.4, 8.4, -4.8), Y, 'bonnet')
    m.box((0.8, 8.4, -9.8), (15.2, 8.6, -5.0), Y, 'bonnet top')
    m.box((0.6, 3.2, -4.8), (15.4, 14.0, 0.2), Y, 'cab')
    m.box((0.8, 8.6, -4.9), (15.2, 13.6, -4.8), 'glass', 'windscreen', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 8.6, -4.8]})
    for x in (0.52, 15.4):
        m.box((x, 8.8, -4.0), (x + 0.08, 13.4, -0.4), 'glass', 'cab side window')
        m.box((x, 3.6, -4.2), (x + 0.1, 8.6, -0.4), 'paint:#e0d020', 'cab door')
    m.box((0.6, 3.4, -10.1), (15.4, 6.2, -9.98), 'paint:#202020', 'grille')
    for x in (-0.6, 15.4):
        m.box((x, 10.6, -4.6), (x + 1.2, 12.2, -4.0), 'paint:#202020', 'mirror')
    m.box((0.6, 14.0, -4.8), (15.4, 14.2, 0.2), 'paint:#f0f0f0', 'cab roof')
    m.box((3.8, 14.0, -4.4), (12.2, 14.2, -2.8), 'paint:#202020', 'light bar base')
    lens(m, 4.0, 14.0, -4.2, 7.8, 14.8, -3.0, '3060ff'); lens(m, 8.2, 14.0, -4.2, 12.0, 14.8, -3.0, '3060ff')
    lens(m, 3.0, 6.4, -10.12, 4.2, 7.0, -10.0, '3060ff'); lens(m, 11.8, 6.4, -10.12, 13.0, 7.0, -10.0, '3060ff')
    lens(m, 1.0, 7.2, -10.12, 2.8, 8.2, -10.0, 'fff4d0', 'headlight'); lens(m, 13.2, 7.2, -10.12, 15.0, 8.2, -10.0, 'fff4d0', 'headlight')
    m.g('box body')
    m.box((0.4, 3.4, 0.2), (15.6, 19.0, 25.8), Y, 'box body')
    m.box((0.4, 19.0, 0.2), (15.6, 19.2, 25.8), 'paint:#f0f0f0', 'box roof')
    for x in (0.32, 15.6):
        for (z0, z1, c) in ((0.6, 3.8, G), (3.8, 7.0, Y), (7.0, 10.2, G), (10.2, 13.4, Y), (13.4, 16.6, G), (16.6, 19.8, Y), (19.8, 23.0, G), (23.0, 25.6, Y)):
            m.box((x, 8.4, z0), (x + 0.08, 10.4, z1), c, 'battenburg')
        m.box((x, 11.6, 2.0), (x + 0.08, 15.8, 9.0), 'plate', 'side lettering', north='label:AMBULANCE', south='label:AMBULANCE', east='label:AMBULANCE', west='label:AMBULANCE')
        m.box((x, 11.4, 14.0), (x + 0.08, 17.0, 17.0), 'paint:#f0f0f0', 'star of life back')
        m.box((x - 0.02, 12.6, 15.1), (x + 0.1, 15.8, 15.9), 'paint:#1a4ab0', 'star of life')
        m.box((x - 0.02, 13.8, 14.3), (x + 0.1, 14.6, 16.7), 'paint:#1a4ab0', 'star of life')
        m.box((x, 3.8, 10.0), (x + 0.1, 16.6, 13.6), 'paint:#e0d020', 'side door')
        m.box((x, 12.0, 10.4), (x + 0.08, 15.6, 13.2), 'glass', 'side door window')
    m.box((1.0, 3.8, 25.8), (7.8, 17.8, 25.9), 'paint:#e0d020', 'rear door')
    m.box((8.2, 3.8, 25.8), (15.0, 17.8, 25.9), 'paint:#e0d020', 'rear door')
    m.box((1.6, 12.0, 25.9), (7.2, 16.4, 25.95), 'glass', 'rear window')
    m.box((8.8, 12.0, 25.9), (14.4, 16.4, 25.95), 'glass', 'rear window')
    m.box((1.0, 4.4, 25.85), (15.0, 8.0, 25.95), 'haz', 'rear chevrons')
    m.box((3.0, 2.4, 25.6), (13.0, 3.4, 26.8), 'metal:steel', 'rear step')
    lens(m, 0.6, 19.0, 0.4, 1.8, 20.0, 1.6, '3060ff'); lens(m, 14.2, 19.0, 0.4, 15.4, 20.0, 1.6, '3060ff')
    lens(m, 0.6, 19.0, 24.4, 1.8, 20.0, 25.6, '3060ff'); lens(m, 14.2, 19.0, 24.4, 15.4, 20.0, 25.6, 'ff2020')
    interior(m, 1.2, 14.8, -3.6, 4.2, seat='#30343a')
    wiper(m, 2.0, 8.8, -4.95); wiper(m, 8.6, 8.8, -4.95)
    m.g('patient bay')
    m.box((2.0, 3.6, 4.0), (6.6, 6.2, 22.0), 'paint:#d8d8dc', 'stretcher base')
    m.box((2.2, 6.2, 4.4), (6.4, 6.8, 21.6), 'paint:#2050a0', 'stretcher mattress')
    m.box((9.6, 3.6, 6.0), (14.8, 12.0, 9.0), 'paint:#e8e8ec', 'equipment cabinet')
    m.box((10.0, 9.0, 5.9), (14.4, 11.6, 6.0), 'paint:#03140a', 'patient monitor')
    m.cyl((12.4, 3.6, 20.0), 0.7, 6.0, 'y', 'paint:#f0f0f0', 'oxygen cylinder')
    for x in (0.4, 15.6):
        for z in (-5.0, 19.0):
            m.box((x - 0.15, 2.2, z - 2.4), (x + 0.15, 4.8, z + 2.4), 'paint:#202020', 'wheel arch')
    for x in (1.2, 13.2):
        m.box((x, 5.4, -10.1), (x + 1.6, 6.0, -10.0), 'lamp:ff8020', 'indicator')
        m.box((x, 5.4, 25.95), (x + 1.6, 6.4, 26.05), 'lamp:c01010', 'tail lamp')
    m.box((0.4, 17.6, 0.2), (15.6, 18.0, 25.8), 'paint:#20a040', 'roof stripe')
    m.box((5.0, 2.8, -10.25), (11.0, 3.8, -10.1), 'plate', 'front plate', north='label:AMB 1')
    for z in (12.0, 16.0):
        m.box((0.0, 19.2, z), (16.0, 19.6, z + 0.4), 'metal:steel', 'roof rail')
    m.box((6.0, 19.2, 8.0), (10.0, 19.6, 12.0), 'paint:#808488', 'roof vent')
    m.save(item_gui=0.25)
    return m


def police_car():
    m = Model('ems_police_car')
    W, B = 'paint:#f2f2f2', 'paint:#1a2a7a'
    m.g('chassis')
    for (x, z) in ((0.6, -1.6), (13.8, -1.6), (0.6, 16.4), (13.8, 16.4)):
        wheel(m, x, z, r=2.0, w=1.6)
    m.box((1.2, 1.4, -6.0), (14.8, 2.4, 22.0), 'paint:#202020', 'sills')
    m.g('body')
    m.box((1.0, 2.4, -6.0), (15.0, 7.0, 22.0), W, 'lower body')
    m.box((0.8, 2.0, -6.2), (15.2, 3.6, -5.6), 'paint:#202020', 'front bumper')
    m.box((0.8, 2.0, 21.6), (15.2, 3.6, 22.2), 'paint:#202020', 'rear bumper')
    m.box((1.0, 3.6, -6.12), (15.0, 6.6, -6.0), 'paint:#202020', 'grille')
    for x in (0.92, 15.0):
        m.box((x, 3.4, -5.6), (x + 0.08, 6.0, 21.6), B, 'blue side stripe')
        for (z0, z1) in ((-5.0, -1.0), (2.0, 7.4), (8.0, 13.6), (17.0, 21.0)):
            m.box((x - 0.02, 4.0, z0), (x + 0.1, 5.4, z1), 'paint:#f0e030' if (z0 // 5) % 2 == 0 else B, 'battenburg')
        m.box((x - 0.02, 5.0, 3.0), (x + 0.1, 6.6, 7.0), 'plate', 'side lettering', north='label:POLICE', south='label:POLICE', east='label:POLICE', west='label:POLICE')
    m.box((1.2, 7.0, -6.0), (14.8, 7.2, 1.0), W, 'bonnet')
    m.box((1.2, 7.0, 15.0), (14.8, 7.2, 22.0), W, 'boot lid')
    m.g('cabin')
    m.box((1.6, 7.0, 1.0), (14.4, 11.6, 15.0), 'paint:#202428', 'cabin')
    m.box((1.8, 7.4, 0.9), (14.2, 11.2, 1.0), 'glass', 'windscreen')
    m.box((1.8, 7.4, 15.0), (14.2, 11.2, 15.1), 'glass', 'rear window')
    for x in (1.52, 14.4):
        m.box((x, 7.6, 1.6), (x + 0.08, 11.0, 7.4), 'glass', 'front door window')
        m.box((x, 7.6, 7.8), (x + 0.08, 11.0, 14.2), 'glass', 'rear door window')
    m.box((1.6, 11.6, 1.0), (14.4, 12.0, 15.0), W, 'roof')
    m.box((4.0, 11.9, 3.0), (12.0, 12.0, 6.0), 'plate', 'roof number', north='label:PR 01')
    m.box((3.2, 12.0, 6.2), (12.8, 13.2, 10.8), 'paint:#202020', 'light bar base')
    lens(m, 3.6, 12.2, 6.4, 7.4, 13.0, 7.6, '3060ff'); lens(m, 8.6, 12.2, 6.4, 12.4, 13.0, 7.6, '3060ff'); lens(m, 7.4, 12.2, 6.4, 8.6, 13.0, 7.6, 'ffffff', 'takedown lamp')
    m.box((3.6, 12.2, 7.6), (12.4, 13.0, 10.6), 'paint:#30343a', 'siren speaker')
    m.box((0.0, 9.4, 0.6), (1.6, 10.6, 1.4), 'paint:#202020', 'mirror')
    m.box((14.4, 9.4, 0.6), (16.0, 10.6, 1.4), 'paint:#202020', 'mirror')
    lens(m, 4.6, 4.6, -6.12, 5.8, 5.2, -6.0, '3060ff'); lens(m, 10.2, 4.6, -6.12, 11.4, 5.2, -6.0, '3060ff')
    lens(m, 2.0, 5.4, -6.12, 4.0, 6.4, -6.0, 'fff4d0', 'headlight'); lens(m, 12.0, 5.4, -6.12, 14.0, 6.4, -6.0, 'fff4d0', 'headlight')
    lens(m, 4.0, 8.0, 14.6, 5.6, 8.6, 15.0, '3060ff', 'rear deck lamp'); lens(m, 10.4, 8.0, 14.6, 12.0, 8.6, 15.0, '3060ff', 'rear deck lamp')
    for x in (1.2, 12.4):
        m.box((x, 4.8, 21.98), (x + 2.4, 6.0, 22.1), 'lamp:c01010', 'tail lamp')
    m.box((5.0, 3.0, 22.0), (11.0, 4.4, 22.15), 'plate', 'number plate', north='label:PR 01', south='label:PR 01')
    m.box((5.0, 3.0, -6.25), (11.0, 3.8, -6.12), 'plate', 'front plate', north='label:PR 01')
    interior(m, 2.0, 14.0, 2.6, 4.0, seat='#2a2a30', rows=2, step=5.6)
    wiper(m, 3.0, 7.4, 0.9); wiper(m, 8.6, 7.4, 0.9)
    m.g('push bar + trim')
    for x in (3.0, 12.4):
        m.box((x, 2.2, -7.2), (x + 0.6, 6.4, -6.6), 'paint:#101010', 'push bar post')
    m.box((2.6, 5.6, -7.4), (13.4, 6.2, -6.8), 'paint:#101010', 'push bar rail')
    m.box((2.6, 3.0, -7.4), (13.4, 3.6, -6.8), 'paint:#101010', 'push bar rail')
    for x in (0.9, 15.0):
        for z in (3.6, 10.0):
            m.box((x - 0.1, 6.2, z), (x + 0.2, 6.5, z + 1.0), 'metal:steel', 'door handle')
        for z in (-3.2, 17.8):
            m.box((x - 0.15, 2.4, z - 2.6), (x + 0.15, 4.8, z + 2.6), 'paint:#202020', 'wheel arch')
    for x in (3.0, 13.0):
        m.cyl((x, 12.0, 13.0), 0.08, 3.2, 'y', 'paint:#101010', 'radio aerial')
    m.cyl((13.0, 1.8, 22.0), 0.3, 0.8, 'z', 'metal:steel', 'exhaust')
    m.box((1.0, 7.0, -0.2), (15.0, 7.3, 0.9), 'paint:#101010', 'scuttle')
    for x in (1.2, 13.0):
        m.box((x, 6.2, 21.9), (x + 1.8, 6.6, 22.05), 'lamp:ff8020', 'indicator')
        m.box((x, 6.2, -6.1), (x + 1.8, 6.6, -6.0), 'lamp:ff8020', 'indicator')
    m.save(item_gui=0.3)
    return m


# ===================================================================================================== coffee cups
CUPS = {'espresso': ('#f8f4ec', '#4a2a10', '#c0a040'), 'americano': ('#f4f0e8', '#3a1e0e', '#202020'), 'latte': ('#f4f0e8', '#d8c0a0', '#c03030'),
        'cappuccino': ('#f4f0e8', '#e8dcc8', '#3060c0'), 'mocha': ('#f4f0e8', '#5a3018', '#7a3a1a')}


def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def cups():
    os.makedirs(os.path.join(ASSETS, 'textures/items'), exist_ok=True)
    for n, (cup, drink, band) in CUPS.items():
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        small = n == 'espresso'
        top, bot = (7, 14) if small else (3, 14)
        for y in range(top, bot + 1):
            inset = (y - top) * 2 // (bot - top + 1)
            for x in range(4 + inset, 12 - inset):
                img.putpixel((x, y), hexc(cup))
            img.putpixel((4 + inset, y), (180, 176, 168, 255)); img.putpixel((11 - inset, y), (150, 146, 140, 255))
        for x in range(3, 13): img.putpixel((x, top - 1), hexc('#e0dcd4') if not small else hexc(drink))
        if not small:
            for x in range(3, 13): img.putpixel((x, top - 2), hexc('#ffffff'))
            for y in range(top + 3, top + 7):
                for x in range(5, 11): img.putpixel((x, y), hexc(band))
        if small:
            for y in (9, 10, 11): img.putpixel((12, y), hexc(cup)); img.putpixel((13, y), hexc(cup))
        for y, x in ((top - 4, 7), (top - 5, 8), (top - 6, 7)):
            if y >= 0: img.putpixel((x, y), (230, 230, 230, 140))
        img.save(os.path.join(ASSETS, 'textures/items/coffee_%s.png' % n))
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/coffee_' + n}}, open(os.path.join(ASSETS, 'models/item/coffee_%s.json' % n), 'w'), indent=1)


RECIPES = {
    'newsstand': (['PBP', 'WCW', 'WIW'], {'P': 'minecraft:paper', 'B': 'minecraft:book', 'W': 'minecraft:planks', 'C': 'minecraft:chest', 'I': 'minecraft:iron_ingot'}),
    'coffee_kiosk': (['IBI', 'WCW', 'WRW'], {'I': 'minecraft:iron_ingot', 'B': 'minecraft:bucket', 'W': 'minecraft:planks', 'C': 'minecraft:chest', 'R': 'minecraft:redstone'}),
    'ticket_office': (['GGG', 'WPW', 'WCW'], {'G': 'minecraft:glass_pane', 'W': 'minecraft:planks', 'P': 'minecraft:paper', 'C': 'minecraft:chest'}),
    'fire_station': (['RBR', 'ICI', 'IWI'], {'R': 'minecraft:redstone_block', 'B': 'minecraft:noteblock', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest', 'W': 'minecraft:water_bucket'}),
    'ambulance_station': (['RBR', 'ICI', 'IAI'], {'R': 'minecraft:redstone', 'B': 'minecraft:noteblock', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest', 'A': 'minecraft:golden_apple'}),
    'police_station': (['LBL', 'ICI', 'IFI'], {'L': 'minecraft:lapis_block', 'B': 'minecraft:noteblock', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest', 'F': 'minecraft:iron_bars'}),
}
DATA = {'minecraft:planks': 0, 'minecraft:golden_apple': 0}


def ing2(v):
    return {'item': v, 'data': DATA[v]} if v in DATA else ing(v)


if __name__ == '__main__':
    for fn in (newsstand, coffee_kiosk, ticket_office, lambda: emergency_desk('fire_station'), lambda: emergency_desk('ambulance_station'),
               lambda: emergency_desk('police_station'), police_cordon, fire_engine, ambulance, police_car):
        mm = fn()
        print('%-20s %d boxes' % (mm.name, len(mm.boxes)))
    cups()
    for n in RECIPES:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing2(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    bs = {'variants': {'facing=north': {'model': 'irextras:police_cordon'}, 'facing=east': {'model': 'irextras:police_cordon', 'y': 90},
                       'facing=south': {'model': 'irextras:police_cordon', 'y': 180}, 'facing=west': {'model': 'irextras:police_cordon', 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates/police_cordon.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/police_cordon'}, open(os.path.join(ASSETS, 'models/item/police_cordon.json'), 'w'), indent=1)
    for n in ('ems_fire_engine', 'ems_ambulance', 'ems_police_car'):
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for k, v in SPOTS.items():
        print(k, 'shelf spots (model px):', v)
