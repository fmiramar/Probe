# Engineering Report: Responsive Layout Scaling & Spectrum Smoothing Rollback

## 1. Overview of Fixes

Following the user's feedback, two key issues were addressed:
1. **Button & Layout Clipping on Font Scaling**: Previously, button bounds (`Button` and `PopUpMenu`) and container heights (`topH`) were hardcoded or had fixed pixel constraints. On macOS with Qt Aqua styling, increasing the font scale caused the font to be clipped by the white/beveled borders of the buttons and dropdown menus, and header labels crowded or wrapped onto multiple lines.
2. **Spectrum Smoothing Rollback**: The experimental audio-rate filter introduced Gibbs phenomenon / ringing ripples at sharp frequency transitions (harmonic peaks). As requested, smoothing was completely rolled back to the pristine, unsmoothed raw FFT magnitude scan, eliminating all ripples and artifacts.

---

## 2. Spectrum Smoothing Rollback

In `classes/SCVSpectrum.sc`:
- **SynthDef Rollback**: Removed `LPF.ar` from both linear and logarithmic SynthDefs. The synthesis graph now reads directly from the raw FFT magnitude buffer via `BufRd.ar` into `ScopeOut2.ar`.
- **Zero Ringing / Gibbs Phenomenon**: Without the 2-pole IIR filter on the scanning phasor, sharp transitions (such as harmonic peaks from a sawtooth wave or pure sine harmonics) render cleanly with zero pre-ringing, overshoot, or ripple artifacts.
- **UI Streamlining**: Removed the `smoothMenu` dropdown from the spectrum analyzer header.

```supercollider
// Restored clean raw FFT magnitude scan (Logarithmic mode)
sdef = SynthDef(synthDefName, { |in=0, scopebufnum=0, dbFactor=0.0208|
    var sig, phasor, mul = 0.00285;
    var halfSamples, phase;
    var fftbuf = LocalBuf(fftSize, 1);
    var rawMag;
    phase = 1 - (rate * fftSize.reciprocal);
    halfSamples = BufSamples.ir(fftbuf) * 0.5;
    sig = In.ar(in, 1);
    FFT(fftbuf, sig, hop: 0.75, wintype: 1);
    phasor = halfSamples.pow(LFSaw.ar(rate / BufDur.ir(fftbuf), phase, 0.5, 0.5)) * 2;
    phasor = phasor.round(2);
    rawMag = BufRd.ar(1, fftbuf, phasor, 1, 1);
    ScopeOut2.ar(((rawMag * mul).ampdb * dbFactor) + 1, scopebufnum, fftSize / rate);
});
```

![Clean Unsmoothed Spectrum with Sharp Transitions](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/spectrum-unsmoothed-sharp.png)

---

## 3. Responsive Button & Layout Scaling Architecture

### Root Cause of Button Clipping:
On macOS Qt, native controls (`PopUpMenu` and `Button`) have standard white/beveled backgrounds with internal padding (top/bottom and left/right margins for dropdown arrows and focus rings). When text scales up from 9pt to 13.5pt (at 150%), fixed-height buttons (e.g., 20px or 22px) leave insufficient vertical space for ascenders and descenders, causing Qt to clip the text against the button boundary. Furthermore, fixed button widths clip longer labels such as `"-23 LUFS (EBU)"` or `"Freeze All"`.

### Implemented Solutions:

1. **Dynamic Button Sizing**:
   - Button heights scale proportionally with `(26 * fontScale).max(24).min(38)`.
   - Control bar height dynamically expands to `topH = btnH + 12`, ensuring ample breathing room.
   - Button widths scale adaptively based on text length and active font metrics:
     - `themeMenu`: `(92 * fontScale).clip(90, 118)`
     - `targetMenu`: `(116 * fontScale).clip(114, 142)`
     - `fontMenu`: `(62 * fontScale).clip(60, 80)`
     - `freezeBtn`: `(86 * fontScale).clip(84, 106)`
     - `btnTarget` (Loudness): `(120 * fontScale).clip(104, 150)`
     - `btnReset` (Loudness): `(24 * fontScale).clip(22, 36)` height

2. **Sequential Horizontal Flow**:
   - Top control bar positions are now computed via an accumulator (`curX = curX + itemW + gap`), completely eliminating fixed pixel coordinates. Elements expand smoothly and shift their neighbors rightward without overlap.

3. **Multi-Scale Grid Adaptation**:
   - `SCVMeter`: At font scales $> 1.2$, vertical dB tick rulers automatically thin from `[0, -3, -6, ...]` to `[0, -6, -12, ...]` to prevent vertical text collisions between closely-spaced labels (`0`, `-3`, `-6`).
   - `SCVLoudness`: Title simplified to standard `"LOUDNESS [BS.1770]"` to guarantee single-line fit alongside the target toggle button at all scales.
   - All 9 visualizer classes (`SCVDashboard`, `SCVMeter`, `SCVScope`, `SCVSpectrum`, `SCVSpectrogram`, `SCVWaterfall`, `SCVHistory`, `SCVVectorscope`, `SCVLoudness`) now trigger their internal `prHandleResize` upon `theme_` and `fontScale_` changes, updating all layout offsets dynamically.

---

## 4. Visual Verification

The dashboard was visually tested at 100%, 125%, and 150% font scales with live multi-harmonic audio:

````carousel
![Dashboard at 100% Font Scale](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/dashboard-scaled-100.png)
<!-- slide -->
![Dashboard at 125% Font Scale](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/dashboard-scaled-125.png)
<!-- slide -->
![Dashboard at 150% Font Scale](/Users/fmmac/.gemini/antigravity-cli/brain/6d35b7e2-50c6-44e3-b02e-dded434f6bea/dashboard-scaled-150.png)
````

---

## 5. Verification & Git Commit

- **Unit Tests**: Executed `tests/run_all_tests.scd` — **9 of 9 suites passed cleanly with 0 failures**.
- **Local Git Tracking**: Committed locally on `main` (`commit d860b3d`). No remote changes pushed.
