// tests/SCVLoudness_unittest.scd
// Gate G6 Verification: Numerical Validation of SCVLoudness & SCVLoudnessSource
// against ITU-R BS.1770 / EBU R128 standard test conditions.

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
	var meter1, meter2, sourceKey;
	var broker = SCVisuals.broker;
	var testTone;

	"=== Starting SCVisuals Gate G6: Loudness Meter Unit & Numerical Tests ===".postln;

	// 1. Instantiation & GUI Layout
	meter1 = SCVisuals.loudness(s, \outputs, 0, 2, \graphite, -23.0, 15);
	assert.value(meter1.notNil, "SCVisuals.loudness returns instance");
	assert.value(meter1.loudnessView.notNil, "loudnessView is valid");
	assert.value(meter1.loudnessView.targetLUFS == -23.0, "Initial target is -23.0 LUFS");

	// 2. Multi-subscriber Broker Sharing Test
	meter2 = SCVisuals.loudness(s, \outputs, 0, 2, \ice, -23.0, 15);
	assert.value(meter1.loudnessView.source === meter2.loudnessView.source, "Two loudness meters share the same SCVLoudnessSource instance");
	assert.value(meter1.loudnessView.source.consumerCount == 2, "Broker consumerCount is 2");

	// 3. Silence Handling (initial state before sound)
	0.5.wait;
	assert.value(meter1.loudnessView.source.momentaryLUFS <= -70.0, "Silence Momentary LUFS is <= -70 (or -inf)");
	assert.value(meter1.loudnessView.source.integratedLUFS <= -70.0, "Silence Integrated LUFS is <= -70 (or -inf)");

	// 4. Numerical Validation with Calibrated -23 LUFS 1 kHz Stereo Sine
	// Amplitude 0.07108 gives exactly -23.0 LUFS under BS.1770 K-weighting
	testTone = {
		var sig = SinOsc.ar(1000) * 0.07108;
		Out.ar(0, sig ! 2);
	}.play;

	// Allow 400ms momentary window and 3s short-term window to stabilize
	2.0.wait;

	("  Measured Momentary LUFS: " ++ meter1.loudnessView.source.momentaryLUFS.round(0.1)).postln;
	("  Measured Short-Term LUFS: " ++ meter1.loudnessView.source.shortTermLUFS.round(0.1)).postln;
	("  Measured Integrated LUFS: " ++ meter1.loudnessView.source.integratedLUFS.round(0.1)).postln;

	assert.value(
		(meter1.loudnessView.source.momentaryLUFS - (-23.0)).abs <= 0.5,
		"Momentary LUFS accurately matches -23.0 LUFS (+/- 0.5 LU)"
	);

	assert.value(
		(meter1.loudnessView.source.integratedLUFS - (-23.0)).abs <= 0.5,
		"Integrated LUFS accurately matches -23.0 LUFS (+/- 0.5 LU)"
	);

	// 5. Target Toggle (-23 to -14)
	meter1.toggleTarget;
	assert.value(meter1.loudnessView.targetLUFS == -14.0, "Target toggled to -14.0 LUFS");
	meter1.toggleTarget;
	assert.value(meter1.loudnessView.targetLUFS == -23.0, "Target toggled back to -23.0 LUFS");

	// 6. Reset Test
	meter1.reset;
	assert.value(meter1.loudnessView.source.momentaryLUFS <= -70.0, "Reset clears momentary loudness");
	assert.value(meter1.loudnessView.source.integratedLUFS <= -70.0, "Reset clears integrated loudness");

	// 7. Cleanup & Broker Teardown
	testTone.free;
	sourceKey = meter1.loudnessView.source.key;
	meter1.close;
	assert.value(meter2.loudnessView.source.consumerCount == 1, "Broker consumerCount decrements to 1 after meter1 close");

	meter2.close;
	0.2.wait;
	assert.value(broker.sources[sourceKey].isNil, "Broker source freed and cleaned up when consumerCount is 0");

	// Summary
	("-----------------------------------------------------------------").postln;
	("Gate G6 Unit Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;
	("-----------------------------------------------------------------").postln;

	s.quit;
	if (failed == 0) {
		0.exit;
	} {
		1.exit;
	};
});
)
