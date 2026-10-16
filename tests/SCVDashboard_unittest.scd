// tests/SCVDashboard_unittest.scd
// Gate G7 Verification: Vectorscope & Unified Multi-Panel Dashboard Integration Test

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
	var dash, vec;
	var broker = SCVisuals.broker;
	var monoTone, antiTone;

	"=== Starting SCVisuals Gate G7: Vectorscope & Dashboard Integration Tests ===".postln;

	// 1. Vectorscope Instantiation & Scope Setup
	vec = SCVisuals.vectorscope(s, 0, \graphite, 15);
	assert.value(vec.notNil, "SCVisuals.vectorscope returns instance");
	assert.value(vec.vectorscopeView.notNil, "vectorscopeView is valid");
	assert.value(vec.vectorscopeView.scopeView.style == 2, "ScopeView style is X/Y (2)");

	// 2. Correlation Test: In-Phase Mono (Expected +1.0)
	monoTone = {
		var sig = SinOsc.ar(440) * 0.3;
		Out.ar(0, sig ! 2); // Identical in L and R
	}.play;

	1.0.wait;
	("  Measured In-Phase Correlation: " ++ vec.vectorscopeView.correlation.round(0.01)).postln;
	assert.value(vec.vectorscopeView.correlation >= 0.90, "In-phase mono signal measures correlation >= +0.90");
	monoTone.free;

	// 3. Correlation Test: Anti-Phase (Expected -1.0)
	antiTone = {
		var sig = SinOsc.ar(440) * 0.3;
		Out.ar(0, [sig, sig.neg]); // Inverted R
	}.play;

	1.0.wait;
	("  Measured Anti-Phase Correlation: " ++ vec.vectorscopeView.correlation.round(0.01)).postln;
	assert.value(vec.vectorscopeView.correlation <= -0.90, "Anti-phase signal measures correlation <= -0.90");
	antiTone.free;

	vec.close;
	0.2.wait;

	// 4. Unified Dashboard Instantiation
	dash = SCVisuals.dashboard(s, numIns: 2, numOuts: 2, busIndex: 0, theme: \graphite, targetLUFS: -23.0);
	assert.value(dash.notNil, "SCVisuals.dashboard returns instance");
	assert.value(dash.dashboardView.notNil, "dashboardView is valid");
	assert.value(dash.dashboardView.meterView.notNil, "Panel 1: meterView embedded");
	assert.value(dash.dashboardView.scopeView.notNil, "Panel 2: scopeView embedded");
	assert.value(dash.dashboardView.spectrumView.notNil, "Panel 3: spectrumView embedded");
	assert.value(dash.dashboardView.loudnessView.notNil, "Panel 4: loudnessView embedded");
	assert.value(dash.dashboardView.vectorscopeView.notNil, "Panel 5: vectorscopeView embedded");

	// 5. Global Theme Propagation Test
	[\phosphor, \ice, \amber, \paper, \monochrome, \graphite].do { |thName|
		dash.theme = thName;
		assert.value(
			(dash.dashboardView.theme.name === thName) and:
			{ dash.dashboardView.meterView.theme.name === thName } and:
			{ dash.dashboardView.scopeView.theme.name === thName } and:
			{ dash.dashboardView.spectrumView.theme.name === thName } and:
			{ dash.dashboardView.loudnessView.theme.name === thName } and:
			{ dash.dashboardView.vectorscopeView.theme.name === thName },
			"Theme '" ++ thName ++ "' successfully propagated to all 5 panels"
		);
	};

	// 6. Master Freeze Test
	dash.toggleFreeze;
	assert.value(dash.dashboardView.isFrozen == true, "Master freeze activates");
	assert.value(dash.dashboardView.scopeView.isFrozen == true, "Scope panel frozen");
	assert.value(dash.dashboardView.loudnessView.isFrozen == true, "Loudness panel frozen");
	assert.value(dash.dashboardView.vectorscopeView.isFrozen == true, "Vectorscope panel frozen");
	dash.toggleFreeze;
	assert.value(dash.dashboardView.isFrozen == false, "Master freeze deactivates");

	// 7. Clean Teardown
	dash.close;
	0.5.wait;
	assert.value(dash.dashboardView.isClosed == true, "Dashboard closed cleanly");

	// Summary
	("-----------------------------------------------------------------").postln;
	("Gate G7 Unit Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;
	("-----------------------------------------------------------------").postln;

	s.quit;
	if (failed == 0) {
		0.exit;
	} {
		1.exit;
	};
});
)
