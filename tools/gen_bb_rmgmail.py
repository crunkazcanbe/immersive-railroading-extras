#!/usr/bin/env python3
"""Batch 5 #1 + #8: the Yard Gantry Crane's operator cab desk (yard_gantry) and the Sorting Office (sorting_office).
Wide desks (3 blocks), models face north; controls on north surfaces sit at u = 16 - x, on 'up' surfaces v = z.
Live controls: freight/TileYardGantry.java and mail/TileSortingOffice.java (ids must match; the gantry itself, its
trolley, ropes, spreader and the stack are drawn live by client/render/YardGantryRenderer, the letters in the
pigeonholes and the bags on the rails by mail/client/MailFrameRenderer). Both have a 12 V terminal AND their real
supply: the crane an HV cable box (480 V / 4.16 kV / 13.8 kV) into a drive cabinet with opening doors (cabkit), the
office a 230 V class mains isolator. Writes models, controls, blockstates, item models, recipes, mail item textures."""
import json, os
from PIL import Image
import cabkit
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing
from gen_bb_economy import back_wall, desk, strip, cupboard_shelf, monitor, X

JOY = ['<<', '<', '0', '>', '>>']
BAYS = ['BAY 1', 'BAY 2', 'BAY 3', 'BAY 4']
LANES = ['TRUCK', 'RAIL', 'ROW 1', 'ROW 2', 'ROW 3', 'ROW 4']


def joystick(m, sf, cid, cx, cz, top, label):
    """a master-switch joystick pod: armrest pod, gaiter, lever, grip with deadman trigger + thumb button, the live
    notch selector (j_*) on the pod's face and its engraved label"""
    m.g('joystick ' + label.lower())
    m.box((cx - 1.6, top, cz - 1.6), (cx + 1.6, top + 0.8, cz + 1.6), 'paint:#2a2f34', 'joystick pod')
    m.box((cx - 1.65, top + 0.8, cz - 1.65), (cx + 1.65, top + 0.95, cz + 1.65), 'paint:#1a1d20', 'pod top plate')
    m.box((cx - 1.4, top + 0.2, cz - 1.62), (cx + 1.4, top + 0.7, cz - 1.6), 'label:' + label, 'pod label', faces=('north',))
    m.cyl((cx, top + 0.95, cz + 0.5), 0.55, 0.35, 'y', 'paint:#111111', 'rubber gaiter')
    m.box((cx - 0.12, top + 1.3, cz + 0.38), (cx + 0.12, top + 2.6, cz + 0.62), 'metal:steel', 'lever')
    m.cyl((cx, top + 2.6, cz + 0.5), 0.42, 1.1, 'y', 'paint:#1a1a1a', 'grip')
    m.box((cx - 0.15, top + 3.0, cz + 0.05), (cx + 0.15, top + 3.4, cz + 0.12), 'paint:#d02020', 'deadman trigger')
    m.box((cx - 0.18, top + 3.7, cz + 0.32), (cx + 0.18, top + 3.8, cz + 0.68), 'paint:#f0d020', 'thumb button')
    m.ctl(sf, 'selector', cid, X(cx), cz - 2.15, w=1.0, h=1.0, col='202020', label=label, pos=JOY)
    m.ctl(sf, 'label', '', X(cx), cz - 1.5, w=0, h=0, col='e8e8e8', label=label)


# =========================================================================================== yard gantry cab desk (#1)
def yard_gantry():
    m = Model('yard_gantry')
    # ---- the cab: chequer floor, the big window over the yard, mullions, wipers, sun blinds
    m.g('cab floor')
    m.box((-14, 0, 0.6), (30, 0.4, 15.6), 'paint:#4a4f54', 'chequer plate floor')
    for x in range(-13, 30, 2):
        m.box((x, 0.4, 1.0), (x + 0.2, 0.45, 15.2), 'paint:#5a6066', 'floor rib')
    m.g('cab window')
    m.box((-14, 0.4, 15.2), (30, 9.0, 16), 'paint:#d8d8d2', 'cab wall below the glass')
    m.box((-14, 9.0, 15.4), (30, 29.0, 15.6), 'glass', 'window over the yard')
    m.box((-14, 29.0, 15.0), (30, 32.0, 16), 'paint:#d8d8d2', 'cab roof edge')
    for x in (-14, -3.2, 8.0, 19.2, 29.4):
        m.box((x, 9.0, 15.0), (x + 0.6, 29.0, 16.0), 'paint:#2a2e33', 'window mullion')
    m.box((-14, 8.8, 14.8), (30, 9.2, 16.0), 'paint:#2a2e33', 'window sill')
    for x in (-8.6, 2.4, 13.6, 24.6):
        m.box((x, 10.0, 15.2), (x + 0.25, 16.0, 15.35), 'paint:#111111', 'wiper arm')
        m.box((x - 0.2, 9.8, 15.1), (x + 0.45, 10.2, 15.4), 'metal:steel', 'wiper motor cap')
    for x in (-13.4, -2.6, 8.6, 19.8):
        m.box((x, 26.0, 14.6), (x + 10.4, 28.8, 15.0), 'paint:#3a3e44', 'roller blind')
    m.box((-6.0, 29.4, 14.9), (22.0, 31.4, 15.0), 'paint:#10304a', 'cab sign', north='plate:RMG GANTRY CRANE - CAB')
    # ---- left wing: the yard planner's desk (jobs, gate) and the operator's locker
    m.g('locker')
    m.box((-13.6, 0.4, 9.6), (-8.4, 18.0, 15.0), 'ral7035', 'operator locker')
    m.box((-13.4, 0.8, 9.4), (-8.6, 17.6, 9.6), 'paint:#b9bcb8', 'locker door')
    for y in range(2, 17, 1):
        m.box((-12.8, y, 9.35), (-11.2, y + 0.2, 9.4), 'paint:#8a8c88', 'locker vent')
    m.box((-9.4, 8.0, 9.1), (-9.1, 9.8, 9.4), 'metal:alu', 'locker handle')
    lk = m.surface('north', face=9.4, title='LOCKER')
    m.ctl(lk, 'zone', 'locker', X(-11.0), 9.2, w=4.8, h=16.8, col='000000', label='')
    for (x, c) in ((-13.2, '#f0c020'), (-11.3, '#f0f0f0')):
        m.box((x, 18.0, 11.0), (x + 1.6, 18.9, 12.6), 'paint:' + c, 'hard hat')
    m.g('planner desk')
    m.box((-8.0, 0.4, 3.6), (-0.4, 8.0, 12.0), 'paint:#30363c', 'planner desk')
    m.box((-8.2, 8.0, 3.4), (-0.2, 8.4, 12.2), 'paint:#22272c', 'planner desk top')
    m.box((-7.8, 1.0, 3.5), (-0.8, 7.4, 3.6), 'louvre', 'planner desk vents')
    pd = m.surface('up', face=16 - 8.4, title='JOBS + GATE')
    for i, (cid, lab, pos) in enumerate((('s_fb', 'FROM BAY', BAYS), ('s_fl', 'FROM', LANES), ('s_tb', 'TO BAY', BAYS), ('s_tl', 'TO', LANES))):
        u = X(-7.2 + i * 1.7)
        m.ctl(pd, 'selector', cid, u, 4.8, w=1.0, h=1.0, col='202020', label=lab, pos=pos)
        m.ctl(pd, 'label', '', u, 4.0, w=0, h=0, col='e8e8e8', label=lab)
    pushes(m, pd, [('addjob', '2050d0', 'ADD JOB'), ('plan', 'f0d020', 'PLAN'), ('tracker', '20a0a0', 'TRACKER'), ('clear', 'd02020', 'CLEAR'), ('print', 'b0b0b0', 'PRINT')],
           X(-7.4), 6.8, step=-1.45, size=0.8, lab_dv=0.7)
    m.ctl(pd, 'selector', 's_gate', X(-7.2), 9.4, w=1.0, h=1.0, col='202020', label='GATE BAY', pos=BAYS)
    m.ctl(pd, 'label', '', X(-7.2), 8.6, w=0, h=0, col='e8e8e8', label='GATE BAY')
    pushes(m, pd, [('gatein', '1fa34a', 'GATE IN'), ('gateout', 'e08020', 'GATE OUT'), ('tkin', '30a0f0', 'TRUCK IN'), ('tkout', '808080', 'TRUCK OUT')],
           X(-5.4), 9.4, step=-1.45, size=0.8, lab_dv=0.7)
    m.ctl(pd, 'digits', 'from', X(-4.2), 11.2, w=7.2, h=0.8, col='8ad4ff', label='', fmt='%s')
    m.box((-8.0, 8.4, 10.6), (-0.6, 8.5, 11.8), 'paint:#081018', 'job readout window')
    m.box((-7.8, 8.4, 7.6), (-6.2, 9.0, 8.4), 'paint:#d8d8d4', 'ticket printer')
    m.box((-7.6, 9.0, 7.8), (-6.4, 9.05, 8.2), 'paint:#f4f2ea', 'printer slot')
    # ---- the main console: three joystick pods, buttons, keys, E-stop
    m.g('console')
    m.box((0, 0.4, 4.0), (16, 8.2, 12.6), 'paint:#30363c', 'console body')
    m.box((-0.2, 8.2, 3.2), (16.2, 8.8, 13.0), 'paint:#22272c', 'console top')
    m.box((0.4, 1.0, 3.9), (15.6, 7.6, 4.0), 'louvre', 'console vents')
    m.box((6.0, 0.4, 3.0), (10.0, 1.2, 4.0), 'paint:#22272c', 'foot rest')
    m.box((6.4, 1.2, 3.1), (9.6, 1.3, 3.9), 'paint:#111111', 'foot rest rubber')
    for x in (1.0, 15.0):
        m.box((x - 0.5, 0.4, 3.0), (x + 0.5, 2.2, 4.0), 'paint:#22272c', 'pedal housing')
        m.box((x - 0.4, 2.2, 3.2), (x + 0.4, 2.4, 3.8), 'paint:#111111', 'foot pedal (horn / deadman)')
    sf = m.surface('up', face=16 - 8.95, title='CAB DESK')
    m.box((0.6, 8.8, 3.6), (15.4, 8.95, 9.0), 'paint:#1a1e22', 'control plate')
    joystick(m, sf, 'j_g', 13.0, 7.4, 8.8, 'GANTRY')
    joystick(m, sf, 'j_t', 8.0, 7.4, 8.8, 'TROLLEY')
    joystick(m, sf, 'j_h', 3.0, 7.4, 8.8, 'HOIST')
    pushes(m, sf, [('lock', '1fa34a', 'LOCK'), ('unlock', 'd02020', 'UNLOCK'), ('pick', '2050d0', 'PICK'), ('drop', '2050d0', 'DROP'), ('go', 'f0f0f0', 'GO'),
                   ('stop', 'e08020', 'STOP'), ('bayl', 'b0b0b0', 'BAY <'), ('bayr', 'b0b0b0', 'BAY >'), ('horn', 'f0d020', 'HORN'), ('ack', 'ffb020', 'ACK'), ('cam', '30a0f0', 'CAM')],
           1.0, 3.95, step=1.3, size=0.65, lab_dv=-0.5)
    m.ctl(sf, 'mushroom', 'm_estop', X(15.0), 7.2, w=1.0, h=1.0, col='d02020', label='E-STOP')
    m.box((14.4, 8.8, 6.6), (15.6, 8.85, 7.8), 'paint:#f0c018', 'E-stop yellow plate')
    for (cid, lab, pos, x, v) in (('k_power', 'CRANE', ['OFF', 'ON'], 5.5, 6.4), ('k_auto', 'AUTO', ['MAN', 'AUTO'], 5.5, 8.2), ('k_sway', 'ANTI-SWAY', ['OFF', 'ON'], 10.5, 6.4),
                                  ('k_deliv', 'DELIVERY', ['OFF', 'ON'], 10.5, 8.2), ('k_ovr', 'OVERRIDE', ['NORM', 'OVR'], 0.95, 7.2)):
        m.ctl(sf, 'key', cid, X(x), v, w=0.8, h=0.8, col='c0a040' if cid != 'k_ovr' else 'd02020', label=lab, pos=pos)
        m.ctl(sf, 'label', '', X(x), v - 0.7, w=0, h=0, col='e8e8e8', label=lab)
    # ---- instrument hood: lamps, bay lamps, four meters
    m.g('instrument hood')
    m.box((0.6, 8.8, 9.6), (15.4, 12.4, 11.2), 'paint:#1b2025', 'instrument hood')
    m.box((0.5, 12.4, 9.5), (15.5, 12.6, 11.3), 'metal:steel', 'hood cap')
    hd = m.surface('north', face=9.58, title='HOOD')
    items = [('pw', GREEN, '12V'), ('sup', GREEN, 'SUPPLY'), ('hvl', AMBER, 'HV'), ('on', GREEN, 'RUN'), ('ready', GREEN, 'READY'), ('lockl', GREEN, 'LOCKED'),
             ('unlk', RED, 'UNLOCK'), ('landed', AMBER, 'LANDED'), ('movel', AMBER, 'MOVING', True), ('autol', BLUE, 'AUTO')]
    lamps(m, hd, items, 1.0, 11.75, step=1.2, size=0.5, lab_dv=0.5)
    items2 = [('windw', AMBER, 'WIND'), ('storm', RED, 'STORM', True), ('swayl', AMBER, 'SWAY'), ('ovl', RED, 'OVLOAD', True), ('estopl', RED, 'E-STOP', True),
              ('fault', RED, 'FAULT', True), ('carl', BLUE, 'CAR'), ('truckl', BLUE, 'TRUCK')]
    lamps(m, hd, items2, 1.0, 10.55, step=1.2, size=0.5, lab_dv=0.5)
    for k in range(4):
        m.ctl(hd, 'lamp', 'bl%d' % k, 13.0 + (k % 2) * 1.2, 11.75 if k < 2 else 10.55, w=0.5, h=0.5, col=WHITE, label='B%d' % (k + 1))
        m.ctl(hd, 'label', '', 13.0 + (k % 2) * 1.2, (11.75 if k < 2 else 10.55) - 0.5, w=0, h=0, col='e8e8e8', label='B%d' % (k + 1))
    m.ctl(hd, 'digits', 'lifts', 13.6, 9.4, w=2.4, h=0.55, col='7cf29a', label='', fmt='%s')
    # meters on a raised meter bridge above the hood
    m.g('meter bridge')
    m.box((0.6, 12.6, 10.2), (15.4, 15.6, 11.4), 'paint:#1b2025', 'meter bridge')
    mb = m.surface('north', face=10.18, title='METERS')
    for i, (cid, lab, mx) in enumerate((('g_load', 'LOAD T', 60), ('g_wind', 'WIND M/S', 40), ('g_kw', 'KW', 900), ('g_reel', 'REEL %', 100))):
        u = 2.2 + i * 3.8
        m.box((X(u) - 1.35, 14.1 - 1.35, 10.06), (X(u) + 1.35, 14.1 + 1.35, 10.2), 'metal:steel', 'meter bezel')
        m.ctl(mb, 'gauge', cid, u, 14.1, w=2.4, h=2.4, col='f2f0e6', label=lab, max=mx)
        m.ctl(mb, 'label', '', u, 12.85, w=0, h=0, col='e8e8e8', label=lab)
    # ---- the screens: crane status, load / supply, the job list, the stack map, camera / gate / fees
    monitor(m, 0.6, 7.8, 15.8, 21.0, 11.2, ['stat', 'pos', 'load', 'wind'], cols=['ffd040', '7cf29a', '7cf29a', '8ad4ff'], stand_y=15.6)
    monitor(m, 8.2, 15.4, 15.8, 21.0, 11.2, ['sway', 'supl', 'box', 'dst'], cols=['7cf29a', 'ffb020', 'ffd040', '8ad4ff'], stand_y=15.6)
    monitor(m, 0.6, 7.8, 21.4, 26.4, 11.6, ['j1', 'j2', 'j3', 'j4'], cols=['f0f0f0', 'b0b0b0', 'b0b0b0', 'b0b0b0'], stand_y=21.0)
    monitor(m, 8.2, 15.4, 21.4, 26.4, 11.6, ['m1', 'm2', 'm3', 'm4'], cols=['7cf29a', '7cf29a', '7cf29a', '7cf29a'], stand_y=21.0)
    m.g('camera monitor')
    m.box((-7.8, 12.0, 12.4), (-0.8, 16.8, 13.2), 'paint:#1c1e20', 'CCTV monitor bezel')
    m.box((-7.4, 12.4, 12.34), (-1.2, 16.4, 12.4), 'screen', 'CCTV monitor screen')
    cm = m.surface('north', face=12.3, title='CCTV')
    m.ctl(cm, 'digits', 'camt', X(-4.3), 15.9, w=5.6, h=0.6, col='5bcefa', label='', fmt='%s')
    m.ctl(cm, 'digits', 'gate', X(-4.3), 14.4, w=5.6, h=0.6, col='ffd040', label='', fmt='%s')
    m.ctl(cm, 'digits', 'fees', X(-4.3), 13.2, w=5.6, h=0.6, col='7cf29a', label='', fmt='%s')
    m.box((-4.6, 8.4, 12.6), (-4.0, 12.0, 13.0), 'paint:#2a2c2e', 'monitor arm')
    # radio, phone, rain gauge display
    m.g('radio + phone')
    m.box((15.0, 8.8, 11.6), (15.8, 10.4, 12.4), 'paint:#1a1a1a', 'yard radio')
    m.box((15.3, 10.4, 11.9), (15.45, 12.6, 12.05), 'paint:#1a1a1a', 'radio aerial')
    m.box((0.6, 8.8, 11.4), (2.2, 9.6, 12.6), 'paint:#2a2c2e', 'cab phone')
    m.box((0.7, 9.6, 11.6), (2.1, 9.9, 12.4), 'paint:#111111', 'handset')
    # ---- right wing: the drive cabinet (opening doors, cabkit) fed by the HV cable box; spares cupboard + shelf
    m.g('drive cabinet')
    m.box((16.6, 0.4, 9.0), (23.0, 26.0, 15.2), 'paint:#3a4048', 'drive cabinet')
    m.box((16.5, 26.0, 8.9), (23.1, 26.6, 15.3), 'paint:#2a2e33', 'cabinet roof')
    m.louvres(17.2, 22.4, 22.0, 25.4, 9.0, n=4)
    m.box((17.4, 1.0, 8.9), (22.2, 3.6, 9.0), 'plate', 'drive plate', north='plate:VFD HOIST|GANTRY TROLLEY')
    m.g('HV feed')
    m.box((24.0, 0.4, 13.0), (29.6, 5.0, 15.2), 'ral7016', 'HV cable box', north='danger:DANGER|HV')
    for bx in (24.4, 29.2):
        for by in (0.8, 4.6):
            m.bolt(bx, by, 13.0, 'north', 0.4)
    for i in range(3):
        m.cyl((25.4 + i * 1.6, -0.4, 14.1), 0.55, 0.8, 'y', 'metal:brass', 'cable gland')
        m.cyl((25.4 + i * 1.6, -1.5, 14.1), 0.45, 1.1, 'y', 'cable:e06a10', 'HV cable (trailing cable reel)')
    m.box((23.0, 3.0, 13.6), (24.0, 3.8, 14.4), 'cable:e06a10', 'feed to the drives')
    m.g('spares cupboard + shelf')
    m.box((23.6, 5.0, 11.4), (29.8, 9.0, 15.2), 'ral7035', 'spares cupboard')
    m.box((23.8, 5.4, 11.25), (29.6, 8.6, 11.4), 'paint:#b9bcb8', 'cupboard door')
    m.box((28.6, 6.6, 11.0), (28.9, 7.6, 11.25), 'metal:alu', 'cupboard handle')
    m.box((24.0, 7.6, 11.2), (27.0, 8.4, 11.25), 'label:SPREADER SPARES', 'spares label', faces=('north',))
    sp = m.surface('north', face=11.2, title='SPARES')
    m.ctl(sp, 'zone', 'spares', X(26.7), 7.0, w=5.8, h=3.2, col='000000', label='')
    m.box((23.4, 9.0, 11.2), (30.0, 9.4, 15.4), 'paint:#3e2716', 'shelf top')
    m.box((23.6, 9.4, 15.0), (29.8, 12.0, 15.4), 'paint:#3e2716', 'shelf back board')
    sh = m.surface('up', face=16 - 9.4, title='SHELF')
    for k, x in enumerate((24.4, 26.4, 28.4)):
        m.ctl(sh, 'zone', 'sh%d' % (k + 1), X(x), 13.4, w=1.9, h=3.8, col='000000', label='')
    m.box((24.2, 9.4, 12.0), (25.4, 10.4, 13.4), 'metal:steel', 'spare twistlock')
    m.terminal_12v(16.2, 1.0, 8.0, 'east')
    cabkit.fit(m, 17.2, 22.4, 5.0, 21.0, 9.0, depth=3.4, col='paint:#3a4048', title='DRIVES|RMG', hv=True, volts='13.8KV 4.16KV 480V',
               fns=(('ctl', '12V CTL'), ('main', 'DRIVES'), ('scr', 'SCREENS')))
    m.save(item_gui=0.26)
    return m


# =============================================================================================== sorting office (#8)
HX0, HY0, HW, HH, HZ = -13.6, 9.0, 2.4, 3.6, 11.6     # mail/TileSortingOffice.java


def sorting_office():
    m = Model('sorting_office')
    back_wall(m, panel='#5a1a1a', plaster='#efe6d2', top=30)
    m.g('fascia')
    m.box((-14, 27.4, 14.6), (30, 30.0, 15.4), 'paint:#c02030', 'fascia board')
    m.box((-4, 27.8, 14.5), (20, 29.6, 14.6), 'plate', 'fascia lettering', north='label:PRIDE POST - SORTING OFFICE')
    # ---- the pigeonhole sorting frame: 5 x 5 holes, live town labels, the letters drawn by MailFrameRenderer
    m.g('sorting frame')
    m.box((HX0 - 0.4, HY0 - 0.4, HZ), (HX0 + 5 * HW + 0.4, HY0 + 5 * HH + 0.6, 15.4), 'wood', 'frame carcass back')
    m.box((HX0 - 0.4, HY0 - 0.4, HZ), (HX0, HY0 + 5 * HH + 0.6, 15.4), 'paint:#5a3a24', 'frame side')
    m.box((HX0 + 5 * HW, HY0 - 0.4, HZ), (HX0 + 5 * HW + 0.4, HY0 + 5 * HH + 0.6, 15.4), 'paint:#5a3a24', 'frame side')
    m.box((HX0 - 0.6, HY0 + 5 * HH, HZ - 0.2), (HX0 + 5 * HW + 0.6, HY0 + 5 * HH + 0.8, 15.4), 'paint:#3e2716', 'frame cornice')
    lab = m.surface('north', face=HZ - 0.12, title='PIGEONHOLES')
    for r in range(5):
        y0 = HY0 + r * HH
        m.box((HX0, y0, HZ), (HX0 + 5 * HW, y0 + 0.7, 15.0), 'paint:#6b4630', 'pigeonhole shelf')
        for c in range(5):
            x0 = HX0 + c * HW
            m.box((x0 + 0.08, y0 + 0.7, 15.0), (x0 + HW - 0.08, y0 + HH, 15.2), 'paint:#2a1a10', 'pigeonhole back')
            m.box((x0 + 0.2, y0 + 0.12, HZ - 0.1), (x0 + HW - 0.2, y0 + 0.6, HZ), 'paint:#f4f2ea', 'label card')
            i = (4 - r) * 5 + c
            m.ctl(lab, 'digits', 'h%d' % i, X(x0 + HW / 2), y0 + 0.36, w=HW - 0.45, h=0.44, col='202020', label='', fmt='%s')
        for c in range(1, 5):
            m.box((HX0 + c * HW - 0.08, y0 + 0.7, HZ), (HX0 + c * HW + 0.08, y0 + HH, 15.0), 'paint:#6b4630', 'divider')
    # under the frame: the posting box and the pickup shelves
    m.g('posting + pickup cabinet')
    m.box((HX0 - 0.4, 0, 11.4), (HX0 + 5 * HW + 0.4, 8.6, 15.4), 'paint:#5a3a24', 'cabinet')
    pc = m.surface('north', face=11.25, title='POST')
    m.box((-13.6, 0.6, 11.25), (-8.2, 8.0, 11.4), 'paint:#c02030', 'posting box door')
    m.box((-12.4, 6.0, 11.1), (-9.4, 6.5, 11.25), 'paint:#111111', 'letter slot')
    m.box((-12.6, 6.5, 11.05), (-9.2, 6.8, 11.25), 'metal:brass', 'slot flap')
    m.box((-12.8, 3.6, 11.2), (-9.0, 4.8, 11.25), 'label:POSTING BOX', 'posting box label', faces=('north',))
    m.box((-12.4, 1.4, 11.15), (-9.4, 2.6, 11.25), 'plate', 'collection plate', north='plate:NEXT COLLECTION|BY TRAIN')
    m.ctl(pc, 'zone', 'post', X(-10.9), 4.3, w=5.2, h=7.2, col='000000', label='')
    m.box((-7.8, 0.6, 11.25), (-2.0, 8.0, 11.4), 'paint:#7a5238', 'pickup door')
    m.box((-3.0, 4.0, 11.0), (-2.7, 5.2, 11.25), 'metal:brass', 'pickup knob')
    m.box((-7.2, 6.4, 11.2), (-3.4, 7.4, 11.25), 'label:PICKUP', 'pickup label', faces=('north',))
    m.ctl(pc, 'zone', 'pick', X(-4.9), 4.3, w=5.6, h=7.2, col='000000', label='')
    # ---- the counter desk
    desk(m, {('l', 1): 'dr', ('r', 1): 'ptray'}, top=9.0, body='#5a3a24', front='#7a5238', pull='metal:brass')
    sf = strip(m, 4.0, 8.4, top=9.0)
    pushes(m, sf, [('frank', 'c02030', 'FRANK'), ('sort', '2050d0', 'SORT'), ('bag', 'b08850', 'BAG'), ('open', '30a040', 'OPEN BAGS'),
                   ('collect', 'f0d020', 'COLLECT'), ('register', '20a0a0', 'REGISTER')], X(1.2), 7.7, step=-1.4, size=0.8, lab_dv=0.7)
    pushes(m, sf, [('wrap', 'c8a070', 'WRAP'), ('paper', 'f0f0e0', 'PAPER'), ('weigh', 'c0a040', 'WEIGH'), ('print', '808080', 'PRINT'), ('bell', 'd0b040', 'BELL')],
           X(1.2), 5.4, step=-1.4, size=0.8, lab_dv=0.7)
    m.ctl(sf, 'key', 'k_open', X(9.0), 5.5, w=0.9, h=0.9, col='c0a040', label='COUNTER', pos=['CLOSED', 'OPEN'])
    m.ctl(sf, 'label', '', X(9.0), 4.7, w=0, h=0, col='e8e8e8', label='COUNTER')
    m.ctl(sf, 'key', 'k_auto', X(10.6), 5.5, w=0.9, h=0.9, col='c0a040', label='AUTO', pos=['HAND', 'AUTO'])
    m.ctl(sf, 'label', '', X(10.6), 4.7, w=0, h=0, col='e8e8e8', label='AUTO')
    m.ctl(sf, 'gauge', 'fill', X(13.6), 6.4, w=2.4, h=2.4, col='f2f0e6', label='FRAME %', max=100)
    m.ctl(sf, 'digits', 'fee', 2.4, 4.5, w=2.6, h=0.55, col='7cf29a', label='', fmt='%s')
    # franking machine, scale, stamp rack, letter tray (the tray's letters are drawn live)
    m.g('franking machine')
    m.box((11.6, 9.0, 9.4), (15.4, 10.8, 12.0), 'paint:#2a2c2e', 'franking machine')
    m.box((11.8, 10.8, 9.6), (15.2, 11.2, 11.8), 'paint:#3a3c3e', 'machine lid')
    m.box((11.5, 9.4, 9.9), (11.6, 9.8, 11.5), 'paint:#111111', 'feed slot')
    m.box((12.2, 11.2, 9.8), (13.8, 11.3, 10.6), 'paint:#03140a', 'meter window')
    m.box((14.2, 11.2, 9.8), (14.8, 11.4, 10.4), 'paint:#c02030', 'ink cartridge')
    m.g('letter scale')
    m.box((8.4, 9.0, 9.6), (11.0, 9.6, 12.0), 'paint:#d8d8d4', 'scale base')
    m.box((8.6, 9.6, 9.8), (10.8, 9.8, 11.8), 'metal:steel', 'scale pan')
    m.box((8.6, 9.0, 9.45), (10.8, 9.5, 9.6), 'paint:#03140a', 'scale display')
    m.g('letter tray')
    m.box((1.0, 9.0, 9.6), (4.4, 9.15, 12.2), 'paint:#3a3e44', 'tray floor')
    for (a, b) in (((1.0, 9.6), (4.4, 9.8)), ((1.0, 12.0), (4.4, 12.2)), ((1.0, 9.6), (1.2, 12.2)), ((4.2, 9.6), (4.4, 12.2))):
        m.box((a[0], 9.0, a[1]), (b[0], 9.9, b[1]), 'paint:#3a3e44', 'tray wall')
    m.box((1.4, 9.9, 9.55), (4.0, 10.4, 9.6), 'label:FRANKED', 'tray label', faces=('north',))
    m.g('stamp rack')
    m.box((5.0, 9.0, 11.0), (7.8, 9.4, 12.4), 'wood', 'stamp rack base')
    for k in range(4):
        m.cyl((5.4 + k * 0.7, 9.4, 11.7), 0.22, 1.0, 'y', 'wood', 'rubber stamp handle')
        m.box((5.2 + k * 0.7, 9.4, 11.5), (5.6 + k * 0.7, 9.5, 11.9), 'paint:#111111', 'stamp face')
    m.box((5.2, 9.0, 9.6), (7.6, 9.2, 10.6), 'paint:#2050a0', 'ink pad')
    # lamp panel + the two screens: counter status and the live bags-in-transit board
    m.g('lamp panel')
    m.box((0.6, 9.0, 12.6), (15.4, 11.6, 13.4), 'paint:#2a2c2e', 'lamp panel')
    lp = m.surface('north', face=12.58, title='OFFICE')
    lamps(m, lp, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('openl', GREEN, 'OPEN'), ('autol', BLUE, 'AUTO'), ('trayl', AMBER, 'TRAY'),
                  ('outl', AMBER, 'BAGS OUT'), ('inl', BLUE, 'BAGS IN'), ('pickl', WHITE, 'PICKUP'), ('robotl', GREEN, 'ROBOTS'), ('tpol', AMBER, 'TPO'),
                  ('noroute', RED, 'NO ROUTE', True)], 1.0, 10.8, step=1.4, size=0.5, lab_dv=0.6)
    monitor(m, 0.6, 7.8, 12.0, 17.6, 13.4, ['sign', 'stn', 'counts', 'inbound', 'last', 'robot'], cols=['ff5070', 'ffd040', '7cf29a', '8ad4ff', 'ffb020', '7cf29a'], stand_y=11.6)
    monitor(m, 8.2, 15.4, 12.0, 17.6, 13.4, ['t1', 't2', 't3', 't4'], cols=['ffd040', 'f0f0f0', 'f0f0f0', 'f0f0f0'], stand_y=11.6)
    m.box((8.6, 17.6, 13.6), (15.0, 18.4, 14.2), 'plate', 'board title', north='plate:BAGS IN TRANSIT')
    # ---- right wing: despatch / arrived cupboards with the shelf on top, and the two bag rails over it
    cupboard_shelf(m, 18.4, 29.4, ['desp', 'arr'], 4, title='COUNTER SHELF', body='#5a3a24', door='#7a5238')
    m.g('bag rails')
    for (y, txt) in ((26.0, 'DESPATCH'), (18.4, 'ARRIVED')):
        m.box((18.4, y, 13.1), (29.6, y + 0.3, 13.5), 'metal:steel', 'bag rail')
        for x in (18.6, 29.2):
            m.box((x, y - 0.2, 13.5), (x + 0.3, y + 0.5, 15.4), 'metal:steel', 'rail bracket')
        for k in range(6):
            m.box((19.3 + k * 1.75, y - 0.9, 13.2), (19.5 + k * 1.75, y, 13.4), 'metal:steel', 'bag hook')
        m.box((21.0, y + 0.4, 15.1), (27.0, y + 1.4, 15.3), 'label:' + txt, 'rail label', faces=('north',))
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    m.save(item_gui=0.28)
    return m


def mail_textures():
    """16 px item textures for the mail item's four kinds"""
    d = os.path.join(ASSETS, 'textures/items')
    def img(fill):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        px = im.load()
        fill(px)
        return im
    def rect(px, x0, y0, x1, y1, c):
        for x in range(x0, x1):
            for y in range(y0, y1): px[x, y] = c
    def paper(px):
        rect(px, 3, 1, 13, 15, (244, 238, 220, 255)); rect(px, 3, 1, 13, 2, (200, 190, 170, 255))
        for y in (4, 6, 8, 10, 12): rect(px, 4, y, 12, y + 1, (184, 200, 224, 255))
        rect(px, 1, 9, 9, 15, (230, 220, 190, 255)); rect(px, 1, 9, 9, 10, (190, 175, 140, 255))
    def letter(px):
        rect(px, 1, 4, 15, 13, (242, 238, 226, 255))
        for i in range(7): rect(px, 1 + i, 4 + i // 2, 15 - i, 5 + i // 2, (214, 206, 186, 255))
        rect(px, 11, 5, 14, 8, (192, 32, 48, 255)); rect(px, 3, 9, 9, 10, (60, 60, 60, 255)); rect(px, 3, 11, 8, 12, (60, 60, 60, 255))
        rect(px, 1, 12, 15, 13, (180, 170, 150, 255))
    def parcel(px):
        rect(px, 2, 3, 14, 14, (176, 136, 80, 255)); rect(px, 7, 3, 9, 14, (216, 192, 144, 255)); rect(px, 2, 8, 14, 9, (216, 192, 144, 255))
        rect(px, 3, 4, 6, 7, (244, 242, 234, 255)); rect(px, 2, 13, 14, 14, (130, 96, 56, 255))
    def bag(px):
        rect(px, 4, 5, 12, 15, (184, 176, 154, 255)); rect(px, 6, 2, 10, 5, (150, 142, 120, 255)); rect(px, 7, 1, 9, 2, (200, 160, 64, 255))
        rect(px, 4, 9, 12, 10, (130, 124, 104, 255)); rect(px, 9, 6, 11, 8, (192, 32, 32, 255)); rect(px, 4, 14, 12, 15, (130, 124, 104, 255))
    for n, f in (('mail_paper', paper), ('mail_letter', letter), ('mail_parcel', parcel), ('mail_bag', bag)):
        img(f).save(os.path.join(d, n + '.png'))
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + n}}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)


RECIPES = {
    'yard_gantry': (['ICI', 'LDL', 'IRI'], {'I': 'minecraft:iron_block', 'C': 'irextras:shipping_container', 'L': 'minecraft:lever', 'D': 'irextras:container_terminal', 'R': 'minecraft:redstone_block'}),
    'sorting_office': (['WPW', 'CSC', 'WBW'], {'W': 'minecraft:planks', 'P': 'minecraft:paper', 'C': 'minecraft:chest', 'S': 'irextras:part@20', 'B': 'minecraft:book'}),
}

if __name__ == '__main__':
    for fn in (yard_gantry, sorting_office):
        mm = fn()
        print('%-16s %d boxes' % (mm.name, len(mm.boxes)))
    for n in RECIPES:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    rec = {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:paper'}, {'item': 'minecraft:paper'}, {'item': 'minecraft:feather'}, {'item': 'minecraft:dye', 'data': 0}],
           'result': {'item': 'irextras:mail', 'count': 4, 'data': 0}}
    json.dump(rec, open(os.path.join(ASSETS, 'recipes/mail_paper.json'), 'w'), indent=1)
    mail_textures()
