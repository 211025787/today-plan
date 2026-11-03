"""生成宣传片 BGM：128 BPM 轻快电子流行（四拍底鼓 + 拍手 + 贝斯 + 琶音 + Pad）。

全部为程序合成，不含任何采样素材，无版权风险。
运行：python video/make_audio.py
输出：.tmp_docs/promo.wav
"""
import array
import math
import os
import random
import struct
import wave

SR = 44100
DUR = 36.0
N = int(SR * DUR)
BPM = 128.0
BEAT = 60.0 / BPM
BAR = BEAT * 4
STEP = BEAT / 4  # 十六分音符

left = array.array("d", [0.0]) * N
right = array.array("d", [0.0]) * N
random.seed(11)

# 和弦进行：C - G - Am - F（roots + 三和弦音）
CHORDS = [
    (130.81, [261.63, 329.63, 392.00]),
    (98.00, [196.00, 246.94, 293.66]),
    (110.00, [220.00, 261.63, 329.63]),
    (87.31, [174.61, 220.00, 261.63]),
]

TOTAL_BARS = int(DUR / BAR)  # 19


def add(i, v, pan=0.0):
    """写入立体声缓冲（pan -1 左，1 右）。"""
    if 0 <= i < N:
        left[i] += v * (1.0 - max(0.0, pan) * 0.5)
        right[i] += v * (1.0 + min(0.0, pan) * 0.5)


def env_exp(t, tau):
    return math.exp(-t / tau)


def kick(t0, amp=0.9):
    dur = int(0.34 * SR)
    start = int(t0 * SR)
    phase = 0.0
    for k in range(dur):
        t = k / SR
        f = 45.0 + 110.0 * math.exp(-t / 0.035)
        phase += 2 * math.pi * f / SR
        v = math.sin(phase) * env_exp(t, 0.11) * amp
        v += (random.random() * 2 - 1) * env_exp(t, 0.004) * 0.25 * amp
        add(start + k, v)


def clap(t0, amp=0.5):
    dur = int(0.22 * SR)
    start = int(t0 * SR)
    prev = 0.0
    for k in range(dur):
        t = k / SR
        noise = random.random() * 2 - 1
        hp = noise - prev  # 简易高通，让拍手更"脆"
        prev = noise
        tap = 0.0
        for off in (0.0, 0.011, 0.022):
            if t >= off:
                tap += env_exp(t - off, 0.022) * 0.5
        v = hp * tap * env_exp(t, 0.09) * amp
        add(start + k, v, pan=0.08)


def hat(t0, amp=0.16, open_=False):
    dur = int((0.13 if open_ else 0.05) * SR)
    start = int(t0 * SR)
    prev = 0.0
    tau = 0.05 if open_ else 0.018
    for k in range(dur):
        t = k / SR
        noise = random.random() * 2 - 1
        hp = noise - prev
        prev = noise
        add(start + k, hp * env_exp(t, tau) * amp, pan=-0.12)


def bass(t0, freq, dur=0.19, amp=0.30):
    n = int(dur * SR)
    start = int(t0 * SR)
    phase = 0.0
    for k in range(n):
        t = k / SR
        phase += 2 * math.pi * freq / SR
        v = math.sin(phase) + 0.35 * math.sin(phase * 2) + 0.15 * math.sin(phase * 3)
        attack = min(1.0, t / 0.006)
        add(start + k, v * attack * env_exp(t, 0.10) * amp)


def pluck(t0, freq, amp=0.16, pan=0.18):
    dur = int(0.20 * SR)
    start = int(t0 * SR)
    phase = 0.0
    for k in range(dur):
        t = k / SR
        phase += 2 * math.pi * freq / SR
        v = math.sin(phase) + 0.22 * math.sin(phase * 3)
        v *= env_exp(t, 0.055) * amp
        add(start + k, v, pan=pan)
        # 轻微延迟回声，制造空间感
        if k > int(0.145 * SR):
            add(start + k - int(0.145 * SR), v * 0.25, pan=-pan)


def pad(t0, freqs, dur=BAR, amp=0.05):
    n = int(dur * SR)
    start = int(t0 * SR)
    for k in range(n):
        t = k / SR
        attack = min(1.0, t / 0.35)
        release = min(1.0, (dur - t) / 0.35)
        e = attack * max(0.0, release)
        v = 0.0
        for j, f in enumerate(freqs):
            v += math.sin(2 * math.pi * f * (1 + j * 0.0015) * t) / (j + 1.6)
        add(start + k, v * e * amp)


def riser(t0, dur, amp=0.22):
    n = int(dur * SR)
    start = int(t0 * SR)
    prev = 0.0
    for k in range(n):
        t = k / SR
        p = t / dur
        noise = random.random() * 2 - 1
        hp = noise - prev
        prev = noise
        v = hp * (p ** 2) * amp
        add(start + k, v, pan=0.1 * math.sin(p * 6))


def crash(t0, amp=0.4):
    dur = int(1.4 * SR)
    start = int(t0 * SR)
    prev = 0.0
    for k in range(dur):
        t = k / SR
        noise = random.random() * 2 - 1
        hp = noise - prev
        prev = noise
        add(start + k, hp * env_exp(t, 0.45) * amp)


# ---------------- 编排 ----------------
# 前奏铺底
pad(0.0, CHORDS[0][1], BAR * 2, amp=0.045)
riser(0.0, BAR * 2, amp=0.18)

for bar in range(TOTAL_BARS):
    t_bar = bar * BAR
    root, tones = CHORDS[bar % 4]
    is_break = 14 <= bar <= 15          # 降下来做 build
    is_full = bar >= 2                 # 从第 3 小节进入完整律动

    # Pad：整段铺底
    pad(t_bar, tones, BAR, amp=0.035 if not is_break else 0.05)

    if is_full and not is_break:
        # 四拍底鼓
        for b in range(4):
            kick(t_bar + b * BEAT)
        # 拍手在 2、4 拍
        clap(t_bar + BEAT)
        clap(t_bar + 3 * BEAT)
        # 八分音符踩镲
        for s in range(8):
            hat(t_bar + s * BEAT / 2, amp=0.15 if s % 2 == 0 else 0.10)
        # 反拍开镲（后半段加）
        if bar >= 8:
            for s in range(4):
                hat(t_bar + s * BEAT + BEAT / 2, amp=0.09, open_=True)
        # 贝斯：八分音符根音
        for s in range(8):
            bass(t_bar + s * BEAT / 2, root * (2 if s % 4 == 3 else 1))
        # 琶音：十六分音符扫和弦音
        seq = [tones[0], tones[1], tones[2], tones[0] * 2]
        for s in range(16):
            pluck(t_bar + s * STEP, seq[s % 4], amp=0.14 if bar >= 6 else 0.11,
                  pan=0.22 if s % 2 == 0 else -0.18)
    elif is_break:
        # 只留 Pad + 琶音 + 上升音效
        seq = [tones[0], tones[1], tones[2], tones[0] * 2]
        for s in range(8):
            pluck(t_bar + s * STEP * 2, seq[s % 4], amp=0.12, pan=0.2)
        riser(t_bar, BAR, amp=0.16)
    else:
        # 前奏：琶音 + 轻踩镲
        seq = [tones[0], tones[1], tones[2], tones[0] * 2]
        for s in range(8):
            pluck(t_bar + s * STEP * 2, seq[s % 4], amp=0.10, pan=0.2)
        for s in range(4):
            hat(t_bar + s * BEAT, amp=0.06)

# 收尾：重拍 + 镲片 + 尾音
crash(30.0, amp=0.32)
kick(34.7, amp=1.0)
pad(34.7, CHORDS[0][1], 1.2, amp=0.06)
crash(34.7, amp=0.26)

# ---------------- 归一化与母线处理 ----------------
peak = 0.0
for i in range(N):
    v = abs(left[i]) + abs(right[i])
    if v > peak:
        peak = v
gain = 0.92 / peak if peak > 0 else 1.0

frames = bytearray()
for i in range(N):
    t = i / SR
    fade = min(1.0, t / 0.25) * min(1.0, (DUR - t) / 0.9)
    l = math.tanh(left[i] * gain * 1.05) * 0.95 * fade
    r = math.tanh(right[i] * gain * 1.05) * 0.95 * fade
    frames += struct.pack("<hh", int(l * 32767), int(r * 32767))


def main():
    out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".tmp_docs")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "promo.wav")
    with wave.open(path, "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(bytes(frames))
    print("written", path, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    main()
