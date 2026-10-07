#!/usr/bin/env python3
"""Pride Rail stations: textures (64 px), block models, blockstates, item models and names for every
station piece in four architectural styles.

    python3 tools/gen_station_assets.py

Styles: victorian (cast iron + glass + brick), modern (steel + concrete + glass), metro (white tile,
terrazzo, enamel roundel), rural (timber + gravel + corrugated iron).
Pieces must stay in the -16..32 model range and only rotate by 22.5/45 degrees.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from gen_hd_textures import S, rng, smooth_noise, bevel, bolts, text  # noqa: E402

MOD = "irextras"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src/main/resources/assets/irextras")
TEX = os.path.join(ROOT, "textures/blocks")

STYLES = ["victorian", "modern", "metro", "rural"]
LABEL = {"victorian": "Victorian", "modern": "Modern", "metro": "Metro", "rural": "Rural Halt"}

# ---------------------------------------------------------------------------------------------- colours
PAL = {
    "victorian": dict(pave="#a8543f", pave2="#8f4433", col="#1f4d33", trim="#d9c9a0", sign="#6e1f2a", signtxt="#efe3c2", glass="#b7d3dc", wood="#7a4b2a", body="#8a3c2c"),
    "modern":    dict(pave="#a7abaf", pave2="#8e9296", col="#c3c8cd", trim="#2b3036", sign="#1c2f5e", signtxt="#ffffff", glass="#a9cfe0", wood="#9b6a3f", body="#9a9ea3"),
    "metro":     dict(pave="#d9d4c7", pave2="#c3bba7", col="#f1f1ee", trim="#c8102e", sign="#c8102e", signtxt="#ffffff", glass="#b9d7e6", wood="#5a5f66", body="#e9e9e4"),
    "rural":     dict(pave="#7c7a72", pave2="#6c6a62", col="#6b4a2c", trim="#2f5d34", sign="#2f5d34", signtxt="#ffffff", glass="#c4d9df", wood="#7d5634", body="#86847c"),
}


def rgbf(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


def img(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB")


def save(im, name):
    im.save(os.path.join(TEX, name + ".png"))


def base(col, name, grain=0.05, blot=0.06):
    r = rng(name)
    a = np.ones((S, S, 3), np.float32) * rgbf(col)
    a *= (1 + blot * smooth_noise(r, 3) + grain * r.standard_normal((S, S)))[..., None]
    return a


# ---------------------------------------------------------------------------------------------- textures
def tex_paving(st):
    p = PAL[st]
    name = "stn_%s_paving" % st
    if st == "victorian":                              # herringbone brick
        a = base(p["pave"], name, 0.06)
        for y in range(S):
            for x in range(S):
                k = ((x // 4) + (y // 4)) % 4
                if (x + y * (1 if k < 2 else -1)) % 8 == 0:
                    a[y, x] *= 0.62
        a = a * (1 + 0.08 * (rng(name + "b").random((S, S)) - 0.5))[..., None]
    elif st == "modern":                               # 600 mm concrete slabs
        a = base(p["pave"], name, 0.07)
        for k in range(0, S, 16):
            a[k:k + 1] *= 0.6
            a[:, k:k + 1] *= 0.6
    elif st == "metro":                                # terrazzo
        r = rng(name)
        a = base(p["pave"], name, 0.03)
        for _ in range(260):
            x, y = r.integers(0, S, 2)
            c = [rgbf("#8b7a66"), rgbf("#efeae0"), rgbf("#5c5a58"), rgbf("#b08a5a")][r.integers(0, 4)]
            a[y:y + r.integers(1, 3), x:x + r.integers(1, 3)] = c
    else:                                              # tarmac with gravel
        r = rng(name)
        a = base(p["pave"], name, 0.12, 0.08)
        g = r.random((S, S)) < 0.08
        a[g] *= 1.35
    save(img(a), name)


def tex_tactile():
    a = base("#f2c318", "stn_tactile", 0.03)
    im = img(a)
    d = ImageDraw.Draw(im)
    for y in range(4, S, 8):
        for x in range(4 + (4 if (y // 8) % 2 else 0), S, 8):
            d.ellipse([x - 2.6, y - 2.6, x + 2.6, y + 2.6], fill=(214, 166, 10))
            d.ellipse([x - 2.2, y - 2.6, x + 1.4, y + 0.6], fill=(255, 220, 90))
    save(im, "stn_tactile")


def tex_coping():
    a = base("#e7e3da", "stn_coping", 0.04)
    a[:, 0:3] *= 0.75
    for x in range(0, S, 21):
        a[:, x:x + 1] *= 0.6
    save(img(a), "stn_coping")


def tex_line():
    a = base("#fbfbf6", "stn_line", 0.02)
    save(img(a), "stn_line")


def tex_body(st):
    p = PAL[st]
    name = "stn_%s_wall" % st
    if st == "metro":                                  # bevelled white subway tile
        a = base(p["body"], name, 0.02)
        for row, y in enumerate(range(0, S, 8)):
            off = 8 if row % 2 else 0
            a[y:y + 1] *= 0.7
            for x in range(off, S + 16, 16):
                a[y:y + 8, x % S:x % S + 1] *= 0.7
            a[y + 1:y + 2] *= 1.06
    elif st == "victorian":                            # red brick, stretcher bond
        a = base(p["body"], name, 0.06)
        for row, y in enumerate(range(0, S, 6)):
            a[y:y + 1] = rgbf("#cfc6b6")
            off = 8 if row % 2 else 0
            for x in range(off, S + 16, 16):
                a[y:y + 6, x % S:x % S + 1] = rgbf("#cfc6b6")
    elif st == "rural":                                # timber cladding
        a = base(p["wood"], name, 0.05)
        for y in range(0, S, 8):
            a[y:y + 1] *= 0.55
            a[y + 1:y + 2] *= 1.15
    else:                                              # fair-faced concrete
        a = base(p["body"], name, 0.06, 0.09)
        for x in (16, 48):
            for y in (16, 48):
                a[y - 1:y + 1, x - 1:x + 1] *= 0.55
    save(img(a), name)


def tex_column(st):
    p = PAL[st]
    name = "stn_%s_column" % st
    if st == "rural":
        a = base(p["col"], name, 0.04)
        for y in range(S):
            a[y] *= 1 + 0.06 * math.sin(y * 0.7)
    elif st == "metro":
        a = base(p["col"], name, 0.02)
        for y in range(0, S, 4):
            a[y:y + 1] *= 0.85
    else:
        a = base(p["col"], name, 0.03)
        xs = np.linspace(-1, 1, S)[None, :]
        a *= (1.12 - 0.3 * xs ** 2)[..., None]
        if st == "victorian":
            for x in range(4, S, 8):
                a[:, x:x + 2] *= 0.75                  # fluting
    save(img(a), name)


def tex_glass(st):
    p = PAL[st]
    c = rgbf(p["glass"])
    a = np.ones((S, S, 3), np.float32) * c
    yy, xx = np.mgrid[0:S, 0:S]
    a += np.clip(1 - np.abs(xx + yy - 40) / 6, 0, 1)[..., None] * 60
    bar = rgbf(p["col"]) if st != "metro" else rgbf("#55595f")
    for k in range(0, S, 16 if st != "victorian" else 10):
        a[:, k:k + 2] = bar
    a[0:2] = bar
    alpha = np.full((S, S), 150, np.uint8)
    for k in range(0, S, 16 if st != "victorian" else 10):
        alpha[:, k:k + 2] = 255
    alpha[0:2] = 255
    im = img(a).convert("RGBA")
    im.putalpha(Image.fromarray(alpha))
    save(im, "stn_%s_glass" % st)


def tex_roof(st):
    p = PAL[st]
    name = "stn_%s_roof" % st
    if st == "rural":                                  # corrugated iron
        a = base("#8c9196", name, 0.03)
        for x in range(S):
            a[:, x] *= 1 + 0.18 * math.sin(x * 0.8)
        rust = np.clip(smooth_noise(rng(name), 3) - 0.3, 0, 1)
        a = a * (1 - 0.5 * rust[..., None]) + rgbf("#8a4b26") * 0.5 * rust[..., None]
    elif st == "metro":                                # tiled vault with light strip
        a = base("#efefea", name, 0.02)
        a[28:36] = rgbf("#fff6d8") * 1.1
    else:
        a = base(p["col"], name, 0.03)
        for y in range(0, S, 8):
            a[y:y + 1] *= 0.7
    save(img(a), name)


def tex_wood(st):
    a = base(PAL[st]["wood"], "stn_%s_wood" % st, 0.04)
    for y in range(0, S, 10):
        a[y:y + 1] *= 0.5
    for y in range(S):
        a[y] *= 1 + 0.05 * math.sin(y * 1.7 + (y // 10))
    save(img(a), "stn_%s_wood" % st)


def tex_trim(st):
    a = base(PAL[st]["trim"], "stn_%s_trim" % st, 0.03)
    save(img(a), "stn_%s_trim" % st)


def tex_sign(st):
    p = PAL[st]
    a = base(p["sign"], "stn_%s_sign" % st, 0.02, 0.03)
    bevel(a, 0, 0, S, S, light=1.2, dark=0.6, w=2)
    im = img(a)
    d = ImageDraw.Draw(im)
    d.rectangle([3, 3, S - 4, S - 4], outline=tuple(int(v) for v in rgbf(p["signtxt"])), width=1)
    save(im, "stn_%s_sign" % st)


def tex_lamp():
    a = np.ones((S, S, 3), np.float32) * rgbf("#fff3c4")
    yy, xx = np.mgrid[0:S, 0:S]
    a *= np.clip(1.2 - np.sqrt((xx - 32) ** 2 + (yy - 32) ** 2) / 50, 0.8, 1.2)[..., None]
    save(img(a), "stn_lamp_glow")


def tex_clock():
    im = Image.new("RGB", (S, S), (245, 243, 236))
    d = ImageDraw.Draw(im)
    d.ellipse([1, 1, S - 2, S - 2], outline=(25, 25, 25), width=3)
    for k in range(12):
        an = k * math.pi / 6
        r0, r1 = (22, 28) if k % 3 == 0 else (25, 28)
        d.line([32 + r0 * math.sin(an), 32 - r0 * math.cos(an), 32 + r1 * math.sin(an), 32 - r1 * math.cos(an)], fill=(25, 25, 25), width=2)
    save(im, "stn_clock_face")


def tex_bin():
    a = base("#2b2f33", "stn_bin", 0.03)
    for x in range(0, S, 6):
        a[:, x:x + 2] *= 1.3
    a[24:30] = rgbf("#f2c318")
    save(img(a), "stn_bin")


def tex_poster(st):
    p = PAL[st]
    a = base("#f4f1e8", "stn_poster_%s" % st, 0.02, 0.02)
    im = img(a)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, 9], fill=tuple(int(v) for v in rgbf(p["sign"])))
    text(im, (32, 5), "TIMETABLE", 6, tuple(int(v) for v in rgbf(p["signtxt"])))
    for i, y in enumerate(range(14, S - 4, 6)):
        d.line([4, y, 20, y], fill=(70, 70, 70))
        d.line([24, y, 56 - (i * 7) % 18, y], fill=(110, 110, 110))
    d.rectangle([0, 0, S - 1, S - 1], outline=(40, 40, 40))
    save(im, "stn_%s_poster" % st)


def tex_desk():
    a = base("#3d4147", "stn_desk", 0.03)
    bevel(a, 0, 0, S, S, w=2)
    save(img(a), "stn_desk")
    a = np.ones((S, S, 3), np.float32) * rgbf("#071426")
    a[::2] *= 0.85
    im = img(a)
    d = ImageDraw.Draw(im)
    for i, y in enumerate(range(8, S - 6, 9)):
        d.text((3, y), ["12:04 P1 ON TIME", "12:11 P2 +2 MIN", "12:15 P3 ON TIME", "12:22 P1 DELAYED", "12:30 P2 ON TIME", "12:41 P4 CANCELLED"][i % 6],
               fill=(120, 220, 255) if i % 3 else (255, 210, 90))
    save(im, "stn_screen")


# ---------------------------------------------------------------------------------------------- models
def el(a, b, t, rot=None, faces=None):  # uv_fix
    e = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b],
         "faces": {d: {"texture": "#" + (faces.get(d, t) if faces else t)} for d in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


def model(name, els, st, particle="paving"):
    tex = {
        "paving": "stn_%s_paving" % st, "wall": "stn_%s_wall" % st, "column": "stn_%s_column" % st, "glass": "stn_%s_glass" % st,
        "roof": "stn_%s_roof" % st, "wood": "stn_%s_wood" % st, "trim": "stn_%s_trim" % st, "sign": "stn_%s_sign" % st,
        "poster": "stn_%s_poster" % st, "tactile": "stn_tactile", "coping": "stn_coping", "line": "stn_line", "lamp": "stn_lamp_glow",
        "clock": "stn_clock_face", "bin": "stn_bin", "desk": "stn_desk", "screen": "stn_screen", "dark": "ohle_dark", "galv": "ohle_galv",
    }
    d = {"textures": dict({"particle": MOD + ":blocks/" + tex[particle]}, **{k: MOD + ":blocks/" + v for k, v in tex.items()}), "elements": els}
    json.dump(d, open(os.path.join(ROOT, "models/block", name + ".json"), "w"), indent=1)


def pieces(st):
    """kind -> list of elements (north = the track side)"""
    P = {}
    P["platform_edge"] = [
        el((0, 0, 0), (16, 15, 16), "wall", faces={"up": "paving"}),
        el((0, 15, -1.5), (16, 16, 3), "coping"),
        el((0, 15, 3), (16, 16.02, 4), "line"),
        el((0, 15, 4), (16, 16.05, 9), "tactile"),
        el((0, 15, 9), (16, 16, 16), "paving"),
        el((0, 11, -1.5), (16, 15, -0.5), "dark"),                   # shadow recess under the coping
    ]
    P["platform"] = [el((0, 0, 0), (16, 15, 16), "wall", faces={"up": "paving"}), el((0, 15, 0), (16, 16, 16), "paving")]
    col = {
        "victorian": [el((6.5, 0, 6.5), (9.5, 1.5, 9.5), "column"), el((7, 1.5, 7), (9, 28, 9), "column"),
                      el((6.3, 26, 6.3), (9.7, 28, 9.7), "column"), el((5.5, 28, 5.5), (10.5, 29, 10.5), "column"),
                      el((7.4, 21, -2), (8.6, 22, 7), "column", {"origin": [8, 21.5, 7], "axis": "x", "angle": -22.5}),
                      el((7.4, 21, 9), (8.6, 22, 18), "column", {"origin": [8, 21.5, 9], "axis": "x", "angle": 22.5}),
                      el((7.6, 28, -4), (8.4, 29, 20), "column")],
        "modern": [el((6, 0, 6), (10, 0.5, 10), "trim"), el((7, 0.5, 7), (9, 29, 9), "column"), el((7.6, 27, -6), (8.4, 29, 22), "column"),
                   el((7.2, 22, 6.8), (8.8, 27.2, 7.2), "column", {"origin": [8, 27, 7], "axis": "x", "angle": 45})],
        "metro": [el((5.5, 0, 5.5), (10.5, 32, 10.5), "column"), el((5, 0, 5), (11, 1, 11), "trim"), el((5.4, 14, 5.4), (10.6, 15, 10.6), "trim")],
        "rural": [el((6.5, 0, 6.5), (9.5, 28, 9.5), "column"), el((7, 28, -4), (9, 30, 20), "column"),
                  el((7.3, 22, 1), (8.7, 23, 7), "column", {"origin": [8, 22.5, 7], "axis": "x", "angle": -45}),
                  el((7.3, 22, 9), (8.7, 23, 15), "column", {"origin": [8, 22.5, 9], "axis": "x", "angle": 45})],
    }
    P["canopy_column"] = col[st]
    roof = {
        "victorian": [el((0, 13, 0), (16, 14, 16), "glass"), el((0, 14, 7.5), (16, 15.5, 8.5), "column"),
                      el((0, 10, -0.5), (16, 13, 0.5), "trim"), el((0, 12, 15.5), (16, 13, 16.5), "column")],
        "modern": [el((0, 13, 0), (16, 14, 16), "glass"), el((0, 14, 0), (16, 15, 1), "column"), el((0, 14, 15), (16, 15, 16), "column"),
                   el((7.5, 14, 0), (8.5, 15, 16), "column")],
        "metro": [el((0, 14, 0), (16, 16, 16), "roof"), el((2, 13.5, 6), (14, 14, 10), "lamp")],
        "rural": [el((0, 13, 0), (16, 14.5, 16), "roof"), el((0, 11, -0.8), (16, 13, 0.2), "trim"),
                  el((0, 14.5, 7), (16, 15.5, 9), "trim")],
    }
    P["canopy_roof"] = roof[st]
    P["bench"] = [
        el((1.5, 0, 6), (2.5, 4.5, 7), "trim"), el((13.5, 0, 6), (14.5, 4.5, 7), "trim"),
        el((1.5, 0, 11), (2.5, 9, 12), "trim"), el((13.5, 0, 11), (14.5, 9, 12), "trim"),
        el((1, 4.5, 5.5), (15, 5.3, 8), "wood"), el((1, 4.5, 8.2), (15, 5.3, 10.7), "wood"),
        el((1, 6, 10.8), (15, 7.6, 11.5), "wood"), el((1, 8.2, 11), (15, 9.8, 11.7), "wood"),
        el((1, 7, 6), (1.6, 7.6, 11), "trim"), el((14.4, 7, 6), (15, 7.6, 11), "trim"),
    ]
    P["shelter"] = [
        el((0, 0, 14.5), (16, 22, 15.5), "glass"), el((0, 0, 1), (0.8, 22, 14.5), "glass"), el((15.2, 0, 1), (16, 22, 14.5), "glass"),
        el((-0.5, 22, 0), (16.5, 23.5, 16.5), "trim"),
        el((0, 0, 14), (1, 22, 16), "column"), el((15, 0, 14), (16, 22, 16), "column"),
        el((0, 0, 0.5), (1, 22, 1.5), "column"), el((15, 0, 0.5), (16, 22, 1.5), "column"),
        el((2, 4.5, 11.5), (14, 5.3, 14), "wood"), el((2, 0, 12), (3, 4.5, 13), "trim"), el((13, 0, 12), (14, 4.5, 13), "trim"),
        el((2, 9, 14.3), (8, 18, 14.5), "poster"),
    ]
    lamp_head = {
        "victorian": [el((5.5, 25, 5.5), (10.5, 30, 10.5), "lamp"), el((5, 30, 5), (11, 31, 11), "column"), el((7, 31, 7), (9, 32, 9), "column"),
                      el((5, 24.5, 5), (11, 25, 11), "column")],
        "modern": [el((7, 28, 2), (9, 29, 9), "column"), el((6, 27, 1), (10, 28, 6), "lamp")],
        "metro": [el((6, 26, 6), (10, 30, 10), "lamp"), el((5.5, 30, 5.5), (10.5, 31, 10.5), "trim")],
        "rural": [el((7, 28, 4), (9, 29, 9), "column"), el((6, 23, 3), (10, 28, 7), "lamp"), el((5.5, 28, 2.5), (10.5, 29, 7.5), "dark")],
    }
    P["lamp"] = [el((6.5, 0, 6.5), (9.5, 1.5, 9.5), "column"), el((7.3, 1.5, 7.3), (8.7, 25, 8.7), "column")] + lamp_head[st]
    P["bin"] = [el((4.5, 0, 4.5), (11.5, 9, 11.5), "bin"), el((4, 9, 4), (12, 10, 12), "dark"), el((7, 10, 7), (9, 11, 9), "dark")]
    P["clock"] = [
        el((7.3, 0, 7.3), (8.7, 24, 8.7), "column"),
        el((3, 24, 7), (13, 32, 9), "trim"),
        el((3.5, 24.5, 6.9), (12.5, 31.5, 7), "clock"), el((3.5, 24.5, 9), (12.5, 31.5, 9.1), "clock"),
    ]
    P["name_sign"] = [
        el((-6, 0, 7.4), (-5, 18, 8.6), "column"), el((21, 0, 7.4), (22, 18, 8.6), "column"),
        el((-8, 12, 7), (24, 20, 9), "sign"),
    ]
    P["platform_sign"] = [
        el((7.5, 26, 7.5), (8.5, 32, 8.5), "column"),
        el((3, 18, 7), (13, 26, 9), "sign"),
    ]
    P["timetable"] = [el((6.5, 0, 9), (9.5, 4, 10), "column"), el((7.3, 0, 9.3), (8.7, 22, 9.7), "column"),
                      el((2, 8, 8.6), (14, 22, 9.3), "trim"), el((2.5, 8.5, 8.5), (13.5, 21.5, 8.6), "poster")]
    return P


KINDS = ["platform_edge", "platform", "canopy_column", "canopy_roof", "bench", "shelter", "lamp", "bin", "clock", "name_sign",
         "platform_sign", "timetable"]
NAMES = {"platform_edge": "Platform Edge", "platform": "Platform", "canopy_column": "Canopy Column", "canopy_roof": "Canopy Roof",
         "bench": "Platform Bench", "shelter": "Waiting Shelter", "lamp": "Platform Lamp", "bin": "Litter Bin", "clock": "Station Clock",
         "name_sign": "Station Name Board", "platform_sign": "Platform Number Sign", "timetable": "Timetable Board"}


def blockstate(name, model_):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    v = {"facing=%s" % f: ({"model": model_, "y": y} if y else {"model": model_}) for f, y in rot.items()}
    json.dump({"variants": v}, open(os.path.join(ROOT, "blockstates", name + ".json"), "w"), indent=1)
    json.dump({"parent": model_.replace(MOD + ":", MOD + ":block/")}, open(os.path.join(ROOT, "models/item", name + ".json"), "w"))


def main():
    tex_tactile(); tex_coping(); tex_line(); tex_lamp(); tex_clock(); tex_bin(); tex_desk()
    lang = []
    for st in STYLES:
        tex_paving(st); tex_body(st); tex_column(st); tex_glass(st); tex_roof(st); tex_wood(st); tex_trim(st); tex_sign(st); tex_poster(st)
        P = pieces(st)
        for k in KINDS:
            name = "station_%s_%s" % (st, k)
            model(name, P[k], st, "paving" if k.startswith("platform") and k != "platform_sign" else "column")
            blockstate(name, "%s:%s" % (MOD, name))
            lang.append("tile.%s.%s.name=%s %s" % (MOD, name, NAMES[k], "(%s)" % LABEL[st]))
    # station master's desk: one look
    model("station_master", [
        el((0, 0, 4), (16, 12, 14), "desk"), el((-0.5, 12, 3.5), (16.5, 13, 14.5), "wood"),
        el((2, 13, 9), (7, 17, 10), "dark"), el((2.3, 13.3, 8.9), (6.7, 16.7, 9), "screen"),
        el((9, 13, 9), (14, 17, 10), "dark"), el((9.3, 13.3, 8.9), (13.7, 16.7, 9), "screen"),
        el((4, 13, 5), (12, 13.4, 7.5), "dark"), el((13, 13, 5), (14.5, 14.5, 6.5), "trim"),
        el((7.5, 13, 11), (8.5, 22, 12), "column"), el((5, 22, 10.5), (11, 25, 12.5), "sign"),
    ], "modern", "desk")
    blockstate("station_master", MOD + ":station_master")
    lang.append("tile.%s.station_master.name=Station Master's Desk" % MOD)
    path = os.path.join(ROOT, "lang/en_us.lang")
    old = [l for l in open(path).read().split("\n") if not l.startswith("tile.%s.station_" % MOD)]
    open(path, "w").write("\n".join([l for l in old if l] + lang) + "\n")
    print("station assets: %d blocks" % (len(STYLES) * len(KINDS) + 1))


if __name__ == "__main__":
    main()

if __name__ == "__main__":
    import subprocess, sys as _s
    subprocess.run([_s.executable, __file__.replace("gen_station_assets.py", "fix_model_uvs.py")])
