#!/usr/bin/env python3
"""Sirens + station-shop machine sounds for IR Extras (emergency services #6, station shops #5), synthesised
(numpy -> wav -> ogg via ffmpeg). Run: python3 tools/gen_sounds_ems.py
Writes assets/irextras/sounds/<id>.ogg and MERGES these plain ids into sounds.json (ems.EmsSounds registers them).
NOTE: tools/gen_sounds.py rewrites the plain ids from its own table - re-run this after it."""
import json, os, subprocess, tempfile, wave
import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/irextras')
OUT = os.path.join(ROOT, 'sounds')
rng = np.random.default_rng(66)


def t(sec): return np.arange(int(SR * sec)) / SR


def fade(x, a=0.01, r=0.05):
    n = len(x); e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na: e[:na] = np.linspace(0, 1, na)
    if nr: e[-nr:] *= np.linspace(1, 0, nr)
    return x * e


def sweep(f, sec):
    """a tone following frequency curve f(t) (Hz), with a horn-like spectrum (odd harmonics, a little buzz)"""
    x = t(sec)
    ph = 2 * np.pi * np.cumsum(f(x)) / SR
    return np.sin(ph) + 0.35 * np.sin(3 * ph) + 0.18 * np.sin(5 * ph) + 0.08 * np.sign(np.sin(ph))


def siren_wail(sec=2.0):          # fire: slow rise and fall
    return fade(sweep(lambda x: 650 + 650 * (0.5 - 0.5 * np.cos(2 * np.pi * x / sec)), sec), 0.02, 0.02)


def siren_hilo(sec=2.0):          # ambulance: two-tone hi-lo
    return fade(sweep(lambda x: np.where((x * 2 / sec) % 1 < 0.5, 960.0, 770.0), sec), 0.01, 0.01)


def siren_yelp(sec=2.0):          # police: fast yelp
    return fade(sweep(lambda x: 700 + 900 * ((x * 6) % 1), sec), 0.01, 0.01)


def air_horn(sec=1.1):
    x = t(sec)
    s = sum(np.sign(np.sin(2 * np.pi * f * x)) * w for f, w in ((233, 0.5), (277, 0.35), (349, 0.25)))
    return fade(s * (1 - np.exp(-x / 0.03)), 0.01, 0.12)


def turnout(sec=2.4):             # station turnout tones: rising 3-tone + bell clangs
    out = np.zeros(int(SR * sec))
    for i, f in enumerate((880, 1100, 1320)):
        x = t(0.35); seg = np.sin(2 * np.pi * f * x) * np.exp(-x / 0.25)
        s = int(i * 0.4 * SR); out[s:s + len(seg)] += seg
    x = t(1.0)
    bell = (np.sin(2 * np.pi * 1500 * x) + 0.5 * np.sin(2 * np.pi * 3020 * x)) * np.exp(-x / 0.3)
    for k in range(3):
        s = int((1.25 + k * 0.35) * SR); n = min(len(bell), len(out) - s); out[s:s + n] += bell[:n] * 0.6
    return fade(out, 0.005, 0.1)


def grind(sec=1.6):
    x = t(sec)
    n = rng.standard_normal(len(x))
    motor = np.sin(2 * np.pi * 180 * x) * 0.3 + np.sin(2 * np.pi * 360 * x) * 0.15
    crunch = n * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 23 * x)))
    lp = np.convolve(crunch, np.ones(6) / 6, 'same')
    return fade((motor + lp * 0.7) * 0.8, 0.05, 0.2)


def steam(sec=1.4):
    n = rng.standard_normal(int(SR * sec))
    hp = n - np.convolve(n, np.ones(3) / 3, 'same')
    gurgle = np.sin(2 * np.pi * 90 * t(sec)) * (rng.random(len(n)) < 0.02)
    return fade(hp * 0.9 + np.convolve(gurgle, np.ones(200) / 20, 'same'), 0.08, 0.3)


def pour(sec=1.2):
    x = t(sec)
    n = rng.standard_normal(len(x))
    lp = np.convolve(n, np.ones(25) / 25, 'same')
    pump = np.sin(2 * np.pi * 50 * x) * 0.25
    return fade(lp * 2.5 + pump, 0.05, 0.25)


def press(sec=1.0):               # little offset press: clack-clack + roller hiss
    out = np.zeros(int(SR * sec))
    x = t(0.05)
    clack = rng.standard_normal(len(x)) * np.exp(-x / 0.01)
    for k in range(4):
        s = int(k * 0.25 * SR); out[s:s + len(clack)] += clack * (1.0 if k % 2 == 0 else 0.6)
    n = rng.standard_normal(len(out))
    out += np.convolve(n, np.ones(40) / 40, 'same') * 0.8
    return fade(out, 0.005, 0.05)


SOUNDS = {'ems_siren_fire': siren_wail, 'ems_siren_ambulance': siren_hilo, 'ems_siren_police': siren_yelp, 'ems_air_horn': air_horn,
          'ems_turnout': turnout, 'coffee_grind': grind, 'coffee_steam': steam, 'coffee_pour': pour, 'news_press': press}


def write(name, x):
    x = x / max(1e-6, np.max(np.abs(x))) * 0.85
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        with wave.open(tmp.name, 'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp.name, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(OUT, name + '.ogg')], check=True)
        os.unlink(tmp.name)


if __name__ == '__main__':
    for n, fn in SOUNDS.items():
        write(n, fn())
    p = os.path.join(ROOT, 'sounds.json')
    sj = json.load(open(p))
    for n in SOUNDS:
        cat = 'neutral' if n.startswith('ems_siren') or n == 'ems_air_horn' else 'block'
        sj[n] = {'category': cat, 'subtitle': 'irextras.sub.' + n, 'sounds': [{'name': 'irextras:' + n, 'stream': False, **({'attenuation_distance': 48} if n.startswith('ems_') else {})}]}
    json.dump(sj, open(p, 'w'), indent=1)
    print('wrote', len(SOUNDS), 'sounds; sounds.json now', len(sj), 'events')
