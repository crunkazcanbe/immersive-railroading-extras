#!/usr/bin/env python3
"""Upgrade every remaining small (16/32 px) IR Extras block texture to 64x64 with real detail.

The originals are kept in tools/tex_src/ (first run copies them there) and are the INPUT every time,
so this can be re-run safely after tweaking. Per texture:
  - pixel-art edges smoothed at 4x, colours kept
  - a material pass (grain, colour drift, grime low down) chosen by name
  - plates/cabinets/signs get a bevelled edge; tiling materials don't (it would show seams)
  - lamps become domed glowing lenses, screens get scanlines + glow, glass gets reflections,
    road signs get retro-reflective sheeting
Alpha is preserved (contact wire, crossbuck cut-outs, glass).
"""
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
from gen_hd_textures import S, rng, smooth_noise, glow_disc, bevel  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src/main/resources/assets/irextras/textures/blocks")
SRC = os.path.join(HERE, "tex_src")

# tile seamlessly across big surfaces: no bevel, no top/bottom grime gradient
TILING = {"concrete", "galvanized", "stainless", "steel", "post_metal", "post_wood", "drumwood", "ohle", "ohle_concrete",
          "ohle_dark", "ohle_galv", "ohle_wire", "contactwire", "pit", "silo", "psd_alu", "psd_rubber", "scale_deck",
          "checker", "hazard", "psd_hazard", "redband", "insulated_joint", "bumper_steel", "blackboard"}
SIGNS = {"crossbuck", "sign_crossbuck", "milepost", "sign_mile", "sign_speed", "sign_speed_plate", "speed_sign",
         "sign_white", "sign_w", "whistle_post", "limitdisc", "idplate", "switch_target", "derail"}
SCREENS = {"sdc_screen", "aei_panel"}
LAMPS = {"psd_lamp_green": (60, 235, 110), "psd_lamp_red": (240, 50, 45), "psd_lamp_off": None}
WOOD = {"post_wood", "drumwood"}
CONCRETE = {"concrete", "ohle_concrete", "pit", "silo"}
METAL = {"galvanized", "stainless", "steel", "post_metal", "ohle", "ohle_galv", "ohle_dark", "psd_alu", "bumper_steel",
         "scale_deck", "ohle_plate", "cabinet", "cabinetdark", "relay_case", "scale_case", "switch_stand", "track_circuit",
         "speaker", "bumper", "bumper_face", "psd_header"}


def smooth_up(img):
    """pixel art -> 64 px with rounded stair-steps but crisp colour areas"""
    rgb = img.convert("RGBA")
    up = rgb.resize((S, S), Image.NEAREST)
    a = up.split()[3]
    col = up.convert("RGB").filter(ImageFilter.GaussianBlur(0.9)).filter(ImageFilter.UnsharpMask(1.2, 160, 1))
    a = a.filter(ImageFilter.GaussianBlur(0.7)).point(lambda v: 255 if v > 110 else 0)
    return col, a


def material(arr, name):
    r = rng("hd-" + name)
    n = smooth_noise(r, 3)
    grain = r.standard_normal((S, S)).astype(np.float32)
    if name in CONCRETE:
        arr *= (1 + 0.07 * n + 0.06 * grain)[..., None]
        pits = r.random((S, S)) < 0.025
        arr[pits] *= 0.72
    elif name in WOOD:
        rows = np.sin(np.linspace(0, 22, S)[:, None] + 2.5 * smooth_noise(r, 2)) * 0.07
        arr *= (1 + rows + 0.03 * grain)[..., None]
    elif name in METAL:
        streak = np.convolve(r.standard_normal(S), np.ones(4) / 4, mode="same").astype(np.float32)
        arr *= (1 + 0.035 * streak[None, :] + 0.05 * n + 0.015 * grain)[..., None]
    else:
        arr *= (1 + 0.04 * n + 0.02 * grain)[..., None]
    if name not in TILING:
        ys = np.linspace(0, 1, S)[:, None]
        dirt = np.clip(smooth_noise(r, 4) * 0.5 + ys - 0.45, 0, 1)
        arr *= (1 - 0.14 * dirt)[..., None]
    return arr


def retro_sheeting(arr, name):
    """the faint prismatic dot grid of road-sign reflective film"""
    yy, xx = np.mgrid[0:S, 0:S]
    dots = ((xx % 4 == 1) & (yy % 4 == 1)) | ((xx % 4 == 3) & (yy % 4 == 3))
    arr[dots] *= 1.06
    yy2 = np.linspace(-1, 1, S)
    sheen = np.clip(1 - np.abs(yy[..., None] / S - xx[..., None] / S), 0, 1) * 0
    return arr + sheen


def screen(arr):
    arr[::2] *= 0.82
    glow = np.asarray(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.2)), np.float32)
    return arr + glow * 0.35


def glass(img, a):
    arr = np.asarray(img, np.float32)
    yy, xx = np.mgrid[0:S, 0:S]
    band = np.clip(1 - np.abs((xx * 0.6 + yy) - 30) / 6, 0, 1) + 0.6 * np.clip(1 - np.abs((xx * 0.6 + yy) - 48) / 3, 0, 1)
    arr = arr + band[..., None] * 70
    arr[0:2] *= 1.25
    alpha = np.asarray(a, np.float32)
    alpha = np.clip(alpha * 0.55 + band * 70, 0, 255)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)), Image.fromarray(alpha.astype(np.uint8))


def lamp(name, col):
    base = np.ones((S, S, 3), np.float32) * np.array([45, 47, 52], np.float32)
    base *= (1 + 0.04 * smooth_noise(rng(name), 3))[..., None]
    bevel(base, 0, 0, S, S, w=2)
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8))
    glow_disc(img, 32, 32, 22, col if col else (70, 74, 80), on=col is not None)
    return img


def upgrade(fn):
    name = fn[:-4]
    src = os.path.join(SRC, fn)
    if name in LAMPS:
        lamp(name, LAMPS[name]).save(os.path.join(TEX, fn))
        return
    col, a = smooth_up(Image.open(src))
    if name == "psd_glass":
        col, a = glass(col, a)
        out = col.convert("RGBA")
        out.putalpha(a)
        out.save(os.path.join(TEX, fn))
        return
    arr = material(np.asarray(col, np.float32).copy(), name)
    if name in SIGNS:
        arr = retro_sheeting(arr, name)
    if name in SCREENS:
        arr = screen(arr)
    if name not in TILING:
        bevel(arr, 0, 0, S, S, light=1.12, dark=0.7, w=1)
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert("RGBA")
    out.putalpha(a)
    out.save(os.path.join(TEX, fn))


def main():
    os.makedirs(SRC, exist_ok=True)
    todo = []
    for fn in sorted(os.listdir(TEX)):
        if not fn.endswith(".png") or fn.startswith("grid_"):
            continue
        keep = os.path.join(SRC, fn)
        if not os.path.exists(keep):
            if Image.open(os.path.join(TEX, fn)).size[0] > 32:
                continue                                   # already HD (the 512 px boards etc.)
            shutil.copy2(os.path.join(TEX, fn), keep)
        todo.append(fn)
    for fn in todo:
        upgrade(fn)
    print("upgraded %d textures to %dx%d" % (len(todo), S, S))


if __name__ == "__main__":
    main()
