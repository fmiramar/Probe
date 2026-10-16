# SCVisuals — Source and Architecture Audit (P0)

**Date:** 2026-10-15  
**Auditor:** Antigravity Pairing Agent  
**Target:** SuperCollider 3.14.1 (pinned commit `426edf6`, tag `Version-3.14.1`)  
**Platform Tested:** macOS (Darwin arm64, macOS 26.6.2), Qt 6.7.3, scsynth  

---

## 1. Executive Summary

This audit establishes the baseline facts and constraints for developing `SCVisuals`, a cohesive, lightweight SuperCollider Quark for real-time audio visualization. The audit investigates five key internal components of the SuperCollider class library, explores existing independent Quarks (such as `supercollider-insight-meter`), verifies Quark packaging and test standards, and establishes what can and cannot be done natively in QtCollider without C++ extensions.

---

## 2. Upstream Pinned Baseline

| Component | Pinned Version / Detail |
|---|---|
| **SuperCollider** | `3.14.1` |
| **Git tag** | `Version-3.14.1` |
| **Git commit** | `426edf656be91e9f6974d0e515dbe8ec6ffbe630` |
| **Audio Server** | `scsynth` (local shared-memory interface) |
| **GUI Scheme** | `QtGUI` (`QcScopeShm`, `QImage`, `QPainter` backing) |
| **Qt Version** | `6.7.3` |
| **OS / Architecture** | macOS (Darwin 25.6.0 arm64, Apple Silicon) |
| **Tested Audio Sample Rates** | 44,100 Hz and 48,000 Hz |

---

## 3. Detailed Audit of Core SC Implementations

### 3.1 `ServerMeter` & `ServerMeterView`
- **Source location:** `SCClassLibrary/Common/GUI/tools/ServerMeter.sc`
- **Transport:** OSC replies sent from `SendPeakRMS.kr(in, updateFreq, 3, cmdName)`.
- **Default rate:** 10 Hz (`updateFreq = 10`).
- **Peak / RMS specification:**
  - Peak is peak amplitude within the update window.
  - RMS is root-mean-square amplitude over the window (`numRMSSamps = server.sampleRate / updateFreq`).
  - Values arrive as raw linear amplitudes in OSC message format: `[cmdName, nodeID, replyID, peak1, rms1, peak2, rms2, ...]`.
  - Converted in sclang via `.ampdb.linlin(dBLow, 0, 0, 1)`.
- **Audio graph management:**
  - Creates two SynthDefs: `server.name ++ "InputLevels"` (reading `In.ar(NumOutputBuses.ir, numIns)` added to RootNode head) and `server.name ++ "OutputLevels"` (reading `In.ar(0, numOuts)` added to RootNode tail).
  - Uses `serverMeterViews` dictionary keyed by server: analysis Synths are only started once per server when the first window opens, and freed when the last window closes.
  - **Limitation / Defect in stock ServerMeterView:** While analysis Synths are shared, **each `ServerMeterView` instance registers its own `OSCFunc`** listening on the same OSC path, and each independently executes `{ ... }.defer` into AppClock. This means multiple meter windows multiply OSC handling and GUI event queue deferrals.
  - **GUI Rendering:** Uses stock `LevelIndicator` widgets inside vertical strips. `UserView` is used only for the simple 0 / -80 dB text marks and vertical divider.
- **Architectural Lesson for SCVisuals:**
  - `SCVLevelsSource` broker must own a single `OSCFunc` per server and distribute parsed data arrays to registered view presenters.
  - Updates must be coalesced and rate-limited rather than deferring unbounded closures on every OSC packet.

### 3.2 `Stethoscope` & `BusScopeSynth`
- **Source location:** `SCClassLibrary/Common/GUI/tools/Stethoscope.sc`
- **Transport:** Native `ScopeOut2.ar(z, bufIndex, bufSize, cycle)` writing directly to a `ScopeBuffer` allocated in server memory.
- **Buffer allocation:** `ScopeBuffer.alloc(server)`.
- **Local vs Remote limitation:**
  - Native scoping checks `server.isLocal`. It communicates with `scsynth` via shared memory (`QcScopeShm`).
  - Scoping on remote servers is **not supported** by `ScopeView` without an alternate OSC buffer-fetching fallback.
- **Lifecycle & Node placement:**
  - Instantiates `BusScopeSynth` which creates a dedicated Synth at `RootNode(server)` tail.
  - Tracks `ServerTree` and `ServerQuit` for clean reboot and shutdown.
- **Scope properties exposed in Stethoscope:**
  - `cycle` (window frame size: default exponential spec 64 to `maxBufSize`).
  - `yZoom` (vertical gain: 0.125 to 16).
  - `style`: 0 = Tracks (channels stacked), 1 = Overlay (channels superimposed), 2 = X/Y (Lissajous stereo).
  - Rate switching (Audio bus vs Control bus using `Select.ar` or `K2A.ar`).

### 3.3 `ScopeView` & `QcScopeShm`
- **Source location:** `SCClassLibrary/Common/GUI/Base/ScopeView.sc`
- **C++ Qt backend:** `QcScopeShm` (shared-memory reader).
- **Properties that CAN be modified without C++:**
  - `background = Color(...)`: Canvas background color.
  - `waveColors = [Color, ...]`: Per-channel waveform trace colors.
  - `style = 0, 1, 2`: Tracks, Overlay, or X/Y display modes.
  - `xZoom`, `yZoom`: Horizontal and vertical display scaling.
  - `x`, `y`: Offset positioning in the scope buffer.
  - `fill = true/false`: Whether the waveform area is filled.
  - Transparent overlay: A transparent `UserView` placed on top of `ScopeView` can draw custom grid lines, zero-crossings, dB scales, and time indicators without any C++ modification.
- **Properties that CANNOT be modified without C++:**
  - Sample acquisition triggering (edge trigger, hysteresis, zero-cross lock) is not implemented in `QcScopeShm`.
  - There is no public sclang callback or method to extract raw sample frames directly from `QcScopeShm` in sclang.
  - Scoping remains strictly local-server shared-memory only.
  - Redraw frequency is driven by internal Qt timer inside `QcScopeShm`.

### 3.4 `FreqScopeView` & `FreqScope`
- **Source location:** `SCClassLibrary/Common/GUI/PlusGUI/Control/FreqScope.sc`
- **DSP analysis pipeline:**
  - FFT size default: 2048 points (`LocalBuf(fftBufSize, 1)`), hop size: 0.75, Hann window (`wintype: 1`).
  - Smoothing: `PV_MagSmear(chain, 1)`.
  - Readout synthesis: An audio-rate sawtooth phasor (`LFSaw.ar`) scans the FFT magnitude buffer at `rate / BufDur.ir(fftbufnum)`.
    - Mode 0 (Linear): Linear phasor scan from bin 2 to 2048.
    - Mode 1 (Logarithmic): Exponential phasor scan (`halfSamples.pow(LFSaw.ar(...)) * 2`).
  - Output: `ScopeOut2.ar(((BufRd.ar(1, fftbufnum, phasor, 1, 1) * mul).ampdb * dbFactor) + 1, scopebufnum, fftBufSize/rate)`.
  - Render widget: A standard `ScopeView` set to `style: 1` (overlay) and `fill: false`, displaying the synthetic audio signal generated by the phasor readout!
- **Key Insight on FreqScope:**
  - **`FreqScope` does NOT transfer FFT bins to sclang as an array.**
  - Instead, it converts FFT buffer magnitudes back into an audio waveform inside `scsynth` and uses the native `ScopeOut2` -> `ScopeView` shared-memory mechanism to plot the curve as an audio trace!
  - Therefore, FreqScope cannot be directly tapped to feed a 2D spectrogram or waterfall matrix in sclang.

### 3.5 `ScopeOut2`
- **Source location:** `SCClassLibrary/Common/Audio/BufIO.sc:125`
- **UGen signature:** `ScopeOut2.ar(inputArray, scopeBufNum, maxFrames, scopeFrames)`
- Writes audio blocks directly to circular shared-memory scope buffers.
- Designed exclusively for consumption by QtCollider's `QcScopeShm` widget.

---

## 4. Quark Packaging and Test Standards

### 4.1 Quark Specification
- Standard Quark package descriptor: `{QuarkName}.quark` returning an `IdentityDictionary` or `Event`.
- Keys:
  - `name`: String
  - `summary`: String
  - `version`: String (SemVer format, e.g. "0.1.0")
  - `schelp`: String (name of main help documentation file)
  - `license`: String ("GPL-3.0-or-later")
  - `copyright`: String
  - `dependencies`: Array of strings
  - `isCompatible`: Function returning Boolean

### 4.2 SuperCollider Test Infrastructure
- Class library tests inherit from `UnitTest`.
- Standalone test scripts `.scd` execute assertions and return formatted summaries.
- Tests must clean up all server nodes, OSC responders, and windows upon completion.

---

## 5. Audit of Alternative Existing Quarks

### 5.1 `supercollider-insight-meter` (Francesco Corvi)
- **License:** GPL-3.0
- **Summary:** Modern dark-themed level meter utilizing custom `LevelIndicator` palettes, segmented meters, and peak/RMS readouts.
- **Takeaways:**
  - Demonstrates that `LevelIndicator` can achieve refined visual styling without abandoning native Qt widgets.
  - Uses `LevelIndicatorStyle.continuous` and custom warning/critical color thresholds.
  - Demonstrates vertical compact meter layout in modern production SC code.
- **SCVisuals Policy:** No direct copy-pasting of code. Independent architecture with centralized broker, strict resource lifecycle, theme engine, and multi-consumer support.

---

## 6. Verified APIs vs Architectural Assumptions

| Feature / API | Status | Verification Detail |
|---|---|---|
| `SendPeakRMS.kr` | Verified | Standard kr UGen, 10–20 Hz updates, reliable linear peak/RMS pairs. |
| `LevelIndicator` styling | Verified | Supports continuous / led styles, custom warning/critical levels, custom bounds. |
| `ScopeView` theming | Verified | Supports `background_`, `waveColors_`, `fill_`, `xZoom_`, `yZoom_`. |
| Transparent `UserView` on ScopeView | Verified | Allows drawing grids, zero axes, and dB ruler overlays on top of ScopeView. |
| Shared FFT to sclang data path | **Assumption (Caution)** | FreqScope does not provide raw FFT arrays to sclang. Spectrogram requires an explicit P4 transport feasibility study before making claims of "zero-cost shared FFT". |
| `ScopeView` on remote server | **Not Supported** | `ScopeView` requires local shared memory; remote server scoping must be treated as a separate fallback. |
| `Image.fromWindow` | Verified | Can capture window / view surfaces to high-res PNG for automated visual verification. |
