// tests/Probe_P3_unittest.scd
// Phase II Stage P3 Unit Test: Advanced Spectral Dashboard & Spectral Band Analyzer
// Tests ProbeBandAnalyzer, ProbeSpectralDashboard, Descriptors, Pitch Tracker, Chroma, and Lifecycle.

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
	var bandWin, dashWin, testSynth, specSource, receivedData;

	"=== Starting Probe Phase II P3: Advanced Spectral Suite Unit Test ===".postln;

	// ------------------------------------------------------------------------
	// 1. Symbol & Class Verification
	// ------------------------------------------------------------------------
	assert.value(\ProbeBandAnalyzer.asClass.notNil, "ProbeBandAnalyzer class exists");
	assert.value(\PB_BandAnalyzerView.asClass.notNil, "PB_BandAnalyzerView class exists");
	assert.value(\PB_SpectralSource.asClass.notNil, "PB_SpectralSource class exists");
	assert.value(\ProbeSpectralDashboard.asClass.notNil, "ProbeSpectralDashboard class exists");
	assert.value(\PB_SpectralDashboardView.asClass.notNil, "PB_SpectralDashboardView class exists");
	assert.value(\PB_DescriptorsView.asClass.notNil, "PB_DescriptorsView class exists");
	assert.value(\PB_PitchTrackerView.asClass.notNil, "PB_PitchTrackerView class exists");
	assert.value(\PB_ChromagramView.asClass.notNil, "PB_ChromagramView class exists");
	assert.value(\PB_SpectralCompareView.asClass.notNil, "PB_SpectralCompareView class exists");

	// ------------------------------------------------------------------------
	// 2. Server & Probe Entry Point Verification
	// ------------------------------------------------------------------------
	assert.value(s.respondsTo(\spectralDashboardPb), "s responds to .spectralDashboardPb");
	assert.value(s.respondsTo(\bandAnalyzerPb), "s responds to .bandAnalyzerPb");
	assert.value(s.respondsTo(\spectralDashboard), "s responds to legacy .spectralDashboard");
	assert.value(s.respondsTo(\bandAnalyzer), "s responds to legacy .bandAnalyzer");
	assert.value(Probe.respondsTo(\spectralDashboard), "Probe responds to *spectralDashboard");
	assert.value(Probe.respondsTo(\bandAnalyzer), "Probe responds to *bandAnalyzer");

	// ------------------------------------------------------------------------
	// 3. Spectral Band Analyzer Smoke Test & Smoothing/Lag Verification
	// ------------------------------------------------------------------------
	bandWin = s.bandAnalyzerPb(0, theme: \graphite);
	assert.value(bandWin.notNil and: { bandWin.isKindOf(ProbeBandAnalyzer) }, "s.bandAnalyzerPb returns ProbeBandAnalyzer instance");
	assert.value(bandWin.analyzerView.notNil, "analyzerView is initialized");
	assert.value(bandWin.smooth == 0.7, "analyzer smooth factor defaults to 0.7");
	assert.value(bandWin.lag > 0.0, "analyzer lag returns positive seconds (> 0.0)");

	bandWin.smooth = 0.4;
	assert.value(bandWin.smooth == 0.4, "analyzer smooth setter updates to 0.4");

	bandWin.lag = 0.2;
	assert.value(bandWin.lag > 0.15 and: { bandWin.lag < 0.25 }, "analyzer lag setter sets calibrated duration (~0.2s)");

	0.2.wait;
	bandWin.close;
	0.2.wait;

	// ------------------------------------------------------------------------
	// 4. Advanced Spectral Dashboard Smoke & Preset Switching Test
	// ------------------------------------------------------------------------
	dashWin = s.spectralDashboard(0, theme: \ice);
	assert.value(dashWin.notNil and: { dashWin.isKindOf(ProbeSpectralDashboard) }, "s.spectralDashboard returns ProbeSpectralDashboard");

	0.2.wait;
	dashWin.preset = \pitch;
	assert.value(dashWin.dashboardView.currentPreset === \pitch, "Dashboard switches to \\pitch preset");

	0.2.wait;
	dashWin.preset = \timeFreq;
	assert.value(dashWin.dashboardView.currentPreset === \timeFreq, "Dashboard switches to \\timeFreq preset");

	0.2.wait;
	dashWin.preset = \compare;
	assert.value(dashWin.dashboardView.currentPreset === \compare, "Dashboard switches to \\compare preset");

	0.2.wait;
	dashWin.preset = \overview;
	assert.value(dashWin.dashboardView.currentPreset === \overview, "Dashboard switches back to \\overview preset");

	dashWin.toggleFreeze;
	assert.value(dashWin.dashboardView.isFrozen, "Dashboard master freeze toggles active");
	dashWin.toggleFreeze;
	assert.value(dashWin.dashboardView.isFrozen.not, "Dashboard master freeze toggles off");

	dashWin.close;
	0.2.wait;

	// ------------------------------------------------------------------------
	// 5. Scientific Telemetry & Algorithmic Verification
	// ------------------------------------------------------------------------
	testSynth = { SinOsc.ar(1000, 0, 0.2) }.play;
	0.2.wait;

	specSource = Probe.broker.subscribeSpectral(this, s, 0, 20, { |data|
		receivedData = data;
	});

	0.5.wait;

	assert.value(receivedData.notNil, "ProbeSpectralSource streams data callback");
	if (receivedData.notNil) {
		assert.value(
			receivedData[\centroid].inclusivelyBetween(800.0, 1300.0),
			"Spectral centroid of 1000 Hz sine is within tolerance (got: " ++ receivedData[\centroid].round(1) ++ " Hz)"
		);
		assert.value(
			receivedData[\pitch].inclusivelyBetween(900.0, 1100.0),
			"Pitch tracker accurately reports 1000 Hz fundamental (got: " ++ receivedData[\pitch].round(1) ++ " Hz)"
		);
		assert.value(
			receivedData[\hasPitch] > 0.5,
			"Pitch tracker voicing confidence is high for stable sine tone (got: " ++ receivedData[\hasPitch] ++ ")"
		);
		assert.value(
			receivedData[\bands].size == 16,
			"16 spectral bands transmitted in telemetry frame"
		);
		assert.value(
			receivedData[\chroma].size == 12,
			"12-tone chromagram transmitted in telemetry frame"
		);
	};

	Probe.broker.unsubscribeAll(this);
	testSynth.free;
	0.3.wait;

	// ------------------------------------------------------------------------
	// 6. CmdPeriod Teardown Test
	// ------------------------------------------------------------------------
	CmdPeriod.run;
	0.2.wait;

	"------------------------------------------------------------------------".postln;
	("Probe Phase II P3 Test Results: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;
	"------------------------------------------------------------------------".postln;

	if (failed == 0) {
		"ALL P3 TESTS PASSED CLEANLY!".postln;
		0.exit;
	} {
		"P3 UNIT TEST FAILURES DETECTED!".postln;
		1.exit;
	};
});
)
