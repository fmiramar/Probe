// examples/phase2/04_ugen_trace_many.scd
// Probe Phase II — Multiple Named Taps & Multichannel Tracing
// Demonstrates multiple simultaneous internal taps coexisting with cascading windows.

s.waitForBoot({
	"=== Probe Phase II: Multiple Named Taps Demo ===".postln;

	~synth = {
		// Tap 1: Raw stereo sawtooth oscillator before filtering
		var raw = Saw.ar([110, 110.5], 0.05).trace(\beforeFilter);

		// Tap 2: Control-rate modulation LFO
		var mod = SinOsc.kr(0.4).exprange(300, 3500).trace(\modLfo);

		// Tap 3: Filtered resonant audio output
		var filtered = RLPF.ar(raw, mod, 0.25).trace(\afterFilter);

		filtered;
	}.play;

	"Playing multi-tap instrument (beforeFilter, modLfo, afterFilter).".postln;
	"To stop: ~synth.free; or press Cmd+.".postln;
});
