#!/usr/bin/env python3
"""Pride Rail voxel train engine: describe a train as boxes (metres; x along the train, +x = front, y up, y=0 = top of
rail / running surface, z across) and write
  * the Immersive Railroading model: OBJ + MTL + palette PNG (+ livery variant palettes, IR tex_variants folders)
  * a Blockbench project (.bbmodel, generic "free" model, 16 px = 1 m) with the same cubes, grouped by IR part.
Colours are named palette slots; every face samples the centre of its slot's 4x4 cell, so a livery variant is just a
different palette PNG."""
import base64, json, math, os, struct, uuid, zlib

CELL, GRID = 4, 16          # 16x16 cells of 4 px -> 64x64 palette


def png_bytes(colors, names):
    w = h = CELL * GRID
    rows = bytearray()
    for y in range(h):
        rows.append(0)
        for x in range(w):
            i = (y // CELL) * GRID + (x // CELL)
            c = colors[names[i]] if i < len(names) else (255, 0, 255)
            rows += bytes((c[0], c[1], c[2], c[3] if len(c) > 3 else 255))

    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(rows), 9)) + chunk(b'IEND', b''))


def hexrgb(h):
    """'#RRGGBB' or '#RRGGBBAA' (alpha 0 = see-through: IR draws texture alpha as cut-out)"""
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in ((0, 2, 4, 6) if len(h) == 8 else (0, 2, 4)))


def _normal(pts):
    """unit normal of a polygon (Newell), None if degenerate"""
    nx = ny = nz = 0.0
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        nx += (a[1] - b[1]) * (a[2] + b[2])
        ny += (a[2] - b[2]) * (a[0] + b[0])
        nz += (a[0] - b[0]) * (a[1] + b[1])
    L = math.sqrt(nx * nx + ny * ny + nz * nz)
    return None if L < 1e-9 else (nx / L, ny / L, nz / L)


def _smooth_normals(faces, angle=50):
    """per face-vertex normals: average of the neighbouring face normals within `angle` degrees"""
    lim = math.cos(math.radians(angle))
    fn = [_normal(f[1]) for f in faces]
    at = {}
    for i, f in enumerate(faces):
        for p in f[1]:
            at.setdefault((f[0], round(p[0], 4), round(p[1], 4), round(p[2], 4)), []).append(i)
    out = []
    for i, f in enumerate(faces):
        ns = []
        for p in f[1]:
            sx = sy = sz = 0.0
            for j in at[(f[0], round(p[0], 4), round(p[1], 4), round(p[2], 4))]:
                a = fn[j]
                if a[0] * fn[i][0] + a[1] * fn[i][1] + a[2] * fn[i][2] >= lim:
                    sx += a[0]; sy += a[1]; sz += a[2]
            L = math.sqrt(sx * sx + sy * sy + sz * sz) or 1.0
            ns.append((sx / L, sy / L, sz / L))
        out.append(ns)
    return out


class Model:
    def __init__(self, name, palette):
        self.name = name
        self.pal = {k: hexrgb(v) if isinstance(v, str) else v for k, v in palette.items()}
        self.boxes = []          # (group, x0, x1, y0, y1, z0, z1, colour)
        self.faces = []          # smooth mesh: (group, [p0..p3], colour, outward hint)

    # ---- primitives -------------------------------------------------------------------------------------------
    def box(self, g, x0, x1, y0, y1, z0, z1, c):
        x0, x1 = sorted((x0, x1)); y0, y1 = sorted((y0, y1)); z0, z1 = sorted((z0, z1))
        if x1 - x0 < 1e-4 or y1 - y0 < 1e-4 or z1 - z0 < 1e-4:
            return
        if c not in self.pal:
            raise KeyError('%s: colour %r not in palette' % (self.name, c))
        self.boxes.append((g, x0, x1, y0, y1, z0, z1, c))

    def sym(self, g, x0, x1, y0, y1, z0, z1, c):
        """a box and its mirror across the centre line"""
        self.box(g, x0, x1, y0, y1, z0, z1, c)
        self.box(g, x0, x1, y0, y1, -z1, -z0, c)

    def slab(self, g, x0, x1, y0, y1, hw, c, inner=0.0):
        if inner > 0:
            self.sym(g, x0, x1, y0, y1, inner, hw, c)
        else:
            self.box(g, x0, x1, y0, y1, -hw, hw, c)

    def loft(self, g, x0, x1, y0, y1, dx, dy, f):
        """Voxel loft: f(x, y) -> None | (half_width, colour[, inner_half_width]) sampled per dx*dy cell; equal
        neighbours along x merge into one box."""
        nx, ny = max(1, round((x1 - x0) / dx)), max(1, round((y1 - y0) / dy))
        dx, dy = (x1 - x0) / nx, (y1 - y0) / ny
        for j in range(ny):
            ya = y0 + j * dy
            run_x, run_v = x0, None
            for i in range(nx + 1):
                v = f(x0 + (i + .5) * dx, ya + dy / 2) if i < nx else '__end'
                if i and v == run_v:
                    continue
                if run_v not in (None, '__end') and i:
                    hw, c = run_v[0], run_v[1]
                    inner = run_v[2] if len(run_v) > 2 else 0.0
                    if hw > 0:
                        self.slab(g, run_x, x0 + i * dx, ya, ya + dy, hw, c, inner)
                run_x, run_v = x0 + i * dx, v

    def line(self, g, p0, p1, t, c, step=None):
        """a stepped voxel bar from p0 to p1 (x, y, z) of thickness t"""
        d = [p1[i] - p0[i] for i in range(3)]
        L = math.sqrt(sum(a * a for a in d))
        n = max(1, int(L / (step or t)))
        for k in range(n + 1):
            p = [p0[i] + d[i] * k / n for i in range(3)]
            self.box(g, p[0] - t / 2, p[0] + t / 2, p[1] - t / 2, p[1] + t / 2, p[2] - t / 2, p[2] + t / 2, c)

    def disc(self, g, x, y, z0, z1, r, c, hub=None):
        """wheel / tyre: an octagon-ish voxel disc in the x-y plane, axle along z"""
        a = r * 0.42
        self.box(g, x - r, x + r, y - a, y + a, z0, z1, c)
        self.box(g, x - a, x + a, y - r, y + r, z0, z1, c)
        b = r * 0.78
        self.box(g, x - b, x + b, y - b, y + b, z0, z1, c)
        if hub:
            h = r * 0.35
            zz = (z1 - z0) * 0.15
            self.box(g, x - h, x + h, y - h, y + h, z0 - zz, z1 + zz, hub)

    def quad(self, g, pts, c, out=None):
        """a mesh face (3 or 4 points); `out` = rough outward direction, used to fix the winding"""
        if c not in self.pal:
            raise KeyError('%s: colour %r not in palette' % (self.name, c))
        n = _normal(pts)
        if n is None:
            return
        if out is not None and n[0] * out[0] + n[1] * out[1] + n[2] * out[2] < 0:
            pts = pts[::-1]
        self.faces.append((g, [tuple(p) for p in pts], c))

    # ---- tidy ------------------------------------------------------------------------------------------------
    def merge(self):
        """merge boxes that only differ by stacked y ranges (same group/x/z/colour)"""
        key = {}
        for b in self.boxes:
            key.setdefault((b[0], round(b[1], 5), round(b[2], 5), round(b[5], 5), round(b[6], 5), b[7]), []).append(b)
        out = []
        for k, bs in key.items():
            bs.sort(key=lambda b: b[3])
            cur = list(bs[0])
            for b in bs[1:]:
                if abs(b[3] - cur[4]) < 1e-5:
                    cur[4] = b[4]
                else:
                    out.append(tuple(cur)); cur = list(b)
            out.append(tuple(cur))
        self.boxes = out

    def bounds(self, prefix=''):
        bs = [b for b in self.boxes if b[0].startswith(prefix)]
        return (min(b[1] for b in bs), max(b[2] for b in bs), min(b[3] for b in bs), max(b[4] for b in bs),
                min(b[5] for b in bs), max(b[6] for b in bs))

    # ---- output ----------------------------------------------------------------------------------------------
    def names(self):
        return list(self.pal)

    def uv(self, c):
        i = self.names().index(c)
        return ((i % GRID) * CELL + CELL / 2) / (CELL * GRID), 1 - ((i // GRID) * CELL + CELL / 2) / (CELL * GRID)

    def write_ir(self, folder, variants=None):
        """OBJ/MTL/palette.png into folder; variants = {name: {slot: rgb}} -> folder/<name>/palette.png"""
        os.makedirs(folder, exist_ok=True)
        self.merge()
        names = self.names()
        out = ['# Pride Rail - %s (generated by tools/voxtrain.py; Blockbench project in blockbench/)' % self.name,
               'mtllib %s.mtl' % self.name]
        for c in names:
            out.append('vt %.6f %.6f' % self.uv(c))
        for nv in ('0 0 -1', '0 0 1', '0 -1 0', '0 1 0', '-1 0 0', '1 0 0'):
            out.append('vn ' + nv)
        verts, body, n = [], [], 0
        groups = {}
        for b in self.boxes:
            groups.setdefault(b[0], []).append(b)
        for g, bs in groups.items():
            body.append('o %s' % g)
            body.append('usemtl palette')
            for (_, x0, x1, y0, y1, z0, z1, col) in bs:
                for p in ((x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)):
                    verts.append('v %.4f %.4f %.4f' % p)
                t = names.index(col) + 1
                for fi, f in enumerate(((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (3, 7, 6, 2), (0, 4, 7, 3), (1, 2, 6, 5))):
                    body.append('f ' + ' '.join('%d/%d/%d' % (n + i + 1, t, fi + 1) for i in f))
                n += 8
        # smooth mesh faces: own vertices + per-vertex normals (indices after the 6 box normals)
        norms = []
        if self.faces:
            sn = _smooth_normals(self.faces)
            fg = {}
            for f, ns in zip(self.faces, sn):
                fg.setdefault(f[0], []).append((f, ns))
            vidx, nidx = {}, {}
            for g, lst in fg.items():
                body.append('o %s' % g)
                body.append('usemtl palette')
                for (gg, pts, col), ns in lst:
                    t = names.index(col) + 1
                    idx = []
                    for p, nn in zip(pts, ns):
                        vs = 'v %.3f %.3f %.3f' % p
                        if vs not in vidx:
                            verts.append(vs)
                            n += 1
                            vidx[vs] = n
                        ns_ = 'vn %.3f %.3f %.3f' % nn
                        if ns_ not in nidx:
                            norms.append(ns_)
                            nidx[ns_] = 6 + len(norms)
                        idx.append('%d/%d/%d' % (vidx[vs], t, nidx[ns_]))
                    body.append('f ' + ' '.join(idx))
        with open(os.path.join(folder, self.name + '.obj'), 'w') as fh:
            fh.write('\n'.join(out[:2] + verts + out[2:] + norms + body) + '\n')
        with open(os.path.join(folder, self.name + '.mtl'), 'w') as fh:
            fh.write('newmtl palette\nKa 1 1 1\nKd 1 1 1\nKs 0 0 0\nd 1\nillum 1\nmap_Kd palette.png\n')
        with open(os.path.join(folder, 'palette.png'), 'wb') as fh:
            fh.write(png_bytes(self.pal, names))
        for vname, over in (variants or {}).items():
            os.makedirs(os.path.join(folder, vname), exist_ok=True)
            pal = dict(self.pal)
            pal.update({k: hexrgb(v) if isinstance(v, str) else v for k, v in over.items() if k in pal})
            with open(os.path.join(folder, vname, 'palette.png'), 'wb') as fh:
                fh.write(png_bytes(pal, names))
        return len(self.boxes) + len(self.faces)

    def write_bbmodel(self, path):
        """Blockbench generic model: 16 px = 1 m, IR part names as outliner groups, palette embedded"""
        self.merge()
        names = self.names()
        tex_uuid = str(uuid.uuid4())
        src = 'data:image/png;base64,' + base64.b64encode(png_bytes(self.pal, names)).decode()
        elements, groups = [], {}
        for (g, x0, x1, y0, y1, z0, z1, col) in self.boxes:
            i = names.index(col)
            cx, cy = (i % GRID) * CELL + CELL / 2, (i // GRID) * CELL + CELL / 2
            uvr = [cx - 1, cy - 1, cx + 1, cy + 1]
            u = str(uuid.uuid4())
            elements.append({
                'name': '%s_%s' % (g.lower(), col), 'box_uv': False, 'rescale': False, 'locked': False,
                'render_order': 'default', 'allow_mirror_modeling': True,
                'from': [round(x0 * 16, 4), round(y0 * 16, 4), round(z0 * 16, 4)],
                'to': [round(x1 * 16, 4), round(y1 * 16, 4), round(z1 * 16, 4)],
                'autouv': 0, 'color': i % 8, 'origin': [0, 0, 0],
                'faces': {f: {'uv': uvr, 'texture': 0} for f in ('north', 'east', 'south', 'west', 'up', 'down')},
                'type': 'cube', 'uuid': u})
            groups.setdefault(g, []).append(u)
        mg = {}
        for f in self.faces:
            mg.setdefault(f[0], []).append(f)
        for g, lst in mg.items():
            vtx, fcs = {}, {}
            for k, (_, pts, col) in enumerate(lst):
                i = names.index(col)
                cx, cy = (i % GRID) * CELL + CELL / 2, (i // GRID) * CELL + CELL / 2
                keys = []
                for j, p in enumerate(pts):
                    vk = 'v%d_%d' % (k, j)
                    vtx[vk] = [round(p[0] * 16, 4), round(p[1] * 16, 4), round(p[2] * 16, 4)]
                    keys.append(vk)
                fcs['f%d' % k] = {'vertices': keys, 'uv': {vk: [cx, cy] for vk in keys}, 'texture': 0}
            u = str(uuid.uuid4())
            elements.append({'name': g.lower() + '_mesh', 'type': 'mesh', 'uuid': u, 'origin': [0, 0, 0],
                             'rotation': [0, 0, 0], 'export': True, 'visibility': True, 'locked': False,
                             'render_order': 'default', 'allow_mirror_modeling': True, 'color': 0,
                             'vertices': vtx, 'faces': fcs})
            groups.setdefault(g, []).append(u)
        outliner = [{'name': g, 'origin': [0, 0, 0], 'color': 0, 'uuid': str(uuid.uuid4()), 'export': True,
                     'mirror_uv': False, 'isOpen': False, 'locked': False, 'visibility': True, 'autouv': 0,
                     'children': ch} for g, ch in groups.items()]
        doc = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
               'name': self.name, 'model_identifier': '', 'visible_box': [1, 1, 0], 'variable_placeholders': '',
               'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {},
               'resolution': {'width': CELL * GRID, 'height': CELL * GRID},
               'elements': elements, 'outliner': outliner,
               'textures': [{'path': '', 'name': 'palette.png', 'folder': '', 'namespace': '', 'id': '0',
                             'width': CELL * GRID, 'height': CELL * GRID, 'uv_width': CELL * GRID,
                             'uv_height': CELL * GRID, 'particle': False, 'render_mode': 'default',
                             'render_sides': 'auto', 'visible': True, 'internal': True, 'saved': False,
                             'uuid': tex_uuid, 'source': src}]}
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as fh:
            json.dump(doc, fh)
