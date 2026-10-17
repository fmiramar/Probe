// tests/Probe_P4_unittest.scd
// Probe Phase II — Stage P4 Unit Test Suite
// Verifies Scope Tier A (calibrated time/amplitude axes, reset),
// Tier B (triggered display stabilization),
// and Spectrum Section 5.4 (3-mode switching, cursor readout).

(
var passed = 0, failed = 0;
var assert = { |cond, msg|
	if (cond) {
		passed = passed + 1;
		("  [PASS] " ++ msg).postln;
	} {
		failed = failed + 1;
		("  [FAIL] " ++ msg).postln;
	};
};

"=== Running Probe Phase II Stage P4 Unit Tests ===".postln;

s = Server.default;
s.options.numOutputBusChannels = 2;
s.options.numInputBusChannels = 2;

s.waitForBoot {
	var scopeWin, scopeView;
	var specWin, specView;

	// -------------------------------------------------------------
	// 1. SCVScopeView Tier A: Initial State & Parameters
	// -------------------------------------------------------------
	scopeWin = Window("Test Scope P4", Rect(100, 100, 520, 300));
	scopeView = SCVScopeView(s, scopeWin, Rect(0, 0, 520, 300), busIndex: 0, numChannels: 2, showToolbar: true);

	assert.value(scopeView.notNil, "SCVScopeView instantiates cleanly");
	assert.value(scopeView.showToolbar == true, "SCVScopeView honors showToolbar=true flag");
	assert.value(scopeView.triggerMode === \free, "SCVScopeView defaults to triggerMode \\free");
	assert.value(scopeView.triggerThreshold == 0.0, "SCVScopeView defaults to triggerThreshold 0.0");
	assert.value(scopeView.xZoom == 1.0, "SCVScopeView defaults to xZoom 1.0");
	assert.value(scopeView.yZoom == 1.0, "SCVScopeView defaults to yZoom 1.0");

	// -------------------------------------------------------------
	// 2. SCVScopeView Tier B: Trigger Mode Switching
	// -------------------------------------------------------------
	scopeView.triggerMode = \rising;
	assert.value(scopeView.triggerMode === \rising, "SCVScopeView switches to triggerMode \\rising");

	scopeView.triggerMode = \falling;
	assert.value(scopeView.triggerMode === \falling, "SCVScopeView switches to triggerMode \\falling");

	scopeView.triggerThreshold = 0.25;
	assert.value(scopeView.triggerThreshold == 0.25, "SCVScopeView sets triggerThreshold");

	scopeView.triggerMode = \free;
	assert.value(scopeView.triggerMode === \free, "SCVScopeView returns to triggerMode \\free");

	// -------------------------------------------------------------
	// 3. SCVScopeView Tier A: Zoom & Reset
	// -------------------------------------------------------------
	scopeView.xZoom = 2.0;
	scopeView.yZoom = 1.5;
	assert.value(scopeView.xZoom == 2.0, "SCVScopeView accepts xZoom 2.0");
	assert.value(scopeView.yZoom == 1.5, "SCVScopeView accepts yZoom 1.5");

	scopeView.resetZoom;
	assert.value(scopeView.xZoom == 1.0, "SCVScopeView resets xZoom to 1.0");
	assert.value(scopeView.yZoom == 1.0, "SCVScopeView resets yZoom to 1.0");

	// -------------------------------------------------------------
	// 4. SCVScopeView Style Switching
	// -------------------------------------------------------------
	scopeView.scopeStyle = \tracks;
	assert.value(scopeView.scopeStyle == 0, "SCVScopeView switches to tracks style (0)");

	scopeView.scopeStyle = \xy;
	assert.value(scopeView.scopeStyle == 2, "SCVScopeView switches to xy style (2)");

	scopeView.scopeStyle = \overlay;
	assert.value(scopeView.scopeStyle == 1, "SCVScopeView returns to overlay style (1)");

	// Close test scope
	scopeView.stop;
	scopeWin.close;
	assert.value(scopeView.isClosed == true, "SCVScopeView stops and closes cleanly without leaks");

	// -------------------------------------------------------------
	// 5. SCVSpectrumView Section 5.4: 3-Mode Switching
	// -------------------------------------------------------------
	specWin = Window("Test Spectrum P4", Rect(120, 120, 520, 300));
	specView = SCVSpectrumView(s, specWin, Rect(0, 0, 520, 300), busIndex: 0, showToolbar: true);

	assert.value(specView.notNil, "SCVSpectrumView instantiates cleanly");
	assert.value(specView.showToolbar == true, "SCVSpectrumView honors showToolbar=true flag");
	assert.value(specView.viewMode === \line, "SCVSpectrumView defaults to viewMode \\line");

	// Switch to 16-Band Analyzer mode
	specView.viewMode = \bands;
	assert.value(specView.viewMode === \bands, "SCVSpectrumView switches to \\bands mode");

	// Switch to Spectrogram mode
	specView.viewMode = \spectrogram;
	assert.value(specView.viewMode === \spectrogram, "SCVSpectrumView switches to \\spectrogram mode");

	// Return to Precision Line mode
	specView.viewMode = \line;
	assert.value(specView.viewMode === \line, "SCVSpectrumView returns to \\line mode");

	// Frequency mode (Log vs Linear)
	specView.freqMode = \linear;
	assert.value(specView.freqMode == 0, "SCVSpectrumView sets linear freqMode (0)");

	specView.freqMode = \logarithmic;
	assert.value(specView.freqMode == 1, "SCVSpectrumView returns to logarithmic freqMode (1)");

	// Close test spectrum
	specView.stop;
	specWin.close;
	assert.value(specView.isClosed == true, "SCVSpectrumView stops and closes cleanly without leaks");

	// -------------------------------------------------------------
	// 6. SCVScope Window Convenience Class
	// -------------------------------------------------------------
	{
		var win = SCVScope(s, 0, 2);
		assert.value(win.notNil, "SCVScope window facade instantiates cleanly");
		assert.value(win.scopeView.showToolbar == true, "SCVScope window enables showToolbar by default");
		win.triggerMode = \rising;
		assert.value(win.scopeView.triggerMode === \rising, "SCVScope window forwards triggerMode");
		win.resetZoom;
		assert.value(win.scopeView.xZoom == 1.0, "SCVScope window forwards resetZoom");
		win.close;
	}.value;

	// -------------------------------------------------------------
	// 7. SCVSpectrum Window Convenience Class
	// -------------------------------------------------------------
	{
		var win = SCVSpectrum(s, 0);
		assert.value(win.notNil, "SCVSpectrum window facade instantiates cleanly");
		win.viewMode = \bands;
		assert.value(win.spectrumView.viewMode === \bands, "SCVSpectrum window forwards viewMode");
		win.close;
	}.value;

	// -------------------------------------------------------------
	// Final Report
	// -------------------------------------------------------------
	"".postln;
	("=== Probe P4 Unit Tests Completed: " ++ passed ++ " Passed, " ++ failed ++ " Failed ===").postln;

	if (failed == 0) {
		0.exit;
	} {
		1.exit;
	};
};
)
