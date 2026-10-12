#!/usr/bin/env python3
"""Batch 5 #2 + #6 (her ask 10-11: "do all of it, Blockbench and buttons, functional screens, real looking"):
the steam servicing depot - Coaling Tower + Coal Receiving Pit (multiblock), Water Column, Ash Pit, Sand Drier and Tower,
Boiler Washout Plant - and the battery / hydrogen train plant - Train Charging Post, Hydrogen Compressor, Hydrogen
Storage Tubes, Hydrogen Dispenser. Models face north (the operator's side; the track is to the south), controls by
u = 16 - x. Live controls match steam/Tile*.java and traction/Tile*.java ids. The coaling skip + chute apron and the
water crane arm are drawn live by steam/client/SteamRenderer (not baked here). Also writes blockstates, item models,
the Locomotive Ash item texture, recipes and lang lines."""
import json, math, os, random
from PIL import Image
from bbkit import Model, ASSETS
import cabkit
from gen_bb_economy import X
from gen_bb_depot import lamp_row, push_row, estop_plate


def gauges(m, sf, items, u0, v, step=2.5, size=2.0):
    """dial gauges with a label under each: items = (id, label)"""
    for i, (cid, lab) in enumerate(items):
        u = u0 + i * step
        m.ctl(sf, 'gauge', cid, u, v, w=size, h=size, col='f2f0e6', label=lab, max=100)
        m.ctl(sf, 'label', '', u, v - size / 2 - 0.45, w=0, h=0, col='e8e8e8', label=lab)


def lab(m, sf, u, v, text):
    m.ctl(sf, 'label', '', u, v, w=0, h=0, col='e8e8e8', label=text)


def screen(m, x0, x1, y0, y1, z, name='screen'):
    """a dark display window in a bezel on a north face at z"""
    m.box((x0 - 0.3, y0 - 0.3, z - 0.1), (x1 + 0.3, y1 + 0.3, z), 'paint:#1c1d20', name + ' bezel')
    m.box((x0, y0, z - 0.15), (x1, y1, z - 0.1), 'paint:#07120c', name)


def beacon(m, x, y, z, col='ff8a00'):
    m.cyl((x, y, z), 0.6, 0.5, 'y', 'paint:#2a2a2a', 'beacon base')
    m.cyl((x, y + 0.5, z), 0.5, 1.0, 'y', 'lamp:' + col, 'beacon dome')


def detector(m, x, y, z):
    """a gas detector head on a north face"""
    m.box((x - 0.6, y, z - 0.8), (x + 0.6, y + 1.6, z), 'paint:#f0c818', 'gas detector')
    m.cyl((x, y - 0.6, z - 0.4), 0.35, 0.6, 'y', 'mesh', 'sensor head')
    m.box((x - 0.2, y + 1.0, z - 0.85), (x + 0.2, y + 1.3, z - 0.8), 'lamp:30e060', 'detector led')


# ===================================================================================================== Coaling Tower
def coaling_tower():
    """the base: winding house, skip pit and the foot of the guides, leg footings, hoist MCC and the coaling desk
    (the legs and guides up to the bunker, the skip, rope and chute apron are drawn live by SteamRenderer)"""
    m = Model('coaling_tower')
    m.g('legs')
    for (x, z) in ((0.4, 0.4), (14.0, 0.4), (0.4, 14.0), (14.0, 14.0)):
        m.box((x, 0, z), (x + 1.6, 32.0, z + 1.6), 'concrete', 'leg')
        m.box((x - 0.3, 0, z - 0.3), (x + 1.9, 0.8, z + 1.9), 'concrete', 'leg footing')
    for y in (14.0, 30.0):
        m.box((0.4, y, 14.2), (15.6, y + 1.2, 15.4), 'concrete', 'tie beam')
        m.box((0.6, y, 2.0), (1.8, y + 1.2, 14.0), 'concrete', 'tie beam')
        m.box((14.2, y, 2.0), (15.4, y + 1.2, 14.0), 'concrete', 'tie beam')
    m.box((0.4, 30.0, 0.6), (15.6, 31.2, 1.8), 'concrete', 'tie beam')
    m.box((1.0, 26.0, -0.5), (15.0, 29.0, -0.4), 'danger:FALLING COAL|KEEP CLEAR', 'danger board')
    m.g('winding house')
    m.box((2.0, 20.0, 4.0), (14.0, 29.6, 13.0), 'paint:#6a5a4a', 'winding house')
    m.box((1.6, 29.6, 3.6), (14.4, 30.0, 13.4), 'paint:#3a3a3a', 'winding house roof')
    for x in (3.0, 7.0, 11.0):
        m.box((x, 25.0, 3.9), (x + 2.4, 27.6, 4.0), 'glass', 'window')
    m.box((4.0, 21.0, 3.9), (12.0, 23.0, 4.0), 'label:SKIP HOIST', 'house lettering')
    m.cyl((9.0, 21.0, 8.5), 1.4, 4.0, 'x', 'paint:#2a5a8a', 'hoist motor 55 kW')
    m.cyl((5.4, 21.0, 8.5), 1.8, 3.4, 'x', 'paint:#3a3f44', 'hoist drum')
    m.cyl((13.2, 20.4, 8.5), 0.9, 0.6, 'x', 'paint:#c82020', 'brake drum')
    m.box((12.8, 20.0, 7.4), (13.8, 22.6, 9.6), 'paint:#c8a020', 'brake thruster')
    m.g('skip pit + guides')
    for z in (5.2, 10.4):
        m.box((16.0, 0, z), (17.2, 32.0, z + 0.6), 'galv', 'guide channel')
    for y in range(4, 32, 5):
        m.box((16.0, y, 5.2), (16.6, y + 0.5, 11.0), 'galv', 'guide tie')
    m.box((15.2, 0, 4.4), (19.6, 2.0, 12.0), 'concrete', 'skip pit sill')
    m.box((15.6, 2.0, 4.6), (19.4, 2.4, 11.8), 'paint:#2a2a2a', 'skip buffer')
    m.box((16.2, 2.4, 3.6), (19.0, 3.0, 4.6), 'paint:#c8a020', 'loading chute lip')
    m.g('access')
    m.ladder(0.6, 2.2, 0.8, 32.0, 15.6)
    for y in (8.0, 16.0, 24.0):
        m.box((0.2, y, 15.4), (2.6, y + 0.2, 16.4), 'galv', 'ladder hoop')
    m.g('weigh beam')
    m.box((14.6, 8.0, -0.4), (15.8, 12.0, 0.6), 'paint:#3a3f44', 'weigh beam case')
    m.box((14.7, 10.0, -0.45), (15.7, 11.6, -0.4), 'gauge:scale', 'weigh dial')
    m.g('mcc')
    m.box((0.6, 0, 0.8), (5.6, 20.0, 4.6), 'paint:#3a4a5a', 'hoist MCC')
    m.box((0.6, 20.0, 0.6), (5.6, 20.6, 4.8), 'paint:#2a3540', 'MCC hood')
    cabkit.fit(m, 1.0, 5.2, 1.0, 19.4, 0.8, col='paint:#3a4a5a', title='HOIST MCC|55 KW', volts='400V 50HZ')
    m.box((-0.8, 6.0, 5.6), (0.4, 9.0, 8.4), 'paint:#3a3f44', 'isolator box', west='danger:400V')
    m.cyl((-1.1, 7.6, 7.0), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    m.cyl((-0.2, 0, 7.0), 0.32, 6.0, 'y', 'cable:202020', 'armoured mains cable')
    m.g('control desk')
    m.box((6.0, 0, 0.8), (15.4, 13.0, 4.4), 'paint:#2f3d4a', 'desk body')
    m.box((6.0, 13.0, 1.4), (15.4, 18.6, 4.4), 'paint:#2b3743', 'desk upstand')
    m.box((5.8, 18.6, 0.8), (15.6, 19.2, 4.6), 'paint:#1c242c', 'desk hood')
    m.box((6.2, 0, 0.6), (15.2, 1.0, 0.8), 'paint:#1c1d20', 'kick plate')
    screen(m, 6.6, 15.0, 11.8, 13.6, 0.8, 'skip screen')
    m.nameplate(6.4, 18.0, 10.0, 18.5, 1.4, 'COAL CTL')
    m.terminal_12v(13.6, 13.6, 4.4, 'south')
    f = m.surface('north', face=0.78, title='COALING DESK')
    m.ctl(f, 'digits', 'line1', X(10.8), 13.1, w=8.2, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(10.8), 12.3, w=8.2, h=0.6, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'tons', X(13.2), 10.6, w=3.4, h=1.1, col='ffd040', label='LOADED', fmt='%s T')
    m.ctl(f, 'digits', 'bunk', X(8.6), 10.6, w=3.6, h=1.1, col='ffd040', label='BUNKER', fmt='%s')
    lab(m, f, X(13.2), 9.7, 'LOADED'); lab(m, f, X(8.6), 9.7, 'IN BUNKER')
    gauges(m, f, [('bunkerg', 'BUNKER'), ('pitg', 'PIT'), ('skipg', 'SKIP'), ('roomg', 'ENGINE')], X(14.1), 7.9, step=2.4, size=1.9)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('formed', '8ad4ff', 'FORMED'), ('hoistl', 'ffb020', 'HOIST', True),
                    ('topl', 'ffb020', 'TOP'), ('botl', '30e060', 'BOTTOM')], X(14.4), 5.6, step=1.6, size=0.5)
    lamp_row(m, f, [('autol', '8ad4ff', 'AUTO'), ('chutel', 'ffb020', 'CHUTE'), ('flow', '30e060', 'FLOW', True), ('low', 'ff3030', 'LOW'),
                    ('full', 'ffb020', 'FULL'), ('locol', '8ad4ff', 'ENGINE')], X(14.4), 4.2, step=1.6, size=0.5)
    push_row(m, f, [('hoist', '1fa34a', 'HOIST'), ('discharge', 'e08020', 'DISCH'), ('stop', 'd02020', 'STOP'), ('test', 'b0b0b0', 'TEST')], X(14.0), 2.4, step=2.0, size=0.9)
    u = m.surface('north', face=1.38, title='COALING UPSTAND')
    m.ctl(u, 'selector', 's_qty', X(13.8), 16.4, w=1.2, h=1.2, col='202020', label='QTY', pos=['1/4', '1/2', 'FULL'])
    m.ctl(u, 'key', 'k_auto', X(11.6), 16.4, w=1.1, h=1.1, col='c0a040', label='SKIP', pos=['MAN', 'AUTO'])
    m.ctl(u, 'lever', 'm_chute', X(9.2), 16.0, w=0.8, h=2.6, col='e8c020', label='CHUTE', pos=['UP', 'DOWN'])
    estop_plate(m, u, X(7.2), 16.4)
    for (x, t) in ((13.8, 'QTY'), (11.6, 'SKIP'), (9.2, 'CHUTE')): lab(m, u, X(x), 14.6, t)
    m.g('locker')
    m.box((15.0, 0.8, 1.0), (15.95, 6.0, 3.6), 'paint:#4c535a', 'locker', east='paint:#5a6268')
    loc = m.surface('east', face=-0.02, title='LOCKER')
    m.ctl(loc, 'zone', 'locker', 13.7, 3.4, w=2.6, h=4.6, col='000000', label='')
    m.save(item_gui=0.4)


def coal_bunker():
    """the elevated bunker: hopper cone below (-16..0), the bunker, tipping horns + head sheave on top"""
    m = Model('coal_bunker')
    m.g('hopper cone')
    for i, y in enumerate((-16.0, -12.0, -8.0, -4.0)):
        m.box((5.0 - i * 1.4, y, 5.0 - i * 1.4), (11.0 + i * 1.4, y + 4.0, 11.0 + i * 1.4), 'concrete', 'hopper step')
    m.box((4.4, -14.0, 13.6), (11.6, -10.0, 16.2), 'paint:#3a3f44', 'gate housing')
    m.box((4.6, -13.8, 16.2), (11.4, -10.4, 16.5), 'paint:#c8a020', 'gate leaf')
    m.cyl((3.4, -14.6, 15.0), 0.45, 4.0, 'y', 'metal:steel', 'gate air cylinder')
    m.cyl((3.4, -10.6, 15.0), 0.2, 1.2, 'y', 'metal:steel', 'piston rod')
    m.box((7.4, -10.2, 16.0), (8.6, -9.4, 17.0), 'metal:steel', 'apron hinge')
    m.cyl((12.6, -16.0, 15.2), 0.25, 16.0, 'y', 'cable:202020', 'air line')
    for (x, z) in ((0.4, 0.4), (14.0, 0.4), (0.4, 14.0), (14.0, 14.0)):
        m.box((x, -16.0, z), (x + 1.6, 0, z + 1.6), 'concrete', 'leg head')
    m.g('bunker')
    m.box((-0.4, 0, -0.4), (16.4, 26.0, 16.4), 'concrete', 'bunker')
    for x in (-0.6, 3.6, 7.6, 11.6, 15.6):
        m.box((x, 0, -0.6), (x + 0.8, 26.0, -0.4), 'concrete', 'pilaster')
        m.box((x, 0, 16.4), (x + 0.8, 26.0, 16.6), 'concrete', 'pilaster')
    for y in (6.0, 12.0, 18.0):
        m.box((-0.6, y, -0.6), (16.6, y + 0.4, -0.4), 'paint:#8a8a82', 'shuttering line')
    m.box((-0.8, 25.4, -0.8), (16.8, 26.4, 16.8), 'concrete', 'bunker cornice')
    m.box((1.0, 8.0, -0.65), (15.0, 11.6, -0.55), 'label:COALING PLANT', 'bunker lettering')
    m.box((1.0, 8.0, 16.55), (15.0, 11.6, 16.65), 'label:COALING PLANT', 'bunker lettering', faces=('south',), south='label:COALING PLANT')
    m.box((3.0, 2.0, -0.65), (13.0, 4.0, -0.55), 'plate:CAPACITY 200 T', 'capacity plate')
    m.g('head + tipping horns')
    m.box((16.0, 16.0, 4.0), (19.4, 17.0, 12.2), 'galv', 'tipping horns')
    m.box((16.4, 17.0, 4.8), (18.6, 20.0, 11.4), 'paint:#3a3f44', 'tipping chute')
    for z in (5.2, 10.4):
        m.box((16.0, -16.0, z), (17.2, 30.0, z + 0.6), 'galv', 'guide channel')
    m.box((16.2, 26.0, 7.0), (17.8, 30.0, 9.0), 'galv', 'sheave bracket')
    m.cyl((17.0, 28.0, 8.0), 1.6, 0.6, 'z', 'paint:#c82020', 'head sheave', cap='metal:steel')
    m.g('bunker details')
    for y in (6.0, 12.0, 18.0):
        m.box((-0.6, y, 16.4), (16.6, y + 0.4, 16.6), 'paint:#8a8a82', 'shuttering line')
        m.box((-0.6, y, -0.6), (-0.4, y + 0.4, 16.6), 'paint:#8a8a82', 'shuttering line')
    for z in (3.6, 7.6, 11.6):
        m.box((-0.6, 0, z), (-0.4, 26.0, z + 0.8), 'concrete', 'pilaster')
    m.box((-0.65, 14.0, 6.0), (-0.4, 19.0, 10.0), 'paint:#3a3f44', 'inspection door')
    m.box((-0.75, 16.2, 6.4), (-0.65, 16.8, 7.2), 'metal:steel', 'door bar')
    for x in (4.8, 6.4, 8.0, 9.6, 11.2):
        m.bolt(x, -10.6, 16.5, 'south', 0.35)
    m.box((4.0, -16.0, 4.0), (12.0, -15.6, 12.0), 'paint:#2a2a2a', 'outlet throat')
    m.cyl((0.2, 0, 0.2), 0.15, 30.0, 'y', 'metal:copper', 'lightning conductor')
    m.g('roof')
    m.box((0.4, 26.4, 0.4), (15.6, 27.0, 15.6), 'paint:#3a3a3a', 'bunker roof')
    for x in (3.0, 11.0):
        m.box((x, 27.0, 3.0), (x + 2.0, 28.4, 5.0), 'louvre', 'dust vent')
        m.box((x - 0.2, 28.4, 2.8), (x + 2.2, 28.8, 5.2), 'paint:#3a3a3a', 'vent cap')
    m.box((5.0, 27.0, 10.0), (9.0, 27.4, 13.0), 'paint:#3a3f44', 'roof hatch')
    m.handrail([(0.4, 0.4), (15.6, 0.4), (15.6, 15.6), (0.4, 15.6), (0.4, 0.4)], 27.0, h=3.0)
    beacon(m, 8.0, 27.0, 8.0)
    m.save(item_gui=0.3)


# ===================================================================================================== Coal Pit
def coal_pit():
    m = Model('coal_pit')
    m.g('pit')
    m.box((0, 0, 0), (16, 2.0, 16), 'concrete', 'pit floor')
    for (a, b) in (((0, 2, 0), (16, 7, 1.2)), ((0, 2, 14.8), (16, 7, 16)), ((0, 2, 0), (1.2, 7, 16)), ((14.8, 2, 0), (16, 7, 16))):
        m.box(a, b, 'concrete', 'pit wall')
    m.box((1.2, 2.0, 1.2), (14.8, 5.2, 14.8), 'paint:#16181a', 'coal in the pit')
    for i in range(14):
        x, z = 1.6 + random.Random(i).random() * 12, 1.6 + random.Random(i + 40).random() * 12
        m.box((x, 5.2, z), (x + 0.9, 5.7, z + 0.8), 'paint:#22262a', 'coal lump')
    m.g('grating')
    for x in range(1, 16, 1):
        m.box((x + 0.0, 6.6, 1.2), (x + 0.25, 7.0, 14.8), 'galv', 'grating bar')
    for z in (1.2, 5.6, 10.2, 14.4):
        m.box((1.2, 6.4, z), (14.8, 6.6, z + 0.4), 'galv', 'grating bearer')
    m.box((-0.2, 7.0, -0.2), (16.2, 7.6, 1.2), 'haz', 'kerb nosing')
    m.g('skip loader')
    m.box((14.8, 0, 4.6), (16.4, 6.0, 11.4), 'paint:#3a3f44', 'measuring hopper')
    m.box((16.0, 1.0, 5.4), (16.6, 4.0, 10.6), 'paint:#c8a020', 'loading door')
    m.cyl((16.2, 4.4, 8.0), 0.35, 2.8, 'z', 'metal:steel', 'door shaft')
    m.g('pit fittings')
    m.ladder(1.4, 3.0, 2.0, 7.0, 14.4)
    for x in range(2, 15, 3):
        for z in (1.2, 14.4):
            m.box((x, 7.0, z), (x + 0.6, 7.2, z + 0.4), 'metal:steel', 'grating clip')
    m.cyl((13.0, 2.0, 2.4), 0.6, 2.4, 'y', 'paint:#2a5a8a', 'sump pump')
    m.cyl((13.0, 4.4, 2.4), 0.25, 3.0, 'y', 'metal:steel', 'sump riser')
    m.box((12.6, 2.0, 12.6), (14.2, 2.4, 14.2), 'mesh', 'drain grate')
    for z in (-0.6, 16.0):
        m.box((3.0, 7.0, z), (13.0, 7.4, z + 0.6), 'paint:#c8a020', 'wagon stop board')
    m.box((15.0, 6.0, -0.1), (16.0, 7.0, 0.0), 'lamp:ffb020', 'pit lamp')
    m.box((-0.2, 7.6, 15.0), (16.2, 8.0, 16.2), 'haz', 'rear nosing')
    m.box((-0.2, 7.6, 1.2), (1.2, 8.0, 15.0), 'haz', 'side nosing')
    m.box((14.8, 7.6, 1.2), (16.2, 8.0, 15.0), 'haz', 'side nosing')
    m.g('indicator')
    m.box((2.0, 7.0, 0.0), (9.0, 13.0, 1.2), 'paint:#3a3f44', 'level panel')
    m.box((2.2, 12.6, -0.1), (8.8, 13.0, 0.0), 'plate:PIT LEVEL', 'level title')
    m.terminal_12v(11.0, 7.0, 0.6, 'north')
    m.box((10.0, 7.6, -0.1), (14.0, 10.6, 0.0), 'danger:NO ENTRY|PIT', 'pit danger sign')
    f = m.surface('north', face=-0.02, title='PIT LEVEL')
    m.ctl(f, 'gauge', 'pitg', X(3.6), 10.0, w=2.2, h=2.2, col='f2f0e6', label='PIT %', max=100)
    lab(m, f, X(3.6), 8.5, 'PIT %')
    m.ctl(f, 'digits', 'coal', X(6.8), 11.2, w=3.6, h=0.9, col='ffd040', label='', fmt='%s COAL')
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('formed', '8ad4ff', 'FORMED')], X(6.0), 9.2, step=1.6, size=0.55)
    g = m.surface('up', face=8.97, title='GRATING')
    m.ctl(g, 'zone', 'pit', 8.0, 8.0, w=13.0, h=13.0, col='000000', label='')
    m.save(item_gui=0.5)


# ===================================================================================================== Water Column
def water_column():
    m = Model('water_column')
    m.g('base')
    m.box((3.0, 0, 3.0), (13.0, 1.2, 13.0), 'concrete', 'plinth')
    m.cyl((8.0, 1.2, 8.0), 3.4, 1.4, 'y', 'paint:#24382c', 'base flange')
    for i in range(8):
        a = i * math.pi / 4
        m.bolt(8 + 3.0 * math.cos(a), 2.6, 8 + 3.0 * math.sin(a), 'up', 0.45)
    m.g('column')
    m.cyl((8.0, 2.6, 8.0), 2.2, 24.0, 'y', 'paint:#2f4a3a', 'cast column')
    for y in (5.0, 10.0, 15.0, 20.0, 25.0):
        m.cyl((8.0, y, 8.0), 2.45, 0.5, 'y', 'paint:#24382c', 'column band')
    m.cyl((8.0, 26.6, 8.0), 2.8, 1.2, 'y', 'paint:#24382c', 'capital')
    m.cyl((8.0, 27.8, 8.0), 1.6, 1.2, 'y', 'metal:brass', 'swivel bearing')
    m.box((5.8, 12.0, 5.75), (10.2, 14.0, 5.8), 'label:WATER', 'column lettering')
    m.g('valve')
    m.cyl((8.0, 3.0, 4.6), 0.4, 1.4, 'z', 'metal:steel', 'valve spindle')
    m.cyl((8.0, 4.0, 3.6), 2.0, 0.4, 'z', 'paint:#c82020', 'hand wheel rim')
    for (dx, dy) in ((1, 0), (0, 1)):
        m.box((8.0 - 1.9 * dx - 0.15 * dy, 4.0 - 1.9 * dy - 0.15 * dx, 3.6), (8.0 + 1.9 * dx + 0.15 * dy, 4.0 + 1.9 * dy + 0.15 * dx, 3.9), 'paint:#c82020', 'wheel spoke')
    m.g('frost brazier')
    m.box((12.0, 1.2, 10.8), (14.0, 2.0, 12.8), 'paint:#2a2a2a', 'brazier pan')
    for (x, z) in ((12.2, 11.0), (13.6, 12.4)):
        m.box((x, 2.0, z), (x + 0.2, 4.4, z + 0.2), 'paint:#2a2a2a', 'brazier bar')
    m.g('instrument cabinet')
    m.box((0.6, 1.2, 0.2), (7.2, 14.0, 3.0), 'paint:#2f4a3a', 'pump starter + meter cabinet')
    m.box((0.4, 14.0, 0.0), (7.4, 14.6, 3.2), 'paint:#24382c', 'cabinet hood')
    screen(m, 1.2, 6.6, 11.6, 13.4, 0.2, 'meter')
    m.terminal_12v(4.0, 1.4, 3.0, 'south')
    m.box((-0.6, 4.0, 0.6), (0.6, 7.0, 2.6), 'paint:#3a3f44', 'isolator box', west='danger:400V')
    m.cyl((-0.9, 5.6, 1.6), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    m.cyl((0.0, 0, 1.6), 0.3, 4.0, 'y', 'cable:202020', 'armoured mains cable')
    m.g('feed pump')
    m.cyl((9.6, 1.2, 1.4), 1.0, 3.0, 'x', 'paint:#2a5a8a', 'pump motor')
    m.cyl((12.6, 1.6, 1.4), 1.2, 1.4, 'x', 'paint:#3a6aa0', 'pump volute')
    m.cyl((13.4, 1.6, 1.4), 0.5, 2.0, 'z', 'metal:steel', 'suction pipe')
    m.box((9.0, 1.2, 0.4), (14.4, 1.6, 2.4), 'paint:#2a2a2a', 'pump bedplate')
    m.g('pump starter')
    m.box((10.6, 1.2, 12.0), (15.6, 12.0, 15.4), 'paint:#2f4a3a', 'pump starter')
    m.box((10.4, 12.0, 11.8), (15.8, 12.6, 15.6), 'paint:#24382c', 'starter hood')
    cabkit.fit(m, 11.0, 15.2, 1.6, 11.6, 15.4, side='south', col='paint:#2f4a3a', title='FEED PUMP|7.5 KW', volts='400V 50HZ')
    m.g('column fittings')
    m.cyl((8.0, 22.0, 10.4), 0.15, 4.0, 'y', 'metal:steel', 'arm tie-down chain')
    m.box((7.6, 21.6, 10.2), (8.4, 22.2, 10.9), 'metal:steel', 'chain hook')
    m.cyl((10.6, 2.6, 8.0), 0.5, 6.0, 'x', 'paint:#2f4a3a', 'feed main')
    m.cyl((8.0, 18.0, 5.6), 0.5, 0.6, 'z', 'lamp:fff0c0', 'column lamp')
    m.box((5.0, 1.2, 10.6), (6.4, 6.0, 12.0), 'paint:#c82020', 'fire hose box')
    m.box((5.1, 3.0, 12.0), (6.3, 4.4, 12.05), 'label:HOSE', 'hose label', faces=('south',), south='label:HOSE')
    m.g('dosing pot')
    m.cyl((14.2, 1.2, 5.0), 1.1, 3.4, 'y', 'paint:#e8e4d8', 'dosing pot')
    m.box((13.3, 4.6, 4.1), (15.1, 4.9, 5.9), 'paint:#2a62c8', 'pot lid')
    m.box((13.2, 3.4, 3.88), (15.2, 4.2, 3.9), 'label:DOSING', 'pot label')
    f = m.surface('north', face=0.18, title='COLUMN')
    m.ctl(f, 'digits', 'litres', X(3.9), 12.9, w=5.0, h=1.0, col='ffd040', label='', fmt='%s L')
    m.ctl(f, 'digits', 'line1', X(3.9), 12.1, w=5.0, h=0.5, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(3.9), 11.6, w=5.0, h=0.4, col='8ad4ff', label='', fmt='%s')
    gauges(m, f, [('tankg', 'TANK'), ('ppmg', 'HARD'), ('stockg', 'ENGINE')], X(1.9), 9.6, step=2.0, size=1.6)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('pumpl', 'ffb020', 'PUMP', True), ('low', 'ff3030', 'LOW'), ('treat', '8ad4ff', 'DOSE')],
             X(1.6), 7.4, step=1.3, size=0.45)
    lamp_row(m, f, [('flow', '30e060', 'FLOW', True), ('spill', 'ff3030', 'SPILL', True), ('armout', 'ffb020', 'ARM'), ('locol', '8ad4ff', 'ENGINE')],
             X(1.6), 6.1, step=1.3, size=0.45)
    push_row(m, f, [('pump', '1fa34a', 'PUMP'), ('reset', '505860', 'RESET'), ('fill', '2050d0', 'FILL'), ('test', 'b0b0b0', 'TEST')], X(1.7), 4.4, step=1.6, size=0.8)
    m.ctl(f, 'key', 'k_float', X(1.8), 2.3, w=0.9, h=0.9, col='c0a040', label='FLOAT', pos=['OFF', 'FLOAT'])
    m.ctl(f, 'selector', 's_treat', X(3.6), 2.3, w=0.9, h=0.9, col='202020', label='DOSE', pos=['RAW', 'DOSE'])
    estop_plate(m, f, X(5.8), 2.3)
    for (x, t) in ((1.8, 'FLOAT'), (3.6, 'DOSE')): lab(m, f, X(x), 1.5, t)
    c = m.surface('north', face=2.4, title='COLUMN VALVES')
    m.ctl(c, 'lever', 'm_valve', X(8.0), 7.0, w=0.8, h=2.6, col='c82020', label='VALVE', pos=['SHUT', 'OPEN'])
    m.ctl(c, 'lever', 'm_arm', X(10.6), 7.0, w=0.8, h=2.6, col='e8c020', label='ARM', pos=['STOWED', 'OUT'])
    m.box((7.2, 5.0, 2.45), (11.4, 9.2, 2.55), 'paint:#24382c', 'lever quadrant plate')
    lab(m, c, X(8.0), 5.3, 'VALVE'); lab(m, c, X(10.6), 5.3, 'ARM')
    d = m.surface('east', face=0.75, title='DOSING POT')
    m.ctl(d, 'zone', 'dose', 11.0, 2.9, w=2.2, h=3.4, col='000000', label='')
    m.save(item_gui=0.4)


# ===================================================================================================== Ash Pit
def ash_pit():
    m = Model('ash_pit')
    m.g('pit lining')
    m.box((0, 0, 4.0), (16, 1.0, 16), 'concrete', 'pit floor')
    for (a, b) in (((0, 1, 4.0), (16, 5.0, 5.2)), ((0, 1, 14.8), (16, 5.0, 16)), ((0, 1, 4.0), (1.2, 5.0, 16)), ((14.8, 1, 4.0), (16, 5.0, 16))):
        m.box(a, b, 'paint:#7a3a28', 'firebrick lining')
    m.box((1.2, 1.0, 5.2), (14.8, 2.6, 14.8), 'paint:#5a5a58', 'ash heap')
    for i in range(10):
        r = random.Random(i + 7)
        x, z = 1.6 + r.random() * 12, 5.6 + r.random() * 8
        m.box((x, 2.6, z), (x + 1.0, 3.0, z + 0.8), 'paint:#3a3a38' if i % 3 else 'lamp:c84a18', 'clinker')
    m.box((-0.2, 5.0, 3.8), (16.2, 5.6, 5.2), 'haz', 'pit edge')
    m.g('lining details')
    for y in (2.0, 3.0, 4.0):
        m.box((1.15, y, 5.2), (1.25, y + 0.1, 14.8), 'paint:#5a2a1c', 'brick course')
        m.box((14.75, y, 5.2), (14.85, y + 0.1, 14.8), 'paint:#5a2a1c', 'brick course')
        m.box((1.2, y, 14.75), (14.8, y + 0.1, 14.85), 'paint:#5a2a1c', 'brick course')
    m.box((12.0, 5.6, 5.4), (15.6, 9.6, 8.0), 'paint:#2a62c8', 'quench water tank')
    m.box((12.2, 8.0, 5.35), (15.4, 9.0, 5.4), 'label:QUENCH', 'tank label')
    m.box((4.0, 5.6, 15.6), (10.0, 8.0, 15.7), 'danger:HOT ASH', 'hot ash sign', faces=('north',))
    m.g('quench sprays')
    m.cyl((0.8, 5.4, 5.0), 0.35, 14.4, 'x', 'paint:#2a62c8', 'spray header')
    for x in (2.0, 5.0, 8.0, 11.0, 14.0):
        m.box((x - 0.2, 4.8, 4.8), (x + 0.2, 5.4, 5.4), 'metal:brass', 'spray nozzle')
    m.cyl((0.8, 0, 4.4), 0.35, 5.4, 'y', 'paint:#2a62c8', 'spray riser')
    m.box((0.2, 5.6, 4.0), (1.4, 6.8, 5.2), 'paint:#3a3f44', 'spray valve')
    m.g('grab hoist')
    for x in (0.4, 14.6):
        m.box((x, 0, 15.0), (x + 1.0, 22.0, 16.0), 'galv', 'gantry leg')
    m.box((0.4, 22.0, 14.6), (15.6, 23.4, 16.2), 'galv', 'gantry beam')
    m.box((6.0, 20.6, 14.4), (10.0, 22.0, 16.4), 'paint:#c8a020', 'hoist trolley')
    m.cyl((8.0, 12.0, 15.4), 0.1, 8.6, 'y', 'metal:steel', 'hoist rope')
    m.box((6.8, 10.0, 14.4), (9.2, 12.0, 16.4), 'paint:#3a3f44', 'grab head')
    m.box((6.4, 8.0, 14.2), (7.6, 10.0, 16.6), 'paint:#2a2a2a', 'grab shell')
    m.box((8.4, 8.0, 14.2), (9.6, 10.0, 16.6), 'paint:#2a2a2a', 'grab shell')
    m.g('ash wagon')
    m.box((10.4, 0, -0.2), (15.6, 0.6, 3.4), 'metal:steel', 'wagon rails')
    m.box((10.8, 1.2, 0.0), (15.2, 5.0, 3.2), 'paint:#4a3a2a', 'ash wagon body')
    m.box((11.0, 4.0, 0.2), (15.0, 4.8, 3.0), 'paint:#5a5a58', 'ash in the wagon')
    for x in (11.4, 14.0):
        m.cyl((x, 0.8, -0.1), 0.7, 0.4, 'z', 'metal:steel', 'wagon wheel')
    m.box((10.8, 3.8, -0.05), (15.2, 4.6, 0.0), 'label:ASH', 'wagon label')
    for z in (0.2, 1.6, 3.0):
        m.box((10.2, 0, z - 0.2), (15.8, 0.3, z + 0.2), 'wood', 'wagon sleeper')
    m.box((10.6, 5.0, 0.0), (15.4, 5.4, 3.2), 'paint:#3a2a1a', 'wagon top rail')
    for x in (11.0, 13.0, 15.0):
        m.box((x - 0.15, 1.2, -0.15), (x + 0.15, 5.0, 0.0), 'metal:steel', 'wagon strap')
    m.box((6.6, 12.0, 15.0), (9.4, 12.6, 16.0), 'metal:steel', 'grab yoke')
    m.box((15.6, 6.0, 15.0), (16.4, 9.0, 16.0), 'paint:#3a3f44', 'hoist pendant')
    m.g('fire irons')
    m.box((-0.4, 0, 0.2), (0.4, 10.0, 3.4), 'paint:#3a3f44', 'rack back')
    m.box((-0.8, 9.0, 0.2), (-0.4, 9.6, 3.4), 'metal:steel', 'hook rail')
    for i, (z, ln) in enumerate(((0.6, 8.0), (1.4, 8.6), (2.2, 7.2), (3.0, 8.2))):
        m.box((-0.8, 9.0 - ln, z), (-0.6, 9.4, z + 0.2), 'metal:steel', 'fire iron')
        m.box((-1.1, 9.0 - ln, z - 0.4), (-0.6, 9.0 - ln + 0.5, z + 0.6), 'metal:steel', 'iron blade')
    m.g('control pillar')
    m.box((2.0, 0, 0.4), (9.6, 15.0, 3.2), 'paint:#3a3f44', 'control pillar')
    m.box((1.8, 15.0, 0.2), (9.8, 15.6, 3.4), 'paint:#2a2e32', 'pillar hood')
    m.box((2.2, 15.6, 1.0), (9.4, 16.4, 2.6), 'lamp:fff0c0', 'pit floodlight')
    screen(m, 2.6, 9.0, 12.4, 14.4, 0.4, 'pit screen')
    m.terminal_12v(5.8, 1.0, 3.2, 'south')
    m.box((9.6, 4.0, 0.6), (10.6, 7.0, 2.8), 'paint:#3a3f44', 'isolator box', east='danger:400V')
    m.cyl((10.6, 5.6, 1.7), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    f = m.surface('north', face=0.38, title='ASH PIT')
    m.ctl(f, 'digits', 'line1', X(5.8), 13.9, w=6.0, h=0.55, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(5.8), 13.2, w=6.0, h=0.5, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'kg', X(4.3), 12.6, w=2.8, h=0.5, col='ffd040', label='', fmt='%s KG')
    m.ctl(f, 'digits', 'temp', X(7.4), 12.6, w=2.8, h=0.5, col='ff8040', label='', fmt='%s C')
    gauges(m, f, [('pitg', 'PIT'), ('tempg', 'TEMP'), ('waterg', 'WATER'), ('wagong', 'WAGON'), ('pang', 'PAN')], X(2.8), 10.4, step=1.5, size=1.3)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('locol', '8ad4ff', 'ENG'), ('interlock', '30e060', 'LOCK'), ('hot', 'ff3030', 'HOT', True)],
             X(2.8), 8.0, step=1.45, size=0.45)
    lamp_row(m, f, [('spray', '8ad4ff', 'SPRAY'), ('hoistl', 'ffb020', 'GRAB', True), ('full', 'ff3030', 'FULL'), ('wfull', 'ffb020', 'WAGON'), ('lowwater', 'ff3030', 'WATER')],
             X(2.8), 6.6, step=1.45, size=0.45)
    push_row(m, f, [('drop', 'e08020', 'DROP'), ('hoist', '1fa34a', 'HOIST'), ('fill', '2050d0', 'FILL'), ('test', 'b0b0b0', 'TEST')], X(2.8), 4.8, step=1.8, size=0.85)
    m.ctl(f, 'lever', 'm_quench', X(3.4), 2.4, w=0.7, h=2.0, col='2a62c8', label='QUENCH', pos=['OFF', 'SPRAY'])
    lab(m, f, X(3.4), 1.1, 'QUENCH')
    estop_plate(m, f, X(7.4), 2.4)
    w = m.surface('north', face=-0.02, title='ASH WAGON')
    m.ctl(w, 'zone', 'wagon', X(13.0), 3.0, w=4.4, h=3.8, col='000000', label='')
    t = m.surface('west', face=-1.15, title='FIRE IRONS')
    m.ctl(t, 'zone', 'tools', 1.8, 5.0, w=3.2, h=9.0, col='000000', label='')
    m.save(item_gui=0.45)


# ===================================================================================================== Sand Tower
def sand_tower():
    m = Model('sand_tower')
    m.g('bins')
    m.box((2.0, 0, 2.4), (14.0, 6.0, 7.0), 'wood', 'damp sand bin')
    m.box((2.4, 5.0, 2.8), (13.6, 5.8, 6.6), 'paint:#c8b070', 'damp sand')
    for x in (2.0, 6.0, 10.0, 13.6):
        m.box((x, 0, 2.2), (x + 0.4, 6.4, 7.2), 'wood', 'bin post')
    m.box((2.0, 4.4, 2.15), (6.0, 5.2, 2.2), 'label:DAMP SAND', 'bin label')
    m.g('drier')
    m.box((3.0, 0, 7.6), (9.0, 9.0, 13.0), 'paint:#5a4a3a', 'drier casing')
    m.box((3.2, 2.0, 7.4), (6.0, 5.0, 7.6), 'paint:#2a2a2a', 'stoking door')
    m.box((6.4, 2.0, 7.4), (8.8, 5.0, 7.55), 'louvre', 'drier vent')
    m.cyl((4.4, 9.0, 11.6), 0.7, 10.0, 'y', 'paint:#3a3a3a', 'drier flue')
    m.cyl((4.4, 19.0, 11.6), 1.1, 0.6, 'y', 'paint:#2a2a2a', 'flue cap')
    m.cyl((9.0, 2.0, 10.3), 0.6, 3.0, 'x', 'paint:#c8a020', 'element terminal box')
    m.g('air lift')
    m.cyl((11.6, 0, 10.4), 0.7, 24.0, 'y', 'metal:steel', 'air lift pipe')
    m.cyl((11.6, 24.0, 10.4), 0.9, 0.6, 'y', 'metal:steel', 'pipe bend')
    m.cyl((11.6, 1.0, 13.0), 1.2, 4.0, 'y', 'paint:#2a5a8a', 'air receiver')
    m.cyl((13.4, 0.6, 13.0), 0.9, 2.0, 'x', 'paint:#3a6aa0', 'compressor')
    for y in (4.0, 9.0, 14.0, 19.0):
        m.box((11.0, y, 9.8), (12.2, y + 0.4, 11.0), 'metal:steel', 'pipe clamp')
    m.g('elevated hopper')
    for (x, z) in ((2.0, 2.0), (13.0, 2.0), (2.0, 13.0), (13.0, 13.0)):
        m.box((x, 9.0, z), (x + 1.0, 21.0, z + 1.0), 'galv', 'hopper leg')
    for y in (13.0, 17.0):
        m.box((2.0, y, 2.0), (14.0, y + 0.5, 3.0), 'galv', 'leg brace')
        m.box((2.0, y, 13.0), (14.0, y + 0.5, 14.0), 'galv', 'leg brace')
    m.box((1.4, 22.0, 1.4), (14.6, 30.0, 14.6), 'paint:#a07a44', 'elevated sand hopper')
    m.box((3.0, 21.0, 3.0), (13.0, 22.0, 13.0), 'paint:#8a6a3a', 'hopper cone')
    m.box((6.0, 19.6, 6.0), (10.0, 21.0, 10.0), 'paint:#8a6a3a', 'hopper outlet')
    m.box((1.0, 30.0, 1.0), (15.0, 30.8, 15.0), 'paint:#6a4a2a', 'hopper roof')
    m.box((3.0, 25.0, 1.35), (13.0, 27.0, 1.4), 'label:DRY SAND', 'hopper label')
    for x in (1.4, 5.4, 9.4, 13.4):
        m.box((x, 22.0, 1.2), (x + 0.4, 30.0, 1.4), 'paint:#8a6a3a', 'hopper stiffener')
    m.box((6.0, 30.8, 6.0), (10.0, 31.6, 10.0), 'paint:#5a3a1a', 'filler hatch')
    m.cyl((8.0, 18.6, 10.0), 0.6, 1.0, 'y', 'metal:brass', 'sand valve body')
    m.cyl((8.0, 18.8, 10.6), 0.45, 8.0, 'z', 'rubber', 'delivery hose')
    m.box((7.2, 16.8, 18.4), (8.8, 19.4, 19.4), 'metal:steel', 'hose nozzle')
    m.ladder(13.2, 14.6, 9.0, 22.0, 14.0)
    m.g('control panel')
    m.box((9.6, 0, 0.4), (15.6, 14.0, 2.4), 'paint:#3a3f44', 'sand panel')
    m.box((9.4, 14.0, 0.2), (15.8, 14.6, 2.6), 'paint:#2a2e32', 'panel hood')
    screen(m, 10.2, 15.0, 12.0, 13.6, 0.4, 'sand screen')
    m.terminal_12v(12.6, 0.8, 2.4, 'south')
    m.box((15.6, 4.0, 0.6), (16.6, 7.0, 2.2), 'paint:#3a3f44', 'isolator box', east='danger:400V')
    m.cyl((16.6, 5.6, 1.4), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    f = m.surface('north', face=0.38, title='SAND TOWER')
    m.ctl(f, 'digits', 'line1', X(12.6), 13.2, w=4.6, h=0.55, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'temp', X(11.4), 12.4, w=2.2, h=0.5, col='ff8040', label='', fmt='%s C')
    m.ctl(f, 'digits', 'count', X(13.8), 12.4, w=2.2, h=0.5, col='ffd040', label='', fmt='%s SAND')
    gauges(m, f, [('tempg', 'DRIER'), ('wetg', 'DAMP'), ('bing', 'DRY BIN')], X(10.8), 10.2, step=1.9, size=1.5)
    gauges(m, f, [('hopg', 'HOPPER'), ('boxg', 'SANDBOX')], X(11.8), 7.6, step=2.4, size=1.5)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('heat', 'ff8040', 'HEAT', True), ('dry', '30e060', 'DRY')], X(10.4), 5.4, step=1.45, size=0.45)
    lamp_row(m, f, [('airl', '8ad4ff', 'AIR', True), ('flow', '30e060', 'FLOW', True), ('locol', '8ad4ff', 'ENG'), ('low', 'ff3030', 'LOW')], X(10.4), 4.1, step=1.45, size=0.45)
    m.ctl(f, 'lamp', 'wetl', X(10.4 + 4 * 1.45 - 0.2), 5.4, w=0.45, h=0.45, col='ffb020', label='NO SAND')
    m.ctl(f, 'selector', 's_drier', X(10.4), 2.2, w=0.9, h=0.9, col='202020', label='DRIER', pos=['OFF', 'ON'])
    m.ctl(f, 'key', 'k_air', X(12.0), 2.2, w=0.9, h=0.9, col='c0a040', label='AIR', pos=['OFF', 'LIFT'])
    m.ctl(f, 'push', 'reset', X(13.4), 2.2, w=0.7, h=0.7, col='505860', label='RESET')
    m.ctl(f, 'push', 'test', X(14.6), 2.2, w=0.7, h=0.7, col='b0b0b0', label='TEST')
    for (x, t) in ((10.4, 'DRIER'), (12.0, 'AIR'), (13.4, 'RST'), (14.6, 'TEST')): lab(m, f, X(x), 1.4, t)
    v = m.surface('north', face=2.18, title='SAND VALVE')
    m.ctl(v, 'lever', 'm_sand', X(8.4), 9.6, w=0.7, h=2.4, col='c8a060', label='SAND', pos=['SHUT', 'OPEN'])
    m.box((7.8, 8.0, 2.2), (9.0, 11.4, 2.3), 'paint:#3a3f44', 'valve quadrant')
    lab(m, v, X(8.4), 7.8, 'SAND')
    estop_plate(m, v, X(8.4), 6.0)
    b = m.surface('north', face=2.18, title='BINS')
    m.ctl(b, 'zone', 'wet', X(5.0), 3.0, w=6.0, h=5.0, col='000000', label='')
    m.save(item_gui=0.3)


# ===================================================================================================== Boiler Washout
def boiler_washout():
    m = Model('boiler_washout')
    m.g('skid')
    m.box((0, 0, 2.0), (16, 1.0, 14.0), 'paint:#2a2a2a', 'skid base')
    m.g('hot well')
    m.cyl((0.4, 6.0, 10.0), 3.6, 15.2, 'x', 'paint:#7a2a1a', 'hot well tank')
    for x in (1.0, 5.0, 10.0, 15.0):
        m.cyl((x, 6.0, 10.0), 3.8, 0.4, 'x', 'paint:#5a1e12', 'tank band')
    for x in (3.0, 13.0):
        m.box((x - 0.8, 1.0, 7.0), (x + 0.8, 3.0, 13.0), 'paint:#3a3a3a', 'tank saddle')
    m.box((4.0, 8.0, 6.3), (12.0, 9.6, 6.4), 'label:HOT WELL 20 000 L', 'tank label')
    m.cyl((14.0, 9.4, 10.0), 0.8, 1.6, 'y', 'paint:#c8a020', 'heater head')
    m.cyl((2.0, 9.4, 10.0), 0.3, 3.0, 'y', 'metal:steel', 'vent pipe')
    m.cyl((8.0, 9.6, 10.0), 0.9, 0.4, 'y', 'paint:#3a3a3a', 'manhole')
    m.g('pump set')
    m.cyl((9.0, 1.0, 4.6), 1.6, 5.0, 'x', 'paint:#2a5a8a', 'washout pump motor')
    m.cyl((14.0, 1.6, 4.6), 1.8, 1.8, 'x', 'paint:#3a6aa0', 'pump casing')
    m.cyl((15.8, 2.2, 4.6), 0.5, 3.0, 'y', 'metal:steel', 'discharge pipe')
    m.cyl((15.8, 5.2, 4.6), 0.5, 10.0, 'z', 'metal:steel', 'discharge header')
    for z in (8.0, 11.0, 14.0):
        m.box((15.4, 4.6, z), (16.6, 5.8, z + 0.6), 'paint:#c82020', 'hose valve')
    m.g('hose reels')
    for z in (12.6, 15.0):
        m.cyl((16.0, 9.0, z), 2.0, 0.8, 'x', 'paint:#c82020', 'hose reel')
        m.cyl((16.8, 9.0, z), 1.6, 0.6, 'x', 'rubber', 'hose on reel')
    m.box((15.6, 0, 12.0), (16.4, 8.0, 15.8), 'paint:#3a3f44', 'reel stand')
    m.g('sequence panel')
    m.box((0.6, 1.0, 1.0), (8.4, 17.0, 4.0), 'paint:#2f3d4a', 'sequence panel')
    m.box((0.4, 17.0, 0.8), (8.6, 17.6, 4.2), 'paint:#1c242c', 'panel hood')
    screen(m, 1.2, 7.8, 14.2, 16.4, 1.0, 'washout screen')
    m.nameplate(1.2, 13.4, 5.0, 13.9, 1.0, 'WASHOUT')
    m.terminal_12v(4.4, 1.6, 4.0, 'south')
    m.box((-0.8, 4.0, 1.6), (0.6, 7.0, 3.6), 'paint:#3a3f44', 'isolator box', west='danger:400V')
    m.cyl((-1.1, 5.6, 2.6), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    m.cyl((-0.2, 0, 2.6), 0.3, 4.0, 'y', 'cable:202020', 'armoured mains cable')
    m.g('pump starter')
    m.box((9.0, 1.0, 1.0), (15.0, 13.0, 3.6), 'paint:#3a4a5a', 'pump starter')
    cabkit.fit(m, 9.4, 14.6, 1.4, 12.6, 1.0, col='paint:#3a4a5a', title='WASHOUT PUMP|37 KW', volts='400V 50HZ')
    f = m.surface('north', face=0.98, title='WASHOUT SEQUENCE')
    m.ctl(f, 'digits', 'step', X(4.5), 16.0, w=6.4, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'line1', X(4.5), 15.3, w=6.4, h=0.45, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(4.5), 14.8, w=6.4, h=0.45, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'line3', X(4.5), 14.35, w=6.4, h=0.4, col='8ad4ff', label='', fmt='%s')
    steps = ['BLOWN', 'ISOL', 'PLUGS', 'WASH', 'WASHED', 'INSP', 'PLUGS', 'TEST', 'TESTED', 'CERT']
    for i, t in enumerate(steps):
        u = X(1.4 + (i % 5) * 1.5)
        v = 12.6 - (i // 5) * 1.3
        m.ctl(f, 'lamp', 'st%d' % (i + 1), u, v, w=0.45, h=0.45, col='30e060', label=t)
        lab(m, f, u, v - 0.5, t)
    gauges(m, f, [('wellg', 'WELL'), ('tempg', 'DEG C'), ('scaleg', 'SCALE'), ('barg', 'BAR'), ('progg', 'STEP')], X(1.5), 8.9, step=1.45, size=1.25)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('locol', '8ad4ff', 'ENG'), ('isol', 'ffb020', 'ISOL'), ('pumpl', 'ffb020', 'PUMP', True)],
             X(1.4), 6.8, step=1.5, size=0.42)
    lamp_row(m, f, [('heat', 'ff8040', 'HEAT', True), ('hot', '30e060', 'HOT'), ('due', 'ffb020', 'DUE'), ('leak', 'ff3030', 'LEAK', True)], X(1.4), 5.6, step=1.5, size=0.42)
    push_row(m, f, [('blow', 'e08020', 'BLOW'), ('plugs', '505860', 'PLUGS'), ('wash', '2050d0', 'WASH'), ('inspect', 'f0f0f0', 'INSP')], X(1.6), 4.1, step=1.7, size=0.75)
    push_row(m, f, [('hyd', '1fa34a', 'TEST'), ('cert', '30e060', 'CERT'), ('abort', 'd02020', 'ABORT'), ('fill', '2050d0', 'FILL')], X(1.6), 2.5, step=1.7, size=0.75)
    s = m.surface('east', face=7.58, title='PANEL SIDE')
    m.ctl(s, 'key', 'k_iso', 13.0, 13.0, w=1.0, h=1.0, col='c0a040', label='ISOLATE', pos=['NORMAL', 'ISOLATED'])
    m.box((8.4, 14.4, 1.6), (8.5, 16.0, 3.6), 'paint:#07120c', 'hot well readout', faces=('east',))
    m.ctl(s, 'digits', 'temp', 13.4, 15.2, w=1.9, h=0.6, col='ff8040', label='', fmt='%s C WELL')
    m.ctl(s, 'selector', 's_heat', 14.6, 13.0, w=1.0, h=1.0, col='202020', label='HEATER', pos=['OFF', 'ON'])
    lab(m, s, 13.0, 12.1, 'ISOLATE'); lab(m, s, 14.6, 12.1, 'HEATER')
    estop_plate(m, s, 13.8, 10.0)
    m.ctl(s, 'zone', 'fittings', 13.8, 4.0, w=2.4, h=4.0, col='000000', label='')
    m.box((8.4, 2.2, 1.6), (8.45, 6.0, 3.6), 'paint:#3a4048', 'fittings drawer', faces=('east',))
    m.box((8.45, 3.8, 2.3), (8.6, 4.2, 2.9), 'metal:steel', 'drawer pull')
    m.save(item_gui=0.45)


# ===================================================================================================== Charging Post
def charge_post():
    m = Model('charge_post')
    m.g('base')
    m.box((0.6, 0, 1.0), (15.4, 0.8, 15.0), 'concrete', 'plinth')
    m.box((0.4, 0.8, 0.8), (15.6, 1.0, 1.2), 'haz', 'kerb')
    m.g('power electronics cabinet')
    m.box((0.8, 0.8, 6.0), (10.0, 24.0, 14.6), 'paint:#d8dcd8', 'PE cabinet')
    m.box((0.6, 24.0, 5.8), (10.2, 24.6, 14.8), 'paint:#2a5a8a', 'cabinet roof')
    for x in (2.4, 6.4):
        m.cyl((x + 1.2, 24.6, 10.2), 1.4, 0.6, 'y', 'mesh', 'roof fan')
    m.louvres(1.4, 9.4, 18.0, 23.0, 6.0, n=6)
    m.box((1.0, 5.0, 14.6), (9.8, 9.0, 14.8), 'louvre', 'rear intake', faces=('south',))
    m.box((1.4, 2.0, 5.9), (9.4, 3.0, 6.0), 'danger:DANGER|DC 1000V', 'dc danger')
    cabkit.fit(m, 1.2, 9.6, 3.4, 17.4, 6.0, col='paint:#d8dcd8', title='RECTIFIER|DC/DC 1 MW', volts='400V 50HZ 1000V DC', fns=(('ctl', '12V CTL'), ('main', 'MAINS'), ('scr', 'SCREEN'), ('out', 'DC OUT')))
    m.box((-0.8, 4.0, 8.0), (0.8, 8.0, 12.0), 'paint:#3a3f44', 'mains isolator', west='danger:400V')
    m.cyl((-1.1, 6.0, 10.0), 0.5, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    m.cyl((0.0, 0, 10.0), 0.4, 4.0, 'y', 'cable:202020', 'supply cable')
    m.g('plug post')
    m.box((11.0, 0.8, 9.0), (14.6, 28.0, 12.6), 'paint:#2a5a8a', 'plug post')
    m.box((10.8, 28.0, 8.8), (14.8, 28.8, 12.8), 'paint:#1e4266', 'post cap')
    m.box((11.2, 26.0, 8.85), (14.4, 27.6, 9.0), 'lamp:30e060', 'status light bar')
    m.box((11.6, 16.0, 12.6), (14.0, 19.0, 13.6), 'paint:#2a2a2a', 'connector holster')
    m.box((12.2, 16.6, 13.6), (13.4, 18.4, 15.4), 'paint:#3a3a3a', 'charging connector')
    m.cyl((12.8, 6.0, 13.0), 0.6, 10.0, 'y', 'rubber', 'charging cable')
    m.cyl((12.8, 6.0, 13.0), 0.6, 3.0, 'z', 'rubber', 'cable loop')
    m.box((11.2, 20.0, 12.6), (14.4, 22.0, 12.7), 'label:CHARGE', 'post label', faces=('south',))
    m.g('cable trench')
    m.box((9.8, 0.4, 10.0), (11.2, 0.8, 11.6), 'paint:#2a2a2a', 'dc cable duct')
    m.g('screen + controls')
    m.box((1.0, 8.0, 1.2), (10.0, 22.0, 5.8), 'paint:#c8ccc8', 'control front')
    screen(m, 1.8, 9.2, 17.0, 21.2, 1.2, 'charge profile screen')
    m.terminal_12v(5.5, 8.4, 5.8, 'south')
    f = m.surface('north', face=1.18, title='CHARGER')
    m.ctl(f, 'digits', 'line1', X(5.5), 20.6, w=7.0, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(5.5), 19.8, w=7.0, h=0.55, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'line3', X(5.5), 19.0, w=7.0, h=0.55, col='ffd040', label='', fmt='%s')
    for i in range(10):
        m.ctl(f, 'lamp', 'soc%d' % (i + 1), X(2.2 + i * 0.66), 17.8, w=0.5, h=0.5, col='30e060' if i > 1 else 'ff3030', label='%d0' % (i + 1))
    lab(m, f, X(5.5), 17.2, 'STATE OF CHARGE')
    m.ctl(f, 'digits', 'kw', X(3.4), 15.8, w=2.8, h=1.0, col='ffd040', label='', fmt='%s KW')
    m.ctl(f, 'digits', 'kwh', X(7.4), 15.8, w=2.8, h=1.0, col='ffd040', label='', fmt='%s KWH')
    gauges(m, f, [('kwg', 'KW'), ('socg', 'SOC'), ('tempg', 'CAB C')], X(2.8), 13.4, step=2.6, size=1.8)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('imd', '30e060', 'IMD'), ('locol', '8ad4ff', 'UNIT'), ('plugl', 'ffb020', 'PLUG'), ('chgl', '30e060', 'CHG', True)],
             X(2.0), 11.2, step=1.4, size=0.45)
    lamp_row(m, f, [('cv', '8ad4ff', 'CV'), ('full', '30e060', 'FULL'), ('fan', '8ad4ff', 'FANS'), ('hot', 'ff3030', 'HOT'), ('der', 'ffb020', 'DERATE', True)],
             X(2.0), 9.9, step=1.4, size=0.45)
    k = m.surface('north', face=0.98, title='CHARGER KEYS')
    m.box((1.0, 1.0, 1.0), (10.0, 8.0, 5.8), 'paint:#b8bcb8', 'lower front')
    push_row(m, k, [('plug', 'ffb020', 'PLUG'), ('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('test', 'b0b0b0', 'TEST')], X(2.2), 6.4, step=1.9, size=0.9)
    m.ctl(k, 'selector', 's_prof', X(2.6), 3.4, w=1.1, h=1.1, col='202020', label='PROFILE', pos=['ECO', 'NORMAL', 'RAPID'])
    lab(m, k, X(2.6), 2.4, 'PROFILE')
    estop_plate(m, k, X(5.4), 3.4)
    m.ctl(k, 'zone', 'cables', X(8.2), 3.0, w=2.6, h=3.0, col='000000', label='')
    m.box((6.9, 1.6, 0.9), (9.5, 4.6, 1.0), 'paint:#a8aca8', 'cable store door')
    m.box((7.2, 3.9, 0.8), (8.0, 4.2, 0.9), 'metal:steel', 'door pull')
    m.save(item_gui=0.4)


# ===================================================================================================== Hydrogen Compressor
def h2_compressor():
    m = Model('h2_compressor')
    m.g('skid')
    m.box((0, 0, 1.0), (16, 1.2, 15.0), 'paint:#2a2a2a', 'skid')
    m.box((0, 1.2, 14.4), (16, 14.0, 15.0), 'mesh', 'enclosure rear mesh')
    for x in (0.0, 15.4):
        m.box((x, 1.2, 1.0), (x + 0.6, 14.0, 15.0), 'galv', 'enclosure frame')
    m.box((0, 13.4, 1.0), (16, 14.0, 15.0), 'galv', 'enclosure roof frame')
    m.g('compressor')
    m.cyl((1.0, 2.0, 10.0), 1.8, 5.0, 'x', 'paint:#2a5a8a', 'drive motor 75 kW')
    m.cyl((6.0, 2.4, 10.0), 1.4, 1.6, 'x', 'paint:#3a3f44', 'crankcase')
    for i, (x, r) in enumerate(((8.0, 1.6), (10.6, 1.2), (12.8, 0.9))):
        m.cyl((x, 4.0, 10.0), r, 5.0 - i, 'y', 'paint:#c8c4b8', 'stage %d head' % (i + 1))
        m.cyl((x, 9.0 - i, 10.0), r + 0.3, 0.5, 'y', 'metal:steel', 'diaphragm flange')
    m.g('intercoolers')
    for i, x in enumerate((8.0, 10.6, 12.8)):
        m.box((x - 0.8, 9.6, 4.0), (x + 0.8, 12.6, 7.4), 'fins', 'intercooler %d' % (i + 1))
        m.cyl((x, 9.2, 7.0), 0.25, 3.0, 'z', 'metal:steel', 'cooler pipe')
    m.cyl((14.4, 1.2, 10.0), 0.5, 13.0, 'y', 'metal:steel', 'discharge line')
    m.cyl((14.4, 14.0, 10.0), 0.35, 16.0, 'y', 'metal:steel', 'vent stack')
    m.cyl((14.4, 30.0, 10.0), 0.6, 0.6, 'y', 'paint:#c82020', 'vent cap')
    m.box((14.0, 6.0, 9.6), (14.8, 7.2, 10.4), 'paint:#c82020', 'relief valve')
    m.cyl((2.0, 3.0, 13.6), 0.4, 13.0, 'x', 'paint:#e8e4d8', 'suction header')
    m.box((0.6, 2.6, 13.2), (1.4, 3.4, 14.0), 'paint:#f0c818', 'suction valve')
    m.box((1.4, 6.0, 0.95), (5.4, 8.2, 1.0), 'warn', 'flammable gas sign')
    m.box((5.6, 6.6, 0.95), (11.4, 8.0, 1.0), 'label:H2 30-500 BAR', 'skid label')
    detector(m, 13.0, 11.0, 1.0)
    beacon(m, 1.0, 14.0, 2.0)
    m.g('drive cabinet')
    m.box((1.0, 1.2, 15.0), (9.0, 13.0, 18.0), 'paint:#3a4a5a', 'VFD cabinet')
    m.box((0.8, 13.0, 14.8), (9.2, 13.6, 18.2), 'paint:#2a3540', 'VFD hood')
    cabkit.fit(m, 1.4, 8.6, 1.6, 12.6, 18.0, side='south', col='paint:#3a4a5a', title='COMPRESSOR VFD|75 KW', volts='480V 60HZ')
    m.g('piping')
    for (x, y) in ((8.0, 9.4), (10.6, 8.4), (12.8, 7.4)):
        m.cyl((x, y, 10.0), 0.3, 1.6, 'y', 'metal:steel', 'interstage pipe')
        m.box((x - 0.5, y + 1.4, 9.5), (x + 0.5, y + 2.0, 10.5), 'metal:brass', 'check valve')
    for x in (3.0, 7.0, 11.0):
        m.box((x, 1.2, 13.2), (x + 0.6, 3.0, 14.0), 'galv', 'pipe support')
    m.box((9.0, 1.2, 12.0), (12.0, 3.4, 14.0), 'paint:#2a62c8', 'coolant pump')
    m.box((12.0, 11.0, 0.95), (15.0, 12.6, 1.0), 'danger:H2 ZONE 1', 'atex sign')
    m.g('control panel')
    m.box((0.6, 1.2, 1.0), (11.8, 6.0, 3.6), 'paint:#3a3f44', 'panel lower')
    m.box((0.6, 8.4, 1.0), (11.8, 13.4, 3.6), 'paint:#3a3f44', 'panel upper')
    screen(m, 1.2, 6.0, 11.6, 13.0, 1.0, 'compressor screen')
    m.terminal_12v(9.0, 1.4, 3.6, 'south')
    m.box((15.6, 3.0, 2.0), (16.8, 6.4, 5.0), 'paint:#3a3f44', 'isolator box', east='danger:480V')
    m.cyl((16.8, 4.8, 3.5), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    f = m.surface('north', face=0.98, title='COMPRESSOR')
    m.ctl(f, 'digits', 'line1', X(3.6), 12.6, w=4.6, h=0.55, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'kg', X(3.6), 11.9, w=4.6, h=0.5, col='ffd040', label='', fmt='%s KG MADE')
    m.ctl(f, 'digits', 'temp', X(9.6), 12.6, w=3.4, h=0.55, col='ff8040', label='', fmt='%s C DISCH')
    gauges(m, f, [('suctg', 'SUCT'), ('st1g', 'ST1'), ('st2g', 'ST2'), ('st3g', 'ST3'), ('tempg', 'TEMP'), ('lelg', 'LEL')], X(1.6), 10.0, step=1.75, size=1.4)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('run', '30e060', 'RUN', True), ('autol', '8ad4ff', 'AUTO'), ('stl', '8ad4ff', 'STORE'), ('full', 'ffb020', 'FULL')],
             X(1.6), 5.2, step=1.7, size=0.45)
    lamp_row(m, f, [('fan', '8ad4ff', 'FANS'), ('tripl', 'ff3030', 'TRIP', True), ('esdl', 'ff3030', 'ESD', True), ('gas', 'ffb020', 'GAS'), ('leakl', 'ff3030', 'LEAK', True)],
             X(1.6), 3.9, step=1.7, size=0.45)
    push_row(m, f, [('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('reset', '2050d0', 'RESET'), ('test', 'b0b0b0', 'TEST')], X(1.6), 2.3, step=1.7, size=0.75)
    m.ctl(f, 'key', 'k_auto', X(8.6), 2.4, w=0.9, h=0.9, col='c0a040', label='AUTO', pos=['MAN', 'AUTO'])
    m.ctl(f, 'selector', 's_cool', X(10.2), 2.4, w=0.9, h=0.9, col='202020', label='COOLING', pos=['AUTO', 'HIGH'])
    lab(m, f, X(8.6), 1.6, 'AUTO'); lab(m, f, X(10.2), 1.6, 'COOL')
    e = m.surface('north', face=0.98, title='COMPRESSOR ESD')
    estop_plate(m, e, X(14.0), 4.0)
    m.box((12.6, 2.6, 0.9), (15.4, 5.4, 1.0), 'paint:#f0c818', 'estop backplate')
    s = m.surface('north', face=0.98, title='SPARES')
    m.ctl(s, 'zone', 'spares', X(14.0), 9.0, w=2.6, h=3.0, col='000000', label='')
    m.box((12.6, 7.4, 0.9), (15.4, 10.6, 1.0), 'paint:#4c535a', 'spares box door')
    m.save(item_gui=0.45)


# ===================================================================================================== Hydrogen Storage
def h2_storage():
    m = Model('h2_storage')
    m.g('rack')
    m.box((0, 0, 1.0), (16, 1.0, 15.0), 'concrete', 'pad')
    for x in (0.4, 7.6, 14.8):
        for z in (2.0, 13.4):
            m.box((x, 1.0, z), (x + 0.8, 26.0, z + 0.8), 'galv', 'rack upright')
    m.g('tubes')
    names = ('LOW 200', 'MED 350', 'HIGH 500')
    for b in range(3):
        for t in range(3):
            y = 3.0 + b * 7.4 + t * 2.2
            m.cyl((0.0, y, 8.0), 1.0, 16.0, 'x', 'paint:#e8e8e8' if b < 2 else 'paint:#d8e4f0', 'composite tube')
            m.cyl((-0.4, y, 8.0), 0.6, 0.4, 'x', 'metal:steel', 'tube end boss')
        m.box((0.2, 3.0 + b * 7.4 - 1.2, 2.6), (15.8, 3.0 + b * 7.4 - 1.0, 13.6), 'galv', 'bank shelf')
        m.box((1.0, 3.0 + b * 7.4 + 4.8, 6.9), (6.0, 3.0 + b * 7.4 + 5.6, 7.0), 'label:' + names[b] + ' BAR', 'bank label')
    m.g('manifold')
    m.cyl((-0.6, 2.0, 8.0), 0.3, 22.0, 'y', 'metal:steel', 'manifold riser')
    for b in range(3):
        y = 3.0 + b * 7.4 + 2.2
        m.box((-1.0, y - 0.4, 6.0), (-0.2, y + 0.4, 7.0), 'paint:#c82020', 'bank solenoid valve')
        m.box((-1.2, y - 0.5, 4.4), (-0.4, y + 0.5, 5.4), 'metal:brass', 'bank hand valve')
    m.cyl((-0.6, 24.0, 8.0), 0.25, 8.0, 'y', 'metal:steel', 'vent stack')
    detector(m, 9.0, 23.0, 1.0)
    beacon(m, 15.0, 26.0, 2.0, 'ff2020')
    m.box((9.4, 18.0, 0.95), (15.0, 21.4, 1.0), 'danger:HYDROGEN|NO FLAMES', 'danger sign')
    m.g('bank panel')
    m.box((1.0, 1.0, 1.0), (8.6, 16.0, 2.6), 'paint:#3a3f44', 'bank panel')
    m.box((0.8, 16.0, 0.8), (8.8, 16.6, 2.8), 'paint:#2a2e32', 'panel hood')
    screen(m, 1.6, 8.0, 13.8, 15.4, 1.0, 'storage screen')
    m.terminal_12v(4.8, 1.2, 2.6, 'south')
    f = m.surface('north', face=0.98, title='STORAGE')
    m.ctl(f, 'digits', 'line1', X(4.8), 15.0, w=6.2, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'kg', X(3.4), 14.2, w=3.0, h=0.55, col='ffd040', label='', fmt='%s KG')
    m.ctl(f, 'digits', 'lel', X(6.6), 14.2, w=2.6, h=0.55, col='ff8040', label='', fmt='%s LEL')
    gauges(m, f, [('b1g', 'LOW'), ('b2g', 'MED'), ('b3g', 'HIGH'), ('lelg', 'LEL')], X(2.0), 11.6, step=1.95, size=1.6)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('o1', '30e060', 'LOW'), ('o2', '30e060', 'MED'), ('o3', '30e060', 'HIGH')], X(2.0), 9.0, step=1.6, size=0.45)
    lamp_row(m, f, [('gas', 'ffb020', 'GAS'), ('leakl', 'ff3030', 'LEAK', True), ('esdl', 'ff3030', 'ESD', True)], X(2.0), 7.7, step=1.6, size=0.45)
    push_row(m, f, [('reset', '2050d0', 'RESET'), ('fix', 'e08020', 'REMAKE'), ('test', 'b0b0b0', 'TEST')], X(2.0), 6.0, step=1.9, size=0.8)
    estop_plate(m, f, X(7.4), 6.0)
    for i in range(3):
        m.ctl(f, 'lever', 'm_v%d' % (i + 1), X(2.0 + i * 1.9), 3.2, w=0.6, h=1.8, col='c8a020', label=('LOW', 'MED', 'HIGH')[i] + ' VALVE', pos=['OPEN', 'SHUT'])
        lab(m, f, X(2.0 + i * 1.9), 2.0, ('LOW', 'MED', 'HIGH')[i])
    m.ctl(f, 'zone', 'cert', X(7.4), 2.8, w=1.8, h=2.4, col='000000', label='')
    m.box((6.6, 1.6, 0.9), (8.2, 4.0, 1.0), 'paint:#4c535a', 'certificate box')
    m.save(item_gui=0.4)


# ===================================================================================================== Hydrogen Dispenser
def h2_dispenser():
    m = Model('h2_dispenser')
    m.g('island')
    m.box((1.0, 0, 1.0), (15.0, 1.0, 15.0), 'concrete', 'dispenser island')
    m.box((0.8, 1.0, 0.8), (15.2, 1.2, 15.2), 'haz', 'island nosing')
    m.box((-6.0, 0.02, 15.0), (22.0, 0.1, 20.0), 'haz', 'safety zone marking', faces=('up',))
    m.g('housing')
    m.box((3.0, 1.0, 4.0), (13.0, 22.0, 12.0), 'paint:#e8ecf0', 'dispenser housing')
    m.box((2.8, 22.0, 3.8), (13.2, 23.0, 12.2), 'paint:#2a62c8', 'housing top')
    m.box((3.0, 18.0, 3.95), (13.0, 19.4, 4.0), 'plate:HYDROGEN H2 350 BAR', 'brand band')
    m.box((3.1, 1.2, 11.9), (12.9, 20.0, 12.05), 'louvre', 'rear vent', faces=('south',))
    m.g('nozzle + hose')
    m.box((13.0, 8.0, 7.0), (14.4, 12.0, 9.0), 'paint:#2a2a2a', 'nozzle holster')
    m.box((13.4, 9.0, 7.4), (15.4, 11.4, 8.6), 'paint:#3a6aa0', 'H2 nozzle')
    m.cyl((14.4, 4.0, 10.0), 0.45, 6.0, 'y', 'rubber', 'dispenser hose')
    m.cyl((14.4, 3.0, 10.0), 0.6, 1.0, 'y', 'metal:steel', 'breakaway coupling')
    m.box((13.0, 14.0, 6.6), (14.6, 16.0, 9.4), 'paint:#f0c818', 'hose label plate')
    m.g('precooler')
    m.box((3.4, 1.0, 12.4), (12.6, 9.0, 15.2), 'paint:#c8ccd0', 'precooler -40 C')
    m.box((4.0, 4.0, 15.2), (12.0, 8.4, 15.3), 'fins', 'precooler condenser', faces=('south',))
    m.cyl((8.0, 9.0, 13.8), 1.4, 0.4, 'y', 'mesh', 'precooler fan')
    cabkit.fit(m, 3.8, 12.2, 1.4, 8.6, 15.2, side='south', col='paint:#c8ccd0', title='PRECOOLER|-40 C', volts='400V 50HZ', fns=(('ctl', '12V CTL'), ('main', 'COMPR'), ('scr', 'SCREEN')))
    m.g('bollards + canopy')
    for (x, z) in ((0.6, 0.6), (15.0, 0.6), (0.6, 14.6), (15.0, 14.6)):
        m.cyl((x + 0.4, 1.0, z + 0.4), 0.5, 5.0, 'y', 'paint:#f0c818', 'bollard')
        m.cyl((x + 0.4, 3.0, z + 0.4), 0.55, 0.6, 'y', 'paint:#202020', 'bollard band')
    m.box((2.0, 23.0, 3.0), (3.0, 32.0, 4.0), 'galv', 'canopy column')
    m.box((-8.0, 31.0, -4.0), (24.0, 32.0, 20.0), 'paint:#e8ecf0', 'canopy deck', faces=('up', 'down', 'north', 'south', 'east', 'west'))
    m.box((-8.0, 30.4, -4.2), (24.0, 31.0, -4.0), 'paint:#2a62c8', 'canopy fascia')
    for x in (-4.0, 4.0, 12.0, 20.0):
        m.box((x, 30.6, 4.0), (x + 2.0, 31.0, 12.0), 'lamp:fff0c0', 'canopy light')
    m.g('housing details')
    for y in (2.0, 6.0, 10.0):
        m.box((2.95, y, 4.4), (3.0, y + 2.6, 11.6), 'louvre', 'side vent', faces=('west',))
    m.box((12.9, 18.0, 5.0), (13.1, 21.0, 11.0), 'label:H2 350', 'side label', faces=('east',), east='label:H2 350')
    m.box((3.0, 1.0, 3.6), (13.0, 1.6, 4.0), 'paint:#2a62c8', 'housing kick')
    m.cyl((14.4, 1.0, 10.0), 0.7, 0.6, 'y', 'metal:steel', 'hose gland')
    detector(m, 4.0, 15.0, 4.0)
    detector(m, 12.0, 15.0, 4.0)
    beacon(m, 8.0, 23.0, 8.0, 'ff2020')
    m.g('front')
    screen(m, 4.0, 12.0, 13.0, 17.2, 4.0, 'dispenser screen')
    m.terminal_12v(6.0, 1.4, 12.0, 'south')
    m.box((1.2, 3.0, 6.0), (3.0, 7.0, 9.0), 'paint:#3a3f44', 'isolator box', west='danger:400V')
    m.cyl((0.9, 5.0, 7.5), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    f = m.surface('north', face=3.98, title='DISPENSER')
    m.ctl(f, 'digits', 'kg', X(8.0), 16.4, w=7.4, h=1.2, col='ffd040', label='', fmt='%s KG')
    m.ctl(f, 'digits', 'line1', X(8.0), 15.3, w=7.4, h=0.55, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(8.0), 14.5, w=7.4, h=0.55, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'line3', X(8.0), 13.7, w=7.4, h=0.55, col='ffb020', label='', fmt='%s')
    gauges(m, f, [('tankg', 'TANK'), ('barg', 'BAR'), ('tempg', 'DEG C'), ('lelg', 'LEL')], X(4.6), 11.2, step=2.25, size=1.8)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('locol', '8ad4ff', 'TRAIN'), ('nozl', 'ffb020', 'NOZZLE'), ('fill', '30e060', 'FILL', True), ('cool', '8ad4ff', 'COOL')],
             X(4.0), 8.6, step=1.6, size=0.45)
    lamp_row(m, f, [('zonel', 'ff3030', 'ZONE', True), ('esdl', 'ff3030', 'ESD', True), ('gas', 'ffb020', 'GAS'), ('leakl', 'ff3030', 'LEAK', True), ('hosel', 'ffb020', 'HOSE')],
             X(4.0), 7.3, step=1.6, size=0.45)
    lamp_row(m, f, [('bk1', '30e060', 'LOW'), ('bk2', '30e060', 'MED'), ('bk3', '30e060', 'HIGH')], X(4.0), 6.0, step=1.6, size=0.45)
    push_row(m, f, [('nozzle', 'ffb020', 'NOZZLE'), ('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP')], X(4.0), 4.4, step=2.0, size=0.85)
    push_row(m, f, [('reset', '2050d0', 'RESET'), ('hose', '505860', 'HOSE'), ('test', 'b0b0b0', 'TEST')], X(4.0), 2.6, step=2.0, size=0.75)
    m.ctl(f, 'key', 'k_cool', X(10.4), 6.0, w=0.9, h=0.9, col='c0a040', label='PRECOOL', pos=['OFF', 'PRECOOL'])
    lab(m, f, X(10.4), 5.2, 'PRECOOL')
    estop_plate(m, f, X(11.6), 3.4)
    s = m.surface('east', face=2.93, title='SPARES DRAWER')
    m.box((13.0, 2.0, 4.6), (13.05, 6.0, 7.4), 'paint:#c8ccd0', 'spares drawer', faces=('east',))
    m.ctl(s, 'zone', 'spares', 10.0, 4.0, w=2.6, h=3.8, col='000000', label='')
    m.save(item_gui=0.4)


# ===================================================================================================== items, states, recipes
def ash_texture():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    r = random.Random(5)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2) / 49 + ((y - 9) ** 2) / 30
            if d < 1:
                g = 70 + r.randint(0, 50)
                c = (g, g, g - 4, 255) if r.random() > 0.06 else (200, 80, 30, 255)
                img.putpixel((x, y), c)
    img.save(os.path.join(ASSETS, 'textures/items/loco_ash.png'))
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/loco_ash'}}, open(os.path.join(ASSETS, 'models/item/loco_ash.json'), 'w'), indent=1)


def P(meta): return {'item': 'irextras:part', 'data': meta}
PART = {'plate': 0, 'frame': 1, 'circuit': 2, 'motor': 3, 'hydraulic': 4, 'fan': 7, 'seal': 12, 'battery': 22, 'diode': 17, 'capacitor': 16, 'heatsink': 19, 'contactor': 18}

RECIPES = {
    'coal_bunker': (['SSS', 'SHS', 'B B'], {'S': ('minecraft:stone', 0), 'H': 'minecraft:hopper', 'B': 'minecraft:iron_bars'}),
    'coaling_tower': (['CHC', 'MBM', 'CFC'], {'C': ('minecraft:stone', 0), 'H': 'minecraft:hopper', 'M': P(PART['motor']), 'B': 'minecraft:iron_block', 'F': P(PART['frame'])}),
    'coal_pit': (['B B', 'BHB', 'SSS'], {'B': 'minecraft:iron_bars', 'H': 'minecraft:hopper', 'S': ('minecraft:stone', 0)}),
    'water_column': ([' PI', ' I ', 'CBV'], {'P': 'minecraft:iron_ingot', 'I': 'minecraft:iron_block', 'C': P(PART['circuit']), 'B': 'minecraft:bucket', 'V': P(PART['seal'])}),
    'ash_pit': (['RHR', 'BMB', 'SSS'], {'R': 'minecraft:iron_bars', 'H': 'minecraft:hopper', 'B': ('minecraft:brick_block', None), 'M': P(PART['motor']), 'S': ('minecraft:stone', 0)}),
    'sand_tower': (['HSH', 'FMF', 'PCP'], {'H': 'minecraft:hopper', 'S': ('minecraft:sand', 0), 'F': 'minecraft:furnace', 'M': P(PART['motor']), 'P': P(PART['plate']), 'C': P(PART['circuit'])}),
    'boiler_washout': (['TBT', 'MCM', 'PFP'], {'T': 'minecraft:cauldron', 'B': 'minecraft:bucket', 'M': P(PART['motor']), 'C': P(PART['circuit']), 'P': P(PART['plate']), 'F': P(PART['frame'])}),
    'charge_post': (['DCD', 'BKB', 'PFP'], {'D': P(PART['diode']), 'C': P(PART['circuit']), 'B': P(PART['battery']), 'K': P(PART['contactor']), 'P': P(PART['plate']), 'F': P(PART['frame'])}),
    'h2_compressor': (['SHS', 'MFM', 'PCP'], {'S': P(PART['seal']), 'H': P(PART['heatsink']), 'M': P(PART['motor']), 'F': P(PART['frame']), 'P': P(PART['plate']), 'C': P(PART['circuit'])}),
    'h2_storage': (['PPP', 'GSG', 'PPP'], {'P': P(PART['plate']), 'G': 'minecraft:glass_bottle', 'S': P(PART['seal'])}),
    'h2_dispenser': (['CSC', 'HFH', 'PKP'], {'C': P(PART['circuit']), 'S': P(PART['seal']), 'H': 'minecraft:leather', 'F': P(PART['frame']), 'P': P(PART['plate']), 'K': P(PART['capacitor'])}),
}


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


LANG = {
    'tile.irextras.coaling_tower.name': 'Coaling Tower', 'tile.irextras.coal_bunker.name': 'Coaling Tower Bunker', 'tile.irextras.coal_pit.name': 'Coal Receiving Pit', 'tile.irextras.water_column.name': 'Water Column',
    'tile.irextras.ash_pit.name': 'Ash Pit', 'tile.irextras.sand_tower.name': 'Sand Drier and Tower', 'tile.irextras.boiler_washout.name': 'Boiler Washout Plant',
    'tile.irextras.charge_post.name': 'Train Charging Post', 'tile.irextras.h2_compressor.name': 'Hydrogen Compressor', 'tile.irextras.h2_storage.name': 'Hydrogen Storage Tubes',
    'tile.irextras.h2_dispenser.name': 'Hydrogen Dispenser', 'item.irextras.loco_ash.name': 'Locomotive Ash',
}

BLOCKS = list(RECIPES)

if __name__ == '__main__':
    coaling_tower(); coal_bunker(); coal_pit(); water_column(); ash_pit(); sand_tower(); boiler_washout()
    charge_post(); h2_compressor(); h2_storage(); h2_dispenser()
    for n in BLOCKS:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    ash_texture()
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# batch 5 #2 steam servicing depot + #6 battery and hydrogen trains\n' + '\n'.join(add) + '\n')
    print('steam depot + battery / hydrogen plant written')
