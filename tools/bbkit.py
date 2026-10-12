#!/usr/bin/env python3
"""bbkit - build detailed IR Extras block models in code and save them as real Blockbench projects.

Her ask 2026-10-09: "make everything in Blockbench and make it as complex as you physically can and make it wire up ...
low voltage gets the little 12 volt wire, high voltage gets the other wires ... if it has buttons, put buttons on it."

A model is a list of boxes, each face painted with a material into one auto-packed texture atlas. save() writes
  blockbench/models/<name>.bbmodel   (open it in Blockbench: groups, names, embedded texture)
  assets/irextras/models/block/<name>.json + textures/blocks/<name>.png   (what the game loads)
Models face NORTH (the front is the z=0 side; a grid machine's live panel is drawn at z=-1 over x = 16-u, y = v).
Java block rules hold: one rotation axis per box, angle 0 / +-22.5 / +-45, coordinates -16..32.
"""
import base64, io, json, math, os, random, sys, uuid
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, '..')
ASSETS = os.path.join(REPO, 'src/main/resources/assets/irextras')
BBDIR = os.path.join(REPO, 'blockbench/models')
sys.path.insert(0, os.path.join(REPO, 'blockbench/tools'))
from bbpaint import FONT   # 5x7 pixel font

DIRS = ('north', 'south', 'east', 'west', 'up', 'down')


def _hex(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _cl(v):
    return max(0, min(255, int(v)))


# ------------------------------------------------------------------------------------------------ materials
# a material paints one face rect: fn(img, x0, y0, w, h, rng, arg); "name:arg" passes arg
PAL = {
    'ral7035': (197, 199, 196), 'ral7032': (181, 177, 160), 'ral7016': (56, 62, 66), 'ral6005': (40, 72, 52),
    'ral5010': (22, 66, 124), 'ral3000': (170, 30, 26), 'ral1023': (240, 196, 24), 'ral9005': (22, 22, 24),
    'ral9010': (238, 236, 226), 'ral2004': (232, 96, 20), 'tank': (92, 108, 100), 'steel': (120, 126, 132),
    'dark': (58, 62, 68), 'black': (26, 26, 28), 'rubber': (36, 36, 38), 'white': (236, 236, 232),
}


def base(img, x0, y0, w, h, rng, rgb, noise=4, bevel=True, grime=True):
    for y in range(h):
        g = (-10 * (y / max(1, h - 1)) ** 2) if grime and h >= 6 else 0
        for x in range(w):
            n = rng.randint(-noise, noise)
            e = 0
            if bevel and w >= 4 and h >= 4:
                if x == 0 or y == 0: e = 14
                elif x == w - 1 or y == h - 1: e = -18
            img.putpixel((x0 + x, y0 + y), (_cl(rgb[0] + n + e + g), _cl(rgb[1] + n + e + g), _cl(rgb[2] + n + e + g), 255))


def m_paint(img, x0, y0, w, h, rng, arg):
    base(img, x0, y0, w, h, rng, PAL.get(arg) or _hex(arg))


def m_galv(img, x0, y0, w, h, rng, arg):                  # hot-dip galvanised: spangled grey
    for y in range(h):
        for x in range(w):
            s = rng.choice((0, 0, 0, 8, -8, 16))
            v = 158 + s + rng.randint(-3, 3)
            img.putpixel((x0 + x, y0 + y), (_cl(v), _cl(v + 4), _cl(v + 8), 255))


def m_concrete(img, x0, y0, w, h, rng, arg):
    for y in range(h):
        for x in range(w):
            v = 150 + rng.randint(-10, 10) - (25 if rng.random() < 0.04 else 0)
            img.putpixel((x0 + x, y0 + y), (_cl(v), _cl(v - 2), _cl(v - 7), 255))


def m_metal(img, x0, y0, w, h, rng, arg):                 # bare metal with a sheen stripe: copper, brass, steel
    c = {'copper': (196, 118, 60), 'brass': (200, 168, 78), 'steel': (172, 176, 182), 'alu': (200, 204, 208),
         'tin': (210, 212, 214)}.get(arg, (172, 176, 182))
    for y in range(h):
        for x in range(w):
            sheen = 30 if (x + y) % max(3, (w + h) // 3) == 0 else 0
            n = rng.randint(-6, 6)
            img.putpixel((x0 + x, y0 + y), (_cl(c[0] + n + sheen), _cl(c[1] + n + sheen), _cl(c[2] + n + sheen), 255))


def m_porcelain(img, x0, y0, w, h, rng, arg):            # glazed insulator: brown (or grey) with a glossy streak
    c = (122, 62, 38) if arg != 'grey' else (196, 196, 190)
    for y in range(h):
        for x in range(w):
            hl = 45 if x == max(1, w // 4) else 20 if x == max(1, w // 4) + 1 else 0
            img.putpixel((x0 + x, y0 + y), (_cl(c[0] + hl), _cl(c[1] + hl), _cl(c[2] + hl), 255))


def m_haz(img, x0, y0, w, h, rng, arg):                   # black/yellow hazard stripes
    for y in range(h):
        for x in range(w):
            on = ((x + y) // 3) % 2 == 0
            img.putpixel((x0 + x, y0 + y), (240, 196, 24, 255) if on else (24, 24, 24, 255))


def m_louvre(img, x0, y0, w, h, rng, arg):
    for y in range(h):
        for x in range(w):
            v = 30 if y % 3 == 0 else 150 if y % 3 == 1 else 120
            img.putpixel((x0 + x, y0 + y), (v, v + 2, v + 4, 255))


def m_mesh(img, x0, y0, w, h, rng, arg):
    for y in range(h):
        for x in range(w):
            wire = x % 3 == 0 or y % 3 == 0
            img.putpixel((x0 + x, y0 + y), (150, 154, 158, 255) if wire else (20, 22, 24, 255))


def m_fins(img, x0, y0, w, h, rng, arg):                  # radiator fins
    for y in range(h):
        for x in range(w):
            v = 68 if x % 2 == 0 else 112
            img.putpixel((x0 + x, y0 + y), (v - 6, v + 8, v, 255))


def m_glass(img, x0, y0, w, h, rng, arg):
    for y in range(h):
        for x in range(w):
            hl = 60 if abs((x - y) - w // 3) <= 0 else 0
            img.putpixel((x0 + x, y0 + y), (_cl(70 + hl), _cl(96 + hl), _cl(110 + hl), 255))


def m_screen(img, x0, y0, w, h, rng, arg):                # a dark screen with faint content lines
    col = _hex(arg) if arg else (60, 220, 110)
    for y in range(h):
        for x in range(w):
            line = y % 3 == 1 and 1 <= x < w - 1 and rng.random() < 0.7
            c = col if line else (10, 16, 14)
            img.putpixel((x0 + x, y0 + y), c + (255,))


def m_lamp(img, x0, y0, w, h, rng, arg):                  # a lamp lens: bright centre, darker rim
    c = _hex(arg)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = max(1.0, min(w, h) / 2)
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy) / r
            k = 1.25 - 0.6 * d
            img.putpixel((x0 + x, y0 + y), (_cl(c[0] * k), _cl(c[1] * k), _cl(c[2] * k), 255))


def m_cable(img, x0, y0, w, h, rng, arg):                 # insulated cable: colour with a sheen line along it
    c = _hex(arg) if arg else (28, 28, 30)
    for y in range(h):
        for x in range(w):
            along = y if h > w else x
            across = x if h > w else y
            span = w if h > w else h
            sheen = 26 if across == span // 3 else 0
            img.putpixel((x0 + x, y0 + y), (_cl(c[0] + sheen), _cl(c[1] + sheen), _cl(c[2] + sheen), 255))


def m_wood(img, x0, y0, w, h, rng, arg):
    for y in range(h):
        for x in range(w):
            g = 14 if (x * 7 + y // 4) % 9 == 0 else 0
            img.putpixel((x0 + x, y0 + y), (_cl(122 - g + rng.randint(-4, 4)), _cl(84 - g), _cl(52 - g), 255))


def m_battery(img, x0, y0, w, h, rng, arg):
    for y in range(h):
        for x in range(w):
            v = 218 if x % 4 else 192
            img.putpixel((x0 + x, y0 + y), (v, v + 2, v + 4, 255))


def m_gauge(img, x0, y0, w, h, rng, arg):                 # analogue dial: white face, ticks, black needle, bezel
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = min(w, h) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy)
            if d > r - 0.6: c = (40, 40, 42)
            elif d > r - 1.6 and int(math.degrees(math.atan2(y - cy, x - cx)) + 360) % 30 < 8 and y < cy + 1: c = (20, 20, 20)
            else: c = (242, 240, 230)
            img.putpixel((x0 + x, y0 + y), c + (255,))
    ang = math.radians(-135 + (hash(arg) % 90 if arg else 40))
    for t in range(int(r * 0.8)):
        px, py = cx + math.sin(ang) * t, cy - math.cos(ang) * t
        if 0 <= px < w and 0 <= py < h: img.putpixel((x0 + int(px), y0 + int(py)), (200, 20, 20, 255))


def _text(img, x0, y0, w, h, s, fg, bg, plate=True):
    for y in range(h):
        for x in range(w):
            e = (-30 if x in (0, w - 1) or y in (0, h - 1) else 0) if plate else 0
            img.putpixel((x0 + x, y0 + y), (_cl(bg[0] + e), _cl(bg[1] + e), _cl(bg[2] + e), 255))
    s = s.upper()
    lines = s.split('|')
    tw1 = max(len(l) for l in lines) * 6 - 1
    th1 = len(lines) * 8 - 1
    if tw1 > w - 2 or th1 > h - 2:                       # too long even at 1x: draw it big, shrink it to fit
        big = Image.new('RGBA', (tw1 + 2, th1 + 2), bg + (255,))
        _text(big, 0, 0, tw1 + 2, th1 + 2, s, fg, bg, plate=False)
        k = min((w - 2) / (tw1 + 2), (h - 2) / (th1 + 2))
        sm = big.resize((max(1, int((tw1 + 2) * k)), max(1, int((th1 + 2) * k))), Image.BOX)
        img.paste(sm, (x0 + (w - sm.width) // 2, y0 + (h - sm.height) // 2))
        return
    sc = 1
    while True:
        tw = max(len(l) for l in lines) * 6 * (sc + 1) - (sc + 1)
        th = len(lines) * 8 * (sc + 1) - (sc + 1)
        if tw > w - 2 or th > h - 2: break
        sc += 1
    ty = y0 + (h - (len(lines) * 8 * sc - sc)) // 2
    for line in lines:
        lw = len(line) * 6 * sc - sc
        tx = x0 + (w - lw) // 2
        for ch in line:
            g = FONT.get(ch, FONT[' '])
            for gy, row in enumerate(g):
                for gx, bit in enumerate(row):
                    if bit == '1':
                        for a in range(sc):
                            for b in range(sc):
                                X, Y = tx + gx * sc + a, ty + gy * sc + b
                                if x0 <= X < x0 + w and y0 <= Y < y0 + h: img.putpixel((X, Y), fg + (255,))
            tx += 6 * sc
        ty += 8 * sc


def m_label(img, x0, y0, w, h, rng, arg):                 # engraved white plate, black letters ("A|B" = two lines)
    _text(img, x0, y0, w, h, arg, (16, 16, 16), (232, 232, 224))


def m_plate(img, x0, y0, w, h, rng, arg):                 # stainless nameplate, dark letters
    _text(img, x0, y0, w, h, arg, (30, 30, 34), (186, 190, 194))


def m_danger(img, x0, y0, w, h, rng, arg):                # red-on-white DANGER sign
    _text(img, x0, y0, w, h, arg or 'DANGER', (200, 20, 20), (244, 244, 240))


def m_warn(img, x0, y0, w, h, rng, arg):                  # yellow triangle, black lightning bolt
    for y in range(h):
        for x in range(w):
            fy = y / max(1, h - 1)
            half = fy * 0.5
            fx = x / max(1, w - 1) - 0.5
            inside = fy >= 0.08 and abs(fx) <= half * 0.95
            edge = inside and (fy > 0.9 or abs(fx) > half * 0.95 - 0.12)
            bolt = inside and abs(fx + (fy - 0.55) * 0.5) < 0.07 and 0.3 < fy < 0.85
            c = (20, 20, 20) if edge or bolt else (244, 200, 30) if inside else (226, 222, 210)
            img.putpixel((x0 + x, y0 + y), c + (255,))


def m_led(img, x0, y0, w, h, rng, arg):                   # dot-matrix display: lit amber dots
    c = _hex(arg) if arg else (255, 170, 30)
    for y in range(h):
        for x in range(w):
            on = x % 2 == 0 and y % 2 == 0 and rng.random() < 0.55
            img.putpixel((x0 + x, y0 + y), (c if on else (26, 18, 10)) + (255,))


MATS = {'paint': m_paint, 'galv': m_galv, 'concrete': m_concrete, 'metal': m_metal, 'porcelain': m_porcelain,
        'haz': m_haz, 'louvre': m_louvre, 'mesh': m_mesh, 'fins': m_fins, 'glass': m_glass, 'screen': m_screen,
        'lamp': m_lamp, 'cable': m_cable, 'wood': m_wood, 'battery': m_battery, 'gauge': m_gauge, 'label': m_label,
        'plate': m_plate, 'danger': m_danger, 'warn': m_warn, 'led': m_led}
# texture pixels per model unit, by material (text needs more)
PXU = {'label': 12, 'plate': 12, 'danger': 12, 'warn': 6, 'gauge': 8, 'led': 4, 'screen': 4, 'lamp': 4}


def _mat(spec):
    if spec in PAL: return 'paint', spec
    if ':' in spec:
        k, a = spec.split(':', 1)
        return k, a
    return spec, ''


# ------------------------------------------------------------------------------------------------ model
class Model:
    def __init__(self, name, px=2, block=None):
        self.name, self.px = name, px
        self.block = block or name           # the controls layout is per block (all states share it)
        self.surfaces = []                   # live controls: drawn and worked by the game, see ctl()
        self.boxes = []          # (name, from, to, rot, {dir: mat}, group)
        self.group = 'body'
        self.groups = []

    def g(self, name):
        """start a named group (Blockbench outliner folder)"""
        self.group = name
        return self

    def box(self, a, b, mat, name=None, faces=None, rot=None, **per):
        """a box from a to b; mat paints every face, per-face override with north='label:HI' etc, faces= limits faces"""
        a = [float(v) for v in a]
        b = [float(v) for v in b]
        lo = [min(a[i], b[i]) for i in range(3)]
        hi = [max(a[i], b[i]) for i in range(3)]
        fm = {}
        for d in (faces or DIRS):
            fm[d] = per.get(d, mat)
        self.boxes.append((name or self.group, lo, hi, rot, fm, self.group))
        if self.group not in self.groups: self.groups.append(self.group)
        return self

    # ---- shapes ----------------------------------------------------------------------------------
    def cyl(self, c, r, h, axis, mat, name=None, cap=None):
        """octagonal prism: centre c (start of the axis), radius r, length h along axis 'x'|'y'|'z'"""
        i = 'xyz'.index(axis)
        a, b = list(c), list(c)
        for k in range(3):
            if k == i: a[k], b[k] = c[k], c[k] + h
            else: a[k], b[k] = c[k] - r * 0.92, c[k] + r * 0.92
        ends = {0: ('east', 'west'), 1: ('up', 'down'), 2: ('north', 'south')}[i]
        kw = {e: cap for e in ends} if cap else {}
        self.box(a, b, mat, name, **kw)
        origin = [c[k] if k != i else c[k] + h / 2 for k in range(3)]
        self.box(a, b, mat, name, rot={'axis': axis, 'angle': 45, 'origin': origin}, **kw)
        return self

    def bolt(self, x, y, z, face='north', s=0.5, mat='metal:steel'):
        """a bolt head standing 0.3 out of a face at (x, y, z)"""
        d = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}[face]
        p = [x, y, z]
        a = [v - s / 2 for v in p]
        b = [v + s / 2 for v in p]
        for k in range(3):
            if d[k]: a[k], b[k] = p[k], p[k] + d[k] * 0.3
        return self.box(a, b, mat, 'bolt')

    def bolts(self, x0, x1, y, z, n, face='north', **k):
        for i in range(n):
            self.bolt(x0 + (x1 - x0) * (i / max(1, n - 1)), y, z, face, **k)
        return self

    def louvres(self, x0, x1, y0, y1, z, n=5, mat='dark'):
        """slanted slats on a north face at z (stand proud toward -z)"""
        step = (y1 - y0) / n
        self.box((x0, y0, z - 0.05), (x1, y1, z), 'louvre', 'louvre back', faces=('north',))
        for i in range(n):
            y = y0 + i * step + step * 0.3
            self.box((x0, y, z - 0.45), (x1, y + step * 0.45, z), mat, 'slat', rot={'axis': 'x', 'angle': -22.5, 'origin': [x0, y, z]})
        return self

    def gland(self, x, y, z, face='down', r=0.45, cable='cable', mat='metal:brass', length=1.2):
        """a cable gland on a face and a short tail of cable leaving it"""
        if face == 'down':
            self.cyl((x, y - 0.6, z), r * 1.4, 0.6, 'y', mat, 'gland')
            self.cyl((x, y - 0.6 - length, z), r, length, 'y', cable, 'cable tail')
        elif face == 'up':
            self.cyl((x, y, z), r * 1.4, 0.6, 'y', mat, 'gland')
            self.cyl((x, y + 0.6, z), r, length, 'y', cable, 'cable tail')
        elif face in ('south', 'north'):
            sgn = 1 if face == 'south' else -1
            z0 = z if sgn > 0 else z - 0.6
            self.cyl((x, y, z0), r * 1.4, 0.6, 'z', mat, 'gland')
            self.cyl((x, y, z0 + 0.6 if sgn > 0 else z0 - length), r, length, 'z', cable, 'cable tail')
        else:
            sgn = 1 if face == 'east' else -1
            x0 = x if sgn > 0 else x - 0.6
            self.cyl((x0, y, z), r * 1.4, 0.6, 'x', mat, 'gland')
            self.cyl((x0 + 0.6 if sgn > 0 else x0 - length, y, z), r, length, 'x', cable, 'cable tail')
        return self

    def terminal_12v(self, x, y, z, face='south'):
        """the 12 V DC control terminal box every piece of gear has: grey box, '12V DC' label, red + black studs,
        a control-wire stud, and a gland underneath for the instrument wire. (x,y,z) = bottom-centre on the face."""
        g = self.group
        self.g('12 V terminal')
        w, h, d = 3.0, 2.4, 1.2
        if face in ('south', 'north'):
            sgn = 1 if face == 'south' else -1
            z0, z1 = (z, z + d) if sgn > 0 else (z - d, z)
            zf = z1 if sgn > 0 else z0
            self.box((x - w / 2, y, z0), (x + w / 2, y + h, z1), 'ral7035', 'terminal box', **{face: 'label:12V DC'})
            for i, col in enumerate(('metal:copper', 'paint:ral3000', 'paint:ral9005', 'paint:ral5010')):
                px = x - 1.1 + i * 0.73
                self.box((px - 0.22, y + 0.25, zf), (px + 0.22, y + 0.69, zf + sgn * 0.35), col, 'terminal stud')
        else:
            sgn = 1 if face == 'east' else -1
            x0, x1 = (x, x + d) if sgn > 0 else (x - d, x)
            xf = x1 if sgn > 0 else x0
            self.box((x0, y, z - w / 2), (x1, y + h, z + w / 2), 'ral7035', 'terminal box', **{face: 'label:12V DC'})
            for i, col in enumerate(('metal:copper', 'paint:ral3000', 'paint:ral9005', 'paint:ral5010')):
                pz = z - 1.1 + i * 0.73
                self.box((xf, y + 0.25, pz - 0.22), (xf + sgn * 0.35, y + 0.69, pz + 0.22), col, 'terminal stud')
        cz = z + (d / 2 if face == 'south' else -d / 2 if face == 'north' else 0)
        cx = x + (d / 2 if face == 'east' else -d / 2 if face == 'west' else 0)
        top, bot = y - 0.4, max(0.0, y - 2.5)
        for off, col, nm in ((-0.5, 'cable:c81e1e', 'red lead (+12 V)'), (0.5, 'cable:1d1d21', 'black lead (0 V)')):
            self.cyl((cx + off, y - 0.4, cz), 0.32, 0.4, 'y', 'metal:brass', 'gland')
            if top - bot > 0.05: self.cyl((cx + off, bot, cz), 0.18, top - bot, 'y', col, nm)
        self.g(g)
        return self

    def hv_box(self, x, y, z, face='south', phases=3):
        """HV cable termination box: a sealed box with bolted lid, DANGER plate, and a heavy armoured cable per phase"""
        g = self.group
        self.g('HV cable box')
        w, h, d = 5.0, 4.0, 2.0
        sgn = 1 if face in ('south', 'east') else -1
        if face in ('south', 'north'):
            z0, z1 = (z, z + d) if sgn > 0 else (z - d, z)
            zf = z1 if sgn > 0 else z0
            self.box((x - w / 2, y, z0), (x + w / 2, y + h, z1), 'ral7016', 'cable box', **{face: 'danger:DANGER|HV'})
            for bx in (x - w / 2 + 0.4, x + w / 2 - 0.4):
                for by in (y + 0.4, y + h - 0.4): self.bolt(bx, by, zf, face, 0.4)
            for i in range(phases):
                px = x - (phases - 1) * 0.8 + i * 1.6
                self.cyl((px, y - 0.8, (z0 + z1) / 2), 0.7, 0.8, 'y', 'metal:brass', 'cable gland')
                self.cyl((px, -1.5 if y > 2 else y - 2.3, (z0 + z1) / 2), 0.55, (y - 0.8) - (-1.5 if y > 2 else y - 2.3), 'y', 'cable', 'HV cable')
        else:
            x0, x1 = (x, x + d) if sgn > 0 else (x - d, x)
            xf = x1 if sgn > 0 else x0
            self.box((x0, y, z - w / 2), (x1, y + h, z + w / 2), 'ral7016', 'cable box', **{face: 'danger:DANGER|HV'})
            for bz in (z - w / 2 + 0.4, z + w / 2 - 0.4):
                for by in (y + 0.4, y + h - 0.4): self.bolt(xf, by, bz, face, 0.4)
            for i in range(phases):
                pz = z - (phases - 1) * 0.8 + i * 1.6
                self.cyl(((x0 + x1) / 2, y - 0.8, pz), 0.7, 0.8, 'y', 'metal:brass', 'cable gland')
                self.cyl(((x0 + x1) / 2, -1.5 if y > 2 else y - 2.3, pz), 0.55, (y - 0.8) - (-1.5 if y > 2 else y - 2.3), 'y', 'cable', 'HV cable')
        self.g(g)
        return self

    def bushing(self, x, z, y0, h, sheds=None, cap=True, mat='porcelain'):
        """porcelain bushing: core, stacked sheds, copper terminal stud on top"""
        g = self.group
        self.cyl((x, y0, z), 0.75, h, 'y', mat, 'bushing core')
        n = sheds or max(2, int(h / 1.3))
        for i in range(n):
            yy = y0 + 0.6 + i * (h - 1.2) / max(1, n - 1)
            self.cyl((x, yy, z), 1.35, 0.45, 'y', mat, 'shed')
        if cap:
            self.cyl((x, y0 + h, z), 0.9, 0.7, 'y', 'metal:copper', 'terminal cap')
            self.cyl((x, y0 + h + 0.7, z), 0.3, 1.4, 'y', 'metal:copper', 'terminal stud')
        self.g(g)
        return self

    def nameplate(self, x0, y0, x1, y1, z, text, face='north'):
        """a riveted stainless nameplate on a north (or south) face"""
        t = 0.12
        if face == 'north': self.box((x0, y0, z - t), (x1, y1, z), 'plate', 'nameplate', north='plate:' + text)
        else: self.box((x0, y0, z), (x1, y1, z + t), 'plate', 'nameplate', south='plate:' + text)
        return self

    def handrail(self, pts, y, h=3.0, r=0.2, mat='paint:ral1023'):
        """posts at each (x, z) and a top rail between them"""
        for (x, z) in pts: self.cyl((x, y, z), r, h, 'y', mat, 'rail post')
        for (xa, za), (xb, zb) in zip(pts, pts[1:]):
            if abs(xa - xb) > abs(za - zb): self.box((min(xa, xb), y + h - r, za - r), (max(xa, xb), y + h + r, za + r), mat, 'rail')
            else: self.box((xa - r, y + h - r, min(za, zb)), (xa + r, y + h + r, max(za, zb)), mat, 'rail')
        return self

    def ladder(self, x0, x1, y0, y1, z, mat='galv'):
        self.box((x0, y0, z), (x0 + 0.3, y1, z + 0.3), mat, 'ladder stile')
        self.box((x1 - 0.3, y0, z), (x1, y1, z + 0.3), mat, 'ladder stile')
        y = y0 + 0.8
        while y < y1 - 0.2:
            self.box((x0 + 0.3, y, z + 0.05), (x1 - 0.3, y + 0.2, z + 0.25), mat, 'rung')
            y += 1.0
        return self

    # ---- live controls (her ask 2026-10-09: "gauges, levers and buttons working on every machine") -----------------
    def surface(self, side='north', face=0.0, tilt=0.0, pivot=0.0, title='', plate=None):
        """a face that carries working controls. Its own frame: the model turned so `side` is the front (north):
        u = 16 - x, v = y, the control faces sit on the plane z = face and stand toward -z (so face is usually the
        model surface's z minus a hair). side 'up' = the top (v runs front to back). tilt leans it back about y=pivot."""
        sf = {'side': side, 'face': round(face, 3), 'title': title, 'controls': []}
        if tilt: sf['tilt'] = tilt; sf['pivot'] = pivot
        if plate: sf['plate'] = plate
        self.surfaces.append(sf)
        return sf

    def ctl(self, sf, t, cid, u, v, w=1.6, h=1.6, col='202020', label='', pos=None, **kw):
        """a working control on surface sf: t = push / mushroom / selector / key / lamp / gauge / digits / lever /
        flag / pull / label; cid = what the machine's code knows it by. col = hex colour (cap, lamp, digits)."""
        c = {'id': cid, 't': t, 'u': round(u, 3), 'v': round(v, 3), 'w': w, 'h': h, 'col': col.lstrip('#'), 'label': label}
        if pos: c['pos'] = list(pos)
        c.update(kw)
        sf['controls'].append(c)
        return self

    def _save_controls(self):
        if not self.surfaces: return
        d = os.path.join(ASSETS, 'controls')
        os.makedirs(d, exist_ok=True)
        json.dump({'title': self.block, 'surfaces': self.surfaces}, open(os.path.join(d, self.block + '.json'), 'w'), indent=1)

    # ---- output ----------------------------------------------------------------------------------
    def _faces(self):
        out = []
        for bi, (nm, lo, hi, rot, fm, grp) in enumerate(self.boxes):
            dx, dy, dz = hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]
            for d, spec in fm.items():
                if spec is None: continue
                fw, fh = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[d]
                k, a = _mat(spec)
                p = PXU.get(k, self.px)
                out.append([bi, d, k, a, max(1, round(fw * p)), max(1, round(fh * p))])
        return out

    def _pack(self, faces):
        # identical plain faces share pixels (same material, arg and size) - keeps the atlas small
        key = lambda f: (f[2], f[3], f[4], f[5])
        uniq = {}
        for f in faces:
            uniq.setdefault(key(f), f)
        items = sorted(uniq.values(), key=lambda f: -f[5])
        for size in (64, 128, 256, 512, 1024, 2048):
            x = y = row = 0
            place = {}
            ok = True
            for f in items:
                w, h = f[4] + 2, f[5] + 2
                if w > size: ok = False; break
                if x + w > size: x, y, row = 0, y + row, 0
                if y + h > size: ok = False; break
                place[key(f)] = (x + 1, y + 1)
                x += w
                row = max(row, h)
            if ok: return size, place, key
        raise SystemExit(self.name + ': texture atlas would be over 2048 px')

    def save(self, item_gui=0.625):
        faces = self._faces()
        size, place, key = self._pack(faces)
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        painted = set()
        for f in faces:
            k = key(f)
            if k in painted: continue
            painted.add(k)
            x0, y0 = place[k]
            rng = random.Random(self.name + repr(k))
            MATS[f[2]](img, x0, y0, f[4], f[5], rng, f[3])
            for xx in range(-1, f[4] + 1):                      # 1 px gutter: extend the edges (no mipmap bleed)
                for yy in (-1, f[5]):
                    sx, sy = min(max(xx, 0), f[4] - 1), min(max(yy, 0), f[5] - 1)
                    img.putpixel((x0 + xx, y0 + yy), img.getpixel((x0 + sx, y0 + sy)))
            for yy in range(f[5]):
                for xx in (-1, f[4]):
                    sx = min(max(xx, 0), f[4] - 1)
                    img.putpixel((x0 + xx, y0 + yy), img.getpixel((x0 + sx, y0 + yy)))
        uv = {}
        for f in faces:
            x0, y0 = place[key(f)]
            uv[(f[0], f[1])] = [x0, y0, x0 + f[4], y0 + f[5]]
        # ---- Blockbench project
        buf = io.BytesIO()
        img.save(buf, 'PNG')
        src = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
        els, groups = [], {}
        for bi, (nm, lo, hi, rot, fm, grp) in enumerate(self.boxes):
            u = str(uuid.uuid5(uuid.NAMESPACE_URL, self.name + str(bi)))
            e = {'name': nm, 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default',
                 'allow_mirror_modeling': True, 'from': [round(v, 4) for v in lo], 'to': [round(v, 4) for v in hi],
                 'autouv': 0, 'color': self.groups.index(grp) % 8, 'origin': [8, 8, 8], 'faces': {}, 'type': 'cube', 'uuid': u}
            if rot:
                r = [0, 0, 0]
                r['xyz'.index(rot['axis'])] = rot['angle']
                e['rotation'] = r
                e['origin'] = [round(v, 4) for v in rot['origin']]
            for d in DIRS:
                if (bi, d) in uv: e['faces'][d] = {'uv': uv[(bi, d)], 'texture': 0}
                else: e['faces'][d] = {'uv': [0, 0, 0, 0], 'texture': None}
            els.append(e)
            groups.setdefault(grp, []).append(u)
        outliner = [{'name': gname, 'origin': [8, 8, 8], 'color': 0, 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, self.name + 'g' + gname)),
                     'export': True, 'isOpen': False, 'locked': False, 'visibility': True, 'autouv': 0, 'children': kids}
                    for gname, kids in groups.items()]
        bb = {'meta': {'format_version': '4.5', 'model_format': 'java_block', 'box_uv': False}, 'name': self.name,
              'parent': '', 'ambientocclusion': False, 'front_gui_light': False, 'visible_box': [1, 1, 0],
              'variable_placeholders': '', 'variable_placeholder_buttons': [], 'unhandled_root_fields': {},
              'resolution': {'width': size, 'height': size}, 'elements': els, 'outliner': outliner,
              'textures': [{'path': '', 'name': self.name + '.png', 'folder': 'blocks', 'namespace': 'irextras', 'id': '0',
                            'width': size, 'height': size, 'uv_width': size, 'uv_height': size, 'particle': True,
                            'render_mode': 'default', 'visible': True, 'mode': 'bitmap', 'saved': True,
                            'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, self.name + 'tex')), 'relative_path': '', 'source': src}]}
        os.makedirs(BBDIR, exist_ok=True)
        json.dump(bb, open(os.path.join(BBDIR, self.name + '.bbmodel'), 'w'))
        # ---- game model
        tex = 'irextras:blocks/bb_' + self.name
        s = 16.0 / size
        jel = []
        for bi, (nm, lo, hi, rot, fm, grp) in enumerate(self.boxes):
            for v in lo + hi:
                if v < -16 or v > 32: raise SystemExit(f'{self.name}: {nm} coordinate {v} outside -16..32')
            j = {'__comment': nm, 'from': [round(v, 4) for v in lo], 'to': [round(v, 4) for v in hi], 'faces': {}}
            if rot:
                if rot['angle'] not in (-45, -22.5, 0, 22.5, 45): raise SystemExit(f'{self.name}: {nm} bad angle')
                j['rotation'] = {'origin': [round(v, 4) for v in rot['origin']], 'axis': rot['axis'], 'angle': rot['angle']}
            for d in DIRS:
                if (bi, d) in uv:
                    a = uv[(bi, d)]
                    j['faces'][d] = {'uv': [round(a[0] * s, 4), round(a[1] * s, 4), round(a[2] * s, 4), round(a[3] * s, 4)], 'texture': '#0'}
            jel.append(j)
        model = {'credit': 'IR Extras - built with bbkit, open blockbench/models/' + self.name + '.bbmodel in Blockbench',
                 'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': jel,
                 'display': {'gui': {'rotation': [30, 225, 0], 'scale': [item_gui] * 3}, 'ground': {'translation': [0, 3, 0], 'scale': [0.25] * 3},
                             'fixed': {'scale': [0.5] * 3}, 'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375] * 3},
                             'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.4] * 3}, 'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.4] * 3}}}
        json.dump(model, open(os.path.join(ASSETS, 'models/block', self.name + '.json'), 'w'), indent=0)
        img.save(os.path.join(ASSETS, 'textures/blocks', 'bb_' + self.name + '.png'))
        self._save_controls()
        print(f'{self.name}: {len(self.boxes)} boxes, {len(faces)} faces, atlas {size}px')
        return self
