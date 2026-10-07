#!/usr/bin/env python3
"""Pride Rail industries: textures, block models, blockstates, item models, names.

    python3 tools/gen_industry_assets.py

Every industry is one block whose model fills a 3x3 footprint up to two blocks high (the model range
is -16..32), so a placed industry reads as a building, not a box. Put a Loading Silo / Unloading Pit
right beside it and trains carry its goods.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from gen_hd_textures import S, rng, smooth_noise, bevel  # noqa: E402

MOD = "irextras"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src/main/resources/assets/irextras")
TEX = os.path.join(ROOT, "textures/blocks")


def rgbf(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32)


def base(col, name, grain=0.05, blot=0.07):
    r = rng(name)
    a = np.ones((S, S, 3), np.float32) * rgbf(col)
    a *= (1 + blot * smooth_noise(r, 3) + grain * r.standard_normal((S, S)))[..., None]
    return a


def save(a, name):
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").save(os.path.join(TEX, name + ".png"))


def pile(name, cols, lumps=320, size=(2, 5)):
    r = rng(name)
    a = base(cols[0], name, 0.08)
    for _ in range(lumps):
        x, y = r.integers(0, S, 2)
        c = rgbf(cols[r.integers(0, len(cols))]) * r.uniform(0.75, 1.2)
        s = r.integers(size[0], size[1])
        yy, xx = np.ogrid[0:S, 0:S]
        m = (xx - x) ** 2 + (yy - y) ** 2 <= (s / 2) ** 2
        a[m] = c
        a[np.roll(m, 1, 0) & ~m] *= 0.7      # little shadow under each lump
    save(a, name)


def textures():
    pile("ind_coal", ["#1b1c1f", "#2a2b30", "#0f1012", "#3a3c42"])
    pile("ind_ironore", ["#7c5a46", "#9a6b4e", "#5f4536", "#b88a63", "#8f8f92"])
    pile("ind_gravel", ["#8d8a84", "#a29e96", "#6f6c67", "#b7b2a8"], 500, (2, 4))
    pile("ind_wheat", ["#d7b552", "#c9a240", "#e7cc72", "#b48c2e"], 600, (1, 3))
    # log ends + bark
    a = base("#6b4a2b", "ind_bark", 0.06)
    for x in range(0, S, 5):
        a[:, x:x + 1] *= 0.6
    save(a, "ind_bark")
    a = base("#c79a62", "ind_logend", 0.03)
    yy, xx = np.mgrid[0:S, 0:S]
    rr = np.sqrt((xx - 32) ** 2 + (yy - 32) ** 2)
    a *= (1 + 0.12 * np.sin(rr * 1.3))[..., None]
    a[rr > 29] = rgbf("#5b3d22")
    save(a, "ind_logend")
    a = base("#9b9fa4", "ind_sheet", 0.03)                      # corrugated cladding
    for x in range(S):
        a[:, x] *= 1 + 0.15 * math.sin(x * 0.9)
    rust = np.clip(smooth_noise(rng("sheet-rust"), 3) - 0.35, 0, 1)
    a = a * (1 - 0.4 * rust[..., None]) + rgbf("#8a4b26") * 0.4 * rust[..., None]
    save(a, "ind_sheet")
    a = base("#7a3328", "ind_brick", 0.05)                       # industrial brick
    for row, y in enumerate(range(0, S, 6)):
        a[y:y + 1] = rgbf("#4a3a33")
        for x in range(8 if row % 2 else 0, S + 16, 16):
            a[y:y + 6, x % S:x % S + 1] = rgbf("#4a3a33")
    save(a, "ind_brick")
    a = base("#3a3d42", "ind_steel", 0.03)                       # dark structural steel
    save(a, "ind_steel")
    a = np.ones((S, S, 3), np.float32) * rgbf("#ff7a1a")         # furnace glow
    a *= np.clip(1.3 - smooth_noise(rng("glow"), 3) * 0.5, 0.6, 1.4)[..., None]
    save(a, "ind_glow")
    a = base("#d9d6cf", "ind_concrete", 0.05, 0.1)
    save(a, "ind_concrete")
    a = base("#c8102e", "ind_awning", 0.02)                      # striped market awning
    for x in range(0, S, 16):
        a[:, x:x + 8] = rgbf("#f4f1e8")
    save(a, "ind_awning")
    a = base("#e9e3d3", "ind_shopfront", 0.02)
    im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    d.rectangle([4, 20, 28, 60], fill=(70, 110, 140), outline=(50, 40, 30), width=2)
    d.rectangle([36, 26, 60, 60], fill=(90, 60, 40), outline=(50, 40, 30), width=2)
    d.rectangle([2, 4, 62, 14], fill=(40, 70, 50))
    im.save(os.path.join(TEX, "ind_shopfront.png"))
    a = base("#3f6d3a", "ind_field", 0.1)                         # crop rows
    for y in range(0, S, 6):
        a[y:y + 2] *= 0.6
    save(a, "ind_field")


def el(a, b, t, rot=None):
    e = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b],
         "faces": {d: {"texture": "#" + t} for d in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


T = {"coal": "ind_coal", "ore": "ind_ironore", "gravel": "ind_gravel", "wheat": "ind_wheat", "bark": "ind_bark", "logend": "ind_logend",
     "sheet": "ind_sheet", "brick": "ind_brick", "steel": "ind_steel", "glow": "ind_glow", "conc": "ind_concrete", "awning": "ind_awning",
     "shop": "ind_shopfront", "field": "ind_field", "galv": "ohle_galv", "dark": "ohle_dark", "wood": "stn_rural_wood",
     "roof": "stn_rural_roof", "tank": "grid_tank", "cab": "grid_cabinet", "warn": "grid_warning", "fins": "grid_fins"}


def model(name, els, particle):
    d = {"textures": dict({"particle": MOD + ":blocks/" + T[particle]}, **{k: MOD + ":blocks/" + v for k, v in T.items()}), "elements": els}
    json.dump(d, open(os.path.join(ROOT, "models/block", name + ".json"), "w"), indent=1)


def headframe(ore):
    m = [el((-14, 0, -14), (30, 1, 30), "conc")]
    for x in (-6, 10):                                           # A-frame headframe legs + wheel
        m.append(el((x, 1, -6), (x + 2, 30, -4), "steel"))
        m.append(el((x, 1, 6), (x + 2, 26, 8), "steel", {"origin": [x + 1, 1, 7], "axis": "x", "angle": -22.5}))
    m += [el((-6, 28, -7), (12, 30, -3), "steel"), el((1, 26, -9), (5, 32, -1), "dark", None),
          el((2.5, 1, -6), (3.5, 28, -5), "galv")]
    m += [el((14, 1, 4), (30, 10, 20), ore), el((17, 10, 7), (27, 14, 17), ore), el((20, 14, 10), (24, 16, 14), ore)]   # spoil heap
    m += [el((-14, 1, 12), (2, 12, 28), "sheet"), el((-15, 12, 11), (3, 13, 29), "roof")]                                # winding house
    m += [el((4, 1, 18), (12, 5, 26), "steel"), el((5, 5, 19), (11, 7, 25), ore)]                                           # ore tub
    return m


def models():
    M = {}
    M["coal_mine"] = (headframe("coal"), "coal")
    M["iron_mine"] = (headframe("ore"), "ore")
    M["quarry"] = ([el((-14, 0, -14), (30, 1, 30), "gravel"), el((-12, 1, -12), (4, 12, 4), "gravel"), el((-9, 12, -9), (1, 18, 1), "gravel"),
                    el((6, 1, -10), (22, 14, 6), "sheet"), el((5, 14, -11), (23, 15, 7), "roof"),
                    el((8, 6, 6), (12, 7, 28), "steel", {"origin": [10, 6.5, 6], "axis": "x", "angle": -22.5}),
                    el((14, 1, 18), (28, 6, 30), "gravel"), el((18, 6, 21), (25, 9, 27), "gravel"),
                    el((-10, 1, 14), (-2, 7, 26), "steel"), el((-9, 7, 15), (-3, 9, 25), "warn")], "gravel")
    logs = []
    for i, y in enumerate((1, 5, 9)):
        for z in range(-12 + (2 if i % 2 else 0), 12, 5):
            logs.append(el((-12, y, z), (6, y + 4, z + 4), "bark"))
            logs.append(el((6, y, z), (6.2, y + 4, z + 4), "logend"))
    M["logging_camp"] = ([el((-14, 0, -14), (30, 1, 30), "field")] + logs +
                         [el((12, 1, 12), (28, 12, 28), "wood"), el((11, 12, 11), (29, 13, 29), "roof"), el((14, 1, -8), (26, 6, -4), "steel"),
                          el((19, 6, -10), (21, 12, -2), "galv")], "bark")
    M["farm"] = ([el((-14, 0, -14), (30, 1, 30), "field"), el((-14, 1, -14), (8, 3, 8), "wheat"),
                  el((10, 1, 6), (28, 16, 28), "brick"), el((9, 16, 5), (29, 18, 29), "roof"), el((13, 18, 9), (25, 22, 25), "roof"),
                  el((-12, 1, 14), (-2, 30, 24), "tank"), el((-11.5, 30, 14.5), (-2.5, 32, 23.5), "galv"),
                  el((14, 1, 5.8), (22, 12, 6), "dark")], "wheat")
    M["sawmill"] = ([el((-14, 0, -14), (30, 1, 30), "conc"), el((-8, 1, -8), (24, 16, 24), "wood"),
                     el((-9, 16, -9), (25, 18, 25), "roof"), el((-6, 18, -6), (22, 22, 22), "roof"),
                     el((-14, 1, -14), (-8, 5, 30), "bark"), el((24, 1, 4), (30, 3, 12), "logend"),
                     el((4, 22, 4), (8, 30, 8), "steel"), el((-8, 4, -8.2), (4, 12, -8), "dark")], "wood")
    M["steel_mill"] = ([el((-14, 0, -14), (30, 1, 30), "conc"), el((-12, 1, -12), (16, 20, 20), "brick"), el((-13, 20, -13), (17, 21, 21), "roof"),
                        el((18, 1, -6), (28, 30, 4), "brick"), el((19, 30, -5), (27, 32, 3), "dark"),
                        el((-4, 1, -12.4), (8, 9, -12), "glow"), el((20, 1, 8), (30, 12, 28), "tank"), el((22, 12, 10), (28, 18, 26), "fins"),
                        el((-12, 1, 22), (14, 4, 30), "ore")], "brick")
    M["flour_mill"] = ([el((-14, 0, -14), (30, 1, 30), "conc"), el((-6, 1, -6), (22, 24, 22), "brick"), el((-7, 24, -7), (23, 26, 23), "roof"),
                        el((-3, 26, -3), (19, 30, 19), "roof"), el((7, 14, -8), (9, 16, 24), "wood", {"origin": [8, 15, 8], "axis": "z", "angle": 45}),
                        el((7, 14, -8), (9, 16, 24), "wood", {"origin": [8, 15, 8], "axis": "z", "angle": -45}),
                        el((22, 1, 6), (30, 18, 14), "tank"), el((-14, 1, 22), (6, 4, 30), "wheat")], "brick")
    M["cement_works"] = ([el((-14, 0, -14), (30, 1, 30), "conc"), el((-12, 6, -4), (28, 12, 4), "tank", {"origin": [8, 9, 0], "axis": "z", "angle": 22.5}),
                          el((-10, 1, -2), (-8, 6, 2), "steel"), el((20, 1, -2), (22, 10, 2), "steel"),
                          el((-12, 1, 10), (-2, 30, 20), "conc"), el((2, 1, 10), (12, 30, 20), "conc"), el((-12.5, 30, 9.5), (12.5, 32, 20.5), "galv"),
                          el((16, 1, 12), (28, 6, 28), "gravel")], "conc")
    M["power_station"] = ([el((-14, 0, -14), (30, 1, 30), "conc"), el((-12, 1, -10), (14, 22, 18), "brick"), el((-13, 22, -11), (15, 24, 19), "roof"),
                           el((18, 1, -12), (24, 32, -6), "brick"), el((17.5, 30, -12.5), (24.5, 32, -5.5), "dark"),
                           el((16, 1, 4), (30, 26, 18), "conc"), el((17, 26, 5), (29, 28, 17), "dark"),
                           el((-12, 1, 20), (14, 6, 30), "coal"), el((-8, 6, 23), (8, 9, 28), "coal"),
                           el((-2, 6, -10.4), (10, 18, -10), "fins"), el((-12, 1, -14), (-4, 9, -10), "warn")], "brick")
    M["town_market"] = ([el((-14, 0, -14), (30, 1, 30), "conc"), el((-12, 1, -2), (28, 18, 18), "brick"), el((-13, 18, -3), (29, 20, 19), "roof"),
                         el((-12, 1, -2.2), (28, 14, -2), "shop"), el((-12, 13, -8), (28, 14, -2), "awning", {"origin": [8, 14, -2], "axis": "x", "angle": 22.5}),
                         el((-10, 1, -10), (-9, 13, -9), "steel"), el((25, 1, -10), (26, 13, -9), "steel"),
                         el((0, 20, 2), (16, 28, 14), "brick"), el((-1, 28, 1), (17, 30, 15), "roof")], "brick")
    return M


NAMES = {"coal_mine": "Coal Mine", "iron_mine": "Iron Mine", "quarry": "Quarry", "logging_camp": "Logging Camp", "farm": "Farm",
         "sawmill": "Sawmill", "steel_mill": "Steel Mill", "flour_mill": "Flour Mill & Bakery", "cement_works": "Cement Works",
         "power_station": "Coal Power Station", "town_market": "Town Market"}


def main():
    textures()
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    lang = []
    for k, (els, part) in models().items():
        name = "industry_" + k
        model(name, els, part)
        v = {"facing=%s" % f: ({"model": "%s:%s" % (MOD, name), "y": y} if y else {"model": "%s:%s" % (MOD, name)}) for f, y in rot.items()}
        json.dump({"variants": v}, open(os.path.join(ROOT, "blockstates", name + ".json"), "w"), indent=1)
        json.dump({"parent": "%s:block/%s" % (MOD, name), "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.22, 0.22, 0.22]},
                   "fixed": {"scale": [0.3, 0.3, 0.3]}, "ground": {"scale": [0.15, 0.15, 0.15]},
                   "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.15, 0.15, 0.15]},
                   "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.2, 0.2, 0.2]}}},
                  open(os.path.join(ROOT, "models/item", name + ".json"), "w"))
        lang.append("tile.%s.%s.name=%s" % (MOD, name, NAMES[k]))
    path = os.path.join(ROOT, "lang/en_us.lang")
    old = [l for l in open(path).read().split("\n") if l and not l.startswith("tile.%s.industry_" % MOD)]
    open(path, "w").write("\n".join(old + lang) + "\n")
    print("industries: %d" % len(NAMES))


if __name__ == "__main__":
    main()

if __name__ == "__main__":
    import subprocess, sys as _s
    subprocess.run([_s.executable, __file__.replace("gen_industry_assets.py", "fix_model_uvs.py")])
