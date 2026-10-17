# Engineering Report: Unified Font Scaling & Spectrum Smoothing Architecture

## 1. Executive Summary

This update delivers:
1. **Unified Global & Per-Instance Font Scaling**: A single setting (`SCVisuals.fontScale`, `view.fontScale`, or Dashboard `"Font:"` popup) that scales all typography dynamically across all 9 visualizers while preserving layout geometry, margins, alignment, and legibility without clipping or line wrapping.
2. **Real-Time Audio-Rate Spectrum Smoothing**: Replaced legacy linear bin smearing with continuous audio-rate low-pass filtering on the FFT magnitude phasor scan, fixing the root causes that rendered smoothing invisible.
3. **Architectural Analysis**: Answers to the question regarding `PV_` UGens vs. language-side (sclang) processing for high-frame-rate visualizers.
4. **Clean Verification & Local Git Tracking**: All 9 unit test suites passed (0 failures), visual verification completed, and changes committed locally to `git` without publishing.

---

## 2. Answers to Architectural Questions

### Q1: Why were PV UGens previously used for smoothing?
- **Historical SuperCollider Legacy**: Legacy SuperCollider tools such as `FreqScope` (dating back to SC 3.0 / 2002) historically used `PV_MagSmear` as a simple built-in phase vocoder UGen.
- **Why `PV_MagSmear` fails on modern high-resolution displays**:
  1. **Linear Bin Resolution vs. Logarithmic Displays**: FFT bins have a linear frequency resolution ($\Delta f = f_s / N$, e.g., $48000 / 2048 \approx 23.44\text{ Hz/bin}$). On a logarithmic frequency axis ($20\text{ Hz} - 20\text{ kHz}$), the entire low range ($20 - 200\text{ Hz}$) occupies only $\sim 8$ bins, whereas the high octave ($10 - 20\text{ kHz}$) packs $> 420$ bins into a small visual horizontal span. Smearing a fixed count of bins linearly produces no perceptible difference at high frequencies while completely obliterating the bass.
  2. **Phase Vocoder Modifies Complex Spectra**: `PV_MagSmear` operates in the complex frequency domain before magnitude calculation, rather than shaping the rendered envelope.
  3. **SuperCollider `synth.isPlaying` Trap**: In SuperCollider, `Synth.play` returns a `Synth` object whose `synth.isPlaying` property defaults to `false` unless explicitly registered with `NodeWatcher.register(synth)`. The previous code guarded server parameter sets with `if (synth.notNil and: { synth.isPlaying })`, causing `synth.set(\smoothCutoff, ...)` to **never reach scsynth**.

---

### Q2: Is it better to apply some type of smoothing on the language side of the plotting?
**Short Answer**: **No, not for real-time (60 FPS) oscilloscope and spectrum plots.**

Here is the technical breakdown:

| Dimension | scsynth Audio-Rate (`LPF.ar` on Phasor) | sclang Language-Side Processing |
| :--- | :--- | :--- |
| **Data Transport** | **Direct C++ Shared Memory**: `scsynth` writes directly into Qt's `ScopeBuffer` ring buffer in memory. | **Network OSC Packets**: 60 packets/sec $\times 1024$ floats $= 61,440$ 32-bit floats streamed over UDP/OSC. |
| **Interpreter Thread Impact** | **0% Sclang CPU**: Sclang does not touch individual audio/spectral sample points. | **High Garbage Collection & CPU**: Single-threaded sclang interpreter allocates, loops, and discards float arrays at 60 Hz. |
| **Frame Rate & Latency** | **Locked 60 FPS**: Rendered via QPainter/OpenGL with zero buffer latency or packet jitter. | **Variable / Jittery**: OSC network queues, sclang scheduling delays, and GC pauses cause frame drops. |
| **Curve Quality** | Continuous, smooth 2-pole analog-modeled filter curve across the frequency axis. | Stepped moving-average or discrete boxcar convolution across array indices. |

> [!NOTE]
> Language-side processing is used intentionally in `SCVSpectrogram` because it aggregates data down to 64 logarithmic bands at a low 20 Hz update rate, where sclang overhead is negligible. But for full-resolution 60 FPS spectrum and scope displays, server-side shared-memory rendering is vastly superior.

---

## 3. The Optimal Audio-Rate Magnitude Filter

In `classes/SCVSpectrum.sc`, the FFT buffer is scanned at audio rate using `Phasor.ar`. We now route the raw magnitude through a resonant-free low-pass filter:

```supercollider
// Scan FFT buffer at audio rate
var rawMag = BufRd.ar(1, buf, ...);

// Real-time audio-rate filter across the frequency spectrum envelope
var smoothedMag = LPF.ar(rawMag, smoothCutoff.clip(20, 20000));
```

### Smoothing Presets & Characteristics:
- `\none` or `\off` ($20,000\text{ Hz}$): Unfiltered raw FFT bins.
- `\low` ($800\text{ Hz}$): Light smoothing; preserves individual harmonic peaks while dampening bin flicker.
- `\med` ($250\text{ Hz}$): Classic studio RTA bell lobes.
- `\high` ($70\text{ Hz}$): Broadcast mastering envelope contour.

Visual comparison:
````carousel
![Raw Spectrum - Smoothing Off](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/spec-smooth-off-live.png)
<!-- slide -->
![Medium Spectrum Smoothing](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/spec-smooth-med-live.png)
<!-- slide -->
![High Spectrum Smoothing](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/spec-smooth-high-live.png)
````

---

## 4. Unified Global & Per-Instance Font Scaling

### Usage:

1. **Global Application-Wide Scaling**:
   ```supercollider
   SCVisuals.fontScale = 1.25; // Scales all open visualizers and all future windows to 125%
   ```
2. **Per-Instance Scaling**:
   ```supercollider
   m = SCVMeter.new(s);
   m.fontScale = 1.4; // Specifically enlarge meter scale numbers to 140%
   ```
3. **Interactive Dashboard Control**:
   The header bar of `SCVDashboard` now features a `"Font:"` dropdown:
   - `100%`, `115%`, `125%`, `140%`, `150%`
   - Instantly updates all 5 embedded visualizers and toolbar labels simultaneously.

### Layout Geometry Preservation:
All visualizer components derive layout geometry dynamically from the active font scale:
- Dual-ruler meter: `rulerW = (24 * fontScale).max(24)`
- Spectrum dB scale: `ox = (28 * fontScale).max(28)`
- Spectrogram frequency scale: `rulerW = (32 * fontScale).max(32)`
- Loudness readout panel: `rowH = (22 * fontScale).max(20)`, compact readout strings prevent clipping.

````carousel
![Dashboard at 100% Font Scale](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/dashboard-font-100.png)
<!-- slide -->
![Dashboard at 125% Font Scale](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/dashboard-font-125.png)
<!-- slide -->
![Dashboard at 150% Font Scale](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/dashboard-font-150.png)
````

---

## 5. Verification & Git Commit

- **Unit Tests**: Ran all 9 test suites via `tests/run_all_tests.scd` — **9 passed, 0 failed**.
- **Local Git Status**: Committed locally to `main` branch (`feat: unified global font scaling and real-time audio-rate spectrum magnitude smoothing`). Zero remote pushes performed.
