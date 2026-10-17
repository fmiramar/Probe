// examples/phase2/05_spectral_dashboard.scd
// Probe Phase II — Advanced Spectral Dashboard Demo
// Demonstrates the dedicated multi-panel spectral analysis console with 4 presets:
// Overview, Pitch & Chroma, Time-Frequency, and Differential Compare.

s.waitForBoot({
	var testSound;

	"=== Probe Phase II: Advanced Spectral Dashboard Demo ===".postln;

	// Rich test sound: stereo sawtooth with slow resonant filter sweep and vibrato
	testSound = {
		var lfo = SinOsc.kr(0.3).exprange(200, 3500);
		var vib = SinOsc.kr(5).bipolar(2);
		var sig = Saw.ar([110, 220] + vib, 0.1);
		RLPF.ar(sig, lfo, 0.25);
	}.play;

	// Launch the Advanced Spectral Dashboard on output bus 0
	~dash = s.spectralDashboard(0, theme: \ice);

	"Advanced Spectral Dashboard launched in Ice theme.".postln;
	"Use the top-left dropdown to switch between presets:".postln;
	"  - Overview: FFT Spectrum + 16-Band Analyzer + 4 Descriptors HUD".postln;
	"  - Pitch & Chroma: Fundamental f0 Tracker + 12-Tone Chromagram".postln;
	"  - Time-Frequency: Rolling Spectrogram + 3D Waterfall".postln;
	"  - Compare: Live vs Reference Hold / Differential Spectrum".postln;

	~cleanup = {
		~dash.close;
		testSound.free;
		"Cleaned up spectral dashboard demo.".postln;
	};
});
