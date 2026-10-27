# Probe Visuals and Representation Suite

A comprehensive architectural reference, class catalog, in-graph signal tracing manual, and GUI embedding guide for the **Probe Visuals and Representation Suite** (`Probe` v0.4.0) in SuperCollider.

---

## 1. Executive Summary & Instrument Directory

The **Probe Visuals and Representation Suite** provides a laboratory-grade, modular, audio-thread safe family of real-time audio visualization, measurement, and signal instrumentation tools for SuperCollider.

### Core Architectural Invariants
1. **Dual-Tier Namespace ([ADR-001](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/docs/decisions/ADR-001-Dual-Tier-Namespace-and-Ergonomics.md)):**
   - **Tier 1 (`Probe*`):** Exactly **14 clean public classes** appear when typing `Probe` in the SuperCollider IDE.
   - **Tier 2 (`PB_*`):** All telemetry brokers, background DSP sources, and composite views are strictly sequestered behind the private `PB_*` prefix.
2. **Dual-Engine Rendering ([ADR-002](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/docs/decisions/ADR-002-Dual-Engine-Smooth-Antialiased-Rendering.md)):**
   - High-resolution hardware-accelerated vector drawing with SuperCollider's `Pen` (`\smooth`, default) eliminates 1-pixel Retina hairline blur and Bresenham staircase artifacts.
   - Zero-SCLang native C++ `ScopeView` blit (`\native`) available as a toggleable fallback.
3. **Embeddable View Factory Pattern:**
   - Every instrument provides a unified `Probe*.view(parent, bounds, ...)` constructor that returns a standard SuperCollider `View` (subclass of `CompositeView`), enabling direct embedding into custom synthesizer interfaces, DAW-like mixers, or default SC windows without touching private classes.
4. **Non-Invasive Server Ergonomics:**
   - Visualizers attach directly to `Server` via the non-colliding `s.*Pb` methods (`s.scopePb`, `s.meterPb`, `s.freqscopePb`, `s.dashboardPb`). Core SuperCollider methods (`s.meter`, `s.scope`, `s.freqscope`) remain 100% unaltered.

---

### Hyperlinked Instrument Catalog

| Instrument Class | Primary Role & Description | Server Shortcut | Embeddable View Factory | Telemetry & Transport |
|---|---|---|---|---|
| [`Probe`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/Probe.sc) | Suite facade, server shortcuts, broker lifecycle, and version registry | `s.scopePb`, `s.meterPb`, etc. | N/A | Centralized Broker & Factory |
| [`ProbeScope`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeScope.sc) | Oscilloscope with dual-engine (`\smooth` vs `\native`), edge triggering, `\tracks` / `\overlay` / `\xy`, zoom, freeze | `s.scopePb(bus, chans)` | [`ProbeScope.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeScope.sc#L862) | `ScopeOut2` + `RecordBuf` continuous ring buffer |
| [`ProbeSpectrum`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectrum.sc) | FFT spectrum analyzer with 3 switchable modes (Line, 16-Band, Spectrogram), dBFS ruler, interactive cursor | `s.freqscopePb(bus)` / `s.spectrumPb` | [`ProbeSpectrum.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectrum.sc#L529) | High-resolution FFT pipeline via `PB_SpectrumSource` |
| [`ProbeSpectrogram`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectrogram.sc) | 2D scrolling time–frequency sonogram with dynamic color gradients and circular ring-buffer blit | `s.spectrogramPb(bus)` | [`ProbeSpectrogram.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectrogram.sc#L249) | 64-band logarithmic `SendReply.kr` telemetry |
| [`ProbeWaterfall`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeWaterfall.sc) | 2.5D isometric pseudo-3D stacked-line spectral history with depth-cued shading | `s.waterfallPb(bus)` | [`ProbeWaterfall.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeWaterfall.sc#L235) | Shared `PB_SpectrumSource` FFT stream |
| [`ProbeMeter`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeMeter.sc) | High-density vertical level meter with peak, RMS, peak-hold, and calibrated dBFS ruler | `s.meterPb(ins, outs)` | [`ProbeMeter.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeMeter.sc#L273) | `SendPeakRMS` via `PB_LevelsSource` |
| [`ProbeLoudness`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeLoudness.sc) | ITU-R BS.1770-4 / EBU R128 loudness meter (M, S, I, LRA, -23 LUFS / -14 LUFS target lines) | `s.loudnessPb(bus)` | [`ProbeLoudness.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeLoudness.sc#L237) | Dual-stage K-weighting biquad filterbank |
| [`ProbeVectorscope`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeVectorscope.sc) | Stereo Lissajous X/Y phase display with Mid/Side polar graticule and phase correlation bar | `s.vectorscopePb(bus)` | [`ProbeVectorscope.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeVectorscope.sc#L478) | `ScopeOut2` + `RecordBuf` + Phase Correlation UGen |
| [`ProbeBandAnalyzer`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeBandAnalyzer.sc) | 16-band ISO 1/3-octave band analyzer with adjustable exponential moving-average smoothing filter | `s.bandAnalyzerPb(bus)` | [`ProbeBandAnalyzer.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeBandAnalyzer.sc#L278) | Shared `PB_SpectralSource` filterbank |
| [`ProbeSpectralDashboard`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectralDashboard.sc) | 4-preset master spectral workstation (`\overview`, `\pitch`, `\timeFreq`, `\compare`) with descriptors & chroma | `s.spectralDashboardPb(bus)` | [`ProbeSpectralDashboard.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectralDashboard.sc#L368) | `PB_SpectralSource` & `PB_Broker` |
| [`ProbeDashboard`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeDashboard.sc) | Unified 5-panel master command center (Meter, Scope, Spectrum, Spectrogram, Loudness) with global freeze | `s.dashboardPb` | [`ProbeDashboard.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeDashboard.sc#L330) | Multi-client `PB_Broker` subscription |
| [`ProbeInspector`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeInspector.sc) | Static topological signal-flow Directed Acyclic Graph (DAG) inspector with rate coloring and bezier routing | `s.inspectorPb(target)` / `s.inspectPb` | [`ProbeInspector.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeInspector.sc#L80) | Pure SCLang static DAG compilation |
| [`ProbeHistory`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeHistory.sc) | Rolling strip-chart timeline recorder for peak, RMS, and crest factor with configurable window duration | `s.historyPb(bus)` | [`ProbeHistory.view`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeHistory.sc#L196) | Shared `PB_LevelsSource` timeline buffer |
| [`ProbeTheme`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeTheme.sc) | Centralized theme management with 5 laboratory palettes (`\graphite`, `\phosphor`, `\amber`, `\ice`, `\puredata`) | `ProbeTheme.current = ...` | N/A | SCLang Theme Dictionary & Font Scaler |

---

## 2. Complete Class Reference & Working Code Examples

All examples assume a booted default server:
```supercollider
s.boot;
```

---

### 1. `Probe` (Entry Point, Facade & Server Extensions)
The [`Probe`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/Probe.sc) class provides unified shortcuts, global theme coordination, and shared telemetry inspection.

```supercollider
// 1. Open any instrument via Server extensions (*Pb style)
s.scopePb;
s.meterPb;
s.freqscopePb;
s.dashboardPb;

// 2. Open via Probe class methods
Probe.scope(s, busIndex: 0, numChannels: 2);
Probe.spectrum(s, busIndex: 0);
Probe.loudness(s, busIndex: 0);

// 3. Inspect shared telemetry broker
Probe.broker.inspect;

// 4. Change theme globally for all running and future instruments
ProbeTheme.current = ProbeTheme.get(\phosphor);
```

---

### 2. `ProbeScope` (Precision Oscilloscope)
[`ProbeScope`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeScope.sc) features dual-engine antialiasing, zero-crossing edge synchronization, channel layout modes, zoom, freeze, and ribbon fill.

```supercollider
(
x = {
    var sig = [SinOsc.ar(160), Saw.ar(80)] * 0.4;
    sig;
}.play;

// Open scope with antialiased smooth engine and separated tracks
~scope = ProbeScope(s, busIndex: 0, numChannels: 2, theme: \graphite, engine: \smooth, style: \tracks);
)

// Interactive controls
~scope.engine = \smooth;      // \smooth (vector antialiasing) or \native (ScopeView blit)
~scope.lineWidth = 2.2;       // adjust vector line thickness (default: 1.8)
~scope.fill = true;           // toggle translucent ribbon fill
~scope.scopeStyle = \tracks;   // \tracks (vertically separated), \overlay (superimposed), \xy (Lissajous)
~scope.triggerMode = \rising;  // stationary trigger: \free, \rising, \falling
~scope.xZoom = 2.0;           // zoom horizontal time axis
~scope.yZoom = 1.5;           // zoom vertical amplitude axis
~scope.toggleFreeze;          // freeze display (pauses server synth, retains waveform)
~scope.toggleFreeze;          // unfreeze

x.free;
~scope.close;
```

---

### 3. `ProbeSpectrum` (Unified FFT Spectrum Analyzer)
[`ProbeSpectrum`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectrum.sc) provides 3 switchable visualization modes (Line curve, 16-Band analyzer, and Spectrogram) with an interactive frequency/dB cursor.

```supercollider
(
x = {
    var mod = SinOsc.ar(120, 0, 300);
    var car = SinOsc.ar(300 + mod) * 0.3;
    var noise = PinkNoise.ar(0.04);
    (car + noise).dup;
}.play;

~spec = ProbeSpectrum(s, busIndex: 0, fftSize: 2048, theme: \ice);
)

// Mode switching
~spec.mode = \line;         // High-resolution continuous curve
~spec.mode = \bands;        // 16-band ISO 1/3-octave bar analyzer
~spec.mode = \spectrogram;  // Scrolling 2D sonogram

// Frequency scaling
~spec.freqMode = 1;         // 1 = Logarithmic (20 Hz - 20 kHz), 0 = Linear

x.free;
~spec.close;
```

---

### 4. `ProbeSpectrogram` (2D Scrolling Sonogram)
[`ProbeSpectrogram`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectrogram.sc) plots continuous time-frequency history with palette gradient mapping.

```supercollider
(
x = {
    var sweep = SinOsc.ar(XLine.kr(80, 7000, 5)) * 0.25;
    sweep.dup;
}.play;

~sonogram = ProbeSpectrogram(s, busIndex: 0, theme: \amber);
)

x.free;
~sonogram.close;
```

---

### 5. `ProbeWaterfall` (2.5D Isometric Spectral Stack)
[`ProbeWaterfall`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeWaterfall.sc) renders stacked isometric spectral frames with depth-cued color shading.

```supercollider
(
x = {
    var harm = Mix.fill(8, { |i| SinOsc.ar((i + 1) * 110, 0, 0.12 / (i + 1)) });
    harm.dup;
}.play;

~waterfall = ProbeWaterfall(s, busIndex: 0, theme: \phosphor);
)

x.free;
~waterfall.close;
```

---

### 6. `ProbeMeter` (Compact Multichannel Peak/RMS Meter)
[`ProbeMeter`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeMeter.sc) features calibrated dBFS rulers, fast peak tracking, RMS energy bars, and peak-hold indicators.

```supercollider
(
x = {
    [Dust2.ar(25) * 0.4, BrownNoise.ar(0.15)];
}.play;

~meter = ProbeMeter(s, numIns: 2, numOuts: 2, theme: \graphite);
)

x.free;
~meter.close;
```

---

### 7. `ProbeLoudness` (ITU-R BS.1770 / EBU R128 Loudness Meter)
[`ProbeLoudness`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeLoudness.sc) provides calibrated Momentary (M), Short-Term (S), Integrated (I), and Loudness Range (LRA) monitoring.

```supercollider
(
x = {
    var env = EnvGen.kr(Env.perc(0.08, 1.2), Dust.kr(1.5));
    (PinkNoise.ar(0.3) * env).dup;
}.play;

// Target: \ebu (-23 LUFS) or \streaming (-14 LUFS)
~lufs = ProbeLoudness(s, busIndex: 0, theme: \graphite, target: \streaming);
)

~lufs.reset; // reset integrated LUFS measurement
x.free;
~lufs.close;
```

---

### 8. `ProbeVectorscope` (Stereo Lissajous & Phase Correlation)
[`ProbeVectorscope`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeVectorscope.sc) monitors stereo imaging, Mid/Side polar balance, and phase alignment (-1 to +1).

```supercollider
(
x = {
    var l = SinOsc.ar(200);
    var r = SinOsc.ar(200, pi * 0.5); // 90 degree phase shift (circular Lissajous)
    [l, r] * 0.25;
}.play;

~vec = ProbeVectorscope(s, busIndex: 0, theme: \graphite, engine: \smooth);
)

~vec.engine = \smooth; // switch between \smooth (vector) and \native
~vec.lineWidth = 2.5;

x.free;
~vec.close;
```

---

### 9. `ProbeBandAnalyzer` (16-Band ISO 1/3-Octave Analyzer)
[`ProbeBandAnalyzer`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeBandAnalyzer.sc) incorporates an exponential moving-average smoothing filter to eliminate rapid visual flickering.

```supercollider
(
x = {
    Saw.ar([110, 220, 440, 880, 1760], 0.08).sum.dup;
}.play;

~bands = ProbeBandAnalyzer(s, busIndex: 0, theme: \graphite, smooth: 0.7);
)

// Adjust smoothing (0.0 = raw instantaneous, 0.9 = ultra-smooth lag)
~bands.smooth = 0.85;

x.free;
~bands.close;
```

---

### 10. `ProbeSpectralDashboard` (4-Preset Master Spectral Workstation)
[`ProbeSpectralDashboard`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectralDashboard.sc) integrates multiple analysis perspectives with instant preset recall.

```supercollider
(
x = {
    var freq = LFNoise0.kr(4).exprange(150, 1200);
    Pulse.ar(freq, 0.5, 0.2).dup;
}.play;

~sdash = ProbeSpectralDashboard(s, busIndex: 0, theme: \graphite);
)

// Select presets programmatically:
~sdash.preset = \overview;   // FFT Curve + 16-Band Bars + Spectral Descriptors
~sdash.preset = \pitch;      // Pitch Tracker + Chromagram + FFT
~sdash.preset = \timeFreq;   // Spectrogram + 16-Band Analyzer
~sdash.preset = \compare;    // Dual-channel A/B spectral overlay

x.free;
~sdash.close;
```

---

### 11. `ProbeDashboard` (5-Panel Master Command Center)
[`ProbeDashboard`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeDashboard.sc) unifies Meter, Scope, Spectrum, Spectrogram, and Loudness into a single responsive window.

```supercollider
s.dashboardPb(theme: \graphite);
```

---

### 12. `ProbeInspector` (Static Signal-Flow DAG Inspector)
[`ProbeInspector`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeInspector.sc) parses SynthDefs or active synths into an interactive signal flow graph without audio-thread overhead.

```supercollider
(
SynthDef(\demoSynth, { |out=0, freq=440, gate=1|
    var osc = Saw.ar(freq);
    var env = EnvGen.kr(Env.adsr(0.01, 0.2, 0.7, 0.5), gate);
    var filt = MoogFF.ar(osc, freq * 2, 2.5);
    Out.ar(out, (filt * env).dup);
}).add;
)

// Inspect SynthDef directly
ProbeInspector(\demoSynth, theme: \graphite);
```

---

### 13. `ProbeHistory` (Strip-Chart Timeline Recorder)
[`ProbeHistory`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeHistory.sc) logs rolling peak, RMS, and crest factor dynamics over time.

```supercollider
(
x = {
    var lfo = LFNoise2.kr(1).exprange(0.02, 0.6);
    (PinkNoise.ar(lfo)).dup;
}.play;

~hist = ProbeHistory(s, busIndex: 0, numChannels: 2, duration: 10, theme: \graphite);
)

x.free;
~hist.close;
```

---

### 14. `ProbeTheme` (Laboratory Aesthetics & Scaling)
[`ProbeTheme`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeTheme.sc) manages palettes and responsive font scaling:

```supercollider
// Set globally to Pure Data mode (minimalist B&W aesthetic)
ProbeTheme.current = ProbeTheme.get(\puredata);

// Adjust font scale globally (1.0 = standard, 1.25 = default Retina, 1.5 = high visibility)
ProbeTheme.current = ProbeTheme.current.withFontScale(1.3);
```

---

## 3. In-Graph Signal Tracing (`.trace`) Deep Dive

The `.trace` extension method ([`extUGenProbe.sc`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/extUGenProbe.sc)) allows instantaneous, zero-latency visual inspection of arbitrary internal nodes in a synthesis graph without altering signal flow, phase, or audio output.

### Syntax
```supercollider
aUGen.trace(label, mode, theme, options)
anArray.trace(label, mode, theme, options)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `label` | `String` / `Symbol` | Class name | Window title and telemetry identifier (e.g. `"Cutoff LFO"`). |
| `mode` | `Symbol` | Auto-detected | `\scope` (audio-rate waveform), `\history` (control-rate 20 Hz timeline HUD), `\spectrum` (FFT spectrum), `\meter` (peak level meter). |
| `theme` | `Symbol` | `\graphite` | Theme palette (`\graphite`, `\phosphor`, `\ice`, `\amber`, `\puredata`). |
| `options` | `Event` | `()` | Telemetry options, e.g. `(updateFreq: 30)`. |

---

### Filter Inspection Examples

#### 1. Resonant Lowpass Filter (Pre vs Post Filter & Cutoff Modulation)
```supercollider
(
x = {
    // 1. Trace the modulation signal (control-rate timeline HUD)
    var lfo = SinOsc.kr(0.5).exprange(150, 3500).trace("Cutoff LFO", \history);
    
    // 2. Trace the raw oscillator before filtering (oscilloscope)
    var raw = Saw.ar(110, 0.2).trace("Raw Saw Wave", \scope);
    
    // 3. Trace the filtered resonant output (oscilloscope)
    var filtered = RLPF.ar(raw, lfo, 0.1).trace("RLPF Output", \scope);
    
    filtered.dup;
}.play;
)
x.free;
```

#### 2. Ladder Filter (`MoogFF`) Saturation & Resonance
```supercollider
(
x = {
    var osc = Pulse.ar(65, 0.3, 0.25).trace("Oscillator", \scope);
    var env = EnvGen.kr(Env.perc(0.05, 1.2), Dust.kr(1)).trace("Filter Env", \history);
    var cutoff = (env * 4000) + 100;
    var moog = MoogFF.ar(osc, cutoff, 3.2).trace("MoogFF Output", \scope, theme: \amber);
    moog.dup;
}.play;
)
x.free;
```

#### 3. Bandpass Resonance Sweep Traced via FFT Spectrum
```supercollider
(
x = {
    var sweep = SinOsc.kr(0.2).exprange(300, 5000).trace("Sweep Hz", \history);
    var noise = WhiteNoise.ar(0.15);
    var bpf = BPF.ar(noise, sweep, 0.08).trace("Resonant Peak", \spectrum, theme: \phosphor);
    bpf.dup;
}.play;
)
x.free;
```

---

### Analytic UGen Inspection Examples

#### 1. Pitch Follower (`Pitch.kr`) Frequency & Confidence Tracking
```supercollider
(
x = {
    var in = SinOsc.ar(LFNoise0.kr(2).exprange(200, 800), 0, 0.2);
    var pitchData = Pitch.kr(in);
    var freq = pitchData[0];
    var hasFreq = pitchData[1];
    
    // Trace audio waveform in scope, detected pitch & confidence flag in HUD
    in.trace("Audio In", \scope);
    freq.trace("Tracked Pitch (Hz)", \history);
    hasFreq.trace("Pitch Confidence (0/1)", \history);
    
    in.dup;
}.play;
)
x.free;
```

#### 2. Dynamic Envelope Follower (`Amplitude.kr` & `PeakFollower.kr`)
```supercollider
(
x = {
    var audio = Dust2.ar(8) * LFNoise1.kr(2).range(0.1, 0.8);
    var env = Amplitude.kr(audio, attackTime: 0.01, releaseTime: 0.1).trace("RMS Envelope", \history);
    var peak = PeakFollower.kr(audio, 0.999).trace("Peak Hold", \history);
    audio.dup;
}.play;
)
x.free;
```

#### 3. Spectral Feature Analytics (`SpecCentroid` & `SpecFlatness`)
```supercollider
(
x = {
    var sig = Saw.ar(XLine.kr(100, 3000, 5), 0.2);
    var chain = FFT(LocalBuf(2048), sig);
    var centroid = SpecCentroid.kr(chain).trace("Spectral Centroid (Hz)", \history);
    var flatness = SpecFlatness.kr(chain).trace("Spectral Flatness", \history);
    sig.dup;
}.play;
)
x.free;
```

#### 4. Analytic Quadrature Signal via Hilbert Transform (`Hilbert.ar`)
```supercollider
(
x = {
    var sig = SinOsc.ar(220, 0, 0.3);
    var quadrature = Hilbert.ar(sig); // [real, imag (90-deg phase shift)]
    quadrature.trace("Hilbert Quadrature", \scope, theme: \ice);
    quadrature;
}.play;
)
x.free;
```

---

### Modulation & Spatialization Examples

#### 1. FM Synthesis Carrier and Modulator Traces
```supercollider
(
x = {
    var mod = SinOsc.ar(110, 0, 400).trace("Modulator", \scope);
    var car = SinOsc.ar(220 + mod, 0, 0.2).trace("FM Carrier", \scope);
    car.dup;
}.play;
)
x.free;
```

#### 2. Stereo Panning & Spatial Rotation
```supercollider
(
x = {
    var sig = Pulse.ar(150, 0.5, 0.2);
    var panLfo = SinOsc.kr(0.4).trace("Pan Position", \history);
    var stereo = Pan2.ar(sig, panLfo).trace("Stereo Output", \scope);
    stereo;
}.play;
)
x.free;
```

---

## 4. Embedding Probe Visualizers into Custom GUIs & Default SC GUI

Every instrument provides an embeddable view factory constructor:
```supercollider
Probe<Instrument>.view(parent, bounds, server, ...)
```
This returns the underlying embeddable view (a subclass of `View`), allowing seamless placement inside any custom `Window`, `CompositeView`, `ScrollView`, or layout manager alongside stock SuperCollider widgets (`Slider`, `Knob`, `Button`, `NumberBox`, etc.).

---

### Example A: Complete Synthesizer Control Panel with Embedded Scope & Meter
A self-contained custom synthesizer GUI featuring native SuperCollider sliders and buttons side-by-side with embedded Probe visualizers:

```supercollider
(
s.waitForBoot({
    var win, synth, freqSlider, resSlider, gateBtn;
    var scopeView, meterView;
    var synthDef;

    // 1. Define interactive synth
    synthDef = SynthDef(\probeGuiDemo, { |out=0, freq=220, res=0.2, gate=1|
        var osc = Mix([Saw.ar(freq * [0.99, 1.01]), Pulse.ar(freq * 0.5, 0.3)]) * 0.2;
        var env = EnvGen.kr(Env.adsr(0.02, 0.3, 0.6, 0.8), gate, doneAction: 0);
        var filt = RLPF.ar(osc, (freq * 4).clip(60, 16000), res);
        Out.ar(out, (filt * env).dup);
    }).add;

    s.sync;

    // 2. Create custom parent Window
    win = Window("Synth Channel Strip — Probe Embedded GUI", Rect(150, 150, 720, 380));
    win.view.background = Color.gray(0.12);

    // 3. Embed Probe Meter (left column)
    meterView = ProbeMeter.view(win, Rect(15, 20, 70, 340), s, numIns: 0, numOuts: 2, theme: \graphite);

    // 4. Embed Probe Scope (center antialiased oscilloscope)
    scopeView = ProbeScope.view(win, Rect(100, 20, 360, 220), s, busIndex: 0, numChannels: 2,
        theme: \graphite, engine: \smooth, style: \tracks);

    // 5. Native SuperCollider Controls (right column & bottom)
    // Frequency Slider
    StaticText(win, Rect(480, 20, 120, 20))
        .string_("Cutoff / Freq")
        .stringColor_(Color.white)
        .font_(Font("Helvetica", 11, true));

    freqSlider = Slider(win, Rect(480, 45, 210, 24))
        .value_(0.3)
        .action_({ |sl|
            var f = sl.value.linexp(0, 1, 60, 1200);
            if (synth.notNil) { synth.set(\freq, f) };
        });

    // Resonance Slider
    StaticText(win, Rect(480, 85, 120, 20))
        .string_("Filter Resonance")
        .stringColor_(Color.white)
        .font_(Font("Helvetica", 11, true));

    resSlider = Slider(win, Rect(480, 110, 210, 24))
        .value_(0.5)
        .action_({ |sl|
            var r = sl.value.linlin(0, 1, 0.05, 0.95);
            if (synth.notNil) { synth.set(\res, r) };
        });

    // Gate Toggle Button
    gateBtn = Button(win, Rect(480, 160, 100, 32))
        .states_([["Note ON", Color.white, Color.gray(0.25)], ["Note OFF", Color.black, Color.green(0.8)]])
        .action_({ |btn|
            if (btn.value == 1) {
                synth = Synth(\probeGuiDemo, [\freq, freqSlider.value.linexp(0, 1, 60, 1200), \res, resSlider.value.linlin(0, 1, 0.05, 0.95)]);
            } {
                if (synth.notNil) { synth.set(\gate, 0); synth = nil; };
            };
        });

    // Theme Selector Menu for embedded views
    PopUpMenu(win, Rect(480, 210, 130, 24))
        .items_(["Graphite", "Phosphor", "Amber", "Ice", "PureData"])
        .action_({ |menu|
            var thName = [\graphite, \phosphor, \amber, \ice, \puredata][menu.value];
            var newTheme = ProbeTheme.get(thName);
            meterView.theme = newTheme;
            scopeView.theme = newTheme;
        });

    // Cleanup on window close
    win.onClose = {
        if (synth.notNil) { synth.free };
        meterView.stop;
        scopeView.stop;
    };

    win.front;
});
)
```

---

### Example B: Mastering & Broadcast Console (Loudness + Vectorscope + Band Analyzer)
A multi-instrument quality control workstation combining loudness, stereo imaging, and ISO 1/3-octave frequency distribution in a single window:

```supercollider
(
s.waitForBoot({
    var win, lufsView, vecView, bandView;
    var sound;

    // Test stereo audio signal
    sound = {
        var bass = SinOsc.ar(55) * 0.4;
        var lead = Saw.ar([220, 222]) * 0.15;
        var noise = PinkNoise.ar(0.04);
        (bass + lead + noise);
    }.play;

    win = Window("Mastering & Quality Control Console", Rect(100, 100, 840, 360));
    win.view.background = Color.gray(0.1);

    // Embed visualizers side-by-side
    lufsView = ProbeLoudness.view(win, Rect(15, 15, 200, 330), s, busIndex: 0, theme: \graphite, target: \streaming);
    vecView  = ProbeVectorscope.view(win, Rect(225, 15, 260, 330), s, busIndex: 0, theme: \graphite, engine: \smooth);
    bandView = ProbeBandAnalyzer.view(win, Rect(495, 15, 330, 330), s, busIndex: 0, theme: \graphite, smooth: 0.7);

    win.onClose = {
        sound.free;
        lufsView.stop;
        vecView.stop;
        bandView.stop;
    };

    win.front;
});
)
```

---

### Example C: Embedding Alongside Default SuperCollider GUI Windows
Embedding a Probe visualizer alongside stock SuperCollider tools like `ServerMeterView`:

```supercollider
(
s.waitForBoot({
    var win = Window("Server Status & Precision Scope", Rect(200, 200, 520, 280));
    var meter, scope;

    // Embed Probe antialiased oscilloscope
    scope = ProbeScope.view(win, Rect(10, 10, 360, 250), s, busIndex: 0, numChannels: 2, theme: \phosphor, engine: \smooth);

    // Embed stock ServerMeterView alongside ProbeScope
    meter = ServerMeterView(s, win, Rect(380, 10, 130, 250), 2, 2);

    win.onClose = {
        scope.stop;
    };

    win.front;
});
)
```

---

## 5. Architectural Invariants & Best Practices

1. **Audio-Thread Safety:**
   - None of the visualizers allocate memory or execute blocking system calls on the real-time audio thread.
   - Shared-memory `ScopeBuffer` and circular `RecordBuf` allocations execute strictly on the server command thread.
2. **Resource Reclamation:**
   - Always call `view.stop` or close parent windows when finished.
   - All Probe views register with `ServerTree` and `ServerQuit` to automatically clean up synthetic resources upon server reboot or Cmd-Period.
3. **Dual-Engine Selection:**
   - Use `\smooth` (default) for sub-pixel antialiased waveforms with configurable stroke thickness on Retina displays.
   - Use `\native` when zero SCLang vector draw overhead is required on low-powered systems.
4. **Zero Core SC Overrides:**
   - Probe never overrides `s.meter`, `s.scope`, or `s.freqscope`. Always use `s.*Pb` or high-level class constructors.
