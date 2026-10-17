# Probe Phase II — Existing State Audit (Gate G0)

**Date:** 2026-10-17  
**Auditor:** Agentic Evaluation Assistant  
**Repository Path:** `/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals`  
**Git Branch:** `phase2/probe-enhancements`  
**Baseline Git Tag:** `v1-baseline` (Commit `bb4d83d`)  
**SuperCollider Version:** SuperCollider 3.14.1 (Built from official release)  
**Host Architecture:** macOS Apple Silicon (arm64, Darwin 24.x)  
**Audio Drivers & Devices:** CoreAudio, 48000 Hz, block size 512  

---

## 1. Class & File Inventory

The existing repository is fully compiled, passing 9/9 unit test suites with zero syntax or compile errors.

### Core Modules (`classes/`)
| Source File | Primary Classes Defined | Responsibility | Current Status |
|---|---|---|---|
| `SCVisuals.sc` | `SCVisuals` | Public API facade & broker delegation | Working, v0.1.0 baseline |
| `SCVTheme.sc` | `SCVTheme` | 6 laboratory color themes, density, `defaultFontScale = 1.25` | Working, fully tested (62 assertions) |
| `SCVBroker.sc` | `SCVBroker`, `SCVSource` | Central telemetry & analysis transport, ref counting | Working, active reference counting |
| `SCVMeter.sc` | `SCVMeter`, `SCVMeterView` | Multi-channel peak/RMS meter with dual dB rulers | Working, vertical layout |
| `SCVScope.sc` | `SCVScope`, `SCVScopeView` | Real-time waveform oscilloscope (`ScopeView` backed) | Working, overlay/stacked modes |
| `SCVSpectrum.sc` | `SCVSpectrum`, `SCVSpectrumView` | 2048-point FFT magnitude spectrum, log Hz grid | Working, raw FFT scan |
| `SCVSpectrogram.sc` | `SCVSpectrogram`, `SCVSpectrogramView` | Ring-buffer scrolling spectrogram with color LUT | Working |
| `SCVWaterfall.sc` | `SCVWaterfall`, `SCVWaterfallView` | 2.5D pseudo-isometric waterfall (48 traces) | Working |
| `SCVLoudness.sc` | `SCVLoudness`, `SCVLoudnessView` | BS.1770 K-weighted LUFS meter (M, S, I, LRA) | Working |
| `SCVVectorscope.sc` | `SCVVectorscope`, `SCVVectorscopeView` | Polar Lissajous (M/S), phase correlation [-1, +1] | Working |
| `SCVDashboard.sc` | `SCVDashboard`, `SCVDashboardView` | High-density multi-panel dashboard | Working, tight 125% layout |
| `SCVHistory.sc` | `SCVHistory`, `SCVHistoryView` | Scrolling numerical history plot | Working |
| `SCVisualsLifecycle.sc` | `SCVisualsLifecycle` | Auto-cleanup on CmdPeriod, quit, or recompile | Working |

---

## 2. Installed Quarks & Extensions

The system environment was audited via `sclang_conf.yaml` and directory scans:
- **`downloaded-quarks/SafetyNet`:** Active (adds output safety clipping protection).
- **`Extensions/`:**
  - `BelaPhaseVocoder`
  - `ChowDSP-SC-Ports`
  - `DragonflyReverbDF`
  - `NovelOscUGens`
  - `sc3-plugins`
  - `SCShader`
  - `UBM`
- **Old SCVisuals installations in Application Support:**
  - `~/Library/Application Support/SuperCollider/Help/Classes/SCVisuals.html` has been removed.
  - No duplicate class files exist in `Extensions/` or `downloaded-quarks/`.
  - `sclang_conf.yaml` directly includes `/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals`.

---

## 3. Analysis Transport & Telemetry Architecture

1. **RMS/Peak Metering:** Implemented via `SCVBroker` using single consolidated `SendReply.kr` synthesis nodes running at 15–30 Hz.
2. **Audio Waveform Scopes:** Uses hardware-accelerated `ScopeView` bound to private internal `ScopeOut2` diagnostic buffers in shared memory.
3. **FFT Transports:** Uses server-side 2048-point FFT pipelines.
4. **Decoupled Architecture:** Window resize, font scale changes, and theme swaps operate purely in sclang and Qt `UserView`/`Pen` without restarting or recompiling audio SynthDefs.

---

## 4. Phase II Gap Analysis (What is Missing for Phase II)

1. **Public Brand Transition:**
   - Add `Probe` top-level facade while preserving backwards-compatibility with existing `SCVisuals` calls.
2. **Server Instance Methods:**
   - Add non-conflicting convenience methods `s.meter2`, `s.scope2`, `s.freqscope2`, `s.dashboard`, `s.waterfall`, `s.spectrogram`, `s.loudness`, `s.spectralDashboard`.
   - Preserve vanilla `s.meter`, `s.scope`, `s.freqscope` without modifications.
3. **Internal Signal `.trace` Interface:**
   - Graph-time pass-through observation: `{ SinOsc.ar(440, 0, 0.1).trace }.play`.
   - Audio rate: automatic waveform scope window.
   - Control rate: scrolling numerical telemetry history.
   - Transparent pass-through returning original UGen expression.
4. **Advanced Spectral Analysis Dashboard:**
   - Dedicated panel hosting Spectral Band Analyzer (FFT-aggregated bands) + 8 spectral descriptors:
     - Spectral centroid history
     - Spectral flatness history
     - Spectral flux / onset timeline
     - Spectral roll-off
     - Chromagram (12 pitch classes)
     - Pitch & harmonic tracker
     - Multi-resolution spectrogram
     - Spectral comparison / difference (A vs B)
5. **Stethoscope / FreqScope Audit:**
   - Tier A/B/C enhancements: triggered display, persistence fading, and cursor peak readouts.
