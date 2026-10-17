# Probe Phase II — Architecture & Transport Analysis

**Date:** 2026-10-17  
**Scope:** Evaluation of signal paths, telemetry messaging, buffer ownership, and local vs remote server capabilities.

---

## 1. Measurement & Telemetry Transport Layers

The visualization suite employs three distinct transport mechanisms, each aligned with specific signal types and computational budgets:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SuperCollider Audio Server                         │
├───────────────────────────────┬───────────────────────────────┬─────────────┤
│ 1. Telemetry Transport        │ 2. Oscilloscope Transport     │ 3. Spectral │
│    (Peak, RMS, Loudness, KR)  │    (AR Waveforms, Lissajous)  │    (FFT)    │
├───────────────────────────────┼───────────────────────────────┼─────────────┤
│ • SendReply.kr (UDP/OSC)      │ • ScopeOut2 UGen              │ • FFT UGen  │
│ • Throttled to 15–30 Hz       │ • Shared memory scope buffer  │ • ScopeOut2 │
│ • OSCdef listeners in sclang  │ • Direct hardware access      │   or OSC    │
└───────────────┬───────────────┴───────────────┬───────────────┴──────┬──────┘
                ▼                               ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SuperCollider Client (sclang)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│ • SCVBroker (Reference-Counted Multi-Subscriber Dispatcher)                 │
│ • Bounded Ring Buffers (History, Time-Domain Windows)                       │
│ • Qt GUI (UserView with Pen vector rendering & ScopeView hardware blits)    │
│ • Throttled Redraw Timers (25–30 FPS on AppClock)                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Detailed Transport Characteristics

### A. Scalar Telemetry (Peak, RMS, Loudness, Control-Rate)
- **Path:** Real-time analysis synths compute envelope signals -> sampled via `Impulse.kr(rate)` -> sent via `SendReply.kr` using unique reply IDs.
- **Client Processing:** Single `OSCdef` dispatches incoming values to registered view subscribers.
- **Network Footprint:** Message size is ~48–96 bytes per frame. At 20 Hz, total network bandwidth is under 2 KB/s per channel.
- **Remote Server Compatibility:** Fully compatible over LAN/WAN. Works identically on local `scsynth`, `supernova`, or headless remote servers.

### B. Audio Waveform Scopes (Oscilloscope, Vectorscope)
- **Path:** Real-time audio bus -> written to a diagnostic buffer using `ScopeOut2.ar`.
- **Display Mechanism:** Qt `ScopeView` communicates directly with scsynth through local shared memory buffers (`mSndBufUpdates`).
- **Performance:** Sub-millisecond latency, zero OSC network flood, hardware-accelerated rendering.
- **Remote Server Constraint:** Requires local shared memory. Remote servers require a fallback waveform streaming transport (such as decimated SendReply or buffer polling).

### C. Spectral Analysis (Spectrum, Spectrogram, Waterfall)
- **Path:** Input bus -> 2048-point `FFT` (Hann window, 50% overlap) -> `PV_MagBuffer` or magnitude scanning -> ScopeOut2 / buffer snapshot.
- **Spectrogram Engine:** Sclang ring buffer stores chronological magnitude slices -> rendered to an offscreen `Image` and blitted with horizontal stretch.
- **Waterfall Engine:** 48 circular frame histories drawn in reverse painter's algorithm order with 2.5D perspective skew.
- **Audio Thread Safety:** Fixed 2048-sample circular buffers allocated at SynthDef init time. Zero dynamically allocated memory during runtime.

---

## 3. Resource Ownership & Lifecycle Invariants

1. **Broker Ownership:** Analysis synths and OSCdefs are owned exclusively by `SCVBroker`. Individual GUI views never allocate or free analysis synths directly; they subscribe and unsubscribe via handles.
2. **Auto-Cleanup on Zero Subscribers:** When the last window viewing a given bus is closed, `SCVBroker` automatically frees the associated analysis synth and diagnostic buffers after a short grace period (0.5 s).
3. **Emergency Cleanup:** `CmdPeriod` and `ServerQuit` handlers purge all running visualizer synths, kill polling clocks, and disconnect OSC listeners to prevent orphan nodes.
4. **Non-Intrusive Tap Invariant:** Diagnostic taps must never insert processing in the original audio signal chain; signals are tapped via `In.ar` or non-mutating pass-through.
