#!/usr/bin/env python3
"""Assets + maintenance (her 48-next list #10 #19 #24 #27 #28 #45): the Commissioning Test Set (a wheeled test cart
with its plan screen, injection set and test-lead drawers), the Remanufacturing Bench (parts washer, strip press,
inspection table, core cage, output racking, scrap skip) and the Locomotive Design + Rebuild Workshop (a three-block
wide works design office: design board, six design selectors, gauges, drawers, parts cupboard and shelf).
Models face north; controls by u = 16 - x. Also writes blockstates, item models, the inspection instrument and new
spare textures, recipes and lang lines. Live controls: see asset/TileCommissioning, TileReman, TileLocoWorkshop."""
import json, math, os, random
from PIL import Image
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, led_board, desk, strip, hood, monitor, cupboard_shelf, X
from gen_bb_depot import lamp_row, push_row, cabinet, mains_box, estop_plate

SPOTS = {}


def castor(m, x, z):
    m.box((x - 0.5, 1.9, z - 0.5), (x + 0.5, 2.3, z + 0.5), 'metal:steel', 'castor plate')
    m.box((x - 0.25, 1.2, z - 0.35), (x + 0.25, 1.9, z + 0.35), 'paint:#3a3f44', 'castor fork')
    m.cyl((x - 0.35, 0.8, z), 0.8, 0.7, 'x', 'rubber', 'castor wheel')
    m.cyl((x - 0.45, 0.8, z), 0.25, 0.9, 'x', 'metal:steel', 'castor axle')


def sockets(m, x0, y, z, cols, face='north'):
    """4 mm test sockets in a row on a north face"""
    for i, c in enumerate(cols):
        x = x0 + i * 0.7
        m.box((x - 0.22, y - 0.22, z - 0.25), (x + 0.22, y + 0.22, z), 'paint:' + c, 'test socket')
        m.box((x - 0.08, y - 0.08, z - 0.3), (x + 0.08, y + 0.08, z - 0.25), 'paint:#101010', 'socket hole')


def coil(m, x, y, z, r, col, turns=3):
    for k in range(turns):
        m.cyl((x, y - k * 0.25, z), r - k * 0.08, 0.22, 'z', 'cable:' + col, 'coiled test lead')


# ============================================================================================ Commissioning Test Set
def commissioning_set():
    m = Model('commissioning_set')
    m.g('trolley')
    for x in (2.2, 13.8):
        for z in (4.0, 12.0):
            castor(m, x, z)
    m.box((1.2, 2.3, 3.0), (14.8, 2.9, 13.0), 'paint:#2a2d30', 'trolley deck')
    for x in (1.2, 14.4):
        m.box((x, 2.9, 3.0), (x + 0.4, 9.4, 3.4), 'paint:#2a2d30', 'corner post')
        m.box((x, 2.9, 12.6), (x + 0.4, 9.4, 13.0), 'paint:#2a2d30', 'corner post')
    m.box((1.2, 9.4, 3.0), (14.8, 9.7, 13.0), 'paint:#2a2d30', 'mid deck')
    m.g('drawers')
    m.box((1.6, 2.9, 3.4), (14.4, 9.4, 12.6), 'paint:#c9ccce', 'drawer cabinet')
    dr = m.surface('north', face=3.2, title='DRAWERS')
    for (x0, x1, zid, lab) in ((1.8, 7.9, 'leads', 'TEST LEADS'), (8.1, 14.2, 'certs', 'CERTIFICATES')):
        for k, (y0, y1) in enumerate(((3.2, 6.0), (6.3, 9.1))):
            m.box((x0, y0, 3.25), (x1, y1, 3.4), 'paint:#d8dadc', 'drawer front')
            m.box(((x0 + x1) / 2 - 1.0, y1 - 0.9, 3.0), ((x0 + x1) / 2 + 1.0, y1 - 0.6, 3.25), 'metal:steel', 'drawer pull')
            m.box(((x0 + x1) / 2 - 1.3, y0 + 0.5, 3.22), ((x0 + x1) / 2 + 1.3, y0 + 1.2, 3.25), 'label:' + lab, 'drawer label')
        m.ctl(dr, 'zone', zid, X((x0 + x1) / 2), 6.15, w=x1 - x0 - 0.2, h=5.8, col='000000', label='')
    m.g('push handle')
    for x in (2.0, 14.0):
        m.box((x - 0.3, 9.7, 13.0), (x + 0.3, 21.0, 13.6), 'metal:steel', 'handle upright')
    m.cyl((1.6, 20.6, 13.3), 0.45, 12.8, 'x', 'rubber', 'push bar grip')
    m.g('console')
    m.box((1.6, 9.7, 4.6), (14.4, 22.6, 12.4), 'paint:#2f4f6a', 'instrument case')
    m.box((1.4, 22.6, 4.4), (14.6, 23.0, 12.6), 'paint:#24394d', 'case lid')
    for y in (10.0, 22.2):
        m.box((1.4, y, 4.4), (14.6, y + 0.4, 4.6), 'metal:steel', 'case trim')
    for (x, y) in ((1.9, 10.4), (14.1, 10.4), (1.9, 22.0), (14.1, 22.0)):
        m.bolt(x, y, 4.6, 'north', 0.35)
    m.box((2.0, 15.8, 4.45), (14.0, 22.0, 4.6), 'paint:#0b0f0d', 'screen bezel')
    m.box((2.3, 16.1, 4.4), (13.7, 21.7, 4.45), 'screen', 'plan screen')
    f = m.surface('north', face=4.38, title='COMMISSIONING')
    m.ctl(f, 'digits', 'title', X(8.0), 21.3, w=11.0, h=0.65, col='ffd040', label='', fmt='%s')
    for i in range(6):
        m.ctl(f, 'digits', 'd%d' % (i + 1), X(8.0), 20.5 - i * 0.72, w=11.0, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'status', X(8.0), 16.35, w=11.0, h=0.5, col='8ad4ff', label='', fmt='%s')
    m.box((2.0, 9.9, 4.45), (14.0, 15.6, 4.6), 'paint:#1e2a33', 'control plate')
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('lscan', '8ad4ff', 'SCAN'), ('lrun', 'ffb020', 'RUN', True), ('lpass', '30e060', 'PASS'),
                    ('lfail', 'ff3030', 'FAIL', True), ('lwarn', 'ffb020', 'NEW'), ('lauth', '8a8aff', 'AUTH'), ('ltgt', 'f0f0f0', 'IED')], X(2.8), 15.0, step=1.5, size=0.55)
    for i, (cid, lab) in enumerate((('nass', 'ASSETS'), ('nnew', 'NEW'), ('nconn', 'LINKS'), ('nver', 'VERIF'), ('ntest', 'TESTS'), ('npass', 'PASS'), ('nfail', 'FAIL'))):
        m.ctl(f, 'digits', cid, X(2.9 + i * 1.7), 13.5, w=1.4, h=0.6, col='ffd040' if cid != 'nfail' else 'ff6040', label='', fmt='%d')
        m.ctl(f, 'label', '', X(2.9 + i * 1.7), 12.95, w=0, h=0, col='e8e8e8', label=lab)
    push_row(m, f, [('scan', '2050d0', 'SCAN'), ('plan', '20a0a0', 'PLAN'), ('run', '1fa34a', 'RUN'), ('abort', 'd02020', 'ABORT'),
                    ('prev', '505860', 'PREV'), ('next', '505860', 'NEXT'), ('sign', 'f0d020', 'SIGN'), ('print', 'f0f0f0', 'PRINT')], X(2.8), 11.6, step=1.5, size=0.85)
    m.g('selector ledge')
    m.box((1.6, 9.4, 1.4), (14.4, 9.8, 4.6), 'paint:#24394d', 'ledge')
    m.box((1.6, 9.8, 1.4), (14.4, 10.0, 1.6), 'metal:steel', 'ledge lip')
    led = m.surface('up', face=16 - 9.8, title='LEDGE')
    for i, (cid, lab, pos) in enumerate((('s_rad', 'RADIUS', ['16M', '32M', '64M']), ('s_scope', 'SCOPE', ['NEW', 'ALL']),
                                         ('s_page', 'PAGE', ['ASSETS', 'TESTS', 'LINKS', 'BENCH', 'CERTS']))):
        m.ctl(led, 'selector', cid, X(2.8 + i * 2.3), 3.0, w=1.2, h=1.2, col='202020', label=lab, pos=pos)
        m.ctl(led, 'label', '', X(2.8 + i * 2.3), 1.9, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(led, 'key', 'k_auth', X(10.0), 3.0, w=1.1, h=1.1, col='c0a040', label='AUTHORISED', pos=['OFF', 'ON'])
    m.ctl(led, 'label', '', X(10.0), 1.9, w=0, h=0, col='e8e8e8', label='AUTHORISED')
    m.ctl(led, 'push', 'test', X(11.8), 3.0, w=0.8, h=0.8, col='b0b0b0', label='LAMP TEST')
    m.ctl(led, 'label', '', X(11.8), 1.9, w=0, h=0, col='e8e8e8', label='TEST')
    m.ctl(led, 'mushroom', 'm_estop', X(13.4), 3.0, w=1.3, h=1.3, col='d02020', label='E-STOP')
    m.g('injection set')
    m.box((3.0, 23.0, 5.2), (13.0, 26.6, 11.6), 'paint:#c8c0a8', 'injection set case')
    m.box((2.8, 26.6, 5.0), (13.2, 27.0, 11.8), 'paint:#8a8270', 'injection set lid')
    m.box((6.6, 27.0, 7.8), (9.4, 27.6, 8.6), 'paint:#202020', 'carry handle')
    m.box((3.2, 23.2, 5.0), (12.8, 26.4, 5.2), 'paint:#2a2a2a', 'injection panel')
    inj = m.surface('north', face=4.98, title='INJECTION SET')
    m.ctl(inj, 'gauge', 'prog', X(4.6), 25.0, w=2.2, h=2.2, col='f2f0e6', label='PLAN %', max=100)
    m.ctl(inj, 'digits', 'tgt', X(8.8), 25.8, w=4.6, h=0.6, col='8ad4ff', label='', fmt='%s')
    m.ctl(inj, 'zone', 'target', X(8.8), 25.8, w=4.8, h=0.8, col='000000', label='TARGET')
    m.ctl(inj, 'label', '', X(8.8), 25.1, w=0, h=0, col='e8e8e8', label='TARGET IED')
    m.ctl(inj, 'push', 'inject', X(11.6), 24.4, w=1.0, h=1.0, col='e08020', label='INJECT')
    m.ctl(inj, 'label', '', X(11.6), 23.6, w=0, h=0, col='e8e8e8', label='INJECT')
    sockets(m, 6.6, 24.0, 5.0, ['#c81e1e', '#1d1d21', '#c81e1e', '#1d1d21', '#f0c818', '#2050d0'])
    m.box((6.4, 23.3, 4.98), (10.8, 23.7, 5.0), 'label:I V TRIP CB', 'socket legend')
    m.g('test leads')
    m.box((0.6, 12.0, 6.0), (1.6, 12.4, 10.0), 'metal:steel', 'lead hook bar')
    for i, col in enumerate(('c81e1e', '1d1d21', 'f0c818')):
        z = 6.6 + i * 1.3
        m.box((0.4, 11.0, z - 0.1), (0.7, 12.2, z + 0.1), 'metal:steel', 'hook')
        for k in range(4):
            m.box((0.2 + k * 0.05, 8.0 - k * 0.3, z - 0.5 + k * 0.05), (0.6, 11.2, z - 0.3 + k * 0.05), 'cable:' + col, 'hanging lead')
        m.box((0.1, 7.4, z - 0.25), (0.6, 8.0, z + 0.25), 'paint:#' + col, 'crocodile clip')
    m.g('cable reel')
    m.cyl((15.0, 6.0, 8.0), 2.0, 0.3, 'x', 'paint:#c82020', 'reel flange')
    m.cyl((15.3, 6.0, 8.0), 1.5, 0.8, 'x', 'cable:202020', 'wound lead')
    m.cyl((16.1, 6.0, 8.0), 2.0, 0.3, 'x', 'paint:#c82020', 'reel flange')
    m.cyl((14.6, 6.0, 8.0), 0.3, 2.0, 'x', 'metal:steel', 'reel spindle')
    m.g('printer + clipboard')
    m.box((11.0, 23.0, 11.6), (14.2, 24.4, 12.6), 'paint:#d8d8d4', 'ticket printer')
    m.box((11.4, 24.0, 12.6), (13.8, 24.1, 12.7), 'paint:#1a1a1a', 'paper slot')
    m.box((1.8, 23.0, 9.0), (4.4, 23.2, 12.2), 'wood', 'clipboard')
    m.box((2.0, 23.2, 9.4), (4.2, 23.25, 12.0), 'paint:#f4f2ea', 'test sheet')
    m.box((2.8, 23.2, 11.6), (3.4, 23.5, 12.1), 'metal:steel', 'clip')
    m.g('nameplates')
    m.box((4.0, 2.95, 3.0), (12.0, 3.15, 3.02), 'plate:COMMISSIONING TEST SET', 'nameplate')
    m.box((14.4, 12.0, 6.0), (14.6, 15.0, 10.5), 'label:ACCEPTANCE|TESTS', 'side label', faces=('east',))
    m.box((1.4, 15.0, 6.0), (1.6, 18.0, 10.5), 'warn', 'hv warning', faces=('west',))
    m.box((1.4, 12.0, 6.0), (1.6, 14.6, 10.5), 'danger:TEST ONLY|ON ISOLATED|PLANT', 'danger plate', faces=('west',))
    m.g('screen hood')
    m.box((1.8, 22.0, 3.2), (14.2, 22.3, 4.6), 'paint:#24394d', 'screen hood')
    for x in (1.8, 13.9):
        m.box((x, 15.8, 3.6), (x + 0.3, 22.0, 4.6), 'paint:#24394d', 'hood cheek')
    m.g('vents + rear panel')
    m.louvres(4.0, 12.0, 17.0, 21.0, 12.4 + 0.0001, n=4)
    m.box((3.0, 10.4, 12.4), (13.0, 15.6, 12.6), 'paint:#1e2a33', 'rear connector panel')
    for i in range(6):
        x = 3.6 + i * 1.5
        m.cyl((x, 13.0, 12.6), 0.4, 0.4, 'z', 'metal:brass', 'test lead connector')
        m.box((x - 0.5, 11.4, 12.6), (x + 0.5, 11.8, 12.65), 'label:' + ['L1', 'L2', 'L3', 'N', 'E', 'TRIP'][i], 'connector label')
    for i in range(4):
        m.box((3.4 + i * 1.2, 14.4, 12.6), (4.2 + i * 1.2, 15.2, 13.0), 'paint:#2a2a2a', 'fuse holder')
    m.box((10.6, 14.2, 12.6), (12.6, 15.4, 13.2), 'paint:#3a3f44', 'mains inlet (battery charger)')
    m.g('bumpers + brakes')
    for (x, z) in ((1.0, 2.8), (14.6, 2.8), (1.0, 12.8), (14.6, 12.8)):
        m.box((x - 0.2, 2.2, z - 0.2), (x + 0.4, 3.2, z + 0.4), 'rubber', 'corner bumper')
    for x in (2.2, 13.8):
        m.box((x - 0.3, 1.9, 3.0), (x + 0.3, 2.4, 3.6), 'paint:#c82020', 'castor brake pedal')
    m.g('radio link')
    m.cyl((13.4, 27.0, 10.8), 0.15, 4.0, 'y', 'paint:#1a1a1a', 'radio antenna')
    m.box((13.0, 26.6, 10.4), (13.8, 27.0, 11.2), 'paint:#2a2a2a', 'antenna base')
    m.g('manuals')
    for i, c in enumerate(('#2a5d8a', '#8a2a2a', '#2a8a4a', '#8a6a2a')):
        m.box((14.45, 3.2 + i * 1.4, 4.0), (15.2, 4.4 + i * 1.4, 7.2), 'paint:' + c, 'procedure manual')
    m.box((14.4, 3.0, 3.8), (15.4, 3.2, 7.4), 'metal:steel', 'manual rack')
    m.box((15.2, 3.0, 3.8), (15.4, 9.0, 4.0), 'metal:steel', 'manual rack end')
    m.terminal_12v(14.4, 4.0, 10.0, 'east')
    m.bolts(2.5, 13.5, 2.6, 3.0, 6, face='north')
    m.save(item_gui=0.4)


# ============================================================================================== Remanufacturing Bench
def reman_bench():
    m = Model('reman_bench')
    m.g('bench frame')
    for x in (0.6, 15.0):
        for z in (2.0, 14.0):
            m.box((x, 0, z), (x + 0.6, 8.0, z + 0.6), 'paint:#3a5f8a', 'bench leg')
    m.box((0.4, 8.0, 1.8), (15.8, 8.8, 14.8), 'metal:steel', 'steel bench top')
    m.box((0.6, 2.0, 2.2), (15.6, 2.4, 14.4), 'paint:#3a5f8a', 'lower rail')
    m.g('core cage (in)')
    m.box((0.9, 0.2, 2.6), (5.6, 6.6, 7.6), 'mesh', 'core cage')
    for x in (0.9, 5.4):
        m.box((x, 0.2, 2.4), (x + 0.2, 6.8, 2.6), 'paint:#f0c818', 'cage upright')
    m.box((0.9, 6.6, 2.4), (5.6, 6.8, 7.6), 'paint:#f0c818', 'cage rim')
    m.box((1.4, 0.4, 3.0), (3.4, 2.6, 5.4), 'paint:#5a4a3a', 'core: traction motor (used)')
    m.cyl((2.4, 2.6, 4.2), 0.6, 0.8, 'y', 'metal:copper', 'core: commutator')
    m.box((3.6, 0.4, 3.2), (5.2, 1.6, 6.6), 'paint:#4a4a44', 'core: bearing box')
    io = m.surface('north', face=2.38, title='BINS')
    m.ctl(io, 'zone', 'bin_in', X(3.25), 3.5, w=4.6, h=6.2, col='000000', label='')
    m.box((1.4, 5.4, 2.36), (5.0, 6.2, 2.4), 'label:CORES IN', 'cage label')
    m.g('output racking (out)')
    m.box((10.6, 0.2, 2.6), (15.4, 7.0, 7.4), 'paint:#2b2f33', 'output rack')
    for y in (2.4, 4.6):
        m.box((10.8, y, 2.8), (15.2, y + 0.3, 7.2), 'galv', 'rack shelf')
    m.box((11.0, 0.5, 3.0), (12.6, 2.2, 5.0), 'paint:#2a6a3a', 'reman unit (grade A)')
    m.box((12.8, 0.5, 3.0), (14.8, 1.6, 5.4), 'paint:#2a6a3a', 'reman unit (grade A)')
    m.box((11.2, 2.7, 3.2), (13.0, 4.0, 5.0), 'paint:#8a8a2a', 'sub-parts tray')
    m.ctl(io, 'zone', 'bin_out', X(13.0), 3.6, w=4.6, h=6.6, col='000000', label='')
    m.box((11.0, 6.0, 2.56), (15.0, 6.8, 2.6), 'label:REMANUFACTURED', 'rack label')
    m.g('scrap skip')
    m.box((6.0, 0.2, 2.6), (10.2, 3.8, 6.8), 'paint:#c86a1a', 'scrap skip')
    m.box((5.8, 3.6, 2.4), (10.4, 4.0, 7.0), 'paint:#8a4a12', 'skip rim')
    m.box((6.4, 3.0, 3.0), (9.8, 3.6, 6.4), 'paint:#5a5048', 'scrap metal')
    m.box((6.6, 3.5, 3.4), (7.8, 3.9, 4.6), 'metal:steel', 'scrap offcut')
    m.box((8.4, 3.4, 4.8), (9.6, 3.8, 5.8), 'metal:copper', 'copper offcut')
    m.ctl(io, 'zone', 'bin_scrap', X(8.1), 2.0, w=4.2, h=3.4, col='000000', label='')
    m.box((6.6, 1.0, 2.56), (9.6, 1.8, 2.6), 'label:METAL SCRAP', 'skip label')
    m.g('parts washer')
    m.box((0.8, 8.8, 9.0), (6.4, 13.4, 14.6), 'metal:steel', 'washer tank')
    m.box((1.1, 13.0, 9.3), (6.1, 13.4, 14.3), 'paint:#3a6070', 'wash liquor')
    m.box((0.6, 13.4, 8.8), (6.6, 13.8, 9.2), 'metal:steel', 'tank rim front')
    m.box((0.6, 13.4, 14.4), (6.6, 18.6, 14.8), 'metal:steel', 'splash back')
    m.box((0.6, 18.6, 9.0), (6.6, 18.9, 14.8), 'metal:steel', 'lid (open)', rot={'axis': 'x', 'angle': 45, 'origin': [3.6, 18.6, 14.6]})
    m.cyl((5.6, 13.4, 13.6), 0.3, 3.6, 'y', 'metal:copper', 'tap riser')
    m.box((4.0, 16.8, 13.4), (5.9, 17.2, 13.8), 'metal:copper', 'tap spout')
    m.box((1.4, 13.6, 10.0), (2.0, 14.4, 12.6), 'paint:#f0c818', 'scrub brush')
    m.box((0.4, 10.0, 9.6), (0.8, 12.4, 12.0), 'paint:#c82020', 'heater box', west='danger:415V')
    m.cyl((0.8, 11.2, 10.8), 0.3, 0.6, 'x', 'metal:brass', 'heater gland')
    m.g('strip press')
    for x in (7.2, 11.6):
        m.box((x, 8.8, 9.6), (x + 0.8, 22.0, 10.8), 'paint:#c82020', 'press column')
        m.box((x, 8.8, 13.2), (x + 0.8, 22.0, 14.4), 'paint:#c82020', 'press column')
    m.box((7.0, 22.0, 9.4), (12.6, 23.4, 14.6), 'paint:#a01818', 'press crown')
    m.cyl((9.8, 17.0, 12.0), 1.4, 5.0, 'y', 'paint:#2a2a2a', 'hydraulic cylinder')
    m.cyl((9.8, 14.0, 12.0), 0.6, 3.0, 'y', 'metal:steel', 'ram')
    m.box((8.2, 13.4, 10.6), (11.4, 14.0, 13.4), 'metal:steel', 'press platen')
    m.box((8.0, 8.8, 10.4), (11.6, 9.8, 13.6), 'metal:steel', 'press bolster')
    m.box((8.6, 9.8, 11.0), (11.0, 11.4, 13.0), 'paint:#5a4a3a', 'work piece (core)')
    for x in (7.0, 12.4):
        m.box((x, 9.8, 9.2), (x + 0.2, 21.0, 9.4), 'paint:#f0c818', 'light curtain post')
    m.box((7.2, 20.6, 9.25), (12.4, 20.8, 9.35), 'paint:#f0c818', 'light curtain head')
    m.g('hydraulic power pack')
    m.box((12.8, 8.8, 11.2), (15.6, 12.6, 14.6), 'paint:#2a4a6a', 'oil reservoir')
    m.cyl((14.2, 12.6, 12.9), 0.9, 1.6, 'y', 'paint:#3a3f44', 'pump motor')
    m.cyl((13.2, 12.6, 14.0), 0.3, 6.0, 'y', 'cable:202020', 'hydraulic hose')
    m.cyl((13.2, 18.4, 13.0), 0.3, 1.2, 'z', 'cable:202020', 'hose bend')
    m.box((15.6, 9.4, 12.0), (15.8, 10.4, 13.6), 'label:HYD 200 BAR', 'pack label', faces=('east',))
    m.g('inspection table')
    m.box((12.8, 8.8, 2.4), (15.6, 9.2, 8.4), 'paint:#e8e8e2', 'inspection mat')
    m.cyl((15.0, 9.2, 7.6), 0.25, 5.0, 'y', 'metal:steel', 'lamp arm')
    m.box((13.0, 14.0, 6.6), (15.2, 14.6, 8.0), 'paint:#3a2a6a', 'UV crack lamp')
    m.box((13.2, 13.9, 6.8), (15.0, 14.0, 7.8), 'lamp:8a5aff', 'UV tube')
    m.box((13.4, 9.2, 3.0), (14.8, 9.5, 4.2), 'metal:steel', 'vernier calipers')
    m.cyl((14.2, 9.2, 5.4), 0.6, 0.4, 'y', 'glass', 'magnifier')
    m.g('control pedestal')
    m.box((3.2, 8.8, 2.0), (12.6, 21.4, 2.8), 'paint:#2b2f33', 'control panel')
    m.box((3.0, 21.4, 1.8), (12.8, 21.8, 3.0), 'metal:steel', 'panel cap')
    for (x, y) in ((3.5, 9.2), (12.3, 9.2), (3.5, 21.0), (12.3, 21.0)):
        m.bolt(x, y, 2.0, 'north', 0.3)
    f = m.surface('north', face=1.98, title='REMAN BENCH')
    m.box((3.6, 18.4, 1.95), (12.2, 21.0, 2.0), 'screen', 'grading screen')
    for i, (cid, col) in enumerate((('line1', 'ffd040'), ('line2', '7cf29a'), ('line3', '8ad4ff'))):
        m.ctl(f, 'digits', cid, X(7.9), 20.4 - i * 0.8, w=8.2, h=0.6, col=col, label='', fmt='%s')
    for i, (cid, lab, mx) in enumerate((('wtemp', 'WASH C', 100), ('press', 'PRESS KN', 200), ('prog', 'CYCLE %', 100))):
        m.ctl(f, 'gauge', cid, X(5.0 + i * 2.9), 16.6, w=2.3, h=2.3, col='f2f0e6', label=lab, max=mx)
        m.ctl(f, 'label', '', X(5.0 + i * 2.9), 15.2, w=0, h=0, col='e8e8e8', label=lab)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mains', '30e060', 'MAINS'), ('run', 'ffb020', 'RUN', True), ('lwash', '8ad4ff', 'WASH'),
                    ('lstrip', 'ffb020', 'STRIP'), ('linsp', '8a5aff', 'INSP')], X(4.0), 14.2, step=1.55, size=0.5)
    lamp_row(m, f, [('ga', '30e060', 'A'), ('gb', 'b8e86a', 'B'), ('gc', 'ffb020', 'C'), ('gs', 'ff3030', 'SCRAP'), ('kit', '8ad4ff', 'KIT'), ('full', 'ff3030', 'FULL', True)],
             X(4.0), 12.9, step=1.55, size=0.5)
    for i, (cid, lab) in enumerate((('nin', 'CORES'), ('ndone', 'DONE'), ('na', 'A'), ('nb', 'B'), ('nc', 'C'), ('ns', 'SCRAP'))):
        m.ctl(f, 'digits', cid, X(4.0 + i * 1.55), 11.7, w=1.2, h=0.55, col='ffd040', label='', fmt='%d')
        m.ctl(f, 'label', '', X(4.0 + i * 1.55), 11.2, w=0, h=0, col='e8e8e8', label=lab)
    push_row(m, f, [('start', '1fa34a', 'START'), ('abort', 'd02020', 'ABORT'), ('count', '2050d0', 'CORES'), ('print', 'f0f0f0', 'PRINT'), ('test', 'b0b0b0', 'TEST')],
             X(4.0), 10.2, step=1.6, size=0.85)
    m.g('selector strip')
    m.box((3.2, 8.8, 0.6), (12.6, 9.2, 2.0), 'paint:#2b2f33', 'selector shelf')
    sel = m.surface('up', face=16 - 9.2, title='SELECTORS')
    for i, (cid, lab, pos) in enumerate((('s_mode', 'MODE', ['SINGLE', 'AUTO']), ('s_wash', 'WASH C', ['40', '60', '80']), ('s_kit', 'KITS', ['ON', 'OFF']))):
        m.ctl(sel, 'selector', cid, X(4.4 + i * 2.2), 1.5, w=1.1, h=1.1, col='202020', label=lab, pos=pos)
        m.ctl(sel, 'label', '', X(4.4 + i * 2.2), 0.7, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(sel, 'mushroom', 'm_estop', X(11.4), 1.3, w=1.2, h=1.2, col='d02020', label='E-STOP')
    m.g('extraction hood')
    m.box((0.6, 23.0, 8.6), (6.6, 24.0, 14.8), 'galv', 'extract hood')
    m.box((2.6, 24.0, 10.6), (4.6, 29.0, 12.6), 'galv', 'extract duct')
    m.box((0.6, 22.6, 8.6), (6.6, 23.0, 8.8), 'mesh', 'grease filter')
    m.g('jib hoist')
    m.cyl((15.2, 8.8, 14.6), 0.35, 20.0, 'y', 'paint:#f0c818', 'jib post')
    m.box((8.0, 28.4, 14.2), (15.6, 29.0, 15.0), 'paint:#f0c818', 'jib arm')
    m.box((10.6, 27.4, 14.0), (12.0, 28.4, 15.2), 'paint:#3a3f44', 'chain hoist')
    for k in range(6):
        m.box((11.2, 21.6 + k, 14.5), (11.4, 22.4 + k, 14.7), 'metal:steel', 'hoist chain')
    m.box((10.9, 21.0, 14.3), (11.7, 21.6, 14.9), 'paint:#c82020', 'hook block')
    m.g('tool board')
    m.box((6.8, 23.6, 14.6), (15.0, 28.0, 15.0), 'wood', 'tool board')
    for i, (x, h, c) in enumerate(((7.4, 3.0, 'metal:steel'), (8.4, 2.6, 'metal:steel'), (9.3, 2.2, 'metal:steel'), (10.2, 1.8, 'paint:#c82020'), (12.6, 2.4, 'paint:#2050d0'), (13.6, 2.8, 'metal:steel'))):
        m.box((x, 27.6 - h, 14.4), (x + 0.4, 27.6, 14.6), c, 'hanging tool')
        m.box((x + 0.1, 27.6, 14.5), (x + 0.3, 27.8, 14.6), 'metal:steel', 'peg')
    m.box((11.0, 24.0, 14.4), (12.2, 25.4, 14.6), 'paint:#3a3f44', 'bearing puller')
    m.g('services')
    mains_box(m, 15.8, 1.0, 10.0, face='east')
    m.terminal_12v(0.6, 3.0, 11.0, 'west')
    m.box((3.6, 21.8, 2.0), (12.2, 22.6, 2.6), 'plate:REMANUFACTURING BENCH', 'nameplate', north='plate:REMANUFACTURING BENCH')
    m.box((0.4, 14.0, 9.4), (0.6, 16.0, 12.0), 'warn', 'hot liquid warning', faces=('west',))
    m.box((15.6, 14.0, 9.6), (15.8, 17.0, 12.8), 'danger:CRUSH|HAZARD', 'press danger', faces=('east',))
    m.save(item_gui=0.4)


# ================================================================================== Loco Design + Rebuild Workshop
def loco_workshop():
    m = Model('loco_workshop')
    back_wall(m, panel='#3a3f44', plaster='#e2e4e0')
    m.g('design board')
    led_board(m, -13, 13, 18.4, 28.8)
    m.box((-13, 29.0, 14.7), (13, 31.0, 15.4), 'paint:#2a3a4a', 'board header')
    m.box((-12.6, 29.2, 14.62), (12.6, 30.8, 14.7), 'plate:LOCOMOTIVE DESIGN + REBUILD', 'board title')
    b = m.surface('north', face=14.42, title='DESIGN BOARD')
    for i in range(6):
        m.ctl(b, 'digits', 'd%d' % (i + 1), X(0.0), 27.8 - i * 1.6, w=24.0, h=1.3, col='ffd040' if i == 0 else '7cf29a', label='', fmt='%s')
    m.g('gauge panel')
    m.box((14.4, 18.4, 14.6), (29.6, 28.8, 15.4), 'paint:#24282c', 'gauge panel')
    gp = m.surface('north', face=14.58, title='DESIGN FIGURES')
    for i, (cid, lab, mx) in enumerate((('gpow', 'POWER %', 120), ('gvmax', 'VMAX KMH', 200), ('gaxle', 'AXLE T', 30))):
        m.ctl(gp, 'gauge', cid, X(17.2 + i * 5.0), 25.6, w=4.0, h=4.0, col='f2f0e6', label=lab, max=mx)
        m.ctl(gp, 'label', '', X(17.2 + i * 5.0), 23.2, w=0, h=0, col='e8e8e8', label=lab)
    for i, (cid, lab, fmt) in enumerate((('mass', 'MASS T', '%d'), ('kw', 'KW', '%d'), ('te', 'TE KN', '%d'), ('vmax', 'KM/H', '%d'), ('cost', 'COST $', '%d'))):
        m.ctl(gp, 'digits', cid, X(16.2 + i * 2.9), 21.6, w=2.6, h=0.9, col='8ad4ff', label='', fmt=fmt)
        m.ctl(gp, 'label', '', X(16.2 + i * 2.9), 20.6, w=0, h=0, col='e8e8e8', label=lab)
    for x in (14.8, 29.2):
        for y in (18.8, 28.4):
            m.bolt(x, y, 14.6, 'north', 0.35)
    m.g('drawings')
    for i, (x0, lab) in enumerate(((-13.0, 'BOGIE GA'), (-8.2, 'TM COOLING'), (-3.4, 'GEAR 4.21'), (1.4, 'BRAKE RIG'), (6.2, 'TANK LAYOUT'))):
        m.box((x0, 13.0, 15.32), (x0 + 4.2, 17.6, 15.4), 'paint:#e8eef4', 'drawing sheet')
        m.box((x0 + 0.3, 13.3, 15.3), (x0 + 3.9, 14.1, 15.32), 'label:' + lab, 'drawing title')
        m.box((x0 + 0.5, 14.6, 15.3), (x0 + 3.7, 17.0, 15.32), 'paint:#5a7aa0', 'drawing linework')
        m.box((x0 + 1.9, 17.4, 15.2), (x0 + 2.3, 17.8, 15.32), 'paint:#c82020', 'drawing pin')
    # left wing: a sectioned traction motor + gearbox on a plinth (the demonstrator) and the parts rack
    m.g('cutaway demonstrator')
    m.box((-13.6, 0, 9.6), (-7.0, 4.0, 14.8), 'paint:#3a3f44', 'plinth')
    m.box((-13.4, 4.0, 9.8), (-7.2, 4.4, 14.6), 'metal:steel', 'plinth top')
    m.cyl((-13.0, 7.2, 12.2), 2.6, 4.2, 'x', 'paint:#2a4a6a', 'traction motor frame')
    m.box((-11.2, 4.6, 9.4), (-9.4, 9.8, 12.2), 'paint:#c87a1a', 'cutaway section')
    m.cyl((-11.0, 7.2, 12.2), 1.6, 1.6, 'x', 'metal:copper', 'stator windings')
    m.cyl((-12.6, 7.2, 12.2), 0.5, 6.0, 'x', 'metal:steel', 'rotor shaft')
    m.box((-8.8, 4.4, 10.4), (-7.4, 10.4, 14.0), 'paint:#3a3f44', 'gear case')
    m.cyl((-8.6, 6.4, 12.2), 1.6, 0.8, 'x', 'metal:brass', 'bull gear')
    m.cyl((-8.6, 8.6, 12.2), 0.7, 0.8, 'x', 'metal:steel', 'pinion')
    m.box((-13.0, 4.4, 9.5), (-10.0, 5.2, 9.6), 'label:AC TRACTION MOTOR', 'demonstrator label')
    m.g('parts rack')
    m.box((-6.6, 0, 11.0), (-1.2, 12.0, 15.2), 'galv', 'parts rack')
    for y in (3.0, 6.0, 9.0):
        m.box((-6.4, y, 11.2), (-1.4, y + 0.3, 15.0), 'galv', 'rack shelf')
    for (x, y, c, n) in ((-6.0, 0.4, '#2a6a3a', 'fan unit'), (-3.8, 0.4, '#3a3f44', 'gearbox'), (-6.0, 3.3, '#2a4a6a', 'battery module'), (-3.6, 3.3, '#2a4a6a', 'battery module'),
                         (-6.0, 6.3, '#c8c0a8', 'brake disc'), (-3.6, 6.3, '#5a4a3a', 'brake blocks'), (-6.0, 9.3, '#8a8a8a', 'steel plates')):
        m.box((x, y, 11.6), (x + 2.0, y + 2.2, 14.4), 'paint:' + c, n)
    m.box((-6.4, 11.4, 10.96), (-1.4, 12.0, 11.0), 'label:REBUILD PARTS', 'rack label')
    m.g('the desk')
    desk(m, {('l', 0): 'd1', ('l', 1): 'd2', ('l', 2): 'd3', ('r', 0): 'd4', ('r', 1): 'd5', ('r', 2): 'd6'}, body='#3a3f44', front='#4c535a', pull='metal:steel')
    sf = strip(m, 3.6, 8.4)
    for i, (cid, lab, n) in enumerate((('s_motor', 'TRACTION', 4), ('s_gear', 'GEAR', 5), ('s_cool', 'COOLING', 4), ('s_store', 'STORAGE', 6), ('s_bogie', 'BOGIES', 4), ('s_brake', 'BRAKES', 4))):
        pos = [['AS BUILT', 'DC SERIES', 'AC IGBT', 'PM SYNC'], ['AS BUILT', '4.93', '4.21', '3.62', '3.12'], ['AS BUILT', 'NATURAL', 'FORCED', 'FORCED+OIL'],
               ['AS BUILT', 'SMALL', 'LARGE', 'BATTERY', 'BEMU', 'HYDROGEN'], ['AS BUILT', 'RIGID COCO', 'STEERING', 'FLEXICOIL'], ['AS BUILT', 'BLOCKS', 'DISC', 'DISC+RHEO']][i]
        m.ctl(sf, 'selector', cid, X(1.6 + i * 2.55), 7.4, w=1.3, h=1.3, col='202020', label=lab, pos=pos)
        m.ctl(sf, 'label', '', X(1.6 + i * 2.55), 6.3, w=0, h=0, col='e8e8e8', label=lab)
    push_row(m, sf, [('sel', 'f0f0f0', 'SELECT'), ('load', '505860', 'LOAD'), ('apply', 'e08020', 'REBUILD'), ('accept', '1fa34a', 'ACCEPT'),
                     ('print', 'f0f0f0', 'PRINT'), ('reset', '505860', 'RESET'), ('test', 'b0b0b0', 'TEST')], X(1.4), 4.6, step=2.15, size=0.85)
    hd = hood(m)
    lamp_row(m, hd, [('pwr', '30e060', '12V'), ('link', '8ad4ff', 'LOCO'), ('pend', 'ffb020', 'NOT ACC', True), ('run', 'ffb020', 'TEST RUN'),
                     ('acc', '30e060', 'ACCEPTED'), ('fail', 'ff3030', 'FAILED', True), ('parts', 'ff3030', 'PARTS'), ('warn', 'ffb020', 'CHECK')], X(1.3), 11.0, step=1.9, size=0.55)
    monitor(m, 1.4, 14.6, 12.2, 17.4, 10.4, ['n1', 'n2', 'n3', 'n4', 'n5', 'n6'], cols=['ffd040', '7cf29a', '7cf29a', '7cf29a', '7cf29a', '7cf29a'])
    # right wing: parts cupboard + shelf (real storage), and the drafting lamp
    SPOTS['loco_workshop'] = cupboard_shelf(m, 18.4, 30.2, ['cup'], 6, title='WORKS SHELF', body='#3a3f44', door='#4c535a')
    m.g('drafting lamp')
    m.box((17.4, 9.0, 11.0), (18.2, 9.4, 11.8), 'paint:#2a2a2a', 'lamp base')
    m.box((17.65, 9.4, 11.25), (17.95, 14.4, 11.55), 'paint:#2a2a2a', 'lamp arm', rot={'axis': 'z', 'angle': -22.5, 'origin': [17.8, 9.4, 11.4]})
    m.box((15.6, 13.6, 10.6), (17.4, 14.4, 12.2), 'paint:#2a2a2a', 'lamp shade')
    m.box((15.8, 13.5, 10.8), (17.2, 13.6, 12.0), 'lamp:fff2c0', 'lamp bulb')
    m.g('works clock + extinguisher')
    m.cyl((22.0, 30.0, 14.4), 0.95, 0.6, 'z', 'gauge:clock', 'works clock', cap='gauge:clock')
    m.cyl((28.6, 0.0, 10.4), 0.7, 3.6, 'y', 'paint:#c82020', 'CO2 extinguisher')
    m.box((28.4, 3.6, 10.2), (28.8, 4.2, 10.6), 'paint:#1a1a1a', 'extinguisher head')
    m.box((27.9, 1.0, 9.6), (29.3, 2.0, 9.7), 'label:CO2', 'extinguisher label')
    m.g('bogie model')
    m.box((-13.2, 4.4, 9.9), (-7.4, 4.8, 10.4), 'paint:#1a1a1a', 'model base rail')
    for x in (-12.4, -8.4):
        m.cyl((x, 4.8, 10.15), 0.55, 0.3, 'z', 'metal:steel', 'model wheel')
    m.box((-12.8, 5.2, 9.95), (-8.0, 5.7, 10.35), 'paint:#2a4a6a', 'model bogie frame')
    m.terminal_12v(16.0, 1.0, 12.8, 'south')
    m.save(item_gui=0.32)


# ================================================================================================ item textures
def item_textures():
    d = os.path.join(ASSETS, 'textures/items')
    os.makedirs(d, exist_ok=True)

    def img():
        return Image.new('RGBA', (16, 16), (0, 0, 0, 0))

    def rect(im, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): im.putpixel((x, y), c)

    def save(im, name):
        im.save(os.path.join(d, name + '.png'))
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + name}}, open(os.path.join(ASSETS, 'models/item/' + name + '.json'), 'w'), indent=1)

    Y, K, G, R, W, B, S = (240, 196, 24, 255), (30, 30, 34, 255), (100, 106, 112, 255), (200, 30, 30, 255), (236, 236, 232, 255), (40, 90, 170, 255), (20, 60, 30, 255)
    # ultrasonic rail tester: the walking stick with its probe sled and the display box
    im = img(); rect(im, 2, 12, 9, 14, G); rect(im, 3, 13, 8, 13, K); rect(im, 7, 2, 8, 12, G); rect(im, 9, 4, 14, 9, Y); rect(im, 10, 5, 13, 7, S); im.putpixel((11, 6), (80, 240, 120, 255)); save(im, 'ndt_ultrasonic')
    # axle tester: probe + coiled lead + small unit
    im = img(); rect(im, 2, 3, 8, 11, B); rect(im, 3, 4, 7, 7, S); rect(im, 3, 9, 4, 10, R); rect(im, 6, 9, 7, 10, W)
    for i in range(6): im.putpixel((9 + i, 6 + (i % 2)), K)
    rect(im, 13, 7, 15, 13, G); save(im, 'ndt_axle')
    # thermal imager: pistol grip camera with a rainbow screen
    im = img(); rect(im, 2, 3, 13, 9, K); rect(im, 3, 4, 8, 8, (255, 80, 0, 255)); rect(im, 5, 5, 7, 7, (255, 230, 60, 255)); rect(im, 3, 8, 8, 8, (60, 60, 220, 255))
    rect(im, 11, 4, 14, 6, G); rect(im, 8, 10, 11, 15, (60, 60, 64, 255)); save(im, 'ndt_thermal')
    # insulation tester: yellow megger with a big dial and two leads
    im = img(); rect(im, 2, 2, 13, 12, Y); rect(im, 4, 3, 11, 8, W)
    for i in range(5): im.putpixel((5 + i, 7 - i // 2), K)
    rect(im, 4, 10, 5, 11, R); rect(im, 10, 10, 11, 11, K)
    for i in range(4): im.putpixel((4 - i // 2, 12 + i), R); im.putpixel((11 + i // 2, 12 + i), K)
    save(im, 'ndt_insulation')
    # structural monitor: tablet + crack gauge
    im = img(); rect(im, 1, 3, 10, 13, K); rect(im, 2, 4, 9, 11, (30, 50, 70, 255))
    for i in range(7): im.putpixel((2 + i, 9 - (i * 3) % 5), (120, 220, 255, 255))
    rect(im, 11, 6, 15, 9, (200, 200, 190, 255)); rect(im, 12, 7, 14, 8, R); save(im, 'ndt_structural')
    # the new spares
    for name, col, kind in (('rail_length', (140, 140, 150, 255), 'rail'), ('point_motor', (60, 90, 60, 255), 'motor'), ('signal_led', (30, 30, 34, 255), 'led'),
                            ('insulator', (150, 70, 50, 255), 'ins'), ('breaker_kit', (200, 170, 40, 255), 'kit'), ('tx_bushing', (130, 60, 40, 255), 'bush'),
                            ('motor_kit', (40, 80, 140, 255), 'kit'), ('structure_kit', (160, 160, 150, 255), 'bag')):
        im = img()
        lt = tuple(min(255, c + 50) for c in col[:3]) + (255,)
        dk = tuple(max(0, c - 50) for c in col[:3]) + (255,)
        if kind == 'rail':
            rect(im, 1, 6, 14, 7, lt); rect(im, 6, 8, 9, 9, col); rect(im, 3, 10, 12, 11, dk)
        elif kind == 'motor':
            rect(im, 3, 4, 11, 12, col); rect(im, 4, 5, 10, 6, lt); rect(im, 11, 7, 14, 9, G); rect(im, 5, 13, 9, 14, dk)
        elif kind == 'led':
            rect(im, 3, 1, 12, 14, col)
            for (cy, lc) in ((3, (230, 40, 40, 255)), (7, (240, 190, 40, 255)), (11, (60, 220, 90, 255))):
                rect(im, 6, cy, 9, cy + 2, lc)
        elif kind == 'ins':
            for k in range(5): rect(im, 4 - (k % 2), 2 + k * 2, 11 + (k % 2), 3 + k * 2, col if k % 2 else lt)
            rect(im, 7, 1, 8, 14, G)
        elif kind == 'bush':
            rect(im, 6, 1, 9, 2, (200, 140, 60, 255))
            for k in range(5): rect(im, 4, 3 + k * 2, 11, 3 + k * 2, col); rect(im, 5, 4 + k * 2, 10, 4 + k * 2, lt)
            rect(im, 3, 13, 12, 14, G)
        elif kind == 'bag':
            rect(im, 3, 4, 12, 14, col); rect(im, 5, 2, 10, 4, lt); rect(im, 4, 8, 11, 9, (200, 60, 30, 255))
        else:
            rect(im, 2, 4, 13, 13, col); rect(im, 2, 4, 13, 5, lt); rect(im, 6, 8, 9, 10, dk)
        save(im, 'spare_' + name)


P = lambda i: {'item': 'irextras:part', 'data': i}
PART = {'plate': 0, 'frame': 1, 'circuit': 2, 'motor': 3, 'hydraulic': 4, 'gearbox': 5, 'fan': 7, 'bearing': 9, 'shaft': 10, 'spring': 11, 'seal': 12,
        'winding': 13, 'insulator': 15, 'contactor': 18, 'screen': 20, 'movement': 21, 'battery': 22, 'antenna': 25}
RECIPES = {
    'commissioning_set': (['SMS', 'CIC', 'FBF'], {'S': P(PART['screen']), 'M': P(PART['movement']), 'C': P(PART['circuit']), 'I': 'irextras:multimeter', 'F': P(PART['frame']), 'B': 'minecraft:chest'}),
    'reman_bench': (['HCH', 'PFP', 'TBT'], {'H': P(PART['hydraulic']), 'C': P(PART['circuit']), 'P': P(PART['plate']), 'F': P(PART['frame']), 'T': 'minecraft:cauldron', 'B': 'minecraft:chest'}),
    'loco_workshop': (['SLS', 'GCG', 'WBW'], {'S': P(PART['screen']), 'L': 'minecraft:paper', 'G': P(PART['gearbox']), 'C': P(PART['circuit']), 'W': 'minecraft:iron_ingot', 'B': 'minecraft:chest'}),
}
SPARE_ORD = {'rail_length': 11, 'point_motor': 12, 'signal_led': 13, 'insulator': 14, 'breaker_kit': 15, 'tx_bushing': 16, 'motor_kit': 17, 'structure_kit': 18}
SPARE_RECIPES = [
    ('rail_length', 2, ['I  ', 'I  ', 'I  '], {'I': 'minecraft:iron_block'}),
    ('point_motor', 1, ['CMC', ' S '], {'C': P(PART['contactor']), 'M': P(PART['motor']), 'S': P(PART['shaft'])}),
    ('signal_led', 2, ['RGR', 'YCY'], {'R': 'minecraft:redstone', 'G': ('minecraft:glass', None), 'Y': 'minecraft:glowstone_dust', 'C': P(PART['circuit'])}),
    ('insulator', 2, [' I ', ' I ', ' I '], {'I': P(PART['insulator'])}),
    ('breaker_kit', 1, ['SCS', ' P '], {'S': P(PART['spring']), 'C': P(PART['contactor']), 'P': P(PART['plate'])}),
    ('tx_bushing', 1, [' W ', ' I ', ' E '], {'W': P(PART['winding']), 'I': P(PART['insulator']), 'E': P(PART['seal'])}),
    ('motor_kit', 1, ['BWB', ' E '], {'B': P(PART['bearing']), 'W': P(PART['winding']), 'E': P(PART['seal'])}),
    ('structure_kit', 2, ['SIS', 'SBS'], {'S': ('minecraft:sand', 0), 'I': 'minecraft:iron_ingot', 'B': 'minecraft:slime_ball'}),
]
NDT_RECIPES = [  # (meta, pattern, key)
    (0, [' D ', ' S ', 'III'], {'D': P(PART['screen']), 'S': 'minecraft:stick', 'I': 'minecraft:iron_ingot'}),
    (1, ['CD ', 'RW '], {'C': P(PART['circuit']), 'D': P(PART['screen']), 'R': 'minecraft:redstone', 'W': {'item': 'irextras:instrument_wire', 'data': 16}}),
    (2, ['GDC', ' I '], {'G': ('minecraft:glass', None), 'D': P(PART['screen']), 'C': P(PART['circuit']), 'I': 'minecraft:iron_ingot'}),
    (3, ['MD ', 'CB '], {'M': P(PART['movement']), 'D': 'minecraft:gold_ingot', 'C': P(PART['circuit']), 'B': P(PART['battery'])}),
    (4, ['DA ', 'CS '], {'D': P(PART['screen']), 'A': P(PART['antenna']), 'C': P(PART['circuit']), 'S': 'minecraft:stick'}),
]


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


LANG = {
    'tile.irextras.commissioning_set.name': 'Commissioning Test Set', 'tile.irextras.reman_bench.name': 'Remanufacturing Bench',
    'tile.irextras.loco_workshop.name': 'Locomotive Design + Rebuild Workshop', 'item.irextras.ndt_tool.name': 'Inspection Instrument',
}

BLOCKS = ['commissioning_set', 'reman_bench', 'loco_workshop']

if __name__ == '__main__':
    commissioning_set(); reman_bench(); loco_workshop()
    for n in BLOCKS:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    item_textures()
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    for sid, cnt, pat, key in SPARE_RECIPES:
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()},
                   'result': {'item': 'irextras:spare', 'data': SPARE_ORD[sid], 'count': cnt}}, open(os.path.join(ASSETS, 'recipes/spare_%s.json' % sid), 'w'), indent=1)
    names = ['ndt_ultrasonic', 'ndt_axle', 'ndt_thermal', 'ndt_insulation', 'ndt_structural']
    for meta, pat, key in NDT_RECIPES:
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()},
                   'result': {'item': 'irextras:ndt_tool', 'data': meta}}, open(os.path.join(ASSETS, 'recipes/%s.json' % names[meta]), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# assets + maintenance: commissioning, remanufacturing, loco works, inspection (48-next list)\n' + '\n'.join(add) + '\n')
    print('shelf spots (model px, for the tiles\' SPOTS):')
    for k, v in SPOTS.items(): print(' ', k, v)
