# SCVisuals — Baseline Measurement Methodology (P0)

**Date:** 2026-10-15  
**Auditor:** Antigravity Pairing Agent  
**Environment:** macOS 26.6.2 (Darwin arm64), SuperCollider 3.14.1 (commit `426edf6`), Qt 6.7.3, scsynth  

---

## 1. Objectives

The goal of baseline profiling in Phase P0 is to establish rigorous, reproducible performance metrics for:
1. **Idle SuperCollider baseline** (scsynth booted, audio driver active, 0 synthesis nodes).
2. **Stock SuperCollider analysis tools:**
   - `s.meter` (`ServerMeterView` on 2 ins / 2 outs)
   - `s.scope` (`Stethoscope` on stereo bus)
   - `s.freqscope` (`FreqScope` on bus 0, FFT size 2048)
3. **SCVisuals P0 minimal prototypes (spikes):**
   - Spike 01: Vertical Peak/RMS meter (2 ins / 2 outs, 15 Hz update)
   - Spike 02: Themed ScopeView wrapper (stereo bus, 4096 buffer)
   - Spike 03: Themed FreqScope wrapper (bus 0, FFT size 2048)
   - Spike 04: Spectrogram raster (synthetic 120 bands, 25 FPS)

---

## 2. Experimental Controls

To ensure scientific comparability:
- **Audio hardware:** Built-in audio driver, sample rate pinned to 48,000 Hz, block size pinned to 512.
- **Audio test signal:** Standard stereo test graph:
  ```supercollider
  {
      var sig = [
          SinOsc.ar(440) * 0.4,
          PinkNoise.ar(0.15)
      ];
      Out.ar(0, sig);
  }
  ```
- **Sampling window:** Each condition is stabilized for 2.0 seconds, followed by 10 repeated samples taken every 200 ms to calculate mean and peak values.
- **Separation of cost centers:**
  - Audio server DSP load: `s.avgCPU`, `s.peakCPU`, `s.numSynths`, `s.numUGens`.
  - GUI / sclang activity: OSC packet rate and AppClock update frequency.

---

## 3. Metric Definitions

- `avgCPU (%)`: SuperCollider audio server average CPU load reported by `scsynth` telemetry.
- `peakCPU (%)`: Audio server peak buffer-processing time percentage.
- `Synth Count`: Number of active nodes in `scsynth`.
- `UGen Count`: Number of active Unit Generators in `scsynth`.
- `Update Rate (Hz)`: GUI refresh or measurement rate.
