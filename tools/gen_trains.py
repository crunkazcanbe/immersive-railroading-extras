#!/usr/bin/env python3
"""Pride Rail train pack for Immersive Railroading (built into IR Extras): 10 front ends + 10 matching passenger cars,
voxel style, modelled on real trains. Electric: they run on RF/FE through IR Extras' Electric (monorail beam /
maglev guideway / overhead wire). Run:  python3 tools/gen_trains.py  [name ...]
Writes assets/immersiverailroading/{models/rolling_stock/pride_rail/<id>/, rolling_stock/...json, stock.json} and
blockbench/<id>.bbmodel."""
import json, math, os, sys
from voxtrain import Model

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/immersiverailroading')
BB = os.path.join(ROOT, 'blockbench')

# liveries every train gets on top of its real one: the six stripe slots carry the flag
PRIDE = {'st0': '#E40303', 'st1': '#FF8C00', 'st2': '#FFED00', 'st3': '#008026', 'st4': '#004DFF', 'st5': '#750787'}
TRANS = {'st0': '#5BCEFA', 'st1': '#F5A9B8', 'st2': '#FFFFFF', 'st3': '#FFFFFF', 'st4': '#F5A9B8', 'st5': '#5BCEFA'}

BASE = {  # shared slots (a train palette adds/overrides)
    'glass': '#A8D0E600', 'tint': '#1B2A3A', 'floor': '#4E535B', 'floor_lt': '#6B717A', 'ceiling': '#ECEEF0',
    'pole': '#D3D8DD', 'seat_dk': '#1E2A3C', 'headrest': '#F1F2F3', 'rack': '#B7BCC2', 'screen': '#0B0F14',
    'desk': '#2C3036', 'wall_in': '#E4E6E8', 'yellow': '#F2C230', 'green_led': '#3BE36B', 'fabric2': '#3A5A8C', 'seat_pri': '#B8344A', 'porcelain': '#F7F7F5', 'mirror': '#C9DCE6', 'steel_lt': '#C8CDD2', 'red': '#D3202A', 'vend': '#C8102E', 'vend_dk': '#5A0A14', 'map_bg': '#F4F4F0', 'map_line': '#1D6FD8', 'curtain': '#9A8F7E', 'wood': '#8A5A33', 'carpet': '#3B3F58', 'blue_sign': '#1E5BB8', 'glass_hi': '#3E5E7E', 'frame': '#2A2D33', 'seam': '#8E949C', 'bogie': '#3A3D42',
    'bogie_dk': '#24262A', 'tyre': '#1A1A1C', 'steel': '#9AA3AD', 'steel_dk': '#5E666F', 'lamp': '#FFF6D8',
    'tail': '#E8202A', 'bellows': '#202124', 'roof_eq': '#B9BEC4', 'roof_eq_dk': '#7D838A', 'coil': '#46A0FF',
    'interior': '#C9B79A', 'xx_unused': '#000000', 'skirt_dk': '#26282C', 'led': '#FF9A1F', 'led_bg': '#141414', 'seat': '#2F4C7A', 'copper': '#C4783C', 'black': '#111214', 'white': '#F4F5F6',
}

BEAM_TOP, BEAM_HW = -0.07, 0.40        # Pride monorail beam in train space (track top 0.30 vs rail top 0.368)


def stripes(y, y0, h=0.06):
    """6 stacked stripe slots st0..st5 from y0 upward; None outside"""
    k = int((y - y0) // h)
    return 'st%d' % k if 0 <= k < 6 else None


# ------------------------------------------------------------------------------------------------------------------
class Straddle:
    """ALWEG-type straddle monorail car: body + skirts wrapping the beam, rubber tyres on top, guide wheels on the
    sides. Subclasses set the dimensions / livery / nose."""
    L = 13.0                    # body length
    hw = 1.32                   # half width
    floor = 0.85
    roof = 3.55
    eave = 3.00                 # roof curve starts
    skirt_bot = -0.95
    skirt_in = 0.62             # hollow around the beam + guide wheels
    trucks = 3.6
    nose_len = 2.6
    win = (1.55, 2.85)          # window band
    stripe_y = 0.98
    stripe_h = 0.06
    doors = (-3.2, 0.9)         # door centres (x)
    door_w = 1.3
    pal = {}
    dx, dy = 0.125, 0.1

    L_car = None                # middle car length (None = same as L)
    both_noses = False          # GG1: streamlined at both ends
    joint = 0.32                # rubber gangway joint at each coupled end
    wall = 0.07                 # body side thickness once hollow
    layout = 'metro'            # interior: metro (side benches + poles) | hs (rows facing forward) | coach
    cab_kind = 'driver'         # driver | none (driverless: sealed nose, passengers look out) | lounge (ICE: glass wall)
    bays = False                # hs: rows in facing pairs (4-seat bays with a table)
    trays = True
    headrests = True
    priority = 2                # metro: bench seats nearest each door in the priority colour
    rows = (2, 2)               # hs/coach: seats left / right of the aisle
    pitch = 1.0                 # hs/coach: row spacing
    light = (0.93, 0.55, 0.78, 0.35, 0.75)   # headlight: nose u, y0, y1, inner/outer fraction of nose half width
    roof_units = 2

    def __init__(self, front=True, rear=False):
        self.front, self.rear = front, rear
        if not front and self.L_car:
            self.L = self.L_car
        self.x1 = self.L / 2
        self.x0 = -self.L / 2

    # --- shape
    def section(self, y):
        """(half width, inner hollow) of the plain body at height y"""
        if y > self.roof:
            return None
        hw = self.hw
        if y > self.eave:
            hw -= 1.4 * (y - self.eave) ** 2
        if y < 0:
            hw -= 0.25 * (-y / -self.skirt_bot)
        inner = self.skirt_in if y < self.floor - 0.05 else 0.0
        return hw, inner

    def nose_top(self, u): return self.roof - 1.25 * u * u
    def nose_bot(self, u): return self.skirt_bot + 1.25 * u * u
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.72 * u * u))

    def nose_hw(self, u, y, hw):
        hw *= self.nose_scale(u)
        top = self.nose_top(u)
        if y > top - 0.55:      # round the top edge
            hw *= math.sqrt(max(0.05, 1 - ((y - (top - 0.55)) / 0.6) ** 2))
        return hw

    def extra_levels(self):
        """extra mesh heights where fine livery detail needs them"""
        return []

    def cut(self, x, y):
        """True = leave this cell empty (bogie openings etc.)"""
        return False

    def nose_glass(self, u, y):
        return 0.12 < u < 0.86 and 1.5 < y < self.nose_top(u) - 0.14

    # --- colour
    def body_colour(self, x, y):
        s = stripes(y, self.stripe_y, self.stripe_h)
        if self.in_door(x):
            return self.door_colour(x, y)
        if s:
            return s
        if self.win[0] < y < self.win[1]:
            return self.window_colour(x, y)
        if y < self.floor - 0.05:
            return 'skirt_dk' if (x % 2.2) < 0.06 or y < self.skirt_bot + 0.12 else 'skirt'
        if y > self.eave + 0.25:
            return 'roof'
        return 'body'

    def window_colour(self, x, y):
        return 'glass'                  # continuous band (override for separate windows)

    def in_door(self, x):
        if not self.doors:
            return False
        return any(abs(x - d) < self.door_w / 2 for d in self.doors) and (self.front or abs(x) < self.L / 2 - 1)

    def door_colour(self, x, y):
        d = min(self.doors, key=lambda d: abs(x - d))
        if not (self.floor - 0.05 < y < self.win[1] + 0.15):
            return self.body_colour_plain(y)
        if y > self.win[1] + 0.05 or abs(abs(x - d) - self.door_w / 2) < 0.07 or abs(x - d) < 0.04:
            return 'seam'
        if True:
            return 'glass' if self.win[0] - 0.15 < y < self.win[1] - 0.1 else 'door'
        return self.body_colour_plain(y)

    def body_colour_plain(self, y):
        return ('skirt_dk' if y < self.skirt_bot + 0.12 else 'skirt') if y < self.floor - 0.05 else 'body'

    def nose_colour(self, u, y):
        if self.nose_glass(u, y):
            return 'glass'
        s = stripes(y, self.stripe_y, self.stripe_h)
        if s:
            return s
        if y < self.floor - 0.05:
            return 'skirt'
        return 'nose' if 'nose' in self.pal else 'body'

    # --- field for the loft
    def surf(self, x, y):
        """outer surface at (x, y): (half width, colour, skirt hollow, cabin hollow?, door opening?) or None"""
        if self.cut(x, y):
            return None
        nose0 = self.x1 - self.nose_len
        if self.front and x > nose0:
            u = (x - nose0) / self.nose_len
            if y > self.nose_top(u) or y < self.nose_bot(u):
                return None
            sec = self.section(min(y, self.roof - 0.01))
            if not sec:
                return None
            hw = self.nose_hw(u, y, sec[0])
            if hw <= 0.02:
                return None
            c = self.nose_colour(u, y)
            if c == 'glass' and not getattr(self, 'smooth', False) and (y > self.ceil(u) - 0.05 or y > self.nose_top(u) - 0.4):
                c = 'tint'                                  # voxel style only: tinted visor hides the stepped glass ribs
            inner = sec[1] if sec[1] < hw - 0.08 else 0.0
            return (hw, c, inner, self.floor - 0.02 < y < self.ceil(u) and hw > 0.35, False)
        sec = self.section(y)
        if not sec:
            return None
        hw, inner = sec
        opening = self.in_door(x) and self.floor - 0.02 < y < self.door_top()
        c = self.body_colour(x, y)
        jx = min(x - self.x0, (self.x1 - x) if not self.front else 99.0)   # distance to a coupled end
        if jx < self.joint and y > self.skirt_bot + 0.05:
            # full-profile rubber gangway joint between cars (the dark band you see where two cars meet)
            c = 'frame' if jx > self.joint - 0.06 else 'bellows'
            return (hw - (0.03 if c == 'frame' else 0.09), c, inner, self.floor - 0.02 < y < self.ceil(0), opening)
        if jx < self.joint + 0.12:
            hw -= 0.035                 # rounded body corner before the joint
        return (hw, c, inner, self.floor - 0.02 < y < self.ceil(0), opening)

    def field(self, x, y):
        """voxel version: (half width, colour, inner) per cell"""
        v = self.surf(x, y)
        if not v or v[4]:
            return None                                       # nothing / door opening (leaves are separate parts)
        hw, c, inner, hollow = v[0], v[1], v[2], v[3]
        if hollow:
            inner = max(inner, hw - self.wall)
        if c == 'glass':
            hw -= 0.02
        return (round(hw, 3), c, round(inner, 3))

    # --- smooth (IR-quality) shell ----------------------------------------------------------------------------
    def smooth_shell(self, g='SHELL'):
        m = self.m
        ylo, yhi = self.skirt_bot - 0.01, self.roof + 0.01
        S = self.surf

        def sv(x, y):
            v = S(x, y)
            if v is None:
                for e in (0.004, -0.004, 0.012, -0.012):
                    v = S(x, y + e)
                    if v:
                        break
            return v

        nose0 = self.x1 - self.nose_len if self.front else self.x1 + 1
        # ring positions: base grid + every colour / opening / outline change along x
        xs = set()
        x = self.x0
        while x < self.x1:
            xs.add(round(x, 4))
            x += (min(0.1, self.nose_len / 60) if x > nose0 or (self.both_noses and x < self.x0 + self.nose_len) else (0.08 if self.both_noses and abs(x) > 7.0 else 0.25))
        xs.add(self.x1)
        for xe in (self.x0 + self.joint, self.x1 - self.joint, self.x0 + self.joint + 0.12, self.x1 - self.joint - 0.12, self.x0 + self.joint - 0.06, self.x1 - self.joint + 0.06):
            xs.add(round(xe, 4))
        probes = [self.floor + 0.2 + k * 0.1 for k in range(int((self.ceil(0) - self.floor) / 0.1))] + \
                 [self.stripe_y + (k + .5) * self.stripe_h for k in range(6)] + [self.skirt_bot + 0.05, (self.skirt_bot + self.floor) / 2]
        step = 0.01
        for py in probes:
            prev = None
            x = self.x0
            while x <= nose0 and x <= self.x1:
                v = S(x, py)
                key = None if v is None else (v[1], v[4])
                if prev is not None and key != prev[1]:
                    xs.add(round(x - step / 2, 4))
                prev = (x, key)
                x += step
        xs = sorted(xs)
        xs = [x for i, x in enumerate(xs) if i == 0 or x - xs[i - 1] > 0.012]
        # level heights: base grid + every colour change up a few vertical lines
        ys = set(round(ylo + k * 0.1, 4) for k in range(int((yhi - ylo) / 0.1) + 1))
        ys.update((self.floor, self.floor - 0.05, self.ceil(0), self.door_top(), self.eave, ylo, yhi))
        ys.update(round(y, 4) for y in self.extra_levels())
        for px in [self.x0 + 0.5 + k * 0.37 for k in range(int((min(nose0, self.x1) - self.x0 - 1) / 0.37))]:
            prev = None
            y = ylo
            while y <= yhi:
                v = S(px, y)
                key = None if v is None else (v[1], v[4], v[3], round(v[2], 2))
                if prev is not None and key != prev:
                    ys.add(round(y - 0.0025, 4))
                prev = key
                y += 0.005
        ys = sorted(ys)
        ys = [y for i, y in enumerate(ys) if i == 0 or y - ys[i - 1] > 0.008]

        # rings
        rings = []
        for x in xs:
            lo = hi = None
            y = ylo
            while y <= yhi:
                if S(x, y):
                    lo = y if lo is None else lo
                    hi = y
                y += 0.01
            if lo is None:
                rings.append(None)
                continue
            pts = []
            for y in ys:
                yc = min(max(y, lo), hi)
                v = sv(x, yc)
                pts.append((x, yc, v[0] if v else 0.0, v[2] if v else 0.0, bool(v and v[3])))
            rings.append(pts)

        def col(xm, ym):
            v = sv(xm, ym)
            return v
        n = len(ys)
        for i in range(len(xs) - 1):
            A, B = rings[i], rings[i + 1]
            if A is None or B is None:
                continue
            xm = (xs[i] + xs[i + 1]) / 2
            for k in range(n - 1):
                a0, a1, b0, b1 = A[k], A[k + 1], B[k], B[k + 1]
                if abs(a1[1] - a0[1]) < 1e-5 and abs(b1[1] - b0[1]) < 1e-5:
                    continue
                # a colour edge (stripe, window, windscreen) crossing this face? split it exactly there
                def lerp(p, q, t):
                    return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, p[2] + (q[2] - p[2]) * t, p[3], p[4] and q[4])

                def at(t):
                    return col(xm, (a0[1] + b0[1]) / 2 + ((a1[1] + b1[1]) / 2 - (a0[1] + b0[1]) / 2) * t)
                lo_v, hi_v = at(0.08), at(0.92)
                parts = [(0.0, 1.0, at(0.5))]
                if lo_v and hi_v and (lo_v[1], lo_v[4]) != (hi_v[1], hi_v[4]):
                    t0, t1 = 0.08, 0.92
                    for _ in range(10):
                        tm = (t0 + t1) / 2
                        vm = at(tm)
                        if vm and (vm[1], vm[4]) == (lo_v[1], lo_v[4]):
                            t0 = tm
                        else:
                            t1 = tm
                    parts = [(0.0, (t0 + t1) / 2, lo_v), ((t0 + t1) / 2, 1.0, hi_v)]
                for (ta, tb, v) in parts:
                    if v is None or v[4]:
                        continue
                    c = v[1]
                    p0, p1, q0, q1 = lerp(a0, a1, ta), lerp(a0, a1, tb), lerp(b0, b1, ta), lerp(b0, b1, tb)
                    for zs in (1, -1):
                        m.quad(g, [(p0[0], p0[1], zs * p0[2]), (q0[0], q0[1], zs * q0[2]), (q1[0], q1[1], zs * q1[2]), (p1[0], p1[1], zs * p1[2])], c, (0, 0, zs))
                        # cabin lining (inside face of the wall), see-through where the outside is glass
                        if a0[4] and a1[4] and b0[4] and b1[4]:
                            w = self.wall
                            m.quad(g + '_lining', [(p0[0], p0[1], zs * (p0[2] - w)), (q0[0], q0[1], zs * (q0[2] - w)),
                                                   (q1[0], q1[1], zs * (q1[2] - w)), (p1[0], p1[1], zs * (p1[2] - w))],
                                   'glass' if c == 'glass' else 'wall_in', (0, 0, -zs))
                # skirt hollow round the beam / guideway: inner walls facing the beam
                if a0[3] > 0 and a1[3] > 0 and b0[3] > 0 and b1[3] > 0:
                    for zs in (1, -1):
                        m.quad(g, [(a0[0], a0[1], zs * a0[3]), (b0[0], b0[1], zs * b0[3]), (b1[0], b1[1], zs * b1[3]), (a1[0], a1[1], zs * a1[3])], 'skirt_dk', (0, 0, -zs))
            # roof cap, belly
            top = (A[-1], B[-1])
            v = col(xm, (top[0][1] + top[1][1]) / 2 - 0.006)
            if v:
                m.quad(g, [(top[0][0], top[0][1], top[0][2]), (top[1][0], top[1][1], top[1][2]), (top[1][0], top[1][1], -top[1][2]), (top[0][0], top[0][1], -top[0][2])], v[1], (0, 1, 0))
            a0, b0 = A[0], B[0]
            v = col(xm, (a0[1] + b0[1]) / 2 + 0.006)
            if v:
                if a0[3] > 0 and b0[3] > 0:
                    k = 0                                     # highest level still hollow: the underside of the floor
                    while k + 1 < n and A[k + 1][3] > 0 and B[k + 1][3] > 0:
                        k += 1
                    for zs in (1, -1):
                        m.quad(g, [(a0[0], a0[1], zs * a0[2]), (b0[0], b0[1], zs * b0[2]), (b0[0], b0[1], zs * b0[3]), (a0[0], a0[1], zs * a0[3])], v[1], (0, -1, 0))
                    ak, bk = A[k], B[k]
                    m.quad(g, [(ak[0], ak[1], ak[3]), (bk[0], bk[1], bk[3]), (bk[0], bk[1], -bk[3]), (ak[0], ak[1], -ak[3])], 'frame', (0, -1, 0))
                else:
                    m.quad(g, [(a0[0], a0[1], a0[2]), (b0[0], b0[1], b0[2]), (b0[0], b0[1], -b0[2]), (a0[0], a0[1], -a0[2])], v[1], (0, -1, 0))
        # end caps (leave the gangway doorway open)
        for idx, sx in ((0, -1), (len(xs) - 1, 1)):
            R = rings[idx]
            if R is None:
                continue
            for k in range(n - 1):
                p0, p1 = R[k], R[k + 1]
                if abs(p1[1] - p0[1]) < 1e-5:
                    continue
                v = col(p0[0] - sx * 0.01, (p0[1] + p1[1]) / 2)
                if v is None:
                    continue
                ym = (p0[1] + p1[1]) / 2
                inner = max(p0[3], p1[3])
                gap = 0.52 if (self.floor < ym < self.floor + 2.05 and not (self.front and sx > 0)) else 0.0
                hole = max(inner, gap)
                if hole > 0:
                    for zs in (1, -1):
                        m.quad(g, [(p0[0], p0[1], zs * hole), (p0[0], p0[1], zs * p0[2]), (p1[0], p1[1], zs * p1[2]), (p1[0], p1[1], zs * hole)], v[1], (sx, 0, 0))
                else:
                    m.quad(g, [(p0[0], p0[1], -p0[2]), (p0[0], p0[1], p0[2]), (p1[0], p1[1], p1[2]), (p1[0], p1[1], -p1[2])], v[1], (sx, 0, 0))

    def ceil(self, u=0.0):
        """inside ceiling height (the cab ceiling follows the nose down)"""
        c = max(self.win[1] + 0.25, self.floor + 2.25, self.roof - 0.22)     # >= 2.25 m headroom: stand up inside
        return min(c, self.nose_top(u) - 0.18) if u > 0 else c

    def door_top(self):
        return self.win[1] + 0.12

    # --- interior ---------------------------------------------------------------------------------------------
    def interior(self):
        m = self.m
        self.n_seat = 0
        self.n_door = 0
        zw = self.hw - self.wall                    # inner face of the side walls
        nose0 = self.x1 - self.nose_len
        xa = self.x0 + 0.1
        xb = (nose0 - 0.1) if self.front else self.x1 - 0.1
        top = self.ceil(0)
        fl = self.floor
        # floor (IR's FLOOR = what you walk on), ceiling lining, light strips, door-edge warning strips
        fx = (nose0 + self.nose_len * 0.55) if self.front else xb
        m.slab('FLOOR', xa, fx, fl - 0.03, fl, zw, 'floor')                  # what you walk on (IR doesn't draw it)
        m.slab('SHELL_floor', xa, fx, fl - 0.025, fl + 0.004, zw, 'floor')   # the visible floor covering
        self.floor_pattern(xa, fx, zw, fl)
        m.slab('SHELL_ceiling', xa, xb, top - 0.04, top, zw, 'ceiling')
        m.sym('SHELL_lights', xa + 0.3, xb - 0.3, top - 0.07, top - 0.04, 0.42, 0.62, 'lamp')
        # end walls with a gangway opening + connecting door
        for ex, sx in ((self.x0, 1),) + (() if self.front else ((self.x1, -1),)):
            x0, x1 = sorted((ex + sx * 0.04, ex + sx * 0.12))      # inset: no flicker against the outer skin
            m.sym('SHELL_end', x0, x1, fl, top, 0.52, zw, 'wall_in')
            m.box('SHELL_end', x0, x1, fl + 2.05, top, -0.52, 0.52, 'wall_in')
            self.n_door += 1
            # gangway door: IR Extras opens 'CF' (front end) / 'CR' (rear end) when someone walks up to it
            m.box('CONNECTING_DOOR_%d_CG_%s_TOGGLE_TL_%.2f_Z' % (self.n_door, 'CR' if sx > 0 else 'CF', 0.98), x0 - 0.01 * sx, x1 - 0.01 * sx,
                  fl, fl + 2.05, -0.5, 0.5, 'door' if 'door' in self.pal else 'wall_in')
            m.box('CONNECTING_DOOR_%d_CG_%s_TOGGLE_TL_%.2f_Z' % (self.n_door, 'CR' if sx > 0 else 'CF', 0.98), x0 - 0.012 * sx, x1 - 0.012 * sx,
                  fl + 1.0, fl + 1.9, -0.4, 0.4, 'glass')
            m.box('SHELL_end', x0 - 0.02 * sx, x1 + 0.02 * sx, fl + 2.05, fl + 2.45, -0.5, 0.5, 'screen')        # car end screen
            self.sign('end', -0.47, 0.47, fl + 2.07, fl + 2.43, (x1 + 0.025) if sx > 0 else (x0 - 0.025), nx=sx)
        # sliding outside doors: two leaves per door per side, both leaves on one control group
        for d in self.door_list():
            for zs in (1, -1):
                self.door_pair(d, zs)
        # rooms first (toilets, luggage, lounges) so the seats leave room for them, then seats, then the details
        self.reserved = []
        self.plan(xa, xb, zw, fl, top)
        getattr(self, 'interior_' + self.layout)(xa + 0.15, xb - 0.15, zw, fl, top)
        self.furnish(xa, xb, zw, fl, top)
        if self.front:
            if self.cab_kind == 'none':
                self.sealed_nose(nose0, zw, fl)
            elif self.cab_kind == 'lounge':
                self.lounge(nose0, zw, fl)
            else:
                self.cab(nose0, zw, fl)

    def free(self, x0, x1=None):
        """is x (or the span x0..x1) clear of reserved rooms?"""
        x1 = x0 if x1 is None else x1
        return not any(a - 0.05 < x1 and x0 < b + 0.05 for a, b in self.reserved)

    def plan(self, xa, xb, zw, fl, top):
        pass

    def furnish(self, xa, xb, zw, fl, top):
        """the bits every real car has: AC + speakers + CCTV, route maps over the doors, extinguishers + intercoms"""
        self.ceiling_kit(xa, xb)
        for d in self.door_list():
            for zs in (1, -1):
                if self.layout == 'metro':
                    self.route_map(d, zs, min(1.2, self.door_w))
        for zs in (1, -1):
            self.safety_kit(xa + 0.35, zs)

    def door_list(self):
        if not self.doors:
            return []
        return [d for d in self.doors if self.front or abs(d) < self.L / 2 - 1] if self.front else list(self.doors)

    def door_pair(self, d, zs):
        m = self.m
        hw = self.hw
        w = self.door_w / 2
        top = self.door_top()
        z0, z1 = sorted((zs * (hw - self.wall), zs * (hw - 0.012)))
        self.n_door += 1
        # control group = side + door number: IR Extras' station doors open 'DR*' (right, +z) or 'DL*' (left)
        idx = sorted(self.door_list()).index(d) + 1 if d in self.door_list() else self.n_door
        cg = '%s%d' % ('DR' if zs > 0 else 'DL', idx)
        for k, (xa, xb, slide) in enumerate(((d - w, d, -(w - 0.06)), (d, d + w, w - 0.06))):
            self.n_door += 1
            g = 'EXTERNAL_DOOR_%d_CG_%s_TOGGLE_TL_%.2f_X' % (self.n_door, cg, slide)
            col = 'door' if 'door' in self.pal else 'body'
            gy0, gy1 = self.win[0] - 0.1, self.win[1] - 0.12
            m.box(g, xa, xb, self.floor, gy0, z0, z1, col)                       # lower panel
            m.box(g, xa, xb, gy1, top, z0, z1, col)                              # head
            m.box(g, xa, xa + 0.07, gy0, gy1, z0, z1, col)                       # stiles
            m.box(g, xb - 0.07, xb, gy0, gy1, z0, z1, col)
            m.box(g, xa + 0.07, xb - 0.07, gy0, gy1, z0 + 0.02, z1 - 0.02, 'glass')
            m.box(g, (xb if k == 0 else xa) - 0.015, (xb if k == 0 else xa) + 0.015, self.floor, top, z0 - 0.005, z1 + 0.005, 'seat_dk')  # rubber edge
        # door surround: yellow floor edge + 'door closing' light + LED screen above (inside)
        zi = zs * (hw - self.wall - 0.01)
        m.box('SHELL_doorkit', d - w, d + w, self.floor, self.floor + 0.005, *sorted((zi, zi - zs * 0.25)), 'yellow')
        m.box('SHELL_doorkit', d - 0.45, d + 0.45, top + 0.02, top + 0.2, *sorted((zi, zi - zs * 0.04)), 'screen')
        self.sign('door', d - 0.42, d + 0.42, top + 0.035, top + 0.185, zi - zs * 0.042, nz=-zs)

    def seat(self, x0, x1, y0, y1, z0, z1, col='seat'):
        self.n_seat += 1
        self.m.box('SEAT_%d' % self.n_seat, x0, x1, y0, y1, z0, z1, col)

    def interior_metro(self, xa, xb, zw, fl, top):
        """metro / monorail: longitudinal benches along both walls between the doors, poles, grab rails"""
        m = self.m
        doors = sorted(self.door_list())
        cuts = [xa] + sum(([d - self.door_w / 2 - 0.35, d + self.door_w / 2 + 0.35] for d in doors), []) + [xb]
        for a, b in zip(cuts[0::2], cuts[1::2]):
            for ra, rb in self.reserved:               # rooms / wheelchair bays cut the bench short
                if ra < b and a < rb:
                    a, b = (rb + 0.1, b) if ra <= a + 0.5 else (a, ra - 0.1)
            if b - a < 0.9:
                continue
            for zs in (1, -1):
                zo = zs * (zw - 0.015)       # 1.5 cm off the wall: touching it z-fought (blue seats flickering through the yellow body)
                zi = zs * (zw - 0.5)
                m.box('SHELL_bench', a, b, fl, fl + 0.4, *sorted((zo, zs * (zw - 0.42))), 'seat_dk')        # plinth
                m.box('SHELL_bench', a, b, fl + 0.5, fl + 1.05, *sorted((zo, zs * (zw - 0.1))), 'seat')     # backrest
                n = max(1, int((b - a) / 0.5))
                for k in range(n):
                    sx = a + (b - a) * k / n
                    pri = (k < self.priority and a > cuts[0] + 0.01) or (n - k <= self.priority and b < cuts[-1] - 0.01)
                    self.seat(sx + 0.02, sx + (b - a) / n - 0.02, fl + 0.4, fl + 0.5, *sorted((zo - zs * 0.05, zi)),
                              'seat_pri' if pri else 'seat')
                for px in (a, b):                                                                          # end screens
                    m.box('SHELL_bench', px - 0.03, px + 0.03, fl, fl + 1.2, *sorted((zo, zs * (zw - 0.55))), 'pole')
                m.box('SHELL_rail', a, b, top - 0.32, top - 0.27, *sorted((zs * (zw - 0.45), zs * (zw - 0.4))), 'pole')   # grab rail
                for k in range(int((b - a) / 0.6)):                                                          # hanging straps
                    hx = a + 0.3 + k * 0.6
                    m.box('SHELL_strap', hx - 0.015, hx + 0.015, top - 0.55, top - 0.3, *sorted((zs * (zw - 0.44), zs * (zw - 0.41))), 'seat_dk')
                    m.box('SHELL_strap', hx - 0.06, hx + 0.06, top - 0.62, top - 0.55, *sorted((zs * (zw - 0.46), zs * (zw - 0.39))), 'yellow')
                m.box('SHELL_ad', a + 0.2, b - 0.2, top - 0.25, top - 0.05, *sorted((zo, zs * (zw - 0.02))), 'wall_in')  # advert strip
        for d in doors:                                                                                   # door poles
            for px, pz in ((d - self.door_w / 2 - 0.2, zw - 0.55), (d + self.door_w / 2 + 0.2, zw - 0.55),
                           (d - self.door_w / 2 - 0.2, -(zw - 0.55)), (d + self.door_w / 2 + 0.2, -(zw - 0.55)), (d, 0.0)):
                m.box('SHELL_pole', px - 0.025, px + 0.025, fl, top, pz - 0.025, pz + 0.025, 'pole')

    def interior_hs(self, xa, xb, zw, fl, top):
        """long distance: rows of seats facing forward with an aisle, luggage racks, end screens"""
        m = self.m
        left, right = self.rows
        sw = 0.46
        aisle = (2 * zw - (left + right) * sw) / 2              # aisle half-width-ish
        zl = [-zw + 0.045 + k * sw for k in range(left)]         # seat left edges, left side
        zr = [zw - 0.045 - (k + 1) * sw for k in range(right)]
        vest = 1.3                                              # keep clear behind the end doors
        doors = self.door_list()
        x = xa + 0.6
        row = 0
        while x + self.pitch * 0.6 < xb:
            if any(abs(x - d) < self.door_w / 2 + vest * 0.6 for d in doors) or not self.free(x - 0.45, x + 0.45):
                x += self.pitch
                continue
            back = self.bays and row % 2 == 1          # facing bays: every second row turned round
            sgn = -1 if back else 1
            for zs in zl + zr:
                z0, z1 = zs + 0.02, zs + sw - 0.02
                m.box('SHELL_seatbase', x - 0.18, x + 0.18, fl, fl + 0.38, z0 + 0.06, z1 - 0.06, 'steel_dk')
                self.seat(*sorted((x - sgn * 0.22, x + sgn * 0.25)), fl + 0.38, fl + 0.5, z0, z1)
                m.box('SHELL_seatback', *sorted((x - sgn * 0.32, x - sgn * 0.2)), fl + 0.45, fl + 1.12, z0, z1, 'seat')
                if self.headrests:
                    m.box('SHELL_seatback', *sorted((x - sgn * 0.335, x - sgn * 0.185)), fl + 0.95, fl + 1.16, z0 + 0.04, z1 - 0.04, 'headrest')
                if self.trays and not self.bays:
                    m.box('SHELL_seatback', *sorted((x - sgn * 0.36, x - sgn * 0.32)), fl + 0.6, fl + 0.95, z0 + 0.06, z1 - 0.06, 'seat_dk')
            for edge in (zl[0], zl[-1] + sw, zr[0] + sw, zr[-1]):                                            # armrests
                m.box('SHELL_arm', x - 0.2, x + 0.2, fl + 0.55, fl + 0.62, edge - 0.03, edge + 0.03, 'seat_dk')
            if back:                                                                                          # bay table
                for z0, z1 in ((zl[0], zl[-1] + sw), (zr[-1], zr[0] + sw)):
                    m.box('SHELL_table', x - self.pitch / 2 - 0.3, x - self.pitch / 2 + 0.3, fl + 0.72, fl + 0.76, z0 + 0.05, z1 - 0.05, 'wood')
                    m.box('SHELL_table', x - self.pitch / 2 - 0.04, x - self.pitch / 2 + 0.04, fl, fl + 0.72, (z0 + z1) / 2 - 0.04, (z0 + z1) / 2 + 0.04, 'steel_dk')
            x += self.pitch
            row += 1
        for zs in (1, -1):                                                                                  # luggage racks
            m.box('SHELL_rack', xa, xb, self.win[1] + 0.08, self.win[1] + 0.11, *sorted((zs * zw, zs * (zw - 0.45))), 'rack')
            m.box('SHELL_rack', xa, xb, self.win[1] + 0.11, self.win[1] + 0.2, *sorted((zs * (zw - 0.42), zs * (zw - 0.45))), 'rack')
            m.box('SHELL_rack', xa, xb, self.win[1] + 0.2, self.win[1] + 0.24, *sorted((zs * zw, zs * (zw - 0.06))), 'lamp')   # reading light strip

    def interior_coach(self, xa, xb, zw, fl, top):
        self.interior_hs(xa, xb, zw, fl, top)

    def cab(self, nose0, zw, fl):
        """driver's cab behind the windscreen: partition with doorway, desk, working IR controls, driver's seat"""
        m = self.m
        px = nose0 - 0.1
        top = self.ceil(0)
        m.sym('SHELL_cabwall', px, px + 0.08, fl, top, 0.45, zw, 'wall_in')
        m.box('SHELL_cabwall', px, px + 0.08, fl + 2.0, top, -0.45, 0.45, 'wall_in')
        # desk sits where the windscreen starts
        u = self.cab_u()
        dx = nose0 + self.nose_len * u
        dhw = max(0.5, self.nose_hw(u, fl + 0.9, self.section(fl + 0.9)[0]) - self.wall - 0.05)
        m.box('SHELL_desk', dx - 0.55, dx + 0.15, fl, fl + 0.85, -dhw, dhw, 'desk')
        m.box('SHELL_desk', dx - 0.6, dx + 0.1, fl + 0.85, fl + 0.92, -dhw, dhw, 'frame')
        m.box('SHELL_desk', dx - 0.25, dx - 0.18, fl + 0.92, fl + 1.2, -0.55, -0.05, 'screen')          # driver displays
        m.box('SHELL_desk', dx - 0.26, dx - 0.25, fl + 0.96, fl + 1.16, -0.52, -0.08, 'green_led')
        m.box('SHELL_desk', dx - 0.25, dx - 0.18, fl + 0.92, fl + 1.2, 0.05, 0.45, 'screen')
        m.box('SHELL_desk', dx - 0.26, dx - 0.25, fl + 0.96, fl + 1.16, 0.08, 0.42, 'led')
        # working IR controls: drag the levers, press the horn
        m.box('THROTTLE_1_TL_0.12_X', dx - 0.5, dx - 0.44, fl + 0.92, fl + 1.12, -0.42, -0.36, 'steel')
        m.box('THROTTLE_1_TL_0.12_X', dx - 0.52, dx - 0.42, fl + 1.12, fl + 1.17, -0.44, -0.34, 'black')
        m.box('TRAIN_BRAKE_1_TL_0.12_X', dx - 0.5, dx - 0.44, fl + 0.92, fl + 1.1, -0.26, -0.2, 'steel')
        m.box('TRAIN_BRAKE_1_TL_0.12_X', dx - 0.52, dx - 0.42, fl + 1.1, fl + 1.15, -0.28, -0.18, 'tail')
        m.box('REVERSER_1_TL_0.08_X', dx - 0.5, dx - 0.45, fl + 0.92, fl + 1.02, 0.12, 0.17, 'yellow')
        m.box('HORN_CONTROL_1_PRESS', dx - 0.5, dx - 0.42, fl + 0.92, fl + 0.96, 0.28, 0.36, 'tail')
        m.box('BELL_CONTROL_1_PRESS', dx - 0.5, dx - 0.42, fl + 0.92, fl + 0.96, 0.4, 0.48, 'yellow')
        # driver's seat (sit in it) on a pedestal
        sx = dx - 1.05
        m.box('SHELL_cabseat', sx - 0.12, sx + 0.12, fl, fl + 0.42, -0.42, -0.18, 'steel_dk')
        self.seat(sx - 0.25, sx + 0.25, fl + 0.42, fl + 0.52, -0.55, -0.05, 'seat_dk')
        m.box('SHELL_cabseat', sx - 0.32, sx - 0.24, fl + 0.5, fl + 1.25, -0.55, -0.05, 'seat_dk')
        # second (instructor) seat
        self.seat(sx - 0.25, sx + 0.25, fl + 0.42, fl + 0.52, 0.12, 0.55, 'seat_dk')
        m.box('SHELL_cabseat', sx - 0.12, sx + 0.12, fl, fl + 0.42, 0.22, 0.45, 'steel_dk')


    # --- interior furniture (each piece is a little model of the real thing) -------------------------------------
    def lavatory(self, xa, xb, zs, accessible=True):
        """enclosed toilet room against one side wall between xa..xb: walls, sliding door, toilet, basin, mirror"""
        m, fl, top = self.m, self.floor, self.ceil(0)
        zw = self.hw - self.wall
        zi = zs * (zw - (1.55 if accessible else 1.05))           # inner wall of the room (towards the aisle)
        zo = zs * (zw - 0.015)
        g = 'SHELL_wc'
        z0, z1 = sorted((zi, zi + zs * 0.06))
        dw = 0.85 if accessible else 0.65                          # door width, centred on the room
        xm = (xa + xb) / 2
        m.box(g, xa, xm - dw / 2, fl, top, z0, z1, 'wall_in')       # aisle-side wall with the door gap
        m.box(g, xm + dw / 2, xb, fl, top, z0, z1, 'wall_in')
        m.box(g, xm - dw / 2, xm + dw / 2, fl + 2.05, top, z0, z1, 'wall_in')
        for x in (xa, xb - 0.06):                                   # end walls
            m.box(g, x, x + 0.06, fl, top, *sorted((zi, zo)), 'wall_in')
        self.n_door += 1
        m.box('DOOR_%d_CG_W%d_TOGGLE_TL_%.2f_X' % (self.n_door, self.n_door, -dw + 0.05), xm - dw / 2, xm + dw / 2, fl, fl + 2.05,
              z0 - zs * 0.03, z1 - zs * 0.03, 'wall_in')
        m.box('SHELL_wcsign', xm - 0.12, xm + 0.12, fl + 2.12, fl + 2.32, *sorted((z0 - zs * 0.035, z0 - zs * 0.04)), 'blue_sign')
        m.box('SHELL_wcsign', xm - 0.05, xm + 0.05, fl + 2.15, fl + 2.29, *sorted((z0 - zs * 0.04, z0 - zs * 0.045)), 'white')
        m.box('SHELL_wcsign', xm + 0.16, xm + 0.24, fl + 2.18, fl + 2.26, *sorted((z0 - zs * 0.035, z0 - zs * 0.04)), 'green_led')  # vacant light
        # inside: floor, toilet (bowl, seat, cistern), basin + tap, mirror, dryer, grab rails, baby table
        m.box(g, xa + 0.06, xb - 0.06, fl, fl + 0.01, *sorted((zi, zo)), 'floor_lt')
        tx = xb - 0.5
        zt = zs * (zw - 0.35)
        m.box(g, tx - 0.22, tx + 0.22, fl, fl + 0.38, zt - 0.2, zt + 0.2, 'porcelain')
        m.box(g, tx - 0.24, tx + 0.24, fl + 0.38, fl + 0.43, zt - 0.22, zt + 0.22, 'steel_lt')
        m.box(g, tx - 0.2, tx + 0.2, fl + 0.43, fl + 0.85, *sorted((zo - zs * 0.02, zo - zs * 0.2)), 'porcelain')
        m.box(g, tx - 0.06, tx + 0.06, fl + 0.85, fl + 0.88, *sorted((zo - zs * 0.08, zo - zs * 0.14)), 'steel')   # flush button
        bx = xa + 0.45
        m.box(g, bx - 0.25, bx + 0.25, fl + 0.78, fl + 0.88, *sorted((zo, zo - zs * 0.45)), 'porcelain')           # basin
        m.box(g, bx - 0.03, bx + 0.03, fl + 0.88, fl + 1.0, *sorted((zo - zs * 0.04, zo - zs * 0.08)), 'steel')    # tap
        m.box(g, bx - 0.3, bx + 0.3, fl + 1.15, fl + 1.75, *sorted((zo - zs * 0.005, zo - zs * 0.015)), 'mirror')
        m.box(g, bx + 0.32, bx + 0.48, fl + 1.1, fl + 1.35, *sorted((zo, zo - zs * 0.12)), 'steel_lt')             # hand dryer
        if accessible:
            m.box(g, tx - 0.3, tx + 0.3, fl + 0.75, fl + 0.79, *sorted((zt + zs * 0.32, zt + zs * 0.36)), 'yellow') # grab rail
            m.box(g, xa + 0.06, xa + 0.1, fl + 0.9, fl + 1.5, *sorted((zi + zs * 0.2, zi + zs * 0.8)), 'steel_lt')   # baby table (folded)
        m.box(g, xa + 0.06, xb - 0.06, top - 0.05, top, *sorted((zi, zo)), 'ceiling')
        m.box(g, xm - 0.2, xm + 0.2, top - 0.07, top - 0.05, *sorted((zi + zs * 0.3, zi + zs * 0.6)), 'lamp')

    def vending(self, x, zs):
        """drinks vending machine against a wall"""
        m, fl = self.m, self.floor
        zo = zs * (self.hw - self.wall - 0.015)
        zi = zo - zs * 0.62
        m.box('SHELL_vend', x - 0.45, x + 0.45, fl, fl + 1.85, *sorted((zo, zi)), 'vend')
        m.box('SHELL_vend', x - 0.38, x + 0.2, fl + 0.8, fl + 1.7, *sorted((zi, zi - zs * 0.01)), 'glass_hi')       # window
        for r in range(3):
            for k in range(5):
                cx = x - 0.32 + k * 0.1
                cy = fl + 0.9 + r * 0.27
                m.box('SHELL_vend', cx, cx + 0.06, cy, cy + 0.16, *sorted((zi - zs * 0.005, zi + zs * 0.08)), ('led', 'green_led', 'white', 'yellow', 'tail')[(k + r) % 5])
        m.box('SHELL_vend', x + 0.25, x + 0.4, fl + 1.2, fl + 1.6, *sorted((zi, zi - zs * 0.015)), 'vend_dk')     # buttons / pay
        m.box('SHELL_vend', x - 0.35, x + 0.35, fl + 0.2, fl + 0.4, *sorted((zi, zi - zs * 0.02)), 'vend_dk')     # tray

    def wheelchair_space(self, xa, xb, zs):
        """accessible area: blue floor marking, fold-up seats, padded backboard, help button"""
        m, fl = self.m, self.floor
        zo = zs * (self.hw - self.wall - 0.015)
        m.box('SHELL_pmr', xa, xb, fl, fl + 0.006, *sorted((zo, zo - zs * 1.0)), 'blue_sign')
        m.box('SHELL_pmr', (xa + xb) / 2 - 0.15, (xa + xb) / 2 + 0.15, fl + 0.006, fl + 0.008, *sorted((zo - zs * 0.35, zo - zs * 0.65)), 'white')
        m.box('SHELL_pmr', xa + 0.1, xb - 0.1, fl + 0.5, fl + 1.2, *sorted((zo, zo - zs * 0.08)), 'seat_dk')        # backboard
        m.box('SHELL_pmr', xa + 0.1, xb - 0.1, fl + 0.85, fl + 0.89, *sorted((zo - zs * 0.08, zo - zs * 0.14)), 'yellow')  # rail
        m.box('SHELL_pmr', xb - 0.12, xb - 0.02, fl + 0.9, fl + 1.1, *sorted((zo, zo - zs * 0.03)), 'tail')          # help button
        for k in range(2):
            sx = xa + 0.2 + k * 0.5
            m.box('SHELL_pmr', sx, sx + 0.45, fl + 0.45, fl + 0.95, *sorted((zo - zs * 0.08, zo - zs * 0.14)), 'seat_pri')   # tip-up seats

    def safety_kit(self, x, zs):
        """fire extinguisher in its holder + emergency intercom"""
        m, fl = self.m, self.floor
        zo = zs * (self.hw - self.wall - 0.015)
        m.box('SHELL_safety', x - 0.08, x + 0.08, fl + 0.15, fl + 0.65, *sorted((zo - zs * 0.02, zo - zs * 0.18)), 'red')
        m.box('SHELL_safety', x - 0.03, x + 0.03, fl + 0.65, fl + 0.75, *sorted((zo - zs * 0.08, zo - zs * 0.12)), 'black')
        m.box('SHELL_safety', x - 0.1, x + 0.1, fl + 1.35, fl + 1.6, *sorted((zo, zo - zs * 0.03)), 'yellow')        # intercom
        m.box('SHELL_safety', x - 0.04, x + 0.04, fl + 1.42, fl + 1.5, *sorted((zo - zs * 0.03, zo - zs * 0.04)), 'tail')

    def route_map(self, x, zs, w=1.0):
        """line map above a door / window: white board with a coloured line and station dots"""
        m = self.m
        top = self.ceil(0)
        zo = zs * (self.hw - self.wall - 0.01)
        m.box('SHELL_map', x - w / 2, x + w / 2, top - 0.42, top - 0.22, *sorted((zo, zo - zs * 0.015)), 'map_bg')
        m.box('SHELL_map', x - w / 2 + 0.05, x + w / 2 - 0.05, top - 0.33, top - 0.31, *sorted((zo - zs * 0.015, zo - zs * 0.02)), 'map_line')
        for k in range(7):
            sx = x - w / 2 + 0.08 + k * (w - 0.16) / 6
            m.box('SHELL_map', sx - 0.02, sx + 0.02, top - 0.345, top - 0.295, *sorted((zo - zs * 0.015, zo - zs * 0.025)), 'white' if k % 3 else 'tail')

    def ceiling_kit(self, xa, xb):
        """AC diffusers + speaker grilles + CCTV domes along the ceiling"""
        m = self.m
        top = self.ceil(0)
        x = xa + 0.8
        k = 0
        while x < xb - 0.5:
            m.box('SHELL_ceil', x - 0.35, x + 0.35, top - 0.06, top - 0.04, -0.18, 0.18, 'steel_lt')        # AC slot diffuser
            for j in range(4):
                m.box('SHELL_ceil', x - 0.3 + j * 0.18, x - 0.24 + j * 0.18, top - 0.065, top - 0.06, -0.15, 0.15, 'frame')
            if k % 3 == 1:
                m.box('SHELL_ceil', x + 0.6, x + 0.75, top - 0.08, top - 0.04, -0.07, 0.07, 'black')            # CCTV dome
            if k % 2 == 0:
                m.box('SHELL_ceil', x - 0.9, x - 0.75, top - 0.05, top - 0.04, 0.75, 0.9, 'frame')              # speaker
            x += 2.0
            k += 1

    def luggage_rack(self, xa, xb, zs):
        """floor-standing luggage stack: three shelves in a steel frame against the wall"""
        m, fl = self.m, self.floor
        zo = zs * (self.hw - self.wall - 0.015)
        zi = zo - zs * 0.55
        for x in (xa, xb - 0.05):
            m.box('SHELL_lug', x, x + 0.05, fl, fl + 1.7, *sorted((zo, zi)), 'steel_lt')
        for k in range(3):
            y = fl + 0.15 + k * 0.6
            m.box('SHELL_lug', xa, xb, y, y + 0.04, *sorted((zo, zi)), 'steel_lt')
        m.box('SHELL_lug', xa + 0.1, xb - 0.15, fl + 0.19, fl + 0.65, *sorted((zo - zs * 0.05, zi + zs * 0.1)), 'vend_dk')   # a suitcase

    def washbasins(self, xa, xb, zs):
        """open washbasin alcove: two basins, big mirror, soap + dryers"""
        m, fl = self.m, self.floor
        zo = zs * (self.hw - self.wall - 0.015)
        m.box('SHELL_wash', xa, xb, fl, fl + 0.8, *sorted((zo, zo - zs * 0.5)), 'wall_in')
        m.box('SHELL_wash', xa, xb, fl + 0.8, fl + 0.86, *sorted((zo, zo - zs * 0.52)), 'porcelain')
        m.box('SHELL_wash', xa + 0.05, xb - 0.05, fl + 1.1, fl + 1.8, *sorted((zo - zs * 0.005, zo - zs * 0.015)), 'mirror')
        m.box('SHELL_wash', (xa + xb) / 2 - 0.03, (xa + xb) / 2 + 0.03, fl + 0.86, fl + 0.98, *sorted((zo - zs * 0.05, zo - zs * 0.09)), 'steel')

    def speed_display(self, x, zs=1):
        """the famous speed readout over the end door (red LED digits on black)"""
        m, top = self.m, self.ceil(0)
        m.box('SHELL_speed', x, x + 0.04, top - 0.45, top - 0.15, -0.6, 0.6, 'screen')
        self.sign('speed', -0.58, 0.58, top - 0.43, top - 0.17, x + 0.045, nx=1)

    def aisle_screen(self, x):
        """small double-sided screen hanging over the aisle"""
        m, top = self.m, self.ceil(0)
        m.box('SHELL_screen', x - 0.02, x + 0.02, top - 0.2, top, -0.03, 0.03, 'steel_dk')
        m.box('SHELL_screen', x - 0.04, x + 0.04, top - 0.45, top - 0.2, -0.32, 0.32, 'screen')
        self.sign('aisle', -0.3, 0.3, top - 0.43, top - 0.22, x + 0.045, nx=1)
        self.sign('aisle', -0.3, 0.3, top - 0.43, top - 0.22, x - 0.045, nx=-1)

    def rows_block(self, xa, xb, base, pitch, rows, bays, col='seat'):
        """transverse seats between xa..xb standing on a floor at `base`"""
        m = self.m
        zw = self.hw - self.wall
        sw = 0.46
        zl = [-zw + 0.045 + k * sw for k in range(rows[0])]
        zr = [zw - 0.045 - (k + 1) * sw for k in range(rows[1])]
        x, row = xa + 0.5, 0
        while x + 0.3 < xb:
            sgn = -1 if bays and row % 2 else 1
            for zs in zl + zr:
                z0, z1 = zs + 0.02, zs + sw - 0.02
                m.box('SHELL_seatbase', x - 0.18, x + 0.18, base, base + 0.38, z0 + 0.06, z1 - 0.06, 'steel_dk')
                self.seat(*sorted((x - sgn * 0.22, x + sgn * 0.25)), base + 0.38, base + 0.5, z0, z1, col)
                m.box('SHELL_seatback', *sorted((x - sgn * 0.32, x - sgn * 0.2)), base + 0.45, base + 1.1, z0, z1, col)
            x += pitch
            row += 1

    def floor_pattern(self, xa, xb, zw, fl):
        """aisle runner / pattern (N700S: Kyoto sand-garden ripples)"""
        if isinstance(self, N700S):
            x = xa + 0.2
            while x < xb:
                self.m.slab('SHELL_floor', x, x + 0.04, fl + 0.004, fl + 0.006, zw - 0.05, 'floor_lt')
                x += 0.22
        elif self.layout in ('hs', 'coach'):
            self.m.slab('SHELL_floor', xa, xb, fl + 0.004, fl + 0.007, 0.36, 'carpet')

    def plinth(self, xa, xb, h=0.3):
        """raised floor over a bogie with a step down at each end (ALWEG / Hitachi monorails)"""
        m, fl = self.m, self.floor
        zw = self.hw - self.wall
        for g in ('FLOOR', 'SHELL_plinth'):
            m.slab(g, xa, xb, fl, fl + h, zw, 'floor')
            for x in (xa - 0.3, xb):
                m.slab(g, x, x + 0.3, fl, fl + h / 2, zw, 'floor_lt')
        m.slab('SHELL_plinth', xa - 0.01, xa + 0.02, fl + h - 0.03, fl + h, zw, 'yellow')
        m.slab('SHELL_plinth', xb - 0.02, xb + 0.01, fl + h - 0.03, fl + h, zw, 'yellow')

    def interior_seattle(self, xa, xb, zw, fl, top):
        """1962 ALWEG: forward-facing red seats on raised platforms over each bogie, benches by the doors"""
        for t in (self.trucks, -self.trucks):
            a, b = max(xa, t - 1.9), min(xb, t + 1.9)
            if self.front and t > 0:
                b = min(b, self.x1 - self.nose_len - 0.2)
            if b - a > 1.2:
                self.plinth(a, b)
                self.rows_block(a, b, fl + 0.3, 0.85, (2, 2), False)
                self.reserved.append((a - 0.3, b + 0.3))
        self.interior_metro(xa, xb, zw, fl, top)

    def interior_tokyo(self, xa, xb, zw, fl, top):
        """Tokyo Monorail: 4-seat facing bays on plinths over the bogies, long benches by the doors"""
        for t in (self.trucks, -self.trucks):
            a, b = max(xa, t - 1.7), min(xb, t + 1.7)
            if self.front and t > 0:
                b = min(b, self.x1 - self.nose_len - 0.2)
            if b - a > 1.2:
                self.plinth(a, b, 0.25)
                self.rows_block(a, b, fl + 0.25, 0.95, (2, 2), True)
                self.reserved.append((a - 0.3, b + 0.3))
        self.interior_metro(xa, xb, zw, fl, top)

    def sealed_nose(self, nose0, zw, fl):
        """driverless (Las Vegas / Innovia): no cab - a low railing at the front and a bench facing the big window"""
        m = self.m
        rx = nose0 + self.nose_len * 0.25
        m.box('SHELL_front', rx, rx + 0.05, fl, fl + 1.0, -zw + 0.1, zw - 0.1, 'pole')
        m.box('SHELL_front', rx - 0.03, rx + 0.08, fl + 1.0, fl + 1.06, -zw + 0.1, zw - 0.1, 'pole')
        m.box('SHELL_front', rx + 0.05, rx + 0.5, fl, fl + 0.85, -zw + 0.15, zw - 0.15, 'frame')            # locked equipment cover
        for zs in (1, -1):                                                                                  # front-facing seats
            z0, z1 = sorted((zs * 0.1, zs * (zw - 0.1)))
            m.box('SHELL_front', rx - 0.95, rx - 0.5, fl, fl + 0.4, z0, z1, 'seat_dk')
            self.seat(rx - 0.95, rx - 0.5, fl + 0.4, fl + 0.5, z0, z1)
            m.box('SHELL_front', rx - 1.05, rx - 0.95, fl + 0.45, fl + 1.1, z0, z1, 'seat')

    def lounge(self, nose0, zw, fl):
        """ICE 3 lounge: the driver behind a glass wall, six seats looking straight down the line"""
        m = self.m
        top = self.ceil(0)
        px = nose0 + 0.6
        m.sym('SHELL_lounge', px, px + 0.06, fl, top, 0.06, zw, 'glass')                                   # glass wall
        m.box('SHELL_lounge', px - 0.02, px + 0.08, fl, fl + 0.12, -zw, zw, 'frame')
        m.box('SHELL_lounge', px - 0.02, px + 0.08, top - 0.12, top, -zw, zw, 'frame')
        m.box('SHELL_lounge', px - 0.02, px + 0.08, fl, top, -0.06, 0.06, 'frame')
        for row, rx in enumerate((px - 1.0, px - 2.1)):
            for z0, z1 in ((-zw + 0.1, -zw + 0.6), (-zw + 0.62, -zw + 1.12), (zw - 0.6, zw - 0.1)):
                m.box('SHELL_lounge', rx - 0.15, rx + 0.15, fl, fl + 0.4, z0 + 0.06, z1 - 0.06, 'steel_dk')
                self.seat(rx - 0.22, rx + 0.28, fl + 0.4, fl + 0.52, z0, z1, 'seat')
                m.box('SHELL_lounge', rx - 0.34, rx - 0.22, fl + 0.45, fl + 1.2, z0, z1, 'seat')
                m.box('SHELL_lounge', rx - 0.35, rx - 0.21, fl + 1.0, fl + 1.22, z0 + 0.05, z1 - 0.05, 'headrest')
        self.reserved.append((px - 2.6, px))
        self.cab(nose0 + 0.9, zw, fl)

    def cab_u(self):
        """where along the nose the desk goes (just behind the windscreen)"""
        for k in range(100):
            u = k / 100
            if self.nose_glass(u, self.floor + 1.4) or self.nose_glass(u, self.floor + 1.0):
                return max(0.02, u - 0.02)
        return 0.15


    # --- build
    def sign(self, kind, x0, x1, y0, y1, z, nz=0, nx=0):
        """a live surface IR Extras draws on in-game: dest = outside LED board, door/aisle/end/speed = inside screens.
        The rect lies in a plane facing +z/-z (nz) or +x/-x (nx); z (or x for nx) is the plane position."""
        self.signs.append({'kind': kind, 'x0': round(x0, 3), 'x1': round(x1, 3), 'y0': round(y0, 3), 'y1': round(y1, 3),
                           'p': round(z, 3), 'nz': nz, 'nx': nx})

    def build(self, name, smooth=False):
        m = Model(name, dict(BASE, **self.pal))
        self.m = m
        self.signs = []
        self.smooth = smooth
        if smooth:
            self.smooth_shell()
        else:
            m.loft('SHELL', self.x0, self.x1, self.skirt_bot, self.roof, self.dx, self.dy, self.field)
        # IR wants a FRAME part: the floor pan (hidden inside the body)
        m.slab('FRAME', self.x0 + 0.3, self.x1 - (self.nose_len + 0.2 if self.front else 0.3), self.floor - 0.12, self.floor, self.hw - 0.12, 'frame')
        self.gangways()
        self.running_gear()
        self.roof_kit()
        self.details()
        self.interior()
        return m

    def gangways(self):
        """outer rubber diaphragm: the car's profile, slightly smaller, sticking out 0.15 m so coupled cars close up"""
        m = self.m

        def ring(x, y):
            v = self.surf(self.x0 + 1.0, y)
            if not v:
                return None
            inner = v[2]
            if self.floor - 0.02 < y < self.floor + 2.12:
                inner = max(inner, 0.58)                     # the walkway through to the next car
            return (round(v[0] - 0.1, 3), 'bellows', round(inner, 3))
        lo = self.skirt_bot + 0.15 if self.skirt_bot < self.floor - 0.4 else self.floor - 0.3
        m.loft('SHELL_diaphragm', self.x0 - 0.15, self.x0, lo, self.roof - 0.12, 0.15, 0.1, ring)
        if not self.front:
            m.loft('SHELL_diaphragm', self.x1, self.x1 + 0.15, lo, self.roof - 0.12, 0.15, 0.1, ring)
        for xa, xb in ((self.x0 - 0.16, self.x0 + 0.12),) + (() if self.front else ((self.x1 - 0.12, self.x1 + 0.16),)):
            m.slab('FLOOR', xa, xb, self.floor - 0.03, self.floor, 0.56, 'steel_dk')            # gangway foot plate
            m.slab('SHELL_gangway', xa, xb, self.floor - 0.02, self.floor + 0.005, 0.56, 'steel_dk')

    def running_gear(self):
        m = self.m
        for pos, tx in (('FRONT', self.trucks), ('REAR', -self.trucks)):
            g = 'BOGEY_' + pos
            m.slab(g, tx - 1.25, tx + 1.25, 0.62, 0.82, 0.58, 'bogie')                     # bolster over the beam
            m.sym(g, tx - 1.25, tx + 1.25, -0.85, 0.62, BEAM_HW + 0.10, BEAM_HW + 0.18, 'bogie_dk')  # side frames
            for gx in (tx - 0.85, tx + 0.85):                                                 # guide + stabiliser wheels
                m.sym(g, gx - 0.28, gx + 0.28, -0.42, -0.14, BEAM_HW + 0.0, BEAM_HW + 0.10, 'tyre')
                m.sym(g, gx - 0.22, gx + 0.22, -0.82, -0.6, BEAM_HW + 0.0, BEAM_HW + 0.10, 'tyre')
            for k, wx in ((1, tx + 0.55), (2, tx - 0.55)):
                m.disc('%s_WHEEL_%d' % (g, k), wx, BEAM_TOP + 0.45, -0.30, 0.30, 0.45, 'tyre', hub='steel')

    def roof_kit(self):
        m = self.m
        n = self.roof_units
        for cx in [(-self.L / 2 + self.L * (k + .5) / n) - (0.8 if self.front else 0) for k in range(n)]:
            m.slab('SHELL_roof', cx - 1.1, cx + 1.1, self.roof - 0.05, self.roof + 0.28, 0.72, 'roof_eq')
            m.slab('SHELL_roof', cx - 0.9, cx + 0.9, self.roof + 0.28, self.roof + 0.34, 0.55, 'roof_eq_dk')
            for k in range(-3, 4):
                m.box('SHELL_roof', cx + k * 0.26 - 0.04, cx + k * 0.26 + 0.04, self.roof + 0.28, self.roof + 0.35, -0.5, 0.5, 'frame')

    def details(self):
        m = self.m
        if self.front:
            u, ya, yb, zi, zo = self.light
            x = self.x1 - self.nose_len * (1 - u)
            hw = self.nose_hw(u, (ya + yb) / 2, self.section((ya + yb) / 2)[0])
            m.sym('HEADLIGHT_1', x - 0.1, x + 0.06, ya, yb, hw * zi, hw * zo, 'lamp')
            m.sym('SHELL_lights', x - 0.12, x + 0.04, ya, yb, hw * zo + 0.03, hw * min(0.97, zo + 0.18), 'tail')
        # side destination boards (LED) next to the first door
        d_last = max(self.door_list()) if self.door_list() else 0
        bx = d_last + self.door_w / 2 + 0.25
        if bx + 1.6 > self.x1 - (self.nose_len if self.front else 0.6):
            bx = d_last - self.door_w / 2 - 0.25 - 1.6          # no room past the door: put it on the inward side
        y0 = self.win[1] + 0.02
        m.sym('SHELL_sign', bx, bx + 1.6, y0, y0 + 0.2, self.hw - 0.01, self.hw + 0.012, 'led_bg')
        for zs in (1, -1):
            self.sign('dest', bx + 0.05, bx + 1.55, y0 + 0.025, y0 + 0.175, zs * (self.hw + 0.013), nz=zs)
        # under-floor equipment boxes (seen between the skirts? no - on top of the skirts at the ends: tail lights)
        if not self.rear:
            pass


# ------------------------------------------------------------------------------------------------------------------
class LasVegas(Straddle):
    """Bombardier M-VI (Las Vegas Monorail): white bullet body, wrap-around dark windshield, continuous dark window
    band, navy skirt; real stripe teal + gold."""
    title = 'Las Vegas Monorail M-VI'
    nose_len = 3.3

    def nose_top(self, u): return self.roof - 2.0 * u ** 2.1
    def nose_bot(self, u): return self.skirt_bot + 1.35 * u ** 2.2
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.62 * u * u))
    def nose_glass(self, u, y): return 0.08 < u < 0.80 and 1.45 < y < self.nose_top(u) - 0.12

    pal = {'body': '#EEF0F2', 'roof': '#D9DCE0', 'skirt': '#1E2F6B', 'skirt_dk': '#15214D', 'door': '#E3E6EA', 'nose': '#EEF0F2',
           'st0': '#00A3AD', 'st1': '#00A3AD', 'st2': '#00A3AD', 'st3': '#F2B33D', 'st4': '#F2B33D', 'st5': '#F2B33D', 'seat': '#2B2D31', 'seat_pri': '#3B78C2', 'wall_in': '#F3F3F3', 'floor': '#3A3C40'}
    cab_kind = 'none'                       # driverless: the nose is a big window, no cab
    doors = (-0.6,)
    door_w = 1.7

    def plan(self, xa, xb, zw, fl, top):
        if self.front:
            self.wheelchair_space(-2.6, -1.6, 1)
            self.reserved.append((-2.7, -1.5))

    def window_colour(self, x, y):
        # continuous dark band with thin frames every 1.5 m
        return 'frame' if (x % 1.5) < 0.08 else 'glass'


def win_row(x, pitch, width, off=0.0):
    """True inside a window of a regular row"""
    return ((x - off) % pitch) < width


class Seattle(Straddle):
    """Seattle Center Monorail (ALWEG, 1962): rounded bubble ends, red upper body, ribbed aluminium skirt,
    separate big windows."""
    title = 'Seattle ALWEG Monorail'
    L, L_car = 12.0, 12.0
    roof, eave, nose_len = 3.35, 2.85, 2.3
    win = (1.5, 2.65)
    stripe_y, stripe_h = 1.22, 0.045
    doors = (-2.8, 1.6)
    trucks = 3.4
    light = (0.92, 0.7, 0.9, 0.3, 0.62)
    pal = {'body': '#B5222B', 'roof': '#ECECEC', 'skirt': '#C7CCD1', 'skirt_dk': '#8E959C', 'door': '#A51E27',
           'st0': '#F2F2F2', 'st1': '#F2F2F2', 'st2': '#C7CCD1', 'st3': '#C7CCD1', 'st4': '#F2F2F2', 'st5': '#F2F2F2', 'seat': '#B3262F', 'seat_pri': '#2F5DA8', 'wall_in': '#ECE6DA', 'floor': '#5C5048', 'ceiling': '#F4F0E8'}
    layout = 'seattle'
    

    def nose_top(self, u): return self.roof - 1.15 * u ** 2
    def nose_bot(self, u): return self.skirt_bot + 1.4 * u ** 2.4
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.82 * u * u))
    def nose_glass(self, u, y): return 0.1 < u < 0.74 and 1.45 < y < self.nose_top(u) - 0.1

    def window_colour(self, x, y):
        return 'glass' if win_row(x, 1.3, 1.0) and y > self.win[0] + 0.05 else 'body'

    def body_colour(self, x, y):
        c = Straddle.body_colour(self, x, y)
        if c == 'skirt' and int((y - self.skirt_bot) / 0.15) % 2:
            return 'skirt_dk'                     # the ribbed aluminium skirt
        return c


class Tokyo(Straddle):
    """Tokyo Monorail 10000 series: white body, black front mask on a raked flat nose, blue + sky-blue stripes."""
    title = 'Tokyo Monorail 10000'
    L, L_car = 15.2, 15.0
    hw = 1.45
    roof, eave, nose_len = 3.6, 3.15, 1.9
    win = (1.6, 2.75)
    stripe_y, stripe_h = 1.18, 0.06
    doors = (-4.6, -0.6, 3.4)
    door_w = 1.2
    trucks = 4.6
    light = (0.9, 0.95, 1.15, 0.25, 0.55)
    roof_units = 3
    pal = {'body': '#F4F6F7', 'roof': '#DADFE3', 'skirt': '#D3D8DD', 'skirt_dk': '#9BA3AB', 'door': '#E4E8EB',
           'mask': '#0D1116',
           'st0': '#0068B7', 'st1': '#0068B7', 'st2': '#0068B7', 'st3': '#F4F6F7', 'st4': '#00A0E9', 'st5': '#00A0E9', 'seat': '#2C5AA0', 'seat_pri': '#C8553D', 'wall_in': '#F2F4F5', 'floor': '#6B6F76'}
    layout = 'tokyo'

    def plan(self, xa, xb, zw, fl, top):
        for d in self.door_list():                      # suitcase racks beside every door (airport line)
            for zs in (1, -1):
                self.luggage_rack(d + self.door_w / 2 + 0.15, d + self.door_w / 2 + 0.85, zs)
        if self.front:
            self.wheelchair_space(xa + 0.3, xa + 1.3, -1)

    def nose_top(self, u): return self.roof - 0.75 * u
    def nose_bot(self, u): return self.skirt_bot + 1.2 * u ** 2
    def nose_scale(self, u): return 1 - 0.32 * u * u
    def nose_glass(self, u, y): return u > 0.08 and 1.35 < y < self.nose_top(u) - 0.12

    def nose_colour(self, u, y):
        if self.nose_glass(u, y):
            return 'glass' if 1.6 < y < self.nose_top(u) - 0.3 and u > 0.25 else 'mask'
        return Straddle.nose_colour(self, u, y)

    def window_colour(self, x, y):
        return 'glass' if win_row(x, 1.55, 1.2) else 'body'


class Innovia(Straddle):
    """Bombardier Innovia Monorail 300 (Sao Paulo Line 15 / Riyadh): silver body, long raked angular glass nose."""
    title = 'Innovia Monorail 300'
    L, L_car = 13.6, 12.6
    hw = 1.4
    roof, eave, nose_len = 3.5, 3.1, 3.6
    win = (1.5, 2.85)
    stripe_y, stripe_h = 1.0, 0.05
    doors = (-3.6, 0.6)
    door_w = 1.6
    trucks = 3.8
    light = (0.95, 0.35, 0.55, 0.2, 0.6)
    pal = {'body': '#C9CED4', 'roof': '#B3B9C0', 'skirt': '#3B4048', 'skirt_dk': '#2A2E34', 'door': '#BCC2C9',
           'nose': '#C9CED4',
           'st0': '#2B3A8C', 'st1': '#2B3A8C', 'st2': '#C9CED4', 'st3': '#5F6CC4', 'st4': '#5F6CC4', 'st5': '#5F6CC4', 'seat': '#5F6A78', 'seat_pri': '#E0B220', 'wall_in': '#EEF0F2', 'floor': '#4C5058'}
    cab_kind = 'none'
    priority = 3

    def plan(self, xa, xb, zw, fl, top):
        for zs in (1, -1):                             # wheelchair bays at both car ends
            self.wheelchair_space(xa + 0.2, xa + 1.3, zs)
        self.reserved.append((xa, xa + 1.4))

    def nose_top(self, u): return self.roof - 2.5 * u ** 1.5
    def nose_bot(self, u): return self.skirt_bot + 1.2 * u ** 1.8
    def nose_scale(self, u): return 1 - 0.42 * u ** 1.4
    def nose_glass(self, u, y): return 0.03 < u < 0.82 and 1.3 < y < self.nose_top(u) - 0.08

    def window_colour(self, x, y):
        return 'frame' if (x % 2.1) < 0.12 else 'glass'


class Chongqing(Straddle):
    """Chongqing Rail Transit Line 2/3 straddle monorail: boxy, near-flat face with a split windscreen,
    white upper / green lower."""
    title = 'Chongqing Straddle Monorail'
    L, L_car = 14.8, 13.9
    hw = 1.45
    roof, eave, nose_len = 3.5, 3.25, 1.3
    win = (1.55, 2.8)
    stripe_y, stripe_h = 1.05, 0.06
    doors = (-4.2, -0.4, 3.0)
    door_w = 1.3
    trucks = 4.4
    light = (0.92, 0.95, 1.15, 0.35, 0.7)
    roof_units = 3
    pal = {'body': '#F2F3EF', 'roof': '#D8DAD4', 'skirt': '#1F7A4C', 'skirt_dk': '#155637', 'door': '#E2E4DE',
           'st0': '#1F7A4C', 'st1': '#1F7A4C', 'st2': '#F2F3EF', 'st3': '#E8C21F', 'st4': '#E8C21F', 'st5': '#F2F3EF', 'seat': '#E07A1F', 'seat_pri': '#2F5DA8', 'wall_in': '#F0F1EC', 'floor': '#5E6258', 'led': '#FF3B2F'}


    def nose_top(self, u): return self.roof - 0.3 * u * u
    def nose_bot(self, u): return self.skirt_bot + 0.5 * u * u
    def nose_scale(self, u): return 1 - 0.14 * u * u
    def nose_glass(self, u, y): return u > 0.55 and 1.55 < y < 3.0

    def nose_hw(self, u, y, hw):
        hw = Straddle.nose_hw(self, u, y, hw)
        return hw

    def window_colour(self, x, y):
        return 'glass' if win_row(x, 1.45, 1.15) else 'body'

    def details(self):
        Straddle.details(self)
        if self.front:     # windscreen centre pillar + wipers + destination box over the screen
            m = self.m
            m.box('SHELL_front', self.x1 - 0.06, self.x1 + 0.02, 1.5, 3.05, -0.06, 0.06, 'frame')
            m.box('SHELL_front', self.x1 - 0.12, self.x1 + 0.02, 3.08, 3.36, -0.7, 0.7, 'led_bg')
            m.box('SHELL_front', self.x1 - 0.05, self.x1 + 0.03, 3.14, 3.30, -0.6, 0.6, 'led')


# ------------------------------------------------------------------------------------------------------------------
GW_TOP, GW_WING_BOT, GW_WING, GW_STEM = -0.07, -0.32, 1.14, 0.35    # Pride maglev guideway in train space


class Maglev(Straddle):
    """Maglev on the T guideway: body hovers 12 cm over the deck; the levitation frame wraps round the wings with
    the lift magnets tucked under them (Transrapid principle)."""
    hw = 1.85
    floor = 0.55
    roof, eave = 4.0, 3.35
    skirt_bot = -0.62
    win = (1.55, 2.6)
    stripe_y = 1.1
    pal = {}

    def section(self, y):
        if y > self.roof:
            return None
        hw = self.hw
        if y > self.eave:
            hw -= 1.25 * (y - self.eave) ** 2
        if y < 0.05:                              # levitation frame below the floor pan
            hw -= 0.06
            return (hw, GW_WING + 0.06) if y > GW_WING_BOT - 0.04 else (hw, 0.80)
        return hw, 0.0

    def body_colour(self, x, y):
        if y < 0.05:
            return 'skirt_dk' if y < GW_WING_BOT - 0.04 else 'skirt'
        return Straddle.body_colour(self, x, y)

    def nose_colour(self, u, y):
        if y < 0.05:
            return 'skirt'
        return Straddle.nose_colour(self, u, y)

    def running_gear(self):
        """levitation + guidance magnets in sections along the car; the 'bogies' IR steers are the magnet frames"""
        m = self.m
        for pos, tx in (('FRONT', self.trucks), ('REAR', -self.trucks)):
            g = 'BOGEY_' + pos
            m.sym(g, tx - 2.2, tx + 2.2, GW_WING_BOT - 0.28, GW_WING_BOT - 0.06, 0.82, GW_WING + 0.02, 'coil')     # lift magnets
            m.sym(g, tx - 2.2, tx + 2.2, GW_WING_BOT - 0.04, GW_TOP - 0.02, GW_WING + 0.03, GW_WING + 0.08, 'coil')  # guidance
            for k in range(-4, 5):                                                                            # coil packs
                m.sym(g, tx + k * 0.48 - 0.05, tx + k * 0.48 + 0.05, GW_WING_BOT - 0.3, GW_WING_BOT - 0.05, 0.84, GW_WING, 'coil_glow')
            m.sym(g, tx - 0.35, tx + 0.35, GW_TOP + 0.02, 0.05, 0.5, 0.75, 'tyre')                             # landing skid

    def roof_kit(self):
        m = self.m
        m.slab('SHELL_roof', self.x0 + 1.5, self.x1 - (self.nose_len + 0.5 if self.front else 1.5), self.roof - 0.02, self.roof + 0.05, 0.5, 'roof')


class Transrapid(Maglev):
    """Shanghai Maglev (Transrapid SMT): white body, rounded snout, blue + grey stripes, separate windows."""
    title = 'Shanghai Transrapid SMT'
    L, L_car = 27.0, 24.8
    nose_len = 5.0
    trucks = 7.5
    doors = (-9.5,)
    door_w = 1.2
    stripe_y, stripe_h = 1.1, 0.07
    light = (0.9, 0.7, 0.95, 0.3, 0.6)
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = {'body': '#F5F6F7', 'roof': '#E3E6E9', 'skirt': '#9AA2AA', 'skirt_dk': '#5D646B', 'door': '#E6E9EC',
           'coil_glow': '#8FD6FF',
           'st0': '#2A4C9B', 'st1': '#2A4C9B', 'st2': '#F5F6F7', 'st3': '#8D96A0', 'st4': '#8D96A0', 'st5': '#8D96A0', 'seat': '#4E6A8E', 'wall_in': '#EEF1F4', 'floor': '#596171', 'led': '#FF3B2F'}
    rows, pitch = (3, 3), 0.95

    def __init__(self, front=True, rear=False):
        Maglev.__init__(self, front, rear)
        if front:                                      # the VIP end: 2+2 in facing bays with tables
            self.rows, self.pitch, self.bays = (2, 2), 1.1, True

    def plan(self, xa, xb, zw, fl, top):
        for zs in (1, -1):
            self.luggage_rack(xa + 0.2, xa + 1.1, zs)
        self.reserved.append((xa, xa + 1.2))
        self.speed_display(xa + 0.1, 1)

    def nose_top(self, u): return self.roof - 2.6 * u ** 2.0
    def nose_bot(self, u): return self.skirt_bot + 1.0 * u ** 2
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.78 * u * u))
    def nose_glass(self, u, y): return 0.18 < u < 0.62 and self.nose_top(u) - 0.85 < y < self.nose_top(u) - 0.08

    def window_colour(self, x, y):
        return 'glass' if win_row(x, 1.5, 1.05) else 'body'


class L0(Maglev):
    """JR Central L0 series (Chuo Shinkansen): 15 m pointed aero nose, white with a blue band, small windows."""
    title = 'JR Central L0 Series'
    L, L_car = 28.0, 24.3
    hw = 1.45
    roof, eave = 3.35, 2.75
    nose_len = 15.0
    trucks = 6.5
    doors = (-11.6,)
    door_w = 0.9
    win = (1.6, 2.15)
    stripe_y, stripe_h = 1.35, 0.06
    light = (0.62, 0.95, 1.1, 0.35, 0.8)
    roof_units = 0
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = {'body': '#F7F8F9', 'roof': '#ECEFF1', 'skirt': '#B4BBC2', 'skirt_dk': '#6F7880', 'door': '#E8EBEE',
           'nose': '#F7F8F9', 'coil_glow': '#6FC4FF',
           'st0': '#0F4C9C', 'st1': '#0F4C9C', 'st2': '#0F4C9C', 'st3': '#0F4C9C', 'st4': '#0F4C9C', 'st5': '#0F4C9C', 'seat': '#5B6B82', 'wall_in': '#F4F5F6', 'floor': '#6A6F78'}

    def plan(self, xa, xb, zw, fl, top):
        self.speed_display(xa + 0.1, 1)

    def section(self, y):
        sec = Maglev.section(self, y)
        if sec and y > self.eave:
            return max(0.2, self.hw - 2.2 * (y - self.eave) ** 2), sec[1]
        return sec

    def nose_top(self, u): return self.roof - (self.roof - 0.75) * (u ** 1.15)
    def nose_bot(self, u): return self.skirt_bot + (0.0 if u < 0.6 else 1.6 * (u - 0.6) ** 1.2)

    def nose_hw(self, u, y, hw):
        if y < 0.05 and u < 0.6:              # levitation frame stays full width over the front magnets
            return hw
        return Maglev.nose_hw(self, u, y, hw)
    def nose_scale(self, u): return max(0.06, 1 - 0.92 * u ** 1.7)
    def nose_glass(self, u, y): return 0.3 < u < 0.42 and self.nose_top(u) - 0.35 < y < self.nose_top(u) - 0.06

    def nose_colour(self, u, y):
        if self.nose_glass(u, y):
            return 'glass'
        if y < 0.05:
            return 'skirt'
        if u < 0.85 and self.stripe_y < y < self.stripe_y + 0.36 + 0.5 * u:   # the blue band sweeps up the nose
            return 'st%d' % min(5, int((y - self.stripe_y) / ((0.36 + 0.5 * u) / 6)))
        return 'nose'

    def window_colour(self, x, y):
        return 'glass' if win_row(x, 1.0, 0.45) else 'body'


# ------------------------------------------------------------------------------------------------------------------
GAUGE_HALF = 0.7175


def rail_bogie(m, g, tx, axles, base=2.5, r=0.43, frame='bogie', wheel='steel_dk'):
    """conventional bogie on standard gauge: side frames outside the wheels, an axle box per axle, IR wheel groups"""
    m.sym(g, tx - base / 2 - 0.45, tx + base / 2 + 0.45, r - 0.12, r + 0.32, 0.86, 1.02, frame)
    m.slab(g, tx - 0.5, tx + 0.5, r + 0.2, r + 0.42, 1.0, frame)                                   # bolster
    m.sym(g, tx - 0.35, tx + 0.35, r - 0.35, r + 0.05, 0.93, 1.04, 'bogie_dk')                     # springs / damper
    for k in range(axles):
        ax = tx - base / 2 + base * k / max(1, axles - 1) if axles > 1 else tx
        w = '%s_WHEEL_%d' % (g, k + 1)
        for zs in (1, -1):
            z0, z1 = sorted((zs * (GAUGE_HALF - 0.06), zs * (GAUGE_HALF + 0.07)))
            m.disc(w, ax, r, z0, z1, r, wheel, hub='steel')
        m.box(w, ax - 0.06, ax + 0.06, r - 0.06, r + 0.06, -0.86, 0.86, 'steel_dk')                 # axle
        for bz in (0.28, -0.28):                                                                   # brake discs on the axle
            m.disc(w, ax, r, bz - 0.02, bz + 0.02, r * 0.7, 'steel')
        m.sym(g, ax - 0.16, ax + 0.16, r - 0.14, r + 0.14, 0.82, 1.05, 'bogie_dk')                 # axle box
        m.sym(g, ax - 0.13, ax + 0.13, r + 0.14, r + 0.2, 0.86, 1.0, 'yellow')                     # axle-box cover cap
        for k2 in range(4):                                                                         # coil spring stack
            m.sym(g, ax - 0.1, ax + 0.1, r + 0.2 + k2 * 0.05, r + 0.23 + k2 * 0.05, 0.88, 0.98, 'steel_dk')
    # air springs under the bolster, yaw dampers, traction motor, brake callipers
    m.sym(g, tx - 0.22, tx + 0.22, r + 0.42, r + 0.62, 0.72, 0.98, 'tyre')
    m.line(g, (tx - 0.9, r + 0.1, 1.08), (tx - 0.1, r + 0.45, 1.08), 0.06, 'yellow')
    m.line(g, (tx - 0.9, r + 0.1, -1.08), (tx - 0.1, r + 0.45, -1.08), 0.06, 'yellow')
    if axles >= 2:
        m.box(g, tx - 0.35, tx + 0.35, r - 0.15, r + 0.2, -0.45, 0.45, 'bogie_dk')                # traction motor
    for k in range(axles):
        ax = tx - base / 2 + base * k / max(1, axles - 1) if axles > 1 else tx
        m.sym(g, ax - 0.06, ax + 0.06, r - 0.05, r + 0.12, 0.2, 0.36, 'steel_dk')                 # disc brake callipers


WIRE_H = 5.72          # contact wire height above the rail top (catenary_wire block at rail+6 draws its wire at +0.125)


def pantograph(m, x, top, kind='single', g='SHELL_pantograph', reach=WIRE_H):
    """raised pantograph whose carbon strips touch the contact wire at `reach` above the rail"""
    m.slab(g, x - 1.0, x + 1.0, top, top + 0.12, 0.55, 'roof_eq_dk')                               # base frame
    for zz in (-0.4, 0.4):
        for xx in (x - 0.8, x + 0.8):
            m.box(g, xx - 0.07, xx + 0.07, top - 0.02, top + 0.22, zz - 0.07, zz + 0.07, 'white')   # insulators
    t, h = 0.06, top + 0.22
    hy = reach - 0.1                                                                                # pan head centre
    if kind == 'single':                                                                            # Faiveley single arm
        knee = (x + 0.55, h + (hy - h) * 0.45)
        for zz in (-0.25, 0.25):
            m.line(g, (x - 0.75, h, zz), (knee[0], knee[1], zz * 0.4), t, 'steel_dk')                 # lower arm (A-frame)
        m.line(g, (knee[0], knee[1], 0), (x - 0.5, hy, 0), t, 'steel_dk')                           # upper arm
        m.line(g, (x - 0.75, h + 0.05, 0), (knee[0] - 0.2, knee[1] - 0.15, 0), t * 0.6, 'steel')   # push rod
        m.box(g, x - 0.85, x - 0.65, h - 0.02, h + 0.14, -0.2, 0.2, 'roof_eq_dk')                   # air-spring drive
        head = x - 0.5
    else:                                                                                           # diamond (GG1 era)
        my = (h + hy) / 2
        for zz in (-0.35, 0.35):
            bot, left, right, topv = (x, h + 0.04), (x - 0.75, my), (x + 0.75, my), (x, hy)
            for p0, p1 in ((bot, left), (bot, right), (left, topv), (right, topv)):
                m.line(g, (p0[0], p0[1], zz), (p1[0], p1[1], zz), t, 'steel_dk')
        for xx, yy in ((x - 0.75, my), (x + 0.75, my)):
            m.box(g, xx - 0.04, xx + 0.04, yy - 0.04, yy + 0.04, -0.4, 0.4, 'steel_dk')             # knee cross-shafts
        m.box(g, x - 0.06, x + 0.06, h - 0.02, h + 0.1, -0.4, 0.4, 'steel_dk')                      # bottom pivot shaft
        head = x
    # pan head: frame across the roof + two carbon collector strips with turned-down horns
    m.box(g, head - 0.04, head + 0.04, hy - 0.06, hy, -0.7, 0.7, 'steel_dk')
    for sx in (-0.13, 0.13):
        m.box(g, head + sx - 0.035, head + sx + 0.035, hy, hy + 0.06, -0.62, 0.62, 'black')         # carbon strips
        for zs in (1, -1):
            m.line(g, (head + sx, hy + 0.03, zs * 0.62), (head + sx, hy - 0.12, zs * 0.86), 0.04, 'steel_dk')  # horns

def underfloor(m, xa, xb, y0, y1, hw, seed=0):
    """equipment slung under the floor between the bogies: converter boxes, compressor, air tanks"""
    x = xa
    k = seed
    while x < xb - 0.8:
        w = 1.2 + 0.6 * ((k * 7) % 3)
        x1 = min(xb, x + w)
        if k % 3 == 2:                                     # air reservoir tanks (two cylinders)
            for zc in (-0.45, 0.45):
                m.box('FRAME', x + 0.05, x1 - 0.05, y0 + 0.1, y1 - 0.05, zc - 0.22, zc + 0.22, 'steel_dk')
        else:
            m.box('FRAME', x, x1, y0, y1, -hw + 0.18, hw - 0.18, 'bogie' if k % 2 else 'bogie_dk')
            for g in range(int((x1 - x) / 0.25)):         # ventilation grille ribs
                gx = x + 0.12 + g * 0.25
                m.sym('FRAME', gx, gx + 0.05, y0 + 0.08, y1 - 0.08, hw - 0.2, hw - 0.17, 'frame')
        x = x1 + 0.25
        k += 1


def coupler(m, xe, sx, y, g='FRAME'):
    """automatic coupler head + draft gear + brake hoses at a car end (sx = +1 front end, -1 rear end)"""
    m.box(g, *sorted((xe, xe + sx * 0.45)), y - 0.07, y + 0.07, -0.08, 0.08, 'steel_dk')
    m.box(g, *sorted((xe + sx * 0.4, xe + sx * 0.62)), y - 0.18, y + 0.18, -0.2, 0.2, 'bogie_dk')
    m.box(g, *sorted((xe + sx * 0.55, xe + sx * 0.62)), y - 0.12, y + 0.12, -0.24, 0.24, 'steel')
    for zz in (-0.28, 0.28):
        m.line(g, (xe + sx * 0.05, y - 0.15, zz), (xe + sx * 0.4, y - 0.3, zz * 1.2), 0.04, 'tyre')


class RailEMU(Straddle):
    """High-speed EMU on ordinary standard-gauge IR track, fed from the overhead wire through a pantograph."""
    hw = 1.69
    floor = 1.25
    roof, eave = 3.6, 3.05
    skirt_bot = 0.5
    win = (1.75, 2.45)
    stripe_y = 1.35
    door_w = 1.0
    trucks = 8.75
    axles = 2
    pans = ()               # x positions of pantographs on the middle car
    pan_kind = 'single'
    roof_units = 0

    def section(self, y):
        if y > self.roof:
            return None
        hw = self.hw
        if y > self.eave:
            hw -= 1.6 * (y - self.eave) ** 2
        if y < self.floor - 0.05:
            hw -= 0.06 + 0.25 * ((self.floor - y) / (self.floor - self.skirt_bot)) ** 2
        return hw, 0.0

    def cut(self, x, y):     # bogie wells
        return y < self.floor - 0.1 and any(abs(x - t) < 1.75 for t in (self.trucks, -self.trucks))

    def nose_hw(self, u, y, hw):
        return Straddle.nose_hw(self, u, y, hw)

    @property
    def doors(self):
        e = self.L / 2 - 1.3
        return (-e,) if self.front else (-e, e)

    def running_gear(self):
        for pos, tx in (('FRONT', self.trucks), ('REAR', -self.trucks)):
            rail_bogie(self.m, 'BOGEY_' + pos, tx, self.axles)
        # equipment under the floor between the bogies (hidden on Shinkansen-style skirts, visible on metros)
        span = self.trucks - 1.9
        if span > 1.2:
            underfloor(self.m, -span, span, max(self.skirt_bot - 0.05, 0.42), self.floor - 0.12, self.hw - 0.05, int(self.L))

    def roof_kit(self):
        if not self.front:
            for px in self.pans:
                pantograph(self.m, px, self.roof - 0.05, self.pan_kind)
                self.m.sym('SHELL_pantograph', px - 1.6, px + 1.6, self.roof - 0.1, self.roof + 0.45, 0.62, 0.72, 'body')  # noise shields


class N700S(RailEMU):
    """Tokaido Shinkansen N700S: 'Dual Supreme Wing' duck-bill nose, white with a blue band under the windows."""
    title = 'Shinkansen N700S'
    L, L_car = 27.35, 25.0
    nose_len = 10.5
    roof, eave = 3.85, 3.25
    stripe_y, stripe_h = 1.42, 0.055
    light = (0.78, 1.15, 1.32, 0.45, 0.72)
    pans = (-5.5,)
    layout, rows, pitch = 'hs', (3, 2), 1.04
    pal = {'body': '#F7F8F8', 'roof': '#EDEFF0', 'skirt': '#E2E5E7', 'skirt_dk': '#9AA2A9', 'door': '#EEF0F1',
           'nose': '#F7F8F8',
           'st0': '#1A4DA1', 'st1': '#F7F8F8', 'st2': '#1A4DA1', 'st3': '#1A4DA1', 'st4': '#1A4DA1', 'st5': '#1A4DA1', 'seat': '#1F4E9C', 'wall_in': '#F5F5F2', 'floor': '#D9D2C3', 'floor_lt': '#E6E0D3'}

    def plan(self, xa, xb, zw, fl, top):
        if not self.front:                             # toilet block at one end: accessible + standard + washbasins
            self.lavatory(xa + 1.4, xa + 3.2, 1, True)
            self.lavatory(xa + 1.4, xa + 2.7, -1, False)
            self.washbasins(xa + 2.75, xa + 3.2, -1)
            self.reserved.append((xa, xa + 3.3))
        for zs in (1, -1):                             # oversized-luggage space behind the last row
            self.luggage_rack(xb - 1.0, xb - 0.2, zs)
        self.reserved.append((xb - 1.1, xb))

    def nose_top(self, u):
        return 1.6 + (self.roof - 1.6) * (1 - u) ** 1.35 + 0.28 * math.exp(-((u - 0.6) / 0.11) ** 2) - 0.2 * u ** 6
    def nose_bot(self, u): return self.skirt_bot + 0.42 * u ** 2.5
    def nose_scale(self, u): return 1 - 0.5 * u ** 2.0

    def nose_hw(self, u, y, hw):
        top = self.nose_top(u)
        wing = (1 - 0.7 * u ** 1.7) * (1.0 + 0.12 * u * max(0.0, 1.5 - y))     # wide flat 'wings' low down
        hw *= wing
        if y > top - 0.45:
            hw *= math.sqrt(max(0.05, 1 - ((y - (top - 0.45)) / 0.5) ** 2))
        return hw

    def nose_glass(self, u, y): return 0.5 < u < 0.68 and self.nose_top(u) - 0.42 < y < self.nose_top(u) - 0.05

    def nose_colour(self, u, y):
        if self.nose_glass(u, y):
            return 'glass'
        s = stripes(y, self.stripe_y, self.stripe_h)
        if s and u < 0.55:
            return s
        return 'nose'

    def window_colour(self, x, y):
        return 'glass' if win_row(x, 1.04, 0.6) else 'body'


class ICE3(RailEMU):
    """Deutsche Bahn ICE 3 (class 403): smooth sloping nose, black window band, red stripe."""
    title = 'DB ICE 3'
    L, L_car = 25.8, 24.8
    hw = 1.475
    roof, eave = 3.85, 3.3
    nose_len = 6.0
    win = (1.7, 2.6)
    stripe_y, stripe_h = 1.18, 0.05
    light = (0.86, 1.05, 1.28, 0.4, 0.72)
    pans = (6.0,)
    layout, rows, pitch = 'hs', (2, 2), 0.98
    pal = {'body': '#F4F5F5', 'roof': '#E9EBEC', 'skirt': '#D9DCDE', 'skirt_dk': '#8E959B', 'door': '#E9EBEC',
           'nose': '#F4F5F5',
           'st0': '#EC0016', 'st1': '#EC0016', 'st2': '#EC0016', 'st3': '#F4F5F5', 'st4': '#F4F5F5', 'st5': '#F4F5F5', 'seat': '#2A4B7C', 'wall_in': '#EDEEEA', 'floor': '#5E626A'}
    cab_kind = 'lounge'

    def plan(self, xa, xb, zw, fl, top):
        if not self.front:
            self.lavatory(xa + 1.4, xa + 3.3, 1, True)
            self.wheelchair_space(xa + 1.5, xa + 2.6, -1)
            self.reserved.append((xa, xa + 3.4))
        for zs in (1, -1):
            self.luggage_rack(xb - 1.0, xb - 0.2, zs)
        self.reserved.append((xb - 1.1, xb))
        x = xa + 5
        while x < xb - 3:                              # little screens hanging over the aisle
            self.aisle_screen(x)
            x += 6

    def nose_top(self, u): return self.roof - (self.roof - 1.15) * u ** 1.7
    def nose_bot(self, u): return self.skirt_bot + 0.45 * u * u
    def nose_scale(self, u): return 1 - 0.5 * u ** 2.2
    def nose_glass(self, u, y): return 0.18 < u < 0.62 and self.nose_top(u) - 1.0 < y < self.nose_top(u) - 0.07

    def window_colour(self, x, y):
        return 'frame' if (x % 1.9) < 0.1 else 'glass'


class GG1(RailEMU):
    """Pennsylvania Railroad GG1 (Raymond Loewy, 1934): centre cab, streamlined hoods, Brunswick green with five
    gold pinstripes sweeping into 'cat whiskers' at each nose; two diamond pantographs."""
    title = 'Pennsylvania GG1'
    L = 24.2
    L_car = 24.4
    hw = 1.6
    floor = 1.3
    roof, eave = 4.45, 3.9
    skirt_bot = 0.75
    nose_len = 2.4
    trucks = 6.2
    axles = 3
    stripe_y, stripe_h = 1.45, 0.07
    light = (0.9, 2.15, 2.45, 0.0, 0.3)
    layout, rows, pitch = 'coach', (2, 2), 1.35
    pal = {'body': '#1C3A2B', 'roof': '#18322A', 'skirt': '#151C19', 'skirt_dk': '#0F1311', 'door': '#1C3A2B',
           'nose': '#1C3A2B', 'number': '#E9E2C8', 'gold': '#D6B25E',
           'st0': '#D6B25E', 'st1': '#D6B25E', 'st2': '#D6B25E', 'st3': '#D6B25E', 'st4': '#D6B25E', 'st5': '#D6B25E', 'seat': '#3E6B4F', 'wall_in': '#DCD5BC', 'floor': '#4A3A32', 'ceiling': '#EFE9D6'}
    trays = False

    def __init__(self, front=True, rear=False):
        RailEMU.__init__(self, front, rear)
        if not front:                       # the matching coach: a streamlined PRR-style car in the same livery
            self.roof, self.eave, self.skirt_bot, self.trucks = 4.1, 3.5, 0.6, 8.6
            self.win = (2.0, 2.9)

    def plan(self, xa, xb, zw, fl, top):
        if self.front:
            return
        # big men's + women's washrooms at the two ends (lounge style), aisle beside them
        self.lavatory(xa + 1.8, xa + 4.4, 1, True)
        self.lavatory(xb - 4.4, xb - 1.8, -1, True)
        self.reserved += [(xa, xa + 4.5), (xb - 4.5, xb)]


    @property
    def doors(self):
        return () if self.front else (-self.L / 2 + 1.1, self.L / 2 - 1.1)

    def window_colour(self, x, y):
        return 'glass' if win_row(x, 1.6, 1.15, 0.3) else 'body'

    def height(self, x):
        """the GG1 profile: tall centre cab, hoods stepping down to the noses"""
        ax = abs(x)
        if ax < 3.0:
            return self.roof
        if ax < 4.0:
            return self.roof - 0.9 * (ax - 3.0)
        return max(2.9, self.roof - 0.9 - 0.12 * (ax - 4.0))

    def gg1_field(self, x, y):
        if self.cut(x, y):
            return None
        ax = abs(x)
        top = self.height(x)
        end = self.L / 2
        if ax > end - self.nose_len:                     # rounded noses at BOTH ends
            u = (ax - (end - self.nose_len)) / self.nose_len
            top -= 0.55 * u * u
            if y < self.skirt_bot + 0.6 * u * u:
                return None
        if y > top:
            return None
        hw = self.hw - (0.18 if ax > 3.0 else 0.0)
        if y > top - 0.5:
            hw *= math.sqrt(max(0.05, 1 - ((y - (top - 0.5)) / 0.55) ** 2))
        if ax > end - self.nose_len:
            u = (ax - (end - self.nose_len)) / self.nose_len
            hw *= math.sqrt(max(0.0, 1 - 0.7 * u * u))
        if y < self.floor - 0.05:
            return (round(hw - 0.08, 3), 'skirt')
        # five pinstripes that sweep down and converge towards the noses (cat whiskers)
        k = min(1.0, max(0.0, ax - 7.5) / (end - 7.5 - 0.2))
        mid = 1.95 - 0.3 * k * k
        gap = 0.15 * (1 - 0.82 * k)
        for i in range(5):
            yi = mid + (i - 2) * gap
            if abs(y - yi) < 0.035 + 0.01 * (1 - k):
                return (round(hw, 3), 'st%d' % i)
        if ax < 2.4 and 2.75 < y < 3.45 and (ax % 1.2) < 0.85:     # cab side windows
            return (round(hw - 0.03, 3), 'tint')
        if ax < 0.9 and 1.4 < y < 3.45:                             # cab door
            return (round(hw, 3), 'seam' if ax > 0.82 or y > 3.4 else 'door')
        if 3.2 < ax < 10 and 2.45 < y < 2.7 and (ax % 0.9) < 0.6:   # hood louvres
            return (round(hw, 3), 'skirt')
        return (round(hw, 3), 'body')

    both_noses = True

    def extra_levels(self):
        if not self.front:
            return []
        return [1.25 + k * 0.025 for k in range(52)]          # fine rows for the converging pinstripes

    def surf(self, x, y):
        if not self.front:
            return RailEMU.surf(self, x, y)
        v = self.gg1_field(x, y)
        return None if v is None else (v[0], v[1], 0.0, False, False)

    def build(self, name, smooth=False):
        self.signs = []
        if not self.front:
            return Coach.build(self, name, smooth)
        m = Model(name, dict(BASE, **self.pal))
        self.m = m
        if smooth:
            self.smooth_shell()
        else:
            m.loft('SHELL', self.x0, self.x1, self.skirt_bot, self.roof, 0.1, 0.035, self.gg1_field)
        m.slab('FRAME', self.x0 + 0.4, self.x1 - 0.4, 0.95, 1.25, self.hw - 0.1, 'skirt')
        for pos, tx in (('FRONT', self.trucks), ('REAR', -self.trucks)):
            rail_bogie(m, 'BOGEY_' + pos, tx, 3, base=4.2, r=0.57, frame='skirt', wheel='bogie_dk')
        for sx in (1, -1):                                          # pilot + pony axle at each end, couplers, lights
            ex = sx * (self.L / 2 - 0.3)
            m.box('SHELL_pilot', ex - 0.3, ex + 0.3, 0.25, 0.95, -1.2, 1.2, 'skirt_dk')
            m.box('SHELL_pilot', ex + sx * 0.25, ex + sx * 0.55, 0.65, 0.95, -0.2, 0.2, 'steel_dk')
            nx = sx * (self.L / 2 - 0.45)
            m.box('HEADLIGHT_1' if sx > 0 else 'SHELL_lamp', nx, nx + sx * 0.22, 2.12, 2.36, -0.17, 0.17, 'lamp')
            m.box('SHELL_number', nx - sx * 0.5, nx - sx * 0.2, 2.42, 2.56, -0.5, 0.5, 'number')
            pantograph(m, sx * 4.7, self.height(sx * 4.7) - 0.05, 'diamond')
        return m


class Coach(RailEMU):
    """generic streamlined coach body (used for the GG1's matching car)"""
    def build(self, name, smooth=False):
        return Straddle.build(self, name, smooth)


class GG1Coach(GG1):
    pass


# ------------------------------------------------------------------------------------------------------------------
class Subway(RailEMU):
    """metro / subway car on ordinary track with a third rail: flat cab end, lots of doors, longitudinal seats,
    collector shoes on the bogies reaching out to the conductor rail"""
    layout = 'metro'
    pans = ()
    trucks = 6.3
    skirt_bot = 0.62
    floor = 1.1
    win = (1.55, 2.55)
    stripe_y, stripe_h = 1.25, 0.05
    door_w = 1.35
    nose_len = 0.7
    joint = 0.28
    light = (0.6, 0.9, 1.08, 0.5, 0.82)
    cab_kind = 'driver'

    def nose_top(self, u): return self.roof - 0.35 * u * u
    def nose_bot(self, u): return self.skirt_bot + 0.1 * u
    def nose_scale(self, u): return 1 - 0.05 * u * u
    def nose_glass(self, u, y): return u > 0.4 and self.win[0] - 0.1 < y < self.roof - 0.55

    def nose_colour(self, u, y):
        if self.nose_glass(u, y):
            return 'glass'
        if u > 0.4 and y > self.roof - 0.5:
            return 'led_bg'                                   # destination display housing above the windscreen
        s = stripes(y, self.stripe_y, self.stripe_h)
        if s:
            return s
        if y < self.floor - 0.05:
            return 'skirt'
        return 'nose' if 'nose' in self.pal else 'body'

    @property
    def doors(self):
        n = self.n_doors
        span = self.L - (2.6 if self.front else 2.0) - (self.nose_len if self.front else 0)
        x0 = self.x0 + 1.4
        return tuple(round(x0 + span * (k + 0.5) / n, 2) for k in range(n))

    n_doors = 4

    def window_colour(self, x, y):
        return 'glass' if win_row(x, self.win_pitch, self.win_w, 0.2) else 'body'

    win_pitch, win_w = 1.15, 0.9

    def roof_kit(self):
        m = self.m
        for cx in (-self.L / 4, self.L / 4 - (0.6 if self.front else 0)):
            m.slab('SHELL_roof', cx - 1.3, cx + 1.3, self.roof - 0.06, self.roof + 0.3, 0.8, 'roof_eq')     # HVAC unit
            for k in range(-4, 5):
                m.box('SHELL_roof', cx + k * 0.26 - 0.05, cx + k * 0.26 + 0.05, self.roof + 0.3, self.roof + 0.33, -0.6, 0.6, 'frame')
            m.slab('SHELL_roof', cx - 0.4, cx + 0.4, self.roof + 0.3, self.roof + 0.36, 0.3, 'roof_eq_dk')  # condenser fan
        m.box('SHELL_roof', -0.1, 0.1, self.roof - 0.02, self.roof + 0.25, -0.06, 0.06, 'black')             # radio antenna
        m.box('SHELL_roof', -0.25, 0.25, self.roof + 0.22, self.roof + 0.26, -0.12, 0.12, 'black')

    def running_gear(self):
        RailEMU.running_gear(self)
        for pos, tx in (('FRONT', self.trucks), ('REAR', -self.trucks)):
            for zs in (1, -1):                                # third-rail collector shoe on each side
                self.m.box('BOGEY_' + pos, tx - 0.45, tx + 0.45, 0.38, 0.44, *sorted((zs * 1.05, zs * 1.42)), 'black')
                self.m.box('BOGEY_' + pos, tx - 0.06, tx + 0.06, 0.44, 0.62, *sorted((zs * 1.0, zs * 1.12)), 'tyre')

    def details(self):
        Straddle.details(self)
        m = self.m
        # couplers at the ends + windscreen wipers
        coupler(m, self.x0, -1, self.skirt_bot + 0.25)
        if not self.front:
            coupler(m, self.x1, 1, self.skirt_bot + 0.25)
        else:
            coupler(m, self.x1, 1, self.skirt_bot + 0.25)
            for zw in (-0.45, 0.45):
                m.line('SHELL_wiper', (self.x1 + 0.03, self.win[0] + 0.05, zw), (self.x1 + 0.03, self.win[0] + 0.6, zw + 0.25), 0.03, 'black')
        # outside grab handles + door-open buttons + car number plates
        for d in self.door_list():
            for zs in (1, -1):
                zo = zs * (self.hw + 0.02)
                for hx in (d - self.door_w / 2 - 0.1, d + self.door_w / 2 + 0.1):
                    m.box('SHELL_handle', hx - 0.025, hx + 0.025, self.floor + 0.6, self.floor + 1.4, *sorted((zo, zo - zs * 0.04)), 'steel')
                m.box('SHELL_button', d + self.door_w / 2 + 0.17, d + self.door_w / 2 + 0.27, self.floor + 1.0, self.floor + 1.1,
                      *sorted((zo, zo - zs * 0.025)), 'green_led')
        for zs in (1, -1):
            px = self.x0 + 0.8
            m.box('SHELL_plate', px, px + 0.45, self.stripe_y - 0.25, self.stripe_y - 0.1, *sorted((zs * (self.hw + 0.005), zs * (self.hw + 0.012))), 'white')
            m.box('SHELL_plate', px + 0.05, px + 0.4, self.stripe_y - 0.21, self.stripe_y - 0.14, *sorted((zs * (self.hw + 0.012), zs * (self.hw + 0.016))), 'black')
        if self.front:                                        # front destination display + route sign over the windscreen
            # sits FLAT on the very front of the nose, inside a black bezel, just above the windscreen (her 10-05:
            # it used to hang above a sloped nose, 'like it's going to go off the train')
            top = self.nose_top(1.0)
            y1 = top - 0.05
            y0 = y1 - 0.2
            hwn = min(0.85, self.hw * self.nose_scale(1.0) - 0.25)
            self.m.box('SHELL_destbox', self.x1 - 0.06, self.x1 + 0.008, y0 - 0.035, y1 + 0.035, -hwn - 0.04, hwn + 0.04, 'black')
            self.sign('dest', -hwn, hwn, y0, y1, self.x1 + 0.012, nx=1)


class R211(Subway):
    """New York City Subway R211 (Kawasaki, 2023): stainless steel, black cab end, blue door edges, LED route display"""
    title = 'NYC Subway R211'
    L, L_car = 18.4, 18.4
    hw = 1.52
    roof, eave = 3.65, 3.1
    win = (1.55, 2.5)
    win_pitch, win_w = 1.3, 1.0
    pal = {'body': '#C8CDD3', 'roof': '#B4BAC1', 'skirt': '#8E959D', 'skirt_dk': '#5C636B', 'door': '#BCC2C9', 'nose': '#1C1E22',
           'seat': '#2B5BA8', 'seat_pri': '#F2C230', 'wall_in': '#E9ECEE', 'floor': '#5A5E66',
           'st0': '#1F4FA8', 'st1': '#1F4FA8', 'st2': '#C8CDD3', 'st3': '#C8CDD3', 'st4': '#C8CDD3', 'st5': '#C8CDD3'}


class R160(Subway):
    """New York City Subway R160 (Alstom / Kawasaki, 2006): stainless, black-painted cab end, 4 door pairs"""
    title = 'NYC Subway R160'
    L, L_car = 18.4, 18.4
    hw = 1.52
    roof, eave = 3.65, 3.15
    win = (1.6, 2.45)
    pal = {'body': '#D0D4D8', 'roof': '#B9BEC4', 'skirt': '#8A9097', 'skirt_dk': '#575D64', 'door': '#C4C9CE', 'nose': '#26282C',
           'seat': '#4A6FA5', 'seat_pri': '#E07A1F', 'wall_in': '#ECEEEF', 'floor': '#6A6E74',
           'st0': '#D0D4D8', 'st1': '#D0D4D8', 'st2': '#D0D4D8', 'st3': '#D0D4D8', 'st4': '#D0D4D8', 'st5': '#D0D4D8'}


class Tube2009(Subway):
    """London Underground 2009 Stock (Victoria line): deep-tube round profile, white body, red doors + cab, blue skirt"""
    title = 'London Underground 2009 Stock'
    L, L_car = 16.6, 16.6
    hw = 1.32
    floor = 0.75
    skirt_bot = 0.35
    roof, eave = 2.88, 2.0
    win = (1.25, 2.1)
    stripe_y = 0.55
    n_doors = 2
    door_w = 1.6
    trucks = 5.4
    win_pitch, win_w = 1.45, 1.05
    light = (0.6, 0.55, 0.72, 0.5, 0.8)
    pal = {'body': '#F2F3F4', 'roof': '#DCDFE2', 'skirt': '#1F3A93', 'skirt_dk': '#152A6E', 'door': '#DC241F', 'nose': '#DC241F',
           'seat': '#2B3F7E', 'seat_pri': '#7AA7D9', 'wall_in': '#F0F0EE', 'floor': '#4C4F55',
           'st0': '#1F3A93', 'st1': '#1F3A93', 'st2': '#1F3A93', 'st3': '#F2F3F4', 'st4': '#F2F3F4', 'st5': '#F2F3F4'}

    def section(self, y):                       # deep-tube profile: walls lean in from the waist, very round roof
        if y > self.roof:
            return None
        hw = self.hw
        if y > self.eave:
            t = (y - self.eave) / (self.roof - self.eave)
            hw = self.hw * math.sqrt(max(0.02, 1 - t * t))
        if y < self.floor - 0.05:
            hw -= 0.12
        return hw, 0.0

    def ceil(self, u=0.0):
        c = self.roof - 0.25
        return min(c, self.nose_top(u) - 0.15) if u > 0 else c


class SStock(Subway):
    """London Underground S Stock (Metropolitan / Circle / District, 2010): walk-through, white with red doors + blue band"""
    title = 'London Underground S Stock'
    L, L_car = 17.4, 15.6
    hw = 1.46
    floor = 0.95
    roof, eave = 3.68, 3.05
    n_doors = 3
    door_w = 1.6
    stripe_y, stripe_h = 0.62, 0.05
    pal = {'body': '#F4F5F6', 'roof': '#DFE2E5', 'skirt': '#1F3A93', 'skirt_dk': '#152A6E', 'door': '#DC241F', 'nose': '#DC241F',
           'seat': '#3A4E8C', 'seat_pri': '#C8102E', 'wall_in': '#F2F2F0', 'floor': '#55585E',
           'st0': '#1F3A93', 'st1': '#1F3A93', 'st2': '#1F3A93', 'st3': '#1F3A93', 'st4': '#F4F5F6', 'st5': '#F4F5F6'}


class Ginza1000(Subway):
    """Tokyo Metro 1000 series (Ginza line, 2012): lemon-yellow retro body after the 1927 cars, rounded cab, 3 doors"""
    title = 'Tokyo Metro 1000 (Ginza)'
    L, L_car = 16.0, 16.0
    hw = 1.275
    floor = 0.95
    roof, eave = 3.5, 2.95
    n_doors = 3
    door_w = 1.3
    nose_len = 1.1
    stripe_y, stripe_h = 1.0, 0.04
    pal = {'body': '#F5C400', 'roof': '#8A8F94', 'skirt': '#3A3A3A', 'skirt_dk': '#242424', 'door': '#E8B800', 'nose': '#F5C400',
           'seat': '#B5452C', 'seat_pri': '#2F5DA8', 'wall_in': '#F2EEE2', 'floor': '#6B5B4B',
           'st0': '#2A2A2A', 'st1': '#2A2A2A', 'st2': '#F5C400', 'st3': '#F5C400', 'st4': '#F5C400', 'st5': '#F5C400'}

    def nose_top(self, u): return self.roof - 0.7 * u ** 1.6
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.35 * u * u))
    def nose_glass(self, u, y): return 0.35 < u < 0.92 and 1.5 < y < self.nose_top(u) - 0.35


class MP14(Subway):
    """Paris Metro MP14 (Alstom, Line 14): white with grey band + teal, wide windscreen, open gangways"""
    title = 'Paris Metro MP14'
    L, L_car = 15.4, 15.4
    hw = 1.25
    floor = 1.0
    roof, eave = 3.48, 2.95
    n_doors = 3
    door_w = 1.65
    nose_len = 1.0
    stripe_y, stripe_h = 0.85, 0.06
    pal = {'body': '#F4F5F6', 'roof': '#C9CDD1', 'skirt': '#5C6670', 'skirt_dk': '#3E464E', 'door': '#E7E9EB', 'nose': '#F4F5F6',
           'seat': '#2E8C8C', 'seat_pri': '#E0465A', 'wall_in': '#F1F2F3', 'floor': '#5E6168',
           'st0': '#2E8C8C', 'st1': '#2E8C8C', 'st2': '#5C6670', 'st3': '#5C6670', 'st4': '#F4F5F6', 'st5': '#F4F5F6'}

    def nose_top(self, u): return self.roof - 0.55 * u ** 1.4
    def nose_glass(self, u, y): return u > 0.3 and 1.35 < y < self.nose_top(u) - 0.3



# ======================================================================================================================
# 10 x 10 (her ask 2026-10-05): ten trains in every family, each with its own cab car AND its own passenger car.
# ======================================================================================================================

def _pal(body, roof, skirt, skirt_dk, door, s, seat, seat_pri='#E0B220', nose=None, wall='#F1F2F3', floor='#5A5E66', **extra):
    """palette helper: s = six stripe colours, bottom to top"""
    p = {'body': body, 'roof': roof, 'skirt': skirt, 'skirt_dk': skirt_dk, 'door': door, 'nose': nose or body,
         'seat': seat, 'seat_pri': seat_pri, 'wall_in': wall, 'floor': floor}
    for i, c in enumerate(s):
        p['st%d' % i] = c
    p.update(extra)
    return p


# ---------------------------------------------------------------- subways (+4)

class Moskva(Subway):
    """Moscow Metro 81-765 'Moskva' (Metrowagonmash, 2017): blue-grey body, white band, big black wrap-round mask"""
    title = 'Moscow Metro 81-765 Moskva'
    L, L_car = 19.2, 19.2
    hw = 1.35
    roof, eave = 3.6, 3.05
    n_doors = 4
    door_w = 1.3
    nose_len = 0.9
    stripe_y, stripe_h = 1.15, 0.07
    pal = _pal('#3B5C8C', '#9AA3AD', '#2B3240', '#1C2230', '#3B5C8C', ('#F2F4F6', '#F2F4F6', '#3B5C8C', '#3B5C8C', '#3B5C8C', '#3B5C8C'),
               '#5B6E8C', '#C8382E', mask='#14171C')
    def nose_top(self, u): return self.roof - 0.45 * u ** 1.3
    def nose_glass(self, u, y): return u > 0.25 and 1.45 < y < self.nose_top(u) - 0.25
    def nose_colour(self, u, y):
        if self.nose_glass(u, y): return 'glass' if 1.7 < y < self.nose_top(u) - 0.4 else 'mask'
        return Subway.nose_colour(self, u, y)


class BerlinIK(Subway):
    """Berlin U-Bahn IK series (Stadler, 2015): small-profile, all yellow with grey doors and a black cab front"""
    title = 'Berlin U-Bahn IK'
    L, L_car = 16.0, 16.0
    hw = 1.15
    floor = 0.95
    roof, eave = 3.4, 2.85
    n_doors = 3
    door_w = 1.3
    nose_len = 0.9
    stripe_y, stripe_h = 0.9, 0.03
    pal = _pal('#F7D117', '#9A9DA1', '#3A3D42', '#26292D', '#F2C900', ('#2A2C30', '#2A2C30', '#F7D117', '#F7D117', '#F7D117', '#F7D117'),
               '#3C5BA8', '#E2402F', nose='#1E2024')
    def nose_top(self, u): return self.roof - 0.3 * u
    def nose_glass(self, u, y): return u > 0.3 and 1.4 < y < self.nose_top(u) - 0.3


class MTRCity(Subway):
    """Hong Kong MTR 'M-Train' (Metro-Cammell): stainless with red + blue stripes, wide windscreen"""
    title = 'Hong Kong MTR M-Train'
    L, L_car = 22.0, 22.0
    hw = 1.55
    roof, eave = 3.7, 3.15
    n_doors = 5
    door_w = 1.4
    nose_len = 1.0
    stripe_y, stripe_h = 1.05, 0.06
    pal = _pal('#CDD2D7', '#B1B7BE', '#7D848C', '#525960', '#BFC5CB', ('#C8102E', '#C8102E', '#CDD2D7', '#1F3F8F', '#1F3F8F', '#CDD2D7'),
               '#8C98A8', '#C8102E', nose='#E1E4E7')
    def nose_top(self, u): return self.roof - 0.4 * u
    def nose_glass(self, u, y): return u > 0.3 and 1.5 < y < self.nose_top(u) - 0.3


class Azur(Subway):
    """Montreal Metro MPM-10 'Azur' (Bombardier/Alstom, 2016): white, deep blue mask, open walk-through, rubber tyres"""
    title = 'Montreal Metro Azur'
    L, L_car = 16.9, 16.9
    hw = 1.25
    floor = 0.98
    roof, eave = 3.55, 2.95
    n_doors = 3
    door_w = 1.45
    nose_len = 1.15
    stripe_y, stripe_h = 0.85, 0.05
    pal = _pal('#F3F5F7', '#D3D8DD', '#2459A6', '#173E78', '#E6EAEE', ('#2459A6', '#2459A6', '#2459A6', '#F3F5F7', '#F3F5F7', '#F3F5F7'),
               '#2E6FB8', '#F2B320', nose='#2459A6')
    def nose_top(self, u): return self.roof - 0.6 * u ** 1.5
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.25 * u * u))
    def nose_glass(self, u, y): return 0.3 < u < 0.92 and 1.4 < y < self.nose_top(u) - 0.3


# ---------------------------------------------------------------- monorails (+5)

class DisneyMk7(Straddle):
    """Walt Disney World Monorail Mark VII (Bombardier, 1989-2021 refit): white, long bubble nose, coloured stripe"""
    title = 'Disney Monorail Mark VII'
    L, L_car = 14.0, 12.4
    nose_len = 3.6
    roof, eave = 3.45, 2.9
    stripe_y, stripe_h = 1.18, 0.08
    cab_kind = 'driver'
    doors = (-2.2, 2.2)
    door_w = 1.5
    pal = _pal('#F7F8F9', '#E3E6E9', '#E7EAED', '#A8AFB6', '#EDEFF1', ('#7B2D8E', '#7B2D8E', '#F7F8F9', '#7B2D8E', '#7B2D8E', '#7B2D8E'),
               '#3B4A6B', '#7B2D8E')
    def nose_top(self, u): return self.roof - 1.7 * u ** 1.9
    def nose_bot(self, u): return self.skirt_bot + 1.2 * u ** 2.2
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.7 * u * u))
    def nose_glass(self, u, y): return 0.12 < u < 0.7 and 1.6 < y < self.nose_top(u) - 0.1
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.4, 1.1) else 'body'


class OsakaMonorail(Straddle):
    """Osaka Monorail 3000 series (Hitachi, 2018): white with a deep blue nose mask and blue/sky stripes"""
    title = 'Osaka Monorail 3000'
    L, L_car = 15.4, 14.6
    hw = 1.45
    roof, eave, nose_len = 3.6, 3.1, 2.1
    stripe_y, stripe_h = 1.15, 0.06
    doors = (-3.8, 1.4)
    door_w = 1.3
    pal = _pal('#F5F7F9', '#DCE1E5', '#D1D7DC', '#97A0A8', '#E4E8EB', ('#1E4FA0', '#1E4FA0', '#F5F7F9', '#3FB2E6', '#3FB2E6', '#3FB2E6'),
               '#2A62B0', '#D9532B', mask='#14325F')
    layout = 'tokyo'
    def nose_top(self, u): return self.roof - 0.9 * u ** 1.2
    def nose_bot(self, u): return self.skirt_bot + 1.25 * u ** 2
    def nose_scale(self, u): return 1 - 0.3 * u * u
    def nose_glass(self, u, y): return u > 0.1 and 1.35 < y < self.nose_top(u) - 0.12
    def nose_colour(self, u, y):
        if self.nose_glass(u, y): return 'glass' if 1.6 < y < self.nose_top(u) - 0.35 and u > 0.3 else 'mask'
        return Straddle.nose_colour(self, u, y)
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.5, 1.15) else 'body'


class Mumbai(Straddle):
    """Mumbai Monorail (Scomi SUTRA): the famous hot-pink train with a white band and a short blunt nose"""
    title = 'Mumbai Monorail (pink)'
    L, L_car = 11.5, 10.8
    roof, eave, nose_len = 3.5, 3.0, 1.6
    stripe_y, stripe_h = 1.22, 0.07
    doors = (-1.2,)
    door_w = 1.5
    pal = _pal('#E5317A', '#C2286A', '#3A3D44', '#2A2C31', '#D92D72', ('#F5F5F5', '#F5F5F5', '#E5317A', '#E5317A', '#F5F5F5', '#F5F5F5'),
               '#8E2A5C', '#F2B320')
    def nose_top(self, u): return self.roof - 0.7 * u ** 1.4
    def nose_bot(self, u): return self.skirt_bot + 1.1 * u ** 2
    def nose_scale(self, u): return 1 - 0.35 * u * u
    def nose_glass(self, u, y): return u > 0.15 and 1.4 < y < self.nose_top(u) - 0.15
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.35, 1.0) else 'body'


class YuiRail(Straddle):
    """Okinawa Yui Rail (Hitachi 1000 series): white with an orange-red band and a rounded friendly face"""
    title = 'Okinawa Yui Rail'
    L, L_car = 14.7, 14.0
    hw = 1.4
    roof, eave, nose_len = 3.55, 3.05, 2.0
    stripe_y, stripe_h = 1.2, 0.07
    doors = (-3.4, 1.6)
    door_w = 1.2
    pal = _pal('#F6F7F8', '#DFE3E6', '#D8DCE0', '#9DA5AD', '#E7EAED', ('#E85A1E', '#E85A1E', '#E85A1E', '#F6F7F8', '#F2A83A', '#F2A83A'),
               '#C9562C', '#2A6FB8')
    def nose_top(self, u): return self.roof - 1.0 * u ** 1.6
    def nose_bot(self, u): return self.skirt_bot + 1.3 * u ** 2.2
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.55 * u * u))
    def nose_glass(self, u, y): return 0.1 < u < 0.82 and 1.45 < y < self.nose_top(u) - 0.12
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.45, 1.1) else 'body'


class PalmJumeirah(Straddle):
    """Palm Jumeirah Monorail (Hitachi, Dubai): sand-gold and white with a long dark glass nose"""
    title = 'Palm Jumeirah Monorail'
    L, L_car = 13.8, 12.8
    roof, eave, nose_len = 3.5, 3.0, 2.9
    stripe_y, stripe_h = 1.05, 0.06
    doors = (-2.6, 1.8)
    door_w = 1.4
    cab_kind = 'none'
    pal = _pal('#F4F1EA', '#DAD4C6', '#C2A35E', '#8C7440', '#ECE6DA', ('#C2A35E', '#C2A35E', '#F4F1EA', '#C2A35E', '#F4F1EA', '#F4F1EA'),
               '#8C7440', '#2A6FB8')
    def nose_top(self, u): return self.roof - 1.9 * u ** 1.7
    def nose_bot(self, u): return self.skirt_bot + 1.2 * u ** 2
    def nose_scale(self, u): return 1 - 0.4 * u ** 1.5
    def nose_glass(self, u, y): return 0.05 < u < 0.85 and 1.3 < y < self.nose_top(u) - 0.08
    def window_colour(self, x, y): return 'frame' if (x % 1.6) < 0.08 else 'glass'


# ---------------------------------------------------------------- maglevs (+8)

class CRRC600(Maglev):
    """CRRC 600 km/h high-speed maglev (2021): silver-white, long flat 'flying' nose, blue-grey skirt"""
    title = 'CRRC 600 km/h Maglev'
    L, L_car = 27.0, 24.6
    hw = 1.7
    nose_len = 9.0
    trucks = 7.2
    doors = (-10.0,)
    door_w = 1.1
    stripe_y, stripe_h = 1.15, 0.06
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#EEF1F4', '#D9DEE3', '#7C8A99', '#4E5A67', '#E4E8EC', ('#1E6FB8', '#1E6FB8', '#EEF1F4', '#8996A4', '#8996A4', '#8996A4'),
               '#2F5D8C', coil_glow='#7FD2FF')
    def nose_top(self, u): return self.roof - (self.roof - 0.9) * u ** 1.25
    def nose_bot(self, u): return self.skirt_bot + 0.9 * u ** 2
    def nose_scale(self, u): return max(0.1, 1 - 0.75 * u ** 1.6)
    def nose_glass(self, u, y): return 0.32 < u < 0.5 and self.nose_top(u) - 0.45 < y < self.nose_top(u) - 0.06
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.0, 0.55) else 'body'


class Linimo(Maglev):
    """Linimo (Nagoya Tobu Kyuryo line, HSST low-speed maglev): small, white with an orange band, bluff face"""
    title = 'Linimo HSST'
    L, L_car = 14.0, 13.5
    hw = 1.3
    roof, eave = 3.35, 2.85
    nose_len = 1.8
    trucks = 4.2
    doors = (-3.0, 2.6)
    door_w = 1.3
    stripe_y, stripe_h = 1.0, 0.07
    pal = _pal('#F5F6F7', '#DDE1E5', '#9AA2AA', '#5D646B', '#E8EBEE', ('#F28A1E', '#F28A1E', '#F5F6F7', '#F28A1E', '#F5F6F7', '#F5F6F7'),
               '#E07A1F', '#2F5DA8', coil_glow='#FFC37F')
    def nose_top(self, u): return self.roof - 0.8 * u ** 1.4
    def nose_bot(self, u): return self.skirt_bot + 0.8 * u ** 2
    def nose_scale(self, u): return 1 - 0.3 * u * u
    def nose_glass(self, u, y): return u > 0.12 and 1.3 < y < self.nose_top(u) - 0.12
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.4, 1.05) else 'body'


class Ecobee(Maglev):
    """Incheon Airport Maglev 'Ecobee' (Hyundai Rotem, 2016): white with a lime-green swoosh, rounded snout"""
    title = 'Incheon Ecobee Maglev'
    L, L_car = 12.5, 12.0
    hw = 1.35
    roof, eave = 3.4, 2.9
    nose_len = 2.0
    trucks = 3.8
    doors = (-2.6, 2.2)
    door_w = 1.4
    stripe_y, stripe_h = 1.05, 0.08
    pal = _pal('#F6F7F8', '#DEE2E6', '#8F979F', '#585F67', '#E9ECEF', ('#7BC043', '#7BC043', '#F6F7F8', '#2E8C4E', '#2E8C4E', '#F6F7F8'),
               '#2E8C4E', '#F2B320', coil_glow='#B8FF7F')
    def nose_top(self, u): return self.roof - 1.1 * u ** 1.8
    def nose_bot(self, u): return self.skirt_bot + 0.9 * u ** 2
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.6 * u * u))
    def nose_glass(self, u, y): return 0.1 < u < 0.78 and 1.35 < y < self.nose_top(u) - 0.1
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.3, 1.0) else 'body'


class ChangshaMaglev(Maglev):
    """Changsha Maglev Express (CRRC Zhuzhou, 2016): white with red and gold bands, short rounded face"""
    title = 'Changsha Maglev Express'
    L, L_car = 16.5, 15.5
    hw = 1.4
    roof, eave = 3.5, 2.95
    nose_len = 2.4
    trucks = 5.0
    doors = (-4.0, 2.8)
    door_w = 1.3
    stripe_y, stripe_h = 1.05, 0.07
    pal = _pal('#F5F5F5', '#DDE0E3', '#8C939A', '#585E64', '#E8EAEC', ('#C8102E', '#C8102E', '#F2B320', '#F5F5F5', '#C8102E', '#C8102E'),
               '#C8102E', '#F2B320', coil_glow='#FFB27F')
    def nose_top(self, u): return self.roof - 1.2 * u ** 1.7
    def nose_bot(self, u): return self.skirt_bot + 0.9 * u ** 2
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.55 * u * u))
    def nose_glass(self, u, y): return 0.12 < u < 0.8 and 1.4 < y < self.nose_top(u) - 0.1
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.45, 1.1) else 'body'


class BeijingS1(Maglev):
    """Beijing Subway Line S1 maglev (2017): white with orange + red, panoramic windscreen"""
    title = 'Beijing S1 Maglev'
    L, L_car = 15.3, 14.8
    hw = 1.42
    roof, eave = 3.6, 3.05
    nose_len = 1.9
    trucks = 4.8
    doors = (-3.8, 0.0, 3.8)
    door_w = 1.3
    stripe_y, stripe_h = 1.05, 0.06
    pal = _pal('#F4F5F6', '#DCDFE2', '#8E959C', '#5A6168', '#E7E9EC', ('#E2541C', '#E2541C', '#C8102E', '#F4F5F6', '#E2541C', '#F4F5F6'),
               '#C8562C', '#2A6FB8', coil_glow='#FFA36F')
    def nose_top(self, u): return self.roof - 0.8 * u ** 1.2
    def nose_bot(self, u): return self.skirt_bot + 0.8 * u ** 2
    def nose_scale(self, u): return 1 - 0.3 * u * u
    def nose_glass(self, u, y): return u > 0.1 and 1.35 < y < self.nose_top(u) - 0.1


class MLX01(Maglev):
    """JR MLX01 (Yamanashi test line, 1997): the 'Aero-wedge' nose, white with a blue line, tiny windows"""
    title = 'JR MLX01'
    L, L_car = 28.0, 21.6
    hw = 1.45
    roof, eave = 3.3, 2.7
    nose_len = 13.0
    trucks = 6.0
    doors = (-10.0,)
    door_w = 0.9
    win = (1.6, 2.1)
    stripe_y, stripe_h = 1.3, 0.05
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#F7F8F9', '#E8EBEE', '#B0B7BE', '#6E767E', '#E8EBEE', ('#1C5CB0', '#1C5CB0', '#F7F8F9', '#F7F8F9', '#F7F8F9', '#F7F8F9'),
               '#5B6B82', coil_glow='#6FC4FF')
    def nose_top(self, u): return self.roof - (self.roof - 0.9) * (u ** 1.05)
    def nose_bot(self, u): return self.skirt_bot + (0.0 if u < 0.55 else 1.4 * (u - 0.55) ** 1.2)
    def nose_scale(self, u): return max(0.08, 1 - 0.95 * u ** 1.5)
    def nose_glass(self, u, y): return 0.28 < u < 0.4 and self.nose_top(u) - 0.35 < y < self.nose_top(u) - 0.06
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.1, 0.42) else 'body'


class TR09(Maglev):
    """Transrapid TR09 (Emsland test track, 2008): white with a yellow-and-grey band, rounded nose, big windscreen"""
    title = 'Transrapid TR09'
    L, L_car = 25.0, 24.0
    nose_len = 5.5
    trucks = 7.0
    doors = (-8.8,)
    door_w = 1.2
    stripe_y, stripe_h = 1.1, 0.08
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#F5F6F7', '#E1E4E7', '#8C949C', '#545B62', '#E6E9EC', ('#F2C230', '#F2C230', '#8C949C', '#8C949C', '#F5F6F7', '#F5F6F7'),
               '#4E6A8E', coil_glow='#FFE27F')
    def nose_top(self, u): return self.roof - 2.4 * u ** 1.9
    def nose_bot(self, u): return self.skirt_bot + 1.0 * u ** 2
    def nose_scale(self, u): return math.sqrt(max(0.0, 1 - 0.75 * u * u))
    def nose_glass(self, u, y): return 0.15 < u < 0.6 and self.nose_top(u) - 0.95 < y < self.nose_top(u) - 0.08
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.4, 1.0) else 'body'


class Birmingham(Maglev):
    """Birmingham Airport Maglev (1984, the world's first commercial maglev): boxy little cabin, cream and red"""
    title = 'Birmingham Maglev (1984)'
    L, L_car = 8.0, 7.6
    hw = 1.15
    roof, eave = 3.15, 2.85
    nose_len = 0.7
    trucks = 2.3
    doors = (0.0,)
    door_w = 1.6
    stripe_y, stripe_h = 0.95, 0.09
    pal = _pal('#EFE6CF', '#D9CFB6', '#7A3A2C', '#4E241B', '#E6DCC2', ('#B5262E', '#B5262E', '#B5262E', '#EFE6CF', '#EFE6CF', '#EFE6CF'),
               '#B5262E', '#2F5DA8', coil_glow='#FF9F7F')
    def nose_top(self, u): return self.roof - 0.25 * u
    def nose_bot(self, u): return self.skirt_bot + 0.4 * u
    def nose_scale(self, u): return 1 - 0.1 * u
    def nose_glass(self, u, y): return u > 0.2 and 1.25 < y < self.nose_top(u) - 0.18
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.2, 0.9) else 'body'


# ---------------------------------------------------------------- high-speed / electric (+7)

class E5(RailEMU):
    """Shinkansen E5 'Hayabusa' (Tohoku): 15 m long nose, tokiwa green top, hiun white sides, pink 'hayate' band"""
    title = 'Shinkansen E5 Hayabusa'
    L, L_car = 26.25, 25.0
    nose_len = 13.0
    roof, eave = 3.65, 3.05
    hw = 1.675
    stripe_y, stripe_h = 1.5, 0.05
    pans = (-5.5,)
    layout, rows, pitch = 'hs', (3, 2), 1.04
    pal = _pal('#F2F3F1', '#00805A', '#E2E5E3', '#8E979A', '#EEF0EE', ('#E8638C', '#E8638C', '#F2F3F1', '#F2F3F1', '#F2F3F1', '#F2F3F1'),
               '#2B6E5A', nose='#F2F3F1')
    def nose_top(self, u): return 1.5 + (self.roof - 1.5) * (1 - u) ** 1.25
    def nose_bot(self, u): return self.skirt_bot + 0.35 * u ** 2.5
    def nose_scale(self, u): return max(0.1, 1 - 0.65 * u ** 1.8)
    def nose_glass(self, u, y): return 0.38 < u < 0.52 and self.nose_top(u) - 0.4 < y < self.nose_top(u) - 0.05
    def body_colour(self, x, y):
        if y > self.eave - 0.35: return 'roof'                       # the green top
        return RailEMU.body_colour(self, x, y)
    def nose_colour(self, u, y):
        if self.nose_glass(u, y): return 'glass'
        if y > self.nose_top(u) - 0.55 * (1 - u): return 'roof'
        s = stripes(y, self.stripe_y, self.stripe_h)
        return s if s and u < 0.7 else 'nose'
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.04, 0.55) else 'body'


class Fuxing(RailEMU):
    """China Railway CR400AF 'Fuxing': long dragon nose, white with a red 'wing' band"""
    title = 'China CR400AF Fuxing'
    L, L_car = 27.1, 25.0
    nose_len = 9.5
    roof, eave = 4.0, 3.35
    hw = 1.68
    stripe_y, stripe_h = 1.45, 0.06
    pans = (6.0,)
    layout, rows, pitch = 'hs', (3, 2), 1.0
    pal = _pal('#F6F7F7', '#E6E9EA', '#DCE0E2', '#8E969B', '#EDEFF0', ('#C8102E', '#C8102E', '#F6F7F7', '#C8102E', '#F6F7F7', '#F6F7F7'),
               '#2F4F7F', nose='#F6F7F7')
    def nose_top(self, u): return self.roof - (self.roof - 1.2) * u ** 1.5
    def nose_bot(self, u): return self.skirt_bot + 0.45 * u ** 2.3
    def nose_scale(self, u): return 1 - 0.55 * u ** 1.9
    def nose_glass(self, u, y): return 0.3 < u < 0.55 and self.nose_top(u) - 0.6 < y < self.nose_top(u) - 0.06
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.0, 0.6) else 'body'


class Frecciarossa(RailEMU):
    """Trenitalia Frecciarossa 1000 (ETR 400): long red nose, red body, silver window band, grey skirt"""
    title = 'Frecciarossa 1000'
    L, L_car = 26.5, 25.0
    nose_len = 8.0
    roof, eave = 3.9, 3.3
    hw = 1.46
    win = (1.7, 2.6)
    stripe_y, stripe_h = 1.25, 0.05
    pans = (5.0,)
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#C8102E', '#B40E28', '#4A4F55', '#2F3338', '#B90F2A', ('#9AA0A6', '#9AA0A6', '#C8102E', '#C8102E', '#C8102E', '#C8102E'),
               '#3A3F45', '#C8102E', nose='#C8102E')
    def nose_top(self, u): return self.roof - (self.roof - 1.15) * u ** 1.6
    def nose_bot(self, u): return self.skirt_bot + 0.45 * u * u
    def nose_scale(self, u): return 1 - 0.55 * u ** 2.0
    def nose_glass(self, u, y): return 0.2 < u < 0.6 and self.nose_top(u) - 0.85 < y < self.nose_top(u) - 0.07
    def window_colour(self, x, y): return 'frame' if (x % 1.9) < 0.1 else 'glass'


class E320(RailEMU):
    """Eurostar e320 (Siemens Velaro): dark navy-grey with a yellow band and the white-silver nose tip"""
    title = 'Eurostar e320'
    L, L_car = 25.7, 24.2
    nose_len = 6.0
    roof, eave = 3.9, 3.3
    hw = 1.46
    win = (1.7, 2.6)
    stripe_y, stripe_h = 1.25, 0.05
    pans = (6.0,)
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#2B3340', '#222932', '#3D4552', '#1E242C', '#2B3340', ('#F2C230', '#F2C230', '#2B3340', '#2B3340', '#2B3340', '#2B3340'),
               '#3C4C6B', '#F2C230', nose='#2B3340')
    cab_kind = 'driver'
    def nose_top(self, u): return self.roof - (self.roof - 1.15) * u ** 1.7
    def nose_bot(self, u): return self.skirt_bot + 0.45 * u * u
    def nose_scale(self, u): return 1 - 0.5 * u ** 2.2
    def nose_glass(self, u, y): return 0.18 < u < 0.6 and self.nose_top(u) - 1.0 < y < self.nose_top(u) - 0.07
    def nose_colour(self, u, y):
        if self.nose_glass(u, y): return 'glass'
        if u > 0.85: return 'skirt'
        s = stripes(y, self.stripe_y, self.stripe_h)
        return s if s and u < 0.8 else 'nose'
    def window_colour(self, x, y): return 'frame' if (x % 1.9) < 0.1 else 'glass'


class AveliaLiberty(RailEMU):
    """Amtrak Avelia Liberty (Acela, 2024): silver-white, long low blue nose band, tilting-train profile"""
    title = 'Amtrak Avelia Liberty'
    L, L_car = 22.0, 21.5
    nose_len = 6.5
    roof, eave = 3.95, 3.35
    hw = 1.5
    stripe_y, stripe_h = 1.3, 0.06
    pans = (4.0,)
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#E9ECEF', '#D3D8DD', '#5A6470', '#3A424C', '#DDE2E6', ('#1D4F91', '#1D4F91', '#3FA9F5', '#E9ECEF', '#E9ECEF', '#E9ECEF'),
               '#2E4A78', '#E0465A', nose='#E9ECEF')
    def nose_top(self, u): return self.roof - (self.roof - 1.25) * u ** 1.4
    def nose_bot(self, u): return self.skirt_bot + 0.5 * u * u
    def nose_scale(self, u): return 1 - 0.45 * u ** 2.0
    def nose_glass(self, u, y): return 0.22 < u < 0.6 and self.nose_top(u) - 0.8 < y < self.nose_top(u) - 0.07
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.25, 0.85) else 'body'


class KTXSancheon(RailEMU):
    """Korail KTX-Sancheon (Hyundai Rotem): named after a Korean mountain fish, white with a blue-grey wave and teal"""
    title = 'KTX-Sancheon'
    L, L_car = 23.0, 22.5
    nose_len = 7.5
    roof, eave = 3.85, 3.25
    hw = 1.45
    stripe_y, stripe_h = 1.35, 0.06
    pans = (5.0,)
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#F4F6F7', '#DDE2E6', '#5C6A78', '#3C4652', '#E6EAED', ('#28568C', '#28568C', '#2FA6A0', '#F4F6F7', '#F4F6F7', '#F4F6F7'),
               '#2E5C8C', '#E2402F', nose='#F4F6F7')
    def nose_top(self, u): return self.roof - (self.roof - 1.2) * u ** 1.45
    def nose_bot(self, u): return self.skirt_bot + 0.45 * u ** 2.2
    def nose_scale(self, u): return 1 - 0.5 * u ** 1.9
    def nose_glass(self, u, y): return 0.22 < u < 0.55 and self.nose_top(u) - 0.7 < y < self.nose_top(u) - 0.07
    def window_colour(self, x, y): return 'glass' if win_row(x, 1.1, 0.7) else 'body'


class TGVinOui(RailEMU):
    """SNCF TGV Duplex 'inOui': grey-white with the purple-and-grey inOui nose, double-deck (drawn single-deck)"""
    title = 'TGV inOui'
    L, L_car = 22.15, 21.85
    nose_len = 6.5
    roof, eave = 4.05, 3.5
    hw = 1.45
    win = (1.75, 2.75)
    stripe_y, stripe_h = 1.3, 0.06
    pans = (4.5,)
    layout, rows, pitch = 'hs', (2, 2), 1.0
    pal = _pal('#ECEDEF', '#D5D8DC', '#5F646B', '#3C4046', '#E1E3E6', ('#5E2A84', '#5E2A84', '#8A8F96', '#ECEDEF', '#ECEDEF', '#ECEDEF'),
               '#5E2A84', '#E0465A', nose='#8A8F96')
    def nose_top(self, u): return self.roof - (self.roof - 1.3) * u ** 1.55
    def nose_bot(self, u): return self.skirt_bot + 0.45 * u ** 2
    def nose_scale(self, u): return 1 - 0.5 * u ** 2.0
    def nose_glass(self, u, y): return 0.2 < u < 0.58 and self.nose_top(u) - 0.85 < y < self.nose_top(u) - 0.07
    def nose_colour(self, u, y):
        if self.nose_glass(u, y): return 'glass'
        if y < self.stripe_y + 0.6 + 0.6 * u: return 'st0' if u > 0.25 else 'nose'
        return 'nose'
    def window_colour(self, x, y): return 'frame' if (x % 1.7) < 0.1 else 'glass'


TRAINS = {
    'las_vegas': LasVegas,
    'seattle': Seattle,
    'tokyo': Tokyo,
    'innovia': Innovia,
    'chongqing': Chongqing,
    'transrapid': Transrapid,
    'l0': L0,
    'n700s': N700S,
    'ice3': ICE3,
    'gg1': GG1,
    'r211': R211,
    'r160': R160,
    'tube2009': Tube2009,
    'sstock': SStock,
    'ginza1000': Ginza1000,
    'mp14': MP14,
    'moskva': Moskva, 'berlin_ik': BerlinIK, 'mtr_mtrain': MTRCity, 'azur': Azur,
    'disney_mk7': DisneyMk7, 'osaka': OsakaMonorail, 'mumbai': Mumbai, 'yui_rail': YuiRail, 'palm_jumeirah': PalmJumeirah,
    'crrc600': CRRC600, 'linimo': Linimo, 'ecobee': Ecobee, 'changsha': ChangshaMaglev, 'beijing_s1': BeijingS1,
    'mlx01': MLX01, 'tr09': TR09, 'birmingham': Birmingham,
    'e5': E5, 'fuxing': Fuxing, 'frecciarossa': Frecciarossa, 'e320': E320, 'avelia': AveliaLiberty, 'ktx_sancheon': KTXSancheon, 'tgv_inoui': TGVinOui,
}

SPECS = {  # IR json bits per train: (max km/h, kW per powered car, tonnes, passengers, works, kind)
    'las_vegas': dict(speed=80, kw=600, t=15.5, pax=20, works='Bombardier', kind='monorail'),
    'seattle': dict(speed=72, kw=450, t=14.0, pax=18, works='ALWEG', kind='monorail'),
    'tokyo': dict(speed=80, kw=640, t=17.0, pax=24, works='Hitachi', kind='monorail'),
    'innovia': dict(speed=80, kw=700, t=16.0, pax=22, works='Bombardier', kind='monorail'),
    'chongqing': dict(speed=75, kw=640, t=17.5, pax=26, works='CRRC Changchun', kind='monorail'),
    'transrapid': dict(speed=431, kw=3000, t=60, pax=40, works='Transrapid International', kind='maglev'),
    'l0': dict(speed=505, kw=3500, t=40, pax=24, works='Mitsubishi Heavy Industries', kind='maglev'),
    'n700s': dict(speed=300, kw=1220, t=45, pax=60, works='Hitachi / Nippon Sharyo', kind='overhead wire'),
    'ice3': dict(speed=300, kw=1000, t=52, pax=48, works='Siemens', kind='overhead wire'),
    'gg1': dict(speed=160, kw=3450, t=216, pax=2, works='Pennsylvania Railroad', kind='overhead wire'),
    'r211': dict(speed=88, kw=460, t=39, pax=40, works='Kawasaki', kind='third rail'),
    'r160': dict(speed=88, kw=460, t=38, pax=40, works='Alstom / Kawasaki', kind='third rail'),
    'tube2009': dict(speed=80, kw=390, t=27, pax=32, works='Bombardier', kind='third rail'),
    'sstock': dict(speed=100, kw=420, t=30, pax=36, works='Bombardier', kind='third rail'),
    'ginza1000': dict(speed=65, kw=380, t=28, pax=34, works='Nippon Sharyo', kind='third rail'),
    'mp14': dict(speed=80, kw=400, t=27, pax=32, works='Alstom', kind='third rail'),
    'moskva': dict(speed=90, kw=440, t=34, pax=40, works='Metrowagonmash', kind='third rail'),
    'berlin_ik': dict(speed=70, kw=360, t=24, pax=30, works='Stadler', kind='third rail'),
    'mtr_mtrain': dict(speed=80, kw=480, t=40, pax=48, works='Metro-Cammell', kind='third rail'),
    'azur': dict(speed=72, kw=420, t=28, pax=34, works='Bombardier / Alstom', kind='third rail'),
    'disney_mk7': dict(speed=65, kw=500, t=14, pax=22, works='Bombardier', kind='monorail'),
    'osaka': dict(speed=75, kw=620, t=17, pax=26, works='Hitachi', kind='monorail'),
    'mumbai': dict(speed=80, kw=560, t=15, pax=24, works='Scomi', kind='monorail'),
    'yui_rail': dict(speed=65, kw=500, t=16, pax=22, works='Hitachi', kind='monorail'),
    'palm_jumeirah': dict(speed=70, kw=520, t=15, pax=22, works='Hitachi', kind='monorail'),
    'crrc600': dict(speed=600, kw=4000, t=50, pax=40, works='CRRC Qingdao Sifang', kind='maglev'),
    'linimo': dict(speed=100, kw=900, t=17, pax=30, works='Chubu HSST', kind='maglev'),
    'ecobee': dict(speed=110, kw=800, t=16, pax=28, works='Hyundai Rotem', kind='maglev'),
    'changsha': dict(speed=100, kw=1000, t=20, pax=34, works='CRRC Zhuzhou', kind='maglev'),
    'beijing_s1': dict(speed=100, kw=1100, t=21, pax=36, works='CRRC Tangshan', kind='maglev'),
    'mlx01': dict(speed=581, kw=3800, t=40, pax=20, works='Mitsubishi / Kawasaki', kind='maglev'),
    'tr09': dict(speed=450, kw=3200, t=55, pax=40, works='ThyssenKrupp / Siemens', kind='maglev'),
    'birmingham': dict(speed=42, kw=180, t=8, pax=16, works='Metro-Cammell', kind='maglev'),
    'e5': dict(speed=320, kw=1200, t=45, pax=56, works='Hitachi / Kawasaki', kind='overhead wire'),
    'fuxing': dict(speed=350, kw=1300, t=50, pax=60, works='CRRC Qingdao Sifang', kind='overhead wire'),
    'frecciarossa': dict(speed=360, kw=1200, t=50, pax=48, works='Hitachi Rail / Bombardier', kind='overhead wire'),
    'e320': dict(speed=320, kw=1100, t=52, pax=48, works='Siemens', kind='overhead wire'),
    'avelia': dict(speed=257, kw=1100, t=55, pax=44, works='Alstom', kind='overhead wire'),
    'ktx_sancheon': dict(speed=305, kw=1100, t=50, pax=46, works='Hyundai Rotem', kind='overhead wire'),
    'tgv_inoui': dict(speed=320, kw=1100, t=55, pax=56, works='Alstom', kind='overhead wire'),
}


def loco_json(tid, cls, s, folder, pack='pride_rail', label='Pride Rail'):
    h = 'immersiverailroading:models/rolling_stock/' + pack + '/%s/%s.obj'
    return {
        'era': 'diesel', 'name': '%s (%s, electric %s)' % (cls.title, label, s['kind']), 'works': s['works'],
        'model': h % (tid, tid + '_front'), 'model_gauge_m': 1.435,
        'tex_variants': {'Real livery': '', 'Pride rainbow': 'pride', 'Trans flag': 'trans'},
        'multi_unit_capable': True,
        'properties': {'fuel_efficiency_%': 100, 'fuel_capacity_l': 120, 'power_hp': int(s['kw'] * 1.341),
                       'tractive_effort_lbf': int(s['kw'] * 55), 'max_speed_kmh': s['speed'],
                       'weight_kg': int(s['t'] * 1000), 'horn_sustained': True},
        'passenger': {'slots': s['pax'], 'center_x': -0.6, 'center_y': cls.floor + 0.35,
                      'length': cls.L - cls.nose_len - 2.0, 'width': cls.hw * 1.3},
        'trucks': {'front': cls.trucks, 'rear': -cls.trucks},
        'couplers': {'front_offset': 0.0, 'rear_offset': 0.12},   # ~0.25 m between car bodies, like a real EMU's gangway gap
    }


def car_json(tid, cls, s, pack='pride_rail', label='Pride Rail'):
    h = 'immersiverailroading:models/rolling_stock/' + pack + '/%s/%s.obj'
    return {
        'name': '%s passenger car (%s)' % (cls.title, label),
        'model': h % (tid, tid + '_car'), 'model_gauge_m': 1.435,
        'tex_variants': {'Real livery': '', 'Pride rainbow': 'pride', 'Trans flag': 'trans'},
        'properties': {'weight_kg': int(s['t'] * 900)},
        'passenger': {'slots': int(s['pax'] * 1.4), 'center_x': 0, 'center_y': cls.floor + 0.35,
                      'length': cls.L - 2.0, 'width': cls.hw * 1.3},
        'trucks': {'front': cls.trucks, 'rear': -cls.trucks},
        'couplers': {'front_offset': 0.12, 'rear_offset': 0.12},
    }


STYLES = (  # (stock id prefix, model folder, name label, smooth?)
    ('pride_', 'pride_rail', 'Pride Rail', True),
    ('pride_voxel_', 'pride_rail_voxel', 'Pride Rail Voxel', False),
)


FORMATION = {  # middle cars between the two cab ends (real sets, shortened where 16 cars would be silly)
    'las_vegas': 2, 'seattle': 1, 'tokyo': 4, 'innovia': 2, 'chongqing': 2,
    'transrapid': 1, 'l0': 1, 'n700s': 4, 'ice3': 2, 'gg1': 4,
    'r211': 3, 'r160': 3, 'tube2009': 2, 'sstock': 3, 'ginza1000': 2, 'mp14': 2,
    'moskva': 3, 'berlin_ik': 2, 'mtr_mtrain': 3, 'azur': 3,
    'disney_mk7': 3, 'osaka': 2, 'mumbai': 2, 'yui_rail': 0, 'palm_jumeirah': 1,
    'crrc600': 2, 'linimo': 1, 'ecobee': 0, 'changsha': 1, 'beijing_s1': 4, 'mlx01': 1, 'tr09': 1, 'birmingham': 0,
    'e5': 4, 'fuxing': 4, 'frecciarossa': 4, 'e320': 4, 'avelia': 3, 'ktx_sancheon': 3, 'tgv_inoui': 3,
}


def units(prefix, label, cls_by_id):
    """IR 'multiple unit' sets: one item places the whole train, coupled, cab at both ends (GG1: loco + coaches)"""
    out = {}
    for tid, cls in cls_by_id.items():
        for vname, tex in (('', ''), (' - Pride rainbow', 'pride'), (' - Trans flag', 'trans')):
            cons = [{'stock': prefix + tid}]
            cons += [{'stock': prefix + tid + '_car'} for _ in range(FORMATION[tid])]
            if tid != 'gg1':
                cons.append({'stock': prefix + tid, 'direction': 'flipped'})
            if tex:
                for c in cons:
                    c['texture'] = tex
            uid = '%s%s_set%s' % (prefix, tid, ('_' + tex) if tex else '')
            out[uid] = {'name': '%s full train (%s%s)' % (cls.title, label, vname), 'consist': cons,
                        'add_tooltip': ['%d cars, coupled' % len(cons), 'Electric - runs on RF / FE']}
    return out


SIGNS = {}


def main(only):
    stock = {'pack': 'Pride Rail', 'locomotives': [], 'passenger': [], 'multiple_unit': []}
    for prefix, pack, label, smooth in STYLES:
        for tid, cls in TRAINS.items():
            stock['locomotives'].append(prefix + tid)
            stock['passenger'].append(prefix + tid + '_car')
            if only and tid not in only and (prefix + tid) not in only:
                continue
            folder = os.path.join(ASSETS, 'models/rolling_stock', pack, tid)
            for part, obj in (('front', cls(front=True)), ('car', cls(front=False))):
                m = obj.build('%s_%s' % (tid, part), smooth=smooth)
                SIGNS['rolling_stock/%s/%s%s%s.json' % ('locomotives' if part == 'front' else 'passenger', prefix, tid,
                                                         '' if part == 'front' else '_car')] = obj.signs
                n = m.write_ir(folder, {'pride': PRIDE, 'trans': TRANS})
                m.write_bbmodel(os.path.join(BB, pack, '%s_%s.bbmodel' % (tid, part)))
                print('%-18s %-22s %6d parts' % (pack, m.name, n))
            s = SPECS[tid]
            for sub, data in (('locomotives', loco_json(tid, cls, s, folder, pack, label)), ('passenger', car_json(tid, cls, s, pack, label))):
                p = os.path.join(ASSETS, 'rolling_stock', sub, '%s%s%s.json' % (prefix, tid, '' if sub == 'locomotives' else '_car'))
                os.makedirs(os.path.dirname(p), exist_ok=True)
                json.dump(data, open(p, 'w'), indent=2)
    for prefix, pack, label, smooth in STYLES:
        for uid, data in units(prefix, label, TRAINS).items():
            stock['multiple_unit'].append(uid)
            p = os.path.join(ASSETS, 'rolling_stock/multiple_unit', uid + '.json')
            os.makedirs(os.path.dirname(p), exist_ok=True)
            json.dump(data, open(p, 'w'), indent=2)
    json.dump(stock, open(os.path.join(ASSETS, 'rolling_stock/stock.json'), 'w'), indent=2)
    if not only:     # live sign / screen surfaces for IR Extras' renderer
        sp = os.path.join(ROOT, 'src/main/resources/assets/irextras/pride_signs.json')
        json.dump(SIGNS, open(sp, 'w'), separators=(',', ':'))
        print('signs:', sum(len(v) for v in SIGNS.values()), 'surfaces on', len(SIGNS), 'models')


if __name__ == '__main__':
    main(sys.argv[1:])
