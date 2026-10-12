#!/usr/bin/env python3
"""Batch 3 (her ChatGPT list #36-50) electrification + permanent-way gear: wayside energy store, OHLE fault locator,
relay test set, rail grinder, rail welder, flood warning outstation, OHLE recording station. Front = north. Every lamp /
button / key / selector / lever / dial / display / zone is a live control the Java tile (com.dogpound.railmap.infra)
reads or works. On north surfaces u = 16 - x (from the viewer's left). 12 V boxes are wire terminals."""
import json, os
from bbkit import Model, ASSETS

LIGHT = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
PANEL = 'paint:#d4d6d2'
YELLOW = 'paint:#f0c020'
ORANGE = 'paint:#e8601c'
NETWORK_RAIL = 'paint:#e8601c'


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label, s=0.6, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def digits(m, sf, cid, u, v, w, h=0.7, col='7cf29a'):
    m.ctl(sf, 'digits', cid, u, v, w=w, h=h, col=col, label='', fmt='%s')


def bezel(m, x0, y0, x1, y1, z, mat='paint:#1a1c1e'):
    """a recessed display window frame on a north face at z (the game draws the display inside)"""
    m.box((x0 - 0.15, y0 - 0.15, z - 0.12), (x1 + 0.15, y0, z), mat, 'bezel', faces=('north', 'up', 'down', 'east', 'west'))
    m.box((x0 - 0.15, y1, z - 0.12), (x1 + 0.15, y1 + 0.15, z), mat, 'bezel', faces=('north', 'up', 'down', 'east', 'west'))
    m.box((x0 - 0.15, y0, z - 0.12), (x0, y1, z), mat, 'bezel', faces=('north', 'east', 'west'))
    m.box((x1, y0, z - 0.12), (x1 + 0.15, y1, z), mat, 'bezel', faces=('north', 'east', 'west'))


def hinge_door(m, x0, x1, y0, y1, z, mat, hinge_left=True, name='door'):
    m.box((x0, y0, z - 0.2), (x1, y1, z), mat, name)
    hx = x1 if hinge_left else x0
    for y in (y0 + 1.0, (y0 + y1) / 2, y1 - 1.6):
        m.box((hx - 0.25, y, z - 0.35), (hx + 0.25, y + 0.7, z), 'metal:steel', 'hinge')
    lx = x0 + 0.5 if hinge_left else x1 - 0.8
    m.box((lx, (y0 + y1) / 2 - 1.0, z - 0.5), (lx + 0.3, (y0 + y1) / 2 + 1.0, z - 0.2), 'metal:steel', 'door handle')
    m.box((lx - 0.1, (y0 + y1) / 2 + 1.3, z - 0.3), (lx + 0.4, (y0 + y1) / 2 + 1.7, z - 0.2), 'metal:brass', 'lock')


# ------------------------------------------------------------------------------------------------ #38 wayside ESS
def wayside_ess():
    m = Model('wayside_ess')
    m.g('foundation')
    m.box((0, 0, 0), (16, 0.8, 16), 'concrete', 'plinth')
    for x in (0.4, 15.0):
        for z in (0.4, 15.0):
            m.box((x, 0.8, z), (x + 0.6, 1.2, z + 0.6), 'galv', 'twist lock')
    m.g('container')
    m.box((0.5, 1.0, 0.5), (15.5, 14.6, 15.5), 'ral7035', 'container body')
    m.box((0.3, 14.6, 0.3), (15.7, 15.2, 15.7), 'ral7035', 'roof')
    m.box((0.3, 0.8, 0.3), (15.7, 1.2, 15.7), 'paint:#3a3d40', 'bottom rail')
    for x in (0.3, 15.2):                                               # corner posts
        for z in (0.3, 15.2):
            m.box((x, 1.2, z), (x + 0.5, 14.6, z + 0.5), 'paint:#b9bbb7', 'corner post')
    for i in range(14):                                                 # corrugated sides
        z = 1.2 + i * 1.0
        m.box((15.5, 1.4, z), (15.75, 14.4, z + 0.5), 'ral7035', 'corrugation E')
        m.box((0.25, 1.4, z), (0.5, 14.4, z + 0.5), 'ral7035', 'corrugation W')
    for i in range(13):
        x = 1.4 + i * 1.0
        m.box((x, 1.4, 15.5), (x + 0.5, 14.4, 15.75), 'ral7035', 'corrugation S')
    m.g('front doors')
    # left (viewer's right) door: the HMI; right door: inspection window onto the racks
    m.box((0.8, 1.4, 0.3), (7.85, 14.3, 0.5), PANEL, 'HMI door')
    m.box((8.15, 1.4, 0.3), (15.2, 14.3, 0.5), PANEL, 'rack door')
    for (x0, x1) in ((8.15, 15.2),):
        for xb in (x0 + 0.25, x1 - 0.55):
            m.box((xb, 1.6, 0.0), (xb + 0.3, 14.1, 0.3), 'metal:steel', 'locking bar')
            m.box((xb - 0.2, 7.0, -0.25), (xb + 0.5, 8.6, 0.0), 'metal:steel', 'locking handle')
            for y in (1.7, 13.6):
                m.box((xb - 0.15, y, 0.0), (xb + 0.45, y + 0.5, 0.3), 'metal:steel', 'cam keeper')
    m.g('inspection window + racks')
    m.box((9.2, 5.6, 0.25), (14.1, 13.6, 0.3), 'glass', 'window')
    bezel(m, 9.2, 5.6, 14.1, 13.6, 0.3)
    for col in range(4):
        for row in range(6):
            x0 = 9.4 + col * 1.18
            y0 = 5.9 + row * 1.25
            m.box((x0, y0, 1.0), (x0 + 1.05, y0 + 1.1, 3.0), 'battery', 'battery module')
            m.box((x0 + 0.15, y0 + 0.75, 0.95), (x0 + 0.4, y0 + 0.95, 1.0), 'paint:#30e060', 'module LED')
            m.box((x0 + 0.55, y0 + 0.2, 0.95), (x0 + 0.9, y0 + 0.4, 1.0), 'paint:#c81e1e', 'module + bus')
    for x in (9.3, 14.0):
        m.box((x, 5.7, 1.0), (x + 0.15, 13.5, 3.2), 'paint:#2b2f33', 'rack upright')
    m.box((9.3, 13.4, 1.0), (14.15, 13.5, 3.2), 'paint:#2b2f33', 'rack top')
    m.g('rack door lower strip')
    m.box((8.6, 1.8, 0.2), (14.8, 5.2, 0.3), 'paint:#bfc1bd', 'control strip')
    m.box((8.8, 4.6, 0.15), (14.6, 5.0, 0.2), 'label:PEAK LOPPING', 'strip legend', faces=('north',))
    m.g('signs')
    m.box((1.0, 13.6, 0.25), (4.6, 14.2, 0.3), 'label:BESS', 'legend', faces=('north',))
    m.box((4.8, 13.4, 0.25), (7.6, 14.2, 0.3), 'danger:DANGER|DC', 'danger', faces=('north',))
    m.box((9.2, 13.8, 0.25), (11.4, 14.2, 0.3), 'label:LI-ION', 'chem plate', faces=('north',))
    m.box((11.6, 13.7, 0.2), (12.6, 14.25, 0.3), 'warn', 'warning triangle', faces=('north',))
    m.box((12.8, 13.8, 0.25), (15.0, 14.2, 0.3), 'label:FM200', 'suppression plate', faces=('north',))
    m.nameplate(1.2, 1.6, 4.4, 2.2, 0.3, 'PCS 2.5MW')
    m.g('HVAC')
    m.box((2.0, 15.2, 2.0), (14.0, 17.4, 8.0), 'ral7035', 'HVAC unit')
    for x in (4.5, 11.5):
        m.cyl((x, 17.4, 5.0), 2.0, 0.3, 'y', 'mesh', 'fan guard')
        m.cyl((x, 17.0, 5.0), 0.4, 0.5, 'y', 'dark', 'fan hub')
        for a in range(3):
            m.box((x - 1.7, 17.05, 4.8), (x + 1.7, 17.15, 5.2), 'dark', 'fan blade', rot={'axis': 'y', 'angle': (-45, 0, 45)[a], 'origin': [x, 17.1, 5.0]})
    m.box((2.0, 15.4, 1.9), (14.0, 17.2, 2.0), 'fins', 'condenser coil', faces=('north',))
    m.box((6.0, 15.2, 8.0), (7.0, 15.8, 12.0), 'paint:#3a3d40', 'refrigerant line')
    m.box((9.0, 15.2, 8.0), (10.0, 15.8, 12.0), 'paint:#3a3d40', 'refrigerant line')
    m.g('fire suppression')
    m.cyl((13.6, 15.2, 12.6), 0.8, 2.2, 'y', 'paint:#c81e1e', 'FM200 bottle')
    m.cyl((13.6, 17.4, 12.6), 0.3, 0.4, 'y', 'metal:brass', 'bottle valve')
    m.box((13.4, 17.2, 10.0), (13.8, 17.6, 12.6), 'metal:copper', 'discharge pipe')
    m.g('PCS cabling')
    m.hv_box(8.0, 2.0, 15.75, 'south', phases=2)
    for z in (4.0, 8.0, 12.0):
        m.gland(15.75, 3.0, z, 'east', r=0.4, length=0.6)
    m.box((15.75, 5.0, 2.0), (16.0, 12.0, 6.0), 'louvre', 'PCS vent', faces=('east',))
    m.box((15.75, 5.0, 10.0), (16.0, 12.0, 14.0), 'louvre', 'PCS vent', faces=('east',))
    m.terminal_12v(0.25, 4.0, 8.0, 'west')
    m.g('earthing')
    m.box((15.2, 0.8, 0.6), (15.6, 1.6, 1.0), 'metal:copper', 'earth boss')
    m.cyl((15.4, 0.0, 0.8), 0.15, 0.8, 'y', 'cable:3a7d2a', 'earth lead')
    # ---- the HMI (left door, viewer's left: u 8.2 .. 15.2) and the peak strip
    f = m.surface('north', face=0.17, title='BESS HMI')
    for cid, v, col in (('mode', 13.0, 'ffd040'), ('soc', 12.2, '7cf29a'), ('pwrk', 11.4, '30a0ff'), ('temp', 10.6, 'ffb020'), ('soh', 9.8, '7cf29a'), ('imb', 9.0, 'b8f0c8')):
        digits(m, f, cid, 11.7, v, 6.2, 0.65, col)
    bezel(m, 1.2, 8.55, 7.45, 13.4, 0.3)
    m.ctl(f, 'gauge', 'g_soc', 9.7, 6.9, w=2.2, h=2.2, col='f2f0e6', label='SOC %', max=100)
    m.ctl(f, 'gauge', 'g_temp', 13.6, 6.9, w=2.2, h=2.2, col='f2f0e6', label='CELL C', max=80)
    for i, (cid, lab, col, bl) in enumerate((('chg', 'CHARGE', '30e060', False), ('dis', 'DISCH', 'ffb020', False), ('hot', 'HOT', 'ff8020', True),
                                             ('bms', 'BMS', 'ff3030', True), ('eol', 'SOH LOW', 'ffb020', False))):
        lamp(m, f, cid, 8.8 + i * 1.5, 4.9, col, lab, 0.55, blink=bl)
    for i, (cid, lab, col, bl) in enumerate((('balr', 'BALANCE', '3080ff', True), ('hvac', 'HVAC', '30e060', False), ('trip', 'TRIP', 'ff3030', True),
                                             ('pcs', 'PCS', '30e060', False), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 8.8 + i * 1.5, 3.8, col, lab, 0.55, blink=bl)
    m.ctl(f, 'selector', 's_tech', 9.0, 2.2, w=1.1, h=1.1, col='202020', label='MODULES', pos=['LI-ION', 'SUPERCAP', 'HYBRID'])
    m.ctl(f, 'selector', 's_mode', 11.0, 2.2, w=1.1, h=1.1, col='202020', label='MODE', pos=['AUTO', 'REGEN', 'SUPPORT', 'OFF'])
    m.ctl(f, 'selector', 's_hvac', 13.0, 2.2, w=1.1, h=1.1, col='202020', label='HVAC', pos=['AUTO', 'ON', 'OFF'])
    m.ctl(f, 'key', 'k_iso', 14.7, 2.2, w=0.9, h=0.9, col='c0a040', label='ISOL', pos=['ONLINE', 'ISOL'])
    # rack-door strip (viewer's right: u 0.8 .. 7.8)
    digits(m, f, 'peak', 4.2, 4.0, 5.0, 0.6, 'ffd040')
    digits(m, f, 'pcsv', 4.2, 3.2, 5.0, 0.55, 'b8f0c8')
    m.ctl(f, 'push', 'pk_dn', 2.2, 2.2, w=0.8, h=0.8, col='f0f0f0', label='PK-')
    m.ctl(f, 'push', 'pk_up', 3.6, 2.2, w=0.8, h=0.8, col='f0f0f0', label='PK+')
    m.ctl(f, 'push', 'balance', 5.2, 2.2, w=0.8, h=0.8, col='2050d0', label='BALANCE')
    m.ctl(f, 'push', 'reset', 6.7, 2.2, w=0.8, h=0.8, col='f0d020', label='RESET')
    m.ctl(f, 'push', 'test', 7.05, 4.0, w=0.45, h=0.45, col='f0f0f0', label='TEST')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 1.5, 14.5, 7.0, 14.0, 0.25, side='south', hv=True, title='WAYSIDE ESS|BESS', volts='750V DC')
    m.save(item_gui=0.45)
    facing_states('wayside_ess')


# ------------------------------------------------------------------------------------------------ #39 fault locator
def ohle_fault_locator():
    m = Model('ohle_fault_locator')
    m.g('cubicle')
    m.box((1.0, 0, 4.0), (15.0, 0.8, 16.0), 'paint:#1c1d20', 'plinth')
    m.box((1.0, 0.8, 4.4), (15.0, 15.4, 16.0), 'paint:#c9cbc7', 'cubicle body')
    m.box((0.8, 15.4, 4.2), (15.2, 16.0, 16.0), 'paint:#c9cbc7', 'cubicle roof')
    for x in (1.0, 14.4):
        m.box((x, 0.8, 4.1), (x + 0.6, 15.4, 4.4), 'paint:#b9bbb7', 'frame upright')
    m.box((1.0, 0.8, 16.0), (15.0, 15.4, 16.0), 'paint:#c9cbc7', 'back', faces=('south',))
    m.g('front panel')
    m.box((1.6, 9.4, 4.2), (14.4, 15.2, 4.4), 'paint:#3a3f44', 'relay front plate')
    m.box((1.6, 5.4, 4.2), (14.4, 9.2, 4.4), 'paint:#2b2f33', 'control plate')
    m.nameplate(1.8, 14.7, 7.2, 15.1, 4.2, 'OHLE 21FL')
    m.box((9.0, 14.7, 4.1), (14.2, 15.1, 4.2), 'label:FAULT LOCATOR', 'legend', faces=('north',))
    for (x0, y0, x1, y1) in ((2.0, 13.0, 14.0, 14.4), (2.0, 10.3, 14.0, 12.8)):
        m.box((x0, y0, 4.15), (x1, y1, 4.2), 'paint:#050805', 'LCD glass', faces=('north',))
        bezel(m, x0, y0, x1, y1, 4.2)
    for x in (1.8, 14.0):
        for y in (9.6, 14.9):
            m.box((x, y, 4.0), (x + 0.3, y + 0.3, 4.2), 'metal:steel', 'panel screw')
    m.g('relay modules')
    for i in range(4):
        x0 = 1.8 + i * 3.15
        m.box((x0, 2.6, 4.2), (x0 + 3.0, 5.2, 4.45), 'paint:#2b2f33', 'relay case')
        m.box((x0 + 0.2, 4.4, 4.05), (x0 + 1.8, 4.9, 4.2), 'paint:#050805', 'case LCD', faces=('north',))
        for j in range(4):
            m.box((x0 + 0.3 + j * 0.6, 3.6, 4.1), (x0 + 0.6 + j * 0.6, 3.9, 4.2), ('paint:#30e060', 'paint:#ffb020', 'paint:#ff3030', 'paint:#30e060')[j], 'case LED')
        m.box((x0 + 2.2, 2.9, 4.0), (x0 + 2.7, 4.9, 4.2), 'metal:alu', 'case handle')
        m.box((x0 + 0.3, 2.8, 4.1), (x0 + 1.9, 3.3, 4.2), 'paint:#c81e1e', 'test block')
    m.g('MCBs + terminals')
    m.box((1.8, 1.0, 4.3), (14.2, 2.4, 4.6), 'paint:#5d6b5a', 'DIN rail')
    for i in range(10):
        x0 = 2.0 + i * 1.2
        m.box((x0, 1.1, 4.1), (x0 + 1.0, 2.3, 4.3), 'white', 'MCB')
        m.box((x0 + 0.35, 1.5, 3.95), (x0 + 0.65, 1.95, 4.1), 'paint:#202020', 'MCB toggle')
    m.box((1.6, 0.9, 4.3), (14.4, 1.0, 5.0), 'paint:#808488', 'wiring duct')
    m.g('cabling')
    for x in (3.0, 6.0, 9.0, 12.0):
        m.gland(x, 0.8, 10.0, 'down', r=0.35, length=0.6)
    m.box((14.9, 3.0, 6.0), (15.3, 10.0, 7.0), 'paint:#808488', 'side duct')
    m.terminal_12v(15.0, 11.0, 10.0, 'east')
    f = m.surface('north', face=4.13, title='OHLE FAULT LOCATOR')
    digits(m, f, 'rec', 8.0, 14.0, 11.4, 0.5, 'b8f0c8')
    digits(m, f, 'dist', 8.0, 13.35, 11.4, 0.6, 'ffb020')
    digits(m, f, 'z', 5.2, 12.4, 5.4, 0.55, '7cf29a')
    digits(m, f, 'rx', 10.9, 12.4, 5.4, 0.55, '7cf29a')
    digits(m, f, 'ika', 5.2, 11.7, 5.4, 0.55, 'ffd040')
    digits(m, f, 'kind', 10.9, 11.7, 5.4, 0.55, 'ff8040')
    digits(m, f, 'loc', 5.2, 11.0, 5.4, 0.55, '30a0ff')
    digits(m, f, 'crew', 10.9, 11.0, 5.4, 0.55, '30e0e0')
    m.ctl(f, 'gauge', 'g_dist', 3.4, 7.5, w=2.4, h=2.4, col='f2f0e6', label='ALONG %', max=100)
    for i, (cid, lab, col, bl) in enumerate((('trip', 'NEW', 'ff3030', True), ('dbl', '2-END', '30e060', False), ('disp', 'CREW', 'ffb020', False), ('site', 'SITE', 'ffb020', False),
                                             ('iso', 'ISOLATE', 'ff3030', True), ('done', 'CLEAR', '30e060', False), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 6.0 + i * 1.3, 8.6, col, lab, 0.5, blink=bl)
    m.ctl(f, 'selector', 's_meth', 6.2, 6.4, w=1.1, h=1.1, col='202020', label='METHOD', pos=['AUTO', 'SINGLE', 'DOUBLE'])
    m.ctl(f, 'push', 'prev', 8.3, 6.4, w=0.8, h=0.8, col='f0f0f0', label='PREV')
    m.ctl(f, 'push', 'next', 9.7, 6.4, w=0.8, h=0.8, col='f0f0f0', label='NEXT')
    m.ctl(f, 'push', 'dispatch', 11.5, 6.4, w=1.1, h=1.1, col='2050d0', label='DISPATCH')
    m.ctl(f, 'push', 'ack', 13.3, 6.4, w=0.8, h=0.8, col='f0d020', label='ACK')
    m.ctl(f, 'push', 'test', 13.6, 7.9, w=0.4, h=0.4, col='f0f0f0', label='TEST')
    m.save(item_gui=0.5)
    facing_states('ohle_fault_locator')


# ------------------------------------------------------------------------------------------------ #40 relay test set
def relay_test_set():
    m = Model('relay_test_set')
    m.g('trolley')
    for (x, z) in ((2.6, 3.0), (13.4, 3.0), (2.6, 13.0), (13.4, 13.0)):
        m.box((x - 0.35, 0.0, z - 0.8), (x + 0.35, 1.6, z + 0.8), 'rubber', 'castor wheel')
        m.box((x - 0.5, 1.6, z - 0.5), (x + 0.5, 2.0, z + 0.5), 'metal:steel', 'castor fork')
    m.box((2.0, 2.0, 2.4), (14.0, 2.6, 13.6), 'paint:#2b2f33', 'bottom shelf')
    m.box((2.0, 7.4, 2.4), (14.0, 8.0, 13.6), 'paint:#2b2f33', 'top shelf')
    for (x, z) in ((2.0, 2.4), (13.5, 2.4), (2.0, 13.1), (13.5, 13.1)):
        m.box((x, 2.0, z), (x + 0.5, 8.0, z + 0.5), 'metal:alu', 'trolley post')
    m.handrail([(2.25, 13.6), (13.75, 13.6)], 8.0, h=5.5, r=0.22, mat='metal:alu')
    m.g('lead drawer (bottom shelf)')
    m.box((2.6, 2.6, 3.0), (13.4, 5.0, 13.0), 'paint:#3a3d40', 'lead box')
    for i, col in enumerate(('c81e1e', 'f0c020', '2050d0', '1d1d21', '30a030', 'c81e1e', '1d1d21')):
        z0 = 4.0 + i * 1.2
        m.cyl((4.0, 5.0, z0), 0.25, 0.4, 'y', 'cable:' + col, 'coiled lead')
        m.box((3.0, 5.0, z0 - 0.3), (12.0, 5.3, z0 + 0.3), 'cable:' + col, 'test lead')
    m.box((2.6, 5.0, 2.8), (13.4, 7.2, 3.0), 'paint:#3a3d40', 'drawer front')
    m.box((6.5, 5.8, 2.6), (9.5, 6.2, 2.8), 'metal:alu', 'drawer handle')
    m.g('test set')
    m.box((2.6, 8.0, 3.2), (13.4, 14.6, 12.6), 'paint:#3c4650', 'test set case')
    m.box((2.6, 8.0, 2.6), (13.4, 14.6, 3.2), 'paint:#d4d6d2', 'front panel')
    for x in (2.4, 13.4):
        m.box((x, 9.0, 4.0), (x + 0.2, 13.6, 11.0), 'rubber', 'side bumper')
    m.box((6.0, 14.6, 6.0), (10.0, 15.2, 8.0), 'paint:#2b2f33', 'carry handle base')
    m.box((6.4, 15.2, 6.6), (9.6, 15.8, 7.4), 'paint:#2b2f33', 'carry handle')
    m.box((3.0, 11.6, 2.55), (9.4, 14.3, 2.6), 'paint:#050805', 'display glass', faces=('north',))
    bezel(m, 3.0, 11.6, 9.4, 14.3, 2.6)
    m.box((10.0, 14.3, 2.55), (13.1, 14.55, 2.6), 'label:3PH TEST SET', 'legend', faces=('north',))
    m.g('output sockets')
    for i, col in enumerate(('c81e1e', 'f0c020', '2050d0', '1d1d21')):             # current outputs I1 I2 I3 N
        x = 3.4 + i * 0.9
        m.cyl((x, 8.6, 2.2), 0.3, 0.4, 'z', 'paint:#' + col, 'current socket')
        m.cyl((x, 8.6, 2.0), 0.15, 0.2, 'z', 'metal:brass', 'socket bore')
    for i, col in enumerate(('c81e1e', 'f0c020', '2050d0', '1d1d21')):             # voltage outputs
        x = 7.4 + i * 0.9
        m.cyl((x, 8.6, 2.2), 0.25, 0.4, 'z', 'paint:#' + col, 'voltage socket')
    for i in range(4):                                                         # binary inputs (relay trip contacts)
        x = 11.0 + i * 0.6
        m.cyl((x, 8.6, 2.3), 0.2, 0.3, 'z', 'paint:#30a030', 'binary input')
    m.box((3.0, 9.2, 2.55), (6.6, 9.5, 2.6), 'label:I OUT', 'socket legend', faces=('north',))
    m.box((7.0, 9.2, 2.55), (10.4, 9.5, 2.6), 'label:V OUT', 'socket legend', faces=('north',))
    m.box((10.8, 9.2, 2.55), (13.0, 9.5, 2.6), 'label:BIN IN', 'socket legend', faces=('north',))
    m.g('leads to the relay')
    for i, col in enumerate(('c81e1e', 'f0c020', '2050d0', '1d1d21')):
        x = 3.4 + i * 0.9
        m.box((x - 0.12, 8.2, 1.4), (x + 0.12, 8.6, 2.0), 'cable:' + col, 'plugged lead')
        m.box((x - 0.12, 2.0, 1.2), (x + 0.12, 8.2, 1.45), 'cable:' + col, 'hanging lead')
    m.g('laptop')
    m.box((3.0, 14.6, 8.4), (8.6, 14.8, 12.0), 'paint:#2b2f33', 'laptop base')
    m.box((3.0, 14.8, 11.8), (8.6, 18.4, 12.0), 'paint:#2b2f33', 'laptop lid')
    m.box((3.3, 15.1, 11.75), (8.3, 18.1, 11.8), 'screen', 'laptop screen', faces=('north',))
    m.box((3.4, 14.8, 8.8), (8.2, 14.85, 10.8), 'paint:#16181a', 'keyboard', faces=('up',))
    m.g('mains')
    m.box((13.4, 9.0, 10.0), (13.9, 10.4, 11.4), 'paint:#1d1d21', 'IEC inlet')
    m.gland(13.9, 9.7, 10.7, 'east', r=0.3, cable='cable:1d1d21', length=1.2)
    m.cyl((15.1, 2.0, 10.7), 0.25, 7.7, 'y', 'cable:1d1d21', 'mains lead')
    m.terminal_12v(2.0, 3.0, 8.0, 'west')
    f = m.surface('north', face=2.53, title='RELAY TEST SET')
    digits(m, f, 'ied', 9.8, 14.0, 6.0, 0.45, 'b8f0c8')
    digits(m, f, 'set', 9.8, 13.45, 6.0, 0.45, '7cf29a')
    digits(m, f, 'inj', 9.8, 12.9, 6.0, 0.45, 'ffd040')
    digits(m, f, 'time', 9.8, 12.35, 6.0, 0.45, '30a0ff')
    digits(m, f, 'err', 9.8, 11.85, 6.0, 0.4, 'ffb020')
    digits(m, f, 'res', 4.5, 13.6, 3.2, 0.5, 'ffffff')
    digits(m, f, 'cal', 4.5, 13.0, 3.2, 0.4, 'ffb020')
    m.ctl(f, 'gauge', 'g_amp', 4.5, 11.5, w=1.8, h=1.8, col='f2f0e6', label='I x10', max=100)
    m.ctl(f, 'push', 'test', 6.1, 12.6, w=0.35, h=0.35, col='f0f0f0', label='')
    for i, (cid, lab, col, bl) in enumerate((('run', 'RUN', '3080ff', True), ('pass', 'PASS', '30e060', False), ('fail', 'FAIL', 'ff3030', False), ('op', 'TRIP', 'ff8020', False))):
        lamp(m, f, cid, 6.9 + i * 1.15, 11.1, col, lab, 0.42, blink=bl)
    for i, (cid, lab, col, bl) in enumerate((('isol', 'ISOL', '30e060', False), ('livet', 'LIVE', 'ff3030', True), ('sup', 'MAINS', '30e060', False), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 6.9 + i * 1.15, 10.15, col, lab, 0.42, blink=bl)
    m.ctl(f, 'selector', 's_mode', 3.4, 10.0, w=0.9, h=0.9, col='202020', label='MODE', pos=['PICKUP', 'TIMING', 'FULL', 'CALIB'])
    m.ctl(f, 'key', 'k_block', 5.1, 10.0, w=0.75, h=0.75, col='c0a040', label='TEST BLK', pos=['LIVE', 'ISOL'])
    m.ctl(f, 'push', 'select', 11.7, 11.1, w=0.6, h=0.6, col='f0f0f0', label='IED')
    m.ctl(f, 'push', 'start', 12.9, 11.1, w=0.7, h=0.7, col='30c050', label='START')
    m.ctl(f, 'push', 'stop', 11.7, 10.15, w=0.6, h=0.6, col='d02020', label='STOP')
    m.ctl(f, 'push', 'print', 12.9, 10.15, w=0.6, h=0.6, col='2050d0', label='PRINT')
    m.save(item_gui=0.5)
    facing_states('relay_test_set')


# ------------------------------------------------------------------------------------------------ #42 on-track machines
RAIL_TOP = -13.6           # the rail head inside the rail block below (the machine block sits on top of the rail block)
GAUGE_X = (-3.5, 19.5)     # standard gauge: rail centres 8 ± 11.5 px


def track_chassis(m, body_col, z0=-6.0, z1=22.0):
    """frame, two wheelsets on the rails, buffers, steps and handrails, cab with windows; returns nothing"""
    m.g('wheelsets')
    for z in (z0 + 5.0, z1 - 5.0):
        cy = RAIL_TOP + 5.0
        for x in GAUGE_X:
            m.cyl((x - 0.7, cy, z), 5.0, 1.4, 'x', 'metal:steel', 'wheel')
            m.cyl((x - 1.3 if x > 8 else x + 0.7, cy, z), 5.6, 0.6, 'x', 'metal:steel', 'wheel flange')
            m.cyl((x + (1.0 if x > 8 else -2.0), cy, z), 1.2, 1.0, 'x', 'paint:#2b2f33', 'axle box')
        m.cyl((GAUGE_X[0] - 1.0, cy, z), 0.7, GAUGE_X[1] - GAUGE_X[0] + 2.0, 'x', 'metal:steel', 'axle')
        for x in (GAUGE_X[0] - 1.6, GAUGE_X[1] + 0.6):
            for dz in (-1.6, 0.9):
                m.cyl((x + 0.5, cy + 1.2, z + dz + 0.35), 0.5, 2.4, 'y', 'paint:#c81e1e', 'suspension spring')
    m.g('frame')
    m.box((-5.0, -6.0, z0), (-3.0, -3.4, z1), body_col, 'solebar W')
    m.box((19.0, -6.0, z0), (21.0, -3.4, z1), body_col, 'solebar E')
    m.box((-5.0, -6.0, z0), (21.0, -3.4, z0 + 1.0), body_col, 'headstock front')
    m.box((-5.0, -6.0, z1 - 1.0), (21.0, -3.4, z1), body_col, 'headstock rear')
    for z in (z0 + 0.5, z1 - 0.5):
        m.box((-5.0, -6.0, z - 0.25), (21.0, -5.6, z + 0.25), 'haz', 'buffer beam stripe', faces=('north', 'south'))
    for x in (-1.5, 17.5):
        m.cyl((x, -4.7, z0 - 1.6), 0.6, 1.6, 'z', 'metal:steel', 'buffer shank')
        m.cyl((x, -4.7, z0 - 1.9), 1.0, 0.3, 'z', 'metal:steel', 'buffer head')
        m.cyl((x, -4.7, z1), 0.6, 1.6, 'z', 'metal:steel', 'buffer shank')
        m.cyl((x, -4.7, z1 + 1.6), 1.0, 0.3, 'z', 'metal:steel', 'buffer head')
    m.box((7.4, -5.2, z0 - 1.4), (8.6, -4.2, z0), 'metal:steel', 'draw hook')
    m.box((7.4, -5.2, z1), (8.6, -4.2, z1 + 1.4), 'metal:steel', 'draw hook')
    m.box((-5.0, -3.4, z0), (21.0, -2.6, z1), 'paint:#3a3d40', 'deck')
    m.g('steps + rails')
    for x in (-5.4, 20.6):
        for z in (z0 + 0.5, z1 - 2.5):
            m.box((x, -9.0, z), (x + 0.8, -3.4, z + 0.3), 'galv', 'step hanger')
            m.box((x, -9.0, z), (x + 0.8, -8.7, z + 2.0), 'mesh', 'step tread')
            m.box((x, -6.4, z), (x + 0.8, -6.1, z + 2.0), 'mesh', 'step tread')
    m.handrail([(-4.6, z1 - 9.0), (-4.6, z1 - 0.6)], -2.6, h=3.6, r=0.2, mat=YELLOW)
    m.handrail([(20.6, z1 - 9.0), (20.6, z1 - 0.6)], -2.6, h=3.6, r=0.2, mat=YELLOW)


def cab(m, body_col, z0=-6.0, z1=4.0, top=12.0):
    m.g('cab')
    m.box((-4.6, -2.6, z0 + 0.4), (20.6, 6.2, z1), body_col, 'cab lower body')
    m.box((-4.6, 6.2, z0 + 0.8), (-3.4, top, z1), body_col, 'cab corner pillar W')
    m.box((19.4, 6.2, z0 + 0.8), (20.6, top, z1), body_col, 'cab corner pillar E')
    m.box((7.4, 6.2, z0 + 0.8), (8.6, top, z0 + 1.4), body_col, 'centre pillar')
    m.box((-3.4, 6.6, z0 + 0.9), (7.4, top - 0.4, z0 + 1.0), 'glass', 'windscreen W', faces=('north', 'south'))
    m.box((8.6, 6.6, z0 + 0.9), (19.4, top - 0.4, z0 + 1.0), 'glass', 'windscreen E', faces=('north', 'south'))
    m.box((-4.7, 7.0, z0 + 2.0), (-4.6, top - 0.8, z1 - 0.6), 'glass', 'side window W', faces=('west', 'east'))
    m.box((20.6, 7.0, z0 + 2.0), (20.7, top - 0.8, z1 - 0.6), 'glass', 'side window E', faces=('west', 'east'))
    m.box((-4.8, top, z0 + 0.4), (20.8, top + 0.8, z1 + 0.2), body_col, 'cab roof')
    m.box((-3.0, 6.0, z0 + 0.6), (19.0, 6.6, z0 + 1.0), 'rubber', 'windscreen seal')
    for x in (1.0, 15.0):
        m.box((x, top - 0.2, z0 + 0.6), (x + 0.3, top + 0.1, z0 + 0.9), 'metal:steel', 'wiper pivot')
        m.box((x - 2.5, top - 1.2, z0 + 0.7), (x + 0.3, top - 1.0, z0 + 0.85), 'paint:#1d1d21', 'wiper blade', rot={'axis': 'z', 'angle': 22.5, 'origin': [x, top - 0.1, z0 + 0.8]})
    m.g('lights + beacons')
    for x in (-2.8, 18.8):
        m.cyl((x, 4.8, z0 - 0.1), 0.6, 0.5, 'z', 'lamp:ffffd0', 'headlight')
        m.cyl((x, 3.6, z0 - 0.1), 0.4, 0.5, 'z', 'lamp:ff2020', 'tail light')
    for x in (-3.0, 19.0):
        m.cyl((x, top + 0.8, z1 - 2.0), 0.6, 1.0, 'y', 'lamp:ffa020', 'amber beacon')
    m.box((4.0, top + 0.8, z0 + 2.0), (12.0, top + 1.3, z0 + 3.0), 'paint:#1d1d21', 'horn bar')
    m.cyl((5.0, top + 1.0, z0 + 1.4), 0.4, 0.8, 'z', 'metal:brass', 'horn')
    m.cyl((11.0, top + 1.0, z0 + 1.4), 0.4, 0.8, 'z', 'metal:brass', 'horn')


def engine_bay(m, body_col, z0, z1, top):
    m.g('engine bay')
    m.box((-4.6, -2.6, z0), (20.6, top, z1), body_col, 'engine hood')
    for x in (-4.65, 20.6):
        for zz in (z0 + 1.0, z0 + (z1 - z0) / 2 + 0.5):
            m.box((x, 0.0, zz), (x + 0.05, top - 1.5, zz + (z1 - z0) / 2 - 1.5), 'louvre', 'hood louvre', faces=('east', 'west'))
    m.box((-3.6, -1.6, z1), (19.6, top - 1.0, z1 + 0.1), 'fins', 'radiator', faces=('south',))
    m.cyl((2.0, top, z1 - 3.0), 0.7, 4.5, 'y', 'paint:#2b2f33', 'exhaust stack')
    m.cyl((2.0, top + 4.5, z1 - 3.0), 0.9, 0.4, 'y', 'paint:#2b2f33', 'rain cap')
    m.box((6.0, top, z0 + 1.0), (10.0, top + 1.2, z0 + 4.0), 'mesh', 'air intake')
    for zz in (z0 + 0.5, z1 - 1.0):
        m.box((-4.8, top, zz), (20.8, top + 0.3, zz + 0.5), body_col, 'lifting eye beam')
    m.box((19.0, -6.0, z0 + 1.0), (21.6, -3.4, z0 + 7.0), 'paint:#2b2f33', 'fuel tank')


def service_point(m, z, labels):
    """the front service strip with fillers / hatches for the zones (x from left to right as given)"""
    m.g('service point')
    m.box((0.0, -3.2, z - 0.3), (16.0, -2.0, z), 'paint:#3a3d40', 'service strip')
    for (x0, x1, lab, kind) in labels:
        if kind == 'cap':
            m.cyl(((x0 + x1) / 2, -2.6, z - 0.6), 0.45, 0.3, 'z', 'metal:brass', 'filler cap')
        else:
            m.box((x0 + 0.2, -3.0, z - 0.5), (x1 - 0.2, -2.2, z - 0.3), 'metal:steel', 'hatch')
            m.box(((x0 + x1) / 2 - 0.4, -2.7, z - 0.7), ((x0 + x1) / 2 + 0.4, -2.5, z - 0.5), 'metal:steel', 'hatch latch')
        m.box((x0, -2.0, z - 0.32), (x1, -1.6, z - 0.3), 'label:' + lab, 'service legend', faces=('north',))


def rail_grinder():
    m = Model('rail_grinder')
    track_chassis(m, YELLOW)
    cab(m, YELLOW)
    engine_bay(m, YELLOW, 13.0, 21.6, 7.0)
    m.g('grinding modules')
    for x in GAUGE_X:
        m.box((x - 2.4, -9.0, 4.4), (x + 2.4, -6.0, 11.6), 'paint:#3a3d40', 'grinding carriage')
        for z in (5.6, 8.0, 10.4):
            m.cyl((x, RAIL_TOP + 0.4, z), 0.9, 1.6, 'y', 'paint:#b8b0a0', 'grinding stone')
            m.cyl((x, RAIL_TOP + 2.0, z), 0.5, 2.6, 'y', 'metal:steel', 'spindle')
            m.box((x - 0.9, -10.2, z - 0.9), (x + 0.9, -9.0, z + 0.9), 'paint:#2050a0', 'grinding motor')
        m.cyl((x + (1.6 if x > 8 else -1.6), -6.0, 6.0), 0.35, 2.6, 'y', 'metal:steel', 'lift cylinder')
        m.cyl((x + (1.6 if x > 8 else -1.6), -6.0, 10.0), 0.35, 2.6, 'y', 'metal:steel', 'lift cylinder')
        for z in (4.2, 11.6):
            m.box((x - 2.8, RAIL_TOP + 0.6, z), (x + 2.8, -6.0, z + 0.2), 'rubber', 'spark curtain')
        for xs in (x - 2.8, x + 2.6):
            for i in range(6):
                m.box((xs, RAIL_TOP + 0.8, 4.5 + i * 1.2), (xs + 0.2, -6.0, 5.4 + i * 1.2), 'rubber', 'curtain strip')
    m.g('water spray')
    m.box((13.0, -2.6, 5.0), (19.6, 4.0, 12.0), 'paint:#2050a0', 'spray water tank')
    m.box((15.6, 4.0, 7.6), (16.6, 4.4, 8.6), 'metal:brass', 'tank filler')
    for x in GAUGE_X:
        m.box((x - 0.15, -8.6, 4.0), (x + 0.15, -6.0, 4.3), 'metal:copper', 'spray pipe')
        m.box((x - 0.4, -9.0, 3.9), (x + 0.4, -8.6, 4.3), 'metal:brass', 'spray nozzle')
    m.g('stone magazine')
    m.box((-4.0, -2.6, 5.0), (3.0, 3.0, 12.0), 'paint:#3a3d40', 'stone magazine')
    for i in range(4):
        m.cyl((-2.5 + i * 1.6, 3.0, 8.5), 0.7, 0.5, 'y', 'paint:#b8b0a0', 'spare stone')
    m.box((3.0, -2.6, 6.0), (13.0, 2.0, 11.0), 'paint:#c9cbc7', 'hydraulic power pack')
    for x in (4.0, 6.0, 8.0, 10.0, 12.0):
        m.cyl((x, 2.0, 8.5), 0.3, 0.8, 'y', 'metal:steel', 'valve block stem')
    m.g('front panel')
    m.box((0.0, -3.2, -6.6), (16.0, 6.2, -6.0), 'paint:#2b2f33', 'local control panel')
    m.box((0.2, 5.0, -6.65), (16.0, 6.0, -6.6), 'paint:#050805', 'display strip', faces=('north',))
    m.box((0.3, 6.25, -6.65), (6.0, 6.55, -6.6), 'label:RAIL GRINDER', 'legend', faces=('north',))
    m.box((10.0, 6.25, -6.65), (15.7, 6.55, -6.6), 'warn', 'warning strip', faces=('north',))
    service_point(m, -6.6, [(10.6, 15.6, 'FUEL', 'cap'), (7.3, 10.3, 'WATER', 'cap'), (1.2, 6.6, 'STONES', 'hatch')])
    m.terminal_12v(-4.6, 1.0, 0.0, 'west')
    f = m.surface('north', face=-6.67, title='RAIL GRINDER')
    digits(m, f, 'state', 5.0, 5.5, 9.4, 0.6, 'ffd040')
    digits(m, f, 'spd', 12.8, 5.5, 4.6, 0.6, '7cf29a')
    digits(m, f, 'stone', 3.4, 4.8, 5.4, 0.5, 'b8f0c8')
    digits(m, f, 'done', 8.4, 4.8, 4.2, 0.5, 'b8f0c8')
    digits(m, f, 'prof', 13.0, 4.8, 5.0, 0.5, '30a0ff')
    for i, (cid, lab) in enumerate((('g_fuel', 'FUEL'), ('g_stone', 'STONE'), ('g_water', 'WATER'), ('g_batt', 'BATT'))):
        m.ctl(f, 'gauge', cid, 1.4 + i * 1.7, 3.9, w=1.2, h=1.2, col='f2f0e6', label=lab, max=100)
    digits(m, f, 'fuel', 11.2, 3.9, 4.6, 0.5, 'ffb020')
    for i, (cid, lab, col, bl) in enumerate((('eng', 'ENG', '30e060', False), ('heads', 'HEADS', 'ffb020', False), ('spark', 'GRIND', 'ff8020', True), ('wet', 'SPRAY', '3080ff', False),
                                             ('move', 'MOVE', '30e060', True), ('end', 'STOP', 'ff3030', True), ('worn', 'STONES', 'ffb020', False), ('nopos', 'NO POSS', 'ff3030', True),
                                             ('poss', 'POSS', '30e060', False), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 0.9 + i * 1.6, 2.3, col, lab, 0.42, blink=bl)
    m.ctl(f, 'selector', 's_dir', 1.2, -0.4, w=0.9, h=0.9, col='202020', label='DIR', pos=['FWD', 'REV'])
    m.ctl(f, 'selector', 's_spd', 2.9, -0.4, w=0.9, h=0.9, col='202020', label='SPEED', pos=['1', '2', '3'])
    m.ctl(f, 'selector', 's_water', 4.6, -0.4, w=0.9, h=0.9, col='202020', label='SPRAY', pos=['OFF', 'ON'])
    m.ctl(f, 'lever', 'm_heads', 6.6, -0.3, w=0.7, h=1.5, col='d02020', label='HEADS', pos=['UP', 'DOWN'])
    m.ctl(f, 'push', 'start', 8.6, -0.4, w=0.9, h=0.9, col='30c050', label='ENGINE')
    m.ctl(f, 'push', 'go', 10.4, -0.4, w=0.9, h=0.9, col='2050d0', label='DRIVE')
    m.ctl(f, 'push', 'test', 11.9, -0.4, w=0.35, h=0.35, col='f0f0f0', label='')
    m.ctl(f, 'mushroom', 'm_estop', 14.4, -0.4, w=1.2, h=1.2, col='d01010', label='E-STOP')
    m.ctl(f, 'zone', 'z_fuel', 2.9, -2.55, w=5.0, h=1.0, label='FUEL')
    m.ctl(f, 'zone', 'z_water', 7.2, -2.55, w=3.0, h=1.0, label='WATER')
    m.ctl(f, 'zone', 'z_stones', 12.1, -2.55, w=5.4, h=1.0, label='STONES')
    m.save(item_gui=0.32)
    facing_states('rail_grinder')


def rail_welder():
    m = Model('rail_welder')
    track_chassis(m, NETWORK_RAIL)
    cab(m, NETWORK_RAIL)
    engine_bay(m, NETWORK_RAIL, 14.0, 21.6, 8.0)
    m.g('welding head gantry')
    for x in (-4.4, 19.4):
        m.box((x, -2.6, 5.0), (x + 1.0, 10.0, 6.0), 'paint:#2b2f33', 'gantry leg')
        m.box((x, -2.6, 12.0), (x + 1.0, 10.0, 13.0), 'paint:#2b2f33', 'gantry leg')
    m.box((-4.4, 10.0, 5.0), (20.4, 11.0, 13.0), 'paint:#2b2f33', 'gantry beam')
    m.box((6.0, 8.0, 7.0), (10.0, 10.0, 11.0), 'paint:#3a3d40', 'hoist trolley')
    for x in (7.0, 9.0):
        m.box((x - 0.1, -2.0, 8.9), (x + 0.1, 8.0, 9.1), 'metal:steel', 'hoist chain')
    m.g('flash-butt head')
    hx = GAUGE_X[0]
    m.box((hx - 3.0, -8.0, 6.0), (hx + 3.0, -2.6, 12.0), 'paint:#3a3d40', 'welding head body')
    for z in (6.4, 10.8):
        m.box((hx - 2.0, RAIL_TOP + 0.2, z), (hx + 2.0, -8.0, z + 0.8), 'metal:copper', 'clamp jaw (electrode)')
        m.box((hx - 2.4, -8.6, z - 0.2), (hx + 2.4, -8.0, z + 1.0), 'paint:#c81e1e', 'clamp cylinder')
    m.cyl((hx, -6.5, 6.0), 0.8, 6.0, 'z', 'metal:steel', 'upset ram')
    m.box((hx - 2.6, -2.6, 7.0), (hx + 2.6, -1.4, 11.0), 'paint:#2b2f33', 'welding transformer')
    for i in range(4):
        m.box((hx - 2.4, -2.4 + i * 0.3, 11.0), (hx + 2.4, -2.3 + i * 0.3, 11.4), 'fins', 'transformer cooling fin')
    m.box((hx + 3.0, -6.0, 8.0), (hx + 5.0, -5.0, 10.0), 'cable:1d1d21', 'secondary bus')
    m.g('rail saw + profile grinder (right rail)')
    sx = GAUGE_X[1]
    m.cyl((sx - 2.0, -9.0, 8.0), 2.2, 0.3, 'x', 'metal:steel', 'abrasive cut-off disc')
    m.box((sx - 1.7, -8.0, 6.6), (sx + 1.3, -5.0, 9.4), YELLOW, 'saw guard')
    m.box((sx - 1.0, -5.0, 7.0), (sx + 1.0, -2.6, 9.0), 'paint:#2b2f33', 'saw motor')
    m.cyl((sx, RAIL_TOP + 0.6, 11.0), 0.8, 1.0, 'y', 'paint:#b8b0a0', 'profile grinding wheel')
    m.box((sx - 1.0, -10.0, 10.0), (sx + 1.0, -8.0, 12.0), 'paint:#2050a0', 'grinder motor')
    m.g('closure rail hopper')
    m.box((3.0, -2.6, 5.0), (13.0, 1.0, 13.0), 'paint:#3a3d40', 'hopper box')
    for i in range(4):
        m.box((3.6 + i * 2.4, 1.0, 5.4), (4.8 + i * 2.4, 1.8, 12.6), 'metal:steel', 'closure rail plug')
        m.box((3.8 + i * 2.4, 1.8, 5.4), (4.6 + i * 2.4, 2.1, 12.6), 'metal:steel', 'plug head')
    m.box((3.0, 1.0, 5.0), (13.0, 1.2, 5.4), YELLOW, 'hopper lip')
    m.g('generator')
    m.box((-4.0, -2.6, 13.0), (2.0, 4.0, 14.0), 'paint:#c9cbc7', 'generator end')
    m.nameplate(-3.6, 1.5, 1.6, 2.3, 13.0, '150 KVA')
    m.g('front panel')
    m.box((0.0, -3.2, -6.6), (16.0, 6.2, -6.0), 'paint:#2b2f33', 'local control panel')
    m.box((0.2, 3.0, -6.65), (16.0, 6.0, -6.6), 'paint:#050805', 'display window', faces=('north',))
    m.box((0.3, 6.25, -6.65), (6.0, 6.55, -6.6), 'label:FLASH BUTT', 'legend', faces=('north',))
    m.box((10.0, 6.25, -6.65), (15.7, 6.55, -6.6), 'danger:DANGER|ARC', 'danger strip', faces=('north',))
    service_point(m, -6.6, [(9.6, 14.4, 'FUEL', 'cap'), (2.0, 8.0, 'RAIL PLUGS', 'hatch')])
    m.terminal_12v(-4.6, 1.0, 0.0, 'west')
    f = m.surface('north', face=-6.67, title='RAIL WELDER')
    digits(m, f, 'step', 4.4, 5.55, 7.8, 0.55, 'ffd040')
    digits(m, f, 'temp', 12.0, 5.55, 5.0, 0.55, 'ff8040')
    digits(m, f, 'amp', 2.9, 4.85, 4.8, 0.5, '30a0ff')
    digits(m, f, 'upset', 7.6, 4.85, 4.4, 0.5, 'b8f0c8')
    digits(m, f, 'rail', 12.6, 4.85, 5.6, 0.5, 'b8f0c8')
    digits(m, f, 'target', 5.5, 4.15, 10.4, 0.5, 'ffb020')
    digits(m, f, 'plug', 13.3, 4.15, 4.6, 0.5, '7cf29a')
    digits(m, f, 'res', 5.5, 3.45, 10.4, 0.5, 'ffffff')
    digits(m, f, 'fuel', 13.3, 3.45, 4.6, 0.5, 'ffb020')
    for i, (cid, lab) in enumerate((('g_temp', 'WELD C'), ('g_fuel', 'FUEL'), ('g_batt', 'BATT'))):
        m.ctl(f, 'gauge', cid, 1.2 + i * 1.6, 2.4, w=1.1, h=1.1, col='f2f0e6', label=lab, max=100)
    for i, (cid, lab, col, bl) in enumerate((('arc', 'ARC', 'ff8020', True), ('busy', 'BUSY', '3080ff', True), ('move', 'MOVE', '30e060', True), ('noplug', 'NO PLUG', 'ffb020', False),
                                             ('eng', 'ENG', '30e060', False), ('nopos', 'NO POSS', 'ff3030', True), ('poss', 'POSS', '30e060', False), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 6.2 + i * 1.25, 2.5, col, lab, 0.4, blink=bl)
    steps = (('cut', 'CUT'), ('align', 'ALIGN'), ('heat', 'HEAT'), ('weld', 'WELD'), ('shear', 'SHEAR'), ('grind', 'GRIND'), ('ut', 'UT'))
    for i, (cid, lab) in enumerate(steps):
        u = 5.8 + i * 1.3
        lamp(m, f, 'l%d' % (i + 1), u, 1.0, '30e060', '', 0.32)
        m.ctl(f, 'push', cid, u, 0.0, w=0.8, h=0.8, col='2050d0' if cid != 'weld' else 'd02020', label=lab)
    m.ctl(f, 'selector', 's_dir', 1.1, -0.2, w=0.85, h=0.85, col='202020', label='DIR', pos=['FWD', 'REV'])
    m.ctl(f, 'push', 'start', 2.7, 0.0, w=0.85, h=0.85, col='30c050', label='ENGINE')
    m.ctl(f, 'push', 'go', 4.2, 0.0, w=0.75, h=0.75, col='f0f0f0', label='TRAVEL')
    m.ctl(f, 'push', 'seek', 2.0, -1.15, w=0.7, h=0.7, col='f0d020', label='SEEK')
    m.ctl(f, 'push', 'test', 3.5, -1.15, w=0.35, h=0.35, col='f0f0f0', label='')
    m.ctl(f, 'mushroom', 'm_estop', 15.0, 0.0, w=1.1, h=1.1, col='d01010', label='E-STOP')
    m.ctl(f, 'zone', 'z_fuel', 4.0, -2.55, w=4.8, h=1.0, label='FUEL')
    m.ctl(f, 'zone', 'z_plug', 11.0, -2.55, w=6.0, h=1.0, label='PLUGS')
    m.save(item_gui=0.32)
    facing_states('rail_welder')


# ------------------------------------------------------------------------------------------------ #43 flood sensor
def flood_sensor():
    m = Model('flood_sensor')
    m.g('sump chamber')
    m.box((0.0, 0, 6.0), (16.0, 1.4, 16.0), 'concrete', 'chamber ring')
    m.box((1.0, 0.6, 7.0), (15.0, 0.9, 15.0), 'paint:#2c4a5a', 'water surface')
    m.box((0.0, 1.4, 6.0), (16.0, 1.6, 6.6), 'haz', 'chamber edge', faces=('up', 'north'))
    m.box((6.0, 0, 15.6), (10.0, 1.2, 16.0), 'concrete', 'inlet pipe headwall')
    m.cyl((8.0, 0.4, 15.4), 0.9, 0.6, 'z', 'concrete', 'inlet pipe')
    m.g('chamber guarding')
    m.handrail([(0.4, 15.6), (0.4, 7.0), (15.6, 7.0), (15.6, 15.6)], 1.6, h=4.0, r=0.18, mat=YELLOW)
    for (x, z) in ((0.4, 11.3), (15.6, 11.3), (4.4, 7.0), (11.6, 7.0)):
        m.cyl((x, 1.6, z), 0.18, 4.0, 'y', YELLOW, 'rail post')
    for (xa, za, xb, zb) in ((0.4, 15.6, 0.4, 7.0), (15.6, 7.0, 15.6, 15.6)):
        m.box((xa - 0.12, 3.6, min(za, zb)), (xa + 0.12, 3.8, max(za, zb)), YELLOW, 'knee rail')
    m.box((0.4, 3.6, 6.88), (15.6, 3.8, 7.12), YELLOW, 'knee rail')
    m.ladder(1.4, 3.0, -0.0, 1.6, 15.0, mat='galv')
    for i in range(6):
        m.box((4.0 + i * 1.4, 1.2, 12.0), (4.6 + i * 1.4, 1.35, 15.4), 'galv', 'grating bar')
    m.box((4.0, 1.2, 11.8), (12.4, 1.35, 12.0), 'galv', 'grating frame')
    m.box((15.65, 2.6, 9.0), (15.75, 4.4, 12.0), 'danger:DEEP|WATER', 'danger sign', faces=('east',))
    m.box((0.25, 2.6, 9.0), (0.35, 4.4, 12.0), 'danger:DEEP|WATER', 'danger sign', faces=('west',))
    m.g('staff gauge')
    m.box((13.6, 0.0, 9.0), (14.6, 12.0, 9.3), 'white', 'staff gauge board')
    for i in range(12):
        col = 'paint:#c81e1e' if i % 2 == 0 else 'paint:#1d1d21'
        m.box((13.6, i + 0.0, 8.95), (14.0 if i % 5 else 14.4, i + 0.12, 9.0), col, 'gauge tick')
    m.box((13.0, 6.0, 8.9), (15.2, 6.2, 9.0), 'paint:#c81e1e', 'rail head mark')
    m.g('post')
    m.box((5.0, 1.4, 4.0), (11.0, 2.2, 10.0), 'concrete', 'post footing')
    m.box((7.4, 2.2, 6.4), (8.6, 27.0, 7.6), 'galv', 'mast')
    for y in (8.0, 14.0, 20.0):
        m.box((7.2, y, 6.2), (8.8, y + 0.4, 7.8), 'galv', 'mast collar')
    m.g('sensor arm')
    m.box((8.6, 13.0, 6.6), (13.0, 13.4, 7.4), 'galv', 'sensor arm')
    m.cyl((12.2, 11.4, 7.0), 0.7, 1.6, 'y', 'paint:#e8e8e2', 'ultrasonic sensor')
    m.cyl((12.2, 11.0, 7.0), 0.5, 0.4, 'y', 'paint:#1d1d21', 'transducer face')
    m.box((8.6, 12.8, 6.9), (12.2, 13.0, 7.1), 'cable:1d1d21', 'sensor cable')
    m.g('outstation cabinet')
    m.box((3.4, 4.0, 4.6), (12.6, 11.4, 6.4), 'paint:#5d6b5a', 'outstation cabinet')
    m.box((3.2, 11.4, 4.4), (12.8, 11.8, 6.6), 'paint:#5d6b5a', 'cabinet roof')
    m.box((3.6, 4.2, 4.4), (12.4, 11.2, 4.6), PANEL, 'cabinet door')
    m.box((3.8, 10.4, 4.35), (8.0, 11.0, 4.4), 'label:FLOOD WARN', 'legend', faces=('north',))
    m.box((8.4, 10.4, 4.35), (12.2, 11.0, 4.4), 'warn', 'warning', faces=('north',))
    m.box((12.4, 6.0, 4.6), (12.7, 9.0, 5.0), 'metal:steel', 'cabinet hinge strip')
    for x in (4.0, 7.0, 10.0):
        m.gland(x, 4.0, 5.5, 'down', r=0.25, length=0.4)
    m.terminal_12v(12.6, 5.0, 5.5, 'east')
    m.g('solar + battery')
    m.box((4.0, 22.0, 4.5), (12.0, 22.3, 10.0), 'paint:#1a2a4a', 'solar panel', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 22, 7]})
    for i in range(4):
        m.box((4.2 + i * 1.95, 22.3, 4.7), (6.0 + i * 1.95, 22.35, 9.8), 'glass', 'solar cell row', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 22, 7]})
    m.box((6.0, 2.2, 8.0), (10.0, 4.2, 10.4), 'paint:#2b2f33', 'battery box')
    m.g('beacon + siren')
    m.cyl((8.0, 27.0, 7.0), 0.9, 0.6, 'y', 'paint:#1d1d21', 'beacon base')
    m.cyl((8.0, 27.6, 7.0), 0.8, 1.6, 'y', 'lamp:ff3020', 'beacon dome')
    m.box((5.0, 24.0, 6.6), (7.4, 25.6, 7.4), 'paint:#c9cbc7', 'siren body')
    m.cyl((4.2, 24.8, 7.0), 0.9, 0.8, 'x', 'paint:#c9cbc7', 'siren horn')
    m.box((8.6, 26.0, 6.8), (8.8, 30.0, 7.0), 'metal:steel', 'telemetry antenna')
    f = m.surface('north', face=4.33, title='FLOOD WARNING')
    digits(m, f, 'lvl', 8.0, 9.6, 7.4, 0.55, '30a0ff')
    digits(m, f, 'rate', 8.0, 8.95, 7.4, 0.5, 'ffb020')
    digits(m, f, 'eta', 8.0, 8.35, 7.4, 0.5, 'ff8040')
    digits(m, f, 'cell', 8.0, 7.75, 7.4, 0.45, 'b8f0c8')
    digits(m, f, 'state', 8.0, 7.2, 7.4, 0.45, 'ffffff')
    m.ctl(f, 'gauge', 'g_lvl', 5.0, 5.6, w=1.4, h=1.4, col='f2f0e6', label='LEVEL', max=100)
    for i, (cid, lab, col, bl) in enumerate((('alert', 'ALERT', 'ffb020', False), ('alarm', 'ALARM', 'ff3030', True), ('flood', 'FLOOD', 'ff3030', True), ('pump', 'PUMPS', '30e060', False),
                                             ('tsrl', 'TSR', 'ffb020', False), ('mute', 'MUTE', 'ffb020', False), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 6.6 + i * 0.9, 6.3, col, lab, 0.35, blink=bl)
    m.ctl(f, 'selector', 's_siren', 11.3, 4.9, w=0.8, h=0.8, col='202020', label='SIREN', pos=['ON', 'MUTE'])
    m.ctl(f, 'push', 'tsr', 6.8, 4.9, w=0.7, h=0.7, col='f0d020', label='TSR')
    m.ctl(f, 'push', 'pumps', 8.2, 4.9, w=0.7, h=0.7, col='2050d0', label='PUMPS')
    m.ctl(f, 'push', 'ack', 9.6, 4.9, w=0.6, h=0.6, col='f0f0f0', label='ACK')
    m.ctl(f, 'push', 'test', 12.2, 6.3, w=0.3, h=0.3, col='f0f0f0', label='')
    m.ctl(f, 'lamp', 'bcn', 8.0, 28.4, w=1.6, h=1.6, col='ff3020', label='', blink=True)
    m.save(item_gui=0.4)
    facing_states('flood_sensor')


# ------------------------------------------------------------------------------------------------ #44 OHLE recording station
def ohle_rec_station():
    m = Model('ohle_rec_station')
    m.g('rack')
    m.box((1.0, 0, 3.0), (15.0, 0.8, 15.0), 'paint:#141416', 'plinth')
    for x in (1.0, 14.2):
        m.box((x, 0.8, 3.0), (x + 0.8, 14.4, 15.0), 'paint:#1c1d20', 'rack upright')
    m.box((1.0, 14.4, 3.0), (15.0, 15.0, 15.0), 'paint:#1c1d20', 'rack top')
    m.box((1.8, 0.8, 14.4), (14.2, 14.4, 15.0), 'mesh', 'rear door')
    for y in range(1, 14, 1):
        m.box((1.7, y, 2.9), (1.95, y + 0.25, 3.0), 'metal:steel', 'rack hole')
        m.box((14.05, y, 2.9), (14.3, y + 0.25, 3.0), 'metal:steel', 'rack hole')
    m.g('recorder chassis')
    m.box((1.8, 8.6, 3.3), (14.2, 14.2, 14.0), 'paint:#2b2f33', 'recorder chassis')
    m.box((1.6, 8.6, 3.1), (14.4, 14.2, 3.3), 'paint:#3a3f44', 'recorder front')
    m.box((2.2, 10.6, 3.05), (13.8, 13.9, 3.1), 'paint:#03140a', 'display window', faces=('north',))
    bezel(m, 2.2, 10.6, 13.8, 13.9, 3.1)
    m.nameplate(2.2, 8.9, 7.4, 9.5, 3.1, 'OLE REC')
    for x in (1.6, 14.0):
        m.box((x, 9.0, 2.7), (x + 0.4, 13.8, 3.1), 'metal:alu', 'rack handle')
    m.g('telemetry receiver')
    m.box((1.8, 6.6, 3.3), (14.2, 8.4, 14.0), 'paint:#2b2f33', 'receiver')
    m.box((1.6, 6.6, 3.1), (14.4, 8.4, 3.3), 'paint:#26282c', 'receiver front')
    for i in range(6):
        m.box((8.6 + i * 0.8, 7.1, 3.0), (9.0 + i * 0.8, 7.5, 3.1), 'paint:#30e060', 'link LED')
    m.g('control panel')
    m.box((1.8, 1.4, 3.3), (14.2, 6.4, 14.0), 'paint:#1c1d20', 'panel chassis')
    m.box((1.6, 1.4, 3.1), (14.4, 6.4, 3.3), 'paint:#3a3f44', 'panel front')
    m.g('monitors')
    for x0 in (1.4, 8.4):
        m.box((x0 + 2.4, 15.0, 7.0), (x0 + 3.8, 15.6, 9.0), 'paint:#1d1d21', 'monitor foot')
        m.box((x0 + 2.9, 15.6, 8.0), (x0 + 3.3, 17.2, 8.4), 'paint:#1d1d21', 'monitor neck')
        m.box((x0, 17.2, 7.6), (x0 + 6.2, 21.4, 8.2), 'paint:#1d1d21', 'monitor')
        m.box((x0 + 0.3, 17.5, 7.55), (x0 + 5.9, 21.1, 7.6), 'screen', 'monitor screen', faces=('north',))
    m.g('printer + keyboard')
    m.box((4.0, 15.0, 10.0), (12.0, 16.6, 14.0), 'paint:#d8d8d4', 'printer')
    m.box((5.0, 16.0, 9.9), (11.0, 16.2, 10.0), 'paint:#1a1a1a', 'paper slot')
    m.box((5.4, 16.1, 9.2), (10.6, 16.2, 9.9), 'paint:#fbfbf6', 'printout')
    m.box((3.0, 15.0, 4.0), (13.0, 15.3, 6.4), 'paint:#2b2f33', 'keyboard')
    for i in range(10):
        m.box((3.3 + i * 0.95, 15.3, 4.4), (4.0 + i * 0.95, 15.45, 6.0), 'paint:#3a3f44', 'key row')
    m.g('antenna')
    m.box((13.8, 15.0, 13.6), (14.2, 21.0, 14.0), 'metal:steel', 'antenna mast')
    m.box((13.2, 20.0, 13.7), (14.8, 20.2, 13.9), 'metal:steel', 'antenna element')
    m.box((13.2, 19.0, 13.7), (14.8, 19.2, 13.9), 'metal:steel', 'antenna element')
    m.terminal_12v(15.0, 2.0, 9.0, 'east')
    m.box((15.0, 4.6, 6.0), (15.5, 6.0, 7.4), 'paint:#1d1d21', 'mains inlet')
    m.gland(15.5, 5.3, 6.7, 'east', r=0.3, cable='cable:1d1d21', length=0.4)
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 0.8, 12.0, 'down', r=0.35, length=0.6)
    f = m.surface('north', face=3.03, title='OHLE RECORDING')
    digits(m, f, 'run', 8.0, 13.45, 11.0, 0.55, 'ffd040')
    digits(m, f, 'veh', 8.0, 12.85, 11.0, 0.5, 'b8f0c8')
    digits(m, f, 'm', 5.2, 12.25, 5.4, 0.5, '7cf29a')
    digits(m, f, 'h', 10.8, 12.25, 5.4, 0.5, '7cf29a')
    digits(m, f, 'stg', 5.2, 11.65, 5.4, 0.5, '30a0ff')
    digits(m, f, 'wear', 10.8, 11.65, 5.4, 0.5, 'e070f0')
    digits(m, f, 'excd', 8.0, 11.05, 11.0, 0.5, 'ffb020')
    for i, (cid, lab, col, bl) in enumerate((('rec', 'REC', 'ff2020', True), ('exc', 'EXCEED', 'ffb020', False), ('link', 'LINK', '30e060', False),
                                             ('sup', 'MAINS', '30e060', False), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 2.2 + i * 1.0, 6.05, col, lab, 0.35, blink=bl)
    for i, (cid, lab) in enumerate((('g_h', 'HEIGHT'), ('g_s', 'STAGGER'), ('g_w', 'WEAR'))):
        m.ctl(f, 'gauge', cid, 3.0 + i * 2.3, 3.9, w=1.6, h=1.6, col='f2f0e6', label=lab, max=100)
    m.ctl(f, 'selector', 's_cls', 9.6, 4.4, w=0.9, h=0.9, col='202020', label='LIMITS', pos=['STANDARD', 'HIGH SPEED'])
    m.ctl(f, 'push', 'new', 11.2, 5.2, w=0.7, h=0.7, col='30c050', label='NEW')
    m.ctl(f, 'push', 'end', 12.6, 5.2, w=0.7, h=0.7, col='d02020', label='END')
    m.ctl(f, 'push', 'orders', 11.2, 3.4, w=0.7, h=0.7, col='f0d020', label='ORDERS')
    m.ctl(f, 'push', 'print', 12.6, 3.4, w=0.7, h=0.7, col='2050d0', label='PRINT')
    m.ctl(f, 'push', 'test', 14.0, 5.6, w=0.3, h=0.3, col='f0f0f0', label='')
    digits(m, f, 'cls', 11.8, 2.1, 4.4, 0.4, 'b8f0c8')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 14.0, 1.4, 14.0, 1.0, side='south', col='paint:#1c1d20', title='OHLE RECORDER', volts='230V 12V DC')
    m.save(item_gui=0.45)
    facing_states('ohle_rec_station')


def recipes():
    R = os.path.join(ASSETS, 'recipes')
    def shaped(name, pattern, key, count=1):
        k = {c: ({'item': v[0], 'data': v[1]} if isinstance(v, tuple) else {'item': v}) for c, v in key.items()}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': k, 'result': {'item': 'irextras:' + name, 'count': count}},
                  open(os.path.join(R, name + '.json'), 'w'), indent=1)
    shaped('wayside_ess', ['IGI', 'RBR', 'ICI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone_block', 'B': 'minecraft:iron_block', 'C': 'minecraft:comparator'})
    shaped('ohle_fault_locator', ['IGI', 'CRC', 'III'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'R': 'minecraft:repeater'})
    shaped('relay_test_set', ['GCG', 'IRI', 'W W'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone', 'W': ('minecraft:planks', 0)})
    shaped('rail_grinder', ['GPG', 'FIF', 'RIR'], {'G': 'minecraft:glass_pane', 'P': 'minecraft:piston', 'F': 'minecraft:flint', 'I': 'minecraft:iron_block', 'R': 'minecraft:rail'})
    shaped('rail_welder', ['GPG', 'BIB', 'RIR'], {'G': 'minecraft:glass_pane', 'P': 'minecraft:piston', 'B': 'minecraft:blaze_powder', 'I': 'minecraft:iron_block', 'R': 'minecraft:rail'})
    shaped('flood_sensor', [' L ', 'ICI', ' I '], {'L': 'minecraft:redstone_lamp', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:comparator'})
    shaped('ohle_rec_station', ['GGG', 'ICI', 'IPI'], {'G': 'minecraft:glass_pane', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:comparator', 'P': 'minecraft:paper'})


if __name__ == '__main__':
    for fn in (wayside_ess, ohle_fault_locator, relay_test_set, rail_grinder, rail_welder, flood_sensor, ohle_rec_station):
        fn()
    recipes()
    print('batch-3 infra assets written')
