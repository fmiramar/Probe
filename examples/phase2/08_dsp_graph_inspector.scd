// examples/phase2/08_dsp_graph_inspector.scd
// Probe Phase II — Static DSP Graph Inspector
//
// Demonstrates how to inspect the topological signal-flow Directed Acyclic Graph (DAG)
// of any compiled SynthDef or audio Function with zero server DSP overhead:
// 1. Inspecting a compiled SynthDef
// 2. Inspecting an active audio Function
// 3. Inspecting a network containing Probe .trace taps

s.waitForBoot({
	"================================================================".postln;
	"        Probe Phase II: Static DSP Graph Inspector Demo         ".postln;
	"================================================================".postln;

	// -------------------------------------------------------------
	// Example 1: Multi-Oscillator Subtractive Synthesis Network
	// -------------------------------------------------------------
	~def1 = SynthDef(\subtractiveLead, { |out=0, freq=220, cutoff=1600, res=0.25, pan=0.0|
		var lfo = SinOsc.kr(1.5).range(200, cutoff);
		var osc1 = Saw.ar(freq);
		var osc2 = Pulse.ar(freq * 1.005, 0.35) * 0.4;
		var mix = (osc1 + osc2) * 0.3;
		var fil = RLPF.ar(mix, lfo, res);
		var env = EnvGen.kr(Env.adsr(0.08, 0.2, 0.7, 0.4), gate: 1);
		var sig = Pan2.ar(fil * env, pan);
		Out.ar(out, sig);
	});

	// Open the interactive DAG graph inspector:
	Probe.inspect(~def1, \graphite);
	"--> Opened DSP Graph Inspector for \\subtractiveLead".postln;


	// -------------------------------------------------------------
	// Example 2: Inspect Directly from an Audio Function
	// -------------------------------------------------------------
	// Evaluates Function into a temporary SynthDef and plots immediately:
	// s.inspect({ |freq=330| RLPF.ar(Saw.ar(freq) * 0.2, SinOsc.kr(0.5).range(300, 3000), 0.15) });


	// -------------------------------------------------------------
	// Example 3: Inspect Network with Probe .trace Taps
	// -------------------------------------------------------------
	~defWithTrace = SynthDef(\synthWithProbeTap, { |out=0, freq=110|
		var osc = Saw.ar(freq).trace(\preFilterTap);
		var fil = RLPF.ar(osc, 800, 0.2);
		Out.ar(out, fil * 0.25);
	});
	// Probe.inspect(~defWithTrace, \ice);
});
