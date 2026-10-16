# P0 Review — SCVisuals Audit, Spikes & Baseline Profiling

**Date:** 2026-10-15  
**Auditor / Engineer:** Antigravity Pairing Agent  
**Stage Gate:** Gate G0 (Stop for human evaluation)  

---

## 1. Upstream SuperCollider Commit & Release

- **SuperCollider Version:** `3.14.1`
- **Git Tag:** `Version-3.14.1`
- **Git Commit:** `426edf656be91e9f6974d0e515dbe8ec6ffbe630`
- **Qt Version:** `6.7.3` (via `QtGUI`, backing `QcScopeShm`, `QImage`, `QPainter`)
- **Audio Server:** `scsynth` (local shared memory enabled)
- **Host System:** macOS 26.6.2 (Darwin 25.6.0 arm64, Apple Silicon)
- **Tested Audio Rates & Buffers:** 48,000 Hz / 44,100 Hz, hardware block size 512, engine block size 64

---

## 2. Implemented Files & Deliverables

```text
SCVisuals/
├── SCVisuals.quark                      # Official Quark package manifest
├── README.md                            # Project overview and instructions
├── LICENSE                              # GNU General Public License v3.0
├── examples/
│   ├── spike-01-meter.scd               # Prototype: Vertical peak/RMS meter
│   ├── spike-02-scope.scd               # Prototype: Themed ScopeView wrapper
│   ├── spike-03-spectrum.scd            # Prototype: Themed FreqScope wrapper
│   └── spike-04-spectrogram.scd         # Prototype: GUI-only scrolling spectrogram
├── benchmarks/
│   ├── baseline-method.md               # Controlled profiling methodology
│   ├── baseline.scd                     # Automated benchmarking test suite
│   └── baseline.csv                     # Measured empirical benchmark data
└── docs/
    ├── architecture/
    │   ├── source-audit.md              # In-depth audit of 5 core SC classes & Quarks
    │   └── compatibility.md             # Platform & server capability guide
    ├── screenshots/
    │   ├── spike-01-meter.png           # Live screenshot of Meter spike
    │   ├── spike-02-scope.png           # Live screenshot of Scope spike
    │   ├── spike-03-spectrum.png        # Live screenshot of Spectrum spike
    │   └── spike-04-spectrogram.png     # Live screenshot of Spectrogram spike
    └── phase-reports/
        └── P0-REPORT.md                 # This stage report
```

---

## 3. Runnable Demonstrations

All four prototype spikes are self-contained executable scripts tested in live SuperCollider:

1. **`spike-01-meter.scd`**: Vertical compact ServerMeter prototype. Preserves vertical channel strips (inputs/outputs grouped), adds legible dB tick ruler (0, -6, -12, -18, -24, -36, -48, -60, -80 dB), distinct RMS bar and peak-hold indicator, Graphite dark theme (`#171A20`), and AppClock-coalesced updates.
2. **`spike-02-scope.scd`**: Themed oscilloscope wrapping native `ScopeView` via `ScopeOut2`. Dark panel styling, multi-trace wave colors (mint `#7ED6BA` and gold `#E5B95F`), transparent `UserView` grid overlay (+1.0, 0.0, -1.0 lines and divisions), and clean lifecycle.
3. **`spike-03-spectrum.scd`**: Themed spectrum analyzer wrapping the verified FreqScope FFT pipeline (2048 FFT, hop 0.75, Hann window, `PV_MagSmear`, phasor readout, `ScopeOut2`). Features logarithmic and linear frequency markings, dB ruler, and dark styling.
4. **`spike-04-spectrogram.scd`**: Pure GUI spectrogram prototype utilizing a preallocated 2D raster `Image` with circular ring-buffer blit (zero data copy), synthetic spectral frames, 25 FPS update rate, palette color mapping, and freeze/clear controls. Memory remains strictly bounded.

---

## 4. Tests Summary

| Test Area | Status | Notes |
|---|---|---|
| SC 3.14.1 & Qt 6.7.3 Compilation | **PASSED** | Compiled 630 files in 0.27s without errors. |
| Server Boot & Shared Memory | **PASSED** | `scsynth` booted on 127.0.0.1:57110, `QcScopeShm` initialized. |
| Spike 01 Execution & Capture | **PASSED** | Rendered, animated with live test signal, captured PNG. |
| Spike 02 Execution & Capture | **PASSED** | `ScopeOut2` plotted stereo sine signal cleanly, captured PNG. |
| Spike 03 Execution & Capture | **PASSED** | FFT pipeline plotted harmonic peaks accurately, captured PNG. |
| Spike 04 Execution & Capture | **PASSED** | 25 FPS circular ring blit animated smoothly, captured PNG. |
| Automated Baseline Benchmark | **PASSED** | Ran 8 distinct conditions over 20+ seconds, generated CSV. |
| Remote Server Scope Support | **NOT SUPPORTED** | Verified: `ScopeView` requires local shared memory; remote server scoping must be treated as a separate fallback. |

---

## 5. Performance vs Built-in Reference

Empirical measurements gathered from `SCVisuals/benchmarks/baseline.csv` on macOS arm64 (Apple Silicon, 48 kHz, block size 512, stereo sine + noise test signal):

| Condition / Tool | Server `avgCPU` (%) | Server `peakCPU` (%) | Synth Count | UGen Count | Audio Graph Regression? |
|---|---|---|---|---|---|
| **Audio Graph Alone** | 0.30% | 0.38% | 2 | 18 | Baseline reference |
| **Stock `ServerMeter`** | 0.32% | 0.54% | 4 | 23 | +0.02% avg, +0.16% peak |
| **Spike 01 `SCVMeter`** | **0.32%** | **0.38%** | 4 | 23 | **Matches stock avgCPU; lower peakCPU** |
| **Stock `Stethoscope`** | 0.30% | 0.38% | 3 | 26 | +0.00% avg |
| **Spike 02 `SCVScope`** | **0.29%** | **0.39%** | 3 | 20 | **Identical DSP cost to stock scope** |
| **Stock `FreqScope`** | 0.63% | 0.99% | 3 | 43 | +0.33% avg, +0.61% peak |
| **Spike 03 `SCVSpectrum`**| **0.47%** | **0.73%** | 3 | 36 | **Lower CPU than stock FreqScope** |
| **Spike 04 `SCVSpectrogram`**| **0.27%** | **0.39%** | 2 | 18 | **Zero added audio server DSP load** |

---

## 6. Measurement Correctness & Known Limitations

1. **Meter:**
   - Values are true unwindowed sample peak and RMS over the update window (`SendPeakRMS`), calibrated from 0 to -80 dBFS.
   - Distinct from true-peak (inter-sample peak) or LUFS (which will be validated in P6).
2. **Scope:**
   - Native `ScopeView` (`QcScopeShm`) does not implement edge triggering or zero-crossing stabilization. The waveform rolls unless the signal frequency is an exact multiple of the window cycle.
   - Grid and dB rulers cannot be drawn by `ScopeView` itself, but our transparent `UserView` overlay technique solves this with minimal GUI cost.
3. **Spectrum:**
   - Preserves FreqScope's 2048-point Hann window and `PV_MagSmear`.
   - Logarithmic frequency axis uses exponential phasor spacing.
4. **Spectrogram:**
   - Spike 04 uses synthetic frames only. The real FFT-to-sclang transport feasibility is the subject of Stage Gate P4.

---

## 7. Resource Lifecycle Results

- All four spikes implement explicit `onClose` handlers that free Synths, OSC responders, buffers, and routines.
- In automated test runs, server node counts and buffer counts return to initial baseline after window closure.
- No orphan OSC listeners or uncollected `Image` memory were detected.

---

## 8. Assumptions Disproven or Decisions Changed

1. **Assumption:** `ScopeView` cannot be styled or given ruler axes without C++ modifications.
   - **Disproven:** Tested and verified that `ScopeView.background_`, `waveColors_`, `fill_`, `style_`, and a superimposed transparent `UserView` grid overlay provide full styling and legible axes in pure sclang without modifying C++.
2. **Assumption:** `FreqScope` provides an array of FFT magnitude bins to sclang that can be directly shared with a spectrogram.
   - **Disproven:** `FreqScope` converts FFT magnitudes into an audio-rate phasor signal and writes it to a `ScopeOut2` buffer for native oscilloscope drawing. It does **not** expose spectral arrays to sclang. Therefore, P4 must evaluate pure-SC vs buffer polling vs shared memory before implementing the production spectrogram.
3. **Stock ServerMeter Inefficiency Identified:** Stock `ServerMeterView` registers an independent `OSCFunc` for every single open window, flooding the AppClock queue with uncoalesced `.defer` calls. SCVisuals will resolve this in P1 with a centralized broker.

---

## 9. Questions Needing User Approval (Gate G0)

1. **Visual Direction:** Do the compact vertical layout, legible dB ruler, and Graphite palette meet the desired aesthetic? (Screenshots available in `SCVisuals/docs/screenshots/`).
2. **Scope Overlays:** Does the transparent `UserView` grid overlay (+1.0 / 0.0 / -1.0 guidelines, vertical divisions) provide sufficient visual context for the oscilloscope without requiring native C++ additions?
3. **Next Phase Approval:** Proceed to **Phase P1** (Quark class skeleton, `SCVTheme` engine with 6 palettes and density presets, and centralized `SCVMeasurementBroker`)?

---

## 10. Recommended Next Phase

**Proceed to Phase P1 (Quark skeleton, theme engine, and measurement broker).**
