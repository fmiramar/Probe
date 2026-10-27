# P4 Review — Spectral-Frame Transport Feasibility Study

**Date:** 2026-10-15  
**Auditor / Engineer:** Antigravity Pairing Agent  
**Stage Gate:** Gate G4 (Stop/Go Technical Gate — **PASSED: GO**)  

---

## 1. Upstream SuperCollider Baseline

- **SuperCollider Version:** `3.14.1` (commit `426edf6`)
- **Qt Engine:** `6.7.3`
- **Audio Server:** `scsynth` (local shared memory enabled)
- **Host System:** macOS 26.6.2 (Darwin arm64, Apple Silicon)

---

## 2. Key Technical Findings & Decision

1. **Native ScopeView (`QcScopeShm`) is Output-Only**:
   - `ScopeOut2` and `QcScopeShm` provide high-speed zero-copy plotting for oscilloscope and spectrum traces, but expose **no sclang API** to extract raw sample or magnitude arrays into language objects.
2. **Buffer Polling (`Buffer.getn`) is Rejected**:
   - Polling 256 bins at 15 Hz produced **19.12% avgCPU** and 20% peakCPU due to constant 2-way OSC message round-trips (`/b_getn` -> `/b_setn`) and scheduling jitter.
3. **Calibrated Spectral Band Streaming (`SendReply.kr`) is Selected**:
   - Pushing 64 logarithmic bands at 20 Hz incurred only **4.93% avgCPU** (and 48 bands uses ~2.5% avgCPU).
   - Single-direction push over loopback UDP eliminates round-trip polling delay.
   - **Zero-overhead multi-subscription**: Two subscribers sharing the stream consume **4.88% avgCPU**, exactly matching a single subscriber.

---

## 3. Architecture Decision Record

Full rationale and measurements are published in:
👉 [ADR-001: Spectral-Frame Transport](file:///Users/fmmac/Desktop/SC%20New%20Visulations%20servers/Probe/docs/architecture/ADR-001-spectral-transport.md)

---

## 4. Gate G4 Status: GO

Decision: **Proceed to Phase P5 (Spectrogram, Waterfall & Waveform History)** utilizing the validated `SendReply` spectral-stream broker.
