// examples/phase2/07_trace_cookbook.scd
// Probe Phase II — Transparent Signal Tracing (.trace) Cookbook
//
// Demonstrates five essential workflows using Probe's transparent runtime diagnostic taps:
// 1. Audio-rate waveform trace (ScopeOut2)
// 2. Control-rate modulation timeline HUD (SendReply.kr)
// 3. Multi-tap A/B pipeline inspection with cascading geometry
// 4. Mode override: Internal node FFT spectrum analyzer
// 5. Stereo multichannel tracing with custom theme palette

s.waitForBoot({
	"================================================================".postln;
	"           Probe Phase II: .trace Usage Cookbook                ".postln;
	"================================================================".postln;

	// -------------------------------------------------------------
	// Example 1: Minimal 1-Liner Audio Waveform Trace
	// Audio-rate (.ar) automatically defaults to \scope mode.
	// -------------------------------------------------------------
	~ex1 = {
		// Output signal is 100% unmodified pass-through:
		SinOsc.ar(220, 0, 0.15).trace(\sine220);
	};
	// Run: ~synth1 = ~ex1.play; ~synth1.free;


	// -------------------------------------------------------------
	// Example 2: Control-Rate LFO Modulation Timeline HUD
	// Control-rate (.kr) automatically defaults to \history mode (20 Hz HUD).
	// -------------------------------------------------------------
	~ex2 = {
		// Tapped LFO displays numerical value, min/max, and rolling curve:
		var lfo = LFNoise1.kr(3).exprange(150, 4000).trace(\filterLfo);
		var sig = Saw.ar(110, 0.08);
		// Filter receives the exact same LFO:
		RLPF.ar(sig, lfo, 0.2);
	};
	// Run: ~synth2 = ~ex2.play; ~synth2.free;


	// -------------------------------------------------------------
	// Example 3: Multi-Tap A/B Pipeline Inspection
	// Multiple taps automatically cascade diagonally (+32@+32).
	// -------------------------------------------------------------
	~ex3 = {
		// Tap 1: Raw oscillator before filter
		var raw = Pulse.ar(110, 0.3, 0.1).trace(\rawOsc);

		// Tap 2: Cutoff sweep LFO
		var cutoff = SinOsc.kr(0.5).exprange(200, 3000).trace(\cutoffLfo);

		// Tap 3: Filtered resonant output
		var filtered = BPF.ar(raw, cutoff, 0.3).trace(\filteredOutput);

		filtered;
	};
	// Run: ~synth3 = ~ex3.play; ~synth3.free;


	// -------------------------------------------------------------
	// Example 4: Mode Override: Internal Node FFT Spectrum Analyzer
	// Explicitly select mode: \spectrum, \scope, \history, or \meter.
	// -------------------------------------------------------------
	~ex4 = {
		var mod = SinOsc.ar(440, 0, 800);
		// Inspect the frequency spectrum of an internal FM carrier node:
		var carrier = SinOsc.ar(220 + mod, 0, 0.1).trace(\fmSpectrum, mode: \spectrum);
		carrier;
	};
	// Run: ~synth4 = ~ex4.play; ~synth4.free;


	// -------------------------------------------------------------
	// Example 5: Stereo Multichannel Tracing with Custom Amber Theme
	// -------------------------------------------------------------
	~ex5 = {
		// Detuned stereo saws rendered in retro amber phosphor:
		Saw.ar([110, 110.8], 0.08).trace(\stereoDCO, theme: \amber);
	};
	// Run: ~synth5 = ~ex5.play; ~synth5.free;

	"Cookbook loaded into ~ex1, ~ex2, ~ex3, ~ex4, ~ex5.".postln;
	"To audition: ~s = ~ex3.play;".postln;
	"To stop:     ~s.free; (or press Cmd+.)".postln;
});
