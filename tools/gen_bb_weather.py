#!/usr/bin/env python3
"""Railway weather gear for her ChatGPT list #63-66: points heater cabinet, lineside weather station, rail temperature
monitor, box culvert, drainage pump station. Front = north. Every lamp / button / key / selector / dial / display is a
live control the Java tile reads or works (com.dogpound.railmap.weather). 12 V boxes are wire terminals."""
import json, os
from bbkit import Model, ASSETS

LIGHT = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
GRP = 'paint:#5d6b5a'          # glass-reinforced plastic lineside cabinet (RAL 6003-ish)
GREEN = 'paint:#2f5d3a'


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label, s=0.7, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def cabinet(m, x0, x1, y0, y1, z0, z1, mat, name='cabinet'):
    """a lineside cabinet: body, roof with overhang, door with handle + hinges + lock, plinth, vents"""
    m.box((x0 - 0.4, 0, z0 + 0.4), (x1 + 0.4, y0, z1 - 0.2), 'concrete', name + ' plinth')
    m.box((x0, y0, z0 + 0.2), (x1, y1, z1), mat, name + ' body')
    m.box((x0 - 0.3, y1, z0 - 0.2), (x1 + 0.3, y1 + 0.5, z1 + 0.3), mat, name + ' roof')
    m.box((x0 + 0.2, y0 + 0.2, z0), (x1 - 0.2, y1 - 0.2, z0 + 0.2), LIGHT, name + ' door')
    m.box((x1 - 0.7, (y0 + y1) / 2 - 1.2, z0 - 0.35), (x1 - 0.4, (y0 + y1) / 2 + 1.2, z0), 'metal:steel', 'door handle')
    m.box((x1 - 0.8, (y0 + y1) / 2 + 1.6, z0 - 0.1), (x1 - 0.3, (y0 + y1) / 2 + 2.1, z0), 'metal:brass', 'cabinet lock')
    for y in (y0 + 1.0, y1 - 1.6):
        m.box((x0 - 0.3, y, z0 + 0.4), (x0, y + 0.8, z0 + 1.0), 'metal:steel', 'hinge')
    for z in (z0 + 1.5, z1 - 1.5):
        m.box((x1, y0 + 1, z - 0.2), (x1 + 0.2, y1 - 1, z + 0.2), mat, 'side rib')
        m.box((x0 - 0.2, y0 + 1, z - 0.2), (x0, y1 - 1, z + 0.2), mat, 'side rib')


def points_heater():
    m = Model('points_heater')
    m.g('cabinet')
    cabinet(m, 3.4, 12.6, 1.0, 14.0, 4.0, 11.6, GRP)
    m.box((3.8, 12.4, 3.96), (12.2, 13.6, 4.0), 'label:POINTS HEATING', 'legend', faces=('north',))
    m.box((3.8, 1.4, 3.96), (7.2, 2.8, 4.0), 'danger:DANGER|400V', 'danger plate', faces=('north',))
    m.louvres(8.4, 12.0, 1.4, 3.2, 4.0, n=3)
    m.g('isolating transformer')
    m.box((12.6, 1.0, 6.0), (15.6, 6.0, 10.4), DARK, 'aux transformer')
    m.box((12.8, 6.0, 6.2), (15.4, 6.4, 10.2), DARK, 'transformer lid')
    for z in (6.6, 7.6, 8.6, 9.6):
        m.box((15.6, 1.4, z), (15.9, 5.6, z + 0.5), 'fins', 'cooling fin')
    m.bushing(14.2, 8.2, 6.4, 2.0, sheds=2)
    m.box((14.0, 8.6, 8.0), (14.4, 14.6, 8.4), 'cable:1d1d21', 'aux supply cable')
    m.g('snow sensor')
    m.box((7.6, 14.5, 10.0), (8.4, 18.0, 10.8), 'galv', 'sensor pole')
    m.box((6.6, 18.0, 9.4), (9.4, 18.6, 11.4), 'paint:#e8e8e2', 'snow / ice detector plate')
    m.box((7.0, 18.6, 9.8), (9.0, 19.0, 11.0), 'glass', 'precipitation sensor')
    m.box((9.4, 17.0, 10.2), (10.6, 18.2, 10.6), 'paint:#2a2d30', 'air temperature probe')
    m.g('heater cables to the rails')
    for i, x in enumerate((4.4, 6.0, 9.8, 11.4)):
        m.gland(x, 1.0, 7.0, 'down', r=0.3, length=0.4)
        m.box((x - 0.2, 0.2, -2.0 - i), (x + 0.2, 0.6, 7.0), 'cable:3a3a3a', 'heater feed')
    m.box((2.0, 0, -4.0), (14.0, 0.2, 2.0), 'concrete', 'cable route slab')
    m.box((1.6, 0.2, -4.0), (14.4, 0.5, -3.4), 'metal:steel', 'stock rail heater clip strip')
    for x in (2.4, 5.0, 7.6, 10.2, 12.8):
        m.box((x, 0.2, -3.6), (x + 0.6, 0.9, -3.2), 'metal:steel', 'heater element clip')
    m.terminal_12v(3.4, 8.0, 8.0, 'west')
    f = m.surface('north', face=3.9, title='POINTS HEATING')
    m.ctl(f, 'selector', 's_mode', 4.2, 9.6, w=1.6, h=1.6, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    m.ctl(f, 'gauge', 'kw', 8.0, 9.6, w=2.8, h=2.8, col='f2f0e6', label='KW', max=15)
    m.ctl(f, 'digits', 'air', 11.6, 10.8, w=3.6, h=0.75, col='5bcefa', label='', fmt='%s')
    m.ctl(f, 'digits', 'rail', 11.6, 9.8, w=3.6, h=0.75, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'src', 11.6, 8.8, w=3.6, h=0.75, col='b8f0c8', label='', fmt='%s')
    lamp(m, f, 'heat', 3.4, 6.4, 'ff7020', 'HEAT')
    lamp(m, f, 'frost', 5.0, 6.4, '5bcefa', 'FROST')
    lamp(m, f, 'snow', 6.6, 6.4, 'f0f0ff', 'SNOW')
    lamp(m, f, 'supply', 8.2, 6.4, '30e060', 'SUPPLY')
    lamp(m, f, 'fault', 9.8, 6.4, 'ff3030', 'FAULT', blink=True)
    lamp(m, f, 'pwr', 11.4, 6.4, '30e060', '12V', 0.5)
    m.ctl(f, 'digits', 'kwh', 5.6, 4.4, w=4.0, h=0.75, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'push', 'rcd', 9.6, 4.4, w=1.2, h=1.2, col='2050d0', label='RCD RESET')
    m.ctl(f, 'push', 'test', 11.8, 4.4, w=0.6, h=0.6, col='f0f0f0', label='TEST')
    m.save(item_gui=0.55)
    facing_states('points_heater')


def weather_station():
    m = Model('weather_station')
    m.g('mast')
    m.box((5.0, 0, 9.0), (11.0, 0.6, 15.0), 'concrete', 'mast foundation')
    m.cyl((8.0, 0.6, 12.0), 0.55, 31.0, 'y', 'galv', 'instrument mast')
    for y in (8.0, 16.0, 24.0):
        m.box((7.2, y, 11.2), (8.8, y + 0.4, 12.8), 'galv', 'mast collar')
    m.g('anemometer and vane')
    m.box((7.6, 30.6, 7.6), (8.4, 31.6, 16.4), 'galv', 'cross arm')
    m.cyl((8.0, 31.6, 8.4), 0.3, 0.4, 'y', 'paint:#202020', 'anemometer body')
    for dx, dz in ((1.6, 0), (-0.8, 1.4), (-0.8, -1.4)):
        m.box((8.0, 31.9, 8.35), (8.0 + dx * 0.9 + 0.05, 32.0, 8.45 + dz * 0.9), 'metal:alu', 'cup arm')
        m.box((8.0 + dx - 0.35, 31.6, 8.4 + dz - 0.35), (8.0 + dx + 0.35, 32.0, 8.4 + dz + 0.35), 'paint:#202020', 'anemometer cup')
    m.cyl((8.0, 31.6, 15.6), 0.25, 0.4, 'y', 'paint:#202020', 'vane pivot')
    m.box((7.9, 31.4, 13.6), (8.1, 32.0, 15.6), 'metal:alu', 'vane tail')
    m.box((7.7, 31.6, 15.6), (8.3, 31.8, 16.6), 'paint:#c81e1e', 'vane head')
    m.g('visibility sensor')
    m.box((4.0, 22.0, 11.6), (12.0, 22.6, 12.4), 'galv', 'visibility T-bar')
    m.box((3.4, 21.2, 11.2), (5.0, 23.4, 12.8), 'paint:#e8e8e2', 'transmitter head')
    m.box((11.0, 21.2, 11.2), (12.6, 23.4, 12.8), 'paint:#e8e8e2', 'receiver head')
    m.box((3.4, 21.0, 11.4), (5.0, 21.2, 12.6), 'glass', 'transmitter window')
    m.box((11.0, 21.0, 11.4), (12.6, 21.2, 12.6), 'glass', 'receiver window')
    for x in (3.6, 11.2):
        m.box((x, 23.4, 11.0), (x + 1.2, 23.7, 13.0), 'paint:#e8e8e2', 'sun hood')
    m.g('stevenson screen')
    m.box((9.0, 14.0, 9.8), (14.6, 18.6, 14.2), 'paint:#f4f4ee', 'screen box')
    m.louvres(9.2, 14.4, 14.4, 18.2, 9.8, n=5, mat='paint:#e4e4de')
    m.box((8.8, 18.6, 9.6), (14.8, 19.0, 14.4), 'paint:#f4f4ee', 'screen roof')
    m.box((8.6, 15.8, 11.6), (9.0, 16.4, 12.4), 'galv', 'screen bracket')
    m.g('rain gauge')
    m.cyl((3.6, 12.0, 13.6), 1.0, 3.0, 'y', 'paint:#e8e8e2', 'rain gauge body')
    m.cyl((3.6, 15.0, 13.6), 1.15, 0.3, 'y', 'metal:alu', 'collector rim')
    m.box((3.0, 14.9, 13.0), (4.2, 15.0, 14.2), 'mesh', 'collector funnel')
    m.box((4.6, 13.0, 13.2), (7.4, 13.4, 13.6), 'galv', 'gauge arm')
    m.g('solar panel')
    m.box((4.0, 25.0, 12.8), (12.0, 25.4, 16.0), 'glass', 'solar panel', rot={'axis': 'x', 'angle': 22.5, 'origin': [8.0, 25.2, 12.8]})
    m.box((3.8, 24.6, 12.6), (12.2, 25.0, 13.0), 'metal:alu', 'panel frame')
    m.g('ice detector')
    m.box((12.0, 27.0, 11.6), (14.6, 27.4, 12.4), 'galv', 'ice detector arm')
    m.cyl((14.2, 27.4, 12.0), 0.25, 1.8, 'y', 'metal:steel', 'vibrating probe')
    m.g('rail probe')
    m.box((7.8, 0.2, 2.0), (8.2, 0.6, 9.0), 'cable:1d1d21', 'rail probe cable')
    m.box((7.0, 0, 0.6), (9.0, 1.2, 2.0), 'paint:#202020', 'rail temperature probe')
    m.g('logger cabinet')
    m.box((2.0, 3.0, 5.0), (14.0, 12.0, 10.6), 'ral7035', 'logger cabinet')
    m.box((1.8, 12.0, 4.8), (14.2, 12.5, 10.8), 'ral7035', 'cabinet hood')
    m.box((2.0, 3.0, 4.8), (14.0, 12.0, 5.0), LIGHT, 'cabinet door')
    m.box((2.2, 11.0, 4.76), (8.8, 11.8, 4.8), 'label:WEATHER STATION', 'legend', faces=('north',))
    m.box((13.4, 6.0, 4.4), (13.8, 9.0, 4.8), 'metal:steel', 'door handle')
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 3.0, 8.0, 'down', r=0.3, length=2.4)
    m.terminal_12v(14.0, 4.0, 8.0, 'east')
    f = m.surface('north', face=4.75, title='WEATHER STATION')
    rows = (('air', 'AIR', '5bcefa'), ('rail', 'RAIL', 'ffb020'), ('wind', 'WIND', 'b8f0c8'), ('vis', 'VIS', 'f0f0f0'),
            ('rain', 'RAIN', '5bcefa'), ('snow', 'SNOW', 'f0f0ff'), ('ice', 'ICE', '9ad8ff'), ('flood', 'WATER', '30a0ff'))
    for i, (cid, lab, col) in enumerate(rows):
        c, r = i % 2, i // 2
        u, v = 4.4 + c * 4.6, 10.4 - r * 1.15
        m.ctl(f, 'label', '', u - 2.6, v, w=0, h=0, col='202020', label=lab)
        m.ctl(f, 'digits', cid, u + 0.3, v, w=3.0, h=0.7, col=col, label='', fmt='%s')
    m.ctl(f, 'gauge', 'windg', 12.6, 9.4, w=2.4, h=2.4, col='f2f0e6', label='M/S', max=40)
    m.ctl(f, 'digits', 'esr', 7.0, 5.6, w=9.6, h=0.8, col='ffd040', label='', fmt='%s')
    for i, (cid, lab, col) in enumerate((('lfrost', 'FROST', '5bcefa'), ('lfog', 'FOG', 'f0f0f0'), ('lheat', 'HEAT', 'ff7020'), ('lflood', 'FLOOD', '30a0ff'),
                                          ('lsnow', 'SNOW', 'f0f0ff'), ('lice', 'ICE', '9ad8ff'))):
        lamp(m, f, cid, 3.0 + i * 1.6, 4.0, col, lab, 0.55)
    lamp(m, f, 'pwr', 13.2, 4.0, '30e060', '12V', 0.5)
    lamp(m, f, 'w2', 14.4, 4.0, '5bcefa', 'W2', 0.45)
    m.ctl(f, 'selector', 's_alarm', 12.4, 6.6, w=1.0, h=1.0, col='202020', label='ALARMS', pos=['OFF', 'ON'])
    m.ctl(f, 'push', 'report', 14.4, 6.6, w=0.9, h=0.9, col='2050d0', label='REPORT')
    m.ctl(f, 'push', 'test', 14.4, 5.4, w=0.5, h=0.5, col='f0f0f0', label='')
    m.save(item_gui=0.4)
    facing_states('weather_station')


def rail_temp_monitor():
    m = Model('rail_temp_monitor')
    m.g('post')
    m.box((6.0, 0, 9.0), (10.0, 0.6, 13.0), 'concrete', 'post foot')
    m.box((7.3, 0.6, 10.3), (8.7, 13.0, 11.7), 'galv', 'post')
    m.box((7.0, 12.8, 10.0), (9.0, 13.2, 12.0), 'galv', 'post cap')
    m.g('solar panel')
    m.box((4.0, 13.2, 9.0), (12.0, 13.6, 14.0), 'glass', 'solar panel', rot={'axis': 'x', 'angle': 22.5, 'origin': [8.0, 13.4, 9.0]})
    m.box((3.8, 12.8, 8.8), (12.2, 13.2, 9.2), 'metal:alu', 'panel frame')
    m.g('transmitter')
    m.box((4.0, 5.0, 4.8), (12.0, 12.2, 10.3), 'ral7035', 'transmitter box')
    m.box((3.8, 12.2, 4.6), (12.2, 12.6, 10.5), 'ral7035', 'box lid')
    m.box((4.0, 5.0, 4.6), (12.0, 12.2, 4.8), LIGHT, 'box front')
    m.box((4.2, 11.2, 4.56), (11.8, 12.0, 4.6), 'label:RAIL TEMP MONITOR', 'legend', faces=('north',))
    for x in (4.4, 11.6):
        for y in (5.4, 11.8):
            m.bolt(x, y, 4.6, 'north', 0.3)
    m.cyl((11.0, 12.6, 7.0), 0.15, 2.4, 'y', 'metal:steel', 'radio aerial')
    m.box((7.0, 4.4, 10.3), (9.0, 5.0, 10.8), 'galv', 'box bracket')
    m.g('rail probe')
    m.box((7.8, 0.2, 1.4), (8.2, 0.6, 9.0), 'cable:1d1d21', 'probe cable')
    m.box((6.6, 0, 0), (9.4, 1.6, 1.4), 'paint:#202020', 'magnetic probe on the rail web')
    m.box((6.8, 1.6, 0.2), (9.2, 1.8, 1.2), 'metal:steel', 'probe magnet')
    m.box((7.6, 0.2, 9.0), (8.4, 5.0, 9.8), 'paint:#3a3d40', 'conduit')
    m.g('ballast')
    for x, z, s in ((1.0, 2.0, 1.2), (2.6, 3.4, 0.9), (12.4, 2.4, 1.1), (13.8, 4.0, 0.8), (3.0, 1.0, 0.7), (11.6, 0.8, 0.9)):
        m.box((x, 0, z), (x + s, s * 0.6, z + s), 'concrete', 'ballast stone')
    m.g('battery and fixings')
    m.box((4.4, 1.0, 9.4), (11.6, 4.2, 12.6), 'ral7035', 'battery box')
    m.box((4.2, 4.2, 9.2), (11.8, 4.5, 12.8), 'ral7035', 'battery lid')
    m.box((5.0, 2.0, 9.36), (9.0, 3.4, 9.4), 'label:BATTERY', 'battery label', faces=('north',))
    for y in (6.0, 10.6):
        m.box((7.0, y, 10.1), (9.0, y + 0.4, 10.3), 'metal:steel', 'U-bolt')
        m.box((6.9, y - 0.1, 11.7), (9.1, y + 0.5, 12.0), 'metal:steel', 'U-bolt plate')
    for y in (1.0, 2.4, 3.8):
        m.box((7.6, y, 9.6), (8.4, y + 0.3, 10.3), 'metal:steel', 'cable cleat')
    m.box((1.4, 0, 0), (4.2, 1.4, 1.4), 'paint:#202020', 'second probe (other rail)')
    m.box((2.6, 0.2, 1.4), (3.0, 0.6, 5.0), 'cable:1d1d21', 'second probe cable')
    m.box((3.0, 0.2, 4.6), (7.0, 0.6, 5.0), 'cable:1d1d21', 'second probe cable')
    m.cyl((11.0, 15.0, 7.0), 0.3, 0.6, 'y', 'paint:#202020', 'aerial tip')
    m.box((12.0, 9.0, 6.0), (12.06, 11.0, 8.4), 'warn', 'warning label', faces=('east',))
    m.terminal_12v(12.0, 5.4, 8.0, 'east')
    f = m.surface('north', face=4.55, title='RAIL TEMPERATURE')
    m.ctl(f, 'gauge', 'railg', 4.4, 9.0, w=2.8, h=2.8, col='f2f0e6', label='RAIL C', max=80)
    m.ctl(f, 'digits', 'rail', 9.8, 10.4, w=3.8, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'air', 9.8, 9.5, w=3.8, h=0.7, col='5bcefa', label='', fmt='%s')
    m.ctl(f, 'digits', 'sft', 9.8, 8.6, w=3.8, h=0.7, col='b8f0c8', label='', fmt='%s')
    m.ctl(f, 'digits', 'lim', 9.8, 7.7, w=3.8, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'cond', 4.4, 6.6, w=3.0, h=0.7, col='b8f0c8', label='', fmt='%s')
    lamp(m, f, 'lwatch', 7.6, 6.6, 'ffb020', 'WATCH', 0.55)
    lamp(m, f, 'lesr', 9.0, 6.6, 'ff7020', 'ESR', 0.55)
    lamp(m, f, 'lbuckle', 10.4, 6.6, 'ff3030', 'BUCKLE', 0.55, blink=True)
    lamp(m, f, 'lpatrol', 11.8, 6.6, '30e060', 'PATROL', 0.55)
    m.ctl(f, 'push', 'patrol', 4.6, 5.6, w=0.9, h=0.9, col='1fa34a', label='')
    m.ctl(f, 'push', 'ballast', 7.0, 5.6, w=0.9, h=0.9, col='8a7a5a', label='')
    m.ctl(f, 'push', 'repair', 9.4, 5.6, w=0.9, h=0.9, col='c81e1e', label='')
    m.ctl(f, 'label', '', 4.6, 5.0, w=0, h=0, col='202020', label='PATROL')
    m.ctl(f, 'label', '', 7.0, 5.0, w=0, h=0, col='202020', label='BALLAST')
    m.ctl(f, 'label', '', 9.4, 5.0, w=0, h=0, col='202020', label='REPAIR')
    lamp(m, f, 'pwr', 11.4, 5.6, '30e060', '', 0.45)
    m.ctl(f, 'push', 'test', 11.4, 5.0, w=0.4, h=0.4, col='f0f0f0', label='')
    m.save(item_gui=0.55)
    facing_states('rail_temp_monitor')


def culvert():
    m = Model('culvert')
    m.g('box culvert')
    m.box((0, 0, 4.0), (16, 1.2, 16), 'concrete', 'culvert floor')
    m.box((0, 7.0, 4.0), (16, 9.0, 16), 'concrete', 'culvert roof slab')
    m.box((0, 1.2, 4.0), (2.0, 7.0, 16), 'concrete', 'culvert wall')
    m.box((14.0, 1.2, 4.0), (16, 7.0, 16), 'concrete', 'culvert wall')
    m.box((2.0, 1.2, 15.6), (14.0, 7.0, 16), 'paint:#101418', 'culvert darkness')
    m.box((2.0, 1.2, 4.0), (14.0, 1.6, 15.6), 'paint:#3a5a6a', 'water in the barrel')
    m.g('headwall and wings')
    m.box((0, 0, 3.0), (2.0, 10.0, 4.0), 'concrete', 'headwall pier')
    m.box((14.0, 0, 3.0), (16, 10.0, 4.0), 'concrete', 'headwall pier')
    m.box((0, 9.0, 3.0), (16, 10.0, 4.0), 'concrete', 'headwall coping')
    for x in (0.0, 15.0):
        m.box((x, 0, 0), (x + 1.0, 6.0, 3.0), 'concrete', 'wing wall')
        m.box((x, 6.0, 1.5), (x + 1.0, 8.0, 3.0), 'concrete', 'wing wall top')
    m.box((1.0, 0, 0), (15.0, 0.6, 3.0), 'concrete', 'apron')
    for x in (2.0, 5.0, 9.0, 12.0):
        m.box((x, 0.6, 0.4), (x + 1.2, 0.9, 1.4), 'concrete', 'rip-rap stone')
    m.g('trash screen')
    for i in range(11):
        x = 2.4 + i * 1.1
        m.box((x, 1.2, 2.6), (x + 0.3, 7.0, 2.9), 'metal:steel', 'screen bar', rot={'axis': 'x', 'angle': -22.5, 'origin': [x, 1.2, 2.9]})
    for y in (2.6, 5.2):
        m.box((2.0, y, 2.4), (14.0, y + 0.3, 2.9), 'metal:steel', 'screen rail')
    m.box((3.0, 1.2, 1.6), (6.0, 1.8, 2.4), 'wood', 'debris branch')
    m.box((9.0, 1.2, 1.8), (10.4, 1.6, 2.4), 'paint:#5a4a2a', 'debris leaves')
    m.g('depth gauge board')
    m.box((14.4, 0.6, 1.6), (15.6, 9.6, 2.0), 'paint:#f4f4ee', 'depth gauge board')
    for i in range(9):
        m.box((14.5, 1.0 + i, 1.56), (15.0 if i % 2 else 15.4, 1.2 + i, 1.6), 'paint:#1a1a1a', 'depth mark', faces=('north',))
    m.box((0.4, 8.0, 2.96), (7.0, 9.0, 3.0), 'plate', 'culvert plate', north='plate:CULVERT 214')
    m.g('stonework')
    for i in range(7):                                      # dressed stone voussoirs over the opening
        x = 2.0 + i * 1.72
        m.box((x, 7.0, 2.8), (x + 1.6, 9.0, 3.0), 'paint:#8e8a82', 'voussoir stone')
    for y in (1.6, 4.2):
        for x in (0.0, 14.0):
            m.box((x + 0.1, y, 2.8), (x + 1.9, y + 2.4, 3.0), 'paint:#8e8a82', 'pier stone')
    for x in (0.2, 15.0):
        for z in (0.4, 1.6):
            m.box((x, 6.0, z), (x + 0.8, 6.4, z + 1.0), 'paint:#a09c94', 'wing coping stone')
    m.box((2.0, 0.9, 0.0), (14.0, 1.0, 3.0), 'paint:#3a5a6a', 'outfall water', faces=('up',))
    m.g('handrail')
    m.handrail([(0.5, 3.4), (15.5, 3.4)], 10.0, h=3.0, r=0.2)
    f = m.surface('north', face=1.5, title='CULVERT')
    m.ctl(f, 'zone', 'screen', 8.0, 4.0, w=11.0, h=5.6, col='000000', label='')
    m.ctl(f, 'flag', 'flow', 3.0, 8.4, w=1.6, h=0.9, col='30a0ff', label='FLOW')
    m.ctl(f, 'gauge', 'level', 13.0, 8.4, w=1.6, h=1.6, col='f2f0e6', label='LEVEL', max=100)
    m.ctl(f, 'digits', 'block', 8.0, 8.4, w=4.4, h=0.7, col='ffb020', label='', fmt='%d%% BLOCKED')
    m.save(item_gui=0.6)
    facing_states('culvert')


def pump_station():
    m = Model('pump_station')
    m.g('sump')
    m.box((0.5, 0, 6.0), (15.5, 1.6, 15.5), 'concrete', 'sump chamber')
    m.box((0.8, 1.6, 6.3), (15.2, 1.8, 15.2), 'metal:steel', 'chequer plate cover')
    for x, z in ((2.0, 7.4), (9.0, 7.4)):
        m.box((x, 1.8, z), (x + 5.0, 1.95, z + 4.4), 'metal:steel', 'pump hatch')
        m.box((x + 2.0, 1.95, z + 0.4), (x + 3.0, 2.2, z + 0.8), 'metal:steel', 'hatch lifting eye')
    m.box((0.8, 1.6, 12.6), (15.2, 1.7, 15.2), 'haz', 'hazard edge', faces=('up',))
    m.g('pump hoist')
    for x in (1.2, 14.2):
        m.box((x, 1.8, 13.6), (x + 0.6, 14.0, 14.2), 'paint:#f0c020', 'hoist leg')
    m.box((1.2, 14.0, 13.4), (14.8, 14.8, 14.4), 'paint:#f0c020', 'hoist beam')
    m.box((7.4, 12.8, 13.6), (8.6, 14.0, 14.2), 'metal:steel', 'chain block')
    m.box((7.9, 8.0, 13.8), (8.1, 12.8, 14.0), 'metal:steel', 'hoist chain')
    m.box((7.4, 7.4, 13.6), (8.6, 8.0, 14.2), 'metal:steel', 'hook')
    m.box((3.0, 14.8, 13.6), (13.0, 15.4, 14.2), 'label:SWL 1T', 'swl plate', faces=('north',))
    m.g('rising mains')
    for x in (4.4, 11.4):
        m.cyl((x, 1.8, 10.6), 0.6, 4.0, 'y', 'paint:#2050a0', 'rising main')
        m.cyl((x, 5.2, 10.6), 0.8, 0.4, 'y', 'metal:steel', 'pipe flange')
        m.cyl((x - 0.0, 5.8, 10.6), 0.6, 4.6, 'z', 'paint:#2050a0', 'discharge pipe')
        m.box((x - 0.8, 4.4, 10.0), (x + 0.8, 5.0, 11.2), 'paint:#c81e1e', 'non-return valve')
        m.cyl((x, 5.0, 10.6), 0.35, 1.0, 'y', 'metal:steel', 'valve spindle')
    m.box((2.0, 5.2, 15.0), (14.0, 6.4, 16.0), 'paint:#2050a0', 'discharge header')
    m.g('control kiosk')
    m.box((1.0, 1.8, 1.0), (15.0, 15.0, 6.0), GREEN, 'kiosk')
    m.box((0.7, 15.0, 0.7), (15.3, 15.6, 6.3), GREEN, 'kiosk roof')
    m.box((1.2, 2.0, 0.8), (14.8, 14.8, 1.0), LIGHT, 'kiosk door')
    m.box((1.4, 13.8, 0.76), (9.0, 14.6, 0.8), 'label:DRAINAGE PUMPS', 'legend', faces=('north',))
    m.box((9.6, 13.6, 0.76), (14.6, 14.6, 0.8), 'danger:DANGER|400V', 'danger plate', faces=('north',))
    m.box((14.4, 7.0, 0.4), (14.8, 10.0, 0.8), 'metal:steel', 'door handle')
    m.louvres(1.6, 5.0, 2.4, 4.4, 0.8, n=3)
    for x in (3.0, 7.0, 11.0):
        m.gland(x, 1.8, 3.4, 'down', r=0.3, length=0.2)
    m.box((15.0, 4.0, 2.0), (15.6, 8.0, 5.0), 'paint:#3a3d40', 'supply isolator')
    m.box((15.6, 5.6, 3.2), (16.0, 6.4, 3.8), 'paint:#c81e1e', 'isolator handle')
    m.terminal_12v(1.0, 6.0, 3.4, 'west')
    f = m.surface('north', face=0.75, title='DRAINAGE PUMPS')
    m.ctl(f, 'gauge', 'level', 3.6, 11.0, w=2.8, h=2.8, col='f2f0e6', label='SUMP %', max=100)
    m.ctl(f, 'digits', 'lvl', 8.6, 12.0, w=4.4, h=0.75, col='30a0ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'h1', 8.6, 11.1, w=4.4, h=0.7, col='b8f0c8', label='', fmt='%s')
    m.ctl(f, 'digits', 'h2', 8.6, 10.2, w=4.4, h=0.7, col='b8f0c8', label='', fmt='%s')
    m.ctl(f, 'digits', 'src', 8.6, 9.3, w=4.4, h=0.7, col='ffd040', label='', fmt='%s')
    for i, (cid, lab, col, bl) in enumerate((('r1', 'P1 RUN', '30e060', False), ('f1', 'P1 TRIP', 'ff3030', True), ('r2', 'P2 RUN', '30e060', False), ('f2', 'P2 TRIP', 'ff3030', True))):
        lamp(m, f, cid, 12.4 + (i % 2) * 1.6, 11.6 - (i // 2) * 1.6, col, lab, 0.6, blink=bl)
    for i, (cid, lab, col, bl) in enumerate((('high', 'HIGH', 'ffb020', True), ('supply', 'SUPPLY', '30e060', False), ('due', 'SERVICE', 'ffb020', False),
                                              ('drown', 'FLOODED', 'ff3030', True), ('pwr', '12V', '30e060', False))):
        lamp(m, f, cid, 3.0 + i * 2.2, 7.0, col, lab, 0.6, blink=bl)
    m.ctl(f, 'selector', 's_mode', 3.6, 4.4, w=1.4, h=1.4, col='202020', label='MODE', pos=['AUTO', 'HAND', 'OFF'])
    m.ctl(f, 'key', 'k_maint', 7.2, 4.4, w=1.3, h=1.3, col='c0a040', label='MAINT', pos=['RUN', 'ISOL'])
    m.ctl(f, 'push', 'svc', 10.4, 4.4, w=1.2, h=1.2, col='2050d0', label='SERVICE')
    m.ctl(f, 'push', 'test', 13.0, 4.4, w=0.6, h=0.6, col='f0f0f0', label='TEST')
    m.save(item_gui=0.5)
    facing_states('pump_station')


def recipes():
    R = os.path.join(ASSETS, 'recipes')
    def shaped(name, pattern, key, count=1):
        k = {c: ({'item': v[0], 'data': v[1]} if isinstance(v, tuple) else {'item': v}) for c, v in key.items()}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': k, 'result': {'item': 'irextras:' + name, 'count': count}},
                  open(os.path.join(R, name + '.json'), 'w'), indent=1)
    shaped('points_heater', ['IRI', 'IFI', 'ICI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone', 'F': 'minecraft:furnace', 'C': 'minecraft:comparator'})
    shaped('weather_station', [' F ', 'GCG', ' I '], {'F': 'minecraft:feather', 'G': 'minecraft:glass_pane', 'C': 'minecraft:compass', 'I': 'minecraft:iron_ingot'})
    shaped('rail_temp_monitor', ['G', 'C', 'I'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'I': 'minecraft:iron_ingot'})
    shaped('culvert', ['SSS', 'B B', 'SSS'], {'S': ('minecraft:stone', 0), 'B': 'minecraft:iron_bars'}, 2)
    shaped('pump_station', ['IHI', 'PRP', 'SSS'], {'I': 'minecraft:iron_ingot', 'H': 'minecraft:hopper', 'P': 'minecraft:piston', 'R': 'minecraft:redstone_block', 'S': ('minecraft:stone', 0)})


if __name__ == '__main__':
    for f in (points_heater, weather_station, rail_temp_monitor, culvert, pump_station):
        f()
    recipes()
    print('weather assets written')
