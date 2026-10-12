#!/usr/bin/env python3
"""cabkit - real opening cabinet doors with working insides, retrofitted onto any bbkit model (her ask 2026-10-11:
"cabinet doors on machines should really swing open and show the insides").

    import cabkit
    ...build the model as before...
    cabkit.fit(m, x0, x1, y0, y1, zf, title='RELAY CASE|LOC 14')   # just before m.save()
    m.save()

fit() works on a north face (z = zf is the front of the body, the rectangle x0..x1 / y0..y1 the door opening):
  * removes the old baked door details standing on that rectangle (leaf, pressings, handles, labels)
  * hollows the body behind it (the boxes are cut around a cavity `depth` deep, so the walls stay)
  * fills the cavity: galvanised back pan, wiring ducts, a DIN rail of MCBs, relays with flags and LEDs, a PLC with
    I/O LEDs, fuse holders, a terminal rail with every wire colour, nameplate stickers and a circuit chart
  * builds new door leaves in groups door1 / door2, which save() writes as their OWN models (<block>_door1.json) so the
    game swings them (cabinet/client/CabinetRenderer) instead of baking them into the block
  * writes the live controls: door zones on the front, MCB toggles (LEVER), relay flags (FLAG) + coil LEDs, PLC LEDs,
    neon fuse lamps (LAMP) and fuse carriers (ZONE, the carrier itself is drawn by the game) on an inside surface
  * writes assets/irextras/cabinets/<block>.json (doors, hinges, functions, HV) for the game

Ids the game knows (cabinet/Cabinet.java): m_cab_door<n>, m_cab_mcb_<fn>, m_cab_fuse_<fn> (mechanical, saved),
cab_k<n> / cab_kl<n> (relay flag / coil LED), cab_run cab_err cab_i<n> cab_q<n> (PLC), cab_fb_<fn> (fuse blown neon).
Functions: ctl = the 12 V control supply, main = the mains / grid supply, scr = the screens, out = the DC output.
"""
import json, os
import bbkit
from bbkit import ASSETS

DEFAULT_FNS = (('ctl', '12V CTL'), ('main', 'MAINS'), ('scr', 'SCREEN'))
RELAYS = ('K1 12V', 'K2 MAINS', 'K3 FAULT', 'K4 DOOR')
TEXTY = ('label', 'plate', 'danger', 'warn', 'txt', 'led', 'screen', 'gauge', 'lamp', 'louvre')


def _inter(lo, hi, clo, chi):
    return all(lo[i] < chi[i] - 1e-4 and hi[i] > clo[i] + 1e-4 for i in range(3))


def _subtract(lo, hi, clo, chi):
    """box lo..hi minus cavity clo..chi as up to 6 boxes"""
    out, lo, hi = [], list(lo), list(hi)
    for ax in range(3):
        if lo[ax] < clo[ax]:
            a, b = list(lo), list(hi); b[ax] = clo[ax]; out.append((a, b)); lo[ax] = clo[ax]
        if hi[ax] > chi[ax]:
            a, b = list(lo), list(hi); a[ax] = chi[ax]; out.append((a, b)); hi[ax] = chi[ax]
    return [(a, b) for a, b in out if all(b[i] - a[i] > 0.01 for i in range(3))]


def _plain(fm):
    for d in ('east', 'west', 'up', 'down', 'south', 'north'):
        s = fm.get(d)
        if s and bbkit._mat(s)[0] not in TEXTY: return s
    return 'ral7035'


# ---- faces other than north: work in the frame where `side` is the front (the same frame m.surface(side) uses)
YAW = {'north': 0, 'east': -90, 'west': 90, 'south': 180}


def _rot(p, yaw, d=False):
    """GL rotation about Y by yaw degrees about the block centre (d: a direction, no centre)"""
    import math
    a = math.radians(yaw); c, s = round(math.cos(a)), round(math.sin(a))
    x, y, z = (p[0], p[1], p[2]) if d else (p[0] - 8, p[1] - 8, p[2] - 8)
    x, z = x * c + z * s, -x * s + z * c
    return [x, y, z] if d else [x + 8, y + 8, z + 8]


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def _turn(b, yaw):
    """a box tuple turned by yaw (axis-aligned stays axis-aligned; faces and turned-box axes follow)"""
    if not yaw: return b
    nm, lo, hi, rot, fm, grp = b
    a, c = _rot(lo, yaw), _rot(hi, yaw)
    lo2 = [min(a[i], c[i]) for i in range(3)]; hi2 = [max(a[i], c[i]) for i in range(3)]
    fm2 = {}
    for d, spec in fm.items():
        n = _rot(NORMALS[d], yaw, True)
        fm2[next(k for k, v in NORMALS.items() if all(abs(v[i] - n[i]) < 1e-6 for i in range(3)))] = spec
    if rot:
        ax = {'x': (1, 0, 0), 'y': (0, 1, 0), 'z': (0, 0, 1)}[rot['axis']]
        n = _rot(ax, yaw, True)
        i = max(range(3), key=lambda k: abs(n[k]))
        rot = {'axis': 'xyz'[i], 'angle': rot['angle'] * (1 if n[i] > 0 else -1), 'origin': _rot(rot['origin'], yaw)}
    return (nm, lo2, hi2, rot, fm2, grp)


def fit(m, x0, x1, y0, y1, zf, side='north', **kw):
    """door opening x0..x1 / y0..y1 at z = zf on face `side`, all in the frame where that side is the front
    (m.surface(side)'s frame: x = 16 - u); see the module doc"""
    yaw = YAW[side]
    m.boxes[:] = [_turn(b, -yaw) for b in m.boxes]                     # model -> front frame
    try:
        _fit(m, x0, x1, y0, y1, zf, side=side, **kw)
    finally:
        m.boxes[:] = [_turn(b, yaw) for b in m.boxes]                   # and back
    for d in m._cabinet['doors']:
        h = _rot([d['hinge'][0], 0, d['hinge'][1]], yaw)
        d['hinge'] = [round(h[0], 3), round(h[2], 3)]
    return m


def _fit(m, x0, x1, y0, y1, zf, side='north', depth=3.4, doors=2, col='ral7035', title='CONTROL CABINET', fns=DEFAULT_FNS,
         hv=False, volts='400V 50HZ', clear=1.2, serial='IRX-2026'):
    fns = list(fns)
    # ---- 1. the old baked door details on that face
    keep = []
    for b in m.boxes:
        nm, lo, hi, rot, fm, grp = b
        on_face = lo[2] >= zf - clear - 0.01 and hi[2] <= zf + 0.01
        inside = lo[0] >= x0 - 0.7 and hi[0] <= x1 + 0.7 and lo[1] >= y0 - 0.7 and hi[1] <= y1 + 0.7
        if not (on_face and inside): keep.append(b)
    m.boxes[:] = keep
    # ---- 2. hollow the body behind the opening
    clo, chi = [x0, y0, zf], [x1, y1, zf + depth]
    out = []
    for b in m.boxes:
        nm, lo, hi, rot, fm, grp = b
        if not _inter(lo, hi, clo, chi): out.append(b); continue
        if rot: continue                                                   # a turned box through the cavity: drop it
        base = _plain(fm)
        for a, c in _subtract(lo, hi, clo, chi):
            out.append((nm, a, c, None, {d: (s if s is None or bbkit._mat(s)[0] not in TEXTY else base) for d, s in fm.items()}, grp))
    m.boxes[:] = out
    g0 = m.group
    zb = zf + depth                                                        # back pan
    zc = zb - 1.25                                                         # the front of the DIN-rail gear
    W, H = x1 - x0 - 0.7, y1 - y0 - 0.7
    nf = len(fns)
    sy = min(1.0, H / 10.6)
    sx = min(1.0, W / max(nf * 1.05 + 0.6, 4 * 1.2 + 3.8))
    # ---- 3. the inside
    m.g('cabinet inside')
    m.box((x0, y0, zb - 0.12), (x1, y1, zb), 'galv', 'mounting plate')
    for (cx, cy) in ((x0 + 0.35, y0 + 0.35), (x1 - 0.35, y0 + 0.35), (x0 + 0.35, y1 - 0.35), (x1 - 0.35, y1 - 0.35)):
        m.box((cx - 0.15, cy - 0.15, zb - 0.3), (cx + 0.15, cy + 0.15, zb - 0.12), 'metal:steel', 'plate fixing')
    L, R = x0 + 0.35, x1 - 0.35                                            # usable width (model x)
    y = y1 - 0.35
    sf = m.surface(side, face=round(zc, 3), title='CABINET')
    # row 0: stickers
    h0 = 1.1 * sy
    m.box((R - min(4.2, W * 0.55), y - h0, zb - 0.16), (R, y, zb - 0.12), 'plate', 'rating plate', north='plate:' + title.split('|')[0] + '|' + volts)
    m.box((L, y - h0, zb - 0.16), (L + min(2.4, W * 0.3), y - h0 * 0.1, zb - 0.12), 'danger' if hv else 'warn', 'danger sticker',
          north=('danger:DANGER|HV' if hv else 'warn'))
    m.ctl(sf, 'lamp', 'cab_light', 16 - (L + R) / 2, y1 - 0.18, w=min(4.0, W * 0.4), h=0.16, col='fff4d0', label='CABINET LIGHT')   # door switch lamp
    gl, gr = L + min(2.4, W * 0.3) + 0.2, R - min(4.2, W * 0.55) - 0.2
    if gr - gl > 0.8:
        m.ctl(sf, 'push', 'cab_lt', 16 - (gl + gr) / 2, y - h0 / 2, w=min(0.6, gr - gl - 0.3), h=min(0.6, h0 * 0.7), col='f0f0f0', label='LAMP TEST')
    y -= h0 + 0.15
    def duct(yt, h=0.55):
        m.box((L, yt - h, zb - 1.0), (R, yt, zb - 0.12), 'paint:#8e9294', 'wiring duct')
        m.box((L, yt - h * 0.25, zb - 1.02), (R, yt - h * 0.1, zb - 1.0), 'paint:#5c6064', 'duct cover slot')
        return yt - h - 0.1
    y = duct(y, 0.6 * sy)
    # row 1: MCBs on a DIN rail
    h1 = 2.0 * sy
    m.box((L, y - h1 * 0.55, zb - 0.35), (R, y - h1 * 0.45, zb - 0.12), 'metal:steel', 'DIN rail')
    mw = 1.0 * sx
    for i, (fid, flab) in enumerate(fns):
        cx = R - 0.35 - mw / 2 - i * (mw + 0.05 * sx)
        m.box((cx - mw / 2, y - h1, zc + 0.1), (cx + mw / 2, y, zb - 0.35), 'paint:#efefea', 'MCB ' + flab)
        m.box((cx - mw / 2 + 0.05, y - h1 * 0.22, zc + 0.08), (cx + mw / 2 - 0.05, y - 0.05, zc + 0.1), 'label', 'MCB label', north='label:F%d' % (i + 1))
        m.box((cx - mw * 0.3, y - h1 * 0.9, zc + 0.08), (cx + mw * 0.3, y - h1 * 0.75, zc + 0.1), 'paint:#2a62b0', 'MCB rating window')
        m.ctl(sf, 'lever', 'm_cab_mcb_' + fid, 16 - cx, y - h1 * 0.5, w=0.26 * sx, h=0.38 * sy, col='f4f4f4', label='F%d %s' % (i + 1, flab))
    # spare MCB blanks to the end of the rail
    k = nf
    while True:
        cx = R - 0.35 - mw / 2 - k * (mw + 0.05 * sx)
        if cx - mw / 2 < L + 0.2: break
        m.box((cx - mw / 2, y - h1 * 0.8, zc + 0.5), (cx + mw / 2, y - h1 * 0.2, zb - 0.35), 'paint:#d8d8d2', 'spare way blank')
        k += 1
    y -= h1 + 0.1
    y = duct(y, 0.55 * sy)
    # row 2: interface relays (flag + coil LED) and the PLC
    h2 = 2.4 * sy
    m.box((L, y - h2 * 0.55, zb - 0.35), (R, y - h2 * 0.45, zb - 0.12), 'metal:steel', 'DIN rail')
    rw = 1.15 * sx
    for i, rl in enumerate(RELAYS):
        cx = R - 0.3 - rw / 2 - i * (rw + 0.08 * sx)
        m.box((cx - rw / 2, y - h2, zc + 0.6), (cx + rw / 2, y, zb - 0.35), 'paint:#1e1e22', 'relay socket')
        m.box((cx - rw / 2 + 0.08, y - h2 + 0.25 * sy, zc + 0.1), (cx + rw / 2 - 0.08, y - 0.15 * sy, zc + 0.6), 'paint:#3a5c8c', 'relay ' + rl)
        m.ctl(sf, 'flag', 'cab_k%d' % (i + 1), 16 - cx, y - h2 * 0.42, w=0.6 * sx, h=0.45 * sy, label=rl)
        m.ctl(sf, 'lamp', 'cab_kl%d' % (i + 1), 16 - cx, y - h2 * 0.78, w=0.24 * sx, h=0.24 * sy, col='ff3020', label='')
        m.box((cx - rw / 2 + 0.1, y - 0.12 * sy - 0.4 * sy, zc + 0.08), (cx + rw / 2 - 0.1, y - 0.16 * sy, zc + 0.1), 'label', 'relay label', north='label:K%d' % (i + 1))
    px1 = R - 0.3 - 4 * (rw + 0.08 * sx) - 0.15
    px0 = max(L + 0.1, px1 - 3.6 * sx)
    m.box((px0, y - h2, zc + 0.2), (px1, y, zb - 0.35), 'ral7035', 'PLC CPU')
    m.box((px0 + 0.1, y - 0.55 * sy, zc + 0.18), (px1 - 0.1, y - 0.08 * sy, zc + 0.2), 'label', 'PLC label', north='label:PLC')
    pu = lambda x: 16 - x
    pw = px1 - px0
    m.ctl(sf, 'lamp', 'cab_run', pu(px1 - pw * 0.15), y - h2 * 0.36, w=0.24 * sx, h=0.24 * sy, col='30ff50', label='PLC RUN', blink=True)
    m.ctl(sf, 'lamp', 'cab_err', pu(px1 - pw * 0.15), y - h2 * 0.58, w=0.24 * sx, h=0.24 * sy, col='ff2a2a', label='PLC ERR')
    for i in range(6):
        m.ctl(sf, 'lamp', 'cab_i%d' % i, pu(px1 - pw * 0.34 - (i % 3) * pw * 0.13), y - h2 * (0.36 if i < 3 else 0.58), w=0.2 * sx, h=0.2 * sy, col='40ff40', label='I%d' % i)
    for i in range(4):
        m.ctl(sf, 'lamp', 'cab_q%d' % i, pu(px1 - pw * 0.34 - (i % 2) * pw * 0.13 - pw * 0.4), y - h2 * (0.36 if i < 2 else 0.58), w=0.2 * sx, h=0.2 * sy, col='ffb020', label='Q%d' % i)
    m.box((px0 + 0.15, y - h2 + 0.05, zc + 0.12), (px1 - 0.15, y - h2 + 0.22 * sy, zc + 0.2), 'paint:#2a2a2e', 'PLC I/O connector')
    m.ctl(sf, 'digits', 'cab_plc', pu((px0 + px1) / 2), y - h2 * 0.8, w=pw * 0.85, h=0.4 * sy, col='7cf29a', label='', fmt='%s')
    y -= h2 + 0.1
    y = duct(y, 0.5 * sy)
    # row 3: fuse holders (carrier drawn by the game: in / pulled / blown) with neon lamps
    h3 = 1.7 * sy
    m.box((L, y - h3 * 0.55, zb - 0.35), (R, y - h3 * 0.45, zb - 0.12), 'metal:steel', 'DIN rail')
    for i, (fid, flab) in enumerate(fns):
        cx = R - 0.35 - mw / 2 - i * (mw + 0.05 * sx)
        m.box((cx - mw / 2, y - h3, zc + 0.75), (cx + mw / 2, y, zb - 0.35), 'paint:#202024', 'fuse holder base')
        m.box((cx - mw / 2, y - 0.3 * sy, zc + 0.73), (cx + mw / 2, y - 0.02, zc + 0.75), 'label', 'fuse label', north='label:FU%d' % (i + 1))
        m.ctl(sf, 'zone', 'm_cab_fuse_' + fid, 16 - cx, y - h3 * 0.55, w=mw * 0.8, h=h3 * 0.7, label='FU%d %s fuse' % (i + 1, flab))
        m.ctl(sf, 'lamp', 'cab_fb_' + fid, 16 - cx, y - h3 * 0.92, w=0.2 * sx, h=0.16 * sy, col='ff5010', label='')
    y -= h3 + 0.1
    # row 4: terminal rail with the wire colours, wires running up into the duct
    h4 = max(0.6, min(1.4 * sy, y - (y0 + 0.3)))
    if h4 > 0.5:
        m.box((L, y - h4 * 0.55, zb - 0.35), (R, y - h4 * 0.45, zb - 0.12), 'metal:steel', 'DIN rail')
        cols = ['paint:#c81e1e', 'paint:#1d1d21', 'paint:#2a3f8a', 'paint:#6b4a2a', 'paint:#1d1d21', 'paint:#7a7a7a', 'paint:#2a62c8',
                'paint:#3a9a3a', 'paint:#c8b020', 'paint:#c81e1e', 'paint:#1d1d21', 'paint:#e07a20']
        tw = 0.36 * sx
        x = R - 0.3
        i = 0
        while x - tw > L + 0.2:
            c = cols[i % len(cols)]
            m.box((x - tw, y - h4, zc + 0.5), (x - 0.03, y, zb - 0.35), 'paint:#7c7c80' if c != 'paint:#3a9a3a' else 'paint:#3a9a3a', 'terminal block')
            m.box((x - tw + 0.05, y - h4 * 0.35, zc + 0.48), (x - 0.08, y - h4 * 0.15, zc + 0.5), c, 'terminal marker')
            m.box((x - tw * 0.65, y, zc + 0.75), (x - tw * 0.35, y + 0.35, zc + 1.0), c.replace('paint:', 'cable:'), 'wire into the duct')
            x -= tw
            i += 1
        m.box((L, y - h4, zc + 0.6), (L + 0.25, y, zb - 0.35), 'paint:#3a9a3a', 'earth bar end clamp')
    # ---- 4. the doors: hinged on the outside edges, swing out toward the viewer (-z)
    leaves = [(x0 - 0.15, (x0 + x1) / 2 - 0.03, 'lo'), ((x0 + x1) / 2 + 0.03, x1 + 0.15, 'hi')] if doors == 2 else [(x0 - 0.15, x1 + 0.15, 'hi')]
    dy0, dy1 = y0 - 0.15, y1 + 0.15
    spec = {'block': m.block, 'title': title.replace('|', ' '), 'hv': hv, 'doors': [],
            'fns': [{'id': f, 'label': l} for f, l in fns], 'relays': list(RELAYS)}
    dsf = m.surface(side, face=round(zf - 0.75, 3), title='DOORS')
    for n, (a, b, hinge_side) in enumerate(leaves, 1):
        m.g('door%d' % n)
        m.box((a, dy0, zf - 0.22), (b, dy1, zf), col, 'door leaf')
        m.box((a + 0.5, dy0 + 0.6, zf - 0.34), (b - 0.5, dy1 - 0.6, zf - 0.22), col, 'door pressing')
        m.box((a + 0.2, dy0 + 0.2, zf), (b - 0.2, dy1 - 0.2, zf + 0.12), 'rubber', 'door gasket (inside)')
        hx = a if hinge_side == 'lo' else b                                # the hinge edge
        lx = b - 0.7 if hinge_side == 'lo' else a + 0.7                   # the handle on the free edge
        hy = (dy0 + dy1) / 2
        m.cyl((lx, hy, zf - 0.55), 0.32, 0.33, 'z', 'paint:#2a2c30', 'lock barrel')
        m.box((lx - 0.15, hy - 1.3, zf - 0.75), (lx + 0.15, hy + 0.1, zf - 0.5), 'metal:steel', 'swing handle')
        m.box((lx - 0.2, hy - 1.45, zf - 0.82), (lx + 0.2, hy - 1.1, zf - 0.5), 'paint:#2a2c30', 'handle grip')
        m.box((lx - 0.3, hy + 0.5, zf - 0.4), (lx + 0.3, hy + 0.9, zf - 0.22), 'galv', 'padlock hasp')
        ww = b - a
        if n == 1:
            m.box((a + 0.8, dy1 - 2.6, zf - 0.4), (a + 0.8 + min(4.2, ww - 1.6), dy1 - 1.2, zf - 0.34), 'label', 'door label', north='label:' + title)
        if hv or n == len(leaves):
            w = min(2.6, ww - 1.8)
            m.box((b - 0.9 - w if hinge_side == 'lo' else a + 0.9, dy1 - 3.0 - w * 0.9, zf - 0.4), (b - 0.9 if hinge_side == 'lo' else a + 0.9 + w, dy1 - 3.0, zf - 0.34),
                  'danger' if hv else 'warn', 'door warning', north='danger:DANGER|' + ('HV' if hv else volts.split(' ')[0]) if hv else 'warn')
        if n == 1:                                                         # the circuit chart in its pocket on the inside
            cw = min(4.0, ww - 1.4)
            m.box((a + 0.7, dy0 + 1.0, zf + 0.12), (a + 0.7 + cw, dy0 + 1.0 + min(5.0, dy1 - dy0 - 2.4), zf + 0.2), 'label', 'circuit chart',
                  south='label:' + '|'.join('F%d %s' % (i + 1, l) for i, (f, l) in enumerate(fns)) + '|K1-4 RELAYS|PLC')
        did = 'm_cab_door%d' % n
        spec['doors'].append({'id': did, 'model': '%s_door%d' % (m.block, n), 'group': 'door%d' % n,
                              'hinge': [round(hx, 3), round(zf - 0.22, 3)], 'angle': 105 if hinge_side == 'lo' else -105})
        m.ctl(dsf, 'zone', did, 16 - (a + b) / 2, (dy0 + dy1) / 2, w=b - a, h=dy1 - dy0, label='door %d' % n)
    m.g('cabinet frame')
    for (a, b, hinge_side) in leaves:                                      # hinge knuckles stay on the frame
        hx = a if hinge_side == 'lo' else b
        for hy in (dy0 + 1.0, dy1 - 2.0):
            m.cyl((hx, hy, zf - 0.22), 0.22, 1.0, 'y', 'metal:steel', 'hinge knuckle')
    m.g(g0)
    m._cabinet = spec
    orig = m.save
    m.save = lambda *a, **k: _save(m, orig, *a, **k)


def _save(m, orig, *a, **k):
    door_groups = {d['group']: d for d in m._cabinet['doors']}
    idx = [i for i, b in enumerate(m.boxes) if b[5] in door_groups]
    r = orig(*a, **k)
    path = os.path.join(ASSETS, 'models/block', m.name + '.json')
    j = json.load(open(path))
    els = j['elements']
    assert len(els) == len(m.boxes), m.name
    full = dict(j)
    json.dump(full, open(os.path.join(ASSETS, 'models/block', m.name + '_full.json'), 'w'), indent=0)
    for g, d in door_groups.items():
        dj = {'credit': 'IR Extras cabinet door (cabkit) - swung by the game', 'ambientocclusion': False,
              'textures': j['textures'], 'elements': [els[i] for i in idx if m.boxes[i][5] == g]}
        json.dump(dj, open(os.path.join(ASSETS, 'models/block', d['model'] + '.json'), 'w'), indent=0)
    j['elements'] = [e for i, e in enumerate(els) if i not in set(idx)]
    json.dump(j, open(path, 'w'), indent=0)
    item = os.path.join(ASSETS, 'models/item', m.block + '.json')
    if os.path.exists(item):                                              # the inventory icon keeps its doors
        it = json.load(open(item))
        if it.get('parent') == 'irextras:block/' + m.name:
            it['parent'] = 'irextras:block/' + m.name + '_full'
            json.dump(it, open(item, 'w'), indent=1)
    d = os.path.join(ASSETS, 'cabinets')
    os.makedirs(d, exist_ok=True)
    spec = json.loads(json.dumps(m._cabinet))
    for x in spec['doors']: x.pop('group', None)
    json.dump(spec, open(os.path.join(d, m.block + '.json'), 'w'), indent=1)
    print('  cabinet: %s doors, %d boxes inside the door models' % (len(door_groups), len(idx)))
    return r


if __name__ == '__main__':                                                 # self-check: python3 cabkit.py
    vol = lambda a, b: (b[0] - a[0]) * (b[1] - a[1]) * (b[2] - a[2])
    lo, hi, clo, chi = [0, 0, 0], [16, 16, 16], [2, 3, -1], [14, 12, 4]
    pieces = _subtract(lo, hi, clo, chi)
    assert abs(sum(vol(a, b) for a, b in pieces) - (vol(lo, hi) - 12 * 9 * 4)) < 1e-6
    assert not any(_inter(a, b, clo, chi) for a, b in pieces)
    box = ('b', [1.0, 2.0, 3.0], [4.0, 5.0, 9.0], {'axis': 'z', 'angle': 45, 'origin': [2, 3, 3]}, {'north': 'label:X', 'east': 'galv'}, 'g')
    for side, yaw in YAW.items():
        t = _turn(_turn(box, -yaw), yaw)
        assert [round(v, 6) for v in t[1]] == box[1] and [round(v, 6) for v in t[2]] == box[2] and t[4] == box[4], side
        assert t[3]['axis'] == 'z' and t[3]['angle'] == 45 and [round(v, 6) for v in t[3]['origin']] == [2, 3, 3], side
    assert _turn(box, -90)[4]['east'] == 'label:X'                       # an east-front cabinet: its north face turns to face east
    print('cabkit self-check ok')
