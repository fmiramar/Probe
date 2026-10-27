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
