#!/usr/bin/env python3
"""Overhead line equipment for her ChatGPT list #31-32 (sectioning, isolation, pantograph interaction). Front = north.
Line pieces (section insulator, neutral section) run north-south when facing north, like the contact wire: contact
wire at y 1.5-2.5 on x 7.5-8.5, messenger at y 19-19.8. Every lamp / button / key / selector / dial on the mast gear
is a live control the Java tile reads or works (com.dogpound.railmap.ohle). 12 V boxes are wire terminals."""
import json, os
from bbkit import Model, ASSETS

LIGHT = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
FRP = 'paint:#c8641e'          # glass-fibre insulating rod (orange-brown)
CU = 'metal:copper'


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label, s=0.7, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def wire_end(m, z0, z1):
    """contact wire + messenger running in from the block edge, with a dropper"""
    m.box((7.5, 1.5, z0), (8.5, 2.5, z1), 'metal:copper', 'contact wire')
    m.box((7.6, 19.0, z0), (8.4, 19.8, z1), 'metal:copper', 'messenger wire')
    zc = (z0 + z1) / 2
    m.box((7.85, 2.5, zc - 0.15), (8.15, 19.0, zc + 0.15), 'metal:copper', 'dropper')
    m.box((7.4, 2.2, zc - 0.35), (8.6, 2.9, zc + 0.35), 'metal:brass', 'dropper clamp')
    m.box((7.4, 18.6, zc - 0.35), (8.6, 19.3, zc + 0.35), 'metal:brass', 'dropper clamp')


def runners(m, z0, z1, xs=(6.0, 9.4)):
    """the skids a pantograph slides on across an insulated gap, turned up at both ends"""
    for x in xs:
        m.box((x, 1.3, z0 + 1.2), (x + 0.6, 1.7, z1 - 1.2), 'metal:steel', 'runner skid')
        m.box((x, 1.3, z0), (x + 0.6, 1.7, z0 + 1.4), 'metal:steel', 'runner end', rot={'axis': 'x', 'angle': -22.5, 'origin': [x, 1.5, z0 + 1.3]})
        m.box((x, 1.3, z1 - 1.4), (x + 0.6, 1.7, z1), 'metal:steel', 'runner end', rot={'axis': 'x', 'angle': 22.5, 'origin': [x, 1.5, z1 - 1.3]})
        for z in (z0 + 2.0, (z0 + z1) / 2, z1 - 2.0):
            m.box((x + 0.1, 1.7, z - 0.2), (x + 0.5, 2.6, z + 0.2), 'metal:steel', 'skid hanger')


def section_insulator():
    m = Model('catenary_section_insulator')
    m.g('wire ends')
    wire_end(m, 0, 2.6)
    wire_end(m, 13.4, 16)
    m.g('end fittings')
    for z0, z1 in ((2.6, 4.2), (11.8, 13.4)):
        m.box((5.6, 1.2, z0), (10.4, 2.8, z1), 'galv', 'end fitting')
        m.bolts(6.2, 9.8, 2.0, z0 if z0 < 8 else z1, 3, face='north' if z0 < 8 else 'south', s=0.35)
        m.box((7.2, 2.8, z0 + 0.3), (8.8, 3.6, z1 - 0.3), 'galv', 'clamp block')
    m.g('insulating rods')
    for x in (6.4, 9.0):
        m.cyl((x + 0.3, 2.0, 4.2), 0.45, 7.6, 'z', FRP, 'glass-fibre rod')
        for z in (5.2, 6.4, 7.6, 8.8, 10.0):
            m.cyl((x + 0.3, 2.0, z), 0.7, 0.25, 'z', 'paint:#9a4a14', 'rod shed')
    m.g('runners')
    runners(m, 3.0, 13.0)
    m.g('arcing horns')
    for z in (4.0, 12.0):
        for x in (5.4, 10.2):
            m.box((x, 1.6, z - 0.15), (x + 0.4, 4.2, z + 0.15), 'metal:steel', 'arcing horn')
            m.box((x, 4.0, z - 0.15), (x + 0.4, 4.4, z + 1.2 if z < 8 else z - 1.2), 'metal:steel', 'horn tip')
    m.g('messenger insulator')
    m.box((7.6, 19.0, 0), (8.4, 19.8, 4.6), 'metal:copper', 'messenger')
    m.box((7.6, 19.0, 11.4), (8.4, 19.8, 16), 'metal:copper', 'messenger')
    m.box((7.4, 18.8, 4.6), (8.6, 20.0, 5.4), 'metal:brass', 'insulator cap')
    m.box((7.4, 18.8, 10.6), (8.6, 20.0, 11.4), 'metal:brass', 'insulator cap')
    m.cyl((8.0, 19.4, 5.4), 0.4, 5.2, 'z', 'porcelain', 'messenger insulator core')
    for i in range(6):
        m.cyl((8.0, 19.4, 5.8 + i * 0.8), 0.95, 0.3, 'z', 'porcelain', 'insulator shed')
    m.g('droppers to the fittings')
    for z in (3.4, 12.6):
        m.box((7.85, 3.6, z - 0.15), (8.15, 19.0, z + 0.15), 'metal:copper', 'dropper')
    m.g('identity')
    m.box((7.0, 8.0, 7.6), (9.0, 11.0, 8.4), 'plate', 'section plate', north='label:SI', south='label:SI')
    m.box((7.85, 11.0, 7.85), (8.15, 19.0, 8.15), 'metal:steel', 'plate hanger')
    m.save(item_gui=0.6)
    line_states('catenary_section_insulator')


def neutral_section():
    m = Model('catenary_neutral_section')
    m.g('wire ends')
    wire_end(m, 0, 1.6)
    wire_end(m, 14.4, 16)
    m.g('insulators in the contact line')
    for z0 in (1.6, 10.6):
        m.box((7.2, 1.3, z0), (8.8, 2.7, z0 + 0.8), 'galv', 'end fitting')
        m.cyl((8.0, 2.0, z0 + 0.8), 0.45, 2.4, 'z', FRP, 'rod insulator')
        for i in range(4):
            m.cyl((8.0, 2.0, z0 + 1.0 + i * 0.6), 0.75, 0.22, 'z', 'paint:#9a4a14', 'shed')
        m.box((7.2, 1.3, z0 + 3.2), (8.8, 2.7, z0 + 4.0), 'galv', 'end fitting')
    m.g('dead bar')
    m.box((7.4, 1.5, 5.6), (8.6, 2.5, 10.6), 'paint:#8c9196', 'neutral (dead) bar')
    m.g('runners')
    runners(m, 1.2, 14.8, xs=(6.2, 9.2))
    m.g('arcing horns')
    for z in (2.2, 5.8, 10.2, 13.8):
        for x in (6.8, 8.8):
            m.box((x, 2.4, z - 0.15), (x + 0.4, 4.6, z + 0.15), 'metal:steel', 'arcing horn')
    m.g('messenger insulators')
    m.box((7.6, 19.0, 0), (8.4, 19.8, 2.8), 'metal:copper', 'messenger')
    m.box((7.6, 19.0, 6.8), (8.4, 19.8, 9.2), 'metal:copper', 'messenger')
    m.box((7.6, 19.0, 13.2), (8.4, 19.8, 16), 'metal:copper', 'messenger')
    for z0 in (2.8, 9.2):
        m.cyl((8.0, 19.4, z0), 0.4, 4.0, 'z', 'porcelain', 'insulator core')
        for i in range(5):
            m.cyl((8.0, 19.4, z0 + 0.4 + i * 0.8), 0.95, 0.3, 'z', 'porcelain', 'shed')
    m.g('droppers')
    for z in (2.0, 6.2, 9.8, 14.0):
        m.box((7.85, 2.7, z - 0.15), (8.15, 19.0, z + 0.15), 'metal:copper', 'dropper')
    m.g('warning board')
    m.box((5.0, 9.0, 7.7), (11.0, 13.0, 8.3), 'paint:#f4f4ee', 'neutral section board', north='label:NEUTRAL|SECTION', south='label:NEUTRAL|SECTION')
    m.box((4.8, 8.8, 7.75), (11.2, 9.0, 8.25), 'paint:#1a1a1a', 'board frame')
    m.box((4.8, 13.0, 7.75), (11.2, 13.2, 8.25), 'paint:#1a1a1a', 'board frame')
    for x in (6.0, 10.0):
        m.box((x - 0.12, 13.2, 7.88), (x + 0.12, 19.0, 8.12), 'metal:steel', 'board hanger')
    m.save(item_gui=0.6)
    line_states('catenary_neutral_section')


def line_states(name):
    """line pieces: facing = which way the line runs (north/south same model, east/west turned)"""
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=south': {'model': 'irextras:' + name},
                       'facing=east': {'model': 'irextras:' + name, 'y': 90}, 'facing=west': {'model': 'irextras:' + name, 'y': 90}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def mast(m, h=32.0, x0=6.2, z0=11.0):
    """galvanised H-beam mast at the back with a base plate and anchor bolts"""
    m.g('mast')
    m.box((x0 - 1.2, 0, z0 - 1.2), (x0 + 4.8, 0.6, z0 + 4.4), 'concrete', 'foundation')
    m.box((x0 - 0.6, 0.6, z0 - 0.6), (x0 + 4.2, 1.0, z0 + 3.8), 'galv', 'base plate')
    for bx in (x0 - 0.2, x0 + 3.8):
        for bz in (z0 - 0.2, z0 + 3.4):
            m.cyl((bx, 1.0, bz), 0.25, 0.6, 'y', 'metal:steel', 'anchor bolt')
    m.box((x0, 1.0, z0), (x0 + 3.6, h, z0 + 0.5), 'galv', 'mast flange')
    m.box((x0, 1.0, z0 + 2.7), (x0 + 3.6, h, z0 + 3.2), 'galv', 'mast flange')
    m.box((x0 + 1.55, 1.0, z0 + 0.5), (x0 + 2.05, h, z0 + 2.7), 'galv', 'mast web')
    m.box((x0 - 0.2, h - 0.6, z0 - 0.2), (x0 + 3.8, h, z0 + 3.4), 'galv', 'mast cap')
    m.box((x0 + 0.4, 9.0, z0 - 0.1), (x0 + 3.2, 10.6, z0), 'plate', 'mast number', north='plate:M 47')


def isolator():
    m = Model('ohle_isolator')
    mast(m)
    m.g('switch head')
    m.box((2.0, 26.0, 9.0), (14.0, 26.8, 11.0), 'galv', 'switch base channel')
    m.box((4.0, 25.2, 10.4), (12.0, 26.0, 11.4), 'galv', 'mast bracket')
    for x in (3.4, 12.6):
        m.bushing(x, 10.0, 26.8, 3.4, sheds=4, cap=False)
        m.box((x - 0.6, 30.2, 9.4), (x + 0.6, 30.8, 10.6), 'metal:copper', 'contact jaw')
    m.box((3.0, 30.8, 9.7), (13.0, 31.4, 10.3), 'metal:copper', 'switch blade')
    m.cyl((8.0, 26.8, 10.0), 0.4, 3.2, 'y', 'porcelain', 'rotating insulator')
    for x in (2.6, 13.4):
        m.box((x - 0.15, 30.8, 9.85), (x + 0.15, 32.0, 10.15), 'metal:steel', 'arcing horn')
    m.box((3.2, 31.0, 9.8), (3.6, 32.0, 10.2), CU, 'jumper to the wire')
    m.box((12.4, 31.0, 9.8), (12.8, 32.0, 10.2), CU, 'jumper to the wire')
    m.g('operating rod')
    m.box((7.7, 15.0, 11.4), (8.3, 25.2, 12.0), 'metal:steel', 'operating rod')
    for y in (17.0, 21.0, 24.4):
        m.box((7.4, y, 11.2), (8.6, y + 0.5, 12.2), 'galv', 'rod guide')
    m.g('drive cabinet')
    m.box((2.0, 3.0, 2.2), (14.0, 15.0, 9.6), 'ral7035', 'drive cabinet')
    m.box((1.8, 15.0, 2.0), (14.2, 15.6, 9.8), 'ral7035', 'rain hood')
    m.box((2.0, 3.0, 2.0), (14.0, 15.0, 2.2), LIGHT, 'cabinet door')
    m.box((13.6, 7.0, 1.6), (14.0, 10.0, 2.0), 'metal:steel', 'door handle')
    m.box((1.6, 4.0, 3.0), (2.0, 5.2, 3.8), 'metal:steel', 'hinge')
    m.box((1.6, 12.0, 3.0), (2.0, 13.2, 3.8), 'metal:steel', 'hinge')
    m.box((9.6, 13.4, 1.96), (13.8, 14.8, 2.0), 'label:MOTORISED|ISOLATOR', 'legend', faces=('north',))
    m.box((2.2, 13.4, 1.96), (6.4, 14.8, 2.0), 'danger:DANGER|25KV', 'danger plate', faces=('north',))
    m.box((6.8, 13.6, 1.96), (9.2, 14.6, 2.0), 'plate', 'number plate', north='plate:I 12')
    m.louvres(10.4, 13.6, 3.4, 5.4, 2.0, n=3)
    m.g('hand crank and earth')
    m.cyl((1.2, 6.0, 5.8), 0.5, 0.8, 'x', 'metal:steel', 'crank socket')
    m.box((14.0, 4.0, 4.0), (15.2, 9.0, 7.6), 'paint:#f0c020', 'earth switch housing')
    m.box((15.2, 6.0, 5.2), (15.8, 6.6, 6.4), 'metal:steel', 'earth handle boss')
    m.box((14.4, 3.0, 5.4), (14.8, 4.0, 6.2), 'cable:2a7a2a', 'earth lead')
    m.box((14.0, 0.6, 5.0), (15.6, 1.0, 6.6), 'metal:copper', 'earth bar')
    m.g('cables')
    m.box((9.6, 15.6, 9.0), (10.4, 25.2, 9.8), 'paint:#3a3d40', 'conduit')
    m.gland(5.0, 3.0, 6.0, 'down', r=0.35, length=2.4)
    m.gland(8.0, 3.0, 6.0, 'down', r=0.35, length=2.4)
    m.box((2.6, 0, 4.0), (11.0, 0.6, 8.0), 'concrete', 'cabinet plinth')
    m.terminal_12v(14.0, 10.4, 7.6, 'east')
    f = m.surface('north', face=1.95, title='MOTORISED ISOLATOR')
    m.ctl(f, 'digits', 'state', 8.0, 12.2, w=10.6, h=0.9, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'secA', 5.3, 11.1, w=5.0, h=0.7, col='b8f0c8', label='', fmt='%s')
    m.ctl(f, 'digits', 'secB', 10.7, 11.1, w=5.0, h=0.7, col='b8f0c8', label='', fmt='%s')
    lamp(m, f, 'lclosed', 3.0, 9.4, 'ff2020', 'CLOSED')
    lamp(m, f, 'lopen', 5.0, 9.4, '30e060', 'OPEN')
    lamp(m, f, 'lmove', 7.0, 9.4, 'ffb020', 'MOVING', blink=True)
    lamp(m, f, 'learth', 9.0, 9.4, 'ffe020', 'EARTH')
    lamp(m, f, 'liveA', 11.0, 9.4, 'ff4040', 'LIVE A')
    lamp(m, f, 'liveB', 13.0, 9.4, 'ff4040', 'LIVE B')
    m.ctl(f, 'push', 'close', 3.2, 7.2, w=1.4, h=1.4, col='c81e1e', label='CLOSE')
    m.ctl(f, 'push', 'open', 5.6, 7.2, w=1.4, h=1.4, col='1fa34a', label='OPEN')
    m.ctl(f, 'selector', 's_mode', 8.4, 7.0, w=1.3, h=1.3, col='202020', label='CONTROL', pos=['LOC', 'REM', 'AUTO'])
    m.ctl(f, 'flag', 'flag', 11.0, 7.3, w=1.4, h=1.0, col='ff2020', label='POS')
    m.ctl(f, 'gauge', 'amps', 13.2, 7.2, w=2.0, h=2.0, col='f2f0e6', label='MOTOR A', max=10)
    m.ctl(f, 'key', 'k_earth', 3.2, 4.4, w=1.3, h=1.3, col='c0a040', label='EARTH', pos=['OFF', 'ON'])
    m.ctl(f, 'selector', 's_side', 5.8, 4.4, w=1.1, h=1.1, col='202020', label='SIDE', pos=['A', 'B'])
    m.ctl(f, 'lever', 'crank', 8.4, 4.4, w=1.0, h=1.8, col='3a3d40', label='CRANK')
    m.ctl(f, 'digits', 'ops', 11.0, 4.4, w=2.4, h=0.7, col='ffb020', label='', fmt='%d OPS')
    lamp(m, f, 'llink', 13.0, 4.9, '5bcefa', 'LINK', 0.55)
    lamp(m, f, 'lfault', 13.0, 3.8, 'ff3030', 'FAULT', 0.55, blink=True)
    lamp(m, f, 'pwr', 14.4, 4.9, '30e060', '12V', 0.5)
    m.ctl(f, 'push', 'test', 14.4, 3.8, w=0.5, h=0.5, col='f0f0f0', label='')
    m.save(item_gui=0.4)
    facing_states('ohle_isolator')


def tensioner():
    m = Model('ohle_tensioner')
    mast(m, x0=6.2, z0=11.6)
    m.g('pulley')
    m.box((7.2, 27.0, 9.4), (9.6, 28.0, 11.6), 'galv', 'pulley bracket')
    m.cyl((4.4, 28.6, 10.0), 3.0, 0.8, 'x', 'galv', 'pulley wheel')
    m.cyl((5.2, 28.6, 10.0), 3.4, 0.3, 'x', 'galv', 'wheel rim')
    m.cyl((4.0, 28.6, 10.0), 0.6, 3.6, 'x', 'metal:steel', 'axle')
    for a in range(4):
        m.box((4.6, 26.0, 9.85), (5.0, 31.2, 10.15), 'galv', 'spoke', rot={'axis': 'x', 'angle': [-45, 0, 45, 22.5][a], 'origin': [4.8, 28.6, 10.0]})
    m.box((4.5, 31.2, 0), (5.1, 31.8, 10.0), 'metal:copper', 'wire to the line')
    m.g('ratchet')
    m.cyl((3.6, 28.6, 10.0), 1.4, 0.4, 'x', 'paint:#2a2d30', 'ratchet wheel')
    m.box((2.8, 26.2, 9.6), (3.4, 27.4, 10.4), 'paint:#2a2d30', 'pawl')
    m.g('weight stack')
    m.box((4.7, 13.0, 6.85), (5.0, 28.6, 7.15), 'metal:steel', 'rope to weights')
    m.box((3.6, 25.0, 6.2), (6.0, 25.6, 7.8), 'galv', 'rope yoke')
    for i in range(14):
        y = 4.0 + i * 0.66
        m.box((3.2, y, 5.4), (6.4, y + 0.56, 8.6), 'paint:#3c3f42', 'cast-iron weight')
        m.box((3.15, y + 0.5, 6.9), (6.45, y + 0.56, 7.1), 'paint:#2a2c2e', 'weight seam')
    m.box((4.6, 13.24, 6.8), (5.0, 25.0, 7.2), 'metal:steel', 'stack rod')
    m.box((4.4, 3.6, 6.6), (5.2, 4.0, 7.4), 'galv', 'stack foot')
    m.g('weight guide')
    for z in (4.8, 9.2):
        m.box((2.6, 1.0, z - 0.2), (3.0, 26.0, z + 0.2), 'galv', 'guide tube')
        m.box((6.6, 1.0, z - 0.2), (7.0, 26.0, z + 0.2), 'galv', 'guide tube')
    for y in (2.0, 14.0, 25.0):
        m.box((2.6, y, 4.6), (7.0, y + 0.3, 9.4), 'galv', 'guide band')
    m.g('scale board')
    m.box((9.8, 4.0, 4.0), (14.2, 26.0, 4.4), 'paint:#f4f4ee', 'temperature scale board')
    for i in range(8):
        y = 5.0 + i * 2.6
        m.box((10.0, y, 3.96), (12.4, y + 0.2, 4.0), 'paint:#1a1a1a', 'scale mark', faces=('north',))
    m.box((10.0, 24.2, 3.96), (14.0, 25.6, 4.0), 'label:TEMP|SCALE', 'scale legend', faces=('north',))
    m.box((12.0, 1.0, 4.4), (12.4, 26.0, 11.6), 'galv', 'board stay')
    f = m.surface('north', face=3.9, title='TENSIONER')
    m.ctl(f, 'gauge', 'pos', 3.6, 21.0, w=2.6, h=2.6, col='f2f0e6', label='WEIGHT %', max=100)
    m.ctl(f, 'gauge', 'kn', 3.6, 16.6, w=2.6, h=2.6, col='f2f0e6', label='KN', max=25)
    m.ctl(f, 'gauge', 'temp', 3.6, 12.2, w=2.6, h=2.6, col='f2f0e6', label='C +20', max=80)
    m.ctl(f, 'flag', 'flag', 3.6, 8.8, w=1.6, h=1.0, col='ff2020', label='RANGE')
    m.ctl(f, 'digits', 'state', 3.6, 7.2, w=3.6, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'key', 'k_lock', 3.6, 5.2, w=1.3, h=1.3, col='c0a040', label='LOCK', pos=['FREE', 'LOCK'])
    m.save(item_gui=0.4)
    facing_states('ohle_tensioner')


def magnet():
    m = Model('ohle_magnet')
    m.g('post')
    m.box((6.4, 0, 9.4), (9.6, 0.6, 12.6), 'concrete', 'post foot')
    m.box((7.3, 0.6, 10.3), (8.7, 15.0, 11.7), 'galv', 'post')
    m.g('sign')
    m.box((3.0, 9.0, 9.4), (13.0, 15.6, 10.0), 'paint:#f4f4ee', 'sign board')
    m.box((2.8, 8.8, 9.5), (13.2, 9.0, 10.1), DARK, 'sign frame')
    m.box((2.8, 15.6, 9.5), (13.2, 15.8, 10.1), DARK, 'sign frame')
    m.box((2.8, 9.0, 9.5), (3.0, 15.6, 10.1), DARK, 'sign frame')
    m.box((13.0, 9.0, 9.5), (13.2, 15.6, 10.1), DARK, 'sign frame')
    m.box((4.0, 13.6, 9.36), (12.0, 15.0, 9.4), 'label:PANTOGRAPH', 'sign title', faces=('north',))
    m.box((7.0, 15.8, 9.6), (9.0, 16.0, 10.4), 'metal:steel', 'lamp hood')
    m.g('track magnet')
    m.box((2.0, 0, 1.0), (14.0, 1.4, 5.0), 'paint:#f0c020', 'magnet casing')
    m.box((2.4, 1.4, 1.4), (13.6, 1.6, 4.6), 'paint:#d8aa18', 'casing lid')
    for x in (3.0, 13.0):
        m.bolt(x, 1.6, 2.0, 'up', 0.4)
        m.bolt(x, 1.6, 4.0, 'up', 0.4)
    m.box((4.0, 1.6, 2.4), (12.0, 1.62, 3.6), 'label:APC', 'magnet legend', faces=('up',))
    m.box((7.6, 0.2, 5.0), (8.4, 0.6, 10.3), 'cable:1d1d21', 'magnet tail cable')
    m.box((2.0, 0, 5.6), (14.0, 1.4, 8.4), 'paint:#f0c020', 'second magnet casing')
    m.box((2.4, 1.4, 5.9), (13.6, 1.6, 8.1), 'paint:#d8aa18', 'second casing lid')
    for x in (3.0, 13.0):
        m.bolt(x, 1.6, 6.4, 'up', 0.4)
        m.bolt(x, 1.6, 7.6, 'up', 0.4)
    for x in (1.2, 14.8):
        for z in (1.4, 4.6, 6.0, 8.0):
            m.box((x - 0.3, 0, z - 0.3), (x + 0.3, 0.9, z + 0.3), 'metal:steel', 'sleeper clip')
    m.box((6.6, 0, 8.4), (9.4, 0.5, 9.4), 'concrete', 'cable trough')
    m.box((6.4, 0.5, 8.4), (9.6, 0.6, 9.4), 'paint:#8c8a84', 'trough lid')
    m.g('sign fixings')
    for x in (3.6, 12.4):
        for y in (9.6, 15.0):
            m.bolt(x, y, 9.4, 'north', 0.3)
    for y in (10.0, 14.4):
        m.box((7.0, y, 10.0), (9.0, y + 0.6, 10.3), 'galv', 'sign clamp')
        m.box((6.8, y - 0.1, 10.3), (9.2, y + 0.7, 11.7), 'galv', 'clamp band')
    m.box((7.4, 15.2, 9.0), (8.6, 15.8, 9.6), 'paint:#2a2d30', 'lamp housing')
    m.box((7.6, 15.0, 9.1), (8.4, 15.2, 9.5), 'glass', 'lamp lens')
    m.box((7.3, 15.0, 10.3), (8.7, 16.4, 11.7), 'galv', 'post cap')
    m.box((7.6, 6.0, 11.7), (8.4, 9.0, 12.1), 'paint:#3a3d40', 'conduit')
    m.box((7.2, 1.0, 10.1), (8.8, 1.6, 11.9), 'cable:2a7a2a', 'earth strap')
    m.g('selector box')
    m.box((6.4, 4.0, 11.7), (9.6, 7.6, 13.4), 'ral7035', 'selector box')
    m.box((6.3, 7.6, 11.6), (9.7, 7.9, 13.5), 'ral7035', 'box rain cap')
    m.box((6.6, 4.2, 13.4), (9.4, 4.7, 13.45), 'label:MODE', 'mode legend', faces=('south',))
    m.terminal_12v(9.6, 4.2, 12.5, 'east')
    m.gland(8.0, 4.0, 12.6, 'down', r=0.25, length=1.0)
    f = m.surface('north', face=9.28, title='PANTOGRAPH SIGN')
    m.ctl(f, 'digits', 'legend', 8.0, 11.4, w=8.4, h=1.8, col='ffd040', label='', fmt='%s')
    lamp(m, f, 'lit', 8.0, 15.9, 'fff0b0', '', 0.6)
    b = m.surface('south', face=16 - 13.42, title='MAGNET')
    m.ctl(b, 'selector', 's_mode', 8.0, 6.2, w=1.4, h=1.4, col='202020', label='MODE', pos=['LOWER', 'RAISE', 'COAST'])
    m.ctl(b, 'digits', 'count', 8.0, 4.6, w=2.6, h=0.6, col='ffb020', label='', fmt='%d')
    m.save(item_gui=0.55)
    facing_states('ohle_magnet')


def diagram():
    m = Model('ohle_diagram')
    m.g('cabinet')
    m.box((0, 0, 12.6), (16, 30.0, 16), DARK, 'mimic cabinet')
    m.box((0.3, 0.3, 12.4), (15.7, 29.7, 12.6), LIGHT, 'mimic face')
    m.box((-0.2, 29.6, 12.2), (16.2, 30.2, 16), DARK, 'cabinet top')
    m.box((0.6, 27.6, 12.36), (15.4, 29.4, 12.4), 'label:OHLE FEEDING DIAGRAM', 'title', faces=('north',))
    m.g('mimic lines')
    # the busbar of the line drawn across the mimic, with the section breaks
    m.box((0.6, 26.9, 12.34), (15.4, 27.1, 12.4), 'paint:#c81e1e', 'traction busbar', faces=('north',))
    for x in (3.0, 5.6, 8.2, 10.8, 13.4):
        m.box((x, 26.6, 12.32), (x + 0.2, 27.4, 12.4), 'paint:#1a1a1a', 'section break', faces=('north',))
    for y in (17.8, 9.4):
        m.box((0.6, y, 12.34), (15.4, y + 0.15, 12.4), 'paint:#1a1a1a', 'divider', faces=('north',))
    m.box((0.6, 18.2, 12.36), (5.0, 18.9, 12.4), 'label:FEEDERS', 'feeders heading', faces=('north',))
    m.box((0.6, 9.8, 12.36), (5.4, 10.5, 12.4), 'label:ISOLATORS', 'isolators heading', faces=('north',))
    m.g('mimic symbols')
    for i in range(6):                                      # a breaker square per section on the busbar
        x = 1.2 + i * 2.6
        m.box((x, 26.4, 12.3), (x + 1.0, 27.6, 12.4), 'paint:#1a1a1a', 'section symbol', faces=('north', 'east', 'west', 'up', 'down'))
        m.box((x + 0.2, 26.6, 12.28), (x + 0.8, 27.4, 12.3), 'paint:#f4f4ee', 'section symbol face', faces=('north',))
    for j in range(4):                                      # feeder symbols: a circle-breaker and a drop line
        v = 16.6 - j * 1.6
        m.box((0.6, v - 0.05, 12.34), (0.9, v + 0.05, 12.4), 'paint:#c81e1e', 'feeder line', faces=('north',))
        m.box((15.3, v - 0.4, 12.3), (15.6, v + 0.4, 12.4), 'paint:#1a1a1a', 'feeder tick', faces=('north',))
    for k in range(6):
        v = 8.6 - k * 1.2
        m.box((15.1, v - 0.3, 12.3), (15.6, v + 0.3, 12.4), 'paint:#1a1a1a', 'isolator symbol', faces=('north',))
    m.g('telephone and key')
    m.box((-1.2, 20.0, 13.0), (0, 24.0, 15.6), 'paint:#1a1a1a', 'control phone')
    m.cyl((-1.5, 22.0, 13.4), 0.4, 2.6, 'z', 'paint:#202020', 'handset')
    m.box((-1.4, 18.4, 13.6), (0, 19.6, 15.0), 'paint:#c0a040', 'key cabinet')
    for y in (4.0, 12.0, 22.0):
        m.box((16.0, y, 12.8), (16.3, y + 1.4, 13.4), 'metal:steel', 'hinge')
    m.box((-0.3, 10.0, 12.8), (0.0, 14.0, 13.2), 'metal:steel', 'door handle')
    m.box((16.0, 16.0, 12.9), (16.08, 19.0, 15.7), 'danger:DANGER|25000 V', 'danger plate', faces=('east',))
    m.g('frame and fixings')
    for x in (0.4, 15.6):
        for y in (0.6, 15.0, 29.2):
            m.bolt(x, y, 12.4, 'north', 0.35)
    m.box((1.0, -0.0, 13.0), (15.0, 0.4, 15.6), 'paint:#1a1a1c', 'plinth')
    m.louvres(10.0, 15.0, 0.8, 2.4, 12.4, n=3)
    m.terminal_12v(16.0, 2.0, 14.2, 'east')
    m.gland(8.0, 0.0, 14.2, 'down', r=0.35, length=0.4)
    f = m.surface('north', face=12.32, title='FEEDING DIAGRAM')
    for i in range(6):
        v = 25.6 - i * 1.25
        m.ctl(f, 'digits', 'sn%d' % i, 3.0, v, w=4.8, h=0.75, col='b8f0c8', label='', fmt='%s')
        m.ctl(f, 'digits', 'ss%d' % i, 8.0, v, w=4.4, h=0.75, col='ffd040', label='', fmt='%s')
        lamp(m, f, 'sl%d' % i, 11.4, v, 'ff2020', 'LIVE' if i == 0 else '', 0.55)
        lamp(m, f, 'se%d' % i, 12.8, v, 'ffe020', 'EARTH' if i == 0 else '', 0.55)
        lamp(m, f, 'sf%d' % i, 14.2, v, 'ff3030', 'FAULT' if i == 0 else '', 0.55, blink=True)
    for j in range(4):
        v = 16.6 - j * 1.6
        m.ctl(f, 'digits', 'fn%d' % j, 3.4, v, w=5.0, h=0.75, col='b8f0c8', label='', fmt='%s')
        lamp(m, f, 'fc%d' % j, 7.0, v, 'ff2020', 'CLOSED' if j == 0 else '', 0.6)
        lamp(m, f, 'ft%d' % j, 8.4, v, 'ffb020', 'TRIP' if j == 0 else '', 0.6, blink=True)
        lamp(m, f, 'fl%d' % j, 9.8, v, 'ff3030', 'LOCKOUT' if j == 0 else '', 0.6, blink=True)
        m.ctl(f, 'push', 'fC%d' % j, 11.8, v, w=0.9, h=0.9, col='c81e1e', label='CLOSE' if j == 0 else '')
        m.ctl(f, 'push', 'fO%d' % j, 13.6, v, w=0.9, h=0.9, col='1fa34a', label='OPEN' if j == 0 else '')
    for k in range(6):
        v = 8.6 - k * 1.2
        m.ctl(f, 'digits', 'in%d' % k, 3.6, v, w=5.6, h=0.7, col='b8f0c8', label='', fmt='%s')
        lamp(m, f, 'ic%d' % k, 7.6, v, 'ff2020', 'CLOSED' if k == 0 else '', 0.5)
        lamp(m, f, 'io%d' % k, 8.9, v, '30e060', 'OPEN' if k == 0 else '', 0.5)
        lamp(m, f, 'ie%d' % k, 10.2, v, 'ffe020', 'EARTH' if k == 0 else '', 0.5)
        m.ctl(f, 'push', 'iop%d' % k, 12.0, v, w=0.8, h=0.8, col='202020', label='OPERATE' if k == 0 else '')
    m.ctl(f, 'digits', 'ev', 7.0, 1.4, w=11.0, h=0.7, col='ffb020', label='', fmt='%s')
    lamp(m, f, 'pwr', 14.0, 1.4, '30e060', '12V', 0.5)
    m.ctl(f, 'push', 'test', 15.0, 1.4, w=0.5, h=0.5, col='f0f0f0', label='')
    m.save(item_gui=0.4)
    facing_states('ohle_diagram')


def recipes():
    R = os.path.join(ASSETS, 'recipes')
    def shaped(name, pattern, key, count=1):
        k = {c: ({'item': v[0], 'data': v[1]} if isinstance(v, tuple) else {'item': v}) for c, v in key.items()}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': k, 'result': {'item': 'irextras:' + name, 'count': count}},
                  open(os.path.join(R, name + '.json'), 'w'), indent=1)
    shaped('catenary_section_insulator', ['WGW', ' B '], {'W': 'irextras:catenary_wire', 'G': 'minecraft:glass_pane', 'B': ('minecraft:dye', 14)}, 2)
    shaped('catenary_neutral_section', ['WSW'], {'W': 'irextras:catenary_wire', 'S': 'irextras:catenary_section_insulator'}, 1)
    shaped('ohle_isolator', [' C ', 'ILI', ' M '], {'C': 'minecraft:iron_bars', 'I': 'minecraft:iron_ingot', 'L': 'minecraft:lever', 'M': 'irextras:catenary_mast'})
    shaped('ohle_tensioner', ['PI ', 'IM ', 'I  '], {'P': 'minecraft:iron_trapdoor', 'I': 'minecraft:iron_block', 'M': 'irextras:catenary_mast'})
    shaped('ohle_magnet', ['S', 'I', 'R'], {'S': 'minecraft:sign', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'})
    shaped('ohle_diagram', ['GGG', 'RIR', 'GGG'], {'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone', 'I': 'minecraft:iron_ingot'})


if __name__ == '__main__':
    for f in (section_insulator, neutral_section, isolator, tensioner, magnet, diagram):
        f()
    recipes()
    print('OHLE assets written')
