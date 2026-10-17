# SuperCollider Visualization Suite — Project Iteration Log & Decision Record

**Project:** Probe (formerly developed under the working name `SCVisuals`)  
**Target Environment:** SuperCollider 3.14+ (macOS, Linux, Windows)  
**Repository Path:** `/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals`  
**Last Updated:** 2026-10-17  

---

## Executive Summary & Engineering Philosophy

This document serves as the persistent historical record of all development iterations, design decisions, performance audits, and user-directed adjustments for the SuperCollider real-time visualization suite.

### Core Principles
1. **Audio Safety First:** DSP synths operate with zero memory allocations, non-blocking asynchronous OSC/bus communications, and strict bounds on control-rate telemetry.
2. **Centralized Transport (`SCVBroker`):** Multiple visualization windows monitoring the same bus share a single underlying analysis synth and reply listener via reference counting.
3. **Decoupled Presentation:** GUI theme switching, density changes, font scaling, and window resizing never reconstruct audio DSP graphs.
4. **Iterative Refinement:** Every modification is verified against live audio synthesis, unit test suites, and visual screenshot fixtures.

---

## Chronological Iteration History

### Iteration 0: Visual Baseline & Architecture Design (Phase P0)
- **Focus:** Interface aesthetics, palette definitions, and architectural blueprint.
- **Key Decisions:**
  - Defined six professional laboratory color palettes: `Graphite` (default dark), `Phosphor` (vintage CRT green), `Ice` (high-tech cyan/blue), `Amber` (warm analog), `Paper` (high-contrast daylight light theme), and `Monochrome` (clean grayscale).
  - Selected a copy-on-write immutable architecture for `SCVTheme` to guarantee thread-safe GUI updates.
  - Established initial vertical layout targets inspired by hardware audio analyzers (Audio Precision, Rohde & Schwarz).
- **Deliverable:** `docs/phase-reports/P0-REPORT.md`.

---

### Iteration 1: Centralized Telemetry Broker (Phase P1)
- **Focus:** Backend audio-to-GUI communication.
- **Key Decisions:**
  - Implemented `SCVBroker` singleton to eliminate duplicate analysis synths.
  - Subscriptions keyed by `(server, busType, busIndex, numChannels)`.
  - Reference counted: analysis synth auto-frees when subscriber count reaches 0.
  - Normalized polling intervals (10–30 Hz) using `SendReply.kr` to prevent client-server OSC saturation.
- **Deliverable:** `docs/phase-reports/P1-REPORT.md`, `tests/SCVBroker_unittest.scd`.

---

### Iteration 2: High-Density Server Meter (Phase P2)
- **Focus:** Multi-channel input/output level metering.
- **Key Decisions:**
  - Implemented `SCVMeter` and `SCVMeterView`.
  - Independent channel selection for inputs and outputs.
  - Peak and RMS segmented metering with 1.5-second peak hold and 0.5-second clip warning indicator.
  - Three density modes: `\micro` (10px wide), `\compact` (16px wide), and `\extended` (24px wide).
- **Deliverable:** `docs/phase-reports/P2-REPORT.md`, `tests/SCVMeter_unittest.scd`.

---

### Iteration 3: Waveform Scope & Spectrum Analyzer (Phase P3)
- **Focus:** Time-domain and frequency-domain core visualizers.
- **Key Decisions:**
  - Leveraged SuperCollider's hardware-accelerated `ScopeView` backed by local shared-memory `ScopeOut2` buffers.
  - `SCVScope`: Overlay and stacked multi-channel waveform viewing with calibrated amplitude ticks (+1.0, 0.0, -1.0).
  - `SCVSpectrum`: 2048-point FFT magnitude scanning with logarithmic frequency grid (100 Hz – 20 kHz), calibrated dBFS scale (-96 dB to 0 dB), and mouse cursor frequency readout.
- **Deliverable:** `docs/phase-reports/P3-REPORT.md`, `tests/SCVScope_unittest.scd`, `tests/SCVSpectrum_unittest.scd`.

---

### Iteration 4: Performance & Audio Safety Benchmark (Phase P4)
- **Focus:** Resource overhead and audio thread safety under load.
- **Key Decisions:**
  - Audio CPU overhead confirmed under 0.6% on 2-channel monitoring.
  - GUI drawing bounded to 25–30 FPS via `AppClock` throttled view refreshes.
  - Confirmed zero allocations or blocking calls on the SuperCollider real-time audio thread.
- **Deliverable:** `docs/phase-reports/P4-REPORT.md`.

---

### Iteration 5: Advanced Spectral Suite (Phase P5)
- **Focus:** Time-frequency representations and history telemetry.
- **Key Decisions:**
  - `SCVSpectrogram`: Circular ring-buffer image blit rendering scrolling time-frequency density with customizable color look-up tables.
  - `SCVWaterfall`: 2.5D pseudo-isometric 48-line spectral waterfall display drawing newest to oldest traces with dynamic depth occlusion.
  - `SCVHistory`: Multi-channel scrolling numerical telemetry history (peak/RMS envelopes over time).
- **Deliverable:** `docs/phase-reports/P5-REPORT.md`, `tests/SCVSpectrogram_unittest.scd`.

---

### Iteration 6: ITU-R BS.1770 Broadcast Loudness Meter (Phase P6)
- **Focus:** Professional broadcast loudness compliance metering.
- **Key Decisions:**
  - Implemented K-weighting pre-filter and revised high-pass filter in `SCVLoudnessSynthDef`.
  - Triple bar meter for Momentary (M, 400 ms), Short-term (S, 3 s), and Integrated (I) loudness.
  - Loudness Range (LRA), delta relative to target, and true-peak estimation.
  - Selectable loudness reference targets: `-23.0 LUFS` (EBU R128) and `-14.0 LUFS` (Web streaming).
- **Deliverable:** `docs/phase-reports/P6-REPORT.md`, `tests/SCVLoudness_unittest.scd`.

---

### Iteration 7: Unified Multi-Panel Dashboard & Vectorscope (Phase P7)
- **Focus:** High-density command center combining all tools.
- **Key Decisions:**
  - Implemented `SCVDashboard` embedding Meter, Scope, Spectrum, Loudness, and Vectorscope in a unified grid.
  - Global control bar: theme selector, LUFS target selector, global freeze toggle (`Space`), reset (`r`), and broker connection status.
  - `SCVVectorscope`: Polar Lissajous (M/S X-Y) plot with real-time stereo phase correlation [-1.0, +1.0] and L/R balance meters.
- **Deliverable:** `docs/phase-reports/P7-REPORT.md`, `tests/SCVDashboard_unittest.scd`.

---

### Iteration 8: Documentation, Tests & Release Sign-Off (Phase P8)
- **Focus:** Quark distribution, master test runner, and HelpSource documentation.
- **Key Decisions:**
  - Created `SCVisuals.quark` with metadata and dependencies.
  - Automated master test suite runner: `tests/run_all_tests.scd` verifying all 9 unit tests.
  - Complete HelpSource documentation for all classes.
- **Deliverable:** `docs/phase-reports/P8-REPORT.md`, Commit `1e6d97b`.

---

### Iteration 9: Window Resize Handling & CompositeView Clipping Fix
- **Focus:** GUI responsiveness and boundary clipping bugs.
- **User Feedback:** When resizing windows, an inner view clipped the visualization canvas, leaving gray borders.
- **Key Decisions & Changes:**
  - Overhauled `onResize` hooks across all 8 visualizer classes.
  - Added dedicated `prHandleResize(w, h)` methods dynamically updating the bounds of `CompositeView`, `ScopeView`, and overlay `UserView`s.
  - Removed fixed-width constraints that caused clipping when windows were expanded or resized on high-DPI displays.
- **Deliverables:** Commits `51e428a`, `c8f6c0d`, `docs/phase-reports/P9-REPORT.md`.

---

### Iteration 10: Global Font Scaling & Spectrum Smoothing Exploration
- **Focus:** Readability scaling and spectral visual stability.
- **User Feedback:** Requested global font size control that scales the whole layout properly, and investigated spectrum smoothing.
- **Key Decisions & Changes:**
  - Added `fontScale` parameter to `SCVTheme` (range 0.6x to 2.5x) and top-level `SCVisuals.fontScale = scale`.
  - Added Font dropdown menu to the Dashboard (`100%`, `115%`, `125%`, `140%`, `150%`).
  - Added logarithmic dB ticks decimation when font scale > 1.2 to prevent label collisions.
  - Explored PV UGen real-time magnitude smoothing on the audio thread.
- **Deliverables:** Commit `3277b50`, `docs/phase-reports/P10-REPORT.md`.

---

### Iteration 11: Button Padding Scaling & Rollback of PV Smoothing
- **Focus:** Button border text clipping and spectral ripple artifacts.
- **User Feedback:** Font was being clipped by button borders (especially in Paper theme); PV smoothing introduced ripples at sharp transient transitions.
- **Key Decisions & Changes:**
  - **Rolled back PV smoothing** completely. Restored raw, razor-sharp FFT scanning with zero transient ripples or delay artifacts.
  - Added responsive button padding and proportional button height/width calculations in the dashboard and loudness panels.
  - Verified crisp rendering across all six palettes.
- **Deliverables:** Commit `d860b3d`, `docs/phase-reports/P11-REPORT.md`.

---

### Iteration 12: Default 125% Font Scaling, Tight Box Layout & Phase II Transition
- **Focus:** Establishing 125% as the production default, tightening control boxes, and establishing the Phase II roadmap.
- **User Feedback:**
  - Keep default font size at 125%.
  - Make the boxes with letters (buttons, dropdowns, rulers) a tight, sleek layout without bulky margins or text clipping.
  - Defer full dynamic reframing/resizing focus for later to prioritize Phase II instrumentation features.
  - Create a persistent iteration tracking document.
- **Key Decisions & Changes:**
  1. **Default Font Scale = 1.25:**
     - Set `SCVTheme.defaultFontScale = 1.25;`. All newly instantiated themes and visualizers default to 125% font scale.
     - Dashboard font dropdown automatically initializes to index 2 (`125%`).
  2. **Tight Control Box Layout:**
     - Sleek button height: reduced base from 26px to 22px (`(22 * fs).max(22).min(28)`), giving 28px height at 125% instead of 33px.
     - Compact top bar: reduced top bar height padding from `btnH + 12` to `btnH + 8`, dedicating more vertical canvas space to the visualizers.
     - Snug dropdown widths:
       - Theme menu: tightened to 95px at 125% (fits "Monochrome" snugly).
       - Target menu: tightened to 118px at 125% (fits "-23 LUFS (EBU)").
       - Font menu: tightened to 60px at 125% (fits "125%").
       - Freeze button: tightened to 90px at 125% (fits "Freeze All").
       - Loudness `Ref: -23 LUFS` button: tightened from 150px to 110px.
       - Loudness `Reset ('r')` button: height reduced to 25px.
     - Horizontal spacing tightened from 8–10px gaps to 4–6px between controls.
     - `titleW` adjusted to 181px so `"SCVisuals DASHBOARD"` renders on a single line without wrapping.
  3. **Spectrum Frequency Label Uncluttering:**
     - At fontScale > 1.2, logarithmic frequency labels are intelligently decimated to `[100, 500, 1000, 5000, 10000, 20000]`, preventing overcrowding in mid-bottom dashboard panels while maintaining full scientific accuracy.
  4. **Phase II Roadmap Alignment:**
     - Suite transitioning to **Probe** branding per `Probe_Phase_II_Agentic_Implementation_Plan.md`.
     - Preserving existing stable classes while preparing non-conflicting Server shortcuts (`s.meter2`, `s.scope2`, `s.freqscope2`, `s.spectralDashboard`) and graph-time transparent `.trace`.
- **Deliverables:** `docs/PROJECT_ITERATION_LOG.md`, `docs/phase-reports/P12-REPORT.md`, Commit `74c2d3c`.

---

### Iteration 13: Phase II Gate G0 Discovery & Namespace Audit
- **Focus:** Complete codebase inventory, symbol collision analysis, transport audit, and baseline benchmarking before initiating Phase II changes.
- **Key Findings & Discoveries:**
  1. **Namespace Clearance:** `Probe` is 100% free with 0 collisions. `Monitor` is a hard collision with JITLib core (`InBus.sc`) and is permanently rejected.
  2. **Core Server Invariants:** `s.meter`, `s.scope`, and `s.freqscope` are core methods. Non-conflicting shortcuts `s.meter2`, `s.scope2`, `s.freqscope2`, `s.dashboard`, `s.spectralDashboard` are 100% free.
  3. **Baseline Resource Usage:** Idle CPU is 0.00% (0 nodes). Full dashboard is 0.42% net audio CPU with 0 leaks across open/close cycles.
  4. **Cleaned Stale Artifacts:** Purged stale cached help file `~/Library/Application Support/SuperCollider/Help/Classes/SCVisuals.html`. Verified no duplicates in `Extensions/` or `downloaded-quarks/`.
- **Deliverables:** `docs/phase2/existing-state.md`, `docs/phase2/feature-inventory.csv`, `docs/phase2/namespace-audit.md`, `docs/phase2/architecture-and-transport.md`, `benchmarks/phase2-baseline/results.md`, `docs/phase2/compatibility-fixtures.md`, Commit `f8c7b93`.

---

### Iteration 14: Phase II Stage P1 — Public API, Probe Facade & Server Extensions
- **Focus:** Establishing the `Probe` facade, non-conflicting `Server` convenience methods, and updating window titles while preserving vanilla SC methods.
- **Key Decisions & Changes:**
  1. **`Probe` Class Facade (`classes/Probe.sc`):**
     - Single top-level entry point for all 9 visualizers, themes, broker, and font scaling (`Probe.meter`, `Probe.scope`, `Probe.freqscope`, `Probe.dashboard`, etc.).
  2. **Server Class Extensions (`classes/extServerProbe.sc`):**
     - Added `s.meter2`, `s.scope2`, `s.freqscope2`, `s.spectrum`, `s.spectrum2`, `s.spectrogram`, `s.waterfall`, `s.history`, `s.loudness`, `s.vectorscope`, `s.dashboard`, `s.spectralDashboard`, `s.bandAnalyzer`.
     - Zero alterations to core vanilla `s.meter`, `s.scope`, `s.freqscope`.
  3. **Branding & Window Titles:**
     - Updated all visualizer window titles to `" ... — Probe"` and dashboard title to `"PROBE DASHBOARD"`.
     - Created `Probe.quark` (v0.2.0) and updated `SCVisuals.quark`.
  4. **Qt Teardown Hardening:**
     - Added defensive `isClosed.not` and `bounds.notNil` checks in `view.onResize` across all visualizers, eliminating transient Qt teardown warnings.
  5. **Verification & Tests:**
     - Created `tests/Probe_P1_unittest.scd` (39 assertions passed cleanly).
     - Executed full master test suite (`tests/run_all_tests.scd`): 10/10 suites passed cleanly.
- **Deliverables:** `classes/Probe.sc`, `classes/extServerProbe.sc`, `Probe.quark`, `tests/Probe_P1_unittest.scd`, `examples/phase2/01_server_shortcuts.scd`, `reports/phase2/P1.md`.

---

## Decision Matrix & Invariant Log

| Parameter / Feature | Chosen Design | Rationale | Invariant Status |
|---|---|---|---|
| **Suite Name** | `Probe` (migrating from `SCVisuals`) | Professional instrumentation terminology; avoids `SC` legacy namespace clutter and collision with core `Monitor`. | Active Invariant (Phase II) |
| **Default Font Scale** | `1.25` (125%) | Optimum balance of legibility on modern high-DPI retina displays without overflowing panels. | Active Invariant |
| **Control Layout** | Tight (28px buttons at 1.25, 4–6px gaps) | Sleek laboratory instrument aesthetic; eliminates bloated dead space. | Active Invariant |
| **FFT Smoothing** | Raw FFT scan (no PV smoothing) | PV smoothing introduced smearing and edge ripples; raw FFT preserves instant transient response. | Active Invariant |
| **Telemetry Transport** | `SCVBroker` Reference-Counted Synths | Prevents duplicate FFT/peak-RMS synths on identical bus monitoring. | Active Invariant |
| **Core SC Overrides** | Forbidden (`s.meter`, `s.scope`, `s.freqscope` unchanged) | Strict non-interference with vanilla SuperCollider workflows. | Permanent Invariant |
| **Server Shortcuts** | `s.meter2`, `s.scope2`, `s.freqscope2`, `s.dashboard` | Non-conflicting ergonomic shortcuts following SuperCollider numbering conventions. | Active Invariant |

