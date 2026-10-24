// Probe — Unit Test: ProbeSpectrum & ProbeSpectrumView
// Verifies spectrum analyzer initialization, FFT linear/logarithmic modes,
// dB scaling, theme changes, freeze toggle, and safe teardown.

(
s.waitForBoot({
	var passed = 0, failed = 0;
	var assert = { |cond, name|
		if (cond) {
			passed = passed + 1;
			("  [PASS] " ++ name).postln;
		} {
			failed = failed + 1;
			("  [FAIL] " ++ name).postln;
		};
	};
	var spec, sv;
	var testSig;

	"--- Testing ProbeSpectrum & ProbeSpectrumView ---".postln;

	// Start test audio with distinct harmonic peaks
	testSig = {
		var sig = (SinOsc.ar([220, 440, 880]) * [0.4, 0.25, 0.15]).sum;
		Out.ar(0, sig ! 2);
	}.play;

	// 1. Instantiation
	spec = Probe.spectrum(s, 0, 2048, theme: \graphite, freqMode: 1, dbRange: 96);
	assert.value(spec.notNil, "Probe.spectrum returned instance");
	sv = spec.spectrumView;
	assert.value(sv.notNil, "spectrumView is valid");
	assert.value(sv.fftSize == 2048, "fftSize is 2048");

	0.5.wait;
	assert.value(sv.scopeBuf.notNil, "ScopeBuffer allocated");
	assert.value(sv.scopeView.bufnum == sv.scopeBuf.index, "ScopeView bufnum matches allocated ScopeBuffer");

	// 2. Frequency Mode switching
	assert.value(sv.freqMode == 1, "Initial mode is logarithmic (1)");
	spec.freqMode = \linear;
	0.15.wait;
	assert.value(sv.freqMode == 0, "Mode switched to linear (0)");
	spec.freqMode = \logarithmic;
	0.15.wait;
	assert.value(sv.freqMode == 1, "Mode switched back to logarithmic (1)");

	// 3. dB Range switching
	spec.dbRange = 72;
	0.1.wait;
	assert.value(sv.dbRange == 72.0, "dbRange set to 72 dB");
	spec.dbRange = 120;
	0.1.wait;
	assert.value(sv.dbRange == 120.0, "dbRange set to 120 dB");
	spec.dbRange = 96;
	0.1.wait;
	assert.value(sv.dbRange == 96.0, "dbRange restored to 96 dB");

	// 4. Theme changes
	[\phosphor, \ice, \amber, \graphite].do { |thName|
		spec.theme = thName;
		assert.value(sv.theme.name === thName, "Theme updated to " ++ thName);
	};

	// 5. Freeze toggle
	sv.toggleFreeze;
	assert.value(sv.isFrozen, "Spectrum is frozen");
	sv.toggleFreeze;
	assert.value(sv.isFrozen.not, "Spectrum is un-frozen (running)");

	// 6. Close and resource reclamation
	spec.close;
	testSig.free;
	0.4.wait;

	assert.value(sv.isClosed, "spectrumView marked closed");
	assert.value(sv.scopeBuf.isNil, "ScopeBuffer freed");

	// Summary
	("--------------------------------------------------").postln;
	("ProbeSpectrum Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;

	s.quit;
	if (failed == 0) {
		"ALL ProbeSpectrum TESTS PASSED!".postln;
		0.exit;
	} {
		"SOME TESTS FAILED!".postln;
		1.exit;
	};
});
)
