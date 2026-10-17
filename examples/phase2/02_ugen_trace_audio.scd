// examples/phase2/02_ugen_trace_audio.scd
// Probe Phase II — Transparent Audio Waveform Tracing
// Demonstrates: { SinOsc.ar(440, 0, 0.1).trace }.play
// Automatically opens a hardware-style oscilloscope trace window with calibrated +/-1.0 grid.

s.waitForBoot({
	"=== Probe Phase II: Audio Waveform Tracing Demo ===".postln;

	// Single-line ergonomic trace:
	// Signal expression is 100% pass-through (unmodified audio output)
	~synth = {
		SinOsc.ar(440, 0, 0.1).trace(\sine440);
	}.play;

	"Playing 440 Hz Sine with transparent waveform trace.".postln;
	"To stop: ~synth.free; or press Cmd+.".postln;
});
