# Phase Report P12 — Default 125% Font Scaling, Tight Box Layout & Phase II Transition

**Stage:** P12 (Post-Release User Feedback & Phase II Baseline Adjustment)  
**Date:** 2026-10-17  
**Status:** Approved & Verified  

---

## 1. Existing Behavior Found
- `SCVTheme.defaultFontScale` was previously initialized to `1.0`.
- Control buttons and popup menus in `SCVDashboard` and `SCVLoudness` had expanded heights (`btnH = 33px` at 125%) and wide bounding boxes (`btnTargetW = 150px` for a 13-character label), creating loose visual margins and bulky appearance.
- In `SCVDashboard`, the dashboard title wrapped onto two lines when font scale was 1.25 because `titleW` was clipped at 155px.
- In `SCVSpectrum`, eight logarithmic frequency ticks (`100`, `250`, `500`, `1k`, `2k`, `5k`, `10k`, `20k`) crowded together at the bottom-right in compact panel widths at 125% font scale.

---

## 2. What Was Missing
- Default font scale of `1.25` across all newly created themes and views.
- Snug, tight bounds for all interactive controls (buttons, dropdown menus, dB rulers) to maintain an authentic precision lab instrument look.
- Automatic frequency tick decimation at font scales > 1.2 to eliminate text collisions.
- Unified iteration history document in repository tracking all engineering iterations and architectural decisions.

---

## 3. Changes Made
1. **`classes/SCVTheme.sc`:**
   - Changed `classvar <>defaultFontScale = 1.25;`. All 6 palettes now default to 125% scaling.
2. **`classes/SCVDashboard.sc`:**
   - Tightened `btnH = (22 * fs).max(22).min(28).round.asInteger;` (28px height at 125%).
   - Reduced top bar vertical padding: `topH = btnH + 8;` (down from `btnH + 12`).
   - Snug control widths:
     - `titleW = (145 * fs).clip(145, 195).round.asInteger;` (fits `"SCVisuals DASHBOARD"` on one single line).
     - `themeW = (76 * fs).clip(76, 96).round.asInteger;` (tight fit for `"Monochrome"`).
     - `targetW = (94 * fs).clip(94, 118).round.asInteger;` (tight fit for `"-23 LUFS (EBU)"`).
     - `fontW = (48 * fs).clip(48, 62).round.asInteger;` (tight fit for `"125%"`).
     - `freezeW = (72 * fs).clip(72, 90).round.asInteger;` (tight fit for `"Freeze All"`).
   - Tightened inter-element spacing to 4–6px.
3. **`classes/SCVLoudness.sc`:**
   - Snug `btnTarget`: `btnTargetW = (88 * fs).clip(88, 112).round.asInteger;` (tight 110px box instead of bloated 150px box).
   - `btnTargetH = (20 * fs).max(20).min(26).round.asInteger;` (25px at 125%).
   - Snug `btnReset`: `btnResetH = (20 * fs).max(20).min(26).round.asInteger;`.
   - Tightened ruler width to `28 * fs` (35px at 125% instead of 40px), maximizing bar width.
4. **`classes/SCVSpectrum.sc`:**
   - Frequency labels at `fontScale > 1.2` decimate to `[100, 500, 1000, 5000, 10000, 20000]`.
   - All text collisions eliminated.
5. **`classes/SCVMeter.sc`:**
   - Tightened ruler width to `22 * fScale` (28px at 125%).
6. **`classes/SCVScope.sc`, `classes/SCVSpectrogram.sc`, `classes/SCVWaterfall.sc`, `classes/SCVVectorscope.sc`, `classes/SCVHistory.sc`:**
   - Updated fallback `fontScale ? 1.25`.
7. **`docs/PROJECT_ITERATION_LOG.md`:**
   - Master iteration tracking log created and added to repository.

---

## 4. Audio/GUI Performance Before & After
- Audio DSP CPU: Unchanged (0.4%–0.6% on 2 channels).
- GUI Memory & Frame Rate: 25–30 FPS sustained, zero leaks.
- Autocomplete / Compilation: 100% clean, 0 warnings.

---

## 5. Regression Test Results
- Ran `tests/run_all_tests.scd`:
  - `SCVTheme_unittest.scd`: 62/62 PASSED
  - `SCVBroker_unittest.scd`: PASSED
  - `SCVLifecycle_unittest.scd`: PASSED
  - `SCVMeter_unittest.scd`: PASSED
  - `SCVScope_unittest.scd`: PASSED
  - `SCVSpectrum_unittest.scd`: PASSED
  - `SCVSpectrogram_unittest.scd`: PASSED
  - `SCVLoudness_unittest.scd`: PASSED
  - `SCVDashboard_unittest.scd`: PASSED
- Total: **9/9 suites passed cleanly (0 failures).**
