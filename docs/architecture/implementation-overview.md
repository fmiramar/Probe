# Probe — Implementation Architecture & Overview

The **Probe Visuals and Representation Suite** (v0.4.0) is a cohesive, laboratory-grade family of real-time audio visualization, measurement, and signal instrumentation tools for SuperCollider.

Designed from first principles for audio-thread safety, predictable lifecycle reclamation, and zero IDE autocomplete clutter, Probe delivers professional studio visualizers without mandatory C++ compilation or changes to stock SuperCollider files.

---

## 1. Dual-Tier Architecture

To maintain pristine developer ergonomics and prevent autocomplete pollution in the SuperCollider IDE, all components adhere strictly to a **Dual-Tier Hierarchy**:

### Tier 1: Public Instruments (`Probe*`)
Exactly **14 clean, high-level classes** appear when typing `Probe` in the IDE:
- `Probe`: Central suite facade, server shortcuts, and broker lifecycle.
- `ProbeScope`: Dual-engine oscilloscope with stationary edge synchronization.
- `ProbeSpectrum`: 3-mode FFT spectrum analyzer with interactive cursor.
- `ProbeSpectrogram`: 2D scrolling time–frequency sonogram.
- `ProbeWaterfall`: 2.5D isometric stacked-line spectral history.
- `ProbeMeter`: Multichannel peak/RMS/peak-hold vertical meter.
- `ProbeLoudness`: ITU-R BS.1770-4 / EBU R128 loudness meter.
- `ProbeVectorscope`: Stereo Lissajous X/Y phase and correlation display.
- `ProbeBandAnalyzer`: 16-band ISO 1/3-octave smoothed analyzer.
- `ProbeSpectralDashboard`: 4-preset master spectral workstation.
- `ProbeDashboard`: Unified 5-panel master command center.
- `ProbeInspector`: Static topological signal-flow DAG inspector.
- `ProbeHistory`: Rolling strip-chart recorder for dynamics and crest factor.
- `ProbeTheme`: Laboratory color palettes and responsive font scaling.

### Tier 2: Plumbing, Telemetry & Views (`PB_*`)
All internal telemetry sources, OSC dispatchers, background measurement synthesizers, and embeddable composite views reside under the private `PB_*` prefix:
- `PB_Broker`: Centralized reference-counted telemetry broker.
- `PB_LevelsSource`: Audio peak/RMS measurement telemetry.
- `PB_SpectrumSource`: Shared FFT magnitude analysis pipeline.
- `PB_LoudnessSource`: ITU-R BS.1770 K-weighting biquad filterbank.
- `PB_VectorscopeSource`: Stereo correlation and phase analysis.
- `PB_SpectralSource`: 16-band ISO filterbank and spectral feature extraction.
- `PB_*View`: Embeddable composite views (`PB_ScopeView`, `PB_MeterView`, `PB_SpectrumView`, etc.).
- `PB_Trace`: Transparent in-graph runtime diagnostic taps and cascading HUD windows.

---

## 2. Embeddable View Factory Pattern

Every instrument class provides a static factory method:
```supercollider
Probe<Instrument>.view(parent, bounds, server, ...)
```
This pattern decouples client code from the internal `PB_*View` implementation classes, returning a fully configured `View` (subclass of `CompositeView`) that embeds seamlessly into custom windows, `HLayout` / `VLayout` containers, or complex multi-panel GUIs.

---

## 3. Server Ergonomics

Every instrument is directly accessible from the `Server` instance using the non-colliding `*Pb` suffix:
- `s.scopePb`: Precision oscilloscope
- `s.meterPb`: Multichannel level meter
- `s.freqscopePb` / `s.spectrumPb`: FFT spectrum analyzer
- `s.spectrogramPb`: Scrolling 2D sonogram
- `s.waterfallPb`: 2.5D isometric spectral waterfall
- `s.historyPb`: Rolling strip-chart dynamics recorder
- `s.loudnessPb`: ITU-R BS.1770 / EBU R128 loudness meter
- `s.vectorscopePb`: Stereo Lissajous vectorscope
- `s.dashboardPb`: 5-panel master command center
- `s.spectralDashboardPb`: 4-preset spectral workstation
- `s.bandAnalyzerPb`: 16-band ISO 1/3-octave analyzer
- `s.inspectorPb` / `s.inspectPb`: Static DSP graph inspector

Core SuperCollider methods (`s.meter`, `s.scope`, `s.freqscope`) remain 100% untouched, ensuring zero breaking changes for existing vanilla workflows.

---

## 4. Visualizer Lineup & Architecture Matrix

| Instrument | Description | Embeddable View Factory | Backend Transport |
|---|---|---|---|
| **`s.meterPb`** / `Probe.meter` | Compact vertical server meter (inputs/outputs, dB ruler, peak/RMS, peak-hold) | `ProbeMeter.view(parent, bounds)` | `SendPeakRMS` via `PB_LevelsSource` |
| **`s.scopePb`** / `Probe.scope` | Oscilloscope with calibrated ms/dBFS axes, stationary trigger edge lock (`\rising`, `\falling`), tracks/overlay/XY | `ProbeScope.view(parent, bounds)` | `ScopeView` / `ScopeOut2` + DSP circular latch buffer |
| **`s.freqscopePb`** / `Probe.spectrum` | Unified FFT spectrum analyzer with 3 modes (Line, 16-Band Analyzer, Spectrogram) and interactive cursor | `ProbeSpectrum.view(parent, bounds)` | Precision FreqScope FFT pipeline |
| **`s.spectralDashboardPb`** / `Probe.spectralDashboard` | 4-preset command center (Overview, Pitch, Time-Frequency, Compare) with 16-band analyzer, chroma, and descriptors | `ProbeSpectralDashboard.view(parent, bounds)` | `PB_SpectralSource` & `PB_Broker` |
| **`s.bandAnalyzerPb`** / `Probe.bandAnalyzer` | 16-band ISO 1/3-octave band analyzer with peak indicators and RMS bars | `ProbeBandAnalyzer.view(parent, bounds)` | `PB_SpectralSource` filterbank |
| **`s.inspectorPb`** / `Probe.inspect` | Static topological signal-flow Directed Acyclic Graph (DAG) inspector with rate coloring and bezier routing | `ProbeInspector.view(parent, bounds)` | Static SCLang DAG compilation (`PB_GraphView`) |
| **`.trace`** | Transparent runtime in-graph signal tracing for audio rate (Scope) and control rate (20 Hz Timeline HUD) | Integrated HUD | Dual transport: `ScopeOut2` / `SendReply.kr` (`PB_Trace`) |
| **`s.spectrogramPb`** / `Probe.spectrogram` | 2D scrolling time–frequency spectrogram with circular ring-buffer blit | `ProbeSpectrogram.view(parent, bounds)` | 64-band logarithmic `SendReply.kr` |
| **`s.waterfallPb`** / `Probe.waterfall` | 2.5D isometric stacked-line spectral history with depth-cued color shading | `ProbeWaterfall.view(parent, bounds)` | Shared `PB_SpectrumSource` |
| **`s.historyPb`** / `Probe.history` | Rolling peak, RMS, and shaded crest factor timeline history chart | `ProbeHistory.view(parent, bounds)` | Shared `PB_LevelsSource` |
| **`s.loudnessPb`** / `Probe.loudness` | ITU-R BS.1770-4 / EBU R128 loudness meter (M, S, I, LRA, -23 / -14 LUFS targets) | `ProbeLoudness.view(parent, bounds)` | Two-stage K-weighting biquads (`PB_LoudnessSource`) |
| **`s.vectorscopePb`** / `Probe.vectorscope` | Stereo Lissajous X/Y display with Mid/Side polar graticule and phase correlation | `ProbeVectorscope.view(parent, bounds)` | `ScopeOut2` + Correlation UGen |
| **`s.dashboardPb`** / `Probe.dashboard` | Unified 5-panel command center with global theme propagation and master freeze | `ProbeDashboard.view(parent, bounds)` | `PB_Broker` |

> [!NOTE]
> Server shorthand methods (`s.meterPb`, `s.scopePb`, `s.freqscopePb`, `s.dashboardPb`, etc.) provide direct access to all instruments directly from the server instance. Legacy shorthand forwarders (`s.meter2`, `s.scope2`, `s.freqscope2`) remain supported.

