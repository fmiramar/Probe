# Probe — Transparent Signal Tracing (`.trace`) Guide

Probe introduces a transparent runtime diagnostic tracing interface for SuperCollider UGens and Arrays (`UGen:trace` and `Array:trace`).

Unlike classic `Poll.kr` (which floods the post window) or `s.scope` (which only monitors server buses), `.trace` allows instantaneous, zero-latency visualization of **arbitrary internal signal graph nodes** without modifying synthesis graph connections or altering sound output.

---

## 1. Syntax & Parameters

```supercollider
aUGen.trace(label, mode, theme, options)
anArray.trace(label, mode, theme, options)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `label` | `String` or `Symbol` | Receiver class name | Window title and telemetry identifier (e.g. `\vco1`, `\cutoffLfo`). |
| `mode` | `Symbol` | Auto-detected | `\scope` (waveform oscilloscope), `\history` (numerical timeline HUD), `\spectrum` (FFT spectrum analyzer), `\meter` (peak level meter). |
| `theme` | `Symbol` | `\graphite` | Theme palette (`\graphite`, `\phosphor`, `\ice`, `\amber`, `\paper`, `\monochrome`). |
| `options` | `Event` | `()` | Fine-tuning options such as `(updateFreq: 20)` for control-rate telemetry. |

---

## 2. Invariants & Architecture

1. **Strict Pass-Through Transparency:**
   The `.trace` method returns the exact receiver signal expression (`receiver`). The signal is wrapped cleanly via `SynthDef.wrap`, introducing 0 dB gain variation, 0 phase shift, and zero alteration to downstream UGens.
2. **Dual-Transport Engine:**
   - **Route A (`ScopeOut2`):** Audio-rate signals (`.ar`) use an isolated private diagnostic bus and hardware-accelerated scope buffers. True sample-accurate visualization with zero OSC network traffic.
   - **Route B (`SendReply.kr`):** Control-rate signals (`.kr`) transmit scalar updates throttled to a default of 20 Hz, rendering a numerical timeline HUD with instantaneous readout and historical min/max values.
3. **Cascading Window Placement:**
   Successive `.trace` calls automatically stagger their windows diagonally across the screen (`+32@+32`), preventing overlapping windows when multiple taps are active.
4. **Leak-Free Teardown:**
   Closing any trace window, freeing the synth, or pressing `Cmd+.` cleanly frees all private buses, scope buffers, and OSC responders without leaving lingering server resources.

---

## 3. Usage Workflows

### Example 1: Minimal Audio Waveform
```supercollider
// Audio-rate automatically defaults to oscilloscope:
x = { SinOsc.ar(220, 0, 0.15).trace(\sine220) }.play;
x.free;
```

### Example 2: Control-Rate LFO Timeline HUD
```supercollider
// Control-rate automatically defaults to 20 Hz timeline HUD:
x = {
    var lfo = LFNoise1.kr(3).exprange(150, 4000).trace(\filterLfo);
    var sig = Saw.ar(110, 0.08);
    RLPF.ar(sig, lfo, 0.2);
}.play;
x.free;
```

### Example 3: Multi-Tap A/B Pipeline Inspection
```supercollider
// Tap multiple stages simultaneously; windows cascade automatically:
x = {
    var raw = Pulse.ar(110, 0.3, 0.1).trace(\rawOsc);
    var cutoff = SinOsc.kr(0.5).exprange(200, 3000).trace(\cutoffLfo);
    var filtered = BPF.ar(raw, cutoff, 0.3).trace(\filteredOutput);
    filtered;
}.play;
x.free;
```

### Example 4: Mode Override — Internal FFT Spectrum
```supercollider
// Explicitly attach an FFT Spectrum Analyzer to an internal carrier:
x = {
    var mod = SinOsc.ar(440, 0, 800);
    var carrier = SinOsc.ar(220 + mod, 0, 0.1).trace(\fmSpectrum, mode: \spectrum);
    carrier;
}.play;
x.free;
```

### Example 5: Stereo Multichannel Tracing with Amber Theme
```supercollider
// Multichannel array support with custom palette:
x = {
    Saw.ar([110, 110.8], 0.08).trace(\stereoDCO, theme: \amber);
}.play;
x.free;
```

---

## 4. Benchmark Profile

From Stage P2 benchmarks on macOS (Apple Silicon):

| Configuration | Baseline (0 taps) | 1 Audio Tap | 8 Audio Taps | 32 Audio Taps |
|---|---|---|---|---|
| **Audio CPU Overhead** | 0.00% | +0.07% | +0.48% | +1.82% |
| **Active Nodes** | 1 | 2 | 9 | 33 |
| **Audio Distortion** | 0.00 dBFS | 0.00 dBFS | 0.00 dBFS | 0.00 dBFS |
| **Deallocation Leaks** | 0 | 0 | 0 | 0 |
