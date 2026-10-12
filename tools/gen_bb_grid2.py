#!/usr/bin/env python3
"""IR Extras power grid machines, part 2 (her ask 2026-10-09: "make everything in Blockbench and make it as complex as
you physically can and make it wire up ... low voltage stuff gets the little 12 volt wire, high voltage gets the other
wires ... like real life ... if it has buttons put buttons on it, if it don't, don't").

Builds: grid_intake, grid_rectifier, grid_inverter, grid_dcdc, grid_motor, grid_breaker (closed/open/tripped),
grid_feeder, grid_meter, grid_battery, grid_capacitor - every model name their blockstates use.

Rules every model follows
  * front = NORTH (z = 0). The live control panel (GridPanelRenderer) is drawn over the Panel.java rectangle at
    x = 16 - u, y = v; nothing of the model stands in front of it - just a flat door leaf behind it. No fake buttons.
  * every machine has the 12 V DC control terminal low down at a back corner / side, leads dropping to the floor.
  * HV comes in where it would in real life (converters: primary on the BACK), with cable boxes, bushings, glands,
    earthing straps, trays and conduits.
Run from tools/:  python3 gen_bb_grid2.py
"""
import json, os
from bbkit import Model

# Panel.java rectangles: kind -> (u0, v0, u1, v1, face)
PANELS = {'intake': (3, 1.5, 13, 14, -1.0), 'rectifier': (2.5, 1.5, 13.5, 14.5, -1.0),
          'inverter': (2.5, 1.5, 13.5, 14.5, -1.0), 'motor': (2.5, 1.5, 13.5, 14.5, -1.0),
          'dcdc': (3.0, 2.0, 13.0, 14.0, -1.0), 'breaker': (2.2, 1.2, 13.8, 15.4, 3.6),
          'meter': (4.2, 2.0, 11.8, 13.6, 3.0), 'battery': (2.5, 1.5, 13.5, 14.5, -1.0),
          'capacitor': (4, 1.5, 12, 10.5, -1.0)}


def rect(kind):
    """the panel in model space: x0, y0, x1, y1, z of the model surface behind it"""
    u0, v0, u1, v1, f = PANELS[kind]
    return 16 - u1, v0, 16 - u0, v1, (0.0 if f < 0 else f + 0.2)


# ------------------------------------------------------------------------------------------------ helpers
def uvf(side, x, y, z):
    """a model point -> (u, v, face) in the frame of a control surface on `side` (see bbkit.surface)"""
    return {'north': (16 - x, y, z), 'south': (x, y, 16 - z), 'east': (16 - z, y, 16 - x),
            'west': (z, y, x), 'up': (16 - x, z, 16 - y)}[side]


def live(m, side, x, y, z, t, cid, w, h, col, label='', **kw):
    """a WORKING control (her ask 2026-10-09: "gauges, levers and buttons working on every machine"): centred on the
    model point (x, y, z) that lies on the model face of `side` (north/south: z is the face, east/west: x, up: y).
    The game draws it live and TileGrid makes it read / do the real thing - keep the id the same in every state."""
    u, v, f = uvf(side, x, y, z)
    sf = next((s for s in m.surfaces if s['side'] == side and s['face'] == round(f, 3)), None) or m.surface(side, f)
    m.ctl(sf, t, cid, u, v, w, h, col, label, **kw)


def beacon_live(m, x, y, z):
    """a status beacon whose lenses are live: green RUN, red FAULT (flashing) - TileGrid.modelTick lights them"""
    m.cyl((x, y, z), 0.75, 0.35, 'y', 'paint:dark', 'beacon base')
    live(m, 'up', x - 0.32, y + 0.35, z, 'lamp', 'brun', 0.5, 0.9, '30ff50', '')
    live(m, 'up', x + 0.32, y + 0.35, z, 'lamp', 'bflt', 0.5, 0.9, 'ff3020', '', blink=True)


def door_leaf(m, kind, col='ral7035', depth=0.3):
    """flat door the live panel is drawn on (it stands just in front of this)"""
    x0, y0, x1, y1, zb = rect(kind)
    g = m.group
    m.g('panel door')
    m.box((x0 - 0.4, y0 - 0.4, zb), (x1 + 0.4, y1 + 0.4, zb + depth), col, 'door leaf (live panel mounts here)')
    m.g(g)


def blades(m, c, r, axis, on, t=0.14):
    """fan rotor at c (start of axis): spinning = blur disc, stopped = four blades"""
    i = 'xyz'.index(axis)
    if on:
        m.cyl(c, r, t, axis, 'paint:#80868c', 'fan blades (spinning blur)')
        m.cyl(c, r * 0.35, t + 0.05, axis, 'paint:dark', 'fan hub')
        return
    p, q = [k for k in range(3) if k != i]
    for ang in (0, 45, -45, 'perp'):
        lo, hi = list(c), list(c)
        lo[i], hi[i] = c[i], c[i] + t
        a, b = (p, q) if ang != 'perp' else (q, p)
        lo[a], hi[a] = c[a] - r, c[a] + r
        lo[b], hi[b] = c[b] - 0.32, c[b] + 0.32
        rot = None if ang in (0, 'perp') else {'axis': axis, 'angle': ang, 'origin': list(c)}
        m.box(lo, hi, 'paint:dark', 'fan blade (stopped)', rot=rot)
    m.cyl(c, r * 0.3, t + 0.05, axis, 'paint:dark', 'fan hub')


def roof_fan(m, cx, y, cz, r, on):
    """roof extract fan: square cowl (open), rotor inside, guard bars over it, motor in the middle"""
    g = m.group
    m.g('roof fans')
    w = 0.2
    m.box((cx - r - w, y, cz - r - w), (cx + r + w, y + 1.3, cz - r), 'ral7035', 'fan cowl')
    m.box((cx - r - w, y, cz + r), (cx + r + w, y + 1.3, cz + r + w), 'ral7035', 'fan cowl')
    m.box((cx - r - w, y, cz - r), (cx - r, y + 1.3, cz + r), 'ral7035', 'fan cowl')
    m.box((cx + r, y, cz - r), (cx + r + w, y + 1.3, cz + r), 'ral7035', 'fan cowl')
    m.box((cx - r, y, cz - r), (cx + r, y + 0.1, cz + r), 'louvre', 'fan inlet', faces=('up',))
    blades(m, (cx, y + 0.5, cz), r * 0.95, 'y', on)
    m.cyl((cx, y + 0.2, cz), 0.45, 0.4, 'y', 'paint:dark', 'fan motor')
    for k in (-0.6, 0, 0.6):
        m.box((cx - r, y + 1.15, cz + k * r - 0.06), (cx + r, y + 1.25, cz + k * r + 0.06), 'metal:steel', 'guard bar')
        m.box((cx + k * r - 0.06, y + 1.15, cz - r), (cx + k * r + 0.06, y + 1.25, cz + r), 'metal:steel', 'guard bar')
    m.g(g)


def side_louvre(m, xf, sgn, z0, z1, y0, y1, n=5):
    """louvre on an east (sgn=+1) / west (-1) wall whose surface is at xf"""
    step = (y1 - y0) / n
    xb = xf + sgn * 0.05
    m.box((min(xf, xb), y0, z0), (max(xf, xb), y1, z1), 'louvre', 'louvre back')
    for i in range(n):
        y = y0 + i * step + step * 0.3
        xa, xo = xf, xf + sgn * 0.45
        m.box((min(xa, xo), y, z0), (max(xa, xo), y + step * 0.45, z1), 'dark', 'louvre slat',
              rot={'axis': 'z', 'angle': 22.5 * sgn, 'origin': [xf, y, z0]})


def back_louvre(m, zf, x0, x1, y0, y1, n=5):
    """louvre on a south wall at zf"""
    step = (y1 - y0) / n
    m.box((x0, y0, zf), (x1, y1, zf + 0.05), 'louvre', 'louvre back')
    for i in range(n):
        y = y0 + i * step + step * 0.3
        m.box((x0, y, zf), (x1, y + step * 0.45, zf + 0.45), 'dark', 'louvre slat',
              rot={'axis': 'x', 'angle': 22.5, 'origin': [x0, y, zf]})


def earth_strap(m, x, z, y1, out, y0=0.0, label=True):
    """copper earth strap: down a surface at (x, z) from y1 to the floor, then along the floor toward `out`
    ('x-', 'x+', 'z+', 'z-') to the earth grid; green/yellow ID band"""
    g = m.group
    m.g('earthing')
    m.box((x - 0.22, y0, z - 0.08), (x + 0.22, y1, z + 0.08), 'metal:copper', 'earth strap')
    m.bolt(x, y1 - 0.3, z - 0.08, 'north', 0.35, 'metal:brass')
    if out:
        ax, sg = out[0], out[1]
        if ax == 'x':
            a, b = (0.0, x) if sg == '-' else (x, 16.0)
            m.box((a, y0, z - 0.22), (b, y0 + 0.12, z + 0.22), 'metal:copper', 'earth tape to grid')
        else:
            a, b = (0.0, z) if sg == '-' else (z, 16.0)
            m.box((x - 0.22, y0, a), (x + 0.22, y0 + 0.12, b), 'metal:copper', 'earth tape to grid')
    if label:
        m.box((x - 0.3, y0 + 1.2, z - 0.14), (x + 0.3, y0 + 1.6, z + 0.14), 'paint:#3a9a3a', 'earth ID band')
        m.box((x - 0.3, y0 + 1.6, z - 0.14), (x + 0.3, y0 + 1.8, z + 0.14), 'paint:ral1023', 'earth ID band')
    m.g(g)


def tray(m, axis, a0, a1, b0, b1, y, cables=('1d1d21', '1d1d21'), rung=1.2):
    """galvanised ladder cable tray along x or z with cables laid in it"""
    def B(p0, p1, q0, q1, y0, y1, mat, nm):
        if axis == 'x': m.box((p0, y0, q0), (p1, y1, q1), mat, nm)
        else: m.box((q0, y0, p0), (q1, y1, p1), mat, nm)
    g = m.group
    m.g('cable tray')
    B(a0, a1, b0, b0 + 0.15, y, y + 0.8, 'galv', 'tray side rail')
    B(a0, a1, b1 - 0.15, b1, y, y + 0.8, 'galv', 'tray side rail')
    a = a0 + 0.3
    while a < a1 - 0.3:
        B(a, a + 0.3, b0 + 0.15, b1 - 0.15, y, y + 0.15, 'galv', 'tray rung')
        a += rung
    n = len(cables)
    w = (b1 - b0 - 0.3) / n
    for i, c in enumerate(cables):
        cb = b0 + 0.15 + w * (i + 0.5)
        B(a0, a1, cb - w * 0.38, cb + w * 0.38, y + 0.15, y + 0.15 + w * 0.76, 'cable:' + c, 'cable in tray')
    m.g(g)


def anchor_bolts(m, pts, y, face='up'):
    for (x, z) in pts:
        m.box((x - 0.35, y, z - 0.35), (x + 0.35, y + 0.12, z + 0.35), 'metal:steel', 'washer')
        m.bolt(x, y + 0.12, z, face, 0.45)


def hcable(m, x, y, z, r, mat, pts):
    """a cable as straight runs through points (each step changes one axis)"""
    cur = (x, y, z)
    for nxt in pts:
        d = [nxt[i] - cur[i] for i in range(3)]
        i = max(range(3), key=lambda k: abs(d[k]))
        start = list(cur)
        if d[i] < 0: start[i] = nxt[i]
        m.cyl(tuple(start), r, abs(d[i]) + 0.0001, 'xyz'[i], mat, 'cable run')
        cur = nxt


# ------------------------------------------------------------------------------------------------ 1 intake
def intake(st):
    on = st == 1
    m = Model('grid_intake_%d' % st, block='grid_intake')
    m.g('substation pad')
    m.box((0, 0, 0), (16, 0.5, 16), 'concrete', 'substation pad')
    m.box((0.2, 0.5, 0.2), (15.8, 0.65, 0.6), 'haz', 'kerb stripe')
    for x0 in (3.0, 6.4, 9.8):                                    # cable trench chequer plates between kiosk and gantry
        m.box((x0, 0.5, 6.9), (x0 + 3.2, 0.62, 9.6), 'paint:#5c6064', 'trench cover plate')
        m.box((x0 + 1.2, 0.62, 8.1), (x0 + 2.0, 0.66, 8.4), 'dark', 'lifting slot')
    # ---- incomer kiosk (11 kV ring main unit + LV incomer; the live main switch panel is on its door)
    m.g('incomer kiosk')
    door_leaf(m, 'intake')
    m.box((2.0, 0.5, 0.0), (14.0, 1.0, 5.8), 'dark', 'kiosk plinth')
    m.box((2.2, 1.0, 0.3), (13.8, 15.2, 5.5), 'ral7035', 'kiosk body')
    m.box((1.8, 15.2, -0.3), (14.2, 15.6, 5.9), 'ral7035', 'roof drip cap')
    m.box((2.2, 15.6, 0.2), (13.8, 16.2, 5.6), 'ral7035', 'roof')
    for x in (3.0, 13.0):
        m.box((x - 0.3, 16.2, 2.6), (x + 0.3, 16.9, 3.2), 'metal:steel', 'lifting eye')
    side_louvre(m, 13.8, +1, 1.0, 4.6, 10.0, 14.0)
    side_louvre(m, 2.2, -1, 1.0, 4.6, 10.0, 14.0)
    m.box((13.8, 5.0, 0.8), (13.92, 8.2, 5.0), 'plate', 'rating plate', east='plate:INCOMER|11 KV|630 A')
    m.box((13.8, 1.6, 1.4), (13.92, 4.4, 4.2), 'warn', 'warning sign', east='warn')
    for i, x in enumerate((6.2, 8.0, 9.8)):          # voltage presence neon lamps above the door (self-powered off the HV)
        m.box((x - 0.55, 14.65, -0.15), (x + 0.55, 15.15, 0.0), 'dark', 'VPIS bezel')
        live(m, 'north', x, 14.9, -0.15, 'lamp', 'vpis%d' % (i + 1), 0.7, 0.3, 'ff5a28', '')
    m.terminal_12v(2.2, 1.3, 4.0, face='west')
    m.hv_box(8.0, 1.6, 5.5, 'south', phases=3)
    m.box((10.6, 1.6, 5.5), (13.8, 5.0, 5.62), 'danger', 'danger sign', south='danger:DANGER|11 KV')
    earth_strap(m, 13.2, 5.6, 3.0, 'x+', y0=0.5)
    # ---- terminal gantry: two lattice legs + lattice crossbeam (the overhead line ends here, from the south)
    m.g('gantry')
    for lx in (0.4, 13.6):
        m.box((lx - 0.3, 0.5, 9.7), (lx + 2.3, 0.8, 12.3), 'galv', 'leg base plate')
        anchor_bolts(m, [(lx, 10.0), (lx + 2.0, 10.0), (lx, 12.0), (lx + 2.0, 12.0)], 0.8)
        for (x, z) in ((lx, 10.0), (lx + 1.7, 10.0), (lx, 11.7), (lx + 1.7, 11.7)):
            m.box((x, 0.8, z), (x + 0.3, 28.0, z + 0.3), 'galv', 'leg angle')
        y = 2.0
        k = 0
        while y < 26.5:
            m.box((lx, y, 10.0), (lx + 2.0, y + 0.18, 10.15), 'galv', 'batten')
            m.box((lx, y, 11.85), (lx + 2.0, y + 0.18, 12.0), 'galv', 'batten')
            cy = y + 1.0
            m.box((lx + 0.15, cy - 0.07, 10.0), (lx + 1.85, cy + 0.07, 10.12), 'galv', 'lattice brace',
                  rot={'axis': 'z', 'angle': 45 if k % 2 else -45, 'origin': [lx + 1.0, cy, 10.06]})
            m.box((lx, cy - 0.07, 10.15), (lx + 0.12, cy + 0.07, 11.85), 'galv', 'lattice brace',
                  rot={'axis': 'x', 'angle': 45 if k % 2 else -45, 'origin': [lx + 0.06, cy, 11.0]})
            m.box((lx + 1.88, cy - 0.07, 10.15), (lx + 2.0, cy + 0.07, 11.85), 'galv', 'lattice brace',
                  rot={'axis': 'x', 'angle': -45 if k % 2 else 45, 'origin': [lx + 1.94, cy, 11.0]})
            y += 2.9
            k += 1
        m.box((lx - 0.2, 6.0, 9.6), (lx + 2.2, 6.4, 12.4), 'paint:#3c3c3c', 'anti-climb guard')
        for x in (lx - 0.2, lx + 0.6, lx + 1.4, lx + 2.2):
            m.box((x - 0.05, 6.4, 9.6), (x + 0.05, 7.0, 9.7), 'paint:#3c3c3c', 'anti-climb spike')
        m.box((lx - 0.6, 3.0, 9.88), (lx + 2.6, 5.4, 10.0), 'danger', 'danger plate', north='danger:DANGER|33 KV')
        m.cyl((lx + 1.0, 28.0, 11.0), 0.2, 2.2, 'y', 'galv', 'earth wire peak')
        m.box((lx + 0.6, 30.0, 10.6), (lx + 1.4, 30.4, 11.4), 'metal:steel', 'earth wire clamp')
        earth_strap(m, lx + 1.0, 9.9, 2.4, 'z-', y0=0.5, label=False)
    # lattice crossbeam
    for (y, z) in ((28.0, 10.0), (28.0, 11.7), (29.7, 10.0), (29.7, 11.7)):
        m.box((0.4, y, z), (15.6, y + 0.3, z + 0.3), 'galv', 'beam chord')
    for i in range(8):
        x = 2.4 + i * 1.6
        m.box((x - 0.07, 28.3, 10.0), (x + 0.07, 29.7, 10.12), 'galv', 'beam strut')
        m.box((x - 0.07, 28.3, 11.88), (x + 0.07, 29.7, 12.0), 'galv', 'beam strut')
        if i < 7:
            m.box((x + 0.1, 28.93, 10.0), (x + 1.5, 29.07, 10.12), 'galv', 'beam diagonal',
                  rot={'axis': 'z', 'angle': 45 if i % 2 else -45, 'origin': [x + 0.8, 29.0, 10.06]})
            m.box((x + 0.1, 28.93, 11.88), (x + 1.5, 29.07, 12.0), 'galv', 'beam diagonal',
                  rot={'axis': 'z', 'angle': 45 if i % 2 else -45, 'origin': [x + 0.8, 29.0, 11.94]})
    m.box((4.4, 28.6, 9.86), (11.6, 29.6, 10.0), 'plate', 'bay plate', north='plate:INCOMER BAY 1')
    # ---- three phases: strain insulator strings (line comes in from the south), jumper, cable sealing end, arrester
    m.box((2.4, 16.6, 10.0), (13.6, 17.2, 14.0), 'galv', 'sealing end crossarm')
    m.box((2.4, 15.4, 12.6), (13.6, 16.6, 12.9), 'galv', 'crossarm web')
    for ph, x in enumerate((4.5, 8.0, 11.5)):
        m.g('phase L%d' % (ph + 1))
        m.box((x - 0.35, 28.2, 12.0), (x + 0.35, 29.0, 12.7), 'metal:steel', 'strain yoke')
        for i in range(5):
            m.cyl((x, 28.6, 12.9 + i * 0.48), 0.85, 0.16, 'z', 'glass' if i % 2 else 'porcelain', 'cap-and-pin disc')
        m.cyl((x, 28.6, 12.7), 0.2, 2.6, 'z', 'metal:steel', 'string pin')
        m.box((x - 0.45, 28.2, 15.2), (x + 0.45, 29.0, 15.9), 'metal:alu', 'tension clamp')
        m.cyl((x, 28.6, 15.9), 0.28, 0.1, 'z', 'metal:alu', 'line conductor')       # the line continues south
        m.cyl((x, 22.6, 15.55), 0.2, 5.6, 'y', 'metal:alu', 'jumper drop')
        m.cyl((x, 22.6, 13.2), 0.2, 2.35, 'z', 'metal:alu', 'jumper')
        m.bushing(x, 13.2, 17.2, 3.0, sheds=3)                                       # cable sealing end (pothead)
        m.cyl((x, 15.9, 13.2), 0.65, 1.3, 'y', 'metal:brass', 'stress cone gland')
        m.cyl((x, 0.5, 13.2), 0.45, 15.4, 'y', 'cable:202022', 'HV cable 33 KV')
        m.box((x - 0.75, 0.5, 12.6), (x + 0.75, 6.0, 13.9), 'galv', 'cable guard')
        for cy in (8.0, 11.0, 14.0):
            m.box((x - 0.6, cy, 12.75), (x + 0.6, cy + 0.4, 13.75), 'paint:dark', 'cable cleat')
        m.bushing(x, 10.8, 17.2, 2.4, sheds=4, mat='porcelain:grey')                  # polymer surge arrester
        m.cyl((x, 21.0, 10.8), 0.12, 2.4, 'z', 'metal:alu', 'arrester line lead')
        m.box((x - 0.4, 16.0, 10.4), (x + 0.4, 16.6, 11.2), 'paint:#3c3c3c', 'surge counter')
        live(m, 'north', x, 16.3, 10.4, 'digits', 'surges', 0.7, 0.4, 'f0f0f0', '', fmt='%03d')
        m.box((x - 0.06, 0.5, 10.75), (x + 0.06, 16.0, 10.85), 'metal:copper', 'arrester earth lead')
    m.save(item_gui=0.38)


# ------------------------------------------------------------------------------------------------ 2 rectifier
def rectifier(st):
    on = st == 1
    m = Model('grid_rectifier_%d' % st, block='grid_rectifier')
    m.g('base')
    m.box((0.2, 0, 0.0), (15.8, 0.6, 12.6), 'dark', 'base channel')
    m.box((0.4, 0.6, 0.2), (15.6, 0.8, 12.4), 'paint:#1d1d1d', 'insulated plinth (frame leakage)')
    m.box((0.2, 0, -0.6), (15.8, 0.15, 0.0), 'haz', 'floor marking')
    m.g('cubicles')
    door_leaf(m, 'rectifier')
    # west end: diode cubicle built hollow so the diode stacks show through its side window
    m.box((0.5, 0.8, 0.3), (3.5, 21.0, 0.6), 'ral7035', 'diode cubicle front')
    m.box((0.5, 0.8, 11.7), (3.5, 21.0, 12.0), 'ral7035', 'diode cubicle back')
    m.box((0.5, 20.6, 0.6), (3.5, 21.0, 11.7), 'ral7035', 'diode cubicle top')
    m.box((0.5, 0.8, 0.6), (0.8, 3.0, 11.7), 'ral7035', 'side wall')
    m.box((0.5, 15.0, 0.6), (0.8, 20.6, 11.7), 'ral7035', 'side wall')
    m.box((0.5, 3.0, 0.6), (0.8, 15.0, 2.2), 'ral7035', 'side wall')
    m.box((0.5, 3.0, 10.2), (0.8, 15.0, 11.7), 'ral7035', 'side wall')
    for z in (3.0, 4.6, 6.2, 7.8, 9.4):                                       # expanded-metal guard bars
        m.box((0.45, 3.0, z), (0.55, 15.0, z + 0.12), 'metal:steel', 'window guard bar')
    m.box((0.45, 8.9, 2.2), (0.55, 9.1, 10.2), 'metal:steel', 'window guard bar')
    m.box((3.5, 0.8, 0.3), (15.5, 21.0, 12.0), 'ral7035', 'transformer + control cubicles')
    m.g('diode stacks')
    for z in (3.4, 6.2, 9.0):                                                 # 3 bridge arms, press-pack diodes + heatsinks
        m.box((1.0, 0.8, z - 0.15), (3.4, 3.2, z + 1.35), 'paint:#2a2a2a', 'stack base (insulated)')
        for k in range(8):
            y = 3.6 + k * 1.35
            m.box((1.0, y, z), (3.2, y + 0.9, z + 1.2), 'fins', 'heatsink')
            m.cyl((2.1, y + 0.9, z + 0.6), 0.45, 0.45, 'y', 'metal:copper', 'press-pack diode')
        m.box((1.9, 3.2, z - 0.1), (2.3, 15.0, z + 0.05), 'metal:steel', 'clamp tie rod')
        m.box((1.0, 14.6, z + 1.25), (3.2, 15.2, z + 1.45), 'paint:#c03030', 'fuse striker')
    m.box((1.0, 15.3, 2.4), (3.4, 15.8, 10.4), 'metal:copper', 'DC busbar (inside)')
    # front seams and louvres above the panel
    m.g('cubicles')
    for x in (5.5, 10.5):
        m.box((x - 0.06, 15.0, 0.22), (x + 0.06, 21.0, 0.3), 'dark', 'cubicle seam')
    for x0, x1 in ((0.9, 5.2), (5.8, 10.2), (10.8, 15.1)):
        m.louvres(x0, x1, 15.6, 19.4, 0.3, n=4)
    m.nameplate(3.0, 19.5, 13.0, 20.9, 0.3, '12 PULSE RECTIFIER|1500 V DC  3 MW')
    m.box((0.2, 21.0, -0.2), (15.8, 21.5, 12.2), 'ral7035', 'roof')
    for x in (1.0, 15.0):
        for z in (1.0, 11.2):
            m.box((x - 0.3, 21.5, z - 0.3), (x + 0.3, 22.2, z + 0.3), 'metal:steel', 'lifting eye')
    roof_fan(m, 4.4, 21.5, 6.2, 2.4, on)
    roof_fan(m, 11.4, 21.5, 6.2, 2.4, on)
    beacon_live(m, 8.0, 21.5, 2.0)
    side_louvre(m, 15.5, +1, 1.4, 5.8, 2.0, 8.0)
    side_louvre(m, 15.5, +1, 1.4, 5.8, 12.0, 18.0)
    m.box((15.5, 9.0, 1.0), (15.62, 11.2, 5.8), 'plate', 'rating plate', east='plate:DIODE|RECTIFIER|IEC 60146')
    m.box((15.5, 9.0, 6.0), (15.62, 11.2, 7.8), 'warn', 'warning', east='warn')
    # ---- DC outputs: positive bus duct out the east side, negative return cables out the west, low
    m.g('DC positive bus duct')
    m.box((15.5, 15.6, 7.6), (16.0, 19.4, 11.4), 'paint:#8a8e90', 'DC bus duct')
    m.box((15.9, 15.3, 7.3), (16.0, 19.7, 11.7), 'dark', 'duct flange')
    for y in (15.5, 19.5):
        for z in (7.5, 9.5, 11.5):
            m.bolt(16.0, y, z, 'east', 0.3)
    m.box((15.5, 19.4, 7.8), (15.9, 20.2, 11.2), 'label', 'label', east='label:DC POS')
    m.g('DC negative return')
    m.box((0.5, 1.0, 12.0), (3.5, 3.4, 13.4), 'paint:#2a2a2a', 'negative cable box')
    m.box((0.4, 3.4, 12.0), (3.6, 3.6, 13.5), 'dark', 'box lid')
    m.box((0.5, 1.6, 13.4), (3.6, 3.0, 13.52), 'label', 'label', south='label:DC NEG')
    for x in (1.3, 2.7):
        m.gland(x, 1.0, 12.7, 'down', r=0.42, cable='cable:1d1d21', length=1.0)
    # ---- AC primary from the 12-pulse rectifier transformer (back): star winding by bus duct, delta by cable box
    m.g('AC primary bus duct (star winding)')
    m.box((3.0, 15.0, 12.0), (13.0, 19.6, 16.0), 'ral7035', 'AC bus duct to transformer')
    for z in (12.2, 15.6):
        m.box((2.8, 14.8, z), (13.2, 19.8, z + 0.25), 'dark', 'duct flange')
    for x in (3.4, 6.4, 9.6, 12.6):
        m.bolt(x, 19.8, 12.3, 'up', 0.35)
        m.bolt(x, 19.8, 15.7, 'up', 0.35)
    m.box((4.0, 16.2, 16.0), (12.0, 18.4, 16.0), 'danger', 'duct end', faces=('south',), south='danger:DANGER|1200 V AC')
    m.box((5.0, 19.6, 13.4), (11.0, 19.9, 14.8), 'label', 'label', up='label:AC IN STAR')
    m.g('AC primary cable box (delta winding)')
    m.hv_box(6.0, 2.2, 12.0, 'south', phases=3)
    m.box((3.2, 6.4, 12.0), (8.8, 7.2, 12.12), 'label', 'label', south='label:AC IN DELTA')
    back_louvre(m, 12.0, 9.0, 12.0, 8.0, 13.0, n=4)
    tray(m, 'z', 12.0, 16.0, 13.6, 15.6, 0.0, cables=('1d1d21', 'c81e1e'))
    # ---- frame leakage earthing (the frame sits on an insulated plinth, one earth via a CT)
    m.g('earthing')
    m.cyl((15.65, 2.0, 11.0), 0.5, 0.6, 'x', 'paint:#3a3a3a', 'frame leakage CT')
    m.box((15.5, 0.6, 10.9), (15.9, 4.0, 11.1), 'metal:copper', 'frame earth lead')
    m.box((15.5, 0.0, 10.8), (16.0, 0.12, 11.2), 'metal:copper', 'earth tape')
    m.box((15.5, 3.2, 10.7), (15.62, 4.6, 11.3), 'paint:#3a9a3a', 'earth label')
    m.terminal_12v(13.4, 1.2, 12.0, face='south')
    m.save(item_gui=0.5)


# ------------------------------------------------------------------------------------------------ 3 inverter
def inverter(st):
    on = st == 1
    m = Model('grid_inverter_%d' % st, block='grid_inverter')
    m.g('skid')
    for z in (0.6, 14.0):
        m.box((0.2, 0, z), (15.8, 1.0, z + 1.2), 'dark', 'skid rail')
    for x in (0.4, 7.4, 14.4):
        m.box((x, 0, 1.8), (x + 1.2, 1.0, 14.0), 'dark', 'skid cross member')
    for x in (3.6, 10.2):
        m.box((x, 0.2, 0.55), (x + 2.2, 0.8, 0.6), 'paint:#101010', 'forklift pocket', faces=('north',))
    m.box((0.2, 1.0, 0.2), (15.8, 1.2, 15.0), 'paint:#5c6064', 'chequer floor plate')
    m.g('enclosure')
    door_leaf(m, 'inverter')
    m.box((0.8, 1.2, 0.3), (15.2, 17.0, 11.6), 'ral7035', 'inverter enclosure')
    m.box((0.5, 17.0, -0.4), (15.5, 17.5, 12.0), 'ral7035', 'roof canopy')
    m.box((0.5, 17.0, -0.6), (15.5, 17.2, -0.4), 'ral7035', 'canopy drip edge')
    m.nameplate(3.6, 15.2, 12.4, 16.7, 0.3, 'CENTRAL INVERTER|1500 V DC>3PH AC')
    for x in (1.4, 14.6):
        m.box((x - 0.15, 1.2, 0.15), (x + 0.15, 17.0, 0.3), 'dark', 'corner trim')
    # west side: filtered air intake
    side_louvre(m, 0.8, -1, 1.5, 6.0, 2.5, 14.0, n=8)
    side_louvre(m, 0.8, -1, 6.6, 10.6, 2.5, 14.0, n=8)
    m.box((0.68, 2.0, 1.2), (0.8, 14.5, 1.4), 'dark', 'filter frame')
    m.box((0.68, 2.0, 10.8), (0.8, 14.5, 11.0), 'dark', 'filter frame')
    # east side: DC isolator handle (real rotary switch), AC output cable box, rating plate
    m.g('DC isolator')
    m.box((15.2, 9.0, 1.4), (15.6, 12.4, 4.8), 'paint:ral1023', 'isolator escutcheon')
    # the rotary DC isolator itself is live: turning it OFF really isolates the DC input (same as the RUN switch)
    live(m, 'east', 15.6, 10.7, 3.1, 'selector', 'run', 1.8, 1.8, 'c81e1e', '', pos=['OFF', 'ON'])
    m.box((15.2, 12.6, 1.0), (15.32, 13.6, 5.0), 'label', 'label', east='label:ISOLATOR')
    m.box((15.2, 4.0, 0.8), (15.32, 8.2, 5.6), 'plate', 'rating plate', east='plate:3.2 MVA|690 V AC|1500 V DC')
    m.g('AC output')
    m.hv_box(15.2, 2.4, 8.5, 'east', phases=3)
    m.box((15.2, 6.6, 6.4), (15.32, 8.6, 10.6), 'danger', 'danger sign', east='danger:DANGER|690 V AC')
    # back: rain-hooded exhaust fans (top) + DC input combiner (primary, bottom)
    m.g('exhaust fans')
    for x in (4.2, 11.8):
        m.box((x - 2.6, 9.0, 11.6), (x + 2.6, 15.6, 11.7), 'paint:#2a2a2a', 'fan opening')
        blades(m, (x, 12.3, 11.7), 2.3, 'z', on)
        m.cyl((x, 12.3, 11.6), 0.5, 0.1, 'z', 'paint:dark', 'fan motor')
        for k in (-1.5, 0, 1.5):
            m.box((x - 2.5, 12.3 + k - 0.06, 12.0), (x + 2.5, 12.3 + k + 0.06, 12.1), 'metal:steel', 'guard bar')
            m.box((x + k - 0.06, 9.7, 12.0), (x + k + 0.06, 14.9, 12.1), 'metal:steel', 'guard bar')
        m.box((x - 2.9, 15.6, 11.6), (x + 2.9, 16.0, 14.4), 'ral7035', 'rain hood top')
        m.box((x - 2.9, 9.0, 11.6), (x - 2.7, 15.6, 14.4), 'ral7035', 'rain hood side')
        m.box((x + 2.7, 9.0, 11.6), (x + 2.9, 15.6, 14.4), 'ral7035', 'rain hood side')
    m.g('DC input (primary)')
    m.box((4.0, 1.2, 11.6), (12.0, 7.6, 13.4), 'ral7035', 'DC combiner box')
    m.box((3.8, 7.6, 11.4), (12.2, 8.0, 13.6), 'ral7035', 'combiner lid')
    for x in (4.4, 11.6):
        for y in (1.6, 7.2):
            m.bolt(x, y, 13.4, 'south', 0.35)
    m.box((4.3, 4.8, 13.4), (9.0, 7.2, 13.52), 'danger', 'danger sign', south='danger:DANGER|1500 V DC')
    m.box((9.2, 5.4, 13.4), (11.8, 6.8, 13.52), 'label', 'label', south='label:DC IN')
    for i, (x, c) in enumerate(((5.0, 'c81e1e'), (6.6, 'c81e1e'), (9.4, '1d1d21'), (11.0, '1d1d21'))):
        m.cyl((x, 2.2, 13.4), 0.6, 0.6, 'z', 'metal:brass', 'DC gland')
        hcable(m, x, 2.2, 14.0, 0.42, 'cable:' + c, [(x, 2.2, 15.0), (x, 1.0, 15.0)])
    tray(m, 'x', 0.4, 15.6, 14.4, 16.0, 1.0, cables=('c81e1e', 'c81e1e', '1d1d21', '1d1d21'))
    m.terminal_12v(2.6, 1.3, 11.6, face='south')
    beacon_live(m, 13.6, 17.5, 9.8)
    m.cyl((2.4, 17.5, 9.8), 0.12, 2.6, 'y', 'paint:#1a1a1a', 'GPS / modem antenna')
    earth_strap(m, 15.0, 11.7, 3.0, 'x+', y0=1.0)
    m.save(item_gui=0.55)


# ------------------------------------------------------------------------------------------------ 4 DC-DC
def dcdc(st):
    on = st == 1
    m = Model('grid_dcdc_%d' % st, block='grid_dcdc')
    m.g('base')
    m.box((1.4, 0, 0.0), (14.6, 0.8, 11.0), 'dark', 'cable plinth')
    m.box((1.4, 0, -0.5), (14.6, 0.12, 0.0), 'haz', 'floor marking')
    m.g('converter cabinet')
    door_leaf(m, 'dcdc', col='paint:#4a4f54')
    m.box((1.8, 0.8, 0.3), (14.2, 16.4, 10.6), 'paint:#5a5f64', 'converter cabinet')
    m.box((1.6, 16.4, -0.2), (14.4, 16.8, 10.8), 'paint:#4a4f54', 'cabinet roof')
    m.nameplate(4.0, 14.6, 12.0, 16.0, 0.3, 'DC-DC CONVERTER|750 > 110 V DC')
    side_louvre(m, 1.8, -1, 1.0, 9.6, 2.0, 7.0, n=6)
    side_louvre(m, 1.8, -1, 1.0, 9.6, 9.0, 14.0, n=6)
    # east side: the IGBT module heatsink sticks out through the wall (real convection-cooled converters)
    m.g('IGBT heatsink')
    m.box((14.2, 6.0, 1.6), (14.5, 14.0, 9.6), 'dark', 'heatsink base')
    for k in range(12):
        z = 1.8 + k * 0.66
        m.box((14.5, 6.2, z), (15.8, 13.8, z + 0.22), 'metal:alu', 'heatsink fin')
    m.box((14.2, 2.0, 0.9), (14.32, 5.4, 7.6), 'plate', 'rating plate', east='plate:DC-DC 250 KW|IN 750 V|OUT 110 V')
    m.box((14.2, 14.4, 2.0), (14.32, 16.0, 8.0), 'warn', 'hot surface', east='warn')
    # roof: smoothing choke (laminated iron core, copper coils) on insulators inside a mesh cage
    m.g('smoothing choke')
    m.box((3.0, 16.8, 2.0), (13.0, 17.2, 9.0), 'galv', 'choke base frame')
    for x in (3.6, 12.4):
        for z in (2.6, 8.4):
            m.cyl((x, 17.2, z), 0.45, 1.0, 'y', 'porcelain', 'support insulator')
    m.box((3.4, 18.2, 3.6), (12.6, 18.8, 7.8), 'paint:#2a2e33', 'core yoke (bottom)')
    m.box((3.4, 22.6, 3.6), (12.6, 23.2, 7.8), 'paint:#2a2e33', 'core yoke (top)')
    for x in (3.4, 7.3, 11.2):
        m.box((x, 18.8, 4.2), (x + 1.4, 22.6, 7.2), 'fins', 'core limb (laminations)')
    for x in (6.1, 10.1):
        for y in (19.0, 20.2, 21.4):
            m.box((x - 1.6, y, 3.8), (x + 1.6, y + 1.0, 7.6), 'metal:copper', 'choke winding turns')
    for x in (3.0, 13.0):
        for z in (2.0, 9.0):
            m.box((x - 0.12, 17.2, z - 0.12), (x + 0.12, 23.8, z + 0.12), 'galv', 'cage post')
    for z in (1.9, 8.9):
        m.box((2.9, 23.8, z), (13.1, 24.0, z + 0.2), 'galv', 'cage roof frame')
    for x in (2.9, 12.9):
        m.box((x, 23.8, 2.1), (x + 0.2, 24.0, 8.9), 'galv', 'cage roof frame')
    for z in (3.4, 5.5, 7.6):
        m.box((2.9, 23.8, z - 0.05), (13.1, 24.0, z + 0.05), 'galv', 'cage roof bar')
    for x in (5.0, 7.0, 9.0, 11.0):
        m.box((x - 0.05, 17.2, 1.88), (x + 0.05, 23.8, 1.98), 'galv', 'cage bar')
        m.box((x - 0.05, 17.2, 9.02), (x + 0.05, 23.8, 9.12), 'galv', 'cage bar')
    m.box((5.0, 21.0, 1.85), (11.0, 22.6, 1.95), 'danger', 'cage sign', north='danger:DANGER|750 V DC')
    m.box((3.1, 19.8, 7.6), (3.6, 20.2, 10.6), 'metal:copper', 'choke lead')
    for x in (4.2, 11.8):                                                  # roof through-bushings into the cabinet
        m.cyl((x, 16.8, 9.8), 0.35, 1.2, 'y', 'porcelain', 'roof bushing')
        m.cyl((x, 17.4, 9.8), 0.6, 0.2, 'y', 'porcelain', 'bushing shed')
        m.box((x - 0.12, 18.0, 7.6), (x + 0.12, 18.25, 9.9), 'metal:copper', 'choke link')
    for x in (2.2, 5.5, 8.0, 10.5, 13.8):
        m.bolt(x, 16.8, 0.2, 'up', 0.3)
        m.bolt(x, 16.8, 10.4, 'up', 0.3)
    m.g('west access door')
    m.box((1.7, 1.4, 1.0), (1.8, 15.6, 1.2), 'dark', 'door seam')
    m.box((1.7, 1.4, 9.8), (1.8, 15.6, 10.0), 'dark', 'door seam')
    m.box((1.5, 7.6, 1.6), (1.8, 9.6, 2.0), 'paint:dark', 'quarter-turn handle')
    for y in (3.0, 13.0):
        m.box((1.55, y, 9.6), (1.8, y + 1.2, 10.2), 'metal:steel', 'door hinge')
    # back: primary DC input 750 V (cable box, two cores)
    m.g('DC input (primary)')
    m.hv_box(7.0, 2.0, 10.6, 'south', phases=2)
    m.box((4.0, 6.2, 10.6), (10.0, 7.0, 10.72), 'label', 'label', south='label:IN 750 V DC')
    back_louvre(m, 10.6, 3.0, 13.0, 9.0, 14.0, n=5)
    # west side: secondary 110 V DC output box + cable on a tray along the wall
    m.g('DC output (secondary)')
    m.box((0.6, 3.0, 2.4), (1.8, 6.4, 5.6), 'ral7035', 'output terminal box')
    m.box((0.48, 4.6, 2.8), (0.6, 6.2, 5.2), 'label', 'label', west='label:OUT|110V')
    for z, c in ((3.3, 'c81e1e'), (4.7, '1d1d21')):
        m.gland(1.2, 3.0, z, 'down', r=0.3, cable='cable:' + c, length=1.6)
        m.box((1.0, 0.0, z - 0.25), (1.4, 1.4, z + 0.25), 'cable:' + c, 'output cable to floor')
        m.box((0.0, 0.0, z - 0.25), (1.0, 0.4, z + 0.25), 'cable:' + c, 'output cable on floor')
    beacon_live(m, 12.6, 16.8, 9.8)
    m.terminal_12v(13.0, 1.0, 10.6, face='south')
    earth_strap(m, 2.4, 10.7, 3.0, 'z+', y0=0.8)
    m.save(item_gui=0.5)


# ------------------------------------------------------------------------------------------------ 5 motor
def motor(st):
    on = st == 1
    m = Model('grid_motor_%d' % st, block='grid_motor')
    blue = 'ral5010'
    ax_y, ax_z, R = 7.6, 9.8, 4.0
    m.g('base frame')
    m.box((0.4, 0, 4.4), (15.6, 0.5, 15.4), 'dark', 'fabricated baseplate')
    for z in (4.4, 14.6):
        m.box((0.4, 0.5, z), (15.6, 1.4, z + 0.8), 'dark', 'base rail')
    anchor_bolts(m, [(1.0, 5.0), (15.0, 5.0), (1.0, 15.0), (15.0, 15.0)], 0.5)
    m.g('motor frame')
    for x0 in (3.4, 10.0):                                                    # cast feet with hold-down bolts
        m.box((x0, 1.4, 5.8), (x0 + 2.2, 4.4, 13.8), blue, 'motor foot')
        for z in (6.4, 13.2):
            m.bolt(x0 + 1.1, 4.4, z, 'up', 0.55)
        m.box((x0 + 0.3, 1.4, 5.2), (x0 + 1.9, 1.6, 6.4), 'metal:steel', 'shim pack')
    m.cyl((2.6, ax_y, ax_z), R, 9.8, 'x', blue, 'cast iron stator frame')
    for ang, (dy, dz) in ((0, (1, 0)), (0, (-1, 0)), (0, (0, 1)), (0, (0, -1))):   # axial cooling fins all round
        for off in (-1.4, 0.0, 1.4):
            if dy:
                y0 = ax_y + dy * (R * 0.92)
                m.box((3.0, min(y0, y0 + dy * 0.8), ax_z + off - 0.14), (12.0, max(y0, y0 + dy * 0.8), ax_z + off + 0.14), blue, 'cooling fin')
            else:
                z0 = ax_z + dz * (R * 0.92)
                m.box((3.0, ax_y + off - 0.14, min(z0, z0 + dz * 0.8)), (12.0, ax_y + off + 0.14, max(z0, z0 + dz * 0.8)), blue, 'cooling fin')
    for a in (45, -45):
        for off in (-1.4, 0.0, 1.4):
            y0 = ax_y + R * 0.92
            m.box((3.0, y0, ax_z + off - 0.14), (12.0, y0 + 0.8, ax_z + off + 0.14), blue, 'cooling fin',
                  rot={'axis': 'x', 'angle': a, 'origin': [7.5, ax_y, ax_z]})
            y1 = ax_y - R * 0.92
            m.box((3.0, y1 - 0.8, ax_z + off - 0.14), (12.0, y1, ax_z + off + 0.14), blue, 'cooling fin',
                  rot={'axis': 'x', 'angle': a, 'origin': [7.5, ax_y, ax_z]})
    for x in (2.2, 12.4):                                                     # end shields with bearing caps
        m.cyl((x, ax_y, ax_z), R * 0.9, 0.5, 'x', blue, 'end shield')
    m.cyl((12.9, ax_y, ax_z), 1.4, 0.6, 'x', blue, 'DE bearing cap')
    for dy, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        m.bolt(12.9, ax_y + dy * 2.4, ax_z + dz * 2.4, 'east', 0.4)
        m.bolt(2.2, ax_y + dy * 2.4, ax_z + dz * 2.4, 'west', 0.4)
    m.box((7.0, ax_y + R * 0.92 + 0.8, ax_z - 0.3), (8.0, ax_y + R * 0.92 + 2.0, ax_z + 0.3), 'metal:steel', 'lifting eye bolt')
    m.box((7.2, ax_y + R * 0.92 + 1.2, ax_z - 0.32), (7.8, ax_y + R * 0.92 + 1.7, ax_z + 0.32), 'paint:#101010', 'eye hole')
    m.nameplate(3.2, 6.2, 9.2, 8.8, ax_z + R * 0.92, 'INDUCTION|3300 V|250 KW TEFC', face='south')
    m.box((9.6, 4.6, ax_z + R * 0.92), (11.0, 6.0, ax_z + R * 0.92 + 0.3), 'paint:dark', 'thermistor box')
    m.g('bearings and sensors')
    for x, sx in ((2.45, 'NDE'), (12.65, 'DE')):
        m.cyl((x, ax_y + R * 0.75, ax_z - 1.2), 0.18, 0.7, 'y', 'metal:brass', 'grease nipple ' + sx)
        m.cyl((x, ax_y + R * 0.6, ax_z + R * 0.75), 0.3, 0.8, 'z', 'metal:steel', 'vibration / temp sensor ' + sx)
        hcable(m, x, ax_y + R * 0.6, ax_z + R * 0.75 + 0.8, 0.12, 'cable:2a3a6a', [(x, ax_y + R * 0.6, 14.9), (x, 1.4, 14.9)])
    m.box((1.2, 1.4, 14.6), (13.0, 1.6, 15.2), 'cable:2a3a6a', 'sensor cable on base rail')
    for x in (2.6, 12.5):
        m.cyl((x, ax_y - R * 0.9, ax_z), 0.2, 0.4, 'y', 'metal:brass', 'condensate drain plug')
    for (x, z) in ((3.0, 5.0), (12.0, 5.0), (3.0, 14.4), (12.0, 14.4)):
        m.box((x - 0.35, 1.4, z - 0.2), (x + 0.35, 2.2, z + 0.2), 'dark', 'jacking bolt bracket')
        m.cyl((x, 1.8, z + (0.2 if z < 9 else -0.9)), 0.15, 0.7, 'z', 'metal:steel', 'jacking bolt')
    # NDE fan cowl: open grille end so you see the cooling fan (spinning when running)
    m.g('fan cowl')
    m.cyl((0.9, ax_y, ax_z), R * 1.02, 1.3, 'x', blue, 'fan cowl')
    blades(m, (0.75, ax_y, ax_z), R * 0.85, 'x', on, t=0.12)
    for k in (-2.4, -1.2, 0, 1.2, 2.4):
        m.box((0.5, ax_y + k - 0.07, ax_z - 3.4), (0.6, ax_y + k + 0.07, ax_z + 3.4), 'metal:steel', 'cowl grille bar')
        m.box((0.5, ax_y - 3.4, ax_z + k - 0.07), (0.6, ax_y + 3.4, ax_z + k + 0.07), 'metal:steel', 'cowl grille bar')
    # drive end: shaft, key, coupling (bolts visible when stopped, a blur when running), coupling guard
    m.g('shaft and coupling')
    m.cyl((13.5, ax_y, ax_z), 0.7, 1.4, 'x', 'metal:steel', 'shaft')
    m.box((13.6, ax_y + 0.55, ax_z - 0.18), (14.8, ax_y + 0.75, ax_z + 0.18), 'metal:steel', 'shaft key')
    m.cyl((14.6, ax_y, ax_z), 1.7, 1.4, 'x', 'paint:#c06020', 'flexible coupling hub')
    if on:
        m.cyl((15.25, ax_y, ax_z), 1.75, 0.12, 'x', 'paint:#b8a080', 'coupling bolts (spinning blur)')
    else:
        for dy, dz in ((1.15, 0), (-1.15, 0), (0, 1.15), (0, -1.15)):
            m.box((15.2, ax_y + dy - 0.22, ax_z + dz - 0.22), (15.5, ax_y + dy + 0.22, ax_z + dz + 0.22), 'metal:steel', 'coupling bolt')
    m.box((13.4, 1.4, 7.2), (16.0, 1.6, 12.4), 'paint:ral1023', 'coupling guard base')
    for x in (13.5, 14.4, 15.3):
        m.box((x - 0.06, 1.6, 7.3), (x + 0.06, ax_y + 2.6, 7.42), 'paint:ral1023', 'guard bar')
        m.box((x - 0.06, 1.6, 12.2), (x + 0.06, ax_y + 2.6, 12.32), 'paint:ral1023', 'guard bar')
        m.box((x - 0.06, ax_y + 2.5, 7.3), (x + 0.06, ax_y + 2.62, 12.32), 'paint:ral1023', 'guard bar')
    m.box((13.4, ax_y + 2.62, 7.3), (16.0, ax_y + 2.74, 12.32), 'paint:ral1023', 'guard top rail')
    # main terminal box on top, 3300 V supply cable to the floor at the back
    m.g('terminal box (3300 V)')
    tb_y = ax_y + R * 0.92
    m.box((5.6, tb_y, 7.6), (9.4, tb_y + 2.6, 12.0), blue, 'main terminal box')
    m.box((5.4, tb_y + 2.6, 7.4), (9.6, tb_y + 2.9, 12.2), blue, 'terminal box lid')
    for x in (5.8, 9.2):
        for z in (7.7, 11.9):
            m.bolt(x, tb_y + 2.9, z, 'up', 0.3)
    m.box((5.6, tb_y + 0.6, 7.48), (9.4, tb_y + 2.2, 7.6), 'danger', 'danger sign', north='danger:DANGER|3300 V')
    m.cyl((7.5, tb_y + 1.2, 12.0), 0.85, 0.7, 'z', 'metal:brass', 'cable gland')
    hcable(m, 7.5, tb_y + 1.2, 12.7, 0.62, 'cable:202022', [(7.5, tb_y + 1.2, 15.6), (7.5, 0.0, 15.6)])
    for y in (4.0, 8.0):
        m.box((6.7, y, 15.0), (8.3, y + 0.5, 16.0), 'paint:dark', 'cable cleat')
    # local control station on the front (the live starter panel is on its door), conduit to the motor
    m.g('local control station')
    door_leaf(m, 'motor', col='paint:#4a4f54')
    m.box((2.0, 0.6, 0.3), (14.0, 15.3, 2.6), 'paint:#4a4f54', 'starter enclosure')
    m.box((1.7, 15.3, -0.2), (14.3, 15.7, 2.9), 'paint:#3a3f44', 'rain hood')
    for x in (2.6, 13.4):
        m.box((x - 0.4, 0, 1.0), (x + 0.4, 0.6, 2.0), 'galv', 'stand foot')
    m.cyl((8.0, 0.6, 2.4), 0.45, 0.2, 'y', 'metal:brass', 'conduit gland')
    hcable(m, 8.0, 0.4, 2.0, 0.35, 'galv', [(8.0, 0.4, 4.4)])
    m.box((14.0, 6.0, 0.35), (14.12, 9.0, 2.55), 'plate', 'tag', east='plate:M101|LCS')
    m.terminal_12v(14.0, 1.0, 1.2, face='east')
    earth_strap(m, 1.6, 6.0, 1.4, 'x-', y0=0.5)
    m.save()


# ------------------------------------------------------------------------------------------------ 6 breaker
def breaker(st):
    names = ('CLOSED', 'OPEN', 'TRIPPED')
    m = Model('grid_breaker_%d' % st, block='grid_breaker')
    x0, y0, x1, y1, zb = rect('breaker')            # recessed mechanism face at z = 3.8
    m.g('base')
    m.box((1.0, 0, 0.4), (15.0, 0.6, 13.0), 'dark', 'base channel')
    m.box((1.0, 0, -0.4), (15.0, 0.12, 0.4), 'haz', 'floor marking')
    m.g('switchgear cubicle')
    door_leaf(m, 'breaker', col='paint:#e4dcc4', depth=0.2)
    m.box((1.5, 0.6, zb + 0.2), (14.5, 26.0, 12.5), 'paint:#e4dcc4', 'cubicle body')
    for xa, xb in ((1.0, x0 - 0.3), (x1 + 0.3, 15.0)):
        m.box((xa, 0.6, 0.8), (xb, 26.0, zb + 0.2), 'paint:#e4dcc4', 'cubicle side frame')
    m.box((x0 - 0.3, 0.6, 0.8), (x1 + 0.3, y0 - 0.3, zb + 0.2), 'paint:#d4ccb4', 'kick plate')
    m.box((x0 - 0.3, y1 + 0.3, zb), (x1 + 0.3, y1 + 0.5, zb + 0.2), 'paint:#d4ccb4', 'mechanism recess top')
    # LV / relay compartment above the mechanism
    m.g('relay compartment')
    m.box((x0 - 0.3, y1 + 0.5, 0.8), (x1 + 0.3, 26.0, zb + 0.2), 'paint:#e4dcc4', 'LV compartment')
    m.box((x0 + 0.2, y1 + 1.0, 0.6), (x1 - 0.2, 25.6, 0.8), 'paint:#e8e0c8', 'LV compartment door')
    m.box((x1 - 0.8, 19.0, 0.3), (x1 - 0.4, 22.0, 0.6), 'paint:dark', 'door handle')
    m.box((3.0, 19.4, 0.5), (8.4, 23.8, 0.6), 'glass', 'relay window')
    m.box((3.4, 19.8, 0.6), (8.0, 23.4, 0.7), 'paint:#24272b', 'protection relay')
    live(m, 'north', 5.7, 22.3, 0.5, 'digits', 'rly', 3.4, 1.2, '40ff60', '', fmt='%s')               # relay display
    live(m, 'north', 7.1, 20.5, 0.5, 'flag', 'target', 1.0, 0.7, '000000', 'TRIP TARGET')               # drops on a trip
    m.box((9.2, 20.0, 0.7), (12.6, 23.4, 0.8), 'paint:#2a2a2a', 'ammeter case')
    live(m, 'north', 10.9, 21.7, 0.7, 'gauge', 'amps', 2.8, 2.8, 'f2f0e6', 'AMPS', max=100)
    m.box((3.0, 16.6, 0.66), (13.0, 18.6, 0.8), 'label', 'mimic label', north='label:FEEDER 3  11 KV')
    m.box((3.0, 24.2, 0.62), (13.0, 25.4, 0.8), 'paint:#2a2a2a', 'lamp strip')
    live(m, 'north', 4.5, 24.8, 0.62, 'lamp', 'mclosed', 1.2, 0.7, 'ff3020', 'CLOSED')
    live(m, 'north', 8.0, 24.8, 0.62, 'lamp', 'mopen', 1.2, 0.7, '30ff50', 'OPEN')
    live(m, 'north', 11.5, 24.8, 0.62, 'lamp', 'mtrip', 1.2, 0.7, 'ffa020', 'TRIPPED', blink=True)
    # roof: arc pressure relief flaps (blown flap on a trip would be a fault; show them shut) + busbar chamber
    m.g('busbar chamber')
    m.box((1.2, 26.0, 0.6), (14.8, 26.4, 12.8), 'paint:#d4ccb4', 'roof')
    for x in (2.0, 6.0, 10.0):
        m.box((x, 26.4, 1.4), (x + 3.6, 26.7, 6.4), 'paint:#c8c0a8', 'arc relief flap')
        m.box((x, 26.4, 6.4), (x + 3.6, 26.8, 6.6), 'metal:steel', 'flap hinge')
    m.box((1.5, 26.4, 7.0), (14.5, 28.6, 12.5), 'paint:#e4dcc4', 'busbar chamber')
    m.box((1.4, 28.6, 6.9), (14.6, 28.8, 12.6), 'paint:#d4ccb4', 'chamber cover')
    for x in (2.0, 5.0, 8.0, 11.0, 14.0):
        m.bolt(x, 28.8, 7.2, 'up', 0.3)
        m.bolt(x, 28.8, 12.3, 'up', 0.3)
    m.box((2.6, 27.0, 6.88), (13.4, 28.4, 7.0), 'label', 'label', north='label:BUSBAR 11 KV 1250 A')
    for x in (14.5, 1.5):                                                   # bus extension flanges to the next panel
        sg = 1 if x > 8 else -1
        m.box((min(x, x + sg * 0.3), 26.6, 7.4), (max(x, x + sg * 0.3), 28.4, 12.1), 'dark', 'bus extension flange')
    # east side: mechanism position window, spring charge flag, operation counter
    m.g('side indicators')
    m.box((14.5, 8.0, 4.6), (15.1, 13.0, 9.4), 'paint:#d4ccb4', 'indicator housing')
    live(m, 'east', 15.1, 11.9, 6.0, 'flag', 'flag', 2.0, 1.1, '000000', 'POSITION')        # red ON = closed
    m.box((15.1, 11.3, 7.4), (15.2, 12.5, 9.2), 'label', 'label', east='label:POS')
    live(m, 'east', 15.1, 10.2, 6.0, 'flag', 'springf', 2.0, 1.0, '000000', 'SPRING')        # ON = closing spring charged
    m.box((15.1, 9.7, 7.4), (15.2, 10.7, 9.2), 'label', 'label', east='label:CHG')
    live(m, 'east', 15.1, 8.85, 7.0, 'digits', 'ops', 1.5, 0.7, 'f0f0f0', '', fmt='%05d')   # mechanical operation counter
    m.box((14.5, 14.0, 4.6), (14.62, 17.6, 9.4), 'plate', 'rating plate', east='plate:VCB 11 KV|25 KA|1250 A')
    m.box((14.5, 18.6, 5.4), (15.0, 21.0, 8.6), 'paint:#d4ccb4', 'gauge boss')
    live(m, 'east', 15.0, 19.8, 7.0, 'gauge', 'sf6', 1.9, 1.9, 'f2f0e6', '', max=100)       # SF6 density (busbar chamber)
    m.box((15.0, 21.2, 5.4), (15.12, 22.2, 8.6), 'label', 'label', east='label:SF6')
    # west side: louvres, panel joint bolts, cable test access cover
    m.g('west side')
    side_louvre(m, 1.0, -1, 5.0, 11.0, 18.0, 23.0, n=4)
    m.box((0.9, 3.0, 5.5), (1.0, 8.0, 10.5), 'paint:#d4ccb4', 'test access cover')
    for y in (3.3, 7.7):
        for z in (5.8, 10.2):
            m.bolt(1.0, y, z, 'west', 0.35)
    for y in (2.0, 6.0, 10.0, 14.0, 18.0, 22.0, 25.0):
        m.bolt(1.0, y, 1.4, 'west', 0.3)
        m.bolt(15.0, y, 1.4, 'east', 0.3)
        m.bolt(1.0, y, 12.0, 'west', 0.3)
    for x in (1.8, 14.2):
        for z in (1.2, 12.2):
            m.box((x - 0.3, 26.4, z - 0.3), (x + 0.3, 27.1, z + 0.3), 'metal:steel', 'lifting eye')
    # mimic diagram on the LV door: busbar, breaker square, cable down
    m.g('mimic')
    m.box((3.0, 18.75, 0.5), (13.0, 18.95, 0.6), 'paint:#20208a', 'mimic busbar')
    m.box((10.4, 15.9, 0.5), (10.6, 18.8, 0.6), 'paint:#20208a', 'mimic drop')
    live(m, 'north', 10.5, 17.2, 0.6, 'flag', 'flag', 1.2, 1.2, '000000', 'MIMIC')          # mimic: red = closed
    # back: HV cable compartment (3 x 11 kV XLPE) and earthing
    m.g('cable compartment')
    m.hv_box(6.4, 1.8, 12.5, 'south', phases=3)
    m.box((9.4, 3.7, 12.5), (13.6, 6.8, 12.62), 'danger', 'danger sign', south='danger:DANGER|11000 V')
    back_louvre(m, 12.5, 3.0, 13.0, 14.0, 19.0, n=4)
    m.box((2.0, 7.0, 12.5), (14.0, 7.6, 13.0), 'metal:copper', 'earth bar')
    for x in (3.0, 7.0, 11.0):
        m.bolt(x, 7.3, 13.0, 'south', 0.35)
    earth_strap(m, 2.4, 13.1, 7.0, 'z+', y0=0.6)
    for i, x in enumerate((9.6, 11.0, 12.4)):                             # cable-side voltage presence (live when closed)
        m.box((x - 0.5, 9.0, 12.5), (x + 0.5, 9.8, 12.65), 'dark', 'VPIS bezel')
        live(m, 'south', x, 9.4, 12.65, 'lamp', 'vpis%d' % (i + 1), 0.6, 0.4, 'ff5a28', '')   # cable side live
    m.box((9.0, 10.0, 12.5), (13.0, 10.8, 12.62), 'label', 'label', south='label:VPIS')
    for x in (2.2, 13.8):
        for y in (8.4, 13.6):
            m.bolt(x, y, 12.5, 'south', 0.35)
    m.terminal_12v(13.0, 1.0, 12.5, face='south')
    m.save(item_gui=0.45)


# ------------------------------------------------------------------------------------------------ 7 feeder
def feeder(st):
    on = st == 1
    m = Model('grid_feeder_%d' % st, block='grid_feeder')
    m.g('foundation')
    m.box((5.0, 0, 5.0), (11.0, 1.0, 9.3), 'concrete', 'concrete foundation')
    m.box((5.6, 1.0, 5.4), (10.4, 1.3, 9.2), 'galv', 'base plate')
    anchor_bolts(m, [(6.0, 5.8), (10.0, 5.8), (6.0, 8.8), (10.0, 8.8)], 1.3)
    m.g('H-column')
    m.box((6.2, 1.3, 6.0), (9.8, 24.4, 6.5), 'galv', 'column flange')
    m.box((6.2, 1.3, 8.5), (9.8, 24.4, 9.0), 'galv', 'column flange')
    m.box((7.75, 1.3, 6.5), (8.25, 24.4, 8.5), 'galv', 'column web')
    m.box((6.0, 24.4, 5.8), (10.0, 24.7, 9.2), 'galv', 'cap plate')
    for z in (6.2, 8.8):                                                   # base gussets
        m.box((6.2, 1.3, z - 0.1), (6.3, 3.0, z + 0.1), 'galv', 'base gusset')
        m.box((9.7, 1.3, z - 0.1), (9.8, 3.0, z + 0.1), 'galv', 'base gusset')
    m.box((6.2, 3.0, 5.88), (9.8, 5.8, 6.0), 'danger', 'danger plate', north='danger:DANGER|25000 V')
    m.box((6.4, 6.4, 5.88), (9.6, 7.6, 6.0), 'plate', 'number plate', north='plate:FDR 12')
    m.box((5.8, 9.0, 5.6), (10.2, 9.4, 9.4), 'paint:#3c3c3c', 'anti-climb collar')
    for x in (5.8, 7.2, 8.8, 10.2):
        m.box((x - 0.05, 9.4, 5.6), (x + 0.05, 10.0, 5.7), 'paint:#3c3c3c', 'anti-climb spike')
        m.box((x - 0.05, 9.4, 9.3), (x + 0.05, 10.0, 9.4), 'paint:#3c3c3c', 'anti-climb spike')
    for k in range(6):                                                     # step bolts above the anti-climb guard
        y = 11.0 + k * 1.6
        x = 6.2 if k % 2 else 9.8
        m.cyl((x - (0.9 if k % 2 else 0.0), y, 8.75), 0.12, 0.9, 'x', 'metal:steel', 'step bolt')
    # cantilever arm to the line (north, -z) + 22.5 deg tie
    m.g('feeder arm')
    m.box((7.2, 20.4, -8.2), (7.6, 21.6, 6.0), 'galv', 'arm channel')
    m.box((8.4, 20.4, -8.2), (8.8, 21.6, 6.0), 'galv', 'arm channel')
    for z in (-6.0, -3.0, 1.6, 5.0):
        m.box((7.6, 20.4, z), (8.4, 20.6, z + 0.6), 'galv', 'arm batten')
    m.box((7.1, 20.3, -8.4), (8.9, 21.7, -8.2), 'galv', 'arm end cap')
    m.box((7.0, 20.0, 5.2), (9.0, 22.0, 6.0), 'galv', 'arm cleat')
    for x in (7.2, 8.8):
        m.bolt(x, 21.6, 5.6, 'up', 0.35)
    m.box((7.85, 21.2, -2.0), (8.15, 21.5, 7.0), 'galv', 'tie rod',
          rot={'axis': 'x', 'angle': -22.5, 'origin': [8.0, 21.6, -2.0]})
    m.box((7.6, 22.4, 5.6), (8.4, 24.0, 6.0), 'galv', 'tie rod bracket')
    # hanging insulator + feeder clamp on the contact wire
    m.g('clamp to the line')
    m.box((7.2, 20.0, -7.7), (8.8, 20.4, -6.7), 'galv', 'insulator cap plate')
    m.bushing(8.0, -7.2, 17.0, 3.0, sheds=3, cap=False)
    m.box((7.0, 14.0, -8.5), (9.0, 17.0, -6.0), 'metal:brass', 'feeder clamp')
    m.box((6.8, 14.4, -8.0), (7.0, 16.6, -6.5), 'metal:steel', 'clamp jaw')
    m.box((9.0, 14.4, -8.0), (9.2, 16.6, -6.5), 'metal:steel', 'clamp jaw')
    for z in (-7.8, -6.7):
        m.bolt(9.2, 15.5, z, 'east', 0.4)
        m.bolt(6.8, 15.5, z, 'west', 0.4)
    m.box((7.6, 13.6, -7.5), (8.4, 14.0, -7.0), 'metal:copper', 'wire groove')
    # cable sealing end + surge arrester on a bracket on the column front, jumper along under the arm to the clamp
    m.g('sealing end and arrester')
    m.box((4.0, 13.6, 3.0), (8.8, 14.0, 6.0), 'galv', 'sealing end bracket')
    m.box((4.0, 12.0, 5.6), (4.4, 13.6, 6.0), 'galv', 'bracket strut')
    m.bushing(8.0, 4.0, 14.0, 2.6, sheds=3)
    m.cyl((8.0, 12.6, 4.0), 0.6, 1.4, 'y', 'metal:brass', 'stress cone gland')
    m.bushing(4.8, 4.0, 14.0, 2.4, sheds=4, mat='porcelain:grey')
    m.cyl((4.8, 17.9, 4.0), 0.12, 3.2, 'x', 'metal:alu', 'arrester line lead')
    m.box((4.4, 12.8, 3.4), (5.2, 13.6, 4.0), 'paint:#3c3c3c', 'surge counter')
    live(m, 'north', 4.8, 13.2, 3.4, 'digits', 'surges', 0.7, 0.45, 'f0f0f0', '', fmt='%03d')
    m.box((4.75, 0.0, 3.2), (4.85, 12.8, 3.3), 'metal:copper', 'arrester earth lead')
    m.box((4.6, 0.0, 3.0), (5.0, 0.12, 5.0), 'metal:copper', 'earth tape')
    m.box((6.9, 13.0, 3.5), (7.5, 13.6, 3.6), 'dark', 'live line indicator body')
    live(m, 'north', 7.2, 13.3, 3.5, 'lamp', 'live', 0.45, 0.45, 'ff5a28', '')               # self-powered off the line
    m.g('feeder jumper')
    m.cyl((8.0, 18.6, -5.3), 0.2, 9.3, 'z', 'cable:202022', 'flexible jumper')
    m.cyl((8.0, 15.4, -5.3), 0.2, 3.4, 'y', 'cable:202022', 'jumper drop')
    m.cyl((8.0, 15.4, -6.0), 0.2, 0.7, 'z', 'cable:202022', 'jumper to clamp')
    m.box((7.6, 15.0, -5.7), (8.4, 15.8, -5.0), 'metal:brass', 'jumper lug')
    for z in (-2.0, 1.4):
        m.box((7.8, 18.8, z), (8.2, 20.4, z + 0.3), 'galv', 'jumper hanger')
    # riser cable from the ground (south) up the east side of the column, cleated, into the sealing end
    m.g('riser cable')
    hcable(m, 10.5, 0.3, 16.0, 0.5, 'cable:202022', [(10.5, 0.3, 7.5), (10.5, 12.0, 7.5), (10.5, 12.0, 4.0), (8.0, 12.0, 4.0), (8.0, 12.6, 4.0)])
    m.box((9.8, 1.3, 6.7), (11.3, 7.0, 8.3), 'galv', 'cable guard')
    for y in (8.0, 10.0):
        m.box((9.8, y, 6.8), (11.2, y + 0.5, 8.2), 'paint:dark', 'cable cleat')
    m.box((9.8, 1.0, 6.7), (11.3, 1.3, 8.3), 'galv', 'guard foot')
    # rail bond / earth
    m.g('earthing')
    m.box((6.2, 0.0, 4.92), (6.6, 1.3, 5.0), 'metal:copper', 'earth strap')
    m.box((6.2, 1.3, 4.92), (6.6, 1.42, 5.6), 'metal:copper', 'earth strap')
    m.box((6.18, 0.0, -0.2), (6.62, 0.12, 5.0), 'metal:copper', 'earth tape to rail')
    m.box((6.1, 0.4, 4.84), (6.7, 0.8, 4.92), 'paint:#3a9a3a', 'earth ID band')
    m.terminal_12v(7.0, 1.4, 9.0, face='south')
    m.save(item_gui=0.42)


# ------------------------------------------------------------------------------------------------ 8 meter
def meter(st):
    on = st == 1
    m = Model('grid_meter_%d' % st, block='grid_meter')
    x0, y0, x1, y1, zb = rect('meter')     # recess face z = 3.2
    m.g('plinth')
    m.box((3.0, 0, 1.2), (13.2, 0.8, 15.8), 'concrete', 'concrete plinth')
    m.g('meter kiosk')
    door_leaf(m, 'meter', col='paint:#2a2a2a', depth=0.2)
    m.box((3.2, 0.8, zb + 0.2), (12.8, 15.2, 10.2), 'ral7032', 'kiosk body')
    m.box((3.0, 0.8, 1.5), (x0 - 0.3, 15.2, zb + 0.2), 'ral7032', 'door frame side')
    m.box((x1 + 0.3, 0.8, 1.5), (13.0, 15.2, zb + 0.2), 'ral7032', 'door frame side')
    m.box((x0 - 0.3, 0.8, 1.5), (x1 + 0.3, y0 - 0.3, zb + 0.2), 'ral7032', 'door sill')
    m.box((x0 - 0.3, y1 + 0.3, 1.5), (x1 + 0.3, 15.2, zb + 0.2), 'ral7032', 'door head')
    m.box((2.6, 15.2, 0.8), (13.4, 15.6, 10.8), 'ral7032', 'rain hood')
    m.box((3.0, 15.6, 1.4), (13.0, 16.0, 10.2), 'ral7032', 'hood ridge')
    m.box((4.0, 14.2, 1.38), (12.0, 15.0, 1.5), 'plate', 'kiosk plate', north='plate:TARIFF METER')
    m.box((3.4, 1.0, 1.38), (4.0, 1.6, 1.5), 'paint:ral3000', 'meter seal')
    # test terminal block (sealed) on the east side, CT warning, rating plate
    m.g('test block')
    m.box((13.0, 4.0, 3.4), (14.2, 9.0, 8.4), 'ral7035', 'test terminal block')
    m.box((14.2, 7.6, 3.6), (14.32, 8.8, 8.2), 'label', 'label', east='label:TEST')
    for k in range(6):
        m.box((14.2, 4.6 + k * 0.48, 4.2), (14.4, 4.9 + k * 0.48, 7.6), 'metal:brass', 'test link')
    m.box((14.2, 4.2, 7.9), (14.3, 7.4, 8.1), 'paint:ral3000', 'seal wire')
    m.box((13.0, 10.0, 3.6), (13.12, 13.6, 9.6), 'danger', 'CT warning', east='danger:DO NOT OPEN|CT CIRCUIT')
    m.box((3.0, 10.0, 3.6), (3.12, 13.6, 9.6), 'plate', 'rating plate', west='plate:CL 0.2S|3 X 63.5 V|5 A')
    # CT / VT secondary wiring: multicore cables in brass glands under the kiosk, conduits into the ground
    m.g('CT and VT wiring')
    m.box((4.0, 0.8, 7.2), (12.0, 1.4, 10.2), 'dark', 'gland plate')
    for x, c, nm in ((5.2, '3a3a3a', 'CT multicore'), (7.4, '3a3a3a', 'VT multicore'), (9.6, '2a3a6a', 'comms cable')):
        m.cyl((x, 0.8, 8.7), 0.5, 0.6, 'y', 'metal:brass', 'cable gland')
        m.cyl((x, 0.0, 8.7), 0.32, 0.8, 'y', 'cable:' + c, nm)
    # combined CT/VT HV metering unit behind the kiosk: oil tank, 11 kV cable boxes in (west) and out (east)
    m.g('HV metering unit')
    m.box((4.4, 0.8, 11.2), (11.6, 7.6, 15.4), 'tank', 'CT/VT metering unit tank')
    m.box((4.2, 7.6, 11.0), (11.8, 8.0, 15.6), 'tank', 'tank cover flange')
    for x in (4.6, 6.6, 8.6, 10.6, 11.4):
        m.bolt(x, 8.0, 11.2, 'up', 0.3)
        m.bolt(x, 8.0, 15.4, 'up', 0.3)
    for x in (6.0, 8.0, 10.0):
        m.box((x - 0.2, 0.8, 15.4), (x + 0.2, 7.4, 15.7), 'tank', 'tank stiffener')
    for x in (4.9, 11.1):
        m.box((x - 0.25, 8.0, 11.4), (x + 0.25, 8.9, 11.9), 'tank', 'lifting lug')
    m.cyl((9.6, 8.0, 13.3), 0.5, 0.7, 'y', 'paint:ral1023', 'pressure relief valve')
    m.box((6.6, 8.0, 12.6), (7.8, 8.6, 13.8), 'tank', 'oil level gauge boss')
    live(m, 'up', 7.2, 8.6, 13.2, 'gauge', 'oillvl', 1.0, 1.0, 'f2f0e6', '', max=100)
    m.cyl((5.4, 0.9, 15.4), 0.3, 0.5, 'z', 'metal:brass', 'oil drain valve')
    m.box((5.6, 3.6, 15.7), (10.4, 6.2, 15.82), 'plate', 'rating plate', south='plate:MU 11 KV|200 > 5 A')
    m.box((6.0, 1.0, 15.7), (10.0, 3.2, 15.82), 'danger', 'danger sign', south='danger:DANGER|11000 V')
    m.hv_box(4.4, 2.0, 13.3, 'west', phases=3)
    m.hv_box(11.6, 2.0, 13.3, 'east', phases=3)
    m.box((2.6, 6.0, 12.6), (4.2, 6.1, 14.0), 'label', 'label', up='label:IN')
    m.box((11.8, 6.0, 12.6), (13.4, 6.1, 14.0), 'label', 'label', up='label:OUT')
    # secondary terminal box on the unit's front, multicore conduit across to the kiosk back
    m.box((6.0, 3.0, 10.6), (10.0, 6.4, 11.2), 'tank', 'secondary terminal box')
    m.box((6.6, 5.2, 10.48), (9.4, 6.2, 10.6), 'label', 'label', north='label:SEC')
    for x in (7.0, 9.0):
        m.box((x - 0.3, 3.8, 10.2), (x + 0.3, 4.4, 10.6), 'galv', 'flexible conduit')
    earth_strap(m, 11.5, 15.45, 2.0, 'x+', y0=0.8)
    # AMR modem antenna on the roof; its link LED shows the meter is live
    m.g('AMR modem')
    m.box((10.4, 16.0, 7.0), (12.0, 16.6, 8.6), 'paint:dark', 'antenna base')
    m.cyl((11.2, 16.6, 7.8), 0.14, 4.0, 'y', 'paint:#1a1a1a', 'whip antenna')
    live(m, 'north', 10.9, 16.3, 7.0, 'lamp', 'modem', 0.45, 0.3, '30ff50', '')
    earth_strap(m, 12.4, 10.3, 4.0, 'x+', y0=0.8)
    m.terminal_12v(3.0, 1.2, 8.6, face='west')
    m.save()


# ------------------------------------------------------------------------------------------------ 9 battery
def battery(st):
    on = st == 1
    m = Model('grid_battery_%d' % st, block='grid_battery')
    m.g('foundations')
    for x in (0.0, 13.8):
        for z in (0.0, 13.8):
            m.box((x, 0, z), (x + 2.2, 0.6, z + 2.2), 'concrete', 'pad footing')
    m.g('container')
    door_leaf(m, 'battery', col='paint:#d8dcd8')
    m.box((0.4, 0.6, 0.3), (15.6, 17.0, 15.6), 'paint:#dfe2df', 'container shell')
    for x in (0.0, 15.2):                                                    # corner posts + castings
        for z in (0.0, 15.2):
            m.box((x, 0.6, z), (x + 0.8, 17.0, z + 0.8), 'paint:#c8ccc8', 'corner post')
            for y in (0.6, 16.2):
                m.box((x - 0.05, y, z - 0.05), (x + 0.85, y + 0.8, z + 0.85), 'dark', 'corner casting')
    m.box((0.8, 0.6, 0.1), (15.2, 1.0, 0.3), 'paint:#c8ccc8', 'bottom front rail')
    m.box((0.8, 16.0, 0.0), (15.2, 17.0, 0.3), 'paint:#c8ccc8', 'top front header')
    m.nameplate(1.6, 16.1, 14.4, 16.9, 0.0, 'BESS 2 MWH LFP 1500 V DC')
    for x in (1.4, 14.6):                                                    # door locking bars + cam keepers
        m.box((x - 0.12, 1.2, -0.25), (x + 0.12, 15.8, 0.0), 'metal:steel', 'locking bar')
        for y in (1.4, 15.2):
            m.box((x - 0.35, y, -0.3), (x + 0.35, y + 0.5, 0.0), 'dark', 'cam keeper')
        m.box((x - 0.3, 7.6, -0.45), (x + 0.3, 8.0, -0.25), 'dark', 'bar handle')
    # corrugated walls
    for k in range(13):
        z = 1.3 + k * 1.05
        m.box((0.15, 1.0, z), (0.4, 16.0, z + 0.5), 'paint:#d4d8d4', 'wall corrugation')
        m.box((15.6, 1.0, z), (15.85, 16.0, z + 0.5), 'paint:#d4d8d4', 'wall corrugation')
        x = 1.3 + k * 1.05
        m.box((x, 1.0, 15.6), (x + 0.5, 16.0, 15.85), 'paint:#d4d8d4', 'wall corrugation')
    m.box((0.3, 17.0, 0.3), (15.7, 17.3, 15.7), 'paint:#c8ccc8', 'roof')
    for x in (2.0, 9.0):                                                     # deflagration vent panels
        m.box((x, 17.3, 5.0), (x + 5.0, 17.6, 11.0), 'paint:#b8bcb8', 'explosion vent panel')
        m.box((x + 1.2, 17.6, 7.4), (x + 3.8, 17.65, 8.6), 'label', 'vent label', up='label:VENT')
    beacon_live(m, 14.2, 17.3, 2.0)
    m.cyl((14.2, 17.3, 4.2), 0.75, 0.35, 'y', 'paint:dark', 'fire beacon base')
    live(m, 'up', 14.2, 17.65, 4.2, 'lamp', 'bfire', 0.9, 0.9, 'ff2020', '', blink=True)    # fire alarm: suppression released
    # east side: two package HVAC units with condenser fans
    m.g('HVAC')
    for zc in (4.2, 11.6):
        m.box((15.85, 6.0, zc - 3.0), (17.8, 14.6, zc + 3.0), 'ral7035', 'HVAC unit')
        m.box((15.85, 14.6, zc - 3.2), (18.0, 15.0, zc + 3.2), 'ral7035', 'HVAC rain cap')
        m.box((17.8, 6.4, zc - 2.7), (17.9, 14.2, zc + 2.7), 'paint:#2a2a2a', 'condenser opening')
        blades(m, (17.85, 11.6, zc), 2.2, 'x', on, t=0.1)
        m.cyl((17.75, 11.6, zc), 0.6, 0.1, 'x', 'paint:dark', 'fan motor')
        for k in (-1.6, -0.8, 0, 0.8, 1.6):
            m.box((17.95, 11.6 + k - 0.05, zc - 2.5), (18.05, 11.6 + k + 0.05, zc + 2.5), 'metal:steel', 'fan guard bar')
        side_louvre(m, 17.8, +1, zc - 2.4, zc + 2.4, 6.6, 9.4, n=3)
        m.cyl((16.6, 1.0, zc + 2.4), 0.15, 5.0, 'y', 'paint:#e0e0e0', 'condensate drain')
        m.box((17.8, 13.0, zc - 2.7), (17.92, 14.2, zc - 0.4), 'label', 'label', east='label:HVAC')
    # west side: BMS cabinet, fire suppression cylinder in its bracket, manual release, gas warning
    m.g('BMS cabinet')
    m.box((-1.2, 4.0, 1.4), (0.15, 12.0, 6.0), 'ral7035', 'BMS cabinet')
    m.box((-1.3, 12.0, 1.2), (0.15, 12.3, 6.2), 'ral7035', 'cabinet hood')
    live(m, 'west', -1.2, 10.1, 3.7, 'digits', 'bms', 2.8, 1.6, '40ff60', '', fmt='%s')
    m.box((-1.3, 6.4, 1.6), (-1.2, 8.4, 5.8), 'label', 'label', west='label:BMS|12 RACKS')
    m.box((-1.4, 7.0, 5.4), (-1.2, 8.0, 5.7), 'paint:dark', 'cabinet lock')
    hcable(m, -0.6, 4.0, 3.7, 0.3, 'galv', [(-0.6, 2.6, 3.7), (0.15, 2.6, 3.7)])
    m.g('fire suppression')
    m.box((-0.4, 1.0, 8.4), (0.15, 1.4, 11.4), 'paint:dark', 'cylinder bracket base')
    m.cyl((-0.9, 1.4, 9.9), 0.95, 7.4, 'y', 'paint:ral3000', 'FM200 cylinder')
    m.cyl((-0.9, 8.8, 9.9), 0.5, 0.8, 'y', 'metal:brass', 'cylinder valve')
    m.box((-0.95, 9.3, 9.75), (0.15, 9.55, 10.05), 'metal:copper', 'discharge pipe')
    for y in (3.0, 7.0):
        m.box((-1.95, y, 8.85), (0.15, y + 0.3, 10.95), 'paint:dark', 'cylinder strap')
    m.box((-2.3, 4.6, 8.6), (-2.2, 5.6, 11.4), 'label', 'label', west='label:FM200')
    m.box((0.0, 12.6, 7.6), (0.15, 15.6, 14.4), 'label', 'gas sign', west='label:GAS DISCHARGE|DO NOT ENTER')
    m.box((-0.5, 8.0, 12.4), (0.15, 9.6, 14.0), 'paint:ral1023', 'manual release station')
    live(m, 'west', -0.5, 8.8, 13.2, 'push', 'release', 0.8, 0.8, 'f0f0f0', 'RELEASE')  # break glass: dump FM200, trip the BESS
    # back: AC output from the PCS (cable box) + cable tray, and earthing
    m.g('AC output')
    m.hv_box(5.0, 2.0, 15.85, 'south', phases=3)
    m.box((7.8, 4.2, 15.85), (12.2, 6.4, 15.97), 'danger', 'danger sign', south='danger:DANGER|690 V AC')
    earth_strap(m, 15.0, 15.9, 2.0, 'x+', y0=0.6)
    earth_strap(m, 0.9, -0.05, 2.0, 'z-', y0=0.6, label=False)
    m.terminal_12v(13.0, 1.4, 15.85, face='south')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 10.4, 14.2, 4.4, 11.6, -1.2, side='west', depth=2.8, doors=1, title='BMS|12 RACKS', volts='690V AC', fns=(('ctl', '12V BMS'), ('out', 'BATT DC')))
    m.save(item_gui=0.55)


# ------------------------------------------------------------------------------------------------ 10 capacitor
def capacitor(st):
    on = st == 1
    m = Model('grid_capacitor_%d' % st, block='grid_capacitor')
    m.g('pad')
    m.box((0.2, 0, 0.2), (15.8, 0.5, 15.8), 'concrete', 'compound pad')
    m.box((0.2, 0.5, 0.2), (15.8, 0.6, 0.5), 'haz', 'kerb stripe')
    # PF controller / step contactor cabinet at the front (live PF controller on its door)
    m.g('controller cabinet')
    door_leaf(m, 'capacitor')
    m.box((3.4, 0.5, 0.3), (12.6, 11.6, 3.4), 'ral7035', 'step contactor cabinet')
    m.box((3.1, 11.6, -0.2), (12.9, 12.0, 3.7), 'ral7035', 'cabinet roof')
    for i in range(4):                                                       # step indicator lamps (stages in service)
        x = 5.2 + i * 1.9
        m.box((x - 0.5, 10.95, -0.12), (x + 0.5, 11.55, 0.0), 'dark', 'lamp bezel')
        live(m, 'north', x, 11.25, -0.12, 'lamp', 'ms%d' % (i + 1), 0.6, 0.4, '30ff50', '')
    side_louvre(m, 12.6, +1, 0.6, 2.8, 6.0, 10.0, n=4)
    m.box((12.6, 1.4, 0.6), (12.72, 4.6, 2.8), 'plate', 'rating plate', east='plate:PFC|1200|KVAR')
    m.terminal_12v(3.4, 1.0, 2.0, face='west')
    hcable(m, 8.0, 6.0, 3.4, 0.35, 'galv', [(8.0, 6.0, 4.2), (8.0, 18.4, 4.2)])
    # insulated rack: 2 tiers of capacitor cans on post insulators
    m.g('capacitor rack')
    for x in (1.0, 14.6):
        for z in (4.6, 14.6):
            m.box((x, 0.5, z), (x + 0.4, 18.0, z + 0.4), 'galv', 'rack post')
    for y in (9.0, 18.0):
        m.box((1.0, y - 0.3, 4.6), (15.0, y, 5.0), 'galv', 'rack beam')
        m.box((1.0, y - 0.3, 14.6), (15.0, y, 15.0), 'galv', 'rack beam')
    tiers = ((1.0, 'tier 1'), (9.6, 'tier 2'))
    for ty, tn in tiers:
        m.g('capacitor rack ' + tn)
        for x in (2.0, 13.6):
            for z in (5.6, 12.4):
                m.cyl((x + 0.2, ty, z + 0.2), 0.4, 1.2, 'y', 'porcelain', 'tier insulator')
        m.box((1.6, ty + 1.2, 5.4), (14.4, ty + 1.5, 13.0), 'galv', 'insulated tier frame')
        for i, x in enumerate((2.4, 6.6, 10.8)):
            for j, z in enumerate((6.0, 10.4)):
                m.box((x, ty + 1.5, z), (x + 2.8, ty + 5.5, z + 2.2), 'ral7035', 'capacitor can (single bushing)')
                m.box((x - 0.2, ty + 4.0, z + 0.7), (x, ty + 4.6, z + 1.5), 'metal:steel', 'mounting lug')
                m.box((x + 2.8, ty + 4.0, z + 0.7), (x + 3.0, ty + 4.6, z + 1.5), 'metal:steel', 'mounting lug')
                bx, bz = x + 1.4, z + 1.1
                m.cyl((bx, ty + 5.5, bz), 0.4, 1.1, 'y', 'porcelain', 'can bushing')
                m.cyl((bx, ty + 5.9, bz), 0.65, 0.25, 'y', 'porcelain', 'bushing shed')
                m.cyl((bx, ty + 6.6, bz), 0.12, 0.5, 'y', 'metal:copper', 'bushing stud')
                m.box((bx - 0.15, ty + 6.9, bz - 0.15), (bx + 0.15, ty + 7.2, bz + 2.0 if j == 0 else bz + 0.15), 'metal:copper', 'fuse link lead')
            m.box((x + 1.2, ty + 7.0, 7.0), (x + 1.6, ty + 7.3, 11.6), 'cable:707070', 'expulsion fuse tube')
        m.box((2.6, ty + 7.3, 9.0), (13.4, ty + 7.6, 9.3), 'metal:copper', 'phase busbar')
        m.box((4.0, ty + 1.5, 5.98), (12.0, ty + 2.4, 6.0), 'label', 'tier label', north='label:' + ('STEP 1  A B C' if ty < 5 else 'STEP 2  A B C'))
    # air-core series (inrush) reactors on top, on post insulators
    m.g('series reactors')
    for z in (9.0, 10.2):
        m.box((1.0, 18.0, z), (15.0, 18.4, z + 0.4), 'galv', 'reactor support beam')
    for x in (3.4, 8.0, 12.6):
        m.cyl((x, 18.4, 9.8), 0.45, 1.4, 'y', 'porcelain', 'reactor insulator')
        m.cyl((x, 19.8, 9.8), 1.9, 3.0, 'y', 'paint:#6a6e50', 'air-core reactor winding')
        for y in (19.8, 22.6):
            m.box((x - 2.0, y, 9.6), (x + 2.0, y + 0.2, 10.0), 'paint:#2a2a2a', 'spider arm')
            m.box((x - 0.2, y, 7.8), (x + 0.2, y + 0.2, 11.8), 'paint:#2a2a2a', 'spider arm')
        m.cyl((x, 22.8, 9.8), 0.15, 0.8, 'y', 'metal:alu', 'reactor terminal')
    m.box((3.3, 23.4, 9.65), (12.7, 23.6, 9.95), 'metal:alu', 'reactor tie')
    m.box((3.25, 19.4, 9.65), (3.55, 23.6, 9.95), 'metal:alu', 'down lead')
    # neutral unbalance CT and incoming 11 kV cable from the back
    m.g('neutral and incomer')
    m.cyl((14.0, 9.6, 12.2), 0.7, 0.6, 'y', 'paint:#3a3a3a', 'unbalance CT')
    m.hv_box(7.0, 1.6, 13.2, 'south', phases=3)
    hcable(m, 10.9, 0.5, 14.2, 0.35, 'cable:202022', [(10.9, 20.5, 14.2), (10.9, 20.5, 11.5)])
    # palisade fence round the sides and back, gate on the east, signs
    m.g('fence')
    for x in (0.3, 15.3):
        for z in (3.8, 15.4):
            m.box((x, 0.5, z), (x + 0.4, 15.0, z + 0.4), 'galv', 'fence post')
        for y in (2.0, 13.0):
            m.box((x + 0.1, y, 3.8), (x + 0.3, y + 0.3, 15.8), 'galv', 'fence rail')
        for k in range(10):
            z = 4.6 + k * 1.05
            m.box((x + 0.12, 0.5, z), (x + 0.28, 14.6, z + 0.22), 'galv', 'palisade pale')
    for k in range(13):
        x = 1.2 + k * 1.05
        m.box((x, 0.5, 15.52), (x + 0.22, 14.6, 15.68), 'galv', 'palisade pale')
    for y in (2.0, 13.0):
        m.box((0.3, y, 15.5), (15.7, y + 0.3, 15.7), 'galv', 'fence rail')
    m.box((15.6, 6.0, 7.6), (15.75, 9.4, 11.4), 'danger', 'fence sign', east='danger:DANGER|11000 V')
    m.box((0.25, 6.0, 7.6), (0.4, 9.4, 11.4), 'danger', 'fence sign', west='danger:DANGER|11000 V')
    m.box((15.65, 6.8, 13.0), (15.95, 7.6, 13.4), 'metal:brass', 'gate padlock')
    earth_strap(m, 14.8, 4.3, 9.0, 'z-', y0=0.5)
    m.save(item_gui=0.5)


# ------------------------------------------------------------------------------------------------ checks
def check_text(model):
    """every label / plate / sign text must fit its face at 12 px per unit (else it is cut off)"""
    bad = []
    for (nm, lo, hi, rot, fm, grp) in model.boxes:
        dx, dy, dz = hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]
        for d, spec in fm.items():
            if not spec or ':' not in spec: continue
            k, a = spec.split(':', 1)
            if k not in ('label', 'plate', 'danger'): continue
            fw, fh = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[d]
            lines = a.upper().split('|')
            if max(len(l) for l in lines) * 6 - 1 > round(fw * 12) or len(lines) * 8 - 1 > round(fh * 12):
                bad.append((nm, d, a, round(fw, 2), round(fh, 2)))
    return bad


def check_panels(model, kind):
    """nothing in front of the live panel (door + swing margin)"""
    x0, y0, x1, y1, zb = rect(kind)
    bad = []
    for (nm, lo, hi, rot, fm, grp) in model.boxes:
        pad = 0.6 if rot else 0.0
        if lo[2] - pad < zb - 1e-6 and lo[0] - pad < x1 + 0.3 and hi[0] + pad > x0 - 0.3 \
                and lo[1] - pad < y1 + 0.3 and hi[1] + pad > y0 - 0.3:
            bad.append((nm, lo, hi))
    return bad


if __name__ == '__main__':
    import bbkit
    built = {}
    _save = bbkit.Model.save
    def _track(self, *a, **k):
        built[self.name] = self
        return _save(self, *a, **k)
    bbkit.Model.save = _track
    for st in (0, 1):
        intake(st); rectifier(st); inverter(st); dcdc(st); motor(st)
        feeder(st); meter(st); battery(st); capacitor(st)
    for st in (0, 1, 2):
        breaker(st)
    root = os.path.join(bbkit.ASSETS, 'models/block')
    problems = 0
    for name, mdl in sorted(built.items()):
        kind = name[5:].rsplit('_', 1)[0]
        if kind in PANELS:
            for b in check_panels(mdl, kind):
                print('  PANEL BLOCKED', name, b); problems += 1
        for b in check_text(mdl):
            print('  TEXT TOO BIG', name, b); problems += 1
        j = json.load(open(os.path.join(root, name + '.json')))
        for e in j['elements']:
            if not e['faces']: print('  NO FACES', name, e['__comment']); problems += 1
            for f in e['faces'].values():
                if not all(0 <= v <= 16 for v in f['uv']): print('  UV OUT', name, e['__comment']); problems += 1
        print('%-22s %4d boxes' % (name, len(j['elements'])))
    print('problems:', problems)
