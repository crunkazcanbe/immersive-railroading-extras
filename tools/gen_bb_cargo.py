#!/usr/bin/env python3
"""Cargo care (ChatGPT batch 3 #21-24): reefer plug post, wagon + container washout plant, axle-load weighbridge,
container tracking terminal. Models face north; controls at u = 16 - x on north surfaces. Live controls: see
freight/Tile*.java (ids must match). Writes models, controls, blockstates, item models and recipes (lang lines are
appended by hand)."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_economy import X
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE

COUNTS = {}


def digits(m, sf, cid, u, v, w, col='7cf29a', h=0.8):
    m.ctl(sf, 'digits', cid, u, v, w=w, h=h, col=col, label='', fmt='%s')


def label(m, sf, u, v, text, col='e8e8e8'):
    m.ctl(sf, 'label', '', u, v, w=0, h=0, col=col, label=text)


# ===================================================================================================== reefer plug post
def reefer_plug():
    m = Model('reefer_plug')
    m.g('foundation')
    m.box((1.0, 0, 2.0), (15.0, 1.0, 14.0), 'concrete', 'plinth')
    for x in (1.6, 14.4):
        for z in (2.6, 13.4):
            m.bolt(x, 1.0, z, 'up', 0.45)
    m.box((1.0, 0.0, 1.6), (15.0, 0.6, 2.0), 'haz', 'kerb hazard strip')
    m.g('pillar')
    m.box((2.4, 1.0, 4.0), (13.6, 28.0, 12.0), 'paint:#2a62a8', 'pillar cabinet')
    m.box((2.0, 28.0, 3.6), (14.0, 28.6, 12.4), 'paint:#1d4a80', 'roof')
    m.box((1.6, 28.6, 3.2), (14.4, 29.0, 12.8), 'paint:#16385f', 'rain canopy')
    m.box((1.6, 28.4, 3.2), (14.4, 28.6, 3.4), 'metal:alu', 'drip edge')
    m.box((2.6, 26.0, 3.9), (13.4, 27.8, 4.0), 'plate', 'title plate', north='plate:REEFER POWER 400V')
    m.box((2.6, 1.2, 3.9), (13.4, 3.6, 4.0), 'danger', 'danger plate', north='danger:DANGER 400V')
    m.box((2.5, 21.6, 3.95), (13.5, 26.0, 4.0), 'paint:#101418', 'display window')
    m.box((2.5, 11.8, 3.95), (13.5, 21.4, 4.0), 'paint:#1e2226', 'control fascia')
    for y in (4.0, 12.0, 20.0, 26.0):
        m.box((2.2, y, 4.2), (2.4, y + 1.2, 4.8), 'metal:steel', 'hinge')
    for x in (2.8, 13.2):
        for y in (11.9, 21.5, 27.6):
            m.bolt(x, y, 4.0, 'north', 0.3)
    m.g('sockets')
    for i in range(4):
        x = 12.0 - i * 2.67
        m.box((x - 1.1, 5.0, 3.0), (x + 1.1, 8.6, 4.0), 'paint:#c82020', 'CEE 400V socket body')
        m.box((x - 1.0, 8.6, 2.6), (x + 1.0, 9.2, 3.8), 'paint:#a01818', 'flip lid', rot={'axis': 'x', 'angle': 22.5, 'origin': [x, 8.6, 3.8]})
        m.cyl((x, 6.8, 2.8), 0.85, 0.2, 'z', 'paint:#7a1010', 'socket face')
        for k, (dx, dy) in enumerate(((-0.45, 0.25), (0.45, 0.25), (-0.45, -0.35), (0.45, -0.35), (0, 0.5))):
            m.box((x + dx - 0.1, 6.8 + dy - 0.1, 2.7), (x + dx + 0.1, 6.8 + dy + 0.1, 2.8), 'metal:brass', 'pin socket')
        m.box((x - 0.9, 4.2, 3.6), (x + 0.9, 4.9, 4.0), 'plate', 'socket label', north='label:S%d' % (i + 1))
        # the reefer cable hanging from the socket in a loop to the hook
        m.cyl((x, 3.6, 3.2), 0.25, 1.4, 'y', 'cable:202428', 'reefer cable drop')
    m.g('cable hooks')
    for i in range(4):
        z = 5.2 + i * 1.8
        m.box((0.6, 18.0, z - 0.2), (2.4, 18.4, z + 0.2), 'metal:steel', 'cable hook arm')
        m.box((0.6, 18.4, z - 0.2), (1.0, 19.2, z + 0.2), 'metal:steel', 'cable hook tip')
        for k in range(4):
            m.box((0.7, 14.0 + k * 1.0, z - 0.35), (1.6, 14.8 + k * 1.0, z + 0.35), 'cable:202428', 'coiled reefer cable')
    m.g('shore cable reel')
    m.cyl((14.2, 14.0, 8.0), 3.2, 0.4, 'x', 'paint:#f0c018', 'reel flange')
    m.cyl((15.6, 14.0, 8.0), 3.2, 0.4, 'x', 'paint:#f0c018', 'reel flange')
    m.cyl((14.6, 14.0, 8.0), 2.2, 1.0, 'x', 'cable:1d1d21', 'reeled shore cable')
    m.cyl((13.6, 14.0, 8.0), 0.5, 2.4, 'x', 'metal:steel', 'reel axle')
    m.box((13.6, 9.0, 7.4), (14.2, 14.0, 8.6), 'metal:steel', 'reel bracket')
    m.box((15.6, 8.0, 7.6), (16.0, 11.0, 8.4), 'cable:1d1d21', 'shore cable tail')
    m.box((15.2, 5.0, 7.0), (16.0, 8.0, 9.0), 'paint:#c82020', 'wagon plug')
    m.g('fuel bin')
    m.box((0.4, 1.0, 5.0), (2.4, 9.0, 11.0), 'paint:#3a3a3a', 'fuel bin')
    m.box((0.2, 9.0, 4.8), (2.6, 9.4, 11.2), 'paint:#2a2a2a', 'fuel bin lid')
    m.box((0.3, 4.0, 6.0), (0.4, 7.0, 10.0), 'plate', 'fuel bin label', west='label:GENSET FUEL')
    m.box((0.2, 7.6, 7.6), (0.4, 8.2, 8.4), 'metal:brass', 'bin handle')
    m.g('beacon + services')
    m.cyl((8.0, 29.0, 8.0), 0.9, 0.4, 'y', 'paint:#202020', 'beacon base')
    m.cyl((8.0, 29.4, 8.0), 0.7, 1.4, 'y', 'glass', 'alarm beacon')
    m.louvres(3.2, 6.8, 22.0, 25.4, 12.0, n=4)
    mains_box(m, 13.6, 1.4, 10.2, 'east')
    m.terminal_12v(2.4, 21.0, 8.0, 'west')
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 1.0, 9.0, 'down', r=0.35)
    m.box((3.0, 1.2, 12.0), (13.0, 2.0, 12.1), 'metal:copper', 'earth bar')
    for x in range(4, 13, 2):
        m.bolt(x, 1.6, 12.1, 'south', 0.25)
    for x in (0.6, 15.4):
        m.cyl((x, 1.0, 1.0), 0.5, 6.0, 'y', 'paint:#f0c018', 'impact bollard')
        m.box((x - 0.5, 4.0, 0.5), (x + 0.5, 4.6, 1.5), 'paint:#202020', 'bollard band')
    f = m.surface('north', face=3.85, title='REEFER POWER')
    for i in range(4):
        v = 25.2 - i * 0.95
        n = i + 1
        m.ctl(f, 'lamp', 'on%d' % n, 3.1, v, w=0.5, h=0.5, col=GREEN, label='')
        digits(m, f, 't%d' % n, 8.0, v, 8.4, col='7cf29a', h=0.7)
        m.ctl(f, 'lamp', 'al%d' % n, 12.9, v, w=0.5, h=0.5, col=RED, label='', blink=True)
    digits(m, f, 'car', 8.0, 21.0, 10.4, col='ffd040')
    digits(m, f, 'line1', 10.0, 20.0, 6.0, col='ff8030')
    digits(m, f, 'setp', 4.4, 20.0, 3.6, col='60c0ff')
    m.ctl(f, 'selector', 's_set', 11.6, 17.6, w=1.5, h=1.5, col='202020', label='SETPOINT', pos=['-25C', '-18C', '-5C', '0C', '+3C', '+5C', '+8C', '+13C'])
    m.ctl(f, 'key', 'k_car', 8.0, 17.6, w=1.2, h=1.2, col='c0a040', label='CAR SUPPLY', pos=['OFF', 'ON'])
    m.ctl(f, 'gauge', 'load', 4.4, 17.6, w=2.0, h=2.0, col='f2f0e6', label='LOAD FE', max=40)
    pushes(m, f, [('apply', '2050d0', 'APPLY'), ('pti', '30a050', 'PTI'), ('fuel', 'e08020', 'FUEL'), ('ack', 'f0d020', 'ACK'), ('test', 'f0f0f0', 'TEST')], 3.6, 14.8, step=2.2, size=1.0)
    lamps(m, f, [('carl', GREEN, 'CAR'), ('caral', RED, 'CAR AL', True), ('ptil', GREEN, 'PTI OK'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 3.6, 12.8, step=2.2, size=0.55)
    for i in range(4):
        m.ctl(f, 'key', 'k_s%d' % (i + 1), X(12.0 - i * 2.67), 10.2, w=1.0, h=1.0, col='c0a040', label='S%d' % (i + 1), pos=['OFF', 'ON'])
    m.ctl(f, 'zone', 'rack', 8.0, 6.8, w=10.6, h=4.0, col='000000', label='')
    side = m.surface('west', face=0.15, title='FUEL BIN')
    m.ctl(side, 'zone', 'fuelbin', 8.0, 5.0, w=6.0, h=7.6, col='000000', label='')
    COUNTS['reefer_plug'] = len(m.boxes)
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 3.0, 13.0, 12.0, 27.0, 4.0, side='south', col='paint:#2a62a8', title='REEFER POWER|POST', volts='400V 50HZ')
    m.save(item_gui=0.4)


# ======================================================================================================= washout plant
def washout_plant():
    m = Model('washout_plant')
    m.g('slab + bund')
    m.box((-15.0, 0, 0), (16.0, 0.4, 16.0), 'concrete', 'wash slab')
    m.box((-15.0, 0.4, 0), (-14.4, 1.0, 16.0), 'concrete', 'bund wall')
    for z in range(1, 16, 2):
        m.box((-12.0, 0.38, z), (2.0, 0.42, z + 0.3), 'paint:#3a3a3a', 'drain grating bar')
    m.box((-12.0, 0.3, 0.4), (2.0, 0.4, 15.6), 'paint:#2a2a2a', 'drain channel')
    m.g('plant house')
    m.box((4.0, 0.4, 2.0), (16.0, 20.0, 16.0), 'paint:#5a7a3a', 'plant house')
    m.box((3.6, 20.0, 1.6), (16.0, 20.8, 16.0), 'paint:#3e5a26', 'house roof')
    m.box((4.2, 18.4, 1.9), (15.8, 19.8, 2.0), 'plate', 'house sign', north='plate:WASHOUT PLANT')
    m.box((4.4, 0.8, 1.95), (15.6, 18.2, 2.0), 'paint:#1e2226', 'control fascia')
    m.box((15.95, 1.0, 9.0), (16.0, 12.0, 15.0), 'paint:#4a6a2a', 'house door', faces=('east',))
    m.box((15.95, 12.4, 10.0), (16.0, 14.4, 14.0), 'danger', 'door sign', east='danger:HOT STEAM')
    m.louvres(10.0, 15.0, 14.0, 17.6, 16.0, n=4)
    for x in (4.6, 15.4):
        for y in (1.2, 17.8):
            m.bolt(x, y, 2.0, 'north', 0.3)
    m.g('tanks')
    m.cyl((7.6, 20.8, 6.0), 3.0, 7.0, 'y', 'paint:#e0e4e8', 'clean water tank')
    m.box((4.8, 25.0, 2.8), (10.4, 26.6, 2.9), 'label:WATER', 'water tank label')
    m.cyl((13.0, 20.8, 6.0), 2.6, 6.0, 'y', 'paint:#6a5030', 'effluent tank')
    m.box((10.6, 24.0, 3.3), (15.4, 25.6, 3.4), 'label:EFFLUENT', 'effluent label')
    for x, r in ((7.6, 3.0), (13.0, 2.6)):
        for y in (22.0, 24.6):
            m.cyl((x, y, 6.0), r + 0.15, 0.3, 'y', 'metal:steel', 'tank band')
    m.cyl((10.4, 20.8, 12.8), 2.2, 4.8, 'y', 'paint:#303030', 'steam boiler')
    m.cyl((10.4, 25.6, 12.8), 0.5, 5.0, 'y', 'metal:steel', 'boiler flue')
    m.cyl((10.4, 30.4, 12.8), 0.8, 0.4, 'y', 'metal:steel', 'flue cap')
    m.cyl((13.6, 22.0, 13.6), 0.4, 2.0, 'y', 'metal:brass', 'safety valve')
    m.box((6.0, 20.8, 11.0), (8.0, 23.6, 15.0), 'paint:#f0c018', 'detergent dosing tank')
    m.box((6.1, 22.0, 10.9), (7.9, 23.2, 11.0), 'label:CHEM', 'chem label')
    m.g('pipework')
    for (x0, z) in ((7.6, 3.0), (13.0, 3.4)):
        m.cyl((x0, 18.0, z - 0.6), 0.35, 2.8, 'y', 'metal:steel', 'tank downpipe')
    m.cyl((-12.0, 26.0, 8.0), 0.45, 18.0, 'x', 'metal:steel', 'spray header to boom')
    m.cyl((4.0, 15.0, 3.6), 0.4, 11.0, 'y', 'metal:steel', 'boom feed riser')
    m.cyl((4.0, 15.0, 3.6), 0.6, 0.6, 'y', 'paint:#c82020', 'riser valve')
    m.g('spray boom')
    for z in (1.0, 15.0):
        m.box((1.0, 0.4, z - 0.6), (3.0, 28.0, z + 0.6), 'galv', 'boom column')
        m.box((0.6, 0.4, z - 1.0), (3.4, 1.0, z + 1.0), 'metal:steel', 'column foot')
    m.box((-14.0, 27.0, 0.4), (3.0, 28.4, 15.6), 'galv', 'boom beam')
    for x in range(-13, 2, 2):
        for z in (3.0, 8.0, 13.0):
            m.box((x, 26.2, z - 0.2), (x + 0.4, 27.0, z + 0.2), 'metal:brass', 'spray nozzle')
    m.box((-14.0, 2.0, 7.4), (-13.4, 27.0, 8.6), 'metal:steel', 'side spray riser')
    for y in range(4, 26, 3):
        m.box((-13.4, y, 7.8), (-13.0, y + 0.4, 8.2), 'metal:brass', 'side nozzle')
    m.g('steam lance')
    m.cyl((3.4, 6.0, 5.0), 0.3, 6.0, 'y', 'cable:202020', 'lance hose')
    m.box((2.8, 12.0, 4.6), (3.6, 12.6, 5.4), 'metal:steel', 'lance holster')
    m.box((3.0, 9.0, 4.8), (3.4, 12.0, 5.2), 'metal:steel', 'steam lance')
    m.g('services')
    mains_box(m, 16.0, 1.2, 6.0, 'east')
    m.terminal_12v(16.0, 5.0, 4.2, 'east')
    for z in (4.0, 12.0):
        m.box((-14.4, 0.4, z - 0.4), (-13.6, 4.0, z + 0.4), 'paint:#f0c018', 'stop post')
    m.box((-14.6, 4.0, 3.0), (-13.4, 5.4, 5.0), 'paint:#c82020', 'stop board', west='label:STOP')
    f = m.surface('north', face=1.85, title='WASHOUT')
    digits(m, f, 'line1', 6.0, 17.6, 10.0, col='7cf29a')
    digits(m, f, 'line2', 6.0, 16.6, 10.0, col='ffd040')
    digits(m, f, 'line3', 6.0, 15.6, 10.0, col='60c0ff')
    for i, (cid, lab) in enumerate((('water', 'WATER %'), ('efflg', 'EFFLUENT %'), ('prog', 'PROGRESS'), ('heat', 'BOILER'))):
        m.ctl(f, 'gauge', cid, 1.8 + i * 2.7, 13.0, w=2.1, h=2.1, col='f2f0e6', label=lab, max=100)
    m.ctl(f, 'selector', 's_prog', 1.8, 10.2, w=1.5, h=1.5, col='202020', label='PROGRAM', pos=['RINSE', 'DETERGENT', 'STEAM'])
    m.ctl(f, 'selector', 's_main', 4.5, 10.2, w=1.4, h=1.4, col='2050d0', label='WATER MAIN', pos=['SHUT', 'OPEN'])
    m.ctl(f, 'mushroom', 'm_estop', 10.0, 10.2, w=1.5, h=1.5, col='d02020', label='E-STOP')
    pushes(m, f, [('wash', '30a050', 'WASH'), ('stop', 'd02020', 'STOP'), ('drain', '806040', 'DRAIN'), ('fill', '2050d0', 'FILL'), ('cert', 'f0f0f0', 'CERT'), ('test', 'f0f0f0', 'TEST')], 1.4, 7.6, step=1.8, size=1.0)
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('run', GREEN, 'RUN'), ('done', BLUE, 'DONE'), ('carl', WHITE, 'WAGON'), ('boxl', WHITE, 'BOXES'), ('drainl', AMBER, 'DRAIN')], 1.4, 5.4, step=1.5, size=0.5)
    lamps(m, f, [('effl', RED, 'EFFL FULL', True), ('loww', AMBER, 'LOW H2O'), ('nochem', AMBER, 'NO CHEM'), ('nofuel', AMBER, 'NO FUEL'), ('haz', RED, 'HAZ LAST'), ('fault', RED, 'FAULT', True)], 1.4, 3.6, step=1.7, size=0.5)
    for i, (cid, lab) in enumerate((('chem', 'DETERGENT'), ('fuel', 'COAL'), ('bay', 'BOX BAY'), ('tray', 'CERTS'))):
        u = 1.8 + i * 2.7
        m.box((X(u) - 1.1, 0.8, 1.7), (X(u) + 1.1, 2.4, 1.95), 'metal:steel', lab.lower() + ' hatch')
        m.ctl(f, 'zone', cid, u, 1.6, w=2.2, h=1.6, col='000000', label='')
        label(m, f, u, 2.7, lab)
    COUNTS['washout_plant'] = len(m.boxes)
    m.save(item_gui=0.3)


# =================================================================================================== axle weighbridge
def axle_weighbridge():
    m = Model('axle_weighbridge')
    m.g('foundation')
    m.box((1.0, 0, 4.0), (15.0, 0.8, 15.0), 'concrete', 'cabinet plinth')
    m.g('load cells (toward the rail)')
    for x in (2.0, 9.0):
        m.box((x, 0, -14.0), (x + 5.0, 0.6, 4.0), 'metal:steel', 'weigh rail beam')
        for z in range(-13, 4, 3):
            m.box((x + 0.5, 0.0, z), (x + 4.5, 0.3, z + 1.2), 'paint:#2a62a8', 'load cell')
            m.bolt(x + 1.0, 0.6, z + 0.6, 'up', 0.3)
            m.bolt(x + 4.0, 0.6, z + 0.6, 'up', 0.3)
        m.box((x + 1.5, 0.6, 1.0), (x + 3.5, 1.6, 3.0), 'ral7035', 'junction box')
        m.cyl((x + 2.5, 0.6, 3.0), 0.2, 1.0, 'z', 'cable:1d1d21', 'cell cable')
    m.box((2.0, 0, -14.6), (14.0, 0.2, -14.0), 'haz', 'approach marker')
    m.g('cabinet')
    m.box((3.0, 0.8, 6.0), (13.0, 22.0, 14.0), 'ral7035', 'instrument cabinet')
    m.box((2.8, 22.0, 5.8), (13.2, 22.6, 14.2), 'ral7035', 'cabinet roof')
    m.box((3.1, 1.0, 5.95), (12.9, 21.8, 6.0), 'paint:#1e2226', 'cabinet door fascia')
    m.box((3.2, 21.0, 5.9), (12.8, 21.9, 5.95), 'plate', 'title', north='plate:AXLE WEIGHBRIDGE')
    m.box((3.2, 14.8, 5.92), (12.8, 21.0, 5.95), 'paint:#081008', 'indicator window')
    for x in (3.4, 12.6):
        for y in (1.4, 14.4, 21.4):
            m.bolt(x, y, 6.0, 'north', 0.28)
    for y in (3.0, 10.0, 18.0):
        m.box((2.8, y, 6.2), (3.0, y + 1.2, 6.8), 'metal:steel', 'hinge')
    m.box((12.6, 9.0, 5.6), (12.9, 11.0, 5.95), 'metal:brass', 'door lock')
    m.louvres(4.0, 12.0, 2.0, 4.0, 14.0, n=4)
    m.g('printer + signal')
    m.box((13.0, 8.0, 7.0), (14.6, 11.0, 11.0), 'paint:#303438', 'ticket printer')
    m.box((14.6, 9.8, 8.0), (14.7, 10.2, 10.0), 'paint:#f4f4f0', 'ticket slot')
    m.box((13.6, 22.6, 9.0), (14.4, 30.0, 9.8), 'galv', 'verdict signal post')
    m.box((12.8, 26.0, 8.6), (15.2, 30.0, 9.0), 'paint:#101010', 'verdict signal head')
    for y, col in ((28.8, 'paint:#2a7a2a'), (27.0, 'paint:#7a1a1a')):
        m.cyl((14.0, y, 8.6), 0.7, 0.2, 'z', col, 'signal lens')
        m.box((13.2, y + 0.7, 8.2), (14.8, y + 0.9, 8.6), 'paint:#101010', 'lens hood')
    m.g('services')
    mains_box(m, 3.0, 1.4, 10.0, 'west')
    m.terminal_12v(13.0, 1.6, 12.6, 'east')
    for x in (5.0, 8.0, 11.0):
        m.gland(x, 0.8, 10.0, 'down', r=0.3)
    m.box((4.0, 0.8, 13.9), (12.0, 1.6, 14.0), 'metal:copper', 'earth bar')
    f = m.surface('north', face=5.85, title='WEIGHBRIDGE')
    digits(m, f, 'car', 8.0, 20.4, 9.0, col='ffd040', h=0.7)
    digits(m, f, 'tot', 8.0, 19.5, 9.0)
    digits(m, f, 'ax', 8.0, 18.6, 9.0, col='60c0ff', h=0.7)
    digits(m, f, 'lat', 5.6, 17.7, 4.2)
    digits(m, f, 'lon', 10.4, 17.7, 4.2)
    digits(m, f, 'verd', 8.0, 16.6, 9.0, col='ff5050')
    digits(m, f, 'limit', 8.0, 15.6, 6.0, col='ff8030', h=0.6)
    m.ctl(f, 'gauge', 'maxax', 5.4, 12.6, w=2.2, h=2.2, col='f2f0e6', label='MAX AXLE T', max=40)
    m.ctl(f, 'gauge', 'latg', 8.0, 12.6, w=2.2, h=2.2, col='f2f0e6', label='SIDE %', max=50)
    m.ctl(f, 'gauge', 'long', 10.6, 12.6, w=2.2, h=2.2, col='f2f0e6', label='END %', max=60)
    m.ctl(f, 'selector', 's_limit', 4.8, 9.4, w=1.4, h=1.4, col='202020', label='LIMIT', pos=['CFG', '16T', '18T', '20T', '22.5T', '25T', '30T', '32.5T'])
    m.ctl(f, 'selector', 's_mode', 7.2, 9.4, w=1.3, h=1.3, col='202020', label='MODE', pos=['STATIC', 'IN MOTION'])
    m.ctl(f, 'selector', 's_cal', 9.6, 9.4, w=1.2, h=1.2, col='404040', label='CAL', pos=['0', '+1%', '+2%', '-2%', '-1%'])
    m.ctl(f, 'key', 'k_hold', 11.8, 9.4, w=1.1, h=1.1, col='c0a040', label='HOLD', pos=['ALARM', 'STOP TRAIN'])
    pushes(m, f, [('reset', 'f0d020', 'RESET'), ('print', '2050d0', 'PRINT'), ('test', 'f0f0f0', 'TEST')], 5.4, 6.6, step=2.6, size=1.0)
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('inmot', WHITE, 'ON BRIDGE'), ('ok', GREEN, 'OK'), ('over', RED, 'OVERLOAD', True), ('imbal', AMBER, 'UNBAL', True)], 4.2, 4.4, step=1.5, size=0.5)
    lamps(m, f, [('fast', AMBER, 'TOO FAST'), ('stopl', RED, 'TRAIN HELD'), ('holdl', AMBER, 'HOLD ARMED')], 5.6, 2.6, step=2.4, size=0.5)
    sig = m.surface('north', face=8.55, title='VERDICT')
    m.ctl(sig, 'lamp', 'ok', X(14.0), 28.8, w=1.3, h=1.3, col=GREEN, label='')
    m.ctl(sig, 'lamp', 'over', X(14.0), 27.0, w=1.3, h=1.3, col=RED, label='', blink=True)
    COUNTS['axle_weighbridge'] = len(m.boxes)
    m.save(item_gui=0.4)


# ================================================================================================= container tracker
def container_tracker():
    m = Model('container_tracker')
    m.g('pedestal')
    m.box((3.0, 0, 4.4), (13.0, 0.6, 12.6), 'paint:#2a2d30', 'base plate')
    for x in (3.6, 12.4):
        for z in (5.0, 12.0):
            m.bolt(x, 0.6, z, 'up', 0.35)
    m.box((3.6, 0.6, 5.0), (12.4, 11.0, 12.0), 'paint:#1f5fa8', 'pedestal body')
    m.box((3.4, 11.0, 4.0), (12.6, 11.6, 12.2), 'paint:#14406f', 'desk top')
    m.box((3.5, 1.0, 4.95), (12.5, 10.6, 5.0), 'paint:#1e2226', 'front fascia')
    m.box((4.0, 6.0, 4.6), (12.0, 6.4, 5.0), 'metal:steel', 'scan tray lip')
    m.box((4.0, 4.0, 4.0), (12.0, 6.0, 5.0), 'paint:#303438', 'scan tray')
    m.box((4.2, 1.2, 4.9), (11.8, 3.6, 4.95), 'plate', 'tray plate', north='plate:SCAN TRAY')
    m.g('keyboard')
    for r in range(3):
        for c in range(8):
            m.box((4.2 + c * 0.95, 11.6, 5.0 + r * 0.9), (4.9 + c * 0.95, 11.9, 5.7 + r * 0.9), 'paint:#2a2a2a', 'key')
    m.box((4.0, 11.6, 8.0), (12.0, 11.7, 9.4), 'paint:#101010', 'barcode reader pad')
    m.cyl((12.0, 11.6, 10.6), 0.5, 0.6, 'y', 'paint:#303030', 'barcode gun holster')
    m.box((11.6, 12.2, 10.2), (12.4, 13.6, 11.0), 'paint:#f0c018', 'barcode gun')
    m.g('monitor')
    m.box((7.2, 11.6, 10.0), (8.8, 15.0, 11.4), 'metal:steel', 'monitor stem')
    m.box((3.4, 15.0, 6.0), (12.6, 26.6, 7.2), 'paint:#202226', 'monitor housing')
    m.box((3.6, 15.4, 5.95), (12.4, 26.2, 6.0), 'paint:#03140a', 'screen')
    m.box((3.4, 26.6, 6.0), (12.6, 27.4, 7.2), 'paint:#1f5fa8', 'monitor brand strip', north='label:BOX TRACKING')
    for x in (4.0, 12.0):
        m.box((x - 0.3, 15.0, 7.2), (x + 0.3, 26.6, 7.6), 'paint:#181a1c', 'housing rib')
    m.g('printer')
    m.box((12.6, 11.6, 6.0), (15.4, 14.0, 9.6), 'paint:#303438', 'receipt printer')
    m.box((12.8, 13.4, 5.9), (15.2, 13.6, 6.0), 'paint:#f4f4f0', 'paper slot')
    m.g('services')
    m.terminal_12v(12.4, 1.4, 8.6, 'east')
    mains_box(m, 3.6, 1.4, 8.6, 'west')
    m.cyl((8.0, 0.6, 11.0), 0.4, 0.6, 'y', 'metal:brass', 'cable gland')
    m.box((4.0, 0.6, 11.8), (12.0, 1.2, 12.0), 'metal:copper', 'earth bar')
    for k in range(6):
        m.box((4.2 + k * 1.3, 7.0, 4.9), (4.8 + k * 1.3, 9.6, 4.95), 'paint:#2a2e33', 'vent slot')
    m.g('rear trunking + side grilles')
    m.box((5.0, 0.6, 12.0), (11.0, 11.0, 12.6), 'ral7035', 'rear cable trunking')
    for y in (2.0, 5.0, 8.0):
        m.box((5.2, y, 12.6), (10.8, y + 0.3, 12.7), 'metal:steel', 'trunking clip')
    for x in (3.55, 12.45):
        for y in range(2, 10, 2):
            m.box((x - 0.05, y, 6.0), (x + 0.05, y + 0.8, 11.0), 'paint:#14406f', 'side grille bar')
    m.box((3.0, 27.4, 6.4), (13.0, 27.8, 6.8), 'paint:#14406f', 'sun hood')
    scr = m.surface('north', face=5.9, title='TRACKING')
    for i, (cid, col) in enumerate((('id', 'ffd040'), ('st', '7cf29a'), ('loc', '7cf29a'), ('who', '7cf29a'), ('cond', '60c0ff'), ('last', 'e0e0e0'))):
        digits(m, scr, cid, 8.0, 25.2 - i * 1.15, 8.4, col=col, h=0.75)
    digits(m, scr, 'cnt', 8.0, 17.6, 6.0, col='ff8030', h=0.6)
    lamps(m, scr, [('valid', GREEN, 'CHK OK'), ('bad', RED, 'CHK BAD', True), ('flag', AMBER, 'FLAG', True), ('sealed', GREEN, 'SEALED'),
                   ('reeferl', BLUE, 'REEFER'), ('contam', RED, 'CONTAM'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 4.3, 16.5, step=1.05, size=0.42, lab_dv=0.5)
    fr = m.surface('north', face=3.95, title='TERMINAL')
    pushes(m, fr, [('scan', '30a050', 'SCAN'), ('find', '2050d0', 'FIND'), ('prev', 'e0e0e0', 'PREV'), ('next', 'e0e0e0', 'NEXT'), ('print', 'f0d020', 'PRINT'), ('test', 'f0f0f0', 'TEST')],
           4.4, 9.6, step=1.45, size=0.9)
    m.ctl(fr, 'selector', 's_filter', 8.0, 7.4, w=1.3, h=1.3, col='202020', label='FILTER', pos=['ALL', 'MOVING', 'FLAGGED', 'SEALED', 'REEFER', 'DIRTY'])
    m.ctl(fr, 'zone', 'scan', 8.0, 5.0, w=8.0, h=2.0, col='000000', label='')
    COUNTS['container_tracker'] = len(m.boxes)
    m.save(item_gui=0.45)


RECIPES = {
    'reefer_plug': (['IPI', 'CFC', 'IBI'], {'I': 'minecraft:iron_ingot', 'P': 'minecraft:packed_ice', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'B': 'minecraft:iron_bars'}),
    'washout_plant': (['WBW', 'CFC', 'IHI'], {'W': 'minecraft:water_bucket', 'B': 'minecraft:cauldron', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot', 'H': 'minecraft:hopper'}),
    'axle_weighbridge': (['LCL', 'IFI', 'PPP'], {'L': 'minecraft:redstone_lamp', 'C': 'irextras:part@2', 'I': 'minecraft:iron_ingot', 'F': 'irextras:part@1', 'P': 'minecraft:heavy_weighted_pressure_plate'}),
    'container_tracker': (['GLG', 'CPC', 'IBI'], {'G': 'minecraft:glass_pane', 'L': 'minecraft:redstone_lamp', 'C': 'irextras:part@2', 'P': 'minecraft:paper', 'I': 'minecraft:iron_ingot', 'B': 'minecraft:stone_button'}),
}
FACING = list(RECIPES)


def ing(v):
    if '@' in v:
        i, d = v.split('@')
        return {'item': i, 'data': int(d)}
    return {'item': v}


if __name__ == '__main__':
    reefer_plug(); washout_plant(); axle_weighbridge(); container_tracker()
    for n in FACING:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    for n, c in COUNTS.items():
        print('%-20s %d boxes' % (n, c))
