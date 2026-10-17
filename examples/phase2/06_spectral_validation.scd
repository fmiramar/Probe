// examples/phase2/06_spectral_validation.scd
// Probe Phase II — Spectral Telemetry & Scientific Validation Suite
// Cycles through controlled test stimuli to verify descriptor algorithms:
// 1. Pure 1000 Hz Sinusoid (Verifies centroid ~1000 Hz, low flatness ~0, stable pitch)
// 2. White Noise (Verifies high flatness ~0.8-1.0, high centroid, no voicing)
// 3. Resonant Low-Pass Filter Sweep (Verifies roll-off and centroid tracking)
// 4. Glissando (Verifies pitch tracker tracking rate)

s.waitForBoot({
	"=== Probe Phase II: Spectral Scientific Validation Demo ===".postln;

	// Open Spectral Dashboard in Overview preset
	~dash = s.spectralDashboard(0, theme: \graphite);
	1.0.wait;

	// Stage 1: Pure 1000 Hz Sine
	"--> Testing Stimulus 1: Pure 1000 Hz Sine Wave".postln;
	~testSig = { SinOsc.ar(1000, 0, 0.2) }.play;
	3.0.wait;
	~testSig.free;
	0.5.wait;

	// Stage 2: White Noise (Broadband)
	"--> Testing Stimulus 2: White Noise (Broadband Flatness Test)".postln;
	~testSig = { WhiteNoise.ar(0.1) }.play;
	3.0.wait;
	~testSig.free;
	0.5.wait;

	// Stage 3: Low-Pass Filtered Sawtooth Sweep
	"--> Testing Stimulus 3: Resonant Filter Sweep (Roll-off Test)".postln;
	~testSig = {
		var f = XLine.kr(200, 8000, 4);
		RLPF.ar(Saw.ar(110, 0.1), f, 0.15);
	}.play;
	4.0.wait;
	~testSig.free;
	0.5.wait;

	// Stage 4: Chromatic Pitch & Chromagram
	"--> Testing Stimulus 4: Chromatic Step Sequence (A4 = 440 Hz)".postln;
	~dash.preset = \pitch;
	~testSig = {
		var freq = Select.kr(Stepper.kr(Impulse.kr(2), 0, 0, 4), [440, 554.37, 659.25, 880]); // A4, C#5, E5, A5
		SinOsc.ar(freq, 0, 0.2);
	}.play;
	5.0.wait;
	~testSig.free;

	"Spectral validation sequence completed. Call ~cleanup.value to finish.".postln;

	~cleanup = {
		~dash.close;
		if (~testSig.notNil) { ~testSig.free };
		"Cleaned up validation demo.".postln;
	};
});
