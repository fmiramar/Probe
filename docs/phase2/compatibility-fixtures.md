# Probe Phase II — Compatibility Fixtures & Public API Invariants

**Date:** 2026-10-17  
**Scope:** Specification of permanent invariant APIs that must remain functional and produce identical visual and audible behavior throughout Phase II.

---

## 1. Vanilla SuperCollider Core Invariants

The following built-in methods are part of the core SuperCollider standard library. **Probe must never override, wrap, or modify their behavior:**

```supercollider
// Core Server tools must remain 100% vanilla
s.meter;       // Launches vanilla ServerMeter
s.scope;       // Launches vanilla Stethoscope
s.freqscope;   // Launches vanilla FreqScope

// Core UGen methods
UGen:poll;     // Native terminal polling remains unmodified
```

---

## 2. Phase I Public API Invariants (`SCVisuals` Facade)

All existing code written against Phase I must continue to run without errors or deprecation breaks:

```supercollider
// Metering
SCVisuals.meter(s, numIns: 2, numOuts: 2, theme: \graphite, density: \compact);

// Oscilloscope & Spectrum
SCVisuals.scope(s, busIndex: 0, numChannels: 2, theme: \graphite, xZoom: 1.0);
SCVisuals.spectrum(s, busIndex: 0, fftSize: 2048, theme: \graphite);

// Time-Frequency & History
SCVisuals.spectrogram(s, busIndex: 0, fftSize: 64, theme: \graphite);
SCVisuals.waterfall(s, busIndex: 0, fftSize: 64, theme: \graphite, numLines: 48);
SCVisuals.history(s, busType: \outputs, busIndex: 0, numChannels: 2, theme: \graphite);

// Loudness & Vectorscope
SCVisuals.loudness(s, busType: \outputs, busIndex: 0, numChannels: 2, theme: \graphite, targetLUFS: -23.0);
SCVisuals.vectorscope(s, busIndex: 0, theme: \graphite);

// Unified Dashboard
SCVisuals.dashboard(s, numIns: 2, numOuts: 2, busIndex: 0, theme: \graphite, targetLUFS: -23.0);

// Global Theme & Font Scale Configuration
SCVisuals.theme = \graphite; // \phosphor, \ice, \amber, \paper, \monochrome
SCVisuals.fontScale = 1.25;  // Default 125%
```

---

## 3. Phase II Target Additive API (To be implemented)

The following selectors have been verified as collision-free in `namespace-audit.md` and will form the new primary interface:

```supercollider
// 1. Probe Facade
Probe.meter(s);
Probe.scope(s);
Probe.freqscope(s);
Probe.dashboard(s);
Probe.spectralDashboard(s);
Probe.bandAnalyzer(s);

// 2. Non-Overriding Server Extensions
s.meter2;
s.scope2;
s.freqscope2;
s.dashboard;
s.spectrogram;
s.waterfall;
s.loudness;
s.vectorscope;
s.spectralDashboard;

// 3. Graph-Time Pass-Through Probe
{ SinOsc.ar(440, 0, 0.1).trace }.play;
{ LFNoise1.kr(2).exprange(100, 2000).trace(\cutoff) }.play;
```
