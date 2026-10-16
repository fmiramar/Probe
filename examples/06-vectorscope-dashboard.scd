// SCVisuals — Example 06: Vectorscope & Unified Multi-Panel Dashboard
// Runs SCVDashboard hosting Meter, Oscilloscope, Spectrum, Loudness, and Vectorscope
// in a single synchronized command center, sharing measurement broker sources.

(
s.waitForBoot({
	var dash, testSig;

	"Starting SCVisuals Unified Dashboard Demo...".postln;

	// Audio Generator: Stereo synthesizer with panning sweep, harmonics, and sub bass
	testSig = {
		var panLFO = SinOsc.kr(0.15);
		var modLFO = SinOsc.kr(0.2).range(300, 3500);
		var oscL = Saw.ar(110 + (SinOsc.kr(4) * 1.5)) * 0.25;
		var oscR = Pulse.ar(110.5, 0.4) * 0.2;
		var noise = BPF.ar(PinkNoise.ar(0.3), modLFO, 0.2);
		var sig = Balance2.ar(oscL + noise, oscR + noise, panLFO);
		Out.ar(0, sig);
	}.play;

	// Launch Unified Dashboard
	dash = SCVisuals.dashboard(s, numIns: 2, numOuts: 2, busIndex: 0, theme: \graphite, targetLUFS: -23.0);

	dash.window.onClose = {
		testSig.free;
		"Dashboard closed.".postln;
	};
});
)
