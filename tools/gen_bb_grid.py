#!/usr/bin/env python3
"""IR Extras power grid machines, modelled in detail with bbkit (her ask 2026-10-09: "make everything in Blockbench ...
as complex as you physically can ... make it wire up"). Each model is saved as a Blockbench project too.
Front (north, z=0) is where the live control panel is drawn; the BACK (south) is a converter's primary side, so the
HV cables come in there; the 12 V control terminal is on the kiosk / cubicle."""
import sys
from bbkit import Model
from gen_bb_grid2 import live


def transformer(st, traction=False):
    name = ('grid_traction_%d' if traction else 'grid_transformer_%d') % st
    m = Model(name, block='grid_traction' if traction else 'grid_transformer')
    m.g('plinth')
    m.box((0, 0, 0), (16, 0.8, 16), 'concrete', 'concrete plinth')
    m.box((0, 0.8, -0.4), (16, 1.0, 0.2), 'haz', 'kerb stripe')
    for x in (2.2, 12.8):
        m.box((x, 0.8, 1), (x + 1.0, 1.0, 15.2), 'paint:dark', 'skid flange')
        m.box((x + 0.35, 1.0, 1), (x + 0.65, 1.6, 15.2), 'paint:dark', 'skid web')
        m.box((x, 1.6, 1), (x + 1.0, 1.8, 15.2), 'paint:dark', 'skid flange')
        for z in (2.2, 13.8):
            m.cyl((x - 0.3, 1.3, z), 0.75, 0.4, 'x', 'paint:ral9005', 'wheel')
    m.g('tank')
    top = 15.0 if traction else 12.5
    m.box((3, 1.8, 2), (13, top, 14.5), 'tank', 'main tank')
    for z in (4.2, 8.2, 12.2):
        m.box((2.7, 1.8, z), (3, top - 0.3, z + 0.5), 'tank', 'stiffener')
        m.box((13, 1.8, z), (13.3, top - 0.3, z + 0.5), 'tank', 'stiffener')
    m.box((2.6, top, 1.6), (13.4, top + 0.6, 14.9), 'tank', 'cover flange')
    for x in (3.0, 5.0, 7.0, 9.0, 11.0, 13.0):
        m.bolt(x, top + 0.6, 1.9, 'up', 0.4)
        m.bolt(x, top + 0.6, 14.6, 'up', 0.4)
    for z in (4.0, 6.5, 9.0, 11.5):
        m.bolt(2.9, top + 0.6, z, 'up', 0.4)
        m.bolt(13.1, top + 0.6, z, 'up', 0.4)
    m.box((3, top + 0.6, 2), (13, top + 1.0, 14.5), 'tank', 'cover')
    for (x, z) in ((3.3, 2.3), (12.3, 2.3), (3.3, 13.8), (12.3, 13.8)):
        m.box((x, top + 1.0, z), (x + 0.4, top + 1.9, z + 0.4), 'tank', 'lifting lug')
    m.g('radiators')
    for side in (0, 1):
        x0, x1 = (0.3, 2.7) if side == 0 else (13.3, 15.7)
        xin = 2.7 if side == 0 else 13.3
        for k in range(8):
            z = 3.2 + k * 1.25
            m.box((x0, 3.0, z), (x1, top - 1.5, z + 0.32), 'fins', 'radiator element')
        for y in (2.6, top - 1.1):
            m.cyl(((x0 + x1) / 2, y, 2.8), 0.42, 10.0, 'z', 'tank', 'header pipe')
            for z in (4.0, 11.6):
                m.cyl((min(xin, (x0 + x1) / 2), y, z), 0.5, abs(xin - (x0 + x1) / 2), 'x', 'tank', 'header flange pipe')
        m.g('cooling fans')
        fx = -0.5 if side == 0 else 15.7
        for z in (5.2, 10.8):
            m.cyl((fx, (3.0 + top - 1.5) / 2, z), 1.9, 0.8, 'x', 'mesh', 'fan guard', cap='mesh')
            m.cyl((fx + 0.2, (3.0 + top - 1.5) / 2, z), 0.5, 0.4, 'x', 'paint:dark', 'fan motor')
            if st:
                m.cyl((fx + 0.35, (3.0 + top - 1.5) / 2, z), 1.6, 0.1, 'x', 'paint:#70767c', 'fan blades (spinning)')
            else:
                yc = (3.0 + top - 1.5) / 2
                m.box((fx + 0.35, yc - 1.6, z - 0.3), (fx + 0.45, yc + 1.6, z + 0.3), 'paint:dark', 'fan blade', rot={'axis': 'x', 'angle': 45, 'origin': [fx + 0.4, yc, z]})
                m.box((fx + 0.35, yc - 1.6, z - 0.3), (fx + 0.45, yc + 1.6, z + 0.3), 'paint:dark', 'fan blade', rot={'axis': 'x', 'angle': -45, 'origin': [fx + 0.4, yc, z]})
        m.g('radiators')
    m.g('control kiosk')
    m.box((2.5, 1.8, 0), (13.5, 15.0, 2), 'ral7035', 'tap-changer kiosk')
    m.box((2.2, 15.0, -0.6), (13.8, 15.5, 2.2), 'ral7035', 'drip cap')
    for y in (3.5, 12.5):
        m.box((13.5, y, 0.4), (13.8, y + 1.2, 0.9), 'metal:steel', 'door hinge')
    m.terminal_12v(13.5, 2.4, 1.0, face='east')
    m.g('conservator')
    cy = top + 4.6
    m.cyl((3.5, cy, 12.4), 1.35, 9.0, 'x', 'tank', 'conservator', cap='tank')
    m.box((12.5, cy - 1.0, 11.5), (12.7, cy + 1.0, 13.3), 'paint:dark', 'oil level gauge case')
    live(m, 'east', 12.7, cy, 12.4, 'gauge', 'oillvl', 1.5, 1.5, 'f2f0e6', 'OIL', max=100)   # float gauge: oil swells as it heats
    for x in (4.5, 11.5):
        m.box((x - 0.3, top + 1.0, 12.1), (x + 0.3, cy - 1.0, 12.7), 'paint:dark', 'conservator bracket')
    m.cyl((8.0, top + 1.0, 12.4), 0.3, cy - 1.2 - top - 1.0, 'y', 'tank', 'Buchholz pipe')
    m.box((7.4, top + 1.7, 11.8), (8.6, top + 2.6, 13.0), 'paint:dark', 'Buchholz relay')
    m.box((7.6, top + 2.0, 11.7), (8.4, top + 2.4, 11.8), 'glass', 'relay window', faces=('north',))
    m.g('breather')
    m.cyl((11.0, 6.5, 15.2), 0.25, cy - 6.5, 'y', 'tank', 'breather pipe')
    m.cyl((11.0, 6.0, 15.2), 0.6, 2.4, 'y', 'paint:#e0782a', 'silica gel breather', cap='glass')
    m.g('tap changer')
    m.cyl((4.6, top + 1.0, 4.2), 1.2, 2.6, 'y', 'tank', 'diverter switch tank', cap='tank')
    m.cyl((11.3, top + 1.0, 4.2), 0.7, 0.8, 'y', 'paint:ral1023', 'pressure relief valve')
    m.g('HV bushings')
    hv = (6.0, 10.0) if traction else (5.0, 8.0, 11.0)
    for x in hv:
        m.cyl((x, top + 1.0, 7.0), 1.1, 0.9, 'y', 'tank', 'bushing turret')
        m.bushing(x, 7.0, top + 1.9, 11.0 if traction else 7.5)
    m.g('LV bushings')
    if not traction:
        for x in (4.6, 7.0, 9.4, 11.8):
            m.bushing(x, 10.0, top + 1.0, 3.0, sheds=2)
    m.g('rating plate and signs')
    m.nameplate(3.6, 9.0, 8.4, 12.0, 14.5, '25 KV TRACTION|ONAN 10 MVA' if traction else '20 MVA|33 / 11 KV|ONAN-ONAF', face='south')
    m.box((9.0, 9.0, 14.5), (12.6, 12.0, 14.62), 'danger', 'danger sign', south='danger:DANGER|33000 V')
    m.hv_box(10.6, 2.2, 14.5, 'south', phases=2 if traction else 3)
    m.hv_box(5.2, 2.2, 14.5, 'south', phases=4) if not traction else None
    m.g('earthing')
    m.box((2.6, 0.8, 14.4), (3.0, 3.2, 14.7), 'metal:copper', 'earth strap')
    m.box((2.6, 0.8, 14.4), (3.0, 1.0, 16.0), 'metal:copper', 'earth strap to grid')
    m.box((2.4, 3.0, 14.3), (3.2, 3.6, 14.8), 'paint:#3a8a3a', 'earth label')
    m.save()


if __name__ == '__main__':
    for st in (0, 1):
        transformer(st)
        transformer(st, traction=True)
