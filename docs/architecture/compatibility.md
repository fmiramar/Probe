# SCVisuals — Platform and Compatibility Guide (P0)

**Date:** 2026-10-15  
**Version:** 0.1.0-dev  
**Auditor:** Antigravity Pairing Agent  

---

## 1. Verified Hardware and Software Environment

The P0 phase has been validated on the following environment:

| Property | Value | Notes |
|---|---|---|
| **Operating System** | macOS 26.6.2 (Darwin 25.6.0) | Apple Silicon (arm64) |
| **SuperCollider Version** | 3.14.1 | Tag `Version-3.14.1`, commit `426edf6` |
| **Qt Version** | 6.7.3 | QtGUI native backend |
| **Audio Server** | `scsynth` | In-process and out-of-process local server |
| **Sample Rates Tested** | 44.1 kHz, 48.0 kHz | Standard hardware rates |
| **Block Sizes Tested** | 64, 512 | Audio driver hardware block sizes |
| **Display Resolution** | Retina / HiDPI (2x scale) | Confirmed via `Image.fromWindow` |

---

## 2. Server Capability Categories

### 2.1 Local Server (Fully Supported)
- **Shared Memory Scoping:** `ScopeView` communicates directly with `scsynth` via `QcScopeShm` reading local shared-memory ring buffers allocated with `ScopeBuffer.alloc(server)`.
- **Fast Telemetry:** `SendPeakRMS` OSC messages over loopback UDP (`127.0.0.1:57110`) incur negligible jitter and minimal CPU overhead.
- **Node Management:** Tail and Head nodes operate with standard `RootNode` ordering.

### 2.2 Remote Server (Constrained / Fallback Required)
- `ScopeView` cannot attach to a remote `scsynth` instance because shared memory is not accessible over network sockets.
- Metering (`SendPeakRMS`) works over network OSC, but network bandwidth and packet loss must be considered.
- Spectrogram and Scope require dedicated transport adaptation (e.g. bounded OSC polling or buffer streaming) when running against remote servers.

---

## 3. GUI Environment and Event Dispatching

- **AppClock Execution:** All UI updates (`LevelIndicator.value_`, `UserView.refresh`, `ScopeView.waveColors_`) **must** be executed on `AppClock`.
- **Coalescing Policy:** Stock `ServerMeterView` calls `.defer` directly on every single OSC packet. When multiple meter windows or high channel counts are active, this floods the AppClock queue.
- **SCVisuals Policy:** Measurements are stored in presentation buffers. A single throttled UI repaint timer or coalesced invalidation routine runs at a predictable rate (e.g., 20–30 Hz), decoupling network packet arrivals from GUI repaint cycles.

---

## 4. Audio Engine Safety and Lifecycle

- **Node Cleanup:** All analysis Synths must be placed in a dedicated Group or at `RootNode(server)` extremities to avoid interfering with user synthesis graphs.
- **Reboot Handling:** The broker must subscribe to `ServerTree` and `ServerQuit` hooks to guarantee that restarting or killing the audio server does not leave orphan OSC responders or trigger nil reference errors in open GUI views.
- **CmdPeriod Protection:** The broker must re-register analysis Synths on `CmdPeriod` (or restore them seamlessly) so that pressing Cmd-. in SuperCollider does not freeze the visualizers.
