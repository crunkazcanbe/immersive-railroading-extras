#!/usr/bin/env python3
"""Batch 6f freight + fun (#26-30, her ask 10-12: "complex buttons and knobs and stuff, screens", real looking):
grain elevator complex (Receiving Pit, Leg, Dryer, Silo bin, Loadout), oil tank farm (Storage Tank, Tank Car Loading
Rack, Fire and Gas Panel), rail-served warehouse (Dock Door, Pallet Rack, Office + the Forklift entity model), Station
Parcel Lockers, and the holiday blocks (Festive Lighting Controller, Christmas Tree, String Lights, Halloween Display)
plus the two train decoration kit item textures. Models face north (the operator's side; track to the south),
controls on north surfaces at u = 16 - x. Live controls match freight6/Tile*.java ids; moving parts (dock door + lip,
rack pallets, tree / festoon LEDs, lantern flicker, forklift forks) are drawn by freight6/client/F6Render.
Writes models, controls, blockstates, item models, item textures, recipes and lang lines."""
import json, os, random
from PIL import Image
from bbkit import Model, ASSETS
import cabkit
from gen_bb_economy import X
from gen_bb_depot import lamp_row, push_row, estop_plate, mains_box
from gen_bb_steam import gauges, lab, screen, beacon, detector

COUNTS = {}


# ===================================================================================================== shared bits
def display(m, x0, x1, y0, y1, z, ids, cols, title='DISPLAY'):
    """a flush display: bezel, dark glass, one DIGITS line per id (north surface at z)"""
    m.box((x0 - 0.35, y0 - 0.35, z - 0.15), (x1 + 0.35, y1 + 0.35, z), 'paint:#1c1e20', title.lower() + ' bezel')
    m.box((x0, y0, z - 0.21), (x1, y1, z - 0.15), 'paint:#04120a', title.lower() + ' glass')
    sf = m.surface('north', face=z - 0.25, title=title)
    h = (y1 - y0) / len(ids)
    for i, cid in enumerate(ids):
        m.ctl(sf, 'digits', cid, X((x0 + x1) / 2), y1 - h * (i + 0.5), w=x1 - x0 - 0.4, h=h * 0.8, col=cols[i % len(cols)], label='', fmt='%s')
    return sf


def console(m, x0, x1, z0, z1, top, name='control desk', body='#2f3d4a'):
    """a steel control desk: plinth, body, sloped-look upstand line, hood, kick plate"""
    m.g(name)
    m.box((x0, 0, z0), (x1, top, z1), 'paint:' + body, name + ' body')
    m.box((x0 + 0.2, 0, z0 - 0.2), (x1 - 0.2, 0.9, z0), 'paint:#1c1d20', 'kick plate')
    m.box((x0 - 0.2, top, z0 - 0.2), (x1 + 0.2, top + 0.5, z1 + 0.2), 'paint:#1c242c', name + ' top')
    for x in (x0 + 0.5, x1 - 0.5):
        for y in (1.5, top - 1.0):
            m.bolt(x, y, z0, 'north', 0.3)


def pipe(m, a, b, r, mat='paint:#8a8e92', name='pipe'):
    """a straight pipe between two points that differ on one axis"""
    for i, ax in enumerate('xyz'):
        if abs(a[i] - b[i]) > 1e-6:
            c = list(a)
            c[i] = min(a[i], b[i])
            m.cyl(tuple(c), r, abs(a[i] - b[i]), ax, mat, name)
            return


def flange(m, c, r, axis, mat='paint:#6a6e72'):
    m.cyl(c, r * 1.6, 0.25, axis, mat, 'flange')


def valve(m, x, y, z, axis='x', col='#c82020'):
    """an inline gate valve with a handwheel on top"""
    m.cyl((x - 0.6 if axis == 'x' else x, y, z - 0.6 if axis == 'z' else z), 0.7, 1.2, axis, 'paint:#3a3f44', 'valve body')
    m.cyl((x, y + 0.6, z), 0.18, 1.4, 'y', 'metal:steel', 'valve stem')
    m.cyl((x, y + 2.0, z), 0.9, 0.2, 'y', 'paint:' + col, 'handwheel')


def railing(m, x0, x1, z0, z1, y, h=3.0):
    m.handrail([(x0, z0), (x1, z0), (x1, z1), (x0, z1), (x0, z0)], y, h)
    m.box((x0, y + h * 0.5 - 0.1, z0 - 0.1), (x1, y + h * 0.5 + 0.1, z0 + 0.1), 'paint:ral1023', 'knee rail')


def stair(m, x0, x1, z0, z1, y0, y1, steps):
    """a straight steel stair rising along -z"""
    for i in range(steps):
        y = y0 + (y1 - y0) * (i + 1) / steps
        z = z1 - (z1 - z0) * (i + 1) / steps
        m.box((x0, y - 0.3, z), (x1, y, z + (z1 - z0) / steps + 0.1), 'galv', 'tread')
    m.box((x0 - 0.3, y0, z0), (x0, y1, z1), 'paint:ral1023', 'stringer')
    m.box((x1, y0, z0), (x1 + 0.3, y1, z1), 'paint:ral1023', 'stringer')


def corrugated(m, cx, cz, r, y0, y1, n=16, col='galv'):
    """a round corrugated bin wall approximated by n vertical ribs"""
    import math
    for i in range(n):
        a = i * 2 * math.pi / n
        x, z = cx + math.cos(a) * r, cz + math.sin(a) * r
        w = r * math.pi / n * 1.05
        m.box((x - w, y0, z - w), (x + w, y1, z + w), col, 'bin wall rib')


def save(m, gui=0.4):
    m.save(item_gui=gui)
    COUNTS[m.name] = len(m.boxes)
    return m


# ===================================================================================================== #26 grain elevator
def grain_pit():
    """grated receiving hopper between the rails (south half) with its kerb, pit conveyor head, dust hood, intake
    scale readout and the control post on the north side"""
    m = Model('grain_pit')
    m.g('pit')
    m.box((0, 0, 0), (16, 1.0, 16), 'concrete', 'pit slab')
    m.box((1.0, 1.0, 5.0), (15.0, 1.6, 15.0), 'paint:#3a3f44', 'grate frame')
    for x in range(2, 15):
        m.box((x - 0.15, 1.6, 5.4), (x + 0.15, 1.9, 14.6), 'paint:#2a2c30', 'grate bar')
    for z in (7.0, 10.0, 13.0):
        m.box((1.4, 1.6, z - 0.15), (14.6, 1.8, z + 0.15), 'paint:#2a2c30', 'grate cross bar')
    m.box((0.6, 1.0, 4.4), (15.4, 2.2, 5.0), 'haz', 'kerb nosing')
    for x in (0.6, 15.0):
        m.box((x, 1.0, 5.0), (x + 0.4, 2.2, 15.4), 'haz', 'side kerb')
    m.g('track over the pit')
    for z in (7.2, 12.8):
        m.box((0, 1.9, z - 0.35), (16, 2.9, z + 0.35), 'metal:steel', 'rail')
        m.box((0, 2.9, z - 0.25), (16, 3.1, z + 0.25), 'metal:steel', 'rail head')
        for x in (0.5, 15.5):
            m.box((x - 0.6, 1.6, z - 0.7), (x + 0.6, 2.0, z + 0.7), 'paint:#2a2a2a', 'rail chair')
    for x in (0.2, 15.8):
        m.box((x - 0.4, 1.0, 6.0), (x + 0.4, 1.6, 14.0), 'wood', 'bridge timber')
    m.g('pit below')
    m.box((1.4, -3.0, 5.4), (14.6, 0.0, 14.6), 'concrete', 'pit hopper wall')
    m.box((6.0, -3.0, 9.0), (10.0, -2.0, 11.0), 'paint:#2a5a8a', 'pit sump pump')
    for x in (3.0, 6.0, 9.0, 12.0):
        m.cyl((x, -2.4, 9.0), 0.35, 2.0, 'z', 'metal:steel', 'belt idler')
    m.box((1.6, -2.0, 9.2), (14.4, -1.8, 10.8), 'rubber', 'pit belt')
    m.g('dust hood')
    m.box((2.0, 1.9, 15.2), (14.0, 4.0, 16.0), 'paint:#8a9098', 'dust hood skirt')
    m.cyl((13.0, 4.0, 15.6), 0.7, 6.0, 'y', 'galv', 'dust duct')
    m.cyl((13.0, 10.0, 15.6), 0.7, 3.0, 'z', 'galv', 'dust duct elbow')
    m.g('pit conveyor')
    m.box((0.0, 0.2, 9.0), (1.2, 1.0, 11.0), 'paint:#2a5a8a', 'conveyor head')
    m.cyl((-1.2, 0.6, 10.0), 0.6, 1.2, 'x', 'paint:#2a5a8a', 'conveyor motor')
    m.box((-1.4, 0.0, 9.2), (0.0, 0.3, 10.8), 'paint:#3a3f44', 'motor plinth')
    m.g('control post')
    m.box((5.0, 1.0, 0.8), (11.0, 3.0, 4.2), 'concrete', 'post plinth')
    m.box((5.4, 3.0, 1.2), (10.6, 19.0, 3.8), 'paint:#2f3d4a', 'control post')
    m.box((5.2, 19.0, 1.0), (10.8, 19.6, 4.0), 'paint:#1c242c', 'post roof')
    m.box((5.2, 17.0, 0.9), (10.8, 18.6, 1.2), 'label:RECEIVING|PIT 1', 'post plate')
    beacon(m, 8.0, 19.6, 2.5, 'ffb020')
    m.terminal_12v(8.0, 3.4, 3.8, 'south')
    mains_box(m, 10.6, 4.0, 2.5, 'east')
    m.box((1.0, 1.0, 1.0), (4.6, 7.0, 3.6), 'paint:#4c535a', 'scale indicator box')
    m.box((1.2, 4.6, 0.85), (4.4, 6.6, 1.0), 'gauge:scale', 'scale dial')
    m.box((1.0, 1.0, 0.85), (4.6, 2.4, 1.0), 'label:INTAKE|SCALE', 'scale label')
    f = m.surface('north', face=1.18, title='PIT POST')
    display(m, 5.8, 10.2, 13.2, 16.4, 1.2, ['line1', 'line2', 'tons'], ['7cf29a', '8ad4ff', 'ffd040'], 'PIT SCREEN')
    lab(m, f, X(8.0), 12.7, 'T THIS CAR')
    gauges(m, f, [('pitg', 'PIT'), ('carg', 'CAR')], X(6.9), 11.0, step=2.2, size=1.6)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('gatel', 'ffb020', 'GATE'), ('carl', '8ad4ff', 'CAR')], X(6.0), 8.6, step=1.4, size=0.45)
    lamp_row(m, f, [('dustl', '30e060', 'DUST'), ('dusty', 'ff3030', 'DUSTY', True), ('wetl', 'ffb020', 'WET')], X(6.0), 7.4, step=1.4, size=0.45)
    push_row(m, f, [('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('probe', '2050d0', 'PROBE')], X(6.2), 5.6, step=1.8, size=0.8)
    push_row(m, f, [('reset', '505860', 'RESET'), ('test', 'b0b0b0', 'TEST')], X(6.2), 3.9, step=1.8, size=0.7)
    m.ctl(f, 'key', 'k_dust', X(9.8), 3.9, w=0.8, h=0.8, col='c0a040', label='DUST COLL', pos=['OFF', 'RUN'])
    lab(m, f, X(9.8), 3.2, 'DUST')
    s2 = m.surface('east', face=16 - 10.6 - 0.02, title='POST SIDE')
    m.ctl(s2, 'mushroom', 'm_estop', 13.5, 14.0, w=1.2, h=1.2, col='d02020', label='E-STOP')
    m.ctl(s2, 'digits', 'probe', 13.5, 11.5, w=2.2, h=0.8, col='ffd040', label='MOISTURE', fmt='%s %%')
    m.ctl(s2, 'digits', 'total', 13.5, 10.2, w=2.2, h=0.8, col='7cf29a', label='TOTAL', fmt='%s T')
    top = m.surface('up', face=16 - 1.95, title='GRATE')
    m.ctl(top, 'zone', 'pit', 8.0, 10.0, w=12.0, h=8.0, col='000000', label='')
    return save(m)


def grain_leg():
    """bucket elevator: boot pit, twin trunking up to the head house (32 px), drive + distributor head; the plant MCC
    and the control desk at its foot (north)"""
    m = Model('grain_leg')
    m.g('boot')
    m.box((9.0, 0, 8.0), (15.0, 4.0, 15.0), 'paint:#5a6a3a', 'leg boot')
    m.box((9.2, 1.0, 7.9), (14.8, 3.6, 8.0), 'paint:#4a5a30', 'boot clean-out door')
    for x in (9.8, 14.2):
        for y in (1.4, 3.2):
            m.bolt(x, y, 7.9, 'north', 0.3)
    m.cyl((15.0, 2.0, 11.5), 0.7, 1.6, 'x', 'galv', 'boot inlet spout')
    m.g('trunking')
    for x0 in (9.4, 12.2):
        m.box((x0, 4.0, 9.0), (x0 + 2.4, 26.0, 14.0), 'paint:#6a7a48', 'leg casing')
        for y in range(6, 26, 4):
            m.box((x0 - 0.1, y, 8.9), (x0 + 2.5, y + 0.3, 14.1), 'paint:#4a5a30', 'casing flange')
    m.box((9.4, 14.0, 8.8), (11.8, 16.0, 9.0), 'paint:#4a5a30', 'inspection door')
    m.g('head house')
    m.box((8.4, 26.0, 7.6), (15.6, 30.5, 15.4), 'paint:#6a7a48', 'head house')
    m.box((8.0, 30.5, 7.2), (16.0, 31.0, 15.8), 'paint:#3a3a3a', 'head roof')
    m.cyl((8.4, 28.2, 11.5), 1.3, 2.2, 'x', 'paint:#2a5a8a', 'head drive motor 30 kW')
    m.box((6.2, 26.6, 10.0), (8.4, 29.8, 13.0), 'paint:#3a3f44', 'gearbox')
    m.box((8.6, 27.0, 7.5), (15.4, 28.6, 7.6), 'label:LEG 1', 'head lettering')
    m.cyl((12.0, 31.0, 11.5), 0.5, 1.0, 'y', 'paint:#c8a020', 'speed switch')
    m.g('distributor')
    m.cyl((12.0, 22.0, 15.4), 1.4, 4.0, 'y', 'paint:#5a6a3a', 'distributor turnhead')
    for i, (dx, dz) in enumerate(((-3.0, 1.0), (-1.5, 2.4), (0.0, 3.0), (1.5, 2.4), (3.0, 1.0))):
        m.box((12.0 + dx - 0.5, 18.0 - i * 0.4, 15.6 + dz - 0.5), (12.0 + dx + 0.5, 22.0, 15.6 + dz + 0.5), 'galv', 'spout to bin')
    m.ladder(15.6, 16.4, 0.0, 30.0, 9.0)
    for y in (8.0, 16.0, 24.0):
        m.box((15.4, y, 8.6), (16.6, y + 0.2, 10.6), 'galv', 'ladder hoop')
    m.box((7.6, 25.6, 6.8), (16.2, 26.0, 16.2), 'galv', 'head platform')
    railing(m, 7.8, 16.0, 7.0, 16.0, 26.0, 2.6)
    m.g('MCC')
    m.box((0.4, 0, 9.0), (8.6, 20.0, 15.6), 'paint:#3a4a5a', 'plant MCC')
    m.box((0.2, 20.0, 8.8), (8.8, 20.6, 15.8), 'paint:#2a3540', 'MCC hood')
    cabkit.fit(m, 0.8, 8.2, 1.0, 19.4, 9.0, col='paint:#3a4a5a', title='GRAIN MCC|LEG DRYER PIT', volts='400V 50HZ')
    m.box((-0.8, 6.0, 10.6), (0.4, 9.0, 13.4), 'paint:#3a3f44', 'isolator box', west='danger:400V')
    m.cyl((-1.1, 7.6, 12.0), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    m.cyl((-0.2, 0, 12.0), 0.32, 6.0, 'y', 'cable:202020', 'armoured mains cable')
    m.g('control desk')
    console(m, 0.4, 15.6, 0.6, 7.8, 11.0)
    m.box((0.6, 11.5, 4.6), (15.4, 18.6, 7.8), 'paint:#2b3743', 'desk upstand')
    m.box((0.4, 18.6, 4.4), (15.6, 19.2, 8.0), 'paint:#1c242c', 'upstand hood')
    m.nameplate(1.0, 17.8, 6.6, 18.4, 4.6, 'GRAIN LEG')
    m.terminal_12v(4.0, 1.6, 7.8, 'south')
    f = m.surface('north', face=0.58, title='LEG DESK')
    display(m, 0.9, 7.6, 7.6, 10.6, 0.6, ['line1', 'line2', 'line3'], ['7cf29a', '8ad4ff', 'ffb020'], 'LEG SCREEN')
    m.ctl(f, 'digits', 'pageno', X(6.8), 7.1, w=1.4, h=0.5, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'amps', X(9.4), 9.8, w=2.4, h=1.0, col='ffd040', label='AMPS', fmt='%s A')
    m.ctl(f, 'digits', 'rpm', X(9.4), 8.3, w=2.4, h=0.8, col='7cf29a', label='HEAD RPM', fmt='%s')
    lab(m, f, X(9.4), 7.5, 'AMPS / RPM')
    gauges(m, f, [('ampg', 'MOTOR A'), ('pitg', 'PIT')], X(12.0), 9.4, step=2.6, size=1.9)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('runl', '30e060', 'RUN'), ('chokel', 'ff3030', 'CHOKE', True),
                    ('pitl', '8ad4ff', 'PIT'), ('autol', '8ad4ff', 'AUTO'), ('dryerl', 'ffb020', 'DRYER'), ('speedl', '30e060', 'SPEED')], X(1.4), 5.6, step=1.5, size=0.45)
    push_row(m, f, [('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('page', '2050d0', 'PAGE'), ('reset', '505860', 'RESET'), ('test', 'b0b0b0', 'TEST')],
             X(1.6), 3.4, step=1.8, size=0.8)
    m.ctl(f, 'key', 'k_auto', X(11.0), 3.4, w=0.9, h=0.9, col='c0a040', label='AUTO', pos=['MAN', 'AUTO'])
    lab(m, f, X(11.0), 2.6, 'AUTO')
    estop_plate(m, f, X(13.6), 3.4)
    u = m.surface('north', face=4.48, title='DISTRIBUTOR')
    m.ctl(u, 'selector', 's_route', X(3.0), 15.4, w=1.6, h=1.6, col='202020', label='DISTRIBUTOR', pos=['BIN 1', 'BIN 2', 'BIN 3', 'BIN 4', 'BIN 5', 'BIN 6', 'DRYER'])
    lab(m, u, X(3.0), 13.8, 'DISTRIBUTOR')
    for i in range(6):
        m.ctl(u, 'gauge', 'b%d' % i, X(6.0 + i * 1.5), 16.0, w=1.2, h=1.2, col='f2f0e6', label='BIN %d' % (i + 1), max=100)
        m.ctl(u, 'lamp', 'bl%d' % i, X(6.0 + i * 1.5), 14.4, w=0.45, h=0.45, col='ffb020', label='TO BIN %d' % (i + 1))
        lab(m, u, X(6.0 + i * 1.5), 13.6, 'BIN%d' % (i + 1))
    m.ctl(u, 'lamp', 'bl6', X(15.0), 14.4, w=0.45, h=0.45, col='ffb020', label='TO DRYER')
    lab(m, u, X(15.0), 13.6, 'DRYER')
    m.box((1.0, 12.0, 4.5), (15.0, 17.6, 4.6), 'paint:#20262c', 'mimic panel')
    for i in range(6):
        m.box((5.6 + i * 1.5, 12.4, 4.45), (6.4 + i * 1.5, 13.2, 4.5), 'paint:#c8c4b8', 'bin mimic')
    s = m.surface('east', face=-0.02, title='SPARES')
    m.box((15.6, 1.0, 1.0), (16.0, 5.0, 4.0), 'paint:#4c535a', 'spares cupboard')
    m.ctl(s, 'zone', 'spares', 13.5, 3.0, w=2.8, h=3.8, col='000000', label='')
    return save(m)


def grain_dryer():
    """mixed-flow tower dryer: wet garner on top, louvred column, burner housing + fan, plenum, metering rolls and the
    discharge, a fuel hopper and the dryer control panel"""
    m = Model('grain_dryer')
    m.g('column')
    m.box((3.0, 6.0, 4.0), (13.0, 26.0, 13.0), 'paint:#b8bcc0', 'dryer column')
    for y in range(7, 26, 2):
        m.box((3.2, y, 3.85), (12.8, y + 0.8, 4.0), 'louvre', 'air louvre')
        m.box((13.0, y, 4.2), (13.15, y + 0.8, 12.8), 'louvre', 'air louvre', faces=('east',))
    for x in (3.0, 12.6):
        m.box((x, 6.0, 3.6), (x + 0.4, 26.0, 4.0), 'paint:#8a8e92', 'column post')
    m.g('garner')
    m.box((2.6, 26.0, 3.6), (13.4, 30.0, 13.4), 'paint:#c8ccd0', 'wet garner')
    m.box((2.4, 30.0, 3.4), (13.6, 30.6, 13.6), 'paint:#8a8e92', 'garner lid')
    m.cyl((8.0, 30.6, 8.5), 0.9, 1.4, 'y', 'galv', 'fill spout')
    m.box((3.0, 27.0, 3.5), (13.0, 29.0, 3.6), 'label:GRAIN DRYER', 'garner lettering')
    m.g('base + plenum')
    m.box((2.0, 0, 3.0), (14.0, 6.0, 14.0), 'paint:#8a8e92', 'dryer base')
    m.box((3.0, 1.0, 2.9), (13.0, 5.0, 3.0), 'paint:#6a6e72', 'metering roll access')
    m.cyl((3.0, 3.0, 8.5), 0.4, 10.0, 'x', 'metal:steel', 'metering roll shaft')
    m.cyl((14.0, 2.4, 8.5), 1.0, 2.0, 'x', 'paint:#2a5a8a', 'discharge motor')
    m.cyl((14.0, 0.4, 12.0), 0.8, 2.0, 'x', 'galv', 'discharge conveyor')
    m.g('burner + fan')
    m.box((13.0, 6.0, 5.0), (16.0, 14.0, 12.0), 'paint:#5a5e62', 'burner housing')
    m.cyl((16.0, 10.0, 8.5), 2.4, 0.6, 'x', 'mesh', 'fan inlet guard')
    m.box((13.2, 11.0, 4.9), (15.0, 13.0, 5.0), 'danger:HOT|BURNER', 'burner danger plate')
    m.cyl((14.5, 6.0, 4.0), 0.5, 2.0, 'z', 'paint:#c82020', 'burner sight glass tube')
    m.box((14.0, 6.5, 3.6), (15.0, 7.5, 4.0), 'lamp:ff6010', 'flame window')
    m.g('fuel hopper')
    m.box((0.0, 6.0, 9.0), (3.0, 12.0, 14.0), 'paint:#3a3f44', 'fuel hopper')
    m.box((-0.2, 12.0, 8.8), (3.2, 12.4, 14.2), 'paint:#2a2c30', 'hopper lid')
    m.box((0.4, 4.0, 10.6), (2.6, 6.0, 12.4), 'paint:#3a3f44', 'fuel auger')
    m.box((0.0, 9.0, 8.9), (3.0, 11.0, 9.0), 'label:FUEL', 'fuel label')
    m.g('platform + ladder')
    m.ladder(14.2, 15.0, 14.0, 30.0, 12.6)
    m.box((2.0, 25.6, 13.4), (14.0, 26.0, 16.0), 'galv', 'top platform')
    railing(m, 2.2, 13.8, 13.6, 15.8, 26.0, 2.4)
    m.g('control panel')
    m.box((0.4, 0, 0.6), (8.0, 14.0, 3.0), 'paint:#3a4a5a', 'dryer panel')
    m.box((0.2, 14.0, 0.4), (8.2, 14.6, 3.2), 'paint:#2a3540', 'panel hood')
    m.box((8.0, 0, 0.6), (13.0, 7.0, 3.0), 'paint:#3a4a5a', 'panel wing')
    m.terminal_12v(4.0, 1.0, 3.0, 'south')
    mains_box(m, 13.0, 3.0, 1.8, 'east')
    f = m.surface('north', face=0.58, title='DRYER PANEL')
    display(m, 0.9, 7.5, 11.2, 13.4, 0.6, ['line1', 'line2'], ['7cf29a', '8ad4ff'], 'DRYER SCREEN')
    m.ctl(f, 'digits', 'moist', X(2.6), 10.0, w=2.6, h=1.0, col='ffd040', label='MOISTURE', fmt='%s %%')
    m.ctl(f, 'digits', 'plen', X(6.0), 10.0, w=2.6, h=1.0, col='ff8040', label='PLENUM', fmt='%s C')
    lab(m, f, X(2.6), 9.2, 'MOISTURE'); lab(m, f, X(6.0), 9.2, 'PLENUM C')
    gauges(m, f, [('garnerg', 'GARNER'), ('plenumg', 'PLENUM'), ('moistg', 'H2O'), ('fuelg', 'FUEL')], X(1.6), 7.6, step=1.8, size=1.4)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('burnl', 'ffb020', 'BURN'), ('flame', 'ff6010', 'FLAME', True), ('trip', 'ff3030', 'TRIP', True)],
             X(1.4), 5.4, step=1.4, size=0.42)
    lamp_row(m, f, [('dryl', '30e060', 'DRY'), ('wetl', 'ffb020', 'WET'), ('discl', '8ad4ff', 'DISCH'), ('fuell', 'ff3030', 'LOW FUEL')], X(1.4), 4.2, step=1.4, size=0.42)
    push_row(m, f, [('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('reset', '505860', 'RESET'), ('test', 'b0b0b0', 'TEST')], X(1.6), 2.4, step=1.6, size=0.75)
    w = m.surface('north', face=0.58, title='DRYER WING')
    m.ctl(w, 'key', 'k_burner', X(9.2), 5.8, w=0.9, h=0.9, col='c0a040', label='BURNER', pos=['OFF', 'ON'])
    lab(m, w, X(9.2), 5.0, 'BURNER')
    m.ctl(w, 'selector', 's_target', X(11.4), 5.8, w=1.2, h=1.2, col='202020', label='TARGET', pos=['12 %', '13 %', '14 %', '15 %'])
    lab(m, w, X(11.4), 4.8, 'TARGET')
    m.ctl(w, 'selector', 's_bin', X(9.2), 3.2, w=1.2, h=1.2, col='202020', label='DISCHARGE TO', pos=['BIN 1', 'BIN 2', 'BIN 3', 'BIN 4', 'BIN 5', 'BIN 6'])
    lab(m, w, X(9.2), 2.2, 'TO BIN')
    estop_plate(m, w, X(11.6), 2.8)
    hp = m.surface('north', face=8.98, title='FUEL HOPPER')
    m.ctl(hp, 'zone', 'fuel', X(1.5), 9.0, w=2.8, h=5.0, col='000000', label='')
    return save(m)


def grain_silo():
    """a corrugated steel bin (two blocks high) on a hopper bottom and legs: roof cone + vent, ladder cage, level
    gauge column, aeration fan, outlet gate and the bin's instrument panel"""
    m = Model('grain_silo')
    m.g('legs + hopper')
    for (x, z) in ((1.0, 1.0), (13.8, 1.0), (1.0, 13.8), (13.8, 13.8)):
        m.box((x, 0, z), (x + 1.2, 7.0, z + 1.2), 'galv', 'bin leg')
        m.box((x - 0.3, 0, z - 0.3), (x + 1.5, 0.4, z + 1.5), 'concrete', 'leg footing')
    for i, y in enumerate((3.0, 4.6, 6.2)):
        m.box((5.0 - i * 1.6, y, 5.0 - i * 1.6), (11.0 + i * 1.6, y + 1.6, 11.0 + i * 1.6), 'galv', 'hopper bottom')
    m.box((6.6, 1.4, 6.6), (9.4, 3.0, 9.4), 'paint:#3a3f44', 'outlet gate housing')
    m.box((9.4, 1.8, 7.4), (11.0, 2.6, 8.6), 'paint:#c8a020', 'gate rack')
    m.g('bin wall')
    corrugated(m, 8.0, 8.0, 7.0, 7.8, 28.0, 16)
    m.box((1.6, 7.8, 1.6), (14.4, 28.0, 14.4), 'galv', 'bin core')
    for y in (8.0, 13.0, 18.0, 23.0, 27.6):
        corrugated(m, 8.0, 8.0, 7.15, y, y + 0.4, 8, 'paint:#8a8e92')
    m.box((3.0, 18.0, 0.7), (13.0, 22.0, 0.9), 'label:BIN', 'bin lettering')
    m.g('roof')
    for i, y in enumerate((28.0, 29.0, 30.0, 31.0)):
        s = 7.4 - i * 1.7
        m.box((8 - s, y, 8 - s), (8 + s, y + 1.0, 8 + s), 'galv', 'roof cone ring')
    m.cyl((8.0, 31.6, 8.0), 0.8, 0.4, 'y', 'paint:#8a8e92', 'roof vent')
    m.g('ladder')
    m.ladder(14.6, 15.6, 7.8, 31.0, 7.6)
    for y in range(12, 31, 4):
        m.box((14.4, y, 8.2), (15.8, y + 0.2, 10.0), 'galv', 'cage hoop')
    m.g('level gauge')
    m.box((0.4, 8.0, 7.0), (0.9, 27.0, 9.0), 'paint:#2a2c30', 'level gauge board')
    m.box((0.3, 9.0, 7.6), (0.4, 26.0, 8.4), 'paint:#f0f0f0', 'gauge scale')
    m.g('aeration')
    m.cyl((15.0, 4.0, 8.0), 1.6, 1.2, 'x', 'paint:#2a5a8a', 'aeration fan')
    m.cyl((16.2, 4.0, 8.0), 1.4, 0.3, 'x', 'mesh', 'fan guard')
    m.box((13.6, 3.0, 7.0), (15.0, 5.0, 9.0), 'galv', 'aeration duct')
    m.g('instrument panel')
    m.box((3.0, 0.4, 0.0), (13.0, 7.6, 1.8), 'paint:#3a4a5a', 'bin panel')
    m.box((2.8, 7.6, -0.2), (13.2, 8.0, 2.0), 'paint:#2a3540', 'panel hood')
    m.terminal_12v(11.0, 0.8, 1.8, 'south')
    mains_box(m, 13.0, 1.0, 1.0, 'east')
    f = m.surface('north', face=-0.02, title='BIN PANEL')
    m.ctl(f, 'digits', 'binno', X(4.2), 6.6, w=1.6, h=1.2, col='ffd040', label='BIN', fmt='BIN %s')
    display(m, 6.0, 12.6, 5.6, 7.3, 0.0, ['line1', 'line2'], ['7cf29a', '8ad4ff'], 'BIN SCREEN')
    m.ctl(f, 'digits', 'tons', X(4.2), 5.0, w=1.8, h=0.8, col='7cf29a', label='TONNES', fmt='%s T')
    gauges(m, f, [('levelg', 'LEVEL'), ('tempg', 'TEMP'), ('moistg', 'H2O')], X(7.0), 4.2, step=1.8, size=1.3)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('aerl', '8ad4ff', 'AERATE'), ('gatel', 'ffb020', 'GATE')], X(3.6), 2.6, step=1.3, size=0.4)
    lamp_row(m, f, [('high', 'ffb020', 'HIGH'), ('low', '8ad4ff', 'LOW'), ('hot', 'ff3030', 'HOT', True), ('wet', 'ffb020', 'WET')], X(9.0), 2.6, step=1.3, size=0.4)
    push_row(m, f, [('probe', '2050d0', 'PROBE'), ('reset', '505860', 'RESET'), ('test', 'b0b0b0', 'TEST')], X(10.6), 4.6, step=1.1, size=0.55, label_dv=-0.55)
    m.ctl(f, 'key', 'k_aer', X(12.4), 1.2, w=0.7, h=0.7, col='c0a040', label='AERATION', pos=['OFF', 'ON'])
    lab(m, f, X(11.2), 1.2, 'AERATE')
    m.ctl(f, 'lever', 'm_gate', X(4.2), 2.0, w=0.6, h=1.6, col='e8c020', label='OUTLET GATE', pos=['SHUT', 'OPEN'])
    lab(m, f, X(5.4), 1.0, 'GATE')
    tc = m.surface('north', face=-0.02, title='TEMP CABLES')
    for k in range(4):
        m.ctl(tc, 'lamp', 'tc%d' % k, X(6.0 + k * 0.9), 1.0, w=0.3, h=0.3, col='ff8040', label='CABLE %d' % (k + 1))
    m.box((5.4, 0.6, -0.1), (9.0, 1.4, 0.0), 'paint:#20262c', 'cable lamp strip')
    hz = m.surface('east', face=16 - 14.6 - 0.02, title='HATCH')
    m.box((13.0, 9.0, 3.0), (14.6, 11.0, 5.0), 'paint:#8a8e92', 'sample hatch')
    m.ctl(hz, 'zone', 'hatch', 12.0, 10.0, w=2.0, h=2.0, col='000000', label='')
    return save(m)


def grain_loadout():
    """batch-weigh loadout: legs over the track (south), surge bin, weigh hopper on load cells, telescopic spout
    hanging over the car; the operator's cab and desk on the north side"""
    m = Model('grain_loadout')
    m.g('structure')
    for (x, z) in ((0.4, 6.0), (14.0, 6.0), (0.4, 28.4), (14.0, 28.4)):
        m.box((x, 0, z), (x + 1.6, 30.0, z + 1.6), 'paint:#2a62c8', 'loadout column')
        m.box((x - 0.3, 0, z - 0.3), (x + 1.9, 0.6, z + 1.9), 'concrete', 'column footing')
    for y in (22.0, 29.0):
        m.box((0.4, y, 6.0), (15.6, y + 1.0, 7.6), 'paint:#2a62c8', 'cross beam')
        m.box((0.4, y, 28.4), (15.6, y + 1.0, 30.0), 'paint:#2a62c8', 'cross beam')
        m.box((0.4, y, 7.6), (2.0, y + 1.0, 28.4), 'paint:#2a62c8', 'side beam')
        m.box((14.0, y, 7.6), (15.6, y + 1.0, 28.4), 'paint:#2a62c8', 'side beam')
    for z in (12.0, 18.0, 24.0):
        m.box((0.6, 10.0, z), (1.0, 22.0, z + 0.4), 'paint:#1a4aa8', 'brace')
    m.g('surge bin + weigh hopper')
    m.box((3.0, 26.0, 13.0), (13.0, 32.0, 23.0), 'paint:#c8ccd0', 'surge bin')
    m.box((4.0, 24.0, 14.0), (12.0, 26.0, 22.0), 'paint:#a8acb0', 'surge bin cone')
    m.box((4.0, 18.0, 14.0), (12.0, 23.0, 22.0), 'paint:#e0e4e8', 'weigh hopper')
    for (x, z) in ((4.0, 14.0), (11.4, 14.0), (4.0, 21.4), (11.4, 21.4)):
        m.box((x, 23.0, z), (x + 0.6, 24.0, z + 0.6), 'paint:#c82020', 'load cell')
    m.box((4.2, 20.0, 13.9), (11.8, 21.6, 14.0), 'label:WEIGH HOPPER', 'hopper label')
    m.box((5.0, 16.0, 15.0), (11.0, 18.0, 21.0), 'paint:#a8acb0', 'discharge gate')
    m.cyl((7.0, 16.0, 18.0), 0.5, 2.0, 'x', 'metal:steel', 'gate cylinder')
    m.g('spout')
    for i, r in enumerate((2.4, 2.1, 1.8)):
        m.cyl((8.0, 13.0 - i * 2.4, 18.0), r, 3.0, 'y', 'paint:#5a5e62' if i % 2 else 'paint:#6a6e72', 'telescopic spout')
    m.cyl((8.0, 7.6, 18.0), 1.9, 0.6, 'y', 'rubber', 'spout skirt')
    for x in (5.0, 11.0):
        m.cyl((x, 16.0, 18.0), 0.08, 6.0, 'y', 'cable:202020', 'spout hoist wire')
    m.g('walkway')
    m.box((0.4, 21.6, 7.6), (15.6, 22.0, 13.0), 'galv', 'walkway grating')
    railing(m, 0.6, 15.4, 7.8, 12.8, 22.0, 2.6)
    m.ladder(14.2, 15.2, 0.0, 22.0, 5.4)
    m.g('cab + desk')
    m.box((1.0, 0, 0.6), (15.0, 9.0, 5.8), 'paint:#2f3d4a', 'loadout cabin desk')
    m.box((0.8, 9.0, 0.4), (15.2, 9.6, 6.0), 'paint:#1c242c', 'desk top')
    m.box((1.0, 9.6, 3.6), (15.0, 16.0, 5.8), 'paint:#2b3743', 'desk upstand')
    m.box((0.8, 16.0, 3.4), (15.2, 16.6, 6.0), 'paint:#1c242c', 'upstand hood')
    m.box((1.0, 0, 0.4), (15.0, 0.9, 0.6), 'paint:#1c1d20', 'kick plate')
    m.nameplate(1.4, 15.0, 7.0, 15.6, 3.6, 'LOADOUT 1')
    m.terminal_12v(12.0, 1.0, 5.8, 'south')
    mains_box(m, 15.0, 2.0, 3.2, 'east')
    f = m.surface('north', face=0.58, title='LOADOUT DESK')
    m.ctl(f, 'digits', 'loaded', X(3.4), 7.8, w=3.4, h=1.1, col='ffd040', label='LOADED', fmt='%s T')
    m.ctl(f, 'digits', 'target', X(7.4), 7.8, w=3.4, h=1.1, col='8ad4ff', label='TARGET', fmt='%s T')
    m.ctl(f, 'digits', 'scale', X(11.6), 7.8, w=3.4, h=1.1, col='7cf29a', label='SCALE', fmt='%s T')
    lab(m, f, X(3.4), 6.9, 'LOADED'); lab(m, f, X(7.4), 6.9, 'TARGET'); lab(m, f, X(11.6), 6.9, 'ON SCALE')
    gauges(m, f, [('hopperg', 'HOPPER'), ('carg', 'CAR'), ('bing', 'BIN')], X(3.0), 5.4, step=2.2, size=1.6)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('runl', '30e060', 'RUN'), ('spoutl', 'ffb020', 'SPOUT'), ('carl', '8ad4ff', 'CAR')], X(9.0), 5.8, step=1.4, size=0.42)
    lamp_row(m, f, [('filll', 'ffb020', 'FILL'), ('weighl', '8ad4ff', 'WEIGH'), ('discl', '30e060', 'DISCH'), ('gatel', 'ffb020', 'BIN GATE')], X(9.0), 4.4, step=1.4, size=0.42)
    push_row(m, f, [('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('test', 'b0b0b0', 'TEST')], X(3.0), 2.4, step=2.0, size=0.85)
    estop_plate(m, f, X(13.4), 2.4)
    u = m.surface('north', face=3.58, title='LOADOUT UPSTAND')
    display(m, 1.6, 8.6, 12.4, 14.8, 3.6, ['line1', 'line2', 'line3'], ['7cf29a', '8ad4ff', 'ffb020'], 'LOADOUT SCREEN')
    m.ctl(u, 'selector', 's_bin', X(10.0), 13.4, w=1.2, h=1.2, col='202020', label='FROM BIN', pos=['BIN 1', 'BIN 2', 'BIN 3', 'BIN 4', 'BIN 5', 'BIN 6'])
    m.ctl(u, 'selector', 's_qty', X(12.2), 13.4, w=1.2, h=1.2, col='202020', label='QTY', pos=['1/4', '1/2', '3/4', 'FULL'])
    m.ctl(u, 'lever', 'm_spout', X(14.2), 13.2, w=0.6, h=2.0, col='e8c020', label='SPOUT', pos=['UP', 'DOWN'])
    for (x, t) in ((10.0, 'BIN'), (12.2, 'QTY'), (14.2, 'SPOUT')): lab(m, u, X(x), 11.4, t)
    s = m.surface('west', face=0.88, title='CAB LOCKER')
    m.box((0.9, 1.0, 1.0), (1.0, 6.0, 3.0), 'paint:#4c535a', 'cab locker', faces=('west',))
    m.ctl(s, 'zone', 'cab', 2.0, 3.5, w=2.0, h=4.8, col='000000', label='')
    return save(m)


# ===================================================================================================== #27 oil tank farm
def oil_tank():
    """welded storage tank in its bund: shell courses, wind girder, cone roof with vents + foam chambers, spiral-ish
    stair, servo level gauge, valve manifold, nameplate and the tank's instrument box"""
    m = Model('oil_tank')
    m.g('bund')
    m.box((-0.0, 0, -0.0), (16, 0.6, 16), 'concrete', 'bund floor')
    for (a, b) in (((0, 0.6, 0), (16, 2.2, 0.6)), ((0, 0.6, 15.4), (16, 2.2, 16)), ((0, 0.6, 0.6), (0.6, 2.2, 15.4)), ((15.4, 0.6, 0.6), (16, 2.2, 15.4))):
        m.box(a, b, 'concrete', 'bund wall')
    m.g('shell')
    corrugated(m, 8.0, 8.5, 6.4, 1.0, 26.0, 16, 'paint:#e8e8e0')
    m.box((2.0, 1.0, 2.5), (14.0, 26.0, 14.5), 'paint:#e8e8e0', 'tank shell core')
    for y in (5.0, 10.0, 15.0, 20.0):
        corrugated(m, 8.0, 8.5, 6.5, y, y + 0.15, 8, 'paint:#c8c8c0')
    corrugated(m, 8.0, 8.5, 6.9, 23.0, 23.6, 8, 'paint:#a8a8a0')
    m.box((3.0, 14.0, 1.9), (13.0, 18.0, 2.1), 'label:TANK|DIESEL', 'tank lettering')
    m.g('roof')
    for i, y in enumerate((26.0, 27.0, 28.0)):
        s = 6.6 - i * 2.0
        m.box((8 - s, y, 8.5 - s), (8 + s, y + 1.0, 8.5 + s), 'paint:#d0d0c8', 'roof cone')
    for (x, z) in ((4.0, 6.0), (12.0, 11.0)):
        m.cyl((x, 27.0, z), 0.6, 2.0, 'y', 'paint:#8a8e92', 'pressure vacuum vent')
        m.cyl((x, 29.0, z), 0.9, 0.3, 'y', 'paint:#8a8e92', 'vent hood')
    for (x, z) in ((2.0, 8.5), (14.0, 8.5)):
        m.box((x - 0.8, 22.0, z - 0.8), (x + 0.8, 25.0, z + 0.8), 'paint:#c82020', 'foam chamber')
        m.cyl((x, 0.6, z + 1.6), 0.3, 21.4, 'y', 'paint:#c82020', 'foam riser')
    railing(m, 3.0, 13.0, 3.5, 13.5, 26.6, 2.0)
    m.g('stair')
    for i in range(10):
        y = 2.0 + i * 2.4
        m.box((14.2, y, 3.0 + i * 1.1), (15.8, y + 0.3, 4.4 + i * 1.1), 'galv', 'stair tread')
    m.box((15.8, 2.0, 3.0), (16.0, 26.0, 15.0), 'paint:ral1023', 'stair handrail')
    m.g('gauge + manifold')
    m.cyl((1.4, 2.0, 4.0), 0.25, 24.0, 'y', 'paint:#8a8e92', 'gauge float well')
    m.box((0.8, 3.0, 3.0), (2.0, 6.0, 5.0), 'paint:#3a3f44', 'servo gauge head')
    m.box((0.6, 4.0, 3.5), (0.8, 5.6, 4.6), 'gauge:level', 'gauge dial')
    for (x, nm) in ((5.0, 'inlet'), (11.0, 'outlet')):
        pipe(m, (x, 2.6, 2.4), (x, 2.6, 0.2), 0.7, 'paint:#3a3f44', nm + ' nozzle')
        flange(m, (x, 2.6, 2.2), 0.7, 'z')
        valve(m, x, 2.6, 1.0, 'z', '#c82020' if nm == 'outlet' else '#2050d0')
    m.g('instrument box')
    m.box((5.6, 3.4, 0.4), (10.4, 10.0, 1.6), 'paint:#3a4a5a', 'tank instrument box')
    m.box((5.4, 10.0, 0.2), (10.6, 10.4, 1.8), 'paint:#2a3540', 'box hood')
    m.terminal_12v(8.0, 0.8, 1.6, 'south')
    f = m.surface('north', face=0.38, title='TANK BOX')
    m.ctl(f, 'digits', 'tankno', X(6.6), 9.2, w=1.8, h=0.9, col='ffd040', label='TANK', fmt='TANK %s')
    display(m, 5.9, 10.1, 7.0, 8.6, 0.4, ['line1', 'line2'], ['7cf29a', '8ad4ff'], 'TANK SCREEN')
    m.ctl(f, 'digits', 'litres', X(9.3), 9.2, w=1.8, h=0.7, col='7cf29a', label='M3', fmt='%s M3')
    m.ctl(f, 'gauge', 'levelg', X(6.8), 5.6, w=1.4, h=1.4, col='f2f0e6', label='LEVEL', max=100)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('high', 'ffb020', 'H'), ('hh', 'ff3030', 'HH', True), ('low', '8ad4ff', 'L')], X(5.9), 4.2, step=1.2, size=0.38, label_dv=-0.45)
    lamp_row(m, f, [('inl', '2050d0', 'IN'), ('outl', 'c82020', 'OUT'), ('firel', 'ff3030', 'FIRE', True)], X(8.4), 6.2, step=1.0, size=0.38, label_dv=-0.45)
    push_row(m, f, [('dip', '2050d0', 'DIP'), ('fill', '1fa34a', 'FILL'), ('reset', '505860', 'RST'), ('test', 'b0b0b0', 'TEST')], X(6.2), 3.0 + 0.0, step=1.2, size=0.5, label_dv=-0.5)
    v = m.surface('north', face=-0.02, title='VALVES')
    m.ctl(v, 'lever', 'm_in', X(5.0), 2.6, w=0.6, h=1.8, col='2050d0', label='INLET VALVE', pos=['OPEN', 'SHUT'])
    m.ctl(v, 'lever', 'm_out', X(11.0), 2.6, w=0.6, h=1.8, col='c82020', label='OUTLET VALVE', pos=['OPEN', 'SHUT'])
    lab(m, v, X(5.0), 1.2, 'INLET'); lab(m, v, X(11.0), 1.2, 'OUTLET')
    k = m.surface('west', face=-0.02, title='KIT BOX')
    m.box((0.0, 7.0, 10.0), (0.6, 10.0, 13.0), 'paint:#c82020', 'dip kit box')
    m.ctl(k, 'zone', 'kit', 11.5, 8.5, w=3.0, h=3.0, col='000000', label='')
    return save(m)


def oil_rack():
    """top-loading rack over the track (south): stair tower, platform and gangway, counterweighted loading arm with
    drop tube, product header with meter, ground-verification box, overfill probe; the batch controller on the north"""
    m = Model('oil_rack')
    m.g('tower')
    for (x, z) in ((0.4, 2.0), (6.0, 2.0), (0.4, 10.0), (6.0, 10.0)):
        m.box((x, 0, z), (x + 1.0, 30.0, z + 1.0), 'paint:ral1023', 'tower column')
    m.box((0.2, 22.0, 1.8), (7.2, 22.6, 11.2), 'galv', 'loading platform')
    railing(m, 0.4, 7.0, 2.0, 11.0, 22.6, 2.6)
    stair(m, 1.6, 5.8, 2.6, 10.4, 0.0, 22.0, 11)
    m.box((0.2, 29.0, 1.8), (7.2, 30.0, 11.2), 'paint:#8a8e92', 'canopy')
    m.g('gangway')
    m.box((7.4, 22.0, 9.0), (8.8, 22.4, 20.0), 'galv', 'gangway')
    for z in (11.0, 14.0, 17.0, 20.0):
        m.box((7.4, 22.4, z - 0.15), (7.6, 24.6, z + 0.15), 'paint:ral1023', 'gangway rail post')
        m.box((8.6, 22.4, z - 0.15), (8.8, 24.6, z + 0.15), 'paint:ral1023', 'gangway rail post')
    m.box((7.4, 24.4, 11.0), (7.6, 24.6, 20.0), 'paint:ral1023', 'gangway rail')
    m.box((8.6, 24.4, 11.0), (8.8, 24.6, 20.0), 'paint:ral1023', 'gangway rail')
    m.cyl((8.1, 22.0, 9.4), 0.3, 6.0, 'y', 'metal:steel', 'gangway counterweight mast')
    m.g('loading arm')
    m.cyl((10.0, 18.0, 6.0), 0.9, 10.0, 'y', 'paint:#3a3f44', 'product riser')
    m.box((9.2, 26.0, 5.2), (10.8, 27.6, 6.8), 'paint:#3a3f44', 'swivel joint')
    m.box((10.0, 26.4, 5.6), (16.0, 27.2, 6.4), 'paint:#c8a020', 'arm inner')
    m.box((15.4, 18.0, 5.4), (16.4, 27.2, 6.6), 'paint:#c8a020', 'drop tube')
    m.cyl((15.9, 16.0, 6.0), 0.35, 2.0, 'y', 'metal:steel', 'drop tube nozzle')
    m.box((15.0, 22.0, 5.0), (16.8, 23.0, 7.0), 'paint:#5a5e62', 'vapour hood')
    m.box((9.0, 27.6, 5.6), (10.0, 30.0, 6.4), 'paint:#5a5e62', 'counterweight arm')
    m.box((8.4, 28.0, 4.8), (9.4, 30.0, 7.2), 'paint:#2a2a2a', 'counterweight')
    m.cyl((10.0, 18.0, 4.6), 0.5, 1.2, 'z', 'paint:#8a8e92', 'vapour return line')
    m.g('header + meter')
    pipe(m, (8.0, 3.0, 6.0), (14.0, 3.0, 6.0), 0.8, 'paint:#3a3f44', 'product header')
    m.box((11.0, 2.0, 5.0), (13.0, 4.4, 7.0), 'paint:#2a5a8a', 'flow meter')
    m.box((11.2, 4.4, 5.4), (12.8, 5.6, 6.6), 'paint:#3a3f44', 'meter register')
    m.cyl((14.0, 1.0, 6.0), 1.0, 2.4, 'x', 'paint:#2a5a8a', 'rack pump')
    m.box((13.6, 0, 5.0), (16.0, 1.0, 7.0), 'paint:#3a3f44', 'pump skid')
    valve(m, 9.0, 3.0, 6.0, 'x', '#c82020')
    m.cyl((10.0, 3.0, 6.0), 0.8, 15.0, 'y', 'paint:#3a3f44', 'header riser')
    m.g('ground + overfill')
    m.box((7.6, 6.0, 12.0), (9.4, 9.0, 13.2), 'paint:#f0c818', 'ground verification box')
    m.box((7.5, 7.0, 12.4), (7.6, 8.2, 12.8), 'lamp:30e060', 'ground ok lamp')
    m.cyl((9.4, 7.0, 12.6), 0.12, 6.0, 'x', 'cable:20a020', 'earthing lead')
    m.box((15.0, 7.0, 12.4), (15.6, 8.0, 13.0), 'paint:#c8a020', 'earth clamp')
    m.box((11.0, 20.0, 12.0), (12.2, 22.0, 13.0), 'paint:#2a2c30', 'overfill monitor')
    m.cyl((11.6, 14.0, 12.5), 0.12, 6.0, 'y', 'cable:202020', 'overfill probe cable')
    detector(m, 3.0, 26.0, 2.0)
    detector(m, 11.0, 10.0, 4.0)
    m.g('controller')
    m.box((0.6, 0, 0.0), (7.4, 14.0, 1.6), 'paint:#3a4a5a', 'batch controller')
    m.box((0.4, 14.0, -0.2), (7.6, 14.4, 1.8), 'paint:#2a3540', 'controller hood')
    m.box((7.6, 0, 0.0), (15.4, 9.0, 1.6), 'paint:#3a4a5a', 'rack control station')
    m.box((7.4, 9.0, -0.2), (15.6, 9.4, 1.8), 'paint:#2a3540', 'station hood')
    m.terminal_12v(3.0, 0.6, 1.6, 'south')
    mains_box(m, 15.4, 1.0, 0.8, 'east')
    f = m.surface('north', face=-0.02, title='BATCH CONTROLLER')
    display(m, 1.0, 7.0, 10.6, 13.4, 0.0, ['line1', 'line2', 'line3'], ['7cf29a', '8ad4ff', 'ffb020'], 'BATCH SCREEN')
    m.ctl(f, 'digits', 'litres', X(2.6), 9.4, w=3.0, h=1.0, col='ffd040', label='METERED', fmt='%s L')
    m.ctl(f, 'digits', 'preset', X(5.6), 9.4, w=2.6, h=1.0, col='8ad4ff', label='PRESET', fmt='%s')
    lab(m, f, X(2.6), 8.6, 'METERED L'); lab(m, f, X(5.6), 8.6, 'PRESET L')
    push_row(m, f, [('p_up', '505860', 'P +'), ('p_dn', '505860', 'P -'), ('p_full', '505860', 'FULL')], X(1.6), 7.2, step=1.9, size=0.7)
    gauges(m, f, [('carg', 'CAR %'), ('tankg', 'TANK'), ('lelg', 'LEL'), ('flowg', 'FLOW')], X(1.6), 4.6, step=1.6, size=1.2)
    push_row(m, f, [('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('test', 'b0b0b0', 'TEST')], X(1.8), 2.0, step=1.9, size=0.75)
    s = m.surface('north', face=-0.02, title='RACK STATION')
    lamp_row(m, s, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('runl', '30e060', 'PUMP'), ('perm', '30e060', 'PERMIT'), ('groundl', '30e060', 'GROUND')], X(8.6), 8.0, step=1.4, size=0.4)
    lamp_row(m, s, [('arml', 'ffb020', 'ARM'), ('carl', '8ad4ff', 'CAR'), ('esdl', 'ff3030', 'ESD', True), ('vrul', '30e060', 'VRU'), ('ofl', 'ff3030', 'OVERFILL'), ('gasl', 'ffb020', 'GAS')],
             X(8.6), 6.7, step=1.3, size=0.4)
    m.ctl(s, 'selector', 's_tank', X(9.0), 4.4, w=1.2, h=1.2, col='202020', label='FROM TANK', pos=['TANK 1', 'TANK 2', 'TANK 3', 'TANK 4', 'TANK 5', 'TANK 6'])
    m.ctl(s, 'key', 'k_mode', X(11.0), 4.4, w=0.9, h=0.9, col='c0a040', label='MODE', pos=['LOAD', 'UNLOAD'])
    m.ctl(s, 'key', 'k_vru', X(12.8), 4.4, w=0.9, h=0.9, col='c0a040', label='VAPOUR RECOVERY', pos=['OFF', 'ON'])
    for (x, t) in ((9.0, 'TANK'), (11.0, 'MODE'), (12.8, 'VRU')): lab(m, s, X(x), 3.4, t)
    m.ctl(s, 'lever', 'm_ground', X(9.4), 1.8, w=0.6, h=1.6, col='30a030', label='GROUND CLAMP', pos=['OFF', 'ON'])
    m.ctl(s, 'lever', 'm_arm', X(11.2), 1.8, w=0.6, h=1.6, col='e8c020', label='LOADING ARM', pos=['UP', 'DOWN'])
    lab(m, s, X(9.4), 0.6, 'GROUND'); lab(m, s, X(11.2), 0.6, 'ARM')
    estop_plate(m, s, X(14.0), 2.2)
    p = m.surface('west', face=-0.42, title='PPE LOCKER')
    m.box((-0.4, 1.0, 12.0), (0.4, 7.0, 15.0), 'paint:#2050d0', 'PPE locker')
    m.ctl(p, 'zone', 'ppe', 13.5, 4.0, w=3.0, h=6.0, col='000000', label='')
    return save(m)


def oil_firegas():
    """fire-water / foam house: fire-water tank, red foam concentrate tank + bladder proportioner, deluge valve set
    with its trim, monitor nozzle, the fire and gas panel with zone mimic, beacon + horn"""
    m = Model('oil_firegas')
    m.g('foam house')
    m.box((0, 0, 6.0), (16, 0.6, 16), 'concrete', 'house slab')
    m.box((0.4, 0.6, 9.0), (15.6, 18.0, 15.6), 'paint:#c82020', 'foam house')
    m.box((0.0, 18.0, 8.6), (16.0, 18.8, 16.0), 'paint:#3a3a3a', 'house roof')
    m.box((1.0, 14.0, 8.9), (15.0, 16.4, 9.0), 'label:FIRE + FOAM|STATION', 'house lettering')
    m.cyl((3.0, 18.8, 12.0), 2.4, 10.0, 'y', 'paint:#e8e8e0', 'fire water tank')
    m.cyl((3.0, 28.8, 12.0), 2.6, 0.4, 'y', 'paint:#c8c8c0', 'water tank roof')
    m.box((0.4, 22.0, 9.4), (1.6, 26.0, 9.6), 'label:WATER', 'water tank label')
    m.cyl((11.0, 18.8, 12.0), 1.8, 6.0, 'y', 'paint:#c82020', 'foam concentrate tank')
    m.box((9.4, 21.0, 10.0), (12.6, 23.0, 10.2), 'label:AFFF 3%', 'foam label')
    m.g('deluge valve set')
    m.cyl((13.0, 0.6, 7.6), 0.9, 10.0, 'y', 'paint:#c82020', 'deluge riser')
    m.box((12.0, 5.0, 6.6), (14.0, 8.0, 8.6), 'paint:#c82020', 'deluge valve')
    m.cyl((14.0, 6.5, 7.6), 0.4, 1.4, 'x', 'paint:#c8a020', 'manual release')
    m.box((12.2, 8.6, 6.4), (13.8, 9.4, 7.0), 'gauge:bar', 'trim gauge')
    pipe(m, (13.0, 10.6, 7.6), (13.0, 10.6, 16.0), 0.7, 'paint:#c82020', 'deluge main')
    m.g('monitor')
    m.cyl((15.0, 18.8, 7.0), 0.5, 4.0, 'y', 'paint:#c82020', 'monitor riser')
    m.box((14.4, 22.8, 6.0), (15.6, 23.8, 7.6), 'paint:#c82020', 'monitor head')
    m.cyl((15.0, 23.3, 3.6), 0.35, 2.4, 'z', 'metal:brass', 'monitor nozzle')
    m.g('hydrant + hose reel')
    m.cyl((15.0, 0.6, 2.0), 0.6, 4.0, 'y', 'paint:#c82020', 'fire hydrant')
    m.cyl((15.0, 4.6, 2.0), 0.7, 0.4, 'y', 'paint:#c82020', 'hydrant cap')
    for zz in (1.4, 2.6):
        m.cyl((14.4, 3.0, zz), 0.25, 0.6, 'x', 'metal:brass', 'hydrant outlet')
    m.box((12.0, 4.0, 4.0), (15.6, 9.0, 5.6), 'paint:#c82020', 'hose reel cabinet')
    m.box((12.2, 4.2, 3.95), (15.4, 8.8, 4.0), 'glass', 'cabinet glass')
    m.cyl((13.8, 5.0, 4.8), 1.6, 0.6, 'z', 'paint:#e8c020', 'hose reel')
    m.box((12.2, 8.0, 3.9), (15.4, 8.8, 3.95), 'label:HOSE REEL', 'reel label')
    m.g('house details')
    m.box((5.0, 0.6, 8.9), (9.0, 9.0, 9.0), 'paint:#8a1010', 'house door')
    m.box((8.4, 4.4, 8.85), (8.7, 5.2, 8.9), 'metal:steel', 'door handle')
    m.louvres(1.0, 4.0, 10.0, 13.0, 9.0, 4)
    m.louvres(10.0, 15.0, 10.0, 13.0, 9.0, 4)
    pipe(m, (3.0, 18.8, 9.6), (3.0, 10.6, 9.6), 0.5, 'paint:#c82020', 'water tank downcomer')
    pipe(m, (11.0, 18.8, 10.0), (11.0, 12.0, 10.0), 0.3, 'paint:#c82020', 'foam line')
    m.box((10.4, 11.0, 9.4), (11.6, 12.6, 10.4), 'paint:#3a3f44', 'proportioner')
    for x in (0.8, 15.2):
        m.cyl((x, 18.8, 15.2), 0.15, 2.0, 'y', 'galv', 'roof rail post')
    beacon(m, 8.0, 18.8, 10.0, 'ff2020')
    m.cyl((6.0, 18.8, 10.0), 0.8, 1.2, 'y', 'paint:#3a3a3a', 'horn')
    m.g('panel')
    m.box((0.4, 0, 0.6), (11.6, 14.0, 6.0), 'paint:#c82020', 'fire and gas panel')
    m.box((0.2, 14.0, 0.4), (11.8, 14.6, 6.2), 'paint:#8a1010', 'panel hood')
    m.box((0.6, 12.6, 0.5), (11.4, 13.8, 0.6), 'label:FIRE + GAS PANEL', 'panel title')
    m.terminal_12v(4.0, 0.6, 6.0, 'south')
    mains_box(m, 11.6, 2.0, 3.0, 'east')
    f = m.surface('north', face=0.58, title='FIRE + GAS')
    display(m, 0.9, 6.6, 9.6, 12.2, 0.6, ['line1', 'line2', 'line3'], ['ff6060', '7cf29a', 'ffb020'], 'F+G SCREEN')
    m.ctl(f, 'digits', 'pageno', X(6.0), 9.2, w=1.0, h=0.4, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'lel', X(8.4), 11.6, w=2.6, h=1.0, col='ffd040', label='GAS', fmt='%s %%LEL')
    gauges(m, f, [('lelg', 'LEL'), ('waterg', 'WATER'), ('foamg', 'FOAM')], X(7.6), 9.6, step=1.6, size=1.2)
    m.box((0.9, 5.4, 0.5), (6.6, 8.6, 0.6), 'paint:#20262c', 'zone mimic')
    zt = m.surface('north', face=0.48, title='ZONE MIMIC')
    for i in range(6):
        m.ctl(zt, 'lamp', 'z%d' % i, X(1.6 + i * 0.9), 7.8, w=0.4, h=0.4, col='ff3030', label='TANK %d FIRE' % (i + 1), blink=True)
        m.ctl(zt, 'tile', 'r%d' % i, X(1.6 + i * 0.9), 6.2, w=0.7, h=0.6, col='ffb020', label='RACK %d GAS' % (i + 1))
    lab(m, zt, X(3.8), 8.5, 'TANK ZONES'); lab(m, zt, X(3.8), 5.6, 'RACK GAS')
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('fire', 'ff3030', 'FIRE', True), ('gaslo', 'ffb020', 'GAS LO'), ('gashi', 'ff3030', 'GAS HI', True), ('esdl', 'ff3030', 'ESD')],
             X(7.2), 7.4, step=0.75, size=0.32, label_dv=-0.4)
    lamp_row(m, f, [('delugel', '4080ff', 'DELUGE'), ('autol', '30e060', 'AUTO'), ('beacon', 'ff3030', 'ALARM', True), ('silencel', 'b0b0b0', 'SIL'), ('foaml', 'ffb020', 'FOAM LO'), ('waterl', 'ffb020', 'WATER LO')],
             X(7.2), 6.2, step=0.75, size=0.32, label_dv=-0.4)
    push_row(m, f, [('deluge', 'd02020', 'DELUGE'), ('dstop', '505860', 'SHUT'), ('esd', 'd02020', 'ESD'), ('silence', '2050d0', 'SILENCE')], X(1.4), 3.6, step=1.6, size=0.75)
    push_row(m, f, [('reset', '1fa34a', 'RESET'), ('page', '2050d0', 'PAGE'), ('fill', '505860', 'FILL'), ('test', 'b0b0b0', 'TEST')], X(1.4), 1.8, step=1.6, size=0.7)
    m.ctl(f, 'key', 'k_auto', X(9.6), 3.0, w=0.9, h=0.9, col='c0a040', label='DELUGE MODE', pos=['MAN', 'AUTO'])
    lab(m, f, X(9.6), 2.2, 'AUTO')
    d = m.surface('north', face=6.98, title='FOAM DRUMS')
    m.box((4.0, 0.6, 7.0), (8.0, 4.0, 9.0), 'paint:#c82020', 'foam drum store')
    for x in (4.8, 6.2, 7.4):
        m.cyl((x, 4.0, 8.0), 0.5, 1.4, 'y', 'paint:#e0c020', 'AFFF drum')
    m.ctl(d, 'zone', 'foam', X(6.0), 2.4, w=4.0, h=3.4, col='000000', label='')
    return save(m)


# ===================================================================================================== #28 warehouse
def warehouse_dock():
    """one dock bay of the warehouse wall: steel frame + cladding round the opening (x 2..14, y 0..28), door guides
    and the drum housing with motor, dock shelter pads, traffic lights in and out, dock lamp on its swing arm, the
    leveller pit and the door control station (the curtain and the lip are drawn live)"""
    m = Model('warehouse_dock')
    m.g('wall')
    m.box((0, 0, 7.0), (2.0, 32.0, 9.0), 'paint:#8a9aa8', 'wall pier')
    m.box((14.0, 0, 7.0), (16.0, 32.0, 9.0), 'paint:#8a9aa8', 'wall pier')
    m.box((2.0, 28.0, 7.0), (14.0, 32.0, 9.0), 'paint:#8a9aa8', 'wall over door')
    for x in (0.4, 1.2, 14.4, 15.2):
        m.box((x, 0, 6.9), (x + 0.3, 32.0, 7.0), 'paint:#7a8a98', 'cladding rib')
    m.box((2.0, 28.0, 6.9), (14.0, 29.0, 7.0), 'label:DOCK', 'dock lettering')
    for y in range(2, 30, 4):
        m.box((0.0, y, 6.95), (2.0, y + 0.15, 7.0), 'paint:#6a7a88', 'cladding seam')
        m.box((14.0, y, 6.95), (16.0, y + 0.15, 7.0), 'paint:#6a7a88', 'cladding seam')
    m.box((0.2, 22.0, 6.85), (1.8, 26.0, 6.95), 'warn', 'door warning sign')
    m.box((14.2, 10.0, 6.85), (15.8, 13.0, 6.95), 'label:NO|ENTRY', 'no entry sign')
    m.cyl((1.0, 0, 6.6), 0.15, 28.0, 'y', 'galv', 'conduit')
    m.cyl((15.0, 0, 6.6), 0.15, 28.0, 'y', 'galv', 'conduit')
    for x in (0.6, 15.4):
        m.cyl((x, 0, 3.0), 0.6, 6.0, 'y', 'paint:#f0c818', 'bollard')
        m.cyl((x, 4.0, 3.0), 0.65, 0.6, 'y', 'paint:#202020', 'bollard band')
    m.g('door guides + drum')
    for x in (1.6, 14.0):
        m.box((x, 0, 7.4), (x + 0.4, 28.0, 8.2), 'galv', 'door guide')
    m.box((1.6, 28.0, 6.0), (14.4, 31.0, 7.0), 'paint:#5a5e62', 'drum housing')
    for x in (2.0, 13.6):
        m.box((x, 0, 7.2), (x + 0.4, 28.0, 7.4), 'paint:#1a1a1a', 'brush seal')
    m.box((2.0, 27.6, 7.2), (14.0, 28.0, 8.2), 'paint:#1a1a1a', 'head seal')
    m.box((14.4, 28.4, 6.0), (16.0, 30.8, 7.0), 'paint:#2a5a8a', 'door motor')
    m.cyl((15.2, 27.0, 6.5), 0.2, 1.4, 'y', 'paint:#c82020', 'hand chain')
    m.g('shelter + leveller')
    for x in (2.0, 13.0):
        m.box((x, 0, 9.0), (x + 1.0, 28.0, 11.0), 'paint:#1a1a1a', 'shelter pad')
    m.box((2.0, 27.0, 9.0), (14.0, 28.0, 11.0), 'paint:#1a1a1a', 'shelter head pad')
    for y in (4.0, 12.0, 20.0):
        m.box((2.0, y, 10.9), (3.0, y + 0.6, 11.0), 'haz', 'pad stripe')
        m.box((13.0, y, 10.9), (14.0, y + 0.6, 11.0), 'haz', 'pad stripe')
    m.box((2.0, 0, 9.0), (14.0, 2.0, 16.0), 'concrete', 'dock edge')
    m.box((2.0, 1.6, 15.6), (14.0, 2.0, 16.0), 'haz', 'dock edge nosing')
    for x in (2.4, 13.0):
        m.box((x, 0.2, 15.9), (x + 0.6, 1.8, 16.6), 'rubber', 'dock bumper')
    m.box((3.0, 1.6, 9.0), (13.0, 2.0, 15.6), 'paint:#5a5e62', 'leveller deck')
    m.g('lights')
    m.box((14.6, 18.0, 11.0), (15.8, 22.0, 12.2), 'paint:#2a2a2a', 'outside traffic light')
    m.cyl((15.2, 22.0, 11.6), 0.15, 2.0, 'y', 'paint:#2a2a2a', 'lamp arm post')
    m.box((15.2, 24.0, 9.0), (15.4, 24.2, 15.0), 'paint:#2a2a2a', 'dock lamp arm')
    m.box((14.8, 23.2, 14.4), (15.8, 24.0, 15.6), 'paint:#c8a020', 'dock lamp')
    m.box((14.9, 23.1, 14.5), (15.7, 23.2, 15.5), 'lamp:fff0c0', 'dock lamp lens')
    m.g('control station')
    m.box((0.4, 4.0, 4.8), (5.0, 14.0, 6.8), 'paint:ral7035', 'door control station')
    m.box((0.2, 14.0, 4.6), (5.2, 14.4, 7.0), 'paint:#8a8e92', 'station hood')
    m.box((10.6, 18.0, 6.0), (12.4, 22.0, 7.0), 'paint:#2a2a2a', 'inside traffic light')
    m.terminal_12v(2.6, 1.0, 6.8, 'north')
    mains_box(m, 0.0, 14.6, 6.0, 'east')
    tl = m.surface('north', face=5.98, title='INSIDE LIGHTS')
    m.ctl(tl, 'lamp', 'red', X(11.5), 21.0, w=0.8, h=0.8, col='ff3030', label='RED - KEEP OUT')
    m.ctl(tl, 'lamp', 'green', X(11.5), 19.0, w=0.8, h=0.8, col='30e060', label='GREEN - SECURED')
    f = m.surface('north', face=4.78, title='DOOR STATION')
    m.ctl(f, 'digits', 'dockno', X(1.6), 13.2, w=1.4, h=1.0, col='ffd040', label='DOCK', fmt='D%s')
    display(m, 2.6, 4.8, 12.2, 13.8, 4.8, ['line1', 'line2'], ['7cf29a', '8ad4ff'], 'DOCK SCREEN')
    m.ctl(f, 'gauge', 'carg', X(1.6), 11.0, w=1.1, h=1.1, col='f2f0e6', label='CAR LOAD', max=100)
    m.ctl(f, 'gauge', 'doorg', X(3.4), 11.0, w=1.1, h=1.1, col='f2f0e6', label='DOOR', max=100)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('openl', '30e060', 'OPEN'), ('shutl', 'ffb020', 'SHUT')], X(1.0), 9.4, step=1.1, size=0.35, label_dv=-0.45)
    lamp_row(m, f, [('movel', 'ffb020', 'MOVE', True), ('levl', '8ad4ff', 'LIP'), ('derl', '4080ff', 'DERAIL'), ('carl', '8ad4ff', 'CAR')], X(1.0), 8.2, step=1.1, size=0.35, label_dv=-0.45)
    push_row(m, f, [('open', '1fa34a', 'OPEN'), ('dstop', 'b0b0b0', 'STOP'), ('close', 'd02020', 'CLOSE')], X(1.2), 6.6, step=1.4, size=0.65, label_dv=-0.6)
    m.ctl(f, 'key', 'k_derail', X(1.2), 4.8, w=0.7, h=0.7, col='4080ff', label='DERAIL / BLUE FLAG', pos=['OFF', 'ON'])
    m.ctl(f, 'lever', 'm_lev', X(2.6), 4.8, w=0.5, h=1.2, col='e8c020', label='LEVELLER', pos=['STORED', 'DOWN'])
    estop_plate(m, f, X(4.0), 4.8)
    m.ctl(f, 'push', 'test', X(4.5), 11.0, w=0.4, h=0.4, col='b0b0b0', label='TEST')
    b = m.surface('north', face=-0.02, title='DOCK BOX')
    m.box((10.0, 0.0, 0.0), (14.0, 3.0, 2.0), 'paint:#f0c818', 'dock box')
    m.box((10.0, 2.0, -0.05), (14.0, 2.8, 0.0), 'label:CHOCKS', 'box label')
    m.ctl(b, 'zone', 'chocks', X(12.0), 1.5, w=4.0, h=3.0, col='000000', label='')
    return save(m, 0.35)


def warehouse_rack():
    """selective pallet racking, two blocks high: blue upright frames with footplates and column guards, orange
    beams at three levels, wire-mesh decks, beam locks, location labels, aisle light and the rack's load sign"""
    m = Model('warehouse_rack')
    m.g('uprights')
    for x in (0.2, 15.0):
        for z in (1.0, 14.0):
            m.box((x, 0, z), (x + 0.8, 31.0, z + 0.8), 'paint:#2a52a8', 'upright')
            m.box((x - 0.2, 0, z - 0.2), (x + 1.0, 0.2, z + 1.0), 'galv', 'footplate')
        for y in range(2, 30, 5):
            m.box((x + 0.2, y, 1.8), (x + 0.6, y + 0.3, 14.0), 'paint:#2a52a8', 'frame brace')
        m.box((x - 0.3, 0, 0.4), (x + 1.1, 3.0, 1.0), 'paint:#f0c818', 'column guard')
    m.g('beams + decks')
    for k, y in enumerate((0.4, 10.4, 20.4)):
        for z in (1.0, 14.2):
            m.box((1.0, y, z), (15.0, y + 0.6, z + 0.6), 'paint:#e06a10', 'beam')
            m.box((1.0, y + 0.2, z - 0.05), (1.6, y + 0.5, z), 'metal:steel', 'beam lock')
            m.box((14.4, y + 0.2, z - 0.05), (15.0, y + 0.5, z), 'metal:steel', 'beam lock')
        m.box((1.0, y + 0.55, 1.6), (15.0, y + 0.6, 14.2), 'mesh', 'mesh deck')
        for x in (4.0, 8.0, 12.0):
            m.box((x - 0.15, y + 0.4, 1.6), (x + 0.15, y + 0.55, 14.2), 'galv', 'deck support')
        m.box((6.0, y - 0.6 if y > 1 else y + 0.6, 0.9), (10.0, (y - 0.0) if y > 1 else y + 1.4, 1.0), 'label:A-%d' % (k + 1), 'location label')
    m.box((1.0, 30.4, 1.0), (15.0, 31.0, 1.6), 'paint:#e06a10', 'top tie')
    for y in (0.4, 10.4, 20.4):
        m.box((1.0, y + 0.6, 14.4), (15.0, y + 1.6, 14.8), 'paint:#2a52a8', 'pallet stop')
        for x in (2.0, 14.0):
            m.box((x - 0.3, y - 1.0, 1.1), (x + 0.3, y, 1.5), 'metal:steel', 'beam connector')
    for x in (0.2, 15.0):
        for y in range(4, 30, 5):
            m.box((x + 0.2, y, 7.0), (x + 0.6, y + 2.2, 7.4), 'paint:#2a52a8', 'diagonal brace')
    m.box((-0.4, 0, 0.2), (16.4, 1.2, 0.6), 'paint:#f0c818', 'row end protector')
    m.box((-0.4, 0, 0.2), (16.4, 0.4, 0.6), 'haz', 'protector stripes')
    m.g('signs + light')
    m.box((5.0, 28.0, 0.8), (11.0, 30.0, 1.0), 'plate:MAX 1000 KG|PER LEVEL', 'load sign')
    m.box((6.0, 31.0, 6.0), (10.0, 31.6, 10.0), 'paint:#2a2c30', 'aisle light')
    m.terminal_12v(12.0, 26.0, 1.0, 'north')
    f = m.surface('north', face=0.78, title='RACK')
    m.ctl(f, 'digits', 'loc', X(3.0), 29.0, w=2.6, h=1.0, col='ffd040', label='LOCATION', fmt='%s')
    m.ctl(f, 'gauge', 'fillg', X(13.0), 29.2, w=1.2, h=1.2, col='f2f0e6', label='SLOTS USED', max=100)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('full', 'ff3030', 'FULL'), ('lamp', 'fff0c0', 'LIGHT')], X(5.6), 26.8, step=1.0, size=0.35, label_dv=-0.45)
    m.ctl(f, 'push', 'test', X(9.2), 26.8, w=0.5, h=0.5, col='b0b0b0', label='TEST')
    for k, (y0, y1) in enumerate(((1.0, 10.0), (11.0, 20.0), (21.0, 28.0))):
        m.ctl(f, 'zone', 'bay%d' % (k + 1), X(8.0), (y0 + y1) / 2, w=13.0, h=y1 - y0, col='000000', label='')
    return save(m, 0.3)


def warehouse_office():
    """the warehouse management desk: L-shaped desk, WMS terminal + paged screen, label printer, radio base with
    mast, shift board with forklift tags, pick-list tray and the label-stock cupboard"""
    m = Model('warehouse_office')
    m.g('desk')
    m.box((0.4, 0, 1.0), (15.6, 9.0, 8.0), 'paint:#4a5560', 'WMS desk')
    m.box((0.2, 9.0, 0.8), (15.8, 9.6, 8.2), 'paint:#c8b88a', 'desk top')
    m.box((0.6, 0, 0.8), (15.4, 0.8, 1.0), 'paint:#1c1d20', 'kick plate')
    m.box((0.6, 9.6, 6.0), (15.4, 18.0, 8.0), 'paint:#3a4550', 'desk upstand')
    m.box((0.4, 18.0, 5.8), (15.6, 18.6, 8.2), 'paint:#2a3540', 'upstand top')
    m.g('terminal + printer')
    m.box((1.0, 9.6, 3.0), (6.0, 10.0, 6.0), 'paint:#2a2c30', 'keyboard')
    for i in range(4):
        m.box((1.2 + i * 1.2, 10.0, 3.2), (2.2 + i * 1.2, 10.15, 5.8), 'paint:#3a3c40', 'key row')
    m.box((11.0, 9.6, 3.0), (15.0, 12.0, 6.0), 'paint:#e8e8e0', 'label printer')
    m.box((11.4, 11.0, 2.9), (14.6, 11.4, 3.0), 'paint:#2a2c30', 'printer slot')
    m.box((11.6, 11.4, 2.6), (14.4, 11.5, 3.0), 'white', 'printed label')
    m.g('radio base')
    m.box((7.0, 9.6, 3.6), (10.0, 11.4, 6.0), 'paint:#2a2c30', 'radio base station')
    m.cyl((9.4, 11.4, 5.4), 0.1, 3.0, 'y', 'metal:steel', 'radio antenna')
    m.box((7.4, 11.4, 3.8), (8.4, 12.6, 4.4), 'paint:#3a3c40', 'handset')
    m.g('monitor + phone')
    m.box((1.2, 10.0, 6.4), (8.8, 10.4, 7.2), 'paint:#1c1e20', 'monitor foot')
    m.box((4.6, 10.4, 6.6), (5.4, 12.6, 7.0), 'paint:#1c1e20', 'monitor neck')
    m.box((12.6, 12.0, 3.4), (14.6, 12.4, 4.6), 'paint:#2a2c30', 'label roll')
    m.box((10.2, 9.6, 1.4), (11.0, 10.6, 2.2), 'white', 'coffee mug')
    m.box((10.95, 9.9, 1.6), (11.2, 10.3, 2.0), 'white', 'mug handle')
    m.box((4.4, 9.6, 1.2), (6.4, 9.8, 2.8), 'white', 'paperwork')
    m.box((4.6, 9.8, 1.3), (6.2, 9.9, 2.6), 'white', 'paperwork')
    m.cyl((14.6, 9.6, 6.8), 0.4, 0.3, 'y', 'paint:#2a2c30', 'desk lamp base')
    m.cyl((14.6, 9.9, 6.8), 0.08, 3.0, 'y', 'metal:steel', 'desk lamp arm')
    m.box((13.6, 12.6, 5.8), (15.0, 13.4, 7.0), 'paint:#2a2c30', 'desk lamp head')
    m.g('pc + clock')
    m.box((9.0, 0.8, 2.0), (11.0, 7.0, 7.0), 'paint:#1c1e20', 'WMS server PC')
    m.box((9.2, 5.6, 1.95), (10.8, 6.2, 2.0), 'lamp:30e060', 'PC power led')
    for y in (1.6, 2.4, 3.2):
        m.box((9.3, y, 1.95), (10.7, y + 0.3, 2.0), 'louvre', 'PC vent')
    m.cyl((12.0, 28.4, 7.6), 1.2, 0.3, 'z', 'white', 'wall clock')
    m.box((11.95, 28.4, 7.5), (12.05, 29.4, 7.6), 'paint:#1a1a1a', 'clock hand')
    m.box((12.0, 28.35, 7.5), (12.8, 28.45, 7.6), 'paint:#1a1a1a', 'clock hand')
    for i in range(3):
        m.box((1.0 + i * 2.2, 19.6, 7.25), (2.8 + i * 2.2, 22.4, 7.3), 'label:FLT%d' % (i + 1), 'driver card')
    m.box((2.0, 9.6, 6.2), (3.0, 11.0, 7.0), 'paint:#2a2c30', 'phone')
    m.g('chair + bin')
    m.cyl((8.0, 0, -3.0), 1.6, 0.4, 'y', 'paint:#2a2c30', 'chair base')
    m.cyl((8.0, 0.4, -3.0), 0.25, 4.0, 'y', 'metal:steel', 'chair column')
    m.box((6.0, 4.4, -4.6), (10.0, 5.4, -1.4), 'paint:#2a3a5a', 'chair seat')
    m.box((6.0, 5.4, -5.0), (10.0, 10.0, -4.4), 'paint:#2a3a5a', 'chair back')
    m.cyl((14.0, 0, -1.0), 0.9, 2.6, 'y', 'paint:#4a5560', 'waste bin')
    m.g('filing')
    m.box((0.6, 0.8, 0.8), (4.0, 8.2, 1.0), 'paint:#5a6570', 'filing drawers')
    for y in (2.6, 5.0, 7.4):
        m.box((1.8, y, 0.7), (2.8, y + 0.4, 0.8), 'metal:steel', 'drawer pull')
    m.g('shift board')
    m.box((0.6, 18.6, 7.4), (15.4, 28.0, 8.0), 'paint:#e8e8e0', 'shift whiteboard')
    m.box((0.4, 18.4, 7.3), (15.6, 28.2, 7.4), 'paint:#8a8e92', 'board frame')
    m.box((1.0, 26.6, 7.25), (8.0, 27.6, 7.3), 'label:FORKLIFTS', 'board heading')
    for i in range(3):
        m.box((1.2 + i * 2.2, 23.0, 7.2), (2.8 + i * 2.2, 25.6, 7.3), 'paint:#f0c818', 'forklift tag')
    m.box((9.0, 20.0, 7.25), (15.0, 26.0, 7.3), 'label:SAFETY|FIRST', 'safety poster')
    m.g('trays + cupboard')
    m.box((1.0, 9.6, 1.2), (4.0, 10.4, 2.8), 'paint:#2050d0', 'pick list tray')
    m.box((1.2, 10.0, 1.15), (3.8, 10.4, 1.2), 'label:PICK', 'tray label')
    m.box((12.0, 0.8, 0.8), (15.4, 8.2, 1.0), 'paint:#5a6570', 'label stock cupboard')
    m.box((12.2, 4.0, 0.7), (12.6, 5.0, 0.8), 'metal:steel', 'cupboard handle')
    m.terminal_12v(8.0, 1.0, 8.0, 'south')
    f = m.surface('north', face=5.98, title='WMS')
    display(m, 1.0, 9.0, 14.0, 17.4, 6.0, ['line1', 'line2', 'line3'], ['7cf29a', '8ad4ff', 'ffb020'], 'WMS SCREEN')
    m.ctl(f, 'digits', 'pageno', X(8.2), 13.6, w=1.2, h=0.5, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'moved', X(11.8), 16.6, w=3.6, h=1.0, col='ffd040', label='MOVED', fmt='%s ITEMS')
    m.ctl(f, 'gauge', 'rackg', X(11.0), 14.6, w=1.4, h=1.4, col='f2f0e6', label='RACKS FULL', max=100)
    for i in range(4):
        m.ctl(f, 'lamp', 'd%d' % i, X(12.6 + i * 0.8), 14.8, w=0.35, h=0.35, col='30e060', label='DOCK %d SECURED' % (i + 1))
        lab(m, f, X(12.6 + i * 0.8), 14.2, 'D%d' % (i + 1))
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('jobl', '30e060', 'JOB'), ('unl', '8ad4ff', 'UNLOAD'), ('ldl', 'ffb020', 'LOAD'), ('autol', '8ad4ff', 'AUTO'), ('fulll', 'ff3030', 'FULL')],
             X(1.4), 12.4, step=1.3, size=0.38, label_dv=-0.45)
    lamp_row(m, f, [('w1', 'ffb020', 'FLT1'), ('w2', 'ffb020', 'FLT2'), ('w3', 'ffb020', 'FLT3')], X(9.4), 12.4, step=1.3, size=0.38, label_dv=-0.45)
    d = m.surface('north', face=0.98, title='WMS DESK')
    push_row(m, d, [('unload', '2050d0', 'UNLOAD'), ('load', 'e08020', 'LOAD'), ('stop', 'd02020', 'STOP'), ('page', '505860', 'PAGE'), ('print', '505860', 'PRINT'), ('test', 'b0b0b0', 'TEST')],
             X(1.6), 7.4, step=1.6, size=0.75)
    m.ctl(d, 'selector', 's_dock', X(2.0), 4.4, w=1.2, h=1.2, col='202020', label='DOCK', pos=['DOCK 1', 'DOCK 2', 'DOCK 3', 'DOCK 4'])
    m.ctl(d, 'selector', 's_workers', X(4.6), 4.4, w=1.2, h=1.2, col='202020', label='WORKERS', pos=['1', '2', '3'])
    m.ctl(d, 'key', 'k_auto', X(7.0), 4.4, w=0.9, h=0.9, col='c0a040', label='AUTO UNLOAD', pos=['MAN', 'AUTO'])
    for (x, t) in ((2.0, 'DOCK'), (4.6, 'DRIVERS'), (7.0, 'AUTO')): lab(m, d, X(x), 3.3, t)
    m.ctl(d, 'zone', 'labels', X(13.7), 4.5, w=3.4, h=7.4, col='000000', label='')
    t = m.surface('up', face=16 - 10.42, title='PICK TRAY')
    m.ctl(t, 'zone', 'picklist', X(2.5), 2.0, w=3.0, h=1.6, col='000000', label='')
    return save(m)


def forklift():
    """a 2.5 t counterbalance forklift (entity model, one block wide, forks north - drawn live): chassis, counterweight,
    overhead guard, mast channels and tilt rams, LPG bottle, driver in the seat"""
    m = Model('forklift')
    m.g('chassis')
    m.box((2.6, 1.0, 3.0), (13.4, 6.0, 22.0), 'paint:#f0b818', 'chassis')
    m.box((2.4, 1.0, 18.0), (13.6, 8.0, 24.0), 'paint:#3a3a3a', 'counterweight')
    m.box((2.6, 6.0, 9.0), (13.4, 7.0, 18.0), 'paint:#2a2a2a', 'floor plate')
    m.box((3.0, 6.0, 3.0), (13.0, 9.0, 6.0), 'paint:#f0b818', 'dash cowl')
    m.box((6.0, 9.0, 4.4), (10.0, 9.4, 5.6), 'paint:#2a2a2a', 'steering wheel')
    m.cyl((8.0, 7.0, 5.0), 0.2, 2.2, 'y', 'paint:#2a2a2a', 'steering column')
    for (x, z) in ((2.0, 5.5), (14.0, 5.5), (2.0, 19.5), (14.0, 19.5)):
        m.cyl((x - 1.0 if x < 8 else x, 2.0, z), 2.0, 1.0, 'x', 'rubber', 'wheel')
        m.cyl((x - 1.1 if x < 8 else x + 0.1, 2.0, z), 0.9, 1.0, 'x', 'paint:#c8c8c8', 'wheel rim')
    m.g('seat + driver')
    m.box((5.0, 7.0, 13.0), (11.0, 9.0, 18.0), 'paint:#2a2a2a', 'seat')
    m.box((5.0, 9.0, 17.0), (11.0, 14.0, 18.0), 'paint:#2a2a2a', 'seat back')
    m.box((6.0, 9.0, 12.0), (10.0, 17.0, 16.0), 'paint:#f07818', 'driver hi-vis')
    m.box((6.6, 17.0, 12.6), (9.4, 20.0, 15.4), 'paint:#d8a888', 'driver head')
    m.box((6.4, 19.4, 12.4), (9.6, 21.0, 15.6), 'paint:#f0f0f0', 'hard hat')
    m.box((6.0, 9.0, 8.0), (7.4, 10.6, 13.0), 'paint:#2a3a5a', 'leg')
    m.box((8.6, 9.0, 8.0), (10.0, 10.6, 13.0), 'paint:#2a3a5a', 'leg')
    m.box((5.0, 13.0, 9.0), (6.0, 15.0, 13.0), 'paint:#f07818', 'arm')
    m.box((10.0, 13.0, 9.0), (11.0, 15.0, 13.0), 'paint:#f07818', 'arm')
    m.g('overhead guard')
    for (x, z) in ((2.8, 6.0), (12.6, 6.0), (2.8, 19.0), (12.6, 19.0)):
        m.box((x, 7.0, z), (x + 0.6, 26.0, z + 0.6), 'paint:#2a2a2a', 'guard post')
    m.box((2.6, 26.0, 5.8), (13.4, 26.6, 19.8), 'paint:#2a2a2a', 'guard roof frame')
    for z in range(7, 19, 2):
        m.box((2.8, 26.6, z), (13.2, 26.9, z + 0.4), 'paint:#3a3a3a', 'guard slat')
    m.box((2.6, 24.0, 19.8), (3.4, 25.0, 20.4), 'lamp:ff8a00', 'warning beacon')
    m.g('mast')
    for x in (3.4, 11.8):
        m.box((x, 1.0, 1.0), (x + 0.8, 30.0, 2.4), 'paint:#3a3a3a', 'outer mast channel')
        m.box((x + 0.8, 1.0, 1.2), (x + 1.4, 29.0, 2.2), 'paint:#4a4a4a', 'inner mast channel')
        m.cyl((x + 0.4, 6.0, 3.0), 0.35, 4.0, 'z', 'metal:steel', 'tilt ram')
    m.box((3.4, 29.0, 1.0), (13.0, 30.0, 2.4), 'paint:#3a3a3a', 'mast head')
    m.cyl((8.0, 2.0, 2.0), 0.6, 20.0, 'y', 'metal:steel', 'lift cylinder')
    for x in (5.6, 10.0):
        m.cyl((x, 2.0, 1.6), 0.08, 27.0, 'y', 'paint:#2a2a2a', 'lift chain')
    m.g('details')
    for x in (3.0, 12.4):
        m.box((x, 7.0, 4.0), (x + 0.6, 7.4, 9.0), 'paint:#2a2a2a', 'grab handle')
        m.box((x - 0.4, 3.0, 9.0), (x + 1.0, 3.4, 11.0), 'paint:#3a3a3a', 'step')
    m.box((2.2, 22.0, 5.0), (2.6, 23.4, 6.4), 'glass', 'mirror')
    m.cyl((6.0, 6.0, 2.6), 0.15, 8.0, 'y', 'cable:1a1a1a', 'hydraulic hose')
    m.cyl((10.0, 6.0, 2.6), 0.15, 8.0, 'y', 'cable:1a1a1a', 'hydraulic hose')
    m.box((3.6, 9.0, 4.0), (5.0, 10.2, 5.0), 'paint:#1a1a1a', 'lift lever')
    m.box((5.0, 9.0, 4.0), (6.0, 10.6, 5.0), 'paint:#1a1a1a', 'tilt lever')
    m.box((5.4, 13.0, 17.2), (10.6, 13.4, 17.4), 'paint:#c82020', 'seat belt')
    m.box((12.8, 8.0, 21.0), (13.5, 10.0, 22.0), 'plate:IRX', 'data plate')
    m.box((3.0, 1.0, 22.0), (13.0, 2.0, 24.2), 'paint:#2a2a2a', 'tow pin housing')
    m.g('lpg + lights')
    m.cyl((8.0, 9.0, 22.4), 1.6, 5.0, 'x', 'paint:#f0f0f0', 'LPG bottle')
    m.box((3.0, 6.0, 2.8), (4.4, 7.2, 3.0), 'lamp:fff0c0', 'headlight')
    m.box((11.6, 6.0, 2.8), (13.0, 7.2, 3.0), 'lamp:fff0c0', 'headlight')
    m.box((3.0, 5.0, 23.9), (4.4, 6.2, 24.0), 'lamp:ff2020', 'tail light')
    m.box((11.6, 5.0, 23.9), (13.0, 6.2, 24.0), 'lamp:ff2020', 'tail light')
    m.box((3.0, 3.0, 2.95), (13.0, 4.6, 3.0), 'plate:2.5 T', 'capacity plate')
    return save(m)


# ===================================================================================================== #29 parcel lockers
def locker_door(m, f, i, x0, x1, y0, y1):
    m.box((x0, y0, 1.6), (x1, y1, 1.8), 'paint:#d8dce0', 'compartment door')
    m.box((x1 - 0.5, (y0 + y1) / 2 - 0.4, 1.45), (x1 - 0.3, (y0 + y1) / 2 + 0.4, 1.6), 'metal:steel', 'door latch')
    m.box((x0 + 0.2, y1 - 0.8, 1.55), (x0 + 1.0, y1 - 0.2, 1.6), 'label:%d' % (i + 1), 'door number')
    m.ctl(f, 'lamp', 'c%d' % i, X(x0 + 1.4), y1 - 0.5, w=0.25, h=0.25, col='30e060', label='COMPARTMENT %d' % (i + 1))
    m.ctl(f, 'zone', 'door%d' % i, X((x0 + x1) / 2), (y0 + y1) / 2, w=x1 - x0 - 0.1, h=y1 - y0 - 0.1, col='000000', label='')


def parcel_lockers():
    """a bank of 24 steel compartments (12 S, 8 M, 4 L) in four columns round a central keypad tower with its
    touch-screen, keypad, card/scan window, camera and parcel-drop slot; plinth, canopy and branding"""
    m = Model('parcel_lockers')
    m.g('carcass')
    m.box((0.2, 0, 1.8), (15.8, 31.0, 15.0), 'paint:#b8bcc2', 'locker carcass')
    m.box((0.0, 0, 1.6), (16.0, 0.8, 15.2), 'paint:#2a2c30', 'plinth')
    m.box((-0.2, 31.0, 0.6), (16.2, 32.0, 15.4), 'paint:#2a62c8', 'canopy')
    m.box((0.0, 30.8, 0.5), (16.0, 31.2, 0.6), 'label:PRIDE PARCEL LOCKERS', 'canopy branding')
    for x in (0.2, 5.6, 10.4, 15.6):
        m.box((x, 0.8, 1.5), (x + 0.2, 31.0, 1.8), 'paint:#8a9098', 'column divider')
    m.g('doors')
    f = m.surface('north', face=1.43, title='LOCKER DOORS')
    cols = ((0.6, 3.0), (3.1, 5.5), (10.5, 12.9), (13.0, 15.4))
    for c, (x0, x1) in enumerate(cols):
        y = 1.0
        stack = [('L', 20 + c, 8.8), ('M', 12 + 2 * c, 5.4), ('M', 13 + 2 * c, 5.4), ('S', 3 * c, 3.2), ('S', 3 * c + 1, 3.2), ('S', 3 * c + 2, 3.2)]
        for (_, i, h) in stack:
            locker_door(m, f, i, x0, x1, y, y + h - 0.1)
            y += h
    m.g('keypad tower')
    m.box((5.8, 0.8, 0.8), (10.2, 30.8, 1.8), 'paint:#2a62c8', 'kiosk tower front')
    display(m, 6.3, 9.7, 22.0, 26.4, 0.8, ['line1', 'entry', 'line2', 'line3'], ['ffffff', 'ffd040', '7cf29a', '8ad4ff'], 'LOCKER SCREEN')
    m.box((6.6, 27.2, 0.7), (9.4, 29.6, 0.8), 'label:PARCELS|ENTER CODE', 'kiosk sign')
    m.cyl((8.0, 29.9, 0.5), 0.3, 0.3, 'z', 'paint:#1a1a1a', 'camera dome')
    m.box((6.6, 4.0, 0.6), (9.4, 5.0, 0.8), 'paint:#1a1a1a', 'parcel drop slot')
    m.box((6.6, 5.2, 0.7), (9.4, 6.0, 0.8), 'label:DROP', 'drop label')
    m.box((6.8, 2.0, 0.6), (9.2, 3.4, 0.8), 'glass', 'scan window')
    m.box((6.2, 10.0, 0.7), (9.8, 19.6, 0.8), 'paint:#1c1e20', 'keypad bezel')
    k = m.surface('north', face=0.68, title='KEYPAD')
    keys = [['1', '2', '3'], ['4', '5', '6'], ['7', '8', '9'], ['clr', '0', 'ok']]
    for r, row in enumerate(keys):
        for c, key in enumerate(row):
            u, v = X(6.9 + c * 1.1), 18.6 - r * 1.25
            cid = 'k' + key if key.isdigit() else key
            col = '1fa34a' if key == 'ok' else 'd02020' if key == 'clr' else 'c8c8c8'
            m.ctl(k, 'push', cid, u, v, w=0.8, h=0.8, col=col, label=key.upper())
            lab(m, k, u, v - 0.6, key.upper())
    push_row(m, k, [('drop', 'e08020', 'DROP'), ('register', '2050d0', 'REG'), ('mine', '505860', 'MINE')], X(6.9), 12.4, step=1.1, size=0.6, label_dv=-0.55)
    lamp_row(m, k, [('pwr', '30e060', '12V'), ('lockl', 'ff3030', 'LOCK', True), ('openl', '30e060', 'OPEN')], X(6.9), 10.8, step=1.1, size=0.3, label_dv=-0.4)
    m.ctl(k, 'digits', 'used', X(8.0), 20.6, w=2.6, h=0.7, col='8ad4ff', label='IN USE', fmt='%s/24 USED')
    side = m.surface('north', face=0.68, title='KIOSK FOOT')
    lamp_row(m, side, [('latel', 'ffb020', 'LATE'), ('full', 'ff3030', 'FULL')], X(6.9), 8.4, step=2.0, size=0.3, label_dv=-0.4)
    m.ctl(side, 'push', 'test', X(9.2), 7.2, w=0.4, h=0.4, col='b0b0b0', label='TEST')
    m.terminal_12v(8.0, 1.0, 15.0, 'south')
    m.g('back')
    m.box((0.2, 0.8, 15.0), (15.8, 31.0, 15.2), 'paint:#8a9098', 'back panel')
    m.louvres(4.0, 12.0, 26.0, 29.0, 15.4, 4)
    return save(m, 0.3)


# ===================================================================================================== #30 holidays
def holiday_controller():
    """festive lighting control pillar: astronomical time switch, pattern sequencer, contactors behind a door, the
    calendar display and the operator's controls on a sloping face"""
    m = Model('holiday_controller')
    m.g('pillar')
    m.box((3.0, 0, 4.0), (13.0, 1.0, 13.0), 'concrete', 'plinth')
    m.box((3.4, 1.0, 4.4), (12.6, 22.0, 12.6), 'paint:#1e5a2a', 'control pillar')
    m.box((3.2, 22.0, 4.2), (12.8, 23.0, 12.8), 'paint:#8a1a1a', 'pillar cap')
    for x in (3.4, 12.2):
        m.box((x, 1.0, 4.3), (x + 0.4, 22.0, 4.4), 'paint:#c8a020', 'gold trim')
    m.box((4.0, 20.6, 4.3), (12.0, 21.8, 4.4), 'label:FESTIVE LIGHTS', 'pillar title')
    m.cyl((8.0, 23.0, 8.5), 0.8, 1.2, 'y', 'paint:#c8a020', 'finial')
    m.box((7.4, 24.2, 7.9), (8.6, 25.4, 9.1), 'lamp:ffe060', 'finial star')
    cabkit.fit(m, 4.0, 12.0, 1.6, 8.6, 4.4, col='paint:#1e5a2a', title='LIGHTING|CONTACTORS', volts='230V 50HZ')
    m.terminal_12v(8.0, 1.4, 12.6, 'south')
    mains_box(m, 12.6, 2.0, 8.5, 'east')
    f = m.surface('north', face=4.38, title='FESTIVE PANEL')
    display(m, 4.2, 11.8, 16.0, 19.6, 4.4, ['line1', 'line2', 'line3'], ['ff6060', '7cf29a', 'ffd040'], 'FESTIVE SCREEN')
    m.ctl(f, 'digits', 'date', X(5.6), 15.0, w=2.6, h=0.8, col='8ad4ff', label='DATE', fmt='%s')
    m.ctl(f, 'digits', 'days', X(10.0), 15.0, w=3.2, h=0.8, col='ffd040', label='DAYS TO GO', fmt='%s DAYS')
    m.ctl(f, 'gauge', 'loadg', X(8.0), 13.2, w=1.2, h=1.2, col='f2f0e6', label='LIGHTING LOAD', max=100)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('onl', 'ffe060', 'ON'), ('showl', 'ff40ff', 'SHOW', True)], X(4.6), 11.4, step=0.95, size=0.32, label_dv=-0.4)
    lamp_row(m, f, [('xmasl', 'e02020', 'XMAS'), ('hallowl', 'ff7a10', 'HALLOW'), ('duskl', '4080ff', 'DUSK'), ('autol', '30e060', 'DATE')], X(8.6), 11.4, step=0.95, size=0.32, label_dv=-0.4)
    for i, p in enumerate(('STEADY', 'CHASE', 'TWINKLE', 'FADE')):
        m.ctl(f, 'lamp', 'p%d' % i, X(4.6 + i * 0.95), 9.9, w=0.3, h=0.3, col='ffb020', label=p)
    m.ctl(f, 'selector', 's_season', X(10.2), 9.8, w=1.1, h=1.1, col='202020', label='SEASON', pos=['AUTO', 'XMAS', 'HALLOW', 'OFF'])
    return holiday_controller_finish(m, f)


def holiday_controller_finish(m, f):
    # the lower operator row sits above the cabinet door (cabinet is 1.6..8.6)
    lab(m, f, X(10.2), 8.9, 'SEASON')
    side = m.surface('east', face=16 - 12.8 - 0.02, title='PILLAR SIDE')
    m.box((12.6, 10.0, 6.0), (12.8, 18.0, 11.0), 'paint:#2a2c30', 'side control plate')
    m.ctl(side, 'selector', 's_pattern', 9.2, 16.4, w=1.1, h=1.1, col='202020', label='PATTERN', pos=['STEADY', 'CHASE', 'TWINKLE', 'FADE'])
    m.ctl(side, 'key', 'k_time', 7.2, 16.4, w=0.9, h=0.9, col='c0a040', label='TIME SWITCH', pos=['DUSK', 'ALWAYS'])
    lab(m, side, 9.2, 15.3, 'PATTERN'); lab(m, side, 7.2, 15.3, 'TIME')
    push_row(m, side, [('show', 'ff40ff', 'SHOW'), ('greet', '1fa34a', 'GREET'), ('test', 'b0b0b0', 'TEST')], 9.6, 13.2, step=1.3, size=0.7, label_dv=-0.6)
    m.ctl(side, 'zone', 'spares', 8.5, 11.0, w=4.0, h=1.6, col='000000', label='')
    m.box((12.6, 10.4, 6.6), (12.75, 11.6, 10.4), 'label:SPARES', 'spares flap')
    return save(m)


def xmas_tree():
    """a tall decorated spruce in a planter tub: tiered branches, baubles, tinsel garland, the star (lit live), the
    LED string controller box with its key and lamps; presents drawn live under it"""
    m = Model('xmas_tree')
    m.g('tub')
    m.box((4.0, 0, 4.0), (12.0, 4.0, 12.0), 'wood', 'planter tub')
    for y in (0.6, 3.0):
        m.box((3.8, y, 3.8), (12.2, y + 0.4, 12.2), 'paint:#c8a020', 'tub band')
    m.box((4.4, 4.0, 4.4), (11.6, 4.4, 11.6), 'paint:#3a2a1a', 'soil')
    m.box((7.4, 4.0, 7.4), (8.6, 8.0, 8.6), 'wood', 'trunk')
    m.g('branches')
    import math
    tiers = [(6.0, 7.0), (9.0, 6.0), (12.0, 5.1), (15.0, 4.2), (18.0, 3.4), (21.0, 2.6), (24.0, 1.8), (26.5, 1.1)]
    for (y, r) in tiers:
        m.box((8 - r, y, 8 - r), (8 + r, y + 2.6, 8 + r), 'paint:#1e5a2a', 'branch tier')
        m.box((8 - r * 0.8, y + 2.6, 8 - r * 0.8), (8 + r * 0.8, y + 3.2, 8 + r * 0.8), 'paint:#2a6e34', 'tier top')
        for a in range(4):
            ang = a * math.pi / 2 + y
            bx, bz = 8 + math.cos(ang) * (r + 0.2), 8 + math.sin(ang) * (r + 0.2)
            m.box((bx - 0.6, y + 0.4, bz - 0.6), (bx + 0.6, y + 1.2, bz + 0.6), 'paint:#2a6e34', 'branch tip')
    m.box((7.4, 28.4, 7.4), (8.6, 29.4, 8.6), 'paint:#1e5a2a', 'leader')
    m.g('baubles')
    rnd = random.Random(25)
    cols = ['#d02028', '#2050c0', '#e0c030', '#c0c0c8', '#a030b0']
    for (y, r) in tiers[:-1]:
        for a in range(3):
            ang = rnd.random() * 2 * math.pi
            bx, bz = 8 + math.cos(ang) * (r + 0.3), 8 + math.sin(ang) * (r + 0.3)
            m.box((bx - 0.45, y + 0.6, bz - 0.45), (bx + 0.45, y + 1.5, bz + 0.45), 'paint:' + rnd.choice(cols), 'bauble')
    m.g('tinsel')
    for i, (y, r) in enumerate(tiers[:-1]):
        m.box((8 - r - 0.1, y + 1.8, 8 - r - 0.1), (8 + r + 0.1, y + 2.0, 8 - r + 0.3), 'paint:#e8d070', 'tinsel')
        m.box((8 - r - 0.1, y + 1.8, 8 + r - 0.3), (8 + r + 0.1, y + 2.0, 8 + r + 0.1), 'paint:#e8d070', 'tinsel')
    m.g('controller box')
    m.box((12.2, 0.4, 2.0), (15.4, 4.4, 4.0), 'paint:#2a2c30', 'LED controller box')
    m.box((12.3, 3.2, 1.95), (15.3, 4.2, 2.0), 'label:12V LED', 'box label')
    m.terminal_12v(13.8, 0.4, 4.0, 'south')
    f = m.surface('north', face=1.98, title='LED BOX')
    m.ctl(f, 'key', 'k_on', X(13.0), 2.2, w=0.6, h=0.6, col='c0a040', label='TREE LIGHTS', pos=['ON', 'OFF'])
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('lit', 'ffe060', 'LIT'), ('presl', 'e02020', 'GIFTS')], X(13.8), 2.6, step=0.6, size=0.22, label_dv=-0.3)
    m.ctl(f, 'push', 'test', X(15.0), 1.2, w=0.3, h=0.3, col='b0b0b0', label='TEST')
    g = m.surface('north', face=3.78, title='PRESENTS')
    m.ctl(g, 'zone', 'presents', X(8.0), 2.0, w=8.0, h=4.0, col='000000', label='')
    return save(m, 0.3)


def holiday_lights():
    """canopy fascia garland: a fascia board, the spruce garland roped along it, red bows, a hanging star, the
    festoon cable (bulbs drawn live) and the 12 V junction box with its switch"""
    m = Model('holiday_lights')
    m.g('fascia')
    m.box((0, 13.0, 13.6), (16, 16.0, 16.0), 'paint:#3a4550', 'canopy fascia')
    m.box((0, 15.6, 12.8), (16, 16.0, 16.0), 'paint:#2a3540', 'fascia cap')
    m.g('garland')
    for i in range(16):
        x = i + 0.5
        y = 13.6 - 0.8 * abs((i % 8) - 3.5) / 3.5
        m.box((x - 0.55, y - 0.5, 12.6), (x + 0.55, y + 0.5, 13.6), 'paint:#1e5a2a' if i % 2 else 'paint:#2a6e34', 'garland')
        if i % 3 == 0:
            m.box((x - 0.2, y - 0.2, 12.5), (x + 0.2, y + 0.2, 12.6), 'paint:#d02028', 'berry')
    for x in (0.4, 8.0, 15.6):
        m.box((x - 0.7, 13.4, 12.4), (x + 0.7, 14.4, 12.6), 'paint:#d02020', 'bow')
        m.box((x - 0.2, 12.2, 12.4), (x + 0.2, 13.4, 12.6), 'paint:#d02020', 'bow tail')
    m.cyl((8.0, 8.6, 12.4), 0.05, 4.0, 'y', 'cable:202020', 'star wire')
    m.box((7.2, 7.4, 12.2), (8.8, 9.0, 12.6), 'paint:#e0c030', 'hanging star')
    m.box((7.7, 6.9, 12.2), (8.3, 9.5, 12.6), 'paint:#e0c030', 'star point')
    m.g('bells')
    for x in (4.0, 12.0):
        m.box((x - 0.5, 10.6, 12.2), (x + 0.5, 11.6, 12.6), 'paint:#e0c030', 'bell')
        m.box((x - 0.7, 10.4, 12.2), (x + 0.7, 10.6, 12.6), 'paint:#e0c030', 'bell lip')
        m.cyl((x, 11.6, 12.4), 0.04, 1.6, 'y', 'paint:#d02020', 'bell ribbon')
    m.g('icicles')
    rnd = random.Random(7)
    for i in range(14):
        x = 0.6 + i * 1.1
        h = 0.6 + rnd.random() * 1.6
        m.box((x - 0.12, 13.0 - h, 15.6), (x + 0.12, 13.0, 15.9), 'paint:#e8f4ff', 'icicle')
    m.g('festoon cable')
    for i in range(10):
        u0, u1 = i * 1.6, (i + 1) * 1.6
        import math
        y0 = 12.4 - 3.0 * math.sin(math.pi * (u0 + 0.8) / 16)
        m.box((u0, y0 + 0.25, 12.15), (u1, y0 + 0.35, 12.25), 'cable:1a1a1a', 'festoon cable')
    m.g('junction box')
    m.box((14.0, 10.0, 14.4), (16.0, 12.6, 15.4), 'paint:ral7035', 'junction box')
    m.terminal_12v(15.0, 10.4, 15.4, 'south')
    f = m.surface('north', face=14.38, title='LIGHTS BOX')
    m.ctl(f, 'key', 'k_on', X(14.6), 11.8, w=0.5, h=0.5, col='c0a040', label='STRING LIGHTS', pos=['ON', 'OFF'])
    m.ctl(f, 'lamp', 'pwr', X(15.4), 11.8, w=0.25, h=0.25, col='30e060', label='12V')
    m.ctl(f, 'lamp', 'lit', X(15.4), 11.0, w=0.25, h=0.25, col='ffe060', label='LIT')
    m.ctl(f, 'push', 'test', X(14.6), 10.8, w=0.3, h=0.3, col='b0b0b0', label='TEST')
    return save(m, 0.5)


def pumpkin(m, x, y, z, s, name='jack-o-lantern'):
    """a carved pumpkin, north face at z - s"""
    m.box((x - s, y, z - s), (x + s, y + s * 1.6, z + s), 'paint:#e07010', name)
    for dx in (-s * 0.55, s * 0.55):
        m.box((x + dx - 0.2, y + 0.1, z - s - 0.02), (x + dx + 0.2, y + s * 1.5, z - s), 'paint:#c05a08', 'rib')
    m.box((x - 0.2, y + s * 1.6, z - 0.2), (x + 0.2, y + s * 1.6 + 0.6, z + 0.2), 'paint:#3a5a20', 'stalk')
    k = s / 1.3
    for (a, b, c, d) in ((-0.9, 0.4, -0.3, 0.9), (0.3, 0.4, 0.9, 0.9), (-0.9, -0.6, 0.9, -0.2)):
        m.box((x + a * k, y + s * 0.8 + b * k, z - s - 0.06), (x + c * k, y + s * 0.8 + d * k, z - s - 0.01), 'paint:#2a1404', 'carved hole')


def halloween_display():
    """hay bales stacked with three jack-o'-lanterns, a scarecrow, a crooked TRICK OR TREAT sign, a cobwebbed lamp
    post, the treat bowl and the LED candle controller"""
    m = Model('halloween_display')
    m.g('hay')
    for (x0, z0, y0) in ((1.0, 4.0, 0), (8.5, 4.0, 0), (4.5, 5.0, 4.0)):
        m.box((x0, y0, z0), (x0 + 6.5, y0 + 4.0, z0 + 6.0), 'paint:#d8b850', 'hay bale')
        for y in (y0 + 1.2, y0 + 2.8):
            m.box((x0 - 0.05, y, z0 - 0.05), (x0 + 6.55, y + 0.2, z0 + 6.05), 'paint:#8a6a30', 'bale twine')
    m.g('lanterns')
    pumpkin(m, 4.0, 4.0, 5.6, 1.3)
    pumpkin(m, 11.5, 4.0, 5.6, 1.3)
    pumpkin(m, 8.0, 8.0, 6.5, 1.7)
    m.g('scarecrow')
    m.cyl((14.5, 0, 12.5), 0.3, 13.0, 'y', 'wood', 'scarecrow pole')
    m.box((12.0, 10.0, 12.3), (17.0, 10.5, 12.7), 'wood', 'cross bar')
    m.box((13.4, 6.0, 11.8), (15.6, 11.0, 13.2), 'paint:#6a3a8a', 'scarecrow shirt')
    m.box((13.6, 11.0, 11.9), (15.4, 13.0, 13.1), 'paint:#d8b850', 'sack head')
    m.box((13.2, 13.0, 11.7), (15.8, 13.4, 13.3), 'paint:#2a2a2a', 'hat brim')
    m.box((13.8, 13.4, 12.0), (15.2, 15.0, 13.0), 'paint:#2a2a2a', 'hat crown')
    m.box((13.9, 12.0, 11.85), (14.3, 12.4, 11.9), 'paint:#1a1a1a', 'button eye')
    m.box((14.7, 12.0, 11.85), (15.1, 12.4, 11.9), 'paint:#1a1a1a', 'button eye')
    m.g('sign')
    m.cyl((1.5, 0, 13.0), 0.25, 9.0, 'y', 'wood', 'sign post')
    m.box((0.2, 7.0, 12.6), (6.0, 9.4, 13.0), 'wood', 'sign board', rot={'axis': 'z', 'angle': -22.5, 'origin': [1.5, 8.0, 12.8]}, north='label:TRICK OR|TREAT')
    m.g('lamp post + webs')
    m.cyl((1.0, 0, 2.0), 0.3, 14.0, 'y', 'paint:#1a1a1a', 'lamp post')
    m.box((0.2, 14.0, 1.2), (1.8, 15.6, 2.8), 'paint:#1a1a1a', 'lantern')
    m.box((0.4, 14.2, 1.15), (1.6, 15.4, 1.2), 'lamp:ff9020', 'lantern glass')
    for i in range(5):
        m.box((1.3, 9.0 + i * 0.9, 2.0), (1.35, 9.8 + i * 0.9, 4.0 - i * 0.3), 'white', 'cobweb strand')
    m.g('bats + candles')
    for (x, y, z) in ((3.0, 20.0, 4.0), (9.0, 22.0, 6.0), (13.0, 19.0, 3.0)):
        m.box((x - 0.2, y - 0.3, z - 0.2), (x + 0.2, y + 0.3, z + 0.2), 'paint:#1a1a1a', 'bat body')
        m.box((x - 1.2, y, z - 0.05), (x - 0.2, y + 0.4, z + 0.05), 'paint:#1a1a1a', 'bat wing', rot={'axis': 'z', 'angle': 22.5, 'origin': [x - 0.2, y, z]})
        m.box((x + 0.2, y, z - 0.05), (x + 1.2, y + 0.4, z + 0.05), 'paint:#1a1a1a', 'bat wing', rot={'axis': 'z', 'angle': -22.5, 'origin': [x + 0.2, y, z]})
        m.cyl((x, y + 0.3, z), 0.03, 24.0 - y, 'y', 'cable:202020', 'bat thread')
    for (x, z) in ((2.0, 3.0), (5.0, 2.6), (11.0, 2.6)):
        m.cyl((x, 0, z), 0.25, 1.4, 'y', 'white', 'candle')
        m.box((x - 0.08, 1.4, z - 0.08), (x + 0.08, 1.7, z + 0.08), 'lamp:ffb040', 'candle flame')
    pumpkin(m, 3.0, 0, 2.6, 0.8, 'small pumpkin')
    m.box((12.0, 6.0, 4.0), (12.6, 6.6, 4.6), 'paint:#1a1a1a', 'spider')
    m.cyl((12.3, 6.6, 4.3), 0.02, 6.0, 'y', 'white', 'spider thread')
    m.g('treat bowl')
    m.box((6.0, 0, 1.0), (10.0, 1.6, 3.4), 'paint:#3a1a4a', 'cauldron')
    m.box((6.3, 1.6, 1.3), (9.7, 1.9, 3.1), 'paint:#5a2a6a', 'cauldron rim')
    for x in (6.8, 7.8, 8.8):
        m.box((x, 1.6, 1.8), (x + 0.6, 2.1, 2.4), 'paint:#e07010', 'candy')
    m.g('controller')
    m.box((11.6, 0.4, 1.0), (15.0, 3.6, 2.4), 'paint:#2a2c30', 'LED candle controller')
    m.terminal_12v(13.3, 0.4, 2.4, 'south')
    f = m.surface('north', face=0.98, title='CANDLES')
    m.ctl(f, 'key', 'k_on', X(12.4), 2.4, w=0.6, h=0.6, col='c0a040', label='CANDLES', pos=['ON', 'OFF'])
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('lit', 'ff9020', 'LIT'), ('bowll', 'a030b0', 'TREATS')], X(13.2), 2.8, step=0.6, size=0.22, label_dv=-0.3)
    m.ctl(f, 'push', 'test', X(14.4), 1.2, w=0.3, h=0.3, col='b0b0b0', label='TEST')
    b = m.surface('north', face=0.98, title='TREAT BOWL')
    m.ctl(b, 'zone', 'bowl', X(8.0), 1.0, w=4.0, h=2.0, col='000000', label='')
    return save(m, 0.4)


# ===================================================================================================== items, states, recipes
def kit_textures():
    for name, bulbs, base in (('holiday_kit_xmas', [(224, 32, 32), (32, 192, 64), (255, 200, 48), (48, 112, 255)], (40, 90, 50)),
                              ('holiday_kit_ghost', [(255, 122, 16), (154, 48, 224), (96, 224, 64)], (40, 30, 50))):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(3, 15):                                                     # a cardboard kit box
            for x in range(2, 14):
                img.putpixel((x, y), (170, 130, 80, 255) if 4 < y else (140, 104, 60, 255))
        for x in range(2, 14):
            img.putpixel((x, 7), base + (255,))
        for i, x in enumerate(range(3, 14, 2)):                                     # a string of bulbs over the lid
            yy = 2 + (i % 2)
            img.putpixel((x, yy), bulbs[i % len(bulbs)] + (255,))
            if x + 1 < 14: img.putpixel((x + 1, 3), (30, 30, 30, 255))
        if name.endswith('ghost'):
            for (x, y) in ((6, 9), (7, 9), (8, 9), (9, 9), (6, 10), (9, 10), (6, 11), (7, 11), (8, 11), (9, 11), (6, 12), (8, 12)):
                img.putpixel((x, y), (240, 240, 255, 255))
            img.putpixel((7, 10), (20, 20, 30, 255)); img.putpixel((8, 10), (20, 20, 30, 255))
        else:
            for (x, y) in ((7, 9), (6, 10), (7, 10), (8, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (7, 12)):
                img.putpixel((x, y), (30, 120, 50, 255))
            img.putpixel((7, 8), (255, 220, 60, 255))
        img.save(os.path.join(ASSETS, 'textures/items/%s.png' % name))
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + name}}, open(os.path.join(ASSETS, 'models/item/%s.json' % name), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/forklift'}, open(os.path.join(ASSETS, 'models/item/forklift.json'), 'w'), indent=1)


def P(meta): return {'item': 'irextras:part', 'data': meta}
PART = {'plate': 0, 'frame': 1, 'circuit': 2, 'motor': 3, 'hydraulic': 4, 'fan': 7, 'seal': 12, 'contactor': 18}
I, ST, CHEST, HOP = 'minecraft:iron_ingot', ('minecraft:stone', 0), 'minecraft:chest', 'minecraft:hopper'

RECIPES = {
    'grain_pit': (['B B', 'BHB', 'SMS'], {'B': 'minecraft:iron_bars', 'H': HOP, 'S': ST, 'M': P(PART['motor'])}),
    'grain_leg': (['PHP', 'PCP', 'MFM'], {'P': P(PART['plate']), 'H': HOP, 'C': P(PART['circuit']), 'M': P(PART['motor']), 'F': P(PART['frame'])}),
    'grain_dryer': (['PHP', 'FAF', 'MCM'], {'P': P(PART['plate']), 'H': HOP, 'F': 'minecraft:furnace', 'A': P(PART['fan']), 'M': P(PART['motor']), 'C': P(PART['circuit'])}),
    'grain_silo': (['PPP', 'PCP', 'IHI'], {'P': P(PART['plate']), 'C': CHEST, 'I': I, 'H': HOP}),
    'grain_loadout': (['IHI', 'CPC', 'I I'], {'I': I, 'H': HOP, 'C': P(PART['circuit']), 'P': 'minecraft:heavy_weighted_pressure_plate'}),
    'oil_tank': (['PPP', 'PBP', 'SVS'], {'P': P(PART['plate']), 'B': 'minecraft:bucket', 'S': ST, 'V': P(PART['seal'])}),
    'oil_rack': (['IRI', 'MCM', 'ILI'], {'I': I, 'R': 'minecraft:ladder', 'M': P(PART['motor']), 'C': P(PART['circuit']), 'L': 'minecraft:lever'}),
    'oil_firegas': (['RBR', 'CWC', 'PFP'], {'R': ('minecraft:redstone_block', None), 'B': 'minecraft:bucket', 'C': P(PART['circuit']), 'W': 'minecraft:water_bucket', 'P': P(PART['plate']), 'F': P(PART['frame'])}),
    'warehouse_dock': (['IDI', 'IMI', 'SCS'], {'I': I, 'D': 'minecraft:iron_door', 'M': P(PART['motor']), 'S': ST, 'C': P(PART['circuit'])}),
    'warehouse_rack': (['ICI', 'WCW', 'ICI'], {'I': I, 'C': CHEST, 'W': ('minecraft:planks', 0)}),
    'warehouse_office': (['GGG', 'CRC', 'WWW'], {'G': 'minecraft:glass_pane', 'C': P(PART['circuit']), 'R': 'minecraft:repeater', 'W': ('minecraft:planks', 0)}),
    'parcel_lockers': (['ICI', 'CBC', 'ICI'], {'I': I, 'C': CHEST, 'B': 'minecraft:stone_button'}),
    'holiday_controller': (['GCG', 'RTR', 'SSS'], {'G': 'minecraft:glowstone_dust', 'C': P(PART['circuit']), 'R': 'minecraft:redstone', 'T': 'minecraft:clock', 'S': ST}),
    'xmas_tree': ([' G ', 'LSL', ' W '], {'G': 'minecraft:gold_nugget', 'L': ('minecraft:leaves', 1), 'S': ('minecraft:sapling', 1), 'W': ('minecraft:planks', 1)}),
    'holiday_lights': (['GGG', 'LRL'], {'G': 'minecraft:glowstone_dust', 'L': ('minecraft:leaves', 1), 'R': 'minecraft:redstone'}),
    'halloween_display': (['PTP', 'HHH'], {'P': 'minecraft:lit_pumpkin', 'T': 'minecraft:torch', 'H': 'minecraft:hay_block'}),
    'holiday_kit': ([' G ', 'RSR', ' P '], {'G': 'minecraft:glowstone_dust', 'R': 'minecraft:redstone', 'S': 'minecraft:string', 'P': 'minecraft:paper'}),
}
KIT_GHOST = ([' G ', 'RSR', ' P '], {'G': 'minecraft:glowstone_dust', 'R': 'minecraft:redstone', 'S': 'minecraft:string', 'P': 'minecraft:pumpkin'})


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


LANG = {
    'tile.irextras.grain_pit.name': 'Grain Receiving Pit', 'tile.irextras.grain_leg.name': 'Grain Leg (Bucket Elevator)', 'tile.irextras.grain_dryer.name': 'Grain Dryer',
    'tile.irextras.grain_silo.name': 'Grain Silo Bin', 'tile.irextras.grain_loadout.name': 'Grain Loadout',
    'tile.irextras.oil_tank.name': 'Oil Storage Tank', 'tile.irextras.oil_rack.name': 'Tank Car Loading Rack', 'tile.irextras.oil_firegas.name': 'Tank Farm Fire and Gas Panel',
    'tile.irextras.warehouse_dock.name': 'Warehouse Dock Door', 'tile.irextras.warehouse_rack.name': 'Warehouse Pallet Rack', 'tile.irextras.warehouse_office.name': 'Warehouse Office',
    'tile.irextras.parcel_lockers.name': 'Station Parcel Lockers',
    'tile.irextras.holiday_controller.name': 'Festive Lighting Controller', 'tile.irextras.xmas_tree.name': 'Station Christmas Tree',
    'tile.irextras.holiday_lights.name': 'Festive String Lights', 'tile.irextras.halloween_display.name': 'Halloween Display',
    'item.irextras.holiday_kit_xmas.name': 'Train Christmas Lights Kit', 'item.irextras.holiday_kit_ghost.name': 'Ghost Train Decoration Kit',
    'entity.irextras.forklift.name': 'Forklift',
}

BLOCKS = [k for k in RECIPES if k != 'holiday_kit']

if __name__ == '__main__':
    for fn in (grain_pit, grain_leg, grain_dryer, grain_silo, grain_loadout, oil_tank, oil_rack, oil_firegas,
               warehouse_dock, warehouse_rack, warehouse_office, forklift, parcel_lockers,
               holiday_controller, xmas_tree, holiday_lights, halloween_display):
        fn()
    for n in BLOCKS:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        res = {'item': 'irextras:' + n}
        if n == 'holiday_kit': res['data'] = 0
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': res},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    json.dump({'type': 'minecraft:crafting_shaped', 'pattern': KIT_GHOST[0], 'key': {k: ing(v) for k, v in KIT_GHOST[1].items()}, 'result': {'item': 'irextras:holiday_kit', 'data': 1}},
              open(os.path.join(ASSETS, 'recipes/holiday_kit_ghost.json'), 'w'), indent=1)
    kit_textures()
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# batch 6 #26-30 grain elevator, oil tank farm, warehouse, parcel lockers, holiday trains\n' + '\n'.join(add) + '\n')
    print('batch 6f freight + fun written')
    for k, v in COUNTS.items(): print('  %-20s %d boxes' % (k, v))
