#!/usr/bin/env python3
"""Batch 6 E #21-25 (her words: "complex buttons and knobs and stuff, screens", Blockbench models, real looking,
everything functional): the 250 t Breakdown Crane (carrier + console; the slewing superstructure, jib, hook and
outrigger beams are drawn live by ops6/client/CraneRenderer), the Flange Lubricator, the Master Clock, the Driver
Training Simulator desk and the RCO Remote Control Beltpack (item; its working face is the beltpack GUI).

Models face north. Every lamp, button, key, knob, gauge and display is a live control the Java tiles (ops6/*) read or
work. Also writes blockstates, item models, recipes and lang lines."""
import json, math, os
from bbkit import Model, ASSETS
from gen_bb_ctl import facing_states, ing

X = lambda x: 16 - x
STEEL = 'metal:steel'
DARK = 'paint:#2c2f34'
YEL = 'paint:#e8b818'
CRANE = 'paint:#e2ae14'


def lamp(m, sf, cid, x, y, col, label='', s=0.5, blink=False):
    m.ctl(sf, 'lamp', cid, X(x), y, w=s, h=s, col=col, label=label, **({'blink': True} if blink else {}))
    m.ctl(sf, 'label', '', X(x), y - s / 2 - 0.35, w=0, h=0, col='e8e8e8', label=label)


def push(m, sf, cid, x, y, col, label, s=0.9):
    m.ctl(sf, 'push', cid, X(x), y, w=s, h=s, col=col, label=label)
    m.ctl(sf, 'label', '', X(x), y - s / 2 - 0.4, w=0, h=0, col='e8e8e8', label=label)


def mush(m, sf, cid, x, y, label='E-STOP', s=1.5):
    m.ctl(sf, 'mushroom', cid, X(x), y, w=s, h=s, col='d01818', label=label)


def digits(m, sf, cid, x, y, w, h=0.7, col='7cf29a', fmt='%s'):
    m.ctl(sf, 'digits', cid, X(x), y, w=w, h=h, col=col, label='', fmt=fmt)


def sel(m, sf, cid, x, y, label, pos, s=1.1, t='selector'):
    m.ctl(sf, t, cid, X(x), y, w=s, h=s, col='c0a040' if t == 'key' else '202020', label=label, pos=pos)
    m.ctl(sf, 'label', '', X(x), y - s / 2 - 0.4, w=0, h=0, col='e8e8e8', label=label)


def gauge(m, sf, cid, x, y, label, s=1.6, mx=100):
    m.ctl(sf, 'gauge', cid, X(x), y, w=s, h=s, col='f2f0e6', label=label, max=mx)
    m.ctl(sf, 'label', '', X(x), y - s / 2 - 0.4, w=0, h=0, col='e8e8e8', label=label)


def bezel(m, x0, x1, y0, y1, z, name='display'):
    """a dark display window in a bezel on a north face at z"""
    m.box((x0 - 0.25, y0 - 0.25, z - 0.08), (x1 + 0.25, y1 + 0.25, z), 'paint:#18191c', name + ' bezel')
    m.box((x0, y0, z - 0.12), (x1, y1, z - 0.08), 'paint:#07120c', name)


def console_box(m, x0, x1, y0, y1, z0, z1, col, name):
    """an enclosure with a door outline, hinges, hood and corner bolts; the controls go on its north face (z0)"""
    m.box((x0, y0, z0), (x1, y1, z1), col, name)
    m.box((x0 - 0.2, y1, z0 - 0.4), (x1 + 0.2, y1 + 0.5, z1 + 0.1), 'paint:#1e2024', name + ' hood')
    m.box((x0 + 0.2, y0, z0 - 0.06), (x1 - 0.2, y0 + 0.25, z0), 'paint:#1e2024', name + ' door edge')
    for y in (y0 + 0.8, y1 - 1.6):
        m.box((x1 - 0.3, y, z0 - 0.25), (x1 - 0.05, y + 0.8, z0), STEEL, 'hinge')
    for x in (x0 + 0.4, x1 - 0.4):
        for y in (y0 + 0.4, y1 - 0.4):
            m.bolt(x, y, z0, 'north', 0.3)


def rail(m, x0, x1, z, y=0.0, name='rail'):
    m.box((x0, y, z - 0.45), (x1, y + 0.3, z + 0.45), STEEL, name + ' foot')
    m.box((x0, y + 0.3, z - 0.12), (x1, y + 0.9, z + 0.12), STEEL, name + ' web')
    m.box((x0, y + 0.9, z - 0.3), (x1, y + 1.2, z + 0.3), 'metal:steel', name + ' head')


# ================================================================================================ #22 breakdown crane
def rail_crane():
    m = Model('rail_crane_250')
    m.g('track')
    for i in range(24):
        x = -15.6 + i * 2
        m.box((x, 0, 1.6), (x + 1.0, 0.5, 14.4), 'wood:5a4028', 'sleeper')
    for z in (4.5, 11.5):
        rail(m, -16, 32, z, 0.5)
    m.g('bogies')
    for bx in (-6.0, 22.0):
        for z in (3.2, 12.0):
            m.box((bx - 5, 2.0, z - 0.4), (bx + 5, 4.6, z + 0.4), 'paint:#2a2c30', 'bogie side frame')
            for wx in (bx - 3, bx + 3):
                m.cyl((wx, 2.9, z + (0.8 if z < 8 else -1.6)), 1.7, 0.8, 'z', 'paint:#1e1e20', 'wheel', cap='metal:steel')
                m.box((wx - 0.9, 2.2, z - 0.7), (wx + 0.9, 3.6, z + 0.7), 'paint:#3a3c40', 'axlebox')
            for sx in (bx - 1.5, bx + 1.5):
                for k in range(3):
                    m.box((sx - 0.5, 4.6 + k * 0.5, z - 0.5), (sx + 0.5, 4.9 + k * 0.5, z + 0.5), 'metal:steel', 'spring coil')
        for wx in (bx - 3, bx + 3):
            m.cyl((wx, 2.9, 4.0), 0.4, 8.0, 'z', 'metal:steel', 'axle')
        m.box((bx - 1.8, 4.2, 3.6), (bx + 1.8, 5.4, 12.4), 'paint:#2a2c30', 'bolster')
        m.box((bx - 4.5, 1.2, 4.0), (bx - 3.6, 2.4, 12.0), 'paint:#3a3c40', 'brake beam')
    m.g('carrier frame')
    for (z0, z1) in ((2.4, 4.0), (12.0, 13.6)):
        m.box((-14.5, 6.0, z0), (30.5, 9.4, z1), CRANE, 'main girder')
        m.box((-14.5, 5.6, z0 - 0.2), (30.5, 6.0, z1 + 0.2), 'paint:#2a2c30', 'bottom flange')
    for x in (-12.0, -4.0, 4.0, 12.0, 20.0, 28.0):
        m.box((x, 6.2, 4.0), (x + 0.8, 9.2, 12.0), 'paint:#c89a10', 'cross member')
    m.box((-15.0, 9.4, 1.2), (31.0, 10.4, 14.8), 'paint:#3a3c40', 'deck plate')
    for x in range(-14, 31, 3):
        m.box((x, 10.4, 1.4), (x + 1.6, 10.5, 14.6), 'paint:#4a4c50', 'chequer plate')
    m.g('headstocks + buffers')
    for (x0, x1, bx0, bx1) in ((-14.5, -13.5, -16.0, -14.5), (29.5, 30.5, 30.5, 32.0)):
        m.box((x0, 4.6, 1.6), (x1, 9.8, 14.4), 'paint:#2a2c30', 'headstock')
        m.box((x0 - 0.05 if x0 < 0 else x1, 5.0, 2.0), (x0 if x0 < 0 else x1 + 0.05, 9.4, 14.0), 'haz', 'hazard stripes', faces=('west',) if x0 < 0 else ('east',))
        for z in (3.6, 12.4):
            m.cyl((bx0, 7.0, z), 1.1, bx1 - bx0, 'x', 'metal:steel', 'buffer', cap='paint:#2a2c30')
        m.box((bx0, 6.4, 7.4), (bx1, 7.6, 8.6), 'paint:#2a2c30', 'coupling hook')
        for z in (2.4, 13.6):
            m.box((x0 if x0 < 0 else x1 - 0.2, 8.0, z - 0.5), (x0 + 0.2 if x0 < 0 else x1, 9.0, z + 0.5), 'lamp:ff2020', 'tail lamp')
    m.g('outrigger boxes')
    for bx in (-9.4, 22.6):
        m.box((bx, 6.2, 1.0), (bx + 3.2, 9.0, 15.0), CRANE, 'outrigger box')
        m.box((bx + 0.4, 6.6, 0.95), (bx + 2.8, 8.6, 1.0), 'haz', 'beam end', faces=('north',))
        m.box((bx + 0.4, 6.6, 15.0), (bx + 2.8, 8.6, 15.05), 'haz', 'beam end', faces=('south',))
        m.box((bx + 0.6, 5.2, 0.6), (bx + 2.6, 6.2, 2.6), 'paint:#3a3f44', 'jack stowed')
        m.box((bx + 0.6, 5.2, 13.4), (bx + 2.6, 6.2, 15.4), 'paint:#3a3f44', 'jack stowed')
        m.box((bx + 0.2, 9.0, 3.0), (bx + 3.0, 9.1, 7.0), 'label:OUTRIGGER', 'outrigger label', faces=('up',))
    m.g('slew base')
    m.cyl((8, 10.4, 8), 6.6, 3.6, 'y', 'paint:#2a2c30', 'slew bearing base')
    for i in range(12):
        a = i * math.pi / 6
        m.bolt(8 + 6.0 * math.cos(a), 14.0, 8 + 6.0 * math.sin(a), 'up', 0.4)
    m.g('deck machinery')
    m.box((22.0, 10.4, 9.0), (29.6, 15.6, 14.4), 'paint:#3a3f44', 'shore supply + generator set')
    m.louvres(22.4, 29.2, 11.0, 15.0, 14.4, n=6)
    m.cyl((28.4, 15.6, 13.0), 0.45, 2.4, 'y', 'metal:steel', 'exhaust stack')
    m.box((22.4, 12.2, 8.95), (29.2, 14.6, 9.0), 'danger:480V', 'danger 480V', faces=('north',))
    m.box((-14.4, 10.4, 9.0), (-8.6, 14.6, 14.4), 'paint:#2a62a0', 'hydraulic oil tank')
    m.cyl((-11.5, 14.6, 11.7), 0.6, 0.6, 'y', 'metal:brass', 'filler cap')
    m.box((-14.45, 11.0, 10.0), (-14.4, 14.0, 10.6), 'glass', 'oil sight glass', faces=('west',))
    m.box((-14.4, 13.2, 8.95), (-8.6, 14.4, 9.0), 'label:HYD OIL', 'tank label', faces=('north',))
    for (x0, x1) in ((-8.6, -2.0), (17.0, 22.0)):
        m.cyl((x0, 11.0, 11.0), 0.3, x1 - x0, 'x', 'cable:202020', 'hydraulic hose')
    m.g('handrails')
    m.handrail([(-14.8, 14.8), (-1.0, 14.8)], 10.4, h=3.0, r=0.15, mat='paint:ral1023')
    m.handrail([(17.0, 14.8), (30.8, 14.8)], 10.4, h=3.0, r=0.15, mat='paint:ral1023')
    for x in (-15.6, 30.4):
        for k in range(3):
            m.box((x, 2.0 + k * 2.4, 14.6), (x + 1.2, 2.3 + k * 2.4, 15.8), 'paint:#2a2c30', 'step')
    m.g('nameplates')
    m.box((-12.0, 6.6, 2.35), (-2.0, 8.8, 2.4), 'plate:250 T|BREAKDOWN CRANE', 'nameplate', faces=('north',))
    m.box((16.5, 6.6, 2.35), (21.5, 8.8, 2.4), 'warn:', 'warning triangle', faces=('north',))
    m.box((-12.0, 6.6, 13.6), (-2.0, 8.8, 13.65), 'plate:250 T|BREAKDOWN CRANE', 'nameplate', faces=('south',))
    m.g('console')
    console_box(m, 1.0, 15.0, 1.0, 12.4, 0.2, 2.4, 'paint:#3a3f44', 'carrier control console')
    m.box((1.2, 12.9, 0.15), (14.8, 13.6, 0.2), 'label:CRANE CONTROL - LMI', 'console title', faces=('north',))
    bezel(m, 1.4, 14.6, 9.2, 12.2, 0.2, 'LMI display')
    m.box((15.0, 2.0, 0.8), (15.8, 5.0, 2.0), 'paint:#3a3f44', 'console side box')
    m.gland(8.0, 1.0, 1.4, 'down', r=0.4, cable='cable:1d1d21')
    m.terminal_12v(8.0, 3.2, 13.6, 'south')
    m.box((3.0, 2.5, 13.6), (6.0, 5.5, 14.8), 'paint:#3a3f44', '480V plug box', south='danger:480V')
    m.cyl((4.5, 0.6, 14.2), 0.35, 1.9, 'y', 'cable:202020', '480V supply cable')
    f = m.surface('north', face=0.18, title='CRANE CONTROL')
    digits(m, f, 'lmi', 3.6, 11.5, 3.6, 1.1, 'ffd040', '%s %%')
    digits(m, f, 'rad', 7.6, 11.5, 3.0, 0.8, '7cf29a', 'R %s M')
    digits(m, f, 'load', 10.6, 11.5, 2.6, 0.8, '7cf29a', '%s T')
    digits(m, f, 'rated', 13.4, 11.5, 2.4, 0.8, '8ad4ff', '/%s T')
    digits(m, f, 'tline', 8.0, 10.4, 12.6, 0.55, 'ffb020')
    digits(m, f, 'ang', 8.0, 9.7, 12.6, 0.55, '7cf29a')
    gauge(m, f, 'g_lmi', 2.6, 7.6, 'LMI %', 1.6, 120)
    gauge(m, f, 'g_wind', 4.6, 7.6, 'WIND', 1.6)
    gauge(m, f, 'g_hyd', 6.6, 7.6, 'HYD', 1.6)
    gauge(m, f, 'g_outr', 8.6, 7.6, 'OUTRIG', 1.6)
    for i, (cid, col, lab, bl) in enumerate((('pwr', '30e060', '12V', False), ('sup', '30e060', '480V', False), ('hyd', '30e060', 'HYD', False), ('outr', '30e060', 'OUTR', False),
                                             ('outrm', 'ffb020', 'MOVE', True), ('rig', '8ad4ff', 'RIG', False))):
        lamp(m, f, cid, 10.0 + (i % 3) * 1.6, 8.3 - (i // 3) * 1.4, col, lab, 0.45, bl)
    for i, (cid, col, lab, bl) in enumerate((('lifted', '30e060', 'LIFT', False), ('lmi90', 'ffb020', 'LMI90', False), ('lmicut', 'ff2020', 'CUT', True),
                                             ('byp', 'ff2020', 'BYP', False), ('wind', 'ff8020', 'WIND', False), ('tgtl', '30e060', 'OVER', False))):
        lamp(m, f, cid, 10.0 + (i % 3) * 1.6, 5.5 - (i // 3) * 1.4, col, lab, 0.45, bl)
    sel(m, f, 's_slew', 2.0, 4.8, 'SLEW', ['STOP', 'LEFT', 'RIGHT'], 1.0)
    sel(m, f, 's_luff', 3.7, 4.8, 'LUFF', ['STOP', 'UP', 'DOWN'], 1.0)
    sel(m, f, 's_hoist', 5.4, 4.8, 'HOIST', ['STOP', 'UP', 'DOWN'], 1.0)
    sel(m, f, 's_outr', 7.1, 4.8, 'OUTRIG', ['IN', 'OUT'], 1.0)
    sel(m, f, 'k_hyd', 8.6, 4.8, 'HYD', ['OFF', 'ON'], 0.8, 'key')
    sel(m, f, 'k_byp', 14.4, 2.2, 'LMI BYP', ['NORMAL', 'BYPASS'], 0.8, 'key')
    mush(m, f, 'm_estop', 14.4, 4.6, 'E-STOP', 1.4)
    for i, (cid, col, lab) in enumerate((('tgt', '2050d0', 'TARGET'), ('aim', 'e0a020', 'AIM'), ('hook', '1fa34a', 'HOOK'), ('horn', '505860', 'HORN'),
                                         ('ack', 'f0d020', 'ACK'), ('test', 'e0e0e0', 'TEST'), ('screen', '303a48', 'SCREEN'))):
        push(m, f, cid, 2.0 + i * 1.65, 2.2, col, lab, 0.8)
    m.save(item_gui=0.32)
    return m


# ================================================================================================ #23 flange lubricator
def flange_lube():
    m = Model('flange_lubricator')
    m.g('base')
    m.box((0.2, 0, 0.2), (15.8, 0.8, 11.0), 'concrete', 'concrete plinth')
    m.g('rail + wiping bars')
    for i in range(8):
        m.box((i * 2.0 + 0.2, 0, 11.4), (i * 2.0 + 1.2, 0.4, 16.0), 'wood:5a4028', 'sleeper')
    rail(m, 0, 16, 13.2, 0.4, 'rail')
    for (x0, x1) in ((1.5, 6.5), (9.5, 14.5)):
        m.box((x0, 0.9, 12.5), (x1, 1.5, 12.95), 'paint:#3a3c40', 'wiping bar')
        m.box((x0, 1.5, 12.7), (x1, 1.6, 12.95), 'paint:#20180c', 'grease ports')
        for x in (x0 + 0.5, x1 - 0.8):
            m.box((x, 0.4, 12.2), (x + 0.3, 1.0, 12.9), STEEL, 'rail clamp')
    m.box((7.4, 0.6, 12.2), (8.6, 1.8, 12.95), 'paint:#2a62a0', 'distribution block')
    m.g('wheel detector')
    m.box((0.6, 0.6, 12.0), (1.8, 2.0, 12.9), 'paint:#f0c818', 'wheel detector head')
    m.box((0.8, 1.6, 11.95), (1.6, 1.9, 12.0), 'lamp:30e060', 'detector led', faces=('north',))
    m.cyl((1.2, 0.8, 11.0), 0.2, 1.0, 'z', 'cable:202020', 'detector cable')
    m.g('grease tank')
    m.box((9.4, 0.8, 1.0), (15.6, 9.6, 7.6), 'paint:#2c3a30', 'grease tank')
    m.box((9.2, 9.6, 0.8), (15.8, 10.2, 7.8), 'paint:#1e2622', 'tank lid')
    m.cyl((12.5, 10.2, 4.3), 0.9, 0.6, 'y', 'paint:#c82020', 'fill cap')
    for x in (10.0, 15.0):
        m.box((x - 0.3, 9.4, 7.8), (x + 0.3, 10.0, 8.1), STEEL, 'lid hinge')
    m.box((15.6, 1.6, 2.6), (15.75, 8.8, 3.2), 'glass', 'level sight tube', faces=('east', 'north', 'south'))
    m.box((15.62, 1.6, 2.65), (15.7, 4.6, 3.15), 'paint:#20180c', 'grease in the tube', faces=('east',))
    m.box((9.35, 6.0, 0.95), (15.4, 8.6, 1.0), 'label:GREASE 40 KG', 'tank label', faces=('north',))
    m.box((9.35, 2.0, 0.95), (13.0, 3.6, 1.0), 'warn:', 'warning', faces=('north',))
    for y in (2.0, 5.0, 8.0):
        m.box((9.3, y, 0.9), (15.7, y + 0.3, 7.7), 'paint:#24302a', 'tank band')
    m.g('pump')
    m.cyl((9.6, 2.2, 9.2), 1.1, 3.6, 'x', 'paint:#2a5a8a', 'pump motor')
    for k in range(4):
        m.box((10.0 + k * 0.8, 1.1, 8.0), (10.3 + k * 0.8, 3.3, 10.4), 'paint:#24507a', 'motor fin')
    m.box((13.2, 1.0, 8.3), (15.2, 3.4, 10.2), 'paint:#3a3f44', 'gear pump')
    m.cyl((14.2, 3.4, 9.2), 0.4, 1.0, 'y', 'metal:brass', 'relief valve')
    m.box((8.6, 0.8, 8.2), (9.6, 2.6, 10.2), 'paint:#1e2024', 'motor terminal box')
    m.g('hoses')
    for (x, z0) in ((7.8, 10.2), (8.2, 10.2)):
        m.cyl((x, 1.2, z0), 0.25, 2.0, 'z', 'cable:181818', 'grease hose')
    m.cyl((12.0, 1.2, 7.6), 0.25, 0.8, 'z', 'cable:181818', 'tank outlet hose')
    m.box((8.0, 0.9, 7.8), (12.4, 1.5, 8.4), 'cable:181818', 'hose run')
    m.g('control cabinet')
    console_box(m, 0.6, 8.6, 1.0, 9.0, 0.6, 3.6, 'ral7035', 'lubricator control cabinet')
    m.box((0.8, 9.6, 0.55), (8.4, 10.3, 0.6), 'label:FLANGE LUBRICATOR', 'cabinet title', faces=('north',))
    bezel(m, 1.0, 8.2, 6.8, 8.7, 0.6, 'controller display')
    m.box((0.6, 1.0, 3.6), (8.6, 8.4, 6.2), 'ral7035', 'cabinet body')
    m.terminal_12v(4.6, 2.0, 6.2, 'south')
    m.box((0.0, 2.0, 3.8), (0.6, 5.0, 5.8), 'paint:#3a3f44', 'supply isolator', west='danger:240V')
    m.cyl((-0.3, 3.5, 4.8), 0.35, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    m.gland(2.0, 1.0, 5.0, 'down', r=0.35, cable='cable:202020')
    m.g('solar + mast')
    m.cyl((2.0, 10.3, 5.0), 0.25, 3.0, 'y', 'galv', 'mast')
    m.box((0.2, 13.3, 3.2), (3.8, 13.5, 6.8), 'paint:#18284a', 'charging panel', rot={'axis': 'x', 'angle': -22.5, 'origin': [2.0, 13.4, 5.0]})
    f = m.surface('north', face=0.58, title='LUBRICATOR')
    digits(m, f, 'axles', 2.8, 8.2, 3.0, 0.6, 'ffd040', '%s AXLES')
    digits(m, f, 'used', 6.4, 8.2, 3.0, 0.6, '7cf29a', '%s G')
    digits(m, f, 'line', 4.6, 7.3, 6.8, 0.5, '8ad4ff')
    gauge(m, f, 'tankg', 2.2, 5.4, 'TANK', 1.4)
    gauge(m, f, 'filmg', 4.2, 5.4, 'FILM', 1.4, 150)
    for i, (cid, col, lab, bl) in enumerate((('pwr', '30e060', '12V', False), ('sup', '30e060', 'SUPPLY', False), ('pumpl', 'ffb020', 'PUMP', True),
                                             ('auto', '30e060', 'AUTO', False), ('det', '8ad4ff', 'AXLE', True))):
        lamp(m, f, cid, 5.8 + (i % 3) * 1.0, 6.0 - (i // 3) * 1.1, col, lab, 0.4, bl)
    for i, (cid, col, lab, bl) in enumerate((('low', 'ffb020', 'LOW', False), ('empty', 'ff2020', 'EMPTY', False), ('filmok', '30e060', 'FILM', False),
                                             ('over', 'ff2020', 'OVER', True), ('wet', '8ad4ff', 'RAIN', False))):
        lamp(m, f, cid, 1.6 + i * 1.1, 3.6, col, lab, 0.4, bl)
    sel(m, f, 'k_on', 7.4, 3.8, 'MODE', ['OFF', 'AUTO'], 0.7, 'key')
    sel(m, f, 's_dose', 7.4, 2.2, 'DOSE', ['LOW', 'MED', 'HIGH'], 0.8)
    for i, (cid, col, lab) in enumerate((('prime', '2050d0', 'PRIME'), ('fill', '1fa34a', 'FILL'), ('reset', '505860', 'RESET'), ('test', 'e0e0e0', 'TEST'), ('screen', '303a48', 'SCREEN'))):
        push(m, f, cid, 1.4 + i * 1.15, 2.0, col, lab, 0.65)
    m.save(item_gui=0.55)
    return m


# ================================================================================================ #24 master clock
def master_clock():
    m = Model('master_clock')
    m.g('cabinet')
    m.box((2.0, 0, 3.0), (14.0, 0.8, 13.0), 'paint:#1e2024', 'plinth')
    m.box((2.2, 0.8, 3.2), (13.8, 15.2, 12.8), 'ral7035', 'rack cabinet')
    for x in (2.0, 13.6):
        for z in (3.0, 12.6):
            m.box((x, 0.8, z), (x + 0.4, 15.4, z + 0.4), 'paint:#3a3e44', 'frame post')
    m.box((2.0, 15.2, 3.0), (14.0, 16.0, 13.0), 'paint:#3a3e44', 'top hat')
    m.louvres(2.2, 13.8, 13.0, 14.8, 12.8, n=6)
    for side, x in (('west', 2.15), ('east', 13.85)):
        for k in range(5):
            m.box((x - 0.05, 3.0 + k * 2.2, 5.0), (x + 0.05, 3.6 + k * 2.2, 11.0), 'louvre', 'side vent', faces=(side,))
    m.g('front panel')
    m.box((2.4, 1.0, 2.9), (13.6, 15.0, 3.2), 'paint:#2a2e34', 'front panel')
    m.box((2.6, 14.0, 2.85), (13.4, 14.8, 2.9), 'label:MASTER CLOCK - GPS / RADIO', 'title', faces=('north',))
    bezel(m, 3.0, 13.0, 10.6, 13.6, 2.9, 'time display')
    bezel(m, 3.0, 13.0, 7.6, 10.2, 2.9, 'status display')
    m.box((3.0, 1.2, 2.85), (6.0, 2.0, 2.9), 'plate:SLAVE OUTPUT', 'output plate', faces=('north',))
    m.g('rear terminals')
    m.terminal_12v(5.0, 2.0, 12.8, 'south')
    for i in range(6):
        x = 8.4 + i * 0.8
        m.box((x - 0.3, 3.0, 12.8), (x + 0.3, 3.6, 13.2), 'paint:#c82020' if i % 2 == 0 else 'paint:#1d1d21', 'impulse terminal')
    m.box((8.0, 3.8, 12.8), (13.2, 4.6, 12.85), 'label:SLAVE LINES 1 2 3', 'terminal label', faces=('south',))
    m.box((9.0, 5.4, 12.8), (13.0, 8.4, 13.6), 'paint:#3a3f44', 'mains inlet', south='danger:240V')
    m.gland(11.0, 0.8, 13.2, 'down', r=0.35, cable='cable:202020')
    for i in range(3):
        m.cyl((8.4 + i * 1.6, 0.8, 12.9), 0.18, 2.2, 'y', 'cable:%s' % ('c81e1e', 'e8e8e8', '2050d0')[i], 'slave line')
    m.g('antennas')
    m.cyl((8.0, 16.0, 8.0), 0.35, 11.0, 'y', 'galv', 'antenna mast')
    m.cyl((8.0, 27.0, 8.0), 1.4, 1.0, 'y', 'white', 'GPS antenna base')
    m.cyl((8.0, 28.0, 8.0), 1.0, 0.8, 'y', 'white', 'GPS antenna dome')
    m.box((8.0, 23.0, 7.7), (12.0, 23.6, 8.3), 'galv', 'radio antenna bracket')
    m.cyl((10.0, 23.6, 5.4), 0.4, 5.2, 'z', 'paint:#2a2a2a', 'ferrite rod antenna')
    m.cyl((8.4, 16.0, 8.4), 0.15, 11.0, 'y', 'cable:1d1d21', 'antenna coax')
    for y in (19.0, 22.0, 25.0):
        m.box((7.6, y, 7.6), (8.4, y + 0.4, 8.4), 'galv', 'mast clamp')
    m.g('rack details')
    for y in (1.6, 8.0, 14.0):
        for x in (2.7, 13.0):
            m.box((x, y - 0.6, 2.6), (x + 0.3, y + 0.6, 2.9), 'metal:alu', 'rack handle')
    for (x0, x1, y0, y1) in ((3.0, 13.0, 10.6, 13.6), (3.0, 13.0, 7.6, 10.2)):
        for x in (x0 - 0.5, x1 + 0.5):
            for y in (y0, y1):
                m.bolt(x, y, 2.9, 'north', 0.25)
    for x in (2.4, 13.2):
        for z in (3.2, 12.4):
            m.cyl((x + 0.2, 0, z + 0.2), 0.3, 0.2, 'y', STEEL, 'levelling foot')
    m.box((2.6, 1.0, 12.8), (13.4, 15.0, 12.9), 'paint:#b8bab6', 'rear door')
    m.louvres(3.0, 7.6, 8.0, 14.0, 12.9, n=5)
    m.box((3.0, 0.9, 12.9), (7.4, 1.4, 13.3), 'paint:#3a8a3a', 'earth bar')
    for i in range(4):
        m.box((3.3 + i * 1.0, 0.95, 13.3), (3.6 + i * 1.0, 1.3, 13.4), STEEL, 'earth bolt')
    m.cyl((7.0, 0, 13.1), 0.2, 1.0, 'y', 'cable:3a8a3a', 'earth lead')
    m.box((13.85, 4.0, 5.0), (14.2, 12.0, 6.0), 'paint:#3a3e44', 'cable tray')
    m.cyl((14.0, 12.0, 5.5), 0.2, 4.0, 'y', 'cable:1d1d21', 'coax down the tray')
    m.box((2.2, 15.4, 3.4), (5.0, 15.8, 6.0), 'paint:#3a3e44', 'cable entry')
    m.box((2.4, 15.8, 3.6), (4.8, 15.9, 5.8), 'mesh', 'brush strip')
    f = m.surface('north', face=2.83, title='MASTER CLOCK')
    digits(m, f, 'time', 8.0, 12.5, 9.4, 1.5, 'ff5030')
    digits(m, f, 'date', 5.2, 11.0, 4.0, 0.6, 'ffd040')
    digits(m, f, 'err', 10.8, 11.0, 4.0, 0.6, '8ad4ff')
    digits(m, f, 'srcl', 8.0, 9.6, 9.4, 0.6, '7cf29a')
    digits(m, f, 'slv', 8.0, 8.4, 9.4, 0.6, '7cf29a')
    gauge(m, f, 'g_sig', 4.0, 6.0, 'SIGNAL', 1.5)
    gauge(m, f, 'g_osc', 6.0, 6.0, 'XTAL', 1.5)
    for i, (cid, col, lab, bl) in enumerate((('pwr', '30e060', '12V', False), ('sup', '30e060', 'MAINS', False), ('gps', '30e060', 'GPS', False), ('radio', '30e060', 'RADIO', False),
                                             ('lock', '30e060', 'LOCK', False), ('hold', 'ffb020', 'HOLD', True), ('int', '8ad4ff', 'XTAL', False), ('out', '30e060', 'PULSE', False),
                                             ('flt', 'ff2020', 'SLAVE', True))):
        lamp(m, f, cid, 7.8 + (i % 5) * 1.25, 6.6 - (i // 5) * 1.3, col, lab, 0.42, bl)
    sel(m, f, 's_src', 3.8, 3.4, 'SOURCE', ['GPS', 'RADIO', 'INTERNAL'], 1.0)
    sel(m, f, 's_pulse', 5.6, 3.4, 'PULSE', ['SEC', '30S', 'MIN'], 1.0)
    sel(m, f, 'k_run', 7.3, 3.4, 'OUTPUT', ['OFF', 'RUN'], 0.8, 'key')
    for i, (cid, col, lab) in enumerate((('sync', '1fa34a', 'SYNC'), ('adv', '2050d0', 'ADV'), ('ret', '2050d0', 'RET'), ('ack', 'f0d020', 'ACK'))):
        push(m, f, cid, 9.0 + i * 1.2, 3.6, col, lab, 0.7)
    push(m, f, 'test', 9.6, 2.0, 'e0e0e0', 'TEST', 0.7)
    push(m, f, 'screen', 11.4, 2.0, '303a48', 'SCREEN', 0.7)
    m.save(item_gui=0.4)
    return m


# ================================================================================================ #25 training simulator
def driver_sim():
    m = Model('driver_sim')
    m.g('pedestals')
    for (x0, x1) in ((-15.5, -8.0), (24.0, 31.5)):
        m.box((x0, 0.8, 0.6), (x1, 11.4, 9.6), DARK, 'pedestal')
        m.box((x0, 0, 0.9), (x1, 0.8, 9.3), 'paint:#141416', 'kick plinth')
        for k in range(3):
            y0 = 1.6 + k * 3.2
            m.box((x0 + 0.3, y0, 0.4), (x1 - 0.3, y0 + 2.9, 0.6), 'paint:#3a3e44', 'cabinet door')
            m.box(((x0 + x1) / 2 - 1.0, y0 + 2.0, 0.1), ((x0 + x1) / 2 + 1.0, y0 + 2.3, 0.4), 'metal:alu', 'handle')
    m.box((-8.0, 1.0, 9.0), (24.0, 11.0, 9.6), DARK, 'modesty panel')
    m.g('cab desk')
    m.box((-16, 11.4, 0), (32, 12.0, 9.4), 'paint:#3c4048', 'desk top')
    m.box((-16, 11.2, -0.3), (32, 12.0, 0), 'paint:#1a1b1e', 'armrest edge')
    m.box((-15.0, 11.45, -0.35), (-9.0, 11.95, -0.3), 'label:INSTRUCTOR', 'edge label', faces=('north',))
    m.box((-4.0, 11.45, -0.35), (12.0, 11.95, -0.3), 'label:DRIVER TRAINING SIMULATOR', 'edge label', faces=('north',))
    m.box((18.0, 11.45, -0.35), (26.0, 11.95, -0.3), 'label:CAB CONTROLS', 'edge label', faces=('north',))
    m.g('master controller')
    m.box((14.0, 12.0, 2.0), (19.0, 14.0, 7.0), 'paint:#1e2024', 'master controller housing')
    m.box((14.2, 14.0, 2.4), (18.8, 14.2, 6.6), 'paint:#3a3e44', 'notch quadrant')
    m.box((20.0, 12.0, 2.0), (24.0, 14.0, 7.0), 'paint:#1e2024', 'brake controller housing')
    m.box((20.2, 14.0, 2.4), (23.8, 14.2, 6.6), 'paint:#3a3e44', 'brake quadrant')
    m.box((25.0, 12.0, 3.0), (30.0, 12.6, 8.0), 'paint:#141416', 'radio handset cradle')
    m.box((25.4, 12.6, 3.4), (26.4, 13.4, 7.6), 'paint:#1e2024', 'handset')
    m.g('bridge + big screen')
    m.box((-16, 12.0, 9.4), (32, 31.0, 15.0), DARK, 'bridge body')
    m.box((-16, 31.0, 7.5), (32, 32.0, 15.0), 'paint:#3c4048', 'canopy')
    m.box((-16, 30.4, 7.6), (32, 31.0, 8.0), 'lamp:fff2d0', 'canopy downlight', faces=('down',))
    for x in (-16, 31.4):
        m.box((x, 12.0, 4.0), (x + 0.6, 31.0, 15.0), 'paint:#1e2024', 'cab window pillar')
    m.box((-10.0, 15.0, 9.3), (26.0, 30.0, 9.4), 'paint:#081018', 'route screen')
    m.box((-10.4, 14.6, 9.32), (26.4, 15.0, 9.4), 'paint:#111214', 'screen bezel')
    m.box((-10.4, 30.0, 9.32), (26.4, 30.4, 9.4), 'paint:#111214', 'screen bezel')
    for x in (-10.4, 26.0):
        m.box((x, 15.0, 9.32), (x + 0.4, 30.0, 9.4), 'paint:#111214', 'screen bezel')
    m.box((-15.4, 13.0, 9.3), (-10.8, 29.0, 9.38), 'paint:#1a1d22', 'speaker grille')
    m.box((26.8, 13.0, 9.3), (31.2, 29.0, 9.38), 'paint:#1a1d22', 'speaker grille')
    m.g('driver seat')
    m.cyl((8.0, 0, -4.0), 1.0, 0.4, 'y', 'paint:#1e2024', 'seat base')
    m.cyl((8.0, 0.4, -4.0), 0.4, 5.6, 'y', STEEL, 'seat column')
    m.box((5.4, 6.0, -6.6), (10.6, 7.2, -1.4), 'paint:#2a3a5a', 'seat cushion')
    m.box((5.4, 7.2, -7.2), (10.6, 13.6, -6.2), 'paint:#2a3a5a', 'seat back')
    m.box((5.2, 8.0, -6.0), (5.6, 8.4, -2.0), 'paint:#1e2024', 'armrest')
    m.box((10.4, 8.0, -6.0), (10.8, 8.4, -2.0), 'paint:#1e2024', 'armrest')
    m.g('leads')
    m.terminal_12v(30.2, 4.0, 5.0, 'east')
    m.box((-16.0, 3.0, 3.0), (-15.5, 6.0, 7.0), 'paint:#3a3f44', 'mains inlet', west='danger:240V')
    m.gland(28.0, 0.8, 8.0, 'down', r=0.45, cable='cable:1d1d21')
    m.g('cab shell')
    for x in (-16.0, 31.0):
        m.box((x, 12.0, 15.0), (x + 1.0, 31.0, 16.0), 'paint:#1e2024', 'rear pillar')
        m.box((x, 0, 9.6), (x + 1.0, 12.0, 16.0), 'paint:#2a2e34', 'side wall')
        m.box((x if x < 0 else x + 0.98, 4.0, 11.0), (x + 0.02 if x < 0 else x + 1.0, 10.0, 15.0), 'louvre', 'cooling vent', faces=('west',) if x < 0 else ('east',))
    m.box((-15.0, 31.0, 8.0), (31.0, 31.4, 8.4), 'paint:#2a2e34', 'sun visor rail')
    for x in (-8.0, 6.0, 20.0):
        m.box((x, 30.0, 6.0), (x + 4.0, 30.4, 7.6), 'paint:#3a3e44', 'sun visor', rot={'axis': 'x', 'angle': 22.5, 'origin': [x + 2.0, 30.2, 7.6]})
    m.box((4.0, 31.4, 10.0), (12.0, 32.0, 14.0), 'paint:#2a2e34', 'wiper motor cover')
    m.g('instructor side')
    m.box((-15.6, 12.0, 6.6), (-12.0, 12.3, 9.0), 'paint:#e8e4d0', 'route card')
    m.box((-15.4, 12.3, 6.8), (-12.2, 12.35, 8.8), 'label:ROUTE CARD', 'route card print', faces=('up',))
    m.box((-15.6, 12.3, 8.6), (-12.0, 12.6, 9.0), STEEL, 'card clip')
    m.cyl((-10.6, 12.0, 8.0), 0.6, 0.3, 'y', 'paint:#1e2024', 'desk lamp base')
    m.cyl((-10.6, 12.3, 8.0), 0.15, 3.0, 'y', STEEL, 'desk lamp arm')
    m.box((-11.6, 15.0, 6.4), (-9.6, 15.8, 8.4), 'paint:#1e2024', 'desk lamp head')
    m.box((-11.4, 14.9, 6.6), (-9.8, 15.0, 8.2), 'lamp:fff2d0', 'desk lamp lens', faces=('down',))
    m.g('under desk')
    m.box((-14.8, 0.8, 9.6), (-9.0, 10.0, 15.4), 'paint:#1e2024', 'sim computer')
    m.louvres(-14.6, -9.2, 2.0, 9.0, 15.4, n=6)
    m.box((24.8, 0.8, 9.6), (30.8, 10.0, 15.4), 'paint:#1e2024', 'visual computer')
    m.louvres(25.0, 30.6, 2.0, 9.0, 15.4, n=6)
    for x in (-6.0, 4.0, 14.0):
        m.box((x, 10.4, 9.0), (x + 6.0, 11.0, 9.6), 'paint:#24272b', 'cable duct')
    m.g('cab floor')
    m.box((2.0, 0, -9.0), (14.0, 0.4, 0), 'paint:#4a4c50', 'cab floor plate')
    for x in range(2, 14, 2):
        m.box((x, 0.4, -9.0), (x + 1.2, 0.45, 0), 'paint:#5a5c60', 'chequer')
    m.box((2.0, 0, -9.4), (14.0, 0.6, -9.0), 'haz', 'floor edge', faces=('north', 'up'))
    m.handrail([(1.6, -9.0), (1.6, -1.0)], 0.4, h=6.0, r=0.15, mat='paint:ral1023')
    up = m.surface('up', face=16 - 12.0, title='SIMULATOR DESK')
    sel(m, up, 'k_desk', -14.0, 2.2, 'DESK', ['OFF', 'ON'], 1.0, 'key')
    sel(m, up, 's_scn', -11.0, 2.2, 'SCENARIO', ['1', '2', '3', '4', '5'], 1.4)
    push(m, up, 'start', -7.8, 2.2, '30c040', 'START', 1.3)
    push(m, up, 'abort', -5.4, 2.2, 'd02020', 'ABORT', 1.3)
    push(m, up, 'screen', -3.0, 2.2, '303a48', 'SIT + DRIVE', 1.3)
    push(m, up, 'test', -0.8, 2.2, 'e0e0e0', 'TEST', 0.9)
    for i, (cid, col, lab, bl) in enumerate((('pwr', '30e060', '12V', False), ('sup', '30e060', 'MAINS', False), ('deskl', '30e060', 'DESK', False), ('run', '30e060', 'RUN', True),
                                             ('aws', 'ffd020', 'AWS', False), ('dsd', 'ff8020', 'DSD', False), ('eb', 'ff2020', 'EMERG', False), ('slip', 'ffb020', 'SLIP', False),
                                             ('pass', '30e060', 'PASS', False), ('fail', 'ff2020', 'FAIL', False))):
        lamp(m, up, cid, -14.0 + i * 1.6, 6.4, col, lab, 0.55, bl)
    sel(m, up, 's_pwr', 16.5, 4.5, 'POWER', ['0', '1', '2', '3', '4', '5', '6', '7', '8'], 2.0, 'selector')
    sel(m, up, 's_brk', 22.0, 4.5, 'BRAKE', ['REL', '1', '2', '3', '4', '5', '6', '7', 'EMERG'], 2.0, 'selector')
    push(m, up, 'p_aws', 2.0, 3.0, '202020', 'AWS ACK', 1.4)
    push(m, up, 'p_vig', 5.0, 3.0, 'ffb020', 'DSD', 1.4)
    push(m, up, 'p_horn', 8.0, 3.0, '505860', 'HORN', 1.1)
    mush(m, up, 'p_eb', 11.0, 3.0, 'EMERG', 1.6)
    br = m.surface('north', face=9.28, title='ROUTE SCREEN')
    digits(m, br, 'd_spd', 8.0, 25.0, 14.0, 4.0, 'ffd040', '%s KM/H')
    digits(m, br, 'd_state', 8.0, 20.4, 30.0, 1.4, '7cf29a')
    digits(m, br, 'd_scn', 0.0, 17.6, 16.0, 1.1, '8ad4ff')
    digits(m, br, 'd_score', 17.0, 17.6, 12.0, 1.1, 'ffb020', 'SCORE %s')
    digits(m, br, 'd_who', 8.0, 28.6, 20.0, 1.0, 'e8e8e8')
    gauge(m, br, 'g_spd', -13.0, 23.0, 'SPEED', 3.0)
    gauge(m, br, 'g_bc', 29.0, 23.0, 'BRAKE CYL', 3.0)
    m.save(item_gui=0.3)
    return m


# ================================================================================================ #21 RCO beltpack (item)
def beltpack():
    m = Model('rco_beltpack')
    m.g('case')
    m.box((3.0, 1.0, 5.0), (13.0, 13.0, 10.0), YEL, 'beltpack case')
    for (x, y) in ((2.6, 0.6), (11.6, 0.6), (2.6, 11.6), (11.6, 11.6)):
        m.box((x, y, 4.6), (x + 1.8, y + 1.8, 10.4), 'rubber', 'corner bumper')
    m.box((3.4, 1.4, 4.9), (12.6, 12.6, 5.0), 'paint:#d0a010', 'front bezel', faces=('north',))
    m.g('display')
    m.box((4.0, 9.0, 4.8), (12.0, 12.2, 4.9), 'paint:#9db58a', 'LCD')
    m.box((4.0, 12.2, 4.8), (12.0, 12.5, 4.9), 'label:RCO', 'brand strip', faces=('north',))
    m.g('controls')
    m.cyl((5.2, 7.2, 4.0), 0.9, 0.8, 'z', 'paint:#202020', 'direction switch')
    m.box((4.95, 7.1, 3.8), (5.45, 8.4, 4.0), 'paint:#e8e8e8', 'direction pointer')
    m.cyl((8.0, 7.2, 4.0), 1.0, 0.8, 'z', 'paint:#202020', 'speed selector')
    m.box((7.75, 7.1, 3.8), (8.25, 8.5, 4.0), 'paint:#e8e8e8', 'speed pointer')
    m.cyl((10.8, 7.2, 4.0), 0.9, 0.8, 'z', 'paint:#5a2a10', 'independent brake')
    for i, col in enumerate(('#505860', '#505860', '#505860', '#1a7a3a', '#2050a0')):
        m.box((4.0 + i * 1.65, 4.4, 4.5), (5.2 + i * 1.65, 5.4, 4.8), 'paint:' + col, 'push button')
    m.cyl((8.0, 2.6, 3.8), 1.2, 1.0, 'z', 'paint:#c01818', 'E-stop')
    m.box((6.2, 1.6, 4.6), (9.8, 3.6, 4.9), 'paint:#f0d020', 'E-stop collar')
    m.g('antenna + clip')
    m.cyl((11.5, 13.0, 7.5), 0.35, 3.0, 'y', 'rubber', 'antenna')
    m.cyl((11.5, 16.0, 7.5), 0.5, 0.6, 'y', 'rubber', 'antenna tip')
    m.box((5.0, 4.0, 10.0), (11.0, 11.0, 10.8), 'paint:#2a2a2a', 'belt clip')
    m.box((4.0, 13.0, 6.0), (6.0, 13.6, 9.0), 'paint:#2a2a2a', 'battery latch')
    m.box((3.0, 3.0, 9.98), (13.0, 5.0, 10.0), 'label:REMOTE CONTROL', 'back label', faces=('south',))
    m.g('details')
    for (x, y) in ((3.6, 1.6), (12.4, 1.6), (3.6, 12.4), (12.4, 12.4)):
        m.bolt(x, y, 5.0, 'north', 0.3)
    for k in range(5):
        m.box((2.9, 2.0 + k * 2.0, 6.0), (3.0, 2.8 + k * 2.0, 9.0), 'rubber', 'side grip')
        m.box((13.0, 2.0 + k * 2.0, 6.0), (13.1, 2.8 + k * 2.0, 9.0), 'rubber', 'side grip')
    for x in (3.0, 12.4):
        m.box((x - 0.4, 10.0, 7.0), (x + 1.0, 11.4, 8.0), STEEL, 'harness ring')
    m.box((4.4, 9.4, 4.75), (5.4, 9.8, 4.8), 'lamp:30e060', 'link LED', faces=('north',))
    m.box((10.6, 9.4, 4.75), (11.6, 9.8, 4.8), 'lamp:ff3030', 'fault LED', faces=('north',))
    for k in range(4):
        m.box((4.0 + k * 2.0, 13.0, 6.2), (5.4 + k * 2.0, 13.3, 8.8), 'paint:#c8a010', 'battery rib')
    for i in range(3):
        m.box((4.1 + i * 1.1, 8.2, 4.85), (4.3 + i * 1.1, 8.6, 4.9), 'paint:#202020', 'switch legend dot')
    m.box((6.2, 5.8, 4.9), (9.8, 6.3, 4.95), 'label:SPEED', 'speed legend', faces=('north',))
    m.box((3.4, 0.6, 6.0), (12.6, 1.0, 9.0), 'rubber', 'bottom bumper')
    m.box((3.4, 13.0, 9.2), (12.6, 13.4, 10.0), 'rubber', 'top bumper')
    m.save(item_gui=0.8)
    return m


def P(meta): return {'item': 'irextras:part', 'data': meta}
PART = {'plate': 0, 'frame': 1, 'circuit': 2, 'motor': 3, 'hydraulic': 4, 'seal': 12}

RECIPES = {
    'rail_crane_250': (['BPB', 'HFH', 'WCW'], {'B': 'minecraft:iron_block', 'P': 'minecraft:piston', 'H': P(PART['hydraulic']), 'F': P(PART['frame']), 'W': 'minecraft:rail', 'C': P(PART['circuit'])}),
    'flange_lubricator': (['SHS', 'MCM', 'PRP'], {'S': 'minecraft:slime_ball', 'H': 'minecraft:hopper', 'M': P(PART['motor']), 'C': P(PART['circuit']), 'P': P(PART['plate']), 'R': 'minecraft:rail'}),
    'master_clock': (['GCG', 'DQD', 'PRP'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:clock', 'D': 'minecraft:daylight_detector', 'Q': 'minecraft:quartz', 'P': P(PART['plate']), 'R': 'minecraft:redstone'}),
    'driver_sim': (['GGG', 'CRC', 'PEP'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'R': 'minecraft:repeater', 'P': ('minecraft:planks', 0), 'E': P(PART['circuit'])}),
    'rco_beltpack': ([' A ', 'BCB', 'YRY'], {'A': 'minecraft:redstone_torch', 'B': 'minecraft:stone_button', 'C': P(PART['circuit']), 'Y': ('minecraft:dye', 11), 'R': 'minecraft:redstone'}),
}

LANG = {
    'tile.irextras.rail_crane_250.name': '250 t Breakdown Crane', 'tile.irextras.flange_lubricator.name': 'Flange Lubricator',
    'tile.irextras.master_clock.name': 'Master Clock', 'tile.irextras.driver_sim.name': 'Driver Training Simulator',
    'item.irextras.rco_beltpack.name': 'RCO Remote Control Beltpack',
}

if __name__ == '__main__':
    counts = {}
    for fn in (rail_crane, flange_lube, master_clock, driver_sim, beltpack):
        mm = fn()
        counts[mm.name] = len(mm.boxes)
    for n in ('rail_crane_250', 'flange_lubricator', 'master_clock', 'driver_sim'):
        facing_states(n)
    json.dump({'parent': 'irextras:block/rco_beltpack'}, open(os.path.join(ASSETS, 'models/item', 'rco_beltpack.json'), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# batch 6 E #21-25: RCO beltpack, breakdown crane, flange lubricator, master clock, training simulator\n' + '\n'.join(add) + '\n')
    print('batch 6 E assets written')
    for k, v in counts.items(): print('  %-22s %d boxes' % (k, v))
