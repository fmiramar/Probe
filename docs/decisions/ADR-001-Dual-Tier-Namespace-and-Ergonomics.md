# ADR 001: Dual-Tier Namespace Hierarchy (`PB_*`), Clean IDE Autocomplete, and Unified Server Ergonomics (`*Pb`)

**Date:** 2026-10-24  
**Status:** Accepted / Active Migration Plan  
**Target:** Probe v0.4.0 (SuperCollider 3.14+)  
**Authors:** DeepMind Antigravity Pair Programming Team

---

## 1. Context & Problem Statement

SuperCollider's language implementation (SCLang) and its Qt IDE enforce specific architectural invariants:
1. **Flat Global Namespace:** There are no packages, namespaces, or private class scopes (unlike Python modules, C++ namespaces, or Java packages). Every class compiled into the system resides in the global dictionary `Class.allClasses`.
2. **Lexical Rules:** Class identifiers must begin with an uppercase letter `[A-Z]`. Leading underscores (`_`) are reserved for VM primitives and cannot be used for user-defined classes.
3. **IDE Autocomplete Behavior:** The SuperCollider Qt IDE autocompletes any capitalized token by prefix matching against `Class.allClasses`.
4. **SCDoc Help Lookup:** Pressing `Cmd+D` triggers an exact symbol lookup in `HelpSource/Classes/<ClassName>.schelp`. If a class is declared in `.sc` without a corresponding `.schelp` file, SCDoc renders an "Undocumented Class" fallback.

### Symptoms in Probe v0.3.0
- **Autocomplete Pollution:** Over 40 classes prefixed with `Probe` appeared in the autocomplete menu upon typing `Probe` (including internal views like `ProbeScopeView`, `ProbeChromagramView`, telemetry sources like `ProbeLevelsSource`, and trace window managers). Users could not easily find the primary instruments.
- **Missing Documentation on Cmd+D:** Instruments developed in Phase II ([`ProbeSpectralDashboard`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeSpectralDashboard.sc), [`ProbeBandAnalyzer`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeBandAnalyzer.sc), [`ProbeInspector`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeInspector.sc)) were documented under class methods in [`HelpSource/Classes/Probe.schelp`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/HelpSource/Classes/Probe.schelp), causing `Cmd+D` on their direct class symbols to show "Undocumented Class".
- **Ergonomic Inconsistencies on Server Extensions:** Server convenience methods used confusing numeric suffixes (`s.meter2`, `s.scope2`, `s.freqscope2`, `s.spectrum2`) while other instruments used bare names (`s.spectrogram`, `s.loudness`, `s.dashboard`, `s.spectralDashboard`), cluttering the `Server` namespace and obscuring their relationship to the Probe suite.

---

## 2. Evaluation of Architectural Alternatives

| Architecture | Description | Pros | Cons / Failure Modes |
|---|---|---|---|
| **Alternative A: Monolithic `Probe*` Prefix** (Current) | Every class, view, source, and window named `Probe*`. | High discoverability under one word; modular OOP. | Severe autocomplete pollution (40+ classes); massive documentation debt for internal plumbing. |
| **Alternative B: Single God-Facade with Closures** | Only `Probe` exists as a class; instruments returned via Dictionary closures. | Zero autocomplete clutter. | **Severe SCLang anti-pattern.** Destroys polymorphism, `isKindOf` typing, and memory layouts; eliminates `Cmd+D` help; debugging stack traces show opaque `<closed FunctionDef>`. |
| **Alternative C: View-Only Pattern** | Eliminate standalone window classes; views act as primary objects. | Reduces class count by ~50%. | Non-standard windowing in SuperCollider; breaks standard window controls (`.alwaysOnTop_`, `.bounds_`, `.front`); still leaves auxiliary sources and views visible. |
| **Alternative D: Dual-Tier Hierarchy (`Probe*` / `PB_*`)** (Selected) | Top-level instruments retain clean `Probe*` names; all internal views, telemetry sources, and helpers use `PB_*` prefix. Custom GUI embedding provided via `Probe*.view` factory methods. | **Pristine autocomplete (exactly 14 instruments); 100% `Cmd+D` coverage; full OOP performance and type safety preserved.** | Internal plumbing resides under `PB_*` (acceptable; invisible when typing `Probe`). |

---

## 3. Decision: Alternative D with `PB_*` Prefix and `*Pb` Server Ergonomics

### Decision 3.1: The 14 Public Instruments (Tier 1 — `Probe*`)
Typing `Probe` in the IDE autocompletes **strictly** to the 14 user instruments:
1. `Probe`: Master facade, version, and global shortcuts.
2. `ProbeTheme`: Laboratory color palettes, styling, and font scaling.
3. `ProbeScope`: Hardware-accelerated oscilloscope with zero-crossing triggering.
4. `ProbeSpectrum`: FFT spectrum analyzer with interactive cursor.
5. `ProbeSpectrogram`: Scrolling time-frequency waterfall spectrogram.
6. `ProbeWaterfall`: 3D pseudo-isometric spectral waterfall.
7. `ProbeMeter`: Segmented peak & RMS level meter with true-peak clip hold.
8. `ProbeLoudness`: ITU-R BS.1770 broadcast loudness meter (LUFS, LRA).
9. `ProbeVectorscope`: Goniometer, Lissajous phase meter, and correlation bar.
10. `ProbeBandAnalyzer`: ISO octave band analyzer (1/1, 1/3, 1/6 octaves).
11. `ProbeSpectralDashboard`: Multi-panel synchronized spectral console.
12. `ProbeDashboard`: Master laboratory multi-instrument console.
13. `ProbeInspector`: Static DSP Graph (DAG) signal flow visualizer.
14. `ProbeHistory`: Rolling numerical telemetry history viewer.

### Decision 3.2: Internal Plumbing Renamed to `PB_*` (Tier 2)
All auxiliary views, telemetry data sources, and trace windows are renamed to use the `PB_*` prefix:
- **Telemetry Sources:** `PB_Broker`, `PB_LevelsSource`, `PB_SpectrumSource`, `PB_LoudnessSource`, `PB_VectorscopeSource`, `PB_SpectralSource`.
- **Composite GUI Views:** `PB_ScopeView`, `PB_SpectrumView`, `PB_SpectrogramView`, `PB_WaterfallView`, `PB_MeterView`, `PB_LoudnessView`, `PB_VectorscopeView`, `PB_HistoryView`, `PB_DashboardView`, `PB_BandAnalyzerView`, `PB_SpectralDashboardView`, `PB_GraphView`, `PB_DescriptorsView`, `PB_PitchTrackerView`, `PB_ChromagramView`, `PB_SpectralCompareView`.
- **Trace Windows:** `PB_TraceScopeWindow`, `PB_TraceHistoryWindow`, `PB_TraceMeterWindow`, `PB_TraceSpectrumWindow`.

### Decision 3.3: Embeddable View Factory Method (`Probe*.view`)
To support advanced users embedding visualizers into custom Qt layouts without requiring them to search for `PB_*View`, every instrument class provides a standard class method:
```supercollider
ProbeScope.view(parent, bounds, bus: 0, numChannels: 2, theme: nil)
ProbeSpectrum.view(parent, bounds, bus: 0, theme: nil)
ProbeMeter.view(parent, bounds, inBus: 0, outBus: 0, inChannels: 2, outChannels: 2, theme: nil)
ProbeLoudness.view(parent, bounds, busIndex: 0, numChannels: 2, theme: nil)
// etc.
```

### Decision 3.4: Server Ergonomics Standardized to `*Pb`
Replace confusing numeric suffixes (`2`) and bare names with the unified, expressive `*Pb` suffix:
- `s.meterPb(...)` (replaces `s.meter2`)
- `s.scopePb(...)` (replaces `s.scope2`)
- `s.freqscopePb(...)` (replaces `s.freqscope2`)
- `s.spectrumPb(...)` (replaces `s.spectrum2` / `s.spectrum`)
- `s.spectrogramPb(...)` (replaces `s.spectrogram`)
- `s.waterfallPb(...)` (replaces `s.waterfall`)
- `s.loudnessPb(...)` (replaces `s.loudness`)
- `s.vectorscopePb(...)` (replaces `s.vectorscope`)
- `s.historyPb(...)` (replaces `s.history`)
- `s.dashboardPb(...)` (replaces `s.dashboard`)
- `s.spectralDashboardPb(...)` (replaces `s.spectralDashboard`)
- `s.bandAnalyzerPb(...)` (replaces `s.bandAnalyzer`)
- `s.inspectorPb(...)` / `s.inspectPb(...)` (replaces `s.inspector` / `s.inspect`)

*Note: Legacy aliases (`s.meter2`, `s.scope2`, `s.freqscope2`) are retained in `classes/extServerProbe.sc` as deprecated forwarders to guarantee non-breaking backward compatibility.*

### Decision 3.5: Comprehensive SCDoc Coverage & Private Tagging
- Create dedicated `.schelp` files for `ProbeSpectralDashboard`, `ProbeBandAnalyzer`, and `ProbeInspector`.
- Update [`HelpSource/Classes/Probe.schelp`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/HelpSource/Classes/Probe.schelp) to document all new `s.*Pb` methods.
- Mark all internal `PB_*` classes with `PRIVATE::` or keep HelpSource strictly scoped to the 14 public instruments, preventing internal classes from indexing into the SCDoc search system.

---

## 4. Invariants Preserved
1. **Audio Safety:** DSP synths remain zero-alloc, non-blocking, and asynchronous.
2. **Centralized Transport:** `PB_Broker` continues reference-counting analysis synths per bus.
3. **Core SC Non-Interference:** `s.meter`, `s.scope`, `s.freqscope` remain completely untouched.
4. **Default Font Scale:** Maintained at `1.25` across all visualizers.
5. **Backwards Compatibility:** Legacy `SCV*` classes remain functional in [`classes/ProbeLegacyCompat.sc`](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/SCVisuals/classes/ProbeLegacyCompat.sc).
