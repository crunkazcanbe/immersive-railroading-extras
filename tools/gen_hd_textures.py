#!/usr/bin/env python3
"""HD (64x64) textures for the Pride Rail power grid machines.

The originals were 16x16 flat colour. Same layouts (the models' UVs don't change), but every
texture is now drawn at 4x: brushed / painted metal, bolts, panel seams, grime, glowing lamps,
lettering. Deterministic (seeded per texture) so rebuilds don't flicker in git.

    python3 tools/gen_hd_textures.py            # writes over textures/blocks/grid_*.png
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

S = 64
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/irextras/textures/blocks")
FONT_PATHS = ["/usr/share/fonts/TTF/DejaVuSans-Bold.ttf", os.path.expanduser("~/.local/share/fonts/Roboto.ttf")]


def font(px):
    for p in FONT_PATHS:
        if os.path.exists(p):
            return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def rng(name):
    return np.random.default_rng(abs(hash(name)) % (2 ** 32))


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def to_img(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB")


def smooth_noise(r, scale, octaves=3):
    """value noise in [-1, 1], tiles nothing fancy, just soft blotches"""
    out = np.zeros((S, S), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = max(2, int(scale * (2 ** o)))
        g = r.uniform(-1, 1, (n + 1, n + 1)).astype(np.float32)
        im = Image.fromarray(((g + 1) * 127.5).astype(np.uint8)).resize((S, S), Image.BICUBIC)
        out += amp * (np.asarray(im, np.float32) / 127.5 - 1)
        tot += amp
        amp *= 0.5
    return out / tot


def painted(base, name, grime=0.10, chips=0.0):
    """painted steel: slight colour drift, dirt collecting low down, a few paint chips"""
    r = rng(name)
    a = np.ones((S, S, 3), np.float32) * rgb(base)
    a *= (1 + 0.06 * smooth_noise(r, 3))[..., None]
    a *= (1 + 0.025 * r.standard_normal((S, S)))[..., None]
    ys = np.linspace(0, 1, S)[:, None]
    dirt = np.clip(smooth_noise(r, 4) * 0.5 + ys * 0.9 - 0.35, 0, 1)
    a = a * (1 - grime * dirt[..., None]) + rgb("#3a3328") * (grime * dirt[..., None])
    if chips:
        for _ in range(int(chips)):
            x, y = r.integers(2, S - 3, 2)
            a[y:y + r.integers(1, 3), x:x + r.integers(1, 4)] = rgb("#6d6a66")
    return a


def brushed(base, name, vertical=False):
    r = rng(name)
    a = np.ones((S, S, 3), np.float32) * rgb(base)
    streak = r.standard_normal(S).astype(np.float32)
    streak = np.convolve(streak, np.ones(3) / 3, mode="same")
    lines = (streak[None, :] if vertical else streak[:, None]) * np.ones((S, S), np.float32)
    a *= (1 + 0.05 * lines + 0.02 * r.standard_normal((S, S)))[..., None]
    a *= (1 + 0.05 * smooth_noise(r, 2))[..., None]
    return a


def bevel(a, x0, y0, x1, y1, light=1.18, dark=0.62, w=1):
    """raised panel edge: light top/left, dark bottom/right"""
    a[y0:y0 + w, x0:x1] *= light
    a[y0:y1, x0:x0 + w] *= light
    a[y1 - w:y1, x0:x1] *= dark
    a[y0:y1, x1 - w:x1] *= dark


def seam(a, x0, y0, x1, y1):
    """recessed panel line"""
    bevel(a, x0, y0, x1, y1, light=0.6, dark=1.15)


def bolts(img, pts, r=1.6, col=(170, 172, 176)):
    d = ImageDraw.Draw(img)
    for x, y in pts:
        d.ellipse([x - r, y - r, x + r, y + r], fill=tuple(int(c * 0.55) for c in col))
        d.ellipse([x - r + 0.4, y - r + 0.2, x + r - 0.8, y + r - 0.8], fill=col)
        d.point((x - 0.5, y - 0.6), fill=(235, 236, 238))


def corner_bolts(img, m=4):
    bolts(img, [(m, m), (S - 1 - m, m), (m, S - 1 - m), (S - 1 - m, S - 1 - m)])


def text(img, xy, s, px, fill, anchor="mm"):
    d = ImageDraw.Draw(img)
    d.text(xy, s, font=font(px), fill=fill, anchor=anchor)


def glow_disc(img, cx, cy, rad, col, on=True):
    """a lit lens: dark bezel, domed coloured glass with highlight"""
    d = ImageDraw.Draw(img)
    d.ellipse([cx - rad - 4, cy - rad - 4, cx + rad + 4, cy + rad + 4], fill=(28, 30, 33))
    d.ellipse([cx - rad - 2, cy - rad - 2, cx + rad + 2, cy + rad + 2], fill=(70, 73, 78))
    base = np.array(col, np.float32)
    lens = Image.new("RGB", (S, S))
    la = np.zeros((S, S, 3), np.float32)
    yy, xx = np.mgrid[0:S, 0:S]
    dist = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / rad
    shade = np.clip(1.25 - 0.55 * dist, 0.35, 1.3) if on else np.clip(0.55 - 0.25 * dist, 0.2, 0.6)
    la[:] = base * shade[..., None]
    lens = to_img(la)
    mask = Image.new("L", (S, S))
    ImageDraw.Draw(mask).ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=255)
    img.paste(lens, (0, 0), mask)
    d.ellipse([cx - rad * 0.55, cy - rad * 0.7, cx - rad * 0.05, cy - rad * 0.3], fill=tuple(min(255, int(c * 0.4 + 160)) for c in col))


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))


# ---------------------------------------------------------------------------------------------
def battery():
    a = painted("#e3e6e8", "battery", grime=0.12)
    for x in (16, 32, 48):
        seam(a, x - 1, 0, x + 1, S)
    a[34:40] = painted("#1f6fd1", "battery-band")[34:40]
    bevel(a, 0, 34, S, 40)
    img = to_img(a)
    for x in (8, 24, 40, 56):
        d = ImageDraw.Draw(img)
        d.rectangle([x - 5, 8, x + 5, 26], fill=(205, 209, 213), outline=(120, 124, 130))
        for k in range(5):
            d.line([x - 3, 11 + k * 3, x + 3, 11 + k * 3], fill=(150, 154, 160))
    text(img, (32, 50), "Li-ION  BESS", 7, (60, 66, 74))
    text(img, (32, 58), "750 V DC", 6, (200, 40, 40))
    corner_bolts(img, 2)
    save(img, "grid_battery")


def blade():
    a = brushed("#eef0f1", "blade", vertical=True)
    xs = np.linspace(-1, 1, S)[None, :]
    a *= (1.08 - 0.18 * xs ** 2)[..., None]          # aerofoil curve
    a[:, 30:33] *= 0.82                             # spar line
    img = to_img(a)
    d = ImageDraw.Draw(img)
    for y in (6, 22, 38, 54):
        d.line([0, y, S, y + 1], fill=(205, 208, 212))
    d.rectangle([0, 0, S, 3], fill=(200, 40, 40))  # red tip band
    save(img, "grid_blade")


def button(name, col, symbol):
    a = painted("#3b3f45", name, grime=0.08)
    bevel(a, 2, 2, S - 2, S - 2, w=2)
    img = to_img(a)
    glow_disc(img, 32, 30, 18, col)
    d = ImageDraw.Draw(img)
    if symbol == "up":
        d.polygon([(32, 20), (22, 37), (42, 37)], fill=(255, 238, 170))
    elif symbol == "down":
        d.polygon([(32, 40), (22, 23), (42, 23)], fill=(255, 238, 170))
    text(img, (32, 58), {"start": "START", "stop": "STOP", "up": "RAISE", "down": "LOWER"}[symbol], 7, (225, 228, 232))
    corner_bolts(img, 4)
    save(img, name)


def cabinet():
    a = painted("#c9cdd1", "cabinet", grime=0.14, chips=10)
    seam(a, 31, 0, 33, S)
    for x0 in (2, 34):
        bevel(a, x0, 2, x0 + 28, S - 2)
    img = to_img(a)
    d = ImageDraw.Draw(img)
    for x in (28, 36):                                      # door handles
        d.rectangle([x - 1, 26, x + 1, 38], fill=(60, 63, 68))
        d.rectangle([x - 1, 26, x, 38], fill=(150, 154, 160))
    for y in range(8, 18, 3):                               # vents
        d.line([8, y, 24, y], fill=(90, 94, 100))
        d.line([40, y, 56, y], fill=(90, 94, 100))
    d.rectangle([40, 44, 56, 54], fill=(250, 210, 40), outline=(30, 30, 30))
    d.polygon([(48, 46), (44, 53), (52, 53)], outline=(30, 30, 30))
    text(img, (48, 51), "!", 6, (30, 30, 30))
    save(img, "grid_cabinet")


def cable():
    r = rng("cable")
    a = np.ones((S, S, 3), np.float32) * rgb("#1d1f22")
    ys = np.linspace(-1, 1, S)[:, None]
    a *= (1.25 - 0.6 * ys ** 2)[..., None] * np.ones((1, S, 1))
    for x in range(0, S, 6):                                   # armour wire wrap
        a[:, x:x + 2] *= 1.35
    a *= (1 + 0.05 * r.standard_normal((S, S)))[..., None]
    a[28:36] = a[28:36] * 0.6 + rgb("#7a1f1f") * 0.4           # red marker stripe
    save(to_img(a), "grid_cable")


def copper():
    r = rng("copper")
    a = brushed("#c3743a", "copper")
    patina = np.clip(smooth_noise(r, 3) - 0.35, 0, 1)
    a = a * (1 - 0.6 * patina[..., None]) + rgb("#4f9a83") * (0.6 * patina[..., None])
    for y in range(4, S, 12):
        bevel(a, 0, y, S, y + 8, light=1.2, dark=0.7)
    save(to_img(a), "grid_copper")


def display():
    a = np.ones((S, S, 3), np.float32) * rgb("#06120b")
    a *= (1 + 0.15 * smooth_noise(rng("display"), 2))[..., None]
    for y in range(0, S, 2):
        a[y] *= 0.8                                         # scanlines
    img = to_img(a)
    d = ImageDraw.Draw(img)
    g = (90, 255, 140)
    text(img, (4, 8), "LINE  750V", 7, g, anchor="lm")
    text(img, (4, 19), "LOAD  62%", 7, g, anchor="lm")
    d.rectangle([4, 26, 60, 31], outline=(40, 120, 70))
    d.rectangle([5, 27, 38, 30], fill=g)
    pts = [(4 + i * 4, 50 - int(9 * math.sin(i * 0.7) + 4 * math.sin(i * 1.9))) for i in range(15)]
    d.line(pts, fill=(255, 215, 90), width=1)
    d.line([4, 58, 60, 58], fill=(40, 120, 70))
    text(img, (60, 8), "OK", 7, (255, 255, 255), anchor="rm")
    save(img, "grid_display")


def engine():
    a = painted("#2f6b3a", "engine", grime=0.25, chips=14)
    for y in (14, 30, 46):
        seam(a, 0, y, S, y + 2)
    img = to_img(a)
    d = ImageDraw.Draw(img)
    for x in range(8, S, 12):                                # cylinder heads
        d.rounded_rectangle([x - 4, 2, x + 4, 12], 2, fill=(60, 64, 70), outline=(25, 27, 30))
        bolts(img, [(x - 2, 4), (x + 2, 4), (x - 2, 10), (x + 2, 10)], r=0.9)
    for y in range(34, 44, 3):
        d.line([4, y, 60, y], fill=(30, 52, 34))
    d.rectangle([12, 49, 52, 61], fill=(235, 235, 225), outline=(40, 40, 40))
    text(img, (32, 55), "V12 GENSET", 7, (30, 30, 30))
    save(img, "grid_engine")


def fins():
    a = painted("#5e6d64", "fins", grime=0.15)
    for x in range(0, S, 6):
        a[:, x:x + 2] *= 1.3
        a[:, x + 4:x + 6] *= 0.55
    save(to_img(a), "grid_fins")


def lamp_amber():
    a = painted("#5a3d0c", "lamp", grime=0.05)
    img = to_img(a)
    glow_disc(img, 32, 32, 22, (255, 168, 30))
    corner_bolts(img, 3)
    save(img, "grid_lamp_amber")


def louvre():
    a = painted("#c4c8cc", "louvre", grime=0.12)
    for y in range(4, S - 4, 8):
        a[y:y + 5, 4:S - 4] *= 0.45
        a[y:y + 1, 4:S - 4] = a[y:y + 1, 4:S - 4] / 0.45 * 1.25
    bevel(a, 2, 2, S - 2, S - 2, w=2)
    img = to_img(a)
    corner_bolts(img, 3)
    save(img, "grid_louvre")


def scada():
    a = np.ones((S, S, 3), np.float32) * rgb("#081227")
    for y in range(0, S, 2):
        a[y] *= 0.85
    img = to_img(a)
    d = ImageDraw.Draw(img)
    c = (80, 200, 255)
    d.line([4, 14, 60, 14], fill=c, width=2)                       # busbar
    for x in (12, 26, 40, 54):
        live = x != 40
        col = (90, 255, 120) if live else (255, 70, 70)
        d.line([x, 14, x, 44], fill=c, width=2)
        d.rectangle([x - 3, 24, x + 3, 32], fill=col)                 # breaker state
        d.ellipse([x - 3, 44, x + 3, 50], outline=c)
    text(img, (32, 6), "SUBSTATION A", 6, (200, 230, 255))
    text(img, (32, 58), "3 OF 4 LIVE", 6, (255, 215, 90))
    save(img, "grid_scada")


def solar_cells():
    r = rng("solar")
    a = np.ones((S, S, 3), np.float32) * rgb("#13224d")
    a *= (1 + 0.10 * smooth_noise(r, 6))[..., None]
    yy, xx = np.mgrid[0:S, 0:S]
    sheen = np.clip(1 - np.abs((xx + yy) / S - 0.8) * 3, 0, 1)        # sky reflection
    a = a + sheen[..., None] * rgb("#7aa3e6") * 0.35
    for k in range(0, S, 16):
        a[k:k + 1] = rgb("#c9ced6")
        a[:, k:k + 1] = rgb("#c9ced6")
    for k in range(4, S, 8):                                          # busbars
        a[:, k] = a[:, k] * 0.7 + rgb("#b6bcc6") * 0.3
    save(to_img(a), "grid_solar_cells")


def switchgear():
    a = painted("#e7e1cf", "switchgear", grime=0.08)
    bevel(a, 2, 2, S - 2, S - 2, w=2)
    img = to_img(a)
    d = ImageDraw.Draw(img)
    blue = (30, 70, 160)
    d.line([8, 14, 56, 14], fill=blue, width=3)                        # mimic diagram
    d.line([32, 14, 32, 46], fill=blue, width=3)
    d.rectangle([26, 26, 38, 36], fill=(210, 30, 30), outline=(80, 10, 10))
    d.ellipse([28, 44, 36, 52], outline=blue, width=2)
    d.rectangle([44, 40, 58, 58], fill=(40, 42, 46))                     # operating handle
    d.rectangle([49, 36, 53, 50], fill=(200, 30, 30))
    text(img, (8, 56), "11kV", 7, (30, 30, 30), anchor="lm")
    corner_bolts(img, 3)
    save(img, "grid_switchgear")


def tank():
    a = painted("#6f7f73", "tank", grime=0.3, chips=8)
    xs = np.linspace(-1, 1, S)[None, :]
    a *= (1.12 - 0.3 * xs ** 2)[..., None]                               # cylinder shading
    for y in (6, 57):
        a[y:y + 2] *= 0.6
    img = to_img(a)
    bolts(img, [(x, 7) for x in range(4, S, 8)] + [(x, 58) for x in range(4, S, 8)], r=1.1)
    d = ImageDraw.Draw(img)
    d.rectangle([18, 22, 46, 40], fill=(240, 236, 220), outline=(40, 40, 40))
    text(img, (32, 28), "DIESEL", 7, (30, 30, 30))
    text(img, (32, 36), "UN 1202", 5, (200, 40, 40))
    save(img, "grid_tank")


def warning():
    a = painted("#efe8d4", "warning", grime=0.10)
    img = to_img(a)
    d = ImageDraw.Draw(img)
    d.polygon([(32, 6), (60, 54), (4, 54)], fill=(20, 20, 20))
    d.polygon([(32, 12), (54, 50), (10, 50)], fill=(255, 205, 0))
    d.polygon([(34, 20), (25, 36), (31, 36), (27, 47), (39, 30), (33, 30), (37, 20)], fill=(20, 20, 20))
    text(img, (32, 60), "DANGER 750V", 6, (180, 20, 20))
    save(img, "grid_warning")


if __name__ == "__main__":
    battery(); blade(); cabinet(); cable(); copper(); display(); engine(); fins(); lamp_amber(); louvre()
    scada(); solar_cells(); switchgear(); tank(); warning()
    button("grid_btn_start", (40, 220, 70), "start")
    button("grid_btn_stop", (235, 40, 40), "stop")
    button("grid_btn_up", (60, 140, 235), "up")
    button("grid_btn_down", (60, 140, 235), "down")
    print("19 grid textures written at %dx%d" % (S, S))
