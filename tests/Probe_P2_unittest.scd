// tests/Probe_P2_unittest.scd
// Phase II Stage P2 Unit Test: Transparent Signal Tracing (.trace)
// Tests UGen:trace, Array:trace, Audio Scope, Control History, Multiple Taps, and Clean Teardown.

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
	var synth1, synth2, synth3;

	"=== Starting Probe Phase II P2: Transparent Signal Tracing Unit Test ===".postln;

	// ------------------------------------------------------------------------
	// 1. Method Existence & Reflection
	// ------------------------------------------------------------------------
	assert.value(\PB_Trace.asClass.notNil, "PB_Trace class exists in symbol table");
	assert.value(UGen.findRespondingMethodFor(\trace).notNil, "UGen responds to .trace");
	assert.value(Array.findRespondingMethodFor(\trace).notNil, "Array responds to .trace");

	// ------------------------------------------------------------------------
	// 2. Pass-Through Signal Transparency (Compile-time graph test)
	// ------------------------------------------------------------------------
	{
		var sdef = SynthDef(\probe_test_transparency, {
			var sig = SinOsc.ar(440, 0, 0.1);
			var traced = sig.trace(\testPassThrough);
			assert.value(traced === sig, "Audio UGen:trace returns exact identical input object (pass-through)");
			assert.value(traced.rate === \audio, "Audio UGen:trace preserves \\audio rate");
			assert.value(traced.numChannels == 1, "Audio UGen:trace preserves numChannels == 1");
			Out.ar(0, traced);
		});
		assert.value(sdef.notNil, "SynthDef with UGen:trace compiles successfully without errors");
	}.value;

	{
		var sdef = SynthDef(\probe_test_kr_transparency, {
			var lfo = LFNoise1.kr(2).exprange(100, 2000);
			var traced = lfo.trace(\testKrPassThrough);
			assert.value(traced === lfo, "Control UGen:trace returns exact identical input object (pass-through)");
			assert.value(traced.rate === \control, "Control UGen:trace preserves \\control rate");
			Out.kr(0, traced);
		});
		assert.value(sdef.notNil, "SynthDef with Control UGen:trace compiles successfully");
	}.value;

	{
		var sdef = SynthDef(\probe_test_array_transparency, {
			var arr = [SinOsc.ar(110), SinOsc.ar(220)];
			var traced = arr.trace(\testArrayPassThrough);
			assert.value(traced.isSequenceableCollection, "Array:trace returns a collection");
			assert.value(traced.size == 2, "Array:trace preserves multichannel array size");
			Out.ar(0, traced);
		});
		assert.value(sdef.notNil, "SynthDef with Array:trace compiles successfully");
	}.value;

	// ------------------------------------------------------------------------
	// 3. Audio Rate Live Tracing & Auto-GUI Smoke Test
	// ------------------------------------------------------------------------
	synth1 = { SinOsc.ar(440, 0, 0.1).trace(\sineWave) }.play;
	0.2.wait;

	assert.value(PB_Trace.activeTraces.size >= 1, "Active trace registered on live { ... .trace }.play");
	assert.value(
		PB_Trace.activeTraces.any { |t| t.isKindOf(PB_TraceScopeWindow) and: { t.label == "sineWave" } },
		"Audio trace automatically launches PB_TraceScopeWindow with correct label"
	);

	synth1.free;
	PB_Trace.closeAll;
	0.2.wait;
	assert.value(PB_Trace.activeTraces.size == 0, "PB_Trace.closeAll frees all active trace windows");

	// ------------------------------------------------------------------------
	// 4. Control Rate Live Tracing & Numerical History Smoke Test
	// ------------------------------------------------------------------------
	synth2 = {
		var cutoff = LFNoise1.kr(5).exprange(200, 3000).trace(\cutoffLfo);
		RLPF.ar(Saw.ar(110, 0.05), cutoff, 0.2);
	}.play;
	0.3.wait;

	assert.value(PB_Trace.activeTraces.size >= 1, "Control-rate trace registered in activeTraces");
	assert.value(
		PB_Trace.activeTraces.any { |t| t.isKindOf(PB_TraceHistoryWindow) and: { t.label == "cutoffLfo" } },
		"Control trace automatically launches PB_TraceHistoryWindow with correct label"
	);

	synth2.free;
	PB_Trace.closeAll;
	0.2.wait;
	assert.value(PB_Trace.activeTraces.size == 0, "All control trace windows closed cleanly");

	// ------------------------------------------------------------------------
	// 5. Multiple Named Taps Coexistence Test
	// ------------------------------------------------------------------------
	synth3 = {
		var raw = Saw.ar(110, 0.05).trace(\beforeFilter);
		var filtered = RLPF.ar(raw, 1200, 0.2).trace(\afterFilter);
		filtered;
	}.play;
	0.3.wait;

	assert.value(PB_Trace.activeTraces.size == 2, "Multiple named taps create distinct trace windows (size == 2)");
	assert.value(
		PB_Trace.activeTraces.collect(_.label).asSet == Set["beforeFilter", "afterFilter"],
		"Both named taps co-exist with their respective distinct labels"
	);

	synth3.free;

	// ------------------------------------------------------------------------
	// 6. CmdPeriod Teardown Test
	// ------------------------------------------------------------------------
	CmdPeriod.run;
	0.2.wait;
	assert.value(PB_Trace.activeTraces.size == 0, "CmdPeriod cleanly closes all trace windows and releases resources");

	"------------------------------------------------------------------------".postln;
	("Probe Phase II P2 Test Results: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;
	"------------------------------------------------------------------------".postln;

	if (failed == 0) {
		"ALL P2 TESTS PASSED CLEANLY!".postln;
		0.exit;
	} {
		"P2 UNIT TEST FAILURES DETECTED!".postln;
		1.exit;
	};
});
)
