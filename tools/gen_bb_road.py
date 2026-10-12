#!/usr/bin/env python3
"""Road traffic (batch 5 #3) and trams (batch 5 #4).

Blocks: traffic signal controller cabinet, 3-aspect traffic signal head, pedestrian crossing unit (push button + WAIT
lamp + red / green man), tram signal (white-bar aspects), detection loop, tram stop shelter with its real-time display
(PIS), ticket validator post, lit platform edge marker, tramway OHLE street pole with its LINE LIVE indicator, and the
street-running tram road (asphalt with grooved rails, also IR's bed fill). Vehicles: car, van, box truck and bus - each
as two models, `road_<kind>` (glass, tyres, trim, lamp lenses) and `road_<kind>_paint` (only the painted panels, near
white: road/client/RoadClient tints it to the vehicle's colour). Cars + vans are modelled at HALF size (drawn x2), the
truck at a THIRD (x3), the bus at a QUARTER (x4); the lamp overlay boxes RoadClient flashes (brake lights, indicators,
headlights) are printed at the end as Java arrays and must match LAMPS here.

Models face north; controls on north surfaces sit at u = 16 - x, on south surfaces at u = x. Live controls:
road/Tile*.java (ids must match). Writes models, controls, blockstates, item models and recipes (lang lines by hand)."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, WHITE, ing
from gen_bb_economy import X

AMB2, GRN2, RED2 = 'ffa018', '30ff70', 'ff2a20'


def pole(m, x, z, y0, y1, r=0.8, mat='galv', name='pole'):
    m.cyl((x, y0, z), r, y1 - y0, 'y', mat, name)


def base_plate(m, x, z, r=2.2):
    m.box((x - r, 0, z - r), (x + r, 0.4, z + r), 'galv', 'base plate')
    for dx in (-r + 0.5, r - 0.5):
        for dz in (-r + 0.5, r - 0.5):
            m.cyl((x + dx, 0.4, z + dz), 0.3, 0.4, 'y', 'metal:steel', 'anchor nut')


def rivets(m, x0, x1, y0, y1, z, n=4, face='north', s=0.3):
    """a ring of bolt heads round a rectangle on a north / south face"""
    for i in range(n):
        x = x0 + (x1 - x0) * i / max(1, n - 1)
        m.bolt(x, y0, z, face, s); m.bolt(x, y1, z, face, s)
    for j in range(1, max(2, n - 1)):
        y = y0 + (y1 - y0) * j / max(1, n - 1)
        m.bolt(x0, y, z, face, s); m.bolt(x1, y, z, face, s)


def pole_top(m, x, z, y, r=0.8):
    m.cyl((x, y, z), r * 1.15, 0.4, 'y', 'paint:#2a2e33', 'pole cap')
    m.cyl((x, y + 0.4, z), r * 0.6, 0.3, 'y', 'paint:#2a2e33', 'cap dome')
    for k in range(3):
        m.cyl((x, 3.0 + k * 0.5, z), r * 1.08, 0.2, 'y', 'paint:#f0f0f0' if k % 2 == 0 else 'paint:#202020', 'pole sleeve band')
    m.box((x - r - 0.05, 1.6, z - 0.6), (x - r + 0.05, 2.8, z + 0.6), 'paint:#2a2e33', 'pole access door')
    m.cyl((x - r - 0.05, 2.2, z), 0.15, 0.1, 'x', 'metal:brass', 'door lock')


def hood(m, x0, x1, y, z, depth=2.6, col='paint:#121214'):
    """a lens visor: a top and two cheeks sticking out toward -z"""
    m.box((x0 - 0.2, y, z - depth), (x1 + 0.2, y + 0.25, z), col, 'visor top')
    m.box((x0 - 0.2, y - (x1 - x0) * 0.55, z - depth * 0.7), (x0, y, z), col, 'visor cheek', rot=None)
    m.box((x1, y - (x1 - x0) * 0.55, z - depth * 0.7), (x1 + 0.2, y, z), col, 'visor cheek')


# =========================================================================================== traffic signal controller
def traffic_controller():
    m = Model('traffic_controller')
    m.g('plinth')
    m.box((0.6, 0, 3.0), (15.4, 2.0, 15.0), 'concrete', 'concrete plinth')
    m.box((1.0, 2.0, 3.4), (15.0, 2.4, 14.6), 'paint:#2e3236', 'cabinet base frame')
    m.g('cabinet')
    m.box((1.2, 2.4, 4.0), (14.8, 27.0, 14.4), 'paint:#5b6a5e', 'cabinet body')
    m.box((0.9, 27.0, 3.6), (15.1, 28.0, 14.8), 'paint:#4e5c50', 'rain hood')
    m.box((1.4, 28.0, 4.4), (14.6, 28.4, 14.0), 'paint:#4e5c50', 'hood crown')
    # louvred vents on both sides
    for x, face in ((1.2, 'west'), (14.8, 'east')):
        for i in range(6):
            y = 20.0 + i * 0.9
            m.box((x - 0.15 if face == 'west' else x, y, 6.0), (x if face == 'west' else x + 0.15, y + 0.4, 12.4), 'paint:#3e4a40', 'side vent louvre')
    m.g('door')
    m.box((1.6, 2.8, 3.8), (14.4, 26.6, 4.0), 'paint:#62725f', 'door')
    for y in (4.6, 24.6):
        m.box((14.4, y, 3.7), (14.8, y + 1.6, 4.1), 'metal:steel', 'hinge')
    m.box((1.8, 13.0, 3.6), (2.4, 16.0, 3.8), 'metal:steel', 'door handle')
    m.cyl((2.1, 17.0, 3.6), 0.35, 0.2, 'z', 'metal:brass', 'triangular key lock')
    m.box((3.0, 24.4, 3.7), (13.0, 26.0, 3.8), 'plate', 'maker plate', north='plate:PRIDE TRAFFIC SYSTEMS')
    m.box((3.2, 3.2, 3.7), (6.8, 4.6, 3.8), 'warn', 'shock sign')
    m.box((7.4, 3.2, 3.7), (12.8, 4.6, 3.8), 'plate', 'site plate', north='label:SITE TS-1')
    rivets(m, 1.9, 14.1, 3.1, 26.3, 3.8, n=8, s=0.22)
    for (a, b) in (((1.5, 2.7), (14.5, 2.8)), ((1.5, 26.6), (14.5, 26.7)), ((1.5, 2.7), (1.6, 26.7)), ((14.4, 2.7), (14.5, 26.7))):
        m.box((a[0], a[1], 3.75), (b[0], b[1], 4.0), 'rubber', 'door seal')
    m.g('earthing + ducts')
    m.box((13.0, 2.4, 14.4), (14.6, 3.6, 14.8), 'metal:copper', 'earth boss')
    m.cyl((13.8, 0.2, 14.9), 0.18, 2.6, 'y', 'cable:3a9a3a', 'earth tail')
    for x in (3.0, 6.0, 10.0, 13.0):
        m.cyl((x, -0.4, 15.2), 0.55, 2.0, 'y', 'paint:#2a2a2c', 'duct stub')
        m.cyl((x, 1.6, 15.2), 0.7, 0.3, 'y', 'paint:#2a2a2c', 'duct collar')
    m.box((4.0, 28.4, 6.0), (12.0, 28.8, 12.0), 'paint:#4e5c50', 'lifting eye plate')
    for x in (5.0, 11.0):
        m.cyl((x, 28.8, 9.0), 0.5, 0.6, 'y', 'metal:steel', 'lifting eye')
    # ---- the police / engineer panel behind the window: screen, lamps, keys, timing buttons
    m.g('engineer panel')
    m.box((2.6, 6.0, 3.55), (13.4, 23.6, 3.8), 'paint:#22262a', 'panel recess')
    m.box((2.8, 15.8, 3.5), (13.2, 23.2, 3.55), 'paint:#03140a', 'timing screen')
    sf = m.surface('north', face=3.48, title='TRAFFIC SIGNAL CONTROLLER')
    rows = ['stage', 'tleft', 'tim1', 'tim2', 'tim3', 'cnt', 'tram']
    cols = ['ffd040', 'ffffff', '7cf29a', '7cf29a', '7cf29a', '5bcefa', 'ff9ad0']
    h = (23.0 - 16.0) / len(rows)
    for i, cid in enumerate(rows):
        m.ctl(sf, 'digits', cid, X(8.0), 23.0 - h * (i + 0.5), w=9.8, h=h * 0.85, col=cols[i], label='', fmt='%s')
    # stage lamps (green) and demand lamps (amber), A B C D PED TRAM
    grp = ['A', 'B', 'C', 'D', 'P', 'T']
    for i, g in enumerate(grp):
        u = X(3.6 + i * 1.75)
        m.ctl(sf, 'lamp', 'g' + g, u, 14.6, w=0.7, h=0.7, col=GREEN, label=g)
        m.ctl(sf, 'lamp', 'd' + g, u, 13.4, w=0.6, h=0.6, col=AMBER, label='DEM ' + g, blink=True)
        m.ctl(sf, 'label', '', u, 12.6, w=0, h=0, col='e8e8e8', label=('PED' if g == 'P' else 'TRAM' if g == 'T' else g))
    # mode / timing selectors, keys
    m.ctl(sf, 'selector', 's_mode', X(4.0), 10.4, w=1.4, h=1.4, col='202020', label='MODE', pos=['AUTO', 'FIXED', 'MANUAL', 'FLASH', 'OFF'])
    m.ctl(sf, 'label', '', X(4.0), 9.1, w=0, h=0, col='e8e8e8', label='MODE')
    m.ctl(sf, 'selector', 's_tsel', X(7.0), 10.4, w=1.4, h=1.4, col='202020', label='TIMING', pos=['MIN GRN', 'MAX GRN', 'AMBER', 'ALL RED', 'PED', 'TRAM'])
    m.ctl(sf, 'label', '', X(7.0), 9.1, w=0, h=0, col='e8e8e8', label='TIMING')
    m.ctl(sf, 'push', 'tdn', X(9.2), 10.4, w=0.9, h=0.9, col='d8d8d0', label='-')
    m.ctl(sf, 'push', 'tup', X(10.6), 10.4, w=0.9, h=0.9, col='d8d8d0', label='+')
    m.ctl(sf, 'label', '', X(9.9), 9.3, w=0, h=0, col='e8e8e8', label='SET')
    m.ctl(sf, 'key', 'k_police', X(12.4), 10.4, w=1.1, h=1.1, col='c0a040', label='POLICE', pos=['OFF', 'MANUAL'])
    m.ctl(sf, 'label', '', X(12.4), 9.1, w=0, h=0, col='e8e8e8', label='POLICE')
    pushes(m, sf, [('adv', '30a040', 'ADV'), ('allred', 'd02020', 'ALL RED'), ('pedtest', 'e0e0e0', 'PED'), ('tramtest', 'e0e0e0', 'TRAM'),
                   ('log', 'e0e0e0', 'LOG'), ('test', 'f0f0f0', 'TEST')], X(3.4), 7.6, step=-1.6, size=0.85)
    lamps(m, sf, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('flt', RED, 'FAULT', True), ('allr', RED, 'ALL RED'), ('pol', AMBER, 'POLICE', True)],
          X(4.0), 5.2 + 0.0, step=-1.9, size=0.5)
    # ---- the cable chamber side: terminal blocks + glands
    m.g('cable entry')
    for i in range(6):
        m.gland(3.0 + i * 2.0, 2.4, 9.0, 'down', r=0.35, length=0.6)
    m.terminal_12v(14.8, 4.0, 9.0, 'east')
    mains_box(m, 1.2, 4.0, 9.0, 'west')
    m.save(item_gui=0.5)
    return m


# =============================================================================================== traffic signal head
def traffic_signal():
    m = Model('traffic_signal')
    m.g('pole')
    base_plate(m, 8, 10)
    pole(m, 8, 10, 0.4, 30.0, r=0.7)
    m.box((7.0, 8.6, 9.0), (9.0, 10.2, 11.0), 'paint:#c8c8c8', 'pole band')
    m.box((6.8, 7.0, 10.6), (9.2, 8.4, 10.8), 'plate', 'pole number', south='label:TS 1')
    m.g('head')
    # head housing (black), yellow-bordered backboard
    m.box((5.0, 15.4, 6.6), (11.0, 31.0, 9.4), 'paint:#141416', 'signal housing')
    m.box((3.6, 14.6, 9.4), (12.4, 31.8, 9.7), 'paint:#101012', 'backboard')
    for (a, b) in (((3.6, 14.6), (12.4, 15.1)), ((3.6, 31.3), (12.4, 31.8)), ((3.6, 14.6), (4.1, 31.8)), ((11.9, 14.6), (12.4, 31.8))):
        m.box((a[0], a[1], 9.35), (b[0], b[1], 9.45), 'paint:#f0f0f0', 'retroreflective border')
    for y in (17.0, 29.0):
        m.box((7.2, y, 9.7), (8.8, y + 1.0, 10.4), 'metal:steel', 'head bracket')
    # three lenses with visors; the lamps are live controls drawn over the dark lenses
    for i, (cy, col) in enumerate(((28.4, '3a0806'), (23.2, '3a2804'), (18.0, '06301a'))):
        m.cyl((8.0, cy, 6.4), 2.3, 0.2, 'z', 'paint:#202022', 'lens ring')
        m.cyl((8.0, cy, 6.35), 1.95, 0.08, 'z', 'paint:#' + col, 'dark lens')
        hood(m, 5.6, 10.4, cy + 2.2, 6.4, depth=2.6)
    sf = m.surface('north', face=6.2, title='SIGNAL')
    for cid, cy, col in (('r', 28.4, RED2), ('a', 23.2, AMB2), ('g', 18.0, GRN2)):
        m.ctl(sf, 'lamp', cid, X(8.0), cy, w=3.6, h=3.6, col=col, label={'r': 'RED', 'a': 'AMBER', 'g': 'GREEN'}[cid])
    # the back: phase-group plug and the ID plate
    m.g('back')
    m.box((6.0, 21.0, 9.7), (10.0, 25.0, 10.6), 'ral7035', 'group plug box')
    bk = m.surface('south', face=16 - 10.62, title='HEAD')
    m.ctl(bk, 'selector', 's_grp', 8.0, 23.0, w=1.6, h=1.6, col='202020', label='GROUP', pos=['A', 'B', 'C', 'D'])
    m.ctl(bk, 'label', '', 8.0, 21.6, w=0, h=0, col='e8e8e8', label='PHASE GROUP')
    m.cyl((8.0, 14.0, 10.0), 0.3, 1.4, 'y', 'cable', 'head cable')
    pole_top(m, 8, 10, 30.0, 0.7)
    rivets(m, 4.0, 12.0, 15.0, 31.4, 9.7, n=5, face='south', s=0.2)
    for cy in (28.4, 23.2, 18.0):
        m.box((10.9, cy - 1.2, 7.0), (11.2, cy + 1.2, 9.2), 'metal:steel', 'lamp door hinge')
        m.box((4.8, cy - 0.4, 7.4), (5.0, cy + 0.4, 8.6), 'metal:steel', 'lamp door catch')
        m.box((5.2, cy - 2.6, 6.6), (10.8, cy - 2.5, 9.4), 'paint:#0c0c0e', 'aspect divider')
    m.gland(8.0, 15.4, 9.0, "down", r=0.25, length=0.4)
    m.box((5.0, 14.8, 7.0), (11.0, 15.4, 9.2), 'paint:#141416', 'head bottom cap')
    m.box((6.2, 20.0, 10.6), (9.8, 20.8, 10.7), 'plate', 'group plate', south='label:A B C D')
    m.save(item_gui=0.4)
    return m


# ============================================================================================ pedestrian crossing unit
def ped_unit():
    m = Model('ped_unit')
    m.g('pole')
    base_plate(m, 8, 10)
    pole(m, 8, 10, 0.4, 30.0, r=0.7)
    m.g('push button box')
    m.box((5.4, 13.0, 7.8), (10.6, 19.6, 9.4), 'paint:#f0d020', 'push button unit')
    m.box((5.6, 13.2, 7.7), (10.4, 19.4, 7.8), 'paint:#e8c818', 'unit front')
    m.box((6.0, 18.0, 7.6), (10.0, 19.0, 7.7), 'plate', 'instruction', north='label:PUSH BUTTON')
    m.box((6.0, 13.6, 7.6), (10.0, 14.4, 7.7), 'plate', 'instruction 2', north='label:WAIT FOR GREEN')
    m.box((6.4, 9.6, 8.6), (9.6, 13.0, 9.4), 'paint:#f0d020', 'tactile box')
    m.cyl((8.0, 9.4, 8.2), 0.5, 0.6, 'y', 'paint:#202020', 'tactile cone')
    m.box((7.0, 13.0, 9.4), (9.0, 19.6, 10.2), 'metal:steel', 'unit clamp')
    sf = m.surface('north', face=7.58, title='PEDESTRIANS')
    m.ctl(sf, 'push', 'ped', X(8.0), 15.4, w=1.6, h=1.6, col='202020', label='PUSH')
    m.ctl(sf, 'lamp', 'wait', X(8.0), 17.3, w=3.2, h=0.8, col='ff7a20', label='WAIT')
    m.ctl(sf, 'label', '', X(8.0), 17.3, w=0, h=0, col='303030', label='WAIT')
    m.g('ped head')
    m.box((5.0, 21.0, 7.0), (11.0, 30.6, 9.4), 'paint:#141416', 'ped head housing')
    for y in (21.0, 26.0):
        m.cyl((8.0, y + 2.4, 6.95), 2.1, 0.05, 'z', 'paint:#1a1a1a', 'man lens')
        hood(m, 5.6, 10.4, y + 4.6, 7.0, depth=1.8)
    m.ctl(sf, 'lamp', 'rman', X(8.0), 28.4, w=3.0, h=3.6, col=RED2, label='DON\'T WALK')
    m.ctl(sf, 'lamp', 'gman', X(8.0), 23.4, w=3.0, h=3.6, col=GRN2, label='WALK')
    for y in (21.6, 28.0):
        m.box((7.2, y, 9.4), (8.8, y + 1.0, 10.2), 'metal:steel', 'head bracket')
    pole_top(m, 8, 10, 30.0, 0.7)
    rivets(m, 5.6, 10.4, 13.4, 19.2, 7.6, n=4, s=0.18)
    for y in (21.0, 26.0):
        m.box((10.9, y + 0.6, 7.2), (11.2, y + 4.2, 9.2), 'metal:steel', 'lamp door hinge')
        m.box((5.2, y + 4.95, 7.0), (10.8, y + 5.0, 9.4), 'paint:#0c0c0e', 'aspect divider')
    m.box((5.0, 30.6, 7.0), (11.0, 31.0, 9.4), 'paint:#141416', 'head top cap')
    for k in range(5):
        m.box((6.6, 10.0 + k * 0.5, 8.55), (9.4, 10.2 + k * 0.5, 8.6), 'paint:#d8b810', 'tactile box ribs')
    m.box((6.6, 11.0, 9.4), (9.4, 12.4, 9.5), 'plate', 'tactile label', south='label:CONE')
    m.cyl((8.0, 12.8, 9.6), 0.25, 0.4, 'y', 'cable', 'button cable')
    m.save(item_gui=0.4)
    return m


# ========================================================================================================= tram signal
def tram_signal():
    m = Model('tram_signal')
    m.g('pole')
    base_plate(m, 8, 10)
    pole(m, 8, 10, 0.4, 22.0, r=0.7)
    m.box((6.8, 6.0, 10.6), (9.2, 7.4, 10.8), 'plate', 'pole number', south='label:TR 1')
    m.g('head')
    m.box((3.4, 17.0, 7.2), (12.6, 31.4, 9.6), 'paint:#141416', 'tram signal housing')
    m.box((3.0, 31.4, 6.6), (13.0, 31.8, 9.8), 'paint:#141416', 'housing hood')
    m.box((3.6, 17.4, 7.1), (12.4, 26.6, 7.2), 'paint:#0a0a0c', 'aspect field')
    m.box((4.8, 27.2, 7.1), (11.2, 31.0, 7.2), 'paint:#0a0a0c', 'request field')
    for y in (18.0, 30.0):
        m.box((7.2, y, 9.6), (8.8, y + 1.0, 10.4), 'metal:steel', 'head bracket')
    sf = m.surface('north', face=7.05, title='TRAM SIGNAL')
    W = 'f4f4ff'
    # stop bar (horizontal), go straight (vertical), diagonal go-left / go-right (three cells each), prepare-to-stop dot
    m.ctl(sf, 'lamp', 'h', X(8.0), 22.0, w=6.4, h=0.9, col=W, label='STOP')
    m.ctl(sf, 'lamp', 'v', X(8.0), 22.0, w=0.9, h=6.4, col=W, label='GO')
    for i, d in enumerate((-1.8, 0.0, 1.8)):
        m.ctl(sf, 'lamp', 'l%d' % (i + 1), X(8.0 + d), 22.0 - d, w=1.2, h=1.2, col=W, label='GO LEFT')
        m.ctl(sf, 'lamp', 'r%d' % (i + 1), X(8.0 + d), 22.0 + d, w=1.2, h=1.2, col=W, label='GO RIGHT')
    m.ctl(sf, 'lamp', 'dot', X(8.0), 19.0, w=1.3, h=1.3, col=W, label='PREPARE')
    m.ctl(sf, 'lamp', 'tri', X(8.0), 29.0, w=2.4, h=2.0, col=W, label='CALLED')
    m.g('back')
    m.box((5.0, 19.0, 9.6), (11.0, 25.0, 10.4), 'ral7035', 'setting box')
    bk = m.surface('south', face=16 - 10.42, title='TRAM SIGNAL SETTINGS')
    m.ctl(bk, 'selector', 's_dir', 6.6, 22.0, w=1.4, h=1.4, col='202020', label='ROUTE', pos=['STRAIGHT', 'LEFT', 'RIGHT'])
    m.ctl(bk, 'selector', 's_mode', 9.4, 22.0, w=1.4, h=1.4, col='202020', label='MODE', pos=['CONTROLLER', 'SECTION'])
    m.ctl(bk, 'label', '', 6.6, 20.6, w=0, h=0, col='e8e8e8', label='ROUTE')
    m.ctl(bk, 'label', '', 9.4, 20.6, w=0, h=0, col='e8e8e8', label='MODE')
    m.cyl((8.0, 15.0, 10.0), 0.3, 2.0, 'y', 'cable', 'head cable')
    pole_top(m, 8, 10, 22.0, 0.7)
    rivets(m, 3.8, 12.2, 17.4, 31.0, 7.2, n=6, s=0.2)
    for x in (3.4, 12.4):
        for y in (17.4, 31.0):
            m.box((x - 0.3, y - 0.3, 7.0), (x + 0.3, y + 0.3, 7.2), 'paint:#f0f0f0', 'corner reflector')
    for k in range(6):
        m.box((4.0, 18.0 + k * 2.2, 9.6), (12.0, 18.4 + k * 2.2, 9.8), 'paint:#0c0c0e', 'back cooling rib')
    m.box((12.6, 22.0, 7.6), (12.9, 26.0, 9.2), 'metal:steel', 'door hinge')
    m.box((3.1, 23.0, 7.6), (3.4, 25.0, 9.2), 'metal:steel', 'door catch')
    m.box((4.0, 26.7, 7.08), (12.0, 27.1, 7.12), 'paint:#2a2a2c', 'field divider')
    m.box((6.0, 11.0, 10.4), (10.0, 13.2, 10.5), 'plate', 'signal plate', south='label:BOSTRAB')
    m.save(item_gui=0.4)
    return m


# ===================================================================================================== detection loop
def tram_loop():
    m = Model('tram_loop')
    m.g('road slab')
    m.box((0, 0, 0), (16, 0.6, 16), 'paint:#2a2b2d', 'road patch')
    m.g('loop slots')
    for (a, b) in (((2.0, 2.0), (14.0, 2.6)), ((2.0, 13.4), (14.0, 14.0)), ((2.0, 2.0), (2.6, 14.0)), ((13.4, 2.0), (14.0, 14.0))):
        m.box((a[0], 0.6, a[1]), (b[0], 0.66, b[1]), 'paint:#101010', 'sawn loop slot (sealed)')
    m.box((7.7, 0.6, 14.0), (8.3, 0.66, 16.0), 'paint:#101010', 'feeder slot')
    m.box((3.0, 0.6, 7.6), (13.0, 0.64, 8.4), 'paint:#e8e8e8', 'loop marking')
    m.g('pit')
    m.box((10.6, 0.6, 10.6), (14.8, 0.9, 14.8), 'metal:steel', 'detector pit cover')
    for x in (11.0, 14.0):
        for z in (11.0, 14.0):
            m.cyl((x, 0.9, z), 0.25, 0.08, 'y', 'metal:brass', 'cover bolt')
    m.box((11.0, 0.9, 12.2), (14.4, 0.94, 13.2), 'plate', 'pit plate', up='label:LOOP')
    for i in range(8):
        m.box((1.0 + i * 1.8, 0.6, 0.6), (1.6 + i * 1.8, 0.75, 1.2), 'lamp:f0f0f0' if i % 2 else 'paint:#e8e8e8', 'road stud / stop line')
    for (a, b) in (((2.4, 2.4), (13.6, 2.5)), ((2.4, 13.5), (13.6, 13.6))):
        m.box((a[0], 0.66, a[1]), (b[0], 0.68, b[1]), 'paint:#000000', 'loop wire sealant')
    m.box((7.2, 0.6, 14.4), (8.8, 0.9, 15.4), 'paint:#2a2a2c', 'feeder joint box')
    sf = m.surface('up', face=16 - 0.95, title='LOOP DETECTOR')
    m.ctl(sf, 'lamp', 'det', X(11.4), 11.4, w=0.6, h=0.6, col=GREEN, label='DET')
    m.ctl(sf, 'lamp', 'pwr', X(12.6), 11.4, w=0.5, h=0.5, col=AMBER, label='PWR')
    m.ctl(sf, 'selector', 's_mode', X(13.8), 14.2, w=1.0, h=1.0, col='202020', label='LOOP', pos=['TRAM CALL', 'TRAM CANCEL', 'VEH A', 'VEH B', 'VEH C', 'VEH D'])
    m.ctl(sf, 'digits', 'cnt', X(12.6), 14.2, w=1.4, h=0.6, col='ff3030', label='', fmt='%s')
    m.save(item_gui=0.625)
    return m


# =========================================================================================================== tram stop
def tram_stop():
    m = Model('tram_stop')
    m.g('platform kerb')
    m.box((-15.6, 0, 0.0), (31.6, 0.6, 16), 'concrete', 'platform slab')
    m.box((-15.6, 0, -0.2), (31.6, 0.62, 1.2), 'paint:#f0d020', 'platform edge line')
    m.g('shelter frame')
    for x in (-14.6, 1.0, 15.0, 30.6):
        m.box((x - 0.4, 0.6, 13.6), (x + 0.4, 28.0, 14.4), 'paint:#2a2e33', 'shelter post')
        m.box((x - 0.4, 0.6, 7.6), (x + 0.4, 28.0, 8.4), 'paint:#2a2e33', 'front post') if x in (-14.6, 30.6) else None
    m.box((-15.4, 28.0, 6.0), (31.4, 29.0, 15.4), 'paint:#2a2e33', 'roof frame')
    m.box((-15.8, 29.0, 5.4), (31.8, 29.6, 15.8), 'paint:#cfd6dc', 'roof canopy')
    m.box((-15.8, 27.4, 5.4), (31.8, 29.0, 5.8), 'paint:#e04a8a', 'fascia')
    m.box((-6.0, 27.6, 5.3), (22.0, 28.8, 5.4), 'plate', 'fascia lettering', north='label:PRIDE TRAMS')
    m.g('glazing')
    for (x0, x1) in ((-14.2, 0.6), (1.4, 14.6), (15.4, 30.2)):
        m.box((x0, 1.4, 13.85), (x1, 26.8, 14.15), 'glass', 'back glass')
        m.box((x0, 12.0, 13.8), (x1, 12.6, 14.2), 'paint:#e04a8a', 'manifestation band')
    for x0, x1 in ((-15.0, -14.2), (30.2, 31.0)):
        m.box((x0, 1.4, 8.4), (x1, 26.8, 13.6), 'glass', 'end glass')
    m.g('bench')
    for x in (-10.0, 6.0, 22.0):
        m.box((x - 0.4, 0.6, 11.0), (x + 0.4, 6.0, 12.6), 'paint:#2a2e33', 'bench leg')
    m.box((-13.0, 6.0, 10.4), (25.0, 6.8, 13.0), 'wood', 'bench seat')
    m.box((-13.0, 6.8, 12.8), (25.0, 10.4, 13.4), 'wood', 'bench back')
    for x in (-4.0, 12.0):
        m.box((x - 0.3, 6.8, 10.6), (x + 0.3, 8.6, 12.8), 'paint:#2a2e33', 'armrest')
    m.g('stop flag')
    pole(m, -12.6, 2.6, 0.6, 31.6, r=0.6, mat='paint:#2a2e33', name='stop flag pole')
    m.box((-15.6, 25.0, 2.2), (-9.6, 31.0, 2.6), 'paint:#20a040', 'stop flag')
    m.box((-14.8, 27.4, 2.15), (-10.4, 30.4, 2.2), 'paint:#f0d020', 'H disc')
    m.box((-14.4, 27.8, 2.1), (-10.8, 30.0, 2.15), 'plate', 'H', north='label:H')
    m.box((-15.2, 25.4, 2.1), (-10.0, 27.0, 2.15), 'plate', 'tram symbol', north='label:TRAM')
    m.g('timetable case')
    m.box((24.0, 10.0, 13.2), (30.0, 22.0, 13.8), 'paint:#2a2e33', 'timetable case')
    m.box((24.4, 10.4, 13.15), (29.6, 21.6, 13.2), 'paint:#f4f2ea', 'timetable')
    for i in range(10):
        m.box((24.8, 20.4 - i * 1.0, 13.12), (29.2 if i % 3 else 27.0, 20.8 - i * 1.0, 13.15), 'paint:#404040', 'timetable line')
    m.box((24.8, 20.9, 13.1), (29.2, 21.5, 13.12), 'plate', 'timetable head', north='label:TIMETABLE')
    m.g('PIS display')
    m.box((0.2, 21.6, 8.6), (15.8, 27.4, 10.4), 'paint:#1c1e20', 'PIS housing')
    m.box((0.6, 22.0, 8.5), (15.4, 27.0, 8.6), 'paint:#060402', 'PIS screen')
    for x in (3.0, 13.0):
        m.box((x - 0.3, 27.4, 9.2), (x + 0.3, 28.0, 9.8), 'metal:steel', 'display hanger')
    sf = m.surface('north', face=8.45, title='PASSENGER INFORMATION')
    rows = ['name', 'p1', 'p2', 'p3', 'pmsg']
    cols = ['ffffff', 'ffb020', 'ffb020', 'ffb020', 'ff6060']
    h = 4.8 / len(rows)
    for i, cid in enumerate(rows):
        m.ctl(sf, 'digits', cid, X(8.0), 26.8 - h * (i + 0.5), w=14.2, h=h * 0.85, col=cols[i], label='', fmt='%s')
    m.g('help point')
    m.box((-8.0, 8.0, 13.0), (-4.0, 16.0, 13.8), 'paint:#20a040', 'help point')
    m.box((-7.6, 14.4, 12.95), (-4.4, 15.6, 13.0), 'plate', 'help label', north='label:INFO')
    m.cyl((-6.0, 11.0, 12.8), 0.9, 0.2, 'z', 'mesh', 'speaker grille')
    hp = m.surface('north', face=12.92, title='HELP POINT')
    m.ctl(hp, 'push', 'info', X(-6.0), 13.2, w=1.2, h=1.2, col='f0f0f0', label='INFO')
    m.ctl(hp, 'lamp', 'appr', X(-6.0), 9.2, w=0.6, h=0.6, col=AMBER, label='TRAM DUE', blink=True)
    m.ctl(hp, 'key', 'k_req', X(-4.8), 9.2, w=0.8, h=0.8, col='c0a040', label='STOP', pos=['ALL STOP', 'REQUEST'])
    m.ctl(hp, 'push', 'test', X(-7.2), 9.2, w=0.6, h=0.6, col='f0f0f0', label='TEST')
    lamps(m, hp, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], X(-7.4), 8.0 - 0.0, step=-1.4, size=0.4, lab_dv=0.45)
    m.g('lighting')
    for x in (-8.0, 8.0, 24.0):
        m.box((x - 3.0, 27.6, 10.0), (x + 3.0, 28.0, 12.0), 'lamp:fff4d8', 'canopy light')
    m.g('street furniture')
    m.cyl((26.0, 0.6, 3.0), 1.2, 5.0, 'y', 'paint:#2a2e33', 'litter bin')
    m.cyl((26.0, 5.6, 3.0), 1.3, 0.4, 'y', 'paint:#e04a8a', 'bin lid')
    m.box((25.4, 3.6, 1.7), (26.6, 4.6, 1.8), 'plate', 'bin label', north='label:LITTER')
    for x in (-15.2, 31.2):
        m.cyl((x, 0.6, 14.8), 0.3, 28.4, 'y', 'paint:#2a2e33', 'downpipe')
    m.box((-15.8, 28.6, 15.4), (31.8, 29.0, 15.9), 'paint:#2a2e33', 'roof gutter')
    m.box((16.0, 13.0, 13.7), (22.6, 26.0, 13.85), 'paint:#2a2e33', 'advert frame')
    m.box((16.3, 13.3, 13.65), (22.3, 25.7, 13.7), 'paint:#f4c0d8', 'poster')
    m.box((16.6, 22.0, 13.62), (22.0, 25.0, 13.65), 'plate', 'poster text', north='label:RIDE|PRIDE|TRAMS')
    for x in (-13.0, -5.0, 3.0, 11.0, 19.0, 27.0):
        m.box((x, 0.6, 1.4), (x + 2.0, 0.66, 2.6), 'paint:#e8c818', 'tactile strip')
    for x in (-15.0, 31.0):
        m.cyl((x, 0.6, 3.0), 0.5, 4.0, 'y', 'paint:#2a2e33', 'bollard')
        m.cyl((x, 3.4, 3.0), 0.55, 0.4, 'y', 'paint:#f0f0f0', 'bollard band')
    m.terminal_12v(30.2, 1.0, 11.0, 'east')
    mains_box(m, -14.4, 1.0, 11.0, 'west')
    m.save(item_gui=0.22)
    return m


# =================================================================================================== ticket validator
def ticket_validator():
    m = Model('ticket_validator')
    m.g('post')
    base_plate(m, 8, 9, r=2.0)
    m.box((6.6, 0.4, 7.6), (9.4, 16.0, 10.4), 'paint:#e04a8a', 'validator post')
    m.box((6.4, 16.0, 7.4), (9.6, 16.6, 10.6), 'paint:#2a2e33', 'post cap')
    m.g('validator')
    m.box((5.4, 16.6, 6.6), (10.6, 25.4, 10.4), 'paint:#2a2e33', 'validator body')
    m.box((5.6, 23.2, 6.4), (10.4, 25.2, 6.6), 'paint:#03140a', 'validator screen')
    m.box((5.8, 17.2, 6.4), (10.2, 22.4, 6.6), 'paint:#1a1c1e', 'card target')
    m.box((6.4, 18.0, 6.35), (9.6, 21.6, 6.4), 'paint:#2a6ad0', 'contactless target')
    m.box((6.8, 21.6, 6.3), (9.2, 22.2, 6.35), 'plate', 'tap label', north='label:TAP / INSERT')
    m.box((6.6, 16.8, 6.4), (9.4, 17.1, 6.6), 'paint:#050505', 'ticket slot')
    m.box((5.4, 25.4, 6.6), (10.6, 26.0, 10.4), 'paint:#e04a8a', 'top trim')
    sf = m.surface('north', face=6.28, title='VALIDATOR')
    m.ctl(sf, 'zone', 'reader', X(8.0), 19.8, w=4.0, h=5.0, col='000000', label='')
    m.ctl(sf, 'lamp', 'ok', X(6.6), 22.8, w=0.6, h=0.6, col=GREEN, label='OK')
    m.ctl(sf, 'lamp', 'no', X(9.4), 22.8, w=0.6, h=0.6, col=RED, label='NO')
    m.ctl(sf, 'digits', 'msg', X(8.0), 24.2, w=4.4, h=1.4, col='7cf29a', label='', fmt='%s')
    m.terminal_12v(8.0, 2.0, 10.4, 'south')
    rivets(m, 5.6, 10.4, 16.8, 25.2, 6.6, n=4, s=0.18)
    for k in range(4):
        m.box((6.8, 1.0 + k * 3.6, 7.5), (9.2, 1.4 + k * 3.6, 7.6), 'paint:#c03070', 'post band')
    m.box((5.4, 16.6, 10.4), (10.6, 25.4, 10.5), 'paint:#22262a', 'back cover')
    m.box((6.0, 19.0, 10.5), (10.0, 21.0, 10.6), 'plate', 'service plate', south='label:VALIDATOR')
    m.cyl((8.0, 25.4, 8.5), 0.2, 0.8, 'y', 'paint:#202020', 'beeper')
    m.box((6.2, 26.0, 7.0), (9.8, 26.4, 10.0), 'paint:#2a2e33', 'top cap')
    m.box((6.6, 9.0, 7.55), (9.4, 13.0, 7.6), 'plate', 'fare sign', north='label:VALIDATE|BEFORE|BOARDING')
    m.save(item_gui=0.45)
    return m


# ========================================================================================== lit platform edge marker
def tram_edge():
    m = Model('tram_edge')
    m.box((0, 0, 0), (16, 1.0, 16), 'concrete', 'platform paving')
    m.box((0, 1.0, 0), (16, 1.1, 3.0), 'paint:#f0d020', 'edge line')
    for i in range(8):
        for j in range(3):
            m.cyl((1.0 + i * 2.0, 1.0, 4.4 + j * 1.6), 0.35, 0.2, 'y', 'paint:#e8c818', 'tactile blister')
    for i in range(6):
        m.box((1.0 + i * 2.6, 1.0, 1.0), (2.6 + i * 2.6, 1.12, 2.0), 'paint:#202020', 'LED housing')
    sf = m.surface('up', face=16 - 1.13, title='EDGE LIGHTS')
    for i in range(6):
        m.ctl(sf, 'lamp', 'led%d' % (i + 1), X(1.8 + i * 2.6), 1.5, w=1.2, h=0.7, col='ffd040', label='EDGE', blink=True)
    m.save(item_gui=0.625)
    return m


# ==================================================================================================== tramway OHLE pole
def tram_pole():
    m = Model('tram_pole')
    m.g('pole')
    base_plate(m, 8, 8, r=2.6)
    m.cyl((8, 0.4, 8), 1.4, 3.0, 'y', 'paint:#2a3a2e', 'pole collar')
    pole(m, 8, 8, 3.4, 31.6, r=1.0, mat='paint:#3a4a3e', name='tramway pole')
    m.cyl((8, 31.6, 8), 1.2, 0.4, 'y', 'paint:#2a3a2e', 'pole finial')
    m.g('bracket arm')
    m.box((-15.6, 28.6, 7.6), (8, 29.4, 8.4), 'paint:#3a4a3e', 'bracket arm')
    m.box((-15.6, 30.6, 7.8), (8, 31.0, 8.2), 'paint:#3a4a3e', 'arm tie')
    for x in (-12.0, -6.0, 0.0, 4.0):
        m.box((x - 0.15, 29.4, 7.85), (x + 0.15, 30.6, 8.15), 'paint:#3a4a3e', 'lattice strut')
    m.cyl((-14.0, 26.8, 8.0), 0.45, 1.8, 'y', 'porcelain', 'insulator')
    m.box((-14.6, 26.2, 7.2), (-13.4, 26.8, 8.8), 'metal:copper', 'trolley ear')
    m.box((-15.8, 26.0, 0), (-15.6, 26.3, 16), 'metal:copper', 'contact wire stub')
    m.g('section indicator')
    m.box((9.0, 12.0, 6.0), (13.0, 20.0, 10.0), 'paint:#2a2e33', 'indicator box')
    m.box((9.2, 18.4, 5.95), (12.8, 19.6, 6.0), 'plate', 'indicator label', north='label:LINE')
    sf = m.surface('north', face=5.9, title='TRAMWAY SECTION')
    m.ctl(sf, 'lamp', 'live', X(11.0), 16.6, w=1.4, h=1.4, col=GREEN, label='LIVE')
    m.ctl(sf, 'lamp', 'dead', X(11.0), 14.4, w=1.4, h=1.4, col=RED, label='DEAD', blink=True)
    m.ctl(sf, 'digits', 'sec', X(11.0), 12.8, w=3.6, h=0.9, col='ffd040', label='', fmt='%s')
    m.box((9.4, 4.0, 6.8), (12.6, 6.0, 7.0), 'danger', 'danger plate', north='danger:DANGER|600V')
    m.terminal_12v(13.0, 8.0, 8.0, 'east')
    rivets(m, 9.3, 12.7, 12.3, 19.7, 6.0, n=3, s=0.18)
    for k in range(6):
        m.cyl((8, 6.0 + k * 4.0, 8), 1.06, 0.2, 'y', 'paint:#2a3a2e', 'pole joint ring')
    m.box((-15.0, 28.0, 7.7), (-13.0, 28.6, 8.3), 'paint:#3a4a3e', 'arm end cap')
    m.cyl((-14.0, 26.0, 8.0), 0.25, 0.8, 'y', 'metal:steel', 'hanger bolt')
    m.box((-8.0, 28.4, 7.6), (-7.0, 28.6, 8.4), 'porcelain', 'arm insulator')
    m.box((8.6, 19.0, 7.4), (9.0, 26.0, 8.6), 'cable:1d1d21', 'indicator sense lead')
    m.box((6.8, 2.0, 6.95), (9.2, 3.0, 7.05), 'plate', 'pole number', north='label:T 12')
    m.save(item_gui=0.3)
    return m


# ================================================================================================== tram road surface
def tram_road():
    m = Model('tram_road')
    m.box((0, 0, 0), (16, 15.9, 16), 'paint:#3a3b3e', 'asphalt')
    for x in (3.2, 11.6):
        m.box((x, 15.9, 0), (x + 1.4, 16.0, 16), 'metal:steel', 'grooved rail head')
        m.box((x + 1.4, 15.92, 0), (x + 1.8, 16.0, 16), 'paint:#141414', 'rail groove')
    for z in (0.0, 15.6):
        m.box((0, 15.9, z), (16, 15.95, z + 0.4), 'paint:#2c2d30', 'asphalt joint')
    m.save(item_gui=0.625)
    return m


# =================================================================================== vehicles (scaled-down models)
# lamp overlay boxes per kind in model px: head (2), tail (2), indicators front-left, rear-left, front-right, rear-right
LAMPS = {}


def tyre(m, x, z, r, w):
    m.cyl((x, r, z), r, w, 'x', 'paint:#161618', 'tyre')
    xs = x - 0.05 if x < 8 else x + w - 0.05
    m.cyl((xs, r, z), r * 0.6, 0.1, 'x', 'metal:steel', 'wheel rim')
    m.cyl((x - 0.1 if x < 8 else x + w, r, z), r * 0.22, 0.1, 'x', 'paint:#202020', 'hub nut')
    for k in range(4):
        m.box((xs, r - 0.1 + (k - 1.5) * r * 0.25, z - r * 0.08), (xs + 0.12, r + 0.1 + (k - 1.5) * r * 0.25, z + r * 0.08), 'paint:#303034', 'rim spoke')


def lens_box(m, b, col, name):
    m.box(b[:3], b[3:], 'lamp:' + col, name)


def lamps_for(m, kind, head, tail, ind):
    """lenses in the fixed model + remember where RoadClient draws the live overlay"""
    LAMPS[kind] = {'head': head, 'tail': tail, 'ind': ind}
    for b in head: lens_box(m, b, 'fff4d0', 'headlight')
    for b in tail: lens_box(m, b, '8a1010', 'tail / brake lamp')
    for b in ind: lens_box(m, b, 'a05010', 'indicator')


def plates(m, z0, z1, y, text):
    m.box((5.6, y, z1), (10.4, y + 1.2, z1 + 0.12), 'plate', 'rear number plate', south='label:' + text)
    m.box((5.6, y, z0 - 0.12), (10.4, y + 1.2, z0), 'plate', 'front number plate', north='label:' + text)


def car():
    m, p = Model('road_car'), Model('road_car_paint')
    p.g('body')
    p.box((1.0, 2.2, -9.4), (15.0, 6.6, 25.4), 'paint:#e6e6e6', 'lower body')
    p.box((1.2, 6.6, -9.0), (14.8, 7.0, 1.6), 'paint:#ececec', 'bonnet')
    p.box((1.2, 6.6, 19.4), (14.8, 7.2, 25.2), 'paint:#ececec', 'boot lid')
    p.box((2.0, 11.2, 3.6), (14.0, 11.6, 17.4), 'paint:#ececec', 'roof')
    for x in (1.6, 14.0):
        p.box((x, 7.0, 1.6), (x + 0.4, 11.2, 2.4), 'paint:#e0e0e0', 'A pillar')
        p.box((x, 7.0, 9.8), (x + 0.4, 11.2, 10.4), 'paint:#e0e0e0', 'B pillar')
        p.box((x, 7.0, 17.4), (x + 0.4, 11.2, 19.4), 'paint:#e0e0e0', 'C pillar')
    for x in (0.92, 14.98):
        for (z0, z1) in ((-6.6, -1.2), (18.6, 24.0)):
            p.box((x, 3.6, z0), (x + 0.1, 6.2, z1), 'paint:#dcdcdc', 'wing panel')
    m.g('chassis')
    for (x, z) in ((0.4, -4.6), (13.8, -4.6), (0.4, 20.4), (13.8, 20.4)):
        tyre(m, x, z, 2.6, 1.8)
    m.box((1.4, 1.2, -8.8), (14.6, 2.2, 24.8), 'paint:#18181a', 'floor pan')
    m.g('trim')
    m.box((0.8, 1.6, -10.0), (15.2, 3.4, -9.2), 'paint:#202022', 'front bumper')
    m.box((0.8, 1.6, 25.2), (15.2, 3.4, 26.0), 'paint:#202022', 'rear bumper')
    m.box((3.4, 3.6, -9.5), (12.6, 5.6, -9.4), 'mesh', 'grille')
    m.box((6.8, 4.0, -9.6), (9.2, 5.2, -9.5), 'metal:steel', 'badge')
    for x in (0.9, 15.0):
        m.box((x, 2.2, -9.0), (x + 0.1, 2.8, 25.0), 'paint:#202022', 'side skirt')
        m.box((x - 0.05, 5.6, 4.6), (x + 0.15, 5.9, 5.8), 'metal:steel', 'door handle')
        m.box((x - 0.05, 5.6, 12.2), (x + 0.15, 5.9, 13.4), 'metal:steel', 'door handle')
        m.box((x - 0.04, 2.8, 10.0), (x + 0.14, 6.6, 10.2), 'paint:#303034', 'door shut line')
        for z in (-4.6, 20.4):
            m.box((x - 0.12, 2.2, z - 3.0), (x + 0.22, 5.4, z + 3.0), 'paint:#18181a', 'wheel arch liner')
    m.g('glass')
    m.box((2.2, 7.2, 1.7), (13.8, 11.0, 2.3), 'glass', 'windscreen', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 7.2, 2.0]})
    m.box((2.2, 7.2, 17.6), (13.8, 11.0, 18.4), 'glass', 'rear window', rot={'axis': 'x', 'angle': 22.5, 'origin': [8, 7.2, 18.0]})
    for x in (1.7, 14.2):
        m.box((x, 7.2, 2.4), (x + 0.1, 11.0, 9.8), 'glass', 'front side window')
        m.box((x, 7.2, 10.4), (x + 0.1, 11.0, 17.4), 'glass', 'rear side window')
    m.box((2.0, 6.6, 1.4), (14.0, 7.2, 3.6), 'paint:#18181a', 'scuttle + dash top')
    m.box((2.2, 6.8, 17.4), (13.8, 7.0, 19.4), 'paint:#18181a', 'parcel shelf')
    for x in (0.0, 14.8):
        m.box((x, 8.4, 2.4), (x + 1.2, 9.4, 3.4), 'paint:#202022', 'door mirror')
    m.g('interior')
    m.box((2.6, 6.8, 3.6), (13.4, 7.6, 5.0), 'paint:#18181a', 'dashboard')
    m.cyl((10.6, 7.8, 5.0), 1.1, 0.25, 'z', 'paint:#101010', 'steering wheel')
    for zz in (7.0, 13.4):
        for (a, b) in ((2.6, 7.4), (8.6, 13.4)):
            m.box((a, 3.2, zz), (b, 4.2, zz + 2.6), 'paint:#34343a', 'seat cushion')
            m.box((a, 4.2, zz + 2.2), (b, 8.0, zz + 2.8), 'paint:#34343a', 'seat back')
            m.box((a + 1.2, 8.0, zz + 2.3), (b - 1.2, 8.8, zz + 2.7), 'paint:#34343a', 'head rest')
    m.cyl((12.0, 1.4, 25.6), 0.35, 0.8, 'z', 'metal:steel', 'exhaust tip')
    m.box((12.6, 11.6, 15.0), (13.0, 12.2, 16.6), 'paint:#18181a', 'shark fin aerial')
    plates(m, -10.0, 26.0, 2.0, 'PR1D3')
    lamps_for(m, 'car', head=[(1.6, 5.0, -9.62, 4.4, 6.2, -9.5), (11.6, 5.0, -9.62, 14.4, 6.2, -9.5)],
              tail=[(1.2, 5.0, 25.4, 4.0, 6.4, 25.52), (12.0, 5.0, 25.4, 14.8, 6.4, 25.52)],
              ind=[(1.2, 3.8, -9.62, 2.8, 4.6, -9.5), (1.2, 4.0, 25.4, 2.6, 4.8, 25.52),
                   (13.2, 3.8, -9.62, 14.8, 4.6, -9.5), (13.4, 4.0, 25.4, 14.8, 4.8, 25.52)])
    m.save(item_gui=0.3); p.save(item_gui=0.3)
    return m, p


def van():
    m, p = Model('road_van'), Model('road_van_paint')
    p.g('body')
    p.box((0.4, 2.2, -13.0), (15.6, 6.6, -4.0), 'paint:#e6e6e6', 'cab lower')
    p.box((0.8, 6.6, -12.8), (15.2, 7.0, -8.8), 'paint:#ececec', 'short bonnet')
    p.box((0.4, 2.2, -4.0), (15.6, 19.6, 29.2), 'paint:#e8e8e8', 'load box')
    p.box((0.6, 19.6, -7.0), (15.4, 20.0, 29.0), 'paint:#ececec', 'roof')
    for x in (0.6, 14.8):
        p.box((x, 7.0, -8.6), (x + 0.6, 19.6, -7.6), 'paint:#e0e0e0', 'A pillar')
    m.g('chassis')
    for (x, z) in ((0.0, -8.6), (14.0, -8.6), (0.0, 22.6), (14.0, 22.6)):
        tyre(m, x, z, 2.8, 2.0)
    m.box((1.0, 1.2, -12.6), (15.0, 2.2, 28.8), 'paint:#18181a', 'chassis')
    m.g('trim')
    m.box((0.2, 1.6, -13.8), (15.8, 3.6, -13.0), 'paint:#202022', 'front bumper')
    m.box((0.2, 1.6, 29.2), (15.8, 3.2, 30.0), 'paint:#202022', 'rear step bumper')
    m.box((3.0, 3.8, -13.1), (13.0, 6.2, -13.0), 'mesh', 'grille')
    m.g('glass')
    m.box((1.4, 7.4, -8.4), (14.6, 15.6, -8.0), 'glass', 'windscreen', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 7.4, -8.2]})
    for x in (0.3, 15.6):
        m.box((x, 8.0, -7.2), (x + 0.1, 15.0, -2.0), 'glass', 'cab door window')
        m.box((x - 0.04, 2.6, -1.6), (x + 0.14, 19.0, -1.4), 'paint:#303034', 'door shut line')
        m.box((x - 0.04, 2.6, 9.0), (x + 0.14, 19.0, 9.2), 'paint:#303034', 'sliding door line')
        m.box((x - 0.05, 9.0, 4.0), (x + 0.15, 9.4, 6.0), 'metal:steel', 'slider handle')
        m.box((x - 0.05, 3.0, 2.0), (x + 0.15, 3.6, 8.0), 'metal:steel', 'slider rail')
        for z in (-8.6, 22.6):
            m.box((x - 0.12, 2.2, z - 3.4), (x + 0.22, 6.0, z + 3.4), 'paint:#18181a', 'wheel arch')
    for x in (-0.6, 15.4):
        m.box((x, 10.4, -7.4), (x + 1.2, 13.0, -6.4), 'paint:#202022', 'door mirror')
    m.box((7.9, 2.4, 29.2), (8.1, 19.2, 29.3), 'paint:#303034', 'rear door split')
    for x in (7.0, 8.4):
        m.box((x, 9.0, 29.25), (x + 0.6, 9.4, 29.45), 'metal:steel', 'rear door handle')
    m.box((2.0, 14.0, 29.22), (14.0, 18.6, 29.3), 'glass', 'rear windows')
    m.g('livery')
    for x, f in ((0.38, 'west'), (15.62, 'east')):
        m.box((x - 0.02 if f == 'west' else x - 0.04, 10.0, 10.0), (x + 0.04 if f == 'west' else x + 0.02, 15.0, 27.0), 'paint:#e04a8a', 'livery panel',
              **{f: 'label:PRIDE PARCELS'})
    m.g('interior')
    m.box((1.6, 6.6, -6.8), (14.4, 7.6, -5.4), 'paint:#18181a', 'dashboard')
    m.cyl((11.0, 8.6, -5.4), 1.2, 0.25, 'z', 'paint:#101010', 'steering wheel')
    m.box((1.0, 7.0, -1.2), (15.0, 18.0, -0.8), 'paint:#3a3a3e', 'bulkhead')
    plates(m, -13.8, 30.0, 2.0, 'PR VAN')
    lamps_for(m, 'van', head=[(0.8, 6.4, -13.12, 3.8, 8.0, -13.0), (12.2, 6.4, -13.12, 15.2, 8.0, -13.0)],
              tail=[(0.2, 8.0, 29.2, 1.6, 13.0, 29.34), (14.4, 8.0, 29.2, 15.8, 13.0, 29.34)],
              ind=[(0.8, 5.0, -13.12, 2.6, 6.0, -13.0), (0.2, 13.2, 29.2, 1.6, 14.4, 29.34),
                   (13.4, 5.0, -13.12, 15.2, 6.0, -13.0), (14.4, 13.2, 29.2, 15.8, 14.4, 29.34)])
    m.save(item_gui=0.25); p.save(item_gui=0.25)
    return m, p


def truck():
    """box truck at a THIRD scale (1 px = 0.1875 m): cab over, 6.5 m box"""
    m, p = Model('road_truck'), Model('road_truck_paint')
    p.g('cab')
    p.box((1.4, 3.0, -13.0), (14.6, 15.2, -6.0), 'paint:#e6e6e6', 'cab shell')
    p.box((1.6, 15.2, -12.6), (14.4, 16.0, -6.4), 'paint:#ececec', 'cab roof')
    p.box((2.4, 16.0, -11.0), (13.6, 17.6, -6.6), 'paint:#e8e8e8', 'roof deflector')
    p.g('box body')
    p.box((1.0, 4.4, -5.4), (15.0, 19.0, 29.0), 'paint:#e9e9e9', 'box body')
    m.g('chassis')
    for (x, z) in ((1.0, -8.6), (13.6, -8.6), (1.0, 21.0), (13.6, 21.0), (1.0, 25.6), (13.6, 25.6)):
        tyre(m, x, z, 1.9, 1.4)
    m.box((3.0, 1.6, -12.6), (13.0, 3.0, 28.6), 'paint:#18181a', 'chassis rails')
    for x in (2.2, 12.2):
        m.box((x, 1.4, 0.0), (x + 1.6, 3.4, 6.0), 'metal:steel', 'fuel tank') if x < 8 else m.box((x, 1.6, 0.0), (x + 1.6, 3.6, 3.0), 'paint:#202022', 'battery box')
    m.box((1.4, 1.6, 6.0), (14.6, 2.0, 18.0), 'paint:#f0d020', 'side underrun guard')
    m.box((1.4, 1.4, 28.6), (14.6, 2.6, 29.4), 'haz', 'rear underrun bar')
    m.g('cab detail')
    m.box((1.2, 2.4, -13.6), (14.8, 4.4, -12.8), 'paint:#202022', 'bumper')
    m.box((3.0, 5.0, -13.06), (13.0, 9.0, -13.0), 'louvre', 'grille')
    m.box((2.2, 9.6, -13.2), (13.8, 14.6, -12.6), 'glass', 'windscreen')
    m.box((7.8, 9.6, -13.25), (8.2, 14.6, -13.15), 'paint:#18181a', 'screen pillar')
    for x in (1.3, 14.6):
        m.box((x, 9.0, -12.0), (x + 0.1, 14.2, -8.0), 'glass', 'door window')
        m.box((x - 0.04, 3.2, -7.0), (x + 0.14, 14.6, -6.8), 'paint:#303034', 'door shut line')
        m.box((x - 0.05, 8.0, -9.0), (x + 0.15, 8.4, -7.6), 'metal:steel', 'door handle')
        for k in range(3):
            m.box((x - 0.05, 3.2 + k * 1.2, -12.4), (x + 0.15, 3.4 + k * 1.2, -11.0), 'metal:steel', 'cab step')
    for x in (-0.4, 14.8):
        m.box((x, 11.0, -12.4), (x + 1.6, 14.0, -11.6), 'paint:#202022', 'mirror arm + glass')
    m.cyl((14.0, 15.2, -6.6), 0.35, 4.0, 'y', 'metal:steel', 'exhaust stack')
    m.g('box detail')
    for z in range(-4, 29, 3):
        for x in (0.96, 15.0):
            m.box((x, 4.6, z), (x + 0.06, 18.8, z + 0.3), 'paint:#c8c8c8', 'box rib')
    m.box((1.0, 4.4, 28.98), (15.0, 19.0, 29.1), 'paint:#b8b8b8', 'roller door')
    for y in range(5, 19):
        m.box((1.2, y + 0.0, 29.1), (14.8, y + 0.1, 29.14), 'paint:#9a9a9a', 'roller slat')
    m.box((6.8, 5.4, 29.14), (9.2, 6.0, 29.3), 'metal:steel', 'door grab')
    for x, f in ((0.94, 'west'), (15.06, 'east')):
        m.box((x - 0.02, 10.0, 0.0), (x + 0.02, 16.0, 26.0), 'paint:#2a6ad0', 'livery panel', **{f: 'label:PRIDE FREIGHT'})
    plates(m, -13.6, 29.4, 2.6, 'PR TRK')
    lamps_for(m, 'truck', head=[(2.0, 4.6, -13.66, 4.6, 6.0, -13.56), (11.4, 4.6, -13.66, 14.0, 6.0, -13.56)],
              tail=[(1.6, 3.0, 29.4, 3.6, 4.2, 29.5), (12.4, 3.0, 29.4, 14.4, 4.2, 29.5)],
              ind=[(1.2, 4.6, -13.66, 2.0, 6.0, -13.56), (3.6, 3.0, 29.4, 4.6, 4.2, 29.5),
                   (14.0, 4.6, -13.66, 14.8, 6.0, -13.56), (11.4, 3.0, 29.4, 12.4, 4.2, 29.5)])
    m.save(item_gui=0.25); p.save(item_gui=0.25)
    return m, p


def bus():
    """single-deck city bus at a QUARTER scale (1 px = 0.25 m), 11.5 m long"""
    m, p = Model('road_bus'), Model('road_bus_paint')
    p.g('body')
    p.box((2.9, 1.0, -15.0), (13.1, 4.4, 31.0), 'paint:#e6e6e6', 'lower body')
    p.box((3.0, 11.0, -14.8), (13.0, 12.4, 30.8), 'paint:#ececec', 'roof band')
    p.box((3.1, 12.4, -14.0), (12.9, 12.8, 30.0), 'paint:#e8e8e8', 'roof')
    for z in (-6.0, 1.0, 8.0, 15.0, 22.0, 29.0):
        for x in (2.9, 12.9):
            p.box((x, 4.4, z), (x + 0.2, 11.0, z + 0.6), 'paint:#e0e0e0', 'window pillar')
    m.g('chassis')
    for (x, z) in ((2.6, -9.0), (12.4, -9.0), (2.6, 20.0), (12.4, 20.0)):
        tyre(m, x, z, 1.25, 1.0)
    m.box((3.2, 0.4, -14.6), (12.8, 1.0, 30.6), 'paint:#18181a', 'underframe')
    m.g('glass')
    m.box((3.2, 4.6, -15.1), (12.8, 10.6, -14.9), 'glass', 'windscreen')
    m.box((3.2, 10.6, -15.1), (12.8, 11.8, -14.9), 'paint:#101010', 'destination box')
    for x in (2.85, 13.05):
        m.box((x, 4.6, -5.4), (x + 0.1, 10.8, 30.0), 'glass', 'saloon windows')
    m.box((3.4, 5.0, 30.95), (12.6, 10.6, 31.05), 'glass', 'rear window')
    m.g('doors')
    for (z0, z1) in ((-13.6, -9.8), (5.6, 9.4)):
        m.box((12.95, 1.2, z0), (13.15, 10.8, z1), 'glass', 'door leaf glass')
        m.box((13.0, 1.0, (z0 + z1) / 2 - 0.08), (13.2, 10.8, (z0 + z1) / 2 + 0.08), 'paint:#18181a', 'door split')
        m.box((13.0, 1.0, z0 - 0.2), (13.2, 11.0, z0), 'paint:#18181a', 'door frame')
        m.box((13.0, 1.0, z1), (13.2, 11.0, z1 + 0.2), 'paint:#18181a', 'door frame')
    m.g('trim')
    m.box((2.8, 0.8, -15.6), (13.2, 2.0, -15.0), 'paint:#202022', 'front bumper')
    m.box((2.8, 0.8, 31.0), (13.2, 2.0, 31.6), 'paint:#202022', 'rear bumper')
    m.box((4.0, 2.4, 31.0), (12.0, 4.0, 31.08), 'louvre', 'engine grille')
    for x in (2.2, 13.0):
        m.box((x, 7.0, -15.4), (x + 0.8, 9.8, -14.8), 'paint:#202022', 'mirror arm')
        m.box((x, 6.4, -16.0), (x + 0.8, 8.0, -15.4), 'paint:#202022', 'mirror head')
    m.box((3.6, 12.8, 2.0), (12.4, 13.6, 12.0), 'paint:#d0d0d0', 'roof AC pod')
    m.box((3.6, 13.6, 4.0), (12.4, 13.7, 10.0), 'mesh', 'AC grille')
    m.g('livery + signs')
    m.box((3.3, 10.65, -15.16), (12.7, 11.75, -15.1), 'led:ffb020', 'destination LED')
    m.box((3.3, 10.65, -15.2), (12.7, 11.75, -15.16), 'plate', 'destination', north='label:12 CENTRAL')
    for x, f in ((2.86, 'west'), (13.14, 'east')):
        m.box((x - 0.02 if f == 'west' else x - 0.04, 2.2, 10.0), (x + 0.04 if f == 'west' else x + 0.02, 4.0, 28.0), 'paint:#e04a8a', 'livery stripe', **{f: 'label:PRIDE BUSES'})
    m.box((12.0, 8.0, -15.12), (12.6, 9.0, -15.06), 'plate', 'fleet no', north='label:12')
    m.g('saloon')
    for z in range(-4, 30, 3):
        for (a, b) in ((3.4, 5.6), (10.4, 12.6)):
            if 5.0 <= z <= 10.0 and a > 8: continue
            m.box((a, 2.4, z), (b, 3.0, z + 1.6), 'paint:#2a3a8a', 'seat cushion')
            m.box((a, 3.0, z + 1.4), (b, 5.4, z + 1.8), 'paint:#2a3a8a', 'seat back')
            m.box((b - 0.2 if a < 8 else a, 5.4, z + 1.5), (b if a < 8 else a + 0.2, 6.4, z + 1.7), 'paint:#f0d020', 'grab handle')
    for z in (-12.0, -4.0, 4.0, 12.0, 20.0, 28.0):
        m.cyl((8.0, 1.0, z), 0.12, 10.0, 'y', 'paint:#f0d020', 'stanchion')
    m.box((7.9, 10.6, -12.0), (8.1, 10.8, 28.0), 'paint:#f0d020', 'ceiling handrail')
    m.box((3.4, 2.0, -13.6), (6.4, 6.0, -11.6), 'paint:#202022', 'driver cab')
    m.cyl((5.0, 5.6, -13.0), 0.9, 0.2, 'z', 'paint:#101010', 'steering wheel')
    m.box((10.4, 2.0, -12.6), (12.0, 5.0, -11.8), 'paint:#2a2e33', 'ticket machine')
    plates(m, -15.6, 31.6, 1.0, 'PR BUS')
    lamps_for(m, 'bus', head=[(3.2, 2.2, -15.12, 5.0, 3.4, -15.0), (11.0, 2.2, -15.12, 12.8, 3.4, -15.0)],
              tail=[(3.0, 2.4, 31.0, 4.0, 4.6, 31.12), (12.0, 2.4, 31.0, 13.0, 4.6, 31.12)],
              ind=[(3.2, 3.6, -15.12, 4.4, 4.2, -15.0), (3.0, 5.0, 31.0, 4.0, 5.8, 31.12),
                   (11.6, 3.6, -15.12, 12.8, 4.2, -15.0), (12.0, 5.0, 31.0, 13.0, 5.8, 31.12)])
    m.save(item_gui=0.2); p.save(item_gui=0.2)
    return m, p


RECIPES = {
    'traffic_controller': (['IRI', 'GCG', 'ISI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone_block', 'G': 'minecraft:glowstone_dust', 'C': 'minecraft:comparator', 'S': 'minecraft:stone@0'}),
    'traffic_signal': (['BRB', 'BYB', 'BGB'], {'B': 'minecraft:iron_ingot', 'R': 'minecraft:dye@1', 'Y': 'minecraft:dye@11', 'G': 'minecraft:dye@10'}),
    'ped_unit': (['IRI', 'IBI', 'IGI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:dye@1', 'B': 'minecraft:stone_button', 'G': 'minecraft:dye@10'}),
    'tram_signal': (['III', 'WGW', 'III'], {'I': 'minecraft:iron_ingot', 'W': 'minecraft:dye@15', 'G': 'minecraft:glowstone_dust'}),
    'tram_loop': (['RRR', 'RCR', 'SSS'], {'R': 'minecraft:redstone', 'C': 'minecraft:comparator', 'S': 'minecraft:stone_slab@0'}),
    'tram_stop': (['PPP', 'GSG', 'WCW'], {'P': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'S': 'minecraft:sign', 'W': 'minecraft:planks@0', 'C': 'minecraft:redstone_lamp'}),
    'ticket_validator': ([' P ', 'IRI', ' I '], {'P': 'minecraft:paper', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'tram_edge': (['YYY', 'GSG'], {'Y': 'minecraft:dye@11', 'G': 'minecraft:glowstone_dust', 'S': 'minecraft:stone_slab@0'}),
    'tram_pole': ([' C ', 'III', ' I '], {'C': 'minecraft:iron_bars', 'I': 'minecraft:iron_ingot'}),
    'tram_road': (['CIC', 'CCC'], {'C': 'minecraft:coal@0', 'I': 'minecraft:iron_ingot'}),
}
RESULT_N = {'tram_road': 6, 'tram_edge': 3}


def java_lamps():
    """the overlay arrays for road/client/RoadClient.java (kinds in EntityRoadVehicle.Kind order)"""
    out = []
    for k in ('car', 'van', 'truck', 'bus'):
        L = LAMPS[k]
        f = lambda bs: '{' + ', '.join('{' + ', '.join('%sf' % round(v, 3) for v in b) + '}' for b in bs) + '}'
        out.append('            {%s, %s, %s}' % (f(L['head']), f(L['tail']), f(L['ind'])))
    return ',\n'.join(out)


if __name__ == '__main__':
    for fn in (traffic_controller, traffic_signal, ped_unit, tram_signal, tram_loop, tram_stop, ticket_validator, tram_edge, tram_pole, tram_road):
        mm = fn()
        print('%-20s %d boxes' % (mm.name, len(mm.boxes)))
    for fn in (car, van, truck, bus):
        a, b = fn()
        print('%-20s %d + %d boxes' % (a.name, len(a.boxes), len(b.boxes)))
    for n in RECIPES:
        if n == 'tram_road':
            bs = {'variants': {'normal': {'model': 'irextras:' + n}}}
        else:
            bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                               'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()},
                   'result': {'item': 'irextras:' + n, 'count': RESULT_N.get(n, 1)}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    for k in ('car', 'van', 'truck', 'bus'):
        for n in ('road_' + k, 'road_' + k + '_paint'):
            json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    print('---- RoadClient.LAMPS')
    print(java_lamps())
