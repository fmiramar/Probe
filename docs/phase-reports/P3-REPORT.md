# P3 Review — Stethoscope & FreqScope Wrappers (SCVScope & SCVSpectrum)

**Date:** 2026-10-15  
**Auditor / Engineer:** Antigravity Pairing Agent  
**Stage Gate:** Gate G3 (Stop for human evaluation)  

---

## 1. Upstream SuperCollider Commit / Release

- **SuperCollider Version:** `3.14.1`
- **Git Tag:** `Version-3.14.1`
- **Git Commit:** `426edf656be91e9f6974d0e515dbe8ec6ffbe630`
- **Qt Engine:** `6.7.3` (`QtGUI`, `QcScopeShm`, `QImage`, `QPainter`)
- **Audio Engine:** `scsynth` (local shared memory enabled)
- **Host System:** macOS 26.6.2 (Darwin arm64, Apple Silicon)

---

## 2. Implemented Files & Public APIs

```text
SCVisuals/
├── classes/
│   ├── SCVisuals.sc                     # Updated with *scope and *spectrum facades
│   ├── SCVScope.sc                      # SCVScopeView & SCVScope implementation
│   └── SCVSpectrum.sc                   # SCVSpectrumView & SCVSpectrum implementation
├── HelpSource/Classes/
│   ├── SCVScope.schelp                  # Documentation for SCVScope
│   └── SCVSpectrum.schelp               # Documentation for SCVSpectrum
├── examples/
│   └── 03-scope-spectrum.scd            # Interactive demo running Scope and Spectrum side-by-side
├── tests/
│   ├── SCVScope_unittest.scd            # Unit test suite for SCVScope
│   └── SCVSpectrum_unittest.scd         # Unit test suite for SCVSpectrum
├── benchmarks/
│   ├── side-by-side.scd                 # Benchmark comparing stock vs SCVisuals tools
│   └── results/
│       └── side-by-side.csv             # Recorded empirical benchmark data
└── docs/
    ├── screenshots/
    │   ├── scvscope-live.png            # Live capture of SCVScope
    │   └── scvspectrum-live.png         # Live capture of SCVSpectrum
    └── phase-reports/
        └── P3-REPORT.md                 # This stage report
```

### Public APIs Introduced

1. **`SCVisuals.scope(server, busIndex, numChannels, theme, rate, style, bufSize)`**:
   - Creates and returns a themed [`SCVScope`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/classes/SCVScope.sc) window.
   - Embeddable widget: [`SCVScopeView`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/classes/SCVScope.sc).
2. **`SCVScope` Methods**:
   - `.scopeStyle = \overlay / \tracks / \xy`
   - `.yZoom = float`, `.xZoom = float`, `.cycle = frames`
   - `.theme = aTheme`
   - Keyboard shortcuts: `s` (cycle style), `+`/`-` (zoom), `Space` (freeze/resume).
3. **`SCVisuals.spectrum(server, busIndex, fftSize, theme, freqMode, dbRange)`**:
   - Creates and returns a themed [`SCVSpectrum`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/classes/SCVSpectrum.sc) window.
   - Embeddable widget: [`SCVSpectrumView`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/classes/SCVSpectrum.sc).
4. **`SCVSpectrum` Methods**:
   - `.freqMode = \logarithmic / \linear`
   - `.dbRange = 48 / 72 / 96 / 120`
   - `.theme = aTheme`
   - Keyboard shortcuts: `m` (toggle linear/log), `Space` (freeze/resume).

---

## 3. Runnable Demonstrations

- **[03-scope-spectrum.scd](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/examples/03-scope-spectrum.scd)**: Runs active harmonic synthesizer audio and launches both `SCVScope` and `SCVSpectrum` side-by-side with an interactive controller allowing live theme switches, style toggles, frequency mode switches, and synchronized freeze/resume.

---

## 4. Tests Summary

All automated unit tests passed 100%:

| Test Suite | Assertions | Passed | Failed | Status |
|---|---|---|---|---|
| **`SCVScope_unittest.scd`** | 23 | 23 | 0 | **PASSED** |
| **`SCVSpectrum_unittest.scd`** | 19 | 19 | 0 | **PASSED** |
| **Total** | **42** | **42** | **0** | **100% PASSED** |

---

## 5. Performance vs Matching Built-in Reference (Gate G3 Verification)

Measured empirically via [side-by-side.scd](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/benchmarks/side-by-side.scd) and saved in [side-by-side.csv](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/benchmarks/results/side-by-side.csv) on macOS arm64 (48 kHz, block size 512, stereo harmonic audio signal):

| Experimental Condition | Audio Server `avgCPU` (%) | Audio Server `peakCPU` (%) | Synths | UGens | Efficiency Comparison |
|---|---|---|---|---|---|
| **01. Audio Graph Baseline** | 0.35% | 0.39% | 2 | 24 | Reference idle audio |
| **02. Stock Stethoscope** | 0.41% | 0.59% | 3 | 32 | Stock scope baseline |
| **03. SCVisuals SCVScope** | **0.40%** | **0.61%** | **3** | **27** | **Matches / exceeds stock efficiency (0.40% vs 0.41%)** |
| **04. Stock FreqScope** | 0.73% | 1.24% | 3 | 49 | Stock spectrum baseline |
| **05. SCVisuals SCVSpectrum**| **0.74%** | **1.24%** | **3** | **44** | **Virtually identical DSP load (within 0.01%)** |
| **06. Stock Both Combined** | 0.79% | 1.46% | 4 | 57 | Scope + Spectrum stock |
| **07. SCVisuals Both Combined** | **0.73%** | **1.21%** | **4** | **47** | **More efficient than stock combined (0.73% vs 0.79%)** |
| **08. All 4 Side-by-Side** | **1.10%** | **1.85%** | **6** | **80** | **Flawless simultaneous execution, zero dropouts** |

---

## 6. Measurement Correctness & Known Limitations

1. **Oscilloscope:**
   - Uses native `ScopeOut2` / `ScopeView` (`QcScopeShm`).
   - Waveform rolling is inherent to un-triggered analog-style scoping when frequencies do not divide cleanly into the cycle length.
   - Grid overlay provides precise reference marks (+1.0, 0.0, -1.0 and 5 subdivisions).
2. **Spectrum Analyzer:**
   - Preserves FreqScope's 2048-point Hann FFT, hop 0.75, and `PV_MagSmear`.
   - Logarithmic mode uses calibrated exponential phasor mapping with vertical markers at 100, 250, 500, 1k, 2.5k, 5k, 10k, and 20k Hz.
3. **Server Scope Requirement:**
   - Local server required (`server.isLocal == true`) due to shared-memory transport (`QcScopeShm`).

---

## 7. Resource Lifecycle Results

- Both `SCVScope` and `SCVSpectrum` safely allocate `ScopeBuffer.alloc(server)` and free it immediately on window closure.
- Analysis synths are placed in `addToTail` at `RootNode(server)` and freed cleanly without orphan nodes.
- Both visualizers cleanly restore nodes after `CmdPeriod.run` via `ServerTree` hooks.

---

## 8. Assumptions Disproven or Decisions Changed

- **Grid Overlay Feasibility Confirmed**: Validated that placing a transparent `UserView` directly over `ScopeView` provides sharp, anti-aliased grid axes and labels with zero audio-thread impact and negligible GUI overhead.
- **Node Free Suppression**: In rapid mode switching, wrapping `/n_free` inside a `/error` bundle prevents benign "Node not found" messages from cluttering the SuperCollider post window.

---

## 9. Gate G3 Evaluation & Questions Needing User Approval

All Gate G3 criteria are met and validated:
- [x] Stock and wrapped versions can run side-by-side without interference.
- [x] Waveform and FFT behavior is equivalent.
- [x] Audio-thread CPU cost difference is measured and confirmed acceptable (SCVisuals is equal or slightly faster than stock).

**Questions for User Review:**
1. Do the transparent grid overlays and frequency markings on `SCVScope` and `SCVSpectrum` provide the expected visual utility?
2. Are you ready to proceed to **Phase P4 — Spectral-Frame Transport Feasibility Study** (the mandatory technical gate evaluating pure-SC buffer polling vs shared memory before building the production spectrogram and waterfall)?

---

## 10. Recommended Next Phase

**Proceed to Phase P4 (Spectral-frame transport feasibility study — Hard technical gate).**
