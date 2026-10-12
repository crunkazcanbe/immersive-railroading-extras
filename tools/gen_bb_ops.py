#!/usr/bin/env python3
"""Station operations gear (her ChatGPT batch 3 #1 loco changeover, #2 banking, #4 coupling points, #5 turnback).
Lineside cabinets, front = north, doors swung open on the control plate. Every lamp, display, dial, selector, key and
button is a live control the Java tile (com.dogpound.railmap.planning.Tile*) reads or works. The 12 V terminal boxes are
wire terminals, not buttons. Run: python3 tools/gen_bb_ops.py  (models, textures, controls, blockstates, item models, recipes)"""
import json, os
from bbkit import Model, ASSETS

SIGNAL_GREY = 'ral7032'
PLATE = 'paint:#c9cbc7'


def facing_states(name, model=None):
    model = model or name
    bs = {'variants': {'facing=north': {'model': 'irextras:' + model}, 'facing=east': {'model': 'irextras:' + model, 'y': 90},
                       'facing=south': {'model': 'irextras:' + model, 'y': 180}, 'facing=west': {'model': 'irextras:' + model, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + model}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label, s=0.62, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def cabinet(m, body, title, maker, stripe=None):
    """the shared lineside cabinet: plinth, body, rain hood, open double doors, control plate, louvres, glands,
    earth bar, door stays and handles, beacon on the roof; returns the control surface (plate face z = 7.9)"""
    m.g('plinth')
    m.box((0.6, 0, 7.6), (15.4, 1.0, 16.0), 'concrete', 'concrete plinth')
    for x in (1.2, 14.8):
        for z in (8.2, 15.4):
            m.bolt(x, 1.0, z, 'up', 0.4)
    m.g('cabinet')
    m.box((1.0, 1.0, 8.0), (15.0, 15.0, 15.6), body, 'cabinet body')
    m.box((0.7, 15.0, 7.5), (15.3, 15.6, 15.9), body, 'rain hood')
    m.box((0.5, 15.6, 7.4), (15.5, 15.9, 16.0), 'paint:#3a3d40', 'hood drip edge')
    m.box((1.0, 1.0, 7.9), (15.0, 1.4, 8.0), body, 'door sill')
    m.box((1.0, 14.6, 7.9), (15.0, 15.0, 8.0), body, 'door head')
    m.box((1.0, 1.4, 7.9), (1.4, 14.6, 8.0), body, 'door post')
    m.box((14.6, 1.4, 7.9), (15.0, 14.6, 8.0), body, 'door post')
    m.box((1.4, 1.4, 7.92), (14.6, 14.6, 8.0), PLATE, 'control plate')
    m.box((2.0, 14.0, 7.86), (14.0, 14.5, 7.92), 'label:' + title, 'legend', faces=('north',))
    if stripe:
        m.box((1.0, 1.0, 7.95), (15.0, 1.4, 8.0), stripe, 'hazard sill', faces=('north',))
    m.g('doors')
    for (x0, x1, hx) in ((-4.6, 1.0, 1.0), (15.0, 20.6, 15.0)):                    # leaves swung 90 degrees open
        xl = -4.6 if x0 < 0 else 15.0
        m.box((hx - 0.2 if x0 < 0 else hx, 1.4, 1.6), (hx if x0 < 0 else hx + 0.2, 14.6, 7.8), body, 'door leaf')
        hxx = hx - 0.2 if x0 < 0 else hx + 0.2
        for y in (3.0, 7.6, 12.2):
            m.box((hx - 0.4, y, 7.6), (hx + 0.4, y + 1.0, 8.1), 'metal:steel', 'hinge')
        sgn = -1 if x0 < 0 else 1
        m.box((hxx + sgn * 0.0 - (0.3 if sgn < 0 else 0), 6.6, 2.4), (hxx + (0 if sgn < 0 else 0.3), 8.6, 2.8), 'metal:steel', 'door handle')
        m.box((hxx - (0.15 if sgn < 0 else 0), 9.0, 3.4), (hxx + (0 if sgn < 0 else 0.15), 13.0, 6.8), 'paint:#f0eee0', 'wiring diagram pocket')
        m.box((hxx - (0.12 if sgn < 0 else 0), 2.4, 3.0), (hxx + (0 if sgn < 0 else 0.12), 5.4, 7.0), 'louvre', 'door louvre')
    m.box((1.2, 14.0, 6.6), (1.4, 14.2, 7.9), 'metal:steel', 'door stay')
    m.box((14.6, 14.0, 6.6), (14.8, 14.2, 7.9), 'metal:steel', 'door stay')
    m.g('sides + back')
    m.louvres(2.0, 14.0, 9.0, 13.0, 15.6, n=5)
    for z in (9.5, 11.5, 13.5):
        m.box((0.85, 10.0, z), (1.0, 13.5, z + 1.2), 'louvre', 'side louvre', faces=('west',))
        m.box((15.0, 10.0, z), (15.15, 13.5, z + 1.2), 'louvre', 'side louvre', faces=('east',))
    m.box((15.0, 2.0, 9.0), (15.25, 6.5, 15.0), 'plate:' + maker, 'maker plate', faces=('east',))
    m.box((0.75, 2.4, 9.4), (1.0, 5.4, 12.6), 'warn:', 'warning label', faces=('west',))
    m.g('glands + earth')
    for x in (3.0, 4.6, 6.2, 9.8, 11.4, 13.0):
        m.gland(x, 1.0, 12.0, 'down', r=0.3, length=0.8)
    m.box((2.0, 1.4, 14.8), (14.0, 1.8, 15.2), 'metal:copper', 'earth bar')
    for x in (3.0, 6.0, 9.0, 12.0):
        m.bolt(x, 1.8, 15.0, 'up', 0.3)
    m.g('beacon')
    m.cyl((8.0, 15.9, 11.8), 0.9, 0.4, 'y', 'paint:#2a2d30', 'beacon base')
    m.cyl((8.0, 16.3, 11.8), 0.7, 1.0, 'y', 'lamp:#ffb020', 'beacon lens')
    m.cyl((8.0, 17.3, 11.8), 0.75, 0.2, 'y', 'paint:#2a2d30', 'beacon cap')
    m.terminal_12v(15.0, 7.4, 12.6, 'east')
    return m.surface('north', face=7.88, title=title)


def selector(m, f, cid, u, v, label, positions):
    m.ctl(f, 'selector', cid, u, v, w=1.0, h=1.0, col='202020', label=label, pos=positions)


# ------------------------------------------------------------------------------------------------ 1 loco changeover
def loco_changeover():
    m = Model('loco_changeover')
    f = cabinet(m, SIGNAL_GREY, 'LOCO CHANGEOVER', 'TRACTION CHANGE PANEL', stripe='haz')
    m.g('traction mimic')                                         # a printed mimic: train at the buffers, standby loco, wires
    m.box((2.2, 12.1, 7.88), (13.8, 12.25, 7.92), 'paint:#2a2d30', 'mimic track', faces=('north',))
    for x in (2.6, 5.0, 7.4):
        m.box((x, 12.25, 7.88), (x + 2.0, 12.9, 7.92), 'paint:#5a8fd0', 'mimic coach', faces=('north',))
    m.box((9.6, 12.25, 7.88), (11.4, 13.0, 7.92), 'paint:#d84030', 'mimic standby loco', faces=('north',))
    m.box((12.0, 12.25, 7.88), (13.6, 13.0, 7.92), 'paint:#f0c020', 'mimic old loco', faces=('north',))
    m.ctl(f, 'digits', 'd_stage', 8.0, 11.0, w=7.0, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_train', 5.0, 10.0, w=5.0, h=0.7, col='ffb020', label='TRAIN', fmt='%s')
    m.ctl(f, 'digits', 'd_mode', 11.2, 10.0, w=4.8, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_old', 5.0, 9.0, w=5.0, h=0.7, col='ff9040', label='OLD', fmt='%s')
    m.ctl(f, 'digits', 'd_new', 11.2, 9.0, w=4.8, h=0.7, col='7cf29a', label='NEW', fmt='%s')
    m.ctl(f, 'digits', 'd_time', 5.6, 8.0, w=6.0, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_done', 11.8, 8.0, w=3.6, h=0.7, col='ffb020', label='', fmt='%d DONE')
    for i, (cid, col, lab, bl) in enumerate((('l_in', '30e060', 'IN', False), ('l_stby', '30e060', 'STBY', False), ('l_att', 'ffb020', 'ATTACH', True),
                                             ('l_det', 'ffb020', 'DETACH', False), ('l_bt', '30e060', 'BRK OK', False), ('l_btf', 'ff3030', 'BRK FAIL', True), ('l_done', '30e060', 'RIGHT AWAY', False))):
        lamp(m, f, cid, 2.6 + i * 1.8, 6.8, col, lab, blink=bl)
    for i, (cid, col, lab, bl) in enumerate((('l_over', 'ff6020', 'OVER DWELL', True), ('l_none', 'ffd020', 'NO STBY', False), ('l_flt', 'ff2020', 'FAULT', True), ('l_pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.6 + i * 1.8, 5.4, col, lab, 0.55, bl)
    m.ctl(f, 'gauge', 'g_dwell', 12.0, 5.0, w=2.4, h=2.4, col='f2f0e6', label='DWELL %', max=100)
    selector(m, f, 's_on', 2.8, 3.4, 'AUTO', ['OFF', 'AUTO'])
    selector(m, f, 's_mode', 5.0, 3.4, 'TRACTION', ['ANY', 'ELEC', 'DIESEL', 'STEAM'])
    selector(m, f, 's_dwell', 7.2, 3.4, 'MAX DWELL', ['5', '10', '15', '20'])
    selector(m, f, 's_range', 9.4, 3.4, 'RANGE M', ['40', '80', '120'])
    m.ctl(f, 'key', 'k_bypass', 2.8, 1.8, w=0.9, h=0.9, col='c0a040', label='BRAKE TEST', pos=['NORMAL', 'BYPASS'])
    m.ctl(f, 'push', 'p_step', 6.0, 1.8, w=0.9, h=0.9, col='d8b020', label='GIVE UP')
    m.ctl(f, 'push', 'p_reset', 8.4, 1.8, w=0.9, h=0.9, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'test', 13.4, 13.4, w=0.45, h=0.45, col='f0f0f0', label='')
    m.g('shunter phone')                                          # the shunter's lineside phone box on the west side
    m.box((-1.4, 6.0, 9.0), (1.0, 10.4, 12.6), 'paint:#f0c020', 'phone box')
    m.box((-1.5, 7.0, 9.6), (-1.4, 9.6, 12.0), 'label:PHONE', 'phone legend', faces=('west',))
    m.box((-1.8, 8.4, 10.2), (-1.4, 9.0, 11.6), 'paint:#202020', 'handset')
    m.save(item_gui=0.5)
    facing_states('loco_changeover')


# ------------------------------------------------------------------------------------------------ 2 banking board
def banking_board():
    m = Model('banking_board')
    f = cabinet(m, 'paint:#3d5a4a', 'BANKING BOARD', 'BANK ENGINE CONTROL')
    m.g('gradient profile')                                       # the bank profile drawn on the plate
    for i in range(10):
        m.box((2.2 + i * 1.15, 12.1 + i * 0.07, 7.88), (3.35 + i * 1.15, 12.25 + i * 0.07, 7.92), 'paint:#2a2d30', 'gradient profile', faces=('north',))
    m.box((12.9, 12.6, 7.88), (13.3, 13.4, 7.92), 'paint:#d84030', 'summit board', faces=('north',))
    m.ctl(f, 'digits', 'd_stage', 6.6, 11.0, w=7.2, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_count', 12.6, 11.0, w=2.6, h=0.8, col='ffb020', label='', fmt='%d')
    m.ctl(f, 'digits', 'd_train', 5.0, 10.0, w=5.0, h=0.7, col='ffb020', label='TRAIN', fmt='%s')
    m.ctl(f, 'digits', 'd_banker', 11.2, 10.0, w=4.8, h=0.7, col='7cf29a', label='BANKER', fmt='%s')
    m.ctl(f, 'digits', 'd_tonnes', 5.0, 9.0, w=5.0, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_kn', 11.2, 9.0, w=4.8, h=0.7, col='ff6040', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_summit', 5.0, 8.0, w=5.0, h=0.7, col='ffd020', label='SUMMIT', fmt='%s')
    m.ctl(f, 'digits', 'd_foot', 11.2, 8.0, w=4.8, h=0.7, col='ffd020', label='FOOT', fmt='%s')
    for i, (cid, col, lab, bl) in enumerate((('l_wait', 'ffb020', 'WAITING', False), ('l_bank', '30e060', 'BANKER', False), ('l_att', 'ffb020', 'ATTACH', True),
                                             ('l_push', '30e060', 'BANKING', False), ('l_summit', '9ad8ff', 'DROP OFF', False), ('l_ret', '9ad8ff', 'RETURN', False))):
        lamp(m, f, cid, 2.6 + i * 1.8, 6.8, col, lab, blink=bl)
    for i, (cid, col, lab, bl) in enumerate((('l_over', 'ff2020', 'OVERLOAD', True), ('l_none', 'ffd020', 'NO BANKER', False), ('l_flt', 'ff2020', 'FAULT', True),
                                             ('l_sum', '30e060', 'SUMMIT SET', False), ('l_pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.6 + i * 1.8, 5.5, col, lab, 0.55, bl)
    m.ctl(f, 'gauge', 'g_kn', 3.6, 3.4, w=2.2, h=2.2, col='f2f0e6', label='BUFF KN', max=600)
    m.ctl(f, 'gauge', 'g_lead', 6.2, 3.4, w=2.2, h=2.2, col='f2f0e6', label='LEAD %', max=100)
    m.ctl(f, 'gauge', 'g_notch', 8.8, 3.4, w=2.2, h=2.2, col='f2f0e6', label='BANKER %', max=100)
    selector(m, f, 's_on', 11.4, 4.2, 'AUTO', ['OFF', 'AUTO'])
    selector(m, f, 's_mode', 13.4, 4.2, 'MODE', ['COUPLED', 'BUFFER'])
    selector(m, f, 's_share', 11.4, 2.4, 'TE SHARE', ['25', '50', '75'])
    selector(m, f, 's_tonnes', 13.4, 2.4, 'OVER T', ['ALL', '500', '1000', '1500', '2000'])
    selector(m, f, 's_range', 2.6, 1.4, 'RANGE M', ['60', '100', '150'])
    m.ctl(f, 'mushroom', 'p_off', 5.2, 1.4, w=1.0, h=1.0, col='d02020', label='DROP OFF')
    m.ctl(f, 'push', 'p_reset', 7.6, 1.4, w=0.9, h=0.9, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'test', 13.4, 13.4, w=0.45, h=0.45, col='f0f0f0', label='')
    m.g('banker bell + gong')                                     # the bank-engine bell (whistle code repeater)
    m.box((7.2, 15.9, 14.0), (8.8, 16.4, 15.4), 'paint:#2a2d30', 'bell bracket')
    m.cyl((8.0, 16.4, 14.7), 0.9, 0.9, 'y', 'metal:brass', 'bell')
    m.g('gradient post')                                          # the gradient post beside it: 1 in 37
    m.box((-2.4, 0, 12.2), (-1.8, 12.0, 12.8), 'paint:#f0f0ea', 'gradient post')
    m.box((-4.0, 10.0, 12.0), (-1.8, 11.0, 12.2), 'label:1 IN 37', 'gradient arm', faces=('north', 'south'))
    m.box((-1.8, 9.0, 12.0), (0.4, 10.0, 12.2), 'label:LEVEL', 'gradient arm', faces=('north', 'south'))
    m.save(item_gui=0.5)
    facing_states('banking_board')


# ------------------------------------------------------------------------------------------------ 4 coupling point
def coupling_point():
    m = Model('coupling_point')
    f = cabinet(m, SIGNAL_GREY, 'AUTO COUPLING POINT', 'COUPLER AIR PLANT 4 KW')
    m.ctl(f, 'digits', 'd_stage', 6.6, 13.0, w=7.6, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_timer', 12.4, 13.0, w=3.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_wait', 5.0, 12.0, w=5.0, h=0.7, col='ffb020', label='WAITING', fmt='%s')
    m.ctl(f, 'digits', 'd_arr', 11.2, 12.0, w=4.8, h=0.7, col='7cf29a', label='PORTION', fmt='%s')
    m.ctl(f, 'digits', 'd_len', 5.0, 11.0, w=5.0, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_why', 11.2, 11.0, w=4.8, h=0.7, col='ff9040', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_pa', 5.0, 10.0, w=5.0, h=0.7, col='ffd020', label='END A', fmt='%s')
    m.ctl(f, 'digits', 'd_pb', 11.2, 10.0, w=4.8, h=0.7, col='ffd020', label='END B', fmt='%s')
    for i, (ok, bad, lab) in enumerate((('l_cmp', 'l_cmpf', 'COMPAT'), ('l_len', 'l_lenf', 'LENGTH'), ('l_brk', 'l_brkf', 'BRAKE'), ('l_ord', 'l_ordf', 'ORDER'))):
        u = 2.8 + i * 2.6                                         # a check = a green / red lamp pair
        lamp(m, f, ok, u - 0.45, 8.6, '30e060', lab, 0.5)
        lamp(m, f, bad, u + 0.45, 8.6, 'ff3030', '', 0.5)
    for i, (cid, col, lab, bl) in enumerate((('l_wait', 'ffb020', 'WAITING', False), ('l_arr', '30e060', 'PORTION', False), ('l_cpl', 'ffb020', 'COUPLING', True),
                                             ('l_done', '30e060', 'DONE', False), ('l_flt', 'ff2020', 'FAULT', True), ('l_air', '30e060', 'AIR', False),
                                             ('l_sup', '30e060', '400V', False), ('l_pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.4 + i * 1.6, 7.2, col, lab, 0.55, bl)
    m.ctl(f, 'gauge', 'g_air', 12.4, 5.2, w=2.2, h=2.2, col='f2f0e6', label='AIR X10', max=100)
    m.ctl(f, 'digits', 'd_air', 12.4, 3.6, w=3.6, h=0.6, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_joins', 3.2, 5.8, w=2.4, h=0.6, col='ffb020', label='', fmt='%d J')
    m.ctl(f, 'digits', 'd_splits', 5.8, 5.8, w=2.4, h=0.6, col='ffb020', label='', fmt='%d S')
    selector(m, f, 's_on', 2.4, 4.0, 'AUTO', ['OFF', 'AUTO'])
    selector(m, f, 's_rule', 4.4, 4.0, 'RULE', ['JOIN', 'SPLIT'])
    selector(m, f, 's_order', 6.4, 4.0, 'ORDER', ['REAR', 'FRONT'])
    selector(m, f, 's_cars', 8.4, 4.0, 'SPLIT N', ['1', '2', '3', '4'])
    selector(m, f, 's_plat', 2.4, 2.0, 'PLATFORM M', ['60', '120', '180', '240', '320'])
    selector(m, f, 's_wait', 4.4, 2.0, 'WAIT MIN', ['2', '5', '10'])
    selector(m, f, 's_range', 6.4, 2.0, 'RANGE M', ['40', '80', '120'])
    m.ctl(f, 'key', 'k_bypass', 8.4, 2.0, w=0.9, h=0.9, col='c0a040', label='BRAKE TEST', pos=['NORMAL', 'BYPASS'])
    m.ctl(f, 'push', 'p_now', 10.4, 1.8, w=0.9, h=0.9, col='d8b020', label='NOW')
    m.ctl(f, 'push', 'p_reset', 12.4, 1.8, w=0.9, h=0.9, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'test', 13.6, 14.0, w=0.4, h=0.4, col='f0f0f0', label='')
    m.g('air plant')                                              # compressor skid on the west side: motor, pump, receiver
    m.box((-6.0, 0, 8.4), (0.6, 0.8, 15.6), 'concrete', 'compressor plinth')
    m.box((-5.6, 0.8, 8.8), (0.2, 1.2, 15.2), 'paint:#3a3d40', 'skid frame')
    m.cyl((-5.0, 3.0, 12.0), 1.8, 4.6, 'x', 'paint:#2f6fb0', 'air receiver', cap='paint:#2a5a90')
    m.box((-4.6, 1.2, 10.6), (-4.0, 2.0, 13.4), 'paint:#3a3d40', 'receiver saddle')
    m.box((-1.4, 1.2, 10.6), (-0.8, 2.0, 13.4), 'paint:#3a3d40', 'receiver saddle')
    m.cyl((-3.6, 4.8, 12.0), 0.25, 1.0, 'y', 'metal:brass', 'safety valve')
    m.cyl((-2.4, 4.8, 12.0), 0.5, 0.3, 'y', 'gauge', 'receiver gauge')
    m.cyl((-5.2, 1.2, 9.6), 1.0, 2.4, 'y', 'paint:#3d5a4a', 'compressor block')
    for i in range(4):
        m.box((-6.3, 1.6 + i * 0.5, 9.0), (-4.1, 1.8 + i * 0.5, 10.2), 'paint:#2a3f33', 'cylinder fin')
    m.cyl((-2.8, 1.9, 9.6), 0.8, 2.6, 'x', 'paint:#2f4a3a', 'motor 400 V', cap='paint:#2a3f33')
    m.box((-3.0, 2.8, 9.0), (-1.4, 3.6, 10.2), 'paint:#2a3f33', 'motor terminal box')
    m.box((-4.2, 1.4, 8.6), (-3.0, 2.4, 8.8), 'mesh', 'belt guard', faces=('north',))
    m.cyl((-0.2, 3.0, 12.0), 0.2, 1.2, 'x', 'metal:copper', 'air main to the platform')
    m.cyl((1.0, 0.8, 9.0), 0.25, 2.2, 'y', 'metal:copper', 'air main down')
    m.box((-1.0, 5.6, 8.6), (0.6, 7.6, 9.6), 'paint:#2a2d30', 'isolator 400 V')
    m.box((-1.05, 6.0, 8.8), (-1.0, 7.2, 9.4), 'danger:', 'danger plate', faces=('west',))
    m.save(item_gui=0.45)
    facing_states('coupling_point')


# ------------------------------------------------------------------------------------------------ 5 turnback panel
def turnback_panel():
    m = Model('turnback_panel')
    f = cabinet(m, 'paint:#4a4f8a', 'AUTO TURNBACK', 'TERMINUS TURNBACK SET')
    m.g('platform clock')                                         # a slave clock on the roof for the crew
    m.box((5.4, 15.9, 11.6), (10.6, 18.4, 12.4), 'paint:#2a2d30', 'clock case')
    m.box((5.8, 16.2, 11.5), (10.2, 18.1, 11.6), 'gauge', 'clock face', faces=('north',))
    m.box((7.8, 15.9, 11.8), (8.2, 16.0, 12.2), 'metal:steel', 'clock stem')
    m.ctl(f, 'digits', 'd_stage', 6.6, 12.4, w=7.4, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_count', 12.6, 12.4, w=2.8, h=0.8, col='ffb020', label='', fmt='%d')
    m.ctl(f, 'digits', 'd_train', 5.0, 11.4, w=5.0, h=0.7, col='ffb020', label='TRAIN', fmt='%s')
    m.ctl(f, 'digits', 'd_timer', 11.2, 11.4, w=4.8, h=0.7, col='ffd020', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_turn', 5.0, 10.4, w=5.0, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'd_cab', 11.2, 10.4, w=4.8, h=0.7, col='7cf29a', label='CAB', fmt='%s')
    m.ctl(f, 'digits', 'd_route', 5.0, 9.4, w=5.0, h=0.7, col='7cf29a', label='ROUTE', fmt='%s')
    m.ctl(f, 'digits', 'd_late', 11.2, 9.4, w=4.8, h=0.7, col='ff6040', label='', fmt='%d LATE')
    for i, (cid, col, lab, bl) in enumerate((('l_arr', '30e060', 'ARRIVED', False), ('l_walk', 'ffb020', 'WALKING', True), ('l_cab', '30e060', 'CAB', False),
                                             ('l_pp', 'ffd020', 'PROPEL', False), ('l_rte', '30e060', 'ROUTE', False), ('l_rtef', 'ff3030', 'NO ROUTE', True),
                                             ('l_brk', '30e060', 'BRAKE', False))):
        lamp(m, f, cid, 2.6 + i * 1.8, 8.0, col, lab, blink=bl)
    for i, (cid, col, lab, bl) in enumerate((('l_brkf', 'ff3030', 'BRK FAIL', True), ('l_go', '30e060', 'RIGHT AWAY', False), ('l_late', 'ff8020', 'LATE', False),
                                             ('l_flt', 'ff2020', 'FAULT', True), ('l_pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.6 + i * 1.8, 6.6, col, lab, 0.55, bl)
    m.ctl(f, 'gauge', 'g_turn', 12.2, 5.6, w=2.4, h=2.4, col='f2f0e6', label='PLAN %', max=100)
    selector(m, f, 's_on', 2.8, 4.2, 'AUTO', ['OFF', 'AUTO'])
    selector(m, f, 's_mode', 5.0, 4.2, 'MODE', ['PUSH-PULL', 'CAB ONLY'])
    selector(m, f, 's_plan', 7.2, 4.2, 'PLAN MIN', ['2', '3', '5', '8'])
    selector(m, f, 's_range', 9.4, 4.2, 'RANGE M', ['40', '80', '120'])
    m.ctl(f, 'key', 'k_bypass', 2.8, 2.0, w=0.9, h=0.9, col='c0a040', label='BRAKE TEST', pos=['NORMAL', 'BYPASS'])
    m.ctl(f, 'push', 'p_now', 6.0, 2.0, w=0.9, h=0.9, col='d8b020', label='SKIP WALK')
    m.ctl(f, 'push', 'p_reset', 8.4, 2.0, w=0.9, h=0.9, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'test', 13.4, 13.6, w=0.45, h=0.45, col='f0f0f0', label='')
    m.g('right-away indicator')                                   # the RA indicator box on a bracket for the driver
    m.box((15.6, 9.0, 9.6), (16.2, 9.6, 14.4), 'paint:#2a2d30', 'RA bracket')
    m.box((16.2, 8.0, 10.4), (18.4, 11.6, 13.6), 'paint:#2a2d30', 'RA indicator')
    m.box((16.15, 8.6, 11.0), (16.2, 11.0, 13.0), 'label:RA', 'RA lens', faces=('west',))
    m.box((18.4, 8.6, 11.0), (18.45, 11.0, 13.0), 'label:RA', 'RA lens', faces=('east',))
    m.save(item_gui=0.5)
    facing_states('turnback_panel')


def recipes():
    rdir = os.path.join(ASSETS, 'recipes')

    def r(name, pattern, key):
        k = {c: ({'item': it[0], 'data': it[1]} if isinstance(it, tuple) else {'item': it}) for c, it in key.items()}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': k, 'result': {'item': 'irextras:' + name, 'count': 1}},
                  open(os.path.join(rdir, name + '.json'), 'w'), indent=1)

    I, RS, C = 'minecraft:iron_ingot', 'minecraft:redstone', 'minecraft:comparator'
    r('loco_changeover', ['IGI', 'CRC', 'ILI'], {'I': I, 'G': 'minecraft:glass_pane', 'C': C, 'R': RS, 'L': 'minecraft:lever'})
    r('banking_board', ['IGI', 'CBC', 'ILI'], {'I': I, 'G': 'minecraft:glass_pane', 'C': C, 'B': 'minecraft:noteblock', 'L': 'minecraft:lever'})
    r('coupling_point', ['IGI', 'CPC', 'IRI'], {'I': I, 'G': 'minecraft:glass_pane', 'C': C, 'P': 'minecraft:piston', 'R': RS})
    r('turnback_panel', ['IGI', 'CKC', 'IRI'], {'I': I, 'G': 'minecraft:glass_pane', 'C': C, 'K': 'minecraft:clock', 'R': RS})


if __name__ == '__main__':
    for fn in (loco_changeover, banking_board, coupling_point, turnback_panel):
        fn()
    recipes()
    print('station operations gear written')
