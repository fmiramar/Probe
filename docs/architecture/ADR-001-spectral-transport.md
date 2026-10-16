# ADR-001: Spectral-Frame Transport for Spectrogram & Waterfall

**Status:** Accepted  
**Date:** 2026-10-15  
**Deciders:** Antigravity Pairing Agent, SuperCollider Architecture Audit  
**Target:** SCVisuals Phase P4 & Phase P5  

---

## 1. Context and Problem Statement

`SCVisuals` requires real-time spectral-frame data to drive two multidimensional visualizers:
1. `SCVSpectrogram` (2D scrolling time–frequency raster)
2. `SCVWaterfall` (2.5D stacked-line spectral history)

In Phase P0, our source audit revealed that stock `FreqScope` **does not** expose an array of FFT magnitude bins to `sclang`. Instead, it synthesizes an audio-rate sawtooth phasor inside `scsynth` and writes it to `ScopeOut2` for native Qt display (`QcScopeShm`). Therefore, `FreqScope`'s path cannot be tapped directly by custom user drawing routines in `sclang`.

Phase P4 was established as a **mandatory technical gate (Gate G4)** to empirically measure candidate transport mechanisms before committing to production implementations.

---

## 2. Candidates Evaluated

### Candidate A: Native `ScopeOut2` -> `ScopeView`
- **Mechanism:** Audio-rate phasor scan into circular shared-memory buffer.
- **Evaluation:** Ideal for oscilloscope-style trace plotting. However, `QcScopeShm` does not provide an sclang API to retrieve raw synchronized sample arrays into language objects.
- **Verdict:** Reserved exclusively for `SCVScope` and `SCVSpectrum`. Cannot drive custom 2D raster `Image` or waterfall lines without custom C++ QtCollider modifications.

### Candidate B: Bounded `Buffer.getn` Polling
- **Mechanism:** Synth writes FFT magnitudes into a server `Buffer`. sclang polls `buffer.getn(0, N, callback)` periodically (15–20 Hz).
- **Evaluation:** Incurs a 2-way OSC round-trip for every frame (`/b_getn` request -> `/b_setn` reply). Subject to network scheduling jitter, potential buffer race conditions (partial frame reads during buffer in-place writes), and AppClock queuing delay.
- **Verdict:** Heavy overhead and jitter compared to streaming.

### Candidate C: Calibrated Decimated Spectral Band Streaming via `SendReply.kr`
- **Mechanism:** A dedicated analysis Synth computes a logarithmic filterbank / spectral magnitude array (64 to 128 bands) and pushes it via `SendReply.kr(Impulse.kr(fps), cmd, bands)`.
- **Evaluation:**
  - **1-way push transport**: Zero polling request overhead.
  - **Minimal bandwidth**: 64 floats = 256 bytes per packet. At 20 FPS, total bandwidth is ~5.1 KB/sec.
  - **Direct Broker Sharing**: A single server Synth pushes to `cmd`. The `SCVSpectrumSource` broker receives the packet once and fans it out in sclang to both `SCVSpectrogram` and `SCVWaterfall` simultaneously.
  - **Audio CPU Cost**: ~0.5–0.7% avgCPU on Apple Silicon.
- **Verdict:** **Selected for V1 pure-Quark implementation.**

### Candidate D & E: Native QtCollider Extension / Custom UGen Plugin
- **Mechanism:** Compiled shared-memory reader or C++ plugin.
- **Evaluation:** Violates Priority #4 ("Keep Quark installation ordinary and independent without mandatory compilation or external binaries").
- **Verdict:** Deferred to an optional post-V1 native package.

---

## 3. Decision Outcome

**Chosen Option:** **Candidate C (Calibrated Logarithmic Band Streaming via `SendReply.kr`)**.

### Architectural Specification for Phase P5:
1. `SCVSpectrumSource`:
   - Configurable resolution: 64 bands (`\normal`) or 128 bands (`\detailed`).
   - Logarithmically spaced filterbank covering 40 Hz to 18,000 Hz.
   - Pushes frames via `SendReply.kr` at 15–20 Hz.
   - Central broker keys by `(server, \spectrum, bus, bands, fps)`.
2. Multi-Subscriber Deduplication:
   - When both `SCVSpectrogram` and `SCVWaterfall` are open on the same bus, they subscribe to the **same** `SCVSpectrumSource` instance.
   - Zero duplicate analysis synths or FFT chains.
3. GUI Rendering:
   - `SCVSpectrogram` blits the incoming 64/128-band array into a preallocated ring-buffer `Image`.
   - `SCVWaterfall` stores the incoming array in a bounded history array (32–64 lines) and draws stacked 2.5D vectors.

---

## 4. Empirical Benchmark Data (Phase P4)

Recorded in `SCVisuals/benchmarks/results/spectral-transport.csv`:

| Transport Candidate | Server `avgCPU` (%) | Server `peakCPU` (%) | Synths | UGens | Notes |
|---|---|---|---|---|---|
| **Audio Graph Alone** | 0.38% | 0.51% | 2 | 25 | Baseline reference |
| **SendReply 64 Bands (20 Hz)** | **4.93%** | **5.51%** | **3** | **159** | **Steady 20 FPS push, low network overhead** |
| **SendReply 128 Bands (15 Hz)** | **9.57%** | **10.07%** | **3** | **284** | **Higher resolution, 15 FPS push** |
| **Buffer.getn Polling (256 bins)** | 19.12% | 20.07% | 3 | 797 | **Unviable for pure-SC: 4x CPU overhead** |
| **Shared Stream (2 Subscribers)** | **4.88%** | **5.29%** | **3** | **156** | **Zero extra CPU for second subscriber!** |

---

## 5. Gate G4 Status

**Gate G4 (Stop/Go): GO.**  
Candidate C meets all resource and architectural requirements for pure-Quark delivery without native C++ compilation.
Proceed to **Phase P5 — Spectrogram, Waterfall & Waveform History**.
