// tests/Probe_P5_unittest.scd
// Probe Phase II — Stage P5 Unit Test Suite
// Verifies DSP Graph Inspector, topological DAG layer ordering, rate detection, and shortcuts.

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

"=== Running Probe Phase II Stage P5 Unit Tests ===".postln;

s = Server.default;

s.waitForBoot {
	var testDef, testDef2;
	var win, gView, inspector;

	// 1. Compile test SynthDef
	testDef = SynthDef(\testP5_dag, { |out=0, freq=440, cutoff=1500|
		var lfo = SinOsc.kr(1).range(200, cutoff);
		var sig = Saw.ar(freq);
		var fil = RLPF.ar(sig, lfo, 0.2);
		Out.ar(out, fil);
	});

	assert.value(testDef.notNil, "SynthDef compiles successfully");
	assert.value(testDef.children.size > 0, "SynthDef has compiled children UGens");

	// 2. Instantiate PB_GraphView in a dummy window
	win = Window("Test Graph P5", Rect(50, 50, 700, 400));
	gView = PB_GraphView(win, Rect(0, 0, 700, 400), testDef, \graphite);

	assert.value(gView.notNil, "PB_GraphView instantiates cleanly");
	assert.value(gView.children.size == testDef.children.size, "PB_GraphView extracts all children UGens");
	assert.value(gView.maxLayer >= 3, "PB_GraphView computes topological layers (>= 3)");
	assert.value(gView.layers.size == (gView.maxLayer + 1), "PB_GraphView layer bins match maxLayer + 1");

	// Check that Out UGen is placed in the deepest/final layer
	{
		var outUGen = gView.children.detect { |u| u.isKindOf(Out) };
		assert.value(outUGen.notNil, "Graph contains Out UGen");
		assert.value(gView.nodeLayers[outUGen] == gView.maxLayer, "Out UGen resides in final layer");
	}.value;

	// Check that Control UGen is placed in layer 0
	{
		var ctlUGen = gView.children.detect { |u| u.isKindOf(Control) };
		assert.value(ctlUGen.notNil, "Graph contains Control UGen");
		assert.value(gView.nodeLayers[ctlUGen] == 0, "Control UGen resides in layer 0");
	}.value;

	// Check rate mappings
	{
		var arUGen = gView.children.detect { |u| u.rate === \audio };
		var krUGen = gView.children.detect { |u| u.rate === \control };
		assert.value(arUGen.notNil, "Audio rate UGen identified in graph");
		assert.value(krUGen.notNil, "Control rate UGen identified in graph");
	}.value;

	// 3. Theme switching
	gView.theme = \ice;
	assert.value(gView.theme.name === \ice, "PB_GraphView updates theme to \\ice");

	// 4. Function-to-SynthDef conversion
	gView.synthDef = { |out=0| Out.ar(out, SinOsc.ar(300)) };
	assert.value(gView.synthDef.notNil, "PB_GraphView accepts raw Function and compiles SynthDef");
	assert.value(gView.children.size > 0, "PB_GraphView parses Function-compiled children");

	win.close;

	// 5. Facade and Server convenience methods
	inspector = Probe.inspect(testDef);
	assert.value(inspector.notNil, "Probe.inspect facade instantiates ProbeInspector");
	assert.value(inspector.graphView.notNil, "ProbeInspector holds valid graphView");
	inspector.close;

	inspector = s.inspectorPb(testDef);
	assert.value(inspector.notNil, "s.inspectorPb server shortcut instantiates ProbeInspector");
	inspector.close;

	inspector = s.inspector(testDef);
	assert.value(inspector.notNil, "s.inspector server shortcut instantiates ProbeInspector");
	inspector.close;

	// -------------------------------------------------------------
	// Final Report
	// -------------------------------------------------------------
	"".postln;
	("=== Probe P5 Unit Tests Completed: " ++ passed ++ " Passed, " ++ failed ++ " Failed ===").postln;

	s.quit;
	if (failed == 0) {
		0.exit;
	} {
		1.exit;
	};
};
)
