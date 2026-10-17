# Namespace and Method Collision Audit
Date: 2026-10-17 03:38:59

## 1. Class Name Collision Audit
- `Probe`: **AVAILABLE** (Zero collisions)
- `SCVisuals`: **COLLISION** (Defined in: /Users/fmmac/Desktop/SC New Visulations servers/SCVisuals/classes/SCVisuals.sc)
- `Analyzer`: **AVAILABLE** (Zero collisions)
- `Bench`: **AVAILABLE** (Zero collisions)
- `Telemetry`: **AVAILABLE** (Zero collisions)
- `Monitor`: **COLLISION** (Defined in: /Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/JITLib/ProxySpace/InBus.sc)
- `Scope`: **AVAILABLE** (Zero collisions)
- `Plot`: **COLLISION** (Defined in: /Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/GUI/PlusGUI/Math/PlotView.sc)
- `Loudness`: **COLLISION** (Defined in: /Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/Audio/MachineListening.sc)

## 2. `Server` Method Collision Audit
| Selector | Status | Defining Class / File |
|---|---|---|
| `Server:meter` | **CLAIMED** | `Server:meter` in `/Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/GUI/PlusGUI/Control/server-meter.sc` |
| `Server:meter2` | **FREE** | None |
| `Server:scope` | **CLAIMED** | `Server:scope` in `/Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/GUI/PlusGUI/Control/server-scope.sc` |
| `Server:scope2` | **FREE** | None |
| `Server:freqscope` | **CLAIMED** | `Server:freqscope` in `/Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/GUI/PlusGUI/Control/server-scope.sc` |
| `Server:freqscope2` | **FREE** | None |
| `Server:dashboard` | **FREE** | None |
| `Server:dashboard2` | **FREE** | None |
| `Server:spectrogram` | **FREE** | None |
| `Server:spectrogram2` | **FREE** | None |
| `Server:waterfall` | **FREE** | None |
| `Server:waterfall2` | **FREE** | None |
| `Server:loudness` | **FREE** | None |
| `Server:loudness2` | **FREE** | None |
| `Server:spectrum` | **FREE** | None |
| `Server:spectrum2` | **FREE** | None |
| `Server:vectorscope` | **FREE** | None |
| `Server:vectorscope2` | **FREE** | None |
| `Server:history` | **FREE** | None |
| `Server:history2` | **FREE** | None |
| `Server:spectralDashboard` | **FREE** | None |
| `Server:spectralDashboard2` | **FREE** | None |
| `Server:bandAnalyzer` | **FREE** | None |
| `Server:bandAnalyzer2` | **FREE** | None |
| `Server:probe` | **FREE** | None |
| `Server:probeMeter` | **FREE** | None |
| `Server:probeScope` | **FREE** | None |

## 3. `UGen` Method Collision Audit
| Selector | Status | Defining Class / File |
|---|---|---|
| `UGen:trace` | **FREE** | None |
| `UGen:traceAudio` | **FREE** | None |
| `UGen:traceKR` | **FREE** | None |
| `UGen:probe` | **FREE** | None |
| `UGen:scope` | **CLAIMED** | `UGen:scope` in `/Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/GUI/PlusGUI/Control/UGen-scope.sc` |
| `UGen:poll` | **CLAIMED** | `UGen:poll` in `/Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/Audio/UGen.sc` |

## 4. `Array` / `SequenceableCollection` Method Collision Audit
| Selector | Class | Status | Defining Class / File |
|---|---|---|---|
| `trace` | `Array` | **FREE** | None |
| `trace` | `SequenceableCollection` | **FREE** | None |
| `traceAudio` | `Array` | **FREE** | None |
| `traceAudio` | `SequenceableCollection` | **FREE** | None |
| `probe` | `Array` | **FREE** | None |
| `probe` | `SequenceableCollection` | **FREE** | None |
| `scope` | `Array` | **CLAIMED** | `Array:scope` in `/Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/GUI/PlusGUI/Control/UGen-scope.sc` |
| `scope` | `SequenceableCollection` | **FREE** | None |

## 5. `Function` Method Collision Audit
| Selector | Status | Defining Class / File |
|---|---|---|
| `Function:trace` | **FREE** | None |
| `Function:scope` | **CLAIMED** | `Function:scope` in `/Applications/SuperCollider.app/Contents/Resources/SCClassLibrary/Common/GUI/PlusGUI/Control/server-scope.sc` |
| `Function:meter` | **FREE** | None |
