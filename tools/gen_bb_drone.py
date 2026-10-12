#!/usr/bin/env python3
"""IR Extras #2 + #4: the Drone Dock, the inspection drone (body / rotor / gimbal, drawn moving by
drone/client/DroneClient), the Weather Train Depot desk, and the item icons for the drone battery, the handheld
controller and the snowplough attachment. Models face north; on north surfaces u = 16 - x (viewer's left), on east
surfaces u = 16 - z, on west surfaces u = z. Every lamp / button / key / selector / dial / screen / zone is a live
control the Java tile (drone.TileDroneDock, weather.TileWxDepot) reads or works. 12 V boxes are wire terminals.
Also writes blockstates, item models, recipes and lang lines, and prints box counts + the desk's shelf spots."""
import json, os
from PIL import Image, ImageDraw
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, led_board, desk, strip, buttons, hood, monitor, cupboard_shelf
import cabkit

X = lambda u: 16 - u
COUNTS, SPOTS = {}, {}


def lamp(m, sf, cid, u, v, col, label, s=0.55, blink=False):
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **({'blink': True} if blink else {}))


def digits(m, sf, cid, u, v, w, h=0.45, col='7cf29a'):
    m.ctl(sf, 'digits', cid, u, v, w=w, h=h, col=col, label='', fmt='%s')


# ============================================================================================================ the dock
def drone_dock():
    m = Model('drone_dock')
    m.g('plinth')
    m.box((0, 0, 0), (16, 0.8, 16), 'concrete', 'concrete plinth')
    for (x, z) in ((0.6, 0.6), (15.4, 0.6), (15.4, 15.4), (0.6, 15.4)):
        m.bolt(x, 0.8, z, 'up', 0.5)
    m.g('enclosure')
    m.box((0.5, 0.8, 0.5), (15.5, 10.0, 15.5), 'ral7035', 'GRP enclosure')
    for (x, z) in ((0.2, 0.2), (14.9, 0.2), (14.9, 14.9), (0.2, 14.9)):
        m.box((x, 0.8, z), (x + 0.9, 10.0, z + 0.9), 'paint:#2b2e33', 'corner post')
    m.box((0.4, 9.4, 0.4), (15.6, 10.0, 15.6), 'paint:#2b2e33', 'deck rim')
    # ---- the landing / charging pad
    m.g('landing pad')
    m.box((0, 10.0, 0), (16, 11.0, 16), 'paint:#30343a', 'landing deck')
    for (a, b) in (((3, 3), (13, 3.5)), ((3, 12.5), (13, 13)), ((3, 3), (3.5, 13)), ((12.5, 3), (13, 13))):
        m.box((a[0], 11.0, a[1]), (b[0], 11.04, b[1]), 'paint:#f0c020', 'pad ring')
    for (a, b) in (((5.6, 5.0), (6.4, 11.0)), ((9.6, 5.0), (10.4, 11.0)), ((6.4, 7.6), (9.6, 8.4))):
        m.box((a[0], 11.0, a[1]), (b[0], 11.05, b[1]), 'paint:#f2f2ee', 'H marking')
    for x in (5.4, 9.8):                                                       # the skids land on the charging rails
        m.box((x, 11.0, 4.4), (x + 0.8, 11.07, 11.6), 'metal:copper', 'charging rail')
        for z in (4.6, 11.0):
            m.box((x + 0.1, 11.0, z), (x + 0.7, 11.1, z + 0.4), 'paint:#141414', 'rail insulator')
    for (x, z, c) in ((0.2, 0.2, '30ff60'), (15.0, 0.2, '30ff60'), (15.0, 15.0, '30ff60'), (0.2, 15.0, '30ff60'),
                      (7.6, 0.1, 'ffb020'), (7.6, 15.1, 'ffb020'), (0.1, 7.6, 'ffb020'), (15.1, 7.6, 'ffb020')):
        m.box((x, 11.0, z), (x + 0.8, 11.35, z + 0.8), 'led:' + c, 'pad edge light')
    m.box((6.8, 11.0, 0.4), (9.2, 11.03, 1.4), 'label:FRONT', 'front arrow')
    # ---- the weather mast: anemometer, vane, rain gauge; GNSS + LTE; beacon
    m.g('weather mast')
    m.cyl((1.6, 11.0, 14.4), 0.32, 13.0, 'y', 'galv', 'mast')
    m.cyl((1.6, 24.0, 14.4), 0.3, 0.8, 'y', 'paint:#202020', 'anemometer hub')
    for (dx, dz) in ((1.6, 0), (-0.8, 1.38), (-0.8, -1.38)):
        m.box((1.6 + min(0, dx), 24.45, 14.4 + min(0, dz) - 0.06), (1.6 + max(0, dx) + 0.01, 24.55, 14.4 + max(0, dz) + 0.06), 'metal:steel', 'cup arm')
        m.cyl((1.6 + dx, 24.5, 14.4 + dz - 0.3), 0.32, 0.6, 'z', 'paint:#f0f0f0', 'anemometer cup')
    m.box((1.5, 22.2, 14.4), (1.7, 22.4, 17.0), 'metal:steel', 'vane arm')
    m.box((1.55, 21.6, 16.6), (1.65, 23.0, 17.6), 'paint:#e04020', 'vane fin')
    m.box((1.45, 22.15, 13.6), (1.75, 22.45, 14.1), 'paint:#202020', 'vane counterweight')
    m.box((1.6, 18.0, 14.2), (3.6, 18.3, 14.6), 'galv', 'rain gauge bracket')
    m.cyl((3.9, 16.6, 14.4), 0.75, 1.6, 'y', 'paint:#f0f0ec', 'tipping-bucket rain gauge')
    m.cyl((3.9, 18.2, 14.4), 0.85, 0.2, 'y', 'metal:steel', 'gauge funnel rim')
    m.cyl((14.4, 11.0, 14.4), 0.18, 2.6, 'y', 'galv', 'GNSS pole')
    m.cyl((14.4, 13.6, 14.4), 0.85, 0.35, 'y', 'paint:#f2f2ee', 'GNSS antenna')
    m.cyl((14.4, 13.95, 14.4), 0.4, 0.3, 'y', 'led:ffb020', 'beacon')
    m.cyl((14.4, 11.0, 1.6), 0.25, 0.5, 'y', 'paint:#202020', 'LTE antenna base')
    m.cyl((14.4, 11.5, 1.6), 0.1, 4.5, 'y', 'paint:#202020', 'LTE whip')
    m.gland(1.6, 11.0, 13.6, 'down', r=0.25, length=0.3)
    # ---- the front: nameplate + rotor danger on the deck edge
    m.g('deck signs')
    m.box((9.0, 10.15, -0.04), (15.4, 10.85, 0.0), 'plate', 'nameplate', north='plate:DRONE DOCK')
    m.box((0.6, 10.15, -0.04), (5.6, 10.85, 0.0), 'danger', 'danger plate', north='danger:DANGER|ROTORS')
    m.box((6.0, 10.2, -0.03), (8.6, 10.8, 0.0), 'warn', 'warning triangle', faces=('north',))
    # ---- the control panel
    m.g('control panel')
    m.box((0.9, 1.4, 0.3), (15.1, 9.3, 0.5), 'paint:#24282c', 'panel plate')
    for (x, y) in ((1.2, 1.7), (14.8, 1.7), (1.2, 9.0), (14.8, 9.0)):
        m.bolt(x, y, 0.3, 'north', 0.3)
    m.box((6.5, 5.0, 0.22), (14.9, 9.15, 0.3), 'paint:#03140a', 'screen')
    m.box((6.35, 4.85, 0.18), (15.05, 5.0, 0.3), 'paint:#1a1c1e', 'screen bezel')
    m.box((6.35, 9.15, 0.18), (15.05, 9.3, 0.3), 'paint:#1a1c1e', 'screen bezel')
    for (x0, y0) in ((1.1, 7.2), (3.6, 7.2), (1.1, 5.1), (3.6, 5.1)):
        m.box((x0, y0, 0.24), (x0 + 2.3, y0 + 2.0, 0.3), 'paint:#1a1c1e', 'gauge bezel')
    f = m.surface('north', face=0.17, title='DOCK PANEL')
    for i in range(8):
        digits(m, f, 's%d' % (i + 1), X(10.7), 8.85 - i * 0.5, 8.0, 0.44, ('ffd040', '7cf29a', '8ad4ff', 'b8f0c8', '7cf29a', 'b8f0c8', 'ffd27a', '8ad4ff')[i])
    for (cid, x, y, lab) in (('g_bat', 2.25, 8.2, 'BATT %'), ('g_bay', 4.75, 8.2, 'BAY %'), ('g_wind', 2.25, 6.1, 'WIND'), ('g_kw', 4.75, 6.1, 'CHG KW')):
        m.ctl(f, 'gauge', cid, X(x), y, w=1.9, h=1.9, col='f2f0e6', label=lab, max=100)
    for i, (cid, col, lab, bl) in enumerate((('l_pwr', '30e060', '12V', False), ('l_mains', '30e060', 'MAINS', False), ('l_chg', 'ffb020', 'CHARGE', True),
                                             ('l_ready', '30e060', 'READY', False), ('l_air', '3080ff', 'AIRBORNE', False), ('l_rth', 'ffb020', 'RTH', True),
                                             ('l_wx', 'ff8020', 'WX HOLD', True), ('l_fault', 'ff3030', 'FAULT', True), ('l_low', 'ff3030', 'LOW BAT', True),
                                             ('l_auto', '30e060', 'AUTO', False), ('l_pad', 'f0f0f0', 'PAD LT', False))):
        lamp(m, f, cid, 1.2 + i * 1.15, 4.4, col, lab, 0.5, bl)
    m.ctl(f, 'mushroom', 'm_estop', 14.35, 4.3, w=1.3, h=1.3, col='d01818', label='E-STOP')
    for i, (cid, lab, pos) in enumerate((('s_mission', 'MISSION', ['TRACK', 'OHLE', 'STRUCT', 'TUNNEL']), ('s_len', 'LENGTH M', ['100', '250', '500', '1000', '2000']),
                                         ('s_dir', 'DIR', ['A', 'B']), ('s_alt', 'HEIGHT M', ['AUTO', '5', '10', '20', '40']),
                                         ('s_charge', 'CHARGE', ['OFF', 'NORM', 'FAST']), ('s_every', 'PATROL', ['OFF', '6H', '12H', '24H']))):
        m.ctl(f, 'selector', cid, 1.4 + i * 1.9, 3.15, w=0.95, h=0.95, col='202020', label=lab, pos=pos)
    m.ctl(f, 'key', 'k_arm', 12.75, 3.15, w=0.8, h=0.8, col='c0a040', label='ARM', pos=['SAFE', 'ARMED'])
    m.ctl(f, 'key', 'k_auto', 14.35, 2.75, w=0.8, h=0.8, col='c0a040', label='AUTO', pos=['OFF', 'AUTO'])
    for i, (cid, col, lab) in enumerate((('launch', '30c040', 'LAUNCH'), ('rth', 'f0a020', 'RETURN'), ('hold', '2050d0', 'HOLD'), ('resume', 'f0f0f0', 'RESUME'),
                                         ('pad', 'f0f0f0', 'PAD LT'), ('print', 'f0f0f0', 'PRINT'), ('test', 'f0f0f0', 'TEST'))):
        m.ctl(f, 'push', cid, 1.4 + i * 1.65, 1.95, w=0.75, h=0.75, col=col, label=lab)
    # ---- east: the battery bay drawer with a window + two LEDs per pack
    m.g('battery bay')
    m.box((15.5, 1.4, 1.2), (15.85, 9.4, 14.8), 'paint:#3a3f45', 'bay drawer front')
    m.box((15.85, 8.5, 4.5), (16.0, 8.9, 11.5), 'metal:steel', 'drawer handle')
    m.box((15.85, 7.4, 2.0), (15.9, 8.2, 4.2), 'label:BAY', 'bay label', faces=('east',))
    m.box((15.85, 7.4, 11.8), (15.9, 8.2, 14.4), 'label:6X', 'pack label', faces=('east',))
    e = m.surface('east', face=16 - 15.9 - 0.02, title='BATTERY BAY')
    for i in range(6):
        z0, y0 = 1.8 + (i % 3) * 4.4, 4.6 - (i // 3) * 2.9
        m.box((15.85, y0, z0), (15.9, y0 + 2.4, z0 + 3.8), 'paint:#101214', 'pack window')
        m.box((15.8, y0 + 0.9, z0 + 0.4), (15.85, y0 + 2.2, z0 + 3.4), 'battery', 'flight battery')
        lamp(m, e, 'bf%d' % (i + 1), 16 - (z0 + 1.0), y0 + 0.45, '30e060', 'FULL', 0.45)
        lamp(m, e, 'bc%d' % (i + 1), 16 - (z0 + 2.8), y0 + 0.45, 'ffb020', 'CHG', 0.45, True)
    m.ctl(e, 'zone', 'bay', 16 - 8.0, 8.7, w=7.4, h=1.0, col='000000', label='')
    # ---- west: the airframe hangar hatch
    m.g('hangar hatch')
    m.box((0.15, 1.4, 1.0), (0.5, 9.4, 12.4), 'paint:#c9cbc7', 'hangar hatch')
    for y in (2.2, 8.4):
        m.box((0.05, y, 1.0), (0.15, y + 0.6, 2.2), 'metal:steel', 'hatch hinge')
    m.box((0.0, 5.0, 10.6), (0.15, 6.0, 11.4), 'metal:steel', 'T-handle')
    m.box((0.1, 7.0, 3.0), (0.15, 7.8, 10.4), 'label:DRONE HANGAR', 'hangar label', faces=('west',))
    wsf = m.surface('west', face=0.13, title='HANGAR')
    m.ctl(wsf, 'zone', 'hangar', 6.7, 5.4, w=11.0, h=7.6, col='000000', label='')
    m.terminal_12v(0.5, 1.2, 14.2, 'west')
    # ---- south: the charger cabinet (opening doors, working MCBs and fuses) + the mains gland
    m.g('mains')
    m.gland(14.9, 1.3, 15.5, 'south', r=0.4, length=0.8)
    m.box((13.6, 2.2, 15.5), (15.4, 3.0, 15.56), 'label:240V AC', 'supply label', faces=('south',))
    cabkit.fit(m, 2.0, 12.6, 1.4, 9.2, 0.5, side='south', title='DOCK CHARGER|240V AC', volts='240V AC')
    m.save(item_gui=0.5)
    COUNTS['drone_dock'] = len(m.boxes)


# ============================================================================================================ the drone
MOTORS = [(-0.5, 7.4, -0.5), (16.5, 7.4, -0.5), (16.5, 7.4, 16.5), (-0.5, 7.4, 16.5)]


def drone_body():
    m = Model('drone_body')
    m.g('fuselage')
    m.box((4.6, 3.6, 3.6), (11.4, 6.4, 12.4), 'paint:#2a2d31', 'fuselage')
    m.box((5.0, 6.4, 4.0), (11.0, 7.1, 12.0), 'paint:#33373c', 'top shell')
    m.box((5.4, 3.9, 2.7), (10.6, 6.0, 3.6), 'paint:#2a2d31', 'nose fairing')
    m.box((5.0, 3.4, 12.4), (11.0, 6.0, 13.2), 'paint:#2a2d31', 'tail fairing')
    for x in (5.8, 9.4):                                                   # forward stereo vision pair
        m.box((x, 4.6, 2.5), (x + 0.8, 5.4, 2.7), 'paint:#101010', 'vision sensor')
        m.box((x + 0.15, 4.75, 2.45), (x + 0.65, 5.25, 2.5), 'glass', 'vision lens')
    for z in (6.0, 9.4):                                                   # side and rear sensors
        m.box((4.4, 4.6, z), (4.6, 5.4, z + 0.8), 'paint:#101010', 'side sensor')
        m.box((11.4, 4.6, z), (11.6, 5.4, z + 0.8), 'paint:#101010', 'side sensor')
    m.box((7.2, 4.4, 13.2), (8.8, 5.4, 13.3), 'paint:#101010', 'rear sensor')
    m.box((7.4, 3.4, 9.0), (8.6, 3.6, 10.2), 'paint:#101010', 'downward ToF sensor')
    m.box((5.6, 5.0, 2.55), (6.0, 5.4, 2.6), 'led:ff2020', 'front status LED')
    m.box((10.0, 5.0, 2.55), (10.4, 5.4, 2.6), 'led:ff2020', 'front status LED')
    m.g('battery')
    m.box((6.0, 7.1, 5.0), (10.0, 8.2, 9.6), 'battery', 'flight battery')
    m.box((6.4, 8.2, 5.4), (9.6, 8.3, 7.0), 'paint:#f0c020', 'battery latch')
    m.box((9.4, 8.2, 8.0), (9.8, 8.25, 9.4), 'led:30e060', 'battery level LEDs')
    m.g('avionics')
    m.cyl((8.0, 7.1, 10.8), 0.95, 0.4, 'y', 'paint:#f2f2ee', 'GNSS / RTK antenna')
    for x in (5.4, 10.6):
        m.box((x - 0.15, 7.1, 11.2), (x + 0.15, 9.4, 11.5), 'paint:#202020', 'LTE / OcuSync antenna')
    m.cyl((8.0, 7.1, 4.4), 0.35, 0.3, 'y', 'led:f8f8f8', 'top anti-collision strobe')
    m.cyl((8.0, 3.2, 11.2), 0.3, 0.2, 'y', 'led:f8f8f8', 'bottom anti-collision strobe')
    m.g('arms')
    arm = {'axis': 'y', 'angle': 45, 'origin': [8, 6.6, 8]}
    m.box((-4.0, 6.2, 7.55), (20.0, 6.95, 8.45), 'paint:#24272b', 'carbon arm', rot=arm)
    m.box((7.55, 6.2, -4.0), (8.45, 6.95, 20.0), 'paint:#24272b', 'carbon arm', rot=arm)
    m.box((3.0, 6.25, 7.6), (13.0, 6.9, 8.4), 'paint:#3a3e44', 'arm fold joint', rot=arm)
    m.g('motors')
    for i, (x, y, z) in enumerate(MOTORS):
        m.cyl((x, 5.6, z), 0.95, 1.4, 'y', 'paint:#1c1c1e', 'motor')
        m.cyl((x, 7.0, z), 0.8, 0.4, 'y', 'metal:steel', 'motor bell')
        m.cyl((x, 5.2, z), 0.5, 0.4, 'y', 'paint:#1c1c1e', 'ESC pod')
        m.box((x - 0.25, 5.0, z - 0.25), (x + 0.25, 5.2, z + 0.25), 'led:' + ('ff2020' if z < 8 else '30ff40'), 'nav light')
    m.g('landing gear')
    for x in (4.6, 10.8):
        m.box((x, 0.0, 3.2), (x + 0.6, 0.5, 12.8), 'paint:#1a1a1a', 'skid tube')
        for z in (4.6, 11.0):
            m.box((x + 0.1, 0.5, z), (x + 0.5, 3.6, z + 0.4), 'paint:#1a1a1a', 'gear leg')
        m.box((x - 0.05, 0.0, 3.0), (x + 0.65, 0.35, 3.3), 'rubber', 'skid cap')
        m.box((x - 0.05, 0.0, 12.7), (x + 0.65, 0.35, 13.0), 'rubber', 'skid cap')
    m.g('gimbal mount')
    m.box((6.4, 3.2, 1.6), (9.6, 3.6, 3.4), 'paint:#33373c', 'damper plate')
    for (x, z) in ((6.7, 1.9), (9.3, 1.9), (6.7, 3.1), (9.3, 3.1)):
        m.box((x - 0.2, 3.0, z - 0.2), (x + 0.2, 3.2, z + 0.2), 'rubber', 'vibration damper')
    m.box((6.3, 4.2, 12.9), (9.7, 4.9, 13.0), 'label:IRX', 'tail label', faces=('south',))
    m.save(item_gui=0.55)
    COUNTS['drone_body'] = len(m.boxes)


def drone_rotor():
    m = Model('drone_rotor')
    m.g('rotor')
    m.cyl((8, 0.0, 8), 0.5, 0.45, 'y', 'paint:#202020', 'prop hub')
    m.cyl((8, 0.45, 8), 0.22, 0.15, 'y', 'metal:steel', 'prop nut')
    m.box((3.6, 0.12, 7.62), (12.4, 0.27, 8.38), 'paint:#1a1a1c', 'blade pair')
    m.box((3.6, 0.13, 7.6), (4.6, 0.28, 8.4), 'paint:#e8601c', 'blade tip')
    m.box((11.4, 0.13, 7.6), (12.4, 0.28, 8.4), 'paint:#e8601c', 'blade tip')
    m.box((5.2, 0.27, 7.7), (6.4, 0.29, 8.1), 'paint:#f2f2ee', 'blade label')
    m.save(item_gui=0.6)
    COUNTS['drone_rotor'] = len(m.boxes)


def drone_gimbal():
    m = Model('drone_gimbal')
    m.g('yoke')
    m.box((7.6, 2.6, 2.2), (8.4, 3.2, 2.6), 'paint:#33373c', 'pan motor')
    m.box((6.3, 1.0, 2.2), (6.7, 2.8, 2.6), 'paint:#33373c', 'yoke arm')
    m.box((9.3, 1.0, 2.2), (9.7, 2.8, 2.6), 'paint:#33373c', 'yoke arm')
    m.box((6.3, 2.6, 2.2), (9.7, 2.9, 2.6), 'paint:#33373c', 'yoke bridge')
    m.cyl((9.7, 2.0, 2.4), 0.45, 0.3, 'x', 'paint:#1c1c1e', 'tilt motor')
    m.g('camera')
    m.box((6.7, 0.8, 1.2), (9.3, 2.6, 3.6), 'paint:#202226', 'camera head')
    m.cyl((7.4, 1.9, 0.8), 0.45, 0.4, 'z', 'paint:#101010', 'zoom lens barrel', cap='glass')
    m.cyl((8.8, 1.9, 0.9), 0.3, 0.3, 'z', 'paint:#101010', 'thermal lens', cap='paint:#3a2a10')
    m.box((7.9, 1.0, 1.1), (8.5, 1.4, 1.2), 'paint:#c03020', 'laser rangefinder')
    m.box((6.8, 2.2, 3.6), (9.2, 2.5, 3.65), 'label:ZOOM+IR', 'payload label', faces=('south',))
    m.save(item_gui=0.6)
    COUNTS['drone_gimbal'] = len(m.boxes)


# ============================================================================================================ the depot desk
def wx_depot_desk():
    m = Model('wx_depot_desk')
    back_wall(m, panel='#1f3a4a', plaster='#e6eaee')
    # ---- the run board on the wall: six runs, RUN / CALLED lamps
    m.g('run board')
    led_board(m, -14, 15.5, 14.0, 28.6)
    m.box((-6, 29.0, 14.7), (22, 31.2, 15.4), 'paint:#0e3a5c', 'board header')
    m.box((-5.6, 29.2, 14.62), (21.6, 31.0, 14.7), 'label:WEATHER TRAINS', 'header lettering')
    m.cyl((27.0, 30.1, 14.4), 0.95, 0.6, 'z', 'gauge:clock', 'control clock', cap='gauge:clock')
    b = m.surface('north', face=14.42, title='RUN BOARD')
    m.ctl(b, 'digits', 'title', X(0.75), 27.6, w=29, h=1.3, col='5bcefa', label='', fmt='%s')
    for r in range(1, 7):
        v = 25.4 - (r - 1) * 1.95
        m.ctl(b, 'lamp', 'ra%d' % r, X(-13.2), v, w=0.6, h=0.6, col='30e060', label='')
        m.ctl(b, 'lamp', 'rc%d' % r, X(-12.2), v, w=0.6, h=0.6, col='ffb020', label='', blink=True)
        m.ctl(b, 'digits', 'r%d' % r, X(2.0), v, w=26, h=1.5, col='7cf29a', label='', fmt='%s')
    # ---- the forecast panel beside the board
    m.g('forecast panel')
    m.box((16.2, 14.0, 14.9), (30.0, 28.6, 15.4), 'paint:#24282c', 'forecast panel')
    m.box((16.6, 26.2, 14.82), (29.6, 28.2, 14.9), 'label:FORECAST', 'forecast title')
    for (x0, y0) in ((16.8, 21.8), (20.3, 21.8), (23.8, 21.8), (27.0, 21.8)):
        m.box((x0, y0, 14.84), (x0 + 2.8, y0 + 3.0, 14.9), 'paint:#1a1c1e', 'dial bezel')
    fp = m.surface('north', face=14.8, title='FORECAST')
    for (cid, x, lab) in (('g_snow', 18.2, 'SNOW MM'), ('g_ice', 21.7, 'ICE MM'), ('g_leaf', 25.2, 'LEAFY'), ('g_sand', 28.4, 'SANDITE')):
        m.ctl(fp, 'gauge', cid, X(x), 23.3, w=2.5, h=2.5, col='f2f0e6', label=lab, max=100)
    for i, (cid, col, lab, bl) in enumerate((('f_snow', 'f0f0ff', 'SNOW', True), ('f_ice', '80c0ff', 'ICE', True), ('f_leaf', 'ffb020', 'LEAF', True),
                                             ('f_wind', 'ff8020', 'WIND', False), ('f_cold', '3080ff', 'COLD', False), ('f_rain', '30a0ff', 'RAIN', False))):
        lamp(m, fp, cid, X(17.2 + i * 2.1), 20.4, col, lab, 0.75, bl)
    m.box((16.6, 14.4, 14.84), (29.6, 19.4, 14.9), 'paint:#03140a', 'forecast screen')
    for i in range(4):
        digits(m, fp, 'fc%d' % (i + 1), X(23.1), 18.6 - i * 1.2, 12.6, 0.9, ('ffd040', 'f0f0ff', 'ffb020', '80c0ff')[i])
    # ---- left end: the route map and the instruments of the weather office
    m.g('route map')
    m.box((-13.6, 9.8, 15.0), (-1.2, 13.4, 15.32), 'paint:#f4f2ea', 'plough beat map')
    for i in range(5):
        m.box((-13.0 + i * 2.4, 11.4, 14.98), (-11.0 + i * 2.4, 11.6, 15.0), 'paint:#2a2a2a', 'line')
        m.box((-11.2 + i * 2.4, 11.0, 14.97), (-10.6 + i * 2.4, 12.0, 15.0), ('paint:#2050d0', 'paint:#e0a020', 'paint:#20a040')[i % 3], 'beat marker')
    m.box((-13.4, 12.6, 14.97), (-6.0, 13.1, 15.0), 'label:PLOUGH BEATS', 'map title')
    m.g('instrument cabinet')
    m.box((-13.6, 0, 9.6), (-1.0, 9.0, 15.2), 'paint:#2c3440', 'instrument cabinet')
    m.box((-13.3, 0.4, 9.4), (-1.3, 8.6, 9.6), 'paint:#3a4452', 'cabinet front')
    for (x, lab) in ((-11.6, 'HYGRO'), (-7.4, 'BARO'), (-3.2, 'THERMO')):
        m.cyl((x, 6.4, 9.2), 1.3, 0.25, 'z', 'gauge:' + lab, 'weather instrument', cap='gauge:' + lab)
        m.box((x - 1.2, 4.6, 9.3), (x + 1.2, 5.0, 9.4), 'label:' + lab, 'instrument label')
    m.box((-13.0, 1.0, 9.3), (-1.6, 3.6, 9.4), 'paint:#101214', 'chart recorder window')
    m.box((-12.6, 1.4, 9.28), (-2.0, 3.2, 9.3), 'paint:#f4f2ea', 'chart paper')
    for i in range(10):
        m.box((-12.4 + i * 1.04, 1.8 + (i * 37 % 9) / 10.0, 9.26), (-11.6 + i * 1.04, 1.9 + (i * 37 % 9) / 10.0, 9.28), 'paint:#c02020', 'pen trace')
    m.box((-13.8, 9.0, 9.4), (-0.8, 9.4, 15.4), 'paint:#22282e', 'cabinet top')
    m.g('radio base station')
    m.box((-12.6, 9.4, 11.0), (-6.4, 11.4, 14.4), 'paint:#2a2c30', 'radio base station')
    m.box((-12.2, 10.4, 10.9), (-9.0, 11.2, 11.0), 'paint:#03140a', 'radio display')
    m.cyl((-7.4, 10.6, 10.8), 0.45, 0.2, 'z', 'paint:#c0c0c0', 'volume knob')
    m.box((-6.2, 9.4, 12.0), (-5.2, 10.6, 13.0), 'paint:#2a2c30', 'desk microphone base')
    m.box((-5.85, 10.6, 12.35), (-5.55, 12.0, 12.65), 'metal:steel', 'gooseneck')
    m.box((-6.1, 12.0, 12.1), (-5.3, 12.6, 12.9), 'paint:#1a1a1a', 'microphone')
    m.box((-4.6, 9.4, 11.4), (-1.8, 12.4, 12.0), 'paint:#f0c020', 'plough blade sample')
    m.box((-4.6, 9.4, 11.0), (-1.8, 9.8, 12.4), 'paint:#2a2a2a', 'blade stand')
    # ---- right end: SANDITE STOCK cupboard + the shelf
    SPOTS['wx_depot_desk'] = cupboard_shelf(m, 18.4, 29.4, ['sand'], 6, title='SHELF', body='#2c3440', door='#3a4452')
    m.box((19.0, 4.0, 11.36), (24.4, 5.0, 11.4), 'label:SANDITE STOCK', 'cupboard label')
    # ---- the controller's desk
    desk(m, {('l', 0): 'cards', ('l', 1): 'radios', ('l', 2): 'ppe', ('r', 0): 'forms', ('r', 1): 'keys', ('r', 2): 'spares'}, body='#2c3440', front='#3a4452', pull='metal:steel')
    sf = strip(m, 3.3, 8.7, col='#1e2329')
    buttons(m, sf, 8.1, [('p_plough', 'f0f0ff', 'PLOUGH'), ('p_sandite', 'e0a020', 'SANDITE'), ('p_deice', '80c0ff', 'DE-ICE'), ('dispatch', '30c040', 'DISPATCH'),
                         ('cancel', 'c02020', 'CANCEL'), ('prev', '505050', 'PREV'), ('next', '505050', 'NEXT'), ('print', 'f0f0f0', 'PRINT')], u0=1.0, step=1.45, size=0.8)
    buttons(m, sf, 6.2, [('ack', 'f0d020', 'ACK'), ('scan', '2050d0', 'SCAN'), ('test', 'f0f0f0', 'TEST')], u0=1.0, step=1.45, size=0.8)
    m.ctl(sf, 'selector', 's_range', 6.2, 6.1, w=1.1, h=1.1, col='202020', label='RANGE', pos=['128', '256', '512', '1024'])
    m.ctl(sf, 'selector', 's_thresh', 8.0, 6.1, w=1.1, h=1.1, col='202020', label='THRESH', pos=['LOW', 'MED', 'HIGH'])
    m.ctl(sf, 'selector', 's_view', 9.8, 6.1, w=1.1, h=1.1, col='202020', label='VIEW', pos=['FCST', 'TRAINS', 'LOG'])
    m.ctl(sf, 'key', 'k_auto', 11.8, 6.1, w=1.0, h=1.0, col='c0a040', label='AUTO', pos=['OFF', 'AUTO'])
    m.ctl(sf, 'key', 'k_lock', 13.5, 6.1, w=1.0, h=1.0, col='c0a040', label='DESK', pos=['OPEN', 'LOCK'])
    hd = hood(m, col='#1c2026')
    for i, (cid, col, lab, bl) in enumerate((('l_pwr', '30e060', '12V', False), ('l_mains', '30e060', 'MAINS', False), ('l_auto', '30e060', 'AUTO', False),
                                             ('l_alarm', 'ff3030', 'ALARM', True), ('l_lock', 'ffb020', 'LOCK', False), ('l_sand', 'ff8020', 'SAND LOW', True))):
        lamp(m, hd, cid, 1.3 + i * 2.4, 11.05, col, lab, 0.6, bl)
    monitor(m, 0.6, 7.8, 12.4, 17.8, 10.2, ['m1', 'm2', 'm3', 'm4', 'm5', 'm6'], cols=['5bcefa', 'f0f0f0', 'ffd040', '7cf29a', 'b8f0c8', 'ffd27a'])
    monitor(m, 8.2, 15.4, 12.4, 17.8, 10.2, ['p1', 'p2', 'p3', 'p4', 'p5', 'p6'], cols=['5bcefa'] + ['7cf29a'] * 5)
    m.g('desk things')
    m.box((4.2, 9.0, 8.85), (11.8, 9.25, 9.6), 'paint:#2a2c30', 'keyboard')
    for i in range(12):
        m.box((4.5 + i * 0.6, 9.25, 9.0), (4.95 + i * 0.6, 9.35, 9.45), 'paint:#d8d8d8', 'key cap')
    m.cyl((15.1, 9.0, 9.4), 0.35, 0.9, 'y', 'paint:#f0f0f0', 'tea mug')
    m.box((0.8, 9.0, 8.9), (3.4, 9.4, 10.4), 'paint:#0e3a5c', 'winter working book')
    m.box((12.4, 9.0, 9.0), (13.0, 9.2, 9.8), 'paint:#2a2c30', 'mouse')
    m.box((13.6, 9.0, 11.4), (15.6, 9.6, 12.8), 'paint:#2a2c30', 'phone base')
    m.cyl((13.6, 10.05, 11.7), 0.3, 2.0, 'x', 'paint:#2a2c30', 'handset')
    m.g('hi-vis hook')
    m.box((30.0, 14.0, 13.8), (30.4, 14.6, 15.4), 'metal:steel', 'coat hook')
    m.box((29.8, 9.8, 13.4), (30.6, 14.2, 15.0), 'paint:#f0e020', 'hi-vis vest')
    m.box((29.78, 11.0, 13.4), (30.62, 11.4, 15.0), 'paint:#d0d0d0', 'reflective band')
    m.g('floor')
    m.box((1.0, 0, 0.4), (15.0, 0.12, 3.2), 'paint:#2a2a2a', 'anti-fatigue mat')
    m.cyl((17.2, 0, 4.0), 0.9, 2.6, 'y', 'paint:#4a5866', 'waste bin')
    m.terminal_12v(16.2, 1.0, 8.0, 'east')
    m.gland(16.2, 0.4, 6.0, 'east', r=0.35, length=0.6)
    m.save(item_gui=0.28)
    COUNTS['wx_depot_desk'] = len(m.boxes)


# ============================================================================================================ icons
def icon(name, draw):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    d = os.path.join(ASSETS, 'textures/items')
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name + '.png'))
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + name}}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def battery_icon(d):
    d.rectangle((3, 2, 12, 14), fill=(44, 48, 54, 255), outline=(16, 16, 18, 255))
    d.rectangle((5, 1, 10, 2), fill=(240, 192, 32, 255))
    d.rectangle((5, 4, 10, 6), fill=(230, 230, 226, 255))
    for i, c in enumerate(((60, 220, 110), (60, 220, 110), (60, 220, 110), (240, 180, 40))):
        d.point((5 + i * 2, 12), fill=c + (255,))
    d.rectangle((4, 8, 11, 9), fill=(232, 96, 28, 255))


def controller_icon(d):
    d.rectangle((1, 6, 14, 12), fill=(40, 42, 46, 255), outline=(16, 16, 18, 255))
    d.rectangle((4, 1, 11, 6), fill=(20, 40, 30, 255), outline=(80, 84, 90, 255))
    d.line((5, 3, 10, 3), fill=(90, 230, 130, 255))
    d.point((7, 4), fill=(255, 176, 32, 255))
    d.ellipse((2, 8, 5, 11), fill=(90, 94, 100, 255))
    d.ellipse((10, 8, 13, 11), fill=(90, 94, 100, 255))
    d.point((3, 9), fill=(20, 20, 20, 255))
    d.point((11, 9), fill=(20, 20, 20, 255))
    d.line((14, 6, 15, 1), fill=(30, 30, 30, 255))


def plough_icon(d):
    d.polygon([(1, 13), (8, 4), (15, 13)], fill=(240, 196, 24, 255), outline=(30, 30, 30, 255))
    d.line((8, 4, 8, 13), fill=(30, 30, 30, 255))
    d.rectangle((6, 13, 10, 15), fill=(60, 62, 66, 255))
    for x in (3, 13):
        d.point((x, 11), fill=(250, 250, 255, 255))
    d.point((2, 9), fill=(250, 250, 255, 255))
    d.point((14, 8), fill=(250, 250, 255, 255))


# ============================================================================================================ data
def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


I, R, G, C = 'minecraft:iron_ingot', 'minecraft:redstone', 'minecraft:glass_pane', 'minecraft:comparator'
RECIPES = {
    'drone_dock': (['RGR', 'CDC', 'III'], {'R': 'minecraft:redstone_lamp', 'G': G, 'C': C, 'D': 'minecraft:dispenser', 'I': I}, 1),
    'drone': (['F F', ' E ', 'F F'], {'F': 'minecraft:feather', 'E': 'minecraft:ender_eye'}, 1),
    'drone_battery': (['IRI', 'RGR', 'IRI'], {'I': I, 'R': 'minecraft:redstone_block', 'G': 'minecraft:gold_ingot'}, 1),
    'drone_controller': ([' T ', 'IGI', 'BRB'], {'T': 'minecraft:redstone_torch', 'I': I, 'G': G, 'B': 'minecraft:stone_button', 'R': R}, 1),
    'snow_plough': (['I I', 'III', ' B '], {'I': I, 'B': 'minecraft:iron_block'}, 1),
    'wx_depot_desk': (['LML', 'PCP', 'WSW'], {'L': 'minecraft:redstone_lamp', 'M': 'minecraft:map', 'P': 'minecraft:paper', 'C': C, 'W': 'minecraft:planks', 'S': 'minecraft:chest'}, 1),
}
DATA0 = {'minecraft:planks'}
LANG = {
    'tile.irextras.drone_dock.name': 'Drone Dock',
    'item.irextras.drone.name': 'Inspection Drone',
    'item.irextras.drone_battery.name': 'Drone Flight Battery (500 Wh)',
    'item.irextras.drone_controller.name': 'Drone Handheld Controller',
    'entity.irextras.inspection_drone.name': 'Inspection Drone',
    'tile.irextras.wx_depot_desk.name': 'Weather Train Depot Desk',
    'item.irextras.snow_plough.name': 'Snowplough Attachment',
}

if __name__ == '__main__':
    drone_dock()
    drone_body(); drone_rotor(); drone_gimbal()
    wx_depot_desk()
    for n in ('drone_dock', 'wx_depot_desk'):
        facing_states(n)
    for n in ('drone_body', 'drone_rotor', 'drone_gimbal'):     # moving parts: baked as item variants, drawn by DroneClient
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item', n + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/drone_body', 'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 2, 0], 'scale': [0.42] * 3},
                                                                 'ground': {'translation': [0, 3, 0], 'scale': [0.3] * 3}}},
              open(os.path.join(ASSETS, 'models/item/drone.json'), 'w'), indent=1)
    icon('drone_battery', battery_icon)
    icon('drone_controller', controller_icon)
    icon('snow_plough', plough_icon)
    ing = lambda v: {'item': v, 'data': 0} if v in DATA0 else {'item': v}
    for n, (pat, key, cnt) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': cnt}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# IR Extras #2 inspection drones + #4 snowploughs and weather trains\n' + '\n'.join(add) + '\n')
    for k, v in COUNTS.items(): print('  %-16s %d boxes' % (k, v))
    print('shelf spots (TileWxDepot.SPOTS):', SPOTS)
