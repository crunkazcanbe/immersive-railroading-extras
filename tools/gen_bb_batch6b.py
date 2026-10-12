#!/usr/bin/env python3
"""Passenger travel (batch 6b #6-10): Pride Card reader + Top-Up Machine, left-luggage lockers, baggage check-in desk,
baggage belt, Baggage Hall (reclaim carousel + make-up cart), car park barrier (boom down / up) and the park & ride pay
station with its SPACES FREE sign; plus the item textures (Pride Card, Pride Rail phone, claim tag, parking ticket,
suitcase). Models face north; north-surface controls sit at u = 16 - x, south at u = x, east at u = 16 - z (face =
16 - x), up at v = z (face = 16 - y). Live controls: travel6/Tile*.java (ids must match).
Writes models, controls, blockstates, item models + textures, recipes and lang lines."""
import json, os
from PIL import Image
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing
from gen_bb_economy import back_wall, led_board, desk, strip, monitor, hood, X

GREY = '505860'
RAINBOW = ('#e40303', '#ff8c00', '#ffed00', '#008026', '#24408e', '#732982')
YELLOW = '#f0c418'


def rainbow(m, x0, x1, y, z, h=0.3, t=0.06):
    """the Pride rainbow strip across a north face"""
    w = (x1 - x0) / 6
    for i, c in enumerate(RAINBOW):
        m.box((x1 - (i + 1) * w, y, z - t), (x1 - i * w, y + h, z), 'paint:' + c, 'rainbow stripe')


def card_pad(m, sf, cx, cy, z, cid='pad', r=1.5, u=None):
    """a yellow contactless reader pad on a north face (centre model x, y), with the contactless waves"""
    m.cyl((cx, cy, z - 0.25), r + 0.25, 0.25, 'z', 'paint:#2a2c30', 'pad bezel')
    m.cyl((cx, cy, z - 0.35), r, 0.1, 'z', 'paint:' + YELLOW, 'reader pad')
    for i, a in enumerate((0.45, 0.75, 1.05)):
        m.box((cx + a - 0.08, cy - 0.25 - i * 0.18, z - 0.4), (cx + a + 0.08, cy + 0.25 + i * 0.18, z - 0.35), 'paint:#1a1a1a', 'contactless wave')
    m.box((cx - 0.6, cy - 0.4, z - 0.4), (cx - 0.1, cy + 0.4, z - 0.35), 'paint:#24408e', 'card symbol')
    m.ctl(sf, 'zone', cid, X(cx) if u is None else u, cy, w=2 * r, h=2 * r, col='000000', label='')


def keypad(m, sf, cx, cy, z, step=1.0, size=0.75):
    """a 12-key stainless PIN pad: 1-9, CLR, 0, ENT (ids n0..n9, clr, ent)"""
    m.box((cx - 1.9, cy - 2.6, z), (cx + 1.9, cy + 2.0, z + 0.2), 'metal:steel', 'keypad plate')
    keys = [('n1', '1'), ('n2', '2'), ('n3', '3'), ('n4', '4'), ('n5', '5'), ('n6', '6'), ('n7', '7'), ('n8', '8'), ('n9', '9'),
            ('clr', 'CLR'), ('n0', '0'), ('ent', 'ENT')]
    cols = {'clr': 'e0a020', 'ent': '20a040'}
    for i, (cid, lab) in enumerate(keys):
        r, c = divmod(i, 3)
        u = X(cx) + (c - 1) * step
        v = cy + 1.4 - r * step * 1.15
        m.ctl(sf, 'push', cid, u, v, w=size, h=size, col=cols.get(cid, 'd8dce0'), label=lab)


# ============================================================================================================ card reader
def card_reader():
    m = Model('card_reader')
    m.g('base')
    m.box((4.0, 0, 4.0), (12.0, 0.5, 12.0), 'galv', 'base plate')
    for (x, z) in ((4.6, 4.6), (11.4, 4.6), (4.6, 11.4), (11.4, 11.4)):
        m.bolt(x, 0.5, z, 'up', 0.5)
    m.box((5.6, 0.5, 5.6), (10.4, 1.2, 10.4), 'paint:#2a2c30', 'plinth')
    m.g('post')
    m.box((6.6, 1.2, 6.6), (9.4, 10.6, 9.4), 'metal:steel', 'stainless post')
    m.box((6.5, 4.0, 6.5), (9.5, 4.4, 9.5), 'paint:#1a1a1a', 'post band')
    m.box((6.5, 2.0, 9.4), (9.5, 5.6, 9.6), 'paint:#2a2c30', 'service hatch')
    m.box((8.6, 3.4, 9.6), (9.0, 4.2, 9.75), 'metal:brass', 'hatch lock')
    m.box((6.4, 1.2, 6.4), (9.6, 1.8, 9.6), 'paint:#1a1a1a', 'post foot ring')
    m.g('head')
    m.box((4.4, 10.6, 5.6), (11.6, 17.6, 10.6), 'paint:#20242c', 'validator head')
    m.box((4.2, 17.6, 5.4), (11.8, 18.2, 10.8), 'paint:#24408e', 'head cap')
    m.box((4.3, 10.4, 5.5), (11.7, 10.7, 10.7), 'paint:#24408e', 'head skirt')
    m.box((4.4, 10.6, 5.5), (11.6, 11.0, 5.6), 'paint:#3a3f48', 'lower lip')
    for z in (6.2, 7.0, 7.8, 8.6, 9.4):
        m.box((11.6, 12.0, z), (11.7, 16.0, z + 0.4), 'paint:#101216', 'side vent')
        m.box((4.3, 12.0, z), (4.4, 16.0, z + 0.4), 'paint:#101216', 'side vent')
    f = m.surface('north', face=5.45, title='PRIDE CARD READER')
    rainbow(m, 4.6, 11.4, 17.1, 5.6, h=0.35)
    m.box((4.8, 16.2, 5.5), (11.2, 17.0, 5.6), 'plate', 'name strip', north='label:PRIDE CARD')
    card_pad(m, f, 8.0, 14.4, 5.6, r=1.55)
    m.box((5.0, 11.2, 5.48), (11.0, 13.0, 5.6), 'paint:#05080a', 'display window')
    m.ctl(f, 'digits', 'l1', X(8.0), 12.55, w=5.6, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'l2', X(8.0), 11.7, w=5.6, h=0.6, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'lamp', 'go', X(5.6), 16.6, w=0.6, h=0.6, col=GREEN, label='GO')
    m.ctl(f, 'lamp', 'stop', X(10.4), 16.6, w=0.6, h=0.6, col=RED, label='STOP')
    m.ctl(f, 'lamp', 'inl', X(5.4), 14.4, w=0.4, h=0.4, col=AMBER, label='IN')
    m.ctl(f, 'label', '', X(5.4), 13.9, w=0, h=0, col='e8e8e8', label='IN')
    m.ctl(f, 'lamp', 'outl', X(10.6), 14.4, w=0.4, h=0.4, col=BLUE, label='OUT')
    m.ctl(f, 'label', '', X(10.6), 13.9, w=0, h=0, col='e8e8e8', label='OUT')
    m.g('head detail')
    for (x, y) in ((4.8, 11.0), (11.2, 11.0), (4.8, 17.2), (11.2, 17.2)):
        m.bolt(x, y, 5.6, 'north', 0.3)
    for k in range(8):                                                   # the reader's LED ring around the pad
        import math
        a = math.pi * 2 * k / 8
        m.box((8.0 + math.cos(a) * 1.95 - 0.15, 14.4 + math.sin(a) * 1.95 - 0.15, 5.3), (8.0 + math.cos(a) * 1.95 + 0.15, 14.4 + math.sin(a) * 1.95 + 0.15, 5.35), 'paint:#5bcefa', 'pad LED')
    m.box((4.6, 13.2, 5.5), (5.0, 15.6, 5.6), 'paint:#3a3f48', 'side trim')
    m.box((11.0, 13.2, 5.5), (11.4, 15.6, 5.6), 'paint:#3a3f48', 'side trim')
    m.box((5.2, 10.8, 5.45), (10.8, 11.15, 5.5), 'label:TOUCH IN/OUT', 'touch legend')
    m.box((6.0, 9.0, 6.5), (10.0, 10.6, 9.5), 'paint:#2a2c30', 'head mounting collar')
    m.box((6.2, 8.6, 6.4), (9.8, 9.0, 9.6), 'metal:steel', 'collar clamp')
    for x in (6.4, 9.6):
        m.bolt(x, 8.8, 6.4, 'north', 0.3)
    m.g('base detail')
    m.box((9.4, 0.5, 6.8), (11.0, 2.2, 9.2), 'ral7035', 'conduit box')
    m.cyl((10.2, 0.0, 8.0), 0.35, 0.5, 'y', 'cable:1d1d21', 'floor conduit')
    m.box((4.2, 0.5, 4.2), (4.6, 0.7, 11.8), 'haz', 'hazard edge')
    m.box((11.4, 0.5, 4.2), (11.8, 0.7, 11.8), 'haz', 'hazard edge')
    m.box((6.4, 6.8, 6.4), (9.6, 7.2, 9.6), 'paint:#24408e', 'post colour band')
    m.box((6.55, 7.4, 6.55), (9.45, 8.4, 6.6), 'label:ZONE', 'zone plate')
    m.g('top display')
    t = m.surface('north', face=5.35, title='STATION')
    m.box((5.0, 18.2, 5.5), (11.0, 19.6, 6.4), 'paint:#20242c', 'station display housing')
    m.box((5.2, 18.4, 5.4), (10.8, 19.4, 5.5), 'paint:#05080a', 'station display')
    m.ctl(t, 'digits', 'stn', X(8.0), 18.9, w=5.4, h=0.7, col='8ad4ff', label='', fmt='%s')
    m.g('service side')
    s = m.surface('south', face=16 - 10.62, title='SERVICE')
    m.box((5.2, 11.4, 10.6), (10.8, 16.8, 10.7), 'paint:#2a2c30', 'service panel')
    for (x, y) in ((5.5, 11.7), (10.5, 11.7), (5.5, 16.5), (10.5, 16.5)):
        m.bolt(x, y, 10.7, 'south', 0.35)
    m.ctl(s, 'key', 'k_mode', 6.6, 15.0, w=0.9, h=0.9, col='c0a040', label='MODE', pos=['AUTO', 'ENTRY', 'EXIT'])
    m.ctl(s, 'label', '', 6.6, 14.1, w=0, h=0, col='e8e8e8', label='MODE')
    m.ctl(s, 'digits', 'modet', 6.6, 13.2, w=2.2, h=0.5, col='ffd040', label='', fmt='%s')
    m.ctl(s, 'push', 'test', 9.6, 15.0, w=0.7, h=0.7, col='f0f0f0', label='TEST')
    m.ctl(s, 'label', '', 9.6, 14.2, w=0, h=0, col='e8e8e8', label='TEST')
    m.ctl(s, 'lamp', 'pwr', 9.6, 13.0, w=0.45, h=0.45, col=GREEN, label='12V')
    m.ctl(s, 'digits', 'ntaps', 8.0, 12.0, w=2.6, h=0.5, col='7cf29a', label='', fmt='%d TAPS')
    m.box((6.6, 16.9, 10.6), (9.4, 17.4, 10.8), 'plate', 'asset tag', south='plate:CR-6')
    m.g('cable')
    m.cyl((8.0, 1.2, 8.0), 0.25, 9.4, 'y', 'cable:1d1d21', 'data cable (inside post)')
    m.terminal_12v(9.5, 2.0, 7.4, 'east')
    m.save(item_gui=0.6)
    return m


# ============================================================================================================ top-up machine
def card_topup():
    m = Model('card_topup')
    m.g('cabinet')
    m.box((1.0, 0, 4.2), (15.0, 27.0, 15.0), 'paint:#24408e', 'cabinet')
    m.box((0.8, 27.0, 4.0), (15.2, 27.6, 15.2), 'paint:#182c62', 'roof')
    m.box((1.0, 0, 4.0), (15.0, 0.8, 15.0), 'paint:#101216', 'plinth')
    m.box((0.9, 0.8, 4.1), (1.2, 26.8, 15.0), 'paint:#182c62', 'corner trim')
    m.box((14.8, 0.8, 4.1), (15.1, 26.8, 15.0), 'paint:#182c62', 'corner trim')
    for z in (6.0, 8.0, 10.0, 12.0):
        m.box((15.0, 22.0, z), (15.1, 25.0, z + 1.0), 'paint:#101216', 'side vent')
    f = m.surface('north', face=4.0, title='TOP UP')
    m.g('lightbox')
    m.box((1.4, 24.8, 3.9), (14.6, 26.8, 4.2), 'paint:#f4f4f0', 'lightbox')
    m.box((1.6, 25.0, 3.85), (14.4, 26.6, 3.9), 'label:PRIDE CARD TOP UP', 'lightbox lettering')
    rainbow(m, 1.4, 14.6, 24.4, 4.2, h=0.4)
    m.g('screen')
    m.box((2.0, 17.0, 3.6), (14.0, 24.0, 4.2), 'paint:#1c1e20', 'screen bezel')
    m.box((2.4, 17.4, 3.55), (13.6, 23.6, 3.6), 'paint:#03140a', 'screen')
    sc = m.surface('north', face=3.5, title='SCREEN')
    m.ctl(sc, 'digits', 'title', X(8.0), 23.0, w=10.6, h=0.7, col='5bcefa', label='', fmt='%s')
    for i in range(6):
        m.ctl(sc, 'digits', 's%d' % (i + 1), X(8.0), 22.0 - i * 0.78, w=10.6, h=0.62, col='ffd040' if i == 0 else 'f0f0e0', label='', fmt='%s')
    m.ctl(sc, 'digits', 'amt', X(11.6), 17.9, w=3.4, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(sc, 'digits', 'msg', X(6.4), 17.9, w=7.6, h=0.5, col='8ad4ff', label='', fmt='%s')
    m.g('page keys')
    for (cid, x, y, lab) in (('pgl', 1.5, 21.2, '<'), ('pgr', 14.5, 21.2, '>'), ('pgup', 1.5, 19.6, 'UP'), ('pgdn', 14.5, 19.6, 'DN')):
        m.ctl(f, 'push', cid, X(x), y, w=0.7, h=0.7, col='c8ccd0', label=lab)
    for i, lab in enumerate(('BAL', 'HIST', 'TOP', 'SRV')):
        u = X(4.5 + i * 2.4)
        m.ctl(f, 'lamp', 'p%d' % i, u, 16.6, w=0.35, h=0.35, col='5bcefa', label=lab)
        m.ctl(f, 'label', '', u, 16.15, w=0, h=0, col='e8e8e8', label=lab)
    m.g('keys')
    m.box((1.8, 9.0, 3.8), (14.2, 15.8, 4.2), 'paint:#2b2f33', 'key panel')
    pushes(m, f, [('a5', '30a0e0', '+5'), ('a10', '30a0e0', '+10'), ('a20', '30a0e0', '+20'), ('a50', '30a0e0', '+50')], X(8.6), 15.0, step=1.9, size=0.9, lab_dv=0.7)
    pushes(m, f, [('clr', 'e0a020', 'CLEAR'), ('cancel', 'd02020', 'CANCEL'), ('pay', '1fa34a', 'PAY')], X(8.6), 12.9, step=1.9, size=0.95, lab_dv=0.75)
    pushes(m, f, [('new', '2050d0', 'NEW CARD'), ('block', 'e06020', 'LOST'), ('refund', '909090', 'HAND BACK'), ('rcpt', 'f0f0f0', 'RECEIPT')], X(8.6), 10.6, step=1.9, size=0.8, lab_dv=0.7)
    m.g('card pad')
    m.box((9.6, 11.2, 3.7), (14.0, 15.6, 4.2), 'paint:#20242c', 'pad housing')
    card_pad(m, f, 11.8, 13.6, 3.7, r=1.5)
    m.ctl(f, 'lamp', 'cardl', X(11.8), 11.7, w=0.4, h=0.4, col=GREEN, label='CARD')
    m.ctl(f, 'label', '', X(11.8), 11.3, w=0, h=0, col='e8e8e8', label='CARD READ')
    m.g('dispenser + printer')
    m.box((2.2, 7.4, 3.7), (7.8, 8.6, 4.2), 'paint:#101216', 'card dispenser')
    m.box((3.0, 7.8, 3.65), (7.0, 8.1, 3.7), 'paint:#000000', 'card slot')
    m.box((8.6, 7.4, 3.7), (13.8, 8.6, 4.2), 'paint:#101216', 'receipt printer')
    m.box((9.2, 7.8, 3.65), (13.2, 8.0, 3.7), 'paint:#f4f2ea', 'receipt paper')
    m.box((2.2, 6.7, 3.9), (7.8, 7.3, 4.0), 'label:CARD', 'dispenser label')
    m.box((8.6, 6.7, 3.9), (13.8, 7.3, 4.0), 'label:RECEIPT', 'printer label')
    m.g('status')
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('payl', AMBER, 'PAY'), ('stockl', AMBER, 'LOW', True), ('lostl', RED, 'BLOCKED', True)], X(13.4), 5.6, step=1.6, size=0.45, lab_dv=0.5)
    m.ctl(f, 'gauge', 'stockg', X(3.2), 5.4, w=1.6, h=1.6, col='f2f0e6', label='CARDS', max=64)
    m.ctl(f, 'push', 'test', X(5.6), 5.6, w=0.45, h=0.45, col='f0f0f0', label='TEST')
    m.g('card stock drawer')
    m.box((1.8, 1.0, 3.9), (14.2, 4.4, 4.2), 'paint:#182c62', 'stock drawer')
    m.box((6.6, 2.4, 3.6), (9.4, 2.9, 3.9), 'metal:steel', 'drawer pull')
    m.box((2.4, 3.4, 3.85), (6.0, 4.1, 3.9), 'label:CARD STOCK', 'drawer label')
    m.ctl(f, 'zone', 'stock', X(7.0), 2.7, w=9.0, h=3.2, col='000000', label='')
    m.ctl(f, 'key', 'k_svc', X(13.2), 2.7, w=0.8, h=0.8, col='c0a040', label='SERVICE', pos=['LOCKED', 'OPEN'])
    m.g('cabinet detail')
    for (x, y) in ((1.4, 1.2), (14.6, 1.2), (1.4, 26.4), (14.6, 26.4), (1.4, 16.6), (14.6, 16.6)):
        m.bolt(x, y, 4.2, 'north', 0.35)
    m.box((1.6, 23.9, 3.2), (14.4, 24.3, 4.2), 'paint:#182c62', 'screen sun hood')
    m.box((1.6, 16.6, 3.6), (14.4, 16.9, 4.2), 'paint:#182c62', 'screen sill')
    m.box((1.8, 15.8, 3.95), (14.2, 16.1, 4.2), 'metal:steel', 'key panel trim')
    m.box((1.8, 8.8, 3.95), (14.2, 9.0, 4.2), 'metal:steel', 'key panel trim')
    m.box((15.0, 14.0, 5.0), (15.1, 20.0, 14.0), 'plate', 'how-to plate', east='label:1 TOUCH CARD|2 PICK AMOUNT|3 PAY')
    m.box((0.9, 14.0, 5.0), (1.0, 20.0, 14.0), 'plate', 'tariff plate', west='label:CAPPED DAILY|ANY ZONE')
    for y in (1.6, 3.8):
        m.box((1.6, y, 3.8), (1.8, y + 0.6, 4.2), 'metal:steel', 'drawer hinge')
    m.cyl((13.4, 1.4, 4.0), 0.25, 0.3, 'z', 'metal:brass', 'drawer lock barrel')
    for (x, z) in ((1.4, 4.6), (14.6, 4.6), (1.4, 14.6), (14.6, 14.6)):
        m.bolt(x, 0.8, z, 'up', 0.45)
    m.louvres(3.0, 13.0, 0.9, 1.0, 3.95, n=2)
    m.terminal_12v(15.0, 1.4, 9.0, 'east')
    mains_box(m, 15.0, 4.6, 12.6, 'east')
    m.save(item_gui=0.45)
    return m


# ============================================================================================================ left luggage
LOCKER_COLS = ((23.6, 30.2), (16.8, 23.4), (-7.6, -0.8), (-14.4, -7.8))   # viewer left -> right (model x high -> low)
LOCKER_ROWS = ((18.6, 27.0), (9.8, 18.2), (0.8, 9.4))                    # top, middle (small), bottom (large)


def left_luggage():
    m = Model('left_luggage')
    m.g('carcass')
    m.box((-14.8, 0, 6.4), (30.6, 27.6, 15.6), 'paint:#3c4a5c', 'locker carcass')
    m.box((-15.0, 27.6, 6.2), (30.8, 28.2, 15.8), 'paint:#2a3442', 'top capping')
    m.box((-14.8, 0, 6.2), (30.6, 0.6, 6.4), 'paint:#101216', 'kick plate')
    lk = m.surface('north', face=6.0, title='LOCKERS')
    for c, (x0, x1) in enumerate(LOCKER_COLS):
        for r, (y0, y1) in enumerate(LOCKER_ROWS):
            n = r * 4 + c + 1                                          # 1-4 top row, 5-8 middle, 9-12 large bottom
            m.g('locker %d' % n)
            m.box((x0 + 0.15, y0, 6.2), (x1 - 0.15, y1, 6.4), 'paint:#5a6e88', 'locker door')
            m.box((x0 + 0.5, y1 - 1.6, 6.15), (x0 + 2.0, y1 - 0.5, 6.2), 'label:%d' % n, 'locker number')
            for k in range(3):
                m.box((x0 + 2.6, y1 - 1.5 + k * 0.4, 6.15), (x1 - 0.8, y1 - 1.3 + k * 0.4, 6.2), 'paint:#2a3442', 'vent slot')
            m.box((x1 - 0.5, y0 + 1.0, 6.1), (x1 - 0.2, y0 + 2.0, 6.4), 'metal:steel', 'hinge')
            m.box((x1 - 0.5, y1 - 2.4, 6.1), (x1 - 0.2, y1 - 1.4, 6.4), 'metal:steel', 'hinge')
            lx = x0 + 1.4
            ly = (y0 + y1) / 2
            m.box((lx - 0.9, ly - 1.4, 5.95), (lx + 0.9, ly + 1.4, 6.2), 'paint:#20242c', 'electronic lock')
            m.box((lx - 0.4, ly - 1.1, 5.85), (lx + 0.4, ly - 0.4, 5.95), 'metal:steel', 'door pull')
            m.ctl(lk, 'lamp', 'fr%d' % n, X(lx - 0.45), ly + 0.8, w=0.35, h=0.35, col=GREEN, label='FREE')
            m.ctl(lk, 'lamp', 'oc%d' % n, X(lx + 0.45), ly + 0.8, w=0.35, h=0.35, col=RED, label='LOCKED')
            m.ctl(lk, 'lamp', 'op%d' % n, X(lx), ly + 0.1, w=0.35, h=0.35, col=AMBER, label='OPEN', blink=True)
            m.ctl(lk, 'zone', 'l%d' % n, X((x0 + x1) / 2 + 0.6), (y0 + y1) / 2, w=x1 - x0 - 2.2, h=y1 - y0 - 0.4, col='000000', label='')
            if r == 2:
                m.box((x0 + 2.6, y0 + 0.8, 6.15), (x1 - 0.8, y0 + 1.6, 6.2), 'label:LARGE', 'size plate')
    m.g('control unit')
    m.box((0.2, 0, 5.2), (15.8, 27.6, 6.4), 'paint:#24408e', 'control unit fascia')
    m.box((0.6, 25.0, 5.1), (15.4, 27.2, 5.2), 'label:LEFT LUGGAGE', 'header')
    rainbow(m, 0.6, 15.4, 24.6, 5.2, h=0.35)
    cu = m.surface('north', face=5.1, title='LOCKER CONTROL')
    m.box((1.4, 17.2, 4.6), (14.6, 24.2, 5.2), 'paint:#1c1e20', 'screen bezel')
    m.box((1.8, 17.6, 4.55), (14.2, 23.8, 4.6), 'paint:#03140a', 'screen')
    scr = m.surface('north', face=4.5, title='SCREEN')
    for i in range(6):
        m.ctl(scr, 'digits', 's%d' % (i + 1), X(8.0), 23.2 - i * 0.92, w=11.8, h=0.72, col='ffd040' if i == 0 else 'f0f0e0', label='', fmt='%s')
    m.box((1.4, 15.0, 4.8), (7.4, 16.8, 5.2), 'paint:#05080a', 'locker no display')
    m.ctl(cu, 'digits', 'selv', X(2.8), 15.9, w=2.0, h=1.2, col='ff3030', label='', fmt='%s')
    m.ctl(cu, 'digits', 'pinv', X(5.8), 15.9, w=2.6, h=1.0, col='7cf29a', label='', fmt='%s')
    m.box((8.6, 15.0, 4.8), (14.6, 16.8, 5.2), 'paint:#05080a', 'msg display')
    m.ctl(cu, 'digits', 'msg', X(11.6), 15.9, w=5.8, h=0.5, col='8ad4ff', label='', fmt='%s')
    m.g('keypad')
    keypad(m, cu, 5.0, 11.4, 4.9)
    m.ctl(cu, 'push', 'prev', X(10.2), 13.6, w=0.8, h=0.8, col='c8ccd0', label='LOCKER <')
    m.ctl(cu, 'push', 'next', X(13.4), 13.6, w=0.8, h=0.8, col='c8ccd0', label='LOCKER >')
    m.ctl(cu, 'selector', 's_hours', X(11.8), 11.4, w=1.2, h=1.2, col='202020', label='HOURS', pos=['1H', '4H', '12H', '24H'])
    m.ctl(cu, 'label', '', X(11.8), 10.4, w=0, h=0, col='e8e8e8', label='HOURS')
    pushes(m, cu, [('rent', '1fa34a', 'RENT'), ('print', 'f0f0f0', 'PRINT')], X(13.4), 9.2, step=3.2, size=0.95, lab_dv=0.75)
    m.g('status + staff')
    lamps(m, cu, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('stfl', AMBER, 'STAFF'), ('alml', RED, 'ALARM', True)], X(14.0), 7.2, step=1.6, size=0.45, lab_dv=0.5)
    m.ctl(cu, 'digits', 'nfree', X(4.4), 7.2, w=3.4, h=0.7, col='7cf29a', label='', fmt='%d FREE')
    m.box((1.6, 2.0, 4.9), (8.6, 5.6, 5.2), 'paint:#2b2f33', 'staff panel')
    m.box((1.8, 5.0, 4.85), (8.4, 5.5, 4.9), 'label:STAFF ONLY', 'staff label')
    m.ctl(cu, 'key', 'k_staff', X(3.2), 3.6, w=0.9, h=0.9, col='c0a040', label='STAFF', pos=['OFF', 'ON'])
    m.ctl(cu, 'push', 'sclr', X(5.4), 3.6, w=0.7, h=0.7, col='e06020', label='CLEAR')
    m.ctl(cu, 'push', 'test', X(7.4), 3.6, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    m.box((9.6, 2.0, 4.8), (14.4, 3.2, 5.2), 'paint:#101216', 'receipt printer')
    m.box((10.2, 2.5, 4.75), (13.8, 2.7, 4.8), 'paint:#f4f2ea', 'receipt paper')
    m.box((9.6, 3.4, 4.9), (14.4, 4.0, 5.0), 'label:RECEIPT', 'printer label')
    m.box((9.6, 4.6, 4.9), (14.4, 6.2, 5.0), 'label:SMALL 25C/H|LARGE 50C/H', 'tariff plate')
    m.terminal_12v(30.4, 1.4, 9.0, 'east')
    mains_box(m, 30.4, 4.6, 13.0, 'east')
    m.save(item_gui=0.26)
    return m


# ============================================================================================================ check-in desk
def bag_checkin():
    m = Model('bag_checkin')
    back_wall(m, panel='#24408e', plaster='#eef0f2', top=26.0)
    m.g('fascia')
    m.box((-14, 23.4, 14.6), (30, 25.6, 15.4), 'paint:#1c2840', 'fascia board')
    m.box((-6, 23.8, 14.5), (22, 25.2, 14.6), 'plate', 'fascia lettering', north='label:BAGGAGE CHECK-IN')
    rainbow(m, -14, 30, 23.0, 15.4, h=0.4)
    # ---- bag drop scale (left block)
    m.g('bag drop')
    m.box((-15.0, 0, 2.0), (-1.0, 3.6, 13.0), 'paint:#2b2f33', 'scale body')
    m.box((-14.6, 3.6, 2.4), (-1.4, 4.0, 12.6), 'paint:#141416', 'belt top')
    for z in range(3, 13, 2):
        m.box((-14.4, 4.0, z), (-1.6, 4.05, z + 0.3), 'paint:#2a2a2c', 'belt cleat')
    m.box((-15.2, 3.6, 1.8), (-14.6, 6.0, 13.0), 'metal:steel', 'side guard')
    m.box((-1.4, 3.6, 1.8), (-0.8, 6.0, 13.0), 'metal:steel', 'side guard')
    for x in (-14.0, -2.0):
        m.cyl((x, 0, 1.6), 0.3, 3.6, 'y', 'metal:steel', 'bumper post')
    bd = m.surface('up', face=16 - 4.05, title='BAG DROP')
    m.ctl(bd, 'zone', 'drop', X(-8.0), 7.5, w=12.8, h=10.0, col='000000', label='')
    m.box((-15.0, 0.6, 1.9), (-1.0, 3.2, 2.0), 'plate', 'scale plate', north='label:PLACE BAG HERE')
    m.g('scale display')
    m.cyl((-2.0, 4.0, 12.0), 0.35, 6.0, 'y', 'metal:steel', 'display pole')
    m.box((-4.6, 10.0, 11.4), (0.6, 13.0, 12.6), 'paint:#20242c', 'scale display')
    sd = m.surface('north', face=11.35, title='SCALE')
    m.box((-4.3, 11.6, 11.38), (-1.2, 12.7, 11.4), 'paint:#05080a', 'kg window')
    m.ctl(sd, 'digits', 'kgv', X(-2.75), 12.15, w=2.9, h=0.9, col='ff3030', label='', fmt='%s')
    m.box((-1.0, 11.8, 11.38), (0.3, 12.5, 11.4), 'label:KG', 'kg label')
    m.ctl(sd, 'gauge', 'scale', X(-1.4), 10.8, w=1.0, h=1.0, col='f2f0e6', label='KG', max=32)
    m.ctl(sd, 'lamp', 'heavy', X(-3.6), 10.6, w=0.4, h=0.4, col=AMBER, label='HEAVY', blink=True)
    # ---- the desk (middle block)
    desk(m, {}, top=9.0, body='#1c2840', front='#2c3c5c', pull='metal:steel')
    sf = strip(m, 3.4, 8.6, top=9.0)
    pushes(m, sf, [('dprev', GREY, 'DEST <'), ('dnext', GREY, 'DEST >'), ('weigh', '30a0e0', 'WEIGH'), ('tag', '1fa34a', 'TAG')], 1.6, 4.6, step=2.6, size=0.95)
    pushes(m, sf, [('send', '2050d0', 'TO BELT'), ('claim', 'f0d020', 'CLAIM'), ('test', 'f0f0f0', 'TEST')], 1.6, 6.8, step=2.6, size=0.85)
    hd = hood(m, z=9.8)
    lamps(m, hd, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('beltl', GREEN, 'BELT'), ('holdl', AMBER, 'HOLD')], 1.2, 11.1, step=1.6, size=0.5)
    m.ctl(hd, 'digits', 'nhold', 9.8, 11.1, w=2.6, h=0.7, col='ffb020', label='', fmt='%d IN HOLD')
    monitor(m, 2.0, 14.0, 12.2, 18.6, 10.6, ['s1', 's2', 's3', 's4', 's5', 's6'], cols=['5bcefa', 'ffd040', 'f0f0e0', '7cf29a', 'ffb020', '8a94a0'], stand_y=11.8)
    mon2 = m.surface('north', face=10.5, title='STATUS')
    m.box((2.0, 11.8, 10.55), (14.0, 12.2, 11.2), 'paint:#1c1e20', 'status bar')
    m.ctl(mon2, 'digits', 'msg', X(8), 12.0, w=11.0, h=0.35, col='8ad4ff', label='', fmt='%s')
    m.g('tag printer')
    m.box((12.4, 9.0, 11.6), (15.4, 10.8, 13.6), 'paint:#e8e8e4', 'bag tag printer')
    m.box((12.8, 10.0, 11.5), (15.0, 10.3, 11.6), 'paint:#101010', 'tag slot')
    m.box((13.0, 10.3, 11.3), (14.8, 10.4, 11.6), 'paint:#f4f2ea', 'tag coming out')
    m.box((0.8, 9.0, 11.6), (2.8, 9.6, 13.4), 'paint:#2a2c2e', 'passport reader')
    m.box((1.0, 9.6, 12.0), (2.6, 9.7, 13.0), 'paint:#60c8f0', 'reader glass')
    # ---- bag hold + the belt hatch (right block)
    m.g('bag hold')
    m.box((17.0, 0, 6.0), (30.0, 14.0, 15.4), 'paint:#3a3e44', 'bag hold cage')
    for x in range(18, 30, 2):
        m.box((x, 0.4, 5.8), (x + 0.3, 13.6, 6.0), 'metal:steel', 'cage bar')
    m.box((17.0, 13.6, 5.8), (30.0, 14.2, 6.0), 'metal:steel', 'cage rail')
    m.box((17.0, 0.4, 5.8), (30.0, 1.0, 6.0), 'metal:steel', 'cage rail')
    hl = m.surface('north', face=5.7, title='BAG HOLD')
    m.ctl(hl, 'zone', 'hold', X(23.5), 7.0, w=12.6, h=13.0, col='000000', label='')
    for i, (x, c) in enumerate(((18.4, '#732982'), (21.6, '#5a3a24'), (25.0, '#24408e'), (27.6, '#c03030'))):
        m.box((x, 1.0, 8.0 + (i % 2)), (x + 2.6, 5.4 - (i % 2) * 0.8, 12.6), 'paint:' + c, 'suitcase')
        m.box((x + 0.9, 5.4 - (i % 2) * 0.8, 9.6), (x + 1.7, 6.0 - (i % 2) * 0.8, 10.0), 'paint:#1a1a1a', 'suitcase handle')
    m.box((17.4, 14.2, 13.0), (29.6, 22.4, 15.4), 'paint:#2a2c30', 'belt hatch surround')
    for x in range(18, 29):
        m.box((x, 15.0, 12.9), (x + 0.9, 21.6, 13.0), 'paint:#101012', 'rubber curtain strip')
    m.box((17.4, 21.8, 12.8), (29.6, 22.4, 13.0), 'haz', 'hatch hazard band')
    m.terminal_12v(30.0, 1.0, 3.0, 'east')
    mains_box(m, 30.0, 16.0, 12.6, 'east')
    m.save(item_gui=0.26)
    return m


# ============================================================================================================ baggage belt
def bag_belt():
    m = Model('bag_belt')
    m.g('frame')
    for x0, x1 in ((0.0, 1.0), (15.0, 16.0)):
        m.box((x0, 2.4, 0), (x1, 6.0, 16), 'paint:#3a3e44', 'side frame')
        m.box((x0, 6.0, 0), (x1, 7.0, 16), 'metal:steel', 'guide rail')
    for (x, z) in ((0.2, 0.6), (15.0, 0.6), (0.2, 14.6), (15.0, 14.6)):
        m.box((x, 0, z), (x + 0.8, 2.4, z + 0.8), 'paint:#2a2c30', 'leg')
        m.box((x - 0.2, 0, z - 0.2), (x + 1.0, 0.3, z + 1.0), 'galv', 'foot')
    m.box((0.6, 0.8, 0.8), (15.4, 1.2, 1.2), 'paint:#2a2c30', 'cross brace')
    m.box((0.6, 0.8, 14.8), (15.4, 1.2, 15.2), 'paint:#2a2c30', 'cross brace')
    m.g('rollers + belt')
    for z in (2.0, 5.0, 8.0, 11.0, 14.0):
        m.cyl((1.0, 4.2, z), 0.6, 14.0, 'x', 'metal:steel', 'idler roller')
    m.cyl((1.0, 4.6, 0.6), 0.8, 14.0, 'x', 'metal:steel', 'tail drum')
    m.cyl((1.0, 4.6, 15.4), 0.8, 14.0, 'x', 'metal:steel', 'drive drum')
    m.box((1.0, 5.2, 0), (15.0, 5.6, 16), 'paint:#141416', 'rubber belt')
    for z in range(1, 16, 3):                                            # chevrons: the way the bags go (+z)
        m.box((4.0, 5.6, z), (7.6, 5.65, z + 0.5), 'paint:#f0c418', 'chevron', rot={'axis': 'y', 'angle': -22.5, 'origin': [7.8, 5.6, z]})
        m.box((8.4, 5.6, z), (12.0, 5.65, z + 0.5), 'paint:#f0c418', 'chevron', rot={'axis': 'y', 'angle': 22.5, 'origin': [8.2, 5.6, z]})
    m.g('frame detail')
    for x, face in ((0.0, 'west'), (16.0, 'east')):
        for z in (1.5, 4.5, 7.5, 10.5, 13.5):
            m.bolt(x, 4.2, z, face, 0.4)
    m.box((1.0, 3.0, 0.6), (15.0, 3.2, 15.4), 'paint:#141416', 'return belt')
    m.box((1.0, 0.4, 1.0), (15.0, 0.6, 15.0), 'galv', 'drip tray')
    for z in (0.0, 15.6):
        m.box((0.6, 3.6, z), (15.4, 5.0, z + 0.4), 'mesh', 'drum guard')
    m.box((3.0, 5.6, 7.6), (13.0, 5.65, 8.0), 'paint:#3a3a3c', 'belt splice')
    m.box((-0.6, 4.4, 0.4), (0.0, 4.8, 0.8), 'metal:steel', 'take-up screw')
    m.g('drive')
    m.cyl((16.0, 3.4, 13.4), 1.0, 2.2, 'x', 'paint:#24408e', 'drive motor')
    m.box((16.0, 2.2, 12.2), (18.2, 2.5, 14.6), 'paint:#2a2c30', 'motor foot')
    m.cyl((18.2, 3.4, 13.4), 0.6, 0.6, 'x', 'paint:#1a1a1a', 'motor fan cover')
    m.box((16.2, 4.4, 12.8), (17.6, 4.8, 14.0), 'plate', 'motor plate', up='plate:0.75KW')
    m.g('pushbutton station')
    m.box((16.0, 0.6, 3.0), (17.6, 6.8, 10.0), 'ral7035', 'control box')
    m.box((17.6, 0.8, 3.2), (17.7, 6.6, 9.8), 'ral7032', 'box lid')
    cb = m.surface('east', face=16 - 17.72, title='BELT')
    m.ctl(cb, 'push', 'start', 16 - 8.6, 5.4, w=0.8, h=0.8, col='1fa34a', label='START')
    m.ctl(cb, 'push', 'stop', 16 - 6.8, 5.4, w=0.8, h=0.8, col='d02020', label='STOP')
    m.ctl(cb, 'mushroom', 'm_estop', 16 - 4.6, 5.0, w=1.3, h=1.3, col='d02020', label='E-STOP')
    m.box((17.7, 3.8, 3.4), (17.75, 6.2, 5.8), 'paint:#f0c418', 'estop collar')
    lamps(m, cb, [('runl', GREEN, 'RUN'), ('jaml', AMBER, 'JAM', True), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 16 - 9.4, 3.5, step=1.3, size=0.4, lab_dv=0.45)
    m.ctl(cb, 'gauge', 'amps', 16 - 4.4, 2.6, w=1.2, h=1.2, col='f2f0e6', label='MOTOR %', max=100)
    m.ctl(cb, 'digits', 'nmoved', 16 - 6.8, 1.6, w=2.4, h=0.5, col='7cf29a', label='', fmt='%d BAGS')
    m.ctl(cb, 'push', 'test', 16 - 9.2, 1.6, w=0.4, h=0.4, col='f0f0f0', label='TEST')
    m.gland(16.8, 0.6, 6.5, 'down', r=0.3, length=0.5)
    m.terminal_12v(16.8, 1.0, 10.0, 'south')
    mains_box(m, 0.0, 1.0, 8.0, 'west')
    m.save(item_gui=0.6)
    return m


# ============================================================================================================ baggage hall
XA, XB, ZC, R, TOP = -6.0, 22.0, 6.5, 4.2, 6.0      # must match travel6/client/CarouselRenderer


def bag_hall():
    m = Model('bag_hall')
    m.g('back wall')
    m.box((-16, 0, 15.2), (32, 24, 16), 'paint:#e9ecef', 'hall wall')
    m.box((-16, 0, 15.0), (32, 0.8, 15.4), 'paint:#1c2840', 'skirting')
    m.box((-16, 21.4, 14.6), (32, 23.6, 15.2), 'paint:#1c2840', 'fascia')
    m.box((-6, 21.8, 14.5), (22, 23.2, 14.6), 'plate', 'fascia lettering', north='label:BAGGAGE RECLAIM')
    rainbow(m, -16, 32, 21.0, 15.2, h=0.4)
    m.g('carousel island')
    m.box((XA, 0, ZC - 2.0), (XB, TOP - 0.4, ZC + 2.0), 'metal:steel', 'centre island')
    for x in (XA, XB):
        m.cyl((x, 0, ZC), 2.0, TOP - 0.4, 'y', 'metal:steel', 'island end')
    m.box((XA, TOP - 0.4, ZC - 0.6), (XB, TOP + 0.6, ZC + 0.6), 'paint:#2a2c30', 'island spine')
    m.g('slat track')
    m.box((XA, 0, ZC - R - 1.2), (XB, TOP - 0.3, ZC - R + 1.2), 'paint:#3a3e44', 'track skirt')
    m.box((XA, 0, ZC + R - 1.2), (XB, TOP - 0.3, ZC + R + 1.2), 'paint:#3a3e44', 'track skirt')
    for x in (XA, XB):
        m.cyl((x, 0, ZC), R + 1.2, TOP - 0.3, 'y', 'paint:#3a3e44', 'track end')
    n = int((XB - XA) / 1.6)
    for i in range(n):
        x = XA + i * (XB - XA) / n
        for zc in (ZC - R, ZC + R):
            m.box((x + 0.05, TOP - 0.3, zc - 1.2), (x + (XB - XA) / n - 0.05, TOP, zc + 1.2), 'metal:steel' if i % 2 else 'metal:alu', 'slat')
    import math
    for xe, sgn in ((XB, 1), (XA, -1)):
        for k in range(8):
            a = math.pi * (k + 0.5) / 8
            cx, cz = xe + sgn * math.sin(a) * R, ZC - math.cos(a) * R
            d = (math.degrees(a) * sgn + 45) % 90 - 45
            ang = min((-45, -22.5, 0, 22.5, 45), key=lambda q: abs(q - d))
            m.box((cx - 0.9, TOP - 0.3, cz - 1.1), (cx + 0.9, TOP, cz + 1.1), 'metal:alu' if k % 2 else 'metal:steel', 'curve slat',
                  rot={'axis': 'y', 'angle': ang, 'origin': [cx, TOP, cz]} if ang else None)
    for zc in (ZC - R - 1.2, ZC + R + 1.2):
        m.box((XA, TOP - 0.3, zc - 0.15), (XB, TOP + 0.5, zc + 0.15), 'paint:#141416', 'rubber edge flap')
    rc = m.surface('up', face=16 - TOP, title='CAROUSEL')
    m.ctl(rc, 'zone', 'reclaim', X((XA + XB) / 2), ZC, w=(XB - XA) + 2 * R + 2, h=2 * R + 2.4, col='000000', label='')
    m.box((XA, 0, ZC - R - 1.4), (XB, 1.2, ZC - R - 1.2), 'haz', 'floor hazard band')
    m.g('arrivals board')
    for x in (0.6, 15.4):
        m.cyl((x, TOP + 0.6, ZC), 0.35, 9.0, 'y', 'metal:steel', 'board post')
    m.box((0.0, 15.2, ZC - 0.4), (16.0, 21.0, ZC + 0.6), 'paint:#23272b', 'board bezel')
    m.box((0.4, 15.6, ZC - 0.5), (15.6, 20.6, ZC - 0.4), 'paint:#070a08', 'LED panel')
    for (x, y) in ((0.2, 15.4), (15.8, 15.4), (0.2, 20.8), (15.8, 20.8)):
        m.bolt(x, y, ZC - 0.4, 'north', 0.35)
    bb = m.surface('north', face=ZC - 0.52, title='ARRIVALS')
    m.ctl(bb, 'digits', 'hdr', X(8.0), 20.0, w=14.6, h=0.7, col='5bcefa', label='', fmt='%s')
    for i in range(4):
        m.ctl(bb, 'digits', 'b%d' % (i + 1), X(8.0), 19.0 - i * 0.85, w=14.6, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(bb, 'digits', 'non', X(8.0), 15.9, w=6.0, h=0.4, col='7cf29a', label='', fmt='%d ON CAROUSEL')
    m.g('beacon')
    m.cyl((20.0, TOP + 0.6, ZC), 0.3, 6.0, 'y', 'metal:steel', 'beacon post')
    m.cyl((20.0, TOP + 6.6, ZC), 0.8, 1.4, 'y', 'paint:#ff9a10', 'amber beacon dome')
    bc = m.surface('north', face=ZC - 0.85, title='BEACON')
    m.ctl(bc, 'lamp', 'beacon', X(20.0), TOP + 7.3, w=1.2, h=1.2, col=AMBER, label='BAGS', blink=True)
    m.g('make-up cart')
    m.box((-15.8, 1.2, 11.6), (-9.2, 1.8, 15.0), 'paint:#2a3442', 'cart bed')
    for (x, z) in ((-15.0, 11.6), (-10.0, 11.6), (-15.0, 15.0), (-10.0, 15.0)):
        m.cyl((x, 0.6, z - 0.2), 0.6, 0.4, 'z', 'rubber', 'cart wheel')
    m.box((-15.8, 1.8, 11.6), (-15.5, 9.0, 15.0), 'mesh', 'cart side')
    m.box((-9.5, 1.8, 11.6), (-9.2, 9.0, 15.0), 'mesh', 'cart side')
    m.box((-15.8, 8.6, 11.4), (-9.2, 9.0, 11.6), 'paint:#f0c418', 'cart rail')
    m.box((-9.2, 1.4, 12.8), (-8.4, 1.8, 13.6), 'metal:steel', 'tow bar')
    for i, (x, c) in enumerate(((-15.4, '#732982'), (-13.4, '#5a3a24'), (-11.4, '#24408e'), (-14.6, '#c03030'), (-12.4, '#2e5e3a'))):
        y = 1.8 if i < 3 else 4.6
        m.box((x, y, 12.0), (x + 1.8, y + 2.6, 14.6), 'paint:' + c, 'bag on cart')
    mk = m.surface('north', face=11.35, title='MAKE-UP')
    m.ctl(mk, 'zone', 'makeup', X(-12.5), 9.5, w=6.4, h=5.0, col='000000', label='')
    m.box((-15.8, 12.4, 15.0), (-9.2, 17.6, 15.2), 'paint:#1c1e20', 'make-up screen')
    m.box((-15.8, 17.6, 15.0), (-9.2, 18.4, 15.2), 'label:MAKE-UP', 'make-up label')
    ms = m.surface('north', face=14.98, title='MAKE-UP SCREEN')
    for i in range(3):
        m.ctl(ms, 'digits', 'mk%d' % (i + 1), X(-12.5), 16.8 - i * 1.4, w=6.2, h=1.0, col='ffd040', label='', fmt='%s')
    m.ctl(ms, 'digits', 'nmk', X(-12.5), 12.9, w=6.0, h=0.5, col='7cf29a', label='', fmt='%d TO LOAD')
    m.g('control pedestal')
    m.box((27.0, 0, 0.6), (31.8, 9.0, 3.6), 'ral7035', 'control pedestal')
    m.box((26.8, 9.0, 0.4), (32.0, 9.4, 3.8), 'ral7016', 'pedestal cap')
    cp = m.surface('north', face=0.5, title='CAROUSEL CONTROL')
    pushes(m, cp, [('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP')], X(31.0), 8.0, step=1.4, size=0.75, lab_dv=0.6)
    m.ctl(cp, 'mushroom', 'm_estop', X(28.2), 7.8, w=1.3, h=1.3, col='d02020', label='E-STOP')
    m.ctl(cp, 'digits', 'msg', X(29.4), 9.0, w=4.6, h=0.4, col='8ad4ff', label='', fmt='%s')
    lamps(m, cp, [('runl', GREEN, 'RUN'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('fulll', RED, 'FULL', True)], X(31.2), 5.8, step=1.05, size=0.4, lab_dv=0.45)
    m.ctl(cp, 'gauge', 'amps', X(30.6), 3.6, w=1.3, h=1.3, col='f2f0e6', label='DRIVE %', max=100)
    pushes(m, cp, [('claim', 'f0d020', 'CLAIM'), ('print', 'f0f0f0', 'PRINT')], X(29.0), 3.9, step=1.3, size=0.65, lab_dv=0.55)
    m.ctl(cp, 'key', 'k_staff', X(28.0), 1.6, w=0.8, h=0.8, col='c0a040', label='STAFF', pos=['OFF', 'ON'])
    m.ctl(cp, 'lamp', 'stfl', X(29.4), 1.6, w=0.35, h=0.35, col=AMBER, label='STAFF')
    m.ctl(cp, 'push', 'test', X(31.0), 1.6, w=0.4, h=0.4, col='f0f0f0', label='TEST')
    m.terminal_12v(29.4, 0.8, 3.6, 'south')
    m.g('cable riser')
    m.box((27.6, 0, 10.4), (30.0, 12.0, 15.2), 'ral7035', 'cable riser cabinet')
    m.box((27.5, 9.0, 10.6), (27.6, 11.6, 13.2), 'warn', 'riser warning')
    m.cyl((28.8, 0, 6.0), 0.3, 0.2, 'y', 'cable:1d1d21', 'floor duct cap')
    mains_box(m, 30.0, 4.0, 12.8, 'east')
    m.save(item_gui=0.26)
    return m


# ============================================================================================================ car park barrier
def barrier_post(m):
    m.g('post cabinet')
    m.box((11.0, 0, 4.0), (16.0, 14.0, 12.0), 'paint:#f0c418', 'barrier cabinet')
    m.box((10.8, 14.0, 3.8), (16.2, 14.6, 12.2), 'paint:#1a1a1a', 'cabinet lid')
    m.box((10.8, 0, 3.8), (16.2, 0.6, 12.2), 'galv', 'base frame')
    for (x, z) in ((11.4, 4.4), (15.6, 4.4), (11.4, 11.6), (15.6, 11.6)):
        m.bolt(x, 0.6, z, 'up', 0.45)
    m.box((11.2, 2.0, 3.9), (15.8, 8.0, 4.0), 'paint:#e0b410', 'access door')
    m.box((15.0, 4.6, 3.75), (15.3, 5.4, 3.9), 'metal:steel', 'door lock')
    m.box((11.2, 8.2, 3.9), (15.8, 8.8, 4.0), 'label:P+R', 'brand strip')
    m.g('drive')
    m.cyl((10.4, 11.2, 8.0), 1.2, 0.6, 'x', 'metal:steel', 'boom hub')
    m.cyl((16.0, 11.2, 8.0), 0.9, 0.6, 'x', 'paint:#2a2c30', 'gear cover')
    f = m.surface('north', face=3.85, title='BARRIER')
    m.box((11.4, 9.2, 3.8), (15.6, 13.6, 3.9), 'paint:#1c1e20', 'screen bezel')
    m.ctl(f, 'digits', 'l1', X(13.5), 12.8, w=3.9, h=0.6, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'l2', X(13.5), 12.0, w=3.9, h=0.55, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'lamp', 'go', X(12.2), 10.6, w=0.55, h=0.55, col=GREEN, label='GO')
    m.ctl(f, 'lamp', 'stop', X(13.5), 10.6, w=0.55, h=0.55, col=RED, label='STOP')
    m.ctl(f, 'lamp', 'fulll', X(14.8), 10.6, w=0.55, h=0.55, col=RED, label='FULL', blink=True)
    m.ctl(f, 'push', 'tkt', X(12.4), 7.2, w=1.2, h=1.2, col='2050d0', label='TICKET')
    m.ctl(f, 'label', '', X(12.4), 6.2, w=0, h=0, col='e8e8e8', label='TICKET')
    m.ctl(f, 'push', 'help', X(14.8), 7.2, w=0.8, h=0.8, col='f0d020', label='HELP')
    for i in range(4):
        m.box((14.2, 5.4 + i * 0.3, 3.85), (15.4, 5.5 + i * 0.3, 3.9), 'paint:#1a1a1a', 'intercom grille')
    m.box((11.6, 4.2, 3.7), (13.2, 5.0, 3.9), 'paint:#101216', 'ticket mouth')
    m.box((11.8, 4.5, 3.65), (13.0, 4.65, 3.7), 'paint:#f4f2ea', 'ticket')
    m.box((13.4, 2.4, 3.6), (15.6, 4.6, 3.9), 'paint:#20242c', 'card reader')
    card_pad(m, f, 14.5, 3.5, 3.6, r=0.8)
    m.ctl(f, 'digits', 'nin', X(12.4), 2.9, w=1.4, h=0.4, col='7cf29a', label='', fmt='%d IN')
    m.ctl(f, 'digits', 'nout', X(12.4), 2.3, w=1.4, h=0.4, col='8ad4ff', label='', fmt='%d OUT')
    m.g('service side')
    s = m.surface('south', face=16 - 12.05, title='SERVICE')
    m.box((11.6, 6.0, 12.0), (15.4, 12.4, 12.1), 'paint:#2a2c30', 'service panel')
    m.ctl(s, 'key', 'k_mode', 12.4, 11.0, w=0.8, h=0.8, col='c0a040', label='MODE', pos=['ENTRY', 'EXIT'])
    m.ctl(s, 'label', '', 12.4, 10.2, w=0, h=0, col='e8e8e8', label='MODE')
    m.ctl(s, 'key', 'k_up', 14.6, 11.0, w=0.8, h=0.8, col='c0a040', label='BOOM', pos=['AUTO', 'UP'])
    m.ctl(s, 'label', '', 14.6, 10.2, w=0, h=0, col='e8e8e8', label='BOOM UP')
    m.ctl(s, 'lamp', 'upl', 14.6, 8.8, w=0.4, h=0.4, col=AMBER, label='HELD UP')
    m.ctl(s, 'lamp', 'pwr', 12.4, 8.8, w=0.4, h=0.4, col=GREEN, label='12V')
    m.ctl(s, 'lamp', 'mainsl', 13.5, 8.8, w=0.4, h=0.4, col=GREEN, label='MAINS')
    m.ctl(s, 'push', 'test', 13.5, 7.2, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    m.g('cabinet detail')
    for y in (12.6, 13.2):
        m.box((16.0, y, 5.0), (16.1, y + 0.3, 11.0), 'paint:#1a1a1a', 'vent slot')
    m.box((16.0, 6.0, 4.4), (16.1, 8.4, 6.6), 'danger', 'danger plate', east='danger:DANGER|BOOM')
    m.box((11.0, 0.6, 3.9), (16.0, 1.4, 4.0), 'haz', 'hazard base band')
    for (x, y) in ((11.3, 1.8), (15.7, 1.8), (11.3, 13.6), (15.7, 13.6)):
        m.bolt(x, y, 4.0, 'north', 0.3)
    m.cyl((16.6, 11.2, 8.0), 1.5, 1.0, 'x', 'paint:#1a1a1a', 'counterweight drum')
    m.box((16.6, 9.0, 7.4), (17.6, 10.0, 8.6), 'paint:#1a1a1a', 'spring housing')
    m.g('vehicle loop')
    m.box((0.0, 0.0, 0.0), (11.0, 0.05, 0.4), 'paint:#2a2a2a', 'loop saw cut')
    m.box((0.0, 0.0, 3.0), (11.0, 0.05, 3.4), 'paint:#2a2a2a', 'loop saw cut')
    m.box((0.0, 0.0, 0.0), (0.4, 0.05, 3.4), 'paint:#2a2a2a', 'loop saw cut')
    m.box((11.0, 0.0, 0.0), (11.4, 0.05, 4.0), 'paint:#2a2a2a', 'loop feeder')
    m.box((11.6, 0.0, 2.2), (13.4, 0.6, 3.6), 'ral7035', 'loop junction box')
    m.terminal_12v(16.0, 1.0, 8.0, 'east')
    mains_box(m, 16.0, 2.0, 5.2, 'east')


def boom_down(m):
    m.g('boom')
    for i, x0 in enumerate(range(-15, 10, 3)):
        m.box((x0, 10.4, 7.3), (min(10.4, x0 + 3), 12.0, 8.7), 'paint:' + ('#d02020' if i % 2 else '#f4f4f0'), 'boom section')
    m.box((-15.8, 10.2, 7.1), (-15.0, 12.2, 8.9), 'rubber', 'boom tip')
    for x in (8.0, 9.4):
        m.bolt(x, 11.2, 7.3, 'north', 0.35)
        m.bolt(x, 11.2, 8.7, 'south', 0.35)
    m.box((7.6, 10.2, 7.1), (10.4, 12.2, 8.9), 'metal:steel', 'boom clamp')
    m.box((-14.0, 9.8, 7.0), (9.0, 10.4, 9.0), 'rubber', 'boom skirt rubber')
    for x in range(-13, 9, 4):
        m.box((x, 10.6, 7.25), (x + 0.6, 11.8, 7.3), 'paint:#f0c418', 'reflector')
    rest(m)


def boom_up(m):
    m.g('boom')
    for i, y0 in enumerate(range(12, 31, 3)):
        m.box((9.6, y0, 7.3), (11.2, min(31.4, y0 + 3), 8.7), 'paint:' + ('#d02020' if i % 2 else '#f4f4f0'), 'boom section')
    m.box((9.4, 31.4, 7.1), (11.4, 32.0, 8.9), 'rubber', 'boom tip')
    for y in (11.6, 13.0):
        m.bolt(10.4, y, 7.3, 'north', 0.35)
        m.bolt(10.4, y, 8.7, 'south', 0.35)
    m.box((9.4, 10.2, 7.1), (11.4, 13.4, 8.9), 'metal:steel', 'boom clamp')
    for y in range(14, 30, 4):
        m.box((9.55, y, 7.25), (11.25, y + 0.6, 7.3), 'paint:#f0c418', 'reflector')
    rest(m)


def rest(m):
    m.g('boom rest')
    m.box((-15.6, 0, 7.0), (-14.4, 9.8, 9.0), 'paint:#f0c418', 'rest post')
    m.box((-15.8, 9.8, 6.8), (-14.2, 10.4, 9.2), 'rubber', 'rest fork')
    m.box((-16.0, 0, 6.6), (-14.0, 0.4, 9.4), 'galv', 'rest foot')
    for y in (2.0, 4.0, 6.0, 8.0):
        m.box((-15.65, y, 6.95), (-14.35, y + 1.0, 7.0), 'paint:#1a1a1a', 'rest post stripe')
    for (x, z) in ((-15.7, 6.8), (-14.3, 6.8), (-15.7, 9.2), (-14.3, 9.2)):
        m.bolt(x, 0.4, z, 'up', 0.35)
    m.box((-15.2, 8.8, 6.9), (-14.8, 9.4, 7.0), 'paint:#f0c418', 'rest reflector')


def park_barrier():
    out = []
    for name, boom in (('park_barrier', boom_down), ('park_barrier_open', boom_up)):
        m = Model(name, block='park_barrier')
        barrier_post(m)
        boom(m)
        m.save(item_gui=0.45)
        out.append(m)
    return out


# ============================================================================================================ pay station + sign
def park_pay():
    m = Model('park_pay')
    m.g('pay station')
    m.box((3.0, 0, 4.4), (13.0, 19.0, 12.0), 'paint:#24408e', 'pay station body')
    m.box((2.8, 19.0, 4.2), (13.2, 19.8, 12.2), 'paint:#182c62', 'hood')
    m.box((2.8, 0, 4.2), (13.2, 0.6, 12.2), 'galv', 'plinth')
    f = m.surface('north', face=4.3, title='PAY HERE')
    m.box((3.4, 17.6, 4.3), (12.6, 18.8, 4.4), 'label:PAY HERE', 'pay label')
    rainbow(m, 3.4, 12.6, 17.2, 4.4, h=0.3)
    m.box((3.6, 12.6, 3.9), (12.4, 16.8, 4.4), 'paint:#1c1e20', 'screen bezel')
    m.box((3.9, 12.9, 3.85), (12.1, 16.5, 3.9), 'paint:#03140a', 'screen')
    sc = m.surface('north', face=3.8, title='SCREEN')
    for i in range(4):
        m.ctl(sc, 'digits', 's%d' % (i + 1), X(8.0), 16.0 - i * 0.78, w=7.8, h=0.62, col='ffd040' if i == 0 else 'f0f0e0', label='', fmt='%s')
    m.ctl(sc, 'digits', 'msg', X(8.0), 13.2, w=7.8, h=0.45, col='8ad4ff', label='', fmt='%s')
    m.g('slot + pad')
    m.box((3.8, 10.2, 3.9), (7.6, 11.8, 4.4), 'paint:#101216', 'ticket slot housing')
    m.box((4.2, 10.9, 3.85), (7.2, 11.1, 3.9), 'paint:#000000', 'ticket slot')
    m.box((3.8, 11.8, 4.2), (7.6, 12.3, 4.3), 'label:TICKET', 'slot label')
    m.ctl(f, 'zone', 'slot', X(5.7), 11.0, w=3.8, h=1.6, col='000000', label='')
    m.ctl(f, 'lamp', 'tktl', X(5.7), 9.7, w=0.35, h=0.35, col=GREEN, label='TICKET IN')
    m.box((8.6, 8.8, 3.9), (12.4, 12.3, 4.4), 'paint:#20242c', 'card reader')
    card_pad(m, f, 10.5, 10.6, 3.9, r=1.3)
    m.box((8.6, 12.3, 4.2), (12.4, 12.6, 4.3), 'label:PRIDE CARD', 'pad label')
    m.g('keys')
    pushes(m, f, [('pay', '1fa34a', 'PAY'), ('ret', '909090', 'RETURN'), ('print', 'f0f0f0', 'LOG')], X(4.6), 7.4, step=2.1, size=0.95, lab_dv=0.75)
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('paidl', GREEN, 'PAID')], X(4.6), 4.8, step=1.6, size=0.4, lab_dv=0.45)
    m.ctl(f, 'gauge', 'occ', X(11.2), 4.4, w=1.6, h=1.6, col='f2f0e6', label='FULL %', max=100)
    m.box((3.6, 1.0, 4.2), (12.4, 3.0, 4.4), 'paint:#182c62', 'coin-free plate')
    m.box((3.8, 1.3, 4.15), (12.2, 2.7, 4.2), 'label:CARD + BANK ONLY', 'payment plate')
    m.g('service side')
    s = m.surface('east', face=16 - 13.05, title='SERVICE')
    m.box((13.0, 4.0, 5.4), (13.1, 12.0, 11.0), 'paint:#2a2c30', 'service panel')
    m.ctl(s, 'key', 'k_svc', 16 - 6.4, 11.0, w=0.8, h=0.8, col='c0a040', label='SERVICE', pos=['LOCKED', 'OPEN'])
    m.ctl(s, 'lamp', 'svcl', 16 - 6.4, 9.8, w=0.35, h=0.35, col=AMBER, label='SVC')
    m.ctl(s, 'push', 'capdn', 16 - 8.2, 11.0, w=0.6, h=0.6, col='c8ccd0', label='BAYS -')
    m.ctl(s, 'push', 'capup', 16 - 9.6, 11.0, w=0.6, h=0.6, col='c8ccd0', label='BAYS +')
    m.ctl(s, 'digits', 'baysv', 16 - 8.9, 9.8, w=2.4, h=0.5, col='7cf29a', label='', fmt='%s')
    m.ctl(s, 'selector', 's_rad', 16 - 7.6, 7.4, w=1.2, h=1.2, col='202020', label='GARAGE', pos=['8', '16', '24', '32'])
    m.ctl(s, 'label', '', 16 - 7.6, 6.4, w=0, h=0, col='e8e8e8', label='GARAGE M')
    m.ctl(s, 'digits', 'radv', 16 - 9.8, 7.4, w=1.6, h=0.5, col='ffd040', label='', fmt='%s')
    m.ctl(s, 'push', 'test', 16 - 6.2, 5.0, w=0.45, h=0.45, col='f0f0f0', label='TEST')
    m.g('pay station detail')
    for (x, z) in ((3.2, 4.6), (12.8, 4.6), (3.2, 11.8), (12.8, 11.8)):
        m.bolt(x, 0.6, z, 'up', 0.45)
    for (x, y) in ((3.4, 0.9), (12.6, 0.9), (3.4, 18.6), (12.6, 18.6)):
        m.bolt(x, y, 4.4, 'north', 0.35)
    m.box((2.8, 19.8, 4.0), (13.2, 20.1, 12.4), 'paint:#101216', 'drip cap')
    for z in (6.0, 8.0, 10.0):
        m.box((2.9, 14.0, z), (3.0, 17.0, z + 1.0), 'paint:#101216', 'side vent')
    m.box((2.9, 4.0, 5.0), (3.0, 12.0, 11.4), 'plate', 'tariff plate', west='label:1 HOUR $1|P+R 25%|WITH PRIDE CARD')
    m.box((3.0, 0.6, 4.3), (13.0, 0.9, 4.4), 'rubber', 'kick strip')
    m.box((3.6, 12.3, 4.2), (12.4, 12.6, 4.4), 'metal:steel', 'screen sill')
    m.cyl((8.0, 19.8, 11.0), 0.3, 4.2, 'y', 'cable:1d1d21', 'sign feed cable')
    m.g('sign mast')
    m.box((7.2, 19.8, 10.2), (8.8, 24.0, 11.8), 'galv', 'sign mast')
    m.box((0.2, 24.0, 9.6), (15.8, 31.6, 11.4), 'paint:#1c2840', 'sign cabinet')
    m.box((0.0, 31.6, 9.4), (16.0, 32.0, 11.6), 'paint:#101216', 'sign cap')
    m.box((0.6, 24.6, 9.5), (5.6, 31.0, 9.6), 'paint:#24408e', 'P panel')
    m.box((1.4, 25.4, 9.45), (4.8, 30.2, 9.5), 'label:P', 'P letter')
    m.box((6.2, 29.0, 9.5), (15.2, 31.0, 9.6), 'label:SPACES', 'spaces legend')
    m.box((6.2, 24.6, 9.5), (15.2, 28.6, 9.6), 'paint:#05080a', 'number window')
    sg = m.surface('north', face=9.45, title='SPACES FREE')
    m.ctl(sg, 'digits', 'free', X(10.0), 26.6, w=5.6, h=3.2, col='ffb020', label='', fmt='%s')
    m.ctl(sg, 'lamp', 'freel', X(14.4), 27.8, w=0.6, h=0.6, col=GREEN, label='SPACES')
    m.ctl(sg, 'lamp', 'fulll', X(14.4), 25.6, w=0.6, h=0.6, col=RED, label='FULL', blink=True)
    m.g('sign detail')
    for (x, y) in ((0.6, 24.4), (15.4, 24.4), (0.6, 31.2), (15.4, 31.2)):
        m.bolt(x, y, 9.6, 'north', 0.35)
    m.box((0.2, 24.0, 11.4), (15.8, 31.6, 11.6), 'paint:#101216', 'sign back plate')
    for x in (3.0, 13.0):
        m.box((x - 0.6, 22.6, 11.4), (x + 0.6, 24.0, 12.4), 'galv', 'sign bracket')
        m.box((x - 0.2, 21.6, 11.6), (x + 0.2, 22.6, 12.0), 'galv', 'bracket strut')
    m.box((7.0, 22.4, 10.0), (9.0, 22.8, 12.0), 'galv', 'mast clamp')
    m.box((0.4, 31.0, 9.3), (15.6, 31.3, 9.5), 'paint:#f4f4f0', 'sign light tube')
    m.box((6.0, 28.7, 9.48), (15.4, 28.9, 9.5), 'paint:#24408e', 'legend rule')
    m.terminal_12v(3.0, 1.0, 8.0, 'west')
    mains_box(m, 3.0, 4.6, 10.2, 'west')
    m.save(item_gui=0.4)
    return m


# ============================================================================================================ item textures
def item_textures():
    d = os.path.join(ASSETS, 'textures/items')
    P = lambda img, x, y, c: img.putpixel((x, y), c)
    hx = lambda h: tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)

    # Pride Card: blue card, rainbow band, gold chip, contactless waves
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(1, 15):
        for y in range(4, 13):
            P(im, x, y, hx('#1c2c6c') if 1 < x < 14 or 4 < y < 12 else hx('#14204e'))
    for i, c in enumerate(RAINBOW):
        P(im, 2 + i * 2, 11, hx(c)); P(im, 3 + i * 2, 11, hx(c))
    for (x, y) in ((3, 6), (4, 6), (5, 6), (3, 7), (4, 7), (5, 7), (3, 8), (4, 8), (5, 8)):
        P(im, x, y, hx('#d8b040'))
    P(im, 4, 7, hx('#a88020'))
    for (x, y) in ((10, 6), (11, 7), (11, 8), (10, 9), (12, 5), (13, 7), (13, 8), (12, 10)):
        P(im, x, y, hx('#f0f0f0'))
    im.save(os.path.join(d, 'pride_card.png'))

    # Pride Rail phone: black handset, screen with the app header + rainbow
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(4, 12):
        for y in range(1, 15):
            P(im, x, y, hx('#16171b'))
    for x in range(5, 11):
        for y in range(3, 13):
            P(im, x, y, hx('#0e1420'))
        P(im, x, 3, hx('#1c2840')); P(im, x, 4, hx('#1c2840'))
    for i, c in enumerate(RAINBOW):
        P(im, 5 + i, 5, hx(c))
    for y in (7, 9, 11):
        for x in range(6, 10 if y != 9 else 9):
            P(im, x, y, hx('#ffd040' if y == 7 else '#7cf29a'))
    P(im, 7, 13, hx('#606470')); P(im, 8, 13, hx('#606470'))
    P(im, 7, 2, hx('#050505')); P(im, 8, 2, hx('#050505'))
    im.save(os.path.join(d, 'rail_phone.png'))

    # claim tag: white tag with a barcode and a loop of string
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        for y in range(5, 14):
            P(im, x, y, hx('#f4f2ea'))
    for x in range(4, 12):
        if x % 2 == 0 or x in (5, 9):
            for y in range(9, 13):
                P(im, x, y, hx('#101010'))
    for x in range(4, 12):
        P(im, x, 6, hx('#24408e')); P(im, x, 7, hx('#24408e'))
    for (x, y) in ((7, 4), (8, 3), (9, 2), (10, 2), (11, 3), (8, 4)):
        P(im, x, y, hx('#c03030'))
    im.save(os.path.join(d, 'bag_tag.png'))

    # parking ticket: white ticket, blue P, magnetic stripe
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(2, 14):
        for y in range(4, 12):
            P(im, x, y, hx('#f4f2ea'))
    for x in range(2, 14):
        P(im, x, 10, hx('#3a3a3a')); P(im, x, 11, hx('#3a3a3a'))
    for (x, y) in ((4, 5), (4, 6), (4, 7), (4, 8), (5, 5), (6, 5), (6, 6), (5, 7), (6, 7)):
        P(im, x, y, hx('#24408e'))
    for x in range(8, 13):
        P(im, x, 6, hx('#808890'))
    for x in range(8, 11):
        P(im, x, 8, hx('#808890'))
    im.save(os.path.join(d, 'park_ticket.png'))

    # suitcase: purple hard case, handle, straps, wheels
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(2, 14):
        for y in range(4, 14):
            P(im, x, y, hx('#732982') if 2 < x < 13 or 4 < y < 13 else hx('#4e1a5a'))
    for y in range(4, 14):
        P(im, 5, y, hx('#4e1a5a')); P(im, 10, y, hx('#4e1a5a'))
    for x in range(6, 10):
        P(im, x, 2, hx('#1a1a1a'))
    P(im, 6, 3, hx('#1a1a1a')); P(im, 9, 3, hx('#1a1a1a'))
    P(im, 3, 14, hx('#101010')); P(im, 12, 14, hx('#101010'))
    for (x, y) in ((11, 6), (12, 6), (11, 7), (12, 7)):
        P(im, x, y, hx('#f4f2ea'))
    im.save(os.path.join(d, 'suitcase.png'))


RECIPES = {
    'card_reader': (['SLS', ' I ', ' I '], {'S': 'irextras:part@20', 'L': 'minecraft:glass_pane', 'I': 'minecraft:iron_bars'}),
    'card_topup': (['SGS', 'BPB', 'IRI'], {'S': 'irextras:part@20', 'G': 'minecraft:glass_pane', 'B': 'minecraft:stone_button', 'P': 'minecraft:paper', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'left_luggage': (['ICI', 'ISI', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest', 'S': 'irextras:part@20'}),
    'bag_checkin': (['SGS', 'PDP', 'WCW'], {'S': 'irextras:part@20', 'G': 'minecraft:glass_pane', 'P': 'minecraft:paper', 'D': 'minecraft:heavy_weighted_pressure_plate', 'W': 'minecraft:planks', 'C': 'minecraft:chest'}),
    'bag_belt': (['LLL', 'IRI'], {'L': 'minecraft:leather', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'bag_hall': (['SLS', 'ICI', 'IRI'], {'S': 'irextras:part@20', 'L': 'minecraft:leather', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest', 'R': 'minecraft:redstone'}),
    'park_barrier': (['   ', 'SRR', 'P  '], {'S': 'irextras:part@20', 'R': 'minecraft:stick', 'P': 'minecraft:iron_block'}),
    'park_pay': (['GSG', 'BPB', ' I '], {'S': 'irextras:part@20', 'G': 'minecraft:glowstone_dust', 'B': 'minecraft:stone_button', 'P': 'minecraft:paper', 'I': 'minecraft:iron_ingot'}),
}
ITEM_RECIPES = [  # (result meta, count, pattern, key)
    (0, 4, ['PPP', 'GRB'], {'P': 'minecraft:paper', 'G': 'minecraft:gold_nugget', 'R': 'minecraft:redstone', 'B': 'minecraft:dye@4'}),
    (1, 1, ['IGI', 'IRI', 'ICI'], {'I': 'minecraft:iron_nugget', 'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone', 'C': 'minecraft:comparator'}),
]
LANG = {
    'tile.irextras.card_reader.name': 'Pride Card Reader', 'tile.irextras.card_topup.name': 'Pride Card Top-Up Machine',
    'tile.irextras.left_luggage.name': 'Left Luggage Lockers', 'tile.irextras.bag_checkin.name': 'Baggage Check-In Desk',
    'tile.irextras.bag_belt.name': 'Baggage Belt', 'tile.irextras.bag_hall.name': 'Baggage Hall (Reclaim Carousel)',
    'tile.irextras.park_barrier.name': 'Car Park Barrier', 'tile.irextras.park_pay.name': 'Park & Ride Pay Station',
    'tile.irextras.park_arm.name': 'Barrier Boom',
    'item.irextras.pride_card.name': 'Pride Card', 'item.irextras.rail_phone.name': 'Pride Rail Phone',
    'item.irextras.bag_tag.name': 'Baggage Claim Tag', 'item.irextras.park_ticket.name': 'Parking Ticket', 'item.irextras.suitcase.name': 'Suitcase',
}
FACING = ['card_reader', 'card_topup', 'left_luggage', 'bag_checkin', 'bag_belt', 'bag_hall', 'park_pay']

if __name__ == '__main__':
    for fn in (card_reader, card_topup, left_luggage, bag_checkin, bag_belt, bag_hall, park_pay):
        mm = fn()
        print('%-16s %d boxes' % (mm.name, len(mm.boxes)))
    for mm in park_barrier():
        print('%-16s %d boxes' % (mm.name, len(mm.boxes)))
    item_textures()
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for n in FACING:
        bs = {'variants': {'facing=%s' % f: dict({'model': 'irextras:' + n}, **({'y': r} if r else {})) for f, r in rot.items()}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
    bs = {'variants': {'facing=%s,open=%s' % (f, o): dict({'model': 'irextras:park_barrier' + ('_open' if o == 'true' else '')}, **({'y': r} if r else {}))
                       for f, r in rot.items() for o in ('false', 'true')}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates/park_barrier.json'), 'w'), indent=1)
    json.dump({'variants': {'facing=%s' % f: {'model': 'irextras:park_arm'} for f in rot}}, open(os.path.join(ASSETS, 'blockstates/park_arm.json'), 'w'), indent=1)
    json.dump({'textures': {'particle': 'irextras:blocks/bb_park_barrier'}, 'elements': []}, open(os.path.join(ASSETS, 'models/block/park_arm.json'), 'w'), indent=1)
    for n in FACING + ['park_barrier']:
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for v in ('pride_card', 'rail_phone', 'bag_tag', 'park_ticket', 'suitcase'):
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + v}}, open(os.path.join(ASSETS, 'models/item/%s.json' % v), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    for meta, count, pat, key in ITEM_RECIPES:
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:travel', 'data': meta, 'count': count}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % ('pride_card_blank' if meta == 0 else 'rail_phone')), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as fh: fh.write(('' if have.endswith('\n') else '\n') + '\n# passenger travel: Pride Card, phone, left luggage, baggage, park & ride (batch 6b #6-10)\n' + '\n'.join(add) + '\n')
