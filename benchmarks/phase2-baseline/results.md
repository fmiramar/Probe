# Probe Phase II — Baseline Performance Benchmark (Gate G0)
Date: 2026-10-17 03:42:24
Host: macOS Apple Silicon (arm64, Darwin 24.x)
SuperCollider: 3.14.1
Sample Rate: 48000.0 Hz
Block Size: 64
SafetyNet Active: Yes (node 1000: safeClip_2)

## 1. Server Idle (No Audio, No Visualizers)
- Avg CPU: 0.08%
- Peak CPU: 0.09%

## 2. Active Audio (2 Channels Synthesis, No Visualizers)
- Avg CPU: 0.3%
- Peak CPU: 0.59%

## 3. Active Audio + Full Dashboard (Meter, Scope, Spectrum, Loudness, Vectorscope)
- Avg CPU: 0.72%
- Peak CPU: 1.63%
- Net Audio Overhead of Full Dashboard: 0.42%

## 4. Lifecycle & Resource Cleanup Audit
- Initial Synths on Boot: 1
- Final Synths after Visualizer Close: 1
- Initial Groups on Boot: 3
- Final Groups after Visualizer Close: 3
- Broker Sources Remaining: 0 (Expected: 0)
- Broker Subscribers Remaining: 0 (Expected: 0)
- Leaked Visualizer Nodes: 0
- Cleanup Status: **PASSED (Zero Leaks)**
