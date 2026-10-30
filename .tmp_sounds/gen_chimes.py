"""生成 5 种专注结束提示音（44.1kHz / 16bit / 单声道 WAV）。

全部由正弦波与包络程序化合成，不包含任何第三方录音素材。
"""
import math
import struct
import wave

SR = 44100
OUT_DIR = "app/src/main/res/raw"


def write_wav(name, samples, peak=0.72):
    # 归一化到目标峰值，避免不同音色音量差异过大
    mx = max(1e-9, max(abs(s) for s in samples))
    scale = peak / mx
    data = bytearray()
    for s in samples:
        v = int(max(-1.0, min(1.0, s * scale)) * 32767)
        data += struct.pack("<h", v)
    with wave.open(f"{OUT_DIR}/{name}.wav", "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(bytes(data))
    print(f"{name}.wav  {len(samples)/SR:.2f}s")


def env_exp(t, dur, decay, attack=0.004):
    a = min(1.0, t / attack) if attack > 0 else 1.0
    return a * math.exp(-decay * t)


def bell():
    """清铃：明亮泛音 + 指数衰减。"""
    dur = 2.4
    n = int(SR * dur)
    out = []
    partials = [(880, 1.0, 2.2), (1320, 0.55, 3.0), (1760, 0.35, 3.8), (2640, 0.18, 5.2)]
    for i in range(n):
        t = i / SR
        s = sum(amp * math.sin(2 * math.pi * f * t) * env_exp(t, dur, d)
                for f, amp, d in partials)
        out.append(s * 0.3)
    return out


def wood():
    """木鱼：短促敲击 + 木质共振。"""
    dur = 0.45
    n = int(SR * dur)
    out = []
    rnd = 12345
    prev = 0.0
    for i in range(n):
        t = i / SR
        # 简易低通噪声模拟敲击瞬态
        rnd = (1103515245 * rnd + 12345) & 0x7FFFFFFF
        noise = (rnd / 0x7FFFFFFF) * 2 - 1
        prev = prev * 0.72 + noise * 0.28
        click = prev * env_exp(t, dur, 90, attack=0.001)
        body = (math.sin(2 * math.pi * 420 * t) * 0.8 +
                math.sin(2 * math.pi * 900 * t) * 0.35) * env_exp(t, dur, 14)
        out.append((click * 0.55 + body * 0.45) * 0.9)
    return out


def drop():
    """水滴：下滑音高 + 微弱尾音。"""
    dur = 0.9
    n = int(SR * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        freq = 1500 * math.exp(-9.0 * t) + 520
        phase += 2 * math.pi * freq / SR
        tone = math.sin(phase) * env_exp(t, dur, 9, attack=0.002)
        # 第二滴轻回响
        t2 = t - 0.18
        echo = 0.0
        if t2 > 0:
            echo = math.sin(2 * math.pi * 660 * t2) * env_exp(t2, dur, 12) * 0.25
        out.append((tone + echo) * 0.8)
    return out


def bowl():
    """颂钵：低频泛音缓慢起伏，长衰减。"""
    dur = 6.0
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        # 每个泛音配一个极近邻频率，产生缓慢的"拍"（beating）
        s = (math.sin(2 * math.pi * 210 * t) + math.sin(2 * math.pi * 211.2 * t)) * 0.5 * env_exp(t, dur, 0.55, 0.05)
        s += (math.sin(2 * math.pi * 315 * t) + math.sin(2 * math.pi * 316.1 * t)) * 0.3 * env_exp(t, dur, 0.7, 0.05)
        s += math.sin(2 * math.pi * 420 * t) * 0.18 * env_exp(t, dur, 0.9, 0.05)
        out.append(s * 0.5)
    return out


def soft():
    """柔音：两声木琴式和弦（C5 → G5）。"""
    dur = 2.0
    n = int(SR * dur)

    def marimba_note(t0, freq, amp):
        def sample(t):
            lt = t - t0
            if lt < 0:
                return 0.0
            s = math.sin(2 * math.pi * freq * lt) + 0.25 * math.sin(2 * math.pi * freq * 2 * lt)
            return s * amp * env_exp(lt, dur, 4.2, attack=0.006)
        return sample

    notes = [marimba_note(0.0, 523.25, 0.9), marimba_note(0.16, 783.99, 0.75)]
    out = []
    for i in range(n):
        t = i / SR
        out.append(sum(n_(t) for n_ in notes) * 0.5)
    return out


def fade_tail(samples, ms=40):
    """尾部淡出，避免截断爆音。"""
    k = int(SR * ms / 1000)
    n = len(samples)
    for i in range(min(k, n)):
        samples[n - 1 - i] *= i / k
    return samples


if __name__ == "__main__":
    import os
    os.makedirs(OUT_DIR, exist_ok=True)
    write_wav("chime_bell", fade_tail(bell()))
    write_wav("chime_wood", fade_tail(wood()))
    write_wav("chime_drop", fade_tail(drop()))
    write_wav("chime_bowl", fade_tail(bowl(), 120))
    write_wav("chime_soft", fade_tail(soft(), 80))
