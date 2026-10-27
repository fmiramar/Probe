// tests/capture-p7-screenshots.scd
// Launches SCVVectorscope and SCVDashboard with live synthesized audio and captures screenshots.

(
s.waitForBoot({
	var vec, dash, testSig;
	var outDir = "/Users/fmmac/Desktop/SC New Visulations servers/Probe/docs/screenshots/";

	"Generating live audio for P7 screenshots...".postln;

	testSig = {
		var mod = SinOsc.kr(0.2).range(400, 3200);
		var oscL = Saw.ar([110, 220]) * 0.25;
		var oscR = Pulse.ar([110.8, 330], 0.3) * 0.2;
		var noise = BPF.ar(WhiteNoise.ar(0.2), mod, 0.25);
		var sig = Balance2.ar(oscL + noise, oscR + noise, SinOsc.kr(0.1) * 0.5);
		Out.ar(0, sig);
	}.play;

	{
		// 1. Capture Vectorscope
		vec = Probe.vectorscope(s, 0, \graphite, 15);
		2.5.wait;
		Image.fromWindow(vec.window).write(outDir ++ "scvvectorscope-live.png");
		("Saved: " ++ outDir ++ "scvvectorscope-live.png").postln;
		vec.close;
		0.5.wait;

		// 2. Capture Unified Dashboard
		dash = Probe.dashboard(s, numIns: 2, numOuts: 2, busIndex: 0, theme: \graphite, targetLUFS: -23.0);
		3.0.wait;
		Image.fromWindow(dash.window).write(outDir ++ "scvdashboard-live.png");
		("Saved: " ++ outDir ++ "scvdashboard-live.png").postln;

		0.5.wait;
		dash.close;
		testSig.free;
		s.quit;
		0.exit;
	}.fork(AppClock);
});
)
