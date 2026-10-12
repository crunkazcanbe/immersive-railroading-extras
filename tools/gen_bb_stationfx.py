#!/usr/bin/env python3
"""Station accessibility, platform allocation and passenger facilities (her ChatGPT list #81, #82, #85): platform
allocation panel, lift landing, help point, accessible toilet, vending machine, retail kiosk (wide desk), platform lamp
column, shelter heater, escalator section, station facilities panel, and the two tactile paving blocks. Models face
north; controls sit at u = 16 - x on north surfaces. Live controls: see station/Tile*.java (ids must match).
Writes models, controls, blockstates, item models and recipes (lang lines are added by hand)."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, cupboard_shelf, X

GREEN, RED, AMBER, BLUE, WHITE = '30e060', 'ff3030', 'ffb020', '4080ff', 'f0f0e0'


def lamps(m, sf, items, u0, v, step=1.4, size=0.6, lab_dv=0.55):
    """a row of lamps with labels under them: items = (id, colour, label[, blink])"""
    for i, it in enumerate(items):
        cid, col, lab = it[:3]
        u = u0 + i * step
        m.ctl(sf, 'lamp', cid, u, v, w=size, h=size, col=col, label=lab, **({'blink': True} if len(it) > 3 and it[3] else {}))
        m.ctl(sf, 'label', '', u, v - lab_dv, w=0, h=0, col='e8e8e8', label=lab)


def pushes(m, sf, items, u0, v, step=1.4, size=0.9, lab_dv=0.75):
    for i, (cid, col, lab) in enumerate(items):
        u = u0 + i * step
        m.ctl(sf, 'push', cid, u, v, w=size, h=size, col=col, label=lab)
        m.ctl(sf, 'label', '', u, v - lab_dv, w=0, h=0, col='e8e8e8', label=lab)


def enclosure(m, x0, x1, y0, y1, z0, z1, col='ral7035', name='enclosure', door=True):
    """a steel control enclosure with a hinged front, bolts and a cable gland underneath"""
    m.box((x0, y0, z0 + 0.2), (x1, y1, z1), col, name)
    m.box((x0 + 0.15, y0 + 0.15, z0), (x1 - 0.15, y1 - 0.15, z0 + 0.2), 'paint:#2b2f33' if door else col, name + ' fascia')
    for x in (x0 + 0.4, x1 - 0.4):
        for y in (y0 + 0.4, y1 - 0.4):
            m.bolt(x, y, z0, 'north', 0.3)
    m.gland((x0 + x1) / 2, y0, (z0 + z1) / 2, 'down', r=0.3, length=0.6)


def mains_box(m, x, y, z, face='east'):
    """the mains isolator: a grey switch-disconnector box with a red/yellow rotary handle and an armoured cable"""
    g = m.group
    m.g('mains isolator')
    if face == 'east':
        m.box((x, y, z - 1.4), (x + 1.2, y + 2.6, z + 1.4), 'ral7035', 'isolator box', east='label:MAINS')
        m.box((x + 1.2, y + 1.0, z - 0.5), (x + 1.6, y + 1.8, z + 0.5), 'paint:#d02020', 'isolator handle')
        m.box((x + 1.2, y + 0.9, z - 0.8), (x + 1.3, y + 1.9, z + 0.8), 'paint:#f0c018', 'handle plate')
        m.cyl((x + 0.6, y - 0.6, z), 0.45, 0.6, 'y', 'metal:brass', 'cable gland')
        m.cyl((x + 0.6, max(-1.0, y - 2.6), z), 0.32, y - 0.6 - max(-1.0, y - 2.6), 'y', 'cable', 'armoured mains cable')
    else:
        m.box((x - 1.2, y, z - 1.4), (x, y + 2.6, z + 1.4), 'ral7035', 'isolator box', west='label:MAINS')
        m.box((x - 1.6, y + 1.0, z - 0.5), (x - 1.2, y + 1.8, z + 0.5), 'paint:#d02020', 'isolator handle')
        m.box((x - 1.3, y + 0.9, z - 0.8), (x - 1.2, y + 1.9, z + 0.8), 'paint:#f0c018', 'handle plate')
        m.cyl((x - 0.6, y - 0.6, z), 0.45, 0.6, 'y', 'metal:brass', 'cable gland')
        m.cyl((x - 0.6, max(-1.0, y - 2.6), z), 0.32, y - 0.6 - max(-1.0, y - 2.6), 'y', 'cable', 'armoured mains cable')
    m.g(g)


# ================================================================================================== platform panel
def platform_panel():
    m = Model('platform_panel')
    m.g('cabinet')
    m.box((0.2, 0, 9.4), (15.8, 1.0, 16), 'paint:#2a2d30', 'plinth')
    m.box((0.4, 1.0, 9.6), (15.6, 30.0, 16), 'ral7035', 'panel cabinet')
    m.box((0.2, 30.0, 9.4), (15.8, 30.6, 16), 'ral7035', 'roof')
    m.box((0.6, 1.2, 9.4), (15.4, 30.0, 9.6), 'paint:#1e2226', 'mimic fascia')
    m.box((1.0, 27.0, 9.3), (15.0, 29.4, 9.4), 'plate', 'title plate', north='plate:PLATFORM ALLOCATION')
    for x in (0.9, 15.1):
        for y in (1.6, 29.6):
            m.bolt(x, y, 9.4, 'north', 0.35)
    # track diagram strip: six platform roads with the throat
    m.g('track diagram')
    for i in range(6):
        y = 25.2 - i * 3.2
        m.box((1.0, y - 1.2, 9.32), (15.0, y + 1.1, 9.4), 'paint:#14181c', 'row backing')
        m.box((10.0, y - 0.1, 9.28), (14.6, y + 0.1, 9.32), 'paint:#f0f0e0', 'platform road')
        m.box((9.0, y - 0.1, 9.28), (10.0, y + 0.1, 9.32), 'paint:#808080', 'throat')
    for z in (11.0, 13.0, 15.0):
        m.box((0.1, 4.0, z), (0.4, 26.0, z + 0.5), 'ral7035', 'side stiffener')
        m.box((15.6, 4.0, z), (15.9, 26.0, z + 0.5), 'ral7035', 'side stiffener')
    m.louvres(1.5, 6.0, 1.6, 3.6, 9.4, n=3)
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 1.0, 13.5, 'down', r=0.35)
    m.terminal_12v(15.6, 2.0, 12.8, 'east')
    f = m.surface('north', face=9.27, title='PLATFORMS')
    for i in range(6):
        v = 25.2 - i * 3.2
        n = i + 1
        m.ctl(f, 'lamp', 'p%ds' % n, 15.1, v, w=0.6, h=0.6, col=WHITE, label='')
        m.ctl(f, 'digits', 'p%d' % n, 9.0, v + 0.45, w=10.6, h=1.0, col='ffd040', label='', fmt='%s')
        m.ctl(f, 'zone', 'z%d' % n, 9.0, v, w=11.0, h=2.2, col='000000', label='')
        m.ctl(f, 'lamp', 'p%da' % n, 4.2, v - 0.55, w=0.55, h=0.55, col=AMBER, label='')
        m.ctl(f, 'lamp', 'p%do' % n, 5.2, v - 0.55, w=0.55, h=0.55, col=RED, label='')
        m.ctl(f, 'lamp', 'p%dc' % n, 6.2, v - 0.55, w=0.55, h=0.55, col='ff60ff', label='', blink=True)
        m.ctl(f, 'push', 'c%d' % n, 2.4, v, w=1.1, h=1.1, col='d02020', label='CLOSE P%d' % n)
        m.ctl(f, 'label', '', 1.4, v + 0.9, w=0, h=0, col='e8e8e8', label='P%d' % n)
    m.ctl(f, 'label', '', 4.2, 26.5, w=0, h=0, col='e8e8e8', label='ALLOC')
    m.ctl(f, 'label', '', 5.7, 26.5, w=0, h=0, col='e8e8e8', label='OCC CL')
    m.ctl(f, 'digits', 'selt', 8.0, 6.3, w=13.0, h=1.0, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'selector', 's_len', 2.2, 4.4, w=1.4, h=1.4, col='202020', label='LENGTH', pos=['40M', '80M', '120M', '160M', '240M', '320M'])
    m.ctl(f, 'selector', 's_dir', 4.6, 4.4, w=1.4, h=1.4, col='202020', label='DIRECTION', pos=['ANY', 'UP', 'DOWN'])
    m.ctl(f, 'selector', 's_conf', 7.0, 4.4, w=1.4, h=1.4, col='202020', label='CONFLICT', pos=['NONE', 'P1', 'P2', 'P3', 'P4', 'P5', 'P6'])
    m.ctl(f, 'key', 'k_auto', 9.4, 4.4, w=1.3, h=1.3, col='c0a040', label='MODE', pos=['AUTO', 'MANUAL'])
    pushes(m, f, [('stepf', '2050d0', 'STEP-FREE'), ('clear', 'f0d020', 'CLEAR'), ('test', 'f0f0f0', 'TEST')], 11.6, 4.4, step=1.6, size=1.0)
    lamps(m, f, [('pwr', GREEN, '12V'), ('autol', GREEN, 'AUTO')], 2.6, 2.2, step=2.0)
    m.g('hinges + fittings')
    for y in (4.0, 12.0, 20.0, 26.0):
        m.box((0.1, y, 9.6), (0.4, y + 1.2, 10.2), 'metal:steel', 'hinge')
        m.box((15.6, y, 9.6), (15.9, y + 1.2, 10.2), 'metal:steel', 'hinge')
    for x in (2.0, 14.0):
        m.box((x - 0.4, 30.6, 12.4), (x + 0.4, 31.6, 13.2), 'metal:steel', 'lifting eye')
    m.box((1.0, 30.6, 14.6), (15.0, 31.0, 15.8), 'galv', 'cable tray')
    for x in (2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0):
        m.box((x, 31.0, 14.8), (x + 0.6, 31.4, 15.6), 'cable:1d1d21', 'tray cable')
    m.box((1.2, 1.2, 15.9), (14.8, 2.0, 16.0), 'metal:copper', 'earth bar')
    for x in range(2, 15, 2):
        m.bolt(x, 1.6, 16.0, 'south', 0.3)
    m.box((0.6, 3.6, 9.32), (15.4, 3.75, 9.4), 'paint:#f0c018', 'control strip line')
    m.save(item_gui=0.45)


# ===================================================================================================== lift landing
def station_lift():
    m = Model('station_lift')
    m.g('shaft wall')
    m.box((0, 0, 9.4), (16, 32, 16), 'paint:#c9ccd0', 'shaft wall')
    m.box((0, 0, 9.0), (16, 0.4, 9.4), 'metal:alu', 'landing sill')
    m.g('door portal')
    m.box((2.0, 0.4, 8.8), (3.0, 24.0, 9.4), 'metal:steel', 'portal jamb')
    m.box((13.0, 0.4, 8.8), (14.0, 24.0, 9.4), 'metal:steel', 'portal jamb')
    m.box((2.0, 24.0, 8.8), (14.0, 25.2, 9.4), 'metal:steel', 'portal head')
    m.box((3.0, 0.4, 9.0), (8.0, 24.0, 9.3), 'metal:steel', 'landing door left')
    m.box((8.0, 0.4, 9.0), (13.0, 24.0, 9.3), 'metal:steel', 'landing door right')
    m.box((7.96, 0.4, 8.95), (8.04, 24.0, 9.0), 'paint:#202020', 'door meeting line')
    for x in (4.0, 12.0):
        m.box((x - 0.6, 10.0, 8.9), (x + 0.6, 16.0, 9.0), 'glass', 'vision panel')
    m.box((3.6, 0.4, 8.9), (12.4, 0.6, 9.0), 'paint:#f0c018', 'sill edge')
    m.box((4.0, 25.6, 9.2), (12.0, 28.0, 9.4), 'paint:#101418', 'indicator housing')
    m.box((2.4, 28.6, 9.2), (13.6, 30.4, 9.4), 'plate', 'lift sign', north='label:LIFT - STEP FREE')
    m.box((14.4, 26.0, 9.2), (15.6, 30.0, 9.4), 'paint:#1d4f9a', 'access symbol', north='label:WC')
    m.g('call station')
    m.box((14.3, 9.0, 9.0), (15.9, 20.0, 9.4), 'metal:steel', 'call station plate')
    m.box((0.2, 9.0, 9.0), (1.8, 20.0, 9.4), 'metal:steel', 'floor panel plate')
    m.box((0.4, 20.4, 9.1), (1.6, 21.2, 9.4), 'plate', 'floors plate', north='plate:FLOOR')
    for y in (2.0, 6.0, 18.0, 22.0):
        m.bolt(1.0, y, 9.4, 'north', 0.3)
        m.bolt(15.0, y, 9.4, 'north', 0.3)
    m.g('services')
    mains_box(m, 16.0, 2.0, 12.6, 'east')
    m.terminal_12v(16.0, 6.0, 12.6, 'east')
    f = m.surface('north', face=8.95, title='LIFT')
    m.ctl(f, 'digits', 'floor', 8.0, 26.8, w=2.2, h=1.6, col='ff8030', label='', fmt='%s')
    m.ctl(f, 'lamp', 'up', 10.6, 27.3, w=0.8, h=0.8, col=GREEN, label='UP')
    m.ctl(f, 'lamp', 'dn', 10.6, 26.2, w=0.8, h=0.8, col=AMBER, label='DOWN')
    m.ctl(f, 'lamp', 'here', 5.4, 26.8, w=0.8, h=0.8, col=WHITE, label='HERE')
    m.ctl(f, 'digits', 'line1', 8.0, 24.6, w=9.6, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'push', 'call', 0.9, 16.0, w=1.1, h=1.1, col='f0f0f0', label='CALL')
    m.ctl(f, 'label', '', 0.9, 15.1, w=0, h=0, col='1a1a1a', label='CALL')
    m.ctl(f, 'lamp', 'doors', 0.9, 13.6, w=0.6, h=0.6, col=GREEN, label='DOORS')
    m.ctl(f, 'lamp', 'oos', 0.9, 12.4, w=0.6, h=0.6, col=RED, label='OUT OF SVC', blink=True)
    m.ctl(f, 'push', 'alarm', 0.9, 10.4, w=0.9, h=0.9, col='f0d020', label='ALARM')
    for i in range(4):
        m.ctl(f, 'push', 'f%d' % (i + 1), 15.1, 18.6 - i * 1.7, w=1.0, h=1.0, col='e0e0e0', label=str(i + 1))
        m.ctl(f, 'label', '', 15.1, 18.0 - i * 1.7, w=0, h=0, col='1a1a1a', label=str(i + 1))
    m.ctl(f, 'key', 'k_oos', 15.1, 10.6, w=0.9, h=0.9, col='c0a040', label='SERVICE', pos=['IN SVC', 'OOS'])
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 3.6, 30.9, step=8.8, size=0.5, lab_dv=-0.6)
    m.ctl(f, 'push', 'test', 15.1, 22.0, w=0.6, h=0.6, col='f0f0f0', label='TEST')
    m.g('wall tiles')
    for y in range(1, 31, 3):
        m.box((0.0, y, 9.38), (2.0, y + 0.12, 9.4), 'paint:#a8acb0', 'tile joint')
        m.box((14.0, y, 9.38), (16.0, y + 0.12, 9.4), 'paint:#a8acb0', 'tile joint')
    m.g('fittings')
    m.cyl((14.8, 30.6, 9.0), 0.5, 0.4, 'z', 'glass', 'cctv dome')
    m.box((5.0, 30.6, 9.2), (11.0, 31.4, 9.4), 'paint:#f4f4f0', 'emergency light', north='label:EXIT')
    for x in (0.3, 15.7):
        m.box((x - 0.2, 4.0, 8.6), (x + 0.2, 4.4, 9.4), 'metal:steel', 'buffer rail bracket')
    m.box((0.1, 4.0, 8.4), (15.9, 4.6, 8.7), 'metal:steel', 'trolley buffer rail')
    m.box((14.4, 7.6, 9.1), (15.6, 8.6, 9.4), 'paint:#202020', 'braille plate')
    m.g('landing door detail')
    for x in (3.0, 8.0):
        m.box((x, 0.4, 8.85), (x + 5.0, 0.7, 9.0), 'metal:alu', 'door bottom guide shoe')
    m.box((2.6, 0.0, 8.4), (13.4, 0.4, 9.0), 'metal:alu', 'landing sill track')
    for x in range(3, 13):
        m.box((x + 0.3, 0.38, 8.5), (x + 0.5, 0.42, 8.9), 'paint:#303030', 'sill groove')
    m.box((7.7, 1.0, 8.9), (7.95, 23.0, 9.0), 'rubber', 'nudging edge left')
    m.box((8.05, 1.0, 8.9), (8.3, 23.0, 9.0), 'rubber', 'nudging edge right')
    m.box((2.6, 24.0, 8.95), (13.4, 24.4, 9.4), 'metal:steel', 'door track hanger cover')
    for x in (3.4, 6.6, 9.4, 12.6):
        m.bolt(x, 24.2, 8.95, 'north', 0.25)
    m.g('architrave')
    m.box((1.6, 0.4, 8.6), (2.0, 25.6, 9.4), 'metal:steel', 'architrave left')
    m.box((14.0, 0.4, 8.6), (14.4, 25.6, 9.4), 'metal:steel', 'architrave right')
    m.box((1.6, 25.2, 8.6), (14.4, 25.6, 9.4), 'metal:steel', 'architrave head')
    m.g('indicator housing')
    m.box((3.8, 25.6, 9.1), (4.0, 28.2, 9.4), 'metal:steel', 'indicator bezel')
    m.box((12.0, 25.6, 9.1), (12.2, 28.2, 9.4), 'metal:steel', 'indicator bezel')
    m.box((3.8, 28.0, 9.1), (12.2, 28.2, 9.4), 'metal:steel', 'indicator bezel')
    m.box((10.2, 27.1, 9.15), (11.0, 27.5, 9.2), 'paint:#204020', 'hall lantern arrow up')
    m.box((10.2, 26.0, 9.15), (11.0, 26.4, 9.2), 'paint:#403010', 'hall lantern arrow down')
    m.g('call station surround')
    m.box((14.2, 8.8, 8.95), (16.0, 9.0, 9.4), 'metal:alu', 'surround')
    m.box((14.2, 20.0, 8.95), (16.0, 20.2, 9.4), 'metal:alu', 'surround')
    m.box((0.0, 8.8, 8.95), (2.0, 9.0, 9.4), 'metal:alu', 'surround')
    m.box((0.0, 20.0, 8.95), (2.0, 20.2, 9.4), 'metal:alu', 'surround')
    for i in range(4):
        m.box((15.6, 18.3 - i * 1.7, 9.0), (15.85, 18.9 - i * 1.7, 9.1), 'paint:#202020', 'braille dot plate')
    m.box((0.3, 21.6, 9.1), (1.7, 23.2, 9.4), 'paint:#d02020', 'fire service key plate', north='label:FIRE')
    m.box((14.3, 21.6, 9.1), (15.9, 23.0, 9.4), 'paint:#1d4f9a', 'induction loop sign', north='label:LOOP')
    m.g('shaft cladding')
    for x in (0.0, 4.0, 8.0, 12.0):
        m.box((x, 25.6, 9.38), (x + 0.12, 32.0, 9.4), 'paint:#a8acb0', 'cladding joint')
    m.box((0.0, 31.6, 9.2), (16.0, 32.0, 9.4), 'metal:steel', 'cladding cap')
    m.save(item_gui=0.45)


# ======================================================================================================= help point
def help_point():
    m = Model('help_point')
    m.g('base')
    m.box((4.4, 0, 4.4), (11.6, 0.8, 11.6), 'concrete', 'foundation')
    for (x, z) in ((5.0, 5.0), (11.0, 5.0), (5.0, 11.0), (11.0, 11.0)):
        m.bolt(x, 0.8, z, 'up', 0.4)
    m.g('pillar')
    m.box((5.0, 0.8, 5.0), (11.0, 28.0, 11.0), 'paint:#1d4f9a', 'pillar')
    m.box((4.8, 28.0, 4.8), (11.2, 28.6, 11.2), 'paint:#143a72', 'cap')
    m.box((5.4, 22.0, 4.9), (10.6, 27.4, 5.0), 'paint:#f4f4f0', 'sign panel', north='label:HELP|POINT')
    m.box((5.4, 16.4, 4.9), (10.6, 21.6, 5.0), 'paint:#101418', 'faceplate')
    m.box((5.4, 6.0, 4.9), (10.6, 16.0, 5.0), 'metal:steel', 'stainless control plate')
    for x in (6.0, 7.0, 8.0, 9.0, 10.0):
        for y in (13.0, 13.6, 14.2):
            m.box((x - 0.15, y, 4.85), (x + 0.15, y + 0.3, 4.9), 'paint:#101010', 'speaker hole')
    m.box((6.2, 10.8, 4.8), (9.8, 11.6, 4.9), 'paint:#f0c018', 'loop symbol plate')
    m.box((5.4, 3.0, 4.9), (10.6, 5.6, 5.0), 'paint:#f0c018', 'gap sign', north='label:MIND THE GAP')
    m.cyl((8.0, 26.2, 4.6), 0.5, 0.4, 'z', 'glass', 'camera dome')
    m.cyl((8.0, 28.6, 8.0), 1.2, 1.4, 'y', 'glass', 'beacon')
    m.terminal_12v(11.0, 1.4, 8.0, 'east')
    f = m.surface('north', face=4.85, title='HELP POINT')
    m.ctl(f, 'digits', 'line1', 8.0, 20.4, w=4.8, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', 8.0, 19.2, w=4.8, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'push', 'help', 9.4, 9.6, w=1.4, h=1.4, col='2fa84a', label='HELP')
    m.ctl(f, 'label', '', 9.4, 8.6, w=0, h=0, col='1a1a1a', label='HELP')
    m.ctl(f, 'push', 'info', 6.6, 9.6, w=1.4, h=1.4, col='2050d0', label='INFO')
    m.ctl(f, 'label', '', 6.6, 8.6, w=0, h=0, col='1a1a1a', label='INFO')
    m.ctl(f, 'push', 'cancel', 8.0, 7.4, w=0.8, h=0.8, col='202020', label='CANCEL')
    m.ctl(f, 'lamp', 'call', 8.0, 17.8, w=0.7, h=0.7, col=GREEN, label='CALLING', blink=True)
    m.ctl(f, 'lamp', 'loop', 6.2, 17.8, w=0.5, h=0.5, col=BLUE, label='LOOP')
    m.ctl(f, 'lamp', 'pwr', 9.8, 17.8, w=0.5, h=0.5, col=GREEN, label='12V')
    m.ctl(f, 'lamp', 'gap', 8.0, 2.4, w=3.8, h=0.6, col='ffd020', label='MIND THE GAP', blink=True)
    m.ctl(f, 'key', 'k_gap', 9.9, 6.6, w=0.6, h=0.6, col='c0a040', label='GAP', pos=['OFF', 'ON'])
    m.ctl(f, 'push', 'test', 6.1, 6.6, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    m.g('detail')
    for x in (5.0, 10.8):
        for y in range(2, 28, 3):
            m.box((x, y, 5.0), (x + 0.2, y + 2.0, 11.0), 'paint:#143a72', 'pillar flute')
    m.box((5.0, 21.6, 4.4), (11.0, 22.0, 5.0), 'paint:#143a72', 'rain hood')
    m.box((6.0, 17.0, 4.85), (10.0, 17.2, 4.9), 'paint:#303438', 'lamp bezel')
    for y in (6.4, 15.6):
        for x in (5.8, 10.2):
            m.bolt(x, y, 4.9, 'north', 0.3)
    m.g('base plinth')
    m.box((4.8, 0.8, 4.8), (11.2, 1.6, 11.2), 'paint:#143a72', 'base plinth')
    m.g('speaker grille slots')
    for y in (12.6, 14.8):
        for x in (6.0, 7.0, 8.0, 9.0, 10.0):
            m.box((x - 0.15, y, 4.85), (x + 0.15, y + 0.3, 4.9), 'paint:#101010', 'speaker hole')
    m.g('camera + beacon')
    m.box((7.4, 26.8, 4.6), (8.6, 27.2, 5.0), 'paint:#202020', 'camera mount')
    m.box((6.8, 28.6, 6.8), (9.2, 29.0, 9.2), 'paint:#143a72', 'beacon base')
    m.box((7.6, 30.2, 7.6), (8.4, 30.6, 8.4), 'paint:#143a72', 'beacon cap')
    m.g('loop logo + rear')
    m.box((6.4, 11.0, 4.75), (7.0, 11.4, 4.8), 'paint:#1d4f9a', 'loop ear logo')
    m.box((6.0, 2.0, 10.9), (10.0, 14.0, 11.1), 'paint:#143a72', 'rear service door')
    m.box((9.2, 7.0, 11.1), (9.6, 8.0, 11.3), 'metal:brass', 'service lock')
    m.g('fixings')
    for (x, y) in ((5.6, 22.4), (10.4, 22.4), (5.6, 27.0), (10.4, 27.0)):
        m.bolt(x, y, 4.9, 'north', 0.25)
    m.box((5.2, 5.8, 4.88), (10.8, 6.0, 4.95), 'metal:alu', 'plate trim')
    m.box((5.2, 16.0, 4.88), (10.8, 16.2, 4.95), 'metal:alu', 'plate trim')
    m.save(item_gui=0.5)


# ============================================================================================ accessible toilet
def station_toilet():
    m = Model('station_toilet')
    m.g('cubicle')
    m.box((0, 0, 2.6), (16, 30, 16), 'paint:#e8e6e0', 'cubicle wall')
    m.box((0, 30, 2.4), (16, 30.6, 16), 'paint:#b8bcc0', 'roof')
    m.box((0, 0, 2.4), (16, 0.4, 2.6), 'paint:#3a3a3a', 'kick strip')
    m.g('door')
    m.box((3.0, 0.4, 2.2), (13.0, 22.0, 2.6), 'paint:#1d4f9a', 'powered door')
    m.box((2.6, 0.4, 2.1), (3.0, 22.6, 2.6), 'metal:steel', 'door frame')
    m.box((13.0, 0.4, 2.1), (13.4, 22.6, 2.6), 'metal:steel', 'door frame')
    m.box((2.6, 22.0, 2.1), (13.4, 22.6, 2.6), 'metal:steel', 'door head')
    m.box((11.4, 8.0, 1.8), (11.8, 14.0, 2.2), 'metal:steel', 'pull handle')
    m.box((4.0, 23.0, 2.3), (12.0, 26.0, 2.4), 'plate', 'sign', north='label:ACCESSIBLE|TOILET')
    m.box((0.4, 23.0, 2.3), (2.6, 26.0, 2.4), 'paint:#1d4f9a', 'symbol', north='label:WC')
    m.box((3.6, 3.0, 2.15), (6.0, 18.0, 2.2), 'metal:steel', 'kick plate + grab rail')
    m.g('services')
    m.terminal_12v(15.0, 1.4, 16.0, 'south')
    m.gland(8.0, 30.6, 9.0, 'up', r=0.4, length=0.8)
    f = m.surface('north', face=2.05, title='TOILET')
    m.ctl(f, 'lamp', 'vacant', 14.6, 20.6, w=0.8, h=0.8, col=GREEN, label='VACANT')
    m.ctl(f, 'lamp', 'closed', 14.6, 19.2, w=0.8, h=0.8, col=RED, label='CLOSED')
    m.ctl(f, 'gauge', 'cleang', 14.6, 16.6, w=2.2, h=2.2, col='f2f0e6', label='CLEAN %', max=100)
    m.ctl(f, 'digits', 'uses', 14.6, 14.4, w=2.4, h=0.8, col='ffd040', label='', fmt='%d')
    m.ctl(f, 'push', 'flush', 14.0, 12.4, w=0.9, h=0.9, col='f0f0f0', label='FLUSH')
    m.ctl(f, 'push', 'wash', 15.2, 12.4, w=0.9, h=0.9, col='4080ff', label='WASH')
    m.ctl(f, 'push', 'cord', 14.6, 10.4, w=1.1, h=1.1, col='d02020', label='EMERGENCY')
    m.ctl(f, 'label', '', 14.6, 9.5, w=0, h=0, col='1a1a1a', label='CORD')
    m.ctl(f, 'push', 'cleaned', 1.4, 12.4, w=1.1, h=1.1, col='2fa84a', label='CLEANED')
    m.ctl(f, 'label', '', 1.4, 11.5, w=0, h=0, col='1a1a1a', label='CLEANED')
    m.ctl(f, 'key', 'k_lock', 1.4, 9.8, w=0.9, h=0.9, col='c0a040', label='DOOR', pos=['OPEN', 'LOCKED'])
    m.ctl(f, 'digits', 'logt', 8.0, 28.0, w=14.0, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'lamp', 'pwr', 1.4, 15.0, w=0.5, h=0.5, col=GREEN, label='12V')
    m.ctl(f, 'push', 'test', 1.4, 7.6, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    m.g('cladding')
    for y in (4.0, 8.0, 12.0, 16.0, 20.0, 24.0, 28.0):
        m.box((0, y, 2.5), (2.6, y + 0.15, 2.6), 'paint:#c8c6c0', 'panel seam')
        m.box((13.4, y, 2.5), (16, y + 0.15, 2.6), 'paint:#c8c6c0', 'panel seam')
    for x in (0.0, 15.6):
        m.box((x, 0, 2.4), (x + 0.4, 30, 2.6), 'metal:alu', 'corner trim')
    m.box((3.6, 18.4, 2.1), (6.0, 19.0, 2.2), 'metal:steel', 'grab rail')
    m.louvres(4.0, 12.0, 27.0, 29.6, 2.6, n=3)
    m.box((13.6, 22.8, 2.2), (15.6, 23.4, 2.4), 'plate', 'induction loop', north='label:LOOP')
    m.box((13.6, 6.6, 2.2), (15.6, 7.2, 2.4), 'plate', 'cord plate', north='label:PULL CORD')
    m.g('door power assist')
    m.box((3.4, 21.4, 1.9), (9.0, 21.9, 2.2), 'metal:alu', 'door operator arm')
    m.box((8.6, 21.0, 2.0), (12.6, 21.9, 2.4), 'paint:#3a3e44', 'door operator')
    m.box((3.2, 21.3, 2.0), (3.8, 22.0, 2.2), 'metal:steel', 'arm pivot')
    m.box((12.6, 1.0, 1.9), (13.0, 21.0, 2.2), 'rubber', 'finger guard')
    m.g('fittings beside the door')
    m.box((0.3, 6.0, 1.8), (2.3, 9.0, 2.4), 'metal:steel', 'hand dryer', north='label:DRY')
    m.box((0.6, 6.2, 1.75), (2.0, 6.6, 1.8), 'paint:#202020', 'dryer outlet')
    m.box((0.4, 14.6, 2.0), (2.4, 19.6, 2.2), 'glass', 'mirror')
    m.box((0.2, 14.4, 2.15), (2.6, 14.6, 2.4), 'metal:steel', 'mirror frame')
    m.box((0.2, 19.6, 2.15), (2.6, 19.8, 2.4), 'metal:steel', 'mirror frame')
    m.box((0.2, 11.6, 1.2), (2.6, 12.6, 2.4), 'paint:#f4f4f0', 'basin')
    m.box((1.2, 12.6, 1.8), (1.6, 13.6, 2.2), 'metal:steel', 'tap')
    m.box((0.4, 10.6, 1.8), (2.4, 11.6, 2.4), 'paint:#d8d8d0', 'basin pedestal')
    m.box((13.6, 1.0, 1.6), (15.6, 4.4, 2.4), 'metal:steel', 'waste bin')
    m.box((13.5, 4.4, 1.5), (15.7, 4.7, 2.4), 'paint:#303030', 'bin lid')
    m.box((13.6, 16.8, 1.4), (15.6, 17.2, 2.4), 'paint:#f4f0e8', 'baby change shelf', north='label:BABY')
    m.g('grab rails')
    m.box((0.2, 8.4, 1.4), (0.5, 10.4, 1.7), 'metal:steel', 'fold-down rail stile')
    m.box((0.2, 10.2, 0.4), (0.5, 10.5, 1.7), 'metal:steel', 'fold-down grab rail')
    m.box((13.5, 9.0, 2.0), (13.8, 15.0, 2.3), 'metal:steel', 'fixed vertical grab rail')
    m.box((13.5, 9.0, 2.0), (14.2, 9.3, 2.3), 'metal:steel', 'rail bracket')
    m.box((13.5, 14.7, 2.0), (14.2, 15.0, 2.3), 'metal:steel', 'rail bracket')
    m.g('pull cord')
    m.box((13.6, 29.0, 2.3), (15.8, 29.3, 2.5), 'paint:#d02020', 'cord run')
    for x in (14.0, 15.4):
        m.box((x, 28.8, 2.3), (x + 0.2, 29.0, 2.5), 'metal:steel', 'cord eye')
    m.box((14.55, 11.0, 2.0), (14.65, 29.0, 2.1), 'paint:#d02020', 'pull cord drop')
    m.box((14.4, 2.2, 1.9), (14.8, 2.8, 2.1), 'paint:#d02020', 'cord bangle')
    m.g('signage')
    m.box((3.8, 22.7, 2.25), (12.2, 23.0, 2.35), 'metal:alu', 'sign frame')
    m.box((4.2, 18.6, 2.15), (6.6, 19.6, 2.2), 'plate', 'occupied indicator', north='label:VACANT')
    m.g('services + finish')
    for x in (4.0, 8.0, 12.0):
        m.box((x, 30.6, 6.0), (x + 0.4, 31.0, 14.0), 'galv', 'roof vent duct')
    m.box((1.0, 30.6, 12.0), (3.0, 31.6, 14.0), 'paint:#808488', 'water heater')
    m.box((3.2, 0.4, 2.0), (12.8, 0.6, 2.2), 'rubber', 'door bottom seal')
    m.box((6.0, 20.2, 2.15), (10.0, 20.8, 2.2), 'plate', 'wash hands sign', north='label:WASH HANDS')
    for y in (2.0, 11.0, 20.0):
        m.box((12.9, y, 2.15), (13.3, y + 1.0, 2.3), 'metal:steel', 'door hinge')
    m.box((11.2, 14.4, 2.1), (12.0, 15.2, 2.2), 'metal:steel', 'lock turn')
    m.box((0.2, 0, 2.2), (2.6, 0.4, 2.6), 'paint:#3a3a3a', 'kick strip')
    m.box((13.4, 0, 2.2), (15.8, 0.4, 2.6), 'paint:#3a3a3a', 'kick strip')
    m.box((13.6, 25.0, 2.2), (15.6, 26.6, 2.4), 'paint:#2fa84a', 'exit sign', north='label:EXIT')
    m.save(item_gui=0.45)


# ============================================================================================== vending machine
def vending_machine():
    m = Model('vending_machine')
    m.g('cabinet')
    m.box((1.0, 0, 4.4), (15.0, 28.0, 15.0), 'paint:#8a1c2c', 'cabinet')
    m.box((0.8, 28.0, 4.2), (15.2, 28.6, 15.2), 'paint:#5e121e', 'top')
    m.box((1.2, 0.4, 4.2), (10.8, 26.0, 4.4), 'paint:#202226', 'window frame')
    m.box((1.6, 6.6, 4.1), (10.4, 25.6, 4.2), 'glass', 'display window')
    m.g('product spirals')
    cols = ('#e03030', '#f0c018', '#30a0e0', '#40c060', '#c060e0')
    for r in range(5):
        y = 8.0 + r * 3.6
        m.box((1.8, y - 0.3, 4.6), (10.2, y, 8.0), 'metal:steel', 'shelf')
        for c in range(4):
            x = 2.6 + c * 2.1
            m.box((x - 0.8, y, 5.0), (x + 0.8, y + 1.8, 6.4), 'paint:' + cols[(r + c) % len(cols)], 'product')
            m.cyl((x, y + 0.9, 6.4), 0.9, 1.2, 'z', 'metal:steel', 'spiral')
    m.box((1.6, 1.0, 4.0), (10.4, 5.4, 4.2), 'paint:#101010', 'delivery flap')
    m.box((11.0, 1.0, 4.2), (14.8, 26.0, 4.4), 'paint:#2b2f33', 'keypad column')
    m.box((11.4, 22.0, 4.1), (14.4, 25.4, 4.2), 'plate', 'brand plate', north='label:SNACKS|DRINKS')
    m.box((12.2, 9.0, 4.0), (13.8, 11.0, 4.2), 'metal:steel', 'coin slot')
    m.box((12.0, 3.0, 4.0), (14.0, 5.0, 4.2), 'metal:steel', 'change cup')
    m.louvres(11.4, 14.4, 26.4, 27.8, 4.4, n=3)
    m.terminal_12v(15.0, 1.4, 10.0, 'east')
    f = m.surface('north', face=4.0, title='VENDING')
    m.ctl(f, 'zone', 'stock', X(6.0), 14.7, w=8.6, h=16.2, col='000000', label='')
    m.ctl(f, 'digits', 'price', X(12.9), 20.4, w=3.0, h=1.0, col='ff3030', label='', fmt='%s')
    m.ctl(f, 'selector', 's_price', X(12.9), 18.0, w=1.3, h=1.3, col='202020', label='PRICE', pos=['1.00', '2.50', '5.00'])
    m.ctl(f, 'push', 'buy', X(12.9), 15.0, w=1.6, h=1.6, col='2fa84a', label='BUY')
    m.ctl(f, 'label', '', X(12.9), 13.8, w=0, h=0, col='e8e8e8', label='BUY')
    m.ctl(f, 'digits', 'nstock', X(12.2), 12.4, w=1.4, h=0.8, col='7cf29a', label='', fmt='%d')
    m.ctl(f, 'digits', 'nsold', X(13.8), 12.4, w=1.4, h=0.8, col='ffd040', label='', fmt='%d')
    lamps(m, f, [('instock', GREEN, 'OK'), ('empty', RED, 'SOLD OUT', True), ('pwr', GREEN, '12V')], X(14.0), 7.6, step=1.0, size=0.5)
    m.ctl(f, 'push', 'test', X(14.0), 6.0, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    # station shops (#5): spiral selection display + buttons, coin return, chiller on mains
    m.box((1.6, 22.8, 4.0), (10.4, 25.4, 4.1), 'paint:#070a08', 'selection display')
    m.ctl(f, 'digits', 'sel', X(6.0), 24.6, w=8.2, h=1.0, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'slotn', X(6.0), 23.4, w=8.2, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'push', 'seldn', X(12.1), 16.9, w=0.7, h=0.7, col='e0e0e0', label='<')
    m.ctl(f, 'push', 'selup', X(13.7), 16.9, w=0.7, h=0.7, col='e0e0e0', label='>')
    m.ctl(f, 'push', 'coinret', X(12.4), 6.0, w=0.5, h=0.5, col='c0c0c0', label='COIN RET')
    lamps(m, f, [('chill', BLUE, 'CHILLED'), ('disp', GREEN, 'DISPLAY'), ('mains', GREEN, 'MAINS')], X(2.6), 6.1, step=-2.6, size=0.45, lab_dv=0.5)
    mains_box(m, 15.0, 4.6, 12.4, 'east')
    m.save(item_gui=0.45)


# ================================================================================================= retail kiosk
SPOTS = {}


def retail_kiosk():
    m = Model('retail_kiosk')
    back_wall(m, panel='#2a4a3a', plaster='#f0ece0', top=30)
    m.g('fascia')
    m.box((-14, 26.0, 14.6), (30, 30.0, 15.4), 'paint:#1f3a2c', 'fascia board')
    m.box((-6, 26.6, 14.5), (22, 29.4, 14.6), 'plate', 'fascia lettering', north='label:NEWS - COFFEE - SNACKS')
    m.g('coffee machine side')
    m.box((-13.4, 0, 9.0), (-2.6, 8.6, 15.2), 'paint:#3a3a3a', 'back counter')
    m.box((-13.6, 8.6, 8.8), (-2.4, 9.0, 15.4), 'metal:steel', 'back counter top')
    m.box((-12.6, 9.0, 11.0), (-7.4, 15.0, 15.0), 'metal:steel', 'espresso machine')
    m.box((-12.4, 13.0, 10.9), (-7.6, 14.6, 11.0), 'paint:#101010', 'machine display')
    for x in (-11.6, -9.0):
        m.box((x - 0.4, 10.6, 10.4), (x + 0.4, 11.4, 11.0), 'paint:#202020', 'group head')
        m.cyl((x, 9.0, 10.6), 0.4, 0.8, 'y', 'paint:#f4f0e8', 'cup')
    m.box((-6.6, 9.0, 11.0), (-3.0, 17.0, 15.0), 'glass', 'drinks fridge')
    for y in (11.0, 13.0, 15.0):
        m.box((-6.4, y, 11.2), (-3.2, y + 0.2, 14.8), 'metal:steel', 'fridge shelf')
        for x in (-6.0, -5.0, -4.0):
            m.cyl((x, y + 0.2, 12.4), 0.35, 1.4, 'y', 'paint:#e03030' if x < -5 else 'paint:#30a0e0', 'bottle')
    SPOTS['retail_kiosk'] = cupboard_shelf(m, 18.4, 29.4, ['k1', 'k2'], 6, title='DISPLAY', body='#2a4a3a', door='#3a6050')
    m.g('counter')
    m.box((0, 0, 3.6), (16, 8.6, 9.0), 'paint:#2a4a3a', 'counter body')
    m.box((-0.2, 8.6, 3.4), (16.2, 9.0, 9.2), 'wood', 'counter top')
    m.box((0.6, 0.4, 3.4), (15.4, 8.2, 3.6), 'paint:#3a6050', 'counter front')
    m.box((2.0, 4.0, 3.3), (14.0, 6.6, 3.4), 'plate', 'counter sign', north='label:PAY HERE')
    m.box((10.4, 5.4, 9.0), (15.4, 7.8, 12.6), 'paint:#4a3020', 'till drawer housing')
    m.box((10.6, 9.0, 7.2), (14.6, 10.6, 8.8), 'paint:#202226', 'till')
    m.box((11.0, 10.6, 7.8), (14.2, 12.4, 8.0), 'paint:#03140a', 'till screen')
    m.box((1.0, 9.0, 4.4), (8.0, 9.6, 8.4), 'paint:#f4f0d8', 'newspapers')
    m.box((1.2, 9.6, 4.6), (7.8, 9.8, 8.2), 'paint:#d8d4c0', 'newspapers')
    m.g('shutter')
    for x in (-0.4, 16.0):
        m.box((x, 0, 3.0), (x + 0.4, 26.0, 3.6), 'metal:steel', 'shutter guide')
    m.box((-0.4, 22.0, 2.8), (16.4, 26.0, 3.6), 'metal:steel', 'shutter box')
    for y in (22.4, 23.2, 24.0, 24.8):
        m.box((0, y, 2.7), (16, y + 0.2, 2.8), 'paint:#808488', 'shutter slat')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    f = m.surface('north', face=2.65, title='KIOSK')
    m.ctl(f, 'digits', 'sign', 8.0, 21.0, w=6.0, h=1.4, col='30e060', label='', fmt='%s')
    m.ctl(f, 'digits', 'rent', 8.0, 19.4, w=6.0, h=0.8, col='ffd040', label='', fmt='%s')
    top = m.surface('up', face=16 - 9.0, title='COUNTER')
    m.ctl(top, 'push', 'bell', X(3.0), 8.4, w=1.0, h=1.0, col='c0a040', label='BELL')
    m.ctl(top, 'key', 'k_open', X(9.2), 8.4, w=1.0, h=1.0, col='c0a040', label='SHUTTER', pos=['CLOSED', 'OPEN'])
    lamps(m, top, [('openl', GREEN, 'OPEN'), ('stockl', AMBER, 'STOCK'), ('pwr', GREEN, '12V')], X(6.6), 6.4, step=-1.2, size=0.5)
    till = m.surface('north', face=8.95, title='TILL')
    m.ctl(till, 'zone', 'k3', X(12.9), 6.6, w=4.6, h=2.2, col='000000', label='')
    m.save(item_gui=0.28)


# ================================================================================================= platform lamp
def platform_lamp():
    m = Model('platform_lamp')
    m.g('base')
    m.box((5.0, 0, 5.0), (11.0, 7.0, 11.0), 'paint:#1f3a2c', 'base box')
    m.box((4.6, 0, 4.6), (11.4, 0.6, 11.4), 'paint:#14281e', 'plinth')
    m.box((5.4, 1.0, 4.9), (10.6, 6.6, 5.0), 'paint:#2a4a3a', 'access door')
    m.box((9.6, 3.4, 4.7), (10.0, 4.4, 4.9), 'metal:brass', 'door lock')
    m.g('column')
    m.cyl((8.0, 7.0, 8.0), 1.2, 1.0, 'y', 'paint:#14281e', 'collar')
    m.cyl((8.0, 8.0, 8.0), 0.8, 18.0, 'y', 'paint:#1f3a2c', 'column')
    for y in (12.0, 18.0):
        m.cyl((8.0, y, 8.0), 1.0, 0.4, 'y', 'paint:#14281e', 'band')
    m.box((7.6, 22.0, 2.0), (8.4, 22.6, 8.0), 'paint:#1f3a2c', 'bracket arm')
    m.box((7.7, 20.0, 7.4), (8.3, 22.0, 8.0), 'paint:#1f3a2c', 'bracket stay', rot={'axis': 'x', 'angle': -45, 'origin': [8.0, 21.0, 7.7]})
    m.g('lantern')
    m.box((5.6, 25.8, 5.6), (10.4, 26.4, 10.4), 'paint:#14281e', 'lantern roof')
    m.box((6.6, 26.4, 6.6), (9.4, 27.2, 9.4), 'paint:#14281e', 'lantern crown')
    m.box((6.2, 22.4, 6.2), (9.8, 25.8, 9.8), 'glass', 'lantern glass')
    for (x, z) in ((6.0, 6.0), (9.6, 6.0), (6.0, 9.6), (9.6, 9.6)):
        m.box((x, 22.4, z), (x + 0.4, 25.8, z + 0.4), 'paint:#14281e', 'lantern post')
    m.box((6.6, 22.0, 6.6), (9.4, 22.4, 9.4), 'paint:#14281e', 'lantern base')
    m.cyl((8.0, 28.0, 8.0), 0.4, 0.8, 'y', 'paint:#202020', 'photocell')
    m.box((5.2, 14.0, 6.8), (5.4, 18.0, 9.2), 'paint:#f4f0e0', 'platform number plate', west='label:P1')
    mains_box(m, 11.0, 1.0, 8.0, 'east')
    m.terminal_12v(5.0, 1.0, 8.0, 'west')
    f = m.surface('north', face=4.85, title='LAMP')
    m.ctl(f, 'selector', 's_mode', 8.0, 4.8, w=1.2, h=1.2, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('litl', WHITE, 'LIT'), ('cell', AMBER, 'DARK')], 6.2, 2.8, step=1.2, size=0.45, lab_dv=0.45)
    m.ctl(f, 'push', 'test', 10.0, 4.8, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    top = m.surface('up', face=16 - 25.85, title='LANTERN')
    m.ctl(top, 'lamp', 'litl', 8.0, 8.0, w=3.0, h=3.0, col='fff0b0', label='')
    m.g('detail')
    for k in range(8):
        a = k * 45
        import math
        x, z = 8.0 + math.cos(math.radians(a)) * 0.8, 8.0 + math.sin(math.radians(a)) * 0.8
        m.box((x - 0.12, 8.0, z - 0.12), (x + 0.12, 26.0 - 4.0, z + 0.12), 'paint:#14281e', 'column flute')
    for (x, z) in ((5.0, 5.0), (11.0, 5.0), (5.0, 11.0), (11.0, 11.0)):
        m.bolt(x, 0.6, z, 'up', 0.4)
    m.box((7.4, 21.0, 2.0), (8.6, 22.0, 2.4), 'paint:#1f3a2c', 'arm finial')
    m.box((4.9, 6.8, 4.9), (11.1, 7.0, 11.1), 'paint:#14281e', 'base cap')
    m.g('base door + joints')
    for y in (1.4, 6.0):
        m.box((5.5, y, 4.85), (5.9, y + 0.4, 4.95), 'metal:steel', 'door hinge')
    m.box((6.0, 5.6, 4.85), (10.0, 6.2, 4.95), 'plate', 'column number', north='plate:PL 07')
    for y in (8.0, 20.0):
        m.cyl((8.0, y, 8.0), 1.05, 0.5, 'y', 'paint:#14281e', 'column joint')
    m.cyl((8.0, 25.4, 8.0), 0.9, 0.6, 'y', 'paint:#14281e', 'lantern spigot')
    m.g('lantern glazing')
    for x in (6.2, 9.8):
        for y in (23.2, 24.2, 25.0):
            m.box((x - 0.05, y, 6.2), (x + 0.05, y + 0.1, 9.8), 'paint:#14281e', 'glazing bar')
    for z in (6.2, 9.8):
        for y in (23.2, 24.2, 25.0):
            m.box((6.2, y, z - 0.05), (9.8, y + 0.1, z + 0.05), 'paint:#14281e', 'glazing bar')
    for (x, z) in ((6.6, 6.6), (9.0, 6.6), (6.6, 9.0), (9.0, 9.0)):
        m.box((x, 26.4, z), (x + 0.4, 26.8, z + 0.4), 'paint:#14281e', 'roof rib')
    m.box((7.4, 22.6, 7.4), (8.6, 24.6, 8.6), 'paint:#fff0b0', 'lamp')
    m.box((7.6, 27.2, 7.6), (8.4, 28.0, 8.4), 'paint:#14281e', 'photocell stem')
    m.g('banner arm')
    m.box((7.6, 17.0, 8.0), (8.4, 17.6, 14.0), 'paint:#1f3a2c', 'banner arm')
    m.box((7.8, 12.0, 13.4), (8.2, 17.0, 13.8), 'paint:#1d4f9a', 'banner', west='label:PRIDE', east='label:PRIDE')
    m.box((7.7, 11.8, 13.3), (8.3, 12.0, 13.9), 'paint:#1f3a2c', 'banner bottom rod')
    m.g('cable entry')
    m.cyl((8.0, -0.6, 8.0), 0.5, 0.6, 'y', 'metal:brass', 'cable entry gland')
    m.save(item_gui=0.5)


# ================================================================================================ shelter heater
def platform_heater():
    m = Model('platform_heater')
    m.g('wall plate')
    m.box((2.0, 6.0, 15.4), (14.0, 16.0, 16.0), 'paint:#6a6e72', 'mounting plate')
    for x in (2.6, 13.4):
        for y in (6.6, 15.4):
            m.bolt(x, y, 15.4, 'north', 0.35)
    m.g('radiant heater')
    for x in (3.0, 12.4):
        m.box((x, 13.0, 9.0), (x + 0.6, 14.0, 15.4), 'metal:steel', 'heater arm')
    m.box((2.4, 13.6, 8.6), (13.6, 15.6, 11.6), 'metal:steel', 'reflector housing')
    m.box((2.8, 13.4, 8.8), (13.2, 13.6, 11.4), 'paint:#c03010', 'quartz element glow')
    for x in (3.4, 5.4, 7.4, 9.4, 11.4):
        m.box((x, 13.2, 8.7), (x + 0.2, 13.4, 11.5), 'mesh', 'guard wire')
    m.box((4.0, 15.6, 9.2), (12.0, 15.9, 11.0), 'danger', 'hot plate', up='danger:HOT')
    m.g('thermostat')
    enclosure(m, 4.0, 12.0, 6.4, 11.4, 13.0, 15.4)
    m.box((4.6, 10.6, 12.9), (11.4, 11.2, 13.0), 'plate', 'thermostat plate', north='plate:SHELTER HEAT')
    mains_box(m, 14.0, 7.0, 13.4, 'east')
    m.terminal_12v(2.0, 7.0, 13.4, 'west')
    f = m.surface('north', face=12.95, title='THERMOSTAT')
    m.ctl(f, 'gauge', 'temp', 10.2, 8.6, w=2.4, h=2.4, col='f2f0e6', label='C', max=40)
    m.ctl(f, 'digits', 'tempd', 10.2, 7.0, w=1.8, h=0.6, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'selector', 's_mode', 7.4, 8.8, w=1.2, h=1.2, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('heat', 'ff6020', 'HEAT'), ('coldl', BLUE, 'COLD')], 5.4, 10.0, step=0.9, size=0.4, lab_dv=0.4)
    m.ctl(f, 'push', 'test', 5.4, 7.4, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    m.g('detail')
    for x in (2.6, 4.0, 5.4, 6.8, 8.2, 9.6, 11.0, 12.4):
        m.box((x, 15.6, 11.0), (x + 0.3, 15.9, 11.6), 'fins', 'cooling fin')
    m.box((2.6, 11.4, 14.0), (3.0, 13.6, 14.4), 'galv', 'conduit')
    m.box((2.6, 13.6, 14.0), (3.6, 14.0, 14.4), 'galv', 'conduit elbow')
    m.box((4.6, 6.6, 12.9), (11.4, 6.9, 13.0), 'warn', 'warning strip')
    m.g('quartz elements')
    for z in (9.3, 10.0, 10.7):
        m.cyl((2.6, 14.4, z), 0.25, 10.8, 'x', 'paint:#ff7020', 'quartz tube')
    m.box((2.6, 14.0, 8.9), (3.0, 15.2, 11.2), 'porcelain:grey', 'element end cap')
    m.box((13.0, 14.0, 8.9), (13.4, 15.2, 11.2), 'porcelain:grey', 'element end cap')
    m.g('reflector ribs + guard')
    for x in (3.0, 4.6, 6.2, 7.8, 9.4, 11.0, 12.6):
        m.box((x, 15.5, 8.6), (x + 0.2, 15.7, 11.6), 'metal:steel', 'reflector rib')
    for z in (8.9, 9.6, 10.3, 11.0):
        m.box((2.4, 13.2, z), (13.6, 13.3, z + 0.1), 'metal:steel', 'guard grille bar')
    m.g('brackets')
    for x in (3.0, 12.4):
        m.box((x, 12.2, 14.6), (x + 0.6, 14.0, 15.4), 'metal:steel', 'wall bracket')
        m.bolt(x + 0.3, 13.0, 15.4, 'north', 0.3)
    m.g('thermostat box detail')
    m.box((10.6, 7.2, 12.85), (11.4, 7.6, 12.95), 'plate', 'sensor grille')
    m.box((4.4, 6.4, 13.6), (4.8, 6.6, 14.2), 'metal:brass', 'gland nut')
    m.box((3.0, 9.0, 14.0), (4.0, 9.4, 14.4), 'galv', 'conduit to box')
    m.box((3.0, 9.4, 14.0), (3.4, 11.4, 14.4), 'galv', 'conduit riser')
    for y in (10.0, 11.0):
        m.box((2.8, y, 14.0), (3.6, y + 0.2, 14.6), 'galv', 'conduit saddle')
    m.box((2.2, 15.9, 8.4), (13.8, 16.0, 11.8), 'metal:steel', 'top cover')
    m.box((7.6, 12.4, 11.6), (8.4, 13.6, 12.0), 'metal:steel', 'centre support strap')
    for x in (4.0, 12.0):
        m.bolt(x, 16.0, 10.0, 'up', 0.3)
    m.save(item_gui=0.5)


# ===================================================================================================== escalator
def escalator():
    m = Model('escalator')
    m.g('truss')
    m.box((1.6, 0, 0), (14.4, 7.4, 16), 'paint:#3a3e44', 'lower truss')
    m.box((1.6, 7.4, 8), (14.4, 15.4, 16), 'paint:#3a3e44', 'upper truss')
    m.g('steps')
    for i in range(8):                                   # the treads climb toward the back, 2 px a step
        z0, top = i * 2.0, (i + 1) * 2.0
        m.box((2.0, top - 0.6, z0), (14.0, top, z0 + 2.0), 'metal:alu', 'step tread')
        m.box((2.0, top - 2.0, z0), (14.0, top - 0.6, z0 + 0.3), 'paint:#2a2c30', 'step riser')
        m.box((2.0, top - 0.6, z0), (14.0, top, z0 + 0.25), 'paint:#f0c018', 'step nosing')
        for x in (3.0, 5.0, 7.0, 9.0, 11.0, 13.0):
            m.box((x, top, z0 + 0.4), (x + 0.15, top + 0.05, z0 + 1.8), 'paint:#202020', 'cleat')
    m.box((2.0, 8.0, 0.0), (14.0, 8.05, 1.0), 'paint:#f0c018', 'comb plate')
    m.box((2.0, 16.0, 8.0), (14.0, 16.05, 9.0), 'paint:#f0c018', 'comb plate')
    m.g('balustrades')
    for x0 in (0.0, 14.4):
        m.box((x0, 0, 0), (x0 + 1.6, 9.0, 16), 'metal:steel', 'skirt + deck')
        m.box((x0 + 0.5, 9.0, 0.5), (x0 + 1.1, 17.0, 8.0), 'glass', 'glass balustrade', rot={'axis': 'x', 'angle': -22.5, 'origin': [x0 + 0.8, 9.0, 0.5]})
        m.box((x0 + 0.5, 9.0, 8.0), (x0 + 1.1, 24.0, 16.0), 'glass', 'glass balustrade')
        m.box((x0 + 0.3, 24.0, 8.0), (x0 + 1.3, 24.8, 16.0), 'rubber', 'handrail')
        m.box((x0 + 0.3, 16.6, 0.0), (x0 + 1.3, 17.4, 9.0), 'rubber', 'handrail', rot={'axis': 'x', 'angle': -45, 'origin': [x0 + 0.8, 17.0, 4.5]})
    m.g('newel')
    m.box((14.4, 0, -0.6), (16.0, 12.0, 1.4), 'metal:steel', 'newel control box')
    m.box((14.5, 10.6, -0.7), (15.9, 11.6, -0.6), 'plate', 'plate', north='plate:ESC')
    mains_box(m, 0.0, 1.0, 12.0, 'west')
    m.terminal_12v(16.0, 1.0, 12.0, 'east')
    f = m.surface('north', face=-0.65, title='ESCALATOR')
    m.ctl(f, 'push', 'start', 0.75, 9.6, w=0.8, h=0.8, col='2fa84a', label='START')
    m.ctl(f, 'push', 'stop', 0.75, 8.4, w=0.8, h=0.8, col='202020', label='STOP')
    m.ctl(f, 'key', 'k_dir', 0.75, 7.0, w=0.8, h=0.8, col='c0a040', label='DIR', pos=['UP', 'DOWN'])
    m.ctl(f, 'mushroom', 'm_estop', 0.75, 5.4, w=1.0, h=1.0, col='d02020', label='STOP')
    m.ctl(f, 'lamp', 'runl', 0.4, 3.8, w=0.4, h=0.4, col=GREEN, label='RUN')
    m.ctl(f, 'lamp', 'upl', 1.1, 3.8, w=0.4, h=0.4, col=WHITE, label='UP')
    m.ctl(f, 'lamp', 'dnl', 0.4, 3.0, w=0.4, h=0.4, col=AMBER, label='DN')
    m.ctl(f, 'lamp', 'pwr', 1.1, 3.0, w=0.4, h=0.4, col=GREEN, label='12V')
    m.ctl(f, 'lamp', 'mainsl', 0.4, 2.2, w=0.4, h=0.4, col=GREEN, label='MAINS')
    m.ctl(f, 'push', 'test', 1.1, 2.2, w=0.4, h=0.4, col='f0f0f0', label='TEST')
    m.save(item_gui=0.45)


# ========================================================================================== facilities panel
def facilities_panel():
    m = Model('facilities_panel')
    m.g('board')
    m.box((0, 0, 10.6), (16, 1.0, 16), 'paint:#2a2d30', 'plinth')
    m.box((0.4, 1.0, 10.8), (15.6, 28.0, 16), 'paint:#1d4f9a', 'board cabinet')
    m.box((0.2, 28.0, 10.6), (15.8, 28.6, 16), 'paint:#143a72', 'roof')
    m.box((0.8, 1.4, 10.6), (15.2, 27.6, 10.8), 'paint:#101418', 'fascia')
    m.box((1.0, 25.0, 10.5), (15.0, 27.2, 10.6), 'plate', 'title', north='plate:STATION FACILITIES')
    m.box((1.0, 14.6, 10.55), (15.0, 22.6, 10.6), 'paint:#03140a', 'screen')
    for x in (0.9, 15.1):
        for y in (1.8, 27.2):
            m.bolt(x, y, 10.6, 'north', 0.35)
    m.louvres(10.0, 15.0, 1.6, 3.4, 10.6, n=3)
    m.terminal_12v(15.6, 2.0, 13.4, 'east')
    f = m.surface('north', face=10.5, title='FACILITIES')
    for i in range(5):
        m.ctl(f, 'digits', 'm%d' % (i + 1), 8.0, 21.8 - i * 1.6, w=13.4, h=1.0, col='ffd040' if i == 0 else '7cf29a', label='', fmt='%s')
    m.ctl(f, 'gauge', 'rating', 3.2, 11.6, w=3.4, h=3.4, col='f2f0e6', label='ACCESS', max=100)
    m.ctl(f, 'digits', 'grade', 3.2, 9.0, w=1.6, h=1.4, col='30e060', label='', fmt='%s')
    lamps(m, f, [('stepf', GREEN, 'STEP-FREE'), ('liftoos', RED, 'LIFT OOS', True), ('wc', GREEN, 'TOILET'), ('help', GREEN, 'HELP'), ('gap', GREEN, 'GAP')],
          6.6, 12.8, step=1.9, size=0.6)
    m.ctl(f, 'digits', 'tact', 7.4, 10.0, w=2.0, h=0.9, col='ffd040', label='', fmt='%d')
    m.ctl(f, 'label', '', 7.4, 9.2, w=0, h=0, col='e8e8e8', label='TACTILE')
    m.ctl(f, 'digits', 'cost', 10.6, 10.0, w=3.0, h=0.9, col='ff8040', label='', fmt='%s')
    m.ctl(f, 'label', '', 10.6, 9.2, w=0, h=0, col='e8e8e8', label='COST/DAY')
    m.ctl(f, 'digits', 'inc', 13.8, 10.0, w=3.0, h=0.9, col='30e060', label='', fmt='%s')
    m.ctl(f, 'label', '', 13.8, 9.2, w=0, h=0, col='e8e8e8', label='INCOME')
    pushes(m, f, [('audit', '2fa84a', 'AUDIT'), ('prev', '1a1a1a', 'PREV'), ('next', '1a1a1a', 'NEXT'), ('print', 'f0f0f0', 'PRINT'), ('test', 'f0f0f0', 'TEST')], 2.4, 6.4, step=2.0, size=1.0)
    m.ctl(f, 'key', 'k_level', 13.4, 6.4, w=1.2, h=1.2, col='c0a040', label='LEVEL ACCESS', pos=['STEPS', 'LEVEL'])
    m.ctl(f, 'lamp', 'pwr', 13.4, 4.2, w=0.5, h=0.5, col=GREEN, label='12V')
    m.g('pictograms')
    for i, t in enumerate(('LIFT', 'WC', 'HELP', 'GAP', 'SHOP')):
        m.box((1.2 + i * 2.8, 23.0, 10.5), (3.6 + i * 2.8, 24.6, 10.6), 'paint:#f4f4f0', 'pictogram', north='label:' + t)
    m.box((0.2, 28.6, 9.4), (15.8, 29.0, 16), 'paint:#143a72', 'sun hood')
    for y in (4.0, 12.0, 20.0, 26.0):
        m.box((0.1, y, 10.8), (0.4, y + 1.2, 11.4), 'metal:steel', 'hinge')
        m.box((15.6, y, 10.8), (15.9, y + 1.2, 11.4), 'metal:steel', 'hinge')
    for x in (1.0, 14.6):
        m.box((x, 1.0, 15.6), (x + 0.4, 28.0, 16.0), 'paint:#143a72', 'rear brace')
    for x in range(2, 15, 3):
        m.box((x, 14.4, 10.5), (x + 0.2, 22.8, 10.55), 'paint:#1a2a22', 'screen frame mullion')
    m.box((0.8, 13.8, 10.5), (15.2, 14.4, 10.6), 'metal:alu', 'screen sill')
    m.box((0.8, 22.6, 10.5), (15.2, 23.0, 10.6), 'metal:alu', 'screen head')
    m.g('frame + bolts')
    for y in range(3, 28, 3):
        m.bolt(0.6, y, 10.6, 'north', 0.3)
        m.bolt(15.4, y, 10.6, 'north', 0.3)
    m.box((0.4, 1.0, 10.55), (0.8, 28.0, 10.8), 'metal:alu', 'frame stile')
    m.box((15.2, 1.0, 10.55), (15.6, 28.0, 10.8), 'metal:alu', 'frame stile')
    m.g('conduit')
    m.box((7.6, 28.6, 13.0), (8.4, 32.0, 13.8), 'galv', 'conduit')
    m.box((7.4, 29.0, 12.8), (8.6, 29.4, 14.0), 'galv', 'conduit saddle')
    m.box((7.4, 31.0, 12.8), (8.6, 31.4, 14.0), 'galv', 'conduit saddle')
    m.g('braille + grade plinth')
    m.box((4.0, 4.4, 10.45), (12.0, 5.0, 10.55), 'metal:steel', 'braille strip')
    for x in range(5, 12):
        m.box((x, 4.6, 10.42), (x + 0.2, 4.8, 10.45), 'paint:#202020', 'braille dots')
    m.box((1.4, 7.8, 10.4), (5.0, 8.2, 10.6), 'metal:steel', 'grade emblem plinth')
    m.box((2.2, 8.2, 10.45), (4.2, 9.8, 10.55), 'paint:#1d4f9a', 'grade emblem')
    m.box((1.0, 2.0, 10.45), (9.0, 3.4, 10.55), 'plate', 'audit plate', north='plate:ACCESS AUDIT')
    m.box((2.0, 28.6, 10.0), (14.0, 28.8, 10.6), 'paint:#fff0b0', 'board light strip')
    for x in (3.0, 13.0):
        m.box((x, 28.0, 10.2), (x + 0.4, 28.6, 10.6), 'metal:steel', 'light bracket')
    m.box((0.0, 0.0, 16.0 - 0.4), (16.0, 0.4, 16.0), 'paint:#14181c', 'rear plinth')
    m.box((12.4, 2.0, 10.45), (14.6, 3.4, 10.55), 'paint:#1d4f9a', 'access symbol', north='label:WC')
    m.box((6.8, 1.0, 10.6), (9.2, 1.4, 11.0), 'metal:brass', 'cable gland')
    m.save(item_gui=0.45)


# =================================================================================================== tactile paving
def tactile(name, base, dome, offset):
    m = Model(name)
    m.g('slab')
    m.box((0, 0, 0), (16, 16, 16), 'concrete', 'paving slab', up='paint:' + base)
    m.g('domes')
    for r in range(6):
        for c in range(6):
            x = 1.6 + c * 2.55 + (1.27 if offset and r % 2 else 0)
            if x > 15.0: continue
            z = 1.6 + r * 2.55
            m.box((x - 0.55, 16, z - 0.55), (x + 0.55, 16.35, z + 0.55), 'paint:' + dome, 'dome')
            m.box((x - 0.35, 16.35, z - 0.35), (x + 0.35, 16.5, z + 0.35), 'paint:' + dome, 'dome top')
    m.save(item_gui=0.625)


RECIPES = {
    'platform_panel': (['LSL', 'CFC', 'IRI'], {'L': 'minecraft:redstone_lamp', 'S': 'irextras:part@20', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'station_lift': (['IGI', 'MDM', 'ICI'], {'I': 'minecraft:iron_block', 'G': 'minecraft:glass_pane', 'M': 'irextras:part@3', 'D': 'minecraft:iron_door', 'C': 'irextras:part@2'}),
    'help_point': (['LSL', 'BCB', 'III'], {'L': 'minecraft:redstone_lamp', 'S': 'irextras:part@25', 'B': 'minecraft:stone_button', 'C': 'irextras:part@2', 'I': 'minecraft:iron_ingot'}),
    'station_toilet': (['QDQ', 'QCQ', 'QBQ'], {'Q': 'minecraft:quartz_block', 'D': 'minecraft:iron_door', 'C': 'minecraft:cauldron', 'B': 'minecraft:stone_button'}),
    'vending_machine': (['IGI', 'ICI', 'IHI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'C': 'irextras:part@2', 'H': 'minecraft:chest'}),
    'retail_kiosk': (['WGW', 'PCP', 'WHW'], {'W': 'minecraft:planks', 'G': 'minecraft:glass_pane', 'P': 'minecraft:paper', 'C': 'minecraft:chest', 'H': 'minecraft:iron_trapdoor'}),
    'platform_lamp': ([' L ', ' I ', ' B '], {'L': 'minecraft:redstone_lamp', 'I': 'minecraft:iron_bars', 'B': 'minecraft:iron_block'}),
    'platform_heater': (['III', 'HHH', ' C '], {'I': 'minecraft:iron_ingot', 'H': 'irextras:part@6', 'C': 'irextras:part@2'}),
    'escalator': (['  R', ' SM', 'SSI'], {'R': 'minecraft:rail', 'S': 'minecraft:stone_stairs', 'M': 'irextras:part@3', 'I': 'minecraft:iron_block'}),
    'facilities_panel': (['SSS', 'CFC', 'IPI'], {'S': 'irextras:part@20', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot', 'P': 'minecraft:paper'}),
    'tactile_blister': (['BBB', 'SSS'], {'B': 'minecraft:stone_button', 'S': 'minecraft:stone'}),
    'tactile_edge': (['DDD', 'BBB', 'SSS'], {'D': 'minecraft:dye', 'B': 'minecraft:stone_button', 'S': 'minecraft:stone'}),
}
DATA = {'minecraft:planks': 0, 'minecraft:stone': 0, 'minecraft:dye': 11}          # dandelion yellow = 11
FACING = ['platform_panel', 'station_lift', 'help_point', 'station_toilet', 'vending_machine', 'retail_kiosk', 'platform_lamp', 'platform_heater', 'escalator', 'facilities_panel']


def ing(v):
    if '@' in v:
        i, d = v.split('@')
        return {'item': i, 'data': int(d)}
    return {'item': v, 'data': DATA[v]} if v in DATA else {'item': v}


if __name__ == '__main__':
    platform_panel(); station_lift(); help_point(); station_toilet(); vending_machine(); retail_kiosk()
    platform_lamp(); platform_heater(); escalator(); facilities_panel()
    tactile('tactile_blister', '#c8c0a8', '#b8b098', False)
    tactile('tactile_edge', '#f0c018', '#e8b810', True)
    for n in FACING:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
    for n in ('tactile_blister', 'tactile_edge'):
        json.dump({'variants': {'normal': {'model': 'irextras:' + n}}}, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
    for n in FACING + ['tactile_blister', 'tactile_edge']:
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        count = 4 if n.startswith('tactile') else 1
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': count}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    print('kiosk shelf spots (model px):', SPOTS['retail_kiosk'])
