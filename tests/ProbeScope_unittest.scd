// Probe — Unit Test: ProbeScope & ProbeScopeView
// Verifies oscilloscope initialization, styles (\tracks, \overlay, \xy),
// zoom scaling, theme updates, freeze toggle, and safe teardown.

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
	var scope, sv;
	var testSig;

	"--- Testing ProbeScope & ProbeScopeView ---".postln;

	// Start test signal
	testSig = {
		var sig = [SinOsc.ar(200) * 0.4, SinOsc.ar(201) * 0.3];
		Out.ar(0, sig);
	}.play;

	// 1. Instantiation
	scope = Probe.scope(s, 0, 2, theme: \graphite, rate: \audio, style: \overlay);
	assert.value(scope.notNil, "Probe.scope returned instance");
	sv = scope.scopeView;
	assert.value(sv.notNil, "scopeView is valid");
	assert.value(sv.numChannels == 2, "numChannels is 2");
	assert.value(sv.engine === \smooth, "engine defaults to \\smooth");
	assert.value(sv.lineWidth == 1.8, "lineWidth defaults to 1.8");
	assert.value(sv.fill === false, "fill defaults to false");

	0.5.wait;
	assert.value(sv.scopeBuf.notNil, "ScopeBuffer allocated");
	assert.value(sv.scopeView.bufnum == sv.scopeBuf.index, "ScopeView bufnum matches allocated ScopeBuffer");
	assert.value(sv.recordBuf.notNil, "Buffer for smooth vector allocated");

	// 1b. Dual-Engine & Fill controls
	scope.engine = \native;
	assert.value(sv.engine === \native, "engine switches to \\native");

	scope.engine = \smooth;
	assert.value(sv.engine === \smooth, "engine switches back to \\smooth");

	scope.lineWidth = 2.5;
	assert.value(sv.lineWidth == 2.5, "lineWidth updated to 2.5");

	scope.fill = true;
	assert.value(sv.fill === true, "fill updated to true");
	scope.fill = false;
	assert.value(sv.fill === false, "fill reset to false");

	// 2. Style switching
	scope.scopeStyle = \tracks;
	assert.value(sv.scopeStyle == 0, "Style set to tracks (0)");
	assert.value(sv.scopeView.style == 0, "Native scopeView style is tracks");

	scope.scopeStyle = \xy;
	assert.value(sv.scopeStyle == 2, "Style set to xy (2)");
	assert.value(sv.scopeView.style == 2, "Native scopeView style is xy");

	scope.scopeStyle = \overlay;
	assert.value(sv.scopeStyle == 1, "Style set back to overlay (1)");

	// 3. Zoom and cycle
	scope.yZoom = 2.0;
	assert.value(sv.yZoom == 2.0, "yZoom updated to 2.0");
	assert.value(sv.scopeView.yZoom == 2.0, "Native scopeView yZoom is 2.0");

	scope.xZoom = 1.5;
	assert.value(sv.xZoom == 1.5, "xZoom updated to 1.5");
	assert.value(sv.scopeView.xZoom == 1.5, "Native scopeView xZoom is 1.5");

	scope.cycle = 2048;
	assert.value(sv.cycle == 2048, "Cycle updated to 2048");

	// 4. Theme changes
	[\phosphor, \ice, \amber, \graphite].do { |thName|
		scope.theme = thName;
		assert.value(sv.theme.name === thName, "Theme updated to " ++ thName);
	};

	// 5. Freeze toggle (smooth and native)
	assert.value(scope.isFrozen == false, "Wrapper scope.isFrozen initially false");
	sv.toggleFreeze;
	0.1.wait;
	assert.value(sv.isFrozen, "Scope is frozen");
	assert.value(scope.isFrozen, "Wrapper isFrozen reflects frozen state");
	if (sv.synth.notNil) {
		assert.value(sv.synth.isRunning == false, "Synth paused during freeze");
	};

	// Switch to native while frozen
	scope.engine = \native;
	0.05.wait;
	assert.value(sv.scopeView.getProperty(\running) == true, "Native ScopeView running flag remains true during freeze to retain waveform");

	sv.toggleFreeze;
	0.1.wait;
	assert.value(sv.isFrozen.not, "Scope is un-frozen (running)");
	assert.value(scope.isFrozen.not, "Wrapper isFrozen reflects running state");
	if (sv.synth.notNil) {
		assert.value(sv.synth.isRunning == true, "Synth resumed on unfreeze");
	};

	// 6. Close and resource reclamation
	scope.close;
	testSig.free;
	0.4.wait;

	assert.value(sv.isClosed, "scopeView marked closed");
	assert.value(sv.scopeBuf.isNil, "ScopeBuffer freed");
	assert.value(sv.recordBuf.isNil, "Buffer recordBuf freed");

	// Summary
	("--------------------------------------------------").postln;
	("ProbeScope Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;

	s.quit;
	if (failed == 0) {
		"ALL ProbeScope TESTS PASSED!".postln;
		0.exit;
	} {
		"SOME TESTS FAILED!".postln;
		1.exit;
	};
});
)
