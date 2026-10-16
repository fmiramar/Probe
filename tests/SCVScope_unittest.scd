// SCVisuals — Unit Test: SCVScope & SCVScopeView
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

	"--- Testing SCVScope & SCVScopeView ---".postln;

	// Start test signal
	testSig = {
		var sig = [SinOsc.ar(200) * 0.4, SinOsc.ar(201) * 0.3];
		Out.ar(0, sig);
	}.play;

	// 1. Instantiation
	scope = SCVisuals.scope(s, 0, 2, theme: \graphite, rate: \audio, style: \overlay);
	assert.value(scope.notNil, "SCVisuals.scope returned instance");
	sv = scope.scopeView;
	assert.value(sv.notNil, "scopeView is valid");
	assert.value(sv.numChannels == 2, "numChannels is 2");

	0.5.wait;
	assert.value(sv.scopeBuf.notNil, "ScopeBuffer allocated");
	assert.value(sv.scopeView.bufnum == sv.scopeBuf.index, "ScopeView bufnum matches allocated ScopeBuffer");

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

	// 5. Freeze toggle
	sv.toggleFreeze;
	assert.value(sv.isFrozen, "Scope is frozen");
	sv.toggleFreeze;
	assert.value(sv.isFrozen.not, "Scope is un-frozen (running)");

	// 6. Close and resource reclamation
	scope.close;
	testSig.free;
	0.4.wait;

	assert.value(sv.isClosed, "scopeView marked closed");
	assert.value(sv.scopeBuf.isNil, "ScopeBuffer freed");

	// Summary
	("--------------------------------------------------").postln;
	("SCVScope Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;

	s.quit;
	if (failed == 0) {
		"ALL SCVScope TESTS PASSED!".postln;
		0.exit;
	} {
		"SOME TESTS FAILED!".postln;
		1.exit;
	};
});
)
