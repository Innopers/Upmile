#!/usr/bin/env python3
"""Generates assets/music/calm_start.m4a: a 60 s ambient pad that breathes on the 4 s inhale / 6 s exhale cycle
(core/routine/Breathing.kt) and loops seamlessly. Python 3 stdlib + macOS afconvert. Deterministic."""
import math, os, struct, subprocess, sys, tempfile, wave

RATE, DUR, PERIOD, INHALE = 22050, 60, 60.0, 4.0
CHORDS = [  # Cmaj7, Am7, Fmaj7, G6 (Hz); 15 s each
    [130.81, 164.81, 196.00, 246.94], [110.00, 164.81, 196.00, 261.63],
    [87.31, 130.81, 174.61, 220.00], [98.00, 146.83, 196.00, 246.94],
]
# Frequencies snapped to k/60 Hz so every oscillator completes whole cycles in 60 s: the loop point is seamless.
SNAP = lambda f: round(f * PERIOD) / PERIOD

def breath(t):  # 0.25..1.0, period 10 s, continuous
    p = t % 10.0
    x = 0.5 - 0.5 * math.cos(math.pi * p / INHALE) if p < INHALE else 0.5 + 0.5 * math.cos(math.pi * (p - INHALE) / 6.0)
    return 0.25 + 0.75 * x

def weight(t, i):  # cos^2 crossfade, circular over 60 s, weights sum to 1
    d = abs((t - 15.0 * i + 30.0) % PERIOD - 30.0)
    return math.cos(math.pi * d / 30.0) ** 2 if d < 15.0 else 0.0

def render():
    out = []
    for n in range(RATE * DUR):
        t = n / RATE
        s = 0.0
        for i, chord in enumerate(CHORDS):
            w = weight(t, i)
            if w:
                s += w * sum(math.sin(2 * math.pi * SNAP(f) * t) + 0.3 * math.sin(4 * math.pi * SNAP(f) * t) for f in chord)
        out.append(s * breath(t))
    peak = max(abs(v) for v in out)
    g = 10 ** (-4 / 20) / peak  # peak -4 dBFS
    return [v * g for v in out]

def main():
    samples = render()
    pcm = [round(v * 32767) for v in samples]
    # self-check (before encoding)
    assert len(pcm) == DUR * RATE
    assert max(abs(v) for v in pcm) / 32767 <= 10 ** (-3 / 20), "peak above -3 dBFS"
    assert abs(pcm[0]) < 100, "first sample not near zero"
    step = max(abs(b - a) for a, b in zip(pcm, pcm[1:]))  # the loop join must be no bigger than a normal step
    assert abs(pcm[0] - pcm[-1]) <= step, "level jump at the loop point"
    wav = os.path.join(tempfile.mkdtemp(), "calm_start.wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(struct.pack("<%dh" % len(pcm), *pcm))
    dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "music", "calm_start.m4a")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run(["afconvert", "-f", "m4af", "-d", "aac", "-b", "64000", wav, dst], check=True)
    print("self-check ok: %d samples, peak %.1f dBFS, first %d last %d -> %s" % (
        len(pcm), 20 * math.log10(max(abs(v) for v in pcm) / 32767), pcm[0], pcm[-1], os.path.normpath(dst)))

if __name__ == "__main__":
    sys.exit(main())
