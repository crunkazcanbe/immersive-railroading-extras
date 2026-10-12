#!/usr/bin/env python3
"""IR Extras realistic sound pack (her ask 2026-10-10: "a full, realistic sound pack").

Every sound is synthesised here from a physical model (struck modes, contact bounce, magnetostriction, motor
slot harmonics, turbulent air, bell partials ...) - no recordings, so no licence questions. Each event gets
3-6 variations (different random strikes, tolerances, speeds) so nothing repeats.

  uv run --with numpy --with scipy python3 tools/gen_soundpack.py [only-this-prefix]

Writes  assets/irextras/sounds/pack/*.ogg            (mono 44.1 kHz Vorbis)
        assets/irextras/soundpack.txt                (event ids: sound/PackSounds registers them)
        sounds.json                                  (merges: dotted ids are ours, plain ids belong to gen_sounds.py,
                                                      voice.* comes from tools/sound_fragments/voice.json)
        tools/sound_fragments/subtitles.lang         (lines for en_us.lang)
"""
import json, os, subprocess, sys, tempfile, wave, zlib
import numpy as np
from scipy import signal as sg

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src/main/resources/assets/irextras')
OUT = os.path.join(ROOT, 'sounds', 'pack')
FRAG = os.path.join(HERE, 'sound_fragments')


# ================================================================================================ primitives
def T(sec):
    return np.arange(int(SR * sec)) / SR


def N(r, sec):
    return r.standard_normal(int(SR * sec))


def lp(x, f, o=2):
    b, a = sg.butter(o, min(f, SR / 2 - 100) / (SR / 2), 'low')
    return sg.lfilter(b, a, x)


def hp(x, f, o=2):
    b, a = sg.butter(o, f / (SR / 2), 'high')
    return sg.lfilter(b, a, x)


def bp(x, lo, hi, o=2):
    b, a = sg.butter(o, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], 'band')
    return sg.lfilter(b, a, x)


def reson(x, f, q):
    """2-pole resonator (a body mode excited by x)"""
    w = 2 * np.pi * f / SR
    rr = np.exp(-w / (2 * q))
    return sg.lfilter([1 - rr], [1, -2 * rr * np.cos(w), rr * rr], x)


def env_ad(n, a, d):
    e = np.exp(-np.arange(n) / max(1, d * SR))
    na = int(a * SR)
    if na > 0:
        e[:na] *= np.linspace(0, 1, na)
    return e


def fade(x, a=0.005, r=0.02):
    x = x.copy()
    na, nr = int(a * SR), int(r * SR)
    if na:
        x[:na] *= np.linspace(0, 1, na)
    if nr:
        x[-nr:] *= np.linspace(1, 0, nr)
    return x


class Sig(np.ndarray):
    """1-D signal whose + pads the shorter operand (a click plus a longer ring)"""
    def __add__(self, o):
        if isinstance(o, np.ndarray) and o.ndim == 1 and o.shape != self.shape:
            a = np.zeros(max(len(self), len(o)))
            a[:len(self)] += self
            a[:len(o)] += o
            return a.view(Sig)
        return np.ndarray.__add__(self, o)
    __radd__ = __add__


def modes(r, sec, freqs, decays, amps=None, jit=0.01):
    """struck object: decaying sinusoidal modes (each a little detuned per variation)"""
    x = T(sec)
    y = np.zeros_like(x)
    amps = amps or [1.0] * len(freqs)
    for f, d, a in zip(freqs, decays, amps):
        f *= 1 + r.normal(0, jit)
        y += a * np.sin(2 * np.pi * f * x + r.uniform(0, 6.28)) * np.exp(-x / d)
    return y.view(Sig)


def impulse(r, sec, bright=4000, d=0.002):
    """the contact itself: a short filtered noise burst"""
    x = T(sec)
    return (lp(N(r, sec), bright) * np.exp(-x / d)).view(Sig)


def put(buf, sig, at):
    i = int(at * SR)
    if i >= len(buf):
        return buf
    k = min(len(sig), len(buf) - i)
    buf[i:i + k] += sig[:k]
    return buf


def Z(sec):
    return np.zeros(int(SR * sec))


def osc(freq_curve, harmonics=((1, 1.0),), phase=0.0):
    """oscillator following a per-sample frequency curve"""
    ph = 2 * np.pi * np.cumsum(freq_curve) / SR + phase
    return sum(a * np.sin(h * ph) for h, a in harmonics)


def saw_bl(freq_curve, nh=24):
    """band-limited sawtooth (reed / horn source)"""
    ph = 2 * np.pi * np.cumsum(freq_curve) / SR
    f0 = float(np.max(freq_curve))
    y = np.zeros_like(ph)
    for h in range(1, nh + 1):
        if h * f0 > SR / 2 - 500:
            break
        y += np.sin(h * ph) / h
    return y


def slow_noise(r, n, rate, smooth=2):
    """smooth random curve (gusts, wander) around 0, about +-1"""
    k = max(2, int(n / SR * rate) + 2)
    pts = r.standard_normal(k)
    y = np.interp(np.linspace(0, k - 1, n), np.arange(k), pts)
    return lp(y, max(0.3, rate), smooth) if rate > 0.6 else y


def loop(y, xf=0.5):
    """seamless loop: crossfade the tail into the head (equal power)"""
    n = int(xf * SR)
    head, tail = y[:n].copy(), y[-n:]
    w = np.linspace(0, np.pi / 2, n)
    y = y[:-n].copy()
    y[:n] = head * np.sin(w) + tail * np.cos(w)
    return y


def norm(y, peak=0.9):
    m = np.max(np.abs(y)) or 1.0
    return y / m * peak


def sat(y, drive=1.5):
    return np.tanh(drive * norm(y, 1.0)) / np.tanh(drive)


def room(r, y, size=0.04, mix=0.15, decay=0.25):
    """small-room reflections (control rooms, signal boxes): a few comb taps + diffuse tail"""
    out = y.copy()
    for k in range(6):
        d = int(SR * size * (1 + 0.6 * k + r.uniform(0, 0.3)))
        g = mix * (0.7 ** k)
        out[d:] += g * y[:-d] if d < len(y) else 0
    tail = lp(N(r, len(y) / SR), 3000) * np.exp(-np.arange(len(y)) / (decay * SR)) * 0.002
    return out + sg.fftconvolve(y, tail)[:len(y)] * mix


def j(r, v, pct):
    return v * (1 + r.uniform(-pct, pct))


# ================================================================================================ sound models
# ---- switchgear / relays
def relay_click(r):
    """small signalling relay: armature hits pole (click), contacts bounce, frame rings briefly"""
    buf = Z(0.12)
    put(buf, impulse(r, 0.01, j(r, 6000, .2), 0.0008), 0)
    for k in range(r.integers(2, 5)):                              # contact bounce
        put(buf, 0.4 * 0.5 ** k * impulse(r, 0.004, 8000, 0.0003), 0.0015 + k * j(r, 0.0012, .4))
    put(buf, 0.25 * modes(r, 0.06, [j(r, 2900, .1), j(r, 5100, .1), 7400], [0.008, 0.005, 0.003]), 0)
    return buf


def relay_clunk(r):
    """big auxiliary / lockout relay: heavier armature, two-stage clack"""
    buf = Z(0.25)
    put(buf, 0.9 * impulse(r, 0.02, 3500, 0.002), 0)
    put(buf, 0.6 * modes(r, 0.15, [j(r, 950, .1), j(r, 2300, .1), 4100], [0.03, 0.015, 0.008]), 0)
    put(buf, 0.5 * impulse(r, 0.01, 6000, 0.001), j(r, 0.018, .3))
    return buf


def contactor(r, close=True):
    """3-phase contactor: coil pull-in, magnet face slam, short AC chatter of the shading ring"""
    buf = Z(0.45)
    put(buf, impulse(r, 0.03, 2500, 0.004), 0)
    put(buf, 0.8 * modes(r, 0.2, [j(r, 420, .1), j(r, 1300, .1), 2900], [0.04, 0.02, 0.01]), 0)
    if close:
        x = T(0.25)
        put(buf, 0.06 * np.sin(2 * np.pi * 100 * x) * np.exp(-x / 0.08) * lp(N(r, 0.25), 2000), 0.01)
    else:
        put(buf, 0.4 * impulse(r, 0.01, 5000, 0.001), 0.012)          # arc chute snap on opening
    return buf


def breaker(r, trip=False):
    """MV vacuum / SF6 breaker mechanism: spring release, linkage rattle, contacts slam, damper thud
    + (trip) arc crack as it breaks load current"""
    sec = 1.2
    buf = Z(sec)
    put(buf, 0.6 * impulse(r, 0.02, 3000, 0.003), 0)                    # latch release
    put(buf, 1.0 * modes(r, 0.6, [j(r, 85, .1), j(r, 240, .1), j(r, 610, .1), 1450], [0.12, 0.08, 0.05, 0.02],
                         [1, 0.6, 0.4, 0.2]), j(r, 0.025, .3))          # mechanism slam
    put(buf, 0.7 * lp(N(r, 0.08), 600) * env_ad(int(SR * 0.08), 0.001, 0.02), 0.03)
    for k in range(r.integers(3, 7)):                                   # linkage rattle
        put(buf, 0.15 * impulse(r, 0.01, 5000, 0.001), 0.05 + r.uniform(0, 0.12))
    put(buf, 0.5 * modes(r, 0.5, [j(r, 55, .1)], [0.15]), 0.09)        # dashpot / damper
    if trip:
        cr = lp(N(r, 0.15), 9000) * (r.random(int(SR * 0.15)) > 0.6) * np.exp(-T(0.15) / 0.03)
        put(buf, 1.2 * cr, 0.028)
        x = T(0.12)
        put(buf, 0.3 * np.sin(2 * np.pi * 100 * x) * np.exp(-x / 0.03), 0.028)
    return buf


def spring_charge(r):
    """breaker spring charging motor after a close (whirr then latch)"""
    sec = j(r, 2.2, .15)
    buf = Z(sec + 0.4)
    x = T(sec)
    f = 140 * np.minimum(1, x / 0.3) * (1 - 0.05 * x / sec)
    y = 0.4 * osc(f, ((1, 1), (2, .5), (6, .2))) + 0.15 * reson(N(r, sec), j(r, 900, .1), 8)
    put(buf, fade(y * np.minimum(1, x / 0.2), 0.01, 0.05), 0)
    put(buf, 0.8 * impulse(r, 0.02, 3000, 0.003) + 0.5 * modes(r, 0.02, [1200], [0.01]), sec)
    return buf


def arc_crack(r):
    """open-air arc: violent crack, sizzling plasma at 100 Hz, decay"""
    sec = j(r, 1.6, .3)
    buf = Z(sec)
    put(buf, modes(r, 0.4, [j(r, 45, .1), 120], [0.1, 0.05]), 0)
    x = T(sec)
    cr = N(r, sec) * (r.random(len(x)) > j(r, 0.93, .03)) * 4
    plasma = (hp(cr, 1200) + 0.7 * lp(N(r, sec), 3000)) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 50 * x)))
    put(buf, plasma * env_ad(len(x), 0.001, j(r, 0.35, .3)), 0)
    return sat(buf, 2.0)


def tap_change(r):
    """on-load tap changer: motor drive whirr, diverter switch snap, oil-damped thump"""
    buf = Z(2.4)
    x = T(1.6)
    put(buf, fade(0.3 * osc(np.full(len(x), j(r, 95, .1)), ((1, 1), (3, .3), (7, .1))) + 0.1 * lp(N(r, 1.6), 1200), 0.05, 0.1), 0)
    put(buf, impulse(r, 0.02, 4000, 0.002), 1.6)
    put(buf, 0.8 * modes(r, 0.6, [j(r, 70, .1), 210], [0.15, 0.06]), 1.61)
    return buf


# ---- hum / electrical machines (loops)
def hum(r, sec, line=50.0, load=0.5, rect=False):
    """transformer: magnetostriction at 2x line frequency + even harmonics; load adds winding buzz and
    higher harmonics; cooling oil pumps / fans faintly; rect = 12-pulse rectifier ripple whine on top"""
    x = T(sec)
    wob = 1 + 0.08 * slow_noise(r, len(x), 0.3)
    y = np.zeros_like(x)
    for h, a in ((2, 1.0), (4, 0.55 + 0.3 * load), (6, 0.3 + 0.35 * load), (8, 0.12 + 0.3 * load),
                 (10, 0.05 + 0.2 * load), (12, 0.03 + 0.15 * load)):
        y += a * np.sin(2 * np.pi * line * h * x + r.uniform(0, 6)) / (1 + 0.1 * h)
    y *= wob
    y += (0.05 + 0.15 * load) * bp(N(r, sec), 200, 2500) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * line * x)))
    y += 0.06 * lp(N(r, sec), 400)                                          # radiator / fan air
    if rect:
        y += 0.25 * np.sin(2 * np.pi * line * 12 * x) + 0.12 * np.sin(2 * np.pi * line * 24 * x)
    return y


def motor_run(r, sec, rpm=1480, poles=4, slots=36, load=0.5):
    """induction motor: slot-pass whine, 2x line magnetic hum, bearing hiss, fan air"""
    x = T(sec)
    fr = rpm / 60.0 * (1 + 0.002 * slow_noise(r, len(x), 0.5))
    y = 0.35 * np.sin(2 * np.pi * slots * np.cumsum(fr) / SR)                 # slot harmonic
    y += 0.25 * np.sin(2 * np.pi * 100 * x) + 0.1 * np.sin(2 * np.pi * 200 * x)
    y += 0.08 * osc(fr, ((1, 1), (2, .5)))                                     # unbalance
    y += 0.2 * bp(N(r, sec), 1500, 6000) * (0.4 + load) * 0.5
    y += 0.3 * lp(N(r, sec), 600)
    return y


def motor_spin(r, up=True):
    """motor start / run-down: rising (falling) slot whine and hum"""
    sec = j(r, 4.0, .2)
    x = T(sec)
    k = np.clip(x / (sec * 0.7), 0, 1)
    sp = (1 - np.exp(-4 * k)) if up else np.exp(-3.5 * x / sec)
    fr = 24.6 * sp + 0.01
    y = 0.4 * np.sin(2 * np.pi * 36 * np.cumsum(fr) / SR) + 0.3 * osc(fr * 2, ((1, 1), (2, .3)))
    y += 0.25 * lp(N(r, sec), 500) * sp + 0.1 * bp(N(r, sec), 2000, 7000) * sp
    if up:
        y += 0.2 * np.sin(2 * np.pi * 100 * x) * np.exp(-x / 0.6)            # inrush growl
        put(y, impulse(r, 0.02, 3000, 0.003), 0)
    return fade(y, 0.01, 0.2)


def fan(r, sec, blades=7, rps=24, big=False):
    """axial fan: blade-pass tone + broadband turbulence, slow flow wander"""
    x = T(sec)
    fr = rps * (1 + 0.01 * slow_noise(r, len(x), 0.4))
    bpf = blades * fr
    y = 0.3 * osc(bpf, ((1, 1), (2, .4), (3, .15)))
    y += (1.0 if big else 0.6) * lp(N(r, sec), 2500 if big else 4000) * (1 + 0.15 * slow_noise(r, len(x), 1.5))
    y += 0.25 * lp(N(r, sec), 200)
    return y


def pump(r, sec):
    """centrifugal pump: motor hum, impeller blade-pass, fluid swirl and cavitation ticks"""
    x = T(sec)
    y = 0.6 * motor_run(r, sec, rpm=2950, slots=28)
    y += 0.3 * np.sin(2 * np.pi * 49 * 6 * x) * (1 + 0.2 * slow_noise(r, len(x), 2))
    y += 0.4 * bp(N(r, sec), 150, 900) * (1 + 0.3 * slow_noise(r, len(x), 3))
    cav = N(r, sec) * (r.random(len(x)) > 0.9995) * 0.5
    y += hp(cav, 2000)
    return y


def compressor(r, sec, rpm=None):
    """reciprocating air compressor: piston thump per rev, valve clatter, intake hiss"""
    rpm = rpm or j(r, 780, .1)
    x = T(sec)
    fr = rpm / 60
    y = Z(sec)
    t0 = 0
    while t0 < sec:
        put(y, 0.8 * modes(r, 0.15, [j(r, 55, .05), 130, 340], [0.05, 0.03, 0.015]), t0)
        put(y, 0.2 * impulse(r, 0.01, 6000, 0.001), t0 + 0.4 / fr)
        t0 += 1 / fr
    y += 0.25 * bp(N(r, sec), 1500, 7000) * (0.5 + 0.5 * np.abs(np.sin(np.pi * fr * x)))
    y += 0.2 * motor_run(r, sec, rpm=1450, slots=36)
    return y


# ---- point machines, signal box
def point_machine(r, kind='electric'):
    """point machine throw: contactor in, motor runs up and drives through the gearbox (whine), switch blades
    scrape, clutch slips at the end, lock drops (thunk), detection contacts make (click)"""
    sec = {'electric': 4.0, 'hydraulic': 3.0, 'air': 1.5}[kind] * j(r, 1.0, .04)
    buf = Z(sec + 0.2)
    put(buf, 0.5 * contactor(r), 0)
    run = sec - 0.6
    x = T(run)
    if kind == 'electric':
        fr = 22 * (1 - np.exp(-x / 0.25)) * (1 - 0.15 * (x > run * 0.75))
        y = 0.35 * np.sin(2 * np.pi * 30 * np.cumsum(fr) / SR) + 0.3 * osc(fr * 2, ((1, 1), (3, .3)))
        y += 0.25 * np.sin(2 * np.pi * 17 * np.cumsum(fr) / SR) * (1 + 0.3 * slow_noise(r, len(x), 4))   # gear mesh
        y += 0.2 * bp(N(r, run), 300, 2500) * (0.5 + 0.5 * slow_noise(r, len(x), 2) ** 2)              # blade scrape
    elif kind == 'hydraulic':                                                                          # pump + ram
        y = 0.45 * pump(r, run) + 0.25 * osc(np.full(len(x), j(r, 380, .1)), ((1, 1), (2, .4))) + 0.15 * bp(N(r, run), 300, 2500)
    else:                                                                                              # pneumatic cylinder
        y = 0.6 * bp(N(r, run), 600, 6000) * np.exp(-x / (run * 0.8)) + 0.2 * bp(N(r, run), 200, 1200)
    put(buf, fade(y, 0.05, 0.08), 0.08)
    put(buf, 0.3 * bp(N(r, 0.25), 200, 900) * np.exp(-T(0.25) / 0.08), 0.08 + run * 0.92)            # clutch slip
    return buf


def points_lock(r):
    """end of stroke: lock dog drops into the lock slide (thunk), detection contacts make (click)"""
    buf = Z(0.6)
    put(buf, 1.0 * modes(r, 0.4, [j(r, 75, .1), j(r, 260, .1), j(r, 1100, .1)], [0.08, 0.05, 0.02]), 0)
    put(buf, 0.4 * relay_click(r), j(r, 0.12, .3))
    return buf


def points_crank(r):
    """hand crank in the point machine: ratchet turning the gear train, blade scraping"""
    buf = Z(1.2)
    t0 = 0
    while t0 < 0.9:
        put(buf, 0.4 * impulse(r, 0.008, 4000, 0.001) + 0.2 * modes(r, 0.05, [j(r, 1500, .2)], [0.01]), t0)
        t0 += j(r, 0.07, .2)
    put(buf, 0.25 * bp(N(r, 1.0), 300, 2500) * np.sin(np.pi * T(1.0)), 0.05)
    return buf


def lever_pull(r, reverse=True):
    """mechanical signal box lever: squeeze the catch handle (latch lifts), heave the lever through the
    quadrant (locking tappets slide, rodding / wire groans), it hits the stop, catch drops in"""
    sec = 1.6
    buf = Z(sec)
    put(buf, 0.6 * impulse(r, 0.015, 3500, 0.002) + 0.3 * modes(r, 0.08, [j(r, 1900, .1)], [0.02]), 0)
    mv = j(r, 0.55, .2)
    x = T(mv)
    y = 0.35 * bp(N(r, mv), 120, 1200) * np.sin(np.pi * x / mv) ** 0.7
    y += 0.12 * reson(N(r, mv), j(r, 380, .2), 15) * np.sin(np.pi * x / mv)       # wire / rod groan
    put(buf, y, 0.06)
    put(buf, modes(r, 0.6, [j(r, 160, .1), j(r, 410, .1), j(r, 920, .1), j(r, 1700, .1)], [0.2, 0.12, 0.07, 0.04],
                   [1, .7, .45, .3]), 0.06 + mv)                              # frame clank
    put(buf, 0.7 * lp(N(r, 0.05), 800) * env_ad(int(SR * 0.05), 0.001, 0.012), 0.06 + mv)
    put(buf, 0.5 * impulse(r, 0.01, 5000, 0.001) + 0.3 * modes(r, 0.1, [j(r, 2400, .1)], [0.02]), 0.12 + mv)
    return room(r, buf, 0.03, 0.12)


def frame_clank(r):
    """locking frame below the lever floor: tappets and locks shifting"""
    buf = Z(0.8)
    for k in range(r.integers(2, 5)):
        put(buf, modes(r, 0.4, [j(r, 220, .2), j(r, 700, .2), j(r, 1500, .2)], [0.1, 0.05, 0.03]) * r.uniform(.4, 1),
            r.uniform(0, 0.25))
    return room(r, buf, 0.03, 0.15)


def block_bell(r, f=None):
    """block instrument bell: a small domed gong struck once by the electromagnet hammer"""
    f = f or j(r, 1380, .03)
    buf = Z(1.8)
    put(buf, 0.3 * impulse(r, 0.006, 7000, 0.0008), 0)
    put(buf, modes(r, 1.8, [f, f * 2.32, f * 4.25, f * 6.63], [0.6, 0.25, 0.1, 0.05], [1, .5, .25, .12], 0.004), 0)
    return buf


BELL_CODES = {   # Rule-book block bell codes (UK absolute block) rung by TileSignalBox's commutators
    'is_line_clear': '3-1', 'train_entering': '2', 'train_out': '2-1',
}


def bell_code(r, code):
    """beat a code on the block bell: beats within a group ~0.25 s apart, groups ~0.9 s apart (human rhythm)"""
    groups = [int(g) for g in code.split('-')]
    total = sum(g * 0.25 for g in groups) + 0.9 * len(groups) + 2
    buf = Z(total)
    t0, f = 0.0, j(r, 1380, .02)
    for g in groups:
        for k in range(g):
            put(buf, block_bell(r, f) * r.uniform(0.85, 1.0), t0)
            t0 += j(r, 0.24, .12)
        t0 += j(r, 0.65, .15)
    return room(r, buf, 0.035, 0.12)


def instrument_needle(r):
    """block instrument needle / commutator turned: detent clicks"""
    buf = Z(0.3)
    for k in range(2):
        put(buf, 0.6 * impulse(r, 0.008, 5000, 0.001) + 0.3 * modes(r, 0.05, [j(r, 2100, .1)], [0.01]), k * j(r, 0.05, .3))
    return buf


# ---- level crossings
def crossing_gong(r):
    """mechanical crossing bell (US gong): electromagnetic hammer on a steel dome, one stroke"""
    f = j(r, 820, .03)
    buf = Z(1.2)
    put(buf, 0.4 * impulse(r, 0.006, 6000, 0.0008), 0)
    put(buf, modes(r, 1.2, [f, f * 2.32, f * 4.25, f * 6.63, f * 9.38], [0.45, 0.2, 0.09, 0.05, 0.03],
                   [1, .6, .35, .2, .1], 0.003), 0)
    return buf


def crossing_ebell(r):
    """electronic crossing bell (US e-bell): synthesised ding-ding at ~2 strokes/s, 4 s"""
    buf = Z(4.0)
    f = j(r, 1050, .02)
    t0 = 0
    while t0 < 3.8:
        x = T(0.35)
        put(buf, (np.sin(2 * np.pi * f * x) + 0.3 * np.sin(2 * np.pi * f * 2.7 * x)) * np.exp(-x / 0.1), t0)
        t0 += j(r, 0.5, .02)
    return lp(buf, 6000)


def crossing_yodel(r):
    """UK AHB / MCB 'yodel' alarm: two-tone warble from a piezo sounder"""
    sec = 4.0
    x = T(sec)
    rate = j(r, 3.0, .1)
    f = np.where(np.sin(2 * np.pi * rate * x) > 0, j(r, 1100, .03), j(r, 760, .03))
    f = lp(f, 60, 1)
    y = np.sign(osc(f)) * 0.5 + 0.5 * osc(f, ((1, 1), (3, .3)))
    return reson(lp(y, 5000), j(r, 2600, .05), 2) * 0.5 + 0.5 * lp(y, 5000)


def crossing_euro(r):
    """continental electronic ding (DB / SNCF style): fast clean bell tone"""
    sec = 4.0
    buf = Z(sec)
    f = j(r, 1650, .03)
    t0 = 0
    while t0 < sec - 0.3:
        x = T(0.3)
        put(buf, np.sin(2 * np.pi * f * x) * np.exp(-x / 0.07), t0)
        t0 += j(r, 0.3, .03)
    return buf


def barrier(r, down=True):
    """barrier machine: gear motor drives the boom; hydraulic buffer at the end; boom rattles"""
    sec = j(r, 3.2, .1)
    buf = Z(sec + 0.6)
    x = T(sec)
    fr = 18 * (1 - np.exp(-x / 0.3)) * (0.9 if down else 1.0)
    y = 0.3 * np.sin(2 * np.pi * 24 * np.cumsum(fr) / SR) + 0.2 * osc(fr * 2) + 0.15 * bp(N(r, sec), 400, 2500)
    put(buf, fade(y, 0.05, 0.4), 0)
    put(buf, 0.3 * contactor(r), 0)
    put(buf, 0.6 * modes(r, 0.5, [j(r, 140, .1), j(r, 520, .1), 1300], [0.12, 0.05, 0.03]), sec)
    for k in range(4):
        put(buf, 0.12 * impulse(r, 0.01, 4000, 0.002), sec + 0.05 + r.uniform(0, 0.3))
    return buf


# ---- alarms / control room
def annunciator(r, f0=None):
    """annunciator / control-room alarm horn: electronic horn, square-ish tone through a horn resonance"""
    sec = 1.6
    f0 = f0 or j(r, 660, .015)
    x = T(sec)
    pat = 0
    if pat == 0:
        g = (np.sin(2 * np.pi * 1.25 * x) > -0.2).astype(float)                  # pulsing
    elif pat == 1:
        g = np.ones_like(x)                                                         # steady
    else:
        g = ((x * 4) % 1 < 0.5).astype(float)                                       # fast pulse
    y = saw_bl(np.full(len(x), f0), 12) * lp(g, 200, 1)
    y = 0.6 * reson(y, f0 * 3, 3) + 0.4 * y
    return fade(lp(y, 6000), 0.005, 0.03)


def klaxon(r):
    """motor klaxon (rotor scraping a diaphragm): rough rising then sustained AWOOGA-ish blast"""
    sec = j(r, 1.8, .15)
    x = T(sec)
    f = j(r, 280, .1) * (0.6 + 0.4 * np.minimum(1, x / 0.35)) * (1 - 0.15 * np.maximum(0, x - sec * 0.7) / sec)
    y = saw_bl(f, 30) * (1 + 0.6 * np.sign(np.sin(2 * np.pi * f * 3 * x)))
    y = reson(y, 900, 2) + 0.5 * y
    return fade(sat(lp(y, 5000), 2.5), 0.02, 0.15)


def fire_bell(r):
    """vibrating fire-alarm bell: striker at ~20 Hz on a 6 inch gong"""
    sec = 3.0
    buf = Z(sec)
    f = j(r, 1150, .04)
    t0, rate = 0.0, j(r, 19, .1)
    while t0 < sec - 0.2:
        put(buf, modes(r, 0.3, [f, f * 2.32, f * 4.25], [0.15, 0.06, 0.03], [1, .5, .2], 0.002) * r.uniform(.7, 1), t0)
        t0 += 1 / rate
    return fade(buf, 0.003, 0.2)


def evac_tone(r):
    """evacuation sounder: slow whoop (rising sweep) BS 5839 / NFPA-style"""
    sec = 3.0
    x = T(sec)
    ph = (x % 1.0)
    f = j(r, 500, .05) + 700 * ph
    y = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * (ph < 0.85)
    return fade(lp(y, 4000) * 0.6, 0.005, 0.05)


def teleprinter(r):
    """teleprinter / event recorder: motor hum, typebar strikes ~10 char/s, carriage return + bell"""
    sec = j(r, 5.0, .2)
    buf = Z(sec)
    x = T(sec)
    put(buf, 0.12 * motor_run(r, sec, rpm=3000, slots=12), 0)
    t0 = 0.1
    col = 0
    while t0 < sec - 0.6:
        if r.random() < 0.12:                                                       # space
            t0 += j(r, 0.1, .1); col += 1; continue
        put(buf, 0.6 * impulse(r, 0.012, 5000, 0.0015) + 0.25 * modes(r, 0.04, [j(r, 1800, .2), j(r, 3500, .2)], [0.01, 0.006]), t0)
        put(buf, 0.15 * impulse(r, 0.01, 2500, 0.002), t0 + 0.02)                  # platen
        t0 += j(r, 0.1, .08); col += 1
        if col > r.integers(28, 48):
            put(buf, block_bell(r, 2600) * 0.15, t0)
            put(buf, 0.35 * bp(N(r, 0.35), 200, 2000) * np.sin(np.pi * T(0.35) / 0.35), t0 + 0.05)
            t0 += 0.45; col = 0
    return room(r, buf, 0.025, 0.1)


def chart_recorder(r):
    """chart recorder / printer: stepper chatter and paper advance"""
    sec = j(r, 2.0, .2)
    buf = Z(sec)
    t0 = 0
    while t0 < sec - 0.1:
        put(buf, 0.3 * impulse(r, 0.006, 4000, 0.001), t0)
        t0 += j(r, 0.025, .2)
    put(buf, 0.3 * bp(N(r, 0.4), 800, 5000) * np.exp(-T(0.4) / 0.15), sec - 0.45)
    return buf


def phone(r):
    """control-room telephone (electromechanical ringer, UK 400 + 450 Hz cadence)"""
    sec = 3.0
    buf = Z(sec)
    f = j(r, 22, .05)
    for at in (0, 0.6):
        x = T(0.4)
        ring = modes(r, 0.4, [j(r, 1200, .05), j(r, 1500, .05)], [0.1, 0.1]) * (np.sin(2 * np.pi * f * x) > 0)
        put(buf, ring, at)
    return buf


# ---- train / cab
def horn(r, chord, sec=None, slide=True):
    """air horn chimes: each chime is a reed (band-limited saw) whose pitch slides up as the air builds,
    shaped by the horn bell resonance; slight beating between chimes"""
    sec = sec or j(r, 1.8, .25)
    x = T(sec)
    y = np.zeros_like(x)
    for f0 in chord:
        f = f0 * (1 + r.normal(0, 0.004))
        fc = f * ((0.93 + 0.07 * np.minimum(1, x / 0.12)) if slide else 1)
        fc = fc * (1 + 0.003 * slow_noise(r, len(x), 5))
        reed = saw_bl(fc, 30)
        y += reson(reed, f * 2, 4) * 0.6 + reed * 0.4
    a = np.minimum(1, x / 0.06) * np.minimum(1, (sec - x) / 0.12)
    y = y * a + 0.04 * bp(N(r, sec), 2000, 8000) * a                                   # air rush
    return lp(sat(y, 1.3), 7000)


HORN_CHORDS = {'k5la': [311.1, 370.0, 415.3, 493.9, 622.3], 'p3': [370.0, 466.2, 554.4],
               'uk2tone': [370.0], 'k3la': [311.1, 370.0, 466.2], 'euro': [440.0, 554.4]}


def cab_horn(r):
    kind = r.choice(['k5la', 'p3', 'k3la', 'uk2tone', 'euro'])
    if kind == 'uk2tone':                                                               # high-low
        hi = horn(r, [j(r, 370, .02)], 0.7)
        lo = horn(r, [j(r, 311, .02)], 0.8)
        buf = Z(1.6)
        put(buf, hi, 0); put(buf, lo, 0.7)
        return buf
    return horn(r, HORN_CHORDS[kind])


def steam_whistle(r):
    """steam chime whistle: noise-excited narrow resonators (one per chamber), breathy onset, condensate gurgle"""
    sec = j(r, 2.2, .25)
    x = T(sec)
    base = j(r, 280, .1)
    chord = [base, base * 1.26, base * 1.5] if r.random() < 0.7 else [base]
    y = np.zeros_like(x)
    exc = N(r, sec)
    for f in chord:
        fc = f * (0.97 + 0.03 * np.minimum(1, x / 0.25))
        for h, a in ((1, 1), (2, .35), (3, .15)):
            y += a * reson(exc, float(np.mean(fc)) * h, 180) * 30
    a = np.minimum(1, x / 0.15) * np.minimum(1, (sec - x) / 0.2)
    y = y * a + 0.15 * bp(N(r, sec), 2000, 9000) * a
    return lp(y, 8000)


def loco_bell(r):
    """locomotive bell: cast bronze, ringer swings ~1 stroke / 0.75 s, 4 s"""
    sec = 4.0
    buf = Z(sec)
    f = j(r, 520, .04)
    t0 = 0
    ratios = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011]
    while t0 < sec - 0.5:
        put(buf, modes(r, 1.6, [f * k for k in ratios], [1.2, 0.9, 0.6, 0.5, 0.4, 0.25, 0.2, 0.15],
                       [.3, 1, .6, .5, .7, .3, .25, .2], 0.002) * r.uniform(.85, 1), t0)
        t0 += j(r, 0.75, .05)
    return fade(buf, 0.002, 0.3)


def brake_apply(r):
    """automatic air brake application: brake valve exhausts the train pipe (rushing hiss, falls as pressure
    drops), then cylinders fill"""
    sec = j(r, 2.5, .25)
    x = T(sec)
    e = np.minimum(1, x / 0.03) * np.exp(-x / j(r, 0.9, .2))
    y = bp(N(r, sec), j(r, 900, .2), j(r, 7000, .1)) * e * (1 + 0.2 * slow_noise(r, len(x), 12))
    y += 0.3 * reson(N(r, sec), j(r, 1800, .2), 6) * e
    put(y, 0.4 * modes(r, 0.1, [j(r, 600, .2)], [0.02]), 0)
    return fade(y, 0.002, 0.2)


def brake_release(r):
    """brake release: cylinder exhaust burst through the triple valve, then the pipe recharges (soft hiss)"""
    sec = j(r, 3.0, .2)
    x = T(sec)
    burst = bp(N(r, sec), j(r, 500, .2), 8000) * np.minimum(1, x / 0.01) * np.exp(-x / j(r, 0.45, .2))
    feed = 0.25 * bp(N(r, sec), 2500, 9000) * np.minimum(1, x / 0.6) * np.exp(-x / 2.0)
    y = burst + feed
    put(y, 0.5 * relay_clunk(r), 0)
    return fade(y, 0.002, 0.2)


def notch(r):
    """master controller notch: cam follower drops into the detent, cam contactors click below"""
    buf = Z(0.35)
    put(buf, 0.8 * impulse(r, 0.012, 3000, 0.002) + 0.4 * modes(r, 0.1, [j(r, 700, .15), j(r, 1700, .15)], [0.03, 0.015]), 0)
    if r.random() < 0.7:
        put(buf, 0.35 * relay_click(r), j(r, 0.09, .3))
    return buf


def cab_switch(r):
    """panel toggle / rotary switch in the cab"""
    buf = Z(0.15)
    put(buf, impulse(r, 0.008, j(r, 6000, .2), 0.0008) + 0.3 * modes(r, 0.05, [j(r, 3200, .2)], [0.008]), 0)
    put(buf, 0.3 * impulse(r, 0.004, 8000, 0.0004), j(r, 0.006, .3))
    return buf


def cab_radio(r):
    """cab radio (GSM-R / VHF): key-up chirp, short burst of band-limited static, squelch tail"""
    sec = j(r, 1.3, .2)
    buf = Z(sec)
    a, b = r.choice([(1200, 1600), (1000, 1400), (1600, 1200), (880, 1320)])
    put(buf, 0.4 * fade(np.sin(2 * np.pi * a * T(0.08)), 0.003, 0.01), 0)
    put(buf, 0.4 * fade(np.sin(2 * np.pi * b * T(0.1)), 0.003, 0.01), 0.1)
    st = bp(N(r, sec - 0.3), 300, 3200) * (0.5 + 0.5 * slow_noise(r, int(SR * (sec - 0.3)), 8) ** 2)
    put(buf, 0.35 * fade(st, 0.01, 0.06), 0.25)
    put(buf, 0.6 * bp(N(r, 0.12), 300, 3400) * np.exp(-T(0.12) / 0.05), sec - 0.15)     # squelch tail
    return buf


def brake_emergency(r):
    """emergency application: the brake valve dumps the whole train pipe - a violent roar that dies away"""
    sec = j(r, 4.0, .15)
    x = T(sec)
    y = bp(N(r, sec), 250, 8000) * np.minimum(1, x / 0.01) * np.exp(-x / j(r, 1.3, .2)) * 1.5
    y += 0.4 * reson(N(r, sec), j(r, 700, .2), 4) * np.exp(-x / 0.8)
    put(y, relay_clunk(r), 0)
    return sat(fade(y, 0.002, 0.3), 1.6)


def brake_handle(r):
    """driver's brake valve handle: detent notches as it swings, valve breathes"""
    buf = Z(0.7)
    for k in range(r.integers(1, 4)):
        put(buf, 0.6 * impulse(r, 0.01, 3500, 0.0015) + 0.3 * modes(r, 0.08, [j(r, 900, .15), j(r, 2100, .15)], [0.02, 0.01]), k * j(r, 0.12, .3))
    put(buf, 0.15 * bp(N(r, 0.4), 1500, 7000) * np.exp(-T(0.4) / 0.15), 0.05)
    return buf


def reverser(r):
    """reverser handle / drum: heavy detent clunk, interlock contacts"""
    buf = Z(0.6)
    put(buf, modes(r, 0.4, [j(r, 180, .1), j(r, 520, .1), 1400], [0.08, 0.04, 0.02], [1, .6, .3]) + 0.6 * impulse(r, 0.015, 3000, 0.002), 0)
    put(buf, 0.35 * relay_click(r), j(r, 0.08, .3))
    return buf


def aws_horn(r):
    """AWS warning horn (caution aspect): continuous electronic horn until acknowledged"""
    x = T(2.0)
    f = j(r, 820, .03)
    y = saw_bl(np.full(len(x), f), 10)
    return fade(lp(reson(y, f * 2, 3) + 0.5 * y, 5000), 0.005, 0.02)


def aws_bell(r):
    """AWS 'bell' (clear aspect): single electronic ding"""
    x = T(1.2)
    f = j(r, 1250, .02)
    return (np.sin(2 * np.pi * f * x) + 0.4 * np.sin(2 * np.pi * f * 2.76 * x) * np.exp(-x / 0.1)) * np.exp(-x / 0.4)


def vigilance(r):
    """driver's vigilance (DSD / DRA) buzzer"""
    x = T(1.5)
    y = np.sign(np.sin(2 * np.pi * j(r, 420, .1) * x)) * ((x * 3) % 1 < 0.6)
    return fade(lp(y, 3500) * 0.5, 0.003, 0.03)


def flange_squeal(r):
    """wheel flange squeal on a tight curve: stick-slip excites a wheel web mode (1-5 kHz) that wanders,
    switches between modes and pulses with the creep"""
    sec = j(r, 3.0, .3)
    x = T(sec)
    y = np.zeros_like(x)
    for k in range(r.integers(1, 3)):
        f = r.uniform(1300, 4500)
        fc = f * (1 + 0.012 * slow_noise(r, len(x), 3))
        g = np.clip(0.5 + slow_noise(r, len(x), r.uniform(1, 4)), 0, 1.4) ** 2
        y += osc(fc, ((1, 1), (2, .25), (3, .08))) * g
    y += 0.15 * bp(N(r, sec), 1000, 6000)
    return fade(y, 0.15, 0.4)


def rail_joint(r):
    """bogie passing a fishplated joint: each wheelset drops onto the next rail end (thump + rail ring)"""
    wb = j(r, 0.12, .25)                                                          # time between axles
    buf = Z(0.9)
    for at in (0, wb):
        put(buf, modes(r, 0.5, [j(r, 85, .15), j(r, 340, .15), j(r, 980, .15), 2300], [0.08, 0.06, 0.04, 0.02],
                       [1, .5, .35, .15]), at)
        put(buf, 0.5 * lp(N(r, 0.03), 900) * np.exp(-T(0.03) / 0.008), at)
    return buf


def wheel_rumble(r, sec):
    """rolling noise on jointed track (loop): roughness rumble + periodic joints"""
    x = T(sec)
    y = 0.5 * bp(N(r, sec), 60, 1200) * (1 + 0.2 * slow_noise(r, len(x), 2))
    return y


# ---- station
def pa_chime(r, kind):
    sec = 3.0
    buf = Z(sec)
    seqs = {'two_tone': [659.3, 523.3], 'dingdong': [784.0, 659.3, 523.3], 'airport': [523.3, 659.3, 784.0, 1046.5],
            'bell': [880.0], 'pling': [1318.5, 1760.0]}
    notes = seqs[kind]
    gap = {'two_tone': 0.55, 'dingdong': 0.5, 'airport': 0.32, 'bell': 0, 'pling': 0.12}[kind]
    for i, f in enumerate(notes):
        f = j(r, f, .004)
        if kind == 'bell':
            v = modes(r, 2.5, [f * k for k in (0.5, 1, 1.183, 1.506, 2.0, 2.514)], [1.5, 1.2, .8, .6, .5, .3],
                      [.3, 1, .5, .4, .6, .25], 0.002)
        else:                                                                       # vibraphone-like bar + PA horn colour
            v = modes(r, 1.8, [f, f * 4.0, f * 9.9], [j(r, 0.9, .1), 0.25, 0.08], [1, .3, .08], 0.001)
        put(buf, v * (1 if i < len(notes) - 1 else 0.9), i * gap)
    return lp(hp(buf, 200), 7000)


def wind(r, sec):
    """station wind (loop): gusting broadband with whistles around canopy edges"""
    x = T(sec)
    g = np.clip(0.55 + 0.45 * slow_noise(r, len(x), 0.25), 0.1, 1.5)
    y = lp(N(r, sec), 700) * g + 0.35 * bp(N(r, sec), 300, 2500) * g ** 2
    for k in range(2):
        f = r.uniform(500, 1400)
        y += 0.15 * reson(N(r, sec), f, 60) * 10 * np.clip(g - 0.8, 0, None)
    return y


def rain(r, sec, heavy=0.5):
    """rain on a station canopy (loop): steady wash + thousands of droplet ticks on sheeting"""
    x = T(sec)
    y = (0.4 + 0.4 * heavy) * bp(N(r, sec), 400, 9000)
    drops = (r.random(len(x)) > (0.9985 - 0.002 * heavy)) * r.uniform(0.2, 1, len(x))
    y += 2.0 * reson(drops, r.uniform(2500, 4500), 6) + 1.2 * reson(drops, r.uniform(700, 1200), 4)
    gutter = (r.random(len(x)) > 0.99995) * 1.0
    y += 1.5 * reson(gutter, 450, 20)
    return y


def gate(r, open_=True):
    """ticket gate paddles: solenoid, motor swing, stop bump"""
    buf = Z(1.0)
    put(buf, 0.5 * relay_clunk(r), 0)
    x = T(0.45)
    put(buf, fade(0.3 * osc(np.full(len(x), j(r, 160, .1)), ((1, 1), (2, .5), (5, .2))) + 0.1 * bp(N(r, .45), 500, 3000), 0.03, 0.05), 0.04)
    put(buf, 0.6 * modes(r, 0.25, [j(r, 300, .1), j(r, 900, .1)], [0.04, 0.02]), 0.5)
    return buf


def lift_ding(r):
    x = T(1.5)
    f = j(r, 1046, .01)
    return (np.sin(2 * np.pi * f * x) + .3 * np.sin(2 * np.pi * f * 3 * x) * np.exp(-x / .1)) * np.exp(-x / 0.45)


def lift_run(r):
    """lift car running: traction motor, rope sheave hum, landing door slide"""
    sec = 3.0
    buf = Z(sec)
    put(buf, 0.5 * fade(motor_run(r, 2.0, rpm=1000, slots=48), 0.4, 0.5), 0)
    x = T(0.9)
    put(buf, fade(0.3 * osc(np.full(len(x), 110), ((1, 1), (3, .3))) + 0.2 * bp(N(r, 0.9), 200, 1500), 0.05, 0.1), 2.0)
    put(buf, 0.5 * modes(r, 0.3, [j(r, 180, .1), 700], [0.05, 0.02]), 2.9)
    return buf


def flush(r):
    """toilet flush: valve clunk, water rush, refill trickle"""
    sec = j(r, 4.0, .1)
    x = T(sec)
    y = 0.8 * bp(N(r, sec), 300, 4000) * np.minimum(1, x / 0.1) * np.exp(-x / 1.2)
    bub = (r.random(len(x)) > 0.999) * 1.0
    y += 0.8 * reson(bub, r.uniform(300, 600), 15) * np.exp(-x / 2.5)
    y += 0.12 * bp(N(r, sec), 2000, 6000) * np.minimum(1, x / 2.0)
    put(y, relay_clunk(r) * 0.6, 0)
    return fade(y, 0.005, 0.5)


def tap_water(r):
    sec = j(r, 1.8, .15)
    x = T(sec)
    y = bp(N(r, sec), 800, 6000) * np.minimum(1, x / 0.08) * np.minimum(1, (sec - x) / 0.2)
    bub = (r.random(len(x)) > 0.998) * 1.0
    return y + 0.7 * reson(bub, r.uniform(500, 900), 12)


def vend(r):
    """vending machine: spiral motor, product drop into the tray"""
    buf = Z(2.2)
    x = T(1.3)
    put(buf, fade(0.3 * osc(np.full(len(x), j(r, 90, .1)), ((1, 1), (2, .5), (4, .2))) + 0.08 * bp(N(r, 1.3), 300, 2000), 0.05, 0.1), 0)
    put(buf, modes(r, 0.5, [j(r, 220, .2), j(r, 640, .2), 1500], [0.08, 0.05, 0.02]), 1.4)
    return buf


def shutter(r, up=True):
    """roller shutter (kiosk): slat rattle as it coils"""
    sec = j(r, 1.6, .15)
    buf = Z(sec + 0.3)
    t0 = 0
    while t0 < sec:
        put(buf, 0.3 * impulse(r, 0.01, 3000, 0.002) + 0.15 * modes(r, 0.05, [j(r, 900, .3)], [0.01]), t0)
        t0 += j(r, 0.045, .4)
    put(buf, 0.6 * modes(r, 0.3, [j(r, 160, .1), 500], [0.06, 0.03]), sec)
    return buf


def heavy_door(r, open_=True):
    """steel fire / cross-passage door: latch bar, hinge groan, closing slam against seals"""
    buf = Z(1.8)
    put(buf, 0.7 * impulse(r, 0.02, 3000, 0.003) + 0.4 * modes(r, 0.15, [j(r, 600, .1), 1500], [0.04, 0.02]), 0)
    x = T(0.8)
    put(buf, 0.15 * reson(N(r, 0.8), j(r, 340, .2), 30) * np.sin(np.pi * x / 0.8) * 8, 0.1)
    if not open_:
        put(buf, modes(r, 0.6, [j(r, 70, .1), j(r, 190, .1), 520], [0.12, 0.07, 0.04]), 0.95)
    return buf


def gas_release(r):
    """fire suppression discharge (inert gas / FM-200): violent nozzle roar decaying over seconds"""
    sec = j(r, 6.0, .15)
    x = T(sec)
    y = bp(N(r, sec), 300, 9000) * np.minimum(1, x / 0.05) * np.exp(-x / j(r, 2.2, .2))
    y += 0.4 * lp(N(r, sec), 300) * np.exp(-x / 1.0)
    put(y, machine_clunk(r), 0)
    return sat(y, 1.5)


def pantograph(r, up=True):
    """pantograph: valve, air into the raising cylinder (hiss), head touches the wire (clack) / drops onto its rests"""
    buf = Z(2.4)
    put(buf, 0.4 * relay_clunk(r), 0)
    x = T(1.2)
    put(buf, 0.5 * bp(N(r, 1.2), 900, 7000) * np.minimum(1, x / 0.05) * np.exp(-x / 0.5), 0.05)
    if up:
        put(buf, 0.6 * modes(r, 0.4, [j(r, 600, .1), j(r, 1700, .1), 3100], [0.08, 0.04, 0.02]), j(r, 1.3, .1))
    else:
        put(buf, modes(r, 0.5, [j(r, 120, .1), j(r, 420, .1), 1100], [0.12, 0.06, 0.03]), j(r, 1.1, .1))
    return buf


def ship_horn(r):
    """ship's whistle / typhon: very low chord, long blast"""
    base = j(r, 110, .08)
    return horn(r, [base, base * 1.26] if r.random() < 0.5 else [base], j(r, 3.5, .2))


def ctl_push(r):
    """panel push button: plunger travel, contact block snap, spring return"""
    buf = Z(0.25)
    put(buf, 0.7 * impulse(r, 0.01, j(r, 4500, .2), 0.0012) + 0.3 * modes(r, 0.06, [j(r, 1900, .15), j(r, 4100, .15)], [0.012, 0.006]), 0)
    put(buf, 0.45 * impulse(r, 0.006, 7000, 0.0006), j(r, 0.012, .3))
    put(buf, 0.35 * impulse(r, 0.008, 5000, 0.001), j(r, 0.11, .3))
    return buf


def ctl_mushroom(r):
    """emergency stop: palm slaps the mushroom head, it latches with a hard clack"""
    buf = Z(0.4)
    put(buf, 0.9 * lp(N(r, 0.05), 1400) * np.exp(-T(0.05) / 0.01), 0)
    put(buf, 0.6 * modes(r, 0.15, [j(r, 260, .1), j(r, 900, .1)], [0.04, 0.02]), 0)
    put(buf, 0.8 * impulse(r, 0.01, 5000, 0.0012) + 0.3 * modes(r, 0.08, [j(r, 2600, .1)], [0.015]), j(r, 0.03, .3))
    return buf


def ctl_selector(r):
    """rotary cam switch: detent spring snaps over, cam contacts change"""
    buf = Z(0.25)
    put(buf, 0.4 * impulse(r, 0.008, 3500, 0.001), 0)
    put(buf, 0.9 * impulse(r, 0.01, 5500, 0.001) + 0.35 * modes(r, 0.08, [j(r, 1400, .15), j(r, 3300, .15)], [0.02, 0.01]), j(r, 0.035, .3))
    put(buf, 0.3 * relay_click(r), j(r, 0.05, .3))
    return buf


def ctl_key(r):
    """key switch: key slides in the pin tumblers, cylinder turns, spring-loaded cam clacks"""
    buf = Z(0.45)
    put(buf, 0.18 * bp(N(r, 0.1), 3000, 9000) * np.exp(-T(0.1) / 0.04), 0)
    put(buf, 0.8 * impulse(r, 0.01, 4500, 0.0012) + 0.35 * modes(r, 0.12, [j(r, 2300, .1), j(r, 5200, .1)], [0.02, 0.01]), j(r, 0.14, .2))
    return buf


def ctl_lever(r):
    """big operating handle: catch, swing, heavy stop"""
    buf = Z(0.7)
    put(buf, 0.6 * impulse(r, 0.02, 3000, 0.003), 0)
    x = T(0.3)
    put(buf, 0.25 * bp(N(r, 0.3), 150, 900) * np.sin(np.pi * x / 0.3), 0.03)
    put(buf, modes(r, 0.4, [j(r, 110, .1), j(r, 380, .1), 1100], [0.1, 0.06, 0.03], [1, .6, .3]), j(r, 0.32, .15))
    return buf


# ---- UI
def keypad(r):
    """membrane keypad: rubber dome click + piezo confirm beep"""
    buf = Z(0.2)
    put(buf, 0.5 * impulse(r, 0.006, 3500, 0.001), 0)
    f = r.choice([2400, 2700, 3000, 3200])
    x = T(0.07)
    put(buf, 0.35 * np.sin(2 * np.pi * f * x) * fade(np.ones(len(x)), 0.002, 0.01), 0.008)
    return buf


def beep(r, ok=True):
    if ok:
        buf = Z(0.45)
        f = r.choice([2000, 2400, 2800])
        put(buf, 0.5 * fade(np.sin(2 * np.pi * f * T(0.09)), 0.002, 0.01), 0)
        put(buf, 0.5 * fade(np.sin(2 * np.pi * f * 1.33 * T(0.14)), 0.002, 0.02), 0.12)
        return buf
    buf = Z(0.7)
    f = r.choice([300, 350, 400])
    for k in range(r.integers(1, 3)):
        put(buf, 0.5 * fade(lp(np.sign(np.sin(2 * np.pi * f * T(0.22))), 3000), 0.003, 0.02), k * 0.3)
    return buf


def ui_click(r):
    buf = Z(0.06)
    put(buf, impulse(r, 0.01, j(r, 7000, .2), 0.0006) + 0.2 * modes(r, 0.03, [j(r, 4200, .2)], [0.004]), 0)
    return buf


def robot_beep(r):
    """delivery / welding robot status chirps: short sine sweeps"""
    buf = Z(1.0)
    t0 = 0
    for k in range(r.integers(2, 6)):
        d = r.uniform(0.05, 0.14)
        f0, f1 = r.uniform(900, 2600), r.uniform(900, 3200)
        x = T(d)
        put(buf, 0.45 * fade(np.sin(2 * np.pi * np.cumsum(np.linspace(f0, f1, len(x))) / SR), 0.003, 0.01), t0)
        t0 += d + r.uniform(0.01, 0.06)
    return buf


def servo(r):
    """robot arm axis move: servo whine up and down"""
    sec = j(r, 1.0, .3)
    x = T(sec)
    sp = np.sin(np.pi * x / sec)
    f = 300 + 900 * sp
    y = 0.4 * osc(f, ((1, 1), (2, .3))) * sp + 0.1 * bp(N(r, sec), 2000, 6000) * sp
    return fade(y, 0.01, 0.05)


# ---- factory / industry
def crusher(r, sec):
    """jaw crusher (loop): eccentric shaft thump, rock fracture cracks, rumble, falling grit"""
    x = T(sec)
    rate = j(r, 4.2, .1)
    y = 0.4 * lp(N(r, sec), 250) * (1 + 0.3 * np.sin(2 * np.pi * rate * x))
    t0 = 0
    while t0 < sec:
        put(y, 0.5 * modes(r, 0.2, [j(r, 45, .1), 110], [0.06, 0.03]), t0)
        if r.random() < 0.6:
            put(y, r.uniform(.3, 1) * (hp(N(r, 0.05), 800) * np.exp(-T(0.05) / 0.008) + modes(r, 0.12, [r.uniform(300, 900), r.uniform(1000, 2500)], [0.03, 0.015])), t0 + r.uniform(0, 0.1))
        t0 += 1 / rate
    grit = (r.random(len(x)) > 0.996) * r.uniform(0, 1, len(x))
    y += 0.6 * hp(grit, 1500)
    return y


def conveyor(r, sec):
    """belt conveyor (loop): idler rollers, belt flap over each idler, drive gearbox, occasional squeak"""
    x = T(sec)
    y = 0.4 * lp(N(r, sec), 500) + 0.2 * motor_run(r, sec, rpm=1480, slots=24)
    idl = j(r, 6.5, .1)
    y += 0.25 * bp(N(r, sec), 100, 600) * (0.6 + 0.4 * np.abs(np.sin(np.pi * idl * x)) ** 4)
    y += 0.12 * np.sin(2 * np.pi * j(r, 37, .1) * 5 * x) * (1 + 0.3 * slow_noise(r, len(x), 1))
    sq = np.clip(slow_noise(r, len(x), 0.4) - 1.0, 0, None)
    y += 0.3 * osc(r.uniform(1800, 3000) * (1 + 0.02 * slow_noise(r, len(x), 3))) * sq
    return y


def arc_furnace(r, sec, melt=True):
    """electric arc furnace (loop): bore-in crackle, 100 Hz arc reignition buzz, thunderous roar, scrap cave-ins"""
    x = T(sec)
    unstable = np.clip(0.6 + 0.6 * slow_noise(r, len(x), 1.2), 0.1, 1.5) if melt else 0.35
    buzz = (np.abs(np.sin(2 * np.pi * 50 * x)) ** 0.3) * hp(N(r, sec), 300)
    y = 0.8 * lp(N(r, sec), 400) + 0.6 * buzz * unstable
    cr = N(r, sec) * (r.random(len(x)) > 0.97) * 3
    y += hp(cr, 1500) * unstable
    t0 = r.uniform(0, 3)
    while t0 < sec - 0.5:
        put(y, 1.2 * modes(r, 0.5, [r.uniform(40, 70), r.uniform(150, 300)], [0.15, 0.06]), t0)
        t0 += r.uniform(1.5, 5)
    return sat(y, 1.6)


def furnace_roar(r, sec):
    """burner / blast furnace (loop): combustion roar with low pulsation"""
    x = T(sec)
    return lp(N(r, sec), 350) * (1 + 0.25 * slow_noise(r, len(x), 0.8)) + 0.3 * bp(N(r, sec), 600, 2500) + \
        0.2 * np.sin(2 * np.pi * j(r, 18, .2) * x) * (1 + 0.5 * slow_noise(r, len(x), 0.5))


def mill_bang(r):
    """rolling mill: billet bites the rolls (huge bang), stands groan, ring of the billet"""
    buf = Z(2.5)
    put(buf, modes(r, 2.0, [j(r, 38, .1), j(r, 95, .1), j(r, 260, .1), j(r, 720, .1), 1600],
                   [0.5, 0.35, 0.2, 0.1, 0.05], [1, .8, .5, .3, .15]), 0)
    put(buf, 0.8 * lp(N(r, 0.1), 2000) * np.exp(-T(0.1) / 0.02), 0)
    x = T(1.5)
    put(buf, 0.3 * motor_run(r, 1.5, rpm=500, slots=60) * np.exp(-x / 0.8), 0.05)
    return sat(buf, 1.4)


def steam_hammer(r):
    """steam hammer blow: admission hiss, tup falls, enormous thud into the anvil block, exhaust"""
    buf = Z(2.4)
    put(buf, 0.4 * bp(N(r, 0.35), 1500, 8000) * np.sin(np.pi * T(0.35) / 0.35), 0)
    hit = j(r, 0.38, .1)
    put(buf, modes(r, 1.5, [j(r, 30, .1), j(r, 72, .1), j(r, 180, .1), j(r, 410, .1)], [0.35, 0.25, 0.12, 0.06]), hit)
    put(buf, lp(N(r, 0.08), 3000) * np.exp(-T(0.08) / 0.015), hit)
    put(buf, 0.5 * bp(N(r, 1.2), 600, 6000) * np.exp(-T(1.2) / 0.4), hit + 0.12)
    return sat(buf, 1.8)


def press_thump(r):
    """hydraulic / oil press stroke"""
    buf = Z(1.6)
    put(buf, 0.3 * fade(pump(r, 1.0), 0.05, 0.1), 0)
    put(buf, modes(r, 0.6, [j(r, 60, .1), 170, 450], [0.12, 0.06, 0.03]), 1.0)
    return buf


def saw(r, sec):
    """sawmill head saw (loop): blade hum, cutting scream in bursts, chips"""
    x = T(sec)
    cut = np.clip(0.4 + slow_noise(r, len(x), 0.5), 0, 1.2)
    y = 0.3 * osc(np.full(len(x), j(r, 59, .05)), ((1, 1), (2, .4), (3, .2)))
    y += 0.45 * osc(j(r, 2900, .1) * (1 - 0.08 * cut), ((1, 1), (2, .3))) * cut
    y += 0.35 * bp(N(r, sec), 2000, 9000) * cut
    return y


def bubbles(r, sec, rate=60):
    """flotation cells / electrolyser (loop): bubbling froth"""
    x = T(sec)
    y = 0.15 * lp(N(r, sec), 300)
    b = (r.random(len(x)) > 1 - rate / SR) * 1.0
    for f in (r.uniform(300, 600), r.uniform(700, 1400)):
        fs = f * (1 + 0.4 * slow_noise(r, len(x), 3))
        y += 0.6 * reson(b, float(np.mean(fs)), 25)
    return y


def welding(r, sec):
    """MIG welding (loop): arc frying-bacon crackle with wire feed whir"""
    x = T(sec)
    on = (slow_noise(r, len(x), 0.6) > -0.6).astype(float)
    on = lp(on, 30, 1)
    cr = N(r, sec) * (r.random(len(x)) > 0.9) * 0.9
    y = (hp(cr, 1500) + 0.5 * bp(N(r, sec), 1000, 8000)) * on
    y += 0.1 * osc(np.full(len(x), 120), ((1, 1), (2, .5))) * on
    return y


def windmill(r, sec):
    """grain windmill (loop): wooden gearing creaks, millstone grind, sail swoosh"""
    x = T(sec)
    y = 0.5 * lp(N(r, sec), 200) * (1 + 0.3 * np.sin(2 * np.pi * 0.15 * x))
    y += 0.4 * bp(N(r, sec), 200, 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.6 * x) ** 8)
    t0 = r.uniform(0, 2)
    while t0 < sec - 1:
        d = r.uniform(0.3, 0.9)
        xx = T(d)
        put(y, 0.25 * osc(r.uniform(250, 700) * (1 + 0.2 * np.sin(np.pi * xx / d)), ((1, 1), (2, .5), (3, .3))) * np.sin(np.pi * xx / d), t0)
        t0 += r.uniform(1.5, 4)
    return y


def machine_clunk(r):
    """heavy valve / gate / damper actuates"""
    buf = Z(0.8)
    put(buf, 0.6 * impulse(r, 0.02, 2500, 0.003), 0)
    put(buf, modes(r, 0.6, [j(r, 90, .15), j(r, 280, .15), j(r, 760, .15)], [0.12, 0.06, 0.03], [1, .6, .3]), 0.005)
    return buf


def machine_door(r, open_=True):
    """machine guard / inspection hatch"""
    buf = Z(0.9)
    put(buf, 0.5 * impulse(r, 0.015, 4000, 0.002), 0)
    x = T(0.4)
    put(buf, 0.1 * reson(N(r, 0.4), j(r, 600, .2), 30) * 8 * np.sin(np.pi * x / 0.4), 0.05)
    if not open_:
        put(buf, modes(r, 0.4, [j(r, 160, .1), j(r, 470, .1), 1200], [0.08, 0.05, 0.02]), 0.45)
    return buf


def explosion(r):
    """dust / boiler explosion: pressure wave, debris"""
    sec = 3.5
    x = T(sec)
    y = lp(N(r, sec), 300) * np.exp(-x / 0.6) * 1.5 + bp(N(r, sec), 300, 4000) * np.exp(-x / 0.3)
    deb = (r.random(len(x)) > 0.998) * np.exp(-x / 1.2)
    y += 1.5 * reson(deb, 900, 5) + reson(deb, 2500, 8)
    return sat(y, 3)


def lathe(r, sec):
    """wheel lathe (loop): wheelset turning, tool cutting chatter, swarf breaking"""
    x = T(sec)
    y = 0.3 * motor_run(r, sec, rpm=980, slots=48)
    y += 0.35 * osc(j(r, 1600, .15) * (1 + 0.01 * slow_noise(r, len(x), 4)), ((1, 1), (2, .2))) * (0.6 + 0.4 * slow_noise(r, len(x), 2))
    sw = (r.random(len(x)) > 0.9993) * 1.0
    y += 0.8 * reson(sw, r.uniform(2500, 5000), 15)
    return y


def wash(r, sec):
    """train wash (loop): rotating brushes slapping, water spray"""
    x = T(sec)
    y = 0.6 * bp(N(r, sec), 800, 9000)
    y += 0.5 * bp(N(r, sec), 100, 800) * (0.5 + 0.5 * np.abs(np.sin(np.pi * j(r, 3, .1) * x)))
    y += 0.2 * motor_run(r, sec, rpm=1400, slots=24)
    return y


def hydraulic_jack(r):
    """bogie drop / jack: hydraulic pump whine, relief hiss, stop"""
    sec = j(r, 3.0, .2)
    buf = Z(sec + 0.5)
    x = T(sec)
    put(buf, fade(0.4 * pump(r, sec) + 0.25 * osc(np.full(len(x), j(r, 420, .1)), ((1, 1), (2, .4))), 0.1, 0.2), 0)
    put(buf, 0.4 * bp(N(r, 0.5), 1500, 8000) * np.exp(-T(0.5) / 0.2), sec - 0.1)
    put(buf, machine_clunk(r) * 0.6, sec)
    return buf


def impact_wrench(r):
    """air tool: impact wrench hammering a wheel nut"""
    sec = j(r, 1.4, .2)
    buf = Z(sec + 0.3)
    put(buf, 0.3 * bp(N(r, sec), 2000, 9000), 0)
    t0 = 0
    rate = j(r, 22, .15)
    while t0 < sec:
        put(buf, 0.6 * modes(r, 0.05, [j(r, 1400, .1), 3300], [0.01, 0.006]), t0)
        t0 += 1 / rate
    return fade(buf, 0.01, 0.1)


def fuel_pump(r, sec):
    x = T(sec)
    return 0.5 * motor_run(r, sec, rpm=2900, slots=24) + 0.3 * bp(N(r, sec), 200, 1500) * (1 + 0.2 * slow_noise(r, len(x), 4))


def jet_fan(r, sec):
    """tunnel jet fan (loop): big axial fan, roar reverberating in the bore"""
    y = fan(r, sec, blades=11, rps=24, big=True)
    return room(r, y, 0.09, 0.3, 1.0)


def construction(r):
    """construction site: hammer blows, steel rattle"""
    buf = Z(2.0)
    t0 = 0
    for k in range(r.integers(3, 6)):
        put(buf, modes(r, 0.3, [r.uniform(600, 1400), r.uniform(1800, 3500)], [0.06, 0.03]) + 0.4 * impulse(r, 0.01, 4000, 0.002), t0)
        t0 += r.uniform(0.25, 0.45)
    return buf


def stamp(r):
    """rubber stamp / paper on a desk (offices, permits, approvals)"""
    buf = Z(0.5)
    put(buf, 0.8 * lp(N(r, 0.04), 1200) * np.exp(-T(0.04) / 0.008) + 0.4 * modes(r, 0.12, [j(r, 180, .2), j(r, 420, .2)], [0.03, 0.02]), 0)
    put(buf, 0.15 * bp(N(r, 0.2), 2000, 8000) * np.exp(-T(0.2) / 0.06), 0.08)
    return buf


def paper(r):
    """paper shuffle / form torn off"""
    sec = j(r, 0.6, .3)
    x = T(sec)
    return bp(N(r, sec), 1500, 9000) * np.clip(slow_noise(r, len(x), 15), 0, None) ** 2


def drawer(r, open_=True):
    """steel cabinet drawer on runners"""
    sec = j(r, 0.5, .2)
    buf = Z(sec + 0.3)
    x = T(sec)
    put(buf, 0.25 * bp(N(r, sec), 600, 4000) * np.sin(np.pi * x / sec) + 0.1 * reson(N(r, sec), j(r, 1500, .2), 20) * 5 * np.sin(np.pi * x / sec), 0)
    put(buf, 0.5 * modes(r, 0.3, [j(r, 300, .2), j(r, 800, .2)], [0.05, 0.02]), sec)
    return buf


def cash(r):
    """cash drawer / coin tray"""
    buf = Z(1.0)
    put(buf, 0.6 * modes(r, 0.6, [j(r, 2637, .02), j(r, 3520, .02)], [0.3, 0.2]), 0)
    put(buf, 0.5 * drawer(r), 0.12)
    for k in range(r.integers(3, 7)):
        put(buf, 0.25 * modes(r, 0.15, [r.uniform(3000, 6000)], [0.04]), 0.3 + r.uniform(0, 0.3))
    return buf


# ================================================================================================ event table
# id: (group subtitle, variants, function(r) -> samples, gain, loop?)
L = 16.0            # loop length: long enough that the ear can't find the seam


def LOOP(fn, sec=L, xf=0.6):
    return lambda r: loop(fn(r, sec + xf), xf)


EVENTS = {
    # ---- switchgear / signalling relays
    'grid.relay_click': ('Relay clicks', 6, relay_click, .7, False),
    'grid.relay_clunk': ('Auxiliary relay clunks', 4, relay_clunk, .8, False),
    'grid.contactor_close': ('Contactor closes', 5, lambda r: contactor(r, True), .85, False),
    'grid.contactor_open': ('Contactor opens', 4, lambda r: contactor(r, False), .85, False),
    'grid.breaker_close': ('Breaker closes', 5, lambda r: breaker(r, False), .95, False),
    'grid.breaker_trip': ('Breaker trips', 5, lambda r: breaker(r, True), .95, False),
    'grid.spring_charge': ('Breaker spring charges', 3, spring_charge, .7, False),
    'grid.arc_crack': ('Arc cracks', 4, arc_crack, .95, False),
    'grid.tap_change': ('Tap changer steps', 4, tap_change, .8, False),
    'grid.hum_light': ('Transformer hums', 3, LOOP(lambda r, s: hum(r, s, 50, 0.15)), .55, True),
    'grid.hum_medium': ('Transformer hums', 3, LOOP(lambda r, s: hum(r, s, 50, 0.55)), .6, True),
    'grid.hum_heavy': ('Transformer groans under load', 3, LOOP(lambda r, s: hum(r, s, 50, 1.0)), .65, True),
    'grid.rectifier': ('Rectifier whines', 3, LOOP(lambda r, s: hum(r, s, 50, 0.5, True)), .55, True),
    'grid.motor_start': ('Motor runs up', 4, lambda r: motor_spin(r, True), .8, False),
    'grid.motor_stop': ('Motor runs down', 4, lambda r: motor_spin(r, False), .75, False),
    'grid.motor_run': ('Motor runs', 3, LOOP(lambda r, s: motor_run(r, s)), .55, True),
    # ---- signalling
    'signal.points': ('Points move', 5, point_machine, .85, False),
    'signal.points_air': ('Points move (pneumatic)', 4, lambda r: point_machine(r, 'air'), .85, False),
    'signal.lever_reverse': ('Signal lever pulled', 6, lambda r: lever_pull(r, True), .9, False),
    'signal.lever_normal': ('Signal lever put back', 5, lambda r: lever_pull(r, False), .9, False),
    'signal.frame_clank': ('Locking frame clanks', 4, frame_clank, .7, False),
    'signal.block_bell': ('Block bell rings', 5, block_bell, .8, False),
    'signal.block_needle': ('Block instrument turned', 4, instrument_needle, .6, False),
    'signal.crossing_gong': ('Crossing bell rings', 5, crossing_gong, .9, False),
    'signal.crossing_ebell': ('Crossing bell rings', 4, crossing_ebell, .7, False),
    'signal.crossing_yodel': ('Crossing alarm sounds', 4, crossing_yodel, .55, False),
    'signal.crossing_euro': ('Crossing bell rings', 4, crossing_euro, .6, False),
    'signal.barrier_down': ('Barriers lower', 3, lambda r: barrier(r, True), .7, False),
    'signal.barrier_up': ('Barriers rise', 3, lambda r: barrier(r, False), .7, False),
    # ---- alarms
    'alarm.annunciator': ('Alarm horn sounds', 6, annunciator, .55, False),
    'alarm.klaxon': ('Klaxon', 4, klaxon, .7, False),
    'alarm.fire_bell': ('Fire bell rings', 4, fire_bell, .7, False),
    'alarm.evac': ('Evacuation alarm', 3, evac_tone, .5, False),
    'alarm.teleprinter': ('Teleprinter chatters', 5, teleprinter, .6, False),
    'alarm.recorder': ('Recorder prints', 4, chart_recorder, .55, False),
    'alarm.phone': ('Telephone rings', 4, phone, .6, False),
    # ---- cab (the cab-controls agent looks these up by name)
    'cab.horn': ('Train horn', 6, cab_horn, .9, False),
    'cab.whistle': ('Steam whistle', 4, steam_whistle, .9, False),
    'cab.bell': ('Locomotive bell', 4, loco_bell, .7, False),
    'cab.brake_apply': ('Brakes apply', 5, brake_apply, .7, False),
    'cab.brake_release': ('Brakes release', 5, brake_release, .75, False),
    'cab.notch': ('Controller notches', 6, notch, .7, False),
    'cab.switch': ('Cab switch', 6, cab_switch, .6, False),
    'cab.radio': ('Cab radio', 5, cab_radio, .55, False),
    'cab.aws_horn': ('AWS horn', 3, aws_horn, .5, False),
    'cab.aws_bell': ('AWS bell', 3, aws_bell, .55, False),
    'cab.compressor': ('Compressor runs', 3, LOOP(compressor), .6, True),
    'cab.brake_emergency': ('Emergency brake', 4, brake_emergency, .9, False),
    'cab.button': ('Cab button', 5, ctl_push, .6, False),
    'cab.key': ('Cab key turned', 4, ctl_key, .65, False),
    'cab.lever': ('Brake handle moved', 5, brake_handle, .7, False),
    'cab.reverser': ('Reverser moved', 4, reverser, .75, False),
    # ---- train running
    'train.flange_squeal': ('Wheels squeal', 6, flange_squeal, .45, False),
    'train.rail_joint': ('Wheels clack over a joint', 6, rail_joint, .75, False),
    # ---- station
    'station.chime_two_tone': ('Station chime', 3, lambda r: pa_chime(r, 'two_tone'), .7, False),
    'station.chime_bell': ('Station bell', 3, lambda r: pa_chime(r, 'bell'), .7, False),
    'station.chime_dingdong': ('Station chime', 3, lambda r: pa_chime(r, 'dingdong'), .7, False),
    'station.chime_airport': ('Station chime', 3, lambda r: pa_chime(r, 'airport'), .7, False),
    'station.chime_pling': ('Station chime', 3, lambda r: pa_chime(r, 'pling'), .7, False),
    'station.wind': ('Wind blows', 3, LOOP(wind, 24.0), .5, True),
    'station.rain': ('Rain on the canopy', 3, LOOP(rain, 20.0), .45, True),
    'station.gate_open': ('Ticket gate opens', 4, lambda r: gate(r, True), .7, False),
    'station.lift_ding': ('Lift arrives', 3, lift_ding, .6, False),
    'station.lift_run': ('Lift runs', 3, lift_run, .6, False),
    'station.flush': ('Toilet flushes', 3, flush, .6, False),
    'station.tap': ('Tap runs', 3, tap_water, .5, False),
    'station.vend': ('Vending machine', 3, vend, .65, False),
    'station.shutter': ('Shutter rolls', 3, shutter, .6, False),
    'station.door_open': ('Heavy door opens', 4, lambda r: heavy_door(r, True), .7, False),
    'station.door_close': ('Heavy door closes', 4, lambda r: heavy_door(r, False), .8, False),
    # ---- UI / office
    'ui.keypad': ('Keypad', 6, keypad, .55, False),
    'ui.beep_ok': ('Accepted beep', 4, lambda r: beep(r, True), .5, False),
    'ui.beep_error': ('Error beep', 3, lambda r: beep(r, False), .45, False),
    'ui.click': ('Click', 5, ui_click, .5, False),
    'ui.robot': ('Robot chirps', 6, robot_beep, .5, False),
    'ui.stamp': ('Stamp', 4, stamp, .7, False),
    'ui.paper': ('Paper', 4, paper, .5, False),
    'ui.drawer_open': ('Drawer opens', 4, lambda r: drawer(r, True), .6, False),
    'ui.cash': ('Payment taken', 3, cash, .6, False),
    # ---- factory / industry
    'machine.crusher': ('Crusher pounds', 3, LOOP(crusher), .7, True),
    'machine.conveyor': ('Conveyor runs', 3, LOOP(conveyor), .55, True),
    'machine.arc_furnace': ('Arc furnace roars', 3, LOOP(arc_furnace), .8, True),
    'machine.furnace': ('Furnace roars', 3, LOOP(furnace_roar), .6, True),
    'machine.pump': ('Pump runs', 3, LOOP(pump), .5, True),
    'machine.fan': ('Fans run', 3, LOOP(lambda r, s: fan(r, s)), .5, True),
    'machine.saw': ('Saw cuts', 3, LOOP(saw), .55, True),
    'machine.bubbles': ('Cells bubble', 3, LOOP(bubbles), .5, True),
    'machine.welding': ('Welding arc', 3, LOOP(welding), .55, True),
    'machine.windmill': ('Windmill turns', 2, LOOP(windmill, 20.0), .55, True),
    'machine.lathe': ('Lathe cuts', 3, LOOP(lathe), .55, True),
    'machine.wash': ('Train wash runs', 2, LOOP(wash), .55, True),
    'machine.fuel_pump': ('Fuel pump runs', 2, LOOP(fuel_pump), .5, True),
    'machine.mill_bang': ('Rolling mill bangs', 5, mill_bang, .95, False),
    'machine.steam_hammer': ('Steam hammer strikes', 5, steam_hammer, .95, False),
    'machine.press': ('Press stroke', 4, press_thump, .8, False),
    'machine.clunk': ('Valve clunks', 6, machine_clunk, .75, False),
    'machine.door_open': ('Hatch opens', 4, lambda r: machine_door(r, True), .65, False),
    'machine.door_close': ('Hatch closes', 4, lambda r: machine_door(r, False), .7, False),
    'machine.explosion': ('Explosion', 3, explosion, .95, False),
    'machine.servo': ('Robot arm moves', 5, servo, .5, False),
    'machine.jack': ('Hydraulic jack', 4, hydraulic_jack, .7, False),
    'machine.air_tool': ('Impact wrench', 4, impact_wrench, .7, False),
    'machine.construction': ('Construction work', 4, construction, .7, False),
    # ---- track / tunnels
    'track.jet_fan': ('Tunnel fans roar', 3, LOOP(jet_fan), .6, True),
    'signal.points_hyd': ('Points move (hydraulic)', 4, lambda r: point_machine(r, 'hydraulic'), .85, False),
    'signal.points_lock': ('Points lock', 5, points_lock, .8, False),
    'signal.points_crank': ('Points cranked by hand', 4, points_crank, .7, False),
    'alarm.gas_release': ('Fire suppression discharges', 3, gas_release, .9, False),
    'train.pantograph_up': ('Pantograph rises', 4, lambda r: pantograph(r, True), .7, False),
    'train.pantograph_down': ('Pantograph drops', 4, lambda r: pantograph(r, False), .7, False),
    'station.ship_horn': ('Ship horn', 3, ship_horn, .95, False),
    'ctl.push': ('Button pressed', 6, ctl_push, .7, False),
    'ctl.mushroom': ('Emergency stop hit', 4, ctl_mushroom, .85, False),
    'ctl.selector': ('Switch turned', 5, ctl_selector, .7, False),
    'ctl.key': ('Key turned', 4, ctl_key, .7, False),
    'ctl.lever': ('Handle thrown', 4, ctl_lever, .85, False),
}
for _code, _beats in BELL_CODES.items():
    EVENTS['signal.bellcode.' + _code] = ('Bell code ' + _beats, 3, (lambda c: lambda r: bell_code(r, c))(_beats), .8, False)


# the older one-file ids (gen_sounds.py / gen_sounds_plant.py) that code already plays: the pack adds its
# variations of the same physical thing to them, so every one of those calls stops repeating itself
AUGMENT = {
    'relay_click': ['grid.relay_click'], 'contactor': ['grid.contactor_close'], 'breaker_close': ['grid.breaker_close'],
    'breaker_trip': ['grid.breaker_trip'], 'tap_change': ['grid.tap_change'], 'arc_flash': ['grid.arc_crack'],
    'hum_transformer': ['grid.hum_medium'], 'fan_loop': ['machine.fan'], 'whine_rect': ['grid.rectifier'],
    'fire_bell': ['alarm.fire_bell'], 'klaxon': ['alarm.klaxon'], 'phone_ring': ['alarm.phone'],
    'crossing_bell': ['signal.crossing_gong'], 'block_bell': ['signal.block_bell'], 'point_motor': ['signal.points'],
    'beep_ok': ['ui.beep_ok'], 'beep_error': ['ui.beep_error'], 'gate': ['station.gate_open'], 'cash': ['ui.cash'],
    'printer': ['alarm.recorder'], 'pa_chime': ['station.chime_airport'], 'air_release': ['cab.brake_release'],
    'ctl_push': ['ctl.push'], 'button_press': ['ctl.push'], 'ctl_selector': ['ctl.selector'], 'selector_turn': ['ctl.selector'],
    'ctl_key': ['ctl.key'], 'key_switch': ['ctl.key'], 'ctl_estop': ['ctl.mushroom'], 'estop_hit': ['ctl.mushroom'],
    'ctl_lever': ['ctl.lever'], 'lever_heavy': ['ctl.lever'], 'ctl_signal_lever': ['signal.lever_reverse'],
    'lever_clack': ['signal.lever_reverse'],
}


# ================================================================================================ output
def save(path, y, gain):
    y = np.asarray(y, dtype=np.float64)
    y = y - np.mean(y)
    y = norm(y, gain)
    pcm = (np.clip(y, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        w = wave.open(tf.name, 'wb')
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes()); w.close()
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tf.name, '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
        os.unlink(tf.name)


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ''
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(FRAG, exist_ok=True)
    frag = {}
    lang = []
    for eid, (sub, n, fn, gain, is_loop) in EVENTS.items():
        files = []
        for k in range(n):
            fname = eid.replace('.', '_') + f'_{k + 1}'
            files.append({'name': 'irextras:pack/' + fname, 'stream': False})
            if only and not eid.startswith(only):
                continue
            r = np.random.default_rng(zlib.crc32(eid.encode()) * 10 + k)
            y = fn(r)
            if not is_loop:
                y = fade(y, 0.0005, 0.02)
            save(os.path.join(OUT, fname + '.ogg'), y, gain)
        frag[eid] = {'subtitle': 'irextras.sub.' + eid, 'sounds': files}
        lang.append(f'irextras.sub.{eid}={sub}')
        print(eid, n, flush=True)
    lang.append('irextras.sub.voice=Station announcement')
    json.dump(frag, open(os.path.join(FRAG, 'pack.json'), 'w'), indent=1)
    open(os.path.join(FRAG, 'subtitles.lang'), 'w').write('\n'.join(lang) + '\n')
    open(os.path.join(ROOT, 'soundpack.txt'), 'w').write('\n'.join(sorted(EVENTS)) + '\n')
    merge()


def merge():
    """sounds.json = gen_sounds.py's plain ids + our dotted ids (pack + voice)"""
    p = os.path.join(ROOT, 'sounds.json')
    sj = json.load(open(p))
    sj = {k: v for k, v in sj.items() if '.' not in k}
    pack = json.load(open(os.path.join(FRAG, 'pack.json'))) if os.path.exists(os.path.join(FRAG, 'pack.json')) else {}
    for old, ids in AUGMENT.items():
        if old in sj:
            names = {x['name'] for x in sj[old]['sounds']}
            for i in ids:
                for snd in pack.get(i, {}).get('sounds', []):
                    if snd['name'] not in names:
                        sj[old]['sounds'].append(snd)
                        names.add(snd['name'])
    for f in ('pack.json', 'voice.json'):
        fp = os.path.join(FRAG, f)
        if os.path.exists(fp):
            sj.update(json.load(open(fp)))
    json.dump(sj, open(p, 'w'), indent=1)
    print('sounds.json:', len(sj), 'events')


if __name__ == '__main__':
    if sys.argv[1:] == ['--merge']:
        merge()
    else:
        main()
