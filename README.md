# Probe

[![SuperCollider](https://img.shields.io/badge/SuperCollider-3.11+-orange.svg)](https://supercollider.github.io/)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Unit Tests](https://img.shields.io/badge/Tests-14%20Suites%20Passed-brightgreen.svg)](tests/)
[![Architecture](https://img.shields.io/badge/Zero%20C%2B%2B%20Dependencies-Pure%20Quark-success.svg)](classes/)

A cohesive, laboratory-grade, **lightweight** family of real-time audio analysis, visualization, and signal instrumentation tools for SuperCollider.

`Probe` (formerly `SCVisuals`) prioritizes audio-thread safety (`scsynth`), predictable resource lifecycle, centralized analysis sharing, and elegant modern aesthetics—with zero mandatory C++ compilation, zero external binary dependencies, and zero modifications to stock SuperCollider files.

---

## Visualizer Lineup

| Instrument | Description | Backend Transport | Status |
|---|---|---|---|
| **`s.meter2`** / `Probe.meter` | Compact vertical server meter (inputs/outputs, dB ruler, peak/RMS, peak-hold) | `SendPeakRMS` via `SCVLevelsSource` | **Production (v0.2.0)** |
| **`s.scope2`** / `Probe.scope` | Oscilloscope with calibrated ms/dBFS axes, stationary trigger edge lock (`\rising`, `\falling`), tracks/overlay/XY | `ScopeView` / `ScopeOut2` + DSP circular latch buffer | **Production (v0.2.0)** |
| **`s.freqscope2`** / `Probe.spectrum` | Unified FFT spectrum analyzer with 3 modes (Line, 16-Band Analyzer, Spectrogram) and interactive cursor | Precision FreqScope FFT pipeline | **Production (v0.2.0)** |
| **`s.spectralDashboard`** | 4-preset command center (Overview, Pitch, Time-Frequency, Compare) with 16-band analyzer, chroma, and descriptors | `ProbeSpectralSource` & `SCVBroker` | **Production (v0.2.0)** |
| **`s.inspector`** / `Probe.inspect` | Static topological signal-flow Directed Acyclic Graph (DAG) inspector with rate coloring and bezier routing | Static SCLang DAG compilation | **Production (v0.2.0)** |
| **`.trace`** | Transparent runtime in-graph signal tracing for audio rate (Scope) and control rate (20 Hz Timeline HUD) | Dual transport: `ScopeOut2` / `SendReply.kr` | **Production (v0.2.0)** |
| **`s.spectrogram`** | 2D scrolling time–frequency spectrogram with circular ring-buffer blit | 64-band logarithmic `SendReply.kr` | **Production (v0.2.0)** |
| **`s.waterfall`** | 2.5D isometric stacked-line spectral history with depth-cued color shading | Shared `SCVSpectrumSource` | **Production (v0.2.0)** |
| **`s.history`** | Rolling peak, RMS, and shaded crest factor timeline history chart | Shared `SCVLevelsSource` | **Production (v0.2.0)** |
| **`s.loudness`** | ITU-R BS.1770-4 / EBU R128 loudness meter (M, S, I, LRA, -23 / -14 LUFS targets) | Two-stage K-weighting biquads | **Production (v0.2.0)** |
| **`s.vectorscope`** | Stereo Lissajous X/Y display with Mid/Side polar graticule and phase correlation | `ScopeOut2` + Correlation UGen | **Production (v0.2.0)** |
| **`s.dashboard`** | Unified 5-panel command center with global theme propagation and master freeze | `SCVMeasurementBroker` | **Production (v0.2.0)** |

---

## Visual Gallery

| Oscilloscope Trigger Stabilization | 16-Band Octave Analyzer |
| :---: | :---: |
| ![Scope Trigger](docs/screenshots/scope-tier-ab-rising.png) | ![Band Analyzer](docs/screenshots/spectrum-mode-bands.png) |

| Static DSP Graph Inspector | Advanced Spectral Dashboard |
| :---: | :---: |
| ![DSP Graph Inspector](docs/screenshots/probe-graph-inspector.png) | ![Spectral Dashboard](docs/screenshots/spectral-dashboard-overview-fixed.png) |

| Precision Line FFT Spectrum | 2.5D Waterfall Spectrum |
| :---: | :---: |
| ![Line Spectrum](docs/screenshots/spectrum-mode-line.png) | ![Waterfall](docs/screenshots/scvwaterfall-live.png) |

---

## Quick Start

```supercollider
// 1. Boot Server
s.boot;

// 2. Play Test Signal
x = { Saw.ar([110, 110.5]) * 0.25 }.play;

// 3. Open Instruments via Server Shortcuts or Probe Facade
s.meter2;                                                     // Vertical Server Meter
s.scope2;                                                     // Oscilloscope with Trigger Stabilization
s.freqscope2;                                                 // 3-Mode Spectrum Analyzer
s.spectralDashboard;                                          // 4-Preset Spectral Dashboard
s.dashboard;                                                  // 5-Panel Command Center
s.loudness;                                                   // EBU R128 Loudness Meter

// 4. Transparent In-Graph Signal Tracing (.trace)
y = {
    var lfo = SinOsc.kr(1.5).range(200, 2000).trace(\cutoff); // 20 Hz scrolling HUD
    var sig = Saw.ar(110).trace(\rawOsc);                     // Sample-accurate scope
    RLPF.ar(sig, lfo, 0.2);
}.play;

// 5. Static DSP Graph Inspection
s.inspect(y.def);
```

---

## Architecture: Centralized Measurement Broker

Multiple visualizers monitoring the same server bus automatically **share a single analysis Synth**:

```text
               ┌───────────────────────────────┐
               │    scsynth Audio Bus          │
               └───────────────┬───────────────┘
                               │
               ┌───────────────▼───────────────┐
               │     SCVMeasurementBroker      │
               └───────┬───────────────┬───────┘
                       │               │
        ┌──────────────▼──────┐ ┌──────▼───────────────┐
        │  SCVSpectrumSource  │ │   SCVLoudnessSource  │
        └──────┬──────────────┘ └──────┬───────────────┘
               │                       │
      ┌────────┴────────┐              │
┌─────▼───────┐  ┌──────▼──────┐ ┌─────▼─────────┐
│ Spectrogram │  │  Waterfall  │ │ LoudnessView  │
└─────────────┘  └─────────────┘ └───────────────┘
```

- **Zero Duplicate Synths:** Opening Spectrogram, Waterfall, and Spectrum together never creates redundant FFT nodes.
- **Reference Counting:** Sources start on first subscriber and automatically free when all subscribers close.
- **Fail-Safe Lifecycle:** Clean recovery on `CmdPeriod` and `ServerQuit`.

---

## Themes & Ergonomics

Probe provides six immutable, scientifically calibrated palettes:

1. **Graphite** (Default): Sleek dark studio aesthetic (`#171A20`).
2. **Phosphor**: Vintage green monochrome CRT glow (`#09140F`).
3. **Ice**: Crisp Nordic blue-cyan technical readout (`#101A2A`).
4. **Amber**: Warm vintage mastering console (`#1A1712`).
5. **Paper**: High-contrast light mode for daytime work and publication printing (`#F1EDE2`).
6. **Monochrome**: Minimalist grayscale (`#111111`).

All instruments support dynamic font scaling (`1.0` to `2.0`, default `1.25`) and dynamic theme switching via `.theme = \ice`.

---

## Truth in Labeling

| Measurement | Standard / Definition | Window / Ballistics | Range / Units |
|---|---|---|---|
| **Peak** | Unwindowed absolute sample maximum | Instantaneous ($O(1)$) | $-\infty$ to $0.0$ dBFS |
| **RMS** | Root mean square energy | 100 ms ($10 \times \text{blockSize}$) | $-\infty$ to $0.0$ dBFS |
| **Momentary Loudness (M)** | ITU-R BS.1770-4 K-weighting | 400 ms sliding rectangular | $-\infty$ to $0.0$ LUFS |
| **Short-Term Loudness (S)** | ITU-R BS.1770-4 K-weighting | 3.0 s sliding rectangular | $-\infty$ to $0.0$ LUFS |
| **Integrated Loudness (I)**| ITU-R BS.1770 / EBU R128 | Dual-gated ($-70$ LUFS abs / $-10$ LU rel) | Target: $-23$ or $-14$ LUFS |
| **Loudness Range (LRA)** | EBU Tech 3342 | 95th minus 10th percentile of $S$ blocks | $0.0$ to $+30.0$ LU |
| **Spectral Centroid** | Frequency-weighted spectral mean | Magnitude-weighted FFT frame | Hz / kHz |
| **Phase Correlation** | Normalized cross-correlation | 250 ms integration | $-1.0$ to $+1.0$ |

---

## Installation & Testing

```supercollider
// Install Quark
Quarks.install("~/Desktop/SC New Visulations servers/SCVisuals");

// Recompile Class Library
thisProcess.recompile;

// Run Master Test Suite (14 Suites Passing Cleanly)
"tests/run_all_tests.scd".loadRelative;
```

---

## License

GNU General Public License v3.0 or later ([LICENSE](LICENSE)).
