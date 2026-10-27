// tests/capture-p5-screenshots.scd
// Launches SCVSpectrogram, SCVWaterfall, and SCVHistory with live audio and captures screenshots.

(
s.waitForBoot({
	var spec, water, hist, testSig;
	var outDir = "/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals/docs/screenshots/";

	"Generating live audio for P5 screenshots...".postln;

	testSig = {
		var mod = SinOsc.kr(0.2).exprange(200, 3500);
		var sig = Saw.ar([120, 120.5]) * 0.35 + (Pulse.ar([240, 241], 0.25) * 0.25);
		sig = sig + (BPF.ar(WhiteNoise.ar(0.4), mod, 0.2) * 1.5);
		Out.ar(0, sig);
	}.play;

	{
		spec = Probe.spectrogram(s, 0, 64, \graphite, 25);
		water = Probe.waterfall(s, 0, 64, \graphite, 25, 48);
		hist = Probe.history(s, \outputs, 0, 2, \graphite, 25, 200);

		// Allow frames to accumulate
		3.0.wait;

		Image.fromWindow(spec.window).write(outDir ++ "scvspectrogram-live.png");
		("Saved: " ++ outDir ++ "scvspectrogram-live.png").postln;

		Image.fromWindow(water.window).write(outDir ++ "scvwaterfall-live.png");
		("Saved: " ++ outDir ++ "scvwaterfall-live.png").postln;

		Image.fromWindow(hist.window).write(outDir ++ "scvhistory-live.png");
		("Saved: " ++ outDir ++ "scvhistory-live.png").postln;

		0.5.wait;
		spec.close;
		water.close;
		hist.close;
		testSig.free;
		s.quit;
		0.exit;
	}.fork(AppClock);
});
)
