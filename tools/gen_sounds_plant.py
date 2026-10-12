#!/usr/bin/env python3
"""Plant + control-room sound pack for IR Extras (her ask 2026-10-10: sounds that sound like the real gear).
Called by gen_sounds.py main(): add(save, reg). Loops are made seamless (crossfaded tail) so the client
can play them forever; GridSounds then follows each machine's real state with volume + pitch."""
import numpy as np
from gen_sounds import SR, t, env, noise, lowpass, highpass, place, thunk, hiss, motor, bell

rng = np.random.default_rng(11)


def looped(gen, sec, xf=0.35):
    """render gen(sec + xf) and fold the tail over the head: the clip loops with no click"""
    y = gen(sec + xf)
    n, k = int(SR * sec), int(SR * xf)
    out = y[:n].copy()
    fade = np.linspace(0, 1, k)
    out[:k] = y[:k] * fade + y[n:n + k] * (1 - fade)
    return out


def tone(x, f, ph=0.0):
    return np.sin(2 * np.pi * f * x + ph)


def flutter(x, rate, depth):
    return 1 + depth * np.sin(2 * np.pi * rate * x)


def crackle(sec, rate, cut=4000, decay=0.004):
    """random sharp pops (arcing, sizzling insulation)"""
    n = int(SR * sec)
    y = np.zeros(n)
    for _ in range(int(rate * sec)):
        i = rng.integers(0, max(1, n - 400))
        k = int(SR * decay * rng.uniform(0.5, 2))
        y[i:i + k] += rng.uniform(0.3, 1) * rng.choice([-1, 1]) * np.exp(-np.arange(k) / (k / 4))[:len(y[i:i + k])]
    return highpass(y, cut * 0.3)


def ring(f, sec, decay):
    """metal ring (breaker frame, lever frame, key)"""
    x = t(sec)
    return sum(tone(x, f * m) * np.exp(-x / (decay / m ** 0.5)) / m for m in (1, 2.76, 5.4, 8.9))


def add(save, reg):
    # ---------------------------------------------------------------- loops (played by client GridSounds)
    # transformer: 60 Hz mains -> 120 Hz magnetostriction + harmonics, core buzz, slow beat between phases
    def tx(sec):
        x = t(sec)
        y = (tone(x, 120) + 0.55 * tone(x, 240, 1) + 0.35 * tone(x, 360, 2) + 0.18 * tone(x, 480, 3) + 0.08 * tone(x, 720, 4))
        y *= flutter(x, 0.5, 0.06)
        return y + 0.05 * lowpass(noise(sec), 2500)
    save('hum_transformer', looped(tx, 4.0), 0.6); reg('hum_transformer', 'Transformer hums')

    # cooling fans: blade-pass thrum + air roar
    def fan(sec):
        x = t(sec)
        air = lowpass(highpass(noise(sec), 120), 1400)
        return 0.6 * air * flutter(x, 14.5, 0.25) + 0.25 * tone(x, 87) * flutter(x, 0.3, 0.2)
    save('fan_loop', looped(fan, 3.0), 0.55); reg('fan_loop', 'Cooling fans run')

    # 12-pulse traction rectifier: 720 Hz ripple whine on top of 120 Hz hum
    def rect(sec):
        x = t(sec)
        return (0.5 * tone(x, 720) + 0.25 * tone(x, 1440, 1) + 0.12 * tone(x, 2160, 2) + 0.6 * tone(x, 120)
                + 0.3 * tone(x, 360) + 0.06 * lowpass(noise(sec), 5000))
    save('whine_rect', looped(rect, 4.0), 0.5); reg('whine_rect', 'Rectifier whines')

    # IGBT inverter: 3 kHz switching carrier with ±120 Hz sidebands, 60 Hz fundamental, choke buzz
    def inv(sec):
        x = t(sec)
        car = tone(x, 3000) + 0.5 * tone(x, 2880) + 0.5 * tone(x, 3120) + 0.2 * tone(x, 6000)
        return 0.25 * car + 0.5 * tone(x, 60) + 0.3 * tone(x, 180) + 0.08 * highpass(noise(sec), 4000)
    save('whine_inv', looped(inv, 4.0), 0.45); reg('whine_inv', 'Inverter whines')

    # diesel genset at 1500 rpm: V12, 150 firings/s, each one a short rough thump
    def diesel(sec):
        n = int(SR * sec)
        y = np.zeros(n)
        k = int(SR * 0.012)
        pulse = lowpass(noise(0.012), 900) * np.exp(-np.arange(k) / (k / 3))
        for i in range(int(sec * 150)):
            j = int(i * SR / 150)
            y[j:j + k] += pulse[:len(y[j:j + k])] * (0.7 + 0.3 * ((i % 12) in (0, 5)) + 0.1 * rng.standard_normal())
        x = t(sec)
        return y + 0.35 * tone(x, 25) + 0.25 * tone(x, 75) + 0.15 * lowpass(noise(sec), 400) + 0.04 * highpass(noise(sec), 3000)
    save('engine_diesel', looped(diesel, 2.0), 0.7); reg('engine_diesel', 'Diesel engine runs')

    # steam turbine: 3000 rpm shaft, blade-pass whistle, steam roar
    def turbine(sec):
        x = t(sec)
        return (0.4 * tone(x, 50) + 0.3 * tone(x, 1250) * flutter(x, 0.7, 0.1) + 0.15 * tone(x, 2500)
                + 0.5 * lowpass(highpass(noise(sec), 200), 3000))
    save('engine_turbine', looped(turbine, 4.0), 0.6); reg('engine_turbine', 'Steam turbine runs')

    # wind turbine: three blades at 12 rpm -> a whoosh every 1.67 s, faint gearbox whine
    def wind(sec):
        x = t(sec)
        swoosh = np.maximum(0, np.sin(np.pi * 0.6 * x)) ** 4
        return 0.8 * lowpass(highpass(noise(sec), 150), 1100) * (0.25 + swoosh) + 0.04 * tone(x, 410)
    save('wind_loop', looped(wind, 5.0, 0.5), 0.6); reg('wind_loop', 'Wind turbine blades swoosh')

    # capacitor bank / reactor: faint 120 Hz + 360 Hz
    def cap(sec):
        x = t(sec)
        return tone(x, 120) + 0.4 * tone(x, 360) + 0.03 * lowpass(noise(sec), 2000)
    save('cap_hum', looped(cap, 4.0), 0.4); reg('cap_hum', 'Capacitor bank hums')

    # ---------------------------------------------------------------- one-shots
    # on-load tap changer: spring wind-up whir, diverter switch SNAP, ring
    buf = np.zeros(int(SR * 1.4))
    place(buf, motor(0.7, 180, 260, 0.25), 0.0)
    place(buf, 0.9 * thunk(0.3, 90) + 0.5 * highpass(noise(0.3), 2500) * env(int(SR * 0.3), 0.0005, 0.01), 0.72)
    place(buf, 0.25 * ring(620, 0.6, 0.25), 0.73)
    save('tap_change', buf); reg('tap_change', 'Tap changer moves')

    # vacuum breaker CLOSE: latch release click, heavy mechanism thunk, frame ring
    buf = np.zeros(int(SR * 1.0))
    place(buf, 0.4 * highpass(noise(0.02), 3000) * env(int(SR * 0.02), 0.0005, 0.004), 0.0)
    place(buf, 1.0 * thunk(0.45, 60), 0.03)
    place(buf, 0.3 * ring(410, 0.8, 0.35), 0.04)
    save('breaker_close', buf); reg('breaker_close', 'Breaker closes')

    # breaker TRIP under fault: bang, arc phht, ring, alarm-free
    buf = np.zeros(int(SR * 1.2))
    place(buf, 1.0 * thunk(0.5, 48), 0.0)
    place(buf, 0.9 * highpass(noise(0.08), 1800) * env(int(SR * 0.08), 0.0003, 0.02), 0.0)
    place(buf, 0.5 * hiss(0.25, 800, 7000, env(int(SR * 0.25), 0.001, 0.06)), 0.01)
    place(buf, 0.35 * ring(350, 1.0, 0.45), 0.02)
    save('breaker_trip', buf); reg('breaker_trip', 'Breaker trips')

    # ARC FLASH (breaker fails): crack, boom, crackling plasma
    buf = np.zeros(int(SR * 2.5))
    place(buf, 1.0 * highpass(noise(0.05), 1000) * env(int(SR * 0.05), 0.0002, 0.012), 0.0)
    x = t(1.2)
    place(buf, 0.9 * lowpass(noise(1.2), 300) * np.exp(-x / 0.35) + 0.6 * tone(x, 38) * np.exp(-x / 0.4), 0.0)
    place(buf, 0.6 * crackle(1.8, 140) * np.exp(-t(1.8) / 0.7), 0.05)
    place(buf, 0.4 * hiss(1.5, 500, 8000, np.exp(-t(1.5) / 0.5)), 0.02)
    save('arc_flash', buf); reg('arc_flash', 'Arc flash!')

    # cable burning out: sizzle + pops
    x = t(2.5)
    y = 0.5 * hiss(2.5, 2500, 9000, env(len(x), 0.2, None, 1, 0.6)) + 0.7 * crackle(2.5, 60, 3000)
    save('cable_sizzle', y); reg('cable_sizzle', 'Cable sizzles')

    # control-room push button: plastic thock + contact click
    buf = np.zeros(int(SR * 0.15))
    place(buf, 0.6 * thunk(0.08, 420), 0.0)
    place(buf, 0.5 * highpass(noise(0.01), 3500) * env(int(SR * 0.01), 0.0003, 0.002), 0.012)
    save('button_press', buf); reg('button_press', 'Button pressed')

    # emergency stop: palm slap on the mushroom + latch
    buf = np.zeros(int(SR * 0.4))
    place(buf, 1.0 * thunk(0.15, 160) + 0.5 * lowpass(noise(0.15), 2000) * np.exp(-t(0.15) / 0.02), 0.0)
    place(buf, 0.6 * highpass(noise(0.015), 3000) * env(int(SR * 0.015), 0.0003, 0.003), 0.06)
    save('estop_hit', buf); reg('estop_hit', 'Emergency stop hit')

    # rotary selector: two detent clicks
    buf = np.zeros(int(SR * 0.2))
    for at in (0.0, 0.05):
        place(buf, 0.6 * highpass(noise(0.012), 2500) * env(int(SR * 0.012), 0.0003, 0.003), at)
        place(buf, 0.2 * thunk(0.04, 700), at)
    save('selector_turn', buf); reg('selector_turn', 'Selector switch turns')

    # key switch: key jingle + cylinder click
    buf = np.zeros(int(SR * 0.5))
    place(buf, 0.25 * ring(2600, 0.35, 0.12), 0.0)
    place(buf, 0.25 * ring(3300, 0.3, 0.1), 0.0)
    place(buf, 0.6 * highpass(noise(0.015), 3000) * env(int(SR * 0.015), 0.0003, 0.003), 0.12)
    save('key_switch', buf); reg('key_switch', 'Key switch turns')

    # heavy operating handle (switchgear): grinding swing + clank
    buf = np.zeros(int(SR * 0.9))
    place(buf, 0.3 * lowpass(highpass(noise(0.4), 300), 2500) * env(int(SR * 0.4), 0.05, None, 1, 0.1), 0.0)
    place(buf, 0.9 * thunk(0.3, 110) + 0.3 * ring(540, 0.4, 0.2)[:int(SR * 0.3)], 0.4)
    save('lever_heavy', buf); reg('lever_heavy', 'Operating handle swings')

    # signal-box lever: catch handle click, swing, heavy clack into the frame
    buf = np.zeros(int(SR * 1.1))
    place(buf, 0.5 * highpass(noise(0.02), 2500) * env(int(SR * 0.02), 0.0003, 0.005), 0.0)
    place(buf, 0.2 * lowpass(highpass(noise(0.45), 200), 1500) * env(int(SR * 0.45), 0.08, None, 1, 0.1), 0.05)
    place(buf, 1.0 * thunk(0.35, 95) + 0.4 * ring(480, 0.5, 0.25)[:int(SR * 0.35)], 0.55)
    place(buf, 0.4 * highpass(noise(0.02), 2500) * env(int(SR * 0.02), 0.0003, 0.005), 0.62)
    save('lever_clack', buf); reg('lever_clack', 'Signal lever thrown')

    # annunciator horn: two-tone industrial horn
    x = t(1.4)
    y = (np.sign(tone(x, 440)) + 0.8 * np.sign(tone(x, 554))) * env(len(x), 0.01, None, 1, 0.05)
    save('alarm_horn', lowpass(y, 3500), 0.6); reg('alarm_horn', 'Alarm horn')

    # acknowledge beep
    buf = np.zeros(int(SR * 0.35))
    for at in (0.0, 0.15):
        x = t(0.09)
        place(buf, tone(x, 1900) * env(len(x), 0.003, None, 1, 0.01), at)
    save('ack_beep', buf, 0.5); reg('ack_beep', 'Alarm acknowledged')

    # control-room telephone: double ring (bells struck at 20 Hz)
    buf = np.zeros(int(SR * 2.2))
    for at in (0.0, 0.6):
        x = t(0.4)
        strike = (np.sin(2 * np.pi * 20 * x) > 0).astype(float)
        place(buf, (tone(x, 1450) + 0.6 * tone(x, 1720)) * (0.5 + 0.5 * strike) * env(len(x), 0.005, None, 1, 0.05), at)
    save('phone_ring', buf, 0.6); reg('phone_ring', 'Phone rings')

    # level-crossing bell (electronic gong), three strikes
    buf = np.zeros(int(SR * 2.4))
    for i in range(3):
        place(buf, bell(1180, 0.8, 0.35), i * 0.75)
    save('crossing_bell', buf); reg('crossing_bell', 'Crossing bell rings')

    # point machine: motor run, then locking clunk
    buf = np.zeros(int(SR * 3.0))
    place(buf, motor(2.4, 140, 150, 0.3) + 0.1 * lowpass(noise(2.4), 600), 0.0)
    place(buf, 1.0 * thunk(0.35, 80), 2.45)
    place(buf, 0.4 * highpass(noise(0.02), 2500) * env(int(SR * 0.02), 0.0003, 0.005), 2.55)
    save('point_motor', buf); reg('point_motor', 'Points move')

    # contactor: sharp clack
    buf = np.zeros(int(SR * 0.25))
    place(buf, 0.8 * thunk(0.12, 300), 0.0)
    place(buf, 0.7 * highpass(noise(0.02), 2000) * env(int(SR * 0.02), 0.0003, 0.004), 0.0)
    save('contactor', buf); reg('contactor', 'Contactor clacks')

    # diesel start: starter whine, coughs, catch, rev to idle
    buf = np.zeros(int(SR * 3.5))
    place(buf, motor(1.2, 220, 300, 0.3), 0.0)
    for at in (0.6, 0.85, 1.05):
        place(buf, 0.8 * thunk(0.15, 50), at)
    x = t(2.2)
    f = 60 + 90 * (1 - np.exp(-x / 0.5))
    ph = 2 * np.pi * np.cumsum(f) / SR
    place(buf, (0.6 * np.sign(np.sin(ph)) * 0.5 + 0.3 * lowpass(noise(2.2), 500)) * env(len(x), 0.05, None, 1, 0.3), 1.2)
    save('diesel_start', lowpass(buf, 4000)); reg('diesel_start', 'Diesel engine starts')

    # diesel stop: rundown + last chuffs
    x = t(2.5)
    f = 150 * np.exp(-x / 0.7) + 8
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = (0.5 * np.sign(np.sin(ph)) * 0.6 + 0.2 * lowpass(noise(2.5), 400)) * np.exp(-x / 1.0)
    save('diesel_stop', lowpass(y, 3000)); reg('diesel_stop', 'Diesel engine stops')
