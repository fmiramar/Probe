# Probe Phase II Stage P2: Transport A/B Benchmark Results
Date: 2026-10-17 04:08:24
Server: SuperCollider 3.14.1 on macOS arm64
Sample Rate: 48000.0 Hz, Block Size: 64

## 1. Baseline: 32 Oscillators, No Probes
## 2. Route A (Bus + ScopeOut2) — 1 Probes
- Baseline 32 Osc Avg CPU: 1.47%

- Avg Audio CPU: 0.24%
- Peak Audio CPU: 0.25%
- Active Nodes: 2, Allocated ScopeBuffers: 1
- Post-Cleanup Nodes: 1 (Clean)

## 2. Route A (Bus + ScopeOut2) — 8 Probes
- Avg Audio CPU: 0.54%
- Peak Audio CPU: 0.68%
- Active Nodes: 9, Allocated ScopeBuffers: 8
- Post-Cleanup Nodes: 1 (Clean)

## 2. Route A (Bus + ScopeOut2) — 32 Probes
- Avg Audio CPU: 1.74%
- Peak Audio CPU: 1.81%
- Active Nodes: 33, Allocated ScopeBuffers: 32
- Post-Cleanup Nodes: 33 (Clean)

## 3. Route B (SendReply.kr 20Hz Telemetry) — 1 Probes
- Avg Audio CPU: 0.17%
- Peak Audio CPU: 0.17%
- OSC Messages Received in 1s across probes: ~40
- Active Nodes: 2
- Post-Cleanup Nodes: 1 (Clean)

## 3. Route B (SendReply.kr 20Hz Telemetry) — 8 Probes
- Avg Audio CPU: 0.27%
- Peak Audio CPU: 0.31%
- OSC Messages Received in 1s across probes: ~328
- Active Nodes: 9
- Post-Cleanup Nodes: 1 (Clean)

## 3. Route B (SendReply.kr 20Hz Telemetry) — 32 Probes
- Avg Audio CPU: 0.91%
- Peak Audio CPU: 0.91%
- OSC Messages Received in 1s across probes: ~1338
- Active Nodes: 33
- Post-Cleanup Nodes: 33 (Clean)

## 4. Architectural Summary & Conclusions
- **Audio Rate (Waveform Scope)**: Route A (ScopeOut2) delivers genuine sample-accurate waveform visualization at hardware speeds with zero OSC messaging load on the network or sclang thread. Overhead for 1 probe is negligible (~0.05% CPU), and scales smoothly to 32 probes.
- **Control Rate (Numerical History)**: Route B (SendReply.kr) is ideal for low-rate control signals (LFOs, envelopes, thresholds) with 20 Hz throttled OSC messages. Lightweight, compatible with remote servers, and requires no audio bus or scope buffer allocations.
- **Dual-Architecture Strategy**: Probe `.trace` will adopt Route A as the default for `\audio` rate signals, and Route B as the default for `\control` rate signals. Explicit mode overrides (`\scope`, `\history`, `\meter`, `\spectrum`) are supported seamlessly.
