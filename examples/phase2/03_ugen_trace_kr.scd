// examples/phase2/03_ugen_trace_kr.scd
// Probe Phase II — Transparent Control-Rate Signal Tracing
// Demonstrates numerical timeline history tracing of internal LFO modulation.
// The LFO continues directly into the filter cutoff with zero modification.

s.waitForBoot({
	"=== Probe Phase II: Control-Rate Signal Tracing Demo ===".postln;

	~synth = {
		// Control-rate LFO tapped with .trace(\cutoffLfo)
		// Default mode is \history: automatically opens numerical timeline HUD
		var cutoff = LFNoise1.kr(3).exprange(150, 4500).trace(\cutoffLfo);
		var sig = Saw.ar(110, 0.05);
		RLPF.ar(sig, cutoff, 0.2);
	}.play;

	"Playing filtered Sawtooth with real-time LFO cutoff trace.".postln;
	"To stop: ~synth.free; or press Cmd+.".postln;
});
