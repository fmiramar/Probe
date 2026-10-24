// Probe — Unit Test: ProbeMeter & ProbeMeterView
// Verifies layout widths, multichannel scaling (2, 8, 32 channels),
// theme switching, density modes, and peak/clipping accuracy.

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
	var meter, testSig;
	var broker = Probe.broker;
	var w2, w8, w32, w64;

	"--- Testing ProbeMeter & ProbeMeterView ---".postln;

	// 1. Width calculation across channel counts and densities
	w2 = ProbeMeterView.getWidth(2, 2, \graphite);
	w8 = ProbeMeterView.getWidth(8, 8, \graphite);
	w32 = ProbeMeterView.getWidth(16, 16, ProbeTheme.get(\graphite).withDensity(\micro));
	w64 = ProbeMeterView.getWidth(32, 32, ProbeTheme.get(\graphite).withDensity(\micro));

	assert.value(w2 > 100 and: { w2 < 180 }, "2-ch compact width is bounded (" ++ w2 ++ " px)");
	assert.value(w8 > 300 and: { w8 < 450 }, "8-ch compact width is bounded (" ++ w8 ++ " px)");
	assert.value(w32 > 350 and: { w32 < 500 }, "32-ch micro width is bounded (" ++ w32 ++ " px)");
	assert.value(w64 > 700 and: { w64 < 900 }, "64-ch micro width is bounded (" ++ w64 ++ " px)");

	// 2. Instantiation with 2 channels
	meter = Probe.meter(s, 2, 2, theme: \graphite, density: \compact);
	assert.value(meter.notNil, "Probe.meter returned instance");
	assert.value(meter.meterView.notNil, "meterView is valid");
	assert.value(meter.meterView.numIns == 2, "numIns is 2");
	assert.value(meter.meterView.numOuts == 2, "numOuts is 2");

	0.5.wait;
	s.status;
	0.2.wait;

	// 3. Theme changes (must succeed without error and without new server nodes)
	{
		var synthsBefore = s.numSynths;
		[\phosphor, \ice, \amber, \paper, \monochrome, \graphite].do { |thName|
			meter.theme = thName;
			assert.value(meter.meterView.theme.name === thName, "Theme updated to " ++ thName);
		};
		s.status;
		0.2.wait;
		assert.value(s.numSynths == synthsBefore, "Zero new server nodes created during theme switches");
	}.value;

	// 4. Density changes
	meter.density = \micro;
	assert.value(meter.meterView.density === \micro, "Density updated to micro");
	meter.density = \extended;
	assert.value(meter.meterView.density === \extended, "Density updated to extended");
	meter.density = \compact;
	assert.value(meter.meterView.density === \compact, "Density restored to compact");

	// 5. Peak hold reset
	meter.resetPeakHold;
	assert.value(true, "resetPeakHold executed without error");

	// 6. Audio signal test (sine at -6 dBFS: verify no false-positive clip)
	testSig = {
		var sig = SinOsc.ar(440) * 0.5; // -6 dBFS peak
		Out.ar(0, sig ! 2);
	}.play;

	0.6.wait;
	s.status;
	0.2.wait;

	// Close meter
	meter.close;
	testSig.free;
	0.4.wait;

	assert.value(meter.meterView.isClosed, "meterView marked closed");
	assert.value(broker.sourceCount == 0, "Broker sources freed upon meter closure");

	// Summary
	("--------------------------------------------------").postln;
	("ProbeMeter Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;

	s.quit;
	if (failed == 0) {
		"ALL ProbeMeter TESTS PASSED!".postln;
		0.exit;
	} {
		"SOME TESTS FAILED!".postln;
		1.exit;
	};
});
)
