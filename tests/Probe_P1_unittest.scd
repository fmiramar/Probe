// tests/Probe_P1_unittest.scd
// Phase II Stage P1 Unit Test: Public API, Probe Facade & Server Extensions

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
	var meter, scope, spec, dash;
	var mVanillaMeter, mVanillaScope, mVanillaFreqscope;

	"=== Starting Probe Phase II P1: Public API & Server Extensions Unit Test ===".postln;

	// ------------------------------------------------------------------------
	// 1. Probe Class Existence & Method Interface
	// ------------------------------------------------------------------------
	assert.value(\Probe.asClass.notNil, "Probe class exists in symbol table");
	assert.value(Probe.respondsTo(\meter), "Probe responds to *meter");
	assert.value(Probe.respondsTo(\scope), "Probe responds to *scope");
	assert.value(Probe.respondsTo(\freqscope), "Probe responds to *freqscope");
	assert.value(Probe.respondsTo(\spectrum), "Probe responds to *spectrum");
	assert.value(Probe.respondsTo(\spectrogram), "Probe responds to *spectrogram");
	assert.value(Probe.respondsTo(\waterfall), "Probe responds to *waterfall");
	assert.value(Probe.respondsTo(\history), "Probe responds to *history");
	assert.value(Probe.respondsTo(\loudness), "Probe responds to *loudness");
	assert.value(Probe.respondsTo(\vectorscope), "Probe responds to *vectorscope");
	assert.value(Probe.respondsTo(\dashboard), "Probe responds to *dashboard");
	assert.value(Probe.respondsTo(\spectralDashboard), "Probe responds to *spectralDashboard");
	assert.value(Probe.respondsTo(\bandAnalyzer), "Probe responds to *bandAnalyzer");
	assert.value(Probe.respondsTo(\broker), "Probe responds to *broker");
	assert.value(Probe.respondsTo(\theme), "Probe responds to *theme");
	assert.value(Probe.respondsTo(\fontScale), "Probe responds to *fontScale");

	// ------------------------------------------------------------------------
	// 2. Server Class Extensions: Unified *Pb Shortcuts and Legacy Aliases
	// ------------------------------------------------------------------------
	assert.value(s.respondsTo(\meterPb), "s responds to .meterPb");
	assert.value(s.respondsTo(\scopePb), "s responds to .scopePb");
	assert.value(s.respondsTo(\freqscopePb), "s responds to .freqscopePb");
	assert.value(s.respondsTo(\spectrumPb), "s responds to .spectrumPb");
	assert.value(s.respondsTo(\spectrogramPb), "s responds to .spectrogramPb");
	assert.value(s.respondsTo(\waterfallPb), "s responds to .waterfallPb");
	assert.value(s.respondsTo(\historyPb), "s responds to .historyPb");
	assert.value(s.respondsTo(\loudnessPb), "s responds to .loudnessPb");
	assert.value(s.respondsTo(\vectorscopePb), "s responds to .vectorscopePb");
	assert.value(s.respondsTo(\dashboardPb), "s responds to .dashboardPb");
	assert.value(s.respondsTo(\spectralDashboardPb), "s responds to .spectralDashboardPb");
	assert.value(s.respondsTo(\bandAnalyzerPb), "s responds to .bandAnalyzerPb");
	assert.value(s.respondsTo(\inspectorPb), "s responds to .inspectorPb");
	assert.value(s.respondsTo(\inspectPb), "s responds to .inspectPb");

	// Legacy aliases
	assert.value(s.respondsTo(\meter2), "s responds to legacy .meter2");
	assert.value(s.respondsTo(\scope2), "s responds to legacy .scope2");
	assert.value(s.respondsTo(\freqscope2), "s responds to legacy .freqscope2");
	assert.value(s.respondsTo(\spectrum), "s responds to legacy .spectrum");
	assert.value(s.respondsTo(\spectrum2), "s responds to legacy .spectrum2");
	assert.value(s.respondsTo(\spectrogram), "s responds to legacy .spectrogram");
	assert.value(s.respondsTo(\waterfall), "s responds to legacy .waterfall");
	assert.value(s.respondsTo(\history), "s responds to legacy .history");
	assert.value(s.respondsTo(\loudness), "s responds to legacy .loudness");
	assert.value(s.respondsTo(\vectorscope), "s responds to legacy .vectorscope");
	assert.value(s.respondsTo(\dashboard), "s responds to legacy .dashboard");
	assert.value(s.respondsTo(\spectralDashboard), "s responds to legacy .spectralDashboard");
	assert.value(s.respondsTo(\bandAnalyzer), "s responds to legacy .bandAnalyzer");

	// ------------------------------------------------------------------------
	// 3. Core SC Method Invariant: Vanilla Methods Unaltered
	// ------------------------------------------------------------------------
	mVanillaMeter = Server.findRespondingMethodFor(\meter);
	mVanillaScope = Server.findRespondingMethodFor(\scope);
	mVanillaFreqscope = Server.findRespondingMethodFor(\freqscope);

	assert.value(
		mVanillaMeter.filenameSymbol.asString.contains("server-meter.sc"),
		"s.meter remains vanilla (defined in SCClassLibrary/.../server-meter.sc)"
	);
	assert.value(
		mVanillaScope.filenameSymbol.asString.contains("server-scope.sc"),
		"s.scope remains vanilla (defined in SCClassLibrary/.../server-scope.sc)"
	);
	assert.value(
		mVanillaFreqscope.filenameSymbol.asString.contains("server-scope.sc"),
		"s.freqscope remains vanilla (defined in SCClassLibrary/.../server-scope.sc)"
	);

	// ------------------------------------------------------------------------
	// 4. Smoke Instantiation via New Server Shortcuts
	// ------------------------------------------------------------------------
	// A. s.meterPb
	meter = s.meterPb(2, 2);
	assert.value(meter.notNil and: { meter.isKindOf(ProbeMeter) }, "s.meterPb creates ProbeMeter instance");
	meter.close;
	0.2.wait;

	// B. s.scopePb
	scope = s.scopePb(0, 2);
	assert.value(scope.notNil and: { scope.isKindOf(ProbeScope) }, "s.scopePb creates ProbeScope instance");
	scope.close;
	0.2.wait;

	// C. s.freqscopePb
	spec = s.freqscopePb(0);
	assert.value(spec.notNil and: { spec.isKindOf(ProbeSpectrum) }, "s.freqscopePb creates ProbeSpectrum instance");
	spec.close;
	0.2.wait;

	// D. s.dashboardPb
	dash = s.dashboardPb(2, 2);
	assert.value(dash.notNil and: { dash.isKindOf(ProbeDashboard) }, "s.dashboardPb creates ProbeDashboard instance");
	dash.close;
	0.5.wait;

	// ------------------------------------------------------------------------
	// 5. Backwards Compatibility with SCVisuals
	// ------------------------------------------------------------------------
	assert.value(SCVisuals.respondsTo(\dashboard), "SCVisuals.dashboard remains fully operational");
	assert.value(SCVisuals.theme.notNil, "SCVisuals.theme returns active theme");
	assert.value(SCVisuals.fontScale == Probe.fontScale, "SCVisuals.fontScale matches Probe.fontScale");

	// ------------------------------------------------------------------------
	// Summary
	// ------------------------------------------------------------------------
	("------------------------------------------------------------------------").postln;
	("Probe Phase II P1 Test Results: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;
	("------------------------------------------------------------------------").postln;

	s.quit;
	if (failed == 0) {
		"ALL P1 TESTS PASSED CLEANLY!".postln;
		0.exit;
	} {
		"ONE OR MORE P1 TESTS FAILED!".postln;
		1.exit;
	};
});
)
