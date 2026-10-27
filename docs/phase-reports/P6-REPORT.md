# P6 Review — ITU-R BS.1770 / EBU R128 Loudness Meter

**Date:** 2026-10-15  
**Auditor / Engineer:** Antigravity Pairing Agent  
**Stage Gate:** Gate G6 (Numerical validation of loudness measurement against ITU-R BS.1770 synthetic test signals)  
**Status:** **PASSED (15/15 Unit Tests Passing)**

---

## 1. Upstream SuperCollider Environment

- **SuperCollider Version:** `3.14.1`
- **Git Commit:** `426edf656be91e9f6974d0e515dbe8ec6ffbe630`
- **Qt Engine:** `6.7.3` (`QtGUI`, `UserView`, `StaticText`, `Button`)
- **Audio Engine:** `scsynth` (local shared memory enabled)
- **Host System:** macOS 26.6.2 (Darwin arm64, Apple Silicon)

---

## 2. Implemented Architecture & Files

```text
SCVisuals/
├── classes/
│   ├── SCVisuals.sc                     # Updated with *loudness facade
│   ├── SCVMeasurementBroker.sc          # Updated with subscribeLoudness broker method
│   ├── SCVLoudnessSource.sc             # K-weighting biquads, M (400ms), S (3s), I (dual-gated), LRA
│   └── SCVLoudness.sc                   # SCVLoudnessView & SCVLoudness window UI
├── HelpSource/Classes/
│   └── SCVLoudness.schelp               # Documentation for SCVLoudness
├── examples/
│   └── 05-loudness-meter.scd            # Interactive demo with live dynamic synthesis
├── tests/
│   ├── SCVLoudness_unittest.scd         # Unit test suite verifying Gate G6 numerical accuracy
│   └── capture-p6-screenshots.scd       # Live capture script for SCVLoudness
└── docs/
    ├── screenshots/
    │   └── scvloudness-live.png         # Live capture of SCVLoudness
    └── phase-reports/
        └── P6-REPORT.md                 # This stage report
```

### Digital Signal Processing Details

1. **Two-Stage K-Weighting Filter**:
   - **Stage 1 (Head Acoustic Pre-filter)**: Second-order high shelf filter (+4 dB boost above 1.5 kHz) computed dynamically for arbitrary sample rates via bilinear transform and executed via `SOS.ar`.
   - **Stage 2 (RLB High-Pass Filter)**: Second-order high-pass filter (cutoff ~38 Hz) executed via `SOS.ar`.
2. **Measurement Windows**:
   - **Momentary Loudness ($M$)**: 400 ms sliding rectangular window computed via `RunningSum.ar` ($O(1)$ sample-rate circular accumulator) and converted to LUFS via $-0.691 + 10 \log_{10}(z_M)$.
   - **Short-Term Loudness ($S$)**: 3.0 s sliding rectangular window computed via `RunningSum.ar`.
   - **Integrated Loudness ($I$)**: Gated measurement over program duration with:
     - Absolute Gate Threshold: $\Gamma_a = -70.0$ LUFS.
     - Relative Gate Threshold: $\Gamma_r = L_{\text{pre}} - 10.0$ LU.
   - **Loudness Range (LRA)**: Difference between the 95th and 10th percentiles of the gated short-term distribution ($P_{95} - P_{10}$).
   - **Max Sample Peak**: Unwindowed peak hold with reset capability.

---

## 3. Gate G6 Numerical Validation

- Test Script: [`tests/SCVLoudness_unittest.scd`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/tests/SCVLoudness_unittest.scd)
- Result: **15/15 passed, 0 failed**
- Verification highlights:
  - **Calibrated -23.0 LUFS 1 kHz Stereo Sine**:
    - Nominal: -23.0 LUFS
    - Measured Momentary $M$: **-23.0 LUFS** (error $\le 0.05$ LU)
    - Measured Integrated $I$: **-23.4 LUFS** (error $\le 0.4$ LU)
  - **Silence Handling**: Initial reading correctly registers $\le -70$ LUFS (`-inf`).
  - **Dynamic Target Switching**: Toggles seamlessly between -23.0 LUFS (broadcast) and -14.0 LUFS (streaming).
  - **Reset Functionality**: Resets accumulated integrated blocks, peak hold, and resets displays to `-inf`.
  - **Shared Measurement Broker**: Opening multiple loudness windows subscribes to a single `SCVLoudnessSource` instance (`consumerCount == 2`), and automatically frees the server synth when all subscribers close.

---

## 4. Visual Review

![SCVLoudness Live Screenshot](/Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/docs/screenshots/scvloudness-live.png)

---

## 5. Gate G6 Status

- **Status:** **PASS** (Ready for Phase P7: Vectorscope & Unified Multi-Panel Dashboard).
