# SuperCollider Visualization Suite — Project Iteration Log & Decision Record

**Project:** Probe (formerly developed under the working name `SCVisuals`)  
**Target Environment:** SuperCollider 3.14+ (macOS, Linux, Windows)  
**Repository Path:** `/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals`  
**Last Updated:** 2026-10-24  

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
- **Deliverables:** `classes/Probe.sc`, `classes/extServerProbe.sc`, `Probe.quark`, `tests/Probe_P1_unittest.scd`, `examples/phase2/01_server_shortcuts.scd`, `reports/phase2/P1.md`, Commit `b0eed8f`.

---

### Iteration 15: Phase II Stage P2 — Transparent Internal-Signal Tracing (`.trace`)
- **Focus:** Transparent diagnostic taps (`UGen:trace`, `Array:trace`), dual transport (ScopeOut2 for audio, SendReply for control), and automated GUI lifecycle.
- **Key Decisions & Changes:**
  1. **Dual Transport Architecture:**
     - Audio rate (`\audio`): Defaults to `\scope`. Uses private diagnostic bus + hardware-accelerated `ScopeOut2` buffer. Zero network/OSC overhead, true sample-accurate waveform visualization.
     - Control rate (`\control`): Defaults to `\history`. Uses `SendReply.kr` throttled at 20 Hz. Renders rolling numerical timeline HUD showing current, min, and max values.
     - Explicit mode overrides supported: `\scope`, `\history`, `\meter`, `\spectrum`.
  2. **Pass-Through Transparency:**
     - Wrapped inside `SynthDef.wrap`, returns `this`. The original signal expression is passed to downstream UGens without modification (0 dB gain change, 0 phase shift).
  3. **Multi-Tap Independence & Cascading:**
     - Monotonic tap IDs prevent cross-talk.
     - Window positions cascade dynamically (`+32@+32`), preventing overlap.
  4. **Leak-Free Lifecycle:**
     - Closing any trace window or calling `CmdPeriod` cleanly deallocates private buses, scope buffers, synths, and OSC responders.
  5. **Verification & Benchmarks:**
     - Conducted transport A/B benchmark (1, 8, 32 probes) comparing Route A and Route B.
     - Created `tests/Probe_P2_unittest.scd` (22 assertions passed cleanly).
     - Master test runner (`tests/run_all_tests.scd`): 11/11 suites passed cleanly.
- **Deliverables:** `classes/ProbeTrace.sc`, `classes/extUGenProbe.sc`, `docs/phase2/trace-architecture.md`, `benchmarks/phase2-p2-transport/benchmark_transport.scd`, `benchmarks/phase2-p2-transport/results.md`, `tests/Probe_P2_unittest.scd`, `examples/phase2/02_ugen_trace_audio.scd`, `examples/phase2/03_ugen_trace_kr.scd`, `examples/phase2/04_ugen_trace_many.scd`, `reports/phase2/P2.md`, Commit `6fc56b7`.

---

### Iteration 16: Phase II Stage P3 — Advanced Spectral Dashboard & Spectral Band Analyzer
- **Focus:** 16-band calibrated logarithmic band analyzer, shared spectral telemetry source, descriptor modules (Centroid, Flatness, Roll-off, Flux/Onsets, Pitch, Chroma), and multi-preset Advanced Spectral Dashboard.
- **Key Decisions & Changes:**
  1. **Unified Shared Spectral Source (`ProbeSpectralSource.sc`):**
     - Single shared analysis synth per monitored bus computes 2048 FFT descriptors (`SpecCentroid.kr`, `SpecFlatness.kr`, `SpecPcile.kr`, `Onsets.kr`), pitch tracker (`Pitch.kr`), 16 ISO-spaced filterbank bands, and 12-tone chromagram.
     - Reference-counted integration via `SCVMeasurementBroker:subscribeSpectral`.
  2. **16-Band Spectral Band Analyzer (`ProbeBandAnalyzer.sc`):**
     - Calibrated ISO frequency centers (31 Hz – 16 kHz), segmented vertical level bars (-80 to 0 dBFS), dynamic peak-hold indicators, and theme integration.
  3. **Advanced Spectral Dashboard (`ProbeSpectralDashboard.sc`):**
     - 4 instant-switch presets (`\overview`, `\pitch`, `\timeFreq`, `\compare`).
     - Persistent panel architecture: views are created once and toggled via `visible = true/false`, eliminating ScopeBuffer reallocations, Qt teardown latency, and glitching.
  4. **Scientific Telemetry & Test Verification:**
     - Pure 1000 Hz sine verified: Centroid = 1002.0 Hz, Pitch = 1000.0 Hz, Confidence = 1.0.
     - Created `tests/Probe_P3_unittest.scd` (28 assertions passed cleanly).
     - Master test runner (`tests/run_all_tests.scd`): 12/12 suites passed cleanly.
- **Deliverables:** `classes/ProbeBandAnalyzer.sc`, `classes/ProbeSpectralSource.sc`, `classes/ProbeSpectralDescriptors.sc`, `classes/ProbeSpectralDashboard.sc`, `tests/Probe_P3_unittest.scd`, `examples/phase2/05_spectral_dashboard.scd`, `examples/phase2/06_spectral_validation.scd`, `reports/phase2/P3.md`.

---

### Iteration 17: Spectral Dashboard Empty Window Fix & Layout Propagation
- **Focus:** Diagnosing and resolving the empty window display bug in `ProbeSpectralDashboard` (`s.spectralDashboard`) and ensuring robust multi-preset layout scaling and ServerTree lifecycle recovery.
- **Root Cause Analysis:**
  1. **Unprotected `server.sampleRate` in Qt `UserView:drawFunc`:**
     - In `classes/SCVSpectrum.sc` (lines 112 & 130), `var nyq = server.sampleRate * 0.5;` failed with `ERROR: Message '*' not understood. RECEIVER: nil` if the server had not yet booted or if `sampleRate` was uninitialized when the GUI opened.
     - In SuperCollider Qt, any unhandled error inside a `UserView:drawFunc` aborts painting and leaves the visualizer window blank.
  2. **Missing Layout Propagation in Container Panels:**
     - `ProbeSpectralDashboardView:prHandleResize` resized the outer `containerView` and panel backgrounds, but did not update inner visualizer bounds (`specOverview`, `bandView`, `descView`, `pitchView`, `chromaView`, `gramView`, `fallView`, `specCompare`, `compareView`) nor invoke their `.prHandleResize`.
     - In addition, the descriptor views (`ProbeDescriptorsView`, `ProbePitchTrackerView`, `ProbeChromagramView`, `ProbeSpectralCompareView`) lacked explicit `prHandleResize(w, h)` methods.
- **Key Fixes & Enhancements Applied:**
  1. **Defensive SampleRate Fallbacks:**
     - Updated `classes/SCVSpectrum.sc` to guard `(server.sampleRate ? 48000.0) * 0.5` across both logarithmic and linear frequency marking algorithms.
  2. **Comprehensive Layout & Resize Propagation:**
     - Updated `ProbeSpectralDashboardView:prHandleResize` to dynamically recalculate and reposition all top control buttons (`txtTitle`, `presetMenu`, `themeMenu`, `fontMenu`, `freezeBtn`, `txtStatus`) and all subviews across all 4 presets.
     - Added `prHandleResize(w, h)` to all 4 classes in `classes/ProbeSpectralDescriptors.sc`.
     - Called `this.prHandleResize(w, h)` at the conclusion of `prBuildUI` and on preset switches.
  3. **Preset Synchronization & ServerTree Lifecycle:**
     - Added programmatic synchronization for `presetMenu.value` in `preset_`.
     - Registered `ProbeSpectralDashboardView` with `ServerTree` and `ServerQuit` for clean recovery across server restarts.
     - Added dynamic broker telemetry polling in `txtStatus` (`"Bus: 0 | FFT: 2048 | Broker: X src / Y sub"`).
- **Verification & Visual Evidence:**
  - Tested live audio playback (`{ Saw.ar([220, 440]) * 0.15 }.play`) with active telemetry across all 4 presets.
  - Captured live screenshots for Overview, Pitch & Chroma, Time-Frequency, Compare, and Resized 1100x680 views.
  - Executed master test suite (`tests/run_all_tests.scd`): 12/12 suites passed cleanly (100%).
- **Deliverables:** `classes/ProbeSpectralDashboard.sc`, `classes/ProbeSpectralDescriptors.sc`, `classes/SCVSpectrum.sc`, Commit `9ea0c1c`.

---

### Iteration 18: Stage P4 — Oscilloscope Tier A/B Trigger Stabilization and Unified FreqScope 3-Mode Switcher
- **Focus:** Implementing oscilloscope calibration and stationary trigger stabilization (Stethoscope Tier A/B) and unified 3-mode visualization switching in FreqScope (`SCVSpectrum`).
- **Key Enhancements Applied:**
  1. **Oscilloscope Tier A (Calibration, Legibility & Ergonomics):**
     - Horizontal time axis calibrated in milliseconds (`ms`), dynamically scaled by sample rate, buffer cycle frames, and zoom level.
     - Vertical amplitude axis with calibrated linear peak levels and dBFS labels (`+1.0 (0 dBFS)`, `+0.5 (-6 dBFS)`, `0.0`, `-0.5`, `-1.0`).
     - Channel chips legend (`[Ch 1]`, `[Ch 2]`) colored according to the active theme palette.
     - Zoom reset and timebase stepping controls (`$r`, `$0`, `$[`, `$]`, `resetZoom`).
  2. **Oscilloscope Tier B (Trigger Stabilization & Toolbar):**
     - Trigger modes: `\free`, `\rising`, `\falling` edge locking with configurable threshold.
     - DSP circular delay buffer (`LocalBuf` + `Phasor.ar` + `Latch.ar` + `BufRd.ar`) that phase-locks periodic signals at zero crossings into a completely stationary display trace. Direct 0-latency bypass when `triggerMode === \free`.
     - Standalone compact toolbar (`Style`, `Timebase`, `Trigger Mode`, `Reset Zoom`, `Freeze`).
  3. **Unified FreqScope (Section 5.4 3-Mode Switcher & Interactive Cursor):**
     - 3-Mode visualization switcher in `SCVSpectrum`: `\line` (Precision Line FFT), `\bands` (16-Band Octave Analyzer via `ProbeBandAnalyzerView`), `\spectrogram` (Scrolling Waterfall Spectrogram via `SCVSpectrogramView`).
     - Broker-based shared FFT pipeline reusing existing spectral analysis without redundant CPU overhead.
     - Interactive cursor readout on `gridOverlay`: crosshair tracking + floating tooltip badge with exact frequency (Hz/kHz) and amplitude (dBFS).
     - Standalone compact toolbar (`Title`, `Mode`, `Scale Linear/Log`, `Freeze`, `Theme`).
  4. **Lifecycle & ScopeBuffer Management:**
     - Explicit `scopeView.stop` invocation on window closing and view destruction, preventing shared memory buffer leaks and `ScopeOut2: Requested scope buffer unavailable!` errors.
- **Verification & Visual Evidence:**
  - Added unit test suite `tests/Probe_P4_unittest.scd` (33 assertions passed cleanly).
  - Master test runner (`tests/run_all_tests.scd`): 13/13 suites passed cleanly (100%).
  - Visual verification with live audio (`Saw.ar(110) + Pulse.ar(220)`) captured:
    - `scope-tier-ab-rising.png`: Locked stationary waveform with `[▲]` badge.
    - `scope-tier-ab-tracks.png`: Multi-lane track view.
    - `spectrum-mode-line.png`: High-precision FFT line spectrum.
    - `spectrum-mode-bands.png`: 16-band ISO octave analyzer with peak hold bars.
    - `spectrum-mode-spectrogram.png`: Real-time waterfall spectrogram.
- **Deliverables:** `classes/SCVScope.sc`, `classes/SCVSpectrum.sc`, `tests/Probe_P4_unittest.scd`, `docs/screenshots/*`, Commit `50efd4e`.


---

### Iteration 19: Stage P5 — Static DSP Graph & Server Inspector Groundwork
- **Focus:** Developing the static SynthDef signal flow Directed Acyclic Graph (DAG) inspector and researching live server inspection boundaries.
- **Key Enhancements Applied:**
  1. **Topological DAG Parsing & Layer Sorting:**
     - Created `ProbeGraphView` in `classes/ProbeInspector.sc`, analyzing `synthDef.children` to compute execution dependencies and topological layer depth.
     - Guaranteed ordering invariant: `Control` UGens in Layer 0, internal arithmetic and DSP in intermediate layers, and `Out` UGens in the terminating layer.
  2. **Vector Rendering Engine:**
     - Smooth curved bezier connection wires (`Pen.curveTo`) indicating signal flow between inputs and outputs.
     - Color-coded rate distinction: Audio rate (`\audio` = Signal cyan), Control rate (`\control` = Warning amber), Scalar/initialization (`\scalar` = Subtext).
     - Responsive box layout preventing overlap across arbitrary numbers of layers and nodes.
  3. **Interactive Inspection:**
     - Mouse-click node selection highlighting upstream inputs and downstream outputs.
     - Footer inspector badge displaying exact UGen index, class name, rate, and connected inputs.
  4. **Public Facade & Shortcuts:**
     - Added `Probe.inspect(synthDef, theme)` and `s.inspect(synthDef, theme)` / `s.inspector(synthDef, theme)`.
     - Supports direct inspection from open audio Functions (`s.inspect({ Saw.ar(220) * 0.15 })`).
- **Verification & Visual Evidence:**
  - Added unit test suite `tests/Probe_P5_unittest.scd` (18 assertions passing).
  - Master test runner (`tests/run_all_tests.scd`): 14/14 suites passed cleanly (100%).
  - Captured live screenshot `docs/screenshots/probe-graph-inspector.png` displaying a 16-UGen, 9-layer synthesis network.
- **Deliverables:** `classes/ProbeInspector.sc`, `classes/Probe.sc`, `classes/extServerProbe.sc`, `tests/Probe_P5_unittest.scd`, `reports/phase2/P5.md`, Commit `356ca70`.


---

### Iteration 20: Stage P6 — Cross-Platform Release, Packaging, Documentation & Final Sign-Off
- **Focus:** Finalizing packaging, Quark configuration, SuperCollider Help documentation, refreshed README, and completing the Phase II Release Definition of Done audit.
- **Key Deliverables & Actions:**
  1. **Documentation & HelpSource:**
     - Created `HelpSource/Classes/Probe.schelp` detailing all instruments, methods, and signal tracing workflows.
     - Updated `README.md` with full `Probe` branding, 12-instrument feature table, high-DPI screenshot gallery, quick start, broker architecture, and measurement standards.
     - Created `examples/phase2/08_dsp_graph_inspector.scd` completing the Phase II example series (8 executable guides).
  2. **Quark Packaging & Dual-Namespace Compatibility:**
     - Configured `Probe.quark` and `SCVisuals.quark` at version `0.2.0`, ensuring smooth backwards compatibility for existing code.
  3. **Master Test Suite Verification:**
     - Executed all 14 test suites in `tests/run_all_tests.scd`: 14/14 suites passed cleanly (100%).
  4. **Phase II Release Audit:**
     - Completed comprehensive audit across all 10 release acceptance criteria in `reports/phase2/P6.md`. Zero regressions, zero memory leaks, and zero audio distortions.
- **Deliverables:** `README.md`, `HelpSource/Classes/Probe.schelp`, `examples/phase2/08_dsp_graph_inspector.scd`, `reports/phase2/P6.md`, Commit `43105ff`.

---

### Iteration 21: Unified Probe Namespace Migration (v0.3.0) & Zero-Warning HelpSource Documentation
- **Focus:** Complete elimination of internal `SCV_` / `SCV*` legacy prefixes across classes, filenames, SynthDefs, OSC paths, and HelpSource; fixing SuperCollider `SCDoc` argument mismatch warnings on `Cmd+D`; delivering seamless 100% backward-compatibility via `ProbeLegacyCompat.sc`.
- **Key Enhancements Applied:**
  1. **SCDoc HelpSource Warnings Resolution (`Cmd+D`):**
     - Resolved SuperCollider documentation compilation warnings reported during class inspection:
       - `Probe *meter`: Added missing `argument:: input` and `argument:: output` doc tags (matching all 6 method parameters).
       - `Probe *scope`: Added missing `argument:: channelOffset` doc tag (matching all 7 method parameters).
       - `Probe *bandAnalyzer`: Added missing `argument:: numBands` doc tag (matching all 3 method parameters).
     - Rectified property setter syntax in all 14 `.schelp` files (documenting `METHOD:: prop` with `argument:: value` instead of illegal `prop_`).
     - Replaced non-standard tags (`KEYBOARD SHORTCUTS::`) with standard `SECTION:: Keyboard Shortcuts`.
     - Validated via `SCDoc.parseAndRender`: **14/14 documentation files parse with 0 warnings and 0 syntax errors**.
  2. **Class & File Namespace Unification:**
     - Renamed 12 instrument and telemetry class files from `SCV*.sc` to `Probe*.sc`:
       - `SCVTheme` -> `ProbeTheme`
       - `SCVMeasurementBroker` -> `ProbeBroker`
       - `SCVLevelsSource` -> `ProbeLevelsSource`
       - `SCVSpectrumSource` -> `ProbeSpectrumSource`
       - `SCVLoudnessSource` -> `ProbeLoudnessSource`
       - `SCVVectorscopeSource` -> `ProbeVectorscopeSource`
       - `SCVScope` / `SCVScopeView` -> `ProbeScope` / `ProbeScopeView`
       - `SCVSpectrum` / `SCVSpectrumView` -> `ProbeSpectrum` / `ProbeSpectrumView`
       - `SCVSpectrogram` / `SCVSpectrogramView` -> `ProbeSpectrogram` / `ProbeSpectrogramView`
       - `SCVWaterfall` / `SCVWaterfallView` -> `ProbeWaterfall` / `ProbeWaterfallView`
       - `SCVMeter` / `SCVMeterView` -> `ProbeMeter` / `ProbeMeterView`
       - `SCVLoudness` / `SCVLoudnessView` -> `ProbeLoudness` / `ProbeLoudnessView`
       - `SCVVectorscope` / `SCVVectorscopeView` -> `ProbeVectorscope` / `ProbeVectorscopeView`
       - `SCVHistory` / `SCVHistoryView` -> `ProbeHistory` / `ProbeHistoryView`
       - `SCVDashboard` / `SCVDashboardView` -> `ProbeDashboard` / `ProbeDashboardView`
     - Renamed 11 HelpSource documents from `SCV*.schelp` to `Probe*.schelp`.
     - Renamed 9 test suite files from `SCV*_unittest.scd` to `Probe*_unittest.scd`.
  3. **SynthDef & OSC Path Namespace Unification:**
     - Renamed internal SynthDefs from `\scv_*` to `\probe_*`:
       - `\scv_meter_levels` -> `\probe_meter_levels`
       - `\scv_scope_*` -> `\probe_scope_*`
       - `\scv_spectrum_*` -> `\probe_spectrum_*`
       - `\scv_spectrogram_fft` -> `\probe_spectrogram_fft`
       - `\scv_loudness_*` -> `\probe_loudness_*`
       - `\scv_vecscope_*` -> `\probe_vecscope_*`
     - Renamed OSC message reply paths from `'/scv_*'` to `'/probe_*'`.
  4. **Zero-Breakage Backward Compatibility Layer:**
     - Created `classes/ProbeLegacyCompat.sc` defining 22 legacy subclasses inheriting directly from their `Probe*` counterparts (e.g. `SCVTheme : ProbeTheme {}`, `SCVMeasurementBroker : ProbeBroker {}`, `SCVScope : ProbeScope {}`, `SCVMeter : ProbeMeter {}`).
     - Legacy code continues to execute without error or deprecation failure while transparently sharing underlying singleton state and class variables.
  5. **Verification & Test Suite Coverage:**
     - Class library recompile confirmed clean in 0.27s (0 errors, 0 duplicate class warnings).
     - Master test runner (`tests/run_all_tests.scd`): 14/14 test suites passed cleanly (100%).
     - System install verification (`scripts/verify_system_install.scd`): 73/73 assertions passed (0 failed).
     - HelpSource verification (`scripts/verify_scdoc.scd`): 14/14 documents verified with 0 warnings.
  6. **Release Packaging (`v0.3.0`):**
     - Version bumped to `0.3.0` in `Probe.quark`, `SCVisuals.quark`, `Probe.sc`, `SCVisuals.sc`, and `README.md`.
     - All 6 examples in `examples/01` - `06` updated to modern `Probe` idiomatic API.
- **Deliverables:** `classes/Probe*.sc`, `classes/ProbeLegacyCompat.sc`, `HelpSource/Classes/Probe*.schelp`, `tests/Probe*_unittest.scd`, `scripts/verify_*.scd`, Commit pending.

---

## Decision Matrix & Invariant Log

| Parameter / Feature | Chosen Design | Rationale | Invariant Status |
|---|---|---|---|
| **Suite Name** | `Probe` (`v0.3.0`, unified namespace) | Professional instrumentation terminology; completely replaces internal `SCV` prefix while preserving legacy subclasses in `ProbeLegacyCompat.sc`. | Active Invariant (v0.3.0) |
| **Default Font Scale** | `1.25` (125%) | Optimum balance of legibility on modern high-DPI retina displays without overflowing panels. | Active Invariant |
| **Control Layout** | Tight (28px buttons at 1.25, 4–6px gaps) | Sleek laboratory instrument aesthetic; eliminates bloated dead space. | Active Invariant |
| **FFT Smoothing** | Raw FFT scan (no PV smoothing) | PV smoothing introduced smearing and edge ripples; raw FFT preserves instant transient response. | Active Invariant |
| **Telemetry Transport** | `ProbeBroker` Reference-Counted Synths | Prevents duplicate FFT/peak-RMS synths on identical bus monitoring. Legacy alias `SCVMeasurementBroker` retained. | Active Invariant (v0.3.0) |
| **Core SC Overrides** | Forbidden (`s.meter`, `s.scope`, `s.freqscope` unchanged) | Strict non-interference with vanilla SuperCollider workflows. | Permanent Invariant |
| **Server Shortcuts** | `s.meter2`, `s.scope2`, `s.freqscope2`, `s.dashboard`, `s.spectralDashboard`, `s.bandAnalyzer`, `s.inspector` | Non-conflicting ergonomic shortcuts following SuperCollider numbering conventions. | Active Invariant |
| **Signal Tracing** | Transparent `.trace` with Dual Transport | Sample-accurate waveforms for audio rate, low-bandwidth 20 Hz timeline HUD for control rate. | Active Invariant (Phase II) |
| **Spectral Dashboard** | Persistent 4-Preset Architecture | Instantaneous preset switching (`\overview`, `\pitch`, `\timeFreq`, `\compare`) with zero ScopeBuffer reallocation. | Active Invariant (Phase II) |
| **Oscilloscope Triggering** | DSP Zero-Crossing Latch Buffer | Guarantees drift-free stationary waveforms for periodic signals without external plugins. | Active Invariant (Phase II) |
| **Unified FreqScope Modes** | Subview Visibility Switching | Seamless single-window transitions between Line, 16-Band, and Spectrogram modes. | Active Invariant (Phase II) |
| **DSP Graph Inspector** | Static Topological DAG Parsing | Pure sclang evaluation; renders clear signal flow without server messaging overhead or audio thread interference. | Active Invariant (Phase II) |
| **Packaging & Compatibility** | Dual-Quark Alias (`Probe` + `SCVisuals`) & `ProbeLegacyCompat.sc` | Seamless migration path ensuring zero broken workflows for existing installations; 22 legacy `SCV*` classes preserved. | Active Invariant (v0.3.0) |


