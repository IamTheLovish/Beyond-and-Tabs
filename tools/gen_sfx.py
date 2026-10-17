#!/usr/bin/env python3
"""Generates the in-game sound effects (bar/.../sounds/sfx/*.ogg) from nothing but maths.

Every sound here is ORIGINAL and procedurally synthesised: seeded noise through filters, modal (sum of decaying
partials) bells, wood and metal, FM and band-limited saw "brass", granular textures (bone rattles, leaf rustles,
bubbles, crackle) and a cheap noise-tail reverb. No game's audio - Total Annihilation, Beyond All Reason,
Subnautica or any other - was sampled, traced or used as a reference recording; nothing is loaded from disk.
The design language per faction is in design/audio.md.

Deterministic: every sound and variation has its own seed (crc32 of "key:variation"), so re-running reproduces the
same samples; the Vorbis encode runs with ffmpeg's bitexact flags so the .ogg bytes are stable for one encoder build.

Needs python3 + numpy + scipy and ffmpeg with libvorbis on PATH. Usage:
    python3 tools/gen_sfx.py                 # write the oggs and refresh the sfx.* entries of sounds.json + en_us.json
    python3 tools/gen_sfx.py --only shot_bow,hit_melee --wav-dir /tmp/wav    # audition a few as wav too
    python3 tools/gen_sfx.py --list          # print the table (key, category, variations, seconds)
Output: 44.1 kHz mono OGG Vorbis (positional sounds must be mono in Minecraft), loudness-matched per category,
peak <= -1 dBFS, DC removed, 5 ms fades. Budget: the whole sfx folder stays under 6 MB (checked at the end).

The Java side (sounds/Sfx.java) lists the same keys; the GameTest sounds_json_covers_every_registered_event checks
that every registered event has a sounds.json entry and every file it names ships.
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile
import zlib
from collections import OrderedDict

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether")
DEFAULT_OUT = os.path.join(ASSETS, "sounds", "sfx")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG_JSON = os.path.join(ASSETS, "lang", "en_us.json")
BUDGET_BYTES = 6_000_000

# loudness targets (RMS dBFS) per category: weapons sit under buildings, match moments on top
TARGET_RMS = {"units": -21.0, "buildings": -19.0, "abilities": -18.0, "announcer": -17.0, "ui": -20.0}


# ================================================================ building blocks

def n_of(d):
    return max(1, int(SR * d))


def t_axis(d):
    return np.arange(n_of(d)) / SR


def silence(d):
    return np.zeros(n_of(d))


def noise(rng, d):
    return rng.standard_normal(n_of(d))


def brown(rng, d):
    """Integrated white noise (6 dB/oct darker), DC-blocked: rumble, surf, wind body."""
    x = np.cumsum(rng.standard_normal(n_of(d)))
    x = signal.sosfilt(signal.butter(1, 20, "hp", fs=SR, output="sos"), x)
    return x / (np.max(np.abs(x)) + 1e-9)


def env_exp(d, tau, attack=0.002):
    t = t_axis(d)
    e = np.exp(-t / max(tau, 1e-4))
    a = max(1, int(SR * attack))
    e[:a] *= np.linspace(0, 1, a)
    return e


def env_ar(d, attack, release_start=None):
    """Linear attack to 1, hold, linear release from release_start to the end."""
    n = n_of(d)
    e = np.ones(n)
    a = max(1, int(SR * attack))
    e[:a] = np.linspace(0, 1, a)
    if release_start is not None:
        r0 = int(SR * release_start)
        if r0 < n:
            e[r0:] *= np.linspace(1, 0, n - r0)
    return e


def env_hann(d):
    return np.hanning(n_of(d))


def release(x, frac=0.25, max_s=0.08):
    """Raised-cosine fade over the end of x, so a layer cut short by its own length never ends in a click."""
    x = np.array(x, dtype=float)
    r = min(int(len(x) * frac), int(SR * max_s))
    if r > 2:
        x[-r:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, r))
    return x


def sine(freq, d, phase=0.0):
    n = n_of(d)
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)


def saw(freq, d, max_h=40):
    """Band-limited sawtooth by additive harmonics (frequency may sweep)."""
    n = n_of(d)
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    ph = 2 * np.pi * np.cumsum(f) / SR
    out = np.zeros(n)
    fmax = float(np.max(f))
    for k in range(1, max_h + 1):
        if k * fmax > SR * 0.45:
            break
        out += np.sin(k * ph) / k
    return out


def sweep(f0, f1, d, curve="exp"):
    n = n_of(d)
    if curve == "exp":
        return f0 * (f1 / f0) ** np.linspace(0, 1, n)
    return np.linspace(f0, f1, n)


def _sos(kind, f, order=2):
    nyq = SR / 2
    if kind == "band":
        lo, hi = max(10.0, f[0]), min(nyq * 0.95, f[1])
        return signal.butter(order, [lo, hi], "band", fs=SR, output="sos")
    return signal.butter(order, min(max(10.0, f), nyq * 0.95), kind, fs=SR, output="sos")


def lp(x, f, order=2):
    return signal.sosfilt(_sos("low", f, order), x)


def hp(x, f, order=2):
    return signal.sosfilt(_sos("high", f, order), x)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_sos("band", (lo, hi), order), x)


def moving_bp(x, centres, q=2.0, block=256):
    """Band-pass whose centre follows `centres` (one value per sample), done in blocks with the filter state carried
    across (an approximation that is inaudible at this block size)."""
    out = np.zeros_like(x)
    zi = None
    for i in range(0, len(x), block):
        c = float(np.mean(centres[i:i + block]))
        bw = c / q
        sos = _sos("band", (c - bw / 2, c + bw / 2), 1)
        if zi is None:
            zi = signal.sosfilt_zi(sos) * 0
        seg, zi = signal.sosfilt(sos, x[i:i + block], zi=zi)
        out[i:i + block] = seg
    return out


def modal(freqs, taus, amps, d, attack=0.0005, rng=None, detune=0.0):
    """Sum of exponentially decaying sine partials: bells, wood, metal, glass."""
    t = t_axis(d)
    out = np.zeros_like(t)
    for f, tau, a in zip(freqs, taus, amps):
        if f >= SR * 0.45:
            continue
        ph = rng.uniform(0, 2 * np.pi) if rng is not None else 0.0
        ff = f * (1 + (rng.uniform(-detune, detune) if rng is not None and detune else 0))
        out += a * np.sin(2 * np.pi * ff * t + ph) * np.exp(-t / tau)
    a = max(1, int(SR * attack))
    out[:a] *= np.linspace(0, 1, a)
    return release(out)


def bell(f0, d, rng, bright=1.0, decay=1.0):
    # church-bell-like partial set (hum, prime, tierce, quint, nominal, ...) - generic acoustics, not a sample
    ratios = [0.5, 1.0, 1.19, 1.5, 2.0, 2.52, 2.66, 3.0, 4.0, 5.33]
    taus = [3.2, 2.4, 1.8, 1.5, 1.2, 0.8, 0.7, 0.55, 0.4, 0.25]
    amps = [0.5, 1.0, 0.6, 0.35, 0.7 * bright, 0.3 * bright, 0.25 * bright, 0.2 * bright, 0.15 * bright, 0.1 * bright]
    return modal([f0 * r for r in ratios], [x * decay for x in taus], amps, d, rng=rng, detune=0.003)


def chime(f0, d, rng, decay=1.0):
    # tubular/glass chime: free-free bar modes (1, 2.76, 5.40, 8.93)
    return modal([f0, f0 * 2.76, f0 * 5.40, f0 * 8.93], [1.4 * decay, 0.6 * decay, 0.3 * decay, 0.15 * decay],
                 [1.0, 0.5, 0.25, 0.12], d, rng=rng, detune=0.002)


def wood(f0, d, rng, decay=1.0):
    # struck wooden bar/plank: few modes, fast decays, plus a click
    out = modal([f0, f0 * 2.57, f0 * 4.9, f0 * 7.6], [0.09 * decay, 0.05 * decay, 0.03 * decay, 0.02 * decay],
                [1.0, 0.6, 0.35, 0.2], d, rng=rng, detune=0.01)
    click = hp(noise(rng, 0.004), 2000) * np.linspace(1, 0, n_of(0.004))
    return place(out, click * 0.6, 0)


def metal(f0, d, rng, n=9, decay=1.0, spread=3.5):
    # inharmonic metal plate/sheet: random partials above f0
    freqs = sorted(f0 * (1 + rng.uniform(0, spread, n)))
    taus = [decay * rng.uniform(0.15, 0.6) / (1 + 0.3 * i) for i in range(n)]
    amps = [rng.uniform(0.4, 1.0) / (1 + 0.15 * i) for i in range(n)]
    return modal(freqs, taus, amps, d, rng=rng)


def drum(f0, d, rng, drop=1.8, tau=0.18, snap=0.3):
    """Membrane: sine with a fast pitch drop plus a filtered noise slap."""
    f = f0 + f0 * (drop - 1) * np.exp(-t_axis(d) / 0.03)
    body = sine(f, d) * env_exp(d, tau, 0.001)
    slap = lp(noise(rng, d), 1800) * env_exp(d, 0.02, 0.0005) * snap
    return release(body + slap)


def brass(f0, d, rng, bend=None, vib=5.0, vib_depth=0.008, bright=3000, attack=0.06):
    """Saw through an opening low-pass with gentle vibrato: horns, trumpets, war-horn blasts."""
    n = n_of(d)
    t = t_axis(d)
    f = np.full(n, float(f0)) if bend is None else np.asarray(bend, dtype=float)[:n]
    f = f * (1 + vib_depth * np.sin(2 * np.pi * vib * t + rng.uniform(0, 6.28)) * np.clip(t / 0.25, 0, 1))
    x = saw(f, d, 30)
    e = env_ar(d, attack, d * 0.7)
    # crude time-varying brightness: mix a dark and a bright copy by the envelope
    dark, light = lp(x, bright * 0.25), lp(x, bright)
    return (dark * (1 - e) + light * e) * e


def whoosh(rng, d, f0, f1, q=1.5):
    x = moving_bp(noise(rng, d), sweep(f0, f1, d), q)
    return x * env_hann(d)


def crackle(rng, d, density=200, lo=1500, hi=9000):
    """Sparse random impulses through a band-pass: fire, static, sparks."""
    n = n_of(d)
    x = np.zeros(n)
    k = rng.poisson(density * d)
    idx = rng.integers(0, n, k)
    x[idx] = rng.uniform(-1, 1, k) * rng.uniform(0.3, 1, k)
    return bp(x, lo, hi)


def grains(rng, d, n_grains, grain_fn, spread=None):
    """Scatter n grains (each from grain_fn(rng)) over d seconds."""
    out = silence(d)
    for _ in range(n_grains):
        g = grain_fn(rng)
        t0 = rng.uniform(0, d if spread is None else spread)
        out = place(out, g, t0)
    return out


def bone_tick(rng):
    f = rng.uniform(1600, 3800)
    return modal([f, f * 1.7, f * 2.9], [0.012, 0.008, 0.005], [1, 0.5, 0.3], 0.03, rng=rng) * rng.uniform(0.4, 1)


def leaf_grain(rng):
    # a dry leaf: a band of noise, not a hiss (kept under ~6 kHz so a forest of them stays soft)
    lo = rng.uniform(1200, 2500)
    g = bp(noise(rng, 0.03), lo, lo * 2.4) * np.hanning(n_of(0.03))
    return g * rng.uniform(0.2, 0.6)


def bubble(rng):
    d = rng.uniform(0.02, 0.06)
    f0 = rng.uniform(350, 1400)
    return sine(sweep(f0, f0 * 1.8, d), d) * env_exp(d, d / 3, 0.001) * rng.uniform(0.3, 1)


def gear_tick(rng):
    f = rng.uniform(2200, 4200)
    return modal([f, f * 1.48, f * 2.3], [0.01, 0.007, 0.004], [1, 0.6, 0.3], 0.025, rng=rng) * rng.uniform(0.6, 1)


def ratchet(rng, d, rate0, rate1):
    """Regular clicks whose rate glides from rate0 to rate1 (gears spinning up/down)."""
    out = silence(d)
    t = 0.0
    while t < d - 0.03:
        out = place(out, gear_tick(rng), t)
        r = rate0 + (rate1 - rate0) * (t / d)
        t += 1.0 / max(r, 1.0)
    return out


def steam(rng, d, attack=0.01, tau=0.3):
    x = bp(noise(rng, d), 1800, 7000, 2) + 0.5 * bp(noise(rng, d), 700, 1800)
    return x * env_exp(d, tau, attack)


def creak(rng, d, f0=40, f1=55, formants=(420, 950, 1900)):
    """Wood/rope creak: a jittery pulse train (stick-slip) through a few resonances."""
    n = n_of(d)
    rate = sweep(f0, f1, d, "lin") * (1 + 0.25 * lp(rng.standard_normal(n), 8) * 10)
    rate = np.clip(rate, 5, 400)
    ph = np.cumsum(rate) / SR
    pulses = np.zeros(n)
    edges = np.nonzero(np.diff(np.floor(ph)) > 0)[0]
    pulses[edges] = rng.uniform(0.5, 1, len(edges))
    out = np.zeros(n)
    for fc in formants:
        out += bp(pulses, fc * 0.85, fc * 1.15, 2)
    return out * env_hann(d)


def whisper(rng, d, vowels=((700, 1200), (400, 2200), (500, 900)), syllables=3):
    """Breathy formant noise with syllable-like swells: a voice without words (Gravebound)."""
    n = n_of(d)
    x = noise(rng, d)
    seg = n // len(vowels)
    out = np.zeros(n)
    for i, (f1, f2) in enumerate(vowels):
        a, b = i * seg, n if i == len(vowels) - 1 else (i + 1) * seg
        part = bp(x, f1 * 0.8, f1 * 1.2) + 0.7 * bp(x, f2 * 0.85, f2 * 1.15) + 0.2 * hp(x, 4000)
        w = np.hanning(b - a)
        out[a:b] += part[a:b] * w
    syl = 0.5 + 0.5 * np.abs(np.sin(np.pi * syllables * np.arange(n) / n))
    return out * syl


def bird(rng, d, f0=2800):
    """A short song of FM chirps (Verdant): no species, just a shape."""
    out = silence(d)
    t = rng.uniform(0, 0.05)
    while t < d - 0.12:
        cd = rng.uniform(0.04, 0.1)
        fa = f0 * rng.uniform(0.8, 1.3)
        fb = fa * rng.uniform(0.6, 1.5)
        f = sweep(fa, fb, cd) * (1 + 0.04 * np.sin(2 * np.pi * rng.uniform(25, 45) * t_axis(cd)))
        out = place(out, sine(f, cd) * np.hanning(n_of(cd)) * rng.uniform(0.5, 1), t)
        t += cd + rng.uniform(0.02, 0.12)
    return out


def wind(rng, d, lo=200, hi=900):
    x = bp(noise(rng, d), lo, hi)
    lfo = 0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(0.3, 0.8) * t_axis(d) + rng.uniform(0, 6))
    return x * lfo * env_hann(d)


def thunder(rng, d, crack=True):
    body = lp(brown(rng, d), 180) * 1.5
    bumps = lp(np.abs(rng.standard_normal(n_of(d))), 6)
    bumps /= np.max(bumps) + 1e-9
    out = body * (0.3 + bumps) * env_exp(d, d / 2.5, 0.05)
    if crack:
        c = hp(noise(rng, 0.25), 1200) * env_exp(0.25, 0.05, 0.001)
        out = place(out, c * 0.8, 0)
    return out


def splash(rng, d):
    body = bp(noise(rng, d), 400, 5000) * env_exp(d, d / 5, 0.004)
    bub = grains(rng, d, int(30 * d), bubble, spread=d * 0.8)
    return body * 0.8 + bub * 0.6


def rumble(rng, d, tau=None):
    out = lp(brown(rng, d), 140) * env_exp(d, tau or d / 3, 0.02)
    crumble = grains(rng, d, int(40 * d), lambda r: lp(noise(r, 0.05), r.uniform(600, 2000)) *
                     np.hanning(n_of(0.05)) * r.uniform(0.2, 0.6), spread=d * 0.7)
    return out + crumble * 0.5


def boom(rng, d, f0=55, tau=0.5):
    """Explosion body: pitched thump + low noise + crackle tail."""
    thump = sine(sweep(f0 * 2.2, f0, 0.25), 0.25) * env_exp(0.25, 0.12, 0.001)
    body = lp(noise(rng, d), 400) * env_exp(d, tau, 0.003)
    tail = crackle(rng, d, 120, 800, 5000) * env_exp(d, tau * 0.6, 0.01) * 0.5
    return place(body + tail, thump * 1.2, 0)


def reverb(x, rng, rt=0.8, wet=0.25, tone=4000):
    """Exponentially decaying filtered-noise impulse response: a small hall that belongs to no one."""
    d = rt * 1.2
    ir = lp(noise(rng, d), tone) * np.exp(-t_axis(d) * 6.9 / rt)
    ir[:int(SR * 0.012)] *= np.linspace(0, 1, int(SR * 0.012))
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    w = signal.fftconvolve(x, ir)[:len(x) + len(ir) // 2]
    dry = np.concatenate([x, np.zeros(len(w) - len(x))])
    return dry * (1 - wet) + w * wet * 1.5


def place(buf, x, t0):
    """Adds x into buf at t0 seconds, growing buf if x runs past its end."""
    i = int(SR * t0)
    need = i + len(x)
    if need > len(buf):
        buf = np.concatenate([buf, np.zeros(need - len(buf))])
    buf = buf.copy()
    buf[i:i + len(x)] += x
    return buf


def mix(*parts):
    """mix((x, t0, gain), ...) -> one buffer."""
    out = np.zeros(1)
    for x, t0, g in parts:
        out = place(out, x * g, t0)
    return out


def finish(x, category, trim_db=-48):
    x = x - np.mean(x)
    # trim trailing near-silence
    thr = np.max(np.abs(x)) * 10 ** (trim_db / 20)
    nz = np.nonzero(np.abs(x) > thr)[0]
    if len(nz):
        x = x[:min(len(x), nz[-1] + int(SR * 0.02))]
    f = min(len(x) // 4, int(SR * 0.003))
    if f > 1:
        x[:f] *= np.linspace(0, 1, f)
    x = release(x, 0.15, 0.06)
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    x = x * (10 ** (TARGET_RMS[category] / 20) / rms)
    peak = np.max(np.abs(x))
    lim = 10 ** (-1 / 20)
    if peak > lim:
        x *= lim / peak
    return x


class Sig(np.ndarray):
    """A 1-D signal whose + and - pad the shorter operand with silence, so layers of different lengths simply stack
    (multiplication stays strict: an envelope of the wrong length is a real bug)."""

    def _pad(self, other, op):
        a = np.asarray(self)
        if isinstance(other, np.ndarray) and other.ndim == 1 and a.ndim == 1 and len(other) != len(a):
            n = max(len(a), len(other))
            a = np.pad(a, (0, n - len(a)))
            other = np.pad(np.asarray(other), (0, n - len(other)))
        return op(a, np.asarray(other) if isinstance(other, np.ndarray) else other).view(Sig)

    def __add__(self, o):
        return self._pad(o, np.add)

    __radd__ = __add__

    def __sub__(self, o):
        return self._pad(o, np.subtract)

    def __rsub__(self, o):
        return self._pad(o, lambda a, b: np.subtract(b, a))


def _sig(fn):
    def wrapped(*a, **k):
        r = fn(*a, **k)
        return r.view(Sig) if isinstance(r, np.ndarray) else r
    wrapped.__name__ = fn.__name__
    return wrapped


for _name in ["release", "silence", "noise", "brown", "env_exp", "env_ar", "env_hann", "sine", "saw", "lp", "hp", "bp",
              "moving_bp", "modal", "bell", "chime", "wood", "metal", "drum", "brass", "whoosh", "crackle", "grains",
              "bone_tick", "leaf_grain", "bubble", "gear_tick", "ratchet", "steam", "creak", "whisper", "bird", "wind",
              "thunder", "splash", "rumble", "boom", "reverb", "place", "mix"]:
    globals()[_name] = _sig(globals()[_name])


# ================================================================ motifs per faction (design/audio.md)
# Each faction has a "voice": an instrument family and a scale. Built/destroyed/stingers reuse it so the same
# faction sounds like itself everywhere.

def note_hz(semitones_from_a4):
    return 440.0 * 2 ** (semitones_from_a4 / 12)


def motif_sunforged(rng, scale=1.0, short=False):
    # bells and brass: a bright major fanfare (root, third, fifth, octave) over a bell
    root = note_hz(-9)  # C4
    notes = [0, 4, 7, 12] if not short else [0, 7, 12]
    out = silence(0.1)
    step = 0.16 * scale
    for i, s in enumerate(notes):
        d = 0.5 if i < len(notes) - 1 else 1.1
        out = place(out, brass(root * 2 ** (s / 12), d * scale, rng, bright=4200) * 0.5, i * step)
    out = place(out, bell(root * 2, 2.2 * scale, rng) * 0.6, len(notes) * step - step)
    return reverb(out, rng, 1.2, 0.3)


def motif_gravebound(rng, scale=1.0, short=False):
    # bone and whispers: a low minor bell toll, a breathy choir of no words, a rattle of bones
    root = note_hz(-21)  # C3
    out = bell(root, 2.6 * scale, rng, bright=0.5, decay=1.2) * 0.9
    out = place(out, bell(root * 2 ** (3 / 12), 2.0 * scale, rng, bright=0.4) * 0.4, 0.45 * scale)
    out = place(out, whisper(rng, 1.6 * scale) * 0.35, 0.15)
    if not short:
        out = place(out, grains(rng, 0.6, 18, bone_tick) * 0.5, 1.0 * scale)
    return reverb(out, rng, 1.8, 0.4, 2500)


def motif_horde(rng, scale=1.0, short=False):
    # drums and iron: a war-drum figure, an anvil strike, a low horn on a fifth
    hits = [0, 0.18, 0.36, 0.45] if not short else [0, 0.2, 0.3]
    out = silence(0.1)
    for i, h in enumerate(hits):
        out = place(out, drum(70 if i % 2 == 0 else 95, 0.5, rng, snap=0.5), h * scale)
    out = place(out, metal(320, 0.9, rng, decay=1.4) * 0.35, hits[-1] * scale)
    root = note_hz(-26)  # G2
    out = place(out, brass(root, 1.1 * scale, rng, bright=1600, vib_depth=0.012) * 0.45, hits[-1] * scale)
    out = place(out, brass(root * 1.5, 1.0 * scale, rng, bright=1600) * 0.3, hits[-1] * scale + 0.02)
    return reverb(out, rng, 0.9, 0.25, 3000)


def motif_verdant(rng, scale=1.0, short=False):
    # wood, wind and birds: a pentatonic marimba run up, wind through it, a bird answers
    root = note_hz(-14)  # G3
    pent = [0, 2, 4, 7, 9, 12] if not short else [0, 4, 7, 12]
    out = wind(rng, 1.8 * scale) * 0.25
    for i, s in enumerate(pent):
        f = root * 2 ** (s / 12)
        out = place(out, wood(f, 0.6, rng, decay=3.5) * 0.7, 0.05 + i * 0.11 * scale)
    out = place(out, bird(rng, 0.7) * 0.25, (len(pent) * 0.11 + 0.1) * scale)
    return reverb(out, rng, 1.0, 0.25, 6000)


def motif_tide(rng, scale=1.0, short=False):
    # waves and creaks: a swell of surf, a ship's bell rung twice, a hull creak under a low dorian horn
    d = 2.2 * scale
    surf = lp(brown(rng, d), 900) * np.sin(np.pi * np.linspace(0, 1, n_of(d))) ** 2
    out = surf * 0.6
    sb = note_hz(-2)  # G4 ship's bell
    out = place(out, bell(sb, 1.4, rng, bright=1.3, decay=0.6) * 0.45, 0.25 * scale)
    out = place(out, bell(sb, 1.2, rng, bright=1.3, decay=0.6) * 0.35, 0.55 * scale)
    if not short:
        out = place(out, creak(rng, 0.6, 30, 48) * 0.5, 0.9 * scale)
        root = note_hz(-26)
        out = place(out, brass(root, 1.2 * scale, rng, bright=1200) * 0.3, 0.8 * scale)
        out = place(out, brass(root * 2 ** (5 / 12), 1.0 * scale, rng, bright=1200) * 0.2, 1.0 * scale)
    return reverb(out, rng, 1.4, 0.3, 3500)


def motif_storm(rng, scale=1.0, short=False):
    # chimes and thunder: a glassy arpeggio (lydian, the raised fourth gives it the air), then distant thunder
    root = note_hz(3)  # C5
    steps = [0, 6, 7, 11, 12] if not short else [0, 7, 12]
    out = silence(0.1)
    for i, s in enumerate(steps):
        out = place(out, chime(root * 2 ** (s / 12), 1.6, rng) * 0.5, i * 0.09 * scale)
    out = place(out, thunder(rng, 1.8 * scale, crack=not short) * 0.6, (len(steps) * 0.09 + 0.15) * scale)
    out = place(out, crackle(rng, 0.4, 300) * 0.25, 0.0)
    return reverb(out, rng, 1.6, 0.3, 7000)


def motif_brass(rng, scale=1.0, short=False):
    # steam and gears: a two-tone steam whistle, a ratchet spinning up, a heavy clank and a hiss
    d = 0.7 * scale
    whistle = (sine(note_hz(5), d) + 0.6 * sine(note_hz(9), d) + 0.2 * sine(note_hz(17), d)) * env_ar(d, 0.04, d * 0.7)
    whistle = whistle * 0.6 + bp(noise(rng, d), 1500, 5000) * env_ar(d, 0.02, d * 0.6) * 0.15
    out = ratchet(rng, 0.6 * scale, 8, 30) * 0.5
    out = place(out, whistle * 0.6, 0.35 * scale)
    out = place(out, metal(180, 0.8, rng, decay=1.2) * 0.4 + drum(60, 0.5, rng, snap=0.2) * 0.6, 0.35 * scale)
    if not short:
        out = place(out, steam(rng, 0.9, 0.12, 0.3) * 0.12, 0.95 * scale)
    return reverb(out, rng, 0.9, 0.2, 5000)


MOTIFS = OrderedDict([
    ("sunforged", motif_sunforged), ("gravebound", motif_gravebound), ("horde", motif_horde),
    ("verdant", motif_verdant), ("tide", motif_tide), ("storm", motif_storm), ("brass", motif_brass),
])


# ================================================================ the sounds

# ---- units: weapon classes (BarFx SHOT/IMPACT kinds)

def s_shot_bow(rng, v):
    d = 0.35
    f = 120 * (1 + 0.06 * v)
    twang = sine(sweep(f * 1.4, f, 0.25), 0.25) * env_exp(0.25, 0.05, 0.001)
    string = (sine(f * 3.02, 0.2) * 0.3 + sine(f * 5.1, 0.2) * 0.15) * env_exp(0.2, 0.03)
    thwip = whoosh(rng, 0.14, 4000, 1500, 2.0)
    return mix((twang, 0, 0.8), (string, 0, 1.0), (thwip, 0.01, 0.7), (wood(900, 0.05, rng), 0, 0.3), (silence(d), 0, 0))


def s_shot_gun(rng, v):
    d = 0.6
    crack = hp(noise(rng, 0.05), 1500) * env_exp(0.05, 0.008, 0.0003)
    bang = lp(noise(rng, 0.35), 900) * env_exp(0.35, 0.07, 0.001)
    thump = sine(sweep(130, 55, 0.2), 0.2) * env_exp(0.2, 0.06, 0.001)
    x = mix((crack, 0, 1.0), (bang, 0, 0.9), (thump, 0, 0.7), (silence(d), 0, 0))
    return reverb(x, rng, 0.5, 0.25, 3000)


def s_shot_fire(rng, v):
    d = 0.55
    x = whoosh(rng, d, 250 + 40 * v, 1300, 1.2)
    roar = lp(noise(rng, d), 300) * env_hann(d)
    return x * 0.9 + roar * 0.6 + crackle(rng, d, 250, 1200, 6000) * env_hann(d) * 0.5


def s_shot_heavy(rng, v):
    d = 0.9
    x = boom(rng, 0.6, 45 + 5 * v, 0.12)
    whump = lp(noise(rng, 0.2), 250) * env_exp(0.2, 0.05)
    x = place(x, whump, 0)
    x = place(x, whoosh(rng, 0.5, 900, 300, 1.5) * 0.25, 0.08)
    return reverb(place(x, silence(d), 0), rng, 0.9, 0.3, 2000)


def s_shot_magic(rng, v):
    d = 0.45
    t = t_axis(d)
    fc = sweep(1300 * (1 + 0.08 * v), 650, d)
    mod = np.sin(2 * np.pi * np.cumsum(fc * 1.41) / SR) * 2.5 * env_exp(d, 0.12)
    zap = np.sin(2 * np.pi * np.cumsum(fc) / SR + mod) * env_exp(d, 0.12, 0.003)
    glass = chime(2600 + 200 * v, d, rng, 0.25) * 0.3
    sparkle = grains(rng, d, 10, lambda r: sine(r.uniform(4000, 8000), 0.02) * np.hanning(n_of(0.02)) * 0.3, d * 0.6)
    return zap * 0.8 + glass + sparkle + 0 * t


def s_shot_rocket(rng, v):
    d = 0.7
    pop = lp(noise(rng, 0.06), 1500) * env_exp(0.06, 0.015)
    hiss = moving_bp(noise(rng, d), sweep(700, 3200 + 300 * v, d), 1.2) * env_ar(d, 0.03, 0.3)
    return mix((pop, 0, 0.9), (hiss, 0.01, 0.8), (hp(noise(rng, d), 5000) * env_exp(d, 0.2), 0, 0.2))


def s_shot_thrown(rng, v):
    return whoosh(rng, 0.25, 600 + 100 * v, 2200, 1.4)


def s_hit_melee(rng, v):
    d = 0.4
    clash = metal(1800 + 250 * v, d, rng, n=10, decay=0.7, spread=2.5)
    scrape = hp(noise(rng, 0.08), 3000) * env_exp(0.08, 0.02)
    return clash * 0.8 + place(scrape, silence(d), 0) * 0.5


def s_hit_heavy(rng, v):
    d = 0.45
    thud = drum(85 - 8 * v, d, rng, drop=1.5, tau=0.1, snap=0.6)
    crunch = bp(noise(rng, 0.1), 500, 2500) * env_exp(0.1, 0.025)
    return mix((thud, 0, 0.9), (crunch, 0.005, 0.4), (grains(rng, d, 4, bone_tick, 0.08), 0, 0.25))


def s_hit_building(rng, v):
    d = 0.5
    knock = wood(170 + 30 * v, d, rng, decay=1.3)
    grit = grains(rng, d, 8, lambda r: lp(noise(r, 0.03), r.uniform(800, 2500)) * np.hanning(n_of(0.03)) * 0.5, 0.25)
    return knock * 0.9 + grit * 0.5 + lp(noise(rng, d), 300) * env_exp(d, 0.05) * 0.4


# ---- units: deaths per faction (BarFx DEATH faction field)

def s_death_sunforged(rng, v):
    d = 0.9
    clang = metal(520 + 60 * v, d, rng, decay=1.2)
    ring = bell(note_hz(-9 + 7 * v), d, rng, bright=1.2, decay=0.4) * 0.4
    clatter = grains(rng, 0.4, 7, lambda r: metal(r.uniform(1500, 3000), 0.08, r, 4, 0.2) * 0.4, 0.35)
    return mix((clang, 0, 0.6), (ring, 0, 1.0), (clatter, 0.08, 1.0))


def s_death_gravebound(rng, v):
    d = 0.9
    rattle = grains(rng, 0.5, 22, bone_tick, 0.45)
    breath = whisper(rng, 0.8, ((600, 1000), (450, 800)), 1) * env_exp(0.8, 0.35)
    return mix((rattle, 0, 0.8), (breath, 0.05, 0.6), (silence(d), 0, 0))


def s_death_horde(rng, v):
    d = 0.8
    hit = drum(65 + 10 * v, 0.6, rng, snap=0.6)
    iron = grains(rng, 0.4, 9, lambda r: metal(r.uniform(900, 2200), 0.07, r, 4, 0.25) * 0.5, 0.3)
    return mix((hit, 0, 0.9), (iron, 0.03, 0.6), (metal(260, 0.6, rng, decay=0.8), 0.02, 0.25), (silence(d), 0, 0))


def s_death_verdant(rng, v):
    d = 0.8
    snap = hp(noise(rng, 0.03), 800) * env_exp(0.03, 0.006, 0.0003)
    crack = wood(260 + 40 * v, 0.4, rng, decay=1.6)
    rustle = grains(rng, 0.6, 30, leaf_grain, 0.5)
    return mix((snap, 0, 1.0), (crack, 0, 0.8), (rustle, 0.03, 0.6), (silence(d), 0, 0))


def s_death_tide(rng, v):
    d = 0.9
    return mix((splash(rng, 0.6), 0, 0.8), (creak(rng, 0.5, 34 + 6 * v, 26), 0.15, 0.5), (silence(d), 0, 0))


def s_death_storm(rng, v):
    d = 0.8
    zap = crackle(rng, 0.25, 900, 2000, 10000) * env_exp(0.25, 0.08)
    pop = thunder(rng, 0.6, crack=True) * 0.6
    fall = chime(note_hz(10 - 2 * v), 0.6, rng, 0.4) * 0.3
    return mix((zap, 0, 0.9), (pop, 0.02, 0.8), (fall, 0.05, 1.0), (silence(d), 0, 0))


def s_death_brass(rng, v):
    d = 0.9
    burst = steam(rng, 0.6, 0.003, 0.2)
    gears = ratchet(rng, 0.6, 35 + 5 * v, 6)
    clank = metal(240, 0.5, rng, decay=0.8)
    return mix((burst, 0, 0.6), (gears, 0.02, 0.6), (clank, 0, 0.5), (silence(d), 0, 0))


def s_death_large(rng, v):
    d = 1.8
    x = boom(rng, d, 38 + 4 * v, 0.7)
    x = place(x, rumble(rng, 1.4) * 0.6, 0.1)
    return reverb(x, rng, 1.4, 0.3, 1800)


# ---- buildings

def make_built(faction):
    def f(rng, v):
        x = MOTIFS[faction](rng, 0.8, short=True)
        thunk = drum(90, 0.3, rng, snap=0.2) * 0.4
        return place(x, thunk, 0)
    return f


def make_destroyed(faction):
    tails = {
        "sunforged": lambda r: bell(note_hz(-21), 1.6, r, bright=0.7, decay=0.5) * 0.4 + metal(400, 1.0, r) * 0.3,
        "gravebound": lambda r: grains(r, 0.9, 40, bone_tick, 0.8) * 0.7 + whisper(r, 1.2, ((500, 900), (350, 700)), 1) * 0.3,
        "horde": lambda r: drum(55, 0.8, r, snap=0.8) + grains(r, 0.6, 12, lambda q: metal(q.uniform(700, 2000), 0.12, q, 4, 0.4) * 0.5, 0.5),
        "verdant": lambda r: wood(140, 0.8, r, decay=2.5) * 0.8 + grains(r, 1.0, 50, leaf_grain, 0.9) * 0.7,
        "tide": lambda r: splash(r, 1.0) * 0.7 + creak(r, 0.9, 22, 14) * 0.6,
        "storm": lambda r: thunder(r, 1.4) * 0.8,
        "brass": lambda r: steam(r, 1.2, 0.005, 0.5) * 0.5 + ratchet(r, 1.0, 30, 4) * 0.6 + metal(150, 1.0, r) * 0.3,
    }

    def f(rng, v):
        d = 2.2
        x = rumble(rng, d, 0.8)
        x = place(x, boom(rng, 0.8, 40, 0.3) * 0.7, 0)
        x = place(x, tails[faction](rng), 0.12)
        return reverb(x, rng, 1.2, 0.3, 2500)
    return f


def make_nano(faction):
    # one granule of the construction "loop" (played every ~0.6 s near busy builders): quiet, faction-flavoured
    def f(rng, v):
        d = 0.45
        if faction == "sunforged":
            x = metal(900 + 80 * v, 0.3, rng, 6, 0.5) * 0.5 + chime(note_hz(15 + 2 * v), 0.4, rng, 0.3) * 0.25
        elif faction == "gravebound":
            x = grains(rng, 0.3, 6, bone_tick, 0.25) * 0.6 + whisper(rng, 0.4, ((500, 900),), 1) * 0.25
        elif faction == "horde":
            x = metal(600 + 60 * v, 0.3, rng, 7, 0.6) * 0.5 + drum(120, 0.15, rng, snap=0.4) * 0.4
        elif faction == "verdant":
            x = creak(rng, 0.35, 70 + 10 * v, 90, (600, 1300, 2600)) * 0.4 + grains(rng, 0.35, 10, leaf_grain, 0.3) * 0.5
        elif faction == "tide":
            x = creak(rng, 0.35, 45 + 8 * v, 35) * 0.5 + grains(rng, 0.3, 4, bubble, 0.25) * 0.5
        elif faction == "storm":
            hum = (sine(120, d) + 0.5 * sine(240, d) + 0.3 * sine(360, d)) * env_hann(d) * 0.3
            x = hum + crackle(rng, d, 120, 3000, 9000) * env_hann(d) * 0.6
        else:  # brass
            x = ratchet(rng, 0.3, 22 + 4 * v, 26) * 0.5 + steam(rng, 0.25, 0.01, 0.08) * 0.2
        return place(x, silence(d), 0)
    return f


def s_reclaim_tick(rng, v):
    d = 0.5
    shimmer = moving_bp(noise(rng, d), sweep(2500 + 300 * v, 6000, d), 6) * env_hann(d)
    crumble = grains(rng, d, 10, lambda r: metal(r.uniform(1200, 2600), 0.05, r, 3, 0.15) * 0.4, 0.4)
    return shimmer * 0.6 + crumble * 0.6


def s_reclaim_done(rng, v):
    d = 0.8
    ching = metal(1500, 0.6, rng, 6, 0.8) * 0.4 + chime(note_hz(10), 0.7, rng, 0.5) * 0.4
    rise = moving_bp(noise(rng, 0.5), sweep(1500, 7000, 0.5), 5) * env_hann(0.5) * 0.4
    return mix((rise, 0, 1), (ching, 0.35, 1), (silence(d), 0, 0))


def s_gate_open(rng, v):
    d = 1.3
    x = creak(rng, 1.0, 26, 44, (300, 700, 1500)) * 0.7
    chain = grains(rng, 0.9, 14, lambda r: metal(r.uniform(1800, 3200), 0.04, r, 3, 0.12) * 0.5, 0.8)
    latch = wood(110, 0.4, rng, 1.6) + drum(70, 0.3, rng, snap=0.3) * 0.6
    return mix((x, 0, 1.0), (chain, 0.05, 0.7), (latch, 1.0, 0.8))


def s_gate_close(rng, v):
    d = 1.1
    x = creak(rng, 0.7, 40, 30, (300, 700, 1500)) * 0.6
    slam = wood(95, 0.5, rng, 2.0) + drum(60, 0.4, rng, snap=0.5) * 0.8 + lp(noise(rng, 0.3), 500) * env_exp(0.3, 0.06) * 0.5
    return reverb(mix((x, 0, 1.0), (slam, 0.6, 1.0), (silence(d), 0, 0)), rng, 0.7, 0.2, 2500)


def s_trap_spikes(rng, v):
    snap = hp(noise(rng, 0.02), 1500) * env_exp(0.02, 0.004, 0.0002)
    shing = metal(2400, 0.5, rng, 8, 0.6, 1.5)
    thunk = wood(150, 0.3, rng)
    return mix((snap, 0, 1.0), (thunk, 0, 0.7), (shing, 0.01, 0.6))


def s_trap_snare(rng, v):
    whip = whoosh(rng, 0.2, 700, 2600, 2.5) * 0.6
    vines = grains(rng, 0.4, 25, leaf_grain, 0.35)
    tight = creak(rng, 0.45, 60, 120, (500, 1100, 2300))
    return mix((whip, 0, 0.9), (vines, 0.05, 0.6), (tight, 0.12, 0.7))


def s_trap_mine(rng, v):
    click = metal(3000, 0.05, rng, 3, 0.1) * 0.6
    x = boom(rng, 1.1, 42, 0.35)
    return reverb(mix((click, 0, 1.0), (x, 0.08, 1.0)), rng, 0.9, 0.25, 2000)


# ---- abilities / hero / beasts

def s_possess_enter(rng, v):
    d = 1.1
    rise = whoosh(rng, 0.8, 200, 2500, 1.4)
    beat = drum(50, 0.35, rng, snap=0.05) * 0.9
    shim = chime(note_hz(8), 0.8, rng, 0.6) * 0.3 + chime(note_hz(15), 0.8, rng, 0.6) * 0.2
    return reverb(mix((rise, 0, 0.8), (beat, 0.75, 1.0), (shim, 0.8, 1.0), (silence(d), 0, 0)), rng, 1.0, 0.3)


def s_possess_exit(rng, v):
    d = 0.9
    fall = whoosh(rng, 0.7, 2500, 250, 1.4)
    breath = whisper(rng, 0.6, ((700, 1100),), 1) * 0.3
    return reverb(mix((fall, 0, 0.9), (breath, 0.15, 1.0), (silence(d), 0, 0)), rng, 0.9, 0.3)


def s_hero_boon(rng, v):
    root = note_hz(-2)
    out = silence(0.1)
    for i, s in enumerate([0, 4, 7, 12, 16]):
        out = place(out, bell(root * 2 ** (s / 12), 1.2, rng, bright=1.4, decay=0.5) * 0.4, i * 0.08)
    out = place(out, grains(rng, 0.8, 24, lambda r: sine(r.uniform(3000, 9000), 0.03) * np.hanning(n_of(0.03)) * 0.25, 0.7), 0.1)
    return reverb(out, rng, 1.3, 0.35, 8000)


def s_beast_trumpet(rng, v):
    d = 1.3
    f = sweep(260, 420, d, "lin") * (1 + 0.04 * np.sin(2 * np.pi * 7 * t_axis(d)))
    horn = brass(300, d, rng, bend=f, bright=3500, attack=0.08, vib_depth=0.02)
    rasp = bp(noise(rng, d), 600, 2400) * env_ar(d, 0.1, d * 0.6) * 0.25
    return reverb(horn * 0.8 + rasp, rng, 1.0, 0.25, 4000)


def s_beast_charge(rng, v):
    d = 1.5
    f = sweep(75, 60, 0.9)
    roar = brass(70, 0.9, rng, bend=f, bright=900, attack=0.05, vib=11, vib_depth=0.03)
    roar = roar + bp(noise(rng, 0.9), 200, 900) * env_ar(0.9, 0.05, 0.5) * 0.5
    out = roar * 0.8
    t = 0.3
    gap = 0.22
    while t < d - 0.2:
        out = place(out, drum(48, 0.25, rng, drop=1.3, tau=0.07, snap=0.5) * 0.8, t)
        t += gap
        gap = max(0.09, gap * 0.85)
    return out


def s_beast_breach(rng, v):
    d = 1.1
    splinter = grains(rng, 0.5, 20, lambda r: wood(r.uniform(300, 900), 0.08, r, 0.6) * 0.5, 0.35)
    x = mix((boom(rng, 0.8, 50, 0.25), 0, 0.8), (splinter, 0, 0.8), (rumble(rng, 0.9), 0.05, 0.5))
    return reverb(place(x, silence(d), 0), rng, 0.8, 0.25, 2500)


def s_beast_devour(rng, v):
    d = 1.0
    grind = grains(rng, 0.8, 45, bone_tick, 0.75)
    growl = brass(55, 0.9, rng, bright=500, vib=14, vib_depth=0.05) * 0.6
    return mix((growl, 0, 0.8), (grind, 0.05, 0.8), (silence(d), 0, 0))


def s_beast_embark(rng, v):
    d = 1.3
    f = sweep(95, 80, 0.9)
    bellow = brass(90, 0.9, rng, bend=f, bright=700, attack=0.12, vib=5, vib_depth=0.02)
    return mix((bellow, 0, 0.7), (grains(rng, 0.8, 35, leaf_grain, 0.7), 0.3, 0.6), (wind(rng, 1.0), 0.2, 0.3))


# ---- game enders (design/big_units.md): global moments, so they lean on the "announcer" category

def s_gameender_start(rng, v):
    d = 3.2
    root = note_hz(-33)  # C2
    toll = bell(root * 2, d, rng, bright=0.6, decay=1.3)
    drone = (saw(root, d, 20) + saw(root * 1.006, d, 20) + 0.5 * saw(root * 1.5, d, 20)) * env_ar(d, 1.2, d * 0.7)
    drone = lp(drone, 600) * 0.25
    return reverb(toll * 0.8 + drone, rng, 2.2, 0.4, 2500)


def s_gameender_stage(rng, v):
    d = 1.1
    fc = sweep(180, 720, 0.8)
    mod = np.sin(2 * np.pi * np.cumsum(fc * 2) / SR) * 1.5
    charge = np.sin(2 * np.pi * np.cumsum(fc) / SR + mod) * env_ar(0.8, 0.3, 0.6)
    ping = bell(note_hz(-9), 1.0, rng, bright=1.2, decay=0.4) * 0.5
    return reverb(mix((charge, 0, 0.5), (ping, 0.75, 1.0), (silence(d), 0, 0)), rng, 1.2, 0.3)


def s_gameender_aim(rng, v):
    out = silence(0.1)
    for i in range(3):
        d = 0.42
        a = brass(note_hz(-14), d, rng, bright=2500, attack=0.02) * 0.5
        b = brass(note_hz(-11), d, rng, bright=2500, attack=0.02) * 0.5
        out = place(out, a, i * 0.62)
        out = place(out, b, i * 0.62 + 0.2)
        out = place(out, drum(55, 0.4, rng, snap=0.3) * 0.5, i * 0.62)
    return reverb(out, rng, 1.0, 0.3, 3000)


def s_gameender_strike(rng, v):
    d = 3.0
    x = boom(rng, 2.0, 32, 0.9)
    x = place(x, thunder(rng, 2.6, crack=True) * 0.7, 0.02)
    x = place(x, rumble(rng, 2.5, 1.0) * 0.6, 0.15)
    return reverb(place(x, silence(d), 0), rng, 2.0, 0.35, 1500)


def s_gameender_intercept(rng, v):
    d = 1.6
    rev = whoosh(rng, 0.5, 400, 4000, 1.5) * np.linspace(0, 1, n_of(0.5)) ** 2
    ping = chime(note_hz(12), 1.4, rng, 0.8) * 0.5 + chime(note_hz(19), 1.2, rng, 0.8) * 0.35
    glass = metal(3500, 0.6, rng, 9, 0.5, 1.0) * 0.25
    return reverb(mix((rev, 0, 0.7), (ping, 0.5, 1.0), (glass, 0.5, 1.0), (silence(d), 0, 0)), rng, 1.5, 0.35, 8000)


# ---- start-position phase (maps/StartPhase, MapClient)

def s_startpos_begin(rng, v):
    d = 2.2
    gong = modal([110, 165.5, 221, 297, 389, 512], [2.5, 1.8, 1.5, 1.0, 0.7, 0.5], [1, 0.6, 0.5, 0.4, 0.3, 0.2], d, rng=rng, detune=0.004)
    pad = (sine(note_hz(-21), d) + sine(note_hz(-14), d) * 0.7 + sine(note_hz(-9), d) * 0.5) * env_ar(d, 0.8, d * 0.6) * 0.2
    return reverb(gong * 0.7 + pad, rng, 1.8, 0.35, 3500)


def s_startpos_place(rng, v):
    return mix((wood(320, 0.3, rng), 0, 0.8), (chime(note_hz(12), 0.4, rng, 0.3), 0.02, 0.3))


def s_startpos_tick(rng, v):
    return mix((wood(1400, 0.08, rng, 0.5), 0, 0.8), (silence(0.12), 0, 0))


def s_startpos_end(rng, v):
    d = 1.6
    root = note_hz(-21)
    horn = brass(root, 1.2, rng, bright=2000) * 0.5 + brass(root * 1.5, 1.2, rng, bright=2000) * 0.35
    return reverb(mix((drum(60, 0.6, rng, snap=0.4), 0, 0.8), (horn, 0.05, 1.0), (silence(d), 0, 0)), rng, 1.2, 0.3)


def make_stinger(faction):
    def f(rng, v):
        return MOTIFS[faction](rng, 1.25, short=False)
    return f


# ================================================================ table
# key -> (category, variations, generator). Keep in step with sounds/Sfx.java (the GameTest checks the files).

SOUNDS = OrderedDict()


def add(key, cat, n, fn):
    SOUNDS[key] = (cat, n, fn)


add("shot_bow", "units", 3, s_shot_bow)
add("shot_gun", "units", 3, s_shot_gun)
add("shot_fire", "units", 3, s_shot_fire)
add("shot_heavy", "units", 2, s_shot_heavy)
add("shot_magic", "units", 3, s_shot_magic)
add("shot_rocket", "units", 2, s_shot_rocket)
add("shot_thrown", "units", 2, s_shot_thrown)
add("hit_melee", "units", 3, s_hit_melee)
add("hit_heavy", "units", 3, s_hit_heavy)
add("hit_building", "units", 2, s_hit_building)
add("death_sunforged", "units", 2, s_death_sunforged)
add("death_gravebound", "units", 2, s_death_gravebound)
add("death_horde", "units", 2, s_death_horde)
add("death_verdant", "units", 2, s_death_verdant)
add("death_tide", "units", 2, s_death_tide)
add("death_storm", "units", 2, s_death_storm)
add("death_brass", "units", 2, s_death_brass)
add("death_large", "units", 2, s_death_large)
for _f in MOTIFS:
    add("built_" + _f, "buildings", 1, make_built(_f))
for _f in MOTIFS:
    add("destroyed_" + _f, "buildings", 1, make_destroyed(_f))
for _f in MOTIFS:
    add("nano_" + _f, "buildings", 2, make_nano(_f))
add("reclaim_tick", "buildings", 2, s_reclaim_tick)
add("reclaim_done", "buildings", 1, s_reclaim_done)
add("gate_open", "buildings", 1, s_gate_open)
add("gate_close", "buildings", 1, s_gate_close)
add("trap_spikes", "buildings", 1, s_trap_spikes)
add("trap_snare", "buildings", 1, s_trap_snare)
add("trap_mine", "buildings", 1, s_trap_mine)
add("possess_enter", "abilities", 1, s_possess_enter)
add("possess_exit", "abilities", 1, s_possess_exit)
add("hero_boon", "abilities", 1, s_hero_boon)
add("beast_trumpet", "abilities", 1, s_beast_trumpet)
add("beast_charge", "abilities", 1, s_beast_charge)
add("beast_breach", "abilities", 1, s_beast_breach)
add("beast_devour", "abilities", 1, s_beast_devour)
add("beast_embark", "abilities", 1, s_beast_embark)
add("gameender_start", "announcer", 1, s_gameender_start)
add("gameender_stage", "announcer", 1, s_gameender_stage)
add("gameender_aim", "announcer", 1, s_gameender_aim)
add("gameender_strike", "announcer", 1, s_gameender_strike)
add("gameender_intercept", "announcer", 1, s_gameender_intercept)
add("startpos_begin", "ui", 1, s_startpos_begin)
add("startpos_place", "ui", 1, s_startpos_place)
add("startpos_tick", "ui", 1, s_startpos_tick)
add("startpos_end", "ui", 1, s_startpos_end)
for _f in MOTIFS:
    add("stinger_" + _f, "announcer", 1, make_stinger(_f))

SUBTITLES = {
    "shot_bow": "Bow fires", "shot_gun": "Musket fires", "shot_fire": "Fireball launched", "shot_heavy": "Heavy shot",
    "shot_magic": "Spell cast", "shot_rocket": "Rocket launched", "shot_thrown": "Something thrown",
    "hit_melee": "Blades clash", "hit_heavy": "Heavy blow", "hit_building": "Building struck",
    "death_large": "Huge unit destroyed", "reclaim_tick": "Wreck reclaimed", "reclaim_done": "Wreck consumed",
    "gate_open": "Gate opens", "gate_close": "Gate closes", "trap_spikes": "Spike trap springs",
    "trap_snare": "Snare tightens", "trap_mine": "Mine detonates", "possess_enter": "Possession begins",
    "possess_exit": "Possession ends", "hero_boon": "Hero boon", "beast_trumpet": "War elephant trumpets",
    "beast_charge": "Beast charges", "beast_breach": "Wall breached", "beast_devour": "Behemoth devours a wreck",
    "beast_embark": "Bison bellows", "gameender_start": "A game ender awakens", "gameender_stage": "Game ender charges",
    "gameender_aim": "Game ender takes aim", "gameender_strike": "Game ender strikes",
    "gameender_intercept": "Strike intercepted", "startpos_begin": "Choose start positions",
    "startpos_place": "Start position placed", "startpos_tick": "Countdown", "startpos_end": "Start positions locked",
}
FACTION_NAMES = {"sunforged": "White Angel", "gravebound": "Death", "horde": "Horde", "verdant": "Verdant",
                 "tide": "Tidewrought", "storm": "Stormcallers", "brass": "Brassworks"}
for _f, _n in FACTION_NAMES.items():
    SUBTITLES["death_" + _f] = _n + " unit destroyed"
    SUBTITLES["built_" + _f] = _n + " building complete"
    SUBTITLES["destroyed_" + _f] = _n + " building destroyed"
    SUBTITLES["nano_" + _f] = _n + " builders at work"
    SUBTITLES["stinger_" + _f] = _n + " fanfare"


# ================================================================ output

def file_names(key, n):
    return [f"{key}_{i + 1}" for i in range(n)]


def encode(x, path):
    pcm = np.clip(x * 32767, -32768, 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tf:
        tmp = tf.name
    try:
        wavfile.write(tmp, SR, pcm)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-map_metadata", "-1", "-fflags", "+bitexact",
                        "-flags:a", "+bitexact", "-ac", "1", "-ar", str(SR), "-c:a", "libvorbis", "-q:a", "3", path],
                       check=True)
    finally:
        os.unlink(tmp)


def _append_entries(text, entries):
    """Appends 'key: value' entry strings before the closing brace of a JSON object text, fixing the comma."""
    body = text.rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip()
    sep = "," if not body.endswith("{") else ""
    return body + sep + "\n" + ",\n".join(entries) + "\n}\n"


def update_json(keys):
    """Refreshes the sfx.* entries of sounds.json and their subtitles in en_us.json as a text edit: other entries keep
    their exact bytes (no reformatting churn for parallel branches), old sfx entries are cut and the current set is
    appended at the end. Both files are re-parsed afterwards as a check."""
    import re
    with open(SOUNDS_JSON, encoding="utf-8") as f:
        text = f.read()
    text = re.sub(r',?\s*"sfx\.[a-z0-9_]+"\s*:\s*\{[^{}]*\}', "", text)
    entries = []
    for key in keys:
        n = SOUNDS[key][1]
        files = ", ".join('"reignofnether:sfx/%s"' % name for name in file_names(key, n))
        entries.append('    "sfx.%s": {\n        "subtitle": "sounds.reignofnether.sfx.%s",\n        "sounds": [ %s ]\n    }'
                       % (key, key, files))
    text = _append_entries(text, entries)
    json.loads(text)
    with open(SOUNDS_JSON, "w", encoding="utf-8") as f:
        f.write(text)
    with open(LANG_JSON, encoding="utf-8") as f:
        lines = f.read().split("\n")
    lines = [ln for ln in lines if not ln.lstrip().startswith('"sounds.reignofnether.sfx.')]
    text = "\n".join(lines)
    text = re.sub(r",(\s*\})\s*$", r"\1", text)   # a cut last line may leave a trailing comma
    entries = ['  "sounds.reignofnether.sfx.%s": %s' % (k, json.dumps(SUBTITLES.get(k, k.replace("_", " ").capitalize())))
               for k in keys]
    text = _append_entries(text, entries)
    json.loads(text)
    with open(LANG_JSON, "w", encoding="utf-8") as f:
        f.write(text)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--wav-dir", default=None, help="also write each sound as .wav here (auditioning)")
    ap.add_argument("--only", default=None, help="comma-separated keys")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--no-json", action="store_true", help="don't touch sounds.json / en_us.json")
    args = ap.parse_args()

    keys = list(SOUNDS) if not args.only else [k.strip() for k in args.only.split(",")]
    for k in keys:
        if k not in SOUNDS:
            sys.exit(f"unknown key {k}")
    os.makedirs(args.out, exist_ok=True)
    if args.wav_dir:
        os.makedirs(args.wav_dir, exist_ok=True)
    total_secs = 0.0
    for key in keys:
        cat, n, fn = SOUNDS[key]
        for i, name in enumerate(file_names(key, n)):
            rng = np.random.default_rng(zlib.crc32(f"{key}:{i}".encode()))
            x = finish(np.asarray(fn(rng, i), dtype=float), cat)
            total_secs += len(x) / SR
            if args.list:
                print(f"{name:28s} {cat:10s} {len(x) / SR:5.2f}s")
                continue
            encode(x, os.path.join(args.out, name + ".ogg"))
            if args.wav_dir:
                wavfile.write(os.path.join(args.wav_dir, name + ".wav"), SR, np.clip(x * 32767, -32768, 32767).astype(np.int16))
    if args.list:
        print(f"{len(keys)} sounds, {total_secs:.1f} s of audio")
        return
    # drop stale files of keys that are gone (only when regenerating everything)
    if not args.only:
        wanted = {name + ".ogg" for k in SOUNDS for name in file_names(k, SOUNDS[k][1])}
        for fname in os.listdir(args.out):
            if fname.endswith(".ogg") and fname not in wanted:
                os.unlink(os.path.join(args.out, fname))
    size = sum(os.path.getsize(os.path.join(args.out, f)) for f in os.listdir(args.out) if f.endswith(".ogg"))
    print(f"{len(keys)} sounds, {total_secs:.1f} s of audio, sfx folder {size / 1e6:.2f} MB")
    if size > BUDGET_BYTES:
        sys.exit(f"sfx folder is {size} bytes, over the {BUDGET_BYTES} budget")
    if not args.no_json:
        update_json(list(SOUNDS))


if __name__ == "__main__":
    main()
