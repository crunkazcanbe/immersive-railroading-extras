#!/usr/bin/env python3
"""Pride Rail monorail / maglev track models for Immersive Railroading (built into IR Extras).
IR track model rules (from IR's own + voxel packs): one 1 m piece, x -0.5..0.5 along the track, y up, z across,
groups RAIL_BASE / RAIL_LEFT / RAIL_RIGHT, colours from a palette png via fixed UVs. IR repeats + bends it on curves."""
import os, json, struct, zlib
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/immersiverailroading')

PAL = {  # name -> rgb, laid out in a 4x4 grid of 4px cells on a 16x16 png
    'concrete': (196, 196, 190), 'concrete_dk': (150, 150, 146), 'concrete_lt': (222, 222, 216), 'steel': (96, 110, 124),
    'steel_dk': (62, 72, 84), 'copper': (196, 120, 60), 'coil': (70, 150, 255), 'coil_glow': (150, 220, 255),
    'white': (240, 240, 240), 'yellow': (240, 200, 40), 'dark': (40, 40, 44), 'rubber': (30, 30, 32),
    'pride_pink': (245, 169, 184), 'pride_blue': (91, 206, 250), 'rust': (140, 84, 50), 'black': (16, 16, 18),
    'ballast': (122, 118, 110), 'ballast_dk': (92, 88, 82), 'sleeper': (168, 166, 158), 'rail_head': (182, 186, 190),
    'rail_web': (88, 70, 58), 'cover': (236, 196, 40), 'ins_white': (226, 226, 220), 'clip': (40, 44, 48)}
NAMES = list(PAL)

G = 8          # 8x8 palette cells of 4 px -> 32x32 png


def png(path):
    w = h = 4 * G
    rows = b''
    for y in range(h):
        rows += b'\x00'
        for x in range(w):
            i = (y // 4) * G + (x // 4)
            r, g, b = PAL[NAMES[i]] if i < len(NAMES) else (255, 0, 255)
            rows += bytes((r, g, b, 255))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)

def uv(c):
    i = NAMES.index(c)
    return ((i % G) * 4 + 2) / (4.0 * G), 1 - ((i // G) * 4 + 2) / (4.0 * G)

class Obj:
    def __init__(self): self.groups = {}
    def box(self, group, x0, x1, y0, y1, z0, z1, color):
        self.groups.setdefault(group, []).append((x0, x1, y0, y1, z0, z1, color))
    def write(self, path, mtl, tex):
        v, vt, out = [], [], ['mtllib %s' % os.path.basename(mtl)]
        for c in NAMES: vt.append(uv(c))
        for u, w in vt: out.append('vt %.6f %.6f' % (u, w))
        for nv in ('0 0 -1', '0 0 1', '0 -1 0', '0 1 0', '-1 0 0', '1 0 0'): out.append('vn ' + nv)
        n = 0
        body = []
        for g, boxes in self.groups.items():
            body.append('o %s' % g)
            body.append('usemtl palette')
            for (x0, x1, y0, y1, z0, z1, col) in boxes:
                corners = [(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)]
                for c in corners: v.append(c)
                t = NAMES.index(col) + 1
                faces = [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]
                for fi, f in enumerate(faces):
                    body.append('f ' + ' '.join('%d/%d/%d' % (n + i + 1, t, fi + 1) for i in f))
                n += 8
        out = out[:1] + ['v %.5f %.5f %.5f' % p for p in v] + out[1:] + body
        open(path, 'w').write('\n'.join(out) + '\n')
        open(mtl, 'w').write('newmtl palette\nKa 1 1 1\nKd 1 1 1\nKs 0 0 0\nd 1\nillum 1\nmap_Kd %s\n' % os.path.basename(tex))

def beam_concrete(o):          # ALWEG-style straddle beam (Disneyland / Tokyo): plain concrete box, power rails on the sides
    o.box('RAIL_BASE', -0.5, 0.5, -1.70, 0.30, -0.40, 0.40, 'concrete')
    o.box('RAIL_BASE', -0.5, 0.5, 0.26, 0.30, -0.36, 0.36, 'concrete_dk')          # running surface
    o.box('RAIL_LEFT', -0.5, 0.5, -0.30, -0.18, -0.46, -0.40, 'copper')             # conductor rail (the beam carries power)
    o.box('RAIL_RIGHT', -0.5, 0.5, -0.30, -0.18, 0.40, 0.46, 'steel')
    o.box('RAIL_BASE', -0.5, 0.5, -1.74, -1.70, -0.44, 0.44, 'concrete_dk')         # bottom chamfer strip

def beam_steel(o):             # box-girder steel beam with flanges
    o.box('RAIL_BASE', -0.5, 0.5, 0.20, 0.30, -0.42, 0.42, 'steel_dk')
    o.box('RAIL_BASE', -0.5, 0.5, -1.30, 0.20, -0.30, 0.30, 'steel')
    o.box('RAIL_BASE', -0.5, 0.5, -1.40, -1.30, -0.42, 0.42, 'steel_dk')
    o.box('RAIL_BASE', -0.10, 0.10, -1.30, 0.20, -0.32, 0.32, 'steel_dk')           # stiffener every metre
    o.box('RAIL_LEFT', -0.5, 0.5, -0.20, -0.10, -0.36, -0.30, 'copper')
    o.box('RAIL_RIGHT', -0.5, 0.5, -0.20, -0.10, 0.30, 0.36, 'steel_dk')

def guideway_maglev(o):        # T-shaped guideway (Transrapid / Shanghai): wide top, stator coils under the wings
    o.box('RAIL_BASE', -0.5, 0.5, 0.05, 0.30, -1.10, 1.10, 'concrete_lt')
    o.box('RAIL_BASE', -0.5, 0.5, -1.40, 0.05, -0.35, 0.35, 'concrete')
    o.box('RAIL_BASE', -0.5, 0.5, 0.29, 0.31, -0.06, 0.06, 'white')                 # centre stripe
    o.box('RAIL_LEFT', -0.5, 0.5, -0.05, 0.05, -1.10, -0.80, 'coil')                # stator packs (the "magnets")
    o.box('RAIL_RIGHT', -0.5, 0.5, -0.05, 0.05, 0.80, 1.10, 'coil')
    o.box('RAIL_LEFT', -0.5, 0.5, 0.05, 0.30, -1.14, -1.10, 'coil_glow')            # guidance rails along the edges
    o.box('RAIL_RIGHT', -0.5, 0.5, 0.05, 0.30, 1.10, 1.14, 'coil_glow')

def guideway_pride(o):         # the same guideway, trans-flag stripes on the sides
    guideway_maglev(o)
    o.box('RAIL_BASE', -0.5, 0.5, 0.12, 0.18, -1.12, 1.12, 'pride_blue')
    o.box('RAIL_BASE', -0.5, 0.5, 0.18, 0.24, -1.12, 1.12, 'pride_pink')

def beam_suspended(o):         # beam for a hanging (SAFEGE / Wuppertal-style) look: tall slim beam with a rail slot underneath
    o.box('RAIL_BASE', -0.5, 0.5, -0.20, 0.30, -0.30, 0.30, 'steel')
    o.box('RAIL_BASE', -0.5, 0.5, -0.24, -0.20, -0.40, 0.40, 'steel_dk')
    o.box('RAIL_LEFT', -0.5, 0.5, 0.30, 0.34, -0.30, -0.10, 'yellow')
    o.box('RAIL_RIGHT', -0.5, 0.5, 0.30, 0.34, 0.10, 0.30, 'yellow')

def third_rail(o):             # standard-gauge ballasted track with an electrified third rail on insulator chairs
    o.box('RAIL_BASE', -0.5, 0.5, -0.06, 0.08, -1.62, 1.62, 'ballast_dk')            # ballast shoulder
    o.box('RAIL_BASE', -0.5, 0.5, 0.08, 0.12, -1.45, 1.45, 'ballast')
    o.box('RAIL_BASE', -0.13, 0.13, 0.12, 0.22, -1.25, 1.25, 'sleeper')               # concrete sleeper
    for zs in (1, -1):
        side = 'RAIL_LEFT' if zs > 0 else 'RAIL_RIGHT'
        o.box('RAIL_BASE', -0.09, 0.09, 0.22, 0.24, *sorted((zs * 0.66, zs * 0.86)), 'clip')     # baseplate + clips
        o.box(side, -0.5, 0.5, 0.22, 0.25, *sorted((zs * 0.679, zs * 0.832)), 'rail_web')        # rail foot
        o.box(side, -0.5, 0.5, 0.25, 0.33, *sorted((zs * 0.745, zs * 0.766)), 'rail_web')        # web
        o.box(side, -0.5, 0.5, 0.33, 0.368, *sorted((zs * 0.7175, zs * 0.7937)), 'rail_head')    # head (running surface)
    # third (conductor) rail outside the right-hand rail, on porcelain insulators, with the yellow cover board
    o.box('RAIL_BASE', -0.08, 0.08, 0.12, 0.30, -1.44, -1.30, 'ins_white')             # insulator pot
    o.box('RAIL_BASE', -0.5, 0.5, 0.30, 0.40, -1.43, -1.31, 'steel')                   # conductor rail
    o.box('RAIL_BASE', -0.5, 0.5, 0.40, 0.41, -1.40, -1.34, 'copper')                  # contact face (shoes run here)
    o.box('RAIL_BASE', -0.06, 0.06, 0.12, 0.52, -1.52, -1.48, 'clip')                  # cover-board bracket
    o.box('RAIL_BASE', -0.5, 0.5, 0.50, 0.53, -1.52, -1.24, 'cover')                   # protection board

TYPES = [
    ('pride_monorail', 'Pride Monorail (concrete beam)', beam_concrete, [('ore:concrete', 4), ('ore:ingotCopper', 1)]),
    ('pride_monorail_steel', 'Pride Monorail (steel beam)', beam_steel, [('ore:ingotIron', 4), ('ore:ingotCopper', 1)]),
    ('pride_maglev', 'Pride Maglev Guideway', guideway_maglev, [('ore:concrete', 3), ('ore:ingotIron', 2)]),
    ('pride_maglev_pride', 'Pride Maglev Guideway (flag stripes)', guideway_pride, [('ore:concrete', 3), ('ore:ingotIron', 2)]),
    ('pride_monorail_slim', 'Pride Monorail (slim steel beam)', beam_suspended, [('ore:ingotIron', 3)]),
    ('pride_thirdrail', 'Pride Third Rail (subway / metro, DC)', third_rail, [('ore:concrete', 1), ('ore:ingotIron', 1)]),
]

if __name__ == '__main__':
    os.makedirs(os.path.join(OUT, 'track'), exist_ok=True)
    for tid, name, fn, mats in TYPES:
        d = os.path.join(OUT, 'models/track', tid)
        os.makedirs(d, exist_ok=True)
        o = Obj(); fn(o)
        png(os.path.join(d, tid + '.png'))
        o.write(os.path.join(d, tid + '.obj'), os.path.join(d, tid + '.mtl'), os.path.join(d, tid + '.png'))
        json.dump({'name': name, 'models': {'>0': 'immersiverailroading:models/track/%s/%s.obj' % (tid, tid)},
                   'materials': {'TIE': [{'item': m, 'cost': c} for m, c in mats], 'RAIL': [{'item': 'ore:irRail', 'cost': 1}]}},
                  open(os.path.join(OUT, 'track', tid + '.json'), 'w'), indent=2)
    json.dump({'pack': 'Pride Rail', 'types': [t[0] for t in TYPES]}, open(os.path.join(OUT, 'track', 'track.json'), 'w'), indent=2)
    print('wrote', len(TYPES), 'track types')
